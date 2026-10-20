package com.example.data.firebase

import com.example.data.entity.PendingOperationEntity
import org.json.JSONArray
import org.json.JSONObject

/** Firebase-user-compatible operations only. Rules guard the affected Entry/Customer while
 * dependencies are read; version preconditions and the final deletes commit atomically.
 * No IAM transaction RPC, elevated credential, timer-based unlock, or financial cleanup. */
internal class FirebaseUserDeletionTransport(
    private val wire: FirebaseLedgerDeletion.Transport,
    private val op: PendingOperationEntity
) : FirebaseLedgerDeletion.Transport {
    override val documentRoot get() = wire.documentRoot
    override fun requireSession() = wire.requireSession()
    private val root get() = "$documentRoot/workspaces/${op.workspaceId}"
    private val read = linkedMapOf<String, JSONObject>()
    private var locks = emptyList<JSONObject>()
    private var initial = true
    private fun text(value: String) = JSONObject().put("stringValue", value)
    private fun number(value: Long) = JSONObject().put("integerValue", value.toString())
    private fun rows(raw: String) = JSONArray(raw).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    private suspend fun fetch(names: List<String>): List<JSONObject> = rows(wire.request("batchGet",
        JSONObject().put("documents", JSONArray(names)))).mapNotNull { it.optJSONObject("found") }.also(::remember)
    private fun remember(documents: List<JSONObject>) { documents.forEach { read.putIfAbsent(it.getString("name"), it) } }
    private fun version(doc: JSONObject) = JSONObject().put("updateTime", doc.getString("updateTime"))
    private fun delete(doc: JSONObject) = JSONObject().put("delete", doc.getString("name")).put("currentDocument", version(doc))

    override suspend fun request(method: String, body: JSONObject): String {
        requireSession()
        when (method) {
            "beginTransaction" -> {
                read.clear(); locks = emptyList(); initial = true
                return JSONObject().put("transaction", "local-guarded-delete").toString()
            }
            "rollback" -> {
                // A definite failure cancels only this attempt's guards. If a response was lost,
                // version checks cannot remove a different operation's guards or financial data.
                if (locks.isNotEmpty()) wire.request("commit", JSONObject().put("writes", JSONArray().apply {
                    locks.forEach { put(delete(it)) }
                }))
                locks = emptyList()
                return "{}"
            }
            "commit" -> {
                check(locks.isNotEmpty()) { "Deletion guards were not acquired" }
                val writes = JSONArray(body.getJSONArray("writes").toString())
                for (index in 0 until writes.length()) {
                    val write = writes.getJSONObject(index)
                    val name = write.optString("delete").ifBlank { write.optJSONObject("update")?.optString("name").orEmpty() }
                    read[name]?.let { write.put("currentDocument", version(it)) }
                }
                locks.forEach { writes.put(delete(it)) }
                val result = wire.request("commit", JSONObject().put("writes", writes))
                locks = emptyList()
                return result
            }
        }
        val request = JSONObject(body.toString()).apply { remove("transaction") }
        val response = wire.request(method, request)
        val documents = rows(response).mapNotNull { it.optJSONObject(if (method == "batchGet") "found" else "document") }
        remember(documents)
        if (method == "batchGet" && initial) {
            initial = false
            val type = if (op.entityType == "JOB") "entries" else "customers"
            val proofType = if (op.entityType == "JOB") "entryDeletionProofs" else "customerDeletionProofs"
            if (read["$root/$proofType/${op.entityId}"] == null) {
                val source = read["$root/$type/${op.entityId}"] ?: return response
                acquire(source)
            }
        }
        return response
    }

    private suspend fun acquire(source: JSONObject) {
        val fields = source.getJSONObject("fields")
        val customerId = if (op.entityType == "JOB") fields.getJSONObject("customerId").getString("integerValue") else op.entityId.toString()
        val customer = if (op.entityType == "JOB") fetch(listOf("$root/customers/$customerId")).singleOrNull()
            ?: error("Source Customer missing") else source
        val specs = mutableListOf(Triple("CUSTOMER", customerId, customer))
        if (op.entityType == "JOB") specs.add(Triple("ENTRY", op.entityId.toString(), source))
        val paths = specs.map { (kind, id, _) -> "$root/ledgerDeletionLocks/${kind}_$id" }
        val existing = fetch(paths).associateBy { it.getString("name") }
        val writes = JSONArray()
        for ((index, spec) in specs.withIndex()) {
            val (kind, id, original) = spec
            val path = paths[index]
            existing[path]?.let { old ->
                if (old.getJSONObject("fields").getJSONObject("actorUid").getString("stringValue") != op.actorUid)
                    throw java.io.IOException("Another member's deletion is pending for this customer; retry after it finishes")
            }
            val guard = JSONObject().put("workspaceId", text(op.workspaceId)).put("recordId", text(id))
                .put("kind", text(kind)).put("actorUid", text(op.actorUid)).put("operationId", text(op.operationId))
                .put("actionTime", number(op.actionTime)).put("jobId", text(if (op.entityType == "JOB") op.entityId.toString() else ""))
                .put("sourceCreatedAt", original.getJSONObject("fields").optJSONObject("createdAt") ?: JSONObject().put("nullValue", JSONObject.NULL))
            writes.put(JSONObject().put("update", JSONObject().put("name", path).put("fields", guard))
                .put("currentDocument", existing[path]?.let(::version) ?: JSONObject().put("exists", false)))
        }
        // Final mutation preconditions retain the first source versions read. An edit
        // racing guard acquisition therefore rejects the whole financial commit.
        wire.request("commit", JSONObject().put("writes", writes))
        val acquired = fetch(paths)
        locks = acquired.filter {
            val data = it.getJSONObject("fields")
            data.getJSONObject("operationId").getString("stringValue") == op.operationId &&
                data.getJSONObject("actorUid").getString("stringValue") == op.actorUid
        }
        if (locks.size != paths.size) throw FirebaseLedgerDeletion.TransactionConflict()
        val originals = listOf(source, customer).distinctBy { it.getString("name") }
        val confirmed = fetch(originals.map { it.getString("name") }).associateBy { it.getString("name") }
        if (originals.any { original -> confirmed[original.getString("name")]?.optString("updateTime") != original.getString("updateTime") })
            throw FirebaseLedgerDeletion.TransactionConflict()
    }
}
