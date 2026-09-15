package com.texto.sms.helpers

import com.texto.sms.models.Events
import org.greenrobot.eventbus.EventBus
import org.joda.time.DateTime
import org.joda.time.DateTimeZone
import kotlin.math.abs
import kotlin.random.Random

const val THREAD_ID = "thread_id"
const val THREAD_TITLE = "thread_title"
const val THREAD_TEXT = "thread_text"
const val THREAD_NUMBER = "thread_number"
const val THREAD_ATTACHMENT_URI = "thread_attachment_uri"
const val THREAD_ATTACHMENT_URIS = "thread_attachment_uris"
const val SEARCHED_MESSAGE_ID = "searched_message_id"
const val USE_SIM_ID_PREFIX = "use_sim_id_"
/**
 * Android keys a user's per-channel sound and importance settings by this string, so changing
 * it on a shipped app silently creates a second, default channel and strands whatever they
 * had configured. It carried the old brand name until now, and renaming it was free only
 * because nothing has been released yet. After the first Play build this string is frozen.
 */
const val NOTIFICATION_CHANNEL_ID = "texto_messages"

// The nine keys below are commons' own pref names, kept as-is so an existing install's stored
// values under them are still read after Config stopped inheriting BaseConfig.
const val BLOCK_UNKNOWN_NUMBERS = "block_unknown_numbers"
const val APP_RUN_COUNT = "app_run_count"
const val APP_ID = "app_id"
const val APP_SIDELOADING_STATUS = "app_sideloading_status"
const val HAD_THANK_YOU_INSTALLED = "had_thank_you_installed"
const val FONT_SIZE = "font_size"
const val DATE_FORMAT = "date_format"
const val TEXT_COLOR_BASE = "text_color"
const val BACKGROUND_COLOR_BASE = "background_color"
const val PRIMARY_COLOR_BASE = "primary_color_2"

/** The four rungs [Config.fontSize] and [com.texto.sms.extensions.getTextSize] read. */
const val FONT_SIZE_SMALL = 0
const val FONT_SIZE_MEDIUM = 1
const val FONT_SIZE_LARGE = 2
const val FONT_SIZE_EXTRA_LARGE = 3

const val SHOW_CHARACTER_COUNTER = "show_character_counter"
const val USE_SIMPLE_CHARACTERS = "use_simple_characters"
const val SEND_ON_ENTER = "send_on_enter"
const val LOCK_SCREEN_VISIBILITY = "lock_screen_visibility"
const val ENABLE_DELIVERY_REPORTS = "enable_delivery_reports"
const val SEND_LONG_MESSAGE_MMS = "send_long_message_mms"
const val SEND_GROUP_MESSAGE_MMS = "send_group_message_mms"
const val MMS_FILE_SIZE_LIMIT = "mms_file_size_limit"
const val PINNED_CONVERSATIONS = "pinned_conversations"
const val EXPORT_SMS = "export_sms"
const val EXPORT_MMS = "export_mms"
const val JSON_FILE_EXTENSION = ".json"
const val JSON_MIME_TYPE = "application/json"
const val XML_MIME_TYPE = "text/xml"
const val TXT_MIME_TYPE = "text/plain"
const val IMPORT_SMS = "import_sms"
const val IMPORT_MMS = "import_mms"
const val WAS_DB_CLEARED = "was_db_cleared_4"
const val EXTRA_VCARD_URI = "vcard"
const val SCHEDULED_MESSAGE_ID = "scheduled_message_id"
const val SOFT_KEYBOARD_HEIGHT = "soft_keyboard_height"
const val IS_MMS = "is_mms"
const val MESSAGE_ID = "message_id"
const val USE_RECYCLE_BIN = "use_recycle_bin"

/** How many distinct chats the recycle bin keeps before the oldest is dropped. */
const val RECYCLE_BIN_THREAD_LIMIT = 20

/**
 * Upper bound when pulling a whole thread out of telephony to bin it. The normal read path
 * uses [MESSAGES_LIMIT] (a screenful), which would silently leave older messages unbinned
 * and the chat still visible, so deleting a chat reads far deeper.
 */
