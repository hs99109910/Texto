package com.texto.sms.adapters

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy
import com.bumptech.glide.load.resource.bitmap.FitCenter
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.RequestOptions
import com.bumptech.glide.request.target.Target

import com.texto.sms.views.TextoRecyclerView
import com.texto.sms.R
import com.texto.sms.activities.NewConversationActivity
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.activities.ThreadActivity
import com.texto.sms.activities.VCardViewerActivity
import com.texto.sms.databinding.*
import com.texto.sms.extensions.*
import com.texto.sms.helpers.*
import com.texto.sms.models.Attachment
import com.texto.sms.models.Message
import com.texto.sms.models.ThreadItem
import com.texto.sms.models.ThreadItem.ThreadDateTime
import com.texto.sms.models.ThreadItem.ThreadError
import com.texto.sms.models.ThreadItem.ThreadSending
import com.texto.sms.models.ThreadItem.ThreadSent
import com.texto.sms.extensions.copyToClipboard
import com.texto.sms.extensions.darkenColor
import com.texto.sms.extensions.getContrastColor
import com.texto.sms.extensions.hideKeyboard
import com.texto.sms.extensions.notificationManager
import com.texto.sms.extensions.shareTextIntent
import com.texto.sms.extensions.showKeyboard
import com.texto.sms.extensions.usableScreenSize
import com.texto.sms.extensions.applyColorFilter
import com.texto.sms.extensions.beGone
import com.texto.sms.extensions.beVisible
import com.texto.sms.extensions.beGoneIf
import com.texto.sms.extensions.beVisibleIf
import com.texto.sms.extensions.toast
import com.texto.sms.extensions.showErrorToast
import com.texto.sms.extensions.viewBinding

