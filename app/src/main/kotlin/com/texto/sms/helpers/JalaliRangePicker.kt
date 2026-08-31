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
import com.texto.sms.extensions.JALALI_MONTH_NAMES
import com.texto.sms.extensions.config
import com.texto.sms.extensions.daysInJalaliMonth
import com.texto.sms.extensions.gregorianToJalali
import com.texto.sms.extensions.jalaliToGregorian
import com.texto.sms.extensions.toPersianDigits
import org.fossify.commons.extensions.applyColorFilter
import com.texto.sms.extensions.withAlpha
import java.util.Calendar

/**
 * A Jalali month grid for picking a date range.
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

    private lateinit var grid: GridLayout
    private lateinit var monthLabel: TextView
    private lateinit var rangeLabel: TextView
    private lateinit var confirm: TextView

    fun show(onPicked: (startMillis: Long, endMillis: Long) -> Unit) = with(activity) {
        val density = resources.displayMetrics.density
        val today = Calendar.getInstance()
        val nowJalali = gregorianToJalali(
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

        // Month bar. Under RTL the first child lands on the right, so "previous month" is
        // added first and the arrows point the way the calendar actually moves.
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(monthArrow(-1))
        monthLabel = TextView(this).apply {
            gravity = Gravity.CENTER
            setTextColor(config.mainTextColor)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.95f))
            typeface = typefaceFor(Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        header.addView(monthLabel)
        header.addView(monthArrow(+1))
        sheet.addView(header)

        val weekdays = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10.getScaledPx(), 0, 4.getScaledPx())
        }
        listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach { name ->
            weekdays.addView(
                TextView(this).apply {
                    text = name
                    gravity = Gravity.CENTER
                    setTextColor(config.mainTextColor.withAlpha(0.5f))
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.72f))
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

    private fun Ymd.toMillis(): Long {
        val gregorian = jalaliToGregorian(y, m, d)
        return Calendar.getInstance().apply {
            clear()
            set(gregorian.year, gregorian.month - 1, gregorian.day)
        }.timeInMillis
    }

    private fun monthArrow(delta: Int): View = with(activity) {
        val side = 34.getScaledPx()
        ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(side, side)
            val pad = 8.getScaledPx()
            setPadding(pad, pad, pad, pad)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageResource(R.drawable.ic_ph_caret_left)
            // Only one caret is in the drawables, so the other direction is the same glyph
            // turned around rather than a second near-identical asset.
            rotation = if (delta < 0) 180f else 0f
            applyColorFilter(config.mainTextColor.withAlpha(0.75f))
            isClickable = true
            setOnClickListener {
                viewMonth += delta
                if (viewMonth < 1) {
                    viewMonth = 12
                    viewYear--
                } else if (viewMonth > 12) {
                    viewMonth = 1
                    viewYear++
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
                setTextColor(if (filled) config.sentBubbleTextColor else config.mainTextColor)
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

    /** Which column a Jalali day lands in, with the week starting on Saturday. */
    private fun columnOf(y: Int, m: Int, d: Int): Int {
        val gregorian = jalaliToGregorian(y, m, d)
        val cal = Calendar.getInstance().apply {
            clear()
            set(gregorian.year, gregorian.month - 1, gregorian.day)
        }
        // SATURDAY is 7 and SUNDAY is 1, so the modulo lands Saturday on column 0.
        return cal.get(Calendar.DAY_OF_WEEK) % COLUMNS
    }

    private fun renderMonth() = with(activity) {
        monthLabel.text = "${JALALI_MONTH_NAMES[viewMonth - 1]} ${viewYear.toPersianDigits()}"

        grid.removeAllViews()
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

        for (day in 1..daysInJalaliMonth(viewYear, viewMonth)) {
            val ymd = Ymd(viewYear, viewMonth, day)
            val start = from
            val end = to
            val isEdge = ymd == start || ymd == end
            val isInside = start != null && end != null && ymd > start && ymd < end

            grid.addView(
                TextView(this).apply {
                    text = day.toPersianDigits()
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, getScaledTextSize(0.82f))
                    typeface = typefaceFor(if (isEdge) Typeface.BOLD else Typeface.NORMAL)
                    setTextColor(
                        when {
                            isEdge -> config.sentBubbleTextColor
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

    private fun renderRangeLabel() = with(activity) {
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
        "${d.toPersianDigits()} ${JALALI_MONTH_NAMES[m - 1]} ${y.toPersianDigits()}"

    private companion object {
        const val COLUMNS = 7
    }
}