const val RECYCLE_BIN_MESSAGE_FETCH_LIMIT = 10000
const val LAST_RECYCLE_BIN_CHECK = "last_recycle_bin_check"
const val IS_RECYCLE_BIN = "is_recycle_bin"
const val IS_ARCHIVE_AVAILABLE = "is_archive_available"
const val CUSTOM_NOTIFICATIONS = "custom_notifications"
const val IS_LAUNCHED_FROM_SHORTCUT = "is_launched_from_shortcut"
const val IS_FROM_NOTIFICATION = "is_from_notification"
const val MESSAGE_CODE = "message_code"
const val KEEP_CONVERSATIONS_ARCHIVED = "keep_conversations_archived"
const val USE_NEW_UI = "use_new_ui"
const val CONVERSATION_ORDER = "conversation_order"
const val CONTACT_SORTING_MODE = "contact_sorting_mode"
const val RECENT_COLOR = "recent_color"
const val UI_SCALE = "ui_scale"
/** Language: 0 follows the phone, 1 Persian, 2 English. See TextoLocale. */
const val APP_LANGUAGE = "app_language"
/** Renamed off the old brand pre-release. Frozen once shipped, like the channel id. */
const val FONT_FAMILY = "font_family_texto"
const val FONT_TYPE = "font_type"
/** The same key as [FONT_FAMILY], under the name the rest of the app calls it by. */
const val FONT_FAMILY_TEXTO = "font_family_texto"
const val APP_THEME = "app_theme"
/** One-shot marker for the move onto the Nocturne design. See App.onCreate. */
const val NOCTURNE_REFRESH_APPLIED = "nocturne_refresh_applied"
/** One-shot marker for the move onto the Neon design that replaced it. See App.onCreate. */
const val NEON_REFRESH_APPLIED = "neon_refresh_applied"
const val GLASS_RECALIBRATED = "glass_recalibrated"
const val NEON_LIGHT_DEFAULT_APPLIED = "neon_light_default_applied"
const val CLASSIC_DEFAULT_APPLIED = "classic_default_applied"
const val NEON_LIGHT_RESTORED = "neon_light_restored"
const val BUBBLE_SIDES_SWAPPED = "bubble_sides_swapped"
const val AURORA_RETIRED = "aurora_retired"
const val NOCTURNE_ACCENT_CONTRAST_FIXED = "nocturne_accent_contrast_fixed"

/**
 * Floor of the glass slider. Below this the bars stop reading as surfaces at all, so the
 * bottom of the old 20-100 travel was dead space that squeezed the usable part of the
 * control into its top third.
 */
const val GLASS_OPACITY_MIN = 40

/** Nav tab glyph size. The mockup draws 18; the capsule here is about a third bigger. */
const val NAV_ICON_DP = 23
const val MAIN_BG_GRADIENT_START = "main_bg_gradient_start"
const val MAIN_BG_GRADIENT_END = "main_bg_gradient_end"
const val CARD_CORNER_RADIUS = "card_corner_radius"
const val CUSTOM_FILTERS = "custom_filters"

const val RECENT_COLOURS = "recent_colours"

/** How many recent colours the picker keeps. Five is the row it draws. */
const val RECENT_COLOURS_KEPT = 5

const val RECENT_EMOJI = "recent_emoji"

/** Three rows of the emoji grid, which is as far as a "recent" list stays useful. */
const val RECENT_EMOJI_KEPT = 24

/**
 * The keyboard's own height, last time one was open, so the emoji panel can be exactly as
 * tall as the thing it replaces. Remembered across runs: the first emoji tap in a session
 * would otherwise guess, and the composer would jump when the guess was wrong.
 */
const val LAST_KEYBOARD_HEIGHT = "last_keyboard_height"
/** Per-conversation colour overrides, as one JSON object keyed by thread id. */
const val THREAD_APPEARANCES = "thread_appearances"
const val ACTIVE_FILTER_ID = "active_filter_id"
const val SHOW_CONTACTS_ONLY_FILTER = "show_contacts_only_filter"
const val SHOW_ADS_FILTER = "show_ads_filter"
const val ADS_FILTER = "ads_filter"
const val DEFAULT_FILTER_ID = "default_filter_id"
const val GLASS_THEME = "glass_theme"
const val GLASS_OPACITY = "glass_opacity"
const val SIM_COLOR_PREFIX = "sim_color_"

/**
 * The digit badge riding the SIM disc's upper corner, as the design draws it: a 15px dot on
 * a 38px control. Rounded up a hair so the numeral inside still has room at the app's
 * smallest UI scale.
 */
const val SIM_BADGE_SIZE_DP = 16

/** The design's small round composer controls -- the attachment clip and the SIM picker. */
const val COMPOSER_DISC_DP = 38

// Customization constants
const val TOP_BAR_COLOR = "top_bar_color"
const val TOP_BAR_TEXT_COLOR = "top_bar_text_color"
const val MAIN_TEXT_COLOR = "main_text_color"
const val MAIN_BACKGROUND_COLOR = "main_background_color"
const val INPUT_BAR_BACKGROUND_COLOR = "input_bar_background_color"
const val INPUT_BAR_TEXT_COLOR = "input_bar_text_color"
const val SENT_BUBBLE_COLOR = "sent_bubble_color"
const val RECEIVED_BUBBLE_COLOR = "received_bubble_color"
const val RECEIVED_BUBBLE_COLOR_SET = "received_bubble_color_set"
const val ACCENT_INK_COLOR = "accent_ink_color"
const val SENT_BUBBLE_TEXT_COLOR = "sent_bubble_text_color"
const val RECEIVED_BUBBLE_TEXT_COLOR = "received_bubble_text_color"

