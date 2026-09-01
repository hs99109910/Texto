package com.texto.sms.helpers

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/**
 * The tonality strip under the theme row: drag it and the whole app's accent gradient slides
 * around the colour wheel.
 *
 * Zero sits at the CENTRE, not at an end. The value being chosen is a rotation away from the
 * skin's own hues, so the skin deserves the reference position: from the middle you can see
 * which way you have gone and get back with one drag. An end-anchored strip would bury the
 * untouched state in a corner and make "put it back" a hunt.
 *
 * Every metric is derived from the measured height rather than from dp constants, so the
 * control follows the app's UI-scale setting through whatever height the caller gives it.
 */
class TextoHueStrip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** The skin's own accent colour, BEFORE any rotation: the strip previews rotations of it. */
    var baseColor: Int = Color.parseColor("#2368C9")
        set(value) { field = value; shader = null; invalidate() }

    /** Colour of the centre notch, so it reads on a light card and a dark one alike. */
    var inkColor: Int = Color.BLACK
        set(value) { field = value; invalidate() }

    /** Current rotation in degrees, -180..180. */
    var shift: Int = 0
        set(value) {
            val clamped = value.coerceIn(-180, 180)
            if (field == clamped) return
            field = clamped
            invalidate()
        }

    /** Fired while dragging; [committed] is true once the finger lifts. */
    var onShiftChanged: ((degrees: Int, committed: Boolean) -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shader: LinearGradient? = null
    private val rect = RectF()

    private val thumbRadius get() = height / 2f
    private val trackHeight get() = height * 0.46f

    /**
     * Rotations of the skin's own accent rather than a raw HSV rainbow. A rainbow would
     * promise saturations and brightnesses this skin never uses, so the colour dragged to
     * would not be the colour you got.
     */
    private fun buildShader(): LinearGradient {
        val stops = 25
        val colors = IntArray(stops) { i ->
            TextoTint.rotateHue(baseColor, -180 + (360 * i / (stops - 1)))
        }
        return LinearGradient(rect.left, 0f, rect.right, 0f, colors, null, Shader.TileMode.CLAMP)
    }

    override fun onDraw(canvas: Canvas) {
        if (width == 0 || height == 0) return
        val r = thumbRadius
        rect.set(r, (height - trackHeight) / 2f, width - r, (height + trackHeight) / 2f)

        val sh = shader ?: buildShader().also { shader = it }
        paint.shader = sh
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(rect, trackHeight / 2f, trackHeight / 2f, paint)
        paint.shader = null

        // the notch marking the skin's untouched hue
        val centreX = rect.centerX()
        paint.color =
            Color.argb(120, Color.red(inkColor), Color.green(inkColor), Color.blue(inkColor))
        paint.strokeWidth = height * 0.05f
        paint.style = Paint.Style.STROKE
        canvas.drawLine(
            centreX, rect.top - height * 0.10f, centreX, rect.bottom + height * 0.10f, paint
        )

        // the thumb, carrying the colour the current rotation actually produces
        val x = rect.left + (shift + 180) / 360f * rect.width()
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawCircle(x, height / 2f, r, paint)
        paint.color = TextoTint.rotateHue(baseColor, shift)
        canvas.drawCircle(x, height / 2f, r * 0.72f, paint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        shader = null
    }

    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var shiftAtDown = 0
    private var dragging = false

    /**
     * The strip sits in the middle of a long ScrollView, so it must not claim a gesture until
     * it knows the gesture is horizontal.
     *
     * Claiming on ACTION_DOWN instead -- which is what this did first -- meant any scroll of
     * the settings page whose finger happened to land on the strip was swallowed and rewrote
     * the app's accent instead of scrolling. It cost a theme, which is how it was found.
     */
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                shiftAtDown = shift
                dragging = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) {
                    val dx = Math.abs(event.x - downX)
                    val dy = Math.abs(event.y - downY)
                    if (dx < touchSlop || dx <= dy) return true
                    dragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                }
                shift = shiftAt(event.x)
                onShiftChanged?.invoke(shift, false)
                return true
            }
            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                val moved = Math.hypot(
                    (event.x - downX).toDouble(), (event.y - downY).toDouble()
                ) > touchSlop
                when {
                    // Committed wherever the drag ended.
                    dragging -> {
                        shift = shiftAt(event.x)
                        onShiftChanged?.invoke(shift, true)
                    }
                    // A tap that never became a drag is still a deliberate pick, the way it
                    // is on any slider.
                    !moved -> {
                        shift = shiftAt(event.x)
                        onShiftChanged?.invoke(shift, true)
                    }
                    // Moved, but never horizontally: a scroll the parent declined to take,
                    // usually because the list was already at its end. Committing here would
                    // rewrite the app's accent for someone who was only trying to scroll.
                    else -> shift = shiftAtDown
                }
                dragging = false
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                // The parent took the gesture after all: it was a scroll. Put back whatever
                // the value was before the finger landed, so scrolling never repaints the app.
                parent?.requestDisallowInterceptTouchEvent(false)
                if (dragging) {
                    shift = shiftAtDown
                    onShiftChanged?.invoke(shift, true)
                }
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /** Snaps near the centre, so getting back to the skin's own hue is a drag and not a fight. */
    private fun shiftAt(x: Float): Int {
        val track = (width - thumbRadius * 2).coerceAtLeast(1f)
        val t = ((x - thumbRadius) / track).coerceIn(0f, 1f)
        val degrees = (-180 + t * 360).roundToInt()
        return if (degrees in -6..6) 0 else degrees
    }
}
