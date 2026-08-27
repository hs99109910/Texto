package com.texto.sms.helpers

import android.graphics.Color

/**
 * A theme is a named bundle of the colour settings the app already exposes, plus the shape
 * and gradient choices that cannot be expressed as a single colour: the card corner radius,
 * whether the background is a flat fill or an aurora halo field, and the accent gradient
 * every emphasis surface is painted with.
 *
 * Selecting one writes its values into [Config], so every existing customisation screen
 * keeps working and the user can still tweak any individual colour afterwards.
 */
data class AppTheme(
    val id: Int,
    val label: String,
    val topBarColor: Int,
    val topBarTextColor: Int,
    val mainTextColor: Int,
    val mainBackgroundColor: Int,
    /** Non-null turns the main background into the drifting aurora halo field. */
    val backgroundGradient: Pair<Int, Int>?,
    val cardColor: Int,
    val inputBarTextColor: Int,
    /** Start/end of the emphasis gradient shared by bubbles, badges, chips and the FAB. */
    val accentGradient: Pair<Int, Int>,
    /** Third halo hue behind the app, alongside the two accent stops. */
    val auroraAccent: Int,
    val sentBubbleTextColor: Int,
    val receivedBubbleColor: Int,
    val receivedBubbleTextColor: Int,
    /** In dp. A very large value renders the cards as full pills. */
    val cardCornerRadiusDp: Int,
    val glass: Boolean,
    /**
     * Paints [backgroundGradient] as a plain two-stop vertical fade instead of the drifting
     * halo field. Themes drawn from a flat mockup want the literal gradient they specify.
     */
    val linearBackground: Boolean = false,
)

object AppThemes {

    const val CLASSIC = 0
    const val AURORA = 1
    const val AURORA_LIGHT = 2
    const val NOCTURNE = 3

    /**
     * The skin's signature hues, shared by every theme so the accent gradient stays
     * recognisable whichever ground it sits on.
     */
    private val CYAN = Config.AURORA_CYAN
    private val BLUE = Config.AURORA_BLUE
    private val MAGENTA = Config.AURORA_MAGENTA

    /**
     * Dark navy ground with drifting cyan/blue/magenta halos -- the design the skin is
     * drawn from. `--background oklch(.16 .035 275)`, `--card oklch(.22 .04 275)`.
     */
    private val aurora = AppTheme(
        id = AURORA,
        label = "آورورا",
        topBarColor = Color.parseColor("#13162B"),
        topBarTextColor = Color.parseColor("#F2F5FC"),
        mainTextColor = Color.parseColor("#F2F5FC"),
        mainBackgroundColor = Color.parseColor("#090C1C"),
        backgroundGradient = Color.parseColor("#090C1C") to Color.parseColor("#15192D"),
        cardColor = Color.parseColor("#15192D"),
        inputBarTextColor = Color.parseColor("#F2F5FC"),
        accentGradient = CYAN to BLUE,
        auroraAccent = MAGENTA,
        sentBubbleTextColor = Color.parseColor("#090C1C"),
        receivedBubbleColor = Color.parseColor("#1B2038"),
        receivedBubbleTextColor = Color.parseColor("#F2F5FC"),
        cardCornerRadiusDp = 32,
        glass = true
    )

    /**
     * Aurora's shape and accent on a light ground: the same halos, 32dp corners and
     * cyan-to-blue gradient, but a pale blue-lavender field with dark text instead of navy.
     */
    private val auroraLight = AppTheme(
        id = AURORA_LIGHT,
        label = "آورورا روشن",
        topBarColor = Color.parseColor("#F4F6FC"),
        topBarTextColor = Color.parseColor("#10162B"),
        mainTextColor = Color.parseColor("#10162B"),
        mainBackgroundColor = Color.parseColor("#EEF1F8"),
        backgroundGradient = Color.parseColor("#F5F7FD") to Color.parseColor("#DDE4F6"),
        cardColor = Color.parseColor("#FFFFFF"),
        inputBarTextColor = Color.parseColor("#10162B"),
        accentGradient = CYAN to BLUE,
        auroraAccent = MAGENTA,
        sentBubbleTextColor = Color.parseColor("#062033"),
        receivedBubbleColor = Color.parseColor("#E3E9F7"),
        receivedBubbleTextColor = Color.parseColor("#10162B"),
        cardCornerRadiusDp = 32,
        glass = true
    )

