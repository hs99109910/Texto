package com.texto.sms.extensions

import android.content.Context
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.View
import android.view.ViewTreeObserver
import android.widget.EditText
import androidx.core.content.FileProvider
import java.io.File
import java.text.Normalizer

/**
 * Strips diacritics, so a Persian or Latin name matches whichever way it was typed.
 *
 * NFD splits an accented letter into its base and its combining mark; the regex then drops
 * the marks and leaves the base. This is what the new-conversation search and the "simple
 * characters" setting both run on.
 */
private val diacriticMarks = """\p{Mn}+""".toRegex()

fun String.normalizeString(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(diacriticMarks, "")

/** The platform's own E.164-ish cleanup, which is what a dialler and a vCard both expect. */
fun String.normalizePhoneNumber(): String = PhoneNumberUtils.normalizeNumber(this)

/**
 * False when the user has turned animations off in Developer options or in an accessibility
 * setting, which the list animations here check before playing.
 */
val Context.areSystemAnimationsEnabled: Boolean
    get() = Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 0f) > 0f

/** The pattern a time is formatted with, following the phone's 12/24-hour setting. */
fun Context.getTimeFormat(): String = if (DateFormat.is24HourFormat(this)) "HH:mm" else "hh:mm a"

/**
 * A content uri for a file of ours, through the provider declared in the manifest.
 *
 * A `file://` uri would be rejected with a FileUriExposedException the moment it left the
 * process, and both callers here hand theirs to the camera or to an attachment.
 */
fun Context.getMyFileUri(file: File): android.net.Uri =
    FileProvider.getUriForFile(this, "$packageName.provider", file)

/** Runs [callback] once the view has been laid out, then stops listening. */
fun View.onGlobalLayout(callback: () -> Unit) {
    viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
        override fun onGlobalLayout() {
            if (viewTreeObserver.isAlive) {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                callback()
            }
        }
    })
}

/** The one TextWatcher callback anything here wants, with the text already in hand. */
fun EditText.onTextChangeListener(callback: (String) -> Unit) {
    addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        override fun afterTextChanged(s: Editable?) = callback(s.toString())
    })
}
