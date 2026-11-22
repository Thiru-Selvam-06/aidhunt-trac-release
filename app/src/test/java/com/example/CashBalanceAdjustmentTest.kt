package com.example

import com.example.data.firebase.*
import com.example.ui.util.*
import com.example.data.entity.*
import org.junit.Assert.*
import org.junit.Test

class CashBalanceAdjustmentTest {
    private val date = java.time.Instant.parse("2026-11-21T12:00:00Z").toEpochMilli()
    private fun edit(delta: Long = 50000, ws: String = "ws_test", at: Long = date) =
        CashBalanceEdit("original-operation", ws, 1, 100000, 100000 + delta, delta,
            "Original cash count", "partner", "Original name", at)

    @Test fun targetBecomesCashAndFutureCollectionsAndWithdrawalsStillMoveIt() {
        val base = 100000L
        val total = CashBalanceAdjustmentPolicy.newTotal(base, 150000)
        assertEquals(50000L, CashBalanceAdjustmentPolicy.delta(0, total))
        assertEquals(150000L, base + total)
        assertEquals(170000L, base + 20000L + total)
        assertEquals(140000L, base + 20000L - 30000L + total)
    }
    @Test fun secondCorrectionAddsOnlyItsDifferenceAndSupportsNegativeBalances() {
        val previous = CashBalanceAdjustmentPolicy.newTotal(100000, 150000)
        val next = CashBalanceAdjustmentPolicy.newTotal(120000, 100000)
        assertEquals(-70000L, CashBalanceAdjustmentPolicy.delta(previous, next))
        assertEquals(100000L, 120000 + next)
        assertEquals(-1L, CashBalanceAdjustmentPolicy.newTotal(0, -1))
        assertTrue(runCatching { CashBalanceAdjustmentPolicy.delta(5, 5) }.isFailure)
        assertTrue(runCatching { CashBalanceAdjustmentPolicy.newTotal(Long.MIN_VALUE, 0) }.isFailure)
    }
    @Test fun paiseAreExactAndSignedAdjustmentDoesNotRoundAwayOnePaisa() {
        val delta = CashBalanceAdjustmentPolicy.delta(0, CashBalanceAdjustmentPolicy.newTotal(128667, 128668))
        assertEquals(1L, delta)
        assertEquals(0.01, FinancialCalculationEngine.cashAdjustmentDelta(listOf(edit(delta)), FinancialScope(workspaceId = "ws_test")), 0.0)
    }
    @Test fun workspaceAndPeriodFiltersExcludeForeignAndHistoricalChanges() {
        val october = java.time.Instant.parse("2026-10-21T12:00:00Z").toEpochMilli()
        val rows = listOf(edit(), edit(10000, "foreign"), edit(-20000, at = october))
        assertEquals(300.0, FinancialCalculationEngine.cashAdjustmentDelta(rows, FinancialScope(workspaceId = "ws_test")), 0.0)
        assertEquals(500.0, FinancialCalculationEngine.cashAdjustmentDelta(rows,
            FinancialScope(mode = FinancialScopeMode.MONTHLY, year = 2026, month = 11, workspaceId = "ws_test")), 0.0)
        assertTrue(runCatching { FinancialCalculationEngine.cashAdjustmentDelta(rows, FinancialScope()) }.isFailure)
    }
    @Test fun correctionNeverCreatesCustomerReceiptsSalesOrDistributableProfit() {
        val job = JobEntryEntity(id=1, workspaceId="ws_test", customerId=1, customerName="Original customer",
            operatorName="Partner", tractorLabel="T", workType="Rotavator", startTimeMillis=date,
            endTimeMillis=date+3600000, durationMinutes=60, totalAmount=2000.0, amountReceived=1000.0,
            pendingAmount=1000.0, addedByPartner="Partner", createdAt=date, notes="Payment Method: Cash")
        val scope = FinancialScope(workspaceId="ws_test")
        val before = FinancialCalculationEngine.calculateSummary(listOf(job), emptyList(), emptyList(), scope, emptyList())
        val after = FinancialCalculationEngine.calculateSummary(listOf(job), emptyList(), emptyList(), scope, emptyList(), listOf(edit()))
        assertEquals(1500.0, after.availableBalance, 0.0)
        assertEquals(before.copy(availableBalance = 1500.0), after)
        assertEquals(2000.0, before.totalRecorded, 0.0)
        assertEquals(1000.0, before.totalReceived, 0.0)
        assertEquals(1000.0, before.totalDue, 0.0)
        assertEquals("Original customer", job.customerName)
        assertEquals(1000.0, job.amountReceived, 0.0)
    }
    @Test fun guardedActivationMustBeExplicitBooleanAndDefaultsToDisabled() {
        assertFalse(WorkspaceSyncCapabilities().cashBalanceAdjustmentsV1)
        assertFalse(WorkspaceSyncCapabilities.fromWorkspace(mapOf("syncCapabilities" to mapOf("cashBalanceAdjustmentsV1" to "true"))).cashBalanceAdjustmentsV1)
        assertTrue(WorkspaceSyncCapabilities.fromWorkspace(mapOf("syncCapabilities" to mapOf("cashBalanceAdjustmentsV1" to true))).cashBalanceAdjustmentsV1)
    }
    @Test fun cashErrorsOrUnconfirmedHistoryMustNeverBeReportedAsSynced() {
        val connected=CloudConnectionState.CONNECTED
        val cash=CashBalanceState("ws_test",enabled=true,loaded=true,confirmed=true)
        assertEquals(connected,CashBalanceAdjustmentPolicy.connection(connected,true,cash))
        assertEquals(CloudConnectionState.CONNECTING,CashBalanceAdjustmentPolicy.connection(connected,true,cash.copy(confirmed=false)))
        assertEquals(CloudConnectionState.CONNECTING,CashBalanceAdjustmentPolicy.connection(connected,false,cash))
        assertEquals(CloudConnectionState.ERROR,CashBalanceAdjustmentPolicy.connection(connected,true,cash.copy(error="Denied")))
        assertEquals(CloudConnectionState.ERROR,CashBalanceAdjustmentPolicy.connection(CloudConnectionState.ERROR,true,cash))
        assertEquals(connected,CashBalanceAdjustmentPolicy.connection(connected,true,CashBalanceState()))
    }
    @Test fun aCashOnlyWorkspaceCannotAccidentallyAbsorbAnotherWorkspacesAdjustment() {
        val payment=PaymentEntity(id=1,workspaceId="cash-only",amount=100.0,paymentMethod="Cash",collectedAt=date)
        assertTrue(runCatching { FinancialCalculationEngine.calculateSummary(emptyList(),emptyList(),emptyList(),
            FinancialScope(),listOf(payment),listOf(edit(ws="foreign"))) }.isFailure)
        val isolated=FinancialCalculationEngine.calculateSummary(emptyList(),emptyList(),emptyList(),
            FinancialScope(workspaceId="cash-only"),listOf(payment),listOf(edit(ws="foreign")))
        assertEquals(100.0,isolated.availableBalance,0.0)
    }
    @Test fun aCapturedCashRequestCannotRetargetAfterAnAccountOrWorkspaceSwitch() {
        CashBalanceAdjustmentPolicy.requireContext("original-ws","original-actor","original-ws","original-actor","original-actor" to "original-ws")
        for ((workspace,actor,verified) in listOf(
            Triple("different-ws","original-actor","original-actor" to "different-ws"),
            Triple("original-ws","different-actor","different-actor" to "original-ws"),
            Triple("original-ws","original-actor",null))) {
            assertTrue(runCatching { CashBalanceAdjustmentPolicy.requireContext("original-ws","original-actor",workspace,actor,verified) }.isFailure)
        }
    }
}
