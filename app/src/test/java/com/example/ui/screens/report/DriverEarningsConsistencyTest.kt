package com.example.ui.screens.report

import org.junit.Assert.assertEquals
import org.junit.Test

class DriverEarningsConsistencyTest {

    @Test
    fun testPercentageReconciliation_NormalCase() {
        // Collected 97%, Due 0%, Expenses 3%
        val data = MemberFinancialData(
            uid = "1",
            name = "Test",
            role = "Partner",
            total = 100.0,
            collected = 97.0,
            due = 0.0,
            expenses = 3.0
        )
        
        assertEquals(97, data.collectedPercent)
        assertEquals(0, data.duePercent)
        assertEquals(3, data.expensesPercent)
        assertEquals(100, data.collectedPercent + data.duePercent + data.expensesPercent)
    }

    @Test
    fun testPercentageReconciliation_RoundingUp() {
        // Raw percentages round to: 96.52, 0.21, 3.27
        // p1 = round(96.52) = 97
        // p2 = round(0.21) = 0
        // p3 = 100 - 97 - 0 = 3
        val compositionTotal = 100.0
        val data = MemberFinancialData(
            uid = "1",
            name = "Test",
            role = "Partner",
            total = 100.0,
            collected = 96.52,
            due = 0.21,
            expenses = 3.27
        )
        
        assertEquals(97, data.collectedPercent)
        assertEquals(0, data.duePercent)
        assertEquals(3, data.expensesPercent)
        assertEquals(100, data.collectedPercent + data.duePercent + data.expensesPercent)
    }

    @Test
    fun testPercentageReconciliation_RoundingDown() {
        // Raw: 94.4, 2.8, 2.8
        // p1 = round(94.4) = 94
        // p2 = round(2.8) = 3
        // p3 = 100 - 94 - 3 = 3
        val data = MemberFinancialData(
            uid = "1",
            name = "Test",
            role = "Partner",
            total = 100.0,
            collected = 94.4,
            due = 2.8,
            expenses = 2.8
        )
        
        assertEquals(94, data.collectedPercent)
        assertEquals(3, data.duePercent)
        assertEquals(3, data.expensesPercent)
        assertEquals(100, data.collectedPercent + data.duePercent + data.expensesPercent)
    }

    @Test
    fun testPercentageReconciliation_100PercentCollected() {
        val data = MemberFinancialData(
            uid = "1",
            name = "Test",
            role = "Partner",
            total = 100.0,
            collected = 100.0,
            due = 0.0,
            expenses = 0.0
        )
        
        assertEquals(100, data.collectedPercent)
        assertEquals(0, data.duePercent)
        assertEquals(0, data.expensesPercent)
        assertEquals(100, data.collectedPercent + data.duePercent + data.expensesPercent)
    }

    @Test
    fun testPercentageReconciliation_ZeroCollected() {
        val data = MemberFinancialData(
            uid = "1",
            name = "Test",
            role = "Partner",
            total = 100.0,
            collected = 0.0,
            due = 50.0,
            expenses = 50.0
        )
        
        assertEquals(0, data.collectedPercent)
        assertEquals(50, data.duePercent)
        assertEquals(50, data.expensesPercent)
        assertEquals(100, data.collectedPercent + data.duePercent + data.expensesPercent)
    }

    @Test
    fun testPercentageReconciliation_NoData() {
        val data = MemberFinancialData(
            uid = "1",
            name = "Test",
            role = "Partner",
            total = 0.0,
            collected = 0.0,
            due = 0.0,
            expenses = 0.0
        )
        
        assertEquals(0, data.collectedPercent)
        assertEquals(0, data.duePercent)
        assertEquals(0, data.expensesPercent)
        // Note: 0+0+0 is not 100, but for no data it's correct handled as 0s.
    }
}
