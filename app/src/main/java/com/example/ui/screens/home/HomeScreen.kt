package com.example.ui.screens.home

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.auth.RoleUtils
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.pdf.PdfGeneratorHelper
import com.example.ui.components.FlatPdfIconButton
import com.example.ui.components.FlatShareIconButton
import com.example.ui.components.MetricCard
import com.example.ui.components.StatusBadge
import com.example.ui.components.buildJobWhatsAppMessage
import com.example.ui.components.formatDate
import com.example.ui.components.formatDateTime
import com.example.ui.components.formatInr
import com.example.ui.components.openDialer
import com.example.ui.components.openWhatsApp
import com.example.ui.components.sendWhatsAppMessage
import com.example.ui.components.shareGenericText
import com.example.ui.theme.AppTheme
import com.example.ui.theme.AlertDueRed
import com.example.ui.theme.AlertDueRedBg
import com.example.ui.theme.CreamBackground
import com.example.ui.theme.DeepSageGreen
import com.example.ui.theme.ForestGreenHeader
import com.example.ui.theme.SageAccent
import com.example.ui.theme.SageCardBg
import com.example.ui.theme.SageOutline
import com.example.ui.theme.SoftSageGreen
import com.example.ui.theme.SuccessPaidGreen
import com.example.ui.theme.SuccessPaidGreenBg
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextSecondaryDark
import com.example.ui.util.JobAuditPresentation
import com.example.ui.util.WorkspaceBannerStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    settings: AppSettingsEntity,
    totalRecorded: Double = 0.0,
    totalReceived: Double = 0.0,
    totalPending: Double = 0.0,
    totalCustomerDue: Double = 0.0,
    totalExpenses: Double = 0.0,
    availableBalance: Double = 0.0,
    cashBalanceVerifying: Boolean = false,
    displayedBalance: Double? = null,
    balanceDisplayControls: @Composable () -> Unit = {},
    recentJobs: List<JobEntryEntity>,
    recordedJobCount: Int = recentJobs.size,
    recordedCustomerCount: Int = 0,
    isOnline: Boolean = true,
    isSyncing: Boolean = false,
    unsyncedCount: Int = 0,
    syncMessage: String = "",
    retainedOperationWarning: String = "",
    onOpenSyncDetails: (() -> Unit)? = null,
    retainedActions: List<com.example.data.entity.PendingOperationEntity> = emptyList(),
    isOwner: Boolean = true,
    currentUid: String = "",
    workspaceMembers: List<com.example.data.firebase.WorkspaceMember> = emptyList(),
    partners: List<com.example.data.entity.PartnerEntity> = emptyList(),
    authenticatedUserName: String = "",
    authenticatedUserRole: String = "",
    expenses: List<ExpenseEntity> = emptyList(),
    onTriggerSync: () -> Unit = {},
    onFabClick: () -> Unit = {},
    onEditJob: ((JobEntryEntity) -> Unit)? = null,
    onDeleteJob: (JobEntryEntity) -> Unit
) {
    val context = LocalContext.current
    var selectedJob by remember { mutableStateOf<JobEntryEntity?>(null) }
    var jobToDelete by remember { mutableStateOf<JobEntryEntity?>(null) }
    val isTamil = settings.language.equals("TA", ignoreCase = true)

    // ---- Workspace Banner (local-only, never uploaded) ----
    val workspaceId = settings.workspaceId
    var bannerFile by remember(workspaceId) {
        mutableStateOf<File?>(WorkspaceBannerStorage.loadBanner(context, workspaceId))
    }
    var showBannerSheet by remember { mutableStateOf(false) }
    val bannerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val bannerScope = rememberCoroutineScope()

    val pickBannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            bannerScope.launch {
                val saved = withContext(Dispatchers.IO) {
                    WorkspaceBannerStorage.saveBanner(context, workspaceId, uri)
                }
                if (saved != null) {
                    bannerFile = saved
                } else {
                    Toast.makeText(
                        context,
                        if (isTamil) "பேனர் புகைப்படம் சேமிக்கவில்லை" else "Banner update failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
    // -------------------------------------------------------

    var pendingPdfJob by remember { mutableStateOf<JobEntryEntity?>(null) }
    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { _ ->
        pendingPdfJob?.let { j ->
            val file = PdfGeneratorHelper.generateJobReceiptPdf(
                context = context,
                settings = settings,
                job = j
            )
            file?.let {
                val displayName = "Receipt_${j.customerName}_#${j.id}"
                PdfGeneratorHelper.downloadPdfToDownloads(context, it, displayName, isTamil)
            }
            pendingPdfJob = null
        }
    }

    fun requirePdfAccess(job: JobEntryEntity) {
        val role = com.example.data.auth.RoleUtils.normalizeRole(authenticatedUserRole)
        require(role in setOf(com.example.data.auth.RoleUtils.ROLE_OWNER, com.example.data.auth.RoleUtils.ROLE_PARTNER) && currentUid.isNotBlank() && job.workspaceId == settings.workspaceId) { "PDF export requires authorized workspace access" }
    }
    val triggerJobPdfDownload: (JobEntryEntity) -> Unit = { j ->
        requirePdfAccess(j)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            pendingPdfJob = j
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            val file = PdfGeneratorHelper.generateJobReceiptPdf(
                context = context,
                settings = settings,
                job = j
            )
            file?.let {
                val displayName = "Receipt_${j.customerName}_#${j.id}"
                PdfGeneratorHelper.downloadPdfToDownloads(context, it, displayName, isTamil)
            }
        }
    }

    val responsive = com.example.ui.theme.rememberResponsiveDimensions()

    val displayJobs = remember(recentJobs) {
        recentJobs.filter { !it.isHistorical && com.example.ui.util.isRecentJobToday(it) }
            .sortedWith(com.example.ui.util.RecentJobOrder)
         .take(20)
    }

    // Track job IDs to animate newly created entries during the current session
    var knownJobIds by remember { mutableStateOf<Set<Long>?>(null) }
    var newlyCreatedJobIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var animatedJobIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    androidx.compose.runtime.LaunchedEffect(displayJobs) {
        val currentIds = displayJobs.map { it.id }.toSet()
        val prev = knownJobIds
        if (prev == null) {
            val now = System.currentTimeMillis()
            val freshJobs = displayJobs.filter { (now - it.createdAt) in 0..15_000L }.map { it.id }.toSet()
            if (freshJobs.isNotEmpty()) {
                newlyCreatedJobIds = newlyCreatedJobIds + freshJobs
            }
            knownJobIds = currentIds
        } else {
            val added = currentIds - prev
            if (added.isNotEmpty()) {
                newlyCreatedJobIds = newlyCreatedJobIds + added
            }
            knownJobIds = currentIds
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = responsive.screenPaddingHorizontal,
                end = responsive.screenPaddingHorizontal,
                top = responsive.screenPaddingVertical,
                bottom = 96.dp
            ),
            verticalArrangement = Arrangement.spacedBy(if (responsive.isSmallPhone) 10.dp else 14.dp)
        ) {
            // 1. Redesigned Workspace Banner Card (Design C)
            item {
                val displayName = settings.businessName.ifBlank { "AIDHUNT Agri & Tractor Services" }
                val currentAuthUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val currentMember = workspaceMembers.find {
                    (currentUid.isNotBlank() && it.uid == currentUid) ||
                    (currentAuthUser?.uid != null && it.uid == currentAuthUser.uid) ||
                    (currentAuthUser?.phoneNumber != null && it.phoneNumber == currentAuthUser.phoneNumber)
                }
                val loggedInRole = when {
                    isOwner -> com.example.ui.util.businessRoleDisplayName("Owner", settings.workspaceId, workspaceMembers, isTamil)
                    authenticatedUserRole.equals("operator", ignoreCase = true) || currentMember?.role.equals("operator", ignoreCase = true) -> if (isTamil) "இயக்குநர்" else "Operator"
                    else -> if (isTamil) "பங்குதாரர்" else "Partner"
                }
                val loggedInName = if (isOwner) {
                    authenticatedUserName.ifBlank { settings.ownerName.ifBlank { settings.activePartnerName.ifBlank { "Owner" } } }
                } else {
                    authenticatedUserName.ifBlank {
                        currentMember?.displayName?.takeIf { it.isNotBlank() }
                            ?: settings.activePartnerName.takeIf { it.isNotBlank() && !it.equals(settings.ownerName, ignoreCase = true) }
                    }
                }
                val initial = displayName.trim().take(1).uppercase(Locale.getDefault())
                    .ifBlank { "A" }

                Card(
                    shape = RoundedCornerShape(18.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_business_profile_card")
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(if (responsive.isSmallPhone) 130.dp else 150.dp)
                    ) {
                        // Background: custom banner image OR gradient fallback
                        if (bannerFile != null) {
                            AsyncImage(
                                model = bannerFile,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(18.dp))
                            )
                            // Gradient scrim for text readability
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xCC072D18),
                                                Color(0x44072D18),
                                                Color(0x00072D18)
                                            )
                                        )
                                    )
                            )
                        } else {
                            // Default bundled tractor landscape banner (Design C)
                            Image(
                                painter = painterResource(id = com.example.R.drawable.default_workspace_banner),
                                contentDescription = "Default Tractor Banner",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(18.dp))
                            )
                            // Subtle dark gradient scrim behind workspace text for legibility
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(
                                                Color(0xCC072D18),
                                                Color(0x44072D18),
                                                Color(0x00072D18)
                                            )
                                        )
                                    )
                            )
                        }

                        // Workspace avatar + name + owner overlay (bottom-left)
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(
                                    start = if (responsive.isSmallPhone) 12.dp else 16.dp,
                                    bottom = if (responsive.isSmallPhone) 12.dp else 14.dp,
                                    end = 48.dp
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(
                                if (responsive.isSmallPhone) 10.dp else 12.dp
                            )
                        ) {
                            // Circular workspace avatar
                            com.example.ui.components.PartnerAvatarImage(
                                photoUri = settings.profilePhotoUri,
                                name = displayName,
                                size = if (responsive.isSmallPhone) 48.dp else 54.dp,
                                fallbackBgColor = Color.White,
                                fallbackTextColor = Color(0xFF072D18),
                                modifier = Modifier.testTag("home_partner_avatar")
                            )
                            Column {
                                Text(
                                    text = displayName,
                                    fontSize = if (responsive.isSmallPhone) 17.sp else 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "$loggedInRole: $loggedInName",
                                    fontSize = if (responsive.isSmallPhone) 12.sp else 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.88f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Camera/edit icon button (top-right corner)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(
                                    top = if (responsive.isSmallPhone) 10.dp else 12.dp,
                                    end = if (responsive.isSmallPhone) 10.dp else 12.dp
                                )
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.90f))
                                .clickable { showBannerSheet = true }
                                .testTag("home_banner_edit_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = if (isTamil) "பேனரை மாற்று" else "Change banner",
                                tint = Color(0xFF072D18),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 2. Financial Metrics Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
                    horizontalArrangement = Arrangement.spacedBy(if (responsive.isSmallPhone) 8.dp else 12.dp)
                ) {
                    val effectiveTotalRecorded = totalRecorded
                    val effectiveTotalDue = totalCustomerDue

                    MetricCard(
                        title = if (isTamil) "மொத்த பதிவு" else "Total Recorded",
                        amount = effectiveTotalRecorded,
                        subtitle = if (isTamil) "$recordedCustomerCount வாடிக்கையாளர்கள் · $recordedJobCount வேலைப் பதிவுகள்" else "$recordedCustomerCount Customers · $recordedJobCount Job entries (all time)",
                        icon = Icons.Default.CheckCircle,
                        containerColor = SuccessPaidGreenBg,
                        contentColor = SuccessPaidGreen,
                        titleColor = Color(0xFF14532D),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )

                    MetricCard(
                        title = if (isTamil) "மொத்த பாக்கி" else "Total Due",
                        amount = effectiveTotalDue,
                        secondaryLabel = if (isTamil) "வாடிக்கையாளர்களிடமிருந்து" else "From Customers",
                        secondaryAmount = effectiveTotalDue,
                        subtitle = if (isTamil) "நிலுவைத் தொகை" else "Pending Dues",
                        icon = Icons.Default.PendingActions,
                        containerColor = AlertDueRedBg,
                        contentColor = AlertDueRed,
                        titleColor = Color(0xFF960000),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        isNegative = true
                    )
                }
            }

            val hasReview = retainedOperationWarning.isNotBlank() || retainedActions.any { it.state == "REJECTED" }
            val hasPending = retainedActions.any { it.state == "PENDING" }
            if (hasReview || hasPending) item {
                val summary = if (hasReview) {
                    if (isTamil) "ஒத்திசைவு பதிவுகளுக்கு ஆய்வு தேவை" else "Sync items need review"
                } else {
                    if (isTamil) "ஒத்திசைவுக்காக காத்திருக்கும் பதிவுகள்" else "Records waiting to sync"
                }
                Card(colors = CardDefaults.cardColors(containerColor = if (hasReview) AlertDueRedBg else AppTheme.colors.cardBg),
                    shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(summary, modifier = Modifier.weight(1f).padding(vertical = 10.dp), fontSize = 12.sp,
                            color = if (hasReview) AlertDueRed else AppTheme.colors.textPrimary)
                        if (onOpenSyncDetails != null) TextButton(onClick = onOpenSyncDetails) {
                            Text(if (isTamil) "விவரங்கள்" else "Details")
                        }
                    }
                }
            }

            // Business cash requires the complete authorized financial dataset.
            if (com.example.data.auth.AuthorizationManager.canViewExpenses(isOwner, role = authenticatedUserRole)) item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.cardBorder.copy(alpha = 0.7f))),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (responsive.isSmallPhone) 12.dp else 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = if (isTamil) "கிடைக்கக்கூடிய வணிக இருப்பு" else "Available Business Balance",
                                fontSize = if (responsive.isSmallPhone) 12.sp else 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.textPrimary,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (displayedBalance != null) (if (isTamil) "திருத்தப்பட்ட காட்சி மட்டும்" else "Edited display only") else (if (isTamil) "கையில் ரொக்கம்" else "Cash in hand"),
                                fontSize = if (responsive.isSmallPhone) 10.5.sp else 11.sp,
                                color = AppTheme.colors.textMuted,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (cashBalanceVerifying) (if (isTamil) "சரிபார்க்கிறது…" else "Verifying…") else formatInr(displayedBalance ?: availableBalance, settings.currency),
                                fontSize = if (responsive.isSmallPhone) 16.sp else if (responsive.isLargePhone) 20.sp else 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (availableBalance < 0) AlertDueRed else DeepSageGreen,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            balanceDisplayControls()
                            if (availableBalance < 0) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isTamil) "ரொக்கப் பற்றாக்குறை" else "Cash deficit",
                                    fontSize = if (responsive.isSmallPhone) 10.sp else 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = AlertDueRed,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 4. Today's Job Entries Header Row — matches approved reference
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EventAvailable,
                            contentDescription = null,
                            tint = Color(0xFF15803D),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isTamil) "இன்றைய வேலைப் பதிவுகள்" else "Today's Job Entries",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFDCFCE7),
                        modifier = Modifier.testTag("home_today_entries_count_badge")
                    ) {
                        Text(
                            text = if (isTamil) "${displayJobs.size} பதிவுகள்" else "${displayJobs.size} Entries",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // 5. Job List
            if (displayJobs.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Agriculture,
                                contentDescription = null,
                                tint = SageAccent,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (isTamil) "வேலைப் பதிவுகள் எதுவும் இல்லை" else "No job entries today",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AppTheme.colors.textPrimary
                            )
                            Text(
                                text = if (isTamil) "உங்கள் முதல் வேலையை பதிவு செய்ய '+ புதிய வேலை' என்பதைத் தொடவும்" else "Jobs recorded today appear here",
                                fontSize = 12.sp,
                                color = AppTheme.colors.textSecondary
                            )
                        }
                    }
                }
            } else {
                items(displayJobs, key = { it.id }) { job ->
                    val isNewlyCreated = (job.id in newlyCreatedJobIds || (System.currentTimeMillis() - job.createdAt in 0..15_000L)) && job.id !in animatedJobIds
                    JobEntryItemCard(
                        job = job,
                        settings = settings,
                        workspaceMembers = workspaceMembers,
                        partners = partners,
                        isNewlyCreated = isNewlyCreated,
                        onAnimationComplete = { completedId ->
                            animatedJobIds = animatedJobIds + completedId
                            newlyCreatedJobIds = newlyCreatedJobIds - completedId
                        },
                        onClick = { selectedJob = job },
                        onShareWhatsApp = {
                            val msg = buildJobWhatsAppMessage(job, settings.businessName, isTamil)
                            sendWhatsAppMessage(context, job.customerPhone, msg)
                        },
                        onSharePdf = {
                            val pdfFile = PdfGeneratorHelper.generateJobReceiptPdf(context, settings, job)
                            if (pdfFile != null) {
                                PdfGeneratorHelper.sharePdf(context, pdfFile, "Tractor Job Work Slip - ${job.customerName} (${job.workType})")
                            } else {
                                Toast.makeText(context, "Could not generate PDF receipt", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }

        // Restored Floating Action Button for New Job at Bottom-Right
        FloatingActionButton(
            onClick = onFabClick,
            containerColor = DeepSageGreen,
            contentColor = Color.White,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
                .testTag("home_fab_new_job")
                .height(if (responsive.isSmallPhone) 48.dp else 54.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = if (responsive.isSmallPhone) 12.dp else 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Job",
                    tint = Color.White,
                    modifier = Modifier.size(if (responsive.isSmallPhone) 20.dp else 22.dp)
                )
                Text(
                    text = if (isTamil) "புதிய வேலை" else "New Job",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = if (responsive.isSmallPhone) 13.sp else 14.sp
                )
            }
        }
    }

    // ---- Change Workspace Banner Bottom Sheet ----
    if (showBannerSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBannerSheet = false },
            sheetState = bannerSheetState,
            containerColor = Color.White,
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isTamil) "பணியிட பேனரை மாற்று" else "Change Workspace Banner",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color(0xFF1A1A1A)
                    )
                    IconButton(
                        onClick = { showBannerSheet = false },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = if (isTamil) "மூடு" else "Close",
                            tint = Color(0xFF555555)
                        )
                    }
                }
                Divider(color = Color(0xFFEEEEEE))

                // Option: Choose from Gallery (always shown)
                BannerSheetOption(
                    icon = Icons.Default.Image,
                    iconBg = Color(0xFFE8F5E9),
                    iconTint = Color(0xFF2E7D32),
                    title = if (isTamil) "கேலரியிலிருந்து தேர்ந்தெடு" else "Choose from Gallery",
                    subtitle = if (isTamil) "உங்கள் தொலைபேசியிலிருந்து படம் தேர்ந்தெடுக்கவும்" else "Select an image from your phone",
                    onClick = {
                        showBannerSheet = false
                        pickBannerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                )

                // Option: Change Banner (only shown when a custom banner exists)
                if (bannerFile != null) {
                    BannerSheetOption(
                        icon = Icons.Default.Edit,
                        iconBg = Color(0xFFE8F5E9),
                        iconTint = Color(0xFF2E7D32),
                        title = if (isTamil) "பேனரை மாற்று" else "Change Banner",
                        subtitle = if (isTamil) "புதிய படத்துடன் மாற்றவும்" else "Replace with a new image",
                        onClick = {
                            showBannerSheet = false
                            pickBannerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    BannerSheetOption(
                        icon = Icons.Default.Delete,
                        iconBg = Color(0xFFFCE8E8),
                        iconTint = Color(0xFFD32F2F),
                        title = if (isTamil) "பேனரை நீக்கு" else "Remove Banner",
                        subtitle = if (isTamil) "இயல்புநிலை பச்சை பேனர் மீட்டமை" else "Restore default green banner",
                        titleColor = Color(0xFFD32F2F),
                        onClick = {
                            showBannerSheet = false
                            bannerScope.launch(Dispatchers.IO) {
                                WorkspaceBannerStorage.removeBanner(context, workspaceId)
                                withContext(Dispatchers.Main) {
                                    bannerFile = null
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // Customer Summary Popup Dialog
    selectedJob?.let { selected -> recentJobs.find { it.workspaceId == selected.workspaceId && it.id == selected.id } }?.let { job ->
        CustomerSummaryPopup(
            job = job,
            settings = settings,
            isOwner = isOwner,
            currentUid = currentUid,
            workspaceMembers = workspaceMembers,
            partners = partners,
            expenses = expenses,
            onDismiss = { selectedJob = null },
            onCallCustomer = {
                if (job.customerPhone.isNotBlank()) {
                    openDialer(context, job.customerPhone)
                } else {
                    Toast.makeText(context, if (isTamil) "${job.customerName}-க்கான தொலைபேசி எண் இல்லை" else "No phone number available for ${job.customerName}", Toast.LENGTH_SHORT).show()
                }
            },
            onShareWhatsApp = {
                val msg = buildJobWhatsAppMessage(job, settings.businessName, isTamil)
                sendWhatsAppMessage(context, job.customerPhone, msg)
            },
            onSharePdf = {
                requirePdfAccess(job)
                val file = PdfGeneratorHelper.generateJobReceiptPdf(
                    context = context,
                    settings = settings,
                    job = job
                )
                file?.let {
                    val subject = if (isTamil) "வேலை ரசீது - ${job.customerName}" else "Work Receipt - ${job.customerName}"
                    PdfGeneratorHelper.sharePdf(
                        context = context,
                        file = it,
                        subject = subject
                    )
                }
            },
            onDownloadPdf = {
                triggerJobPdfDownload(job)
            },
            onDeleteRequest = {
                jobToDelete = job
                selectedJob = null
            },
            onEditJob = onEditJob
        )
    }

    // Confirm Delete Dialog
    jobToDelete?.let { job ->
        AlertDialog(
            onDismissRequest = { jobToDelete = null },
            title = {
                Text(
                    text = if (isTamil) "வேலைப் பதிவை நீக்கவா?" else "Delete Job Record?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isTamil) {
                        "வாடிக்கையாளர் '${job.customerName}'-க்கான வேலைப் பதிவை (${formatInr(job.totalAmount, settings.currency)}) நிச்சயமாக நீக்க விரும்புகிறீர்களா?\n\nஇணைக்கப்பட்ட செலவுகளும் அனுமதி இருந்தால் சேர்த்து நீக்கப்படும். செயலில் உள்ள வசூல் இருந்தால் முதலில் அதைத் திரும்பப் பெறவும்."
                    } else {
                        "Delete the Entry for '${job.customerName}' (${formatInr(job.totalAmount, settings.currency)}) and its eligible linked Expenses? Active Collections must be reversed first. The customer's due updates after confirmation."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteJob(job)
                        jobToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertDueRed),
                    modifier = Modifier.testTag("btn_confirm_delete_job")
                ) {
                    Text(if (isTamil) "நீக்கு" else "Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { jobToDelete = null },
                    modifier = Modifier.testTag("btn_cancel_delete_job")
                ) {
                    Text(if (isTamil) "ரத்து செய்" else "Cancel")
                }
            }
        )
    }
}

private fun getCustomerInitials(name: String): String {
    val clean = name.trim()
    if (clean.isBlank()) return "C"
    val parts = clean.split("\\s+".toRegex()).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "C"
        parts.size == 1 -> parts[0].take(2).uppercase(java.util.Locale.getDefault())
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase(java.util.Locale.getDefault())
    }
}

private data class CustomerAvatarColor(val bg: Color, val text: Color)

private fun getCustomerAvatarColor(name: String): CustomerAvatarColor {
    val palette = listOf(
        CustomerAvatarColor(bg = Color(0xFFDCFCE7), text = Color(0xFF166534)), // Light Mint Green
        CustomerAvatarColor(bg = Color(0xFFE0F2FE), text = Color(0xFF0369A1)), // Light Sky Blue
        CustomerAvatarColor(bg = Color(0xFFFEF3C7), text = Color(0xFF92400E)), // Light Amber/Yellow
        CustomerAvatarColor(bg = Color(0xFFFFEDD5), text = Color(0xFFC2410C)), // Light Warm Orange
        CustomerAvatarColor(bg = Color(0xFFE0E7FF), text = Color(0xFF3730A3)), // Light Indigo
        CustomerAvatarColor(bg = Color(0xFFFCE7F3), text = Color(0xFF9D174D))  // Light Rose
    )
    val index = kotlin.math.abs(name.hashCode()) % palette.size
    return palette[index]
}

private fun isJobHistorical(job: JobEntryEntity): Boolean {
    // Explicit old/historical entry indicator according to existing entry logic
    val isExplicitOld = job.isHistorical


    return isExplicitOld
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun JobEntryItemCard(
    job: JobEntryEntity,
    settings: AppSettingsEntity,
    workspaceMembers: List<com.example.data.firebase.WorkspaceMember> = emptyList(),
    partners: List<com.example.data.entity.PartnerEntity> = emptyList(),
    isNewlyCreated: Boolean = false,
    onAnimationComplete: (Long) -> Unit = {},
    onClick: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onSharePdf: () -> Unit
) {
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    val isPaid = (job.amountReceived >= job.totalAmount && job.pendingAmount <= 0.0) || job.pendingAmount <= 0.0
    val archived = workspaceMembers.any { it.workspaceId == job.workspaceId && it.uid in setOf(job.createdByUid, job.entryForUid, job.financialAttributionUid) && it.status.equals("removed", true) }
    val isHistorical = isJobHistorical(job)
    val isOldAndDue = isHistorical && !isPaid
    val initials = getCustomerInitials(job.customerName)

    // Visual Semantics matching approved Design C / Today's Job Entry:
    // DUE -> Soft rose/pink card (#FFF5F6), rose border (#FECDD3), red indicator bar (#F43F5E)
    // PAID -> Soft mint-green card (#F0FDF4), mint border (#BBF7D0), green indicator bar (#16A34A)
    // OLD + DUE -> Soft amber card (#FFFBEB), amber border (#FDE68A), amber indicator bar (#F59E0B)
    val (cardBg, cardBorder, accentColor) = when {
        archived -> Triple(Color(0xFFF1F5F9), Color(0xFFCBD5E1), Color(0xFF94A3B8))
        isPaid -> Triple(Color(0xFFF0FDF4), Color(0xFFBBF7D0), Color(0xFF16A34A))
        isOldAndDue -> Triple(Color(0xFFFFFBEB), Color(0xFFFDE68A), Color(0xFFF59E0B))
        else -> Triple(Color(0xFFFFF5F6), Color(0xFFFECDD3), Color(0xFFF43F5E))
    }

    // New Job Entry Color Transition Animation:
    // When newly created, starts with the strong red highlight (#FF1456)
    // and smoothly transitions over exactly 1000 ms to the standard pastel card background (cardBg)
    val highlightRed = Color(0xFFFF1456)
    val animatedBg = remember(job.id, isNewlyCreated) {
        Animatable(if (isNewlyCreated) highlightRed else cardBg)
    }

    androidx.compose.runtime.LaunchedEffect(job.id, isNewlyCreated) {
        if (isNewlyCreated) {
            animatedBg.animateTo(
                targetValue = cardBg,
                animationSpec = tween(
                    durationMillis = 1000,
                    easing = FastOutSlowInEasing
                )
            )
            onAnimationComplete(job.id)
        }
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (isNewlyCreated) animatedBg.value else cardBg,
        border = BorderStroke(1.2.dp, cardBorder),
        shadowElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("job_item_${job.id}")
    ) {
        val workType = when {
            job.workType.isNotBlank() -> com.example.ui.util.Localization.getWorkTypeDisplayName(job.workType, isTamil)
            job.tractorLabel.isNotBlank() -> job.tractorLabel
            else -> if (isTamil) "வேலை" else "Work"
        }
        val displayAmount = if (job.totalAmount > 0.0) job.totalAmount else job.amountReceived
        val workDate = JobAuditPresentation.date(job.startTimeMillis, isTamil)
        val recordedDate = JobAuditPresentation.date(job.createdAt, isTamil)
        val recordedTime = JobAuditPresentation.recordedTime(job.createdAt, isTamil)
        val creator = RoleUtils.getCreatedByDisplay(job, workspaceMembers, partners, isTamil)
        val operator = RoleUtils.getEntryForDisplay(job, workspaceMembers, partners, isTamil)
        val differentOperator = operator != null && (
            job.entryForUid != job.createdByUid ||
                RoleUtils.getEntryForName(job, workspaceMembers, partners) !=
                RoleUtils.getCreatedByName(job, workspaceMembers, partners, isTamil)
            )

        Box(
            modifier = Modifier.fillMaxWidth()
        ) {
            // Left vertical accent indicator bar
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .matchParentSize()
                    .background(accentColor)
            )

            // Card content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            ) {
                // 1. Customer Identity Row: Initials Avatar + Customer Name + Amount Badge + Chevron
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar & Customer Name
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFDCFCE7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = job.customerName.ifBlank { if (isTamil) "வாடிக்கையாளர்" else "Customer" },
                            modifier = Modifier.weight(1f),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Amount Badge + Right Chevron
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFDCFCE7)
                        ) {
                            Text(
                                text = formatInr(displayAmount, settings.currency),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F172A),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = if (isTamil) "விவரங்களைப் பார்க்கவும்" else "View details",
                            tint = Color(0xFF475569),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Work Type & Payment Status Badges Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Work type badge (Tractor icon + Work Type name)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFDCFCE7)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Agriculture,
                                contentDescription = null,
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = workType,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF15803D)
                            )
                        }
                    }

                    // Payment status badge
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = when {
                            isPaid -> Color(0xFFDCFCE7)
                            isOldAndDue -> Color(0xFFFEF3C7)
                            else -> Color(0xFFFFE4E6)
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    isPaid -> Icons.Default.CheckCircle
                                    else -> Icons.Default.WarningAmber
                                },
                                contentDescription = null,
                                tint = when {
                                    isPaid -> Color(0xFF15803D)
                                    isOldAndDue -> Color(0xFFB45309)
                                    else -> Color(0xFF991B1B)
                                },
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = when {
                                    isPaid -> if (isTamil) "செலுத்தப்பட்டது" else "Paid"
                                    else -> if (isTamil) "பாக்கி" else "Due"
                                },
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    isPaid -> Color(0xFF15803D)
                                    isOldAndDue -> Color(0xFFB45309)
                                    else -> Color(0xFF991B1B)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Subtle Horizontal Divider
                HorizontalDivider(
                    color = Color(0xFFE2E8F0).copy(alpha = 0.8f),
                    thickness = 1.dp
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Metadata Row with Distinct Icons and Separators
                val showSeparateRecording = job.isHistorical || workDate != recordedDate
                val dateTimeText = if (!showSeparateRecording && recordedTime.isNotEmpty()) "$workDate · $recordedTime" else workDate

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Date & Time
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarToday,
                            contentDescription = null,
                            tint = Color(0xFF15803D),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = dateTimeText,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF0F172A)
                        )
                    }

                    Text("|", color = Color(0xFFCBD5E1), fontSize = 13.sp)

                    // Duration
                    val duration = com.example.ui.util.WorkBillingCalculator.formatDuration(job.durationMinutes)
                    if (duration.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.HourglassEmpty,
                                contentDescription = null,
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = duration,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF0F172A)
                            )
                        }

                        Text("|", color = Color(0xFFCBD5E1), fontSize = 13.sp)
                    }

                    // Entry Owner / Creator
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PersonOutline,
                            contentDescription = null,
                            tint = Color(0xFF15803D),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = creator.ifBlank { operator.orEmpty().ifBlank { "—" } },
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }

                // Operator attribution if different from creator
                if (differentOperator && !operator.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = operator,
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B)
                    )
                }

                // Separate recording date if historical or different
                if (showSeparateRecording && recordedDate.isNotEmpty()) {
                    val recordedLabel = if (isTamil) "பதிவு செய்த நேரம்" else "Recorded"
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (recordedTime.isNotEmpty()) "$recordedLabel: $recordedDate · $recordedTime" else "$recordedLabel: $recordedDate",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }
        }
    }
}

