package com.example

import com.example.data.auth.AuthorizationManager
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PendingOperationEntity
import com.example.data.firebase.WorkspaceMember
import com.example.data.sync.OperationPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class Phase53DeletionPolicyTest {
    private val now = 1_800_000_000_000L
    private fun source(creator: String, historical: Boolean) = JobEntryEntity(
        id = 1, workspaceId = "ws_test", customerName = "Synthetic", operatorName = "Partner",
        tractorLabel = "T", workType = "Work", startTimeMillis = 1, endTimeMillis = 2,
        durationMinutes = 1, totalAmount = 100.0, amountReceived = 0.0, pendingAmount = 100.0,
        addedByPartner = creator, createdAt = now - 365 * 86_400_000L, createdByUid = creator,
        entryForUid = "partner", financialAttributionUid = "partner", isHistorical = historical)
    private fun command(job: JobEntryEntity, actor: String) = PendingOperationEntity(
        "original-offline-delete", "ws_test", actor, "JOB", job.id, "DELETE", 1, now,
        JSONObject().put("operationProtocolVersion", 2).put("workspaceId", "ws_test")
            .put("createdByUid", job.createdByUid).put("entryForUid", "partner")
            .put("createdAt", job.createdAt).put("isHistorical", job.isHistorical).toString())
    private fun members(actor: String = "partner", role: String = "PARTNER", status: String = "ACTIVE", ws: String = "ws_test") =
        listOf(WorkspaceMember(uid = actor, workspaceId = ws, role = role, status = status),
            WorkspaceMember(uid = "partner", workspaceId = "ws_test", role = "PARTNER", status = "ACTIVE"))

    @Test fun stagedDeletionPermitsBothKindsAcrossCreatorsAndAgesWithoutChangingOriginalOperation() {
        for (creator in listOf("owner", "other-partner")) for (old in listOf(false, true)) {
            val job = source(creator, old)
            assertTrue(AuthorizationManager.canDeleteEntry(job, true, "Owner", "owner", now, "OWNER", true))
            assertTrue(AuthorizationManager.canDeleteEntry(job, false, "Partner", "partner", now, "PARTNER", true))
            assertFalse(AuthorizationManager.canDeleteEntry(job, false, "Operator", "operator", now, "OPERATOR", true))
            assertFalse(AuthorizationManager.canDeleteEntry(job, false, "Partner", "partner", now, "PARTNER", false))
            val original = command(job, "partner")
            OperationPolicy.requireAuthorized(original, "PARTNER", members() + WorkspaceMember(uid = creator, workspaceId = "ws_test", role = "PARTNER", status = "ACTIVE"), sharedPartnerDeletion = true)
            assertEquals(now, original.actionTime)
            assertEquals("original-offline-delete", original.operationId)
        }
    }

    @Test fun shippedGateAndMembershipFailClosed() {
        assertFalse(BuildConfig.SHARED_PARTNER_JOB_DELETION)
        val op = command(source("other", true), "actor")
        for (roster in listOf(members("actor", status = "REMOVED"), members("actor", ws = "foreign"), members("actor", role = "OPERATOR"))) {
            assertTrue(runCatching { OperationPolicy.requireAuthorized(op, roster.first().role, roster, sharedPartnerDeletion = true) }.isFailure)
        }
        assertTrue(runCatching { OperationPolicy.requireAuthorized(op, "PARTNER", members("actor"), sharedPartnerDeletion = false) }.isFailure)
        assertTrue(AuthorizationManager.canCreateOldEntry(false, "PARTNER"))
        assertFalse(AuthorizationManager.canCreateOldEntry(false, "OPERATOR"))
    }
}
