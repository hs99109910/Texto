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
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import android.widget.RelativeLayout
import androidx.core.widget.addTextChangedListener
import org.fossify.commons.activities.AboutActivity
import org.fossify.commons.dialogs.ConfirmationDialog
import org.fossify.commons.dialogs.PermissionRequiredDialog
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import com.texto.sms.BuildConfig
import com.texto.sms.R
import com.texto.sms.adapters.ConversationsAdapter
import com.texto.sms.adapters.SearchResultsAdapter
import com.texto.sms.databinding.ActivityMainBinding
import com.texto.sms.extensions.*
import com.texto.sms.dialogs.EditFilterDialog
import com.texto.sms.helpers.MessageFilter
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

        binding.novaTitle.text = getString(R.string.app_launcher_name)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.conversationsList))
        setupSearchEdgeToEdge()
        setupTopAppBar(binding.mainAppbar, NavigationIcon.None, Color.TRANSPARENT)

        setupNovaNavBar()

        // Opening the app starts on the filter the user nominated in settings, rather than
        // wherever they happened to leave the chips last time. Written through activeFilterId
        // so buildFilterChips() and everything downstream keep reading a single source.
        config.activeFilterId = config.defaultFilterId

        loadMessages()
        
        // No update check: it contacted the upstream project's GitHub repo on every
        // launch and would have offered to install their APK over this build.

        ViewCompat.setOnApplyWindowInsetsListener(binding.mainCoordinator) { _, insets ->
            val isImeVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
            if (wasImeVisible && !isImeVisible && config.useNewUi && binding.novaSearchInput.text?.isEmpty() == true) {
                shrinkSearchBar()
            }
            wasImeVisible = isImeVisible
            insets
        }
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

        binding.novaTitle.updateLayoutParams<Toolbar.LayoutParams> {
            marginEnd = 60.getScaledPx()
        }
        setupScaledToolbar(binding.mainToolbar)
        binding.novaMenuBtn.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
            topMargin = 3.getScaledPx()
            marginEnd = 8.getScaledPx()
        }
        binding.novaMenuBtn.imageTintList =
            android.content.res.ColorStateList.valueOf(config.topBarTextColor)
        binding.novaMenuBtn.alpha = 0.6f

        binding.novaNavContainer.updateLayoutParams {
            height = 55.getScaledPx()
        }

        getOrCreateConversationsAdapter().updateScaling()
        applyCustomColors()
        setupNovaNavBar()
        setupOverlayBars()

        styleFab()
        filterChipsAdapter?.notifyDataSetChanged()
        binding.novaSearchInput.setTextColor(config.inputBarTextColor)
        binding.novaSearchInput.setHintTextColor(config.inputBarTextColor.withAlpha(0.5f))

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

    private fun setupNovaNavBar() = binding.apply {
        if (config.useNewUi) {
            novaNavContainer.beVisible()
            
            // Apply compact width and transparency
            novaNavContainer.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                width = 240.getScaledPx()
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            }
            novaNavContainer.alpha = 0.92f
            
            // Set icon transparency
            navHomeIcon.alpha = 0.9f // Lighter for active
            navSettingsIcon.alpha = 0.6f
            novaSearchIcon.alpha = 0.6f
            
            // Highlight Home (Current Screen) with subtle transparency
            // No highlight behind the home icon: a square tint inside a rounded bar
            // reads as a stray halo.
            navHomeBtn.background = null
            
            navSearchContainer.setOnClickListener {
                if (!isSearchExpanded) expandSearchBar()
            }
            
            navSettingsBtn.setOnClickListener {
                startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
            }
            
            navHomeBtn.setOnClickListener {
                clearPendingScroll()
                binding.conversationsList.smoothScrollToPosition(0)
            }

            if (novaSearchInput.tag != "text_watcher_attached") {
                novaSearchInput.addTextChangedListener { text ->
                    searchTextChanged(text?.toString() ?: "")
                }
                novaSearchInput.tag = "text_watcher_attached"
            }
            
            // Set initial state
            if (!isSearchExpanded) {
                navDivider1.beVisible()
                navDivider2.beVisible()
                navHomeBtn.beVisible()
                navSettingsBtn.beVisible()
                novaSearchInput.beGone()
                
                // Center search icon when collapsed
                (novaSearchIcon.layoutParams as? LinearLayout.LayoutParams)?.marginStart = 0
                navSearchContainer.gravity = android.view.Gravity.CENTER
            }
        } else {
            novaNavContainer.beGone()
        }
    }

    private fun expandSearchBar() = binding.apply {
        if (isSearchExpanded) return@apply
        isSearchExpanded = true
        
        val startWidth = 240.getScaledPx()
        val endWidth = root.width - 32.getScaledPx()
        
        val animator = ValueAnimator.ofFloat(0f, 1f)
        animator.duration = 400
        animator.interpolator = OvershootInterpolator(1.0f)
        
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            
            // Expand width
            val currentWidth = startWidth + ((endWidth - startWidth) * value).toInt()
            novaNavContainer.updateLayoutParams { width = currentWidth }
            
            // Shrink side buttons weight
            val weight = 1f - value
            navHomeBtn.layoutParams = (navHomeBtn.layoutParams as LinearLayout.LayoutParams).apply { this.weight = weight }
            navSettingsBtn.layoutParams = (navSettingsBtn.layoutParams as LinearLayout.LayoutParams).apply { this.weight = weight }
            
            // Expand search container weight
            navSearchContainer.layoutParams = (navSearchContainer.layoutParams as LinearLayout.LayoutParams).apply { this.weight = 1f + (2f * value) }
            
            // Fade out dividers and side icons
            navDivider1.alpha = 1f - value
            navDivider2.alpha = 1f - value
            navHomeIcon.alpha = 0.9f * (1f - value)
            navSettingsIcon.alpha = 0.6f * (1f - value)
            
            // Move search icon to start
            navSearchContainer.gravity = if (value > 0.5f) android.view.Gravity.CENTER_VERTICAL else android.view.Gravity.CENTER
            (novaSearchIcon.layoutParams as? LinearLayout.LayoutParams)?.marginStart = (12.getScaledPx() * value).toInt()
            
            novaNavContainer.requestLayout()
        }
        
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                novaNavContainer.alpha = 1.0f // Solid when searching
                // The top title bar and filter chips row are glass too now -- brighten them
                // in lockstep with the nav bar instead of leaving them dimmed while it isn't.
                mainAppbar.alpha = 1.0f
                filterBar.alpha = 1.0f
            }
            override fun onAnimationEnd(animation: Animator) {
                navHomeBtn.beGone()
                navSettingsBtn.beGone()
                navDivider1.beGone()
                navDivider2.beGone()
                novaSearchInput.beVisible()
                novaSearchInput.requestFocus()
                showKeyboard(novaSearchInput)
            }
        })
        animator.start()
    }

    private fun shrinkSearchBar() = binding.apply {
        if (!isSearchExpanded) return@apply
        isSearchExpanded = false
        
        navHomeBtn.beVisible()
        navSettingsBtn.beVisible()
        navDivider1.beVisible()
        navDivider2.beVisible()
        novaSearchInput.beGone()
        hideKeyboard()
        
        val startWidth = novaNavContainer.width
        val endWidth = 240.getScaledPx()
        
        val animator = ValueAnimator.ofFloat(1f, 0f)
        animator.duration = 300
        animator.interpolator = DecelerateInterpolator()
        
        animator.addUpdateListener { animation ->
            val value = animation.animatedValue as Float
            
            // Shrink width
            val currentWidth = endWidth + ((startWidth - endWidth) * value).toInt()
            novaNavContainer.updateLayoutParams { width = currentWidth }
            
            val weight = 1f - value
            navHomeBtn.layoutParams = (navHomeBtn.layoutParams as LinearLayout.LayoutParams).apply { this.weight = weight }
            navSettingsBtn.layoutParams = (navSettingsBtn.layoutParams as LinearLayout.LayoutParams).apply { this.weight = weight }
            navSearchContainer.layoutParams = (navSearchContainer.layoutParams as LinearLayout.LayoutParams).apply { this.weight = 1f + (2f * value) }
            
            navDivider1.alpha = 1f - value
            navDivider2.alpha = 1f - value
            navHomeIcon.alpha = 0.9f * (1f - value)
            navSettingsIcon.alpha = 0.6f * (1f - value)
            
            // Recenter search icon
            navSearchContainer.gravity = if (value < 0.5f) android.view.Gravity.CENTER else android.view.Gravity.CENTER_VERTICAL
            (novaSearchIcon.layoutParams as? LinearLayout.LayoutParams)?.marginStart = (12.getScaledPx() * value).toInt()
            
            novaNavContainer.requestLayout()
        }
        
        animator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                novaNavContainer.alpha = 0.92f // Less transparent when idle
                mainAppbar.alpha = 0.92f
                filterBar.alpha = 0.92f
            }
        })
        animator.start()
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

        filterChipsAdapter?.submitFilters(filters, activeFilter.id)
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
     * Accent action button parked above the nav bar. [SimpleActivity.applyCustomColors]
     * still treats this view as the old top-bar glyph and dims it, so restyle it afterwards.
     */
    private fun styleFab() = binding.conversationsFab.apply {
        val size = 56.getScaledPx()
        updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
            width = size
            height = size
            marginEnd = 16.getScaledPx()
            bottomMargin = 88.getScaledPx()
        }
        // Solid accent gradient rather than glass: the design's FAB is the one surface that
        // is meant to sit on top of the halos, not blend into them. Rounded to a squircle at
        // 32% of its side, matching the avatars and cards.
        background = com.texto.sms.helpers.NovaGlass.accent(
            start = config.accentGradientEnd,
            end = config.auroraAccentColor,
            cornerRadius = size * 0.32f
        )
        setTextColor(config.sentBubbleTextColor)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.9f))
        alpha = 1f
        elevation = 12 * resources.displayMetrics.density
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        bringToFront()
    }

    private fun styleFilterChip(chip: TextView, filterId: String, isActive: Boolean) {
        val density = resources.displayMetrics.density
        val radius = 100f * density
        val baseColor = if (config.topBarColor != 0) config.topBarColor else Color.BLACK

        // The selected chip used to differ only by opacity of the top-bar colour. Under
        // Aurora that colour is near-black on a near-black background, so 0.85 against 0.45
        // was almost invisible. The active chip now takes the theme's own accent -- the sent
        // bubble colour -- as a solid fill with a matching outline, which reads clearly on
        // any background, while the inactive ones drop back to a faint tint.
        val textColor = if (isActive) config.sentBubbleTextColor else config.topBarTextColor
        com.texto.sms.helpers.NovaGlass.applyPanel(
            view = chip,
            // Active chips carry the same accent gradient as the sent bubble and the FAB.
            tint = if (isActive) config.accentGradientStart else baseColor,
            tintEnd = if (isActive) config.accentGradientEnd else null,
            cornerRadius = radius,
            opacity = if (isActive) 0.98f else 0.30f,
            strokeWidthPx = 1.getScaledPx(),
            outlineColor = if (isActive) config.sentBubbleTextColor else null,
            outlineWidthPx = if (isActive) 1.getScaledPx() else 0
        )
        val horizontal = if (filterId == com.texto.sms.adapters.FilterChipsAdapter.ADD_CHIP_ID) {
            20.getScaledPx()
        } else {
            16.getScaledPx()
        }
        chip.setPadding(horizontal, 8.getScaledPx(), horizontal, 8.getScaledPx())
        chip.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.8f))
        chip.setTextColor(textColor)
        chip.alpha = if (isActive) 1f else 0.55f
        chip.typeface = typefaceFor(
            if (isActive) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL
        )
        chip.elevation = 6 * density
    }

    private fun selectFilter(filter: MessageFilter) {
        if (activeFilter.id == filter.id) return
        activeFilter = filter
        config.activeFilterId = filter.id
        applyActiveFilter()
    }

    private fun applyActiveFilter() {
        clearPendingScroll()
        filterChipsAdapter?.submitFilters(currentFilters(), activeFilter.id)
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

    private fun showMainMenu() {
        val items = arrayListOf<Pair<Int, String>>()
        if (config.isArchiveAvailable) {
            items.add(R.id.show_archived to getString(R.string.show_archived_conversations))
        }
        // Recycle bin and blocked numbers are reached from Settings now,
        // where they sit together under one heading instead of being scattered here.
        items.add(R.id.settings to getString(org.fossify.commons.R.string.settings))
        items.add(R.id.about to getString(org.fossify.commons.R.string.about))

        showModernMenu(binding.novaMenuBtn, items) { itemId ->
            when (itemId) {
                R.id.show_archived ->
                    startActivity(Intent(this, ArchivedConversationsActivity::class.java))

                R.id.settings -> startActivity(Intent(this, SettingsActivity::class.java))
                R.id.about -> launchAbout()
            }
        }
    }


    private fun launchAbout() {
        startAboutActivity(
            R.string.app_launcher_name,
            0L,
            BuildConfig.VERSION_NAME,
            ArrayList<org.fossify.commons.models.FAQItem>(),
            false
        )
    }

    private fun setupOneTimeViews() {
        binding.noConversationsPlaceholder2.setOnClickListener { launchNewConversation() }
        binding.conversationsFab.setOnClickListener { launchNewConversation() }
        binding.novaMenuBtn.setOnClickListener { showMainMenu() }
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

        val fabAnim = AnimationUtils.loadAnimation(this, R.anim.fab_in)
        binding.conversationsFab.startAnimation(fabAnim)

        val searchAnim = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom)
        binding.novaNavContainer.startAnimation(searchAnim)

        binding.novaSearchInput.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())
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

    private fun searchTextChanged(text: String) {
        lastSearchedText = text
        if (text.length >= 2) {
            binding.mainNestedScrollview.getChildAt(0).beGone()
            binding.searchHolder.beVisible()
            binding.searchHolder.animate().alpha(1f).setDuration(200L).start()
            ensureBackgroundThread {
                val searchQuery = "%$text%"
                // The provider holds every message on the device; the Room table only has
                // the ones that arrived since install, so both are searched and merged.
                val fromProvider = searchMessagesInProvider(text)
                val fromCache = messagesDB.getMessagesWithText(searchQuery)
                val messages = (fromProvider + fromCache)
                    .distinctBy { it.id }
                    .sortedByDescending { it.date }

                val conversations = conversationsDB.getConversationsWithText(searchQuery)

                // Searching while a filter chip is active stays inside that chip: the
                // results are narrowed to the threads the filter itself would show, so
                // "search" means "search in what I am looking at" rather than silently
                // reaching across every conversation on the device.
                val allowedThreads = if (activeFilter.id == MessageFilter.ID_ALL) {
                    null
                } else {
                    allConversations
                        .filter {
                            com.texto.sms.helpers.MessageClassifier
                                .matches(it, activeFilter, contactPhoneNumbers)
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
        } else {
            binding.mainNestedScrollview.getChildAt(0).beVisible()
            binding.searchHolder.beGone()
            binding.searchHolder.alpha = 0f
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
                listOf(binding.conversationsList, binding.searchResultsList).forEach { list ->
                    if (list.paddingTop != inset) {
                        list.setPadding(list.paddingLeft, inset, list.paddingRight, list.paddingBottom)
                        list.scrollToPosition(0)
                    }
                }
                // The empty-state text is already positioned with layout_below="filter_bar",
                // so it follows the bar without any extra offset here.
            }
        }
    }

    private fun setupSearchEdgeToEdge() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.novaSearchInput) { _, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.novaNavContainer.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                bottomMargin = (if (imeInsets.bottom > 0) imeInsets.bottom else systemBars.bottom) + 16.getScaledPx()
            }
            insets
        }
    }

    private fun showSearchResults(messages: List<Message>, conversations: List<Conversation>, searchedText: String) {
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
            binding.searchPlaceholder.beGoneIf(results.isNotEmpty())
            binding.searchPlaceholder2.beGoneIf(results.isNotEmpty())
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
            binding.novaNavContainer.foreground = layerDrawable
            
            // Sync icon and divider colors with search bar text color
            binding.navHomeIcon.imageTintList = android.content.res.ColorStateList.valueOf(inputBarTextColor)
            binding.navSettingsIcon.imageTintList = android.content.res.ColorStateList.valueOf(inputBarTextColor)
            binding.novaSearchIcon.imageTintList = android.content.res.ColorStateList.valueOf(inputBarTextColor)
            binding.navDivider1.setBackgroundColor(inputBarTextColor.withAlpha(0.2f))
            binding.navDivider2.setBackgroundColor(inputBarTextColor.withAlpha(0.2f))
        } else {
            binding.novaNavContainer.foreground = null
        }
    }

}
