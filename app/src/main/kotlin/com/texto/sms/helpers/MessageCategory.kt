package com.texto.sms.helpers

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import com.texto.sms.models.Conversation

/**
 * A filter chip on the main screen. "All" always ships built in; "Contacts only" is a
 * built-in filter that can be toggled on from settings; the rest are defined by the user
 * as a name plus a list of keywords.
 */
@Serializable
data class MessageFilter(
    val id: String,
    val label: String,
    val keywords: List<String> = emptyList(),
    val isCustom: Boolean = false,
    /** Phone numbers picked from the chat list; a thread matches if its sender is here. */
    val senders: List<String> = emptyList(),
    /** Display names for [senders], kept only so the editor can list them back. */
    val senderLabels: List<String> = emptyList(),
) {
    companion object {
        const val ID_ALL = "all"
        const val ID_CONTACTS_ONLY = "contacts_only"


        /** Storage-only id for the list of senders the user has marked as advertising. */
        const val ID_ADS = "ads"

        /** The chip that is actually shown: everything except the [ID_ADS] senders. */
        const val ID_NO_ADS = "no_ads"

        fun all(label: String) = MessageFilter(ID_ALL, label)
        fun contactsOnly(label: String) = MessageFilter(ID_CONTACTS_ONLY, label)

        /**
         * The advertising sender list itself. This is never shown as a chip -- it only holds
         * what the user has marked, and [noAds] is what appears on screen. Not marked
         * isCustom, so it stays out of the user-defined filter storage and cannot be
         * renamed, reordered or deleted.
         */
        fun ads(label: String, senders: List<String>, senderLabels: List<String>) =
            MessageFilter(ID_ADS, label, senders = senders, senderLabels = senderLabels)

        /**
         * Carries the same sender list as [ads] but matches by exclusion, so every thread is
         * in it until the user marks that thread as advertising.
         */
        fun noAds(label: String, senders: List<String>) =
            MessageFilter(ID_NO_ADS, label, senders = senders)
    }
}

object FilterStore {

    private val json = Json { ignoreUnknownKeys = true }

