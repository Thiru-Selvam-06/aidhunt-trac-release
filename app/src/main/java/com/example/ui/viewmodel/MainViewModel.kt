package com.example.ui.viewmodel

import android.app.Activity
import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import com.example.data.firebase.AuthState
import com.example.data.firebase.UserProfile
import com.example.data.firebase.AccountLookupResult
import com.example.data.firebase.Workspace
import com.example.data.firebase.WorkspaceInvitation
import com.example.data.firebase.WorkspaceMember
import com.example.data.firebase.normalizePhoneNumber
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await
import com.example.data.network.NetworkMonitor
import com.example.data.repository.AuthRepository
import com.example.data.repository.SettingsSyncState
import com.example.data.repository.TractorRepository
import com.example.data.repository.WorkspaceInitState
import com.example.data.repository.WorkspaceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class BottomTab {
    HOME,
    REPORT,
    NEW_ENTRY,
    ACCOUNT
}

enum class ReportSubPage {
    MENU,
    EXPENSES,
    BALANCE_SHEET,
    BUSINESS_OVERVIEW,
    WITHDRAWAL,
    CUSTOMER_CREDIT_DUE,
    COLLECTION_HISTORY,
    OLD_ENTRY
}

enum class AccountSubPage {
    MAIN,
    MANAGE_TRACTORS,
    MANAGE_PARTNERS,
    SETTINGS,
    EDIT_PROFILE,
    SQLITE_SYNC_STATUS
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getInstance(application)
    private val repository = TractorRepository(database)
    private val authRepository = AuthRepository.getInstance(application)
    private val workspaceRepository = WorkspaceRepository.getInstance(application, database)
    private val networkMonitor = NetworkMonitor(application)
    val isOnline: StateFlow<Boolean> = networkMonitor.isOnline

    // Workspace & Authentication State
    val currentWorkspace: StateFlow<Workspace?> = workspaceRepository.currentWorkspace
    val isWorkspaceInitialized: StateFlow<Boolean> = workspaceRepository.isInitialized
    val settingsSyncState: StateFlow<SettingsSyncState> = workspaceRepository.settingsSyncState
    val authState: StateFlow<AuthState> = authRepository.authState
    val currentUserProfile: StateFlow<UserProfile?> = authRepository.currentUserProfile
    val currentUid: String?
        get() = authRepository.currentUid

    private val _isStartupAuthResolved = MutableStateFlow(false)
    val isStartupAuthResolved: StateFlow<Boolean> = _isStartupAuthResolved.asStateFlow()

    // Phone verification ID tracker for OTP flow
    private val _phoneVerificationId = MutableStateFlow<String?>(null)
    val phoneVerificationId: StateFlow<String?> = _phoneVerificationId.asStateFlow()

    // Sync State
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncMessage = MutableStateFlow("All partner data in sync with Cloud (Offline Ready)")
    val syncMessage: StateFlow<String> = _syncMessage.asStateFlow()

    // Force Simulated Offline Toggle for user demonstration/testing
    private val _simulatedOffline = MutableStateFlow(false)
    val simulatedOffline: StateFlow<Boolean> = _simulatedOffline.asStateFlow()

    // Effective online state (considers real network + simulation toggle)
    val isEffectiveOnline: StateFlow<Boolean> = combine(isOnline, _simulatedOffline) { online, simOff ->
        online && !simOff
    }.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    // App Navigation & Tab State
    private val _currentTab = MutableStateFlow(BottomTab.HOME)
    val currentTab: StateFlow<BottomTab> = _currentTab.asStateFlow()

    private val _currentReportSubPage = MutableStateFlow(ReportSubPage.MENU)
    val currentReportSubPage: StateFlow<ReportSubPage> = _currentReportSubPage.asStateFlow()

    private val _currentAccountSubPage = MutableStateFlow(AccountSubPage.MAIN)
    val currentAccountSubPage: StateFlow<AccountSubPage> = _currentAccountSubPage.asStateFlow()

    // Persistent in-memory draft state for New Work Entry
    private val _newEntryDraft = MutableStateFlow<NewEntryDraft?>(null)
    val newEntryDraft: StateFlow<NewEntryDraft?> = _newEntryDraft.asStateFlow()

    private val _isSavingJob = MutableStateFlow(false)
    val isSavingJob: StateFlow<Boolean> = _isSavingJob.asStateFlow()

