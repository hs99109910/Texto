package com.texto.sms.helpers

import android.text.Spannable
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.UnderlineSpan
import android.view.MotionEvent
import android.view.View
import android.widget.TextView

/**
 * Makes the figures inside a message tappable — account numbers, tracking codes, amounts,
 * one-time passwords — so a single number can be copied or forwarded without selecting
 * text by hand.
 */
object NumberSpans {

    /**
     * Digit runs joined across single spaces and commas, so a card or account number
     * written as `6037 9977 1234 5678` is one figure rather than four, and `5,000,000`
     * stays whole.
     *
     * Dashes and slashes deliberately do not join: they separate the parts of dates and
     * times (`1405/5/15-19:53`), which should not be glued into one long number.
     */
    private val numberPattern = Regex("""\d+(?:[ ,]\d+)*""")

    /** Digits only, which is what you actually want on the clipboard. */
    fun cleanNumber(raw: String) = raw.filter { it.isDigit() }

    fun findNumbers(text: String): List<MatchResult> =
        numberPattern.findAll(text).filter { cleanNumber(it.value).length >= 4 }.toList()

    /**
     * Applies tappable spans to [textView]. Returns true when at least one number was
     * found, so the caller knows whether the movement method is needed at all.
     */
    fun apply(textView: TextView, text: CharSequence, onNumberTapped: (String) -> Unit): Boolean {
        val matches = findNumbers(text.toString())
        if (matches.isEmpty()) {
            textView.movementMethod = null
            textView.text = text
            return false
        }

        val spannable = SpannableString(text)
        matches.forEach { match ->
            val value = cleanNumber(match.value)
            spannable.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) = onNumberTapped(value)

                    override fun updateDrawState(ds: android.text.TextPaint) {
                        // Keep the bubble's own colour; the underline is the only hint.
                        ds.isUnderlineText = true
                    }
                },
                match.range.first,
                match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                UnderlineSpan(),
                match.range.first,
                match.range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        textView.text = spannable
        textView.movementMethod = SpanOnlyMovementMethod
        return true
    }

    /**
     * A movement method that consumes a touch only when it actually lands on a span.
     * The default LinkMovementMethod swallows every touch, which would stop the bubble
     * itself from being tapped or long-pressed.
     */
    private object SpanOnlyMovementMethod : LinkMovementMethod() {
        override fun onTouchEvent(
            widget: TextView,
            buffer: Spannable,
            event: MotionEvent,
        ): Boolean {
            val action = event.action
            if (action != MotionEvent.ACTION_UP && action != MotionEvent.ACTION_DOWN) {
                return false
            }

            var x = event.x.toInt() - widget.totalPaddingLeft + widget.scrollX
            var y = event.y.toInt() - widget.totalPaddingTop + widget.scrollY

            val layout = widget.layout ?: return false
            val line = layout.getLineForVertical(y)
            // A touch past the end of a line is not on the span, whatever getOffset says.
            if (x < layout.getLineLeft(line) || x > layout.getLineRight(line)) return false

            val offset = layout.getOffsetForHorizontal(line, x.toFloat())
            val links = buffer.getSpans(offset, offset, ClickableSpan::class.java)
            if (links.isEmpty()) return false

            if (action == MotionEvent.ACTION_UP) {
                links[0].onClick(widget)
            }
            return true
        }
    }
}
