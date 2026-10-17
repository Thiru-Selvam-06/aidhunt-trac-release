package com.example.data.firebase

/** Durable identity of an Owner-authorized purge; retries never create a new action time. */
internal data class PurgeReconciliationProtocol(val operationId: String, val actorUid: String, val actionTime: Long) {
    companion object {
        fun fromManifest(data: Map<String, Any?>, actorUid: String): PurgeReconciliationProtocol {
            val id = data["purgeOperationId"] as? String
            val time = (data["purgeActionTime"] as? Number)?.toLong()
            if (id.isNullOrBlank() || time == null || time <= 0 || data["purgeActorUid"] !is String)
                throw WorkspaceUpgradeRequiredException()
            require(data["purgeActorUid"] == actorUid) { "Purge belongs to a different authenticated Owner" }
            return PurgeReconciliationProtocol(id, actorUid, time)
        }
    }
    fun envelope(workspaceId: String, recordKey: String, dependencies: List<Map<String, String>>): Map<String, Any> = mapOf(
        "workspaceId" to workspaceId, "protocolVersion" to WorkspaceCommandProtocol.VERSION,
        "operationType" to "UPSERT", "dependencies" to dependencies,
        "operationId" to "$operationId:$recordKey", "actorUid" to actorUid, "actionTime" to actionTime, "isDeleted" to false
    )
    fun entryUpdate(workspaceId: String, targetUid: String, path: String, data: Map<String, Any?>,
                    amount: Double, dependencies: List<Map<String, String>>): Map<String, Any> {
        val parts = path.split('/')
        require(parts.size == 4 && parts[0] == "workspaces" && parts[1] == workspaceId && parts[2] == "entries" && parts[3].toLongOrNull()?.let { it > 0 } == true) { "Invalid purge Entry scope" }
        require(data["workspaceId"] == workspaceId && data["isDeleted"] != true) { "Retained Entry is unavailable" }
        val total = (data["totalAmount"] as? Number)?.toDouble() ?: error("Missing Entry total")
        val received = (data["amountReceived"] as? Number)?.toDouble() ?: error("Missing Entry paid amount")
        val due = (data["pendingAmount"] as? Number)?.toDouble() ?: error("Missing Entry due")
        require(listOf(total, received, due, amount).all { it.isFinite() && it >= 0 } && amount > 0 && received >= amount && kotlin.math.abs(total - received - due) < 0.001) { "Purge reconciliation conflict" }
        return envelope(workspaceId, parts[3], dependencies) + mapOf("amountReceived" to received - amount, "pendingAmount" to due + amount, "purgeUid" to targetUid)
    }
}
