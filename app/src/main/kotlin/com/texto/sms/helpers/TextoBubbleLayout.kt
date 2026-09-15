package com.texto.sms.helpers

import android.content.Context
import android.text.Layout
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.TextView

/**
 * A message bubble laid out the way Telegram lays one out: the body text, and the time (with
 * the delivery ticks) tucked into the bottom corner of the last line when that line leaves room
 * for it, or dropped onto a line of its own when it does not.
 *
 * Child 0 is the body [TextView], child 1 the meta view. The meta goes to whichever side the
 * last line ends on, read from the text layout's own paragraph direction, so an English message
 * inside the Persian UI and a Persian one inside the English UI both keep the time clear of
 * their text.
 */
class TextoBubbleLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : ViewGroup(context, attrs) {

    /** Space between the end of the last line and the meta, in pixels. */
    var metaGap = (6 * resources.displayMetrics.density).toInt()

    /** How far the inline meta sits below the last line's baseline region, in pixels. */
    var metaDrop = (2 * resources.displayMetrics.density).toInt()

    private var inline = false
    private var metaOnLeft = false

    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams =
        MarginLayoutParams(context, attrs)

    override fun generateLayoutParams(p: LayoutParams?): LayoutParams = MarginLayoutParams(p)

    override fun checkLayoutParams(p: LayoutParams?) = p is MarginLayoutParams

    private val body: TextView? get() = getChildAt(0) as? TextView
    private val meta: View? get() = if (childCount > 1) getChildAt(1) else null

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val hPad = paddingLeft + paddingRight
        val vPad = paddingTop + paddingBottom
        val body = body
        val meta = meta
        val childWidthSpec = if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        } else {
            MeasureSpec.makeMeasureSpec((MeasureSpec.getSize(widthMeasureSpec) - hPad).coerceAtLeast(0), MeasureSpec.AT_MOST)
        }
        val available = MeasureSpec.getSize(widthMeasureSpec) - hPad
        val unbounded = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED

        var metaW = 0
        var metaH = 0
        if (meta != null && meta.visibility != GONE) {
            meta.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            metaW = meta.measuredWidth
            metaH = meta.measuredHeight
        }

        if (body == null || body.visibility == GONE) {
            inline = false
            setMeasuredDimension(
                resolveSize(metaW + hPad, widthMeasureSpec),
                resolveSize(metaH + vPad, heightMeasureSpec)
            )
            return
        }

        body.measure(childWidthSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        val bodyW = body.measuredWidth
        val bodyH = body.measuredHeight

        val layout: Layout? = body.layout
        var lastLineW = bodyW
        metaOnLeft = layoutDirection == LAYOUT_DIRECTION_RTL
        if (layout != null && layout.lineCount > 0) {
            val last = layout.lineCount - 1
            lastLineW = (layout.getLineMax(last) + body.totalPaddingLeft + body.totalPaddingRight).toInt()
            metaOnLeft = layout.getParagraphDirection(last) == Layout.DIR_RIGHT_TO_LEFT
        }

        val inlineW = lastLineW + metaGap + metaW
        inline = metaW > 0 && (unbounded || inlineW <= available)
        val contentW: Int
        val contentH: Int
        if (inline) {
            contentW = maxOf(bodyW, inlineW)
            contentH = maxOf(bodyH, bodyH + metaDrop)
        } else {
            contentW = maxOf(bodyW, metaW)
            contentH = bodyH + metaH
        }
        setMeasuredDimension(
            resolveSize(contentW + hPad, widthMeasureSpec),
            resolveSize(contentH + vPad, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val innerLeft = paddingLeft
        val innerRight = width - paddingRight
        val innerBottom = height - paddingBottom
        val body = body
        val meta = meta

        var bodyBottom = paddingTop
        if (body != null && body.visibility != GONE) {
            // Text that reads right to left hugs the right edge, as its own lines do.
            val left = if (metaOnLeft) innerRight - body.measuredWidth else innerLeft
            body.layout(left, paddingTop, left + body.measuredWidth, paddingTop + body.measuredHeight)
            bodyBottom = paddingTop + body.measuredHeight
        }

        if (meta != null && meta.visibility != GONE) {
            val left = if (metaOnLeft) innerLeft else innerRight - meta.measuredWidth
            val bottom = if (inline) innerBottom else innerBottom.coerceAtLeast(bodyBottom + meta.measuredHeight)
            meta.layout(left, bottom - meta.measuredHeight, left + meta.measuredWidth, bottom)
        }
    }
}
