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
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.extensions.TextoCalendar
import com.texto.sms.extensions.config
import com.texto.sms.extensions.toUiDigits
import com.texto.sms.extensions.applyColorFilter
import com.texto.sms.extensions.withAlpha
import java.util.Calendar

/**
 * A month grid for picking a date range, on whichever calendar the app is speaking:
 * Jalali under Persian, Gregorian under English.
 *
 * Android ships no Persian calendar and the platform `DatePickerDialog` is Gregorian: the
 * search filter used to open two of those back to back and only *label* the result in Jalali,
 * which asked the user to convert dates in their head. This draws the months the app already
 * speaks, in the same card, capsule and accent every other screen uses.
 *
 * Two taps set the range: the first starts it, the second closes it, a third starts over.
 */
class JalaliRangePicker(private val activity: SimpleActivity) {

    private data class Ymd(val y: Int, val m: Int, val d: Int) : Comparable<Ymd> {
        override fun compareTo(other: Ymd): Int =
            compareValuesBy(this, other, { it.y }, { it.m }, { it.d })
    }

    private var viewYear = 0
    private var viewMonth = 0
    private var from: Ymd? = null
    private var to: Ymd? = null

    /**
     * True while the sheet is showing the twelve months of [viewYear] instead of the days of
     * [viewMonth]. One grid serves both, so this is what every renderer and the two header
     * arrows branch on.
     */
    private var pickingMonth = false

    private lateinit var grid: GridLayout
    private lateinit var weekdays: LinearLayout
    private lateinit var monthCaret: ImageView
    private lateinit var monthLabel: TextView
    private lateinit var rangeLabel: TextView
    private lateinit var confirm: TextView

