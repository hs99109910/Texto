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

    /**
     * The band is a stripe down the middle rather than the whole view, so the lens marking
     * the chosen colour has room to stand proud of it at both edges.
     */
    private val bandTop get() = height * (1f - BAND_FRACTION) / 2f
    private val bandBottom get() = height - bandTop

    override fun onDraw(canvas: Canvas) {
        if (colours.isEmpty() || width == 0 || height == 0) return

        val top = bandTop
        val bottom = bandBottom
        val radius = (bottom - top) / 2f
        bounds.set(0f, top, width.toFloat(), bottom)
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
            segment.set(slot * step - 0.5f, top, (slot + 1) * step + 0.5f, bottom)
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
     * A lens: the chosen colour lifted out of the band as a disc that overhangs it top and
     * bottom, ringed in white and dropped on a soft shadow.
     *
     * This replaced two concentric rounded squares sitting flat inside the band. Those were
     * legible but read as a hole punched in the strip, and on a band of 24 tonalities the
     * square was wider than the segment it named, so it looked like a crop mark rather than a
     * choice. Standing the colour proud of the band says "this one" the way a handle does,
     * and it can carry the colour itself, so the mark is also a swatch.
     *
     * The white ring is what keeps it readable across the whole wheel: a single stroke in any
     * one colour disappears against some part of a strip running near white to near black,
     * and the shadow underneath separates the disc from a band of a similar tone.
     */
    private fun drawMarker(canvas: Canvas, step: Float) {
        val index = selectedIndex
        if (index !in colours.indices) return

        val lensRadius = height / 2f - density
        // With two dozen tonalities a segment is far narrower than the lens, so one simply
        // centred on the chosen colour hangs off the end at either extreme : at index 0 it
        // was drawn from -5px and half of it was clipped away. Sliding it back inside keeps
        // the whole disc visible, and which segment it names is still unambiguous because it
        // is the only one there.
        val centre = ((slotOf(index) + 0.5f) * step)
            .coerceIn(lensRadius + density, width - lensRadius - density)
        val middle = height / 2f

        // Drawn rather than a shadow layer: setShadowLayer needs the whole view in software,
        // which is a heavy price for one disc.
        markerOuter.style = Paint.Style.FILL
        markerOuter.color = Color.argb(38, 0, 0, 0)
        canvas.drawCircle(centre, middle + 1.5f * density, lensRadius, markerOuter)

        fill.color = colours[index]
        canvas.drawCircle(centre, middle, lensRadius, fill)

        markerInner.style = Paint.Style.STROKE
        markerInner.color = Color.WHITE
        markerInner.strokeWidth = 3f * density
        canvas.drawCircle(centre, middle, lensRadius - markerInner.strokeWidth / 2f, markerInner)

        // A hairline outside the white, so the lens still has an edge when the colour it
        // carries is itself near white.
        markerOuter.style = Paint.Style.STROKE
        markerOuter.color = Color.argb(46, 0, 0, 0)
        markerOuter.strokeWidth = density
        canvas.drawCircle(centre, middle, lensRadius, markerOuter)
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

    private companion object {
        /** How much of the view height the band itself takes; the rest is lens overhang. */
        const val BAND_FRACTION = 0.58f
    }

    private fun indexAt(x: Float): Int {
        val step = width.toFloat() / colours.size
        val slot = (x / step).toInt().coerceIn(0, colours.size - 1)
        return slotOf(slot)
    }
}
