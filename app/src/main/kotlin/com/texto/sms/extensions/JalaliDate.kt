package com.texto.sms.extensions

import java.util.Calendar
import java.util.TimeZone
import com.texto.sms.helpers.TextoLocale
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


/* ------------------------------------------------------------------------------------
 * Everything below this line is display, and display follows the app's language.
 *
 * A calendar and a digit shape are not resources, so values-fa cannot carry them: the
 * resource system resolves strings, not the Jalali/Gregorian split or the choice between
 * "1404" in Persian digits and "2025". They branch on TextoLocale.isPersian instead, which
 * is set in the one place the locale is decided.
 * ------------------------------------------------------------------------------------ */

private val ENGLISH_WEEKDAY_NAMES = arrayOf(
    "", // Calendar.DAY_OF_WEEK is 1-based (SUNDAY = 1)
    "Sunday",
    "Monday",
    "Tuesday",
    "Wednesday",
    "Thursday",
    "Friday",
    "Saturday"
)

val GREGORIAN_MONTH_NAMES = arrayOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December"
)

/**
 * The calendar the UI is currently drawn on: Jalali under Persian, Gregorian under English.
 *
 * The two date pickers and the range picker are built by hand out of a month grid, so they
 * need the pieces of a calendar rather than a formatted string. Routing all three through
 * here is what keeps an English user off a Persian month grid with Latin numerals on it,
 * which is what "translated" would have meant if only the strings had moved.
 */
object TextoCalendar {

    val isJalali get() = TextoLocale.isPersian

    val monthNames: Array<String>
        get() = if (isJalali) JALALI_MONTH_NAMES else GREGORIAN_MONTH_NAMES

    /** Column headers, in the order [firstColumnOf] numbers them. */
    val weekdayInitials: List<String>
        get() = if (isJalali) {
            listOf("ش", "ی", "د", "س", "چ", "پ", "ج")
        } else {
            listOf("S", "M", "T", "W", "T", "F", "S")
        }

    /** A civil (Gregorian) date, as the display calendar numbers it. */
    fun fromCivil(gYear: Int, gMonth: Int, gDay: Int): JalaliYmd =
        if (isJalali) gregorianToJalali(gYear, gMonth, gDay) else JalaliYmd(gYear, gMonth, gDay)

    /** The inverse of [fromCivil]. */
    fun toCivil(year: Int, month: Int, day: Int): JalaliYmd =
        if (isJalali) jalaliToGregorian(year, month, day) else JalaliYmd(year, month, day)

    fun daysInMonth(year: Int, month: Int): Int = if (isJalali) {
        daysInJalaliMonth(year, month)
    } else {
        when (month) {
            2 -> if ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }

    /**
     * Which column a date falls in. The Persian week starts on Saturday and the English one
     * on Sunday, so the two grids are offset by a day and cannot share one formula.
     */
    fun firstColumnOf(year: Int, month: Int, day: Int): Int {
        val civil = toCivil(year, month, day)
        val cal = Calendar.getInstance().apply {
            clear()
            set(civil.year, civil.month - 1, civil.day)
        }
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK) // SUNDAY = 1 .. SATURDAY = 7
        // Saturday lands on column 0 under Persian, Sunday under English.
        return if (isJalali) dayOfWeek % 7 else dayOfWeek - 1
    }

    /** The epoch millis at the start of the given display date. */
    fun millisOf(year: Int, month: Int, day: Int): Long {
        val civil = toCivil(year, month, day)
        return Calendar.getInstance().apply {
            clear()
            set(civil.year, civil.month - 1, civil.day)
        }.timeInMillis
    }

    /** "31 مرداد 1405", or "31 August 2025", as the language asks. */
    fun dateText(year: Int, month: Int, day: Int): String =
        "${day.toUiDigits()} ${monthNames[month - 1]} ${year.toUiDigits()}"
}

