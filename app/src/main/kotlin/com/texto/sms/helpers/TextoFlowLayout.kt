package com.texto.sms.helpers

import android.content.Context
import android.view.View
import android.view.ViewGroup

/**
 * Lays its children out left to right and wraps to a new line when the next one will not fit.
 *
 * The picked-sender chips need this and Android ships nothing that does it. A horizontal
 * scroller was the first answer and the wrong one: a filter with several senders hid most of
 * them behind an edge with nothing to say they were there, so the one screen that exists to
 * show what a filter covers showed about half of it. Wrapping means the sheet grows instead,
 * which is what a scrollable sheet is for.
 *
 * Margins are honoured, so a chip's own spacing works here exactly as it does in a row.
 */
// The attributes go to ViewGroup rather than being dropped: a view built from XML takes its
// id from them, and without it view binding cannot find the recipient chips and the activity
// fails to start.
class TextoFlowLayout @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
) : ViewGroup(context, attrs) {

    /** Vertical gap between wrapped lines, in pixels. Horizontal spacing is the child's margin. */
    var lineSpacing = 0

    private fun marginsOf(child: View) = child.layoutParams as? MarginLayoutParams

    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(attrs: android.util.AttributeSet?): LayoutParams =
        MarginLayoutParams(context, attrs)

    override fun generateLayoutParams(p: LayoutParams?): LayoutParams = MarginLayoutParams(p)

    override fun checkLayoutParams(p: LayoutParams?) = p is MarginLayoutParams

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingStart - paddingEnd
        var lineWidth = 0
        var lineHeight = 0
        var totalHeight = paddingTop + paddingBottom

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) continue
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
            val margins = marginsOf(child)
            val childWidth = child.measuredWidth + (margins?.leftMargin ?: 0) + (margins?.rightMargin ?: 0)
            val childHeight = child.measuredHeight + (margins?.topMargin ?: 0) + (margins?.bottomMargin ?: 0)

            if (lineWidth + childWidth > available && lineWidth > 0) {
                totalHeight += lineHeight + lineSpacing
                lineWidth = childWidth
                lineHeight = childHeight
            } else {
                lineWidth += childWidth
                lineHeight = maxOf(lineHeight, childHeight)
            }
        }
        totalHeight += lineHeight

        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            resolveSize(totalHeight, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // Right to left under Persian, so a wrapped chip row reads the same way the text in
        // it does. Everything below works in "distance from the line's start edge" and is
        // mirrored once, here, rather than at every use of x.
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val available = width - paddingStart - paddingEnd
        var lineStart = 0
        var lineTop = paddingTop
        var lineHeight = 0

        var i = 0
        while (i < childCount) {
            val child = getChildAt(i)
            if (child.visibility == GONE) {
                i++
                continue
            }
            val margins = marginsOf(child)
            val leading = if (isRtl) margins?.rightMargin ?: 0 else margins?.leftMargin ?: 0
            val trailing = if (isRtl) margins?.leftMargin ?: 0 else margins?.rightMargin ?: 0
            val childWidth = child.measuredWidth + leading + trailing
            val childHeight = child.measuredHeight + (margins?.topMargin ?: 0) + (margins?.bottomMargin ?: 0)

            if (lineStart + childWidth > available && lineStart > 0) {
                lineTop += lineHeight + lineSpacing
                lineStart = 0
                lineHeight = 0
            }

            val start = lineStart + leading
            val top = lineTop + (margins?.topMargin ?: 0)
            if (isRtl) {
                val right = width - paddingEnd - start
                child.layout(right - child.measuredWidth, top, right, top + child.measuredHeight)
            } else {
                val left = paddingStart + start
                child.layout(left, top, left + child.measuredWidth, top + child.measuredHeight)
            }

            lineStart += childWidth
            lineHeight = maxOf(lineHeight, childHeight)
            i++
        }
    }
}
