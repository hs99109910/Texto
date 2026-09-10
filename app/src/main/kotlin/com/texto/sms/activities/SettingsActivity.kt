package com.texto.sms.activities

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.core.view.updateLayoutParams
import android.widget.TextView
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.commons.helpers.PERMISSION_WRITE_STORAGE
import org.fossify.commons.views.MyAppBarLayout
import com.texto.sms.helpers.textoCapsuleDialog
import com.texto.sms.BuildConfig
import com.texto.sms.R
import com.texto.sms.databinding.ActivitySettingsBinding
import com.texto.sms.extensions.config
import com.texto.sms.extensions.toUiDigits
import com.texto.sms.extensions.uiPercentSign
import com.texto.sms.helpers.*

class SettingsActivity : SimpleActivity() {

    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        setupEdgeToEdge(padBottomImeAndSystem = listOf(binding.settingsNestedScrollview))
        setupTopAppBar(binding.settingsAppbar, NavigationIcon.Arrow, Color.TRANSPARENT)

        (binding.settingsAppbar as? MyAppBarLayout)?.let { appBar ->
            appBar.setBackgroundColor(Color.TRANSPARENT)
            binding.settingsToolbar.navigationIcon?.setTint(config.topBarTextColor)
            binding.settingsToolbar.setNavigationOnClickListener { finish() }
        }

