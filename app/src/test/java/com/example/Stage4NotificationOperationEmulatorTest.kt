package com.example

import com.example.data.entity.AppNotificationEntity
import com.example.data.entity.PendingOperationEntity
import com.example.data.firebase.FirestoreValueCodec
import com.example.data.firebase.WorkspaceMember
import com.example.data.firebase.notificationFromFirestoreMap
import com.example.data.sync.NotificationEmitter
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64

/** Emulator-backed verification for Stage 4 notifications and audit retention lifecycle. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class Stage4NotificationOperationEmulatorTest {

    private val origin = "http://127.0.0.1:18180"
    private val project = "demo-aidhunt-v16"
    private val root = "projects/$project/databases/(default)/documents"
    private val ws = "ws_stage4_emu"
    private val client = okhttp3.OkHttpClient()
    private val now get() = System.currentTimeMillis() - 5000L

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

    private fun createDoc(actor: String, path: String, data: Map<String, Any?>): Pair<Int, String> {
        val write = JSONObject()
            .put("update", JSONObject().put("name", "$root/$path").put("fields", JSONObject().apply {
                data.forEach { (k, v) -> put(k, FirestoreValueCodec.encode(v)) }
            }))
            .put("currentDocument", JSONObject().put("exists", false))
        return request("v1/$root:commit", body = JSONObject().put("writes", JSONArray().put(write)), token = jwt(actor))
    }

    @Before
    fun seedWorkspace() {
        val reach = runCatching { request("v1/$root/workspaces/$ws", method = "GET", token = "owner").first }.getOrNull()
        assumeTrue("Firestore emulator unavailable on 127.0.0.1:18180", reach != null)
        request("emulator/v1/projects/$project/databases/(default)/documents", method = "DELETE", token = "owner")

        fun bypassSet(path: String, data: Map<String, Any?>) {
            val payload = JSONObject().put("fields", JSONObject().apply {
                data.forEach { (k, v) -> put(k, FirestoreValueCodec.encode(v)) }
            })
            val code = request("v1/$root/$path", method = "PATCH", body = payload, token = "owner").first
            assertEquals("Seed failed for $path", 200, code)
        }

        bypassSet("workspaces/$ws", mapOf("workspaceId" to ws, "ownerUid" to "owner"))
        listOf("owner" to "OWNER", "partnerA" to "PARTNER", "partnerB" to "PARTNER", "operator" to "OPERATOR")
            .forEach { (uid, role) ->
                bypassSet("workspaces/$ws/members/$uid", mapOf("workspaceId" to ws, "uid" to uid, "role" to role, "status" to "ACTIVE", "displayName" to uid))
            }
    }

    @Test
    fun confirmedPartnerActionPublishesNotificationOnceAndRecipientMarksRead() {
        val members = listOf(
            WorkspaceMember(uid = "owner", workspaceId = ws, role = "OWNER", status = "ACTIVE", displayName = "Owner"),
            WorkspaceMember(uid = "partnerA", workspaceId = ws, role = "PARTNER", status = "ACTIVE", displayName = "Partner A")
        )
        val op = PendingOperationEntity(
            operationId = "op-s4-job-1",
            workspaceId = ws,
            sequence = 1L,
            entityType = "JOB",
            entityId = 701L,
            mutationType = "UPSERT",
            payloadJson = JSONObject().put("customerName", "Farmer S").put("totalAmount", 1400.0).toString(),
            actorUid = "partnerA",
            actionTime = now
        )
        val emitted = NotificationEmitter.buildForConfirmedOperation(op, members, "owner")
        assertEquals(1, emitted.size)
        val notif = emitted.first()

        // Publish absent -> 200 OK
        val (createCode, _) = createDoc("partnerA", "workspaces/$ws/notifications/${notif.id}", notif.toFirestoreMap())
        assertEquals(200, createCode)

        // Duplicate create with exists=false fails precondition -> idempotent replay never overwrites
        val (dupCode, _) = createDoc("partnerA", "workspaces/$ws/notifications/${notif.id}", notif.toFirestoreMap())
        assertNotEquals(200, dupCode)

        // Owner reads notification & marks it read; operator is denied
        val (ownerGet, ownerBody) = request("v1/$root/workspaces/$ws/notifications/${notif.id}", method = "GET", token = jwt("owner"))
        assertEquals(200, ownerGet)
        val fields = JSONObject(ownerBody).getJSONObject("fields")
        val decodedMap = fields.keys().asSequence().associateWith { FirestoreValueCodec.decode(fields.getJSONObject(it)) }
        val parsed = notificationFromFirestoreMap(decodedMap)
        assertEquals(AppNotificationEntity.EVENT_JOB_CREATED, parsed.eventType)

        val (opGet, _) = request("v1/$root/workspaces/$ws/notifications/${notif.id}", method = "GET", token = jwt("operator"))
        assertEquals(403, opGet)

        val patchBody = JSONObject().put("fields", JSONObject().apply {
            put("isRead", FirestoreValueCodec.encode(true))
            put("readAt", FirestoreValueCodec.encode(now + 200L))
        })
        val (markCode, _) = request(
            "v1/$root/workspaces/$ws/notifications/${notif.id}?updateMask.fieldPaths=isRead&updateMask.fieldPaths=readAt",
            method = "PATCH",
            body = patchBody,
            token = jwt("owner")
        )
        assertEquals(200, markCode)
    }
}
