package com.example.ui.screens.report

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import android.app.DatePickerDialog
import java.util.Calendar
import com.example.ui.components.DatePreset
import com.example.ui.components.isDateInPreset
import com.example.ui.components.formatDate
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material.icons.filled.CalendarToday
import com.example.data.auth.RoleUtils
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.PaymentEntity
import com.example.data.firebase.WorkspaceMember
import com.example.ui.components.formatInr
import com.example.ui.theme.AppTheme
import com.example.ui.theme.DeepSageGreen
import com.example.ui.theme.ForestGreenHeader
import com.example.ui.theme.SageCardBg
import com.example.ui.theme.SoftSageGreen
import com.example.ui.theme.SuccessPaidGreen
import com.example.ui.theme.SuccessPaidGreenBg
import com.example.ui.theme.TextMutedDark
import com.example.ui.theme.TextSecondaryDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CollectionHistoryTab(
    settings: AppSettingsEntity,
    payments: List<PaymentEntity>,
    jobs: List<JobEntryEntity> = emptyList(),
    workspaceMembers: List<WorkspaceMember> = emptyList(),
    partners: List<PartnerEntity> = emptyList(),
    isOwner: Boolean = false,
    currentUid: String = "",
    ownerName: String = "",
    onDeleteCollection: ((Long) -> Unit)? = null
) {
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    val context = LocalContext.current
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()

    // Upgraded workspaces can contain both explicit Collections and older Entry receipts.
    // Reconcile their amounts for display without storing synthetic Collections.
    val source = remember(settings.workspaceId, payments, jobs, workspaceMembers, isOwner, currentUid) {
        authorizedCollectionSource(
            workspaceId = settings.workspaceId,
            payments = payments,
            jobs = jobs,
            workspaceMembers = workspaceMembers,
            isOwner = isOwner,
            currentUid = currentUid
        )
    }
    val effectivePayments = source.payments + source.legacyJobs.map { j ->
        val (role, name) = RoleUtils.resolveCreatorRoleAndName(j, ownerName, workspaceMembers, partners)
        PaymentEntity(id = -j.id, workspaceId = j.workspaceId, jobEntryId = j.id,
            customerId = j.customerId,
            customerName = j.customerName, amount = j.amountReceived,
            paymentMethod = recordedLegacyPaymentMethod(j.notes),
            collectedAt = j.startTimeMillis.takeIf { it > 0 } ?: j.createdAt,
            collectedByUid = j.createdByUid, collectedByName = name,
            collectedByRole = j.createdByRole.ifBlank { role }, notes = j.notes,
            tractorId = j.tractorId, tractorLabel = j.tractorLabel, isSynced = j.isSynced, createdAt = j.createdAt)
    }
    val jobHistory = remember(settings.workspaceId, effectivePayments, jobs) {
        visibleCollectionJobHistory(collectionJobHistory(settings.workspaceId, effectivePayments, jobs))
    }
    val storedPaymentIds = source.payments.map { it.id }.toSet()
    val displayPayments = jobHistory.flatMap { group -> group.events.map { event ->
        val p = event.payment
        val collector = com.example.data.auth.CollectionPolicy.collector(p, workspaceMembers)
        val recorder = com.example.data.auth.CollectionPolicy.recorder(p, workspaceMembers)
        PaymentDisplayModel(
            id = p.id, groupKey = group.key, customerName = p.customerName.ifBlank { "Customer" },
            amount = event.amount, originalAmount = p.amount, paymentMethod = p.paymentMethod,
            collectedAt = p.collectedAt, collectorUid = collector.uid, collectorName = collector.name,
            collectorRole = collector.role, recordedByName = recorder.name, recordedByUid = recorder.uid,
            notes = p.notes, tractorId = p.tractorId, tractorLabel = p.tractorLabel, status = p.status,
            jobEntryId = group.jobId ?: 0, reversedAt = p.reversedAt, reversedByUid = p.reversedByUid,
            isSynced = p.isSynced,
            canReverse = p.id in storedPaymentIds && com.example.data.auth.AuthorizationManager.canReverseCollection(p, isOwner, currentUid, if (isOwner) "Owner" else "Partner")
        )
    } }

    var selectedMethodFilter by remember { mutableStateOf("All") }
    var selectedStatusFilter by remember(settings.workspaceId) { mutableStateOf("ACTIVE") }
    var selectedDatePreset by remember { mutableStateOf(DatePreset.ALL_TIME) }
    var customDateMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCollectorFilter by remember { mutableStateOf<String?>(null) }
    var selectedTractorFilter by remember { mutableStateOf<String?>(null) }
    var collectorDropdownExpanded by remember { mutableStateOf(false) }
    var tractorDropdownExpanded by remember { mutableStateOf(false) }

    val uniqueCollectors = remember(displayPayments) {
        displayPayments.map {
            val key = if (it.collectorUid.isNotBlank()) it.collectorUid else it.collectorName.ifBlank { "Owner" }
            key to it.collectorName.ifBlank { "Owner" }
        }.distinctBy { it.first }
    }

    val uniqueTractors = remember(displayPayments) {
        displayPayments.map {
            val key = if (it.tractorId > 0L) it.tractorId.toString() else it.tractorLabel.ifBlank { "No Tractor" }
            key to it.tractorLabel.ifBlank { "No Tractor" }
        }.distinctBy { it.first }
    }

    val filteredPayments = remember(displayPayments, selectedStatusFilter, selectedMethodFilter, selectedDatePreset, customDateMillis, searchQuery, selectedCollectorFilter, selectedTractorFilter) {
        displayPayments.filter { payment ->
            val matchesStatus = payment.status == selectedStatusFilter
            val matchesSearch = if (searchQuery.isBlank()) {
                true
            } else {
                payment.customerName.contains(searchQuery, ignoreCase = true) ||
                payment.collectorName.contains(searchQuery, ignoreCase = true) ||
                payment.notes.contains(searchQuery, ignoreCase = true)
            }

            val matchesMethod = when (selectedMethodFilter) {
                "All" -> true
                "Cash" -> payment.paymentMethod.contains("Cash", ignoreCase = true) || payment.paymentMethod.isBlank()
                "UPI" -> payment.paymentMethod.contains("UPI", ignoreCase = true) || payment.paymentMethod.contains("GPay", ignoreCase = true)
                "Bank" -> payment.paymentMethod.contains("Bank", ignoreCase = true) || payment.paymentMethod.contains("Transfer", ignoreCase = true)
                "Cheque" -> payment.paymentMethod.contains("Cheque", ignoreCase = true)
                else -> true
            }

            val matchesDate = when (selectedDatePreset) {
                DatePreset.ALL_TIME -> true
                DatePreset.TODAY -> isDateInPreset(payment.collectedAt, DatePreset.TODAY, timeZone = com.example.data.auth.CollectionPolicy.timeZone)
                DatePreset.THIS_MONTH -> isDateInPreset(payment.collectedAt, DatePreset.THIS_MONTH, timeZone = com.example.data.auth.CollectionPolicy.timeZone)
                DatePreset.CUSTOM -> {
                    val calP = Calendar.getInstance(com.example.data.auth.CollectionPolicy.timeZone).apply { timeInMillis = payment.collectedAt }
                    val calCustom = Calendar.getInstance(com.example.data.auth.CollectionPolicy.timeZone).apply { timeInMillis = customDateMillis }
                    calP.get(Calendar.YEAR) == calCustom.get(Calendar.YEAR) &&
                    calP.get(Calendar.DAY_OF_YEAR) == calCustom.get(Calendar.DAY_OF_YEAR)
                }
                else -> isDateInPreset(payment.collectedAt, selectedDatePreset, timeZone = com.example.data.auth.CollectionPolicy.timeZone)
            }

            val matchesCollector = if (selectedCollectorFilter == null) {
                true
            } else {
                payment.collectorUid == selectedCollectorFilter || (payment.collectorUid.isBlank() && payment.collectorName == selectedCollectorFilter)
            }

            val matchesTractor = if (selectedTractorFilter == null) {
                true
            } else {
                payment.tractorId.toString() == selectedTractorFilter || payment.tractorLabel == selectedTractorFilter
            }

            matchesStatus && matchesSearch && matchesMethod && matchesDate && matchesCollector && matchesTractor
        }
    }

    val totalCollected = filteredPayments.filter { it.status == "ACTIVE" }.sumOf { it.amount }
    val filteredGroups = filteredPayments.groupBy { it.groupKey }
    val visibleJobHistory = jobHistory.filter { it.key in filteredGroups }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("collection_history_list"),
        contentPadding = PaddingValues(
            horizontal = responsive.screenPaddingHorizontal,
            vertical = responsive.screenPaddingVertical
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Summary Header Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = SageCardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (isTamil) "மொத்த வசூல் தொகை" else "Total Collected",
                            fontSize = 12.sp,
                            color = TextSecondaryDark,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatInr(totalCollected, settings.currency),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessPaidGreen
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = SuccessPaidGreenBg
                    ) {
                        Text(
                            text = if (isTamil) "${visibleJobHistory.size} வேலைகள் · ${filteredPayments.distinctBy { it.id }.size} கட்டணங்கள்" else "${visibleJobHistory.size} Jobs · ${filteredPayments.distinctBy { it.id }.size} Payments",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessPaidGreen
                        )
                    }
                }
            }
        }

        // Simple Filters Section
        if (displayPayments.isNotEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedStatusFilter == "ACTIVE",
                            onClick = { selectedStatusFilter = "ACTIVE" },
                            label = { Text(if (isTamil) "செயலில்" else "Active") },
                            modifier = Modifier.testTag("collection_filter_active")
                        )
                        FilterChip(
                            selected = selectedStatusFilter == "REVERSED",
                            onClick = { selectedStatusFilter = "REVERSED" },
                            label = { Text(if (isTamil) "திரும்பப் பெற்றவை" else "Reversed audit") },
                            modifier = Modifier.testTag("collection_filter_reversed")
                        )
                    }
                    Text(
                        text = if (isTamil) "தேதி வடிகட்டி:" else "Filter by Date:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = DeepSageGreen
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(DatePreset.ALL_TIME to "All", DatePreset.TODAY to "Today", DatePreset.THIS_MONTH to "This Month", DatePreset.CUSTOM to "Specific Date").forEach { (preset, label) ->
                            FilterChip(
                                selected = selectedDatePreset == preset,
                                onClick = {
                                    selectedDatePreset = preset
                                    if (preset == DatePreset.CUSTOM) {
                                        val cal = Calendar.getInstance(com.example.data.auth.CollectionPolicy.timeZone).apply { timeInMillis = customDateMillis }
                                        DatePickerDialog(
                                            context,
                                            { _, y, m, d ->
                                                val nc = Calendar.getInstance(com.example.data.auth.CollectionPolicy.timeZone).apply { set(y, m, d) }
                                                customDateMillis = nc.timeInMillis
                                            },
                                            cal.get(Calendar.YEAR),
                                            cal.get(Calendar.MONTH),
                                            cal.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    }
                                },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = SoftSageGreen)
                            )
                        }
                    }
                    if (selectedDatePreset == DatePreset.CUSTOM) {
                        Text(
                            text = "${if (isTamil) "தேர்ந்தெடுக்கப்பட்ட தேதி:" else "Selected Date:"} ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).apply { timeZone = com.example.data.auth.CollectionPolicy.timeZone }.format(Date(customDateMillis))}",
                            fontSize = 11.sp,
                            color = DeepSageGreen,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = if (isTamil) "வாடிக்கையாளர், வசூலித்தவர் அல்லது குறிப்பைத் தேடு..." else "Search customer, collector or note...",
                                fontSize = 13.sp,
                                color = TextMutedDark
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_payments_input"),
                        shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = DeepSageGreen,
                            unfocusedBorderColor = SoftSageGreen,
                            cursorColor = DeepSageGreen,
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        ),
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = null,
                                tint = DeepSageGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear",
                                        tint = TextMutedDark,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else null
                    )

                    // Dropdowns for Collected By and Tractor
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Collector Dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SoftSageGreen.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, DeepSageGreen.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { collectorDropdownExpanded = true }
                                    .testTag("filter_collector_dropdown")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val currentCollectorLabel = if (selectedCollectorFilter == null) {
                                        if (isTamil) "வசூலித்தவர்: அனைத்தும்" else "Collected By: All"
                                    } else {
                                        val name = uniqueCollectors.find { it.first == selectedCollectorFilter }?.second ?: "Collector"
                                        if (isTamil) "வசூலித்தவர்: $name" else "By: $name"
                                    }
                                    Text(
                                        text = currentCollectorLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ForestGreenHeader,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = DeepSageGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = collectorDropdownExpanded,
                                onDismissRequest = { collectorDropdownExpanded = false },
                                modifier = Modifier.background(Color.White)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (isTamil) "அனைத்து வசூலிப்பாளர்கள்" else "All Collectors", fontSize = 13.sp) },
                                    onClick = {
                                        selectedCollectorFilter = null
                                        collectorDropdownExpanded = false
                                    }
                                )
                                uniqueCollectors.forEach { (uid, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name, fontSize = 13.sp) },
                                        onClick = {
                                            selectedCollectorFilter = uid
                                            collectorDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        // Tractor Dropdown
                        Box(modifier = Modifier.weight(1f)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = SoftSageGreen.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, DeepSageGreen.copy(alpha = 0.15f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { tractorDropdownExpanded = true }
                                    .testTag("filter_tractor_dropdown")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val currentTractorLabel = if (selectedTractorFilter == null) {
                                        if (isTamil) "டிராக்டர்: அனைத்தும்" else "Tractor: All"
                                    } else {
                                        val label = uniqueTractors.find { it.first == selectedTractorFilter }?.second ?: "Tractor"
                                        if (isTamil) "டிராக்டர்: $label" else "Tractor: $label"
                                    }
                                    Text(
                                        text = currentTractorLabel,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = ForestGreenHeader,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = null,
                                        tint = DeepSageGreen,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = tractorDropdownExpanded,
                                onDismissRequest = { tractorDropdownExpanded = false },
                                modifier = Modifier.background(Color.White)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (isTamil) "அனைத்து டிராக்டர்கள்" else "All Tractors", fontSize = 13.sp) },
                                    onClick = {
                                        selectedTractorFilter = null
                                        tractorDropdownExpanded = false
                                    }
                                )
                                uniqueTractors.forEach { (tid, label) ->
                                    DropdownMenuItem(
                                        text = { Text(label, fontSize = 13.sp) },
                                        onClick = {
                                            selectedTractorFilter = tid
                                            tractorDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Method filter horizontal row
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val filterOptions = listOf(
                            "All" to (if (isTamil) "அனைத்தும்" else "All"),
                            "Cash" to (if (isTamil) "ரொக்கம்" else "Cash"),
                            "UPI" to "UPI",
                            "Bank" to (if (isTamil) "வங்கி" else "Bank"),
                            "Cheque" to (if (isTamil) "காசோலை" else "Cheque")
                        )
                        items(filterOptions) { (key, label) ->
                            val isSelected = selectedMethodFilter == key
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) DeepSageGreen else SoftSageGreen.copy(alpha = 0.5f),
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (isSelected) DeepSageGreen else DeepSageGreen.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier
                                    .clickable { selectedMethodFilter = key }
                                    .testTag("filter_chip_$key")
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else ForestGreenHeader,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        if (displayPayments.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SageCardBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = TextMutedDark,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isTamil) "வசூல் பதிவுகள் எதுவும் இல்லை" else "No Collection Records Found",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondaryDark
                        )
                    }
                }
            }
        } else if (filteredPayments.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SageCardBg)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            tint = TextMutedDark,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isTamil) "பொருந்தும் வசூல் பதிவுகள் எதுவும் இல்லை" else "No Matching Collection Records Found",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondaryDark
                        )
                    }
                }
            }
        } else {
            items(visibleJobHistory, key = { it.key }) { group ->
                CollectionJobCard(group, filteredGroups.getValue(group.key), settings,
                    workspaceMembers, partners, ownerName, isTamil, isOwner, onDeleteCollection)
            }
        }
    }
}

