package com.texto.sms.helpers

import android.content.Context
import android.graphics.Color
import org.fossify.commons.helpers.BaseConfig
import com.texto.sms.extensions.getDefaultKeyboardHeight
import com.texto.sms.models.Conversation

class Config(context: Context) : BaseConfig(context) {
    companion object {
        fun newInstance(context: Context) = Config(context)
        
        // FINAL ABSOLUTE DEFAULTS
        /**
         * Solid by default. The glass look is still there in the rim, the shadow and the
         * capsule geometry; what the old translucent default bought was a bar you could read
         * message text through, which is the one thing a bar must not do.
         */
        const val DEFAULT_GLASS_OPACITY = 100

        val DEFAULT_DARK_GREY = Color.parseColor("#333333")
        val DEFAULT_LIGHT_GREY = Color.parseColor("#E0E0E0")
        val DEFAULT_SENT_GREY = Color.parseColor("#D8D8D8")

        /**
         * Received bubbles are the heavier side in Classic. Darkened a further 30% from the
         * earlier #ADADAD so they separate clearly from the light #D8D8D8 sent bubble.
         */
        val DEFAULT_RECEIVED_GREY = Color.parseColor("#797979")

        /** Neutral card grey the Classic theme opens with. */
        val DEFAULT_CARD_GREY = Color.parseColor("#EDEDED")

        /** Slot 0 and slot 1 badge colours; deliberately far apart on the hue wheel. */
        val DEFAULT_SIM_COLORS = listOf(
            Color.parseColor("#2F6BFF"),
            Color.parseColor("#FF7A29")
        )

        /**
         * The three aurora hues the skin is built on, converted from the design's oklch
         * values: aurora-3 `oklch(.78 .17 190)`, aurora-1 `oklch(.7 .19 250)` and aurora-2
         * `oklch(.72 .2 330)`. Cyan and blue form the accent gradient; magenta is the third
         * background halo.
         */
        val AURORA_CYAN = Color.parseColor("#00D8CF")
        val AURORA_BLUE = Color.parseColor("#1CA2FF")
        val AURORA_MAGENTA = Color.parseColor("#E76EDF")
    }

    fun saveUseSIMIdAtNumber(number: String, SIMId: Int) {
        prefs.edit().putInt(USE_SIM_ID_PREFIX + number, SIMId).apply()
    }

    fun getUseSIMIdAtNumber(number: String) = prefs.getInt(USE_SIM_ID_PREFIX + number, 0)

    /**
     * Colour standing in for a SIM slot. A tinted badge is far easier to read at a glance
     * than a tiny digit, so the slot number is not drawn at all any more -- the colour is
     * the identifier, and the user picks it.
     */
    fun getSimColor(slot: Int): Int =
        prefs.getInt(SIM_COLOR_PREFIX + slot, DEFAULT_SIM_COLORS.getOrElse(slot) { DEFAULT_SIM_COLORS[0] })

    fun setSimColor(slot: Int, color: Int) {
        prefs.edit().putInt(SIM_COLOR_PREFIX + slot, color).apply()
    }

    var showCharacterCounter: Boolean
        get() = prefs.getBoolean(SHOW_CHARACTER_COUNTER, false)
        set(showCharacterCounter) = prefs.edit()
            .putBoolean(SHOW_CHARACTER_COUNTER, showCharacterCounter).apply()

    var useSimpleCharacters: Boolean
        get() = prefs.getBoolean(USE_SIMPLE_CHARACTERS, false)
        set(useSimpleCharacters) = prefs.edit()
            .putBoolean(USE_SIMPLE_CHARACTERS, useSimpleCharacters).apply()

    var sendOnEnter: Boolean
        get() = prefs.getBoolean(SEND_ON_ENTER, false)
        set(sendOnEnter) = prefs.edit().putBoolean(SEND_ON_ENTER, sendOnEnter).apply()

    var enableDeliveryReports: Boolean
        // On by default: without requesting a report the carrier never tells us a message
        // arrived, so the second tick could never appear.
        get() = prefs.getBoolean(ENABLE_DELIVERY_REPORTS, true)
        set(enableDeliveryReports) = prefs.edit()
            .putBoolean(ENABLE_DELIVERY_REPORTS, enableDeliveryReports).apply()

    var sendLongMessageMMS: Boolean
        get() = prefs.getBoolean(SEND_LONG_MESSAGE_MMS, false)
        set(sendLongMessageMMS) = prefs.edit().putBoolean(SEND_LONG_MESSAGE_MMS, sendLongMessageMMS)
            .apply()

    var sendGroupMessageMMS: Boolean
        get() = prefs.getBoolean(SEND_GROUP_MESSAGE_MMS, false)
        set(sendGroupMessageMMS) = prefs.edit()
            .putBoolean(SEND_GROUP_MESSAGE_MMS, sendGroupMessageMMS).apply()

    var lockScreenVisibilitySetting: Int
        get() = prefs.getInt(LOCK_SCREEN_VISIBILITY, LOCK_SCREEN_SENDER_MESSAGE)
        set(lockScreenVisibilitySetting) = prefs.edit()
            .putInt(LOCK_SCREEN_VISIBILITY, lockScreenVisibilitySetting).apply()

    var mmsFileSizeLimit: Long
        get() = prefs.getLong(MMS_FILE_SIZE_LIMIT, FILE_SIZE_100_MB)
        set(mmsFileSizeLimit) = prefs.edit().putLong(MMS_FILE_SIZE_LIMIT, mmsFileSizeLimit).apply()

    var pinnedConversations: Set<String>
        get() = prefs.getStringSet(PINNED_CONVERSATIONS, HashSet<String>())!!
        set(pinnedConversations) = prefs.edit()
            .putStringSet(PINNED_CONVERSATIONS, pinnedConversations).apply()

