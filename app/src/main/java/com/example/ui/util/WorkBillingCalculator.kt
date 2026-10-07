package com.example.ui.util

import java.util.Locale

/**
 * Single source of truth for all duration and billing calculations across AIDHUNT Trac.
 * Ensures consistent behavior between Clock Time selection, Manual Duration entry,
 * UI display, database storage, and PDF generation.
 */
object WorkBillingCalculator {

    /**
     * Calculates duration in total minutes from Clock Time picker (start and end).
     * Supports midnight crossing (e.g. 22:15 to 02:30 -> 255 minutes = 4h 15m).
     * If start or end time is not set, returns 0L.
     */
    fun calculateDurationFromClock(
        startHour: Int?,
        startMinute: Int?,
        endHour: Int?,
        endMinute: Int?
    ): Long {
        if (startHour == null || startMinute == null || endHour == null || endMinute == null) {
            return 0L
        }
        val startTotal = (startHour * 60) + startMinute
        val endTotal = (endHour * 60) + endMinute

        val diff = if (endTotal >= startTotal) {
            endTotal - startTotal
        } else {
            // Overnight crossing 24:00 (1440 minutes in a day)
            (1440 - startTotal) + endTotal
        }
        return diff.toLong().coerceAtLeast(0L)
    }

    /**
     * Calculates duration in total minutes from Manual hour and minute inputs.
     * Treats blank or invalid inputs as 0.
     */
    fun calculateDurationFromManual(hoursStr: String, minutesStr: String): Long {
        val h = hoursStr.trim().toLongOrNull() ?: 0L
        val m = minutesStr.trim().toLongOrNull() ?: 0L
        val total = (h * 60) + m
        return total.coerceAtLeast(0L)
    }

    /**
     * Canonical Billing Formula:
     * totalAmount = durationMinutes * (hourlyRate / 60.0)
     * Handles 0 duration, 0 rate, NaN, and negative protection.
     */
    fun calculateAmount(durationMinutes: Long, hourlyRate: Double): Double {
        if (durationMinutes <= 0L || hourlyRate <= 0.0 || hourlyRate.isNaN() || hourlyRate.isInfinite()) {
            return 0.0
        }
        val amount = durationMinutes * (hourlyRate / 60.0)
        return if (amount.isNaN() || amount.isInfinite() || amount < 0.0) 0.0 else Math.round(amount).toDouble()
    }

    /**
     * Converts duration in minutes to decimal hours.
     * e.g. 150 mins -> 2.5 hours
     */
    fun toDecimalHours(durationMinutes: Long): Double {
        if (durationMinutes <= 0L) return 0.0
        return durationMinutes / 60.0
    }

    /**
     * Formats duration in clean standard format:
     * 0 -> "0"
     * 60 -> "1h"
     * 90 -> "1h 30m"
     * 210 -> "3h 30m"
     * 230 -> "3h 50m"
     * 45 -> "45m"
     */
    fun formatDuration(durationMinutes: Long): String {
        if (durationMinutes <= 0L) return "0"
        val hours = durationMinutes / 60
        val mins = durationMinutes % 60
        return when {
            hours > 0 && mins > 0 -> "${hours}h ${mins}m"
            hours > 0 && mins == 0L -> "${hours}h"
            else -> "${mins}m"
        }
    }

    /**
     * Formats duration with both hours decimal and total minutes:
     * e.g. "3.83 hrs (230 mins)"
     */
    fun formatDurationDetailed(durationMinutes: Long): String {
        if (durationMinutes <= 0L) return "0 mins"
        val hours = durationMinutes / 60.0
        return String.format(Locale.US, "%.2f hrs (%d mins)", hours, durationMinutes)
    }

    /**
     * Formats duration as hours decimal:
     * e.g. "3.8 hrs"
     */
    fun formatHoursDecimal(durationMinutes: Long): String {
        if (durationMinutes <= 0L) return "0.0 hrs"
        val hours = durationMinutes / 60.0
        return String.format(Locale.US, "%.1f hrs", hours)
    }
}
