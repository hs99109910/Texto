package com.texto.sms.extensions

import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import com.texto.sms.R

/**
 * Version gates, named after the release rather than the number they compare.
 *
 * Functions rather than properties, which is how every call site already reads them: these
 * replace commons' own, and making them values instead would be a rename of eighty call
 * sites for no gain.
 */
fun isQPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
fun isRPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
fun isSPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
fun isTiramisuPlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
fun isUpsideDownCakePlus() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

val Context.notificationManager: NotificationManager
    get() = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

private val Context.inputMethodManager: InputMethodManager
    get() = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager

/**
 * Puts [text] on the clipboard and says so.
 *
 * The toast is skipped from Android 13 on, where the platform shows its own copy
 * confirmation: both at once reads as the app not knowing what it just did.
 */
fun Context.copyToClipboard(text: String) {
    val clip = ClipData.newPlainText(getString(R.string.app_launcher_name), text)
    (getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(clip)
    if (!isTiramisuPlus()) {
        toast(R.string.value_copied_to_clipboard)
    }
}

/**
 * Dismisses the keyboard.
 *
 * Takes the token from whatever currently has focus rather than from a view handed in: the
 * callers here are closing a screen or a sheet, and by then the field that had the keyboard
 * up is often already gone.
 */
fun android.app.Activity.hideKeyboard() {
    val token = currentFocus?.windowToken ?: window.decorView.windowToken
    inputMethodManager.hideSoftInputFromWindow(token, 0)
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    currentFocus?.clearFocus()
}

fun Context.hideKeyboard(view: View) {
    inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
}

/**
 * Brings the keyboard up for [view], asking again on the next frame.
 *
 * The retry is not belt and braces: a field that has just been made focusable, or that is
 * still being laid out when the call is made, is not ready to receive input yet and the
 * first request is dropped silently.
 */
fun android.app.Activity.showKeyboard(view: View) {
    window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    view.requestFocus()
    inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    view.post {
        inputMethodManager.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT)
    }
}

/** The display minus the system bars, which is what a layout actually gets. */
val Context.usableScreenSize: Point
    get() {
        val size = Point()
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (isRPlus()) {
            val metrics = windowManager.currentWindowMetrics
            val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.systemBars()
            )
            size.x = metrics.bounds.width() - insets.left - insets.right
            size.y = metrics.bounds.height() - insets.top - insets.bottom
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getSize(size)
        }
        return size
    }
