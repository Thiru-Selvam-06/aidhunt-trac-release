package com.example

import com.example.data.entity.AppNotificationEntity
import com.example.data.entity.ExternalLoanEntity
import com.example.data.entity.ExternalLoanRepaymentEntity
import com.example.data.entity.PendingOperationEntity
import com.example.data.expense.ExpenseDateRange
import com.example.data.expense.LoanAnalytics
import com.example.data.firebase.WorkspaceMember
import com.example.data.sync.NotificationEmitter
import com.example.ui.util.JobAuditPresentation
import com.example.ui.util.NotificationPresenter
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class Stage4NotificationUnitTest {

    private val members = listOf(
        WorkspaceMember(uid = "owner_1", workspaceId = "ws_1", role = "OWNER", status = "ACTIVE", displayName = "Selvam"),
        WorkspaceMember(uid = "partner_a", workspaceId = "ws_1", role = "PARTNER", status = "ACTIVE", displayName = "Kumar"),
        WorkspaceMember(uid = "partner_b", workspaceId = "ws_1", role = "PARTNER", status = "ACTIVE", displayName = "Arun")
    )

    private fun pendingOp(
        opId: String,
        actorUid: String,
        entityType: String,
        mutationType: String = "UPSERT",
        entityId: Long = 101L,
        payload: JSONObject = JSONObject()
    ) = PendingOperationEntity(
        operationId = opId,
        workspaceId = "ws_1",
        sequence = 1L,
        entityType = entityType,
        entityId = entityId,
        mutationType = mutationType,
        payloadJson = payload.toString(),
        actorUid = actorUid,
        actionTime = 1_763_100_000_000L
    )

    @Test
    fun ownerNeverReceivesSelfNotificationsAndPartnerActionsNotifyOwnerWithDeterministicIds() {
        val ownerJobOp = pendingOp(
            opId = "op_owner_job",
            actorUid = "owner_1",
            entityType = "JOB",
            payload = JSONObject().put("customerName", "Ravi").put("totalAmount", 1200.0)
        )
        assertTrue(NotificationEmitter.buildForConfirmedOperation(ownerJobOp, members, "owner_1").isEmpty())

        val partnerJobOp = pendingOp(
            opId = "op_partner_job",
            actorUid = "partner_a",
            entityType = "JOB",
            payload = JSONObject().put("customerName", "Ravi").put("totalAmount", 1200.0)
        )
        val emitted = NotificationEmitter.buildForConfirmedOperation(partnerJobOp, members, "owner_1")
        val replay = NotificationEmitter.buildForConfirmedOperation(partnerJobOp, members, "owner_1")
        assertEquals(1, emitted.size)
        assertEquals("op_partner_job_OWNER_JOB_CREATED", emitted[0].id)
        assertEquals("owner_1", emitted[0].recipientUid)
        assertEquals(AppNotificationEntity.EVENT_JOB_CREATED, emitted[0].eventType)
        assertEquals(emitted, replay)
    }

    @Test
    fun partnerTargetedBorrowingAndSettlementEventsNotifyRelevantCounterpartiesOnly() {
        val borrowReq = pendingOp(
            opId = "op_borr_req",
            actorUid = "partner_a",
            entityType = "BORROWING_REQUEST",
            payload = JSONObject()
                .put("stage3Action", "CREATE")
                .put("borrowerUid", "partner_a")
                .put("lendingPartnerUid", "partner_b")
                .put("amount", 900.0)
        )
        val reqAlerts = NotificationEmitter.buildForConfirmedOperation(borrowReq, members, "owner_1")
        assertEquals(setOf("partner_b", "owner_1"), reqAlerts.map { it.recipientUid }.toSet())
        assertTrue(reqAlerts.first { it.recipientUid == "partner_b" }.targetAudience == AppNotificationEntity.AUDIENCE_PARTNER_TARGETED)

        val confirmRepay = pendingOp(
            opId = "op_set_conf",
            actorUid = "partner_b",
            entityType = "SETTLEMENT_REPAYMENT",
            payload = JSONObject()
                .put("stage3Action", "CONFIRM")
                .put("payerUid", "partner_a")
                .put("creditorUid", "partner_b")
                .put("creditorName", "Arun")
                .put("amount", 900.0)
        )
        val confAlerts = NotificationEmitter.buildForConfirmedOperation(confirmRepay, members, "owner_1")
        assertEquals(setOf("partner_a", "owner_1"), confAlerts.map { it.recipientUid }.toSet())
        assertTrue(confAlerts.none { it.recipientUid == "partner_b" })
    }

    @Test
    fun presenterRendersLocalizedEnglishAndTamilInRecipientLanguageWithIstTimestamp() {
        val notif = AppNotificationEntity(
            id = "n1",
            workspaceId = "ws_1",
            operationId = "op_1",
            recipientUid = "owner_1",
            targetAudience = AppNotificationEntity.AUDIENCE_OWNER_ONLY,
            actorUid = "partner_a",
            actorName = "Kumar",
            eventType = AppNotificationEntity.EVENT_PAYMENT_RECEIVED,
            entityType = "PAYMENT",
            entityId = 10L,
            amount = 2500.0,
            counterpartyName = "Muthu",
            createdAt = 1_763_100_000_000L
        )
        val en = NotificationPresenter.present(notif, isTamil = false)
        val ta = NotificationPresenter.present(notif, isTamil = true)

        assertTrue(en.title.contains("Payment Received"))
        assertTrue(en.body.contains("Kumar"))
        assertTrue(en.body.contains("Muthu"))
        assertTrue(en.timestampLabel.contains("IST"))

        assertTrue(ta.title.contains("கட்டணம்"))
        assertTrue(ta.body.contains("Kumar"))
        assertTrue(ta.body.contains("Muthu"))
        assertTrue(ta.timestampLabel.contains("IST"))
        assertFalse(en.body == ta.body)
        assertTrue(JobAuditPresentation.instant(1_763_100_000_000L, false).contains("IST"))
    }

    @Test
    fun loanAnalyticsSeparatesRepaymentsOutstandingPrincipalAndLoanFundingAcrossPeriods() {
        val now = 1_763_100_000_000L
        val oldLoanTime = now - 400L * 24L * 60L * 60L * 1000L
        val loans = listOf(
            ExternalLoanEntity(
                id = 1L, workspaceId = "ws_1", lenderName = "Co-op Bank",
                principalAmount = 5000.0, interestAmount = 500.0,
                totalRepaidPrincipal = 2000.0, totalRepaidInterest = 300.0,
                destination = "BUSINESS_BANK", status = "ACTIVE", loanDateMillis = now
            ),
            ExternalLoanEntity(
                id = 2L, workspaceId = "ws_1", lenderName = "Dealer Credit",
                principalAmount = 3000.0, interestAmount = 0.0,
                totalRepaidPrincipal = 1000.0, totalRepaidInterest = 0.0,
                destination = "DIRECT_TO_SUPPLIER", status = "ACTIVE", loanDateMillis = oldLoanTime
            )
        )
        val repayments = listOf(
            ExternalLoanRepaymentEntity(
                id = 11L, workspaceId = "ws_1", loanId = 1L,
                principalPaid = 2000.0, interestPaid = 300.0, source = "BUSINESS_BANK", repaidAt = now
            ),
            ExternalLoanRepaymentEntity(
                id = 12L, workspaceId = "ws_1", loanId = 2L,
                principalPaid = 1000.0, interestPaid = 0.0, source = "BUSINESS_CASH", repaidAt = oldLoanTime
            )
        )

        val all = LoanAnalytics.from(loans, repayments, ExpenseDateRange.Period.ALL_TIME, now)
        assertEquals(3300.0, all.totalRepaid, 0.01)
        assertEquals(3000.0, all.totalPrincipalRepaid, 0.01)
        assertEquals(300.0, all.totalInterestRepaid, 0.01)
        assertEquals(5000.0, all.totalPrincipalOutstanding, 0.01)
        assertEquals(200.0, all.totalInterestOutstanding, 0.01)
        assertEquals(8000.0, all.totalLoanFunding, 0.01)
        assertEquals(2, all.repaymentsByLoan.size)
        assertEquals(2, all.outstandingByLoan.size)
        assertEquals(2, all.fundingByDestination.size)

        val today = LoanAnalytics.from(loans, repayments, ExpenseDateRange.Period.TODAY, now)
        assertEquals(2300.0, today.totalRepaid, 0.01)
        assertEquals(3000.0, today.totalPrincipalOutstanding, 0.01)
        assertEquals(5000.0, today.totalLoanFunding, 0.01)
    }
}
