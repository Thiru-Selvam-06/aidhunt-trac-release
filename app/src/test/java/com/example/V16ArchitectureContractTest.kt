package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthorizationManager
import com.example.data.auth.RoleUtils
import com.example.data.database.AppDatabase
import com.example.data.entity.*
import com.example.data.firebase.WorkspaceMember
import com.example.data.firebase.toFirestoreMap
import com.example.data.sync.MemberPurgePlan
import com.example.data.sync.OperationPolicy
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class V16ArchitectureContractTest {
    @Test fun unmappedBeneficiaryIsNotGuessedFromCreatorAndTotalsRemainVisible() {
        val legacy = entry("owner").copy(entryForUid = "", financialAttributionUid = "")
        val rows = com.example.ui.util.FinancialCalculationEngine.calculatePartnerBreakdown(
            listOf(legacy), emptyList(), emptyList(), emptyList(),
            listOf(WorkspaceMember(uid = "owner", workspaceId = "a", role = "OWNER", status = "ACTIVE", displayName = "Owner")),
            com.example.ui.util.FinancialScope(workspaceId = "a"))
        assertEquals(1, rows.size)
        assertEquals("", rows.single().partnerUid)
        assertEquals(50000.0, rows.single().recorded, 0.001)
    }
    @Test fun financialScopeExcludesSameNumberedRecordInAnotherWorkspace() {
        val summary = com.example.ui.util.FinancialCalculationEngine.calculateSummary(
            listOf(entry(), entry().copy(workspaceId = "b")), emptyList(), emptyList(),
            com.example.ui.util.FinancialScope(workspaceId = "a"))
        assertEquals(1, summary.jobCount)
        assertEquals(50000.0, summary.totalRecorded, 0.001)
    }
    private val monday10 = 1_000_000L
    private fun entry(uid: String = "operator", historical: Boolean = false) = JobEntryEntity(
        id = 1, workspaceId = "a", customerName = "Customer", operatorName = "Same Name", tractorLabel = "Tractor", workType = "Work",
        startTimeMillis = monday10 - 30 * 86_400_000L, endTimeMillis = monday10, durationMinutes = 60,
        totalAmount = 50000.0, amountReceived = 0.0, pendingAmount = 50000.0, addedByPartner = "Same Name",
        createdAt = monday10, createdByUid = uid, entryForUid = uid, financialAttributionUid = uid, isHistorical = historical)
    private fun members(role: String = "OPERATOR") = listOf(WorkspaceMember(uid = "operator", workspaceId = "a", role = role, status = "ACTIVE"))
    private fun operation(actionTime: Long, job: JobEntryEntity = entry()) = PendingOperationEntity("op", "a", "operator", "JOB", job.id,
        "UPSERT", 1, actionTime, JSONObject(job.toFirestoreMap()).put("operationProtocolVersion", 2).toString())

    @Test fun ordinaryEntryDoesNotBecomeHistoricalFromWorkDateOrAge() {
        val job = entry()
        assertFalse(job.isHistorical)
        assertTrue(AuthorizationManager.canEditEntry(job, false, currentActorName = "", currentUid = "operator", currentTimeMillis = monday10 + 5 * 3600000, role = "OPERATOR"))
        assertTrue(AuthorizationManager.canDeleteEntry(job, false, currentActorName = "", currentUid = "operator", currentTimeMillis = monday10 + 5 * 3600000, role = "OPERATOR"))
        assertFalse(AuthorizationManager.canEditEntry(job.copy(isHistorical = true), false, currentActorName = "", currentUid = "operator", currentTimeMillis = monday10 + 5 * 3600000, role = "OPERATOR"))
    }
    @Test fun mondayOfflineActionPreservesAuthorizationAtTuesdaySync() {
        OperationPolicy.requireAuthorized(operation(monday10 + 5 * 3600000), "OPERATOR", members())
        assertThrows(SecurityException::class.java) { OperationPolicy.requireAuthorized(operation(monday10 + 26 * 3600000), "OPERATOR", members()) }
        assertThrows(SecurityException::class.java) { OperationPolicy.requireAuthorized(operation(monday10 + 24 * 3600000), "OPERATOR", members()) }
    }
    @Test fun queuedActionCannotCrossWorkspaceOrSurviveMembershipRemoval() {
        val op = operation(monday10 + 1000)
        assertThrows(SecurityException::class.java) { OperationPolicy.requireAuthorized(op, "OPERATOR", members().map { it.copy(workspaceId = "b") }) }
        assertThrows(SecurityException::class.java) { OperationPolicy.requireAuthorized(op, "OPERATOR", members().map { it.copy(status = "REMOVED") }) }
        assertThrows(SecurityException::class.java) { OperationPolicy.requireAuthorized(op.copy(actorUid = "other"), "OPERATOR", members()) }
    }
    @Test fun noNameOrLegacyRoleCanGrantPermission() {
        assertEquals("", RoleUtils.normalizeRole("Co-Owner"))
        assertEquals("", RoleUtils.normalizeRole("unknown"))
        assertFalse(AuthorizationManager.canEditEntry(entry(), false, "Same Name", "other", monday10 + 1, "OPERATOR"))
    }
    @Test fun partnerExpenseIsOwnAndTimelyOperatorHasNoExpensePermission() {
        val expense = ExpenseEntity(id = 1, workspaceId = "a", expenseType = "Fuel", amount = 100.0, tractorLabel = "T", addedByPartner = "Same Name",
            createdAt = monday10, createdByUid = "operator", expenseForUid = "operator", financialAttributionUid = "operator")
        val op = PendingOperationEntity("exp", "a", "operator", "EXPENSE", 1, "UPSERT", 1, monday10 + 1, JSONObject(expense.toFirestoreMap()).put("operationProtocolVersion", 2).toString())
        OperationPolicy.requireAuthorized(op, "PARTNER", members("PARTNER"))
        assertThrows(SecurityException::class.java) { OperationPolicy.requireAuthorized(op, "OPERATOR", members()) }
        assertThrows(SecurityException::class.java) { OperationPolicy.requireAuthorized(op.copy(actionTime = monday10 + 86400000), "PARTNER", members("PARTNER")) }
    }
    @Test fun collectionReversalUsesCreationTimeAndKeepsOtherCollectorUnchanged() {
        val ownerPayment = PaymentEntity(id = 1, workspaceId = "a", amount = 20000.0, collectedByUid = "owner", createdAt = monday10)
        val partnerPayment = PaymentEntity(id = 2, workspaceId = "a", amount = 30000.0, collectedByUid = "partner", createdAt = monday10, collectedAt = monday10 - 86400000)
        assertTrue(AuthorizationManager.canReverseCollection(partnerPayment, false, "partner", "PARTNER", monday10 + 1000))
        assertFalse(AuthorizationManager.canReverseCollection(ownerPayment, false, "partner", "PARTNER", monday10 + 1000))
        val reversed = partnerPayment.copy(status = "REVERSED", reversedByUid = "partner")
        assertEquals("ACTIVE", ownerPayment.status)
        assertEquals(20000.0, listOf(ownerPayment, reversed).filter { it.status == "ACTIVE" }.sumOf { it.amount }, 0.001)
        assertFalse(AuthorizationManager.canReverseCollection(reversed, false, "partner", "PARTNER", monday10 + 1000))
    }
    @Test fun purgeIncludesOwnerCreatedBeneficiaryAndProtectsSharedCustomerAndOtherWorkspace() {
        val own = entry("owner").copy(entryForUid = "partner", financialAttributionUid = "partner", customerId = 7)
        val retained = entry("other").copy(id = 2, customerId = 7)
        val foreign = own.copy(id = 3, workspaceId = "b")
        val customer = CustomerEntity(id = 7, workspaceId = "a", name = "Shared", phone = "", createdByUid = "partner")
        val attached = PaymentEntity(id = 5, workspaceId = "a", jobEntryId = 1, collectedByUid = "owner")
        val plan = MemberPurgePlan.build("a", "partner", listOf(own, retained, foreign), emptyList(), emptyList(), emptyList(), emptyList(), listOf(attached), listOf(customer))
        assertEquals(listOf(1L), plan.jobs.map { it.id })
        assertEquals(listOf(5L), plan.collections.map { it.id })
        assertTrue(plan.customers.isEmpty())
    }
    @Test fun roomMutationAndQueueRollbackTogether() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            try { db.withTransaction { db.jobEntryDao().insertJob(entry()); db.pendingOperationDao().insert(operation(monday10 + 1)); error("Simulated interruption") } }
            catch (_: IllegalStateException) { }
            assertNull(db.jobEntryDao().getJobById("a", 1))
            assertTrue(db.pendingOperationDao().nextBatch("a", "operator").isEmpty())
            db.withTransaction { db.jobEntryDao().insertJob(entry()); db.pendingOperationDao().insert(operation(monday10 + 1)) }
            assertEquals(monday10 + 1, db.pendingOperationDao().nextBatch("a", "operator").single().actionTime)
        } finally { db.close() }
    }
}
