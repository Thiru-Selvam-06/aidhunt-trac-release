package com.example

import com.example.data.entity.*
import com.example.data.firebase.*
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

/** Runs the actual app deletion planner and HTTP transport against local Firestore Rules. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class FirebaseOnlyDeletionEmulatorTest {
    private val origin = "http://127.0.0.1:18180"
    private val root = "projects/demo-aidhunt-v16/databases/(default)/documents"
    private val id = 3758959838990728744L
    private val customerId = 3758959911078658900L
    private val now get() = System.currentTimeMillis()
    private val client = okhttp3.OkHttpClient()
    private fun name(collection: String, id: Any) = "$root/workspaces/ws_test/$collection/$id"
    private fun request(path: String, method: String = "POST", body: JSONObject? = null, token: String = "owner"): Pair<Int, String> {
        val builder = okhttp3.Request.Builder().url("$origin/$path").header("Authorization", "Bearer $token")
        builder.method(method, if (method == "GET" || method == "DELETE") null else (body ?: JSONObject()).toString().toRequestBody("application/json".toMediaType()))
        return client.newCall(builder.build()).execute().use { it.code to it.body?.string().orEmpty() }
    }
    private fun jwt(uid: String): String {
        fun encoded(value: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray())
        val issued = now / 1000
        return encoded("""{"alg":"none","typ":"JWT"}""") + "." + encoded(JSONObject().put("sub", uid).put("user_id", uid)
            .put("aud", "demo-aidhunt-v16").put("iss", "https://securetoken.google.com/demo-aidhunt-v16")
            .put("iat", issued).put("auth_time", issued).put("exp", issued + 3600).toString()) + "."
    }
    private fun typed(value: Any?): JSONObject = when(value) {
        null -> JSONObject().put("nullValue", JSONObject.NULL)
        is String -> JSONObject().put("stringValue", value)
        is Boolean -> JSONObject().put("booleanValue", value)
        is Int, is Long -> JSONObject().put("integerValue", value.toString())
        is Number -> JSONObject().put("doubleValue", value.toDouble())
        else -> error("Unsupported fixture")
    }
    private fun update(name: String, values: Map<String, Any?>) = JSONObject().put("update", JSONObject().put("name", name)
        .put("fields", JSONObject().apply { values.forEach { (key, value) -> put(key, typed(value)) } }))
    private fun seed(vararg records: Pair<String, Map<String, Any?>>) {
        val result = request("v1/$root:commit", body = JSONObject().put("writes", JSONArray().apply {
            records.forEach { (name, data) -> put(update(name, data)) }
        }))
        assertEquals(result.second, 200, result.first)
    }
    private fun job(actor: String = "owner", paid: Double = 40.0): JobEntryEntity = JobEntryEntity(
        id = id, workspaceId = "ws_test", customerId = customerId, customerName = "Synthetic Customer",
        operatorName = "Partner", tractorLabel = "T", workType = "Work", startTimeMillis = now - 2000,
        endTimeMillis = now - 1000, durationMinutes = 1, totalAmount = 100.0, amountReceived = paid,
        pendingAmount = 100.0 - paid, addedByPartner = actor, createdAt = now - 1000,
        createdByUid = actor, entryForUid = "partner", financialAttributionUid = "partner")
    private fun op(source: JobEntryEntity, actor: String = "owner"): PendingOperationEntity {
        fun documents(collection: String) = JSONArray(request("v1/$root/workspaces/ws_test:runQuery", body = JSONObject()
            .put("structuredQuery", JSONObject().put("from", JSONArray().put(JSONObject().put("collectionId", collection))))).second)
        val expenseIds = JSONArray(); val collectionIds = JSONArray(); val snapshots = JSONArray()
        val expenses = documents("expenses")
        for (index in 0 until expenses.length()) expenses.getJSONObject(index).optJSONObject("document")?.let { doc ->
            val fields = doc.getJSONObject("fields")
            if (fields.optJSONObject("relatedJobId")?.optString("integerValue") == id.toString()) {
                expenseIds.put(doc.getString("name").substringAfterLast('/'))
                snapshots.put(JSONObject().apply { fields.keys().forEach { key ->
                    val value = fields.getJSONObject(key)
                    put(key, when {
                        value.has("integerValue") -> value.getString("integerValue").toLong()
                        value.has("doubleValue") -> value.getDouble("doubleValue")
                        value.has("stringValue") -> value.getString("stringValue")
                        value.has("booleanValue") -> value.getBoolean("booleanValue")
                        else -> JSONObject.NULL
                    })
                } })
            }
        }
        val collections = documents("collections")
        for (index in 0 until collections.length()) collections.getJSONObject(index).optJSONObject("document")?.let { doc ->
            val fields = doc.getJSONObject("fields")
            if (fields.optJSONObject("jobEntryId")?.optString("integerValue") == id.toString() &&
                fields.optJSONObject("status")?.optString("stringValue") == "ACTIVE") collectionIds.put(doc.getString("name").substringAfterLast('/'))
        }
        return PendingOperationEntity("explicit-delete", "ws_test", actor, "JOB", id, "DELETE", 1, now,
            JSONObject(source.toFirestoreMap()).put("operationProtocolVersion", 2).put("linkedExpenseIds", expenseIds)
                .put("linkedCollectionIds", collectionIds).put("linkedExpenseSnapshots", snapshots).toString())
    }
    private fun service(actor: String) = FirebaseLedgerDeletion(FirebaseLedgerDeletion.HttpTransport("demo-aidhunt-v16", origin, jwt(actor)) {})
    private fun exists(collection: String, id: Any): Boolean = request("v1/${name(collection, id)}", "GET").first == 200

    private fun newJobWrite(source: JobEntryEntity, recordId: Long): JSONObject {
        val write = update(name("entries", recordId), source.copy(id = recordId).toFirestoreMap() + mapOf(
            "protocolVersion" to 2, "operationType" to "UPSERT", "actorUid" to "owner", "operationId" to "new-job",
            "actionTime" to now, "isDeleted" to false, "authorizationTimeStatus" to "CLIENT_ORIGINAL_V2"))
        write.getJSONObject("update").getJSONObject("fields").apply {
            put("dependencies", JSONObject().put("arrayValue", JSONObject().put("values", JSONArray())))
            put("authorizationCreatedAt", JSONObject().put("timestampValue", java.time.Instant.ofEpochMilli(source.createdAt).toString()))
        }
        return write.put("updateTransforms", JSONArray().put(JSONObject().put("fieldPath", "serverReceivedAt").put("setToServerValue", "REQUEST_TIME")))
    }

    @Test fun screenshotUnpaidOwnerEntryWithIntegerAmountsAndLowercaseMembershipDeletes() = runBlocking {
        val source = job(paid = 0.0).copy(totalAmount = 5500.0, pendingAmount = 5500.0,
            entryForUid = "owner", financialAttributionUid = "owner", createdByRole = "Owner")
        seed(name("members", "owner") to mapOf("uid" to "owner", "role" to "owner", "status" to "active"),
            name("entries", id) to (source.toFirestoreMap() + mapOf("totalAmount" to 5500L, "pendingAmount" to 5500L,
                "amountReceived" to 0L, "protocolVersion" to 2, "authorizationTimeStatus" to "CLIENT_ORIGINAL_V2")))
        service("owner").execute(op(source))
        assertFalse(exists("entries", id)); assertFalse(exists("customers", customerId))
        assertFalse(exists("ledgerDeletionLocks", "ENTRY_$id")); assertFalse(exists("ledgerDeletionLocks", "CUSTOMER_$customerId"))
    }

    @Test fun validNewJobCreationWorksWhenCustomerIsNotBeingDeleted() {
        val result = request("v1/$root:commit", body = JSONObject().put("writes", JSONArray().put(newJobWrite(job(paid = 0.0), id + 1))), token = jwt("owner"))
        assertEquals(result.second, 200, result.first)
    }

    @Before fun localEmulatorOnly() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR_HOST") == "127.0.0.1:18180" &&
            System.getenv("GCLOUD_PROJECT") == "demo-aidhunt-v16" && System.getenv("GOOGLE_APPLICATION_CREDENTIALS").isNullOrBlank())
        assertEquals(200, request("emulator/v1/projects/demo-aidhunt-v16/databases/(default)/documents", "DELETE").first)
        seed("$root/workspaces/ws_test" to mapOf("workspaceId" to "ws_test", "ownerUid" to "owner"),
            name("members", "owner") to mapOf("uid" to "owner", "workspaceId" to "ws_test", "role" to "OWNER", "status" to "ACTIVE"),
            name("members", "partner") to mapOf("uid" to "partner", "workspaceId" to "ws_test", "role" to "PARTNER", "status" to "ACTIVE"),
            name("customers", customerId) to mapOf("id" to customerId, "workspaceId" to "ws_test", "name" to "Synthetic Customer",
                "phone" to "", "createdByUid" to "owner", "createdAt" to now - 3000, "isDeleted" to false))
    }
    private fun seedCurrent(source: JobEntryEntity, collector: String = "owner", expenseCreator: String? = "owner") {
        val extras = mapOf("protocolVersion" to 2, "authorizationTimeStatus" to "CLIENT_ORIGINAL_V2", "isDeleted" to false)
        seed(name("entries", id) to (source.toFirestoreMap() + extras),
            name("collections", 31) to PaymentEntity(id = 31, workspaceId = "ws_test", jobEntryId = id, customerId = customerId,
                amount = source.amountReceived, collectedByUid = collector, createdAt = source.createdAt,
                allocationsJson = """[{"jobId":"$id","amount":${source.amountReceived}}]""").toFirestoreMap() + extras)
        if (expenseCreator != null) seed(name("expenses", 32) to ExpenseEntity(id = 32, workspaceId = "ws_test", relatedJobId = id,
            expenseType = "Diesel", amount = 10.0, tractorLabel = "T", addedByPartner = expenseCreator,
            createdByUid = expenseCreator, expenseForUid = source.entryForUid, createdAt = source.createdAt).toFirestoreMap() + extras)
    }

    @Test fun ownerDeletesSourcePaymentExpenseAndFinalCustomerThroughFirebaseOnly() = runBlocking {
        val source = job(); seedCurrent(source)
        val command = op(source)
        service("owner").execute(command)
        for ((collection, recordId) in listOf("entries" to id, "collections" to 31L, "expenses" to 32L, "customers" to customerId))
            assertFalse("$collection must be removed", exists(collection, recordId))
        assertTrue(exists("entryDeletionProofs", id)); assertTrue(exists("customerDeletionProofs", customerId))
        service("owner").execute(command) // Same command must be idempotent.
    }

    @Test fun partnerDeletesOwnEntryAndOwnFinancialChildrenWithoutDeletingSharedCustomerMetadata() = runBlocking {
        val source = job("partner"); seedCurrent(source, "partner", "partner")
        service("partner").execute(op(source, "partner"))
        assertFalse(exists("entries", id)); assertFalse(exists("collections", 31)); assertFalse(exists("expenses", 32))
        assertTrue(exists("customers", customerId))
        assertTrue(exists("entryDeletionProofs", id))
    }

    @Test fun partnerCannotDeleteAnOwnersLinkedCollectionAndAllRecordsRemain() = runBlocking {
        val source = job("partner"); seedCurrent(source, "owner", "partner")
        try { service("partner").execute(op(source, "partner")); fail("Owner receipt must remain protected") }
        catch (_: IllegalArgumentException) { }
        assertTrue(exists("entries", id)); assertTrue(exists("collections", 31)); assertTrue(exists("expenses", 32))
        assertFalse(exists("entryDeletionProofs", id))
        assertFalse(exists("ledgerDeletionLocks", "ENTRY_$id")); assertFalse(exists("ledgerDeletionLocks", "CUSTOMER_$customerId"))
    }

    @Test fun aPartnerAndOutsiderCannotGuardAnOwnersEntry() {
        val source = job(paid = 0.0)
        seed(name("entries", id) to source.toFirestoreMap())
        for (actor in listOf("partner", "outsider")) {
            val guard = update(name("ledgerDeletionLocks", "ENTRY_$id"), mapOf("workspaceId" to "ws_test", "recordId" to id.toString(),
                "kind" to "ENTRY", "actorUid" to actor, "operationId" to "forged-guard", "actionTime" to now,
                "jobId" to id.toString(), "sourceCreatedAt" to source.createdAt))
            val result = request("v1/$root:commit", body = JSONObject().put("writes", JSONArray().put(guard)), token = jwt(actor))
            assertEquals(result.second, 403, result.first)
        }
        assertFalse(exists("ledgerDeletionLocks", "ENTRY_$id")); assertTrue(exists("entries", id))
    }

    @Test fun explicitOwnerDeletionSupportsLegacyEmbeddedReceiptWithoutACloudFunction() = runBlocking {
        val source = job(paid = 100.0)
        seed(name("entries", id) to source.toFirestoreMap())
        service("owner").execute(op(source))
        assertFalse(exists("entries", id)); assertTrue(exists("entryDeletionProofs", id))
    }

    @Test fun changedSourceRollsBackWithoutDeletingAnyFinancialRecord() = runBlocking {
        val source = job(); seedCurrent(source)
        seed(name("entries", id) to (source.copy(totalAmount = 200.0, pendingAmount = 160.0).toFirestoreMap() +
            mapOf("protocolVersion" to 2, "authorizationTimeStatus" to "CLIENT_ORIGINAL_V2")))
        try { service("owner").execute(op(source)); fail("Changed source must require review") }
        catch (_: IllegalArgumentException) { }
        assertTrue(exists("entries", id)); assertTrue(exists("collections", 31)); assertTrue(exists("expenses", 32))
    }

    @Test fun rawDeletesWithoutMatchingAuditAreDeniedEvenForOwner() {
        val source = job(); seedCurrent(source)
        for ((collection, recordId) in listOf("entries" to id, "collections" to 31L, "customers" to customerId)) {
            val result = request("v1/$root:commit", body = JSONObject().put("writes", JSONArray().put(
                JSONObject().put("delete", name(collection, recordId)))), token = jwt("owner"))
            assertEquals(result.second, 403, result.first)
            assertTrue(exists(collection, recordId))
        }
    }

    @Test fun aPartnerCannotForgeAnAuditToDeleteAnOwnersEntry() {
        val source = job(); seedCurrent(source)
        val proof = mapOf("workspaceId" to "ws_test", "recordId" to id.toString(), "actorUid" to "partner",
            "operationId" to "forged-delete", "actionTime" to now, "protocolVersion" to 2,
            "sourceCreatedAt" to source.createdAt, "customerId" to customerId, "sourceCreatorUid" to "owner",
            "sourceBeneficiaryUid" to "partner", "totalAmount" to 100.0, "amountReceived" to 40.0, "pendingAmount" to 60.0)
        val writes = JSONArray().put(JSONObject().put("delete", name("entries", id)))
            .put(update(name("entryDeletionProofs", id), proof))
        val result = request("v1/$root:commit", body = JSONObject().put("writes", writes), token = jwt("partner"))
        assertEquals(result.second, 403, result.first)
        assertTrue(exists("entries", id)); assertTrue(exists("collections", 31)); assertFalse(exists("entryDeletionProofs", id))
    }

    @Test fun evenABypassedAppCannotDeleteAPartnerJobWhichContainsAnOwnersReceipt() {
        val source = job("partner"); seedCurrent(source, "owner", "partner")
        val proofWrite = update(name("entryDeletionProofs", id), mapOf("workspaceId" to "ws_test", "recordId" to id.toString(),
            "actorUid" to "partner", "operationId" to "forged-delete", "actionTime" to now, "protocolVersion" to 2,
            "sourceCreatedAt" to source.createdAt, "customerId" to customerId, "sourceCreatorUid" to "partner",
            "sourceBeneficiaryUid" to "partner", "totalAmount" to 100.0, "amountReceived" to 40.0, "pendingAmount" to 60.0))
        proofWrite.getJSONObject("update").getJSONObject("fields").put("collectionIds", JSONObject().put("arrayValue",
            JSONObject().put("values", JSONArray().put(typed("31")))))
        val result = request("v1/$root:commit", body = JSONObject().put("writes", JSONArray()
            .put(JSONObject().put("delete", name("entries", id))).put(proofWrite)), token = jwt("partner"))
        assertEquals(result.second, 403, result.first)
        assertTrue(exists("entries", id)); assertTrue(exists("collections", 31)); assertFalse(exists("entryDeletionProofs", id))
    }

    @Test fun partnerCanDeleteMultipleOwnReceiptsWithTheSourceAtomically() = runBlocking {
        val source = job("partner"); seedCurrent(source, "partner", "partner")
        seed(name("collections", 31) to (PaymentEntity(id = 31, workspaceId = "ws_test", jobEntryId = id,
            customerId = customerId, amount = 10.0, collectedByUid = "partner", createdAt = source.createdAt,
            allocationsJson = """[{"jobId":"$id","amount":10}]""").toFirestoreMap() + mapOf("authorizationTimeStatus" to "CLIENT_ORIGINAL_V2")),
            name("collections", 33) to (PaymentEntity(id = 33, workspaceId = "ws_test", jobEntryId = id,
                customerId = customerId, amount = 30.0, collectedByUid = "partner", createdAt = source.createdAt,
                allocationsJson = """[{"jobId":"$id","amount":30}]""").toFirestoreMap() + mapOf("authorizationTimeStatus" to "CLIENT_ORIGINAL_V2")))
        service("partner").execute(op(source, "partner"))
        assertFalse(exists("entries", id)); assertFalse(exists("collections", 31)); assertFalse(exists("collections", 33))
    }

    @Test fun fiveReceiptBoundaryWorksForAPartnerEntryRecordedForOwner() = runBlocking {
        val source = job("partner", 50.0).copy(entryForUid = "owner", financialAttributionUid = "owner", operatorName = "Owner")
        seedCurrent(source, "partner", "partner")
        for (receiptId in 31L..35L) seed(name("collections", receiptId) to (PaymentEntity(id = receiptId,
            workspaceId = "ws_test", jobEntryId = id, customerId = customerId, amount = 10.0,
            collectedByUid = "partner", createdAt = source.createdAt,
            allocationsJson = """[{"jobId":"$id","amount":10}]""").toFirestoreMap() +
                mapOf("authorizationTimeStatus" to "CLIENT_ORIGINAL_V2")))
        service("partner").execute(op(source, "partner"))
        assertFalse(exists("entries", id)); assertFalse(exists("expenses", 32))
        for (receiptId in 31L..35L) assertFalse(exists("collections", receiptId))
    }

    @Test fun deletionAuditsCannotBeAlteredAndAnOldUploadCannotRecreateTheDeletedId() = runBlocking {
        val source = job(paid = 0.0)
        seed(name("entries", id) to source.toFirestoreMap())
        service("owner").execute(op(source))
        val proofResponse = request("v1/${name("entryDeletionProofs", id)}", "GET")
        val proof = JSONObject(proofResponse.second)
        proof.getJSONObject("fields").put("actorUid", typed("partner"))
        val result = request("v1/$root:commit", body = JSONObject().put("writes", JSONArray().put(
            JSONObject().put("update", JSONObject().put("name", proof.getString("name")).put("fields", proof.getJSONObject("fields"))))), token = jwt("owner"))
        assertEquals(result.second, 403, result.first)
        seed(name("customers", customerId) to mapOf("id" to customerId, "workspaceId" to "ws_test", "isDeleted" to false, "createdAt" to source.createdAt))
        fun replay(recordId: Long): JSONObject {
            val write = update(name("entries", recordId), source.copy(id = recordId).toFirestoreMap() + mapOf(
                "protocolVersion" to 2, "operationType" to "UPSERT", "actorUid" to "owner", "operationId" to "late-old-upload",
                "actionTime" to now, "isDeleted" to false, "authorizationTimeStatus" to "CLIENT_ORIGINAL_V2"))
            write.getJSONObject("update").getJSONObject("fields").apply {
                put("dependencies", JSONObject().put("arrayValue", JSONObject().put("values", JSONArray())))
                put("authorizationCreatedAt", JSONObject().put("timestampValue", java.time.Instant.ofEpochMilli(source.createdAt).toString()))
            }
            return write.put("updateTransforms", JSONArray().put(JSONObject().put("fieldPath", "serverReceivedAt").put("setToServerValue", "REQUEST_TIME")))
        }
        val control = request("v1/$root:commit", body = JSONObject().put("writes", JSONArray().put(replay(id + 1))), token = jwt("owner"))
        assertEquals(control.second, 200, control.first)
        val denied = request("v1/$root:commit", body = JSONObject().put("writes", JSONArray().put(replay(id))), token = jwt("owner"))
        assertEquals(denied.second, 403, denied.first)
        assertFalse(exists("entries", id))
    }

    @Test fun aConcurrentNewJobCannotLoseItsCustomerDuringDeletion() = runBlocking {
        val source = job(paid = 0.0)
        seed(name("entries", id) to source.toFirestoreMap())
        val command = op(source)
        val real = FirebaseLedgerDeletion.HttpTransport("demo-aidhunt-v16", origin, jwt("owner")) {}
        var injected = false
        var creationSucceeded = false
        val transport = object : FirebaseLedgerDeletion.Transport {
            override val documentRoot = root
            override suspend fun prepare(op: PendingOperationEntity) = real.prepare(op)
            override fun requireSession() = real.requireSession()
            override suspend fun request(method: String, body: JSONObject): String {
                if (method == "commit" && !injected) {
                    injected = true
                    val attempt = this@FirebaseOnlyDeletionEmulatorTest.request("v1/$root:commit", body = JSONObject().put("writes", JSONArray().put(
                        newJobWrite(source, id + 1))), token = jwt("owner"))
                    assertEquals(attempt.second, 403, attempt.first)
                    creationSucceeded = attempt.first == 200
                }
                return real.request(method, body)
            }
        }
        FirebaseLedgerDeletion(transport).execute(command)
        assertFalse(exists("entries", id))
        if (creationSucceeded) {
            assertTrue(exists("entries", id + 1)); assertTrue(exists("customers", customerId))
        } else assertFalse(exists("entries", id + 1)) // Firestore refused the contending insertion.
    }

    @Test fun editRacingGuardAcquisitionIsRetainedAndNoIamRpcIsSent() = runBlocking {
        val source = job(paid = 0.0)
        seed(name("entries", id) to source.toFirestoreMap())
        val command = op(source)
        val real = FirebaseLedgerDeletion.HttpTransport("demo-aidhunt-v16", origin, jwt("owner")) {}
        val methods = mutableListOf<String>()
        var injected = false
        val wire = object : FirebaseLedgerDeletion.Transport {
            override val documentRoot = root
            override fun requireSession() = real.requireSession()
            override suspend fun request(method: String, body: JSONObject): String {
                methods.add(method)
                if (method == "commit" && !injected) {
                    injected = true
                    seed(name("entries", id) to source.copy(totalAmount = 200.0, pendingAmount = 200.0).toFirestoreMap())
                }
                return real.request(method, body)
            }
        }
        try {
            FirebaseLedgerDeletion(FirebaseUserDeletionTransport(wire, command)).execute(command)
            fail("An Entry changed before guard acquisition must remain")
        } catch (_: IllegalArgumentException) { }
        assertTrue(exists("entries", id)); assertFalse(exists("entryDeletionProofs", id))
        assertFalse(exists("ledgerDeletionLocks", "ENTRY_$id")); assertFalse(exists("ledgerDeletionLocks", "CUSTOMER_$customerId"))
        assertFalse(methods.contains("beginTransaction")); assertFalse(methods.contains("rollback"))
    }
}
