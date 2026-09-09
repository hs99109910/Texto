package com.texto.sms.adapters

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.Parcelable
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.beVisibleIf
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
import com.texto.sms.helpers.TextoAvatars
import com.texto.sms.helpers.TextoGlass
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
            return
        }
        
        // Save state immediately
        val start = initialDragPosition
        val end = hoveredPosition 
        val oldHover = hoveredPosition


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
                R.drawable.ic_ph_check
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
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun setupRecentView(view: View, conversation: Conversation, position: Int, holder: ViewHolder) {
        com.texto.sms.databinding.ItemConversationRecentBinding.bind(view).apply {
            val mainTextColor = activity.config.mainTextColor
            val isLead = position < 2

            // An unsaved sender's row is titled with the bare number; isolated so its leading
            // "+" is not laid out at the far end (see String.asLtrPhone).
            recentAddress.text = conversation.title.asLtrPhone()
            recentAddress.setTextColor(mainTextColor)
            // The design's row type ramp, relative to the preview line: 15 / 13 / 11.
            recentAddress.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 1.15f)

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

            recentDate.text = (conversation.date * 1000L).formatUiDateOrTime()
            // Secondary and tertiary text weights come straight from the design's tokens
            // (--txt2 58%, --txt3 36%) rather than from a blanket view alpha, so the
            // unread/read distinction below is free to use alpha for its own purpose.
            // .36 measured 2.04:1 against the card on the light skins -- a timestamp is
            // secondary, but not to the point of being unreadable. .52 measures 3.04:1,
            // the large-text floor, and still sits well behind the name and the preview.
            recentDate.setTextColor(mainTextColor.withAlpha(0.52f))
            recentDate.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.85f)
            recentDate.alpha = 1f
            recentBody.setTextColor(mainTextColor.withAlpha(0.58f))

            val isUnread = !conversation.read
            val style = if (isUnread) {
                recentBody.alpha = 1f
                if (conversation.isScheduled) Typeface.BOLD_ITALIC else Typeface.BOLD
            } else {
                recentBody.alpha = 0.85f
                if (conversation.isScheduled) Typeface.ITALIC else Typeface.NORMAL
            }

            val simpleActivity = activity as SimpleActivity
            recentAddress.typeface = simpleActivity.typefaceFor(Typeface.BOLD)
            recentBody.typeface = simpleActivity.typefaceFor(style)
            recentDate.typeface = simpleActivity.typefaceFor(style)
            recentUnreadBadge.typeface = simpleActivity.typefaceFor(Typeface.BOLD)

            recentPinIndicator.beVisibleIf(
                activity.config.pinnedConversations.contains(conversation.threadId.toString())
            )
            recentPinIndicator.applyColorFilter(mainTextColor.withAlpha(0.36f))
            setupBadgeCount(recentUnreadBadge, isUnread, conversation.unreadCount)

            recentImage.updateLayoutParams {
                // The design's own list avatar, and 6 x 8dp on the grid. Every avatar in the
                // list gets exactly this box whatever is drawn in it: a generated monogram,
                // a contact's photo or a company's logo all sit in the same square, at the
                // same corner radius, the same distance from the card's leading edge.
                val size = AVATAR_DP.getScaledPxIn(activity as SimpleActivity)
                width = size
                height = size
            }

            // Row density. The design's 15dp padding and 7dp gap put roughly 92dp between
            // one row and the next, where Google Messages sits near 72dp: on this screen
            // that is two whole conversations of difference. 9dp and 4dp brought the pitch
            // to about 74dp; 6dp, 3dp and a 46dp avatar bring it to about 64, another 13%,
            // and still costs no type size at all -- both text lines are untouched, and at
            // 46dp the avatar is still taller than the two of them stacked.
            //
            // Set here rather than in the layout so they follow the UI-scale setting; the
            // XML values did not, which is why the row never grew with the rest of the app.
            run {
                val a = activity as SimpleActivity
                val padH = ROW_PADDING_H_DP.getScaledPxIn(a)
                val padV = ROW_PADDING_V_DP.getScaledPxIn(a)
                recentFrame.setPadding(padH, padV, padH, padV)
                recentFrame.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                    topMargin = ROW_GAP_DP.getScaledPxIn(a)
                    bottomMargin = ROW_GAP_DP.getScaledPxIn(a)
                }

                val avatarGap = AVATAR_TEXT_GAP_DP.getScaledPxIn(a)
                val endGap = TEXT_END_GAP_DP.getScaledPxIn(a)
                recentAddress.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                    marginStart = avatarGap
                    // The date is always there, so the name always clears it.
                    marginEnd = endGap
                }
                recentBody.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                    marginStart = avatarGap
                    topMargin = TEXT_LINE_GAP_DP.getScaledPxIn(a)
                    // Only when there is something at the end of this line to clear. See
                    // TEXT_END_GAP_DP: left on unconditionally, it holds a gap against a
                    // GONE badge and leaves the preview short of the date's right edge.
                    marginEnd = if (recentUnreadBadge.isVisible || recentPinIndicator.isVisible) {
                        endGap
                    } else {
                        0
                    }
                }
                recentPinIndicator.updateLayoutParams<android.view.ViewGroup.MarginLayoutParams> {
                    marginEnd = if (recentUnreadBadge.isVisible) endGap else 0
                }
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
                // The design's row is a glass wash of the card colour behind a `--divider`
                // hairline: `border: 1px solid var(--divider)` on `background: var(--glass)`.
                // The rim is what gives a row its edge against the halo field behind it --
                // without it the cards bleed into the background on the paler themes.
                //
                // Kept deliberately faint. `--divider` is a hairline, not an outline: at .22
                // it read as a drawn border and gave every row a raised, chip-like edge the
                // design does not have. .10 still separates the card from the halo field
                // behind it without announcing itself.
                TextoGlass.applyPanel(
                    view = recentFrame,
                    tint = baseColor,
                    cornerRadius = cardRadius,
                    opacity = if (isSelected) 0.82f else 0.68f,
                    strokeWidthPx = (resources.displayMetrics.density).toInt().coerceAtLeast(1),
                    rimAlpha = 0.10f,
                    sheenAlpha = 0f,
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

            // The glass card used to cast nothing at all, because a stock shadow at a small
            // elevation is nearly opaque black and drew a second, darker edge just outside
            // the divider hairline, undoing the point of it. That is an argument against
            // *that* shadow, not against a shadow: given a tinted colour at a tenth of full
            // strength and a radius wide enough to have no visible edge of its own, the card
            // reads as softly lifted rather than as a rectangle sitting on the background.
            //
            // The shadow colour can only be set from API 28. Below that the platform gives
            // its own hard black, so those devices keep the flat card.
            val canTintShadow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            val isGlass = activity.config.glassTheme
            val cardElevation = when {
                !isGlass -> 8f
                canTintShadow -> GLASS_CARD_ELEVATION_DP
                else -> 0f
            }
            recentFrame.elevation = cardElevation * resources.displayMetrics.density
            recentFrame.translationZ = if (isGlass) 0f else 4f
            if (canTintShadow && isGlass) {
                // Cast in the theme's own darkest ground rather than black, so the shadow
                // belongs to the background it falls on.
                val shadowTint = activity.config.mainBackgroundColor
                recentFrame.outlineAmbientShadowColor = shadowTint.withAlpha(GLASS_SHADOW_ALPHA)
                recentFrame.outlineSpotShadowColor = shadowTint.withAlpha(GLASS_SHADOW_ALPHA)
            }
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

            // A plain tap never starts selection -- only a long-press does. Tapping the
            // avatar used to call viewLongClicked() and drop the list into selection mode,
            // so opening a chat by aiming slightly left of its name selected it instead.
            recentImage.setOnClickListener {
                if (System.currentTimeMillis() - lastDragTime < 500) return@setOnClickListener
                holder.viewClicked(conversation)
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

            val placeholder = TextoAvatars.letterAvatar(activity, conversation.title)
            TextoAvatars.clipToSquircle(recentImage)
            TextoAvatars.loadInto(activity, recentImage, conversation.photoUri, placeholder)
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
                text = (conversation.date * 1000L).formatUiDateOrTime()
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
            val simpleActivity = activity as SimpleActivity
            // The name stays bold regardless of read state; only the preview line reacts.
            conversationAddress.typeface = simpleActivity.typefaceFor(Typeface.BOLD)
            conversationBodyShort.typeface = simpleActivity.typefaceFor(style)
            conversationDate.typeface = simpleActivity.typefaceFor(style)
            unreadCountBadge.typeface = simpleActivity.typefaceFor(Typeface.BOLD)
            draftIndicator.typeface = simpleActivity.typefaceFor(Typeface.ITALIC)

            arrayListOf(conversationAddress, conversationBodyShort, conversationDate).forEach {
                it.setTextColor(currentMainTextColor)
            }
            unreadCountBadge.setTextColor(currentMainTextColor)

            setupBadgeCount(unreadCountBadge, isUnread, conversation.unreadCount)

            // Gradient squircle monogram instead of the library's flat circular letter icon.
            // Group threads get one too: the icon the commons helper draws is a circle and
            // would be the only round avatar left in the list.
            val placeholder = TextoAvatars.letterAvatar(activity, conversation.title)

            conversationImage.updateLayoutParams {
                // The design's list avatar is 48dp, not the commons list-icon size.
                val side = 48.getScaledPxIn(activity as SimpleActivity)
                width = side
                height = side
            }
            // Real contact photos get clipped to the same silhouette as the generated ones.
            TextoAvatars.clipToSquircle(conversationImage)

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
                val config = activity.config
                setTextColor(config.accentInkColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.85f)
                // `background: var(--grad)` -- the accent gradient, the same surface the
                // avatars and the active filter chip carry, not a flat stop off it. The
                // badge is a 20dp pill that grows wider for a two-digit count.
                //
                // Height, ink padding and corner are fixed, so every badge in the list is
                // the same object at the same place: the only thing a row decides is how
                // many digits go in it, and the pill is a circle until there are two.
                val size = BADGE_SIZE_DP.getScaledPxIn(activity as SimpleActivity)
                val pad = BADGE_PADDING_H_DP.getScaledPxIn(activity as SimpleActivity)
                updateLayoutParams {
                    width = ViewGroup.LayoutParams.WRAP_CONTENT
                    height = size
                }
                minWidth = size
                setPadding(pad, 0, pad, 0)
                background = TextoGlass.accent(
                    start = config.accentGradientStart,
                    end = config.accentGradientEnd,
                    cornerRadius = size / 2f,
                    mid = config.accentGradientMid
                )
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

        /**
         * The conversation row's geometry, in dp before the UI-scale setting is applied.
         *
         * One 8dp grid, with 4dp allowed as its half step where a full one would cost
         * height. Everything a row measures comes from here rather than from the layout,
         * because a dp in XML is fixed and these follow the UI-scale setting; the margins
         * used to sit in the XML at 13/8/3 and so were the one part of the row that never
         * grew with the rest of the app.
         *
         * The row is taller than its avatar: the two text lines stack to about 60dp against
         * the avatar's 48, so the pitch is 2x padding + text + 2x gap and the avatar rides
         * centred inside it. That is why the vertical values are the half step -- at a full
         * 8dp of padding and 8dp of gap every row grows by 8dp, which is more screen than
         * the shorter header gives back.
         */
        private const val AVATAR_DP = 48
        private const val ROW_PADDING_H_DP = 16
        private const val ROW_PADDING_V_DP = 4
        private const val ROW_GAP_DP = 4

        /** Avatar to the name and the preview, and the name to the preview below it. */
        private const val AVATAR_TEXT_GAP_DP = 12
        private const val TEXT_LINE_GAP_DP = 4

        /**
         * What the name and the preview keep clear of whatever sits at the end of their
         * line -- the date above, the badge below.
         *
         * The preview's is applied per row rather than in the layout because the badge and
         * the pin it is constrained to are usually GONE, and a gone widget keeps the margin
         * of the view pointing at it. Every unbadged row therefore held an 8dp gap against
         * nothing, and its preview stopped 8dp short of the right edge the date above it
         * ended on: measured 964px against the date's 985px at 420dpi. Applied only when
         * there is something there to clear, the two edges line up on every row.
         */
        private const val TEXT_END_GAP_DP = 8

        /**
         * The unread badge's slot: a fixed 20dp tall pill with 6dp of ink padding, anchored
         * to the card's own end padding. Size and position come from the count alone -- a
         * long preview cannot push it, because the preview is constrained to it rather than
         * the other way round.
         */
        private const val BADGE_SIZE_DP = 20
        private const val BADGE_PADDING_H_DP = 6

        /**
         * How far the glass card is lifted. Android derives the shadow's blur from this, so
         * it is deliberately large: a wide, faint shadow has no edge of its own, where a
         * tight one draws a second line just outside the card's hairline.
         */
        private const val GLASS_CARD_ELEVATION_DP = 16f

        /** A tenth of full strength. Any denser and the card stops floating and starts sitting. */
        private const val GLASS_SHADOW_ALPHA = 0.10f
        
        const val VIEW_TYPE_DEFAULT = 0
        const val VIEW_TYPE_RECENT = 1
        private const val HOVER_PAYLOAD = "hover_payload"
    }
}

