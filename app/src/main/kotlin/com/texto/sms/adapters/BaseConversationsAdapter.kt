package com.texto.sms.adapters

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Parcelable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getContrastColor
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.setupViewBackground
import org.fossify.commons.extensions.toast
import org.fossify.commons.helpers.FontHelper
import org.fossify.commons.helpers.SimpleContactsHelper
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.commons.views.MyRecyclerView
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.databinding.ItemConversationBinding
import com.texto.sms.extensions.*
import com.texto.sms.helpers.NovaGlass
import com.texto.sms.models.Conversation

@Suppress("LeakingThis")
abstract class BaseConversationsAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    onRefresh: () -> Unit,
    itemClick: (Any) -> Unit,
) : MyRecyclerViewListAdapter<Conversation>(
    activity = activity,
    recyclerView = recyclerView,
    diffUtil = ConversationDiffCallback(),
    itemClick = itemClick,
    onRefresh = onRefresh
),
    RecyclerViewFastScroller.OnPopupTextUpdate {
    var itemTouchHelper: androidx.recyclerview.widget.ItemTouchHelper? = null
    private var lastDragTime = 0L
    private var drafts = HashMap<Long, String>()

    // Drag State for Delayed Swap
    private var isDragging = false
    private var initialDragPosition = -1
    private var lastTargetPosition = -1
    private var hoveredPosition = -1
    private var pendingUpdate: ArrayList<Conversation>? = null
    private var pendingNotify = false
    private var suppressStateRestoration = false

    private var fontSize = activity.getScaledTextSize()
    private var iconSize = activity.getScaledDimen(org.fossify.commons.R.dimen.list_icon_size_medium)

    private var recyclerViewState: Parcelable? = null

    init {
        setHasStableIds(false) // Must be false because Top 2 are duplicates of items in the grid
        updateDrafts()

        registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                if (!suppressStateRestoration) restoreRecyclerViewState()
                updateCustomSelectionBar()
            }
            override fun onItemRangeMoved(fromPosition: Int, toPosition: Int, itemCount: Int) {
                if (!suppressStateRestoration) restoreRecyclerViewState()
            }

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                if (!suppressStateRestoration) restoreRecyclerViewState()
            }
            
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                updateCustomSelectionBar()
            }
            
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                updateCustomSelectionBar()
            }
        })
    }

    fun updateConversations(
        newConversations: ArrayList<Conversation>,
        shouldSuppressStateRestoration: Boolean = false,
        commitCallback: (() -> Unit)? = null,
    ) {
        if (isDragging) {
            pendingUpdate = newConversations
            return
        }

        // OPTIMIZATION: Check if the list actually changed to avoid redundant DiffUtil work.
        // Compares real equality, not just hashCode -- two different filtered lists can
        // collide on hashCode and silently keep a stale list on screen.
        if (currentList == newConversations) {
            commitCallback?.invoke()
            return
        }

        suppressStateRestoration = shouldSuppressStateRestoration
        if (!suppressStateRestoration) saveRecyclerViewState()
        submitList(newConversations.toList()) {
            commitCallback?.invoke()
            suppressStateRestoration = false // Reset after update is committed
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    fun updateDrafts() {
        ensureBackgroundThread {
            val newDrafts = HashMap<Long, String>()
            fetchDrafts(newDrafts)
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (drafts.hashCode() != newDrafts.hashCode()) {
                    drafts = newDrafts
                    safeNotifyDataSetChanged()
                }
            }
        }
    }

    override fun getSelectableItemCount() = itemCount

    protected fun getSelectedItems() = currentList.filter {
        selectedKeys.contains(it.hashCode())
    } as ArrayList<Conversation>

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = currentList.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = currentList.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {
        updateCustomSelectionBar()
    }

    override fun onActionModeDestroyed() {
        (activity as? SimpleActivity)?.toggleCustomSelectionBar(false)
        // Leaving selection clears the keys but rebinds nothing, so every badge and tint
        // stayed painted until the list was rebuilt from scratch.
        safeNotifyDataSetChanged()
    }

    /** Refreshes the selection bar straight away, without waiting for a data-set event. */
    private fun updateCustomSelectionBarNow() {
        recyclerView.post {
            if (activity.isFinishing || activity.isDestroyed) return@post
            updateCustomSelectionBar()
        }
    }

    private fun updateCustomSelectionBar() {
        val simpleActivity = activity as? SimpleActivity ?: return
        val count = selectedKeys.size
        if (count > 0) {
            val actions = getCustomActions()
            simpleActivity.toggleCustomSelectionBar(true, count, actions) { actionId ->
                if (actionId == R.id.selection_cancel) {
                    finishActMode()
                } else {
                    actionItemPressed(actionId)
                }
            }
        } else {
            simpleActivity.toggleCustomSelectionBar(false)
        }
    }

    abstract fun getCustomActions(): List<Int>

    fun isSelectionModeActive() = selectedKeys.isNotEmpty()

    override fun getItemViewType(position: Int): Int {
        // Every row in the new UI is a full-width card showing name and message body.
        return if (activity.config.useNewUi) VIEW_TYPE_RECENT else VIEW_TYPE_DEFAULT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = when (viewType) {
            VIEW_TYPE_RECENT -> com.texto.sms.databinding.ItemConversationRecentBinding.inflate(layoutInflater, parent, false)
            else -> ItemConversationBinding.inflate(layoutInflater, parent, false)
        }
        return createViewHolder(binding.root)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(HOVER_PAYLOAD)) {
            if (activity.config.useNewUi && position >= 2) {
                updateRecentHoverState(holder, position)
            }
            return
        }
        super.onBindViewHolder(holder, position, payloads)
    }

    private fun updateRecentHoverState(holder: ViewHolder, position: Int) {
        val conversation = getItem(position)
        com.texto.sms.databinding.ItemConversationRecentBinding.bind(holder.itemView).apply {
            val isSelected = selectedKeys.contains(conversation.hashCode())
            val isHoverTarget = hoveredPosition == position
            recentSelectionGlow.beVisibleIf(isSelected || isHoverTarget)
            recentSelectionGlow.background?.applyColorFilter(properPrimaryColor)
            recentSelectionGlow.alpha = if (isHoverTarget) 0.5f else 1.0f
            recentSelectionCheck.beVisibleIf(isSelected)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val conversation = getItem(position)
        holder.bindView(
            conversation,
            allowSingleClick = true, // Restore default click for opening threads
            allowLongClick = false    // Keep false to manage custom drag/select split
        ) { itemView, _ ->
            if (activity.config.useNewUi) {
                setupRecentView(itemView, conversation, position, holder)
            } else {
                setupView(itemView, conversation, holder)
            }
        }
        bindViewHolder(holder)
    }

    fun onDragStarted(position: Int) {
        if (!activity.config.useNewUi || position < 2) return
        android.util.Log.d("DRAG_DEBUG", "Drag STARTED at position: $position")
        isDragging = true
        pendingNotify = false
        initialDragPosition = position
        lastTargetPosition = position
        hoveredPosition = -1 // Reset hover target at start
        
        // Prevent the dragged View from being recycled by boosting the off-screen cache.
        // This stops ItemTouchHelper from cancelling the drag if we scroll far from the origin.
        recyclerView.setItemViewCacheSize(100)
        
        // Explicitly tell the LayoutManager/RecyclerView not to recycle the view we are holding
        recyclerView.findViewHolderForAdapterPosition(position)?.setIsRecyclable(false)
    }

    fun getInitialDragPosition() = initialDragPosition

    fun updateHoverTarget(toPosition: Int) {
        if (!isDragging || toPosition == hoveredPosition) return
        
        // Block target detection for the top "Recent" cards (0 and 1)
        val validTarget = if (toPosition >= 2 && toPosition != initialDragPosition) toPosition else -1
        
        val oldHover = hoveredPosition
        hoveredPosition = validTarget
        val newHover = hoveredPosition

        // Wrap UI updates in post to avoid IllegalStateException during scroll/layout
        recyclerView.post {
            if (!isDragging) return@post
            if (oldHover != -1) notifyItemChanged(oldHover, HOVER_PAYLOAD)
            if (newHover != -1) notifyItemChanged(newHover, HOVER_PAYLOAD)
        }
    }

    fun onDragEnded() {
        if (!isDragging) {
            android.util.Log.d("DRAG_DEBUG", "onDragEnded called but isDragging was FALSE (already handled or never started)")
            return
        }
        
        // Save state immediately
        val start = initialDragPosition
        val end = hoveredPosition 
        val oldHover = hoveredPosition

        android.util.Log.d("DRAG_DEBUG", "Drag ENDED. Start: $start, End (Hover): $end")

        // Immediately set isDragging to false to prevent multiple calls or interruptions
        isDragging = false
        initialDragPosition = -1
        lastTargetPosition = -1
        hoveredPosition = -1

        // Restore normal cache size
        recyclerView.setItemViewCacheSize(2)
        
        // Allow the previously dragged view to be recycled again
        if (start != -1) {
            recyclerView.findViewHolderForAdapterPosition(start)?.setIsRecyclable(true)
        }

        // Defer all UI updates to post to avoid IllegalStateException during scroll/layout
        recyclerView.post {
            // ... (rest of the post block remains the same)
            // Clear visual hover states immediately
            if (oldHover != -1) notifyItemChanged(oldHover, HOVER_PAYLOAD)
            
            // Force cleanup of the visual item view to prevent it getting stuck on screen
            val cleanupView = { view: View ->
                view.translationX = 0f
                view.translationY = 0f
                view.translationZ = 0f
                view.scaleX = 1f
                view.scaleY = 1f
                view.elevation = 0f
                view.alpha = 1f
            }

            if (start != -1) recyclerView.findViewHolderForAdapterPosition(start)?.itemView?.let { cleanupView(it) }
            if (end != -1) recyclerView.findViewHolderForAdapterPosition(end)?.itemView?.let { cleanupView(it) }
            
            // Comprehensive pass on all visible children
            for (i in 0 until recyclerView.childCount) {
                recyclerView.getChildAt(i)?.let { cleanupView(it) }
            }

            if (start >= 2 && end >= 2 && start != end) {
                // Atomic One-to-One Swap: Direct and Stable
                val list = currentList.toArrayList()
                if (start < list.size && end < list.size) {
                    java.util.Collections.swap(list, start, end)
                    
                    // Save the final order
                    val others = list.drop(2)
                    activity.config.conversationOrder = others.joinToString(",") { it.threadId.toString() }
                    
                    // Finalize the list state with a fresh copy to ensure clean animations
                    // SKIP state restoration here to prevent the "jump" and "flash"
                    suppressStateRestoration = true
                    submitList(list.toList()) {
                        suppressStateRestoration = false
                        // Visual cleanup
                        notifyItemChanged(start, HOVER_PAYLOAD)
                        notifyItemChanged(end, HOVER_PAYLOAD)
                    }
                }
            }

            // Process any refresh that happened during the drag
            pendingUpdate?.let { 
                val update = it
                pendingUpdate = null
                updateConversations(update)
            }
            
            if (pendingNotify) {
                pendingNotify = false
                safeNotifyDataSetChanged()
            }
        }
    }

    private val lastSenderCache = HashMap<Long, Int>()

    /**
     * thread id -> newest body and its type, read in one query alongside the list rather
     * than per row while binding. Plain data: it is replaced whenever the list is
     * refreshed, so there is nothing to invalidate and nothing that can go stale.
     */
    private var latestSnippets: Map<Long, Pair<String, Int>> = emptyMap()

    /** When [latestSnippets] was read, used to tell it apart from a fresher cached row. */
    private var latestSnippetsCapturedAt = 0L

    fun setLatestSnippets(snippets: Map<Long, Pair<String, Int>>) {
        latestSnippets = snippets
        latestSnippetsCapturedAt = System.currentTimeMillis()
        safeNotifyDataSetChanged()
    }

    /**
     * Newest body and type for a row, or null when the cached conversation itself is the
     * fresher source and should be shown instead.
     *
     * The snapshot only refreshes at the end of the slow telephony sync, while the cached
     * list is drawn immediately from the database. A thread whose newest message is dated
     * after the snapshot was taken cannot possibly be in it, so letting the snapshot win
     * there painted the previous message over a row the database already knew was newer --
     * the stale preview seen for a moment after backing out of a thread.
     */
    private fun freshestSnippet(conversation: Conversation): Pair<String, Int>? {
        if (conversation.date * 1000L > latestSnippetsCapturedAt) return null
        return latestSnippets[conversation.threadId]
    }

    /**
     * Icon menu beside a long-pressed conversation card, mirroring the one on message
     * bubbles. "Mark" just closes it, leaving the card selected for multi-select.
     */
    private fun showConversationActions(anchor: View, conversation: Conversation) {
        val simpleActivity = activity as? SimpleActivity ?: return
        if (simpleActivity.isFinishing || simpleActivity.isDestroyed) return

        val isPinned = activity.config.pinnedConversations.contains(conversation.threadId.toString())
        val items = arrayListOf(
            SimpleActivity.BubbleAction(
                R.id.cab_mark,
                simpleActivity.getString(R.string.mark_select),
                R.drawable.ic_check_circle_filled
            ),
            if (isPinned) {
                SimpleActivity.BubbleAction(
                    R.id.cab_unpin_conversation,
                    simpleActivity.getString(R.string.unpin_conversation),
                    R.drawable.ic_unpin_vector
                )
            } else {
                SimpleActivity.BubbleAction(
                    R.id.cab_pin_conversation,
                    simpleActivity.getString(R.string.pin_conversation),
                    R.drawable.ic_pin_vector
                )
            }
        )
        when {
            // These two lists show conversations that already left the main list; the
            // useful action here is getting them back, not archiving them again. Routing
            // "Archive" here sent an id (cab_archive) neither adapter's actionItemPressed()
            // handled, so the button silently did nothing.
            this is RecycleBinConversationsAdapter -> items.add(
                SimpleActivity.BubbleAction(
                    R.id.cab_restore,
                    simpleActivity.getString(R.string.restore_all_messages),
                    R.drawable.ic_unarchive_vector
                )
            )
            this is ArchivedConversationsAdapter -> items.add(
                SimpleActivity.BubbleAction(
                    R.id.cab_unarchive,
                    simpleActivity.getString(R.string.unarchive),
                    R.drawable.ic_unarchive_vector
                )
            )
            activity.config.isArchiveAvailable -> items.add(
                SimpleActivity.BubbleAction(
                    R.id.cab_archive,
                    simpleActivity.getString(R.string.archive),
                    R.drawable.ic_archive_vector
                )
            )
        }
        if (activity.config.customFilters.isNotEmpty() || activity.config.showAdsFilter) {
            items.add(
                SimpleActivity.BubbleAction(
                    R.id.cab_add_to_filter,
                    simpleActivity.getString(R.string.add_to_filter),
                    R.drawable.ic_filter_vector
                )
            )
        }
        items.add(
            SimpleActivity.BubbleAction(
                R.id.cab_delete,
                simpleActivity.getString(org.fossify.commons.R.string.delete),
                R.drawable.ic_delete_vector
            )
        )

        anchor.post {
            if (simpleActivity.isFinishing || simpleActivity.isDestroyed) return@post
            if (!isSelectionModeActive()) return@post
            simpleActivity.showBubbleMenu(anchor, items) { actionId ->
                when (actionId) {
                    // The card is already selected, so "mark" needs no extra work.
                    R.id.cab_mark -> Unit
                    R.id.cab_add_to_filter -> addToFilter(simpleActivity, conversation)
                    else -> actionItemPressed(actionId)
                }
            }
        }
    }

    /** Adds this conversation's number to one of the user's filters. */
    private fun addToFilter(activity: SimpleActivity, conversation: Conversation) {
        val customFilters = activity.config.customFilters
        // The built-in ads filter is offered here too whenever it is switched on, listed
        // first, even though it is stored on its own rather than among the custom filters.
        val adsFilter = if (activity.config.showAdsFilter) {
            activity.config.adsFilter.copy(label = activity.getString(R.string.filter_ads))
        } else {
            null
        }
        val filters = listOfNotNull(adsFilter) + customFilters
        if (filters.isEmpty()) return

        val labels = filters.map { it.label }.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle(R.string.add_to_filter)
            .setItems(labels) { _, which ->
                val target = filters[which]
                val alreadyThere = target.senders.any {
                    com.texto.sms.helpers.SystemBlockedNumbers
                        .isSameSender(it, conversation.phoneNumber)
                }
                if (alreadyThere) {
                    activity.toast(R.string.already_in_filter)
                    return@setItems
                }

                val label = conversation.title.ifBlank { conversation.phoneNumber }
                val updated = target.copy(
                    senders = target.senders + conversation.phoneNumber,
                    senderLabels = target.senderLabels + label
                )
                if (updated.id == com.texto.sms.helpers.MessageFilter.ID_ADS) {
                    activity.config.adsFilter = updated
                } else {
                    activity.config.customFilters = customFilters.map {
                        if (it.id == updated.id) updated else it
                    }
                }
                activity.toast(R.string.added_to_filter)
                // Persisted senders must reach MainActivity regardless of what happens while
                // leaving selection mode, so post the refresh first and let it stand on its
                // own instead of risking finishActMode() throwing and swallowing it.
                com.texto.sms.helpers.refreshConversations()
                try { finishActMode() } catch (_: Exception) {}
            }
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .show()
    }

    private fun setupRecentView(view: View, conversation: Conversation, position: Int, holder: ViewHolder) {
        com.texto.sms.databinding.ItemConversationRecentBinding.bind(view).apply {
            val mainTextColor = activity.config.mainTextColor
            val isLead = position < 2

            recentAddress.text = conversation.title
            recentAddress.setTextColor(mainTextColor)
            recentAddress.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.1f)

            val smsDraft = drafts[conversation.threadId]
            val draftLabel = activity.getString(R.string.draft)

            recentBody.setTextColor(mainTextColor)
            recentBody.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            recentBody.text = if (!smsDraft.isNullOrEmpty()) {
                "$draftLabel: $smsDraft"
            } else {
                conversation.snippet
            }

            // Snippets come from a single query run before the list was submitted, so
            // binding a row touches no database at all and nothing can go stale.
            val latest = freshestSnippet(conversation)
            val liveSnippet = latest?.first.orEmpty()
            val type = latest?.second ?: 0
            // type 2 is SENT, 4/5/6 are the outbox states
            val isSent = type == 2 || type == 4 || type == 5 || type == 6
            recentBody.text = when {
                isSent && liveSnippet.isNotEmpty() -> "You: $liveSnippet"
                liveSnippet.isNotEmpty() -> liveSnippet
                !smsDraft.isNullOrEmpty() -> "$draftLabel: $smsDraft"
                else -> conversation.snippet
            }

            recentDate.text = (conversation.date * 1000L).formatJalaliDateOrTime()
            recentDate.setTextColor(mainTextColor)
            recentDate.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            recentDate.alpha = 0.7f

            val isUnread = !conversation.read
            val style = if (isUnread) {
                recentBody.alpha = 1f
                if (conversation.isScheduled) Typeface.BOLD_ITALIC else Typeface.BOLD
            } else {
                recentBody.alpha = 0.8f
                if (conversation.isScheduled) Typeface.ITALIC else Typeface.NORMAL
            }

            val customTypeface = (activity as SimpleActivity).getCustomTypeface()
            recentAddress.setTypeface(customTypeface, Typeface.BOLD)
            recentBody.setTypeface(customTypeface, style)
            recentDate.setTypeface(customTypeface, style)
            recentUnreadBadge.typeface = customTypeface

            recentPinIndicator.beVisibleIf(
                activity.config.pinnedConversations.contains(conversation.threadId.toString())
            )
            recentPinIndicator.applyColorFilter(mainTextColor)
            recentUnreadBadge.setTextColor(mainTextColor)
            setupBadgeCount(recentUnreadBadge, isUnread, conversation.unreadCount)

            recentImage.updateLayoutParams {
                val size = (activity as SimpleActivity)
                    .getScaledDimen(org.fossify.commons.R.dimen.list_icon_size_medium)
                width = size
                height = size
            }

            recentFrame.setupViewBackground(activity)

            // Every card carries the same colour; a selected one shifts towards the accent
            // so the selection is obvious at a glance.
            val isSelected = selectedKeys.contains(conversation.hashCode())
            val cardColor = activity.config.recentColor
            val baseColor = if (isSelected) {
                blendColors(cardColor, properPrimaryColor, 0.45f)
            } else {
                cardColor
            }

            val outlineEnabled = activity.config.useNewUi && (
                if (isLead) activity.config.bigContactsOutline
                else activity.config.smallContactsOutline
                )
            val outlineColor = if (!outlineEnabled) {
                null
            } else if (isLead) {
                activity.config.bigContactsOutlineColor
            } else {
                activity.config.smallContactsOutlineColor
            }
            val outlineThickness = if (!outlineEnabled) {
                0
            } else {
                val thickness = if (isLead) {
                    activity.config.bigContactsOutlineThickness
                } else {
                    activity.config.smallContactsOutlineThickness
                }
                (thickness * resources.displayMetrics.density).toInt()
            }

            val cardRadius =
                activity.config.cardCornerRadiusDp * resources.displayMetrics.density

            if (activity.config.glassTheme) {
                // Frosted card matching the menus and bars. Kept dense enough that the
                // message body stays legible against whatever background is behind it.
                NovaGlass.applyPanel(
                    view = recentFrame,
                    tint = baseColor,
                    cornerRadius = cardRadius,
                    opacity = if (isLead) 0.72f else 0.62f,
                    strokeWidthPx = (resources.displayMetrics.density).toInt().coerceAtLeast(1),
                    outlineColor = outlineColor,
                    outlineWidthPx = outlineThickness
                )
            } else {
                // Classic look: an opaque vertical gradient in the same single colour.
                val gd = GradientDrawable(
                    GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(
                        baseColor.adjustColor(1.2f),
                        baseColor,
                        baseColor.adjustColor(0.8f)
                    )
                ).apply { cornerRadius = cardRadius }

                if (outlineColor != null && outlineThickness > 0) {
                    gd.setStroke(outlineThickness, outlineColor)
                    recentFrame.background = LayerDrawable(arrayOf(gd))
                } else {
                    recentFrame.background = gd
                }
            }

            recentFrame.elevation = (if (isLead) 10f else 8f) * resources.displayMetrics.density
            recentFrame.translationZ = if (isLead) 6f else 4f
            recentFrame.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            recentFrame.clipToOutline = false

            // Selection / drag-target Glow
            val isHoverTarget = hoveredPosition == position
            recentSelectionGlow.beVisibleIf(isSelected || isHoverTarget)
            recentSelectionGlow.background?.applyColorFilter(properPrimaryColor)
            recentSelectionGlow.alpha = if (isHoverTarget) 0.5f else 1.0f

            recentSelectionCheck.beVisibleIf(isSelected)
            recentSelectionCheck.imageTintList = null

            recentFrame.setOnClickListener {
                if (System.currentTimeMillis() - lastDragTime < 500) return@setOnClickListener

                // viewClicked opens the thread when idle and toggles just this one card
                // while selecting. viewLongClicked would instead select the whole range
                // back to the last long-pressed row.
                holder.viewClicked(conversation)
                notifyItemChanged(position)
                updateCustomSelectionBarNow()
            }

            recentFrame.setOnLongClickListener {
                lastDragTime = System.currentTimeMillis()
                val wasSelecting = isSelectionModeActive()
                if (wasSelecting) {
                    // Already selecting: treat it as a plain toggle of this one card so
                    // the range between long-presses is never swept up.
                    holder.viewClicked(conversation)
                } else {
                    holder.viewLongClicked()
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                }
                notifyItemChanged(position)
                updateCustomSelectionBarNow()
                // Same affordance as a message bubble: any long-press that leaves exactly
                // this one card selected offers the common actions right beside it. Gating
                // on "!wasSelecting" alone meant the bubble (and "add to filter" with it)
                // only ever appeared on the very first long-press of a session -- if that
                // flow was dismissed without clearing selection (e.g. tapping outside the
                // bubble, or the "cancel" button in addToFilter()'s dialog), selection mode
                // stayed active and every subsequent long-press silently just toggled the
                // card instead of reopening the menu, making "add to filter" look flaky.
                if (isSelectionModeActive() && selectedKeys.size == 1) {
                    showConversationActions(recentFrame, conversation)
                }
                true
            }

            // Tapping the avatar toggles selection for that single card.
            recentImage.setOnClickListener {
                if (!isSelectionModeActive()) holder.viewLongClicked()
                else holder.viewClicked(conversation)
                notifyItemChanged(position)
                updateCustomSelectionBarNow()
            }

            recentImage.setOnLongClickListener {
                if (!isLead && isSelectionModeActive()) {
                    lastDragTime = System.currentTimeMillis()
                    itemTouchHelper?.startDrag(holder)
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                } else {
                    holder.viewLongClicked()
                }
                true
            }

            val placeholder = if (conversation.isGroupConversation) {
                SimpleContactsHelper(activity).getColoredGroupIcon(conversation.title)
            } else {
                null
            }

            SimpleContactsHelper(activity).loadContactImage(
                path = conversation.photoUri,
                imageView = recentImage,
                placeholderName = conversation.title,
                placeholderImage = placeholder
            )
        }
    }

    /** Mixes [ratio] of [overlay] into [base], keeping the base alpha. */
    private fun blendColors(base: Int, overlay: Int, ratio: Float): Int {
        val inverse = 1f - ratio
        return android.graphics.Color.argb(
            android.graphics.Color.alpha(base),
            (android.graphics.Color.red(base) * inverse + android.graphics.Color.red(overlay) * ratio).toInt(),
            (android.graphics.Color.green(base) * inverse + android.graphics.Color.green(overlay) * ratio).toInt(),
            (android.graphics.Color.blue(base) * inverse + android.graphics.Color.blue(overlay) * ratio).toInt()
        )
    }

    override fun getItemId(position: Int) = getItem(position).threadId

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            try {
                // Ultra-Safe Image Clearing: Just find the possible image views directly
                val recentImg = holder.itemView.findViewById<ImageView>(R.id.recent_image)
                val pillImg = holder.itemView.findViewById<ImageView>(R.id.pill_image)
                val convImg = holder.itemView.findViewById<ImageView>(R.id.conversation_image)
                
                recentImg?.let { Glide.with(activity).clear(it) }
                pillImg?.let { Glide.with(activity).clear(it) }
                convImg?.let { Glide.with(activity).clear(it) }
            } catch (_: Exception) { }
        }
    }

    private fun fetchDrafts(drafts: HashMap<Long, String>) {
        drafts.clear()
        for ((threadId, draft) in activity.getAllDrafts()) {
            drafts[threadId] = draft
        }
    }

    private fun setupView(view: View, conversation: Conversation, holder: ViewHolder) {
        ItemConversationBinding.bind(view).apply {
            root.setupViewBackground(activity)
            
            // Manually re-add listeners since we disabled default ones
            root.setOnClickListener {
                if (System.currentTimeMillis() - lastDragTime < 500) return@setOnClickListener

                // Single-item toggle while selecting, open the thread otherwise.
                holder.viewClicked(conversation)
                updateCustomSelectionBarNow()
            }

            root.setOnLongClickListener {
                lastDragTime = System.currentTimeMillis()
                if (isSelectionModeActive()) {
                    holder.viewClicked(conversation)
                } else {
                    holder.viewLongClicked()
                }
                updateCustomSelectionBarNow()
                // Classic (non-new-UI) rows previously had no way to reach "add to filter"
                // at all via long-press -- only the new-UI card layout showed this bubble.
                if (isSelectionModeActive() && selectedKeys.size == 1) {
                    showConversationActions(root, conversation)
                }
                true
            }
            root.minimumHeight = (activity as SimpleActivity).getScaledDimen(org.fossify.commons.R.dimen.two_line_list_item_min_height)
            val paddingStart = (activity as SimpleActivity).getScaledDimen(org.fossify.commons.R.dimen.activity_margin)
            val paddingTop = (activity as SimpleActivity).getScaledDimen(org.fossify.commons.R.dimen.medium_margin)
            root.setPadding(paddingStart, paddingTop, paddingStart, paddingTop)
            
            val currentMainTextColor = activity.config.mainTextColor
            val smsDraft = drafts[conversation.threadId]
            draftIndicator.apply {
                beVisibleIf(!smsDraft.isNullOrEmpty())
                setTextColor(currentMainTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.9f)
            }

            pinIndicator.beVisibleIf(
                activity.config.pinnedConversations.contains(conversation.threadId.toString())
            )
            pinIndicator.applyColorFilter(currentMainTextColor)

            conversationFrame.isSelected = selectedKeys.contains(conversation.hashCode())

            conversationAddress.apply {
                text = conversation.title
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.1f)
            }

            conversationBodyShort.apply {
                // Same pre-fetched map as the new UI: no database work while binding.
                val latest = freshestSnippet(conversation)
                val liveSnippet = latest?.first.orEmpty()
                val type = latest?.second ?: 0
                val isSent = type == 2 || type == 4 || type == 5 || type == 6
                text = if (isSent && liveSnippet.isNotEmpty()) {
                    "You: $liveSnippet"
                } else {
                    liveSnippet.ifEmpty { smsDraft ?: conversation.snippet }
                }
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            }

            conversationDate.apply {
                text = (conversation.date * 1000L).formatJalaliDateOrTime()
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
            }

            val isUnread = !conversation.read
            val style = if (isUnread) {
                conversationBodyShort.alpha = 1f
                if (conversation.isScheduled) Typeface.BOLD_ITALIC else Typeface.BOLD
            } else {
                conversationBodyShort.alpha = 0.7f
                if (conversation.isScheduled) Typeface.ITALIC else Typeface.NORMAL
            }
            val customTypeface = (activity as SimpleActivity).getCustomTypeface()
            conversationAddress.setTypeface(customTypeface, style)
            conversationBodyShort.setTypeface(customTypeface, style)
            conversationDate.setTypeface(customTypeface, style)
            unreadCountBadge.typeface = customTypeface
            draftIndicator.typeface = Typeface.create(customTypeface, Typeface.ITALIC)

            arrayListOf(conversationAddress, conversationBodyShort, conversationDate).forEach {
                it.setTextColor(currentMainTextColor)
            }
            unreadCountBadge.setTextColor(currentMainTextColor)

            setupBadgeCount(unreadCountBadge, isUnread, conversation.unreadCount)
            // at group conversations we use an icon as the placeholder, not any letter
            val placeholder = if (conversation.isGroupConversation) {
                SimpleContactsHelper(activity).getColoredGroupIcon(conversation.title)
            } else {
                null
            }

            conversationImage.updateLayoutParams {
                width = (activity as SimpleActivity).getScaledDimen(org.fossify.commons.R.dimen.list_icon_size_medium)
                height = (activity as SimpleActivity).getScaledDimen(org.fossify.commons.R.dimen.list_icon_size_medium)
            }

            SimpleContactsHelper(activity).loadContactImage(
                path = conversation.photoUri,
                imageView = conversationImage,
                placeholderName = conversation.title,
                placeholderImage = placeholder
            )
        }
    }

    private fun setupBadgeCount(view: TextView, isUnread: Boolean, count: Int) {
        view.apply {
            beVisibleIf(isUnread)
            if (isUnread) {
                text = when {
                    count > MAX_UNREAD_BADGE_COUNT -> "$MAX_UNREAD_BADGE_COUNT+"
                    count == 0 -> ""
                    else -> count.toString()
                }
                setTextColor(properPrimaryColor.getContrastColor())
                background?.applyColorFilter(properPrimaryColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.7f)
                updateLayoutParams {
                    val size = (activity as SimpleActivity).getScaledDimen(com.texto.sms.R.dimen.small_icon_size)
                    width = size
                    height = size
                }
            }
        }
    }

    override fun onChange(position: Int) = currentList.getOrNull(position)?.title ?: ""

    private fun saveRecyclerViewState() {
        if (suppressStateRestoration) return
        recyclerViewState = recyclerView.layoutManager?.onSaveInstanceState()
    }

    fun updateScaling() {
        fontSize = (activity as SimpleActivity).getScaledTextSize()
        iconSize = (activity as SimpleActivity).getScaledDimen(org.fossify.commons.R.dimen.list_icon_size_medium)
        safeNotifyDataSetChanged()
    }

    @SuppressLint("NotifyDataSetChanged")
    fun safeNotifyDataSetChanged(shouldSuppressStateRestoration: Boolean = false) {
        if (isDragging) {
            pendingNotify = true
        } else {
            suppressStateRestoration = shouldSuppressStateRestoration
            notifyDataSetChanged()
            if (shouldSuppressStateRestoration) {
                // Reset flag soon after notifyDataSetChanged starts processing
                recyclerView.post { suppressStateRestoration = false }
            }
        }
    }

    private fun restoreRecyclerViewState() {
        recyclerView.layoutManager?.onRestoreInstanceState(recyclerViewState)
    }

    private class ConversationDiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return Conversation.areItemsTheSame(oldItem, newItem)
        }

        override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return Conversation.areContentsTheSame(oldItem, newItem)
        }
    }

    companion object {
        private const val MAX_UNREAD_BADGE_COUNT = 99
        
        const val VIEW_TYPE_DEFAULT = 0
        const val VIEW_TYPE_RECENT = 1
        private const val HOVER_PAYLOAD = "hover_payload"
    }
}
