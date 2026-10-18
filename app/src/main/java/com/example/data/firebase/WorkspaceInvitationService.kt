package com.example.data.firebase

import android.app.Activity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.FirebaseException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Secondary Auth verifies the invitee without changing the Owner's authenticated session. */
class WorkspaceInvitationService {
    companion object {
        const val OWNER_ONLY_ERROR = "Only the workspace Owner can send invitations. Ask the Owner to invite this number."

        const val VALIDATION_ERROR = "Check the invitation name, phone number and role, then try again."
        const val AUTHENTICATION_ERROR = "Your session could not be verified. Sign in again and retry the invitation."
        const val SERVICE_ERROR = "Invitation service is unavailable. Try again later."
        const val ENDPOINT_UNAVAILABLE_ERROR = "Invitation service is not available for this workspace. No member was added. Contact support to restore the backend."

        fun mapBackendError(statusCode: Int, rawBody: String): Exception {
            val errorObj = runCatching { JSONObject(rawBody).optJSONObject("error") }.getOrNull()
            val status = (errorObj?.optString("status").orEmpty().ifBlank {
                errorObj?.optString("code").orEmpty()
            }).trim().replace('-', '_').uppercase(java.util.Locale.ROOT)
            // Messages are untrusted backend details, never authorization signals or UI copy.
            return when {
                statusCode == 403 || status == "PERMISSION_DENIED" -> SecurityException(OWNER_ONLY_ERROR)
                statusCode == 401 || status == "UNAUTHENTICATED" -> SecurityException(AUTHENTICATION_ERROR)
                statusCode in 500..599 -> IllegalStateException(SERVICE_ERROR)
                statusCode == 404 && errorObj == null -> IllegalStateException(ENDPOINT_UNAVAILABLE_ERROR)
                statusCode == 429 || status == "RESOURCE_EXHAUSTED" ->
                    IllegalStateException("Too many invitation attempts. Wait and try again.")
                status == "ALREADY_EXISTS" ->
                    IllegalStateException("This member or invitation already exists. Refresh the member list.")
                status == "FAILED_PRECONDITION" || status == "NOT_FOUND" ->
                    IllegalStateException("This invitation is no longer valid. Refresh and start a new invitation.")
                statusCode == 400 || status == "INVALID_ARGUMENT" -> IllegalArgumentException(VALIDATION_ERROR)
                statusCode in 400..499 -> IllegalStateException("Invitation request could not be completed. Refresh and try again.")
                else -> IllegalStateException("Invitation service returned an invalid response. Try again.")
            }
        }

    }

    private val primaryAuth = FirebaseAuth.getInstance()
    private val app = FirebaseApp.getApps(FirebaseApp.getInstance().applicationContext)
        .firstOrNull { it.name == "workspace-invitation" }
        ?: FirebaseApp.initializeApp(FirebaseApp.getInstance().applicationContext, FirebaseApp.getInstance().options, "workspace-invitation")
    private val targetAuth = FirebaseAuth.getInstance(app).also { if (com.example.BuildConfig.DEVICE_TEST) it.useEmulator("127.0.0.1", 19099) }
    private var invitationId = ""
    private var workspaceId = ""
    private var verificationId = ""
    private var verifiedCredential: PhoneAuthCredential? = null
    private var ownerAtBegin = ""
    private val attempts = FirebaseApp.getInstance().applicationContext
        .getSharedPreferences("workspace_invitation_attempts", android.content.Context.MODE_PRIVATE)
    private var attemptKey = ""


