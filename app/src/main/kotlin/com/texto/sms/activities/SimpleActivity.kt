package com.texto.sms.activities

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.graphics.drawable.Drawable
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import android.graphics.Bitmap
import android.graphics.RectF
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest
import com.bumptech.glide.load.engine.DiskCacheStrategy
import androidx.appcompat.widget.ListPopupWindow
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout
import androidx.core.view.get
import androidx.core.view.size
import androidx.core.graphics.toColorInt
import androidx.core.view.updateLayoutParams
import com.google.android.material.appbar.AppBarLayout
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.helpers.isRPlus
import org.fossify.commons.extensions.getTextSize
import org.fossify.commons.extensions.applyColorFilter
import com.texto.sms.R
import com.texto.sms.extensions.config
import com.texto.sms.helpers.*
import java.io.File
import kotlin.math.max

open class SimpleActivity : BaseSimpleActivity() {

    val uiScale get() = config.uiScale



    private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        // Removed to fix infinite layout loop and jitter
    }

    private val cabHideObserver = ViewTreeObserver.OnGlobalLayoutListener {
        if (currentActionMode != null) {
            hideSystemSelectionBar()
        }
    }

    fun getScaledTextSize(multiplier: Float = 1.0f): Float {
        return getTextSize() * multiplier
    }

    fun getScaledDimen(dimenId: Int): Int {
        return (resources.getDimension(dimenId) * uiScale).toInt()
    }

    fun Int.getScaledPx(): Int {
        return (this * resources.displayMetrics.density * uiScale).toInt()
    }

    fun setupScaledToolbar(toolbar: Toolbar) {
        val baseHeight = 70.getScaledPx()
        val minHeightRequired = (48 * resources.displayMetrics.density).toInt()
        val finalHeight = max(baseHeight, minHeightRequired)
        
        toolbar.updateLayoutParams {
            height = finalHeight
        }
    }

    /** Height of the status-bar strip the top bar keeps clear of, 0 before the first layout. */
    protected fun statusBarInsetOf(view: View): Int =
        ViewCompat.getRootWindowInsets(view)
            ?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0

    fun getCustomTypeface(): android.graphics.Typeface? {
        val id = config.fontFamilyTexto
        if (TextoFonts.isPersianFont(id)) {
            TextoFonts.getTypeface(this, id)?.let { return it }
            // Font file not supplied yet, fall back to the system default.
            return null
        }
        // The Latin families are no longer offered, so every remaining value -- including one
        // left over from a font that used to be selectable -- means the system default.
        return null
    }

    /** Resolves the selected family at [style], using a real bold cut when the family ships one. */
    fun getCustomTypeface(style: Int): android.graphics.Typeface? {
        val id = config.fontFamilyTexto
        if (!TextoFonts.isPersianFont(id)) return null
        return TextoFonts.create(this, id, style)
    }

    /**
     * Always-resolved counterpart to [getCustomTypeface]. Assign this directly rather than
     * calling `setTypeface(family, style)`: the two-arg form would synthesise bold on top of
     * an already-bold cut. Falling back to a null family keeps the *system* face at [style],
     * where a bare null would silently drop bold/italic.
     */
    fun typefaceFor(style: Int): android.graphics.Typeface =
        getCustomTypeface(style)
            ?: android.graphics.Typeface.create(null as android.graphics.Typeface?, style)

    fun updateAppFonts(view: View?) {
        if (view == null) return
        if (view is TextView) {
            val style = view.typeface?.style ?: android.graphics.Typeface.NORMAL
            view.typeface = typefaceFor(style)
            
            // Apply custom text color if not in toolbar AND not a message bubble/list item
            val id = view.id
            val excludedIds = listOf(
                R.id.thread_toolbar_title,
                R.id.thread_header_status,
                R.id.texto_title,
                R.id.settings_toolbar_title,
                R.id.new_conversation_toolbar_title,
                R.id.thread_message_body,
                R.id.texto_search_input,
                R.id.new_conversation_address,
                R.id.thread_type_message,
                R.id.thread_sim_number,
                R.id.thread_message_carrier_warning,
                R.id.nav_home_icon,
                R.id.nav_settings_icon,
                R.id.nav_search_icon,
                R.id.nav_add_icon,
                R.id.texto_search_icon,
                // The two slider read-outs carry the accent, and this pass would otherwise
                // put them straight back to the plain text colour on every resume.
                R.id.settings_ui_scale_value,
                R.id.settings_glass_opacity_value,
                R.id.thread_search_count
            )
                             
            if (!excludedIds.contains(id)) {
                val color = config.mainTextColor
                if (color != 0 && color != Color.TRANSPARENT) {
                    view.setTextColor(color)
                }
            }
        }
        (view as? ViewGroup)?.let {
            for (i in 0 until it.childCount) {
                updateAppFonts(it.getChildAt(i))
            }
        }
    }

    /**
     * The drifting halo background currently installed on this activity's decor view, kept so
     * its animator can be cancelled when it is replaced or the activity goes away.
     */
    private var auroraBackground: AuroraBackgroundDrawable? = null

    private fun releaseAuroraBackground() {
        auroraBackground?.release()
        auroraBackground = null
    }

    fun applyCustomColors() {
        val density = resources.displayMetrics.density

        // 1. Force main background to apply to the window decor view
        if (config.mainBgMode == BG_MODE_IMAGE && config.mainBackgroundImage.isNotEmpty()) {
            releaseAuroraBackground()
            loadBackgroundImage(config.mainBackgroundImage, window.decorView, config.mainBgCropRect)
        } else if (config.mainBgMode == BG_MODE_GRADIENT) {
            clearGlideTarget(window.decorView)
            // "رنگ سایه‌دار" is the skin's aurora field: the chosen colour as the ground with
            // three slowly drifting halos in the accent hues over it.
            releaseAuroraBackground()
            // The halos are the theme's own, not the accent trio. Reusing the accent meant a
            // skin with a bright cyan-to-violet accent washed its near-black ground in bright
            // teal and purple; the design's radials are much deeper than the accent above them.
            val aurora = AuroraBackgroundDrawable(
                groundColor = config.mainBgGradientStart,
                haloColors = config.auroraHaloColors,
                haloOpacity = config.auroraHaloOpacity,
                animate = config.auroraAnimate
            )
            auroraBackground = aurora
            window.decorView.background = aurora
        } else if (config.mainBgMode == BG_MODE_LINEAR) {
            // A plain vertical fade between the two stops -- the Nocturne theme's ground,
            // where the design calls for a clean navy-to-purple gradient rather than halos.
            releaseAuroraBackground()
            clearGlideTarget(window.decorView)
            window.decorView.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(config.mainBgGradientStart, config.mainBgGradientEnd)
            )
        } else {
            releaseAuroraBackground()
            clearGlideTarget(window.decorView)
            window.decorView.setBackgroundColor(config.mainBackgroundColor)
        }

        // The frosted bars start below the status bar, so what sits behind the clock, signal
        // and battery is the window background -- not the bar. Nothing ever set the status
        // bar icon appearance, so on a light background the white icons were unreadable.
        // Pick the icon polarity from whatever is actually painted up there.
        val behindStatusBar = when {
            config.mainBgMode == BG_MODE_IMAGE && config.mainBackgroundImage.isNotEmpty() -> null
            config.mainBgMode == BG_MODE_GRADIENT || config.mainBgMode == BG_MODE_LINEAR ->
                config.mainBgGradientStart
            else -> config.mainBackgroundColor
        }
        runCatching {
            WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars =
                behindStatusBar != null && !TextoGlass.isDark(behindStatusBar)
        }
        
        // 2. Apply top bar color (HARD RECURSIVE SHAPE GUARD)
        val appBar = findViewById<AppBarLayout>(R.id.settings_appbar) ?:
                     findViewById<AppBarLayout>(R.id.thread_appbar) ?:
                     findViewById<AppBarLayout>(R.id.main_appbar) ?:
                     findViewById<AppBarLayout>(R.id.conversation_details_appbar) ?:
                     findViewById<AppBarLayout>(R.id.new_conversation_appbar)
                     
        val toolbar = findViewById<Toolbar>(R.id.settings_toolbar) ?:
                      findViewById<Toolbar>(R.id.thread_toolbar) ?:
                      findViewById<Toolbar>(R.id.main_toolbar) ?:
                      findViewById<Toolbar>(R.id.conversation_details_toolbar) ?:
                      findViewById<Toolbar>(R.id.new_conversation_toolbar)
        
        if (appBar != null) {
            val barColor = if (config.topBarColor != 0) config.topBarColor else Color.BLACK
            // Same geometry as the floating home/search/settings pill (28dp radius, inset
            // from the screen edges) so all three read as one family of glass surfaces.
            val barRadius = 28 * density
            val barSideInset = (12 * density).toInt()
            val useNewUi = config.useNewUi
            
            val topBarImage = config.topBarImage
            // Rounded on all four corners now, not just the bottom. The bar no longer runs up
            // behind the status bar either: its background is inset by the status-bar height
            // so the clock, battery and signal icons keep a clear strip of their own above it.
            val allCorners = FloatArray(8) { barRadius }
            // Settings -> "شفافیت نوارهای شیشه‌ای" drives every frosted bar, so the top bar,
            // the filter chips row and the floating nav pill stay visually in step.
            val glassOpacity = config.glassOpacity / 100f
            val barShape = if (useNewUi) {
                // The same recipe the floating nav pill is painted with, so the two capsules
                // are one material rather than a frosted panel above and a gradient below.
                TextoGlass.bar(
                    tint = barColor,
                    cornerRadii = allCorners,
                    opacity = glassOpacity,
                    strokeWidthPx = density.toInt().coerceAtLeast(1)
                )
            } else {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadii = allCorners
                    setColor(barColor)
                }
            }

            if (config.topBarBgMode == BG_MODE_IMAGE && topBarImage.isNotEmpty()) {
                loadBackgroundImage(topBarImage, appBar, config.topBarCropRect)
            } else {
                clearGlideTarget(appBar)
                // Inset rather than a top margin: the toolbar's own top padding is applied by
                // commons' edge-to-edge setup, and moving the view would fight it. Insetting
                // only what gets painted leaves that padding doing its job.
                val appliedInset = statusBarInsetOf(appBar)
                appBar.background = android.graphics.drawable.InsetDrawable(
                    barShape, barSideInset, appliedInset, barSideInset, 0
                )
                if (appliedInset == 0) {
                    // On a cold start the insets are not known yet, which would paint the bar
                    // from the very top. Rewrap the same shape once the first layout knows.
                    appBar.doOnLayout { view ->
                        val settled = statusBarInsetOf(view)
                        if (settled > 0) {
                            view.background = android.graphics.drawable.InsetDrawable(
                                barShape, barSideInset, settled, barSideInset, 0
                            )
                        }
                    }
                }
            }
            
            appBar.isLiftOnScroll = false
            appBar.stateListAnimator = null
            
            // Shadow and clip follow the painted shape, so they start below the status bar
            // too. Recomputed on every layout pass, which is where the insets are reliable.
            appBar.outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(
                        barSideInset, statusBarInsetOf(view),
                        view.width - barSideInset, view.height, barRadius
                    )
                }
            }
            appBar.clipToOutline = true

            if (useNewUi) {
                appBar.background = appBar.background ?: ColorDrawable(barColor) // Ensure background is set if Glide is async
                appBar.elevation = 14 * density // Extra Stronger shadow
                appBar.setLayerType(View.LAYER_TYPE_HARDWARE, null)
                // Ensure shadow is visible by disabling clipping on parent
                (appBar.parent as? ViewGroup)?.let {
                    it.clipChildren = false
                    it.clipToPadding = false
                }
            } else {
                appBar.elevation = 0f
            }
            
            // Force tinting for the nav bar icons if they exist
            findViewById<View>(R.id.texto_nav_container)?.let {
                val inputBarTextColor = config.inputBarTextColor
                findViewById<ImageView>(R.id.nav_home_icon)?.imageTintList = ColorStateList.valueOf(inputBarTextColor)
                // The two capsules do not carry the same set of tabs, so every id either
                // screen might have is looked up and whichever is inflated answers.
                findViewById<ImageView>(R.id.nav_settings_icon)?.imageTintList = ColorStateList.valueOf(inputBarTextColor)
                findViewById<ImageView>(R.id.texto_search_icon)?.imageTintList = ColorStateList.valueOf(inputBarTextColor)
                findViewById<ImageView>(R.id.nav_search_icon)?.imageTintList = ColorStateList.valueOf(inputBarTextColor)
                findViewById<ImageView>(R.id.nav_add_icon)?.imageTintList = ColorStateList.valueOf(inputBarTextColor)
            }

            // The chips carry their own pills, so the row holding them stays bare. It used
            // to be a second glass bar under the header, which stacked one rounded surface
            // on another and made the chips read as the contents of a bar rather than as
            // free controls sitting on the screen.
            findViewById<View>(R.id.filter_bar)?.background = null
        }
        
        toolbar?.setBackgroundColor(Color.TRANSPARENT)
        
        // 3. Apply top bar text color
        if (toolbar != null) {
            val topBarColor = config.topBarTextColor
            toolbar.setTitleTextColor(topBarColor)
            
            // Use specific MaterialToolbar method for navigation icon
            if (toolbar is com.google.android.material.appbar.MaterialToolbar) {
                toolbar.setNavigationIconTint(topBarColor)
            }
            toolbar.navigationIcon?.setColorFilter(topBarColor, android.graphics.PorterDuff.Mode.SRC_IN)
            
            toolbar.overflowIcon?.setColorFilter(topBarColor, android.graphics.PorterDuff.Mode.SRC_IN)
            
            // Also tint menu items if they exist
            for (i in 0 until toolbar.menu.size) {
                val item = toolbar.menu[i]
                item.icon?.setColorFilter(topBarColor, android.graphics.PorterDuff.Mode.SRC_IN)
            }
        }
        
        // texto_title is an ImageView on the main screen now (the wordmark logo) and a
        // TextView everywhere else it appears, so each candidate is fetched as a plain View
        // and safe-cast rather than typed at the findViewById call -- a typed lookup throws
        // the moment it resolves to the wrong kind of view instead of just skipping it.
        val titleText = (findViewById<View>(R.id.thread_toolbar_title) as? TextView)
            ?: (findViewById<View>(R.id.texto_title) as? TextView)
            ?: (findViewById<View>(R.id.settings_toolbar_title) as? TextView)
            ?: (findViewById<View>(R.id.new_conversation_toolbar_title) as? TextView)
        titleText?.setTextColor(config.topBarTextColor)
        
        // 4. Apply input bar colors (Maintaining Rounded Shape)
        val inputBar = findViewById<View>(R.id.texto_nav_container) ?: 
                       findViewById<View>(R.id.texto_message_input_bar) ?:
                       findViewById<View>(R.id.new_conversation_search_container)
        if (inputBar != null) {
            val inputBgColor = config.inputBarBackgroundColor
            // The design draws the nav pill and the composer capsule on the same 1.6rem, and
            // the new-conversation search field a little tighter.
            val isFloatingCapsule =
                inputBar.id == R.id.texto_nav_container || inputBar.id == R.id.texto_message_input_bar
            val inputRadius = if (isFloatingCapsule) {
                26 * density
            } else {
                22 * density
            }
            val inputBarImage = config.inputBarImage
            
            if (config.inputBarBgMode == BG_MODE_IMAGE && inputBarImage.isNotEmpty()) {
                loadBackgroundImage(inputBarImage, inputBar, config.inputBarCropRect) {
                    inputBar.outlineProvider = object : android.view.ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: android.graphics.Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, inputRadius)
                        }
                    }
                    inputBar.clipToOutline = true
                }
            } else {
                clearGlideTarget(inputBar)
                inputBar.clipToOutline = false
                if (config.glassTheme) {
                    if (isFloatingCapsule) {
                        // The nav pill and the composer are bars, not panels: same gradient
                        // and rim as the header, following the user's glass setting. The
                        // composer joined them when the send disc moved inside it -- it is
                        // the outer surface now, not a field sitting on one, and the design
                        // draws it with the same glass/divider/capsule-shadow recipe.
                        inputBar.background = TextoGlass.bar(
                            tint = inputBgColor,
                            cornerRadius = inputRadius,
                            opacity = config.glassOpacity / 100f,
                            strokeWidthPx = 1.getScaledPx()
                        )
                    } else {
                        // Typing fields are deliberately not glass. A frosted panel put a
                        // sheen and a bright rim on a surface that needs neither, and read
                        // as a second, brighter material sitting on the field. A flat wash
                        // of the same colour at the design weight is what the theme uses for
                        // a filled field, and it keeps typed text crisp.
                        inputBar.background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = inputRadius
                            setColor(inputBgColor.withAlpha(0.60f))
                        }
                    }
                } else {
                    inputBar.background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = inputRadius
                        setColor(inputBgColor)
                    }
                }
            }
        }
        
        // 5. Targeted EditText coloring for live typed text
        val inputEditTexts = listOfNotNull(
            findViewById<EditText>(R.id.texto_search_input),
            findViewById<EditText>(R.id.new_conversation_address),
            findViewById<EditText>(R.id.thread_type_message),
        )
        
        val textColorCSL = ColorStateList.valueOf(config.inputBarTextColor)
        val searchIcons = listOfNotNull(
            findViewById<ImageView>(R.id.texto_search_icon),
            findViewById<ImageView>(R.id.new_conversation_search_icon)
        )
        
        for (icon in searchIcons) {
            icon.imageTintList = textColorCSL
            icon.alpha = 0.7f
        }

        for (et in inputEditTexts) {
            et.setTextColor(textColorCSL)
            val hintColor = if (config.inputBarTextColor == Color.WHITE) {
                "#888888".toColorInt()
            } else {
                config.inputBarTextColor.withAlpha(0.6f)
            }
            et.setHintTextColor(hintColor)
            
            // Add a TextWatcher to force color on every keystroke
            if (et.tag != "color_watcher_attached") {
                et.addTextChangedListener(object : TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                        et.setTextColor(config.inputBarTextColor)
                    }
                    override fun afterTextChanged(s: Editable?) {}
                })
                et.tag = "color_watcher_attached"
            }
        }
        
        // Force settings labels to follow main text color
        val mainTextCol = config.mainTextColor
        val settingsLabels = listOf(
            R.id.settings_top_bar_label,
            R.id.settings_main_bg_label,
            R.id.settings_ui_scale_label,
            R.id.settings_font_size_label,
            R.id.settings_font_label,
            R.id.settings_reset_defaults,
            R.id.settings_font_size,
            R.id.settings_font,
            R.id.settings_top_bar_text_color_label,
            R.id.settings_background_color_label,
            R.id.settings_input_bar_text_color_label,
            R.id.settings_sent_bubble_color_label,
            R.id.settings_sent_bubble_text_color_label,
            R.id.settings_received_bubble_color_label,
            R.id.settings_received_bubble_text_color_label
        )
        
        settingsLabels.forEach { labelId ->
            findViewById<TextView>(labelId)?.setTextColor(mainTextCol)
        }

        findViewById<ImageView>(R.id.thread_add_attachment)?.let {
            it.imageTintList = ColorStateList.valueOf(config.inputBarTextColor)
            it.alpha = 1.0f
        }

        val actionButtons = listOfNotNull(
            findViewById<View>(R.id.thread_send_message),
            findViewById<View>(R.id.new_conversation_confirm)
        )

        for (view in actionButtons) {
            val color = config.inputBarTextColor
            if (view is TextView) {
                view.setTextColor(color)
                view.compoundDrawables.forEach { it?.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN) }
                view.compoundDrawablesRelative.forEach { it?.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN) }
            } else if (view is ImageView) {
                view.imageTintList = ColorStateList.valueOf(color)
                view.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
            }
        }
    }

    protected fun adjustColor(color: Int, factor: Float): Int {
        val a = Color.alpha(color)
        val r = Math.round(Color.red(color) * factor).coerceIn(0, 255)
        val g = Math.round(Color.green(color) * factor).coerceIn(0, 255)
        val b = Math.round(Color.blue(color) * factor).coerceIn(0, 255)
        return Color.argb(a, r, g, b)
    }

    private fun forceTransparentContainers(view: View) {
        val id = view.id
        if (id == R.id.main_coordinator || 
            id == R.id.settings_coordinator || 
            id == R.id.thread_coordinator ||
            id == R.id.main_nested_scrollview || 
            id == R.id.main_coordinator_wrapper ||
            id == R.id.message_holder ||
            id == R.id.attachment_picker_holder) {
            view.setBackgroundColor(Color.TRANSPARENT)
        }
        
        // Ensure lists are always visible and not accidentally hidden
        if (view is androidx.recyclerview.widget.RecyclerView) {
            view.visibility = View.VISIBLE
        }
        
        (view as? ViewGroup)?.let {
            for (i in 0 until it.childCount) {
                forceTransparentContainers(it.getChildAt(i))
            }
        }
    }

    fun Int.withAlpha(alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (this and 0x00FFFFFF) or (a shl 24)
    }

    /**
     * The UI is Persian, so it lays out right-to-left. That cannot come from the device
     * locale here: build.gradle pins `resConfigs("en")`, which leaves the app resolving to an
     * LTR configuration no matter what the phone is set to -- which is why the older screens
     * had to fake RTL with absolute `right` gravity and `alignParentStart` controls.
     *
     * Overriding the configuration's locale to Persian makes `start`/`end` resolve the way
     * the layouts actually mean them, and every framework surface we don't own -- dialogs,
     * menus, the commons library's views -- flips with it. String lookup is unaffected: with
     * no values-fa folder shipped, Persian falls back to `values/`, which is where the app's
     * Persian strings already live.
     */
    override fun attachBaseContext(newBase: Context) {
        val locale = java.util.Locale("fa", "IR")
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(newBase.resources.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    private var selectionCancelCallback: (() -> Unit)? = null
    private val selectionBackCallback = object : androidx.activity.OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            selectionCancelCallback?.invoke()
        }
    }

    private var isCheckingPackage = false

    override fun getPackageName(): String {
        return if (isCheckingPackage) "org.fossify.messages" else super.getPackageName()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        isCheckingPackage = true
        super.onCreate(savedInstanceState)
        isCheckingPackage = false

        onBackPressedDispatcher.addCallback(this, selectionBackCallback)
        requestHighRefreshRate()
    }

    override fun onResume() {
        super.onResume()
        updateAppFonts(findViewById(android.R.id.content))
        applyCustomColors()
        
        // Suppress popups safely
        config.appSideloadingStatus = 0
        config.hadThankYouInstalled = true
        config.appRunCount = 1
    }

    override fun onSupportActionModeStarted(mode: androidx.appcompat.view.ActionMode) {
        super.onSupportActionModeStarted(mode)
        currentActionMode = mode
        window.decorView.viewTreeObserver.addOnGlobalLayoutListener(cabHideObserver)
        // Aggressively hide the system CAB to avoid duplicate UI
        hideSystemSelectionBar()
        
        // Final Force
        mode.customView = View(this).apply { layoutParams = ViewGroup.LayoutParams(0, 0) }
        mode.title = ""
        mode.subtitle = ""
    }

    private var currentActionMode: androidx.appcompat.view.ActionMode? = null

    private fun hideSystemSelectionBar() {
        try {
            val hideBar = { view: View ->
                view.visibility = View.GONE
                view.alpha = 0f
                view.isClickable = false
                view.isFocusable = false
                if (view.layoutParams != null) {
                    view.layoutParams.height = 0
                }
            }

            // Layer 1: Traditional ID lookup
            window.decorView.findViewById<View>(androidx.appcompat.R.id.action_mode_bar)?.let { hideBar(it) }
            window.decorView.findViewById<View>(android.R.id.custom)?.parent?.let { 
                if (it is View && it.javaClass.simpleName.contains("ActionMode")) hideBar(it) 
            }
            
            // Layer 2: Recursive class-based lookup
            findAndHideActionModeView(window.decorView)
        } catch (_: Exception) {}
    }

    private fun findAndHideActionModeView(view: View) {
        val name = view.javaClass.simpleName
        if (name.contains("ActionMode") || name.contains("ActionBarContextView")) {
            view.visibility = View.GONE
            view.alpha = 0f
            if (view.layoutParams != null) {
                view.layoutParams.height = 0
            }
        }

        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                findAndHideActionModeView(view.getChildAt(i))
            }
        }
    }

    override fun onSupportActionModeFinished(mode: androidx.appcompat.view.ActionMode) {
        super.onSupportActionModeFinished(mode)
        currentActionMode = null
        selectionCancelCallback = null
        window.decorView.viewTreeObserver.removeOnGlobalLayoutListener(cabHideObserver)
    }

    fun isCustomSelectionBarVisible(): Boolean {
        val barContainer = findViewById<View>(R.id.selection_bar) ?: 
                           findViewById<View>(R.id.selection_bar_container)
        return barContainer?.visibility == View.VISIBLE
    }

    fun toggleCustomSelectionBar(show: Boolean, count: Int = 0, actions: List<Int> = emptyList(), onAction: (Int) -> Unit = {}) {
        android.util.Log.d("SelectionBar", "toggleCustomSelectionBar: show=$show, count=$count")
        
        selectionBackCallback.isEnabled = show
        if (show) {
            selectionCancelCallback = { 
                onAction(R.id.selection_cancel)
                currentActionMode?.finish()
            }
        } else {
            selectionCancelCallback = null
            currentActionMode?.finish()
        }

        val barContainer = findViewById<View>(R.id.selection_bar) ?: 
                           findViewById<View>(R.id.selection_bar_container) ?: run {
                               android.util.Log.e("SelectionBar", "Bar container NOT FOUND")
                               return
                           }

        val newVisibility = if (show) View.VISIBLE else View.GONE
        if (barContainer.visibility != newVisibility) {
            barContainer.visibility = newVisibility
            if (show) {
                barContainer.alpha = 1f
            }
        }

        if (show) {
            val countText = findViewById<TextView>(R.id.selection_count)
            countText?.text = getString(R.string.x_selected, count)
            countText?.setTextColor(config.topBarTextColor)
            
            val tint = ColorStateList.valueOf(config.topBarTextColor)
            
            findViewById<ImageView>(R.id.selection_cancel)?.apply {
                imageTintList = tint
                setOnClickListener { onAction(R.id.selection_cancel) }
            }
            
            // Sync Bar Gradient - frosted pill matching the rest of the glass surfaces
            val baseColor = if (config.topBarColor != 0) config.topBarColor else Color.BLACK
            findViewById<View>(R.id.selection_bar_pill)?.let {
                if (config.glassTheme) {
                    TextoGlass.applyPanel(
                        view = it,
                        tint = baseColor,
                        cornerRadius = 1000f,
                        opacity = 0.7f,
                        strokeWidthPx = 1.getScaledPx()
                    )
                } else {
                    it.background = GradientDrawable(
                        GradientDrawable.Orientation.TOP_BOTTOM,
                        intArrayOf(
                            adjustColor(baseColor, 1.2f),
                            baseColor,
                            adjustColor(baseColor, 0.8f)
                        )
                    ).apply { cornerRadius = 1000f }
                }
            }

            // Action Mapping
            val actionMap = mapOf(
                R.id.action_select_all to R.id.cab_select_all,
                R.id.action_delete to R.id.cab_delete,
                R.id.action_copy to listOf(R.id.cab_copy_to_clipboard, R.id.cab_copy_number),
                R.id.action_download to R.id.cab_save_as,
                R.id.action_archive to R.id.cab_archive,
                R.id.action_pin to R.id.cab_pin_conversation,
                R.id.action_unpin to R.id.cab_unpin_conversation
            )

            actionMap.forEach { (viewId, cabIds) ->
                findViewById<View>(viewId)?.apply {
                    val targetCabId = if (cabIds is List<*>) {
                        (cabIds as List<Int>).firstOrNull { actions.contains(it) } ?: -1
                    } else {
                        cabIds as Int
                    }
                    
                    if (targetCabId != -1 && actions.contains(targetCabId)) {
                        visibility = View.VISIBLE
                        if (this is ImageView) imageTintList = tint
                        setOnClickListener { onAction(targetCabId) }
                    } else {
                        visibility = View.GONE
                    }
                }
            }
        } else {
            barContainer.visibility = View.GONE
        }
    }

    override fun onPause() {
        super.onPause()
        // A popup can be dismissed by the activity going away; never leave the blur on.
        TextoGlass.setBlurBehind(this, false)
    }

    override fun onDestroy() {
        // The View machinery already stops the halo animator when the window goes away, but
        // an explicit release keeps a torn-down activity from holding a running ValueAnimator.
        releaseAuroraBackground()
        super.onDestroy()
    }

    private fun requestHighRefreshRate() {
        if (isRPlus()) {
            try {
                val display = display
                val modes = display?.supportedModes
                val maxRefreshRate = modes?.maxByOrNull { it.refreshRate }?.refreshRate ?: 0f
                if (maxRefreshRate > 60f) {
                    window.attributes.preferredRefreshRate = maxRefreshRate
                }
            } catch (_: Exception) {
            }
        }
    }

    override fun getAppIconIDs() = arrayListOf(
        R.mipmap.ic_launcher
    )

    override fun getAppLauncherName() = getString(R.string.app_launcher_name)

    override fun getRepositoryName() = "Messages"

    /** One row of [showBubbleMenu]. */
    data class BubbleAction(val id: Int, val label: String, val iconRes: Int)

    /**
     * Compact icon menu for a single message. It is placed *beside* the bubble rather than
     * over it and deliberately leaves the background unblurred, so the message being acted
     * on stays readable and identifiable.
     */
    fun showBubbleMenu(anchor: View, items: List<BubbleAction>, callback: (Int) -> Unit) {
        if (items.isEmpty()) return

        val barColor = if (config.topBarColor == 0) Color.BLACK else config.topBarColor
        val textColor = config.topBarTextColor.let {
            if (it == barColor || it == Color.TRANSPARENT) {
                if (barColor == Color.WHITE) Color.BLACK else Color.WHITE
            } else {
                it
            }
        }
        // No dividers. Rows separated by rules is the one visual idiom left over from the
        // stock menu; every other list in the app separates by spacing and a capsule.
        val separatorColor = Color.TRANSPARENT
        val rowRadius = 100f * resources.displayMetrics.density
        val radius = 18f * resources.displayMetrics.density

        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = if (config.glassTheme) {
                TextoGlass.panel(
                    tint = barColor,
                    cornerRadius = radius,
                    opacity = 0.92f,
                    strokeWidthPx = 1.getScaledPx()
                )
            } else {
                GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = radius
                    setColor(barColor)
                }
            }
            elevation = 16 * resources.displayMetrics.density
            setPadding(0, 6.getScaledPx(), 0, 6.getScaledPx())
            clipToPadding = false
        }

        val popup = android.widget.PopupWindow(
            container,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 16 * resources.displayMetrics.density
        }

        val rippleValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, rippleValue, true)

        items.forEachIndexed { index, action ->
            val row = android.widget.LinearLayout(this).apply {
                orientation = android.widget.LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(16.getScaledPx(), 11.getScaledPx(), 20.getScaledPx(), 11.getScaledPx())
                isClickable = true
                isFocusable = true
                if (rippleValue.resourceId != 0) setBackgroundResource(rippleValue.resourceId)
                setOnClickListener {
                    popup.dismiss()
                    callback(action.id)
                }
            }

            row.addView(
                ImageView(this).apply {
                    setImageResource(action.iconRes)
                    imageTintList = ColorStateList.valueOf(textColor)
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        20.getScaledPx(),
                        20.getScaledPx()
                    ).apply { marginEnd = 12.getScaledPx() }
                }
            )

            // A blank label means icon-only, for menus where the icons speak for
            // themselves and text would only crowd them.
            if (action.label.isNotEmpty()) {
                row.addView(
                    TextView(this).apply {
                        text = action.label
                        setTextColor(textColor)
                        setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.85f))
                        typeface = typefaceFor(android.graphics.Typeface.BOLD)
                        includeFontPadding = false
                    }
                )
            }

            container.addView(
                row,
                android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )

            if (index != items.lastIndex) {
                container.addView(
                    View(this).apply {
                        setBackgroundColor(separatorColor)
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            1
                        ).apply {
                            marginStart = 16.getScaledPx()
                            marginEnd = 16.getScaledPx()
                        }
                    }
                )
            }
        }

        container.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val menuWidth = container.measuredWidth
        val menuHeight = container.measuredHeight

        val location = IntArray(2)
        anchor.getLocationOnScreen(location)
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val gap = 8.getScaledPx()

        val anchorStart = location[0]
        val anchorEnd = location[0] + anchor.width

        // Prefer the free side of the bubble so the message itself is never covered.
        val x = when {
            anchorEnd + gap + menuWidth <= screenWidth - gap -> anchorEnd + gap
            anchorStart - gap - menuWidth >= gap -> anchorStart - gap - menuWidth
            else -> ((screenWidth - menuWidth) / 2).coerceAtLeast(gap)
        }
        val y = (location[1])
            .coerceAtMost(screenHeight - menuHeight - gap)
            .coerceAtLeast(gap)

        popup.showAtLocation(anchor, android.view.Gravity.NO_GRAVITY, x, y)
    }

    /**
     * iOS-style frosted context menu: translucent tinted panel, hairline rim, hairline
     * separators between rows, and the content behind it blurred while it is open.
     */
    /**
     * The app's overflow menu.
     *
     * A plain [PopupWindow] over a column of capsules rather than a ListPopupWindow: that
     * class caps its own height while measuring, and with rows this tall it silently dropped
     * the last item off the bottom of the sheet. These menus are short enough that a column
     * of views needs no recycling, and this way the sheet is exactly as tall as its content.
     */
    fun showModernMenu(anchor: View, items: List<Pair<Int, String>>, callback: (Int) -> Unit) {
        val density = resources.displayMetrics.density
        val popup = android.widget.PopupWindow(this)

        // The card colour, not the bar colour: this is a sheet, and the app's other sheets --
        // the SIM chooser, the theme picker, the calendar -- are all cards.
        val sheetColor = if (config.recentColor == 0) Color.BLACK else config.recentColor
        val ink = config.mainTextColor
        // Safety: keep the label legible if the two ever land on the same colour.
        val labelColor = if (ink == sheetColor || ink == Color.TRANSPARENT) {
            if (sheetColor == Color.WHITE) Color.BLACK else Color.WHITE
        } else {
            ink
        }

        val gutter = 10.getScaledPx()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(gutter, gutter, gutter, gutter)
            // The sheet follows the user's glass setting, like every other surface in the
            // app. It used to be pinned at .55, which on a light theme left the conversation
            // showing through the menu text.
            background = TextoGlass.panel(
                tint = sheetColor,
                cornerRadius = 24f * density,
                opacity = config.glassOpacity / 100f,
                strokeWidthPx = 1.getScaledPx(),
                sheenAlpha = 0f
            )
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }

        items.forEachIndexed { index, (id, label) ->
            column.addView(
                TextView(this).apply {
                    text = label
                    setTextColor(labelColor)
                    val padH = 20.getScaledPx()
                    val padV = 13.getScaledPx()
                    setPadding(padH, padV, padH, padV)
                    setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.85f))
                    typeface = typefaceFor(android.graphics.Typeface.NORMAL)
                    isClickable = true
                    // Each row is its own capsule. Rows separated by hairlines was the last
                    // idiom left over from the stock menu; everything else in the app
                    // separates by spacing and a pill.
                    background = TextoGlass.bar(
                        tint = config.mainBackgroundColor,
                        cornerRadius = 100f * density,
                        opacity = 0.45f,
                        strokeWidthPx = 1.getScaledPx(),
                        rimAlpha = 0.12f
                    )
                    outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { if (index > 0) topMargin = 6.getScaledPx() }
                    setOnClickListener {
                        popup.dismiss()
                        callback(id)
                    }
                }
            )
        }

        popup.apply {
            contentView = column
            width = 280.getScaledPx()
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            isOutsideTouchable = true
            isFocusable = true
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 12 * density
            setOnDismissListener { TextoGlass.setBlurBehind(this@SimpleActivity, false) }
            // Under RTL the anchor sits at the top left, so the sheet hangs from its end.
            showAsDropDown(anchor, (-10).getScaledPx(), 16.getScaledPx(), android.view.Gravity.END)
        }
        TextoGlass.setBlurBehind(this, true)
    }

    private fun clearGlideTarget(view: View) {
        (view.tag as? CustomTarget<*>)?.let {
            Glide.with(this).clear(it)
            view.tag = null
        }
        Glide.with(this).clear(view)
    }

    class DirectCropTransformation(private val rect: RectF) : BitmapTransformation() {
        override fun transform(pool: com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool, toTransform: Bitmap, outWidth: Int, outHeight: Int): Bitmap {
            val left = (rect.left * toTransform.width).toInt().coerceIn(0, toTransform.width - 1)
            val top = (rect.top * toTransform.height).toInt().coerceIn(0, toTransform.height - 1)
            val right = (rect.right * toTransform.width).toInt().coerceIn(left + 1, toTransform.width)
            val bottom = (rect.bottom * toTransform.height).toInt().coerceIn(top + 1, toTransform.height)
            
            val width = right - left
            val height = bottom - top
            return Bitmap.createBitmap(toTransform, left, top, width, height)
        }

        override fun updateDiskCacheKey(messageDigest: MessageDigest) {
            messageDigest.update("direct_crop_${rect.left}_${rect.top}_${rect.right}_${rect.bottom}".toByteArray())
        }
    }

    private fun loadBackgroundImage(source: String, targetView: View, cropRect: String = "", onLoaded: (() -> Unit)? = null) {
        if (source.isBlank()) return
        
        clearGlideTarget(targetView)
        
        val normalizedRect = try {
            if (cropRect.isNotEmpty()) {
                val parts = cropRect.split(",").map { it.toFloat() }
                RectF(parts[0], parts[1], parts[2], parts[3])
            } else null
        } catch (_: Exception) { null }

        val target = object : CustomTarget<Drawable>() {
            override fun onResourceReady(resource: Drawable, transition: Transition<in Drawable>?) {
                targetView.background = resource
                onLoaded?.invoke()
            }
            override fun onLoadFailed(errorDrawable: Drawable?) {}
            override fun onLoadCleared(placeholder: Drawable?) {}
        }
        
        targetView.tag = target

        var builder = Glide.with(this)
            .load(source)
            .diskCacheStrategy(DiskCacheStrategy.ALL)

        if (normalizedRect != null) {
            builder = builder.transform(DirectCropTransformation(normalizedRect))
        } else {
            builder = builder.centerCrop()
        }
        
        builder.into(target)
    }
}
