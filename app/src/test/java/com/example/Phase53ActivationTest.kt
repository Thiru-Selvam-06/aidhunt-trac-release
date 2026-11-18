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

/** Run in both debug CI and the actual release variant before packaging. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class Phase53ActivationTest {
    private val now = System.currentTimeMillis()
    private fun job(old: Boolean) = JobEntryEntity(id=1, workspaceId="ws_test", customerName="Synthetic",
        operatorName="Partner", tractorLabel="T", workType="Work", startTimeMillis=1, endTimeMillis=2,
        durationMinutes=1, totalAmount=100.25, amountReceived=0.0, pendingAmount=100.25,
        addedByPartner="owner", createdByUid="owner", createdAt=now-365*86_400_000L,
        entryForUid="partner", financialAttributionUid="partner", isHistorical=old)
    private val roster = listOf(
        WorkspaceMember(uid="owner",workspaceId="ws_test",role="OWNER",status="ACTIVE"),
        WorkspaceMember(uid="partner",workspaceId="ws_test",role="PARTNER",status="ACTIVE"))
    private fun command(source: JobEntryEntity) = PendingOperationEntity("original-delete","ws_test","partner","JOB",1,"DELETE",1,now,
        JSONObject().put("operationProtocolVersion",2).put("workspaceId","ws_test").put("createdByUid",source.createdByUid)
            .put("entryForUid",source.entryForUid).put("createdAt",source.createdAt).put("isHistorical",source.isHistorical).toString())

    @Test fun releaseEnablesReviewedDefaultPolicyAndIsolatedDebugPreservesItsGate() {
        assertEquals(!BuildConfig.DEBUG, BuildConfig.SHARED_PARTNER_JOB_DELETION)
        for(old in listOf(false,true)) {
            val source=job(old)
            assertEquals(BuildConfig.SHARED_PARTNER_JOB_DELETION,
                AuthorizationManager.canDeleteEntry(source,false,"Partner","partner",now,"PARTNER"))
            val op=command(source)
            val result=runCatching { OperationPolicy.requireAuthorized(op,"PARTNER",roster) }
            assertEquals(BuildConfig.SHARED_PARTNER_JOB_DELETION,result.isSuccess)
            assertEquals("original-delete",op.operationId)
            assertEquals(now,op.actionTime)
        }
    }

    @Test fun activationDoesNotAuthorizeOperatorsRemovedMembersOrOtherWorkspaces() {
        val source=job(true)
        assertFalse(AuthorizationManager.canDeleteEntry(source,false,"Operator","partner",now,"OPERATOR"))
        for(changed in listOf(
            roster.map { if(it.uid=="partner") it.copy(status="REMOVED") else it },
            roster.map { it.copy(workspaceId="foreign") },
            roster.map { if(it.uid=="partner") it.copy(role="OPERATOR") else it })) {
            val role=changed.first { it.uid=="partner" }.role
            assertTrue(runCatching { OperationPolicy.requireAuthorized(command(source),role,changed) }.isFailure)
        }
    }
}
