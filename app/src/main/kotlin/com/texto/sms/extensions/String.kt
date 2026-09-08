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

/** Unicode LEFT-TO-RIGHT ISOLATE and POP DIRECTIONAL ISOLATE. */
private const val LRI = '⁦'
private const val PDI = '⁩'

/**
 * Wraps a phone number so it reads left-to-right inside this app's right-to-left screens.
 *
 * The leading "+" of an international number is bidi class ET, which only joins the run to
 * its right when that run is *European* digits. Persian and Arabic-Indic digits are class AN
 * instead, so the "+" stayed neutral, took the paragraph's own right-to-left direction, and
 * was laid out at the far end: "+98912..." arrived on screen as "98912...+".
 *
 * An explicit isolate settles it whatever the digits are -- inside it the run is left-to-right
 * and the "+" resolves to the left of the number, while the isolate keeps the surrounding
 * Persian text unaffected, which a bare LRM or a view-wide textDirection would not.
 *
 * Only a string that is *entirely* dialable is wrapped. Names and alphanumeric sender ids
 * ("BANKMELLI") are returned untouched: this is applied to fields that sometimes hold a
 * number and sometimes hold a name, and forcing a Persian name left-to-right would be the
 * same bug in the other direction.
 *
 * Display only. Never store, compare or dial the result: the marks are real characters.
 */
fun String.asLtrPhone(): String {
    if (isEmpty()) return this
    var digits = 0
    for (c in this) {
        when {
            c.isDigit() -> digits++
            c in DIALABLE_PUNCTUATION -> Unit
            else -> return this
        }
    }
    return if (digits == 0) this else "$LRI$this$PDI"
}

/** What may sit between the digits of a number without making it a name. */
private const val DIALABLE_PUNCTUATION = "+-()./  *#"
