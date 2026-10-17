package com.example.data.firebase

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirebaseAuthService(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : AuthService {

    private val TAG = "FirebaseAuthService"

    private val firestoreRepository: FirestoreRepository by lazy { FirestoreRepository(context) }

    private val auth: FirebaseAuth? by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            val instance = FirebaseAuth.getInstance()
            if (com.example.BuildConfig.DEBUG) {
                instance.firebaseAuthSettings.setAppVerificationDisabledForTesting(true)
                Log.d("TRAC_AUTH", "DEBUG build: setAppVerificationDisabledForTesting(true)")
            }
            instance
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FirebaseAuth: ${e.message}")
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FirebaseFirestore: ${e.message}")
            null
        }
    }

    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _currentUserProfile = MutableStateFlow<UserProfile?>(null)
    override val currentUserProfile: StateFlow<UserProfile?> = _currentUserProfile.asStateFlow()

    override val currentUid: String?
        get() = auth?.currentUser?.let { if (it.isAnonymous) null else it.uid }

    private val authEpoch = java.util.concurrent.atomic.AtomicLong(0)
    private var profileRefresh: kotlinx.coroutines.Job? = null
    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    override fun startAuthStateListener() {
        val currentAuth = auth ?: return
        if (authStateListener != null) return
        authStateListener = FirebaseAuth.AuthStateListener { instance ->
            val user = instance.currentUser
            val epoch = authEpoch.incrementAndGet()
            profileRefresh?.cancel()
            if (user == null || user.isAnonymous) {
                _currentUserProfile.value = null; _authState.value = AuthState.Unauthenticated()
            } else {
                val cached = cachedVerifiedProfile(user.uid)
                _currentUserProfile.value = cached
                // A new UID must never inherit the previous account while online verification is pending.
                _authState.value = if (cached != null) AuthState.Authenticated(cached) else AuthState.Loading
                profileRefresh = scope.launch {
                    try {
                        val profile = withTimeout(10_000L) { fetchExistingUserProfile(user) }
                        if (authEpoch.get() != epoch || instance.currentUser?.uid != user.uid) return@launch
                        if (profile != null) { _currentUserProfile.value = profile; _authState.value = AuthState.Authenticated(profile) }
                        else { _currentUserProfile.value = null; _authState.value = AuthState.Unauthenticated("Account not registered") }
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e
                    } catch (e: Exception) {
                        if (authEpoch.get() != epoch || instance.currentUser?.uid != user.uid) return@launch
                        if (e is com.google.firebase.auth.FirebaseAuthInvalidUserException) { signOut(); return@launch }
                        if (e is SecurityException) {
                            context.getSharedPreferences("verified_auth_profile", Context.MODE_PRIVATE).edit().clear().commit()
                            _currentUserProfile.value = null
                            _authState.value = AuthState.Error(e.message ?: "Workspace access denied")
                        } else {
                            val cached = cachedVerifiedProfile(user.uid)
                            if (cached != null) { _currentUserProfile.value = cached; _authState.value = AuthState.Authenticated(cached) }
                            else _authState.value = AuthState.Error("Connect once to verify workspace access")
                        }
                    }
                }
            }
        }
        currentAuth.addAuthStateListener(authStateListener!!)
    }

    override suspend fun signInWithGoogle(context: Context, webClientId: String?): Result<UserProfile> {
        return try {
            val currentAuth = auth ?: return Result.failure(IllegalStateException("Firebase is not initialized."))

            _authState.value = AuthState.Loading
            val credentialManager = CredentialManager.create(context)

            // Resolve Web Client ID from resources or parameter
            val serverClientId = webClientId?.ifBlank { null }
                ?: getResourceString(context, "default_web_client_id")
                ?: "898996717587-udfsfb6gtt14v5n6phjoima6kt1rjj9r.apps.googleusercontent.com"

            val googleIdOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(context, request)
            val credential = result.credential

            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val authCredential = GoogleAuthProvider.getCredential(googleIdTokenCredential.idToken, null)
                val authResult = currentAuth.signInWithCredential(authCredential).awaitTask()
                val user = authResult.user ?: throw IllegalStateException("Firebase user is null after sign in")

                val profile = fetchExistingUserProfile(user)
                if (profile != null) {
                    Log.i("TRAC_AUTH", "AUTH_SUCCESS ACCOUNT_FOUND uid=${user.uid}")
                    _currentUserProfile.value = profile
                    _authState.value = AuthState.Authenticated(profile)
                    Result.success(profile)
                } else {
                    Log.w("TRAC_AUTH", "AUTH_SUCCESS ACCOUNT_NOT_FOUND uid=${user.uid} -> NAVIGATION_BLOCKED")
                    currentAuth.signOut()
                    _currentUserProfile.value = null
                    _authState.value = AuthState.Unauthenticated("No account found")
                    Result.failure(IllegalStateException("No account found"))
                }
            } else {
                val err = "Unrecognized credential type received from CredentialManager"
                _authState.value = AuthState.Error(err)
                Result.failure(IllegalStateException(err))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed: ${e.message}", e)
            _authState.value = AuthState.Error(e.message ?: "Google Sign-In failed")
            Result.failure(e)
        }
    }

    override suspend fun signInWithEmail(email: String, password: String): Result<UserProfile> {
        return try {
            val currentAuth = auth ?: return Result.failure(IllegalStateException("Firebase is not initialized."))
            _authState.value = AuthState.Loading
            val authResult = currentAuth.signInWithEmailAndPassword(email.trim(), password).awaitTask()
            val user = authResult.user ?: throw IllegalStateException("Firebase user is null after email sign in")

            val profile = fetchExistingUserProfile(user)
            if (profile != null) {
                Log.i("TRAC_AUTH", "AUTH_SUCCESS ACCOUNT_FOUND uid=${user.uid}")
                _currentUserProfile.value = profile
                _authState.value = AuthState.Authenticated(profile)
                Result.success(profile)
            } else {
                Log.w("TRAC_AUTH", "AUTH_SUCCESS ACCOUNT_NOT_FOUND uid=${user.uid} -> NAVIGATION_BLOCKED")
                currentAuth.signOut()
                _currentUserProfile.value = null
                _authState.value = AuthState.Unauthenticated("No account found")
                Result.failure(IllegalStateException("No account found"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Email Sign-In failed: ${e.message}", e)
            _authState.value = AuthState.Error(e.message ?: "Email Sign-In failed")
            Result.failure(e)
        }
    }

    override suspend fun signUpWithEmail(email: String, password: String): Result<UserProfile> {
        _authState.value = AuthState.Error("New accounts must be created using phone OTP.")
        return Result.failure(IllegalStateException("New accounts must be created using phone OTP."))
    }

    override fun sendPhoneOtp(
        activity: Activity,
        phoneNumber: String,
        onCodeSent: (verificationId: String) -> Unit,
        onError: (message: String) -> Unit,
        onAutoVerified: (userProfile: UserProfile) -> Unit
    ) {
        val currentAuth = auth
        if (currentAuth == null) {
            onError("Firebase Auth is not initialized.")
            return
        }

        // Canonical phone normalization
        val formattedNumber = normalizePhoneNumber(phoneNumber)
        if (formattedNumber.isBlank()) {
            val errMsg = "The phone number is too short. Please enter a valid 10-digit mobile number."
            _authState.value = AuthState.Error(errMsg)
            onError(errMsg)
            return
        }

        Log.i("TRAC_AUTH", "OTP_SEND_START phoneLength=${formattedNumber.length}")
        _authState.value = AuthState.Loading

        var callbacksHandled = false
        val watchdogJob = scope.launch {
            kotlinx.coroutines.delay(15000L)
            if (!callbacksHandled) {
                callbacksHandled = true
                Log.w("TRAC_AUTH", "OTP_SEND_TIMEOUT")
                _authState.value = AuthState.Unauthenticated("Unable to send OTP. Please check your connection and try again.")
                onError("Unable to send OTP. Please check your connection and try again.")
            }
        }

        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                if (callbacksHandled) return
                callbacksHandled = true
                watchdogJob.cancel()
                Log.i("TRAC_AUTH", "OTP_VERIFICATION_COMPLETED")
                scope.launch {
                    try {
                        val authResult = currentAuth.signInWithCredential(credential).awaitTask()
                        val user = authResult.user ?: throw IllegalStateException("User null after verification")
                        val profile = fetchExistingUserProfile(user)
                        if (profile != null) {
                            Log.i("TRAC_AUTH", "AUTH_SUCCESS ACCOUNT_FOUND uid=${user.uid}")
                            _currentUserProfile.value = profile
                            _authState.value = AuthState.Authenticated(profile)
                            onAutoVerified(profile)
                        } else {
                            Log.w("TRAC_AUTH", "AUTH_SUCCESS ACCOUNT_NOT_FOUND uid=${user.uid} -> READY_FOR_REGISTRATION")
                            _currentUserProfile.value = null
                            _authState.value = AuthState.Unauthenticated("Account not registered")
                            onError("Account not registered")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Auto verification sign in failed: ${e.message}", e)
                        _authState.value = AuthState.Unauthenticated(e.message ?: "Verification failed")
                        onError(e.message ?: "Verification failed")
                    }
                }
            }

            override fun onVerificationFailed(e: FirebaseException) {
                if (callbacksHandled) return
                callbacksHandled = true
                watchdogJob.cancel()
                Log.e("TRAC_AUTH", "OTP_VERIFICATION_FAILED error=${e.javaClass.simpleName} msg=${e.message}")
                val msg = e.message ?: ""
                val userFriendlyMsg = when {
                    msg.contains("TOO_SHORT", ignoreCase = true) ||
                    msg.contains("format of the phone number", ignoreCase = true) ->
                        "Invalid phone number format. Please enter a valid 10-digit mobile number."
                    msg.contains("quota", ignoreCase = true) ->
                        "SMS quota exceeded. Please try again later or use Google / Email sign-in."
                    msg.contains("blocked", ignoreCase = true) ->
                        "Requests from this device are blocked. Please try again later."
                    else -> msg.ifBlank { "Phone verification failed" }
                }
                _authState.value = AuthState.Unauthenticated(userFriendlyMsg)
                onError(userFriendlyMsg)
            }

            override fun onCodeSent(
                verificationId: String,
                token: PhoneAuthProvider.ForceResendingToken
            ) {
                if (callbacksHandled) return
                callbacksHandled = true
                watchdogJob.cancel()
                Log.i("TRAC_AUTH", "OTP_CODE_SENT verificationIdLength=${verificationId.length}")
                _authState.value = AuthState.CodeSent(verificationId, formattedNumber)
                onCodeSent(verificationId)
            }

            override fun onCodeAutoRetrievalTimeOut(verificationId: String) {
                Log.i("TRAC_AUTH", "OTP_AUTO_RETRIEVAL_TIMEOUT verificationIdLength=${verificationId.length}")
            }
        }

        val options = PhoneAuthOptions.newBuilder(currentAuth)
            .setPhoneNumber(formattedNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(callbacks)
            .build()

        try {
            PhoneAuthProvider.verifyPhoneNumber(options)
        } catch (e: Exception) {
            watchdogJob.cancel()
            Log.e(TAG, "Exception calling verifyPhoneNumber: ${e.message}", e)
            val err = e.message ?: "Failed to send verification code"
            _authState.value = AuthState.Unauthenticated(err)
            onError(err)
        }
    }

    override suspend fun verifyPhoneOtp(verificationId: String, otpCode: String): Result<UserProfile> {
        return try {
            val currentAuth = auth ?: return Result.failure(IllegalStateException("Firebase not initialized."))

            _authState.value = AuthState.Loading

            val credential = PhoneAuthProvider.getCredential(verificationId, otpCode)
            val authResult = currentAuth.signInWithCredential(credential).awaitTask()
            val user = authResult.user ?: throw IllegalStateException("User is null after OTP verification")

            val profile = fetchExistingUserProfile(user)
            if (profile != null) {
                Log.i("TRAC_AUTH", "AUTH_SUCCESS ACCOUNT_FOUND uid=${user.uid}")
                _currentUserProfile.value = profile
                _authState.value = AuthState.Authenticated(profile)
                Result.success(profile)
            } else {
                Log.w("TRAC_AUTH", "AUTH_SUCCESS ACCOUNT_NOT_FOUND uid=${user.uid} -> PROMPT_REGISTRATION")
                // Keep Firebase Auth session active so user can register account in Firestore
                _currentUserProfile.value = null
                _authState.value = AuthState.Unauthenticated("Account not registered")
                Result.failure(IllegalStateException("Account not registered"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "OTP verification failed: ${e.message}", e)
            val rawMsg = e.message ?: "Invalid OTP Code"
            val userMsg = when {
                rawMsg.contains("Unable to connect", ignoreCase = true) ||
                rawMsg.contains("network", ignoreCase = true) ||
                rawMsg.contains("unavailable", ignoreCase = true) ||
                rawMsg.contains("deadline", ignoreCase = true) ->
                    "Unable to connect. Check your internet connection and try again."
                rawMsg.contains("Access denied", ignoreCase = true) ||
                rawMsg.contains("PERMISSION_DENIED", ignoreCase = true) ->
                    "Access denied. Please check your permissions and try again."
                rawMsg.contains("Account not registered", ignoreCase = true) ->
                    "Account not registered"
                else -> rawMsg
            }
            _authState.value = AuthState.Error(userMsg)
            Result.failure(IllegalStateException(userMsg, e))
        }
    }

    override suspend fun signOut() {
        authEpoch.incrementAndGet()
        profileRefresh?.cancel()
        try {
            context.getSharedPreferences("verified_auth_profile", Context.MODE_PRIVATE).edit().clear().commit()
            context.getSharedPreferences("verified_workspace_session", Context.MODE_PRIVATE).edit().clear().commit()
            auth?.signOut()
            _currentUserProfile.value = null
            _authState.value = AuthState.Unauthenticated()
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out: ${e.message}", e)
        }
    }

    override fun setAuthenticatedProfile(profile: UserProfile) {
        require(profile.uid == currentUid && profile.uid.isNotBlank()) { "Authenticated profile identity mismatch" }
        _currentUserProfile.value = profile
        _authState.value = AuthState.Authenticated(profile)
    }

    /**
     * Resolves the AIDHUNT application account after Firebase authentication establishes Firebase UID:
     * 1. Existing users/{firebaseUid}
     * 2. Existing phoneDirectory mapping/invitation that can safely bind the newly authenticated UID
     * 3. Existing workspace membership relationship (userWorkspaceMemberships or pending partner links)
     * 4. Pre-provisioned test account for configured Firebase test phone (Step 8)
     *
     * If none exists: returns null -> caller signs out Firebase user and reports "Account not registered".
     * Does NOT silently create an Owner account for unknown phones, does NOT create a workspace for unknown phones.
     */
    private fun cachedVerifiedProfile(uid: String): UserProfile? {
        if (auth?.currentUser?.uid != uid) return null
        val session = context.getSharedPreferences("verified_workspace_session", Context.MODE_PRIVATE)
        if (session.getString("uid", "") != uid || session.getString("workspaceId", "").isNullOrBlank()) return null
        val prefs = context.getSharedPreferences("verified_auth_profile", Context.MODE_PRIVATE)
        if (prefs.getString("uid", "") != uid) return null
        val json = runCatching { prefs.getString("profile", null)?.let { org.json.JSONObject(it) } }.getOrNull() ?: return null
        val data = mutableMapOf<String, Any?>()
        json.keys().forEach { key -> val value = json.opt(key); data[key] = if (value == org.json.JSONObject.NULL) null else value }
        return UserProfile.fromMap(data).copy(uid = uid, defaultWorkspaceId = session.getString("workspaceId", ""), role = null)
    }

    private suspend fun fetchExistingUserProfile(user: FirebaseUser): UserProfile? {
        val uid = user.uid
        val db = firestore ?: return cachedVerifiedProfile(uid)
        return try {
            val snapshot = db.collection("users").document(uid).get(com.google.firebase.firestore.Source.SERVER).awaitTask()
            val profile = if (snapshot.exists()) {
                UserProfile.fromMap(snapshot.data.orEmpty()).copy(uid = uid, phoneNumber = user.phoneNumber, role = null)
            } else {
                // Authentication succeeds without creating or authorizing any workspace.
                UserProfile(uid = uid, displayName = user.displayName, phoneNumber = user.phoneNumber)
            }
            if (auth?.currentUser?.uid != uid) throw kotlinx.coroutines.CancellationException("Authenticated account changed")
            // Authentication is independent of workspace authorization and build routing.
            val scopedProfile = profile
            context.getSharedPreferences("verified_auth_profile", Context.MODE_PRIVATE).edit()
                .putString("uid", uid).putString("profile", org.json.JSONObject(scopedProfile.toMap()).toString()).commit()
            scopedProfile
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
            if (e is SecurityException) {
                context.getSharedPreferences("verified_auth_profile", Context.MODE_PRIVATE).edit().clear().commit()
            }
            recoverVerifiedProfile(e) { cachedVerifiedProfile(uid) }
        }
    }

    private suspend fun registerPhoneDirectoryEntry(user: FirebaseUser) {
        val phone = user.phoneNumber
        if (phone.isNullOrBlank()) return
        val db = firestore ?: return
        val normalizedPhone = normalizePhoneNumber(phone)
        if (normalizedPhone.isBlank()) return
        val docData = mapOf(
            "uid" to user.uid,
            "phoneNumber" to normalizedPhone,
            "updatedAt" to System.currentTimeMillis()
        )
        try {
            db.collection("phoneDirectory").document(normalizedPhone)
                .set(docData, SetOptions.merge())
                .awaitTask()
            Log.d("TRAC_PHONE_DIRECTORY", "REGISTER phone=$normalizedPhone uid=${user.uid} SUCCESS")
        } catch (e: Exception) {
            Log.w("TRAC_PHONE_DIRECTORY", "REGISTER phone=$normalizedPhone uid=${user.uid} FAILED: ${e.message}")
        }
    }

    private fun getResourceString(context: Context, name: String): String? {
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        return if (id != 0) context.getString(id) else null
    }
}

/**
 * Extension helper to await Task results safely with coroutine cancellation.
 */
suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { continuation ->
    addOnCompleteListener { task ->
        if (task.isSuccessful) {
            continuation.resume(task.result)
        } else {
            continuation.resumeWithException(task.exception ?: RuntimeException("Task failed with unknown error"))
        }
    }
}
