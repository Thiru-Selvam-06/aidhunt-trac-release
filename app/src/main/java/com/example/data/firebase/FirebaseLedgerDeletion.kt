package com.example.data.firebase

import com.example.data.entity.PendingOperationEntity
import com.example.data.sync.EntryDeletionPolicy
import com.example.ui.util.FinancialCalculationEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Firebase-only deletion. Guarded reads and an atomic commit use the signed-in user's access;
 * current membership. No service credential, Cloud Function or third-party endpoint is used. */
internal class FirebaseLedgerDeletion(private val transport: Transport) {
    interface Transport {
        val documentRoot: String
        suspend fun prepare(op: PendingOperationEntity) {}
        suspend fun request(method: String, body: JSONObject): String
        fun requireSession()
    }
    private val root get() = transport.documentRoot
    private fun name(ws: String, collection: String, id: String) = "$root/workspaces/$ws/$collection/$id"
    private fun rows(raw: String): List<JSONObject> = JSONArray(raw).let { a -> (0 until a.length()).map { a.getJSONObject(it) } }
    private fun decode(value: JSONObject): Any? = when {
        value.has("integerValue") -> value.getString("integerValue").toLong()
        value.has("doubleValue") -> value.getDouble("doubleValue")
        value.has("stringValue") -> value.getString("stringValue")
        value.has("booleanValue") -> value.getBoolean("booleanValue")
        value.has("nullValue") -> null
        else -> null
    }
    private fun data(document: JSONObject): Map<String, Any?> = document.getJSONObject("fields").let { fields ->
        fields.keys().asSequence().associateWith { decode(fields.getJSONObject(it)) }
    }
    private fun value(value: Any?): JSONObject = when (value) {
        null -> JSONObject().put("nullValue", JSONObject.NULL)
        is String -> JSONObject().put("stringValue", value)
        is Boolean -> JSONObject().put("booleanValue", value)
        is Int, is Long -> JSONObject().put("integerValue", value.toString())
        is Number -> JSONObject().put("doubleValue", value.toDouble())
        is Collection<*> -> JSONObject().put("arrayValue", JSONObject().put("values", JSONArray().apply {
            value.forEach { put(this@FirebaseLedgerDeletion.value(it)) }
        }))
        else -> error("Unsupported deletion field")
    }
    private fun fields(values: Map<String, Any?>) = JSONObject().apply { values.forEach { (key, item) -> put(key, value(item)) } }
    private suspend fun get(transaction: String, names: List<String>): List<JSONObject> = rows(transport.request(
        "batchGet", JSONObject().put("documents", JSONArray(names)).put("transaction", transaction)))
        .mapNotNull { it.optJSONObject("found") }
    private suspend fun list(transaction: String, ws: String, collection: String): List<JSONObject> = rows(transport.request(
        "workspaces/$ws:runQuery", JSONObject().put("transaction", transaction).put("structuredQuery",
            JSONObject().put("from", JSONArray().put(JSONObject().put("collectionId", collection))))))
        .mapNotNull { it.optJSONObject("document") }
    private fun delete(document: JSONObject) = JSONObject().put("delete", document.getString("name"))
    private fun create(name: String, values: Map<String, Any?>): JSONObject = JSONObject()
        .put("update", JSONObject().put("name", name).put("fields", fields(values)))
        .put("currentDocument", JSONObject().put("exists", false))

    suspend fun execute(op: PendingOperationEntity) {
        require(op.entityType in setOf("JOB", "CUSTOMER") && op.mutationType == "DELETE")
        require(op.workspaceId.matches(Regex("ws_[A-Za-z0-9_-]{1,124}")) && op.entityId > 0 && op.actorUid.isNotBlank())
        require(op.operationId.matches(Regex("[A-Za-z0-9-]{1,128}")) && op.actionTime > 0)
        transport.prepare(op)
        repeat(3) { attempt ->
            transport.requireSession()
            val transaction = JSONObject(transport.request("beginTransaction", JSONObject())).getString("transaction")
            try {
                executeTransaction(op, transaction)
                return
            } catch (error: Throwable) {
                runCatching { transport.request("rollback", JSONObject().put("transaction", transaction)) }
                if (error !is TransactionConflict || attempt == 2) throw error
            }
        }
    }

