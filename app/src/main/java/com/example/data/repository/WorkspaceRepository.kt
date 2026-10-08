package com.example.data.repository

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
import com.example.data.entity.ChecklistItemEntity
import com.example.data.entity.WorkTypeExtensionEntity
import com.example.data.firebase.FirestoreRepository
import com.example.data.firebase.UserProfile
import com.example.data.firebase.AccountLookupResult
import com.example.data.firebase.Workspace
import com.example.data.firebase.WorkspaceInvitation
import com.example.data.firebase.WorkspaceMember
import com.example.data.firebase.appSettingsFromFirestoreMap
import com.example.data.sync.PendingDeleteManager
import com.example.data.util.IdGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class WorkspaceInitState {
    object Uninitialized : WorkspaceInitState()
    object Loading : WorkspaceInitState()
    data class Ready(val workspaceId: String) : WorkspaceInitState()
    data class Error(val exception: Throwable) : WorkspaceInitState()
}

sealed interface SettingsSyncState {
    object Uninitialized : SettingsSyncState
    object Loading : SettingsSyncState
    data class LoadedFromCloud(val settings: AppSettingsEntity) : SettingsSyncState
    data class CreatedInCloud(val settings: AppSettingsEntity) : SettingsSyncState
    data class Error(val message: String) : SettingsSyncState
}

class WorkspaceRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val firestoreRepository: FirestoreRepository,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val TAG = "WorkspaceRepository"

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

    private val refreshMembersMutex = kotlinx.coroutines.sync.Mutex()

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

    private val _activeWorkspaceId = MutableStateFlow<String?>(null)
    val activeWorkspaceId: StateFlow<String?> = _activeWorkspaceId.asStateFlow()

    private val _settingsSyncState = MutableStateFlow<SettingsSyncState>(SettingsSyncState.Uninitialized)
    val settingsSyncState: StateFlow<SettingsSyncState> = _settingsSyncState.asStateFlow()

    private val _pendingInvitations = MutableStateFlow<List<WorkspaceInvitation>>(emptyList())
    val pendingInvitations: StateFlow<List<WorkspaceInvitation>> = _pendingInvitations.asStateFlow()

    private val _workspaceMembers = MutableStateFlow<List<WorkspaceMember>>(emptyList())
    val workspaceMembers: StateFlow<List<WorkspaceMember>> = _workspaceMembers.asStateFlow()

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
        if (wsId.isBlank()) return
        if (!isCloudReady()) return
        try {
            val (percentageMap, detailMap) = firestoreRepository.getProfitShareAllocations(wsId)
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

    /**
     * Migrates legacy name-based partnerPercentages to UID-based profitShareAllocations.
     * Uses workspaceMembers to resolve names to UIDs. Skips ambiguous names.
     */
    suspend fun migrateLegacyPercentagesToUidAllocations(wsId: String) {
        if (wsId.isBlank() || _profitShareMigrationDone.value) return
        
        val legacyPercentages = _partnerPercentages.value
        if (legacyPercentages.isEmpty()) {
            _profitShareMigrationDone.value = true
            return
        }

        // Check if we already have UID-based allocations
        if (_profitShareAllocations.value.isNotEmpty()) {
            _profitShareMigrationDone.value = true
            return
        }

        val members = workspaceMembers.value
        val memberByName = members.associateBy { 
            it.displayName?.trim() ?: it.email?.trim() ?: ""
        }.filter { (name, _) -> name.isNotBlank() }
        
        // Also check local partners as fallback
        val localPartners = partnerDao.getPartnersForWorkspace(wsId).firstOrNull() ?: emptyList()
        val partnerByName = localPartners.associateBy { it.name.trim() }
        
        val newAllocations = mutableMapOf<String, Int>()
        val newDetails = mutableMapOf<String, com.example.data.firebase.ProfitShareAllocation>()
        var migratedCount = 0
        
        legacyPercentages.forEach { (name, percentage) ->
            val cleanName = name.trim()
            // Try to resolve UID from workspace members first (authoritative)
            val member = memberByName[cleanName]
            var resolvedUid = member?.uid
            var resolvedName = member?.displayName ?: member?.email ?: cleanName
            var resolvedRole = member?.role ?: "partner"
            
            // Fallback to local partner
            if (resolvedUid.isNullOrBlank()) {
                val partner = partnerByName[cleanName]
                resolvedUid = partner?.let { 
                    // Try to find matching member by phone
                    members.find { m -> 
                        m.phoneNumber?.filter { it.isDigit() }?.takeLast(10) == it.phone.filter { it.isDigit() }?.takeLast(10)
                    }?.uid
                }
                resolvedName = partner?.name ?: cleanName
                resolvedRole = partner?.role?.lowercase() ?: "partner"
            }
            
            val targetUid = resolvedUid
            if (!targetUid.isNullOrBlank()) {
                newAllocations[targetUid] = percentage
                newDetails[targetUid] = com.example.data.firebase.ProfitShareAllocation(
                    participantUid = targetUid,
                    percentage = percentage,
                    displayName = resolvedName ?: cleanName,
                    role = resolvedRole ?: "partner",
                    isActive = true
                )
                migratedCount++
                Log.d(TAG, "Migrated legacy percentage: $cleanName -> UID $targetUid ($percentage%)")
            } else {
                Log.w(TAG, "Could not resolve UID for legacy percentage entry: $cleanName ($percentage%)")
            }
        }
        
        if (newAllocations.isNotEmpty()) {
            _profitShareAllocations.value = newAllocations
            _profitShareAllocationDetails.value = newDetails
            
            // Cache locally
            try {
                val prefs = context.getSharedPreferences("workspace_profit_share_prefs", android.content.Context.MODE_PRIVATE)
                val jsonObj = org.json.JSONObject()
                newAllocations.forEach { (uid, percentage) ->
                    val detail = newDetails[uid]
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
                Log.w(TAG, "Error caching migrated profit share allocations: ${e.message}")
            }
            
            // Sync to cloud
            if (isCloudReady()) {
                try {
                    firestoreRepository.saveProfitShareAllocations(wsId, newAllocations, newDetails)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed syncing migrated profit share allocations to cloud: ${e.message}")
                }
            }
        }
        
        _profitShareMigrationDone.value = true
        Log.i(TAG, "Legacy percentage migration complete for $wsId: $migratedCount/${legacyPercentages.size} entries migrated")
    }

    private val _genuinePartnerWorkspaces = MutableStateFlow<List<Workspace>>(emptyList())
    val genuinePartnerWorkspaces: StateFlow<List<Workspace>> = _genuinePartnerWorkspaces.asStateFlow()

    private var activeUid: String? = null
    private var currentUserRole: String? = null

    init {
        try {
            val initialUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            if (!initialUid.isNullOrBlank()) {
                activeUid = initialUid
                val defaultWsId = "ws_${initialUid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"
                _personalWorkspaceId.value = defaultWsId
                _visibleWorkspaceIds.value = setOf(defaultWsId)
                _activeWorkspaceId.value = defaultWsId
                _isInitialized.value = true
                _workspaceInitState.value = WorkspaceInitState.Ready(defaultWsId)
            }
        } catch (_: Exception) {}
    }

    fun getActiveUid(): String? = getEffectiveActorUid()

    fun getEffectiveActorUid(): String? {
        val currentAuthUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (!currentAuthUid.isNullOrBlank()) {
            activeUid = currentAuthUid
            return currentAuthUid
        }
        return activeUid
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
            action = "PARTNER",
            entityType = "PARTNER",
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
    suspend fun initializeForUser(userProfile: UserProfile): Result<String> = kotlinx.coroutines.withContext(Dispatchers.IO) {
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
        currentUserRole = userProfile.role

        val rawPhone = userProfile.phoneNumber ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.phoneNumber ?: ""
        val verifiedPhone = normalizePhoneNumber(rawPhone)
        var activeProfile = userProfile

        // AUTHORITATIVE MEMBERSHIP RESOLUTION (must run BEFORE any workspace bootstrap).
        // workspaceId (WHERE the user belongs) and role (WHAT the user may do) are read
        // exclusively from workspaces/{workspaceId}/members/{uid}. Ownership of the workspace
        // document is NEVER used to infer a role.
        var authoritativeWsId: String? = null
        var authoritativeRole: String? = null
        try {
            val membership = firestoreRepository.resolveAuthoritativeMembership(uid)
            if (membership != null) {
                authoritativeWsId = membership.workspaceId
                authoritativeRole = membership.role
                Log.i(
                    "TRAC_ROLE",
                    "Authoritative membership uid=$uid ws=${membership.workspaceId} role=${membership.role}"
                )
            } else {
                Log.d("TRAC_ROLE", "No membership record found for uid=$uid")
            }
        } catch (e: Exception) {
            Log.w(TAG, "resolveAuthoritativeMembership failed for uid=$uid: ${e.message}")
        }

        // A Partner may be mis-provisioned from an earlier regression into their own
        // ws_{uid} workspace with role=owner. If the profile points at a canonical
        // self-workspace but a real Owner membership exists elsewhere, prefer the membership.
        val profileWsId = activeProfile.defaultWorkspaceId?.ifBlank { null }
        val canonicalSelfWsId = "ws_${uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"
        val profileIsCanonicalSelfWs = profileWsId == null || profileWsId == canonicalSelfWsId
        if (authoritativeWsId != null && profileIsCanonicalSelfWs &&
            authoritativeWsId != canonicalSelfWsId &&
            !authoritativeRole.equals("owner", ignoreCase = true)
        ) {
            Log.i(
                "TRAC_WORKSPACE",
                "Replacing self-workspace $profileWsId with Owner membership workspace $authoritativeWsId role=$authoritativeRole"
            )
            activeProfile = activeProfile.copy(
                defaultWorkspaceId = authoritativeWsId,
                workspaces = listOf(authoritativeWsId),
                role = authoritativeRole
            )
            currentUserRole = authoritativeRole
        } else if (authoritativeWsId != null && authoritativeWsId == profileWsId) {
            currentUserRole = authoritativeRole
        }

        // MANDATORY RESOLUTION FLOW: Check if this user was invited by an Owner before bootstrapping.
        // Conditions to attempt re-resolution:
        //   a) No workspace ID set yet (new user), OR
        //   b) Workspace ID is the canonical self-workspace for this UID (hasn't been bound to owner yet), OR
        //   c) The profile role is explicitly "partner" (returning Partner — re-validate binding to preserve role)
        val isExplicitPartnerRole = (activeProfile.role ?: "").equals("partner", ignoreCase = true) ||
                                    (currentUserRole ?: "").equals("partner", ignoreCase = true)
        val needsPartnerResolution = activeProfile.defaultWorkspaceId.isNullOrBlank() ||
                                     activeProfile.defaultWorkspaceId!!.startsWith("ws_$uid") ||
                                     isExplicitPartnerRole
        if (needsPartnerResolution && verifiedPhone.isNotBlank() && authoritativeWsId == null) {
            try {
                val bound = firestoreRepository.resolveAndBindPartnerInvitation(
                    userUid = uid,
                    rawPhone = verifiedPhone,
                    userDisplayName = activeProfile.displayName,
                    knownWorkspaceIds = firestoreRepository.getAllKnownWorkspaceIdsFromDisk()
                )
                if (bound != null) {
                    Log.i("TRAC_PARTNER", "initializeForUser resolved partner invitation into ws=${bound.defaultWorkspaceId} role=${bound.role}")
                    activeProfile = bound
                    currentUserRole = "partner"
                } else if (isExplicitPartnerRole) {
                    // Returning Partner where invitation is already consumed — keep role explicit
                    Log.d("TRAC_PARTNER", "Returning Partner uid=$uid: invitation already consumed, preserving partner role")
                    currentUserRole = "partner"
                }
            } catch (e: Exception) {
                Log.w(TAG, "resolveAndBindPartnerInvitation check failed in initializeForUser: ${e.message}")
                if (isExplicitPartnerRole) {
                    currentUserRole = "partner" // always preserve partner role even on error
                }
            }
        }

        // Deterministic fallback workspace ID based on UID
        val canonicalWsId = activeProfile.defaultWorkspaceId?.ifBlank { null }
            ?: "ws_${uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"

        // 1. INSTANT LOCAL STARTUP: Immediately prime local workspace state from Room/Cache so UI renders immediately
        val isPreResolvedPartner = (authoritativeRole ?: "").equals("partner", ignoreCase = true) ||
            (authoritativeRole ?: "").equals("operator", ignoreCase = true) ||
            isExplicitPartnerRole
        val initialWsId = canonicalWsId
        _personalWorkspaceId.value = if (!isPreResolvedPartner) canonicalWsId else null
        _visibleWorkspaceIds.value = setOf(initialWsId)
        _activeWorkspaceId.value = initialWsId
        _isInitialized.value = true
        _workspaceInitState.value = WorkspaceInitState.Loading

        return@withContext try {
            Log.d(TAG, "Resolving deterministic cloud workspace for user UID: $uid")

            // 1-7. Resolve/create, verify and write users/{uid}.defaultWorkspaceId on Firestore.
            // When an authoritative membership already exists, bootstrap verifies that
            // membership and NEVER creates a workspace or rewrites defaultWorkspaceId.
            val bootstrapPreResolvedWs = authoritativeWsId
                ?: activeProfile.defaultWorkspaceId?.ifBlank { null }
            val bootstrapPreResolvedRole = authoritativeRole ?: activeProfile.role
            val workspace = firestoreRepository.bootstrapWorkspaceForUser(
                user = activeProfile,
                preResolvedWorkspaceId = bootstrapPreResolvedWs,
                preResolvedRole = bootstrapPreResolvedRole
            )
            val resolvedWsId = workspace.workspaceId.ifBlank { canonicalWsId }
            _personalWorkspaceId.value = if (!isPreResolvedPartner) resolvedWsId else null

            // Auto-connect if this verified phone number was previously added by an owner while unregistered
            if (verifiedPhone.isNotBlank()) {
                try {
                    val connected = firestoreRepository.findAndConnectPendingPartnerLinks(
                        userUid = uid,
                        verifiedPhone = verifiedPhone,
                        userWorkspaceId = resolvedWsId,
                        userDisplayName = activeProfile.displayName
                    )
                    if (connected.isNotEmpty()) {
                        Log.d("TRAC_PARTNER", "Auto-connected to groups on login: $connected")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "findAndConnectPendingPartnerLinks error: ${e.message}")
                }
            }

            // Discover genuine partner workspaces strictly
            val genuinePartners = getGenuinePartnerWorkspaces(activeProfile, resolvedWsId)
            _genuinePartnerWorkspaces.value = genuinePartners

            // ROLE RESOLUTION IS AUTHORITATIVE AND MEMBERSHIP-DRIVEN.
            // workspaceId (WHERE) and role (WHAT) are fully independent values.
            val targetWsId = authoritativeWsId
                ?: activeProfile.defaultWorkspaceId?.ifBlank { null }
                ?: resolvedWsId

            // Re-read the authoritative membership after bootstrap so an Owner keeps OWNER
            // and a Partner keeps PARTNER even though both share the same workspaceId.
            val finalRole = try {
                firestoreRepository.resolveAuthoritativeRole(uid, targetWsId)
            } catch (_: Exception) {
                null
            } ?: authoritativeRole ?: activeProfile.role

            val normalizedFinalRole = com.example.data.auth.RoleUtils.normalizeRole(finalRole)
            val isOwner = normalizedFinalRole == com.example.data.auth.RoleUtils.ROLE_OWNER ||
                normalizedFinalRole == com.example.data.auth.RoleUtils.ROLE_CO_OWNER
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

            // Load or initialize cloud settings before enabling writes
            val currentLocal = appSettingsDao.getSettingsForWorkspaceOnce(resolvedActiveWsId)
                ?: AppSettingsEntity(
                    workspaceId = resolvedActiveWsId,
                    businessName = if (isOwner) (activeProfile.displayName ?: "") else (genuinePartners.find { it.workspaceId == resolvedActiveWsId }?.name ?: workspace.name)
                )
            val resolvedSettings = firestoreRepository.fetchOrCreateWorkspaceSettings(
                workspaceId = resolvedActiveWsId,
                uid = uid,
                localSettings = currentLocal
            )

            // Ensure business name is populated from Owner workspace if missing in settings document
            val effectiveBusinessName = resolvedSettings.businessName.ifBlank {
                workspace.name.ifBlank {
                    genuinePartners.find { it.workspaceId == resolvedActiveWsId }?.name ?: ""
                }
            }

            // Load profit share allocations from cloud (after settings are fetched)
            scope.launch {
                loadProfitShareAllocationsFromCloud(resolvedActiveWsId)
                // Migrate legacy name-based percentages to UID-based allocations
                migrateLegacyPercentagesToUidAllocations(resolvedActiveWsId)
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
            appSettingsDao.insertOrUpdateSettings(mergedSettings)
            _settingsSyncState.value = SettingsSyncState.LoadedFromCloud(mergedSettings)

            // Safe migration on first login if personal workspace has no remote records yet
            if (isOwner) {
                firestoreRepository.migrateLocalDataIfRequired(resolvedWsId, uid, database)
            }

            _workspaceInitState.value = WorkspaceInitState.Ready(resolvedActiveWsId)

            // Start real-time snapshot listeners for all visible workspaces
            attachRealtimeListenersForVisibleWorkspaces()

            // Start listener for collaboration groups discovery
            listenToCollaborationGroupsDiscovery(uid, resolvedWsId)

            refreshWorkspaceMembers(resolvedActiveWsId)

            // Run pushUnsyncedToCloud()
            pushUnsyncedToCloud(resolvedActiveWsId)

            Result.success(resolvedActiveWsId)
        } catch (e: Throwable) {
            Log.w(TAG, "Cloud workspace bootstrap deferred (offline fallback): ${e.message}")
            val fallbackWsId = canonicalWsId
            val isPartnerFallback = (currentUserRole ?: "").equals("partner", ignoreCase = true) || (activeProfile.role ?: "").equals("partner", ignoreCase = true)
            _personalWorkspaceId.value = if (!isPartnerFallback) fallbackWsId else null
            _isCollaborationOwner.value = !isPartnerFallback
            _visibleWorkspaceIds.value = setOf(fallbackWsId)
            _activeWorkspaceId.value = fallbackWsId
            _genuinePartnerWorkspaces.value = emptyList()

            val fallbackSettings = appSettingsDao.getSettingsForWorkspaceOnce(fallbackWsId)
                ?: AppSettingsEntity(workspaceId = fallbackWsId, businessName = activeProfile.displayName ?: "")
            appSettingsDao.insertOrUpdateSettings(fallbackSettings)

            _isInitialized.value = true
            _workspaceInitState.value = WorkspaceInitState.Ready(fallbackWsId)
            _settingsSyncState.value = SettingsSyncState.LoadedFromCloud(fallbackSettings)

            Result.success(fallbackWsId)
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
                            refreshWorkspaceMembers(_activeWorkspaceId.value)
                        }
                    }
                    if (listener != null) {
                        groupMembersListeners[groupId] = listener
                    }
                }
                if (!groupPendingListeners.containsKey(groupId)) {
                    val pListener = firestoreRepository.listenToCollaborationGroupPendingPhones(groupId) {
                        scope.launch {
                            refreshWorkspaceMembers(_activeWorkspaceId.value)
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
        val visibleIds = _visibleWorkspaceIds.value
        if (visibleIds.isEmpty()) {
            return
        }
        firestoreRepository.startRealtimeListenersForWorkspaces(
            workspaceIds = visibleIds,
            onJobsUpdated = { remoteJobs, listenerWsId ->
                scope.launch {
                    syncRemoteJobsToLocal(remoteJobs, listenerWsId)
                }
            },
            onExpensesUpdated = { remoteExpenses, listenerWsId ->
                scope.launch {
                    syncRemoteExpensesToLocal(remoteExpenses, listenerWsId)
                }
            },
            onCustomersUpdated = { remoteCustomers, listenerWsId ->
                scope.launch {
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
                scope.launch {
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
                scope.launch {
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
                    workTypeExtensionDao.insertAll(remoteExtensions)
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
        val syncedPayments = remotePayments.map { it.copy(isSynced = true) }
        paymentDao.insertPayments(syncedPayments)

        // Reconciliation: remove local synced payments that are no longer in the remote list
        val validRemoteIds = remotePayments.map { it.id }
        if (validRemoteIds.isNotEmpty()) {
            paymentDao.deleteSyncedNotIn(listenerWsId, validRemoteIds)
        } else {
            paymentDao.deleteAllForWorkspace(listenerWsId)
        }
    }

    /**
     * Cleanly detaches all cloud snapshot listeners on logout.
     */
    fun stopWorkspaceListeners() {
        firestoreRepository.stopRealtimeListeners()
        collaborationIndexListener?.remove()
        collaborationIndexListener = null
        groupMembersListeners.values.forEach { it.remove() }
        groupMembersListeners.clear()
        groupPendingListeners.values.forEach { it.remove() }
        groupPendingListeners.clear()
        groupWorkspacesMap.clear()
        _workspaceMembers.value = emptyList()
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
        Log.d(TAG, "Workspace listeners stopped.")
    }

    // --- Remote to Local Synchronization Helpers (ID-based Reconciliation) ---

    private suspend fun syncRemoteJobsToLocal(remoteJobs: List<JobEntryEntity>, listenerWsId: String) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (activeUid != currentUid || !_visibleWorkspaceIds.value.contains(listenerWsId)) {
            Log.w(TAG, "Ignoring jobs snapshot from $listenerWsId because it is not in visible workspaces")
            return
        }
        try {
            val pendingDeleteIds = pendingDeleteManager.getPendingDeleteIds("JOB", listenerWsId)
            val unsyncedLocalJobIds = jobEntryDao.getUnsyncedJobs().filter { it.workspaceId == listenerWsId }.map { it.id }.toSet()
            val validRemoteJobs = remoteJobs.filterNot { pendingDeleteIds.contains(it.id) }

            for (job in validRemoteJobs) {
                if (unsyncedLocalJobIds.contains(job.id)) {
                    // Do not overwrite pending local edits
                    continue
                }
                jobEntryDao.insertJob(job.copy(workspaceId = listenerWsId, isSynced = true))
            }

            val validRemoteIds = validRemoteJobs.map { it.id }
            if (validRemoteIds.isNotEmpty()) {
                jobEntryDao.deleteSyncedNotIn(listenerWsId, validRemoteIds)
            } else {
                jobEntryDao.deleteAllSyncedForWorkspace(listenerWsId)
            }

            // Keep local customer totals synchronized with freshly synced jobs
            val allLocalJobs = jobEntryDao.getAllJobs().firstOrNull() ?: emptyList()
            val localCustomers = customerDao.getCustomersForWorkspace(listenerWsId).firstOrNull() ?: emptyList()
            for (c in localCustomers) {
                val stats = com.example.ui.util.FinancialCalculationEngine.calculateCustomerFinancials(c, allLocalJobs)
                if (stats.totalBilled != c.totalBilled || stats.totalPaid != c.totalPaid || stats.balanceDue != c.balanceDue) {
                    customerDao.updateCustomer(
                        c.copy(
                            totalBilled = stats.totalBilled,
                            totalPaid = stats.totalPaid,
                            balanceDue = stats.balanceDue,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote jobs to local: ${e.message}")
        }
    }

    private suspend fun syncRemoteExpensesToLocal(remoteExpenses: List<ExpenseEntity>, listenerWsId: String) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (activeUid != currentUid || !_visibleWorkspaceIds.value.contains(listenerWsId)) {
            Log.w(TAG, "Ignoring expenses snapshot from $listenerWsId because it is not in visible workspaces")
            return
        }
        try {
            val pendingDeleteIds = pendingDeleteManager.getPendingDeleteIds("EXPENSE", listenerWsId)
            val unsyncedLocalExpIds = expenseDao.getUnsyncedExpenses().filter { it.workspaceId == listenerWsId }.map { it.id }.toSet()
            val validRemoteExpenses = remoteExpenses.filterNot { pendingDeleteIds.contains(it.id) }

            for (expense in validRemoteExpenses) {
                if (unsyncedLocalExpIds.contains(expense.id)) {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote expenses to local: ${e.message}")
        }
    }

    private suspend fun syncRemoteCustomersToLocal(remoteCustomers: List<CustomerEntity>, listenerWsId: String) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (activeUid != currentUid || !_visibleWorkspaceIds.value.contains(listenerWsId)) {
            Log.w(TAG, "Ignoring customers snapshot from $listenerWsId because it is not in visible workspaces")
            return
        }
        try {
            val pendingDeleteIds = pendingDeleteManager.getPendingDeleteIds("CUSTOMER", listenerWsId)
            val unsyncedLocalCustIds = customerDao.getUnsyncedCustomers().filter { it.workspaceId == listenerWsId }.map { it.id }.toSet()
            val validRemoteCustomers = remoteCustomers.filterNot { pendingDeleteIds.contains(it.id) }

            val allJobs = jobEntryDao.getAllJobs().firstOrNull() ?: emptyList()
            for (customer in validRemoteCustomers) {
                if (unsyncedLocalCustIds.contains(customer.id)) {
                    // Do not overwrite pending local edits
                    continue
                }
                val stats = com.example.ui.util.FinancialCalculationEngine.calculateCustomerFinancials(customer, allJobs)
                customerDao.insertCustomer(
                    customer.copy(
                        workspaceId = listenerWsId,
                        totalBilled = stats.totalBilled,
                        totalPaid = stats.totalPaid,
                        balanceDue = stats.balanceDue,
                        isSynced = true
                    )
                )
            }

            val validRemoteIds = validRemoteCustomers.map { it.id }
            if (validRemoteIds.isNotEmpty()) {
                customerDao.deleteSyncedNotIn(listenerWsId, validRemoteIds)
            } else {
                customerDao.deleteAllSyncedForWorkspace(listenerWsId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote customers to local: ${e.message}")
        }
    }

    private suspend fun syncRemoteTractorsToLocal(remoteTractors: List<TractorEntity>, listenerWsId: String) {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (activeUid != currentUid || !_visibleWorkspaceIds.value.contains(listenerWsId)) {
            Log.w(TAG, "Ignoring tractors snapshot from $listenerWsId because it is not in visible workspaces")
            return
        }
        try {
            val pendingDeleteIds = pendingDeleteManager.getPendingDeleteIds("TRACTOR", listenerWsId)
            val validRemoteTractors = remoteTractors.filterNot { pendingDeleteIds.contains(it.id) }

            for (tractor in validRemoteTractors) {
                tractorDao.insertTractor(tractor.copy(workspaceId = listenerWsId))
            }

            val validRemoteIds = validRemoteTractors.map { it.id }
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
            val pendingDeleteIds = pendingDeleteManager.getPendingDeleteIds("PARTNER", listenerWsId)
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
            val pendingDeleteIds = pendingDeleteManager.getPendingDeleteIds("WITHDRAWAL", listenerWsId)
            val unsyncedLocalWthIds = withdrawalDao.getUnsyncedWithdrawals().filter { it.workspaceId == listenerWsId }.map { it.id }.toSet()
            val validRemoteWithdrawals = remoteWithdrawals.filterNot { pendingDeleteIds.contains(it.id) }

            for (withdrawal in validRemoteWithdrawals) {
                if (unsyncedLocalWthIds.contains(withdrawal.id)) {
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
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing remote withdrawals to local: ${e.message}")
        }
    }

    private suspend fun getOrResolveWorkspaceId(): String? {
        val current = _activeWorkspaceId.value
        if (!current.isNullOrBlank()) return current
        return null
    }

    // --- CRUD Bridge (Local + Cloud with Collision-Resistant IDs) ---

    suspend fun saveJobEntry(
        job: JobEntryEntity,
        linkedExpense: ExpenseEntity? = null,
        linkedExpenses: List<ExpenseEntity> = emptyList()
    ): Long {
        Log.d("TRAC_AUTH", "activeUid=$activeUid")
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: ""
        val existingJob = if (job.id > 0) jobEntryDao.getJobById(job.id) else null
        var customerId = job.customerId
        if (customerId <= 0) {
            if (existingJob != null && existingJob.customerId > 0) {
                customerId = existingJob.customerId
            } else {
                customerId = addOrFindCustomer(job.customerName, job.customerPhone, job.customerLocation)
            }
        }

        // Determine authorized person attribution
        val isOwner = isOwnerOfWorkspace(wsId)
        val appSettings = appSettingsDao.getSettings().firstOrNull()
        val currentActorName: String = if (isOwner) {
            // Owner is authorized to record as self or on behalf of an active registered partner
            val candidateName = job.addedByPartner.ifBlank { job.operatorName }
            if (candidateName.isNotBlank()) {
                candidateName
            } else {
                appSettings?.ownerName?.ifBlank { null } ?: appSettings?.activePartnerName?.ifBlank { null } ?: "Owner"
            }
        } else {
            // PARTNER: MUST ALWAYS record entries under their own authenticated identity
            appSettings?.activePartnerName?.ifBlank { null } ?: "Partner"
        }

        val actorUid = getEffectiveActorUid() ?: ""
        if (existingJob != null) {
            if (!isOwner && existingJob.createdByUid.isNotBlank() && existingJob.createdByUid != actorUid) {
                Log.w(TAG, "Security rejection: Non-owner ($currentActorName, uid=$actorUid) cannot edit another person's entry (${existingJob.createdByUid})")
                throw SecurityException("Unauthorized: You can only edit your own entries")
            }
            val currentMember = workspaceMembers.value.firstOrNull { it.uid == actorUid }
            val actorRole = if (isOwner) com.example.data.auth.RoleUtils.ROLE_OWNER else (currentMember?.role?.takeIf { it.isNotBlank() }?.let { com.example.data.auth.RoleUtils.normalizeRole(it) } ?: com.example.data.auth.RoleUtils.ROLE_PARTNER)
            if (!com.example.data.auth.AuthorizationManager.canEditEntry(existingJob, isOwner, currentActorName, actorUid, role = actorRole)) {
                Log.w(TAG, "Security rejection: User ($currentActorName, isOwner=$isOwner, uid=$actorUid, role=$actorRole) is not authorized to edit entry ${existingJob.id}")
                throw SecurityException("Unauthorized to edit entry ${existingJob.id}")
            }
        } else {
            val currentMember = workspaceMembers.value.firstOrNull { it.uid == actorUid }
            val actorRole = if (isOwner) com.example.data.auth.RoleUtils.ROLE_OWNER else (currentMember?.role?.takeIf { it.isNotBlank() }?.let { com.example.data.auth.RoleUtils.normalizeRole(it) } ?: com.example.data.auth.RoleUtils.ROLE_PARTNER)
            if (!isOwner && job.createdByUid.isNotBlank() && job.createdByUid != actorUid) {
                Log.w(TAG, "Security rejection: Non-owner ($currentActorName, uid=$actorUid) cannot create entry for another person (${job.createdByUid})")
                throw SecurityException("Unauthorized: Non-owner cannot create an entry for another person")
            }
            val isHistorical = !com.example.data.auth.AuthorizationManager.isSameCalendarDay(job.startTimeMillis)
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
        val finalCreatedAt = existingJob?.createdAt ?: if (job.createdAt > 0) job.createdAt else System.currentTimeMillis()
        val finalAddedBy = if (existingJob != null) {
            existingJob.addedByPartner.ifBlank { job.addedByPartner }
        } else {
            job.addedByPartner.ifBlank { currentActorName }
        }
        val finalCreatedByUid = if (existingJob != null) {
            existingJob.createdByUid.ifBlank { job.createdByUid }
        } else {
            job.createdByUid.ifBlank { actorUid }
        }
        val currentMember = workspaceMembers.value.firstOrNull { it.uid == actorUid }
        val resolvedDefaultRole = if (isOwner) "Owner" else (currentMember?.role?.takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() } ?: "Partner")
        val candidateRole = if (existingJob != null) {
            existingJob.createdByRole.ifBlank { job.createdByRole }.ifBlank { resolvedDefaultRole }
        } else {
            job.createdByRole.ifBlank { resolvedDefaultRole }
        }
        val finalCreatedByRole = if (!isOwner && com.example.data.auth.RoleUtils.isOwner(candidateRole)) "Partner" else candidateRole
        val finalOperator = if (!isOwner) {
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

        val isHistorical = job.notes.contains("Old", ignoreCase = true) ||
                job.notes.contains("Historical", ignoreCase = true) ||
                job.customerName.contains("old", ignoreCase = true) ||
                job.workType.contains("Old", ignoreCase = true) ||
                job.tractorLabel.contains("Old", ignoreCase = true)

        val finalCustomerPhone = if (existingJob != null && job.customerPhone.isBlank() && existingJob.customerPhone.isNotBlank() && job.pendingAmount > 0.0 && !isHistorical) {
            existingJob.customerPhone
        } else {
            job.customerPhone
        }

        // Edited by attribution (Recorded when an existing entry is edited)
        val finalEditedByUid = if (existingJob != null) actorUid else ""
        val finalEditedByName = if (existingJob != null) currentActorName else ""
        val finalEditedByRole = if (existingJob != null) resolvedDefaultRole else ""
        val finalUpdatedAt = if (existingJob != null) System.currentTimeMillis() else 0L

        val localJob = job.copy(
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
            createdAt = finalCreatedAt
        )
        jobEntryDao.insertJob(localJob)

        val allExpensesToSave = mutableListOf<ExpenseEntity>()
        if (linkedExpenses.isNotEmpty()) {
            allExpensesToSave.addAll(linkedExpenses.filter { it.amount > 0 })
        } else if (linkedExpense != null && linkedExpense.amount > 0) {
            allExpensesToSave.add(linkedExpense)
        }
        val distinctExpenses = allExpensesToSave.distinctBy { if (it.id > 0) it.id else "${it.expenseType}_${it.amount}_${it.paidBy}" }

        // Reconcile: delete any existing linked expenses for this job that were removed in the edit form
        val existingLinkedExpenses = if (safeJobId > 0) {
            try { expenseDao.getExpensesForJob(safeJobId) } catch (e: Exception) { emptyList() }
        } else emptyList()
        val newExpenseIds = distinctExpenses.map { it.id }.filter { it > 0 }.toSet()
        val deletedLinkedExpenses = existingLinkedExpenses.filter { it.id !in newExpenseIds }

        for (delExp in deletedLinkedExpenses) {
            expenseDao.deleteExpense(delExp)
            if (wsId.isNotBlank()) {
                pendingDeleteManager.recordPendingDelete("EXPENSE", delExp.id, wsId)
            }
        }

        val savedExpensesList = mutableListOf<ExpenseEntity>()
        for (exp in distinctExpenses) {
            val safeExpId = if (exp.id > 0) exp.id else IdGenerator.generateId()
            val effectivePartner = if (exp.paidBy == "I Paid") {
                if (exp.paidByPartner.isNotBlank()) exp.paidByPartner else finalAddedBy
            } else ""
            val effectivePaidByUid = if (exp.paidBy == "I Paid") {
                exp.paidByUid.ifBlank { finalCreatedByUid }
            } else ""
            val localExp = exp.copy(
                id = safeExpId,
                workspaceId = wsId,
                operatorName = finalOperator,
                addedByPartner = finalAddedBy,
                paidBy = exp.paidBy,
                paidByPartner = effectivePartner,
                paidByUid = effectivePaidByUid,
                createdByUid = exp.createdByUid.ifBlank { finalCreatedByUid },
                createdByRole = exp.createdByRole.ifBlank { finalCreatedByRole },
                relatedJobId = safeJobId,
                isSynced = false
            )
            expenseDao.insertExpense(localExp)
            savedExpensesList.add(localExp)
        }

        recalculateCustomerStats(customerId)

        // Push to Cloud Workspace asynchronously so local write returns immediately
        Log.d("TRAC_WORKSPACE", "workspaceId=$wsId isCloudReady=$isReady")
        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.saveJobEntry(wsId, localJob, activeUid)
                    jobEntryDao.markJobsSynced(listOf(safeJobId))
                    Log.d("TRAC_ENTRY", "marked synced id=$safeJobId")
                } catch (e: Exception) {
                    Log.w("TRAC_FIRESTORE", "Job cloud sync deferred: ${e.message}")
                }

                for (delExp in deletedLinkedExpenses) {
                    try {
                        firestoreRepository.deleteExpense(wsId, delExp.id)
                        pendingDeleteManager.removePendingDelete("EXPENSE", delExp.id, wsId)
                    } catch (e: Exception) {
                        Log.w("TRAC_FIRESTORE", "Deleted linked expense cloud sync deferred: ${e.message}")
                    }
                }

                for (savedExp in savedExpensesList) {
                    try {
                        firestoreRepository.saveExpense(wsId, savedExp, activeUid)
                        expenseDao.markExpensesSynced(listOf(savedExp.id))
                    } catch (e: Exception) {
                        Log.w("TRAC_FIRESTORE", "Linked expense cloud sync deferred: ${e.message}")
                    }
                }
                val cust = customerDao.getCustomerById(customerId)
                if (cust != null) {
                    try {
                        firestoreRepository.saveCustomer(wsId, cust, activeUid)
                        customerDao.markCustomersSynced(listOf(cust.id))
                    } catch (e: Exception) {
                        Log.w("TRAC_FIRESTORE", "Customer cloud sync deferred: ${e.message}")
                    }
                }
            }
        } else {
            Log.d("TRAC_WORKSPACE", "workspace not ready for cloud writes, keeping local isSynced=false")
        }

        return safeJobId
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
        val uid = (actorUid ?: getEffectiveActorUid())?.takeIf { it.isNotBlank() } ?: ""
        val isActiveWs = wsId.isBlank() || wsId == _activeWorkspaceId.value
        val isSelf = actorUid == null || actorUid == getEffectiveActorUid()
        if (isActiveWs && isSelf && !_isCollaborationOwner.value) {
            val sessionRole = currentUserRole?.takeIf { it.isNotBlank() }
            if (sessionRole != null && !com.example.data.auth.RoleUtils.isOwner(sessionRole)) {
                return com.example.data.auth.RoleUtils.normalizeRole(sessionRole)
            }
            return com.example.data.auth.RoleUtils.ROLE_PARTNER
        }
        if (uid.isNotBlank() && wsId.isNotBlank()) {
            workspaceMembers.value.firstOrNull { it.uid == uid }?.role
                ?.takeIf { it.isNotBlank() }
                ?.let { return com.example.data.auth.RoleUtils.normalizeRole(it) }
        }
        val sessionRole = currentUserRole?.takeIf { it.isNotBlank() }
        if (sessionRole != null && isActiveWs) {
            return com.example.data.auth.RoleUtils.normalizeRole(sessionRole)
        }
        return com.example.data.auth.RoleUtils.ROLE_PARTNER
    }

    /**
     * OWNER test. This is a ROLE test, not a workspace-ownership test.
     *
     * A Partner and an Owner resolve to the SAME workspaceId; the membership role alone
     * decides. There is deliberately no `workspaceId == ws_$uid` or `ownerUid == uid`
     * shortcut, because that inference is what misclassified Partners as Owners.
     */
    fun isOwnerOfWorkspace(wsId: String): Boolean {
        if (wsId.isBlank() || wsId == _activeWorkspaceId.value) {
            return _isCollaborationOwner.value
        }
        val resolved = resolveRoleForWorkspace(wsId)
        return resolved == com.example.data.auth.RoleUtils.ROLE_OWNER ||
            resolved == com.example.data.auth.RoleUtils.ROLE_CO_OWNER
    }

    suspend fun deleteJob(job: JobEntryEntity) {
        val wsId = job.workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        val isOwner = isOwnerOfWorkspace(wsId)
        val appSettings = appSettingsDao.getSettings().firstOrNull()
        val currentActorName: String = if (isOwner) {
            appSettings?.ownerName?.ifBlank { null } ?: appSettings?.activePartnerName?.ifBlank { null } ?: "Owner"
        } else {
            appSettings?.activePartnerName?.ifBlank { null } ?: "Partner"
        }

        val actorUid = getEffectiveActorUid() ?: ""
        val currentMember = workspaceMembers.value.firstOrNull { it.uid == actorUid }
        val actorRole = if (isOwner) com.example.data.auth.RoleUtils.ROLE_OWNER else (currentMember?.role?.takeIf { it.isNotBlank() }?.let { com.example.data.auth.RoleUtils.normalizeRole(it) } ?: com.example.data.auth.RoleUtils.ROLE_PARTNER)
        // STRICT SECURITY / ROLE CHECK USING CENTRALIZED AUTHORIZATION
        if (!com.example.data.auth.AuthorizationManager.canDeleteEntry(job, isOwner, currentActorName, actorUid, role = actorRole)) {
            Log.w(TAG, "Security rejection: User ($currentActorName, isOwner=$isOwner, uid=$actorUid, role=$actorRole) is not authorized to delete entry ${job.id} (created by ${job.addedByPartner} at ${job.createdAt})")
            return
        }

        // Atomically retrieve and delete any linked job expenses
        val linkedExpenses = try { expenseDao.getExpensesForJob(job.id) } catch (e: Exception) { emptyList() }
        linkedExpenses.forEach { exp ->
            expenseDao.deleteExpense(exp)
            if (wsId.isNotBlank()) {
                pendingDeleteManager.recordPendingDelete("EXPENSE", exp.id, wsId)
            }
        }

        jobEntryDao.deleteJob(job)
        recalculateCustomerStats(job.customerId)
        if (wsId.isNotBlank()) {
            pendingDeleteManager.recordPendingDelete("JOB", job.id, wsId)
        }

        val isReady = isCloudReady()
        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    linkedExpenses.forEach { exp ->
                        try {
                            firestoreRepository.deleteExpense(wsId, exp.id)
                            pendingDeleteManager.removePendingDelete("EXPENSE", exp.id, wsId)
                        } catch (e: Exception) {
                            Log.w(TAG, "Linked expense delete cloud sync deferred: ${e.message}")
                        }
                    }
                    firestoreRepository.deleteJob(wsId, job.id)
                    pendingDeleteManager.removePendingDelete("JOB", job.id, wsId)
                    val cust = customerDao.getCustomerById(job.customerId)
                    if (cust != null) {
                        firestoreRepository.saveCustomer(wsId, cust, activeUid)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Job delete cloud sync deferred: ${e.message}")
                }
            }
        }
    }

    suspend fun getExpensesForJob(jobId: Long): List<ExpenseEntity> {
        return try {
            expenseDao.getExpensesForJob(jobId)
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addExpense(expense: ExpenseEntity): Long {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: ""
        val safeExpId = if (expense.id > 0) expense.id else IdGenerator.generateId()
        val currentUid = activeUid ?: ""
        val effectiveCreatedByUid = expense.createdByUid.ifBlank { currentUid }
        val effectivePaidByUid = if (expense.paidBy == "I Paid") {
            expense.paidByUid.ifBlank { currentUid }
        } else {
            ""
        }
        val savedExp = expense.copy(
            id = safeExpId,
            workspaceId = wsId,
            createdByUid = effectiveCreatedByUid,
            paidByUid = effectivePaidByUid,
            isSynced = false
        )
        expenseDao.insertExpense(savedExp)

        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.saveExpense(wsId, savedExp, activeUid)
                    expenseDao.markExpensesSynced(listOf(safeExpId))
                } catch (e: Exception) {
                    Log.w("TRAC_FIRESTORE", "Expense cloud sync deferred: ${e.message}")
                }
            }
        }
        return safeExpId
    }

    suspend fun updateExpense(expense: ExpenseEntity) {
        val wsId = expense.workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        val isOwner = isOwnerOfWorkspace(wsId)
        val actorUid = getEffectiveActorUid() ?: ""
        val currentMember = workspaceMembers.value.firstOrNull { it.uid == actorUid }
        val actorRole = if (isOwner) com.example.data.auth.RoleUtils.ROLE_OWNER else (currentMember?.role?.takeIf { it.isNotBlank() }?.let { com.example.data.auth.RoleUtils.normalizeRole(it) } ?: com.example.data.auth.RoleUtils.ROLE_PARTNER)

        if (!com.example.data.auth.AuthorizationManager.canEditExpense(
                expenseCreatedByUid = expense.createdByUid,
                expensePaidByUid = expense.paidByUid,
                expenseCreatedAt = expense.createdAt,
                isOwner = isOwner,
                currentUid = actorUid,
                role = actorRole
        )) {
            Log.w(TAG, "Security rejection: Actor (uid=$actorUid, role=$actorRole, isOwner=$isOwner) is not authorized to edit expense ${expense.id}")
            return
        }

        val isReady = isCloudReady()
        val effectiveCreatedByUid = expense.createdByUid.ifBlank { actorUid }
        val effectivePaidByUid = if (expense.paidBy == "I Paid") {
            expense.paidByUid.ifBlank { actorUid }
        } else {
            ""
        }
        val updated = expense.copy(
            workspaceId = wsId,
            createdByUid = effectiveCreatedByUid,
            paidByUid = effectivePaidByUid,
            isSynced = false
        )
        expenseDao.updateExpense(updated)

        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.saveExpense(wsId, updated, activeUid)
                    expenseDao.markExpensesSynced(listOf(updated.id))
                } catch (e: Exception) {
                    Log.w("TRAC_FIRESTORE", "Expense update cloud sync deferred: ${e.message}")
                }
            }
        }
    }

    suspend fun deleteExpense(expense: ExpenseEntity) {
        val wsId = expense.workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        val actorUid = getEffectiveActorUid() ?: ""
        val currentMember = workspaceMembers.value.firstOrNull { it.uid == actorUid }
        val rawRole = currentMember?.role?.takeIf { it.isNotBlank() } ?: resolveRoleForWorkspace(wsId)
        val actorRole = com.example.data.auth.RoleUtils.normalizeRole(rawRole)
        val isOwner = isOwnerOfWorkspace(wsId) && actorRole != com.example.data.auth.RoleUtils.ROLE_PARTNER && actorRole != com.example.data.auth.RoleUtils.ROLE_OPERATOR

        val effectiveCreatedAt = if (expense.createdAt > 0) expense.createdAt else expense.dateTimestamp

        if (!com.example.data.auth.AuthorizationManager.canDeleteExpense(
                expenseCreatedByUid = expense.createdByUid,
                expensePaidByUid = expense.paidByUid,
                expenseCreatedAt = effectiveCreatedAt,
                isOwner = isOwner,
                currentUid = actorUid,
                role = actorRole
        )) {
            Log.w(TAG, "Security rejection: Actor (uid=$actorUid, role=$actorRole, isOwner=$isOwner) is not authorized to delete expense ${expense.id}")
            return
        }

        expenseDao.deleteExpense(expense)
        if (wsId.isNotBlank()) {
            pendingDeleteManager.recordPendingDelete("EXPENSE", expense.id, wsId)
        }

        val isReady = isCloudReady()
        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.deleteExpense(wsId, expense.id)
                    pendingDeleteManager.removePendingDelete("EXPENSE", expense.id, wsId)
                } catch (e: Exception) {
                    Log.w(TAG, "Expense delete cloud sync deferred: ${e.message}")
                }
            }
        }
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
        val totalRec = jobEntryDao.getTotalReceivedForWorkspace(wsId).firstOrNull() ?: 0.0
        val totalExp = expenseDao.getTotalExpensesForWorkspace(wsId).firstOrNull() ?: 0.0
        val totalWth = withdrawalDao.getTotalWithdrawnForWorkspace(wsId).firstOrNull() ?: 0.0
        return totalRec - totalExp - totalWth
    }

    suspend fun addWithdrawal(withdrawal: WithdrawalEntity): Long {
        val actorUid = getEffectiveActorUid() ?: withdrawal.createdByUid.ifBlank { null } ?: ""
        val wsId = getOrResolveWorkspaceId() ?: withdrawal.workspaceId
        val isOwner = isOwnerOfWorkspace(wsId)
        val members = workspaceMembers.value
        val currentMember = members.firstOrNull { it.uid == actorUid }
        val actorRole = if (isOwner) {
            com.example.data.auth.RoleUtils.ROLE_OWNER
        } else {
            currentMember?.role?.takeIf { it.isNotBlank() }?.let { com.example.data.auth.RoleUtils.normalizeRole(it) }
                ?: withdrawal.createdByRole.takeIf { it.isNotBlank() }?.let { com.example.data.auth.RoleUtils.normalizeRole(it) }
                ?: com.example.data.auth.RoleUtils.ROLE_PARTNER
        }

        var targetUid = withdrawal.targetPartnerUid.trim()
        if (targetUid.isBlank()) {
            targetUid = if (actorRole == com.example.data.auth.RoleUtils.ROLE_PARTNER) {
                val targetMember = members.firstOrNull {
                    it.displayName?.trim()?.equals(withdrawal.partnerName.trim(), ignoreCase = true) == true
                }
                targetMember?.uid ?: if (withdrawal.partnerName.isBlank() || withdrawal.partnerName.trim().equals(currentMember?.displayName?.trim(), ignoreCase = true)) actorUid else "unresolved_target"
            } else {
                members.firstOrNull { it.displayName?.trim()?.equals(withdrawal.partnerName.trim(), ignoreCase = true) == true }?.uid ?: actorUid
            }
        }

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

        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.saveWithdrawal(wsId, saved, actorUid)
                    withdrawalDao.markWithdrawalsSynced(listOf(safeWithId))
                } catch (e: Exception) {
                    Log.w("TRAC_FIRESTORE", "Withdrawal cloud sync deferred: ${e.message}")
                }
            }
        }
        return safeWithId
    }

    suspend fun updateWithdrawal(withdrawal: WithdrawalEntity) {
        val actorUid = getEffectiveActorUid() ?: withdrawal.createdByUid.ifBlank { null } ?: ""
        val wsId = getOrResolveWorkspaceId() ?: withdrawal.workspaceId
        val isOwner = isOwnerOfWorkspace(wsId)
        val members = workspaceMembers.value
        val currentMember = members.firstOrNull { it.uid == actorUid }
        val actorRole = if (isOwner) {
            com.example.data.auth.RoleUtils.ROLE_OWNER
        } else {
            currentMember?.role?.takeIf { it.isNotBlank() }?.let { com.example.data.auth.RoleUtils.normalizeRole(it) }
                ?: withdrawal.createdByRole.takeIf { it.isNotBlank() }?.let { com.example.data.auth.RoleUtils.normalizeRole(it) }
                ?: com.example.data.auth.RoleUtils.ROLE_PARTNER
        }
        val targetUid = withdrawal.targetPartnerUid.ifBlank { actorUid }

        if (!com.example.data.auth.AuthorizationManager.canCreateWithdrawal(
                actorUid = actorUid,
                actorRole = actorRole,
                targetUid = targetUid,
                workspaceId = wsId,
                workspaceMembers = members
            )
        ) {
            Log.w(TAG, "Security rejection: Actor (uid=$actorUid, role=$actorRole) is not authorized to update withdrawal for target (uid=$targetUid)")
            throw SecurityException("Unauthorized withdrawal update attempt for member: ${withdrawal.partnerName}")
        }
        val isReady = isCloudReady()
        val updated = withdrawal.copy(
            workspaceId = wsId,
            isSynced = false,
            createdByUid = actorUid,
            createdByRole = actorRole,
            targetPartnerUid = targetUid
        )
        withdrawalDao.updateWithdrawal(updated)

        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.saveWithdrawal(wsId, updated, actorUid)
                    withdrawalDao.markWithdrawalsSynced(listOf(updated.id))
                } catch (e: Exception) {
                    Log.w("TRAC_FIRESTORE", "Withdrawal update cloud sync deferred: ${e.message}")
                }
            }
        }
    }

    suspend fun deleteWithdrawal(withdrawal: WithdrawalEntity) {
        val wsId = withdrawal.workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        val actorUid = getEffectiveActorUid() ?: ""
        val currentMember = workspaceMembers.value.firstOrNull { it.uid == actorUid }
        val rawRole = currentMember?.role?.takeIf { it.isNotBlank() } ?: resolveRoleForWorkspace(wsId)
        val actorRole = com.example.data.auth.RoleUtils.normalizeRole(rawRole)
        val isOwner = isOwnerOfWorkspace(wsId) && actorRole != com.example.data.auth.RoleUtils.ROLE_PARTNER && actorRole != com.example.data.auth.RoleUtils.ROLE_OPERATOR

        val effectiveCreatedAt = if (withdrawal.createdAt > 0) withdrawal.createdAt else withdrawal.timestamp

        if (!com.example.data.auth.AuthorizationManager.canDeleteWithdrawal(
                withdrawalTargetPartnerUid = withdrawal.targetPartnerUid,
                withdrawalCreatedByUid = withdrawal.createdByUid,
                withdrawalCreatedAt = effectiveCreatedAt,
                isOwner = isOwner,
                currentUid = actorUid,
                role = actorRole
        )) {
            Log.w(TAG, "Security rejection: Actor (uid=$actorUid, role=$actorRole, isOwner=$isOwner) is not authorized to delete withdrawal ${withdrawal.id}")
            return
        }

        withdrawalDao.deleteWithdrawal(withdrawal)
        if (wsId.isNotBlank()) {
            pendingDeleteManager.recordPendingDelete("WITHDRAWAL", withdrawal.id, wsId)
        }

        val isReady = isCloudReady()
        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.deleteWithdrawal(wsId, withdrawal.id)
                    pendingDeleteManager.removePendingDelete("WITHDRAWAL", withdrawal.id, wsId)
                } catch (e: Exception) {
                    Log.w(TAG, "Withdrawal delete cloud sync deferred: ${e.message}")
                }
            }
        }
    }

    suspend fun updateCustomer(customer: CustomerEntity) {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: customer.workspaceId
        val sanitized = customer.copy(
            workspaceId = wsId,
            phone = com.example.ui.components.sanitizePhoneNumberForStorage(customer.phone),
            isSynced = false
        )
        customerDao.updateCustomer(sanitized)

        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.saveCustomer(wsId, sanitized, activeUid)
                    customerDao.markCustomersSynced(listOf(sanitized.id))
                } catch (e: Exception) {
                    Log.w("TRAC_FIRESTORE", "Customer update cloud sync deferred: ${e.message}")
                }
            }
        }
    }

    suspend fun deleteCustomer(customer: CustomerEntity) {
        val wsId = customer.workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        val isOwner = isOwnerOfWorkspace(wsId)

        // STRICT SECURITY / ROLE CHECK USING CENTRALIZED AUTHORIZATION
        if (!com.example.data.auth.AuthorizationManager.canDeleteCustomer(isOwner)) {
            Log.w(TAG, "Security rejection: User is not authorized to delete customer ${customer.name} (isOwner=$isOwner)")
            return
        }

        // Find all associated job records for this customer by customerId or unique phone
        val associatedJobs = try {
            jobEntryDao.getJobsForWorkspaceOnce(wsId).filter { job ->
                job.customerId == customer.id || (customer.phone.isNotBlank() && job.customerPhone == customer.phone)
            }
        } catch (e: Exception) {
            emptyList()
        }

        // 1. Delete associated expenses from local database & record pending delete
        val associatedExpenses = expenseDao.getExpensesForCustomer(customer.id)
        associatedExpenses.forEach { exp ->
            expenseDao.deleteExpense(exp)
            if (wsId.isNotBlank()) {
                pendingDeleteManager.recordPendingDelete("EXPENSE", exp.id, wsId)
            }
        }

        // 2. Delete associated payments from local database & record pending delete
        val associatedPayments = paymentDao.getPaymentsForCustomer(customer.id)
        associatedPayments.forEach { pay ->
            paymentDao.deletePayment(pay)
            if (wsId.isNotBlank()) {
                pendingDeleteManager.recordPendingDelete("PAYMENT", pay.id, wsId)
            }
        }

        // 3. Delete associated jobs from local database & record pending delete
        associatedJobs.forEach { job ->
            jobEntryDao.deleteJob(job)
            if (wsId.isNotBlank()) {
                pendingDeleteManager.recordPendingDelete("JOB", job.id, wsId)
            }
        }

        // 4. Delete the customer from local database & record pending delete
        customerDao.deleteCustomer(customer)
        if (wsId.isNotBlank()) {
            pendingDeleteManager.recordPendingDelete("CUSTOMER", customer.id, wsId)
        }

        // 5. Synchronize deletions to Firestore
        val isReady = isCloudReady()
        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    associatedExpenses.forEach { exp ->
                        try {
                            firestoreRepository.deleteExpense(wsId, exp.id)
                            pendingDeleteManager.removePendingDelete("EXPENSE", exp.id, wsId)
                        } catch (e: Exception) {
                            Log.w(TAG, "Associated expense delete cloud sync deferred: ${e.message}")
                        }
                    }
                    associatedPayments.forEach { pay ->
                        try {
                            firestoreRepository.deletePayment(wsId, pay.id)
                            pendingDeleteManager.removePendingDelete("PAYMENT", pay.id, wsId)
                        } catch (e: Exception) {
                            Log.w(TAG, "Associated payment delete cloud sync deferred: ${e.message}")
                        }
                    }
                    associatedJobs.forEach { job ->
                        try {
                            firestoreRepository.deleteJob(wsId, job.id)
                            pendingDeleteManager.removePendingDelete("JOB", job.id, wsId)
                        } catch (e: Exception) {
                            Log.w(TAG, "Associated job delete cloud sync deferred: ${e.message}")
                        }
                    }
                    firestoreRepository.deleteCustomer(wsId, customer.id)
                    pendingDeleteManager.removePendingDelete("CUSTOMER", customer.id, wsId)
                } catch (e: Exception) {
                    Log.w(TAG, "Customer delete cloud sync deferred: ${e.message}")
                }
            }
        }
    }

    suspend fun addOrFindCustomer(name: String, phone: String, location: String): Long {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: ""
        val customers = customerDao.getCustomersForWorkspace(wsId).firstOrNull() ?: emptyList()
        val cleanPhone = com.example.ui.components.sanitizePhoneNumberForStorage(phone)
        val cleanName = name.trim()
        val cleanLocation = location.trim()

        // Match by exact name AND exact phone (never merge distinct customers with same name and different/blank phones)
        val existing = customers.find { 
            it.name.trim().equals(cleanName, ignoreCase = true) && 
            cleanPhone.isNotBlank() && it.phone == cleanPhone 
        }

        val custId: Long
        val customerEntity: CustomerEntity

        if (existing != null) {
            val updated = existing.copy(
                phone = if (cleanPhone.isNotBlank()) cleanPhone else existing.phone,
                location = if (cleanLocation.isNotBlank()) cleanLocation else existing.location,
                updatedAt = System.currentTimeMillis(),
                isSynced = false
            )
            customerDao.updateCustomer(updated)
            custId = existing.id
            customerEntity = updated
        } else {
            val safeCustId = IdGenerator.generateId()
            val newCust = CustomerEntity(
                id = safeCustId,
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
            custId = safeCustId
            customerEntity = newCust
        }

        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.saveCustomer(wsId, customerEntity, activeUid)
                    customerDao.markCustomersSynced(listOf(custId))
                } catch (e: Exception) {
                    Log.w(TAG, "Customer sync to cloud deferred: ${e.message}")
                }
            }
        }

        return custId
    }

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
        val isReady = isCloudReady()
        // CRITICAL: A payment for a customer MUST always belong to the customer's workspace.
        // Falling back to personal workspace causes split records that are invisible to the Owner.
        val wsId = customer.workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        val activeUid = getEffectiveActorUid()

        // 1. Validate amount against outstanding due
        val roundedAmount = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(amount)
        val currentBalanceDue = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(customer.balanceDue)
        if (roundedAmount > currentBalanceDue) {
            throw IllegalArgumentException("Payment amount $roundedAmount cannot be greater than outstanding due $currentBalanceDue")
        }

        // 2. Fetch all jobs for the customer
        val jobs = jobEntryDao.getJobsForCustomer(customer.id).firstOrNull() ?: emptyList()

        // 3. Find job entries with pending dues and sort by startTimeMillis (oldest first)
        val unpaidJobs = jobs.filter { 
            !com.example.ui.util.FinancialCalculationEngine.isSyntheticPayment(it) && 
            com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(it.pendingAmount) > 0.0 
        }.sortedBy { it.startTimeMillis }

        var remainingPayment = roundedAmount
        val updatedJobsList = mutableListOf<JobEntryEntity>()

        for (job in unpaidJobs) {
            if (remainingPayment <= 0.0) break

            val currentPending = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(job.pendingAmount)
            val allocation = minOf(remainingPayment, currentPending)

            val newAmountReceived = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(job.amountReceived + allocation)
            val newPendingAmount = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(maxOf(0.0, job.totalAmount - newAmountReceived))

            // Combine note with existing notes
            val notePart = if (note.isNotBlank()) "Payment Note: $note" else ""
            val methodPart = if (paymentMethod.isNotBlank()) "Payment Method: $paymentMethod" else ""
            val paymentNotes = listOf(methodPart, notePart).filter { it.isNotBlank() }.joinToString(" • ")

            val updatedNotes = if (paymentNotes.isNotBlank()) {
                if (job.notes.isNotBlank()) "${job.notes} • $paymentNotes" else paymentNotes
            } else {
                job.notes
            }

            val updatedJob = job.copy(
                amountReceived = newAmountReceived,
                pendingAmount = newPendingAmount,
                notes = updatedNotes,
                isSynced = false,
                updatedAt = System.currentTimeMillis()
            )
            updatedJobsList.add(updatedJob)
            remainingPayment = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(maxOf(0.0, remainingPayment - allocation))
        }

        // 4. Update the jobs in database
        for (updatedJob in updatedJobsList) {
            jobEntryDao.insertJob(updatedJob)
        }

        // 5. If there were NO unpaid jobs to apply to (e.g. advance payment with 0 due jobs),
        // create a synthetic Payment Received record in job_entries so it is tracked as credit.
        var fallbackPaymentId: Long? = null
        if (updatedJobsList.isEmpty()) {
            val methodDesc = if (paymentMethod.isNotBlank()) "Payment Method: $paymentMethod" else ""
            val noteDesc = if (note.isNotBlank()) "Note: $note" else ""
            val combinedNotes = listOf(methodDesc, noteDesc).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Direct Payment Received" }

            val safeEntryId = IdGenerator.generateId()
            val paymentEntry = JobEntryEntity(
                id = safeEntryId,
                workspaceId = wsId,
                customerId = customer.id,
                customerName = customer.name,
                customerPhone = customer.phone,
                customerLocation = customer.location,
                operatorName = operatorName.ifBlank { "Partner" },
                tractorId = 0,
                tractorLabel = "Payment",
                workType = "Payment Received",
                startTimeMillis = dateTimestamp,
                endTimeMillis = dateTimestamp,
                durationMinutes = 0,
                hourlyRate = 0.0,
                totalAmount = 0.0,
                amountReceived = roundedAmount,
                pendingAmount = 0.0,
                addedByPartner = operatorName.ifBlank { "Partner" },
                notes = combinedNotes,
                isSynced = false,
                createdByUid = activeUid ?: "",
                createdByRole = if (isOwnerOfWorkspace(wsId)) com.example.data.auth.RoleUtils.ROLE_OWNER else com.example.data.auth.RoleUtils.ROLE_PARTNER
            )
            jobEntryDao.insertJob(paymentEntry)
            fallbackPaymentId = safeEntryId
        }

        // 6. Record Payment event in payments table (for accounting and history)
        val paymentId = IdGenerator.generateId()
        val firstJob = updatedJobsList.firstOrNull() ?: unpaidJobs.firstOrNull()
        val isOwner = isOwnerOfWorkspace(wsId)
        val finalCollectorUid = if (collectedByUid.isNotBlank()) collectedByUid else (activeUid ?: "")
        val finalCollectorName = if (collectedByName.isNotBlank()) collectedByName else operatorName.ifBlank { "Owner" }
        val finalCollectorRole = if (collectedByRole.isNotBlank()) com.example.data.auth.RoleUtils.normalizeRole(collectedByRole) else (if (isOwner) com.example.data.auth.RoleUtils.ROLE_OWNER else com.example.data.auth.RoleUtils.ROLE_PARTNER)

        val paymentRecord = PaymentEntity(
            id = paymentId,
            workspaceId = wsId,
            jobEntryId = firstJob?.id ?: (fallbackPaymentId ?: 0L),
            customerId = customer.id,
            customerName = customer.name,
            tractorId = firstJob?.tractorId ?: 0L,
            tractorLabel = firstJob?.tractorLabel ?: "Payment",
            amount = roundedAmount,
            paymentMethod = paymentMethod.ifBlank { "Cash" },
            notes = note,
            collectedByUid = finalCollectorUid,
            collectedByName = finalCollectorName,
            collectedByRole = finalCollectorRole,
            collectedAt = dateTimestamp,
            createdAt = System.currentTimeMillis(),
            isSynced = false
        )
        paymentDao.insertPayment(paymentRecord)

        // Recalculate stats for the customer
        recalculateCustomerStats(customer.id)

        // Sync changes to cloud
        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    for (updatedJob in updatedJobsList) {
                        firestoreRepository.saveJobEntry(wsId, updatedJob, activeUid)
                        jobEntryDao.markJobsSynced(listOf(updatedJob.id))
                    }
                    if (fallbackPaymentId != null) {
                        val paymentEntry = jobEntryDao.getJobById(fallbackPaymentId)
                        if (paymentEntry != null) {
                            firestoreRepository.saveJobEntry(wsId, paymentEntry, activeUid)
                            jobEntryDao.markJobsSynced(listOf(fallbackPaymentId))
                        }
                    }
                    firestoreRepository.savePayment(wsId, paymentRecord, activeUid)
                    paymentDao.markPaymentsSynced(listOf(paymentId))

                    val updatedCust = customerDao.getCustomerById(customer.id)
                    if (updatedCust != null) {
                        firestoreRepository.saveCustomer(wsId, updatedCust, activeUid)
                        customerDao.markCustomersSynced(listOf(customer.id))
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Customer update after payment deferred: ${e.message}")
                }
            }
        }

        return fallbackPaymentId ?: paymentId
    }

    fun getPaymentsFlow(workspaceId: String): Flow<List<PaymentEntity>> {
        return paymentDao.getPaymentsForWorkspace(workspaceId)
    }

    suspend fun deletePayment(paymentId: Long) {
        val wsId = getOrResolveWorkspaceId() ?: ""
        val actorUid = getEffectiveActorUid() ?: ""
        val currentMember = workspaceMembers.value.firstOrNull { it.uid == actorUid }
        val rawRole = currentMember?.role?.takeIf { it.isNotBlank() } ?: resolveRoleForWorkspace(wsId)
        val actorRole = com.example.data.auth.RoleUtils.normalizeRole(rawRole)
        val isOwner = _isCollaborationOwner.value || 
            isOwnerOfWorkspace(wsId) || 
            com.example.data.auth.RoleUtils.isOwner(actorRole) || 
            (currentMember?.role?.let { com.example.data.auth.RoleUtils.isOwner(it) } == true) ||
            (currentWorkspace.value?.ownerUid == actorUid)

        Log.d("TRAC_DELETE", "WorkspaceRepository.deletePayment: paymentId=$paymentId, wsId=$wsId, actorUid=$actorUid, isOwner=$isOwner")

        val resolvedId = kotlin.math.abs(paymentId)
        val payment = paymentDao.getPaymentById(paymentId) 
            ?: paymentDao.getPaymentById(resolvedId)
            ?: paymentDao.getPaymentsForWorkspace(wsId).firstOrNull()?.find { it.jobEntryId == resolvedId || it.id == resolvedId }

        if (payment == null) {
            Log.d("TRAC_DELETE", "WorkspaceRepository.deletePayment: PaymentEntity not found for paymentId=$paymentId (resolvedId=$resolvedId). Checking job entries.")
            // PaymentEntity not found. Check if paymentId corresponds to a standalone synthetic or job-backed collection entry
            val job = jobEntryDao.getJobById(resolvedId) ?: jobEntryDao.getJobById(paymentId)
            if (job != null) {
                Log.d("TRAC_DELETE", "WorkspaceRepository.deletePayment: Found job entry for collection deletion reversal: job.id=${job.id}")
                val customerId = job.customerId
                if (com.example.ui.util.FinancialCalculationEngine.isSyntheticPayment(job)) {
                    jobEntryDao.deleteJob(job)
                    if (wsId.isNotBlank()) {
                        pendingDeleteManager.recordPendingDelete("JOB", job.id, wsId)
                        scope.launch {
                            try {
                                firestoreRepository.deleteJob(wsId, job.id)
                                pendingDeleteManager.removePendingDelete("JOB", job.id, wsId)
                            } catch (_: Exception) {}
                        }
                    }
                } else if (job.amountReceived > 0.0) {
                    val newReceived = 0.0
                    val newPending = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(maxOf(0.0, job.totalAmount))
                    val updatedJob = job.copy(
                        amountReceived = newReceived,
                        pendingAmount = newPending,
                        isSynced = false,
                        updatedAt = System.currentTimeMillis()
                    )
                    jobEntryDao.insertJob(updatedJob)
                    if (wsId.isNotBlank()) {
                        scope.launch {
                            try {
                                firestoreRepository.saveJobEntry(wsId, updatedJob, activeUid)
                                jobEntryDao.markJobsSynced(listOf(updatedJob.id))
                            } catch (_: Exception) {}
                        }
                    }
                }
                if (customerId > 0L) {
                    recalculateCustomerStats(customerId)
                }
            } else {
                Log.w("TRAC_DELETE", "WorkspaceRepository.deletePayment: Neither PaymentEntity nor JobEntry found for paymentId=$paymentId")
            }
            return
        }

        Log.d("TRAC_DELETE", "WorkspaceRepository.deletePayment: Found PaymentEntity: id=${payment.id}, amount=${payment.amount}, customerId=${payment.customerId}, collectorUid=${payment.collectedByUid}")

        // Workspace validation
        if (payment.workspaceId.isNotBlank() && wsId.isNotBlank() && payment.workspaceId != wsId) {
            Log.w(TAG, "Security rejection: Payment $paymentId belongs to workspace ${payment.workspaceId}, not active workspace $wsId")
            return
        }

        val originalCreatedAt = if (payment.createdAt > 0L) payment.createdAt else payment.collectedAt
        val canDelete = com.example.data.auth.AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = payment.collectedByUid,
            collectionCreatedAt = originalCreatedAt,
            isOwner = isOwner,
            currentUid = actorUid,
            role = actorRole
        )

        if (!canDelete) {
            Log.w(TAG, "Security rejection: Actor (uid=$actorUid, role=$actorRole, isOwner=$isOwner) is not authorized to delete collection/payment $paymentId")
            throw SecurityException("Unauthorized collection deletion")
        }

        val customerId = payment.customerId
        val paymentAmount = payment.amount
        val targetJobId = payment.jobEntryId
        val targetWsId = payment.workspaceId.ifBlank { wsId }

        var remainingReversal = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(paymentAmount)
        val updatedJobsList = mutableListOf<JobEntryEntity>()

        // 1a. If payment was linked to a specific jobEntryId, reverse from that job first
        if (targetJobId > 0L) {
            val targetJob = jobEntryDao.getJobById(targetJobId)
            if (targetJob != null && !com.example.ui.util.FinancialCalculationEngine.isSyntheticPayment(targetJob)) {
                val currentReceived = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(targetJob.amountReceived)
                val maxReversible = minOf(currentReceived, remainingReversal)
                if (maxReversible > 0.0) {
                    val newAmountReceived = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(maxOf(0.0, currentReceived - maxReversible))
                    val newPendingAmount = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(maxOf(0.0, targetJob.totalAmount - newAmountReceived))
                    val updatedJob = targetJob.copy(
                        amountReceived = newAmountReceived,
                        pendingAmount = newPendingAmount,
                        isSynced = false,
                        updatedAt = System.currentTimeMillis()
                    )
                    updatedJobsList.add(updatedJob)
                    remainingReversal = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(maxOf(0.0, remainingReversal - maxReversible))
                }
            }
        }

        // 1b. If there is still amount to reverse (or targetJob was not set), reverse from customer's work jobs (newest first)
        if (remainingReversal > 0.0 && customerId > 0L) {
            val jobs = jobEntryDao.getJobsForCustomer(customerId).firstOrNull() ?: emptyList()
            val candidateJobs = jobs.filter { 
                !com.example.ui.util.FinancialCalculationEngine.isSyntheticPayment(it) && 
                it.id != targetJobId &&
                com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(it.amountReceived) > 0.0 
            }.sortedByDescending { it.startTimeMillis }

            for (job in candidateJobs) {
                if (remainingReversal <= 0.0) break
                val currentReceived = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(job.amountReceived)
                val maxReversible = minOf(currentReceived, remainingReversal)
                if (maxReversible > 0.0) {
                    val newAmountReceived = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(maxOf(0.0, currentReceived - maxReversible))
                    val newPendingAmount = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(maxOf(0.0, job.totalAmount - newAmountReceived))
                    val updatedJob = job.copy(
                        amountReceived = newAmountReceived,
                        pendingAmount = newPendingAmount,
                        isSynced = false,
                        updatedAt = System.currentTimeMillis()
                    )
                    updatedJobsList.add(updatedJob)
                    remainingReversal = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(maxOf(0.0, remainingReversal - maxReversible))
                }
            }
        }

        // 1c. Update the reversed jobs in Room database
        for (updatedJob in updatedJobsList) {
            jobEntryDao.insertJob(updatedJob)
        }

        // 1d. Clean up any synthetic payment job associated with this payment or customer
        if (customerId > 0L) {
            val allCustomerJobs = jobEntryDao.getJobsForCustomer(customerId).firstOrNull() ?: emptyList()
            val syntheticPaymentJobs = allCustomerJobs.filter { 
                com.example.ui.util.FinancialCalculationEngine.isSyntheticPayment(it) && (
                    it.id == targetJobId ||
                    it.id == payment.id ||
                    (it.amountReceived == paymentAmount && kotlin.math.abs((it.startTimeMillis.takeIf { t -> t > 0 } ?: it.createdAt) - payment.collectedAt) < 120_000L)
                )
            }
            for (pJob in syntheticPaymentJobs) {
                jobEntryDao.deleteJob(pJob)
                if (targetWsId.isNotBlank()) {
                    pendingDeleteManager.recordPendingDelete("JOB", pJob.id, targetWsId)
                    scope.launch {
                        try {
                            firestoreRepository.deleteJob(targetWsId, pJob.id)
                            pendingDeleteManager.removePendingDelete("JOB", pJob.id, targetWsId)
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        // 2. Delete the PaymentEntity from Room and record pending delete
        paymentDao.deletePayment(paymentId)
        if (targetWsId.isNotBlank()) {
            pendingDeleteManager.recordPendingDelete("PAYMENT", paymentId, targetWsId)
        }

        // 3. Recalculate customer stats immediately
        if (customerId > 0L) {
            recalculateCustomerStats(customerId)
        }

        // 4. Sync reversal to cloud
        val isReady = isCloudReady()
        if (isReady && targetWsId.isNotBlank()) {
            scope.launch {
                try {
                    // Sync updated reversed jobs to Firestore
                    for (job in updatedJobsList) {
                        firestoreRepository.saveJobEntry(targetWsId, job, activeUid)
                        jobEntryDao.markJobsSynced(listOf(job.id))
                    }

                    // Sync updated customer to Firestore
                    if (customerId > 0L) {
                        val updatedCust = customerDao.getCustomerById(customerId)
                        if (updatedCust != null) {
                            firestoreRepository.saveCustomer(targetWsId, updatedCust, activeUid)
                            customerDao.markCustomersSynced(listOf(customerId))
                        }
                    }

                    // Delete payment document from Firestore
                    firestoreRepository.deletePayment(targetWsId, paymentId)
                    pendingDeleteManager.removePendingDelete("PAYMENT", paymentId, targetWsId)
                } catch (e: Exception) {
                    Log.w(TAG, "Payment reversal sync deferred: ${e.message}")
                }
            }
        }
    }

    fun getChecklistFlow(workspaceId: String): Flow<List<ChecklistItemEntity>> {
        return checklistItemDao.getChecklistForWorkspace(workspaceId)
    }

    suspend fun addChecklistItem(text: String, workspaceId: String): Long {
        val item = ChecklistItemEntity(
            workspaceId = workspaceId,
            text = text,
            isChecked = false,
            createdAt = System.currentTimeMillis()
        )
        return checklistItemDao.insertChecklistItem(item)
    }

    suspend fun updateChecklistItem(item: ChecklistItemEntity) {
        checklistItemDao.updateChecklistItem(item)
    }

    suspend fun deleteChecklistItem(item: ChecklistItemEntity) {
        checklistItemDao.deleteChecklistItem(item)
    }

    suspend fun deleteChecklistItemById(id: Long) {
        checklistItemDao.deleteChecklistItemById(id)
    }

    private suspend fun recalculateCustomerStats(customerId: Long) {
        val customer = customerDao.getCustomerById(customerId) ?: return
        val jobs = jobEntryDao.getAllJobs().firstOrNull() ?: emptyList()
        val stats = com.example.ui.util.FinancialCalculationEngine.calculateCustomerFinancials(customer, jobs)

        val updated = customer.copy(
            totalBilled = stats.totalBilled,
            totalPaid = stats.totalPaid,
            balanceDue = stats.balanceDue,
            isSynced = false,
            updatedAt = System.currentTimeMillis()
        )
        customerDao.updateCustomer(updated)
    }

    suspend fun addTractor(tractor: TractorEntity): Long {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: ""
        val safeTracId = if (tractor.id > 0) tractor.id else IdGenerator.generateId()
        val actorUid = getEffectiveActorUid() ?: activeUid ?: ""
        val isOwner = isOwnerOfWorkspace(wsId)
        val myRole = if (isOwner) com.example.data.auth.RoleUtils.ROLE_OWNER else {
            val member = _workspaceMembers.value.find { it.uid == actorUid }
            if (member != null && member.role.isNotBlank()) com.example.data.auth.RoleUtils.normalizeRole(member.role) else com.example.data.auth.RoleUtils.ROLE_PARTNER
        }
        val saved = tractor.copy(
            id = safeTracId,
            workspaceId = wsId,
            createdByUid = if (tractor.createdByUid.isNotBlank()) tractor.createdByUid else actorUid,
            createdByRole = if (tractor.createdByRole.isNotBlank()) tractor.createdByRole else myRole
        )
        tractorDao.insertTractor(saved)

        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.saveTractor(wsId, saved, actorUid)
                } catch (e: Exception) {
                    Log.w(TAG, "Tractor cloud sync deferred: ${e.message}")
                }
            }
        }
        return safeTracId
    }

    fun getWorkTypeExtensionsFlow(workspaceId: String): Flow<List<WorkTypeExtensionEntity>> {
        return workTypeExtensionDao.getExtensionsForWorkspace(workspaceId)
    }

    suspend fun addExtension(name: String, workspaceId: String = ""): WorkTypeExtensionEntity {
        val cleanName = name.trim()
        if (cleanName.isBlank()) throw IllegalArgumentException("Extension name cannot be empty")
        val wsId = workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        val actorUid = getEffectiveActorUid() ?: ""
        val isOwner = isOwnerOfWorkspace(wsId)
        val myRole = if (isOwner) {
            com.example.data.auth.RoleUtils.ROLE_OWNER
        } else {
            val member = _workspaceMembers.value.find { it.uid == actorUid }
            if (member != null && member.role.isNotBlank()) {
                com.example.data.auth.RoleUtils.normalizeRole(member.role)
            } else {
                com.example.data.auth.RoleUtils.ROLE_PARTNER
            }
        }
        if (!com.example.data.auth.AuthorizationManager.canAddExtension(isOwner, myRole)) {
            throw SecurityException("Operators cannot add extensions")
        }
        val id = IdGenerator.generateId()
        val entity = WorkTypeExtensionEntity(
            id = id,
            workspaceId = wsId,
            name = cleanName,
            createdAt = System.currentTimeMillis(),
            createdByUid = actorUid,
            createdByRole = myRole
        )
        workTypeExtensionDao.insertExtension(entity)
        if (isCloudReady() && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.saveExtension(wsId, entity, actorUid)
                } catch (e: Exception) {
                    Log.w(TAG, "Offline extension saved locally, cloud sync pending: ${e.message}")
                }
            }
        }
        return entity
    }

    suspend fun deleteExtension(extension: WorkTypeExtensionEntity) {
        val wsId = extension.workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        workTypeExtensionDao.deleteExtension(extension)
        if (wsId.isNotBlank()) {
            pendingDeleteManager.recordPendingDelete("EXTENSION", extension.id, wsId)
        }
        if (isCloudReady() && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.deleteExtension(wsId, extension.id)
                    pendingDeleteManager.removePendingDelete("EXTENSION", extension.id, wsId)
                } catch (e: Exception) {
                    Log.w(TAG, "Cloud delete extension failed: ${e.message}")
                }
            }
        }
    }

    suspend fun updateTractor(tractor: TractorEntity) {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: tractor.workspaceId
        val scoped = tractor.copy(workspaceId = wsId)
        tractorDao.updateTractor(scoped)

        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.saveTractor(wsId, scoped, activeUid)
                } catch (e: Exception) {
                    Log.w(TAG, "Tractor update cloud sync deferred: ${e.message}")
                }
            }
        }
    }

    suspend fun deleteTractor(tractor: TractorEntity) {
        val wsId = tractor.workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        val isOwner = isOwnerOfWorkspace(wsId)
        val actorUid = getEffectiveActorUid() ?: ""
        val currentMember = workspaceMembers.value.firstOrNull { it.uid == actorUid }
        val actorRole = if (isOwner) com.example.data.auth.RoleUtils.ROLE_OWNER else (currentMember?.role?.takeIf { it.isNotBlank() }?.let { com.example.data.auth.RoleUtils.normalizeRole(it) } ?: com.example.data.auth.RoleUtils.ROLE_PARTNER)

        if (!com.example.data.auth.AuthorizationManager.canDeleteTractor(
                tractorCreatedByUid = tractor.createdByUid,
                tractorCreatedAt = tractor.createdAt,
                isOwner = isOwner,
                currentUid = actorUid,
                role = actorRole
        )) {
            Log.w(TAG, "Security rejection: Actor (uid=$actorUid, role=$actorRole, isOwner=$isOwner) is not authorized to delete tractor ${tractor.id} (created by ${tractor.createdByUid})")
            return
        }

        tractorDao.deleteTractor(tractor)
        if (wsId.isNotBlank()) {
            pendingDeleteManager.recordPendingDelete("TRACTOR", tractor.id, wsId)
        }

        val isReady = isCloudReady()
        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.deleteTractor(wsId, tractor.id)
                    pendingDeleteManager.removePendingDelete("TRACTOR", tractor.id, wsId)
                } catch (e: Exception) {
                    Log.w(TAG, "Tractor delete cloud sync deferred: ${e.message}")
                }
            }
        }
    }

    suspend fun addPartner(partner: PartnerEntity): Long {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: ""
        val safePartId = if (partner.id > 0) partner.id else IdGenerator.generateId()
        val saved = partner.copy(id = safePartId, workspaceId = wsId)
        partnerDao.insertPartner(saved)

        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.savePartner(wsId, saved, activeUid)
                } catch (e: Exception) {
                    Log.w(TAG, "Partner cloud sync deferred: ${e.message}")
                }
            }
        }
        return safePartId
    }

    suspend fun updatePartner(partner: PartnerEntity) {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: partner.workspaceId
        val scoped = partner.copy(workspaceId = wsId)
        partnerDao.updatePartner(scoped)

        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.savePartner(wsId, scoped, activeUid)
                    if (partner.phone.isNotBlank()) {
                        firestoreRepository.savePendingPartnerPhone(
                            groupId = wsId,
                            normalizedPhone = partner.phone,
                            displayName = partner.name,
                            role = partner.role,
                            ownerUid = activeUid ?: ""
                        )
                    }
                    refreshWorkspaceMembers(wsId)
                } catch (e: Exception) {
                    Log.w(TAG, "Partner update cloud sync deferred: ${e.message}")
                }
            }
        }
    }

    suspend fun deletePartner(partner: PartnerEntity) {
        val wsId = partner.workspaceId.ifBlank { getOrResolveWorkspaceId() ?: "" }
        partnerDao.deletePartner(partner)
        if (wsId.isNotBlank()) {
            pendingDeleteManager.recordPendingDelete("PARTNER", partner.id, wsId)
        }

        val isReady = isCloudReady()
        if (isReady && wsId.isNotBlank()) {
            scope.launch {
                try {
                    firestoreRepository.deletePartner(wsId, partner.id)
                    if (partner.phone.isNotBlank()) {
                        firestoreRepository.deletePendingPartnerPhone(wsId, partner.phone)
                    }
                    pendingDeleteManager.removePendingDelete("PARTNER", partner.id, wsId)
                    refreshWorkspaceMembers(wsId)
                } catch (e: Exception) {
                    Log.w(TAG, "Partner delete cloud sync deferred: ${e.message}")
                }
            }
        }
    }

    // --- Direct Partner Management & Shared Workspace Discovery ---

    sealed class DirectAddPartnerResult {
        data class Success(val partner: PartnerEntity, val partnerUid: String) : DirectAddPartnerResult()
        data class AccountNotRegistered(val partner: PartnerEntity, val message: String) : DirectAddPartnerResult()
        data class Error(val message: String) : DirectAddPartnerResult()
    }

    suspend fun refreshWorkspaceMembers(workspaceId: String? = null) {
        if (!isCloudReady()) return
        val currentWs = if (!workspaceId.isNullOrBlank()) workspaceId else (_activeWorkspaceId.value ?: _personalWorkspaceId.value)
        if (currentWs.isNullOrBlank()) return
        
        refreshMembersMutex.lock()
        try {
            val visible = setOf(currentWs)
            val allMembers = mutableListOf<WorkspaceMember>()
            for (wsId in visible) {
                val members = firestoreRepository.getWorkspaceMembers(wsId)
                allMembers.addAll(members)
                val collabMembers = firestoreRepository.getCollaborationGroupMembersList(wsId)
                allMembers.addAll(collabMembers)
                val pendingPhones = firestoreRepository.getPendingPartnerPhones(wsId)
                for (pending in pendingPhones) {
                    val registeredUid = if (isCloudReady()) {
                        firestoreRepository.lookupPhoneInDirectory(pending.normalizedPhone)
                    } else null

                    if (!registeredUid.isNullOrBlank() && registeredUid != activeUid) {
                        try {
                            val currentSettings = appSettingsDao.getSettingsForWorkspaceOnce(wsId)
                            val businessName = currentSettings?.businessName?.ifBlank { "" } ?: ""
                            firestoreRepository.addPartnerMemberDirectly(
                                workspaceId = wsId,
                                partnerUid = registeredUid,
                                partnerName = pending.displayName.ifBlank { "Partner" },
                                partnerPhone = pending.normalizedPhone,
                                role = pending.role.ifBlank { "partner" },
                                ownerUid = activeUid ?: "",
                                businessName = businessName
                            )
                            firestoreRepository.deletePendingPartnerPhone(wsId, pending.normalizedPhone)
                            allMembers.add(
                                WorkspaceMember(
                                    uid = registeredUid,
                                    role = pending.role.ifBlank { "partner" },
                                    status = "active",
                                    phoneNumber = pending.normalizedPhone,
                                    displayName = pending.displayName.ifBlank { null },
                                    addedByUid = activeUid ?: "",
                                    invitedByUid = activeUid ?: ""
                                )
                            )
                            continue
                        } catch (e: Exception) {
                            Log.w(TAG, "Auto-promote pending partner error: ${e.message}")
                        }
                    }

                    allMembers.add(
                        WorkspaceMember(
                            uid = "",
                            role = pending.role.ifBlank { "partner" },
                            status = "waiting_for_registration",
                            phoneNumber = pending.normalizedPhone,
                            displayName = pending.displayName.ifBlank { null },
                            addedByUid = pending.addedByUid,
                            invitedByUid = pending.addedByUid
                        )
                    )
                }
            }
            val distinctMembers = allMembers.distinctBy {
                val phoneDigits = it.phoneNumber?.filter { c -> c.isDigit() }?.takeLast(10) ?: ""
                if (it.uid.isNotBlank()) it.uid else "pending_$phoneDigits"
            }
            
            // Only update if current active workspace matches the one we refreshed for
            val latestActive = _activeWorkspaceId.value ?: _personalWorkspaceId.value
            if (currentWs == latestActive) {
                for (wsId in visible) {
                    for (m in distinctMembers) {
                        if (m.uid.isNotBlank() && m.defaultHourlyRate != null && m.defaultHourlyRate > 0.0) {
                            val key = "hourly_rate_${wsId}_${m.uid}"
                            if (!userHourlyRatesPrefs.contains(key)) {
                                userHourlyRatesPrefs.edit().putFloat(key, m.defaultHourlyRate.toFloat()).apply()
                            }
                        }
                    }
                }
                _workspaceMembers.value = distinctMembers
            } else {
                Log.d(TAG, "Skipping members update: workspace changed during refresh ($currentWs -> $latestActive)")
            }
        } finally {
            refreshMembersMutex.unlock()
        }
    }

    suspend fun addPartnerDirectly(name: String, phone: String, role: String): DirectAddPartnerResult {
        val wsId = getOrResolveWorkspaceId() ?: ""
        if (wsId.isBlank()) {
            return DirectAddPartnerResult.Error("Workspace is not ready")
        }

        val isOwner = isOwnerOfWorkspace(wsId)
        val normalizedTargetRole = com.example.data.auth.RoleUtils.normalizeRole(role)
        if (!isOwner && (normalizedTargetRole == com.example.data.auth.RoleUtils.ROLE_OWNER || normalizedTargetRole == com.example.data.auth.RoleUtils.ROLE_CO_OWNER)) {
            return DirectAddPartnerResult.Error("Partners can only add Partner or Operator roles.")
        }

        val normalizedPhone = normalizePhoneNumber(phone)
        val cleanDigits = normalizedPhone.filter { it.isDigit() }.takeLast(10)

        if (cleanDigits.length < 10) {
            return DirectAddPartnerResult.Error("Please enter a valid 10-digit phone number.")
        }

        // Perform Phone Directory Lookup first to validate single business per partner constraint
        val partnerUid = if (isCloudReady()) firestoreRepository.lookupPhoneInDirectory(normalizedPhone) else null

        if (partnerUid != null && partnerUid == activeUid) {
            return DirectAddPartnerResult.Error("Owner cannot add themselves as Partner.")
        }

        if (isCloudReady()) {
            val isAlreadyPartner = firestoreRepository.checkIsUserOrPhoneAlreadyPartner(partnerUid, normalizedPhone, wsId)
            if (isAlreadyPartner) {
                return DirectAddPartnerResult.Error("This partner is already added to this business.")
            }
        }

        // 1. Create or update local PartnerEntity (used for local driver/operator functionality)
        val existingPartners = partnerDao.getPartnersForWorkspace(wsId).firstOrNull() ?: emptyList()
        val existing = existingPartners.firstOrNull { it.phone.filter { ch -> ch.isDigit() }.takeLast(10) == cleanDigits }
        val partnerEntity = if (existing != null) {
            val updated = existing.copy(name = name.trim(), phone = normalizedPhone, role = role.trim().ifBlank { "Partner" })
            partnerDao.updatePartner(updated)
            updated
        } else {
            val newPartner = PartnerEntity(
                id = IdGenerator.generateId(),
                workspaceId = wsId,
                name = name.trim(),
                phone = normalizedPhone,
                role = role.trim().ifBlank { "Partner" },
                avatarColorHex = "#1E4D2B",
                isCurrentActive = false
            )
            partnerDao.insertPartner(newPartner)
            newPartner
        }

        if (!isCloudReady()) {
            return DirectAddPartnerResult.AccountNotRegistered(
                partnerEntity,
                "Partner account not found. Ask this partner to create/login to their Phone account first."
            )
        }

        if (partnerUid.isNullOrBlank()) {
            val pendingRes = firestoreRepository.savePendingPartnerPhone(
                groupId = wsId,
                normalizedPhone = normalizedPhone,
                displayName = name.trim(),
                role = role.trim().ifBlank { "partner" },
                ownerUid = activeUid ?: ""
            )
            try {
                firestoreRepository.savePartner(wsId, partnerEntity, activeUid)
            } catch (e: Exception) {
                Log.w(TAG, "Local partner save deferred: ${e.message}")
            }
            refreshWorkspaceMembers(wsId)
            return DirectAddPartnerResult.AccountNotRegistered(
                partnerEntity,
                "Partner account not found. It will automatically connect when the partner registers their phone number."
            )
        }

        try {
            firestoreRepository.deletePendingPartnerPhone(wsId, normalizedPhone)
        } catch (_: Exception) {}

        // 3. Directly create workspace membership and user discovery index
        val currentSettings = appSettingsDao.getSettingsForWorkspaceOnce(wsId)
            ?: AppSettingsEntity(workspaceId = wsId)
        val businessName = currentSettings.businessName.ifBlank { "" }

        val addResult = firestoreRepository.addPartnerMemberDirectly(
            workspaceId = wsId,
            partnerUid = partnerUid,
            partnerName = name.trim(),
            partnerPhone = normalizedPhone,
            role = role.trim().ifBlank { "Partner" },
            ownerUid = activeUid ?: "",
            businessName = businessName
        )

        // 4. Link partner to owner's workspace in collaboration group
        firestoreRepository.addPartnerToCollaborationGroup(
            ownerWorkspaceId = wsId,
            ownerUid = activeUid ?: "",
            partnerUid = partnerUid,
            partnerWorkspaceId = wsId,
            partnerPhone = normalizedPhone,
            partnerDisplayName = name.trim()
        )

        // Ensure partner's default workspace is set to owner's workspace on Firestore
        try {
            firestoreRepository.updateUserDefaultWorkspace(partnerUid, wsId, "partner")
        } catch (e: Exception) {
            Log.w(TAG, "updateUserDefaultWorkspace for partner failed: ${e.message}")
        }

        // Update local visible workspaces
        val currentVisible = _visibleWorkspaceIds.value.toMutableSet()
        currentVisible.add(wsId)
        _visibleWorkspaceIds.value = currentVisible
        attachRealtimeListenersForVisibleWorkspaces()

        return if (addResult.isSuccess) {
            refreshWorkspaceMembers(wsId)
            DirectAddPartnerResult.Success(partnerEntity, partnerUid)
        } else {
            val err = addResult.exceptionOrNull()?.message ?: "Could not connect partner"
            DirectAddPartnerResult.Error(err)
        }
    }

    suspend fun removePartner(partner: PartnerEntity, partnerUid: String? = null) {
        removePartnerFromWorkspace(partner, partnerUid)
    }

    suspend fun checkForInvitations(phoneNumber: String): List<WorkspaceInvitation> {
        _pendingInvitations.value = emptyList()
        return emptyList()
    }

    suspend fun switchActiveWorkspace(targetWorkspaceId: String, userProfile: UserProfile): Result<String> {
        val uid = userProfile.uid
        if (targetWorkspaceId.isBlank()) {
            return Result.failure(IllegalArgumentException("Target workspace ID cannot be blank"))
        }
        if (targetWorkspaceId == _activeWorkspaceId.value && _isInitialized.value) {
            return Result.success(targetWorkspaceId)
        }

        return try {
            _workspaceInitState.value = WorkspaceInitState.Loading
            _settingsSyncState.value = SettingsSyncState.Loading

            // Load settings for target workspace
            val localSettings = appSettingsDao.getSettingsForWorkspaceOnce(targetWorkspaceId)
                ?: AppSettingsEntity(workspaceId = targetWorkspaceId)
            val resolvedSettings = if (isCloudReady() && uid.isNotBlank()) {
                firestoreRepository.fetchOrCreateWorkspaceSettings(targetWorkspaceId, uid, localSettings)
            } else localSettings

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
            appSettingsDao.insertOrUpdateSettings(mergedSettings)
            loadCachedPercentages(targetWorkspaceId)
            loadCachedProfitShareAllocations(targetWorkspaceId)
            _settingsSyncState.value = SettingsSyncState.LoadedFromCloud(mergedSettings)

            // Switch active workspace ID
            _activeWorkspaceId.value = targetWorkspaceId
            _isInitialized.value = true
            _workspaceInitState.value = WorkspaceInitState.Ready(targetWorkspaceId)

            // Re-evaluate role for target workspace using the AUTHORITATIVE MEMBERSHIP RECORD only.
            // Workspace ownership (ownerUid) must never be used to infer the role.
            val targetRole = firestoreRepository.resolveAuthoritativeRole(uid, targetWorkspaceId)
                ?: (currentUserRole?.takeIf { it.isNotBlank() } ?: com.example.data.auth.RoleUtils.ROLE_PARTNER)
            val normalizedTargetRole = com.example.data.auth.RoleUtils.normalizeRole(targetRole)
            currentUserRole = normalizedTargetRole
            val isOwner = normalizedTargetRole == com.example.data.auth.RoleUtils.ROLE_OWNER ||
                normalizedTargetRole == com.example.data.auth.RoleUtils.ROLE_CO_OWNER
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
                    migrateLegacyPercentagesToUidAllocations(targetWorkspaceId)
                }
            }

            Result.success(targetWorkspaceId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to switch active workspace: ${e.message}", e)
            _workspaceInitState.value = WorkspaceInitState.Error(e)
            _settingsSyncState.value = SettingsSyncState.Error(e.message ?: "Failed to switch workspace")
            Result.failure(e)
        }
    }

    suspend fun removePartnerFromWorkspace(partner: PartnerEntity, targetPartnerUid: String? = null) {
        val currentWsId = _personalWorkspaceId.value ?: _activeWorkspaceId.value ?: partner.workspaceId
        val isOwner = _isCollaborationOwner.value
        if (!isOwner && !targetPartnerUid.isNullOrBlank()) {
            Log.w(TAG, "Unauthorized: Non-owner cannot remove active connected members")
            return
        }
        try {
            partnerDao.deletePartner(partner)
            if (isCloudReady() && currentWsId.isNotBlank()) {
                scope.launch {
                    try {
                        firestoreRepository.deletePartner(currentWsId, partner.id)
                        val normPhone = normalizePhoneNumber(partner.phone)
                        if (normPhone.isNotBlank()) {
                            firestoreRepository.deletePendingPartnerPhone(currentWsId, normPhone)
                        }
                        if (isOwner) {
                            val partnerUid = targetPartnerUid?.ifBlank { null }
                                ?: firestoreRepository.lookupPhoneInDirectory(partner.phone)
                            firestoreRepository.removePartnerFromWorkspace(
                                workspaceId = currentWsId,
                                partnerUid = partnerUid,
                                partnerPhone = partner.phone
                            )
                            if (!partnerUid.isNullOrBlank()) {
                                firestoreRepository.removePartnerFromCollaborationGroup(currentWsId, partnerUid)
                                val partnerPersonalWsId = "ws_${partnerUid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"
                                val currentVisible = _visibleWorkspaceIds.value.toMutableSet()
                                currentVisible.remove(partnerPersonalWsId)
                                _visibleWorkspaceIds.value = currentVisible
                                attachRealtimeListenersForVisibleWorkspaces()
                            }
                        }
                        refreshWorkspaceMembers(currentWsId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing partner in cloud: ${e.message}", e)
                    }
                }
            }
            Log.d("TRAC_PARTNER", "Removed partner ${partner.name} from workspace $currentWsId")
        } catch (e: Exception) {
            Log.e(TAG, "Error removing partner: ${e.message}", e)
        }
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
        val personalWsId = _personalWorkspaceId.value 
            ?: userProfile.defaultWorkspaceId?.ifBlank { null }
            ?: "ws_${uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"
        val list = getGenuinePartnerWorkspaces(userProfile, personalWsId)
        _genuinePartnerWorkspaces.value = list
        return list
    }

    suspend fun getWorkspaceMembers(workspaceId: String? = null): List<WorkspaceMember> {
        val targetWsId = workspaceId ?: getOrResolveWorkspaceId() ?: return emptyList()
        return firestoreRepository.getWorkspaceMembers(targetWsId)
    }

    suspend fun updateSettings(settings: AppSettingsEntity) {
        val isReady = isCloudReady()
        val wsId = getOrResolveWorkspaceId() ?: settings.workspaceId
        val isOwner = _isCollaborationOwner.value
        val existing = appSettingsDao.getSettingsForWorkspaceOnce(wsId)
        val scoped = if (!isOwner && existing != null) {
            settings.copy(
                workspaceId = wsId,
                businessName = existing.businessName,
                businessPhone = existing.businessPhone,
                businessAddress = existing.businessAddress,
                gstNumber = existing.gstNumber,
                ownerName = existing.ownerName
            )
        } else {
            settings.copy(workspaceId = wsId)
        }
        appSettingsDao.insertOrUpdateSettings(scoped)

        val actorUid = getEffectiveActorUid()
        saveUserLanguage(settings.language, actorUid)

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
        if (!isOnline) {
            return SyncResult(
                isSuccess = false,
                syncedItemsCount = 0,
                message = "Device offline. Records stored safely in local Room database."
            )
        }

        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (activeUid == null) {
            activeUid = currentUid
        }
        if (activeUid == null || (currentUid != null && activeUid != currentUid)) {
            return SyncResult(
                isSuccess = false,
                syncedItemsCount = 0,
                message = "Workspace not ready or sign-in mismatch."
            )
        }

        val fallbackWsId = targetWorkspaceId
            ?: (_workspaceInitState.value as? WorkspaceInitState.Ready)?.workspaceId
            ?: _activeWorkspaceId.value
            ?: _personalWorkspaceId.value
            ?: ""

        var syncedCount = 0

        // 1. Process all pending deletions first so cloud copies are deleted
        val pendingDeletions = pendingDeleteManager.getAllPendingDeletions()
        for (del in pendingDeletions) {
            try {
                when (del.entityType) {
                    "JOB" -> firestoreRepository.deleteJob(del.workspaceId, del.recordId)
                    "EXPENSE" -> firestoreRepository.deleteExpense(del.workspaceId, del.recordId)
                    "CUSTOMER" -> firestoreRepository.deleteCustomer(del.workspaceId, del.recordId)
                    "WITHDRAWAL" -> firestoreRepository.deleteWithdrawal(del.workspaceId, del.recordId)
                    "TRACTOR" -> firestoreRepository.deleteTractor(del.workspaceId, del.recordId)
                    "PARTNER" -> firestoreRepository.deletePartner(del.workspaceId, del.recordId)
                }
                pendingDeleteManager.removePendingDelete(del.entityType, del.recordId, del.workspaceId)
                syncedCount++
            } catch (e: Exception) {
                Log.w(TAG, "Pending delete for ${del.entityType} ${del.recordId} deferred: ${e.message}")
            }
        }

        // 1.5 Process all pending audit events
        val pendingAudits = pendingAuditManager.getAllPendingAudits()
        for (audit in pendingAudits) {
            val recordWsId = audit.workspaceId.ifBlank { fallbackWsId }
            if (recordWsId.isNotBlank()) {
                try {
                    firestoreRepository.recordAuditEvent(recordWsId, audit.copy(workspaceId = recordWsId))
                    pendingAuditManager.removePendingAudit(audit.eventId)
                    syncedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Pending audit ${audit.eventId} deferred: ${e.message}")
                }
            }
        }

        // 2. Fetch unsynced items sorted by creation time
        val unsyncedCustomers = customerDao.getUnsyncedCustomers().sortedBy { it.createdAt }
        val unsyncedJobs = jobEntryDao.getUnsyncedJobs().sortedBy { it.createdAt }
        val unsyncedExpenses = expenseDao.getUnsyncedExpenses().sortedBy { it.createdAt }
        val unsyncedWithdrawals = withdrawalDao.getUnsyncedWithdrawals().sortedBy { it.createdAt }

        val totalRecordsCount = pendingDeletions.size + pendingAudits.size + unsyncedJobs.size + unsyncedExpenses.size + unsyncedWithdrawals.size + unsyncedCustomers.size
        if (totalRecordsCount == 0) {
            // Even when no local operational rows are pending, update cloud sync status when explicitly pushed
            if (fallbackWsId.isNotBlank()) {
                try {
                    val currentActorUid = activeUid ?: ""
                    val currentSettings = appSettingsDao.getSettingsForWorkspaceOnce(fallbackWsId)
                    val currentActorName = if (isCollaborationOwner.value) {
                        currentSettings?.ownerName?.ifBlank { "Owner" } ?: "Owner"
                    } else {
                        currentSettings?.activePartnerName?.ifBlank { "Partner" } ?: "Partner"
                    }
                    val currentRole = if (isCollaborationOwner.value) "Owner" else "Partner"
                    firestoreRepository.updateLastSuccessfulSync(fallbackWsId, currentActorUid, currentActorName, currentRole)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to update lastSuccessfulSync on cloud: ${e.message}")
                }
            }
            return SyncResult(
                isSuccess = true,
                syncedItemsCount = 0,
                message = "All records are already in sync with Cloud."
            )
        }

        // 3. Sync Customers first so FK relationships exist
        for (cust in unsyncedCustomers) {
            val recordWsId = cust.workspaceId.ifBlank { fallbackWsId }
            if (recordWsId.isNotBlank()) {
                try {
                    firestoreRepository.saveCustomer(recordWsId, cust.copy(workspaceId = recordWsId), activeUid)
                    customerDao.markCustomersSynced(listOf(cust.id))
                    syncedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Retry sync for customer ${cust.id} failed: ${e.message}")
                }
            }
        }

        // 4. Sync Jobs
        for (job in unsyncedJobs) {
            val recordWsId = job.workspaceId.ifBlank { fallbackWsId }
            if (recordWsId.isNotBlank()) {
                try {
                    firestoreRepository.saveJobEntry(recordWsId, job.copy(workspaceId = recordWsId), activeUid)
                    jobEntryDao.markJobsSynced(listOf(job.id))
                    syncedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Retry sync for job ${job.id} failed: ${e.message}")
                }
            }
        }

        // 5. Sync Expenses
        for (exp in unsyncedExpenses) {
            val recordWsId = exp.workspaceId.ifBlank { fallbackWsId }
            if (recordWsId.isNotBlank()) {
                try {
                    firestoreRepository.saveExpense(recordWsId, exp.copy(workspaceId = recordWsId), activeUid)
                    expenseDao.markExpensesSynced(listOf(exp.id))
                    syncedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Retry sync for expense ${exp.id} failed: ${e.message}")
                }
            }
        }

        // 6. Sync Withdrawals
        for (wth in unsyncedWithdrawals) {
            val recordWsId = wth.workspaceId.ifBlank { fallbackWsId }
            if (recordWsId.isNotBlank()) {
                try {
                    firestoreRepository.saveWithdrawal(recordWsId, wth.copy(workspaceId = recordWsId), activeUid)
                    withdrawalDao.markWithdrawalsSynced(listOf(wth.id))
                    syncedCount++
                } catch (e: Exception) {
                    Log.w(TAG, "Retry sync for withdrawal ${wth.id} failed: ${e.message}")
                }
            }
        }

        // 7. Ensure settings for visible workspaces are synchronized
        val visible = _visibleWorkspaceIds.value.ifEmpty { setOf(fallbackWsId).filter { it.isNotBlank() } }
        for (ws in visible) {
            val localSettings = appSettingsDao.getSettingsForWorkspaceOnce(ws)
            if (localSettings != null) {
                try {
                    firestoreRepository.saveSettings(ws, localSettings, activeUid)
                } catch (e: Exception) {
                    Log.w(TAG, "Settings sync for $ws deferred: ${e.message}")
                }
            }
        }

        if (fallbackWsId.isNotBlank()) {
            try {
                val currentActorUid = activeUid ?: ""
                val currentSettings = appSettingsDao.getSettingsForWorkspaceOnce(fallbackWsId)
                val currentActorName = if (isCollaborationOwner.value) {
                    currentSettings?.ownerName?.ifBlank { "Owner" } ?: "Owner"
                } else {
                    currentSettings?.activePartnerName?.ifBlank { "Partner" } ?: "Partner"
                }
                val currentRole = if (isCollaborationOwner.value) "Owner" else "Partner"
                firestoreRepository.updateLastSuccessfulSync(fallbackWsId, currentActorUid, currentActorName, currentRole)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to update lastSuccessfulSync on cloud: ${e.message}")
            }
        }

        val success = (syncedCount >= totalRecordsCount)
        return SyncResult(
            isSuccess = success,
            syncedItemsCount = syncedCount,
            message = if (success) "Pushed $syncedCount offline records to Cloud successfully!"
                      else "Synced $syncedCount of $totalRecordsCount items. Cloud sync pending for remainder."
        )
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
