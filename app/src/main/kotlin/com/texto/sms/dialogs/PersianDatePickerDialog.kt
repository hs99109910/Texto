package com.texto.sms.dialogs

import com.texto.sms.extensions.config
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.widget.NumberPicker
import androidx.appcompat.app.AlertDialog
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.setupDialogStuff
import com.texto.sms.R
import com.texto.sms.databinding.PersianDatePickerDialogBinding
import com.texto.sms.extensions.JALALI_MONTH_NAMES
import com.texto.sms.extensions.daysInJalaliMonth
import com.texto.sms.extensions.gregorianToJalali
import com.texto.sms.extensions.jalaliToGregorian
import com.texto.sms.extensions.toPersianDigits
import org.joda.time.DateTime

/**
 * Lets the user pick a date on the Jalali (Persian/Shamsi) calendar, plus one-tap "tomorrow" /
 * "day after tomorrow" shortcuts. Delivers the choice back as a Gregorian (year, month 0-based,
 * day) triple, matching [android.app.DatePickerDialog.OnDateSetListener], so callers don't need
 * to deal with Jalali dates themselves.
 */
class PersianDatePickerDialog(
    private val activity: BaseSimpleActivity,
    initialDateTime: DateTime,
    private val minDateTime: DateTime,
    private val onDateSet: (year: Int, month: Int, day: Int) -> Unit
) {
    private val binding = PersianDatePickerDialogBinding.inflate(activity.layoutInflater)
    private val minJalali = gregorianToJalali(minDateTime.year, minDateTime.monthOfYear, minDateTime.dayOfMonth)

    private val initialJalali = run {
        val jalali = gregorianToJalali(initialDateTime.year, initialDateTime.monthOfYear, initialDateTime.dayOfMonth)
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
            .setPositiveButton(org.fossify.commons.R.string.ok, null)
            .setNegativeButton(org.fossify.commons.R.string.cancel, null)
            .apply {
                activity.setupDialogStuff(binding.root, this, R.string.select_date) { alertDialog ->
                    dialog = alertDialog
                    alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val gregorian = jalaliToGregorian(
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
            (minJalali.year..maxJalaliYear).map { it.toPersianDigits() }.toTypedArray()
        )
        binding.yearPicker.value = initialJalali.year

        // Bootstrap month/day with the widest possible range so the `value` assignments below are
        // always legal; refreshDependentPickers() immediately narrows them to the real min/max.
        configurePicker(binding.monthPicker, 1, 12, JALALI_MONTH_NAMES)
        binding.monthPicker.value = initialJalali.month

        configurePicker(binding.dayPicker, 1, 31, (1..31).map { it.toPersianDigits() }.toTypedArray())
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
        configurePicker(binding.monthPicker, monthMin, 12, JALALI_MONTH_NAMES.copyOfRange(monthMin - 1, 12))

        val selectedMonth = binding.monthPicker.value
        val dayMin = if (selectedYear == minJalali.year && selectedMonth == minJalali.month) minJalali.day else 1
        val dayMax = daysInJalaliMonth(selectedYear, selectedMonth)
        if (binding.dayPicker.value < dayMin) {
            binding.dayPicker.value = dayMin
        } else if (binding.dayPicker.value > dayMax) {
            binding.dayPicker.value = dayMax
        }
        configurePicker(binding.dayPicker, dayMin, dayMax, (dayMin..dayMax).map { it.toPersianDigits() }.toTypedArray())
    }

    /** Changes a NumberPicker's range/labels without hitting the "min must stay <= max" pitfall. */
    private fun configurePicker(picker: NumberPicker, min: Int, max: Int, values: Array<String>?) {
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
