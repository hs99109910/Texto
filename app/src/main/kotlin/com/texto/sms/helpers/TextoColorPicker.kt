package com.texto.sms.helpers

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.extensions.config
import com.texto.sms.extensions.toUiDigits

/**
 * The app's colour picker: pick a family, then how light, and watch what text on it will
 * actually look like.
 *
 * What this replaces was two pickers wearing one name. Most rows opened a single scrolling
 * row of eighteen fixed swatches, which cannot offer a lighter version of a colour it has --
 * the shade you want either happens to be in the list or does not exist. The rest, and the
 * "custom colour" escape hatch, dropped into commons' `ColorPickerDialog`: a saturation
 * square and a hue bar on a *white* card in a foreign typeface, which is sixteen million
 * answers to a question with about fifty good ones, and the one surface in the app that
 * ignored the skin entirely.
 *
 * Splitting it in two bands is what makes the second band possible, and the second band is
 * the point: those five colours are one hue at five weights, which is the choice actually
 * being made when someone tints a bar or a bubble. Both are [TextoHueStrip], the same widget
 * the settings screen's tonality row uses, so picking a colour looks the same everywhere.
 *
 * @param current the colour the row holds now; the sheet opens on it
 * @param defaultColour the skin's own value for this row, offered as a way back. Null hides
 *   that button, for a row that has no meaningful default.
 * @param contrastAgainst the ink that will sit on this colour, when the row is a background,
 *   or the ground it will sit on, when the row is ink. Null reduces the preview to a plain
 *   swatch, for a row where nothing is written on the result.
 * @param onPick fired on every change, so the screen behind previews it. Dismissing without
 *   saving fires it once more with [current] to put things back.
 */
fun SimpleActivity.textoColorPicker(
    title: String,
    current: Int,
    defaultColour: Int? = null,
    contrastAgainst: Int? = null,
    onPick: (Int) -> Unit,
): AlertDialog? {
    val density = resources.displayMetrics.density
    var dialog: AlertDialog? = null

    val opaqueCurrent = current or 0xFF000000.toInt()
    var (familyIndex, shadeIndex) = TextoPalette.locate(opaqueCurrent)
    var chosen = opaqueCurrent
    // Only a tap on Save keeps the choice. Everything else -- back, the scrim, Default
    // followed by back -- has to leave the row exactly as it was found, because the preview
    // has been writing to config the whole time.
    var saved = false

    val sheet = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = 20.getScaledPx()
        setPadding(pad, pad, pad, pad)
        background = pickerCard()
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
    }

    sheet.addView(
        TextView(this).apply {
            text = title
            setTextColor(config.mainTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.05f))
            typeface = typefaceFor(Typeface.BOLD)
            gravity = Gravity.START
            setPadding(0, 0, 0, 16.getScaledPx())
        }
    )

    // ---- the preview -----------------------------------------------------------------------
    // First rather than last, because it is the answer and the two bands are the question.
    val preview = TextView(this).apply {
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.95f))
        typeface = typefaceFor(Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 64.getScaledPx()
        )
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
    }
    sheet.addView(preview)

    val verdict = TextView(this).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.76f))
        typeface = typefaceFor(Typeface.NORMAL)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 8.getScaledPx() }
        visibility = if (contrastAgainst == null) View.GONE else View.VISIBLE
    }
    sheet.addView(verdict)

    // ---- 1: the family band ----------------------------------------------------------------
    sheet.addView(
        sectionLabel(getString(R.string.colour_family)).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20.getScaledPx() }
        }
    )
    val familyStrip = TextoHueStrip(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 44.getScaledPx()
        ).apply { topMargin = 8.getScaledPx() }
    }
    sheet.addView(familyStrip)

    // ---- 2: the ladder ---------------------------------------------------------------------
    val shadeHeader = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 18.getScaledPx() }
    }
    shadeHeader.addView(
        sectionLabel(getString(R.string.colour_lightness)).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
    )
    shadeHeader.addView(
        TextView(this).apply {
            text = getString(R.string.colour_dark_to_light)
            setTextColor(config.mainTextColor.withAlpha(0.58f))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.74f))
            typeface = typefaceFor(Typeface.NORMAL)
        }
    )
    sheet.addView(shadeHeader)

    val shadeStrip = TextoHueStrip(this).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 44.getScaledPx()
        ).apply { topMargin = 8.getScaledPx() }
    }
    sheet.addView(shadeStrip)

    // ---- recents ---------------------------------------------------------------------------
    val recentRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 18.getScaledPx() }
    }
    sheet.addView(recentRow)

    // ---- wiring ----------------------------------------------------------------------------
    fun renderPreview() {
        preview.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f * density
            setColor(chosen)
            setStroke(1.getScaledPx(), TextoGlass.rimFor(chosen, 0.34f))
        }
        val ink = contrastAgainst
        if (ink == null) {
            preview.text = ""
            return
        }
        // The sample is set in the very ink that will sit on this colour, so the preview is
        // the test rather than an illustration of it : an unreadable pair looks unreadable
        // here before it is committed anywhere.
        preview.text = getString(R.string.colour_preview_sample)
        preview.setTextColor(ink)

        val ratio = TextoPalette.contrastRatio(chosen, ink)
        val ok = ratio >= 4.5
        val rounded = (Math.round(ratio * 10) / 10.0).toString()
        verdict.text = (if (ok) "✓  " else "!  ") + getString(
            if (ok) R.string.colour_contrast_ok else R.string.colour_contrast_low
        ) + "   " + getString(R.string.colour_contrast_ratio, rounded).toUiDigits()
        verdict.setTextColor(
            if (ok) config.mainTextColor.withAlpha(0.72f) else 0xFFF54651.toInt()
        )
    }

    fun renderShadeStrip() {
        shadeStrip.submit(TextoPalette.families[familyIndex].toList(), shadeIndex)
    }

    fun renderRecents() {
        recentRow.removeAllViews()
        val recents = config.recentColours
        recentRow.visibility = if (recents.isEmpty()) View.GONE else View.VISIBLE
        if (recents.isEmpty()) return
        recentRow.addView(
            TextView(this).apply {
                text = getString(R.string.colour_recent)
                setTextColor(config.mainTextColor.withAlpha(0.58f))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.74f))
                typeface = typefaceFor(Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 10.getScaledPx() }
            }
        )
        recents.forEach { colour ->
            recentRow.addView(
                recentTile(colour, TextoPalette.sameColour(colour, chosen)) {
                    val (family, shade) = TextoPalette.locate(colour)
                    familyIndex = family
                    shadeIndex = shade
                    chosen = colour or 0xFF000000.toInt()
                    familyStrip.setSelectedSilently(family)
                    renderShadeStrip()
                    onPick(chosen)
                    renderPreview()
                    renderRecents()
                }
            )
        }
    }

    familyStrip.submit(TextoPalette.families.indices.map { TextoPalette.faceOf(it) }, familyIndex)
    familyStrip.onPicked = { index ->
        familyIndex = index
        chosen = TextoPalette.families[index][shadeIndex]
        renderShadeStrip()
        onPick(chosen)
        renderPreview()
    }
    shadeStrip.onPicked = { index ->
        shadeIndex = index
        chosen = TextoPalette.families[familyIndex][index]
        onPick(chosen)
        renderPreview()
    }
    renderShadeStrip()
    renderRecents()
    renderPreview()

    // ---- footer ----------------------------------------------------------------------------
    val footer = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 22.getScaledPx() }
    }
    defaultColour?.let { fallback ->
        footer.addView(
            pickerButton(getString(R.string.colour_default), filled = false) {
                val (family, shade) = TextoPalette.locate(fallback)
                familyIndex = family
                shadeIndex = shade
                chosen = fallback or 0xFF000000.toInt()
                familyStrip.setSelectedSilently(family)
                renderShadeStrip()
                onPick(chosen)
                renderPreview()
                renderRecents()
            }
        )
    }
    footer.addView(
        pickerButton(getString(R.string.colour_save), filled = true) {
            saved = true
            config.rememberColour(chosen)
            dialog?.dismiss()
        }
    )
    sheet.addView(footer)

    val scroller = android.widget.ScrollView(this).apply {
        isFillViewport = true
        overScrollMode = View.OVER_SCROLL_NEVER
        addView(sheet)
    }

    dialog = AlertDialog.Builder(this)
        .setView(scroller)
        .create()
        .apply {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setOnDismissListener {
                // The preview wrote every intermediate colour straight to config, so leaving
                // without saving has to put the original back or a cancelled look-around
                // silently becomes the new setting.
                if (!saved) onPick(current)
            }
            show()
        }

    return dialog
}

