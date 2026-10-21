package com.example.ui.util

import com.example.data.entity.PendingOperationEntity
import org.json.JSONObject

/** Read-only projection of immutable commands, kept separate from confirmed ledger totals. */
object RetainedActionDetails {
    fun summary(action: PendingOperationEntity): String = runCatching {
        val data = JSONObject(action.payloadJson)
        val amount = if (action.entityType == "JOB") data.opt("totalAmount") else data.opt("amount")
        listOf(data.optString("customerName").ifBlank { action.entityType },
            data.optString("workType"), amount?.takeUnless { it == JSONObject.NULL }?.let { "₹$it" }.orEmpty())
            .filter { it.isNotBlank() }.joinToString(" · ")
    }.getOrDefault("Original ${action.entityType.lowercase()} saved on this device")
    fun blocked(action: PendingOperationEntity): Boolean = action.state == "PENDING" &&
        action.failureReason.startsWith("Blocked by a rejected earlier action")
    fun status(action: PendingOperationEntity): String = when {
        action.state == "REJECTED" -> "Needs Review"
        blocked(action) -> "Blocked"
        action.state == "ACKNOWLEDGED" -> "Synced"
        action.state == "INVALIDATED" -> "Cancelled"
        else -> "Pending Sync"
    }
    fun text(action: PendingOperationEntity): String = runCatching {
        val data = JSONObject(action.payloadJson)
        val amount = if (action.entityType == "JOB") data.opt("totalAmount") else data.opt("amount")
        buildString {
            append(data.optString("customerName").ifBlank { action.entityType })
            if (data.optString("workType").isNotBlank()) append(" · ${data.optString("workType")}")
            if (amount != null && amount != JSONObject.NULL) append(" · ₹$amount")
            append("\nOriginal actor: ${action.actorUid}")
            for (field in listOf("createdByUid", "entryForUid", "collectorUid", "collectedByUid", "paidByUid")) {
                if (data.optString(field).isNotBlank()) append("\n$field: ${data.optString(field)}")
            }
            if (action.entityType == "JOB") append("\nRecorded paid: ₹${data.opt("amountReceived")} · Due: ₹${data.opt("pendingAmount")}")
            append("\nOriginal time: ${action.actionTime}")
        }
    }.getOrElse { "Original action retained; payload cannot be displayed. Operation ${action.operationId}" }
}
