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
        /** Opaque enough that text scrolling under a bar stays out of the way of its title. */
        const val DEFAULT_GLASS_OPACITY = 88

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

    fun resetColors() {
        prefs.edit().remove(TOP_BAR_COLOR).remove(TOP_BAR_TEXT_COLOR).remove(MAIN_BACKGROUND_COLOR).remove(MAIN_TEXT_COLOR).remove(INPUT_BAR_BACKGROUND_COLOR).remove(INPUT_BAR_TEXT_COLOR).remove(SENT_BUBBLE_COLOR).remove(SENT_BUBBLE_TEXT_COLOR).remove(RECEIVED_BUBBLE_COLOR).remove(RECEIVED_BUBBLE_TEXT_COLOR).remove(RECENT_COLOR)
            .remove(TOP_BAR_IMAGE).remove(MAIN_BACKGROUND_IMAGE).remove(INPUT_BAR_IMAGE)
            .remove(TOP_BAR_BG_MODE).remove(MAIN_BG_MODE).remove(INPUT_BAR_BG_MODE)
            .remove(APP_THEME).remove(MAIN_BG_GRADIENT_START).remove(MAIN_BG_GRADIENT_END)
            .remove(ACCENT_GRADIENT_START).remove(ACCENT_GRADIENT_END)
            .remove(AURORA_ACCENT_COLOR).remove(AURORA_ANIMATE)
            .remove(CARD_CORNER_RADIUS).apply()
    }

    var appTheme: Int
        get() = prefs.getInt(APP_THEME, AppThemes.AURORA)
        set(appTheme) = prefs.edit().putInt(APP_THEME, appTheme).apply()

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
    var accentGradientStart: Int
        get() = prefs.getInt(ACCENT_GRADIENT_START, AURORA_CYAN)
        set(color) = prefs.edit().putInt(ACCENT_GRADIENT_START, color).apply()

    var accentGradientEnd: Int
        get() = prefs.getInt(ACCENT_GRADIENT_END, AURORA_BLUE)
        set(color) = prefs.edit().putInt(ACCENT_GRADIENT_END, color).apply()

    /** Third halo hue behind the app, alongside the two accent-gradient stops. */
    var auroraAccentColor: Int
        get() = prefs.getInt(AURORA_ACCENT_COLOR, AURORA_MAGENTA)
        set(color) = prefs.edit().putInt(AURORA_ACCENT_COLOR, color).apply()

    /** Whether the background halos drift. Off still paints them, just frozen. */
    var auroraAnimate: Boolean
        get() = prefs.getBoolean(AURORA_ANIMATE, true)
        set(animate) = prefs.edit().putBoolean(AURORA_ANIMATE, animate).apply()

    /** Very large values render conversation cards as full pills. */
    var cardCornerRadiusDp: Int
        get() = prefs.getInt(CARD_CORNER_RADIUS, 500)
        set(radius) = prefs.edit().putInt(CARD_CORNER_RADIUS, radius).apply()

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

    /** How opaque the frosted surfaces are, as a percentage. Lower reads as more see-through. */
    var glassOpacity: Int
        get() = prefs.getInt(GLASS_OPACITY, DEFAULT_GLASS_OPACITY).coerceIn(20, 100)
        set(glassOpacity) = prefs.edit().putInt(GLASS_OPACITY, glassOpacity).apply()

    var fontFamilyNova: Int
        // Vazirmatn is the app's default face -- it ships with the app (SIL OFL) and is the
        // only bundled family with a real bold cut. NovaFonts falls back to the system font
        // on its own if the asset is ever missing, so this is safe even without the file.
        get() = prefs.getInt(FONT_FAMILY_NOVA, NovaFonts.FONT_VAZIRMATN)
        set(fontFamilyNova) = prefs.edit().putInt(FONT_FAMILY_NOVA, fontFamilyNova).apply()

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
            FONT_FAMILY_NOVA, UI_SCALE, ALWAYS_EXPAND_SEARCH_BAR, USE_NEW_UI,
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
            FONT_FAMILY_NOVA, UI_SCALE, ALWAYS_EXPAND_SEARCH_BAR, USE_NEW_UI,
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