const val TOP_BAR_IMAGE = "top_bar_image_v2"
const val MAIN_BACKGROUND_IMAGE = "main_background_image_v2"
const val INPUT_BAR_IMAGE = "input_bar_image_v2"
const val TOP_BAR_CROP_RECT = "top_bar_crop_rect"
const val MAIN_BG_CROP_RECT = "main_bg_crop_rect"
const val INPUT_BAR_CROP_RECT = "input_bar_crop_rect"
const val CROP_TARGET = "crop_target"
const val CROP_TARGET_TOP_BAR = 1
const val CROP_TARGET_SEARCH_BAR = 2
const val CROP_TARGET_BACKGROUND = 3
const val TOP_BAR_BG_MODE = "top_bar_bg_mode"
const val MAIN_BG_MODE = "main_bg_mode"
const val INPUT_BAR_BG_MODE = "input_bar_bg_mode"

const val BG_MODE_COLOR = 0
const val BG_MODE_IMAGE = 1
const val BG_MODE_GRADIENT = 2

// A plain two-stop vertical gradient between mainBgGradientStart and mainBgGradientEnd.
// Distinct from BG_MODE_GRADIENT, which ignores the end stop and paints the drifting
// aurora halo field over the start colour instead.
const val BG_MODE_LINEAR = 3

/**
 * Accent gradient the skin runs through every emphasis surface -- sent bubbles, the unread
 * badge, the active filter chip, the FAB and the active nav tab -- so they read as one system.
 */
const val ACCENT_GRADIENT_START = "accent_gradient_start"
const val ACCENT_GRADIENT_END = "accent_gradient_end"
const val ACCENT_GRADIENT_MID = "accent_gradient_mid"
const val ACCENT_HUE_SHIFT = "accent_hue_shift"
/** Where the accent gradient's middle stop sits, matching the design's `--grad` 55%. */
const val ACCENT_GRADIENT_MID_POSITION = 0.55f

/** Third aurora hue, used only by the background halos. */
const val AURORA_ACCENT_COLOR = "aurora_accent_color"

/**
 * The three halo colours painted over the main background.
 *
 * Separate from the accent gradient on purpose. They used to BE the accent trio, which meant
 * a skin whose accent is a bright cyan-to-violet washed its own near-black ground in bright
 * teal and purple -- nothing like the design, whose halos are far deeper colours than the
 * accent they sit under. 0 in any slot falls back to the accent trio, so older themes keep
 * the look they had.
 */
const val AURORA_HALO_ONE = "aurora_halo_one"
const val AURORA_HALO_TWO = "aurora_halo_two"
const val AURORA_HALO_THREE = "aurora_halo_three"

/** Multiplier on every halo's alpha, so a light skin can keep the hues but calm them down. */
const val AURORA_HALO_OPACITY = "aurora_halo_opacity"

/** Slow drifting halos behind the app; off leaves them painted but still. */
const val AURORA_ANIMATE = "aurora_animate"

const val ALWAYS_EXPAND_SEARCH_BAR = "always_expand_search_bar"

const val PRESET_1_NAME = "preset_1_name"
const val PRESET_2_NAME = "preset_2_name"
const val PRESET_3_NAME = "preset_3_name"

const val TOP_BAR_OUTLINE = "top_bar_outline"
const val TOP_BAR_OUTLINE_COLOR = "top_bar_outline_color"
const val TOP_BAR_OUTLINE_THICKNESS = "top_bar_outline_thickness"

const val SEARCH_BAR_OUTLINE = "search_bar_outline"
const val SEARCH_BAR_OUTLINE_COLOR = "search_bar_outline_color"
const val SEARCH_BAR_OUTLINE_THICKNESS = "search_bar_outline_thickness"

const val BIG_CONTACTS_OUTLINE = "big_contacts_outline"
const val BIG_CONTACTS_OUTLINE_COLOR = "big_contacts_outline_color"
const val BIG_CONTACTS_OUTLINE_THICKNESS = "big_contacts_outline_thickness"

const val SMALL_CONTACTS_OUTLINE = "small_contacts_outline"
const val SMALL_CONTACTS_OUTLINE_COLOR = "small_contacts_outline_color"
const val SMALL_CONTACTS_OUTLINE_THICKNESS = "small_contacts_outline_thickness"

const val SENT_BUBBLES_OUTLINE = "sent_bubbles_outline"
const val SENT_BUBBLES_OUTLINE_COLOR = "sent_bubbles_outline_color"
const val SENT_BUBBLES_OUTLINE_THICKNESS = "sent_bubbles_outline_thickness"

