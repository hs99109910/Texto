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

/**
 * Darkens by [factor] percent of *lightness*, working in HSL rather than HSV.
 *
 * The distinction is the whole point and the reason this is not [adjustColor]: HSV's "value"
 * is the brightest channel, so scaling it turns a pale colour grey long before it turns dark,
 * while HSL's lightness is the midpoint between the brightest and darkest channels and steps
 * evenly. Eight percent is what the callers here have always used -- a nudge for a pressed or
 * recessed surface, not a shade.
 */
fun Int.darkenColor(factor: Int = 8): Int {
    if (this == Color.WHITE || this == Color.BLACK) return this

    val hsv = FloatArray(3)
    Color.colorToHSV(this, hsv)
    val hsl = hsvToHsl(hsv)
    hsl[2] = (hsl[2] - factor / 100f).coerceAtLeast(0f)
    return Color.HSVToColor(hslToHsv(hsl))
}

/**
 * The ink to put on this colour: near-black or white, whichever the eye can actually read.
 *
 * Decided by measured contrast rather than by a luminance threshold, because the two
 * disagree exactly where it matters -- a mid blue can sit either side of "0.5 luminance"
 * while one of the two inks is plainly the readable one. A translucent colour has no single
 * answer, since what shows through decides it, so that case falls back to luminance.
 */
fun Int.getContrastColor(): Int {
    if (Color.alpha(this) < 255) {
        return if (androidx.core.graphics.ColorUtils.calculateLuminance(this) < 0.5) {
            Color.WHITE
        } else {
            DARK_INK
        }
    }
    val onDark = androidx.core.graphics.ColorUtils.calculateContrast(DARK_INK, this)
    val onLight = androidx.core.graphics.ColorUtils.calculateContrast(Color.WHITE, this)
    return if (onDark >= onLight) DARK_INK else Color.WHITE
}

/** The near-black half of the contrast pair. Not pure black, which reads as a hole. */
private const val DARK_INK = 0xFF333333.toInt()

private fun hsvToHsl(hsv: FloatArray): FloatArray {
    val lightness = (2 - hsv[1]) * hsv[2] / 2
    val saturation = when {
        lightness == 0f || lightness == 1f -> 0f
        lightness < 0.5f -> hsv[1] * hsv[2] / (lightness * 2)
        else -> hsv[1] * hsv[2] / (2 - lightness * 2)
    }
    return floatArrayOf(hsv[0], saturation.coerceIn(0f, 1f), lightness)
}

private fun hslToHsv(hsl: FloatArray): FloatArray {
    val value = hsl[2] + hsl[1] * minOf(hsl[2], 1 - hsl[2])
    val saturation = if (value == 0f) 0f else 2 * (1 - hsl[2] / value)
    return floatArrayOf(hsl[0], saturation.coerceIn(0f, 1f), value)
}
