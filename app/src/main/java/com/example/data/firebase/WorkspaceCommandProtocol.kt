package com.example.data.firebase

import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

internal class WorkspaceAuthenticationRequiredException : IOException("Sign in again to synchronize pending deletions")
internal class WorkspaceSessionChangedException : IOException("Account changed; switch back to the original account to synchronize this deletion")

/** The callable HTTP wire contract; shared by production transport and local tests. */
internal class WorkspaceUpgradeRequiredException : IllegalArgumentException("UPGRADE_REQUIRED: Update AIDHUNT Trac; pending actions are retained for review. Do not recreate their timestamps.")

internal object WorkspaceCommandProtocol {
    const val VERSION = 2
    fun entryData(workspaceId: String, id: String, operationId: String, actionTime: Long, actorUid: String, dependencies: org.json.JSONArray = org.json.JSONArray()): JSONObject = JSONObject()
        .put("workspaceId", workspaceId).put("entryId", id).put("operationId", operationId).put("actionTime", actionTime).put("protocolVersion", VERSION).put("actorContext", actorUid).put("operationType", "DELETE_ENTRY").put("dependencies", dependencies)

    fun request(url: String, token: String, data: JSONObject): Request {
        if (token.isBlank()) throw WorkspaceAuthenticationRequiredException()
        return Request.Builder().url(url).header("Authorization", "Bearer $token")
            .post(JSONObject().put("data", data).toString().toRequestBody("application/json".toMediaType())).build()
    }

    suspend fun withAuthenticatedToken(expectedUid: String, currentUid: () -> String?,
        fetchToken: suspend (Boolean) -> String?, action: (String) -> Unit) {
        if (currentUid() == null) throw WorkspaceAuthenticationRequiredException()
        var forceRefresh = false
        while (true) {
            if (currentUid() != expectedUid) throw WorkspaceSessionChangedException()
            val token = fetchToken(forceRefresh) ?: throw WorkspaceAuthenticationRequiredException()
            if (currentUid() != expectedUid) throw WorkspaceSessionChangedException()
            try { action(token); return }
            catch (auth: WorkspaceAuthenticationRequiredException) {
                if (forceRefresh) throw auth
                forceRefresh = true
            }
        }
    }

    fun verifyResponse(code: Int, body: String, expectedCommand: JSONObject? = null) {
        val json = try { JSONObject(body) } catch (_: org.json.JSONException) { null }
        val error = json?.optJSONObject("error")
        if (error?.optJSONObject("details")?.optString("code") == "UPGRADE_REQUIRED") throw WorkspaceUpgradeRequiredException()
        if (code == 401 || error?.optString("status") == "UNAUTHENTICATED") throw WorkspaceAuthenticationRequiredException()
        if (code !in 200..299 || error != null) {
            if (error?.optString("status") in setOf("PERMISSION_DENIED", "FAILED_PRECONDITION", "INVALID_ARGUMENT"))
                throw IllegalArgumentException("HTTP $code ${error?.optString("status")}: ${error?.optString("message") ?: "Deletion rejected"}")
            throw IOException("HTTP $code ${error?.optString("status").orEmpty()}: ${error?.optString("message") ?: "Deletion could not sync; retry when online"}")
        }
        if (expectedCommand == null) throw IOException("Unidentified deletion cannot be acknowledged")
        val result = json?.optJSONObject("result")
        val recordKey = when (expectedCommand.optString("operationType")) {
            "DELETE_ENTRY" -> "entryId"
            "DELETE_CUSTOMER" -> "customerId"
            else -> throw IOException("Unknown deletion command cannot be acknowledged")
        }
        val echoedTime = result?.opt("actionTime")
        if (result == null || result.opt("deleted") != true ||
            listOf("workspaceId", recordKey, "operationId").any {
                !result.has(it) || result.opt(it) !is String || result.optString(it) != expectedCommand.optString(it)
            } || echoedTime !is Number || echoedTime.toString() != expectedCommand.optLong("actionTime").toString())
            throw IOException("Deletion acknowledgement was not confirmed; retry safely")
    }
}
