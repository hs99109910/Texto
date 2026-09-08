package com.texto.sms.helpers

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.PathInterpolator

/**
 * The soft lozenge that marks where you are, and slides to say where you went.
 *
 * Both selectors in the app are the same object: the one behind the current tab in the
 * bottom capsule and the one behind the chosen filter chip at the top. They used to be
 * repainted -- the old one erased and a new one drawn a chip away -- which reads as a blink,
 * not as movement. This keeps a single rectangle and animates it, so the eye follows one
 * thing across the row and lands where the selection went.
 *
 * Two consumers, two ways of drawing it:
 *
 *  - a **view** whose bounds are animated (the bottom capsule's halo is a real View behind
 *    the tabs, so it can be laid out once and only translated after that), via [moveView];
 *  - a **canvas**, for the filter chips, where the chips live in a RecyclerView and recycle
 *    as it scrolls, so the halo is painted underneath them by an ItemDecoration rather than
 *    being a view that would have to be recycled with them. See [moveRect] and [draw].
 */
class TextoHalo(private val host: View) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private val current = RectF()
    private var hasPosition = false
    private var animator: ValueAnimator? = null

    var cornerRadius = 0f
    var isVisible = false
        private set

    fun setColors(fill: Int, stroke: Int, strokeWidthPx: Float) {
        fillPaint.color = fill
        strokePaint.color = stroke
        strokePaint.strokeWidth = strokeWidthPx
    }

    /** True while the halo is travelling, which is what stops a scroll from cutting it short. */
    val isTravelling get() = animator?.isRunning == true

    fun hide() {
        animator?.cancel()
        animator = null
        hasPosition = false
        if (isVisible) {
            isVisible = false
            host.invalidate()
        }
    }

    /**
     * Puts the halo somewhere without animating and *without* asking for a redraw.
     *
     * The chip row calls this from inside its own draw pass to keep the halo under a chip
     * that is scrolling: invalidating there would schedule another frame, which would draw,
     * which would invalidate again, and the row would never go idle.
     */
    fun snapTo(target: RectF) {
        if (target.isEmpty) {
            isVisible = false
            hasPosition = false
            return
        }
        animator?.cancel()
        animator = null
        current.set(target)
        hasPosition = true
        isVisible = true
    }

    /**
     * Moves the halo to [target], in [host]'s own coordinates.
     *
     * The first placement jumps: there is nothing to travel from, and animating in from a
     * zero rectangle at the corner would look like the selector being thrown onto the screen
     * every time the screen opens.
     */
    fun moveRect(target: RectF) {
        if (target.isEmpty) {
            hide()
            return
        }
        if (!hasPosition) {
            snapTo(target)
            host.invalidate()
            return
        }
        if (current == target) return
        isVisible = true
        animator?.cancel()

        val from = RectF(current)
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = TRAVEL_MS
            interpolator = TRAVEL_CURVE
            addUpdateListener {
                val t = it.animatedFraction
                current.set(
                    from.left + (target.left - from.left) * t,
                    from.top + (target.top - from.top) * t,
                    from.right + (target.right - from.right) * t,
                    from.bottom + (target.bottom - from.bottom) * t,
                )
                host.invalidate()
            }
            start()
        }
    }

    fun draw(canvas: Canvas) {
        if (!isVisible || !hasPosition) return
        canvas.drawRoundRect(current, cornerRadius, cornerRadius, fillPaint)
        if (strokePaint.strokeWidth > 0f) {
            val inset = strokePaint.strokeWidth / 2f
            canvas.drawRoundRect(
                current.left + inset, current.top + inset,
                current.right - inset, current.bottom - inset,
                cornerRadius, cornerRadius, strokePaint
            )
        }
    }

    companion object {
        /**
         * Long enough to be followed, short enough not to lag the tap. The curve overshoots
         * nothing: a selector that springs past its target and comes back reads as a toy,
         * where this should read as weight being carried across.
         */
        const val TRAVEL_MS = 260L
        val TRAVEL_CURVE: PathInterpolator = PathInterpolator(0.2f, 0f, 0f, 1f)

        /**
         * Animates a view onto a tab: its size is set outright and only the offset travels,
         * which is what keeps the movement cheap enough to stay smooth on a list that is
         * scrolling underneath it.
         */
        fun moveView(halo: View, target: View, animate: Boolean) {
            val parent = halo.parent as? View ?: return
            val width = target.width
            val height = target.height
            if (width == 0 || height == 0) return

            // The target lives in the tab row; the halo lives in the frame around it. Both
            // sit inside the same padding box, so the row's own origin is the frame's
            // padding and the difference below is exact -- and it stays exact when the
            // layout mirrors, which an absolute left would not.
            val x = (parent.paddingLeft + target.left - halo.left).toFloat()
            val y = (parent.paddingTop + target.top - halo.top).toFloat()

            val params = halo.layoutParams
            if (params.width != width || params.height != height) {
                params.width = width
                params.height = height
                halo.layoutParams = params
            }

            if (!animate || halo.width == 0) {
                halo.translationX = x
                halo.translationY = y
                return
            }
            halo.animate().cancel()
            halo.animate()
                .translationX(x)
                .translationY(y)
                .setDuration(TRAVEL_MS)
                .setInterpolator(TRAVEL_CURVE)
                .start()
        }
    }
}