    fun addPinnedConversationByThreadId(threadId: Long) {
        pinnedConversations = pinnedConversations.plus(threadId.toString())
    }

    fun addPinnedConversations(conversations: List<Conversation>) {
        pinnedConversations = pinnedConversations.plus(conversations.map { it.threadId.toString() })
    }

    fun removePinnedConversationByThreadId(threadId: Long) {
        pinnedConversations = pinnedConversations.minus(threadId.toString())
    }

    fun removePinnedConversations(conversations: List<Conversation>) {
        pinnedConversations =
            pinnedConversations.minus(conversations.map { it.threadId.toString() })
    }


    var exportSms: Boolean
        get() = prefs.getBoolean(EXPORT_SMS, true)
        set(exportSms) = prefs.edit().putBoolean(EXPORT_SMS, exportSms).apply()

    var exportMms: Boolean
        get() = prefs.getBoolean(EXPORT_MMS, true)
        set(exportMms) = prefs.edit().putBoolean(EXPORT_MMS, exportMms).apply()

    var importSms: Boolean
        get() = prefs.getBoolean(IMPORT_SMS, true)
        set(importSms) = prefs.edit().putBoolean(IMPORT_SMS, importSms).apply()

    var importMms: Boolean
        get() = prefs.getBoolean(IMPORT_MMS, true)
        set(importMms) = prefs.edit().putBoolean(IMPORT_MMS, importMms).apply()

    var wasDbCleared: Boolean
        get() = prefs.getBoolean(WAS_DB_CLEARED, false)
        set(wasDbCleared) = prefs.edit().putBoolean(WAS_DB_CLEARED, wasDbCleared).apply()

    var keyboardHeight: Int
        get() = prefs.getInt(SOFT_KEYBOARD_HEIGHT, context.getDefaultKeyboardHeight())
        set(keyboardHeight) = prefs.edit().putInt(SOFT_KEYBOARD_HEIGHT, keyboardHeight).apply()

    var useRecycleBin: Boolean
        // On by default: deleting a message is otherwise irreversible, and the bin now has a
        // bounded size so leaving it on cannot grow without limit.
        get() = prefs.getBoolean(USE_RECYCLE_BIN, true)
        set(useRecycleBin) = prefs.edit().putBoolean(USE_RECYCLE_BIN, useRecycleBin).apply()

    var lastRecycleBinCheck: Long
        get() = prefs.getLong(LAST_RECYCLE_BIN_CHECK, 0L)
        set(lastRecycleBinCheck) = prefs.edit().putLong(LAST_RECYCLE_BIN_CHECK, lastRecycleBinCheck)
            .apply()

    var isArchiveAvailable: Boolean
        get() = prefs.getBoolean(IS_ARCHIVE_AVAILABLE, true)
        set(isArchiveAvailable) = prefs.edit().putBoolean(IS_ARCHIVE_AVAILABLE, isArchiveAvailable)
            .apply()

    var customNotifications: Set<String>
        get() = prefs.getStringSet(CUSTOM_NOTIFICATIONS, HashSet<String>())!!
        set(customNotifications) = prefs.edit()
            .putStringSet(CUSTOM_NOTIFICATIONS, customNotifications).apply()

    fun addCustomNotificationsByThreadId(threadId: Long) {
        customNotifications = customNotifications.plus(threadId.toString())
    }

    fun removeCustomNotificationsByThreadId(threadId: Long) {
        customNotifications = customNotifications.minus(threadId.toString())
    }


    var keepConversationsArchived: Boolean
        get() = prefs.getBoolean(KEEP_CONVERSATIONS_ARCHIVED, false)
        set(keepConversationsArchived) = prefs.edit()
            .putBoolean(KEEP_CONVERSATIONS_ARCHIVED, keepConversationsArchived).apply()

    var useNewUi: Boolean
        get() = prefs.getBoolean(USE_NEW_UI, true)
        set(useNewUi) = prefs.edit().putBoolean(USE_NEW_UI, useNewUi).apply()

    var conversationOrder: String
        get() = prefs.getString(CONVERSATION_ORDER, "")!!
        set(conversationOrder) = prefs.edit().putString(CONVERSATION_ORDER, conversationOrder).apply()

    var contactSortingMode: Int
        get() = prefs.getInt(CONTACT_SORTING_MODE, 0) // 0: Manual, 1: Alphabetical, 2: Recent
        set(contactSortingMode) = prefs.edit().putInt(CONTACT_SORTING_MODE, contactSortingMode).apply()

    var recentColor: Int
        get() = prefs.getInt(RECENT_COLOR, DEFAULT_CARD_GREY)
        set(recentColor) = prefs.edit().putInt(RECENT_COLOR, recentColor).apply()

    var uiScale: Float
        get() = prefs.getFloat(UI_SCALE, 1.0f)
        set(uiScale) = prefs.edit().putFloat(UI_SCALE, uiScale).apply()

    /**
     * Which language the UI speaks: [TextoLocale.SYSTEM], [TextoLocale.PERSIAN] or
     * [TextoLocale.ENGLISH]. Following the phone is the default.
     */
    var appLanguage: Int
        get() = prefs.getInt(APP_LANGUAGE, TextoLocale.SYSTEM)
        set(appLanguage) = prefs.edit().putInt(APP_LANGUAGE, appLanguage).apply()

