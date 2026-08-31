package com.texto.sms.helpers

import android.content.Context
import android.graphics.Color
import com.texto.sms.R

/**
 * Names a colour in Persian, closely enough for a label.
 *
 * The SIM badge in the composer identifies a slot by colour alone, so the picker needs to say
 * which colour that is. Matching an exact hex against a table would fail the moment the user
 * picks their own shade, so the colour is classified by hue, saturation and lightness instead
 * and every possible colour gets an answer.
 */
object ColorNames {

    /**
     * Hue bands in degrees, each paired with the string naming it. Ranges are half-open and
     * the last one wraps past 360 back to red.
     */
    private val HUE_BANDS = listOf(
        15f to R.string.color_red,
        45f to R.string.color_orange,
        70f to R.string.color_yellow,
        170f to R.string.color_green,
        200f to R.string.color_teal,
        250f to R.string.color_blue,
        290f to R.string.color_purple,
        335f to R.string.color_pink,
        360f to R.string.color_red,
    )

    fun of(context: Context, color: Int): String {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        val hue = hsv[0]
        val saturation = hsv[1]
        val value = hsv[2]

        // Greys have no meaningful hue, so they are named by lightness instead.
        if (saturation < 0.12f) {
            return context.getString(
                when {
                    value < 0.2f -> R.string.color_black
                    value < 0.75f -> R.string.color_grey
                    else -> R.string.color_white
                }
            )
        }

        val band = HUE_BANDS.first { hue < it.first }.second
        return context.getString(band)
    }
}
