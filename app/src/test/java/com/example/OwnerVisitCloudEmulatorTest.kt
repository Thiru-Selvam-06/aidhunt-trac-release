package com.example

import com.example.data.entity.AppNotificationEntity.Companion as Event
import com.example.data.firebase.FirestoreValueCodec
import com.example.data.firebase.OwnerVisitCloud
import com.example.data.sync.OwnerVisitKey
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class OwnerVisitCloudEmulatorTest {
    private val client = okhttp3.OkHttpClient()
    private val project = "demo-aidhunt-v16"
    private val root = "projects/$project/databases/(default)/documents"
    private val ws = "owner-visit-" + UUID.randomUUID()
    private val owner = "original-owner"
    private val partner = "active-partner"
    private val now = System.currentTimeMillis() - 4000L

    private fun jwt(uid: String): String {
        fun enc(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())
        val issued = System.currentTimeMillis() / 1000
        return enc("""{"alg":"none","typ":"JWT"}""") + "." + enc(JSONObject()
            .put("sub", uid).put("user_id", uid).put("aud", project)
            .put("iss", "https://securetoken.google.com/$project").put("iat", issued)
            .put("auth_time", issued).put("exp", issued + 3600).toString()) + "."
    }

    private fun rpc(path: String, method: String, body: JSONObject?, credential: String): Pair<Int, String> {
        val request = okhttp3.Request.Builder().url("http://127.0.0.1:18180/v1/$path")
            .header("Authorization", "Bearer $credential")
            .method(method, body?.toString()?.toRequestBody("application/json".toMediaType())).build()
        return client.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
    }

    private fun encoded(fields: Map<String, Any?>) = JSONObject().apply {
        fields.forEach { (key, value) -> put(key, FirestoreValueCodec.encode(value)) }
    }

    private fun seed(path: String, fields: Map<String, Any?>) {
        val (status, details) = rpc("$root/$path", "PATCH", JSONObject().put("fields", encoded(fields)), "owner")
        assertEquals(details, 200, status)
    }

    private fun ordinary(id: Long, op: String, uid: String = partner) = mapOf(
        "workspaceId" to ws, "protocolVersion" to 2L, "operationType" to "UPSERT", "isDeleted" to false,
        "id" to id, "actorUid" to uid, "createdByUid" to uid, "actionTime" to now, "operationId" to op,
        "authorizationTimeStatus" to "CLIENT_ORIGINAL_V2"
    )

    private fun confirmExpense(id: Long) {
        val fields = encoded(ordinary(id, "expense-confirmed") + mapOf(
            "dependencies" to emptyList<String>(), "createdAt" to now, "createdByRole" to "PARTNER",
            "expenseForUid" to partner, "financialAttributionUid" to partner, "expenseType" to "Synthetic diesel",
            "paidBy" to "I Paid", "paidByUid" to partner, "amount" to 12L, "relatedJobId" to 0L
        ))
        val instant = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        fields.put("authorizationCreatedAt", JSONObject().put("timestampValue", instant.format(java.util.Date(now))))
        val update = JSONObject().put("name", "$root/workspaces/" + ws + "/expenses/" + id).put("fields", fields)
        val transforms = JSONArray().put(JSONObject().put("fieldPath", "serverReceivedAt").put("setToServerValue", "REQUEST_TIME"))
        val write = JSONObject().put("update", update).put("updateTransforms", transforms)
        val (code, body) = rpc("$root:commit", "POST", JSONObject().put("writes", JSONArray().put(write)), jwt(partner))
        assertEquals(body, 200, code)
    }

    @Test fun ownerReadsAuthorizedLedgerAndMissingNotificationCollectionWithoutTrustingClientClaims() = runBlocking {
        val ping = runCatching { rpc("$root/workspaces/$ws", "GET", null, "owner") }.getOrNull()
        assumeTrue("Requires local Firestore Emulator at 127.0.0.1:18180", ping != null)
        seed("workspaces/$ws", mapOf("workspaceId" to ws, "ownerUid" to owner))
        for ((uid, role) in listOf(owner to "OWNER", partner to "PARTNER", "second-partner" to "PARTNER")) {
            seed("workspaces/$ws/members/$uid", mapOf("workspaceId" to ws, "uid" to uid, "role" to role,
                "status" to "ACTIVE", "displayName" to uid))
        }
        seed("workspaces/$ws/entries/1", ordinary(1L, "job") + mapOf("isHistorical" to false, "updatedAt" to now))
        seed("workspaces/$ws/entries/2", ordinary(2L, "owner-action", owner) + mapOf("isHistorical" to false, "updatedAt" to now))
        seed("workspaces/$ws/collections/3", ordinary(3L, "late-payment", "second-partner") +
            mapOf("status" to "ACTIVE", "collectedByUid" to "second-partner", "customerName" to "Synthetic customer"))
        seed("workspaces/$ws/withdrawals/4", ordinary(4L, "ordinary-withdrawal") + mapOf("targetPartnerUid" to partner))
        seed("workspaces/$ws/notifications/fabricated", ordinary(99L, "fabricated"))
        confirmExpense(5L)
        suspend fun fetch(key: OwnerVisitKey, actor: String) = OwnerVisitCloud.load(key, now - 500L, project) { method, data ->
            val (status, body) = rpc(method, "POST", data, jwt(actor))
            check(status == 200) { "$status $body" }
            JSONArray(body)
        }
        val first = fetch(OwnerVisitKey(ws, owner), owner)
        val rows = first.activities.map { it.item }
        assertEquals(setOf(Event.EVENT_JOB_CREATED, Event.EVENT_PAYMENT_RECEIVED, Event.EVENT_EXPENSE_ADDED,
            Event.EVENT_WITHDRAWAL_RECORDED), rows.map { it.eventType }.toSet())
        assertEquals(setOf("job", "late-payment", "ordinary-withdrawal", "expense-confirmed"), rows.map { it.operationId }.toSet())
        assertEquals(setOf(partner, "second-partner"), rows.map { it.actorUid }.toSet())
        assertTrue(first.readTime >= now)
        val repeat = fetch(OwnerVisitKey(ws, owner), owner).activities.map { it.item.id }.toSet()
        assertEquals(rows.map { it.id }.toSet(), repeat)
        assertTrue(runCatching { fetch(OwnerVisitKey(ws, partner), partner) }.isFailure)
        assertTrue(runCatching { fetch(OwnerVisitKey(ws, owner), "foreign-account") }.isFailure)
    }
}
