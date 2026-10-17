package com.example.data.firebase

import com.example.data.auth.RoleUtils

/** Indexes suggest candidates; only a current workspace-scoped membership grants authority. */
internal object WorkspaceDiscoveryPolicy {
    fun personalId(uid: String): String {
        require(uid.isNotBlank() && '/' !in uid) { "Invalid authenticated identity" }
        return "ws_${uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16)}"
    }

    fun candidates(uid: String, preferred: String?, defaultId: String?, profile: List<String>, index: List<String>): List<String> =
        (listOfNotNull(preferred, defaultId) + profile + index + personalId(uid))
            .filter { it.isNotBlank() && '/' !in it && it != "." && it != ".." }.distinct()

    /** An explicit invalid selection fails closed; it never redirects a pending command. */
    fun selectedWorkspace(authorized: List<String>, requested: String?, remembered: String?): String? {
        if (requested != null) {
            require(requested in authorized) { "Selected workspace access is no longer active" }
            return requested
        }
        return remembered?.takeIf { it in authorized } ?: authorized.singleOrNull()
    }

    fun role(uid: String, ws: String, data: Map<String, Any?>, ownerUid: String): String? {
        if (!(data["status"] as? String).orEmpty().equals("active", true)) return null
        if (data["uid"] != null && data["uid"] != uid) return null
        if (data["workspaceId"] != null && data["workspaceId"] != ws) return null
        val role = RoleUtils.normalizeRole(data["role"] as? String).takeIf { it.isNotBlank() } ?: return null
        if (RoleUtils.isOwner(role) && ownerUid != uid) return null
        return role
    }
}
