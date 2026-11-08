package com.example.data.entity

import androidx.room.Entity

@Entity(tableName = "expense_categories", primaryKeys = ["workspaceId", "id"])
data class ExpenseCategoryEntity(
    val id: Long,
    val workspaceId: String,
    val name: String,
    val nameKey: String,
    val createdAt: Long,
    val createdByUid: String,
    val createdByRole: String,
    val isSynced: Boolean = false
)