    private suspend fun executeTransaction(op: PendingOperationEntity, transaction: String) {
        val ws = op.workspaceId
        val id = op.entityId.toString()
        val sourceCollection = if (op.entityType == "JOB") "entries" else "customers"
        val proofCollection = if (op.entityType == "JOB") "entryDeletionProofs" else "customerDeletionProofs"
        val proofName = name(ws, proofCollection, id)
        val sources = get(transaction, listOf("$root/workspaces/$ws", name(ws, "members", op.actorUid),
            name(ws, sourceCollection, id), proofName)).associateBy { it.getString("name") }
        val workspace = sources["$root/workspaces/$ws"]?.let(::data) ?: error("Workspace is missing")
        val member = sources[name(ws, "members", op.actorUid)]?.let(::data) ?: error("Workspace access is missing")
        require(member["status"].toString().equals("ACTIVE", true)) { "Workspace access removed" }
        val role = member["role"].toString().uppercase()
        val owner = role == "OWNER" && workspace["ownerUid"] == op.actorUid
        require(owner || role == "PARTNER") { "Ask an Owner or Partner to delete this financial entry" }
        sources[proofName]?.let { proof ->
            val saved = data(proof)
            require(saved["operationId"] == op.operationId && saved["actorUid"] == op.actorUid && saved["actionTime"] == op.actionTime) {
                "Deletion was confirmed by another action; refresh the shared records"
            }
            transport.request("rollback", JSONObject().put("transaction", transaction))
            return
        }
        val source = sources[name(ws, sourceCollection, id)] ?: error("Record missing without a deletion acknowledgement")
        require(data(source)["workspaceId"] == ws) { "Record workspace mismatch" }
        val entries = list(transaction, ws, "entries")
        val collections = list(transaction, ws, "collections")
        val writes = JSONArray()
        val proof = mutableMapOf<String, Any?>("workspaceId" to ws, "actorUid" to op.actorUid,
            "operationId" to op.operationId, "actionTime" to op.actionTime, "protocolVersion" to 2,
            "recordId" to id, "sourceCreatedAt" to data(source)["createdAt"])
        if (op.entityType == "CUSTOMER") {
            require(owner) { "Only the Owner may delete a customer" }
            require((entries + collections).none { data(it)["customerId"].toString() == id }) {
                "Customer is still referenced by business records"
            }
        } else {
            val entry = jobEntryFromFirestoreMap(data(source))
            val payload = jobEntryFromFirestoreMap(JSONObject(op.payloadJson).let { json ->
                json.keys().asSequence().associateWith { json.opt(it).takeUnless { item -> item == JSONObject.NULL } }
            })
            require(entry.id == op.entityId && entry.workspaceId == ws &&
                entry.copy(isSynced = true, updatedAt = 0) == payload.copy(isSynced = true, updatedAt = 0)) {
                "Entry changed after this deletion was requested; refresh and review it before deleting"
            }
            require(owner || (entry.createdByUid == op.actorUid && !entry.isHistorical &&
                data(source)["authorizationTimeStatus"] in setOf("CLIENT_ORIGINAL_V2", "VERIFIED") &&
                com.example.data.auth.AuthorizationManager.within24Hours(entry.createdAt, op.actionTime))) {
                "Entry deletion is outside your permissions or deadline"
            }
            val payments = collections.map { doc -> paymentFromFirestoreMap(data(doc)).also {
                require(it.workspaceId == ws && it.id.toString() == doc.getString("name").substringAfterLast('/')) {
                    "Collection identity needs reconciliation before deletion"
                }
            } }
            val linked = payments.filter { EntryDeletionPolicy.references(it, entry.id) }
            val toDelete = EntryDeletionPolicy.plannedCollectionDeletes(entry, linked, owner, op.actorUid, role, op.actionTime)
            require(owner || toDelete.size <= 5) {
                "This Entry has more than five receipts. Ask the Owner to delete it, or reverse your Collections first."
            }
            proof["collectionIds"] = toDelete.map { it.id.toString() }
            // Legacy embedded receipts are allowed only for an explicit Owner action on pre-collection data.
            if (kotlin.math.abs(entry.amountReceived - toDelete.sumOf { it.amount }) >= 0.01)
                require(owner && linked.isEmpty() && data(source)["protocolVersion"] == null &&
                    data(source)["authorizationTimeStatus"] == null && data(source)["collectionId"] == null) {
                    "Entry and Collection balances need reconciliation before deletion"
                }
            val expenses = list(transaction, ws, "expenses")
            val linkedExpenses = expenses.filter { data(it)["relatedJobId"].toString() == id }
            val requested = JSONObject(op.payloadJson)
            val expectedExpenses = requested.optJSONArray("linkedExpenseIds")
            val expectedCollections = requested.optJSONArray("linkedCollectionIds")
            fun ids(array: JSONArray): Set<String> = (0 until array.length()).map { array.getString(it) }.toSet()
            require(expectedExpenses != null && expectedCollections != null &&
                ids(expectedExpenses) == linkedExpenses.map { it.getString("name").substringAfterLast('/') }.toSet() &&
                ids(expectedCollections) == toDelete.map { it.id.toString() }.toSet()) {
                "Related records changed or the old deletion needs review; refresh the entry before deleting"
            }
            val expectedSnapshots = requested.optJSONArray("linkedExpenseSnapshots") ?: JSONArray()
            val snapshots = (0 until expectedSnapshots.length()).map { index ->
                val json = expectedSnapshots.getJSONObject(index)
                expenseFromFirestoreMap(json.keys().asSequence().associateWith { key -> json.opt(key).takeUnless { it == JSONObject.NULL } })
            }.associateBy { it.id }
            require(linkedExpenses.all { doc ->
                val expense = expenseFromFirestoreMap(data(doc))
                snapshots[expense.id]?.copy(isSynced = true) == expense.copy(isSynced = true)
            }) { "A linked Expense changed; refresh and review the entry before deleting" }
            EntryDeletionPolicy.requireNoLinkedExpenses(entry, linkedExpenses.map { expenseFromFirestoreMap(data(it)) }, owner, op.actorUid, role, op.actionTime)
            require(linkedExpenses.all { data(it)["workspaceId"] == ws }) { "Linked Expense workspace mismatch" }
            val deletedCollections = collections.filter { doc -> toDelete.any { it.id.toString() == doc.getString("name").substringAfterLast('/') } }
            fun audit(documents: List<JSONObject>): String = JSONArray().apply {
                documents.forEach { doc -> put(JSONObject().put("documentId", doc.getString("name").substringAfterLast('/'))
                    .put("fields", doc.getJSONObject("fields"))) }
            }.toString().also { require(it.length <= 131072) { "Linked audit is too large for one deletion" } }
            proof["deletedCollectionAuditJson"] = audit(deletedCollections)
            proof["deletedExpenseAuditJson"] = audit(linkedExpenses)
            deletedCollections.forEach { writes.put(delete(it)) }
            linkedExpenses.forEach { writes.put(delete(it)) }
            val customerName = name(ws, "customers", entry.customerId.toString())
            val customer = get(transaction, listOf(customerName)).singleOrNull() ?: error("Source Customer missing")
            require(data(customer)["workspaceId"] == ws) { "Customer workspace mismatch" }
            val retained = entries.filterNot { it.getString("name") == source.getString("name") }
            val retainedCollections = collections.filterNot { doc -> toDelete.any { it.id.toString() == doc.getString("name").substringAfterLast('/') } }
            val customerUnused = (retained + retainedCollections).none { data(it)["customerId"].toString() == entry.customerId.toString() }
            if (owner && customerUnused) {
                writes.put(delete(customer))
                writes.put(create(name(ws, "customerDeletionProofs", entry.customerId.toString()), mapOf(
                    "workspaceId" to ws, "actorUid" to op.actorUid, "operationId" to op.operationId,
                    "actionTime" to op.actionTime, "protocolVersion" to 2, "recordId" to entry.customerId.toString(),
                    "sourceCreatedAt" to data(customer)["createdAt"], "entryId" to id)))
            } else {
                val customerEntity = customerFromFirestoreMap(data(customer), fallbackId = entry.customerId)
                val stats = FinancialCalculationEngine.calculateCustomerFinancials(customerEntity,
                    retained.filter { data(it)["isDeleted"] != true }.map { jobEntryFromFirestoreMap(data(it)) })
                val updated = JSONObject().put("name", customerName).put("fields", JSONObject(customer.getJSONObject("fields").toString()))
                updated.getJSONObject("fields").apply {
                    put("totalBilled", value(stats.totalBilled)); put("totalPaid", value(stats.totalPaid)); put("balanceDue", value(stats.balanceDue))
                    put("protocolVersion", value(2)); put("operationType", value("UPSERT")); put("actorUid", value(op.actorUid))
                    put("operationId", value(op.operationId)); put("actionTime", value(op.actionTime)); put("isDeleted", value(false))
                    put("dependencies", JSONObject().put("arrayValue", JSONObject().put("values", JSONArray())))
                }
                writes.put(JSONObject().put("update", updated))
            }
            proof.putAll(mapOf("customerId" to entry.customerId, "sourceCreatorUid" to entry.createdByUid,
                "sourceBeneficiaryUid" to entry.entryForUid, "totalAmount" to entry.totalAmount,
                "amountReceived" to entry.amountReceived, "pendingAmount" to entry.pendingAmount))
        }
        writes.put(delete(source))
        writes.put(create(proofName, proof))
        require(writes.length() <= 450) { "Too many related records to delete in one action" }
        transport.requireSession()
        transport.request("commit", JSONObject().put("transaction", transaction).put("writes", writes))
    }

