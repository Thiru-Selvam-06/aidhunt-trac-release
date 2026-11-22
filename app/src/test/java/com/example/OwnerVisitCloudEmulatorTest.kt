package com.example

import com.example.data.entity.AppNotificationEntity.Companion as Event
import com.example.data.firebase.FirestoreValueCodec
import com.example.data.firebase.OwnerVisitCloud
import com.example.data.firebase.toFirestoreMap
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

    private fun confirmJobThenEdit(): Pair<Long, () -> Unit> {
        val id = 6L
        val stamp = System.currentTimeMillis() - 1000
        seed("workspaces/$ws/customers/8", mapOf("workspaceId" to ws, "id" to 8L, "isDeleted" to false))
        val job = com.example.data.entity.JobEntryEntity(id = id, workspaceId = ws, customerId = 8,
            customerName = "Synthetic", operatorName = "Partner", tractorLabel = "T", workType = "Work",
            startTimeMillis = stamp, endTimeMillis = stamp + 1000, durationMinutes = 1,
            totalAmount = 100.25, amountReceived = 0.0, pendingAmount = 100.25, addedByPartner = partner,
            createdByUid = partner, entryForUid = partner, financialAttributionUid = partner, createdAt = stamp)
        val values = job.toFirestoreMap()
        val fields = encoded(values + mapOf("workspaceId" to ws, "protocolVersion" to 2L, "operationType" to "UPSERT",
            "actorUid" to partner, "operationId" to "job-confirmed", "actionTime" to stamp,
            "dependencies" to emptyList<String>(), "authorizationTimeStatus" to "CLIENT_ORIGINAL_V2", "isDeleted" to false))
        fields.put("authorizationCreatedAt", JSONObject().put("timestampValue", java.time.Instant.ofEpochMilli(stamp).toString()))
        val update = JSONObject().put("name", "$root/workspaces/$ws/entries/$id").put("fields", fields)
        val transforms = JSONArray().put(JSONObject().put("fieldPath", "serverReceivedAt").put("setToServerValue", "REQUEST_TIME"))
        val committed = rpc("$root:commit", "POST", JSONObject().put("writes", JSONArray().put(JSONObject().put("update", update).put("updateTransforms", transforms))), jwt(partner))
        assertEquals(committed.second, 200, committed.first)
        return id to {
            val found = rpc("$root/workspaces/$ws/entries/$id", "GET", null, jwt(partner))
            assertEquals(200, found.first)
            val stored = JSONObject(found.second).getJSONObject("fields")
            val before = stored.keys().asSequence().associateWith { FirestoreValueCodec.decode(stored.getJSONObject(it)) }
            val editTime = System.currentTimeMillis() - 1
            val after = before + mapOf("notes" to "Changed synthetic notes")
            val history = com.example.data.sync.JobEditAudit.append(emptyList<Any>(), before, after, partner, "job-edit-confirmed", editTime)
            stored.put("editedByUid", FirestoreValueCodec.encode(partner))
                .put("editedByName", FirestoreValueCodec.encode("Synthetic Partner"))
                .put("editedByRole", FirestoreValueCodec.encode("PARTNER"))
                .put("notes", FirestoreValueCodec.encode("Changed synthetic notes"))
                .put("updatedAt", FirestoreValueCodec.encode(editTime)).put("actionTime", FirestoreValueCodec.encode(editTime))
                .put("operationId", FirestoreValueCodec.encode("job-edit-confirmed")).put("jobEditHistory", FirestoreValueCodec.encode(history))
            val result = rpc("$root:commit", "POST", JSONObject().put("writes", JSONArray().put(JSONObject().put("update",
                JSONObject().put("name", "$root/workspaces/$ws/entries/$id").put("fields", stored)))), jwt(partner))
            assertEquals(result.second, 200, result.first)
        }
    }

    @Test fun cashHistoryUsesConfirmedServerTimeAndKeepsOwnerOnlyRecipientAndRolloutBoundaries() = runBlocking {
        val ping = runCatching { rpc("$root/workspaces/$ws", "GET", null, "owner") }.getOrNull()
        assumeTrue("Requires local Firestore Emulator at 127.0.0.1:18180", ping != null)
        seed("workspaces/$ws", mapOf("workspaceId" to ws, "ownerUid" to owner,
            "syncCapabilities" to mapOf("cashBalanceAdjustmentsV1" to true)))
        for ((uid, role) in listOf(owner to "OWNER", partner to "PARTNER"))
            seed("workspaces/$ws/members/$uid", mapOf("uid" to uid, "role" to role,
                "status" to "ACTIVE", "displayName" to uid))
        var cashQueries = 0
        suspend fun fetch() = OwnerVisitCloud.load(OwnerVisitKey(ws, owner), now - 500L, project) { method, data ->
            val query = data.optJSONObject("structuredQuery")
            if (query?.getJSONArray("from")?.getJSONObject(0)?.optString("collectionId") == "cashBalanceAdjustments") {
                cashQueries++
                val filter = query.getJSONObject("where").getJSONObject("fieldFilter")
                assertEquals("confirmedAt", filter.getJSONObject("field").getString("fieldPath"))
                assertTrue(filter.getJSONObject("value").has("timestampValue"))
            }
            val (status, body) = rpc(method, "POST", data, jwt(owner))
            check(status == 200) { "Owner history request failed: $status" }
            JSONArray(body)
        }
        assertTrue(fetch().activities.isEmpty()) // No confirmed action, no success alert.
        fun confirm(actor: String, revision: Long, priorTotal: Long) {
            val op = "cash-confirmed-$revision"
            val state = encoded(mapOf("workspaceId" to ws, "revision" to revision,
                "totalAdjustmentPaise" to priorTotal + 100L, "operationId" to op, "actorUid" to actor))
            val edit = encoded(mapOf("workspaceId" to ws, "revision" to revision, "operationId" to op,
                "previousTotalPaise" to priorTotal, "totalAdjustmentPaise" to priorTotal + 100L,
                "basePaise" to 100000L, "beforePaise" to 100000L + priorTotal,
                "afterPaise" to 100100L + priorTotal, "deltaPaise" to 100L,
                "reason" to "Cash reason / காரணம்", "actorUid" to actor, "actorName" to actor,
                "actionTime" to System.currentTimeMillis() - 1000))
            fun write(path: String, fields: JSONObject) = JSONObject().put("update", JSONObject()
                .put("name", "$root/workspaces/$ws/$path").put("fields", fields))
                .put("updateTransforms", JSONArray().put(JSONObject().put("fieldPath", "confirmedAt").put("setToServerValue", "REQUEST_TIME")))
            val writes = JSONArray().put(write("cashBalanceState/current", state)).put(write("cashBalanceAdjustments/$op", edit))
            val result = rpc("$root:commit", "POST", JSONObject().put("writes", writes), jwt(actor))
            assertEquals(result.second, 200, result.first)
        }
        confirm(partner, 1L, 0L)
        val first = fetch()
        assertTrue(first.complete)
        val event = first.activities.single().item
        assertEquals(Event.EVENT_CASH_BALANCE_ADJUSTED, event.eventType)
        assertEquals(owner, event.recipientUid)
        assertEquals(partner, event.actorUid)
        assertEquals(1.0, event.amount, 0.0)
        assertEquals("Cash reason / காரணம்", JSONObject(event.metadataJson).getString("reason"))
        assertEquals(event.id, fetch().activities.single().item.id)
        confirm(owner, 2L, 100L)
        assertEquals(listOf(event.id), fetch().activities.map { it.item.id }) // No Owner self-alert.
        assertTrue(cashQueries > 0)
        seed("workspaces/$ws", mapOf("workspaceId" to ws, "ownerUid" to owner))
        cashQueries = 0
        val disabled = fetch()
        assertTrue(disabled.complete)
        assertEquals(0, cashQueries)
        assertTrue(disabled.activities.isEmpty())
    }

    @Test fun ownerReadsAuthorizedLedgerAndMissingNotificationCollectionWithoutTrustingClientClaims() = runBlocking {
        val ping = runCatching { rpc("$root/workspaces/$ws", "GET", null, "owner") }.getOrNull()
        assumeTrue("Requires local Firestore Emulator at 127.0.0.1:18180", ping != null)
        seed("workspaces/$ws", mapOf("workspaceId" to ws, "ownerUid" to owner))
        for ((uid, role) in listOf(owner to "OWNER", partner to "PARTNER", "second-partner" to "PARTNER")) {
            seed("workspaces/$ws/members/$uid", mapOf("uid" to uid, "role" to role,
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
        assertTrue(first.complete)
        val rows = first.activities.map { it.item }
        assertEquals(setOf(Event.EVENT_JOB_CREATED, Event.EVENT_PAYMENT_RECEIVED, Event.EVENT_EXPENSE_ADDED,
            Event.EVENT_WITHDRAWAL_RECORDED), rows.map { it.eventType }.toSet())
        assertEquals(setOf("job", "late-payment", "ordinary-withdrawal", "expense-confirmed"), rows.map { it.operationId }.toSet())
        assertEquals(setOf(partner, "second-partner"), rows.map { it.actorUid }.toSet())
        assertTrue(first.readTime >= now)
        val repeat = fetch(OwnerVisitKey(ws, owner), owner).activities.map { it.item.id }.toSet()
        assertEquals(rows.map { it.id }.toSet(), repeat)
        val (confirmedId, edit) = confirmJobThenEdit()
        val reopened = fetch(OwnerVisitKey(ws, owner), owner)
        assertTrue(reopened.complete)
        assertTrue(reopened.activities.any { it.item.entityId == confirmedId && it.item.operationId == "job-confirmed" && it.item.eventType == Event.EVENT_JOB_CREATED })
        edit()
        val edited = fetch(OwnerVisitKey(ws, owner), owner)
        assertTrue(edited.complete)
        assertTrue(edited.activities.any { it.item.entityId == confirmedId && it.item.operationId == "job-edit-confirmed" && it.item.eventType == Event.EVENT_JOB_EDITED })
        val visit = com.example.data.sync.OwnerVisit(OwnerVisitKey(ws, owner), first.readTime, first.readTime, com.example.data.sync.OwnerVisitStatus.READY)
            .refreshed(edited.readTime, edited.activities.map { it.item.id }.toSet())
        assertEquals(com.example.data.sync.OwnerVisitStatus.READY, visit.status)
        assertEquals(edited.readTime, visit.lastUpdated)
        val partial = OwnerVisitCloud.load(OwnerVisitKey(ws, owner), now - 500L, project) { method, data ->
            if (data.optJSONObject("structuredQuery")?.getJSONArray("from")?.getJSONObject(0)
                    ?.optString("collectionId") == "expenses") {
                throw OwnerVisitCloud.ReadFailure("HTTP", "PERMISSION_OR_APP_CHECK", 403)
            }
            val (status, body) = rpc(method, "POST", data, jwt(owner))
            check(status == 200)
            JSONArray(body)
        }
        assertFalse(partial.complete)
        assertTrue(partial.incompleteSources.all { it.contains("EXPENSES/PERMISSION_OR_APP_CHECK/403") })
        assertTrue(partial.activities.any { it.item.eventType == Event.EVENT_JOB_CREATED })
        assertTrue(partial.activities.none { it.item.eventType == Event.EVENT_EXPENSE_ADDED })
        assertTrue(runCatching { fetch(OwnerVisitKey(ws, partner), partner) }.isFailure)
        assertTrue(runCatching { fetch(OwnerVisitKey(ws, owner), "foreign-account") }.isFailure)
    }
}
