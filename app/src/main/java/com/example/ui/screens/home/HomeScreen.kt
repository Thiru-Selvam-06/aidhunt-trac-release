package com.example.ui.screens.home

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Money
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    recentJobs: List<JobEntryEntity>,
    recordedJobCount: Int = recentJobs.size,
    recordedCustomerCount: Int = 0,
    isOnline: Boolean = true,
    isSyncing: Boolean = false,
    unsyncedCount: Int = 0,
    syncMessage: String = "",
    retainedOperationWarning: String = "",
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
            // 1. Redesigned Business / Profile Card (Matching navigation bar color, white text, white avatar shape)
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF072D18)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_business_profile_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(if (responsive.isSmallPhone) 12.dp else 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (responsive.isSmallPhone) 12.dp else 16.dp)
                    ) {
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
                                    ?: currentAuthUser?.displayName?.takeIf { it.isNotBlank() }
                                    ?: (if (isTamil) "பங்குதாரர்" else "Partner")
                            }
                        }

                        com.example.ui.components.PartnerAvatarImage(
                            photoUri = settings.profilePhotoUri,
                            name = if (settings.businessName.isNotBlank()) settings.businessName else loggedInName,
                            size = if (responsive.isSmallPhone) 50.dp else if (responsive.isLargePhone) 64.dp else 56.dp,
                            fallbackBgColor = Color.White,
                            fallbackTextColor = Color(0xFF072D18),
                            modifier = Modifier.testTag("home_partner_avatar")
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = settings.businessName.ifBlank { "AIDHUNT Agri & Tractor Services" },
                                fontSize = if (responsive.isSmallPhone) 17.sp else if (responsive.isLargePhone) 21.sp else 18.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "$loggedInRole: $loggedInName",
                                fontSize = if (responsive.isSmallPhone) 12.5.sp else 13.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.88f),
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
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

            if (retainedOperationWarning.isNotBlank()) item {
                Card(colors = CardDefaults.cardColors(containerColor = AlertDueRedBg), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(retainedOperationWarning, color = AlertDueRed, fontSize = 12.sp, modifier = Modifier.padding(12.dp))
                }
            }

            // Immutable rejected snapshots stay visible without contaminating confirmed balances.
            items(retainedActions.filter { it.state in setOf("PENDING", "REJECTED") }, key = { "retained-${it.operationId}" }) { action ->
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = AlertDueRedBg)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("${action.entityType}: ${com.example.ui.util.RetainedActionDetails.status(action)}", fontWeight = FontWeight.Bold)
                        Text(com.example.ui.util.RetainedActionDetails.summary(action), fontSize = 12.sp)
                        Text("Original local action; awaiting server confirmation or review. Details in Account → SQLite & Cloud Sync.", fontSize = 12.sp)
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
                                text = if (isTamil) "கையில் ரொக்கம்" else "Cash in hand",
                                fontSize = if (responsive.isSmallPhone) 10.5.sp else 11.sp,
                                color = AppTheme.colors.textMuted,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = formatInr(availableBalance, settings.currency),
                                fontSize = if (responsive.isSmallPhone) 16.sp else if (responsive.isLargePhone) 20.sp else 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (availableBalance < 0) AlertDueRed else DeepSageGreen,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
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

            // 4. Recent Job Entries Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isTamil) "சமீபத்திய வேலைப் பதிவுகள்" else "Today’s Job Entries",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary
                    )

                    Text(
                        text = if (isTamil) "${displayJobs.size} பதிவுகள்" else "${displayJobs.size} Entries",
                        fontSize = 12.sp,
                        color = SageAccent,
                        fontWeight = FontWeight.Medium
                    )
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
                    JobEntryItemCard(
                        job = job,
                        settings = settings,
                        workspaceMembers = workspaceMembers,
                        partners = partners,
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

@Composable
fun JobEntryItemCard(
    job: JobEntryEntity,
    settings: AppSettingsEntity,
    workspaceMembers: List<com.example.data.firebase.WorkspaceMember> = emptyList(),
    partners: List<com.example.data.entity.PartnerEntity> = emptyList(),
    onClick: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onSharePdf: () -> Unit
) {
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    val isPaid = (job.amountReceived >= job.totalAmount && job.pendingAmount <= 0.0) || job.pendingAmount <= 0.0
    val archived = workspaceMembers.any { it.workspaceId == job.workspaceId && it.uid in setOf(job.createdByUid, job.entryForUid, job.financialAttributionUid) && it.status.equals("removed", true) }
    val isHistorical = isJobHistorical(job)
    val isOldAndDue = isHistorical && !isPaid
    val avatarColor = getCustomerAvatarColor(job.customerName)
    val initials = getCustomerInitials(job.customerName)

    // Status priority: PAID -> Green, OLD + DUE -> Dark Red + Yellow, DUE -> Red
    val (cardBg, cardBorder) = when {
        isPaid -> Pair(Color(0xFFF2FBF5), Color(0xFFCBEAD7))                  // PAID -> GREEN
        isOldAndDue -> Pair(Color(0xFFFFF7F5), Color(0xFFF59E0B))             // OLD + DUE -> DARK RED + YELLOW
        else -> Pair(Color(0xFFFFF5F5), Color(0xFFFFD4D4))                     // DUE -> RED
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = if (archived) Color(0xFFE9E9E9) else cardBg),
        border = BorderStroke(1.dp, cardBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("job_item_${job.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left Column: Avatar + Customer Details (Name, Work Type, Date & Duration)
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // 1. Customer Initials / Avatar Circle
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(avatarColor.bg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = avatarColor.text
                    )
                }

                // 2. Details: Name, Machinery/Work Type, Date & Duration
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    // Customer Name
                    Text(
                        text = job.customerName.ifBlank { if (isTamil) "வாடிக்கையாளர்" else "Customer" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Machinery / Work Type
                    val machineryText = if (job.workType.isNotBlank()) {
                        com.example.ui.util.Localization.getWorkTypeDisplayName(job.workType, isTamil)
                    } else if (job.tractorLabel.isNotBlank()) {
                        job.tractorLabel
                    } else {
                        if (isTamil) "வேலை" else "Work"
                    }
                    Text(
                        text = machineryText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF15803D),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    // Date & Duration Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Date with Calendar Icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(13.5.dp)
                            )
                            Text(
                                text = com.example.ui.components.formatDate(job.startTimeMillis.takeIf { it > 0 } ?: job.createdAt),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF4B5563)
                            )
                        }

                        // Duration with Clock Icon
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = Color(0xFF6B7280),
                                modifier = Modifier.size(13.5.dp)
                            )
                            Text(
                                text = com.example.ui.util.WorkBillingCalculator.formatDuration(job.durationMinutes),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF4B5563)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Right Column: Amount on top, Status Badge & Chevron right underneath
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Amount on the right
                val displayAmount = if (job.totalAmount > 0.0) job.totalAmount else job.amountReceived
                Text(
                    text = formatInr(displayAmount, settings.currency),
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827),
                    maxLines = 1
                )

                // Paid/Due Status Badge & Right-facing Chevron
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    when {
                        isPaid -> {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFDCFCE7)
                            ) {
                                Text(
                                    text = if (isTamil) "செலுத்தப்பட்டது" else "Paid",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF15803D)
                                )
                            }
                        }
                        isOldAndDue -> {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF7F1D1D),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFCD34D))
                                    )
                                    Text(
                                        text = if (isTamil) "பாக்கி" else "Due",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFEF08A)
                                    )
                                }
                            }
                        }
                        else -> {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFFFE2E2)
                            ) {
                                Text(
                                    text = if (isTamil) "பாக்கி" else "Due",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFDC2626)
                                )
                            }
                        }
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "View Details",
                        tint = Color(0xFF6B7280),
                        modifier = Modifier.size(18.dp)
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
                    val (cRole, cName) = com.example.data.auth.RoleUtils.resolveCreatorRoleAndName(job, settings.ownerName, workspaceMembers, partners)
                    val createdByText = com.example.data.auth.RecordIdentityPolicy.savedCreatorLabel(
                        com.example.data.auth.RoleUtils.formatRoleAndName(cRole, cName, isTamil), job.createdByUid, isTamil)
                    val entryForDisplay = com.example.data.auth.RoleUtils.getEntryForDisplay(job, settings.ownerName, workspaceMembers, partners, isTamil,
                        com.example.ui.util.rememberBusinessPartnership(settings.workspaceId, workspaceMembers))
                    val editedByDisplayModal = com.example.data.auth.RoleUtils.getEditedByDisplay(job, isTamil)

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
                                        text = if (isTamil) "உருவாக்கியவர்" else "Created By",
                                        fontSize = 11.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                    Text(
                                        text = createdByText,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF111827),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (entryForDisplay != null) {
                                Divider(color = Color(0xFFE5E7EB), thickness = 1.dp, modifier = Modifier.padding(horizontal = 12.dp))
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (isTamil) "யாருக்காக" else "Entry For",
                                        fontSize = 11.sp,
                                        color = Color(0xFF6B7280)
                                    )
                                    Text(
                                        text = entryForDisplay.removePrefix(if (isTamil) "யாருக்காக: " else "Entry For: "),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF111827),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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
                                                        val roleName = when {
                                                            m.role.equals("owner", ignoreCase = true) -> if (isTamil) "உரிமையாளர்" else "Owner"
                                                            m.role.equals("partner", ignoreCase = true) -> if (isTamil) "பங்குதாரர்" else "Partner"
                                                            m.role.equals("operator", ignoreCase = true) -> if (isTamil) "இயக்குநர்" else "Operator"
                                                            else -> m.role
                                                        }
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
                                text = formatDate(job.startTimeMillis),
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
