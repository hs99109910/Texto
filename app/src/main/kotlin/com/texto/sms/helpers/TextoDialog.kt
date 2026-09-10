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
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.extensions.config
import com.texto.sms.extensions.withAlpha

/**
 * One capsule row in a [textoCapsuleDialog].
 *
 * [swatch] paints a small filled dot ahead of the label -- a SIM slot's colour, a theme's
 * accent -- and is left out entirely when the choice has no colour of its own.
 */
data class CapsuleChoice(
    val label: String,
    val subtitle: String? = null,
    val swatch: Int? = null,
    val swatchEnd: Int? = null,
    /** Drawable shown ahead of the label, in the row's own ink. Takes the swatch's slot. */
    val icon: Int? = null,
    val isActive: Boolean = false,
    /** Paints the row in the same red the overflow menus give delete, label and icon alike. */
    val isDestructive: Boolean = false,
    val onPick: () -> Unit,
)

/**
 * The card every sheet in the app is drawn on: the theme's card colour under a hairline rim,
 * rounded to the same 26dp. Shared so the sheets this file builds and the ones commons builds
 * for us end up on literally the same ground.
 */
private fun SimpleActivity.sheetCard(): GradientDrawable {
    val density = resources.displayMetrics.density
    return GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = 26 * density
        setColor(config.recentColor)
        setStroke(1.getScaledPx(), TextoGlass.rimFor(config.recentColor, 0.18f))
    }
}

/**
 * One action capsule at the foot of a sheet: the accent gradient for the affirmative, a glass
 * pill for everything else. Shared by [textoInputDialog] and [textoConfirmDialog] so the two
 * offer visibly the same pair of buttons.
 */
private fun SimpleActivity.sheetButton(
    label: String,
    filled: Boolean,
    ink: Int? = null,
    onTap: () -> Unit,
): TextView {
    val density = resources.displayMetrics.density
    return TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.88f))
        typeface = typefaceFor(if (filled) Typeface.BOLD else Typeface.NORMAL)
        setTextColor(ink ?: if (filled) config.accentInkColor else config.mainTextColor)
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

/**
 * The app's own confirmation sheet, in place of commons' `ConfirmationDialog`.
 *
 * That one is built by `setupDialogStuff`, which resolves its ground, its ink and its
 * typeface from the *base* theme -- the light one -- so on any of this app's skins it arrived
 * as a white card carrying a face nothing else on screen uses. This is the same card, ink and
 * capsules as every other sheet here.
 *
 * [negativeLabel] of null makes it a one-button acknowledgement rather than a question.
 */
fun SimpleActivity.textoConfirmDialog(
    message: String,
    title: String = "",
    positiveLabel: String = getString(com.texto.sms.R.string.action_confirm),
    negativeLabel: String? = getString(com.texto.sms.R.string.action_cancel),
    isDestructive: Boolean = false,
    cancelOnTouchOutside: Boolean = true,
    onConfirm: () -> Unit,
): AlertDialog {
    var dialog: AlertDialog? = null

    val sheet = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = 18.getScaledPx()
        setPadding(pad, pad, pad, pad)
        background = sheetCard()
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
    }

    if (title.isNotEmpty()) {
        sheet.addView(
            TextView(this).apply {
                text = title
                setTextColor(config.mainTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.05f))
                typeface = typefaceFor(Typeface.BOLD)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                setPadding(8.getScaledPx(), 0, 8.getScaledPx(), 10.getScaledPx())
            }
        )
    }

    // Scrolled, because this doubles as the "show the whole message" sheet and an SMS can
    // easily be taller than the screen.
    sheet.addView(
        ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(
                TextView(this@textoConfirmDialog).apply {
                    text = message
                    setTextColor(config.mainTextColor.withAlpha(0.82f))
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())
                    typeface = typefaceFor(Typeface.NORMAL)
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    setTextIsSelectable(true)
                    setPadding(8.getScaledPx(), 0, 8.getScaledPx(), 0)
                }
            )
        },
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )

    val buttons = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 14.getScaledPx(), 0, 0)
    }
    buttons.addView(
        sheetButton(
            label = positiveLabel,
            // An irreversible action is marked the same way it is in the menus: red ink on
            // the plain pill, never a red fill. Keeping the accent gradient under it would
            // have put red type on cyan, and a red *fill* reads as the thing to press.
            filled = !isDestructive,
            ink = if (isDestructive) DESTRUCTIVE_INK else null
        ) {
            dialog?.dismiss()
            onConfirm()
        }
    )
    negativeLabel?.let { label ->
        buttons.addView(sheetButton(label, filled = false) { dialog?.dismiss() })
    }
    sheet.addView(buttons)

    dialog = AlertDialog.Builder(this)
        .setView(sheet)
        .create()
        .apply {
            setCanceledOnTouchOutside(cancelOnTouchOutside)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            show()
        }

    return dialog
}

