package com.example.data.expense

import java.nio.ByteBuffer
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

/** Category identity is metadata only: never rewrites a historical expense's entered type. */
object ExpenseCategories {
    val builtIns = listOf(
        "Diesel", "Petrol", "Repair", "Puncture", "Oil Change", "Driver Bata", "Spare Parts",
        "Toll / Parking", "Maintenance", "Oil", "Parts", "General", "Seeds", "Pesticides",
        "Labour", "Furniture", "Tyre / Tire", "Rental", "Other"
    )

    fun key(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFC)
        .replace(Regex("[\\p{Z}\\s]+"), " ").trim().lowercase(Locale.ROOT)
        .let { if (it == "others") "other" else it }

    fun validate(name: String): String {
        val display = name.trim()
        require(display.isNotBlank() && key(display).isNotBlank()) { "Enter a category name" }
        require(display.codePointCount(0, display.length) <= 60) { "Category name must be 60 characters or fewer" }
        require(display.none { Character.isISOControl(it) }) { "Category name contains control characters" }
        return display
    }

    /** Stable across devices, case/spacing variants and canonically equivalent Tamil input. */
    fun id(name: String): Long = (ByteBuffer.wrap(MessageDigest.getInstance("SHA-256")
        .digest(key(validate(name)).toByteArray(Charsets.UTF_8))).long and Long.MAX_VALUE).coerceAtLeast(1L)

    fun builtIn(name: String): String? = builtIns.firstOrNull { key(it) == key(name) }

    fun choices(custom: List<String>, historical: List<String> = emptyList()): List<String> =
        (builtIns + custom + historical).filter { it.isNotBlank() }.distinctBy(::key)

    fun label(name: String, tamil: Boolean): String {
        if (!tamil) return name
        return when (key(name)) {
            "diesel" -> "டீசல்"; "petrol" -> "பெட்ரோல்"; "repair" -> "பழுதுபார்ப்பு"
            "puncture" -> "பஞ்சர்"; "oil change" -> "ஆயில் மாற்றம்"; "driver bata" -> "டிரைவர் படி"
            "spare parts" -> "உதிரி பாகங்கள்"; "toll / parking" -> "சுங்கச்சாவடி / பார்க்கிங்"
            "maintenance" -> "பராமரிப்பு"; "oil" -> "ஆயில்"; "parts" -> "பாகங்கள்"
            "general" -> "பொது"; "seeds" -> "விதைகள்"; "pesticides" -> "பூச்சிக்கொல்லிகள்"
            "labour" -> "கூலி"; "furniture" -> "மரச்சாமான்கள்"; "tyre / tire" -> "டயர்"
            "rental" -> "வாடகை"; "other" -> "இதர"
            else -> name // Custom user data is never translated.
        }
    }
}
