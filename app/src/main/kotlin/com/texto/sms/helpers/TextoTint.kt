package com.texto.sms.helpers

import android.graphics.Color
import android.graphics.ColorMatrix

/**
 * The tonality strip's arithmetic, in one place because three very different surfaces need
 * the same rotation to agree: [Config]'s accent getters (plain colours), the strip itself
 * (its preview swatches), and the header wordmark (a bitmap, so a [ColorMatrix] rather than
 * a colour).
 *
 * Hue rotation rather than replacement is what keeps a skin recognisable. The Neon accent is
 * a three stop ramp -- teal, sky, violet -- and the spacing between those stops is the thing
 * that reads as "Neon". Rotating all three by the same angle slides that ramp around the
 * wheel with its shape intact; overwriting them with one picked colour would throw the shape
 * away and leave every accent surface flat.
 */
object TextoTint {

    /** [color] moved [degrees] around the colour wheel, keeping its saturation and value. */
    fun rotateHue(color: Int, degrees: Int): Int {
        if (degrees.mod(360) == 0) return color
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        hsv[0] = (hsv[0] + degrees).mod(360f)
        return Color.HSVToColor(Color.alpha(color), hsv)
    }

    /**
     * The same rotation as a [ColorMatrix], for tinting a bitmap whose colours are baked in.
     *
     * This is the luminance preserving hue rotation from the SVG filter spec, not
     * [ColorMatrix.setRotate], which spins around one primary axis and skews the other two:
     * on the wordmark that turned the violet end muddy well before the teal end had moved.
     */
    fun hueRotationMatrix(degrees: Int): ColorMatrix {
        val rad = Math.toRadians(degrees.toDouble())
        val c = Math.cos(rad).toFloat()
        val s = Math.sin(rad).toFloat()
        return ColorMatrix(
            floatArrayOf(
                0.213f + c * 0.787f - s * 0.213f,
                0.715f - c * 0.715f - s * 0.715f,
                0.072f - c * 0.072f + s * 0.928f, 0f, 0f,

                0.213f - c * 0.213f + s * 0.143f,
                0.715f + c * 0.285f + s * 0.140f,
                0.072f - c * 0.072f - s * 0.283f, 0f, 0f,

                0.213f - c * 0.213f - s * 0.787f,
                0.715f - c * 0.715f + s * 0.715f,
                0.072f + c * 0.928f + s * 0.072f, 0f, 0f,

                0f, 0f, 0f, 1f, 0f
            )
        )
    }
}
