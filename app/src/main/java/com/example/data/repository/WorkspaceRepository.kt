package com.example.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.ChecklistItemEntity
import com.example.data.entity.WorkTypeExtensionEntity
import com.example.data.firebase.FirestoreRepository
import com.example.data.firebase.UserProfile
import com.example.data.firebase.AccountLookupResult
import com.example.data.firebase.Workspace
import com.example.data.firebase.WorkspaceInvitation
import com.example.data.firebase.WorkspaceMember
import com.example.data.firebase.appSettingsFromFirestoreMap
import com.example.data.firebase.toFirestoreMap
import com.example.data.entity.PendingOperationEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.example.data.sync.PendingDeleteManager
import com.example.data.util.IdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

sealed class WorkspaceInitState {
    object Uninitialized : WorkspaceInitState()
    object Loading : WorkspaceInitState()
    data class Ready(val workspaceId: String) : WorkspaceInitState()
    data class CachedReady(val workspaceId: String) : WorkspaceInitState()
    data class SelectionRequired(val workspaces: List<Workspace>) : WorkspaceInitState()
    data class Error(val exception: Throwable) : WorkspaceInitState()
}

sealed interface SettingsSyncState {
    object Uninitialized : SettingsSyncState
    object Loading : SettingsSyncState
    data class LoadedFromCloud(val settings: AppSettingsEntity) : SettingsSyncState
    data class CreatedInCloud(val settings: AppSettingsEntity) : SettingsSyncState
    data class Error(val message: String) : SettingsSyncState
}

/**
 * Preserves a Partner's selected beneficiary and always records the real creator.
 * Operators remain self-only. Call only after validating target membership.
 */
internal fun enforceEntryIdentity(
    job: JobEntryEntity,
    actorUid: String,
    actorRole: String,
    actorDisplayName: String
): JobEntryEntity {
    require(actorUid.isNotBlank()) { "Authenticated UID is required for a self-only entry" }
    val normalizedRole = com.example.data.auth.RoleUtils.normalizeRole(actorRole)
    require(
        normalizedRole == com.example.data.auth.RoleUtils.ROLE_PARTNER ||
            normalizedRole == com.example.data.auth.RoleUtils.ROLE_OPERATOR
    ) { "Only Partner or Operator members can use self-only entry identity" }
    return job.copy(
        createdByUid = actorUid,
        createdByRole = normalizedRole,
        operatorName = if (normalizedRole == com.example.data.auth.RoleUtils.ROLE_OPERATOR)
            actorDisplayName.trim() else job.operatorName.ifBlank { actorDisplayName.trim() },
        addedByPartner = actorDisplayName.trim(),
        entryForUid = if (normalizedRole == com.example.data.auth.RoleUtils.ROLE_OPERATOR) actorUid else job.entryForUid.ifBlank { actorUid },
        financialAttributionUid = if (normalizedRole == com.example.data.auth.RoleUtils.ROLE_OPERATOR) actorUid else job.entryForUid.ifBlank { actorUid }
    )
}

internal fun effectiveEntryStartTimeForEdit(
    existingJob: JobEntryEntity?,
    editedJob: JobEntryEntity
): Long {
    return if (existingJob != null && existingJob.createdAt <= 0L) {
        existingJob.startTimeMillis
    } else {
        editedJob.startTimeMillis
    }
}

/** Seed only a missing settings row, using the verified workspace's business identity. */
internal fun initialWorkspaceSettings(
    workspaceId: String,
    workspaceName: String,
    isOwner: Boolean,
    actorName: String,
    actorPhone: String
): AppSettingsEntity = AppSettingsEntity(
    workspaceId = workspaceId,
    businessName = workspaceName,
    ownerName = if (isOwner) actorName else "",
    businessPhone = if (isOwner) actorPhone else ""
)

class WorkspaceRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val firestoreRepository: FirestoreRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val TAG = "WorkspaceRepository"
    private val replicaUpdates = com.example.data.sync.ReplicaDispatcher(scope) { failure ->
        Log.e(TAG, "Confirmed snapshot application failed; retained local actions are unchanged", failure)
    }

    private val partnerDao = database.partnerDao()
    private val tractorDao = database.tractorDao()
    private val customerDao = database.customerDao()
    private val jobEntryDao = database.jobEntryDao()
    private val expenseDao = database.expenseDao()
    private val withdrawalDao = database.withdrawalDao()
    private val appSettingsDao = database.appSettingsDao()
    private val paymentDao = database.paymentDao()
    private val checklistItemDao = database.checklistItemDao()
    private val workTypeExtensionDao = database.workTypeExtensionDao()

    val currentWorkspace: StateFlow<Workspace?> = firestoreRepository.currentWorkspace

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _workspaceInitState = MutableStateFlow<WorkspaceInitState>(WorkspaceInitState.Uninitialized)
    val workspaceInitState: StateFlow<WorkspaceInitState> = _workspaceInitState.asStateFlow()

    private val _personalWorkspaceId = MutableStateFlow<String?>(null)
    val personalWorkspaceId: StateFlow<String?> = _personalWorkspaceId.asStateFlow()

    fun getPersonalWorkspaceId(): String? = _personalWorkspaceId.value ?: _activeWorkspaceId.value

    private val _visibleWorkspaceIds = MutableStateFlow<Set<String>>(emptySet())
    val visibleWorkspaceIds: StateFlow<Set<String>> = _visibleWorkspaceIds.asStateFlow()

    private val _verifiedWorkspaceContext = MutableStateFlow<Pair<String, String>?>(null)
    val verifiedWorkspaceContext: StateFlow<Pair<String, String>?> = _verifiedWorkspaceContext.asStateFlow()

    private val _activeWorkspaceId = MutableStateFlow<String?>(null)
    val activeWorkspaceId: StateFlow<String?> = _activeWorkspaceId.asStateFlow()

    val cloudConnectionState = combine(firestoreRepository.cloudListenerStates, activeWorkspaceId) { states, ws ->
        states[ws] ?: com.example.data.firebase.CloudConnectionState.IDLE
    }.stateIn(scope, SharingStarted.Eagerly, com.example.data.firebase.CloudConnectionState.IDLE)

    fun retryCloudListeners() {
        firestoreRepository.stopRealtimeListeners()
        attachRealtimeListenersForVisibleWorkspaces()
    }

    private val _settingsSyncState = MutableStateFlow<SettingsSyncState>(SettingsSyncState.Uninitialized)
    val settingsSyncState: StateFlow<SettingsSyncState> = _settingsSyncState.asStateFlow()

    private val _pendingInvitations = MutableStateFlow<List<WorkspaceInvitation>>(emptyList())
    val pendingInvitations: StateFlow<List<WorkspaceInvitation>> = _pendingInvitations.asStateFlow()

    private val _workspaceMembers = MutableStateFlow<List<WorkspaceMember>>(emptyList())
    val workspaceMembers: StateFlow<List<WorkspaceMember>> = _workspaceMembers.asStateFlow()

    // The union above is retained for workspace discovery. Business data must use
    // this active-workspace-only projection so a visible collaboration workspace
    // can never contribute participants to the current report.
    private val _activeWorkspaceMembers = MutableStateFlow<List<WorkspaceMember>>(emptyList())
    val activeWorkspaceMembers: StateFlow<List<WorkspaceMember>> = _activeWorkspaceMembers.asStateFlow()

    // SAFE DEFAULT: non-owner. Owner is only ever granted from an explicit resolved
    // membership role, never from a default, a fallback, or workspace ownership.
    private val _isCollaborationOwner = MutableStateFlow(false)
    val isCollaborationOwner: StateFlow<Boolean> = _isCollaborationOwner.asStateFlow()

    private val _partnerPercentages = MutableStateFlow<Map<String, Int>>(emptyMap())
    val partnerPercentages: StateFlow<Map<String, Int>> = _partnerPercentages.asStateFlow()

    // UID-based profit share allocations (new authoritative system)
    private val _profitShareAllocations = MutableStateFlow<Map<String, Int>>(emptyMap())
    val profitShareAllocations: StateFlow<Map<String, Int>> = _profitShareAllocations.asStateFlow()

    private val _profitShareAllocationDetails = MutableStateFlow<Map<String, com.example.data.firebase.ProfitShareAllocation>>(emptyMap())
    val profitShareAllocationDetails: StateFlow<Map<String, com.example.data.firebase.ProfitShareAllocation>> = _profitShareAllocationDetails.asStateFlow()

    private val _profitShareMigrationDone = MutableStateFlow<Boolean>(false)
    val profitShareMigrationDone: StateFlow<Boolean> = _profitShareMigrationDone.asStateFlow()

    fun loadCachedPercentages(wsId: String) {
        _partnerPercentages.value = emptyMap()
        if (wsId.isBlank()) return
        try {
            val prefs = context.getSharedPreferences("workspace_percentages_prefs", android.content.Context.MODE_PRIVATE)
            val cachedJson = prefs.getString("pct_$wsId", null)
            if (!cachedJson.isNullOrBlank()) {
                val jobj = org.json.JSONObject(cachedJson)
                val map = mutableMapOf<String, Int>()
                val keys = jobj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = jobj.getInt(k)
                }
                _partnerPercentages.value = map
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading cached percentages: ${e.message}")
        }
    }

    suspend fun updatePartnerPercentages(percentages: Map<String, Int>) {
        val wsId = getOrResolveWorkspaceId() ?: return
        _partnerPercentages.value = percentages
        try {
            val prefs = context.getSharedPreferences("workspace_percentages_prefs", android.content.Context.MODE_PRIVATE)
            val json = org.json.JSONObject(percentages as Map<*, *>).toString()
            prefs.edit().putString("pct_$wsId", json).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error caching percentages: ${e.message}")
        }
        if (isCloudReady()) {
            try {
                firestoreRepository.savePartnerPercentages(wsId, percentages)
            } catch (e: Exception) {
                Log.w(TAG, "Failed syncing percentages to cloud: ${e.message}")
            }
        }
    }

    fun loadCachedProfitShareAllocations(wsId: String) {
        _profitShareAllocations.value = emptyMap()
        _profitShareAllocationDetails.value = emptyMap()
        if (wsId.isBlank()) return
        try {
            val prefs = context.getSharedPreferences("workspace_profit_share_prefs", android.content.Context.MODE_PRIVATE)
            val cachedJson = prefs.getString("psa_$wsId", null)
            if (!cachedJson.isNullOrBlank()) {
                val jobj = org.json.JSONObject(cachedJson)
                val percentageMap = mutableMapOf<String, Int>()
                val detailMap = mutableMapOf<String, com.example.data.firebase.ProfitShareAllocation>()
                val keys = jobj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val item = jobj.getJSONObject(k)
                    val uid = item.getString("participantUid")
                    val percentage = item.getInt("percentage")
                    if (uid.isNotBlank()) {
                        percentageMap[uid] = percentage
                        detailMap[uid] = com.example.data.firebase.ProfitShareAllocation(
                            participantUid = uid,
                            percentage = percentage,
                            displayName = item.optString("displayName", ""),
                            role = item.optString("role", "partner"),
                            isActive = item.optBoolean("isActive", true)
                        )
                    }
                }
                _profitShareAllocations.value = percentageMap
                _profitShareAllocationDetails.value = detailMap
                Log.d(TAG, "Loaded cached profit share allocations for $wsId: $percentageMap")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading cached profit share allocations: ${e.message}")
        }
    }

    suspend fun loadProfitShareAllocationsFromCloud(wsId: String) {
        val actorUid = getEffectiveActorUid() ?: return
        if (wsId.isBlank() || _activeWorkspaceId.value != wsId) return
        if (!isCloudReady()) return
        try {
            val (percentageMap, detailMap) = firestoreRepository.getProfitShareAllocations(wsId)
            if (getEffectiveActorUid() != actorUid || _activeWorkspaceId.value != wsId) return
            _profitShareAllocations.value = percentageMap
            _profitShareAllocationDetails.value = detailMap
            if (percentageMap.isNotEmpty()) {
                _profitShareAllocations.value = percentageMap
                _profitShareAllocationDetails.value = detailMap
                // Cache locally
                val prefs = context.getSharedPreferences("workspace_profit_share_prefs", android.content.Context.MODE_PRIVATE)
                val jsonObj = org.json.JSONObject()
                percentageMap.forEach { (uid, percentage) ->
                    val detail = detailMap[uid]
                    val item = org.json.JSONObject().apply {
                        put("participantUid", uid)
                        put("percentage", percentage)
                        put("displayName", detail?.displayName ?: "")
                        put("role", detail?.role ?: "partner")
                        put("isActive", detail?.isActive ?: true)
                    }
                    jsonObj.put(uid, item)
                }
                prefs.edit().putString("psa_$wsId", jsonObj.toString()).apply()
                Log.d(TAG, "Loaded profit share allocations from cloud for $wsId: $percentageMap")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load profit share allocations from cloud: ${e.message}")
        }
    }

    suspend fun updateProfitShareAllocations(
        allocations: Map<String, Int>,
        participantDetails: Map<String, com.example.data.firebase.ProfitShareAllocation> = emptyMap()
    ) {
        val wsId = getOrResolveWorkspaceId() ?: return
        _profitShareAllocations.value = allocations
        _profitShareAllocationDetails.value = participantDetails

        // Cache locally
        try {
            val prefs = context.getSharedPreferences("workspace_profit_share_prefs", android.content.Context.MODE_PRIVATE)
            val jsonObj = org.json.JSONObject()
            allocations.forEach { (uid, percentage) ->
                val detail = participantDetails[uid]
                val item = org.json.JSONObject().apply {
                    put("participantUid", uid)
                    put("percentage", percentage)
                    put("displayName", detail?.displayName ?: "")
                    put("role", detail?.role ?: "partner")
                    put("isActive", detail?.isActive ?: true)
                }
                jsonObj.put(uid, item)
            }
            prefs.edit().putString("psa_$wsId", jsonObj.toString()).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error caching profit share allocations: ${e.message}")
        }

        // Sync to cloud
        if (isCloudReady()) {
            try {
                firestoreRepository.saveProfitShareAllocations(wsId, allocations, participantDetails)
            } catch (e: Exception) {
                Log.w(TAG, "Failed syncing profit share allocations to cloud: ${e.message}")
            }
        }
    }

    /** Automatic name/phone matching is not evidence of immutable UID identity. */
    suspend fun migrateLegacyPercentagesToUidAllocations(wsId: String) {
        requireWorkspaceActor(wsId)
        throw IllegalStateException("Legacy allocation migration requires a reviewed UID mapping and verified backup; name/phone inference is disabled")
    }

    private val _availableWorkspaces = MutableStateFlow<List<Workspace>>(emptyList())
    val availableWorkspaces: StateFlow<List<Workspace>> = _availableWorkspaces.asStateFlow()

    private val _genuinePartnerWorkspaces = MutableStateFlow<List<Workspace>>(emptyList())
    val genuinePartnerWorkspaces: StateFlow<List<Workspace>> = _genuinePartnerWorkspaces.asStateFlow()

    private var activeUid: String? = null
    private var currentUserRole: String? = null

    init {
        try {
            val initialUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (!initialUid.isNullOrBlank()) {
                activeUid = initialUid
                restoreVerifiedMembership(initialUid)
            }
        } catch (_: Exception) {}
    }

    fun getActiveUid(): String? = getEffectiveActorUid()

    fun getEffectiveActorUid(): String? {
        val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) { activeUid = null; return null }
        activeUid = uid
        return uid
    }

    private val userLangPrefs by lazy {
        context.getSharedPreferences("trac_user_language_prefs", android.content.Context.MODE_PRIVATE)
    }

    fun getSavedUserLanguage(uid: String? = null): String? {
        val targetUid = uid ?: getEffectiveActorUid()
        return if (!targetUid.isNullOrBlank()) {
            userLangPrefs.getString("lang_$targetUid", null) ?: userLangPrefs.getString("user_language", null)
        } else {
            userLangPrefs.getString("user_language", null)
        }
    }

    fun saveUserLanguage(language: String, uid: String? = null) {
        val targetUid = uid ?: getEffectiveActorUid()
        val editor = userLangPrefs.edit().putString("user_language", language)
        if (!targetUid.isNullOrBlank()) {
            editor.putString("lang_$targetUid", language)
        }
        editor.apply()
    }

    private val userHourlyRatesPrefs by lazy {
        context.getSharedPreferences("trac_user_hourly_rates_prefs", android.content.Context.MODE_PRIVATE)
    }

    fun getPersonalHourlyRate(workspaceId: String? = null, userUid: String? = null): Double {
        val targetUid = userUid?.ifBlank { null } ?: getEffectiveActorUid() ?: ""
        val targetWsId = workspaceId?.ifBlank { null } ?: _activeWorkspaceId.value ?: _personalWorkspaceId.value ?: "main"

        if (targetUid.isNotBlank()) {
            val key = "hourly_rate_${targetWsId}_$targetUid"
            if (userHourlyRatesPrefs.contains(key)) {
                val stored = userHourlyRatesPrefs.getFloat(key, -1f)
                if (stored >= 0f) return stored.toDouble()
            }
            // Check member in _workspaceMembers.value
            val memberRate = _workspaceMembers.value.firstOrNull { it.uid == targetUid }?.defaultHourlyRate
            if (memberRate != null && memberRate > 0.0) {
                userHourlyRatesPrefs.edit().putFloat(key, memberRate.toFloat()).apply()
                return memberRate
            }
        }
        return 1100.0
    }

    suspend fun setPersonalHourlyRate(rate: Double, workspaceId: String? = null, userUid: String? = null) {
        val targetUid = userUid?.ifBlank { null } ?: getEffectiveActorUid() ?: ""
        val targetWsId = workspaceId?.ifBlank { null } ?: getOrResolveWorkspaceId() ?: _personalWorkspaceId.value ?: "main"

        if (targetUid.isNotBlank()) {
            val key = "hourly_rate_${targetWsId}_$targetUid"
            userHourlyRatesPrefs.edit().putFloat(key, rate.toFloat()).apply()

            // Update in-memory _workspaceMembers
            val currentMembers = _workspaceMembers.value.toMutableList()
            val idx = currentMembers.indexOfFirst { it.uid == targetUid }
            if (idx >= 0) {
                currentMembers[idx] = currentMembers[idx].copy(defaultHourlyRate = rate)
                _workspaceMembers.value = currentMembers
            }

            // Sync to Firestore member doc
            if (isCloudReady()) {
                scope.launch {
                    try {
                        firestoreRepository.updateMemberHourlyRate(targetWsId, targetUid, rate)
                    } catch (e: Exception) {
                        Log.w(TAG, "Error syncing member hourly rate to cloud: ${e.message}")
                    }
                }
            }
        }

        // If the owner is updating their rate, also update AppSettingsEntity.defaultHourlyRate for backward compatibility
        val isOwner = isOwnerOfWorkspace(targetWsId)
        if (isOwner) {
            val currentSettings = appSettingsDao.getSettingsForWorkspaceOnce(targetWsId)
            if (currentSettings != null) {
                appSettingsDao.insertOrUpdateSettings(currentSettings.copy(defaultHourlyRate = rate))
            }
        }
    }

    private val partnerAccessPrefs by lazy {
        context.getSharedPreferences("trac_partner_access_prefs", android.content.Context.MODE_PRIVATE)
    }

    private val accessControlPrefs by lazy {
        context.getSharedPreferences("trac_access_control_prefs", android.content.Context.MODE_PRIVATE)
    }

    private val pendingAuditManager by lazy {
        com.example.data.sync.PendingAuditManager.getInstance(context)
    }

    private val _accessControlState = MutableStateFlow(com.example.data.auth.AccessControlDocument())
    val accessControlState: StateFlow<com.example.data.auth.AccessControlDocument> = _accessControlState.asStateFlow()

    fun loadCachedAccessControl(workspaceId: String) {
        val targetWsId = workspaceId.ifBlank { _activeWorkspaceId.value ?: _personalWorkspaceId.value ?: "" }
        if (targetWsId.isBlank()) return
        val raw = accessControlPrefs.getString("access_control_$targetWsId", null)
        val doc = com.example.data.auth.AccessControlDocument.fromJsonString(raw)
        _accessControlState.value = doc
        com.example.data.auth.AuthorizationManager.currentAccessControl = doc
        Log.d(TAG, "Loaded cached access control for $targetWsId: status=${doc.accountStatus} blocked=${doc.isAccountBlocked}")
    }

    fun recordAuditEvent(
        action: String,
        entityType: String,
        recordId: Long,
        reference: String,
        workspaceId: String = ""
    ) {
        val currentActorUid = activeUid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val targetWsId = workspaceId.ifBlank { _activeWorkspaceId.value ?: _personalWorkspaceId.value ?: "" }
        val currentRole = if (isCollaborationOwner.value) "Owner" else "Partner"
        val partnerWs = _genuinePartnerWorkspaces.value.find { it.workspaceId == targetWsId }
        val ownerUid = partnerWs?.ownerUid?.ifBlank { null } ?: activeUid ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""

        scope.launch(Dispatchers.IO) {
            val settings = appSettingsDao.getSettingsForWorkspaceOnce(targetWsId)
            val actorName = if (isCollaborationOwner.value) {
                settings?.ownerName?.ifBlank { "Owner" } ?: "Owner"
            } else {
                settings?.activePartnerName?.ifBlank { "Partner" } ?: "Partner"
            }

            val event = com.example.data.sync.AuditEvent(
                actorUid = currentActorUid,
                actorName = actorName,
                actorRole = currentRole,
                primaryOwnerUid = ownerUid,
                workspaceId = targetWsId,
                action = action,
                entityType = entityType,
                recordId = recordId,
                humanReadableReference = reference,
                timestamp = System.currentTimeMillis(),
                source = "MOBILE_APP"
            )
            pendingAuditManager.recordAuditEvent(event)

            // If cloud is ready, immediately attempt to push the audit event
            if (isCloudReady()) {
                try {
                    firestoreRepository.recordAuditEvent(targetWsId, event)
                    pendingAuditManager.removePendingAudit(event.eventId)
                } catch (e: Exception) {
                    Log.d(TAG, "Deferred audit event sync: ${e.message}")
                }
            }
        }
    }

    private val _lockedPagesState = MutableStateFlow<Set<String>>(emptySet())
    val lockedPagesState: StateFlow<Set<String>> = _lockedPagesState.asStateFlow()

    fun getPartnerLockedPages(workspaceId: String, partnerKey: String): Set<String> {
        val targetWsId = workspaceId.ifBlank { _activeWorkspaceId.value ?: "default_ws" }
        if (targetWsId.isBlank() || partnerKey.isBlank()) return emptySet()
        val cleanKey = partnerKey.trim().replace(Regex("[^a-zA-Z0-9_]"), "_")
        val raw = partnerAccessPrefs.getString("locked_pages_${targetWsId}_$cleanKey", "") ?: ""
        return if (raw.isBlank()) emptySet() else raw.split(",").filter { it.isNotBlank() }.toSet()
    }

    fun setPartnerPageLocked(workspaceId: String, partnerKey: String, pageKey: String, isLocked: Boolean) {
        val targetWsId = workspaceId.ifBlank { _activeWorkspaceId.value ?: "default_ws" }
        if (targetWsId.isBlank() || partnerKey.isBlank()) return
        val cleanKey = partnerKey.trim().replace(Regex("[^a-zA-Z0-9_]"), "_")
        val current = getPartnerLockedPages(targetWsId, partnerKey).toMutableSet()
        if (isLocked) {
            current.add(pageKey)
        } else {
            current.remove(pageKey)
        }
        partnerAccessPrefs.edit().putString("locked_pages_${targetWsId}_$cleanKey", current.joinToString(",")).apply()
        refreshLockedPages(targetWsId)

        // Realtime sync to Cloud Firestore
        scope.launch {
            try {
                firestoreRepository.savePartnerPermissions(
                    workspaceId = targetWsId,
                    partnerKey = cleanKey,
                    lockedPages = current.toList(),
                    ownerUid = activeUid ?: ""
                )
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync partner permissions to cloud: ${e.message}")
            }
        }

        // Record compact audit event
        recordAuditEvent(
            action = com.example.data.auth.RoleUtils.ROLE_PARTNER,
            entityType = com.example.data.auth.RoleUtils.ROLE_PARTNER,
            recordId = 0L,
            reference = "Updated permissions for partner '$partnerKey': locked '$pageKey' = $isLocked",
            workspaceId = targetWsId
        )
    }

    fun getLockedPages(workspaceId: String): Set<String> {
        val targetWsId = workspaceId.ifBlank { _activeWorkspaceId.value ?: "default_ws" }
        if (targetWsId.isBlank()) return emptySet()
        val raw = partnerAccessPrefs.getString("locked_pages_$targetWsId", "") ?: ""
        return if (raw.isBlank()) emptySet() else raw.split(",").filter { it.isNotBlank() }.toSet()
    }

    fun setPageLocked(workspaceId: String, pageKey: String, isLocked: Boolean) {
        val targetWsId = workspaceId.ifBlank { _activeWorkspaceId.value ?: "default_ws" }
        if (targetWsId.isBlank()) return
        val current = getLockedPages(targetWsId).toMutableSet()
        if (isLocked) {
            current.add(pageKey)
        } else {
            current.remove(pageKey)
        }
        partnerAccessPrefs.edit().putString("locked_pages_$targetWsId", current.joinToString(",")).apply()
        _lockedPagesState.value = current
    }

    fun refreshLockedPages(workspaceId: String) {
        val targetWsId = workspaceId.ifBlank { _activeWorkspaceId.value ?: "default_ws" }
        if (targetWsId.isNotBlank()) {
            val isOwner = isOwnerOfWorkspace(targetWsId)
            if (isOwner) {
                _lockedPagesState.value = emptySet()
            } else {
                val currentUid = getEffectiveActorUid() ?: ""
                val locksByUid = if (currentUid.isNotBlank()) getPartnerLockedPages(targetWsId, currentUid) else emptySet()
                val globalLocks = getLockedPages(targetWsId)
                _lockedPagesState.value = locksByUid + globalLocks

                CoroutineScope(Dispatchers.IO).launch {
                    val currentSettings = appSettingsDao.getSettingsForWorkspaceOnce(targetWsId)
                    val currentPhone = currentSettings?.activePartnerPhone ?: ""
                    val currentName = currentSettings?.activePartnerName ?: ""

                    val locksByPhone = if (currentPhone.isNotBlank()) getPartnerLockedPages(targetWsId, currentPhone) else emptySet()
                    val locksByName = if (currentName.isNotBlank()) getPartnerLockedPages(targetWsId, currentName) else emptySet()
                    val combined = locksByUid + locksByPhone + locksByName + globalLocks
                    _lockedPagesState.value = combined
                }
            }
        }
    }

    private val pendingDeleteManager = PendingDeleteManager.getInstance(context)
    private var collaborationIndexListener: com.google.firebase.firestore.ListenerRegistration? = null
    private val groupMembersListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
    private val groupPendingListeners = mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
    private val groupWorkspacesMap = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

    private val workspaceRequests = WorkspaceRequestFence { getEffectiveActorUid() }

    fun isCloudReady(): Boolean = _workspaceInitState.value is WorkspaceInitState.Ready

    fun normalizePhoneNumber(phone: String): String = com.example.data.firebase.normalizePhoneNumber(phone)

    suspend fun checkPhoneAccountExists(rawPhone: String): AccountLookupResult {
        val clean10 = rawPhone.filter { it.isDigit() }.takeLast(10)
        val formatted = normalizePhoneNumber(rawPhone)
        try {
            val localPartners = partnerDao.getPartnersForWorkspace(_activeWorkspaceId.value ?: "").firstOrNull() ?: emptyList()
            val match = localPartners.find { normalizePhoneNumber(it.phone) == formatted || it.phone.filter { ch -> ch.isDigit() }.takeLast(10) == clean10 }
            if (match != null) {
                return AccountLookupResult.Found(match.id.toString(), match.name)
            }
        } catch (_: Exception) {}

        return firestoreRepository.checkPhoneAccountExists(rawPhone)
    }

    suspend fun checkUserExists(uid: String): Boolean {
        return firestoreRepository.checkUserExists(uid)
    }

    suspend fun checkApplicationAccountExists(uid: String, rawPhone: String? = null): Boolean {
        return firestoreRepository.checkApplicationAccountExists(uid, rawPhone)
    }

    /**
     * Initializes workspace, prioritizing immediate Room availability for offline access,
     * then resolves Firestore bootstrap & verification, attaches real-time snapshot listeners,
     * resolves settings safely, and performs safe initial migration.
     */
    suspend fun createPersonalWorkspace(user: com.google.firebase.auth.FirebaseUser, fullName: String, businessName: String): UserProfile =
        firestoreRepository.createPersonalWorkspace(user, fullName, businessName)

    suspend fun initializeForUser(userProfile: UserProfile, selectedWorkspaceId: String? = null): Result<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val uid = userProfile.uid
        if (uid.isBlank()) {
            val err = IllegalArgumentException("User profile UID is blank")
            _workspaceInitState.value = WorkspaceInitState.Error(err)
            return@withContext Result.failure(err)
        }

        // STRICT ACCOUNT BOUNDARY: Detect auth UID change or new session
        if (activeUid != null && activeUid != uid) {
            stopWorkspaceListeners()
        }
        activeUid = uid
        currentUserRole = null
        val request = workspaceRequests.begin(uid)

        restoreVerifiedMembership(uid)
        val activeProfile = userProfile
        val cachedWorkspaceId = _activeWorkspaceId.value.takeIf { _verifiedWorkspaceContext.value == (uid to it) }
        if (cachedWorkspaceId == null) _workspaceInitState.value = WorkspaceInitState.Loading
        val canonicalWsId = activeProfile.defaultWorkspaceId.orEmpty()
        var authoritativeWsId: String? = null
        var authoritativeRole: String? = null
        var isPreResolvedPartner = false

        return@withContext try {
            kotlinx.coroutines.withTimeout(20_000L) {
            Log.d(TAG, "Resolving deterministic cloud workspace for user UID: $uid")

            val preferredWs = if (com.example.BuildConfig.PHYSICAL_TEST) {
                if (com.example.data.firebase.FirebaseEnvironment.isStagingWorkspace(cachedWorkspaceId)) cachedWorkspaceId else null
            } else cachedWorkspaceId
            val choices = getAvailableWorkspaces(userProfile)
            workspaceRequests.check(request)
            val selected = com.example.data.firebase.WorkspaceDiscoveryPolicy.selectedWorkspace(
                choices.map { it.workspaceId }, selectedWorkspaceId, preferredWs)
            if (selected == null && choices.size > 1) {
                stopWorkspaceListeners()
                _workspaceInitState.value = WorkspaceInitState.SelectionRequired(choices)
                return@withTimeout Result.failure(IllegalStateException("Choose a workspace to continue"))
            }
            val membership = selected?.let { id -> choices.firstOrNull { it.workspaceId == id } }
                ?: throw SecurityException(if (com.example.BuildConfig.PHYSICAL_TEST) "Signed in. This isolated test build cannot open business workspaces. Choose an authorized staging workspace, or use the standard app for your personal/business workspace." else "No active workspace. Create a personal workspace or ask the Owner to complete your invitation.")
            if (com.example.BuildConfig.PHYSICAL_TEST && !com.example.data.firebase.FirebaseEnvironment.isStagingWorkspace(membership.workspaceId))
                throw SecurityException("Physical test membership is outside the staging workspace")
            authoritativeWsId = membership.workspaceId
            authoritativeRole = membership.accessRole
            isPreResolvedPartner = !com.example.data.auth.RoleUtils.isOwner(membership.accessRole)
            val workspace = firestoreRepository.bootstrapWorkspaceForUser(activeProfile, membership.workspaceId, membership.accessRole)
            workspaceRequests.check(request)
            val resolvedWsId = workspace.workspaceId
            _personalWorkspaceId.value = if (!isPreResolvedPartner) resolvedWsId else null

            // Discover genuine partner workspaces strictly
            val genuinePartners = getGenuinePartnerWorkspaces(activeProfile, resolvedWsId)
            workspaceRequests.check(request)
            _genuinePartnerWorkspaces.value = genuinePartners

            // ROLE RESOLUTION IS AUTHORITATIVE AND MEMBERSHIP-DRIVEN.
            // workspaceId (WHERE) and role (WHAT) are fully independent values.
            val targetWsId = authoritativeWsId
                ?: activeProfile.defaultWorkspaceId?.ifBlank { null }
                ?: resolvedWsId

            // Re-read the authoritative membership after bootstrap so an Owner keeps OWNER
            // and a Partner keeps PARTNER even though both share the same workspaceId.
            val finalRole = firestoreRepository.resolveAuthoritativeRole(uid, targetWsId)
                ?: throw SecurityException("Verified workspace membership is inactive or missing")

            workspaceRequests.check(request)
            val normalizedFinalRole = com.example.data.auth.RoleUtils.normalizeRole(finalRole)
            val isOwner = normalizedFinalRole == com.example.data.auth.RoleUtils.ROLE_OWNER
            if (normalizedFinalRole.isBlank()) throw SecurityException("Unsupported workspace role")
            val verifiedMembers = firestoreRepository.getWorkspaceMembers(targetWsId)
            workspaceRequests.check(request)
            _activeWorkspaceMembers.value = verifiedMembers
            cacheVerifiedMembership(uid, targetWsId, verifiedMembers)
            currentUserRole = normalizedFinalRole
            Log.i(
                "TRAC_ROLE",
                "Resolved uid=$uid workspace=$targetWsId role=$normalizedFinalRole isOwner=$isOwner " +
                    "(wsOwnerUid=${workspace.ownerUid})"
            )

            val visibleSet = setOf(targetWsId)
            val resolvedActiveWsId = targetWsId

            _isCollaborationOwner.value = isOwner
            _personalWorkspaceId.value = if (isOwner) resolvedActiveWsId else null
            _visibleWorkspaceIds.value = visibleSet
            _activeWorkspaceId.value = resolvedActiveWsId
            loadCachedPercentages(resolvedActiveWsId)
            loadCachedProfitShareAllocations(resolvedActiveWsId)
            workspaceRequests.check(request)

            // Load or initialize cloud settings before enabling writes
            val currentLocal = appSettingsDao.getSettingsForWorkspaceOnce(resolvedActiveWsId)
                ?: initialWorkspaceSettings(
                    workspaceId = resolvedActiveWsId,
                    workspaceName = workspace.name,
                    isOwner = isOwner,
                    actorName = activeProfile.displayName.orEmpty(),
                    actorPhone = activeProfile.phoneNumber.orEmpty()
                )
            val resolvedSettings = firestoreRepository.fetchOrCreateWorkspaceSettings(
                workspaceId = resolvedActiveWsId,
                uid = uid,
                localSettings = currentLocal
            )

            // Ensure business name is populated from Owner workspace if missing in settings document
            workspaceRequests.check(request)
            val effectiveBusinessName = resolvedSettings.businessName.ifBlank {
                workspace.name.ifBlank {
                    genuinePartners.find { it.workspaceId == resolvedActiveWsId }?.name ?: ""
                }
            }

            // Load profit share allocations from cloud (after settings are fetched)
            scope.launch {
                loadProfitShareAllocationsFromCloud(resolvedActiveWsId)
                // Migrate legacy name-based percentages to UID-based allocations
                // Legacy identity migration requires a reviewed backup and explicit manifest.
            }

            // Decoupled language: user's personal preference, NOT shared workspace's
            val savedLang = getSavedUserLanguage(uid) ?: currentLocal.language

            // Authoritative Actor details: Never confuse Business Owner and Current Actor
            // Try loading Owner-assigned name from workspace member record first
            val assignedMemberRecord = try {
                firestoreRepository.getWorkspaceMembers(resolvedActiveWsId).firstOrNull { it.uid == uid }
            } catch (_: Exception) {
                null
            }
            val assignedMemberName = assignedMemberRecord?.displayName?.takeIf { it.isNotBlank() && !it.equals("Partner", ignoreCase = true) }

            val currentActorName = assignedMemberName
                ?: activeProfile.displayName?.takeIf { it.isNotBlank() && !it.equals("Partner", ignoreCase = true) }
                ?: (appSettingsDao.getSettingsForWorkspaceOnce(resolvedWsId)?.activePartnerName?.ifBlank { null })
                ?: (appSettingsDao.getSettingsForWorkspaceOnce(resolvedActiveWsId)?.activePartnerName?.ifBlank { null })
                ?: (if (isOwner) resolvedSettings.ownerName.ifBlank { "Owner" } else "Partner")

            val currentActorPhone = activeProfile.phoneNumber?.ifBlank { null }
                ?: (appSettingsDao.getSettingsForWorkspaceOnce(resolvedWsId)?.activePartnerPhone?.ifBlank { null })
                ?: (appSettingsDao.getSettingsForWorkspaceOnce(resolvedActiveWsId)?.activePartnerPhone?.ifBlank { null })
                ?: resolvedSettings.businessPhone

            // Merge user profile details with resolved settings
            val mergedSettings = resolvedSettings.copy(
                workspaceId = resolvedActiveWsId,
                businessName = effectiveBusinessName,
                isLoggedIn = true,
                language = savedLang,
                activePartnerName = currentActorName,
                activePartnerPhone = currentActorPhone,
                profilePhotoUri = activeProfile.photoUrl?.ifBlank { null }
                    ?: resolvedSettings.profilePhotoUri,
                lastSyncTime = System.currentTimeMillis()
            )
            workspaceRequests.check(request)
            appSettingsDao.insertOrUpdateSettings(mergedSettings)
            workspaceRequests.check(request)
            _settingsSyncState.value = SettingsSyncState.LoadedFromCloud(mergedSettings)

            // Safe migration on first login if personal workspace has no remote records yet
            if (isOwner) {
                // Legacy migration is explicit and backup-gated; initialization never rewrites legacy business rows.
            }

            _workspaceInitState.value = WorkspaceInitState.Ready(resolvedActiveWsId)

            // Start real-time snapshot listeners for all visible workspaces
            attachRealtimeListenersForVisibleWorkspaces()

            // Start listener for collaboration groups discovery
            // Legacy collaboration indexes are discovery data, never membership authority.

            refreshWorkspaceMembers(resolvedActiveWsId)

            // Run pushUnsyncedToCloud()
            pushUnsyncedToCloud(resolvedActiveWsId)

            Result.success(resolvedActiveWsId)
            }
        } catch (e: Throwable) {
            workspaceRequests.check(request)
            if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            Log.w(TAG, "Workspace verification unavailable: ${e.message}")
            if (e is SecurityException || (e is com.google.firebase.firestore.FirebaseFirestoreException &&
                e.code in setOf(com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED,
                    com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED))) {
                context.getSharedPreferences("verified_workspace_session", Context.MODE_PRIVATE).edit().clear().commit()
                _activeWorkspaceMembers.value = emptyList()
                _workspaceMembers.value = emptyList()
                currentUserRole = null
                stopWorkspaceListeners()
                _workspaceInitState.value = WorkspaceInitState.Error(e)
                return@withContext Result.failure(e)
            }
            restoreVerifiedMembership(uid)
            val cachedId = _activeWorkspaceId.value
            if (!cachedId.isNullOrBlank() && resolveRoleForWorkspace(cachedId).isNotBlank()) {
                val cachedSettings = appSettingsDao.getSettingsForWorkspaceOnce(cachedId)
                if (cachedSettings != null) _settingsSyncState.value = SettingsSyncState.LoadedFromCloud(cachedSettings)
                Result.success(cachedId)
            } else {
                _workspaceInitState.value = WorkspaceInitState.Error(e)
                Result.failure(e)
            }
        }
    }

    private fun listenToCollaborationGroupsDiscovery(uid: String, personalWsId: String) {
        collaborationIndexListener?.remove()
        collaborationIndexListener = firestoreRepository.listenToUserCollaborationGroups(uid) { groupIds ->
            val allGroups = (groupIds + personalWsId).distinct()
            Log.d("TRAC_WORKSPACE", "Collaboration groups updated for $uid: $allGroups")

            // Re-evaluate partner status and active workspace
            scope.launch {
                val userProf = UserProfile(uid = uid)
                val refreshed = getGenuinePartnerWorkspaces(userProf, personalWsId)
                _genuinePartnerWorkspaces.value = refreshed

                val currentActive = _activeWorkspaceId.value ?: personalWsId
                // AUTHORITATIVE: the role comes from workspaces/{workspaceId}/members/{uid} only.
                // Workspace ownership (ownerUid) must never be used to infer the role, because an
                // Owner and a Partner share the same workspaceId.
                val discoveredRole = try {
                    firestoreRepository.resolveAuthoritativeRole(uid, currentActive)
                } catch (_: Exception) {
                    null
                }
                val normalizedDiscoveredRole = com.example.data.auth.RoleUtils.normalizeRole(
                    discoveredRole ?: currentUserRole
                )
                val isOwner = normalizedDiscoveredRole == com.example.data.auth.RoleUtils.ROLE_OWNER ||
                    normalizedDiscoveredRole == com.example.data.auth.RoleUtils.ROLE_CO_OWNER
                currentUserRole = normalizedDiscoveredRole
                Log.i(
                    "TRAC_ROLE",
                    "listenCollab uid=$uid wsId=$currentActive membershipRole=$discoveredRole " +
                        "→ role=$normalizedDiscoveredRole isOwner=$isOwner"
                )
                val visibleSet = setOf(currentActive)

                _isCollaborationOwner.value = isOwner
                _visibleWorkspaceIds.value = visibleSet

                attachRealtimeListenersForVisibleWorkspaces()
                refreshWorkspaceMembers(currentActive)
            }

            // Listen to each group's members
            val currentListeningGroups = groupMembersListeners.keys.toSet()
            val groupsToRemove = currentListeningGroups - allGroups.toSet()
            for (gId in groupsToRemove) {
                groupMembersListeners.remove(gId)?.remove()
                groupWorkspacesMap.remove(gId)
            }

            if (groupsToRemove.isNotEmpty()) {
                scope.launch {
                    for (removedWsId in groupsToRemove) {
                        if (removedWsId != personalWsId) {
                            try {
                                jobEntryDao.deleteAllSyncedForWorkspace(removedWsId)
                                expenseDao.deleteAllSyncedForWorkspace(removedWsId)
                                customerDao.deleteAllSyncedForWorkspace(removedWsId)
                                tractorDao.deleteAllForWorkspace(removedWsId)
                                partnerDao.deleteAllForWorkspace(removedWsId)
                                withdrawalDao.deleteAllSyncedForWorkspace(removedWsId)
                            } catch (e: Exception) {
                                Log.w("TRAC_WORKSPACE", "Error cleaning up removed workspace $removedWsId: ${e.message}")
                            }
                        }
                    }

                    val newVisible = (groupWorkspacesMap.values.flatten() + personalWsId).toSet()
                    if (newVisible != _visibleWorkspaceIds.value) {
                        Log.d("TRAC_WORKSPACE", "Visible workspaces after group removal: $newVisible")
                        _visibleWorkspaceIds.value = newVisible
                        attachRealtimeListenersForVisibleWorkspaces()
                        refreshWorkspaceMembers()
                    }

                    if (_activeWorkspaceId.value != null && groupsToRemove.contains(_activeWorkspaceId.value)) {
                        val available = getAvailableWorkspaces(UserProfile(uid = uid))
                        val fallbackId = available.firstOrNull()?.workspaceId ?: personalWsId
                        if (fallbackId != _activeWorkspaceId.value) {
                            switchActiveWorkspace(fallbackId, UserProfile(uid = uid))
                        }
                    }
                }
            }

            for (groupId in allGroups) {
                if (!groupMembersListeners.containsKey(groupId)) {
                    val listener = firestoreRepository.listenToCollaborationGroupMembers(groupId) { memberWorkspaces ->
                        scope.launch {
                            groupWorkspacesMap[groupId] = memberWorkspaces
                            val newVisible = (groupWorkspacesMap.values.flatten() + personalWsId).toSet()
                            if (newVisible != _visibleWorkspaceIds.value) {
                                Log.d("TRAC_WORKSPACE", "Visible workspaces updated: $newVisible")
                                _visibleWorkspaceIds.value = newVisible
                                attachRealtimeListenersForVisibleWorkspaces()
                            }
                            refreshWorkspaceMembers()
                        }
                    }
                    if (listener != null) {
                        groupMembersListeners[groupId] = listener
                    }
                }
                if (!groupPendingListeners.containsKey(groupId)) {
                    val pListener = firestoreRepository.listenToCollaborationGroupPendingPhones(groupId) {
                        scope.launch {
                            refreshWorkspaceMembers()
                        }
                    }
                    if (pListener != null) {
                        groupPendingListeners[groupId] = pListener
                    }
                }
            }
        }
    }

    private fun attachRealtimeListenersForVisibleWorkspaces() {
        val currentAuthUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (currentAuthUid.isNullOrBlank()) {
            Log.d(TAG, "Skipping attachRealtimeListenersForVisibleWorkspaces: not authenticated")
            return
        }
        val visibleIds = setOfNotNull(_activeWorkspaceId.value).filter { it.isNotBlank() }.toSet()
        if (visibleIds.isEmpty()) {
            return
        }
        firestoreRepository.startRealtimeListenersForWorkspaces(
            workspaceIds = visibleIds,
            operatorWorkspaceIds = if (currentUserRole == com.example.data.auth.RoleUtils.ROLE_OPERATOR) visibleIds else emptySet(),
            onMembershipUpdated = { members, wsId ->
                if (wsId == _activeWorkspaceId.value) {
                    val uid = getEffectiveActorUid().orEmpty()
                    val actor = members.firstOrNull { it.uid == uid && it.status.equals("active", true) }
                    val role = com.example.data.auth.RoleUtils.normalizeRole(actor?.role)
                    if (role.isBlank()) {
                        context.getSharedPreferences("verified_workspace_session", Context.MODE_PRIVATE).edit().clear().commit()
                        _activeWorkspaceMembers.value = emptyList()
                        _workspaceMembers.value = emptyList()
                        currentUserRole = ""
                        _isCollaborationOwner.value = false
                        stopWorkspaceListeners()
                        _workspaceInitState.value = WorkspaceInitState.Error(SecurityException("Workspace access removed"))
                    } else {
                        _activeWorkspaceMembers.value = members
                        _workspaceMembers.value = members
                        currentUserRole = role
                        _isCollaborationOwner.value = role == com.example.data.auth.RoleUtils.ROLE_OWNER
                        cacheVerifiedMembership(uid, wsId, members)
                    }
                }
            },
            onJobsUpdated = { remoteJobs, listenerWsId ->
                replicaUpdates.submit {
                    syncRemoteJobsToLocal(remoteJobs, listenerWsId)
                }
            },
            onExpensesUpdated = { remoteExpenses, listenerWsId ->
                replicaUpdates.submit {
                    syncRemoteExpensesToLocal(remoteExpenses, listenerWsId)
                }
            },
            onCustomersUpdated = { remoteCustomers, listenerWsId ->
                replicaUpdates.submit {
                    syncRemoteCustomersToLocal(remoteCustomers, listenerWsId)
                }
            },
            onTractorsUpdated = { remoteTractors, listenerWsId ->
                scope.launch {
                    syncRemoteTractorsToLocal(remoteTractors, listenerWsId)
                }
            },
            onPartnersUpdated = { remotePartners, listenerWsId ->
                scope.launch {
                    syncRemotePartnersToLocal(remotePartners, listenerWsId)
                }
            },
            onWithdrawalsUpdated = { remoteWithdrawals, listenerWsId ->
                replicaUpdates.submit {
                    syncRemoteWithdrawalsToLocal(remoteWithdrawals, listenerWsId)
                }
            },
            onSettingsUpdated = { remoteSettingsMap, listenerWsId ->
                scope.launch {
                    val pWsId = _personalWorkspaceId.value
                    val activeWsId = _activeWorkspaceId.value
                    if (!_visibleWorkspaceIds.value.contains(listenerWsId) && listenerWsId != activeWsId && listenerWsId != pWsId) {
                        return@launch
                    }
                    val current = appSettingsDao.getSettingsForWorkspaceOnce(listenerWsId)
                        ?: AppSettingsEntity(workspaceId = listenerWsId)
                    val updated = appSettingsFromFirestoreMap(remoteSettingsMap, current, fallbackWorkspaceId = listenerWsId)
                    val currentActorUid = getEffectiveActorUid()
                    val savedLang = getSavedUserLanguage(currentActorUid) ?: current.language
                    // Preserve user personal identity & language preference
                    val preserved = updated.copy(
                        activePartnerName = current.activePartnerName.ifBlank {
                            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.displayName ?: ""
                        },
                        activePartnerPhone = current.activePartnerPhone.ifBlank {
                            com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.phoneNumber ?: ""
                        },
                        profilePhotoUri = current.profilePhotoUri,
                        language = savedLang
                    )
                    appSettingsDao.insertOrUpdateSettings(preserved)

                    val rawPct = remoteSettingsMap["partnerPercentages"] as? Map<*, *>
                    if (rawPct != null) {
                        val parsed = rawPct.mapNotNull { (k, v) ->
                            val key = k?.toString() ?: return@mapNotNull null
                            val value = (v as? Number)?.toInt() ?: return@mapNotNull null
                            key to value
                        }.toMap()
                        _partnerPercentages.value = parsed
                        try {
                            val prefs = context.getSharedPreferences("workspace_percentages_prefs", android.content.Context.MODE_PRIVATE)
                            val json = org.json.JSONObject(parsed as Map<*, *>).toString()
                            prefs.edit().putString("pct_$listenerWsId", json).apply()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed caching percentages: ${e.message}")
                        }
                    }

                    // Load profit share allocations (UID-based)
                    val rawAllocations = remoteSettingsMap["profitShareAllocations"] as? List<*>
                    if (rawAllocations != null && rawAllocations.isNotEmpty()) {
                        val percentageMap = mutableMapOf<String, Int>()
                        val detailMap = mutableMapOf<String, com.example.data.firebase.ProfitShareAllocation>()
                        for (item in rawAllocations) {
                            if (item is Map<*, *>) {
                                val uid = item["participantUid"] as? String ?: ""
                                val percentage = (item["percentage"] as? Number)?.toInt() ?: 0
                                if (uid.isNotBlank()) {
                                    percentageMap[uid] = percentage
                                    detailMap[uid] = com.example.data.firebase.ProfitShareAllocation(
                                        participantUid = uid,
                                        percentage = percentage,
                                        displayName = item["displayName"] as? String ?: "",
                                        role = item["role"] as? String ?: "partner",
                                        isActive = (item["isActive"] as? Boolean) ?: true
                                    )
                                }
                            }
                        }
                        if (percentageMap.isNotEmpty()) {
                            _profitShareAllocations.value = percentageMap
                            _profitShareAllocationDetails.value = detailMap
                            // Cache locally
                            try {
                                val prefs = context.getSharedPreferences("workspace_profit_share_prefs", android.content.Context.MODE_PRIVATE)
                                val jsonObj = org.json.JSONObject()
                                percentageMap.forEach { (uid, percentage) ->
                                    val detail = detailMap[uid]
                                    val itemObj = org.json.JSONObject().apply {
                                        put("participantUid", uid)
                                        put("percentage", percentage)
                                        put("displayName", detail?.displayName ?: "")
                                        put("role", detail?.role ?: "partner")
                                        put("isActive", detail?.isActive ?: true)
                                    }
                                    jsonObj.put(uid, itemObj)
                                }
                                prefs.edit().putString("psa_$listenerWsId", jsonObj.toString()).apply()
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed caching profit share allocations: ${e.message}")
                            }
                            Log.d(TAG, "Real-time update: profitShareAllocations for $listenerWsId: $percentageMap")
                        }
                    }
                }
            },
            onPaymentsUpdated = { remotePayments, listenerWsId ->
                replicaUpdates.submit {
                    syncRemotePaymentsToLocal(remotePayments, listenerWsId)
                }
            },
            onAccessControlUpdated = { remoteData, listenerWsId ->
                scope.launch {
                    val activeWsId = _activeWorkspaceId.value
                    val pWsId = _personalWorkspaceId.value
                    if (listenerWsId != activeWsId && listenerWsId != pWsId && !_visibleWorkspaceIds.value.contains(listenerWsId)) {
                        return@launch
                    }
                    val doc = if (remoteData != null) {
                        com.example.data.auth.AccessControlDocument.fromMap(remoteData)
                    } else {
                        com.example.data.auth.AccessControlDocument(accountStatus = "ACTIVE")
                    }
                    _accessControlState.value = doc
                    com.example.data.auth.AuthorizationManager.currentAccessControl = doc
                    accessControlPrefs.edit().putString("access_control_$listenerWsId", doc.toJsonString()).apply()
                    Log.d(TAG, "AccessControl updated for $listenerWsId: status=${doc.accountStatus} blocked=${doc.isAccountBlocked}")
                }
            },
            onPartnerPermissionsUpdated = { remoteData, listenerWsId ->
                scope.launch {
                    if (remoteData == null) return@launch
                    val partnerKey = remoteData["partnerKey"] as? String ?: return@launch
                    val lockedPagesList = (remoteData["lockedPages"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()
                    val targetKey = partnerKey.trim().replace(Regex("[^a-zA-Z0-9_]"), "_")
                    partnerAccessPrefs.edit().putString("locked_pages_${listenerWsId}_$targetKey", lockedPagesList.joinToString(",")).apply()
                    refreshLockedPages(listenerWsId)
                    Log.d(TAG, "Partner permissions synced for $partnerKey in $listenerWsId: locks=$lockedPagesList")
                }
            },
            onExtensionsUpdated = { remoteExtensions, listenerWsId ->
                scope.launch {
                    val pWsId = _personalWorkspaceId.value
                    val activeWsId = _activeWorkspaceId.value
                    if (!_visibleWorkspaceIds.value.contains(listenerWsId) && listenerWsId != activeWsId && listenerWsId != pWsId) {
                        return@launch
                    }
                    val pendingIds = operations.outstandingOperations(listenerWsId).filter { it.entityType == "EXTENSION" }.map { it.entityId }.toSet()
                    workTypeExtensionDao.insertAll(remoteExtensions.filter { it.id !in pendingIds })
                    Log.d(TAG, "Extensions synced for $listenerWsId: count=${remoteExtensions.size}")
                }
            }
        )
        // Immediately load any cached access control for the active workspace
        val fallbackWs = _activeWorkspaceId.value ?: _personalWorkspaceId.value ?: ""
        if (fallbackWs.isNotBlank()) {
            loadCachedAccessControl(fallbackWs)
        }
    }

    private suspend fun syncRemotePaymentsToLocal(remotePayments: List<PaymentEntity>, listenerWsId: String) {
        val pWsId = _personalWorkspaceId.value
        val activeWsId = _activeWorkspaceId.value
        if (!_visibleWorkspaceIds.value.contains(listenerWsId) && listenerWsId != activeWsId && listenerWsId != pWsId) {
            return
        }
        if (activeUid != getEffectiveActorUid() || listenerWsId != activeWsId) return
        database.withTransaction {
            if (activeUid != getEffectiveActorUid() || listenerWsId != _activeWorkspaceId.value) return@withTransaction
            require(remotePayments.all { it.workspaceId == listenerWsId }) { "Collection snapshot workspace mismatch" }
            val outstanding = operations.outstandingOperations(listenerWsId)
            val compoundCollectionIds = outstanding.filter {
                it.entityType == "JOB" && it.mutationType == "DELETE" && it.state == "PENDING"
            }.flatMap { op ->
                val ids = org.json.JSONObject(op.payloadJson).optJSONArray("linkedCollectionIds")
                if (ids == null) emptyList() else (0 until ids.length()).map { ids.getString(it).toLong() }
            }
            val pendingIds = (outstanding.filter { it.entityType == "PAYMENT" }.map { it.entityId } + compoundCollectionIds).toSet()
            val confirmedDeletedIds = operations.observe(listenerWsId).firstOrNull().orEmpty().filter {
                it.entityType == "JOB" && it.mutationType == "DELETE" && it.state == "ACKNOWLEDGED"
            }.flatMap { op ->
                val ids = org.json.JSONObject(op.payloadJson).optJSONArray("linkedCollectionIds")
                if (ids == null) emptyList() else (0 until ids.length()).map { ids.getString(it).toLong() }
            }.toSet()
            // Repair old clients' provisional reversal rows once the compound
            // deletion is confirmed. They must not remain permanently unsynced.
            confirmedDeletedIds.filterNot { it in pendingIds }.forEach { paymentDao.deletePayment(listenerWsId, it) }
            val unsynced = paymentDao.getUnsyncedPaymentsForWorkspace(listenerWsId).associateBy { it.id }
            paymentDao.insertPayments(remotePayments.filter { payment -> payment.id !in pendingIds && payment.id !in confirmedDeletedIds &&
                unsynced[payment.id]?.let { com.example.data.sync.ConfirmedReplica.payment(it, payment, false) } != false
            }.map { it.copy(workspaceId = listenerWsId, isSynced = true) })
            // A successful full listener snapshot is authoritative for confirmed Collections.
            // Preserve pending/rejected and unsynced local actions; remove only synced cloud rows
            // absent from this workspace's snapshot (including legacy phantom ACTIVE rows).
            val retainedIds = (remotePayments.map { it.id }.filterNot { it in confirmedDeletedIds } + pendingIds).distinct()
            if (retainedIds.isEmpty()) paymentDao.deleteAllSyncedForWorkspace(listenerWsId)
            else paymentDao.deleteSyncedNotIn(listenerWsId, retainedIds)
        }
    }

    /**
     * Cleanly detaches all cloud snapshot listeners on logout.
     */
    fun stopWorkspaceListeners() {
        workspaceRequests.invalidate()
        _partnerPercentages.value = emptyMap()
        _profitShareAllocations.value = emptyMap()
        _profitShareAllocationDetails.value = emptyMap()
        _verifiedWorkspaceContext.value = null
        firestoreRepository.stopRealtimeListeners()
        collaborationIndexListener?.remove()
        collaborationIndexListener = null
        groupMembersListeners.values.forEach { it.remove() }
        groupMembersListeners.clear()
        groupPendingListeners.values.forEach { it.remove() }
        groupPendingListeners.clear()
        groupWorkspacesMap.clear()
        _workspaceMembers.value = emptyList()
        _activeWorkspaceMembers.value = emptyList()
        _isInitialized.value = false
        _workspaceInitState.value = WorkspaceInitState.Uninitialized
        _settingsSyncState.value = SettingsSyncState.Uninitialized
        activeUid = null
        _personalWorkspaceId.value = null
        _visibleWorkspaceIds.value = emptySet()
        _activeWorkspaceId.value = null
        _genuinePartnerWorkspaces.value = emptyList()
        // Reset to the least-privileged state. Owner must be re-resolved from the
        // membership record on the next initializeForUser(), never assumed.
        _isCollaborationOwner.value = false
        currentUserRole = null
        _workspaceMembers.value = emptyList()
        _activeWorkspaceMembers.value = emptyList()
        Log.d(TAG, "Workspace listeners stopped.")
    }

    // --- Remote to Local Synchronization Helpers (ID-based Reconciliation) ---

    internal suspend fun syncRemoteJobsToLocal(remoteJobs: List<JobEntryEntity>, listenerWsId: String) {
        val snapshotActorUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (snapshotActorUid.isNullOrBlank() || activeUid != snapshotActorUid || !_visibleWorkspaceIds.value.contains(listenerWsId)) return
        try {
            database.withTransaction {
                if (activeUid != snapshotActorUid || getEffectiveActorUid() != snapshotActorUid ||
                    !_visibleWorkspaceIds.value.contains(listenerWsId)) return@withTransaction
                require(remoteJobs.all { it.workspaceId == listenerWsId }) { "Entry snapshot workspace mismatch" }
                val history = operations.observe(listenerWsId).firstOrNull().orEmpty()
                val deletes = history.filter { it.entityType == "JOB" && it.mutationType == "DELETE" }
                val pendingIds = pendingDeleteManager.getPendingDeleteIds("JOB", listenerWsId) +
                    deletes.filter { it.state == "PENDING" }.map { it.entityId }
                val confirmed = deletes.filter { it.state == "ACKNOWLEDGED" }
                val deletedCustomers = confirmed.mapNotNull { org.json.JSONObject(it.payloadJson).optLong("customerId").takeIf { id -> id > 0 } }.toSet()
                val unsyncedRows = jobEntryDao.getUnsyncedJobsForWorkspace(listenerWsId).associateBy { it.id }
                val outstandingIds = history.filter { it.entityType == "JOB" && it.state in setOf("PENDING", "REJECTED") }.map { it.entityId }.toSet()
                val retained = remoteJobs.filterNot { job -> job.id in pendingIds || confirmed.any { deletion ->
                    deletion.entityId == job.id && org.json.JSONObject(deletion.payloadJson).optLong("createdAt") == job.createdAt
                } }
                for (job in retained) if (unsyncedRows[job.id]?.let {
                        com.example.data.sync.ConfirmedReplica.job(it, job, job.id in outstandingIds)
                    } != false)
                    jobEntryDao.insertJob(job.copy(workspaceId = listenerWsId, isSynced = true))
                val ids = retained.map { it.id }
                if (ids.isEmpty()) jobEntryDao.deleteAllSyncedForWorkspace(listenerWsId)
                else jobEntryDao.deleteSyncedNotIn(listenerWsId, ids)
                val jobs = jobEntryDao.getJobsForWorkspaceOnce(listenerWsId)
                for (customer in customerDao.getCustomersForWorkspaceOnce(listenerWsId)) {
                    val source = if (customer.id in deletedCustomers)
                        customer.copy(totalBilled = 0.0, totalPaid = 0.0, balanceDue = 0.0) else customer
                    val stats = com.example.ui.util.FinancialCalculationEngine.calculateCustomerFinancials(source, jobs)
                    if (stats.totalBilled != customer.totalBilled || stats.totalPaid != customer.totalPaid || stats.balanceDue != customer.balanceDue)
                        customerDao.updateCustomer(customer.copy(totalBilled = stats.totalBilled,
                            totalPaid = stats.totalPaid, balanceDue = stats.balanceDue, updatedAt = System.currentTimeMillis()))
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) { Log.e(TAG, "Error syncing remote jobs to local: ${e.message}") }
    }
    private suspend fun syncRemoteExpensesToLocal(remoteExpenses: List<ExpenseEntity>, listenerWsId: String) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (activeUid != currentUid || !_visibleWorkspaceIds.value.contains(listenerWsId)) {
            Log.w(TAG, "Ignoring expenses snapshot from $listenerWsId because it is not in visible workspaces")
            return
        }
        try {
            database.withTransaction {
                if (currentUid.isNullOrBlank() || getEffectiveActorUid() != currentUid || activeUid != currentUid ||
                    !_visibleWorkspaceIds.value.contains(listenerWsId)) return@withTransaction
            val compoundPendingIds = operations.outstandingOperations(listenerWsId)
                .filter { it.entityType == "JOB" && it.mutationType == "DELETE" && it.state == "PENDING" }
                .flatMap { op ->
                    val ids = org.json.JSONObject(op.payloadJson).optJSONArray("linkedExpenseIds")
                    if (ids == null) emptyList() else (0 until ids.length()).map { ids.getLong(it) }
                }.toSet()
            val pendingDeleteIds = pendingDeleteManager.getPendingDeleteIds("EXPENSE", listenerWsId) + compoundPendingIds
            val unsyncedLocalExpenses = expenseDao.getUnsyncedExpensesForWorkspace(listenerWsId).associateBy { it.id }
            val outstandingIds = operations.outstandingOperations(listenerWsId).filter { it.entityType == "EXPENSE" }.map { it.entityId }.toSet()
            val validRemoteExpenses = remoteExpenses.filterNot { pendingDeleteIds.contains(it.id) }

            for (expense in validRemoteExpenses) {
                if (unsyncedLocalExpenses[expense.id]?.let {
                        com.example.data.sync.ConfirmedReplica.expense(it, expense, expense.id in outstandingIds)
                    } == false) {
                    // Do not overwrite pending local edits
                    continue
                }
                expenseDao.insertExpense(expense.copy(workspaceId = listenerWsId, isSynced = true))
            }

            val validRemoteIds = validRemoteExpenses.map { it.id }
            if (validRemoteIds.isNotEmpty()) {
                expenseDao.deleteSyncedNotIn(listenerWsId, validRemoteIds)
            } else {
                expenseDao.deleteAllSyncedForWorkspace(listenerWsId)
            }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote expenses to local: ${e.message}")
        }
    }

    internal suspend fun syncRemoteCustomersToLocal(remoteCustomers: List<CustomerEntity>, listenerWsId: String) {
        val snapshotActorUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (snapshotActorUid.isNullOrBlank() || activeUid != snapshotActorUid || !_visibleWorkspaceIds.value.contains(listenerWsId)) return
        try {
            // ACK can finish before a previously delivered customer snapshot is applied.
            // Check actual server proof, since deleting one job may leave other customer jobs intact.
            val sourceCustomerIds = operations.observe(listenerWsId).firstOrNull().orEmpty().filter {
                it.entityType == "JOB" && it.mutationType == "DELETE" && it.state == "ACKNOWLEDGED"
            }.map { org.json.JSONObject(it.payloadJson).optLong("customerId") }.toSet()
            val confirmedDeletedIds = mutableSetOf<Long>()
            for (customer in remoteCustomers.filter { it.id in sourceCustomerIds }) {
                val proof = firestoreRepository.getCustomerDeletionProofFromServer(listenerWsId, customer.id)
                if (com.example.data.sync.ConfirmedEntryDeletion.matchesCustomerProof(customer, proof))
                    confirmedDeletedIds.add(customer.id)
            }
            database.withTransaction {
                if (activeUid != snapshotActorUid || getEffectiveActorUid() != snapshotActorUid ||
                    !_visibleWorkspaceIds.value.contains(listenerWsId)) return@withTransaction
                require(remoteCustomers.all { it.workspaceId == listenerWsId }) { "Customer snapshot workspace mismatch" }
                // Serialize the source rows and pending protection with deletion/acknowledgement.
                val pendingDeleteIds = pendingDeleteManager.getPendingDeleteIds("CUSTOMER", listenerWsId)
                val unsyncedIds = customerDao.getUnsyncedCustomersForWorkspace(listenerWsId).map { it.id }.toSet()
                val retained = remoteCustomers.filterNot { it.id in pendingDeleteIds || it.id in confirmedDeletedIds }
                val jobs = jobEntryDao.getJobsForWorkspaceOnce(listenerWsId)
                val deletedSourceCustomers = operations.observe(listenerWsId).firstOrNull().orEmpty().filter {
                    it.entityType == "JOB" && it.mutationType == "DELETE" && it.state == "ACKNOWLEDGED"
                }.mapNotNull { op -> org.json.JSONObject(op.payloadJson).optLong("customerId").takeIf { it > 0 } }.toSet()
                for (customer in retained) {
                    if (customer.id in unsyncedIds) continue
                    // Empty-job legacy totals remain intact except where a source deletion was confirmed.
                    val source = if (customer.id in deletedSourceCustomers)
                        customer.copy(totalBilled = 0.0, totalPaid = 0.0, balanceDue = 0.0) else customer
                    val stats = com.example.ui.util.FinancialCalculationEngine.calculateCustomerFinancials(source, jobs)
                    customerDao.insertCustomer(customer.copy(workspaceId = listenerWsId,
                        totalBilled = stats.totalBilled, totalPaid = stats.totalPaid,
                        balanceDue = stats.balanceDue, isSynced = true))
                }
                val ids = retained.map { it.id }
                if (ids.isEmpty()) customerDao.deleteAllSyncedForWorkspace(listenerWsId)
                else customerDao.deleteSyncedNotIn(listenerWsId, ids)
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) { Log.e(TAG, "Error syncing remote customers to local: ${e.message}") }
    }
    private suspend fun syncRemoteTractorsToLocal(remoteTractors: List<TractorEntity>, listenerWsId: String) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (activeUid != currentUid || !_visibleWorkspaceIds.value.contains(listenerWsId)) {
            Log.w(TAG, "Ignoring tractors snapshot from $listenerWsId because it is not in visible workspaces")
            return
        }
        try {
            val pendingDeleteIds = pendingDeleteManager.getPendingDeleteIds("TRACTOR", listenerWsId)
            val queuedIds = operations.outstandingOperations(listenerWsId).filter { it.entityType == "TRACTOR" }.map { it.entityId }.toSet()
            val validRemoteTractors = remoteTractors.filterNot { pendingDeleteIds.contains(it.id) || it.id in queuedIds }

            for (tractor in validRemoteTractors) {
                tractorDao.insertTractor(tractor.copy(workspaceId = listenerWsId))
            }

            val validRemoteIds = validRemoteTractors.map { it.id } + queuedIds
            if (validRemoteIds.isNotEmpty()) {
                tractorDao.deleteNotIn(listenerWsId, validRemoteIds)
            } else {
                tractorDao.deleteAllForWorkspace(listenerWsId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote tractors to local: ${e.message}")
        }
    }

    private suspend fun syncRemotePartnersToLocal(remotePartners: List<PartnerEntity>, listenerWsId: String) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (activeUid != currentUid || !_visibleWorkspaceIds.value.contains(listenerWsId)) {
            Log.w(TAG, "Ignoring partners snapshot from $listenerWsId because it is not in visible workspaces")
            return
        }
        try {
            val pendingDeleteIds = pendingDeleteManager.getPendingDeleteIds(com.example.data.auth.RoleUtils.ROLE_PARTNER, listenerWsId)
            val validRemotePartners = remotePartners.filterNot { pendingDeleteIds.contains(it.id) }

            for (partner in validRemotePartners) {
                partnerDao.insertPartner(partner.copy(workspaceId = listenerWsId))
            }

            val validRemoteIds = validRemotePartners.map { it.id }
            if (validRemoteIds.isNotEmpty()) {
                partnerDao.deleteNotIn(listenerWsId, validRemoteIds)
            } else {
                partnerDao.deleteAllForWorkspace(listenerWsId)
            }
            refreshWorkspaceMembers()
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote partners to local: ${e.message}")
        }
    }

    private suspend fun syncRemoteWithdrawalsToLocal(remoteWithdrawals: List<WithdrawalEntity>, listenerWsId: String) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (activeUid != currentUid || !_visibleWorkspaceIds.value.contains(listenerWsId)) {
            Log.w(TAG, "Ignoring withdrawals snapshot from $listenerWsId because it is not in visible workspaces")
            return
        }
        try {
            database.withTransaction {
                if (currentUid.isNullOrBlank() || getEffectiveActorUid() != currentUid || activeUid != currentUid ||
                    !_visibleWorkspaceIds.value.contains(listenerWsId)) return@withTransaction
            val pendingDeleteIds = pendingDeleteManager.getPendingDeleteIds("WITHDRAWAL", listenerWsId)
            val unsyncedLocalWithdrawals = withdrawalDao.getUnsyncedWithdrawalsForWorkspace(listenerWsId).associateBy { it.id }
            val outstandingIds = operations.outstandingOperations(listenerWsId).filter { it.entityType == "WITHDRAWAL" }.map { it.entityId }.toSet()
            val validRemoteWithdrawals = remoteWithdrawals.filterNot { pendingDeleteIds.contains(it.id) }

            for (withdrawal in validRemoteWithdrawals) {
                if (unsyncedLocalWithdrawals[withdrawal.id]?.let {
                        com.example.data.sync.ConfirmedReplica.withdrawal(it, withdrawal, withdrawal.id in outstandingIds)
                    } == false) {
                    // Do not overwrite pending local edits
                    continue
                }
                withdrawalDao.insertWithdrawal(withdrawal.copy(workspaceId = listenerWsId, isSynced = true))
            }

            val validRemoteIds = validRemoteWithdrawals.map { it.id }
            if (validRemoteIds.isNotEmpty()) {
                withdrawalDao.deleteSyncedNotIn(listenerWsId, validRemoteIds)
            } else {
                withdrawalDao.deleteAllSyncedForWorkspace(listenerWsId)
            }
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote withdrawals to local: ${e.message}")
        }
    }

    private suspend fun getOrResolveWorkspaceId(): String? {
        val current = _activeWorkspaceId.value
        if (!current.isNullOrBlank()) return current
        val pWsId = _personalWorkspaceId.value
        if (!pWsId.isNullOrBlank()) return pWsId
        val readyWsId = (_workspaceInitState.value as? WorkspaceInitState.Ready)?.workspaceId
        if (!readyWsId.isNullOrBlank()) return readyWsId
        val wsCurrent = currentWorkspace.value?.workspaceId
        if (!wsCurrent.isNullOrBlank()) return wsCurrent
        return null
    }

    private fun requireRecordMembersActive(creatorUid: String, targetUid: String = creatorUid) {
        com.example.data.auth.RecordIdentityPolicy.requireActive(
            _activeWorkspaceId.value.orEmpty(), creatorUid, targetUid, _activeWorkspaceMembers.value)
    }

    private fun requireWorkspaceActor(wsId: String): String {
        if (com.example.BuildConfig.PHYSICAL_TEST && !com.example.data.firebase.FirebaseEnvironment.isStagingWorkspace(wsId))
            throw SecurityException("Physical test access is limited to the staging workspace")
        val actor = getEffectiveActorUid().orEmpty()
        if (actor.isBlank() || _verifiedWorkspaceContext.value != (actor to wsId)) throw SecurityException("Verify workspace access for this authenticated account")
        val role = resolveRoleForWorkspace(wsId)
        if (role.isBlank()) throw SecurityException("Active authenticated workspace membership is required")
        return role
    }

    private fun cacheVerifiedMembership(uid: String, wsId: String, members: List<WorkspaceMember>) {
        if (members.none { it.uid == uid && it.workspaceId == wsId && it.status.equals("active", true) }) return
        val json = org.json.JSONArray()
        members.forEach { json.put(org.json.JSONObject(it.toMap())) }
        context.getSharedPreferences("verified_workspace_session", Context.MODE_PRIVATE).edit()
            .putString("uid", uid).putString("workspaceId", wsId).putString("members", json.toString()).commit()
        _verifiedWorkspaceContext.value = uid to wsId
    }

    private fun restoreVerifiedMembership(uid: String) {
        val prefs = context.getSharedPreferences("verified_workspace_session", Context.MODE_PRIVATE)
        if (prefs.getString("uid", "") != uid) return
        val wsId = prefs.getString("workspaceId", "").orEmpty()
        if (com.example.BuildConfig.PHYSICAL_TEST && !com.example.data.firebase.FirebaseEnvironment.isStagingWorkspace(wsId)) return
        if (wsId.isBlank()) return
        val json = runCatching { org.json.JSONArray(prefs.getString("members", "[]")) }.getOrNull() ?: return
        val members = (0 until json.length()).map { index ->
            val row = json.getJSONObject(index)
            WorkspaceMember(uid = row.optString("uid"), workspaceId = wsId,
                role = row.optString("role"), status = row.optString("status"), displayName = row.optString("displayName"))
        }
        val actor = members.firstOrNull { it.uid == uid && it.status.equals("active", true) } ?: return
        val role = com.example.data.auth.RoleUtils.normalizeRole(actor.role)
        if (role.isBlank()) return
        _activeWorkspaceId.value = wsId
        _visibleWorkspaceIds.value = setOf(wsId)
        _activeWorkspaceMembers.value = members
        _workspaceMembers.value = members
        currentUserRole = role
        _isCollaborationOwner.value = role == com.example.data.auth.RoleUtils.ROLE_OWNER
        _workspaceInitState.value = WorkspaceInitState.CachedReady(wsId)
        _isInitialized.value = true
        _verifiedWorkspaceContext.value = uid to _activeWorkspaceId.value.orEmpty()
    }

    private val syncMutex = Mutex()
    private val operations get() = database.pendingOperationDao()
    fun pendingOperations(wsId: String): kotlinx.coroutines.flow.Flow<List<com.example.data.entity.PendingOperationEntity>> {
        val uid = getEffectiveActorUid().orEmpty()
        if (_verifiedWorkspaceContext.value != (uid to wsId) || uid.isBlank()) return kotlinx.coroutines.flow.flowOf(emptyList())
        return operations.observeActor(wsId, uid)
    }

    /** Reopen only the original collection action after verifying current access and cloud state. */
    suspend fun retryRejectedCollection(operationId: String) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val op = operations.getOperation(operationId) ?: throw IllegalArgumentException("Retained action was not found")
            val uid = getEffectiveActorUid().orEmpty()
            if (op.actorUid != uid || op.workspaceId != _activeWorkspaceId.value ||
                _verifiedWorkspaceContext.value != (uid to op.workspaceId))
                throw SecurityException("Sign in to the original account and workspace to retry this action")
            if (op.state != "REJECTED" || op.entityType != "PAYMENT" || op.mutationType != "UPSERT" ||
                !op.failureReason.contains("PERMISSION_DENIED", ignoreCase = true))
                throw IllegalStateException("This action is not eligible for collection retry")
            val role = firestoreRepository.resolveAuthoritativeRole(uid, op.workspaceId)
                ?: throw SecurityException("Workspace access has been removed; action retained")
            com.example.data.sync.OperationPolicy.requireAuthorized(op, role,
                firestoreRepository.getWorkspaceMembers(op.workspaceId))
            val json = org.json.JSONObject(op.payloadJson)
            val source = firestoreRepository.getEntryFromServer(op.workspaceId, json.getLong("jobEntryId"))
                ?: throw IllegalStateException("Source job is not confirmed; original collection retained")
            require(kotlin.math.abs(source.amountReceived - json.getDouble("baseAmountReceived")) < 0.001 &&
                kotlin.math.abs(source.pendingAmount - json.getDouble("basePendingAmount")) < 0.001) {
                "Source balances changed; inspect the original collection before authorizing recovery"
            }
            require(operations.outstandingOperations(op.workspaceId).none { it.operationId != op.operationId &&
                it.state == "REJECTED" && it.entityType == "JOB" && it.entityId == source.id }) {
                "Resolve the rejected source job first; original collection retained"
            }
            val cloud = try { firestoreRepository.getOperationRecordFromServer(op) }
                catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                    if (e.code == com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
                        throw SecurityException("Collection access is still denied by deployed Firebase Rules. Original action retained.")
                    throw e
                }
            if (cloud != null) throw IllegalStateException("Collection already exists in Cloud; reconcile it before retrying")
            if (getEffectiveActorUid() != uid || _activeWorkspaceId.value != op.workspaceId)
                throw SecurityException("Account or workspace changed; original action retained")
            check(operations.requeueRejected(operationId) == 1) { "Action state changed; refresh and retry" }
        }
    }

    private suspend fun enqueue(type: String, id: Long, payload: Map<String, Any?>, mutation: String = "UPSERT", expectedActorUid: String? = null, actionTime: Long = System.currentTimeMillis(), baseSnapshot: Map<String, Any?>? = null, operationId: String = java.util.UUID.randomUUID().toString()) {
        val wsId = payload["workspaceId"] as? String ?: throw SecurityException("Missing operation workspace")
        requireWorkspaceActor(wsId)
        val operationActor = getEffectiveActorUid().orEmpty()
        require(expectedActorUid == null || operationActor == expectedActorUid) { "Account changed before queuing deletion" }
        operations.supersedeReconciledRejections(wsId, operationActor, type, id)
        val command = PendingOperationEntity(operationId, wsId,
            operationActor, type, id, mutation, operations.nextSequence(), actionTime,
            org.json.JSONObject(payload).put("operationProtocolVersion", 2).apply {
                if (type in setOf("JOB", "EXPENSE") && mutation == "UPSERT")
                    put("expectedBaseJson", baseSnapshot?.let { org.json.JSONObject(it).toString() } ?: "null")
            }.toString())
        operations.insert(command)
    }

    private suspend fun reconcileRejectedOperation(op: PendingOperationEntity): Boolean {
        fun contextMatches() = getEffectiveActorUid() == op.actorUid && _activeWorkspaceId.value == op.workspaceId
        if (!contextMatches()) return false
        val cloud = firestoreRepository.getOperationRecordFromServer(op)
        if (!contextMatches()) return false
        if (op.entityType == "JOB") return com.example.data.sync.OfflineEntryRecovery.restore(database, op, cloud,
            contextMatches = { contextMatches() }, recalculate = { recalculateCustomerStats(it, op.workspaceId, clearDeletedSourceBalance = true) })
        val sourceId = if (op.entityType == "PAYMENT") org.json.JSONObject(op.payloadJson).getLong("jobEntryId") else null
        val source = sourceId?.let { firestoreRepository.getEntryFromServer(op.workspaceId, it) }
        return database.withTransaction {
            if (!contextMatches()) return@withTransaction false
            val pending = operations.outstandingOperations(op.workspaceId)
            val newerRecord = pending.any { it.state == "PENDING" && it.entityType == op.entityType && it.entityId == op.entityId && it.sequence > op.sequence }
            val pendingSource = sourceId != null && pending.any { it.state == "PENDING" &&
                ((it.entityType == "JOB" && it.entityId == sourceId) ||
                    (it.entityType == "PAYMENT" && it.operationId != op.operationId && org.json.JSONObject(it.payloadJson).optLong("jobEntryId") == sourceId)) }
            if (newerRecord || pendingSource) return@withTransaction false
            when(op.entityType) {
                "CUSTOMER" -> if (cloud != null) customerDao.insertCustomer(com.example.data.firebase.customerFromFirestoreMap(cloud, op.entityId, op.workspaceId))
                    else customerDao.getCustomerById(op.workspaceId, op.entityId)?.let { customerDao.deleteCustomer(it) }
                "EXPENSE" -> if (cloud != null) expenseDao.insertExpense(com.example.data.firebase.expenseFromFirestoreMap(cloud, op.entityId, op.workspaceId))
                    else {
                        expenseDao.getExpenseById(op.workspaceId, op.entityId)?.let { expenseDao.deleteExpense(it) }
                        if (op.mutationType == "DELETE" && op.state == "REJECTED" &&
                            op.failureReason.startsWith("Confirmed state restored;"))
                            operations.resolveAbsentExpenseDelete(op.operationId, op.workspaceId)
                    }
                "WITHDRAWAL" -> if (cloud != null) withdrawalDao.insertWithdrawal(com.example.data.firebase.withdrawalFromFirestoreMap(cloud, op.entityId, op.workspaceId))
                    else withdrawalDao.getWithdrawalById(op.workspaceId, op.entityId)?.let { withdrawalDao.deleteWithdrawal(it) }
                "PAYMENT" -> {
                    if (cloud != null) paymentDao.insertPayment(com.example.data.firebase.paymentFromFirestoreMap(cloud, op.entityId, op.workspaceId))
                    else paymentDao.deletePayment(op.workspaceId, op.entityId)
                    if (source != null) { jobEntryDao.insertJob(source); recalculateCustomerStats(source.customerId, op.workspaceId) }
                }
            }
            true
        }
    }

    private fun clearCompoundDeleteMarkers(op: PendingOperationEntity) {
        if (op.mutationType != "DELETE") return
        pendingDeleteManager.removePendingDelete(op.entityType, op.entityId, op.workspaceId)
        if (op.entityType == "JOB") {
            val ids = org.json.JSONObject(op.payloadJson).optJSONArray("linkedExpenseIds")
            if (ids != null) for (index in 0 until ids.length())
                pendingDeleteManager.removePendingDelete("EXPENSE", ids.getLong(index), op.workspaceId)
        }
    }

    private suspend fun acknowledge(op: PendingOperationEntity) {
        database.withTransaction {
            operations.acknowledge(op.operationId)
            if (op.mutationType == "DELETE") pendingDeleteManager.removePendingDelete(op.entityType, op.entityId, op.workspaceId)
            if (op.entityType == "JOB" && op.mutationType == "DELETE") {
                val deletedSource = org.json.JSONObject(op.payloadJson)
                val customerId = deletedSource.getLong("customerId")
                val remainingOperations = operations.outstandingOperations(op.workspaceId)
                val newerSourceWrite = remainingOperations.any {
                    it.entityType == "JOB" && it.entityId == op.entityId && it.mutationType == "UPSERT" &&
                        it.state == "PENDING" && it.sequence > op.sequence
                }
                // Repair a retained/stale local source only after the trusted server confirms deletion.
                if (!newerSourceWrite) jobEntryDao.getJobById(op.workspaceId, op.entityId)?.takeIf {
                    it.createdAt == deletedSource.optLong("createdAt")
                }?.let { jobEntryDao.deleteJob(it) }
                val linkedExpenseIds = deletedSource.optJSONArray("linkedExpenseIds")
                if (linkedExpenseIds != null) for (index in 0 until linkedExpenseIds.length()) {
                    val expenseId = linkedExpenseIds.getLong(index)
                    expenseDao.getExpenseById(op.workspaceId, expenseId)?.takeIf {
                        it.relatedJobId == op.entityId
                    }?.let { expenseDao.deleteExpense(it) }
                    pendingDeleteManager.removePendingDelete("EXPENSE", expenseId, op.workspaceId)
                    operations.supersedeExpenseDeleteAfterEntryDelete(op.workspaceId, expenseId)
                }
                com.example.data.sync.ConfirmedEntryDeletion.apply(database, op, allowCustomerCleanup = isOwnerOfWorkspace(op.workspaceId))
                val relatedPending = remainingOperations.any { remaining ->
                    com.example.data.sync.OperationDependencies.blocksCustomerAcknowledgement(remaining, customerId)
                }
                if (!relatedPending) {
                    // A pre-delete customer snapshot can arrive while its command is in flight.
                    // Reconcile the acknowledged deletion before declaring its balance synced.
                    recalculateCustomerStats(customerId, op.workspaceId, clearDeletedSourceBalance = true)
                    customerDao.markCustomersSynced(op.workspaceId, listOf(customerId))
                }
            }
            if (operations.outstanding(op.workspaceId, op.entityType, op.entityId) == 0 && op.mutationType != "DELETE") {
                when (op.entityType) {
                    "JOB" -> if (operations.outstandingOperations(op.workspaceId).none {
                        com.example.data.sync.OperationDependencies.blocksEntryAcknowledgement(it, op.entityId)
                    }) jobEntryDao.markJobsSynced(op.workspaceId, listOf(op.entityId))
                    "EXPENSE" -> expenseDao.markExpensesSynced(op.workspaceId, listOf(op.entityId))
                    "CUSTOMER" -> customerDao.markCustomersSynced(op.workspaceId, listOf(op.entityId))
                    "WITHDRAWAL" -> withdrawalDao.markWithdrawalsSynced(op.workspaceId, listOf(op.entityId))
                    "PAYMENT" -> {
                        paymentDao.markPaymentsSynced(op.workspaceId, listOf(op.entityId))
                        val jobId = org.json.JSONObject(op.payloadJson).optLong("jobEntryId")
                        if (operations.outstandingOperations(op.workspaceId).none {
                            com.example.data.sync.OperationDependencies.blocksEntryAcknowledgement(it, jobId)
                        }) jobEntryDao.markJobsSynced(op.workspaceId, listOf(jobId))
                    }
                }
            }
            if (op.entityType in setOf("JOB", "PAYMENT") && op.mutationType != "DELETE") {
                val customerId = org.json.JSONObject(op.payloadJson).optLong("customerId")
                if (customerId > 0) recalculateCustomerStats(customerId, op.workspaceId)
            }
        }
    }

    // --- CRUD Bridge (Local + Cloud with Collision-Resistant IDs) ---

    suspend fun saveJobEntry(
        job: JobEntryEntity,
        linkedExpense: ExpenseEntity? = null,
        linkedExpenses: List<ExpenseEntity> = emptyList()
    ): Long {
        val operationActorUid = getEffectiveActorUid().orEmpty()
        val operationTime = System.currentTimeMillis()
        val result = database.withTransaction {

        com.example.ui.util.requireOptionalContactPhone(job.customerPhone)
        Log.d("TRAC_AUTH", "activeUid=$activeUid")
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: ""
        val existingJob = if (job.id > 0) jobEntryDao.getJobById(_activeWorkspaceId.value.orEmpty(), job.id) else null
        if (existingJob != null && existingJob.workspaceId.isNotBlank() && existingJob.workspaceId != wsId) {
            throw SecurityException("Entry does not belong to the active workspace")
        }
        val verifiedActorRole = requireWorkspaceActor(wsId)
        if (job.workspaceId.isNotBlank() && job.workspaceId != wsId) throw SecurityException("Entry workspace mismatch")
        val authenticatedUid = getEffectiveActorUid().orEmpty()
        val entryTarget = existingJob?.entryForUid ?: job.entryForUid.ifBlank { authenticatedUid }
        if (!com.example.data.auth.AuthorizationManager.canCreateEntryFor(isOwnerOfWorkspace(wsId), authenticatedUid, entryTarget, verifiedActorRole)) {
            throw SecurityException("Entry target must be the authenticated member")
        }
        if (existingJob == null && entryTarget != authenticatedUid && _activeWorkspaceMembers.value.none {
            it.uid == entryTarget && it.workspaceId == wsId && it.status.equals("active", true)
        }) throw SecurityException("Entry target is not an active workspace member")
        if (verifiedActorRole == com.example.data.auth.RoleUtils.ROLE_OPERATOR && (linkedExpense != null || linkedExpenses.isNotEmpty())) {
            throw SecurityException("Operators cannot create expenses")
        }
        var customerId = job.customerId
        if (customerId <= 0) {
            if (existingJob != null && existingJob.customerId > 0) {
                customerId = existingJob.customerId
            } else {
                customerId = addOrFindCustomer(job.customerName, job.customerPhone, job.customerLocation)
            }
        }

        if (customerId > 0 && customerDao.getCustomerById(wsId, customerId) == null &&
            jobEntryDao.getJobsForWorkspaceOnce(wsId).none { it.customerId == customerId }) throw SecurityException("Customer reference is outside this workspace")
        if (job.tractorId > 0 && (existingJob == null || existingJob.tractorId != job.tractorId) &&
            tractorDao.getTractorsForWorkspaceOnce(wsId).none { it.id == job.tractorId }) throw SecurityException("Tractor reference is outside this workspace")

        // Determine authorized person attribution
        val isOwner = isOwnerOfWorkspace(wsId)
        val actorUid = getEffectiveActorUid()?.trim().orEmpty()
        val activeActorMember = if (!isOwner) {
            _activeWorkspaceMembers.value.firstOrNull {
                it.uid == actorUid && it.status.equals("active", ignoreCase = true)
            } ?: throw SecurityException("Active workspace membership is required to create an entry")
        } else {
            null
        }
        val appSettings = appSettingsDao.getSettingsForWorkspaceOnce(wsId)
        val currentActorName = _activeWorkspaceMembers.value.firstOrNull { it.uid == actorUid }?.displayName?.takeIf { it.isNotBlank() }
            ?: if (isOwner) appSettings?.ownerName.orEmpty().ifBlank { "Owner" } else appSettings?.activePartnerName.orEmpty().ifBlank { "Member" }

        if (!isOwner && actorUid.isBlank()) {
            throw SecurityException("Authenticated UID is required to create an entry")
        }
        if (existingJob != null) {
            requireRecordMembersActive(existingJob.createdByUid, existingJob.entryForUid)
            if (!isOwner && existingJob.createdByUid.isNotBlank() && existingJob.createdByUid != actorUid) {
                Log.w(TAG, "Security rejection: Non-owner ($currentActorName, uid=$actorUid) cannot edit another person's entry (${existingJob.createdByUid})")
                throw SecurityException("Unauthorized: You can only edit your own entries")
            }
            val currentMember = workspaceMembers.value.firstOrNull { it.uid == actorUid }
            val actorRole = requireWorkspaceActor(wsId)
            if (!com.example.data.auth.AuthorizationManager.canEditEntry(existingJob, isOwner, currentActorName, actorUid, role = actorRole, currentTimeMillis = operationTime)) {
                Log.w(TAG, "Security rejection: User ($currentActorName, isOwner=$isOwner, uid=$actorUid, role=$actorRole) is not authorized to edit entry ${existingJob.id}")
                throw SecurityException("Unauthorized to edit entry ${existingJob.id}")
            }
        } else {
            val currentMember = workspaceMembers.value.firstOrNull { it.uid == actorUid }
            val actorRole = requireWorkspaceActor(wsId)
            if (!isOwner && job.createdByUid.isNotBlank() && job.createdByUid != actorUid) {
                Log.w(TAG, "Security rejection: Non-owner ($currentActorName, uid=$actorUid) cannot create entry for another person (${job.createdByUid})")
                throw SecurityException("Unauthorized: Non-owner cannot create an entry for another person")
            }
            val isHistorical = job.isHistorical
            if (isHistorical && !com.example.data.auth.AuthorizationManager.canCreateOldEntry(isOwner, role = actorRole)) {
                Log.w(TAG, "Security rejection: User ($currentActorName, isOwner=$isOwner, uid=$actorUid, role=$actorRole) is not authorized to create old/historical entry")
                throw SecurityException("Unauthorized: Old/historical entry creation is restricted to the workspace Owner")
            }
        }

        // Generate globally unique collision-resistant ID before insert if new record
        val safeJobId = if (job.id > 0) job.id else IdGenerator.generateId()
        Log.d("TRAC_ENTRY", "local record saved id=$safeJobId isSynced=false jobTitle=${job.workType} wsId=$wsId person=$currentActorName isOwner=$isOwner")

        // CRITICAL EDIT RULES:
        // - Owner can edit any entry, including partner entries.
        // - Preserve the original entry creation date/time exactly.
        // - Editing must never overwrite or reset the original entry timestamp or author.
        val finalCreatedAt = existingJob?.createdAt ?: if (job.createdAt > 0) job.createdAt else operationTime
        val finalAddedBy = if (!isOwner && existingJob == null) {
            currentActorName
        } else if (existingJob != null) {
            existingJob.addedByPartner.ifBlank { job.addedByPartner }
        } else {
            currentActorName
        }
        val finalCreatedByUid = if (existingJob != null) {
            existingJob.createdByUid.ifBlank { job.createdByUid }
        } else {
            actorUid
        }
        val currentMember = workspaceMembers.value.firstOrNull { it.uid == actorUid }
        val resolvedDefaultRole = if (isOwner) "Owner" else (currentMember?.role?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() } ?: "Partner")
        val candidateRole = if (existingJob != null) {
            existingJob.createdByRole.ifBlank { job.createdByRole }.ifBlank { resolvedDefaultRole }
        } else if (!isOwner) {
            activeActorMember?.role?.ifBlank { null }?.let { com.example.data.auth.RoleUtils.normalizeRole(it) }
                ?: resolvedDefaultRole
        } else {
            verifiedActorRole
        }
        val finalCreatedByRole = if (!isOwner && com.example.data.auth.RoleUtils.isOwner(candidateRole)) "Partner" else candidateRole
        val finalOperator = if (existingJob == null) {
            _activeWorkspaceMembers.value.firstOrNull { it.uid == entryTarget && it.workspaceId == wsId }
                ?.displayName.orEmpty().ifBlank { job.operatorName.ifBlank { currentActorName } }
        } else if (verifiedActorRole == com.example.data.auth.RoleUtils.ROLE_OPERATOR) {
            currentActorName
        } else if (job.operatorName.isNotBlank()) {
            job.operatorName
        } else if (existingJob != null && existingJob.operatorName.isNotBlank()) {
            existingJob.operatorName
        } else {
            currentActorName
        }

        val finalCustomerName = if (job.customerName.isNotBlank()) {
            job.customerName
        } else if (existingJob != null && existingJob.customerName.isNotBlank()) {
            existingJob.customerName
        } else {
            job.customerName
        }

        val isHistorical = job.isHistorical

        val finalCustomerPhone = com.example.ui.util.requireOptionalContactPhone(job.customerPhone)

        // Edited by attribution (Recorded when an existing entry is edited)
        val finalEditedByUid = if (existingJob != null) actorUid else ""
        val finalEditedByName = if (existingJob != null) currentActorName else ""
        val finalEditedByRole = if (existingJob != null) resolvedDefaultRole else ""
        val finalUpdatedAt = if (existingJob != null) System.currentTimeMillis() else 0L

        val identitySafeJob = if (!isOwner && existingJob == null) {
            enforceEntryIdentity(
                job = job,
                actorUid = actorUid,
                actorRole = activeActorMember?.role ?: "",
                actorDisplayName = currentActorName
            )
        } else {
            job
        }

        val localJob = identitySafeJob.copy(
            id = safeJobId,
            workspaceId = wsId,
            customerId = customerId,
            customerName = finalCustomerName,
            customerPhone = finalCustomerPhone,
            operatorName = finalOperator,
            addedByPartner = finalAddedBy,
            createdByUid = finalCreatedByUid,
            createdByRole = finalCreatedByRole,
            editedByUid = finalEditedByUid,
            editedByName = finalEditedByName,
            editedByRole = finalEditedByRole,
            updatedAt = finalUpdatedAt,
            isSynced = false,
            createdAt = finalCreatedAt,
            isHistorical = existingJob?.isHistorical ?: job.isHistorical,
            entryForUid = existingJob?.entryForUid ?: identitySafeJob.entryForUid.ifBlank { actorUid },
            financialAttributionUid = existingJob?.financialAttributionUid ?: identitySafeJob.entryForUid.ifBlank { actorUid },
            startTimeMillis = effectiveEntryStartTimeForEdit(existingJob, job)
        )
        jobEntryDao.insertJob(localJob)
        com.example.data.auth.EntryFinancialMutationPolicy.requireAuthorized(verifiedActorRole, existingJob?.amountReceived, localJob.amountReceived)
        val entrySnapshot = if (existingJob == null && localJob.amountReceived > 0) localJob.copy(amountReceived = 0.0, pendingAmount = localJob.totalAmount) else localJob
        val initialJobOperationId = java.util.UUID.randomUUID().toString()
        enqueue("JOB", localJob.id, entrySnapshot.toFirestoreMap(), "UPSERT", actionTime = operationTime, expectedActorUid = operationActorUid, baseSnapshot = existingJob?.toFirestoreMap(), operationId = initialJobOperationId)
        if (existingJob == null && localJob.amountReceived > 0) {
            val initialCollector = com.example.data.auth.CollectionPolicy.requireInitialCollector(wsId, localJob.entryForUid, _activeWorkspaceMembers.value)
            val collection = PaymentEntity(id = IdGenerator.generateId(), workspaceId = wsId, jobEntryId = localJob.id,
                customerId = customerId, customerName = localJob.customerName, tractorId = localJob.tractorId, tractorLabel = localJob.tractorLabel,
                amount = localJob.amountReceived, collectedByUid = actorUid, collectedByName = currentActorName,
                collectorUid = initialCollector.uid,
                collectorName = initialCollector.displayName.orEmpty(),
                collectorRole = initialCollector.role,
                collectedByRole = verifiedActorRole, collectedAt = operationTime, createdAt = operationTime, isSynced = false,
                allocationsJson = org.json.JSONArray().put(org.json.JSONObject().put("jobId", localJob.id).put("amount", localJob.amountReceived)).toString())
            paymentDao.insertPayment(collection)
            enqueue("PAYMENT", collection.id, collection.toFirestoreMap() + mapOf("entrySnapshotJson" to org.json.JSONObject(localJob.toFirestoreMap()).toString(),
                "baseAmountReceived" to 0.0, "basePendingAmount" to localJob.totalAmount,
                "initialEntryCollection" to true, "initialJobOperationId" to initialJobOperationId), expectedActorUid = operationActorUid, actionTime = operationTime)
        }

        val allExpensesToSave = mutableListOf<ExpenseEntity>()
        if (linkedExpenses.isNotEmpty()) {
            allExpensesToSave.addAll(linkedExpenses.filter { it.amount > 0 })
        } else if (linkedExpense != null && linkedExpense.amount > 0) {
            allExpensesToSave.add(linkedExpense)
        }
        val distinctExpenses = allExpensesToSave.distinctBy { if (it.id > 0) it.id else "${it.expenseType}_${it.amount}_${it.paidBy}" }

        // Reconcile: delete any existing linked expenses for this job that were removed in the edit form
        if (verifiedActorRole == com.example.data.auth.RoleUtils.ROLE_OPERATOR && distinctExpenses.isNotEmpty()) throw SecurityException("Operators cannot mutate Expenses")
        val existingLinkedExpenses = if (safeJobId > 0 && verifiedActorRole != com.example.data.auth.RoleUtils.ROLE_OPERATOR) {
            try { expenseDao.getExpensesForJob(_activeWorkspaceId.value.orEmpty(), safeJobId) } catch (e: Exception) { emptyList() }
        } else emptyList()
        val newExpenseIds = distinctExpenses.map { it.id }.filter { it > 0 }.toSet()
        val deletedLinkedExpenses = existingLinkedExpenses.filter { it.id !in newExpenseIds }

        for (delExp in deletedLinkedExpenses) {
            if (delExp.workspaceId != wsId || !com.example.data.auth.AuthorizationManager.canDeleteExpense(delExp, isOwner, currentUid = actorUid, role = verifiedActorRole))
                throw SecurityException("A linked expense cannot be deleted by this member")
            requireRecordMembersActive(delExp.createdByUid, delExp.expenseForUid)
            expenseDao.deleteExpense(delExp)
            enqueue("EXPENSE", delExp.id, delExp.toFirestoreMap(), "DELETE", expectedActorUid = operationActorUid, actionTime = operationTime)
            if (wsId.isNotBlank()) {
                pendingDeleteManager.recordPendingDelete("EXPENSE", delExp.id, wsId)
            }
        }

        val savedExpensesList = mutableListOf<ExpenseEntity>()
        for (exp in distinctExpenses) {
            val originalExpense = existingLinkedExpenses.firstOrNull { it.id == exp.id }
            if (originalExpense != null) {
                if (originalExpense.workspaceId != wsId || !com.example.data.auth.AuthorizationManager.canEditExpense(originalExpense, isOwner, currentUid = actorUid, role = verifiedActorRole))
                    throw SecurityException("A linked expense cannot be edited by this member")
                requireRecordMembersActive(originalExpense.createdByUid, originalExpense.expenseForUid)
            }
            val safeExpId = if (exp.id > 0) exp.id else IdGenerator.generateId()
            // Personal funding refers to the authorized selected Entry member, not its creator.
            // Existing Expenses retain their recorded payer when edited.
            val effectivePaidByUid = com.example.ui.viewmodel.linkedExpensePayerUid(
                exp.paidBy, originalExpense?.paidByUid.orEmpty(), entryTarget)
            val effectivePartner = if (exp.paidBy == "I Paid") {
                originalExpense?.paidByPartner?.takeIf { it.isNotBlank() }
                    ?: _activeWorkspaceMembers.value.firstOrNull { it.uid == effectivePaidByUid }?.displayName
                    ?: finalOperator
            } else ""
            val localExp = exp.copy(
                id = safeExpId,
                workspaceId = wsId,
                operatorName = finalOperator,
                addedByPartner = finalAddedBy,
                paidBy = exp.paidBy,
                paidByPartner = effectivePartner,
                paidByUid = effectivePaidByUid,
                createdByUid = existingLinkedExpenses.firstOrNull { it.id == safeExpId }?.createdByUid ?: actorUid,
                createdByRole = existingLinkedExpenses.firstOrNull { it.id == safeExpId }?.createdByRole ?: verifiedActorRole,
                expenseForUid = originalExpense?.expenseForUid ?: entryTarget,
                financialAttributionUid = originalExpense?.financialAttributionUid ?: entryTarget,
                createdAt = originalExpense?.createdAt ?: operationTime,
                relatedJobId = safeJobId,
                isSynced = false
            )
            expenseDao.insertExpense(localExp)
            enqueue("EXPENSE", localExp.id, localExp.toFirestoreMap(), "UPSERT", expectedActorUid = operationActorUid, actionTime = operationTime, baseSnapshot = originalExpense?.toFirestoreMap())
            savedExpensesList.add(localExp)
        }

        recalculateCustomerStats(customerId)
        if (existingJob != null && existingJob.customerId != customerId) {
            recalculateCustomerStats(existingJob.customerId, wsId, clearDeletedSourceBalance = true)
        }

        // Push to Cloud Workspace asynchronously so local write returns immediately
        Log.d("TRAC_WORKSPACE", "workspaceId=$wsId isCloudReady=$isReady")


        return@withTransaction safeJobId

        }
        if (isCloudReady()) scope.launch { pushUnsyncedToCloud() }
        return result
    }

    /**
     * Resolves the effective role of the given actor in the given workspace.
     *
     * ROLE RESOLUTION IS MEMBERSHIP-DRIVEN ONLY. Ownership of workspaces/{workspaceId}
     * (ownerUid) is deliberately NOT consulted, because a Partner and an Owner share the
     * same workspaceId and only the membership record distinguishes them.
     *
     * Precedence:
     *  1. workspaces/{workspaceId}/members/{actorUid}.role (authoritative, populated at init)
     *  2. the resolved session role for this workspace
     *  3. Partner (safe default — never grants Owner)
     */
    fun resolveRoleForWorkspace(wsId: String, actorUid: String? = null): String {
        val uid = (actorUid ?: getEffectiveActorUid()).orEmpty()
        if (uid.isBlank() || wsId.isBlank() || wsId != _activeWorkspaceId.value) return ""
        val member = _activeWorkspaceMembers.value.firstOrNull {
            it.uid == uid && it.workspaceId == wsId && it.status.equals("active", true)
        } ?: return ""
        return com.example.data.auth.RoleUtils.normalizeRole(member.role)
    }

    /**
     * OWNER test. This is a ROLE test, not a workspace-ownership test.
     *
     * A Partner and an Owner resolve to the SAME workspaceId; the membership role alone
     * decides. There is deliberately no `workspaceId == ws_$uid` or `ownerUid == uid`
     * shortcut, because that inference is what misclassified Partners as Owners.
     */
    fun isOwnerOfWorkspace(wsId: String): Boolean {
        return resolveRoleForWorkspace(wsId) == com.example.data.auth.RoleUtils.ROLE_OWNER
    }

    suspend fun deleteJob(job: JobEntryEntity) {
        val operationActorUid = getEffectiveActorUid().orEmpty()
        val operationTime = System.currentTimeMillis()
        val wsId = _activeWorkspaceId.value.orEmpty()
        val actorUid = getEffectiveActorUid().orEmpty()
        require(wsId.isNotBlank() && job.workspaceId == wsId) { "Entry workspace mismatch" }
        // Older Room rows may lack identities already present on the same cloud record.
        // Refresh only matching business content with no retained local action.
        val before = jobEntryDao.getJobById(wsId, job.id)
        if (before != null && (before.createdByUid.isBlank() || before.entryForUid.isBlank())) {
            val remote = firestoreRepository.getEntryFromServer(wsId, job.id)
            if (remote != null) database.withTransaction {
                require(getEffectiveActorUid() == actorUid && _activeWorkspaceId.value == wsId) { "Workspace or account changed" }
                val current = jobEntryDao.getJobById(wsId, job.id)
                val outstanding = operations.outstandingOperations(wsId).any { it.entityType == "JOB" && it.entityId == job.id }
                if (current != null && com.example.data.sync.ConfirmedReplica.job(current, remote, outstanding))
                    jobEntryDao.insertJob(remote.copy(isSynced = true))
            }
        }
        database.withTransaction {
            val role = requireWorkspaceActor(wsId)
            val stored = jobEntryDao.getJobById(wsId, job.id)
            if (stored == null) {
                require(operations.outstandingOperations(wsId).any {
                    it.actorUid == actorUid && it.entityType == "JOB" && it.entityId == job.id && it.mutationType == "DELETE"
                }) { "Entry is no longer available; refresh before retrying" }
                return@withTransaction
            }
            requireRecordMembersActive(stored.createdByUid, stored.entryForUid)
            val owner = isOwnerOfWorkspace(wsId)
            require(com.example.data.auth.AuthorizationManager.canDeleteEntry(stored, owner, currentActorName = "", currentUid = actorUid, role = role, currentTimeMillis = operationTime)) {
                "You cannot delete this Entry: check ownership, role and the 24-hour deadline"
            }
            val payments = paymentDao.getPaymentsForWorkspace(wsId).firstOrNull().orEmpty()
            val linked = payments.filter { com.example.data.sync.EntryDeletionPolicy.references(it, stored.id) }
            val collectionDeletes = com.example.data.sync.EntryDeletionPolicy.plannedCollectionDeletes(
                stored, linked, owner, actorUid, role, operationTime)
            val expenses = expenseDao.getExpensesForJob(wsId, stored.id)
            com.example.data.sync.EntryDeletionPolicy.requireNoLinkedExpenses(stored, expenses, owner, actorUid, role, operationTime)
            val dependencies = operations.outstandingOperations(wsId).filter {
                (it.entityType == "PAYMENT" && com.example.data.sync.OperationDependencies.references(it).contains("JOB" to stored.id)) ||
                    (it.entityType == "EXPENSE" && org.json.JSONObject(it.payloadJson).optLong("relatedJobId") == stored.id)
            }
            // Pending children upload in dependency order before this compound command.
            // Rejected financial writes still need explicit resolution and are never retried here.
            val supersededExpenseDeletes = dependencies.filter { it.state == "REJECTED" &&
                it.entityType == "EXPENSE" && it.mutationType == "DELETE" && expenses.any { expense -> expense.id == it.entityId }
            }
            require(dependencies.none { it.state == "REJECTED" && it !in supersededExpenseDeletes }) {
                "Deletion is protected: resolve the rejected linked Payment or Expense in Account > SQLite & Cloud Sync first. Original actions are retained."
            }
            require(_activeWorkspaceId.value == wsId && getEffectiveActorUid() == actorUid) { "Workspace or account changed; retry in the original workspace" }
            val payload = stored.toFirestoreMap().toMutableMap()
            payload["linkedExpenseIds"] = org.json.JSONArray().apply {
                expenses.forEach { put(it.id.toString()) }
            }
            payload["linkedExpenseSnapshots"] = org.json.JSONArray().apply {
                expenses.forEach { put(org.json.JSONObject(it.toFirestoreMap())) }
            }
            payload["linkedCollectionIds"] = org.json.JSONArray().apply {
                collectionDeletes.forEach { put(it.id.toString()) }
            }
            payload["linkedCollectionSnapshots"] = org.json.JSONArray().apply {
                collectionDeletes.forEach { put(org.json.JSONObject(it.toFirestoreMap())) }
            }
            payload["deleteDependencies"] = dependencies.filterNot { it in supersededExpenseDeletes }
                .map { mapOf("type" to it.entityType, "id" to it.entityId) }
            jobEntryDao.deleteJob(stored)
            expenses.forEach { expenseDao.deleteExpense(it) }
            collectionDeletes.forEach { payment ->
                val reversed = payment.copy(
                    status = "REVERSED",
                    reversedByUid = actorUid,
                    reversedAt = operationTime,
                    isSynced = false
                )
                paymentDao.insertPayment(reversed)
            }
            operations.invalidateRejectedEntryDelete(wsId, actorUid, stored.id)
            enqueue("JOB", stored.id, payload, "DELETE", expectedActorUid = operationActorUid, actionTime = operationTime)
            recalculateCustomerStats(stored.customerId, wsId, clearDeletedSourceBalance = true)
        }
        pendingDeleteManager.recordPendingDelete("JOB", job.id, wsId)
        val queued = operations.outstandingOperations(wsId).firstOrNull {
            it.entityType == "JOB" && it.entityId == job.id && it.mutationType == "DELETE" && it.state == "PENDING"
        }
        queued?.let { op ->
            val ids = org.json.JSONObject(op.payloadJson).optJSONArray("linkedExpenseIds")
            if (ids != null) for (index in 0 until ids.length())
                pendingDeleteManager.recordPendingDelete("EXPENSE", ids.getLong(index), wsId)
        }
        if (isCloudReady()) scope.launch { pushUnsyncedToCloud(wsId) }
    }

    suspend fun getExpensesForJob(jobId: Long): List<ExpenseEntity> {
        val wsId = _activeWorkspaceId.value.orEmpty()
        val role = requireWorkspaceActor(wsId)
        if (role == com.example.data.auth.RoleUtils.ROLE_OPERATOR) return emptyList()
        return expenseDao.getExpensesForJob(wsId, jobId)
    }

    suspend fun addExpense(expense: ExpenseEntity): Long {
        val operationActorUid = getEffectiveActorUid().orEmpty()
        val operationTime = System.currentTimeMillis()
        val result = database.withTransaction {

        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: ""
        val safeExpId = if (expense.id > 0) expense.id else IdGenerator.generateId()
        val actorUid = getEffectiveActorUid() ?: ""
        val isOwner = isOwnerOfWorkspace(wsId)
        val appSettings = appSettingsDao.getSettingsForWorkspaceOnce(wsId)
        val currentActorName = if (isOwner) {
            appSettings?.ownerName?.ifBlank { null } ?: appSettings?.activePartnerName?.ifBlank { null } ?: "Owner"
        } else {
            appSettings?.activePartnerName?.ifBlank { null } ?: "Partner"
        }
        val currentMember = workspaceMembers.value.firstOrNull { it.uid == actorUid }
        val actorRole = requireWorkspaceActor(wsId)

        if (actorRole != com.example.data.auth.RoleUtils.ROLE_OWNER && actorRole != com.example.data.auth.RoleUtils.ROLE_PARTNER) throw SecurityException("Expenses are unavailable for this role")
        if (expense.workspaceId.isNotBlank() && expense.workspaceId != wsId) throw SecurityException("Expense workspace mismatch")
        val targetUid = if (isOwner) expense.expenseForUid.ifBlank { actorUid } else actorUid
        if (_activeWorkspaceMembers.value.none { it.uid == targetUid && it.workspaceId == wsId && it.status.equals("active", true) }) throw SecurityException("Expense target is not active")

        // Requirement 2: PARTNER EXPENSE OWNERSHIP
        // When logged in as a Partner, the Partner must not be able to create an Expense on behalf of another Partner.
        // Partner expense creation must be for the current Partner only.
        val isBusinessPaid = expense.paidBy.equals("Business Paid", ignoreCase = true)
        val finalExpense = if (!isOwner) {
            expense.copy(
                id = safeExpId,
                workspaceId = wsId,
                operatorName = currentActorName,
                addedByPartner = currentActorName,
                paidBy = if (isBusinessPaid) "Business Paid" else "I Paid",
                paidByPartner = if (!isBusinessPaid) currentActorName else "",
                paidByUid = if (!isBusinessPaid) actorUid else "",
                createdByUid = actorUid,
                createdByRole = actorRole,
                isSynced = false
            )
        } else {
            val finalCreatedByUid = actorUid
            val finalCreatedByRole = actorRole
            expense.copy(
                id = safeExpId,
                workspaceId = wsId,
                paidBy = if (isBusinessPaid) "Business Paid" else "I Paid",
                paidByPartner = if (!isBusinessPaid) expense.paidByPartner else "",
                paidByUid = if (!isBusinessPaid) expense.paidByUid else "",
                createdByUid = finalCreatedByUid,
                createdByRole = finalCreatedByRole,
                isSynced = false
            )
        }
        val attributedExpense = finalExpense.copy(expenseForUid = targetUid, financialAttributionUid = targetUid)
        expenseDao.insertExpense(attributedExpense)
        enqueue("EXPENSE", attributedExpense.id, attributedExpense.toFirestoreMap(), expectedActorUid = operationActorUid, actionTime = operationTime)


        return@withTransaction safeExpId

        }
        if (isCloudReady()) scope.launch { pushUnsyncedToCloud() }
        return result
    }

    suspend fun updateExpense(expense: ExpenseEntity) {
        val operationActorUid = getEffectiveActorUid().orEmpty()
        val operationTime = System.currentTimeMillis()
        val result = database.withTransaction {

        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: expense.workspaceId
        val actorUid = getEffectiveActorUid() ?: ""
        val isOwner = isOwnerOfWorkspace(wsId)
        val appSettings = appSettingsDao.getSettingsForWorkspaceOnce(wsId)
        val currentActorName = if (isOwner) {
            appSettings?.ownerName?.ifBlank { null } ?: appSettings?.activePartnerName?.ifBlank { null } ?: "Owner"
        } else {
            appSettings?.activePartnerName?.ifBlank { null } ?: "Partner"
        }
        val currentMember = workspaceMembers.value.firstOrNull { it.uid == actorUid }
        val actorRole = requireWorkspaceActor(wsId)

        val original = expenseDao.getExpenseById(_activeWorkspaceId.value.orEmpty(), expense.id)
        val expenseToCheck = original ?: throw SecurityException("Expense no longer exists")
        requireRecordMembersActive(expenseToCheck.createdByUid, expenseToCheck.expenseForUid)
        if (expenseToCheck.workspaceId != wsId || expense.workspaceId != wsId) throw SecurityException("Expense workspace mismatch")

        if (!com.example.data.auth.AuthorizationManager.canEditExpense(expenseToCheck, isOwner, currentActorName, actorUid, role = actorRole, currentTimeMillis = operationTime)) {
            Log.w(TAG, "Security rejection: User ($currentActorName, isOwner=$isOwner, uid=$actorUid, role=$actorRole) is not authorized to edit expense ${expense.id}")
            throw SecurityException("Unauthorized to edit expense ${expense.id}")
        }

        val isUpdateBusinessPaid = expense.paidBy.equals("Business Paid", ignoreCase = true)
        val updated = expense.copy(
            workspaceId = wsId,
            paidBy = if (isUpdateBusinessPaid) "Business Paid" else "I Paid",
            paidByPartner = if (!isUpdateBusinessPaid) expense.paidByPartner else "",
            paidByUid = if (!isUpdateBusinessPaid) expense.paidByUid else "",
            expenseForUid = expenseToCheck.expenseForUid,
            financialAttributionUid = expenseToCheck.financialAttributionUid,
            relatedJobId = expenseToCheck.relatedJobId,
            createdAt = original?.createdAt ?: expense.createdAt,
            createdByUid = if (original != null && original.createdByUid.isNotBlank()) original.createdByUid else (expense.createdByUid.ifBlank { actorUid }),
            createdByRole = if (original != null && original.createdByRole.isNotBlank()) original.createdByRole else (expense.createdByRole.ifBlank { actorRole }),
            isSynced = false
        )
        expenseDao.updateExpense(updated)
        enqueue("EXPENSE", updated.id, updated.toFirestoreMap(), "UPSERT", actionTime = operationTime, expectedActorUid = operationActorUid, baseSnapshot = original?.toFirestoreMap())



        }
        if (isCloudReady()) scope.launch { pushUnsyncedToCloud() }
        return result
    }

    suspend fun deleteExpense(expenseInput: ExpenseEntity) {
        val operationActorUid = getEffectiveActorUid().orEmpty()
        val operationTime = System.currentTimeMillis()
        val result = database.withTransaction {
        val expense = expenseDao.getExpenseById(_activeWorkspaceId.value.orEmpty(), expenseInput.id) ?: throw SecurityException("Record no longer exists")
        requireRecordMembersActive(expense.createdByUid, expense.expenseForUid)
        if (expense.workspaceId != _activeWorkspaceId.value) throw SecurityException("Record is outside the active workspace")


        val wsId = expense.workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        val actorUid = getEffectiveActorUid() ?: ""
        val isOwner = isOwnerOfWorkspace(wsId)
        val appSettings = appSettingsDao.getSettingsForWorkspaceOnce(wsId)
        val currentActorName = if (isOwner) {
            appSettings?.ownerName?.ifBlank { null } ?: appSettings?.activePartnerName?.ifBlank { null } ?: "Owner"
        } else {
            appSettings?.activePartnerName?.ifBlank { null } ?: "Partner"
        }
        val currentMember = workspaceMembers.value.firstOrNull { it.uid == actorUid }
        val actorRole = requireWorkspaceActor(wsId)

        if (!com.example.data.auth.AuthorizationManager.canDeleteExpense(expense, isOwner, currentActorName, actorUid, role = actorRole, currentTimeMillis = operationTime)) {
            Log.w(TAG, "Security rejection: User ($currentActorName, isOwner=$isOwner, uid=$actorUid, role=$actorRole) is not authorized to delete expense ${expense.id}")
            throw SecurityException("Unauthorized to delete expense ${expense.id}")
        }

        expenseDao.deleteExpense(expense)
        enqueue("EXPENSE", expense.id, expense.toFirestoreMap(), "DELETE", actionTime = operationTime, expectedActorUid = operationActorUid)
        if (wsId.isNotBlank()) {
            pendingDeleteManager.recordPendingDelete("EXPENSE", expense.id, wsId)
        }

        val isReady = isCloudReady()


        }
        if (isCloudReady()) scope.launch { pushUnsyncedToCloud() }
        return result
    }

    /**
     * Calculates the available business balance using cash-basis accounting.
     * Formula: Total Received (collected payments) - Total Expenses - Total Withdrawals
     * This represents the actual cash available in the business, NOT the accrual-based
     * profit which would include unpaid invoices (totalRecorded - totalReceived).
     *
     * NOTE: This is a cash-basis calculation. If accrual-basis balance is needed
     * (including pending receivables), a separate method should be implemented.
     */
    suspend fun getAvailableBalance(): Double {
        val wsId = getOrResolveWorkspaceId() ?: return 0.0
        return com.example.ui.util.FinancialCalculationEngine.cashAvailable(
            paymentDao.getPaymentsForWorkspace(wsId).firstOrNull().orEmpty(),
            expenseDao.getExpensesForWorkspaceOnce(wsId),
            withdrawalDao.getWithdrawalsForWorkspaceOnce(wsId),
            com.example.ui.util.FinancialScope(workspaceId = wsId),
            jobEntryDao.getJobsForWorkspaceOnce(wsId)
        )
    }

    suspend fun addWithdrawal(withdrawal: WithdrawalEntity): Long {
        val operationActorUid = getEffectiveActorUid().orEmpty()
        val operationTime = System.currentTimeMillis()
        val result = database.withTransaction {

        val actorUid = getEffectiveActorUid().orEmpty()
        val wsId = getOrResolveWorkspaceId() ?: withdrawal.workspaceId
        val isOwner = isOwnerOfWorkspace(wsId)
        val members = _activeWorkspaceMembers.value
        val currentMember = members.firstOrNull { it.uid == actorUid }
        val actorRole = requireWorkspaceActor(wsId)
        val targetUid = if (isOwner) withdrawal.targetPartnerUid.ifBlank { actorUid } else actorUid
        // STRICT DEFENSE-IN-DEPTH: Reject unauthorized callers BEFORE Room insertion
        if (!com.example.data.auth.AuthorizationManager.canCreateWithdrawal(
                actorUid = actorUid,
                actorRole = actorRole,
                targetUid = targetUid,
                workspaceId = wsId,
                workspaceMembers = members
            )
        ) {
            Log.w(TAG, "Security rejection: Actor (uid=$actorUid, role=$actorRole) is not authorized to create withdrawal for target (uid=$targetUid, name=${withdrawal.partnerName})")
            throw SecurityException("Unauthorized withdrawal attempt for member: ${withdrawal.partnerName}")
        }

        if (withdrawal.amount <= 0) {
            throw IllegalArgumentException("Withdrawal amount must be greater than ₹0")
        }
        val currentAvailable = getAvailableBalance()
        if (withdrawal.amount > currentAvailable) {
            throw IllegalStateException("Insufficient available balance. Available: ₹$currentAvailable")
        }
        val isReady = isCloudReady()
        val safeWithId = if (withdrawal.id > 0) withdrawal.id else IdGenerator.generateId()
        val saved = withdrawal.copy(
            id = safeWithId,
            workspaceId = wsId,
            isSynced = false,
            createdByUid = actorUid,
            createdByRole = actorRole,
            targetPartnerUid = targetUid
        )
        withdrawalDao.insertWithdrawal(saved)
        enqueue("WITHDRAWAL", saved.id, saved.toFirestoreMap(), "UPSERT", expectedActorUid = operationActorUid, actionTime = operationTime)


        return@withTransaction safeWithId

        }
        if (isCloudReady()) scope.launch { pushUnsyncedToCloud() }
        return result
    }

    suspend fun updateWithdrawal(withdrawal: WithdrawalEntity) {
        throw SecurityException("Withdrawals cannot be edited; delete and recreate the record")
    }

    suspend fun deleteWithdrawal(withdrawalInput: WithdrawalEntity) {
        val operationActorUid = getEffectiveActorUid().orEmpty()
        val operationTime = System.currentTimeMillis()
        val result = database.withTransaction {
        val withdrawal = withdrawalDao.getWithdrawalById(_activeWorkspaceId.value.orEmpty(), withdrawalInput.id) ?: throw SecurityException("Record no longer exists")
        requireRecordMembersActive(withdrawal.createdByUid, withdrawal.targetPartnerUid)
        if (withdrawal.workspaceId != _activeWorkspaceId.value) throw SecurityException("Record is outside the active workspace")


        val wsId = withdrawal.workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        val actorUid = getEffectiveActorUid() ?: ""
        val isOwner = isOwnerOfWorkspace(wsId)
        val appSettings = appSettingsDao.getSettingsForWorkspaceOnce(wsId)
        val currentActorName = if (isOwner) {
            appSettings?.ownerName?.ifBlank { null } ?: appSettings?.activePartnerName?.ifBlank { null } ?: "Owner"
        } else {
            appSettings?.activePartnerName?.ifBlank { null } ?: "Partner"
        }
        val currentMember = workspaceMembers.value.firstOrNull { it.uid == actorUid }
        val actorRole = requireWorkspaceActor(wsId)

        if (!com.example.data.auth.AuthorizationManager.canDeleteWithdrawal(withdrawal, isOwner, currentActorName, actorUid, role = actorRole, currentTimeMillis = operationTime)) {
            Log.w(TAG, "Security rejection: User ($currentActorName, isOwner=$isOwner, uid=$actorUid, role=$actorRole) is not authorized to delete withdrawal ${withdrawal.id}")
            throw SecurityException("Unauthorized to delete withdrawal ${withdrawal.id}")
        }

        withdrawalDao.deleteWithdrawal(withdrawal)
        enqueue("WITHDRAWAL", withdrawal.id, withdrawal.toFirestoreMap(), "DELETE", actionTime = operationTime, expectedActorUid = operationActorUid)
        if (wsId.isNotBlank()) {
            pendingDeleteManager.recordPendingDelete("WITHDRAWAL", withdrawal.id, wsId)
        }

        val isReady = isCloudReady()


        }
        if (isCloudReady()) scope.launch { pushUnsyncedToCloud() }
        return result
    }

    suspend fun updateCustomer(customer: CustomerEntity) {
        val operationActorUid = getEffectiveActorUid().orEmpty()
        val operationTime = System.currentTimeMillis()
        database.withTransaction {
            val wsId = _activeWorkspaceId.value.orEmpty()
            val role = requireWorkspaceActor(wsId)
            if (!com.example.data.auth.AuthorizationManager.canManageCustomers(isOwnerOfWorkspace(wsId), role)) throw SecurityException("Customer edits are forbidden")
            val original = customerDao.getCustomerById(_activeWorkspaceId.value.orEmpty(), customer.id) ?: throw SecurityException("Customer no longer exists")
            if (original.workspaceId != wsId || customer.workspaceId != wsId) throw SecurityException("Customer workspace mismatch")
            val updated = customer.copy(createdByUid = original.createdByUid, createdAt = original.createdAt,
                phone = com.example.ui.util.requireOptionalContactPhone(customer.phone), isSynced = false,
                totalBilled = original.totalBilled, totalPaid = original.totalPaid, balanceDue = original.balanceDue)
            customerDao.updateCustomer(updated)
            enqueue("CUSTOMER", updated.id, updated.toFirestoreMap(), expectedActorUid = operationActorUid, actionTime = operationTime)
        }
        if (isCloudReady()) scope.launch { pushUnsyncedToCloud() }
    }

    suspend fun deleteCustomer(customer: CustomerEntity) {
        val operationActorUid = getEffectiveActorUid().orEmpty()
        val operationTime = System.currentTimeMillis()
        database.withTransaction {
            val wsId = _activeWorkspaceId.value.orEmpty()
            val role = requireWorkspaceActor(wsId)
            if (role != com.example.data.auth.RoleUtils.ROLE_OWNER) throw SecurityException("Only Owner can delete customers")
            val stored = customerDao.getCustomerById(_activeWorkspaceId.value.orEmpty(), customer.id) ?: throw SecurityException("Customer no longer exists")
            if (stored.workspaceId != wsId) throw SecurityException("Customer workspace mismatch")
            require(jobEntryDao.getJobsForWorkspaceOnce(wsId).none { it.customerId == stored.id } &&
                paymentDao.getPaymentsForCustomer(_activeWorkspaceId.value.orEmpty(), stored.id).none { it.workspaceId == wsId }) { "Customer is referenced by retained financial history" }
            customerDao.deleteCustomer(stored)
            enqueue("CUSTOMER", stored.id, stored.toFirestoreMap(), "DELETE", expectedActorUid = operationActorUid, actionTime = operationTime)
        }
        if (isCloudReady()) scope.launch { pushUnsyncedToCloud() }
    }

    suspend fun addOrFindCustomer(name: String, phone: String, location: String): Long {
        val operationActorUid = getEffectiveActorUid().orEmpty()
        val operationTime = System.currentTimeMillis()
        val result = database.withTransaction {

        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: ""
        val actorRole = requireWorkspaceActor(wsId)
        if (actorRole == com.example.data.auth.RoleUtils.ROLE_OPERATOR) {
            val cleanPhone = com.example.ui.util.requireOptionalContactPhone(phone)
            val permitted = jobEntryDao.getJobsForWorkspaceOnce(wsId).filter { !it.isHistorical && it.customerId > 0 &&
                (if (cleanPhone.isNotBlank()) it.customerPhone == cleanPhone else it.customerName.trim() == name.trim() && it.customerLocation.trim() == location.trim()) }
                .map { it.customerId }.distinct()
            if (permitted.size == 1) return@withTransaction permitted.single()
            throw SecurityException("Use a customer from an existing ordinary Entry; only Owner or Partner can create customers")
        }
        val customers = customerDao.getCustomersForWorkspace(wsId).firstOrNull() ?: emptyList()
        val cleanPhone = com.example.ui.util.requireOptionalContactPhone(phone)
        val cleanName = name.trim()
        val cleanLocation = location.trim()

        // Match by phone only. If phone is blank, always create a new customer to avoid
        // merging distinct customers with the same name but no phone.
        val existing = if (cleanPhone.isNotBlank()) {
            customers.find { it.phone == cleanPhone }
        } else {
            null
        }

        val custId: Long
        val customerEntity: CustomerEntity

        if (existing != null) {
            if (actorRole == com.example.data.auth.RoleUtils.ROLE_OPERATOR) return@withTransaction existing.id
            val updated = existing.copy(
                phone = if (cleanPhone.isNotBlank()) cleanPhone else existing.phone,
                location = if (cleanLocation.isNotBlank()) cleanLocation else existing.location,
                updatedAt = operationTime,
                isSynced = false
            )
            customerDao.updateCustomer(updated)
            enqueue("CUSTOMER", updated.id, updated.toFirestoreMap(), "UPSERT", expectedActorUid = operationActorUid, actionTime = operationTime)
            custId = existing.id
            customerEntity = updated
        } else {
            if (actorRole == com.example.data.auth.RoleUtils.ROLE_OPERATOR) throw SecurityException("Only Owner or Partner can create customers")
            val safeCustId = IdGenerator.generateId()
            val newCust = CustomerEntity(
                id = safeCustId,
                createdAt = operationTime,
                createdByUid = getEffectiveActorUid().orEmpty(),
                workspaceId = wsId,
                name = name.trim(),
                phone = cleanPhone,
                location = cleanLocation,
                totalBilled = 0.0,
                totalPaid = 0.0,
                balanceDue = 0.0,
                isSynced = false
            )
            customerDao.insertCustomer(newCust)
            enqueue("CUSTOMER", newCust.id, newCust.toFirestoreMap(), "UPSERT", expectedActorUid = operationActorUid, actionTime = operationTime)
            custId = safeCustId
            customerEntity = newCust
        }



        return@withTransaction custId

        }
        if (isCloudReady()) scope.launch { pushUnsyncedToCloud() }
        return result
    }

    /** The legacy collectedBy input selects the business collector; authenticated identity always comes from Firebase Auth.
     * Caller-supplied name/role are deliberately ignored in favor of current workspace membership.
     */
    suspend fun recordCustomerPayment(
        customer: CustomerEntity,
        amount: Double,
        dateTimestamp: Long,
        paymentMethod: String,
        note: String,
        operatorName: String,
        collectedByUid: String = "",
        collectedByName: String = "",
        collectedByRole: String = ""
    ): Long {
        val operationActorUid = getEffectiveActorUid().orEmpty()
        val operationTime = System.currentTimeMillis()
        val result = database.withTransaction {

        val wsId = getOrResolveWorkspaceId().orEmpty()
        val role = requireWorkspaceActor(wsId)
        val actorUid = getEffectiveActorUid().orEmpty()
        if (!com.example.data.auth.AuthorizationManager.canCollectPayment(isOwnerOfWorkspace(wsId), role)) throw SecurityException("Collections are forbidden for this role")
        val storedCustomer = customerDao.getCustomerById(_activeWorkspaceId.value.orEmpty(), customer.id) ?: throw SecurityException("Customer not found")
        if (storedCustomer.workspaceId != wsId) throw SecurityException("Customer workspace mismatch")
        val value = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(amount)
        require(value > 0 && amount.isFinite()) { "Collection amount must be positive" }
        val actorName = _activeWorkspaceMembers.value.first { it.uid == actorUid && it.workspaceId == wsId }.displayName.orEmpty()
        val collector = com.example.data.auth.CollectionPolicy.requireCollector(wsId, actorUid,
            isOwnerOfWorkspace(wsId), collectedByUid, _activeWorkspaceMembers.value)
        val collections = mutableListOf<PaymentEntity>()
        val updates = mutableListOf<JobEntryEntity>()
        database.withTransaction {
            val jobs = jobEntryDao.getJobsForWorkspaceOnce(wsId).filter { it.customerId == customer.id && it.pendingAmount > 0 }
                .sortedWith(compareBy<JobEntryEntity> { it.startTimeMillis }.thenBy { it.id })
            require(value <= jobs.sumOf { it.pendingAmount }) { "Collection exceeds the outstanding job dues" }
            // Validate the complete split before changing Room or writing any outbox command.
            com.example.data.auth.CollectionPolicy.allocatedJobs(jobs, wsId, customer.id, value).forEach {
                com.example.data.auth.CollectionPolicy.requireDate(dateTimestamp, it.createdAt)
            }
            var remaining = value
            for (job in jobs) {
                if (remaining <= 0) break
                val allocated = minOf(remaining, job.pendingAmount)
                val collection = PaymentEntity(id = IdGenerator.generateId(), workspaceId = wsId,
                    jobEntryId = job.id, customerId = customer.id, customerName = storedCustomer.name,
                    tractorId = job.tractorId, tractorLabel = job.tractorLabel, amount = allocated,
                    paymentMethod = paymentMethod, notes = note, collectedByUid = actorUid,
                    collectedByName = actorName, collectedByRole = role, collectedAt = dateTimestamp, createdAt = operationTime,
                    collectorUid = collector.uid, collectorName = collector.displayName.orEmpty(), collectorRole = collector.role,
                    isSynced = false, status = "ACTIVE",
                    allocationsJson = org.json.JSONArray().put(org.json.JSONObject().put("jobId", job.id).put("amount", allocated)).toString())
                val updated = job.copy(amountReceived = job.amountReceived + allocated,
                    pendingAmount = maxOf(0.0, job.pendingAmount - allocated), isSynced = false)
                jobEntryDao.insertJob(updated)
                paymentDao.insertPayment(collection)
                enqueue("PAYMENT", collection.id, collection.toFirestoreMap() + mapOf(
                    "entrySnapshotJson" to org.json.JSONObject(updated.toFirestoreMap()).toString(),
                    "baseAmountReceived" to job.amountReceived, "basePendingAmount" to job.pendingAmount), expectedActorUid = operationActorUid, actionTime = operationTime)
                collections.add(collection); updates.add(updated)
                remaining -= allocated
            }
            recalculateCustomerStats(customer.id)
        }

        return@withTransaction collections.first().id

        }
        if (isCloudReady()) scope.launch { pushUnsyncedToCloud() }
        return result
    }

    private fun <T> authorizedRecords(workspaceId: String, financial: Boolean = false, records: Flow<List<T>>): Flow<List<T>> =
        kotlinx.coroutines.flow.combine(records, _verifiedWorkspaceContext, _activeWorkspaceId, _activeWorkspaceMembers) { items, verified, active, members ->
            val uid = getEffectiveActorUid().orEmpty()
            val role = com.example.data.auth.RoleUtils.normalizeRole(members.firstOrNull { it.uid == uid && it.workspaceId == workspaceId && it.status.equals("active", true) }?.role)
            if (uid.isBlank() || active != workspaceId || verified != (uid to workspaceId) || role.isBlank() ||
                (financial && role == com.example.data.auth.RoleUtils.ROLE_OPERATOR)) emptyList() else items
        }

    fun getPaymentsFlow(workspaceId: String): Flow<List<PaymentEntity>> {
        return authorizedRecords(workspaceId, true, paymentDao.getPaymentsForWorkspace(workspaceId))
    }

    suspend fun deletePayment(paymentId: Long) {
        val operationActorUid = getEffectiveActorUid().orEmpty()
        val operationTime = System.currentTimeMillis()
        val result = database.withTransaction {

        val wsId = getOrResolveWorkspaceId().orEmpty()
        val role = requireWorkspaceActor(wsId)
        val actorUid = getEffectiveActorUid().orEmpty()
        var reversed: PaymentEntity? = null
        val updates = mutableListOf<JobEntryEntity>()
        database.withTransaction {
            val original = paymentDao.getPaymentById(_activeWorkspaceId.value.orEmpty(), paymentId) ?: throw SecurityException("Collection requires verified migration before reversal")
            if (original.workspaceId != wsId) throw SecurityException("Collection workspace mismatch")
            if (!com.example.data.auth.AuthorizationManager.canReverseCollection(original, isOwnerOfWorkspace(wsId), actorUid, role, actionTime = operationTime)) throw SecurityException("Collection reversal is not permitted")
            val allocations = org.json.JSONArray(original.allocationsJson)
            if (allocations.length() == 0) throw IllegalStateException("Collection allocation must be migrated before reversal")
            var total = 0.0
            for (i in 0 until allocations.length()) {
                val allocation = allocations.getJSONObject(i)
                val job = jobEntryDao.getJobById(_activeWorkspaceId.value.orEmpty(), allocation.getLong("jobId")) ?: throw IllegalStateException("Collection source job is missing")
                val amount = allocation.getDouble("amount")
                require(job.workspaceId == wsId && job.customerId == original.customerId && amount > 0 && job.amountReceived >= amount) { "Invalid collection allocation" }
                val updated = job.copy(amountReceived = job.amountReceived - amount, pendingAmount = job.pendingAmount + amount, isSynced = false)
                jobEntryDao.insertJob(updated); updates.add(updated); total += amount
            }
            require(kotlin.math.abs(total - original.amount) < 0.001) { "Collection allocation does not match the collection amount" }
            reversed = original.copy(status = "REVERSED", reversedByUid = actorUid, reversedAt = operationTime, isSynced = false)
            paymentDao.insertPayment(reversed!!)
            require(updates.size == 1) { "Legacy aggregate allocation requires reviewed migration" }
            val updated = updates.single()
            enqueue("PAYMENT", reversed!!.id, reversed!!.toFirestoreMap() + mapOf(
                "entrySnapshotJson" to org.json.JSONObject(updated.toFirestoreMap()).toString(),
                "baseAmountReceived" to (updated.amountReceived + original.amount),
                "basePendingAmount" to (updated.pendingAmount - original.amount)), actionTime = operationTime, expectedActorUid = operationActorUid)
            recalculateCustomerStats(original.customerId)
        }


        }
        if (isCloudReady()) scope.launch { pushUnsyncedToCloud() }
        return result
    }

    fun getChecklistFlow(workspaceId: String): Flow<List<ChecklistItemEntity>> {
        return authorizedRecords(workspaceId, records = checklistItemDao.getChecklistForWorkspace(workspaceId))
    }

    suspend fun addChecklistItem(text: String, workspaceId: String): Long {
        requireWorkspaceActor(workspaceId)
        val item = ChecklistItemEntity(
            workspaceId = workspaceId,
            text = text,
            isChecked = false,
            createdAt = System.currentTimeMillis()
        )
        return checklistItemDao.insertChecklistItem(item)
    }

    suspend fun updateChecklistItem(item: ChecklistItemEntity) {
        requireWorkspaceActor(item.workspaceId)
        checklistItemDao.updateChecklistItem(item)
    }

    suspend fun deleteChecklistItem(item: ChecklistItemEntity) {
        requireWorkspaceActor(item.workspaceId)
        checklistItemDao.deleteChecklistItem(item)
    }

    suspend fun deleteChecklistItemById(id: Long) {
        requireWorkspaceActor(_activeWorkspaceId.value.orEmpty())
        checklistItemDao.deleteChecklistItemById(_activeWorkspaceId.value.orEmpty(), id)
    }

    private suspend fun recalculateCustomerStats(customerId: Long, workspaceId: String = _activeWorkspaceId.value.orEmpty(), clearDeletedSourceBalance: Boolean = false) {
        val customer = customerDao.getCustomerById(workspaceId, customerId) ?: return
        val jobs = jobEntryDao.getJobsForWorkspaceOnce(customer.workspaceId)
        val source = if (clearDeletedSourceBalance) customer.copy(totalBilled = 0.0, totalPaid = 0.0, balanceDue = 0.0) else customer
        val stats = com.example.ui.util.FinancialCalculationEngine.calculateCustomerFinancials(source, jobs)

        val pendingCustomerWork = operations.outstandingOperations(workspaceId).any {
            com.example.data.sync.OperationDependencies.blocksCustomerAcknowledgement(it, customerId)
        }
        val updated = customer.copy(
            totalBilled = stats.totalBilled,
            totalPaid = stats.totalPaid,
            balanceDue = stats.balanceDue,
            isSynced = !pendingCustomerWork && jobs.filter { it.customerId == customerId }.all { it.isSynced },
            updatedAt = System.currentTimeMillis()
        )
        customerDao.updateCustomer(updated)
    }

    suspend fun addTractor(tractor: TractorEntity): Long {
        val operationActorUid = getEffectiveActorUid().orEmpty()
        val operationTime = System.currentTimeMillis()
        val result = database.withTransaction {

        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: ""
        val safeTracId = if (tractor.id > 0) tractor.id else IdGenerator.generateId()
        val role = requireWorkspaceActor(wsId)
        if (!com.example.data.auth.AuthorizationManager.canManageTractors(isOwnerOfWorkspace(wsId), role)) throw SecurityException("Asset creation is forbidden")
        val saved = tractor.copy(id = safeTracId, workspaceId = wsId, createdByUid = getEffectiveActorUid().orEmpty(), createdByRole = role)
        tractorDao.insertTractor(saved)
        enqueue("TRACTOR", saved.id, saved.toFirestoreMap(), "UPSERT", expectedActorUid = operationActorUid, actionTime = operationTime)


        return@withTransaction safeTracId

        }
        if (isCloudReady()) scope.launch { pushUnsyncedToCloud() }
        return result
    }

    fun getWorkTypeExtensionsFlow(workspaceId: String): Flow<List<WorkTypeExtensionEntity>> {
        return authorizedRecords(workspaceId, records = workTypeExtensionDao.getExtensionsForWorkspace(workspaceId))
    }

    suspend fun addExtension(name: String, workspaceId: String = ""): WorkTypeExtensionEntity {
        val operationActorUid = getEffectiveActorUid().orEmpty()
        val operationTime = System.currentTimeMillis()
        val result = database.withTransaction {

        val cleanName = name.trim()
        if (cleanName.isBlank()) throw IllegalArgumentException("Extension name cannot be empty")
        val wsId = workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        val actorUid = getEffectiveActorUid() ?: ""
        val isOwner = isOwnerOfWorkspace(wsId)
        val myRole = requireWorkspaceActor(wsId)
        if (!com.example.data.auth.AuthorizationManager.canAddExtension(isOwner, myRole)) {
            throw SecurityException("Operators cannot add extensions")
        }
        val id = IdGenerator.generateId()
        val entity = WorkTypeExtensionEntity(
            id = id,
            workspaceId = wsId,
            name = cleanName,
            createdByUid = actorUid,
            createdByRole = myRole,
            createdAt = operationTime
        )
        workTypeExtensionDao.insertExtension(entity)
        enqueue("EXTENSION", entity.id, entity.toFirestoreMap(), "UPSERT", expectedActorUid = operationActorUid, actionTime = operationTime)

        return@withTransaction entity

        }
        if (isCloudReady()) scope.launch { pushUnsyncedToCloud() }
        return result
    }

    suspend fun updateExtension(extension: WorkTypeExtensionEntity) {
        val operationActorUid = getEffectiveActorUid().orEmpty()
        val operationTime = System.currentTimeMillis()
        database.withTransaction {
            val wsId = _activeWorkspaceId.value.orEmpty()
            val role = requireWorkspaceActor(wsId)
            val original = workTypeExtensionDao.getExtensionsForWorkspaceOnce(wsId).firstOrNull { it.id == extension.id }
                ?: throw SecurityException("Extension is outside this workspace")
            requireRecordMembersActive(original.createdByUid)
            if (!com.example.data.auth.AuthorizationManager.canMutateAsset(isOwnerOfWorkspace(wsId), role, getEffectiveActorUid().orEmpty(), original.createdByUid))
                throw SecurityException("Cannot edit another member's extension")
            require(extension.name.isNotBlank()) { "Extension name is required" }
            val saved = original.copy(name = extension.name.trim())
            workTypeExtensionDao.insertExtension(saved)
            enqueue("EXTENSION", saved.id, saved.toFirestoreMap(), expectedActorUid = operationActorUid, actionTime = operationTime)
        }
        if (isCloudReady()) scope.launch { pushUnsyncedToCloud() }
    }

    suspend fun deleteExtension(extension: WorkTypeExtensionEntity) {
        val operationActorUid = getEffectiveActorUid().orEmpty()
        val operationTime = System.currentTimeMillis()
        database.withTransaction {
            val wsId = _activeWorkspaceId.value.orEmpty()
            val role = requireWorkspaceActor(wsId)
            val original = workTypeExtensionDao.getExtensionsForWorkspaceOnce(wsId).firstOrNull { it.id == extension.id }
                ?: throw SecurityException("Extension is outside this workspace")
            requireRecordMembersActive(original.createdByUid)
            if (!com.example.data.auth.AuthorizationManager.canMutateAsset(isOwnerOfWorkspace(wsId), role, getEffectiveActorUid().orEmpty(), original.createdByUid))
                throw SecurityException("Cannot delete another member's extension")
            workTypeExtensionDao.deleteExtension(original)
            enqueue("EXTENSION", original.id, original.toFirestoreMap(), "DELETE", expectedActorUid = operationActorUid, actionTime = operationTime)
        }
        if (isCloudReady()) scope.launch { pushUnsyncedToCloud() }
    }

    suspend fun updateTractor(tractor: TractorEntity) {
        val operationActorUid = getEffectiveActorUid().orEmpty()
        val operationTime = System.currentTimeMillis()
        val result = database.withTransaction {

        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: tractor.workspaceId
        val original = tractorDao.getTractorsForWorkspace(wsId).firstOrNull()?.firstOrNull { it.id == tractor.id } ?: throw SecurityException("Tractor is not in the active workspace")
        val role = requireWorkspaceActor(wsId)
        if (!com.example.data.auth.AuthorizationManager.canMutateAsset(isOwnerOfWorkspace(wsId), role, getEffectiveActorUid().orEmpty(), original.createdByUid)) throw SecurityException("Cannot edit another member's tractor")
        requireRecordMembersActive(original.createdByUid)
        val scoped = tractor.copy(workspaceId = wsId, createdByUid = original.createdByUid, createdAt = original.createdAt)
        tractorDao.updateTractor(scoped)
        enqueue("TRACTOR", scoped.id, scoped.toFirestoreMap(), "UPSERT", expectedActorUid = operationActorUid, actionTime = operationTime)



        }
        if (isCloudReady()) scope.launch { pushUnsyncedToCloud() }
        return result
    }

    suspend fun deleteTractor(tractor: TractorEntity) {
        val operationActorUid = getEffectiveActorUid().orEmpty()
        val operationTime = System.currentTimeMillis()
        val result = database.withTransaction {

        val wsId = tractor.workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        val role = requireWorkspaceActor(wsId)
        val stored = tractorDao.getTractorsForWorkspace(wsId).firstOrNull()?.firstOrNull { it.id == tractor.id } ?: throw SecurityException("Tractor is not in the active workspace")
        if (!com.example.data.auth.AuthorizationManager.canMutateAsset(isOwnerOfWorkspace(wsId), role, getEffectiveActorUid().orEmpty(), stored.createdByUid)) throw SecurityException("Cannot delete another member's tractor")
        requireRecordMembersActive(stored.createdByUid)
        tractorDao.deleteTractor(stored)
        enqueue("TRACTOR", stored.id, stored.toFirestoreMap(), "DELETE", expectedActorUid = operationActorUid, actionTime = operationTime)
        if (wsId.isNotBlank()) {
            pendingDeleteManager.recordPendingDelete("TRACTOR", tractor.id, wsId)
        }

        val isReady = isCloudReady()


        }
        if (isCloudReady()) scope.launch { pushUnsyncedToCloud() }
        return result
    }

    suspend fun addPartner(partner: PartnerEntity): Long {
        throw SecurityException("Membership creation requires verified invitation OTP")
    }

    suspend fun updatePartner(partner: PartnerEntity) {
        val wsId = _activeWorkspaceId.value.orEmpty()
        if (requireWorkspaceActor(wsId) != com.example.data.auth.RoleUtils.ROLE_OWNER) throw SecurityException("Member management is Owner-only")
        val member = _activeWorkspaceMembers.value.firstOrNull { it.uid == partner.firebaseUid && it.workspaceId == wsId && it.status.equals("active", true) }
            ?: throw SecurityException("Verified member identity is required")
        require(com.example.data.auth.RoleUtils.normalizeRole(member.role) == com.example.data.auth.RoleUtils.normalizeRole(partner.role)) { "Role changes are unavailable" }
        require(partner.phone.filter(Char::isDigit).takeLast(10) == member.phoneNumber.orEmpty().filter(Char::isDigit).takeLast(10)) { "Changing a verified phone requires a new verification workflow" }
        firestoreRepository.updateMemberDisplayName(wsId, member.uid, partner.name)
        partnerDao.insertPartner(partner.copy(workspaceId = wsId, role = member.role))
        refreshWorkspaceMembers(wsId)
    }

    suspend fun deletePartner(partner: PartnerEntity) {
        removePartnerFromWorkspace(partner)
    }

    sealed class DirectAddPartnerResult {
        data class Success(val partner: PartnerEntity, val partnerUid: String) : DirectAddPartnerResult()
        data class AccountNotRegistered(val partner: PartnerEntity, val message: String) : DirectAddPartnerResult()
        data class Error(val message: String) : DirectAddPartnerResult()
    }

    sealed class PendingInvitationCleanupResult {
        object Deleted : PendingInvitationCleanupResult()
        object AlreadyGone : PendingInvitationCleanupResult()
        object Registered : PendingInvitationCleanupResult()
        data class Rejected(val message: String) : PendingInvitationCleanupResult()
        data class Failed(val message: String) : PendingInvitationCleanupResult()
    }

    suspend fun refreshWorkspaceMembers(workspaceId: String? = null) {
        val activeId = _activeWorkspaceId.value ?: return
        val members = firestoreRepository.getWorkspaceMembers(activeId)
        _activeWorkspaceMembers.value = members
        _workspaceMembers.value = members
        cacheVerifiedMembership(getEffectiveActorUid().orEmpty(), activeId, members)
    }

    suspend fun addPartnerDirectly(name: String, phone: String, role: String): DirectAddPartnerResult {
        val wsId = _activeWorkspaceId.value.orEmpty()
        if (requireWorkspaceActor(wsId) != com.example.data.auth.RoleUtils.ROLE_OWNER) return DirectAddPartnerResult.Error("Only Owner can invite members")
        val normalizedRole = com.example.data.auth.RoleUtils.normalizeRole(role)
        if (normalizedRole !in setOf(com.example.data.auth.RoleUtils.ROLE_PARTNER, com.example.data.auth.RoleUtils.ROLE_OPERATOR)) return DirectAddPartnerResult.Error("Choose Partner or Operator")
        val members = firestoreRepository.getWorkspaceMembers(wsId)
        val digits = phone.filter(Char::isDigit).takeLast(10)
        val verified = members.firstOrNull { it.uid.isNotBlank() && it.phoneNumber.orEmpty().filter(Char::isDigit).takeLast(10) == digits &&
            it.status.equals("active", true) && com.example.data.auth.RoleUtils.normalizeRole(it.role) == normalizedRole }
            ?: return DirectAddPartnerResult.Error("Invitation OTP verification is required before adding this member")
        val partner = PartnerEntity(id = IdGenerator.generateId(), workspaceId = wsId, name = verified.displayName.orEmpty(),
            phone = verified.phoneNumber.orEmpty(), role = normalizedRole, firebaseUid = verified.uid)
        partnerDao.insertPartner(partner)
        refreshWorkspaceMembers(wsId)
        return DirectAddPartnerResult.Success(partner, verified.uid)
    }

    suspend fun removePartner(partner: PartnerEntity, partnerUid: String? = null) {
        removePartnerFromWorkspace(partner, partnerUid)
    }

    /**
     * Removes only a waiting-for-registration invitation from the active
     * workspace. This is deliberately separate from normal member removal.
     */
    suspend fun removePendingPartnerInvitation(
        pending: WorkspaceMember
    ): PendingInvitationCleanupResult {
        val workspaceId = _activeWorkspaceId.value?.trim().orEmpty()
        val actorUid = getEffectiveActorUid()?.trim().orEmpty()
        if (workspaceId.isBlank() || actorUid.isBlank()) {
            return PendingInvitationCleanupResult.Rejected("Session or workspace is not ready")
        }
        val actorRole = if (isOwnerOfWorkspace(workspaceId)) "Owner" else
            _activeWorkspaceMembers.value.firstOrNull { it.uid == actorUid && it.status.equals("active", true) }?.role.orEmpty()
        if (!com.example.data.auth.canCleanupPendingInvitation(pending, workspaceId, actorUid, actorRole)) {
            return PendingInvitationCleanupResult.Rejected("Only the workspace Owner or the creating Partner may remove this unresolved Operator invitation")
        }
        val pendingPhone = pending.phoneNumber?.trim().orEmpty()
        if (pendingPhone.isBlank()) {
            return PendingInvitationCleanupResult.Rejected("Pending invitation has no valid phone identity")
        }
        if (!isCloudReady()) {
            return PendingInvitationCleanupResult.Failed("Connect to the workspace before removing this pending invitation")
        }

        val cloudResult = firestoreRepository.deletePendingPartnerInvitation(
            workspaceId = workspaceId,
            normalizedPhone = pendingPhone,
            actorUid = actorUid
        ).getOrElse { error ->
            return PendingInvitationCleanupResult.Failed(
                error.message ?: "Pending invitation cleanup failed"
            )
        }

        if (cloudResult == FirestoreRepository.PendingPartnerCleanupResult.REGISTERED) {
            refreshWorkspaceMembers(workspaceId)
            return PendingInvitationCleanupResult.Registered
        }

        if (_activeWorkspaceId.value != workspaceId || getEffectiveActorUid() != actorUid) {
            return PendingInvitationCleanupResult.Rejected("Session or workspace changed during cleanup")
        }
        // The cloud operation has confirmed that no active member exists for
        // this pending phone. Remove only local unresolved placeholders from
        // the same workspace; never delete by name or across workspaces.
        val cleanPhone = pendingPhone.filter { it.isDigit() }.takeLast(10)
        if (cleanPhone.length == 10) {
            partnerDao.getPartnersForWorkspaceOnce(workspaceId)
                .filter { it.phone.filter { ch -> ch.isDigit() }.takeLast(10) == cleanPhone }
                .forEach { partnerDao.deletePartner(it) }
        }
        refreshWorkspaceMembers(workspaceId)
        return when (cloudResult) {
            FirestoreRepository.PendingPartnerCleanupResult.DELETED -> PendingInvitationCleanupResult.Deleted
            FirestoreRepository.PendingPartnerCleanupResult.ALREADY_GONE -> PendingInvitationCleanupResult.AlreadyGone
            FirestoreRepository.PendingPartnerCleanupResult.REGISTERED -> PendingInvitationCleanupResult.Registered
        }
    }

    suspend fun checkForInvitations(phoneNumber: String): List<WorkspaceInvitation> {
        _pendingInvitations.value = emptyList()
        return emptyList()
    }

    suspend fun switchActiveWorkspace(targetWorkspaceId: String, userProfile: UserProfile): Result<String> {
        if (!com.example.data.firebase.FirebaseEnvironment.permitsWorkspaceDiscovery(targetWorkspaceId, com.example.BuildConfig.PHYSICAL_TEST))
            return Result.failure(SecurityException(if (com.example.BuildConfig.PHYSICAL_TEST) "Physical test can use only authorized staging workspaces" else "Target workspace is not permitted in this build"))
        val uid = userProfile.uid
        if (targetWorkspaceId.isBlank()) {
            return Result.failure(IllegalArgumentException("Target workspace ID cannot be blank"))
        }

        val request = workspaceRequests.begin(uid)
        return try {
            if (getEffectiveActorUid() != uid) throw SecurityException("Authenticated identity mismatch")
            val normalizedTargetRole = firestoreRepository.resolveAuthoritativeRole(uid, targetWorkspaceId)
                ?: throw SecurityException("Target workspace membership is inactive or missing")
            val targetMembers = firestoreRepository.getWorkspaceMembers(targetWorkspaceId)
            workspaceRequests.check(request)
            _workspaceInitState.value = WorkspaceInitState.Loading
            _settingsSyncState.value = SettingsSyncState.Loading

            // Load settings for target workspace
            val localSettings = appSettingsDao.getSettingsForWorkspaceOnce(targetWorkspaceId)
                ?: AppSettingsEntity(workspaceId = targetWorkspaceId)
            val resolvedSettings = firestoreRepository.fetchOrCreateWorkspaceSettings(targetWorkspaceId, uid, localSettings)
            workspaceRequests.check(request)

            val savedLang = getSavedUserLanguage(uid) ?: localSettings.language
            val personalWsId = _personalWorkspaceId.value
            val currentActorName = userProfile.displayName?.ifBlank { null }
                ?: (if (!personalWsId.isNullOrBlank()) appSettingsDao.getSettingsForWorkspaceOnce(personalWsId)?.activePartnerName?.ifBlank { null } else null)
                ?: localSettings.activePartnerName.ifBlank { null }
                ?: "Partner"

            val currentActorPhone = userProfile.phoneNumber?.ifBlank { null }
                ?: (if (!personalWsId.isNullOrBlank()) appSettingsDao.getSettingsForWorkspaceOnce(personalWsId)?.activePartnerPhone?.ifBlank { null } else null)
                ?: localSettings.activePartnerPhone.ifBlank { null }
                ?: resolvedSettings.businessPhone

            val mergedSettings = resolvedSettings.copy(
                workspaceId = targetWorkspaceId,
                isLoggedIn = true,
                language = savedLang,
                activePartnerName = currentActorName,
                activePartnerPhone = currentActorPhone,
                profilePhotoUri = userProfile.photoUrl?.ifBlank { null }
                    ?: resolvedSettings.profilePhotoUri,
                lastSyncTime = System.currentTimeMillis()
            )
            workspaceRequests.check(request)
            appSettingsDao.insertOrUpdateSettings(mergedSettings)
            workspaceRequests.check(request)
            loadCachedPercentages(targetWorkspaceId)
            loadCachedProfitShareAllocations(targetWorkspaceId)
            workspaceRequests.check(request)
            _settingsSyncState.value = SettingsSyncState.LoadedFromCloud(mergedSettings)

            // Switch active workspace ID
            _activeWorkspaceId.value = targetWorkspaceId
            _activeWorkspaceMembers.value = targetMembers
            _workspaceMembers.value = targetMembers
            cacheVerifiedMembership(uid, targetWorkspaceId, targetMembers)
            _isInitialized.value = true
            _workspaceInitState.value = WorkspaceInitState.Ready(targetWorkspaceId)

            // Re-evaluate role for target workspace using the AUTHORITATIVE MEMBERSHIP RECORD only.
            // Workspace ownership (ownerUid) must never be used to infer the role.
            currentUserRole = normalizedTargetRole
            val isOwner = normalizedTargetRole == com.example.data.auth.RoleUtils.ROLE_OWNER
            _isCollaborationOwner.value = isOwner
            Log.d("TRAC_ROLE", "ACTIVATE uid=$uid workspace=$targetWorkspaceId role=$normalizedTargetRole isOwner=$isOwner")

            // Reconnect real-time listeners for the new workspace
            if (isCloudReady()) {
                _visibleWorkspaceIds.value = setOf(targetWorkspaceId)
                attachRealtimeListenersForVisibleWorkspaces()
                refreshWorkspaceMembers(targetWorkspaceId)
                pushUnsyncedToCloud(targetWorkspaceId)

                // Load profit share allocations from cloud for new workspace
                scope.launch {
                    loadProfitShareAllocationsFromCloud(targetWorkspaceId)
                    // No automatic identity migration during workspace switching.
                }
            }

            Result.success(targetWorkspaceId)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) {
            workspaceRequests.check(request)
            Log.e(TAG, "Failed to switch active workspace: ${e.message}", e)
            _workspaceInitState.value = WorkspaceInitState.Error(e)
            _settingsSyncState.value = SettingsSyncState.Error(e.message ?: "Failed to switch workspace")
            Result.failure(e)
        }
    }

    suspend fun removePartnerFromWorkspace(partner: PartnerEntity, targetPartnerUid: String? = null) {
        val wsId = _activeWorkspaceId.value.orEmpty()
        if (requireWorkspaceActor(wsId) != com.example.data.auth.RoleUtils.ROLE_OWNER) throw SecurityException("Only Owner can remove members")
        val targetUid = targetPartnerUid?.takeIf { it.isNotBlank() } ?: partner.firebaseUid.takeIf { it.isNotBlank() } ?: throw SecurityException("Verified member UID is required; names and phone numbers cannot authorize removal")
        val archived = firestoreRepository.archiveMember(wsId, targetUid)
        database.withTransaction { database.deletedMemberDao().save(archived) }
        refreshWorkspaceMembers(wsId)
    }

    fun deletedMembers(wsId: String) = database.deletedMemberDao().observe(wsId)

    suspend fun refreshDeletedMembers() {
        val wsId = _activeWorkspaceId.value.orEmpty()
        if (requireWorkspaceActor(wsId) != com.example.data.auth.RoleUtils.ROLE_OWNER) throw SecurityException("Deleted Users is Owner-only")
        firestoreRepository.getDeletedMembers(wsId).forEach { database.deletedMemberDao().save(it) }
    }

    suspend fun permanentlyDeleteMember(uid: String, confirmation: String) = syncMutex.withLock {
        val wsId = _activeWorkspaceId.value.orEmpty()
        if (requireWorkspaceActor(wsId) != com.example.data.auth.RoleUtils.ROLE_OWNER) throw SecurityException("Permanent deletion is Owner-only")
        require(confirmation == "PERMANENTLY DELETE") { "Explicit permanent-delete confirmation is required" }
        val archive = database.deletedMemberDao().get(wsId, uid) ?: throw SecurityException("Member is not in Deleted Users")
        val plan = com.example.data.sync.MemberPurgePlan.build(wsId, uid,
            jobEntryDao.getJobsForWorkspaceOnce(wsId), expenseDao.getExpensesForWorkspace(wsId).firstOrNull().orEmpty(),
            withdrawalDao.getWithdrawalsForWorkspaceOnce(wsId), tractorDao.getTractorsForWorkspaceOnce(wsId),
            workTypeExtensionDao.getExtensionsForWorkspace(wsId).firstOrNull().orEmpty(),
            paymentDao.getPaymentsForWorkspace(wsId).firstOrNull().orEmpty(), customerDao.getCustomersForWorkspace(wsId).firstOrNull().orEmpty())
        database.withTransaction {
            database.deletedMemberDao().save(archive.copy(purgeState = "PURGE_PENDING"))
            operations.invalidateActor(wsId, uid)
            plan.jobs.forEach { operations.invalidateEntity(wsId, "JOB", it.id) }
            plan.expenses.forEach { operations.invalidateEntity(wsId, "EXPENSE", it.id) }
            plan.withdrawals.forEach { operations.invalidateEntity(wsId, "WITHDRAWAL", it.id) }
            plan.collections.forEach { operations.invalidateEntity(wsId, "PAYMENT", it.id) }
            plan.tractors.forEach { operations.invalidateEntity(wsId, "TRACTOR", it.id) }
            plan.extensions.forEach { operations.invalidateEntity(wsId, "EXTENSION", it.id) }
            plan.customers.forEach { operations.invalidateEntity(wsId, "CUSTOMER", it.id) }
        }
        firestoreRepository.stopRealtimeListeners()
        try {
        val backup = firestoreRepository.permanentlyDeleteMember(wsId, uid)
        val manifest = firestoreRepository.memberPurgeManifest(wsId, uid)
        database.withTransaction {
            for ((type, id) in manifest) operations.invalidateEntity(wsId, type, id)
            // Also invalidate queued owner-created target records that never reached the cloud/Room plan.
            for (op in operations.outstandingOperations(wsId)) {
                val data = org.json.JSONObject(op.payloadJson)
                if (listOf("createdByUid", "entryForUid", "expenseForUid", "targetPartnerUid", "financialAttributionUid", "collectedByUid").any { data.optString(it) == uid })
                    operations.invalidateEntity(wsId, op.entityType, op.entityId)
            }
        }
        val deletedJobIds = plan.jobs.map { it.id }.toSet()
        val retainedSources = plan.collections.filter { it.status == "ACTIVE" && it.jobEntryId !in deletedJobIds }
            .map { it.jobEntryId }.distinct().map { id ->
                firestoreRepository.getEntryFromServer(wsId, id)
                    ?: throw IllegalStateException("Retained source is missing after cloud reconciliation")
            }
        database.withTransaction {
            retainedSources.forEach { reconciled ->
                jobEntryDao.insertJob(reconciled.copy(isSynced = true))
            }
            plan.collections.forEach { paymentDao.deletePayment(it) }
            plan.expenses.forEach { expenseDao.deleteExpense(it) }
            plan.withdrawals.forEach { withdrawalDao.deleteWithdrawal(it) }
            plan.jobs.forEach { jobEntryDao.deleteJob(it) }
            plan.tractors.forEach { tractorDao.deleteTractor(it) }
            plan.extensions.forEach { workTypeExtensionDao.deleteExtension(it) }
            plan.customers.forEach { customerDao.deleteCustomer(it) }
            customerDao.getCustomersForWorkspace(wsId).firstOrNull().orEmpty().forEach { recalculateCustomerStats(it.id) }
            database.deletedMemberDao().save(archive.copy(purgeState = "PURGE_PENDING", backupPath = backup))
        }
        firestoreRepository.completeMemberPurge(wsId, uid)
        database.deletedMemberDao().save(archive.copy(purgeState = "PURGED", backupPath = backup))
        } finally { attachRealtimeListenersForVisibleWorkspaces() }
    }

    suspend fun leaveCollaborationGroup(ownerWorkspaceId: String) {
        val uid = activeUid ?: ""
        val currentSettings = appSettingsDao.getSettingsForWorkspaceOnce(getOrResolveWorkspaceId() ?: "")
        val currentPhone = currentSettings?.activePartnerPhone ?: ""

        if (isCloudReady() && ownerWorkspaceId.isNotBlank() && uid.isNotBlank()) {
            try {
                firestoreRepository.removePartnerFromWorkspace(
                    workspaceId = ownerWorkspaceId,
                    partnerUid = uid,
                    partnerPhone = currentPhone
                )
                firestoreRepository.removePartnerFromCollaborationGroup(ownerWorkspaceId, uid)
                val currentVisible = _visibleWorkspaceIds.value.toMutableSet()
                currentVisible.remove(ownerWorkspaceId)
                _visibleWorkspaceIds.value = currentVisible
                attachRealtimeListenersForVisibleWorkspaces()
                refreshWorkspaceMembers()
                Log.d("TRAC_PARTNER", "User $uid left collaboration group $ownerWorkspaceId")

                if (_activeWorkspaceId.value == ownerWorkspaceId) {
                    val available = getAvailableWorkspaces(UserProfile(uid = uid))
                    val fallbackId = available.firstOrNull()?.workspaceId ?: _personalWorkspaceId.value
                    if (fallbackId != null && fallbackId != ownerWorkspaceId) {
                        switchActiveWorkspace(fallbackId, UserProfile(uid = uid))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error leaving collaboration group: ${e.message}", e)
            }
        }
    }

    suspend fun getGenuinePartnerWorkspaces(userProfile: UserProfile, personalWsId: String): List<Workspace> {
        if (com.example.BuildConfig.PHYSICAL_TEST) return emptyList()
        val uid = userProfile.uid
        if (uid.isBlank()) return emptyList()
        val genuinePartners = mutableMapOf<String, Workspace>()

        // 1. Discover partner workspaces from userCollaborationGroups
        if (isCloudReady()) {
            try {
                val groupIndices = firestoreRepository.getUserCollaborationGroups(uid)
                for (grp in groupIndices) {
                    val gId = grp.groupId.trim()
                    val ownerUid = grp.ownerUid.trim()
                    val role = grp.role.trim()
                    val status = grp.status.trim()

                    // Strict Genuine Partner Rules:
                    // - Owner's own workspace must NEVER count as a partner workspace
                    // - Owner's own collaborationGroups document must NEVER count as partner membership
                    if (gId.isBlank() || gId == personalWsId) continue
                    if (grp.ownerWorkspaceId.isNotBlank() && grp.ownerWorkspaceId == personalWsId) continue
                    if (role.equals("owner", ignoreCase = true)) continue
                    if (ownerUid.isBlank() || ownerUid == uid) continue
                    if (status.isNotBlank() && !status.equals("active", ignoreCase = true)) continue

                    val localWsSettings = appSettingsDao.getSettingsForWorkspaceOnce(gId)
                    val businessName = localWsSettings?.businessName?.ifBlank { null }
                        ?: try {
                            val details = firestoreRepository.getWorkspaceDetails(gId)
                            details?.name?.ifBlank { null }
                        } catch (_: Exception) { null }
                        ?: "Partner Business"

                    genuinePartners[gId] = Workspace(
                        workspaceId = gId,
                        name = businessName,
                        ownerUid = ownerUid,
                        createdAt = grp.joinedAt
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching userCollaborationGroups: ${e.message}")
            }

            // 2. Discover partner workspaces from userWorkspaceMemberships
            try {
                val memberships = firestoreRepository.getUserWorkspaceMemberships(uid)
                for (m in memberships) {
                    val wsId = (m["workspaceId"] as? String)?.trim() ?: continue
                    val ownerUid = (m["ownerUid"] as? String)?.trim() ?: ""
                    val role = (m["role"] as? String)?.trim() ?: ""
                    val status = (m["status"] as? String)?.trim() ?: "active"

                    if (wsId.isBlank() || wsId == personalWsId) continue
                    if (role.equals("owner", ignoreCase = true)) continue
                    if (ownerUid.isBlank() || ownerUid == uid) continue
                    if (status.isNotBlank() && !status.equals("active", ignoreCase = true)) continue
                    if (genuinePartners.containsKey(wsId)) continue

                    val details = firestoreRepository.getWorkspaceDetails(wsId)
                    val wsName = details?.name?.ifBlank { null }
                        ?: (m["workspaceName"] as? String)?.ifBlank { null }
                        ?: appSettingsDao.getSettingsForWorkspaceOnce(wsId)?.businessName?.ifBlank { null }
                        ?: "Partner Business"

                    genuinePartners[wsId] = Workspace(
                        workspaceId = wsId,
                        name = wsName,
                        ownerUid = ownerUid,
                        createdAt = (m["joinedAt"] as? Long) ?: System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error fetching userWorkspaceMemberships: ${e.message}")
            }
        }

        // 3. User profile listed workspaces (with strict ownerUid != uid verification)
        for (wsId in userProfile.workspaces) {
            val cleanId = wsId.trim()
            if (cleanId.isBlank() || cleanId == personalWsId || genuinePartners.containsKey(cleanId)) continue
            try {
                val ws = firestoreRepository.getWorkspaceDetails(cleanId)
                if (ws != null && ws.ownerUid.isNotBlank() && ws.ownerUid != uid) {
                    genuinePartners[cleanId] = ws
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking workspace $cleanId: ${e.message}")
            }
        }

        Log.d("TRAC_WORKSPACE", "uid=$uid personal=$personalWsId genuinePartnerWorkspaces=${genuinePartners.keys}")
        return genuinePartners.values.toList()
    }

    suspend fun getAvailableWorkspaces(userProfile: UserProfile): List<Workspace> {
        val uid = userProfile.uid
        require(uid.isNotBlank() && uid == getEffectiveActorUid()) { "Authenticated identity mismatch" }
        val memberships = firestoreRepository.discoverAuthoritativeMemberships(uid, _activeWorkspaceId.value)
        val available = mutableListOf<Workspace>()
        for (membership in memberships) {
            if (!com.example.data.firebase.FirebaseEnvironment.permitsWorkspaceDiscovery(membership.workspaceId, com.example.BuildConfig.PHYSICAL_TEST)) continue
            if (uid != getEffectiveActorUid()) throw SecurityException("Session changed during workspace discovery")
            val ws = firestoreRepository.getWorkspaceDetails(membership.workspaceId)
                ?.copy(workspaceId = membership.workspaceId, accessRole = membership.role)
                ?: Workspace(workspaceId = membership.workspaceId, name = membership.workspaceId, accessRole = membership.role)
            available.add(ws)
        }
        if (uid != getEffectiveActorUid()) throw SecurityException("Session changed during workspace discovery")
        val deduplicated = available
            .distinctBy { it.workspaceId }
            .sortedWith(compareBy({ it.name.ifBlank { it.workspaceId } }, { it.workspaceId }))
        _availableWorkspaces.value = deduplicated
        _genuinePartnerWorkspaces.value = deduplicated
        return deduplicated
    }

    suspend fun getWorkspaceMembers(workspaceId: String? = null): List<WorkspaceMember> {
        val targetWsId = workspaceId ?: getOrResolveWorkspaceId() ?: return emptyList()
        return firestoreRepository.getWorkspaceMembers(targetWsId)
    }

    suspend fun updateSettings(settings: AppSettingsEntity) {
        com.example.ui.util.requireOptionalContactPhone(settings.activePartnerPhone)
        val isReady = isCloudReady()
        val wsId = _activeWorkspaceId.value.orEmpty()
        val settingsRole = requireWorkspaceActor(wsId)
        require(settings.workspaceId == wsId) { "Settings workspace mismatch" }
        val isOwner = settingsRole == com.example.data.auth.RoleUtils.ROLE_OWNER
        val existing = appSettingsDao.getSettingsForWorkspaceOnce(wsId)
        val scoped = if (!isOwner && existing != null) {
            settings.copy(
                workspaceId = wsId,
                defaultHourlyRate = existing.defaultHourlyRate,
                currency = existing.currency,
                sharedAccountId = existing.sharedAccountId,
                lockedTractorLabel = existing.lockedTractorLabel,
                businessName = existing.businessName,
                businessPhone = existing.businessPhone,
                businessAddress = existing.businessAddress,
                gstNumber = existing.gstNumber,
                ownerName = existing.ownerName
            )
        } else {
            settings.copy(workspaceId = wsId, businessPhone = com.example.ui.util.requireOptionalContactPhone(settings.businessPhone))
        }
        appSettingsDao.insertOrUpdateSettings(scoped)

        val actorUid = getEffectiveActorUid()
        saveUserLanguage(settings.language, actorUid)

        val personalName = if (isOwner) scoped.ownerName.trim() else scoped.activePartnerName.trim()
        val profileActorUid = actorUid.orEmpty()
        if (wsId.isNotBlank() && profileActorUid.isNotBlank() && personalName.isNotBlank()) {
            // Keep the active workspace membership mirror current immediately. The cloud
            // membership write below remains the cross-session source of truth.
            _workspaceMembers.value = _workspaceMembers.value.map { member ->
                if (member.uid == profileActorUid) member.copy(displayName = personalName) else member
            }
            _activeWorkspaceMembers.value = _activeWorkspaceMembers.value.map { member ->
                if (member.uid == profileActorUid) member.copy(displayName = personalName) else member
            }
        }
        if (isReady && wsId.isNotBlank() && profileActorUid.isNotBlank() && personalName.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.updateMemberDisplayName(wsId, profileActorUid, personalName)
                } catch (e: Exception) {
                    Log.w(TAG, "Member display name sync deferred: ${e.message}")
                }
            }
        }

        // Only push shared business profile to cloud workspace if user is the business owner or editing their personal workspace
        val syncState = _settingsSyncState.value
        if (isOwner && isReady && wsId.isNotBlank() && (syncState is SettingsSyncState.LoadedFromCloud || syncState is SettingsSyncState.CreatedInCloud)) {
            scope.launch {
                try {
                    firestoreRepository.saveSettings(wsId, scoped, actorUid)
                } catch (e: Exception) {
                    Log.w(TAG, "Settings update cloud sync deferred: ${e.message}")
                }
            }
        }
    }

    /**
     * Safely retries pushing all locally unsynced records and pending deletions to Cloud.
     * Records are pushed according to their own record.workspaceId.
     */
    suspend fun pushUnsyncedToCloud(targetWorkspaceId: String? = null, isOnline: Boolean = true): SyncResult {
        if (!isOnline) return SyncResult(false, 0, "Offline actions are saved on this device")
        return kotlinx.coroutines.withContext(Dispatchers.IO) { syncMutex.withLock {
            val uid = getEffectiveActorUid().orEmpty()
            val wsId = targetWorkspaceId ?: _activeWorkspaceId.value.orEmpty()
            if (uid.isBlank() || wsId.isBlank() || wsId != _activeWorkspaceId.value) return@withLock SyncResult(false, 0, "No authorized active workspace")
            val verifiedRole: String
            val members: List<WorkspaceMember>
            try {
                val role = firestoreRepository.resolveAuthoritativeRole(uid, wsId)
                if (role == null) {
                    context.getSharedPreferences("verified_workspace_session", Context.MODE_PRIVATE).edit().clear().commit()
                    _activeWorkspaceMembers.value = emptyList()
                    _workspaceMembers.value = emptyList()
                    currentUserRole = ""
                    _isCollaborationOwner.value = false
                    stopWorkspaceListeners()
                    _workspaceInitState.value = WorkspaceInitState.Error(SecurityException("Workspace access removed"))
                    return@withLock SyncResult(false, 0, "Workspace access removed; pending actions retained")
                }
                verifiedRole = role
                members = firestoreRepository.getWorkspaceMembers(wsId)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                return@withLock SyncResult(false, 0, "Connection unavailable; pending actions retained")
            }
            if (getEffectiveActorUid() != uid || _activeWorkspaceId.value != wsId)
                return@withLock SyncResult(false, 0, "Session or workspace changed; pending actions retained")
            currentUserRole = verifiedRole
            _isCollaborationOwner.value = verifiedRole == com.example.data.auth.RoleUtils.ROLE_OWNER
            _activeWorkspaceMembers.value = members
            cacheVerifiedMembership(uid, wsId, members)
            // Offline cold start restores Room context without attaching cloud listeners.
            // Reconnect only after authoritative membership and the original session are verified.
            if (firestoreRepository.cloudListenerStates.value[wsId] == null ||
                firestoreRepository.cloudListenerStates.value[wsId] == com.example.data.firebase.CloudConnectionState.IDLE)
                attachRealtimeListenersForVisibleWorkspaces()
            var count = 0
            var failed = operations.rejectedCount(wsId, uid) > 0
            var authenticationMessage: String? = null
            var backendMessage: String? = null
            var blockingMessage: String? = null
            // Recovery failures remain durable and retry separately from permanently rejected mutations.
            for (rejected in operations.outstandingOperations(wsId).filter { it.actorUid == uid && it.state == "REJECTED" &&
                (it.failureReason.contains("Local reconciliation pending") ||
                    (it.entityType == "EXPENSE" && it.mutationType == "DELETE" && it.failureReason.startsWith("Confirmed state restored;"))) }) {
                try {
                    if (reconcileRejectedOperation(rejected)) {
                        clearCompoundDeleteMarkers(rejected)
                        val originalFailure = rejected.failureReason.substringBefore("; Local reconciliation pending")
                            .substringAfterLast("Confirmed state restored; ")
                        operations.waitingRecovery(rejected.operationId, "Confirmed state restored; " + originalFailure)
                    }
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) { /* Keep the warning and original payload; retry recovery next sync. */ }
            }
            var cursor = 0L
            while (true) {
                if (getEffectiveActorUid() != uid || _activeWorkspaceId.value != wsId) return@withLock SyncResult(false, count, "Session or workspace changed; remaining actions retained")
                val batch = operations.pendingAfter(wsId, uid, cursor)
                if (batch.isEmpty()) break
                for (op in batch) {
                    cursor = op.sequence
                    if (getEffectiveActorUid() != uid || _activeWorkspaceId.value != wsId) return@withLock SyncResult(false, count, "Session or workspace changed; remaining actions retained")
                    var syncStage = "SERVER_MEMBERSHIP"
                    try {
                        val freshRole = firestoreRepository.resolveAuthoritativeRole(uid, wsId) ?: throw SecurityException("Workspace access removed")
                        val freshMembers = firestoreRepository.getWorkspaceMembers(wsId)
                    val refs = com.example.data.sync.OperationDependencies.references(op)
                    var waiting = false
                    var rejectedPredecessor = false
                    for ((type, id) in refs) if (operations.unresolvedPredecessors(wsId, type, id, op.sequence) > 0) {
                        waiting = true
                        if (operations.rejectedPredecessors(wsId, type, id, op.sequence) > 0) {
                            rejectedPredecessor = true
                            break
                        }
                    }
                    if (waiting) {
                        val reason = if (rejectedPredecessor)
                            "Blocked by a rejected earlier action. Resolve that action before retrying."
                        else "Waiting for an earlier related action"
                        operations.waiting(op.operationId, reason)
                        if (rejectedPredecessor) blockingMessage = reason
                        failed = true
                        continue
                    }
                        val linkedJobId = if (op.entityType == "EXPENSE") org.json.JSONObject(op.payloadJson).optLong("relatedJobId") else 0L
                        val localLinkedJob = if (linkedJobId > 0) jobEntryDao.getJobById(wsId, linkedJobId) else null
                        val linkedJob = if (op.entityType == "EXPENSE") com.example.data.sync.resolveExpenseAuthorizationJob(
                            op, localLinkedJob, firestoreRepository::getOperationRecordFromServer) else null
                        syncStage = "LOCAL_POLICY"
                        com.example.data.sync.OperationPolicy.requireAuthorized(op, freshRole, freshMembers, linkedJob)
                        syncStage = "FIRESTORE_COMMIT"
                        var attempt = 0
                        while (true) {
                            try {
                                if (getEffectiveActorUid() != uid || _activeWorkspaceId.value != wsId) throw kotlinx.coroutines.CancellationException("Sync context changed")
                                firestoreRepository.applyPendingOperation(op)
                                break
                            } catch (e: kotlinx.coroutines.CancellationException) { throw e
                            } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
                                if (e.code !in setOf(com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE, com.google.firebase.firestore.FirebaseFirestoreException.Code.ABORTED, com.google.firebase.firestore.FirebaseFirestoreException.Code.DEADLINE_EXCEEDED, com.google.firebase.firestore.FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED) || attempt >= 2) throw e
                                kotlinx.coroutines.delay(1000L shl attempt++)
                            }
                        }
                        acknowledge(op); count++
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e
                    } catch (e: Exception) {
                        if (e is com.example.data.firebase.WorkspaceAuthenticationRequiredException || e is com.example.data.firebase.WorkspaceSessionChangedException) authenticationMessage = e.message
                        if (e is com.example.data.firebase.WorkspaceBackendUnavailableException) backendMessage = e.message
                        val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code
                        val permanent = com.example.data.sync.SyncFailurePolicy.permanent(e)
                        val failure = (e.message ?: "Sync failed") + "; " + com.example.data.sync.SyncAuthorizationDiagnostic.describe(
                            op, getEffectiveActorUid(), syncStage)
                        operations.failed(op.operationId, if (permanent) "REJECTED" else "PENDING", failure)
                        if (permanent && op.entityType in setOf("JOB", "CUSTOMER", "EXPENSE", "WITHDRAWAL", "PAYMENT") && e !is com.example.data.firebase.WorkspaceUpgradeRequiredException) {
                            var recovered = false
                            try { recovered = reconcileRejectedOperation(op) }
                            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                            catch (_: Exception) { }
                            if (recovered) {
                                clearCompoundDeleteMarkers(op)
                                operations.waitingRecovery(op.operationId, "Confirmed state restored; " + failure)
                            }
                            if (!recovered) operations.waitingRecovery(op.operationId, failure + "; Local reconciliation pending — reconnect to restore shared state")
                        }
                        failed = true
                    }
                }
                kotlinx.coroutines.yield()
            }
            val retainedLocalCount = jobEntryDao.getUnsyncedJobsForWorkspace(wsId).size +
                expenseDao.getUnsyncedExpensesForWorkspace(wsId).size +
                withdrawalDao.getUnsyncedWithdrawalsForWorkspace(wsId).size +
                customerDao.getUnsyncedCustomersForWorkspace(wsId).size +
                paymentDao.getUnsyncedPaymentsForWorkspace(wsId).size
            val needsReview = operations.outstandingOperations(wsId).any { it.actorUid == uid && it.state == "REJECTED" }
            SyncResult(!failed && retainedLocalCount == 0 && !needsReview, count,
                if (failed) authenticationMessage ?: backendMessage ?: blockingMessage ?: "An action could not sync; check pending actions"
                else if (retainedLocalCount > 0 || needsReview) "Uploaded $count actions; remaining local records need sync verification. Original data retained."
                else "Synced $count actions")
        } }
    }

    companion object {
        @Volatile
        private var INSTANCE: WorkspaceRepository? = null

        fun getInstance(context: Context, database: AppDatabase): WorkspaceRepository {
            return INSTANCE ?: synchronized(this) {
                val firestoreRepo = FirestoreRepository(context.applicationContext)
                val instance = WorkspaceRepository(context.applicationContext, database, firestoreRepo)
                INSTANCE = instance
                instance
            }
        }
    }
}
