package com.example

import com.example.data.entity.ExpenseEntity
import com.example.data.entity.ExternalLoanEntity
import com.example.data.entity.ExternalLoanRepaymentEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.firebase.FirestoreValueCodec
import com.example.data.firebase.toFirestoreMap
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant
import java.util.Base64

/** Emulator-backed verification for Stage 3 loans, direct-to-supplier linking, borrowing reservations, and settlements. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class Stage3AtomicOperationEmulatorTest {

    private val origin = "http://127.0.0.1:18180"
    private val project = "demo-aidhunt-v16"
    private val root = "projects/$project/databases/(default)/documents"
    private val ws = "ws_stage3_emu"
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
                .put("fields", JSONObject().apply { values.forEach { (k, v) -> put(k, FirestoreValueCodec.encode(v)) } })
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

    private fun commitWrites(actor: String, vararg writes: JSONObject): Pair<Int, String> {
        val arr = JSONArray()
        writes.forEach { arr.put(it) }
        return request("v1/$root:commit", body = JSONObject().put("writes", arr), token = jwt(actor))
    }

    private fun getDoc(collection: String, id: Any, actor: String = "owner"): JSONObject? {
        val res = request("v1/${docName(collection, id)}", "GET", token = jwt(actor))
        return if (res.first == 200) JSONObject(res.second) else null
    }

    private fun num(doc: JSONObject, key: String): Double {
        val v = doc.getJSONObject("fields").getJSONObject(key)
        return if (v.has("doubleValue")) v.getDouble("doubleValue") else v.getString("integerValue").toDouble()
    }

    private fun seedWorkspace(cash: Double = 1000.0, bank: Double = 500.0) {
        assertEquals(200, request("emulator/v1/projects/$project/databases/(default)/documents", "DELETE").first)
        val t = now
        val writes = arrayOf(
            updateWrite("$root/workspaces/$ws", mapOf("workspaceId" to ws, "ownerUid" to "owner", "syncCapabilities" to mapOf("atomicCashControlV1" to true))),
            updateWrite(docName("members", "owner"), mapOf("uid" to "owner", "workspaceId" to ws, "role" to "OWNER", "status" to "ACTIVE", "displayName" to "Owner")),
            updateWrite(docName("members", "partnerA"), mapOf("uid" to "partnerA", "workspaceId" to ws, "role" to "PARTNER", "status" to "ACTIVE", "displayName" to "Partner A")),
            updateWrite(docName("members", "partnerB"), mapOf("uid" to "partnerB", "workspaceId" to ws, "role" to "PARTNER", "status" to "ACTIVE", "displayName" to "Partner B")),
            updateWrite(
                docName("financialState", "current"),
                mapOf(
                    "workspaceId" to ws, "version" to 1L, "confirmedCash" to cash, "reservedCash" to 0.0,
                    "confirmedBank" to bank, "baselineSource" to "SERVER_COMPUTED",
                    "lastOperationId" to "seed", "lastEntityType" to "BASELINE", "lastEntityId" to "current",
                    "lastActorUid" to "owner", "actionTime" to t - 1000L
                )
            )
        )
        val arr = JSONArray().apply { writes.forEach { put(it) } }
        val res = request("v1/$root:commit", body = JSONObject().put("writes", arr), token = "owner")
        assertEquals(res.second, 200, res.first)
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
    fun externalLoansRepaymentsAndDirectSupplierLinkingExecuteAtomically() {
        seedWorkspace(cash = 1000.0, bank = 500.0)
        val t = now
        val fs1 = getDoc("financialState", "current")!!
        val cashLoan = ExternalLoanEntity(
            id = 701L, workspaceId = ws, lenderName = "Cash Lender",
            principalAmount = 800.0, interestAmount = 80.0, destination = "BUSINESS_CASH",
            loanDateMillis = t, createdAt = t, updatedAt = t, createdByUid = "owner", createdByRole = "OWNER"
        )
        val loanEnvelope = mapOf(
            "protocolVersion" to 2, "operationType" to "UPSERT", "dependencies" to emptyList<String>(),
            "operationId" to "op-loan-701", "actorUid" to "owner", "actionTime" to t, "isDeleted" to false
        )
        val loanCommit = commitWrites(
            "owner",
            updateWrite(docName("externalLoans", 701L), cashLoan.toFirestoreMap() + loanEnvelope, JSONObject().put("exists", false), listOf("serverReceivedAt")),
            updateWrite(
                docName("cashOperations", "op-loan-701"),
                mapOf(
                    "workspaceId" to ws, "operationId" to "op-loan-701", "entityType" to "EXTERNAL_LOAN", "entityId" to "701",
                    "mutationType" to "UPSERT", "actorUid" to "owner", "actionTime" to t,
                    "cashDelta" to 800.0, "reservedDelta" to 0.0, "reservedCashAfter" to 0.0,
                    "bankDelta" to 0.0, "confirmedBankAfter" to 500.0,
                    "expectedVersion" to 1L, "newVersion" to 2L, "confirmedCashAfter" to 1800.0
                ),
                JSONObject().put("exists", false), listOf("createdAt")
            ),
            updateWrite(
                docName("financialState", "current"),
                mapOf(
                    "workspaceId" to ws, "version" to 2L, "confirmedCash" to 1800.0, "reservedCash" to 0.0,
                    "confirmedBank" to 500.0, "baselineSource" to "SERVER_COMPUTED",
                    "lastOperationId" to "op-loan-701", "lastEntityType" to "EXTERNAL_LOAN", "lastEntityId" to "701",
                    "lastActorUid" to "owner", "actionTime" to t
                ),
                JSONObject().put("updateTime", fs1.getString("updateTime")), listOf("updatedAt")
            )
        )
        assertEquals(loanCommit.second, 200, loanCommit.first)

        // Direct-to-supplier loan + 1:1 expense link leaves cash untouched at 1800
        val dtsLoan = ExternalLoanEntity(
            id = 702L, workspaceId = ws, lenderName = "Pump Co",
            principalAmount = 600.0, interestAmount = 50.0, destination = "DIRECT_TO_SUPPLIER",
            loanDateMillis = t, createdAt = t, updatedAt = t, createdByUid = "partnerA", createdByRole = "PARTNER"
        )
        val dtsCreate = commitWrites(
            "partnerA",
            updateWrite(
                docName("externalLoans", 702L),
                dtsLoan.toFirestoreMap() + mapOf(
                    "protocolVersion" to 2, "operationType" to "UPSERT", "dependencies" to emptyList<String>(),
                    "operationId" to "op-loan-702", "actorUid" to "partnerA", "actionTime" to t, "isDeleted" to false
                ),
                JSONObject().put("exists", false), listOf("serverReceivedAt")
            )
        )
        assertEquals(dtsCreate.second, 200, dtsCreate.first)

        val linkedExp = ExpenseEntity(
            id = 801L, workspaceId = ws, expenseType = "Fuel / Diesel", amount = 600.0,
            tractorLabel = "T1", addedByPartner = "Partner A",
            paidBy = "Loan Funded", paidByUid = "", fundingSource = "EXTERNAL_LOAN",
            externalLoanId = 702L, loanFundingMode = "DIRECT_TO_SUPPLIER",
            expenseForUid = "partnerA", financialAttributionUid = "partnerA",
            createdAt = t, dateTimestamp = t, createdByUid = "partnerA", createdByRole = "PARTNER"
        )
        val expWrite = updateWrite(
            docName("expenses", 801L),
            linkedExp.toFirestoreMap() + mapOf(
                "protocolVersion" to 2, "operationType" to "UPSERT", "dependencies" to emptyList<String>(),
                "operationId" to "op-exp-801", "actorUid" to "partnerA", "actionTime" to t, "isDeleted" to false,
                "authorizationTimeStatus" to "CLIENT_ORIGINAL_V2"
            ),
            JSONObject().put("exists", false), listOf("serverReceivedAt")
        )
        expWrite.getJSONObject("update").getJSONObject("fields")
            .put("authorizationCreatedAt", JSONObject().put("timestampValue", Instant.ofEpochMilli(t).toString()))
        val linkCommit = commitWrites(
            "partnerA",
            expWrite,
            updateWrite(
                docName("externalLoans", 702L),
                dtsLoan.copy(linkedExpenseId = 801L).toFirestoreMap() + mapOf(
                    "protocolVersion" to 2, "operationType" to "UPSERT", "dependencies" to emptyList<String>(),
                    "operationId" to "op-exp-801", "actorUid" to "partnerA", "actionTime" to t, "isDeleted" to false
                )
            )
        )
        assertEquals(linkCommit.second, 200, linkCommit.first)

        // Bank repayment reduces confirmedBank while keeping confirmedCash at 1800
        val fs2 = getDoc("financialState", "current")!!
        val rep701 = ExternalLoanRepaymentEntity(
            id = 901L, workspaceId = ws, loanId = 701L, principalPaid = 400.0, interestPaid = 50.0,
            source = "BUSINESS_BANK", repaidAt = t, createdAt = t, createdByUid = "owner", createdByRole = "OWNER"
        )
        val bankRepayCommit = commitWrites(
            "owner",
            updateWrite(
                docName("externalLoanRepayments", 901L),
                rep701.toFirestoreMap() + mapOf(
                    "protocolVersion" to 2, "operationType" to "UPSERT", "dependencies" to emptyList<String>(),
                    "operationId" to "op-lrep-901", "actorUid" to "owner", "actionTime" to t, "isDeleted" to false
                ),
                JSONObject().put("exists", false), listOf("serverReceivedAt")
            ),
            updateWrite(
                docName("externalLoans", 701L),
                cashLoan.copy(totalRepaidPrincipal = 400.0, totalRepaidInterest = 50.0, status = "ACTIVE").toFirestoreMap() + mapOf(
                    "protocolVersion" to 2, "operationType" to "UPSERT", "dependencies" to emptyList<String>(),
                    "operationId" to "op-lrep-901", "actorUid" to "owner", "actionTime" to t, "isDeleted" to false
                )
            ),
            updateWrite(
                docName("cashOperations", "op-lrep-901"),
                mapOf(
                    "workspaceId" to ws, "operationId" to "op-lrep-901", "entityType" to "EXTERNAL_LOAN_REPAYMENT", "entityId" to "901",
                    "mutationType" to "UPSERT", "actorUid" to "owner", "actionTime" to t,
                    "cashDelta" to 0.0, "reservedDelta" to 0.0, "reservedCashAfter" to 0.0,
                    "bankDelta" to -450.0, "confirmedBankAfter" to 50.0,
                    "expectedVersion" to 2L, "newVersion" to 3L, "confirmedCashAfter" to 1800.0
                ),
                JSONObject().put("exists", false), listOf("createdAt")
            ),
            updateWrite(
                docName("financialState", "current"),
                mapOf(
                    "workspaceId" to ws, "version" to 3L, "confirmedCash" to 1800.0, "reservedCash" to 0.0,
                    "confirmedBank" to 50.0, "baselineSource" to "SERVER_COMPUTED",
                    "lastOperationId" to "op-lrep-901", "lastEntityType" to "EXTERNAL_LOAN_REPAYMENT", "lastEntityId" to "901",
                    "lastActorUid" to "owner", "actionTime" to t
                ),
                JSONObject().put("updateTime", fs2.getString("updateTime")), listOf("updatedAt")
            )
        )
        assertEquals(bankRepayCommit.second, 200, bankRepayCommit.first)

        val fsFinal = getDoc("financialState", "current")!!
        assertEquals(1800.0, num(fsFinal, "confirmedCash"), 0.001)
        assertEquals(50.0, num(fsFinal, "confirmedBank"), 0.001)
    }

    @Test
    fun businessBorrowingConcurrencyAllowsStrictlyOneAndPreventsOverdraw() {
        seedWorkspace(cash = 1000.0, bank = 0.0)
        val t = now
        val fs1 = getDoc("financialState", "current")!!
        val w = WithdrawalEntity(
            id = 1201L, workspaceId = ws, partnerName = "Partner B", amount = 700.0, category = "Withdrawal",
            timestamp = t, createdAt = t, targetPartnerUid = "partnerB", createdByUid = "partnerB", createdByRole = "PARTNER"
        )
        val wWrite = updateWrite(
            docName("withdrawals", 1201L),
            w.toFirestoreMap() + mapOf(
                "protocolVersion" to 2, "operationType" to "UPSERT", "dependencies" to emptyList<String>(),
                "operationId" to "op-w-conc", "actorUid" to "partnerB", "actionTime" to t, "isDeleted" to false,
                "authorizationTimeStatus" to "CLIENT_ORIGINAL_V2"
            ),
            JSONObject().put("exists", false), listOf("serverReceivedAt")
        )
        wWrite.getJSONObject("update").getJSONObject("fields")
            .put("authorizationCreatedAt", JSONObject().put("timestampValue", Instant.ofEpochMilli(t).toString()))
        val commitB = commitWrites(
            "partnerB",
            wWrite,
            updateWrite(
                docName("cashOperations", "op-w-conc"),
                mapOf(
                    "workspaceId" to ws, "operationId" to "op-w-conc", "entityType" to "WITHDRAWAL", "entityId" to "1201",
                    "mutationType" to "UPSERT", "actorUid" to "partnerB", "actionTime" to t,
                    "cashDelta" to -700.0, "reservedDelta" to 0.0, "reservedCashAfter" to 0.0,
                    "expectedVersion" to 1L, "newVersion" to 2L, "confirmedCashAfter" to 300.0
                ),
                JSONObject().put("exists", false), listOf("createdAt")
            ),
            updateWrite(
                docName("financialState", "current"),
                mapOf(
                    "workspaceId" to ws, "version" to 2L, "confirmedCash" to 300.0, "reservedCash" to 0.0,
                    "confirmedBank" to 0.0, "baselineSource" to "SERVER_COMPUTED",
                    "lastOperationId" to "op-w-conc", "lastEntityType" to "WITHDRAWAL", "lastEntityId" to "1201",
                    "lastActorUid" to "partnerB", "actionTime" to t
                ),
                JSONObject().put("updateTime", fs1.getString("updateTime")), listOf("updatedAt")
            )
        )
        assertEquals(commitB.second, 200, commitB.first)

        // Concurrent Partner A withdrawal of 700 using stale updateTime MUST fail!
        val wA = w.copy(id = 1202L, partnerName = "Partner A", targetPartnerUid = "partnerA", createdByUid = "partnerA")
        val wAWrite = updateWrite(
            docName("withdrawals", 1202L),
            wA.toFirestoreMap() + mapOf(
                "protocolVersion" to 2, "operationType" to "UPSERT", "dependencies" to emptyList<String>(),
                "operationId" to "op-w-stale", "actorUid" to "partnerA", "actionTime" to t, "isDeleted" to false,
                "authorizationTimeStatus" to "CLIENT_ORIGINAL_V2"
            ),
            JSONObject().put("exists", false), listOf("serverReceivedAt")
        )
        wAWrite.getJSONObject("update").getJSONObject("fields")
            .put("authorizationCreatedAt", JSONObject().put("timestampValue", Instant.ofEpochMilli(t).toString()))
        val commitStale = commitWrites(
            "partnerA",
            wAWrite,
            updateWrite(
                docName("cashOperations", "op-w-stale"),
                mapOf(
                    "workspaceId" to ws, "operationId" to "op-w-stale", "entityType" to "WITHDRAWAL", "entityId" to "1202",
                    "mutationType" to "UPSERT", "actorUid" to "partnerA", "actionTime" to t,
                    "cashDelta" to -700.0, "reservedDelta" to 0.0, "reservedCashAfter" to 0.0,
                    "expectedVersion" to 1L, "newVersion" to 2L, "confirmedCashAfter" to 300.0
                ),
                JSONObject().put("exists", false), listOf("createdAt")
            ),
            updateWrite(
                docName("financialState", "current"),
                mapOf(
                    "workspaceId" to ws, "version" to 2L, "confirmedCash" to 300.0, "reservedCash" to 0.0,
                    "confirmedBank" to 0.0, "baselineSource" to "SERVER_COMPUTED",
                    "lastOperationId" to "op-w-stale", "lastEntityType" to "WITHDRAWAL", "lastEntityId" to "1202",
                    "lastActorUid" to "partnerA", "actionTime" to t
                ),
                JSONObject().put("updateTime", fs1.getString("updateTime")), listOf("updatedAt")
            )
        )
        assertNotEquals(200, commitStale.first)
        assertFalse(request("v1/${docName("withdrawals", 1202L)}", "GET", token = jwt("owner")).first == 200)
    }
}
