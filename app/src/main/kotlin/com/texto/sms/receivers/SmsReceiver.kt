package com.texto.sms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import org.fossify.commons.extensions.baseConfig
import org.fossify.commons.extensions.getMyContactsCursor
import com.texto.sms.helpers.isNumberBlockedBySystem
import org.fossify.commons.helpers.ContactLookupResult
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.models.PhoneNumber
import org.fossify.commons.models.SimpleContact
import com.texto.sms.extensions.getConversations
import com.texto.sms.extensions.getNameFromAddress
import com.texto.sms.extensions.getNotificationBitmap
import com.texto.sms.extensions.getThreadId
import com.texto.sms.extensions.insertNewSMS
import com.texto.sms.extensions.insertOrUpdateConversation
import com.texto.sms.extensions.messagesDB
import com.texto.sms.extensions.shouldUnarchive
import com.texto.sms.extensions.showReceivedMessageNotification
import com.texto.sms.extensions.updateConversationArchivedStatus
import com.texto.sms.helpers.refreshConversations
import com.texto.sms.helpers.refreshMessages
import com.texto.sms.models.Message

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val appContext = context.applicationContext

        ensureBackgroundThread {
            try {
                // Nothing here logs the address or the body: this build ships as-is, so a
                // Log call would leave every incoming message readable in logcat.
                val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                if (parts.isEmpty()) return@ensureBackgroundThread

                val address = parts.first().originatingAddress.orEmpty()
                if (address.isBlank()) return@ensureBackgroundThread
                val subject = parts.last().pseudoSubject.orEmpty()
                val status = parts.last().status
                val body = buildString { parts.forEach { append(it.messageBody.orEmpty()) } }

                if (appContext.isNumberBlockedBySystem(address)) return@ensureBackgroundThread
                if (appContext.baseConfig.blockUnknownNumbers) {
                    val privateCursor =
                        appContext.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
                    val result = SimpleContactsHelper(appContext).existsSync(address, privateCursor)
                    if (result == ContactLookupResult.NotFound) return@ensureBackgroundThread
                }

                // The service centre's timestamp, not the moment this receiver ran. They are
                // the same for a message that arrives immediately, but everything the carrier
                // held while the phone was off or out of coverage would otherwise land stamped
                // "now" -- a whole backlog collapsing onto one time, in the wrong order.
                val date = parts.first().timestampMillis.takeIf { it > 0 }
                    ?: System.currentTimeMillis()
                val threadId = appContext.getThreadId(address)
                // EXTRA_SUBSCRIPTION_INDEX is the documented extra; "subscription" is an
                // OEM-specific one that is absent on many devices, which left dual-SIM
                // messages attributed to no SIM at all.
                val subscriptionId = intent.getIntExtra(
                    SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
                    intent.getIntExtra("subscription", SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                )

                handleMessageSync(
                    context = appContext,
                    address = address,
                    subject = subject,
                    body = body,
                    date = date,
                    threadId = threadId,
                    subscriptionId = subscriptionId,
                    status = status
                )
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleMessageSync(
        context: Context,
        address: String,
        subject: String,
        body: String,
        date: Long,
        read: Int = 0,
        threadId: Long,
        type: Int = Telephony.Sms.MESSAGE_TYPE_INBOX,
        subscriptionId: Int,
        status: Int
    ) {
        val photoUri = SimpleContactsHelper(context).getPhotoUriFromPhoneNumber(address)
        val bitmap = context.getNotificationBitmap(photoUri)

        val newMessageId = context.insertNewSMS(
            address = address,
            subject = subject,
            body = body,
            date = date,
            read = read,
            threadId = threadId,
            type = type,
            subscriptionId = subscriptionId
        )

        context.getConversations(threadId).firstOrNull()?.let { conv ->
            runCatching { context.insertOrUpdateConversation(conv) }
        }

        val senderName = context.getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true).use {
            context.getNameFromAddress(address, it)
        }

        val participant = SimpleContact(
            rawId = 0,
            contactId = 0,
            name = senderName,
            photoUri = photoUri,
            phoneNumbers = arrayListOf(PhoneNumber(value = address, type = 0, label = "", normalizedNumber = address)),
            birthdays = ArrayList(),
            anniversaries = ArrayList()
        )

        val message = Message(
            id = newMessageId,
            body = body,
            type = type,
            status = status,
            participants = arrayListOf(participant),
            date = (date / 1000).toInt(),
            read = false,
            threadId = threadId,
            isMMS = false,
            attachment = null,
            senderPhoneNumber = address,
            senderName = senderName,
            senderPhotoUri = photoUri,
            subscriptionId = subscriptionId
        )

        context.messagesDB.insertOrUpdate(message)

        if (context.shouldUnarchive()) {
            context.updateConversationArchivedStatus(threadId, false)
        }

        refreshMessages()
        refreshConversations()
        context.showReceivedMessageNotification(
            messageId = newMessageId,
            address = address,
            senderName = senderName,
            body = body,
            threadId = threadId,
            bitmap = bitmap
        )
    }
}
