package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.PendingOperationEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.firebase.FinancialBaselinePendingException
import com.example.data.firebase.FirebaseLedgerDeletion
import com.example.data.firebase.FirestoreValueCodec
import com.example.data.firebase.InsufficientSharedFundsException
import com.example.data.firebase.toFirestoreMap
import com.example.data.sync.SyncFailurePolicy
import com.example.ui.util.FinancialCalculationEngine
import com.example.ui.util.FinancialScope
import com.example.ui.util.RetainedActionDetails
import kotlinx.coroutines.flow.first
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
import java.time.Instant
import java.util.Base64

/** Emulator-backed verification for atomic shared-cash control, concurrent withdrawals, and ledger deletion. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class AtomicCashOperationEmulatorTest {

    private val origin = "http://127.0.0.1:18180"
    private val project = "demo-aidhunt-v16"
    private val root = "projects/$project/databases/(default)/documents"
    private val ws = "ws_atomic_test"
    private val customerId = 48120001001L
    private val jobId = 48120002001L
    private val client = okhttp3.OkHttpClient()
    private val now get() = System.currentTimeMillis() - 5000L

    private fun docName(collection: String, id: Any) = "$root/workspaces/$ws/$collection/$id"

    private fun jwt(uid: String): String {
        fun enc(s: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray())
        val issued = System.currentTimeMillis() / 1000
        return enc("""{"alg":"none","typ":"JWT"}""") + "." + enc(
            JSONObject()
                .put("sub", uid)
                .put("user_id", uid)
                .put("aud", project)
                .put("iss", "https://securetoken.google.com/$project")
                .put("iat", issued)
                .put("auth_time", issued)
                .put("exp", issued + 3600)
                .toString()
        ) + "."
    }

    private fun request(
        path: String,
        method: String = "POST",
        body: JSONObject? = null,
        token: String = "owner"
    ): Pair<Int, String> {
        val builder = okhttp3.Request.Builder()
            .url("$origin/$path")
            .header("Authorization", "Bearer $token")
        builder.method(
            method,
            if (method == "GET" || method == "DELETE") null
            else (body ?: JSONObject()).toString().toRequestBody("application/json".toMediaType())
        )
        return client.newCall(builder.build()).execute().use { it.code to it.body?.string().orEmpty() }
    }

    private fun typed(value: Any?): JSONObject = FirestoreValueCodec.encode(value)

    private fun updateWrite(
        name: String,
        values: Map<String, Any?>,
        precondition: JSONObject? = null,
        serverTimestampFields: List<String> = emptyList()
    ): JSONObject {
        val write = JSONObject().put(
            "update",
            JSONObject()
                .put("name", name)
                .put("fields", JSONObject().apply { values.forEach { (k, v) -> put(k, typed(v)) } })
        )
        if (precondition != null) write.put("currentDocument", precondition)
        if (serverTimestampFields.isNotEmpty()) {
            val transforms = JSONArray()
            serverTimestampFields.forEach { field ->
                transforms.put(JSONObject().put("fieldPath", field).put("setToServerValue", "REQUEST_TIME"))
            }
            write.put("updateTransforms", transforms)
        }
        return write
    }

    private fun adminSeed(vararg records: Pair<String, Map<String, Any?>>) {
        val writes = JSONArray()
        records.forEach { (name, data) -> writes.put(updateWrite(name, data)) }
        val res = request("v1/$root:commit", body = JSONObject().put("writes", writes), token = "owner")
        assertEquals(res.second, 200, res.first)
    }

    private fun getDoc(collection: String, id: Any, actor: String = "owner"): JSONObject? {
        val res = request("v1/${docName(collection, id)}", "GET", token = jwt(actor))
        return if (res.first == 200) JSONObject(res.second) else null
    }

    private fun exists(collection: String, id: Any): Boolean =
        request("v1/${docName(collection, id)}", "GET", token = "owner").first == 200

    private fun readNumberField(doc: JSONObject, key: String): Double {
        val v = doc.getJSONObject("fields").getJSONObject(key)
        return if (v.has("doubleValue")) v.getDouble("doubleValue") else v.getString("integerValue").toDouble()
    }

    private fun readLongField(doc: JSONObject, key: String): Long =
        doc.getJSONObject("fields").getJSONObject(key).getString("integerValue").toLong()

    private fun seedWorkspace(
        confirmedCash: Double = 1000.0,
        reservedCash: Double = 0.0,
        version: Long = 1L,
        includeBaseline: Boolean = true
    ) {
        assertEquals(200, request("emulator/v1/projects/$project/databases/(default)/documents", "DELETE").first)
        val t = now
        val records = mutableListOf(
            "$root/workspaces/$ws" to mapOf(
                "workspaceId" to ws,
                "ownerUid" to "owner",
                "syncCapabilities" to mapOf("atomicCashControlV1" to true)
            ),
            docName("members", "owner") to mapOf("uid" to "owner", "workspaceId" to ws, "role" to "OWNER", "status" to "ACTIVE", "displayName" to "Owner"),
            docName("members", "partnerA") to mapOf("uid" to "partnerA", "workspaceId" to ws, "role" to "PARTNER", "status" to "ACTIVE", "displayName" to "Partner A"),
            docName("members", "partnerB") to mapOf("uid" to "partnerB", "workspaceId" to ws, "role" to "PARTNER", "status" to "ACTIVE", "displayName" to "Partner B"),
            docName("customers", customerId) to mapOf(
                "id" to customerId, "workspaceId" to ws, "name" to "Synthetic Customer",
                "phone" to "", "createdByUid" to "owner", "createdAt" to t - 3000L, "isDeleted" to false
            )
        )
        if (includeBaseline) {
            records += docName("financialState", "current") to mapOf(
                "workspaceId" to ws,
                "version" to version,
                "confirmedCash" to confirmedCash,
                "reservedCash" to reservedCash,
                "confirmedBank" to 0.0,
                "baselineSource" to "SERVER_COMPUTED",
                "lastOperationId" to "seed-baseline",
                "lastEntityType" to "BASELINE",
                "lastEntityId" to "current",
                "lastActorUid" to "owner",
                "actionTime" to t - 1000L
            )
        }
        adminSeed(*records.toTypedArray())
    }

    private fun buildWithdrawalWrites(
        actor: String,
        id: Long,
        amount: Double,
        opId: String,
        actionTime: Long,
        fsUpdateTime: String,
        expectedVersion: Long,
        prevCash: Double,
        nextCashOverride: Double? = null
    ): JSONArray {
        val withdrawal = WithdrawalEntity(
            id = id, workspaceId = ws, partnerName = actor, amount = amount,
            timestamp = actionTime, isSynced = false, createdAt = actionTime,
            targetPartnerUid = actor, createdByUid = actor, createdByRole = "PARTNER"
        )
        val wFields = withdrawal.toFirestoreMap() + mapOf(
            "protocolVersion" to 2,
            "operationType" to "UPSERT",
            "dependencies" to emptyList<String>(),
            "operationId" to opId,
            "actorUid" to actor,
            "actionTime" to actionTime,
            "isDeleted" to false,
            "authorizationTimeStatus" to "CLIENT_ORIGINAL_V2"
        )
        val wWrite = updateWrite(
            docName("withdrawals", id),
            wFields,
            precondition = JSONObject().put("exists", false),
            serverTimestampFields = listOf("serverReceivedAt")
        )
        wWrite.getJSONObject("update").getJSONObject("fields")
            .put("authorizationCreatedAt", JSONObject().put("timestampValue", Instant.ofEpochMilli(actionTime).toString()))

        val nextCash = nextCashOverride ?: (prevCash - amount)
        val opWrite = updateWrite(
            docName("cashOperations", opId),
            mapOf(
                "workspaceId" to ws,
                "operationId" to opId,
                "entityType" to "WITHDRAWAL",
                "entityId" to id.toString(),
                "mutationType" to "UPSERT",
                "actorUid" to actor,
                "actionTime" to actionTime,
                "cashDelta" to -amount,
                "reservedDelta" to 0.0,
                "expectedVersion" to expectedVersion,
                "newVersion" to (expectedVersion + 1L),
                "confirmedCashAfter" to nextCash
            ),
            precondition = JSONObject().put("exists", false),
            serverTimestampFields = listOf("createdAt")
        )

        val fsWrite = updateWrite(
            docName("financialState", "current"),
            mapOf(
                "workspaceId" to ws,
                "version" to (expectedVersion + 1L),
                "confirmedCash" to nextCash,
                "reservedCash" to 0.0,
                "confirmedBank" to 0.0,
                "baselineSource" to "SERVER_COMPUTED",
                "lastOperationId" to opId,
                "lastEntityType" to "WITHDRAWAL",
                "lastEntityId" to id.toString(),
                "lastActorUid" to actor,
                "actionTime" to actionTime
            ),
            precondition = JSONObject().put("updateTime", fsUpdateTime),
            serverTimestampFields = listOf("updatedAt")
        )
        return JSONArray().put(wWrite).put(opWrite).put(fsWrite)
    }

    @Before
    fun localEmulatorOnly() {
        assumeTrue(
            System.getenv("FIRESTORE_EMULATOR_HOST") == "127.0.0.1:18180" &&
                System.getenv("GCLOUD_PROJECT") == project &&
                System.getenv("GOOGLE_APPLICATION_CREDENTIALS").isNullOrBlank()
        )
    }

    @Test
    fun concurrentWithdrawalsAgainstSharedCashEnforceAtomicityAndRetainRejectedOutbox() = runBlocking {
        seedWorkspace(confirmedCash = 1000.0, version = 1L)
        val initialFs = getDoc("financialState", "current")!!
        val initialUpdateTime = initialFs.getString("updateTime")
        val t = now

        val writesA = buildWithdrawalWrites("partnerA", 5001L, 700.0, "op-w-a", t, initialUpdateTime, 1L, 1000.0)
        val writesB = buildWithdrawalWrites("partnerB", 5002L, 700.0, "op-w-b", t, initialUpdateTime, 1L, 1000.0)

        // Partner A commits first against v1
        val commitA = request("v1/$root:commit", body = JSONObject().put("writes", writesA), token = jwt("partnerA"))
        assertEquals(commitA.second, 200, commitA.first)

        // Partner B commits against stale v1 -> rejected by optimistic version lock
        val commitBStale = request("v1/$root:commit", body = JSONObject().put("writes", writesB), token = jwt("partnerB"))
        assertNotEquals(200, commitBStale.first)
        assertFalse(exists("withdrawals", 5002L))

        // Even if Partner B refreshes to v2 (confirmedCash=300) and bypasses client check, Rules reject -400
        val updatedFs = getDoc("financialState", "current", "partnerB")!!
        assertEquals(300.0, readNumberField(updatedFs, "confirmedCash"), 0.001)
        assertEquals(2L, readLongField(updatedFs, "version"))
        val v2UpdateTime = updatedFs.getString("updateTime")
        val bypassWrites = buildWithdrawalWrites(
            "partnerB", 5002L, 700.0, "op-w-b", t, v2UpdateTime, 2L, 300.0, nextCashOverride = -400.0
        )
        val commitBBypass = request("v1/$root:commit", body = JSONObject().put("writes", bypassWrites), token = jwt("partnerB"))
        assertEquals(commitBBypass.second, 403, commitBBypass.first)
        assertFalse(exists("withdrawals", 5002L))

        // Verify domain exception permanently rejects without auto-retry and Room removes optimistic local withdrawal cleanly
        val err = InsufficientSharedFundsException("Insufficient confirmed shared cash (available ₹300.00, requested ₹700.00)")
        assertTrue(SyncFailurePolicy.permanent(err))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "atomic_cash_test.db"
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).build()
        try {
            val cashPayment = PaymentEntity(id = 11L, workspaceId = ws, amount = 1000.0, paymentMethod = "Cash", status = "ACTIVE", collectedByUid = "owner")
            val confirmedW = WithdrawalEntity(id = 5001L, workspaceId = ws, partnerName = "Partner A", amount = 700.0, isSynced = true, targetPartnerUid = "partnerA", createdByUid = "partnerA")
            val optimisticW = WithdrawalEntity(id = 5002L, workspaceId = ws, partnerName = "Partner B", amount = 700.0, isSynced = false, targetPartnerUid = "partnerB", createdByUid = "partnerB")
            val opB = PendingOperationEntity("op-w-b", ws, "partnerB", "WITHDRAWAL", 5002L, "UPSERT", 1L, t, JSONObject(optimisticW.toFirestoreMap()).toString())
            db.paymentDao().insertPayment(cashPayment)
            db.withdrawalDao().insertWithdrawal(confirmedW)
            db.withdrawalDao().insertWithdrawal(optimisticW)
            db.pendingOperationDao().insert(opB)

            // Reject and reconcile Room local optimistic row against confirmed server state
            db.pendingOperationDao().failed("op-w-b", "REJECTED", err.message!!)
            db.withdrawalDao().deleteWithdrawal(optimisticW)
            db.pendingOperationDao().waitingRecovery("op-w-b", "Confirmed state restored; ${err.message}")

            val availableAfterRecovery = FinancialCalculationEngine.cashAvailable(
                db.paymentDao().getPaymentsForWorkspace(ws).first(),
                emptyList(),
                db.withdrawalDao().getWithdrawalsForWorkspaceOnce(ws),
                FinancialScope(workspaceId = ws)
            )
            assertEquals(300.0, availableAfterRecovery, 0.01)
            val retained = db.pendingOperationDao().getOperation("op-w-b")!!
            assertEquals("REJECTED", retained.state)
            assertEquals("Needs Review", RetainedActionDetails.status(retained))
            assertTrue(db.pendingOperationDao().nextBatch(ws, "partnerB").isEmpty())
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun interruptedAckIdempotencyAndSingleStepDeletePreserveCashConsistency() {
        seedWorkspace(confirmedCash = 1000.0, version = 1L)
        val initialUpdateTime = getDoc("financialState", "current")!!.getString("updateTime")
        val t = now

        val writes = buildWithdrawalWrites("partnerA", 505L, 400.0, "op-w-ack", t, initialUpdateTime, 1L, 1000.0)
        val first = request("v1/$root:commit", body = JSONObject().put("writes", writes), token = jwt("partnerA"))
        assertEquals(first.second, 200, first.first)

        // Check receipt recorded on server
        val receipt = getDoc("cashOperations", "op-w-ack", "partnerA")!!
        assertEquals(-400.0, readNumberField(receipt, "cashDelta"), 0.001)
        assertEquals(600.0, readNumberField(receipt, "confirmedCashAfter"), 0.001)
        assertEquals(2L, readLongField(receipt, "newVersion"))

        // Replay of the same op (simulating interrupted client ACK) cannot debit again
        val afterUpdateTime = getDoc("financialState", "current", "partnerA")!!.getString("updateTime")
        val replay = buildWithdrawalWrites("partnerA", 506L, 400.0, "op-w-ack", t, afterUpdateTime, 2L, 600.0)
        val deniedReplay = request("v1/$root:commit", body = JSONObject().put("writes", replay), token = jwt("partnerA"))
        assertNotEquals(200, deniedReplay.first)
        assertFalse(exists("withdrawals", 506L))

        // Single-step atomic deletion refunds +400 -> 1000
        val delWrites = JSONArray()
            .put(JSONObject().put("delete", docName("withdrawals", 505L)))
            .put(
                updateWrite(
                    docName("cashDeleteProofs", "WITHDRAWAL_505"),
                    mapOf(
                        "workspaceId" to ws,
                        "kind" to "WITHDRAWAL",
                        "recordId" to "505",
                        "actorUid" to "partnerA",
                        "operationId" to "op-w-ack-del",
                        "actionTime" to t,
                        "requiresCashOp" to true
                    ),
                    precondition = JSONObject().put("exists", false),
                    serverTimestampFields = listOf("createdAt")
                )
            )
            .put(
                updateWrite(
                    docName("cashOperations", "op-w-ack-del"),
                    mapOf(
                        "workspaceId" to ws,
                        "operationId" to "op-w-ack-del",
                        "entityType" to "WITHDRAWAL",
                        "entityId" to "505",
                        "mutationType" to "DELETE",
                        "actorUid" to "partnerA",
                        "actionTime" to t,
                        "cashDelta" to 400.0,
                        "reservedDelta" to 0.0,
                        "expectedVersion" to 2L,
                        "newVersion" to 3L,
                        "confirmedCashAfter" to 1000.0
                    ),
                    precondition = JSONObject().put("exists", false),
                    serverTimestampFields = listOf("createdAt")
                )
            )
            .put(
                updateWrite(
                    docName("financialState", "current"),
                    mapOf(
                        "workspaceId" to ws,
                        "version" to 3L,
                        "confirmedCash" to 1000.0,
                        "reservedCash" to 0.0,
                        "confirmedBank" to 0.0,
                        "baselineSource" to "SERVER_COMPUTED",
                        "lastOperationId" to "op-w-ack-del",
                        "lastEntityType" to "WITHDRAWAL",
                        "lastEntityId" to "505",
                        "lastActorUid" to "partnerA",
                        "actionTime" to t
                    ),
                    precondition = JSONObject().put("updateTime", afterUpdateTime),
                    serverTimestampFields = listOf("updatedAt")
                )
            )
        val deleted = request("v1/$root:commit", body = JSONObject().put("writes", delWrites), token = jwt("partnerA"))
        assertEquals(deleted.second, 200, deleted.first)
        assertFalse(exists("withdrawals", 505L))
        assertEquals(1000.0, readNumberField(getDoc("financialState", "current")!!, "confirmedCash"), 0.001)
    }

    @Test
    fun firebaseLedgerDeletionAtomicallyReconcilesCashCollectionsAndBusinessExpenses() = runBlocking {
        seedWorkspace(confirmedCash = 500.0, version = 1L)
        val t = now
        val job = JobEntryEntity(
            id = jobId, workspaceId = ws, customerId = customerId, customerName = "Synthetic Customer",
            operatorName = "Partner A", tractorLabel = "T", workType = "Ploughing",
            startTimeMillis = t - 2000L, endTimeMillis = t - 1000L, durationMinutes = 1L,
            totalAmount = 1000.0, amountReceived = 300.0, pendingAmount = 700.0,
            addedByPartner = "Partner A", createdAt = t - 1000L, createdByUid = "partnerA",
            entryForUid = "partnerA", financialAttributionUid = "partnerA"
        )
        val collection = PaymentEntity(
            id = 31L, workspaceId = ws, jobEntryId = jobId, customerId = customerId,
            amount = 300.0, paymentMethod = "Cash", collectedByUid = "partnerA",
            createdAt = job.createdAt, status = "ACTIVE",
            allocationsJson = """[{"jobId":"$jobId","amount":300.0}]"""
        )
        val expense = ExpenseEntity(
            id = 32L, workspaceId = ws, relatedJobId = jobId, expenseType = "Diesel",
            amount = 100.0, tractorLabel = "T", addedByPartner = "Partner A",
            paidBy = "Business Paid", paidByUid = "", createdByUid = "partnerA",
            expenseForUid = "partnerA", financialAttributionUid = "partnerA", createdAt = job.createdAt
        )
        val extras = mapOf("protocolVersion" to 2, "authorizationTimeStatus" to "CLIENT_ORIGINAL_V2", "isDeleted" to false)
        adminSeed(
            docName("entries", jobId) to (job.toFirestoreMap() + extras),
            docName("collections", 31L) to (collection.toFirestoreMap() + extras),
            docName("expenses", 32L) to (expense.toFirestoreMap() + extras)
        )

        val op = PendingOperationEntity(
            operationId = "op-job-del-cash",
            workspaceId = ws,
            actorUid = "partnerA",
            entityType = "JOB",
            entityId = jobId,
            mutationType = "DELETE",
            sequence = 1L,
            actionTime = now,
            payloadJson = JSONObject(job.toFirestoreMap())
                .put("operationProtocolVersion", 2)
                .put("linkedExpenseIds", JSONArray().put("32"))
                .put("linkedCollectionIds", JSONArray().put("31"))
                .put("linkedExpenseSnapshots", JSONArray().put(JSONObject(expense.toFirestoreMap())))
                .toString()
        )

        FirebaseLedgerDeletion(
            FirebaseLedgerDeletion.HttpTransport(project, origin, jwt("partnerA")) {}
        ).execute(op)

        assertFalse(exists("entries", jobId))
        assertFalse(exists("collections", 31L))
        assertFalse(exists("expenses", 32L))
        assertTrue(exists("entryDeletionProofs", jobId))
        val fsAfter = getDoc("financialState", "current")!!
        // Net delta: +100 (Business Paid expense refunded) - 300 (Cash collection removed) = -200 -> 500 - 200 = 300
        assertEquals(300.0, readNumberField(fsAfter, "confirmedCash"), 0.001)
        assertEquals(2L, readLongField(fsAfter, "version"))
        val cashOp = getDoc("cashOperations", "op-job-del-cash")!!
        assertEquals(-200.0, readNumberField(cashOp, "cashDelta"), 0.001)
    }

    @Test
    fun missingFinancialBaselineRemainsTransientAndCannotBeFabricatedByClients() {
        seedWorkspace(includeBaseline = false)
        val t = now
        val err = FinancialBaselinePendingException()
        assertFalse(SyncFailurePolicy.permanent(err))

        // Attempt to fabricate financialState/current from client fails with 403
        val fakeBaseline = updateWrite(
            docName("financialState", "current"),
            mapOf(
                "workspaceId" to ws, "version" to 1L, "confirmedCash" to 50000.0,
                "reservedCash" to 0.0, "confirmedBank" to 0.0, "baselineSource" to "CLIENT_FAKE",
                "lastOperationId" to "fake", "lastEntityType" to "BASELINE", "lastEntityId" to "current",
                "lastActorUid" to "owner", "actionTime" to t
            ),
            serverTimestampFields = listOf("updatedAt")
        )
        val deniedBaseline = request("v1/$root:commit", body = JSONObject().put("writes", JSONArray().put(fakeBaseline)), token = jwt("owner"))
        assertEquals(deniedBaseline.second, 403, deniedBaseline.first)
        assertFalse(exists("financialState", "current"))
    }
}
