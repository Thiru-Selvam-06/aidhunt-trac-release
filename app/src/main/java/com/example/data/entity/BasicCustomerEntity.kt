package com.example.data.entity

import androidx.room.Entity

/** Customer choices for operational users. This table deliberately has no financial columns. */
@Entity(tableName = "basic_customers", primaryKeys = ["workspaceId", "id"])
data class BasicCustomerEntity(
    val id: Long,
    val workspaceId: String,
    val name: String,
    val phone: String = "",
    val location: String = "",
    val createdByUid: String,
    val createdAt: Long,
    val creationOperationId: String,
    val updatedAt: Long,
    val isSynced: Boolean = false
)
