package com.example.data.sync

import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PendingOperationEntity
import com.example.data.firebase.jobEntryFromFirestoreMap
import org.json.JSONObject

/** An optimistic local delete can hide the job before its earlier expenses upload.
 * Resolve only the exact server source, after queued predecessors have completed;
 * the operation policy still validates creator, beneficiary and current membership.
 */
internal suspend fun resolveExpenseAuthorizationJob(
    op: PendingOperationEntity,
    localJob: JobEntryEntity?,
    serverRead: suspend (PendingOperationEntity) -> Map<String, Any?>?
): JobEntryEntity? {
    if (op.entityType != "EXPENSE") return null
    val id = JSONObject(op.payloadJson).optLong("relatedJobId")
    if (id <= 0) return null
    if (localJob != null) return localJob
    val data = serverRead(op.copy(entityType = "JOB", entityId = id)) ?: return null
    if (data["isDeleted"] == true || data["isHistorical"] != false) return null
    if (data["workspaceId"] != op.workspaceId)
        throw SecurityException("Linked expense source workspace mismatch")
    val source = jobEntryFromFirestoreMap(data, fallbackId = id, fallbackWorkspaceId = op.workspaceId)
    if (source.id != id || source.workspaceId != op.workspaceId)
        throw SecurityException("Linked expense source identity mismatch")
    return source
}
