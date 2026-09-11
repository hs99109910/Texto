package com.texto.sms.extensions

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import com.texto.sms.R

fun View.beVisible() {
    visibility = View.VISIBLE
}

fun View.beGone() {
    visibility = View.GONE
}

fun View.beInvisible() {
    visibility = View.INVISIBLE
}

fun View.beVisibleIf(condition: Boolean) = if (condition) beVisible() else beGone()

fun View.beGoneIf(condition: Boolean) = beVisibleIf(!condition)

/**
 * Tints the drawable this view is showing.
 *
 * Sets the filter rather than an `imageTintList` because the two do not compose: whichever
 * is applied last wins, and the app already sets tint lists on some of the same views from
 * its own theming pass.
 */
fun ImageView.applyColorFilter(color: Int) = setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)

/**
 * The same, for a drawable held somewhere other than an ImageView -- a view's background, a
 * compound drawable on a TextView.
 */
fun android.graphics.drawable.Drawable.applyColorFilter(color: Int) =
    mutate().setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)

/**
 * A toast, from any thread.
 *
 * The hop to the main looper is the whole reason this is not a bare Toast.makeText call:
 * most of the callers here are error paths inside `ensureBackgroundThread`, and Toast has to
 * be built on a thread with a looper or it throws -- which would turn a handled failure into
 * a crash.
 */
fun Context.toast(id: Int, length: Int = Toast.LENGTH_SHORT) = toast(getString(id), length)

fun Context.toast(message: String, length: Int = Toast.LENGTH_SHORT) {
    try {
        if (isOnMainThread()) {
            Toast.makeText(this, message, length).show()
        } else {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, message, length).show()
            }
        }
    } catch (_: Exception) {
    }
}

fun Context.showErrorToast(message: String, length: Int = Toast.LENGTH_LONG) {
    toast(String.format(getString(R.string.error), message), length)
}

fun Context.showErrorToast(exception: Exception, length: Int = Toast.LENGTH_LONG) {
    showErrorToast(exception.toString(), length)
}

/**
 * Called as a function, not read as a property: this replaces commons' `View.isVisible()`,
 * and androidx's `isVisible` of the same name is a property, so the two cannot be swapped
 * for one another at the call site without touching every caller.
 */
fun View.isVisible() = visibility == View.VISIBLE
