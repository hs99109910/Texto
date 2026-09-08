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

    /**
     * [color] moved [degrees] around the colour wheel, keeping its perceived lightness and
     * colourfulness.
     *
     * Rotated in OKLCh, not HSV. HSV holds saturation and value constant, which are not what
     * the eye measures: at one fixed S and V a yellow or a green is far more intense than a
     * blue or a violet of the same numbers. Rotating the skin's teal through HSV therefore
     * produced calm blues at one end of the wheel and glaring greens and yellows at the
     * other -- the whole strip read as "too loud" because a third of it genuinely was.
     *
     * OKLCh is built so that a hue rotation at fixed L and C keeps perceived intensity flat,
     * which is also the space the mockup itself specifies its colours in.
     *
     * A rotation of zero returns [color] untouched, so the skin's own designed colours are
     * never round-tripped through this at all.
     */
    fun rotateHue(color: Int, degrees: Int): Int {
        if (degrees.mod(360) == 0) return color

        val (lightness, a, b) = srgbToOklab(color)
        val chroma = Math.hypot(a.toDouble(), b.toDouble())
        // A grey has no hue to rotate; spinning its (near zero) chroma only invents one.
        if (chroma < 1e-4) return color

        val hue = Math.atan2(b.toDouble(), a.toDouble()) + Math.toRadians(degrees.toDouble())
        return oklchToSrgb(lightness, chroma, hue, Color.alpha(color))
    }

    /** L, a, b in OKLab for an sRGB colour. */
    private fun srgbToOklab(color: Int): Triple<Double, Double, Double> {
        val r = toLinear(Color.red(color) / 255.0)
        val g = toLinear(Color.green(color) / 255.0)
        val bl = toLinear(Color.blue(color) / 255.0)

        val l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * bl)
        val m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * bl)
        val s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * bl)

        return Triple(
            0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
            1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
            0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        )
    }

    /**
     * Back to sRGB, reducing chroma until the colour actually fits in the display's gamut.
     *
     * Rotating at a fixed chroma lands outside sRGB for part of the wheel -- there is no
     * blue as colourful as the yellow of the same L and C. Letting the channels clip there
     * would shift the hue and flatten the result; stepping the chroma down instead keeps the
     * hue exactly and gives up only the colourfulness the screen cannot show anyway.
     */
    private fun oklchToSrgb(lightness: Double, chroma: Double, hue: Double, alpha: Int): Int {
        var low = 0.0
        var high = chroma
        var best = 0
        // Eight halvings put the chroma within 0.4% of the gamut boundary, which is well
        // under one step of an 8-bit channel.
        repeat(8) {
            val mid = (low + high) / 2
            val packed = oklabToSrgb(lightness, mid * Math.cos(hue), mid * Math.sin(hue))
            if (packed == null) high = mid else { best = packed; low = mid }
        }
        val fitted = best.takeIf { it != 0 }
            ?: oklabToSrgb(lightness, 0.0, 0.0, clamp = true)!!
        return Color.argb(alpha, Color.red(fitted), Color.green(fitted), Color.blue(fitted))
    }

    /** Null when the colour falls outside sRGB, unless [clamp] is asked for. */
    private fun oklabToSrgb(lightness: Double, a: Double, b: Double, clamp: Boolean = false): Int? {
        val l = lightness + 0.3963377774 * a + 0.2158037573 * b
        val m = lightness - 0.1055613458 * a - 0.0638541728 * b
        val s = lightness - 0.0894841775 * a - 1.2914855480 * b

        val l3 = l * l * l
        val m3 = m * m * m
        val s3 = s * s * s

        val r = 4.0767416621 * l3 - 3.3077115913 * m3 + 0.2309699292 * s3
        val g = -1.2684380046 * l3 + 2.6097574011 * m3 - 0.3413193965 * s3
        val bl = -0.0041960863 * l3 - 0.7034186147 * m3 + 1.7076147010 * s3

        if (!clamp && (r < -1e-4 || r > 1 + 1e-4 || g < -1e-4 || g > 1 + 1e-4 ||
                bl < -1e-4 || bl > 1 + 1e-4)
        ) {
            return null
        }
        return Color.rgb(toByte(r), toByte(g), toByte(bl))
    }

    private fun toLinear(channel: Double) =
        if (channel <= 0.04045) channel / 12.92 else Math.pow((channel + 0.055) / 1.055, 2.4)

    private fun toByte(linear: Double): Int {
        val clamped = linear.coerceIn(0.0, 1.0)
        val encoded = if (clamped <= 0.0031308) {
            clamped * 12.92
        } else {
            1.055 * Math.pow(clamped, 1 / 2.4) - 0.055
        }
        return Math.round(encoded * 255).toInt().coerceIn(0, 255)
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
