package com.texto.sms.helpers

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.extensions.config
import com.texto.sms.extensions.withAlpha

/**
 * The app's emoji sheet: categories across the top, a scrolling grid under them, and the most
 * recently used first.
 *
 * It exists because Android offers no way to open the keyboard's emoji panel. There is no
 * intent for it and no key that reaches it -- KEYCODE_PICTSYMBOLS was tried on the device and
 * this keyboard ignores it -- which is why Messages, WhatsApp and Telegram all draw their own.
 * The keyboard still supplies the *stickers and GIFs*, through the composer field's accepted
 * content types; this covers the one thing that route cannot.
 *
 * What it replaced was twenty-four emoji in a popup with no categories, no search order and no
 * memory, so the same faces had to be hunted for every time.
 *
 * The sheet stays open on a pick. Emoji are sent in runs -- three of them, or one repeated --
 * and a sheet that closed after each would make the second one cost as much as the first.
 */
fun SimpleActivity.textoEmojiPicker(onPick: (String) -> Unit) {
    val density = resources.displayMetrics.density
    var dialog: AlertDialog? = null

    val sheet = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = 14.getScaledPx()
        setPadding(pad, pad, pad, pad)
        background = pickerCard()
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
    }

    val grid = GridLayout(this).apply {
        columnCount = EMOJI_COLUMNS
    }
    val scroller = ScrollView(this).apply {
        overScrollMode = View.OVER_SCROLL_NEVER
        isVerticalScrollBarEnabled = false
        addView(grid)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 260.getScaledPx()
        ).apply { topMargin = 10.getScaledPx() }
    }

    val cell = 42.getScaledPx()

    fun cellFor(glyph: String) = TextView(this).apply {
        text = glyph
        gravity = Gravity.CENTER
        includeFontPadding = false
        // A colour emoji is drawn at its paint's *alpha*, and a TextView that never sets a
        // colour inherits the theme's default text ink, which is translucent. Measured on
        // device: the faces came out #fde6c0 against a real #fcc21b and the eyes #d2c8c3
        // against near-black -- every emoji in this sheet washed halfway into the card behind
        // it. Nothing here wants that ink for its own sake, only its opacity.
        setTextColor(config.mainTextColor.withAlpha(1f))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.35f))
        layoutParams = GridLayout.LayoutParams().apply {
            width = cell
            height = cell
        }
        isClickable = true
        val ripple = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true)
        if (ripple.resourceId != 0) setBackgroundResource(ripple.resourceId)
        setOnClickListener {
            config.rememberEmoji(glyph)
            onPick(glyph)
        }
    }

    fun showCategory(emoji: List<String>) {
        grid.removeAllViews()
        emoji.forEach { grid.addView(cellFor(it)) }
        scroller.scrollTo(0, 0)
    }

    // ---- the category strip ----------------------------------------------------------------
    val tabsRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    val tabs = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(tabsRow)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    sheet.addView(tabs)
    sheet.addView(scroller)

    val recents = config.recentEmoji
    // "Recent" is a category like any other and leads when there is anything in it, because
    // the emoji somebody sends are a short list they send over and over.
    val allTabs = buildList {
        if (recents.isNotEmpty()) add("🕘" to recents)
        TextoEmoji.categories.forEach { add(it.icon to it.emoji) }
    }

    val tabViews = mutableListOf<TextView>()
    fun paintTabs(selected: Int) {
        tabViews.forEachIndexed { index, view ->
            view.background = if (index == selected) {
                TextoGlass.bar(
                    tint = config.accentGradientStart,
                    cornerRadius = 100f * density,
                    opacity = 0.20f,
                    strokeWidthPx = 1.getScaledPx(),
                    rimAlpha = 0.4f
                )
            } else {
                null
            }
        }
    }

    allTabs.forEachIndexed { index, (icon, emoji) ->
        val tab = TextView(this).apply {
            text = icon
            gravity = Gravity.CENTER
            includeFontPadding = false
            // Opaque for the same reason the cells are: the category icons are emoji too.
            setTextColor(config.mainTextColor.withAlpha(1f))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.05f))
            val padH = 10.getScaledPx()
            val padV = 7.getScaledPx()
            setPadding(padH, padV, padH, padV)
            isClickable = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 4.getScaledPx() }
            setOnClickListener {
                showCategory(emoji)
                paintTabs(index)
            }
        }
        tabViews.add(tab)
        tabsRow.addView(tab)
    }

    showCategory(allTabs.first().second)
    paintTabs(0)

    sheet.addView(
        pickerButton(getString(R.string.action_close), filled = false) {
            dialog?.dismiss()
        }.apply {
            // pickerButton is built for a footer *row*, where it shares the width with a
            // sibling: it comes back 0dp wide on a weight. This sheet's footer is one button
            // in a vertical column, so it has to take the width outright or it measures to
            // nothing and never appears.
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.getScaledPx() }
        }
    )

    dialog = AlertDialog.Builder(this)
        .setView(sheet)
        .create()
        .apply {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            show()
        }
}

/** Eight across fits a 42dp cell on the narrowest phone this app supports without scrolling sideways. */
private const val EMOJI_COLUMNS = 8
