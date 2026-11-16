package com.example.ui.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone

internal val businessReportZone: TimeZone get() = TimeZone.getTimeZone("Asia/Kolkata")

internal enum class ReportPeriod(val english: String, val tamil: String) {
    ALL_TIME("All Time", "எல்லா தேதிகளும்"),
    TODAY("Today", "இன்று"),
    YESTERDAY("Yesterday", "நேற்று"),
    THIS_WEEK("This Week", "இந்த வாரம்"),
    THIS_MONTH("This Month", "இந்த மாதம்"),
    LAST_MONTH("Last Month", "கடந்த மாதம்"),
    MONTH_YEAR("Select Month & Year", "மாதம் மற்றும் ஆண்டைத் தேர்ந்தெடுக்கவும்"),
    THIS_YEAR("This Year", "இந்த ஆண்டு"),
    SPECIFIC_DATE("Specific Date", "குறிப்பிட்ட தேதி"),
    CUSTOM_RANGE("Custom Date Range", "தேதி வரம்பு");

    fun label(isTamil: Boolean): String = if (isTamil) tamil else english
}

internal data class ReportDateFilter(
    val period: ReportPeriod = ReportPeriod.ALL_TIME,
    val from: Long = 0,
    val to: Long = from
) {
    fun bounds(now: Long): LongRange? {
        if (period == ReportPeriod.ALL_TIME) return null
        val today = reportCalendar(now)
        val start = floorToDay(if (period in setOf(ReportPeriod.MONTH_YEAR, ReportPeriod.SPECIFIC_DATE, ReportPeriod.CUSTOM_RANGE)) from else now)
        val end = start.clone() as Calendar
        when (period) {
            ReportPeriod.ALL_TIME -> error("All Time has no bounds")
            ReportPeriod.TODAY, ReportPeriod.SPECIFIC_DATE -> end.add(Calendar.DAY_OF_MONTH, 1)
            ReportPeriod.YESTERDAY -> start.add(Calendar.DAY_OF_MONTH, -1)
            ReportPeriod.THIS_WEEK -> {
                start.add(Calendar.DAY_OF_MONTH, -((today.get(Calendar.DAY_OF_WEEK) - today.firstDayOfWeek + 7) % 7))
                end.add(Calendar.DAY_OF_MONTH, 1)
            }
            ReportPeriod.THIS_MONTH, ReportPeriod.LAST_MONTH, ReportPeriod.MONTH_YEAR -> {
                start.set(Calendar.DAY_OF_MONTH, 1)
                if (period == ReportPeriod.LAST_MONTH) start.add(Calendar.MONTH, -1)
                end.timeInMillis = start.timeInMillis
                end.add(Calendar.MONTH, 1)
            }
            ReportPeriod.THIS_YEAR -> {
                start.set(Calendar.DAY_OF_YEAR, 1)
                end.add(Calendar.DAY_OF_MONTH, 1)
            }
            ReportPeriod.CUSTOM_RANGE -> {
                end.timeInMillis = floorToDay(to).timeInMillis
                end.add(Calendar.DAY_OF_MONTH, 1)
            }
        }
        return start.timeInMillis until end.timeInMillis
    }

    fun selectionLabel(isTamil: Boolean): String = when (period) {
        ReportPeriod.MONTH_YEAR -> reportDate(from, isTamil, "MMMM yyyy")
        ReportPeriod.SPECIFIC_DATE -> reportDate(from, isTamil)
        ReportPeriod.CUSTOM_RANGE -> "${reportDate(from, isTamil)} – ${reportDate(to, isTamil)}"
        else -> period.label(isTamil)
    }
}

internal fun reportDateMatches(timestamp: Long, bounds: LongRange?): Boolean =
    bounds == null || (timestamp > 0L && timestamp in bounds)

internal fun reportCalendar(time: Long): Calendar =
    GregorianCalendar(businessReportZone, Locale.forLanguageTag("en-IN")).apply { timeInMillis = time }

internal fun reportDay(year: Int, month: Int, day: Int): Long =
    GregorianCalendar(businessReportZone).apply { clear(); set(year, month, day) }.timeInMillis

internal fun dateUnavailable(isTamil: Boolean): String = if (isTamil) "தேதி பதிவு செய்யப்படவில்லை" else "Date not recorded"

internal fun reportDate(time: Long, isTamil: Boolean, pattern: String = "dd MMM yyyy"): String =
    if (time <= 0L) dateUnavailable(isTamil)
    else SimpleDateFormat(pattern, Locale.forLanguageTag(if (isTamil) "ta-IN" else "en-IN")).apply {
        timeZone = businessReportZone
    }.format(Date(time))

private fun floorToDay(time: Long): Calendar = reportCalendar(time).apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}
