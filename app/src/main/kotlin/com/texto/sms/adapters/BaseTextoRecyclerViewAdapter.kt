package com.texto.sms.adapters

import android.content.res.Resources
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.extensions.config
import com.texto.sms.extensions.getContrastColor
import com.texto.sms.views.TextoRecyclerView

/**
 * Replaces commons' `MyRecyclerViewAdapter`: a plain, non-diffing adapter with the same
 * selection contract as [BaseTextoRecyclerViewListAdapter], for the one screen that manages
 * its own list mutations directly rather than through a submitted list -- search results.
 */
abstract class BaseTextoRecyclerViewAdapter(
    override val activity: SimpleActivity,
    override val recyclerView: TextoRecyclerView,
    val itemClick: (Any) -> Unit,
) : RecyclerView.Adapter<BaseTextoRecyclerViewAdapter.ViewHolder>(), TextoSelectableAdapter {

    protected val layoutInflater: LayoutInflater = activity.layoutInflater
    protected val resources: Resources = activity.resources

    /** See the matching comment in [BaseTextoRecyclerViewListAdapter]. */
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
    protected fun getSelectedItemPositions(sortDescending: Boolean = false) =
        selection.getSelectedItemPositions(sortDescending)

    fun updateTextColor(color: Int) {
        textColor = color
        notifyDataSetChanged()
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