/** The date as the current calendar renders it: Jalali under Persian, Gregorian otherwise. */
fun DateTime.toUiDateText(): String {
    val ymd = TextoCalendar.fromCivil(year, monthOfYear, dayOfMonth)
    return TextoCalendar.dateText(ymd.year, ymd.month, ymd.day)
}

fun Int.toUiDigits(): String = toString().toUiDigits()

/**
 * Shapes ASCII `0`-`9` into Persian digits, and only while the UI is in Persian. Under
 * English the string is handed back untouched, which is what keeps a version number, a
 * percentage and a clock time reading like an English app rather than a translated
 * Persian one.
 *
 * Only ASCII is mapped, deliberately. `Char.isDigit()` is true for Persian and Arabic-Indic
 * digits as well, so a string that had already been shaped -- or that came out of
 * `String.format` under a fa-IR locale, which emits Persian digits on its own -- indexed
 * this array with `'1' - '0'` in Persian = 1729 and took the process down. Converting an
 * already-converted string is a no-op.
 */
fun String.toUiDigits(): String {
    if (!TextoLocale.isPersian) return this
    return map { c ->
        if (c in '0'..'9') PERSIAN_DIGITS[c - '0'] else c
    }.joinToString("")
}

/** The percent sign the current language writes: Persian has its own. */
val uiPercentSign: String get() = if (TextoLocale.isPersian) "٪" else "%"

private fun weekdayName(cal: Calendar): String {
    val index = cal.get(Calendar.DAY_OF_WEEK)
    return if (TextoLocale.isPersian) {
        PERSIAN_WEEKDAY_NAMES[index]
    } else {
        ENGLISH_WEEKDAY_NAMES[index]
    }
}

/**
 * Formats an epoch-millis timestamp for a conversation row.
 * Within the last 7 days: weekday name and time, e.g. "Tuesday 21:12".
 * Older than 7 days: full date and time, e.g. "1405-03-28.21:12".
 */
fun Long.formatUiDateOrTime(): String {
    val cal = Calendar.getInstance(TimeZone.getDefault())
    cal.timeInMillis = this

    val time = "%02d:%02d"
        .format(java.util.Locale.US, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        .toUiDigits()
    val diff = System.currentTimeMillis() - this

    return if (diff < SEVEN_DAYS_MILLIS) {
        "${weekdayName(cal)} $time"
    } else {
        val ymd = TextoCalendar.fromCivil(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        val date = "%04d-%02d-%02d"
            .format(java.util.Locale.US, ymd.year, ymd.month, ymd.day)
            .toUiDigits()
        "$date.$time"
    }
}

/**
 * Clock time alone -- what the design prints inside each bubble. The day a message belongs
 * to is carried by the date separator above it, so repeating it on every bubble would only
 * crowd the line.
 */
fun Long.formatUiTimeOnly(): String {
    val cal = Calendar.getInstance(TimeZone.getDefault())
    cal.timeInMillis = this
    // Formatted in a fixed locale, then shaped explicitly. Left to the default locale,
    // `format` emits Persian digits by itself under fa-IR, which would tie the result to the
    // device locale rather than to the app's own language setting.
    return "%02d:%02d"
        .format(java.util.Locale.US, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        .toUiDigits()
}

/**
 * The separator between groups of messages: today, yesterday, a weekday name within the
 * last week, or a full date beyond that. Takes a Context because the first two are
 * resources now rather than the two Persian literals they used to be.
 */
fun Long.formatUiDayLabel(context: android.content.Context): String {
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
        this >= startOfDay -> context.getString(com.texto.sms.R.string.today)
        this >= startOfDay - dayMillis -> context.getString(com.texto.sms.R.string.yesterday)
        this >= startOfDay - 6 * dayMillis -> weekdayName(cal)
        else -> {
            val ymd = TextoCalendar.fromCivil(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH)
            )
            TextoCalendar.dateText(ymd.year, ymd.month, ymd.day)
        }
    }
}
