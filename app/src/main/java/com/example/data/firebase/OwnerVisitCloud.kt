package com.example.data.firebase

import com.example.BuildConfig
import com.example.data.auth.RoleUtils
import com.example.data.sync.ConfirmedOwnerActivity
import com.example.data.sync.OwnerActivityEvidence
import com.example.data.sync.OwnerVisitActivity
import com.example.data.sync.OwnerVisitKey
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Read-only REST queries using the same Firebase user, Rules and App Check as the app. */
internal object OwnerVisitCloud {
    data class Refresh(val readTime: Long, val activities: List<ConfirmedOwnerActivity>)

    private val http = okhttp3.OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).followRedirects(false).build()
    private val pathSegment = Regex("[A-Za-z0-9_-]{1,128}")
    private val fields = (
        "workspaceId id isDeleted isHistorical actorUid operationId operationType actionTime protocolVersion " +
        "createdByUid authorizationTimeStatus jobEditHistory deletedJobEditHistory recordId kind customerName " +
        "expenseType paidBy fundingSource externalLoanId collectedByUid reversedByUid status " +
        "targetPartnerUid borrowingRequestId isBorrowing withdrawalType uid displayName role ownerUid syncCapabilities "+
        "entityType entityId mutationType"
    ).split(' ').toSet()

    suspend fun refresh(key: OwnerVisitKey, since: Long?): Refresh {
        val app = FirebaseApp.getInstance()
        val auth = FirebaseAuth.getInstance(app)
        val user = auth.currentUser?.takeIf { it.uid == key.ownerUid } ?: throw WorkspaceSessionChangedException()
        val token = user.getIdToken(false).await().token ?: throw WorkspaceAuthenticationRequiredException()
        val check = try { FirebaseAppCheck.getInstance(app).getAppCheckToken(false).await().token }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        val project = app.options.projectId ?: throw IOException("Firebase project unavailable")
        val origin = if (BuildConfig.DEVICE_TEST) {
            check(project == FirebaseEnvironment.DEMO_PROJECT)
            "http://127.0.0.1:18180"
        } else "https://firestore.googleapis.com"
        return load(key, since, project) { rpc, body ->
            withContext(Dispatchers.IO) {
                if (auth.currentUser?.uid != key.ownerUid) throw WorkspaceSessionChangedException()
                val request = okhttp3.Request.Builder().url("$origin/v1/$rpc").header("Authorization", "Bearer $token")
                    .post(body.toString().toRequestBody("application/json".toMediaType()))
                if (!check.isNullOrBlank()) request.header("X-Firebase-AppCheck", check)
                http.newCall(request.build()).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("Owner activity read failed (" + response.code + ")")
                    JSONArray(response.body?.string() ?: throw IOException("Missing owner activity response"))
                }
            }
        }
    }

    internal suspend fun load(
        key: OwnerVisitKey, since: Long?, project: String,
        request: suspend (String, JSONObject) -> JSONArray
    ): Refresh = coroutineScope {
        require(pathSegment.matches(project) && pathSegment.matches(key.ownerUid) && pathSegment.matches(key.workspaceId))
        val root = "projects/$project/databases/(default)/documents"
        val workspace = "$root/workspaces/" + key.workspaceId
        val identity = request("$root:batchGet", JSONObject().put("documents", JSONArray().put(workspace)))
            .getJSONObject(0)
        val found = identity.optJSONObject("found") ?: throw SecurityException("Owner workspace is unavailable")
        val workspaceData = decode(found)
        require(found.optString("name") == workspace && workspaceData["ownerUid"] == key.ownerUid &&
            workspaceData["workspaceId"] == key.workspaceId) { "Original workspace Owner required" }
        val readTime = FirestoreServerTime.parseReadTime(identity.getString("readTime"))
        // Bound the first history fetch. Later visits read the elapsed interval with one day of
        // overlap for offline operations, preserving old already-saved activity in Room.
        val day = 86_400_000L
        val lowerBound = ((since ?: (readTime - 30 * day)) - day).coerceAtLeast(0L)
        val utc = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US).apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }
        val timestampFloor = utc.format(java.util.Date(lowerBound))

        suspend fun query(collection: String, filterField: String? = null): List<OwnerActivityEvidence> {
            val query = JSONObject().put("from", JSONArray().put(JSONObject().put("collectionId", collection)))
            if (filterField != null) {
                val value = if (filterField == "serverReceivedAt" || filterField == "createdAt")
                    JSONObject().put("timestampValue", timestampFloor)
                else JSONObject().put("integerValue", lowerBound.toString())
                query.put("where", JSONObject().put("fieldFilter", JSONObject()
                    .put("field", JSONObject().put("fieldPath", filterField))
                    .put("op", "GREATER_THAN_OR_EQUAL").put("value", value)))
            }
            val rows = request("$workspace:runQuery", JSONObject().put("structuredQuery", query))
            return (0 until rows.length()).mapNotNull { index ->
                val envelope = rows.getJSONObject(index)
                if (envelope.has("error")) throw IOException("Owner activity query was incomplete")
                val document = envelope.optJSONObject("document") ?: return@mapNotNull null
                val prefix = "$workspace/$collection/"
                val name = document.getString("name")
                require(name.startsWith(prefix) && '/' !in name.removePrefix(prefix))
                OwnerActivityEvidence(collection, name.removePrefix(prefix), decode(document),
                    document.getString("createTime"), document.getString("updateTime"),
                    FirestoreServerTime.parseReadTime(document.getString("updateTime")))
            }
        }

        val members = query("members").map { record ->
            WorkspaceMember(
                uid = record.documentId,
                workspaceId = (record.data["workspaceId"] as? String).orEmpty(),
                status = (record.data["status"] as? String).orEmpty(),
                role = (record.data["role"] as? String).orEmpty(),
                displayName = record.data["displayName"] as? String
            )
        }
        require(members.any {
            it.workspaceId == key.workspaceId && it.uid == key.ownerUid &&
                it.status.equals("ACTIVE", true) && RoleUtils.normalizeRole(it.role) == RoleUtils.ROLE_OWNER
        }) { "Active Owner membership required" }

        val sources = mutableMapOf(
            "entries" to listOf("updatedAt", "serverReceivedAt"),
            "entryDeletionProofs" to listOf("actionTime"),
            "collections" to listOf("actionTime", "serverReceivedAt"),
            "expenses" to listOf("actionTime", "serverReceivedAt"),
            "withdrawals" to listOf("actionTime", "serverReceivedAt")
        )
        if ((workspaceData["syncCapabilities"] as? Map<*, *>)?.get("atomicCashControlV1") == true) {
            sources["cashDeleteProofs"] = listOf("actionTime", "createdAt")
            sources["cashOperations"] = listOf("actionTime", "createdAt")
        }

        // Use simple range queries supported by the existing Spark indexes. Deduplicate
        // overlapping results by document, choosing the freshest server version.
        val evidence = sources.flatMap { (collection, filters) ->
            filters.map { filter -> async { query(collection, filter) } }
        }.awaitAll().flatten().groupBy { it.collection to it.documentId }.values.map { versions ->
            versions.maxBy { it.availableAt }
        }
        Refresh(readTime, OwnerVisitActivity.verified(key, members, evidence))
    }

    private fun decode(document: JSONObject): Map<String, Any?> {
        val row = document.optJSONObject("fields") ?: return emptyMap()
        return row.keys().asSequence().filter { it in fields }
            .associateWith { FirestoreValueCodec.decode(row.getJSONObject(it)) }
    }
}