const val RECEIVED_BUBBLES_OUTLINE = "received_bubbles_outline"
const val RECEIVED_BUBBLES_OUTLINE_COLOR = "received_bubbles_outline_color"
const val RECEIVED_BUBBLES_OUTLINE_THICKNESS = "received_bubbles_outline_thickness"

private const val PATH = "com.texto.sms.action."
const val MARK_AS_READ = PATH + "mark_as_read"
const val REPLY = PATH + "reply"
const val COPY_CODE = PATH + "copy_code"

// view types for the thread list view
const val THREAD_DATE_TIME = 1
const val THREAD_RECEIVED_MESSAGE = 2
const val THREAD_SENT_MESSAGE = 3
const val THREAD_SENT_MESSAGE_ERROR = 4
const val THREAD_SENT_MESSAGE_SENT = 5
const val THREAD_SENT_MESSAGE_SENDING = 6
const val THREAD_TYPE_BITS = 3
const val THREAD_KEY_BITS = Long.SIZE_BITS - THREAD_TYPE_BITS
const val THREAD_TYPE_SHIFT = THREAD_KEY_BITS
const val THREAD_KEY_MASK = (1L shl THREAD_KEY_BITS) - 1

// view types for attachment list
const val ATTACHMENT_DOCUMENT = 7
const val ATTACHMENT_MEDIA = 8
const val ATTACHMENT_VCARD = 9

// lock screen visibility constants
const val LOCK_SCREEN_SENDER_MESSAGE = 1
const val LOCK_SCREEN_SENDER = 2
const val LOCK_SCREEN_NOTHING = 3

const val FILE_SIZE_NONE = -1L
const val FILE_SIZE_100_KB = 102_400L
const val FILE_SIZE_200_KB = 204_800L
const val FILE_SIZE_300_KB = 307_200L
const val FILE_SIZE_600_KB = 614_400L
const val FILE_SIZE_1_MB = 1_048_576L
const val FILE_SIZE_2_MB = 2_097_152L
const val FILE_SIZE_50_MB = 52_428_800L
const val FILE_SIZE_100_MB = 104_857_600L

const val MESSAGES_LIMIT = 50
const val MAX_MESSAGE_LENGTH = 5000

// intent launch request codes
const val PICK_PHOTO_INTENT = 42
const val PICK_VIDEO_INTENT = 49
const val PICK_SAVE_FILE_INTENT = 43
const val CAPTURE_PHOTO_INTENT = 44
const val CAPTURE_VIDEO_INTENT = 45
const val CAPTURE_AUDIO_INTENT = 46
const val PICK_AUDIO_INTENT = 51
const val PICK_DOCUMENT_INTENT = 47
const val PICK_CONTACT_INTENT = 48

/**
 * Opens NewConversationActivity to hand one contact back rather than start a thread with it.
 * The attach-contact option used to open the system picker -- an unthemed screen, in English
 * whatever the app's language -- and then drop what it returned on the floor.
 */
const val PICK_CONTACT_MODE = "pick_contact_mode"
const val PICK_CONTACT_RESULT_NAME = "pick_contact_result_name"
const val PICK_CONTACT_RESULT_NUMBERS = "pick_contact_result_numbers"
const val PICK_SAVE_DIR_INTENT = 50
const val PICK_TOP_BAR_IMAGE_INTENT = 2001
const val PICK_MAIN_BG_IMAGE_INTENT = 2002
const val PICK_INPUT_BAR_IMAGE_INTENT = 2003
const val CROP_RESULT_INTENT = 2004

/**
 * The red every irreversible row wears: the settings reset, delete in the overflow menus,
 * delete in a capsule sheet. One value so the three cannot drift apart.
 */
val DESTRUCTIVE_INK: Int = android.graphics.Color.parseColor("#F54651")


fun refreshMessages() {
    EventBus.getDefault().post(Events.RefreshMessages())
}

fun refreshConversations(isManualReorder: Boolean = false) {
    EventBus.getDefault().post(Events.RefreshConversations(isManualReorder))
}

/** Not to be used with real messages persisted in the telephony db. This is for internal use only (e.g. scheduled messages, notification ids etc). */
fun generateRandomId(length: Int = 9): Long {
    val millis = DateTime.now(DateTimeZone.UTC).millis
    val random = abs(Random(millis).nextLong())
    return random.toString().takeLast(length).toLong()
}

fun generateStableId(type: Int, key: Long): Long {
    require(type in 0 until (1 shl THREAD_TYPE_BITS))
    return (type.toLong() shl THREAD_TYPE_SHIFT) or (key and THREAD_KEY_MASK)
}

/** One-shot: settings sets it on the way out so the conversation list opens its live editor. */
const val START_APPEARANCE_EDITOR = "start_appearance_editor"
