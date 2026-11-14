package com.example

import com.example.data.auth.AuthorizationManager
import com.example.data.auth.RoleUtils
import com.example.data.entity.BorrowingRequestEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.ExternalLoanEntity
import com.example.data.entity.ExternalLoanRepaymentEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.PendingOperationEntity
import com.example.data.entity.SettlementObligationEntity
import com.example.data.entity.SettlementRepaymentEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.firebase.WorkspaceMember
import com.example.data.sync.OperationDependencies
import com.example.ui.util.FinancialCalculationEngine
import com.example.ui.util.FinancialScope
import com.example.ui.util.PartnerBorrowingCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class PartnerBorrowingCalculatorTest {

    private val ws = "ws_finance"

    @Test
    fun businessBorrowingProtectsWithdrawableEntitlementsAndReservedCashWithoutProfitOffset() {
        val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = listOf(
                JobEntryEntity(
                    id = 1L, workspaceId = ws, customerName = "Customer", operatorName = "Owner",
                    tractorLabel = "T1", workType = "Ploughing", startTimeMillis = 1000L, endTimeMillis = 4000L,
                    durationMinutes = 60L, totalAmount = 1000.0, amountReceived = 1000.0, pendingAmount = 0.0,
                    addedByPartner = "Owner", entryForUid = "owner", financialAttributionUid = "owner", isSynced = true
                )
            ),
            expenses = emptyList(),
            withdrawals = emptyList(),
            workspaceMembers = listOf(
                WorkspaceMember(uid = "owner", workspaceId = ws, role = "OWNER", status = "ACTIVE", displayName = "Owner"),
                WorkspaceMember(uid = "partnerA", workspaceId = ws, role = "PARTNER", status = "ACTIVE", displayName = "Partner A")
            ),
            profitShareAllocations = mapOf("owner" to 50, "partnerA" to 50),
            scope = FinancialScope(workspaceId = ws),
            businessName = "Test Biz"
        )

        // Cash = 2500 (e.g., 1000 job cash + 1500 business cash loan), reserved = 300, entitlements = 500 + 500 = 1000.
        // Unreserved cash = 2200 -> availableForBusinessBorrowing = 2200 - 1000 = 1200.
        val overview = PartnerBorrowingCalculator.calculate(
            confirmedCash = 2500.0,
            reservedCash = 300.0,
            statuses = statuses,
            obligations = listOf(
                SettlementObligationEntity(
                    id = 10L, workspaceId = ws, borrowerUid = "partnerA", borrowerName = "Partner A",
                    creditorType = "BUSINESS", principalAmount = 200.0, repaidAmount = 0.0, status = "OPEN"
                )
            )
        )
        assertEquals(2200.0, overview.unreservedCash, 0.01)
        assertEquals(1000.0, overview.protectedEntitlementsTotal, 0.01)
        assertEquals(1200.0, overview.availableForBusinessBorrowing, 0.01)
        // Automatic profit offset must be disabled: partnerA still shows full 500 base entitlement + separate 200 open debt
        assertEquals(500.0, overview.partnerSummaries["partnerA"]!!.withdrawableEntitlement, 0.01)
        assertEquals(200.0, overview.partnerSummaries["partnerA"]!!.openBorrowingDebt, 0.01)
    }

    @Test
    fun namedPartnerLendingAndDirectSettledRecoveryReduceLenderWithdrawablePool() {
        val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = listOf(
                JobEntryEntity(
                    id = 2L, workspaceId = ws, customerName = "Customer", operatorName = "Owner",
                    tractorLabel = "T1", workType = "Ploughing", startTimeMillis = 1000L, endTimeMillis = 4000L,
                    durationMinutes = 60L, totalAmount = 2000.0, amountReceived = 2000.0, pendingAmount = 0.0,
                    addedByPartner = "Owner", entryForUid = "owner", financialAttributionUid = "owner", isSynced = true
                )
            ),
            expenses = emptyList(),
            withdrawals = emptyList(),
            workspaceMembers = listOf(
                WorkspaceMember(uid = "owner", workspaceId = ws, role = "OWNER", status = "ACTIVE", displayName = "Owner"),
                WorkspaceMember(uid = "partnerB", workspaceId = ws, role = "PARTNER", status = "ACTIVE", displayName = "Partner B")
            ),
            profitShareAllocations = mapOf("owner" to 50, "partnerB" to 50),
            scope = FinancialScope(workspaceId = ws),
            businessName = "Test Biz"
        )
        // Partner B earned 1000, lent 400 to Owner, Owner repaid 250 DIRECT_TO_PARTNER and 150 remains OPEN.
        // Partner B's remaining withdrawable = 1000 - 150 (open lent) - 250 (direct settled outside business) = 600.
        val overview = PartnerBorrowingCalculator.calculate(
            confirmedCash = 1600.0,
            reservedCash = 0.0,
            statuses = statuses,
            obligations = listOf(
                SettlementObligationEntity(
                    id = 20L, workspaceId = ws, borrowerUid = "owner", borrowerName = "Owner",
                    creditorType = "PARTNER", creditorUid = "partnerB", creditorName = "Partner B",
                    principalAmount = 400.0, repaidAmount = 250.0, directSettledAmount = 250.0, status = "OPEN"
                )
            )
        )
        assertEquals(600.0, overview.partnerSummaries["partnerB"]!!.withdrawableEntitlement, 0.01)
        assertEquals(150.0, overview.partnerSummaries["owner"]!!.openBorrowingDebt, 0.01)
    }

    @Test
    fun loanFundedExpensesAndExternalLoanCashFlowsCalculateAccurately() {
        val cashLoan = ExternalLoanEntity(
            id = 1L, workspaceId = ws, lenderName = "Cash Lender",
            principalAmount = 1000.0, interestAmount = 100.0, destination = "BUSINESS_CASH"
        )
        val bankLoan = ExternalLoanEntity(
            id = 2L, workspaceId = ws, lenderName = "Bank Lender",
            principalAmount = 2000.0, interestAmount = 200.0, destination = "BUSINESS_BANK"
        )
        val supplierLoan = ExternalLoanEntity(
            id = 3L, workspaceId = ws, lenderName = "Fuel Depot",
            principalAmount = 500.0, interestAmount = 50.0, destination = "DIRECT_TO_SUPPLIER", linkedExpenseId = 11L
        )
        val loanFundedExpense = ExpenseEntity(
            id = 11L, workspaceId = ws, expenseType = "Fuel / Diesel", amount = 500.0,
            tractorLabel = "T1", addedByPartner = "Owner",
            paidBy = "Loan Funded", paidByUid = "", fundingSource = "EXTERNAL_LOAN",
            externalLoanId = 3L, loanFundingMode = "DIRECT_TO_SUPPLIER",
            expenseForUid = "owner", financialAttributionUid = "owner", isSynced = true
        )
        val cashRepayment = ExternalLoanRepaymentEntity(
            id = 21L, workspaceId = ws, loanId = 1L, principalPaid = 300.0, interestPaid = 50.0, source = "BUSINESS_CASH"
        )
        val bankRepayment = ExternalLoanRepaymentEntity(
            id = 22L, workspaceId = ws, loanId = 2L, principalPaid = 500.0, interestPaid = 50.0, source = "BUSINESS_BANK"
        )
        val confirmedBusinessRepay = SettlementRepaymentEntity(
            id = 31L, workspaceId = ws, obligationId = 99L, payerUid = "partnerA", creditorType = "BUSINESS",
            amount = 200.0, mode = "INTO_BUSINESS", status = "CONFIRMED"
        )
        val pendingBusinessRepay = confirmedBusinessRepay.copy(id = 32L, amount = 900.0, status = "PENDING_CONFIRMATION")
        val directConfirmedRepay = confirmedBusinessRepay.copy(id = 33L, amount = 400.0, mode = "DIRECT_TO_PARTNER", status = "CONFIRMED")

        // Cash delta = +1000 (cashLoan) - 350 (cashRepayment) + 200 (confirmed INTO_BUSINESS) = +850
        // Note: loanFundedExpense (500), bankLoan (2000), bankRepayment (550), pending (900), directConfirmedRepay (400) DO NOT change shared cash.
        val cash = FinancialCalculationEngine.cashAvailableWithStage3(
            payments = listOf(PaymentEntity(id = 1L, workspaceId = ws, amount = 1000.0, paymentMethod = "Cash", status = "ACTIVE")),
            expenses = listOf(loanFundedExpense),
            withdrawals = emptyList(),
            scope = FinancialScope(workspaceId = ws),
            externalLoans = listOf(cashLoan, bankLoan, supplierLoan),
            externalLoanRepayments = listOf(cashRepayment, bankRepayment),
            settlementRepayments = listOf(confirmedBusinessRepay, pendingBusinessRepay, directConfirmedRepay)
        )
        assertEquals(1850.0, cash, 0.01)

        // Loan-funded expense still counts in profit/loss summary expenses, and creates zero personal investment!
        val summary = FinancialCalculationEngine.calculateSummary(
            jobs = listOf(
                JobEntryEntity(
                    id = 5L, workspaceId = ws, customerName = "Customer", operatorName = "Owner",
                    tractorLabel = "T1", workType = "Ploughing", startTimeMillis = 1000L, endTimeMillis = 4000L,
                    durationMinutes = 60L, totalAmount = 1500.0, amountReceived = 1000.0, pendingAmount = 500.0,
                    addedByPartner = "Owner", entryForUid = "owner", financialAttributionUid = "owner", isSynced = true
                )
            ),
            expenses = listOf(loanFundedExpense),
            withdrawals = emptyList(),
            scope = FinancialScope(workspaceId = ws)
        )
        assertEquals(500.0, summary.totalExpenses, 0.01)
        val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = listOf(
                JobEntryEntity(
                    id = 5L, workspaceId = ws, customerName = "Customer", operatorName = "Owner",
                    tractorLabel = "T1", workType = "Ploughing", startTimeMillis = 1000L, endTimeMillis = 4000L,
                    durationMinutes = 60L, totalAmount = 1500.0, amountReceived = 1000.0, pendingAmount = 500.0,
                    addedByPartner = "Owner", entryForUid = "owner", financialAttributionUid = "owner", isSynced = true
                )
            ),
            expenses = listOf(loanFundedExpense),
            withdrawals = emptyList(),
            workspaceMembers = listOf(WorkspaceMember(uid = "owner", workspaceId = ws, role = "OWNER", status = "ACTIVE", displayName = "Owner")),
            scope = FinancialScope(workspaceId = ws)
        )
        assertEquals(0.0, statuses.single().totalInvestment, 0.01)
    }

    @Test
    fun stage3AuthorizationAndOperationDependenciesEnforcePolicies() {
        assertFalse(AuthorizationManager.canManageExternalLoans(false, RoleUtils.ROLE_OPERATOR))
        assertTrue(AuthorizationManager.canManageExternalLoans(false, RoleUtils.ROLE_PARTNER))
        assertTrue(AuthorizationManager.canManageExternalLoans(true, RoleUtils.ROLE_OWNER))

        assertTrue(AuthorizationManager.canConfirmSettlementRepayment("partnerA", RoleUtils.ROLE_PARTNER, "INTO_BUSINESS", ""))
        assertFalse(AuthorizationManager.canConfirmSettlementRepayment("owner", RoleUtils.ROLE_OWNER, "DIRECT_TO_PARTNER", "partnerB"))
        assertTrue(AuthorizationManager.canConfirmSettlementRepayment("partnerB", RoleUtils.ROLE_PARTNER, "DIRECT_TO_PARTNER", "partnerB"))

        val expenseOp = PendingOperationEntity(
            "op-exp", ws, "owner", "EXPENSE", 88L, "UPSERT", 0L, 200L,
            """{"externalLoanId":77,"fundingSource":"EXTERNAL_LOAN"}"""
        )
        assertTrue(OperationDependencies.references(expenseOp).contains("EXTERNAL_LOAN" to 77L))

        val loanRepayOp = PendingOperationEntity(
            "op-lrep", ws, "owner", "EXTERNAL_LOAN_REPAYMENT", 99L, "UPSERT", 0L, 250L,
            """{"loanId":77}"""
        )
        assertTrue(OperationDependencies.references(loanRepayOp).contains("EXTERNAL_LOAN" to 77L))

        val setRepayOp = PendingOperationEntity(
            "op-srep", ws, "partnerA", "SETTLEMENT_REPAYMENT", 66L, "UPSERT", 0L, 300L,
            """{"obligationId":55}"""
        )
        assertTrue(OperationDependencies.references(setRepayOp).contains("SETTLEMENT_OBLIGATION" to 55L))
    }
}
