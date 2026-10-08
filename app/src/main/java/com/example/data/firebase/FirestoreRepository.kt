package com.example.data.firebase

import android.content.Context
import android.util.Log
import com.example.data.database.AppDatabase
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.WorkTypeExtensionEntity
import com.example.data.util.IdGenerator
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class FirestoreRepository(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val TAG = "FirestoreRepository"

    private val db: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isEmpty()) {
                FirebaseApp.initializeApp(context)
            }
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Firestore: ${e.message}")
            null
        }
    }

    private val _currentWorkspace = MutableStateFlow<Workspace?>(null)
    val currentWorkspace: StateFlow<Workspace?> = _currentWorkspace.asStateFlow()

    // Real-time listener registrations per workspace (workspaceId -> Map of listener registrations)
    private val activeWorkspaceListeners = mutableMapOf<String, MutableList<ListenerRegistration>>()

    /**
     * Completes strict deterministic workspace bootstrap for an authenticated user.
     * Sequences:
     * 1. Load users/{uid}
     * 2. Resolve workspaceId (stored defaultWorkspaceId or canonical, or preResolvedWorkspaceId)
     * 3. Ensure workspaces/{workspaceId} exists
     * 4. Ensure workspaces/{workspaceId}/members/{uid} exists with explicit role
     * 5. Persist and AWAIT users/{uid}.defaultWorkspaceId = workspaceId (only if not pre-resolved)
     * 6. Read users/{uid} again and verify defaultWorkspaceId == workspaceId
     * 7. Return validated Workspace
     */
    suspend fun bootstrapWorkspaceForUser(
        user: UserProfile,
        preResolvedWorkspaceId: String? = null,
        preResolvedRole: String? = null
    ): Workspace {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val uid = user.uid
        if (uid.isBlank()) throw IllegalArgumentException("User UID is blank")

        val isPreResolved = preResolvedWorkspaceId != null && preResolvedWorkspaceId.isNotBlank()
        var currentOperation = "reading_users"
        try {
            // 1. Read users/{uid}
            Log.d("TRAC_WORKSPACE", "reading users/$uid")
            currentOperation = "reading_users/$uid"
            val userDoc = firestore.collection("users").document(uid).get().await()

            var workspaceId: String? = null
            var existingWorkspaces: List<String> = emptyList()
            var userData: Map<String, Any?> = emptyMap()

            if (userDoc.exists() && userDoc.data != null) {
                userData = userDoc.data!!
                val userFromCloud = UserProfile.fromMap(userData)
                val stored = userFromCloud.defaultWorkspaceId?.ifBlank { null }
                if (!stored.isNullOrBlank()) {
                    workspaceId = stored
                    Log.d("TRAC_WORKSPACE", "stored defaultWorkspaceId=$workspaceId")
                }
                existingWorkspaces = userFromCloud.workspaces.filter { it.isNotBlank() }
            }

            // 2. Resolve workspaceId: preResolved > stored > canonical
            val effectiveRole = preResolvedRole?.lowercase()
                ?: (userData["role"] as? String)?.lowercase()
                ?: user.role?.lowercase()
                ?: ""
            val isExplicitPartner = effectiveRole == "partner" || effectiveRole == "operator"

            if (isPreResolved) {
                workspaceId = preResolvedWorkspaceId
                Log.d("TRAC_WORKSPACE", "using preResolvedWorkspaceId=$workspaceId role=$effectiveRole")
            } else if (workspaceId.isNullOrBlank()) {
                val canonicalWsId = "ws_${uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"
                workspaceId = canonicalWsId
                Log.d("TRAC_WORKSPACE", "no default workspace, canonical=$canonicalWsId")
            }

            val now = System.currentTimeMillis()

            // 3. Ensure workspaces/{workspaceId} exists
            currentOperation = "creating_verifying_workspaces/$workspaceId"
            Log.d("TRAC_FIRESTORE", "creating/verifying workspaces/$workspaceId")
            val wsDocRef = firestore.collection("workspaces").document(workspaceId)
            val wsDoc = wsDocRef.get().await()
            val workspace: Workspace

            if (wsDoc.exists() && wsDoc.data != null) {
                val existing = Workspace.fromMap(wsDoc.data!!)
                workspace = existing.copy(
                    workspaceId = workspaceId,
                    ownerUid = existing.ownerUid.ifBlank { uid },
                    updatedAt = now
                )
            } else {
                // CRITICAL SAFETY CHECK: A Partner/Operator must NEVER create a new workspace.
                // If the workspace doesn't exist and the user is a Partner/Operator, their invitation
                // binding data may not be settled yet. Throw to trigger offline fallback.
                if (isExplicitPartner) {
                    val err = "Partner/Operator uid=$uid attempted to create workspace $workspaceId as owner — this is forbidden. Workspace must pre-exist."
                    Log.e("TRAC_FIRESTORE", err)
                    throw IllegalStateException(err)
                }
                val workspaceName = if (!user.displayName.isNullOrBlank()) {
                    "${user.displayName}'s Tractor Services"
                } else {
                    ""
                }
                workspace = Workspace(
                    workspaceId = workspaceId,
                    name = workspaceName,
                    ownerUid = uid,
                    createdAt = now,
                    updatedAt = now
                )
                wsDocRef.set(workspace.toMap(), SetOptions.merge()).await()
            }

            // Verify workspaces/{workspaceId}
            val verifiedWs = wsDocRef.get().await()
            if (!verifiedWs.exists()) {
                val err = "Verification failed: workspaces/$workspaceId does not exist after write"
                Log.e("TRAC_FIRESTORE", "FAILED operation=$currentOperation message=$err")
                throw IllegalStateException(err)
            }
            Log.d("TRAC_FIRESTORE", "verified workspaces/$workspaceId exists")

            // 4. Ensure workspaces/{workspaceId}/members/{uid} exists with EXPLICIT role
            currentOperation = "creating_verifying_workspaces/$workspaceId/members/$uid"
            Log.d("TRAC_FIRESTORE", "creating/verifying workspaces/$workspaceId/members/$uid role=$effectiveRole")
            // isOwner: workspace owner must be this uid AND this user must not have an explicit Partner/Operator role
            val isOwner = (workspace.ownerUid == uid) && !isExplicitPartner
            Log.i("TRAC_ROLE", "bootstrapWorkspaceForUser uid=$uid wsId=$workspaceId wsOwnerUid=${workspace.ownerUid} userRole=$effectiveRole → isOwner=$isOwner")
            val memberDocRef = wsDocRef.collection("members").document(uid)
            val memberDoc = memberDocRef.get().await()

            val membershipRole = if (isOwner) "owner" else effectiveRole
            if (membershipRole.isBlank()) {
                throw IllegalStateException("Cannot create membership: no explicit role resolved for uid=$uid ws=$workspaceId")
            }

            if (!isOwner && memberDoc.exists()) {
                Log.d("TRAC_FIRESTORE", "Partner/Operator member doc already exists, skipping write to respect permissions")
            } else {
                val member = if (memberDoc.exists() && memberDoc.data != null) {
                    WorkspaceMember.fromMap(memberDoc.data!!).copy(role = membershipRole)
                } else {
                    WorkspaceMember(
                        uid = uid,
                        role = membershipRole,
                        status = "active",
                        joinedAt = now,
                        displayName = user.displayName ?: (userData["displayName"] as? String),
                        email = user.email ?: (userData["email"] as? String),
                        phoneNumber = user.phoneNumber ?: (userData["phoneNumber"] as? String)
                    )
                }
                memberDocRef.set(member.toMap(), SetOptions.merge()).await()
            }

            // Ensure owner's collaboration group exists: ONLY if this user is the actual Owner
            if (isOwner) {
                ensureCollaborationGroup(workspaceId, uid)
            }

            // 5. Persist and AWAIT: users/{uid}.defaultWorkspaceId = workspaceId
            // ONLY if NOT pre-resolved (i.e., user is a true new Owner creating their first workspace)
            if (!isPreResolved) {
                currentOperation = "updating_users/$uid.defaultWorkspaceId=$workspaceId"
                Log.d("TRAC_FIRESTORE", "updating users/$uid.defaultWorkspaceId=$workspaceId (new Owner)")
                val updatedWorkspaces = (existingWorkspaces + workspaceId).distinct()
                val userUpdateMap = mutableMapOf<String, Any?>(
                    "uid" to uid,
                    "defaultWorkspaceId" to workspaceId,
                    "role" to if (isOwner) "owner" else effectiveRole,
                    "workspaces" to updatedWorkspaces,
                    "updatedAt" to now
                )
                val displayName = user.displayName ?: (userData["displayName"] as? String)
                if (displayName != null) userUpdateMap["displayName"] = displayName
                val email = user.email ?: (userData["email"] as? String)
                if (email != null) userUpdateMap["email"] = email
                val phone = user.phoneNumber ?: (userData["phoneNumber"] as? String)
                if (phone != null) userUpdateMap["phoneNumber"] = phone
                val photo = user.photoUrl ?: (userData["photoUrl"] as? String)
                if (photo != null) userUpdateMap["photoUrl"] = photo

                firestore.collection("users").document(uid)
                    .set(userUpdateMap, SetOptions.merge())
                    .await()

                // 6. READ users/{uid} again from Firestore and verify defaultWorkspaceId == workspaceId
                currentOperation = "verifying_users/$uid.defaultWorkspaceId"
                val verifyDoc = firestore.collection("users").document(uid).get().await()
                val verifiedDefaultWs = verifyDoc.getString("defaultWorkspaceId")
                if (verifiedDefaultWs != workspaceId) {
                    val err = "Verification failed: expected defaultWorkspaceId=$workspaceId, but got $verifiedDefaultWs"
                    Log.e("TRAC_FIRESTORE", "FAILED operation=$currentOperation code=VERIFICATION_FAILED message=$err")
                    throw IllegalStateException(err)
                }
            } else {
                Log.d("TRAC_FIRESTORE", "Skipping users/{uid}.defaultWorkspaceId write — user joining existing workspace (pre-resolved)")
            }

            Log.d("TRAC_FIRESTORE", "user workspace bootstrap SUCCESS")
            _currentWorkspace.value = workspace
            return workspace
        } catch (e: Exception) {
            val code = (e as? FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED operation=$currentOperation code=$code message=${e.message}", e)
            throw e
        }
    }

    /**
     * Authoritative workspace membership for a user.
     * Both workspaceId (WHERE the user belongs) and role (WHAT the user may do) come from the
     * membership record workspaces/{workspaceId}/members/{uid}. Workspace ownership is never
     * used to infer a role.
     */
    data class AuthoritativeMembership(
        val workspaceId: String,
        val role: String,
        val status: String
    )

    /**
     * Reads the authoritative membership role from workspaces/{workspaceId}/members/{uid}.
     * Returns null when the document is missing or carries no explicit role.
     */
    suspend fun resolveAuthoritativeRole(uid: String, workspaceId: String): String? {
        val firestore = db ?: return null
        if (uid.isBlank() || workspaceId.isBlank()) return null
        return try {
            val memberDoc = firestore.collection("workspaces").document(workspaceId)
                .collection("members").document(uid).get().await()
            if (!memberDoc.exists()) return null
            val role = memberDoc.getString("role")?.trim()?.lowercase() ?: return null
            if (role.isBlank()) return null
            when {
                role.contains("owner", ignoreCase = true) -> "owner"
                role.contains("operator", ignoreCase = true) -> "operator"
                role.contains("partner", ignoreCase = true) -> "partner"
                else -> "partner"
            }
        } catch (e: Exception) {
            Log.w(TAG, "resolveAuthoritativeRole failed for uid=$uid ws=$workspaceId: ${e.message}")
            null
        }
    }

    /**
     * Discovers the user's active workspace membership so initialization can bind to an
     * existing Owner workspace instead of provisioning a new one.
     *
     * Resolution order:
     *  1. userWorkspaceMemberships/{uid}/workspaces (explicit role, written by the Owner)
     *  2. userCollaborationGroups/{uid}/groups
     *  3. workspaces/{workspaceId}/members/{uid} for candidate workspaces discovered from the
     *     user's stored workspace list
     *
     * A non-owner membership always wins over an owner membership, because an owner
     * membership in a self-provisioned ws_{uid} workspace is a regression artifact.
     */
    suspend fun resolveAuthoritativeMembership(uid: String): AuthoritativeMembership? {
        val firestore = db ?: return null
        if (uid.isBlank()) return null

        fun normalizeRole(raw: String?): String {
            val role = raw?.trim()?.lowercase() ?: ""
            return when {
                role.contains("owner", ignoreCase = true) -> "owner"
                role.contains("operator", ignoreCase = true) -> "operator"
                else -> "partner"
            }
        }

        val candidates = LinkedHashMap<String, AuthoritativeMembership>()

        // 1. userWorkspaceMemberships discovery index — the Owner's authoritative invite record.
        try {
            val snap = firestore.collection("userWorkspaceMemberships").document(uid)
                .collection("workspaces").get().await()
            for (doc in snap.documents) {
                val wsId = doc.getString("workspaceId")?.ifBlank { null } ?: doc.id
                val status = doc.getString("status")?.trim()?.lowercase() ?: "active"
                val ownerUid = doc.getString("ownerUid")?.trim() ?: ""
                if (wsId.isBlank()) continue
                if (status.isNotBlank() && status != "active") continue
                // Ignore a self-referential "membership" that just points at the user's own UID.
                if (ownerUid.isNotBlank() && ownerUid == uid) continue
                candidates[wsId] = AuthoritativeMembership(wsId, normalizeRole(doc.getString("role")), status)
            }
        } catch (e: Exception) {
            Log.w(TAG, "userWorkspaceMemberships lookup failed for uid=$uid: ${e.message}")
        }

        // 2. userCollaborationGroups discovery index.
        try {
            val snap = firestore.collection("userCollaborationGroups").document(uid)
                .collection("groups").get().await()
            for (doc in snap.documents) {
                val wsId = doc.getString("groupId")?.ifBlank { null }
                    ?: doc.getString("ownerWorkspaceId")?.ifBlank { null }
                    ?: doc.id
                val status = doc.getString("status")?.trim()?.lowercase() ?: "active"
                val ownerUid = doc.getString("ownerUid")?.trim() ?: ""
                if (wsId.isBlank()) continue
                if (status.isNotBlank() && status != "active") continue
                if (ownerUid.isNotBlank() && ownerUid == uid) continue
                if (candidates.containsKey(wsId)) continue
                candidates[wsId] = AuthoritativeMembership(wsId, normalizeRole(doc.getString("role")), status)
            }
        } catch (e: Exception) {
            Log.w(TAG, "userCollaborationGroups lookup failed for uid=$uid: ${e.message}")
        }

        // 3. Direct workspaces/{workspaceId}/members/{uid} verification for candidate workspaces:
        //    - Known workspaces stored on disk
        //    - workspaces listed on the user's document
        try {
            val candidateWorkspaceIds = mutableSetOf<String>()
            candidateWorkspaceIds.addAll(getAllKnownWorkspaceIdsFromDisk())
            try {
                val userDoc = firestore.collection("users").document(uid).get().await()
                if (userDoc.exists() && userDoc.data != null) {
                    val defWs = userDoc.getString("defaultWorkspaceId")?.ifBlank { null }
                    if (defWs != null) candidateWorkspaceIds.add(defWs)
                    val wsList = (userDoc.get("workspaces") as? List<*>)?.filterIsInstance<String>()
                    if (wsList != null) candidateWorkspaceIds.addAll(wsList)
                }
            } catch (ue: Exception) {
                Log.w(TAG, "User doc lookup failed during candidate discovery for uid=$uid: ${ue.message}")
            }

            for (cWsId in candidateWorkspaceIds) {
                if (cWsId.isBlank() || candidates.containsKey(cWsId)) continue
                val isSelfCanonical = cWsId.startsWith("ws_${uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16)}")
                try {
                    val memberDoc = firestore.collection("workspaces").document(cWsId)
                        .collection("members").document(uid).get().await()
                    if (memberDoc.exists() && memberDoc.data != null) {
                        val status = memberDoc.getString("status")?.trim()?.lowercase() ?: "active"
                        if (status.isNotBlank() && status != "active") continue
                        val role = normalizeRole(memberDoc.getString("role"))
                        if (isSelfCanonical && role == "owner") continue
                        candidates[cWsId] = AuthoritativeMembership(cWsId, role, status)
                        Log.i("TRAC_ROLE", "Found direct workspace member doc: ws=$cWsId uid=$uid role=$role")
                    }
                } catch (me: Exception) {
                    Log.w(TAG, "Direct member lookup failed for ws=$cWsId uid=$uid: ${me.message}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Candidate workspace member verification failed for uid=$uid: ${e.message}")
        }

        if (candidates.isEmpty()) {
            Log.d(TAG, "resolveAuthoritativeMembership: no shared membership found for uid=$uid")
            return null
        }

        // Prefer a non-owner membership; fall back to the only owner membership (true Owner).
        val preferred = candidates.values.firstOrNull { it.role != "owner" }
            ?: candidates.values.first()
        Log.i(
            "TRAC_ROLE",
            "resolveAuthoritativeMembership uid=$uid → ws=${preferred.workspaceId} role=${preferred.role} " +
                "all=${candidates.values.map { it.workspaceId to it.role }}"
        )
        return preferred
    }

    private suspend fun ensureCollaborationGroup(workspaceId: String, ownerUid: String) {
        val firestore = db ?: return
        try {
            val groupRef = firestore.collection("collaborationGroups").document(workspaceId)
            val groupSnap = groupRef.get().await()
            val now = System.currentTimeMillis()
            if (!groupSnap.exists()) {
                val group = CollaborationGroup(
                    groupId = workspaceId,
                    ownerUid = ownerUid,
                    ownerWorkspaceId = workspaceId,
                    createdAt = now,
                    updatedAt = now
                )
                groupRef.set(group.toMap(), SetOptions.merge()).await()
            }

            // Also ensure owner is registered as active member in the group
            val ownerMemberRef = groupRef.collection("members").document(ownerUid)
            val ownerMemberSnap = ownerMemberRef.get().await()
            if (!ownerMemberSnap.exists()) {
                val member = CollaborationGroupMember(
                    uid = ownerUid,
                    workspaceId = workspaceId,
                    role = "owner",
                    status = "active",
                    joinedAt = now
                )
                ownerMemberRef.set(member.toMap(), SetOptions.merge()).await()
            }

            // Also ensure user's discovery index exists
            val userGroupIndexRef = firestore.collection("userCollaborationGroups").document(ownerUid)
                .collection("groups").document(workspaceId)
            userGroupIndexRef.set(
                UserCollaborationGroupIndex(
                    groupId = workspaceId,
                    ownerUid = ownerUid,
                    ownerWorkspaceId = workspaceId,
                    role = "owner",
                    status = "active",
                    joinedAt = now
                ).toMap(),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "ensureCollaborationGroup deferred: ${e.message}")
        }
    }

    /**
     * Resolves an existing workspace or creates a canonical one for the user deterministically.
     */
    suspend fun resolveOrCreateWorkspace(user: UserProfile): Workspace? {
        return try {
            bootstrapWorkspaceForUser(user)
        } catch (e: Exception) {
            Log.e(TAG, "resolveOrCreateWorkspace failed: ${e.message}", e)
            null
        }
    }

    /**
     * Fetches settings from Firestore `workspaces/{workspaceId}/settings/main`.
     * If existing cloud settings are found, returns them so local Room cache can be updated.
     * If no settings exist in cloud yet, writes initial settings to cloud and returns them.
     */
    suspend fun fetchOrCreateWorkspaceSettings(
        workspaceId: String,
        uid: String,
        localSettings: AppSettingsEntity
    ): AppSettingsEntity {
        val firestore = db ?: return localSettings
        val settingsDocRef = firestore.collection("workspaces").document(workspaceId)
            .collection("settings").document("main")

        return try {
            val snapshot = settingsDocRef.get().await()
            if (snapshot.exists() && snapshot.data != null) {
                // Cloud settings exist: Merge with local entity
                val remoteData = snapshot.data!!
                val merged = appSettingsFromFirestoreMap(remoteData, localSettings)
                Log.d(TAG, "Loaded existing cloud settings for workspace: $workspaceId")
                merged
            } else {
                // No cloud settings exist yet: initialize with local settings
                Log.d(TAG, "Initializing first-time cloud settings for workspace: $workspaceId")
                settingsDocRef.set(localSettings.toFirestoreMap(uid), SetOptions.merge()).await()
                localSettings
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching/creating cloud settings: ${e.message}", e)
            localSettings
        }
    }

    /**
     * Registers real-time Firestore listeners for a set of visible workspaces.
     * Listens to entries, expenses, customers, tractors, attendees, withdrawals, settings for all given workspaces.
     */
    fun startRealtimeListenersForWorkspaces(
        workspaceIds: Set<String>,
        onJobsUpdated: (List<JobEntryEntity>, String) -> Unit,
        onExpensesUpdated: (List<ExpenseEntity>, String) -> Unit,
        onCustomersUpdated: (List<CustomerEntity>, String) -> Unit,
        onTractorsUpdated: (List<TractorEntity>, String) -> Unit,
        onPartnersUpdated: (List<PartnerEntity>, String) -> Unit,
        onWithdrawalsUpdated: (List<WithdrawalEntity>, String) -> Unit,
        onSettingsUpdated: (Map<String, Any?>, String) -> Unit,
        onPaymentsUpdated: (List<PaymentEntity>, String) -> Unit = { _, _ -> },
        onAccessControlUpdated: (Map<String, Any?>?, String) -> Unit = { _, _ -> },
        onPartnerPermissionsUpdated: (Map<String, Any?>?, String) -> Unit = { _, _ -> },
        onExtensionsUpdated: (List<WorkTypeExtensionEntity>, String) -> Unit = { _, _ -> }
    ) {
        val firestore = db ?: return
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        if (currentUid.isBlank()) {
            Log.d(TAG, "Skipping realtime Firestore listeners: user not authenticated")
            return
        }

        // Stop listeners for workspaces no longer in the set
        val existingWorkspaces = activeWorkspaceListeners.keys.toSet()
        val toRemove = existingWorkspaces - workspaceIds
        for (wsId in toRemove) {
            activeWorkspaceListeners.remove(wsId)?.forEach { it.remove() }
            Log.d("TRAC_FIRESTORE", "STOP_LISTEN workspace=$wsId")
        }

        // Add listeners for new workspaces
        val toAdd = workspaceIds - existingWorkspaces
        for (workspaceId in toAdd) {
            if (workspaceId.isBlank()) continue
            val wsRef = firestore.collection("workspaces").document(workspaceId)
            val regList = mutableListOf<ListenerRegistration>()

            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=entries")
            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=expenses")
            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=customers")
            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=tractors")
            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=attendees")
            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=withdrawals")
            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=settings")

            // 1. Entries Listener
            val regEntries = wsRef.collection("entries")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Entries listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=entries count=${snapshot.documents.size}")
                        val jobs = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { jobEntryFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                        }
                        onJobsUpdated(jobs, workspaceId)
                    }
                }
            regList.add(regEntries)

            // 2. Expenses Listener
            val regExpenses = wsRef.collection("expenses")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Expenses listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=expenses count=${snapshot.documents.size}")
                        val expenses = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { expenseFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                        }
                        onExpensesUpdated(expenses, workspaceId)
                    }
                }
            regList.add(regExpenses)

            // 3. Customers Listener
            val regCustomers = wsRef.collection("customers")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Customers listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=customers count=${snapshot.documents.size}")
                        val customers = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { customerFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                        }
                        onCustomersUpdated(customers, workspaceId)
                    }
                }
            regList.add(regCustomers)

            // 4. Tractors Listener
            val regTractors = wsRef.collection("tractors")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Tractors listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=tractors count=${snapshot.documents.size}")
                        val tractors = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { tractorFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                        }
                        onTractorsUpdated(tractors, workspaceId)
                    }
                }
            regList.add(regTractors)

            // 5. Attendees (Partners / Operators) Listener
            val regAttendees = wsRef.collection("attendees")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Attendees listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=attendees count=${snapshot.documents.size}")
                        val partners = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { partnerFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                        }
                        onPartnersUpdated(partners, workspaceId)
                    }
                }
            regList.add(regAttendees)

            // 6. Withdrawals Listener
            val regWithdrawals = wsRef.collection("withdrawals")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Withdrawals listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=withdrawals count=${snapshot.documents.size}")
                        val withdrawals = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { withdrawalFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                        }
                        onWithdrawalsUpdated(withdrawals, workspaceId)
                    }
                }
            regList.add(regWithdrawals)

            // 7. Settings Listener
            val regSettings = wsRef.collection("settings").document("main")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Settings listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null && snapshot.exists()) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=settings exists=true")
                        snapshot.data?.let { onSettingsUpdated(it, workspaceId) }
                    }
                }
            regList.add(regSettings)

            // 8. Payments Listener
            val regPayments = wsRef.collection("payments")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Payments listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=payments count=${snapshot.documents.size}")
                        val payments = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { paymentFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                        }
                        onPaymentsUpdated(payments, workspaceId)
                    }
                }
            regList.add(regPayments)

            // 9. Access Control Listener: workspaces/{workspaceId}/settings/access_control
            val regAccessControl = wsRef.collection("settings").document("access_control")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "AccessControl listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId settings/access_control exists=${snapshot.exists()}")
                        onAccessControlUpdated(if (snapshot.exists()) snapshot.data else null, workspaceId)
                    }
                }
            regList.add(regAccessControl)

            // 10. Partner Permissions Listener: workspaces/{workspaceId}/partnerPermissions
            val regPartnerPermissions = wsRef.collection("partnerPermissions")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "PartnerPermissions listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId partnerPermissions count=${snapshot.documents.size}")
                        snapshot.documents.forEach { doc ->
                            onPartnerPermissionsUpdated(doc.data, workspaceId)
                        }
                    }
                }
            regList.add(regPartnerPermissions)

            // 11. Extensions Listener: workspaces/{workspaceId}/extensions
            val regExtensions = wsRef.collection("extensions")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Extensions listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=extensions count=${snapshot.documents.size}")
                        val exts = snapshot.documents.mapNotNull { doc ->
                            doc.data?.let { extensionFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                        }
                        onExtensionsUpdated(exts, workspaceId)
                    }
                }
            regList.add(regExtensions)

            activeWorkspaceListeners[workspaceId] = regList
            Log.d(TAG, "All Firestore snapshot listeners started for workspace: $workspaceId")
        }
    }

    suspend fun savePartnerPermissions(
        workspaceId: String,
        partnerKey: String,
        lockedPages: List<String>,
        ownerUid: String
    ) {
        val firestore = db ?: return
        val docData = mapOf(
            "workspaceId" to workspaceId,
            "partnerKey" to partnerKey,
            "ownerUid" to ownerUid,
            "lockedPages" to lockedPages,
            "updatedAt" to System.currentTimeMillis()
        )
        firestore.collection("workspaces").document(workspaceId)
            .collection("partnerPermissions").document(partnerKey)
            .set(docData, com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    suspend fun recordAuditEvent(workspaceId: String, event: com.example.data.sync.AuditEvent) {
        val firestore = db ?: return
        firestore.collection("workspaces").document(workspaceId)
            .collection("auditEvents").document(event.eventId)
            .set(event.toMap(), com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    suspend fun updateLastSuccessfulSync(
        workspaceId: String,
        actorUid: String,
        actorName: String,
        actorRole: String
    ) {
        val firestore = db ?: return
        val now = System.currentTimeMillis()
        val data = mapOf(
            "workspaceId" to workspaceId,
            "lastSuccessfulSyncAt" to now,
            "lastSyncActorUid" to actorUid,
            "lastSyncActorName" to actorName,
            "lastSyncActorRole" to actorRole,
            "source" to "MOBILE_APP",
            "updatedAt" to now
        )
        firestore.collection("workspaces").document(workspaceId)
            .collection("settings").document("sync_status")
            .set(data, com.google.firebase.firestore.SetOptions.merge())
            .await()
    }

    /**
     * Backward compatibility wrapper for single workspace listener.
     */
    fun startRealtimeListeners(
        workspaceId: String,
        onJobsUpdated: (List<JobEntryEntity>, String) -> Unit,
        onExpensesUpdated: (List<ExpenseEntity>, String) -> Unit,
        onCustomersUpdated: (List<CustomerEntity>, String) -> Unit,
        onTractorsUpdated: (List<TractorEntity>, String) -> Unit,
        onPartnersUpdated: (List<PartnerEntity>, String) -> Unit,
        onWithdrawalsUpdated: (List<WithdrawalEntity>, String) -> Unit,
        onSettingsUpdated: (Map<String, Any?>, String) -> Unit,
        onPaymentsUpdated: (List<PaymentEntity>, String) -> Unit = { _, _ -> }
    ) {
        startRealtimeListenersForWorkspaces(
            workspaceIds = if (workspaceId.isBlank()) emptySet() else setOf(workspaceId),
            onJobsUpdated = onJobsUpdated,
            onExpensesUpdated = onExpensesUpdated,
            onCustomersUpdated = onCustomersUpdated,
            onTractorsUpdated = onTractorsUpdated,
            onPartnersUpdated = onPartnersUpdated,
            onWithdrawalsUpdated = onWithdrawalsUpdated,
            onSettingsUpdated = onSettingsUpdated,
            onPaymentsUpdated = onPaymentsUpdated
        )
    }

    /**
     * Cleanly stops and unregisters all real-time listeners across all workspaces.
     */
    fun stopRealtimeListeners() {
        activeWorkspaceListeners.values.forEach { list ->
            list.forEach { it.remove() }
        }
        activeWorkspaceListeners.clear()
        Log.d(TAG, "All Firestore snapshot listeners stopped.")
    }

    // --- Direct Cloud Operations ---

    suspend fun saveJobEntry(workspaceId: String, job: JobEntryEntity, uid: String?) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val docId = if (job.id > 0) job.id.toString() else IdGenerator.generateId().toString()
        val path = "workspaces/$workspaceId/entries/$docId"
        val currentAuthUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: uid
        Log.d("TRAC_PARTNER", "SHARED_WRITE authenticatedUid=$currentAuthUid workspace=$workspaceId entryId=$docId")
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$currentAuthUid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("entries").document(docId)
                .set(job.toFirestoreMap(currentAuthUid), SetOptions.merge())
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS entryId=$docId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun deleteJob(workspaceId: String, jobId: Long) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val path = "workspaces/$workspaceId/entries/$jobId"
        Log.d("TRAC_FIRESTORE", "deleting $path")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("entries").document(jobId.toString())
                .delete()
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS deleted entryId=$jobId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun saveExpense(workspaceId: String, expense: ExpenseEntity, uid: String?) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val docId = if (expense.id > 0) expense.id.toString() else IdGenerator.generateId().toString()
        val path = "workspaces/$workspaceId/expenses/$docId"
        val currentAuthUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: uid
        Log.d("TRAC_PARTNER", "SHARED_WRITE authenticatedUid=$currentAuthUid workspace=$workspaceId expenseId=$docId")
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$currentAuthUid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("expenses").document(docId)
                .set(expense.toFirestoreMap(currentAuthUid), SetOptions.merge())
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS expenseId=$docId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun deleteExpense(workspaceId: String, expenseId: Long) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val path = "workspaces/$workspaceId/expenses/$expenseId"
        Log.d("TRAC_FIRESTORE", "deleting $path")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("expenses").document(expenseId.toString())
                .delete()
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS deleted expenseId=$expenseId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun savePayment(workspaceId: String, payment: PaymentEntity, uid: String?) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val docId = if (payment.id > 0) payment.id.toString() else IdGenerator.generateId().toString()
        val path = "workspaces/$workspaceId/payments/$docId"
        val currentAuthUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: uid
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$currentAuthUid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("payments").document(docId)
                .set(payment.toFirestoreMap(), SetOptions.merge())
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS paymentId=$docId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun deletePayment(workspaceId: String, paymentId: Long) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val path = "workspaces/$workspaceId/payments/$paymentId"
        Log.d("TRAC_FIRESTORE", "deleting $path")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("payments").document(paymentId.toString())
                .delete()
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS deleted paymentId=$paymentId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun saveCustomer(workspaceId: String, customer: CustomerEntity, uid: String?) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val docId = if (customer.id > 0) customer.id.toString() else IdGenerator.generateId().toString()
        val path = "workspaces/$workspaceId/customers/$docId"
        val currentAuthUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: uid
        Log.d("TRAC_PARTNER", "SHARED_WRITE authenticatedUid=$currentAuthUid workspace=$workspaceId customerId=$docId")
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$currentAuthUid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("customers").document(docId)
                .set(customer.toFirestoreMap(currentAuthUid), SetOptions.merge())
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS customerId=$docId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun deleteCustomer(workspaceId: String, customerId: Long) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val path = "workspaces/$workspaceId/customers/$customerId"
        Log.d("TRAC_FIRESTORE", "deleting $path")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("customers").document(customerId.toString())
                .delete()
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS deleted customerId=$customerId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun saveTractor(workspaceId: String, tractor: TractorEntity, uid: String?) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val docId = if (tractor.id > 0) tractor.id.toString() else IdGenerator.generateId().toString()
        val path = "workspaces/$workspaceId/tractors/$docId"
        val currentAuthUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: uid
        Log.d("TRAC_PARTNER", "SHARED_WRITE authenticatedUid=$currentAuthUid workspace=$workspaceId tractorId=$docId")
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$currentAuthUid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("tractors").document(docId)
                .set(tractor.toFirestoreMap(currentAuthUid), SetOptions.merge())
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS tractorId=$docId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun deleteTractor(workspaceId: String, tractorId: Long) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val path = "workspaces/$workspaceId/tractors/$tractorId"
        Log.d("TRAC_FIRESTORE", "deleting $path")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("tractors").document(tractorId.toString())
                .delete()
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS deleted tractorId=$tractorId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun savePartner(workspaceId: String, partner: PartnerEntity, uid: String?) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val docId = if (partner.id > 0) partner.id.toString() else IdGenerator.generateId().toString()
        val path = "workspaces/$workspaceId/attendees/$docId"
        val currentAuthUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: uid
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$currentAuthUid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("attendees").document(docId)
                .set(partner.toFirestoreMap(currentAuthUid), SetOptions.merge())
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS partnerId=$docId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun deletePartner(workspaceId: String, partnerId: Long) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val path = "workspaces/$workspaceId/attendees/$partnerId"
        Log.d("TRAC_FIRESTORE", "deleting $path")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("attendees").document(partnerId.toString())
                .delete()
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS deleted partnerId=$partnerId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun saveExtension(workspaceId: String, extension: WorkTypeExtensionEntity, uid: String?) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val docId = if (extension.id > 0) extension.id.toString() else IdGenerator.generateId().toString()
        val path = "workspaces/$workspaceId/extensions/$docId"
        val currentAuthUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: uid
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$currentAuthUid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("extensions").document(docId)
                .set(extension.toFirestoreMap(currentAuthUid), SetOptions.merge())
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS extensionId=$docId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun deleteExtension(workspaceId: String, extensionId: Long) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val path = "workspaces/$workspaceId/extensions/$extensionId"
        Log.d("TRAC_FIRESTORE", "deleting $path")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("extensions").document(extensionId.toString())
                .delete()
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS deleted extensionId=$extensionId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun saveWithdrawal(workspaceId: String, withdrawal: WithdrawalEntity, uid: String?) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val docId = if (withdrawal.id > 0) withdrawal.id.toString() else IdGenerator.generateId().toString()
        val path = "workspaces/$workspaceId/withdrawals/$docId"
        val currentAuthUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: uid
        Log.d("TRAC_PARTNER", "SHARED_WRITE authenticatedUid=$currentAuthUid workspace=$workspaceId withdrawalId=$docId")
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$currentAuthUid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("withdrawals").document(docId)
                .set(withdrawal.toFirestoreMap(currentAuthUid), SetOptions.merge())
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS withdrawalId=$docId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun deleteWithdrawal(workspaceId: String, withdrawalId: Long) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val path = "workspaces/$workspaceId/withdrawals/$withdrawalId"
        Log.d("TRAC_FIRESTORE", "deleting $path")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("withdrawals").document(withdrawalId.toString())
                .delete()
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS deleted withdrawalId=$withdrawalId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun saveSettings(workspaceId: String, settings: AppSettingsEntity, uid: String?) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val path = "workspaces/$workspaceId/settings/main"
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$uid")
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("settings").document("main")
                .set(settings.toFirestoreMap(uid), SetOptions.merge())
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS settings written to $path")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun savePartnerPercentages(workspaceId: String, percentages: Map<String, Int>) {
        val firestore = db ?: return
        val path = "workspaces/$workspaceId/settings/main"
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("settings").document("main")
                .set(mapOf("partnerPercentages" to percentages, "updatedAt" to System.currentTimeMillis()), SetOptions.merge())
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS partnerPercentages written to $path: $percentages")
        } catch (e: Exception) {
            Log.w("TRAC_FIRESTORE", "Failed to write partnerPercentages to $path: ${e.message}")
        }
    }

    /**
     * Saves UID-based profit share allocations to Firestore.
     * Key: participantUid, Value: percentage (0-100)
     */
    suspend fun saveProfitShareAllocations(
        workspaceId: String,
        allocations: Map<String, Int>,
        participantDetails: Map<String, ProfitShareAllocation> = emptyMap()
    ) {
        val firestore = db ?: return
        val path = "workspaces/$workspaceId/settings/main"
        val now = System.currentTimeMillis()
        val allocationList = allocations.entries.map { (uid, percentage) ->
            val detail = participantDetails[uid]
            mapOf<String, Any>(
                "participantUid" to uid,
                "percentage" to percentage,
                "displayName" to (detail?.displayName ?: ""),
                "role" to (detail?.role ?: "partner"),
                "isActive" to (detail?.isActive ?: true)
            )
        }
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("settings").document("main")
                .set(
                    mapOf(
                        "profitShareAllocations" to allocationList,
                        "updatedAt" to now
                    ),
                    SetOptions.merge()
                )
                .await()
            Log.d("TRAC_FIRESTORE", "SUCCESS profitShareAllocations written to $path: $allocations")
        } catch (e: Exception) {
            Log.w("TRAC_FIRESTORE", "Failed to write profitShareAllocations to $path: ${e.message}")
        }
    }

    /**
     * Loads UID-based profit share allocations from Firestore.
     * Returns a map of participantUid -> percentage, and a map of participantUid -> ProfitShareAllocation for details.
     */
    suspend fun getProfitShareAllocations(workspaceId: String): Pair<Map<String, Int>, Map<String, ProfitShareAllocation>> {
        val firestore = db ?: return Pair(emptyMap<String, Int>(), emptyMap<String, ProfitShareAllocation>())
        val path = "workspaces/$workspaceId/settings/main"
        return try {
            val snapshot = firestore.collection("workspaces").document(workspaceId)
                .collection("settings").document("main")
                .get()
                .await()
            if (snapshot.exists() && snapshot.data != null) {
                val data = snapshot.data!!
                val rawList = data["profitShareAllocations"] as? List<*>
                if (rawList != null) {
                    val percentageMap = mutableMapOf<String, Int>()
                    val detailMap = mutableMapOf<String, ProfitShareAllocation>()
                    for (item in rawList) {
                        if (item is Map<*, *>) {
                            val uid = item["participantUid"] as? String ?: ""
                            val percentage = (item["percentage"] as? Number)?.toInt() ?: 0
                            if (uid.isNotBlank()) {
                                percentageMap[uid] = percentage
                                detailMap[uid] = ProfitShareAllocation(
                                    participantUid = uid,
                                    percentage = percentage,
                                    displayName = item["displayName"] as? String ?: "",
                                    role = item["role"] as? String ?: "partner",
                                    isActive = (item["isActive"] as? Boolean) ?: true
                                )
                            }
                        }
                    }
                    Log.d("TRAC_FIRESTORE", "Loaded profitShareAllocations from $path: $percentageMap")
                    Pair(percentageMap, detailMap)
                } else {
                    Pair(emptyMap(), emptyMap())
                }
            } else {
                Pair(emptyMap(), emptyMap())
            }
        } catch (e: Exception) {
            Log.w("TRAC_FIRESTORE", "Failed to load profitShareAllocations from $path: ${e.message}")
            Pair(emptyMap(), emptyMap())
        }
    }

    /**
     * Safe Migration: On first login with a new workspace, if local Room database has existing records,
     * upload them to the workspace without duplicating or overwriting newer cloud data.
     */
    suspend fun migrateLocalDataIfRequired(
        workspaceId: String,
        uid: String,
        database: AppDatabase
    ) {
        val firestore = db ?: return
        val prefs = context.getSharedPreferences("firestore_migration_prefs", Context.MODE_PRIVATE)
        val migrationKey = "migration_${uid}_${workspaceId}"
        if (prefs.getBoolean(migrationKey, false)) {
            Log.d(TAG, "Workspace $workspaceId for user $uid already migrated.")
            return
        }

        try {
            val wsRef = firestore.collection("workspaces").document(workspaceId)
            val entriesSnap = wsRef.collection("entries").limit(1).get().await()
            val customersSnap = wsRef.collection("customers").limit(1).get().await()

            // If workspace already has entries or customers in cloud, do not upload local default records
            if (!entriesSnap.isEmpty || !customersSnap.isEmpty) {
                prefs.edit().putBoolean(migrationKey, true).apply()
                Log.d(TAG, "Remote workspace already has cloud data. Local seed migration skipped.")
                return
            }

            Log.d(TAG, "Migrating local Room data to new workspace: $workspaceId")

            // Only retrieve records for this workspaceId
            val tractors = database.tractorDao().getTractorsForWorkspace(workspaceId).firstOrNull() ?: emptyList()
            val customers = database.customerDao().getCustomersForWorkspace(workspaceId).firstOrNull() ?: emptyList()
            val jobs = database.jobEntryDao().getJobsForWorkspace(workspaceId).firstOrNull() ?: emptyList()
            val expenses = database.expenseDao().getExpensesForWorkspace(workspaceId).firstOrNull() ?: emptyList()
            val withdrawals = database.withdrawalDao().getWithdrawalsForWorkspace(workspaceId).firstOrNull() ?: emptyList()
            val partners = database.partnerDao().getPartnersForWorkspace(workspaceId).firstOrNull() ?: emptyList()
            val currentSettings = database.appSettingsDao().getSettingsForWorkspaceOnce(workspaceId)

            val batch = firestore.batch()

            // 1. Tractors
            for (tractor in tractors) {
                val docId = if (tractor.id > 0) tractor.id.toString() else IdGenerator.generateId().toString()
                val scoped = tractor.copy(workspaceId = workspaceId)
                batch.set(wsRef.collection("tractors").document(docId), scoped.toFirestoreMap(uid), SetOptions.merge())
                database.tractorDao().insertTractor(scoped)
            }

            // 2. Customers
            for (customer in customers) {
                val docId = if (customer.id > 0) customer.id.toString() else IdGenerator.generateId().toString()
                val scoped = customer.copy(workspaceId = workspaceId)
                batch.set(wsRef.collection("customers").document(docId), scoped.toFirestoreMap(uid), SetOptions.merge())
                database.customerDao().insertCustomer(scoped)
            }

            // 3. Jobs
            for (job in jobs) {
                val docId = if (job.id > 0) job.id.toString() else IdGenerator.generateId().toString()
                val scoped = job.copy(workspaceId = workspaceId)
                batch.set(wsRef.collection("entries").document(docId), scoped.toFirestoreMap(uid), SetOptions.merge())
                database.jobEntryDao().insertJob(scoped)
            }

            // 4. Expenses
            for (expense in expenses) {
                val docId = if (expense.id > 0) expense.id.toString() else IdGenerator.generateId().toString()
                val scoped = expense.copy(workspaceId = workspaceId)
                batch.set(wsRef.collection("expenses").document(docId), scoped.toFirestoreMap(uid), SetOptions.merge())
                database.expenseDao().insertExpense(scoped)
            }

            // 5. Withdrawals
            for (withdrawal in withdrawals) {
                val docId = if (withdrawal.id > 0) withdrawal.id.toString() else IdGenerator.generateId().toString()
                val scoped = withdrawal.copy(workspaceId = workspaceId)
                batch.set(wsRef.collection("withdrawals").document(docId), scoped.toFirestoreMap(uid), SetOptions.merge())
                database.withdrawalDao().insertWithdrawal(scoped)
            }

            // 6. Partners / Attendees
            for (partner in partners) {
                val docId = if (partner.id > 0) partner.id.toString() else IdGenerator.generateId().toString()
                val scoped = partner.copy(workspaceId = workspaceId)
                batch.set(wsRef.collection("attendees").document(docId), scoped.toFirestoreMap(uid), SetOptions.merge())
                database.partnerDao().insertPartner(scoped)
            }

            // 7. Settings
            if (currentSettings != null) {
                val scopedSettings = currentSettings.copy(workspaceId = workspaceId)
                batch.set(
                    wsRef.collection("settings").document("main"),
                    scopedSettings.toFirestoreMap(uid),
                    SetOptions.merge()
                )
                database.appSettingsDao().insertOrUpdateSettings(scopedSettings)
            }

            batch.commit().await()
            prefs.edit().putBoolean(migrationKey, true).apply()
            Log.d(TAG, "Migration completed successfully for workspace: $workspaceId")
        } catch (e: Exception) {
            Log.e(TAG, "Error during migration: ${e.message}", e)
        }
    }

    // --- Direct Phone Account Directory & Direct Partner Membership ---

    suspend fun lookupPhoneInDirectory(rawPhone: String): String? {
        val firestore = db ?: return null
        val formatted = normalizePhoneNumber(rawPhone)
        if (formatted.isBlank()) return null
        val docPath = "phoneDirectory/$formatted"
        return try {
            val doc = firestore.collection("phoneDirectory").document(formatted).get().await()
            if (doc.exists() && doc.data != null) {
                val partnerUid = doc.getString("uid")
                if (!partnerUid.isNullOrBlank()) {
                    Log.d("TRAC_PARTNER", "PHONE_LOOKUP phone=$formatted uid=$partnerUid")
                    partnerUid
                } else {
                    Log.w("TRAC_PARTNER", "PHONE_LOOKUP phone=$formatted NOT_FOUND")
                    null
                }
            } else {
                Log.w("TRAC_PARTNER", "PHONE_LOOKUP phone=$formatted NOT_FOUND")
                null
            }
        } catch (e: Exception) {
            Log.e("TRAC_PARTNER", "PHONE_LOOKUP phone=$formatted FAILED: ${e.message}", e)
            null
        }
    }

    suspend fun checkPhoneAccountExists(rawPhone: String): AccountLookupResult {
        val firestore = db ?: return AccountLookupResult.Error("Firestore is not initialized")
        val formatted = normalizePhoneNumber(rawPhone)
        if (formatted.isBlank()) return AccountLookupResult.Error("Invalid phone number format")

        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        val wasUnauthenticated = (auth.currentUser == null)

        return try {
            if (wasUnauthenticated) {
                try {
                    auth.signInAnonymously().await()
                } catch (ae: Exception) {
                    Log.d("TRAC_AUTH", "Anonymous sign-in for lookup not available: ${ae.message}")
                }
            }

            try {
                // 1. Direct phone directory document lookup
                val doc = firestore.collection("phoneDirectory").document(formatted).get().await()
                if (doc.exists() && doc.data != null) {
                    val partnerUid = doc.getString("uid")
                    if (!partnerUid.isNullOrBlank()) {
                        Log.d("TRAC_AUTH", "Account found in phoneDirectory: phone=$formatted uid=$partnerUid")
                        return AccountLookupResult.Found(partnerUid, doc.getString("displayName"))
                    }
                }

                // 2. Query users collection where phoneNumber == formatted
                try {
                    val usersQuery = firestore.collection("users")
                        .whereEqualTo("phoneNumber", formatted)
                        .limit(1)
                        .get()
                        .await()
                    if (!usersQuery.isEmpty) {
                        val userDoc = usersQuery.documents.first()
                        Log.d("TRAC_AUTH", "Account found in users collection: phone=$formatted uid=${userDoc.id}")
                        return AccountLookupResult.Found(userDoc.id, userDoc.getString("displayName"))
                    }
                } catch (ue: Exception) {
                    Log.d("TRAC_AUTH", "users query skipped or denied: ${ue.message}")
                }

                Log.d("TRAC_AUTH", "No account found in Firestore for phone=$formatted")
                AccountLookupResult.NotFound
            } finally {
                if (wasUnauthenticated && auth.currentUser?.isAnonymous == true) {
                    try {
                        auth.signOut()
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e("TRAC_AUTH", "checkPhoneAccountExists failed: ${e.message}", e)
            val msg = e.message ?: ""
            if (e is com.google.firebase.FirebaseNetworkException ||
                e is java.net.UnknownHostException ||
                e is java.io.IOException ||
                msg.contains("network", ignoreCase = true) ||
                msg.contains("unavailable", ignoreCase = true) ||
                msg.contains("deadline", ignoreCase = true)
            ) {
                AccountLookupResult.Error("Unable to connect. Check your internet connection and try again.", isNetworkError = true)
            } else if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                Log.d("TRAC_AUTH", "Permission denied on phone lookup")
                AccountLookupResult.Error("Access denied. Please check your permissions and try again.")
            } else {
                AccountLookupResult.Error("Unable to check account. Please try again.")
            }
        }
    }

    suspend fun checkUserExists(uid: String): Boolean {
        val firestore = db ?: return false
        return try {
            val doc = firestore.collection("users").document(uid).get().await()
            doc.exists()
        } catch (e: Exception) {
            false
        }
    }

    suspend fun checkApplicationAccountExists(uid: String, rawPhone: String? = null): Boolean {
        val firestore = db ?: return false
        if (uid.isBlank()) return false
        return try {
            val userDoc = firestore.collection("users").document(uid).get().await()
            if (userDoc.exists() && userDoc.data != null) {
                return true
            }
            if (!rawPhone.isNullOrBlank()) {
                val formatted = normalizePhoneNumber(rawPhone)
                if (formatted.isNotBlank()) {
                    val phoneDoc = firestore.collection("phoneDirectory").document(formatted).get().await()
                    if (phoneDoc.exists()) {
                        return true
                    }
                }
            }
            val memberships = firestore.collection("userWorkspaceMemberships").document(uid)
                .collection("workspaces").limit(1).get().await()
            if (!memberships.isEmpty) {
                return true
            }
            false
        } catch (e: Exception) {
            Log.e("TRAC_AUTH", "checkApplicationAccountExists error: ${e.message}")
            false
        }
    }


    suspend fun addPartnerMemberDirectly(
        workspaceId: String,
        partnerUid: String,
        partnerName: String,
        partnerPhone: String,
        role: String,
        ownerUid: String,
        businessName: String
    ): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore is not initialized"))
        val now = System.currentTimeMillis()
        val formattedPhone = normalizePhoneNumber(partnerPhone)

        return try {
            val batch = firestore.batch()

            // 1. Create workspace member document: workspaces/{workspaceId}/members/{partnerUid}
            val memberRef = firestore.collection("workspaces").document(workspaceId)
                .collection("members").document(partnerUid)
            val member = WorkspaceMember(
                uid = partnerUid,
                role = role.ifBlank { "partner" },
                status = "active",
                phoneNumber = formattedPhone,
                joinedAt = now,
                addedByUid = ownerUid,
                invitedByUid = ownerUid,
                displayName = partnerName.ifBlank { "Partner" }
            )
            batch.set(memberRef, member.toMap(), SetOptions.merge())

            // 2. Create user workspace membership discovery index: userWorkspaceMemberships/{partnerUid}/workspaces/{workspaceId}
            val membershipIndexRef = firestore.collection("userWorkspaceMemberships").document(partnerUid)
                .collection("workspaces").document(workspaceId)
            val membershipData = mapOf(
                "workspaceId" to workspaceId,
                "ownerUid" to ownerUid,
                "role" to role.ifBlank { "partner" },
                "status" to "active",
                "joinedAt" to now,
                "workspaceName" to businessName.ifBlank { "AIDHUNT Tractor Fleet" }
            )
            batch.set(membershipIndexRef, membershipData, SetOptions.merge())

            // 3. Ensure PartnerEntity is created under workspace attendees for business/operator management
            val partnerAttendeeRef = firestore.collection("workspaces").document(workspaceId)
                .collection("attendees").document(partnerUid)
            val partnerAttendeeId = parseLongId(partnerUid)
            val partnerAttendee = PartnerEntity(
                id = partnerAttendeeId,
                workspaceId = workspaceId,
                name = partnerName.ifBlank { "Partner" },
                phone = formattedPhone,
                role = role.ifBlank { "Partner" },
                avatarColorHex = "#1E4D2B",
                isCurrentActive = false
            )
            batch.set(partnerAttendeeRef, partnerAttendee.toFirestoreMap(ownerUid), SetOptions.merge())

            batch.commit().await()
            Log.d("TRAC_PARTNER", "MEMBER_WRITE workspace=$workspaceId partnerUid=$partnerUid SUCCESS")
            Log.d("TRAC_PARTNER", "INDEX_WRITE workspace=$workspaceId partnerUid=$partnerUid SUCCESS")
            Result.success(Unit)
        } catch (e: Exception) {
            val code = (e as? FirebaseFirestoreException)?.code?.name ?: "ERROR"
            Log.e("TRAC_PARTNER", "MEMBER_WRITE workspace=$workspaceId partnerUid=$partnerUid FAILED code=$code message=${e.message}", e)
            Log.e("TRAC_PARTNER", "INDEX_WRITE workspace=$workspaceId partnerUid=$partnerUid FAILED code=$code message=${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getUserWorkspaceMemberships(uid: String): List<Map<String, Any?>> {
        val firestore = db ?: return emptyList()
        if (uid.isBlank()) return emptyList()

        return try {
            val snapshot = firestore.collection("userWorkspaceMemberships").document(uid)
                .collection("workspaces").get().await()
            val list = snapshot.documents.mapNotNull { it.data }
            for (item in list) {
                val wsId = item["workspaceId"] as? String ?: ""
                Log.d("TRAC_WORKSPACE", "MEMBERSHIP uid=$uid sharedWorkspace=$wsId")
            }
            list
        } catch (e: Exception) {
            Log.w("TRAC_WORKSPACE", "Error fetching userWorkspaceMemberships for $uid: ${e.message}")
            emptyList()
        }
    }

    fun listenToUserMemberships(uid: String, onWorkspacesChanged: (List<String>) -> Unit): ListenerRegistration? {
        val firestore = db ?: return null
        if (uid.isBlank()) return null

        return try {
            firestore.collection("userWorkspaceMemberships").document(uid)
                .collection("workspaces")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("TRAC_WORKSPACE", "Listener error on userWorkspaceMemberships: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val workspaceIds = snapshot.documents.map { it.id }.filter { it.isNotBlank() }
                        Log.d("TRAC_WORKSPACE", "Membership listener update for $uid: $workspaceIds")
                        onWorkspacesChanged(workspaceIds)
                    }
                }
        } catch (e: Exception) {
            Log.w("TRAC_WORKSPACE", "Failed to register membership listener: ${e.message}")
            null
        }
    }

    suspend fun removePartnerFromWorkspace(
        workspaceId: String,
        partnerUid: String?,
        partnerPhone: String
    ) {
        val firestore = db ?: return
        try {
            // 1. Remove member from workspace
            if (!partnerUid.isNullOrBlank()) {
                val memberRef = firestore.collection("workspaces").document(workspaceId)
                    .collection("members").document(partnerUid)
                memberRef.delete().await()

                // Delete userWorkspaceMemberships discovery index
                try {
                    firestore.collection("userWorkspaceMemberships").document(partnerUid)
                        .collection("workspaces").document(workspaceId).delete().await()
                } catch (e: Exception) {
                    Log.w("TRAC_PARTNER", "Error deleting membership index: ${e.message}")
                }
            }

            // 2. Remove partner entity from attendees collection
            val attendeesSnap = firestore.collection("workspaces").document(workspaceId)
                .collection("attendees").get().await()
            val cleanPhone = partnerPhone.filter { it.isDigit() }.takeLast(10)
            for (doc in attendeesSnap.documents) {
                val phone = doc.getString("phone") ?: ""
                val clean = phone.filter { it.isDigit() }.takeLast(10)
                if (doc.id == partnerUid || (cleanPhone.isNotBlank() && clean == cleanPhone)) {
                    doc.reference.delete().await()
                }
            }
            Log.d("TRAC_PARTNER", "Partner removed safely from $workspaceId")
        } catch (e: Exception) {
            Log.e("TRAC_PARTNER", "Error removing partner from workspace: ${e.message}", e)
        }
    }

    suspend fun getWorkspaceDetails(workspaceId: String): Workspace? {
        val firestore = db ?: return null
        return try {
            val doc = firestore.collection("workspaces").document(workspaceId).get().await()
            if (doc.exists() && doc.data != null) {
                Workspace.fromMap(doc.data!!)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun updateUserDefaultWorkspace(uid: String, workspaceId: String) {
        val firestore = db ?: return
        try {
            firestore.collection("users").document(uid).set(
                mapOf(
                    "defaultWorkspaceId" to workspaceId,
                    "updatedAt" to System.currentTimeMillis()
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "Error updating defaultWorkspaceId: ${e.message}")
        }
    }

    suspend fun getWorkspaceMembers(workspaceId: String): List<WorkspaceMember> {
        val firestore = db ?: return emptyList()
        return try {
            val snapshot = firestore.collection("workspaces").document(workspaceId)
                .collection("members")
                .get()
                .await()
            val list = mutableListOf<WorkspaceMember>()
            for (doc in snapshot.documents) {
                val data = doc.data ?: continue
                var member = WorkspaceMember.fromMap(data)
                if (member.displayName.isNullOrBlank() && member.uid.isNotBlank()) {
                    try {
                        val userDoc = firestore.collection("users").document(member.uid).get().await()
                        val uName = userDoc.getString("displayName")
                        if (!uName.isNullOrBlank()) {
                            member = member.copy(displayName = uName)
                        }
                    } catch (_: Exception) {}
                }
                list.add(member)
            }
            list
        } catch (e: Exception) {
            Log.w("TRAC_PARTNER", "Error getting members for $workspaceId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Result of repairing one user's regressed membership records.
     */
    data class MembershipRepairResult(
        val uid: String,
        val ownerWorkspaceId: String,
        val repairedRole: String,
        val selfWorkspaceId: String,
        val duplicateWorkspaceDeleted: Boolean,
        val details: List<String>
    )

    /**
     * DATA REPAIR PROTOCOL
     *
     * Repairs memberships broken by the workspace/role conflation regression, where an
     * authenticated Partner was provisioned into a self-owned ws_{uid} workspace with
     * role=owner instead of joining the real Owner's workspace with role=partner.
     *
     * For the given user:
     *  1. Find the authoritative Owner workspace (a membership whose ownerUid != this uid).
     *  2. Create/normalize workspaces/{ownerWorkspaceId}/members/{uid} with role = PARTNER,
     *     preserving the real Firebase UID and the Owner's ownerUid.
     *  3. Rewrite the discovery indexes to the Owner workspace with role = PARTNER.
     *  4. Point users/{uid}.defaultWorkspaceId at the Owner workspace.
     *  5. Delete the self-owned ws_{uid} workspace ONLY when it is confirmed to be an empty
     *     artifact of the bug. Any subcollection with data blocks deletion and is reported.
     *
     * Never deletes historical entries, customers, expenses or withdrawals.
     */
    suspend fun repairRegressedPartnerMembership(uid: String): MembershipRepairResult? {
        val firestore = db ?: return null
        if (uid.isBlank()) return null
        val details = mutableListOf<String>()

        val canonicalSelfWsId =
            "ws_${uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"

        // 1. Locate the authoritative Owner workspace from the discovery indexes.
        var ownerWorkspaceId = ""
        var invitedRole = "partner"
        try {
            val wsMemberships = firestore.collection("userWorkspaceMemberships").document(uid)
                .collection("workspaces").get().await()
            for (doc in wsMemberships.documents) {
                val wsId = doc.getString("workspaceId")?.ifBlank { null } ?: doc.id
                val ownerUid = doc.getString("ownerUid")?.trim() ?: ""
                if (wsId.isBlank() || ownerUid.isBlank() || ownerUid == uid) continue
                ownerWorkspaceId = wsId
                invitedRole = doc.getString("role")?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
                    ?: "partner"
                break
            }
        } catch (e: Exception) {
            details += "userWorkspaceMemberships read failed: ${e.message}"
        }

        if (ownerWorkspaceId.isBlank()) {
            try {
                val groups = firestore.collection("userCollaborationGroups").document(uid)
                    .collection("groups").get().await()
                for (doc in groups.documents) {
                    val wsId = doc.getString("groupId")?.ifBlank { null } ?: doc.id
                    val ownerUid = doc.getString("ownerUid")?.trim() ?: ""
                    if (wsId.isBlank() || ownerUid.isBlank() || ownerUid == uid) continue
                    ownerWorkspaceId = wsId
                    invitedRole = doc.getString("role")?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
                        ?: "partner"
                    break
                }
            } catch (e: Exception) {
                details += "userCollaborationGroups read failed: ${e.message}"
            }
        }

        if (ownerWorkspaceId.isBlank() || ownerWorkspaceId == canonicalSelfWsId) {
            Log.i("TRAC_REPAIR", "repairRegressedPartnerMembership uid=$uid: no Owner membership found, nothing to repair")
            return null
        }

        val repairedRole = if (invitedRole == "operator") "operator" else "partner"
        val now = System.currentTimeMillis()
        val wsRef = firestore.collection("workspaces").document(ownerWorkspaceId)

        // 2-4. Rewrite membership + indexes + profile to the Owner workspace with the explicit role.
        try {
            val existingMember = wsRef.collection("members").document(uid).get().await()
            val baseMember = if (existingMember.exists() && existingMember.data != null) {
                WorkspaceMember.fromMap(existingMember.data!!)
            } else {
                WorkspaceMember(uid = uid, joinedAt = now)
            }
            wsRef.collection("members").document(uid).set(
                baseMember.copy(
                    role = repairedRole,
                    status = "active"
                ).toMap(),
                SetOptions.merge()
            ).await()
            details += "membership role set to $repairedRole in $ownerWorkspaceId"

            firestore.collection("userWorkspaceMemberships").document(uid)
                .collection("workspaces").document(ownerWorkspaceId).set(
                    mapOf(
                        "workspaceId" to ownerWorkspaceId,
                        "ownerUid" to (wsRef.get().await().getString("ownerUid") ?: ""),
                        "role" to repairedRole,
                        "status" to "active",
                        "joinedAt" to now,
                        "updatedAt" to now
                    ),
                    SetOptions.merge()
                ).await()

            firestore.collection("userCollaborationGroups").document(uid)
                .collection("groups").document(ownerWorkspaceId).set(
                    mapOf(
                        "groupId" to ownerWorkspaceId,
                        "ownerUid" to (wsRef.get().await().getString("ownerUid") ?: ""),
                        "ownerWorkspaceId" to ownerWorkspaceId,
                        "role" to repairedRole,
                        "status" to "active",
                        "joinedAt" to now,
                        "updatedAt" to now
                    ),
                    SetOptions.merge()
                ).await()

            firestore.collection("users").document(uid).set(
                mapOf(
                    "uid" to uid,
                    "defaultWorkspaceId" to ownerWorkspaceId,
                    "workspaces" to listOf(ownerWorkspaceId),
                    "role" to repairedRole,
                    "updatedAt" to now
                ),
                SetOptions.merge()
            ).await()
            details += "profile repointed to $ownerWorkspaceId with role=$repairedRole"
        } catch (e: Exception) {
            details += "membership write failed: ${e.message}"
            Log.e("TRAC_REPAIR", "repairRegressedPartnerMembership write failed for uid=$uid: ${e.message}")
        }

        // 5. Remove the self-owned duplicate workspace ONLY when confirmed empty.
        var duplicateDeleted = false
        try {
            val selfWsRef = firestore.collection("workspaces").document(canonicalSelfWsId)
            val selfWs = selfWsRef.get().await()
            if (selfWs.exists() && selfWs.getString("ownerUid") == uid) {
                val dataSubcollections = listOf("entries", "expenses", "customers", "tractors", "withdrawals", "attendees", "payments")
                var totalDocs = 0
                for (sub in dataSubcollections) {
                    val snap = selfWsRef.collection(sub).get().await()
                    totalDocs += snap.size()
                }
                if (totalDocs == 0) {
                    for (sub in listOf("settings", "extensions", "members", "partnerPermissions")) {
                        try {
                            val snap = selfWsRef.collection(sub).get().await()
                            for (doc in snap.documents) doc.reference.delete().await()
                        } catch (_: Exception) {}
                    }
                    selfWsRef.delete().await()
                    duplicateDeleted = true
                    details += "deleted empty self-owned workspace $canonicalSelfWsId"
                } else {
                    details += "KEPT self-owned workspace $canonicalSelfWsId (contains $totalDocs records)"
                }
            } else {
                details += "no self-owned workspace $canonicalSelfWsId to clean"
            }
        } catch (e: Exception) {
            details += "duplicate cleanup failed: ${e.message}"
        }

        val result = MembershipRepairResult(
            uid = uid,
            ownerWorkspaceId = ownerWorkspaceId,
            repairedRole = repairedRole,
            selfWorkspaceId = canonicalSelfWsId,
            duplicateWorkspaceDeleted = duplicateDeleted,
            details = details
        )
        Log.i(
            "TRAC_REPAIR",
            "repairRegressedPartnerMembership uid=$uid → ws=$ownerWorkspaceId role=$repairedRole " +
                "duplicateDeleted=$duplicateDeleted details=$details"
        )
        return result
    }

    suspend fun updateMemberHourlyRate(workspaceId: String, memberUid: String, hourlyRate: Double) {
        val firestore = db ?: return
        try {
            firestore.collection("workspaces").document(workspaceId)
                .collection("members").document(memberUid)
                .set(
                    mapOf(
                        "defaultHourlyRate" to hourlyRate,
                        "hourlyRate" to hourlyRate,
                        "updatedAt" to System.currentTimeMillis()
                    ),
                    SetOptions.merge()
                ).await()
            Log.d("TRAC_HOURLY_RATE", "Updated member $memberUid hourly rate to $hourlyRate in workspace $workspaceId")
        } catch (e: Exception) {
            Log.w("TRAC_HOURLY_RATE", "Error updating member hourly rate in Firestore: ${e.message}")
        }
    }

    // --- Collaboration Groups & Multi-Workspace Discovery ---

    suspend fun getUserCollaborationGroupIds(uid: String): List<String> {
        val firestore = db ?: return emptyList()
        if (uid.isBlank()) return emptyList()

        return try {
            val snap = firestore.collection("userCollaborationGroups").document(uid)
                .collection("groups").get().await()
            snap.documents.map { it.id }.filter { it.isNotBlank() }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting userCollaborationGroupIds for $uid: ${e.message}")
            emptyList()
        }
    }

    fun listenToUserCollaborationGroups(uid: String, onGroupsChanged: (List<String>) -> Unit): ListenerRegistration? {
        val firestore = db ?: return null
        if (uid.isBlank()) return null

        return try {
            firestore.collection("userCollaborationGroups").document(uid)
                .collection("groups")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Error in userCollaborationGroups listener: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val groupIds = snapshot.documents.map { it.id }.filter { it.isNotBlank() }
                        onGroupsChanged(groupIds)
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register userCollaborationGroups listener: ${e.message}")
            null
        }
    }

    suspend fun getWorkspacesInCollaborationGroup(groupId: String): List<String> {
        val firestore = db ?: return listOf(groupId)
        if (groupId.isBlank()) return emptyList()

        return try {
            val snap = firestore.collection("collaborationGroups").document(groupId)
                .collection("members").get().await()
            val workspaceIds = snap.documents.mapNotNull { doc ->
                doc.getString("workspaceId")?.ifBlank { null }
            }.filter { it.isNotBlank() }
            if (workspaceIds.isNotEmpty()) workspaceIds.distinct() else listOf(groupId)
        } catch (e: Exception) {
            Log.w(TAG, "Error getting workspaces for group $groupId: ${e.message}")
            listOf(groupId)
        }
    }

    fun listenToCollaborationGroupMembers(groupId: String, onWorkspacesChanged: (List<String>) -> Unit): ListenerRegistration? {
        val firestore = db ?: return null
        if (groupId.isBlank()) return null

        return try {
            firestore.collection("collaborationGroups").document(groupId)
                .collection("members")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Error in collaboration group members listener for $groupId: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val workspaceIds = snapshot.documents.mapNotNull { doc ->
                            doc.getString("workspaceId")?.ifBlank { null }
                        }.filter { it.isNotBlank() }
                        onWorkspacesChanged(if (workspaceIds.isNotEmpty()) workspaceIds.distinct() else listOf(groupId))
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register collaboration group members listener: ${e.message}")
            null
        }
    }

    fun listenToCollaborationGroupPendingPhones(groupId: String, onPendingChanged: () -> Unit): ListenerRegistration? {
        val firestore = db ?: return null
        if (groupId.isBlank()) return null

        return try {
            firestore.collection("collaborationGroups").document(groupId)
                .collection("pendingPhones")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w(TAG, "Error in collaboration group pendingPhones listener for $groupId: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        onPendingChanged()
                    }
                }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register collaboration group pendingPhones listener: ${e.message}")
            null
        }
    }

    suspend fun addPartnerToCollaborationGroup(
        ownerWorkspaceId: String,
        ownerUid: String,
        partnerUid: String,
        partnerWorkspaceId: String,
        partnerPhone: String? = null,
        partnerDisplayName: String? = null
    ): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore is not initialized"))
        val now = System.currentTimeMillis()

        return try {
            val batch = firestore.batch()

            // 1. collaborationGroups/{ownerWorkspaceId}/members/{partnerUid}
            val memberRef = firestore.collection("collaborationGroups").document(ownerWorkspaceId)
                .collection("members").document(partnerUid)
            val groupMember = CollaborationGroupMember(
                uid = partnerUid,
                workspaceId = partnerWorkspaceId,
                role = "partner",
                status = "active",
                joinedAt = now,
                phoneNumber = partnerPhone,
                displayName = partnerDisplayName
            )
            batch.set(memberRef, groupMember.toMap(), SetOptions.merge())

            // 2. userCollaborationGroups/{partnerUid}/groups/{ownerWorkspaceId}
            val partnerIndexRef = firestore.collection("userCollaborationGroups").document(partnerUid)
                .collection("groups").document(ownerWorkspaceId)
            val partnerIndex = UserCollaborationGroupIndex(
                groupId = ownerWorkspaceId,
                ownerUid = ownerUid,
                ownerWorkspaceId = ownerWorkspaceId,
                role = "partner",
                status = "active",
                joinedAt = now
            )
            batch.set(partnerIndexRef, partnerIndex.toMap(), SetOptions.merge())

            batch.commit().await()
            Log.d("TRAC_PARTNER", "COLLABORATION_GROUP_LINK ownerWs=$ownerWorkspaceId partnerUid=$partnerUid partnerWs=$partnerWorkspaceId SUCCESS")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("TRAC_PARTNER", "COLLABORATION_GROUP_LINK failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun removePartnerFromCollaborationGroup(
        ownerWorkspaceId: String,
        partnerUid: String
    ) {
        val firestore = db ?: return
        try {
            val batch = firestore.batch()
            val memberRef = firestore.collection("collaborationGroups").document(ownerWorkspaceId)
                .collection("members").document(partnerUid)
            batch.delete(memberRef)

            val partnerIndexRef = firestore.collection("userCollaborationGroups").document(partnerUid)
                .collection("groups").document(ownerWorkspaceId)
            batch.delete(partnerIndexRef)

            batch.commit().await()
            Log.d("TRAC_PARTNER", "COLLABORATION_GROUP_UNLINK ownerWs=$ownerWorkspaceId partnerUid=$partnerUid SUCCESS")
        } catch (e: Exception) {
            Log.w("TRAC_PARTNER", "Error unlinking from collaboration group: ${e.message}")
        }
    }

    suspend fun updateUserDefaultWorkspace(uid: String, workspaceId: String, role: String = "partner"): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore is not initialized"))
        if (uid.isBlank() || workspaceId.isBlank()) return Result.failure(IllegalArgumentException("uid or workspaceId is blank"))
        return try {
            val userDocRef = firestore.collection("users").document(uid)
            val userSnap = userDocRef.get().await()
            val existingWs = (userSnap.get("workspaces") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val updatedWs = (existingWs + workspaceId).distinct()
            val updates = mapOf(
                "defaultWorkspaceId" to workspaceId,
                "role" to role,
                "workspaces" to updatedWs,
                "updatedAt" to System.currentTimeMillis()
            )
            userDocRef.set(updates, SetOptions.merge()).await()
            Log.d("TRAC_PARTNER", "USER_WS_UPDATED uid=$uid ws=$workspaceId role=$role")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("TRAC_PARTNER", "USER_WS_UPDATED failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun savePendingPartnerPhone(
        groupId: String,
        normalizedPhone: String,
        displayName: String,
        role: String,
        ownerUid: String
    ): Result<Unit> {
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore is not initialized"))
        val formattedPhone = normalizePhoneNumber(normalizedPhone)
        if (formattedPhone.isBlank() || groupId.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid phone or groupId"))
        }

        return try {
            val clean10 = formattedPhone.filter { it.isDigit() }.takeLast(10)
            val pendingData = PendingPartnerPhone(
                normalizedPhone = formattedPhone,
                displayName = displayName.trim(),
                role = role.trim().ifBlank { "partner" },
                addedByUid = ownerUid,
                groupId = groupId,
                createdAt = System.currentTimeMillis(),
                status = "waiting_for_registration"
            )
            val batch = firestore.batch()
            // 1. Full E.164 phone document (+91...)
            val pendingRef1 = firestore.collection("collaborationGroups").document(groupId)
                .collection("pendingPhones").document(formattedPhone)
            batch.set(pendingRef1, pendingData.toMap(), SetOptions.merge())

            // 2. 10-digit clean phone document
            if (clean10.length == 10 && clean10 != formattedPhone) {
                val pendingRef2 = firestore.collection("collaborationGroups").document(groupId)
                    .collection("pendingPhones").document(clean10)
                batch.set(pendingRef2, pendingData.toMap(), SetOptions.merge())
            }

            batch.commit().await()
            Log.d("TRAC_PARTNER", "PENDING_PHONE_SAVED group=$groupId phone=$formattedPhone SUCCESS")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("TRAC_PARTNER", "PENDING_PHONE_SAVED failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun deletePendingPartnerPhone(
        groupId: String,
        normalizedPhone: String
    ) {
        val firestore = db ?: return
        val formattedPhone = normalizePhoneNumber(normalizedPhone)
        if (formattedPhone.isBlank() || groupId.isBlank()) return
        val clean10 = formattedPhone.filter { it.isDigit() }.takeLast(10)
        try {
            val batch = firestore.batch()
            batch.delete(firestore.collection("collaborationGroups").document(groupId).collection("pendingPhones").document(formattedPhone))
            if (clean10.length == 10 && clean10 != formattedPhone) {
                batch.delete(firestore.collection("collaborationGroups").document(groupId).collection("pendingPhones").document(clean10))
            }
            batch.commit().await()
            Log.d("TRAC_PARTNER", "PENDING_PHONE_DELETED group=$groupId phone=$formattedPhone")
        } catch (e: Exception) {
            Log.w("TRAC_PARTNER", "Failed to delete pending phone: ${e.message}")
        }
    }

    suspend fun getPendingPartnerPhones(groupId: String): List<PendingPartnerPhone> {
        val firestore = db ?: return emptyList()
        if (groupId.isBlank()) return emptyList()
        return try {
            val snap = firestore.collection("collaborationGroups").document(groupId)
                .collection("pendingPhones")
                .get().await()
            snap.documents.mapNotNull { doc ->
                doc.data?.let { PendingPartnerPhone.fromMap(it) }
            }.filter { it.status == "waiting_for_registration" || it.status.isBlank() }
        } catch (e: Exception) {
            Log.w("TRAC_PARTNER", "Failed to get pending phones for $groupId: ${e.message}")
            emptyList()
        }
    }

    suspend fun checkIsUserOrPhoneAlreadyPartner(
        uid: String?,
        normalizedPhone: String,
        currentOwnerWorkspaceId: String
    ): Boolean {
        val firestore = db ?: return false
        val cleanPhone = normalizePhoneNumber(normalizedPhone)
        val clean10 = cleanPhone.filter { it.isDigit() }.takeLast(10)

        // 1. If UID is known, check if user is already a member of THIS business
        if (!uid.isNullOrBlank()) {
            try {
                val memberSnap = firestore.collection("collaborationGroups").document(currentOwnerWorkspaceId)
                    .collection("members").document(uid).get().await()
                if (memberSnap.exists()) {
                    val status = memberSnap.getString("status") ?: "active"
                    if (status == "active") return true
                }
                val wsMemberSnap = firestore.collection("workspaces").document(currentOwnerWorkspaceId)
                    .collection("members").document(uid).get().await()
                if (wsMemberSnap.exists()) {
                    val status = wsMemberSnap.getString("status") ?: "active"
                    if (status == "active") return true
                }
            } catch (e: Exception) {
                Log.w(TAG, "checkIsUserOrPhoneAlreadyPartner uid check failed: ${e.message}")
            }
        }

        // 2. Check if this phone number is already pending or a member in THIS business
        if (clean10.isNotBlank()) {
            try {
                val pendingRef = firestore.collection("collaborationGroups").document(currentOwnerWorkspaceId)
                    .collection("pendingPhones").document(cleanPhone).get().await()
                if (pendingRef.exists()) {
                    return true
                }

                // Also check if any active member in this group already has this phone
                val membersSnap = firestore.collection("collaborationGroups").document(currentOwnerWorkspaceId)
                    .collection("members").get().await()
                for (doc in membersSnap.documents) {
                    val phone = doc.getString("phoneNumber") ?: ""
                    if (phone.filter { it.isDigit() }.takeLast(10) == clean10) {
                        return true
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "checkIsUserOrPhoneAlreadyPartner phone check failed: ${e.message}")
            }
        }

        return false
    }

    fun getAllKnownWorkspaceIdsFromDisk(): List<String> {
        return try {
            val db = AppDatabase.getInstance(context)
            val settingsIds = kotlinx.coroutines.runBlocking(Dispatchers.IO) {
                try {
                    db.appSettingsDao().getAllSettingsOnce().map { it.workspaceId }
                } catch (_: Exception) {
                    emptyList()
                }
            }
            settingsIds.filter { it.isNotBlank() }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun resolveAndBindPartnerInvitation(
        userUid: String,
        rawPhone: String?,
        userDisplayName: String?,
        knownWorkspaceIds: List<String> = emptyList()
    ): UserProfile? {
        val firestore = db ?: return null
        if (userUid.isBlank()) return null
        val formattedPhone = normalizePhoneNumber(rawPhone ?: "")
        if (formattedPhone.isBlank()) return null
        val clean10 = formattedPhone.filter { it.isDigit() }.takeLast(10)
        val phoneVariants = listOf(formattedPhone, clean10, "+91$clean10", "91$clean10").filter { it.isNotBlank() }.distinct()

        Log.d("TRAC_PARTNER", "RESOLVE_INVITATION starting for uid=$userUid phone=$formattedPhone variants=$phoneVariants")

        // 1. First, check if this user already has an active workspace membership with role == 'partner'
        try {
            val memberships = firestore.collection("userWorkspaceMemberships").document(userUid)
                .collection("workspaces").get().await()
            for (mDoc in memberships.documents) {
                val mRole = mDoc.getString("role")?.lowercase() ?: ""
                val mWsId = mDoc.getString("workspaceId") ?: mDoc.id
                val mOwnerUid = mDoc.getString("ownerUid") ?: ""
                if (mRole == "partner" && mWsId.isNotBlank() && mOwnerUid != userUid) {
                    Log.d("TRAC_PARTNER", "Found existing partner membership in $mWsId for uid=$userUid")
                    val now = System.currentTimeMillis()
                    val profile = UserProfile(
                        uid = userUid,
                        displayName = userDisplayName?.ifBlank { null } ?: mDoc.getString("displayName") ?: "Partner",
                        phoneNumber = formattedPhone,
                        defaultWorkspaceId = mWsId,
                        workspaces = listOf(mWsId),
                        role = "partner",  // CRITICAL: must be set in the object, not just Firestore
                        createdAt = now,
                        updatedAt = now
                    )
                    val userMap = profile.toMap().toMutableMap()
                    userMap["role"] = "partner"
                    firestore.collection("users").document(userUid).set(userMap, SetOptions.merge()).await()
                    Log.i("TRAC_PARTNER", "Returning partner uid=$userUid resolved to existing membership ws=$mWsId")
                    return profile
                }
            }
        } catch (e: Exception) {
            Log.w("TRAC_PARTNER", "userWorkspaceMemberships check error: ${e.message}")
        }

        // 2. Discover pending partner invitation
        var targetGroupId: String = ""
        var targetOwnerUid: String = ""
        var targetDisplayName: String = ""
        var targetRole: String = "partner"
        var pendingDocToDelete: com.google.firebase.firestore.DocumentReference? = null

        // Strategy A: collectionGroup("pendingPhones") query
        for (variant in phoneVariants) {
            try {
                val snap = firestore.collectionGroup("pendingPhones")
                    .whereEqualTo("normalizedPhone", variant)
                    .get().await()
                val found = snap.documents.firstOrNull {
                    val p = PendingPartnerPhone.fromMap(it.data ?: emptyMap())
                    p.status == "waiting_for_registration" || p.status.isBlank()
                }
                if (found != null) {
                    val p = PendingPartnerPhone.fromMap(found.data!!)
                    targetGroupId = p.groupId.ifBlank { found.reference.parent.parent?.id ?: "" }
                    targetOwnerUid = p.addedByUid
                    targetDisplayName = p.displayName
                    targetRole = p.role.ifBlank { "partner" }
                    pendingDocToDelete = found.reference
                    Log.d("TRAC_PARTNER", "Found pending invitation via collectionGroup: group=$targetGroupId owner=$targetOwnerUid")
                    break
                }
            } catch (e: Exception) {
                Log.w("TRAC_PARTNER", "collectionGroup query for $variant skipped: ${e.message}")
            }
        }

        // Strategy B: If collectionGroup failed or found nothing, direct lookup in known workspaces/collaborationGroups
        if (targetGroupId.isBlank()) {
            val candidateGroupIds = (knownWorkspaceIds + getAllKnownWorkspaceIdsFromDisk()).filter { it.isNotBlank() }.distinct()
            Log.d("TRAC_PARTNER", "Testing candidate group IDs: $candidateGroupIds")
            for (cId in candidateGroupIds) {
                for (variant in phoneVariants) {
                    try {
                        val pDoc = firestore.collection("collaborationGroups").document(cId)
                            .collection("pendingPhones").document(variant).get().await()
                        if (pDoc.exists() && pDoc.data != null) {
                            val p = PendingPartnerPhone.fromMap(pDoc.data!!)
                            targetGroupId = p.groupId.ifBlank { cId }
                            targetOwnerUid = p.addedByUid
                            targetDisplayName = p.displayName
                            targetRole = p.role.ifBlank { "partner" }
                            pendingDocToDelete = pDoc.reference
                            Log.d("TRAC_PARTNER", "Found pending invitation via direct lookup in $cId/$variant")
                            break
                        }
                    } catch (e: Exception) {
                        Log.w("TRAC_PARTNER", "Direct pending check for $cId/$variant skipped: ${e.message}")
                    }
                }
                if (targetGroupId.isNotBlank()) break

                // Also check workspaces/{cId}/attendees
                try {
                    val attendeesSnap = firestore.collection("workspaces").document(cId)
                        .collection("attendees").get().await()
                    for (aDoc in attendeesSnap.documents) {
                        val aPhone = aDoc.getString("phone") ?: ""
                        val aClean = normalizePhoneNumber(aPhone).filter { it.isDigit() }.takeLast(10)
                        if (aClean.isNotBlank() && aClean == clean10) {
                            targetGroupId = cId
                            targetOwnerUid = aDoc.getString("ownerUid") ?: cId.removePrefix("ws_")
                            targetDisplayName = aDoc.getString("name") ?: ""
                            targetRole = aDoc.getString("role") ?: "partner"
                            Log.d("TRAC_PARTNER", "Found partner attendee in $cId for phone=$clean10")
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.w("TRAC_PARTNER", "Attendees check for $cId skipped: ${e.message}")
                }
                if (targetGroupId.isNotBlank()) break
            }
        }

        if (targetGroupId.isBlank()) {
            Log.d("TRAC_PARTNER", "No partner invitation found for phone=$formattedPhone uid=$userUid")
            return null
        }

        // 3. Resolve Owner details and Workspace name
        val now = System.currentTimeMillis()
        var resolvedWsName = "AIDHUNT Tractor Fleet"
        try {
            val wsDoc = firestore.collection("workspaces").document(targetGroupId).get().await()
            if (wsDoc.exists()) {
                val wsOwner = wsDoc.getString("ownerUid")
                if (!wsOwner.isNullOrBlank() && targetOwnerUid.isBlank()) {
                    targetOwnerUid = wsOwner
                }
                val name = wsDoc.getString("name")
                if (!name.isNullOrBlank()) resolvedWsName = name
            }
        } catch (_: Exception) {}

        if (targetOwnerUid.isBlank()) {
            targetOwnerUid = targetGroupId.removePrefix("ws_")
        }

        val finalPartnerName = userDisplayName?.ifBlank { null }
            ?: targetDisplayName.ifBlank { null }
            ?: "Partner"

        // 4. BIND THE REAL AUTHENTICATED UID TO THE OWNER'S WORKSPACE
        Log.i("TRAC_PARTNER", "BINDING REAL UID $userUid TO OWNER WORKSPACE $targetGroupId (owner=$targetOwnerUid, role=$targetRole)")
        val batch = firestore.batch()

        // 4a. workspaces/{ownerWorkspaceId}/members/{userUid}
        val wsMemberRef = firestore.collection("workspaces").document(targetGroupId)
            .collection("members").document(userUid)
        val wsMember = WorkspaceMember(
            uid = userUid,
            role = "partner",
            status = "active",
            phoneNumber = formattedPhone,
            joinedAt = now,
            addedByUid = targetOwnerUid,
            invitedByUid = targetOwnerUid,
            displayName = finalPartnerName
        )
        batch.set(wsMemberRef, wsMember.toMap(), SetOptions.merge())

        // 4b. userWorkspaceMemberships/{userUid}/workspaces/{ownerWorkspaceId}
        val userWsRef = firestore.collection("userWorkspaceMemberships").document(userUid)
            .collection("workspaces").document(targetGroupId)
        batch.set(userWsRef, mapOf(
            "workspaceId" to targetGroupId,
            "ownerUid" to targetOwnerUid,
            "role" to "partner",
            "status" to "active",
            "joinedAt" to now,
            "workspaceName" to resolvedWsName
        ), SetOptions.merge())

        // 4c. collaborationGroups/{ownerWorkspaceId}/members/{userUid}
        val groupMemberRef = firestore.collection("collaborationGroups").document(targetGroupId)
            .collection("members").document(userUid)
        val groupMember = CollaborationGroupMember(
            uid = userUid,
            workspaceId = targetGroupId,
            role = "partner",
            status = "active",
            joinedAt = now,
            phoneNumber = formattedPhone,
            displayName = finalPartnerName
        )
        batch.set(groupMemberRef, groupMember.toMap(), SetOptions.merge())

        // 4d. userCollaborationGroups/{userUid}/groups/{ownerWorkspaceId}
        val userGroupRef = firestore.collection("userCollaborationGroups").document(userUid)
            .collection("groups").document(targetGroupId)
        val groupIndex = UserCollaborationGroupIndex(
            groupId = targetGroupId,
            ownerUid = targetOwnerUid,
            ownerWorkspaceId = targetGroupId,
            role = "partner",
            status = "active",
            joinedAt = now
        )
        batch.set(userGroupRef, groupIndex.toMap(), SetOptions.merge())

        // 4e. Link attendee document in workspaces/{ownerWorkspaceId}/attendees
        try {
            val attendeesRef = firestore.collection("workspaces").document(targetGroupId).collection("attendees")
            val attendeesSnap = attendeesRef.get().await()
            for (aDoc in attendeesSnap.documents) {
                val aPhone = aDoc.getString("phone") ?: ""
                if (normalizePhoneNumber(aPhone).filter { it.isDigit() }.takeLast(10) == clean10) {
                    batch.set(attendeesRef.document(aDoc.id), mapOf(
                        "partnerUid" to userUid,
                        "status" to "CONNECTED"
                    ), SetOptions.merge())
                    break
                }
            }
        } catch (ex: Exception) {
            Log.w("TRAC_PARTNER", "Could not link attendee doc: ${ex.message}")
        }

        // 4f. phoneDirectory/{formattedPhone}
        val dirRef = firestore.collection("phoneDirectory").document(formattedPhone)
        batch.set(dirRef, mapOf(
            "uid" to userUid,
            "phoneNumber" to formattedPhone,
            "role" to "partner",
            "defaultWorkspaceId" to targetGroupId,
            "workspaceId" to targetGroupId,
            "displayName" to finalPartnerName,
            "workspaces" to listOf(targetGroupId),
            "updatedAt" to now
        ), SetOptions.merge())

        // 4g. Delete pending document
        pendingDocToDelete?.let { batch.delete(it) }
        for (variant in phoneVariants) {
            try {
                batch.delete(firestore.collection("collaborationGroups").document(targetGroupId).collection("pendingPhones").document(variant))
            } catch (_: Exception) {}
        }

        // 4h. users/{userUid}
        val profile = UserProfile(
            uid = userUid,
            displayName = finalPartnerName,
            phoneNumber = formattedPhone,
            defaultWorkspaceId = targetGroupId,
            workspaces = listOf(targetGroupId),
            role = "partner",  // CRITICAL: role must be set in the returned object, not just Firestore
            createdAt = now,
            updatedAt = now
        )
        val userMap = profile.toMap().toMutableMap()
        userMap["role"] = "partner"
        batch.set(firestore.collection("users").document(userUid), userMap, SetOptions.merge())

        batch.commit().await()
        Log.i("TRAC_PARTNER", "BINDING_SUCCESS Partner $userUid successfully bound to Owner Workspace $targetGroupId with role=partner")
        return profile
    }

    suspend fun findAndConnectPendingPartnerLinks(
        userUid: String,
        verifiedPhone: String,
        userWorkspaceId: String,
        userDisplayName: String?
    ): List<String> {
        val profile = resolveAndBindPartnerInvitation(userUid, verifiedPhone, userDisplayName)
        return if (profile != null) listOf(profile.defaultWorkspaceId ?: "") else emptyList()
    }

    suspend fun getCollaborationGroupMembersList(groupId: String): List<WorkspaceMember> {
        val firestore = db ?: return emptyList()
        if (groupId.isBlank()) return emptyList()
        return try {
            val snapshot = firestore.collection("collaborationGroups").document(groupId)
                .collection("members").get().await()
            val list = mutableListOf<WorkspaceMember>()
            for (doc in snapshot.documents) {
                val data = doc.data ?: continue
                var member = WorkspaceMember(
                    uid = doc.getString("uid") ?: doc.id,
                    role = doc.getString("role") ?: "partner",
                    status = doc.getString("status") ?: "active",
                    phoneNumber = doc.getString("phoneNumber"),
                    displayName = doc.getString("displayName"),
                    joinedAt = doc.getLong("joinedAt") ?: System.currentTimeMillis()
                )
                if (member.displayName.isNullOrBlank() && member.uid.isNotBlank()) {
                    try {
                        val userDoc = firestore.collection("users").document(member.uid).get().await()
                        val uName = userDoc.getString("displayName")
                        val uPhone = userDoc.getString("phoneNumber")
                        if (!uName.isNullOrBlank()) {
                            member = member.copy(displayName = uName)
                        }
                        if (member.phoneNumber.isNullOrBlank() && !uPhone.isNullOrBlank()) {
                            member = member.copy(phoneNumber = uPhone)
                        }
                    } catch (_: Exception) {}
                }
                list.add(member)
            }
            list
        } catch (e: Exception) {
            Log.w("TRAC_PARTNER", "Error getting collaboration members for $groupId: ${e.message}")
            emptyList()
        }
    }

    suspend fun getUserCollaborationGroups(uid: String): List<UserCollaborationGroupIndex> {
        val firestore = db ?: return emptyList()
        if (uid.isBlank()) return emptyList()
        return try {
            val snap = firestore.collection("userCollaborationGroups").document(uid)
                .collection("groups").get().await()
            snap.documents.mapNotNull { doc ->
                doc.data?.let { UserCollaborationGroupIndex.fromMap(it) }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting userCollaborationGroups: ${e.message}")
            emptyList()
        }
    }

    private fun parseLongId(idStr: String): Long {
        return idStr.toLongOrNull() ?: (idStr.hashCode().toLong().let { if (it <= 0) Math.abs(it) + 1L else it })
    }
}
