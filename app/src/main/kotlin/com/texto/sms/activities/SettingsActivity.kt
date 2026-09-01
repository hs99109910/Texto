package com.texto.sms.activities

import android.app.Activity
import android.graphics.Color
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
import com.texto.sms.extensions.toPersianDigits
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
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.topBarTextColor) { wasPositive, color ->
                if (wasPositive) {
                    config.topBarTextColor = color
                    updatePreview(settingsTopBarTextColorPreview, color)
                    applyCustomColors()
                }
            }
        }

        settingsMainTextColorHolder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.mainTextColor) { wasPositive, color ->
                if (wasPositive) {
                    config.mainTextColor = color
                    updatePreview(settingsMainTextColorPreview, color)
                    applyCustomColors()
                    updateAppFonts(binding.root)
                }
            }
        }

        // SIM badges are identified by colour rather than a slot digit, so each slot needs
        // a colour the user can choose.
        listOf(
            0 to (settingsSim1ColorHolder to settingsSim1ColorPreview),
            1 to (settingsSim2ColorHolder to settingsSim2ColorPreview)
        ).forEach { (slot, views) ->
            val (holder, preview) = views
            updatePreview(preview, config.getSimColor(slot))
            holder.setOnClickListener {
                org.fossify.commons.dialogs.ColorPickerDialog(
                    this@SettingsActivity, config.getSimColor(slot)
                ) { wasPositive, color ->
                    if (wasPositive) {
                        config.setSimColor(slot, color)
                        updatePreview(preview, color)
                    }
                }
            }
        }

        settingsInputBarTextColorHolder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.inputBarTextColor) { wasPositive, color ->
                if (wasPositive) {
                    config.inputBarTextColor = color
                    updatePreview(settingsInputBarTextColorPreview, color)
                    applyCustomColors()
                }
            }
        }

        settingsSentBubbleColorHolder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.sentBubbleColor) { wasPositive, color ->
                if (wasPositive) {
                    config.sentBubbleColor = color
                    updatePreview(settingsSentBubbleColorPreview, color)
                }
            }
        }

        settingsSentBubbleTextColorHolder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.sentBubbleTextColor) { wasPositive, color ->
                if (wasPositive) {
                    config.sentBubbleTextColor = color
                    updatePreview(settingsSentBubbleTextColorPreview, color)
                }
            }
        }

        settingsReceivedBubbleColorHolder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.receivedBubbleColor) { wasPositive, color ->
                if (wasPositive) {
                    config.receivedBubbleColor = color
                    updatePreview(settingsReceivedBubbleColorPreview, color)
                }
            }
        }

        settingsReceivedBubbleTextColorHolder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.receivedBubbleTextColor) { wasPositive, color ->
                if (wasPositive) {
                    config.receivedBubbleTextColor = color
                    updatePreview(settingsReceivedBubbleTextColorPreview, color)
                }
            }
        }

        settingsColorRecentHolder.setOnClickListener {
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.recentColor) { wasPositive, color ->
                if (wasPositive) {
                    config.recentColor = color
                    updatePreview(settingsColorRecentPreview, color)
                }
            }
        }

        settingsAboutVersion.text = BuildConfig.VERSION_NAME
        settingsAboutHolder.setOnClickListener {
            startAboutActivity(
                R.string.app_launcher_name,
                0L,
                BuildConfig.VERSION_NAME,
                ArrayList<org.fossify.commons.models.FAQItem>(),
                false
            )
        }

        settingsResetDefaults.setOnClickListener {
            config.resetColors()
            // resetColors() clears APP_THEME along with every colour, so without re-applying
            // here the screen would come back on the bare code defaults and only settle on
            // the real default theme at the next cold start, when App.onCreate notices that
            // nothing is stored. Reset now lands where a fresh install lands.
            AppThemes.apply(config, AppThemes.byId(AppThemes.NEON))
            finish()
            startActivity(intent)
        }

        settingsTopBarPreviewContainer.setOnClickListener {
            val color = if (config.topBarColor == 0) Color.BLACK else config.topBarColor
            org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, color) { wasPositive, color ->
                if (wasPositive) {
                    config.topBarColor = if (color == Color.BLACK) 0 else color
                    updatePreview(settingsTopBarColorPreview, color)
                    applyCustomColors()
                }
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
    private fun setupContactsOnlyFilter() = binding.apply {
        settingsContactsOnlyFilterSwitch.isChecked = config.showContactsOnlyFilter
        settingsContactsOnlyFilterSwitch.setOnCheckedChangeListener { _, isChecked ->
            config.showContactsOnlyFilter = isChecked
        }
        settingsContactsOnlyFilterHolder.setOnClickListener {
            settingsContactsOnlyFilterSwitch.toggle()
        }
    }

    /** Shows or hides the built-in "بدون تبلیغات" chip. Switching it off keeps the marked
     *  senders, so turning it back on restores the same exclusions. */
    private fun setupAdsFilter() = binding.apply {
        settingsAdsFilterSwitch.isChecked = config.showAdsFilter
        settingsAdsFilterSwitch.setOnCheckedChangeListener { _, isChecked ->
            config.showAdsFilter = isChecked
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
            val items = filters.mapIndexed { index, (_, label) ->
                org.fossify.commons.models.RadioItem(index, label)
            } as ArrayList<org.fossify.commons.models.RadioItem>
            val current = filters.indexOfFirst { it.first == config.defaultFilterId }
                .coerceAtLeast(0)

            org.fossify.commons.dialogs.RadioGroupDialog(this@SettingsActivity, items, current) {
                config.defaultFilterId = filters[it as Int].first
                settingsDefaultFilter.text = defaultFilterLabel()
            }
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
            com.texto.sms.helpers.AppThemes.familyOf(config.appTheme).label

        settingsAppThemeHolder.setOnClickListener {
            val isDark = com.texto.sms.helpers.AppThemes.isDarkVariant(config.appTheme)
            // The same capsule rows the SIM chooser uses, each carrying the skin's own accent
            // as its swatch: a radio list named four themes without showing any of them, and
            // came up on commons' light dialog ground in a face nothing else here uses.
            val choices = com.texto.sms.helpers.AppThemes.families.map { family ->
                val preview = family.forDark(isDark)
                com.texto.sms.helpers.CapsuleChoice(
                    label = family.label,
                    swatch = preview.accentGradient.first,
                    swatchEnd = preview.accentGradient.second,
                    isActive = family.has(config.appTheme),
                    onPick = {
                        com.texto.sms.helpers.AppThemes.apply(config, preview)
                        settingsAppTheme.text = family.label
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
                settingsBlockedNumbersCount.text = count.toString()
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
        "${Math.round(value * 100)}٪".toPersianDigits()

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
    private fun glassOpacityLabel(opacity: Int) = "${100 - opacity}٪".toPersianDigits()

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
        val signed = if (stored > 180) stored - 360 else stored
        settingsAccentHueStrip.baseColor =
            com.texto.sms.helpers.TextoTint.rotateHue(config.accentGradientMid.takeIf { it != 0 }
                ?: config.accentGradientEnd, -signed)
        settingsAccentHueStrip.inkColor = config.mainTextColor
        settingsAccentHueStrip.shift = signed
        settingsAccentHueValue.text = accentHueLabel(signed)
        settingsAccentHueValue.setTextColor(config.accentGradientStart)

        settingsAccentHueStrip.onShiftChanged = { degrees, committed ->
            config.accentHueShift = degrees
            settingsAccentHueValue.text = accentHueLabel(degrees)
            settingsAccentHueValue.setTextColor(config.accentGradientStart)
            // Repaint on every move so the whole settings screen previews the new tonality
            // live; the heavier font pass can wait for the finger to lift.
            applyCustomColors()
            if (committed) {
                updateCustomizationUI()
                updateAppFonts(binding.root)
                // The nav capsule and the switches are painted from their own setup passes
                // rather than by applyCustomColors, so without these two they keep the old
                // tonality until the screen is next resumed : the one corner of the screen
                // that visibly disagreed with the strip being dragged.
                styleNavTabs()
                styleAllSwitches(binding.root)
                // The two sliders carry the accent on their track and read-out. Restyled
                // directly rather than by re-running their setup functions, which would
                // stack a second change listener on each one every time the strip is let go.
                settingsUiScaleSlider.applyTextoStyle()
                settingsUiScaleValue.setTextColor(config.accentGradientStart)
                settingsGlassOpacitySlider.applyTextoStyle()
                settingsGlassOpacityValue.setTextColor(config.accentGradientStart)
                // Only once the finger lifts. The launcher icon is swapped by enabling a
                // different manifest component, which is far too heavy to do on every frame
                // of a drag, and most launchers animate the change.
                com.texto.sms.helpers.TextoLauncherIcon.apply(
                    this@SettingsActivity, config.accentHueShift
                )
            }
        }
    }

    /** Centre reads as the theme's own colour rather than as a meaningless zero. */
    private fun accentHueLabel(degrees: Int) =
        if (degrees == 0) getString(R.string.settings_accent_hue_original)
        else "${degrees}°".toPersianDigits()

    private fun setupFontSize() = binding.apply {
        settingsFontSize.text = getFontSizeText()
        settingsFontSizeHolder.setOnClickListener {
            val items = arrayListOf(
                org.fossify.commons.models.RadioItem(org.fossify.commons.helpers.FONT_SIZE_SMALL, getString(org.fossify.commons.R.string.small)),
                org.fossify.commons.models.RadioItem(org.fossify.commons.helpers.FONT_SIZE_MEDIUM, getString(org.fossify.commons.R.string.medium)),
                org.fossify.commons.models.RadioItem(org.fossify.commons.helpers.FONT_SIZE_LARGE, getString(org.fossify.commons.R.string.large)),
                org.fossify.commons.models.RadioItem(org.fossify.commons.helpers.FONT_SIZE_EXTRA_LARGE, getString(org.fossify.commons.R.string.extra_large))
            )

            org.fossify.commons.dialogs.RadioGroupDialog(this@SettingsActivity, items, config.fontSize) {
                config.fontSize = it as Int
                settingsFontSize.text = getFontSizeText()
                updateAppFonts(binding.root)
            }
        }
    }

    private fun getFontSizeText() = getString(
        when (config.fontSize) {
            org.fossify.commons.helpers.FONT_SIZE_SMALL -> org.fossify.commons.R.string.small
            org.fossify.commons.helpers.FONT_SIZE_MEDIUM -> org.fossify.commons.R.string.medium
            org.fossify.commons.helpers.FONT_SIZE_LARGE -> org.fossify.commons.R.string.large
            else -> org.fossify.commons.R.string.extra_large
        }
    )

    private fun setupFontFamily() = binding.apply {
        settingsFont.text = getFontText()
        settingsFontHolder.setOnClickListener {
            // Only the system font and the bundled Persian faces are offered; the Latin
            // families that used to be here were never a sensible choice for a Persian UI.
            val items = arrayListOf(
                org.fossify.commons.models.RadioItem(0, getString(R.string.font_system_default))
            )

            // Persian typefaces, greyed out with a hint until their file is dropped in assets/fonts.
            com.texto.sms.helpers.TextoFonts.displayNames.forEach { (id, name) ->
                val installed = com.texto.sms.helpers.TextoFonts.isInstalled(this@SettingsActivity, id)
                val label = if (installed) name else "$name — ${getString(R.string.font_not_installed)}"
                items.add(org.fossify.commons.models.RadioItem(id, label))
            }

            org.fossify.commons.dialogs.RadioGroupDialog(this@SettingsActivity, items, config.fontFamilyTexto) {
                val selected = it as Int
                if (com.texto.sms.helpers.TextoFonts.isPersianFont(selected) &&
                    !com.texto.sms.helpers.TextoFonts.isInstalled(this@SettingsActivity, selected)
                ) {
                    toast(R.string.font_not_installed)
                }
                config.fontFamilyTexto = selected
                settingsFont.text = getFontText()
                updateAppFonts(binding.root)
                applyCustomColors()
            }
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

        /** The design draws a settings row glyph at 18px, seated in its own tile. */
        const val SETTINGS_GLYPH_DP = 18

        /** That tile: 36px on a 0.9rem radius, filled with `--primary-soft`. */
        const val SETTINGS_TILE_DP = 36
        const val SETTINGS_TILE_RADIUS_DP = 14
    }
}
