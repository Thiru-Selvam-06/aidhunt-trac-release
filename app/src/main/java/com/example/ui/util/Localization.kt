package com.example.ui.util

import com.example.data.entity.AppSettingsEntity
import com.example.ui.components.DatePreset

val AppSettingsEntity.isTamil: Boolean
    get() = language.equals("TA", ignoreCase = true)

object Localization {
    fun getWorkTypeDisplayName(workType: String, isTamil: Boolean): String {
        if (!isTamil) return workType
        return when (workType) {
            "Rotavator" -> "ரோட்டாவேட்டர்"
            "5 Claws" -> "5 கலப்பை"
            "9 Claws" -> "9 கலப்பை"
            "Loader" -> "லோடர்"
            "Ploughing" -> "உழவு"
            "Cultivator" -> "கொத்துக்கலப்பை"
            "Harvester" -> "அறுவடை இயந்திரம்"
            "Leveler" -> "லெவலர்"
            "Trailer" -> "டிரெய்லர்"
            "Payment Received" -> "கட்டணம் வரவு"
            "Other" -> "பிற வேலைகள்"
            else -> {
                // Check if starts with known base
                when {
                    workType.contains("Rotavator", ignoreCase = true) -> "ரோட்டாவேட்டர்"
                    workType.contains("5 Claws", ignoreCase = true) -> "5 கலப்பை"
                    workType.contains("9 Claws", ignoreCase = true) -> "9 கலப்பை"
                    workType.contains("Loader", ignoreCase = true) -> "லோடர்"
                    workType.contains("Plough", ignoreCase = true) -> "உழவு"
                    workType.contains("Cultivator", ignoreCase = true) -> "கொத்துக்கலப்பை"
                    workType.contains("Harvester", ignoreCase = true) -> "அறுவடை இயந்திரம்"
                    workType.contains("Leveler", ignoreCase = true) -> "லெவலர்"
                    workType.contains("Trailer", ignoreCase = true) -> "டிரெய்லர்"
                    workType.contains("Payment", ignoreCase = true) -> "கட்டணம் வரவு"
                    else -> workType
                }
            }
        }
    }

    fun getExpenseTypeDisplayName(type: String, isTamil: Boolean): String {
        if (!isTamil) return type
        return when (type) {
            "Diesel" -> "டீசல்"
            "Petrol" -> "பெட்ரோல்"
            "Repair" -> "பழுதுபார்ப்பு (ரிப்பேர்)"
            "Puncture" -> "பஞ்சர்"
            "Oil Change" -> "ஆயில் மாற்றம்"
            "Driver Bata" -> "டிரைவர் பட்டா"
            "Spare Parts" -> "உதிரிபாகங்கள்"
            "Toll / Parking" -> "டோல் / பார்க்கிங்"
            "Other" -> "பிற செலவுகள்"
            else -> type
        }
    }

    fun getWithdrawalCategoryDisplayName(cat: String, isTamil: Boolean): String {
        if (!isTamil) return cat
        return when (cat) {
            "Personal Use" -> "சொந்த உபயோகம்"
            "Fuel Advance" -> "எரிபொருள் முன்பணம்"
            "Salary" -> "சம்பளம்"
            "Profit Share" -> "லாபப் பங்கு"
            "Emergency" -> "அவசர தேவை"
            "Other" -> "பிற"
            else -> cat
        }
    }

    fun getDatePresetDisplayName(preset: DatePreset, isTamil: Boolean): String {
        if (!isTamil) return preset.label
        return when (preset) {
            DatePreset.ALL_TIME -> "தொடக்கம் முதல்"
            DatePreset.TODAY -> "இன்று"
            DatePreset.YESTERDAY -> "நேற்று"
            DatePreset.THIS_WEEK -> "இந்த வாரம்"
            DatePreset.THIS_MONTH -> "இந்த மாதம்"
            DatePreset.CUSTOM -> "தேதி வரம்பு"
        }
    }
}
