package com.example.data.entity

import androidx.room.Entity

@Entity(tableName = "deleted_members", primaryKeys = ["workspaceId", "uid"])
data class DeletedMemberEntity(
    val workspaceId: String,
    val uid: String,
    val displayName: String,
    val role: String,
    val removedAt: Long,
    val removedByUid: String,
    val purgeState: String = "ARCHIVED",
    val backupPath: String = "",
    val backupSha256: String = ""
)
