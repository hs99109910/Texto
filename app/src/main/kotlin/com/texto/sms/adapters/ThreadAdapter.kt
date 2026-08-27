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
import org.fossify.commons.adapters.MyRecyclerViewListAdapter
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.views.MyRecyclerView
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

class ThreadAdapter(
    activity: SimpleActivity,
    recyclerView: MyRecyclerView,
    itemClick: (Any) -> Unit,
    private val isRecycleBin: Boolean,
    private val deleteMessages: (messages: List<Message>, toRecycleBin: Boolean, fromRecycleBin: Boolean) -> Unit
) : MyRecyclerViewListAdapter<ThreadItem>(activity, recyclerView, ThreadItemDiffCallback(), itemClick) {

    private val hasMultipleSIMCards = try {
        activity.subscriptionManagerCompat().activeSubscriptionInfoList?.size ?: 0 > 1
    } catch (_: SecurityException) {
        false
    }
    
    private val uiScale get() = (activity as SimpleActivity).uiScale
    private val maxChatBubbleWidth = (activity.usableScreenSize.x * 0.75f).toInt()
    private var fontSize = (activity as SimpleActivity).getScaledTextSize()
    private var lastAnimatedPosition = -1

    fun updateScaling() {
        fontSize = (activity as SimpleActivity).getScaledTextSize()
        notifyItemRangeChanged(0, itemCount)
    }

    companion object {
        private const val MAX_MEDIA_HEIGHT_RATIO = 2
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
        android.util.Log.d("ThreadSelection", "Action mode created")
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
    private fun showNumberActions(anchor: View, number: String) {
        val simpleActivity = activity as? SimpleActivity ?: return
        if (simpleActivity.isFinishing || simpleActivity.isDestroyed) return

        // Icons alone: the actions are unmistakable and a label would only crowd a
        // menu that pops up right next to the figure being acted on.
        val items = arrayListOf(
            SimpleActivity.BubbleAction(
                R.id.dial_number,
                "",
                org.fossify.commons.R.drawable.ic_phone_vector
            ),
            SimpleActivity.BubbleAction(R.id.copy_number, "", R.drawable.ic_copy_vector),
            SimpleActivity.BubbleAction(
                R.id.cab_forward_message,
                "",
                R.drawable.ic_forward_vector
            )
        )

        simpleActivity.showBubbleMenu(anchor, items) { actionId ->
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
                R.drawable.ic_check_circle_filled
            )
        )
        if (!message.isMMS) {
            items.add(
                SimpleActivity.BubbleAction(
                    R.id.cab_copy_to_clipboard,
                    simpleActivity.getString(org.fossify.commons.R.string.copy_to_clipboard),
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
                    simpleActivity.getString(org.fossify.commons.R.string.save_as),
                    R.drawable.ic_download_vector
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
                actionItemPressed(actionId)
            }
        }
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
                if (position > lastAnimatedPosition) {
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
                    lastAnimatedPosition = position
                }
            }
            is ThreadDateTime -> setupDateTime(binding.root, item)
            is ThreadError -> setupThreadError(binding.root)
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

    private fun selectText() {
        val message = getSelectedItems().first() as Message
        val binding = DialogSelectTextBinding.inflate(layoutInflater)
        binding.dialogSelectTextValue.text = message.body
        (activity as SimpleActivity).updateAppFonts(binding.root)
        ConfirmationDialog(activity, "", 0, org.fossify.commons.R.string.ok, 0, false, message.body) {
            // Nothing to do
        }
    }

    private fun showProperties() {
        val message = getSelectedItems().first() as Message
        (activity as ThreadActivity).showProperties(message)
        finishActMode()
    }

    private fun askConfirmDelete() {
        val items = getSelectedItems().filterIsInstance<Message>()
        val baseString = org.fossify.commons.R.string.deletion_confirmation
        val message = String.format(activity.getString(baseString), items.size)
        ConfirmationDialog(activity, message) {
            deleteMessages(items, false, false)
            finishActMode()
        }
    }

    private fun askConfirmRestore() {
        val items = getSelectedItems().filterIsInstance<Message>()
        val message = if (items.size == 1) {
            activity.getString(R.string.restore_confirmation, items.first().senderName)
        } else {
            activity.getString(org.fossify.commons.R.string.files_restored_successfully, items.size)
        }

        ConfirmationDialog(activity, message) {
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

    private fun setupView(holder: ViewHolder, binding: ViewBinding, message: Message) {
        val isSelected = selectedKeys.contains(message.getSelectionKey())
        val isReceived = message.isReceivedMessage()
        
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

        // One tick once sent, two once the carrier reports delivery. Only meaningful on
        // outgoing messages, and only when delivery reports are switched on.
        if (binding is ItemMessageSentBinding) {
            val isDelivered = message.status == android.provider.Telephony.Sms.STATUS_COMPLETE
            val isSent = message.type == android.provider.Telephony.Sms.MESSAGE_TYPE_SENT
            binding.deliveryStatus.apply {
                beVisibleIf(!isReceived && isSent)
                setImageResource(
                    if (isDelivered) R.drawable.ic_check_delivered else R.drawable.ic_check_single
                )
                // A delivered pair is wider than a single tick, so the view has to grow
                // with it or the second tick gets clipped.
                val density = resources.displayMetrics.density
                layoutParams = layoutParams.apply {
                    width = ((if (isDelivered) 20 else 16) * density).toInt()
                    height = (16 * density).toInt()
                }
                imageTintList = android.content.res.ColorStateList.valueOf(
                    (activity as SimpleActivity).config.sentBubbleTextColor
                )
            }
        }

        // Reactions are removed from the UI. The column stays in the database so no
        // migration is needed, but nothing reads or writes it any more.
        reactionView.beGone()

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
            val config = activity.config
            val bgColor = if (isReceived) config.receivedBubbleColor else config.sentBubbleColor
            
            val isNewUi = (activity as SimpleActivity).config.useNewUi
            val backgroundDrawable = AppCompatResources.getDrawable(activity, if (isReceived) R.drawable.item_received_background else R.drawable.item_sent_background)
            
            if (isNewUi) {
                val density = resources.displayMetrics.density
                val r18 = 18f * density
                val r4 = 4f * density

                val baseRadii = if (isReceived) {
                    floatArrayOf(r18, r18, r18, r18, r18, r18, r4, r4)
                } else {
                    floatArrayOf(r18, r18, r18, r18, r4, r4, r18, r18)
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

                // Sent bubbles carry the skin's accent gradient; received ones stay a single
                // tint so the two sides never compete for attention.
                val tintEnd = if (isReceived) null else config.accentGradientEnd

                // Frosted bubble, kept dense so message text stays fully legible.
                background = com.texto.sms.helpers.NovaGlass.panel(
                    tint = if (isReceived) bgColor else config.accentGradientStart,
                    tintEnd = tintEnd,
                    cornerRadii = baseRadii,
                    opacity = 0.88f,
                    strokeWidthPx = density.toInt().coerceAtLeast(1),
                    outlineColor = outlineColor,
                    outlineWidthPx = outlineWidth
                )

                // Material 3 Depth (Standardized for stability)
                elevation = 4f * density
                clipToOutline = false 
                outlineProvider = object : android.view.ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: android.graphics.Outline) {
                        val path = android.graphics.Path()
                        // Use baseRadii for clipping to ensure content matches the visual interior
                        path.addRoundRect(
                            0f, 0f, view.width.toFloat(), view.height.toFloat(),
                            baseRadii, android.graphics.Path.Direction.CW
                        )
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                            outline.setPath(path)
                        } else {
                            @Suppress("DEPRECATION")
                            outline.setConvexPath(path)
                        }
                    }
                }
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
                if (isSelectionModeActive()) {
                    // viewClicked toggles this one bubble; viewLongClicked would select
                    // everything between here and the last long-pressed message.
                    holder.viewClicked(message)
                    notifyItemChanged(holder.bindingAdapterPosition)
                    updateCustomSelectionBar()
                }
            }
        }

        bodyView.apply {
            val config = activity.config
            val bgColor = if (isReceived) config.receivedBubbleColor else config.sentBubbleColor
            val textColor = if (isReceived) config.receivedBubbleTextColor else config.sentBubbleTextColor
            
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
            setLinkTextColor(if (isReceived) activity.getProperPrimaryColor() else finalTextColor)

            // Figures in the body become tappable so a single account number, code or
            // amount can be copied or forwarded without hand-selecting text.
            com.texto.sms.helpers.NumberSpans.apply(this, message.body) { number ->
                showNumberActions(this, number)
            }
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
                android.util.Log.d("ReactionInteraction", "bodyView tapped for message ${message.id}, delegating to bodyHolder")
                bodyHolder.performClick()
            }
            
            setOnLongClickListener {
                android.util.Log.d("ReactionInteraction", "bodyView long-pressed for message ${message.id}, delegating to bodyHolder")
                bodyHolder.performLongClick()
            }

            if (!isReceived && message.isScheduled) {
                val scheduledDrawable = AppCompatResources.getDrawable(activity, org.fossify.commons.R.drawable.ic_clock_vector)?.apply {
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
                    imageViewBinding.attachmentImage.applyColorFilter(activity.getProperPrimaryColor())
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
            if (actModeCallback.isSelectable) {
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
                    if (actModeCallback.isSelectable) {
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
                    if (actModeCallback.isSelectable) {
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
            threadDateTime.apply {
                visibility = View.VISIBLE
                text = (dateTime.date * 1000L).formatJalaliDateOrTime()
                setTextSize(TypedValue.COMPLEX_UNIT_PX, fontSize * 0.8f)
                val customTypeface = (activity as SimpleActivity).getCustomTypeface()
                typeface = Typeface.create(customTypeface, Typeface.NORMAL)
            }
            
            val dateTimeColor = activity.config.mainTextColor
            if (dateTimeColor != 0 && dateTimeColor != Color.TRANSPARENT) {
                threadDateTime.setTextColor(dateTimeColor)
                threadDateTime.alpha = 1.0f
            }

            threadSimIcon.beVisibleIf(hasMultipleSIMCards)
            // The slot digit is gone: at this size it was unreadable and it shared the
            // icon's colour anyway. The badge is now half as big and simply carries the
            // colour the user assigned to that SIM in Settings.
            threadSimNumber.beGone()
            if (hasMultipleSIMCards) {
                val slot = dateTime.simID.toIntOrNull()?.minus(1)?.coerceAtLeast(0) ?: 0
                threadSimIcon.applyColorFilter(activity.config.getSimColor(slot))
                threadSimIcon.updateLayoutParams {
                    width = (fontSize * 0.6f).toInt()
                    height = (fontSize * 0.6f).toInt()
                }
            }
        }
    }

    private fun setupThreadSuccess(view: View, isDelivered: Boolean) {
        ItemThreadSuccessBinding.bind(view).apply {
            threadSuccess.setImageResource(if (isDelivered) R.drawable.ic_check_double_vector else org.fossify.commons.R.drawable.ic_check_vector)
            threadSuccess.applyColorFilter(Color.GRAY)
        }
    }

    private fun setupThreadError(view: View) {
        val binding = ItemThreadErrorBinding.bind(view)
        binding.threadError.setTextColor(activity.getProperTextColor())
    }

    private fun setupThreadSending(view: View) {
        ItemThreadSendingBinding.bind(view).threadSending.apply {
            setTextColor(activity.getProperTextColor())
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