    private suspend fun call(name: String, data: JSONObject): JSONObject = withContext(Dispatchers.IO) {
        val owner = primaryAuth.currentUser ?: throw SecurityException("Owner sign-in is required")
        val token = owner.getIdToken(false).await().token ?: throw SecurityException("Authentication token is unavailable")
        val project = FirebaseApp.getInstance().options.projectId ?: throw IllegalStateException("Firebase project is unavailable")
        val request = okhttp3.Request.Builder().url(FirebaseEnvironment.commandUrl(project, name,
            data.optString("workspaceId")))
            .header("Authorization", "Bearer $token")
            .post(JSONObject().put("data", data).toString().toRequestBody("application/json".toMediaType())).build()
        okhttp3.OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build().newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            val result = runCatching { JSONObject(rawBody) }.getOrNull()
            if (!response.isSuccessful || result?.has("error") == true) {
                throw mapBackendError(response.code, rawBody)
            }
            result?.optJSONObject("result") ?: result?.optJSONObject("data") ?: throw IllegalStateException("Invalid invitation response")
        }
    }

    suspend fun begin(activity: Activity, wsId: String, name: String, phone: String, role: String,
        onCodeSent: () -> Unit, onError: (String) -> Unit, onVerified: () -> Unit = {}) {
        ownerAtBegin = primaryAuth.currentUser?.uid ?: throw SecurityException("Owner sign-in is required")
        verificationId = ""; verifiedCredential = null
        val digits = phone.filter { it.isDigit() }
        val e164 = if (phone.trim().startsWith("+")) "+$digits" else if (digits.length == 10) "+91$digits" else "+$digits"
        attemptKey = java.security.MessageDigest.getInstance("SHA-256")
            .digest("$ownerAtBegin|$wsId|$e164|${role.uppercase()}|$name".toByteArray()).joinToString("") { "%02x".format(it) }
        val prior = attempts.getString(attemptKey, null)
        val saved = prior?.let { runCatching { JSONObject(it) }.getOrNull() }
        val operationId = if (saved != null && saved.optLong("expiresAt") > System.currentTimeMillis()) saved.getString("operationId")
            else java.util.UUID.randomUUID().toString().also {
                check(attempts.edit().putString(attemptKey, JSONObject().put("operationId", it)
                    .put("expiresAt", System.currentTimeMillis() + 300_000L).toString()).commit()) { "Could not retain invitation request" }
            }
        val result = call("beginWorkspaceInvitation", JSONObject().put("workspaceId", wsId).put("name", name)
            .put("phone", e164).put("role", role.uppercase()).put("operationId", operationId))
        workspaceId = wsId; invitationId = result.getString("invitationId")
        targetAuth.signOut()
        val options = PhoneAuthOptions.newBuilder(targetAuth).setActivity(activity).setPhoneNumber(e164)
            .setTimeout(60, TimeUnit.SECONDS).setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // The credential is retained, but redemption still needs explicit dialog confirmation.
                    if (primaryAuth.currentUser?.uid == ownerAtBegin) { verifiedCredential = credential; onVerified() }

                }
                override fun onVerificationFailed(error: FirebaseException) { onError(error.message ?: "Unable to send invitation OTP") }
                override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) { verificationId = id; onCodeSent() }
            }).build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    suspend fun confirm(code: String): String {
        require(invitationId.isNotBlank() && (targetAuth.currentUser != null || verifiedCredential != null || (verificationId.isNotBlank() && code.length == 6))) { "Enter the invitation OTP" }
        val ownerUid = primaryAuth.currentUser?.uid ?: throw SecurityException("Owner sign-in is required")
        check(ownerUid == ownerAtBegin) { "Owner session changed" }
        val credential = if (code.isNotBlank()) PhoneAuthProvider.getCredential(verificationId, code) else verifiedCredential
        var confirmed = false
        try {
            val user = targetAuth.currentUser ?: targetAuth.signInWithCredential(credential ?: throw SecurityException("Target verification is required")).await().user
                ?: throw SecurityException("Target verification failed")
            check(primaryAuth.currentUser?.uid == ownerUid) { "Owner session changed" }
            val token = user.getIdToken(true).await().token ?: throw SecurityException("Target verification token is unavailable")
            val result = call("redeemWorkspaceInvitation", JSONObject().put("workspaceId", workspaceId).put("invitationId", invitationId).put("targetIdToken", token))
            check(result.optString("uid") == user.uid && result.optString("workspaceId") == workspaceId) { "Invitation acknowledgement mismatch; retry retained invitation" }
            confirmed = true
            attempts.edit().remove(attemptKey).commit()
            invitationId = ""; verificationId = ""; verifiedCredential = null
            return result.getString("uid")
        } finally {
            // Preserve the SDK-managed verified session for acknowledgement-loss retries.
            if (confirmed || primaryAuth.currentUser?.uid != ownerAtBegin) {
                targetAuth.signOut(); verifiedCredential = null
            }
        }
    }
}
