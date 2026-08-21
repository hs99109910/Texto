package com.texto.sms.extensions

import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import android.provider.OpenableColumns
import android.provider.Telephony.Mms
import android.provider.Telephony.MmsSms
import android.provider.Telephony.Sms
import android.provider.Telephony.Threads
import android.provider.Telephony.ThreadsColumns
import android.telephony.SubscriptionManager
import android.text.TextUtils
import androidx.core.net.toUri
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.google.android.mms.pdu_alt.PduHeaders
import org.fossify.commons.extensions.areDigitsOnly
import org.fossify.commons.extensions.getIntValue
import org.fossify.commons.extensions.getIntValueOr
import org.fossify.commons.extensions.getLongValue
import org.fossify.commons.extensions.getMyContactsCursor
import org.fossify.commons.extensions.getStringValue
import org.fossify.commons.extensions.hasPermission
import org.fossify.commons.extensions.normalizeString
import org.fossify.commons.extensions.notificationManager
import org.fossify.commons.extensions.queryCursor
import org.fossify.commons.extensions.showErrorToast
import org.fossify.commons.extensions.toast
import org.fossify.commons.extensions.trimToComparableNumber
import org.fossify.commons.helpers.DAY_SECONDS
import org.fossify.commons.helpers.MONTH_SECONDS
import org.fossify.commons.helpers.MyContactsContentProvider
import org.fossify.commons.helpers.PERMISSION_READ_CALL_LOG
import org.fossify.commons.helpers.PERMISSION_READ_CONTACTS
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.helpers.isQPlus
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.SimpleContact
import com.texto.sms.R
import com.texto.sms.databases.MessagesDatabase
import com.texto.sms.helpers.AttachmentUtils.parseAttachmentNames
import com.texto.sms.helpers.Config
import com.texto.sms.helpers.FILE_SIZE_NONE
import com.texto.sms.helpers.MAX_MESSAGE_LENGTH
import com.texto.sms.helpers.MESSAGES_LIMIT
import com.texto.sms.helpers.RECYCLE_BIN_THREAD_LIMIT
import com.texto.sms.helpers.MessagingCache
import com.texto.sms.helpers.NotificationHelper
import com.texto.sms.helpers.ShortcutHelper
import com.texto.sms.helpers.SystemBlockedNumbers
import com.texto.sms.helpers.blockedNumbersSnapshot
import com.texto.sms.helpers.isNumberBlockedIn
import com.texto.sms.interfaces.AttachmentsDao
import com.texto.sms.interfaces.ConversationsDao
import com.texto.sms.interfaces.DraftsDao
import com.texto.sms.interfaces.MessageAttachmentsDao
import com.texto.sms.interfaces.MessagesDao
import com.texto.sms.interfaces.ReactionsDao
import com.texto.sms.messaging.MessagingUtils
import com.texto.sms.messaging.MessagingUtils.Companion.ADDRESS_SEPARATOR
import com.texto.sms.messaging.SmsSender
import com.texto.sms.messaging.scheduleMessage
import com.texto.sms.models.Attachment
import com.texto.sms.models.Conversation
import com.texto.sms.models.Draft
import com.texto.sms.models.Message
import com.texto.sms.models.MessageAttachment
import com.texto.sms.models.NamePhoto
import com.texto.sms.models.RecycleBinMessage
import org.xmlpull.v1.XmlPullParserException
import java.io.FileNotFoundException
import kotlin.time.Duration.Companion.minutes

val Context.config: Config
    get() = Config.newInstance(applicationContext)

fun Context.getMessagesDB() = MessagesDatabase.getInstance(this)

val Context.conversationsDB: ConversationsDao
    get() = getMessagesDB().ConversationsDao()

val Context.attachmentsDB: AttachmentsDao
    get() = getMessagesDB().AttachmentsDao()

val Context.messageAttachmentsDB: MessageAttachmentsDao
    get() = getMessagesDB().MessageAttachmentsDao()

val Context.messagesDB: MessagesDao
    get() = getMessagesDB().MessagesDao()

val Context.reactionsDB: ReactionsDao
    get() = getMessagesDB().ReactionsDao()

val Context.draftsDB: DraftsDao
    get() = getMessagesDB().DraftsDao()

val Context.notificationHelper
    get() = NotificationHelper(this)

val Context.messagingUtils
    get() = MessagingUtils(this)

val Context.smsSender
    get() = SmsSender.getInstance(applicationContext as Application)

val Context.shortcutHelper get() = ShortcutHelper(this)

