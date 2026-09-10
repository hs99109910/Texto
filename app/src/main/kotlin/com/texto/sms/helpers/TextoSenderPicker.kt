package com.texto.sms.helpers

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.extensions.config

/**
 * One list of pickable senders (chats or contacts), built to read as the rest of the app's
 * sheets rather than a bare `AlertDialog` wrapped around a native checkbox `ListView` -- which
 * is what a filter's two source pickers opened as before this, the one pair of windows in the
 * whole "add filter" flow that still wore the base theme's white card and system typeface.
 *
 * Each row carries a plus that becomes a check on tap, rather than a checkbox, so the state is
 * the same glyph the rest of the app already uses for "chosen". [selected] is read once to seed
 * the sheet and never written to directly -- everything here works on its own copy, and only
 * [onConfirm] hands the final set back, so a picker opened and then cancelled costs nothing.
 *
 * Picking survives the search box narrowing the rows, because it is tracked by sender number
 * against the *full* list rather than by adapter position -- position is what the old picker
 * kept, and a row's position changes the moment a search narrows the list under it.
 *
 * The people already picked also sit in their own row under the search field, each one a chip
 * with its own close glyph, so removing someone does not require finding their row again in a
 * list that may have scrolled past them.
 */
fun SimpleActivity.textoSenderPicker(
    title: String,
    source: List<Pair<String, String>>,
    initiallySelected: List<String>,
    onConfirm: (numbers: List<String>, labels: List<String>) -> Unit,
) {
    val density = resources.displayMetrics.density

    // Keyed by number rather than list position -- see the note above.
    val selected = LinkedHashSet<String>()
    source.forEach { (_, number) ->
        if (initiallySelected.any { SystemBlockedNumbers.isSameSender(it, number) }) {
            selected.add(number)
        }
    }

    var visible = source
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
            text = title
            setTextColor(config.mainTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.05f))
            typeface = typefaceFor(Typeface.BOLD)
            gravity = Gravity.START
            setPadding(0, 0, 0, 14.getScaledPx())
        }
    )

    // ---- search --------------------------------------------------------------------------
    val search = EditText(this).apply {
        hint = getString(R.string.search_senders_hint)
        setHintTextColor(config.mainTextColor.withAlpha(0.42f))
        setTextColor(config.mainTextColor)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.92f))
        typeface = typefaceFor(Typeface.NORMAL)
        maxLines = 1
        isSingleLine = true
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 100f * density
            setColor(config.recentColor)
            setStroke(1.getScaledPx(), TextoGlass.rimFor(config.recentColor, 0.20f))
        }
        val padH = 16.getScaledPx()
        val padV = 10.getScaledPx()
        setPadding(padH, padV, padH, padV)
    }
    sheet.addView(search)

    // ---- chips: who is picked so far ------------------------------------------------------
    val chipRow = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }
    val chipScroll = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12.getScaledPx() }
        addView(chipRow)
    }
    sheet.addView(chipScroll)

    // ---- the list ---------------------------------------------------------------------------
    val list = RecyclerView(this).apply {
        layoutManager = LinearLayoutManager(this@textoSenderPicker)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, (320 * density).toInt()
        ).apply { topMargin = 12.getScaledPx() }
        overScrollMode = View.OVER_SCROLL_NEVER
    }
    sheet.addView(list)

    val emptyLabel = TextView(this).apply {
        setTextColor(config.mainTextColor.withAlpha(0.5f))
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.88f))
        typeface = typefaceFor(Typeface.NORMAL)
        gravity = Gravity.CENTER
        visibility = View.GONE
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 12.getScaledPx() }
    }
    sheet.addView(emptyLabel)

    fun renderChips() {
        chipRow.removeAllViews()
        selected.forEach { number ->
            val label = source.firstOrNull { SystemBlockedNumbers.isSameSender(it.second, number) }?.first
                ?: number
            chipRow.addView(senderChip(label) {
                selected.remove(number)
                renderChips()
                list.adapter?.notifyDataSetChanged()
            })
        }
    }

    val adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        inner class Holder(val row: LinearLayout, val label: TextView, val icon: ImageView) :
            RecyclerView.ViewHolder(row)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                val padH = 4.getScaledPx()
                val padV = 12.getScaledPx()
                setPadding(padH, padV, padH, padV)
            }
            val label = TextView(parent.context).apply {
                setTextColor(config.mainTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.92f))
                typeface = typefaceFor(Typeface.NORMAL)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val icon = ImageView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(22.getScaledPx(), 22.getScaledPx()).apply {
                    marginStart = 12.getScaledPx()
                }
            }
            row.addView(label)
            row.addView(icon)
            return Holder(row, label, icon)
        }

        override fun getItemCount() = visible.size

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            holder as Holder
            val (label, number) = visible[position]
            holder.label.text = label
            val isChosen = selected.any { SystemBlockedNumbers.isSameSender(it, number) }
            holder.icon.setImageResource(if (isChosen) R.drawable.ic_ph_check else R.drawable.ic_ph_plus)
            holder.icon.setColorFilter(
                if (isChosen) config.accentGradientStart else config.mainTextColor.withAlpha(0.45f)
            )
            holder.row.setOnClickListener {
                if (isChosen) {
                    selected.removeAll { SystemBlockedNumbers.isSameSender(it, number) }
                } else {
                    selected.add(number)
                }
                notifyItemChanged(position)
                renderChips()
            }
        }
    }
    list.adapter = adapter

    search.addTextChangedListener(object : TextWatcher {
        override fun afterTextChanged(s: Editable?) {
            val query = s?.toString()?.trim().orEmpty()
            visible = if (query.isEmpty()) {
                source
            } else {
                source.filter { (label, number) ->
                    label.contains(query, ignoreCase = true) || number.contains(query, ignoreCase = true)
                }
            }
            emptyLabel.visibility = if (visible.isEmpty()) View.VISIBLE else View.GONE
            emptyLabel.text = getString(R.string.no_senders_picked)
            adapter.notifyDataSetChanged()
        }
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    })

    renderChips()

    // ---- footer ----------------------------------------------------------------------------
    val footer = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 18.getScaledPx() }
    }
    footer.addView(pickerButton(getString(R.string.action_cancel), filled = false) {
        dialog?.dismiss()
    })
    footer.addView(pickerButton(getString(R.string.action_confirm), filled = true) {
        val numbers = source.filter { (_, number) -> selected.contains(number) }
        onConfirm(numbers.map { it.second }, numbers.map { it.first })
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

/** A single "picked" pill: the name, and a close glyph that removes it on its own. */
fun SimpleActivity.senderChip(label: String, onRemove: () -> Unit): View {
    val density = resources.displayMetrics.density
    val row = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        background = TextoGlass.bar(
            tint = config.accentGradientStart,
            cornerRadius = 100f * density,
            opacity = 0.18f,
            strokeWidthPx = 1.getScaledPx(),
            rimAlpha = 0.30f
        )
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true
        val padH = 12.getScaledPx()
        val padV = 6.getScaledPx()
        setPadding(padH, padV, padH, padV)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { marginEnd = 8.getScaledPx() }
    }
    row.addView(TextView(this).apply {
        text = label
        setTextColor(config.mainTextColor)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.84f))
        typeface = typefaceFor(Typeface.NORMAL)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            marginEnd = 6.getScaledPx()
        }
    })
    row.addView(ImageView(this).apply {
        setImageResource(R.drawable.ic_ph_x)
        setColorFilter(config.mainTextColor.withAlpha(0.7f))
        layoutParams = LinearLayout.LayoutParams(16.getScaledPx(), 16.getScaledPx())
        isClickable = true
        setOnClickListener { onRemove() }
    })
    return row
}
