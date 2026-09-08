package com.texto.sms.extensions

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.texto.sms.helpers.TextoFonts

fun Int.withAlpha(alpha: Float): Int {
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    return (this and 0x00FFFFFF) or (a shl 24)
}

fun Int.adjustColor(factor: Float): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(this, hsv)
    hsv[2] *= factor
    return Color.HSVToColor(hsv)
}

/**
 * Last resort for a call site that has a Context but no [com.texto.sms.activities.SimpleActivity]:
 * device density only, no UI-scale. Prefer [getScaledPxIn].
 *
 * There used to be a receiver-less `Int.getScaledPx()` beside this that returned `this * 2.5`
 * -- a hardcoded density and no UI-scale at all. It shared its name with
 * [com.texto.sms.activities.SimpleActivity.getScaledPx], so it silently took over wherever a
 * call site happened not to have the activity as its receiver, and the sizes there were wrong
 * on every device that is not xhdpi. It is gone; nothing should reintroduce it.
 */
fun Int.getScaledPx(context: Context): Int {
    return (this * context.resources.displayMetrics.density).toInt()
}

/**
 * [com.texto.sms.activities.SimpleActivity.getScaledPx] reached from an adapter, which is not
 * the activity itself. Keeps the design's dp figures readable at the call site while still
 * honouring the UI-scale setting.
 */
fun Int.getScaledPxIn(activity: com.texto.sms.activities.SimpleActivity): Int =
    with(activity) { this@getScaledPxIn.getScaledPx() }

fun Float.getScaledTextSize(context: Context): Float {
    return this * context.config.uiScale
}

fun Context.getScaledTextSize(size: Float = 16f): Float {
    return size * config.uiScale
}

fun Context.getScaledPx(px: Int): Int {
    return (px * resources.displayMetrics.density).toInt()
}

fun Context.getScaledDimen(resId: Int): Int {
    return resources.getDimensionPixelSize(resId)
}

fun Int.getScaledDimen(context: Context): Int {
    return context.resources.getDimensionPixelSize(this)
}

/**
 * Context-level counterpart to [com.texto.sms.activities.SimpleActivity.getCustomTypeface].
 * This used to be a hard-coded `null`, so any caller whose receiver was a plain Context --
 * dialogs and non-SimpleActivity views -- silently kept the system font while the rest of
 * the app switched to the selected Persian face.
 */
fun Context.getCustomTypeface(): Typeface? =
    TextoFonts.getTypeface(this, config.fontFamilyTexto)

fun View.updateAppFonts() {
    if (this is ViewGroup) {
        for (i in 0 until childCount) {
            getChildAt(i).updateAppFonts()
        }
    } else if (this is TextView) {
        val typeface = context.getCustomTypeface()
        if (typeface != null) {
            this.typeface = typeface
        }
    }
}

fun Context.updateAppFonts(view: View) {
    view.updateAppFonts()
}

fun Context.setupScaledToolbar(toolbar: View) {}