fun Context.getMessages(
    threadId: Long,
    dateFrom: Int = -1,
    includeScheduledMessages: Boolean = true,
    limit: Int = MESSAGES_LIMIT,
): ArrayList<Message> {
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms._ID,
        Sms.BODY,
        Sms.TYPE,
        Sms.ADDRESS,
        Sms.DATE,
        Sms.READ,
        Sms.THREAD_ID,
        Sms.SUBSCRIPTION_ID,
        Sms.STATUS
    )

    val rangeQuery = if (dateFrom == -1) "" else "AND ${Sms.DATE} < ${dateFrom.toLong() * 1000}"
    val selection = "${Sms.THREAD_ID} = ? $rangeQuery"
    val selectionArgs = arrayOf(threadId.toString())
    val sortOrder = "${Sms.DATE} DESC LIMIT $limit"

    val blockStatus = HashMap<String, Boolean>()
    val blockedNumbers = blockedNumbersSnapshot()
    var messages = ArrayList<Message>()
    queryCursor(uri, projection, selection, selectionArgs, sortOrder, showErrors = true) { cursor ->
        val senderNumber = cursor.getStringValue(Sms.ADDRESS) ?: return@queryCursor
        val isNumberBlocked = blockStatus.getOrPut(senderNumber) { isNumberBlockedIn(senderNumber, blockedNumbers) }
        if (isNumberBlocked) {
            return@queryCursor
        }

        val id = cursor.getLongValue(Sms._ID)
        val body = cursor.getStringValue(Sms.BODY)
        val type = cursor.getIntValue(Sms.TYPE)
        val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
        val senderName = namePhoto.name
        val photoUri = namePhoto.photoUri ?: ""
        val date = (cursor.getLongValue(Sms.DATE) / 1000).toInt()
        val read = cursor.getIntValue(Sms.READ) == 1
        val thread = cursor.getLongValue(Sms.THREAD_ID)
        val subscriptionId = cursor.getIntValueOr(
            key = Sms.SUBSCRIPTION_ID,
            defaultValue = SubscriptionManager.INVALID_SUBSCRIPTION_ID
        )

        val status = cursor.getIntValue(Sms.STATUS)
        val participants = senderNumber.split(ADDRESS_SEPARATOR).map { number ->
            val phoneNumber = PhoneNumber(number, 0, "", number)
            val participantPhoto = getNameAndPhotoFromPhoneNumber(number)
            SimpleContact(
                rawId = 0,
                contactId = 0,
                name = participantPhoto.name,
                photoUri = photoUri,
                phoneNumbers = arrayListOf(phoneNumber),
                birthdays = ArrayList(),
                anniversaries = ArrayList()
            )
        }
        val isMMS = false
        val message =
            Message(
                id = id,
                body = body,
                type = type,
                status = status,
                participants = ArrayList(participants),
                date = date,
                read = read,
                threadId = thread,
                isMMS = isMMS,
                attachment = null,
                senderPhoneNumber = senderNumber,
                senderName = senderName,
                senderPhotoUri = photoUri,
                subscriptionId = subscriptionId
            )
        messages.add(message)
    }

    messages.addAll(getMMS(threadId, sortOrder, dateFrom))

    if (includeScheduledMessages) {
        try {
            val scheduledMessages = messagesDB.getScheduledThreadMessages(threadId)
            messages.addAll(scheduledMessages)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    messages = messages
        .filter { it.participants.isNotEmpty() }
        .filterNot { it.isScheduled && it.millis() < System.currentTimeMillis() }
        .sortedWith(compareBy<Message> { it.date }.thenBy { it.id })
        .takeLast(limit)
        .toMutableList() as ArrayList<Message>

    return messages
}

// as soon as a message contains multiple recipients it counts as an MMS instead of SMS
fun Context.getMMS(
    threadId: Long? = null,
    sortOrder: String? = null,
    dateFrom: Int = -1,
): ArrayList<Message> {
    val uri = Mms.CONTENT_URI
    val projection = arrayOf(
        Mms._ID,
        Mms.DATE,
        Mms.READ,
        Mms.MESSAGE_BOX,
        Mms.THREAD_ID,
        Mms.SUBSCRIPTION_ID,
        Mms.STATUS
    )

    var selection: String? = null
    var selectionArgs: Array<String>? = null

    if (threadId == null && dateFrom != -1) {
        // Should not multiply 1000 here, because date in mms's database is different from sms's.
        selection = "${Sms.DATE} < ${dateFrom.toLong()}"
    } else if (threadId != null && dateFrom == -1) {
        selection = "${Sms.THREAD_ID} = ?"
        selectionArgs = arrayOf(threadId.toString())
    } else if (threadId != null) {
        selection = "${Sms.THREAD_ID} = ? AND ${Sms.DATE} < ${dateFrom.toLong()}"
        selectionArgs = arrayOf(threadId.toString())
    }

    val messages = ArrayList<Message>()
    val contactsMap = HashMap<Int, SimpleContact>()
    queryCursor(uri, projection, selection, selectionArgs, sortOrder, showErrors = true) { cursor ->
        val mmsId = cursor.getLongValue(Mms._ID)
        val type = cursor.getIntValue(Mms.MESSAGE_BOX)
        val date = cursor.getLongValue(Mms.DATE).toInt()
        val read = cursor.getIntValue(Mms.READ) == 1
        val threadId = cursor.getLongValue(Mms.THREAD_ID)
        val subscriptionId = cursor.getIntValue(Mms.SUBSCRIPTION_ID)
        val status = cursor.getIntValue(Mms.STATUS)
        val participants = getThreadParticipants(threadId, contactsMap)

        val isMMS = true
        val attachment = getMmsAttachment(mmsId)
        val body = attachment.text
        var senderNumber = ""
        var senderName = ""
        var senderPhotoUri = ""

        if (type != Mms.MESSAGE_BOX_SENT && type != Mms.MESSAGE_BOX_FAILED) {
            senderNumber = getMMSSender(mmsId)
            val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
            senderName = namePhoto.name
            senderPhotoUri = namePhoto.photoUri ?: ""
        }

        val message =
            Message(
                id = mmsId,
                body = body,
                type = type,
                status = status,
                participants = participants,
                date = date,
                read = read,
                threadId = threadId,
                isMMS = isMMS,
                attachment = attachment,
                senderPhoneNumber = senderNumber,
                senderName = senderName,
                senderPhotoUri = senderPhotoUri,
                subscriptionId = subscriptionId
            )
        messages.add(message)

        participants.forEach {
            contactsMap[it.rawId] = it
        }
    }

    return messages
}

fun Context.getMMSSender(msgId: Long): String {
    val uri = "${Mms.CONTENT_URI}/$msgId/addr".toUri()
    val projection = arrayOf(
        Mms.Addr.ADDRESS
    )

    val selection = "${Mms.Addr.TYPE} = ?"
    val selectionArgs = arrayOf(PduHeaders.FROM.toString())

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (it.moveToFirst()) {
                return it.getStringValue(Mms.Addr.ADDRESS)
            }
        }
    } catch (_: Exception) {
    }
    return ""
}

fun Context.getUnreadCountsByThread(): Map<Long, Int> {
    val result = HashMap<Long, Int>(128)

    fun bump(id: Long) {
        result[id] = (result[id] ?: 0) + 1
    }

    // Unread SMS
    queryCursor(
        uri = Sms.CONTENT_URI,
        projection = arrayOf(Sms.THREAD_ID),
        selection = "${Sms.READ}=0 AND ${Sms.TYPE}=${Sms.MESSAGE_TYPE_INBOX}",
        selectionArgs = null,
        showErrors = false
    ) { bump(it.getLongValue(Sms.THREAD_ID)) }

    // Unread MMS
    queryCursor(
        uri = Mms.CONTENT_URI,
        projection = arrayOf(Mms.THREAD_ID),
        selection = "${Mms.READ}=0 AND ${Mms.MESSAGE_BOX}=${Mms.MESSAGE_BOX_INBOX}",
        selectionArgs = null,
        showErrors = false
    ) { bump(it.getLongValue(Mms.THREAD_ID)) }

    return result
}

