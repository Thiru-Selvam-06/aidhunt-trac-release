package com.example.data.auth

import com.example.data.firebase.WorkspaceMember

object ArchivedRecordAuthorization {
    fun isReadOnly(workspaceId: String, creatorUid: String, targetUid: String, members: List<WorkspaceMember>): Boolean =
        members.any { it.workspaceId == workspaceId && it.uid.isNotBlank() &&
            it.uid in setOf(creatorUid, targetUid) && it.status.equals("REMOVED", true) }
}
