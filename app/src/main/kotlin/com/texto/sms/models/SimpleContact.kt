package com.texto.sms.models

import android.telephony.PhoneNumberUtils
import com.texto.sms.extensions.normalizePhoneNumber

/**
 * A contact reduced to what a messaging app asks of one: a name, a picture and some numbers.
 *
 * Field names and order are commons', for the Gson reason spelled out on [PhoneNumber].
 * `birthdays` and `anniversaries` are carried rather than used -- they are in the stored JSON
 * of every existing install, and dropping them from the class would make Gson throw the dates
 * away on the first rewrite of a row.
 */
data class SimpleContact(
    val rawId: Int,
    val contactId: Int,
    var name: String,
    var photoUri: String,
    var phoneNumbers: ArrayList<PhoneNumber>,
    var birthdays: ArrayList<String> = ArrayList(),
    var anniversaries: ArrayList<String> = ArrayList(),
) : Comparable<SimpleContact> {

    /**
     * Loose comparison, and deliberately so: the number on a message is whatever the carrier
     * put in the header, and the one in the address book is however the user typed it. The
     * platform's own comparison is what decides that +98 912 x and 0912 x are one person.
     */
    fun doesHavePhoneNumber(text: String): Boolean {
        val normalized = text.replace(" ", "")
        return if (normalized.isNotEmpty()) {
            phoneNumbers.any { number ->
                val stripped = number.normalizedNumber.replace(" ", "")
                PhoneNumberUtils.compare(stripped, normalized) ||
                    stripped == normalized ||
                    number.value.replace(" ", "") == normalized ||
                    stripped == text
            }
        } else {
            phoneNumbers.any { it.value.replace(" ", "") == normalized }
        }
    }

    /**
     * Substring match, for the search field rather than for identity.
     * Both sides are normalized first, so typing 912 finds a contact stored as +98 912 x.
     */
    fun doesContainPhoneNumber(text: String): Boolean {
        val normalized = text.normalizePhoneNumber()
        if (normalized.isEmpty()) return false
        return phoneNumbers.any { number ->
            number.normalizedNumber.contains(normalized) ||
                number.value.contains(text) ||
                number.value.normalizePhoneNumber().contains(normalized)
        }
    }

    override fun compareTo(other: SimpleContact): Int = name.compareTo(other.name, true)
}
