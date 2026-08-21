package com.texto.sms.helpers

import android.graphics.Color

/**
 * A theme is a named bundle of the colour settings the app already exposes, plus the two
 * shape choices that cannot be expressed as a colour: the card corner radius and whether
 * the background is a flat fill or a vertical gradient.
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
    val backgroundGradient: Pair<Int, Int>?,
    val cardColor: Int,
    val inputBarBackgroundColor: Int,
    val inputBarTextColor: Int,
    val sentBubbleColor: Int,
    val sentBubbleTextColor: Int,
    val receivedBubbleColor: Int,
    val receivedBubbleTextColor: Int,
    /** In dp. A very large value renders the cards as full pills. */
    val cardCornerRadiusDp: Int,
    val glass: Boolean,
)

object AppThemes {

    const val CLASSIC = 0
    const val AURORA = 1

    /** The look the app shipped with: light background, pastel pill cards. */
    private val classic = AppTheme(
        id = CLASSIC,
        label = "کلاسیک",
        topBarColor = Color.BLACK,
        topBarTextColor = Color.WHITE,
        mainTextColor = Color.BLACK,
        mainBackgroundColor = Color.WHITE,
        backgroundGradient = null,
        cardColor = Color.parseColor("#FFE4E1"),
        inputBarBackgroundColor = Config.DEFAULT_DARK_GREY,
        inputBarTextColor = Color.WHITE,
        sentBubbleColor = Config.DEFAULT_SENT_GREY,
        sentBubbleTextColor = Color.BLACK,
        receivedBubbleColor = Config.DEFAULT_LIGHT_GREY,
        receivedBubbleTextColor = Color.BLACK,
        cardCornerRadiusDp = 500,
        glass = true
    )

    /** Dark navy fading into purple, translucent slate cards, bright blue accent. */
    private val aurora = AppTheme(
        id = AURORA,
        label = "آورورا",
        topBarColor = Color.parseColor("#0B1026"),
        topBarTextColor = Color.WHITE,
        mainTextColor = Color.WHITE,
        mainBackgroundColor = Color.parseColor("#0B1026"),
        backgroundGradient = Color.parseColor("#0B1026") to Color.parseColor("#3D2260"),
        cardColor = Color.parseColor("#1B2136"),
        inputBarBackgroundColor = Color.parseColor("#171C2E"),
        inputBarTextColor = Color.WHITE,
        sentBubbleColor = Color.parseColor("#2F6BFF"),
        sentBubbleTextColor = Color.WHITE,
        receivedBubbleColor = Color.parseColor("#232838"),
        receivedBubbleTextColor = Color.WHITE,
        cardCornerRadiusDp = 24,
        glass = true
    )

    val all = listOf(classic, aurora)

    fun byId(id: Int) = all.firstOrNull { it.id == id } ?: classic

    /** Overwrites the colour settings with [theme]'s values. */
    fun apply(config: Config, theme: AppTheme) {
        config.appTheme = theme.id
        config.topBarColor = theme.topBarColor
        config.topBarTextColor = theme.topBarTextColor
        config.mainTextColor = theme.mainTextColor
        config.mainBackgroundColor = theme.mainBackgroundColor
        config.recentColor = theme.cardColor
        config.inputBarBackgroundColor = theme.inputBarBackgroundColor
        config.inputBarTextColor = theme.inputBarTextColor
        config.sentBubbleColor = theme.sentBubbleColor
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
            config.mainBgMode = BG_MODE_GRADIENT
            config.mainBgGradientStart = theme.backgroundGradient.first
            config.mainBgGradientEnd = theme.backgroundGradient.second
        } else {
            config.mainBgMode = BG_MODE_COLOR
        }
    }
}
