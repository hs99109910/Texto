package com.texto.sms.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.provider.Telephony
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import android.widget.RelativeLayout
import androidx.core.widget.addTextChangedListener


import com.texto.sms.R
import com.texto.sms.adapters.ConversationsAdapter
import com.texto.sms.adapters.SearchResultsAdapter
import com.texto.sms.databinding.ActivityMainBinding
import com.texto.sms.extensions.*
import com.texto.sms.dialogs.EditFilterDialog
import com.texto.sms.helpers.CapsuleChoice
import com.texto.sms.helpers.MessageFilter
import com.texto.sms.helpers.AppThemes
import com.texto.sms.helpers.TextoEditPulse
import com.texto.sms.helpers.hideInlineBars
import com.texto.sms.helpers.inlineColourBars
import com.texto.sms.helpers.showInlineBarsAround
import com.texto.sms.helpers.TextoGlass
import com.texto.sms.helpers.textoCapsuleDialog
import com.texto.sms.helpers.textoColorPicker
import com.texto.sms.helpers.expandTouchTarget
import com.texto.sms.helpers.SEARCHED_MESSAGE_ID
import com.texto.sms.helpers.THREAD_ID
import com.texto.sms.helpers.THREAD_TITLE
import com.texto.sms.helpers.textoConfirmDialog
import com.texto.sms.helpers.textoFilterCustomizer
import com.texto.sms.models.Conversation
import com.texto.sms.models.Events
import com.texto.sms.models.Message
import com.texto.sms.models.SearchResult
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import com.texto.sms.extensions.areSystemAnimationsEnabled
import com.texto.sms.extensions.copyToClipboard
import com.texto.sms.extensions.darkenColor
import com.texto.sms.extensions.getContrastColor
import com.texto.sms.extensions.hideKeyboard
import com.texto.sms.extensions.notificationManager
import com.texto.sms.extensions.openNotificationSettings
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
import com.texto.sms.helpers.NavigationIcon
import com.texto.sms.extensions.PERMISSION_READ_CONTACTS
import com.texto.sms.extensions.PERMISSION_READ_SMS
import com.texto.sms.extensions.PERMISSION_SEND_SMS
import com.texto.sms.extensions.isQPlus
import com.texto.sms.extensions.isSPlus
import androidx.core.view.isVisible

class MainActivity : SimpleActivity() {

    private companion object {
        /**
         * Filter chips are full pills in the design (`border-radius: 999px`). Any radius past
         * half the chip's height rounds the ends completely, so this only has to clear the
         * tallest a chip gets at the largest UI scale.
         */
        const val CHIP_PILL_RADIUS_DP = 100

        /** Ringtone picker for a filter's own notification sound. */
        const val PICK_FILTER_SOUND_REQUEST = 1201

        /** The design's wordmark: 20% up from the 34dp it shipped at, then 10% back down. */
        const val LOGO_HEIGHT_DP = 34

        /**
         * The home header, 12dp shorter than the 70dp every other bar uses.
         *
         * Measured at 420dpi, the 70dp bar left 16.4dp of empty space above and below a 37dp
         * wordmark: the bar was tall because of its padding, not because of what it holds.
         * At 58dp the same wordmark keeps 10.5dp on each side and the gear disc 9dp, so
         * nothing inside it shrank -- only the air around them. Twelve dp is most of a
         * conversation row's worth of screen, and the list starts that much higher.
         */
        const val HEADER_HEIGHT_DP = 58

        /** Matches the other panel transitions in the app. */
        const val SEARCH_ANIM_MILLIS = 260L
    }

    /** The date window the search is narrowed to; [SearchDateRange.ANY] means no narrowing. */
    private var searchDateRange = com.texto.sms.helpers.SearchDateRange.ANY

    /** The conversation filter the search is narrowed to, independent of the home screen's. */
    // Both start with an empty label and are rebuilt from resources in onCreate: a literal
    // here would be a Persian word waiting to flash on an English screen.
    private var searchFilter: MessageFilter = MessageFilter.all("")
    private var searchFilterChipsAdapter: com.texto.sms.adapters.FilterChipsAdapter? = null

    private val MAKE_DEFAULT_APP_REQUEST = 1
    private var storedTextColor = 0
    private var lastSearchedText = ""
    private var bus: EventBus? = null
    private var isActivityVisible = false
    private var isFirstResume = true
    private var isInitialized = false
    // Bumped at the start of every top-level conversation fetch; a background thread that
    // finishes after a newer fetch has already started discards its (now stale) results
    // instead of stomping fresher data that may have already landed on screen.
    private var conversationLoadGeneration = 0
    private var wasImeVisible = false
    private var isSearchExpanded = false

    private var lastUsedNewUi: Boolean? = null

    /** The look config was last painted from; see [appearanceSignature]. */
    private var lastAppearanceSignature: String? = null

    /** Set when a launch lands on a filter, so the first resume can say which one. */
    private var pendingFilterNotice = false

    // Scroll position captured when leaving for a thread, replayed once the list is back.
    private var pendingScrollPosition = androidx.recyclerview.widget.RecyclerView.NO_POSITION
    private var pendingScrollOffset = 0
    private var dragTouchHelper: androidx.recyclerview.widget.ItemTouchHelper? = null
    private var allConversations = ArrayList<Conversation>()
    // Kept only so the filter editor can offer archived threads as pickable senders too --
    // they are deliberately absent from allConversations, which drives the visible list.
    private var archivedConversations: List<Conversation> = emptyList()
    // Comparable (SystemBlockedNumbers.comparable) numbers of every saved contact, refreshed
    // alongside the conversation list; backs the "Contacts only" filter.
    private var contactPhoneNumbers: Set<String> = emptySet()

    /** Phone book snapshot kept for the "build a filter from contacts" picker. */
    private var cachedContactsForFilters: List<Pair<String, String>> = emptyList()
    private var activeFilter: MessageFilter = MessageFilter.all("")

    /** The customiser's sound row, waiting on the ringtone picker; see [customizeFilter]. */
    private var pendingSoundPick: ((uri: String?, label: String?) -> Unit)? = null

    /** Paints the sliding halo behind the chosen filter chip; installed in buildFilterChips. */
    private var filterHalo: FilterHaloDecoration? = null

    private var filterChipsAdapter: com.texto.sms.adapters.FilterChipsAdapter? = null
    private var filterChipDragHelper: androidx.recyclerview.widget.ItemTouchHelper? = null

