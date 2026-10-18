package com.example.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A durable action snapshot. actionTime is local evidence under the v1.6 offline model. */
@Entity(tableName = "pending_operations", indices = [Index(value = ["workspaceId", "actorUid", "sequence"])])
data class PendingOperationEntity(
    @PrimaryKey val operationId: String,
    val workspaceId: String,
    val actorUid: String,
    val entityType: String,
    val entityId: Long,
    val mutationType: String,
    val sequence: Long,
    val actionTime: Long,
    val payloadJson: String,
    val state: String = "PENDING",
    val attempts: Int = 0,
    val failureReason: String = ""
)
