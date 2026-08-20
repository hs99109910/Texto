package com.texto.sms.extensions

import java.util.Calendar
import java.util.TimeZone

private val PERSIAN_WEEKDAY_NAMES = arrayOf(
    "", // Calendar.DAY_OF_WEEK is 1-based (SUNDAY = 1)
    "یکشنبه",
    "دوشنبه",
    "سه‌شنبه",
    "چهارشنبه",
    "پنجشنبه",
    "جمعه",
    "شنبه"
)

private const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000

/** Gregorian (year, month 1-based, day) -> Jalali (year, month 1-based, day). */
private data class JalaliYmd(val year: Int, val month: Int, val day: Int)

private fun gregorianToJalali(gYear: Int, gMonth: Int, gDay: Int): JalaliYmd {
    val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
    val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)

    val isGregorianLeap = (gYear % 4 == 0 && gYear % 100 != 0) || gYear % 400 == 0

    var gy = gYear - 1600
    val gm = gMonth - 1
    val gd = gDay - 1

    var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
    for (i in 0 until gm) {
        gDayNo += gDaysInMonth[i]
    }
    if (gm > 1 && isGregorianLeap) gDayNo += 1
    gDayNo += gd

    var jDayNo = gDayNo - 79

    val jNp = jDayNo / 12053
    jDayNo %= 12053

    var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
    jDayNo %= 1461

    if (jDayNo >= 366) {
        jy += (jDayNo - 1) / 365
        jDayNo = (jDayNo - 1) % 365
    }

    var jm = 0
    var jd = jDayNo
    for (i in 0 until 11) {
        if (jd < jDaysInMonth[i]) {
            jm = i
            break
        }
        jd -= jDaysInMonth[i]
        jm = 11
    }

    return JalaliYmd(jy, jm + 1, jd + 1)
}

/**
 * Formats an epoch-millis timestamp using the Jalali (Persian/Shamsi) calendar.
 * Within the last 7 days: weekday name + time only, e.g. "سه‌شنبه 21:12".
 * Older than 7 days: full Jalali date + time, e.g. "1405-03-28.21:12".
 */
fun Long.formatJalaliDateOrTime(): String {
    val cal = Calendar.getInstance(TimeZone.getDefault())
    cal.timeInMillis = this

    val time = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    val diff = System.currentTimeMillis() - this

    return if (diff < SEVEN_DAYS_MILLIS) {
        val weekday = PERSIAN_WEEKDAY_NAMES[cal.get(Calendar.DAY_OF_WEEK)]
        "$weekday $time"
    } else {
        val jalali = gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        "%04d-%02d-%02d.%s".format(jalali.year, jalali.month, jalali.day, time)
    }
}
