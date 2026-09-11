package com.texto.sms.dialogs

import android.app.Activity
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
import com.texto.sms.extensions.toast
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.extensions.config
import com.texto.sms.extensions.toUiDigits
import com.texto.sms.helpers.DESTRUCTIVE_INK
import com.texto.sms.helpers.FilterStore
import com.texto.sms.helpers.MessageFilter
import com.texto.sms.helpers.SystemBlockedNumbers
import com.texto.sms.helpers.TextoFlowLayout
import com.texto.sms.helpers.TextoGlass
import com.texto.sms.helpers.senderChip
import com.texto.sms.helpers.textoSenderPicker

/**
 * Creates or edits a user-defined filter chip: its name, who it covers, and how the chats it
 * covers look and sound.
 *
 * Built as one of the app's own sheets rather than through commons' `setupDialogStuff`, which
 * is what this was before. That drew a white card, a Material outlined field in the base
 * theme's blue and three all-caps system buttons -- so the one screen reached by long-pressing
 * a chip looked like it belonged to a different app than the chip did, and than the two
 * pickers and the colours sheet it opens, both of which had already been rebuilt this way.
 *
 * Everything about the filter is on it at once, which is the other half of the same problem:
 * the sender list used to be a single scrolling line that hid most of a longer filter behind
 * an edge, and the colours were a row that said nothing about what was set until you opened
 * it. Senders wrap now, so all of them are visible, and the colours row carries the actual
 * swatches and the sound's name.
 */
