package com.example.ui.screens.report

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.view.WindowManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.firebase.WorkspaceMember
import com.example.data.firebase.ProfitShareAllocation
import com.example.pdf.PdfGeneratorHelper
import com.example.ui.components.*
import com.example.ui.components.DatePreset
import com.example.ui.theme.*
import com.example.ui.util.FinancialCalculationEngine
import com.example.ui.util.PartnerShareInvestmentStatus
import java.util.Calendar
import java.util.Locale

val WithdrawalCategories = listOf(
    "Withdrawal",
    "Investment"
)

data class ProfitShareParticipant(
    val uid: String,
    val displayName: String,
    val role: String,
    val isOwner: Boolean
)

fun getEligibleProfitShareParticipants(
    workspaceMembers: List<WorkspaceMember>,
    partners: List<PartnerEntity>,
    settings: AppSettingsEntity,
    isTamil: Boolean
): List<ProfitShareParticipant> {
    val list = mutableListOf<ProfitShareParticipant>()
    val seenUids = mutableSetOf<String>()
    val seenNames = mutableSetOf<String>()
    val bizName = settings.businessName.trim()

    // 1. Authoritative source: workspaceMembers
    workspaceMembers.forEach { member ->
        if (member.status.isBlank() || member.status.equals("active", ignoreCase = true)) {
            val normalizedRole = com.example.data.auth.RoleUtils.normalizeRole(member.role)
            if (normalizedRole == com.example.data.auth.RoleUtils.ROLE_OWNER ||
                normalizedRole == com.example.data.auth.RoleUtils.ROLE_CO_OWNER ||
                normalizedRole == com.example.data.auth.RoleUtils.ROLE_PARTNER
            ) {
                val isOwnerRole = normalizedRole == com.example.data.auth.RoleUtils.ROLE_OWNER || normalizedRole == com.example.data.auth.RoleUtils.ROLE_CO_OWNER
                val memberName = member.displayName?.trim().orEmpty()
                val resolvedName = when {
                    memberName.isNotBlank() && !memberName.equals(bizName, ignoreCase = true) -> memberName
                    isOwnerRole && settings.ownerName.trim().isNotBlank() && !settings.ownerName.trim().equals(bizName, ignoreCase = true) -> settings.ownerName.trim()
                    member.phoneNumber?.isNotBlank() == true -> member.phoneNumber!!
                    isOwnerRole -> "Owner"
                    else -> "Partner"
                }

                if (!resolvedName.equals(bizName, ignoreCase = true)) {
                    val uid = if (member.uid.isNotBlank()) member.uid else "member_${member.phoneNumber ?: resolvedName}"
                    if (!seenUids.contains(uid)) {
                        seenUids.add(uid)
                        seenNames.add(resolvedName.lowercase())
                        list.add(
                            ProfitShareParticipant(
                                uid = uid,
                                displayName = resolvedName,
                                role = if (isOwnerRole) (if (isTamil) "உரிமையாளர்" else "Owner") else (if (isTamil) "பங்குதாரர்" else "Partner"),
                                isOwner = isOwnerRole
                            )
                        )
                    }
                }
            }
        }
    }

    // 2. Fallback if Owner is not in workspaceMembers
    val hasOwner = list.any { it.isOwner }
    if (!hasOwner) {
        val ownerName = settings.ownerName.trim().ifBlank { "Owner" }
        if (!ownerName.equals(bizName, ignoreCase = true)) {
            val ownerPhoneDigits = settings.businessPhone.filter { it.isDigit() }
            val ownerUid = if (ownerPhoneDigits.isNotBlank()) "owner_$ownerPhoneDigits" else "owner_primary"
            if (!seenUids.contains(ownerUid)) {
                seenUids.add(ownerUid)
                seenNames.add(ownerName.lowercase())
                list.add(0, ProfitShareParticipant(
                    uid = ownerUid,
                    displayName = ownerName,
                    role = if (isTamil) "உரிமையாளர்" else "Owner",
                    isOwner = true
                ))
            }
        }
    }

    // 3. Fallback: local partners not in workspaceMembers (excluding operators and business name)
    partners.forEach { partner ->
        val pName = partner.name.trim()
        val isOperator = partner.role.equals("Operator", ignoreCase = true)
        val matchesBizName = pName.equals(bizName, ignoreCase = true)
        if (pName.isNotBlank() && !isOperator && !matchesBizName && !seenNames.contains(pName.lowercase())) {
            val pUid = "partner_${partner.id}"
            if (!seenUids.contains(pUid)) {
                seenUids.add(pUid)
                seenNames.add(pName.lowercase())
                list.add(
                    ProfitShareParticipant(
                        uid = pUid,
                        displayName = pName,
                        role = partner.role.ifBlank { if (isTamil) "பங்குதாரர்" else "Partner" },
                        isOwner = false
                    )
                )
            }
        }
    }

    return list.sortedByDescending { it.isOwner }
}

data class ProfitShareDisplayItem(
    val uid: String,
    val displayName: String,
    val percentage: Int,
    val isActive: Boolean
)

fun getLocalizedWithdrawalCategory(category: String, isTamil: Boolean): String {
    if (!isTamil) return category
    return when (category.trim().lowercase()) {
        "withdrawal", "withdrawal (share)", "profit share" -> "பங்கு எடுப்பு"
        "investment" -> "முதலீடு திரும்பப் பெறுதல்"
        "share & investment", "share & investment (settlement)" -> "பங்கு & முதலீடு"
        "personal use / advance", "personal use", "advance" -> "தனிப்பட்ட பயன்பாடு / முன்பணம்"
        "salary" -> "சம்பளம்"
        "fuel advance" -> "எரிபொருள் முன்பணம்"
        "emergency" -> "அவசர தேவை"
        "maintenance advance" -> "பராமரிப்பு முன்பணம்"
        "other" -> "இதர"
        else -> category
    }
}

