package com.example

import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.PendingOperationEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.expense.ExpenseAnalytics
import com.example.pdf.balanceSheetPages
import com.example.ui.screens.report.BalanceSheetTabMode
import com.example.ui.screens.report.ReceiptPeriodReport
import com.example.ui.util.FinancialCalculationEngine
import com.example.ui.util.FinancialScope
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FinancialReconciliationTest {
    private val workDate = at("2026-10-08T01:00:00Z")
    private val laterDay = at("2026-10-09T01:00:00Z")
    private val laterMonth = at("2026-11-13T01:00:00Z")
    private val scope = FinancialScope(workspaceId = "w")

    private fun at(instant: String) = Instant.parse(instant).toEpochMilli()

    private fun job(id: Long = 1, paid: Double = 0.0, amount: Double = 1000.0,
        date: Long = workDate, historical: Boolean = false, notes: String = "") =
        JobEntryEntity(id = id, workspaceId = "w", customerId = id, customerName = "Synthetic customer",
            operatorName = "Partner", tractorLabel = "T", workType = "Rotavator", startTimeMillis = date,
            endTimeMillis = date + 3600000, durationMinutes = 60, totalAmount = amount,
            amountReceived = paid, pendingAmount = amount - paid, addedByPartner = "Partner", createdAt = laterMonth,
            isHistorical = historical, notes = notes)

    private fun payment(id: Long = 1, amount: Double = 1000.0, at: Long = workDate,
        jobId: Long = 1, method: String = "Cash") =
        PaymentEntity(id = id, workspaceId = "w", jobEntryId = jobId, customerId = jobId,
            amount = amount, paymentMethod = method, collectedAt = at, createdAt = laterMonth)

    private fun expense(id: Long = 1, amount: Double, paidBy: String = "Business Paid", date: Long = workDate) =
        ExpenseEntity(id = id, workspaceId = "w", expenseType = "Diesel", amount = amount, tractorLabel = "T",
            addedByPartner = "Partner", dateTimestamp = date, createdAt = laterMonth, paidBy = paidBy)

    private fun report(jobs: List<JobEntryEntity> = emptyList(), payments: List<PaymentEntity> = emptyList(),
        expenses: List<ExpenseEntity> = emptyList(), mode: BalanceSheetTabMode = BalanceSheetTabMode.DAILY,
        actions: List<PendingOperationEntity> = emptyList()) =
        ReceiptPeriodReport.project("w", payments, expenses, jobs, mode, actions = actions)

    @Test fun fin001PaidOnWorkDateAndFin002UnpaidJob() {
        val paid = job(paid = 1000.0)
        val unpaid = job(id = 2)
        val result = report(listOf(paid, unpaid), listOf(payment()))
        assertEquals(1000.0, result.totalReceived, 0.0)
        assertEquals("08 Oct 2026 (Thu)", result.periods.single().title)
        assertEquals(0.0, result.embeddedUndatedReceived, 0.0)
        assertEquals(1000.0, FinancialCalculationEngine.calculateSummary(listOf(paid, unpaid), emptyList(), emptyList(), scope).totalDue, 0.0)
        assertTrue(report(listOf(unpaid)).periods.isEmpty())
        assertEquals(0.0, report(listOf(unpaid)).totalReceived, 0.0)
    }

    @Test fun fin003And004LateDueCollectionsUseTheirOwnDayAndMonth() {
        val source = job(paid = 1000.0)
        val payments = listOf(payment(amount = 300.0, at = laterDay), payment(id = 2, amount = 700.0, at = laterMonth))
        assertEquals(listOf("13 Nov 2026 (Fri)", "09 Oct 2026 (Fri)"), report(listOf(source), payments).periods.map { it.title })
        assertEquals(listOf("November 2026", "October 2026"),
            report(listOf(source), payments, mode = BalanceSheetTabMode.MONTHLY).periods.map { it.title })
    }

    @Test fun fin005006And010HistoricalVerificationUsesCollectionDateNotRecordingDate() {
        val oldDate = at("2024-07-12T01:00:00Z")
        val fullyPaid = job(paid = 1000.0, date = oldDate, historical = true)
        val late = job(id = 2, paid = 1000.0, date = oldDate, historical = true)
        val rows = listOf(payment(at = oldDate), payment(id = 2, at = laterMonth, jobId = 2))
        val result = report(listOf(fullyPaid, late), rows, mode = BalanceSheetTabMode.MONTHLY)
        assertEquals(listOf("November 2026", "July 2024"), result.periods.map { it.title })
        assertEquals(listOf(1000.0, 1000.0), result.periods.map { it.received })
        assertEquals(listOf("2026", "2024"), report(listOf(fullyPaid, late), rows, mode = BalanceSheetTabMode.YEARLY).periods.map { it.title })
        assertEquals(laterMonth, result.periods.last().payments.single().createdAt)
        assertEquals(0.0, result.embeddedUndatedReceived, 0.0)
    }

    @Test fun fin007And008PartialPaymentsCountOnceAndReversalRestatesOriginalPeriod() {
        val first = payment(amount = 350.25)
        val second = payment(id = 2, amount = 449.75, at = laterMonth)
        val source = job(paid = 800.0)
        assertEquals(800.0, report(listOf(source), listOf(first, first, second)).totalReceived, 0.0)
        val reversed = second.copy(status = "REVERSED", reversedAt = at("2027-02-01T01:00:00Z"))
        val result = report(listOf(source.copy(amountReceived = 350.25, pendingAmount = 649.75)), listOf(first, reversed, reversed))
        assertEquals("08 Oct 2026 (Thu)", result.periods.single().title)
        assertEquals(350.25, result.totalReceived, 0.0)
        assertEquals(0.0, result.embeddedUndatedReceived, 0.0)
    }

    @Test fun fin009011And012LegacyRemainderAndExplicitUndatedRecordsStayOverallOnly() {
        val source = job(paid = 750.25, notes = "Cash")
        val documented = payment(amount = 400.0)
        val undated = payment(id = 2, amount = 100.0, at = 0L)
        val result = report(listOf(source), listOf(documented, undated, documented))
        assertEquals(400.0, result.periods.single().received, 0.0)
        assertEquals(250.25, result.embeddedUndatedReceived, 0.0)
        assertEquals(100.0, result.explicitUndatedReceived, 0.0)
        assertEquals(750.25, result.totalReceived, 0.0)
        assertEquals(result.totalReceived, result.pdfRows(false).sumOf { it.sales }, 0.0)
        assertEquals(0L, undated.collectedAt)
    }

    @Test fun fin011And020SplitAndAmbiguousAllocationsDoNotInventExtraReceipts() {
        val first = job(id = 9007199254740995L, paid = 220.25)
        val second = job(id = 9007199254740997L, paid = 120.25).copy(customerId = first.customerId)
        val receipt = payment(amount = 220.50, jobId = first.id).copy(allocationsJson =
            """[{"jobId":9007199254740995,"amount":100.25},{"jobId":9007199254740997,"amount":120.25}]""")
        val split = report(listOf(first, second), listOf(receipt, receipt))
        assertEquals(220.50, split.datedReceived, 0.0)
        assertEquals(120.0, split.embeddedUndatedReceived, 0.0)
        val ambiguous = receipt.copy(customerId = first.customerId, allocationsJson = "malformed")
        val unresolved = report(listOf(first, second), listOf(ambiguous))
        assertEquals(220.50, unresolved.totalReceived, 0.0)
        assertEquals(0.0, unresolved.embeddedUndatedReceived, 0.0)
        assertEquals("malformed", ambiguous.allocationsJson)
    }

    @Test fun fin013019And020ReportedDifferencesReproduceOnSyntheticLedger() {
        val embedded = listOf(2500.0, 500.0, 4000.0, 1100.0, 800.0, 2000.0, 6000.0)
            .mapIndexed { index, amount ->
                val paid = if (index == 4) amount + 900.0 else amount
                job(id = index + 2L, paid = paid, amount = paid, notes = if (index in setOf(0, 5)) "" else "Cash")
            }
        val jobs = listOf(job(paid = 155800.0, amount = 155800.0)) + embedded
        val payments = listOf(payment(amount = 155800.0), payment(id = 2, amount = 900.0, jobId = 6, at = laterMonth))
        val expenses = listOf(expense(amount = 142220.0))
        val projected = report(jobs, payments, expenses, BalanceSheetTabMode.YEARLY)
        assertEquals(156700.0, projected.periods.single().received, 0.0)
        assertEquals(16900.0, projected.embeddedUndatedReceived, 0.0)
        assertEquals(173600.0, projected.totalReceived, 0.0)
        assertEquals(31380.0, projected.netBalance, 0.0)
        val cash = FinancialCalculationEngine.cashAvailable(payments, expenses, emptyList(), scope, jobs)
        assertEquals(26880.0, cash, 0.0)
        assertEquals(4500.0, projected.netBalance - cash, 0.0)
        assertEquals(2, projected.periods.single().payments.size)
        assertEquals(156700.0, payments.sumOf { it.amount }, 0.0)
        assertEquals(7, embedded.size)
    }

    @Test fun fin014Through018CashAndFundingRulesStayDistinct() {
        val receipts = listOf(payment(amount = 1000.0), payment(id = 2, amount = 200.0, method = "UPI / GPay"),
            payment(id = 3, amount = 300.0, method = "Bank Transfer"))
        val business = expense(amount = 100.0)
        val personal = expense(id = 2, amount = 50.0, paidBy = "I Paid")
        assertEquals(1350.0, report(payments = receipts, expenses = listOf(business, personal)).netBalance, 0.0)
        assertEquals(900.0, FinancialCalculationEngine.cashAvailable(receipts, listOf(business, personal), emptyList(), scope), 0.0)
        assertEquals(report(payments = receipts.take(1), expenses = listOf(business)).netBalance,
            FinancialCalculationEngine.cashAvailable(receipts.take(1), listOf(business), emptyList(), scope), 0.0)
        val withdrawal = WithdrawalEntity(1L, "w", partnerName = "Partner", amount = 75.25, timestamp = laterDay)
        assertEquals(824.75, FinancialCalculationEngine.cashAvailable(receipts, listOf(business, personal), listOf(withdrawal), scope), 0.0)
        assertEquals(150.0, report(payments = receipts, expenses = listOf(business, personal)).totalExpenses, 0.0)
    }

    @Test fun fin021IstMidnightBoundariesApplyToReceiptsAndExpensesForEveryMode() {
        val before = at("2026-12-31T18:29:59.999Z")
        val after = at("2026-12-31T18:30:00Z")
        for (mode in BalanceSheetTabMode.entries) {
            val result = report(payments = listOf(payment(amount = 100.0, at = before), payment(id = 2, amount = 200.0, at = after)),
                expenses = listOf(expense(amount = 5.0, date = before), expense(id = 2, amount = 10.0, date = after)), mode = mode)
            assertEquals(listOf(200.0, 100.0), result.periods.map { it.received })
            assertEquals(listOf(10.0, 5.0), result.periods.map { it.spent })
        }
    }

    @Test fun fin022And023PdfKeepsEveryPeriodUndatedExpensesAndRefunds() {
        val expenses = listOf(expense(amount = 200.25), expense(id = 2, amount = -25.25), expense(id = 3, amount = -5.0, date = 0))
        val receipts = (1L..31L).map { payment(id = it, amount = 10.25, at = workDate + it * 86400000) }
        val result = report(payments = receipts, expenses = expenses)
        assertEquals(170.0, result.totalExpenses, 0.0)
        assertEquals(-5.0, result.undatedExpenses, 0.0)
        assertEquals(170.0, ExpenseAnalytics.from(expenses).net, 0.0)
        val pages = balanceSheetPages(result.pdfRows(false))
        assertEquals(2, pages.size)
        assertEquals(result.pdfRows(false), pages.flatten())
        assertTrue(pages.last().last().periodLabel.contains("overall only"))
        assertEquals(result.totalReceived, pages.flatten().sumOf { it.sales }, 0.0)
        assertEquals(result.totalExpenses, pages.flatten().sumOf { it.expenses }, 0.0)
        assertEquals(result.netBalance, pages.flatten().sumOf { it.balance }, 0.0)
    }

    @Test fun fin024PendingAndRejectedActionsAreIdentifiedWithoutReadingQueueAmounts() {
        val pending = payment(amount = 123.25).copy(isSynced = false)
        val rejected = PendingOperationEntity("rejected", "w", "partner", "PAYMENT", 6, "UPSERT", 1, laterDay,
            """{"amount":9999.0}""", state = "REJECTED")
        val pendingDelete = rejected.copy(operationId = "delete", entityType = "EXPENSE", state = "PENDING")
        val current = report(payments = listOf(pending), actions = listOf(rejected, pendingDelete))
        assertEquals(123.25, current.totalReceived, 0.0)
        assertTrue(current.hasPendingChanges); assertTrue(current.needsReview)
        assertEquals(2, current.syncNotices(false).size)
        val restored = report(payments = listOf(pending.copy(isSynced = true)), actions = listOf(rejected))
        assertFalse(restored.hasPendingChanges); assertTrue(restored.needsReview)
        assertEquals(123.25, restored.totalReceived, 0.0)
        assertTrue(report(actions = listOf(pendingDelete)).hasPendingChanges)
        assertEquals(0.0, report(actions = listOf(rejected)).totalReceived, 0.0)
    }
}
