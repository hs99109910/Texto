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
    /**
     * Optional middle stop of that gradient, at [ACCENT_GRADIENT_MID_POSITION]. Null means a
     * plain two-stop blend. The design gives `--grad` three colours, and the blend between
     * its ends does not pass anywhere near the one in the middle.
     */
    val accentGradientMid: Int? = null,
    /** Third halo hue behind the app, alongside the two accent stops. */
    val auroraAccent: Int,
    /**
     * The three background halos. Null keeps the old behaviour of reusing the accent trio,
     * which is right for the skins designed that way and wrong for any theme whose accent is
     * far brighter than its ground -- there it washed a near-black background in bright
     * accent colour instead of the deep glow the design draws.
     */
    val haloColors: Triple<Int, Int, Int>? = null,
    /** Multiplier on halo alpha; the design dims them to 40% on its light variant. */
    val haloOpacity: Float = 1f,
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
    const val NOCTURNE_LIGHT = 4
    const val NEON = 5
    const val NEON_LIGHT = 6

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
        // The mockup draws every card, sheet and thread row on a 22px radius.
        cardCornerRadiusDp = 22,
        glass = true,
        linearBackground = true
    )

    /**
     * Nocturne by day. The design ships a light variant of the same skin as its `.lt` token
     * block, and these are those values rather than the dark theme inverted by hand:
     * `--bg1 #F6F7FC`, `--bg2 #E6E1F7`, white cards and bars, `--txt #14172B`, and an
     * accent that deepens from #9184D9 to #5B4FC4 so it still carries on a pale ground.
     *
     * The sent bubble keeps the dark theme's blue: the design does not move `--sent`
     * between the two, and it is the one surface that stays saturated in both.
     */
    private val nocturneLight = AppTheme(
        id = NOCTURNE_LIGHT,
        label = "نوکترن روشن",
        topBarColor = Color.WHITE,
        topBarTextColor = Color.parseColor("#14172B"),
        mainTextColor = Color.parseColor("#14172B"),
        mainBackgroundColor = Color.parseColor("#F6F7FC"),
        backgroundGradient = Color.parseColor("#F6F7FC") to Color.parseColor("#E6E1F7"),
        cardColor = Color.WHITE,
        inputBarTextColor = Color.parseColor("#14172B"),
        accentGradient = Color.parseColor("#4A80FF") to Color.parseColor("#2F6BFF"),
        auroraAccent = Color.parseColor("#5B4FC4"),
        sentBubbleTextColor = Color.WHITE,
        receivedBubbleColor = Color.WHITE,
        receivedBubbleTextColor = Color.parseColor("#14172B"),
        cardCornerRadiusDp = 22,
        glass = true,
        linearBackground = true
    )

    /**
     * The Claude Design "Texto" reimport (2026-08-31, repo `persian-chat-hub`): a near-black
     * ground with three drifting halos -- teal, sky-blue and violet -- behind glass capsules,
     * taken straight from that project's own OKLCH tokens rather than reshaded into either
     * existing skin. `--bg oklch(.155 .014 264)`, `--primary oklch(.78 .12 196)`,
     * `--primary-alt oklch(.68 .17 298)`, the sent bubble uses `--grad` with dark text
     * (`--primary-fg oklch(.16 .03 240)`) because the gradient itself is light enough to need it.
     */
    private val neon = AppTheme(
        id = NEON,
        label = "نئون",
        topBarColor = Color.parseColor("#171B22"),
        topBarTextColor = Color.parseColor("#F4F5F8"),
        mainTextColor = Color.parseColor("#F4F5F8"),
        mainBackgroundColor = Color.parseColor("#090C12"),
        backgroundGradient = Color.parseColor("#090C12") to Color.parseColor("#10141B"),
        cardColor = Color.parseColor("#171B22"),
        inputBarTextColor = Color.parseColor("#F4F5F8"),
        accentGradient = Color.parseColor("#3BCFD0") to Color.parseColor("#A67DF2"),
        accentGradientMid = Color.parseColor("#55ADFF"),
        auroraAccent = Color.parseColor("#55ADFF"),
        // The design's three background radials, which are far deeper than the accent that
        // sits over them: oklch(.6 .12 195), oklch(.45 .11 250), oklch(.48 .16 305).
        haloColors = Triple(
            Color.parseColor("#009696"),
            Color.parseColor("#1A588F"),
            Color.parseColor("#733EA4")
        ),
        sentBubbleTextColor = Color.parseColor("#020F19"),
        receivedBubbleColor = Color.parseColor("#1F242E"),
        receivedBubbleTextColor = Color.parseColor("#F4F5F8"),
        cardCornerRadiusDp = 22,
        glass = true
    )

    /**
     * Neon by day: the same import's `light ? LIGHT_VARS` block. `--bg oklch(.928 .008 250)`,
     * white glass cards, and the visible `--grad` stops (not the plainer `--primary` token,
     * which the mockup keeps a shade off them) so the bubble/badge gradient matches what the
     * design actually paints on screen: `oklch(.58 .13 208)` to `oklch(.49 .185 302)`.
     */
    private val neonLight = AppTheme(
        id = NEON_LIGHT,
        label = "نئون روشن",
        topBarColor = Color.parseColor("#FBFEFF"),
        topBarTextColor = Color.parseColor("#141A24"),
        mainTextColor = Color.parseColor("#141A24"),
        mainBackgroundColor = Color.parseColor("#E3E8EC"),
        backgroundGradient = Color.parseColor("#E3E8EC") to Color.parseColor("#EFF3F6"),
        cardColor = Color.parseColor("#FBFEFF"),
        inputBarTextColor = Color.parseColor("#141A24"),
        accentGradient = Color.parseColor("#008EA2") to Color.parseColor("#763BB5"),
        accentGradientMid = Color.parseColor("#2368C9"),
        auroraAccent = Color.parseColor("#2368C9"),
        // Same radials, dimmed: the design pairs its light variant with
        // `opacity: 0.4; filter: saturate(0.75)` on the halo layer.
        haloColors = Triple(
            Color.parseColor("#009696"),
            Color.parseColor("#1A588F"),
            Color.parseColor("#733EA4")
        ),
        haloOpacity = 0.4f,
        sentBubbleTextColor = Color.parseColor("#FCFDFF"),
        receivedBubbleColor = Color.parseColor("#C6CDD5"),
        receivedBubbleTextColor = Color.parseColor("#141A24"),
        cardCornerRadiusDp = 22,
        glass = true
    )

    val all = listOf(classic, aurora, auroraLight, nocturne, nocturneLight, neon, neonLight)

    /**
     * A theme picked from the settings swatch row is really a choice of two things at once --
     * a skin, and whether it is running by day or by night. The picker only needs to ask the
     * first question; a [family]'s [dark]/[light] pair answers the second one from the
     * separate "تم تاریک" switch above it. [classic] has no counterpart, so both slots point
     * at the same instance and the switch is a no-op while it is active, with no special case
     * needed anywhere that reads a family.
     */
    data class ThemeFamily(val label: String, val dark: AppTheme, val light: AppTheme) {
        fun forDark(isDark: Boolean) = if (isDark) dark else light
        fun has(id: Int) = dark.id == id || light.id == id
    }

    val families = listOf(
        ThemeFamily("کلاسیک", classic, classic),
        ThemeFamily("آورورا", aurora, auroraLight),
        ThemeFamily("نوکترن", nocturne, nocturneLight),
        ThemeFamily("نئون", neon, neonLight),
    )

    fun familyOf(themeId: Int): ThemeFamily = families.firstOrNull { it.has(themeId) } ?: families[1]

    fun isDarkVariant(themeId: Int): Boolean = familyOf(themeId).dark.id == themeId

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
        config.accentGradientMid = theme.accentGradientMid ?: 0
        config.auroraAccentColor = theme.auroraAccent
        config.auroraHaloOne = theme.haloColors?.first ?: 0
        config.auroraHaloTwo = theme.haloColors?.second ?: 0
        config.auroraHaloThree = theme.haloColors?.third ?: 0
        config.auroraHaloOpacity = theme.haloOpacity

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
