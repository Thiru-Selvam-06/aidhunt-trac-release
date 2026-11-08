package com.example

import com.example.data.entity.ExpenseEntity
import com.example.data.expense.ExpenseAnalytics
import com.example.data.expense.ExpenseDateRange
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class ExpenseAnalyticsTest {
    private fun expense(id: Long, category: String, amount: Double, paidBy: String, synced: Boolean = true) =
        ExpenseEntity(id = id, workspaceId = "w", expenseType = category, amount = amount, tractorLabel = "T",
            addedByPartner = "Original", paidBy = paidBy, isSynced = synced)
    private fun at(text: String) = Instant.parse(text).toEpochMilli()

    @Test fun categoryAndFundingReconcileSignedMoneyWithoutNegativePieSlices() {
        val records = listOf(expense(1, "Diesel", 100.25, "Business Paid", false),
            expense(2, "DIESEL", -20.10, "Business Paid"), expense(3, "Labour", 50.50, "I Paid"),
            expense(4, "Other", -3.30, "Unavailable"), expense(5, "Other", Double.NaN, "I Paid"))
        val copy = records.toList()
        val result = ExpenseAnalytics.from(records)
        assertEquals(150.75, result.charged, 0.000001)
        assertEquals(23.40, result.refunded, 0.000001)
        assertEquals(127.35, result.net, 0.000001)
        assertEquals(1, result.invalidCount); assertEquals(1, result.pendingCount)
        assertEquals(80.15, result.funding.single { it.key == "BUSINESS" }.net, 0.000001)
        assertEquals(50.50, result.funding.single { it.key == "PERSONAL" }.net, 0.000001)
        assertEquals(-3.30, result.funding.single { it.key == "UNKNOWN" }.net, 0.000001)
        assertEquals(result.net, result.categories.sumOf { it.net }, 0.000001)
        assertEquals(result.net, result.funding.sumOf { it.net }, 0.000001)
        assertTrue((result.categories + result.funding).all { it.charged >= 0 && it.refunded >= 0 })
        assertEquals(copy, records)
    }

    @Test fun refundsWithoutChargesRemainNegativeRatherThanDisappear() {
        val result = ExpenseAnalytics.from(listOf(expense(1, "Other", -20.75, "Business Paid")))
        assertEquals(0.0, result.charged, 0.0); assertEquals(20.75, result.refunded, 0.0)
        assertEquals(-20.75, result.net, 0.0)
    }

    @Test fun dayMonthYearAndCustomUseIstBoundariesIncludingFinalMillisecond() {
        val previous = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"))
            val midnight = at("2026-08-01T18:30:00Z") // 2 August IST
            val now = at("2026-08-02T05:00:00Z")
            fun includes(t: Long, p: ExpenseDateRange.Period) = ExpenseDateRange.contains(t, p, now, midnight, midnight + 86399999)
            assertFalse(includes(midnight - 1, ExpenseDateRange.Period.TODAY))
            assertTrue(includes(midnight, ExpenseDateRange.Period.TODAY))
            assertTrue(includes(midnight + 86399999, ExpenseDateRange.Period.CUSTOM))
            assertFalse(includes(midnight + 86400000, ExpenseDateRange.Period.CUSTOM))
            assertFalse(includes(at("2026-07-31T18:29:59.999Z"), ExpenseDateRange.Period.THIS_MONTH))
            assertTrue(includes(at("2026-07-31T18:30:00Z"), ExpenseDateRange.Period.THIS_MONTH))
            assertFalse(includes(at("2025-12-31T18:29:59.999Z"), ExpenseDateRange.Period.THIS_YEAR))
            assertTrue(includes(at("2025-12-31T18:30:00Z"), ExpenseDateRange.Period.THIS_YEAR))
            assertTrue(includes(0, ExpenseDateRange.Period.ALL_TIME))
            assertFalse(includes(0, ExpenseDateRange.Period.TODAY))
            assertFalse(ExpenseDateRange.contains(midnight, ExpenseDateRange.Period.CUSTOM, now, midnight + 1, midnight))
        } finally { TimeZone.setDefault(previous) }
    }
}