    class TransactionConflict : java.io.IOException("Record changed during deletion; retrying safely")

    internal class HttpTransport(project: String, private val origin: String, private val token: String,
        private val sessionCheck: () -> Unit) : Transport {
        override val documentRoot = "projects/$project/databases/(default)/documents"
        private var guarded: FirebaseUserDeletionTransport? = null
        override suspend fun prepare(op: PendingOperationEntity) {
            guarded = FirebaseUserDeletionTransport(object : Transport {
                override val documentRoot get() = this@HttpTransport.documentRoot
                override fun requireSession() = this@HttpTransport.requireSession()
                override suspend fun request(method: String, body: JSONObject) = wireRequest(method, body)
            }, op)
        }
        override fun requireSession() = sessionCheck()
        override suspend fun request(method: String, body: JSONObject): String =
            guarded?.request(method, body) ?: wireRequest(method, body)
        private suspend fun wireRequest(method: String, body: JSONObject): String {
            require(method !in setOf("beginTransaction", "rollback")) { "Server-only transaction RPC cannot be sent with mobile authentication" }
            requireSession()
            val path = if (method.contains(':')) "$documentRoot/$method" else "$documentRoot:$method"
            val request = okhttp3.Request.Builder().url("$origin/v1/$path").header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            http.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val error = runCatching { JSONObject(raw).optJSONObject("error") }.getOrNull()
                    if (error?.optString("status") in setOf("ABORTED", "FAILED_PRECONDITION", "ALREADY_EXISTS")) throw TransactionConflict()
                    if (response.code == 401) throw WorkspaceAuthenticationRequiredException()
                    if (response.code == 403) throw SecurityException("Firebase rejected deletion during $method; verify the deployed deletion rules. Original data retained.")
                    error("Firebase deletion could not complete (${error?.optString("status") ?: response.code}); original action retained")
                }
                return raw
            }
        }
    }

    companion object {
        suspend fun delete(op: PendingOperationEntity) = withContext(Dispatchers.IO) {
            val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
            val user = auth.currentUser ?: throw WorkspaceAuthenticationRequiredException()
            require(user.uid == op.actorUid) { "Deletion account changed" }
            val project = com.google.firebase.FirebaseApp.getInstance().options.projectId ?: error("Firebase project missing")
            val token = user.getIdToken(false).await().token ?: throw WorkspaceAuthenticationRequiredException()
            val origin = if (com.example.BuildConfig.DEVICE_TEST) "http://127.0.0.1:18180" else "https://firestore.googleapis.com"
            val transport = HttpTransport(project, origin, token) {
                if (auth.currentUser?.uid != op.actorUid) throw WorkspaceSessionChangedException()
            }
            FirebaseLedgerDeletion(transport).execute(op)
        }
        private val http = okhttp3.OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
    }
}
