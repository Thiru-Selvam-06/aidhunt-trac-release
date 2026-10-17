package com.example.data.sync

import com.example.data.auth.AuthorizationManager
import com.example.data.auth.RoleUtils
import com.example.data.entity.PendingOperationEntity
import com.example.data.firebase.WorkspaceMember
import org.json.JSONObject

/** Revalidates the durable action against current canonical membership, using its original local time. */
class ExpiredOperationException(message: String) : SecurityException(message)

object OperationPolicy {
    fun requireAuthorized(op: PendingOperationEntity, role: String, members: List<WorkspaceMember>) {
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
        val creator = data.optString("createdByUid")
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
        if (op.entityType in setOf("JOB", "EXPENSE", "WITHDRAWAL", "TRACTOR", "EXTENSION") &&
            members.none { it.uid == creator && it.workspaceId == op.workspaceId && it.status.equals("active", true) })
            throw SecurityException("Record creator membership removed")
        val permitted = when (op.entityType) {
            "JOB" -> owner || (!data.optBoolean("isHistorical") && (partner || operator) && creator == op.actorUid && target == op.actorUid && timely)
            "EXPENSE" -> owner || (partner && creator == op.actorUid && target == op.actorUid && timely)
            "WITHDRAWAL" -> owner || (partner && creator == op.actorUid && target == op.actorUid && timely)
            "TRACTOR", "EXTENSION" -> owner || (partner && creator == op.actorUid)
            "CUSTOMER" -> owner || (partner && op.mutationType != "DELETE")
            "PAYMENT" -> owner || (partner && data.optString("collectedByUid") == op.actorUid &&
                (data.optString("status") == "ACTIVE" || AuthorizationManager.within24Hours(data.optLong("createdAt"), op.actionTime)))
            else -> false
        }
        if (!permitted) {
            val ownTimed = !owner && ((op.entityType in setOf("JOB", "EXPENSE", "WITHDRAWAL") && creator == op.actorUid && target == op.actorUid && (partner || (operator && op.entityType == "JOB")) && !data.optBoolean("isHistorical")) ||
                (op.entityType == "PAYMENT" && partner && data.optString("collectedByUid") == op.actorUid && data.optString("status") == "REVERSED"))
            if (ownTimed && !timely) throw ExpiredOperationException("${op.entityType} #${op.entityId}, operation ${op.operationId}: action outside original 24-hour window; rejected change retained for review")
            throw SecurityException("Action is no longer authorized")
        }
    }
}