internal data class AuthorizedCollectionSource(
    val payments: List<PaymentEntity>,
    val legacyJobs: List<JobEntryEntity>
)

/** Builds the single authorized dataset shared by Collection History summary and detail. */
internal fun authorizedCollectionSource(
    workspaceId: String,
    payments: List<PaymentEntity>,
    jobs: List<JobEntryEntity>,
    workspaceMembers: List<WorkspaceMember>,
    isOwner: Boolean,
    currentUid: String
): AuthorizedCollectionSource {
    if (!isOwner && workspaceMembers.none {
            it.uid == currentUid && it.workspaceId == workspaceId &&
                it.status.equals("active", ignoreCase = true) &&
                RoleUtils.normalizeRole(it.role) == RoleUtils.ROLE_PARTNER
        }) return AuthorizedCollectionSource(emptyList(), emptyList())

    fun inWorkspace(recordWorkspaceId: String): Boolean =
        workspaceId.isNotBlank() && recordWorkspaceId == workspaceId

    val scopedPayments = payments.filter {
        inWorkspace(it.workspaceId)
    }
    // An upgraded workspace can contain both entry-only receipts and Collections.
    // Subtract only receipts explicitly allocated to this Entry, never a different
    // customer's payment or a reversed event. Do not invent dates for missing history:
    // the remaining legacy amount retains its original Entry date.
    val scopedLegacyJobs = reconciledLegacyReceiptJobs(workspaceId, scopedPayments, jobs)
    return AuthorizedCollectionSource(scopedPayments, scopedLegacyJobs)
}

