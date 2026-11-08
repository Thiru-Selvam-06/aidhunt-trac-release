package com.example.data.firebase

import com.example.data.entity.BasicCustomerEntity
import com.example.data.entity.CustomerEntity

/** Explicit allowlist: never serialize customer balances or payment history to the directory. */
fun BasicCustomerEntity.toBasicFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id, "workspaceId" to workspaceId, "name" to name, "phone" to phone,
    "location" to location, "createdByUid" to createdByUid, "createdAt" to createdAt,
    "creationOperationId" to creationOperationId, "updatedAt" to updatedAt
)

fun basicCustomerFromFirestoreMap(map: Map<String, Any?>, workspaceId: String, id: Long): BasicCustomerEntity {
    require(id > 0 && map["id"] == id && workspaceId.isNotBlank() && map["workspaceId"] == workspaceId) {
        "Basic customer identity mismatch"
    }
    // Cloud data must be a projection, not a financial customer document misrouted to Operators.
    require(map.keys.all { it in BASIC_CUSTOMER_KEYS }) { "Unexpected basic customer fields" }
    fun text(key: String) = requireNotNull(map[key] as? String) { "Missing basic customer $key" }
    val name = text("name")
    val creator = text("createdByUid")
    val operation = text("creationOperationId")
    val createdAt = requireNotNull(map["createdAt"] as? Long) { "Missing basic creation time" }
    val updatedAt = requireNotNull(map["updatedAt"] as? Long) { "Missing basic update time" }
    require(createdAt >= 0 && updatedAt >= 0 && operation.matches(Regex("[A-Za-z0-9-]{1,128}"))) {
        "Invalid basic customer metadata"
    }
    return BasicCustomerEntity(id, workspaceId, name, text("phone"), text("location"), creator,
        createdAt, operation, updatedAt, isSynced = true)
}

private val BASIC_CUSTOMER_KEYS = setOf("id", "workspaceId", "name", "phone", "location", "createdByUid",
    "createdAt", "creationOperationId", "updatedAt", "protocolVersion", "operationType", "dependencies",
    "operationId", "actorUid", "actionTime", "isDeleted")

/** Used only as an operational choice. Financial screens remain separately permission-guarded. */
fun BasicCustomerEntity.asCustomerChoice() = CustomerEntity(id = id, workspaceId = workspaceId,
    name = name, phone = phone, location = location, createdByUid = createdByUid,
    createdAt = createdAt, updatedAt = updatedAt, isSynced = isSynced)

internal fun basicCustomerProjection(source: Map<String, Any?>, prior: Map<String, Any?>?,
    op: com.example.data.entity.PendingOperationEntity): Map<String, Any?> {
    require(source["workspaceId"] == op.workspaceId && (source["id"] as? Number)?.toLong() == op.entityId) {
        "Customer projection workspace or identity mismatch"
    }
    val basic = BasicCustomerEntity(op.entityId, op.workspaceId,
        source["name"] as? String ?: "",
        source["phone"] as? String ?: "", source["location"] as? String ?: "",
        source["createdByUid"] as? String ?: "", (source["createdAt"] as? Long)?.takeIf { it >= 0 } ?: 0,
        prior?.get("creationOperationId") as? String ?: op.operationId,
        (source["updatedAt"] as? Long)?.takeIf { it >= 0 } ?: 0)
    return basic.toBasicFirestoreMap() + mapOf("protocolVersion" to 2, "operationType" to "UPSERT",
        "dependencies" to emptyList<Any>(), "operationId" to op.operationId, "actorUid" to op.actorUid,
        "actionTime" to op.actionTime, "isDeleted" to false)
}