fun Context.getConversations(
    threadId: Long? = null,
    privateContacts: ArrayList<SimpleContact> = ArrayList(),
): ArrayList<Conversation> {
    val archiveAvailable = config.isArchiveAvailable

    val uri = "${Threads.CONTENT_URI}?simple=true".toUri()
    val projection = mutableListOf(
        Threads._ID,
        Threads.SNIPPET,
        Threads.DATE,
        Threads.READ,
        Threads.RECIPIENT_IDS,
    )

    if (archiveAvailable) {
        projection += Threads.ARCHIVED
    }

    var selection = "${Threads.MESSAGE_COUNT} > 0"
    var selectionArgs = arrayOf<String>()
    if (threadId != null) {
        selection += " AND ${Threads._ID} = ?"
        selectionArgs += threadId.toString()
    }

    val sortOrder = "${Threads.DATE} DESC"

    // Buffered first so every RECIPIENT_IDS across all threads can be resolved to phone
    // numbers in one batched query below, instead of one canonical-addresses query per
    // thread (previously the dominant cost of loading the conversation list).
    data class RawThread(
        val id: Long,
        val snippet: String,
        val date: Long,
        val read: Boolean,
        val archived: Boolean,
        val recipientIds: List<Int>,
    )

    val rawThreads = ArrayList<RawThread>()
    try {
        queryCursorUnsafe(
            uri,
            projection.toTypedArray(),
            selection,
            selectionArgs,
            sortOrder
        ) { cursor ->
            val id = cursor.getLongValue(Threads._ID)
            val snippet = cursor.getStringValue(Threads.SNIPPET) ?: ""

            var date = cursor.getLongValue(Threads.DATE)
            if (date.toString().length > 10) {
                date /= 1000
            }

            val rawIds = cursor.getStringValue(Threads.RECIPIENT_IDS)
            val recipientIds =
                rawIds.split(" ").filter { it.areDigitsOnly() }.map { it.toInt() }
            val read = cursor.getIntValue(Threads.READ) == 1
            val archived =
                if (archiveAvailable) cursor.getIntValue(Threads.ARCHIVED) == 1 else false

            rawThreads.add(RawThread(id, snippet, date, read, archived, recipientIds))
        }
    } catch (sqliteException: SQLiteException) {
        if (
            sqliteException.message?.contains("no such column: archived") == true
            && archiveAvailable
        ) {
            config.isArchiveAvailable = false
            return getConversations(threadId, privateContacts)
        } else {
            showErrorToast(sqliteException)
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }

    val addressToNumber = getPhoneNumbersFromAddressIds(rawThreads.flatMap { it.recipientIds })

    val conversations = ArrayList<Conversation>()
    val blockedNumbers = blockedNumbersSnapshot()
    val unreadMap = getUnreadCountsByThread()

    for (thread in rawThreads) {
        val phoneNumbers = ArrayList(thread.recipientIds.map { addressToNumber[it] ?: "" })
        if (phoneNumbers.isEmpty() || phoneNumbers.any { isNumberBlockedIn(it, blockedNumbers) }) {
            continue
        }

        val snippet = thread.snippet.ifEmpty { getThreadSnippet(thread.id) }
        // Resolve each recipient's name + photo once via the LruCache-backed lookup, so a
        // single cached query per distinct number serves both here and the adapter, instead
        // of getThreadContactNames and getPhotoUriFromPhoneNumber each issuing their own
        // uncached contacts-provider query per conversation.
        val namePhotos = phoneNumbers.map { getNameAndPhotoFromPhoneNumber(it) }
        val names = ArrayList<String>(phoneNumbers.size)
        phoneNumbers.forEachIndexed { index, number ->
            val resolvedName = namePhotos[index].name
            if (resolvedName != number) {
                names.add(resolvedName)
            } else {
                val privateContact = privateContacts.firstOrNull { it.doesHavePhoneNumber(number) }
                names.add(privateContact?.name ?: resolvedName)
            }
        }
        val title = TextUtils.join(", ", names.toTypedArray())
        val photoUri =
            if (phoneNumbers.size == 1) namePhotos.first().photoUri ?: "" else ""
        val isGroupConversation = phoneNumbers.size > 1
        val unreadCount = if (!thread.read) unreadMap[thread.id] ?: 0 else 0
        conversations.add(
            Conversation(
                threadId = thread.id,
                snippet = snippet,
                date = thread.date.toInt(),
                read = thread.read,
                title = title,
                photoUri = photoUri,
                isGroupConversation = isGroupConversation,
                phoneNumber = phoneNumbers.first(),
                isArchived = thread.archived,
                unreadCount = unreadCount,
            )
        )
    }

    conversations.sortByDescending { it.date }
    return conversations
}

private fun Context.queryCursorUnsafe(
    uri: Uri,
    projection: Array<String>,
    selection: String? = null,
    selectionArgs: Array<String>? = null,
    sortOrder: String? = null,
    callback: (cursor: Cursor) -> Unit,
) {
    val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
    cursor?.use {
        if (cursor.moveToFirst()) {
            do {
                callback(cursor)
            } while (cursor.moveToNext())
        }
    }
}

fun Context.getConversationIds(): List<Long> {
    val projection = arrayOf(Threads._ID)
    val sortOrder = "${Threads.DATE} ASC"
    val conversationIds = mutableListOf<Long>()
    queryCursor(Threads.CONTENT_URI, projection, null, null, sortOrder, true) { cursor ->
        val id = cursor.getLongValue(Threads._ID)
        conversationIds.add(id)
    }
    return conversationIds
}

// based on https://stackoverflow.com/a/6446831/1967672
@SuppressLint("NewApi")
fun Context.getMmsAttachment(id: Long): MessageAttachment {
    val uri = if (isQPlus()) {
        Mms.Part.CONTENT_URI
    } else {
        "content://mms/part".toUri()
    }

    val projection = arrayOf(
        Mms._ID,
        Mms.Part.CONTENT_TYPE,
        Mms.Part.TEXT
    )
    val selection = "${Mms.Part.MSG_ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    val messageAttachment = MessageAttachment(id, "", arrayListOf())

    var attachmentNames: List<String>? = null
    var attachmentCount = 0
    queryCursor(uri, projection, selection, selectionArgs, showErrors = true) { cursor ->
        val partId = cursor.getLongValue(Mms._ID)
        val mimetype = cursor.getStringValue(Mms.Part.CONTENT_TYPE)
        if (mimetype == "text/plain") {
            messageAttachment.text = cursor
                .getStringValue(Mms.Part.TEXT)
                ?.take(MAX_MESSAGE_LENGTH)
                .orEmpty()
        } else if (mimetype.startsWith("image/") || mimetype.startsWith("video/")) {
            val fileUri = Uri.withAppendedPath(uri, partId.toString())
            messageAttachment.attachments.add(
                Attachment(
                    id = partId,
                    messageId = id,
                    uriString = fileUri.toString(),
                    mimetype = mimetype,
                    width = 0,
                    height = 0,
                    filename = ""
                )
            )
        } else if (mimetype != "application/smil") {
            val attachmentName = attachmentNames?.getOrNull(attachmentCount) ?: ""
            val attachment = Attachment(
                id = partId,
                messageId = id,
                uriString = Uri.withAppendedPath(uri, partId.toString()).toString(),
                mimetype = mimetype,
                width = 0,
                height = 0,
                filename = attachmentName
            )
            messageAttachment.attachments.add(attachment)
            attachmentCount++
        } else {
            val text = cursor.getStringValue(Mms.Part.TEXT)
            attachmentNames = try {
                parseAttachmentNames(text)
            } catch (e: XmlPullParserException) {
                e.printStackTrace()
                null
            }
        }
    }

    return messageAttachment
}

fun Context.getLatestMMS(): Message? {
    val sortOrder = "${Mms.DATE} DESC LIMIT 1"
    return getMMS(sortOrder = sortOrder).firstOrNull()
}

fun Context.getThreadSnippet(threadId: Long): String {
    val sortOrder = "date DESC LIMIT 1"
    
    // Get latest MMS
    val latestMms = getMMS(threadId, sortOrder).firstOrNull()
    val mmsDate = latestMms?.date ?: 0L
    val mmsBody = latestMms?.body ?: ""

    // Get latest SMS
    var smsDate = 0L
    var smsBody = ""
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(Sms.BODY, Sms.DATE)
    val selection = "${Sms.THREAD_ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        cursor?.use {
            if (cursor.moveToFirst()) {
                smsBody = cursor.getStringValue(Sms.BODY) ?: ""
                smsDate = cursor.getLongValue(Sms.DATE)
                if (smsDate.toString().length > 10) smsDate /= 1000
            }
        }
    } catch (_: Exception) {}

    // Compare and return the absolute latest
    return if (mmsDate.toLong() >= smsDate.toLong()) {
        mmsBody.ifEmpty { smsBody }
    } else {
        smsBody.ifEmpty { mmsBody }
    }
}