    fun resetColors() {
        prefs.edit().remove(TOP_BAR_COLOR).remove(TOP_BAR_TEXT_COLOR).remove(MAIN_BACKGROUND_COLOR).remove(MAIN_TEXT_COLOR).remove(INPUT_BAR_BACKGROUND_COLOR).remove(INPUT_BAR_TEXT_COLOR).remove(SENT_BUBBLE_COLOR).remove(SENT_BUBBLE_TEXT_COLOR).remove(RECEIVED_BUBBLE_COLOR).remove(RECEIVED_BUBBLE_TEXT_COLOR).remove(RECENT_COLOR)
            .remove(TOP_BAR_IMAGE).remove(MAIN_BACKGROUND_IMAGE).remove(INPUT_BAR_IMAGE)
            .remove(TOP_BAR_BG_MODE).remove(MAIN_BG_MODE).remove(INPUT_BAR_BG_MODE)
            .remove(APP_THEME).remove(MAIN_BG_GRADIENT_START).remove(MAIN_BG_GRADIENT_END)
            .remove(ACCENT_GRADIENT_START).remove(ACCENT_GRADIENT_END).remove(ACCENT_GRADIENT_MID)
            .remove(AURORA_ACCENT_COLOR).remove(AURORA_ANIMATE)
            .remove(AURORA_HALO_ONE).remove(AURORA_HALO_TWO).remove(AURORA_HALO_THREE)
            .remove(AURORA_HALO_OPACITY)
            .remove(CARD_CORNER_RADIUS)
            // The tonality and the glass level are appearance too, and leaving them behind
            // was why a reset did not land where a fresh install lands: the accent came back
            // still rotated to whatever the strip had been dragged to.
            .remove(ACCENT_HUE_SHIFT).remove(GLASS_OPACITY).apply()
    }

