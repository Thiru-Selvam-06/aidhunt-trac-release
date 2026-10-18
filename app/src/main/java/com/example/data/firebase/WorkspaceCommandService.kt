package com.example.data.firebase

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Trusted commands for checks which Firestore Rules cannot perform with collection queries. */
object WorkspaceCommandService {
    /** A fresh Owner purge action, not reconstruction of any legacy queued timestamp. */
    suspend fun deleteCustomer(workspaceId: String, id: String) {
        val actor = FirebaseAuth.getInstance().currentUser?.uid ?: throw WorkspaceAuthenticationRequiredException()
        deleteCustomer(workspaceId, id, actor, java.util.UUID.randomUUID().toString(), System.currentTimeMillis(), org.json.JSONArray())
    }
    suspend fun deleteCustomer(workspaceId: String, id: String, actorUid: String, operationId: String, actionTime: Long, dependencies: org.json.JSONArray) = command("deleteWorkspaceCustomer", WorkspaceCommandProtocol.entryData(workspaceId, id, operationId, actionTime, actorUid, dependencies).apply { remove("entryId"); put("customerId", id); put("operationType", "DELETE_CUSTOMER") }, actorUid)
    suspend fun deleteEntry(workspaceId: String, id: String, operationId: String, actorUid: String, actionTime: Long, dependencies: org.json.JSONArray) = command("deleteWorkspaceEntry", WorkspaceCommandProtocol.entryData(workspaceId, id, operationId, actionTime, actorUid, dependencies), actorUid)
    private suspend fun command(name: String, data: JSONObject, actorUid: String? = null) = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: throw WorkspaceAuthenticationRequiredException()
        if (actorUid != null && user.uid != actorUid) throw WorkspaceSessionChangedException()
        val project = FirebaseApp.getInstance().options.projectId ?: throw IllegalStateException("Firebase project unavailable")
        WorkspaceCommandProtocol.withAuthenticatedToken(actorUid ?: user.uid,
            currentUid = { FirebaseAuth.getInstance().currentUser?.uid },
            fetchToken = { forceRefresh ->
                try { user.getIdToken(forceRefresh).await().token }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: com.google.firebase.auth.FirebaseAuthInvalidUserException) { throw WorkspaceAuthenticationRequiredException() }
            }) { token ->
            val request = WorkspaceCommandProtocol.request(FirebaseEnvironment.commandUrl(project, name,
                data.optString("workspaceId")), token, data)
            http.newCall(request).execute().use { response ->
                WorkspaceCommandProtocol.verifyResponse(response.code, response.body?.string().orEmpty(), data)
            }
        }
    }

    private val http = okhttp3.OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()
}
