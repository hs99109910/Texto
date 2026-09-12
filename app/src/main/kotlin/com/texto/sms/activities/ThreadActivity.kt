package com.texto.sms.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
import android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Telephony
import android.telephony.SmsMessage
import android.telephony.SubscriptionInfo
import android.text.TextUtils
import android.util.TypedValue
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.graphics.drawable.toDrawable
import android.view.ViewGroup
import android.view.KeyEvent
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.webkit.MimeTypeMap
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView


import com.texto.sms.BuildConfig
import com.texto.sms.R
import com.texto.sms.adapters.AttachmentsAdapter
import com.texto.sms.adapters.AutoCompleteTextViewAdapter
import com.texto.sms.adapters.ThreadAdapter
import com.texto.sms.databinding.ActivityThreadBinding
import com.texto.sms.databinding.ItemSelectedContactBinding
import com.texto.sms.dialogs.MessageDetailsDialog
import com.texto.sms.dialogs.RenameConversationDialog
import com.texto.sms.dialogs.ScheduleMessageDialog
import com.texto.sms.extensions.*
import com.texto.sms.helpers.CapsuleChoice
import com.texto.sms.helpers.textoCapsuleDialog
import com.texto.sms.helpers.textoConfirmDialog
import com.texto.sms.helpers.*
import com.texto.sms.messaging.isLongMmsMessage
import com.texto.sms.messaging.isShortCodeWithLetters
import com.texto.sms.models.*
import com.texto.sms.models.ThreadItem.ThreadDateTime
import com.texto.sms.models.ThreadItem.ThreadError
import com.texto.sms.models.ThreadItem.ThreadSending
import com.texto.sms.models.ThreadItem.ThreadSent
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.joda.time.DateTime
import java.io.File
import com.texto.sms.models.SimpleContact
import com.texto.sms.messaging.*
import com.texto.sms.dialogs.AttachmentPickerDialog
import com.texto.sms.extensions.copyToClipboard
import com.texto.sms.extensions.darkenColor
import com.texto.sms.extensions.getBottomNavigationBackgroundColor
import com.texto.sms.extensions.getContrastColor
import com.texto.sms.extensions.getFilenameFromPath
import com.texto.sms.extensions.getFilenameFromUri
import com.texto.sms.extensions.getMyFileUri
import com.texto.sms.extensions.getTimeFormat
import com.texto.sms.extensions.hideKeyboard
import com.texto.sms.extensions.normalizeString
import com.texto.sms.extensions.notificationManager
import com.texto.sms.extensions.onTextChangeListener
import com.texto.sms.extensions.openRequestExactAlarmSettings
import com.texto.sms.extensions.showKeyboard
import com.texto.sms.extensions.usableScreenSize
import com.texto.sms.extensions.applyColorFilter
import com.texto.sms.extensions.beGone
import com.texto.sms.extensions.isVisible
import com.texto.sms.extensions.beVisible
import com.texto.sms.extensions.beGoneIf
import com.texto.sms.extensions.beVisibleIf
import com.texto.sms.extensions.toast
import com.texto.sms.extensions.showErrorToast
import com.texto.sms.extensions.viewBinding
import com.texto.sms.helpers.NavigationIcon
import com.texto.sms.extensions.PERMISSION_CAMERA
import com.texto.sms.extensions.PERMISSION_READ_PHONE_STATE
import com.texto.sms.extensions.PERMISSION_RECORD_AUDIO
import com.texto.sms.helpers.SimpleContactsHelper
import com.texto.sms.extensions.isQPlus
import com.texto.sms.extensions.isSPlus

class ThreadActivity : SimpleActivity() {

    private var threadId = 0L
    private var currentSIMCardIndex = 0
    private var isActivityVisible = false
    private var isFirstResume = true
    private var refreshedSinceSent = false
    private var threadItems = ArrayList<ThreadItem>()

    /**
     * The in-thread search term, or empty when the bar is closed.
     *
     * Held on the activity rather than read off the field, because the refresh paths below
     * run from background work and from EventBus and have to know whether what they are
     * about to submit should be filtered.
     */
    private var threadSearchQuery = ""
    private var bus: EventBus? = null
    private var conversation: Conversation? = null
    private var participants = ArrayList<SimpleContact>()
    private var messages = ArrayList<Message>()
    private val availableSIMCards = ArrayList<SIMCard>()
    private var pendingAttachmentsToSave: List<Attachment>? = null
    private var capturedImageUri: Uri? = null
    private var loadingOlderMessages = false
    private var allMessagesFetched = false
    private var isJumpingToMessage = false
    private var isRecycleBin = false
    private var isLaunchedFromShortcut = false
    private var isFromNotification = false

    private var isScheduledMessage: Boolean = false
    private var messageToResend: Long? = null
    private lateinit var scheduledDateTime: DateTime
    private var isRefreshing = false

    /**
     * A refresh that arrived while another was in flight, kept rather than dropped.
     *
     * [setupAdapter] used to return outright when [isRefreshing] was set, which threw the
     * newer request away: a send posts its own refresh and the sent:status receiver posts a
     * second one moments later, so the two overlap routinely and whichever lost carried the
     * message. Nothing then redrew the list until the screen was rebuilt.
     */
    private var refreshQueued = false
    private var queuedForceScroll = false

    /**
     * Set the instant send is tapped and consumed by the next list update.
     *
     * The scroll after a send cannot be decided by asking the list where it is: by the time
     * the question is asked the new bubble is already in it, so there is always somewhere
     * further to go and the answer is always "not at the end". The intent is recorded up
     * front instead, on the thread the tap arrives on, and survives however many refreshes
     * it takes for the message to actually appear.
     */
    private var scrollOnNextUpdate = false
    private var isSendingMessage = false
    private var wasImeVisible = false
    private var isEmojiPanelOpen = false
    private var threadAnchorPosition = RecyclerView.NO_POSITION
    private var threadAnchorOffset = 0

    private val binding by viewBinding(ActivityThreadBinding::inflate)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        finish()
        startActivity(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setupOptionsMenu()
        refreshMenuItems()
        setupEdgeToEdge(
            padBottomImeAndSystem = listOf(
                binding.messageHolder.root,
                binding.shortCodeHolder.root
            )
        )
        setupMessagingEdgeToEdge()

        val extras = intent.extras
        if (extras == null) {
            toast(R.string.unknown_error_occurred)
            finish()
            return
        }

        threadId = intent.getLongExtra(THREAD_ID, 0L)
        intent.getStringExtra(THREAD_TITLE)?.let {
            binding.threadToolbarTitle.text = it
        }
        isRecycleBin = intent.getBooleanExtra(IS_RECYCLE_BIN, false)
        isLaunchedFromShortcut = intent.getBooleanExtra(IS_LAUNCHED_FROM_SHORTCUT, false)
        isFromNotification = intent.getBooleanExtra(IS_FROM_NOTIFICATION, false)

        bus = EventBus.getDefault()
        bus!!.register(this)

        // A message that lands with no motion at all reads as nothing having happened, and
        // this list had its animator switched off entirely. Change animations stay off: a
        // reaction or a delivery tick redraws a bubble already on screen, and cross:fading
        // it there is a flicker, not feedback.
        binding.threadMessagesList.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 220
            removeDuration = 160
            moveDuration = 220
            supportsChangeAnimations = false
        }
        binding.threadMessagesList.setItemViewCacheSize(20)
        (binding.threadMessagesList.layoutManager as LinearLayoutManager).stackFromEnd = true
        loadConversation()
        setupExpandingInputBar()
        setupThreadSearch()
        setupThreadScrollAnchoring()

