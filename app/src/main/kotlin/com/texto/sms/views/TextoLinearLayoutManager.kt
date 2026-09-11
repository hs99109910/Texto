package com.texto.sms.views

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.LinearLayoutManager

/**
 * Replaces commons' `MyLinearLayoutManager`. Predictive item animations are off: RecyclerView's
 * own pre/post-layout diffing assumes stable row heights, and a conversation row's height
 * changes with its content (a draft marker, a pinned badge, the preview line wrapping), which
 * is exactly what predictive animation gets wrong -- rows jumping or flickering on a refresh.
 */
class TextoLinearLayoutManager : LinearLayoutManager {
    constructor(context: Context) : super(context)

    /** The four-arg constructor `app:layoutManager="..."` in XML actually instantiates. */
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    override fun supportsPredictiveItemAnimations() = false
}