        setupCustomization()
        setupUIScale()
        setupGlassOpacity()
        setupAccentHue()
        setupAppTheme()
        setupDarkModeSwitch()
        setupBlockedNumbers()
        setupConversationScreens()
        setupContactsOnlyFilter()
        setupAdsFilter()
        setupDefaultFilter()
        setupFontSize()
        setupFontFamily()
        setupLanguage()
        setupBarBgMode()
        updateAppFonts(binding.root)
    }

    override fun onResume() {
        super.onResume()
        applyOutlines()
        updateCustomizationUI()
        setupTextoNavBar()
        // Ensure UI is fully up to date for modern design
        updateAppFonts(binding.root)
        applyCustomColors()
        // After applyCustomColors, which is what repaints on a theme change.
        styleAllSwitches(binding.root)
    }

    private fun setupTextoNavBar() = binding.apply {
        if (config.useNewUi) {
            textoNavContainer.beVisible()
            
            // Sync edge-to-edge padding to match Home screen
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(textoNavContainer) { v, insets ->
                val navigationHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
                v.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                    bottomMargin = 22.getScaledPx() + navigationHeight
                }
                insets
            }

            // Full width and self-sizing, exactly as on the home screen.
            textoNavContainer.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.WRAP_CONTENT
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            }

            // Every tab at full opacity, exactly as on the home screen: styleNavTabs marks
            // the current one in colour, and fading the others on top of that took them well
            // below the contrast the design gives them.
            listOf(navHomeIcon, navSearchIcon, navAddIcon).forEach { it.alpha = 1f }
            listOf(navHomeLabel, navSearchLabel, navAddLabel).forEach { it.alpha = 1f }

            styleNavTabs()

            navHomeBtn.setOnClickListener {
                finish() // Go back to main
            }

            navSearchBtn.setOnClickListener {
                finish() // Go back to main and expand search
            }

            navAddBtn.setOnClickListener {
                startActivity(Intent(this@SettingsActivity, NewConversationActivity::class.java))
            }
        } else {
            textoNavContainer.beGone()
        }
    }

    /**
     * The capsule's tabs, painted the same way the home screen paints its own so the two
     * bars are one control that follows you between screens, carrying the same three
     * destinations in the same order.
     */
    private fun styleNavTabs() = binding.apply {
        val muted = config.mainTextColor.withAlpha(0.68f)

        // No lozenge on this screen. The capsule carries the home screen's three
        // destinations, and settings is not one of them : it is reached from the header gear.
        // Marking a tab here would mean marking a screen you are not on.
        listOf(navAddBtn, navHomeBtn, navSearchBtn).forEach { it.background = null }

        val icons = listOf(navHomeIcon, navSearchIcon, navAddIcon)
        val labels = listOf(navHomeLabel, navSearchLabel, navAddLabel)
        icons.forEach { it.applyColorFilter(muted) }
        labels.forEach { it.setTextColor(muted) }

        val glyph = com.texto.sms.helpers.NAV_ICON_DP.getScaledPx()
        icons.forEach { icon ->
            icon.updateLayoutParams {
                width = glyph
                height = glyph
            }
        }
        labels.forEach {
            it.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.78f))
        }
    }

    private fun applyOutlines() = binding.apply {
        val density = resources.displayMetrics.density
        val inputBarTextColor = config.inputBarTextColor
        val isNewUi = config.useNewUi
        
        // Top Bar Outline (Settings)
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
            drawable.setLayerInset(0, 0, statusBarInsetOf(binding.settingsAppbar), 0, 0)
            binding.settingsAppbar.foreground = drawable
        } else {
            binding.settingsAppbar.foreground = null
        }

        // Nav Bar Outline (Settings)
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
            binding.navSearchIcon.imageTintList = android.content.res.ColorStateList.valueOf(inputBarTextColor)
        } else {
            binding.textoNavContainer.foreground = null
        }
    }

    private fun updateCustomizationUI() = binding.apply {
        val mainTextColor = config.mainTextColor

        // Every row's title, subtitle, value and icon is tinted from the theme here rather
        // than in XML, so a theme switch repaints the whole screen without reinflating it.
        styleSettingsRows()
        
        settingsTopBarTextColorLabel.setTextColor(mainTextColor)
        settingsBackgroundColorLabel.setTextColor(mainTextColor)
        settingsInputBarTextColorLabel.setTextColor(mainTextColor)
        settingsSentBubbleColorLabel.setTextColor(mainTextColor)
        settingsSentBubbleTextColorLabel.setTextColor(mainTextColor)
        settingsReceivedBubbleColorLabel.setTextColor(mainTextColor)
        settingsReceivedBubbleTextColorLabel.setTextColor(mainTextColor)

        // Function to update color previews safely without losing shape
        val updatePreview = { view: View, color: Int ->
            val bg = view.background as? android.graphics.drawable.LayerDrawable
            if (bg != null) {
                bg.findDrawableByLayerId(R.id.color_preview_main)?.mutate()?.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                view.background?.applyColorFilter(color)
            }
        }

        // Force all color previews to update based on current config
        updatePreview(settingsTopBarColorPreview, if (config.topBarColor == 0) Color.BLACK else config.topBarColor)
        updatePreview(settingsTopBarTextColorPreview, config.topBarTextColor)
        updatePreview(settingsMainTextColorPreview, config.mainTextColor)
        updatePreview(settingsInputBarTextColorPreview, config.inputBarTextColor)

        updatePreview(settingsSentBubbleColorPreview, config.sentBubbleColor)
        updatePreview(settingsSentBubbleTextColorPreview, config.sentBubbleTextColor)
        updatePreview(settingsReceivedBubbleColorPreview, config.receivedBubbleColor)
        updatePreview(settingsReceivedBubbleTextColorPreview, config.receivedBubbleTextColor)

        updatePreview(settingsColorRecentPreview, config.recentColor)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        if (resultCode != Activity.RESULT_OK || resultData == null) return

        if (requestCode == CROP_RESULT_INTENT && resultCode == RESULT_OK) {
            val target = resultData.getIntExtra(CROP_TARGET, -1)
            val originalUri = resultData.getStringExtra("uri") ?: ""
            val cropRect = resultData.getStringExtra("crop_rect") ?: ""
            
            when (target) {
                CROP_TARGET_TOP_BAR -> {
                    config.topBarImage = originalUri
                    config.topBarCropRect = cropRect
                }
                CROP_TARGET_BACKGROUND -> {
                    config.mainBackgroundImage = originalUri
                    config.mainBgCropRect = cropRect
                }
                CROP_TARGET_SEARCH_BAR -> {
                    config.inputBarImage = originalUri
                    config.inputBarCropRect = cropRect
                }
            }
            applyCustomColors()
            return
        }

        val uri = resultData?.data ?: return
        val uriString = uri.toString()

        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // Not all URIs support persistable permissions (e.g. some file managers)
        }

        when (requestCode) {
            PICK_TOP_BAR_IMAGE_INTENT -> startCropper(uriString, CROP_TARGET_TOP_BAR)
            PICK_MAIN_BG_IMAGE_INTENT -> startCropper(uriString, CROP_TARGET_BACKGROUND)
            PICK_INPUT_BAR_IMAGE_INTENT -> startCropper(uriString, CROP_TARGET_SEARCH_BAR)
        }
    }

    private fun startCropper(uri: String, target: Int) {
        val intent = Intent(this, ImageCropperActivity::class.java).apply {
            putExtra("uri", uri)
            putExtra(CROP_TARGET, target)
        }
        startActivityForResult(intent, CROP_RESULT_INTENT)
    }

    /**
     * The main background's mode picker and the halo animation switch were taken out of
     * the menu. Their stored values are deliberately left alone : the background still
     * renders in whatever mode it was in and the halos still drift if they were drifting, so
     * this only removes the controls, not the behaviour.
     *
     * What survives here is the top and bottom bars, which stopped supporting anything but a
     * plain colour: an install carrying a stale Image selection from before that would paint
     * one thing and preview another.
     */
    private fun setupBarBgMode() {
        if (config.topBarBgMode != BG_MODE_COLOR) {
            config.topBarBgMode = BG_MODE_COLOR
        }
    }

    private fun setupCustomization() = binding.apply {
        val mainTextColor = config.mainTextColor

        val updatePreview = { view: View, color: Int ->
            val bg = view.background as? android.graphics.drawable.LayerDrawable
            if (bg != null) {
                bg.findDrawableByLayerId(R.id.color_preview_main)?.mutate()?.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
            } else {
                view.background?.applyColorFilter(color)
            }
        }

        updatePreview(settingsTopBarColorPreview, if (config.topBarColor == 0) Color.BLACK else config.topBarColor)
        updatePreview(settingsTopBarTextColorPreview, config.topBarTextColor)
        updatePreview(settingsMainTextColorPreview, config.mainTextColor)
        updatePreview(settingsInputBarTextColorPreview, config.inputBarTextColor)

        updatePreview(settingsSentBubbleColorPreview, config.sentBubbleColor)
        updatePreview(settingsSentBubbleTextColorPreview, config.sentBubbleTextColor)
        updatePreview(settingsReceivedBubbleColorPreview, config.receivedBubbleColor)
        updatePreview(settingsReceivedBubbleTextColorPreview, config.receivedBubbleTextColor)

        updatePreview(settingsColorRecentPreview, config.recentColor)

        settingsTopBarTextColorHolder.setOnClickListener {
            pickColour(R.string.settings_top_bar_text, config.topBarTextColor, contrastAgainst = config.topBarColor, defaultColour = skin.topBarTextColor) { color ->
                config.topBarTextColor = color
                updatePreview(settingsTopBarTextColorPreview, color)
                applyCustomColors()
            }
        }

        settingsMainTextColorHolder.setOnClickListener {
            pickColour(R.string.settings_main_text, config.mainTextColor, contrastAgainst = config.mainBackgroundColor, defaultColour = skin.mainTextColor) { color ->
                config.mainTextColor = color
                updatePreview(settingsMainTextColorPreview, color)
                applyCustomColors()
                updateAppFonts(binding.root)
            }
        }

        // SIM badges are identified by colour rather than a slot digit, so each slot needs
        // a colour the user can choose.
        listOf(
            0 to Triple(settingsSim1ColorHolder, settingsSim1ColorPreview, R.string.settings_sim1_color),
            1 to Triple(settingsSim2ColorHolder, settingsSim2ColorPreview, R.string.settings_sim2_color)
        ).forEach { (slot, views) ->
            val (holder, preview, labelRes) = views
            updatePreview(preview, config.getSimColor(slot))
            holder.setOnClickListener {
                pickColour(labelRes, config.getSimColor(slot)) { color ->
                    config.setSimColor(slot, color)
                    updatePreview(preview, color)
                }
            }
        }

        settingsInputBarTextColorHolder.setOnClickListener {
            pickColour(R.string.settings_input_bar_text, config.inputBarTextColor, contrastAgainst = config.inputBarBackgroundColor, defaultColour = skin.inputBarTextColor) { color ->
                config.inputBarTextColor = color
                updatePreview(settingsInputBarTextColorPreview, color)
                applyCustomColors()
            }
        }

        settingsSentBubbleColorHolder.setOnClickListener {
            pickColour(R.string.settings_sent_bubble_color, config.sentBubbleColor, contrastAgainst = config.sentBubbleTextColor) { color ->
                config.sentBubbleColor = color
                updatePreview(settingsSentBubbleColorPreview, color)
            }
        }

        settingsSentBubbleTextColorHolder.setOnClickListener {
            pickColour(R.string.settings_sent_bubble_text, config.sentBubbleTextColor, contrastAgainst = config.sentBubbleColor, defaultColour = skin.sentBubbleTextColor) { color ->
                config.sentBubbleTextColor = color
                updatePreview(settingsSentBubbleTextColorPreview, color)
            }
        }

        settingsReceivedBubbleColorHolder.setOnClickListener {
            pickColour(R.string.settings_received_bubble_color, config.receivedBubbleColor, contrastAgainst = config.receivedBubbleTextColor, defaultColour = skin.receivedBubbleColor) { color ->
                config.receivedBubbleColor = color
                // Marks the gradient as overridden on that side; see Config.
                config.receivedBubbleColorSet = true
                updatePreview(settingsReceivedBubbleColorPreview, color)
            }
        }

        settingsReceivedBubbleTextColorHolder.setOnClickListener {
            pickColour(R.string.settings_received_bubble_text, config.receivedBubbleTextColor, contrastAgainst = config.receivedBubbleColor, defaultColour = skin.receivedBubbleTextColor) { color ->
                config.receivedBubbleTextColor = color
                updatePreview(settingsReceivedBubbleTextColorPreview, color)
            }
        }

        settingsColorRecentHolder.setOnClickListener {
            pickColour(R.string.settings_conversation_card_color, config.recentColor, contrastAgainst = config.mainTextColor, defaultColour = skin.cardColor) { color ->
                config.recentColor = color
                updatePreview(settingsColorRecentPreview, color)
            }
        }

        // Shaped, like every other number the app shows. The version was the one figure on
        // the settings screen still reading in Latin digits.
        settingsAboutVersion.text = BuildConfig.VERSION_NAME.toUiDigits()
        // Commons' about screen is gone from here. It was opened with licenseMask 0 and an
        // empty FAQ list, so it carried nothing this app had put in it, and it is one of the
        // screens `setupDialogStuff` builds from the base theme : a white page in a foreign
        // face at the end of a skinned settings list. For now the row shows the maker's mark
        // and nothing else.
        settingsAboutHolder.setOnClickListener { showAboutSheet() }

        settingsResetDefaults.setOnClickListener {
            // Everything, not only the colours: the font, its size, the UI scale, the
            // bubble outlines, the delivery reports, the recycle bin -- every setting the
            // app stores. resetColors() named its keys one at a time and so kept missing
            // whatever had been added since.
            config.resetAllSettings()
            // resetColors() clears APP_THEME along with every colour, so without re-applying
            // here the screen would come back on the bare code defaults and only settle on
            // the real default theme at the next cold start, when App.onCreate notices that
            // nothing is stored. Reset now lands where a fresh install lands.
            // NEON_LIGHT, not NEON: the light variant is what a fresh install opens on, and
            // this was left pointing at the dark one when that default changed, so a reset
            // came back on a theme the app never starts with.
            AppThemes.apply(config, AppThemes.byId(AppThemes.NEON_LIGHT))
            config.glassOpacity = Config.DEFAULT_GLASS_OPACITY
            // App.onCreate writes these on every launch to keep the upstream first-run
            // popups away, but the reset does not restart the process -- only the activity --
            // so they are put back here rather than left at zero until the next cold start.
            config.appId = packageName
            config.appSideloadingStatus = 0
            config.hadThankYouInstalled = true
            config.appRunCount = 100
            finish()
            startActivity(intent)
        }

        // The last row that still opened commons' picker. It is the bar the header, the nav
        // pill and the chips are all painted from, so of every colour here it is the one most
        // worth seeing previewed live rather than behind a white modal.
        settingsTopBarPreviewContainer.setOnClickListener {
            val current = if (config.topBarColor == 0) Color.BLACK else config.topBarColor
            pickColour(
                titleRes = R.string.settings_top_bar_background,
                current = current,
                contrastAgainst = config.topBarTextColor,
                defaultColour = skin.topBarColor
            ) { color ->
                // Zero is the "follow the skin" marker rather than a colour; see Config.
                config.topBarColor = if (color == Color.BLACK) 0 else color
                updatePreview(settingsTopBarColorPreview, color)
                applyCustomColors()
            }
        }

    }


    /**
     * The contacts-only filter toggle now lives on the main settings screen (outside every
     * collapsible section), so it is set up on its own rather than from setupNewUi().
     * MyMaterialSwitch is its own clickable View: a tap landing on the switch is consumed by
     * it before reaching the holder's click listener, so persistence is driven from the
     * switch's own checked-change callback (fires however the toggle happened) and the row
     * just forwards taps to the switch.
     */
    /**
     * The halos are always painted; this only decides whether they drift. Applying it needs a
     * fresh drawable, which is what applyCustomColors() builds.
     */
    /**
     * Shows or hides the built-in "مخاطبین" chip.
     *
     * Switching it off has to release the two ids that can still be pointing at it, exactly
     * as [setupAdsFilter] does for its own chip. Without that the *active* filter self-heals
     * -- buildFilterChips falls back to the first chip when the stored id is not on the row
     * -- but the *default* one does not: it stays "contacts_only" in prefs while the row here
     * reads "همه", and re-enabling the chip months later silently reopens the app filtered by
     * something the user never chose.
     */
    private fun setupContactsOnlyFilter() = binding.apply {
        settingsContactsOnlyFilterSwitch.isChecked = config.showContactsOnlyFilter
        settingsContactsOnlyFilterSwitch.setOnCheckedChangeListener { _, isChecked ->
            config.showContactsOnlyFilter = isChecked
            if (!isChecked && config.activeFilterId == MessageFilter.ID_CONTACTS_ONLY) {
                config.activeFilterId = MessageFilter.ID_ALL
            }
            if (!isChecked && config.defaultFilterId == MessageFilter.ID_CONTACTS_ONLY) {
                config.defaultFilterId = MessageFilter.ID_ALL
            }
            settingsDefaultFilter.text = defaultFilterLabel()
        }
        settingsContactsOnlyFilterHolder.setOnClickListener {
            settingsContactsOnlyFilterSwitch.toggle()
        }
    }

    /**
     * Shows or hides the built-in "بدون تبلیغات" chip. Switching it off keeps the marked
     * senders, so turning it back on restores the same exclusions.
     *
     * Switching it *on* also makes it the filter the app opens in. Turning it on is a
     * statement about which list you want to see, and leaving the default pointing at "همه"
     * meant the setting only took effect after a tap on every launch.
     */
    private fun setupAdsFilter() = binding.apply {
        settingsAdsFilterSwitch.isChecked = config.showAdsFilter
        settingsAdsFilterSwitch.setOnCheckedChangeListener { _, isChecked ->
            config.showAdsFilter = isChecked
            if (isChecked) {
                config.defaultFilterId = MessageFilter.ID_NO_ADS
                config.activeFilterId = MessageFilter.ID_NO_ADS
                // The filter is empty of exclusions until senders are marked, so on its own
                // it changes nothing visible. Said once, here, rather than left to be found.
                textoConfirmDialog(
                    message = getString(R.string.filter_no_ads_explainer),
                    title = getString(R.string.filter_no_ads),
                    negativeLabel = null
                ) {}
            }
            // A chip that has just disappeared must not stay selected, or the list would
            // open filtered by something with no way back to it.
            if (!isChecked && config.activeFilterId == MessageFilter.ID_NO_ADS) {
                config.activeFilterId = MessageFilter.ID_ALL
            }
            if (!isChecked && config.defaultFilterId == MessageFilter.ID_NO_ADS) {
                config.defaultFilterId = MessageFilter.ID_ALL
            }
            settingsDefaultFilter.text = defaultFilterLabel()
        }
        settingsAdsFilterHolder.setOnClickListener {
            settingsAdsFilterSwitch.toggle()
        }
    }

    /** Every chip that can currently appear, in the order the main screen shows them. */
    private fun selectableFilters(): List<Pair<String, String>> = buildList {
        add(MessageFilter.ID_ALL to getString(R.string.filter_all))
        if (config.showContactsOnlyFilter) {
            add(MessageFilter.ID_CONTACTS_ONLY to getString(R.string.filter_contacts_only))
        }
        if (config.showAdsFilter) {
            add(MessageFilter.ID_NO_ADS to getString(R.string.filter_no_ads))
        }
        config.customFilters.forEach { add(it.id to it.label) }
    }

    private fun defaultFilterLabel(): String {
        val filters = selectableFilters()
        // A filter that has since been deleted or switched off reads as "All", which is what
        // the main screen would fall back to anyway.
        return filters.firstOrNull { it.first == config.defaultFilterId }?.second
            ?: getString(R.string.filter_all)
    }

    private fun setupDefaultFilter() = binding.apply {
        settingsDefaultFilter.text = defaultFilterLabel()
        settingsDefaultFilterHolder.setOnClickListener {
            val filters = selectableFilters()
            val choices = filters.map { (id, label) ->
                com.texto.sms.helpers.CapsuleChoice(
                    label = label,
                    isActive = config.defaultFilterId == id,
                    onPick = {
                        config.defaultFilterId = id
                        settingsDefaultFilter.text = defaultFilterLabel()
                    }
                )
            }
            textoCapsuleDialog(getString(R.string.default_filter), choices)
        }
    }

    /**
     * Picking a theme rewrites the individual colour settings, so the rest of the
     * customisation screen keeps working and anything can still be tweaked afterwards.
     */
    /**
     * The swatch row now only picks a skin -- Classic, Aurora, Nocturne or Neon -- and leaves
     * day/night to the "تم تاریک" switch above it. Aurora and Nocturne used to list their
     * light variant as a second, separate entry; that pairing now lives in
     * [com.texto.sms.helpers.AppThemes.families] instead, one row per skin regardless of which
     * of its two variants is actually applied.
     */
    private fun setupAppTheme() = binding.apply {
        settingsAppTheme.text =
            getString(com.texto.sms.helpers.AppThemes.familyOf(config.appTheme).labelRes)

        settingsAppThemeHolder.setOnClickListener {
            val isDark = com.texto.sms.helpers.AppThemes.isDarkVariant(config.appTheme)
            // The same capsule rows the SIM chooser uses, each carrying the skin's own accent
            // as its swatch: a radio list named four themes without showing any of them, and
            // came up on commons' light dialog ground in a face nothing else here uses.
            val choices = com.texto.sms.helpers.AppThemes.families.map { family ->
                val preview = family.forDark(isDark)
                com.texto.sms.helpers.CapsuleChoice(
                    label = getString(family.labelRes),
                    swatch = preview.accentGradient.first,
                    swatchEnd = preview.accentGradient.second,
                    isActive = family.has(config.appTheme),
                    onPick = {
                        com.texto.sms.helpers.AppThemes.apply(config, preview)
                        settingsAppTheme.text = getString(family.labelRes)
                        setupAccentHue()
                        updateCustomizationUI()
                        updateAppFonts(binding.root)
                        applyCustomColors()
                        refreshDarkModeSwitch()
                        toast(R.string.theme_applied)
                    }
                )
            }

            textoCapsuleDialog(getString(R.string.app_theme), choices)
        }
    }

    /**
     * Day/night for whichever skin is active. Switching it re-applies the *same* family at
     * the other variant -- the swatch row's own selection never changes -- so this is the one
     * place a light/dark pair actually differs from picking a whole new theme. Classic has no
     * counterpart (both its slots are the same instance), so the switch disables itself rather
     * than accepting a tap that would visibly flip with nothing behind it to apply.
     */
    private fun setupDarkModeSwitch() = binding.apply {
        refreshDarkModeSwitch()
        settingsDarkModeHolder.setOnClickListener {
            // Guarded on the switch's own enabled state: the row forwarded taps to toggle()
            // unconditionally, so tapping it under Classic -- whose switch is deliberately
            // disabled because the skin has no light counterpart -- still flipped it.
            if (settingsDarkModeSwitch.isEnabled) settingsDarkModeSwitch.toggle()
        }
    }

    /** Re-reads which family is active and whether it actually has two variants to switch between. */
    private fun refreshDarkModeSwitch() = binding.apply {
        val family = com.texto.sms.helpers.AppThemes.familyOf(config.appTheme)
        val hasCounterpart = family.dark.id != family.light.id
        settingsDarkModeSwitch.setOnCheckedChangeListener(null)
        settingsDarkModeSwitch.isChecked = com.texto.sms.helpers.AppThemes.isDarkVariant(config.appTheme)
        settingsDarkModeSwitch.isEnabled = hasCounterpart
        settingsDarkModeHolder.alpha = if (hasCounterpart) 1f else 0.45f
        settingsDarkModeSwitch.setOnCheckedChangeListener { _, isChecked ->
            val target = family.forDark(isChecked)
            if (target.id == config.appTheme) return@setOnCheckedChangeListener
            com.texto.sms.helpers.AppThemes.apply(config, target)
            setupAccentHue()
            updateCustomizationUI()
            updateAppFonts(binding.root)
            applyCustomColors()
        }
    }

    /**
     * Archive and the recycle bin. They used to hang off the main
     * screen's overflow menu only, which meant the recycle bin vanished entirely whenever
     * its own setting was off and left no way to reach what was already in it.
     */
    private fun setupConversationScreens() = binding.apply {
        settingsArchivedHolder.setOnClickListener {
            startActivity(Intent(this@SettingsActivity, ArchivedConversationsActivity::class.java))
        }
        settingsRecycleBinHolder.setOnClickListener {
            startActivity(Intent(this@SettingsActivity, RecycleBinConversationsActivity::class.java))
        }
    }

    private fun setupBlockedNumbers() = binding.apply {
        settingsBlockedNumbersHolder.setOnClickListener {
            startActivity(Intent(this@SettingsActivity, BlockedNumbersActivity::class.java))
        }
        org.fossify.commons.helpers.ensureBackgroundThread {
            // Explicit receiver: inside binding.apply the implicit `this` is the binding,
            // not the Context the extension needs.
            val count = with(com.texto.sms.helpers.SystemBlockedNumbers) {
                this@SettingsActivity.listAll().size
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                settingsBlockedNumbersCount.text = count.toUiDigits()
                settingsBlockedNumbersCount.setTextColor(config.mainTextColor)
            }
        }
    }


    /**
     * Material's Slider throws IllegalStateException the first time it is measured with a
     * value that is not valueFrom plus a whole number of steps -- a crash that only shows up
     * when the section holding the slider is expanded. Snap and clamp before assigning so a
     * stored preference can never take the screen down.
     */
    /**
     * Paints a slider from the live theme.
     *
     * Material's default track and thumb come from the base (light) theme's colour attributes,
     * so both sliders came up in a palette that belonged to no skin the app actually ships:
     * the two controls on this screen were the only things on it not following the theme.
     */
    /**
     * Paints a switch from the live theme, for the same reason the sliders needed it: the
     * Material default track is the base theme's lavender, which belongs to no skin the app
     * ships and was the last thing on this screen not following the accent.
     */
    private fun com.google.android.material.materialswitch.MaterialSwitch.applyTextoStyle() {
        val accent = config.accentGradientStart
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        trackTintList = android.content.res.ColorStateList(
            states, intArrayOf(accent.withAlpha(0.45f), config.mainTextColor.withAlpha(0.16f))
        )
        thumbTintList = android.content.res.ColorStateList(
            states, intArrayOf(accent, config.mainTextColor.withAlpha(0.62f))
        )
        trackDecorationTintList = android.content.res.ColorStateList(
            states, intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
    }

    /** Every switch on the screen, found by walking the tree so a new row is covered too. */
    private fun styleAllSwitches(view: android.view.View) {
        if (view is com.google.android.material.materialswitch.MaterialSwitch) {
            view.applyTextoStyle()
            return
        }
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) styleAllSwitches(view.getChildAt(i))
        }
    }

    private fun com.google.android.material.slider.Slider.applyTextoStyle() {
        val accent = config.accentGradientStart
        trackActiveTintList = android.content.res.ColorStateList.valueOf(accent)
        trackInactiveTintList =
            android.content.res.ColorStateList.valueOf(config.mainTextColor.withAlpha(0.16f))
        thumbTintList = android.content.res.ColorStateList.valueOf(accent)
        haloTintList = android.content.res.ColorStateList.valueOf(accent.withAlpha(0.20f))
        // The step is one unit on both sliders, so the tick marks would be a solid line.
        tickActiveTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
        tickInactiveTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
        trackHeight = 6.getScaledPx()
        thumbRadius = 9.getScaledPx()
        thumbStrokeWidth = 0f
    }

    private fun com.google.android.material.slider.Slider.setSteppedValue(raw: Float) {
        val snapped = if (stepSize > 0f) {
            valueFrom + Math.round((raw - valueFrom) / stepSize) * stepSize
        } else {
            raw
        }
        value = snapped.coerceIn(valueFrom, valueTo)
    }

    private fun setupUIScale() = binding.apply {
        settingsUiScaleSlider.applyTextoStyle()
        settingsUiScaleSlider.setSteppedValue(config.uiScale)
        settingsUiScaleValue.text = uiScaleLabel(config.uiScale)
        settingsUiScaleValue.setTextColor(config.accentGradientStart)
        settingsUiScaleSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                config.uiScale = value
                settingsUiScaleValue.text = uiScaleLabel(value)
            }
        }
    }

    /** Shown beside the slider as a percentage, so the raw 0.5-1.5 factor never surfaces. */
    private fun uiScaleLabel(value: Float) =
        "${Math.round(value * 100)}$uiPercentSign".toUiDigits()

    private fun setupGlassOpacity() = binding.apply {
        settingsGlassOpacitySlider.applyTextoStyle()
        settingsGlassOpacitySlider.setSteppedValue(config.glassOpacity.toFloat())
        settingsGlassOpacityValue.text = glassOpacityLabel(config.glassOpacity)
        settingsGlassOpacityValue.setTextColor(config.accentGradientStart)
        settingsGlassOpacitySlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                config.glassOpacity = value.toInt()
                settingsGlassOpacityValue.text = glassOpacityLabel(value.toInt())
                // Repaint straight away so the bar behind the slider previews the new value.
                applyCustomColors()
            }
        }
    }

    /** The stored value is opacity; the design's label reads as transparency, so invert it. */
    private fun glassOpacityLabel(opacity: Int) = "${100 - opacity}$uiPercentSign".toUiDigits()

    /**
     * The tonality strip. Dragging it rewrites one number, and every accent surface in the
     * app follows because they all read [Config]'s accent getters, which apply the rotation
     * on the way out.
     *
     * The strip needs the skin's UNROTATED accent to build its preview, and the getter cannot
     * give it that -- by the time a colour leaves Config the rotation is already in it. Undoing
     * the current shift recovers the stored value without reaching past Config into raw prefs,
     * and it stays correct for a hand-picked accent, which reading the theme table would not.
     */
    private fun setupAccentHue() = binding.apply {
        val stored = config.accentHueShift
        settingsAccentHueValue.text = accentHueLabel(stored)
        settingsAccentHueValue.setTextColor(config.accentGradientStart)

        // The skin's own accent with the current rotation taken back out, so each dot below
        // can be painted as "this skin, rotated by N" rather than as a raw hue.
        val base = com.texto.sms.helpers.TextoTint.rotateHue(
            config.accentGradientMid.takeIf { it != 0 } ?: config.accentGradientEnd, -stored
        )

        // The swatch every other colour row carries, so the row says what it opens before it
        // is opened. Painted with the accent *gradient* rather than one stop, because that is
        // what a tonality moves and what makes it recognisable as this row and not another
        // flat colour. Taking the strip out of the row had left it as bare text.
        settingsAccentHuePreview.background = com.texto.sms.helpers.TextoGlass.accent(
            start = config.accentGradientStart,
            end = config.accentGradientEnd,
            cornerRadius = 100f * resources.displayMetrics.density,
            mid = config.accentGradientMid
        )

        // Opens the same sheet every other colour row opens, rather than sitting open in the
        // row. This was the one colour on the screen chosen a different way: first a grid of
        // dots, then a strip that had to be scrolled to the middle to be read, while its
        // neighbours all opened a picker.
        val degrees = (0 until 360 step ACCENT_HUE_STEP).toList()
        settingsAccentHueHolder.setOnClickListener {
            textoTonalityPicker(
                title = getString(R.string.settings_accent_hue),
                degrees = degrees,
                current = config.accentHueShift,
                baseColour = base
            ) { chosen -> applyAccentHue(chosen) }
        }
    }

    /**
     * Commits a tonality and repaints everything that carries the accent.
     *
     * The nav capsule, the switches and the two sliders are painted by their own setup passes
     * rather than by [applyCustomColors], so without the last four calls they keep the old
     * tonality until the screen is next resumed : the corner of the screen that visibly
     * disagreed with the choice just made.
     */
    private fun applyAccentHue(degrees: Int) {
        config.accentHueShift = degrees

        // Still not setupAccentHue(): that would re-register the row's click listener on
        // every step of a drag. Only the two things the row actually shows are repainted.
        binding.settingsAccentHueValue.text = accentHueLabel(degrees)
        binding.settingsAccentHueValue.setTextColor(config.accentGradientStart)
        binding.settingsAccentHuePreview.background = com.texto.sms.helpers.TextoGlass.accent(
            start = config.accentGradientStart,
            end = config.accentGradientEnd,
            cornerRadius = 100f * resources.displayMetrics.density,
            mid = config.accentGradientMid
        )

        applyCustomColors()
        updateCustomizationUI()
        updateAppFonts(binding.root)
        styleNavTabs()
        styleAllSwitches(binding.root)
        binding.settingsUiScaleSlider.applyTextoStyle()
        binding.settingsUiScaleValue.setTextColor(config.accentGradientStart)
        binding.settingsGlassOpacitySlider.applyTextoStyle()
        binding.settingsGlassOpacityValue.setTextColor(config.accentGradientStart)
    }

    /**
     * Every colour row on this screen goes through here, including the two that used to slip
     * past it into commons' `ColorPickerDialog` : a white card in a foreign typeface, and the
     * one surface left in the app that ignored the skin completely.
     *
     * [contrastAgainst] is what will be written on this colour, or the ground it will be
     * written on, so the sheet can say whether the pair is readable before it is committed.
     * Null for a row that carries no text, like a SIM slot's dot.
     */
    private fun pickColour(
        titleRes: Int,
        current: Int,
        contrastAgainst: Int? = null,
        defaultColour: Int? = null,
        onChosen: (Int) -> Unit,
    ) {
        textoColorPicker(
            title = getString(titleRes),
            current = current,
            defaultColour = defaultColour,
            contrastAgainst = contrastAgainst,
            onPick = onChosen
        )
    }

    /** The active skin's own value for a slot, which is what "Default" puts back. */
    private val skin get() = com.texto.sms.helpers.AppThemes.byId(config.appTheme)

    /**
     * The about sheet: the maker's mark on the app's own card, and the version.
     *
     * Deliberately just the image for now. The artwork is wider than it is tall, so it is
     * given the card's own corner and left to size itself by its aspect ratio rather than
     * cropped to a box that would cut the wordmark off.
     */
    private fun showAboutSheet() {
        val density = resources.displayMetrics.density
        val pad = 20.getScaledPx()
        val sheet = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 26 * density
                setColor(config.recentColor)
                setStroke(
                    1.getScaledPx(),
                    com.texto.sms.helpers.TextoGlass.rimFor(config.recentColor, 0.18f)
                )
            }
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }

        sheet.addView(
            android.widget.ImageView(this).apply {
                setImageResource(R.drawable.img_about_hjt)
                adjustViewBounds = true
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                contentDescription = getString(R.string.about)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = 18 * density
                }
                outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                clipToOutline = true
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        )

        sheet.addView(
            TextView(this).apply {
                text = BuildConfig.VERSION_NAME.toUiDigits()
                gravity = android.view.Gravity.CENTER
                setTextColor(config.mainTextColor.withAlpha(0.58f))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.8f))
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 14.getScaledPx() }
            }
        )

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(sheet)
            .create()
            .apply {
                window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
                show()
            }
    }

    /** Centre reads as the theme's own colour rather than as a meaningless zero. */
    private fun accentHueLabel(degrees: Int) =
        if (degrees == 0) getString(R.string.settings_accent_hue_original)
        else "${degrees}°".toUiDigits()

    /**
     * The four sizes, and their labels. Commons has its own set of these, but its
     * strings arrive in English under this app's locked locale, so the row read "Medium".
     */
    private val fontSizes = listOf(
        org.fossify.commons.helpers.FONT_SIZE_SMALL to R.string.font_size_small,
        org.fossify.commons.helpers.FONT_SIZE_MEDIUM to R.string.font_size_medium,
        org.fossify.commons.helpers.FONT_SIZE_LARGE to R.string.font_size_large,
        org.fossify.commons.helpers.FONT_SIZE_EXTRA_LARGE to R.string.font_size_extra_large,
    )


    /** The three states of the language setting, in the order the sheet lists them. */
    private val languages = listOf(
        com.texto.sms.helpers.TextoLocale.SYSTEM to R.string.language_system,
        com.texto.sms.helpers.TextoLocale.PERSIAN to R.string.language_persian,
        com.texto.sms.helpers.TextoLocale.ENGLISH to R.string.language_english,
    )

    /**
     * Language, on the same capsule sheet every other chooser here uses.
     *
     * Picking one has to restart the activity stack rather than repaint it: the locale is
     * fixed when a Context is created, in `attachBaseContext`, so every string, every
     * layout direction and every already-inflated view on screen belongs to the old
     * language until the activity is built again. Settings is recreated so the change is
     * visible immediately, and MainActivity behind it is cleared so it rebuilds on the way
     * back instead of coming forward still speaking the previous language.
     */
    private fun setupLanguage() = binding.apply {
        settingsLanguage.text = getString(languageLabelRes())
        settingsLanguageHolder.setOnClickListener {
            val choices = languages.map { (id, labelRes) ->
                com.texto.sms.helpers.CapsuleChoice(
                    label = getString(labelRes),
                    subtitle = if (id == com.texto.sms.helpers.TextoLocale.SYSTEM) {
                        getString(R.string.language_system_sub)
                    } else {
                        null
                    },
                    isActive = config.appLanguage == id,
                    onPick = {
                        if (config.appLanguage != id) {
                            config.appLanguage = id
                            applyLanguageChange()
                        }
                    }
                )
            }
            textoCapsuleDialog(getString(R.string.settings_language), choices)
        }
    }

    private fun languageLabelRes(): Int =
        languages.firstOrNull { it.first == config.appLanguage }?.second
            ?: R.string.language_system

    private fun applyLanguageChange() {
        // The task is restarted from the launcher activity: recreating only this screen
        // would leave every activity under it -- and the conversation list is always under
        // it -- holding the old locale's resources.
        val intent = Intent(this@SettingsActivity, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
        startActivity(Intent(this@SettingsActivity, SettingsActivity::class.java))
        overridePendingTransition(0, 0)
        finish()
    }
    private fun setupFontSize() = binding.apply {
        settingsFontSize.text = getFontSizeText()
        settingsFontSizeHolder.setOnClickListener {
            // The app's own capsule sheet, like every other chooser here. A commons radio
            // list came up on the base theme's light ground in a face nothing else uses.
            val choices = fontSizes.map { (size, labelRes) ->
                com.texto.sms.helpers.CapsuleChoice(
                    label = getString(labelRes),
                    isActive = config.fontSize == size,
                    onPick = {
                        config.fontSize = size
                        settingsFontSize.text = getFontSizeText()
                        updateAppFonts(binding.root)
                    }
                )
            }
            textoCapsuleDialog(getString(R.string.settings_font_size), choices)
        }
    }

    private fun getFontSizeText() = getString(
        fontSizes.firstOrNull { it.first == config.fontSize }?.second
            ?: R.string.font_size_extra_large
    )

    private fun setupFontFamily() = binding.apply {
        settingsFont.text = getFontText()
        settingsFontHolder.setOnClickListener {
            // Only the system font and the bundled Persian faces are offered; the Latin
            // families that used to be here were never a sensible choice for a Persian UI.
            //
            // A face is picked from the app's own capsule sheet rather than a commons radio
            // list: choosing a typeface on a dialog drawn in a *different* typeface was the
            // one place where that mismatch was impossible to miss.
            val faces = listOf(0 to getString(R.string.font_system_default)) +
                com.texto.sms.helpers.TextoFonts.displayNames.map { (id, name) -> id to name }

            val choices = faces.map { (id, name) ->
                // Persian typefaces read as unavailable until their file is dropped into
                // assets/fonts; the row still picks, and says so with a toast.
                val installed = id == 0 ||
                    com.texto.sms.helpers.TextoFonts.isInstalled(this@SettingsActivity, id)
                com.texto.sms.helpers.CapsuleChoice(
                    label = name,
                    subtitle = if (installed) null else getString(R.string.font_not_installed),
                    isActive = config.fontFamilyTexto == id,
                    onPick = {
                        if (!installed) toast(R.string.font_not_installed)
                        config.fontFamilyTexto = id
                        settingsFont.text = getFontText()
                        updateAppFonts(binding.root)
                        applyCustomColors()
                    }
                )
            }
            textoCapsuleDialog(getString(R.string.settings_font), choices)
        }
    }

    /** Anything that is not a bundled Persian face now reads as the system font, which also
     *  covers a value left behind by one of the Latin families that has been dropped. */
    private fun getFontText(): String =
        com.texto.sms.helpers.TextoFonts.displayNames[config.fontFamilyTexto]
            ?: getString(R.string.font_system_default)

    /** Every grouped card on the screen, in display order. */
    private fun settingsCards() = binding.run {
        listOf(
            settingsCardGeneral,
            settingsCardConversations, settingsCardFilters, settingsCardAppearance,
            settingsCardSizeFont, settingsCardTextColors, settingsCardConversationColors,
            settingsCardBubbles, settingsCardAbout
        )
    }

    /**
     * Paints the grouped cards and walks every row inside them applying the theme's text
     * colour. Doing it here rather than in XML means one pass repaints the whole screen
     * after a theme change, and new rows pick the styling up for free.
     */
    private fun styleSettingsRows() {
        val mainTextColor = config.mainTextColor
        val cardRadius = config.cardCornerRadiusDp * resources.displayMetrics.density

        // The hairlines between rows inside a card. They were a fixed white at 12%, which is
        // the right weight on the dark skins and completely invisible on the light ones --
        // the settings cards lost every separator the moment the theme went light. Drawn
        // from the same rimFor() the glass surfaces use, so they flip with the ground.
        tintDividers(binding.root)

        settingsCards().forEach { card ->
            // The design's settings card is the same glass wash as a thread row, behind the
            // shared `--divider` hairline: `border: 1px solid var(--divider)` on
            // `background: var(--glass)`. The rows inside it keep their own separators.
            TextoGlass.applyPanel(
                view = card,
                tint = config.recentColor,
                cornerRadius = cardRadius,
                opacity = if (config.glassTheme) 0.68f else 1f,
                strokeWidthPx = 1.getScaledPx(),
                rimAlpha = 0.22f,
                sheenAlpha = 0f
            )
            card.clipToOutline = true
            card.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cardRadius)
                }
            }
            tintRowsIn(card, mainTextColor)
            scaleRowsIn(card)
        }

        // Section labels sit outside the cards, so they are tinted and sized separately. The
        // reset row is the one destructive action on the screen and keeps its warning colour.
        val holder = binding.settingsHolder
        for (i in 0 until holder.childCount) {
            (holder.getChildAt(i) as? TextView)?.apply {
                setTextColor(mainTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.8f))
            }
        }
        binding.settingsResetDefaults.apply {
            setTextColor(DESTRUCTIVE_COLOR)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())
            val padV = 16.getScaledPx()
            setPadding(0, padV, 0, padV)
        }
    }

    /**
     * Paints every row separator in the tree from the live theme. Tagged rather than given
     * sixteen ids: they carry no other behaviour and nothing else needs to address them.
     */
    private fun tintDividers(view: View) {
        if (view.tag == DIVIDER_TAG) {
            view.setBackgroundColor(TextoGlass.rimFor(config.recentColor, 0.30f))
            return
        }
        (view as? ViewGroup)?.let {
            for (i in 0 until it.childCount) tintDividers(it.getChildAt(i))
        }
    }

    /**
     * The rows are laid out in dp, which ignores the UI-scale setting the rest of the app
     * honours -- at 1.4x the type grew but the rows around it did not, so the text crowded
     * its own padding. Every measurement on a row is re-applied here through
     * [getScaledPx]/[getScaledTextSize] instead of being duplicated across 1300 lines of XML.
     *
     * Recognises a row by shape rather than by id: a horizontal [LinearLayout] whose first
     * child is the leading glyph. That keeps new rows working without being listed here.
     */
    private fun scaleRowsIn(card: View) {
        val group = card as? ViewGroup ?: return
        for (i in 0 until group.childCount) {
            val row = group.getChildAt(i)
            if (row !is ViewGroup) continue

            if (row is android.widget.LinearLayout && row.orientation == android.widget.LinearLayout.HORIZONTAL) {
                row.setPadding(
                    16.getScaledPx(), 15.getScaledPx(), 16.getScaledPx(), 15.getScaledPx()
                )
                // The design seats each row's glyph in its own tile rather than letting it
                // sit bare on the card: a 36px square on a 0.9rem radius, filled with
                // `--primary-soft` behind the `--divider` hairline. The glyph itself keeps
                // the accent colour tintRowsIn gives it.
                (row.getChildAt(0) as? android.widget.ImageView)?.apply {
                    val tile = SETTINGS_TILE_DP.getScaledPx()
                    updateLayoutParams<android.widget.LinearLayout.LayoutParams> {
                        width = tile
                        height = tile
                        marginEnd = 13.getScaledPx()
                    }
                    val inset = (tile - SETTINGS_GLYPH_DP.getScaledPx()) / 2
                    setPadding(inset, inset, inset, inset)
                    val accent = config.accentGradientStart
                    background = android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                        cornerRadius = SETTINGS_TILE_RADIUS_DP * resources.displayMetrics.density
                        setColor(accent.withAlpha(0.14f))
                        setStroke(
                            1.getScaledPx(),
                            com.texto.sms.helpers.TextoGlass.rimFor(config.recentColor, 0.22f)
                        )
                    }
                }
            } else {
                // Nested holders (a row wrapped in another column) get the same pass.
                scaleRowsIn(row)
            }
        }
    }

    /**
     * Recursively tints a card's text and icons, and sizes its type. Colour swatches are
     * plain [View]s, so they fall through every branch here and keep the fill that
     * represents their stored value.
     */
    private fun tintRowsIn(view: View, textColor: Int) {
        when (view) {
            is TextView -> view.setTextColor(textColor)
            // Row glyphs are the design's one splash of the accent hue on this screen, so
            // they deliberately do not follow the text colour the way everything else does.
            is android.widget.ImageView -> view.applyColorFilter(config.auroraAccentColor)
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    tintRowsIn(view.getChildAt(i), textColor)
                }
            }
            // The hairlines between rows. They were a fixed white wash in XML, which only
            // looked right on a dark theme; deriving them from the text colour keeps them
            // legible whichever way the theme goes.
            else -> if (view.id == View.NO_ID && view.layoutParams?.height == dividerHeight) {
                view.setBackgroundColor(textColor.withAlpha(0.12f))
            }
        }
    }

    /** 1dp in pixels: how the row hairlines are recognised in [tintRowsIn]. */
    private val dividerHeight: Int
        get() = resources.displayMetrics.density.toInt().coerceAtLeast(1)

    private companion object {
        /** The design's `--destructive`, oklch(0.65 0.21 22). */
        val DESTRUCTIVE_COLOR = Color.parseColor("#F54651")

        /** Marks a row separator in activity_settings.xml so [tintDividers] can find it. */
        const val DIVIDER_TAG = "texto_divider"

        /**
         * The tonality row: twenty-four rotations, 15° apart, in four lines of six.
         *
         * Twelve read as too few once the colours were evened out in OKLCh -- neighbouring
         * stops stopped shouting at each other and started looking like one another. The
         * launcher icon still ships only twelve pre-rendered rotations and lands on the
         * nearest, so every second stop here matches it exactly and the ones between it are
         * off by at most 7.5°, half of what the old continuous strip could be.
         */
        const val ACCENT_HUE_STEP = 15
        const val ACCENT_HUE_COLUMNS = 6
        const val ACCENT_DOT_DP = 44

        /** The design draws a settings row glyph at 18px, seated in its own tile. */
        const val SETTINGS_GLYPH_DP = 18

        /** That tile: 36px on a 0.9rem radius, filled with `--primary-soft`. */
        const val SETTINGS_TILE_DP = 36
        const val SETTINGS_TILE_RADIUS_DP = 14
    }
}
