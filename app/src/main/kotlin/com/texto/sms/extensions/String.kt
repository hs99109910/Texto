package com.texto.sms.extensions

fun String.getExtensionFromMimeType(): String {
    return when (lowercase()) {
        "image/png" -> ".png"
        "image/apng" -> ".apng"
        "image/webp" -> ".webp"
        "image/svg+xml" -> ".svg"
        "image/gif" -> ".gif"
        else -> ".jpg"
    }
}

fun String.isImageMimeType(): Boolean {
    return lowercase().startsWith("image")
}

fun String.isGifMimeType(): Boolean {
    return lowercase().endsWith("gif")
}

fun String.isVideoMimeType(): Boolean {
    return lowercase().startsWith("video")
}

fun String.isVCardMimeType(): Boolean {
    val lowercase = lowercase()
    return lowercase.endsWith("x-vcard") || lowercase.endsWith("vcard")
}

fun String.isAudioMimeType(): Boolean {
    return lowercase().startsWith("audio")
}

fun String.isCalendarMimeType(): Boolean {
    return lowercase().endsWith("calendar")
}

fun String.isPdfMimeType(): Boolean {
    return lowercase().endsWith("pdf")
}

fun String.isZipMimeType(): Boolean {
    return lowercase().endsWith("zip")
}

fun String.isPlainTextMimeType(): Boolean {
    return lowercase() == "text/plain"
}

/**
 * Persian text arrives from carriers in two different encodings of the same letters, and a
 * a LIKE comparison treats them as different characters: Arabic yeh vs Farsi yeh, Arabic
 * kaf vs Farsi keheh, and the two forms of heh. Digits come in three sets -- ASCII, Persian
 * and Arabic-Indic.
 *
 * A user typing a Farsi yeh therefore missed every message a carrier sent with the Arabic
 * one, which is most bank and service SMS. Both haystack and needle are folded to one
 * canonical form before they are compared.
 */
fun String.foldPersian(): String {
    val sb = StringBuilder(length)
    for (c in this) {
        sb.append(
            when (c) {
                'ي', 'ﻱ', 'ﻲ', 'ئ' -> 'ی'
                'ك', 'ﻛ', 'ﻜ' -> 'ک'
                'ة' -> 'ه'
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ؤ' -> 'و'
                // Persian and Arabic-Indic digit blocks both fold to ASCII.
                in '۰'..'۹' -> '0' + (c - '۰')
                in '٠'..'٩' -> '0' + (c - '٠')
                // Zero-width joiners, bidi marks and tatweel carry no meaning for a match.
                '‌', '‍', '‎', '‏', 'ـ' -> ' '
                else -> c
            }
        )
    }
    // Harakat (short vowels) appear in some promotional SMS and never in what a user types.
    return sb.toString().filterNot { it in 'ً'..'ْ' }
}

/** True when [needle] appears in this text once both sides are folded. */
fun String.containsPersian(needle: String): Boolean =
    foldPersian().contains(needle.foldPersian(), ignoreCase = true)
