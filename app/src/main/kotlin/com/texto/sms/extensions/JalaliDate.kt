package com.texto.sms.extensions

import java.util.Calendar
import java.util.TimeZone
import org.joda.time.DateTime
import org.joda.time.LocalDate

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

val JALALI_MONTH_NAMES = arrayOf(
    "فروردین",
    "اردیبهشت",
    "خرداد",
    "تیر",
    "مرداد",
    "شهریور",
    "مهر",
    "آبان",
    "آذر",
    "دی",
    "بهمن",
    "اسفند"
)

private val PERSIAN_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

private const val SEVEN_DAYS_MILLIS = 7L * 24 * 60 * 60 * 1000

private val JALALI_MONTH_LENGTHS = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
private val GREGORIAN_MONTH_LENGTHS = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

/** Gregorian (year, month 1-based, day) -> Jalali (year, month 1-based, day). */
data class JalaliYmd(val year: Int, val month: Int, val day: Int)

fun gregorianToJalali(gYear: Int, gMonth: Int, gDay: Int): JalaliYmd {
    val isGregorianLeap = (gYear % 4 == 0 && gYear % 100 != 0) || gYear % 400 == 0

    val gy = gYear - 1600
    val gm = gMonth - 1
    val gd = gDay - 1

    var gDayNo = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400
    for (i in 0 until gm) {
        gDayNo += GREGORIAN_MONTH_LENGTHS[i]
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
        if (jd < JALALI_MONTH_LENGTHS[i]) {
            jm = i
            break
        }
        jd -= JALALI_MONTH_LENGTHS[i]
        jm = 11
    }

    return JalaliYmd(jy, jm + 1, jd + 1)
}

/** Jalali (year, month 1-based, day) -> Gregorian (year, month 1-based, day). Inverse of [gregorianToJalali]. */
fun jalaliToGregorian(jYear: Int, jMonth: Int, jDay: Int): JalaliYmd {
    val yearsFrom979 = jYear - 979
    val jNp = Math.floorDiv(yearsFrom979, 33)
    val yearInCycle = yearsFrom979 - jNp * 33

    val rem1 = if (yearInCycle == 32) {
        8 * 1461
    } else {
        val blockIndex = yearInCycle / 4
        val yearInBlock = yearInCycle % 4
        val withinBlockOffset = if (yearInBlock == 0) 0 else 366 + (yearInBlock - 1) * 365
        blockIndex * 1461 + withinBlockOffset
    }
    val dYearStart = jNp * 12053 + rem1

    var dayOfYear = jDay - 1
    for (i in 0 until (jMonth - 1)) {
        dayOfYear += JALALI_MONTH_LENGTHS[i]
    }

    val gDayNo = dYearStart + dayOfYear + 79
    val gregorianDate = LocalDate(1600, 1, 1).plusDays(gDayNo)
    return JalaliYmd(gregorianDate.year, gregorianDate.monthOfYear, gregorianDate.dayOfMonth)
}

fun isJalaliLeapYear(jYear: Int): Boolean {
    val yearsFrom979 = jYear - 979
    val yearInCycle = Math.floorMod(yearsFrom979, 33)
    return yearInCycle != 32 && yearInCycle % 4 == 0
}

fun daysInJalaliMonth(jYear: Int, jMonth: Int): Int {
    return if (jMonth == 12) {
        if (isJalaliLeapYear(jYear)) 30 else 29
    } else {
        JALALI_MONTH_LENGTHS[jMonth - 1]
    }
}

/** Renders as e.g. "31 مرداد 1405" using the Jalali (Persian/Shamsi) calendar. */
fun DateTime.toJalaliDateText(): String {
    val jalali = gregorianToJalali(year, monthOfYear, dayOfMonth)
    return "${jalali.day.toPersianDigits()} ${JALALI_MONTH_NAMES[jalali.month - 1]} ${jalali.year.toPersianDigits()}"
}

fun Int.toPersianDigits(): String = toString().toPersianDigits()

/**
 * Only ASCII `0`-`9` are mapped, deliberately.
 *
 * `Char.isDigit()` is true for Persian and Arabic-Indic digits as well, so a string that had
 * already been shaped -- or that came out of `String.format` under the app's fa-IR locale,
 * which emits Persian digits on its own -- indexed this array with `'۱' - '0'` = 1729 and
 * took the process down. Converting an already-converted string is now a no-op.
 */
fun String.toPersianDigits(): String = map { c ->
    if (c in '0'..'9') PERSIAN_DIGITS[c - '0'] else c
}.joinToString("")

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

/**
 * Clock time alone, in Persian digits -- what the design prints inside each bubble.
 * The day a message belongs to is carried by the date separator above it, so repeating it
 * on every bubble would only crowd the line.
 */
fun Long.formatJalaliTimeOnly(): String {
    val cal = Calendar.getInstance(TimeZone.getDefault())
    cal.timeInMillis = this
    // Formatted in a fixed locale, then shaped explicitly. Left to the default locale,
    // `format` emits Persian digits by itself under fa-IR, which makes the result depend on
    // the device locale rather than on this function.
    return "%02d:%02d"
        .format(java.util.Locale.US, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        .toPersianDigits()
}

/**
 * The separator between groups of messages: "امروز", "دیروز", a weekday name within the
 * last week, or a full Jalali date beyond that. Replaces the old full timestamp, which
 * repeated the clock time already shown inside every bubble.
 */
fun Long.formatJalaliDayLabel(): String {
    val cal = Calendar.getInstance(TimeZone.getDefault())
    cal.timeInMillis = this

    val startOfDay = Calendar.getInstance(TimeZone.getDefault()).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val dayMillis = 24L * 60 * 60 * 1000

    return when {
        this >= startOfDay -> "امروز"
        this >= startOfDay - dayMillis -> "دیروز"
        this >= startOfDay - 6 * dayMillis -> PERSIAN_WEEKDAY_NAMES[cal.get(Calendar.DAY_OF_WEEK)]
        else -> {
            val jalali = gregorianToJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            "${jalali.day.toPersianDigits()} ${JALALI_MONTH_NAMES[jalali.month - 1]} ${jalali.year.toPersianDigits()}"
        }
    }
}
