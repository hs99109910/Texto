package com.texto.sms.helpers

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable

/**
 * A chat bubble with Telegram's tail: a small hook swept out of the bottom corner on the
 * screen-edge side, drawn only on the last message of a run.
 *
 * The bubble always leaves [tailWidth] free on its [tailOnRight] side, whether or not it
 * carries the tail, so every bubble in a run lines up on the same edge as the one that does.
 * [radii] are physical (TL, TR, BR, BL pairs), like GradientDrawable's.
 */
class TextoBubbleDrawable(
    private val colors: IntArray,
    private val radii: FloatArray,
    private val tailOnRight: Boolean,
    private val showTail: Boolean,
    private val tailWidth: Float,
    private val tailHeight: Float,
    private val strokeColor: Int? = null,
    private val strokeWidth: Float = 0f,
) : Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val path = Path()
    private val body = RectF()

    override fun onBoundsChange(bounds: android.graphics.Rect) {
        super.onBoundsChange(bounds)
        rebuild()
    }

    /** The bubble's own rectangle, without the tail or the space kept for it. */
    fun bodyRect(): RectF = RectF(body)

    private fun rebuild() {
        val b = bounds
        body.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat())
        if (tailOnRight) body.right -= tailWidth else body.left += tailWidth

        val corners = radii.copyOf()
        if (showTail) {
            // The tail replaces the rounded corner it grows out of.
            if (tailOnRight) { corners[4] = 0f; corners[5] = 0f } else { corners[6] = 0f; corners[7] = 0f }
        }
        path.reset()
        path.addRoundRect(body, corners, Path.Direction.CW)

        if (showTail) {
            val tail = Path()
            val bottom = body.bottom
            val h = tailHeight.coerceAtMost(body.height())
            // Built for the right side, then mirrored about the bubble's edge for the left.
            val edge = if (tailOnRight) body.right else body.left
            val s = if (tailOnRight) 1f else -1f
            tail.moveTo(edge - s * 1f, bottom - h)
            tail.lineTo(edge, bottom - h)
            // A concave sweep from the side down to the tip, then a slightly rounded tip.
            tail.cubicTo(
                edge, bottom - h * 0.35f,
                edge + s * tailWidth * 0.45f, bottom - tailWidth * 0.15f,
                edge + s * tailWidth, bottom - tailWidth * 0.08f
            )
            tail.quadTo(edge + s * tailWidth * 1.02f, bottom, edge + s * tailWidth * 0.72f, bottom)
            tail.lineTo(edge - s * 1f, bottom)
            tail.close()
            path.op(tail, Path.Op.UNION)
        }

        fillPaint.shader = if (colors.size > 1) {
            LinearGradient(body.left, body.top, body.right, body.bottom, colors, null, Shader.TileMode.CLAMP)
        } else {
            null
        }
        if (colors.size == 1) fillPaint.color = colors[0]

        if (strokeColor != null && strokeWidth > 0f) {
            strokePaint.color = strokeColor
            strokePaint.strokeWidth = strokeWidth
        }
    }

    override fun draw(canvas: Canvas) {
        canvas.drawPath(path, fillPaint)
        if (strokeColor != null && strokeWidth > 0f) canvas.drawPath(path, strokePaint)
    }

    override fun getOutline(outline: Outline) {
        val r = radii[0]
        outline.setRoundRect(
            body.left.toInt(), body.top.toInt(), body.right.toInt(), body.bottom.toInt(), r
        )
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
        strokePaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        fillPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
