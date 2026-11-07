package com.example.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Recording instants are independent from work dates and the device's timezone. */
object JobAuditPresentation {
    fun field(key: String, tamil: Boolean): String {
        val labels = mapOf("customerId" to ("Customer" to "வாடிக்கையாளர்"), "customerName" to ("Customer name" to "வாடிக்கையாளர் பெயர்"),
            "customerPhone" to ("Phone" to "தொலைபேசி"), "customerLocation" to ("Location" to "இடம்"), "operatorName" to ("Operator" to "இயக்குபவர்"),
            "tractorId" to ("Tractor" to "டிராக்டர்"), "tractorLabel" to ("Tractor" to "டிராக்டர்"), "workType" to ("Work type" to "வேலை வகை"),
            "startTimeMillis" to ("Work start" to "வேலை தொடக்கம்"), "endTimeMillis" to ("Work end" to "வேலை முடிவு"),
            "durationMinutes" to ("Minutes" to "நிமிடங்கள்"), "hourlyRate" to ("Rate" to "கட்டணம்"), "totalAmount" to ("Amount" to "தொகை"), "notes" to ("Notes" to "குறிப்புகள்"))
        val label = labels[key] ?: ("Details" to "விவரங்கள்")
        return if (tamil) label.second else label.first
    }
    fun value(key: String, raw: Any?, historical: Boolean, tamil: Boolean = false): String = when {
        raw == null -> "—"
        key in setOf("startTimeMillis", "endTimeMillis") -> if (historical) date((raw as? Number)?.toLong() ?: 0L, tamil) else instant((raw as? Number)?.toLong() ?: 0L, tamil)
        else -> raw.toString()
    }
    fun instant(time: Long, tamil: Boolean = false): String = if (time <= 0) "—" else
        SimpleDateFormat("dd MMM yyyy, hh:mm a 'IST'", if (tamil) Locale.forLanguageTag("ta-IN") else Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }.format(Date(time))
    fun date(time: Long, tamil: Boolean = false): String = if (time <= 0) "—" else
        SimpleDateFormat("dd MMM yyyy", if (tamil) Locale.forLanguageTag("ta-IN") else Locale.ENGLISH).apply {
            timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        }.format(Date(time))
}