fun Context.getMessageRecipientAddress(messageId: Long): String {
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms.ADDRESS
    )

    val selection = "${Sms._ID} = ?"
    val selectionArgs = arrayOf(messageId.toString())

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getStringValue(Sms.ADDRESS)
            }
        }
    } catch (_: Exception) {
    }

    return ""
}

fun Context.getThreadParticipants(
    threadId: Long,
    contactsMap: HashMap<Int, SimpleContact>?,
): ArrayList<SimpleContact> {
    MessagingCache.participantsCache.get(threadId)?.let {
        return it.map { contact ->
            contact.copy(
                phoneNumbers = contact.phoneNumbers.toArrayList(),
                birthdays = contact.birthdays.toArrayList(),
                anniversaries = contact.anniversaries.toArrayList()
            )
        }.toArrayList()
    }

    val uri = "${MmsSms.CONTENT_CONVERSATIONS_URI}?simple=true".toUri()
    val projection = arrayOf(
        ThreadsColumns.RECIPIENT_IDS
    )
    val selection = "${Mms._ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    val participants = ArrayList<SimpleContact>()
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                val address = cursor.getStringValue(ThreadsColumns.RECIPIENT_IDS)
                address.split(" ").filter { it.areDigitsOnly() }.forEach {
                    val addressId = it.toInt()
                    if (contactsMap?.containsKey(addressId) == true) {
                        participants.add(contactsMap[addressId]!!)
                        return@forEach
                    }

                    val number = getPhoneNumberFromAddressId(addressId)
                    val namePhoto = getNameAndPhotoFromPhoneNumber(number)
                    val name = namePhoto.name
                    val photoUri = namePhoto.photoUri ?: ""
                    val phoneNumber = PhoneNumber(number, 0, "", number)
                    val contact = SimpleContact(
                        rawId = addressId,
                        contactId = addressId,
                        name = name,
                        photoUri = photoUri,
                        phoneNumbers = arrayListOf(phoneNumber),
                        birthdays = ArrayList(),
                        anniversaries = ArrayList()
                    )
                    participants.add(contact)
                }
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }

    MessagingCache.participantsCache.put(threadId, participants)
    return participants
}

fun Context.getThreadPhoneNumbers(recipientIds: List<Int>): ArrayList<String> {
    val numbers = ArrayList<String>()
    recipientIds.forEach {
        numbers.add(getPhoneNumberFromAddressId(it))
    }
    return numbers
}

/** Batched version of [getPhoneNumberFromAddressId]: one query for every id instead of one query per id. */
fun Context.getPhoneNumbersFromAddressIds(addressIds: Collection<Int>): Map<Int, String> {
    val distinctIds = addressIds.distinct()
    if (distinctIds.isEmpty()) return emptyMap()

    val result = HashMap<Int, String>(distinctIds.size)
    val uri = Uri.withAppendedPath(MmsSms.CONTENT_URI, "canonical-addresses")
    val projection = arrayOf(Mms._ID, Mms.Addr.ADDRESS)
    val selection = "${Mms._ID} IN (${distinctIds.joinToString(",") { "?" }})"
    val selectionArgs = distinctIds.map { it.toString() }.toTypedArray()

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                do {
                    result[cursor.getIntValue(Mms._ID)] = cursor.getStringValue(Mms.Addr.ADDRESS) ?: ""
                } while (cursor.moveToNext())
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }

    return result
}

fun Context.getThreadContactNames(
    phoneNumbers: List<String>,
    privateContacts: ArrayList<SimpleContact>,
): ArrayList<String> {
    val names = ArrayList<String>()
    phoneNumbers.forEach { number ->
        // Reuse the LruCache-backed lookup (getNameAndPhotoFromPhoneNumber) instead of
        // SimpleContactsHelper(this).getNameFromPhoneNumber, which issues an uncached
        // contacts-provider query on every call -- previously once per recipient per
        // conversation, a dominant cost when loading a large conversation list.
        val name = getNameAndPhotoFromPhoneNumber(number).name
        if (name != number) {
            names.add(name)
        } else {
            val privateContact = privateContacts.firstOrNull { it.doesHavePhoneNumber(number) }
            if (privateContact == null) {
                names.add(name)
            } else {
                names.add(privateContact.name)
            }
        }
    }
    return names
}

fun Context.getPhoneNumberFromAddressId(canonicalAddressId: Int): String {
    val uri = Uri.withAppendedPath(MmsSms.CONTENT_URI, "canonical-addresses")
    val projection = arrayOf(
        Mms.Addr.ADDRESS
    )

    val selection = "${Mms._ID} = ?"
    val selectionArgs = arrayOf(canonicalAddressId.toString())
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getStringValue(Mms.Addr.ADDRESS)
            }
        }
    } catch (e: Exception) {
        showErrorToast(e)
    }
    return ""
}

/**
 * Last time each number was messaged, keyed by its comparable form. Used to float the
 * people you actually talk to above everyone else, both in the idle contact list and in
 * search results.
 */
/**
 * Full-text search over every SMS on the device.
 *
 * The Room `messages` table only ever receives messages that arrive while the app is
 * installed, so searching it missed the entire existing history. The system provider is
 * the real source of truth and is what gets queried here.
 */
fun Context.searchMessagesInProvider(query: String, limit: Int = 200): ArrayList<Message> {
    val messages = ArrayList<Message>()
    if (query.isBlank()) return messages

    val projection = arrayOf(
        Sms._ID,
        Sms.BODY,
        Sms.TYPE,
        Sms.ADDRESS,
        Sms.DATE,
        Sms.READ,
        Sms.THREAD_ID,
        Sms.STATUS,
        Sms.SUBSCRIPTION_ID
    )
    val selection = "${Sms.BODY} LIKE ?"
    val selectionArgs = arrayOf("%$query%")
    val sortOrder = "${Sms.DATE} DESC LIMIT $limit"
    val blockedNumbers = blockedNumbersSnapshot()

    try {
        queryCursor(Sms.CONTENT_URI, projection, selection, selectionArgs, sortOrder) { cursor ->
            val senderNumber = cursor.getStringValue(Sms.ADDRESS) ?: return@queryCursor
            if (isNumberBlockedIn(senderNumber, blockedNumbers)) return@queryCursor

            val id = cursor.getLongValue(Sms._ID)
            val body = cursor.getStringValue(Sms.BODY) ?: ""
            val type = cursor.getIntValue(Sms.TYPE)
            val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
            var date = cursor.getLongValue(Sms.DATE)
            if (date.toString().length > 10) {
                date /= 1000
            }

            messages.add(
                Message(
                    id = id,
                    body = body,
                    type = type,
                    status = cursor.getIntValue(Sms.STATUS),
                    participants = ArrayList(),
                    date = date.toInt(),
                    read = cursor.getIntValue(Sms.READ) == 1,
                    threadId = cursor.getLongValue(Sms.THREAD_ID),
                    isMMS = false,
                    attachment = null,
                    senderPhoneNumber = senderNumber,
                    senderName = namePhoto.name,
                    senderPhotoUri = namePhoto.photoUri ?: "",
                    subscriptionId = cursor.getIntValue(Sms.SUBSCRIPTION_ID)
                )
            )
        }
    } catch (_: Exception) {
    }

    return messages
}

