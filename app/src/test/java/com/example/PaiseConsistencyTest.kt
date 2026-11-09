package com.example

import com.example.data.entity.*
import com.example.data.firebase.WorkspaceMember
import com.example.ui.components.formatAmountText
import com.example.ui.components.formatInr
import com.example.ui.util.*
import com.example.ui.viewmodel.NewEntryDraft
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Approved paise policy: independent recorded amounts, derived ledgers and cash/claims must agree. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class PaiseConsistencyTest {
    private val customer = CustomerEntity(id = 42, workspaceId = "w", name = "Raw தமிழ் ? Customer", phone = "")
    private fun job(billed: Double = 850.25, paid: Double = 0.25) = JobEntryEntity(id = 7, workspaceId = "w",
        customerId = 42, customerName = customer.name, operatorName = "M", tractorLabel = "Tractor",
        workType = "Rotavator", startTimeMillis = 1000, endTimeMillis = 61000, durationMinutes = 1,
        totalAmount = billed, amountReceived = paid, pendingAmount = billed - paid, addedByPartner = "M",
        createdByUid = "m", entryForUid = "m", financialAttributionUid = "m", createdAt = 1000)
    private val members = listOf(WorkspaceMember(uid = "m", workspaceId = "w", role = "Owner", status = "ACTIVE", displayName = "M"),
        WorkspaceMember(uid = "p", workspaceId = "w", role = "Partner", status = "ACTIVE", displayName = "P"),
        WorkspaceMember(uid = "s", workspaceId = "w", role = "Partner", status = "ACTIVE", displayName = "S"))
    private val scope = FinancialScope(workspaceId = "w")

    @Test fun customerHomeAndCashReconcileWithoutDroppingAQuarterRupeeOrDoubleCountingItsReceipt() {
        val source = job(); val before = source.copy()
        val projection = FinancialCalculationEngine.calculateCustomerFinancials(customer, listOf(source))
        val summary = FinancialCalculationEngine.calculateSummary(listOf(source), emptyList(), emptyList(), scope)
        val receipt = PaymentEntity(id = 8, workspaceId = "w", customerId = 42, jobEntryId = 7,
            customerName = customer.name, amount = 0.25, paymentMethod = "Cash", collectedAt = 1000)
        assertEquals(850.25, projection.totalBilled, 0.0); assertEquals(0.25, projection.totalPaid, 0.0)
        assertEquals(850.0, projection.balanceDue, 0.0)
        assertEquals(projection.totalBilled, summary.totalRecorded, 0.0)
        assertEquals(projection.totalPaid, summary.totalReceived, 0.0); assertEquals(projection.balanceDue, summary.totalDue, 0.0)
        assertEquals(0.25, FinancialCalculationEngine.cashAvailable(listOf(receipt), emptyList(), emptyList(), scope, listOf(source)), 0.0)
        assertEquals(before, source)
        val small = FinancialCalculationEngine.calculateCustomerFinancials(customer, listOf(job(0.25, 0.0)))
        assertEquals(0.25, small.balanceDue, 0.0)
    }

    @Test fun quarterRupeeCollectionAndTenPaisaExpenseKeepCashProfitAndInvestmentDistinct() {
        val source = job(1.0, 0.25)
        val receipt = PaymentEntity(id = 8, workspaceId = "w", customerId = 42, jobEntryId = 7,
            customerName = customer.name, amount = 0.25, paymentMethod = "Cash", collectedAt = 1000)
        val personal = ExpenseEntity(id = 9, workspaceId = "w", expenseType = "Diesel", amount = 0.10,
            tractorLabel = "Tractor", addedByPartner = "M", paidBy = "I Paid", paidByPartner = "M", paidByUid = "m", expenseForUid = "m", createdByUid = "m", dateTimestamp = 1000)
        val claims = FinancialCalculationEngine.calculatePartnerShareAndInvestment(listOf(source), listOf(personal), emptyList(),
            workspaceMembers = members, scope = scope)
        assertEquals(0.25, FinancialCalculationEngine.cashAvailable(listOf(receipt), listOf(personal), emptyList(), scope, listOf(source)), 0.0)
        assertEquals(0.15, FinancialCalculationEngine.roundMoney(claims.sumOf { it.totalShare }), 0.0)
        assertEquals(0.10, claims.single { it.partnerUid == "m" }.totalInvestment, 0.0)
        assertEquals(0.25, FinancialCalculationEngine.roundMoney(claims.sumOf { it.totalToGet }), 0.0)
        val business = personal.copy(paidBy = "Business Paid", paidByUid = "", paidByPartner = "Business")
        val businessClaims = FinancialCalculationEngine.calculatePartnerShareAndInvestment(listOf(source), listOf(business), emptyList(),
            workspaceMembers = members, scope = scope)
        assertEquals(0.15, FinancialCalculationEngine.cashAvailable(listOf(receipt), listOf(business), emptyList(), scope, listOf(source)), 0.0)
        assertEquals(0.0, businessClaims.sumOf { it.totalInvestment }, 0.0)
        assertEquals(0.15, FinancialCalculationEngine.roundMoney(businessClaims.sumOf { it.totalToGet }), 0.0)
    }

    @Test fun billingUsesExactDecimalRateBeforePaisaRoundingWithoutChangingDuration() {
        assertEquals(568.33, WorkBillingCalculator.calculateAmount(31, 1100.0), 0.0)
        assertEquals(0.04, WorkBillingCalculator.calculateAmount(2, 1.05), 0.0)
        assertEquals(0.02, WorkBillingCalculator.calculateAmount(1, 1.0), 0.0)
        assertEquals(0.0, WorkBillingCalculator.calculateAmount(0, 1100.0), 0.0)
        assertEquals(31L, WorkBillingCalculator.calculateDurationFromManual("0", "31"))
    }

    @Test fun signedBusinessRefundRetainsPaisaWithoutBecomingAnotherExpenseOrCashOutflow() {
        val source = job(1.0, 0.25)
        val receipt = PaymentEntity(id = 8, workspaceId = "w", customerId = 42, jobEntryId = 7,
            customerName = customer.name, amount = 0.25, paymentMethod = "Cash", collectedAt = 1000)
        val charge = ExpenseEntity(id = 9, workspaceId = "w", expenseType = "Diesel", amount = 0.10,
            tractorLabel = "Tractor", addedByPartner = "M", paidBy = "Business Paid", dateTimestamp = 1000)
        val refund = charge.copy(id = 10, amount = -0.05)
        val before = listOf(charge.copy(), refund.copy())
        val summary = FinancialCalculationEngine.calculateSummary(listOf(source), before, emptyList(), scope)
        val claims = FinancialCalculationEngine.calculatePartnerShareAndInvestment(listOf(source), before, emptyList(),
            workspaceMembers = members, scope = scope)
        assertEquals(0.05, summary.totalExpenses, 0.0)
        assertEquals(0.20, FinancialCalculationEngine.cashAvailable(listOf(receipt), before, emptyList(), scope, listOf(source)), 0.0)
        assertEquals(0.20, FinancialCalculationEngine.roundMoney(claims.sumOf { it.totalShare }), 0.0)
        assertEquals(0.05, FinancialCalculationEngine.roundMoney(claims.sumOf { it.businessFundedCost }), 0.0)
        assertEquals(-0.05, refund.amount, 0.0); assertEquals(charge, before[0]); assertEquals(refund, before[1])
    }

    @Test fun reopeningOldQuoteOrUnusualHistoricalPrecisionDoesNotRepriceANotesEdit() {
        for (original in listOf(job(568.0, 0.0).copy(durationMinutes = 31, hourlyRate = 1100.0),
            job(850.254, 0.125).copy(hourlyRate = 1100.125))) {
            val draft = NewEntryDraft.fromJobEntry(original)
            assertEquals(storedMoneyInput(original.hourlyRate), draft.hourlyRateInput)
            assertEquals(storedMoneyInput(original.amountReceived), draft.amountReceivedInput)
            val kept = draft.retainUneditedWork(original.copy(totalAmount = 999.99, amountReceived = 1.0,
                pendingAmount = 998.99, hourlyRate = 1200.0, notes = "Edited raw தமிழ் ? note"))
            assertEquals(original.totalAmount, kept.totalAmount, 0.0); assertEquals(original.amountReceived, kept.amountReceived, 0.0)
            assertEquals(original.pendingAmount, kept.pendingAmount, 0.0); assertEquals(original.hourlyRate, kept.hourlyRate, 0.0)
            assertEquals(original.createdAt, kept.createdAt); assertEquals(original.createdByUid, kept.createdByUid)
            assertEquals("Edited raw தமிழ் ? note", kept.notes)
        }
        val old = NewEntryDraft.fromJobEntry(job(568.0, 0.0).copy(durationMinutes = 31, hourlyRate = 1100.0))
        assertTrue(old.isWorkAmountManuallyEdited); assertEquals("568", old.customWorkAmountInput)
    }

    @Test fun summariesPdfAndSharingKeepPaisaAndSignedAdjustments() {
        assertEquals("850.25", formatAmountText(850.25)); assertEquals("0.25", formatAmountText(0.25))
        assertEquals("-0.05", formatAmountText(-0.05)); assertEquals("850", formatAmountText(850.0))
        assertEquals("₹850.25", formatInr(850.25))
        assertEquals("₹850.25", com.example.pdf.PdfGeneratorHelper.formatInr(850.25))
        assertTrue(com.example.ui.components.buildJobWhatsAppMessage(job(), "Raw தமிழ் business ?").contains("850.25"))
    }

    @Test fun percentageAllocationRetainsItsWeightAndNeverDistributesTheSamePaisaTwice() {
        val weights = linkedMapOf("main" to 50).apply { repeat(50) { put("p%02d".format(it), 1) } }
        val shares = percentageMoneyShares(0.50, weights)
        assertEquals(0.25, shares["main"]!!, 0.0)
        assertEquals(0.50, FinancialCalculationEngine.roundMoney(shares.values.sum()), 0.0)
        assertEquals(25, shares.filterKeys { it != "main" }.values.count { it == 0.01 })
        assertEquals(shares, percentageMoneyShares(0.50, weights.entries.reversed().associate { it.toPair() }))
        assertThrows(IllegalArgumentException::class.java) { percentageMoneyShares(1.0, mapOf("m" to 99)) }
    }

    @Test fun collectionPlanAndDatePreviewDoNotCreateATinyThirdReceiptFromBinaryRemainders() {
        val jobs = listOf(job(0.10, 0.0).copy(id = 1, startTimeMillis = 1000),
            job(0.20, 0.0).copy(id = 2, startTimeMillis = 2000),
            job(10.0, 0.0).copy(id = 3, startTimeMillis = 3000, createdAt = 9999999999999))
        val before = jobs.map { it.copy() }
        val plan = com.example.data.sync.planCollectionAmounts(jobs, "w", 42, 0.30)
        assertEquals(listOf(1L, 2L), plan.map { it.job.id })
        assertEquals(listOf(0.10, 0.20), plan.map { it.amount })
        assertEquals(listOf(0.0, 0.0), plan.map { it.pendingAmount })
        assertEquals(listOf(0.10, 0.20), plan.map { it.amountReceived })
        assertEquals(plan.map { it.job.id }, com.example.data.auth.CollectionPolicy.allocatedJobs(jobs, "w", 42, 0.30).map { it.id })
        assertEquals(before, jobs)
    }

    @Test fun allocationAcceptsBinaryNoiseButRetainsAndRejectsUnrepresentableHistoricalAmounts() {
        val noisy = job(0.30000000000000004, 0.10).copy(pendingAmount = 0.20000000000000004)
        val paid = com.example.data.sync.planCollectionAmounts(listOf(noisy), "w", 42, 0.20).single()
        assertEquals(0.30, paid.amountReceived, 0.0); assertEquals(0.0, paid.pendingAmount, 0.0)
        assertEquals(noisy, paid.job)
        val historical = job(850.254, 0.125); val before = historical.copy()
        assertThrows(IllegalArgumentException::class.java) {
            com.example.data.sync.planCollectionAmounts(listOf(historical), "w", 42, 0.01)
        }
        assertEquals(before, historical)
        for (amount in listOf(0.0, -0.01, 0.001, Double.NaN, Double.POSITIVE_INFINITY, 10.0))
            assertThrows(IllegalArgumentException::class.java) {
                com.example.data.sync.planCollectionAmounts(listOf(job(1.0, 0.0)), "w", 42, amount)
            }
        assertThrows(IllegalArgumentException::class.java) {
            com.example.data.sync.planCollectionAmounts(listOf(job(1.0, 0.0)), "foreign", 42, 0.25)
        }
    }

    @Test fun reversingAReceiptRestoresExactPaisaAndPreservesItsSourceAndOriginalAmount() {
        val source = job(0.30, 0.30)
        val reversal = com.example.data.sync.planCollectionReversal(source, 0.20)
        assertEquals(0.20, reversal.amount, 0.0)
        assertEquals(0.10, reversal.amountReceived, 0.0)
        assertEquals(0.20, reversal.pendingAmount, 0.0)
        assertEquals(0.30, source.amountReceived, 0.0)
        assertEquals(0.0, source.pendingAmount, 0.0)
        assertEquals(source, reversal.job)
        assertThrows(IllegalArgumentException::class.java) {
            com.example.data.sync.planCollectionReversal(source, 0.31)
        }
    }

    @Test fun aggregateMoneyFromSeparateCustomersHasNoFractionalFloatingPointRemainder() {
        val first = job(0.10, 0.0)
        val second = job(0.20, 0.0).copy(id = 9, customerId = 43)
        val summary = FinancialCalculationEngine.calculateSummary(listOf(first, second), emptyList(), emptyList(), scope)
        assertEquals(0.30, summary.totalRecorded, 0.0)
        assertEquals(0.30, summary.totalDue, 0.0)
    }
}