        // Keyboard Sync: Shrink input bar when keyboard goes down
        ViewCompat.setOnApplyWindowInsetsListener(binding.threadHolder) { _, insets ->
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (wasImeVisible && !isImeVisible && config.useNewUi && binding.messageHolder.threadTypeMessage.text?.isEmpty() == true && !config.alwaysExpandSearchBar) {
                shrinkInputBar()
            }
            // The keyboard's own height, for the emoji panel to match. Taken while it is up,
            // net of the navigation bar it overlaps, and kept so the first emoji tap of a
            // session opens at the right height rather than guessing at one.
            if (isImeVisible) {
                val imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val navBottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                val keyboard = imeBottom - navBottom
                if (keyboard > 0) config.lastKeyboardHeight = keyboard
                // The keyboard and the panel are two answers to the same question, so the one
                // coming up puts the other away rather than stacking under it.
                if (isEmojiPanelOpen) closeEmojiPanel()
            }
            wasImeVisible = isImeVisible
            insets
        }
    }

    /**
     * The design's composer is one full-width row on every screen: a field that fills
     * whatever the send disc leaves beside it. That replaced an older variant which sat as a
     * 240dp pill in the middle and grew when you tapped it -- the field is a weighted child
     * of the row now, so there is no free width for that animation to run in, and the send
     * disc outside it had nothing sensible to do while the field was narrow.
     *
     * [expandInputBar] and [shrinkInputBar] survive as the focus half of that behaviour.
     */
    /**
     * Keeps the thread where the reader left it when a bar above it changes height.
     *
     * Anything that grows the app bar resizes the list laid out below it, and `stackFromEnd`
     * then re-anchors what is left to the *newest* message: measured, the bubbles moved 569px
     * up the viewport and the item animator glided them there, 53px a frame. The selection bar
     * no longer grows the app bar -- it floats over the thread instead, see activity_thread --
     * but the search bar still does, and it is the same list underneath.
     *
     * The anchor is recorded on every scroll rather than when the bar opens, because by the
     * time anything here hears about that the re-layout has already happened.
     */
    private fun rememberThreadAnchor() {
        val manager = binding.threadMessagesList.layoutManager as? LinearLayoutManager ?: return
        val position = manager.findFirstVisibleItemPosition()
        if (position == RecyclerView.NO_POSITION) return
        val child = manager.findViewByPosition(position) ?: return
        threadAnchorPosition = position
        threadAnchorOffset = child.top - binding.threadMessagesList.paddingTop
    }

    private fun restoreThreadAnchor() {
        if (threadAnchorPosition == RecyclerView.NO_POSITION) return
        val manager = binding.threadMessagesList.layoutManager as? LinearLayoutManager ?: return
        manager.scrollToPositionWithOffset(threadAnchorPosition, threadAnchorOffset)
    }

    private fun setupThreadScrollAnchoring() {
        binding.threadMessagesList.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    rememberThreadAnchor()
                }
            }
        )
        binding.threadAppbar.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom != oldBottom && oldBottom != 0) {
                // The list is resized in this same traversal, so the restore waits for the
                // one after it -- by then the re-anchor it is undoing has happened.
                binding.threadMessagesList.post { restoreThreadAnchor() }
            }
        }
    }

    /**
     * Swaps the emoji panel for the keyboard, the way the keyboard's own panel would.
     *
     * The panel is a view in the layout under the composer rather than a dialog over it, so
     * the composer -- and the send button on the end of it -- stays on screen and in the same
     * place whichever of the two is up. It is sized to the keyboard's last measured height,
     * so swapping between them moves nothing.
     */
    private fun toggleEmojiPanel() {
        if (isEmojiPanelOpen) {
            closeEmojiPanel()
            // The field lost focus when the panel took the keyboard's place, and a show
            // request against an unfocused field is dropped: focus first, ask on the frame
            // after, which is the same retry showKeyboard already does for its own reasons.
            val field = binding.messageHolder.threadTypeMessage
            field.requestFocus()
            field.post { showKeyboard(field) }
        } else {
            openEmojiPanel()
        }
    }

    private fun openEmojiPanel() {
        val panel = binding.emojiPanel
        // A keyboard this app has never seen leaves nothing to match, so fall back to a
        // height in the range every soft keyboard lands in rather than to none at all.
        val height = config.lastKeyboardHeight.takeIf { it > 0 } ?: 280.getScaledPx()
        panel.updateLayoutParams { this.height = height }
        buildTextoEmojiPanel(panel) { glyph ->
            val field = binding.messageHolder.threadTypeMessage
            val at = field.selectionStart.coerceAtLeast(0)
            field.text?.insert(at, glyph)
        }
        // The list is padded clear of the floating composer; with the panel under it, that
        // clearance has to cover both or the newest message sits behind them.
        binding.threadMessagesList.updatePadding(bottom = threadListBottomPadding + height)
        panel.beVisible()
        isEmojiPanelOpen = true
        // The field keeps the caret -- an emoji is inserted where you were typing -- but the
        // keyboard itself has to go, or the panel would open underneath it.
        hideKeyboard()
    }

    private fun closeEmojiPanel() {
        if (!isEmojiPanelOpen) return
        binding.emojiPanel.beGone()
        binding.emojiPanel.removeAllViews()
        binding.threadMessagesList.updatePadding(bottom = threadListBottomPadding)
        isEmojiPanelOpen = false
    }

    /** The list's own clearance for the floating composer, as the layout declares it. */
    private val threadListBottomPadding by lazy { binding.threadMessagesList.paddingBottom }

    private fun setupExpandingInputBar() {
        val inputBar = binding.messageHolder.textoMessageInputBar
        val inputField = binding.messageHolder.threadTypeMessage
        val isNewUi = config.useNewUi

        inputBar.updateLayoutParams<LinearLayout.LayoutParams> {
            width = 0
            weight = 1f
        }
        inputBar.alpha = if (isNewUi) 0.95f else 1f
        // 3dp, down from 10 plus a 4px translationZ on top of it.
        //
        // The shadow is cast correctly -- it follows the capsule, checked by cropping the
        // corner rather than by reading this -- but at that height it piles up where the two
        // edges meet, so the bar read as having a grey smudge stuck to each bottom corner
        // while its straight edges looked clean. The bar already separates itself from the
        // page with TextoGlass's own hairline rim; the shadow only has to hint that it
        // floats, and at 3dp the corners measure clean.
        inputBar.elevation = if (isNewUi) 3f * resources.displayMetrics.density else 0f
        inputBar.translationZ = 0f

        // The shape the shadow is cast from, set here because this is where the elevation
        // that casts it is, so the two cannot drift apart. clipToOutline is left alone: only
        // the shadow needs the shape.
        // Stickers, GIFs and images handed over by the keyboard land in the same place a
        // picked file does, so one arriving is an MMS attachment like any other -- size
        // limit, preview row, removal and all -- rather than a second path to keep in step.
        inputField.onContentReceived = { uri -> runOnUiThread { addAttachment(uri) } }

        val capsuleRadius = TextoGlass.COMPOSER_RADIUS_DP * resources.displayMetrics.density
        inputBar.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, capsuleRadius)
            }
        }


        val startsFocusable = !isNewUi || config.alwaysExpandSearchBar
        inputField.isFocusable = startsFocusable
        inputField.isFocusableInTouchMode = startsFocusable
        inputField.isEnabled = true

        if (isNewUi && !config.alwaysExpandSearchBar) {
            inputBar.setOnClickListener { if (!inputField.isFocusable) expandInputBar() }
            inputField.setOnClickListener { if (!inputField.isFocusable) expandInputBar() }
            inputField.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus && inputField.text?.isEmpty() == true) shrinkInputBar()
            }
        } else {
            inputBar.setOnClickListener(null)
            inputField.setOnClickListener(null)
            inputField.onFocusChangeListener = null
        }
        inputBar.requestLayout()
    }

    /** Hands the field focus and opens the keyboard. */
    private fun expandInputBar() {
        val inputField = binding.messageHolder.threadTypeMessage
        inputField.isFocusable = true
        inputField.isFocusableInTouchMode = true
        inputField.requestFocus()
        showKeyboard(inputField)
    }

    /** Gives the focus back up so the next tap on the row is what opens the keyboard. */
    private fun shrinkInputBar() {
        if (config.alwaysExpandSearchBar) return
        val inputField = binding.messageHolder.threadTypeMessage
        inputField.isFocusable = false
        inputField.isFocusableInTouchMode = false
        hideKeyboard()
    }

    override fun onResume() {
        super.onResume()
        if (isFinishing || isDestroyed) return
        applyOutlines()
        
        currentThreadId = threadId
        // No navigation icon: the design's back control is the rounded tile at the head of
        // the header row, which setupOptionsMenu wires up.
        setupTextoTopAppBar(
            appBar = binding.threadAppbar,
            navigationIcon = NavigationIcon.None,
            backgroundColor = Color.TRANSPARENT
        )

        isActivityVisible = true

        notificationManager.cancel(threadId.hashCode())

        ensureBackgroundThread {
            val newConv = conversationsDB.getConversationWithThreadId(threadId)
            if (newConv != null) {
                conversation = newConv
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    setupThreadTitle()
                }
            }

            val smsDraft = getSmsDraft(threadId)
            if (smsDraft.isNotEmpty()) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    binding.messageHolder.threadTypeMessage.setText(smsDraft)
                    binding.messageHolder.threadTypeMessage.setSelection(smsDraft.length)
                }
            }

            markThreadMessagesRead(threadId)
        }

        setupScaledToolbar(binding.threadToolbar)

        binding.scrollToBottomFab.updateLayoutParams<androidx.constraintlayout.widget.ConstraintLayout.LayoutParams> {
            marginEnd = 20.getScaledPx()
            bottomMargin = 20.getScaledPx()
        }

        binding.messageHolder.textoMessageInputBar.apply {
            // The capsule now holds the 44dp send disc plus its own 8dp of padding, so it
            // has to clear 60dp rather than the 44 it needed when send sat outside it.
            minimumHeight = 60.getScaledPx()
            val pad = 8.getScaledPx()
            setPadding(pad, pad, pad, pad)
        }

        getOrCreateThreadAdapter().updateScaling()

        val bottomAnim = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom)
        binding.messageHolder.root.startAnimation(bottomAnim)

        applyCustomColors()
        // After applyCustomColors, which would otherwise repaint the header tiles with the
        // generic top-bar tint and undo the design's own weights.
        styleThreadHeader()
        // Also after it: applyCustomColors paints the window from the app's own background,
        // so a filter's own ground has to be laid over the top of that rather than under it.
        applyFilterAppearance()

        if (isFirstResume && config.useNewUi) {
            isFirstResume = false
            // Anchor at the top for stretching effect
            binding.threadAppbar.pivotY = 0f
            binding.threadAppbar.scaleY = 0.4f
            binding.threadAppbar.alpha = 0f
            binding.threadAppbar.animate()
                .scaleY(1f)
                .alpha(1f)
                .setDuration(800)
                .setInterpolator(android.view.animation.OvershootInterpolator(2.2f))
                .start()
        }
    }

    override fun onPause() {
        super.onPause()
        currentThreadId = 0L
        saveDraftMessage()
        isActivityVisible = false
    }

    override fun onStop() {
        super.onStop()
        saveDraftMessage()
        bus?.post(Events.RefreshConversations())
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (resultCode != Activity.RESULT_OK) return
        
        val data = resultData?.data
        val clipData = resultData?.clipData
        messageToResend = null

        try {
            when (requestCode) {
                CAPTURE_PHOTO_INTENT -> {
                    if (capturedImageUri != null) {
                        addAttachment(capturedImageUri!!)
                    }
                }
                CAPTURE_VIDEO_INTENT,
                PICK_DOCUMENT_INTENT,
                CAPTURE_AUDIO_INTENT,
                PICK_AUDIO_INTENT,
                PICK_PHOTO_INTENT,
                PICK_VIDEO_INTENT -> {
                    if (clipData != null) {
                        for (i in 0 until clipData.itemCount) {
                            addAttachment(clipData.getItemAt(i).uri)
                        }
                    } else if (data != null) {
                        addAttachment(data)
                    }
                }

                PICK_CONTACT_INTENT -> data?.let { addContactAttachment(it) }
                PICK_SAVE_FILE_INTENT -> saveAttachments(resultData!!)
                PICK_SAVE_DIR_INTENT -> saveAttachments(resultData!!)
            }
        } catch (e: Exception) {
            showErrorToast(e)
        }
    }

    private fun setupThread(callback: () -> Unit) {
        if (conversation == null && isLaunchedFromShortcut) {
            if (isTaskRoot) {
                Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    startActivity(this)
                }
            }
            finish()
            return
        }
        ensureBackgroundThread {
            val cachedMessagesCode = messages.hashCode()
            if (!isRecycleBin) {
                val rawMessages = getMessages(threadId)
                messages = ArrayList(processReactions(rawMessages))

                if (config.useRecycleBin) {
                    val recycledMessages = try { messagesDB.getThreadMessagesFromRecycleBin(threadId) } catch (e: Exception) { emptyList() }
                    messages = ArrayList(messages.filterNotInByKey(recycledMessages) { it.getStableId() })
                }
            }

            setupParticipants()
            setupAdapter(forceScroll = true)

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                setupThreadTitle()
                setupSIMSelector()
                updateMessageType()
                callback()
            }
        }
    }

    private fun getOrCreateThreadAdapter(): ThreadAdapter {
        var currAdapter = binding.threadMessagesList.adapter
        if (currAdapter == null) {
            currAdapter = ThreadAdapter(
                activity = this,
                recyclerView = binding.threadMessagesList,
                itemClick = { handleItemClick(it) },
                isRecycleBin = isRecycleBin,
                deleteMessages = { messages, toRecycleBin, fromRecycleBin ->
                    deleteMessages(
                        messages,
                        toRecycleBin,
                        fromRecycleBin
                    )
                }
            )

            binding.threadMessagesList.adapter = currAdapter
        }
        return currAdapter as ThreadAdapter
    }

    private fun setupAdapter(forceScroll: Boolean = false) {
        if (isRefreshing) {
            refreshQueued = true
            queuedForceScroll = queuedForceScroll || forceScroll
            return
        }
        isRefreshing = true
        ensureBackgroundThread {
            val items = getThreadItems()
            runOnUiThread {
                if (isFinishing || isDestroyed) {
                    isRefreshing = false
                    return@runOnUiThread
                }
                threadItems = items
                refreshMenuItems()
                val forceScrollOnOpen = isFromNotification
                isFromNotification = false
                // Sampled before the new items land, and that ordering is the whole point:
                // once an appended message is in the list the view can always scroll further,
                // so asking afterwards answers "not at the bottom" for every message that
                // arrives -- and the list sits still while the new bubble waits out of sight
                // below the fold.
                val wasAtBottom = !binding.threadMessagesList.canScrollVertically(1)
                getOrCreateThreadAdapter().apply {
                    updateMessages(visibleThreadItems()) {
                        isRefreshing = false
                        if (isFinishing || isDestroyed) return@updateMessages
                        // Not while searching: the results are a list you read from the top,
                        // and snapping to its end every time one arrives fights that.
                        if (threadSearchQuery.isEmpty()) {
                            val justSent = scrollOnNextUpdate
                            if (justSent) scrollOnNextUpdate = false
                            scrollToBottom(
                                forceScroll || forceScrollOnOpen || wasAtBottom || justSent
                            )
                        }
                        if (refreshQueued) {
                            refreshQueued = false
                            val queued = queuedForceScroll
                            queuedForceScroll = false
                            setupAdapter(queued)
                        }
                    }
                }
            }
        }

        SimpleContactsHelper(this).getAvailableContacts(false) { contacts ->
            if (isFinishing || isDestroyed) return@getAvailableContacts
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                val adapter = AutoCompleteTextViewAdapter(this, contacts)
                binding.addContactOrNumber.setAdapter(adapter)
                binding.addContactOrNumber.imeOptions = EditorInfo.IME_ACTION_NEXT
                binding.addContactOrNumber.setOnItemClickListener { _, _, position, _ ->
                    val currContacts = (binding.addContactOrNumber.adapter as AutoCompleteTextViewAdapter).resultList
                    val contact = currContacts.getOrNull(position) ?: return@setOnItemClickListener
                    val contactId = contact.contactId
                    if (participants.any { it.contactId == contactId }) {
                        return@setOnItemClickListener
                    }
                    addParticipant(contact)
                    binding.addContactOrNumber.setText("")
                }
            }
        }
    }

    private fun addParticipant(contact: SimpleContact) {
        participants.add(contact)
        updateParticipants()
    }

    private fun updateParticipants() {
        participants = participants.distinctBy { it.contactId }.toArrayList()
        showSelectedContacts()
        setupAdapter()
        updateMessageType()
        setupThreadTitle()
        checkSendMessageAvailability()
    }

    private fun setupScrollListener() {
        binding.threadMessagesList.onScroll(
            onScrolled = { _, _ ->
                tryLoadMoreMessages()
                val layoutManager = binding.threadMessagesList.layoutManager as LinearLayoutManager
                val lastVisibleItemPosition = layoutManager.findLastCompletelyVisibleItemPosition()
                val isCloseToBottom =
                    lastVisibleItemPosition >= getOrCreateThreadAdapter().itemCount - SCROLL_TO_BOTTOM_FAB_LIMIT
                val fab = binding.scrollToBottomFab
                if (isCloseToBottom) fab.beGone() else fab.beVisible()
            },
            onScrollStateChanged = { newState ->
                if (newState == RecyclerView.SCROLL_STATE_IDLE) tryLoadMoreMessages()
            }
        )
        
        // Solid Snap on Layout Change (e.g. Keyboard pop-up)
        binding.threadMessagesList.addOnLayoutChangeListener { _, _, _, _, bottom, _, _, _, oldBottom ->
            if (bottom < oldBottom) {
                // Keyboard opened or size decreased, force snap to bottom
                scrollToBottom(forceScroll = true)
            }
        }
    }

    private fun handleItemClick(any: Any) {
        when (any) {
            is Message -> {
                if (any.isScheduled) {
                    // Show scheduled info
                } else if (any.hasFailedToSend()) {
                    // The bubble itself, not only the caption underneath it: the caption is
                    // a thin line of text and the message above it is what the eye and the
                    // thumb both go to.
                    askToResend(any.id, any.body, any.isMMS)
                } else if (any.attachment?.attachments?.isNotEmpty() == true) {
                    val firstAttachment = any.attachment.attachments.first()
                    val mimetype = firstAttachment.mimetype
                    if (mimetype.isImageMimeType() || mimetype.isVideoMimeType()) {
                        launchViewIntent(firstAttachment.getUri(), mimetype, firstAttachment.filename)
                    }
                }
            }
            is ThreadError -> askToResend(any.messageId, any.messageText, any.isMMS)
        }
    }

    private fun tryLoadMoreMessages() {
        if (isJumpingToMessage) return
        val layoutManager = binding.threadMessagesList.layoutManager as LinearLayoutManager
        if (layoutManager.findFirstVisibleItemPosition() <= PREFETCH_THRESHOLD) {
            loadMoreMessages()
        }
    }

    private fun loadMoreMessages() {
        // A filtered thread is short, so it sits at the top of the list from the moment the
        // first character is typed -- which fired this, which re-submitted the *unfiltered*
        // items and wiped the results before they could be read. Paging waits for the search
        // to close.
        if (threadSearchQuery.isNotEmpty()) return
        if (messages.isEmpty() || allMessagesFetched || loadingOlderMessages) return
        loadingOlderMessages = true
        val cutoff = messages.first().date
        ensureBackgroundThread {
            fetchOlderMessages(cutoff)
            threadItems = getThreadItems()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                loadingOlderMessages = false
                getOrCreateThreadAdapter().updateMessages(visibleThreadItems())
            }
        }
    }

    private fun fetchOlderMessages(cutoff: Int): List<Message> {
        val older = getMessages(threadId, cutoff)
            .filterNotInByKey(messages) { it.getStableId() }

        if (older.isEmpty()) {
            allMessagesFetched = true
            return emptyList()
        }

        messages.addAll(0, older)
        messages.sortBy { it.date }
        return older
    }

    private fun loadConversation() {
        handlePermission(PERMISSION_READ_PHONE_STATE) { granted ->
            if (granted) {
                setupButtons()
                ensureBackgroundThread {
                    conversation = conversationsDB.getConversationWithThreadId(threadId)
                    setupThread {
                        val searchedMessageId = intent.getLongExtra(SEARCHED_MESSAGE_ID, -1L)
                        intent.removeExtra(SEARCHED_MESSAGE_ID)
                        if (searchedMessageId != -1L) {
                            jumpToMessage(searchedMessageId)
                        }
                    }
                    runOnUiThread {
                        setupScrollListener()
                    }
                }
            } else {
                finish()
            }
        }
    }

    private fun setupButtons() = binding.apply {
        val inputBarColor = config.inputBarTextColor
        val mainTextColor = config.mainTextColor

        binding.messageHolder.apply {
            val density = resources.displayMetrics.density

            // The design's send control is a full accent disc (`border-radius: 999px`, not a
            // squircle) under a glow, painted through TextoGlass so it is the same accent
            // surface as the unread badges, the active filter chip and the sent bubbles.
            val sendSide = 40.getScaledPx()
            threadSendMessage.updateLayoutParams<LinearLayout.LayoutParams> {
                width = sendSide
                height = sendSide
                marginStart = 4.getScaledPx()
                marginEnd = 0
            }
            threadSendMessage.imageTintList =
                android.content.res.ColorStateList.valueOf(config.accentInkColor)
            threadSendMessage.background = com.texto.sms.helpers.TextoGlass.accent(
                start = config.accentGradientStart,
                end = config.accentGradientEnd,
                cornerRadius = sendSide / 2f,
                mid = config.accentGradientMid
            )
            threadSendMessage.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            threadSendMessage.elevation = 6 * density

            // No fill and no hairline on the row: everything the composer draws belongs to
            // the capsule inside it, so anything painted here would read as a second slab.
            textoMessageBarRow.background = null
            textoMessageBarRow.setPadding(
                10.getScaledPx(), 6.getScaledPx(), 10.getScaledPx(), 14.getScaledPx()
            )

            confirmManageContacts.applyColorFilter(mainTextColor)

            // The clip and the SIM are 38dp discs filled with `--inset` behind the shared
            // `--divider` hairline, with a `--muted` glyph -- the design's own small-control
            // recipe, which the header's action tiles use too.
            styleComposerDisc(threadAddAttachment, inputBarColor)
            threadAddAttachment.alpha = 1.0f

            styleComposerDisc(threadAddEmoji, inputBarColor)
            threadAddEmoji.alpha = 1.0f
            // The app's own sheet, because Android offers no way into the keyboard's emoji
            // panel: there is no intent for it, and KEYCODE_PICTSYMBOLS -- the one key that
            // is supposed to mean this -- was tried on the device and ignored. Messages,
            // WhatsApp and Telegram all draw their own for the same reason. Stickers and
            // GIFs still come from the keyboard, through the composer field's accepted
            // content types; this covers what that route cannot.
            threadAddEmoji.setOnClickListener { toggleEmojiPanel() }

            // threadMessagesFastscroller removed

            threadCharacterCounter.beGone()
            threadCharacterCounter.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())

            threadTypeMessage.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())
            threadSendMessage.setOnClickListener {
                sendMessage()
            }

            threadSendMessage.setOnLongClickListener {
                if (!isScheduledMessage) {
                    launchScheduleSendDialog()
                }
                true
            }

            threadSendMessage.isClickable = false
            threadTypeMessage.onTextChangeListener {
                messageToResend = null
                checkSendMessageAvailability()
                val messageString = if (config.useSimpleCharacters) it.normalizeString() else it
                val messageLength = SmsMessage.calculateLength(messageString, false)
                @SuppressLint("SetTextI18n")
                threadCharacterCounter.text = "${messageLength[2]}/${messageLength[0]}"
            }

            if (config.sendOnEnter) {
                threadTypeMessage.inputType = EditorInfo.TYPE_TEXT_FLAG_CAP_SENTENCES
                threadTypeMessage.imeOptions = EditorInfo.IME_ACTION_SEND
                threadTypeMessage.setOnEditorActionListener { _, action, _ ->
                    if (action == EditorInfo.IME_ACTION_SEND) {
                        dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
                        return@setOnEditorActionListener true
                    }
                    false
                }

                threadTypeMessage.setOnKeyListener { _, keyCode, event ->
                    if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                        sendMessage()
                        return@setOnKeyListener true
                    }
                    false
                }
            }

            confirmManageContacts.setOnClickListener {
                hideKeyboard()
                threadAddContacts.beGone()

                val numbers = HashSet<String>()
                participants.forEach { contact ->
                    contact.phoneNumbers.forEach {
                        numbers.add(it.normalizedNumber)
                    }
                }

                val newThreadId = getThreadId(numbers)
                if (threadId != newThreadId) {
                    hideKeyboard()
                    Intent(this@ThreadActivity, ThreadActivity::class.java).apply {
                        putExtra(THREAD_ID, newThreadId)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        startActivity(this)
                    }
                }
            }

            threadTypeMessage.setText(intent.getStringExtra(THREAD_TEXT))
            threadAddAttachment.setOnClickListener {
                showAttachmentPickerDialog()
            }

            if (intent.extras?.containsKey(THREAD_ATTACHMENT_URI) == true) {
                val uri = intent.getStringExtra(THREAD_ATTACHMENT_URI)!!.toUri()
                addAttachment(uri)
            } else if (intent.extras?.containsKey(THREAD_ATTACHMENT_URIS) == true) {
                val uris = intent.getParcelableArrayListExtra<Uri>(THREAD_ATTACHMENT_URIS)
                uris?.forEach { addAttachment(it) }
            }
            scrollToBottomFab.setOnClickListener {
                scrollToBottom(isManual = true)
            }
            scrollToBottomFab.backgroundTintList = ColorStateList.valueOf(getBottomBarColor())
        }
        setupScheduleSendUi()
        binding
    }

    private fun showAttachmentPickerDialog() {
        AttachmentPickerDialog { id ->
            when (id) {
                R.id.picker_image -> launchGetContentIntent(arrayOf("image/*"), PICK_PHOTO_INTENT)
                R.id.picker_video -> launchGetContentIntent(arrayOf("video/*"), PICK_VIDEO_INTENT)
                R.id.picker_camera -> launchCapturePhotoIntent()
                R.id.picker_camera_video -> launchCaptureVideoIntent()
                R.id.picker_audio -> launchCaptureAudioIntent()
                R.id.picker_file -> launchGetContentIntent(arrayOf("*/*"), PICK_DOCUMENT_INTENT)
                R.id.picker_contact -> launchPickContactIntent()
                R.id.picker_schedule -> {
                    if (isScheduledMessage) {
                        launchScheduleSendDialog(scheduledDateTime)
                    } else {
                        launchScheduleSendDialog()
                    }
                }
            }
        }.show(supportFragmentManager, AttachmentPickerDialog.TAG)
    }

    /**
     * Persists the message as scheduled and sets the alarm that will send it. The send
     * button previously fell straight through to the radio, so a scheduled message went
     * out immediately no matter what time had been picked.
     */
    private fun storeScheduledMessage(text: String, hasAttachments: Boolean) {
        val sendAt = scheduledDateTime
        if (!sendAt.isAfterNow) {
            toast(R.string.must_pick_time_in_the_future)
            return
        }

        clearCurrentMessage()
        // Re-scheduling an existing draft keeps its id so the old alarm is replaced.
        val existingId = messageToResend
        messageToResend = null

        ensureBackgroundThread {
            try {
                val messageId = existingId ?: generateRandomId()
                val message = Message(
                    id = messageId,
                    body = text,
                    type = Telephony.Sms.MESSAGE_TYPE_QUEUED,
                    status = Telephony.Sms.STATUS_PENDING,
                    participants = participants,
                    date = (sendAt.millis / 1000L).toInt(),
                    read = true,
                    threadId = threadId,
                    isMMS = hasAttachments,
                    attachment = null,
                    senderPhoneNumber = "",
                    senderName = "",
                    senderPhotoUri = "",
                    subscriptionId = currentSIMCardIndex,
                    isScheduled = true
                )

                messagesDB.insertOrUpdate(message)
                scheduleMessage(message)

                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    isScheduledMessage = false
                    checkSendMessageAvailability()
                    toast(R.string.message_scheduled)
                }
                refreshMessages()
                refreshConversations()
            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    showErrorToast(e)
                }
            }
        }
    }

    private fun askForExactAlarmPermissionIfNeeded(callback: () -> Unit = {}) {
        if (isSPlus()) {
            val alarmManager: AlarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
            if (alarmManager.canScheduleExactAlarms()) {
                callback()
            } else {
                // The app's own sheet. Commons' PermissionRequiredDialog builds itself from
                // the base theme and its own strings, so on the way to scheduling a message
                // the user met a white card of English text -- the one dialog in the whole
                // flow that neither the skin nor the locale reached.
                textoConfirmDialog(
                    message = getString(R.string.allow_alarm_scheduled_messages),
                    title = getString(R.string.permission_required),
                    positiveLabel = getString(R.string.grant_permission)
                ) {
                    openRequestExactAlarmSettings(BuildConfig.APPLICATION_ID)
                }
            }
        } else {
            callback()
        }
    }

    private fun launchScheduleSendDialog(dateTime: DateTime = DateTime.now().plusMinutes(10)) {
        askForExactAlarmPermissionIfNeeded {
            ScheduleMessageDialog(this, dateTime) {
                if (it != null) {
                    scheduledDateTime = it
                    isScheduledMessage = true
                    updateMessageType()
                    checkSendMessageAvailability()
                }
            }
        }
    }

    private fun setupScheduleSendUi() {
        binding.messageHolder.scheduledMessageButton.setOnClickListener {
            launchScheduleSendDialog(scheduledDateTime)
        }

        binding.messageHolder.discardScheduledMessage.setOnClickListener {
            isScheduledMessage = false
            updateMessageType()
            checkSendMessageAvailability()
        }
    }

    private fun clearCurrentMessage() {
        binding.messageHolder.threadTypeMessage.setText("")
        getAttachmentsAdapter()?.clear()
        checkSendMessageAvailability()
    }

    /**
     * The call and overflow controls are real views in the header row now, not toolbar menu
     * items, so the design's rounded tiles can sit behind them. The menu itself is still
     * inflated -- [refreshMenuItems] and [showThreadModernMenu] read it for the item list --
     * it just no longer draws anything.
     */
    private fun setupOptionsMenu() {
        binding.threadMenuBtn.setOnClickListener { showThreadModernMenu(it) }
        binding.threadBackBtn.setOnClickListener { finish() }
        styleThreadHeader()
    }

    /**
     * The header's three tiles and the line under the name, painted from the live theme. The
     * design gives the back tile a 13dp radius and the two action tiles 12dp, all of them
     * filled with the card colour at half strength.
     */
    private fun styleThreadHeader() {
        val fill = config.mainBackgroundColor.withAlpha(0.55f)
        val rim = com.texto.sms.helpers.TextoGlass.rimFor(config.recentColor, 0.22f)
        val glyph = config.topBarTextColor.withAlpha(0.68f)

        // Every header control is a full disc in the design (`border-radius: 999px`) filled
        // with `--inset` behind the `--divider` hairline -- the same recipe the composer's
        // clip and SIM discs use, which is what makes the two bars read as one material.
        fun tile(view: android.widget.ImageView, sizeDp: Int) {
            val side = sizeDp.getScaledPx()
            view.updateLayoutParams {
                width = side
                height = side
            }
            view.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(fill)
                setStroke(1.getScaledPx(), rim)
            }
            view.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            view.imageTintList = android.content.res.ColorStateList.valueOf(glyph)
        }

        tile(binding.threadBackBtn, 40)
        tile(binding.threadSearchBtn, 38)
        tile(binding.threadMenuBtn, 38)

        // The design's header avatar is 42dp on a 16dp radius, and carries the accent
        // gradient rather than the neutral tile fill the actions use.
        binding.threadHeaderAvatar.updateLayoutParams {
            width = 42.getScaledPx()
            height = 42.getScaledPx()
        }
        com.texto.sms.helpers.TextoAvatars.clipToSquircle(binding.threadHeaderAvatar)

        binding.threadToolbarTitle.setTextColor(config.topBarTextColor)
        binding.threadToolbarTitle.setTextSize(
            TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.95f)
        )
        // The design paints this one line in the accent hue -- the only coloured text in the
        // header, which is what makes it read as status rather than a second title.
        binding.threadHeaderStatus.setTextColor(config.auroraAccentColor)
        binding.threadHeaderStatus.setTextSize(
            TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.88f)
        )
    }

    /**
     * The overflow, in the order the four most-used rows are reached for: call, copy the
     * number, mark read, archive. Everything else follows in a second group, with delete
     * last because it is the one row that cannot be undone.
     *
     * The list is built top-down on purpose -- an `add` further down is a row further down --
     * so the order on screen is the order of this function.
     */
    private fun showThreadModernMenu(anchor: android.view.View) {
        val items = mutableListOf<Pair<Int, String>>()
        val firstPhoneNumber = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value
        val archiveAvailable = config.isArchiveAvailable

        // The call tile used to sit in the header, where it took 44dp from a name column
        // that only had 127dp to begin with. The handler for it was always here.
        if (canDialCurrentParticipant()) {
            items.add(R.id.dial_number to getString(R.string.dial_number))
        }

        if (participants.size == 1 && !isRecycleBin && !firstPhoneNumber.isNullOrEmpty()) {
            items.add(R.id.copy_number to getString(R.string.copy_number_to_clipboard))
        }

        if (threadItems.isNotEmpty() && !isRecycleBin) {
            items.add(R.id.mark_as_read to getString(R.string.mark_as_read))
        }

        if (threadItems.isNotEmpty() && archiveAvailable && !isRecycleBin) {
            if (conversation?.isArchived == false) {
                items.add(R.id.archive to getString(R.string.archive))
            } else if (conversation?.isArchived == true) {
                items.add(R.id.unarchive to getString(R.string.unarchive))
            }
        }

        // Saving to contacts is one row now, not two. "Save this number" and "save all the
        // numbers" ran the same intent and differed only in how many numbers they handed
        // over, so this hands over every number in the thread that has no card yet -- one in
        // a private chat, all of them in a group -- and only while somebody is still unsaved.
        if (canAddToContacts()) {
            items.add(R.id.add_number_to_contact to getString(R.string.add_number_to_contact))
        }

        if (conversation != null && !isRecycleBin) {
            items.add(R.id.conversation_details to getString(R.string.conversation_details))
            items.add(R.id.rename_conversation to getString(R.string.rename_conversation))
        }

        if (!isRecycleBin) {
            items.add(R.id.block_number to getString(R.string.block_number))
        }

        if (isRecycleBin && threadItems.isNotEmpty()) {
            items.add(R.id.restore to getString(R.string.restore))
        }

        // Last, and painted in the destructive colour by showModernMenu. It was the first
        // row: the one irreversible action in the list, sitting where the thumb lands.
        if (threadItems.isNotEmpty()) {
            items.add(R.id.delete to getString(R.string.delete))
        }

        // The same Phosphor set the settings rows are drawn from, so the two lists read as
        // one family rather than two.
        val icons = mapOf(
            R.id.dial_number to R.drawable.ic_ph_phone,
            R.id.copy_number to R.drawable.ic_copy_vector,
            R.id.mark_as_read to R.drawable.ic_ph_checks,
            R.id.archive to R.drawable.ic_ph_archive_box,
            R.id.unarchive to R.drawable.ic_ph_arrow_u_up_left,
            R.id.add_number_to_contact to R.drawable.ic_ph_user_plus,
            R.id.conversation_details to R.drawable.ic_ph_info,
            R.id.rename_conversation to R.drawable.ic_ph_text_aa,
            R.id.block_number to R.drawable.ic_ph_prohibit,
            R.id.restore to R.drawable.ic_ph_arrow_u_up_left,
            R.id.delete to R.drawable.ic_ph_trash,
        )

        showModernMenu(anchor, items, icons) { itemId ->
            when (itemId) {
                R.id.dial_number -> dialNumber()
                R.id.archive -> archiveThread()
                R.id.unarchive -> unarchiveThread()
                R.id.add_number_to_contact -> addNumberToContact()
                R.id.copy_number -> copyNumberToClipboard()
                R.id.rename_conversation -> renameConversation()
                R.id.conversation_details -> launchConversationDetails(threadId)
                R.id.mark_as_read -> markAsRead()
                R.id.block_number -> tryBlocking()
                R.id.delete -> askConfirmDelete()
                R.id.restore -> restoreMessages()
            }
        }
    }

    /**
     * Numbers in this thread that have no contact card yet. A participant with no card keeps
     * its number as its display name, which is how the list is read off without a second
     * contacts query on every menu open.
     */
    private fun unsavedParticipantNumbers(): List<String> = participants
        .filter { participant ->
            val number = participant.phoneNumbers.firstOrNull()?.value
            number != null && participant.name == number
        }
        .mapNotNull { it.phoneNumbers.firstOrNull()?.normalizedNumber?.ifBlank { null } }
        .distinct()

    /** Whether the "save to contacts" row has anything to save. */
    private fun canAddToContacts(): Boolean =
        !isRecycleBin && !isSpecialNumber() && unsavedParticipantNumbers().isNotEmpty()

    /**
     * The title view spans the whole toolbar, so it has to be inset by exactly as much room
     * as the action icons on the right actually occupy. A flat 100.getScaledPx() multiplied
     * that inset by the app's UI scale, while Toolbar lays its action items out at a fixed
     * 48dp -- leaving a gap between the name and the call icon on larger scales and stealing
     * width the name needed. Measured from the icons that are really showing instead, so the
     * name starts right beside the call icon and runs as far left as it needs to.
     */
    private fun refreshMenuItems() {
        val firstPhoneNumber = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value
        val archiveAvailable = config.isArchiveAvailable
        binding.threadToolbar.menu.apply {
            findItem(R.id.delete)?.isVisible = threadItems.isNotEmpty()
            findItem(R.id.restore)?.isVisible = threadItems.isNotEmpty() && isRecycleBin
            findItem(R.id.archive)?.isVisible =
                threadItems.isNotEmpty() && conversation?.isArchived == false && !isRecycleBin && archiveAvailable
            findItem(R.id.unarchive)?.isVisible =
                threadItems.isNotEmpty() && conversation?.isArchived == true && !isRecycleBin && archiveAvailable
            findItem(R.id.conversation_details)?.isVisible = conversation != null && !isRecycleBin
            findItem(R.id.block_number)?.title = getString(R.string.block_number)
            findItem(R.id.block_number)?.isVisible = !isRecycleBin
            findItem(R.id.dial_number)?.isVisible = canDialCurrentParticipant()
            findItem(R.id.mark_as_read)?.isVisible = threadItems.isNotEmpty() && !isRecycleBin

            findItem(R.id.add_number_to_contact)?.isVisible = canAddToContacts()
            findItem(R.id.copy_number)?.isVisible =
                participants.size == 1 && !firstPhoneNumber.isNullOrEmpty() && !isRecycleBin
        }
    }

    private fun checkSendMessageAvailability() {
        val text = binding.messageHolder.threadTypeMessage.text.toString().trim()
        val hasText = text.isNotEmpty()
        val hasAttachments = getAttachmentSelections().isNotEmpty()
        val isAttachmentPending = getAttachmentSelections().any { it.isPending }
        val isSendable = (hasText || hasAttachments) && !isAttachmentPending && participants.isNotEmpty()

        binding.messageHolder.threadSendMessage.apply {
            alpha = if (isSendable) 1.0f else 0.4f
            isClickable = isSendable
        }

        binding.messageHolder.scheduledMessageHolder.beVisibleIf(isScheduledMessage)
        if (isScheduledMessage) {
            // A bare date told you nothing about what it meant. Spell out that the
            // message is queued, and say "today"/"tomorrow" when that is clearer.
            val now = org.joda.time.DateTime.now()
            val dayLabel = when (scheduledDateTime.toLocalDate()) {
                now.toLocalDate() -> getString(R.string.today)
                now.plusDays(1).toLocalDate() -> getString(R.string.tomorrow)
                else -> scheduledDateTime.toString(config.dateFormat)
            }
            val time = scheduledDateTime.toString(getTimeFormat())
            binding.messageHolder.scheduledMessageButton.text =
                getString(R.string.will_send_at, dayLabel, time)
        }
    }

    private fun setupParticipants() {
        ensureBackgroundThread {
            participants = getThreadParticipants(threadId, null)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                showSelectedContacts()
                setupThreadTitle()
                checkSendMessageAvailability()
                refreshMenuItems()
                refreshMessages()
                // The participants are what decide which filter this thread belongs to, so
                // this is the first moment the answer is known.
                applyFilterAppearance()
            }
        }
    }

    /**
     * A filter's own colours, for the threads that filter covers.
     *
     * Only a one-to-one thread takes them: a filter covers senders, and a group thread has
     * several, so "which filter is this" has no single answer there and the app's own colours
     * stay. The adapter repaints its bubbles from [ThreadAdapter.filterOverride]; the ground
     * behind them is the window's, which is painted here because applyCustomColors -- shared
     * by every screen -- knows nothing about filters.
     */
    private fun applyFilterAppearance() {
        val number = participants.singleOrNull()?.phoneNumbers?.firstOrNull()?.value
        val override = number?.let {
            FilterStore.customisedFilterFor(config.customFilters, it) { filter ->
                filter.hasAppearanceOverride
            }
        }
        getOrCreateThreadAdapter().filterOverride = override
        override?.backgroundColor?.let { window.decorView.setBackgroundColor(it) }
    }

    private fun showSelectedContacts() {
        binding.selectedContacts.removeAllViews()
        participants.forEach { contact ->
            val contactBinding = ItemSelectedContactBinding.inflate(layoutInflater, binding.selectedContacts, false)
            // The row ships with a fixed near-white name, which is invisible on the light
            // skins. Painted at inflation rather than left to the resume sweep, so a row
            // added between two resumes is readable straight away.
            contactBinding.selectedContactName.setTextColor(config.mainTextColor)
            contactBinding.selectedContactRemove.applyColorFilter(
                config.mainTextColor.withAlpha(0.6f)
            )
            contactBinding.selectedContactName.text = contact.name.asLtrPhone()
            contactBinding.selectedContactRemove.setOnClickListener {
                participants.remove(contact)
                updateParticipants()
            }
            binding.selectedContacts.addView(contactBinding.root)
        }

        // The two hairlines around the recipient field carried commons' light-theme grey,
        // which reads as a bright line on the dark skins and as nothing on the light ones.
        val rim = TextoGlass.rimFor(config.recentColor, 0.30f)
        binding.messageDividerOne.setBackgroundColor(rim)
        binding.messageDividerTwo.setBackgroundColor(rim)
        binding.addContactOrNumber.setTextColor(config.mainTextColor)
        binding.addContactOrNumber.setHintTextColor(config.mainTextColor.withAlpha(0.5f))
        binding.confirmManageContacts.applyColorFilter(config.mainTextColor)
        binding.confirmInsertedNumber.applyColorFilter(config.mainTextColor)

        binding.threadAddContacts.beVisibleIf(participants.size > 1 || conversation == null)
    }

    private fun setupThreadTitle() {
        val title = conversation?.title
        val finalTitle = if (!title.isNullOrEmpty()) title else participants.getThreadTitle()

        binding.threadToolbar.title = null
        // A thread with no contact behind it is titled with the number itself, which needs
        // the same isolate the number rows get : see String.asLtrPhone.
        binding.threadToolbarTitle.text = finalTitle.asLtrPhone()

        // Tapping the name reveals the numbers behind it, so a call or a copy is one
        // step away even when the thread is titled with a contact name.
        binding.threadToolbarTitle.setOnClickListener { showParticipantNumbers() }

        // One-to-one threads carry nothing under the name. The title above is already
        // either the contact's name or -- when there is no name to show -- the number
        // itself, so a number on the second line was repeating the same person twice.
        // Groups keep their head count, which is the one thing the title cannot say.
        val status = if (participants.size > 1) {
            resources.getQuantityString(
                R.plurals.thread_members, participants.size, participants.size
            )
        } else {
            ""
        }
        binding.threadHeaderStatus.text = status
        binding.threadHeaderStatus.beVisibleIf(status.isNotEmpty())

        binding.threadHeaderAvatar.beVisible()
        val placeholder = com.texto.sms.helpers.TextoAvatars.letterAvatar(this, finalTitle)
        TextoAvatars.loadInto(this, binding.threadHeaderAvatar, conversation?.photoUri.orEmpty(), placeholder)
    }

    private fun showParticipantNumbers() {
        val numbers = participants.flatMap { participant ->
            participant.phoneNumbers.map { it.normalizedNumber }
        }.filter { it.isNotBlank() }.distinct()

        if (numbers.isEmpty()) {
            toast(R.string.unknown_error_occurred)
            return
        }

        // The app's own sheet, like every other list of choices here. This was a bare
        // AlertDialog.Builder, which took its ground, its typeface and its buttons from the
        // base theme and so arrived looking like a dialog from a different app : and its two
        // buttons were commons' strings, which reach a Persian-only screen in English.
        //
        // No cancel row: the capsule sheets dismiss on a tap outside or Back, and adding one
        // would be the only such button in the app.
        val choices = numbers.map { number ->
            val copies = isShortCodeWithLetters(number)
            CapsuleChoice(
                // Isolated so the leading "+" stays leading; see String.asLtrPhone.
                label = number.asLtrPhone(),
                subtitle = getString(
                    if (copies) R.string.copy_number_to_clipboard else R.string.dial_number
                ),
                icon = if (copies) R.drawable.ic_copy_vector else R.drawable.ic_ph_phone,
                onPick = { if (copies) copyToClipboard(number) else dialNumber(number) }
            )
        } + CapsuleChoice(
            label = getString(R.string.copy_to_clipboard),
            icon = R.drawable.ic_copy_vector,
            onPick = { copyToClipboard(numbers.joinToString(", ")) }
        )

        textoCapsuleDialog(binding.threadToolbarTitle.text.toString(), choices)
    }

    private fun isSpecialNumber(): Boolean {
        val normalizedNumber = participants.singleOrNull()?.phoneNumbers?.firstOrNull()?.normalizedNumber ?: return false
        return isShortCodeWithLetters(normalizedNumber)
    }

    // The call icon should only show for a single, real participant with an actual number to
    // dial. Checked against the same field dialNumber() hands to the dialer, so the icon can
    // never appear for something that would not place a call.
    private fun canDialCurrentParticipant(): Boolean {
        if (isRecycleBin) return false
        val phoneNumber = participants.singleOrNull()?.phoneNumbers?.firstOrNull()?.value
        if (phoneNumber.isNullOrBlank() || phoneNumber.none { it.isDigit() }) return false
        return !isSpecialNumber()
    }

    private fun jumpToMessage(messageId: Long) {
        ensureBackgroundThread {
            val messageIndex = messages.indexOfFirstOrNull { it.id == messageId }
            if (messageIndex != null) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    getOrCreateThreadAdapter().updateMessages(getThreadItems()) {
                        if (isFinishing || isDestroyed) return@updateMessages
                        binding.threadMessagesList.scrollToPosition(messageIndex)
                    }
                }
            }
        }
    }

    private fun deleteMessages(messages: List<Message>, toRecycleBin: Boolean, fromRecycleBin: Boolean) {
        ensureBackgroundThread {
            if (fromRecycleBin) {
                messages.forEach { restoreMessageFromRecycleBin(it.id) }
            } else if (toRecycleBin) {
                messages.forEach { moveMessageToRecycleBin(it.id) }
                enforceRecycleBinThreadLimit()
            } else {
                messages.forEach { deleteMessage(it.id, it.isMMS) }
                // A thread with nothing left in it goes now rather than at the next full
                // sync. pruneVanishedConversations already removes it, but that only runs
                // when the conversation list next asks the provider for everything, so
                // emptying a chat left its name on the list for the twenty seconds or so
                // that took -- long enough to read as "it did not work".
                dropConversationIfEmptied()
            }
            refreshMessages()
        }
    }

    /**
     * Removes this conversation once its last message is gone.
     *
     * Checked against the provider rather than the adapter: the recycle bin and scheduled
     * sends live in Room under the same thread id and both should keep the thread alive, and
     * `getNonRecycledThreadMessages` is what the list itself counts.
     */
    private fun dropConversationIfEmptied() {
        try {
            val localLeft = messagesDB.countThreadMessages(threadId)
            if (localLeft > 0) return
            if (getMessages(threadId, limit = 1).isNotEmpty()) return
            deleteConversation(threadId)
            refreshConversations()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveSmsDraftInternal(text: String, threadId: Long) {
        ensureBackgroundThread {
            saveSmsDraft(text, threadId)
        }
    }

    private fun saveDraftMessage() {
        val text = binding.messageHolder.threadTypeMessage.text.toString()
        saveSmsDraftInternal(text, threadId)
    }

    private fun getAttachmentSelections() = getAttachmentsAdapter()?.attachments ?: emptyList()

    private fun addAttachment(uri: Uri) {
        val id = uri.toString()
        if (getAttachmentSelections().any { it.id == id }) return

        try {
            contentResolver.takePersistableUriPermission(uri, FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {}

        var mimeType = contentResolver.getType(uri)
        if (mimeType == null) {
            val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
            if (extension.isNotEmpty()) {
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            }
        }

        if (mimeType == null) mimeType = "image/jpeg"

        val isImage = mimeType.isImageMimeType()
        val isGif = mimeType.isGifMimeType()
        val isRawImage = mimeType.contains("dng", true) || mimeType.contains("raw", true)
        val isVideo = mimeType.isVideoMimeType()

        val fileSize = getFileSizeFromUri(uri)
        val mmsFileSizeLimit = config.mmsFileSizeLimit
        
        if (mmsFileSizeLimit != FILE_SIZE_NONE && fileSize > mmsFileSizeLimit && (!isImage || isGif || isRawImage) && !isVideo) {
            toast(R.string.attachment_sized_exceeds_max_limit, Toast.LENGTH_LONG)
            return
        }

        var adapter = getAttachmentsAdapter()
        if (adapter == null) {
            adapter = AttachmentsAdapter(
                activity = this,
                recyclerView = binding.messageHolder.threadAttachmentsRecyclerview,
                onAttachmentsRemoved = {
                    if (getAttachmentSelections().isEmpty()) {
                        binding.messageHolder.threadAttachmentsRecyclerview.beGone()
                    }
                    checkSendMessageAvailability()
                },
                onReady = { 
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        checkSendMessageAvailability() 
                    }
                }
            )
            binding.messageHolder.threadAttachmentsRecyclerview.adapter = adapter
        }

        binding.messageHolder.threadAttachmentsRecyclerview.beVisible()
        val attachment = AttachmentSelection(
            id = id,
            uri = uri,
            mimetype = mimeType,
            filename = getFilenameFromUri(uri),
            isPending = ((isImage && !isGif && !isRawImage) || isVideo) && (mmsFileSizeLimit == FILE_SIZE_NONE || fileSize > mmsFileSizeLimit)
        )
        adapter.addAttachment(attachment)
        
        if (!attachment.isPending) {
            checkSendMessageAvailability()
        }
    }

    fun saveMMS(attachments: List<Attachment>) {
        pendingAttachmentsToSave = attachments
        if (attachments.size == 1) {
            val attachment = attachments.first()
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = attachment.mimetype
                putExtra(Intent.EXTRA_TITLE, attachment.filename)
            }
            startActivityForResult(intent, PICK_SAVE_FILE_INTENT)
        } else {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
            startActivityForResult(intent, PICK_SAVE_DIR_INTENT)
        }
    }

    private fun saveAttachments(resultData: Intent) {
        val destinationUri = resultData.data ?: return
        try {
            applicationContext.contentResolver.takePersistableUriPermission(
                destinationUri, FLAG_GRANT_READ_URI_PERMISSION or FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {}
        
        ensureBackgroundThread {
            try {
                if (DocumentsContract.isTreeUri(destinationUri)) {
                    val outputDir = DocumentFile.fromTreeUri(this, destinationUri) ?: return@ensureBackgroundThread
                    pendingAttachmentsToSave?.forEach { attachment ->
                        val documentFile = outputDir.createFile(
                            attachment.mimetype,
                            attachment.filename.takeIf { it.isNotBlank() } ?: attachment.uriString.getFilenameFromPath()
                        ) ?: return@forEach
                        copyToUri(src = attachment.getUri(), dst = documentFile.uri)
                    }
                } else {
                    copyToUri(pendingAttachmentsToSave!!.first().getUri(), destinationUri)
                }
            } catch (e: Exception) {
                showErrorToast(e)
            }
        }
    }

    private fun getAttachmentsAdapter(): AttachmentsAdapter? {
        return binding.messageHolder.threadAttachmentsRecyclerview.adapter as? AttachmentsAdapter
    }

    private fun sendMessage() {
        val text = binding.messageHolder.threadTypeMessage.text.toString().trim()
        val attachments = ArrayList(getAttachmentSelections())

        if (text.isEmpty() && attachments.isEmpty()) return

        if (participants.isEmpty() || threadId == 0L) {
            toast(R.string.unknown_error_occurred)
            return
        }

        // A scheduled message must be stored and alarmed, never handed to the radio now.
        if (isScheduledMessage) {
            askForExactAlarmPermissionIfNeeded {
                storeScheduledMessage(text, attachments.isNotEmpty())
            }
            return
        }

        clearCurrentMessage()
        // Recorded here, not deduced later: see [scrollOnNextUpdate].
        scrollOnNextUpdate = true
        ensureBackgroundThread {
            val subscriptionId = currentSIMCardIndex

            isSendingMessage = true
            var handedOver = false
            try {
                if (attachments.isNotEmpty()) {
                    sendMmsMessage(text, attachments, subscriptionId)
                } else {
                    sendNormalMessage(text, subscriptionId)
                }

                updateLastConversationMessage(threadId)
                handedOver = true
                refreshMessages()
                refreshConversations()
            } finally {
                // Only when the send never reached the refresh. Clearing the flag here
                // unconditionally raced the refresh it had just posted: the refresh reads
                // isSendingMessage from a background thread after querying the provider,
                // this ran on the main thread within a millisecond, so the read almost
                // always lost and the message arrived without the list following it. The
                // refresh clears the flag itself on both of its paths.
                if (!handedOver) {
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        isSendingMessage = false
                    }
                }
            }
        }
    }

    /**
     * Asked on a tap, in the app's own sheet rather than a system alert.
     *
     * A failed message used to drop its text straight into the composer with nothing said,
     * which reads as the app having lost the message rather than offering to send it again.
     */
    private fun askToResend(messageId: Long, body: String, isMMS: Boolean) {
        if (body.isEmpty()) {
            toast(R.string.resend_failed_empty)
            return
        }

        val choices = ArrayList<CapsuleChoice>()
        // Only for SMS. An MMS carries attachments that would have to be rebuilt from the
        // provider to be sent again, so offering a one:tap resend there would quietly send
        // the text without them.
        if (!isMMS) {
            choices.add(
                CapsuleChoice(
                    label = getString(R.string.resend_failed_send),
                    subtitle = getString(R.string.resend_failed_send_desc),
                    icon = R.drawable.ic_ph_paper_plane_right,
                ) { resendMessage(messageId, body) }
            )
        }
        choices.add(
            CapsuleChoice(
                label = getString(R.string.resend_failed_edit),
                subtitle = getString(
                    if (isMMS) R.string.resend_failed_edit_mms_desc
                    else R.string.resend_failed_edit_desc
                ),
                icon = R.drawable.ic_ph_text_t,
            ) {
                binding.messageHolder.threadTypeMessage.setText(body)
                binding.messageHolder.threadTypeMessage.setSelection(body.length)
                messageToResend = messageId
                checkSendMessageAvailability()
            }
        )

        // Last, and in the menus' red: a failed message often just wants to go away, and
        // without this the only way to be rid of one was a long:press into selection mode.
        choices.add(
            CapsuleChoice(
                label = getString(R.string.resend_failed_delete),
                subtitle = getString(R.string.resend_failed_delete_desc),
                icon = R.drawable.ic_ph_trash,
                isDestructive = true,
            ) {
                ensureBackgroundThread {
                    deleteMessage(messageId, isMMS)
                    refreshMessages()
                    refreshConversations()
                }
            }
        )

        textoCapsuleDialog(getString(R.string.resend_failed_title), choices)
    }

    private fun resendMessage(messageId: Long, body: String) {
        // Same intent as a fresh send, recorded on the tap thread: see [scrollOnNextUpdate].
        scrollOnNextUpdate = true
        ensureBackgroundThread {
            isSendingMessage = true
            var handedOver = false
            try {
                // The failed row goes first so the thread does not end up holding the
                // message twice. A send that fails again inserts its own row before it
                // reaches the radio, so the failure stays visible either way.
                deleteMessage(messageId, false)
                sendNormalMessage(body, currentSIMCardIndex)
                updateLastConversationMessage(threadId)
                handedOver = true
                refreshMessages()
                refreshConversations()
            } catch (e: Exception) {
                runOnUiThread { showErrorToast(e) }
            } finally {
                if (!handedOver) {
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        isSendingMessage = false
                    }
                }
            }
        }
    }

    private fun sendNormalMessage(text: String, subscriptionId: Int) {
        val addresses = participants.flatMap { it.phoneNumbers.map { pn -> pn.normalizedNumber } }.distinct()
        if (addresses.isNotEmpty()) {
            val subId = availableSIMCards.getOrNull(subscriptionId)?.subscriptionId
            sendMessageCompat(text, addresses, subId, emptyList())
        }
    }

    private fun sendMmsMessage(text: String, attachments: List<AttachmentSelection>, subscriptionId: Int) {
        val addresses = participants.flatMap { it.phoneNumbers.map { pn -> pn.normalizedNumber } }.distinct()
        if (addresses.isNotEmpty()) {
            val subId = availableSIMCards.getOrNull(subscriptionId)?.subscriptionId
            val mmsAttachments = attachments.map {
                com.texto.sms.models.Attachment(null, 0L, it.uri.toString(), it.mimetype, 0, 0, it.filename)
            }
            sendMessageCompat(text, addresses, subId, mmsAttachments)
        }
    }

    private fun dialNumber() {
        val phoneNumber = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value
        if (phoneNumber != null) dialNumber(phoneNumber)
    }

    fun showProperties(message: Message) {
        MessageDetailsDialog(this, message)
    }

    /**
     * Archiving, and its two siblings below, write to the SMS provider and then to Room.
     * Room refuses to be touched from the main thread and takes the process down when it
     * is, which is what these three did straight from the overflow menu. The provider write
     * is slow I/O in its own right and does not belong there either.
     */
    private fun archiveThread() {
        setConversationArchived(archived = true) { finish() }
    }

    private fun unarchiveThread() {
        setConversationArchived(archived = false) { refreshMenuItems() }
    }

    private fun setConversationArchived(archived: Boolean, onDone: () -> Unit) {
        ensureBackgroundThread {
            try {
                updateConversationArchivedStatus(threadId, archived)
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    onDone()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    showErrorToast(e)
                }
            }
        }
    }

    /**
     * Hands the contacts app every number here that has no card yet. This absorbed the
     * separate "save all the numbers" row: that one ran exactly this intent with the whole
     * participant list, so the only difference between the two was how many numbers went in.
     */
    private fun addNumberToContact() {
        val numbers = unsavedParticipantNumbers()
        if (numbers.isEmpty()) return
        Intent(Intent.ACTION_INSERT_OR_EDIT).apply {
            type = ContactsContract.Contacts.CONTENT_ITEM_TYPE
            putExtra(ContactsContract.Intents.Insert.PHONE, numbers.joinToString(";"))
            startActivity(this)
        }
    }

    private fun copyNumberToClipboard() {
        val number = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.value ?: return
        copyToClipboard(number)
    }
    private fun renameConversation() {
        if (conversation != null) {
            RenameConversationDialog(this, conversation!!) {
                ensureBackgroundThread {
                    val updatedConv = renameConversation(conversation!!, it)
                    conversation = updatedConv
                    runOnUiThread {
                        if (isFinishing || isDestroyed) return@runOnUiThread
                        setupThreadTitle()
                    }
                }
            }
        }
    }
    /**
     * Opening a thread already marks it read, so this is normally a no-op: it is here for the
     * cases the automatic pass cannot cover : a message that arrived while the thread was
     * open, or a row the provider update missed. It stays on this screen rather than closing
     * it, since there is nothing to go back and look at.
     */
    private fun markAsRead() {
        ensureBackgroundThread {
            markThreadMessagesRead(threadId)
            refreshConversations()
        }
    }
    private fun tryBlocking() {
        val numbers = participants.getAddresses()
        val numbersString = TextUtils.join(", ", numbers)
        val question = String.format(resources.getString(R.string.block_confirmation), numbersString)

        textoConfirmDialog(question, isDestructive = true) {
            ensureBackgroundThread {
                numbers.forEach { blockNumber(it) }
                refreshConversations()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    finish()
                }
            }
        }
    }
    private fun askConfirmDelete() {
        val question = resources.getString(R.string.delete_whole_conversation_confirmation)
        textoConfirmDialog(question, isDestructive = true) {
            ensureBackgroundThread {
                deleteOrRecycleConversation(threadId)
                refreshConversations()
                runOnUiThread {
                    if (isFinishing || isDestroyed) return@runOnUiThread
                    finish()
                }
            }
        }
    }
    private fun restoreMessages() {
        ensureBackgroundThread {
            restoreAllMessagesFromRecycleBinForConversation(threadId)
            refreshConversations()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                finish()
            }
        }
    }
    private fun addContactAttachment(data: Uri) {}

    /**
     * Slot number of the SIM a message went through, for the date separator's SIM badge.
     * ThreadAdapter has always been ready to draw this, but getThreadItems() passed a
     * hard-coded "" so the badge was blank on every message, incoming ones included.
     * Empty on single-SIM devices, where the badge would say nothing useful.
     */
    private fun simLabelFor(message: Message): String {
        if (availableSIMCards.size < 2) return ""
        return availableSIMCards
            .firstOrNull { it.subscriptionId == message.subscriptionId }
            ?.id
            ?.toString()
            .orEmpty()
    }

    private fun getThreadItems(): ArrayList<ThreadItem> {
        val items = ArrayList<ThreadItem>()
        var prevDateTime = 0L
        messages.forEach { message ->
            if (message.date - prevDateTime > MIN_DATE_TIME_DIFF_SECS) {
                items.add(ThreadDateTime(message.date, simLabelFor(message)))
                prevDateTime = message.date.toLong()
            }
            items.add(message)
            
            if (!message.isReceivedMessage() && !message.isScheduled) {
                if (message.type == Telephony.Sms.MESSAGE_TYPE_SENT) {
                    // No separate status row: a sent message carries its ticks on the
                    // bubble itself, which is where they belong and where they can sit
                    // next to each other.
                } else if (message.type == Telephony.Sms.MESSAGE_TYPE_OUTBOX || message.type == Telephony.Sms.MESSAGE_TYPE_QUEUED) {
                    items.add(ThreadSending(message.id))
                } else if (message.type == Telephony.Sms.MESSAGE_TYPE_FAILED || message.status == Telephony.Sms.STATUS_FAILED) {
                    // The body, not an error string. The row draws its own fixed caption
                    // from the layout, so this field was never read for display -- and the
                    // English commons string it used to hold was what a tap put in the
                    // composer instead of the message the user had tried to send.
                    items.add(ThreadError(message.id, message.body, message.isMMS))
                }
            }
        }
        return items
    }

    private fun scrollToBottom(forceScroll: Boolean = false, isManual: Boolean = false) {
        val adapter = getOrCreateThreadAdapter()
        if (adapter.itemCount > 0) {
            // Guard: Never auto-scroll while selection mode is active, UNLESS it's a manual scroll request (FAB)
            if (!isManual && adapter.isSelectionModeActive()) {
                return
            }

            val recyclerView = binding.threadMessagesList
            // Re-calculate the "at bottom" check. canScrollVertically(1) returns false if at the very end.
            val isAtBottom = !recyclerView.canScrollVertically(1)
            
            if (forceScroll || isAtBottom || isManual) {
                recyclerView.post {
                    if (!isFinishing && !isDestroyed) {
                        // Smoothly glide to the bottom instead of instant jump
                        if (forceScroll || isManual) {
                            // For forced scrolls (sending) or manual, use custom smooth scroller for perfect speed
                            val scroller = object : androidx.recyclerview.widget.LinearSmoothScroller(this) {
                                override fun getVerticalSnapPreference(): Int = SNAP_TO_END
                                override fun calculateSpeedPerPixel(displayMetrics: android.util.DisplayMetrics): Float {
                                    return 120f / displayMetrics.densityDpi
                                }
                            }
                            scroller.targetPosition = adapter.itemCount - 1
                            recyclerView.layoutManager?.startSmoothScroll(scroller)
                        } else {
                            // Standard smooth scroll for background receipts
                            recyclerView.smoothScrollToPosition(adapter.itemCount - 1)
                        }
                    }
                }
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshMessages(@Suppress("unused") event: Events.RefreshMessages) {
        if (isRecycleBin || isDestroyed || isFinishing) return

        if (isActivityVisible) {
            notificationManager.cancel(threadId.hashCode())
        }

        ensureBackgroundThread {
            if (isDestroyed || isFinishing) return@ensureBackgroundThread
            val addresses = participants.getAddresses().toSet()
            if (addresses.isEmpty()) return@ensureBackgroundThread
            
            val newThreadId = getThreadId(addresses)
            val newMessages = getMessages(newThreadId, includeScheduledMessages = false)
            
            val scheduledMessages = try {
                messagesDB.getScheduledThreadMessages(threadId)
                    .filterNot { it.isScheduled && it.millis() < System.currentTimeMillis() }
            } catch (e: Exception) { emptyList() }
            
            val combinedRawMessages = ArrayList<Message>(newMessages)
            combinedRawMessages.addAll(scheduledMessages)
            
            // Centralized processing handles both DB persistence and wire-format hiding
            val combinedMessages = processReactions(combinedRawMessages)
            
            if (messages.size != combinedMessages.size || messages.hashCode() != combinedMessages.hashCode()) {
                messages = ArrayList(combinedMessages)
                val forceScroll = isSendingMessage
                allMessagesFetched = false
                if (!isDestroyed && !isFinishing) {
                    setupAdapter(forceScroll)
                    if (forceScroll) {
                        runOnUiThread {
                            binding.threadMessagesList.postDelayed({
                                scrollToBottom(forceScroll = true)
                                isSendingMessage = false
                            }, 100)
                        }
                    }
                }
            } else {
                if (isSendingMessage) {
                    isSendingMessage = false
                    runOnUiThread { scrollToBottom(forceScroll = true) }
                }
            }
        }
    }

    fun onReactionPicked(message: Message, emoji: String) {
        if (message.reaction == emoji) return
        
        val bodySnippet = message.body.take(30).trim()
        val text = "$emoji to '$bodySnippet'"
        
        runOnUiThread {
            message.reaction = emoji
            val cacheKey = MessagingCache.getReactionKey(message.id, message.isMMS)
            MessagingCache.reactionsCache[cacheKey] = emoji
            val adapter = getOrCreateThreadAdapter()
            val index = adapter.currentList.indexOf(message)
            if (index != -1) {
                adapter.notifyItemChanged(index)
            }
        }

        ensureBackgroundThread {
            try {
                reactionsDB.insertOrUpdate(Reaction(message.id, message.isMMS, threadId, emoji))
            } catch (e: Exception) {
                android.util.Log.e("ReactionError", "Failed to save reaction", e)
            }
            
            val subscriptionId = currentSIMCardIndex 
            sendNormalMessage(text, subscriptionId)
            updateLastConversationMessage(threadId)
            runOnUiThread {
                refreshMessages()
                refreshConversations()
            }
        }
    }

    private fun processReactions(messages: List<Message>): List<Message> {
        val (regex, map) = getReactionTools()
        
        // 1. Efficiently load stored reactions from the dedicated table
        val persistedReactions = try { reactionsDB.getThreadReactions(threadId) } catch (e: Exception) { emptyList() }
        val reactionMapDB = persistedReactions.associateBy({ MessagingCache.getReactionKey(it.messageId, it.isMms) }, { it.emoji })
        
        // 2. Build a snippet-based index for fast O(1) target lookup
        // We use a snippet of the body to match reaction texts
        val snippetMap = messages.filter { !it.body.isNullOrBlank() }
            .associateBy({ it.body.take(30).trim() }, { it })

        val result = messages.toMutableList()
        
        // Pass 1: Handle incoming reaction texts and UI filtering
        for (i in result.indices.reversed()) {
            val msg = result[i]
            val match = regex.find(msg.body)
            
            if (match != null) {
                // IT IS A REACTION MESSAGE - HIDE IT IMMEDIATELY
                msg.isReactionMessage = true
                
                val prefix = match.groupValues[1].ifEmpty { match.groupValues[2] }
                val quotedText = match.groupValues[3].trim()
                val emoji = map[prefix] ?: "👍"

                // Fast O(1) lookup in our snippet index
                val target = snippetMap[quotedText]
                if (target != null && target.id != msg.id && target.date <= msg.date) {
                    val targetKey = MessagingCache.getReactionKey(target.id, target.isMMS)
                    target.reaction = emoji
                    MessagingCache.reactionsCache[targetKey] = emoji
                    
                    // Persist newly discovered reaction to DB
                    if (reactionMapDB[targetKey] != emoji) {
                        ensureBackgroundThread {
                            reactionsDB.insertOrUpdate(Reaction(target.id, target.isMMS, threadId, emoji))
                        }
                    }
                }
            } else {
                // Pass 2: Re-apply already known reactions from DB or Cache
                val cacheKey = MessagingCache.getReactionKey(msg.id, msg.isMMS)
                msg.reaction = reactionMapDB[cacheKey] ?: MessagingCache.reactionsCache[cacheKey]
            }
        }

        return result.filter { !it.isReactionMessage }
    }

    private fun getReactionTools(): Pair<Regex, Map<String, String>> {
        // Regex 1: "Liked message '...'" (Old format)
        // Regex 2: "👍 to '...'" (Modern format)
        val reactionRegex = Regex("^(?:(Liked|Loved|Disliked|Laughed at|Surprised by|Cried at|Angry at|Emphasized|Questioned)\\s+(?:message\\s+)?|([👍❤️👎😂😮😢😡⁉️❓🔥💯👏✅🎉]+|\\p{So}+)\\s+to\\s+)['\"](.*?)['\"]?\\s*$")
        
        val reactionMap = mapOf(
            "Liked" to "👍",
            "Loved" to "❤️",
            "Disliked" to "👎",
            "Laughed at" to "😂",
            "Surprised by" to "😮",
            "Cried at" to "😢",
            "Angry at" to "😡",
            "Emphasized" to "‼️",
            "Questioned" to "❓",
            // Direct emoji mapping (identity)
            "👍" to "👍", "❤️" to "❤️", "😂" to "😂", "😮" to "😮", "😢" to "😢", "😡" to "😡", "👎" to "👎", 
            "⁉️" to "⁉️", "❓" to "❓", "🔥" to "🔥", "💯" to "💯", "👏" to "👏", "✅" to "✅", "🎉" to "🎉",
            "‼️" to "‼️"
        )
        return Pair(reactionRegex, reactionMap)
    }

    private fun isMmsMessage(text: String): Boolean {
        return getAttachmentSelections().isNotEmpty() || participants.size > 1 || isLongMmsMessage(text)
    }

    private fun updateMessageType() {
        // Nothing to do since send became an icon: it never carried a label, and the empty
        // string this used to write was only there to keep a Button from showing one.
    }

    @SuppressLint("MissingPermission")
    private fun setupSIMSelector() {
        val manager = subscriptionManagerCompat()
        val activeSubscriptions = manager.activeSubscriptionInfoList ?: emptyList()
        availableSIMCards.clear()
        activeSubscriptions.forEachIndexed { index, info ->
            availableSIMCards.add(SIMCard(index + 1, info.subscriptionId, info.displayName.toString()))
        }

        // The design's SIM control: a disc the same size as the clip beside it, with the
        // slot's digit riding its upper corner as a small filled badge. The digit alone was
        // too small to read on its own, and colour alone said nothing about which slot it
        // was -- the design carries both, so this does too.
        val simHolder = binding.messageHolder.threadSimHolder
        val simIcon = binding.messageHolder.threadSelectSimIcon
        val simNumber = binding.messageHolder.threadSelectSimNumber
        val number = participants.firstOrNull()?.phoneNumbers?.firstOrNull()?.normalizedNumber

        if (availableSIMCards.size < 2 || number.isNullOrEmpty()) {
            simHolder.beGone()
            simIcon.beGone()
            simNumber.beGone()
            return
        }

        currentSIMCardIndex = config.getUseSIMIdAtNumber(number)
            .coerceIn(0, availableSIMCards.lastIndex)
        simHolder.beVisible()
        simIcon.beVisible()
        simNumber.beVisible()

        val discSide = COMPOSER_DISC_DP.getScaledPx()
        simHolder.updateLayoutParams<LinearLayout.LayoutParams> {
            width = discSide
            height = discSide
            marginStart = 4.getScaledPx()
        }
        styleComposerDisc(simIcon, config.inputBarTextColor)

        val badgeSide = SIM_BADGE_SIZE_DP.getScaledPx()
        simNumber.updateLayoutParams<android.widget.FrameLayout.LayoutParams> {
            width = badgeSide
            height = badgeSide
        }
        simNumber.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.58f))
        simNumber.typeface = typefaceFor(android.graphics.Typeface.BOLD)

        fun renderSelectedSIM() {
            val simColor = config.getSimColor(currentSIMCardIndex)
            // The glyph takes the slot's colour; the disc under it stays the neutral inset
            // the design gives every small control.
            simIcon.applyColorFilter(simColor)
            simNumber.text = (currentSIMCardIndex + 1).toString()
            simNumber.setTextColor(config.mainBackgroundColor)
            simNumber.background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.OVAL
                setColor(simColor)
                // The ring that lifts the badge off the disc behind it.
                setStroke(
                    (1.5f * resources.displayMetrics.density).toInt().coerceAtLeast(1),
                    config.recentColor
                )
            }
        }

        // Tapping opens the list of SIMs to choose from; it used to silently cycle to the
        // next one, which gave no indication of what was picked or what else was available.
        val pickSim = android.view.View.OnClickListener { showSimPicker(number) { renderSelectedSIM() } }
        // The badge sits on top of the glyph, so it has to carry the handler too or a tap
        // landing on the digit would fall through to the capsule and do nothing.
        simIcon.setOnClickListener(pickSim)
        simNumber.setOnClickListener(pickSim)
        renderSelectedSIM()
    }

    /**
     * In-thread search: narrows the thread to the messages whose body matches, so you can
     * find something inside one long conversation without leaving it.
     *
     * Matching goes through [containsPersian] for the same reason the global search does :
     * carriers send the Arabic forms of letters a Persian keyboard never types.
     */
    private fun setupThreadSearch() {
        val input = binding.threadSearchInput

        binding.threadSearchBtn.setOnClickListener {
            if (binding.threadSearchBar.isVisible()) closeThreadSearch() else openThreadSearch()
        }

        binding.threadSearchClose.setOnClickListener {
            // Something typed: clear it and stay in search. Nothing typed: the X is the way
            // out, rather than a button that visibly does nothing.
            if (input.text?.isNotEmpty() == true) input.setText("") else closeThreadSearch()
        }

        if (input.tag != "thread_search_watcher") {
            input.addTextChangedListener { text ->
                applyThreadSearch(text?.toString().orEmpty())
            }
            input.tag = "thread_search_watcher"
        }

        onBackPressedDispatcher.addCallback(this) {
            if (isEmojiPanelOpen) {
                // Back closes the panel before it closes the thread, for the same reason it
                // closes the keyboard first: it is the thing that just opened.
                closeEmojiPanel()
            } else if (binding.threadSearchBar.isVisible()) {
                closeThreadSearch()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    private fun openThreadSearch() {
        binding.threadSearchBar.beVisible()
        styleThreadSearchBar()
        binding.threadSearchInput.requestFocus()
        showKeyboard(binding.threadSearchInput)
        styleThreadSearchButton()
    }

    private fun closeThreadSearch() {
        hideKeyboard()
        binding.threadSearchInput.setText("")
        binding.threadSearchBar.beGone()
        applyThreadSearch("")
        styleThreadSearchButton()
    }

    /** Marks the header magnifier while the bar is open, the way the nav capsule marks its tab. */
    private fun styleThreadSearchButton() {
        val isOpen = binding.threadSearchBar.isVisible()
        binding.threadSearchBtn.applyColorFilter(
            if (isOpen) config.accentGradientStart else config.mainTextColor
        )
    }

    /**
     * What the adapter should be showing right now.
     *
     * Every refresh path submits through this rather than handing over [threadItems] raw.
     * They used to submit the unfiltered list directly, so an arriving message, a resume, or
     * the pager firing on a now:short list all silently threw the search results away.
     */
    private fun visibleThreadItems(): ArrayList<ThreadItem> {
        val needle = threadSearchQuery
        if (needle.isEmpty()) return threadItems

        val matches = threadItems.filter {
            it is com.texto.sms.models.Message && it.body.containsPersian(needle)
        }

        // Date separators are kept for the days that still have a hit, so a result is still
        // anchored to when it was sent. A bare list of bubbles out of context was most of
        // what made the filtered view hard to read.
        val keptDates = matches.mapNotNull { (it as? com.texto.sms.models.Message)?.date }.toSet()
        return ArrayList(
            threadItems.filter { item ->
                when (item) {
                    is com.texto.sms.models.Message -> item.body.containsPersian(needle)
                    is com.texto.sms.models.ThreadItem.ThreadDateTime ->
                        keptDates.any { isSameDayAs(it, item.date) }
                    else -> false
                }
            }
        )
    }

    private fun isSameDayAs(a: Int, b: Int): Boolean {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = a * 1000L
        val dayA = cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
        cal.timeInMillis = b * 1000L
        val dayB = cal.get(java.util.Calendar.YEAR) * 1000 + cal.get(java.util.Calendar.DAY_OF_YEAR)
        return dayA == dayB
    }

    private fun applyThreadSearch(query: String) {
        threadSearchQuery = query.trim()
        val shown = visibleThreadItems()
        val hits = shown.count { it is com.texto.sms.models.Message }

        binding.threadSearchCount.apply {
            if (threadSearchQuery.isEmpty()) {
                beGone()
            } else {
                beVisible()
                text = resources.getQuantityString(
                    R.plurals.search_results_count, hits, hits.toString().toUiDigits()
                )
            }
        }

        // A thread reads from the bottom, so the list is anchored there; a handful of search
        // hits anchored the same way sat at the foot of an empty screen. Results read from
        // the top, and the anchor goes back when the search closes.
        (binding.threadMessagesList.layoutManager as? androidx.recyclerview.widget.LinearLayoutManager)
            ?.stackFromEnd = threadSearchQuery.isEmpty()

        // The adapter highlights the term inside each bubble, so a long message says which
        // part of it actually matched instead of leaving you to find it by eye.
        getOrCreateThreadAdapter().setSearchTerm(threadSearchQuery)
        getOrCreateThreadAdapter().updateMessages(shown) {
            if (isFinishing || isDestroyed) return@updateMessages
            if (threadSearchQuery.isNotEmpty()) binding.threadMessagesList.scrollToPosition(0)
        }

        binding.threadSearchEmpty.apply {
            beVisibleIf(threadSearchQuery.isNotEmpty() && hits == 0)
            setTextColor(config.mainTextColor.withAlpha(0.68f))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())
        }
    }

    /** Paints the in-thread search bar as the same glass capsule the composer is. */
    private fun styleThreadSearchBar() = binding.apply {
        threadSearchBar.background = com.texto.sms.helpers.TextoGlass.bar(
            tint = config.inputBarBackgroundColor,
            cornerRadius = 100f * resources.displayMetrics.density,
            opacity = config.glassOpacity / 100f,
            strokeWidthPx = 1.getScaledPx()
        )
        threadSearchBar.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        threadSearchIcon.applyColorFilter(config.inputBarTextColor.withAlpha(0.68f))
        threadSearchClose.applyColorFilter(config.inputBarTextColor.withAlpha(0.68f))
        threadSearchInput.setTextColor(config.inputBarTextColor)
        threadSearchInput.setHintTextColor(config.inputBarTextColor.withAlpha(0.5f))
        threadSearchInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())
        threadSearchCount.setTextColor(config.accentGradientStart)
        threadSearchCount.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.7f))
    }

    /**
     * The SIM chooser. Each row carries the slot's own colour twice over : as a filled dot
     * and as the colour's name in brackets after the carrier : because the badge on the
     * composer identifies a slot by colour alone, and a list that only spelled out carrier
     * names never said which colour went with which.
     *
     * Built by [textoCapsuleDialog] rather than commons' `setupDialogStuff`, which drew its
     * title bar from the base (light) theme and left a white strip above the rows in a face
     * nothing else in the app uses.
     */
    private fun showSimPicker(number: String, onPicked: () -> Unit) {
        val choices = availableSIMCards.mapIndexed { index, card ->
            val simColor = config.getSimColor(index)
            CapsuleChoice(
                label = card.label,
                subtitle = com.texto.sms.helpers.ColorNames.of(this, simColor),
                swatch = simColor,
                isActive = index == currentSIMCardIndex,
                onPick = {
                    currentSIMCardIndex = index.coerceIn(0, availableSIMCards.lastIndex)
                    config.saveUseSIMIdAtNumber(number, currentSIMCardIndex)
                    onPicked()
                }
            )
        }

        textoCapsuleDialog(getString(R.string.select_sim_card), choices)
    }

    /**
     * The design's small round control, shared by the composer's clip and SIM: a disc filled
     * with `--inset` behind the `--divider` hairline, carrying a `--muted` glyph.
     */
    private fun styleComposerDisc(view: android.widget.ImageView, inkColor: Int) {
        val side = COMPOSER_DISC_DP.getScaledPx()
        view.updateLayoutParams {
            width = side
            height = side
        }
        val pad = 10.getScaledPx()
        view.setPadding(pad, pad, pad, pad)
        view.applyColorFilter(inkColor.withAlpha(0.68f))
        view.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(config.mainBackgroundColor.withAlpha(0.55f))
            setStroke(
                1.getScaledPx(),
                com.texto.sms.helpers.TextoGlass.rimFor(config.recentColor, 0.22f)
            )
        }
        view.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
    }

    private fun setupMessagingEdgeToEdge() {
        // The padding below is computed from the composer's height, so it has to be redone
        // whenever the composer resizes: a draft wrapping to a second line, the attachments
        // strip appearing, the scheduled banner opening. Asking for the insets again is
        // enough, since that listener is where the padding is decided.
        binding.messageHolder.root.addOnLayoutChangeListener { v, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) {
                ViewCompat.requestApplyInsets(binding.threadMessagesList)
                v.post { scrollToBottom() }
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.threadMessagesList) { view, insets ->
            
            // The composer's real height, not a constant. 86dp was less than the bar
            // actually measures once its own padding and the gesture area are counted, so
            // the newest bubble sat under it with its bottom edge clipped off. The bar also
            // grows -- a multi-line draft, the attachments strip, the scheduled banner --
            // and no fixed number can follow that.
            //
            // Its height alone is the whole answer: message_holder is anchored to the
            // parent's bottom and runs to the very bottom of the screen, so the navigation
            // inset is already inside that measurement. Adding systemBars on top would be
            // the same space counted twice.
            val imeType = WindowInsetsCompat.Type.ime()
            val isImeVisible = insets.isVisible(imeType)
            val imeHeight = insets.getInsets(imeType).bottom
            val systemBarsHeight = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom

            // The composer's height and nothing else -- no keyboard term, no navigation
            // term. message_holder is anchored to the parent's bottom and always runs to the
            // very bottom of the screen: with the keyboard down it measures 262px, and with
            // the keyboard up it measures 1195px because it now stretches from just above
            // the keys all the way down behind them. Both readings already contain whatever
            // is covering the list.
            //
            // Adding the IME inset on top of that counted the keyboard twice and was why a
            // sent message could not be seen until the keyboard was dismissed: the padding
            // came to roughly 2095px on a 2119px list, so nearly the whole viewport was dead
            // space and every new bubble landed inside it. One press of back closed the
            // keyboard, the composer shrank back to 262px, and the message appeared -- which
            // is exactly the symptom, and it pointed at the padding rather than at the send.
            val finalBottomPadding = maxOf(binding.messageHolder.root.height, 86.getScaledPx())
            
            // No top inset for the header. thread_holder carries
            // appbar_scrolling_view_behavior, so the CoordinatorLayout already offsets this
            // list below the app bar : padding for the header on top of that is the same
            // space counted twice. It went unnoticed while the thread was anchored to its
            // newest message, because the gap sat above content that was scrolled off; it
            // showed the moment a search left a short list reading from the top, opening the
            // results a header and a status bar down the screen.
            //
            // A small breathing gap is kept so the first bubble is not flush against the bar.
            view.setPadding(
                view.paddingLeft,
                TOP_GAP_DP.getScaledPx(),
                view.paddingRight,
                finalBottomPadding
            )

            if (isImeVisible) {
                // Force sync scroll to keep latest messages in view above the keyboard
                scrollToBottom(forceScroll = true)
            }

            // Keep the library's keyboard height tracker updated
            if (isImeVisible && imeHeight > 150) {
                config.keyboardHeight = imeHeight - systemBarsHeight
            }

            insets
        }
    }

    /**
     * The scroll-to-bottom FAB's colour follows the app's own theme, not the system's
     * dynamic-colour palette -- the app has its own set of themes (Neon, Classic, ...) for
     * this, and picking up Material You here meant the one floating control on this screen
     * could be wearing a colour the user never chose from any of them.
     */
    private fun getBottomBarColor() = getBottomNavigationBackgroundColor()

    private fun launchGetContentIntent(types: Array<String>, requestCode: Int) {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            type = types.first()
            if (types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, types)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            addCategory(Intent.CATEGORY_OPENABLE)
        }
        try {
            startActivityForResult(intent, requestCode)
        } catch (_: Exception) {
            toast(R.string.no_app_found)
        }
    }

    private fun launchCapturePhotoIntent() {
        handlePermission(PERMISSION_CAMERA) {
            if (it) {
                try {
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    val file = File(cacheDir, "photo.jpg")
                    capturedImageUri = getMyFileUri(file)
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, capturedImageUri)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    startActivityForResult(intent, CAPTURE_PHOTO_INTENT)
                } catch (e: Exception) {
                    // Fallback to general capture if specific file path fails
                    try {
                        startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE), CAPTURE_PHOTO_INTENT)
                    } catch (e2: Exception) {
                        toast(R.string.no_app_found)
                    }
                }
            }
        }
    }

    private fun launchCaptureVideoIntent() {
        handlePermission(PERMISSION_CAMERA) {
            if (it) {
                try {
                    startActivityForResult(Intent(MediaStore.ACTION_VIDEO_CAPTURE), CAPTURE_VIDEO_INTENT)
                } catch (e: Exception) {
                    toast(R.string.no_app_found)
                }
            }
        }
    }

    private fun launchCaptureAudioIntent() {
        handlePermission(PERMISSION_RECORD_AUDIO) {
            if (it) {
                try {
                    startActivityForResult(Intent(MediaStore.Audio.Media.RECORD_SOUND_ACTION), CAPTURE_AUDIO_INTENT)
                } catch (e: Exception) {
                    val systemRecorderIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "audio/*"
                    }
                    try {
                        startActivityForResult(systemRecorderIntent, PICK_AUDIO_INTENT)
                    } catch (e2: Exception) {
                        toast(R.string.no_app_found)
                    }
                }
            }
        }
    }

    private fun launchPickContactIntent() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        startActivityForResult(intent, PICK_CONTACT_INTENT)
    }

    private fun applyOutlines() = binding.apply {
        val density = resources.displayMetrics.density
        val isNewUi = config.useNewUi
        
        // Top Bar Outline (Matching texto_topbar_bg corners)
        if (config.topBarOutline && isNewUi) {
            val r26 = 26f * density
            val thickness = config.topBarOutlineThickness
            val thickStroke = (thickness * density).toInt()
            val outline = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setStroke(thickStroke, config.topBarOutlineColor)
                setColor(Color.TRANSPARENT)
                cornerRadii = FloatArray(8) { r26 }
            }
            val drawable = android.graphics.drawable.LayerDrawable(arrayOf(outline))
            // Sits exactly on the painted bar, which is now rounded all round and
            // starts below the status bar rather than behind it.
            drawable.setLayerInset(0, 0, statusBarInsetOf(binding.threadAppbar), 0, 0)
            binding.threadAppbar.foreground = drawable
        } else {
            binding.threadAppbar.foreground = null
        }

        // The composer carries no outline of its own. It used to take the same ring the
        // floating search pill wears, drawn at a 100dp radius over a 22dp field, so the
        // stroke followed a different curve to the edge underneath it and read as a halo
        // sitting slightly off the shape. The field is a flat filled surface now, and the
        // row it sits in is what separates it from the messages above.
        binding.messageHolder.root
            .findViewById<android.view.View>(R.id.texto_message_input_bar)
            ?.foreground = null
    }

    companion object {
        /** Breathing room above the first bubble; the app bar's own offset is separate. */
        private const val TOP_GAP_DP = 12

        var currentThreadId = 0L
        private const val MIN_DATE_TIME_DIFF_SECS = 300

        /** Width Toolbar gives each action item, independent of the app's UI scale. */
        private const val SCROLL_TO_BOTTOM_FAB_LIMIT = 20
        private const val PREFETCH_THRESHOLD = 45
    }
}