    /**
     * Puts every setting back where a fresh install starts, not just the colours.
     *
     * [resetColors] names its keys one by one, which is why the reset row kept missing
     * things: the font, the font family, the UI scale, the delivery reports, the recycle
     * bin, the bubble outlines and everything added after it was written all survived it.
     * This clears the store instead and lets each getter fall back to its own default, so a
     * setting added later is covered without anyone remembering to add it here.
     *
     * Three things are deliberately kept:
     *
     *  - **The app lock.** Anything holding a password, a hash or a protection type stays.
     *    A reset must not quietly take the lock off the app or off a locked conversation.
     *  - **User content**, which is not a setting: pinned conversations, the filters the
     *    user built, the archive and recycle:bin bookkeeping. (Drafts are in Room, not
     *    here, so they are out of reach either way.)
     *  - **The migration flags and the app's own bookkeeping**, so clearing them does not
     *    re:run a one:time move on the next launch.
     */
    fun resetAllSettings() {
        val keep = setOf(
            PINNED_CONVERSATIONS, CUSTOM_FILTERS,
            NOCTURNE_REFRESH_APPLIED, NEON_REFRESH_APPLIED, NEON_LIGHT_DEFAULT_APPLIED,
            CLASSIC_DEFAULT_APPLIED, NEON_LIGHT_RESTORED, BUBBLE_SIDES_SWAPPED,
            GLASS_RECALIBRATED,
            LAST_RECYCLE_BIN_CHECK, IS_ARCHIVE_AVAILABLE,
            // Language is not an appearance setting: a look reset should not silently put
            // an English user back on Persian.
            APP_LANGUAGE,
        )
        // Matched on the name rather than listed: the protection keys live in commons and
        // are not visible from here, and a missed one would silently unlock the app.
        val protectedWords = listOf("password", "protection", "hash", "pin_", "lock")
        val editor = prefs.edit()
        prefs.all.keys
            .filter { key ->
                key !in keep &&
                    !key.startsWith("draft_") &&
                    protectedWords.none { key.contains(it, ignoreCase = true) }
            }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    var appTheme: Int
        get() = prefs.getInt(APP_THEME, AppThemes.DEFAULT)
        set(appTheme) = prefs.edit().putInt(APP_THEME, appTheme).apply()

    /** Set once the install has been moved onto the Nocturne design. See App.onCreate. */
    var nocturneRefreshApplied: Boolean
        get() = prefs.getBoolean(NOCTURNE_REFRESH_APPLIED, false)
        set(applied) = prefs.edit().putBoolean(NOCTURNE_REFRESH_APPLIED, applied).apply()

    /** Set once the install has been moved onto the Neon design. See App.onCreate. */
    /** One-time move onto the recalibrated glass scale; see [GLASS_OPACITY_MIN]. */
    var glassRecalibrated: Boolean
        get() = prefs.getBoolean(GLASS_RECALIBRATED, false)
        set(done) = prefs.edit().putBoolean(GLASS_RECALIBRATED, done).apply()

    var neonRefreshApplied: Boolean
        get() = prefs.getBoolean(NEON_REFRESH_APPLIED, false)
        set(applied) = prefs.edit().putBoolean(NEON_REFRESH_APPLIED, applied).apply()

    /**
     * Set once the install has been moved onto Neon's light variant, which is the default
     * now rather than its dark one. Needs its own flag because [neonRefreshApplied] is
     * already true on every install that has run since Neon landed, so reusing it would move
     * nobody.
     */
    var neonLightDefaultApplied: Boolean
        get() = prefs.getBoolean(NEON_LIGHT_DEFAULT_APPLIED, false)
        set(applied) = prefs.edit().putBoolean(NEON_LIGHT_DEFAULT_APPLIED, applied).apply()

    /**
     * Set once the install has been moved onto Classic, which carries the palette the app
     * now ships with. A third flag rather than a reused one: the two before it are already
     * true everywhere, so either would move nobody.
     */
    var classicDefaultApplied: Boolean
        get() = prefs.getBoolean(CLASSIC_DEFAULT_APPLIED, false)
        set(applied) = prefs.edit().putBoolean(CLASSIC_DEFAULT_APPLIED, applied).apply()

    /**
     * Set once the install has been moved back onto Neon's light variant, which is the
     * default again. Classic keeps the palette it was given -- it is still there to pick --
     * but it is no longer what a fresh install opens on. A fourth flag for the same reason
     * as the third: the three before it are true on every install that has launched since,
     * so reusing any of them would move nobody.
     */
    var neonLightRestored: Boolean
        get() = prefs.getBoolean(NEON_LIGHT_RESTORED, false)
        set(applied) = prefs.edit().putBoolean(NEON_LIGHT_RESTORED, applied).apply()

    /**
     * Set once an install wearing the retired Aurora skin has been moved onto Neon. Its own
     * flag rather than a reuse of the others: those have all already run on existing
     * installs, so nothing that keys off them would fire again for this.
     */
    var auroraRetired: Boolean
        get() = prefs.getBoolean(AURORA_RETIRED, false)
        set(applied) = prefs.edit().putBoolean(AURORA_RETIRED, applied).apply()

    /**
     * Set once the two bubble colours have been written the new way round.
     *
     * The swap lives in [AppThemes.apply], so an install whose colours were already stored
     * would keep the old pair and draw the outgoing bubble in the accent's end stop as a
     * flat slab -- the gradient gone from both sides. Its own flag, like the three before
     * it, because those are already true everywhere.
     */
    var bubbleSidesSwapped: Boolean
        get() = prefs.getBoolean(BUBBLE_SIDES_SWAPPED, false)
        set(applied) = prefs.edit().putBoolean(BUBBLE_SIDES_SWAPPED, applied).apply()

    /**
     * False until a theme has actually been written, which is what separates a fresh install
     * from someone who deliberately picked Classic. appRunCount cannot answer this: App
     * forces it to 100 on every launch to suppress the upstream first-run popups.
     */
    val hasStoredAppTheme: Boolean
        get() = prefs.contains(APP_THEME)

    var mainBgGradientStart: Int
        get() = prefs.getInt(MAIN_BG_GRADIENT_START, mainBackgroundColor)
        set(color) = prefs.edit().putInt(MAIN_BG_GRADIENT_START, color).apply()

    var mainBgGradientEnd: Int
        get() = prefs.getInt(MAIN_BG_GRADIENT_END, mainBackgroundColor)
        set(color) = prefs.edit().putInt(MAIN_BG_GRADIENT_END, color).apply()

    /**
     * The skin's emphasis gradient. Every accent surface -- sent bubble, unread badge, active
     * filter chip, FAB, active nav tab -- is painted with this one pair, which is what makes
     * them read as a set. Defaults keep a theme-less install on the Aurora cyan-to-blue.
     */
    /**
     * How far around the colour wheel the user has dragged the tonality strip, in degrees.
     *
     * It is applied on the way OUT of the accent getters below rather than written into
     * them, which is what keeps it orthogonal to the theme: picking a new skin rewrites the
     * stored colours and the shift rides along untouched, and dragging back to 0 restores
     * the skin's own hues exactly instead of leaving them somewhere approximate. Every
     * accent surface in the app already reads these getters, so nothing else has to know
     * this exists.
     */
    var accentHueShift: Int
        get() = prefs.getInt(ACCENT_HUE_SHIFT, 0).mod(360)
        set(degrees) = prefs.edit().putInt(ACCENT_HUE_SHIFT, degrees.mod(360)).apply()

    /**
     * 0 is not a colour here, it is the "unset" marker that [accentGradientMid] and the halo
     * slots are tested against. Rotating it would produce a transparent black that is no
     * longer `== 0`, so every one of those checks would silently start taking the wrong
     * branch: the middle gradient stop would come back from the dead as a smear of nothing.
     */
    private fun tinted(color: Int): Int =
        if (color == 0 || accentHueShift == 0) color else TextoTint.rotateHue(color, accentHueShift)

    var accentGradientStart: Int
        get() = tinted(prefs.getInt(ACCENT_GRADIENT_START, AURORA_CYAN))
        set(color) = prefs.edit().putInt(ACCENT_GRADIENT_START, color).apply()

    var accentGradientEnd: Int
        get() = tinted(prefs.getInt(ACCENT_GRADIENT_END, AURORA_BLUE))
        set(color) = prefs.edit().putInt(ACCENT_GRADIENT_END, color).apply()

    /**
     * Optional middle stop of the accent gradient, at [ACCENT_GRADIENT_MID_POSITION].
     *
     * The design's `--grad` runs through three colours, and the middle one is not what a
     * straight two-stop blend produces: cyan to violet interpolates through a dull grey-mauve
     * in sRGB, where the design passes through a bright sky blue. Dropping the stop was what
     * made the accent read as flat and muddy next to the mockup even though both ends matched
     * exactly. 0 means "no middle stop", so a user-picked two-colour accent still works.
     */
    var accentGradientMid: Int
        get() = tinted(prefs.getInt(ACCENT_GRADIENT_MID, 0))
        set(color) = prefs.edit().putInt(ACCENT_GRADIENT_MID, color).apply()

    /**
     * The three halos painted over the main background, deliberately independent of the
     * accent gradient. 0 in a slot means "unset", and [auroraHaloColors] falls back to the
     * accent trio there so themes that never defined halos look exactly as they did.
     */
    var auroraHaloOne: Int
        get() = tinted(prefs.getInt(AURORA_HALO_ONE, 0))
        set(color) = prefs.edit().putInt(AURORA_HALO_ONE, color).apply()

    var auroraHaloTwo: Int
        get() = tinted(prefs.getInt(AURORA_HALO_TWO, 0))
        set(color) = prefs.edit().putInt(AURORA_HALO_TWO, color).apply()

    var auroraHaloThree: Int
        get() = tinted(prefs.getInt(AURORA_HALO_THREE, 0))
        set(color) = prefs.edit().putInt(AURORA_HALO_THREE, color).apply()

    /** 0f..1f multiplier on every halo's alpha. */
    var auroraHaloOpacity: Float
        get() = prefs.getFloat(AURORA_HALO_OPACITY, 1f).coerceIn(0f, 1f)
        set(value) = prefs.edit().putFloat(AURORA_HALO_OPACITY, value).apply()

    /** Halo colours for the background, falling back to the accent trio slot by slot. */
    val auroraHaloColors: List<Int>
        get() = listOf(
            auroraHaloOne.takeIf { it != 0 } ?: accentGradientStart,
            auroraHaloTwo.takeIf { it != 0 } ?: accentGradientEnd,
            auroraHaloThree.takeIf { it != 0 } ?: auroraAccentColor
        )

    /** Third halo hue behind the app, alongside the two accent-gradient stops. */
    var auroraAccentColor: Int
        get() = tinted(prefs.getInt(AURORA_ACCENT_COLOR, AURORA_MAGENTA))
        set(color) = prefs.edit().putInt(AURORA_ACCENT_COLOR, color).apply()

    /** Whether the background halos drift. Off still paints them, just frozen. */
    var auroraAnimate: Boolean
        get() = prefs.getBoolean(AURORA_ANIMATE, true)
        set(animate) = prefs.edit().putBoolean(AURORA_ANIMATE, animate).apply()

    /** Very large values render conversation cards as full pills. */
    var cardCornerRadiusDp: Int
        get() = prefs.getInt(CARD_CORNER_RADIUS, 500)
        set(radius) = prefs.edit().putInt(CARD_CORNER_RADIUS, radius).apply()

    /**
     * Colours chosen recently, newest first, across every colour row in settings.
     *
     * Shared rather than per row on purpose: someone building a skin picks the same few
     * colours for the bar, the bubble and the card, and a per-row history would make them
     * find each one again from scratch every time.
     */
    var recentColours: List<Int>
        get() = prefs.getString(RECENT_COLOURS, "")!!
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
        set(colours) = prefs.edit()
            .putString(RECENT_COLOURS, colours.take(RECENT_COLOURS_KEPT).joinToString(","))
            .apply()

    /** Records [colour] as the newest choice, without letting it appear twice. */
    fun rememberColour(colour: Int) {
        val opaque = colour or 0xFF000000.toInt()
        recentColours = listOf(opaque) + recentColours.filter { it != opaque }
    }

    /** User-defined filter chips, serialized as JSON. */
    var customFilters: List<MessageFilter>
        get() = FilterStore.decode(prefs.getString(CUSTOM_FILTERS, "")!!)
        set(filters) = prefs.edit().putString(CUSTOM_FILTERS, FilterStore.encode(filters)).apply()

    var activeFilterId: String
        get() = prefs.getString(ACTIVE_FILTER_ID, MessageFilter.ID_ALL)!!
        set(activeFilterId) = prefs.edit().putString(ACTIVE_FILTER_ID, activeFilterId).apply()

    /** Toggles the built-in "Contacts only" filter chip on the home screen. */
    var showAdsFilter: Boolean
        get() = prefs.getBoolean(SHOW_ADS_FILTER, false)
        set(showAdsFilter) = prefs.edit().putBoolean(SHOW_ADS_FILTER, showAdsFilter).apply()

    /**
     * The built-in ads filter's stored senders. Kept apart from [customFilters] so it cannot
     * be renamed or deleted from the filter editor, and so turning the chip off in settings
     * does not throw away what has already been sorted into it.
     */
    var adsFilter: MessageFilter
        get() = FilterStore.decodeOne(prefs.getString(ADS_FILTER, "") ?: "")
            ?: MessageFilter.ads("", emptyList(), emptyList())
        set(adsFilter) = prefs.edit()
            .putString(ADS_FILTER, FilterStore.encodeOne(adsFilter)).apply()

    /** Filter chip selected when the app is opened. Defaults to "All". */
    var defaultFilterId: String
        get() = prefs.getString(DEFAULT_FILTER_ID, MessageFilter.ID_ALL) ?: MessageFilter.ID_ALL
        set(defaultFilterId) = prefs.edit()
            .putString(DEFAULT_FILTER_ID, defaultFilterId).apply()

    var showContactsOnlyFilter: Boolean
        get() = prefs.getBoolean(SHOW_CONTACTS_ONLY_FILTER, false)
        set(showContactsOnlyFilter) = prefs.edit()
            .putBoolean(SHOW_CONTACTS_ONLY_FILTER, showContactsOnlyFilter).apply()

    var glassTheme: Boolean
        get() = prefs.getBoolean(GLASS_THEME, true)
        set(glassTheme) = prefs.edit().putBoolean(GLASS_THEME, glassTheme).apply()

    /**
     * How opaque the frosted surfaces are, as a percentage. Lower reads as more see-through.
     *
     * The floor is 40 rather than 20: below that the bars stopped being surfaces and the
     * lower half of the slider's travel was all unusable, which left the usable part of the
     * control squeezed into its top third.
     */
    var glassOpacity: Int
        get() = prefs.getInt(GLASS_OPACITY, DEFAULT_GLASS_OPACITY).coerceIn(GLASS_OPACITY_MIN, 100)
        set(glassOpacity) = prefs.edit().putInt(GLASS_OPACITY, glassOpacity).apply()

    var fontFamilyTexto: Int
        // Vazirmatn is the app's default face -- it ships with the app (SIL OFL) and is the
        // only bundled family with a real bold cut. TextoFonts falls back to the system font
        // on its own if the asset is ever missing, so this is safe even without the file.
        get() = prefs.getInt(FONT_FAMILY_TEXTO, TextoFonts.FONT_VAZIRMATN)
        set(fontFamilyTexto) = prefs.edit().putInt(FONT_FAMILY_TEXTO, fontFamilyTexto).apply()

    var topBarColor: Int
        get() = prefs.getInt(TOP_BAR_COLOR, 0)
        set(topBarColor) = prefs.edit().putInt(TOP_BAR_COLOR, topBarColor).apply()

    var topBarTextColor: Int
        get() = prefs.getInt(TOP_BAR_TEXT_COLOR, Color.WHITE)
        set(topBarTextColor) = prefs.edit().putInt(TOP_BAR_TEXT_COLOR, topBarTextColor).apply()

    var mainTextColor: Int
        get() = prefs.getInt(MAIN_TEXT_COLOR, Color.BLACK)
        set(mainTextColor) = prefs.edit().putInt(MAIN_TEXT_COLOR, mainTextColor).apply()

    var mainBackgroundColor: Int
        get() = prefs.getInt(MAIN_BACKGROUND_COLOR, Color.WHITE)
        set(mainBackgroundColor) = prefs.edit().putInt(MAIN_BACKGROUND_COLOR, mainBackgroundColor).apply()

    /**
     * The top and bottom (search/typing) bars share one background setting now, so this is
     * an alias for [topBarColor] rather than its own stored value -- kept as a distinct
     * property because callers still read/write "the input bar's colour".
     */
    var inputBarBackgroundColor: Int
        get() = if (topBarColor != 0) topBarColor else Color.BLACK
        set(inputBarBackgroundColor) {
            topBarColor = inputBarBackgroundColor
        }

    var inputBarTextColor: Int
        get() = prefs.getInt(INPUT_BAR_TEXT_COLOR, Color.WHITE)
        set(inputBarTextColor) = prefs.edit().putInt(INPUT_BAR_TEXT_COLOR, inputBarTextColor).apply()

    var sentBubbleColor: Int
        get() = prefs.getInt(SENT_BUBBLE_COLOR, DEFAULT_SENT_GREY)
        set(sentBubbleColor) = prefs.edit().putInt(SENT_BUBBLE_COLOR, sentBubbleColor).apply()

    var receivedBubbleColor: Int
        get() = prefs.getInt(RECEIVED_BUBBLE_COLOR, DEFAULT_RECEIVED_GREY)
        set(receivedBubbleColor) = prefs.edit().putInt(RECEIVED_BUBBLE_COLOR, receivedBubbleColor).apply()

    /**
     * True once the user has picked a colour for the received bubble themselves.
     *
     * The received side carries the accent gradient by default, which left its colour
     * picker with nothing to do. This is what lets the picker win: set it, and the bubble
     * drops the gradient for the flat colour chosen. Applying a theme or resetting clears
     * it, so a skin change puts the gradient back.
     */
    var receivedBubbleColorSet: Boolean
        get() = prefs.getBoolean(RECEIVED_BUBBLE_COLOR_SET, false)
        set(isSet) = prefs.edit().putBoolean(RECEIVED_BUBBLE_COLOR_SET, isSet).apply()

    /**
     * Ink for anything painted on the accent gradient that is not a message bubble: the
     * active filter chip, the send button, the unread badge, a picked date, a dialog's
     * confirm row.
     *
     * These all read [sentBubbleTextColor] before, which was only ever right because the
     * gradient happened to sit on the outgoing bubble. It sits on the incoming one now, and
     * the two bubble inks belong to the settings screen's two pickers, so this is the app's
     * own on-accent ink and moves with the theme rather than with either bubble.
     */
    var accentInkColor: Int
        get() = prefs.getInt(ACCENT_INK_COLOR, Color.WHITE)
        set(color) = prefs.edit().putInt(ACCENT_INK_COLOR, color).apply()

    var sentBubbleTextColor: Int
        get() = prefs.getInt(SENT_BUBBLE_TEXT_COLOR, Color.BLACK)
        set(sentBubbleTextColor) = prefs.edit().putInt(SENT_BUBBLE_TEXT_COLOR, sentBubbleTextColor).apply()

    var receivedBubbleTextColor: Int
        get() = prefs.getInt(RECEIVED_BUBBLE_TEXT_COLOR, Color.BLACK)
        set(receivedBubbleTextColor) = prefs.edit().putInt(RECEIVED_BUBBLE_TEXT_COLOR, receivedBubbleTextColor).apply()

    var topBarImage: String
        get() = prefs.getString(TOP_BAR_IMAGE, "")!!
        set(topBarImage) = prefs.edit().putString(TOP_BAR_IMAGE, topBarImage).apply()

    var mainBackgroundImage: String
        get() = prefs.getString(MAIN_BACKGROUND_IMAGE, "")!!
        set(mainBackgroundImage) = prefs.edit().putString(MAIN_BACKGROUND_IMAGE, mainBackgroundImage).apply()

    /** Alias for [topBarImage] -- see [inputBarBackgroundColor]. */
    var inputBarImage: String
        get() = topBarImage
        set(inputBarImage) {
            topBarImage = inputBarImage
        }

    var topBarBgMode: Int
        get() = prefs.getInt(TOP_BAR_BG_MODE, BG_MODE_COLOR)
        set(topBarBgMode) = prefs.edit().putInt(TOP_BAR_BG_MODE, topBarBgMode).apply()

    var mainBgMode: Int
        get() = prefs.getInt(MAIN_BG_MODE, BG_MODE_COLOR)
        set(mainBgMode) = prefs.edit().putInt(MAIN_BG_MODE, mainBgMode).apply()

    /** Alias for [topBarBgMode] -- see [inputBarBackgroundColor]. */
    var inputBarBgMode: Int
        get() = topBarBgMode
        set(inputBarBgMode) {
            topBarBgMode = inputBarBgMode
        }

    var topBarCropRect: String
        get() = prefs.getString(TOP_BAR_CROP_RECT, "")!!
        set(topBarCropRect) = prefs.edit().putString(TOP_BAR_CROP_RECT, topBarCropRect).apply()

    var mainBgCropRect: String
        get() = prefs.getString(MAIN_BG_CROP_RECT, "")!!
        set(mainBgCropRect) = prefs.edit().putString(MAIN_BG_CROP_RECT, mainBgCropRect).apply()

    /** Alias for [topBarCropRect] -- see [inputBarBackgroundColor]. */
    var inputBarCropRect: String
        get() = topBarCropRect
        set(inputBarCropRect) {
            topBarCropRect = inputBarCropRect
        }

    var alwaysExpandSearchBar: Boolean
        get() = prefs.getBoolean(ALWAYS_EXPAND_SEARCH_BAR, false)
        set(alwaysExpandSearchBar) = prefs.edit().putBoolean(ALWAYS_EXPAND_SEARCH_BAR, alwaysExpandSearchBar).apply()

    var preset1Name: String
        get() = prefs.getString(PRESET_1_NAME, "Preset 1")!!
        set(preset1Name) = prefs.edit().putString(PRESET_1_NAME, preset1Name).apply()

    var preset2Name: String
        get() = prefs.getString(PRESET_2_NAME, "Preset 2")!!
        set(preset2Name) = prefs.edit().putString(PRESET_2_NAME, preset2Name).apply()

    var preset3Name: String
        get() = prefs.getString(PRESET_3_NAME, "Preset 3")!!
        set(preset3Name) = prefs.edit().putString(PRESET_3_NAME, preset3Name).apply()

    var topBarOutline: Boolean
        get() = prefs.getBoolean(TOP_BAR_OUTLINE, false)
        set(topBarOutline) = prefs.edit().putBoolean(TOP_BAR_OUTLINE, topBarOutline).apply()

    var topBarOutlineColor: Int
        get() = prefs.getInt(TOP_BAR_OUTLINE_COLOR, Color.BLACK)
        set(topBarOutlineColor) = prefs.edit().putInt(TOP_BAR_OUTLINE_COLOR, topBarOutlineColor).apply()

    var topBarOutlineThickness: Int
        get() = prefs.getInt(TOP_BAR_OUTLINE_THICKNESS, 2)
        set(topBarOutlineThickness) = prefs.edit().putInt(TOP_BAR_OUTLINE_THICKNESS, topBarOutlineThickness).apply()

    var searchBarOutline: Boolean
        get() = prefs.getBoolean(SEARCH_BAR_OUTLINE, false)
        set(searchBarOutline) = prefs.edit().putBoolean(SEARCH_BAR_OUTLINE, searchBarOutline).apply()

    var searchBarOutlineColor: Int
        get() = prefs.getInt(SEARCH_BAR_OUTLINE_COLOR, Color.BLACK)
        set(searchBarOutlineColor) = prefs.edit().putInt(SEARCH_BAR_OUTLINE_COLOR, searchBarOutlineColor).apply()

    var searchBarOutlineThickness: Int
        get() = prefs.getInt(SEARCH_BAR_OUTLINE_THICKNESS, 2)
        set(searchBarOutlineThickness) = prefs.edit().putInt(SEARCH_BAR_OUTLINE_THICKNESS, searchBarOutlineThickness).apply()

    var bigContactsOutline: Boolean
        get() = prefs.getBoolean(BIG_CONTACTS_OUTLINE, false)
        set(bigContactsOutline) = prefs.edit().putBoolean(BIG_CONTACTS_OUTLINE, bigContactsOutline).apply()

    var bigContactsOutlineColor: Int
        get() = prefs.getInt(BIG_CONTACTS_OUTLINE_COLOR, Color.BLACK)
        set(bigContactsOutlineColor) = prefs.edit().putInt(BIG_CONTACTS_OUTLINE_COLOR, bigContactsOutlineColor).apply()

    var bigContactsOutlineThickness: Int
        get() = prefs.getInt(BIG_CONTACTS_OUTLINE_THICKNESS, 2)
        set(bigContactsOutlineThickness) = prefs.edit().putInt(BIG_CONTACTS_OUTLINE_THICKNESS, bigContactsOutlineThickness).apply()

    var smallContactsOutline: Boolean
        get() = prefs.getBoolean(SMALL_CONTACTS_OUTLINE, false)
        set(smallContactsOutline) = prefs.edit().putBoolean(SMALL_CONTACTS_OUTLINE, smallContactsOutline).apply()

    var smallContactsOutlineColor: Int
        get() = prefs.getInt(SMALL_CONTACTS_OUTLINE_COLOR, Color.BLACK)
        set(smallContactsOutlineColor) = prefs.edit().putInt(SMALL_CONTACTS_OUTLINE_COLOR, smallContactsOutlineColor).apply()

    var smallContactsOutlineThickness: Int
        get() = prefs.getInt(SMALL_CONTACTS_OUTLINE_THICKNESS, 2)
        set(smallContactsOutlineThickness) = prefs.edit().putInt(SMALL_CONTACTS_OUTLINE_THICKNESS, smallContactsOutlineThickness).apply()

    var sentBubblesOutline: Boolean
        get() = prefs.getBoolean(SENT_BUBBLES_OUTLINE, false)
        set(sentBubblesOutline) = prefs.edit().putBoolean(SENT_BUBBLES_OUTLINE, sentBubblesOutline).apply()

    var sentBubblesOutlineColor: Int
        get() = prefs.getInt(SENT_BUBBLES_OUTLINE_COLOR, Color.BLACK)
        set(sentBubblesOutlineColor) = prefs.edit().putInt(SENT_BUBBLES_OUTLINE_COLOR, sentBubblesOutlineColor).apply()

    var sentBubblesOutlineThickness: Int
        get() = prefs.getInt(SENT_BUBBLES_OUTLINE_THICKNESS, 2)
        set(sentBubblesOutlineThickness) = prefs.edit().putInt(SENT_BUBBLES_OUTLINE_THICKNESS, sentBubblesOutlineThickness).apply()

    var receivedBubblesOutline: Boolean
        get() = prefs.getBoolean(RECEIVED_BUBBLES_OUTLINE, false)
        set(receivedBubblesOutline) = prefs.edit().putBoolean(RECEIVED_BUBBLES_OUTLINE, receivedBubblesOutline).apply()

    var receivedBubblesOutlineColor: Int
        get() = prefs.getInt(RECEIVED_BUBBLES_OUTLINE_COLOR, Color.BLACK)
        set(receivedBubblesOutlineColor) = prefs.edit().putInt(RECEIVED_BUBBLES_OUTLINE_COLOR, receivedBubblesOutlineColor).apply()

    var receivedBubblesOutlineThickness: Int
        get() = prefs.getInt(RECEIVED_BUBBLES_OUTLINE_THICKNESS, 2)
        set(receivedBubblesOutlineThickness) = prefs.edit().putInt(RECEIVED_BUBBLES_OUTLINE_THICKNESS, receivedBubblesOutlineThickness).apply()

    fun savePreset(id: Int) {
        val prefix = "preset_${id}_"
        val editor = prefs.edit()
        val themeKeys = listOf(
            TOP_BAR_COLOR, TOP_BAR_TEXT_COLOR, MAIN_TEXT_COLOR, MAIN_BACKGROUND_COLOR,
            INPUT_BAR_BACKGROUND_COLOR, INPUT_BAR_TEXT_COLOR, SENT_BUBBLE_COLOR,
            RECEIVED_BUBBLE_COLOR, SENT_BUBBLE_TEXT_COLOR, RECEIVED_BUBBLE_TEXT_COLOR,
            TOP_BAR_IMAGE, MAIN_BACKGROUND_IMAGE, INPUT_BAR_IMAGE,
            TOP_BAR_CROP_RECT, MAIN_BG_CROP_RECT, INPUT_BAR_CROP_RECT,
            TOP_BAR_BG_MODE, MAIN_BG_MODE, INPUT_BAR_BG_MODE,
            RECENT_COLOR,
            FONT_FAMILY_TEXTO, UI_SCALE, ALWAYS_EXPAND_SEARCH_BAR, USE_NEW_UI,
            APP_THEME, GLASS_THEME, CARD_CORNER_RADIUS, MAIN_BG_GRADIENT_START, MAIN_BG_GRADIENT_END,
            TOP_BAR_OUTLINE, TOP_BAR_OUTLINE_COLOR, SEARCH_BAR_OUTLINE, SEARCH_BAR_OUTLINE_COLOR,
            BIG_CONTACTS_OUTLINE, BIG_CONTACTS_OUTLINE_COLOR, SMALL_CONTACTS_OUTLINE, SMALL_CONTACTS_OUTLINE_COLOR,
            SENT_BUBBLES_OUTLINE, SENT_BUBBLES_OUTLINE_COLOR, RECEIVED_BUBBLES_OUTLINE, RECEIVED_BUBBLES_OUTLINE_COLOR,
            PRESET_1_NAME, PRESET_2_NAME, PRESET_3_NAME // Include names in preset
        )
        themeKeys.forEach { key ->
            val value = prefs.all[key]
            if (value != null) {
                when (value) {
                    is Int -> editor.putInt(prefix + key, value)
                    is String -> editor.putString(prefix + key, value)
                    is Boolean -> editor.putBoolean(prefix + key, value)
                    is Float -> editor.putFloat(prefix + key, value)
                    is Long -> editor.putLong(prefix + key, value)
                }
            }
        }
        editor.apply()
    }

    fun loadPreset(id: Int) {
        val prefix = "preset_${id}_"
        val editor = prefs.edit()
        val themeKeys = listOf(
            TOP_BAR_COLOR, TOP_BAR_TEXT_COLOR, MAIN_TEXT_COLOR, MAIN_BACKGROUND_COLOR,
            INPUT_BAR_BACKGROUND_COLOR, INPUT_BAR_TEXT_COLOR, SENT_BUBBLE_COLOR,
            RECEIVED_BUBBLE_COLOR, SENT_BUBBLE_TEXT_COLOR, RECEIVED_BUBBLE_TEXT_COLOR,
            TOP_BAR_IMAGE, MAIN_BACKGROUND_IMAGE, INPUT_BAR_IMAGE,
            TOP_BAR_CROP_RECT, MAIN_BG_CROP_RECT, INPUT_BAR_CROP_RECT,
            TOP_BAR_BG_MODE, MAIN_BG_MODE, INPUT_BAR_BG_MODE,
            RECENT_COLOR,
            FONT_FAMILY_TEXTO, UI_SCALE, ALWAYS_EXPAND_SEARCH_BAR, USE_NEW_UI,
            APP_THEME, GLASS_THEME, CARD_CORNER_RADIUS, MAIN_BG_GRADIENT_START, MAIN_BG_GRADIENT_END,
            TOP_BAR_OUTLINE, TOP_BAR_OUTLINE_COLOR, SEARCH_BAR_OUTLINE, SEARCH_BAR_OUTLINE_COLOR,
            BIG_CONTACTS_OUTLINE, BIG_CONTACTS_OUTLINE_COLOR, SMALL_CONTACTS_OUTLINE, SMALL_CONTACTS_OUTLINE_COLOR,
            SENT_BUBBLES_OUTLINE, SENT_BUBBLES_OUTLINE_COLOR, RECEIVED_BUBBLES_OUTLINE, RECEIVED_BUBBLES_OUTLINE_COLOR,
            PRESET_1_NAME, PRESET_2_NAME, PRESET_3_NAME
        )
        themeKeys.forEach { key ->
            val value = prefs.all[prefix + key]
            if (value != null) {
                when (value) {
                    is Int -> editor.putInt(key, value)
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Long -> editor.putLong(key, value)
                }
            }
        }
        editor.apply()
    }
}