/**
 * Newest message body per thread, for the whole list, in one pass.
 *
 * The alternative — asking per row while binding — meant two queries for every visible
 * card on every scroll. Reading it all up front keeps that cost to a single query and,
 * because it is recomputed whenever the list is rebuilt, it cannot go stale the way a
 * lazily filled cache does.
 */
fun Context.getLatestSnippets(limit: Int = 2000): Map<Long, Pair<String, Int>> {
    val snippets = HashMap<Long, Pair<String, Int>>()
    val projection = arrayOf(Sms.THREAD_ID, Sms.BODY, Sms.TYPE)
    val sortOrder = "${Sms.DATE} DESC LIMIT $limit"

    try {
        queryCursor(Sms.CONTENT_URI, projection, null, null, sortOrder) { cursor ->
            val threadId = cursor.getLongValue(Sms.THREAD_ID)
            // Rows arrive newest first, so the first hit for a thread is its latest.
            if (!snippets.containsKey(threadId)) {
                val body = cursor.getStringValue(Sms.BODY) ?: ""
                snippets[threadId] = body to cursor.getIntValue(Sms.TYPE)
            }
        }
    } catch (_: Exception) {
    }

    return snippets
}

fun Context.getContactRecency(limit: Int = 500): Map<String, Long> {
    val recency = HashMap<String, Long>()

    fun record(number: String?, date: Long) {
        if (number.isNullOrBlank()) return
        val key = SystemBlockedNumbers.comparable(number).ifEmpty { number.lowercase() }
        // Keep the newest contact across both sources.
        if ((recency[key] ?: 0L) < date) {
            recency[key] = date
        }
    }

    try {
        queryCursor(
            Sms.CONTENT_URI,
            arrayOf(Sms.ADDRESS, Sms.DATE),
            null,
            null,
            "${Sms.DATE} DESC LIMIT $limit"
        ) { cursor ->
            record(cursor.getStringValue(Sms.ADDRESS), cursor.getLongValue(Sms.DATE))
        }
    } catch (_: Exception) {
    }

    // Recent calls count as "recently used" too, but the permission is optional: without
    // it the ranking simply falls back to message history.
    if (hasPermission(PERMISSION_READ_CALL_LOG)) {
        try {
            queryCursor(
                android.provider.CallLog.Calls.CONTENT_URI,
                arrayOf(
                    android.provider.CallLog.Calls.NUMBER,
                    android.provider.CallLog.Calls.DATE
                ),
                null,
                null,
                "${android.provider.CallLog.Calls.DATE} DESC LIMIT $limit"
            ) { cursor ->
                record(
                    cursor.getStringValue(android.provider.CallLog.Calls.NUMBER),
                    cursor.getLongValue(android.provider.CallLog.Calls.DATE)
                )
            }
        } catch (_: Exception) {
        }
    }

    return recency
}

fun Context.getSuggestedContacts(
    privateContacts: ArrayList<SimpleContact>,
): ArrayList<SimpleContact> {
    val contacts = ArrayList<SimpleContact>()
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(
        Sms.ADDRESS
    )

    val sortOrder = "${Sms.DATE} DESC LIMIT 50"
    val blockedNumbers = blockedNumbersSnapshot()

    queryCursor(uri, projection, null, null, sortOrder, showErrors = true) { cursor ->
        val senderNumber = cursor.getStringValue(Sms.ADDRESS) ?: return@queryCursor
        val namePhoto = getNameAndPhotoFromPhoneNumber(senderNumber)
        var senderName = namePhoto.name
        var photoUri = namePhoto.photoUri ?: ""
        if (isNumberBlockedIn(senderNumber, blockedNumbers)) {
            return@queryCursor
        } else if (namePhoto.name == senderNumber) {
            if (privateContacts.isNotEmpty()) {
                val privateContact = privateContacts.firstOrNull {
                    it.phoneNumbers.first().normalizedNumber == senderNumber
                }
                if (privateContact != null) {
                    senderName = privateContact.name
                    photoUri = privateContact.photoUri
                } else {
                    return@queryCursor
                }
            } else {
                return@queryCursor
            }
        }

        val phoneNumber = PhoneNumber(senderNumber, 0, "", senderNumber)
        val contact = SimpleContact(
            rawId = 0,
            contactId = 0,
            name = senderName,
            photoUri = photoUri,
            phoneNumbers = arrayListOf(phoneNumber),
            birthdays = ArrayList(),
            anniversaries = ArrayList()
        )
        if (!contacts.map { it.phoneNumbers.first().normalizedNumber.trimToComparableNumber() }
                .contains(senderNumber.trimToComparableNumber())) {
            contacts.add(contact)
        }
    }

    return contacts
}

/**
 * Every number in the device address book, reduced to the comparable trailing-digits form
 * so the "مخاطبین" filter can decide with a single set lookup per conversation.
 */
fun Context.getContactNumbersSnapshot(): Set<String> {
    if (!hasPermission(PERMISSION_READ_CONTACTS)) return emptySet()

    val numbers = HashSet<String>()
    try {
        contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.NORMALIZED_NUMBER, Phone.NUMBER),
            null,
            null,
            null
        )?.use { cursor ->
            val normalizedIndex = cursor.getColumnIndex(Phone.NORMALIZED_NUMBER)
            val rawIndex = cursor.getColumnIndex(Phone.NUMBER)
            while (cursor.moveToNext()) {
                // NORMALIZED_NUMBER is null for numbers the platform could not parse into
                // E.164, so the raw one it was entered with is the fallback.
                val number = normalizedIndex.takeIf { it >= 0 }
                    ?.let { cursor.getString(it) }
                    ?.takeIf { it.isNotBlank() }
                    ?: rawIndex.takeIf { it >= 0 }?.let { cursor.getString(it) }.orEmpty()

                val comparable = SystemBlockedNumbers.comparable(number)
                if (comparable.isNotEmpty()) numbers.add(comparable)
            }
        }
    } catch (_: Exception) {
        return numbers
    }
    return numbers
}

fun Context.getNameAndPhotoFromPhoneNumber(number: String): NamePhoto {
    MessagingCache.namePhoto.get(number)?.let { return it }
    if (!hasPermission(PERMISSION_READ_CONTACTS)) {
        return NamePhoto(number, null)
    }

    val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
    val projection = arrayOf(
        PhoneLookup.DISPLAY_NAME,
        PhoneLookup.PHOTO_URI
    )

    val result = try {
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor.use {
            if (cursor?.moveToFirst() == true) {
                val name = cursor.getStringValue(PhoneLookup.DISPLAY_NAME)
                val photoUri = cursor.getStringValue(PhoneLookup.PHOTO_URI)
                NamePhoto(name, photoUri)
            } else {
                NamePhoto(number, null)
            }
        }
    } catch (_: Exception) {
        NamePhoto(number, null)
    }

    MessagingCache.namePhoto.put(number, result)
    return result
}

