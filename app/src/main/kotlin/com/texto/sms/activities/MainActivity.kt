package com.texto.sms.activities

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Telephony
import android.text.TextUtils
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import android.widget.RelativeLayout
import androidx.core.widget.addTextChangedListener
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import com.texto.sms.R
import com.texto.sms.adapters.ConversationsAdapter
import com.texto.sms.adapters.SearchResultsAdapter
import com.texto.sms.databinding.ActivityMainBinding
import com.texto.sms.extensions.*
import com.texto.sms.dialogs.EditFilterDialog
import com.texto.sms.helpers.MessageFilter
import com.texto.sms.helpers.NAV_ICON_DP
import com.texto.sms.helpers.NAV_TAB_RADIUS_DP
import com.texto.sms.helpers.SEARCHED_MESSAGE_ID
import com.texto.sms.helpers.THREAD_ID
import com.texto.sms.helpers.THREAD_TITLE
import com.texto.sms.models.Conversation
import com.texto.sms.models.Events
import com.texto.sms.models.Message
import com.texto.sms.models.SearchResult
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : SimpleActivity() {

    private companion object {
        /**
         * Filter chips are full pills in the design (`border-radius: 999px`). Any radius past
         * half the chip's height rounds the ends completely, so this only has to clear the
         * tallest a chip gets at the largest UI scale.
         */
        const val CHIP_PILL_RADIUS_DP = 100

        /** The design's wordmark, 20% up from the 34dp it shipped at. */
        const val LOGO_HEIGHT_DP = 41

        /** Matches the other panel transitions in the app. */
        const val SEARCH_ANIM_MILLIS = 260L
    }

    /** The date window the search is narrowed to; [SearchDateRange.ANY] means no narrowing. */
    private var searchDateRange = com.texto.sms.helpers.SearchDateRange.ANY

    /** The conversation filter the search is narrowed to, independent of the home screen's. */
    private var searchFilter: MessageFilter = MessageFilter.all("همه")
    private var searchFilterChipsAdapter: com.texto.sms.adapters.FilterChipsAdapter? = null

    override var isSearchBarEnabled = false

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
    private var activeFilter: MessageFilter = MessageFilter.all("همه")
    private var filterChipsAdapter: com.texto.sms.adapters.FilterChipsAdapter? = null
    private var filterChipDragHelper: androidx.recyclerview.widget.ItemTouchHelper? = null

    private val binding by viewBinding(ActivityMainBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.conversationsList))
        setupSearchEdgeToEdge()
        setupTopAppBar(binding.mainAppbar, NavigationIcon.None, Color.TRANSPARENT)

        setupTextoNavBar()

        // Opening the app starts on the filter the user nominated in settings, rather than
        // wherever they happened to leave the chips last time. Written through activeFilterId
        // so buildFilterChips() and everything downstream keep reading a single source.
        config.activeFilterId = config.defaultFilterId

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

        // Belt-and-suspenders alongside the refreshConversations() subscriber: if a
        // RefreshConversations event was ever missed while this activity wasn't visible,
        // resuming still picks up the latest config.customFilters (e.g. senders added to a
        // filter from elsewhere) instead of leaving activeFilter/chips stale until some
        // other event happens to fire.
        if (isInitialized) buildFilterChips()
        initMessenger()

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

        styleAppTitle()
        setupScaledToolbar(binding.mainToolbar)

        styleHeaderGear()

        getOrCreateConversationsAdapter().updateScaling()
        applyCustomColors()
        setupTextoNavBar()
        setupOverlayBars()

        filterChipsAdapter?.notifyDataSetChanged()
        binding.textoSearchInput.setTextColor(config.inputBarTextColor)
        binding.textoSearchInput.setHintTextColor(config.inputBarTextColor.withAlpha(0.5f))

        if (isFirstResume && config.useNewUi) {
            isFirstResume = false
            binding.mainAppbar.pivotY = 0f
            binding.mainAppbar.scaleY = 0.4f
            binding.mainAppbar.alpha = 0f
            binding.mainAppbar.animate()
                .scaleY(1f)
                .alpha(1f)
                .setDuration(800)
                .setInterpolator(android.view.animation.OvershootInterpolator(2.2f))
                .start()
        }
    }

    override fun onPause() {
        super.onPause()
        isActivityVisible = false
        storedTextColor = getProperTextColor()
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
    private fun styleHeaderGear() = binding.textoMenuBtn.apply {
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
    }

    private fun setupTextoNavBar() = binding.apply {
        if (config.useNewUi) {
            textoNavContainer.beVisible()

            // The capsule spans the width now that compose lives inside it: four equal
            // slots, sized up about a third from the mockup's so the labels sit comfortably.
            textoNavContainer.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            }

            // Every tab draws at full opacity: styleNavTabs separates the current one from
            // the rest with the design's own `--primary`/`--muted` pair, and dimming on top
            // of that would take the idle tabs well below the contrast the design gives them.
            listOf(navHomeIcon, navAddIcon, textoSearchIcon).forEach { it.alpha = 1f }
            listOf(navHomeLabel, navAddLabel, navSearchLabel).forEach { it.alpha = 1f }

            styleNavTabs()

            navSearchContainer.setOnClickListener {
                if (!isSearchExpanded) expandSearchBar()
            }

            navAddBtn.setOnClickListener { launchNewConversation() }

            navHomeBtn.setOnClickListener {
                // While searching, this tab is the way back to the list rather than a
                // scroll-to-top on a list that is not on screen.
                if (isSearchExpanded) {
                    shrinkSearchBar()
                } else {
                    clearPendingScroll()
                    binding.conversationsList.smoothScrollToPosition(0)
                }
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

            // Set initial state
            if (!isSearchExpanded) {
                navHomeBtn.beVisible()
                navAddBtn.beVisible()
                navSearchLabel.beVisible()
                navSearchContainer.gravity = android.view.Gravity.CENTER
            }
        } else {
            textoNavContainer.beGone()
        }
    }

    /**
     * The capsule's tabs and the lozenge behind the current one, painted here rather than
     * left to the XML placeholders so a theme change repaints them without reinflating.
     */
    private fun styleNavTabs() = binding.apply {
        val density = resources.displayMetrics.density
        // The lozenge marks where you actually are, so while the search panel is up it sits
        // behind the search tab rather than staying on a conversation list that is not on
        // screen. Painted per tab from the same recipe, so only which one gets it changes.
        val activeTab: android.view.View = if (isSearchExpanded) navSearchContainer else navHomeBtn
        // `--primary`, the accent the design tints the active tab with -- not `--primary-alt`
        // (auroraAccentColor), which is only the third halo hue behind the app.
        val accent = config.accentGradientStart
        val muted = config.mainTextColor.withAlpha(0.68f)

        val activeLozenge = android.graphics.drawable.GradientDrawable().apply {
            shape = android.graphics.drawable.GradientDrawable.RECTANGLE
            cornerRadius = NAV_TAB_RADIUS_DP * density
            // `background: var(--primary-soft)` over `border: 1px solid primary/0.25`.
            setColor(accent.withAlpha(0.16f))
            setStroke(density.toInt().coerceAtLeast(1), accent.withAlpha(0.25f))
        }
        listOf(navHomeBtn, navAddBtn, navSearchContainer).forEach { tab ->
            tab.background = if (tab === activeTab) activeLozenge else null
        }

        // The design carries the active/idle distinction in colour -- `--primary` against
        // `--muted` -- rather than by fading the whole tab, so the idle tabs get the muted
        // ink at full opacity instead of the accent at 36%.
        val searchIsActive = activeTab === navSearchContainer
        navHomeIcon.applyColorFilter(if (searchIsActive) muted else accent)
        navHomeLabel.setTextColor(if (searchIsActive) muted else accent)
        textoSearchIcon.applyColorFilter(if (searchIsActive) accent else muted)
        navSearchLabel.setTextColor(if (searchIsActive) accent else muted)
        navAddIcon.applyColorFilter(muted)
        navAddLabel.setTextColor(muted)

        val padH = 4.getScaledPx()
        val padV = 10.getScaledPx()
        listOf(navHomeBtn, navAddBtn, navSearchContainer).forEach { tab ->
            tab.minimumWidth = 0
            tab.setPadding(padH, padV, padH, padV)
        }
        val glyph = NAV_ICON_DP.getScaledPx()
        listOf(navHomeIcon, navAddIcon, textoSearchIcon).forEach { icon ->
            icon.updateLayoutParams {
                width = glyph
                height = glyph
            }
        }
        listOf(navHomeLabel, navAddLabel, navSearchLabel).forEach { label ->
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.78f))
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

        // The header capsule and the home filter row step aside entirely while searching.
        // They sit above this panel in the same space, so leaving them up hid the field and
        // its chips behind the wordmark; search gets the top of the screen to itself.
        mainAppbar.beGone()
        filterBar.beGone()

        buildSearchFilterChips()
        buildSearchDateChips()
        styleSearchPanel()
        styleNavTabs()

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

        mainAppbar.beVisible()
        filterBar.beVisible()
        // Full view alpha: how see-through these are is the glass setting's job alone. A
        // 0.92 here multiplied against the fill and put the bars below whatever the slider
        // said, which is part of why its top end never looked opaque.
        mainAppbar.alpha = 1f
        filterBar.alpha = 1f
        styleNavTabs()
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
                styleChip = { chip, filterId, isActive -> styleFilterChip(chip, filterId, isActive) }
            )
            binding.searchFilterBar.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(
                this, androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL, false
            )
            binding.searchFilterBar.adapter = searchFilterChipsAdapter
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
        // before it off screen. Anchored to the start, unless something further along is
        // picked, in which case that is what needs to be on screen.
        val target = activeChip
        binding.searchDateBar.post {
            if (target == null) {
                binding.searchDateBar.fullScroll(android.view.View.FOCUS_RIGHT)
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
                else -> com.texto.sms.helpers.TextoGlass.bar(
                    tint = config.recentColor,
                    cornerRadius = chipRadius,
                    opacity = 0.5f,
                    strokeWidthPx = 1.getScaledPx(),
                    rimAlpha = 0.18f
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
                    isActive -> config.sentBubbleTextColor
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
                        setTint(if (isActive) config.sentBubbleTextColor else config.accentGradientStart)
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
                toast(org.fossify.commons.R.string.unknown_error_occurred)
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
                    handleNotificationPermission { granted ->
                        if (!granted) {
                            PermissionRequiredDialog(
                                activity = this,
                                textId = org.fossify.commons.R.string.allow_notifications_incoming_messages,
                                positiveActionCallback = { openNotificationSettings() }
                            )
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
    private fun currentFilters(): List<MessageFilter> = buildList {
        add(MessageFilter.all(getString(R.string.filter_all)))
        if (config.showContactsOnlyFilter) {
            add(MessageFilter.contactsOnly(getString(R.string.filter_contacts_only)))
        }
        if (config.showAdsFilter) {
            // The chip is the inverse of the stored ads list: it starts holding every thread
            // and loses them one at a time as they are marked as advertising. The ads list
            // itself never gets a chip of its own.
            add(
                MessageFilter.noAds(
                    label = getString(R.string.filter_no_ads),
                    senders = config.adsFilter.senders
                )
            )
        }
        addAll(config.customFilters)
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
        centerFilterChips()
    }

    /**
     * Keeps the chip row visually centred instead of hugging one edge. A horizontal
     * LinearLayoutManager always packs its children against the start, so with only a few
     * chips the row sat off to one side; padding the leftover space equally on both sides
     * centres them while still letting the row scroll normally once they overflow.
     */
    private fun centerFilterChips() {
        val bar = binding.filterBar
        bar.post {
            if (isFinishing || isDestroyed) return@post
            val manager = bar.layoutManager ?: return@post
            var content = 0
            for (i in 0 until manager.childCount) {
                val child = manager.getChildAt(i) ?: continue
                val params = child.layoutParams as ViewGroup.MarginLayoutParams
                content += child.measuredWidth + params.marginStart + params.marginEnd
            }

            val base = 12.getScaledPx()
            val available = bar.width - base * 2
            // Overflowing chips leave no slack, so this collapses to the base padding and
            // the row simply scrolls.
            val sidePadding = base + ((available - content) / 2).coerceAtLeast(0)
            if (bar.paddingLeft != sidePadding || bar.paddingRight != sidePadding) {
                bar.setPadding(sidePadding, bar.paddingTop, sidePadding, bar.paddingBottom)
            }
        }
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
            }
        ) { filter ->
            val filters = config.customFilters.toMutableList()
            val index = filters.indexOfFirst { it.id == filter.id }
            if (index >= 0) filters[index] = filter else filters.add(filter)
            config.customFilters = filters
            config.activeFilterId = filter.id
            buildFilterChips()
            applyActiveFilter()
        }
    }

    private fun deleteFilter(filter: MessageFilter) {
        val question = getString(R.string.delete_filter_confirmation, filter.label)
        ConfirmationDialog(this, question) {
            config.customFilters = config.customFilters.filter { it.id != filter.id }
            if (config.activeFilterId == filter.id) {
                config.activeFilterId = MessageFilter.ID_ALL
            }
            buildFilterChips()
            applyActiveFilter()
        }
    }

    /**
     * How many conversations each chip currently holds. "All" is the whole list; every other
     * filter reuses the same predicate the list itself is filtered with, so a chip's badge can
     * never disagree with what tapping it shows.
     */
    /**
     * The wordmark image carries its own neon cyan-to-magenta colouring baked into the PNG,
     * so nothing here recolours it -- except on a light ground, where the import's own CSS
     * (`saturate(1.1) brightness(0.72) contrast(1.12)`) darkens it a touch for legibility.
     * That is reproduced as a [ColorMatrix] rather than a flat tint, which would just paint
     * over the gradient the mark is drawn with.
     */
    private fun styleAppTitle() = binding.textoTitle.apply {
        updateLayoutParams<LinearLayout.LayoutParams> {
            height = LOGO_HEIGHT_DP.getScaledPx()
        }
        colorFilter = if (com.texto.sms.helpers.TextoGlass.isDark(config.mainBackgroundColor)) {
            null
        } else {
            val saturation = android.graphics.ColorMatrix().apply { setSaturation(1.1f) }
            val brightness = android.graphics.ColorMatrix(
                floatArrayOf(
                    0.72f, 0f, 0f, 0f, 0f,
                    0f, 0.72f, 0f, 0f, 0f,
                    0f, 0f, 0.72f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            val contrastScale = 1.12f
            val contrastTranslate = (1 - contrastScale) * 127.5f
            val contrast = android.graphics.ColorMatrix(
                floatArrayOf(
                    contrastScale, 0f, 0f, 0f, contrastTranslate,
                    0f, contrastScale, 0f, 0f, contrastTranslate,
                    0f, 0f, contrastScale, 0f, contrastTranslate,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            saturation.postConcat(brightness)
            saturation.postConcat(contrast)
            android.graphics.ColorMatrixColorFilter(saturation)
        }
    }

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
        val textColor = if (isActive) {
            config.sentBubbleTextColor
        } else {
            config.mainTextColor.withAlpha(0.58f)
        }
        val chipRadius = CHIP_PILL_RADIUS_DP * density
        chip.background = if (isActive) {
            com.texto.sms.helpers.TextoGlass.accent(
                start = config.accentGradientStart,
                end = config.accentGradientEnd,
                cornerRadius = chipRadius,
                mid = config.accentGradientMid
            )
        } else {
            com.texto.sms.helpers.TextoGlass.bar(
                tint = config.recentColor,
                cornerRadius = chipRadius,
                opacity = 0.5f,
                strokeWidthPx = stroke,
                rimAlpha = 0.22f
            )
        }
        val horizontal = if (filterId == com.texto.sms.adapters.FilterChipsAdapter.ADD_CHIP_ID) {
            18.getScaledPx()
        } else {
            // The design's own 8px/16px chip padding.
            16.getScaledPx()
        }
        chip.setPadding(horizontal, 8.getScaledPx(), horizontal, 8.getScaledPx())
        chip.alpha = 1f
        chip.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        // `box-shadow: var(--glow)` on the active chip only.
        chip.elevation = if (isActive) 6 * density else 0f

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

    /** Moves to the next/previous filter chip, wrapping around at either end. */
    private fun cycleFilter(forward: Boolean) {
        val filters = currentFilters()
        if (filters.size <= 1) return
        val currentIndex = filters.indexOfFirst { it.id == activeFilter.id }.let { if (it == -1) 0 else it }
        val nextIndex = if (forward) {
            (currentIndex + 1) % filters.size
        } else {
            (currentIndex - 1 + filters.size) % filters.size
        }
        selectFilter(filters[nextIndex])
    }

    /**
     * A left/right fling anywhere on the conversation list pages through the filter chips,
     * like swiping between tabs. Registered as a passive observer (onInterceptTouchEvent
     * always returns false) so normal row taps, vertical scrolling, and the conversation
     * drag-reorder ItemTouchHelper all keep working exactly as before.
     */
    private fun setupFilterSwipeGesture() {
        val gestureDetector = android.view.GestureDetector(
            this,
            object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: android.view.MotionEvent?,
                    e2: android.view.MotionEvent,
                    velocityX: Float,
                    velocityY: Float,
                ): Boolean {
                    if (e1 == null) return false
                    val deltaX = e2.x - e1.x
                    val deltaY = e2.y - e1.y
                    val isHorizontalFling = kotlin.math.abs(deltaX) > kotlin.math.abs(deltaY) * 2 &&
                        kotlin.math.abs(deltaX) > 60 &&
                        kotlin.math.abs(velocityX) > 300
                    if (!isHorizontalFling) return false
                    // Swipe left (negative dx) advances, matching the usual left-to-right
                    // page order convention (e.g. ViewPager).
                    cycleFilter(forward = deltaX < 0)
                    return true
                }
            }
        )

        binding.conversationsList.addOnItemTouchListener(
            object : androidx.recyclerview.widget.RecyclerView.OnItemTouchListener {
                override fun onInterceptTouchEvent(
                    rv: androidx.recyclerview.widget.RecyclerView,
                    e: android.view.MotionEvent,
                ): Boolean {
                    gestureDetector.onTouchEvent(e)
                    return false
                }

                override fun onTouchEvent(
                    rv: androidx.recyclerview.widget.RecyclerView,
                    e: android.view.MotionEvent,
                ) = Unit

                override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) = Unit
            }
        )
    }

    private fun setupOneTimeViews() {
        binding.noConversationsPlaceholder2.setOnClickListener { launchNewConversation() }
        // The header's single control. The overflow menu it replaced offered archived
        // conversations, settings and about; all three live inside settings now, so the
        // gear opens that rather than a menu with one real entry left in it.
        binding.textoMenuBtn.setOnClickListener {
            startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
        }
        buildFilterChips()
        setupFilterSwipeGesture()

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
            }
        )

        val searchAnim = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom)
        binding.textoNavContainer.startAnimation(searchAnim)

        binding.textoSearchInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())
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
            // Querying the contacts provider for the cursor itself is a synchronous call,
            // not just consuming it below -- both need to happen off the main thread.
            val privateCursor = getMyContactsCursor(favoritesOnly = false, withPhoneNumbersOnly = true)
            val privateContacts = MyContactsContentProvider.getSimpleContacts(this, privateCursor)
            val conversations = getConversations(privateContacts = privateContacts)
            insertOrUpdateConversations(conversations)
            val all = conversationsDB.getNonArchived() as ArrayList<Conversation>
            // One query for every thread's newest message, on the same background pass
            // that loads the list. Rows then bind without touching the database.
            val snippets = getLatestSnippets()
            // The address book itself is the source for the "مخاطبین" filter. It used to be
            // built from privateContacts alone -- the Fossify private-contacts provider,
            // which is empty unless the user also runs Simple Contacts -- so the filter
            // matched nothing and always came up empty.
            val contactNumbers = getContactNumbersSnapshot() +
                privateContacts.flatMap { it.phoneNumbers }
                    .map { com.texto.sms.helpers.SystemBlockedNumbers.comparable(it.normalizedNumber) }
                    .filter { it.isNotEmpty() }
            val contactsForFilters = getContactsWithNamesSnapshot() +
                privateContacts.mapNotNull { contact ->
                    val number = contact.phoneNumbers.firstOrNull()?.normalizedNumber.orEmpty()
                    if (number.isBlank()) null else contact.name.ifBlank { number } to number
                }
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
                val top2 = allSortedByDate.take(2)
                val remaining = allSortedByDate.filter { conv -> !top2.any { it.threadId == conv.threadId } }
                
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
                top2 + sortedPills
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
        if (binding.conversationsList.layoutManager !is org.fossify.commons.views.MyLinearLayoutManager) {
            binding.conversationsList.layoutManager =
                org.fossify.commons.views.MyLinearLayoutManager(this)
        }

        // No per-position offsets: the card's own margin is the only gap, so the spacing
        // between every pair of chats is identical.
        if (useNewUi && dragTouchHelper == null) {
            val callback = com.texto.sms.helpers.ModernDragCallback(getOrCreateConversationsAdapter())
            dragTouchHelper = androidx.recyclerview.widget.ItemTouchHelper(callback)
        }

        allConversations = sorted
        submitFilteredConversations(cached, isManualReorder)
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
        }
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
            val barsGap = 8.getScaledPx()
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
                // The empty-state text is already positioned with layout_below="filter_bar",
                // so it follows the bar without any extra offset here.
            }
        }
    }

    private fun setupSearchEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.textoSearchInput) { _, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomInset = if (imeInsets.bottom > 0) imeInsets.bottom else systemBars.bottom
            binding.textoNavContainer.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                bottomMargin = bottomInset + 22.getScaledPx()
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
            val date = (conv.date * 1000L).formatJalaliDateOrTime()
            results.add(SearchResult(messageId = -1, title = conv.title, snippet = conv.phoneNumber, date = date, threadId = conv.threadId, photoUri = conv.photoUri))
        }
        messages.sortedByDescending { it.id }.forEach { msg ->
            var recipient = msg.senderName
            if (recipient.isEmpty() && msg.participants.isNotEmpty()) {
                recipient = TextUtils.join(", ", msg.participants.map { it.name })
            }
            val date = (msg.date * 1000L).formatJalaliDateOrTime()
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
                    results.size.toString().toPersianDigits()
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
        val inputBarTextColor = config.inputBarTextColor
        val isNewUi = config.useNewUi
        
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
            drawable.setLayerInset(0, 0, statusBarInsetOf(binding.mainAppbar), 0, 0)
            binding.mainAppbar.foreground = drawable
        } else {
            binding.mainAppbar.foreground = null
        }

        if (config.searchBarOutline && isNewUi) {
            val thickness = config.searchBarOutlineThickness
            val thickStroke = (thickness * density).toInt()
            val r_base = 100f * density
            val drawable = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                setStroke(thickStroke, config.searchBarOutlineColor)
                cornerRadius = r_base
                setColor(Color.TRANSPARENT)
            }
            val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(drawable))
            layerDrawable.setLayerInset(0, 0, 0, 0, 0)
            binding.textoNavContainer.foreground = layerDrawable
            
            // Sync icon and divider colors with search bar text color
            binding.navHomeIcon.imageTintList = android.content.res.ColorStateList.valueOf(inputBarTextColor)
            binding.navAddIcon.imageTintList = android.content.res.ColorStateList.valueOf(inputBarTextColor)
            binding.textoSearchIcon.imageTintList = android.content.res.ColorStateList.valueOf(inputBarTextColor)
        } else {
            binding.textoNavContainer.foreground = null
        }
    }

}