/**
 * Repaints a dialog commons built for us.
 *
 * `setupDialogStuff` takes its window ground, its ink and its typeface from the *base* theme,
 * which on this app is the light one, so on any skin those dialogs arrive as a white card
 * with a foreign face on it. The sheets in this file avoid it by building themselves; the
 * ones that inflate a real layout cannot, so this undoes it afterwards instead.
 *
 * Call it from `setupDialogStuff`'s own callback: that runs after the dialog is shown and
 * after commons has finished colouring it, which is the only point where this wins.
 */
fun SimpleActivity.applyTextoDialogSkin(dialog: AlertDialog) {
    val density = resources.displayMetrics.density
    val window = dialog.window ?: return

    // Painted on the window itself: commons' content sits straight on it, so there is no
    // intermediate view left to carry a rounded ground.
    val sideInset = (16 * density).toInt()
    window.setBackgroundDrawable(
        android.graphics.drawable.InsetDrawable(sheetCard(), sideInset, 0, sideInset, 0)
    )

    val decor = window.decorView
    // Face and ink for everything at once, before the few that want something else. This is
    // the same pass onResume runs over an activity, so a dialog and the screen behind it end
    // up on identical type.
    updateAppFonts(decor)

    decor.findViewById<TextView>(org.fossify.commons.R.id.dialog_title_textview)?.apply {
        setTextColor(config.mainTextColor)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.05f))
        typeface = typefaceFor(Typeface.BOLD)
        background = null
    }

    // The affirmative carries the accent, as it does on every sheet the app draws itself.
    listOf(
        AlertDialog.BUTTON_POSITIVE to true,
        AlertDialog.BUTTON_NEGATIVE to false,
        AlertDialog.BUTTON_NEUTRAL to false,
    ).forEach { (which, isPrimary) ->
        val button = runCatching { dialog.getButton(which) }.getOrNull() ?: return@forEach
        button.setTextColor(
            if (isPrimary) config.accentGradientStart else config.mainTextColor.withAlpha(0.75f)
        )
        button.typeface = typefaceFor(if (isPrimary) Typeface.BOLD else Typeface.NORMAL)
        button.setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.88f))
        // The base theme puts an all-caps transform on dialog buttons. Persian has no case,
        // so it does nothing here except drop the label out of the app's own type.
        button.transformationMethod = null
    }
}

/**
 * A one-field sheet, drawn to the same recipe as [textoCapsuleDialog].
 *
 * Commons' setupDialogStuff puts a title bar and buttons from the base (light) theme above
 * whatever content it is given, which is what left a white strip and a face nothing else in
 * the app uses on the rename dialog.
 */
fun SimpleActivity.textoInputDialog(
    title: String,
    hint: String,
    initialText: String = "",
    onConfirm: (String) -> Unit,
): AlertDialog {
    val density = resources.displayMetrics.density
    var dialog: AlertDialog? = null

    val sheet = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = 18.getScaledPx()
        setPadding(pad, pad, pad, pad)
        background = sheetCard()
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
    }

    sheet.addView(
        TextView(this).apply {
            text = title
            setTextColor(config.mainTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.05f))
            typeface = typefaceFor(Typeface.BOLD)
            setPadding(8.getScaledPx(), 0, 8.getScaledPx(), 12.getScaledPx())
        }
    )

    // The field is the same glass capsule the search bar and the composer are.
    val field = EditText(this).apply {
        setText(initialText)
        setSelection(text.length)
        this.hint = hint
        setSingleLine()
        setTextColor(config.inputBarTextColor)
        setHintTextColor(config.inputBarTextColor.withAlpha(0.5f))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        background = TextoGlass.bar(
            tint = config.inputBarBackgroundColor,
            cornerRadius = 100f * density,
            opacity = 0.6f,
            strokeWidthPx = 1.getScaledPx()
        )
        outlineProvider = ViewOutlineProvider.BACKGROUND
        val padH = 18.getScaledPx()
        val padV = 14.getScaledPx()
        setPadding(padH, padV, padH, padV)
    }
    sheet.addView(
        field,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )

    val buttons = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 14.getScaledPx(), 0, 0)
    }

    buttons.addView(
        sheetButton(getString(com.texto.sms.R.string.action_confirm), filled = true) {
            onConfirm(field.text.toString())
            dialog?.dismiss()
        }
    )
    buttons.addView(
        sheetButton(getString(com.texto.sms.R.string.action_cancel), filled = false) {
            dialog?.dismiss()
        }
    )
    sheet.addView(buttons)

    dialog = AlertDialog.Builder(this)
        .setView(sheet)
        .create()
        .apply {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            show()
        }

    field.requestFocus()
    return dialog
}

