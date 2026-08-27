package com.texto.sms.activities

import android.app.Activity
import android.graphics.Color
import android.content.Intent
import android.os.Bundle
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
        setupAppTheme()
        setupBlockedNumbers()
        setupConversationScreens()
        setupContactsOnlyFilter()
        setupAdsFilter()
        setupDefaultFilter()
        setupFontSize()
        setupFontFamily()
        setupBgModes()
        setupAuroraAnimate()
        updateAppFonts(binding.root)
    }

    override fun onResume() {
        super.onResume()
        applyOutlines()
        updateCustomizationUI()
        setupNovaNavBar()
        // Ensure UI is fully up to date for modern design
        updateAppFonts(binding.root)
        applyCustomColors()
    }

    private fun setupNovaNavBar() = binding.apply {
        if (config.useNewUi) {
            novaNavContainer.beVisible()
            
            // Sync edge-to-edge padding to match Home screen
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(novaNavContainer) { v, insets ->
                val navigationHeight = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
                v.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                    bottomMargin = 16.getScaledPx() + navigationHeight
                }
                insets
            }
            
            // Apply compact width and transparency
            novaNavContainer.updateLayoutParams<androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams> {
                width = 240.getScaledPx()
                // Matches the home screen's bar, which grew to fit the tab captions.
                height = 62.getScaledPx()
                gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            }
            novaNavContainer.alpha = 0.92f
            
            // Set icon transparency
            navHomeIcon.alpha = 0.6f
            navSettingsIcon.alpha = 0.9f // Lighter for active
            novaSearchIcon.alpha = 0.6f
            
            // Highlight Settings (Current Screen) with subtle transparency
            navSettingsBtn.setBackgroundColor(Color.WHITE.withAlpha(0.1f))
            
            navHomeBtn.setOnClickListener {
                finish() // Go back to main
            }
            
            navSearchBtn.setOnClickListener {
                finish() // Go back to main and expand search
            }
        } else {
            novaNavContainer.beGone()
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
            binding.novaNavContainer.foreground = layerDrawable
            
            // Sync icon and divider colors with search bar text color
            binding.navHomeIcon.imageTintList = android.content.res.ColorStateList.valueOf(inputBarTextColor)
            binding.navSettingsIcon.imageTintList = android.content.res.ColorStateList.valueOf(inputBarTextColor)
            binding.novaSearchIcon.imageTintList = android.content.res.ColorStateList.valueOf(inputBarTextColor)
        } else {
            binding.novaNavContainer.foreground = null
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
        updatePreview(settingsMainBackgroundColorPreview, config.mainBackgroundColor)
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

    private fun setupBgModes() = binding.apply {
        // Top & bottom bars only support a plain colour now (no mode picker for them at all)
        // -- migrate away from a stale Image selection from before this was simplified, so the
        // preview swatch and renderer agree on what's actually shown.
        if (config.topBarBgMode != BG_MODE_COLOR) {
            config.topBarBgMode = BG_MODE_COLOR
        }

        // The main background picks between a flat colour and an auto-shaded gradient derived
        // from one colour. The two mode values (BG_MODE_COLOR, BG_MODE_GRADIENT) are not
        // contiguous -- BG_MODE_IMAGE(1) used to sit between them -- so position and stored
        // mode are mapped through mainModeValues rather than assumed equal.
        val mainModes = arrayListOf(getString(R.string.bg_mode_color), getString(R.string.bg_mode_gradient))
        val mainModeValues = intArrayOf(BG_MODE_COLOR, BG_MODE_GRADIENT)

        val adapter = object : ArrayAdapter<String>(this@SettingsActivity, android.R.layout.simple_spinner_item, mainModes) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(config.mainTextColor)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(config.mainTextColor)
                return view
            }
        }

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)

        settingsMainBgModeSpinner.adapter = adapter
        // The dropdown popup is its own window with a system-default (usually light)
        // background, unrelated to the settings row it opens from. On Aurora the label text
        // is white, so without an explicit popup background it was white-on-white.
        settingsMainBgModeSpinner.setPopupBackgroundDrawable(android.graphics.drawable.ColorDrawable(config.mainBackgroundColor))
        settingsMainBgModeSpinner.setSelection(mainModeValues.indexOf(config.mainBgMode).coerceAtLeast(0))
        settingsMainBgModeSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val newMode = mainModeValues[position]
                if (newMode != config.mainBgMode) {
                    config.mainBgMode = newMode
                    if (newMode == BG_MODE_GRADIENT) {
                        // Switching to shaded should look different right away, so seed the
                        // shade from the current flat colour instead of leaving a flat gradient.
                        config.mainBgGradientStart = config.mainBackgroundColor
                        config.mainBgGradientEnd = adjustColor(config.mainBackgroundColor, 0.75f)
                    }
                    updateCustomizationUI()
                    applyCustomColors()
                    // Force refresh of the spinner text color immediately
                    (settingsMainBgModeSpinner.selectedView as? TextView)?.setTextColor(config.mainTextColor)
                }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
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
        updatePreview(settingsMainBackgroundColorPreview, config.mainBackgroundColor)
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

        settingsResetDefaults.setOnClickListener {
            config.resetColors()
            // resetColors() clears APP_THEME along with every colour, so without re-applying
            // here the screen would come back on the bare code defaults and only settle on
            // the real default theme at the next cold start, when App.onCreate notices that
            // nothing is stored. Reset now lands where a fresh install lands.
            AppThemes.apply(config, AppThemes.byId(AppThemes.AURORA))
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

        settingsMainBgPreviewContainer.setOnClickListener {
            if (config.mainBgMode == BG_MODE_GRADIENT) {
                // One colour is all the user picks; the second stop is an automatic shade.
                org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.mainBgGradientStart) { wasPositive, color ->
                    if (wasPositive) {
                        config.mainBackgroundColor = color
                        config.mainBgGradientStart = color
                        config.mainBgGradientEnd = adjustColor(color, 0.75f)
                        updatePreview(settingsMainBackgroundColorPreview, color)
                        applyCustomColors()
                    }
                }
            } else {
                org.fossify.commons.dialogs.ColorPickerDialog(this@SettingsActivity, config.mainBackgroundColor) { wasPositive, color ->
                    if (wasPositive) {
                        config.mainBackgroundColor = color
                        updatePreview(settingsMainBackgroundColorPreview, color)
                        applyCustomColors()
                    }
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
    private fun setupAuroraAnimate() = binding.apply {
        settingsAuroraAnimateLabel.setTextColor(config.mainTextColor)
        settingsAuroraAnimateSwitch.isChecked = config.auroraAnimate
        settingsAuroraAnimateSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked == config.auroraAnimate) return@setOnCheckedChangeListener
            config.auroraAnimate = isChecked
            applyCustomColors()
        }
        settingsAuroraAnimateHolder.setOnClickListener {
            settingsAuroraAnimateSwitch.toggle()
        }
    }

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
    private fun setupAppTheme() = binding.apply {
        settingsAppTheme.text =
            com.texto.sms.helpers.AppThemes.byId(config.appTheme).label

        settingsAppThemeHolder.setOnClickListener {
            val themes = com.texto.sms.helpers.AppThemes.all
            val items = themes.mapIndexed { index, theme ->
                org.fossify.commons.models.RadioItem(index, theme.label)
            } as ArrayList<org.fossify.commons.models.RadioItem>

            val current = themes.indexOfFirst { it.id == config.appTheme }.coerceAtLeast(0)
            org.fossify.commons.dialogs.RadioGroupDialog(this@SettingsActivity, items, current) {
                val theme = themes[it as Int]
                com.texto.sms.helpers.AppThemes.apply(config, theme)
                settingsAppTheme.text = theme.label
                updateCustomizationUI()
                updateAppFonts(binding.root)
                applyCustomColors()
                toast(R.string.theme_applied)
            }
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
    private fun com.google.android.material.slider.Slider.setSteppedValue(raw: Float) {
        val snapped = if (stepSize > 0f) {
            valueFrom + Math.round((raw - valueFrom) / stepSize) * stepSize
        } else {
            raw
        }
        value = snapped.coerceIn(valueFrom, valueTo)
    }

    private fun setupUIScale() = binding.apply {
        settingsUiScaleSlider.setSteppedValue(config.uiScale)
        settingsUiScaleValue.text = uiScaleLabel(config.uiScale)
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
        settingsGlassOpacitySlider.setSteppedValue(config.glassOpacity.toFloat())
        settingsGlassOpacityValue.text = glassOpacityLabel(config.glassOpacity)
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
            com.texto.sms.helpers.NovaFonts.displayNames.forEach { (id, name) ->
                val installed = com.texto.sms.helpers.NovaFonts.isInstalled(this@SettingsActivity, id)
                val label = if (installed) name else "$name — ${getString(R.string.font_not_installed)}"
                items.add(org.fossify.commons.models.RadioItem(id, label))
            }

            org.fossify.commons.dialogs.RadioGroupDialog(this@SettingsActivity, items, config.fontFamilyNova) {
                val selected = it as Int
                if (com.texto.sms.helpers.NovaFonts.isPersianFont(selected) &&
                    !com.texto.sms.helpers.NovaFonts.isInstalled(this@SettingsActivity, selected)
                ) {
                    toast(R.string.font_not_installed)
                }
                config.fontFamilyNova = selected
                settingsFont.text = getFontText()
                updateAppFonts(binding.root)
                applyCustomColors()
            }
        }
    }

    /** Anything that is not a bundled Persian face now reads as the system font, which also
     *  covers a value left behind by one of the Latin families that has been dropped. */
    private fun getFontText(): String =
        com.texto.sms.helpers.NovaFonts.displayNames[config.fontFamilyNova]
            ?: getString(R.string.font_system_default)

    /** Every grouped card on the screen, in display order. */
    private fun settingsCards() = binding.run {
        listOf(
            settingsCardConversations, settingsCardFilters, settingsCardAppearance,
            settingsCardSizeFont, settingsCardTextColors, settingsCardConversationColors,
            settingsCardBubbles
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
            NovaGlass.applyPanel(
                view = card,
                tint = config.recentColor,
                cornerRadius = cardRadius,
                opacity = if (config.glassTheme) 0.55f else 1f,
                strokeWidthPx = 1.getScaledPx()
            )
            card.clipToOutline = true
            card.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cardRadius)
                }
            }
            tintRowsIn(card, mainTextColor)
        }

        // Section labels sit outside the cards, so they are tinted separately. The reset row
        // is the one destructive action on the screen and keeps its own warning colour.
        val holder = binding.settingsHolder
        for (i in 0 until holder.childCount) {
            (holder.getChildAt(i) as? TextView)?.setTextColor(mainTextColor)
        }
        binding.settingsResetDefaults.setTextColor(DESTRUCTIVE_COLOR)
    }

    /**
     * Recursively tints a card's text and icons. Colour swatches are plain [View]s, so they
     * fall through every branch here and keep the fill that represents their stored value.
     */
    private fun tintRowsIn(view: View, textColor: Int) {
        when (view) {
            is TextView -> view.setTextColor(textColor)
            is android.widget.ImageView -> view.applyColorFilter(textColor)
            is ViewGroup -> {
                for (i in 0 until view.childCount) {
                    tintRowsIn(view.getChildAt(i), textColor)
                }
            }
        }
    }

    private companion object {
        /** The design's `--destructive`, oklch(0.65 0.21 22). */
        val DESTRUCTIVE_COLOR = Color.parseColor("#F54651")
    }
}
