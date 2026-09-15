package com.texto.sms.helpers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * One bar that picks any colour: hue runs along it, lightness runs down it, and a narrow
 * column of greys at its start edge carries pure white to pure black.
 *
 * It replaced the family strip and the lightness strip. Two bands asked two questions in two
 * gestures and still offered only 55 colours; this answers both with one touch, anywhere on a
 * continuous field, and drags freely across it.
 *
 * Lightness is laid out in OKLCh through [TextoTint.withLightness], so a row of the bar is one
 * perceived brightness across every hue, rather than HSV's glaring yellows beside dull blues.
 */
class TextoSpectrumBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Fires as the finger moves, throttled, and always once on lift. */
    var onPicked: ((Int) -> Unit)? = null

    var colour: Int = Color.WHITE
        private set

    private val density get() = resources.displayMetrics.density
    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val clip = Path()
    private val rect = RectF()

    /** The marker as fractions of the view, so it survives a resize. */
    private var markX = 0.5f
    private var markY = 0.5f
    private var lastSent = 0L
    private var lastSentColour = 0

    /** Places the marker on the closest point to [color] without reporting a pick. */
    fun setColourSilently(color: Int) {
        colour = color or 0xFF000000.toInt()
        val hsv = FloatArray(3)
        Color.colorToHSV(colour, hsv)
        val lightness = TextoTint.lightnessOf(colour).toFloat()
        if (hsv[1] < NEUTRAL_SATURATION) {
            markX = NEUTRAL_FRACTION / 2f
            markY = 1f - lightness
        } else {
            markX = NEUTRAL_FRACTION + GAP_FRACTION + (hsv[0] / 360f) * (1f - NEUTRAL_FRACTION - GAP_FRACTION)
            markY = ((TOP_L - lightness) / (TOP_L - BOTTOM_L))
        }
        markY = markY.coerceIn(0f, 1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = 14f * density

        rect.set(0f, 0f, w, h)
        clip.reset()
        clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        val saved = canvas.save()
        canvas.clipPath(clip)
        val neutralRight = w * NEUTRAL_FRACTION
        val hueLeft = w * (NEUTRAL_FRACTION + GAP_FRACTION)
        val physical = mirrored()
        drawZone(canvas, neutralsBitmap, if (physical) w - neutralRight else 0f, if (physical) w else neutralRight, h)
        drawZone(canvas, huesBitmap, if (physical) 0f else hueLeft, if (physical) w - hueLeft else w, h)
        canvas.restoreToCount(saved)

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = density
        paint.color = TextoGlass.rimFor(Color.GRAY, 0.30f)
        rect.inset(density / 2f, density / 2f)
        canvas.drawRoundRect(rect, radius, radius, paint)

        drawMarker(canvas, w, h)
    }

    private fun drawZone(canvas: Canvas, bitmap: Bitmap, left: Float, right: Float, h: Float) {
        val zone = RectF(left, 0f, right, h)
        if (mirrored() && bitmap === huesBitmap) {
            // Hue keeps running from the start edge, so it flips with the reading direction.
            canvas.save()
            canvas.scale(-1f, 1f, zone.centerX(), zone.centerY())
            canvas.drawBitmap(bitmap, null, zone, bitmapPaint)
            canvas.restore()
        } else {
            canvas.drawBitmap(bitmap, null, zone, bitmapPaint)
        }
    }

    private fun drawMarker(canvas: Canvas, w: Float, h: Float) {
        val r = (minOf(h / 2f, 15f * density) - density).coerceAtLeast(4f * density)
        val px = if (mirrored()) 1f - markX else markX
        val cx = (px * w).coerceIn(r + density, w - r - density)
        val cy = (markY * h).coerceIn(r + density, h - r - density)

        paint.style = Paint.Style.FILL
        paint.color = Color.argb(46, 0, 0, 0)
        canvas.drawCircle(cx, cy + 1.5f * density, r, paint)
        paint.color = colour
        canvas.drawCircle(cx, cy, r, paint)
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = 3f * density
        canvas.drawCircle(cx, cy, r - paint.strokeWidth / 2f, paint)
        paint.color = Color.argb(60, 0, 0, 0)
        paint.strokeWidth = density
        canvas.drawCircle(cx, cy, r, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                pickAt(event.x, event.y)
                val now = SystemClock.uptimeMillis()
                if (event.actionMasked == MotionEvent.ACTION_DOWN || now - lastSent >= THROTTLE_MS) {
                    send(now)
                }
                if (event.actionMasked == MotionEvent.ACTION_DOWN) performClick()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                send(SystemClock.uptimeMillis())
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun send(now: Long) {
        if (colour == lastSentColour && lastSent != 0L) return
        lastSent = now
        lastSentColour = colour
        onPicked?.invoke(colour)
    }

    private fun pickAt(x: Float, y: Float) {
        val u = (x / width).coerceIn(0f, 1f).let { if (mirrored()) 1f - it else it }
        markY = (y / height).coerceIn(0f, 1f)
        if (u < NEUTRAL_FRACTION + GAP_FRACTION / 2f) {
            markX = NEUTRAL_FRACTION / 2f
            colour = neutralAt(markY)
        } else {
            val hueSpan = 1f - NEUTRAL_FRACTION - GAP_FRACTION
            val t = ((u - NEUTRAL_FRACTION - GAP_FRACTION) / hueSpan).coerceIn(0f, 1f)
            markX = NEUTRAL_FRACTION + GAP_FRACTION + t * hueSpan
            colour = hueAt(t, markY)
        }
        invalidate()
    }

    private fun mirrored() = layoutDirection == LAYOUT_DIRECTION_RTL

    companion object {
        private const val NEUTRAL_FRACTION = 0.11f
        private const val GAP_FRACTION = 0.015f
        private const val NEUTRAL_SATURATION = 0.12f
        private const val TOP_L = 0.95f
        private const val BOTTOM_L = 0.26f
        private const val THROTTLE_MS = 40L
        private const val HUE_COLUMNS = 90
        private const val ROWS = 24

        /** Pure white and black at the two ends, so both are exactly reachable. */
        fun neutralAt(v: Float): Int = when {
            v <= 0.05f -> Color.WHITE
            v >= 0.95f -> Color.BLACK
            else -> TextoTint.withLightness(Color.GRAY, (1f - v).toDouble()) or 0xFF000000.toInt()
        }

        fun hueAt(t: Float, v: Float): Int {
            val seed = Color.HSVToColor(floatArrayOf(t * 360f, 1f, 1f))
            val lightness = TOP_L + (BOTTOM_L - TOP_L) * v
            return TextoTint.withLightness(seed, lightness.toDouble()) or 0xFF000000.toInt()
        }

        private val neutralsBitmap: Bitmap by lazy {
            Bitmap.createBitmap(1, ROWS, Bitmap.Config.ARGB_8888).apply {
                for (row in 0 until ROWS) setPixel(0, row, neutralAt(row / (ROWS - 1f)))
            }
        }

        private val huesBitmap: Bitmap by lazy {
            Bitmap.createBitmap(HUE_COLUMNS, ROWS, Bitmap.Config.ARGB_8888).apply {
                for (col in 0 until HUE_COLUMNS) for (row in 0 until ROWS) {
                    setPixel(col, row, hueAt(col / (HUE_COLUMNS - 1f), row / (ROWS - 1f)))
                }
            }
        }
    }
}
