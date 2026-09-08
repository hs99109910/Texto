package com.texto.sms.helpers

import android.content.Context
import com.texto.sms.R
import com.texto.sms.extensions.toUiDateText
import org.joda.time.DateTime
import java.util.Calendar
import java.util.TimeZone

/**
 * The date half of the search filters: a window of time, in epoch seconds, that a message's
 * own timestamp has to fall inside.
 *
 * Held separately from the conversation filter and the typed query so the three can be
 * combined or used alone -- picking a range with no query narrows the whole list by date, and
 * a query with no range searches everything.
 *
 * [startSec] and [endSec] are inclusive/exclusive respectively; [ANY] covers all of time.
 */
data class SearchDateRange(
    val id: String,
    val startSec: Long,
    val endSec: Long,
) {

    fun contains(dateSec: Int): Boolean {
        val sec = dateSec.toLong()
        return sec >= startSec && sec < endSec
    }

    val isAny: Boolean get() = id == ID_ANY

    companion object {
        const val ID_ANY = "any"
        const val ID_TODAY = "today"
        const val ID_YESTERDAY = "yesterday"
        const val ID_WEEK = "week"
        const val ID_MONTH = "month"
        const val ID_QUARTER = "quarter"
        const val ID_CUSTOM = "custom"

        private const val DAY_SEC = 24L * 60 * 60

        val ANY = SearchDateRange(ID_ANY, Long.MIN_VALUE / 2, Long.MAX_VALUE / 2)

        /** Local midnight today, in epoch seconds. */
        private fun startOfTodaySec(): Long {
            val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis / 1000L
        }

        fun preset(id: String): SearchDateRange {
            val today = startOfTodaySec()
            val tomorrow = today + DAY_SEC
            return when (id) {
                ID_TODAY -> SearchDateRange(id, today, tomorrow)
                ID_YESTERDAY -> SearchDateRange(id, today - DAY_SEC, today)
                ID_WEEK -> SearchDateRange(id, today - 6 * DAY_SEC, tomorrow)
                ID_MONTH -> SearchDateRange(id, today - 29 * DAY_SEC, tomorrow)
                ID_QUARTER -> SearchDateRange(id, today - 89 * DAY_SEC, tomorrow)
                else -> ANY
            }
        }

        /**
         * A user-picked window. Both ends are whole days: [startMillis] is taken from its own
         * midnight and [endMillis] runs to the end of its day, so picking the same date twice
         * still matches everything sent that day.
         */
        fun custom(startMillis: Long, endMillis: Long): SearchDateRange {
            fun midnight(millis: Long): Long {
                val cal = Calendar.getInstance(TimeZone.getDefault()).apply {
                    timeInMillis = millis
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                return cal.timeInMillis / 1000L
            }

            val from = midnight(minOf(startMillis, endMillis))
            val to = midnight(maxOf(startMillis, endMillis)) + DAY_SEC
            return SearchDateRange(ID_CUSTOM, from, to)
        }

        /** What the chip reads, in Persian and on the Jalali calendar. */
        fun labelOf(context: Context, range: SearchDateRange): String = when (range.id) {
            ID_ANY -> context.getString(R.string.search_date_any)
            ID_TODAY -> context.getString(R.string.search_date_today)
            ID_YESTERDAY -> context.getString(R.string.search_date_yesterday)
            ID_WEEK -> context.getString(R.string.search_date_week)
            ID_MONTH -> context.getString(R.string.search_date_month)
            ID_QUARTER -> context.getString(R.string.search_date_quarter)
            else -> context.getString(
                R.string.search_date_range,
                DateTime(range.startSec * 1000L).toUiDateText(),
                // The stored end is exclusive, so step back inside the last day before naming it.
                DateTime((range.endSec - 1) * 1000L).toUiDateText()
            )
        }
    }
}