class ThreadAdapter(
    activity: SimpleActivity,
    recyclerView: TextoRecyclerView,
    itemClick: (Any) -> Unit,
    private val isRecycleBin: Boolean,
    private val deleteMessages: (messages: List<Message>, toRecycleBin: Boolean, fromRecycleBin: Boolean) -> Unit
) : BaseTextoRecyclerViewListAdapter<ThreadItem>(activity, recyclerView, ThreadItemDiffCallback(), itemClick) {

    /**
     * The activity's tap handler, kept as a property because [itemClick] is a plain
     * constructor parameter and member functions cannot reach one.
     */
    private val onItemTap: (Any) -> Unit = itemClick

    private val hasMultipleSIMCards = try {
        activity.subscriptionManagerCompat().activeSubscriptionInfoList?.size ?: 0 > 1
    } catch (_: SecurityException) {
        false
    }
    
    private val uiScale get() = (activity as SimpleActivity).uiScale
    private val maxChatBubbleWidth = (activity.usableScreenSize.x * 0.75f).toInt()
    private var fontSize = (activity as SimpleActivity).getScaledTextSize()
    /**
     * Messages whose entry animation has already played, by id.
     *
     * This was a single "highest position animated so far", and a position is the wrong
     * identity for it: a row is rebound whenever anything about it changes -- and
     * long-pressing one changes its selection state -- so the bubble replayed a 0.7-to-1
     * overshoot scale at the exact moment the menu opened over it. The message being acted
     * on appeared to flinch, which is the one thing it must not do while you are aiming at
     * a menu beside it. Keyed on the message instead, a bubble animates once, ever.
     */
    private val animatedMessageIds = HashSet<Long>()

    /**
     * The in-thread search term, highlighted inside each bubble so a long message says which
     * part of it matched. Empty when no search is running.
     */
    private var searchTerm = ""

    /** The last bubble tapped and when, so the next tap can tell a double-tap from a single. */
    private var lastTapMessageId = -1L
    private var lastTapAt = 0L

    /**
     * The filter this thread's sender belongs to, when that filter carries colours of its own.
     * Null is every other thread, and every thread before this existed: the four accessors
     * below fall through to the app's own settings, which is exactly what they read before.
     *
     * Set by [ThreadActivity] once the participants are known, so a thread opened from a
     * notification -- where the participants land a moment after the list does -- repaints
     * rather than staying on the app's colours until it is reopened.
     */
    var filterOverride: MessageFilter? = null

    /**
     * Set while the appearance editor is open: a tap or a long-press on a bubble then names
     * the element being restyled instead of acting on the message. Selecting, reacting and
     * the context menu are all off for the duration -- in edit mode a bubble is a swatch, and
     * leaving both meanings live on one gesture would make every tap a guess.
     */
    var onEditAppearanceElement: ((anchor: View, isReceived: Boolean) -> Unit)? = null
        set(value) {
            if (field == value) return
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    private val sentBubbleColor
        get() = filterOverride?.sentBubbleColor ?: activity.config.sentBubbleColor
    private val sentBubbleTextColor
        get() = filterOverride?.sentBubbleTextColor ?: activity.config.sentBubbleTextColor
    private val receivedBubbleColor
        get() = filterOverride?.receivedBubbleColor ?: activity.config.receivedBubbleColor
    private val receivedBubbleTextColor
        get() = filterOverride?.receivedBubbleTextColor ?: activity.config.receivedBubbleTextColor
    private val threadBackgroundColor
        get() = filterOverride?.backgroundColor ?: activity.config.mainBackgroundColor

    /**
     * True while the received side wears the accent gradient rather than a flat colour. A
     * filter picking its own received colour has to switch it off for the same reason the
     * settings picker does: a gradient painted over the chosen colour ignores the choice.
     */
    private val receivedIsFlat
        get() = filterOverride?.receivedBubbleColor != null || activity.config.receivedBubbleColorSet

    fun setSearchTerm(term: String) {
        if (term == searchTerm) return
        searchTerm = term
        notifyItemRangeChanged(0, itemCount)
    }

    fun updateScaling() {
        fontSize = (activity as SimpleActivity).getScaledTextSize()
        notifyItemRangeChanged(0, itemCount)
    }

    companion object {
        /**
         * The six Messages and iMessage both offer, in their order. Sending one puts
         * "<emoji> to '<the first words>'" on the wire, which is the wording both apps
         * parse back into a reaction -- there is no capability bit in plain SMS to ask
         * with, so this text *is* the interoperability. A recipient whose app does not
         * know the form sees that short line instead of a silent no-op.
         */
        // 👎 gave up its slot to 😘: the thumbs-down is the one of the six almost nobody
        // sends, and 😘 is. The trade is that a 😘 reaching Messages or iMessage arrives as
        // the plain line "😘 to '…'" rather than as a tapback -- the other five still map.
        val REACTION_EMOJI = listOf("❤️", "👍", "😘", "😂", "😮", "😢")

        private const val MAX_MEDIA_HEIGHT_RATIO = 2

        /** Space above a bubble that starts a run, and above one that continues it. */
        private const val MESSAGE_GAP_DP = 8
        private const val RUN_GAP_DP = 2
        private const val BUBBLE_RADIUS_DP = 17
        private const val BUBBLE_JOIN_RADIUS_DP = 6
        private const val BUBBLE_TAIL_W_DP = 7
        private const val BUBBLE_TAIL_H_DP = 14
        private const val BUBBLE_PAD_H_DP = 11
        private const val BUBBLE_PAD_TOP_DP = 7
        private const val BUBBLE_PAD_BOTTOM_DP = 6

        /** Messages further apart than this start a new run even from the same sender. */
        private const val RUN_WINDOW_SECONDS = 5 * 60

        /** Rebinds a bubble in place when its neighbours, and so its corners, change. */
        private const val RUN_PAYLOAD = "run_payload"
        private const val SIM_BITS = 10
        private const val SIM_MASK = (1L shl SIM_BITS) - 1
    }

    init {
        setupDragListener(true)
        registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onChanged() {
                updateCustomSelectionBar()
            }
            override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                updateCustomSelectionBar()
            }
            override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
                updateCustomSelectionBar()
            }
        })
    }

    override fun getActionMenuId() = R.menu.cab_thread

    fun isSelectionModeActive() = selectedKeys.isNotEmpty()

    private fun getCustomActions(): List<Int> {
        val selectedItems = getSelectedItems()
        if (selectedItems.isEmpty()) return emptyList()

        val isOneItemSelected = selectedItems.size == 1
        val selectedMessage = selectedItems.first() as? Message
        val isMms = selectedMessage?.isMMS == true
        
        val actions = mutableListOf<Int>()
        actions.add(R.id.cab_select_all)
        actions.add(R.id.cab_delete)
        
        if (isOneItemSelected && !isMms) {
            actions.add(R.id.cab_copy_to_clipboard)
            actions.add(R.id.cab_share)
            actions.add(R.id.cab_select_text)
            actions.add(R.id.cab_forward_message)
        }

        if (isOneItemSelected && isMms && selectedMessage?.attachment?.attachments?.isNotEmpty() == true) {
            actions.add(R.id.cab_save_as)
        }
        
        return actions
    }

    override fun onActionModeCreated() {
        updateCustomSelectionBar()
    }

    @android.annotation.SuppressLint("NotifyDataSetChanged")
    override fun onActionModeDestroyed() {
        (activity as? SimpleActivity)?.toggleCustomSelectionBar(false)
        // Same as the conversation list: clearing the selection rebinds nothing, so the
        // badges stayed on screen until the thread was reopened.
        notifyDataSetChanged()
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

    override fun prepareActionMode(menu: Menu) {
        try {
            val selectedItems = getSelectedItems()
            if (selectedItems.isEmpty()) {
                return
            }

            val isOneItemSelected = selectedItems.size == 1
            val selectedMessage = selectedItems.first() as? Message
            val isMms = selectedMessage?.isMMS == true

            // ROBUST NULL SAFETY
            menu.findItem(R.id.cab_copy_to_clipboard)?.isVisible = isOneItemSelected && !isMms
            menu.findItem(R.id.cab_save_as)?.isVisible = isOneItemSelected && isMms && selectedMessage?.attachment?.attachments?.isNotEmpty() == true
            menu.findItem(R.id.cab_share)?.isVisible = isOneItemSelected && !isMms
            menu.findItem(R.id.cab_select_text)?.isVisible = isOneItemSelected && !isMms
            menu.findItem(R.id.cab_properties)?.isVisible = isOneItemSelected && selectedMessage != null
            menu.findItem(R.id.cab_forward_message)?.isVisible = isOneItemSelected && selectedMessage != null
            menu.findItem(R.id.cab_restore)?.isVisible = isRecycleBin
        } catch (e: Exception) {
            android.util.Log.e("SelectionCrash", "Error preparing action mode", e)
        }
    }

    /**
     * Quick actions anchored to the bubble the user just long-pressed. The selection bar
     * at the top stays up, so dismissing this popup still leaves multi-select available.
     */
    /** Call, copy or forward just the number that was tapped, not the whole message. */
    /**
     * Hands a link in a message to whatever the phone uses for the web. Wrapped because an
     * SMS can carry a malformed address and a device can genuinely have no browser -- neither
     * should take the thread down.
     */
    private fun openMessageLink(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
        } catch (_: Exception) {
            activity.toast(R.string.no_browser_found)
        }
    }

    private fun showNumberActions(anchor: View, number: String) {
        val simpleActivity = activity as? SimpleActivity ?: return
        if (simpleActivity.isFinishing || simpleActivity.isDestroyed) return

        // Icons alone: the actions are unmistakable and a label would only crowd a
        // menu that pops up right next to the figure being acted on.
        val items = arrayListOf(
            SimpleActivity.BubbleAction(
                R.id.dial_number,
                "",
                R.drawable.ic_ph_phone
            ),
            SimpleActivity.BubbleAction(R.id.copy_number, "", R.drawable.ic_copy_vector),
            SimpleActivity.BubbleAction(
                R.id.cab_forward_message,
                "",
                R.drawable.ic_forward_vector
            )
        )

        simpleActivity.showBubbleMenu(anchor, items, header = number) { actionId ->
            when (actionId) {
                // The same helper the thread's own call icon uses, so a number tapped in a
                // message behaves exactly like calling the person the thread belongs to: it
                // places the call directly when CALL_PHONE has been granted and falls back
                // to handing the number to the dialer when it has not.
                R.id.dial_number -> simpleActivity.dialNumber(number)

                R.id.copy_number -> simpleActivity.copyToClipboard(number)
                R.id.cab_forward_message -> {
                    Intent(simpleActivity, NewConversationActivity::class.java).apply {
                        putExtra(THREAD_TEXT, number)
                        simpleActivity.startActivity(this)
                    }
                }
            }
        }
    }

    private fun showBubbleActions(anchor: View, message: Message) {
        val simpleActivity = activity as? SimpleActivity ?: return
        if (simpleActivity.isFinishing || simpleActivity.isDestroyed) return

        val items = arrayListOf<SimpleActivity.BubbleAction>()
        // The message is already selected by the long-press that opened this menu, so
        // "mark" simply closes it and leaves the user in multi-select.
        items.add(
            SimpleActivity.BubbleAction(
                R.id.cab_mark,
                simpleActivity.getString(R.string.mark_select),
                R.drawable.ic_ph_check
            )
        )
        if (!message.isMMS) {
            items.add(
                SimpleActivity.BubbleAction(
                    R.id.cab_copy_to_clipboard,
                    simpleActivity.getString(R.string.copy_to_clipboard),
                    R.drawable.ic_copy_vector
                )
            )
        }
        items.add(
            SimpleActivity.BubbleAction(
                R.id.cab_forward_message,
                simpleActivity.getString(R.string.forward_message),
                R.drawable.ic_forward_vector
            )
        )
        if (message.attachment?.attachments?.isNotEmpty() == true) {
            items.add(
                SimpleActivity.BubbleAction(
                    R.id.cab_save_as,
                    simpleActivity.getString(R.string.save_as),
                    R.drawable.ic_download_vector
                )
            )
        }
        items.add(
            SimpleActivity.BubbleAction(
                R.id.cab_delete,
                simpleActivity.getString(R.string.delete),
                R.drawable.ic_delete_vector
            )
        )

        anchor.post {
            if (simpleActivity.isFinishing || simpleActivity.isDestroyed) return@post
            if (!isSelectionModeActive()) return@post
            // A reaction is not a recycle-bin action, and a scheduled message has not been
            // sent yet, so neither offers the strip.
            val canReact = !isRecycleBin && !message.isScheduled &&
                activity is ThreadActivity
            simpleActivity.showBubbleMenu(
                anchor = anchor,
                items = items,
                reactions = if (canReact) REACTION_EMOJI else emptyList(),
                activeReaction = message.reaction,
                onReaction = { emoji ->
                    finishActMode()
                    (activity as ThreadActivity).onReactionPicked(message, emoji)
                },
            ) { actionId ->
                actionItemPressed(actionId)
            }
        }
    }

    /**
     * The double-tap reaction picker: all six of [REACTION_EMOJI] beside the bubble, one tap
     * to send. The same strip the long-press menu carries, on its own without the action
     * rows -- a reaction goes out as a real SMS, so picking which one is worth the extra tap
     * rather than committing to a heart the moment two taps land.
     *
     * Silently does nothing where the long-press strip would not have offered reactions
     * either: the recycle bin, or a message not yet sent.
     */
    private fun quickReact(anchor: View, message: Message) {
        if (isSelectionModeActive() || isRecycleBin || message.isScheduled) return
        val thread = activity as? ThreadActivity ?: return
        val simpleActivity = activity as? SimpleActivity ?: return
        simpleActivity.showBubbleMenu(
            anchor = anchor,
            items = emptyList(),
            reactions = REACTION_EMOJI,
            activeReaction = message.reaction,
            onReaction = { emoji -> thread.onReactionPicked(message, emoji) }
        ) { }
    }

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            // Selection already happened; nothing more to do than stay in select mode.
            R.id.cab_mark -> updateCustomSelectionBar()
            R.id.cab_copy_to_clipboard -> copyToClipboard()
            R.id.cab_save_as -> saveAs()
            R.id.cab_share -> shareText()
            R.id.cab_select_text -> selectText()
            R.id.cab_properties -> showProperties()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_restore -> askConfirmRestore()
            R.id.cab_forward_message -> forwardMessage()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = currentList.filterIsInstance<Message>().size

    override fun getIsItemSelectable(position: Int) = currentList[position] is Message

    override fun getItemSelectionKey(position: Int): Int? {
        return (currentList.getOrNull(position) as? Message)?.getSelectionKey()
    }

    override fun getItemKeyPosition(key: Int): Int {
        return currentList.indexOfFirst { (it as? Message)?.getSelectionKey() == key }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = when (viewType) {
            THREAD_RECEIVED_MESSAGE -> ItemMessageReceivedBinding.inflate(layoutInflater, parent, false)
            THREAD_SENT_MESSAGE -> ItemMessageSentBinding.inflate(layoutInflater, parent, false)
            THREAD_DATE_TIME -> ItemThreadDateTimeBinding.inflate(layoutInflater, parent, false)
            THREAD_SENT_MESSAGE_ERROR -> ItemThreadErrorBinding.inflate(layoutInflater, parent, false)
            THREAD_SENT_MESSAGE_SENDING -> ItemThreadSendingBinding.inflate(layoutInflater, parent, false)
            THREAD_SENT_MESSAGE_SENT -> ItemThreadSuccessBinding.inflate(layoutInflater, parent, false)
            else -> ItemThreadSuccessBinding.inflate(layoutInflater, parent, false)
        }
        return ThreadViewHolder(binding)
    }

    /**
     * Whether [message] and the item at [neighbourPosition] belong to one run: both messages,
     * from the same side and -- for incoming ones -- the same sender, within a few minutes of
     * each other. A reaction badge hangs below its bubble, so a run is broken under one rather
     * than letting the next bubble sit on top of the badge.
     */
    private fun continuesRun(message: Message, neighbourPosition: Int, neighbourIsNext: Boolean): Boolean {
        val other = currentList.getOrNull(neighbourPosition) as? Message ?: return false
        if (other.isReceivedMessage() != message.isReceivedMessage()) return false
        if (message.isReceivedMessage() && other.senderPhoneNumber != message.senderPhoneNumber) {
            return false
        }
        val upper = if (neighbourIsNext) message else other
        if (!upper.reaction.isNullOrEmpty()) return false
        return kotlin.math.abs(other.date - message.date) <= RUN_WINDOW_SECONDS
    }

    /**
     * A bubble's corners depend on its neighbours, and DiffUtil only rebinds the rows whose own
     * content changed. So when a message arrives, the one above it -- unchanged itself -- would
     * keep the rounded bottom it had as the last of its run. Every message whose neighbours are
     * not the ones it had before is rebound here, with a payload so it is redrawn in place.
     */
    override fun onCurrentListChanged(
        previousList: MutableList<ThreadItem>,
        currentList: MutableList<ThreadItem>
    ) {
        super.onCurrentListChanged(previousList, currentList)
        if (previousList.isEmpty()) return
        val oldIds = previousList.map { getItemIdForRawItem(it) }
        val oldIndex = HashMap<Long, Int>(oldIds.size)
        oldIds.forEachIndexed { index, id -> oldIndex[id] = index }
        val stale = ArrayList<Int>()
        currentList.forEachIndexed { index, item ->
            if (item !is Message) return@forEachIndexed
            val was = oldIndex[getItemIdForRawItem(item)] ?: return@forEachIndexed
            val before = currentList.getOrNull(index - 1)?.let { getItemIdForRawItem(it) }
            val after = currentList.getOrNull(index + 1)?.let { getItemIdForRawItem(it) }
            if (before != oldIds.getOrNull(was - 1) || after != oldIds.getOrNull(was + 1)) {
                stale.add(index)
            }
        }
        if (stale.isEmpty()) return
        recyclerView.post {
            stale.forEach { if (it < itemCount) notifyItemChanged(it, RUN_PAYLOAD) }
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = currentList[position]
        val binding = (holder as ThreadViewHolder).binding
        when (item) {
            is Message -> {
                // Use bindView for robust interaction handling (tap/long-press synchronization)
                holder.bindView(item, allowSingleClick = true, allowLongClick = true) { _, _ ->
                    // Root click is handled by bodyHolder/bodyView logic for reactions
                }
                
                setupView(holder, binding, item)
                
                // Bubble Entry Animation
                if (animatedMessageIds.add(item.id)) {
                    val isReceived = item.isReceivedMessage()
                    val bodyHolder = if (binding is ItemMessageReceivedBinding) binding.threadMessageBodyHolder else (binding as ItemMessageSentBinding).threadMessageBodyHolder
                    
                    val anim = android.view.animation.ScaleAnimation(
                        0.7f, 1.0f, 0.7f, 1.0f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, if (isReceived) 0f else 1.0f,
                        android.view.animation.Animation.RELATIVE_TO_SELF, 1.0f
                    )
                    anim.duration = 400
                    anim.interpolator = android.view.animation.OvershootInterpolator(1.2f)
                    bodyHolder.startAnimation(anim)
                }
            }
            is ThreadDateTime -> setupDateTime(binding.root, item)
            is ThreadError -> setupThreadError(binding.root, item)
            is ThreadSending -> setupThreadSending(binding.root)
            is ThreadSent -> setupThreadSuccess(binding.root, item.delivered)
        }
        bindViewHolder(holder)
    }

    override fun getItemId(position: Int): Long {
        val item = currentList.getOrNull(position) ?: return 0L
        return getItemIdForRawItem(item)
    }

    override fun getItemViewType(position: Int): Int {
        return when (val item = currentList[position]) {
            is Message -> if (item.isReceivedMessage()) THREAD_RECEIVED_MESSAGE else THREAD_SENT_MESSAGE
            is ThreadDateTime -> THREAD_DATE_TIME
            is ThreadError -> THREAD_SENT_MESSAGE_ERROR
            is ThreadSending -> THREAD_SENT_MESSAGE_SENDING
            is ThreadSent -> THREAD_SENT_MESSAGE_SENT
        }
    }

    private fun copyToClipboard() {
        val message = getSelectedItems().first() as Message
        activity.copyToClipboard(message.body)
        finishActMode()
    }

    private fun getSelectedAttachments(): List<Attachment> {
        val message = getSelectedItems().first() as Message
        return message.attachment?.attachments ?: emptyList()
    }

    private fun saveAs() {
        val attachments = getSelectedAttachments()
        (activity as ThreadActivity).saveMMS(attachments)
        finishActMode()
    }

    private fun shareText() {
        val message = getSelectedItems().first() as Message
        activity.shareTextIntent(message.body)
        finishActMode()
    }

    /**
     * The body on its own sheet, selectable. It used to inflate `dialog_select_text` and then
     * throw it away: the dialog it handed to commons was built from the body as a *title*,
     * so the inflated view never reached the screen. The app's own sheet already renders its
     * message selectable, which is the whole feature.
     */
    private fun selectText() {
        val message = getSelectedItems().first() as Message
        (activity as SimpleActivity).textoConfirmDialog(
            message = message.body,
            negativeLabel = null,
            cancelOnTouchOutside = false
        ) {
            // Nothing to do
        }
    }

    private fun showProperties() {
        val message = getSelectedItems().first() as Message
        (activity as ThreadActivity).showProperties(message)
        finishActMode()
    }

    /**
     * The count alone is not the noun. Both confirmations name what is being acted on
     * through [R.plurals.delete_messages] first, the way every other adapter here does:
     * dropped, `deletion_confirmation`'s `%s` took the bare number and the dialog read
     * "Are you sure you want to delete 1?" -- measured on device, in both languages.
     */
    private fun messageCountPhrase(count: Int): String =
        resources.getQuantityString(R.plurals.delete_messages, count, count)

    private fun askConfirmDelete() {
        val items = getSelectedItems().filterIsInstance<Message>()
        val baseString = R.string.deletion_confirmation
        val question = String.format(resources.getString(baseString), messageCountPhrase(items.size))
        (activity as SimpleActivity).textoConfirmDialog(question, isDestructive = true) {
            deleteMessages(items, false, false)
            finishActMode()
        }
    }

    private fun askConfirmRestore() {
        val items = getSelectedItems().filterIsInstance<Message>()
        // `files_restored_successfully` is the message shown *after* a restore, and it
        // carries no placeholder at all, so asking with it announced a success that had
        // not happened yet and silently swallowed the count.
        val baseString = R.string.restore_confirmation
        val question = String.format(resources.getString(baseString), messageCountPhrase(items.size))

        (activity as SimpleActivity).textoConfirmDialog(question) {
            deleteMessages(items, false, true)
            finishActMode()
        }
    }

    private fun forwardMessage() {
        val message = getSelectedItems().first() as Message
        val intent = Intent(activity, NewConversationActivity::class.java).apply {
            putExtra(THREAD_TEXT, message.body)
            if (message.isMMS) {
                // ThreadActivity reads these back with getParcelableArrayListExtra<Uri>,
                // so they have to go in as Uri parcelables, not strings.
                val uris = ArrayList<android.net.Uri>(
                    message.attachment?.attachments?.map { it.getUri() } ?: emptyList()
                )
                if (uris.isNotEmpty()) {
                    putParcelableArrayListExtra(THREAD_ATTACHMENT_URIS, uris)
                }
            }
        }
        activity.startActivity(intent)
        finishActMode()
    }

    private fun getSelectedItems(): ArrayList<ThreadItem> {
        val items = ArrayList<ThreadItem>()
        selectedKeys.forEach { key ->
            val item = currentList.firstOrNull { (it as? Message)?.getSelectionKey() == key }
            if (item != null) {
                items.add(item)
            }
        }
        return items
    }

    fun updateMessages(newMessages: ArrayList<ThreadItem>, callback: (() -> Unit)? = null) {
        submitList(newMessages) {
            callback?.invoke()
        }
    }

    /**
     * Paints every occurrence of the search term with the accent behind it.
     *
     * Matching folds both sides through [foldPersian] the same way the filter does, and the
     * fold is length:preserving per character, so an index found in the folded string points
     * at the same character in the original. Spans go on the TextView's existing text, so
     * the number and link spans already installed survive.
     */
    private fun highlightSearchTerm(view: TextView, body: String, ink: Int) {
        if (searchTerm.isEmpty() || body.isEmpty()) return
        val haystack = body.foldPersian().lowercase()
        val needle = searchTerm.foldPersian().lowercase()
        if (needle.isEmpty() || haystack.length != body.length) return

        val spannable = android.text.SpannableString(view.text)
        var from = haystack.indexOf(needle)
        var found = false
        while (from >= 0) {
            val to = (from + needle.length).coerceAtMost(spannable.length)
            spannable.setSpan(
                android.text.style.BackgroundColorSpan(
                    activity.config.accentGradientStart.withAlpha(0.35f)
                ),
                from, to, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannable.setSpan(
                android.text.style.StyleSpan(Typeface.BOLD),
                from, to, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            found = true
            from = haystack.indexOf(needle, from + needle.length)
        }
        if (found) view.text = spannable
    }

    private fun setupView(holder: ViewHolder, binding: ViewBinding, message: Message) {
        val isSelected = selectedKeys.contains(message.getSelectionKey())
        val isReceived = message.isReceivedMessage()
        
        // Which side wears the theme's accent gradient. It was the outgoing side; it is
        // the incoming one now, so an arriving message is what the eye lands on and the
        // thread reads the way Messages and iMessage do.
        //
        // The picker wins over it: choose a colour for the received bubble in settings and
        // that side goes flat, which is what makes both bubble colours editable rather
        // than one of them being quietly ignored by the gradient.
        val hasAccent = isReceived && !receivedIsFlat

        val wrapper = if (binding is ItemMessageReceivedBinding) binding.threadMessageWrapper else (binding as ItemMessageSentBinding).threadMessageWrapper
        val holderView = if (binding is ItemMessageReceivedBinding) binding.threadMessageHolder else (binding as ItemMessageSentBinding).threadMessageHolder
        val bodyView = if (binding is ItemMessageReceivedBinding) binding.threadMessageBody else (binding as ItemMessageSentBinding).threadMessageBody
        val bodyHolder = if (binding is ItemMessageReceivedBinding) binding.threadMessageBodyHolder else (binding as ItemMessageSentBinding).threadMessageBodyHolder
        val photoView = if (binding is ItemMessageReceivedBinding) binding.threadMessageSenderPhoto else (binding as ItemMessageSentBinding).threadMessageSenderPhoto
        val attachmentsHolder = if (binding is ItemMessageReceivedBinding) binding.threadMessageAttachmentsHolder else (binding as ItemMessageSentBinding).threadMessageAttachmentsHolder
        val playOutline = if (binding is ItemMessageReceivedBinding) binding.threadMessagePlayOutline else (binding as ItemMessageSentBinding).threadMessagePlayOutline
        val overlay = if (binding is ItemMessageReceivedBinding) binding.selectionOverlay else (binding as ItemMessageSentBinding).selectionOverlay
        val selectionCheck = if (binding is ItemMessageReceivedBinding) binding.selectionCheck else (binding as ItemMessageSentBinding).selectionCheck
        val reactionView = if (binding is ItemMessageReceivedBinding) binding.messageReaction else (binding as ItemMessageSentBinding).messageReaction

        holderView.visibility = View.VISIBLE
        wrapper.visibility = View.VISIBLE

        // Consecutive messages from the same side read as one run: they sit closer together,
        // and the corners they share on the screen-edge side flatten (see the radii below).
        val adapterPosition = holder.bindingAdapterPosition
        val joinsPrevious = continuesRun(message, adapterPosition - 1, neighbourIsNext = false)
        val joinsNext = continuesRun(message, adapterPosition + 1, neighbourIsNext = true)
        holderView.updateLayoutParams<ViewGroup.MarginLayoutParams> {
            topMargin = (if (joinsPrevious) RUN_GAP_DP else MESSAGE_GAP_DP)
                .getScaledPxIn(activity as SimpleActivity)
        }

        // The design carries each message's own clock time inside its bubble. The row between
        // bubbles is now only a date separator, so this is the only place a time is shown.
        val timeView = if (binding is ItemMessageReceivedBinding) {
            binding.threadMessageTime
        } else {
            (binding as ItemMessageSentBinding).threadMessageTime
        }
        timeView.apply {
            text = (message.date * 1000L).formatUiTimeOnly()
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.68f)
            // `--meta-on-primary` over the sent gradient, `--muted` on a received bubble.
            val ink = if (isReceived) receivedBubbleTextColor else sentBubbleTextColor
            setTextColor(ink.withAlpha(if (isReceived) 0.6f else 0.7f))
            typeface = Typeface.create((activity as SimpleActivity).getCustomTypeface(), Typeface.NORMAL)
        }

        // One tick once sent, two once the carrier reports delivery. Only meaningful on
        // outgoing messages, and only when delivery reports are switched on.
        if (binding is ItemMessageSentBinding) {
            val isDelivered = message.status == android.provider.Telephony.Sms.STATUS_COMPLETE
            val isSent = message.type == android.provider.Telephony.Sms.MESSAGE_TYPE_SENT
            binding.deliveryStatus.apply {
                beVisibleIf(!isReceived && isSent)
                // The design's own delivery glyphs: `check-check` once delivered, `check`
                // once sent. Same pair as the rest of the skin's icon set, rather than the
                // two one-off tick drawables this used to carry.
                setImageResource(
                    if (isDelivered) R.drawable.ic_ph_checks else R.drawable.ic_ph_check
                )
                // A delivered pair is wider than a single tick, so the view has to grow
                // with it or the second tick gets clipped.
                val density = resources.displayMetrics.density
                layoutParams = layoutParams.apply {
                    width = ((if (isDelivered) 20 else 16) * density).toInt()
                    height = (16 * density).toInt()
                }
                imageTintList = android.content.res.ColorStateList.valueOf(
                    sentBubbleTextColor
                )
            }
        }

        // The reaction badge, tucked under the bubble's inner corner. Both Messages and
        // iMessage carry the emoji on the message it belongs to rather than as a line of
        // its own, and processReactions already folds an incoming "<emoji> to '...'" text
        // onto its target and hides the carrier message, so the two sides agree.
        val reaction = message.reaction
        if (reaction.isNullOrEmpty()) {
            reactionView.beGone()
        } else {
            reactionView.beVisible()
            reactionView.text = reaction
            // Opaque: a colour emoji is drawn at its paint's alpha, and the badge carries no
            // ink of its own, so it would otherwise inherit the theme's translucent default
            // and wash into the pill behind it.
            reactionView.setTextColor(activity.config.mainTextColor.withAlpha(1f))
            reactionView.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.85f)
            // The badge sits half off the bubble, so it needs a ground of its own to stay
            // readable over both the bubble above it and the thread behind it.
            reactionView.background = com.texto.sms.helpers.TextoGlass.bar(
                tint = threadBackgroundColor,
                cornerRadius = 100f * resources.displayMetrics.density,
                opacity = 1f,
                strokeWidthPx = 1.getScaledPxIn(activity as SimpleActivity),
                rimAlpha = 0.35f
            )
            val padH = 6.getScaledPxIn(activity as SimpleActivity)
            val padV = 2.getScaledPxIn(activity as SimpleActivity)
            reactionView.setPadding(padH, padV, padH, padV)
            reactionView.elevation = 4 * resources.displayMetrics.density
        }

        // A verification code gets a one-tap copy under its message, the same code the
        // notification's own action copies, so it is reachable after the notification is gone.
        if (binding is ItemMessageReceivedBinding) {
            val body = message.body
            val code = if (!message.isMMS && MessageClassifier.isOtpMessage(body)) {
                MessageClassifier.extractCode(body)
            } else {
                null
            }
            binding.threadMessageOtpCopy.apply {
                beVisibleIf(code != null)
                if (code != null) {
                    val simpleActivity = activity as SimpleActivity
                    val config = simpleActivity.config
                    val accent = config.accentGradientStart
                    val density = resources.displayMetrics.density
                    // The code in an isolate, so it keeps its own order inside a Persian label.
                    text = "${simpleActivity.getString(R.string.copy_verification_code)}  ⁦$code⁩"
                    setTextColor(config.mainTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
                    typeface = simpleActivity.typefaceFor(Typeface.BOLD)
                    val padH = 12.getScaledPxIn(simpleActivity)
                    val padV = 8.getScaledPxIn(simpleActivity)
                    setPaddingRelative(padH, padV, padH, padV)
                    background = TextoGlass.bar(
                        tint = accent,
                        cornerRadius = 100f * density,
                        opacity = 0.14f,
                        strokeWidthPx = 1.getScaledPxIn(simpleActivity),
                        rimAlpha = 0.30f
                    )
                    val icon = AppCompatResources.getDrawable(simpleActivity, R.drawable.ic_copy_vector)
                        ?.mutate()
                        ?.apply {
                            val size = 16.getScaledPxIn(simpleActivity)
                            setBounds(0, 0, size, size)
                            setTint(accent)
                        }
                    setCompoundDrawablesRelative(icon, null, null, null)
                    updateLayoutParams<RelativeLayout.LayoutParams> {
                        // Clear of the reaction badge, which hangs off the bubble's bottom edge.
                        topMargin = (if (reactionView.visibility == View.VISIBLE) 16 else 6)
                            .getScaledPxIn(simpleActivity)
                    }
                    setOnClickListener {
                        if (onEditAppearanceElement != null) return@setOnClickListener
                        simpleActivity.copyToClipboard(code)
                    }
                }
            }
        }

        // Selection Overlay Logic (Theme Perfect)
        overlay.beVisibleIf(isSelected)
        selectionCheck.beVisibleIf(isSelected)
        if (isSelected) {
            val simpleActivity = activity as SimpleActivity
            val highlightColor = if (simpleActivity.config.topBarColor != 0) {
                simpleActivity.run { simpleActivity.config.topBarColor.withAlpha(0.45f) }
            } else {
                simpleActivity.run { Color.BLACK.withAlpha(0.2f) }
            }
            
            val radius = 20f * simpleActivity.resources.displayMetrics.density
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = radius
                setColor(highlightColor)
            }
            overlay.background = shape
        }

        // NO LayoutParams logic here anymore! Alignment is handled by distinct XML files.

        bodyHolder.apply {
            // A recycled bubble can still wear the editor's pulse ring from before it scrolled away.
            if (onEditAppearanceElement == null) foreground = null
            val config = activity.config
            val bgColor = if (isReceived) receivedBubbleColor else sentBubbleColor
            
            val isNewUi = (activity as SimpleActivity).config.useNewUi
            val backgroundDrawable = AppCompatResources.getDrawable(activity, if (isReceived) R.drawable.item_received_background else R.drawable.item_sent_background)
            
            if (isNewUi) {
                val density = resources.displayMetrics.density
                val scaled = activity as SimpleActivity
                // Telegram's two radii: a large one all round, a small one where a bubble
                // meets its neighbour on the screen-edge side.
                val r20 = BUBBLE_RADIUS_DP.getScaledPxIn(scaled).toFloat()
                val r6 = BUBBLE_JOIN_RADIUS_DP.getScaledPxIn(scaled).toFloat()
                val tailWidth = BUBBLE_TAIL_W_DP.getScaledPxIn(scaled)

                // Telegram's pattern: the corners a bubble shares with its neighbours in a run
                // drop to the small radius on the screen-edge side, and the last bubble of the
                // run grows a tail out of that side's bottom corner. Radii are physical (TL,
                // TR, BR, BL) and not flipped for us, so the screen-edge side is read off the
                // layout direction: under RTL the received column sits on the right.
                val isRtl = resources.configuration.layoutDirection ==
                    View.LAYOUT_DIRECTION_RTL
                val onRight = isReceived == isRtl
                val edgeTop = if (joinsPrevious) r6 else r20
                val edgeBottom = if (joinsNext) r6 else r20
                // Physical corners, TL TR BR BL.
                val baseRadii = if (onRight) {
                    floatArrayOf(r20, r20, edgeTop, edgeTop, edgeBottom, edgeBottom, r20, r20)
                } else {
                    floatArrayOf(edgeTop, edgeTop, r20, r20, r20, r20, edgeBottom, edgeBottom)
                }

                val outlineOn =
                    if (isReceived) config.receivedBubblesOutline else config.sentBubblesOutline
                val outlineColor = if (!outlineOn) {
                    null
                } else if (isReceived) {
                    config.receivedBubblesOutlineColor
                } else {
                    config.sentBubblesOutlineColor
                }
                val outlineWidth = if (!outlineOn) {
                    0
                } else {
                    val thickness = if (isReceived) {
                        config.receivedBubblesOutlineThickness
                    } else {
                        config.sentBubblesOutlineThickness
                    }
                    (thickness * density).toInt()
                }

                // The accent side carries the skin's gradient; the other stays a single tint
                // so the two sides never compete for attention.
                val tintEnd = if (hasAccent) config.accentGradientEnd else null
                val tintMid = if (!hasAccent || config.accentGradientMid == 0) {
                    null
                } else {
                    config.accentGradientMid
                }

                // Both bubbles are solid in the design: `background: var(--bubble-in)` and
                // `background: var(--grad)`, with no transparency at all. Drawing the
                // received one at 70% let the background halos through it, so its colour
                // drifted with whatever was behind it instead of staying --bubble-in.
                // Telegram's tail: a hook out of the screen-edge bottom corner, on the last
                // bubble of a run only. Every bubble keeps the tail's width free on that side
                // so a run lines up on one edge.
                val colors = when {
                    tintEnd == null -> intArrayOf(bgColor)
                    tintMid == null -> intArrayOf(config.accentGradientStart, tintEnd)
                    else -> intArrayOf(config.accentGradientStart, tintMid, tintEnd)
                }
                background = com.texto.sms.helpers.TextoBubbleDrawable(
                    colors = colors,
                    radii = baseRadii,
                    tailOnRight = onRight,
                    showTail = !joinsNext,
                    tailWidth = tailWidth.toFloat(),
                    tailHeight = BUBBLE_TAIL_H_DP.getScaledPxIn(scaled).toFloat(),
                    strokeColor = outlineColor,
                    strokeWidth = outlineWidth.toFloat()
                )
                val padInner = BUBBLE_PAD_H_DP.getScaledPxIn(scaled)
                val padOuter = padInner + tailWidth
                setPadding(
                    if (onRight) padInner else padOuter,
                    BUBBLE_PAD_TOP_DP.getScaledPxIn(scaled),
                    if (onRight) padOuter else padInner,
                    BUBBLE_PAD_BOTTOM_DP.getScaledPxIn(scaled)
                )

                // Both bubbles sit flush. The design gives the sent one only `var(--soft)`,
                // a shadow tuned almost to nothing; a real 4dp lift instead put a visible
                // edge under it and made it read as raised against a flat mockup.
                elevation = 0f
                clipToOutline = false
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            } else {
                if (backgroundDrawable is GradientDrawable) {
                    backgroundDrawable.setColor(bgColor)
                } else if (backgroundDrawable != null) {
                    backgroundDrawable.applyColorFilter(bgColor)
                }
                background = backgroundDrawable
                elevation = 0f
            }

            setOnLongClickListener {
                onEditAppearanceElement?.let { edit ->
                    edit(this, message.isReceivedMessage())
                    return@setOnLongClickListener true
                }
                val wasSelecting = isSelectionModeActive()
                if (wasSelecting) holder.viewClicked(message) else holder.viewLongClicked()
                notifyItemChanged(holder.bindingAdapterPosition)
                updateCustomSelectionBar()
                // On the first long-press, offer the common actions right next to the
                // bubble. Subsequent long-presses are multi-select, so stay out of the way.
                if (!wasSelecting && isSelectionModeActive()) {
                    showBubbleActions(this, message)
                }
                true
            }

            setOnClickListener {
                onEditAppearanceElement?.let { edit ->
                    edit(this, message.isReceivedMessage())
                    return@setOnClickListener
                }
                // Two taps on the same bubble inside the platform's double-tap window open
                // the reaction strip. Counted here rather than through a GestureDetector on
                // a touch listener: the detector never saw the events on this view, while
                // this listener demonstrably fires, and it needs no second gesture pipeline.
                val now = System.currentTimeMillis()
                val isDoubleTap = lastTapMessageId == message.id &&
                    now - lastTapAt <= android.view.ViewConfiguration.getDoubleTapTimeout()
                lastTapMessageId = if (isDoubleTap) -1L else message.id
                lastTapAt = now
                if (isDoubleTap) {
                    quickReact(it, message)
                    return@setOnClickListener
                }

                if (isSelectionModeActive()) {
                    // viewClicked toggles this one bubble; viewLongClicked would select
                    // everything between here and the last long-pressed message.
                    holder.viewClicked(message)
                    notifyItemChanged(holder.bindingAdapterPosition)
                    updateCustomSelectionBar()
                } else {
                    // Outside selection this listener used to do nothing at all, so the
                    // activity's tap handler was never reached and a bubble was inert. The
                    // one tap that has somewhere to go is a failed message asking to be
                    // sent again; everything else still ignores a plain tap.
                    onItemTap(message)
                }
            }
        }

        bodyView.apply {
            val bgColor = if (isReceived) receivedBubbleColor else sentBubbleColor
            val textColor = if (isReceived) receivedBubbleTextColor else sentBubbleTextColor
            
            // Safety: if background and text are too similar or transparent, use defaults
            val finalTextColor = if (textColor == 0 || textColor == Color.TRANSPARENT || textColor == bgColor) {
                if (bgColor == Color.WHITE) Color.BLACK else Color.WHITE
            } else {
                textColor
            }

            background = null // Managed by bodyHolder
            elevation = 0f    // Managed by bodyHolder
            
            setTextColor(finalTextColor)
            alpha = 1.0f
            // A link on the accent bubble cannot be the accent colour -- it would vanish
            // into its own ground -- so only a flat bubble gets the accent link ink.
            setLinkTextColor(if (hasAccent) finalTextColor else activity.config.accentGradientStart)

            // Figures in the body become tappable so a single account number, code or
            // amount can be copied or forwarded without hand-selecting text; web addresses
            // open in the phone's browser.
            com.texto.sms.helpers.NumberSpans.apply(
                textView = this,
                // Isolated so a link or a number inside a Persian sentence keeps its own
                // direction; the spans are found on the same string they are laid on.
                text = com.texto.sms.helpers.NumberSpans.isolateLtrRuns(message.body),
                onNumberTapped = { number -> showNumberActions(this, number) },
                onUrlTapped = { url -> openMessageLink(url) }
            )
            // Layered over whatever spans NumberSpans just installed rather than replacing
            // the text, so a number inside a search hit stays tappable.
            // Against the text on screen rather than message.body: the isolates added above
            // shift every offset after the first link, and a highlight computed on the raw
            // body would land a few characters early.
            highlightSearchTerm(this, text.toString(), finalTextColor)
            visibility = if (message.body.isNotEmpty()) View.VISIBLE else View.GONE
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize)
            
            val customTypeface = (activity as SimpleActivity).getCustomTypeface()
            val style = if (message.isScheduled) Typeface.ITALIC else Typeface.NORMAL
            typeface = Typeface.create(customTypeface, style)

            // Ensure clicks pass through to the bubble container. NumberSpans installs a
            // movement method only when there is something to tap, and that one hands
            // the touch back when it does not land on a number.
            isClickable = true
            isFocusable = true
            isLongClickable = true

            setOnClickListener {
                bodyHolder.performClick()
            }
            
            setOnLongClickListener {
                bodyHolder.performLongClick()
            }

            if (!isReceived && message.isScheduled) {
                val scheduledDrawable = AppCompatResources.getDrawable(activity, R.drawable.ic_ph_clock)?.apply {
                    applyColorFilter(finalTextColor)
                    val size = lineHeight
                    setBounds(0, 0, size, size)
                }
                setCompoundDrawables(null, null, scheduledDrawable, null)
            } else {
                setCompoundDrawables(null, null, null, null)
            }
        }

        if (message.attachment?.attachments?.isNotEmpty() == true) {
            attachmentsHolder.beVisible()
            if (attachmentsHolder.tag != message.id) {
                attachmentsHolder.removeAllViews()
                for (attachment in message.attachment.attachments) {
                    val mimetype = attachment.mimetype
                    when {
                        mimetype.isImageMimeType() || mimetype.isVideoMimeType() -> setupImageView(holder, binding, message, attachment)
                        mimetype.isVCardMimeType() -> setupVCardView(holder, attachmentsHolder, message, attachment)
                        else -> setupFileView(holder, attachmentsHolder, message, attachment)
                    }
                }
                attachmentsHolder.tag = message.id
            }
            
            val hasVideo = message.attachment.attachments.any { it.mimetype.startsWith("video/") }
            playOutline.beVisibleIf(hasVideo)
        } else {
            attachmentsHolder.beGone()
            playOutline.beGone()
            attachmentsHolder.tag = null
        }
    }

    private fun setupImageView(holder: ViewHolder, binding: ViewBinding, message: Message, attachment: Attachment) {
        val attachmentsHolder = if (binding is ItemMessageReceivedBinding) binding.threadMessageAttachmentsHolder else (binding as ItemMessageSentBinding).threadMessageAttachmentsHolder
        val playOutline = if (binding is ItemMessageReceivedBinding) binding.threadMessagePlayOutline else (binding as ItemMessageSentBinding).threadMessagePlayOutline
        
        val mimetype = attachment.mimetype
        val uri = attachment.getUri()

        val imageViewBinding = ItemAttachmentImageBinding.inflate(layoutInflater)
        attachmentsHolder.addView(imageViewBinding.root)

        // Set stable placeholder height to prevent jitter
        imageViewBinding.attachmentImage.updateLayoutParams<ViewGroup.LayoutParams> {
            width = maxChatBubbleWidth
            height = (maxChatBubbleWidth * 0.6f).toInt() // Stable aspect ratio placeholder
        }

        val placeholderDrawable = Color.TRANSPARENT.toDrawable()
        val options = RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.RESOURCE)
            .placeholder(placeholderDrawable)
            .transform(FitCenter())

        Glide.with(activity)
            .load(uri)
            .apply(options)
            .dontAnimate()
            .override(maxChatBubbleWidth, (maxChatBubbleWidth * MAX_MEDIA_HEIGHT_RATIO))
            .downsample(DownsampleStrategy.AT_MOST)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(e: GlideException?, model: Any?, target: Target<Drawable>?, isFirstResource: Boolean): Boolean {
                    imageViewBinding.attachmentImage.updateLayoutParams<ViewGroup.LayoutParams> {
                        width = maxChatBubbleWidth
                        height = (maxChatBubbleWidth * 0.6f).toInt()
                    }
                    imageViewBinding.attachmentImage.setImageResource(R.drawable.ic_image_vector)
                    imageViewBinding.attachmentImage.applyColorFilter(activity.config.accentGradientStart)
                    playOutline.beGone()
                    return true
                }

                override fun onResourceReady(resource: Drawable?, model: Any?, target: Target<Drawable>?, dataSource: DataSource?, isFirstResource: Boolean): Boolean {
                    // Adjust height only after load, but Glide does this smoothly with dontAnimate
                    imageViewBinding.attachmentImage.updateLayoutParams<ViewGroup.LayoutParams> {
                        width = maxChatBubbleWidth
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                    return false
                }
            })
            .into(imageViewBinding.attachmentImage)

        imageViewBinding.attachmentImage.updateLayoutParams<ViewGroup.LayoutParams> {
            width = maxChatBubbleWidth
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }

        imageViewBinding.attachmentImage.setOnClickListener {
            if (isSelecting()) {
                holder.viewClicked(message)
            } else {
                activity.launchViewIntent(uri, mimetype, attachment.filename)
            }
        }
        imageViewBinding.root.setOnLongClickListener {
            holder.viewLongClicked()
            true
        }
    }

    private fun setupVCardView(holder: ViewHolder, parent: LinearLayout, message: Message, attachment: Attachment) {
        val uri = attachment.getUri()
        val vCardView = ItemAttachmentVcardBinding.inflate(layoutInflater).apply {
            setupVCardPreview(
                activity = activity,
                uri = uri,
                onClick = {
                    if (isSelecting()) {
                        holder.viewClicked(message)
                    } else {
                        val intent = Intent(activity, VCardViewerActivity::class.java).also {
                            it.putExtra(EXTRA_VCARD_URI, uri)
                        }
                        activity.startActivity(intent)
                    }
                },
                onLongClick = { holder.viewLongClicked() }
            )
        }.root

        parent.addView(vCardView)
    }

    private fun setupFileView(holder: ViewHolder, parent: LinearLayout, message: Message, attachment: Attachment) {
        val mimetype = attachment.mimetype
        val uri = attachment.getUri()
        val attachmentView = ItemAttachmentDocumentBinding.inflate(layoutInflater).apply {
            setupDocumentPreview(
                uri = uri,
                title = attachment.filename,
                mimeType = attachment.mimetype,
                onClick = {
                    if (isSelecting()) {
                        holder.viewClicked(message)
                    } else {
                        activity.launchViewIntent(uri, mimetype, attachment.filename)
                    }
                },
                onLongClick = { holder.viewLongClicked() }
            )
        }.root

        parent.addView(attachmentView)
    }

    private fun setupDateTime(view: View, dateTime: ThreadDateTime) {
        ItemThreadDateTimeBinding.bind(view).apply {
            val simpleActivity = activity as SimpleActivity
            val config = activity.config
            threadDateTime.apply {
                visibility = View.VISIBLE
                // A day label, not a full timestamp: the clock time now lives inside each
                // bubble, so repeating it here said the same thing twice.
                text = (dateTime.date * 1000L).formatUiDayLabel(activity)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.68f)
                typeface = Typeface.create(simpleActivity.getCustomTypeface(), Typeface.NORMAL)

                // The design's date chip: a glass pill behind the `--divider` hairline.
                val padH = with(simpleActivity) { 14.getScaledPx() }
                val padV = with(simpleActivity) { 5.getScaledPx() }
                setPadding(padH, padV, padH, padV)
                background = TextoGlass.bar(
                    tint = config.recentColor,
                    cornerRadius = 100f * resources.displayMetrics.density,
                    opacity = 0.5f,
                    strokeWidthPx = with(simpleActivity) { 1.getScaledPx() },
                    rimAlpha = 0.22f
                )
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            }

            val dateTimeColor = config.mainTextColor
            if (dateTimeColor != 0 && dateTimeColor != Color.TRANSPARENT) {
                threadDateTime.setTextColor(dateTimeColor.withAlpha(0.68f))
                threadDateTime.alpha = 1.0f
            }

            // The SIM this day's messages went out on, as a filled dot in the colour that
            // slot carries everywhere else in the app. Only worth drawing on a dual-SIM
            // phone, where which card a message used is a real question.
            //
            // The card's own silhouette, filled with the slot colour, rather than the
            // Phosphor outline it used to be: at this size an outline collapses into a
            // smudge, and the colour is the whole of what the marker says.
            threadSimNumber.beGone()
            // A recycled marker can still wear the editor's pulse ring from before it scrolled away.
            if (onEditAppearanceElement == null) threadSimIcon.foreground = null
            threadSimIcon.beVisibleIf(hasMultipleSIMCards)
            if (hasMultipleSIMCards) {
                val slot = dateTime.simID.toIntOrNull()?.minus(1)?.coerceAtLeast(0) ?: 0
                val side = (fontSize * 0.95f).toInt().coerceAtLeast(1)
                threadSimIcon.background = null
                threadSimIcon.scaleType = ImageView.ScaleType.FIT_CENTER
                threadSimIcon.setImageResource(R.drawable.ic_sim_card_filled)
                threadSimIcon.imageTintList =
                    android.content.res.ColorStateList.valueOf(config.getSimColor(slot))
                threadSimIcon.updateLayoutParams {
                    width = side
                    height = side
                }
            }
        }
    }

    private fun setupThreadSuccess(view: View, isDelivered: Boolean) {
        ItemThreadSuccessBinding.bind(view).apply {
            threadSuccess.setImageResource(if (isDelivered) R.drawable.ic_check_double_vector else R.drawable.ic_ph_check)
            threadSuccess.applyColorFilter(Color.GRAY)
        }
    }

    private fun setupThreadError(view: View, item: ThreadError) {
        val binding = ItemThreadErrorBinding.bind(view)
        // A failed send in the app's own warning red, the same one the menus give delete.
        // It shipped in commons' dark-theme red from the layout and was then repainted as
        // ordinary body text here, which left the one line that reports a failure looking
        // exactly like the messages that went through.
        binding.threadError.setTextColor(DESTRUCTIVE_INK)
        binding.threadError.setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
        // The caption says "touch to retry" and had no listener behind it, in this adapter
        // or anywhere else, for as long as it has been on screen.
        binding.threadError.setOnClickListener { onItemTap(item) }
    }

    private fun setupThreadSending(view: View) {
        ItemThreadSendingBinding.bind(view).threadSending.apply {
            setTextColor(activity.config.mainTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
        }
    }

    private fun adjustColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = Math.round(Color.red(color) * factor).coerceIn(0, 255)
        val g = Math.round(Color.green(color) * factor).coerceIn(0, 255)
        val b = Math.round(Color.blue(color) * factor).coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        if (!activity.isDestroyed && !activity.isFinishing) {
            val binding = (holder as ThreadViewHolder).binding
            val photoView = if (binding is ItemMessageReceivedBinding) {
                binding.threadMessageSenderPhoto
            } else if (binding is ItemMessageSentBinding) {
                binding.threadMessageSenderPhoto
            } else {
                null
            }
            
            photoView?.let {
                Glide.with(activity).clear(it)
            }
        }
    }

    inner class ThreadViewHolder(val binding: ViewBinding) : ViewHolder(binding.root)

    private fun getItemIdForRawItem(item: ThreadItem): Long {
        return when (item) {
            is Message -> item.getStableId()
            is ThreadDateTime -> {
                val sim = (item.simID.hashCode().toLong() and SIM_MASK)
                val key = (item.date.toLong() shl SIM_BITS) or sim
                generateStableId(THREAD_DATE_TIME, key)
            }
            is ThreadError -> generateStableId(THREAD_SENT_MESSAGE_ERROR, item.messageId)
            is ThreadSending -> generateStableId(THREAD_SENT_MESSAGE_SENDING, item.messageId)
            is ThreadSent -> generateStableId(THREAD_SENT_MESSAGE_SENT, item.messageId)
        }
    }
}

