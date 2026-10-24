package com.example.data.firebase

import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

/** Uses the existing Firebase user and Firestore REST API. No probe document or
 * server credential is written. readTime is server evidence, not device wall time. */
internal object FirestoreServerTime {
    private val http = okhttp3.OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()

    suspend fun read(workspaceId: String, actorUid: String, app: FirebaseApp = FirebaseApp.getInstance()): Long = withContext(Dispatchers.IO) {
        val auth = FirebaseAuth.getInstance(app)
        fun requireActor() {
            if (auth.currentUser?.uid != actorUid) throw WorkspaceSessionChangedException()
        }
        requireActor()
        val user = auth.currentUser ?: throw WorkspaceAuthenticationRequiredException()
        val project = app.options.projectId ?: throw IOException("Firebase project unavailable")
        val token = user.getIdToken(false).await().token ?: throw WorkspaceAuthenticationRequiredException()
        requireActor()
        val origin = if (BuildConfig.DEVICE_TEST) {
            check(project == FirebaseEnvironment.DEMO_PROJECT)
            "http://127.0.0.1:18180"
        } else "https://firestore.googleapis.com"
        val root = "projects/$project/databases/(default)/documents"
        val request = okhttp3.Request.Builder().url("$origin/v1/$root:batchGet")
            .header("Authorization", "Bearer $token")
            .post(JSONObject().put("documents", JSONArray().put("$root/workspaces/$workspaceId/members/$actorUid"))
                .put("mask", JSONObject().put("fieldPaths", JSONArray().put("status")))
                .toString().toRequestBody("application/json".toMediaType()))
        // Keep existing App Check protection when a provider is installed. Like the
        // SDK, lack of a configured provider cannot supply a valid token; Rules and
        // any enforced App Check policy still decide whether this read is allowed.
        try {
            val check = com.google.firebase.appcheck.FirebaseAppCheck.getInstance(app).getAppCheckToken(false).await()
            if (check.token.isNotBlank()) request.header("X-Firebase-AppCheck", check.token)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled
        } catch (_: Exception) { }
        requireActor()
        http.newCall(request.build()).execute().use { response ->
            requireActor()
            if (response.code == 401) throw WorkspaceAuthenticationRequiredException()
            if (response.code == 403) throw FirebaseFirestoreException(
                "Server time membership read denied; original action retained", FirebaseFirestoreException.Code.PERMISSION_DENIED)
            if (!response.isSuccessful) throw IOException("Server time verification unavailable (${response.code}); original action remains pending")
            val row = try {
                JSONArray(response.body?.string() ?: throw IOException("Missing server time response")).getJSONObject(0)
            } catch (error: org.json.JSONException) {
                throw IOException("Invalid server time response; original action remains pending", error)
            }
            if (row.optJSONObject("found")?.optString("name") != "$root/workspaces/$workspaceId/members/$actorUid")
                throw SecurityException("Server time membership identity is missing")
            parseReadTime(row.optString("readTime"))
        }
    }

    /** Firestore returns UTC RFC3339 with up to nine fractional digits. Floor to
     * milliseconds, including on API 24/25 without java.time desugaring. */
    internal fun parseReadTime(raw: String): Long {
        val match = Regex("(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})(?:\\.(\\d{1,9}))?Z").matchEntire(raw)
            ?: throw IOException("Invalid server readTime; original action remains pending")
        val normalized = match.groupValues[1] + "." + match.groupValues[2].padEnd(3, '0').take(3) + "Z"
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            isLenient = false; timeZone = TimeZone.getTimeZone("UTC")
        }
        return try { format.parse(normalized)?.time ?: throw IOException("Missing server readTime") }
        catch (error: java.text.ParseException) { throw IOException("Invalid server readTime", error) }
    }
}
