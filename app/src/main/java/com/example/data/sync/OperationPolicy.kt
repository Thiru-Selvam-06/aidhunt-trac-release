package com.example.data.sync

import com.example.data.auth.AuthorizationManager
import com.example.data.auth.RoleUtils
import com.example.data.entity.PendingOperationEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.firebase.WorkspaceMember
import org.json.JSONObject

/** Revalidates the durable action against current canonical membership, using its original local time. */
class ExpiredOperationException(message: String) : SecurityException(message)

object OperationPolicy {
    fun requireAuthorized(op: PendingOperationEntity, role: String, members: List<WorkspaceMember>, linkedJob: JobEntryEntity? = null) {
        val data = JSONObject(op.payloadJson)
        if (data.optInt("operationProtocolVersion") != 2 || op.operationId.isBlank() || op.actionTime <= 0 || op.actorUid.isBlank()) throw com.example.data.firebase.WorkspaceUpgradeRequiredException()
        val member = members.firstOrNull { it.uid == op.actorUid && it.workspaceId == op.workspaceId && it.status.equals("active", true) }
            ?: throw SecurityException("Membership removed")
        val normalized = RoleUtils.normalizeRole(member.role)
        if (normalized.isBlank() || normalized != RoleUtils.normalizeRole(role)) throw SecurityException("Workspace role changed")
        if (data.optString("workspaceId") != op.workspaceId) throw SecurityException("Operation workspace mismatch")
        val owner = normalized == RoleUtils.ROLE_OWNER
        val partner = normalized == RoleUtils.ROLE_PARTNER
        val operator = normalized == RoleUtils.ROLE_OPERATOR
        val creator = if (op.entityType == "BORROWING_REQUEST") {
            data.optString("requestedByUid").ifBlank { data.optString("createdByUid") }
        } else {
            data.optString("createdByUid")
        }
        val target = when (op.entityType) {
            "JOB" -> data.optString("entryForUid")
            "EXPENSE" -> data.optString("expenseForUid")
            "WITHDRAWAL" -> data.optString("targetPartnerUid")
            else -> ""
        }
        if (target.isNotBlank() && members.none { it.uid == target && it.workspaceId == op.workspaceId && it.status.equals("active", true) }) {
            throw SecurityException("Target membership removed")
        }
        val timely = AuthorizationManager.within24Hours(data.optLong("createdAt"), op.actionTime)
        val stage3Action = data.optString("stage3Action")
        val requiresCreatorMembership = op.entityType in setOf("JOB", "EXPENSE", "WITHDRAWAL", "TRACTOR", "EXTENSION", "EXTERNAL_LOAN", "EXTERNAL_LOAN_REPAYMENT") ||
            (op.entityType in setOf("BORROWING_REQUEST", "SETTLEMENT_REPAYMENT") && stage3Action in setOf("", "CREATE", "EXECUTE_BUSINESS"))
        if (requiresCreatorMembership &&
            members.none { it.uid == creator && it.workspaceId == op.workspaceId && it.status.equals("active", true) })
            throw SecurityException("Record creator membership removed")
        val matchingOwnJob = op.entityType == "EXPENSE" && linkedJob != null &&
            data.optLong("relatedJobId") > 0 && linkedJob.id == data.optLong("relatedJobId") &&
            linkedJob.workspaceId == op.workspaceId && linkedJob.createdByUid == op.actorUid &&
            linkedJob.entryForUid == target && !linkedJob.isHistorical
        val permitted = when (op.entityType) {
            "JOB" -> owner || (partner && op.mutationType == "UPSERT" && data.optString("expectedBaseJson").let { it.isNotBlank() && it != "null" }) || (!data.optBoolean("isHistorical") && (partner || operator) && creator == op.actorUid && (partner || target == op.actorUid) && timely)
            "EXPENSE" -> owner || (partner && creator == op.actorUid && (target == op.actorUid || matchingOwnJob) && timely)
            "WITHDRAWAL" -> owner || (partner && creator == op.actorUid && target == op.actorUid && timely)
            "TRACTOR", "EXTENSION" -> owner || (partner && creator == op.actorUid)
            "EXPENSE_CATEGORY" -> (owner || partner) && creator == op.actorUid && op.mutationType == "UPSERT"
            "CUSTOMER" -> owner || (partner && op.mutationType != "DELETE") ||
                (operator && op.mutationType == "UPSERT" && data.optBoolean("basicProfileCreate") &&
                 creator == op.actorUid && data.optLong("id") == op.entityId &&
                 data.optLong("createdAt") == op.actionTime && data.optLong("updatedAt") == op.actionTime &&
                 !data.has(com.example.data.sync.CustomerProfileEdit.PAYLOAD_KEY) &&
                 listOf("totalBilled", "totalPaid", "balanceDue").all { field ->
                    data.has(field) && data.optDouble(field, Double.NaN) == 0.0
                 })
            "PAYMENT" -> owner || (partner && data.optString("collectedByUid") == op.actorUid &&
                (data.optString("status") == "ACTIVE" || AuthorizationManager.within24Hours(data.optLong("createdAt"), op.actionTime)))
            "EXTERNAL_LOAN", "EXTERNAL_LOAN_REPAYMENT" -> (owner || partner) && creator == op.actorUid && op.mutationType == "UPSERT"
            "BORROWING_REQUEST" -> (owner || partner) && op.mutationType == "UPSERT" && when (stage3Action) {
                "EXECUTE_BUSINESS", "CREATE" -> {
                    val borrowerUid = data.optString("borrowerUid")
                    val lendingUid = data.optString("lendingPartnerUid")
                    (borrowerUid == op.actorUid || owner) &&
                        members.any { it.uid == borrowerUid && it.workspaceId == op.workspaceId && it.status.equals("active", true) && RoleUtils.normalizeRole(it.role) in setOf(RoleUtils.ROLE_OWNER, RoleUtils.ROLE_PARTNER) } &&
                        (data.optString("sourceType") != "NAMED_PARTNER" ||
                            (lendingUid.isNotBlank() && lendingUid != borrowerUid &&
                                members.any { it.uid == lendingUid && it.workspaceId == op.workspaceId && it.status.equals("active", true) && RoleUtils.normalizeRole(it.role) in setOf(RoleUtils.ROLE_OWNER, RoleUtils.ROLE_PARTNER) }))
                }
                "APPROVE_RESERVE", "REJECT" -> data.optString("lendingPartnerUid") == op.actorUid
                "EXECUTE" -> data.optString("borrowerUid") == op.actorUid || owner
                "CANCEL" -> data.optString("borrowerUid") == op.actorUid || data.optString("requestedByUid") == op.actorUid || owner
                else -> false
            }
            "SETTLEMENT_REPAYMENT" -> (owner || partner) && op.mutationType == "UPSERT" && when (stage3Action) {
                "CREATE", "" -> creator == op.actorUid
                "CONFIRM", "REJECT" -> data.optString("mode") != "DIRECT_TO_PARTNER" || data.optString("creditorUid") == op.actorUid
                else -> false
            }
            else -> false
        }
        if (!permitted) {
            val ownTimed = !owner && ((op.entityType in setOf("JOB", "EXPENSE", "WITHDRAWAL") && creator == op.actorUid && (target == op.actorUid || (partner && op.entityType in setOf("JOB", "EXPENSE"))) && (partner || (operator && op.entityType == "JOB")) && !data.optBoolean("isHistorical")) ||
                (op.entityType == "PAYMENT" && partner && data.optString("collectedByUid") == op.actorUid && data.optString("status") == "REVERSED"))
            if (ownTimed && !timely) throw ExpiredOperationException("${op.entityType} #${op.entityId}, operation ${op.operationId}: action outside original 24-hour window; rejected change retained for review")
            throw SecurityException("Action is no longer authorized")
        }
    }
}
