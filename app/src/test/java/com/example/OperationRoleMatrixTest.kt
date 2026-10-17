package com.example

import com.example.data.entity.PendingOperationEntity
import com.example.data.firebase.WorkspaceMember
import com.example.data.sync.OperationPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/** Explicit action expectations; membership authority is always scoped to the operation workspace. */
@RunWith(ParameterizedRobolectricTestRunner::class)
class OperationRoleMatrixTest(private val role: String, private val entity: String,
    private val mutation: String, private val allowed: Boolean) {
    companion object {
        private val expectations = mapOf(
            "JOB" to setOf("OWNER","PARTNER","OPERATOR"),
            "EXPENSE" to setOf("OWNER","PARTNER"), "WITHDRAWAL" to setOf("OWNER","PARTNER"),
            "TRACTOR" to setOf("OWNER","PARTNER"), "EXTENSION" to setOf("OWNER","PARTNER"),
            "CUSTOMER" to setOf("OWNER","PARTNER"), "PAYMENT" to setOf("OWNER","PARTNER"))
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}/{1}/{2}")
        fun cases(): List<Array<Any>> = listOf("OWNER","PARTNER","OPERATOR").flatMap { role ->
            expectations.flatMap { (entity, permitted) -> listOf("UPSERT","DELETE").map { mutation ->
                arrayOf<Any>(role,entity,mutation, role in permitted &&
                    !(entity=="CUSTOMER" && mutation=="DELETE" && role!="OWNER"))
            } }
        }
    }
    private val created = 100000L
    private fun member(ws: String="a", status: String="ACTIVE", selectedRole: String=role) =
        WorkspaceMember(uid="actor",workspaceId=ws,role=selectedRole,status=status)
    private fun operation(time: Long=created+1, creator: String="actor", historical: Boolean=false): PendingOperationEntity {
        val payload=JSONObject().put("operationProtocolVersion",2).put("workspaceId","a")
            .put("createdAt",created).put("createdByUid",creator).put("entryForUid",creator)
            .put("expenseForUid",creator).put("targetPartnerUid",creator)
            .put("collectedByUid",creator).put("status","ACTIVE").put("isHistorical",historical)
        return PendingOperationEntity("operation-a","a","actor",entity,9007199254740995L,mutation,1,time,payload.toString())
    }
    private fun denied(op: PendingOperationEntity=operation(), members: List<WorkspaceMember> = listOf(member())) {
        try { OperationPolicy.requireAuthorized(op,role,members); fail("Unauthorized operation accepted") }
        catch (_: SecurityException) {}
    }
    @Test fun explicitRoleActionPermission() {
        if(allowed) OperationPolicy.requireAuthorized(operation(),role,listOf(member())) else denied()
        removedMembershipCannotUseTheSameRole()
        foreignWorkspaceMembershipCannotAuthorize()
        roleFromAnotherWorkspaceCannotCarryOver()
        missingMembershipCannotAuthorize()
        timeLimitedOperationsRespectExactCutoffAndUnknownCreation()
        nonOwnerCannotMutateOtherCreatorsOrHistoricalJobs()
    }
    private fun removedMembershipCannotUseTheSameRole() = denied(members=listOf(member(status="REMOVED")))
    private fun foreignWorkspaceMembershipCannotAuthorize() = denied(members=listOf(member(ws="b")))
    private fun roleFromAnotherWorkspaceCannotCarryOver() = denied(members=listOf(member(selectedRole="UNKNOWN"),member(ws="b")))
    private fun missingMembershipCannotAuthorize() = denied(members=emptyList())
    private fun timeLimitedOperationsRespectExactCutoffAndUnknownCreation() {
        if(role!="OWNER" && entity in setOf("JOB","EXPENSE","WITHDRAWAL")) {
            denied(operation(created+86400000))
            denied(operation(created-1))
            if(allowed) OperationPolicy.requireAuthorized(operation(created+86399999),role,listOf(member()))
            val malformed=operation().let { it.copy(payloadJson=JSONObject(it.payloadJson).put("createdAt",0).toString()) }
            denied(malformed)
        }
    }
    private fun nonOwnerCannotMutateOtherCreatorsOrHistoricalJobs() {
        if(role!="OWNER" && entity in setOf("JOB","EXPENSE","WITHDRAWAL","TRACTOR","EXTENSION","PAYMENT")) {
            denied(operation(creator="other"),listOf(member(),WorkspaceMember(uid="other",workspaceId="a",role="PARTNER",status="ACTIVE")))
        }
        if(role!="OWNER" && entity=="JOB") denied(operation(historical=true))
    }
}
