package com.example.data.firebase

import com.example.data.auth.RoleUtils
import com.example.data.entity.PendingOperationEntity

/** Audit labels are not authorization. The retained actor UID and action time remain authoritative. */
internal object JobEditorMetadata {
    fun fields(operation: PendingOperationEntity, queued: Map<String, Any?>, member: Map<String, Any?>): Map<String, Any?> {
        val role = RoleUtils.normalizeRole(member["role"] as? String)
        if (!(member["status"] as? String).equals("active", true) || role !in setOf(
            RoleUtils.ROLE_OWNER, RoleUtils.ROLE_PARTNER, RoleUtils.ROLE_OPERATOR) ||
            (member["uid"] as? String)?.takeIf { it.isNotBlank() }?.let { it != operation.actorUid } == true ||
            (member["workspaceId"] as? String)?.takeIf { it.isNotBlank() }?.let { it != operation.workspaceId } == true)
            throw SecurityException("Job editor membership is no longer active or matches a different identity")
        val originalName = (queued["editedByName"] as? String)?.takeIf {
            queued["editedByUid"] == operation.actorUid && it.isNotBlank()
        }
        return mapOf("editedByUid" to operation.actorUid, "editedByRole" to role,
            "editedByName" to (originalName ?: (member["displayName"] as? String).orEmpty()),
            "updatedAt" to operation.actionTime)
    }
}
