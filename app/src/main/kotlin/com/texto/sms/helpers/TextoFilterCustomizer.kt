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
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.extensions.config

/**
 * One filter's own look and sound: the colours its threads are drawn in, and what arriving
 * messages from its senders sound like.
 *
 * Every colour here is an *override*, not a value. Null means the thread follows the app's own
 * setting, which is what a filter that has never been opened here does, so this sheet only ever
 * adds exceptions rather than forking the theme -- and "Clear" on a row puts that one back
 * without touching the others. That is also why each row previews the colour it would use
 * rather than the colour it holds: a row that is still following the app has nothing of its
 * own to show, and showing an empty swatch there says "unset" far less clearly than showing
 * what you would actually get.
 *
 * The sound is picked by the caller, not here -- a ringtone picker is an activity result and
 * this is a dialog. [onPickSound] hands that back out to whoever opened the sheet.
 */
fun SimpleActivity.textoFilterCustomizer(
    filter: MessageFilter,
    onPickSound: (current: String?, onPicked: (uri: String?, label: String?) -> Unit) -> Unit,
    onSave: (MessageFilter) -> Unit,
) {
    val density = resources.displayMetrics.density
    var working = filter
    var dialog: AlertDialog? = null

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
            text = getString(R.string.filter_customize_title, filter.label)
            setTextColor(config.mainTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.05f))
            typeface = typefaceFor(Typeface.BOLD)
            setPadding(0, 0, 0, 4.getScaledPx())
        }
    )
    sheet.addView(
        TextView(this).apply {
            text = getString(R.string.filter_customize_subtitle)
            setTextColor(config.mainTextColor.withAlpha(0.58f))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.78f))
            typeface = typefaceFor(Typeface.NORMAL)
            setPadding(0, 0, 0, 14.getScaledPx())
        }
    )

    val rows = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    sheet.addView(rows)

    val soundRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isClickable = true
        val padV = 12.getScaledPx()
        setPadding(0, padV, 0, padV)
    }
    val soundValue = TextView(this).apply {
        setTextColor(config.mainTextColor.withAlpha(0.72f))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.82f))
        typeface = typefaceFor(Typeface.NORMAL)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }

    /**
     * One colour row. [read] pulls this row's override off the working copy and [write] puts
     * a new one back, so every row is the same three lines with two accessors swapped.
     */
    fun colourRow(
        titleRes: Int,
        read: (MessageFilter) -> Int?,
        write: (MessageFilter, Int?) -> MessageFilter,
        appDefault: () -> Int,
        contrastAgainst: () -> Int,
    ): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            val padV = 10.getScaledPx()
            setPadding(0, padV, 0, padV)
        }
        val title = TextView(this).apply {
            text = getString(titleRes)
            setTextColor(config.mainTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.88f))
            typeface = typefaceFor(Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val state = TextView(this).apply {
            setTextColor(config.mainTextColor.withAlpha(0.55f))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.76f))
            typeface = typefaceFor(Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 10.getScaledPx() }
        }
        val swatch = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(30.getScaledPx(), 30.getScaledPx())
        }

        fun render() {
            val override = read(working)
            val shown = override ?: appDefault()
            state.text = getString(
                if (override == null) R.string.filter_colour_following else R.string.filter_colour_custom
            )
            swatch.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10 * density
                setColor(shown)
                setStroke(
                    if (override == null) 1.getScaledPx() else (2 * density).toInt(),
                    if (override == null) {
                        TextoGlass.rimFor(shown, 0.30f)
                    } else {
                        config.accentGradientStart
                    }
                )
            }
        }

        row.setOnClickListener {
            textoColorPicker(
                title = getString(titleRes),
                current = read(working) ?: appDefault(),
                defaultColour = appDefault(),
                contrastAgainst = contrastAgainst(),
            ) { picked ->
                // Picking the app's own value back is how a row is cleared: there is nothing
                // to override once the two agree, and storing it anyway would freeze this
                // filter on today's theme while the rest of the app follows a later one.
                working = write(working, if (picked == appDefault()) null else picked)
                render()
            }
        }
        row.addView(title)
        row.addView(state)
        row.addView(swatch)
        render()
        return row
    }

    rows.addView(
        colourRow(
            R.string.settings_sent_bubble_color,
            { it.sentBubbleColor },
            { f, v -> f.copy(sentBubbleColor = v) },
            { config.sentBubbleColor },
            { working.sentBubbleTextColor ?: config.sentBubbleTextColor },
        )
    )
    rows.addView(
        colourRow(
            R.string.settings_sent_bubble_text,
            { it.sentBubbleTextColor },
            { f, v -> f.copy(sentBubbleTextColor = v) },
            { config.sentBubbleTextColor },
            { working.sentBubbleColor ?: config.sentBubbleColor },
        )
    )
    rows.addView(
        colourRow(
            R.string.settings_received_bubble_color,
            { it.receivedBubbleColor },
            { f, v -> f.copy(receivedBubbleColor = v) },
            { config.receivedBubbleColor },
            { working.receivedBubbleTextColor ?: config.receivedBubbleTextColor },
        )
    )
    rows.addView(
        colourRow(
            R.string.settings_received_bubble_text,
            { it.receivedBubbleTextColor },
            { f, v -> f.copy(receivedBubbleTextColor = v) },
            { config.receivedBubbleTextColor },
            { working.receivedBubbleColor ?: config.receivedBubbleColor },
        )
    )
    rows.addView(
        colourRow(
            R.string.filter_colour_background,
            { it.backgroundColor },
            { f, v -> f.copy(backgroundColor = v) },
            { config.mainBackgroundColor },
            { config.mainTextColor },
        )
    )

    // ---- the sound -------------------------------------------------------------------------
    fun renderSound() {
        soundValue.text = working.notificationSoundLabel
            ?: getString(R.string.filter_sound_following)
    }
    soundRow.addView(
        TextView(this).apply {
            text = getString(R.string.filter_sound)
            setTextColor(config.mainTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.88f))
            typeface = typefaceFor(Typeface.NORMAL)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
    )
    soundRow.addView(soundValue)
    soundRow.setOnClickListener {
        onPickSound(working.notificationSoundUri) { uri, label ->
            working = working.copy(notificationSoundUri = uri, notificationSoundLabel = label)
            renderSound()
        }
    }
    renderSound()

    sheet.addView(
        View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1.getScaledPx()
            ).apply {
                topMargin = 8.getScaledPx()
                bottomMargin = 4.getScaledPx()
            }
            setBackgroundColor(TextoGlass.rimFor(config.recentColor, 0.30f))
        }
    )
    sheet.addView(soundRow)

    // ---- footer ----------------------------------------------------------------------------
    val footer = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 18.getScaledPx() }
    }
    footer.addView(pickerButton(getString(R.string.filter_clear_customization), filled = false) {
        working = working.copy(
            sentBubbleColor = null,
            sentBubbleTextColor = null,
            receivedBubbleColor = null,
            receivedBubbleTextColor = null,
            backgroundColor = null,
            notificationSoundUri = null,
            notificationSoundLabel = null,
        )
        onSave(working)
        dialog?.dismiss()
    })
    footer.addView(pickerButton(getString(R.string.colour_save), filled = true) {
        onSave(working)
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