private class ThreadItemDiffCallback : DiffUtil.ItemCallback<ThreadItem>() {

    override fun areItemsTheSame(oldItem: ThreadItem, newItem: ThreadItem): Boolean {
        if (oldItem::class.java != newItem::class.java) return false
        return when (oldItem) {
            is ThreadError -> oldItem.messageId == (newItem as ThreadError).messageId
            is ThreadSent -> oldItem.messageId == (newItem as ThreadSent).messageId
            is ThreadSending -> oldItem.messageId == (newItem as ThreadSending).messageId
            is Message -> Message.areItemsTheSame(oldItem, newItem as Message)
            is ThreadDateTime -> {
                val new = newItem as ThreadDateTime
                oldItem.date == new.date && oldItem.simID == new.simID
            }
            else -> false
        }
    }

    override fun areContentsTheSame(oldItem: ThreadItem, newItem: ThreadItem): Boolean {
        if (oldItem::class.java != newItem::class.java) return false
        return when (oldItem) {
            is ThreadSending -> true
            is ThreadDateTime -> oldItem.simID == (newItem as ThreadDateTime).simID
            is ThreadError -> oldItem.messageText == (newItem as ThreadError).messageText
            is ThreadSent -> oldItem.delivered == (newItem as ThreadSent).delivered
            is Message -> Message.areContentsTheSame(oldItem, newItem as Message)
            else -> true
        }
    }
}