/**
 * The app's own chooser sheet.
 *
 * Commons' `setupDialogStuff` draws its title and its window ground from the *base* theme,
 * which on this app is the light one: on the Neon skin that surfaced as a white bar above
 * the content and a title in a face nothing else on screen uses. This builds the whole sheet
 * instead -- card ground, app typeface, the same capsule rows the filter chips and the SIM
 * picker already use -- so every chooser in the app is visibly one control.
 */
fun SimpleActivity.textoCapsuleDialog(
    title: String,
    choices: List<CapsuleChoice>,
    footer: View? = null,
): AlertDialog {
    val density = resources.displayMetrics.density
    var dialog: AlertDialog? = null

    val sheet = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val pad = 18.getScaledPx()
        setPadding(pad, pad, pad, pad)
        background = sheetCard()
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
            setPadding(8.getScaledPx(), 0, 8.getScaledPx(), 12.getScaledPx())
        }
    )

    val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    choices.forEach { choice ->
        val capsuleRadius = 100f * density
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.getScaledPx(), 12.getScaledPx(), 16.getScaledPx(), 12.getScaledPx())
            isClickable = true
            background = if (choice.isActive) {
                TextoGlass.accent(
                    start = config.accentGradientStart,
                    end = config.accentGradientEnd,
                    cornerRadius = capsuleRadius,
                    mid = config.accentGradientMid
                )
            } else {
                TextoGlass.bar(
                    tint = config.mainBackgroundColor,
                    cornerRadius = capsuleRadius,
                    opacity = 0.5f,
                    strokeWidthPx = 1.getScaledPx(),
                    rimAlpha = 0.18f
                )
            }
            outlineProvider = ViewOutlineProvider.BACKGROUND
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 6.getScaledPx() }
            setOnClickListener {
                choice.onPick()
                dialog?.dismiss()
            }
        }

        val ink = when {
            choice.isActive -> config.accentInkColor
            choice.isDestructive -> DESTRUCTIVE_INK
            else -> config.mainTextColor
        }

        // Drawn on every row that has one, the picked row included. Skipping it there was
        // what made the text jump: the label started 30dp further in on rows that kept their
        // dot, so choosing a different SIM shifted the whole column sideways. The rim below
        // is already mixed from the row's own ink, so the dot reads on the accent fill too.
        choice.swatch?.let { swatch ->
            val side = 18.getScaledPx()
            row.addView(
                ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(side, side).apply {
                        marginEnd = 12.getScaledPx()
                    }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        if (choice.swatchEnd != null) {
                            colors = intArrayOf(swatch, choice.swatchEnd)
                            orientation = GradientDrawable.Orientation.TL_BR
                        } else {
                            setColor(swatch)
                        }
                        // A rim in the row's own ink, so a swatch close to either ground
                        // still reads as a dot rather than vanishing into it.
                        setStroke(1.getScaledPx(), ink.withAlpha(0.45f))
                    }
                }
            )
        }

        // Same leading slot as the swatch, so a sheet of icon rows and a sheet of colour
        // rows line their labels up at the same place.
        choice.icon?.let { iconRes ->
            val side = 20.getScaledPx()
            row.addView(
                ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(side, side).apply {
                        marginEnd = 12.getScaledPx()
                    }
                    setImageResource(iconRes)
                    imageTintList = android.content.res.ColorStateList.valueOf(ink)
                }
            )
        }

        val text = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
            )
            addView(
                TextView(this@textoCapsuleDialog).apply {
                    text = choice.label
                    setTextColor(ink)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize())
                    typeface = typefaceFor(
                        if (choice.isActive) Typeface.BOLD else Typeface.NORMAL
                    )
                    // Pinned to the row's start rather than left to the text's own
                    // direction. A Latin carrier name ("Irancell") resolves LTR and a
                    // Persian one RTL, so without this the two rows of the SIM chooser
                    // hung off opposite edges and the label appeared to jump between them.
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                }
            )
            choice.subtitle?.let { sub ->
                addView(
                    TextView(this@textoCapsuleDialog).apply {
                        text = sub
                        setTextColor(ink.withAlpha(0.68f))
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.76f))
                        typeface = typefaceFor(Typeface.NORMAL)
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    }
                )
            }
        }
        row.addView(text)
        list.addView(row)
    }

    // Long lists (the date presets, a phone with many themes) stay reachable rather than
    // running off the bottom of a sheet that cannot scroll.
    sheet.addView(
        ScrollView(this).apply {
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(list)
        },
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    )

    footer?.let { sheet.addView(it) }

    dialog = AlertDialog.Builder(this)
        .setView(sheet)
        .create()
        .apply {
            // The sheet paints its own rounded card; leaving the platform's opaque one behind
            // it puts a square light panel around every corner.
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            show()
        }

    return dialog
}
