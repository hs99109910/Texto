package com.texto.sms.dialogs

import com.texto.sms.extensions.config
import android.app.TimePickerDialog
import android.app.TimePickerDialog.OnTimeSetListener
import android.text.format.DateFormat
import androidx.appcompat.app.AlertDialog
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import org.fossify.commons.activities.BaseSimpleActivity
import org.fossify.commons.extensions.applyColorFilter
import org.fossify.commons.extensions.getAlertDialogBuilder
import org.fossify.commons.extensions.getDatePickerDialogTheme
import org.fossify.commons.extensions.getProperTextColor
import org.fossify.commons.extensions.getTimeFormat
import org.fossify.commons.extensions.isDynamicTheme
import org.fossify.commons.extensions.setupDialogStuff
import org.fossify.commons.extensions.toast
import com.texto.sms.R
import com.texto.sms.activities.SimpleActivity
import com.texto.sms.databinding.ScheduleMessageDialogBinding
import com.texto.sms.helpers.applyTextoDialogSkin
import com.texto.sms.extensions.roundToClosestMultipleOf
import com.texto.sms.extensions.toUiDateText
import com.texto.sms.extensions.toUiDigits
import com.texto.sms.extensions.withAlpha
import com.texto.sms.helpers.TextoGlass
import android.graphics.Typeface
import android.util.TypedValue
import android.view.ViewOutlineProvider
import org.joda.time.DateTime
import java.util.Calendar