class EditFilterDialog(
    private val activity: Activity,
    private val existing: MessageFilter? = null,
    /** Every conversation currently on the main screen, offered as pickable senders. */
    private val pickableSenders: List<Pair<String, String>> = emptyList(),
    /** The phone book, offered as the second source to build a filter from. */
    private val pickableContacts: List<Pair<String, String>> = emptyList(),
    private val onDelete: (() -> Unit)? = null,
    /**
     * Opens the colours-and-sound sheet, and reports back what was saved so this sheet can
     * repaint its own preview without waiting to be reopened.
     */
    private val onCustomize: ((MessageFilter, onSaved: (MessageFilter) -> Unit) -> Unit)? = null,
    private val callback: (filter: MessageFilter) -> Unit,
) {
    private val simpleActivity = activity as? SimpleActivity
    private var dialog: AlertDialog? = null

    private val chosenNumbers = existing?.senders.orEmpty().toMutableList()
    private val chosenLabels = existing?.senderLabels.orEmpty().toMutableList()

    /** Tracks what the colours sheet has saved, so the preview row stays current. */
    private var appearance = existing

    init {
        val act = simpleActivity
        if (act == null) {
            activity.toast(R.string.unknown_error_occurred)
        } else {
            act.build()
        }
    }

    private fun SimpleActivity.build() {
        val density = resources.displayMetrics.density

        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = 20.getScaledPx()
            setPadding(pad, pad, pad, pad)
            background = sheetGround()
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }

        sheet.addView(
            TextView(this).apply {
                text = getString(if (existing == null) R.string.add_filter else R.string.edit_filter)
                setTextColor(config.mainTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.05f))
                typeface = typefaceFor(Typeface.BOLD)
                setPadding(0, 0, 0, 14.getScaledPx())
            }
        )

        // ---- the name ----------------------------------------------------------------------
        val nameField = EditText(this).apply {
            setText(existing?.label.orEmpty())
            setSelection(text.length)
            hint = getString(R.string.filter_name)
            setSingleLine()
            filters = arrayOf(android.text.InputFilter.LengthFilter(24))
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
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        sheet.addView(nameField)

        // ---- who it covers -----------------------------------------------------------------
        val sendersHeader = TextView(this).apply {
            setTextColor(config.accentGradientStart)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.78f))
            typeface = typefaceFor(Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20.getScaledPx() }
        }
        sheet.addView(sendersHeader)

        val chips = TextoFlowLayout(this).apply {
            lineSpacing = 8.getScaledPx()
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.getScaledPx() }
        }

        fun renderSenders() {
            // The count is the point of the header: a filter is its sender list, and the
            // number was the one thing the old summary line never said.
            sendersHeader.text = if (chosenNumbers.isEmpty()) {
                getString(R.string.filter_senders_section)
            } else {
                "${getString(R.string.filter_senders_section)}  ·  ${chosenNumbers.size.toString().toUiDigits()}"
            }
            chips.removeAllViews()
            chips.visibility = if (chosenNumbers.isEmpty()) View.GONE else View.VISIBLE
            chosenLabels.forEachIndexed { index, label ->
                chips.addView(senderChip(label) {
                    if (index in chosenNumbers.indices) {
                        chosenNumbers.removeAt(index)
                        chosenLabels.removeAt(index)
                        renderSenders()
                    }
                })
            }
        }

        sheet.addView(
            sourceRow(R.drawable.ic_settings_bubble, R.string.pick_from_chats) {
                pickSenders(pickableSenders, R.string.no_conversations_to_pick, R.string.pick_from_chats) {
                    renderSenders()
                }
            }
        )
        sheet.addView(
            sourceRow(R.drawable.ic_settings_person, R.string.pick_from_contacts) {
                pickSenders(pickableContacts, R.string.no_contacts_to_pick, R.string.pick_from_contacts) {
                    renderSenders()
                }
            }
        )
        sheet.addView(chips)
        sheet.addView(
            TextView(this).apply {
                text = getString(R.string.no_senders_picked)
                setTextColor(config.mainTextColor.withAlpha(0.5f))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.8f))
                typeface = typefaceFor(Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = 10.getScaledPx() }
                // Kept in step with the chips by the same pass that renders them.
                chips.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    visibility = if (chosenNumbers.isEmpty()) View.VISIBLE else View.GONE
                }
                visibility = if (chosenNumbers.isEmpty()) View.VISIBLE else View.GONE
            }
        )

        // ---- how it looks ------------------------------------------------------------------
        if (existing != null && onCustomize != null) {
            sheet.addView(
                TextView(this).apply {
                    text = getString(R.string.filter_appearance)
                    setTextColor(config.accentGradientStart)
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.78f))
                    typeface = typefaceFor(Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = 20.getScaledPx() }
                }
            )

            val swatches = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val summary = TextView(this).apply {
                setTextColor(config.mainTextColor.withAlpha(0.58f))
                setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.76f))
                typeface = typefaceFor(Typeface.NORMAL)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }

            fun renderAppearance() {
                val filter = appearance
                swatches.removeAllViews()
                // The swatches are the preview: a row that only said "Colours and sound" made
                // you open the sheet to find out whether anything was set at all.
                listOfNotNull(
                    filter?.sentBubbleColor,
                    filter?.receivedBubbleColor,
                    filter?.backgroundColor,
                ).forEach { colour ->
                    swatches.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            16.getScaledPx(), 16.getScaledPx()
                        ).apply { marginStart = 4.getScaledPx() }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(colour)
                            setStroke(1.getScaledPx(), TextoGlass.rimFor(colour, 0.35f))
                        }
                    })
                }
                summary.text = when {
                    filter?.notificationSoundLabel != null -> filter.notificationSoundLabel
                    filter?.hasAppearanceOverride == true -> getString(R.string.filter_colour_custom)
                    else -> getString(R.string.filter_colour_following)
                }
            }

            sheet.addView(
                sourceRow(R.drawable.ic_ph_swatches, R.string.filter_customize, extra = {
                    it.addView(summary)
                    it.addView(swatches)
                }) {
                    val label = nameField.text.toString().trim()
                    val current = (appearance ?: existing)!!
                    onCustomize.invoke(
                        if (label.isEmpty()) current else current.copy(label = label)
                    ) { saved ->
                        appearance = saved
                        renderAppearance()
                    }
                }
            )
            renderAppearance()
        }

        renderSenders()

        // ---- footer --------------------------------------------------------------------------
        val footer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 20.getScaledPx() }
        }
        if (existing != null && onDelete != null) {
            // Named in the destructive red rather than sitting in the same grey as Cancel,
            // which is what the base theme's neutral button gave it.
            footer.addView(
                footerButton(getString(R.string.delete), filled = false, ink = DESTRUCTIVE_INK) {
                    dialog?.dismiss()
                    onDelete.invoke()
                }
            )
        }
        footer.addView(footerButton(getString(R.string.action_cancel), filled = false) {
            dialog?.dismiss()
        })
        footer.addView(footerButton(getString(R.string.action_confirm), filled = true) {
            val label = nameField.text.toString().trim()
            if (label.isEmpty()) {
                toast(R.string.filter_name_required)
                return@footerButton
            }
            // A filter is defined purely by who it covers now that the keyword field is gone,
            // so at least one sender has to be picked.
            if (chosenNumbers.isEmpty()) {
                toast(R.string.filter_needs_senders)
                return@footerButton
            }
            val base = appearance ?: existing
            val filter = base?.copy(
                label = label,
                senders = chosenNumbers.toList(),
                senderLabels = chosenLabels.toList()
            ) ?: FilterStore.newCustomFilter(
                label = label,
                senders = chosenNumbers.toList(),
                senderLabels = chosenLabels.toList()
            )
            callback(filter)
            dialog?.dismiss()
        })
        sheet.addView(footer)

        dialog = AlertDialog.Builder(this)
            .setView(ScrollView(this).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(sheet)
            })
            .create()
            .apply {
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                show()
            }
    }

    /** Search-filterable, multi-pick list of one source, pre-ticked with what is saved. */
    private fun SimpleActivity.pickSenders(
        source: List<Pair<String, String>>,
        emptyMessage: Int,
        titleRes: Int,
        onChanged: () -> Unit,
    ) {
        if (source.isEmpty()) {
            toast(emptyMessage)
            return
        }
        textoSenderPicker(
            title = getString(titleRes),
            source = source,
            initiallySelected = chosenNumbers,
        ) { numbers, labels ->
            // Only this source's entries are rewritten; anything picked from the other source
            // stays, so a filter can mix chats and contacts.
            source.map { it.second }.forEach { number ->
                val at = chosenNumbers.indexOfFirst { SystemBlockedNumbers.isSameSender(it, number) }
                if (at >= 0) {
                    chosenNumbers.removeAt(at)
                    chosenLabels.removeAt(at)
                }
            }
            numbers.forEachIndexed { index, number ->
                chosenNumbers.add(number)
                chosenLabels.add(labels[index])
            }
            onChanged()
        }
    }

    /** One tappable row: an icon, a label, and whatever the caller wants on the trailing end. */
    private fun SimpleActivity.sourceRow(
        iconRes: Int,
        titleRes: Int,
        extra: ((LinearLayout) -> Unit)? = null,
        onTap: () -> Unit,
    ): View {
        val density = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = TextoGlass.bar(
                tint = config.mainBackgroundColor,
                cornerRadius = 18 * density,
                opacity = 0.5f,
                strokeWidthPx = 1.getScaledPx(),
                rimAlpha = 0.18f
            )
            outlineProvider = ViewOutlineProvider.BACKGROUND
            val padH = 14.getScaledPx()
            val padV = 13.getScaledPx()
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 10.getScaledPx() }

            addView(ImageView(this@sourceRow).apply {
                setImageResource(iconRes)
                setColorFilter(config.mainTextColor.withAlpha(0.75f))
                layoutParams = LinearLayout.LayoutParams(
                    20.getScaledPx(), 20.getScaledPx()
                ).apply { marginEnd = 12.getScaledPx() }
            })
            addView(TextView(this@sourceRow).apply {
                text = getString(titleRes)
                setTextColor(config.mainTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.88f))
                typeface = typefaceFor(Typeface.NORMAL)
                layoutParams = LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
                )
            })
            extra?.invoke(this)
            setOnClickListener { onTap() }
        }
    }

    private fun SimpleActivity.footerButton(
        label: String,
        filled: Boolean,
        ink: Int? = null,
        onTap: () -> Unit,
    ): TextView {
        val density = resources.displayMetrics.density
        return TextView(this).apply {
            text = label
            gravity = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.86f))
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

    private fun SimpleActivity.sheetGround(): GradientDrawable {
        val density = resources.displayMetrics.density
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 26 * density
            setColor(config.recentColor)
            setStroke(1.getScaledPx(), TextoGlass.rimFor(config.recentColor, 0.18f))
        }
    }

}
