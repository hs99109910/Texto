package com.texto.sms.extensions

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Paint
import android.net.Uri
import android.provider.Settings
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.TextView
import com.texto.sms.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.log10
import kotlin.math.pow

/** Half opacity, for a control that is present but not yet usable. */
const val MEDIUM_ALPHA = 0.5f

const val DAY_SECONDS = 86_400
const val MONTH_SECONDS = 2_592_000

/** The extra a conversation shortcut carries its number in. */
const val KEY_PHONE = "phone"

/**
 * The SharedPreferences file every setting in this app lives in.
 *
 * The name is frozen. It is where an existing install's colours, theme, language and filters
 * already are, so renaming it would present every user with a factory-fresh app.
 */
const val PREFS_KEY = "Prefs"

/** Base type size for the four-rung font-size setting; the UI-scale slider multiplies it. */
fun Context.getTextSize(): Float = resources.getDimension(
    when (config.fontSize) {
        0 -> R.dimen.texto_text_small
        1 -> R.dimen.texto_text_medium
        2 -> R.dimen.texto_text_large
        else -> R.dimen.texto_text_extra_large
    }
)

fun Context.getBottomNavigationBackgroundColor(): Int = config.backgroundColor

fun Context.getTimeFormatWithSeconds(): String =
    if (android.text.format.DateFormat.is24HourFormat(this)) "HH:mm:ss" else "hh:mm:ss a"

/** `yyyyMMdd_HHmmss`, for a filename rather than for a reader. */
fun Context.getCurrentFormattedDateTime(): String =
    SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(System.currentTimeMillis()))

fun String.areDigitsOnly(): Boolean = matches("[0-9]+".toRegex())

fun String.isAValidFilename(): Boolean =
    isNotEmpty() && none { it in "*/\"\\:<>?|" }

fun String.getFilenameFromPath(): String = substringAfterLast('/')

/**
 * The platform's own extension table rather than a thousand-line map of our own: commons
 * shipped a HashMap of every mimetype it could think of, built on every call.
 */
fun String.getMimeType(): String {
    val extension = substringAfterLast('.', "").lowercase(Locale.US)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension).orEmpty()
}

fun Context.getFilenameFromUri(uri: Uri): String =
    if (uri.scheme == "file") {
        uri.path?.getFilenameFromPath().orEmpty()
    } else {
        getFilenameFromContentUri(uri) ?: uri.lastPathSegment.orEmpty()
    }

private fun Context.getFilenameFromContentUri(uri: Uri): String? = runCatching {
    contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { if (it.moveToFirst()) it.getString(0) else null }
}.getOrNull()

/** B through EB, three significant figures, which is what an attachment row has room for. */
fun Long.formatSize(): String {
    if (this <= 0) return "0 B"
    val units = arrayOf("B", "kB", "MB", "GB", "TB", "PB", "EB")
    val digitGroups = (log10(toDouble()) / log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    return "%.1f %s".format(Locale.getDefault(), this / 1024.0.pow(digitGroups.toDouble()), units[digitGroups])
}

/** Paints every occurrence of [part] in [color], for a search result's own snippet. */
fun String.highlightTextPart(part: String, color: Int): SpannableString {
    val spannable = SpannableString(this)
    if (part.isEmpty()) return spannable
    var index = indexOf(part, 0, true)
    while (index >= 0) {
        spannable.setSpan(
            ForegroundColorSpan(color), index, index + part.length,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        index = indexOf(part, index + part.length, true)
    }
    return spannable
}

fun Int.getColorStateList(): ColorStateList = ColorStateList.valueOf(this)

val EditText.value: String get() = text.toString().trim()

fun TextView.underlineText() {
    paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
}

/**
 * The platform's own press feedback, rather than a drawable from another package.
 *
 * The conversation row paints over this immediately with its own glass panel; the plainer
 * row in the new-conversation list is the one that keeps it.
 */
fun View.setupViewBackground(context: Context) {
    val outValue = TypedValue()
    context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
    setBackgroundResource(outValue.resourceId)
}

fun Activity.shareTextIntent(text: String) {
    Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        launchChooser(this, R.string.share_via)
    }
}

fun Activity.sendEmailIntent(recipient: String) {
    Intent(Intent.ACTION_SENDTO).apply {
        data = Uri.fromParts("mailto", recipient, null)
        launchSafely(this)
    }
}

fun Activity.launchViewContactIntent(uri: Uri) {
    Intent(Intent.ACTION_VIEW).apply {
        data = uri
        launchSafely(this)
    }
}

/** Opens whatever the system offers for [uri], mimetype included when we know it. */
fun Activity.launchActivityIntent(intent: Intent) = launchSafely(intent)

fun Activity.openNotificationSettings() {
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        launchSafely(this)
    }
}

fun Activity.openRequestExactAlarmSettings(appId: String) {
    if (isSPlus()) {
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
            data = Uri.fromParts("package", appId, null)
            launchSafely(this)
        }
    }
}

private fun Activity.launchSafely(intent: Intent) {
    try {
        startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        toast(R.string.no_app_found)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

private fun Activity.launchChooser(intent: Intent, titleRes: Int) {
    try {
        startActivity(Intent.createChooser(intent, getString(titleRes)))
    } catch (_: ActivityNotFoundException) {
        toast(R.string.no_app_found)
    } catch (e: Exception) {
        showErrorToast(e)
    }
}

