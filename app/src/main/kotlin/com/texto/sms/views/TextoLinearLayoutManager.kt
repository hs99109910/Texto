package com.texto.sms.views

import android.content.Context
import androidx.recyclerview.widget.LinearLayoutManager

/**
 * Replaces commons' `MyLinearLayoutManager`. Predictive item animations are off: RecyclerView's
 * own pre/post-layout diffing assumes stable row heights, and a conversation row's height
 * changes with its content (a draft marker, a pinned badge, the preview line wrapping), which
 * is exactly what predictive animation gets wrong -- rows jumping or flickering on a refresh.
 */
class TextoLinearLayoutManager(context: Context) : LinearLayoutManager(context) {
    override fun supportsPredictiveItemAnimations() = false
}