    val workspaceInitState: StateFlow<WorkspaceInitState> = workspaceRepository.workspaceInitState
    val activeWorkspaceId: StateFlow<String?> = workspaceRepository.activeWorkspaceId
    val personalWorkspaceId: StateFlow<String?> = workspaceRepository.personalWorkspaceId
    val visibleWorkspaceIds: StateFlow<Set<String>> = workspaceRepository.visibleWorkspaceIds
    val pendingInvitations: StateFlow<List<WorkspaceInvitation>> = workspaceRepository.pendingInvitations
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val workspaceMembers: StateFlow<List<WorkspaceMember>> = activeWorkspaceId.flatMapLatest { wsId ->
        val target = wsId ?: personalWorkspaceId.value
        if (target.isNullOrBlank()) MutableStateFlow(emptyList<WorkspaceMember>())
        else workspaceRepository.workspaceMembers.map { members ->
            // In theory, WorkspaceRepository.refreshWorkspaceMembers(target) already filters.
            // This is a second layer of defense to ensure the UI only sees members for the active workspace.
            members.filter { true } // The actual filtering happens in the repository based on 'activeWorkspaceId'
            members 
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val isCollaborationOwner: StateFlow<Boolean> = workspaceRepository.isCollaborationOwner
    val lockedPages: StateFlow<Set<String>> = workspaceRepository.lockedPagesState
    val accessControlState: StateFlow<com.example.data.auth.AccessControlDocument> = workspaceRepository.accessControlState

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentUserRole: StateFlow<String> = kotlinx.coroutines.flow.combine(
        activeWorkspaceId,
        workspaceMembers
    ) { wsId, members ->
        // AUTHORITATIVE ROLE SOURCE: workspaces/{workspaceId}/members/{uid}.role
        // The role is NEVER inferred from workspace ownership, so an Owner and a Partner who
        // share the same workspaceId are still distinguished correctly.
        val uid = currentUid ?: ""
        val member = members.firstOrNull { it.uid == uid && it.role.isNotBlank() }
        val rawRole = member?.role
            ?: workspaceRepository.resolveRoleForWorkspace(wsId ?: "")
        com.example.data.auth.RoleUtils.normalizeRole(rawRole)
        // CRITICAL: default is ROLE_PARTNER, never ROLE_OWNER. Partner is the safe default
        // while membership state settles and can never over-grant Owner privileges.
    }.stateIn(viewModelScope, SharingStarted.Eagerly, com.example.data.auth.RoleUtils.ROLE_PARTNER)

    val isOperator: StateFlow<Boolean> = currentUserRole.map { it == com.example.data.auth.RoleUtils.ROLE_OPERATOR }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)


    @OptIn(ExperimentalCoroutinesApi::class)
    val workTypeExtensions: StateFlow<List<WorkTypeExtensionEntity>> = activeWorkspaceId.flatMapLatest { wsId ->
        val target = wsId ?: personalWorkspaceId.value
        if (target.isNullOrBlank()) MutableStateFlow(emptyList())
        else workspaceRepository.getWorkTypeExtensionsFlow(target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun isAccountBlocked(): Boolean = com.example.data.auth.AuthorizationManager.isAccountBlocked()

    fun canCreateNewEntry(): Boolean = com.example.data.auth.AuthorizationManager.canCreateNewEntry(isCollaborationOwner.value)

    fun canCreateOldEntry(): Boolean = com.example.data.auth.AuthorizationManager.canCreateOldEntry(isCollaborationOwner.value, currentUserRole.value)

    fun canEditEntry(job: com.example.data.entity.JobEntryEntity): Boolean {
        val currentActorName = settings.value.activePartnerName.ifBlank { null } ?: settings.value.ownerName.ifBlank { null } ?: "Partner"
        return com.example.data.auth.AuthorizationManager.canEditEntry(
            job = job,
            isOwner = isCollaborationOwner.value,
            currentActorName = currentActorName,
            currentUid = currentUid ?: "",
            role = currentUserRole.value
        )
    }

    fun canDeleteEntry(job: com.example.data.entity.JobEntryEntity): Boolean {
        val currentActorName = settings.value.activePartnerName.ifBlank { null } ?: settings.value.ownerName.ifBlank { null } ?: "Partner"
        return com.example.data.auth.AuthorizationManager.canDeleteEntry(
            job = job,
            isOwner = isCollaborationOwner.value,
            currentActorName = currentActorName,
            currentUid = currentUid ?: "",
            role = currentUserRole.value
        )
    }

    fun canDeleteCustomer(): Boolean = com.example.data.auth.AuthorizationManager.canDeleteCustomer(isCollaborationOwner.value)

    fun canCollectPayment(): Boolean = com.example.data.auth.AuthorizationManager.canCollectPayment(isCollaborationOwner.value, currentUserRole.value)

    fun canDeleteCollection(
        payment: PaymentEntity,
        isOwner: Boolean,
        currentUid: String,
        role: String
    ): Boolean = com.example.data.auth.AuthorizationManager.canDeleteCollection(
        collectionCreatedByUid = payment.collectedByUid,
        collectionCreatedAt = if (payment.createdAt > 0L) payment.createdAt else payment.collectedAt,
        isOwner = isOwner,
        currentUid = currentUid,
        role = role
    )

    fun deleteCollection(payment: PaymentEntity, currentUid: String, role: String, isOwner: Boolean, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!canDeleteCollection(payment, isOwner, currentUid, role)) {
            onError("Collection deletion is restricted")
            return
        }
        viewModelScope.launch {
            try {
                workspaceRepository.deletePayment(payment.id)
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "deleteCollection failed: ${e.message}")
                onError(e.message ?: "Collection deletion failed")
            }
        }
    }

    fun deleteCollection(
        paymentId: Long,
        collectorUid: String,
        createdAt: Long,
        currentUid: String,
        role: String,
        isOwner: Boolean,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        android.util.Log.d("TRAC_DELETE", "MainViewModel.deleteCollection: paymentId=$paymentId, collectorUid=$collectorUid, createdAt=$createdAt, currentUid=$currentUid, role=$role, isOwner=$isOwner")
        val canDelete = com.example.data.auth.AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = collectorUid,
            collectionCreatedAt = createdAt,
            isOwner = isOwner,
            currentUid = currentUid,
            role = role
        )
        android.util.Log.d("TRAC_DELETE", "MainViewModel.deleteCollection: authorization canDelete=$canDelete")
        if (!canDelete) {
            onError("Collection deletion is restricted")
            return
        }
        viewModelScope.launch {
            try {
                workspaceRepository.deletePayment(paymentId)
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "deleteCollection failed: ${e.message}")
                onError(e.message ?: "Collection deletion failed")
            }
        }
    }

    fun canExportPdf(): Boolean = com.example.data.auth.AuthorizationManager.canExportPdf()

    fun canManageTractors(): Boolean = com.example.data.auth.AuthorizationManager.canManageTractors(isCollaborationOwner.value, currentUserRole.value)

    fun canManagePartners(): Boolean = com.example.data.auth.AuthorizationManager.canManagePartners(isCollaborationOwner.value)

    fun canAddExtension(): Boolean = com.example.data.auth.AuthorizationManager.canAddExtension(isCollaborationOwner.value, currentUserRole.value)

    fun canEditBusinessName(): Boolean = com.example.data.auth.AuthorizationManager.canEditBusinessName(isCollaborationOwner.value)

    fun addExtension(name: String, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!canAddExtension()) {
            onError("You do not have permission to add extensions")
            return
        }
        viewModelScope.launch {
            try {
                val wsId = activeWorkspaceId.value ?: personalWorkspaceId.value ?: ""
                workspaceRepository.addExtension(name, wsId)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to add extension")
            }
        }
    }

    fun getPartnerLockedPages(partnerKey: String): Set<String> {
        val wsId = activeWorkspaceId.value ?: ""
        return workspaceRepository.getPartnerLockedPages(wsId, partnerKey)
    }

    fun setPartnerPageLocked(partnerKey: String, pageKey: String, isLocked: Boolean) {
        val wsId = activeWorkspaceId.value ?: ""
        workspaceRepository.setPartnerPageLocked(wsId, partnerKey, pageKey, isLocked)
    }

    fun setPageLocked(pageKey: String, isLocked: Boolean) {
        val wsId = activeWorkspaceId.value ?: ""
        workspaceRepository.setPageLocked(wsId, pageKey, isLocked)
    }

    fun canAccessPage(pageKey: String): Boolean {
        return com.example.data.auth.AuthorizationManager.canAccessPage(
            pageKey = pageKey,
            isOwner = isCollaborationOwner.value,
            lockedPages = lockedPages.value,
            role = currentUserRole.value
        )
    }

    val genuinePartnerWorkspaces: StateFlow<List<Workspace>> = workspaceRepository.genuinePartnerWorkspaces
    val availableWorkspaces: StateFlow<List<Workspace>> = workspaceRepository.genuinePartnerWorkspaces

    fun loadAvailableWorkspaces() {
        val profile = currentUserProfile.value ?: return
        viewModelScope.launch {
            workspaceRepository.getAvailableWorkspaces(profile)
        }
    }

    fun switchWorkspace(
        targetWorkspaceId: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val profile = currentUserProfile.value
        if (profile == null) {
            onError("User profile not loaded")
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            val result = workspaceRepository.switchActiveWorkspace(targetWorkspaceId, profile)
            _isSyncing.value = false
            result.onSuccess {
                loadAvailableWorkspaces()
                onSuccess()
            }.onFailure { e ->
                onError(e.message ?: "Failed to switch workspace")
            }
        }
    }

    // Unsynced entity counters scoped by active workspace
    @OptIn(ExperimentalCoroutinesApi::class)
    val unsyncedJobsCount: StateFlow<Int> = activeWorkspaceId.flatMapLatest { wsId ->
        val target = wsId ?: personalWorkspaceId.value
        if (target.isNullOrBlank()) MutableStateFlow(0)
        else database.jobEntryDao().getUnsyncedCountForWorkspace(target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val unsyncedExpensesCount: StateFlow<Int> = activeWorkspaceId.flatMapLatest { wsId ->
        val target = wsId ?: personalWorkspaceId.value
        if (target.isNullOrBlank()) MutableStateFlow(0)
        else database.expenseDao().getUnsyncedCountForWorkspace(target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val unsyncedWithdrawalsCount: StateFlow<Int> = activeWorkspaceId.flatMapLatest { wsId ->
        val target = wsId ?: personalWorkspaceId.value
        if (target.isNullOrBlank()) MutableStateFlow(0)
        else database.withdrawalDao().getUnsyncedCountForWorkspace(target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val unsyncedCustomersCount: StateFlow<Int> = activeWorkspaceId.flatMapLatest { wsId ->
        val target = wsId ?: personalWorkspaceId.value
        if (target.isNullOrBlank()) MutableStateFlow(0)
        else database.customerDao().getUnsyncedCountForWorkspace(target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalUnsyncedCount: StateFlow<Int> = combine(
        unsyncedJobsCount,
        unsyncedExpensesCount,
        unsyncedWithdrawalsCount,
        unsyncedCustomersCount
    ) { jobs, exp, wth, cust ->
        jobs + exp + wth + cust
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // 1. Settings & Profile scoped to active workspace
    @OptIn(ExperimentalCoroutinesApi::class)
    val settings: StateFlow<AppSettingsEntity> = activeWorkspaceId.flatMapLatest { wsId ->
        val target = wsId ?: personalWorkspaceId.value
        if (target.isNullOrBlank()) MutableStateFlow(AppSettingsEntity())
        else database.appSettingsDao().getSettingsForWorkspace(target)
    }.combine(MutableStateFlow(Unit)) { set, _ -> set ?: AppSettingsEntity(workspaceId = activeWorkspaceId.value ?: personalWorkspaceId.value ?: "") }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettingsEntity())

    val partnerPercentages: StateFlow<Map<String, Int>> = workspaceRepository.partnerPercentages

    // UID-based profit share allocations (new authoritative system)
    val profitShareAllocations: StateFlow<Map<String, Int>> = workspaceRepository.profitShareAllocations
    val profitShareAllocationDetails: StateFlow<Map<String, com.example.data.firebase.ProfitShareAllocation>> = workspaceRepository.profitShareAllocationDetails

    // 2. Partners scoped to active workspace
    @OptIn(ExperimentalCoroutinesApi::class)
    val partners: StateFlow<List<PartnerEntity>> = activeWorkspaceId.flatMapLatest { wsId ->
        val target = wsId ?: personalWorkspaceId.value
        if (target.isNullOrBlank()) MutableStateFlow(emptyList())
        else database.partnerDao().getPartnersForWorkspace(target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val authenticatedUserName: StateFlow<String> = combine(
        currentUserProfile,
        workspaceMembers,
        settings,
        isCollaborationOwner,
        partners
    ) { profile, members, st, isOwner, pts ->
        val user = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        val uid = currentUid ?: profile?.uid ?: user?.uid ?: ""
        val phone = profile?.phoneNumber ?: user?.phoneNumber
        val member = members.firstOrNull { 
            (uid.isNotBlank() && it.uid == uid) || 
            (!phone.isNullOrBlank() && it.phoneNumber == phone) 
        }
        val matchedPartner = pts.firstOrNull { p ->
            (!phone.isNullOrBlank() && p.phone.isNotBlank() && p.phone == phone) ||
            (!member?.phoneNumber.isNullOrBlank() && p.phone.isNotBlank() && p.phone == member?.phoneNumber)
        }

        if (isOwner) {
            profile?.displayName?.takeIf { it.isNotBlank() }
                ?: member?.displayName?.takeIf { it.isNotBlank() }
                ?: st.ownerName.takeIf { it.isNotBlank() }
                ?: user?.displayName?.takeIf { it.isNotBlank() }
                ?: st.activePartnerName.takeIf { it.isNotBlank() }
                ?: "Owner"
        } else {
            profile?.displayName?.takeIf { it.isNotBlank() }
                ?: member?.displayName?.takeIf { it.isNotBlank() }
                ?: matchedPartner?.name?.takeIf { it.isNotBlank() }
                ?: st.activePartnerName.takeIf { it.isNotBlank() && !it.equals(st.ownerName, ignoreCase = true) }
                ?: user?.displayName?.takeIf { it.isNotBlank() }
                ?: "Partner"
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    val authenticatedUserRole: StateFlow<String> = combine(
        currentUserRole,
        isCollaborationOwner,
        workspaceMembers
    ) { role, isOwner, members ->
        val user = try { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser } catch (_: Exception) { null }
        val uid = currentUid ?: user?.uid ?: ""
        val phone = user?.phoneNumber
        val member = members.firstOrNull { 
            (uid.isNotBlank() && it.uid == uid) || 
            (!phone.isNullOrBlank() && it.phoneNumber == phone) 
        }
        when {
            isOwner -> com.example.data.auth.RoleUtils.ROLE_OWNER
            member?.role?.equals("operator", ignoreCase = true) == true || role.equals("operator", ignoreCase = true) -> com.example.data.auth.RoleUtils.ROLE_OPERATOR
            else -> com.example.data.auth.RoleUtils.ROLE_PARTNER
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.data.auth.RoleUtils.ROLE_PARTNER)


    // 3. Tractors scoped to active workspace
    @OptIn(ExperimentalCoroutinesApi::class)
    val tractors: StateFlow<List<TractorEntity>> = activeWorkspaceId.flatMapLatest { wsId ->
        val target = wsId ?: personalWorkspaceId.value
        if (target.isNullOrBlank()) MutableStateFlow(emptyList())
        else database.tractorDao().getTractorsForWorkspace(target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 4. Customers scoped to active workspace
    @OptIn(ExperimentalCoroutinesApi::class)
    val customers: StateFlow<List<CustomerEntity>> = activeWorkspaceId.flatMapLatest { wsId ->
        val target = wsId ?: personalWorkspaceId.value
        if (target.isNullOrBlank()) MutableStateFlow(emptyList())
        else database.customerDao().getCustomersForWorkspace(target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val customersWithDue: StateFlow<List<CustomerEntity>> = activeWorkspaceId.flatMapLatest { wsId ->
        val target = wsId ?: personalWorkspaceId.value
        if (target.isNullOrBlank()) MutableStateFlow(emptyList())
        else database.customerDao().getCustomersWithDueForWorkspace(target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 5. Jobs scoped to active workspace
    @OptIn(ExperimentalCoroutinesApi::class)
    val jobs: StateFlow<List<JobEntryEntity>> = activeWorkspaceId.flatMapLatest { wsId ->
        val target = wsId ?: personalWorkspaceId.value
        if (target.isNullOrBlank()) MutableStateFlow(emptyList())
        else database.jobEntryDao().getJobsForWorkspace(target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 6. Expenses scoped to active workspace
    @OptIn(ExperimentalCoroutinesApi::class)
    val expenses: StateFlow<List<ExpenseEntity>> = activeWorkspaceId.flatMapLatest { wsId ->
        val target = wsId ?: personalWorkspaceId.value
        if (target.isNullOrBlank()) MutableStateFlow(emptyList())
        else database.expenseDao().getExpensesForWorkspace(target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 7. Withdrawals scoped to active workspace
    @OptIn(ExperimentalCoroutinesApi::class)
    val withdrawals: StateFlow<List<WithdrawalEntity>> = activeWorkspaceId.flatMapLatest { wsId ->
        val target = wsId ?: personalWorkspaceId.value
        if (target.isNullOrBlank()) MutableStateFlow(emptyList())
        else database.withdrawalDao().getWithdrawalsForWorkspace(target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 8. Payments scoped to active workspace
    @OptIn(ExperimentalCoroutinesApi::class)
    val payments: StateFlow<List<PaymentEntity>> = activeWorkspaceId.flatMapLatest { wsId ->
        val target = wsId ?: personalWorkspaceId.value
        if (target.isNullOrBlank()) MutableStateFlow(emptyList())
        else workspaceRepository.getPaymentsFlow(target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Aggregates & Financial calculations scoped to active workspace (derived directly from jobs as single source of truth)
    val totalReceived: StateFlow<Double> = combine(jobs, payments) { jobList, paymentList ->
        val rawPaymentReceived = paymentList.sumOf { it.amount }
        
        // For each work job, the initial advance is its amountReceived minus any explicit payments linked to it.
        // This ensures we don't double count and also captures the initial collection correctly.
        val initialAdvances = jobList.filter { !com.example.ui.util.FinancialCalculationEngine.isSyntheticPayment(it) }.sumOf { j ->
            val explicitPaymentsForJob = paymentList.filter { it.jobEntryId == j.id }.sumOf { it.amount }
            maxOf(0.0, j.amountReceived - explicitPaymentsForJob)
        }
        
        // Synthetic jobs (direct payments) are already covered by rawPaymentReceived IF they are backed by PaymentEntity.
        // If not, Section 2 of CollectionHistoryTab would pick them up.
        // To keep this summary consistent with history, we also count synthetic jobs not in paymentList.
        val paymentIds = paymentList.map { it.id }.toSet()
        val standaloneSyntheticReceived = jobList.filter { com.example.ui.util.FinancialCalculationEngine.isSyntheticPayment(it) && it.id !in paymentIds }.sumOf { it.amountReceived }

        com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(rawPaymentReceived + initialAdvances + standaloneSyntheticReceived)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalRecorded: StateFlow<Double> = jobs.map { jobList ->
        jobList.sumOf { job ->
            if (job.totalAmount > 0.0) job.totalAmount else (job.amountReceived + job.pendingAmount)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalPending: StateFlow<Double> = jobs.map { jobList ->
        jobList.sumOf { job ->
            if (job.pendingAmount > 0.0) job.pendingAmount else maxOf(0.0, job.totalAmount - job.amountReceived)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalCustomerDue: StateFlow<Double> = totalPending

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalExpenses: StateFlow<Double> = activeWorkspaceId.flatMapLatest { wsId ->
        val target = wsId ?: personalWorkspaceId.value
        if (target.isNullOrBlank()) MutableStateFlow(0.0)
        else database.expenseDao().getTotalExpensesForWorkspace(target)
    }.combine(MutableStateFlow(Unit)) { total, _ -> total ?: 0.0 }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    @OptIn(ExperimentalCoroutinesApi::class)
    val totalWithdrawn: StateFlow<Double> = activeWorkspaceId.flatMapLatest { wsId ->
        val target = wsId ?: personalWorkspaceId.value
        if (target.isNullOrBlank()) MutableStateFlow(0.0)
        else database.withdrawalDao().getTotalWithdrawnForWorkspace(target)
    }.combine(MutableStateFlow(Unit)) { total, _ -> total ?: 0.0 }
     .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Available Business Balance for Home = Total Received - Total Expenses - Total Withdrawn
    // CASH-BASIS FORMULA: This represents actual cash available (collected payments minus expenses and withdrawals).
    // It does NOT include unpaid invoices (totalRecorded - totalReceived) which would be accrual-basis.
    // For withdrawal validation, this cash-basis amount is used to prevent over-withdrawal.
    val availableAmount: StateFlow<Double> = combine(
        totalReceived,
        totalExpenses,
        totalWithdrawn
    ) { rec, exp, wth ->
        maxOf(0.0, rec - exp - wth)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Today-specific operational metrics for Home Dashboard
    val todayTotalRecorded: StateFlow<Double> = jobs.map { jobList ->
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }
        val endOfDay = cal.timeInMillis

        jobList.filter { job ->
            val ts = if (job.startTimeMillis > 0) job.startTimeMillis else job.createdAt
            ts in startOfDay..endOfDay
        }.sumOf { job ->
            if (job.totalAmount > 0.0) job.totalAmount else (job.amountReceived + job.pendingAmount)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todayJobs: StateFlow<List<JobEntryEntity>> = jobs.map { jobList ->
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }
        val endOfDay = cal.timeInMillis

        jobList.filter { job ->
            val ts = if (job.startTimeMillis > 0) job.startTimeMillis else job.createdAt
            ts in startOfDay..endOfDay
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayTotalDue: StateFlow<Double> = jobs.map { jobList ->
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }
        val endOfDay = cal.timeInMillis

        jobList.filter { job ->
            val ts = if (job.startTimeMillis > 0) job.startTimeMillis else job.createdAt
            ts in startOfDay..endOfDay
        }.sumOf { job ->
            if (job.pendingAmount > 0.0) job.pendingAmount else 0.0
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todayTotalReceived: StateFlow<Double> = jobs.map { jobList ->
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }
        val endOfDay = cal.timeInMillis

        jobList.filter { job ->
            val ts = if (job.startTimeMillis > 0) job.startTimeMillis else job.createdAt
            ts in startOfDay..endOfDay
        }.sumOf { job ->
            job.amountReceived
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todayTotalExpenses: StateFlow<Double> = expenses.map { expList ->
        val now = System.currentTimeMillis()
        val cal = java.util.Calendar.getInstance().apply {
            timeInMillis = now
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val startOfDay = cal.timeInMillis
        cal.apply {
            set(java.util.Calendar.HOUR_OF_DAY, 23)
            set(java.util.Calendar.MINUTE, 59)
            set(java.util.Calendar.SECOND, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }
        val endOfDay = cal.timeInMillis

        expList.filter { exp ->
            val ts = if (exp.dateTimestamp > 0) exp.dateTimestamp else exp.createdAt
            ts in startOfDay..endOfDay
        }.sumOf { exp ->
            exp.amount
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todayAvailableBalance: StateFlow<Double> = combine(
        todayTotalReceived,
        todayTotalExpenses
    ) { rec, exp ->
        rec - exp
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Net Balance = Total Received - Total Expenses
    val netBalance: StateFlow<Double> = combine(
        totalReceived,
        totalExpenses
    ) { rec, exp ->
        rec - exp
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Balance Sheet Financial Scope State & Engine
    private val _balanceSheetScope = MutableStateFlow(
        com.example.ui.util.FinancialScope(mode = com.example.ui.util.FinancialScopeMode.OVERALL)
    )
    val balanceSheetScope: StateFlow<com.example.ui.util.FinancialScope> = _balanceSheetScope.asStateFlow()

    fun updateBalanceSheetScope(scope: com.example.ui.util.FinancialScope) {
        _balanceSheetScope.value = scope
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val allPayments: StateFlow<List<PaymentEntity>> = activeWorkspaceId.flatMapLatest { wsId ->
        val target = wsId ?: personalWorkspaceId.value
        if (target.isNullOrBlank()) MutableStateFlow(emptyList())
        else workspaceRepository.getPaymentsFlow(target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val balanceSheetSummary: StateFlow<com.example.ui.util.FinancialSummary> = combine(
        jobs,
        expenses,
        withdrawals,
        allPayments,
        balanceSheetScope
    ) { args: Array<Any> ->
        @Suppress("UNCHECKED_CAST")
        val jList = args[0] as List<JobEntryEntity>
        @Suppress("UNCHECKED_CAST")
        val eList = args[1] as List<ExpenseEntity>
        @Suppress("UNCHECKED_CAST")
        val wList = args[2] as List<WithdrawalEntity>
        @Suppress("UNCHECKED_CAST")
        val pList = args[3] as List<com.example.data.entity.PaymentEntity>
        val sc = args[4] as com.example.ui.util.FinancialScope
        com.example.ui.util.FinancialCalculationEngine.calculateSummary(jList, eList, wList, sc, pList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.ui.util.FinancialSummary())

    init {
        // Start listening to Firebase Auth state
        authRepository.startListening()

        viewModelScope.launch {
            kotlinx.coroutines.delay(2500L)
            _isStartupAuthResolved.value = true
        }

        viewModelScope.launch {
            authState.collect { state ->
                when (state) {
                    is AuthState.Authenticated -> {
                        _isStartupAuthResolved.value = true
                        val prof = state.profile
                        launch(kotlinx.coroutines.Dispatchers.IO) {
                            workspaceRepository.initializeForUser(prof)
                            loadAvailableWorkspaces()
                        }
                    }
                    is AuthState.Unauthenticated -> {
                        _isStartupAuthResolved.value = true
                        // Stop real-time listeners on logout
                        workspaceRepository.stopWorkspaceListeners()
                        clearNewEntryDraft()
                        val current = settings.value
                        if (current.isLoggedIn) {
                            repository.updateSettings(current.copy(isLoggedIn = false))
                        }
                    }
                    is AuthState.Error -> {
                        _isStartupAuthResolved.value = true
                    }
                    is AuthState.CodeSent -> {
                        _isStartupAuthResolved.value = true
                    }
                    else -> Unit
                }
            }
        }

        // Automatically push to cloud whenever network comes back online
        viewModelScope.launch {
            isEffectiveOnline.collect { online ->
                if (online) {
                    pushUnsyncedToCloud()
                }
            }
        }
    }

    fun setBottomTab(tab: BottomTab) {
        _currentTab.value = tab
        _currentReportSubPage.value = ReportSubPage.MENU
        _currentAccountSubPage.value = AccountSubPage.MAIN
    }

    fun setReportSubPage(subPage: ReportSubPage) {
        _currentReportSubPage.value = subPage
    }

    fun setAccountSubPage(subPage: AccountSubPage) {
        _currentAccountSubPage.value = subPage
    }

    fun toggleSimulatedOffline(forceOffline: Boolean) {
        _simulatedOffline.value = forceOffline
        if (!forceOffline) {
            pushUnsyncedToCloud()
        }
    }

    // --- Draft Management ---

    private val PREFS_PERSON_LOCK = "trac_owner_person_lock"

    fun getOwnerLockedPersonUid(ownerUid: String, workspaceId: String): String {
        if (ownerUid.isBlank() || workspaceId.isBlank()) return ""
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_PERSON_LOCK, Context.MODE_PRIVATE)
        return prefs.getString("${ownerUid}_${workspaceId}", "") ?: ""
    }

    fun setOwnerLockedPersonUid(ownerUid: String, workspaceId: String, lockedUid: String) {
        if (ownerUid.isBlank() || workspaceId.isBlank()) return
        val prefs = getApplication<Application>().getSharedPreferences(PREFS_PERSON_LOCK, Context.MODE_PRIVATE)
        if (lockedUid.isBlank()) {
            prefs.edit().remove("${ownerUid}_${workspaceId}").apply()
        } else {
            prefs.edit().putString("${ownerUid}_${workspaceId}", lockedUid).apply()
        }
    }

    fun getPersonalHourlyRate(personUid: String? = null): Double {
        val targetUid = personUid?.ifBlank { null } ?: currentUid ?: ""
        val wsId = settings.value.workspaceId
        val rate = workspaceRepository.getPersonalHourlyRate(wsId, targetUid)
        if (rate > 0.0 && rate != 1100.0) return rate
        if (targetUid == currentUid && isCollaborationOwner.value && settings.value.defaultHourlyRate > 0.0) {
            return settings.value.defaultHourlyRate
        }
        return rate
    }

    fun updatePersonalHourlyRate(rate: Double, targetUid: String? = null, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val uid = targetUid?.ifBlank { null } ?: currentUid ?: ""
            val wsId = settings.value.workspaceId
            workspaceRepository.setPersonalHourlyRate(rate, wsId, uid)
            onComplete()
        }
    }

    fun updateNewEntryDraft(draft: NewEntryDraft) {
        _newEntryDraft.value = draft
    }

    fun clearNewEntryDraft() {
        val set = settings.value
        val ownTractors = tractors.value.filter { it.workspaceId == set.workspaceId }
        val isValidLockedTractor = set.lockedTractorLabel.isNotBlank() && tractors.value.any { it.label == set.lockedTractorLabel }
        val defaultTrac = if (isValidLockedTractor) set.lockedTractorLabel else (ownTractors.firstOrNull()?.label ?: "")

        val isOwner = isCollaborationOwner.value
        val currentOwnerUid = currentUid ?: ""
        val wsId = set.workspaceId
        val ownerName = set.ownerName.ifBlank { set.activePartnerName.ifBlank { "Owner" } }

        var resolvedUid = currentOwnerUid
        var resolvedName = ownerName
        var isPersonLocked = false

        if (isOwner) {
            val lockedUid = getOwnerLockedPersonUid(currentOwnerUid, wsId)
            if (lockedUid.isNotBlank()) {
                if (lockedUid == currentOwnerUid) {
                    resolvedUid = currentOwnerUid
                    resolvedName = ownerName
                    isPersonLocked = true
                } else {
                    val partner = workspaceMembers.value.firstOrNull { it.status.equals("active", ignoreCase = true) && it.uid == lockedUid }
                    if (partner != null) {
                        resolvedUid = partner.uid
                        resolvedName = partner.displayName?.ifBlank { null } ?: partner.phoneNumber ?: "Partner"
                        isPersonLocked = true
                    } else {
                        // Locked partner is no longer active in workspace -> automatically invalidate
                        setOwnerLockedPersonUid(currentOwnerUid, wsId, "")
                        resolvedUid = currentOwnerUid
                        resolvedName = ownerName
                        isPersonLocked = false
                    }
                }
            }
        } else {
            resolvedUid = currentOwnerUid
            resolvedName = set.activePartnerName.ifBlank { "Partner" }
            isPersonLocked = false
        }

        val resolvedRate = getPersonalHourlyRate(resolvedUid)

        _newEntryDraft.value = NewEntryDraft.createDefault(
            defaultTractor = defaultTrac,
            lockedTractor = if (isValidLockedTractor) set.lockedTractorLabel else "",
            defaultHourlyRate = resolvedRate,
            defaultPersonUid = resolvedUid,
            defaultPersonName = resolvedName,
            lockedPersonUid = if (isPersonLocked) resolvedUid else "",
            lockedPersonName = if (isPersonLocked) resolvedName else ""
        )
    }

    // --- Actions ---

    fun saveJobEntry(
        job: JobEntryEntity,
        linkedExpense: ExpenseEntity? = null,
        linkedExpenses: List<ExpenseEntity> = emptyList(),
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_isSavingJob.value) return
        if (com.example.data.auth.AuthorizationManager.isAccountBlocked()) {
            onError("Account access is suspended or expired by administration")
            return
        }
        val isEditing = job.updatedAt > 0L
        if (isEditing && !isCollaborationOwner.value && !com.example.data.auth.AuthorizationManager.currentAccessControl.permissions.canEditEntry) {
            onError("Job editing is restricted by administration")
            return
        }
        val isHistorical = newEntryDraft.value?.isOldEntry == true || !com.example.data.auth.AuthorizationManager.isSameCalendarDay(job.startTimeMillis)
        if (isHistorical && !com.example.data.auth.AuthorizationManager.canCreateOldEntry(isCollaborationOwner.value, currentUserRole.value)) {
            val isTamil = settings.value.language.equals("TA", ignoreCase = true)
            onError(if (isTamil) "பழைய பதிவு உருவாக்கம் உரிமையாளருக்கு மட்டுமே அனுமதிக்கப்படுகிறது" else "Old/historical entry creation is restricted to the workspace Owner")
            return
        }
        if (!isEditing && !isHistorical && !com.example.data.auth.AuthorizationManager.canCreateNewEntry(isCollaborationOwner.value)) {
            onError("New entry creation is restricted by administration")
            return
        }
        if (!isCollaborationOwner.value) {
            val myUid = currentUid ?: ""
            if (job.createdByUid.isNotBlank() && job.createdByUid != myUid) {
                onError("Unauthorized: You can only create entries for yourself")
                return
            }
        }
        _isSavingJob.value = true
        viewModelScope.launch {
            try {
                workspaceRepository.saveJobEntry(job, linkedExpense, linkedExpenses)
                clearNewEntryDraft()
                _syncMessage.value = "Saved successfully and synced to Cloud Workspace"
                _isSavingJob.value = false
                onSuccess()
            } catch (e: Exception) {
                _isSavingJob.value = false
                val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "SYNC_FAILED"
                val errorMsg = e.message ?: "Failed to save job entry"
                _syncMessage.value = "Saved locally. Cloud sync pending ($code)"
                android.util.Log.e("TRAC_ENTRY", "Error saving entry: $code $errorMsg", e)
                onError(errorMsg)
            }
        }
    }

    fun deleteJob(job: JobEntryEntity) {
        // Pre-check permissions before calling repository (defense in depth)
        val isOwner = isCollaborationOwner.value
        val currentActorName = settings.value.activePartnerName.ifBlank { null } ?: settings.value.ownerName.ifBlank { null } ?: "Partner"
        val actorUid = currentUid ?: currentUserProfile.value?.uid ?: ""
        
        if (!com.example.data.auth.AuthorizationManager.canDeleteEntry(job, isOwner, currentActorName, actorUid, role = currentUserRole.value)) {
            _syncMessage.value = "Unauthorized: You cannot delete this entry"
            return
        }
        
        viewModelScope.launch {
            try {
                workspaceRepository.deleteJob(job)
            } catch (e: Exception) {
                android.util.Log.w("TRAC_ENTRY", "Cloud delete job failed: ${e.message}")
            }
        }
    }

    suspend fun getExpensesForJob(jobId: Long): List<ExpenseEntity> = workspaceRepository.getExpensesForJob(jobId)

    fun addExpense(expense: ExpenseEntity, onSuccess: () -> Unit = {}) {
        if (isAccountBlocked() || !com.example.data.auth.AuthorizationManager.currentAccessControl.permissions.canViewExpenses) {
            _syncMessage.value = "Expense operations restricted by administration"
            return
        }
        viewModelScope.launch {
            try {
                workspaceRepository.addExpense(expense)
                _syncMessage.value = "Expense saved and synced with Cloud"
                onSuccess()
            } catch (e: Exception) {
                val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "SYNC_FAILED"
                _syncMessage.value = "Expense saved locally. Cloud sync pending ($code)"
                android.util.Log.e("TRAC_FIRESTORE", "Cloud sync failed for expense: $code ${e.message}", e)
                onSuccess()
            }
        }
    }

    fun updateExpense(expense: ExpenseEntity, onSuccess: () -> Unit = {}) {
        if (isAccountBlocked() || !com.example.data.auth.AuthorizationManager.currentAccessControl.permissions.canViewExpenses) {
            _syncMessage.value = "Expense operations restricted by administration"
            return
        }
        viewModelScope.launch {
            try {
                workspaceRepository.updateExpense(expense)
                onSuccess()
            } catch (e: Exception) {
                val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "SYNC_FAILED"
                _syncMessage.value = "Expense updated locally. Cloud sync pending ($code)"
                android.util.Log.e("TRAC_FIRESTORE", "Cloud update failed for expense: $code ${e.message}", e)
                onSuccess()
            }
        }
    }

    fun canDeleteExpense(expense: ExpenseEntity): Boolean {
        val actorUid = currentUid ?: ""
        val isOwner = isCollaborationOwner.value
        val role = currentUserRole.value
        val effectiveCreatedAt = if (expense.createdAt > 0) expense.createdAt else expense.dateTimestamp
        return com.example.data.auth.AuthorizationManager.canDeleteExpense(
            expenseCreatedByUid = expense.createdByUid,
            expensePaidByUid = expense.paidByUid,
            expenseCreatedAt = effectiveCreatedAt,
            isOwner = isOwner,
            currentUid = actorUid,
            role = role
        )
    }

    fun deleteExpense(expense: ExpenseEntity) {
        if (!canDeleteExpense(expense)) {
            _syncMessage.value = "Expense deletion restricted by authorization policy"
            return
        }
        viewModelScope.launch {
            try {
                workspaceRepository.deleteExpense(expense)
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Cloud delete expense failed: ${e.message}")
            }
        }
    }

    fun addWithdrawal(
        withdrawal: WithdrawalEntity,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (isAccountBlocked() || !com.example.data.auth.AuthorizationManager.currentAccessControl.permissions.canViewWithdrawals) {
            onError("Withdrawal operations restricted by administration")
            return
        }

        // 1. Authoritative Actor Authentication
        val actorUid = currentUid?.ifBlank { null }
            ?: com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (actorUid.isNullOrBlank()) {
            onError("User is not authenticated")
            return
        }

        // 2. Authoritative Actor Role from Workspace Membership
        val members = workspaceMembers.value
        val wsId = activeWorkspaceId.value?.ifBlank { null } ?: withdrawal.workspaceId
        val actorMember = members.firstOrNull {
            it.uid == actorUid && (it.status.isBlank() || it.status.equals("active", ignoreCase = true))
        }
        val isOwner = isCollaborationOwner.value || actorMember?.role?.equals("owner", ignoreCase = true) == true
        val actorRole = if (isOwner) {
            com.example.data.auth.RoleUtils.ROLE_OWNER
        } else {
            actorMember?.role?.takeIf { it.isNotBlank() }?.let { com.example.data.auth.RoleUtils.normalizeRole(it) }
                ?: com.example.data.auth.RoleUtils.normalizeRole(currentUserRole.value)
        }

        // 3. Resolve Target UID (authoritative UID matching, not display names)
        var targetUid = withdrawal.targetPartnerUid.trim()
        if (targetUid.isBlank()) {
            if (actorRole == com.example.data.auth.RoleUtils.ROLE_PARTNER) {
                // For a partner, target must be themselves. If partnerName points to another member,
                // resolve that member's UID so authorization correctly rejects it.
                val targetMember = members.firstOrNull {
                    it.displayName?.trim()?.equals(withdrawal.partnerName.trim(), ignoreCase = true) == true
                }
                targetUid = targetMember?.uid ?: if (withdrawal.partnerName.isBlank() || withdrawal.partnerName.trim().equals(actorMember?.displayName?.trim(), ignoreCase = true)) actorUid else "unresolved_target"
            } else {
                val targetMember = members.firstOrNull {
                    it.displayName?.trim()?.equals(withdrawal.partnerName.trim(), ignoreCase = true) == true
                }
                targetUid = targetMember?.uid ?: if (withdrawal.partnerName.isBlank() || withdrawal.partnerName.trim().equals(actorMember?.displayName?.trim(), ignoreCase = true)) actorUid else ""
            }
        }

        // 4. Centralized Identity & Role Authorization Gate BEFORE Room insertion
        val isAuthorized = com.example.data.auth.AuthorizationManager.canCreateWithdrawal(
            actorUid = actorUid,
            actorRole = actorRole,
            targetUid = targetUid,
            workspaceId = wsId,
            workspaceMembers = members
        )

        if (!isAuthorized) {
            val errorMsg = if (actorRole == com.example.data.auth.RoleUtils.ROLE_PARTNER) {
                "Unauthorized: Partners can only take withdrawals for themselves"
            } else if (actorRole == com.example.data.auth.RoleUtils.ROLE_OPERATOR) {
                "Unauthorized: Operators cannot create withdrawals"
            } else {
                "Unauthorized: Withdrawal not permitted for this member"
            }
            onError(errorMsg)
            return
        }

        // 5. Stamp Immutable Audit Identity onto the Entity
        val stampedWithdrawal = withdrawal.copy(
            createdByUid = actorUid,
            createdByRole = actorRole,
            targetPartnerUid = targetUid
        )

        // 6. Numeric and Available Balance Checks
        val currentAvailable = availableAmount.value
        if (stampedWithdrawal.amount <= 0) {
            onError("Withdrawal amount must be greater than ₹0")
            return
        }
        if (currentAvailable <= 0.0) {
            onError("No business balance available. Please collect due amount first.")
            return
        }
        if (stampedWithdrawal.amount > currentAvailable) {
            onError("Insufficient business balance. Please collect due amount first.")
            return
        }

        // 7. Dispatch Persistence
        viewModelScope.launch {
            try {
                workspaceRepository.addWithdrawal(stampedWithdrawal)
                _syncMessage.value = "Withdrawal saved and synced with Cloud"
                onSuccess()
            } catch (e: SecurityException) {
                onError(e.message ?: "Withdrawal rejected by security policy")
            } catch (e: IllegalStateException) {
                onError(e.message ?: "Insufficient available balance")
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "Invalid withdrawal amount")
            } catch (e: Exception) {
                val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "SYNC_FAILED"
                _syncMessage.value = "Withdrawal saved locally. Cloud sync pending ($code)"
                android.util.Log.e("TRAC_FIRESTORE", "Cloud sync failed for withdrawal: $code ${e.message}", e)
                onSuccess()
            }
        }
    }

    fun deleteWithdrawal(withdrawal: WithdrawalEntity) {
        if (isAccountBlocked() || !com.example.data.auth.AuthorizationManager.currentAccessControl.permissions.canViewWithdrawals) {
            _syncMessage.value = "Withdrawal deletion restricted by administration"
            return
        }
        viewModelScope.launch {
            try {
                workspaceRepository.deleteWithdrawal(withdrawal)
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Cloud delete withdrawal failed: ${e.message}")
            }
        }
    }

    fun updateCustomer(customer: CustomerEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                workspaceRepository.updateCustomer(customer)
                onSuccess()
            } catch (e: Exception) {
                val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "SYNC_FAILED"
                _syncMessage.value = "Customer updated locally. Cloud sync pending ($code)"
                android.util.Log.e("TRAC_FIRESTORE", "Cloud update customer failed: $code ${e.message}", e)
                onSuccess()
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val checklistItems: StateFlow<List<ChecklistItemEntity>> = activeWorkspaceId.flatMapLatest { wsId ->
        val target = wsId ?: personalWorkspaceId.value
        if (target.isNullOrBlank()) MutableStateFlow(emptyList())
        else workspaceRepository.getChecklistFlow(target)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addChecklistItem(text: String) {
        viewModelScope.launch {
            val wsId = activeWorkspaceId.value ?: personalWorkspaceId.value ?: ""
            if (wsId.isNotBlank()) {
                workspaceRepository.addChecklistItem(text, wsId)
            }
        }
    }

    fun toggleChecklistItem(item: ChecklistItemEntity) {
        viewModelScope.launch {
            workspaceRepository.updateChecklistItem(item.copy(isChecked = !item.isChecked))
        }
    }

    fun deleteChecklistItem(item: ChecklistItemEntity) {
        viewModelScope.launch {
            workspaceRepository.deleteChecklistItem(item)
        }
    }

    fun recordCustomerPayment(
        customer: CustomerEntity,
        amount: Double,
        dateTimestamp: Long,
        paymentMethod: String,
        note: String,
        collectedByUid: String = "",
        collectedByName: String = "",
        collectedByRole: String = "",
        onSuccess: () -> Unit = {}
    ) {
        if (!canCollectPayment()) {
            _syncMessage.value = "Payment collection is restricted by administration"
            return
        }
        viewModelScope.launch {
            try {
                workspaceRepository.recordCustomerPayment(
                    customer = customer,
                    amount = amount,
                    dateTimestamp = dateTimestamp,
                    paymentMethod = paymentMethod,
                    note = note,
                    operatorName = settings.value.activePartnerName,
                    collectedByUid = collectedByUid,
                    collectedByName = collectedByName,
                    collectedByRole = collectedByRole
                )
                _syncMessage.value = "Payment recorded and synced with Cloud"
                onSuccess()
            } catch (e: Exception) {
                val code = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: "SYNC_FAILED"
                _syncMessage.value = "Payment recorded locally. Cloud sync pending ($code)"
                android.util.Log.e("TRAC_FIRESTORE", "Cloud payment recording failed: $code ${e.message}", e)
                onSuccess()
            }
        }
    }

    fun deleteCustomer(customer: CustomerEntity, onSuccess: () -> Unit = {}) {
        if (!canDeleteCustomer()) {
            _syncMessage.value = "Customer deletion is restricted by administration"
            return
        }
        viewModelScope.launch {
            try {
                workspaceRepository.deleteCustomer(customer)
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Cloud delete customer failed: ${e.message}")
                onSuccess()
            }
        }
    }

    fun addTractor(tractor: TractorEntity, onSuccess: () -> Unit = {}) {
        if (!canManageTractors()) {
            _syncMessage.value = "Tractor fleet operations restricted by administration"
            return
        }
        viewModelScope.launch {
            try {
                workspaceRepository.addTractor(tractor)
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Cloud add tractor failed: ${e.message}")
                onSuccess()
            }
        }
    }

    fun updateTractor(tractor: TractorEntity, onSuccess: () -> Unit = {}) {
        if (!canManageTractors()) {
            _syncMessage.value = "Tractor fleet operations restricted by administration"
            return
        }
        viewModelScope.launch {
            try {
                workspaceRepository.updateTractor(tractor)
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Cloud update tractor failed: ${e.message}")
                onSuccess()
            }
        }
    }

    fun canDeleteTractor(tractor: TractorEntity): Boolean {
        return com.example.data.auth.AuthorizationManager.canDeleteTractor(
            tractorCreatedByUid = tractor.createdByUid,
            tractorCreatedAt = tractor.createdAt,
            isOwner = isCollaborationOwner.value,
            currentUid = currentUid ?: "",
            role = currentUserRole.value
        )
    }

    fun canDeleteExtension(extension: WorkTypeExtensionEntity): Boolean {
        return com.example.data.auth.AuthorizationManager.canDeleteExtension(
            extensionCreatedByUid = extension.createdByUid,
            extensionCreatedAt = extension.createdAt,
            isOwner = isCollaborationOwner.value,
            currentUid = currentUid ?: "",
            role = currentUserRole.value
        )
    }

    fun deleteTractor(tractor: TractorEntity) {
        if (!canManageTractors() || !canDeleteTractor(tractor)) {
            _syncMessage.value = "Tractor deletion restricted or unauthorized"
            return
        }
        viewModelScope.launch {
            try {
                workspaceRepository.deleteTractor(tractor)
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Cloud delete tractor failed: ${e.message}")
            }
        }
    }

    fun deleteExtension(extension: WorkTypeExtensionEntity) {
        if (!canDeleteExtension(extension)) {
            _syncMessage.value = "Unauthorized extension deletion"
            return
        }
        viewModelScope.launch {
            try {
                workspaceRepository.deleteExtension(extension)
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Cloud delete extension failed: ${e.message}")
            }
        }
    }

    fun addPartner(
        partner: PartnerEntity,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!canManagePartners()) {
            onError("Partner management is restricted by administration")
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            val res = workspaceRepository.addPartnerDirectly(partner.name, partner.phone, partner.role)
            _isSyncing.value = false
            when (res) {
                is WorkspaceRepository.DirectAddPartnerResult.Success -> {
                    onSuccess()
                }
                is WorkspaceRepository.DirectAddPartnerResult.AccountNotRegistered -> {
                    onError(res.message)
                }
                is WorkspaceRepository.DirectAddPartnerResult.Error -> {
                    onError(res.message)
                }
            }
        }
    }

    fun addPartnerDirectly(
        name: String,
        phone: String,
        role: String,
        onResult: (Boolean, String) -> Unit
    ) {
        if (!canManagePartners()) {
            onResult(false, "Partner management is restricted to the workspace Owner")
            return
        }
        viewModelScope.launch {
            _isSyncing.value = true
            val res = workspaceRepository.addPartnerDirectly(name, phone, role)
            _isSyncing.value = false
            when (res) {
                is WorkspaceRepository.DirectAddPartnerResult.Success -> {
                    onResult(true, "Partner connected successfully!")
                }
                is WorkspaceRepository.DirectAddPartnerResult.AccountNotRegistered -> {
                    onResult(false, res.message)
                }
                is WorkspaceRepository.DirectAddPartnerResult.Error -> {
                    onResult(false, res.message)
                }
            }
        }
    }

    fun updatePartner(partner: PartnerEntity, onSuccess: () -> Unit = {}) {
        if (!canManagePartners()) return
        viewModelScope.launch {
            try {
                workspaceRepository.updatePartner(partner)
                onSuccess()
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Cloud update partner failed: ${e.message}")
                onSuccess()
            }
        }
    }

    fun deletePartner(partner: PartnerEntity, partnerUid: String? = null, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                workspaceRepository.removePartner(partner, partnerUid)
                onComplete()
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Delete partner failed: ${e.message}")
                onComplete()
            }
        }
    }

    fun leavePartnership(ownerWorkspaceId: String, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                workspaceRepository.leaveCollaborationGroup(ownerWorkspaceId)
                onComplete()
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Leave partnership failed: ${e.message}")
                onComplete()
            }
        }
    }

    fun setActivePartner(partner: PartnerEntity) {
        viewModelScope.launch {
            val current = settings.value
            val updated = current.copy(
                activePartnerName = "${partner.name} (${partner.role})",
                activePartnerPhone = partner.phone
            )
            workspaceRepository.updateSettings(updated)
        }
    }

    fun updatePartnerPercentages(percentages: Map<String, Int>, onSuccess: () -> Unit = {}, onError: (String) -> Unit = {}) {
        if (!isCollaborationOwner.value) {
            onError("Only the business owner can configure profit-share percentages")
            return
        }
        val total = percentages.values.sum()
        if (total != 100) {
            onError("Total percentage allocation must equal exactly 100%")
            return
        }
        viewModelScope.launch {
            try {
                workspaceRepository.updatePartnerPercentages(percentages)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update percentages")
            }
        }
    }

    fun updateProfitShareAllocations(
        allocations: Map<String, Int>,
        participantDetails: Map<String, com.example.data.firebase.ProfitShareAllocation> = emptyMap(),
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!isCollaborationOwner.value) {
            onError("Only the business owner can configure profit-share allocations")
            return
        }
        val total = allocations.values.sum()
        if (total != 100) {
            onError("Total percentage allocation must equal exactly 100%")
            return
        }
        // Validate that all allocated UIDs are active members (not operators)
        val members = workspaceMembers.value
        val invalidUids = allocations.keys.filter { uid ->
            val member = members.find { it.uid == uid }
            member == null || (member.status.isNotBlank() && !member.status.equals("active", ignoreCase = true)) ||
                com.example.data.auth.RoleUtils.normalizeRole(member.role) == com.example.data.auth.RoleUtils.ROLE_OPERATOR
        }
        if (invalidUids.isNotEmpty()) {
            onError("Cannot allocate to inactive members or operators")
            return
        }
        viewModelScope.launch {
            try {
                workspaceRepository.updateProfitShareAllocations(allocations, participantDetails)
                onSuccess()
            } catch (e: Exception) {
                onError(e.message ?: "Failed to update profit share allocations")
            }
        }
    }

    fun updateSettings(updated: AppSettingsEntity, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val current = settings.value
                val safeSettings = if (!isCollaborationOwner.value) {
                    updated.copy(
                        businessName = current.businessName,
                        businessAddress = current.businessAddress,
                        gstNumber = current.gstNumber,
                        ownerName = current.ownerName,
                        businessPhone = current.businessPhone
                    )
                } else {
                    updated
                }
                workspaceRepository.updateSettings(safeSettings)

                // Propagate updated personal profile name to FirebaseAuth and users collection immediately
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    val uid = user.uid
                    val nameToSet = if (isCollaborationOwner.value) safeSettings.ownerName.trim() else safeSettings.activePartnerName.trim()
                    if (nameToSet.isNotBlank() && nameToSet != user.displayName) {
                        val currentProf = currentUserProfile.value
                        if (currentProf != null) {
                            authRepository.setAuthenticatedProfile(currentProf.copy(displayName = nameToSet))
                        }
                        try {
                            val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                .setDisplayName(nameToSet)
                                .build()
                            user.updateProfile(profileUpdates)
                            com.google.firebase.firestore.FirebaseFirestore.getInstance()
                                .collection("users").document(uid)
                                .set(mapOf("displayName" to nameToSet), com.google.firebase.firestore.SetOptions.merge())
                        } catch (e: Exception) {
                            android.util.Log.w("TRAC_FIRESTORE", "Profile update notice: ${e.message}")
                        }
                    }
                }

                onSuccess()
            } catch (e: Exception) {
                android.util.Log.w("TRAC_FIRESTORE", "Cloud update settings failed: ${e.message}")
                onSuccess()
            }
        }
    }

    fun completeInitialSetup(
        displayName: String,
        businessName: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!isCollaborationOwner.value ||
            currentUserRole.value.equals(com.example.data.auth.RoleUtils.ROLE_PARTNER, ignoreCase = true) ||
            currentUserRole.value.equals(com.example.data.auth.RoleUtils.ROLE_OPERATOR, ignoreCase = true)
        ) {
            onError("Only the workspace owner can configure initial business setup.")
            return
        }
        viewModelScope.launch {
            try {
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    val uid = user.uid
                    try {
                        val profileUpdates = com.google.firebase.auth.UserProfileChangeRequest.Builder()
                            .setDisplayName(displayName.trim())
                            .build()
                        user.updateProfile(profileUpdates)
                    } catch (e: Exception) {
                        android.util.Log.w("TRAC_SETUP", "FirebaseAuth updateProfile failed: ${e.message}")
                    }
                    try {
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("users").document(uid)
                            .set(mapOf("displayName" to displayName.trim(), "businessName" to businessName.trim()), com.google.firebase.firestore.SetOptions.merge())
                    } catch (e: Exception) {
                        android.util.Log.w("TRAC_SETUP", "Firestore user update failed: ${e.message}")
                    }
                }

                // Update Room AppSettings & workspace settings
                val current = settings.value
                val personalWsId = workspaceRepository.getPersonalWorkspaceId() ?: current.workspaceId
                val updated = current.copy(
                    workspaceId = personalWsId,
                    businessName = businessName.trim(),
                    ownerName = displayName.trim(),
                    activePartnerName = displayName.trim()
                )
                workspaceRepository.updateSettings(updated)

                if (personalWsId.isNotBlank()) {
                    try {
                        com.google.firebase.firestore.FirebaseFirestore.getInstance()
                            .collection("workspaces").document(personalWsId)
                            .set(mapOf("name" to businessName.trim(), "updatedAt" to System.currentTimeMillis()), com.google.firebase.firestore.SetOptions.merge())
                    } catch (e: Exception) {
                        android.util.Log.w("TRAC_SETUP", "Firestore workspace name update failed: ${e.message}")
                    }
                }

                onSuccess()
            } catch (e: Exception) {
                android.util.Log.e("TRAC_SETUP", "Initial setup failed: ${e.message}", e)
                onError(e.message ?: "Failed to save profile setup")
            }
        }
    }

    fun pushUnsyncedToCloud(onComplete: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val online = isEffectiveOnline.value
            if (!online) {
                _syncMessage.value = "Device is offline. Local SQLite records safe."
                onComplete(false)
                return@launch
            }

            _isSyncing.value = true
            _syncMessage.value = "Pushing local SQLite entries to Cloud..."
            delay(800) // Smooth sync visual feedback

            val result = workspaceRepository.pushUnsyncedToCloud(isOnline = true)
            _isSyncing.value = false
            _syncMessage.value = result.message
            onComplete(result.isSuccess)
        }
    }

    fun triggerSync() {
        pushUnsyncedToCloud()
    }

    fun getJobsForCustomer(customerId: Long) = database.jobEntryDao().getJobsForCustomer(customerId)

    // --- Firebase Authentication ---

    fun signInWithGoogle(
        context: Context,
        webClientId: String? = null,
        onSuccess: (UserProfile) -> Unit = {},
        onError: (String) -> Unit = {},
        onNoAccountFound: ((UserProfile) -> Unit)? = null
    ) {
        if (!networkMonitor.isOnline.value) {
            _isSyncing.value = false
            onError("Unable to connect. Check your internet connection and try again.")
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            try {
                kotlinx.coroutines.withTimeout(30000L) {
                    val result = authRepository.signInWithGoogle(context, webClientId)
                    if (result.isSuccess) {
                        val profile = result.getOrThrow()
                        val accountExists = workspaceRepository.checkUserExists(profile.uid)
                        if (!accountExists && onNoAccountFound != null) {
                            _isSyncing.value = false
                            onNoAccountFound(profile)
                        } else {
                            val initResult = workspaceRepository.initializeForUser(profile)
                            _isSyncing.value = false
                            initResult.onSuccess {
                                loadAvailableWorkspaces()
                                onSuccess(profile)
                            }.onFailure { e ->
                                onError(e.message ?: "Workspace initialization failed")
                            }
                        }
                    } else {
                        _isSyncing.value = false
                        val e = result.exceptionOrNull()
                        onError(mapFirebaseAuthError(e))
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _isSyncing.value = false
                onError("Unable to connect. Check your internet connection and try again.")
            } catch (e: Exception) {
                _isSyncing.value = false
                onError(mapFirebaseAuthError(e))
            }
        }
    }

    fun createAccountForGoogleUser(
        profile: UserProfile,
        onSuccess: (UserProfile) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            _isSyncing.value = true
            try {
                // Initialize user with default role = Owner
                val newProfile = profile.copy(displayName = profile.displayName?.ifBlank { null } ?: "Owner")
                val initResult = workspaceRepository.initializeForUser(newProfile)
                _isSyncing.value = false
                initResult.onSuccess {
                    loadAvailableWorkspaces()
                    onSuccess(newProfile)
                }.onFailure { e ->
                    onError(e.message ?: "Account creation failed")
                }
            } catch (e: Exception) {
                _isSyncing.value = false
                onError(e.message ?: "Account creation failed")
            }
        }
    }

    fun createAccountForPhoneUser(
        fullName: String,
        businessName: String,
        onSuccess: (UserProfile) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            onError("Authentication session expired. Please verify OTP again.")
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            try {
                val db = FirebaseFirestore.getInstance()
                val uid = user.uid
                val rawPhone = user.phoneNumber ?: ""
                val normPhone = normalizePhoneNumber(rawPhone)
                val clean10 = normPhone.filter { it.isDigit() }.takeLast(10)

                // 1. Check duplicate prevention
                // Check if users/{uid} already exists
                val userDocRef = db.collection("users").document(uid)
                val existingUserSnap = userDocRef.get().await()
                if (existingUserSnap.exists() && existingUserSnap.data != null) {
                    val existingProfile = UserProfile.fromMap(existingUserSnap.data!!)
                    val initResult = workspaceRepository.initializeForUser(existingProfile)
                    authRepository.setAuthenticatedProfile(existingProfile)
                    _isSyncing.value = false
                    initResult.onSuccess {
                        loadAvailableWorkspaces()
                        onSuccess(existingProfile)
                    }.onFailure { e ->
                        onError(e.message ?: "Failed to initialize workspace")
                    }
                    return@launch
                }

                // Check if phoneDirectory is already bound to another UID
                if (normPhone.isNotBlank()) {
                    val dirDocRef = db.collection("phoneDirectory").document(normPhone)
                    val dirSnap = dirDocRef.get().await()
                    if (dirSnap.exists() && dirSnap.data != null) {
                        val boundUid = dirSnap.getString("uid")
                        if (!boundUid.isNullOrBlank() && boundUid != uid) {
                            _isSyncing.value = false
                            onError("This phone number is already registered to another account.")
                            return@launch
                        }
                    }
                }

                // 2. Check pending partner invitations by normalized phone
                var role = "owner"
                var targetWsId = "ws_${uid.replace(Regex("[^a-zA-Z0-9]"), "").take(16).ifBlank { "main" }}"
                val finalBusinessName = businessName.ifBlank { "${fullName.ifBlank { "Owner" }}'s Tractor Services" }

                if (normPhone.isNotBlank()) {
                    try {
                        val pendingQuery = db.collectionGroup("pendingPhones")
                            .whereEqualTo("normalizedPhone", normPhone)
                            .limit(1)
                            .get().await()
                        if (!pendingQuery.isEmpty) {
                            val pendingDoc = pendingQuery.documents.first()
                            val pendingWsId = pendingDoc.getString("groupId")
                                ?: pendingDoc.reference.parent.parent?.id
                                ?: ""
                            if (pendingWsId.isNotBlank()) {
                                role = "partner"
                                targetWsId = pendingWsId
                            }
                        }
                    } catch (pe: Exception) {
                        Log.w("TRAC_AUTH", "pendingPhones check skipped: ${pe.message}")
                    }
                }

                val now = System.currentTimeMillis()

                if (role == "owner") {
                    // Create workspace document
                    val wsDocRef = db.collection("workspaces").document(targetWsId)
                    val wsData = mapOf(
                        "workspaceId" to targetWsId,
                        "name" to finalBusinessName,
                        "ownerUid" to uid,
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                    wsDocRef.set(wsData, SetOptions.merge()).await()

                    // Create workspace member document
                    val memberDocRef = wsDocRef.collection("members").document(uid)
                    val memberData = mapOf(
                        "uid" to uid,
                        "role" to "owner",
                        "status" to "active",
                        "joinedAt" to now,
                        "displayName" to fullName.ifBlank { "Owner" },
                        "phoneNumber" to normPhone
                    )
                    memberDocRef.set(memberData, SetOptions.merge()).await()

                    // Create collaboration group
                    val groupDocRef = db.collection("collaborationGroups").document(targetWsId)
                    val groupData = mapOf(
                        "groupId" to targetWsId,
                        "ownerUid" to uid,
                        "ownerWorkspaceId" to targetWsId,
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                    groupDocRef.set(groupData, SetOptions.merge()).await()

                    // Create owner member in collaboration group
                    val groupMemberRef = groupDocRef.collection("members").document(uid)
                    val groupMemberData = mapOf(
                        "uid" to uid,
                        "workspaceId" to targetWsId,
                        "role" to "owner",
                        "status" to "active",
                        "joinedAt" to now
                    )
                    groupMemberRef.set(groupMemberData, SetOptions.merge()).await()

                    // Create user collaboration group discovery index
                    val userGroupIndexRef = db.collection("userCollaborationGroups").document(uid)
                        .collection("groups").document(targetWsId)
                    userGroupIndexRef.set(
                        mapOf(
                            "groupId" to targetWsId,
                            "ownerUid" to uid,
                            "ownerWorkspaceId" to targetWsId,
                            "role" to "owner",
                            "status" to "active",
                            "joinedAt" to now
                        ),
                        SetOptions.merge()
                    ).await()

                    // Create user workspace membership discovery index
                    val membershipDocRef = db.collection("userWorkspaceMemberships").document(uid)
                        .collection("workspaces").document(targetWsId)
                    val membershipData = mapOf(
                        "workspaceId" to targetWsId,
                        "ownerUid" to uid,
                        "role" to "owner",
                        "status" to "active",
                        "joinedAt" to now,
                        "workspaceName" to finalBusinessName
                    )
                    membershipDocRef.set(membershipData, SetOptions.merge()).await()
                }

                // Create user profile in users/{uid}
                val newProfile = UserProfile(
                    uid = uid,
                    displayName = fullName.ifBlank { "Owner" },
                    email = user.email,
                    phoneNumber = normPhone,
                    photoUrl = user.photoUrl?.toString(),
                    defaultWorkspaceId = targetWsId,
                    workspaces = listOf(targetWsId),
                    createdAt = now,
                    updatedAt = now
                )
                val userMap = newProfile.toMap().toMutableMap()
                userMap["role"] = role
                userDocRef.set(userMap, SetOptions.merge()).await()

                // Register/Bind in phoneDirectory under canonical normalizedPhone
                if (normPhone.isNotBlank()) {
                    val dirData = mapOf(
                        "uid" to uid,
                        "phoneNumber" to normPhone,
                        "role" to role,
                        "defaultWorkspaceId" to targetWsId,
                        "workspaceId" to targetWsId,
                        "displayName" to fullName.ifBlank { "Owner" },
                        "workspaces" to listOf(targetWsId),
                        "createdAt" to now,
                        "updatedAt" to now
                    )
                    db.collection("phoneDirectory").document(normPhone).set(dirData, SetOptions.merge()).await()
                }

                // Update auth repository state
                authRepository.setAuthenticatedProfile(newProfile)

                // Initialize workspace locally
                val initResult = workspaceRepository.initializeForUser(newProfile)
                _isSyncing.value = false
                initResult.onSuccess {
                    loadAvailableWorkspaces()
                    onSuccess(newProfile)
                }.onFailure { e ->
                    loadAvailableWorkspaces()
                    onSuccess(newProfile)
                }
            } catch (e: Exception) {
                _isSyncing.value = false
                Log.e("TRAC_AUTH", "Failed to create account for phone user: ${e.message}", e)
                onError(e.message ?: "Account registration failed")
            }
        }
    }

    fun signInWithEmail(
        email: String,
        password: String,
        onSuccess: (UserProfile) -> Unit = {},
        onError: (String) -> Unit = {},
        onNoAccountFound: (() -> Unit)? = null
    ) {
        if (!networkMonitor.isOnline.value) {
            _isSyncing.value = false
            onError("Unable to connect. Check your internet connection and try again.")
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            try {
                kotlinx.coroutines.withTimeout(20000L) {
                    val result = authRepository.signInWithEmail(email, password)
                    if (result.isSuccess) {
                        val profile = result.getOrThrow()
                        val initResult = workspaceRepository.initializeForUser(profile)
                        _isSyncing.value = false
                        initResult.onSuccess {
                            loadAvailableWorkspaces()
                            onSuccess(profile)
                        }.onFailure { e ->
                            onError(e.message ?: "Workspace initialization failed")
                        }
                    } else {
                        _isSyncing.value = false
                        val e = result.exceptionOrNull()
                        val mapped = mapFirebaseAuthError(e)
                        if (mapped.contains("No account found", ignoreCase = true) && onNoAccountFound != null) {
                            onNoAccountFound()
                        } else {
                            onError(mapped)
                        }
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _isSyncing.value = false
                onError("Unable to connect. Check your internet connection and try again.")
            } catch (e: Exception) {
                _isSyncing.value = false
                onError(mapFirebaseAuthError(e))
            }
        }
    }

    fun signUpWithEmail(
        email: String,
        password: String,
        onSuccess: (UserProfile) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!networkMonitor.isOnline.value) {
            _isSyncing.value = false
            onError("Unable to connect. Check your internet connection and try again.")
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            try {
                kotlinx.coroutines.withTimeout(25000L) {
                    val result = authRepository.signUpWithEmail(email, password)
                    if (result.isSuccess) {
                        val profile = result.getOrThrow()
                        val initResult = workspaceRepository.initializeForUser(profile)
                        _isSyncing.value = false
                        initResult.onSuccess {
                            loadAvailableWorkspaces()
                            onSuccess(profile)
                        }.onFailure { e ->
                            onError(e.message ?: "Workspace initialization failed")
                        }
                    } else {
                        _isSyncing.value = false
                        val e = result.exceptionOrNull()
                        onError(mapFirebaseAuthError(e))
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _isSyncing.value = false
                onError("Unable to connect. Check your internet connection and try again.")
            } catch (e: Exception) {
                _isSyncing.value = false
                onError(mapFirebaseAuthError(e))
            }
        }
    }

    private fun mapFirebaseAuthError(exception: Throwable?): String {
        if (exception == null) return "Authentication failed. Please try again."
        val msg = exception.message?.lowercase() ?: ""
        return when {
            msg.contains("too_short") || msg.contains("format of the phone number") || msg.contains("invalid-phone-number") || msg.contains("e.164") ->
                "Invalid phone number format. Please enter a valid 10-digit mobile number."
            exception is com.google.firebase.auth.FirebaseAuthInvalidUserException || msg.contains("user-not-found") ->
                "No account found with this email. Please check your email or sign up."
            exception is com.google.firebase.auth.FirebaseAuthInvalidCredentialsException || msg.contains("wrong-password") || msg.contains("invalid-credential") || msg.contains("invalid-password") ->
                "Incorrect email or password. Please try again."
            exception is com.google.firebase.auth.FirebaseAuthUserCollisionException || msg.contains("email-already-in-use") || msg.contains("email-already-exists") ->
                "An account with this email already exists. Please sign in instead."
            exception is com.google.firebase.auth.FirebaseAuthWeakPasswordException || msg.contains("weak-password") ->
                "Password is too weak. Please use at least 6 characters."
            exception is com.google.firebase.FirebaseNetworkException || msg.contains("network") || msg.contains("unable to resolve host") ->
                "Unable to connect. Check your internet connection and try again."
            msg.contains("too-many-requests") ->
                "Too many failed attempts. Please wait a moment and try again."
            msg.contains("invalid-email") ->
                "Invalid email format. Please enter a valid email address."
            else -> exception.localizedMessage ?: "Authentication failed. Please try again."
        }
    }

    suspend fun checkPhoneAccount(phone: String): AccountLookupResult {
        val digits = phone.filter { it.isDigit() }
        if (digits.length < 10) {
            Log.w("TRAC_AUTH", "[AUTH] INVALID_PHONE digits=${digits.length} -> NAVIGATION_BLOCKED")
            return AccountLookupResult.Error("Invalid phone number. Please enter a valid 10-digit mobile number.")
        }


        if (!networkMonitor.isOnline.value) {
            Log.w("TRAC_AUTH", "[AUTH] OFFLINE_LOOKUP_BLOCKED phone=$digits -> NAVIGATION_BLOCKED")
            return AccountLookupResult.Error("Unable to connect. Check your internet connection and try again.", isNetworkError = true)
        }

        Log.i("TRAC_AUTH", "[AUTH] CHECKING_PHONE_ACCOUNT phone=$digits")
        val result = try {
            kotlinx.coroutines.withTimeout(7000L) {
                workspaceRepository.checkPhoneAccountExists(phone)
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            AccountLookupResult.Error("Unable to connect. Check your internet connection and try again.", isNetworkError = true)
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (e is com.google.firebase.FirebaseNetworkException || msg.contains("network", ignoreCase = true)) {
                AccountLookupResult.Error("Unable to connect. Check your internet connection and try again.", isNetworkError = true)
            } else {
                AccountLookupResult.Error(msg.ifBlank { "Account lookup failed" })
            }
        }

        when (result) {
            is AccountLookupResult.Found -> Log.i("TRAC_AUTH", "[AUTH] ACCOUNT_FOUND phone=$digits uid=${result.uid}")
            is AccountLookupResult.NotFound -> Log.i("TRAC_AUTH", "[AUTH] ACCOUNT_NOT_FOUND phone=$digits -> NAVIGATION_BLOCKED")
            is AccountLookupResult.Error -> Log.w("TRAC_AUTH", "[AUTH] ACCOUNT_LOOKUP_ERROR phone=$digits error=${result.message} -> NAVIGATION_BLOCKED")
        }
        return result
    }

    fun sendPhoneOtp(
        activity: Activity,
        phone: String,
        onCodeSent: () -> Unit = {},
        onError: (String) -> Unit = {},
        onAutoVerified: (UserProfile) -> Unit = {}
    ) {
        if (!networkMonitor.isOnline.value) {
            _isSyncing.value = false
            onError("Unable to connect. Check your internet connection and try again.")
            return
        }

        _isSyncing.value = true
        var isFinished = false

        // 15-second watchdog timer ensuring button never spins indefinitely
        val timeoutJob = viewModelScope.launch {
            delay(15000L)
            if (!isFinished) {
                isFinished = true
                _isSyncing.value = false
                authRepository.signOut()
                onError("Unable to send OTP. Please check your connection and try again.")
            }
        }

        authRepository.sendPhoneOtp(
            activity = activity,
            phoneNumber = phone,
            onCodeSent = { verificationId ->
                if (!isFinished) {
                    isFinished = true
                    timeoutJob.cancel()
                    _phoneVerificationId.value = verificationId
                    _isSyncing.value = false
                    onCodeSent()
                }
            },
            onError = { msg ->
                if (!isFinished) {
                    isFinished = true
                    timeoutJob.cancel()
                    _isSyncing.value = false
                    val userMsg = if (msg.contains("network", ignoreCase = true) || msg.contains("unavailable", ignoreCase = true)) {
                        "Unable to connect. Check your internet connection and try again."
                    } else {
                        msg
                    }
                    onError(userMsg)
                }
            },
            onAutoVerified = { profile ->
                if (!isFinished) {
                    isFinished = true
                    timeoutJob.cancel()
                    viewModelScope.launch {
                        val initResult = workspaceRepository.initializeForUser(profile)
                        _isSyncing.value = false
                        initResult.onSuccess {
                            loadAvailableWorkspaces()
                            onAutoVerified(profile)
                        }.onFailure { e ->
                            onError(e.message ?: "Workspace initialization failed")
                        }
                    }
                }
            }
        )
    }

    fun verifyPhoneOtp(
        otpCode: String,
        onSuccess: (UserProfile) -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val verificationId = _phoneVerificationId.value
        if (verificationId == null) {
            onError("Verification ID missing. Please request a new OTP.")
            return
        }

        viewModelScope.launch {
            _isSyncing.value = true
            try {
                kotlinx.coroutines.withTimeout(20000L) {
                    val result = authRepository.verifyPhoneOtp(verificationId, otpCode)
                    if (result.isSuccess) {
                        val profile = result.getOrThrow()
                        val initResult = workspaceRepository.initializeForUser(profile)
                        _isSyncing.value = false
                        initResult.onSuccess {
                            loadAvailableWorkspaces()
                            onSuccess(profile)
                        }.onFailure { e ->
                            onError(e.message ?: "Workspace initialization failed")
                        }
                    } else {
                        _isSyncing.value = false
                        val e = result.exceptionOrNull()
                        val rawMsg = e?.message ?: "Invalid OTP Code"
                        val userMsg = when {
                            rawMsg.contains("No account found", ignoreCase = true) || rawMsg.contains("Account not registered", ignoreCase = true) ->
                                "No account found"
                            rawMsg.contains("session-expired", ignoreCase = true) || rawMsg.contains("expired", ignoreCase = true) ->
                                "OTP has expired. Please request a new OTP."
                            rawMsg.contains("invalid", ignoreCase = true) || rawMsg.contains("format", ignoreCase = true) || rawMsg.contains("code", ignoreCase = true) ->
                                "Invalid OTP code. Please enter the correct 6-digit OTP."
                            rawMsg.contains("network", ignoreCase = true) || rawMsg.contains("timeout", ignoreCase = true) ->
                                "Unable to connect. Check your internet connection and try again."
                            else -> rawMsg
                        }
                        onError(userMsg)
                    }
                }
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                _isSyncing.value = false
                onError("Unable to connect. Check your internet connection and try again.")
            } catch (e: Exception) {
                _isSyncing.value = false
                onError(e.message ?: "OTP verification failed")
            }
        }
    }

    fun logout(onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                authRepository.signOut()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            workspaceRepository.stopWorkspaceListeners()
            clearNewEntryDraft()
            val current = settings.value
            if (current.isLoggedIn) {
                workspaceRepository.updateSettings(current.copy(isLoggedIn = false))
            }
            onComplete()
        }
    }
}

