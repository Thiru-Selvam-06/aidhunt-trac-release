package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.BorrowingRequestEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.ExternalLoanEntity
import com.example.data.entity.ExternalLoanRepaymentEntity
import com.example.data.entity.SettlementObligationEntity
import com.example.data.entity.SettlementRepaymentEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class Stage3RoomDatabaseTest {

    @Test
    fun stage3EntitiesPersistWithSeparatePrincipalInterestAndLoanFunding() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val ws = "ws_room_s3"
            val dao = db.stage3Dao()
            val loan = ExternalLoanEntity(
                id = 1001L, workspaceId = ws, lenderName = "Co-op Bank",
                principalAmount = 5000.0, interestAmount = 500.0,
                totalRepaidPrincipal = 1000.0, totalRepaidInterest = 100.0,
                destination = "DIRECT_TO_SUPPLIER", linkedExpenseId = 2001L, status = "ACTIVE"
            )
            dao.insertExternalLoan(loan)
            val savedLoan = dao.getExternalLoanById(ws, 1001L)!!
            assertEquals(4000.0, savedLoan.remainingPrincipal, 0.001)
            assertEquals(400.0, savedLoan.remainingInterest, 0.001)
            assertEquals(2001L, savedLoan.linkedExpenseId)

            dao.insertExternalLoanRepayment(
                ExternalLoanRepaymentEntity(
                    id = 1002L, workspaceId = ws, loanId = 1001L,
                    principalPaid = 1000.0, interestPaid = 100.0, source = "BUSINESS_BANK"
                )
            )
            assertEquals(1, dao.observeExternalLoanRepayments(ws).first().size)

            db.expenseDao().insertExpense(
                ExpenseEntity(
                    id = 2001L, workspaceId = ws, expenseType = "Fuel / Diesel", amount = 5000.0,
                    tractorLabel = "T1", addedByPartner = "Owner",
                    paidBy = "Loan Funded", fundingSource = "EXTERNAL_LOAN",
                    externalLoanId = 1001L, loanFundingMode = "DIRECT_TO_SUPPLIER",
                    expenseForUid = "owner", financialAttributionUid = "owner"
                )
            )
            val savedExp = db.expenseDao().getExpenseById(ws, 2001L)!!
            assertEquals("EXTERNAL_LOAN", savedExp.fundingSource)
            assertEquals(1001L, savedExp.externalLoanId)

            dao.insertBorrowingRequest(
                BorrowingRequestEntity(
                    id = 3001L, workspaceId = ws, borrowerUid = "partnerA", borrowerName = "Partner A",
                    lendingPartnerUid = "partnerB", lendingPartnerName = "Partner B",
                    sourceType = "NAMED_PARTNER", amount = 800.0, status = "APPROVED_RESERVED", reservedAmount = 800.0
                )
            )
            assertEquals(800.0, dao.getBorrowingRequestsOnce(ws).single().reservedAmount, 0.001)

            dao.insertSettlementObligation(
                SettlementObligationEntity(
                    id = 4001L, workspaceId = ws, borrowerUid = "partnerA", borrowerName = "Partner A",
                    creditorType = "PARTNER", creditorUid = "partnerB", creditorName = "Partner B",
                    sourceRequestId = 3001L, principalAmount = 800.0, repaidAmount = 200.0, directSettledAmount = 200.0, status = "OPEN"
                )
            )
            dao.insertSettlementRepayment(
                SettlementRepaymentEntity(
                    id = 5001L, workspaceId = ws, obligationId = 4001L, payerUid = "partnerA", payerName = "Partner A",
                    creditorType = "PARTNER", creditorUid = "partnerB", creditorName = "Partner B",
                    amount = 200.0, mode = "DIRECT_TO_PARTNER", status = "CONFIRMED"
                )
            )
            assertEquals(600.0, dao.getSettlementObligationsOnce(ws).single().remainingAmount, 0.001)
            assertEquals(1, dao.getSettlementRepaymentsOnce(ws).size)
        } finally {
            db.close()
        }
    }
}
