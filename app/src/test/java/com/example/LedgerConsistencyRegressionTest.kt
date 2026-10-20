package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.*
import com.example.data.firebase.WorkspaceMember
import com.example.data.firebase.toFirestoreMap
import com.example.data.sync.ConfirmedEntryDeletion
import com.example.data.sync.OfflineEntryRecovery
import com.example.ui.screens.report.authorizedCollectionSource
import com.example.ui.util.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LedgerConsistencyRegressionTest {
    private fun job(id: Long = 1, customer: Long = 10, paid: Double = 40.0) = JobEntryEntity(
        id = id, workspaceId = "a", customerId = customer, customerName = "Same Name", operatorName = "Owner",
        tractorLabel = "Tractor", workType = "Work", startTimeMillis = 1000, endTimeMillis = 2000,
        durationMinutes = 1, totalAmount = 100.0, amountReceived = paid, pendingAmount = 100 - paid,
        addedByPartner = "Owner", createdAt = 1000, createdByUid = "owner", entryForUid = "owner")
    private fun customer(id: Long = 10) = CustomerEntity(id = id, workspaceId = "a", name = "Same Name", phone = "",
        totalBilled = 999.0, totalPaid = 999.0, balanceDue = 999.0)
    private fun payment(id: Long = 20, jobId: Long = 1, amount: Double = 40.0) = PaymentEntity(
        id = id, workspaceId = "a", customerId = 10, jobEntryId = jobId, amount = amount,
        allocationsJson = """[{"jobId":"$jobId","amount":$amount}]""")
    private fun source(jobs: List<JobEntryEntity>, payments: List<PaymentEntity>) =
        authorizedCollectionSource("a", payments, jobs, emptyList(), true, "owner")

    @Test fun oldEntryReceiptsRemainVisibleAfterTheFirstNewCollectionWithoutDoubleCounting() {
        val jobs = listOf(job(paid = 70.0), job(2, 11, 50.0), job(3, paid = 20.0).copy(workspaceId = "b"))
        val source = source(jobs, listOf(payment(amount = 30.0)))
        assertEquals(listOf(1L, 2L), source.legacyJobs.map { it.id })
        assertEquals(90.0, source.legacyJobs.sumOf { it.amountReceived }, 0.0)
        assertEquals(120.0, source.legacyJobs.sumOf { it.amountReceived } + source.payments.sumOf { it.amount }, 0.0)
        assertTrue(source(listOf(job()), listOf(payment())).legacyJobs.isEmpty())
    }

    @Test fun splitAllocationsAndReversedEventsDoNotHideUnrelatedOlderEntries() {
        val split = payment(amount = 50.0).copy(allocationsJson = """[{"jobId":"1","amount":20},{"jobId":"2","amount":30}]""")
        val rows = listOf(job(paid = 20.0), job(2, paid = 30.0), job(3, paid = 15.0))
        assertEquals(listOf(3L), source(rows, listOf(split)).legacyJobs.map { it.id })
        assertTrue(source(listOf(job(paid = 0.0)), listOf(payment().copy(status = "REVERSED"))).legacyJobs.isEmpty())
        val bad = payment().copy(allocationsJson = "invalid")
        assertTrue(source(listOf(job()), listOf(bad)).legacyJobs.isEmpty())
    }

    @Test fun homeSummaryAndDuesUseTheSameCustomerLedgerAcrossEditAndDelete() {
        val customers = listOf(customer(), customer(11))
        val work = job(paid = 20.0)
        val legacyReceipt = job(2, paid = 30.0).copy(tractorLabel = "Payment", totalAmount = 30.0, pendingAmount = 0.0)
        var rows = listOf(work, legacyReceipt, job(3, 11, 0.0))
        fun check(expected: Double) {
            val dues = FinancialCalculationEngine.customersWithLedger(customers, rows)
            val home = HomeRecordSummary.from(rows)
            val report = FinancialCalculationEngine.calculateSummary(rows, emptyList(), emptyList(), FinancialScope(workspaceId = "a"))
            assertEquals(expected, home.due, 0.0)
            assertEquals(home.due, dues.sumOf { it.balanceDue }, 0.0)
            assertEquals(home.due, report.totalDue, 0.0)
            assertEquals(home.recorded, dues.sumOf { it.totalBilled }, 0.0)
        }
        check(150.0)
        rows = listOf(work.copy(totalAmount = 150.0, pendingAmount = 130.0), legacyReceipt, rows.last())
        check(200.0)
        rows = listOf(rows.last())
        check(100.0)
        assertEquals(listOf(11L), FinancialCalculationEngine.customersWithLedger(customers, rows).map { it.id })
        assertEquals(0.0, FinancialCalculationEngine.calculateCustomerFinancials(customer(), emptyList()).balanceDue, 0.0)
    }

    @Test fun businessPaidIsSplitAmongThreeFinancialMembersAndNeverPersonalInvestment() {
        val members = listOf("owner", "p1", "p2").map { WorkspaceMember(uid = it, workspaceId = "a",
            role = if (it == "owner") "OWNER" else "PARTNER", status = "ACTIVE", displayName = it) } +
            listOf(WorkspaceMember(uid = "operator", workspaceId = "a", role = "OPERATOR", status = "ACTIVE"),
                WorkspaceMember(uid = "foreign", workspaceId = "b", role = "PARTNER", status = "ACTIVE"))
        val expense = ExpenseEntity(id = 1, workspaceId = "a", expenseType = "Diesel", amount = 100.0,
            tractorLabel = "T", addedByPartner = "p1", createdByUid = "p1", paidBy = "Business Paid",
            expenseForUid = "p1", financialAttributionUid = "p1")
        val rows = FinancialCalculationEngine.calculatePartnerBreakdown(emptyList(), listOf(expense), emptyList(),
            emptyList(), members, FinancialScope(workspaceId = "a"))
        assertEquals(setOf("owner", "p1", "p2"), rows.map { it.partnerUid }.toSet())
        assertEquals(100.0, rows.sumOf { it.businessExpenses }, 0.000001)
        assertEquals(listOf(33.33, 33.33, 33.34), rows.map { it.businessExpenses }.sorted())
        assertTrue(rows.all { it.expenses == 0.0 })
        val reordered = FinancialCalculationEngine.calculatePartnerBreakdown(emptyList(), listOf(expense), emptyList(),
            emptyList(), members.reversed(), FinancialScope(workspaceId = "a"))
        assertEquals(rows.associate { it.partnerUid to it.businessExpenses },
            reordered.associate { it.partnerUid to it.businessExpenses })
        val investments = FinancialCalculationEngine.calculatePartnerShareAndInvestment(emptyList(), listOf(expense),
            emptyList(), workspaceMembers = members, scope = FinancialScope(workspaceId = "a"))
        assertTrue(investments.all { it.totalInvestment == 0.0 })
        val personal = expense.copy(paidBy = "I Paid", paidByUid = "p1")
        val personalRows = FinancialCalculationEngine.calculatePartnerBreakdown(emptyList(), listOf(personal), emptyList(),
            emptyList(), members, FinancialScope(workspaceId = "a"))
        assertEquals("p1", personalRows.single().partnerUid)
        assertEquals(100.0, personalRows.single().expenses, 0.0)
        assertEquals(0.0, personalRows.single().businessExpenses, 0.0)
    }

    @Test fun confirmedDeletionRemovesProvisionalRowsAndUnreferencedCustomerButPreservesOtherWorkspace() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            val original = job()
            val receipt = payment()
            val payload = JSONObject(original.toFirestoreMap()).put("linkedCollectionIds", JSONArray().put("20"))
            val op = PendingOperationEntity("delete", "a", "owner", "JOB", 1, "DELETE", 1, 2000,
                payload.toString(), state = "ACKNOWLEDGED")
            db.customerDao().insertCustomer(customer())
            db.customerDao().insertCustomer(customer().copy(workspaceId = "b"))
            db.jobEntryDao().insertJob(original)
            db.jobEntryDao().insertJob(original.copy(workspaceId = "b"))
            db.paymentDao().insertPayment(receipt.copy(status = "REVERSED", isSynced = false))
            db.paymentDao().insertPayment(receipt.copy(workspaceId = "b"))
            db.pendingOperationDao().insert(op)
            repeat(2) { ConfirmedEntryDeletion.apply(db, op, allowCustomerCleanup = true) }
            assertNull(db.jobEntryDao().getJobById("a", 1))
            assertNull(db.paymentDao().getPaymentById("a", 20))
            assertNull(db.customerDao().getCustomerById("a", 10))
            assertNotNull(db.jobEntryDao().getJobById("b", 1))
            assertNotNull(db.paymentDao().getPaymentById("b", 20))
            assertNotNull(db.customerDao().getCustomerById("b", 10))
        } finally { db.close() }
    }

    @Test fun sharedCustomerAndReversedAuditReferencesSurviveConfirmedDeletion() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            val original = job()
            val op = PendingOperationEntity("delete", "a", "owner", "JOB", 1, "DELETE", 1, 2000,
                JSONObject(original.toFirestoreMap()).put("linkedCollectionIds", JSONArray()).toString(), state = "ACKNOWLEDGED")
            db.pendingOperationDao().insert(op)
            db.customerDao().insertCustomer(customer())
            db.jobEntryDao().insertJob(job(2))
            ConfirmedEntryDeletion.apply(db, op, allowCustomerCleanup = true)
            assertNotNull(db.customerDao().getCustomerById("a", 10))
            db.jobEntryDao().deleteJob(job(2))
            db.paymentDao().insertPayment(payment().copy(status = "REVERSED"))
            ConfirmedEntryDeletion.apply(db, op, allowCustomerCleanup = true)
            assertNotNull(db.customerDao().getCustomerById("a", 10))
            assertTrue(FinancialCalculationEngine.customersWithLedger(listOf(customer()), emptyList()).isEmpty())
        } finally { db.close() }
    }

    @Test fun pendingOrRejectedDeleteCannotFinalizeAndServerRecoveryRestoresTheLedger() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            val original = job()
            val receipt = payment()
            val payload = JSONObject(original.toFirestoreMap())
                .put("linkedCollectionIds", JSONArray().put("20"))
                .put("linkedCollectionSnapshots", JSONArray().put(JSONObject(receipt.toFirestoreMap())))
            val op = PendingOperationEntity("pending-delete", "a", "owner", "JOB", 1, "DELETE", 1, 2000, payload.toString())
            db.pendingOperationDao().insert(op)
            db.customerDao().insertCustomer(customer())
            db.jobEntryDao().insertJob(original)
            db.paymentDao().insertPayment(receipt)
            try {
                ConfirmedEntryDeletion.apply(db, op, allowCustomerCleanup = true)
                fail("Unacknowledged delete must not finalize")
            } catch (_: IllegalArgumentException) { }
            assertNotNull(db.jobEntryDao().getJobById("a", 1))
            db.jobEntryDao().deleteJob(original)
            db.paymentDao().insertPayment(receipt.copy(status = "REVERSED", isSynced = false))
            db.pendingOperationDao().failed(op.operationId, "REJECTED", "Synthetic rejection")
            assertTrue(OfflineEntryRecovery.restore(db, op.copy(state = "REJECTED"), original.toFirestoreMap(), { true }) { id ->
                val customer = db.customerDao().getCustomerById("a", id)!!
                db.customerDao().updateCustomer(FinancialCalculationEngine.synchronizeCustomerWithJobs(customer,
                    db.jobEntryDao().getJobsForWorkspaceOnce("a")))
            })
            assertEquals("ACTIVE", db.paymentDao().getPaymentById("a", 20)!!.status)
            assertEquals(60.0, db.customerDao().getCustomerById("a", 10)!!.balanceDue, 0.0)
            assertEquals(60.0, HomeRecordSummary.from(db.jobEntryDao().getJobsForWorkspaceOnce("a")).due, 0.0)
        } finally { db.close() }
    }
}
