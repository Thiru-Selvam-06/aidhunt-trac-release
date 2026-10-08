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

    private var authStateListener: FirebaseAuth.AuthStateListener? = null

    override fun startAuthStateListener() {
        val currentAuth = auth ?: run {
            _authState.value = AuthState.Unauthenticated("Firebase not initialized")
            return
        }

        if (authStateListener != null) return

        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user == null || user.isAnonymous) {
                _currentUserProfile.value = null
                _authState.value = AuthState.Unauthenticated()
                return@AuthStateListener
            }

            scope.launch {
                try {
                    val profile = withTimeout(30_000L) {
                        fetchExistingUserProfile(user)
                    }
                    if (profile != null) {
                        saveCachedUserProfile(profile)
                        Log.i("TRAC_AUTH", "AUTH_SUCCESS ACCOUNT_FOUND uid=${user.uid}")
                        _currentUserProfile.value = profile
                        _authState.value = AuthState.Authenticated(profile)
                    } else {
                        Log.w("TRAC_AUTH", "AUTH_SUCCESS ACCOUNT_NOT_FOUND uid=${user.uid} -> Using cached/fallback profile")
                        val cached = loadCachedUserProfile(user.uid)
                        val fallback = cached ?: UserProfile(
                            uid = user.uid,
                            displayName = user.displayName ?: "User",
                            email = user.email,
                            phoneNumber = user.phoneNumber ?: "",
                            defaultWorkspaceId = "ws_${user.uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}",
                            workspaces = listOf("ws_${user.uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"),
                            role = "owner"
                        )
                        _currentUserProfile.value = fallback
                        _authState.value = AuthState.Authenticated(fallback)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error checking user profile (offline fallback activated): ${e.message}")
                    val cached = loadCachedUserProfile(user.uid)
                    val fallback = cached ?: UserProfile(
                        uid = user.uid,
                        displayName = user.displayName ?: "User",
                        email = user.email,
                        phoneNumber = user.phoneNumber ?: "",
                        defaultWorkspaceId = "ws_${user.uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}",
                        workspaces = listOf("ws_${user.uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"),
                        role = "owner"
                    )
                    _currentUserProfile.value = fallback
                    _authState.value = AuthState.Authenticated(fallback)
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
        _authState.value = AuthState.Error("Account creation will be available soon.")
        return Result.failure(IllegalStateException("Account creation will be available soon."))
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
        try {
            auth?.signOut()
            _currentUserProfile.value = null
            _authState.value = AuthState.Unauthenticated()
        } catch (e: Exception) {
            Log.e(TAG, "Error signing out: ${e.message}", e)
        }
    }

    override fun setAuthenticatedProfile(profile: UserProfile) {
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
    private suspend fun fetchExistingUserProfile(user: FirebaseUser): UserProfile? {
        if (user.isAnonymous) return null
        val uid = user.uid
        val db = firestore ?: return null

        val userDocRef = db.collection("users").document(uid)

        return try {
            val rawPhone = user.phoneNumber ?: ""
            val normPhone = normalizePhoneNumber(rawPhone)

            // 0. MANDATORY RESOLUTION FLOW: Check for partner invitation/relationship first
            if (normPhone.isNotBlank()) {
                try {
                    val boundProfile = firestoreRepository.resolveAndBindPartnerInvitation(
                        userUid = uid,
                        rawPhone = normPhone,
                        userDisplayName = user.displayName
                    )
                    if (boundProfile != null) {
                        Log.i("TRAC_AUTH", "AUTH_PARTNER_BOUND uid=$uid phone=$normPhone ws=${boundProfile.defaultWorkspaceId}")
                        return boundProfile
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error resolving partner invitation in fetchExistingUserProfile: ${e.message}")
                }
            }

            // Possibility 1: Existing users/{firebaseUid}
            val snapshot = userDocRef.get().awaitTask()
            if (snapshot.exists() && snapshot.data != null) {
                val data = snapshot.data!!
                val existing = UserProfile.fromMap(data)
                val updateMap = mutableMapOf<String, Any?>(
                    "uid" to uid,
                    "updatedAt" to System.currentTimeMillis()
                )
                if (user.displayName != null) updateMap["displayName"] = user.displayName
                if (user.email != null) updateMap["email"] = user.email
                if (user.phoneNumber != null) updateMap["phoneNumber"] = user.phoneNumber
                if (user.photoUrl != null) updateMap["photoUrl"] = user.photoUrl.toString()
                userDocRef.set(updateMap, SetOptions.merge()).awaitTask()
                registerPhoneDirectoryEntry(user)
                Log.i("TRAC_AUTH", "Resolved account via existing users/$uid")
                return existing.copy(
                    updatedAt = System.currentTimeMillis(),
                    displayName = existing.displayName ?: user.displayName,
                    email = existing.email ?: user.email,
                    phoneNumber = existing.phoneNumber ?: user.phoneNumber,
                    photoUrl = existing.photoUrl ?: user.photoUrl?.toString()
                )
            }

            // Possibility 2: Existing phoneDirectory mapping/invitation that can safely bind the newly authenticated UID
            val clean10 = normPhone.filter { it.isDigit() }.takeLast(10)
            val phoneCandidates = listOfNotNull(
                normPhone.ifBlank { null },
                if (clean10.length == 10) clean10 else null,
                if (clean10.length == 10) "91$clean10" else null,
                if (clean10.length == 10) "+91$clean10" else null
            ).distinct()

            var foundPhoneDoc: com.google.firebase.firestore.DocumentSnapshot? = null
            var foundCandidateKey: String = ""

            for (candidate in phoneCandidates) {
                try {
                    val pDoc = db.collection("phoneDirectory").document(candidate).get().awaitTask()
                    if (pDoc.exists() && pDoc.data != null) {
                        foundPhoneDoc = pDoc
                        foundCandidateKey = candidate
                        break
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "phoneDirectory lookup check skipped for key $candidate: ${e.message}")
                }
            }

            if (foundPhoneDoc != null && foundPhoneDoc.data != null) {
                val phoneData = foundPhoneDoc.data!!
                val boundUid = phoneData["uid"] as? String

                // Check UID binding protection:
                // Do not overwrite an existing different Firebase UID that already has an active profile
                var canBind = true
                if (!boundUid.isNullOrBlank() && boundUid != uid) {
                    try {
                        val boundUserDoc = db.collection("users").document(boundUid).get().awaitTask()
                        if (boundUserDoc.exists()) {
                            Log.w(TAG, "phoneDirectory $foundCandidateKey has active existing UID: $boundUid, cannot overwrite")
                            canBind = false
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error checking bound user doc: ${e.message}")
                    }
                }

                if (canBind) {
                    val existingRole = (phoneData["role"] as? String)?.lowercase() ?: "partner"
                    val directoryWsId = (phoneData["defaultWorkspaceId"] as? String)?.ifBlank { null }
                        ?: (phoneData["workspaceId"] as? String)?.ifBlank { null }

                    // A non-Owner directory entry without a resolvable workspace must never fall
                    // back to a canonical self-workspace, which would provision a second Owner
                    // workspace for a Partner. Defer to the membership/pending resolution below.
                    if (directoryWsId.isNullOrBlank() && existingRole != "owner") {
                        Log.w(TAG, "phoneDirectory $foundCandidateKey role=$existingRole has no workspaceId; deferring to membership resolution")
                    } else {
                    val targetWsId = directoryWsId
                        ?: "ws_${uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"
                    val wsList = (phoneData["workspaces"] as? List<*>)?.filterIsInstance<String>()?.ifEmpty { null }
                        ?: listOf(targetWsId)
                    val displayName = (phoneData["displayName"] as? String)?.ifBlank { null }
                        ?: user.displayName?.ifBlank { null }
                        ?: if (existingRole == "owner") "Owner" else "Partner"

                    val newProfile = UserProfile(
                        uid = uid,
                        displayName = displayName,
                        email = user.email,
                        phoneNumber = normPhone,
                        photoUrl = user.photoUrl?.toString(),
                        defaultWorkspaceId = targetWsId,
                        workspaces = wsList,
                        role = existingRole,
                        createdAt = (phoneData["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    // create/link users/{firebaseUid} using the existing role/workspace data
                    val userMap = newProfile.toMap().toMutableMap()
                    userMap["role"] = existingRole
                    userDocRef.set(userMap, SetOptions.merge()).awaitTask()

                    // Bind UID in phoneDirectory under canonical normalizedPhone
                    val dirUpdate = mutableMapOf<String, Any>(
                        "uid" to uid,
                        "phoneNumber" to normPhone,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    if (phoneData["role"] != null) dirUpdate["role"] = phoneData["role"]!!
                    if (phoneData["defaultWorkspaceId"] != null) dirUpdate["defaultWorkspaceId"] = phoneData["defaultWorkspaceId"]!!
                    if (phoneData["workspaceId"] != null) dirUpdate["workspaceId"] = phoneData["workspaceId"]!!
                    if (phoneData["displayName"] != null) dirUpdate["displayName"] = phoneData["displayName"]!!
                    db.collection("phoneDirectory").document(normPhone).set(dirUpdate, SetOptions.merge()).awaitTask()

                    Log.i("TRAC_AUTH", "Bound phoneDirectory $normPhone to newly authenticated UID $uid (role=$existingRole, ws=$targetWsId)")
                    return newProfile
                    }
                }
            }

            // Possibility 3: Existing workspace membership relationship.
            // The membership index is authoritative for BOTH workspaceId AND role.
            // Read ALL memberships and prefer an active non-owner membership, because a
            // regression may have previously written a spurious owner membership.
            val membershipSnapshot = db.collection("userWorkspaceMemberships").document(uid)
                .collection("workspaces").get().awaitTask()
            if (!membershipSnapshot.isEmpty) {
                data class MembershipCandidate(val workspaceId: String, val role: String)
                val candidates = membershipSnapshot.documents.mapNotNull { mDoc ->
                    val wsId = mDoc.getString("workspaceId")?.ifBlank { null } ?: mDoc.id
                    val rawRole = mDoc.getString("role")?.trim()?.lowercase() ?: ""
                    val status = mDoc.getString("status")?.trim()?.lowercase() ?: "active"
                    if (wsId.isBlank() || (status.isNotBlank() && status != "active")) {
                        null
                    } else {
                        // The membership record must carry an explicit role. Default to partner
                        // rather than owner so ownership is never inferred from workspace context.
                        val resolvedRole = when {
                            rawRole.contains("owner", ignoreCase = true) -> "owner"
                            rawRole.contains("operator", ignoreCase = true) -> "operator"
                            else -> "partner"
                        }
                        MembershipCandidate(wsId, resolvedRole)
                    }
                }
                val preferred = candidates.firstOrNull { it.role != "owner" }
                    ?: candidates.firstOrNull()
                if (preferred != null) {
                    val displayName = user.displayName?.ifBlank { null }
                        ?: preferred.workspaceId.let { _ -> "Partner" }
                    val newProfile = UserProfile(
                        uid = uid,
                        displayName = displayName,
                        email = user.email,
                        phoneNumber = normPhone.ifBlank { user.phoneNumber },
                        photoUrl = user.photoUrl?.toString(),
                        defaultWorkspaceId = preferred.workspaceId,
                        workspaces = listOf(preferred.workspaceId),
                        role = preferred.role,
                        createdAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                    val userMap = newProfile.toMap().toMutableMap()
                    userMap["role"] = preferred.role
                    userDocRef.set(userMap, SetOptions.merge()).awaitTask()
                    registerPhoneDirectoryEntry(user)
                    Log.i(
                        "TRAC_AUTH",
                        "Resolved account via userWorkspaceMemberships for UID $uid " +
                            "ws=${preferred.workspaceId} role=${preferred.role} candidates=${candidates.size}"
                    )
                    return newProfile
                }
            }

            // Check pending partner invitations by normalized phone
            if (normPhone.isNotBlank()) {
                try {
                    val pendingQuery = db.collectionGroup("pendingPhones")
                        .whereEqualTo("normalizedPhone", normPhone)
                        .limit(1)
                        .get().awaitTask()
                    if (!pendingQuery.isEmpty) {
                        val pendingDoc = pendingQuery.documents.first()
                        val pendingWsId = pendingDoc.getString("groupId")
                            ?: pendingDoc.reference.parent.parent?.id
                            ?: ""
                        val pendingName = pendingDoc.getString("displayName")?.ifBlank { null }
                            ?: user.displayName?.ifBlank { null }
                            ?: "Partner"
                        val targetWsId = pendingWsId.ifBlank {
                            "ws_${uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"
                        }
                        val newProfile = UserProfile(
                            uid = uid,
                            displayName = pendingName,
                            email = user.email,
                            phoneNumber = normPhone,
                            photoUrl = user.photoUrl?.toString(),
                            defaultWorkspaceId = targetWsId,
                            workspaces = listOf(targetWsId),
                            role = "partner",
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                        val userMap = newProfile.toMap().toMutableMap()
                        userMap["role"] = "partner"
                        userDocRef.set(userMap, SetOptions.merge()).awaitTask()
                        registerPhoneDirectoryEntry(user)
                        Log.i("TRAC_AUTH", "Resolved account via pendingPhones invitation for UID $uid")
                        return newProfile
                    }
                } catch (pe: Exception) {
                    Log.w(TAG, "pendingPhones query check skipped: ${pe.message}")
                }
            }

            // If genuinely none exists:
            // -> show "Account not registered"
            // -> do NOT silently create an Owner account
            // -> do NOT create a workspace
            // -> do NOT grant application access
            Log.w(TAG, "No AIDHUNT Trac account found for uid: $uid, phone: ${user.phoneNumber}")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Firestore users/{uid} fetch error: ${e.message}", e)
            val msg = e.message ?: ""
            if (e is com.google.firebase.FirebaseNetworkException ||
                e is java.io.IOException ||
                e is java.net.UnknownHostException ||
                msg.contains("network", ignoreCase = true) ||
                msg.contains("unavailable", ignoreCase = true) ||
                msg.contains("deadline", ignoreCase = true)
            ) {
                throw IllegalStateException("Unable to connect. Check your internet connection and try again.", e)
            }
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                throw IllegalStateException("Access denied. Please check your permissions and try again.", e)
            }
            throw e
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

    private fun saveCachedUserProfile(profile: UserProfile) {
        try {
            val prefs = context.getSharedPreferences("trac_auth_prefs", Context.MODE_PRIVATE)
            val json = org.json.JSONObject(profile.toMap()).toString()
            prefs.edit().putString("cached_profile_${profile.uid}", json).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error caching user profile: ${e.message}")
        }
    }

    private fun loadCachedUserProfile(uid: String): UserProfile? {
        try {
            val prefs = context.getSharedPreferences("trac_auth_prefs", Context.MODE_PRIVATE)
            val json = prefs.getString("cached_profile_$uid", null)
            if (!json.isNullOrBlank()) {
                val jobj = org.json.JSONObject(json)
                val map = mutableMapOf<String, Any?>()
                val keys = jobj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = jobj.get(k)
                }
                return UserProfile.fromMap(map)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading cached user profile: ${e.message}")
        }
        return null
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
