

package com.example.ui.screens.account

import androidx.compose.foundation.ExperimentalFoundationApi

import android.content.Context
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.example.ui.utils.trackFocusedField
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.R
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HeadsetMic
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.CheckCircle
import com.example.data.auth.AuthorizationManager
import com.example.ui.screens.report.ConfigurePercentagesDialog
import com.example.ui.screens.report.getEligibleProfitShareParticipants
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.ChecklistItemEntity
import com.example.data.entity.WorkTypeExtensionEntity
import com.example.data.firebase.Workspace
import com.example.data.firebase.WorkspaceInvitation
import com.example.ui.components.ImageCropDialog
import com.example.ui.components.PartnerAvatarImage
import com.example.ui.components.openDialer
import com.example.ui.components.openWhatsApp
import com.example.ui.viewmodel.FIXED_WORK_TYPES
import com.example.ui.theme.AlertDueRed
import com.example.ui.theme.AlertDueRedBg
import com.example.ui.theme.AppTheme
import com.example.ui.theme.DeepSageGreen
import com.example.ui.theme.ForestGreenHeader
import com.example.ui.theme.SageAccent
import com.example.ui.theme.SageCardBg
import com.example.ui.theme.SageOutline
import com.example.ui.theme.SoftSageGreen
import com.example.ui.theme.SuccessPaidGreen
import com.example.ui.theme.SuccessPaidGreenBg
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextPrimaryDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.viewmodel.AccountSubPage
data class PartnerUiModel(
    val id: Long,
    val name: String,
    val phone: String,
    val role: String,
    val photoUri: String,
    val isConnected: Boolean,
    val originalPartnerEntity: PartnerEntity?,
    val uid: String = ""
)
private fun parseLongId(idStr: String): Long {
    return idStr.toLongOrNull() ?: (idStr.hashCode().toLong().let { if (it <= 0) Math.abs(it) + 1L else it })
}
@Composable
fun AccountScreen(
    currentSubPage: AccountSubPage,
    onSubPageSelected: (AccountSubPage) -> Unit,
    settings: AppSettingsEntity,
    partners: List<PartnerEntity>,
    tractors: List<TractorEntity>,
    isSyncing: Boolean,
    isOnline: Boolean = true,
    unsyncedJobsCount: Int = 0,
    unsyncedExpensesCount: Int = 0,
    unsyncedWithdrawalsCount: Int = 0,
    unsyncedCustomersCount: Int = 0,
    totalUnsyncedCount: Int = 0,
    pendingInvitations: List<WorkspaceInvitation> = emptyList(),
    workspaceMembers: List<com.example.data.firebase.WorkspaceMember> = emptyList(),
    isCollaborationOwner: Boolean = true,
    activeWorkspaceId: String? = null,
    onAcceptInvitation: (WorkspaceInvitation) -> Unit = {},
    onDeclineInvitation: (WorkspaceInvitation) -> Unit = {},
    isSimulatedOffline: Boolean = false,
    onToggleSimulatedOffline: ((Boolean) -> Unit)? = null,
    onTriggerSync: () -> Unit,
    onAddTractor: (TractorEntity) -> Unit,
    onUpdateTractor: (TractorEntity) -> Unit,
    onDeleteTractor: (TractorEntity) -> Unit,
    canDeleteTractor: (TractorEntity) -> Boolean = { true },
    canDeleteExtension: (WorkTypeExtensionEntity) -> Boolean = { true },
    onDeleteExtension: ((WorkTypeExtensionEntity) -> Unit)? = null,
    onAddPartner: (PartnerEntity) -> Unit,
    onUpdatePartner: (PartnerEntity) -> Unit,
    onDeletePartner: (PartnerEntity) -> Unit,
    onUpdateSettings: (AppSettingsEntity) -> Unit,
    lockedPages: Set<String> = emptySet(),
    onSetPageLocked: ((String, Boolean) -> Unit)? = null,
    onGetPartnerLockedPages: ((String) -> Set<String>)? = null,
    onSetPartnerPageLocked: ((String, String, Boolean) -> Unit)? = null,
    onLogout: () -> Unit,
    checklistItems: List<com.example.data.entity.ChecklistItemEntity> = emptyList(),
    onAddChecklistItem: ((String) -> Unit)? = null,
    onToggleChecklistItem: ((com.example.data.entity.ChecklistItemEntity) -> Unit)? = null,
    onDeleteChecklistItem: ((com.example.data.entity.ChecklistItemEntity) -> Unit)? = null,
    canAddExtension: Boolean = true,
    workTypeExtensions: List<WorkTypeExtensionEntity> = emptyList(),
    onAddExtension: ((String) -> Unit)? = null,
    currentPersonalHourlyRate: Double? = null,
    onUpdatePersonalHourlyRate: ((Double) -> Unit)? = null,
    authenticatedUserName: String = "",
    authenticatedUserRole: String = "",
    profitShareAllocations: Map<String, Int> = emptyMap(),
    profitShareAllocationDetails: Map<String, com.example.data.firebase.ProfitShareAllocation> = emptyMap(),
    onUpdateProfitShareAllocations: ((Map<String, Int>, Map<String, com.example.data.firebase.ProfitShareAllocation>) -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
    ) {
        when (currentSubPage) {
            AccountSubPage.MAIN -> {
                AccountMainDashboard(
                    settings = settings,
                    partners = partners,
                    tractors = tractors,
                    isSyncing = isSyncing,
                    isOnline = isOnline,
                    totalUnsyncedCount = totalUnsyncedCount,
                    pendingInvitations = pendingInvitations,
                    workspaceMembers = workspaceMembers,
                    isCollaborationOwner = isCollaborationOwner,
                    activeWorkspaceId = activeWorkspaceId,
                    onAcceptInvitation = onAcceptInvitation,
                    onDeclineInvitation = onDeclineInvitation,
                    isSimulatedOffline = isSimulatedOffline,
                    onToggleSimulatedOffline = onToggleSimulatedOffline,
                    onNavigate = onSubPageSelected,
                    onTriggerSync = onTriggerSync,
                    onUpdateSettings = onUpdateSettings,
                    onLogout = onLogout,
                    currentPersonalHourlyRate = currentPersonalHourlyRate,
                    authenticatedUserName = authenticatedUserName,
                    authenticatedUserRole = authenticatedUserRole
                )
            }
            AccountSubPage.MANAGE_TRACTORS -> {
                ManageTractorsPage(
                    settings = settings,
                    tractors = tractors,
                    onAddTractor = onAddTractor,
                    onUpdateTractor = onUpdateTractor,
                    onDeleteTractor = onDeleteTractor,
                    canAddExtension = canAddExtension,
                    workTypeExtensions = workTypeExtensions,
                    onAddExtension = onAddExtension,
                    canDeleteExtension = canDeleteExtension,
                    onDeleteExtension = onDeleteExtension
                )
            }
            AccountSubPage.MANAGE_PARTNERS -> {
                ManagePartnersPage(
                    settings = settings,
                    partners = partners,
                    activePartnerName = settings.activePartnerName,
                    workspaceMembers = workspaceMembers,
                    isCollaborationOwner = isCollaborationOwner,
                    pendingInvitations = pendingInvitations,
                    lockedPages = lockedPages,
                    onSetPageLocked = onSetPageLocked,
                    onGetPartnerLockedPages = onGetPartnerLockedPages,
                    onSetPartnerPageLocked = onSetPartnerPageLocked,
                    onAcceptInvitation = onAcceptInvitation,
                    onDeclineInvitation = onDeclineInvitation,
                    onAddPartner = onAddPartner,
                    onUpdatePartner = onUpdatePartner,
                    onDeletePartner = onDeletePartner,
                    checklistItems = checklistItems,
                    onAddChecklistItem = onAddChecklistItem,
                    onToggleChecklistItem = onToggleChecklistItem,
                    onDeleteChecklistItem = onDeleteChecklistItem,
                    profitShareAllocations = profitShareAllocations,
                    profitShareAllocationDetails = profitShareAllocationDetails,
                    onUpdateProfitShareAllocations = onUpdateProfitShareAllocations,
                    authenticatedUserRole = authenticatedUserRole,
                    onSwitchActivePartner = { partner ->
                        onUpdateSettings(
                            settings.copy(
                                activePartnerName = partner.name,
                                activePartnerPhone = partner.phone,
                                profilePhotoUri = partner.photoUri ?: settings.profilePhotoUri
                            )
                        )
                    }
                )
            }
        AccountSubPage.SETTINGS -> {
                BusinessSettingsPage(
                    settings = settings,
                    onUpdateSettings = onUpdateSettings,
                    isCollaborationOwner = isCollaborationOwner,
                    currentPersonalHourlyRate = currentPersonalHourlyRate,
                    onUpdatePersonalHourlyRate = onUpdatePersonalHourlyRate
                )
            }
            AccountSubPage.EDIT_PROFILE -> {
                AccountMainDashboard(
                    settings = settings,
                    partners = partners,
                    tractors = tractors,
                    isSyncing = isSyncing,
                    isOnline = isOnline,
                    totalUnsyncedCount = totalUnsyncedCount,
                    pendingInvitations = pendingInvitations,
                    workspaceMembers = workspaceMembers,
                    isCollaborationOwner = isCollaborationOwner,
                    activeWorkspaceId = activeWorkspaceId,
                    onAcceptInvitation = onAcceptInvitation,
                    onDeclineInvitation = onDeclineInvitation,
                    isSimulatedOffline = isSimulatedOffline,
                    onToggleSimulatedOffline = onToggleSimulatedOffline,
                    onNavigate = onSubPageSelected,
                    onTriggerSync = onTriggerSync,
                    onUpdateSettings = onUpdateSettings,
                    onLogout = onLogout,
                    currentPersonalHourlyRate = currentPersonalHourlyRate
                )
            }
            AccountSubPage.SQLITE_SYNC_STATUS -> {
                SqliteSyncStatusPage(
                    settings = settings,
                    isOnline = isOnline,
                    isSyncing = isSyncing,
                    totalUnsyncedCount = totalUnsyncedCount,
                    unsyncedJobsCount = unsyncedJobsCount,
                    unsyncedExpensesCount = unsyncedExpensesCount,
                    unsyncedWithdrawalsCount = unsyncedWithdrawalsCount,
                    unsyncedCustomersCount = unsyncedCustomersCount,
                    isSimulatedOffline = isSimulatedOffline,
                    onToggleSimulatedOffline = onToggleSimulatedOffline,
                    onTriggerSync = onTriggerSync
                )
            }
        }
    }
}
@Composable
fun AccountMainDashboard(
    settings: AppSettingsEntity,
    partners: List<PartnerEntity>,
    tractors: List<TractorEntity>,
    isSyncing: Boolean,
    isOnline: Boolean = true,
    totalUnsyncedCount: Int = 0,
    pendingInvitations: List<WorkspaceInvitation> = emptyList(),
    workspaceMembers: List<com.example.data.firebase.WorkspaceMember> = emptyList(),
    isCollaborationOwner: Boolean = true,
    activeWorkspaceId: String? = null,
    onAcceptInvitation: (WorkspaceInvitation) -> Unit = {},
    onDeclineInvitation: (WorkspaceInvitation) -> Unit = {},
    isSimulatedOffline: Boolean = false,
    onToggleSimulatedOffline: ((Boolean) -> Unit)? = null,
    onNavigate: (AccountSubPage) -> Unit,
    onTriggerSync: () -> Unit,
    onUpdateSettings: (AppSettingsEntity) -> Unit,
    onLogout: () -> Unit,
    currentPersonalHourlyRate: Double? = null,
    authenticatedUserName: String = "",
    authenticatedUserRole: String = ""
) {
    val context = LocalContext.current
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()
    // Dialog States
    var showProfileDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }
    var showCloudSyncDialog by remember { mutableStateOf(false) }
    var showSupportDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    // Section expansion states (all expanded by default like in screenshot)
    var isAccountExpanded by remember { mutableStateOf(true) }
    var isPreferencesExpanded by remember { mutableStateOf(true) }
    var isSupportExpanded by remember { mutableStateOf(true) }
    // Sub-item expansion states for Currency and Language
    var isCurrencyExpanded by remember { mutableStateOf(false) }
    var isLanguageExpanded by remember { mutableStateOf(false) }
    var photoToCropUri by remember { mutableStateOf<Uri?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            photoToCropUri = uri
        }
    }
    if (photoToCropUri != null) {
        ImageCropDialog(
            imageUri = photoToCropUri!!,
            onDismiss = { photoToCropUri = null },
            onCropSaved = { croppedUri ->
                onUpdateSettings(settings.copy(profilePhotoUri = croppedUri.toString()))
                photoToCropUri = null
            }
        )
    }
    val activePartner = partners.find { it.name.equals(settings.activePartnerName, ignoreCase = true) }
    val currentAuthUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
    val currentMember = workspaceMembers.find {
        it.uid == currentAuthUser?.uid || (currentAuthUser?.phoneNumber != null && it.phoneNumber == currentAuthUser.phoneNumber)
    }
    val loggedInName = if (isCollaborationOwner) {
        authenticatedUserName.ifBlank { settings.ownerName.ifBlank { settings.activePartnerName.ifBlank { "Owner" } } }
    } else {
        authenticatedUserName.ifBlank {
            currentMember?.displayName?.takeIf { it.isNotBlank() }
                ?: settings.activePartnerName.takeIf { it.isNotBlank() && !it.equals(settings.ownerName, ignoreCase = true) }
                ?: currentAuthUser?.displayName?.takeIf { it.isNotBlank() }
                ?: (if (isTamil) "பங்குதாரர்" else "Partner")
        }
    }
    val displayRole = if (isCollaborationOwner) {
        if (isTamil) "வணிக உரிமையாளர்" else "Business Owner"
    } else {
        when {
            authenticatedUserRole.equals("operator", ignoreCase = true) || currentMember?.role.equals("operator", ignoreCase = true) -> if (isTamil) "இயக்குநர்" else "Operator"
            else -> if (isTamil) "பங்குதாரர்" else "Partner"
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = responsive.screenPaddingHorizontal,
            vertical = responsive.screenPaddingVertical
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Pending Shared Workspace Invitation Alert Banner
        if (pendingInvitations.isNotEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF9E6)),
                    border = BorderStroke(1.5.dp, Color(0xFFFFB300)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card_pending_invitation_banner")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = Color(0xFFE65100),
                                modifier = Modifier.size(22.dp)
                            )
                            Text(
                                text = if (isTamil) "பகிர்வு கணக்கு அழைப்பு வந்துள்ளது!" else "Shared Workspace Invitation!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFFE65100)
                            )
                        }
                        pendingInvitations.forEach { inv ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White, RoundedCornerShape(10.dp))
                                    .border(1.dp, Color(0xFFFFE082), RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${inv.workspaceName} (${if (isTamil) "உரிமையாளர்" else "Owner"}: ${inv.ownerName.ifBlank { inv.ownerPhone }})",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = TextPrimaryDark
                                )
                                Text(
                                    text = if (isTamil) "உங்களை ${inv.role} ஆக இந்த கணக்கில் இணைய அழைத்துள்ளார்." else "Invited you to collaborate as a ${inv.role}.",
                                    fontSize = 12.sp,
                                    color = TextSecondaryDark
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = { onDeclineInvitation(inv) }
                                    ) {
                                        Text(if (isTamil) "நிராகரி" else "Decline", color = AlertDueRed, fontSize = 12.sp)
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Button(
                                        onClick = { onAcceptInvitation(inv) },
                                        colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Text(if (isTamil) "இணைந்துகொள்" else "Accept & Join", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // =========================================================================
        // 1. Hero Profile Card (Shri Guru Agency / Owner Details) - Matching Image 2
        // =========================================================================
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                border = BorderStroke(1.dp, Color(0xFFC8E6C9)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("account_hero_profile_card")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFEAF5ED),
                                    Color(0xFFE0F2E6),
                                    Color(0xFFD6EEDB)
                                )
                            )
                        )
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.account_tractor_hero),
                        contentDescription = "Account Hero Illustration",
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (responsive.isSmallPhone) 12.dp else 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Avatar / Tractor Logo Circle
                            Box(
                                contentAlignment = Alignment.BottomEnd,
                                modifier = Modifier.size(if (responsive.isSmallPhone) 62.dp else 68.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White,
                                    shadowElevation = 3.dp,
                                    border = BorderStroke(1.5.dp, Color.White),
                                    modifier = Modifier
                                        .size(if (responsive.isSmallPhone) 62.dp else 68.dp)
                                        .clickable { photoPickerLauncher.launch("image/*") }
                                ) {
                                    if (settings.profilePhotoUri.isNotBlank()) {
                                        PartnerAvatarImage(
                                            photoUri = settings.profilePhotoUri,
                                            name = loggedInName,
                                            size = if (responsive.isSmallPhone) 62.dp else 68.dp,
                                            avatarColorHex = "#0F4C28"
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier.fillMaxSize(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Agriculture,
                                                contentDescription = "Farm Fleet Logo",
                                                tint = Color(0xFF166534),
                                                modifier = Modifier.size(if (responsive.isSmallPhone) 36.dp else 40.dp)
                                            )
                                        }
                                    }
                                }
                                // Camera overlay badge
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF166534))
                                        .clickable { photoPickerLauncher.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CameraAlt,
                                        contentDescription = "Change Photo",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                            // Agency details
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    text = settings.businessName.ifBlank { if (isTamil) "டிராக்டர் சேவைகள்" else "Tractor Services" },
                                    fontSize = if (responsive.isSmallPhone) 16.sp else 17.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF133220),
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color(0xFF166534),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = loggedInName,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1B382B),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }

                                val displayPhone = if (isCollaborationOwner) {
                                    settings.businessPhone.ifBlank { currentAuthUser?.phoneNumber.orEmpty() }
                                } else {
                                    currentAuthUser?.phoneNumber?.takeIf { it.isNotBlank() }
                                        ?: currentMember?.phoneNumber?.takeIf { it.isNotBlank() }
                                        ?: settings.activePartnerPhone.takeIf { it.isNotBlank() }
                                        ?: ""
                                }
                                if (displayPhone.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = null,
                                            tint = Color(0xFF166534),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = displayPhone,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1B382B),
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                if (settings.businessAddress.isNotBlank()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            tint = Color(0xFF166534),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = settings.businessAddress,
                                            fontSize = 12.sp,
                                            color = Color(0xFF2C4336),
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 2,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        // Bottom full-width dark green capsule bar (Matching Image 2)
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF072D18),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp, horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFF1D5A37),
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isTamil) "பங்கு: $displayRole" else "Role: $displayRole",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
        // =========================================================================
        // 2. Section 1: ACCOUNT & FLEET
        // =========================================================================
        item {
            AccountSectionCard(
                icon = Icons.Default.People,
                title = if (isTamil) "கணக்கு & கடற்படை" else "ACCOUNT & FLEET",
                isExpanded = isAccountExpanded,
                onToggleExpand = { isAccountExpanded = !isAccountExpanded }
            ) {
                Column {
                    AccountSectionItem(
                        icon = Icons.Default.Person,
                        title = if (isTamil) "சுயவிவர தகவல்" else "Profile Information",
                        testTag = "btn_nav_profile_info",
                        onClick = { showProfileDialog = true }
                    )
                    HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                    AccountSectionItem(
                        icon = Icons.Default.Agriculture,
                        title = if (isTamil) "டிராக்டர்களை நிர்வகி" else "Manage Fleet Tractors",
                        badgeText = "${tractors.size} Tractors",
                        testTag = "btn_nav_manage_tractors",
                        onClick = { onNavigate(AccountSubPage.MANAGE_TRACTORS) }
                    )
                    HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                    AccountSectionItem(
                        icon = Icons.Default.People,
                        title = if (isCollaborationOwner) {
                            if (isTamil) "பங்குதாரர்களை நிர்வகி" else "Manage Partners"
                        } else {
                            if (isTamil) "வணிக பங்குதாரர்கள்" else "Business Partners"
                        },
                        badgeText = "${partners.size} Partners",
                        testTag = "btn_nav_manage_partners",
                        onClick = { onNavigate(AccountSubPage.MANAGE_PARTNERS) }
                    )
                    HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                    AccountSectionItem(
                        icon = Icons.Default.CloudDone,
                        title = if (isTamil) "பகிர்வு மேகக்கணி ஒத்திசைவு" else "Shared Cloud Sync",
                        badgeText = if (isSyncing) "Syncing..." else if (!isOnline) "Offline" else "Connected",
                        testTag = "btn_nav_cloud_sync",
                        onClick = { showCloudSyncDialog = true }
                    )
                    HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                    AccountSectionItem(
                        icon = Icons.Default.Security,
                        title = if (isTamil) "பயன்பாட்டு அணுகல் & பாதுகாப்பு" else "App Access & Security",
                        testTag = "btn_nav_app_security",
                        onClick = { showSecurityDialog = true }
                    )
                }
            }
        }
        // =========================================================================
        // 3. Section 2: PREFERENCES
        // =========================================================================
        item {
            AccountSectionCard(
                icon = Icons.Default.Tune,
                title = if (isTamil) "விருப்பத்தேர்வுகள்" else "PREFERENCES",
                isExpanded = isPreferencesExpanded,
                onToggleExpand = { isPreferencesExpanded = !isPreferencesExpanded }
            ) {
                Column {
                    // Currency row (Click to expand/collapse selection card underneath)
                    AccountSectionItem(
                        icon = Icons.Default.MonetizationOn,
                        title = if (isTamil) "நாணயம்" else "Currency",
                        showChevron = false,
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFE8F5E9)
                                ) {
                                    Text(
                                        text = when (settings.currency) {
                                            "$" -> "$ (USD)"
                                            "€" -> "€ (EUR)"
                                            else -> "₹ (INR)"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF166534),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Icon(
                                    imageVector = if (isCurrencyExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isCurrencyExpanded) "Collapse Currency" else "Expand Currency",
                                    tint = Color(0xFF166534),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        testTag = "btn_pref_currency",
                        onClick = { isCurrencyExpanded = !isCurrencyExpanded }
                    )
                    // Expandable Card for Currency Selection
                    AnimatedVisibility(
                        visible = isCurrencyExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    Triple("₹", "₹ Indian Rupee (INR)", if (isTamil) "ரூபாய் (INR)" else "Indian Rupee (INR)"),
                                    Triple("$", "$ US Dollar (USD)", if (isTamil) "அமெரிக்க டாலர் (USD)" else "US Dollar (USD)"),
                                    Triple("€", "€ Euro (EUR)", if (isTamil) "யூரோ (EUR)" else "Euro (EUR)")
                                ).forEach { (symbol, labelEn, labelTa) ->
                                    val isSelected = settings.currency == symbol
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color(0xFFE8F5E9) else Color.Transparent,
                                        border = if (isSelected) BorderStroke(1.dp, Color(0xFF166534)) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onUpdateSettings(settings.copy(currency = symbol))
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Surface(
                                                    shape = CircleShape,
                                                    color = if (isSelected) Color(0xFF166534) else Color(0xFFE5E7EB),
                                                    modifier = Modifier.size(26.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Text(
                                                            text = symbol,
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (isSelected) Color.White else Color(0xFF4B5563)
                                                        )
                                                    }
                                                }
                                                Text(
                                                    text = if (isTamil) labelTa else labelEn,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color(0xFF166534) else Color(0xFF374151)
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = Color(0xFF166534),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                    // Language row (Click to expand/collapse selection card underneath)
                    AccountSectionItem(
                        icon = Icons.Default.Language,
                        title = if (isTamil) "மொழி" else "Language",
                        showChevron = false,
                        trailingContent = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFFE8F5E9)
                                ) {
                                    Text(
                                        text = if (isTamil) "தமிழ்" else "English",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF166534),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                                Icon(
                                    imageVector = if (isLanguageExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isLanguageExpanded) "Collapse Language" else "Expand Language",
                                    tint = Color(0xFF166534),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        testTag = "btn_pref_language",
                        onClick = { isLanguageExpanded = !isLanguageExpanded }
                    )
                    // Expandable Card for Language Selection
                    AnimatedVisibility(
                        visible = isLanguageExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                            border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(
                                    Pair("EN", "English (Default)"),
                                    Pair("TA", "தமிழ் (Tamil)")
                                ).forEach { (langCode, langTitle) ->
                                    val isSelected = (langCode == "TA" && isTamil) || (langCode == "EN" && !isTamil)
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) Color(0xFFE8F5E9) else Color.Transparent,
                                        border = if (isSelected) BorderStroke(1.dp, Color(0xFF166534)) else null,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onUpdateSettings(settings.copy(language = langCode))
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Language,
                                                    contentDescription = null,
                                                    tint = if (isSelected) Color(0xFF166534) else Color(0xFF6B7280),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                    text = langTitle,
                                                    fontSize = 13.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) Color(0xFF166534) else Color(0xFF374151)
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = Color(0xFF166534),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                    // Business Preferences row
                    val personalRateForBadge = currentPersonalHourlyRate ?: settings.defaultHourlyRate
                    AccountSectionItem(
                        icon = Icons.Default.Settings,
                        title = if (isTamil) "வணிக விருப்பத்தேர்வுகள்" else "Business Preferences",
                        badgeText = "${settings.currency}${personalRateForBadge.toInt()}/hr",
                        testTag = "btn_nav_settings",
                        onClick = { onNavigate(AccountSubPage.SETTINGS) }
                    )
                    HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                    // Room SQLite & Cloud Push row
                    AccountSectionItem(
                        icon = Icons.Default.Storage,
                        title = if (isTamil) "SQLite & கிளவுட் புஷ்" else "Room SQLite & Cloud Push",
                        badgeText = if (!isOnline) "Offline ($totalUnsyncedCount)" else if (totalUnsyncedCount > 0) "$totalUnsyncedCount Pending" else "Synced",
                        testTag = "btn_nav_sqlite_sync",
                        onClick = { onNavigate(AccountSubPage.SQLITE_SYNC_STATUS) }
                    )
                }
            }
        }
        // =========================================================================
        // 4. Section 3: SUPPORT & LEGAL
        // =========================================================================
        item {
            AccountSectionCard(
                icon = Icons.Default.HeadsetMic,
                title = if (isTamil) "ஆதரவு & சட்டபூர்வ" else "SUPPORT & LEGAL",
                isExpanded = isSupportExpanded,
                onToggleExpand = { isSupportExpanded = !isSupportExpanded }
            ) {
                Column {
                    AccountSectionItem(
                        icon = Icons.Default.Call,
                        title = if (isTamil) "உதவி & ஆதரவு" else "Help & Support",
                        testTag = "btn_nav_help_support",
                        onClick = { showSupportDialog = true }
                    )
                    HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                    AccountSectionItem(
                        icon = Icons.Default.MenuBook,
                        title = if (isTamil) "வழிகாட்டி" else "Guide",
                        testTag = "btn_nav_guide",
                        onClick = { showGuideDialog = true }
                    )
                    HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                    AccountSectionItem(
                        icon = Icons.Default.Policy,
                        title = if (isTamil) "தனியுரிமைக் கொள்கை" else "Privacy Policy",
                        testTag = "btn_nav_privacy_policy",
                        onClick = { showPrivacyPolicyDialog = true }
                    )
                    HorizontalDivider(color = Color(0xFFF3F4F6), thickness = 1.dp)
                    AccountSectionItem(
                        icon = Icons.Default.Description,
                        title = if (isTamil) "சட்டபூர்வ, விதிமுறைகள் & நிபந்தனைகள்" else "Legal, Terms & Conditions",
                        testTag = "btn_nav_terms",
                        onClick = { showTermsDialog = true }
                    )
                }
            }
        }
        // =========================================================================
        // 5. Logout Button (Matching Image 3)
        // =========================================================================
        item {
            Button(
                onClick = { showLogoutDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("btn_logout_action")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Logout",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isTamil) "வெளியேறு" else "Logout",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
    // =============================================================================
    // Dialogs
    // =============================================================================
    // 1. Edit Profile Information Dialog
    if (showProfileDialog) {
        EditProfileInfoDialog(
            settings = settings,
            isTamil = isTamil,
            isCollaborationOwner = isCollaborationOwner,
            currentUserName = loggedInName,
            onDismiss = { showProfileDialog = false },
            onSave = { updatedSettings ->
                onUpdateSettings(updatedSettings)
                showProfileDialog = false
            },
            onPickPhoto = { photoPickerLauncher.launch("image/*") }
        )
    }
    // 2. App Access & Security Dialog
    if (showSecurityDialog) {
        AppSecurityDialog(
            settings = settings,
            isTamil = isTamil,
            onDismiss = { showSecurityDialog = false }
        )
    }
    // 3. Shared Cloud Sync Dialog
    if (showCloudSyncDialog) {
        CloudSyncStatusDialog(
            settings = settings,
            isSyncing = isSyncing,
            isOnline = isOnline,
            totalUnsyncedCount = totalUnsyncedCount,
            isTamil = isTamil,
            onTriggerSync = onTriggerSync,
            onDismiss = { showCloudSyncDialog = false }
        )
    }
    // 4. Help & Support Dialog (Call & WhatsApp)
    if (showSupportDialog) {
        SupportContactDialog(
            isTamil = isTamil,
            onDismiss = { showSupportDialog = false },
            onCall = { openDialer(context, "8778285956") },
            onWhatsApp = {
                openWhatsApp(
                    context,
                    "918778285956",
                    "Hello AIDHUNT Trac Support, I need assistance with my tractor management account."
                )
            }
        )
    }
    // 5. User Guide Dialog
    if (showGuideDialog) {
        UserGuideDialog(
            isTamil = isTamil,
            onDismiss = { showGuideDialog = false }
        )
    }
    // 6. Privacy Policy Dialog
    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(
            isTamil = isTamil,
            onDismiss = { showPrivacyPolicyDialog = false }
        )
    }
    // 7. Terms & Conditions Dialog
    if (showTermsDialog) {
        TermsAndConditionsDialog(
            isTamil = isTamil,
            onDismiss = { showTermsDialog = false }
        )
    }
    // 8. Logout Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Text(
                    text = if (isTamil) "வெளியேற வேண்டுமா?" else "Log Out from Account?",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF166534)
                )
            },
            text = {
                Text(
                    text = if (isTamil) "மீண்டும் உள்நுழைய OTP சரிபார்ப்பு தேவைப்படும்." else "You will need to verify via Phone OTP to log back into your shared account."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertDueRed)
                ) {
                    Text(if (isTamil) "வெளியேறு" else "Log Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(if (isTamil) "ரத்து" else "Cancel")
                }
            }
        )
    }
}
// -----------------------------------------------------------------------------
// Component: Section Card with Expand/Collapse Header
// -----------------------------------------------------------------------------
@Composable
fun AccountSectionCard(
    icon: ImageVector,
    title: String,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Section Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .padding(vertical = 4.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE8F5E9)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF166534),
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF166534),
                    letterSpacing = 0.5.sp
                )
            }
            IconButton(
                onClick = onToggleExpand,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color(0xFF166534),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        // Section Content Card
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                content()
            }
        }
    }
}
// -----------------------------------------------------------------------------
// Component: Section Item Row
// -----------------------------------------------------------------------------
@Composable
fun AccountSectionItem(
    icon: ImageVector,
    title: String,
    badgeText: String = "",
    trailingContent: (@Composable () -> Unit)? = null,
    showChevron: Boolean = true,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Icon Circle
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color(0xFFE8F5E9)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = Color(0xFF166534),
                modifier = Modifier.size(20.dp)
            )
        }
        // Title
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1F2937),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
        // Trailing Content / Badge / Chevron
        if (trailingContent != null) {
            trailingContent()
        } else if (badgeText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFE8F5E9)
            ) {
                Text(
                    text = badgeText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF166534),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
        if (showChevron) {
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Navigate",
                tint = Color(0xFF9CA3AF),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
// -----------------------------------------------------------------------------
// Dialog 1: Profile Information Dialog
// -----------------------------------------------------------------------------
@Composable
fun EditProfileInfoDialog(
    settings: AppSettingsEntity,
    isTamil: Boolean,
    isCollaborationOwner: Boolean = true,
    currentUserName: String = "",
    onDismiss: () -> Unit,
    onSave: (AppSettingsEntity) -> Unit,
    onPickPhoto: () -> Unit
) {
    val currentAuthUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
    var businessName by remember { mutableStateOf(settings.businessName) }
    var activePartnerName by remember {
        mutableStateOf(
            if (isCollaborationOwner) settings.ownerName.ifBlank { currentUserName.ifBlank { settings.activePartnerName } }
            else currentUserName.ifBlank { settings.activePartnerName.takeIf { !it.equals(settings.ownerName, ignoreCase = true) } ?: "" }
        )
    }
    var userPhone by remember {
        mutableStateOf(
            if (isCollaborationOwner) {
                settings.businessPhone.ifBlank { currentAuthUser?.phoneNumber.orEmpty() }
            } else {
                currentAuthUser?.phoneNumber?.takeIf { it.isNotBlank() }
                    ?: settings.activePartnerPhone.takeIf { it.isNotBlank() }
                    ?: ""
            }
        )
    }
    var businessAddress by remember { mutableStateOf(settings.businessAddress) }
    var gstNumber by remember { mutableStateOf(settings.gstNumber) }
    val coroutineScope = rememberCoroutineScope()
    val busNameReq = remember { BringIntoViewRequester() }
    val pNameReq = remember { BringIntoViewRequester() }
    val phoneReq = remember { BringIntoViewRequester() }
    val addressReq = remember { BringIntoViewRequester() }
    val gstReq = remember { BringIntoViewRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isTamil) "சுயவிவரத் தகவல்" else "Profile Information",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = Color(0xFF166534)
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 340.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    // Photo Change Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PartnerAvatarImage(
                                photoUri = settings.profilePhotoUri,
                                name = activePartnerName,
                                size = 48.dp,
                                avatarColorHex = "#0F4C28"
                            )
                            Column {
                                Text(text = if (isTamil) "சுயவிவரப் படம்" else "Profile Photo", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                Text(text = if (isTamil) "மாற்ற தட்டவும்" else "Tap to change", fontSize = 11.sp, color = Color.Gray)
                            }
                        }
                        OutlinedButton(
                            onClick = onPickPhoto,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(if (isTamil) "மாற்று" else "Change", fontSize = 11.sp, color = Color(0xFF166534))
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = { if (isCollaborationOwner) businessName = it },
                        readOnly = !isCollaborationOwner,
                        enabled = isCollaborationOwner,
                        label = {
                            Text(
                                if (isCollaborationOwner) {
                                    if (isTamil) "வணிகப் பெயர்" else "Agency / Business Name"
                                } else {
                                    if (isTamil) "வணிகப் பெயர் (பார்வை மட்டும்)" else "Business Name (Read-Only)"
                                }
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = Color(0xFF166534)) },
                        modifier = Modifier.fillMaxWidth().trackFocusedField(busNameReq, coroutineScope),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                item {
                    OutlinedTextField(
                        value = activePartnerName,
                        onValueChange = { activePartnerName = it },
                        label = {
                            Text(
                                if (isCollaborationOwner) {
                                    if (isTamil) "உரிமையாளர் பெயர்" else "Owner Name"
                                } else {
                                    if (isTamil) "பங்குதாரர் பெயர்" else "Partner Name"
                                }
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF166534)) },
                        modifier = Modifier.fillMaxWidth().trackFocusedField(pNameReq, coroutineScope),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                item {
                    OutlinedTextField(
                        value = userPhone,
                        onValueChange = { userPhone = it },
                        label = {
                            Text(
                                if (isCollaborationOwner) {
                                    if (isTamil) "வணிக தொலைபேசி எண்" else "Business Phone"
                                } else {
                                    if (isTamil) "உங்கள் தொலைபேசி எண்" else "Your Phone Number"
                                }
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.Call, contentDescription = null, tint = Color(0xFF166534)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.fillMaxWidth().trackFocusedField(phoneReq, coroutineScope),
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp)
                    )
                }
                if (isCollaborationOwner) {
                    item {
                        OutlinedTextField(
                            value = businessAddress,
                            onValueChange = { businessAddress = it },
                            label = { Text(if (isTamil) "முகவரி / இருப்பிடம்" else "Address / Location") },
                            leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF166534)) },
                            modifier = Modifier.fillMaxWidth().trackFocusedField(addressReq, coroutineScope),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = gstNumber,
                            onValueChange = { gstNumber = it },
                            label = { Text(if (isTamil) "ஜிஎஸ்டி / பதிவு குறியீடு" else "GST / Reg Code") },
                            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = Color(0xFF166534)) },
                            modifier = Modifier.fillMaxWidth().trackFocusedField(gstReq, coroutineScope),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isCollaborationOwner) {
                        onSave(
                            settings.copy(
                                businessName = businessName.trim(),
                                ownerName = activePartnerName.trim(),
                                activePartnerName = activePartnerName.trim(),
                                businessPhone = userPhone.trim(),
                                businessAddress = businessAddress.trim(),
                                gstNumber = gstNumber.trim()
                            )
                        )
                    } else {
                        onSave(
                            settings.copy(
                                activePartnerName = activePartnerName.trim(),
                                activePartnerPhone = userPhone.trim()
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF166534))
            ) {
                Text(if (isTamil) "சேமி" else "Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isTamil) "ரத்து" else "Cancel")
            }
        }
    )
}
// -----------------------------------------------------------------------------
// Dialog 2: App Access & Security Dialog
// -----------------------------------------------------------------------------
@Composable
fun AppSecurityDialog(
    settings: AppSettingsEntity,
    isTamil: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Security, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isTamil) "பயன்பாட்டு பாதுகாப்பு" else "App Access & Security",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF166534)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isTamil) "🔐 உள்ளூர் குறியாக்கம் (Room SQLite)" else "🔐 Local Encryption (Room SQLite)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF166534)
                        )
                        Text(
                            text = if (isTamil) "உங்கள் தரவு உங்கள் சாதனத்தில் பாதுகாப்பாக குறியாக்கம் செய்யப்பட்டுள்ளது." else "Customer billing logs, diesel slips, and partner balances are stored in encrypted local sandbox storage.",
                            fontSize = 11.5.sp,
                            color = Color(0xFF374151)
                        )
                    }
                }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isTamil) "பகிரப்பட்ட கணக்கு ஐடி" else "Shared Fleet Account ID",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF1F2937)
                        )
                        Text(
                            text = settings.sharedAccountId.ifBlank { "AIDHUNT-TRAC-FLEET-SYNC" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF166534)
                        )
                    }
                }
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF9FAFB)),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (isTamil) "OTP உள்நுழைவு அங்கீகாரம்" else "OTP Multi-Device Verification",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF1F2937)
                        )
                        Text(
                            text = if (isTamil) "அனைத்து 3 பங்குதாரர் சாதனங்களும் பாதுகாப்பான OTP மூலம் உள்நுழைந்துள்ளன." else "Only registered partner phone numbers can sign in and sync live fleet updates.",
                            fontSize = 11.5.sp,
                            color = Color(0xFF4B5563)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF166534))
            ) {
                Text(if (isTamil) "சரி" else "OK")
            }
        }
    )
}
// -----------------------------------------------------------------------------
// Dialog 3: Shared Cloud Sync Dialog
// -----------------------------------------------------------------------------
@Composable
fun CloudSyncStatusDialog(
    settings: AppSettingsEntity,
    isSyncing: Boolean,
    isOnline: Boolean,
    totalUnsyncedCount: Int,
    isTamil: Boolean,
    onTriggerSync: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isTamil) "மேகக்கணி ஒத்திசைவு நிலை" else "Shared Cloud Sync Status",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF166534)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = if (isTamil) "இணைப்பு நிலை:" else "Network Status:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isOnline) Color(0xFFE8F5E9) else AlertDueRedBg
                    ) {
                        Text(
                            text = if (isOnline) "Connected (Online)" else "Offline",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isOnline) Color(0xFF166534) else AlertDueRed,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = if (isTamil) "நிலுவையில் உள்ள பதிவுகள்:" else "Pending Sync Records:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = "$totalUnsyncedCount Records",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (totalUnsyncedCount > 0) Color(0xFFD97706) else Color(0xFF166534)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = {
                        onTriggerSync()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF166534)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isSyncing) "Syncing..." else (if (isTamil) "இப்போது ஒத்திசைக்கவும்" else "Sync Cloud Data Now"))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isTamil) "மூடு" else "Close")
            }
        }
    )
}
// -----------------------------------------------------------------------------
// Dialog 4: Support Contact Dialog (Phone & WhatsApp)
// -----------------------------------------------------------------------------
@Composable
fun SupportContactDialog(
    isTamil: Boolean,
    onDismiss: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.HeadsetMic, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isTamil) "உதவி & ஆதரவு" else "AIDHUNT Trac Support",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF166534)
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(
                    text = if (isTamil) "டிராக்டர் மேலாண்மை மற்றும் கணக்கு தொடர்பான உதவிக்கு தொடர்பு கொள்ளவும்:" else "Contact our 24/7 tractor fleet support desk for queries, data restoration, or training:",
                    fontSize = 12.5.sp,
                    color = Color(0xFF374151)
                )
                // Phone Support Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3F4F6)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCall)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFE8F5E9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Call, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text(text = if (isTamil) "தொலைபேசி அழைப்பு" else "Direct Phone Call", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(text = "8778285956", fontSize = 12.sp, color = Color(0xFF166534), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                    }
                }
                // WhatsApp Support Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onWhatsApp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                            Column {
                                Text(text = if (isTamil) "வாட்ஸ்அப் ஆதரவு" else "WhatsApp Support Chat", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(text = "+91 8778285956", fontSize = 12.sp, color = Color(0xFF166534), fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF166534))
            ) {
                Text(if (isTamil) "சரி" else "Done")
            }
        }
    )
}
// -----------------------------------------------------------------------------
// Dialog 5: User Guide Dialog
// -----------------------------------------------------------------------------
@Composable
fun UserGuideDialog(
    isTamil: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.MenuBook, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isTamil) "பயன்பாட்டு வழிகாட்டி" else "AIDHUNT Trac User Guide",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF166534)
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(320.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = if (isTamil) "🚜 1. புதிய வேலை பதிவு:" else "🚜 1. Logging New Jobs:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF166534)
                    )
                    Text(
                        text = if (isTamil) "தொடக்க/முடிவு மீட்டர் நேரம், வாடிக்கையாளர் பெயர், வேலை வகை ஆகியவற்றை உள்ளிடவும். கட்டணம் தானாக கணக்கிடப்படும்." else "Select tractor, enter start/end hour meter, customer name, and rate. The total amount is calculated automatically.",
                        fontSize = 11.5.sp,
                        color = Color(0xFF374151)
                    )
                }
                item {
                    Text(
                        text = if (isTamil) "⛽ 2. டீசல் & பராமரிப்பு செலவுகள்:" else "⛽ 2. Fleet Expenses & Diesel:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF166534)
                    )
                    Text(
                        text = if (isTamil) "டிராக்டர் செலவுகள் பக்கத்தில் லிட்டர், தொகை மற்றும் ஆப்பரேட்டர் செலவுகளைப் பதிவு செய்யவும்." else "Log diesel quantity, price, driver betti, and spare parts under Fleet Expenses to accurately track cost per hour.",
                        fontSize = 11.5.sp,
                        color = Color(0xFF374151)
                    )
                }
                item {
                    Text(
                        text = if (isTamil) "🤝 3. 3-பங்குதாரர் பகிர்வு:" else "🤝 3. 3-Partner Live Sharing:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF166534)
                    )
                    Text(
                        text = if (isTamil) "அனைத்து பதிவுகளும் இணைக்கப்பட்ட அனைத்து பங்குதாரர் தொலைபேசிகளுடன் ஒத்திசைக்கப்படும்." else "All jobs, diesel slips, and partner withdrawals sync automatically across all connected partner phones.",
                        fontSize = 11.5.sp,
                        color = Color(0xFF374151)
                    )
                }
                item {
                    Text(
                        text = if (isTamil) "📄 4. வாட்ஸ்அப் ரசீது:" else "📄 4. WhatsApp Billing Slips:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF166534)
                    )
                    Text(
                        text = if (isTamil) "வேலை முடிந்ததும் ஒரே தட்டலில் வாடிக்கையாளருக்கு வாட்ஸ்அப்பில் விவர அறிக்கை அனுப்பலாம்." else "Share instant bill slips, customer outstanding dues, and monthly statements directly via WhatsApp with one tap.",
                        fontSize = 11.5.sp,
                        color = Color(0xFF374151)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF166534))
            ) {
                Text(if (isTamil) "புரிந்தது" else "Understood")
            }
        }
    )
}
// -----------------------------------------------------------------------------
// Dialog 6: Terms and Conditions Dialog
// -----------------------------------------------------------------------------
@Composable
fun TermsAndConditionsDialog(
    isTamil: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Description, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isTamil) "விதிமுறைகள் & நிபந்தனைகள்" else "Terms & Conditions",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF166534)
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(300.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "AIDHUNT Trac Fleet Terms of Service",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF166534)
                    )
                }
                item {
                    Text(
                        text = if (isTamil) "1. தரவு உரிமை: அனைத்து விவசாய வாடிக்கையாளர் மற்றும் டிராக்டர் தகவல்களும் உங்கள் நிறுவனத்தின் பிரத்யேக சொத்தாகும்." else "1. Fleet Ownership: All customer entries, hourly calculations, tractor maintenance logs, and financial records belong solely to your business.",
                        fontSize = 12.sp,
                        color = Color(0xFF374151)
                    )
                }
                item {
                    Text(
                        text = if (isTamil) "2. துல்லியம்: வேலை நேரங்கள் மற்றும் டீசல் விலைகள் உள்ளீடு செய்யப்பட்ட தரவின் அடிப்படையில் துல்லியமாக கணக்கிடப்படுகிறது." else "2. Mathematical Precision: Hourly rates, acre conversions, diesel mileage, and partner shares are computed with exact decimal precision.",
                        fontSize = 12.sp,
                        color = Color(0xFF374151)
                    )
                }
                item {
                    Text(
                        text = if (isTamil) "3. ஆதரவு: தொழில்நுட்ப உதவிக்கு 8778285956 என்ற எண்ணில் அழைக்கலாம்." else "3. Continuous Support: For technical queries or system updates, contact support at 8778285956.",
                        fontSize = 12.sp,
                        color = Color(0xFF374151)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF166534))
            ) {
                Text(if (isTamil) "ஏற்கிறேன்" else "Accept")
            }
        }
    )
}
// -----------------------------------------------------------------------------
// Dialog 7: Privacy Policy Dialog
// -----------------------------------------------------------------------------
@Composable
fun PrivacyPolicyDialog(
    isTamil: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Policy, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isTamil) "தனியுரிமைக் கொள்கை" else "Privacy Policy",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF166534)
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.height(300.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "AIDHUNT Trac Farm Management & Partner Fleet Sharing",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF166534)
                    )
                }
                item {
                    Text(
                        text = if (isTamil) {
                            "1. தரவுப் பாதுகாப்பு: உங்கள் வாடிக்கையாளர் விவரங்கள், நிலுவைத் தொகைகள், மற்றும் டிராக்டர் செலவுகள் உங்கள் உள்ளூர் சாதனத்திலும் உங்கள் அங்கீகரிக்கப்பட்ட பங்குதாரர் மேகக்கணியிலும் மட்டுமே பாதுகாப்பாக சேமிக்கப்படுகின்றன."
                        } else {
                            "1. Data Privacy & Local-First: All customer records, job logs, hourly rates, and diesel expense statements are securely maintained on your device and synchronized exclusively across your authorized business partners."
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF374151),
                        lineHeight = 16.sp
                    )
                }
                item {
                    Text(
                        text = if (isTamil) {
                            "2. தொலைபேசி எண்கள்: வாடிக்கையாளர் மற்றும் பங்குதாரர் தொலைபேசி எண்கள் வாட்ஸ்அப் அறிக்கை அனுப்புவதற்கும் தொலைபேசி அழைப்பிற்கும் மட்டுமே பயன்படுத்தப்படுகின்றன."
                        } else {
                            "2. Communication Usage: Customer phone numbers and contact details are used solely for WhatsApp statement sharing, billing verification, and direct customer telephone calls initiated by you."
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF374151),
                        lineHeight = 16.sp
                    )
                }
                item {
                    Text(
                        text = if (isTamil) {
                            "3. உதவி மற்றும் ஆதரவு: ஏதேனும் கேள்விகள் இருப்பின் எங்களது உதவி எண் 8778285956-ஐ தொடர்பு கொள்ளலாம்."
                        } else {
                            "3. Support Contact: For data queries, assistance, or account recovery, contact the AIDHUNT Trac helpline directly at 8778285956."
                        },
                        fontSize = 12.sp,
                        color = Color(0xFF374151),
                        lineHeight = 16.sp
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF166534))
            ) {
                Text(if (isTamil) "சரி" else "Close")
            }
        }
    )
}
// ----------------------------------------------------
// Subpage 1: Manage Fleet Tractors
// ----------------------------------------------------
@Composable
fun ManageTractorsPage(
    settings: AppSettingsEntity,
    tractors: List<TractorEntity>,
    onAddTractor: (TractorEntity) -> Unit,
    onUpdateTractor: (TractorEntity) -> Unit,
    onDeleteTractor: (TractorEntity) -> Unit,
    canAddExtension: Boolean = true,
    workTypeExtensions: List<WorkTypeExtensionEntity> = emptyList(),
    onAddExtension: ((String) -> Unit)? = null,
    canDeleteExtension: (WorkTypeExtensionEntity) -> Boolean = { true },
    onDeleteExtension: ((WorkTypeExtensionEntity) -> Unit)? = null
) {
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    var isAddingTractor by remember { mutableStateOf(false) }
    var isAddingExtension by remember { mutableStateOf(false) }
    var extensionNameInput by remember { mutableStateOf("") }
    var tractorToEdit by remember { mutableStateOf<TractorEntity?>(null) }
    var tractorToDelete by remember { mutableStateOf<TractorEntity?>(null) }
    if (isAddingTractor || tractorToEdit != null) {
        TractorFormPage(
            isTamil = isTamil,
            initialTractor = tractorToEdit,
            onBack = {
                isAddingTractor = false
                tractorToEdit = null
            },
            onSave = { tractor ->
                if (tractorToEdit != null) {
                    onUpdateTractor(tractor)
                } else {
                    onAddTractor(tractor)
                }
                isAddingTractor = false
                tractorToEdit = null
            },
            onDelete = { tractor ->
                tractorToDelete = tractor
            }
        )
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { isAddingTractor = true },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("btn_add_tractor"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isTamil) "புதிய டிராக்டர்" else "Add Tractor", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    if (canAddExtension) {
                        Button(
                            onClick = { isAddingExtension = true },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("btn_add_extension"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenHeader)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isTamil) "வேலை வகை சேர்க்க" else "Add Extension", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
            if (tractors.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = SageCardBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Agriculture,
                                contentDescription = null,
                                tint = SageAccent,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (isTamil) "டிராக்டர்கள் பதிவு செய்யப்படவில்லை" else "No tractors registered yet",
                                fontWeight = FontWeight.Medium,
                                color = ForestGreenHeader
                            )
                        }
                    }
                }
            } else {
                items(tractors, key = { it.id }) { tractor ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline.copy(alpha = 0.6f))),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { tractorToEdit = tractor }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(SoftSageGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Agriculture,
                                    contentDescription = null,
                                    tint = DeepSageGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tractor.label,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreenHeader
                                )
                                Text(
                                    text = "Reg / Chassis: ${tractor.chassisNo}",
                                    fontSize = 12.sp,
                                    color = TextSecondaryDark
                                )
                            }
                            Row {
                                IconButton(onClick = { tractorToEdit = tractor }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = DeepSageGreen, modifier = Modifier.size(20.dp))
                                }
                                IconButton(onClick = { tractorToDelete = tractor }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = TextMutedDark, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }
            if (workTypeExtensions.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = if (isTamil) "கூடுதல் வேலை வகைகள் (Extensions)" else "Work Type Extensions",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = ForestGreenHeader
                    )
                }
                items(workTypeExtensions, key = { it.id }) { ext ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline.copy(alpha = 0.6f))),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SoftSageGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Agriculture,
                                    contentDescription = null,
                                    tint = DeepSageGreen,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ext.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ForestGreenHeader
                                )
                                Text(
                                    text = if (isTamil) "பணிமனை வகை" else "Workspace Work Type",
                                    fontSize = 12.sp,
                                    color = TextSecondaryDark
                                )
                            }
                            if (canDeleteExtension(ext) && onDeleteExtension != null) {
                                IconButton(
                                    onClick = { onDeleteExtension.invoke(ext) },
                                    modifier = Modifier.size(36.dp).testTag("btn_delete_extension_${ext.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Extension",
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (isAddingExtension) {
        AlertDialog(
            onDismissRequest = {
                isAddingExtension = false
                extensionNameInput = ""
            },
            title = { Text(if (isTamil) "புதிய வேலை வகை (Extension) சேர்க்க" else "Add Work Type Extension", fontWeight = FontWeight.Bold, color = ForestGreenHeader) },
            text = {
                Column {
                    Text(
                        text = if (isTamil) "வேலை வகையின் பெயரை உள்ளிடவும் (எ.கா: Rotary Cutter, Leveler)" else "Enter the name of the work type extension (e.g. Rotary Cutter, Leveler)",
                        fontSize = 13.sp,
                        color = TextSecondaryDark
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = extensionNameInput,
                        onValueChange = { extensionNameInput = it },
                        label = { Text(if (isTamil) "வேலை வகை பெயர்" else "Extension Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("input_extension_name")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = extensionNameInput.trim()
                        if (trimmed.isNotBlank()) {
                            onAddExtension?.invoke(trimmed)
                            isAddingExtension = false
                            extensionNameInput = ""
                        }
                    },
                    enabled = extensionNameInput.trim().isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen),
                    modifier = Modifier.testTag("btn_save_extension")
                ) {
                    Text(if (isTamil) "சேமி" else "Save Extension", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    isAddingExtension = false
                    extensionNameInput = ""
                }) {
                    Text(if (isTamil) "ரத்து செய்" else "Cancel")
                }
            }
        )
    }
    tractorToDelete?.let { tractor ->
        AlertDialog(
            onDismissRequest = { tractorToDelete = null },
            title = { Text(if (isTamil) "டிராக்டரை நீக்கவா?" else "Delete Tractor?", fontWeight = FontWeight.Bold, color = ForestGreenHeader) },
            text = { Text(if (isTamil) "'${tractor.label}' டிராக்டரை நிச்சயமாக நீக்க விரும்புகிறீர்களா?" else "Are you sure you want to remove '${tractor.label}' from your fleet?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteTractor(tractor)
                        if (tractorToEdit?.id == tractor.id) {
                            tractorToEdit = null
                            isAddingTractor = false
                        }
                        tractorToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertDueRed)
                ) {
                    Text(if (isTamil) "நீக்கு" else "Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { tractorToDelete = null }) {
                    Text(if (isTamil) "ரத்து" else "Cancel")
                }
            }
        )
    }
}
@Composable
fun TractorFormPage(
    isTamil: Boolean,
    initialTractor: TractorEntity?,
    onBack: () -> Unit,
    onSave: (TractorEntity) -> Unit,
    onDelete: ((TractorEntity) -> Unit)? = null
) {
    BackHandler(onBack = onBack)
    var label by remember { mutableStateOf(initialTractor?.label ?: "") }
    var chassisNo by remember { mutableStateOf(initialTractor?.chassisNo ?: "") }
    var hasAttemptedSubmit by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val labelRequester = remember { BringIntoViewRequester() }
    val chassisRequester = remember { BringIntoViewRequester() }
    val isLabelInvalid = label.isBlank()
    val isChassisInvalid = chassisNo.isBlank()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
    ) {
        Surface(
            color = Color.White,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = DeepSageGreen
                    )
                }
                Text(
                    text = if (initialTractor != null) (if (isTamil) "டிராக்டர் விவரங்களை திருத்துக" else "Edit Tractor") else (if (isTamil) "புதிய டிராக்டர் சேர்க்க" else "Add New Tractor"),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForestGreenHeader,
                    modifier = Modifier.weight(1f)
                )
                if (initialTractor != null && onDelete != null) {
                    IconButton(onClick = { onDelete(initialTractor) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = AlertDueRed
                        )
                    }
                }
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline.copy(alpha = 0.7f))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text(
                            text = if (isTamil) "டிராக்டர் விவரங்கள்" else "Tractor Information",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreenHeader
                        )
                        OutlinedTextField(
                            value = label,
                            onValueChange = { label = it },
                            label = { Text(if (isTamil) "டிராக்டர் பெயர் / மாதிரி *" else "Tractor Name / Model *") },
                            placeholder = { Text("e.g. Mahindra 575 DI / John Deere") },
                            singleLine = true,
                            isError = hasAttemptedSubmit && isLabelInvalid,
                            supportingText = {
                                if (hasAttemptedSubmit && isLabelInvalid) {
                                    Text(
                                        if (isTamil) "டிராக்டர் பெயர் தேவை *" else "Tractor name is required *",
                                        color = AlertDueRed,
                                        fontSize = 10.sp
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .trackFocusedField(labelRequester, coroutineScope)
                                .testTag("input_tractor_name")
                        )
                        OutlinedTextField(
                            value = chassisNo,
                            onValueChange = { chassisNo = it },
                            label = { Text(if (isTamil) "பதிவு எண் / சேஸ் எண் *" else "Registration / Chassis No *") },
                            placeholder = { Text("e.g. TN-45-AB-1234") },
                            singleLine = true,
                            isError = hasAttemptedSubmit && isChassisInvalid,
                            supportingText = {
                                if (hasAttemptedSubmit && isChassisInvalid) {
                                    Text(
                                        if (isTamil) "பதிவு எண் அல்லது சேஸ் எண் தேவை *" else "Registration or chassis number is required *",
                                        color = AlertDueRed,
                                        fontSize = 10.sp
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .trackFocusedField(chassisRequester, coroutineScope)
                                .testTag("input_tractor_chassis")
                        )
                        // Display Supported Fixed Work Attachments with Tamil Translation
                        Text(
                            text = if (isTamil) "ஆதரிக்கப்படும் பணி வகைகள்:" else "Available Work / Extension Types:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DeepSageGreen
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            FIXED_WORK_TYPES.forEach { wt ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SageCardBg,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = if (isTamil) wt.ta else wt.en,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = DeepSageGreen,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        hasAttemptedSubmit = true
                        if (isLabelInvalid || isChassisInvalid) {
                            // Validation highlights displayed
                        } else {
                            val tractor = TractorEntity(
                                id = initialTractor?.id ?: 0,
                                label = label.trim(),
                                chassisNo = chassisNo.trim(),
                                modelYear = initialTractor?.modelYear ?: "",
                                operatorName = initialTractor?.operatorName ?: "",
                                isActive = true
                            )
                            onSave(tractor)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_save_tractor_page")
                ) {
                    Text(
                        text = if (initialTractor != null) (if (isTamil) "விவரங்களை புதுப்பிக்கவும்" else "Update Tractor Details") else (if (isTamil) "சேமிக்கவும்" else "Save New Tractor"),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(if (isTamil) "ரத்து" else "Cancel", color = TextMutedDark, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
// ----------------------------------------------------
// Subpage 2: Manage Partners
// ----------------------------------------------------
@Composable
fun ManagePartnersPage(
    settings: AppSettingsEntity,
    partners: List<PartnerEntity>,
    activePartnerName: String,
    workspaceMembers: List<com.example.data.firebase.WorkspaceMember> = emptyList(),
    isCollaborationOwner: Boolean = true,
    pendingInvitations: List<WorkspaceInvitation> = emptyList(),
    lockedPages: Set<String> = emptySet(),
    onSetPageLocked: ((String, Boolean) -> Unit)? = null,
    onGetPartnerLockedPages: ((String) -> Set<String>)? = null,
    onSetPartnerPageLocked: ((String, String, Boolean) -> Unit)? = null,
    onAcceptInvitation: (WorkspaceInvitation) -> Unit = {},
    onDeclineInvitation: (WorkspaceInvitation) -> Unit = {},
    onAddPartner: (PartnerEntity) -> Unit,
    onUpdatePartner: (PartnerEntity) -> Unit,
    onDeletePartner: (PartnerEntity) -> Unit,
    onSwitchActivePartner: (PartnerEntity) -> Unit,
    checklistItems: List<com.example.data.entity.ChecklistItemEntity> = emptyList(),
    onAddChecklistItem: ((String) -> Unit)? = null,
    onToggleChecklistItem: ((com.example.data.entity.ChecklistItemEntity) -> Unit)? = null,
    onDeleteChecklistItem: ((com.example.data.entity.ChecklistItemEntity) -> Unit)? = null,
    profitShareAllocations: Map<String, Int> = emptyMap(),
    profitShareAllocationDetails: Map<String, com.example.data.firebase.ProfitShareAllocation> = emptyMap(),
    onUpdateProfitShareAllocations: ((Map<String, Int>, Map<String, com.example.data.firebase.ProfitShareAllocation>) -> Unit)? = null,
    authenticatedUserRole: String = ""
) {
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    val isOperator = authenticatedUserRole.equals(com.example.data.auth.RoleUtils.ROLE_OPERATOR, ignoreCase = true)
    var showProfitShareDialog by remember { mutableStateOf(false) }
    val eligibleProfitShareParticipants = remember(workspaceMembers, partners, settings, isTamil) {
        getEligibleProfitShareParticipants(workspaceMembers, partners, settings, isTamil)
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var partnerToEdit by remember { mutableStateOf<PartnerEntity?>(null) }
    var partnerToDelete by remember { mutableStateOf<PartnerEntity?>(null) }
    var partnerForAccessControl by remember { mutableStateOf<PartnerUiModel?>(null) }
    val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid?.trim()
    val ownerNameClean = settings.ownerName.trim()
    val ownerPhoneClean = settings.businessPhone.filter { it.isDigit() }.takeLast(10)
    // Filter out local owner entities so owner never appears as a regular partner
    val filteredLocalPartners = partners.filter { partner ->
        val pPhoneClean = partner.phone.filter { it.isDigit() }.takeLast(10)
        val isOwnerRole = partner.role.equals("owner", ignoreCase = true)
        val isOwnerPhone = ownerPhoneClean.isNotBlank() && pPhoneClean == ownerPhoneClean
        !isOwnerRole && !isOwnerPhone
    }
    // Reconcile into unified UI models with strict Owner vs Partner display filtering
    val reconciledPartners = remember(filteredLocalPartners, workspaceMembers, currentUid, ownerPhoneClean, isCollaborationOwner, isTamil, settings.workspaceId, settings.ownerName, settings.businessPhone) {
        val list = mutableListOf<PartnerUiModel>()
        val matchedMemberKeys = mutableSetOf<String>()
        val ownerMember = workspaceMembers.find { it.role.equals("owner", ignoreCase = true) }
        val resolvedOwnerUid = ownerMember?.uid?.trim()?.ifBlank { null }
            ?: settings.workspaceId.removePrefix("ws_").trim()
        val resolvedOwnerName = ownerMember?.displayName?.trim()?.ifBlank { null }
            ?: settings.ownerName.trim().ifBlank { null }
            ?: (if (isTamil) "உரிமையாளர்" else "Owner")
        val resolvedOwnerPhone = ownerMember?.phoneNumber?.trim()?.ifBlank { null }
            ?: settings.businessPhone.trim()
        fun isOwner(uid: String?, role: String?): Boolean {
            if (role?.equals("owner", ignoreCase = true) == true) return true
            if (!uid.isNullOrBlank() && resolvedOwnerUid.isNotBlank() && uid == resolvedOwnerUid) return true
            return false
        }
        fun isCurrentUser(uid: String?): Boolean {
            return !currentUid.isNullOrBlank() && !uid.isNullOrBlank() && uid == currentUid
        }
        // 1. If viewing as a Partner, inject the Owner row as a read-only person at the top
        if (!isCollaborationOwner) {
            val ownerModel = PartnerUiModel(
                id = parseLongId("owner_$resolvedOwnerUid"),
                name = resolvedOwnerName,
                phone = resolvedOwnerPhone,
                role = if (isTamil) "உரிமையாளர்" else "Owner",
                photoUri = "",
                isConnected = true,
                originalPartnerEntity = null,
                uid = resolvedOwnerUid
            )
            list.add(ownerModel)
            if (resolvedOwnerUid.isNotBlank()) {
                matchedMemberKeys.add(resolvedOwnerUid)
            }
        }
        // 2. Process local partners and match with remote members
        for (partner in filteredLocalPartners) {
            val pPhoneClean = partner.phone.filter { it.isDigit() }.takeLast(10)
            val matchedMember = workspaceMembers.find { member ->
                val isUidMatch = member.uid.isNotBlank() && (partner.id == parseLongId(member.uid) || partner.id == member.uid.hashCode().toLong())
                val mPhoneClean = member.phoneNumber?.filter { it.isDigit() }?.takeLast(10) ?: ""
                val isPhoneMatch = mPhoneClean.isNotBlank() && mPhoneClean == pPhoneClean
                isUidMatch || isPhoneMatch
            }
            // Exclude Owner from partner entries
            if (isOwner(matchedMember?.uid, matchedMember?.role ?: partner.role)) {
                continue
            }
            // Exclude current logged in user (the partner themself or owner themself)
            val isSelfByUid = isCurrentUser(matchedMember?.uid) ||
                (!currentUid.isNullOrBlank() && (partner.id == parseLongId(currentUid) || partner.id == currentUid.hashCode().toLong()))
            if (isSelfByUid) {
                continue
            }
            val isConnected = matchedMember != null && !matchedMember.status.equals("waiting_for_registration", ignoreCase = true)
            if (matchedMember != null) {
                if (matchedMember.uid.isNotBlank()) {
                    matchedMemberKeys.add(matchedMember.uid)
                }
                val mPhoneClean = matchedMember.phoneNumber?.filter { it.isDigit() }?.takeLast(10) ?: ""
                if (mPhoneClean.isNotBlank()) {
                    matchedMemberKeys.add("phone_$mPhoneClean")
                }
            }
            if (pPhoneClean.isNotBlank()) {
                matchedMemberKeys.add("phone_$pPhoneClean")
            }
            list.add(
                PartnerUiModel(
                    id = partner.id,
                    name = partner.name.ifBlank { matchedMember?.displayName ?: partner.phone },
                    phone = partner.phone.ifBlank { matchedMember?.phoneNumber ?: "" },
                    role = partner.role.ifBlank { if (isTamil) "பங்குதாரர்" else "Partner" },
                    photoUri = partner.photoUri,
                    isConnected = isConnected,
                    originalPartnerEntity = partner,
                    uid = matchedMember?.uid ?: ""
                )
            )
        }
        // 3. Add remote members who aren't in the local list
        for (member in workspaceMembers) {
            // Exclude Owner
            if (isOwner(member.uid, member.role)) {
                continue
            }
            // Exclude current logged in user (the partner themself or owner themself)
            if (isCurrentUser(member.uid)) {
                continue
            }
            val memberPhoneClean = member.phoneNumber?.filter { it.isDigit() }?.takeLast(10) ?: ""
            val memberPhoneKey = if (memberPhoneClean.isNotBlank()) "phone_$memberPhoneClean" else ""
            if ((member.uid.isNotBlank() && matchedMemberKeys.contains(member.uid)) ||
                (memberPhoneKey.isNotBlank() && matchedMemberKeys.contains(memberPhoneKey))) {
                continue
            }
            // Prevent duplicate by phone if any
            val duplicateByPhone = list.any { p ->
                val pPhoneClean = p.phone.filter { it.isDigit() }.takeLast(10)
                pPhoneClean.isNotBlank() && pPhoneClean == memberPhoneClean
            }
            if (duplicateByPhone) {
                continue
            }
            val isConnectedMember = !member.status.equals("waiting_for_registration", ignoreCase = true) || member.uid.isNotBlank()
            list.add(
                PartnerUiModel(
                    id = if (member.uid.isNotBlank()) parseLongId(member.uid) else parseLongId(member.phoneNumber ?: "0"),
                    name = member.displayName?.ifBlank { null } ?: member.phoneNumber ?: (if (isTamil) "பங்குதாரர்" else "Partner"),
                    phone = member.phoneNumber ?: "",
                    role = member.role.ifBlank { if (isTamil) "பங்குதாரர்" else "Partner" },
                    photoUri = "",
                    isConnected = isConnectedMember,
                    originalPartnerEntity = null,
                    uid = member.uid
                )
            )
        }
        list
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isCollaborationOwner) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8F5)),
                    border = BorderStroke(1.dp, DeepSageGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = DeepSageGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isTamil) "வணிக பங்குதாரர்கள் (பார்வை மட்டும்)" else "Business Partners (Read-Only)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenHeader
                            )
                            Text(
                                text = if (isTamil)
                                    "பங்குதாரர்களை வணிக உரிமையாளர் மட்டுமே சேர்க்க/நீக்க முடியும்."
                                else
                                    "Partner memberships are managed by the business owner (${settings.ownerName.ifBlank { "Owner" }}).",
                                fontSize = 11.sp,
                                color = TextSecondaryDark
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Button(
                    onClick = { showAddDialog = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_add_partner"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isTamil) "புதிய பங்குதாரர் சேர்க்க" else "Add Business Partner", fontWeight = FontWeight.Bold)
                }
            }
        }
        if (!isOperator) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SageCardBg),
                    border = BorderStroke(1.dp, SageOutline.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showProfitShareDialog = true }
                        .testTag("card_profit_share_allocation")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = DeepSageGreen.copy(alpha = 0.12f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PieChart,
                                    contentDescription = null,
                                    tint = DeepSageGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = if (isTamil) "லாபப் பங்கு ஒதுக்கீடு" else "Profit Share Allocation",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreenHeader
                                )
                                if (!isCollaborationOwner) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = DeepSageGreen.copy(alpha = 0.1f)
                                    ) {
                                        Text(
                                            text = if (isTamil) "பார்வை மட்டும்" else "View Only",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = DeepSageGreen,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (isCollaborationOwner) {
                                    if (isTamil) "கூட்டாளர்களிடையே லாபப் பகிர்வு சதவீதத்தை உள்ளமைக்கவும்"
                                    else "Configure profit sharing percentages across partners"
                                } else {
                                    if (isTamil) "தற்போதைய லாபப் பகிர்வு ஒதுக்கீட்டைக் காண்க"
                                    else "View current profit sharing percentages"
                                },
                                fontSize = 11.sp,
                                color = TextSecondaryDark
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = TextSecondaryDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
        if (reconciledPartners.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, SageOutline.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = DeepSageGreen.copy(alpha = 0.6f),
                            modifier = Modifier.size(44.dp)
                        )
                        Text(
                            text = if (isTamil) "பங்குதாரர்கள் சேர்க்கப்படவில்லை" else "No Partners Added Yet",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreenHeader
                        )
                        Text(
                            text = if (isTamil)
                                "உங்கள் கூட்டாளியின் பதிவு செய்யப்பட்ட தொலைபேசி எண்ணை உள்ளிட்டு அவர்களை உடனடியாக இணைக்கவும்."
                            else
                                "Add your business partner using their registered phone number to automatically synchronize records.",
                            fontSize = 12.sp,
                            color = TextSecondaryDark,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
        items(reconciledPartners, key = { "${it.id}_${it.phone}_${it.role}" }) { partner ->
            val roleLabel = com.example.data.auth.RoleUtils.getRoleDisplayName(partner.role, isTamil)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline.copy(alpha = 0.6f))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PartnerAvatarImage(
                        photoUri = partner.photoUri,
                        name = partner.name,
                        size = 44.dp,
                        avatarColorHex = "#1E4D2B"
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = partner.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreenHeader
                        )
                        Text(
                            text = "$roleLabel • ${partner.phone}",
                            fontSize = 12.sp,
                            color = TextSecondaryDark
                        )
                        if (partner.isConnected) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFE8F5E9),
                                border = BorderStroke(0.5.dp, Color(0xFF81C784)),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = if (isTamil) "இணைக்கப்பட்டது" else "CONNECTED",
                                    fontSize = 10.sp,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = Color(0xFFFFF3E0),
                                border = BorderStroke(0.5.dp, Color(0xFFFFB74D)),
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Text(
                                    text = if (isTamil) "கணக்கு பதிவு செய்யப்படவில்லை" else "ACCOUNT NOT CREATED",
                                    fontSize = 10.sp,
                                    color = Color(0xFFE65100),
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    val canDeleteThisPartner = isCollaborationOwner || !partner.isConnected
                    if (isCollaborationOwner || canDeleteThisPartner) {
                        val entityToUse = partner.originalPartnerEntity ?: PartnerEntity(
                            id = partner.id,
                            workspaceId = settings.workspaceId,
                            name = partner.name,
                            phone = partner.phone,
                            role = partner.role,
                            photoUri = partner.photoUri
                        )
                        Row {
                            if (isCollaborationOwner) {
                                IconButton(onClick = { partnerToEdit = entityToUse }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = DeepSageGreen, modifier = Modifier.size(20.dp))
                                }
                            }
                            IconButton(onClick = { partnerToDelete = entityToUse }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AlertDueRed, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }

    }
    if (showAddDialog || partnerToEdit != null) {
        PartnerFormDialog(
            isTamil = isTamil,
            initialPartner = partnerToEdit,
            isCollaborationOwner = isCollaborationOwner,
            onDismiss = {
                showAddDialog = false
                partnerToEdit = null
            },
            onSave = { partner ->
                if (partnerToEdit != null) {
                    onUpdatePartner(partner)
                } else {
                    onAddPartner(partner)
                }
                showAddDialog = false
                partnerToEdit = null
            }
        )
    }
    partnerToDelete?.let { partner ->
        AlertDialog(
            onDismissRequest = { partnerToDelete = null },
            title = { Text(if (isTamil) "பங்குதாரரை நீக்கவா?" else "Remove Partner?", fontWeight = FontWeight.Bold, color = ForestGreenHeader) },
            text = {
                Text(
                    if (isTamil)
                        "'${partner.name}' பங்குதாரரை நிச்சயமாக நீக்க விரும்புகிறீர்களா? இவர்கள் இனி பகிரப்பட்ட பதிவுகளை பார்க்க முடியாது."
                    else
                        "Are you sure you want to remove '${partner.name}' from this shared business? They will no longer share entries and records."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeletePartner(partner)
                        partnerToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertDueRed)
                ) {
                    Text(if (isTamil) "நீக்கு" else "Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { partnerToDelete = null }) {
                    Text(if (isTamil) "ரத்து" else "Cancel")
                }
            }
        )
    }
    if (showProfitShareDialog && !isOperator) {
        ConfigurePercentagesDialog(
            eligibleParticipants = eligibleProfitShareParticipants,
            currentAllocations = profitShareAllocations,
            currentDetails = profitShareAllocationDetails,
            isTamil = isTamil,
            isReadOnly = !isCollaborationOwner,
            onDismiss = { showProfitShareDialog = false },
            onApply = { allocations, details ->
                onUpdateProfitShareAllocations?.invoke(allocations, details)
                showProfitShareDialog = false
            }
        )
    }
}

@Composable
fun BusinessChecklistCard(
    isTamil: Boolean,
    checklistItems: List<com.example.data.entity.ChecklistItemEntity>,
    onAddChecklistItem: ((String) -> Unit)?,
    onToggleChecklistItem: ((com.example.data.entity.ChecklistItemEntity) -> Unit)?,
    onDeleteChecklistItem: ((com.example.data.entity.ChecklistItemEntity) -> Unit)?
) {
    var newTaskText by remember { mutableStateOf("") }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, SageOutline.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("card_business_checklist")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = DeepSageGreen.copy(alpha = 0.12f),
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = DeepSageGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isTamil) "தினசரி வேலைப் பட்டியல்" else "Daily Tasks Checklist",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenHeader
                    )
                    Text(
                        text = if (isTamil) 
                            "டிராக்டர் பராமரிப்பு, வசூல் மற்றும் அன்றாட வேலைகளை திட்டமிடுங்கள்." 
                            else "Plan tractor maintenance, customer collections, and daily activities.",
                        fontSize = 11.5.sp,
                        color = TextSecondaryDark
                    )
                }
            }

            HorizontalDivider(color = SageOutline.copy(alpha = 0.4f))

            // Text Field to Add New Task
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newTaskText,
                    onValueChange = { newTaskText = it },
                    placeholder = {
                        Text(
                            text = if (isTamil) "புதிய வேலை..." else "Add new task...",
                            fontSize = 13.sp,
                            color = TextMutedDark
                        )
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("input_checklist_task"),
                    shape = RoundedCornerShape(8.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = DeepSageGreen,
                        unfocusedBorderColor = SageOutline,
                        cursorColor = DeepSageGreen
                    ),
                    singleLine = true
                )

                Button(
                    onClick = {
                        if (newTaskText.isNotBlank()) {
                            onAddChecklistItem?.invoke(newTaskText.trim())
                            newTaskText = ""
                        }
                    },
                    enabled = newTaskText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("btn_add_checklist_task")
                ) {
                    Text(
                        text = if (isTamil) "சேர்" else "Add",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            if (checklistItems.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (isTamil) "வேலைகள் ஏதும் இல்லை" else "No Tasks Added",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondaryDark
                        )
                        Text(
                            text = if (isTamil) "மேலே உள்ள படிவத்தைப் பயன்படுத்தி ஒரு வேலைைையச் சேர்க்கவும்." else "Use the form above to add a daily task.",
                            fontSize = 11.sp,
                            color = TextMutedDark
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    checklistItems.forEach { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleChecklistItem?.invoke(item) }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Checkbox(
                                    checked = item.isChecked,
                                    onCheckedChange = { onToggleChecklistItem?.invoke(item) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = DeepSageGreen,
                                        uncheckedColor = SageOutline,
                                        checkmarkColor = Color.White
                                    ),
                                    modifier = Modifier.testTag("checkbox_task_${item.id}")
                                )
                                Text(
                                    text = item.text,
                                    fontSize = 13.sp,
                                    fontWeight = if (item.isChecked) FontWeight.Normal else FontWeight.Medium,
                                    color = if (item.isChecked) TextMutedDark else TextPrimaryDark,
                                    style = androidx.compose.ui.text.TextStyle(
                                        textDecoration = if (item.isChecked) androidx.compose.ui.text.style.TextDecoration.LineThrough else null
                                    )
                                )
                            }

                            IconButton(
                                onClick = { onDeleteChecklistItem?.invoke(item) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("btn_delete_task_${item.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = if (isTamil) "நீக்கு" else "Delete",
                                    tint = AlertDueRed.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = SageOutline.copy(alpha = 0.25f))
                    }
                }
            }
        }
    }
}

@Composable
fun PartnerAccessControlDialog(
    isTamil: Boolean,
    partner: PartnerUiModel,
    lockedPages: Set<String>,
    onDismiss: () -> Unit,
    onToggleLock: (String, Boolean) -> Unit
) {
    val pageItems = listOf(
        Triple(
            AuthorizationManager.PAGE_HOME,
            if (isTamil) "முகப்பு பக்கம் (Home Dashboard)" else "Home Dashboard",
            if (isTamil) "சமீபத்திய வேலைகள் மற்றும் சுருக்கப் புள்ளிவிவரங்கள்" else "Recent jobs, balance, and quick summary"
        ),
        Triple(
            AuthorizationManager.PAGE_REPORT,
            if (isTamil) "அறிக்கைகள் மெனு (Reports)" else "Reports & Analytics",
            if (isTamil) "அனைத்து அறிக்கைகள் மற்றும் முதன்மை மெனு" else "Reports dashboard and analytics"
        ),
        Triple(
            AuthorizationManager.PAGE_CUSTOMER_CREDIT_DUE,
            if (isTamil) "வாடிக்கையாளர் நிலுவை (Customer Dues)" else "Customer Credit Due",
            if (isTamil) "கடன் விவரங்கள், கட்டணப் பதிவுகள் மற்றும் PDF அறிக்கை" else "Customer due balances, payments and statements"
        ),
        Triple(
            AuthorizationManager.PAGE_EXPENSES,
            if (isTamil) "செலவுகள் (Expenses)" else "Expenses Management",
            if (isTamil) "டீசல், பராமரிப்பு மற்றும் வணிக செலவுகள்" else "Diesel, tractor maintenance & expenses"
        ),
        Triple(
            AuthorizationManager.PAGE_BALANCE_SHEET,
            if (isTamil) "இருப்புநிலை அறிக்கை (Balance Sheet)" else "Balance Sheet",
            if (isTamil) "வருவாய், செலவு மற்றும் நிகர லாப அறிக்கை" else "Revenue, expense, and net profit ledger"
        ),
        Triple(
            AuthorizationManager.PAGE_WITHDRAWAL,
            if (isTamil) "பண எடுப்பு (Withdrawals)" else "Withdrawals & Payouts",
            if (isTamil) "கூட்டாளர் / உரிமையாளர் பண எடுப்பு பதிவுகள்" else "Partner & owner withdrawal entries"
        ),
        Triple(
            AuthorizationManager.PAGE_ACCOUNT,
            if (isTamil) "கணக்கு & அமைப்புகள் (Account)" else "Account & Settings",
            if (isTamil) "டிராக்டர் பட்டியல், மொழி மற்றும் அமைப்புகள்" else "Tractors, business info & configuration"
        )
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = DeepSageGreen,
                        modifier = Modifier.size(22.dp)
                    )
                    Text(
                        text = if (isTamil) "${partner.name} - அணுகல் கட்டுப்பாடு" else "${partner.name} - Access Control",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = ForestGreenHeader
                    )
                }
                Text(
                    text = if (isTamil)
                        "இந்த பங்குதாரருக்கான பக்க அனுமதிகளைத் தேர்வு செய்யவும். மற்ற பங்குதாரர்களை இது பாதிக்காது."
                    else
                        "Configure permissions specifically for ${partner.name}. Does not affect any other partner.",
                    fontSize = 11.5.sp,
                    color = TextSecondaryDark
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Fixed Always Allowed: New Job Entry
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = null,
                                tint = SuccessPaidGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = if (isTamil) "புதிய வேலைப் பதிவு (New Job Entry)" else "New Job Entry",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = TextPrimaryDark
                                )
                                Text(
                                    text = if (isTamil) "எப்போதும் அணுகலாம் (பூட்ட முடியாது)" else "Always accessible (Cannot be locked)",
                                    fontSize = 10.5.sp,
                                    color = SuccessPaidGreen
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFFE8F5E9),
                            border = BorderStroke(0.5.dp, Color(0xFF81C784))
                        ) {
                            Text(
                                text = if (isTamil) "எப்போதும் அனுமதி" else "ALWAYS ALLOWED",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF2E7D32),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                items(pageItems) { (pageKey, title, subtitle) ->
                    val isLocked = lockedPages.contains(pageKey)
                    val isAllowed = !isLocked

                    HorizontalDivider(color = SageOutline.copy(alpha = 0.25f))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = if (isAllowed) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null,
                                tint = if (isAllowed) SuccessPaidGreen else AlertDueRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isAllowed) TextPrimaryDark else TextSecondaryDark
                                )
                                Text(
                                    text = subtitle,
                                    fontSize = 10.5.sp,
                                    color = TextMutedDark
                                )
                            }
                        }

                        Switch(
                            checked = isAllowed,
                            onCheckedChange = { allowed ->
                                onToggleLock(pageKey, !allowed)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = DeepSageGreen,
                                uncheckedThumbColor = Color.White,
                                uncheckedTrackColor = Color.LightGray
                            ),
                            modifier = Modifier.testTag("switch_lock_${partner.id}_$pageKey")
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen)
            ) {
                Text(if (isTamil) "முடிந்தது" else "Done")
            }
        }
    )
}
@Composable
fun PartnerFormDialog(
    isTamil: Boolean,
    initialPartner: PartnerEntity?,
    isCollaborationOwner: Boolean = true,
    onDismiss: () -> Unit,
    onSave: (PartnerEntity) -> Unit
) {
    var name by remember { mutableStateOf(initialPartner?.name ?: "") }
    var phone by remember { mutableStateOf(initialPartner?.phone ?: "") }
    var selectedRole by remember {
        mutableStateOf(
            if (isCollaborationOwner) {
                com.example.data.auth.RoleUtils.normalizeRole(initialPartner?.role ?: "Partner")
            } else {
                com.example.data.auth.RoleUtils.ROLE_OPERATOR
            }
        )
    }
    var hasAttemptedSubmit by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val nameRequester = remember { BringIntoViewRequester() }
    val phoneRequester = remember { BringIntoViewRequester() }
    val isNameInvalid = name.isBlank()
    val isPhoneInvalid = phone.isBlank()

    val allowedRoles = remember(isCollaborationOwner) {
        if (isCollaborationOwner) {
            listOf(
                com.example.data.auth.RoleUtils.ROLE_CO_OWNER,
                com.example.data.auth.RoleUtils.ROLE_PARTNER,
                com.example.data.auth.RoleUtils.ROLE_OPERATOR
            )
        } else {
            listOf(
                com.example.data.auth.RoleUtils.ROLE_OPERATOR
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialPartner != null) (if (isTamil) "பங்குதாரர் திருத்து" else "Edit Partner") else (if (isTamil) "புதிய நபர் / பங்குதாரர்" else "Add Person / Partner"),
                fontWeight = FontWeight.Bold,
                color = ForestGreenHeader
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isTamil) "பெயர் *" else "Name *") },
                    singleLine = true,
                    isError = hasAttemptedSubmit && isNameInvalid,
                    supportingText = {
                        if (hasAttemptedSubmit && isNameInvalid) {
                            Text(
                                if (isTamil) "பெயர் தேவை *" else "Name is required *",
                                color = AlertDueRed,
                                fontSize = 10.sp
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().trackFocusedField(nameRequester, coroutineScope)
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text(if (isTamil) "தொலைபேசி எண் *" else "Phone Number *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    isError = hasAttemptedSubmit && isPhoneInvalid,
                    supportingText = {
                        if (hasAttemptedSubmit && isPhoneInvalid) {
                            Text(
                                if (isTamil) "தொலைபேசி எண் தேவை *" else "Phone number is required *",
                                color = AlertDueRed,
                                fontSize = 10.sp
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth().trackFocusedField(phoneRequester, coroutineScope)
                )

                Text(
                    text = if (isTamil) "பொறுப்பு / பணி *" else "Assigned Business Role *",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = ForestGreenHeader
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allowedRoles.forEach { roleOption ->
                        val isSelected = selectedRole == roleOption
                        val roleLabel = com.example.data.auth.RoleUtils.getRoleDisplayName(roleOption, isTamil)
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedRole = roleOption },
                            label = { Text(roleLabel, fontSize = 12.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = DeepSageGreen,
                                selectedLabelColor = Color.White
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    hasAttemptedSubmit = true
                    if (!isNameInvalid && !isPhoneInvalid) {
                        val partner = PartnerEntity(
                            id = initialPartner?.id ?: 0,
                            name = name.trim(),
                            phone = phone.trim(),
                            role = selectedRole,
                            photoUri = initialPartner?.photoUri ?: ""
                        )
                        onSave(partner)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen)
            ) {
                Text(if (initialPartner != null) (if (isTamil) "புதுப்பிக்க" else "Update") else (if (isTamil) "சேமி" else "Save Partner"))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isTamil) "ரத்து" else "Cancel")
            }
        }
    )
}
// ----------------------------------------------------
// Subpage 3: Business Settings
// ----------------------------------------------------
@Composable
fun BusinessSettingsPage(
    settings: AppSettingsEntity,
    onUpdateSettings: (AppSettingsEntity) -> Unit,
    isCollaborationOwner: Boolean = true,
    currentPersonalHourlyRate: Double? = null,
    onUpdatePersonalHourlyRate: ((Double) -> Unit)? = null
) {
    val context = LocalContext.current
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    var businessName by remember { mutableStateOf(settings.businessName) }
    var ownerName by remember { mutableStateOf(settings.ownerName) }
    var businessPhone by remember { mutableStateOf(settings.businessPhone) }
    var ratePerHour by remember { mutableStateOf((currentPersonalHourlyRate ?: settings.defaultHourlyRate).toInt().toString()) }
    var address by remember { mutableStateOf(settings.businessAddress) }
    var profilePhotoUri by remember { mutableStateOf(settings.profilePhotoUri) }
    var photoToCropUri by remember { mutableStateOf<Uri?>(null) }
    var isSaved by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val busNameReq = remember { BringIntoViewRequester() }
    val ownerNameReq = remember { BringIntoViewRequester() }
    val phoneReq = remember { BringIntoViewRequester() }
    val rateReq = remember { BringIntoViewRequester() }
    val addressReq = remember { BringIntoViewRequester() }
    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            photoToCropUri = uri
        }
    }
    if (photoToCropUri != null) {
        ImageCropDialog(
            imageUri = photoToCropUri!!,
            onDismiss = { photoToCropUri = null },
            onCropSaved = { croppedUri ->
                profilePhotoUri = croppedUri.toString()
                val updated = settings.copy(profilePhotoUri = croppedUri.toString())
                onUpdateSettings(updated)
                photoToCropUri = null
                isSaved = true
            }
        )
    }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!isCollaborationOwner) {
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8F5)),
                    border = BorderStroke(1.dp, DeepSageGreen.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = DeepSageGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isTamil) "வணிக அமைப்புகள் (பார்வை மட்டும்)" else "Business Information (Read-Only)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenHeader
                            )
                            Text(
                                text = if (isTamil)
                                    "வணிக விவரங்கள் உரிமையாளரால் மட்டுமே மாற்றப்பட முடியும்."
                                else
                                    "Business profile and firm information can only be edited by the workspace owner.",
                                fontSize = 11.sp,
                                color = TextSecondaryDark
                            )
                        }
                    }
                }
            }
        }
        // Business Profile Logo / Avatar Card with Crop & Upload Option
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = SageCardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.BottomEnd,
                        modifier = if (isCollaborationOwner) Modifier.clickable { photoPicker.launch("image/*") } else Modifier
                    ) {
                        PartnerAvatarImage(
                            photoUri = profilePhotoUri,
                            name = businessName.ifBlank { settings.activePartnerName },
                            size = 64.dp,
                            avatarColorHex = "#1E4D2B"
                        )
                        if (isCollaborationOwner) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(DeepSageGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddAPhoto,
                                    contentDescription = "Upload Photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isTamil) "வணிக சின்னம் / சுயவிவரப் படம்" else "Business Logo / Profile Photo",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreenHeader
                        )
                        Text(
                            text = if (isCollaborationOwner) {
                                if (isTamil) "படத்தை செதுக்கி (Crop) சேமிக்கலாம்" else "Upload, crop, zoom, and save photo"
                            } else {
                                if (isTamil) "உரிமையாளரால் நிர்வகிக்கப்படுகிறது" else "Managed by workspace owner"
                            },
                            fontSize = 11.sp,
                            color = SageAccent
                        )
                        if (isCollaborationOwner) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = { photoPicker.launch("image/*") },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("btn_upload_crop_profile_photo")
                                ) {
                                    Icon(Icons.Default.AddAPhoto, contentDescription = null, tint = DeepSageGreen, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isTamil) "படம் மாற்று & Crop" else "Change & Crop", fontSize = 11.sp, color = DeepSageGreen)
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline.copy(alpha = 0.6f))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = if (isTamil) "வணிக சுயவிவரம் & இன்வாய்ஸ் அமைப்புகள்" else "Business Profile & Invoice Settings",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenHeader
                    )
                    OutlinedTextField(
                        value = businessName,
                        onValueChange = {
                            if (isCollaborationOwner) {
                                businessName = it
                                isSaved = false
                            }
                        },
                        readOnly = !isCollaborationOwner,
                        enabled = isCollaborationOwner,
                        label = {
                            Text(
                                if (isCollaborationOwner) {
                                    if (isTamil) "நிறுவனம் / தொழில் பெயர்" else "Business / Firm Name"
                                } else {
                                    if (isTamil) "தொழில் பெயர் (பார்வை மட்டும்)" else "Business Name (Read-Only)"
                                }
                            )
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().trackFocusedField(busNameReq, coroutineScope)
                    )
                    OutlinedTextField(
                        value = ownerName,
                        onValueChange = {
                            if (isCollaborationOwner) {
                                ownerName = it
                                isSaved = false
                            }
                        },
                        readOnly = !isCollaborationOwner,
                        enabled = isCollaborationOwner,
                        label = { Text(if (isTamil) "உரிமையாளர் / கூட்டாண்மை பெயர்" else "Owner / Partnership Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().trackFocusedField(ownerNameReq, coroutineScope)
                    )
                    OutlinedTextField(
                        value = businessPhone,
                        onValueChange = {
                            if (isCollaborationOwner) {
                                businessPhone = it
                                isSaved = false
                            }
                        },
                        readOnly = !isCollaborationOwner,
                        enabled = isCollaborationOwner,
                        label = { Text(if (isTamil) "தொடர்பு தொலைபேசி எண்" else "Primary Contact Phone") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().trackFocusedField(phoneReq, coroutineScope)
                    )
                    OutlinedTextField(
                        value = ratePerHour,
                        onValueChange = {
                            ratePerHour = it
                            isSaved = false
                        },
                        readOnly = false,
                        enabled = true,
                        label = { Text(if (isTamil) "உங்கள் தனிப்பட்ட இயல்புநிலை கட்டணம் (${settings.currency}/Hr)" else "Your Personal Default Rate (${settings.currency}/Hr)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().trackFocusedField(rateReq, coroutineScope)
                            .testTag("input_personal_hourly_rate")
                    )
                    OutlinedTextField(
                        value = address,
                        onValueChange = {
                            if (isCollaborationOwner) {
                                address = it
                                isSaved = false
                            }
                        },
                        readOnly = !isCollaborationOwner,
                        enabled = isCollaborationOwner,
                        label = { Text(if (isTamil) "ஊர் / முகவரி" else "Location / Village / District") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().trackFocusedField(addressReq, coroutineScope)
                    )
                    if (isCollaborationOwner) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                val rateVal = ratePerHour.toDoubleOrNull() ?: 1100.0
                                val updated = settings.copy(
                                    businessName = businessName,
                                    ownerName = ownerName,
                                    businessPhone = businessPhone,
                                    defaultHourlyRate = rateVal,
                                    businessAddress = address,
                                    profilePhotoUri = profilePhotoUri
                                )
                                onUpdateSettings(updated)
                                onUpdatePersonalHourlyRate?.invoke(rateVal)
                                isSaved = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_save_business_settings"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen)
                        ) {
                            Text(if (isSaved) (if (isTamil) "✓ சேமிக்கப்பட்டது" else "✓ Settings Saved") else (if (isTamil) "சேமிக்கவும்" else "Save Changes"), fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = {
                                val rateVal = ratePerHour.toDoubleOrNull() ?: 1100.0
                                onUpdatePersonalHourlyRate?.invoke(rateVal)
                                isSaved = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("btn_save_personal_rate"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen)
                        ) {
                            Text(if (isSaved) (if (isTamil) "✓ கட்டணம் சேமிக்கப்பட்டது" else "✓ Personal Rate Saved") else (if (isTamil) "கட்டணத்தை சேமிக்கவும்" else "Save Personal Rate"), fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun SqliteSyncStatusPage(
    settings: AppSettingsEntity,
    isOnline: Boolean,
    isSyncing: Boolean,
    totalUnsyncedCount: Int,
    unsyncedJobsCount: Int,
    unsyncedExpensesCount: Int,
    unsyncedWithdrawalsCount: Int,
    unsyncedCustomersCount: Int,
    isSimulatedOffline: Boolean,
    onToggleSimulatedOffline: ((Boolean) -> Unit)?,
    onTriggerSync: () -> Unit
) {
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Status Hero Card
        item {
            val infiniteTransition = rememberInfiniteTransition(label = "sqlite_sync_anim")
            val spinAngle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "spin"
            )
            val pulseDotAlpha by infiniteTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(800, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulse"
            )
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (!isOnline) AlertDueRedBg.copy(alpha = 0.7f) else SageCardBg
                ),
                border = CardDefaults.outlinedCardBorder().copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (!isOnline) AlertDueRed.copy(alpha = 0.5f) else SageOutline
                    )
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = if (!isOnline) {
                                    Icons.Default.CloudOff
                                } else if (isSyncing) {
                                    Icons.Default.Sync
                                } else if (totalUnsyncedCount > 0) {
                                    Icons.Default.CloudUpload
                                } else {
                                    Icons.Default.CloudDone
                                },
                                contentDescription = null,
                                tint = if (!isOnline) AlertDueRed else DeepSageGreen,
                                modifier = Modifier
                                    .size(28.dp)
                                    .let { m -> if (isSyncing) m.rotate(spinAngle) else m }
                            )
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isSyncing -> DeepSageGreen.copy(alpha = pulseDotAlpha)
                                                    !isOnline -> AlertDueRed
                                                    totalUnsyncedCount > 0 -> DeepSageGreen.copy(alpha = pulseDotAlpha)
                                                    else -> SuccessPaidGreen
                                                }
                                            )
                                    )
                                    Text(
                                        text = if (!isOnline) {
                                            if (isTamil) "ஆஃப்லைன் பயன்முறை" else "Offline Storage Mode"
                                        } else if (isSyncing) {
                                            if (isTamil) "கிளவுடிற்கு ஒத்திசைக்கப்படுகிறது..." else "Syncing with Cloud..."
                                        } else if (totalUnsyncedCount > 0) {
                                            if (isTamil) "$totalUnsyncedCount பதிவுகள் கிளவுடிற்கு தயாராக உள்ளன" else "$totalUnsyncedCount Items Pending Upload"
                                        } else {
                                            if (isTamil) "அனைத்து பதிவுகளும் ஒத்திசைக்கப்பட்டது" else "All SQLite Records Synced"
                                        },
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (!isOnline) AlertDueRed else ForestGreenHeader
                                    )
                                }
                                Text(
                                    text = if (isTamil) "உள்ளூர் SQLite தரவுத்தளம் மற்றும் தானியங்கி கிளவுட் புஷ்" else "Local SQLite Database + Auto Cloud Push",
                                    fontSize = 12.sp,
                                    color = TextSecondaryDark
                                )
                            }
                        }
                    }
                    if (isSyncing) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp),
                            color = DeepSageGreen,
                            trackColor = SoftSageGreen
                        )
                    }
                    Text(
                        text = if (!isOnline) {
                            if (isTamil) "இணைய இணைப்பு இல்லாதபோது எல்லா பதிவுகளும் உங்கள் மொபைலில் உள்ள SQLite தரவுத்தளத்தில் பாதுகாப்பாக சேமிக்கப்படும். மீண்டும் இணையம் கிடைக்கும்போது தானாகவே கிளவுடிற்கு அனுப்பப்படும்." else "When offline, all jobs, expenses, withdrawals, and payments persist safely inside local Room SQLite. When network connectivity is restored, they automatically sync and push to the cloud."
                        } else {
                            if (isTamil) "இணைப்பு செயலில் உள்ளது. புதிய பதிவுகள் உடனடியாக உள்ளூர் SQLite-ல் சேமிக்கப்பட்டு கிளவுடிற்கு தானாகவே பதிவேற்றப்படும்." else "Network connected. New entries are persisted in local SQLite and pushed to Cloud in real-time."
                        },
                        fontSize = 12.sp,
                        color = TextPrimaryDark,
                        lineHeight = 17.sp
                    )
                    if (onToggleSimulatedOffline != null) {
                        Divider(color = SageOutline.copy(alpha = 0.5f))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (isTamil) "ஆஃப்லைன் சோதனை பயன்முறை" else "Simulate Offline Mode (Testing)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = ForestGreenHeader
                                )
                                Text(
                                    text = if (isTamil) "நெட்வொர்க் இல்லாமல் உள்ளூர் சேமிப்பை சோதிக்கவும்" else "Test creating records offline, then reconnecting",
                                    fontSize = 11.sp,
                                    color = TextMutedDark
                                )
                            }
                            Switch(
                                checked = isSimulatedOffline,
                                onCheckedChange = { isChecked ->
                                    onToggleSimulatedOffline(isChecked)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AlertDueRed,
                                    checkedTrackColor = AlertDueRedBg,
                                    uncheckedThumbColor = DeepSageGreen,
                                    uncheckedTrackColor = SoftSageGreen
                                )
                            )
                        }
                    }
                    Button(
                        onClick = onTriggerSync,
                        enabled = !isSyncing,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isSyncing) (if (isTamil) "ஒத்திசைக்கப்படுகிறது..." else "Pushing to Cloud...") else (if (isTamil) "இப்போது கிளவுடிற்கு ஒத்திசைக்கவும்" else "Push All Local Records Now"),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
        // SQLite Tables Breakdown
        item {
            Text(
                text = if (isTamil) "உள்ளூர் SQLite அட்டவணைகள்" else "Room SQLite Database Tables",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = ForestGreenHeader,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        item {
            SqliteTableItemCard(
                tableName = "job_entries",
                tableDesc = if (isTamil) "டிராக்டர் வேலை பதிவுகள்" else "Tractor Jobs & Land Work Logs",
                unsyncedCount = unsyncedJobsCount,
                isTamil = isTamil
            )
        }
        item {
            SqliteTableItemCard(
                tableName = "expenses",
                tableDesc = if (isTamil) "டீசல், சர்வீஸ் மற்றும் பராமரிப்பு செலவுகள்" else "Diesel, Maintenance & Driver Expenses",
                unsyncedCount = unsyncedExpensesCount,
                isTamil = isTamil
            )
        }
        item {
            SqliteTableItemCard(
                tableName = "withdrawals",
                tableDesc = if (isTamil) "பங்குதாரர் லாப எடுப்புகள்" else "Partner Profit Withdrawals",
                unsyncedCount = unsyncedWithdrawalsCount,
                isTamil = isTamil
            )
        }
        item {
            SqliteTableItemCard(
                tableName = "customers",
                tableDesc = if (isTamil) "வாடிக்கையாளர் கணக்கு மற்றும் கடன் நிலுவை" else "Customer Credit Dues & Payment Records",
                unsyncedCount = unsyncedCustomersCount,
                isTamil = isTamil
            )
        }
        item {
            SqliteTableItemCard(
                tableName = "app_settings",
                tableDesc = if (isTamil) "வணிக அமைப்புகள் மற்றும் பங்குதாரர் விவரங்கள்" else "Business Profile & Multi-Partner Sync Config",
                unsyncedCount = 0,
                isTamil = isTamil
            )
        }
    }
}
@Composable
fun SqliteTableItemCard(
    tableName: String,
    tableDesc: String,
    unsyncedCount: Int,
    isTamil: Boolean
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline.copy(alpha = 0.6f))),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SoftSageGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = null,
                        tint = DeepSageGreen,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column {
                    Text(
                        text = tableName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenHeader
                    )
                    Text(
                        text = tableDesc,
                        fontSize = 11.sp,
                        color = TextMutedDark
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (unsyncedCount > 0) AlertDueRedBg else SuccessPaidGreenBg
            ) {
                Text(
                    text = if (unsyncedCount > 0) "$unsyncedCount ${if (isTamil) "நிலுவை" else "Pending"}" else (if (isTamil) "✓ ஒத்திசைவு" else "✓ Synced"),
                    fontSize = 11.sp,
                    color = if (unsyncedCount > 0) AlertDueRed else SuccessPaidGreen,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}
