package com.texto.sms.views

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.recyclerview.widget.RecyclerView
import com.texto.sms.R

/**
 * A RecyclerView that can turn a drag gesture into a range of selected rows.
 *
 * This replaces commons' `MyRecyclerView`, minus the two features nothing in this app ever
 * asked for -- pinch-zoom (`setupZoomListener` is never called) and endless-scroll paging
 * (`EndlessScrollListener`/`RecyclerScrollCallback` are never referenced either) -- confirmed
 * by grep before dropping either. The one feature that is load-bearing is the thread screen's
 * drag-to-select: long-press a message, then drag across the list, and every row the finger
 * passes over joins the selection, with a stripe near either edge auto-scrolling the list so
 * the gesture is not bounded by what is on screen.
 */
open class TextoRecyclerView : RecyclerView {

    /** What the owning adapter hears back as a drag unfolds. */
    interface DragListener {
        /** The row the drag started on -- selected immediately, before any movement. */
        fun selectItem(position: Int)

        /**
         * The row the finger is over now, and the extremes explored so far.
         *
         * [min]/[max] track the drag's whole history, not just this move: dragging past a row
         * and back has to unselect it, which needs to know it was ever reached at all.
         */
        fun selectRange(initialSelection: Int, current: Int, min: Int, max: Int)
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    private val autoScrollHandler = Handler(Looper.getMainLooper())
    private val autoScrollDelayMs = 25L
    private val autoScrollRunnable = object : Runnable {
        override fun run() {
            if (inTopHotspot) {
                scrollBy(0, -autoScrollVelocity)
                autoScrollHandler.postDelayed(this, autoScrollDelayMs)
            } else if (inBottomHotspot) {
                scrollBy(0, autoScrollVelocity)
                autoScrollHandler.postDelayed(this, autoScrollDelayMs)
            }
        }
    }

    private var dragListener: DragListener? = null
    private var isDragSelectionEnabled = false
    private var dragSelectActive = false
    private var lastDraggedIndex = -1
    private var minReached = -1
    private var maxReached = -1
    private var initialSelection = -1

    private val hotspotHeight =
        resources.getDimensionPixelSize(R.dimen.texto_dragselect_hotspot_height)
    private var hotspotTopBoundStart = 0
    private var hotspotTopBoundEnd = 0
    private var hotspotBottomBoundStart = 0
    private var hotspotBottomBoundEnd = 0
    private var autoScrollVelocity = 0
    private var inTopHotspot = false
    private var inBottomHotspot = false

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, heightSpec)
        if (hotspotHeight > -1) {
            hotspotTopBoundStart = 0
            hotspotTopBoundEnd = hotspotHeight
            hotspotBottomBoundStart = measuredHeight - hotspotHeight
            hotspotBottomBoundEnd = measuredHeight
        }
    }

    fun setupDragListener(listener: DragListener?) {
        isDragSelectionEnabled = listener != null
        dragListener = listener
    }

    /** Called on the row a long-press landed on, to arm the drag that may follow it. */
    fun setDragSelectActive(startPosition: Int) {
        if (dragSelectActive || !isDragSelectionEnabled) return
        lastDraggedIndex = -1
        minReached = -1
        maxReached = -1
        initialSelection = startPosition
        dragSelectActive = true
        dragListener?.selectItem(startPosition)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (!dragSelectActive) {
            try {
                super.dispatchTouchEvent(ev)
            } catch (_: Exception) {
                // A view mid-detach during a fast fling can throw here; the touch stream is
                // already lost at that point, and there is nothing left to hand it to.
            }
        }
        return when (ev.action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragSelectActive = false
                inTopHotspot = false
                inBottomHotspot = false
                autoScrollHandler.removeCallbacks(autoScrollRunnable)
                true
            }

            MotionEvent.ACTION_MOVE -> {
                if (dragSelectActive) handleDragMove(ev)
                true
            }

            else -> true
        }
    }

    private fun handleDragMove(ev: MotionEvent) {
        val itemPos = getItemPositionAt(ev.x, ev.y)

        if (hotspotHeight > -1) {
            val y = ev.y
            if (y in hotspotTopBoundStart.toFloat()..hotspotTopBoundEnd.toFloat()) {
                inBottomHotspot = false
                if (!inTopHotspot) {
                    inTopHotspot = true
                    autoScrollHandler.removeCallbacks(autoScrollRunnable)
                    autoScrollHandler.postDelayed(autoScrollRunnable, autoScrollDelayMs)
                }
                autoScrollVelocity =
                    (((hotspotTopBoundEnd - hotspotTopBoundStart) - (y - hotspotTopBoundStart)) / 2).toInt()
            } else if (y in hotspotBottomBoundStart.toFloat()..hotspotBottomBoundEnd.toFloat()) {
                inTopHotspot = false
                if (!inBottomHotspot) {
                    inBottomHotspot = true
                    autoScrollHandler.removeCallbacks(autoScrollRunnable)
                    autoScrollHandler.postDelayed(autoScrollRunnable, autoScrollDelayMs)
                }
                autoScrollVelocity =
                    (((y + hotspotBottomBoundEnd) - (hotspotBottomBoundStart + hotspotBottomBoundEnd)) / 2).toInt()
            } else if (inTopHotspot || inBottomHotspot) {
                autoScrollHandler.removeCallbacks(autoScrollRunnable)
                inTopHotspot = false
                inBottomHotspot = false
            }
        }

        if (itemPos != -1 && lastDraggedIndex != itemPos) {
            lastDraggedIndex = itemPos
            if (minReached == -1) minReached = lastDraggedIndex
            if (maxReached == -1) maxReached = lastDraggedIndex
            if (lastDraggedIndex > maxReached) maxReached = lastDraggedIndex
            if (lastDraggedIndex < minReached) minReached = lastDraggedIndex
            dragListener?.selectRange(initialSelection, lastDraggedIndex, minReached, maxReached)
            if (initialSelection == lastDraggedIndex) {
                minReached = lastDraggedIndex
                maxReached = lastDraggedIndex
            }
        }
    }

    /**
     * The adapter position under a point, via the row's own tag rather than a hit-test math.
     * `TextoViewHolder` sets `itemView.tag = this` on every bind, which is what this reads.
     */
    private fun getItemPositionAt(x: Float, y: Float): Int {
        val view = findChildViewUnder(x, y) ?: return -1
        val holder = view.tag as? RecyclerView.ViewHolder ?: return -1
        return holder.adapterPosition
    }
}
