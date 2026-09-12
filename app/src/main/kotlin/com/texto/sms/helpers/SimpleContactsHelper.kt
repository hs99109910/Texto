package com.texto.sms.helpers

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.PhoneLookup
import com.texto.sms.extensions.ensureBackgroundThread
import com.texto.sms.extensions.getIntValue
import com.texto.sms.extensions.getStringValue
import com.texto.sms.extensions.getStringValueOr
import com.texto.sms.extensions.normalizePhoneNumber
import com.texto.sms.extensions.queryCursor
import com.texto.sms.models.PhoneNumber
import com.texto.sms.models.SimpleContact

/** What a lookup of an unknown number can say, which is more than a boolean. */
sealed class ContactLookupResult {
    data object NotFound : ContactLookupResult()
    data class Found(val name: String, val photoUri: String) : ContactLookupResult()
}

/**
 * The address book, reduced to the six questions this app asks of it.
 *
 * Everything here goes through the phone table rather than the raw data table: a contact with
 * no number cannot be messaged and so has no place in any list here, and asking Phone for the
 * name and the number together saves the join that reading Data by mimetype would need.
 */
class SimpleContactsHelper(private val context: Context) {

    /**
     * Every contact that has at least one number, one entry per contact.
     *
     * The callback lands on the calling thread when that is already a background one, which
     * is what `ensureBackgroundThread` guarantees: three of the four callers are themselves
     * inside a background pass and would otherwise hop threads for nothing.
     */
    fun getAvailableContacts(favoritesOnly: Boolean, callback: (ArrayList<SimpleContact>) -> Unit) {
        ensureBackgroundThread {
            callback(getContactsSync(favoritesOnly))
        }
    }

    private fun getContactsSync(favoritesOnly: Boolean): ArrayList<SimpleContact> {
        val byContactId = LinkedHashMap<Int, SimpleContact>()
        val projection = arrayOf(
            Phone.CONTACT_ID,
            ContactsContract.Data.RAW_CONTACT_ID,
            Phone.DISPLAY_NAME,
            Phone.PHOTO_THUMBNAIL_URI,
            Phone.NUMBER,
            Phone.NORMALIZED_NUMBER,
            Phone.TYPE,
            Phone.LABEL,
            Phone.IS_PRIMARY,
        )
        val selection = if (favoritesOnly) "${ContactsContract.Data.STARRED} = 1" else null

        context.queryCursor(Phone.CONTENT_URI, projection, selection) { cursor ->
            val contactId = cursor.getIntValue(Phone.CONTACT_ID)
            val number = cursor.getStringValueOr(Phone.NUMBER, "")
            if (number.isEmpty()) return@queryCursor

            val phone = PhoneNumber(
                value = number,
                type = cursor.getIntValue(Phone.TYPE),
                label = cursor.getStringValueOr(Phone.LABEL, ""),
                // The provider fills NORMALIZED_NUMBER only when it could parse the number,
                // so a short code or a service sender arrives with it empty and has to be
                // normalized here or it would never match an incoming address.
                normalizedNumber = cursor.getStringValueOr(Phone.NORMALIZED_NUMBER, "")
                    .ifEmpty { number.normalizePhoneNumber() },
                isPrimary = cursor.getIntValue(Phone.IS_PRIMARY) != 0,
            )

            val existing = byContactId[contactId]
            if (existing != null) {
                existing.phoneNumbers.add(phone)
            } else {
                byContactId[contactId] = SimpleContact(
                    rawId = cursor.getIntValue(ContactsContract.Data.RAW_CONTACT_ID),
                    contactId = contactId,
                    name = cursor.getStringValueOr(Phone.DISPLAY_NAME, ""),
                    photoUri = cursor.getStringValueOr(Phone.PHOTO_THUMBNAIL_URI, ""),
                    phoneNumbers = arrayListOf(phone),
                )
            }
        }

        return ArrayList(byContactId.values.filter { it.name.isNotEmpty() }.sorted())
    }

    /** The display name for [number], or the number itself when nobody owns it. */
    fun getNameFromPhoneNumber(number: String): String =
        lookup(number)?.let { it.first.ifEmpty { number } } ?: number

    fun getPhotoUriFromPhoneNumber(number: String): String = lookup(number)?.second ?: ""

    /**
     * Whether [number] is in the address book, and who it is.
     *
     * Used by the two receivers to decide whether a message is from a stranger, so it runs on
     * the receiver's own thread and must not go through a callback.
     */
    fun existsSync(number: String, cursor: android.database.Cursor? = null): ContactLookupResult {
        val found = lookup(number) ?: return ContactLookupResult.NotFound
        return ContactLookupResult.Found(name = found.first, photoUri = found.second)
    }

    /**
     * PhoneLookup rather than a LIKE over the phone table: the provider keeps its own index of
     * numbers in every shape it has seen, which is the only way a `+98...` header matches a
     * `0912...` address book entry without reimplementing the country rules.
     */
    private fun lookup(number: String): Pair<String, String>? {
        if (number.isEmpty()) return null
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(PhoneLookup.DISPLAY_NAME, PhoneLookup.PHOTO_URI)
        var result: Pair<String, String>? = null
        context.queryCursor(uri, projection) { cursor ->
            if (result == null) {
                result = Pair(
                    cursor.getStringValueOr(PhoneLookup.DISPLAY_NAME, ""),
                    cursor.getStringValueOr(PhoneLookup.PHOTO_URI, ""),
                )
            }
        }
        return result
    }

    /**
     * The lookup key for a raw contact, which is what a view-contact intent wants: a raw id is
     * this device`s and this account`s, a lookup key survives a merge or a resync.
     */
    fun getContactLookupKey(contactId: String): String {
        var key = ""
        context.queryCursor(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(ContactsContract.Data.CONTACT_ID, ContactsContract.Data.LOOKUP_KEY),
            "${ContactsContract.Data.RAW_CONTACT_ID} = ?",
            arrayOf(contactId),
        ) { cursor ->
            if (key.isEmpty()) key = cursor.getStringValue(ContactsContract.Data.LOOKUP_KEY).orEmpty()
        }
        return key
    }

    /** The app`s own monogram, for the surfaces that can only take a Bitmap. */
    fun getContactLetterIcon(name: String): Bitmap = TextoAvatars.letterBitmap(context, name)
}