@Composable
fun CustomerSummaryPopup(
    job: JobEntryEntity,
    settings: AppSettingsEntity,
    isOwner: Boolean = true,
    currentUid: String = "",
    workspaceMembers: List<com.example.data.firebase.WorkspaceMember> = emptyList(),
    partners: List<com.example.data.entity.PartnerEntity> = emptyList(),
    expenses: List<ExpenseEntity> = emptyList(),
    onDismiss: () -> Unit,
    onCallCustomer: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onSharePdf: () -> Unit,
    onDownloadPdf: () -> Unit,
    onDeleteRequest: () -> Unit,
    onEditJob: ((JobEntryEntity) -> Unit)? = null
) {
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    val currentActorName = if (isOwner) {
        settings.ownerName.ifBlank { settings.activePartnerName }
    } else {
        settings.activePartnerName
    }
    val sourceReadOnly = workspaceMembers.any { it.workspaceId == job.workspaceId && it.uid in setOf(job.createdByUid, job.entryForUid, job.financialAttributionUid) && it.status.equals("removed", true) }
    val currentMember = workspaceMembers.find { it.uid == currentUid }
    val currentRole = if (isOwner) com.example.data.auth.RoleUtils.ROLE_OWNER else (currentMember?.role?.takeIf { it.isNotBlank() }?.let { com.example.data.auth.RoleUtils.normalizeRole(it) } ?: "")
    val canEdit = !sourceReadOnly && com.example.data.auth.AuthorizationManager.canEditEntry(
        job = job,
        isOwner = isOwner,
        currentActorName = currentActorName,
        currentUid = currentUid,
        role = currentRole
    )
    val canDelete = (isOwner || !sourceReadOnly) && com.example.data.auth.AuthorizationManager.canDeleteEntry(
        job = job,
        isOwner = isOwner,
        currentActorName = currentActorName,
        currentUid = currentUid,
        role = currentRole
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (responsive.isSmallPhone) 16.dp else 20.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color.White,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(if (responsive.isSmallPhone) 14.dp else 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // 1. HEADER: Avatar, Name & Phone, Edit (Pencil) & Close (X)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            val initial = job.customerName.trim().take(1).uppercase(Locale.getDefault()).ifBlank { "C" }
                            Surface(
                                shape = CircleShape,
                                color = Color(0xFFE8F5E9),
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = initial,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessPaidGreen
                                    )
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = job.customerName.ifBlank { "—" },
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (job.customerPhone.isNotBlank()) job.customerPhone else (if (isTamil) "தொலைபேசி எண் இல்லை" else "No phone number"),
                                    fontSize = 12.sp,
                                    color = Color(0xFF6B7280),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (sourceReadOnly) {
                                    Text(
                                        text = if (isTamil) "முன்னாள் உறுப்பினர் பதிவு" else "Retained Historical Record",
                                        fontSize = 11.sp,
                                        color = Color(0xFFB45309),
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            if (canEdit) {
                                IconButton(
                                    onClick = {
                                        onDismiss()
                                        onEditJob?.invoke(job)
                                    },
                                    modifier = Modifier
                                        .size(32.dp)
                                        .testTag("modal_header_edit_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = if (isTamil) "திருத்து" else "Edit Job",
                                        tint = Color(0xFF374151),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("modal_header_close_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = if (isTamil) "மூடு" else "Close",
                                    tint = Color(0xFF6B7280),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // 2. CALL CUSTOMER: Compact Action Row
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF9FAFB),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                enabled = job.customerPhone.isNotBlank(),
                                onClick = onCallCustomer
                            )
                            .testTag("btn_call_customer_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Call,
                                contentDescription = null,
                                tint = SuccessPaidGreen,
                                modifier = Modifier.size(16.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isTamil) "வாடிக்கையாளரை அழைக்கவும்" else "Call Customer",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF111827)
                                )
                                Text(
                                    text = if (job.customerPhone.isNotBlank()) (if (isTamil) "அழைக்க தொடவும்" else "Tap to call using phone number") else (if (isTamil) "தொலைபேசி எண் இல்லை" else "Phone number unavailable"),
                                    fontSize = 11.sp,
                                    color = Color(0xFF6B7280)
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = Color(0xFF9CA3AF),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // 3. FINANCIAL INFORMATION: Compact 2x2 Grid Surface
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF9FAFB),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("modal_financial_summary_card")
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Row 1: Total Amount | Total Time
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isTamil) "மொத்த தொகை" else "Total Amount",
                                        fontSize = 11.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                    val modalDisplayAmount = if (job.totalAmount > 0.0) job.totalAmount else job.amountReceived
                                    Text(
                                        text = formatInr(modalDisplayAmount, settings.currency),
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF111827)
                                    )
                                }

                                val durationStr = remember(job.durationMinutes) {
                                    com.example.ui.util.WorkBillingCalculator.formatDuration(job.durationMinutes)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isTamil) "மொத்த நேரம்" else "Total Time",
                                        fontSize = 11.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                    Text(
                                        text = durationStr,
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF111827)
                                    )
                                }
                            }

                            Divider(color = Color(0xFFE5E7EB), thickness = 1.dp)

                            // Row 2: Paid | Balance Due
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isTamil) "செலுத்தியது" else "Paid",
                                        fontSize = 11.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                    Text(
                                        text = formatInr(job.amountReceived, settings.currency),
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = SuccessPaidGreen
                                    )
                                }

                                val balanceDue = job.pendingAmount
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isTamil) "நிலுவை பாக்கி" else "Balance Due",
                                        fontSize = 11.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                    val isOld = isJobHistorical(job)
                                    Text(
                                        text = formatInr(balanceDue, settings.currency),
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when {
                                            balanceDue <= 0 -> SuccessPaidGreen
                                            isOld -> Color(0xFF991B1B)
                                            else -> AlertDueRed
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 4. TRACTOR / OPERATOR: Compact Surface
                    val hasPartnership = com.example.ui.util.rememberBusinessPartnership(settings.workspaceId, workspaceMembers)
                    val createdByText = RoleUtils.getCreatedByName(job, workspaceMembers, partners, isTamil)
                    val entryForName = RoleUtils.getEntryForName(job, workspaceMembers, partners)
                    val editedByDisplayModal = com.example.data.auth.RoleUtils.getEditedByDisplay(job, isTamil, hasPartnership)

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFFF9FAFB),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("modal_tractor_operator_card")
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isTamil) "டிராக்டர்" else "Tractor",
                                        fontSize = 11.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                    Text(
                                        text = job.tractorLabel.ifBlank { "—" },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF111827),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isTamil) "உருவாக்கியவர்" else "Entry by",
                                        fontSize = 11.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                    Text(
                                        text = createdByText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF111827)
                                    )
                                }
                            }
                            if (entryForName != null) {
                                Divider(color = Color(0xFFE5E7EB), thickness = 1.dp, modifier = Modifier.padding(horizontal = 12.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isTamil) "வேலை இயக்குபவர்" else "Entry for Operator",
                                        modifier = Modifier.weight(1f),
                                        fontSize = 11.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                    Text(
                                        text = entryForName,
                                        modifier = Modifier.weight(1f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF111827)
                                    )
                                }
                            }
                            if (editedByDisplayModal != null) {
                                Divider(color = Color(0xFFE5E7EB), thickness = 1.dp, modifier = Modifier.padding(horizontal = 12.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isTamil) "திருத்தியவர்" else "Edited By",
                                        fontSize = 11.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                    Text(
                                        text = editedByDisplayModal.removePrefix(if (isTamil) "திருத்தியவர்: " else "Edited By: "),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF111827),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // 4B. EXPENSES SECTION (Associated with this Job Entry)
                    val linkedJobExpenses = remember(job.workspaceId, job.id, expenses) { expenses.filter { it.workspaceId == job.workspaceId && it.relatedJobId == job.id } }
                    if (linkedJobExpenses.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFF9FAFB),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE5E7EB)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("modal_expenses_card")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isTamil) "செலவுகள்" else "Expenses",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF4B5563)
                                    )
                                    val totalExp = linkedJobExpenses.sumOf { it.amount }
                                    Text(
                                        text = formatInr(totalExp, settings.currency),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AlertDueRed
                                    )
                                }

                                linkedJobExpenses.forEachIndexed { expIdx, exp ->
                                    if (expIdx > 0) {
                                        Divider(color = Color(0xFFE5E7EB), thickness = 0.5.dp)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            val descText = if (exp.description.isNotBlank() && !exp.description.startsWith("Expense for", ignoreCase = true)) {
                                                "${exp.expenseType} (${exp.description})"
                                            } else {
                                                exp.expenseType
                                            }
                                            Text(
                                                text = descText,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF111827),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            val payerDisplay = if (exp.paidBy == "Business Paid") {
                                                if (isTamil) "செலுத்தியவர்: வணிகம்" else "Paid By: Business"
                                            } else {
                                                val resolvedName = if (exp.paidByUid.isNotBlank()) {
                                                    val m = workspaceMembers.find { it.uid == exp.paidByUid }
                                                    if (m != null) {
                                                        val hasPartnership = com.example.ui.util.hasBusinessPartnershipHistory(settings.workspaceId, workspaceMembers)
                                                        val roleName = com.example.data.auth.RoleUtils.getRoleDisplayName(
                                                            com.example.ui.util.businessDisplayRole(m.role, hasPartnership),
                                                            isTamil
                                                        )
                                                        "${m.displayName ?: m.phoneNumber ?: "Member"} ($roleName)"
                                                    } else {
                                                        exp.paidByPartner.ifBlank { exp.operatorName.ifBlank { exp.addedByPartner } }
                                                    }
                                                } else {
                                                    val raw = exp.paidByPartner.ifBlank { exp.operatorName.ifBlank { exp.addedByPartner } }
                                                    if (raw.equals(settings.businessName, ignoreCase = true)) {
                                                        settings.ownerName.ifBlank { if (isTamil) "உரிமையாளர்" else "Owner" }
                                                    } else {
                                                        raw
                                                    }
                                                }
                                                val prefix = if (isTamil) "செலுத்தியவர்: " else "Paid By: "
                                                "$prefix$resolvedName"
                                            }
                                            Text(
                                                text = payerDisplay,
                                                fontSize = 10.5.sp,
                                                color = Color(0xFF6B7280),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(
                                            text = formatInr(exp.amount, settings.currency),
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF111827)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Text((if (isTamil) "பதிவு செய்த நேரம்: " else "Recorded in TRAC: ") +
                        com.example.ui.util.JobAuditPresentation.instant(job.createdAt, isTamil), fontSize = 12.sp)
                    if (job.editedByUid.isNotBlank()) Text((if (isTamil) "கடைசியாக திருத்திய நேரம்: " else "Last edited: ") +
                        com.example.ui.util.JobAuditPresentation.instant(job.updatedAt, isTamil), fontSize = 12.sp)
                    val history = runCatching { com.example.data.sync.JobEditAudit.read(job.jobEditHistoryJson) }.getOrDefault(emptyList())
                    if (history.isNotEmpty()) {
                        var showHistory by remember(job.id) { mutableStateOf(false) }
                        TextButton(onClick = { showHistory = !showHistory }) { Text(if (isTamil) "திருத்த வரலாறு" else "Edit history") }
                        if (showHistory) history.asReversed().forEach { event ->
                            val editor = workspaceMembers.firstOrNull { it.uid == event["actorUid"] }?.displayName ?: (if (isTamil) "உறுப்பினர் விவரம் இல்லை" else "Member unavailable")
                            val before = event["before"] as? Map<*, *> ?: emptyMap<Any, Any>()
                            val after = event["after"] as? Map<*, *> ?: emptyMap<Any, Any>()
                            Text(editor + " · " + com.example.ui.util.JobAuditPresentation.instant((event["actionTime"] as? Number)?.toLong() ?: 0L, isTamil), fontSize = 12.sp)
                            before.keys.filter { it !in setOf("customerId", "tractorId") && before[it].toString() != after[it].toString() }.forEach { key ->
                                Text(com.example.ui.util.JobAuditPresentation.field(key.toString(), isTamil) + ": " + com.example.ui.util.JobAuditPresentation.value(key.toString(), before[key], job.isHistorical, isTamil) + " → " + com.example.ui.util.JobAuditPresentation.value(key.toString(), after[key], job.isHistorical, isTamil), fontSize = 11.sp)
                            }
                        }
                    }
                    // 5. SIMPLE METADATA ROWS (Date, Work Type, Notes)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Date Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isTamil) "தேதி" else "Date",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                            Text(
                                text = if (job.isHistorical) {
                                    com.example.ui.util.JobAuditPresentation.date(job.startTimeMillis, isTamil)
                                } else {
                                    com.example.ui.util.JobAuditPresentation.instant(job.startTimeMillis, isTamil)
                                },
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF111827)
                            )
                        }

                        // Work Type Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isTamil) "பணி வகை" else "Work Type",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                            Text(
                                text = if (job.workType.isNotBlank()) com.example.ui.util.Localization.getWorkTypeDisplayName(job.workType, isTamil) else "—",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF111827)
                            )
                        }

                        // Notes Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isTamil) "குறிப்புகள்" else "Notes",
                                fontSize = 12.sp,
                                color = Color(0xFF6B7280)
                            )
                            Text(
                                text = job.notes.ifBlank { "—" },
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF374151),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // 6. SHARE BUTTONS ROW (WhatsApp, Share PDF, Save PDF)
                    if (currentRole in setOf(com.example.data.auth.RoleUtils.ROLE_OWNER, com.example.data.auth.RoleUtils.ROLE_PARTNER)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onShareWhatsApp,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessPaidGreen),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("btn_modal_share_whatsapp"),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "WhatsApp",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onSharePdf,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AlertDueRed.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("btn_modal_share_pdf"),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = AlertDueRed,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isTamil) "PDF பகிர்க" else "Share PDF",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AlertDueRed,
                                    maxLines = 1
                                )
                            }
                        }

                        OutlinedButton(
                            onClick = onDownloadPdf,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DeepSageGreen.copy(alpha = 0.6f)),
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .testTag("btn_modal_save_pdf"),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FileDownload,
                                    contentDescription = null,
                                    tint = DeepSageGreen,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = if (isTamil) "PDF சேமி" else "Save PDF",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DeepSageGreen,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    }
                    // 7. DELETE BUTTON (Authorized only)
                    if (canDelete) {
                        OutlinedButton(
                            onClick = onDeleteRequest,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertDueRed),
                            border = androidx.compose.foundation.BorderStroke(1.dp, AlertDueRed.copy(alpha = 0.5f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                                .testTag("btn_modal_delete_job"),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = null,
                                tint = AlertDueRed,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isTamil) "வேலைப் பதிவை நீக்கு" else "Delete Job Entry",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AlertDueRed
                            )
                        }
                    }
                }
            }
        }
    }
}


// ---------------------------------------------------------------------------
// Banner bottom-sheet row option
// ---------------------------------------------------------------------------
@Composable
private fun BannerSheetOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconBg: Color,
    iconTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    titleColor: Color = Color(0xFF1A1A1A)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = titleColor
            )
            Text(
                text = subtitle,
                fontSize = 12.sp,
                color = Color(0xFF777777)
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFFAAAAAA),
            modifier = Modifier.size(20.dp)
        )
    }
}