fun Context.insertNewSMS(
    address: String,
    subject: String,
    body: String,
    date: Long,
    read: Int,
    threadId: Long,
    type: Int,
    subscriptionId: Int,
): Long {
    val uri = Sms.CONTENT_URI
    val contentValues = ContentValues().apply {
        put(Sms.ADDRESS, address)
        put(Sms.SUBJECT, subject)
        put(Sms.BODY, body)
        put(Sms.DATE, date)
        put(Sms.READ, read)
        put(Sms.THREAD_ID, threadId)
        put(Sms.TYPE, type)
        put(Sms.SUBSCRIPTION_ID, subscriptionId)
    }

    return try {
        val newUri = contentResolver.insert(uri, contentValues)
        newUri?.lastPathSegment?.toLong() ?: 0L
    } catch (_: Exception) {
        0L
    }
}

fun Context.removeAllArchivedConversations(callback: (() -> Unit)? = null) {
    ensureBackgroundThread {
        try {
            for (conversation in conversationsDB.getAllArchived()) {
                deleteConversation(conversation.threadId)
            }
            toast(R.string.archive_emptied_successfully)
            callback?.invoke()
        } catch (_: Exception) {
            toast(org.fossify.commons.R.string.unknown_error_occurred)
        }
    }
}

fun Context.deleteConversation(threadId: Long) {
    var uri = Sms.CONTENT_URI
    val selection = "${Sms.THREAD_ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    try {
        contentResolver.delete(uri, selection, selectionArgs)
    } catch (e: Exception) {
        showErrorToast(e)
    }

    uri = Mms.CONTENT_URI
    try {
        contentResolver.delete(uri, selection, selectionArgs)
    } catch (e: Exception) {
        e.printStackTrace()
    }

    conversationsDB.deleteThreadId(threadId)
    messagesDB.deleteThreadMessages(threadId)
    MessagingCache.participantsCache.remove(threadId)

    if (config.customNotifications.contains(threadId.toString())) {
        config.removeCustomNotificationsByThreadId(threadId)
        notificationManager.deleteNotificationChannel(threadId.toString())
    }
    if(shortcutHelper.getShortcut(threadId) != null) {
        shortcutHelper.removeShortcutForThread(threadId)
    }
}

/**
 * Soft-deletes a whole conversation: every message moves into the recycle bin instead of
 * being removed from the telephony provider, exactly like a single-message delete does when
 * [Config.useRecycleBin] is on. The conversation still drops off the main list and its
 * notification channel/shortcut are cleared, but [restoreAllMessagesFromRecycleBinForConversation]
 * can bring it back intact.
 */
fun Context.moveConversationToRecycleBin(threadId: Long) {
    try {
        messagesDB.getThreadMessages(threadId).forEach { moveMessageToRecycleBin(it.id) }
        enforceRecycleBinThreadLimit()
    } catch (e: Exception) {
        showErrorToast(e)
    }

    conversationsDB.deleteThreadId(threadId)
    MessagingCache.participantsCache.remove(threadId)

    if (config.customNotifications.contains(threadId.toString())) {
        config.removeCustomNotificationsByThreadId(threadId)
        notificationManager.deleteNotificationChannel(threadId.toString())
    }
    if (shortcutHelper.getShortcut(threadId) != null) {
        shortcutHelper.removeShortcutForThread(threadId)
    }
}

/** Routes a user-initiated "delete this conversation" through the recycle bin when enabled;
 *  callers that need a real permanent purge (emptying the recycle bin/archive) must keep
 *  calling [deleteConversation] directly. */
fun Context.deleteOrRecycleConversation(threadId: Long) {
    if (config.useRecycleBin) {
        moveConversationToRecycleBin(threadId)
    } else {
        deleteConversation(threadId)
    }
}

fun Context.checkAndDeleteOldRecycleBinMessages(callback: (() -> Unit)? = null) {
    if (
        config.useRecycleBin
        && config.lastRecycleBinCheck < System.currentTimeMillis() - DAY_SECONDS * 1000
    ) {
        config.lastRecycleBinCheck = System.currentTimeMillis()
        ensureBackgroundThread {
            try {
                messagesDB.getOldRecycleBinMessages(
                    timestamp = System.currentTimeMillis() - MONTH_SECONDS * 1000L
                ).forEach { message ->
                    deleteMessage(message.id, message.isMMS)
                }
                callback?.invoke()
            } catch (_: Exception) {
            }
        }
    }
}

fun Context.emptyMessagesRecycleBin() {
    val messages = messagesDB.getAllRecycleBinMessages()
    for (message in messages) {
        deleteMessage(message.id, message.isMMS)
    }
}

fun Context.emptyMessagesRecycleBinForConversation(threadId: Long) {
    val messages = messagesDB.getThreadMessagesFromRecycleBin(threadId)
    for (message in messages) {
        deleteMessage(message.id, message.isMMS)
    }
}

fun Context.restoreAllMessagesFromRecycleBinForConversation(threadId: Long) {
    messagesDB.deleteThreadMessagesFromRecycleBin(threadId)
}

/**
 * Caps the recycle bin at [RECYCLE_BIN_THREAD_LIMIT] chats. Counting chats rather than
 * messages is what makes the bin predictable: deleting one long conversation would otherwise
 * evict every other chat in it. Once the limit is passed, the chats whose newest deletion is
 * oldest are dropped for good, one whole chat at a time.
 */
fun Context.enforceRecycleBinThreadLimit() {
    try {
        val threadIds = messagesDB.getRecycleBinThreadIdsNewestFirst()
        if (threadIds.size <= RECYCLE_BIN_THREAD_LIMIT) return
        threadIds.drop(RECYCLE_BIN_THREAD_LIMIT).forEach { threadId ->
            messagesDB.getThreadMessagesFromRecycleBin(threadId).forEach { message ->
                deleteMessage(message.id, message.isMMS)
            }
        }
    } catch (_: Exception) {
    }
}

