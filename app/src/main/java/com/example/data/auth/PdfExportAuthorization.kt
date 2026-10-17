package com.example.data.auth

object PdfExportAuthorization {
    fun permits(actorUid: String, verifiedUid: String, activeWorkspace: String, requestedWorkspace: String,
        role: String, status: String, recordWorkspaces: List<String> = emptyList()): Boolean =
        actorUid.isNotBlank() && actorUid == verifiedUid && activeWorkspace.isNotBlank() &&
        activeWorkspace == requestedWorkspace && status.equals("ACTIVE", true) &&
        RoleUtils.normalizeRole(role) in setOf(RoleUtils.ROLE_OWNER, RoleUtils.ROLE_PARTNER) &&
        recordWorkspaces.all { it == requestedWorkspace }
}
