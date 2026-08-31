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

    /**
     * Web addresses, with or without a scheme. `www.example.com` and bare `example.ir` are
     * both linked; the trailing-character class deliberately excludes `.`, `,`, `)` and the
     * Persian comma so a URL at the end of a sentence does not swallow its punctuation.
     *
     * Matched before numbers, and numbers falling inside a URL are skipped -- otherwise the
     * digits in `example.com/track/12345` would each become their own tappable span sitting
     * on top of the link.
     */
    private val urlPattern = Regex(
        """(?:https?://|www\.)[^\s<>"'،؛]+|""" +
            """(?<![@\w.])[\w-]+(?:\.[\w-]+)*\.(?:com|net|org|ir|info|io|me|co|dev|app|xyz|biz|shop|site|online|gov|edu)(?::\d+)?(?:/[^\s<>"'،؛]*)?""",
        RegexOption.IGNORE_CASE
    )

    /** Digits only, which is what you actually want on the clipboard. */
    fun cleanNumber(raw: String) = raw.filter { it.isDigit() }

    fun findNumbers(text: String): List<MatchResult> =
        numberPattern.findAll(text).filter { cleanNumber(it.value).length >= 4 }.toList()

    fun findUrls(text: String): List<MatchResult> = urlPattern.findAll(text).toList()

    /** What to hand the browser: a bare host needs a scheme bolted on to resolve. */
    fun normalizeUrl(raw: String): String =
        if (raw.startsWith("http://", true) || raw.startsWith("https://", true)) raw else "https://$raw"

    /**
     * Applies tappable spans to [textView]: web addresses first, then any figure that is not
     * already part of one. Returns true when at least one span was added, so the caller knows
     * whether the movement method is needed at all.
     */
    fun apply(
        textView: TextView,
        text: CharSequence,
        onNumberTapped: (String) -> Unit,
        onUrlTapped: ((String) -> Unit)? = null,
    ): Boolean {
        val raw = text.toString()
        val urls = if (onUrlTapped == null) emptyList() else findUrls(raw)
        val urlRanges = urls.map { it.range }
        val numbers = findNumbers(raw).filterNot { match ->
            urlRanges.any { match.range.first >= it.first && match.range.last <= it.last }
        }

        if (urls.isEmpty() && numbers.isEmpty()) {
            textView.movementMethod = null
            textView.text = text
            return false
        }

        val spannable = SpannableString(text)

        fun mark(range: IntRange, onTap: () -> Unit) {
            spannable.setSpan(
                object : ClickableSpan() {
                    override fun onClick(widget: View) = onTap()

                    override fun updateDrawState(ds: android.text.TextPaint) {
                        // Keep the bubble's own colour; the underline is the only hint.
                        ds.isUnderlineText = true
                    }
                },
                range.first,
                range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                UnderlineSpan(),
                range.first,
                range.last + 1,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        urls.forEach { match ->
            val target = normalizeUrl(match.value)
            mark(match.range) { onUrlTapped?.invoke(target) }
        }
        numbers.forEach { match ->
            val value = cleanNumber(match.value)
            mark(match.range) { onNumberTapped(value) }
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