    /**
     * The neutral option: no halos and a plain white ground, but the same corner radius and
     * accent gradient as the rest so it reads as the same app rather than a different one.
     */
    private val classic = AppTheme(
        id = CLASSIC,
        label = "کلاسیک",
        topBarColor = Color.parseColor("#101216"),
        topBarTextColor = Color.WHITE,
        mainTextColor = Color.parseColor("#101216"),
        mainBackgroundColor = Color.WHITE,
        backgroundGradient = null,
        cardColor = Config.DEFAULT_CARD_GREY,
        inputBarTextColor = Color.WHITE,
        accentGradient = CYAN to BLUE,
        auroraAccent = MAGENTA,
        sentBubbleTextColor = Color.parseColor("#062033"),
        receivedBubbleColor = Color.parseColor("#ECEFF4"),
        receivedBubbleTextColor = Color.parseColor("#101216"),
        cardCornerRadiusDp = 32,
        glass = true
    )

    /**
     * The Claude Design "Texto RTL" mockup, taken at its own values rather than reshaded into
     * the cyan skin: a navy ground fading to deep purple, slate cards, a saturated blue sent
     * bubble and a blurple accent. Its background is the literal gradient the mockup draws,
     * so it opts out of the halo field the two Aurora themes use.
     */
    private val nocturne = AppTheme(
        id = NOCTURNE,
        label = "نوکترن",
        topBarColor = Color.parseColor("#171C2E"),
        topBarTextColor = Color.parseColor("#F2F3F8"),
        mainTextColor = Color.parseColor("#F2F3F8"),
        mainBackgroundColor = Color.parseColor("#0B1026"),
        backgroundGradient = Color.parseColor("#0B1026") to Color.parseColor("#3D2260"),
        cardColor = Color.parseColor("#1B2136"),
        inputBarTextColor = Color.parseColor("#F2F3F8"),
        // The mockup's own sent-bubble gradient, light stop first.
        accentGradient = Color.parseColor("#4A80FF") to Color.parseColor("#2F6BFF"),
        auroraAccent = Color.parseColor("#9184D9"),
        // Unlike the cyan themes, this accent is a deep blue, so its text is white.
        sentBubbleTextColor = Color.WHITE,
        receivedBubbleColor = Color.parseColor("#232838"),
        receivedBubbleTextColor = Color.parseColor("#F2F3F8"),
        cardCornerRadiusDp = 24,
        glass = true,
        linearBackground = true
    )

    val all = listOf(classic, aurora, auroraLight, nocturne)

    fun byId(id: Int) = all.firstOrNull { it.id == id } ?: aurora

    /** Overwrites the colour settings with [theme]'s values. */
    fun apply(config: Config, theme: AppTheme) {
        config.appTheme = theme.id
        config.topBarColor = theme.topBarColor
        config.topBarTextColor = theme.topBarTextColor
        config.mainTextColor = theme.mainTextColor
        config.mainBackgroundColor = theme.mainBackgroundColor
        config.recentColor = theme.cardColor
        // No separate write for inputBarBackgroundColor: it's an alias for topBarColor now
        // that the top and bottom bars share one background setting.
        config.inputBarTextColor = theme.inputBarTextColor

        config.accentGradientStart = theme.accentGradient.first
        config.accentGradientEnd = theme.accentGradient.second
        config.auroraAccentColor = theme.auroraAccent

        // The sent bubble is painted with the accent gradient, so its own colour only needs
        // to be a sane flat fallback for the non-glass path.
        config.sentBubbleColor = theme.accentGradient.second
        config.sentBubbleTextColor = theme.sentBubbleTextColor
        config.receivedBubbleColor = theme.receivedBubbleColor
        config.receivedBubbleTextColor = theme.receivedBubbleTextColor
        config.cardCornerRadiusDp = theme.cardCornerRadiusDp
        config.glassTheme = theme.glass

        // A picked background image would sit on top of the theme, so clear it.
        config.mainBackgroundImage = ""
        config.topBarImage = ""
        config.topBarBgMode = BG_MODE_COLOR

        if (theme.backgroundGradient != null) {
            config.mainBgMode = if (theme.linearBackground) BG_MODE_LINEAR else BG_MODE_GRADIENT
            config.mainBgGradientStart = theme.backgroundGradient.first
            config.mainBgGradientEnd = theme.backgroundGradient.second
        } else {
            config.mainBgMode = BG_MODE_COLOR
        }
    }
}
