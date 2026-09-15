package com.texto.sms.helpers

import android.graphics.Color
import com.texto.sms.R

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
    const val NOCTURNE = 3
    const val NOCTURNE_LIGHT = 4
    const val NEON = 5
    const val NEON_LIGHT = 6

    /**
     * What a fresh install opens on, what "Reset to defaults" returns to, and what an
     * install with nothing stored is labelled as. The picker names it "Default".
     *
     * One constant because this id was written out by hand in three places, and they drifted:
     * the reset button was still applying the skin from two defaults ago, so resetting landed
     * on a theme the app no longer starts with. Its own comment had already caught that
     * happening once. Change it here and all three follow.
     *
     * The light variant, not the dark one: a fresh install and a reset are both meant to open
     * in light mode. Dark mode is a choice made from inside the app, not the state it should
     * arrive in.
     */
    const val DEFAULT = NOCTURNE_LIGHT

    /**
     * Aurora's two ids. The skin is gone, but the numbers were written into
     * `appTheme` on every install that ever wore it, so they still have to be recognised --
     * see the retirement migration in `App`, which moves those installs onto Neon. Nothing
     * else may reuse 1 or 2.
     */
    const val RETIRED_AURORA = 1
    const val RETIRED_AURORA_LIGHT = 2

    /**
     * The skin's signature hues, shared by every theme so the accent gradient stays
     * recognisable whichever ground it sits on.
     */
    private val CYAN = Config.AURORA_CYAN
    private val BLUE = Config.AURORA_BLUE
    private val MAGENTA = Config.AURORA_MAGENTA

    /**
     * Classic's single ink, carrying a navy cast so the skin still reads blue while the
     * accent gradient does the actual colouring. Shared by the top bar, the body text and
     * the compose field: one ink is the point.
     */
    private val CLASSIC_INK = Color.parseColor("#16213A")

    /**
     * Classic, carrying the palette the app now opens on. The values below were read off a
     * device the skin had been tuned on rather than invented here: light bars instead of the
     * near-black they used to be, and the accent turned 11 degrees so its blue end sits on
     * the blue of Samsung's own messaging icon (hue 211, measured off that icon) instead of
     * the cyan the skin started from.
     *
     * The rotation is baked into the stops rather than shipped as a default `accentHueShift`.
     * The tonality strip calls its centre "the theme's colour", and that has to stay true: a
     * non-zero default would put the theme's own palette somewhere off-centre and leave the
     * strip unable to return to it.
     *
     * The three text settings used to be saturated blues -- #324C9B, #294D7B, #375390 -- and
     * that was the theme's one real weakness. Two things were wrong with it. Body text in a
     * link blue reads as if every name and preview were tappable, and the accent is already
     * blue, so nothing was left to mark what actually *is* selected. And the list draws its
     * preview line by fading `mainTextColor` to 58%: #324C9B faded to #8897C5, which measures
     * **2.88:1 on white** -- under every accessibility floor there is, on the single line of
     * text this app exists to show.
     *
     * They are one ink now, #16213A, which keeps a navy cast so the skin still reads as blue
     * while the accent gradient does the colouring. The faded preview goes to 4.06:1 and the
     * three settings land between 11:1 and 16:1, up from 5.3-7.9.
     */
    private val classic = AppTheme(
        id = CLASSIC,
        label = "کلاسیک",
        topBarColor = Color.parseColor("#DAD9D9"),
        topBarTextColor = CLASSIC_INK,
        mainTextColor = CLASSIC_INK,
        mainBackgroundColor = Color.WHITE,
        backgroundGradient = null,
        cardColor = Config.DEFAULT_CARD_GREY,
        inputBarTextColor = CLASSIC_INK,
        accentGradient = Color.parseColor("#00B9D8") to Color.parseColor("#1C78FF"),
        auroraAccent = MAGENTA,
        // Deepened from #062033. The gradient's blue end is the darkest ground any bubble
        // text sits on, and there the old value measured 4.1:1; this clears AA at 4.66 and
        // lifts the cyan end from 7.1 to 8.0.
        sentBubbleTextColor = Color.parseColor("#04121F"),
        receivedBubbleColor = Color.parseColor("#ECEFF4"),
        // The same ink as the list, so one message does not change colour between the
        // preview line and the bubble it opens into.
        receivedBubbleTextColor = CLASSIC_INK,
        cardCornerRadiusDp = 32,
        glass = true
    )

    /**
     * The Claude Design "Texto RTL" mockup, taken at its own values rather than reshaded into
     * the cyan skin: a navy ground fading to deep purple, slate cards, a saturated blue sent
     * bubble and a blurple accent. Its background is the literal gradient the mockup draws,
     * so it opts out of the drifting halo field the other skins use.
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
        // The mockup's own blue, deepened so white ink clears WCAG AA on both stops: the
        // mockup's #4A80FF measured 3.61:1 and #2F6BFF 4.50:1, which the colour picker's own
        // 4.5 gate rejected on a fresh install. #3366EE is 4.91:1, #2B5FE6 5.41:1.
        accentGradient = Color.parseColor("#3366EE") to Color.parseColor("#2B5FE6"),
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
        // Same deepened blue as Nocturne; see there for the measured contrast.
        accentGradient = Color.parseColor("#3366EE") to Color.parseColor("#2B5FE6"),
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

    val all = listOf(classic, nocturne, nocturneLight, neon, neonLight)

    /**
     * A theme picked from the settings swatch row is really a choice of two things at once --
     * a skin, and whether it is running by day or by night. The picker only needs to ask the
     * first question; a [family]'s [dark]/[light] pair answers the second one from the
     * separate dark:mode switch above it. [classic] has no counterpart, so both slots point
     * at the same instance and the switch is a no-op while it is active, with no special case
     * needed anywhere that reads a family.
     */
    /**
     * [labelRes] rather than a literal: the picker names the skins on screen, so they have
     * to arrive in the language the app is running in.
     */
    data class ThemeFamily(val labelRes: Int, val dark: AppTheme, val light: AppTheme) {
        fun forDark(isDark: Boolean) = if (isDark) dark else light
        fun has(id: Int) = dark.id == id || light.id == id
    }

    val families = listOf(
        ThemeFamily(R.string.theme_classic, classic, classic),
        ThemeFamily(R.string.theme_nocturne, nocturne, nocturneLight),
        ThemeFamily(R.string.theme_neon, neon, neonLight),
    )

    /**
     * Falls back to Nocturne, which is what the app opens on and what the picker calls
     * "Default". A retired Aurora id lands here only in the window before the retirement
     * migration has run.
     */
    fun familyOf(themeId: Int): ThemeFamily =
        families.firstOrNull { it.has(themeId) } ?: families[1]

    fun isDarkVariant(themeId: Int): Boolean = familyOf(themeId).dark.id == themeId

    fun byId(id: Int) = all.firstOrNull { it.id == id } ?: nocturne

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

        // The two bubbles trade places: the accent gradient belongs to the incoming side
        // now and the outgoing one takes the flat tint. Only the stored values move -- every
        // getter still means "that side's bubble", so side:based code stays right, and both
        // colour pickers in settings now act on a bubble that is actually drawn.
        config.sentBubbleColor = theme.receivedBubbleColor
        config.sentBubbleTextColor = theme.receivedBubbleTextColor
        config.receivedBubbleColor = theme.accentGradient.second
        config.receivedBubbleTextColor = theme.sentBubbleTextColor
        config.accentInkColor = theme.sentBubbleTextColor
        config.receivedBubbleColorSet = false
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
