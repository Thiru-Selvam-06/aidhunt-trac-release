package com.example

import com.example.ui.components.formatInr
import com.example.ui.components.formatInrWithDecimals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class ReportCurrencyFormatTest {
    @Test fun thousandsRemainCommaAndPaiseRemainDecimalAcrossDeviceLocales() {
        val original = Locale.getDefault()
        try {
            listOf(Locale.US, Locale.GERMANY, Locale.forLanguageTag("ta-IN"), Locale.forLanguageTag("en-IN")).forEach {
                Locale.setDefault(it)
                assertEquals("₹5,786.67", formatInr(5786.67))
                assertEquals("₹12,643.33", formatInr(12643.33))
                assertEquals("₹180,100", formatInr(180100.0))
                assertEquals("₹-4,500", formatInr(-4500.0))
                assertEquals("₹0.01", formatInr(0.01))
                assertEquals("₹1,000.00", formatInrWithDecimals(1000.0))
            }
        } finally { Locale.setDefault(original) }
    }
}
