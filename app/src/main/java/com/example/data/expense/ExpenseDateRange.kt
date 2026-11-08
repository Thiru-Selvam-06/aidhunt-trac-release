package com.example.data.expense

import java.util.Calendar
import java.util.TimeZone

/** Whole business days in IST, independent of the phone's current timezone. */
object ExpenseDateRange {
    enum class Period { TODAY, THIS_WEEK, THIS_MONTH, THIS_YEAR, ALL_TIME, CUSTOM }
    fun startOfDay(time: Long): Long = calendar(time).apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    fun endOfDay(time: Long): Long = calendar(startOfDay(time)).apply { add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis - 1
    fun calendar(time: Long): Calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply { timeInMillis = time }
    fun contains(time: Long, period: Period, now: Long, customStart: Long, customEnd: Long): Boolean {
        if (period == Period.ALL_TIME) return true
        if (time <= 0) return false // Undated history is overall-only.
        if (period == Period.CUSTOM) return customStart <= customEnd && time in customStart..customEnd
        val start = calendar(startOfDay(now)).apply {
            when (period) {
                Period.THIS_WEEK -> set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                Period.THIS_MONTH -> set(Calendar.DAY_OF_MONTH, 1)
                Period.THIS_YEAR -> set(Calendar.DAY_OF_YEAR, 1)
                else -> Unit
            }
        }.timeInMillis
        return time in start..endOfDay(now)
    }
}
