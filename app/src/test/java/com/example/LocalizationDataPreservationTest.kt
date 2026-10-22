package com.example

import com.example.ui.util.Localization
import org.junit.Assert.assertEquals
import org.junit.Test

class LocalizationDataPreservationTest {
    @Test fun customWorkTypeNamesArePreservedInBothLanguages() {
        listOf("Rotavator 7ft — என் வகை", "Loader Rental Plus", "Ploughing Custom Job",
            "Payment tank cleaning", "My Trailer", "All", "Other தமிழ்").forEach { entered ->
            assertEquals(entered, Localization.getWorkTypeDisplayName(entered, true))
            assertEquals(entered, Localization.getWorkTypeDisplayName(entered, false))
        }
    }

    @Test fun builtInAppOptionsStillTranslateWithoutChangingTheEnglishValue() {
        assertEquals("ரோட்டாவேட்டர்", Localization.getWorkTypeDisplayName("Rotavator", true))
        assertEquals("Rotavator", Localization.getWorkTypeDisplayName("Rotavator", false))
        assertEquals("கட்டணம் வரவு", Localization.getWorkTypeDisplayName("Payment Received", true))
        assertEquals("Payment Received", Localization.getWorkTypeDisplayName("Payment Received", false))
    }
}
