package com.texto.sms.adapters

import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.view.ActionMode
import androidx.recyclerview.widget.RecyclerView
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.views.TextoRecyclerView

/**
 * What an adapter that supports multi-select has to answer, so the state machine below can
 * drive it without knowing whether it is sitting on a plain `RecyclerView.Adapter` or a
 * `ListAdapter`.
 */
interface TextoSelectableAdapter {
    val activity: SimpleActivity
    val recyclerView: TextoRecyclerView
    fun notifyItemChangedAt(position: Int)
    fun getActionMenuId(): Int
    fun prepareActionMode(menu: Menu)
    fun actionItemPressed(id: Int)
    fun getSelectableItemCount(): Int
    fun getIsItemSelectable(position: Int): Boolean
    fun getItemSelectionKey(position: Int): Int?
    fun getItemKeyPosition(key: Int): Int
    fun onActionModeCreated()
    fun onActionModeDestroyed()
}

/**
 * The selection state machine behind every list here that supports multi-select: which rows
 * are selected, the contextual action mode that back-press and the platform expect to exist,
 * and the drag-to-select gesture.
 *
 * This replaces commons' `MyRecyclerViewAdapter` / `MyRecyclerViewListAdapter`, which
 * duplicated this same logic across two base classes because Kotlin has no multiple
 * inheritance. Composition does the same job once: `BaseTextoRecyclerViewAdapter` and
 * `BaseTextoRecyclerViewListAdapter` each hold one of these rather than each reimplementing
 * it, so the trickiest part of the contract -- selectItemRange, the drag gesture's range
 * math -- exists in exactly one place.
 *
 * The action mode itself is close to vestigial on purpose: every screen that uses this draws
 * its own selection bar (toggleCustomSelectionBar), and SimpleActivity hides the native one
 * the moment it appears. What the action mode is actually for is the lifecycle -- a real
 * ActionMode gives the system back button somewhere to go, and currentActionMode is what
 * toggleCustomSelectionBar calls finish() on when the custom bar's own Cancel is tapped.
 */
