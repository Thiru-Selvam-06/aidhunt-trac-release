package com.example

import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.firebase.WorkspaceMember
import com.example.ui.util.FinancialCalculationEngine
import com.example.ui.util.FinancialScope
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CashBalanceRegressionTest {
    private val scope = FinancialScope(workspaceId = "a")

    @Test fun onlyActiveCashCollectionsFundTheAvailableBalance() {
        val payments = listOf(
            PaymentEntity(id = 1, workspaceId = "a", amount = 100.0, paymentMethod = "Cash", status = "ACTIVE"),
            PaymentEntity(id = 2, workspaceId = "a", amount = 200.0, paymentMethod = "UPI / GPay", status = "ACTIVE"),
            PaymentEntity(id = 3, workspaceId = "a", amount = 80.0, paymentMethod = "Cash", status = "REVERSED"),
            PaymentEntity(id = 4, workspaceId = "b", amount = 999.0, paymentMethod = "Cash", status = "ACTIVE")
        )
        val expenses = listOf(ExpenseEntity(id = 5, workspaceId = "a", expenseType = "Diesel", amount = 30.0,
            tractorLabel = "T", addedByPartner = "Owner", paidBy = "Business Paid"))
        val withdrawals = listOf(WithdrawalEntity(id = 6, workspaceId = "a", partnerName = "Owner", amount = 20.0))
        assertEquals(50.0, FinancialCalculationEngine.cashAvailable(payments, expenses, withdrawals, scope), 0.01)
        assertEquals(0.0, FinancialCalculationEngine.cashAvailable(payments.filter { it.id == 2L }, emptyList(), emptyList(), scope), 0.01)
    }

    @Test fun personallyFundedInvestmentDoesNotConsumeBusinessCash() {
        val payments = listOf(PaymentEntity(id = 1, workspaceId = "a", amount = 100.0,
            paymentMethod = "Cash", status = "ACTIVE"))
        val personalExpense = ExpenseEntity(id = 2, workspaceId = "a", expenseType = "Diesel",
            amount = 80.0, tractorLabel = "T", addedByPartner = "Partner", paidBy = "I Paid", paidByUid = "partner")
        assertEquals(100.0, FinancialCalculationEngine.cashAvailable(payments, listOf(personalExpense), emptyList(), scope), 0.01)
        assertEquals(20.0, FinancialCalculationEngine.cashAvailable(payments,
            listOf(personalExpense.copy(paidBy = "Business Paid")), emptyList(), scope), 0.01)
    }

    @Test fun cashDeficitRemainsVisibleAndPartnerWithdrawalIsCountedOnce() {
        val payments = listOf(PaymentEntity(id = 1, workspaceId = "a", amount = 100.0,
            paymentMethod = "Cash", status = "ACTIVE"))
        val withdrawals = listOf(WithdrawalEntity(id = 2, workspaceId = "a", partnerName = "Partner",
            targetPartnerUid = "partner", amount = 150.0))
        assertEquals(-50.0, FinancialCalculationEngine.cashAvailable(payments, emptyList(), withdrawals, scope), 0.01)
    }

    @Test fun ownerPaidPartnerJobKeepsPayerAndBeneficiarySeparate() {
        val job = JobEntryEntity(id = 1, workspaceId = "a", customerName = "Test",
            operatorName = "Partner", tractorLabel = "T", workType = "Work",
            startTimeMillis = 1, endTimeMillis = 2, durationMinutes = 1,
            totalAmount = 100.0, amountReceived = 100.0, pendingAmount = 0.0,
            addedByPartner = "Owner", createdByUid = "owner", entryForUid = "partner",
            financialAttributionUid = "partner")
        val expense = ExpenseEntity(id = 2, workspaceId = "a", expenseType = "Diesel",
            amount = 30.0, tractorLabel = "T", addedByPartner = "Owner", paidBy = "I Paid",
            paidByUid = "owner", createdByUid = "owner", expenseForUid = "partner",
            financialAttributionUid = "partner", relatedJobId = 1)
        val members = listOf(
            WorkspaceMember(uid = "owner", workspaceId = "a", role = "OWNER", status = "active", displayName = "Owner"),
            WorkspaceMember(uid = "partner", workspaceId = "a", role = "PARTNER", status = "active", displayName = "Partner")
        )
        val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            listOf(job), listOf(expense), emptyList(), workspaceMembers = members, scope = scope)
        assertEquals(30.0, statuses.single { it.partnerUid == "owner" }.totalInvestment, 0.01)
        assertEquals(0.0, statuses.single { it.partnerUid == "partner" }.totalInvestment, 0.01)
        assertEquals(0.0, FinancialCalculationEngine.cashAvailable(emptyList(), listOf(expense), emptyList(), scope), 0.01)
    }

    @Test fun unscopedMixedWorkspaceCashIsRejected() {
        try {
            FinancialCalculationEngine.cashAvailable(listOf(
                PaymentEntity(id = 1, workspaceId = "a", amount = 100.0),
                PaymentEntity(id = 2, workspaceId = "b", amount = 100.0)
            ), emptyList(), emptyList(), FinancialScope())
            fail("Mixed workspaces must not be aggregated")
        } catch (_: SecurityException) { }
    }
}
