package com.texto.sms.helpers

import android.content.Context
import com.texto.sms.extensions.config
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * Contact avatars in the skin's own language: a squircle filled with the active theme's
 * accent gradient, with the contact's initials on top. Replaces the flat circular letter
 * icon the commons library draws.
 *
 * Nothing here is per-contact but the initials -- the design paints every avatar from the
 * same `--grad`/`--primary-fg` pair, so an avatar follows the theme rather than the name.
 */
object TextoAvatars {

    /**
     * Corner radius as a fraction of the avatar's side. The design draws a 44px avatar on a
     * 16px radius, so a soft square rather than a circle.
     */
    private const val CORNER_FRACTION = 0.364f

    /** How far the tile is lifted off the row behind it, in dp. */
    private const val AVATAR_ELEVATION_DP = 3f

    /**
     * How much of the accent ramp's spread the avatar gives up, 0f..1f.
     *
     * The tile is small and now carries a shadow, and at that size the full accent ramp read
     * as two colours fighting rather than one surface. Each stop is pulled this far toward
     * the ramp's own average, which keeps the hue run and the overall brightness but flattens
     * the travel between the ends. The badges, chips and bubbles keep the full ramp.
     */
    private const val GRADIENT_SOFTENING = 0.20f

    /** [color] moved [amount] of the way toward [target]. */
    private fun blend(color: Int, target: Int, amount: Float): Int = android.graphics.Color.argb(
        android.graphics.Color.alpha(color),
        (android.graphics.Color.red(color) +
            (android.graphics.Color.red(target) - android.graphics.Color.red(color)) * amount).toInt(),
        (android.graphics.Color.green(color) +
            (android.graphics.Color.green(target) - android.graphics.Color.green(color)) * amount).toInt(),
        (android.graphics.Color.blue(color) +
            (android.graphics.Color.blue(target) - android.graphics.Color.blue(color)) * amount).toInt()
    )

    /** Every stop pulled [GRADIENT_SOFTENING] toward the set's average colour. */
    private fun soften(stops: IntArray): IntArray {
        val n = stops.size
        val mean = android.graphics.Color.rgb(
            stops.sumOf { android.graphics.Color.red(it) } / n,
            stops.sumOf { android.graphics.Color.green(it) } / n,
            stops.sumOf { android.graphics.Color.blue(it) } / n
        )
        return IntArray(n) { blend(stops[it], mean, GRADIENT_SOFTENING) }
    }

    /**
     * Every avatar carries the active theme's own accent gradient -- `var(--grad)` in the
     * design, which paints all of them the same way rather than varying by contact. The six
     * per-name tints this used to hash into were the previous design's, and they survived a
     * theme change untouched, so switching skins left the list's avatars on the old palette.
     */
    fun gradientFor(context: Context): Pair<Int, Int> {
        val config = context.config
        return config.accentGradientStart to config.accentGradientEnd
    }

    /** The accent gradient's middle stop, or 0 when the theme only defines two. */
    private fun midStopFor(context: Context): Int = context.config.accentGradientMid

    /**
     * Clips [view] to the same squircle the generated avatars use, so a contact who *does*
     * have a photo gets the identical silhouette instead of the library's circle.
     */
    fun clipToSquircle(view: android.view.View) {
        view.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(v: android.view.View, outline: android.graphics.Outline) {
                val side = min(v.width, v.height).toFloat()
                outline.setRoundRect(0, 0, v.width, v.height, side * CORNER_FRACTION)
            }
        }
        view.clipToOutline = true
        // The outline is already the squircle, so elevation casts a shadow of exactly the
        // tile's shape and lifts it off the card it sits on. Every avatar in the app goes
        // through this call, so they all lift by the same amount.
        view.elevation = AVATAR_ELEVATION_DP * view.resources.displayMetrics.density
    }

    /**
     * Full avatar for [name]: gradient squircle plus initials. Suitable as a Glide
     * placeholder, so a contact with a real photo still gets this while the photo loads and
     * keeps it permanently if there is none.
     */
    fun letterAvatar(context: Context, name: String): Drawable = LetterAvatarDrawable(
        initials = initialsOf(name),
        gradient = gradientFor(context),
        midStop = midStopFor(context),
        // The design sets the monogram in `--primary-fg`, the same ink the sent bubble uses
        // on the same gradient, rather than always-white.
        textColor = context.config.accentInkColor,
        density = context.resources.displayMetrics.density
    )

    /**
     * Up to two leading letters. Persian names split on whitespace the same way Latin ones
     * do, so "سارا محمدی" yields "سم" -- matching the design's two-letter monograms.
     *
     * The placeholder for a name with nothing usable in it follows the language: Persian
     * writes its question mark the other way round.
     */
    private fun unknownInitial() = if (TextoLocale.isPersian) "؟" else "?"

    fun initialsOf(name: String): String {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return unknownInitial()

        // A phone number has no meaningful initials; its last two digits identify it better.
        if (cleaned.all { it.isDigit() || it in "+-() " }) {
            val digits = cleaned.filter { it.isDigit() }
            return if (digits.length >= 2) digits.takeLast(2) else digits.ifEmpty { unknownInitial() }
        }

        val words = cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            words.isEmpty() -> unknownInitial()
            words.size == 1 -> words[0].take(1)
            else -> "${words[0].take(1)}${words[1].take(1)}"
        }
    }

    private class LetterAvatarDrawable(
        private val initials: String,
        private val gradient: Pair<Int, Int>,
        private val midStop: Int,
        private val textColor: Int,
        private val density: Float,
    ) : Drawable() {

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        private val rect = RectF()

        override fun draw(canvas: Canvas) {
            val b = bounds
            if (b.isEmpty) return

            rect.set(b)
            val side = min(b.width(), b.height()).toFloat()
            val radius = side * CORNER_FRACTION

            // Three stops when the theme defines a middle one, so an avatar carries the same
            // cyan-through-sky-blue-to-violet run the sent bubbles and badges do.
            fillPaint.shader = if (midStop == 0) {
                val stops = soften(intArrayOf(gradient.first, gradient.second))
                LinearGradient(
                    b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
                    stops[0], stops[1], Shader.TileMode.CLAMP
                )
            } else {
                LinearGradient(
                    b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
                    soften(intArrayOf(gradient.first, midStop, gradient.second)),
                    floatArrayOf(0f, ACCENT_GRADIENT_MID_POSITION, 1f),
                    Shader.TileMode.CLAMP
                )
            }
            canvas.drawRoundRect(rect, radius, radius, fillPaint)

            textPaint.textSize = side * 0.36f
            // Centre on the glyph box rather than the baseline, so one- and two-letter
            // monograms sit at the same optical height.
            val metrics = textPaint.fontMetrics
            val baseline = rect.centerY() - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(initials, rect.centerX(), baseline, textPaint)
        }

        override fun getIntrinsicWidth() = (40 * density).toInt()

        override fun getIntrinsicHeight() = (40 * density).toInt()

        override fun setAlpha(alpha: Int) {
            fillPaint.alpha = alpha
            textPaint.alpha = alpha
            invalidateSelf()
        }

        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
            fillPaint.colorFilter = colorFilter
            invalidateSelf()
        }

        @Deprecated("Deprecated in Drawable, but still abstract on the minSdk we compile against.")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT

        override fun onBoundsChange(bounds: Rect) {
            super.onBoundsChange(bounds)
            invalidateSelf()
        }
    }
}
