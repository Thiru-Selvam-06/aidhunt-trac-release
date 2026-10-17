package com.example.data.sync

import com.example.data.entity.PendingOperationEntity
import org.json.JSONObject

/** The existing durable payload is the dependency snapshot; no second queue. */
object OperationDependencies {
    /** Terminal rejected payloads remain recoverable, but are not pending financial writes. */
    fun blocksCustomerAcknowledgement(op: PendingOperationEntity, customerId: Long): Boolean {
        if (op.state != "PENDING" && !(op.state == "REJECTED" && op.failureReason.contains("Local reconciliation pending"))) return false
        if (op.entityType == "CUSTOMER") return op.entityId == customerId
        return op.entityType in setOf("JOB", "PAYMENT") && JSONObject(op.payloadJson).optLong("customerId") == customerId
    }
    fun blocksEntryAcknowledgement(op: PendingOperationEntity, jobId: Long): Boolean {
        if (op.state != "PENDING" && !(op.state == "REJECTED" && op.failureReason.contains("Local reconciliation pending"))) return false
        return (op.entityType == "JOB" && op.entityId == jobId) ||
            (op.entityType == "PAYMENT" && references(op).contains("JOB" to jobId))
    }
    fun references(op: PendingOperationEntity): Set<Pair<String, Long>> {
        val data = JSONObject(op.payloadJson)
        val refs = mutableSetOf(op.entityType to op.entityId)
        fun ref(type: String, field: String) { data.optLong(field).takeIf { it > 0 }?.let { refs.add(type to it) } }
        when (op.entityType) {
            "JOB" -> { ref("CUSTOMER", "customerId"); ref("TRACTOR", "tractorId"); ref("EXTENSION", "extensionId") }
            "EXPENSE" -> ref("JOB", "relatedJobId")
            "PAYMENT" -> { ref("JOB", "jobEntryId"); ref("CUSTOMER", "customerId")
                val allocations = org.json.JSONArray(data.optString("allocationsJson", "[]"))
                for (i in 0 until allocations.length()) allocations.getJSONObject(i).optLong("jobId").takeIf { it > 0 }?.let { refs.add("JOB" to it) }
            }
        }
        if (op.entityType == "JOB" && op.mutationType == "DELETE") {
            val dependencies = data.optJSONArray("deleteDependencies")
            if (dependencies != null) for (i in 0 until dependencies.length()) {
                val dependency = dependencies.getJSONObject(i)
                val type = dependency.getString("type")
                val id = dependency.getLong("id")
                require(type in setOf("PAYMENT", "EXPENSE") && id > 0) { "Invalid deletion dependency" }
                refs.add(type to id)
            }
        }
        return refs
    }
}
