package com.texto.sms.adapters

import android.content.res.Resources
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.extensions.config
import com.texto.sms.extensions.getContrastColor
import com.texto.sms.views.TextoRecyclerView

/**
 * Replaces commons' `MyRecyclerViewListAdapter`: the selection contract from
 * [TextoSelectableAdapter], on top of `ListAdapter`'s diffing, for the three screens whose
 * rows come from `submitList` -- conversations, contacts, and a thread's messages.
 */
abstract class BaseTextoRecyclerViewListAdapter<T : Any>(
    override val activity: SimpleActivity,
    override val recyclerView: TextoRecyclerView,
    diffUtil: DiffUtil.ItemCallback<T>,
    val itemClick: (Any) -> Unit,
    val onRefresh: () -> Unit = {},
) : ListAdapter<T, BaseTextoRecyclerViewListAdapter<T>.ViewHolder>(diffUtil), TextoSelectableAdapter {

    protected val layoutInflater: LayoutInflater = activity.layoutInflater
    protected val resources: Resources = activity.resources

    /**
     * Cached at construction, matching commons' own timing: a theme change that happens while
     * this adapter is alive does not retint it on its own, which is what [updateTextColor] and
     * friends are for. Read straight off `config` rather than through commons' "proper colour"
     * getters -- those resolve the system's dynamic-colour palette when that setting is on,
     * which this app does not follow (see the ThreadActivity FAB and the schedule dialog's
     * time picker for the same call). With that off, commons' own getters reduce to exactly
     * these three `config` reads anyway.
     */
    protected var textColor: Int = activity.config.textColor
        private set
    protected var backgroundColor: Int = activity.config.backgroundColor
        private set
    protected var properPrimaryColor: Int = activity.config.primaryColor
        private set
    protected var contrastColor: Int = properPrimaryColor.getContrastColor()
        private set

    private val selection = TextoSelectionController(this, itemClick)

    protected val selectedKeys: LinkedHashSet<Int> get() = selection.selectedKeys

    protected fun isOneItemSelected() = selection.isOneItemSelected()
    protected fun isSelecting() = selection.isSelecting()
    protected fun finishActMode() = selection.finishActMode()
    protected fun selectAll() = selection.selectAll(itemCount)
    protected fun getSelectedItemPositions(sortDescending: Boolean = false) =
        selection.getSelectedItemPositions(sortDescending)

    /** MainActivity calls this after a language/theme change to retint an existing adapter. */
    fun updateTextColor(color: Int) {
        textColor = color
        onRefresh()
    }

    fun updatePrimaryColor() {
        properPrimaryColor = activity.config.primaryColor
        contrastColor = properPrimaryColor.getContrastColor()
    }

    fun updateBackgroundColor(color: Int) {
        backgroundColor = color
    }

    abstract override fun getActionMenuId(): Int
    abstract override fun prepareActionMode(menu: Menu)
    abstract override fun actionItemPressed(id: Int)
    abstract override fun getSelectableItemCount(): Int
    abstract override fun getIsItemSelectable(position: Int): Boolean
    abstract override fun getItemSelectionKey(position: Int): Int?
    abstract override fun getItemKeyPosition(key: Int): Int
    abstract override fun onActionModeCreated()
    abstract override fun onActionModeDestroyed()

    override fun notifyItemChangedAt(position: Int) = notifyItemChanged(position)

    /** Rows this app's own drag-to-select feature reaches for; only ThreadAdapter enables it. */
    protected fun setupDragListener(enabled: Boolean) {
        recyclerView.setupDragListener(if (enabled) selection else null)
    }

    protected fun createViewHolder(layoutId: Int, parent: ViewGroup): ViewHolder =
        ViewHolder(layoutInflater.inflate(layoutId, parent, false))

    protected fun createViewHolder(view: View): ViewHolder = ViewHolder(view)

    protected fun bindViewHolder(holder: ViewHolder) {
        holder.itemView.tag = holder
    }

    open inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bindView(
            item: Any,
            allowSingleClick: Boolean = true,
            allowLongClick: Boolean = true,
            callback: (View, Int) -> Unit,
        ): View = selection.bindView(this, item, allowSingleClick, allowLongClick, callback)

        /** A plain tap on this row: toggles it while selecting, opens it otherwise. */
        fun viewClicked(item: Any) = selection.viewClicked(this, item)

        /** Starts (or extends) the selection range at this row. */
        fun viewLongClicked() = selection.viewLongClicked(this)
    }
}