private data class PaymentDisplayModel(
    val id: Long,
    val groupKey: String,
    val originalAmount: Double,
    val isSynced: Boolean,
    val customerName: String,
    val amount: Double,
    val paymentMethod: String,
    val collectedAt: Long,
    val collectorUid: String,
    val collectorName: String,
    val collectorRole: String,
    val notes: String,
    val tractorId: Long = 0L,
    val tractorLabel: String = "",
    val status: String = "ACTIVE",
    val jobEntryId: Long = 0,
    val reversedAt: Long = 0,
    val reversedByUid: String = "",
    val canReverse: Boolean = false,
    val recordedByName: String = "",
    val recordedByUid: String = ""
)

@Composable
private fun CollectionHistoryCard(
    payment: PaymentDisplayModel,
    settings: AppSettingsEntity,
    workspaceMembers: List<WorkspaceMember>,
    partners: List<PartnerEntity>,
    ownerName: String,
    isTamil: Boolean,
    isOwner: Boolean = false,
    onDeleteCollection: ((Long) -> Unit)? = null
) {
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showDetails by remember(payment.id) { mutableStateOf(false) }

    if (showDetails) {
        val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).apply { timeZone = com.example.data.auth.CollectionPolicy.timeZone } }
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text(if (isTamil) "வசூல் விவரங்கள்" else "Collection Details", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${if (isTamil) "நிலை" else "Status"}: ${payment.status}")
                    Text("${if (isTamil) "வாடிக்கையாளர்" else "Customer"}: ${payment.customerName}")
                    Text("${if (isTamil) "தொகை" else "Amount"}: ${formatInr(payment.originalAmount, settings.currency)}")
                    Text("${if (isTamil) "தேதி" else "Collected"}: ${dateFormat.format(Date(payment.collectedAt))}")
                    Text("${if (isTamil) "வசூல் செய்தவர்" else "Collected by"}: ${payment.collectorName}")
                    if (payment.recordedByUid.isNotBlank() && payment.recordedByUid != payment.collectorUid)
                        Text("${if (isTamil) "பதிவு செய்தவர்" else "Recorded by"}: ${payment.recordedByName}")
                    if (payment.originalAmount != payment.amount) Text("Allocated to this job: ${formatInr(payment.amount, settings.currency)}")
                    if (payment.status == "REVERSED") {
                        if (payment.reversedAt > 0) Text("${if (isTamil) "திரும்பப் பெற்ற தேதி" else "Reversed"}: ${dateFormat.format(Date(payment.reversedAt))}")
                        if (payment.reversedByUid.isNotBlank()) Text("${if (isTamil) "திரும்பப் பெற்றவர்" else "Reversed by"}: ${payment.reversedByUid}")
                    }
                    if (payment.notes.isNotBlank()) Text("${if (isTamil) "குறிப்பு" else "Note"}: ${payment.notes}")
                }
            },
            confirmButton = { TextButton(onClick = { showDetails = false }) { Text(if (isTamil) "மூடு" else "Close") } },
            containerColor = Color.White
        )
    }

    if (showDeleteDialog) {
        val dateStr = remember(payment.collectedAt) {
            java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).apply { timeZone = com.example.data.auth.CollectionPolicy.timeZone }.format(java.util.Date(payment.collectedAt))
        }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = if (isTamil) "வசூலைத் திரும்பப் பெறவா?" else "Reverse Collection?",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${if (isTamil) "வாடிக்கையாளர்" else "Customer"}: ${payment.customerName}",
                        fontSize = 14.sp,
                        color = Color(0xFF334155)
                    )
                    Text(
                        text = "${if (isTamil) "தொகை" else "Amount"}: +${com.example.ui.components.formatInr(payment.originalAmount, settings.currency)}",
                        fontSize = 14.sp,
                        color = com.example.ui.theme.SuccessPaidGreen,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${if (isTamil) "தேதி" else "Date"}: $dateStr",
                        fontSize = 14.sp,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                    text = if (isTamil) "இந்த வசூல் திரும்பப் பெறப்பட்டு, வாடிக்கையாளர் நிலுவை மீண்டும் கணக்கிடப்படும். வேலைப் பதிவு நீக்கப்படாது." else "This entire collection, including all job allocations, will be reversed and the customer's due recalculated. The job entries will not be deleted.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteCollection?.invoke(payment.id)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = com.example.ui.theme.AlertDueRed)
                ) {
                    Text(
                        text = if (isTamil) "திரும்பப் பெறு" else "Reverse",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(if (isTamil) "ரத்து செய்" else "Cancel")
                }
            },
            containerColor = Color.White
        )
    }
    val dateTimeStr = remember(payment.collectedAt) {
        val date = Date(payment.collectedAt)
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).apply { timeZone = com.example.data.auth.CollectionPolicy.timeZone }
        if (payment.collectedAt > 0) sdf.format(date) else "Date unavailable"
    }

    val collectorDisplay = remember(payment, workspaceMembers, partners, ownerName, isTamil) {
        val role = RoleUtils.normalizeRole(payment.collectorRole)
        val name = payment.collectorName.ifBlank { "Owner" }
        RoleUtils.formatRoleAndName(role, name, isTamil)
    }

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 8.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(DeepSageGreen))
            Box(Modifier.width(1.dp).height(84.dp).background(SoftSageGreen))
        }
        Column(
            modifier = Modifier.weight(1f).clickable { showDetails = true }
                .testTag("collection_history_card_${payment.id}").padding(vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Top Row: Customer Name, Amount & (Owner) Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (payment.isSynced) (if (isTamil) "வசூல்" else "Payment") else
                    (if (isTamil) "ஒத்திசைவு நிலுவையில்" else "Pending sync"), fontSize = 12.sp, color = TextSecondaryDark,
                    modifier = Modifier.weight(1f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${if (payment.status == "ACTIVE") "+" else ""}${formatInr(payment.amount, settings.currency)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = if (payment.status == "REVERSED") TextSecondaryDark else SuccessPaidGreen
                    )
                    if (payment.status == "REVERSED") {
                        Text("REVERSED", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            color = com.example.ui.theme.AlertDueRed,
                            modifier = Modifier.testTag("reversed_collection_${payment.id}"))
                    }
                    if (payment.canReverse && onDeleteCollection != null) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("reverse_collection_${payment.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = if (isTamil) "வசூலைத் திரும்பப் பெறு" else "Reverse Collection",
                                tint = com.example.ui.theme.AlertDueRed,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Middle Row: Payment Method Chip & Date/Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SoftSageGreen
                ) {
                    Text(
                        text = payment.paymentMethod.ifBlank { "Cash" },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DeepSageGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = dateTimeStr,
                    fontSize = 11.sp,
                    color = TextMutedDark
                )
            }

            // Bottom Row: Collected By Attribution
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = DeepSageGreen,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "${if (isTamil) "வசூல் செய்தவர்" else "Collected by"}: $collectorDisplay",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondaryDark
                )
            }

            if (payment.notes.isNotBlank() && !payment.notes.startsWith("Payment Method:")) {
                Text(
                    text = "${if (isTamil) "குறிப்பு" else "Note"}: ${payment.notes}",
                    fontSize = 11.sp,
                    color = TextMutedDark,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun CollectionJobCard(
    group: CollectionJobHistory, events: List<PaymentDisplayModel>, settings: AppSettingsEntity,
    workspaceMembers: List<WorkspaceMember>, partners: List<PartnerEntity>, ownerName: String,
    isTamil: Boolean, isOwner: Boolean, onDeleteCollection: ((Long) -> Unit)?,
    initiallyExpanded: Boolean = false
) {
    var isExpanded by rememberSaveable(group.key) { mutableStateOf(initiallyExpanded) }
    val job = group.job
    val totals = remember(job) { job?.let {
        com.example.ui.util.FinancialCalculationEngine.calculateSummary(listOf(it), emptyList(), emptyList(),
            com.example.ui.util.FinancialScope(workspaceId = it.workspaceId))
    } }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).apply {
        timeZone = com.example.data.auth.CollectionPolicy.timeZone
    } }
    val identifierTag = group.jobId?.toString() ?: events.firstOrNull()?.id?.toString() ?: group.key

    Card(Modifier.fillMaxWidth().testTag("collection_job_card_${group.key}"), shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SageCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Compact Header Row: Customer Name & Work Type + Expand/Collapse Control
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("collection_card_header_${group.key}"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = job?.customerName?.ifBlank { null } ?: events.first().customerName,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenHeader,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (group.jobId != null) job?.workType?.takeIf { it.isNotBlank() }
                            ?: if (isTamil) "வேலை" else "Job"
                            else if (isTamil) "இணைக்கப்படாத வசூல்" else "Unlinked collection",
                        fontSize = 12.sp,
                        color = TextSecondaryDark
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "${events.size} ${if (events.size == 1) (if (isTamil) "பணம்" else "payment") else (if (isTamil) "பணங்கள்" else "payments")}",
                        fontSize = 11.sp,
                        color = TextMutedDark
                    )
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("expand_job_card_$identifierTag")
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse collection history" else "Expand collection history",
                            tint = DeepSageGreen
                        )
                    }
                }
            }

            // Created Date & Sync Status
            Text(
                text = "${if (isTamil) "உருவாக்கப்பட்டது" else "Created"}: ${job?.createdAt?.takeIf { it > 0 }?.let { dateFormat.format(Date(it)) } ?: "—"}",
                fontSize = 11.sp,
                color = TextMutedDark
            )
            if (job?.isSynced == false) {
                Text(
                    text = if (isTamil) "ஒத்திசைக்காத மாற்றங்கள் உள்ளன" else "Includes pending changes",
                    fontSize = 11.sp,
                    color = TextSecondaryDark
                )
            }

            // Summary Totals: Billed, Collected, Outstanding (always visible in compact state)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(if (isTamil) "மொத்தம்" else "Billed", fontSize = 11.sp, color = TextSecondaryDark)
                    Text(totals?.let { formatInr(it.totalRecorded, settings.currency) } ?: "—", fontWeight = FontWeight.Bold)
                }
                Column(Modifier.weight(1f)) {
                    Text(if (isTamil) "வசூலானது" else "Collected", fontSize = 11.sp, color = TextSecondaryDark)
                    Text(totals?.let { formatInr(it.totalReceived, settings.currency) } ?: "—", fontWeight = FontWeight.Bold, color = SuccessPaidGreen)
                }
                Column(Modifier.weight(1f)) {
                    Text(if (isTamil) "நிலுவை" else "Outstanding", fontSize = 11.sp, color = TextSecondaryDark)
                    Text(totals?.let { formatInr(it.totalDue, settings.currency) } ?: "—", fontWeight = FontWeight.Bold, color = com.example.ui.theme.AlertDueRed)
                }
            }
            if (job == null) {
                Text(
                    text = if (isTamil) "வேலை விவரங்கள் கிடைக்கவில்லை; வசூல் வரலாறு தக்கவைக்கப்பட்டுள்ளது." else
                        "Job details unavailable; payment history retained.",
                    fontSize = 11.sp,
                    color = TextMutedDark
                )
            }
            if (group.allocationNeedsReview) {
                Text(
                    text = "Payment allocation unavailable; original collection retained.",
                    fontSize = 11.sp,
                    color = com.example.ui.theme.AlertDueRed
                )
            }

            // Expanded Payment Timeline Section (only displayed when expanded)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(
                        color = DeepSageGreen.copy(alpha = 0.25f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Text(
                        text = if (isTamil) "கட்டண காலவரிசை" else "Payment timeline · oldest first",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DeepSageGreen
                    )
                    events.forEach { payment ->
                        androidx.compose.runtime.key(payment.id) {
                            CollectionHistoryCard(payment, settings, workspaceMembers, partners, ownerName, isTamil, isOwner, onDeleteCollection)
                        }
                    }
                }
            }
        }
    }
}
