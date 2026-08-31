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
    val isActive: Boolean = false,
    val onPick: () -> Unit,
)

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
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 26 * density
            setColor(config.recentColor)
            setStroke(1.getScaledPx(), TextoGlass.rimFor(config.recentColor, 0.18f))
        }
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

        val ink = if (choice.isActive) config.sentBubbleTextColor else config.mainTextColor

        choice.swatch?.takeIf { !choice.isActive }?.let { swatch ->
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
                }
            )
            choice.subtitle?.let { sub ->
                addView(
                    TextView(this@textoCapsuleDialog).apply {
                        text = sub
                        setTextColor(ink.withAlpha(0.68f))
                        setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.76f))
                        typeface = typefaceFor(Typeface.NORMAL)
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