    fun show(onPicked: (startMillis: Long, endMillis: Long) -> Unit) = with(activity) {
        val density = resources.displayMetrics.density
        val today = Calendar.getInstance()
        val nowJalali = TextoCalendar.fromCivil(
            today.get(Calendar.YEAR),
            today.get(Calendar.MONTH) + 1,
            today.get(Calendar.DAY_OF_MONTH)
        )
        viewYear = nowJalali.year
        viewMonth = nowJalali.month

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
                text = getString(R.string.search_date_custom)
                setTextColor(config.mainTextColor)
                setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(1.05f))
                typeface = typefaceFor(Typeface.BOLD)
                setPadding(8.getScaledPx(), 0, 8.getScaledPx(), 10.getScaledPx())
            }
        )

        // Month bar. "Previous month" is added first, so it lands on the row's start --
        // the right under Persian, the left under English -- and each caret is turned to
        // point the way that button actually moves the calendar.
        // Layout direction, taken here rather than in monthArrow: an arrow is only ever
        // built as part of this row.
        val isRtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(monthArrow(-1, isRtl))

        // The label is a button, not a caption. Stepping one month at a time was the only
        // way to move: reaching a date a year back took twelve taps of the same arrow, and
        // there was no way at all to change the year directly. Tapping it swaps the day grid
        // for the twelve months, and the arrows then step years.
        monthLabel = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(config.mainTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.95f))
            typeface = typefaceFor(Typeface.BOLD)
        }
        monthCaret = ImageView(this).apply {
            val side = 14.getScaledPx()
            layoutParams = LinearLayout.LayoutParams(side, side).apply {
                marginStart = 5.getScaledPx()
            }
            setImageResource(R.drawable.ic_ph_caret_left)
            // Only a left caret is in the drawables. Turned a quarter anticlockwise it points
            // down, which is what marks the label as opening something; in month mode it is
            // turned again to point up, for the way back.
            rotation = -90f
            applyColorFilter(config.mainTextColor.withAlpha(0.75f))
        }
        val labelBox = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            isClickable = true
            val padV = 6.getScaledPx()
            setPadding(0, padV, 0, padV)
            addView(monthLabel)
            addView(monthCaret)
            setOnClickListener {
                pickingMonth = !pickingMonth
                renderMonth()
            }
        }
        header.addView(labelBox)
        header.addView(monthArrow(+1, isRtl))
        sheet.addView(header)

        weekdays = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10.getScaledPx(), 0, 4.getScaledPx())
        }
        TextoCalendar.weekdayInitials.forEach { name ->
            weekdays.addView(
                TextView(this).apply {
                    text = name
                    gravity = Gravity.CENTER
                    setTextColor(config.mainTextColor.withAlpha(0.5f))
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.72f))
                    typeface = typefaceFor(Typeface.NORMAL)
                    layoutParams =
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
            )
        }
        sheet.addView(weekdays)

        grid = GridLayout(this).apply {
            columnCount = COLUMNS
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        sheet.addView(grid)

        rangeLabel = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(config.mainTextColor.withAlpha(0.68f))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.78f))
            typeface = typefaceFor(Typeface.NORMAL)
            setPadding(0, 12.getScaledPx(), 0, 10.getScaledPx())
        }
        sheet.addView(rangeLabel)

        var dialog: AlertDialog? = null

        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        confirm = capsuleButton(getString(R.string.action_confirm), filled = true) {
            val start = from ?: return@capsuleButton
            onPicked(start.toMillis(), (to ?: start).toMillis())
            dialog?.dismiss()
        }
        buttons.addView(confirm)
        buttons.addView(
            capsuleButton(getString(R.string.action_cancel), filled = false) {
                dialog?.dismiss()
            }
        )
        sheet.addView(buttons)

        renderMonth()

        dialog = AlertDialog.Builder(this)
            .setView(sheet)
            .create()
            .apply {
                // The sheet paints its own rounded card; the platform's opaque one behind it
                // would put a square light panel around every corner.
                window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                show()
            }
        Unit
    }

    private fun Ymd.toMillis(): Long = TextoCalendar.millisOf(y, m, d)

    private fun monthArrow(delta: Int, isRtl: Boolean): View = with(activity) {
        val side = 34.getScaledPx()
        ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(side, side)
            val pad = 8.getScaledPx()
            setPadding(pad, pad, pad, pad)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.ic_ph_caret_left)
            // Only one caret is in the drawables, so the other direction is the same glyph
            // turned around rather than a second near-identical asset. Which of the two
            // gets turned depends on the layout direction: the button that steps back sits
            // on the right under RTL and on the left under LTR, and has to point the way it
            // sits. Hard-coded to the RTL case, the English picker's carets both pointed
            // outwards from the month name, away from the months they move to.
            rotation = if ((delta < 0) == isRtl) 180f else 0f
            applyColorFilter(config.mainTextColor.withAlpha(0.75f))
            isClickable = true
            setOnClickListener {
                // Same two buttons, two strides: a month while the days are up, a year while
                // the months are. Reading the mode here rather than rebuilding the arrows
                // keeps the header from being torn down on every toggle.
                if (pickingMonth) {
                    viewYear += delta
                } else {
                    viewMonth += delta
                    if (viewMonth < 1) {
                        viewMonth = 12
                        viewYear--
                    } else if (viewMonth > 12) {
                        viewMonth = 1
                        viewYear++
                    }
                }
                renderMonth()
            }
        }
    }

    private fun capsuleButton(label: String, filled: Boolean, onTap: () -> Unit): TextView =
        with(activity) {
            val density = resources.displayMetrics.density
            TextView(this).apply {
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

    /** Which column a day lands in; the week starts on Saturday in Persian, Sunday in English. */
    private fun columnOf(y: Int, m: Int, d: Int): Int = TextoCalendar.firstColumnOf(y, m, d)

    private fun renderMonth(): Unit = with(activity) {
        monthCaret.rotation = if (pickingMonth) 90f else -90f
        weekdays.visibility = if (pickingMonth) View.GONE else View.VISIBLE

        if (pickingMonth) {
            monthLabel.text = viewYear.toUiDigits()
            renderMonthChooser()
            return@with
        }

        monthLabel.text = "${TextoCalendar.monthNames[viewMonth - 1]} ${viewYear.toUiDigits()}"

        // Emptied before the count changes, not after: GridLayout validates a new columnCount
        // against the children already in it, so setting 7 while the twelve month cells are
        // still attached throws.
        grid.removeAllViews()
        grid.columnCount = COLUMNS
        val cellSide = 38.getScaledPx()

        repeat(columnOf(viewYear, viewMonth, 1)) {
            grid.addView(
                View(this).apply {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        height = cellSide
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    }
                }
            )
        }

        for (day in 1..TextoCalendar.daysInMonth(viewYear, viewMonth)) {
            val ymd = Ymd(viewYear, viewMonth, day)
            val start = from
            val end = to
            val isEdge = ymd == start || ymd == end
            val isInside = start != null && end != null && ymd > start && ymd < end

            grid.addView(
                TextView(this).apply {
                    text = day.toUiDigits()
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.82f))
                    typeface = typefaceFor(if (isEdge) Typeface.BOLD else Typeface.NORMAL)
                    setTextColor(
                        when {
                            isEdge -> config.accentInkColor
                            isInside -> config.accentGradientStart
                            else -> config.mainTextColor
                        }
                    )
                    background = when {
                        isEdge -> TextoGlass.accent(
                            start = config.accentGradientStart,
                            end = config.accentGradientEnd,
                            cornerRadius = 100f * resources.displayMetrics.density,
                            mid = config.accentGradientMid
                        )
                        // The span between the ends is a flat wash rather than a capsule, so
                        // the two picked days stay the only things reading as selected.
                        isInside -> GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 8 * resources.displayMetrics.density
                            setColor(config.accentGradientStart.withAlpha(0.16f))
                        }
                        else -> null
                    }
                    isClickable = true
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        height = cellSide
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        val gap = 2.getScaledPx()
                        setMargins(gap, gap, gap, gap)
                    }
                    setOnClickListener { onDayTapped(ymd) }
                }
            )
        }

        renderRangeLabel()
    }

    /**
     * The twelve months of [viewYear], three to a row, in the same grid the days use. Picking
     * one drops straight back to that month's days: the year is already set by the header
     * arrows, so any month of any year is three taps from any other.
     *
     * The months of a year that is already part of the picked range are marked, so the range
     * stays visible while navigating away from it.
     */
    private fun renderMonthChooser(): Unit = with(activity) {
        // Emptied first, for the reason given in renderMonth.
        grid.removeAllViews()
        grid.columnCount = MONTH_COLUMNS

        val start = from
        val end = to
        TextoCalendar.monthNames.forEachIndexed { index, name ->
            val month = index + 1
            val touched = listOfNotNull(start, end).any { it.y == viewYear && it.m == month }
            val isCurrent = month == viewMonth

            grid.addView(
                TextView(this).apply {
                    text = name
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.82f))
                    typeface = typefaceFor(if (touched || isCurrent) Typeface.BOLD else Typeface.NORMAL)
                    setTextColor(
                        when {
                            touched -> config.accentInkColor
                            isCurrent -> config.accentGradientStart
                            else -> config.mainTextColor
                        }
                    )
                    background = when {
                        touched -> TextoGlass.accent(
                            start = config.accentGradientStart,
                            end = config.accentGradientEnd,
                            cornerRadius = 100f * resources.displayMetrics.density,
                            mid = config.accentGradientMid
                        )
                        isCurrent -> GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = 100f * resources.displayMetrics.density
                            setColor(config.accentGradientStart.withAlpha(0.16f))
                        }
                        else -> null
                    }
                    isClickable = true
                    val padV = 12.getScaledPx()
                    setPadding(0, padV, 0, padV)
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = 0
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        val gap = 4.getScaledPx()
                        setMargins(gap, gap, gap, gap)
                    }
                    setOnClickListener {
                        viewMonth = month
                        pickingMonth = false
                        renderMonth()
                    }
                }
            )
        }

        renderRangeLabel()
    }

    private fun onDayTapped(ymd: Ymd) {
        val start = from
        when {
            // Nothing picked yet, or a finished range already sitting there: start over.
            start == null || to != null -> {
                from = ymd
                to = null
            }
            ymd < start -> {
                from = ymd
                to = start
            }
            else -> to = ymd
        }
        renderMonth()
    }

    private fun renderRangeLabel(): Unit = with(activity) {
        val start = from
        confirm.alpha = if (start == null) 0.4f else 1f
        confirm.isClickable = start != null
        rangeLabel.text = when {
            start == null -> getString(R.string.search_date_pick_hint)
            to == null -> getString(R.string.search_date_pick_end, start.text())
            else -> getString(R.string.search_date_range, start.text(), to!!.text())
        }
    }

    private fun Ymd.text() =
        TextoCalendar.dateText(y, m, d)

    private companion object {
        const val COLUMNS = 7

        /** Three months to a row: twelve names fit without any of them wrapping. */
        const val MONTH_COLUMNS = 3
    }
}
