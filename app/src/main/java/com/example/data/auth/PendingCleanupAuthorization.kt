package com.example.data.auth

import com.example.data.firebase.WorkspaceMember

internal fun canCleanupPendingInvitation(
    pending: WorkspaceMember,
    workspaceId: String,
    actorUid: String,
    actorRole: String
): Boolean {
    if (workspaceId.isBlank() || actorUid.isBlank() || pending.pendingWorkspaceId != workspaceId ||
        pending.uid.isNotBlank() || !pending.status.equals("waiting_for_registration", true)) return false
    return when (RoleUtils.normalizeRole(actorRole)) {
        RoleUtils.ROLE_OWNER -> true
        RoleUtils.ROLE_PARTNER -> pending.addedByUid == actorUid &&
            pending.role.equals("Operator", true)
        else -> false
    }
}
