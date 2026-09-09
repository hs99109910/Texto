package com.texto.sms.helpers

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * One band of colours, chosen by touching along it.
 *
 * A capsule rather than a row of tiles, because that is what every other surface in this app
 * is, and because a band with no gaps in it reads as one continuous choice -- which is what
 * picking a hue, or a weight of one hue, actually is. Built as a single view that draws its
 * own segments instead of a `LinearLayout` of coloured `View`s: the ends have to be clipped
 * by the capsule, and eleven children each carrying their own rounded background is both
 * slower and impossible to round only at the two outer corners.
 *
 * The same widget serves the picker's two rows and the settings screen's tonality strip, so
 * choosing a colour looks the same wherever it is done.
 */
class TextoHueStrip @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val markerInner = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val markerOuter = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val clip = Path()
    private val bounds = RectF()
    private val segment = RectF()
    private val marker = RectF()

    private var colours: List<Int> = emptyList()

    /** Index currently marked, or -1 for none. */
    var selectedIndex: Int = -1
        private set

    /** Fires while a finger is down as well as on lift, so the screen behind can preview. */
    var onPicked: ((Int) -> Unit)? = null

    private val density get() = resources.displayMetrics.density

    fun submit(colours: List<Int>, selectedIndex: Int) {
        this.colours = colours
        this.selectedIndex = selectedIndex.coerceIn(-1, colours.size - 1)
        invalidate()
    }

    /** Moves the marker without reporting a pick, for when the choice came from elsewhere. */
    fun setSelectedSilently(index: Int) {
        if (index == selectedIndex) return
        selectedIndex = index.coerceIn(-1, colours.size - 1)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        if (colours.isEmpty() || width == 0 || height == 0) return

        val radius = height / 2f
        bounds.set(0f, 0f, width.toFloat(), height.toFloat())
        clip.reset()
        clip.addRoundRect(bounds, radius, radius, Path.Direction.CW)

        val saved = canvas.save()
        canvas.clipPath(clip)

        // Half a pixel of overlap between neighbours, or antialiasing leaves a hairline of
        // the background showing at every seam and the band stops reading as one object.
        val step = width.toFloat() / colours.size
        colours.forEachIndexed { index, colour ->
            val slot = slotOf(index)
            fill.color = colour
            segment.set(slot * step - 0.5f, 0f, (slot + 1) * step + 0.5f, height.toFloat())
            canvas.drawRect(segment, fill)
        }
        canvas.restoreToCount(saved)

        // The same hairline every glass surface carries, keyed off the middle colour so it
        // sits correctly on a band that is mostly light or mostly dark.
        rim.color = TextoGlass.rimFor(colours[colours.size / 2], 0.30f)
        rim.strokeWidth = 1f.coerceAtLeast(density)
        val half = rim.strokeWidth / 2f
        bounds.inset(half, half)
        canvas.drawRoundRect(bounds, radius, radius, rim)

        drawMarker(canvas, step)
    }

    /**
     * Two concentric rounded squares, white inside a near black.
     *
     * A single stroke cannot work here: any one colour disappears against some part of a band
     * that runs the whole wheel from near white to near black. A light ring with a dark one
     * behind it is legible on every hue, which is why the mark on a colour picker is nearly
     * always drawn this way.
     */
    private fun drawMarker(canvas: Canvas, step: Float) {
        val index = selectedIndex
        if (index !in colours.indices) return

        val inset = height * 0.22f
        val side = (height - inset * 2f).coerceAtLeast(2f)
        // With two dozen tonalities a segment is far narrower than the marker, so a marker
        // simply centred on the chosen one hangs off the end of the band at either extreme :
        // at index 0 it was drawn from -5px and the capsule clipped its left half away.
        // Sliding it back inside keeps the whole ring visible; which segment it names is
        // still unambiguous, because it is the only one there is.
        val edge = side / 2f + inset
        val centre = ((slotOf(index) + 0.5f) * step).coerceIn(edge, width - edge)
        marker.set(centre - side / 2f, inset, centre + side / 2f, height - inset)
        val corner = side * 0.32f

        markerOuter.color = Color.argb(120, 0, 0, 0)
        markerOuter.strokeWidth = 3f * density
        canvas.drawRoundRect(marker, corner, corner, markerOuter)

        markerInner.color = Color.WHITE
        markerInner.strokeWidth = 2f * density
        canvas.drawRoundRect(marker, corner, corner, markerInner)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (colours.isEmpty()) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                val index = indexAt(event.x)
                if (index != selectedIndex) {
                    selectedIndex = index
                    invalidate()
                    onPicked?.invoke(index)
                }
                if (event.actionMasked == MotionEvent.ACTION_DOWN) performClick()
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    /**
     * Which physical slot a colour occupies. The band runs from the reader's start edge, so
     * under Persian the first colour is on the right; painting and hit testing both go
     * through here, or a tap would land on the segment mirrored from the one touched.
     */
    private fun slotOf(index: Int): Int =
        if (layoutDirection == LAYOUT_DIRECTION_RTL) colours.size - 1 - index else index

    private fun indexAt(x: Float): Int {
        val step = width.toFloat() / colours.size
        val slot = (x / step).toInt().coerceIn(0, colours.size - 1)
        return slotOf(slot)
    }
}
