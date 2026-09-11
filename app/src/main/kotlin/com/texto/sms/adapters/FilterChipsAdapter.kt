package com.texto.sms.adapters

import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.texto.sms.extensions.beGone
import com.texto.sms.extensions.beVisible
import com.texto.sms.databinding.ItemFilterChipBinding
import com.texto.sms.extensions.getScaledPx
import com.texto.sms.extensions.getScaledPxIn
import com.texto.sms.extensions.toUiDigits
import com.texto.sms.helpers.MessageFilter
import kotlin.math.hypot

/**
 * The three views a chip is built from. Passed to the styling callback as a unit so the
 * caller can tint the label and the count badge differently without re-finding them.
 */
data class FilterChipViews(
    val root: android.view.ViewGroup,
    val label: TextView,
    val count: TextView,
)

/**
 * Backs the home screen's filter chip row. Custom filters can be dragged to reorder (see
 * [com.texto.sms.helpers.FilterChipDragCallback]) or long-pressed (held without moving)
 * to open the editor; the built-in "All"/"Contacts only" chips and the trailing "+" chip are
 * excluded from both and stay in place.
 */
class FilterChipsAdapter(
    private val onSelect: (MessageFilter) -> Unit,
    private val onEditRequested: (MessageFilter) -> Unit,
    private val onAddRequested: () -> Unit,
    /**
     * Whether to append the trailing "+" chip. The search panel reuses this row to narrow a
     * search by the same audiences, where making a new filter is not on offer: the chip was
     * there, did nothing, and read as a control.
     */
    private val showAddChip: Boolean = true,
    private val styleChip: (chip: FilterChipViews, filterId: String, isActive: Boolean) -> Unit,
) : RecyclerView.Adapter<FilterChipsAdapter.ViewHolder>() {

    companion object {
        const val ADD_CHIP_ID = "__add__"

        // Shorter than the platform long-press timeout (~500ms) so this fires before
        // ItemTouchHelper's own drag-start ever would -- see the class doc on
        // FilterChipDragCallback for why that callback's long-press-drag is disabled.
        private const val LONG_PRESS_EDIT_DELAY_MS = 400L
    }

    /** Lets the adapter kick off a reorder drag itself once a held touch moves past slop. */
    var itemTouchHelper: ItemTouchHelper? = null

    private val addChipFilter = MessageFilter(id = ADD_CHIP_ID, label = "+")
    private var items: List<MessageFilter> = emptyList()
    private var activeFilterId: String = MessageFilter.ID_ALL
    private var counts: Map<String, Int> = emptyMap()

    private val longPressHandler = Handler(Looper.getMainLooper())
    private var pendingEditRunnable: Runnable? = null
    private var touchDownX = 0f
    private var touchDownY = 0f

    /**
     * [filters] should already include the built-in "All"/"Contacts only" chips; the "+" chip
     * is added here. [filterCounts] maps a filter id to how many conversations it holds; ids
     * missing from it simply show no badge.
     */
    fun submitFilters(
        filters: List<MessageFilter>,
        activeId: String,
        filterCounts: Map<String, Int> = emptyMap(),
    ) {
        items = if (showAddChip) filters + addChipFilter else filters
        activeFilterId = activeId
        counts = filterCounts
        notifyDataSetChanged()
    }

    /** Where the selected chip sits, or -1 while nothing in the row is selected. */
    fun activePosition(): Int = items.indexOfFirst { it.id == activeFilterId }

    /** The custom filters in their current on-screen order, for persisting after a drag. */
    fun currentCustomFilterOrder(): List<MessageFilter> = items.filter { it.isCustom }

    fun getFilterAt(position: Int): MessageFilter? = items.getOrNull(position)

    fun moveItem(fromPosition: Int, toPosition: Int) {
        items = items.toMutableList().apply { add(toPosition, removeAt(fromPosition)) }
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemFilterChipBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        // Through the activity, so the gap follows the UI-scale setting like every other
        // measurement. The receiver-less helper this used hardcoded a density of 2.5, so the
        // spacing was wrong on any device that is not xhdpi and never moved with the slider.
        val gap = (parent.context as? com.texto.sms.activities.SimpleActivity)
            ?.let { 8.getScaledPxIn(it) }
            ?: 8.getScaledPx(parent.context)
        (binding.root.layoutParams as? ViewGroup.MarginLayoutParams)?.marginEnd = gap
        return ViewHolder(
            FilterChipViews(
                root = binding.root,
                label = binding.filterChipLabel,
                count = binding.filterChipCount
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val filter = items[position]
        val chip = holder.chip.root
        holder.chip.label.text = filter.label

        // The "+" chip is an action, not a filter, so it never carries a count.
        val count = if (filter.id == ADD_CHIP_ID) null else counts[filter.id]
        holder.chip.count.apply {
            if (count == null) {
                beGone()
            } else {
                text = count.toUiDigits()
                beVisible()
            }
        }

        val isActive = filter.id == activeFilterId
        styleChip(holder.chip, filter.id, isActive)

        chip.setOnClickListener {
            when {
                filter.id == ADD_CHIP_ID -> onAddRequested()
                // Kept as a fallback alongside long-press: tapping the already-active
                // custom chip also opens the editor.
                filter.isCustom && filter.id == activeFilterId -> onEditRequested(filter)
                else -> onSelect(filter)
            }
        }

        if (filter.isCustom) {
            chip.setOnTouchListener { view, event -> handleCustomChipTouch(view, event, holder) }
        } else {
            chip.setOnTouchListener(null)
        }
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        cancelPendingEdit()
    }

    /**
     * Held-without-moving opens the editor after [LONG_PRESS_EDIT_DELAY_MS]; held-then-moved
     * past touch slop cancels that and starts a reorder drag instead. Always returns false so
     * the chip's own click listener still runs normally for plain taps.
     */
    private fun handleCustomChipTouch(view: View, event: MotionEvent, holder: ViewHolder): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                cancelPendingEdit()
                touchDownX = event.rawX
                touchDownY = event.rawY
                val runnable = Runnable {
                    pendingEditRunnable = null
                    val filter = items.getOrNull(holder.bindingAdapterPosition)
                    if (filter != null && filter.isCustom) onEditRequested(filter)
                }
                pendingEditRunnable = runnable
                longPressHandler.postDelayed(runnable, LONG_PRESS_EDIT_DELAY_MS)
            }

            MotionEvent.ACTION_MOVE -> {
                if (pendingEditRunnable != null) {
                    val slop = ViewConfiguration.get(view.context).scaledTouchSlop
                    val moved = hypot((event.rawX - touchDownX).toDouble(), (event.rawY - touchDownY).toDouble())
                    if (moved > slop) {
                        cancelPendingEdit()
                        itemTouchHelper?.startDrag(holder)
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelPendingEdit()
        }
        return false
    }

    private fun cancelPendingEdit() {
        pendingEditRunnable?.let { longPressHandler.removeCallbacks(it) }
        pendingEditRunnable = null
    }

    override fun getItemCount() = items.size

    class ViewHolder(val chip: FilterChipViews) : RecyclerView.ViewHolder(chip.root)
}