    fun decode(raw: String): List<MessageFilter> {
        if (raw.isBlank()) return emptyList()
        return try {
            json.decodeFromString<List<MessageFilter>>(raw).filter { it.isCustom }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun encode(filters: List<MessageFilter>): String = try {
        json.encodeToString(filters.filter { it.isCustom })
    } catch (_: Exception) {
        ""
    }

    /** Single-filter round trip, for the built-in ads filter which is stored on its own. */
    fun decodeOne(raw: String): MessageFilter? = if (raw.isBlank()) {
        null
    } else {
        try {
            json.decodeFromString<MessageFilter>(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun encodeOne(filter: MessageFilter): String = try {
        json.encodeToString(filter)
    } catch (_: Exception) {
        ""
    }

    fun newCustomFilter(
        label: String,
        keywords: List<String>,
        senders: List<String> = emptyList(),
        senderLabels: List<String> = emptyList(),
    ) = MessageFilter(
        id = "custom:${System.currentTimeMillis()}",
        label = label,
        keywords = keywords,
        isCustom = true,
        senders = senders,
        senderLabels = senderLabels
    )

    /** Splits a free-text keyword field on Persian and Latin commas plus newlines. */
    fun parseKeywords(input: String): List<String> =
        input.split(',', '،', '\n', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
}

/**
 * Rule based classifier for Persian SMS. The verification-code rule is the only one left;
 * it powers the notification OTP-copy action rather than any filter chip.
 */
object MessageClassifier {

    private val otpKeywords = listOf(
        "رمز پویا", "رمز یکبار", "رمز یک بار", "رمز دوم", "رمز عبور", "رمز ورود",
        "کد تایید", "کد تأیید", "کد فعالسازی", "کد فعال سازی", "کد ورود", "کد یکبار",
        "کد امنیتی", "کد احراز", "کد اعتبارسنجی", "کد پیگیری ثبت نام",
        "otp", "verification code", "one time password", "one-time", "your code",
        "auth code", "security code"
    )

    // Zero-width and bidi control characters that show up in Persian SMS payloads.
    private const val ZWNJ = '‌'
    private const val ZWJ = '‍'
    private const val LRM = '‎'
    private const val RLM = '‏'
    private const val BOM = '﻿'

    // Letter variants that need folding to a single canonical form.
    private const val ARABIC_YEH = 'ي'
    private const val ALEF_MAKSURA = 'ى'
    private const val YEH_HAMZA = 'ئ'
    private const val FARSI_YEH = 'ی'
    private const val ARABIC_KAF = 'ك'
    private const val KEHEH = 'ک'
    private const val TEH_MARBUTA = 'ة'
    private const val HEH = 'ه'
    private const val ALEF_HAMZA_ABOVE = 'أ'
    private const val ALEF_HAMZA_BELOW = 'إ'
    private const val ALEF_MADDA = 'آ'
    private const val ALEF = 'ا'
    private const val WAW_HAMZA = 'ؤ'
    private const val WAW = 'و'

    private const val PERSIAN_ZERO = '۰'
    private const val PERSIAN_NINE = '۹'
    private const val ARABIC_ZERO = '٠'
    private const val ARABIC_NINE = '٩'

    /** Normalizes Arabic/Persian letter variants and eastern digits so matching is stable. */
    fun normalize(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            val mapped = when (ch) {
                ARABIC_YEH, ALEF_MAKSURA, YEH_HAMZA -> FARSI_YEH
                ARABIC_KAF -> KEHEH
                TEH_MARBUTA -> HEH
                ALEF_HAMZA_ABOVE, ALEF_HAMZA_BELOW, ALEF_MADDA -> ALEF
                WAW_HAMZA -> WAW
                ZWNJ, ZWJ, LRM, RLM, BOM -> ' '
                in PERSIAN_ZERO..PERSIAN_NINE -> '0' + (ch - PERSIAN_ZERO)
                in ARABIC_ZERO..ARABIC_NINE -> '0' + (ch - ARABIC_ZERO)
                else -> ch
            }
            sb.append(mapped)
        }
        return sb.toString().lowercase()
    }

    private fun containsAny(text: String, keywords: List<String>) =
        keywords.any { text.contains(normalize(it)) }

    /** A bare 4-8 digit block, the shape almost every verification code arrives in. */
    private val codePattern = Regex("(?<![0-9])[0-9]{4,8}(?![0-9])")

    /** Same OTP heuristic, applied to a raw message body instead of a [Conversation]. */
    fun isOtpMessage(body: String): Boolean {
        val text = normalize(body)
        return containsAny(text, otpKeywords) && codePattern.containsMatchIn(text)
    }

    /** The verification code digits in [body], or null when [isOtpMessage] would be false. */
    fun extractCode(body: String): String? {
        val text = normalize(body)
        if (!containsAny(text, otpKeywords)) return null
        return codePattern.find(text)?.value
    }

    private fun matchesKeywords(conversation: Conversation, keywords: List<String>): Boolean {
        if (keywords.isEmpty()) return false
        val text = normalize("${conversation.snippet} ${conversation.title}")
        return containsAny(text, keywords)
    }

    /** A thread matches when its number is one of the ones picked for this filter. */
    private fun matchesSenders(conversation: Conversation, senders: List<String>): Boolean {
        if (senders.isEmpty()) return false
        return senders.any { picked ->
            SystemBlockedNumbers.isSameSender(picked, conversation.phoneNumber)
        }
    }

    /** A thread matches when its sender's number is one of the [contactNumbers] loaded from the phone's address book. */
    private fun isKnownContact(conversation: Conversation, contactNumbers: Set<String>): Boolean {
        if (contactNumbers.isEmpty()) return false
        return contactNumbers.contains(SystemBlockedNumbers.comparable(conversation.phoneNumber))
    }

    fun matches(conversation: Conversation, filter: MessageFilter, contactNumbers: Set<String> = emptySet()) = when {
        filter.id == MessageFilter.ID_ALL -> true
        filter.id == MessageFilter.ID_CONTACTS_ONLY -> isKnownContact(conversation, contactNumbers)
        // Inverted on purpose: a thread belongs here until it is marked as advertising, so an
        // untouched filter holds every conversation rather than none.
        filter.id == MessageFilter.ID_NO_ADS -> !matchesSenders(conversation, filter.senders)
        // Picked senders and typed keywords are alternatives, so a filter can be built
        // from either one alone or from both together.
        else -> matchesSenders(conversation, filter.senders) ||
                matchesKeywords(conversation, filter.keywords)
    }
}