val PartnerDistributionColors = listOf(
    Color(0xFF0F9D58), // Green (Muniyappan)
    Color(0xFF2563EB), // Blue (Perumal)
    Color(0xFFF97316), // Orange (Sriram)
    Color(0xFF8B5CF6), // Purple
    Color(0xFFEC4899), // Pink
    Color(0xFF06B6D4)  // Teal
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WithdrawalTab(
    settings: AppSettingsEntity,
    withdrawals: List<WithdrawalEntity>,
    partners: List<PartnerEntity>,
    availableAmount: Double,
    totalWithdrawn: Double,
    onAddWithdrawal: (WithdrawalEntity) -> Unit,
    onDeleteWithdrawal: (WithdrawalEntity) -> Unit,
    expenses: List<ExpenseEntity> = emptyList(),
    jobs: List<JobEntryEntity> = emptyList(),
    workspaceMembers: List<WorkspaceMember> = emptyList(),
    isOwner: Boolean = false,
    currentUserName: String = "",
    partnerPercentages: Map<String, Int> = emptyMap(),
    onUpdatePartnerPercentages: (Map<String, Int>) -> Unit = {},
    profitShareAllocations: Map<String, Int> = emptyMap(),
    profitShareAllocationDetails: Map<String, com.example.data.firebase.ProfitShareAllocation> = emptyMap(),
    onUpdateProfitShareAllocations: (Map<String, Int>, Map<String, com.example.data.firebase.ProfitShareAllocation>) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    var takeAmountTargetStatus by remember { mutableStateOf<PartnerShareInvestmentStatus?>(null) }
    var showTakeAmountDialog by remember { mutableStateOf(false) }
    var showPdfOptionsDialog by remember { mutableStateOf(false) }
    var selectedPartnerFilter by remember { mutableStateOf("All") }
    var draftPartnerFilter by remember { mutableStateOf("All") }
    var selectedDatePreset by remember { mutableStateOf(DatePreset.ALL_TIME) }
    var draftDatePreset by remember { mutableStateOf(DatePreset.ALL_TIME) }
    var customDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var draftCustomDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var isFilterExpanded by remember { mutableStateOf(false) }
    var withdrawalToDelete by remember { mutableStateOf<WithdrawalEntity?>(null) }
    var showFullWithdrawalHistory by remember { mutableStateOf(false) }

    BackHandler(enabled = showFullWithdrawalHistory) {
        showFullWithdrawalHistory = false
    }

    val filteredWithdrawals = withdrawals.filter { w ->
        val matchesPartner = selectedPartnerFilter == "All" || w.partnerName.contains(selectedPartnerFilter, ignoreCase = true)
        val ts = if (w.timestamp > 0) w.timestamp else w.createdAt
        val matchesDate = when (selectedDatePreset) {
            DatePreset.ALL_TIME -> true
            DatePreset.TODAY -> isDateInPreset(ts, DatePreset.TODAY)
            DatePreset.THIS_MONTH -> isDateInPreset(ts, DatePreset.THIS_MONTH)
            DatePreset.CUSTOM -> {
                val calW = Calendar.getInstance().apply { timeInMillis = ts }
                val calCustom = Calendar.getInstance().apply { timeInMillis = customDateMillis }
                calW.get(Calendar.YEAR) == calCustom.get(Calendar.YEAR) &&
                calW.get(Calendar.DAY_OF_YEAR) == calCustom.get(Calendar.DAY_OF_YEAR)
            }
            else -> isDateInPreset(ts, selectedDatePreset)
        }
        matchesPartner && matchesDate
    }
    val currentFilteredTotal = filteredWithdrawals.sumOf { it.amount }

    // Canonical participant list for withdrawals (strictly exclude business name)
    val allWorkspacePersons = remember(partners, settings.activePartnerName, settings.ownerName, withdrawals, settings.businessName) {
        val personMap = linkedMapOf<String, String>()
        val bizName = settings.businessName.trim()
        val currentActor = settings.activePartnerName.trim()
        if (currentActor.isNotBlank() && !currentActor.equals(bizName, ignoreCase = true)) {
            personMap[currentActor] = if (isTamil) "பங்குதாரர்" else "Partner"
        }
        val owner = settings.ownerName.trim()
        if (owner.isNotBlank() && !owner.equals(bizName, ignoreCase = true) && !personMap.containsKey(owner)) {
            personMap[owner] = if (isTamil) "உரிமையாளர்" else "Business Owner"
        }
        partners.forEach { p ->
            val pName = p.name.trim()
            if (pName.isNotBlank() && !pName.equals(bizName, ignoreCase = true) && !personMap.containsKey(pName)) {
                personMap[pName] = p.role.ifBlank { if (isTamil) "பங்குதாரர்" else "Partner" }
            }
        }
        withdrawals.forEach { w ->
            val wName = w.partnerName.trim()
            if (wName.isNotBlank() && !wName.equals(bizName, ignoreCase = true) && !personMap.containsKey(wName)) {
                personMap[wName] = if (isTamil) "பங்குதாரர்" else "Partner"
            }
        }
        personMap
    }

    // Authoritative eligible participants for Profit Share Allocation
    // Derived strictly from actual eligible workspace members (OWNER and PARTNER, never OPERATOR or Business Name)
    val eligibleProfitShareParticipants = remember(workspaceMembers, partners, settings, isTamil) {
        getEligibleProfitShareParticipants(workspaceMembers, partners, settings, isTamil)
    }

    // Calculate Partner Share & Investment Statuses
    val partnerShareInvestmentStatuses = remember(jobs, expenses, withdrawals, partners, workspaceMembers, profitShareAllocations, profitShareAllocationDetails, settings.businessName) {
        FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = jobs,
            expenses = expenses,
            withdrawals = withdrawals,
            partners = partners,
            workspaceMembers = workspaceMembers,
            profitShareAllocations = profitShareAllocations,
            profitShareAllocationDetails = profitShareAllocationDetails,
            businessName = settings.businessName
        )
    }

    // Dynamic items for Profit-Share Percentage Allocation meter and legend
    val displayedProfitShareItems = remember(eligibleProfitShareParticipants, profitShareAllocations, profitShareAllocationDetails, partnerShareInvestmentStatuses, settings.businessName) {
        val bizName = settings.businessName.trim()
        val items = mutableListOf<ProfitShareDisplayItem>()
        val hasConfig = profitShareAllocations.isNotEmpty() && profitShareAllocations.values.sum() == 100

        eligibleProfitShareParticipants.forEach { p ->
            val name = p.displayName
            if (!name.equals(bizName, ignoreCase = true)) {
                val pct = if (hasConfig) {
                    profitShareAllocations[p.uid] ?: 0
                } else {
                    val status = partnerShareInvestmentStatuses.find { it.partnerUid == p.uid || it.partnerName.equals(name, ignoreCase = true) }
                    status?.percentage ?: 0
                }
                val detail = profitShareAllocationDetails[p.uid]
                val isActive = detail?.isActive ?: (pct > 0 || !hasConfig)
                items.add(
                    ProfitShareDisplayItem(
                        uid = p.uid,
                        displayName = name,
                        percentage = pct,
                        isActive = isActive
                    )
                )
            }
        }
        items
    }

    // Dynamic Pie Chart Slices matching active filters with dedicated color palette
    val pieSlices = remember(filteredWithdrawals, allWorkspacePersons, selectedPartnerFilter) {
        val targetNames = if (selectedPartnerFilter == "All") {
            allWorkspacePersons.keys.toList()
        } else {
            allWorkspacePersons.keys.filter { it.contains(selectedPartnerFilter, ignoreCase = true) }
        }
        targetNames.mapIndexedNotNull { index, personName ->
            val pWithdrawals = filteredWithdrawals.filter { it.partnerName.contains(personName, ignoreCase = true) }
            val amount = pWithdrawals.sumOf { it.amount }
            val count = pWithdrawals.size
            if (amount > 0 || selectedPartnerFilter != "All") {
                val color = PartnerDistributionColors.getOrElse(index % PartnerDistributionColors.size) { PartnerDistributionColors[0] }
                PieChartSlice(
                    name = personName,
                    value = amount,
                    color = color,
                    subText = if (isTamil) "$count பரிவர்த்தனைகள்" else "$count txns"
                )
            } else null
        }
    }

    if (showFullWithdrawalHistory) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF8FAFC))
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { showFullWithdrawalHistory = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isTamil) "அனைத்து எடுப்பு வரலாறும்" else "Full Withdrawal History",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    if (filteredWithdrawals.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(28.dp).fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (isTamil) "எடுப்புப் பதிவுகள் எதுவும் இல்லை" else "No withdrawal records found",
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF334155),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    } else {
                        items(filteredWithdrawals, key = { "full_withdrawal_${it.id}_${it.timestamp}" }) { w ->
                            WithdrawalItemCard(
                                withdrawal = w,
                                isTamil = isTamil,
                                onDelete = { withdrawalToDelete = w },
                                onShareWhatsApp = {
                                    val shareText = buildWithdrawalWhatsAppMessage(w, settings.businessName, isTamil)
                                    sendWhatsAppMessage(context, null, shareText)
                                }
                            )
                        }
                    }
                }
            }

            // Confirm Delete Dialog
            withdrawalToDelete?.let { w ->
                AlertDialog(
                    onDismissRequest = { withdrawalToDelete = null },
                    title = { Text(if (isTamil) "எடுப்புப் பதிவை நீக்கவா?" else "Delete Withdrawal Record?", fontWeight = FontWeight.Bold, color = ForestGreenHeader) },
                    text = {
                        Text(
                            text = if (isTamil) "${w.partnerName} என்பவரால் எடுக்கப்பட்ட ரூ. ${formatInr(w.amount)} எடுப்புப் பதிவை நிச்சயமாக நீக்க விரும்புகிறீர்களா?"
                            else "Are you sure you want to delete the withdrawal entry of ${formatInr(w.amount)} taken by ${w.partnerName}?"
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                onDeleteWithdrawal(w)
                                withdrawalToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertDueRed)
                        ) {
                            Text(if (isTamil) "நீக்கு" else "Delete")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { withdrawalToDelete = null }) {
                            Text(if (isTamil) "ரத்து" else "Cancel")
                        }
                    }
                )
            }
        }
    } else {
    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFF8FAFC))) {
        // 1. Two Action Buttons: Left "+ Take Amount", Right "PDF Report"
        Surface(
            color = Color.White,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        takeAmountTargetStatus = null
                        showTakeAmountDialog = true
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_take_amount"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B532E))
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isTamil) "எடுப்புப் பதிவு செய்க" else "Take Amount",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = Color.White
                    )
                }
                OutlinedButton(
                    onClick = { showPdfOptionsDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("btn_export_withdrawal_pdf"),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White)
                ) {
                    Icon(
                        Icons.Default.PictureAsPdf,
                        contentDescription = null,
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isTamil) "PDF அறிக்கை" else "PDF Report",
                        color = Color(0xFF0F172A),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 10.dp,
                bottom = 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 2. Top Summary Card: Available Cash (Green wallet) + Total Withdrawals (Orange trending)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Card: Available Cash
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFDCFCE7),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = if (isTamil) "கிடைக்கும் ரொக்கம்" else "Available Cash",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = formatInr(availableAmount),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0B532E)
                                )
                            }
                        }
                    }

                    // Right Card: Total Withdrawals
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFFFFFBEB),
                        border = BorderStroke(1.dp, Color(0xFFFEF3C7)),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFFEF3C7),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.TrendingUp,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = if (isTamil) "மொத்த எடுப்புகள்" else "Total Withdrawals",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                                val displayDrawings = if (selectedDatePreset != DatePreset.ALL_TIME || selectedPartnerFilter != "All") currentFilteredTotal else totalWithdrawn
                                Text(
                                    text = formatInr(displayDrawings),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFD97706)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Filter Records (Collapsible)
            item {
                CollapsibleFilterCard(
                    isExpanded = isFilterExpanded,
                    onToggleExpand = {
                        if (!isFilterExpanded) {
                            draftPartnerFilter = selectedPartnerFilter
                            draftDatePreset = selectedDatePreset
                            draftCustomDateMillis = customDateMillis
                        }
                        isFilterExpanded = !isFilterExpanded
                    },
                    activeFiltersCount = (if (selectedPartnerFilter != "All") 1 else 0) + (if (selectedDatePreset != DatePreset.ALL_TIME) 1 else 0),
                    onClearFilters = {
                        draftPartnerFilter = "All"
                        draftDatePreset = DatePreset.ALL_TIME
                        selectedPartnerFilter = "All"
                        selectedDatePreset = DatePreset.ALL_TIME
                        isFilterExpanded = false
                    },
                    onApplyFilters = {
                        selectedPartnerFilter = draftPartnerFilter
                        selectedDatePreset = draftDatePreset
                        customDateMillis = draftCustomDateMillis
                        isFilterExpanded = false
                    }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = if (isTamil) "தேதி வடிகட்டி:" else "Filter by Date:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(DatePreset.ALL_TIME to "All", DatePreset.TODAY to "Today", DatePreset.THIS_MONTH to "This Month", DatePreset.CUSTOM to "Specific Date").forEach { (preset, label) ->
                                FilterChip(
                                    selected = draftDatePreset == preset,
                                    onClick = {
                                        draftDatePreset = preset
                                        if (preset == DatePreset.CUSTOM) {
                                            val cal = Calendar.getInstance().apply { timeInMillis = draftCustomDateMillis }
                                            DatePickerDialog(
                                                context,
                                                { _, y, m, d ->
                                                    val nc = Calendar.getInstance().apply { set(y, m, d) }
                                                    draftCustomDateMillis = nc.timeInMillis
                                                },
                                                cal.get(Calendar.YEAR),
                                                cal.get(Calendar.MONTH),
                                                cal.get(Calendar.DAY_OF_MONTH)
                                            ).show()
                                        }
                                    },
                                    label = { Text(label, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFDCFCE7))
                                )
                            }
                        }
                        if (draftDatePreset == DatePreset.CUSTOM) {
                            Text(
                                text = "${if (isTamil) "தேர்ந்தெடுக்கப்பட்ட தேதி:" else "Selected Date:"} ${formatDate(draftCustomDateMillis)}",
                                fontSize = 11.sp,
                                color = Color(0xFF0B532E),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isTamil) "பங்குதாரர் மூலம் வடிகட்டு:" else "Filter by Partner:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = draftPartnerFilter == "All",
                                onClick = { draftPartnerFilter = "All" },
                                label = { Text(if (isTamil) "அனைத்து பங்குதாரர்கள்" else "All Partners", fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFDCFCE7))
                            )
                            allWorkspacePersons.keys.forEach { personName ->
                                FilterChip(
                                    selected = draftPartnerFilter == personName,
                                    onClick = { draftPartnerFilter = if (draftPartnerFilter == personName) "All" else personName },
                                    label = { Text(personName, fontSize = 11.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFDCFCE7))
                                )
                            }
                        }
                    }
                }
            }

            // 4. Partner Withdrawal Distribution Donut Chart
            item {
                PartnerWithdrawalPieChart(
                    slices = pieSlices,
                    totalAmount = currentFilteredTotal
                )
            }

            // 5. Past Withdrawals Ledger
            item(key = "section_past_withdrawals_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isTamil) "கடந்த எடுப்புப் பதிவேடு" else "Past Withdrawals Ledger",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = if (isTamil) "அனைத்தும் பார் >" else "View All >",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0B532E),
                        modifier = Modifier.clickable {
                            selectedPartnerFilter = "All"
                            selectedDatePreset = DatePreset.ALL_TIME
                            showFullWithdrawalHistory = true
                        }
                    )
                }
            }

            item(key = "section_ledger_summary_card") {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedPartnerFilter = "All"
                            selectedDatePreset = DatePreset.ALL_TIME
                            showFullWithdrawalHistory = true
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFDCFCE7),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = Color(0xFF0B532E),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = if (isTamil) "பதிவு செய்யப்பட்ட எடுப்புகள் (${filteredWithdrawals.size})" else "Recorded Withdrawals (${filteredWithdrawals.size})",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = if (isTamil) "அனைத்து விவரங்களையும் காண 'அனைத்தும் பார்' என்பதைத் தட்டவும்" else "Tap 'View All' to open complete ledger history",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = Color(0xFF0B532E),
                            modifier = Modifier
                                .size(18.dp)
                                .graphicsLayer(rotationZ = 180f)
                        )
                    }
                }
            }

            // 5.5. Profit-Share Percentage Allocation Meter
            item(key = "section_profit_share_percentage_meter") {
                val meterColors = listOf(
                    Color(0xFF059669),
                    Color(0xFF2563EB),
                    Color(0xFFD97706),
                    Color(0xFF7C3AED),
                    Color(0xFFDB2777),
                    Color(0xFF0891B2)
                )
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth().testTag("profit_share_percentage_card")
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Default.PieChart,
                                    contentDescription = null,
                                    tint = Color(0xFF0B532E),
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isTamil) "லாபப் பங்கு சதவீத ஒதுக்கீடு" else "Profit-Share Allocation",
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }
                        }

                        // Segmented Percentage Bar
                        val activeMeterItems = displayedProfitShareItems.filter { it.percentage > 0 }
                        if (activeMeterItems.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(12.dp)
                                    .clip(RoundedCornerShape(6.dp))
                            ) {
                                activeMeterItems.forEachIndexed { index, item ->
                                    val col = meterColors[index % meterColors.size]
                                    Box(
                                        modifier = Modifier
                                            .weight(item.percentage.toFloat())
                                            .fillMaxHeight()
                                            .background(col)
                                    )
                                }
                            }
                        }

                        // Legend with Person Names and Percentages (Never Business Name)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            displayedProfitShareItems.forEachIndexed { index, item ->
                                val pct = item.percentage
                                val col = if (pct > 0) meterColors[index % meterColors.size] else Color(0xFF94A3B8)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(col))
                                    Text(
                                        text = item.displayName,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (pct > 0) Color(0xFF334155) else Color(0xFF64748B)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = if (pct > 0) col.copy(alpha = 0.12f) else Color(0xFFF1F5F9)
                                    ) {
                                        Text(
                                            text = "$pct%",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = col,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 6. Partner Share & Investment Section (Horizontal Cards)
            item(key = "section_partner_share_investment_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isTamil) "பங்குதாரர் பங்கு & முதலீடு" else "Partner Share & Investment",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF0B532E)))
                            Text(text = if (isTamil) "எடுக்கப்பட்டது" else "Taken", fontSize = 10.sp, color = Color(0xFF475569))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF86EFAC)))
                            Text(text = if (isTamil) "மீதம்" else "Remaining", fontSize = 10.sp, color = Color(0xFF475569))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Color(0xFF2563EB)))
                            Text(text = if (isTamil) "முதலீடு" else "Investment", fontSize = 10.sp, color = Color(0xFF475569))
                        }
                    }
                }
            }

            items(partnerShareInvestmentStatuses, key = { "partner_share_inv_${it.partnerName}" }) { status ->
                // Partners only see the "Get" action on their OWN card
                val isOwnCard = isOwner || status.partnerName.trim().equals(currentUserName.trim(), ignoreCase = true)
                PartnerShareInvestmentCard(
                    status = status,
                    isTamil = isTamil,
                    isOwner = isOwner,
                    isOwnCard = isOwnCard,
                    onTakeAmount = { targetStatus ->
                        takeAmountTargetStatus = targetStatus
                        showTakeAmountDialog = true
                    }
                )
            }
        }
    }

    // Screen 2: Take Amount Modal / Bottom Sheet
    if (showTakeAmountDialog) {
        TakeAmountDialog(
            partners = partners,
            allPersons = allWorkspacePersons,
            partnerStatuses = partnerShareInvestmentStatuses,
            initialTargetStatus = takeAmountTargetStatus,
            availableAmount = availableAmount,
            currency = settings.currency,
            isTamil = isTamil,
            isOwner = isOwner,
            currentUserName = currentUserName,
            workspaceMembers = workspaceMembers,
            onDismiss = {
                showTakeAmountDialog = false
                takeAmountTargetStatus = null
            },
            onConfirm = { withdrawalsToSave ->
                withdrawalsToSave.forEach { onAddWithdrawal(it) }
                showTakeAmountDialog = false
                takeAmountTargetStatus = null
            }
        )
    }

    

    // PDF Options Dialog
    if (showPdfOptionsDialog) {
        PdfOptionsDialog(
            title = if (isTamil) "எடுப்பு அறிக்கை PDF" else "Withdrawal Report PDF",
            subtitle = if (isTamil) "பங்குதாரர் எடுப்பு அறிக்கையை பகிரவும் அல்லது சேமிக்கவும்" else "Share or save partner withdrawal report",
            isTamil = isTamil,
            onSharePdf = {
                val file = PdfGeneratorHelper.generateWithdrawalReportPdf(
                    context = context,
                    settings = settings,
                    availableAmount = availableAmount,
                    totalWithdrawn = totalWithdrawn,
                    withdrawals = filteredWithdrawals
                )
                file?.let {
                    PdfGeneratorHelper.sharePdf(context, it, "Partner Withdrawal Report - ${settings.businessName}")
                }
            },
            onDownloadPdf = {
                val file = PdfGeneratorHelper.generateWithdrawalReportPdf(
                    context = context,
                    settings = settings,
                    availableAmount = availableAmount,
                    totalWithdrawn = totalWithdrawn,
                    withdrawals = filteredWithdrawals
                )
                file?.let {
                    val displayName = "Withdrawal_Report_${settings.businessName.ifBlank { "Business" }}"
                    PdfGeneratorHelper.downloadPdfToDownloads(context, it, displayName, isTamil)
                }
            },
            onDismiss = { showPdfOptionsDialog = false }
        )
    }
}
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENT: HORIZONTAL PARTNER SHARE & INVESTMENT CARD (REFERENCE IMAGE 1)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PartnerShareInvestmentCard(
    status: PartnerShareInvestmentStatus,
    isTamil: Boolean,
    isOwner: Boolean = false,
    isOwnCard: Boolean = true,
    onTakeAmount: (PartnerShareInvestmentStatus) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Partner Top Row: Avatar Initial + Partner Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFDCFCE7),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = status.partnerName.take(1).uppercase(Locale.getDefault()),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF15803D),
                            fontSize = 13.sp
                        )
                    }
                }
                Text(
                    text = status.partnerName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Horizontal Details Row: Share | Investment | Total to get
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Column 1: Share
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Share (${formatInr(status.totalShare)})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // Share 2-tone progress bar: Dark green (Taken) + Light green (Remaining)
                    val shareTakenFrac = if (status.totalShare > 0) (status.takenShare / status.totalShare).toFloat().coerceIn(0f, 1f) else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF86EFAC)) // Remaining light green
                    ) {
                        if (shareTakenFrac > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(shareTakenFrac)
                                    .background(Color(0xFF0B532E)) // Taken dark green
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(formatInr(status.takenShare), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
                            Text(if (isTamil) "எடுக்கப்பட்டது" else "Taken", fontSize = 9.5.sp, color = Color(0xFF64748B))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatInr(status.remainingShare), fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF15803D))
                            Text(if (isTamil) "மீதம்" else "Remaining", fontSize = 9.5.sp, color = Color(0xFF64748B))
                        }
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                // Column 2: Investment
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (status.totalInvestment > 0) "Investment (${formatInr(status.totalInvestment)})" else "Investment",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    // Investment 2-tone progress bar: Strong blue (Taken) + Light blue (Remaining)
                    val invTakenFrac = if (status.totalInvestment > 0) (status.takenInvestment / status.totalInvestment).toFloat().coerceIn(0f, 1f) else 0f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (status.totalInvestment > 0) Color(0xFFBFDBFE) else Color(0xFFE2E8F0))
                    ) {
                        if (invTakenFrac > 0f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(invTakenFrac)
                                    .background(Color(0xFF2563EB)) // Strong blue
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = formatInr(status.takenInvestment),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (status.totalInvestment > 0) Color(0xFF0F172A) else Color(0xFF2563EB)
                            )
                            Text(if (isTamil) "எடுக்கப்பட்டது" else "Taken", fontSize = 9.5.sp, color = Color(0xFF64748B))
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = formatInr(status.remainingInvestment),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (status.totalInvestment > 0) Color(0xFF2563EB) else Color(0xFF64748B)
                            )
                            Text(if (isTamil) "மீதம்" else "Remaining", fontSize = 9.5.sp, color = Color(0xFF64748B))
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Column 3: Total to get card / Direct "Get ₹X" Action
                // Partners can only take their OWN entitlement (isOwnCard enforces this)
                if (isOwnCard) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                        modifier = Modifier.clickable { onTakeAmount(status) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = if (isTamil) "பெறவேண்டியது" else "Total to get",
                                    fontSize = 9.5.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = if (status.totalToGet > 0) (if (isTamil) "${formatInr(status.totalToGet)} பெறுக" else "Get ${formatInr(status.totalToGet)}") else formatInr(0.0),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0B532E)
                                )
                            }
                            Spacer(modifier = Modifier.width(2.dp))
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFF16A34A),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                } else {
                    // Partner viewing another person's card — show read-only entitlement amount
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF8FAFC),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = if (isTamil) "பெறவேண்டியது" else "Total to get",
                                fontSize = 9.5.sp,
                                color = Color(0xFF94A3B8),
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = formatInr(status.totalToGet),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENT: SCREEN 2 — TAKE AMOUNT MODAL (REFERENCE IMAGE 2)
// ─────────────────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakeAmountDialog(
    partners: List<PartnerEntity>,
    allPersons: Map<String, String> = emptyMap(),
    partnerStatuses: List<PartnerShareInvestmentStatus> = emptyList(),
    initialTargetStatus: PartnerShareInvestmentStatus? = null,
    availableAmount: Double = 0.0,
    currency: String = "₹",
    isTamil: Boolean = false,
    isOwner: Boolean = false,
    currentUserName: String = "",
    workspaceMembers: List<WorkspaceMember> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (List<WithdrawalEntity>) -> Unit
) {
    val context = LocalContext.current
    // Owners see all partners; Partners see only themselves
    val personOptions = remember(allPersons, partners, isOwner, currentUserName) {
        val all = if (allPersons.isNotEmpty()) {
            allPersons.entries.map { it.key to it.value }
        } else {
            partners.map { it.name to it.role }
        }
        if (isOwner) {
            all
        } else {
            // Restrict to current user only
            val matched = all.filter { it.first.trim().equals(currentUserName.trim(), ignoreCase = true) }
            matched.ifEmpty { all.take(1) } // fallback: show at least first option
        }
    }

    var selectedPartner by remember(initialTargetStatus) {
        mutableStateOf(
            if (initialTargetStatus != null && initialTargetStatus.partnerName.isNotBlank()) {
                val matchedOption = personOptions.find { it.first.trim().equals(initialTargetStatus.partnerName.trim(), ignoreCase = true) }
                matchedOption?.first ?: initialTargetStatus.partnerName.trim()
            } else {
                personOptions.firstOrNull()?.first ?: ""
            }
        )
    }

    var selectedCategory by remember(initialTargetStatus) {
        mutableStateOf(
            when {
                initialTargetStatus != null && initialTargetStatus.remainingInvestment > 0 && initialTargetStatus.remainingShare <= 0 -> "Investment"
                else -> "Withdrawal"
            }
        )
    }

    var amountText by remember(initialTargetStatus) {
        mutableStateOf(
            if (initialTargetStatus != null && initialTargetStatus.totalToGet > 0) {
                val rounded = Math.round(initialTargetStatus.totalToGet)
                rounded.toString()
            } else ""
        )
    }
    var note by remember { mutableStateOf("") }
    var selectedDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var isDateCustom by remember { mutableStateOf(false) }

    var partnerDropdownExpanded by remember { mutableStateOf(false) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }
    var hasAttemptedSubmit by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }

    val enteredAmount = amountText.replace(",", "").toDoubleOrNull() ?: 0.0
    val matchedStatus = remember(selectedPartner, partnerStatuses) {
        partnerStatuses.find { it.partnerName.trim().equals(selectedPartner.trim(), ignoreCase = true) }
    }

    val remShare = matchedStatus?.remainingShare ?: 0.0
    val remInv = matchedStatus?.remainingInvestment ?: 0.0
    val totalToGet = matchedStatus?.totalToGet ?: 0.0

    val maxAvailableFromSource = when {
        selectedCategory.equals("Share & Investment", ignoreCase = true) -> 2 * minOf(remShare, remInv)
        selectedCategory.contains("share", ignoreCase = true) -> remShare
        selectedCategory.contains("invest", ignoreCase = true) -> remInv
        selectedCategory.contains("personal use", ignoreCase = true) || selectedCategory.contains("advance", ignoreCase = true) -> totalToGet
        else -> totalToGet
    }

    val isAmountZeroOrNegative = enteredAmount <= 0.0
    val isAmountExceedsSource = when {
        selectedCategory.equals("Share & Investment", ignoreCase = true) -> {
            val sharePart = Math.ceil(enteredAmount / 2.0)
            val invPart = enteredAmount - sharePart
            sharePart > remShare || invPart > remInv
        }
        selectedCategory.contains("share", ignoreCase = true) -> {
            enteredAmount > remShare
        }
        selectedCategory.contains("invest", ignoreCase = true) -> {
            enteredAmount > remInv
        }
        selectedCategory.contains("personal use", ignoreCase = true) || selectedCategory.contains("advance", ignoreCase = true) -> {
            enteredAmount > totalToGet
        }
        else -> enteredAmount > totalToGet
    }
    val isAmountExceedsCash = enteredAmount > availableAmount
    val isAmountInvalid = isAmountZeroOrNegative || isAmountExceedsSource || isAmountExceedsCash
    val isPartnerInvalid = selectedPartner.isBlank()

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState,
        containerColor = Color.White,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .verticalScroll(rememberScrollState())
        ) {
                    // Top Drag Handle Bar
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(36.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFCBD5E1))
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Header Row: "Take Amount" | Date pill "Today ▼" | "X" Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isTamil) "பணம் எடுத்தல்" else "Take Amount",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Date selector pill
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = Color(0xFFF8FAFC),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.clickable {
                                    val cal = Calendar.getInstance().apply { timeInMillis = selectedDateMillis }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val nc = Calendar.getInstance().apply { set(y, m, d) }
                                            selectedDateMillis = nc.timeInMillis
                                            isDateCustom = true
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        tint = Color(0xFF475569),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (isDateCustom) formatDate(selectedDateMillis) else (if (isTamil) "இன்று ▼" else "Today ▼"),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF334155)
                                    )
                                }
                            }

                            // Close "X" Button
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(Color(0xFFF1F5F9), CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF475569),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. Partner Dropdown Field
                    Text(
                        text = if (isTamil) "பங்குதாரர்" else "Partner",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Partners cannot change partner — dropdown is locked for them
                    val canChangePartner = isOwner && personOptions.size > 1
                    ExposedDropdownMenuBox(
                        expanded = partnerDropdownExpanded,
                        onExpandedChange = { if (!isSubmitting && canChangePartner) partnerDropdownExpanded = !partnerDropdownExpanded }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .menuAnchor()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xFFDCFCE7),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = selectedPartner.take(1).uppercase(Locale.getDefault()),
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF15803D),
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = selectedPartner.ifBlank { if (isTamil) "பங்குதாரரைத் தேர்ந்தெடுக்கவும்" else "Select Partner" },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    modifier = Modifier.weight(1f)
                                )
                                if (canChangePartner) {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color(0xFF64748B)
                                    )
                                }
                            }
                        }
                        ExposedDropdownMenu(
                            expanded = partnerDropdownExpanded,
                            onDismissRequest = { partnerDropdownExpanded = false }
                        ) {
                            personOptions.forEach { (personName, personRole) ->
                                DropdownMenuItem(
                                    text = { Text("$personName ($personRole)", fontWeight = FontWeight.Medium) },
                                    onClick = {
                                        selectedPartner = personName
                                        partnerDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 2. Category Dropdown Field
                    Text(
                        text = if (isTamil) "வகை" else "Category",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    ExposedDropdownMenuBox(
                        expanded = categoryDropdownExpanded,
                        onExpandedChange = { if (!isSubmitting) categoryDropdownExpanded = !categoryDropdownExpanded }
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .menuAnchor()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFDCFCE7),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.AccountBalanceWallet,
                                            contentDescription = null,
                                            tint = Color(0xFF16A34A),
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = getLocalizedWithdrawalCategory(selectedCategory, isTamil),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF0F172A),
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color(0xFF64748B)
                                )
                            }
                        }
                        ExposedDropdownMenu(
                            expanded = categoryDropdownExpanded,
                            onDismissRequest = { categoryDropdownExpanded = false }
                        ) {
                            WithdrawalCategories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(getLocalizedWithdrawalCategory(cat, isTamil), fontWeight = FontWeight.Medium) },
                                    onClick = {
                                        selectedCategory = cat
                                        categoryDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 3. Amount Field
                    Text(
                        text = if (isTamil) "தொகை" else "Amount",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(
                            1.dp,
                            if (hasAttemptedSubmit && isAmountInvalid) Color(0xFFDC2626) else Color(0xFFE2E8F0)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFDCFCE7),
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "₹",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF16A34A)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            BasicTextField(
                                value = amountText,
                                onValueChange = { amountText = it },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                textStyle = TextStyle(
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F172A)
                                ),
                                decorationBox = { innerTextField ->
                                    if (amountText.isEmpty()) {
                                        Text(
                                            text = if (isTamil) "தொகையை உள்ளிடவும்" else "Enter amount",
                                            fontSize = 14.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier.fillMaxWidth().testTag("take_amount_input")
                            )
                        }
                    }

                    // Human-readable validation message
                    val showCashWarning = isAmountExceedsCash && enteredAmount > 0
                    if (showCashWarning || (hasAttemptedSubmit && (isAmountInvalid || isPartnerInvalid))) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val validationMsg = when {
                            isPartnerInvalid -> if (isTamil) "பங்குதாரரைத் தேர்ந்தெடுக்கவும்" else "Please select a partner"
                            isAmountZeroOrNegative -> if (isTamil) "செல்லுபடியான தொகையை உள்ளிடவும்" else "Please enter a valid amount"
                            isAmountExceedsCash -> {
                                if (availableAmount <= 0.0) {
                                    if (isTamil) "வணிக இருப்பு இல்லை. நிலுவைத் தொகையை முதலில் வசூலிக்கவும்."
                                    else "No business balance available. Please collect due amount first."
                                } else {
                                    if (isTamil) "போதுமான வணிக இருப்பு இல்லை. நிலுவைத் தொகையை முதலில் வசூலிக்கவும்."
                                    else "Insufficient business balance. Please collect due amount first."
                                }
                            }
                            isAmountExceedsSource -> {
                                when {
                                    selectedCategory.equals("Share & Investment", ignoreCase = true) -> {
                                        val sharePart = Math.ceil(enteredAmount / 2.0)
                                        val invPart = enteredAmount - sharePart
                                        if (sharePart > remShare) {
                                            if (isTamil) "50/50 பிரிவுக்கு தேவையான பங்கு ரூ. ${formatInr(sharePart)} இல்லை (இருப்பு: ரூ. ${formatInr(remShare)})"
                                            else "Insufficient Profit Share for 50/50 split (requires ${formatInr(sharePart)}, available: ${formatInr(remShare)})"
                                        } else {
                                            if (isTamil) "50/50 பிரிவுக்கு தேவையான முதலீடு ரூ. ${formatInr(invPart)} இல்லை (இருப்பு: ரூ. ${formatInr(remInv)})"
                                            else "Insufficient Investment for 50/50 split (requires ${formatInr(invPart)}, available: ${formatInr(remInv)})"
                                        }
                                    }
                                    selectedCategory.contains("share", true) ->
                                        if (isTamil) "பங்கில் ரூ. ${formatInr(remShare)} மட்டுமே உள்ளது"
                                        else "Only ${formatInr(remShare)} available in Profit Share"
                                    selectedCategory.contains("invest", true) ->
                                        if (isTamil) "முதலீட்டில் ரூ. ${formatInr(remInv)} மட்டுமே உள்ளது"
                                        else "Only ${formatInr(remInv)} available in Investment"
                                    selectedCategory.contains("personal use", true) || selectedCategory.contains("advance", true) ->
                                        if (isTamil) "உரிமைத்தொகை ரூ. ${formatInr(totalToGet)} மட்டுமே உள்ளது"
                                        else "Only ${formatInr(totalToGet)} available in Total Entitlement"
                                    else -> "Invalid amount"
                                }
                            }
                            else -> "Invalid amount"
                        }
                        Text(
                            text = validationMsg,
                            color = Color(0xFFDC2626),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // 4. Notes (Optional)
                    Text(
                        text = if (isTamil) "குறிப்புகள் (விருப்பத்தேர்வு)" else "Notes (Optional)",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color.White,
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                BasicTextField(
                                    value = note,
                                    onValueChange = { if (it.length <= 100) note = it },
                                    maxLines = 2,
                                    textStyle = TextStyle(
                                        fontSize = 13.5.sp,
                                        color = Color(0xFF0F172A)
                                    ),
                                    decorationBox = { innerTextField ->
                                        if (note.isEmpty()) {
                                            Text(
                                                text = if (isTamil) "குறிப்பு சேர்க்க..." else "Add a note...",
                                                fontSize = 13.sp,
                                                color = Color(0xFF94A3B8)
                                            )
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Text(
                                text = "${note.length}/100",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8),
                                modifier = Modifier.align(Alignment.BottomEnd)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Available Cash Card at Bottom of modal
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0xFFF0FDF4),
                        border = BorderStroke(1.dp, Color(0xFFDCFCE7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFDCFCE7),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = if (isTamil) "கிடைக்கும் ரொக்கம்" else "Available Cash",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF64748B),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = formatInr(availableAmount),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0B532E)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Confirm Button: "Confirm Take Amount"
                    Button(
                        onClick = {
                            hasAttemptedSubmit = true
                            val amt = amountText.replace(",", "").toDoubleOrNull() ?: 0.0
                            if (amt > 0 && !isAmountInvalid && selectedPartner.isNotBlank() && !isSubmitting) {
                                isSubmitting = true
                                val matchedPartner = partners.find { it.name.trim().equals(selectedPartner.trim(), ignoreCase = true) }
                                val pId = matchedPartner?.id ?: 0
                                val targetUid = matchedStatus?.partnerUid?.takeIf { it.isNotBlank() }
                                    ?: initialTargetStatus?.partnerUid?.takeIf { it.isNotBlank() }
                                    ?: workspaceMembers.firstOrNull {
                                        it.displayName?.trim()?.equals(selectedPartner.trim(), ignoreCase = true) == true ||
                                        it.phoneNumber?.filter { c -> c.isDigit() }?.takeLast(10) == selectedPartner.filter { c -> c.isDigit() }.takeLast(10)
                                    }?.uid
                                    ?: ""
                                val withdrawalsToRecord = mutableListOf<WithdrawalEntity>()
                                withdrawalsToRecord.add(
                                    WithdrawalEntity(
                                        partnerId = pId,
                                        partnerName = selectedPartner,
                                        amount = amt,
                                        category = selectedCategory,
                                        note = note,
                                        timestamp = selectedDateMillis,
                                        targetPartnerUid = targetUid
                                    )
                                )
                                onConfirm(withdrawalsToRecord)
                            }
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("confirm_take_amount_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B532E))
                    ) {
                        Text(
                            text = if (isTamil) "எடுப்பை உறுதிசெய்" else "Confirm Take Amount",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
    }
}

@Composable
fun WithdrawalItemCard(
    withdrawal: WithdrawalEntity,
    isTamil: Boolean = false,
    onDelete: () -> Unit,
    onShareWhatsApp: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("withdrawal_card_${withdrawal.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFEF3C7)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBalanceWallet,
                        contentDescription = null,
                        tint = Color(0xFFD97706),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = withdrawal.partnerName,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = formatInr(withdrawal.amount),
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD97706)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isTamil) "வகை: ${getLocalizedWithdrawalCategory(withdrawal.category, isTamil)}" else "Category: ${withdrawal.category}",
                        fontSize = 11.5.sp,
                        color = Color(0xFF64748B)
                    )
                    if (withdrawal.note.isNotBlank()) {
                        Text(
                            text = if (isTamil) "குறிப்பு: ${withdrawal.note}" else "Note: ${withdrawal.note}",
                            fontSize = 11.sp,
                            color = Color(0xFF94A3B8),
                            maxLines = 1
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDateTime(withdrawal.timestamp),
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8),
                    modifier = Modifier.weight(1f, fill = false)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FlatShareIconButton(
                        onClick = onShareWhatsApp,
                        modifier = Modifier.testTag("btn_share_withdrawal_${withdrawal.id}"),
                        contentDescription = if (isTamil) "வாட்ஸ்அப் மூலம் பகிரவும்" else "Share Withdrawal via WhatsApp",
                        tint = SuccessPaidGreen,
                        iconSize = 18.dp
                    )
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_delete_withdrawal_${withdrawal.id}")
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENT: CONFIGURE PROFIT-SHARE PERCENTAGES DIALOG (OWNER ONLY)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun ConfigurePercentagesDialog(
    eligibleParticipants: List<ProfitShareParticipant>,
    currentAllocations: Map<String, Int>,
    currentDetails: Map<String, ProfitShareAllocation>,
    isTamil: Boolean,
    isReadOnly: Boolean = false,
    onDismiss: () -> Unit,
    onApply: (Map<String, Int>, Map<String, ProfitShareAllocation>) -> Unit = { _, _ -> }
) {
    val selectedMap = remember(eligibleParticipants, currentAllocations, currentDetails) {
        val map = mutableStateMapOf<String, Boolean>()
        val hasConfig = currentAllocations.isNotEmpty() && currentAllocations.values.sum() == 100
        eligibleParticipants.forEach { p ->
            if (hasConfig) {
                val detail = currentDetails[p.uid]
                val pct = currentAllocations[p.uid] ?: 0
                // Participant is selected if explicitly active or percentage > 0
                map[p.uid] = detail?.isActive == true || pct > 0
            } else {
                // Default: all eligible participants initially selected
                map[p.uid] = true
            }
        }
        map
    }

    val draftPercentages = remember(eligibleParticipants, currentAllocations) {
        val map = mutableStateMapOf<String, Int>()
        val hasConfig = currentAllocations.isNotEmpty() && currentAllocations.values.sum() == 100
        if (hasConfig) {
            eligibleParticipants.forEach { p ->
                map[p.uid] = currentAllocations[p.uid] ?: 0
            }
        } else {
            val count = eligibleParticipants.size
            val base = if (count > 0) 100 / count else 0
            val rem = if (count > 0) 100 % count else 0
            eligibleParticipants.forEachIndexed { i, p ->
                map[p.uid] = base + (if (i < rem) 1 else 0)
            }
        }
        map
    }

    val selectedCount = eligibleParticipants.count { selectedMap[it.uid] == true }
    val totalSum = eligibleParticipants.filter { selectedMap[it.uid] == true }.sumOf { draftPercentages[it.uid] ?: 0 }
    val isValid = totalSum == 100 && selectedCount > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = Color(0xFF0B532E),
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isReadOnly) (if (isTamil) "லாபப் பங்கு ஒதுக்கீடு" else "Profit Share Allocation")
                           else (if (isTamil) "லாபப் பங்கு சதவீதத்தை அமைக்கவும்" else "Configure Profit Share"),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = Color(0xFF0F172A)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = if (isReadOnly) (if (isTamil) "பங்குபெறும் நபர்கள் மற்றும் அவர்களின் ஒதுக்கப்பட்ட சதவீத விவரம்." else "Participating members and their allocated share percentages.")
                           else (if (isTamil) "பங்குபெறும் நபர்களைத் தேர்ந்தெடுத்து சதவீதங்களை உள்ளிடவும். மொத்த ஒதுக்கீடு சரியாக 100% இருக்க வேண்டும்."
                           else "Select participating members and configure their share percentage. Total allocation must equal exactly 100%."),
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )

                // Quick Action Bar: Selected count and Equal Share Button (only in editable mode)
                if (!isReadOnly) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isTamil) "$selectedCount நபர்கள் தேர்வு" else "$selectedCount selected",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF475569)
                        )
                        OutlinedButton(
                            onClick = {
                                val selectedList = eligibleParticipants.filter { selectedMap[it.uid] == true }
                                if (selectedList.isNotEmpty()) {
                                    val count = selectedList.size
                                    val base = 100 / count
                                    val rem = 100 % count
                                    selectedList.forEachIndexed { i, p ->
                                        draftPercentages[p.uid] = base + (if (i < rem) 1 else 0)
                                    }
                                    eligibleParticipants.filter { selectedMap[it.uid] != true }.forEach { p ->
                                        draftPercentages[p.uid] = 0
                                    }
                                }
                            },
                            enabled = selectedCount > 0,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF0B532E)),
                            border = BorderStroke(1.dp, Color(0xFF0B532E))
                        ) {
                            Text(
                                text = if (isTamil) "சம பங்கு" else "Equal Share",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                // Participant List
                eligibleParticipants.forEach { participant ->
                    val isSelected = selectedMap[participant.uid] == true
                    val currentVal = draftPercentages[participant.uid] ?: 0

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) Color(0xFFF8FAFC) else Color(0xFFF1F5F9),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFFCBD5E1) else Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Checkbox + Name + Role
                            val checkModifier = if (!isReadOnly) {
                                Modifier.clickable {
                                    val newState = !isSelected
                                    selectedMap[participant.uid] = newState
                                    if (newState) {
                                        val curSum = eligibleParticipants.filter { selectedMap[it.uid] == true && it.uid != participant.uid }.sumOf { draftPercentages[it.uid] ?: 0 }
                                        draftPercentages[participant.uid] = maxOf(0, 100 - curSum)
                                    } else {
                                        draftPercentages[participant.uid] = 0
                                    }
                                }
                            } else Modifier

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .then(checkModifier)
                            ) {
                                Checkbox(
                                    checked = isSelected,
                                    enabled = !isReadOnly,
                                    onCheckedChange = { checked ->
                                        if (!isReadOnly) {
                                            selectedMap[participant.uid] = checked
                                            if (checked) {
                                                val curSum = eligibleParticipants.filter { selectedMap[it.uid] == true && it.uid != participant.uid }.sumOf { draftPercentages[it.uid] ?: 0 }
                                                draftPercentages[participant.uid] = maxOf(0, 100 - curSum)
                                            } else {
                                                draftPercentages[participant.uid] = 0
                                            }
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF0B532E))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column {
                                    Text(
                                        text = participant.displayName,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 13.5.sp,
                                        color = if (isSelected) Color(0xFF1E293B) else Color(0xFF64748B)
                                    )
                                    Text(
                                        text = participant.role,
                                        fontSize = 11.sp,
                                        color = if (isSelected) Color(0xFF64748B) else Color(0xFF94A3B8)
                                    )
                                }
                            }

                            // Controls or Excluded label
                            if (isSelected) {
                                if (isReadOnly) {
                                    Text(
                                        text = "$currentVal%",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0B532E),
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        // -5 button
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFFE2E8F0),
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clickable {
                                                    draftPercentages[participant.uid] = maxOf(0, currentVal - 5)
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("-", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF334155))
                                            }
                                        }

                                        var textInput by remember(currentVal) { mutableStateOf(currentVal.toString()) }
                                        BasicTextField(
                                            value = textInput,
                                            onValueChange = { input ->
                                                if (input.all { it.isDigit() } && input.length <= 3) {
                                                    textInput = input
                                                    val parsed = input.toIntOrNull() ?: 0
                                                    draftPercentages[participant.uid] = parsed.coerceIn(0, 100)
                                                }
                                            },
                                            textStyle = TextStyle(
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0B532E),
                                                textAlign = TextAlign.Center
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            modifier = Modifier
                                                .width(44.dp)
                                                .background(Color.White, RoundedCornerShape(6.dp))
                                                .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(6.dp))
                                                .padding(vertical = 4.dp)
                                        )

                                        Text("%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF64748B))

                                        // +5 button
                                        Surface(
                                            shape = CircleShape,
                                            color = Color(0xFFDCFCE7),
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clickable {
                                                    draftPercentages[participant.uid] = minOf(100, currentVal + 5)
                                                }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("+", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF0B532E))
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    text = if (isTamil) "0% (பங்கேற்கவில்லை)" else "0% (Excluded)",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF94A3B8),
                                    modifier = Modifier.padding(end = 4.dp)
                                )
                            }
                        }
                    }
                }

                // Total allocation sum indicator
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isValid) Color(0xFFF0FDF4) else Color(0xFFFEF2F2),
                    border = BorderStroke(1.dp, if (isValid) Color(0xFF86EFAC) else Color(0xFFFECACA)),
                    modifier = Modifier.fillMaxWidth().testTag("percentage_total_indicator")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (isTamil) "மொத்த சதவீதம்:" else "Total Allocation:",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF334155)
                        )
                        Text(
                            text = when {
                                selectedCount == 0 -> if (isTamil) "குறைந்தது ஒருவரை தேர்வு செய்யவும்" else "Select at least 1 member"
                                isValid -> "100% ✓"
                                else -> "$totalSum% (Must equal 100%)"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isValid) Color(0xFF16A34A) else Color(0xFFDC2626)
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (!isReadOnly) {
                Button(
                    onClick = {
                        if (isValid) {
                            val newAllocations = mutableMapOf<String, Int>()
                            val newDetails = mutableMapOf<String, ProfitShareAllocation>()
                            eligibleParticipants.forEach { p ->
                                val isSelected = selectedMap[p.uid] == true
                                val pct = if (isSelected) (draftPercentages[p.uid] ?: 0) else 0
                                newAllocations[p.uid] = pct
                                newDetails[p.uid] = ProfitShareAllocation(
                                    participantUid = p.uid,
                                    percentage = pct,
                                    displayName = p.displayName,
                                    role = if (p.isOwner) "owner" else "partner",
                                    isActive = isSelected
                                )
                            }
                            onApply(newAllocations, newDetails)
                        }
                    },
                    enabled = isValid,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B532E)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("btn_save_percentages")
                ) {
                    Text(if (isTamil) "பயன்படுத்து" else "Apply", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = if (isReadOnly) (if (isTamil) "மூடு" else "Close") else (if (isTamil) "ரத்து" else "Cancel"),
                    color = Color(0xFF64748B)
                )
            }
        }
    )
}
