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
    private val listenerHealth = CloudListenerHealth()
    val cloudListenerStates = listenerHealth.states
    private val listenerEpoch = java.util.concurrent.atomic.AtomicLong(0)
    private val actionTimeBarriers = java.util.concurrent.ConcurrentHashMap<Pair<String, String>, com.example.data.sync.ServerActionTimeBarrier>()

    enum class PendingPartnerCleanupResult {
        DELETED,
        ALREADY_GONE,
        REGISTERED
    }

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
        val firestore = db ?: throw IllegalStateException("Firestore unavailable")
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank() || uid != user.uid) throw SecurityException("Authenticated identity mismatch")
        val wsId = preResolvedWorkspaceId?.takeIf { it.isNotBlank() }
            ?: user.defaultWorkspaceId?.takeIf { it.isNotBlank() }
            ?: throw SecurityException("A verified workspace membership is required")
        resolveAuthoritativeRole(uid, wsId) ?: throw SecurityException("Workspace membership is inactive or missing")
        val snapshot = firestore.collection("workspaces").document(wsId)
            .get(com.google.firebase.firestore.Source.SERVER).await()
        if (!snapshot.exists()) throw SecurityException("Workspace does not exist")
        val workspace = Workspace.fromMap(snapshot.data.orEmpty()).copy(workspaceId = wsId)
        _currentWorkspace.value = workspace
        return workspace
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
        val firestore = db ?: throw IllegalStateException("Firestore unavailable")
        if (uid.isBlank() || workspaceId.isBlank()) return null
        val member = firestore.collection("workspaces").document(workspaceId).collection("members")
            .document(uid).get(com.google.firebase.firestore.Source.SERVER).await()
        if (member.exists()) {
            val storedRole = member.getString("role")
            val roleSchemaValid = storedRole in setOf("OWNER", "Owner", "owner", "PARTNER", "Partner", "partner", "OPERATOR", "Operator", "operator")
            val statusSchemaValid = member.getString("status") in setOf("ACTIVE", "active")
            Log.i("TRAC_MEMBERSHIP", "Server membership schema: roleValid=$roleSchemaValid statusValid=$statusSchemaValid uidFieldMatches=${member.getString("uid") == uid} workspaceFieldMatches=${member.getString("workspaceId") == workspaceId}")
        }
        if (!member.exists() || !member.getString("status").orEmpty().equals("active", true)) return null
        val workspace = firestore.collection("workspaces").document(workspaceId)
            .get(com.google.firebase.firestore.Source.SERVER).await()
        if (!workspace.exists()) return null
        return WorkspaceDiscoveryPolicy.role(uid, workspaceId, member.data.orEmpty(), workspace.getString("ownerUid").orEmpty())
    }

    suspend fun discoverAuthoritativeMemberships(uid: String, preferredWorkspaceId: String? = null): List<AuthoritativeMembership> {
        val firestore = db ?: throw IllegalStateException("Firestore unavailable")
        require(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid == uid) { "Authenticated identity mismatch" }
        val profile = firestore.collection("users").document(uid).get(com.google.firebase.firestore.Source.SERVER).await()
        val index = firestore.collection("userWorkspaceMemberships").document(uid).collection("workspaces")
            .get(com.google.firebase.firestore.Source.SERVER).await()
        val candidates = WorkspaceDiscoveryPolicy.candidates(uid, preferredWorkspaceId, profile.getString("defaultWorkspaceId"),
            (profile.get("workspaces") as? List<*>)?.filterIsInstance<String>().orEmpty(), index.documents.map { it.id })
        val result = mutableListOf<AuthoritativeMembership>()
        for (wsId in candidates) {
            if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != uid)
                throw SecurityException("Session changed during workspace discovery")
            val role = try { resolveAuthoritativeRole(uid, wsId) }
            catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED ||
                    e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.NOT_FOUND) null else throw e
            } ?: continue
            result.add(AuthoritativeMembership(wsId, role, "ACTIVE"))
        }
        return result
    }

    suspend fun resolveAuthoritativeMembership(uid: String, preferredWorkspaceId: String? = null): AuthoritativeMembership? {
        val memberships = discoverAuthoritativeMemberships(uid, preferredWorkspaceId)
        // Test builds keep their mutation boundary, but never make staging access a sign-in condition.
        return if (com.example.BuildConfig.PHYSICAL_TEST)
            memberships.firstOrNull { FirebaseEnvironment.isStagingWorkspace(it.workspaceId) }
        else memberships.firstOrNull()
    }

    /** Explicit onboarding only. Retry the same UID-derived identity after either phase fails. */
    suspend fun createPersonalWorkspace(user: com.google.firebase.auth.FirebaseUser, fullName: String, businessName: String): UserProfile {
        val firestore = db ?: throw IllegalStateException("Firestore unavailable")
        require(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid == user.uid && !user.phoneNumber.isNullOrBlank())
        val uid = user.uid
        val owned = discoverAuthoritativeMemberships(uid).firstOrNull { com.example.data.auth.RoleUtils.isOwner(it.role) }
        val ws = owned?.workspaceId ?: WorkspaceDiscoveryPolicy.personalId(uid)
        if (com.example.BuildConfig.PHYSICAL_TEST && !FirebaseEnvironment.isStagingWorkspace(ws))
            throw SecurityException("Personal workspace creation requires the standard app; this test build is staging-only")
        val wsRef = firestore.collection("workspaces").document(ws)
        val memberRef = wsRef.collection("members").document(uid)
        val name = businessName.trim().ifBlank { "Personal workspace" }
        val now = System.currentTimeMillis()
        // Membership get is permitted for its own UID even before workspace creation. Never
        // overwrite an existing removed membership or an existing workspace owned by another UID.
        firestore.runTransaction { tx ->
            val member = tx.get(memberRef)
            if (member.exists()) {
                val workspace = tx.get(wsRef)
                if (WorkspaceDiscoveryPolicy.role(uid, ws, member.data.orEmpty(), workspace.getString("ownerUid").orEmpty()) != "Owner")
                    throw SecurityException("Existing personal workspace requires Owner review")
            } else {
                tx.set(wsRef, mapOf("workspaceId" to ws, "ownerUid" to uid, "name" to name, "createdAt" to now, "updatedAt" to now))
                tx.set(memberRef, mapOf("uid" to uid, "workspaceId" to ws, "role" to "OWNER", "status" to "ACTIVE",
                    "joinedAt" to now, "displayName" to fullName.trim(), "phoneNumber" to user.phoneNumber))
            }
        }.await()
        // Deployed index rules require an already committed Owner membership. The second
        // atomic phase publishes the index + profile together; a retry repairs only this phase.
        return firestore.runTransaction { tx ->
            val profileRef = firestore.collection("users").document(uid)
            val profileDoc = tx.get(profileRef)
            val member = tx.get(memberRef)
            val workspace = tx.get(wsRef)
            if (WorkspaceDiscoveryPolicy.role(uid, ws, member.data.orEmpty(), workspace.getString("ownerUid").orEmpty()) != "Owner")
                throw SecurityException("Personal workspace authorization changed")
            val old = UserProfile.fromMap(profileDoc.data.orEmpty())
            val profile = old.copy(uid = uid, displayName = old.displayName ?: fullName.trim(), phoneNumber = user.phoneNumber,
                defaultWorkspaceId = ws, workspaces = (old.workspaces + ws).distinct(), role = null, updatedAt = now)
            tx.set(firestore.collection("userWorkspaceMemberships").document(uid).collection("workspaces").document(ws),
                mapOf("workspaceId" to ws, "ownerUid" to uid, "role" to "OWNER", "status" to "ACTIVE",
                    "joinedAt" to (member.getLong("joinedAt") ?: now)))
            tx.set(profileRef, profile.toMap(), SetOptions.merge())
            profile
        }.await()
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
        onExtensionsUpdated: (List<WorkTypeExtensionEntity>, String) -> Unit = { _, _ -> },
        operatorWorkspaceIds: Set<String> = emptySet(),
        onMembershipUpdated: (List<WorkspaceMember>, String) -> Unit = { _, _ -> }
    ) {
        val epoch = listenerEpoch.get()
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
            listenerHealth.remove(wsId)
            Log.d("TRAC_FIRESTORE", "STOP_LISTEN workspace=$wsId")
        }

        // Add listeners for new workspaces
        val toAdd = workspaceIds - existingWorkspaces
        for (workspaceId in toAdd) {
            if (workspaceId.isBlank()) continue
            val wsRef = firestore.collection("workspaces").document(workspaceId)
            val regList = mutableListOf<ListenerRegistration>()
            val operational = setOf("actorMembership", "members", "entries", "tractors", "attendees", "settings", "accessControl", "extensions")
            listenerHealth.begin(workspaceId, operational + if (workspaceId in operatorWorkspaceIds) emptySet() else setOf("customers", "expenses", "withdrawals", "payments"))
            // The full members query loses permission after removal. The actor's own
            // member document remains readable so revocation still reaches this device.
            regList.add(wsRef.collection("members").document(currentUid).addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (listenerEpoch.get() != epoch || com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != currentUid || workspaceId !in activeWorkspaceListeners) return@addSnapshotListener
                if (error != null) listenerHealth.failed(workspaceId, "actorMembership")
                if (snapshot != null) listenerHealth.snapshot(workspaceId, "actorMembership", snapshot.metadata.isFromCache)
                if (error == null && snapshot != null && !snapshot.metadata.isFromCache) {
                    val actor = snapshot.data?.let { WorkspaceMember.fromMap(it) }
                    if (actor == null || !actor.status.equals("active", true) ||
                        com.example.data.auth.RoleUtils.normalizeRole(actor.role).isBlank()) {
                        onMembershipUpdated(emptyList(), workspaceId)
                    }
                }
            })
            regList.add(wsRef.collection("members").addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                if (listenerEpoch.get() != epoch || com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != currentUid || workspaceId !in activeWorkspaceListeners) return@addSnapshotListener
                if (error != null) listenerHealth.failed(workspaceId, "members")
                if (snapshot != null) listenerHealth.snapshot(workspaceId, "members", snapshot.metadata.isFromCache)
                // Cached/offline snapshots never manufacture a new verification result.
                if (error == null && snapshot != null && !snapshot.metadata.isFromCache) {
                    onMembershipUpdated(snapshot.documents.mapNotNull { doc -> doc.data?.let {
                        WorkspaceMember.fromMap(it).copy(uid = doc.id, workspaceId = workspaceId)
                    } }, workspaceId)
                }
            })

            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=entries")
            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=expenses")
            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=customers")
            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=tractors")
            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=attendees")
            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=withdrawals")
            Log.d("TRAC_FIRESTORE", "LISTEN uid=$currentUid workspace=$workspaceId collection=settings")

            // 1. Entries Listener
            val entryQuery: com.google.firebase.firestore.Query = if (workspaceId in operatorWorkspaceIds) wsRef.collection("entries").whereEqualTo("isHistorical", false) else wsRef.collection("entries")
            val regEntries = entryQuery
                .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                    if (listenerEpoch.get() != epoch || com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != currentUid || workspaceId !in activeWorkspaceListeners) return@addSnapshotListener
                    if (error != null) {
                        listenerHealth.failed(workspaceId, "entries")
                        Log.w(TAG, "Entries listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) listenerHealth.snapshot(workspaceId, "entries", snapshot.metadata.isFromCache)
                    if (snapshot != null && authoritativeLedgerSnapshot(snapshot.metadata.isFromCache, snapshot.metadata.hasPendingWrites())) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=entries count=${snapshot.documents.size}")
                        val jobs = snapshot.documents.mapNotNull { doc ->
                            doc.data?.takeUnless { it["isDeleted"] == true }?.let { jobEntryFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                        }
                        onJobsUpdated(jobs, workspaceId)
                    }
                }
            regList.add(regEntries)

            // 2. Expenses Listener
            if (workspaceId !in operatorWorkspaceIds) {
                val regExpenses = wsRef.collection("expenses")
                    .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                        if (listenerEpoch.get() != epoch || com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != currentUid || workspaceId !in activeWorkspaceListeners) return@addSnapshotListener
                        if (error != null) {
                            listenerHealth.failed(workspaceId, "expenses")
                            Log.w(TAG, "Expenses listener notice: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) listenerHealth.snapshot(workspaceId, "expenses", snapshot.metadata.isFromCache)
                        if (snapshot != null && authoritativeLedgerSnapshot(snapshot.metadata.isFromCache, snapshot.metadata.hasPendingWrites())) {
                            Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=expenses count=${snapshot.documents.size}")
                            val expenses = snapshot.documents.mapNotNull { doc ->
                                doc.data?.takeUnless { it["isDeleted"] == true }?.let { expenseFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                            }
                            onExpensesUpdated(expenses, workspaceId)
                        }
                    }
                regList.add(regExpenses)
            }

            // 3. Customers Listener
            if (workspaceId !in operatorWorkspaceIds) {
            val regCustomers = wsRef.collection("customers")
                .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                    if (listenerEpoch.get() != epoch || com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != currentUid || workspaceId !in activeWorkspaceListeners) return@addSnapshotListener
                    if (error != null) {
                        listenerHealth.failed(workspaceId, "customers")
                        Log.w(TAG, "Customers listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) listenerHealth.snapshot(workspaceId, "customers", snapshot.metadata.isFromCache)
                    if (snapshot != null && authoritativeLedgerSnapshot(snapshot.metadata.isFromCache, snapshot.metadata.hasPendingWrites())) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=customers count=${snapshot.documents.size}")
                        val customers = snapshot.documents.mapNotNull { doc ->
                            doc.data?.takeUnless { it["isDeleted"] == true }?.let { customerFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                        }
                        onCustomersUpdated(customers, workspaceId)
                    }
                }
            regList.add(regCustomers)
            }

            // 4. Tractors Listener
            val regTractors = wsRef.collection("tractors")
                .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                    if (listenerEpoch.get() != epoch || com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != currentUid || workspaceId !in activeWorkspaceListeners) return@addSnapshotListener
                    if (error != null) {
                        listenerHealth.failed(workspaceId, "tractors")
                        Log.w(TAG, "Tractors listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) listenerHealth.snapshot(workspaceId, "tractors", snapshot.metadata.isFromCache)
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=tractors count=${snapshot.documents.size}")
                        val tractors = snapshot.documents.mapNotNull { doc ->
                            doc.data?.takeUnless { it["isDeleted"] == true }?.let { tractorFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                        }
                        onTractorsUpdated(tractors, workspaceId)
                    }
                }
            regList.add(regTractors)

            // 5. Attendees (Partners / Operators) Listener
            val regAttendees = wsRef.collection("attendees")
                .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                    if (listenerEpoch.get() != epoch || com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != currentUid || workspaceId !in activeWorkspaceListeners) return@addSnapshotListener
                    if (error != null) {
                        listenerHealth.failed(workspaceId, "attendees")
                        Log.w(TAG, "Attendees listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) listenerHealth.snapshot(workspaceId, "attendees", snapshot.metadata.isFromCache)
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=attendees count=${snapshot.documents.size}")
                        val partners = snapshot.documents.mapNotNull { doc ->
                            doc.data?.takeUnless { it["isDeleted"] == true }?.let { partnerFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                        }
                        onPartnersUpdated(partners, workspaceId)
                    }
                }
            regList.add(regAttendees)

            // 6. Withdrawals Listener
            if (workspaceId !in operatorWorkspaceIds) {
                val regWithdrawals = wsRef.collection("withdrawals")
                    .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                        if (listenerEpoch.get() != epoch || com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != currentUid || workspaceId !in activeWorkspaceListeners) return@addSnapshotListener
                        if (error != null) {
                            listenerHealth.failed(workspaceId, "withdrawals")
                            Log.w(TAG, "Withdrawals listener notice: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) listenerHealth.snapshot(workspaceId, "withdrawals", snapshot.metadata.isFromCache)
                        if (snapshot != null && authoritativeLedgerSnapshot(snapshot.metadata.isFromCache, snapshot.metadata.hasPendingWrites())) {
                            Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=withdrawals count=${snapshot.documents.size}")
                            val withdrawals = snapshot.documents.mapNotNull { doc ->
                                doc.data?.takeUnless { it["isDeleted"] == true }?.let { withdrawalFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
                            }
                            onWithdrawalsUpdated(withdrawals, workspaceId)
                        }
                    }
                regList.add(regWithdrawals)
            }

            // 7. Settings Listener
            val regSettings = wsRef.collection("settings").document("main")
                .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                    if (listenerEpoch.get() != epoch || com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != currentUid || workspaceId !in activeWorkspaceListeners) return@addSnapshotListener
                    if (error != null) {
                        listenerHealth.failed(workspaceId, "settings")
                        Log.w(TAG, "Settings listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) listenerHealth.snapshot(workspaceId, "settings", snapshot.metadata.isFromCache)
                    if (snapshot != null && snapshot.exists()) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=settings exists=true")
                        snapshot.data?.let { onSettingsUpdated(it, workspaceId) }
                    }
                }
            regList.add(regSettings)

            // 8. Payments Listener
            if (workspaceId !in operatorWorkspaceIds) {
                val regPayments = wsRef.collection("collections")
                    .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                        if (listenerEpoch.get() != epoch || com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != currentUid || workspaceId !in activeWorkspaceListeners) return@addSnapshotListener
                        if (error != null) {
                            listenerHealth.failed(workspaceId, "payments")
                            Log.w(TAG, "Payments listener notice: ${error.message}")
                            return@addSnapshotListener
                        }
                        if (snapshot != null) listenerHealth.snapshot(workspaceId, "payments", snapshot.metadata.isFromCache)
                        // Room pruning requires an authoritative server snapshot. A cached
                        // Firestore view can be incomplete during offline startup or reconnect.
                        if (snapshot != null && authoritativeLedgerSnapshot(snapshot.metadata.isFromCache, snapshot.metadata.hasPendingWrites())) {
                            Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=payments count=${snapshot.documents.size}")
                            val payments = snapshot.documents.mapNotNull { doc ->
                                // Collection commands address the document by exact int64 ID. A
                                // hash of a legacy string ID creates a local row that cannot be
                                // reversed or acknowledged at its real cloud path.
                                val data = doc.data ?: return@mapNotNull null
                                val id = exactCollectionDocumentId(doc.id, data) ?: run {
                                    Log.w(TAG, "Collection document requires ID migration in workspace=$workspaceId")
                                    return@mapNotNull null
                                }
                                data.takeUnless { it["isDeleted"] == true }?.let {
                                    paymentFromFirestoreMap(it, fallbackId = id, fallbackWorkspaceId = workspaceId)
                                }
                            }
                            onPaymentsUpdated(payments, workspaceId)
                        }
                    }
                regList.add(regPayments)
            }

            // 9. Access Control Listener: workspaces/{workspaceId}/settings/access_control
            val regAccessControl = wsRef.collection("settings").document("access_control")
                .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                    if (listenerEpoch.get() != epoch || com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != currentUid || workspaceId !in activeWorkspaceListeners) return@addSnapshotListener
                    if (error != null) {
                        listenerHealth.failed(workspaceId, "accessControl")
                        Log.w(TAG, "AccessControl listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) listenerHealth.snapshot(workspaceId, "accessControl", snapshot.metadata.isFromCache)
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId settings/access_control exists=${snapshot.exists()}")
                        onAccessControlUpdated(if (snapshot.exists()) snapshot.data else null, workspaceId)
                    }
                }
            regList.add(regAccessControl)

            // v1.6 has no per-Partner page locks; do not query legacy partnerPermissions.

            // 11. Extensions Listener: workspaces/{workspaceId}/extensions
            val regExtensions = wsRef.collection("extensions")
                .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { snapshot, error ->
                    if (listenerEpoch.get() != epoch || com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != currentUid || workspaceId !in activeWorkspaceListeners) return@addSnapshotListener
                    if (error != null) {
                        listenerHealth.failed(workspaceId, "extensions")
                        Log.w(TAG, "Extensions listener notice: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) listenerHealth.snapshot(workspaceId, "extensions", snapshot.metadata.isFromCache)
                    if (snapshot != null) {
                        Log.d("TRAC_FIRESTORE", "SNAPSHOT workspace=$workspaceId collection=extensions count=${snapshot.documents.size}")
                        val exts = snapshot.documents.mapNotNull { doc ->
                            doc.data?.takeUnless { it["isDeleted"] == true }?.let { extensionFromFirestoreMap(it, fallbackId = parseLongId(doc.id), fallbackWorkspaceId = workspaceId) }
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
        listenerEpoch.incrementAndGet()
        listenerHealth.clear()
        activeWorkspaceListeners.values.forEach { list ->
            list.forEach { it.remove() }
        }
        activeWorkspaceListeners.clear()
        Log.d(TAG, "All Firestore snapshot listeners stopped.")
    }

    // --- Direct Cloud Operations ---

    /** Preserve immutable local creation time; serverReceivedAt records upload separately. */
    private suspend fun saveTimedDocument(
        ref: com.google.firebase.firestore.DocumentReference,
        data: Map<String, Any?>,
        merge: Boolean = false,
        queuedOperation: com.example.data.entity.PendingOperationEntity? = null
    ) {
        val firestore = db ?: throw IllegalStateException("Firestore is unavailable")
        val originalCustomer = queuedOperation?.takeIf { it.entityType == "JOB" }?.let {
            org.json.JSONObject(it.payloadJson).optString("expectedBaseJson").takeIf { base -> base.isNotBlank() && base != "null" }
                ?.let { base -> org.json.JSONObject(base).optLong("customerId") }
        }
        val reassigning = originalCustomer != null && originalCustomer != (data["customerId"] as? Number)?.toLong()
        // The job's version is checked again inside the transaction. A new collection changes
        // its financial base; known receipt documents are also read transactionally below.
        val receiptRefs = if (reassigning) firestore.collection("workspaces").document(queuedOperation!!.workspaceId)
            .collection("collections").get(com.google.firebase.firestore.Source.SERVER).await().documents.filter {
                com.example.data.sync.EntryDeletionPolicy.references(paymentFromFirestoreMap(it.data.orEmpty()), queuedOperation.entityId)
            }.map { it.reference } else emptyList()
        firestore.runTransaction { transaction ->
            val existing = transaction.get(ref)
            val operationId = data["operationId"] as? String
            // An already committed command needs acknowledgement, not a new deadline check.
            if (!operationId.isNullOrBlank() && existing.getString("operationId") == operationId) {
                return@runTransaction null
            }
            if (queuedOperation != null && queuedOperation.entityType in setOf("JOB", "EXPENSE")) {
                com.example.data.sync.QueuedWriteConflict.requireUnchanged(queuedOperation.entityType,
                    org.json.JSONObject(queuedOperation.payloadJson).optString("expectedBaseJson").takeIf { it.isNotBlank() },
                    existing.data, queuedOperation.entityId, queuedOperation.workspaceId)
            }
            if (reassigning) {
                val receipts = receiptRefs.mapNotNull { receipt -> transaction.get(receipt).data?.let { paymentFromFirestoreMap(it) } }
                com.example.data.sync.JobCustomerReassignment.requireSafe(jobEntryFromFirestoreMap(existing.data.orEmpty()), receipts, emptyList())
            }
            val payload = data.toMutableMap()
            payload.remove("operationProtocolVersion")
            payload.remove("authorizationCreatedAt")
            if (!existing.exists()) {
                payload["authorizationTimeStatus"] = "CLIENT_ORIGINAL_V2"
                payload["authorizationCreatedAt"] = com.google.firebase.Timestamp(java.util.Date((data["createdAt"] as Number).toLong()))
                payload["serverReceivedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
            } else {
                // Room/UI may supply a fallback label for legacy records; it is not new provenance.
                payload.remove("createdByRole")
                existing.data?.get("createdByRole")?.let { payload["createdByRole"] = it }
                existing.getTimestamp("authorizationCreatedAt")?.let { payload["authorizationCreatedAt"] = it }
                existing.getString("authorizationTimeStatus")?.let { payload["authorizationTimeStatus"] = it }
                existing.getTimestamp("serverReceivedAt")?.let { payload["serverReceivedAt"] = it }
                if (queuedOperation?.entityType == "JOB") {
                    val history = (existing.data?.get("customerReassignmentHistory") as? List<*>)?.toList().orEmpty()
                    if (reassigning) {
                        require(history.size < 1000) { "Customer reassignment audit needs separate archival review" }
                        payload["customerReassignmentHistory"] = history + mapOf(
                            "fromCustomerId" to originalCustomer, "toCustomerId" to data["customerId"],
                            "actorUid" to queuedOperation.actorUid, "operationId" to queuedOperation.operationId,
                            // Rules compare the complete audit map, including Firestore numeric types.
                            // JSON may have originally stored whole amounts as integers; do not convert
                            // those immutable server values to Double while copying the audit.
                            "actionTime" to queuedOperation.actionTime, "totalAmount" to existing.data?.get("totalAmount"),
                            "amountReceived" to existing.data?.get("amountReceived"), "pendingAmount" to existing.data?.get("pendingAmount"))
                    } else if (existing.contains("customerReassignmentHistory")) payload["customerReassignmentHistory"] = history
                }
            }
            if (merge) transaction.set(ref, payload, SetOptions.merge()) else transaction.set(ref, payload)
            null
        }.await()
    }

    suspend fun saveJobEntry(workspaceId: String, job: JobEntryEntity, uid: String?) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val docId = if (job.id > 0) job.id.toString() else IdGenerator.generateId().toString()
        val path = "workspaces/$workspaceId/entries/$docId"
        val currentAuthUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: uid
        Log.d("TRAC_PARTNER", "SHARED_WRITE authenticatedUid=$currentAuthUid workspace=$workspaceId entryId=$docId")
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$currentAuthUid")
        try {
            saveTimedDocument(firestore.collection("workspaces").document(workspaceId)
                .collection("entries").document(docId), job.toFirestoreMap(currentAuthUid), merge = true)
            Log.d("TRAC_FIRESTORE", "SUCCESS entryId=$docId")
        } catch (e: Exception) {
            val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "UNKNOWN"
            Log.e("TRAC_FIRESTORE", "FAILED code=$code message=${e.message} path=$path", e)
            throw e
        }
    }

    suspend fun deleteJob(workspaceId: String, jobId: Long) {
        throw IllegalStateException("Entry deletion must use the durable repository operation and checked command")
    }

    suspend fun saveExpense(workspaceId: String, expense: ExpenseEntity, uid: String?) {
        val firestore = db ?: throw IllegalStateException("FirebaseFirestore instance is null")
        val docId = if (expense.id > 0) expense.id.toString() else IdGenerator.generateId().toString()
        val path = "workspaces/$workspaceId/expenses/$docId"
        val currentAuthUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: uid
        Log.d("TRAC_PARTNER", "SHARED_WRITE authenticatedUid=$currentAuthUid workspace=$workspaceId expenseId=$docId")
        Log.d("TRAC_FIRESTORE", "writing $path with uid=$currentAuthUid")
        try {
            saveTimedDocument(firestore.collection("workspaces").document(workspaceId)
                .collection("expenses").document(docId), expense.toFirestoreMap(currentAuthUid), merge = true)
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

    suspend fun getCustomerDeletionProofFromServer(workspaceId: String, customerId: Long): Map<String, Any?>? =
        (db ?: throw IllegalStateException("Firestore unavailable")).collection("workspaces").document(workspaceId)
            .collection("customerDeletionProofs").document(customerId.toString())
            .get(com.google.firebase.firestore.Source.SERVER).await().data

    /** Explicit server read for rejected-action recovery; never falls back to the SDK cache. */
    suspend fun getOperationRecordFromServer(op: com.example.data.entity.PendingOperationEntity): Map<String, Any?>? {
        val collection = when(op.entityType) {
            "JOB" -> "entries"; "EXPENSE" -> "expenses"; "WITHDRAWAL" -> "withdrawals"; "PAYMENT" -> "collections"
            "CUSTOMER" -> "customers"
            else -> throw IllegalArgumentException("Unsupported recovery record")
        }
        val snapshot = (db ?: throw IllegalStateException("Firestore unavailable")).collection("workspaces")
            .document(op.workspaceId).collection(collection).document(op.entityId.toString())
            .get(com.google.firebase.firestore.Source.SERVER).await()
        val data = snapshot.data ?: return null
        require(data["workspaceId"] == op.workspaceId) { "Recovery workspace mismatch" }
        return data
    }

    suspend fun applyPendingOperation(op: com.example.data.entity.PendingOperationEntity) {
        if (com.example.BuildConfig.PHYSICAL_TEST && !FirebaseEnvironment.isStagingWorkspace(op.workspaceId))
            throw SecurityException("Physical test operation is outside the staging workspace")
        val firestore = db ?: throw IllegalStateException("Firestore is unavailable")
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid != op.actorUid) throw SecurityException("Operation actor does not match the authenticated user")
        val collection = when (op.entityType) {
            "JOB" -> "entries"
            "EXPENSE" -> "expenses"
            "CUSTOMER" -> "customers"
            "WITHDRAWAL" -> "withdrawals"
            "PAYMENT" -> "collections"
            "TRACTOR" -> "tractors"
            "EXTENSION" -> "extensions"
            else -> throw SecurityException("Unsupported queued operation")
        }
        val json = org.json.JSONObject(op.payloadJson)
        if (json.optInt("operationProtocolVersion") != 2 || op.actionTime <= 0) throw WorkspaceUpgradeRequiredException()
        // Server Rules reject even milliseconds of future skew. Confirm eligibility
        // before the first mutation, retaining original IDs/times and rejected states.
        actionTimeBarriers.getOrPut(op.workspaceId to op.actorUid) {
            com.example.data.sync.ServerActionTimeBarrier({ FirestoreServerTime.read(op.workspaceId, op.actorUid) })
        }.awaitEligible(op.actionTime)
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != op.actorUid) throw WorkspaceSessionChangedException()
        val dependencies = org.json.JSONArray()
        com.example.data.sync.OperationDependencies.references(op).filter { it != (op.entityType to op.entityId) }.forEach { (type,id) -> dependencies.put(org.json.JSONObject().put("entityType",type).put("entityId",id.toString())) }
        val payload = mutableMapOf<String, Any?>()
        json.keys().forEach { key -> payload[key] = json.opt(key).takeUnless { it == org.json.JSONObject.NULL } }
        payload.remove("operationProtocolVersion")
        payload.remove("expectedBaseJson")
        payload.remove(com.example.data.sync.CustomerProfileEdit.PAYLOAD_KEY)
        payload["protocolVersion"] = 2
        payload["operationType"] = op.mutationType
        payload["dependencies"] = (0 until dependencies.length()).map { index -> val item = dependencies.getJSONObject(index); mapOf("entityType" to item.getString("entityType"), "entityId" to item.getString("entityId")) }
        payload["operationId"] = op.operationId
        payload["actorUid"] = op.actorUid
        payload["actionTime"] = op.actionTime
        payload["isDeleted"] = op.mutationType == "DELETE"
        if (op.entityType == "PAYMENT") {
            val collectionRef = firestore.collection("workspaces").document(op.workspaceId).collection("collections").document(op.entityId.toString())
            val jobId = json.getLong("jobEntryId")
            val jobRef = firestore.collection("workspaces").document(op.workspaceId).collection("entries").document(jobId.toString())
            firestore.runTransaction { transaction ->
                val existing = transaction.get(collectionRef)
                val currentJob = transaction.get(jobRef)
                if (existing.getString("operationId") == op.operationId) return@runTransaction null
                if (!currentJob.exists() || currentJob.getString("workspaceId") != op.workspaceId) throw IllegalArgumentException("Collection source entry is missing")
                require(kotlin.math.abs((currentJob.getDouble("amountReceived") ?: 0.0) - json.getDouble("baseAmountReceived")) < 0.001 &&
                    kotlin.math.abs((currentJob.getDouble("pendingAmount") ?: 0.0) - json.getDouble("basePendingAmount")) < 0.001) { "Collection conflict: refresh the source due before retrying" }
                val jobJson = org.json.JSONObject(json.getString("entrySnapshotJson"))
                val entry = mutableMapOf<String, Any?>()
                jobJson.keys().forEach { key -> entry[key] = jobJson.opt(key).takeUnless { it == org.json.JSONObject.NULL } }
                entry["collectionId"] = op.entityId.toString()
                entry["operationId"] = op.operationId
                entry["actorUid"] = op.actorUid
                entry["actionTime"] = op.actionTime
                entry["isDeleted"] = false
                payload.remove("authorizationCreatedAt")
                if (!existing.exists()) {
                    com.example.data.auth.CollectionPolicy.requireDate(json.getLong("collectedAt"), currentJob.getLong("createdAt") ?: 0L)
                    payload["authorizationTimeStatus"] = "CLIENT_ORIGINAL_V2"
                    payload["authorizationCreatedAt"] = com.google.firebase.Timestamp(java.util.Date(json.getLong("createdAt")))
                    payload["serverReceivedAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
                }
                else {
                    existing.getTimestamp("authorizationCreatedAt")?.let { payload["authorizationCreatedAt"] = it }
                    existing.getTimestamp("serverReceivedAt")?.let { payload["serverReceivedAt"] = it }
                    // The Room payment model does not contain authorization provenance. Preserve the stored value.
                    payload.remove("authorizationTimeStatus")
                    existing.getString("authorizationTimeStatus")?.let { payload["authorizationTimeStatus"] = it }
                }
                transaction.set(collectionRef, payload)
                // A Collection changes its due, never concurrent source-record edits.
                transaction.update(jobRef, mapOf(
                    "amountReceived" to entry["amountReceived"], "pendingAmount" to entry["pendingAmount"],
                    "protocolVersion" to 2, "operationType" to op.mutationType, "dependencies" to payload["dependencies"],
                    "collectionId" to op.entityId.toString(), "operationId" to op.operationId,
                    "actorUid" to op.actorUid, "actionTime" to op.actionTime, "isDeleted" to false))
                null
            }.await()
            return
        }
        // Original queued action time determines eligibility; arrival time is separate audit evidence.
        val ref = firestore.collection("workspaces").document(op.workspaceId).collection(collection).document(op.entityId.toString())
        if (op.mutationType == "DELETE") {
            if (op.entityType == "JOB") {
                FirebaseLedgerDeletion.delete(op)
                return
            }
            if (op.entityType == "CUSTOMER") {
                FirebaseLedgerDeletion.delete(op)
                return
            }
            val existing = ref.get(com.google.firebase.firestore.Source.SERVER).await()
            if (!existing.exists()) return // An interrupted acknowledged delete is idempotent.
            if (existing.getBoolean("isDeleted") != true) {
                // A delete tombstone may change command metadata, but must preserve
                // the record's immutable creation and authorization provenance.
                // Replacing the document with the queued Room snapshot drops those
                // fields and is rejected by the deployed Rules' original() check.
                ref.update(deleteTombstoneFields(op.operationId, op.actorUid, op.actionTime,
                    payload["dependencies"] ?: emptyList<Any>(),
                    com.google.firebase.firestore.FieldValue.serverTimestamp())).await()
            }
            else require(existing.getString("operationId") == op.operationId) { "Delete belongs to another pending operation" }
            ref.delete().await()
        } else if (op.entityType == "CUSTOMER") {
            firestore.runTransaction { transaction ->
                val existing = transaction.get(ref)
                if (existing.getString("operationId") == op.operationId) return@runTransaction null
                val patch = json.optString(com.example.data.sync.CustomerProfileEdit.PAYLOAD_KEY)
                if (patch.isNotBlank()) {
                    require(existing.exists() && existing.getString("workspaceId") == op.workspaceId && existing.getBoolean("isDeleted") != true) {
                        "Customer no longer exists; original profile edit retained for review"
                    }
                    val changes = com.example.data.sync.CustomerProfileEdit.changes(patch, existing.data.orEmpty())
                    // Updating only editable fields preserves immutable provenance, unrelated edits and totals.
                    transaction.update(ref, changes + payload.filterKeys { it in setOf(
                        "protocolVersion", "operationType", "dependencies", "operationId", "actorUid", "actionTime", "isDeleted", "updatedAt") })
                } else {
                    require(!existing.exists()) { "Customer edit base is unavailable; original action retained for review" }
                    transaction.set(ref, payload)
                }
                null
            }.await()
        } else if (op.entityType in setOf("JOB", "EXPENSE", "WITHDRAWAL")) {
            saveTimedDocument(ref, payload, queuedOperation = op)
        } else ref.set(payload).await()
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
            saveTimedDocument(firestore.collection("workspaces").document(workspaceId)
                .collection("withdrawals").document(docId), withdrawal.toFirestoreMap(currentAuthUid), merge = true)
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
            if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != uid ||
                resolveAuthoritativeRole(uid, workspaceId) == null) throw SecurityException("Workspace selection requires current membership")
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

    suspend fun archiveMember(workspaceId: String, targetUid: String): com.example.data.entity.DeletedMemberEntity {
        val firestore = db ?: throw IllegalStateException("Firestore is unavailable")
        val actorUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (resolveAuthoritativeRole(actorUid, workspaceId) != com.example.data.auth.RoleUtils.ROLE_OWNER) throw SecurityException("Only Owner can remove members")
        require(targetUid.isNotBlank() && targetUid != actorUid) { "Owner membership cannot be removed" }
        val memberRef = firestore.collection("workspaces").document(workspaceId).collection("members").document(targetUid)
        val archiveRef = firestore.collection("workspaces").document(workspaceId).collection("deletedUsers").document(targetUid)
        return firestore.runTransaction { tx ->
            val memberDoc = tx.get(memberRef)
            val archiveDoc = tx.get(archiveRef)
            if (archiveDoc.exists()) {
                require(memberDoc.getString("status").equals("REMOVED", true)) { "Member is not revoked; refresh before retrying removal" }
                val data = archiveDoc.data!!
                return@runTransaction com.example.data.entity.DeletedMemberEntity(workspaceId, targetUid, data["displayName"] as? String ?: "",
                    data["role"] as? String ?: "", (data["removedAt"] as? Number)?.toLong() ?: 0L, data["removedByUid"] as? String ?: "")
            }
            if (!memberDoc.exists() || memberDoc.getString("role").orEmpty().equals("owner", true)) throw SecurityException("Member cannot be removed")
            val now = System.currentTimeMillis()
            val member = com.example.data.entity.DeletedMemberEntity(workspaceId, targetUid, memberDoc.getString("displayName").orEmpty(),
                memberDoc.getString("role").orEmpty(), now, actorUid)
            tx.set(archiveRef, mapOf("workspaceId" to workspaceId, "uid" to targetUid, "displayName" to member.displayName,
                "role" to member.role, "removedAt" to now, "removedByUid" to actorUid, "purgeState" to "ARCHIVED"))
            tx.update(memberRef, mapOf("status" to "REMOVED"))
            member
        }.await()
    }

    suspend fun getDeletedMembers(workspaceId: String): List<com.example.data.entity.DeletedMemberEntity> {
        val firestore = db ?: throw IllegalStateException("Firestore is unavailable")
        return firestore.collection("workspaces").document(workspaceId).collection("deletedUsers").get().await().documents.mapNotNull { doc ->
            val data = doc.data ?: return@mapNotNull null
            com.example.data.entity.DeletedMemberEntity(workspaceId, doc.id, data["displayName"] as? String ?: "", data["role"] as? String ?: "",
                (data["removedAt"] as? Number)?.toLong() ?: 0L, data["removedByUid"] as? String ?: "", data["purgeState"] as? String ?: "ARCHIVED")
        }
    }

    /** Owner-confirmed, resumable purge. The immutable manifest is written before the first delete. */
    suspend fun permanentlyDeleteMember(workspaceId: String, targetUid: String): String = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val firestore = db ?: throw IllegalStateException("Firestore is unavailable")
        val actorUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (resolveAuthoritativeRole(actorUid, workspaceId) != com.example.data.auth.RoleUtils.ROLE_OWNER) throw SecurityException("Only Owner can permanently delete members")
        val ws = firestore.collection("workspaces").document(workspaceId)
        val archiveRef = ws.collection("deletedUsers").document(targetUid)
        val archive = archiveRef.get(com.google.firebase.firestore.Source.SERVER).await()
        if (!archive.exists()) throw SecurityException("Member must be archived first")
        if (archive.getString("purgeState") == "PURGED") return@withContext archive.getString("backupPath").orEmpty()
        // Capture this new Owner action once, then reuse the committed identity on every retry.
        val purgeActionTime = System.currentTimeMillis()
        val purgeOperationId = "purge-" + java.util.UUID.randomUUID()
        // Freeze affected collections before reading the backup snapshot. No deletion is authorized until backupReady.
        firestore.runTransaction { tx ->
            val current = tx.get(archiveRef)
            if (current.getString("purgeState") == "ARCHIVED") tx.update(archiveRef, mapOf("purgeState" to "PURGE_PENDING", "backupReady" to false, "backupSha256" to "", "purgeManifest" to emptyList<String>(),
                "purgeOperationId" to purgeOperationId, "purgeActionTime" to purgeActionTime, "purgeActorUid" to actorUid))
        }.await()
        val preparedPurge = archiveRef.get(com.google.firebase.firestore.Source.SERVER).await()
        val purgeProtocol = PurgeReconciliationProtocol.fromManifest(preparedPurge.data.orEmpty(), actorUid)
        var manifest = if (archive.getBoolean("backupReady") == false) null else archive.get("purgeManifest") as? List<*>
        var backupPath = archive.getString("backupPath").orEmpty()
        if (manifest == null) {
            val names = listOf("entries", "expenses", "withdrawals", "tractors", "extensions", "collections", "customers")
            val documents = mutableMapOf<String, List<com.google.firebase.firestore.DocumentSnapshot>>()
            for (name in names) documents[name] = ws.collection(name).get(com.google.firebase.firestore.Source.SERVER).await().documents
            fun related(doc: com.google.firebase.firestore.DocumentSnapshot) = listOf("createdByUid", "entryForUid", "expenseForUid", "financialAttributionUid", "targetPartnerUid").any { doc.getString(it) == targetUid }
            val removedJobs = documents.getValue("entries").filter(::related)
            val jobIds = removedJobs.map { it.id }.toSet()
            val removedCollections = documents.getValue("collections").filter { it.getString("collectedByUid") == targetUid || (it.getLong("jobEntryId") ?: 0).toString() in jobIds }
            val collectionIds = removedCollections.map { it.id }.toSet()
            val retainedCustomerIds = documents.getValue("entries").filter { it.id !in jobIds }.map { it.getLong("customerId") }.toSet() +
                documents.getValue("collections").filter { it.id !in collectionIds }.map { it.getLong("customerId") }.toSet()
            val selected = mutableListOf<com.google.firebase.firestore.DocumentSnapshot>()
            selected.addAll(removedCollections)
            selected.addAll(removedJobs)
            selected.addAll(documents.getValue("expenses").filter { related(it) || (it.getLong("relatedJobId") ?: 0).toString() in jobIds })
            selected.addAll(documents.getValue("withdrawals").filter(::related))
            selected.addAll(documents.getValue("tractors").filter { it.getString("createdByUid") == targetUid })
            selected.addAll(documents.getValue("extensions").filter { it.getString("createdByUid") == targetUid })
            selected.addAll(documents.getValue("customers").filter { it.getString("createdByUid") == targetUid && it.getLong("id") !in retainedCustomerIds })
            // Restorable workspace export, verified on disk before any destructive write.
            val export = org.json.JSONObject().put("workspaceId", workspaceId).put("schemaVersion", 18)
            for ((name, docs) in documents) {
                val records = org.json.JSONArray()
                docs.forEach { records.put(org.json.JSONObject().put("id", it.id).put("data", FirestoreBackupCodec.encode(it.data.orEmpty()))) }
                export.put(name, records)
            }
            val file = java.io.File(context.filesDir, "purge-backup-${workspaceId}-${System.currentTimeMillis()}.json")
            val bytes = export.toString().toByteArray(Charsets.UTF_8)
            file.writeBytes(bytes)
            require(file.readBytes().contentEquals(bytes)) { "Backup verification failed" }
            val restored = org.json.JSONObject(file.readText(Charsets.UTF_8))
            for ((name, docs) in documents) {
                val rows = restored.getJSONArray(name)
                require(rows.length() == docs.size) { "Backup record count mismatch" }
                docs.forEachIndexed { index, source ->
                    val row = rows.getJSONObject(index)
                    val decoded = FirestoreBackupCodec.decode(row.getJSONObject("data")) { firestore.document(it) }
                    require(row.getString("id") == source.id && decoded == source.data.orEmpty()) { "Backup restore verification failed" }
                }
            }
            val checksum = java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
            val chunks = bytes.toList().chunked(200000).map { it.toByteArray() }
            for ((index, chunk) in chunks.withIndex()) {
                val ref = archiveRef.collection("backupChunks").document("${checksum}_$index")
                val encoded = android.util.Base64.encodeToString(chunk, android.util.Base64.NO_WRAP)
                val prior = ref.get(com.google.firebase.firestore.Source.SERVER).await()
                if (prior.exists()) require(prior.getString("data") == encoded) { "Backup chunk differs" }
                else ref.set(mapOf("workspaceId" to workspaceId, "memberUid" to targetUid, "index" to index, "backupId" to checksum, "data" to encoded)).await()
            }
            backupPath = file.absolutePath
            manifest = selected.map { it.reference.path }
            // Retained jobs lose only collections being physically purged. Save the adjustments in the manifest.
            val adjustments = removedCollections.filter { it.getString("status") == "ACTIVE" && (it.getLong("jobEntryId") ?: 0).toString() !in jobIds }
                .groupBy { (it.getLong("jobEntryId") ?: 0).toString() }.map { (id, collections) ->
                    val job = documents.getValue("entries").firstOrNull { it.id == id } ?: throw IllegalStateException("Collection source is missing")
                    val amount = collections.sumOf { it.getDouble("amount") ?: 0.0 }
                    mapOf("path" to job.reference.path, "amount" to amount,
                        "dependencies" to collections.map { mapOf("entityType" to "PAYMENT", "entityId" to it.id) })
                }
            firestore.runTransaction { tx ->
                val current = tx.get(archiveRef)
                check(current.getBoolean("backupReady") != true) { "Another device prepared this purge; retry using its verified backup" }
                tx.update(archiveRef, mapOf("purgeState" to "PURGE_PENDING", "purgeManifest" to manifest,
                    "reconciliations" to adjustments, "backupPath" to backupPath, "backupSha256" to checksum, "backupChunkCount" to chunks.size, "cloudPurgeFinished" to false, "backupReady" to true))
            }.await()
        }
        val pending = archiveRef.get(com.google.firebase.firestore.Source.SERVER).await()
        var backupFile = java.io.File(backupPath)
        if (!backupFile.isFile) {
            val checksum = pending.getString("backupSha256") ?: throw IllegalStateException("Backup checksum is missing")
            val chunks = pending.getLong("backupChunkCount")?.toInt() ?: throw IllegalStateException("Restore the legacy purge backup before retrying")
            require(chunks in 1..10000) { "Invalid backup chunk count" }
            backupFile = java.io.File(context.filesDir, "purge-restored-$checksum.json")
            backupFile.outputStream().use { output -> for (index in 0 until chunks) {
                val chunk = archiveRef.collection("backupChunks").document("${checksum}_$index").get(com.google.firebase.firestore.Source.SERVER).await()
                val data = chunk.getString("data") ?: throw IllegalStateException("Backup chunk is missing")
                output.write(android.util.Base64.decode(data, android.util.Base64.NO_WRAP))
            } }
            backupPath = backupFile.absolutePath
        }
        require(backupFile.isFile) { "Verified purge backup is unavailable; restore the backup before retrying" }
        val backupHash = java.security.MessageDigest.getInstance("SHA-256").digest(backupFile.readBytes()).joinToString("") { "%02x".format(it) }
        require(backupHash == pending.getString("backupSha256")) { "Purge backup checksum mismatch" }
        if (pending.getBoolean("cloudPurgeFinished") == true) return@withContext backupPath
        if (pending.getBoolean("backupReady") != true) archiveRef.update("backupReady", true).await()
        val reconciliations = pending.get("reconciliations") as? List<*> ?: emptyList<Any>()
        for ((chunkIndex, chunk) in reconciliations.chunked(20).withIndex()) {
        firestore.runTransaction { tx ->
            val state = tx.get(archiveRef)
            if (state.getBoolean("financialReconciled") == true) return@runTransaction null
            val completed = (state.get("reconciledChunks") as? List<*>)?.mapNotNull { (it as? Number)?.toLong() }.orEmpty()
            if (chunkIndex.toLong() in completed) return@runTransaction null
            require(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid == actorUid) { "Account changed during purge" }
            require(state.getString("purgeState") == "PURGE_PENDING" && state.getBoolean("backupReady") == true) { "Purge is not ready" }
            require(PurgeReconciliationProtocol.fromManifest(state.data.orEmpty(), actorUid) == purgeProtocol) { "Purge identity changed" }
            val adjustments = chunk.map { raw ->
                val adjustment = raw as? Map<*, *> ?: throw IllegalStateException("Invalid purge reconciliation")
                val path = adjustment["path"] as? String ?: error("Missing purge Entry path")
                require(path.split('/').let { it.size == 4 && it[0] == "workspaces" && it[1] == workspaceId && it[2] == "entries" }) { "Purge crossed workspace boundary" }
                val ref = firestore.document(path)
                val job = tx.get(ref)
                val amount = (adjustment["amount"] as? Number)?.toDouble() ?: error("Missing purge amount")
                val dependencies = (adjustment["dependencies"] as? List<*>)?.map { item ->
                    val dependency = item as? Map<*, *> ?: error("Invalid purge dependency")
                    require(dependency["entityType"] == "PAYMENT" && (dependency["entityId"] as? String)?.toLongOrNull()?.let { it > 0 } == true) { "Invalid purge Collection dependency" }
                    mapOf("entityType" to "PAYMENT", "entityId" to dependency["entityId"] as String)
                } ?: throw WorkspaceUpgradeRequiredException()
                require(dependencies.isNotEmpty() && dependencies.size <= 100) { "Purge dependency count requires review" }
                Triple(ref, job, purgeProtocol.entryUpdate(workspaceId, targetUid, path, job.data.orEmpty(), amount, dependencies))
            }
            // Update the retained Customer's aggregate in the same transaction/checkpoint.
            val customerDeltas = adjustments.groupBy { (_, job, _) -> job.getLong("customerId") ?: error("Missing retained Customer") }
            val customerUpdates = customerDeltas.map { (id, jobs) ->
                require(id > 0) { "Invalid retained Customer" }
                val ref = ws.collection("customers").document(id.toString())
                val customer = tx.get(ref)
                require(customer.exists() && customer.getString("workspaceId") == workspaceId && customer.getBoolean("isDeleted") != true) { "Retained Customer is unavailable" }
                val amount = jobs.sumOf { (_, job, update) -> (job.getDouble("amountReceived") ?: 0.0) - (update["amountReceived"] as Double) }
                val paid = customer.getDouble("totalPaid") ?: error("Missing Customer paid amount")
                val due = customer.getDouble("balanceDue") ?: error("Missing Customer due")
                val billed = customer.getDouble("totalBilled") ?: error("Missing Customer billed amount")
                require(listOf(paid, due, billed).all { it.isFinite() && it >= 0 } && paid >= amount && kotlin.math.abs(billed - paid - due) < 0.001) { "Customer totals require reconciliation before purge" }
                ref to (purgeProtocol.envelope(workspaceId, "customer-$id-chunk-$chunkIndex", jobs.map { mapOf("entityType" to "JOB", "entityId" to it.first.id) }) +
                    mapOf("totalPaid" to paid - amount, "balanceDue" to due + amount))
            }
            adjustments.forEach { (ref, _, update) -> tx.update(ref, update) }
            customerUpdates.forEach { (ref, update) -> tx.update(ref, update) }
            tx.update(archiveRef, "reconciledChunks", completed + chunkIndex.toLong())
            null
        }.await()
        }
        archiveRef.update("financialReconciled", true).await()
        for (chunk in manifest.orEmpty().map { it as String }.chunked(20)) {
            require(chunk.all { it.startsWith("workspaces/$workspaceId/") }) { "Purge crossed workspace boundary" }
            val customers = chunk.filter { it.startsWith("workspaces/$workspaceId/customers/") }
            val recordRefs = chunk.filterNot { it in customers }.map(firestore::document)
            firestore.runTransaction { tx ->
                // A prior attempt may have committed only this delete chunk. Read every ref
                // before writing, and delete only documents that still exist.
                val existing = recordRefs.map(tx::get)
                existing.filter { it.exists() }.forEach { tx.delete(it.reference) }
            }.await()
            customers.forEach { path ->
                val id = path.substringAfterLast('/')
                WorkspaceCommandService.deleteCustomer(workspaceId, id, actorUid,
                    "${purgeProtocol.operationId}:customer-$id", purgeProtocol.actionTime, org.json.JSONArray())
            }
        }
        archiveRef.update(mapOf("cloudPurgeFinished" to true)).await()
        backupPath
    }

    suspend fun memberPurgeManifest(workspaceId: String, uid: String): Set<Pair<String, Long>> {
        val firestore = db ?: throw IllegalStateException("Firestore unavailable")
        val actor = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        require(resolveAuthoritativeRole(actor, workspaceId) == com.example.data.auth.RoleUtils.ROLE_OWNER) { "Owner access required" }
        val archive = firestore.document("workspaces/$workspaceId/deletedUsers/$uid").get(com.google.firebase.firestore.Source.SERVER).await()
        val types = mapOf("entries" to "JOB", "expenses" to "EXPENSE", "withdrawals" to "WITHDRAWAL", "collections" to "PAYMENT", "tractors" to "TRACTOR", "extensions" to "EXTENSION", "customers" to "CUSTOMER")
        return (archive.get("purgeManifest") as? List<*>).orEmpty().mapNotNull { path ->
            val parts = (path as? String)?.split('/') ?: return@mapNotNull null
            if (parts.size != 4 || parts[0] != "workspaces" || parts[1] != workspaceId) throw SecurityException("Invalid purge scope")
            val type = types[parts[2]] ?: throw SecurityException("Invalid purge record type")
            type to (parts[3].toLongOrNull() ?: throw SecurityException("Invalid purge record ID"))
        }.toSet()
    }

    suspend fun completeMemberPurge(workspaceId: String, uid: String) {
        val firestore = db ?: throw IllegalStateException("Firestore unavailable")
        val actor = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        require(resolveAuthoritativeRole(actor, workspaceId) == com.example.data.auth.RoleUtils.ROLE_OWNER) { "Owner access required" }
        val ref = firestore.document("workspaces/$workspaceId/deletedUsers/$uid")
        val archive = ref.get(com.google.firebase.firestore.Source.SERVER).await()
        if (archive.getString("purgeState") == "PURGED") return
        require(archive.getBoolean("cloudPurgeFinished") == true) { "Cloud purge has not completed" }
        ref.update(mapOf("purgeState" to "PURGED", "purgedAt" to System.currentTimeMillis(), "purgedByUid" to actor)).await()
    }

    suspend fun getWorkspaceMembers(workspaceId: String): List<WorkspaceMember> {
        val firestore = db ?: throw IllegalStateException("Firestore unavailable")
        val snapshot = firestore.collection("workspaces").document(workspaceId).collection("members")
            .get(com.google.firebase.firestore.Source.SERVER).await()
        return snapshot.documents.mapNotNull { doc -> doc.data?.let {
            WorkspaceMember.fromMap(it).copy(uid = doc.id, workspaceId = workspaceId)
        } }
    }

    suspend fun getEntryFromServer(workspaceId: String, id: Long): JobEntryEntity? {
        val firestore = db ?: throw IllegalStateException("Firestore unavailable")
        val doc = firestore.collection("workspaces").document(workspaceId).collection("entries").document(id.toString())
            .get(com.google.firebase.firestore.Source.SERVER).await()
        return doc.data?.let { jobEntryFromFirestoreMap(it, fallbackId = id, fallbackWorkspaceId = workspaceId) }
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

    /** Update the authenticated member's personal display name in workspace mirrors. */
    suspend fun updateMemberDisplayName(workspaceId: String, memberUid: String, displayName: String) {
        val firestore = db ?: return
        if (workspaceId.isBlank() || memberUid.isBlank() || displayName.isBlank()) return
        val update = mapOf(
            "displayName" to displayName.trim(),
            "updatedAt" to System.currentTimeMillis()
        )
        firestore.collection("workspaces").document(workspaceId)
            .collection("members").document(memberUid)
            .set(update, SetOptions.merge())
            .await()
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
            if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != uid ||
                resolveAuthoritativeRole(uid, workspaceId) == null) throw SecurityException("Workspace selection requires current membership")
            val userDocRef = firestore.collection("users").document(uid)
            val userSnap = userDocRef.get().await()
            val existingWs = (userSnap.get("workspaces") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
            val updatedWs = (existingWs + workspaceId).distinct()
            val updates = mapOf(
                "defaultWorkspaceId" to workspaceId,
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

    /**
     * Removes only an unresolved pending partner invitation.
     *
     * The transaction re-reads the pending documents, the workspace owner,
     * the phone directory, active members, and attendees immediately before
     * deleting anything. If registration completed while the UI was stale,
     * this returns REGISTERED and leaves the real membership untouched.
     */
    suspend fun deletePendingPartnerInvitation(
        workspaceId: String,
        normalizedPhone: String,
        actorUid: String
    ): Result<PendingPartnerCleanupResult> {
        val formattedPhone = normalizePhoneNumber(normalizedPhone)
        val clean10 = formattedPhone.filter { it.isDigit() }.takeLast(10)
        if (workspaceId.isBlank() || formattedPhone.isBlank() || clean10.length != 10 || actorUid.isBlank()) {
            return Result.failure(IllegalArgumentException("Invalid pending invitation identity"))
        }
        val firestore = db ?: return Result.failure(IllegalStateException("Firestore is not initialized"))

        val groupRef = firestore.collection("collaborationGroups").document(workspaceId)
        val workspaceRef = firestore.collection("workspaces").document(workspaceId)
        val pendingCollection = groupRef.collection("pendingPhones")
        val pendingFormattedRef = pendingCollection.document(formattedPhone)
        val pendingCleanRef = pendingCollection.document(clean10)
        val directoryRef = firestore.collection("phoneDirectory").document(formattedPhone)
        val membersCollection = workspaceRef.collection("members")
        val attendeesCollection = workspaceRef.collection("attendees")

        return try {
            // The Firebase SDK used by this project exposes document reads on
            // transactions, so first collect the candidate document refs and
            // then re-read those exact refs transactionally below.
            val memberRefs = membersCollection.get().await().documents.map { it.reference }
            val attendeeRefs = attendeesCollection.get().await().documents.map { it.reference }
            val result = firestore.runTransaction { transaction ->
                if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != actorUid) {
                    throw SecurityException("Authentication changed during pending cleanup")
                }
                val workspaceSnapshot = transaction.get(workspaceRef)
                val groupSnapshot = transaction.get(groupRef)
                val ownerUid = workspaceSnapshot.getString("ownerUid")
                    ?: groupSnapshot.getString("ownerUid")
                    ?: ""
                val isOwner = ownerUid == actorUid
                val actorMember = transaction.get(membersCollection.document(actorUid))
                if (!isOwner && (actorMember.getString("status") != "active" ||
                        !actorMember.getString("role").orEmpty().equals("Partner", true))) {
                    throw SecurityException("Active workspace Partner membership is required")
                }

                val pendingSnapshots = listOf(
                    transaction.get(pendingFormattedRef),
                    transaction.get(pendingCleanRef)
                )
                val existingPendingSnapshots = pendingSnapshots.filter { it.exists() && it.data != null }
                if (existingPendingSnapshots.isEmpty() && !isOwner) {
                    throw SecurityException("Pending creator ownership can no longer be verified; ask the Owner to clean up the legacy placeholder")
                }

                val pendingDataList = existingPendingSnapshots.map { it.data ?: emptyMap() }
                if (pendingDataList.any { data ->
                        (data["groupId"] as? String)?.let { it != workspaceId } == true
                    }) throw SecurityException("Pending workspace does not match its path")
                if (pendingDataList.any { data ->
                        !(data["uid"] as? String).isNullOrBlank() || !(data["partnerUid"] as? String).isNullOrBlank()
                    }) return@runTransaction PendingPartnerCleanupResult.REGISTERED
                val hasNonPendingAlias = pendingDataList.any { data ->
                    val status = data["status"] as? String ?: "waiting_for_registration"
                    status.isNotBlank() && !status.equals("waiting_for_registration", ignoreCase = true)
                }
                if (hasNonPendingAlias) {
                    return@runTransaction PendingPartnerCleanupResult.REGISTERED
                }

                // Legacy records may have no creator UID. The workspace owner
                // check above is the authoritative ownership check in that case;
                // no UID is inferred from a phone number or display name.
                val pendingCreatorUids = pendingDataList
                    .map { (it["addedByUid"] as? String)?.trim().orEmpty() }
                    .filter { it.isNotBlank() }
                    .distinct()
                if (!isOwner && (pendingCreatorUids != listOf(actorUid) || pendingDataList.any {
                        (it["addedByUid"] as? String) != actorUid ||
                            !(it["role"] as? String).orEmpty().equals("Operator", true) ||
                            ((it["groupId"] as? String)?.let { groupId -> groupId != workspaceId } == true)
                    })) {
                    throw SecurityException("Pending invitation belongs to another actor")
                }

                val directorySnapshot = transaction.get(directoryRef)
                val resolvedUid = directorySnapshot.getString("uid")?.trim().orEmpty()
                val memberSnapshots = memberRefs.map { transaction.get(it) }
                val activeMemberExists = memberSnapshots.any { member ->
                    val data = member.data ?: return@any false
                    val status = (data["status"] as? String).orEmpty()
                    if (!status.equals("active", ignoreCase = true)) return@any false
                    val memberUid = (data["uid"] as? String ?: member.id).trim()
                    val memberPhone = normalizePhoneNumber((data["phoneNumber"] as? String).orEmpty())
                    (!resolvedUid.isNullOrBlank() && memberUid == resolvedUid) ||
                        (memberPhone.isNotBlank() && memberPhone.filter { it.isDigit() }.takeLast(10) == clean10)
                }
                if (resolvedUid.isNotBlank() || activeMemberExists) {
                    return@runTransaction PendingPartnerCleanupResult.REGISTERED
                }

                val attendeeSnapshots = attendeeRefs.map { transaction.get(it) }
                val pendingAttendees = attendeeSnapshots.filter { attendee ->
                    val data = attendee.data ?: return@filter false
                    val phone = normalizePhoneNumber((data["phone"] as? String).orEmpty())
                    phone.filter { it.isDigit() }.takeLast(10) == clean10
                }
                if (pendingAttendees.any {
                        !it.getString("partnerUid").isNullOrBlank() || it.getString("status").orEmpty().equals("CONNECTED", true)
                    }) return@runTransaction PendingPartnerCleanupResult.REGISTERED
                if (!isOwner && pendingAttendees.isNotEmpty()) {
                    throw SecurityException("This legacy placeholder requires Owner cleanup under the existing Firestore permissions")
                }
                pendingAttendees.forEach { attendee ->
                    val data = attendee.data ?: return@forEach
                    val phone = normalizePhoneNumber((data["phone"] as? String).orEmpty())
                    val partnerUid = (data["partnerUid"] as? String).orEmpty().trim()
                    val status = (data["status"] as? String).orEmpty()
                    if (phone.filter { it.isDigit() }.takeLast(10) == clean10 &&
                        partnerUid.isBlank() && !status.equals("CONNECTED", ignoreCase = true)) {
                        transaction.delete(attendee.reference)
                    }
                }
                transaction.delete(pendingFormattedRef)
                if (pendingCleanRef.path != pendingFormattedRef.path) {
                    transaction.delete(pendingCleanRef)
                }
                if (existingPendingSnapshots.isEmpty() && pendingAttendees.isEmpty()) PendingPartnerCleanupResult.ALREADY_GONE
                else PendingPartnerCleanupResult.DELETED
            }.await()
            Log.d("TRAC_PARTNER", "PENDING_INVITATION_CLEANUP workspace=$workspaceId phone=$formattedPhone result=$result")
            Result.success(result)
        } catch (e: Exception) {
            Log.w("TRAC_PARTNER", "Pending invitation cleanup failed: ${e.message}")
            Result.failure(e)
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
                doc.data?.takeUnless { it["isDeleted"] == true }?.let { PendingPartnerPhone.fromMap(it) }
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

    /** Legacy caller compatibility: discovery only; a phone/index can never create membership. */
    suspend fun resolveAndBindPartnerInvitation(
        userUid: String,
        rawPhone: String?,
        userDisplayName: String?,
        knownWorkspaceIds: List<String> = emptyList()
    ): UserProfile? {
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid != userUid)
            throw SecurityException("Authenticated identity mismatch")
        val memberships = discoverAuthoritativeMemberships(userUid)
        val membership = memberships.firstOrNull { !com.example.data.auth.RoleUtils.isOwner(it.role) } ?: return null
        return UserProfile(uid = userUid, displayName = userDisplayName, defaultWorkspaceId = membership.workspaceId,
            workspaces = memberships.map { it.workspaceId }, role = null)
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
                doc.data?.takeUnless { it["isDeleted"] == true }?.let { UserCollaborationGroupIndex.fromMap(it) }
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
