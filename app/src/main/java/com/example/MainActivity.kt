package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import com.example.ui.components.AppBottomNav
import com.example.ui.components.AppTopHeader
import com.example.ui.components.StartupSplashScreen
import com.example.ui.screens.account.AccountScreen
import com.example.ui.screens.auth.FirstAccountSetupDialog
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.entry.NewEntryScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.report.ReportScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.AccountSubPage
import com.example.ui.viewmodel.BottomTab
import com.example.ui.viewmodel.MainViewModel
import com.example.ui.viewmodel.ReportSubPage

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContent(viewModel = viewModel, onShowToast = { msg ->
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                })
            }
        }
    }
}

@Composable
fun MainAppContent(
    viewModel: MainViewModel,
    onShowToast: (String) -> Unit
) {
    val settings by viewModel.settings.collectAsState()
    val partners by viewModel.partners.collectAsState()
    val tractors by viewModel.tractors.collectAsState()
    val customers by viewModel.customers.collectAsState()
    val jobs by viewModel.jobs.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val withdrawals by viewModel.withdrawals.collectAsState()

    val totalReceived by viewModel.totalReceived.collectAsState()
    val totalRecorded by viewModel.totalRecorded.collectAsState()
    val totalPending by viewModel.totalPending.collectAsState()
    val totalCustomerDue by viewModel.totalCustomerDue.collectAsState()
    val totalExpenses by viewModel.totalExpenses.collectAsState()
    val totalWithdrawn by viewModel.totalWithdrawn.collectAsState()
    val availableAmount by viewModel.availableAmount.collectAsState()
    val netBalance by viewModel.netBalance.collectAsState()
    val balanceSheetScope by viewModel.balanceSheetScope.collectAsState()

    val todayTotalRecorded by viewModel.todayTotalRecorded.collectAsState()
    val todayTotalDue by viewModel.todayTotalDue.collectAsState()
    val todayTotalExpenses by viewModel.todayTotalExpenses.collectAsState()
    val todayAvailableBalance by viewModel.todayAvailableBalance.collectAsState()
    val todayJobs by viewModel.todayJobs.collectAsState()
    val payments by viewModel.allPayments.collectAsState()

    val currentTab by viewModel.currentTab.collectAsState()
    val newEntryDraft by viewModel.newEntryDraft.collectAsState()
    val currentReportSubPage by viewModel.currentReportSubPage.collectAsState()
    val currentAccountSubPage by viewModel.currentAccountSubPage.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val cloudConnectionState by viewModel.cloudConnectionState.collectAsState()
    val isOnline by viewModel.isEffectiveOnline.collectAsState()
    val totalUnsyncedCount by viewModel.totalUnsyncedCount.collectAsState()
    val unsyncedJobsCount by viewModel.unsyncedJobsCount.collectAsState()
    val unsyncedExpensesCount by viewModel.unsyncedExpensesCount.collectAsState()
    val unsyncedWithdrawalsCount by viewModel.unsyncedWithdrawalsCount.collectAsState()
    val unsyncedCustomersCount by viewModel.unsyncedCustomersCount.collectAsState()
    val unsyncedPaymentsCount by viewModel.unsyncedPaymentsCount.collectAsState()
    val simulatedOffline by viewModel.simulatedOffline.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()
    val rejectedOperationCount by viewModel.rejectedOperationCount.collectAsState()
    val blockedOperationCount by viewModel.blockedOperationCount.collectAsState()
    val retainedOperationWarning by viewModel.retainedOperationWarning.collectAsState()
    val retainedHomeActions by viewModel.pendingActions.collectAsState()
    val pendingInvitations by viewModel.pendingInvitations.collectAsState()
    val activeWorkspaceId by viewModel.activeWorkspaceId.collectAsState()
    val workspaceMembers by viewModel.workspaceMembers.collectAsState()
    val activeWorkspaceMembers by viewModel.activeWorkspaceMembers.collectAsState()
    val isCollaborationOwner by viewModel.isCollaborationOwner.collectAsState()
    val isSavingJob by viewModel.isSavingJob.collectAsState()
    val lockedPages by viewModel.lockedPages.collectAsState()
    val checklistItems by viewModel.checklistItems.collectAsState()
    val workTypeExtensions by viewModel.workTypeExtensions.collectAsState()
    val authenticatedUserName by viewModel.authenticatedUserName.collectAsState()
    val authenticatedUserRole by viewModel.authenticatedUserRole.collectAsState()
    val profitShareAllocations by viewModel.profitShareAllocations.collectAsState()
    val profitShareAllocationDetails by viewModel.profitShareAllocationDetails.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Handle Hardware Back Button
    BackHandler(enabled = currentTab == BottomTab.REPORT && currentReportSubPage != ReportSubPage.MENU) {
        viewModel.setReportSubPage(ReportSubPage.MENU)
    }
    BackHandler(enabled = currentTab == BottomTab.ACCOUNT && currentAccountSubPage != AccountSubPage.MAIN) {
        viewModel.setAccountSubPage(AccountSubPage.MAIN)
    }
    BackHandler(enabled = currentTab != BottomTab.HOME && currentReportSubPage == ReportSubPage.MENU && currentAccountSubPage == AccountSubPage.MAIN) {
        viewModel.setBottomTab(BottomTab.HOME)
    }

    val isTamil = settings.language.equals("TA", ignoreCase = true)
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity
    val authState by viewModel.authState.collectAsState()



    val topBarTitle: String
    val showBackButton: Boolean
    val onBackAction: (() -> Unit)?
    val rightActionIcon: androidx.compose.ui.graphics.vector.ImageVector?
    val onRightActionClick: (() -> Unit)?

    when (currentTab) {
        BottomTab.HOME -> {
            topBarTitle = if (isTamil) "AIDHUNT டிராக்" else "AIDHUNT Trac"
            showBackButton = false
            onBackAction = null
            rightActionIcon = null
            onRightActionClick = null
        }
        BottomTab.NEW_ENTRY -> {
            topBarTitle = if (isTamil) "புதிய பதிவு" else "New Entry"
            showBackButton = false
            onBackAction = null
            rightActionIcon = null
            onRightActionClick = null
        }
        BottomTab.REPORT -> {
            rightActionIcon = null
            onRightActionClick = null
            when (currentReportSubPage) {
                ReportSubPage.MENU -> {
                    topBarTitle = if (isTamil) "அறிக்கைகள் & பகுப்பாய்வு" else "Reports & Analytics"
                    showBackButton = false
                    onBackAction = null
                }
                ReportSubPage.EXPENSES -> {
                    topBarTitle = if (isTamil) "டிராக்டர் செலவுகள்" else "Fleet Expenses"
                    showBackButton = false
                    onBackAction = null
                }
                ReportSubPage.BALANCE_SHEET -> {
                    topBarTitle = if (isTamil) "இருப்புநிலை அறிக்கை" else "Balance Sheet"
                    showBackButton = true
                    onBackAction = { viewModel.setReportSubPage(ReportSubPage.MENU) }
                }
                ReportSubPage.BUSINESS_OVERVIEW -> {
                    topBarTitle = if (isTamil) "வணிக மேலோட்டம்" else "Business Overview"
                    showBackButton = true
                    onBackAction = { viewModel.setReportSubPage(ReportSubPage.MENU) }
                }
                ReportSubPage.WITHDRAWAL -> {
                    topBarTitle = if (isTamil) "பங்குதாரர் எடுப்புகள்" else "Partner Withdrawals"
                    showBackButton = true
                    onBackAction = { viewModel.setReportSubPage(ReportSubPage.MENU) }
                }
                ReportSubPage.CUSTOMER_CREDIT_DUE -> {
                    topBarTitle = if (isTamil) "வாடிக்கையாளர் கடன் நிலுவை" else "Customer Credit Due"
                    showBackButton = true
                    onBackAction = { viewModel.setReportSubPage(ReportSubPage.MENU) }
                }
                ReportSubPage.OLD_ENTRY -> {
                    topBarTitle = if (isTamil) "பழைய பதிவு" else "Old Entry"
                    showBackButton = true
                    onBackAction = { viewModel.setReportSubPage(ReportSubPage.MENU) }
                }
                ReportSubPage.COLLECTION_HISTORY -> {
                    topBarTitle = if (isTamil) "கட்டணம் வசூல் வரலாறு" else "Collection History"
                    showBackButton = true
                    onBackAction = { viewModel.setReportSubPage(ReportSubPage.MENU) }
                }
            }
        }
        BottomTab.ACCOUNT -> {
            when (currentAccountSubPage) {
                AccountSubPage.MAIN -> {
                    topBarTitle = if (isTamil) "கணக்கு" else "Account"
                    showBackButton = false
                    onBackAction = null
                    rightActionIcon = null
                    onRightActionClick = null
                }
                AccountSubPage.MANAGE_TRACTORS -> {
                    topBarTitle = if (isTamil) "டிராக்டர்களை நிர்வகி" else "Manage Fleet Tractors"
                    showBackButton = true
                    onBackAction = { viewModel.setAccountSubPage(AccountSubPage.MAIN) }
                    rightActionIcon = null
                    onRightActionClick = null
                }
                AccountSubPage.MANAGE_PARTNERS -> {
                    topBarTitle = if (isTamil) "பங்குதாரர்களை நிர்வகி" else "Manage Partners"
                    showBackButton = true
                    onBackAction = { viewModel.setAccountSubPage(AccountSubPage.MAIN) }
                    rightActionIcon = null
                    onRightActionClick = null
                }
                AccountSubPage.SETTINGS -> {
                    topBarTitle = if (isTamil) "வணிக அமைப்புகள்" else "Business Preferences"
                    showBackButton = true
                    onBackAction = { viewModel.setAccountSubPage(AccountSubPage.MAIN) }
                    rightActionIcon = null
                    onRightActionClick = null
                }
                AccountSubPage.EDIT_PROFILE -> {
                    topBarTitle = if (isTamil) "சுயவிவரத்தைத் திருத்து" else "Edit Partner Profile"
                    showBackButton = true
                    onBackAction = { viewModel.setAccountSubPage(AccountSubPage.MAIN) }
                    rightActionIcon = null
                    onRightActionClick = null
                }
                AccountSubPage.DELETED_USERS -> {
                    topBarTitle = "Deleted Users"
                    showBackButton = true
                    onBackAction = { viewModel.setAccountSubPage(AccountSubPage.MAIN) }
                    rightActionIcon = null
                    onRightActionClick = null
                }
                AccountSubPage.SQLITE_SYNC_STATUS -> {
                    topBarTitle = if (isTamil) "SQLite & கிளவுட் ஒத்திசைவு" else "SQLite & Cloud Sync"
                    showBackButton = true
                    onBackAction = { viewModel.setAccountSubPage(AccountSubPage.MAIN) }
                    rightActionIcon = null
                    onRightActionClick = null
                }
            }
        }
    }

    val isStartupAuthResolved by viewModel.isStartupAuthResolved.collectAsState()
    val workspaceInitState by viewModel.workspaceInitState.collectAsState()
    var pendingGoogleProfile by remember { mutableStateOf<com.example.data.firebase.UserProfile?>(null) }

    // HARD AUTH BOUNDARY:
    // isAuthenticated is ONLY true when authState is explicitly Authenticated (Firestore confirmed).
    // We never use FirebaseAuth.currentUser alone to grant access — that would allow stale/background
    // Firebase sessions to bypass the Login screen (the reported "No account found" → Home bug).
    val isAuthenticated = authState is com.example.data.firebase.AuthState.Authenticated

    // A verified Authenticated state and ready workspace are still required for Home.
    // Do not dispose LoginScreen during an interactive OTP/email/Google request.
    val isAuthInitializing = com.example.ui.util.shouldShowStartupSplash(
        isStartupAuthResolved, authState,
        workspaceInitState is com.example.data.repository.WorkspaceInitState.Ready ||
            workspaceInitState is com.example.data.repository.WorkspaceInitState.CachedReady,
        workspaceInitState is com.example.data.repository.WorkspaceInitState.Error ||
            workspaceInitState is com.example.data.repository.WorkspaceInitState.SelectionRequired
    )
    if (isAuthInitializing) {
        StartupSplashScreen()
    } else if (!isAuthenticated) {
        val authError = (authState as? com.example.data.firebase.AuthState.Error)?.message
        LoginScreen(
            partners = partners,
            isTamil = isTamil,
            onToggleLanguage = {
                viewModel.updateSettings(settings.copy(language = if (isTamil) "EN" else "TA"))
            },
            onLoginSuccess = { _, _ -> },
            onCheckPhoneAccount = { phoneNumber ->
                viewModel.checkPhoneAccount(phoneNumber)
            },
            isOnline = isOnline,
            onPhoneRegisterAccount = { fullName, businessName, onErrorCallback ->
                viewModel.createAccountForPhoneUser(
                    fullName = fullName,
                    businessName = businessName,
                    onSuccess = { profile ->
                        onShowToast(if (isTamil) "கணக்கு வெற்றிகரமாக உருவாக்கப்பட்டது" else "Account created successfully")
                    },
                    onError = { err ->
                        onErrorCallback(err)
                        onShowToast(err)
                    }
                )
            },
            onCancelPhoneAccountCreation = {
                viewModel.logout()
            },
            isGoogleAccountNotFound = pendingGoogleProfile != null,
            onGoogleCreateAccountRequested = {
                val profile = pendingGoogleProfile
                if (profile != null) {
                    viewModel.createAccountForGoogleUser(
                        profile = profile,
                        onSuccess = {
                            pendingGoogleProfile = null
                            onShowToast(if (isTamil) "Google கணக்கு உருவாக்கப்பட்டது" else "Google account created successfully")
                        },
                        onError = { err ->
                            onShowToast(err)
                        }
                    )
                }
            },
            onGoogleCancelRequested = {
                pendingGoogleProfile = null
            },
            onGoogleSignInRequested = {
                viewModel.signInWithGoogle(
                    context = context,
                    onSuccess = { profile ->
                        pendingGoogleProfile = null
                        onShowToast(if (isTamil) "கூகிள் மூலம் உள்நுழைந்தது: ${profile.displayName ?: profile.email ?: ""}" else "Logged in with Google: ${profile.displayName ?: profile.email ?: ""}")
                    },
                    onError = { err ->
                        onShowToast(err)
                    },
                    onNoAccountFound = { profile ->
                        pendingGoogleProfile = profile
                    }
                )
            },
            onEmailSignInRequested = { email, password, onNoAccount, onError ->
                viewModel.signInWithEmail(
                    email = email,
                    password = password,
                    onSuccess = { profile ->
                        onShowToast(if (isTamil) "மின்னஞ்சல் மூலம் உள்நுழைந்தது: ${profile.displayName ?: profile.email ?: ""}" else "Logged in: ${profile.displayName ?: profile.email ?: ""}")
                    },
                    onError = { err ->
                        onError(err)
                        onShowToast(err)
                    },
                    onNoAccountFound = onNoAccount
                )
            },
            onEmailSignUpRequested = { email, password ->
                viewModel.signUpWithEmail(
                    email = email,
                    password = password,
                    onSuccess = { profile ->
                        onShowToast(if (isTamil) "கணக்கு உருவாக்கப்பட்டது: ${profile.displayName ?: profile.email ?: ""}" else "Account created: ${profile.displayName ?: profile.email ?: ""}")
                    },
                    onError = { err ->
                        onShowToast(err)
                    }
                )
            },
            onSendOtpRequested = { phone, onSent, onError ->
                if (activity != null) {
                    viewModel.sendPhoneOtp(
                        activity = activity,
                        phone = phone,
                        onCodeSent = onSent,
                        onError = { err ->
                            onError(err)
                            onShowToast(err)
                        },
                        onAutoVerified = {
                            onShowToast(if (isTamil) "தொலைபேசி சரிபார்க்கப்பட்டது" else "Phone verified automatically")
                        }
                    )
                } else {
                    onSent()
                }
            },
            onVerifyOtpRequested = { otpCode, onVerificationError ->
                viewModel.verifyPhoneOtp(
                    otpCode = otpCode,
                    onSuccess = { profile ->
                        onShowToast(if (isTamil) "உள்நுழைவு வெற்றிகரமானது" else "Logged in successfully")
                    },
                    onError = { err ->
                        onVerificationError(err)
                        onShowToast(err)
                    }
                )
            },
            onQuickPartnerSelected = null,
            onGmailLoginRequested = { email ->
                viewModel.signInWithGoogle(
                    context = context,
                    onSuccess = { profile ->
                        pendingGoogleProfile = null
                        onShowToast(if (isTamil) "கூகிள் மூலம் உள்நுழைந்தது: ${profile.displayName ?: profile.email ?: ""}" else "Logged in with Google: ${profile.displayName ?: profile.email ?: ""}")
                    },
                    onError = { err ->
                        onShowToast(err)
                    },
                    onNoAccountFound = { profile ->
                        pendingGoogleProfile = profile
                    }
                )
            },
            isLoggingIn = authState is com.example.data.firebase.AuthState.Loading || isSyncing,
            errorMessage = authError
        )
    } else if (workspaceInitState is com.example.data.repository.WorkspaceInitState.Error ||
        workspaceInitState is com.example.data.repository.WorkspaceInitState.SelectionRequired) {
        val choices by viewModel.availableWorkspaces.collectAsState()
        com.example.ui.screens.auth.WorkspaceRecoveryScreen(
            message = (workspaceInitState as? com.example.data.repository.WorkspaceInitState.Error)?.exception?.message ?: "Choose the workspace and role you want to use.",
            workspaces = (workspaceInitState as? com.example.data.repository.WorkspaceInitState.SelectionRequired)?.workspaces ?: choices,
            canCreate = !com.example.BuildConfig.PHYSICAL_TEST,
            onRetry = { viewModel.retryWorkspaceDiscovery() },
            onSwitch = { ws -> viewModel.switchWorkspace(ws, onError = onShowToast) },
            onCreate = { name, business -> viewModel.createAccountForPhoneUser(name, business, onError = onShowToast) },
            onLogout = { viewModel.logout() }
        )
    } else {
        val accessControl by viewModel.accessControlState.collectAsState()
        val isCollaborationOwner by viewModel.isCollaborationOwner.collectAsState()
        val currentUserRole by viewModel.currentUserRole.collectAsState()
        if (viewModel.isAccountBlocked()) {
            com.example.ui.components.AccountSuspendedOrExpiredScreen(
                accessControl = accessControl,
                settings = settings,
                isTamil = isTamil,
                isSyncing = isSyncing,
                onRefreshStatus = {
                    viewModel.pushUnsyncedToCloud()
                },
                onLogout = {
                    viewModel.logout { onShowToast(if (isTamil) "வெளியேறியது" else "Logged out") }
                }
            )
        } else {
            // AUTHORITATIVE ONBOARDING GATE:
            // A Partner or Operator belongs to the Owner's workspace and must NEVER be prompted
            // to enter or configure a business name. Only a genuine workspace Owner with a missing
            // business name or owner name requires initial setup.
            val isExplicitOwner = isCollaborationOwner &&
                !currentUserRole.equals(com.example.data.auth.RoleUtils.ROLE_PARTNER, ignoreCase = true) &&
                !currentUserRole.equals(com.example.data.auth.RoleUtils.ROLE_OPERATOR, ignoreCase = true)
            val needsInitialSetup = isExplicitOwner && settings.isLoggedIn && (settings.ownerName.isBlank() || settings.businessName.isBlank())
            if (needsInitialSetup) {
                val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val prefilledName = currentUser?.displayName ?: settings.ownerName.ifBlank { settings.activePartnerName }
                FirstAccountSetupDialog(
                    settings = settings,
                    initialDisplayName = prefilledName,
                    initialBusinessName = settings.businessName,
                    isTamil = isTamil,
                    onCompleteSetup = { dName, bName ->
                        viewModel.completeInitialSetup(
                            displayName = dName,
                            businessName = bName,
                            onSuccess = {
                                onShowToast(if (isTamil) "சுயவிவரம் சேமிக்கப்பட்டது" else "Profile setup completed")
                            },
                            onError = { err ->
                                onShowToast(err)
                            }
                        )
                    }
                )
            }

            Scaffold(
                topBar = {
                    AppTopHeader(
                        title = topBarTitle,
                        showBack = showBackButton,
                        onBackClick = onBackAction,
                        settings = settings,
                        partners = partners,
                        isSyncing = isSyncing,
                        isOnline = isOnline,
                        totalUnsyncedCount = totalUnsyncedCount,
                        rejectedOperationCount = rejectedOperationCount,
                        blockedOperationCount = blockedOperationCount,
                        cloudConnectionState = cloudConnectionState,
                        onSyncClick = {
                            when (com.example.ui.util.cloudStatusAction(rejectedOperationCount, cloudConnectionState)) {
                                com.example.ui.util.CloudStatusAction.OPEN_RETAINED_ACTIONS -> {
                                    viewModel.setBottomTab(BottomTab.ACCOUNT)
                                    viewModel.setAccountSubPage(AccountSubPage.SQLITE_SYNC_STATUS)
                                }
                                com.example.ui.util.CloudStatusAction.RETRY_LISTENERS_AND_QUEUE -> {
                                    viewModel.retryCloudListeners()
                                    viewModel.pushUnsyncedToCloud()
                                }
                                com.example.ui.util.CloudStatusAction.SYNC_QUEUE -> viewModel.pushUnsyncedToCloud()
                            }
                        },
                        onPartnerSelected = { partner ->
                            viewModel.setActivePartner(partner)
                            onShowToast(if (isTamil) "பங்குதாரர் மாற்றப்பட்டது: ${partner.name}" else "Switched to ${partner.name}")
                        },
                        rightActionIcon = rightActionIcon,
                        onRightActionClick = onRightActionClick,
                        isDarkGreenStyle = true
                    )
                },
                bottomBar = {
                    AppBottomNav(
                        currentTab = currentTab,
                        onTabSelected = { tab ->
                            val (pageKey, canAccess) = when (tab) {
                                BottomTab.HOME -> com.example.data.auth.AuthorizationManager.PAGE_HOME to viewModel.canAccessPage(com.example.data.auth.AuthorizationManager.PAGE_HOME)
                                BottomTab.NEW_ENTRY -> com.example.data.auth.AuthorizationManager.PAGE_NEW_ENTRY to (viewModel.canAccessPage(com.example.data.auth.AuthorizationManager.PAGE_NEW_ENTRY) && viewModel.canCreateNewEntry())
                                BottomTab.REPORT -> com.example.data.auth.AuthorizationManager.PAGE_REPORT to viewModel.canAccessPage(com.example.data.auth.AuthorizationManager.PAGE_REPORT)
                                BottomTab.ACCOUNT -> com.example.data.auth.AuthorizationManager.PAGE_ACCOUNT to viewModel.canAccessPage(com.example.data.auth.AuthorizationManager.PAGE_ACCOUNT)
                            }
                            if (canAccess) {
                                viewModel.setBottomTab(tab)
                            } else {
                                if (tab == BottomTab.NEW_ENTRY && !viewModel.canCreateNewEntry()) {
                                    onShowToast(if (isTamil) "புதிய பதிவு உருவாக்கம் நிர்வாகத்தால் முடக்கப்பட்டுள்ளது" else "New entry creation is restricted by administration")
                                } else {
                                    onShowToast(if (isTamil) "இந்த பகுதி அணுகல் கட்டுப்படுத்தப்பட்டுள்ளது" else "This section is restricted")
                                }
                            }
                        },
                        isTamil = isTamil
                    )
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .imePadding()
                ) {
                    Crossfade(targetState = currentTab, label = "tab_crossfade") { tab ->
                        when (tab) {
                            BottomTab.HOME -> {
                                val homeSummary = remember(jobs, customers) { com.example.ui.util.HomeRecordSummary.from(jobs, customers) }
                                HomeScreen(
                                    settings = settings,
                                    totalRecorded = homeSummary.recorded,
                                    totalCustomerDue = homeSummary.due,
                                    totalExpenses = totalExpenses,
                                    availableBalance = availableAmount,
                                    recentJobs = todayJobs.take(20),
                                    recordedJobCount = homeSummary.count,
                                    recordedCustomerCount = homeSummary.customerCount,
                                    expenses = expenses,
                                    isOnline = isOnline,
                                    isSyncing = isSyncing,
                                    unsyncedCount = totalUnsyncedCount,
                                    syncMessage = syncMessage,
                                    retainedOperationWarning = retainedOperationWarning,
                                    retainedActions = retainedHomeActions,
                                    isOwner = isCollaborationOwner,
                                    currentUid = viewModel.currentUid ?: "",
                                    workspaceMembers = activeWorkspaceMembers,
                                    partners = partners,
                                    authenticatedUserName = authenticatedUserName,
                                    authenticatedUserRole = authenticatedUserRole,
                                    onTriggerSync = {
                                        viewModel.pushUnsyncedToCloud()
                                    },
                                    onFabClick = {
                                        if (viewModel.canCreateNewEntry()) {
                                            viewModel.setBottomTab(BottomTab.NEW_ENTRY)
                                        } else {
                                            onShowToast(if (isTamil) "புதிய பதிவு உருவாக்கம் நிர்வாகத்தால் முடக்கப்பட்டுள்ளது" else "New entry creation is restricted by administration")
                                        }
                                    },
                                    onEditJob = { job ->
                                        if (viewModel.canEditEntry(job)) {
                                            coroutineScope.launch {
                                                val jobExpenses = viewModel.getExpensesForJob(job.id)
                                                val editDraft = com.example.ui.viewmodel.NewEntryDraft.fromJobEntry(job, jobExpenses)
                                                viewModel.updateNewEntryDraft(editDraft)
                                                viewModel.setBottomTab(BottomTab.NEW_ENTRY)
                                            }
                                        } else {
                                            onShowToast(if (isTamil) "பதிவைத் திருத்த அனுமதி இல்லை" else "You do not have permission to edit this entry")
                                        }
                                    },
                                    onDeleteJob = { job ->
                                        if (viewModel.canDeleteEntry(job)) {
                                            viewModel.deleteJob(job, onResult = onShowToast)
                                        } else {
                                            onShowToast(if (isTamil) "பதிவை நீக்க அனுமதி இல்லை" else "You do not have permission to delete this entry")
                                        }
                                    }
                                )
                            }

                            BottomTab.REPORT -> {
                                ReportScreen(
                                    currentSubPage = currentReportSubPage,
                                    onSubPageSelected = { subPage ->
                                        val pageKey = when (subPage) {
                                            ReportSubPage.EXPENSES -> com.example.data.auth.AuthorizationManager.PAGE_EXPENSES
                                            ReportSubPage.BALANCE_SHEET -> com.example.data.auth.AuthorizationManager.PAGE_BALANCE_SHEET
                                            ReportSubPage.BUSINESS_OVERVIEW -> com.example.data.auth.AuthorizationManager.PAGE_BUSINESS_OVERVIEW
                                            ReportSubPage.WITHDRAWAL -> com.example.data.auth.AuthorizationManager.PAGE_WITHDRAWAL
                                            ReportSubPage.CUSTOMER_CREDIT_DUE -> com.example.data.auth.AuthorizationManager.PAGE_CUSTOMER_CREDIT_DUE
                                            ReportSubPage.COLLECTION_HISTORY -> com.example.data.auth.AuthorizationManager.PAGE_COLLECTION_HISTORY
                                            else -> null
                                        }
                                        if (subPage == ReportSubPage.OLD_ENTRY && !viewModel.canCreateOldEntry()) {
                                            onShowToast(if (isTamil) "பழைய பதிவு உருவாக்கம் நிர்வாகத்தால் முடக்கப்பட்டுள்ளது" else "Old entry creation is restricted by administration")
                                        } else if (pageKey == null || viewModel.canAccessPage(pageKey)) {
                                            viewModel.setReportSubPage(subPage)
                                        } else {
                                            onShowToast(if (isTamil) "இந்த அறிக்கை அணுகல் கட்டுப்படுத்தப்பட்டுள்ளது" else "This report section is restricted")
                                        }
                                    },
                                    settings = settings,
                                    expenses = expenses,
                                    jobs = jobs,
                                    customers = customers,
                                    withdrawals = withdrawals,
                                    partners = partners,
                                    tractors = tractors,
                                    totalSales = totalReceived,
                                    totalExpenses = totalExpenses,
                                    netBalance = netBalance,
                                    availableAmount = availableAmount,
                                    totalWithdrawn = totalWithdrawn,
                                    onAddExpense = { expense ->
                                        viewModel.addExpense(expense) {
                                            onShowToast(if (isTamil) "செலவு சேர்க்கப்பட்டது" else "Expense added")
                                        }
                                    },
                                    onUpdateExpense = { expense ->
                                        viewModel.updateExpense(expense) {
                                            onShowToast(if (isTamil) "செலவு புதுப்பிக்கப்பட்டது" else "Expense updated")
                                        }
                                    },
                                    onDeleteExpense = { expense ->
                                        viewModel.deleteExpense(expense,
                                            onQueued = { onShowToast(if (isTamil) "செலவு நீக்கம் சாதனத்தில் சேமிக்கப்பட்டது; மேக உறுதிப்படுத்தல் நிலுவையில் உள்ளது" else "Expense deletion saved on this device; cloud confirmation pending") },
                                            onError = { onShowToast(it) })
                                    },
                                    onAddWithdrawal = { withdrawal ->
                                        viewModel.addWithdrawal(
                                            withdrawal,
                                            onSuccess = {
                                                onShowToast(if (isTamil) "எடுப்பு பதிவு செய்யப்பட்டது" else "Withdrawal recorded")
                                            },
                                            onError = { errorMsg ->
                                                onShowToast(errorMsg)
                                            }
                                        )
                                    },
                                    onDeleteWithdrawal = { withdrawal ->
                                        viewModel.deleteWithdrawal(withdrawal)
                                        onShowToast(if (isTamil) "எடுப்பு நீக்கப்பட்டது" else "Withdrawal deleted")
                                    },
                                    onUpdateCustomer = { customer ->
                                        viewModel.updateCustomer(customer) {
                                            onShowToast(if (isTamil) "வாடிக்கையாளர் புதுப்பிக்கப்பட்டார்" else "Customer updated")
                                        }
                                    },
                                    onRecordPayment = { customer, amount, dateMillis, method, note ->
                                        if (!viewModel.canCollectPayment()) {
                                            onShowToast(if (isTamil) "கட்டணம் வசூலிக்க அனுமதி இல்லை" else "Payment collection is restricted")
                                        } else {
                                            viewModel.recordCustomerPayment(customer, amount, dateMillis, method, note) {
                                                onShowToast(if (isTamil) "வசூல் சாதனத்தில் சேமிக்கப்பட்டது; மேக ஒத்திசைவு நிலுவையில் உள்ளது" else "Collection saved on this device; cloud confirmation pending")
                                            }
                                        }
                                    },
                                    onRecordPaymentWithCollector = { customer, amount, dateMillis, method, note, collectorUid, collectorName, collectorRole ->
                                        if (!viewModel.canCollectPayment()) {
                                            onShowToast(if (isTamil) "கட்டணம் வசூலிக்க அனுமதி இல்லை" else "Payment collection is restricted")
                                        } else {
                                            viewModel.recordCustomerPayment(
                                                customer = customer,
                                                amount = amount,
                                                dateTimestamp = dateMillis,
                                                paymentMethod = method,
                                                note = note,
                                                collectedByUid = collectorUid,
                                                collectedByName = collectorName,
                                                collectedByRole = collectorRole
                                            ) {
                                                onShowToast(if (isTamil) "வசூல் சாதனத்தில் சேமிக்கப்பட்டது; மேக ஒத்திசைவு நிலுவையில் உள்ளது" else "Collection saved on this device; cloud confirmation pending")
                                            }
                                        }
                                    },
                                    onEditJob = { job ->
                                        if (viewModel.canEditEntry(job)) {
                                            coroutineScope.launch {
                                                val jobExpenses = viewModel.getExpensesForJob(job.id)
                                                val editDraft = com.example.ui.viewmodel.NewEntryDraft.fromJobEntry(job, jobExpenses)
                                                viewModel.updateNewEntryDraft(editDraft)
                                                viewModel.setBottomTab(BottomTab.NEW_ENTRY)
                                            }
                                        } else {
                                            onShowToast(if (isTamil) "பதிவைத் திருத்த அனுமதி இல்லை" else "You do not have permission to edit this entry")
                                        }
                                    },
                                    onDeleteJob = { job ->
                                        if (viewModel.canDeleteEntry(job)) {
                                            viewModel.deleteJob(job, onResult = onShowToast)
                                        } else {
                                            onShowToast(if (isTamil) "பதிவை நீக்க அனுமதி இல்லை" else "You do not have permission to delete this entry")
                                        }
                                    },
                                    onDeleteCustomer = { customer ->
                                        if (viewModel.canDeleteCustomer()) {
                                            viewModel.deleteCustomer(customer) {
                                                onShowToast("Customer removed on this device; cloud deletion pending")
                                            }
                                        } else {
                                            onShowToast(if (isTamil) "வாடிக்கையாளரை நீக்க அனுமதி இல்லை" else "You do not have permission to delete customers")
                                        }
                                    },
                                    onDeleteCollection = { paymentId ->
                                        if (viewModel.canDeleteCollection()) {
                                            viewModel.deleteCollection(paymentId,
                                                onSuccess = { onShowToast("Collection reversal saved on this device; cloud sync pending") },
                                                onError = { onShowToast(it) })
                                        } else {
                                            onShowToast(if (isTamil) "வசூல் பதிவை நீக்க அனுமதி இல்லை" else "You do not have permission to delete collections")
                                        }
                                    },
                                    isOwner = isCollaborationOwner,
                                    actorName = if (isCollaborationOwner) settings.ownerName.ifBlank { "Owner" } else settings.activePartnerName.ifBlank { "Partner" },
                                    currentUid = viewModel.currentUid ?: "",
                                    workspaceMembers = activeWorkspaceMembers,
                                    businessOverviewMembers = activeWorkspaceMembers,
                                    payments = payments,
                                    partnerPercentages = viewModel.partnerPercentages.collectAsState().value,
                                    onUpdatePartnerPercentages = { viewModel.updatePartnerPercentages(it) },
                                    profitShareAllocations = profitShareAllocations,
                                    profitShareAllocationDetails = profitShareAllocationDetails,
                                    onUpdateProfitShareAllocations = { allocations, details ->
                                        viewModel.updateProfitShareAllocations(allocations, details)
                                    },
                                    onStartOldEntry = { dateMillis ->
                                        if (!viewModel.canCreateOldEntry()) {
                                            onShowToast(if (isTamil) "பழைய பதிவு உருவாக்கம் நிர்வாகத்தால் முடக்கப்பட்டுள்ளது" else "Old entry creation is restricted by administration")
                                        } else {
                                            val ownTractors = tractors.filter { it.workspaceId == settings.workspaceId }
                                            val currentActorUid = viewModel.currentUid ?: ""
                                            val wsId = settings.workspaceId
                                            val ownerName = settings.ownerName.ifBlank { settings.activePartnerName.ifBlank { "Owner" } }
                                            var resolvedUid = currentActorUid
                                            var resolvedName = ownerName
                                            var isPersonLocked = false

                                            if (isCollaborationOwner) {
                                                val lockedUid = viewModel.getOwnerLockedPersonUid(currentActorUid, wsId)
                                                if (lockedUid.isNotBlank()) {
                                                    if (lockedUid == currentActorUid) {
                                                        resolvedUid = currentActorUid
                                                        resolvedName = ownerName
                                                        isPersonLocked = true
                                                    } else {
                                                        val partner = workspaceMembers.firstOrNull { it.status.equals("active", ignoreCase = true) && it.uid == lockedUid }
                                                        if (partner != null) {
                                                            resolvedUid = partner.uid
                                                            resolvedName = partner.displayName?.ifBlank { null } ?: partner.phoneNumber ?: "Partner"
                                                            isPersonLocked = true
                                                        }
                                                    }
                                                }
                                            }
                                            val draft = com.example.ui.viewmodel.NewEntryDraft.createHistorical(
                                                dateMillis = dateMillis,
                                                defaultTractor = if (settings.lockedTractorLabel.isNotBlank()) settings.lockedTractorLabel else (ownTractors.firstOrNull()?.label ?: ""),
                                                lockedTractor = settings.lockedTractorLabel,
                                                defaultHourlyRate = viewModel.getPersonalHourlyRate(resolvedUid),
                                                defaultPersonUid = resolvedUid,
                                                defaultPersonName = resolvedName,
                                                lockedPersonUid = if (isPersonLocked) resolvedUid else "",
                                                lockedPersonName = if (isPersonLocked) resolvedName else ""
                                            )
                                            viewModel.updateNewEntryDraft(draft)
                                            viewModel.setBottomTab(BottomTab.NEW_ENTRY)
                                        }
                                    },
                                    initialScope = balanceSheetScope,
                                    onUpdateFinancialScope = { viewModel.updateBalanceSheetScope(it) }
                                )
                            }

                            BottomTab.NEW_ENTRY -> {
                                val ownTractors = tractors.filter { it.workspaceId == settings.workspaceId }
                                val currentActorUid = viewModel.currentUid ?: ""
                                val wsId = settings.workspaceId
                                val ownerName = settings.ownerName.ifBlank { settings.activePartnerName.ifBlank { "Owner" } }
                                val canSelectEntryMember = isCollaborationOwner || activeWorkspaceMembers.any {
                                    it.uid == currentActorUid && it.workspaceId == wsId && it.status.equals("active", true) &&
                                        com.example.data.auth.RoleUtils.normalizeRole(it.role) == com.example.data.auth.RoleUtils.ROLE_PARTNER
                                }
                                val actorDisplayName = activeWorkspaceMembers.firstOrNull {
                                    it.uid == currentActorUid && it.workspaceId == wsId
                                }?.displayName.orEmpty().ifBlank { if (isCollaborationOwner) ownerName else settings.activePartnerName.ifBlank { "Partner" } }

                                val draft = newEntryDraft ?: run {
                                    var resolvedUid = currentActorUid
                                    var resolvedName = actorDisplayName
                                    var isPersonLocked = false

                                    if (canSelectEntryMember) {
                                        val lockedUid = viewModel.getOwnerLockedPersonUid(currentActorUid, wsId)
                                        if (lockedUid.isNotBlank()) {
                                            if (lockedUid == currentActorUid) {
                                                resolvedUid = currentActorUid
                                                resolvedName = actorDisplayName
                                                isPersonLocked = true
                                            } else {
                                                val partner = workspaceMembers.firstOrNull { it.status.equals("active", ignoreCase = true) && it.uid == lockedUid }
                                                if (partner != null) {
                                                    resolvedUid = partner.uid
                                                    resolvedName = partner.displayName?.ifBlank { null } ?: partner.phoneNumber ?: "Partner"
                                                    isPersonLocked = true
                                                } else {
                                                    viewModel.setOwnerLockedPersonUid(currentActorUid, wsId, "")
                                                }
                                            }
                                        }
                                    } else {
                                        resolvedUid = currentActorUid
                                        resolvedName = settings.activePartnerName.ifBlank { "Partner" }
                                    }

                                    com.example.ui.viewmodel.NewEntryDraft.createDefault(
                                        defaultTractor = if (settings.lockedTractorLabel.isNotBlank()) settings.lockedTractorLabel else (ownTractors.firstOrNull()?.label ?: ""),
                                        lockedTractor = settings.lockedTractorLabel,
                                        defaultHourlyRate = viewModel.getPersonalHourlyRate(resolvedUid),
                                        defaultPersonUid = resolvedUid,
                                        defaultPersonName = resolvedName,
                                        lockedPersonUid = if (isPersonLocked) resolvedUid else "",
                                        lockedPersonName = if (isPersonLocked) resolvedName else ""
                                    )
                                }

                                NewEntryScreen(
                                    settings = settings,
                                    tractors = tractors,
                                    customers = customers,
                                    jobs = jobs,
                                    draft = draft,
                                    isSaving = isSavingJob,
                                    isCollaborationOwner = isCollaborationOwner,
                                    workspaceMembers = activeWorkspaceMembers,
                                    currentUid = currentActorUid,
                                    workTypeExtensions = workTypeExtensions,
                                    onUpdateDraft = { viewModel.updateNewEntryDraft(it) },
                                    onClearDraft = { viewModel.clearNewEntryDraft() },
                                    onSaveJob = { job, linkedExpense, linkedExpenses ->
                                        android.util.Log.d("TRAC_ENTRY", "SAVE_START entryId=${job.id} customer=${job.customerName}")
                                        viewModel.saveJobEntry(
                                            job = job,
                                            linkedExpense = linkedExpense,
                                            linkedExpenses = linkedExpenses,
                                            onSuccess = {
                                                android.util.Log.d("TRAC_ENTRY", "SAVE_SUCCESS entryId=${job.id} -> NAVIGATE_HOME")
                                                onShowToast(if (isTamil) "வேலைப் பதிவு வெற்றிகரமாகச் சேமிக்கப்பட்டது!" else "Job Entry saved successfully!")
                                                viewModel.setBottomTab(BottomTab.HOME)
                                            },
                                            onError = { errMsg ->
                                                android.util.Log.e("TRAC_ENTRY", "SAVE_ERROR entryId=${job.id} err=$errMsg")
                                                onShowToast(if (isTamil) "சேமிப்பதில் பிழை: $errMsg" else "Failed to save: $errMsg")
                                            }
                                        )
                                    },
                                    onUpdateLockedTractor = { locked ->
                                        viewModel.updateSettings(settings.copy(lockedTractorLabel = locked))
                                    },
                                    onUpdateLockedPerson = { lockedUid ->
                                        if (canSelectEntryMember) {
                                            viewModel.setOwnerLockedPersonUid(currentActorUid, wsId, lockedUid)
                                        }
                                    },
                                    onResolvePersonalRate = { personUid ->
                                        viewModel.getPersonalHourlyRate(personUid)
                                    }
                                )
                            }

                            BottomTab.ACCOUNT -> {
                                val actorUid = viewModel.currentUid ?: ""
                                val availableWorkspaces by viewModel.availableWorkspaces.collectAsState()
                                androidx.compose.runtime.LaunchedEffect(actorUid) { viewModel.loadAvailableWorkspaces() }
                                val deletedUsers by viewModel.deletedUsers.collectAsState()
                                val deletedUsersError by viewModel.deletedUsersError.collectAsState()
                                androidx.compose.runtime.LaunchedEffect(currentAccountSubPage) {
                                    if (currentAccountSubPage == AccountSubPage.DELETED_USERS) viewModel.refreshDeletedUsers()
                                }
                                val pendingActions by viewModel.pendingActions.collectAsState()
                                AccountScreen(
                                    availableWorkspaces = availableWorkspaces,
                                    onSwitchWorkspace = { id -> viewModel.switchWorkspace(id, onError = onShowToast) },
                                    pendingActions = pendingActions,
                                    cloudConnectionState = cloudConnectionState,
                                    onUpdateExtension = { viewModel.updateExtension(it) },
                                    onDeleteExtension = { viewModel.deleteExtension(it) },
                                    deletedUsers = deletedUsers,
                                    deletedUsersError = deletedUsersError,
                                    onPermanentlyDeleteUser = { uid, confirmation -> viewModel.permanentlyDeleteUser(uid, confirmation) },
                                    currentSubPage = currentAccountSubPage,
                                    onSubPageSelected = { viewModel.setAccountSubPage(it) },
                                    settings = settings,
                                    partners = partners,
                                    tractors = tractors,
                                    isSyncing = isSyncing,
                                    isOnline = isOnline,
                                    unsyncedJobsCount = unsyncedJobsCount,
                                    unsyncedExpensesCount = unsyncedExpensesCount,
                                    unsyncedWithdrawalsCount = unsyncedWithdrawalsCount,
                                    unsyncedCustomersCount = unsyncedCustomersCount,
                                    unsyncedPaymentsCount = unsyncedPaymentsCount,
                                    totalUnsyncedCount = totalUnsyncedCount,
                                    rejectedOperationCount = rejectedOperationCount,
                                    blockedOperationCount = blockedOperationCount,
                                    syncMessage = syncMessage,
                                    pendingInvitations = pendingInvitations,
                                    workspaceMembers = workspaceMembers,
                                    isCollaborationOwner = isCollaborationOwner,
                                    activeWorkspaceId = activeWorkspaceId,
                                    currentPersonalHourlyRate = viewModel.getPersonalHourlyRate(actorUid),
                                    onUpdatePersonalHourlyRate = { newRate ->
                                        viewModel.updatePersonalHourlyRate(newRate, actorUid) {
                                            onShowToast(if (isTamil) "கட்டணம் புதுப்பிக்கப்பட்டது" else "Hourly rate updated")
                                        }
                                    },
                                    isSimulatedOffline = simulatedOffline,
                                    onToggleSimulatedOffline = { viewModel.toggleSimulatedOffline(it) },
                                    onTriggerSync = {
                                        viewModel.retryCloudListeners()
                                        viewModel.pushUnsyncedToCloud()
                                    },
                                    onRetryRejectedCollection = { viewModel.retryRejectedCollection(it) },
                                    canAddExtension = viewModel.canAddExtension(),
                                    workTypeExtensions = workTypeExtensions,
                                    onAddExtension = { extName ->
                                        viewModel.addExtension(extName) {
                                            onShowToast(if (isTamil) "வேலை வகை சேர்க்கப்பட்டது" else "Extension added")
                                        }
                                    },
                                    onAddTractor = {
                                        if (!viewModel.canManageTractors()) {
                                            onShowToast(if (isTamil) "டிராக்டர்களை நிர்வகிக்க அனுமதி இல்லை" else "Fleet tractor management is restricted")
                                        } else {
                                            viewModel.addTractor(it) { onShowToast(if (isTamil) "டிராக்டர் சேர்க்கப்பட்டது" else "Tractor added") }
                                        }
                                    },
                                    onUpdateTractor = {
                                        if (!viewModel.canManageTractors()) {
                                            onShowToast(if (isTamil) "டிராக்டர்களை நிர்வகிக்க அனுமதி இல்லை" else "Fleet tractor management is restricted")
                                        } else {
                                            viewModel.updateTractor(it) { onShowToast(if (isTamil) "டிராக்டர் புதுப்பிக்கப்பட்டது" else "Tractor updated") }
                                        }
                                    },
                                    onDeleteTractor = {
                                        if (!viewModel.canManageTractors()) {
                                            onShowToast(if (isTamil) "டிராக்டர்களை நிர்வகிக்க அனுமதி இல்லை" else "Fleet tractor management is restricted")
                                        } else {
                                            viewModel.deleteTractor(it)
                                            onShowToast(if (isTamil) "டிராக்டர் நீக்கப்பட்டது" else "Tractor deleted")
                                        }
                                    },
                                    onAddPartner = { partner ->
                                        if (!viewModel.canManagePartners()) {
                                            onShowToast(if (isTamil) "பங்குதாரர்களை நிர்வகிக்க அனுமதி இல்லை" else "Partner management is restricted")
                                        } else {
                                            viewModel.addPartner(
                                                partner = partner,
                                                onSuccess = {
                                                    onShowToast(if (isTamil) "பங்குதாரர் வெற்றிகரமாக இணைக்கப்பட்டார்!" else "Partner connected successfully!")
                                                },
                                                onError = { err ->
                                                    onShowToast(err)
                                                }
                                            )
                                        }
                                    },
                                    onUpdatePartner = {
                                        if (!viewModel.canManagePartners()) {
                                            onShowToast(if (isTamil) "பங்குதாரர்களை நிர்வகிக்க அனுமதி இல்லை" else "Partner management is restricted")
                                        } else {
                                            viewModel.updatePartner(it) { onShowToast(if (isTamil) "பங்குதாரர் புதுப்பிக்கப்பட்டார்" else "Partner updated") }
                                        }
                                    },
                                    onDeletePartner = {
                                        if (!viewModel.canManagePartners()) {
                                            onShowToast(if (isTamil) "பங்குதாரர்களை நீக்க அனுமதி இல்லை" else "Partner management is restricted")
                                        } else {
                                            viewModel.deletePartner(it, onComplete = { onShowToast("Member access removed; records retained") }, onError = onShowToast)
                                        }
                                    },
                                    onRemovePendingInvitation = { pending ->
                                        viewModel.removePendingPartnerInvitation(pending) { result ->
                                            val message = when (result) {
                                                com.example.data.repository.WorkspaceRepository.PendingInvitationCleanupResult.Deleted,
                                                com.example.data.repository.WorkspaceRepository.PendingInvitationCleanupResult.AlreadyGone ->
                                                    if (isTamil) "நிலுவையில் உள்ள அழைப்பு நீக்கப்பட்டது" else "Pending invitation removed"
                                                com.example.data.repository.WorkspaceRepository.PendingInvitationCleanupResult.Registered ->
                                                    if (isTamil) "இந்த கணக்கு ஏற்கனவே இணைக்கப்பட்டுள்ளது" else "This account is already connected"
                                                is com.example.data.repository.WorkspaceRepository.PendingInvitationCleanupResult.Rejected ->
                                                    result.message
                                                is com.example.data.repository.WorkspaceRepository.PendingInvitationCleanupResult.Failed ->
                                                    result.message
                                            }
                                            onShowToast(message)
                                        }
                                    },
                                    onUpdateSettings = {
                                        viewModel.updateSettings(it) { onShowToast(if (isTamil) "அமைப்புகள் புதுப்பிக்கப்பட்டன" else "Settings updated") }
                                    },
                                    lockedPages = lockedPages,
                                    checklistItems = checklistItems,
                                    onAddChecklistItem = { viewModel.addChecklistItem(it) },
                                    onToggleChecklistItem = { viewModel.toggleChecklistItem(it) },
                                    onDeleteChecklistItem = { viewModel.deleteChecklistItem(it) },
                                    onSetPageLocked = { pageKey, isLocked ->
                                        viewModel.setPageLocked(pageKey, isLocked)
                                    },
                                    onGetPartnerLockedPages = { partnerIdentifier ->
                                        viewModel.getPartnerLockedPages(partnerIdentifier)
                                    },
                                    onSetPartnerPageLocked = { partnerIdentifier, pageKey, isLocked ->
                                        viewModel.setPartnerPageLocked(partnerIdentifier, pageKey, isLocked)
                                    },
                                    onLogout = {
                                        viewModel.logout { onShowToast(if (isTamil) "வெளியேறியது" else "Logged out") }
                                    },
                                    authenticatedUserName = authenticatedUserName,
                                    authenticatedUserRole = authenticatedUserRole,
                                    profitShareAllocations = profitShareAllocations,
                                    profitShareAllocationDetails = profitShareAllocationDetails,
                                    onUpdateProfitShareAllocations = { allocations, details ->
                                        viewModel.updateProfitShareAllocations(
                                            allocations = allocations,
                                            participantDetails = details,
                                            onSuccess = {
                                                onShowToast(if (isTamil) "லாபப் பங்கு ஒதுக்கீடு சேமிக்கப்பட்டது" else "Profit share allocations saved")
                                            },
                                            onError = { err ->
                                                onShowToast(err)
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
