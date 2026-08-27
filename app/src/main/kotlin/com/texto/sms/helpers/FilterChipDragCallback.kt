package com.texto.sms.helpers

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.texto.sms.adapters.FilterChipsAdapter

/**
 * Lets a custom filter chip be dragged left/right to reorder it. Built-in chips
 * ("All"/"Contacts only") and the trailing "+" chip report no movement flags, so they can
 * neither be dragged nor be a drop target -- they stay fixed at their positions.
 *
 * Long-press drag detection is left to [FilterChipsAdapter] instead of this callback's usual
 * built-in gesture recognizer: the adapter also wants to open the filter editor on a plain
 * long-press-without-movement, and running two independent long-press timers (this one and
 * the adapter's) would race unpredictably. So [isLongPressDragEnabled] is off here, and the
 * adapter calls [androidx.recyclerview.widget.ItemTouchHelper.startDrag] itself as soon as a
 * held touch moves past touch slop, preempting its own pending "open editor" callback.
 */
class FilterChipDragCallback(
    private val adapter: FilterChipsAdapter,
    private val onReorderFinished: (List<MessageFilter>) -> Unit,
) : ItemTouchHelper.Callback() {

    override fun isLongPressDragEnabled() = false
    override fun isItemViewSwipeEnabled() = false

    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
        val position = viewHolder.bindingAdapterPosition
        val filter = adapter.getFilterAt(position)
        if (filter == null || !filter.isCustom) return 0
        return makeMovementFlags(ItemTouchHelper.START or ItemTouchHelper.END, 0)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean {
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
        val targetFilter = adapter.getFilterAt(to) ?: return false
        if (!targetFilter.isCustom) return false
        adapter.moveItem(from, to)
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        onReorderFinished(adapter.currentCustomFilterOrder())
    }
}
