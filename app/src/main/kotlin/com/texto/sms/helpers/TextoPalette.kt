package com.texto.sms.helpers

import android.graphics.Color
import kotlin.math.abs

/**
 * The colours a picker offers, as a family times a lightness rather than one flat list.
 *
 * A single row of swatches answers "which colour" and "how light" with the same gesture, so
 * every shade of every hue has to be in it or neither question is properly answerable. Split
 * in two, eleven families and five rungs cover 55 colours in two taps, and the second row can
 * say something the first cannot: these are the *same* colour at different weights, which is
 * the choice actually being made when someone is picking a bar tint or a bubble.
 *
 * The ladders are generated in OKLCh (see [TextoTint.withLightness]) rather than written out,
 * so every family is lit the same way and adding a hue costs one entry.
 */
object TextoPalette {

    /**
     * Lightness of each rung, 0..1 in OKLCh, darkest first.
     *
     * The ends stop short of black and white on purpose: those two are in [NEUTRALS], and a
     * "red" that has been taken to either end is no longer a red, so spending a rung of every
     * family on it would give eleven identical swatches at each extreme.
     */
    private val RUNGS = doubleArrayOf(0.34, 0.47, 0.60, 0.73, 0.86)

    val SHADES = RUNGS.size

    /**
     * Paper to ink, kept as designed values rather than generated. Text and card colours are
     * picked from here, and pure white and pure black have to be exactly reachable -- a
     * generated grey lands a shade off and the one colour a user is most likely to want is
     * then the one they cannot have.
     */
    private val NEUTRALS = intArrayOf(
        0xFF000000.toInt(), 0xFF232833.toInt(), 0xFF8A90A0.toInt(),
        0xFFC9CDD6.toInt(), 0xFFFFFFFF.toInt()
    )

    /**
     * One seed per family. The first six are the skin's own -- the design's cyan, sky and
     * violet, and the green, teal and blue that sit between them -- and the rest complete the
     * wheel so somebody who wants off the house palette is not stuck.
     *
     * Only the hue and chroma of a seed survive; its lightness is replaced by every rung, so
     * these are read as directions on the wheel rather than as colours in their own right.
     */
    private val SEEDS = intArrayOf(
        0xFFF54651.toInt(), // red
        0xFFFF8A3D.toInt(), // orange
        0xFFF5C542.toInt(), // yellow
        0xFF4FC97A.toInt(), // green
        0xFF1F8F86.toInt(), // teal
        0xFF3BCFD0.toInt(), // cyan, the design's own
        0xFF55ADFF.toInt(), // sky, the design's own
        0xFF2368C9.toInt(), // blue
        0xFF7A5AF8.toInt(), // indigo
        0xFFA67DF2.toInt(), // violet, the design's own
        0xFFF26FB0.toInt(), // pink
    )

    /**
     * Every family as its own ladder, darkest first. The neutrals lead, because the rows that
     * choose a text or a card colour want them and would otherwise start by scrolling past
     * every hue in the app.
     */
    val families: List<IntArray> by lazy {
        buildList {
            add(NEUTRALS)
            SEEDS.forEach { seed ->
                add(IntArray(SHADES) { rung -> TextoTint.withLightness(seed, RUNGS[rung]) })
            }
        }
    }

    /** The swatch shown on the family strip: the rung that reads clearest at a glance. */
    fun faceOf(family: Int): Int = families[family][SHADES / 2]

    /**
     * Which family and rung [color] is, or the closest pair when it is not exactly one of
     * them.
     *
     * It has to be closest rather than exact. A colour can arrive from an older build, from a
     * skin, or from the hex field that used to be here, and a picker that cannot say where it
     * is has to open somewhere arbitrary -- which is how you lose a setting by glancing at it.
     * Matched in OKLab, so "closest" means closest to the eye rather than in RGB, where a
     * dark blue and a dark green are neighbours.
     */
    fun locate(color: Int): Pair<Int, Int> {
        val rgb = color or 0xFF000000.toInt()
        var bestFamily = 0
        var bestShade = SHADES / 2
        var bestDistance = Double.MAX_VALUE
        families.forEachIndexed { family, ladder ->
            ladder.forEachIndexed { shade, candidate ->
                val distance = perceptualDistance(rgb, candidate)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestFamily = family
                    bestShade = shade
                }
            }
        }
        return bestFamily to bestShade
    }

    /**
     * Rough OKLab distance. Lightness is weighted up because the two rows ask separate
     * questions: landing on the right family but the wrong rung is a visible error, while
     * being one hue out on a near grey is not.
     */
    private fun perceptualDistance(a: Int, b: Int): Double {
        val la = TextoTint.lightnessOf(a)
        val lb = TextoTint.lightnessOf(b)
        val dl = (la - lb) * 2.0
        val dr = (Color.red(a) - Color.red(b)) / 255.0
        val dg = (Color.green(a) - Color.green(b)) / 255.0
        val db = (Color.blue(a) - Color.blue(b)) / 255.0
        return dl * dl + dr * dr + dg * dg + db * db
    }

    /**
     * WCAG contrast ratio between two opaque colours, 1.0 to 21.0.
     *
     * The picker reports this because the app has shipped the mistake it catches: Classic's
     * ink measured 2.88:1 on white once the conversation list faded it to 58%, which is
     * unreadable and was not obvious from the swatch.
     */
    fun contrastRatio(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        val lighter = maxOf(la, lb)
        val darker = minOf(la, lb)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** WCAG 2.1's 4.5:1 floor for body text. */
    fun isReadable(background: Int, ink: Int) = contrastRatio(background, ink) >= 4.5

    private fun relativeLuminance(color: Int): Double {
        fun channel(value: Int): Double {
            val c = value / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }

    /** Whether two colours are the same to the eye, for marking the chosen swatch. */
    fun sameColour(a: Int, b: Int) =
        abs(Color.red(a) - Color.red(b)) <= 1 &&
            abs(Color.green(a) - Color.green(b)) <= 1 &&
            abs(Color.blue(a) - Color.blue(b)) <= 1
}
