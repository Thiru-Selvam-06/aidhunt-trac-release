package com.example.data.firebase

import com.example.data.entity.ExpenseCategoryEntity
import com.example.data.expense.ExpenseCategories

fun ExpenseCategoryEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id, "workspaceId" to workspaceId, "name" to name, "nameKey" to nameKey,
    "createdAt" to createdAt, "createdByUid" to createdByUid, "createdByRole" to createdByRole
)

fun expenseCategoryFromFirestoreMap(data: Map<String, Any?>, workspaceId: String): ExpenseCategoryEntity {
    require(data["workspaceId"] == workspaceId) { "Category workspace mismatch" }
    val name = ExpenseCategories.validate(data["name"] as? String ?: "")
    val key = data["nameKey"] as? String ?: ""
    val id = (data["id"] as? Number)?.toLong() ?: 0L
    require(key == ExpenseCategories.key(name) && id == ExpenseCategories.id(name)) { "Invalid category identity" }
    return ExpenseCategoryEntity(id, workspaceId, name, key, (data["createdAt"] as Number).toLong(),
        data["createdByUid"] as String, data["createdByRole"] as String, isSynced = true)
}
