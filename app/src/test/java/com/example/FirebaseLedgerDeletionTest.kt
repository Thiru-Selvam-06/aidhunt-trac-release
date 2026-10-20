package com.example

import com.example.data.entity.*
import com.example.data.firebase.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class FirebaseLedgerDeletionTest {
    @Test fun mobileTransportRefusesServerOnlyTransactionRpcBeforeNetworkAccess() = runBlocking {
        val transport = FirebaseLedgerDeletion.HttpTransport("demo-aidhunt-v16", "http://127.0.0.1:1", "not-a-token") {}
        for (method in listOf("beginTransaction", "rollback")) {
            try { transport.request(method, JSONObject()); fail("Mobile transport must not send $method") }
            catch (expected: IllegalArgumentException) { assertTrue(expected.message.orEmpty().contains("Server-only")) }
        }
    }
    private val id = 3758959838990728744L
    private val customerId = 3758959911078658900L
    private val now = 1792411844817L
    private fun job() = JobEntryEntity(id = id, workspaceId = "ws_test", customerId = customerId,
        customerName = "Synthetic customer", operatorName = "Partner", tractorLabel = "T", workType = "Work",
        startTimeMillis = now - 2000, endTimeMillis = now - 1000, durationMinutes = 1,
        totalAmount = 100.0, amountReceived = 100.0, pendingAmount = 0.0, addedByPartner = "Owner",
        createdAt = now - 1000, createdByUid = "owner", entryForUid = "partner", financialAttributionUid = "partner")
    private fun operation(job: JobEntryEntity = job(), actor: String = "owner") = PendingOperationEntity(
        "explicit-delete", "ws_test", actor, "JOB", job.id, "DELETE", 1, now,
        JSONObject(job.toFirestoreMap()).put("operationProtocolVersion", 2).put("linkedExpenseIds", JSONArray())
            .put("linkedCollectionIds", JSONArray()).put("linkedExpenseSnapshots", JSONArray()).toString())

    private class MemoryTransport : FirebaseLedgerDeletion.Transport {
        override val documentRoot = "projects/demo-aidhunt-v16/databases/(default)/documents"
        val records = linkedMapOf<String, JSONObject>()
        val commits = mutableListOf<JSONObject>()
        var begins = 0
        var rollbacks = 0
        var conflictOnce = false
        var sessionValid = true
        fun name(collection: String, id: Any) = "$documentRoot/workspaces/ws_test/$collection/$id"
        fun put(name: String, values: Map<String, Any?>) {
            val fields = JSONObject()
            for ((key, value) in values) fields.put(key, when(value) {
                null -> JSONObject().put("nullValue", JSONObject.NULL)
                is String -> JSONObject().put("stringValue", value)
                is Boolean -> JSONObject().put("booleanValue", value)
                is Int, is Long -> JSONObject().put("integerValue", value.toString())
                is Number -> JSONObject().put("doubleValue", value.toDouble())
                else -> error("Unsupported fixture")
            })
            records[name] = JSONObject().put("name", name).put("fields", fields)
        }
        override fun requireSession() { check(sessionValid) { "Session changed" } }
        override suspend fun request(method: String, body: JSONObject): String = when {
            method == "beginTransaction" -> JSONObject().put("transaction", "tx${++begins}").toString()
            method == "rollback" -> { rollbacks++; "{}" }
            method == "batchGet" -> JSONArray().apply {
                val names = body.getJSONArray("documents")
                for (i in 0 until names.length()) {
                    val name = names.getString(i)
                    put(records[name]?.let { JSONObject().put("found", it) } ?: JSONObject().put("missing", name))
                }
            }.toString()
            method.endsWith(":runQuery") -> JSONArray().apply {
                val collection = body.getJSONObject("structuredQuery").getJSONArray("from").getJSONObject(0).getString("collectionId")
                records.filterKeys { it.startsWith(name(collection, "")) }.values.forEach { put(JSONObject().put("document", it)) }
            }.toString()
            method == "commit" -> {
                if (conflictOnce) { conflictOnce = false; throw FirebaseLedgerDeletion.TransactionConflict() }
                commits.add(JSONObject(body.toString())); "{}"
            }
            else -> error("Unexpected method $method")
        }
    }
    private fun fixture(source: JobEntryEntity = job(), role: String = "OWNER"): MemoryTransport = MemoryTransport().apply {
        put("$documentRoot/workspaces/ws_test", mapOf("ownerUid" to "owner"))
        put(name("members", "owner"), mapOf("uid" to "owner", "role" to role, "status" to "ACTIVE"))
        put(name("entries", source.id), source.toFirestoreMap())
        put(name("customers", customerId), mapOf("id" to customerId, "workspaceId" to "ws_test", "name" to "Synthetic customer",
            "phone" to "", "createdAt" to now - 1000, "createdByUid" to "owner"))
    }
    private fun deleted(transport: MemoryTransport): Set<String> = transport.commits.single().getJSONArray("writes").let { rows ->
        (0 until rows.length()).mapNotNull { rows.getJSONObject(it).optString("delete").takeIf(String::isNotEmpty) }.toSet()
    }

    @Test fun explicitLegacyOwnerDeletionRemovesTheSourceAndUnusedCustomerInOneCommit() = runBlocking {
        val transport = fixture()
        FirebaseLedgerDeletion(transport).execute(operation())
        assertEquals(setOf(transport.name("entries", id), transport.name("customers", customerId)), deleted(transport))
        val raw = transport.commits.single().toString()
        assertTrue(raw.contains(id.toString()))
        assertTrue(raw.contains(customerId.toString()))
        val writes = transport.commits.single().getJSONArray("writes")
        val proofNames = (0 until writes.length()).mapNotNull { writes.getJSONObject(it).optJSONObject("update")?.getString("name") }
        assertTrue(proofNames.contains(transport.name("entryDeletionProofs", id)))
        assertTrue(proofNames.contains(transport.name("customerDeletionProofs", customerId)))
        assertEquals(1, transport.begins)
    }

    @Test fun currentEntryDeletesItsCollectionAndExpenseTogether() = runBlocking {
        val transport = fixture()
        val receipt = PaymentEntity(id = 31, workspaceId = "ws_test", jobEntryId = id, customerId = customerId,
            amount = 100.0, collectedByUid = "owner", createdAt = now - 1000,
            allocationsJson = """[{"jobId":"$id","amount":100}]""")
        transport.put(transport.name("collections", 31), receipt.toFirestoreMap())
        val expense = ExpenseEntity(id = 32, workspaceId = "ws_test", relatedJobId = id,
            expenseType = "Diesel", amount = 10.0, tractorLabel = "T", addedByPartner = "Owner",
            createdByUid = "owner", expenseForUid = "partner", createdAt = now - 1000)
        transport.put(transport.name("expenses", 32), expense.toFirestoreMap())
        val base = operation()
        val command = base.copy(payloadJson = JSONObject(base.payloadJson).put("linkedCollectionIds", JSONArray().put("31"))
            .put("linkedExpenseIds", JSONArray().put("32")).put("linkedExpenseSnapshots", JSONArray().put(JSONObject(expense.toFirestoreMap()))).toString())
        FirebaseLedgerDeletion(transport).execute(command)
        assertEquals(4, deleted(transport).size)
        assertTrue(deleted(transport).contains(transport.name("collections", 31)))
        assertTrue(deleted(transport).contains(transport.name("expenses", 32)))
    }

    @Test fun changedSourceIsRetainedAndTheTransactionRollsBack() = runBlocking {
        val transport = fixture(job().copy(totalAmount = 200.0, pendingAmount = 100.0))
        try { FirebaseLedgerDeletion(transport).execute(operation()); fail("Changed source must not delete") }
        catch (expected: IllegalArgumentException) { assertTrue(expected.message.orEmpty().contains("changed")) }
        assertTrue(transport.commits.isEmpty())
        assertEquals(1, transport.rollbacks)
    }

    @Test fun sourceWithMissingV2CollectionIsProtected() = runBlocking {
        val transport = fixture()
        transport.put(transport.name("entries", id), job().toFirestoreMap() + mapOf("protocolVersion" to 2, "authorizationTimeStatus" to "CLIENT_ORIGINAL_V2"))
        try { FirebaseLedgerDeletion(transport).execute(operation()); fail("Missing current Collection must not delete") }
        catch (_: IllegalArgumentException) { }
        assertTrue(transport.commits.isEmpty())
    }

    @Test fun sharedCustomerIsRecalculatedAndNeverDeleted() = runBlocking {
        val transport = fixture()
        val other = job().copy(id = id + 1, totalAmount = 200.0, amountReceived = 20.0, pendingAmount = 180.0)
        transport.put(transport.name("entries", other.id), other.toFirestoreMap())
        FirebaseLedgerDeletion(transport).execute(operation())
        assertEquals(setOf(transport.name("entries", id)), deleted(transport))
        val writes = transport.commits.single().getJSONArray("writes")
        val customerUpdate = (0 until writes.length()).mapNotNull { writes.getJSONObject(it).optJSONObject("update") }
            .single { it.getString("name") == transport.name("customers", customerId) }
        assertEquals(180.0, customerUpdate.getJSONObject("fields").getJSONObject("balanceDue").getDouble("doubleValue"), 0.0)
    }

    @Test fun conflictRetriesTheWholeTransactionAndCommitsOnce() = runBlocking {
        val transport = fixture().apply { conflictOnce = true }
        FirebaseLedgerDeletion(transport).execute(operation())
        assertEquals(2, transport.begins)
        assertEquals(1, transport.commits.size)
        assertEquals(1, transport.rollbacks)
    }

    @Test fun partnerCannotDeleteAnotherCreatorsEntry() = runBlocking {
        val transport = fixture(role = "PARTNER")
        transport.put("${transport.documentRoot}/workspaces/ws_test", mapOf("ownerUid" to "another-owner"))
        try { FirebaseLedgerDeletion(transport).execute(operation(job().copy(createdByUid = "partner"))); fail("Mismatched source must stay") }
        catch (_: IllegalArgumentException) { }
        assertTrue(transport.commits.isEmpty())
    }
}