class ScheduleMessageDialog(
    private val activity: BaseSimpleActivity,
    private var dateTime: DateTime? = null,
    private val callback: (dateTime: DateTime?) -> Unit
) {
    private val binding = ScheduleMessageDialogBinding.inflate(activity.layoutInflater)
    private val textColor = activity.config.mainTextColor

    private var previewDialog: AlertDialog? = null
    private var previewShown = false
    private var isNewMessage = dateTime == null

    private val calendar = Calendar.getInstance()

    init {
        styleSheet()

        binding.editDateRow.setOnClickListener { showDatePicker() }
        binding.editTimeRow.setOnClickListener { showTimePicker() }

        val targetDateTime = dateTime ?: DateTime.now().plusHours(1)
        updateTexts(targetDateTime)

        if (isNewMessage) {
            showDatePicker()
        } else {
            showPreview()
        }
    }

    /**
     * The two rows are the same capsules the chooser sheets are built from, rather than plain
     * text with a stock ripple on it. Sizes come from the UI:scale setting for the same reason
     * every other surface reads them from there.
     */
    private fun styleSheet() {
        val texto = activity as? SimpleActivity ?: return
        val density = activity.resources.displayMetrics.density

        binding.subtitle.apply {
            setTextColor(textColor.withAlpha(0.62f))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, texto.getScaledTextSize(0.78f))
            typeface = texto.typefaceFor(Typeface.NORMAL)
        }

        arrayOf(binding.editDate, binding.editTime).forEach {
            it.setTextColor(textColor)
            it.setTextSize(TypedValue.COMPLEX_UNIT_PX, texto.getScaledTextSize())
            it.typeface = texto.typefaceFor(Typeface.NORMAL)
        }

        arrayOf(binding.dateImage, binding.timeImage).forEach {
            it.applyColorFilter(activity.config.accentGradientStart)
        }

        arrayOf(binding.editDateRow, binding.editTimeRow).forEach { row ->
            row.background = TextoGlass.bar(
                tint = activity.config.mainBackgroundColor,
                cornerRadius = 100f * density,
                opacity = 0.5f,
                strokeWidthPx = 1,
                rimAlpha = 0.18f
            )
            row.outlineProvider = ViewOutlineProvider.BACKGROUND
        }
    }

    private fun updateTexts(dateTime: DateTime) {
        val timeFormat = activity.getTimeFormat()
        binding.editDate.text = dateTime.toUiDateText()
        // Shaped to the app's language, like every other time in the app: Persian digits
        // under fa, ASCII under en. See toUiDigits.
        binding.editTime.text = dateTime.toString(timeFormat).toUiDigits()
    }

    private fun showPreview() {
        if (previewShown) {
            return
        }

        activity.getAlertDialogBuilder()
            .setPositiveButton(R.string.action_confirm, null)
            .setNegativeButton(R.string.action_cancel, null)
            .apply {
                previewShown = true
                activity.setupDialogStuff(binding.root, this, R.string.schedule_message) { dialog ->
                    previewDialog = dialog
                    (activity as? SimpleActivity)?.applyTextoDialogSkin(dialog)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        if (validateDateTime()) {
                            callback(dateTime)
                            dialog.dismiss()
                        }
                    }

                    dialog.setOnDismissListener {
                        previewShown = false
                        previewDialog = null
                    }
                }
            }
    }

    private fun showDatePicker() {
        PersianDatePickerDialog(
            activity = activity,
            initialDateTime = dateTime ?: DateTime.now(),
            minDateTime = DateTime.now()
        ) { y, m, d -> dateSet(y, m, d) }
    }

    private fun showTimePicker() {
        val hourOfDay = dateTime?.hourOfDay ?: getNextHour()
        val minute = dateTime?.minuteOfHour ?: getNextMinute()

        if (activity.isDynamicTheme()) {
            val timeFormat = if (DateFormat.is24HourFormat(activity)) {
                TimeFormat.CLOCK_24H
            } else {
                TimeFormat.CLOCK_12H
            }

            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(timeFormat)
                .setHour(hourOfDay)
                .setMinute(minute)
                .build()

            timePicker.addOnPositiveButtonClickListener {
                timeSet(timePicker.hour, timePicker.minute)
            }

            timePicker.show(activity.supportFragmentManager, "")
        } else {
            val timeSetListener = OnTimeSetListener { _, hours, minutes -> timeSet(hours, minutes) }
            // The platform clock, at the polarity of whatever the skin actually paints behind
            // it. Commons resolves this from the *base* theme, so on a dark skin the picker
            // arrived as a white panel over a near-black screen.
            val pickerTheme = if (TextoGlass.isDark(activity.config.mainBackgroundColor)) {
                android.R.style.Theme_DeviceDefault_Dialog_Alert
            } else {
                android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
            }
            TimePickerDialog(
                activity,
                pickerTheme,
                timeSetListener,
                hourOfDay,
                minute,
                DateFormat.is24HourFormat(activity)
            ).apply {
                show()
                getButton(AlertDialog.BUTTON_NEGATIVE).apply {
                    // Commons' string reaches this Persian-only screen in English.
                    text = activity.getString(R.string.action_cancel)
                    setOnClickListener {
                        dismiss()
                    }
                }
                getButton(AlertDialog.BUTTON_POSITIVE)?.text =
                    activity.getString(R.string.action_confirm)
            }
        }
    }

    private fun dateSet(year: Int, monthOfYear: Int, dayOfMonth: Int) {
        if (isNewMessage) {
            showTimePicker()
        }

        dateTime = DateTime.now()
            .withDate(year, monthOfYear + 1, dayOfMonth)
            .run {
                if (dateTime != null) {
                    withTime(dateTime!!.hourOfDay, dateTime!!.minuteOfHour, 0, 0)
                } else {
                    withTime(getNextHour(), getNextMinute(), 0, 0)
                }
            }

        if (!isNewMessage) {
            validateDateTime()
        }

        isNewMessage = false
        updateTexts(dateTime!!)
    }

    private fun timeSet(hourOfDay: Int, minute: Int) {
        dateTime = dateTime?.withHourOfDay(hourOfDay)?.withMinuteOfHour(minute)
        if (validateDateTime()) {
            updateTexts(dateTime!!)
            showPreview()
        } else {
            showTimePicker()
        }
    }

    private fun validateDateTime(): Boolean {
        return if (dateTime?.isAfterNow == false) {
            activity.toast(R.string.must_pick_time_in_the_future)
            false
        } else {
            true
        }
    }

    private fun getNextHour(): Int {
        return (calendar.get(Calendar.HOUR_OF_DAY) + 1)
            .coerceIn(0, 23)
    }

    private fun getNextMinute(): Int {
        return (calendar.get(Calendar.MINUTE) + 5)
            .roundToClosestMultipleOf(5)
            .coerceIn(0, 59)
    }
}
