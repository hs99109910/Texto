package com.texto.sms.dialogs

import com.texto.sms.extensions.config
import com.texto.sms.extensions.withAlpha
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.setupDialogStuff
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.databinding.PersianDatePickerDialogBinding
import com.texto.sms.helpers.applyTextoDialogSkin
import com.texto.sms.extensions.TextoCalendar
import com.texto.sms.extensions.toUiDigits
import org.joda.time.DateTime

/**
 * Lets the user pick a date on whichever calendar the app is speaking -- Jalali under
 * Persian, Gregorian under English -- plus one-tap "tomorrow" / "day after tomorrow"
 * shortcuts. Delivers the choice back as a Gregorian (year, month 0-based, day) triple,
 * matching [android.app.DatePickerDialog.OnDateSetListener], so callers never deal with the
 * display calendar themselves.
 */
class PersianDatePickerDialog(
    private val activity: BaseSimpleActivity,
    initialDateTime: DateTime,
    private val minDateTime: DateTime,
    private val onDateSet: (year: Int, month: Int, day: Int) -> Unit
) {
    private val binding = PersianDatePickerDialogBinding.inflate(activity.layoutInflater)
    private val minJalali = TextoCalendar.fromCivil(minDateTime.year, minDateTime.monthOfYear, minDateTime.dayOfMonth)

    private val initialJalali = run {
        val jalali = TextoCalendar.fromCivil(initialDateTime.year, initialDateTime.monthOfYear, initialDateTime.dayOfMonth)
        if (isBeforeMin(jalali.year, jalali.month, jalali.day)) minJalali else jalali
    }

    private var dialog: AlertDialog? = null

    init {
        val textColor = activity.config.mainTextColor
        val primaryColor = activity.config.accentGradientStart

        binding.customDateLabel.setTextColor(textColor)

        arrayOf(binding.quickPickTomorrow, binding.quickPickDayAfterTomorrow).forEach {
            it.setTextColor(primaryColor)
            it.background = buildQuickPickBackground(primaryColor)
        }

        setupPickers()

        binding.quickPickTomorrow.setOnClickListener { pickQuickDate(daysFromNow = 1) }
        binding.quickPickDayAfterTomorrow.setOnClickListener { pickQuickDate(daysFromNow = 2) }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.action_confirm, null)
            .setNegativeButton(R.string.action_cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.select_date) { alertDialog ->
                    dialog = alertDialog
                    (activity as? SimpleActivity)?.applyTextoDialogSkin(alertDialog)
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val gregorian = TextoCalendar.toCivil(
                            binding.yearPicker.value,
                            binding.monthPicker.value,
                            binding.dayPicker.value
                        )
                        onDateSet(gregorian.year, gregorian.month - 1, gregorian.day)
                        alertDialog.dismiss()
                    }
                }
            }
    }

    private fun pickQuickDate(daysFromNow: Int) {
        val target = minDateTime.plusDays(daysFromNow)
        onDateSet(target.year, target.monthOfYear - 1, target.dayOfMonth)
        dialog?.dismiss()
    }

    private fun isBeforeMin(year: Int, month: Int, day: Int): Boolean {
        if (year != minJalali.year) return year < minJalali.year
        if (month != minJalali.month) return month < minJalali.month
        return day < minJalali.day
    }

    private fun setupPickers() {
        val maxJalaliYear = minJalali.year + 5
        configurePicker(
            binding.yearPicker,
            minJalali.year,
            maxJalaliYear,
            (minJalali.year..maxJalaliYear).map { it.toUiDigits() }.toTypedArray()
        )
        binding.yearPicker.value = initialJalali.year

        // Bootstrap month/day with the widest possible range so the `value` assignments below are
        // always legal; refreshDependentPickers() immediately narrows them to the real min/max.
        configurePicker(binding.monthPicker, 1, 12, TextoCalendar.monthNames)
        binding.monthPicker.value = initialJalali.month

        configurePicker(binding.dayPicker, 1, 31, (1..31).map { it.toUiDigits() }.toTypedArray())
        binding.dayPicker.value = initialJalali.day

        refreshDependentPickers()

        binding.yearPicker.setOnValueChangedListener { _, _, _ -> refreshDependentPickers() }
        binding.monthPicker.setOnValueChangedListener { _, _, _ -> refreshDependentPickers() }
    }

    /** Keeps the month/day pickers from ever landing on a date before [minJalali]. */
    private fun refreshDependentPickers() {
        val selectedYear = binding.yearPicker.value

        val monthMin = if (selectedYear == minJalali.year) minJalali.month else 1
        if (binding.monthPicker.value < monthMin) {
            binding.monthPicker.value = monthMin
        }
        configurePicker(binding.monthPicker, monthMin, 12, TextoCalendar.monthNames.copyOfRange(monthMin - 1, 12))

        val selectedMonth = binding.monthPicker.value
        val dayMin = if (selectedYear == minJalali.year && selectedMonth == minJalali.month) minJalali.day else 1
        val dayMax = TextoCalendar.daysInMonth(selectedYear, selectedMonth)
        if (binding.dayPicker.value < dayMin) {
            binding.dayPicker.value = dayMin
        } else if (binding.dayPicker.value > dayMax) {
            binding.dayPicker.value = dayMax
        }
        configurePicker(binding.dayPicker, dayMin, dayMax, (dayMin..dayMax).map { it.toUiDigits() }.toTypedArray())
    }

    /** Changes a NumberPicker's range/labels without hitting the "min must stay <= max" pitfall. */
    /**
     * Paints the wheel from the live theme.
     *
     * NumberPicker draws its rows with the *base* theme's primary text colour and offers no
     * setter for it before Android 10, so on this app's dark skins every row but the selected
     * one was dark ink on a dark sheet -- the wheel looked empty and there was no way to see
     * what scrolling past the current value would land on.
     */
    private fun styleWheel(picker: NumberPicker) {
        val ink = activity.config.mainTextColor

        // The rows either side of the selection. Public only from Android 10; below that the
        // platform exposes nothing for them and they keep the base theme's colour.
        // Deliberately fainter than the selection : the weight the app gives secondary text.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            picker.setTextColor(ink.withAlpha(0.45f))
        }

        // The selected row is a real EditText inside the picker, and it is the one piece
        // reachable on every version : setSelectedTextColor is not public API.
        for (i in 0 until picker.childCount) {
            (picker.getChildAt(i) as? android.widget.EditText)?.setTextColor(ink)
        }
    }

    private fun configurePicker(picker: NumberPicker, min: Int, max: Int, values: Array<String>?) {
        styleWheel(picker)
        picker.displayedValues = null
        if (min > picker.maxValue) {
            picker.maxValue = max
            picker.minValue = min
        } else {
            picker.minValue = min
            picker.maxValue = max
        }
        picker.displayedValues = values
    }

    private fun buildQuickPickBackground(color: Int): RippleDrawable {
        val density = activity.resources.displayMetrics.density
        val shape = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 1000f * density
            setStroke((1.5f * density).toInt(), color)
            setColor(android.graphics.Color.TRANSPARENT)
        }
        return RippleDrawable(ColorStateList.valueOf(color), shape, shape)
    }
}