fun Context.moveMessageToRecycleBin(id: Long) {
    try {
        messagesDB.insertRecycleBinEntry(RecycleBinMessage(id, System.currentTimeMillis()))
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.restoreMessageFromRecycleBin(id: Long) {
    try {
        messagesDB.deleteFromRecycleBin(id)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.updateConversationArchivedStatus(threadId: Long, archived: Boolean) {
    val uri = Threads.CONTENT_URI
    val values = ContentValues().apply {
        put(Threads.ARCHIVED, archived)
    }
    val selection = "${Threads._ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    try {
        contentResolver.update(uri, values, selection, selectionArgs)
    } catch (sqliteException: SQLiteException) {
        if (
            sqliteException.message?.contains("no such column: archived") == true
            && config.isArchiveAvailable
        ) {
            config.isArchiveAvailable = false
            return
        } else {
            throw sqliteException
        }
    }
    if (archived) {
        conversationsDB.moveToArchive(threadId)
    } else {
        conversationsDB.unarchive(threadId)
    }
}

fun Context.deleteMessage(id: Long, isMMS: Boolean) {
    val uri = if (isMMS) Mms.CONTENT_URI else Sms.CONTENT_URI
    val selection = "${Sms._ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    try {
        contentResolver.delete(uri, selection, selectionArgs)
        messagesDB.delete(id)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.deleteScheduledMessage(messageId: Long) {
    try {
        messagesDB.delete(messageId)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

fun Context.markMessageRead(id: Long, isMMS: Boolean) {
    val uri = if (isMMS) Mms.CONTENT_URI else Sms.CONTENT_URI
    val contentValues = ContentValues().apply {
        put(Sms.READ, 1)
        put(Sms.SEEN, 1)
    }
    val selection = "${Sms._ID} = ?"
    val selectionArgs = arrayOf(id.toString())
    contentResolver.update(uri, contentValues, selection, selectionArgs)
    messagesDB.markRead(id)
}

fun Context.markThreadMessagesRead(threadId: Long) {
    val id = threadId.toString()

    val smsValues = ContentValues().apply {
        put(Sms.READ, 1)
        put(Sms.SEEN, 1)
    }
    val smsSelection = "${Sms.THREAD_ID}=? AND ${Sms.TYPE}=? AND (${Sms.READ}=? OR ${Sms.SEEN}=?)"
    val smsArgs = arrayOf(id, Sms.MESSAGE_TYPE_INBOX.toString(), "0", "0")
    contentResolver.update(Sms.CONTENT_URI, smsValues, smsSelection, smsArgs)

    val mmsValues = ContentValues().apply {
        put(Mms.READ, 1)
        put(Mms.SEEN, 1)
    }
    val mmsSelection = "${Mms.THREAD_ID}=? AND ${Mms.MESSAGE_BOX}=? AND (${Mms.READ}=? OR ${Mms.SEEN}=?)"
    val mmsArgs = arrayOf(id, Mms.MESSAGE_BOX_INBOX.toString(), "0", "0")
    contentResolver.update(Mms.CONTENT_URI, mmsValues, mmsSelection, mmsArgs)

    messagesDB.markThreadRead(threadId)
    conversationsDB.markRead(threadId)

    // Opening a thread is exactly the moment its cached row is most likely to be behind:
    // the message that produced the notification landed while nothing was refreshing the
    // conversation list, so the row still describes the previous message. Pull this single
    // thread straight from telephony now -- one thread-scoped query, off any list-loading
    // path -- so the snippet and date are already correct when the user backs out, instead
    // of staying stale until the next full sync catches up.
    runCatching {
        getConversations(threadId).firstOrNull()?.let { insertOrUpdateConversation(it) }
    }
}

fun Context.markThreadMessagesUnread(threadId: Long) {
    arrayOf(Sms.CONTENT_URI, Mms.CONTENT_URI).forEach { uri ->
        val contentValues = ContentValues().apply {
            put(Sms.READ, 0)
            put(Sms.SEEN, 0)
        }
        val selection = "${Sms.THREAD_ID} = ?"
        val selectionArgs = arrayOf(threadId.toString())
        contentResolver.update(uri, contentValues, selection, selectionArgs)
    }
    conversationsDB.markUnread(threadId)
} 

@SuppressLint("NewApi")
fun Context.getThreadId(address: String): Long {
    return try {
        Threads.getOrCreateThreadId(this, address)
    } catch (_: Exception) {
        0L
    }
}

@SuppressLint("NewApi")
fun Context.getThreadId(addresses: Set<String>): Long {
    return try {
        Threads.getOrCreateThreadId(this, addresses)
    } catch (_: Exception) {
        0L
    }
}

fun Context.showReceivedMessageNotification(
    messageId: Long,
    address: String,
    senderName: String,
    body: String,
    threadId: Long,
    bitmap: Bitmap?,
) {
    if (com.texto.sms.activities.ThreadActivity.currentThreadId == threadId) {
        return
    }

    notificationHelper.showMessageNotification(
        messageId = messageId,
        address = address,
        body = body,
        threadId = threadId,
        bitmap = bitmap,
        sender = senderName
    )
}

fun Context.getNameFromAddress(address: String, privateCursor: Cursor?): String {
    var sender = getNameAndPhotoFromPhoneNumber(address).name
    if (address == sender) {
        val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
        sender = privateContacts.firstOrNull { it.doesHavePhoneNumber(address) }?.name ?: address
    }
    return sender
}

fun Context.getContactFromAddress(address: String, callback: ((contact: SimpleContact?) -> Unit)) {
    val privateCursor = getMyContactsCursor(false, true)
    SimpleContactsHelper(this).getAvailableContacts(false) {
        val contact = it.firstOrNull { it.doesHavePhoneNumber(address) }
        if (contact == null) {
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val privateContact = privateContacts.firstOrNull { it.doesHavePhoneNumber(address) }
            callback(privateContact)
        } else {
            callback(contact)
        }
    }
}

fun Context.getNotificationBitmap(photoUri: String): Bitmap? {
    val size = resources.getDimension(R.dimen.notification_large_icon_size).toInt()
    if (photoUri.isEmpty()) {
        return null
    }

    val options = RequestOptions()
        .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
        .centerCrop()

    return try {
        Glide.with(this)
            .asBitmap()
            .load(photoUri)
            .apply(options)
            .apply(RequestOptions.circleCropTransform())
            .into(size, size)
            .get()
    } catch (_: Exception) {
        null
    }
}

fun Context.getLatestMessageType(threadId: Long): Int {
    val uri = Sms.CONTENT_URI
    val projection = arrayOf(Sms.TYPE)
    val selection = "${Sms.THREAD_ID} = ?"
    val selectionArgs = arrayOf(threadId.toString())
    val sortOrder = "${Sms.DATE} DESC LIMIT 1"
    
    var type = -1
    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
        cursor?.use {
            if (it.moveToFirst()) {
                type = it.getIntValue(Sms.TYPE)
            }
        }
    } catch (_: Exception) {}
    
    // Also check MMS
    val mmsUri = Mms.CONTENT_URI
    val mmsProjection = arrayOf(Mms.MESSAGE_BOX, Mms.DATE)
    try {
        val mmsCursor = contentResolver.query(mmsUri, mmsProjection, selection, selectionArgs, "${Mms.DATE} DESC LIMIT 1")
        mmsCursor?.use {
            if (it.moveToFirst()) {
                // Simplified comparison - just take the very latest
                type = it.getIntValue(Mms.MESSAGE_BOX)
            }
        }
    } catch (_: Exception) {}
    
    return type
}

fun Context.removeDiacriticsIfNeeded(text: String): String {
    return if (config.useSimpleCharacters) text.normalizeString() else text
}

fun Context.getSmsDraft(threadId: Long): String {
    val draft = try {
        draftsDB.getDraftById(threadId)
    } catch (_: Exception) {
        null
    }

    return draft?.body.orEmpty()
}

fun Context.getAllDrafts(): HashMap<Long, String> {
    val drafts = HashMap<Long, String>()
    try {
        draftsDB.getAll().forEach {
            drafts[it.threadId] = it.body
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return drafts
}

fun Context.saveSmsDraft(body: String, threadId: Long) {
    val draft = Draft(
        threadId = threadId,
        body = body,
        date = System.currentTimeMillis()
    )

    try {
        draftsDB.insertOrUpdate(draft)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun Context.deleteSmsDraft(threadId: Long) {
    try {
        draftsDB.delete(threadId)
    } catch (e: Exception) {
        e.printStackTrace()
        showErrorToast(e)
    }
}

fun Context.updateLastConversationMessage(threadId: Long) {
    updateLastConversationMessage(setOf(threadId))
}

fun Context.updateLastConversationMessage(threadIds: Iterable<Long>) {
    // update the date and the snippet of the threads, by triggering the
    // following Android code (which runs even if no messages are deleted):
    // https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/android14-release/src/com/android/providers/telephony/MmsSmsProvider.java#1409
    val uri = Threads.CONTENT_URI
    val selection =
        "1 = 0" // always-false condition, because we don't actually want to delete any messages
    try {
        contentResolver.delete(uri, selection, null)
        val allSystemConvs = getConversations()
        val toUpdate = allSystemConvs.filter { it.threadId in threadIds }
        if (toUpdate.isNotEmpty()) {
            // The date is allowed to move backwards here. This runs right after a message
            // was deleted, so the thread's newest message really is older than it was, and
            // clamping to the cached value would leave the conversation stuck at the top of
            // the list showing a timestamp for a message that no longer exists.
            insertOrUpdateConversations(toUpdate, keepNewestDate = false)
        }
    } catch (_: Exception) {
    }
}

/**
 * @param keepNewestDate when true, a thread whose incoming date is older than the cached one
 * keeps the cached date. That guards the periodic sync against a partially-populated read,
 * but it has to be off for updates that follow a deletion, where going backwards is correct.
 */
fun Context.insertOrUpdateConversations(
    conversations: List<Conversation>,
    keepNewestDate: Boolean = true,
) {
    val existing = conversationsDB.getNonArchived().associateBy { it.threadId }
    val updated = conversations.map { conv ->
        val cachedConv = existing[conv.threadId]
        var updatedConv = conv
        if (cachedConv != null) {
            if (keepNewestDate && cachedConv.date > updatedConv.date) {
                updatedConv.date = cachedConv.date
            }
            if (cachedConv.usesCustomTitle) {
                updatedConv = updatedConv.copy(title = cachedConv.title, usesCustomTitle = true)
            }
        }
        updatedConv
    }
    conversationsDB.insertOrUpdateAll(updated)
}

fun Context.getFileSizeFromUri(uri: Uri): Long {
    val assetFileDescriptor = try {
        contentResolver.openAssetFileDescriptor(uri, "r")
    } catch (_: FileNotFoundException) {
        null
    }

    // uses ParcelFileDescriptor#getStatSize underneath if failed
    val length = assetFileDescriptor?.use { it.length } ?: FILE_SIZE_NONE
    if (length != -1L) {
        return length
    }

    // if "content://" uri scheme, try contentResolver table
    if (uri.scheme.equals(ContentResolver.SCHEME_CONTENT)) {
        return contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                // maybe shouldn't trust ContentResolver for size:
                // https://stackoverflow.com/questions/48302972/content-resolver-returns-wrong-size
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex == -1) {
                    return@use FILE_SIZE_NONE
                }
                cursor.moveToFirst()
                return try {
                    cursor.getLong(sizeIndex)
                } catch (_: Throwable) {
                    FILE_SIZE_NONE
                }
            } ?: FILE_SIZE_NONE
    } else {
        return FILE_SIZE_NONE
    }
}

// fix a glitch at enabling Release version minifying from 5.12.3
// reset messages in 5.14.3 again, as PhoneNumber is no longer minified
// reset messages in 5.19.1 again, as SimpleContact is no longer minified
fun Context.clearAllMessagesIfNeeded(callback: () -> Unit) {
    if (!config.wasDbCleared) {
        ensureBackgroundThread {
            messagesDB.deleteAll()
            config.wasDbCleared = true
            Handler(Looper.getMainLooper()).post(callback)
        }
    } else {
        callback()
    }
}

fun Context.subscriptionManagerCompat(): SubscriptionManager {
    return getSystemService(SubscriptionManager::class.java)
}

fun Context.insertOrUpdateConversation(
    conversation: Conversation,
    cachedConv: Conversation? = conversationsDB.getConversationWithThreadId(conversation.threadId),
) {
    var updatedConv = conversation
    if (cachedConv != null) {
        // Anti-Regression: If the cached conversation has a NEWER date than the sync data, keep the newer date.
        // This handles system provider lag during active messaging.
        if (cachedConv.date > updatedConv.date) {
            updatedConv.date = cachedConv.date
        }

        if (cachedConv.usesCustomTitle) {
            updatedConv = updatedConv.copy(
                title = cachedConv.title,
                usesCustomTitle = true
            )
        }
    }
    conversationsDB.insertOrUpdate(updatedConv)
}

fun Context.renameConversation(conversation: Conversation, newTitle: String): Conversation {
    val updatedConv = conversation.copy(title = newTitle, usesCustomTitle = true)
    try {
        conversationsDB.insertOrUpdate(updatedConv)
        ensureBackgroundThread {
            shortcutHelper.createOrUpdateShortcut(updatedConv)
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return updatedConv
}

fun Context.updateScheduledMessagesThreadId(messages: List<Message>, newThreadId: Long) {
    val scheduledMessages = messages.map { it.copy(threadId = newThreadId) }.toTypedArray()
    messagesDB.insertMessages(*scheduledMessages)
}

fun Context.clearExpiredScheduledMessages(threadId: Long, messagesToDelete: List<Message>? = null) {
    val messages = messagesToDelete ?: messagesDB.getScheduledThreadMessages(threadId)
    val cutoff = System.currentTimeMillis() - 1.minutes.inWholeMilliseconds

    try {
        messages.filter { it.isScheduled && it.millis() < cutoff }.forEach { msg ->
            messagesDB.delete(msg.id)
        }
        if (messages.filterNot { it.isScheduled && it.millis() < cutoff }.isEmpty()) {
            // delete empty temporary thread
            val conversation = conversationsDB.getConversationWithThreadId(threadId)
            if (conversation != null && conversation.isScheduled) {
                conversationsDB.deleteThreadId(threadId)
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        return
    }
}

fun Context.rescheduleAllScheduledMessages() {
    val scheduledMessages = try {
        messagesDB.getAllScheduledMessages()
    } catch (_: Exception) {
        return
    }

    scheduledMessages.forEach { message ->
        runCatching { scheduleMessage(message) }
    }
}

fun Context.getDefaultKeyboardHeight(): Int {
    return resources.getDimensionPixelSize(R.dimen.default_keyboard_height)
}

fun Context.shouldUnarchive(): Boolean {
    return config.isArchiveAvailable && !config.keepConversationsArchived
}

fun Context.copyToUri(src: Uri, dst: Uri) {
    contentResolver.openInputStream(src)?.use { input ->
        contentResolver.openOutputStream(dst, "rwt")?.use { out ->
            input.copyTo(out)
        }
    }
}