class TextoSelectionController(
    private val owner: TextoSelectableAdapter,
    private val itemClick: (Any) -> Unit,
) : TextoRecyclerView.DragListener {

    val selectedKeys = LinkedHashSet<Int>()
    private var lastLongPressedItem = -1
    private var actMode: ActionMode? = null

    /** [TextoRecyclerView.DragListener]: the row a long-press landed on, selected at once. */
    override fun selectItem(position: Int) {
        toggleItemSelection(true, position)
    }

    /**
     * [TextoRecyclerView.DragListener]: the drag has moved. Once it has covered more than one
     * row, the long-press range anchor is dropped -- a drag in progress is doing its own
     * continuous selection, and resuming the anchor-based range afterward would union the
     * drag's result with wherever the last long-press happened to land.
     */
    override fun selectRange(initialSelection: Int, current: Int, min: Int, max: Int) {
        selectItemRange(initialSelection, current, min, max)
        if (min != max) clearLastLongPressAnchor()
    }

    fun isOneItemSelected() = selectedKeys.size == 1

    fun isSelectionModeActive() = selectedKeys.isNotEmpty()

    /** Whether an action mode is currently up -- what a tap or long-press branches on. */
    fun isSelecting() = actMode != null

    /** Breaks the long-press-range anchor, so the next long-press starts a fresh range. */
    fun clearLastLongPressAnchor() {
        lastLongPressedItem = -1
    }

    /**
     * A key the caller already knows is selected can be un-selected safely; one that is not
     * cannot be re-selected if getIsItemSelectable refuses it. Deselecting an item that was
     * never selected is a no-op, not an error -- selectItemRange leans on exactly that.
     */
    fun toggleItemSelection(select: Boolean, pos: Int, updateActionMode: Boolean = true) {
        if (select && !owner.getIsItemSelectable(pos)) return
        val key = owner.getItemSelectionKey(pos) ?: return
        if (!select && !selectedKeys.contains(key)) return
        if (select) selectedKeys.add(key) else selectedKeys.remove(key)
        owner.notifyItemChangedAt(pos)
        if (updateActionMode) {
            actMode?.invalidate()
            if (selectedKeys.isEmpty()) finishActMode()
        }
    }

    /**
     * A long-press selects everything between it and the previous long-press, which is what
     * lets a shift-click-style range grow across several presses without a drag -- one tap at
     * the top of a run, a second at its end, both included.
     */
    fun itemLongClicked(position: Int) {
        owner.recyclerView.setDragSelectActive(position)
        val from = if (lastLongPressedItem == -1) position else minOf(lastLongPressedItem, position)
        val to = if (lastLongPressedItem == -1) position else maxOf(lastLongPressedItem, position)
        for (i in from..to) toggleItemSelection(true, i, updateActionMode = false)
        actMode?.invalidate()
        lastLongPressedItem = position
    }

    /**
     * The drag gesture's range math: from is where the drag started, to is where the finger
     * is now, min/max are the furthest points reached in either direction across the whole
     * gesture so far -- needed because dragging past a row and back has to deselect it, which
     * requires knowing it was ever reached.
     */
    fun selectItemRange(from: Int, to: Int, min: Int, max: Int) {
        if (from == to) {
            for (i in min..max) if (i != from) toggleItemSelection(false, i)
            return
        }
        if (to < from) {
            for (i in to..from) toggleItemSelection(true, i)
            if (min > -1 && min < to) {
                for (i in min until to) toggleItemSelection(false, i)
            }
            if (max > -1) {
                for (i in (from + 1)..max) toggleItemSelection(false, i)
            }
        } else {
            for (i in from..to) toggleItemSelection(true, i)
            if (max > -1 && max > to) {
                for (i in (to + 1)..max) toggleItemSelection(false, i)
            }
            if (min > -1 && min < from) {
                for (i in min until from) toggleItemSelection(false, i)
            }
        }
    }

    fun selectAll(itemCount: Int) {
        for (i in 0 until itemCount) toggleItemSelection(true, i, updateActionMode = false)
        lastLongPressedItem = -1
        actMode?.invalidate()
    }

    fun getSelectedItemPositions(sortDescending: Boolean = false): ArrayList<Int> {
        val positions = ArrayList<Int>()
        for (key in selectedKeys.toList()) {
            val position = owner.getItemKeyPosition(key)
            if (position != -1) positions.add(position)
        }
        if (sortDescending) positions.sortDescending()
        return positions
    }

    fun finishActMode() {
        actMode?.finish()
    }

    /**
     * Binds [item] to [holder]'s row via [callback], then wires the row's tap and long-press.
     *
     * The callback runs first regardless of [allowSingleClick] -- it is how the adapter paints
     * the row itself, not conditional on whether the row ends up interactive. Setting the tag
     * is what lets [TextoRecyclerView] map a drag position back to an adapter position.
     */
    fun bindView(
        holder: RecyclerView.ViewHolder,
        item: Any,
        allowSingleClick: Boolean,
        allowLongClick: Boolean,
        callback: (View, Int) -> Unit,
    ): View {
        val itemView = holder.itemView
        itemView.tag = holder
        callback(itemView, holder.adapterPosition)
        if (allowSingleClick) {
            itemView.setOnClickListener { viewClicked(holder, item) }
            itemView.setOnLongClickListener {
                if (allowLongClick) viewLongClicked(holder) else viewClicked(holder, item)
                true
            }
        } else {
            itemView.setOnClickListener(null)
            itemView.setOnLongClickListener(null)
        }
        return itemView
    }

    fun viewClicked(holder: RecyclerView.ViewHolder, item: Any) {
        if (isSelecting()) {
            val position = holder.adapterPosition
            val isSelected = selectedKeys.contains(owner.getItemSelectionKey(position))
            toggleItemSelection(!isSelected, position)
        } else {
            itemClick(item)
        }
        clearLastLongPressAnchor()
    }

    fun viewLongClicked(holder: RecyclerView.ViewHolder) {
        val position = holder.adapterPosition
        if (!isSelecting()) startActionModeIfNeeded()
        itemLongClicked(position)
    }

    /**
     * Starts (or reuses) the action mode, on the first item selected.
     *
     * getActionMenuId() == 0 marks an adapter that never selects at all (search results, the
     * new-conversation contact list): nothing here is reachable for it since its rows never
     * answer getIsItemSelectable with true, but the guard is kept faithful to the contract
     * those adapters implement rather than relying on that.
     */
    fun startActionModeIfNeeded() {
        if (actMode != null || owner.getActionMenuId() == 0) return
        actMode = owner.activity.startSupportActionMode(object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                selectedKeys.clear()
                owner.activity.menuInflater.inflate(owner.getActionMenuId(), menu)
                owner.onActionModeCreated()
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                owner.prepareActionMode(menu)
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                owner.actionItemPressed(item.itemId)
                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                // Deselect one at a time rather than just clearing the set, so every row that
                // was highlighted gets the notifyItemChanged that un-highlights it.
                for (key in selectedKeys.toHashSet()) {
                    val position = owner.getItemKeyPosition(key)
                    if (position != -1) toggleItemSelection(false, position, updateActionMode = false)
                }
                selectedKeys.clear()
                actMode = null
                lastLongPressedItem = -1
                owner.onActionModeDestroyed()
            }
        })
    }
}