/** The sheet's own ground: the same card and rim every other sheet in the app is drawn on. */
private fun SimpleActivity.pickerCard(): GradientDrawable {
    val density = resources.displayMetrics.density
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 26 * density
        setColor(config.recentColor)
        setStroke(1.getScaledPx(), TextoGlass.rimFor(config.recentColor, 0.18f))
    }
}

private fun SimpleActivity.sectionLabel(text: String) = TextView(this).apply {
    this.text = text
    setTextColor(config.accentGradientStart)
    setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.78f))
    typeface = typefaceFor(Typeface.BOLD)
}

private fun SimpleActivity.recentTile(colour: Int, isChosen: Boolean, onTap: () -> Unit): View {
    val density = resources.displayMetrics.density
    val side = 34.getScaledPx()
    return View(this).apply {
        layoutParams = LinearLayout.LayoutParams(side, side).apply {
            marginEnd = 8.getScaledPx()
        }
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 11 * density
            setColor(colour)
            setStroke(
                if (isChosen) (2 * density).toInt() else 1.getScaledPx(),
                if (isChosen) config.mainTextColor else TextoGlass.rimFor(colour, 0.30f)
            )
        }
        outlineProvider = ViewOutlineProvider.BACKGROUND
        isClickable = true
        setOnClickListener { onTap() }
    }
}

/** The same capsule pair every other sheet ends with. */
private fun SimpleActivity.pickerButton(
    label: String,
    filled: Boolean,
    onTap: () -> Unit,
): TextView {
    val density = resources.displayMetrics.density
    return TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.88f))
        typeface = typefaceFor(if (filled) Typeface.BOLD else Typeface.NORMAL)
        setTextColor(if (filled) config.accentInkColor else config.mainTextColor)
        val padV = 12.getScaledPx()
        setPadding(0, padV, 0, padV)
        background = if (filled) {
            TextoGlass.accent(
                start = config.accentGradientStart,
                end = config.accentGradientEnd,
                cornerRadius = 100f * density,
                mid = config.accentGradientMid
            )
        } else {
            TextoGlass.bar(
                tint = config.mainBackgroundColor,
                cornerRadius = 100f * density,
                opacity = 0.5f,
                strokeWidthPx = 1.getScaledPx(),
                rimAlpha = 0.18f
            )
        }
        outlineProvider = ViewOutlineProvider.BACKGROUND
        isClickable = true
        layoutParams = LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ).apply { marginStart = 8.getScaledPx() }
        setOnClickListener { onTap() }
    }
}
