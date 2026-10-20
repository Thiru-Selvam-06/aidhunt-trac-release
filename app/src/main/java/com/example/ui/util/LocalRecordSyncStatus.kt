package com.example.ui.util

import com.example.data.entity.PendingOperationEntity

data class SyncRecordKey(val entityType: String, val id: Long)
data class LocalUnsyncedRecords(val workspaceId: String = "", val records: Set<SyncRecordKey> = emptySet())

/** The badge and table rows describe the same local records; a missing outbox row is not a sync acknowledgement. */
data class LocalRecordSyncStatus(
    val loaded: Boolean = false,
    val records: Set<SyncRecordKey> = emptySet(),
    val pendingRecords: Set<SyncRecordKey> = emptySet(),
    val untrackedRecords: Set<SyncRecordKey> = emptySet(),
    val rejectedActions: Int = 0
) {
    fun count(entityType: String) = records.count { it.entityType == entityType }
    val reviewCount: Int get() = rejectedActions + untrackedRecords.size

    companion object {
        fun from(workspaceId: String, local: LocalUnsyncedRecords,
                 operations: List<PendingOperationEntity>): LocalRecordSyncStatus {
            if (workspaceId.isBlank()) return LocalRecordSyncStatus()
            val rows = operations.filter { it.workspaceId == workspaceId && it.state in setOf("PENDING", "REJECTED") }
            val queued = rows.map { SyncRecordKey(it.entityType, it.entityId) }.toSet()
            val raw = if (local.workspaceId == workspaceId) local.records else emptySet()
            return LocalRecordSyncStatus(
                loaded = local.workspaceId == workspaceId,
                records = raw + queued,
                pendingRecords = rows.filter { it.state == "PENDING" }.map { SyncRecordKey(it.entityType, it.entityId) }.toSet(),
                untrackedRecords = raw - queued,
                rejectedActions = rows.count { it.state == "REJECTED" }
            )
        }
    }
}