    private val binding by viewBinding(ActivityMainBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        // The night-mode pin lives in App.onCreate now. Set here it ran after the activity
        // had already been created with the phone's own mode, so a device in dark mode built
        // this screen twice on every cold start and flashed the other palette in between.
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.conversationsList))
        setupSearchEdgeToEdge()
        setupTextoTopAppBar(binding.mainAppbar, NavigationIcon.None, Color.TRANSPARENT)

        setupHomeActions()

        // Opening the app starts on the filter the user nominated in settings, rather than
        // wherever they happened to leave the chips last time. Written through activeFilterId
        // so buildFilterChips() and everything downstream keep reading a single source.
        //
        // Only on a real launch. onCreate also runs on a rotation or any other configuration
        // change, and doing this there threw away the chip the user had just picked.
        if (savedInstanceState == null) {
            config.activeFilterId = config.defaultFilterId
            // Landing on a filter hides threads, so say which one rather than letting a
            // filtered list look like a list with messages missing from it.
            pendingFilterNotice = config.defaultFilterId != MessageFilter.ID_ALL
        }

        loadMessages()
        
        // No update check: it contacted the upstream project's GitHub repo on every
        // launch and would have offered to install their APK over this build.

        // Dismissing the keyboard no longer closes search: the panel carries a filter row and
        // a date range that are worth keeping while you scroll the results.
        ViewCompat.setOnApplyWindowInsetsListener(binding.mainCoordinator) { _, insets ->
            wasImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            insets
        }

        // Back closes the search panel first and only then leaves the app, so search is a
        // state you can step out of rather than a screen you get stuck in.
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSearchExpanded) {
                    shrinkSearchBar()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    override fun onResume() {
        super.onResume()
        isActivityVisible = true
        applyOutlines()

        if (config.startAppearanceEditor) {
            config.startAppearanceEditor = false
            binding.conversationsList.post { openAppearanceEditor() }
        }

        // Belt-and-suspenders alongside the refreshConversations() subscriber: if a
        // RefreshConversations event was ever missed while this activity wasn't visible,
        // resuming still picks up the latest config.customFilters (e.g. senders added to a
        // filter from elsewhere) instead of leaving activeFilter/chips stale until some
        // other event happens to fire.
        if (isInitialized) buildFilterChips()
        initMessenger()

        if (pendingFilterNotice) {
            pendingFilterNotice = false
            // Said once, on the launch that did it, so a filtered list is never mistaken for
            // messages having gone missing.
            toast(getString(R.string.filter_active_notice, activeFilter.label))
        }

        // No second syncConversations() here. initMessenger() -> getCachedConversations()
        // already starts a full fetch cycle and ends it with its own sync, and starting
        // another one straight afterwards bumped conversationLoadGeneration. The cached
        // pass then failed its own generation check when it came back and threw the list
        // away, so nothing was drawn until the much slower telephony sync finished -- the
        // whole point of reading the cache first was lost on every single launch.

        val mainTextColor = config.mainTextColor
        getOrCreateConversationsAdapter().apply {
            if (storedTextColor != mainTextColor) updateTextColor(mainTextColor)
            updateDrafts()
        }

        // Repainting and re-measuring the whole screen -- header, gear, chips, glass, the two
        // floating bars and every row's scaling -- used to happen on every single resume, so
        // stepping back from a conversation redid work whose answer had not changed and
        // dropped frames doing it. It is done on the first resume and after that only when
        // something it reads from config actually differs; see [appearanceSignature].
        val signature = appearanceSignature()
        if (signature != lastAppearanceSignature) {
            lastAppearanceSignature = signature
            styleAppTitle()
            setupScaledToolbar(binding.mainToolbar, HEADER_HEIGHT_DP)
            styleHeaderGear()
            styleEmptyState()
            getOrCreateConversationsAdapter().updateScaling()
            applyCustomColors()
            setupHomeActions()
            setupOverlayBars()
            // buildFilterChips() above already rebuilt and repainted the row; the blanket
            // notifyDataSetChanged() that used to follow it here rebound every chip a second
            // time for nothing.
            binding.textoSearchInput.setTextColor(config.inputBarTextColor)
            binding.textoSearchInput.setHintTextColor(config.inputBarTextColor.withAlpha(0.5f))
        }

        if (isFirstResume && config.useNewUi) {
            isFirstResume = false
            // A short, plain settle. It was an 800ms overshoot that squashed the header to
            // 40% and sprang it back, which read as the app struggling to open rather than
            // as an entrance.
            binding.mainAppbar.alpha = 0f
            binding.mainAppbar.translationY = -8f * resources.displayMetrics.density
            binding.mainAppbar.animate()
                .translationY(0f)
                .alpha(1f)
                .setDuration(SEARCH_ANIM_MILLIS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
    }

    /**
     * Everything the home screen's painters read out of config, in one value.
     *
     * onResume compares it with the last one and only repaints when it moved. Anything added
     * to a styling pass here has to be added to this list too, or a change to it will not
     * show up until the activity is recreated.
     */
    private fun appearanceSignature(): String = listOf(
        config.appTheme, config.useNewUi, config.uiScale, config.glassOpacity,
        config.fontFamilyTexto, config.fontSize,
        config.mainTextColor, config.mainBackgroundColor,
        config.topBarColor, config.topBarTextColor,
        config.recentColor, config.accentGradientStart, config.accentInkColor,
        config.inputBarBackgroundColor, config.inputBarTextColor,
        config.topBarOutline, config.topBarOutlineColor, config.topBarOutlineThickness,
    ).joinToString("|")

    override fun onPause() {
        super.onPause()
        isActivityVisible = false
        storedTextColor = config.mainTextColor
        rememberScrollPosition()
    }

    /**
     * The refresh that runs on the way back submits a new list, which lands the user back
     * at the top. Capture where they were so it can be restored after that update commits.
     */
    private fun rememberScrollPosition() {
        val layoutManager = binding.conversationsList.layoutManager
                as? androidx.recyclerview.widget.LinearLayoutManager ?: return
        val position = layoutManager.findFirstVisibleItemPosition()
        if (position == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return

        pendingScrollPosition = position
        pendingScrollOffset = layoutManager.findViewByPosition(position)?.let {
            it.top - binding.conversationsList.paddingTop
        } ?: 0
    }

    private fun clearPendingScroll() {
        pendingScrollPosition = androidx.recyclerview.widget.RecyclerView.NO_POSITION
        pendingScrollOffset = 0
    }

    private fun restoreScrollPosition() {
        if (pendingScrollPosition == androidx.recyclerview.widget.RecyclerView.NO_POSITION) return
        val layoutManager = binding.conversationsList.layoutManager
                as? androidx.recyclerview.widget.LinearLayoutManager ?: return
        if (pendingScrollPosition >= getOrCreateConversationsAdapter().itemCount) return

        layoutManager.scrollToPositionWithOffset(pendingScrollPosition, pendingScrollOffset)
    }

    override fun onDestroy() {
        super.onDestroy()
        bus?.unregister(this)
    }

    /**
     * The design's header gear: a 38dp rounded square of the card colour at 55%, rimmed with
     * the same hairline every glass surface carries. Painted from the theme rather than left
     * on the XML placeholder so a theme change repaints it in place.
     */
    /**
     * The header's one control, drawn the way the design draws every small round control:
     * a 40dp disc (`border-radius: 999px`) filled with `--inset` behind the `--divider`
     * hairline, carrying a `--muted` glyph. Same recipe as the thread header's action tiles
     * and the composer's clip and SIM discs, which is what ties the three bars together.
     */
    private fun styleHeaderGear() {
        // Both tiles on this row, not just the gear. The live editor's palette was left on
        // the layout's placeholder -- a dark rounded square with a white glyph -- sitting
        // directly beside a light 40dp disc with a dark one: two materials on one row, on the
        // one row the design means to read as a single control set.
        styleHeaderTile(binding.textoMenuBtn)

        // The capsule's middle is the search field's placeholder: the bar's own ink at the
        // idle 58%, with the magnifier at the 68% every header glyph carries.
        val ink = config.topBarTextColor
        binding.textoHeaderSearchIcon.apply {
            updateLayoutParams<LinearLayout.LayoutParams> {
                width = 20.getScaledPx()
                height = 20.getScaledPx()
            }
            applyColorFilter(ink.withAlpha(0.68f))
        }
        binding.textoHeaderSearchLabel.apply {
            setTextColor(ink.withAlpha(0.58f))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.0f))
            typeface = typefaceFor(android.graphics.Typeface.NORMAL)
        }
    }

    private fun styleHeaderTile(tile: android.widget.ImageView) = tile.apply {
        val density = resources.displayMetrics.density
        val size = 40.getScaledPx()
        updateLayoutParams<LinearLayout.LayoutParams> {
            width = size
            height = size
        }
        val pad = 11.getScaledPx()
        setPadding(pad, pad, pad, pad)
        background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
            setColor(config.mainBackgroundColor.withAlpha(0.55f))
            setStroke(
                density.toInt().coerceAtLeast(1),
                com.texto.sms.helpers.TextoGlass.rimFor(config.recentColor, 0.22f)
            )
        }
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        imageTintList = android.content.res.ColorStateList.valueOf(
            config.topBarTextColor.withAlpha(0.68f)
        )
        alpha = 1f

        // The disc is 40dp because the design draws it at 40dp, and the header is now 58dp
        // rather than 70dp, so neither can be leaned on to reach the platform's 48dp touch
        // floor. The hit rect is padded out to it instead: the tile looks the same and the
        // area you can actually hit grows.
        //
        // Both tiles get it. This used to assign `touchDelegate` directly, and a View carries
        // exactly one, so the row kept whichever was painted last -- the gear -- and the
        // palette next to it stayed at 40dp in the corner of the screen where the thumb is
        // least accurate. TextoTouchDelegate holds a rect per view instead.
        expandTouchTarget()
    }

    /**
     * The home screen's two ways out: search, from the capsule that is the header, and a new
     * conversation, from the extended FAB.
     *
     * The three-tab floating pill that used to carry both is gone from the layout as well as
     * from the screen now -- its "Conversations" tab only ever pointed at the screen it sat
     * on -- along with the halo, the six tab views and the painting passes that kept styling
     * all of it on every resume for something nobody could see.
     */
    private fun setupHomeActions() = binding.apply {
        val radiant = AppThemes.isRadiant(config.appTheme)
        styleFab()
        textoFab.setOnClickListener { launchNewConversation() }
        textoFab.beVisibleIf(!radiant && !isSearchExpanded)
        setupRadiantNavigation(radiant)
        textoHeaderSearch.setOnClickListener {
            if (!isSearchExpanded) expandSearchBar()
        }

        if (textoSearchInput.tag != "text_watcher_attached") {
            textoSearchInput.addTextChangedListener { text ->
                searchTextChanged(text?.toString() ?: "")
            }
            textoSearchInput.tag = "text_watcher_attached"
        }

        textoSearchClear.setOnClickListener {
            if (textoSearchInput.text?.isNotEmpty() == true) {
                textoSearchInput.setText("")
            } else {
                // Nothing typed, so the X is the way out of search rather than a no-op.
                shrinkSearchBar()
            }
        }
    }

    /** The Radiant skin replaces the lone FAB with a persistent Telegram-style capsule. */
    private fun setupRadiantNavigation(visible: Boolean) = binding.apply {
        radiantNavContainer.beVisibleIf(visible)
        if (!visible) return@apply

        val density = resources.displayMetrics.density
        TextoGlass.applyPanel(
            view = radiantNavContainer,
            tint = config.topBarColor,
            cornerRadius = 32f * density,
            opacity = 0.94f,
            strokeWidthPx = density.toInt().coerceAtLeast(1),
            rimAlpha = 0.12f,
            sheenAlpha = 0.04f,
        )

        val active = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(config.accentGradientStart, config.accentGradientEnd)
        ).apply { cornerRadius = 24f * density }
        radiantMessagesBtn.background = active

        val muted = config.mainTextColor.withAlpha(0.66f)
        val activeInk = config.accentInkColor
        radiantMessagesIcon.applyColorFilter(activeInk)
        radiantMessagesLabel.setTextColor(activeInk)
        listOf(radiantContactsIcon, radiantArchiveIcon).forEach { it.applyColorFilter(muted) }
        listOf(radiantContactsLabel, radiantArchiveLabel).forEach { it.setTextColor(muted) }

        radiantComposeBtn.background = android.graphics.drawable.GradientDrawable(
            android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
            intArrayOf(config.accentGradientStart, config.accentGradientEnd)
        ).apply {
            shape = android.graphics.drawable.GradientDrawable.OVAL
        }
        radiantComposeBtn.applyColorFilter(activeInk)

        radiantMessagesBtn.setOnClickListener {
            if (isSearchExpanded) shrinkSearchBar() else conversationsList.smoothScrollToPosition(0)
        }
        radiantContactsBtn.setOnClickListener { launchNewConversation() }
        radiantArchiveBtn.setOnClickListener {
            startActivity(Intent(this@MainActivity, ArchivedConversationsActivity::class.java))
        }
        radiantComposeBtn.setOnClickListener { launchNewConversation() }

        listOf(radiantMessagesBtn, radiantContactsBtn, radiantArchiveBtn, radiantComposeBtn).forEach {
            it.isClickable = true
            it.isFocusable = true
        }
    }

    /**
     * The FAB in the theme's own accent and on-accent ink, at the UI scale, with Material 3's
     * 16dp corner rather than a full pill so it reads as the one primary action on the screen.
     */
    private fun styleFab() = binding.textoFab.apply {
        val accent = config.accentGradientStart
        val ink = config.accentInkColor
        backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
        setTextColor(ink)
        iconTint = android.content.res.ColorStateList.valueOf(ink)
        rippleColor = android.content.res.ColorStateList.valueOf(ink.withAlpha(0.20f))
        iconSize = 22.getScaledPx()
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.95f))
        typeface = typefaceFor(android.graphics.Typeface.BOLD)
        // Material's button style shouts the label in English; the design writes it as a sentence.
        isAllCaps = false
        minHeight = 56.getScaledPx()
        shapeAppearanceModel = shapeAppearanceModel.withCornerSize(16f * resources.displayMetrics.density)
        updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
            marginStart = 16.getScaledPx()
            marginEnd = 16.getScaledPx()
        }
    }

    /**
     * Search opens as its own panel across the top rather than swallowing the bottom capsule,
     * so the three nav tabs stay reachable the whole time. The panel slides down from behind
     * the header on the same decelerate curve the rest of the app animates on, and the
     * conversation list underneath cross-fades out.
     */
    private fun expandSearchBar() = binding.apply {
        if (isSearchExpanded) return@apply
        isSearchExpanded = true

        // The header capsule and the home filter row step aside while searching: they sit
        // above this panel in the same space, so leaving them up hid the field and its chips
        // behind the wordmark. They fade out over the same interval the panel slides in on
        // rather than blinking out of existence a frame before it -- three things vanishing
        // at once was the jolt this transition had.
        //
        // Starting a conversation is not what the search screen is for either, and the FAB
        // would sit on top of the results and the keyboard.
        listOf<View>(mainAppbar, filterBar, textoFab).forEach { bar ->
            bar.animate()
                .alpha(0f)
                .setDuration(SEARCH_ANIM_MILLIS)
                .setInterpolator(DecelerateInterpolator())
                .withEndAction {
                    if (isFinishing || isDestroyed) return@withEndAction
                    if (isSearchExpanded) bar.beGone()
                }
                .start()
        }

        buildSearchFilterChips()
        buildSearchDateChips()
        styleSearchPanel()

        searchHolder.beVisible()
        searchHolder.alpha = 0f
        textoSearchPanel.translationY = -textoSearchPanel.height.toFloat().coerceAtLeast(1f)

        searchHolder.animate()
            .alpha(1f)
            .setDuration(SEARCH_ANIM_MILLIS)
            .setInterpolator(DecelerateInterpolator())
            .start()
        textoSearchPanel.animate()
            .translationY(0f)
            .setDuration(SEARCH_ANIM_MILLIS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (isFinishing || isDestroyed) return@withEndAction
                textoSearchInput.requestFocus()
                showKeyboard(textoSearchInput)
            }
            .start()

        // The list behind it steps aside rather than sitting under a translucent panel.
        mainNestedScrollview.getChildAt(0)?.animate()
            ?.alpha(0f)
            ?.setDuration(SEARCH_ANIM_MILLIS)
            ?.withEndAction { mainNestedScrollview.getChildAt(0)?.beGone() }
            ?.start()

        applySearch()
    }

    private fun shrinkSearchBar() = binding.apply {
        if (!isSearchExpanded) return@apply
        isSearchExpanded = false

        hideKeyboard()
        textoSearchInput.setText("")
        searchDateRange = com.texto.sms.helpers.SearchDateRange.ANY
        searchFilter = MessageFilter.all(getString(R.string.filter_all))

        val panelHeight = textoSearchPanel.height.toFloat().coerceAtLeast(1f)
        textoSearchPanel.animate()
            .translationY(-panelHeight)
            .setDuration(SEARCH_ANIM_MILLIS)
            .setInterpolator(DecelerateInterpolator())
            .start()
        searchHolder.animate()
            .alpha(0f)
            .setDuration(SEARCH_ANIM_MILLIS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                if (isFinishing || isDestroyed) return@withEndAction
                searchHolder.beGone()
                textoSearchPanel.translationY = 0f
            }
            .start()

        mainNestedScrollview.getChildAt(0)?.apply {
            beVisible()
            animate().alpha(1f).setDuration(SEARCH_ANIM_MILLIS).start()
        }

        // Back in on the same fade they left on. Full view alpha at the end of it: how
        // see-through these are is the glass setting's job alone. A 0.92 here multiplied
        // against the fill and put the bars below whatever the slider said, which is part of
        // why its top end never looked opaque.
        listOf<View>(mainAppbar, filterBar, textoFab).forEach { bar ->
            if (bar === textoFab && AppThemes.isRadiant(config.appTheme)) return@forEach
            bar.beVisible()
            bar.animate()
                .alpha(1f)
                .setDuration(SEARCH_ANIM_MILLIS)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        textoFab.extend()
    }

    /**
     * Paints the search panel from the live theme: the field is the same glass capsule the
     * header and nav pill are, and the date chip is styled by [styleFilterChip] so it is
     * visibly the same control as the filter chips beside it.
     */
    private fun styleSearchPanel() = binding.apply {
        val density = resources.displayMetrics.density

        // The panel owns the top of the screen once the header steps aside, so it carries the
        // status-bar inset itself. Read from the window rather than assumed, so it is right
        // whatever the device puts up there.
        textoSearchPanel.setPadding(
            textoSearchPanel.paddingLeft,
            statusBarInsetOf(textoSearchPanel) + 8.getScaledPx(),
            textoSearchPanel.paddingRight,
            textoSearchPanel.paddingBottom
        )

        textoSearchCapsule.background = com.texto.sms.helpers.TextoGlass.bar(
            tint = config.inputBarBackgroundColor,
            cornerRadius = CHIP_PILL_RADIUS_DP * density,
            opacity = config.glassOpacity / 100f,
            strokeWidthPx = 1.getScaledPx()
        )
        textoSearchCapsule.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND

        textoSearchPanelIcon.applyColorFilter(config.inputBarTextColor.withAlpha(0.68f))
        textoSearchClear.applyColorFilter(config.inputBarTextColor.withAlpha(0.68f))
        textoSearchInput.setTextColor(config.inputBarTextColor)
        textoSearchInput.setHintTextColor(config.inputBarTextColor.withAlpha(0.5f))
        textoSearchInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())

        searchResultCount.setTextColor(config.mainTextColor.withAlpha(0.68f))
        searchResultCount.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.7f))

        buildSearchDateChips()
    }

    /**
     * The conversation filters, reused verbatim on the search panel so the same audience
     * chips narrow a search that narrow the home list.
     */
    private fun buildSearchFilterChips() {
        val filters = currentFilters()
        if (searchFilterChipsAdapter == null) {
            searchFilterChipsAdapter = com.texto.sms.adapters.FilterChipsAdapter(
                onSelect = { filter ->
                    searchFilter = filter
                    searchFilterChipsAdapter?.submitFilters(
                        currentFilters(), searchFilter.id, filterCounts(currentFilters())
                    )
                    applySearch()
                },
                onEditRequested = { },
                onAddRequested = { },
                showAddChip = false,
                styleChip = { chip, filterId, isActive -> styleFilterChip(chip, filterId, isActive) }
            )
            binding.searchFilterBar.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
            )
            binding.searchFilterBar.adapter = searchFilterChipsAdapter
            // The same selector the home row has. Without it the chosen filter on the search
            // panel had nothing marking it at all.
            binding.searchFilterBar.addItemDecoration(
                FilterHaloDecoration { searchFilterChipsAdapter?.activePosition() ?: -1 }
            )
        }
        searchFilterChipsAdapter?.submitFilters(filters, searchFilter.id, filterCounts(filters))
    }

    /**
     * The date row: every window the search offers, as its own chip, plus one that opens the
     * Jalali calendar for anything else.
     *
     * This used to be a single chip that opened a list dialog, which hid the whole control
     * behind two taps and a screen change. The presets are the common case, so they are on
     * the panel where the filter chips are, drawn to the same recipe so the two rows read as
     * one set of controls rather than two unrelated ones.
     */
    private fun buildSearchDateChips() {
        val row = binding.searchDateChips
        row.removeAllViews()
        // The chip the row should be showing once it is laid out: null while the filter is
        // "any", which is the first chip and so is already where the row starts.
        var activeChip: android.view.View? = null
        val range = com.texto.sms.helpers.SearchDateRange

        // Two windows and a calendar, rather than the six that pushed everything else off
        // the row. Today, yesterday and the quarter are all a couple of taps away in the
        // picker, and a row you can read at a glance is worth more than the shortcut.
        val presets = listOf(
            range.ID_WEEK to R.string.search_date_week,
            range.ID_MONTH to R.string.search_date_month,
        )

        presets.forEach { (id, labelRes) ->
            val isActive = searchDateRange.id == id
            val chip = dateChip(getString(labelRes), isActive) {
                // Tapping the one that is already on clears it. With "any" no longer a chip
                // of its own, this is the only way back to an unfiltered search.
                searchDateRange = if (isActive) range.ANY else range.preset(id)
                buildSearchDateChips()
                applySearch()
            }
            if (isActive) activeChip = chip
            row.addView(chip)
        }

        // The custom chip carries the picked window once there is one, so the row still says
        // what is being filtered on without a second label to read.
        // Deliberately not drawn like the two beside it. This one opens a calendar rather
        // than toggling, and a chip that looks identical to its neighbours promises a filter
        // you switch on: the caret and the accent outline say a screen is coming.
        val isCustom = searchDateRange.id == range.ID_CUSTOM
        val customChip = dateChip(
            if (isCustom) {
                range.labelOf(this, searchDateRange)
            } else {
                getString(R.string.search_date_picked)
            },
            isCustom,
            opensPicker = true
        ) {
            com.texto.sms.helpers.JalaliRangePicker(this).show { start, end ->
                searchDateRange = range.custom(start, end)
                buildSearchDateChips()
                applySearch()
            }
        }
        if (isCustom) activeChip = customChip
        row.addView(customChip)

        // Where the row should sit once it has been measured. A HorizontalScrollView counts
        // scrollX from the visual left whichever way the layout runs, so leaving it at 0
        // under RTL showed the row's *end* : it opened on "select a range" with everything
        // before it off screen. Anchored to the start -- which is the right edge under
        // Persian and the left one under English -- unless something further along is
        // picked, in which case that is what needs to be on screen.
        val target = activeChip
        binding.searchDateBar.post {
            if (target == null) {
                val startEdge = if (resources.configuration.layoutDirection ==
                    android.view.View.LAYOUT_DIRECTION_RTL
                ) {
                    android.view.View.FOCUS_RIGHT
                } else {
                    android.view.View.FOCUS_LEFT
                }
                binding.searchDateBar.fullScroll(startEdge)
            } else {
                val centred = target.left - (binding.searchDateBar.width - target.width) / 2
                binding.searchDateBar.scrollTo(centred.coerceAtLeast(0), 0)
            }
        }
    }

    /**
     * One chip on the date row, painted to the same recipe as a conversation filter chip.
     *
     * [opensPicker] marks the one that leads to the calendar instead of switching a filter
     * on: it keeps the accent outline and carries a caret, so the row does not offer three
     * identical-looking controls of which one behaves differently.
     */
    private fun dateChip(
        label: String,
        isActive: Boolean,
        opensPicker: Boolean = false,
        onTap: () -> Unit,
    ): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            text = label
            maxLines = 1
            includeFontPadding = false
            isClickable = true
            val chipRadius = CHIP_PILL_RADIUS_DP * density
            background = when {
                isActive -> com.texto.sms.helpers.TextoGlass.accent(
                    start = config.accentGradientStart,
                    end = config.accentGradientEnd,
                    cornerRadius = chipRadius,
                    mid = config.accentGradientMid
                )
                opensPicker -> android.graphics.drawable.GradientDrawable().apply {
                    shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                    cornerRadius = chipRadius
                    setColor(config.accentGradientStart.withAlpha(0.10f))
                    setStroke(1.getScaledPx(), config.accentGradientStart.withAlpha(0.55f))
                }
                // The header capsule's recipe, same as the filter chips beside it.
                else -> com.texto.sms.helpers.TextoGlass.bar(
                    tint = if (config.topBarColor != 0) config.topBarColor else Color.BLACK,
                    cornerRadius = chipRadius,
                    opacity = config.glassOpacity / 100f,
                    strokeWidthPx = 1.getScaledPx(),
                    rimAlpha = 0.20f
                )
            }
            val padH = 14.getScaledPx()
            val padV = 8.getScaledPx()
            setPadding(padH, padV, padH, padV)
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            elevation = if (isActive) 6 * density else 0f
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.78f))
            setTextColor(
                when {
                    isActive -> config.accentInkColor
                    opensPicker -> config.accentGradientStart
                    else -> config.mainTextColor.withAlpha(0.58f)
                }
            )
            typeface = typefaceFor(
                if (isActive || opensPicker) {
                    android.graphics.Typeface.BOLD
                } else {
                    android.graphics.Typeface.NORMAL
                }
            )
            if (opensPicker) {
                // A caret at the label's end : the same shorthand the rest of the app uses
                // for "this opens something". Rotated because only the one caret ships.
                val caret = androidx.appcompat.content.res.AppCompatResources
                    .getDrawable(context, R.drawable.ic_ph_caret_left)?.mutate()?.apply {
                        val side = 14.getScaledPx()
                        setBounds(0, 0, side, side)
                        setTint(if (isActive) config.accentInkColor else config.accentGradientStart)
                    }
                setCompoundDrawablesRelative(caret, null, null, null)
                compoundDrawablePadding = 6.getScaledPx()
            }
            layoutParams = LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 6.getScaledPx() }
            setOnClickListener { onTap() }
        }
    }

    /** Re-runs the search with whatever combination of query, filter and date is set. */
    private fun applySearch() {
        searchTextChanged(binding.textoSearchInput.text?.toString().orEmpty())
    }

    private fun loadMessages() {
        if (isQPlus()) {
            val roleManager = getSystemService(RoleManager::class.java)
            if (roleManager?.isRoleAvailable(RoleManager.ROLE_SMS) == true) {
                if (roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
                    askPermissions()
                } else {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
                    startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
                }
            } else if (!isFinishing && !isDestroyed && config.appRunCount <= 1) {
                toast(R.string.unknown_error_occurred)
                finish()
            }
        } else {
            if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
                askPermissions()
            } else {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivityForResult(intent, MAKE_DEFAULT_APP_REQUEST)
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (requestCode == PICK_FILTER_SOUND_REQUEST) {
            val callback = pendingSoundPick
            pendingSoundPick = null
            if (resultCode == RESULT_OK && callback != null) {
                val uri = resultData
                    ?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                // A null uri here is "Silent", which is a choice rather than a cancellation --
                // cancelling never reaches this branch at all. Both it and a real ringtone are
                // an override; only the sheet's own Clear puts the filter back on the app's.
                val label = uri?.let {
                    runCatching { RingtoneManager.getRingtone(this, it)?.getTitle(this) }.getOrNull()
                } ?: getString(R.string.no_sound)
                callback(uri?.toString(), label)
            }
            return
        }
        if (requestCode == MAKE_DEFAULT_APP_REQUEST) {
            if (isQPlus()) {
                val roleManager = getSystemService(RoleManager::class.java)
                if (roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true) {
                    askPermissions()
                } else if (!isFinishing && !isDestroyed && config.appRunCount <= 1) {
                    finish()
                }
            } else {
                if (Telephony.Sms.getDefaultSmsPackage(this) == packageName) {
                    askPermissions()
                } else if (!isFinishing && !isDestroyed && config.appRunCount <= 1) {
                    finish()
                }
            }
        }
    }

    private fun askPermissions() {
        handlePermission(PERMISSION_READ_SMS) { readSms ->
            if (!readSms) {
                if (!isFinishing && !isDestroyed) {
                    finish()
                }
                return@handlePermission
            }

            handlePermission(PERMISSION_SEND_SMS) { sendSms ->
                if (!sendSms) {
                    if (!isFinishing && !isDestroyed) {
                        finish()
                    }
                    return@handlePermission
                }

                handlePermission(PERMISSION_READ_CONTACTS) {
                    askNotificationPermission { granted ->
                        if (!granted) {
                            // The app's own sheet: commons' builds itself from the base theme
                            // and its own English strings. See the same swap in ThreadActivity.
                            textoConfirmDialog(
                                message = getString(R.string.allow_notifications_incoming_messages),
                                title = getString(R.string.permission_required),
                                positiveLabel = getString(R.string.grant_permission)
                            ) {
                                openNotificationSettings()
                            }
                        }
                    }

                    initMessenger()
                    bus = EventBus.getDefault()
                    try { bus!!.register(this) } catch (_: Exception) {}
                }
            }
        }
    }

    private fun initMessenger(isManualReorder: Boolean = false) {
        if (isFinishing || isDestroyed) return
        try {
            if (!isInitialized) {
                setupOneTimeViews()
                isInitialized = true
            }
            checkWhatsNewDialog()
            getCachedConversations(isManualReorder)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to init messenger during transition", e)
        }
    }

    /** "همه" always ships with the app; "مخاطبین" and "تبلیغات" only show when enabled in settings; everything after them the user defined. */
    /**
     * The chip row, in the order it is read.
     *
     * "بدون تبلیغات" leads when it is switched on, and "همه" drops to the end. Turning that
     * filter on is a statement that the unfiltered list is not the one you want to land in,
     * so it takes the first position under the thumb and "همه" becomes the thing you reach
     * for deliberately. With the row laid out right-to-left, first in this list is the
     * rightmost chip.
     */
    private fun currentFilters(): List<MessageFilter> = buildList {
        val noAds = if (config.showAdsFilter) {
            // The chip is the inverse of the stored ads list: it starts holding every thread
            // and loses them one at a time as they are marked as advertising. The ads list
            // itself never gets a chip of its own.
            MessageFilter.noAds(
                label = getString(R.string.filter_no_ads),
                senders = config.adsFilter.senders
            )
        } else {
            null
        }

        noAds?.let { add(it) }
        if (config.showContactsOnlyFilter) {
            add(MessageFilter.contactsOnly(getString(R.string.filter_contacts_only)))
        }
        if (noAds == null) add(MessageFilter.all(getString(R.string.filter_all)))
        addAll(config.customFilters)
        if (noAds != null) add(MessageFilter.all(getString(R.string.filter_all)))
    }

    private fun buildFilterChips() {
        val filters = currentFilters()
        activeFilter = filters.firstOrNull { it.id == config.activeFilterId } ?: filters.first()

        if (filterChipsAdapter == null) {
            val adapter = com.texto.sms.adapters.FilterChipsAdapter(
                onSelect = { selectFilter(it) },
                onEditRequested = { editFilter(it) },
                onAddRequested = { editFilter(null) },
                styleChip = { chip, filterId, isActive -> styleFilterChip(chip, filterId, isActive) }
            )
            filterChipsAdapter = adapter
            binding.filterBar.apply {
                layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                    this@MainActivity,
                    androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL,
                    false
                )
                this.adapter = adapter
                // Behind the chips, not on one of them: the row scrolls and its chips are
                // recycled, so the selector cannot be a view that travels with them.
                filterHalo = FilterHaloDecoration { filterChipsAdapter?.activePosition() ?: -1 }
                    .also { addItemDecoration(it) }
            }
            // Custom chips can be dragged (once the finger moves past touch slop while held)
            // to reorder; "All"/"Contacts only"/"+" are excluded inside the callback so they
            // can't move or be a drop target. Drag is started manually by the adapter itself
            // -- see FilterChipsAdapter -- so it can coexist with long-press-to-edit.
            filterChipDragHelper = androidx.recyclerview.widget.ItemTouchHelper(
                com.texto.sms.helpers.FilterChipDragCallback(adapter) { reorderedCustomFilters ->
                    config.customFilters = reorderedCustomFilters
                }
            ).apply { attachToRecyclerView(binding.filterBar) }
            adapter.itemTouchHelper = filterChipDragHelper
        }

        filterChipsAdapter?.submitFilters(filters, activeFilter.id, filterCounts(filters))
    }

    private fun editFilter(existing: MessageFilter?) {
        // Offer every loaded conversation as a pickable sender, newest first and
        // de-duplicated by number, so a filter can be built by tapping names.
        val pickable = (allConversations + archivedConversations)
            .distinctBy { conversation ->
                // Alphanumeric sender ids ("BANKMELLI", "Irancell") have no digits, so their
                // comparable form is empty. Keying on that alone collapsed every one of them
                // into a single row and the rest could never be picked.
                com.texto.sms.helpers.SystemBlockedNumbers.comparable(conversation.phoneNumber)
                    .ifEmpty { conversation.phoneNumber.lowercase() }
            }
            .map { conversation ->
                val label = conversation.title.ifBlank { conversation.phoneNumber }
                // A group thread is stored under its first recipient's number only, so a
                // filter built from it matches that one person rather than the whole group.
                // Say so in the row instead of letting it look like it covers everyone.
                val suffix = if (conversation.isGroupConversation) {
                    " ${getString(R.string.filter_group_first_member_only)}"
                } else {
                    ""
                }
                "$label$suffix" to conversation.phoneNumber
            }

        // A filter can also be built straight from the phone book, not just from people who
        // have already texted. Read off the already-loaded contact cache so opening the
        // dialog never blocks; it is empty until contacts finish loading, and the picker
        // says so rather than showing an empty list.
        val pickableContacts = cachedContactsForFilters
            .filter { it.second.isNotBlank() }
            .distinctBy { it.second }

        EditFilterDialog(
            activity = this,
            existing = existing,
            pickableSenders = pickable,
            pickableContacts = pickableContacts,
            onDelete = if (existing == null) null else {
                { deleteFilter(existing) }
            },
            onCustomize = { filter, onSaved -> customizeFilter(filter, onSaved) }
        ) { filter ->
            val filters = config.customFilters.toMutableList()
            val index = filters.indexOfFirst { it.id == filter.id }
            if (index >= 0) {
                // The editor holds the filter as it was when it opened, so anything the
                // colours-and-sound sheet saved while it was up -- that sheet writes straight
                // through, since it is reached from here and has nowhere else to save to --
                // is not in the copy coming back. Confirming would otherwise quietly undo it.
                val stored = filters[index]
                filters[index] = filter.copy(
                    sentBubbleColor = stored.sentBubbleColor,
                    sentBubbleTextColor = stored.sentBubbleTextColor,
                    receivedBubbleColor = stored.receivedBubbleColor,
                    receivedBubbleTextColor = stored.receivedBubbleTextColor,
                    backgroundColor = stored.backgroundColor,
                    notificationSoundUri = stored.notificationSoundUri,
                    notificationSoundLabel = stored.notificationSoundLabel,
                )
            } else {
                filters.add(filter)
            }
            config.customFilters = filters
            config.activeFilterId = filter.id
            buildFilterChips()
            applyActiveFilter()
        }
    }

    /**
     * The colours-and-sound sheet, and the ringtone picker it cannot open itself.
     *
     * A ringtone picker is an activity result, so the sheet hands the request back here and
     * [pendingSoundPick] holds the sheet's callback until [onActivityResult] fires. The sheet
     * stays up while the picker is in front of it, so the callback is still live when it
     * returns and the row updates in place rather than the whole sheet being rebuilt.
     */
    private fun customizeFilter(filter: MessageFilter, onSaved: (MessageFilter) -> Unit = {}) {
        textoFilterCustomizer(
            filter = filter,
            onPickSound = { current, onPicked ->
                pendingSoundPick = onPicked
                val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, getString(R.string.filter_sound))
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                    putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                    putExtra(
                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                        current?.let { Uri.parse(it) }
                    )
                }
                try {
                    startActivityForResult(intent, PICK_FILTER_SOUND_REQUEST)
                } catch (e: Exception) {
                    pendingSoundPick = null
                    showErrorToast(e)
                }
            }
        ) { updated ->
            val filters = config.customFilters.toMutableList()
            val index = filters.indexOfFirst { it.id == updated.id }
            if (index >= 0) {
                filters[index] = updated
                config.customFilters = filters
                buildFilterChips()
            }
            // Back to the editor underneath, so its preview shows what was just saved rather
            // than what the filter looked like when it opened.
            onSaved(updated)
        }
    }

    private fun deleteFilter(filter: MessageFilter) {
        val question = getString(R.string.delete_filter_confirmation, filter.label)
        textoConfirmDialog(question, isDestructive = true) {
            config.customFilters = config.customFilters.filter { it.id != filter.id }
            if (config.activeFilterId == filter.id) {
                config.activeFilterId = MessageFilter.ID_ALL
            }
            buildFilterChips()
            applyActiveFilter()
        }
    }

    /**
     * The wordmark is the brand's own artwork and is left at its own colours: the tonality
     * strip deliberately does not reach it. Every other accent surface follows the strip,
     * but the logo is the one mark that has to stay the same object whatever the rest of the
     * app is wearing, so the hue rotation that used to be applied here is gone.
     *
     * What remains is a legibility correction, and only on a dark ground. The mark is deep
     * blue on white; on the dark skins that navy sits within a few percent of the background
     * and the word all but disappears while the badge still reads. Scaling RGB up lifts the
     * lettering into a legible blue and leaves the white bubble at white, since the channels
     * were already clamped there. It is a brightness change, not a hue change -- the mark
     * stays on-brand, it is only exposed differently for the ground it sits on.
     */
    /**
     * The brand mark leading the search capsule: the launcher badge itself, so the header and
     * the home-screen icon are one mark. It needs no brightness lift on the dark skins the
     * wordmark did, because the badge carries its own blue ground.
     */
    private fun styleAppTitle() = binding.textoTitle.apply {
        updateLayoutParams<LinearLayout.LayoutParams> {
            width = LOGO_HEIGHT_DP.getScaledPx()
            height = LOGO_HEIGHT_DP.getScaledPx()
        }
        colorFilter = null
    }

    /**
     * How many conversations each chip currently holds. "All" is the whole list; every other
     * filter reuses the same predicate the list itself is filtered with, so a chip's badge can
     * never disagree with what tapping it shows.
     */
    private fun filterCounts(filters: List<MessageFilter>): Map<String, Int> =
        filters.associate { filter ->
            filter.id to if (filter.id == MessageFilter.ID_ALL) {
                allConversations.size
            } else {
                allConversations.count {
                    com.texto.sms.helpers.MessageClassifier.matches(it, filter, contactPhoneNumbers)
                }
            }
        }

    /**
     * Draws the sliding halo under the chosen filter chip.
     *
     * An ItemDecoration rather than a view: the chips are a RecyclerView, so a selector view
     * would be recycled along with them and would have to be torn down and rebuilt every
     * time the row scrolled. Drawing underneath in the parent's own coordinates sidesteps
     * that -- the halo follows the chip while the row scrolls, for free.
     */
    /**
     * [activePosition] rather than a hardcoded reference to the home screen's adapter: the
     * search panel shows the same chips from a second adapter, and with the halo bound to the
     * first one that row drew no selection at all.
     *
     * Drawn in onDrawOver, so it lands on top of the chips rather than behind them. Every
     * chip carries the header capsule's glass now, the chosen one included, and a halo drawn
     * underneath that would simply be covered up. Its fill is the accent at .16, which over
     * a label already painted in the accent changes nothing that can be measured.
     */
    private inner class FilterHaloDecoration(
        private val activePosition: () -> Int,
    ) : androidx.recyclerview.widget.RecyclerView.ItemDecoration() {
        private var halo: com.texto.sms.helpers.TextoHalo? = null
        private val bounds = android.graphics.RectF()
        private var lastPosition = -1

        override fun onDrawOver(
            canvas: android.graphics.Canvas,
            parent: androidx.recyclerview.widget.RecyclerView,
            state: androidx.recyclerview.widget.RecyclerView.State,
        ) {
            val position = activePosition()
            val paint = halo ?: com.texto.sms.helpers.TextoHalo(parent).also { halo = it }

            val accent = config.accentGradientStart
            val density = resources.displayMetrics.density
            paint.cornerRadius = CHIP_PILL_RADIUS_DP * density
            paint.setColors(
                fill = accent.withAlpha(0.16f),
                stroke = accent.withAlpha(0.25f),
                // Scaled, for the same reason the bars are: the halo outlines a chip whose
                // own hairline follows the UI-scale slider, and a fixed density left the two
                // drifting apart as the slider moved.
                strokeWidthPx = 1.getScaledPx().toFloat().coerceAtLeast(1f)
            )

            val child = (0 until parent.childCount)
                .map { parent.getChildAt(it) }
                .firstOrNull { parent.getChildAdapterPosition(it) == position }

            if (position < 0 || child == null) {
                // Scrolled off rather than gone: keep the halo where it was so it is still
                // there, in place, when the row scrolls back. Only a row with no selection
                // at all clears it.
                if (position < 0) {
                    lastPosition = -1
                    paint.hide()
                }
            } else {
                bounds.set(
                    child.left + child.translationX,
                    child.top.toFloat(),
                    child.right + child.translationX,
                    child.bottom.toFloat()
                )
                when {
                    // The selection moved: travel to it.
                    position != lastPosition -> {
                        lastPosition = position
                        paint.moveRect(bounds)
                    }
                    // Mid-travel: leave it alone, or the next frame of a scrolling row
                    // would snap it to the destination and the movement would be lost.
                    paint.isTravelling -> Unit
                    // Same chip, new place -- the row is scrolling. Follow it exactly,
                    // with no animation and no redraw request of its own.
                    else -> paint.snapTo(bounds)
                }
            }
            paint.draw(canvas)
        }
    }

    private fun styleFilterChip(
        views: com.texto.sms.adapters.FilterChipViews,
        filterId: String,
        isActive: Boolean,
    ) {
        val chip = views.root
        val density = resources.displayMetrics.density
        val stroke = 1.getScaledPx()

        // Straight from the design's chip rule: a full pill (`border-radius: 999px`), the
        // active one filled with the accent *gradient* -- `var(--grad)`, not a flat stop --
        // under a glow, the rest a glass wash behind the shared `--divider` hairline.
        // The chosen chip is marked by the halo behind it now -- the same soft lozenge the
        // bottom capsule uses -- so it carries the accent as ink rather than as a fill. Two
        // selectors drawn the same way read as one idea, and the halo can then slide between
        // chips instead of a solid fill blinking from one to the next.
        val textColor = if (isActive) {
            config.accentGradientStart
        } else {
            config.mainTextColor.withAlpha(0.58f)
        }
        val chipRadius = CHIP_PILL_RADIUS_DP * density
        // Exactly the recipe setupOverlayBars() paints the header capsule with: the bar
        // colour, the bar rim, and the user's glass setting rather than a fixed opacity.
        // The chips sit directly under that capsule, so anything else read as a second,
        // differently-frosted material -- and the settings slider moved one and not the
        // other, which is the part that showed.
        //
        // The chosen chip carries it too. It used to be given no background at all, so the
        // one chip you were most likely to look at was the only surface on the row that was
        // not glass and the only one the transparency slider did nothing to: measured, its
        // neighbours were the header's own #171B22 while it was bare accent wash over the
        // page. The halo marks it instead, drawn over the glass rather than under it.
        chip.background = com.texto.sms.helpers.TextoGlass.bar(
            tint = if (config.topBarColor != 0) config.topBarColor else Color.BLACK,
            cornerRadius = chipRadius,
            opacity = config.glassOpacity / 100f,
            strokeWidthPx = stroke,
            rimAlpha = 0.20f
        )
        val horizontal = if (filterId == com.texto.sms.adapters.FilterChipsAdapter.ADD_CHIP_ID) {
            18.getScaledPx()
        } else {
            // The design's own 8px/16px chip padding.
            16.getScaledPx()
        }
        chip.setPadding(horizontal, 8.getScaledPx(), horizontal, 8.getScaledPx())
        chip.alpha = 1f
        chip.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        // Flat. The lift belonged to the solid gradient chip; over a halo it would cast a
        // shadow onto the very thing marking the selection.
        chip.elevation = 0f

        views.label.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.78f))
            setTextColor(textColor)
            typeface = typefaceFor(
                if (isActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
            )
        }

        // The count sits in its own pill: `--pill-on` over the active gradient, `--pill` over
        // the glass. Both are a wash of the chip's own ink, which is what those two tokens
        // resolve to on either ground.
        views.count.apply {
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.62f))
            setTextColor(textColor)
            typeface = typefaceFor(android.graphics.Typeface.BOLD)
            val padH = 6.getScaledPx()
            setPadding(padH, 2.getScaledPx(), padH, 2.getScaledPx())
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 100f * density
                setColor(textColor.withAlpha(if (isActive) 0.25f else 0.10f))
            }
        }
    }

    private fun selectFilter(filter: MessageFilter) {
        if (activeFilter.id == filter.id) return
        activeFilter = filter
        config.activeFilterId = filter.id
        applyActiveFilter()
    }

    private fun applyActiveFilter() {
        clearPendingScroll()
        val filters = currentFilters()
        filterChipsAdapter?.submitFilters(filters, activeFilter.id, filterCounts(filters))
        submitFilteredConversations()
        binding.conversationsList.scrollToPosition(0)
    }

    private fun setupOneTimeViews() {
        binding.noConversationsPlaceholder2.setOnClickListener { launchNewConversation() }
        styleEmptyState()
        setupPullToRefresh()

        // The header's middle looks like a field but is not one: it opens the search panel,
        // which owns the real input. Sighted users get that from the tap; a screen reader was
        // told only the text, so it announced a label with nothing to do. Announced as a
        // button now, which is what it behaves like.
        ViewCompat.setAccessibilityDelegate(
            binding.textoHeaderSearch,
            object : androidx.core.view.AccessibilityDelegateCompat() {
                override fun onInitializeAccessibilityNodeInfo(
                    host: View,
                    info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat,
                ) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = android.widget.Button::class.java.name
                }
            }
        )
        // The header's single control. The overflow menu it replaced offered archived
        // conversations, settings and about; all three live inside settings now, so the
        // gear opens that rather than a menu with one real entry left in it.
        binding.textoMenuBtn.setOnClickListener {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }
        buildFilterChips()

        // Once the user scrolls themselves, the remembered position is stale.
        binding.conversationsList.addOnScrollListener(
            object : androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
                override fun onScrollStateChanged(
                    recyclerView: androidx.recyclerview.widget.RecyclerView,
                    newState: Int,
                ) {
                    if (newState == androidx.recyclerview.widget.RecyclerView.SCROLL_STATE_DRAGGING) {
                        clearPendingScroll()
                    }
                }

                // The FAB gives the list its width back while you read down it, and offers
                // its label again the moment you turn back towards the top.
                override fun onScrolled(
                    recyclerView: androidx.recyclerview.widget.RecyclerView,
                    dx: Int,
                    dy: Int,
                ) {
                    if (dy > 0 && binding.textoFab.isExtended) {
                        binding.textoFab.shrink()
                    } else if (dy < 0 && !binding.textoFab.isExtended) {
                        binding.textoFab.extend()
                    }
                }
            }
        )

        val searchAnim = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom)
        binding.textoFab.startAnimation(searchAnim)

        binding.textoSearchInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())
    }

    /**
     * Swipe down to re-read the threads. The app refreshes itself on every message and on
     * every resume, but there was no way to ask it to: when a message did not show up, the
     * only move left was to force-stop the app.
     *
     * The spinner is dropped below the header and the chip row, which float over the list, so
     * it does not come down behind them.
     */
    private fun setupPullToRefresh() {
        binding.conversationsRefresh.apply {
            setOnRefreshListener {
                syncConversations(cached = ArrayList(allConversations))
            }
            setColorSchemeColors(config.accentGradientStart)
            setProgressBackgroundColorSchemeColor(config.mainBackgroundColor)
        }
    }

    /** The frosted bars float over the list, so the spinner has to start below them. */
    private fun setRefreshSpinnerOffset(topInset: Int) {
        val start = topInset
        val end = topInset + 64.getScaledPx()
        binding.conversationsRefresh.setProgressViewOffset(false, start, end)
    }

    private fun getCachedConversations(isManualReorder: Boolean = false) {
        // Captured now, on the main thread, so a later top-level fetch starting before this
        // one's background work finishes can be detected and this one discarded below.
        val myGeneration = ++conversationLoadGeneration
        ensureBackgroundThread {
            val conversations = try {
                conversationsDB.getNonArchived().toMutableList() as ArrayList<Conversation>
            } catch (_: Exception) { ArrayList() }

            val archived = try { conversationsDB.getAllArchived() } catch (_: Exception) { listOf() }
            // Loaded on this first (cached) pass as well, not just in syncConversations, so
            // the "Contacts only" chip filters correctly the instant the list appears rather
            // than coming up empty until the slower telephony sync finishes.
            val earlyContactNumbers =
                if (contactPhoneNumbers.isEmpty()) getContactNumbersSnapshot() else null

            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                // A newer fetch cycle already started (e.g. another message arrived, or the
                // activity resumed again) -- applying this older snapshot could stomp fresher
                // data that already landed, so this whole cycle is abandoned.
                if (myGeneration != conversationLoadGeneration) return@runOnUiThread
                archivedConversations = archived
                earlyContactNumbers?.let { contactPhoneNumbers = it }
                setupConversations(conversations, cached = true, isManualReorder = isManualReorder)
                syncConversations(
                    cached = (conversations + archived).toMutableList() as ArrayList<Conversation>,
                    isManualReorder = isManualReorder,
                    generation = myGeneration
                )
                applyOutlines()
            }

            conversations.forEach { clearExpiredScheduledMessages(it.threadId) }
        }
    }

    private fun syncConversations(cached: ArrayList<Conversation>, isManualReorder: Boolean = false, generation: Int? = null) {
        // Reuses the caller's generation when part of the same fetch cycle (from
        // getCachedConversations); otherwise this is itself a fresh top-level fetch.
        val myGeneration = generation ?: ++conversationLoadGeneration
        ensureBackgroundThread {
            val conversations = getConversations()
            insertOrUpdateConversations(conversations)
            // The provider is the authority on which threads still exist, and this is the one
            // pass that has just asked it, so it is also the only place that can tell a
            // deleted thread from one that simply has not been cached yet.
            pruneVanishedConversations(conversations.mapTo(HashSet()) { it.threadId })
            val all = conversationsDB.getNonArchived() as ArrayList<Conversation>
            // One query for every thread's newest message, on the same background pass
            // that loads the list. Rows then bind without touching the database.
            val snippets = getLatestSnippets()
            // The address book itself is the source for the "مخاطبین" filter. It used to be
            // built from the Fossify private-contacts provider alone, which is empty unless
            // that separate app is installed, so the filter matched nothing and always came
            // up empty.
            val contactNumbers = getContactNumbersSnapshot()
            val contactsForFilters = getContactsWithNamesSnapshot()
            runOnUiThread {
                if (!isFinishing && !isDestroyed && myGeneration == conversationLoadGeneration) {
                    contactPhoneNumbers = contactNumbers
                    cachedContactsForFilters = contactsForFilters
                    getOrCreateConversationsAdapter().setLatestSnippets(snippets)
                    setupConversations(all, isManualReorder = isManualReorder)
                    applyOutlines()
                }
            }
        }
    }

    private fun getOrCreateConversationsAdapter(): ConversationsAdapter {
        var curr = binding.conversationsList.adapter
        if (curr == null) {
            hideKeyboard()
            curr = ConversationsAdapter(
                activity = this,
                recyclerView = binding.conversationsList,
                onRefresh = { notifyDatasetChanged() },
                itemClick = { handleConversationClick(it) }
            )
            binding.conversationsList.adapter = curr
            if (areSystemAnimationsEnabled) binding.conversationsList.scheduleLayoutAnimation()
        }
        return curr as ConversationsAdapter
    }

    private fun setupConversations(conversations: ArrayList<Conversation>, cached: Boolean = false, isManualReorder: Boolean = false) {
        val useNewUi = config.useNewUi
        val currentAdapter = binding.conversationsList.adapter as? ConversationsAdapter
        if (currentAdapter != null && currentAdapter.itemCount > 0) {
             if (lastUsedNewUi != null && lastUsedNewUi != useNewUi) {
                 binding.conversationsList.adapter = null
                 while (binding.conversationsList.itemDecorationCount > 0) {
                     binding.conversationsList.removeItemDecorationAt(0)
                 }
                 dragTouchHelper?.attachToRecyclerView(null)
                 dragTouchHelper = null
             }
        }
        lastUsedNewUi = useNewUi

        val sorted = if (useNewUi) {
            val allSortedByDate = conversations.sortedByDescending { it.date }
            if (allSortedByDate.isEmpty()) {
                allSortedByDate
            } else {
                // "Pin to top" wrote to config.pinnedConversations and nothing here ever read
                // it: the two most recent conversations were taken unconditionally, so a
                // pinned conversation only ever surfaced by coincidence of also being recent.
                // Pulling pinned threads out first, ahead of the recency/manual sort below,
                // is what the action's own name promises.
                val pinnedIds = config.pinnedConversations
                val pinned = allSortedByDate.filter { pinnedIds.contains(it.threadId.toString()) }
                val unpinned = allSortedByDate.filter { !pinnedIds.contains(it.threadId.toString()) }

                val top2 = unpinned.take(2)
                val remaining = unpinned.filter { conv -> !top2.any { it.threadId == conv.threadId } }

                val sortedPills = when (config.contactSortingMode) {
                    1 -> remaining.sortedBy { it.title.lowercase() }
                    2 -> remaining.sortedByDescending { it.date }
                    else -> {
                        val manualOrder = config.conversationOrder.split(",").filter { it.isNotEmpty() }.map { it.toLong() }
                        val manuallySorted = ArrayList<Conversation>()
                        manualOrder.forEach { id ->
                            remaining.find { it.threadId == id }?.let { manuallySorted.add(it) }
                        }
                        remaining.forEach { conv ->
                            if (!manuallySorted.any { it.threadId == conv.threadId }) {
                                manuallySorted.add(conv)
                            }
                        }
                        manuallySorted
                    }
                }
                pinned + top2 + sortedPills
            }
        } else {
            conversations.sortedWith(
                compareByDescending<Conversation> { config.pinnedConversations.contains(it.threadId.toString()) }
                    .thenByDescending { it.date }
            )
        }.toMutableList() as ArrayList<Conversation>

        // Gated on appRunCount == 1 before, but App.onCreate forces appRunCount to 100 on
        // every launch, so the spinner never appeared and an empty cache showed the bare
        // "no conversations" text while the sync was still running. Key it on the thing that
        // actually matters: an empty cached pass means results are still on their way.
        if (cached && conversations.isEmpty()) {
            showOrHideProgress(true)
        } else {
            showOrHideProgress(false)
            showOrHidePlaceholder(conversations.isEmpty())
        }

        // Every row is now a full-width card, so a single column is used in both UI modes.
        if (binding.conversationsList.layoutManager !is com.texto.sms.views.TextoLinearLayoutManager) {
            binding.conversationsList.layoutManager =
                com.texto.sms.views.TextoLinearLayoutManager(this)
        }

        // No per-position offsets: the card's own margin is the only gap, so the spacing
        // between every pair of chats is identical.
        if (useNewUi && dragTouchHelper == null) {
            val callback = com.texto.sms.helpers.ModernDragCallback(getOrCreateConversationsAdapter())
            dragTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(callback)
        }

        allConversations = sorted
        refreshFilterChipCounts()
        submitFilteredConversations(cached, isManualReorder)
    }

    /**
     * Re-labels the chips once the conversations behind them are known.
     *
     * [filterCounts] reads `allConversations`, and the chips are built in `onResume` -- which
     * on a cold start runs long before the list has loaded. The counts were computed once
     * against an empty list and never again, so every chip opened on 0: measured on device,
     * "All" read 0 with 393 conversations on screen, and only corrected itself after leaving
     * the screen and coming back, because that ran `setupFilterChips` a second time.
     */
    private fun refreshFilterChipCounts() {
        val filters = currentFilters()
        filterChipsAdapter?.submitFilters(filters, activeFilter.id, filterCounts(filters))
        searchFilterChipsAdapter?.submitFilters(filters, searchFilter.id, filterCounts(filters))
    }

    /**
     * Reordering writes the manual order back from the visible list, so dragging is only
     * available while the unfiltered list is on screen.
     */
    private fun submitFilteredConversations(cached: Boolean = false, isManualReorder: Boolean = false) {
        val showingEverything = activeFilter.id == MessageFilter.ID_ALL
        val filtered = if (showingEverything) {
            allConversations
        } else {
            allConversations.filter {
                com.texto.sms.helpers.MessageClassifier.matches(it, activeFilter, contactPhoneNumbers)
            }.toMutableList() as ArrayList<Conversation>
        }

        val dragAllowed = config.useNewUi && showingEverything
        dragTouchHelper?.attachToRecyclerView(if (dragAllowed) binding.conversationsList else null)
        getOrCreateConversationsAdapter().itemTouchHelper = if (dragAllowed) dragTouchHelper else null

        try {
            getOrCreateConversationsAdapter().apply {
                updateConversations(filtered, shouldSuppressStateRestoration = isManualReorder) {
                    restoreScrollPosition()
                    if (!cached) showOrHidePlaceholder(currentList.isEmpty())
                }
            }
        } catch (_: Exception) {}
    }

    private fun showOrHideProgress(show: Boolean) {
        if (show) {
            binding.conversationsProgressBar.show()
            binding.noConversationsPlaceholder.beVisible()
            binding.noConversationsPlaceholder.text = getString(R.string.loading_messages)
        } else {
            binding.conversationsProgressBar.hide()
            binding.noConversationsPlaceholder.beGone()
            binding.conversationsRefresh.isRefreshing = false
        }
        // No mark while loading: it belongs to "there is nothing here", not to "not yet".
        binding.emptyStateIcon.beGone()
    }

    private fun showOrHidePlaceholder(show: Boolean) {
        val isFiltered = activeFilter.id != MessageFilter.ID_ALL
        binding.noConversationsPlaceholder.beVisibleIf(show)
        binding.noConversationsPlaceholder.text = if (isFiltered) {
            getString(R.string.no_conversations_in_filter)
        } else {
            getString(R.string.no_conversations_found)
        }
        binding.noConversationsPlaceholder2.beVisibleIf(show && !isFiltered)
        // An empty screen was two lines of text in the middle of nothing. The mark gives it a
        // centre, and it comes and goes with the message it belongs to.
        binding.emptyStateIcon.beVisibleIf(show)
        binding.conversationsRefresh.isRefreshing = false
    }

    /**
     * The empty state, painted from the theme: a soft accent mark, and a button that looks
     * like one. "Start a conversation" used to be plain text with a ripple behind it, which
     * read as a third line of the message rather than as the one thing to do here.
     */
    private fun styleEmptyState() = binding.apply {
        emptyStateIcon.applyColorFilter(config.accentGradientStart.withAlpha(0.35f))
        val size = 88.getScaledPx()
        emptyStateIcon.updateLayoutParams<LinearLayout.LayoutParams> {
            width = size
            height = size
        }

        noConversationsPlaceholder.apply {
            setTextColor(config.mainTextColor.withAlpha(0.68f))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.0f))
            typeface = typefaceFor(android.graphics.Typeface.NORMAL)
        }

        noConversationsPlaceholder2.apply {
            setTextColor(config.accentInkColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.95f))
            typeface = typefaceFor(android.graphics.Typeface.BOLD)
            minHeight = 48.getScaledPx()
            minimumWidth = 180.getScaledPx()
            val padH = 24.getScaledPx()
            val padV = 12.getScaledPx()
            setPadding(padH, padV, padH, padV)
            background = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = 16f * resources.displayMetrics.density
                setColor(config.accentGradientStart)
            }
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            ViewCompat.setAccessibilityDelegate(
                this,
                object : androidx.core.view.AccessibilityDelegateCompat() {
                    override fun onInitializeAccessibilityNodeInfo(
                        host: View,
                        info: androidx.core.view.accessibility.AccessibilityNodeInfoCompat,
                    ) {
                        super.onInitializeAccessibilityNodeInfo(host, info)
                        info.className = android.widget.Button::class.java.name
                    }
                }
            )
        }
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun notifyDatasetChanged(isManualReorder: Boolean = false) {
        getOrCreateConversationsAdapter().safeNotifyDataSetChanged(shouldSuppressStateRestoration = isManualReorder)
    }

    private fun handleConversationClick(any: Any) {
        val conv = any as Conversation
        Intent(this, ThreadActivity::class.java).apply {
            putExtra(THREAD_ID, conv.threadId)
            putExtra(THREAD_TITLE, conv.title)
            startActivity(this)
        }
    }

    private fun launchNewConversation() {
        hideKeyboard()
        startActivity(Intent(this, NewConversationActivity::class.java))
    }

    /**
     * Runs whatever combination of the three search inputs is set: the typed text, the filter
     * chip and the date range. Any one of them alone narrows the results, and they stack --
     * so a date on its own lists everything from that window, and a filter on its own lists
     * that chip's threads, without a query being typed at all.
     */
    private fun searchTextChanged(text: String) {
        lastSearchedText = text

        val hasQuery = text.length >= 2
        val hasFilter = searchFilter.id != MessageFilter.ID_ALL
        val hasDate = !searchDateRange.isAny

        if (!hasQuery && !hasFilter && !hasDate) {
            // Nothing to narrow by: show the prompt rather than dumping every message.
            binding.searchResultCount.text = ""
            showSearchResults(emptyList(), emptyList(), text, isPrompting = true)
            return
        }

        val range = searchDateRange
        val filter = searchFilter

        ensureBackgroundThread {
            // The provider holds every message on the device; the Room table only has the
            // ones that arrived since install, so both are searched and merged.
            val messages = if (hasQuery) {
                val fromProvider = searchMessagesInProvider(text)
                val fromCache = messagesDB.getMessagesWithText("%$text%")
                (fromProvider + fromCache).distinctBy { it.id }
            } else {
                // No query, so there is nothing to match message bodies against; the result
                // set is built from conversations alone.
                emptyList()
            }.filter { range.contains(it.date) }.sortedByDescending { it.date }

            // Room's LIKE has the same Persian problem as the provider's, so the cached
            // titles are re-filtered here rather than trusted: a thread named with an Arabic
            // yeh was invisible to a query typed with the Farsi one. The wide query stays as
            // the cheap first pass; this narrows it correctly.
            val conversations = conversationsDB
                .getConversationsWithText("%%")
                .filter { conversation ->
                    val matchesText = !hasQuery ||
                        conversation.title.containsPersian(text) ||
                        conversation.phoneNumber.containsPersian(text)
                    matchesText && range.contains(conversation.date)
                }

            // A filter narrows both halves to the threads that chip would show.
            val allowedThreads = if (!hasFilter) {
                null
            } else {
                allConversations
                    .filter {
                        com.texto.sms.helpers.MessageClassifier
                            .matches(it, filter, contactPhoneNumbers)
                    }
                    .map { it.threadId }
                    .toSet()
            }

            val visibleMessages = allowedThreads
                ?.let { allowed -> messages.filter { it.threadId in allowed } }
                ?: messages
            val visibleConversations = allowedThreads
                ?.let { allowed -> conversations.filter { it.threadId in allowed } }
                ?: conversations

            if (text == lastSearchedText) {
                showSearchResults(visibleMessages, visibleConversations, text)
            }
        }
    }

    /**
     * Makes the chat list run *behind* the two frosted bars instead of starting below them.
     *
     * This is what every previous attempt at "make the bars glassy" was missing: the bars were
     * translucent, but nothing was ever drawn behind them, so there was nothing to see through.
     * Two things caused that -- the content root carried appbar_scrolling_view_behavior, which
     * offsets it below the app bar, and the list was laid out with layout_below="filter_bar".
     * Both are gone from the layout; the list now fills the window from the very top and simply
     * pads its *content* down by the height of the two bars, with clipToPadding=false so rows
     * scroll up into that padding and pass under the glass, Telegram-style.
     *
     * The heights are only known after layout (the top bar includes the status-bar inset and
     * scales with the UI-scale setting), so they are measured rather than hard-coded.
     */
    private fun setupOverlayBars() {
        val appbar = binding.mainAppbar
        val filterBar = binding.filterBar

        appbar.doOnLayout {
            if (isFinishing || isDestroyed) return@doOnLayout
            // The chips carry their own vertical padding, so this gap is only what separates
            // one floating surface from the next. At 8dp on both sides of the row -- on top
            // of that padding -- the strip between the header and the first card was mostly
            // empty screen.
            val barsGap = 5.getScaledPx()
            filterBar.updateLayoutParams<RelativeLayout.LayoutParams> {
                topMargin = appbar.height + barsGap
            }
            filterBar.doOnLayout {
                if (isFinishing || isDestroyed) return@doOnLayout
                val inset = appbar.height + barsGap + filterBar.height + barsGap
                // Only the conversation list. The search results sit below the search
                // panel, which is a view in the layout rather than a floating bar, so the
                // same inset there is counted twice: it opened the results 630px down the
                // screen with nothing in the gap.
                binding.conversationsList.apply {
                    if (paddingTop != inset) {
                        setPadding(paddingLeft, inset, paddingRight, paddingBottom)
                        scrollToPosition(0)
                    }
                }
                // The loading bar and the pull-to-refresh spinner both belong just under the
                // chip row: drawn at the very top they were behind the frosted header, which
                // is the whole reason the progress bar was never seen.
                binding.conversationsProgressBar.updateLayoutParams<RelativeLayout.LayoutParams> {
                    topMargin = inset
                }
                setRefreshSpinnerOffset(inset)
                // The empty-state text is already positioned with layout_below="filter_bar",
                // so it follows the bar without any extra offset here.
            }
        }

        // The same idea at the other end. The FAB floats over the list rather than sitting in
        // the layout, so nothing reserves its height: without this the last conversation
        // could not be scrolled clear of it. (It was the nav pill's height before the pill was
        // retired; measured then, the bottom card sat permanently half under it.)
        val bottomControl = if (AppThemes.isRadiant(config.appTheme)) binding.radiantNavContainer else binding.textoFab
        bottomControl.doOnLayout { pill ->
            if (isFinishing || isDestroyed) return@doOnLayout
            val margin = (pill.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin ?: 0
            // The pill's own bottom margin already clears the gesture bar, so the system inset
            // that setupEdgeToEdge adds on top of this would be counted twice: take the
            // margin's own share back out of it.
            val systemBottom = ViewCompat.getRootWindowInsets(pill)
                ?.getInsets(WindowInsetsCompat.Type.systemBars())?.bottom ?: 0
            val room = (pill.height + margin + 5.getScaledPx() - systemBottom).coerceAtLeast(0)
            binding.conversationsList.setBaseBottomPadding(room)
            binding.searchResultsList.setBaseBottomPadding(room)
        }
    }

    private fun setupSearchEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.textoSearchInput) { _, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomInset = if (imeInsets.bottom > 0) imeInsets.bottom else systemBars.bottom
            binding.textoFab.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                bottomMargin = bottomInset + 16.getScaledPx()
            }
            insets
        }
    }

    private fun showSearchResults(
        messages: List<Message>,
        conversations: List<Conversation>,
        searchedText: String,
        isPrompting: Boolean = false,
    ) {
        val results = ArrayList<SearchResult>()
        conversations.forEach { conv ->
            val date = (conv.date * 1000L).formatUiDateOrTime()
            results.add(SearchResult(messageId = -1, title = conv.title, snippet = conv.phoneNumber, date = date, threadId = conv.threadId, photoUri = conv.photoUri))
        }
        messages.sortedByDescending { it.id }.forEach { msg ->
            var recipient = msg.senderName
            if (recipient.isEmpty() && msg.participants.isNotEmpty()) {
                recipient = TextUtils.join(", ", msg.participants.map { it.name })
            }
            val date = (msg.date * 1000L).formatUiDateOrTime()
            results.add(SearchResult(messageId = msg.id, title = recipient, snippet = msg.body, date = date, threadId = msg.threadId, photoUri = msg.senderPhotoUri))
        }
        runOnUiThread {
            if (isFinishing || isDestroyed) return@runOnUiThread

            // Two different empty states: "type something" before anything is narrowed, and
            // "nothing matched" once it has been.
            binding.searchPlaceholder.beGoneIf(results.isNotEmpty())
            binding.searchPlaceholder2.beGone()
            binding.searchPlaceholder.text = getString(
                if (isPrompting) R.string.search_type_more else R.string.search_no_results
            )
            binding.searchResultCount.text = if (isPrompting) {
                ""
            } else {
                resources.getQuantityString(
                    R.plurals.search_results_count,
                    results.size,
                    results.size.toString().toUiDigits()
                )
            }

            val curr = binding.searchResultsList.adapter
            if (curr == null) {
                SearchResultsAdapter(this, results, binding.searchResultsList, searchedText) {
                    hideKeyboard()
                    Intent(this, ThreadActivity::class.java).apply {
                        putExtra(THREAD_ID, (it as SearchResult).threadId)
                        putExtra(THREAD_TITLE, it.title)
                        putExtra(SEARCHED_MESSAGE_ID, it.messageId)
                        startActivity(this)
                    }
                }.also { binding.searchResultsList.adapter = it }
            } else {
                (curr as SearchResultsAdapter).updateItems(results, searchedText)
            }
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun refreshConversations(event: Events.RefreshConversations) {
        if (isActivityVisible && !isFinishing && !isDestroyed) {
            // Filter chips are only built once in setupOneTimeViews(); rebuild them here too
            // so edits made elsewhere (e.g. "add to filter" from the selection menu) are
            // picked up instead of filtering against a stale, senders-less MessageFilter.
            if (isInitialized) buildFilterChips()
            initMessenger(isManualReorder = event.isManualReorder)
        }
        // Nothing to remember when the activity is hidden: onResume() always runs a full
        // fetch through initMessenger(), so whatever arrived meanwhile is picked up anyway.
    }

    private fun checkWhatsNewDialog() {}

    private fun applyOutlines() = binding.apply {
        val density = resources.displayMetrics.density
        val isNewUi = config.useNewUi
        
        if (config.topBarOutline && isNewUi) {
            // The bar it traces is a capsule, so this is half the painted height too, not a
            // fixed corner. The nav pill's own outline below already used a pill radius.
            val r26 = (binding.mainAppbar.height - statusBarInsetOf(binding.mainAppbar))
                .coerceAtLeast(0) / 2f
            val thickness = config.topBarOutlineThickness
            val thickStroke = (thickness * density).toInt()
            val outline = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setStroke(thickStroke, config.topBarOutlineColor)
                setColor(Color.TRANSPARENT)
                cornerRadii = FloatArray(8) { r26 }
            }
            val drawable = android.graphics.drawable.LayerDrawable(arrayOf(outline))
            // Sits exactly on the painted bar, which is now rounded all round and starts
            // below the status bar rather than behind it. The side inset is the bar's own:
            // without it this optional outline was drawn across the full screen width while
            // the capsule it is meant to trace stopped 16dp short of each edge.
            val side = (com.texto.sms.helpers.TextoGlass.FLOATING_BAR_INSET_DP * density).toInt()
            drawable.setLayerInset(0, side, statusBarInsetOf(binding.mainAppbar), side, 0)
            binding.mainAppbar.foreground = drawable
        } else {
            binding.mainAppbar.foreground = null
        }

        // The search-bar outline setting traced the retired bottom pill. That view is gone,
        // and this screen's own search control is the header capsule the top-bar outline
        // already traces, so there is nothing left here for it to draw on. The setting still
        // applies where a search bar actually exists.
    }

    // ---- the live appearance editor ----------------------------------------------------------

    /** True while the editor is open, which is also what the list's rows check before acting. */
    private var isEditingAppearance = false

    /**
     * The app's own colours as they were when the editor opened, to put back on discard.
     *
     * Every pick writes straight to config so the screen previews itself, which is the same
     * trick the colour picker already plays for a single row -- so "discard" has to mean
     * putting these four back, not undoing a list of edits.
     */
    private var appearanceSnapshot: IntArray? = null

    /**
     * Editing the app's chrome where it lives: the capsule at the top, the pill at the bottom
     * and a conversation card are all tapped on the screen they are part of, rather than named
     * in a settings list that cannot show them.
     *
     * Everything here is app-wide by nature -- one bar, one card style, every screen -- so this
     * editor has no per-conversation scope to ask about. That question belongs to the thread's
     * own editor, where the thing being restyled is one conversation.
     */
    private fun openAppearanceEditor() {
        if (isEditingAppearance) return
        isEditingAppearance = true
        appearanceSnapshot = intArrayOf(
            config.topBarColor,
            config.topBarTextColor,
            config.recentColor,
            config.inputBarBackgroundColor,
        )
        styleAppearanceBar()
        binding.appearanceBar.beVisible()
        setHomeFunctionsEnabled(false)
        // The same breathing ring the thread's editor puts on its bubbles, on the three things
        // this screen can recolour: the header, the pill, and the cards on screen right now.
        editPulse.start {
            buildList {
                add(binding.mainToolbar)
                binding.conversationsList.children.forEach { row ->
                    row.findViewById<View>(R.id.recent_frame)?.let { add(it) }
                }
            }
        }
    }

    private val editPulse by lazy { TextoEditPulse(this) }

    private fun closeAppearanceEditor(keep: Boolean) {
        if (!isEditingAppearance) return
        if (!keep) {
            appearanceSnapshot?.let { (bar, barInk, card, inputBar) ->
                config.topBarColor = bar
                config.topBarTextColor = barInk
                config.recentColor = card
                config.inputBarBackgroundColor = inputBar
            }
        }
        appearanceSnapshot = null
        isEditingAppearance = false
        editPulse.stop()
        binding.appearanceOverlay.hideInlineBars()
        binding.appearanceBar.beGone()
        setHomeFunctionsEnabled(true)
        repaintHome()
    }

    /** Repaints every surface this editor can touch, so a pick shows up immediately. */
    private fun repaintHome() {
        applyCustomColors()
        setupOverlayBars()
        setupHomeActions()
        styleEmptyState()
        updateAppFonts(binding.root)
        binding.conversationsList.adapter?.notifyDataSetChanged()
        if (isEditingAppearance) styleAppearanceBar()
    }

    /**
     * Turns the list's own behaviour off while the editor is open. A tap on a card here picks
     * a colour; it must not also open the conversation, and the gear must not walk out of the
     * editor into settings.
     */
    private fun setHomeFunctionsEnabled(enabled: Boolean) = binding.apply {
        listOf<View>(
            textoMenuBtn, textoFab, textoHeaderSearch, radiantNavContainer,
            filterBar
        )
            .forEach {
                it.isEnabled = enabled
                it.isClickable = enabled
            }
        (conversationsList.adapter as? ConversationsAdapter)?.onEditAppearanceElement =
            if (enabled) null else { anchor -> showCardAppearanceBars(anchor) }
    }

    private fun styleAppearanceBar() = binding.apply {
        val ink = config.topBarTextColor
        val inset = (TextoGlass.FLOATING_BAR_INSET_DP * resources.displayMetrics.density).toInt()
        // CoordinatorLayout's own params, not FrameLayout's: they are not the same class and
        // the cast is unchecked until it runs.
        appearanceBar.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
            marginStart = inset
            marginEnd = inset
            topMargin = mainToolbar.bottom + 8.getScaledPx()
        }
        val padH = 8.getScaledPx()
        appearanceBar.setPadding(padH, 0, padH, 0)
        TextoGlass.applyPanel(
            view = appearanceBar,
            tint = if (config.topBarColor != 0) config.topBarColor else Color.BLACK,
            cornerRadius = 1000f,
            opacity = 0.92f,
            strokeWidthPx = 1.getScaledPx()
        )
        appearanceCancel.applyColorFilter(ink)
        appearanceHint.setTextColor(ink.withAlpha(0.75f))
        appearanceHint.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.78f))
        appearanceHint.typeface = typefaceFor(android.graphics.Typeface.NORMAL)
        appearanceDone.setTextColor(config.mainBackgroundColor)
        appearanceDone.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.78f))
        appearanceDone.typeface = typefaceFor(android.graphics.Typeface.BOLD)
        val innerH = 12.getScaledPx()
        val innerV = 7.getScaledPx()
        appearanceDone.setPadding(innerH, innerV, innerH, innerV)
        appearanceDone.background = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = 100f * resources.displayMetrics.density
            setColor(config.accentGradientStart)
        }

        appearanceCancel.setOnClickListener { closeAppearanceEditor(keep = false) }
        appearanceDone.setOnClickListener { closeAppearanceEditor(keep = true) }
        // The two capsules are one material and take one colour, so either of them opens the
        // same picker rather than pretending they can differ.
        listOf<View>(mainToolbar, textoHeaderRow).forEach { bar ->
            bar.setOnClickListener { if (isEditingAppearance) showBarAppearanceBars(bar) }
            bar.isClickable = true
        }
    }

    /**
     * The capsules' own colours, on the same two strips a bubble is recoloured with.
     *
     * The bar's colour above whatever was tapped and its ink below, exactly as a bubble gets
     * its colour above and its text below: the two capsules and a bubble are the same kind of
     * thing to restyle, so they are restyled the same way.
     *
     * Both capsules take one colour, because they are painted from one: giving them separate
     * pickers would offer a choice the app cannot honour.
     */
    private fun showBarAppearanceBars(anchor: View) {
        val barBars = inlineColourBars(
            label = getString(R.string.appearance_element_bars),
            read = { if (config.topBarColor == 0) Color.BLACK else config.topBarColor },
            write = { picked ->
                config.topBarColor = picked
                // The composer and the nav pill are painted from the same tint, so they move
                // together or the three stop reading as one material.
                config.inputBarBackgroundColor = picked
                repaintHome()
            },
            onReset = {
                config.topBarColor = skin.topBarColor
                config.inputBarBackgroundColor = skin.topBarColor
                repaintHome()
                binding.appearanceOverlay.hideInlineBars()
            },
        )
        val inkBars = inlineColourBars(
            label = getString(R.string.settings_top_bar_text),
            read = { config.topBarTextColor },
            write = { picked ->
                config.topBarTextColor = picked
                repaintHome()
            },
            onReset = {
                config.topBarTextColor = skin.topBarTextColor
                repaintHome()
                binding.appearanceOverlay.hideInlineBars()
            },
        )
        showInlineBarsAround(
            overlay = binding.appearanceOverlay,
            anchor = anchor,
            above = barBars,
            below = inkBars,
            ceiling = { binding.appearanceBar.bottom + 8.getScaledPx() },
        )
    }

    /** One card colour, one pair of strips, and the way back to the skin's own. */
    private fun showCardAppearanceBars(anchor: View) {
        val bars = inlineColourBars(
            label = getString(R.string.appearance_element_card),
            read = { config.recentColor },
            write = { picked ->
                config.recentColor = picked
                repaintHome()
            },
            onReset = {
                config.recentColor = skin.cardColor
                repaintHome()
                binding.appearanceOverlay.hideInlineBars()
            },
        )
        showInlineBarsAround(
            overlay = binding.appearanceOverlay,
            anchor = anchor,
            above = bars,
            below = null,
            ceiling = { binding.appearanceBar.bottom + 8.getScaledPx() },
        )
    }

    /** The skin the app is on, which is what "default" means for any one of its colours. */
    private val skin get() = AppThemes.byId(config.appTheme)
}
