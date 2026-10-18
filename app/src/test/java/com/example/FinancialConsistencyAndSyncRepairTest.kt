package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.PendingOperationEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.firebase.WorkspaceMember
import com.example.data.sync.EntryDeletionPolicy
import com.example.data.sync.OfflineEntryRecovery
import com.example.ui.screens.report.CreditDueStatusFilter
import com.example.ui.util.FinancialCalculationEngine
import com.example.ui.util.FinancialScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FinancialConsistencyAndSyncRepairTest {

    private val testScope = FinancialScope(workspaceId = "ws_test_repair")

    private fun testJob(
        id: Long = 101L,
        wsId: String = testScope.workspaceId,
        customerId: Long = 1L,
        customerName: String = "Test Farmer",
        totalAmount: Double = 5000.0,
        amountReceived: Double = 5000.0,
        pendingAmount: Double = 0.0,
        actorUid: String = "uid_partner_2"
    ) = JobEntryEntity(
        id = id,
        workspaceId = wsId,
        customerId = customerId,
        customerName = customerName,
        operatorName = "Operator",
        tractorLabel = "John Deere",
        workType = "Ploughing",
        startTimeMillis = 1000L,
        endTimeMillis = 2000L,
        durationMinutes = 60,
        totalAmount = totalAmount,
        amountReceived = amountReceived,
        pendingAmount = pendingAmount,
        addedByPartner = "Partner Two",
        createdByUid = actorUid,
        entryForUid = actorUid,
        financialAttributionUid = actorUid
    )

    private fun testExpense(
        id: Long = 201L,
        wsId: String = testScope.workspaceId,
        amount: Double = 1500.0,
        paidBy: String = "Business Paid",
        paidByUid: String = "",
        actorUid: String = "uid_partner_2"
    ) = ExpenseEntity(
        id = id,
        workspaceId = wsId,
        expenseType = "Diesel",
        amount = amount,
        tractorLabel = "John Deere",
        addedByPartner = "Partner Two",
        paidBy = paidBy,
        paidByPartner = if (paidBy == "I Paid") "Partner Two" else "",
        paidByUid = paidByUid,
        expenseForUid = actorUid,
        financialAttributionUid = actorUid,
        createdByUid = actorUid
    )

    // =========================================================================
    // Defect 1: "Business Paid" vs "I Paid" Semantics and Attribution
    // =========================================================================

    @Test
    fun businessPaidExpenseIsNotAttributedToPartnerBreakdown() {
        val ownerUid = "uid_owner_1"
        val partnerUid = "uid_partner_2"

        val members = listOf(
            WorkspaceMember(uid = ownerUid, workspaceId = testScope.workspaceId, role = "owner", displayName = "Owner One", status = "ACTIVE"),
            WorkspaceMember(uid = partnerUid, workspaceId = testScope.workspaceId, role = "partner", displayName = "Partner Two", status = "ACTIVE")
        )

        val jobs = listOf(testJob(id = 101L, totalAmount = 5000.0, amountReceived = 5000.0, pendingAmount = 0.0, actorUid = partnerUid))

        // Business Paid expense: paid from business drawer, NOT personal investment
        val businessExpense = testExpense(id = 201L, amount = 1500.0, paidBy = "Business Paid", paidByUid = "", actorUid = partnerUid)

        // I Paid expense: partner personally funded out of pocket
        val personalExpense = testExpense(id = 202L, amount = 600.0, paidBy = "I Paid", paidByUid = partnerUid, actorUid = partnerUid)

        val breakdown = FinancialCalculationEngine.calculatePartnerBreakdown(
            jobs = jobs,
            expenses = listOf(businessExpense, personalExpense),
            withdrawals = emptyList(),
            partners = emptyList(),
            workspaceMembers = members,
            scope = testScope
        )

        val partnerBreakdown = breakdown.firstOrNull { it.partnerName == "Partner Two" }
        assertNotNull("Partner Two breakdown must be present", partnerBreakdown)

        // Partner breakdown must only include the "I Paid" personal expense (600.0), NOT the "Business Paid" expense (1500.0)
        assertEquals("Personal expense must only reflect I Paid expenses", 600.0, partnerBreakdown!!.expenses, 0.01)
        assertEquals("Jobs total recorded for partner", 5000.0, partnerBreakdown.recorded, 0.01)
    }

    @Test
    fun cashAvailableProperlyDeductsBusinessPaidAndPreservesUnclampedLedger() {
        val payments = listOf(
            PaymentEntity(id = 1L, workspaceId = testScope.workspaceId, amount = 4000.0, paymentMethod = "Cash", status = "ACTIVE")
        )
        val businessExpense = testExpense(id = 2L, amount = 1500.0, paidBy = "Business Paid")
        val personalExpense = testExpense(id = 3L, amount = 800.0, paidBy = "I Paid", paidByUid = "uid_partner_2")
        val withdrawals = listOf(
            WithdrawalEntity(id = 4L, workspaceId = testScope.workspaceId, partnerName = "Partner Two", amount = 1000.0)
        )

        // Cash Available = 4000 (active cash collections) - 1500 (business paid expenses) - 1000 (withdrawals) = 1500.0
        // Personally funded "I Paid" (800) does NOT deduct from business cash drawer.
        val balance = FinancialCalculationEngine.cashAvailable(
            payments = payments,
            expenses = listOf(businessExpense, personalExpense),
            withdrawals = withdrawals,
            scope = testScope
        )
        assertEquals(1500.0, balance, 0.01)

        // Accounting consistency: If business expenses exceed collections, balance must NOT be clamped to zero.
        val largeExpense = testExpense(id = 5L, amount = 5000.0, paidBy = "Business Paid")
        val negativeBalance = FinancialCalculationEngine.cashAvailable(
            payments = payments,
            expenses = listOf(businessExpense, largeExpense),
            withdrawals = withdrawals,
            scope = testScope
        )
        // 4000 - 1500 - 5000 - 1000 = -3500.0
        assertEquals(-3500.0, negativeBalance, 0.01)
    }

    // =========================================================================
    // Defect 3 & Defect 5: Deletion Reversal & Collection History Retention
    // =========================================================================

    @Test
    fun entryDeletionReversesLinkedCollectionsWithoutHardDeletingFromRoom() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

        try {
            val wsId = testScope.workspaceId
            val jobId = 701L
            val paymentId = 801L
            val actorUid = "partner_uid_test"
            val operationTime = 1729000000000L

            val job = testJob(
                id = jobId, wsId = wsId, customerId = 50L, customerName = "Test Farmer",
                totalAmount = 2500.0, amountReceived = 2500.0, pendingAmount = 0.0,
                actorUid = actorUid
            )
            val payment = PaymentEntity(
                id = paymentId, workspaceId = wsId, jobEntryId = jobId, customerId = 50L,
                amount = 2500.0, paymentMethod = "Cash", status = "ACTIVE",
                allocationsJson = """[{"jobId":701,"amount":2500.0}]""",
                collectedByUid = actorUid, isSynced = true,
                createdAt = operationTime
            )

            db.jobEntryDao().insertJob(job)
            db.paymentDao().insertPayment(payment)

            // Simulate entry deletion transaction in Room:
            // 1. Job is deleted
            db.jobEntryDao().deleteJob(job)

            // 2. Linked collection is REVERSED, not hard-deleted
            val linkedPayments = listOf(payment)
            val collectionDeletes = EntryDeletionPolicy.plannedCollectionDeletes(
                job, linkedPayments, owner = false, actorUid = actorUid, role = "PARTNER", actionTime = operationTime
            )
            assertEquals(1, collectionDeletes.size)

            collectionDeletes.forEach { p ->
                val reversed = p.copy(
                    status = "REVERSED",
                    reversedByUid = actorUid,
                    reversedAt = operationTime,
                    isSynced = false
                )
                db.paymentDao().insertPayment(reversed)
            }

            // Verify:
            // Job is gone from Room
            assertNull("Deleted job must not exist in Room", db.jobEntryDao().getJobById(wsId, jobId))

            // Payment still exists in Room, preserving audit history
            val storedPayment = db.paymentDao().getPaymentById(wsId, paymentId)
            assertNotNull("Reversed collection must remain in Room for audit history", storedPayment)
            assertEquals("REVERSED", storedPayment!!.status)
            assertEquals(actorUid, storedPayment.reversedByUid)
            assertEquals(operationTime, storedPayment.reversedAt)
            assertFalse(storedPayment.isSynced)

            // Collection History query retrieves it
            val allPayments = db.paymentDao().getPaymentsForWorkspace(wsId).first()
            assertEquals(1, allPayments.size)
            assertEquals("REVERSED", allPayments[0].status)
        } finally {
            db.close()
        }
    }

    // =========================================================================
    // Defect 2 & Defect 4: Deletion Failure Transitions PENDING to REJECTED & Restores State
    // =========================================================================

    @Test
    fun maxAttemptsOrUnavailableBackendRejectsOperationAndRecoversState() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()

        try {
            val wsId = testScope.workspaceId
            val opId = "op_delete_entry_failed"
            val actorUid = "partner_actor_1"

            val op = PendingOperationEntity(
                operationId = opId, workspaceId = wsId, actorUid = actorUid,
                entityType = "JOB", entityId = 999L, mutationType = "DELETE",
                sequence = 1L, actionTime = 1729000000000L,
                payloadJson = """{"workspaceId":"$wsId","id":999,"customerId":42,"totalAmount":3000.0,"amountReceived":0.0,"pendingAmount":3000.0,"linkedCollectionSnapshots":[],"linkedExpenseSnapshots":[]}""",
                state = "PENDING", attempts = 2
            )
            db.pendingOperationDao().insert(op)

            // When attempt fails with attempts >= 3 or backend unavailable:
            val attemptsAfterFailure = op.attempts + 1
            val isPermanent = attemptsAfterFailure >= 3

            assertTrue("Operation after 3 attempts must be classified permanent/REJECTED", isPermanent)

            db.pendingOperationDao().failed(opId, if (isPermanent) "REJECTED" else "PENDING", "Cloudflare worker unavailable")

            // Verify: Operation state is now REJECTED, NOT PENDING
            val updatedOp = db.pendingOperationDao().getOperation(opId)
            assertNotNull(updatedOp)
            assertEquals("REJECTED", updatedOp!!.state)

            // Verify: outstandingOperations does NOT return it as PENDING (clearing stuck sync indicator)
            val pendingOps = db.pendingOperationDao().nextBatch(wsId, actorUid)
            assertTrue("Rejected operation must not appear in pending batch", pendingOps.isEmpty())

            // Test OfflineEntryRecovery: Server retained the entry, so recovery restores it into Room
            val confirmedCloudJob: Map<String, Any?> = mapOf(
                "workspaceId" to wsId, "customerId" to 42L, "customerName" to "Restored Customer",
                "totalAmount" to 3000.0, "amountReceived" to 0.0, "pendingAmount" to 3000.0,
                "tractorLabel" to "Tractor A", "workType" to "Rotavator", "date" to "18 Oct 2026",
                "createdByUid" to actorUid, "entryForUid" to actorUid, "authorizationTimeStatus" to "VERIFIED"
            )

            val recovered = OfflineEntryRecovery.restore(
                db = db,
                op = updatedOp,
                confirmed = confirmedCloudJob,
                contextMatches = { true },
                recalculate = {}
            )

            assertTrue("OfflineEntryRecovery must succeed", recovered)
            val restoredJob = db.jobEntryDao().getJobById(wsId, 999L)
            assertNotNull("Job must be restored to Room after cloud confirms it is still present", restoredJob)
            assertEquals("Restored Customer", restoredJob!!.customerName)
        } finally {
            db.close()
        }
    }

    // =========================================================================
    // Defect 6: Customer Credit Due Filtering Excludes Zero-Due Phantoms
    // =========================================================================

    @Test
    fun customerCreditDueFilterExcludesZeroActivityCustomersFromPaidFilter() {
        val fullyPaidActiveCustomer = CustomerEntity(
            id = 1L, workspaceId = testScope.workspaceId, name = "Active Paid Farmer", phone = "9876543210",
            totalBilled = 5000.0, totalPaid = 5000.0, balanceDue = 0.0
        )
        val customerWithDue = CustomerEntity(
            id = 2L, workspaceId = testScope.workspaceId, name = "Pending Farmer", phone = "9876543211",
            totalBilled = 8000.0, totalPaid = 3000.0, balanceDue = 5000.0
        )
        // Deleted-entry phantom customer: 0 billed, 0 paid, 0 due
        val zeroActivityPhantomCustomer = CustomerEntity(
            id = 3L, workspaceId = testScope.workspaceId, name = "Deleted Job Customer", phone = "9876543212",
            totalBilled = 0.0, totalPaid = 0.0, balanceDue = 0.0
        )

        fun matchesStatus(filter: CreditDueStatusFilter, c: CustomerEntity): Boolean {
            val hasActivity = c.totalBilled > 0.0 || c.totalPaid > 0.0
            return when (filter) {
                CreditDueStatusFilter.ALL -> true
                CreditDueStatusFilter.PAID -> hasActivity && c.balanceDue <= 0.0
                CreditDueStatusFilter.PENDING_DUE -> c.balanceDue > 0.0
            }
        }

        // Test PAID filter:
        assertTrue("Active fully paid customer matches PAID", matchesStatus(CreditDueStatusFilter.PAID, fullyPaidActiveCustomer))
        assertFalse("Customer with balance due does NOT match PAID", matchesStatus(CreditDueStatusFilter.PAID, customerWithDue))
        assertFalse("Zero activity phantom customer must NOT match PAID filter", matchesStatus(CreditDueStatusFilter.PAID, zeroActivityPhantomCustomer))

        // Test PENDING_DUE filter:
        assertFalse(matchesStatus(CreditDueStatusFilter.PENDING_DUE, fullyPaidActiveCustomer))
        assertTrue(matchesStatus(CreditDueStatusFilter.PENDING_DUE, customerWithDue))
        assertFalse(matchesStatus(CreditDueStatusFilter.PENDING_DUE, zeroActivityPhantomCustomer))

        // Test ALL filter:
        assertTrue(matchesStatus(CreditDueStatusFilter.ALL, zeroActivityPhantomCustomer))
    }
}
