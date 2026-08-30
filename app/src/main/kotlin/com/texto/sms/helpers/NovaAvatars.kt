package com.texto.sms.helpers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.min

/**
 * Contact avatars in the skin's own language: a squircle filled with a two-stop gradient
 * derived from the contact's name, with their initials on top. Replaces the flat circular
 * letter icon the commons library draws.
 *
 * The hue is hashed from the name, so the same person keeps the same colour on every device
 * and across restarts without anything being stored.
 */
object NovaAvatars {

    /**
     * Corner radius as a fraction of the avatar's side. The design draws a 44px avatar on a
     * 16px radius, so a soft square rather than a circle.
     */
    private const val CORNER_FRACTION = 0.364f

    /**
     * The design's six avatar tints, verbatim. It cycles them by row index, which only works
     * on a fixed mock list; here the choice is hashed from the name instead, so a contact
     * keeps the same colour wherever they appear and however the list is sorted.
     */
    private val TINTS = listOf(
        Color.parseColor("#5B7CFF") to Color.parseColor("#2F6BFF"),
        Color.parseColor("#C86BD8") to Color.parseColor("#7D3FC4"),
        Color.parseColor("#3FC4A8") to Color.parseColor("#1F8F86"),
        Color.parseColor("#FF8A5B") to Color.parseColor("#E0543C"),
        Color.parseColor("#7F8CFF") to Color.parseColor("#4A4FD0"),
        Color.parseColor("#5BC0FF") to Color.parseColor("#2F7AD8"),
    )

    fun gradientFor(name: String): Pair<Int, Int> {
        val key = name.trim().ifEmpty { "?" }
        // String.hashCode is stable across JVM versions and platforms, unlike Object.hashCode.
        return TINTS[Math.floorMod(key.hashCode(), TINTS.size)]
    }

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
    }

    /**
     * Full avatar for [name]: gradient squircle plus initials. Suitable as a Glide
     * placeholder, so a contact with a real photo still gets this while the photo loads and
     * keeps it permanently if there is none.
     */
    fun letterAvatar(context: Context, name: String): Drawable = LetterAvatarDrawable(
        initials = initialsOf(name),
        gradient = gradientFor(name),
        density = context.resources.displayMetrics.density
    )

    /**
     * Up to two leading letters. Persian names split on whitespace the same way Latin ones
     * do, so "سارا محمدی" yields "سم" -- matching the design's two-letter monograms.
     */
    fun initialsOf(name: String): String {
        val cleaned = name.trim()
        if (cleaned.isEmpty()) return "؟"

        // A phone number has no meaningful initials; its last two digits identify it better.
        if (cleaned.all { it.isDigit() || it in "+-() " }) {
            val digits = cleaned.filter { it.isDigit() }
            return if (digits.length >= 2) digits.takeLast(2) else digits.ifEmpty { "؟" }
        }

        val words = cleaned.split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            words.isEmpty() -> "؟"
            words.size == 1 -> words[0].take(1)
            else -> "${words[0].take(1)}${words[1].take(1)}"
        }
    }

    private class LetterAvatarDrawable(
        private val initials: String,
        private val gradient: Pair<Int, Int>,
        private val density: Float,
    ) : Drawable() {

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
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

            fillPaint.shader = LinearGradient(
                b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
                gradient.first, gradient.second, Shader.TileMode.CLAMP
            )
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
