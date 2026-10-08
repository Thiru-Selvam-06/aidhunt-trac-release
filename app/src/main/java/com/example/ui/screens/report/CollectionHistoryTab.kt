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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    ownerName: String = "",
    actorUid: String = "",
    currentUserRole: String = "",
    onDeleteCollection: ((Long, String, Long) -> Unit)? = null
) {
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    val context = LocalContext.current
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()

    // Consolidate payment records.
    // 1. Explicit PaymentEntity records are authoritative.
    // 2. Legacy/standalone synthetic payment jobs not yet in PaymentEntity are included.
    // 3. Initial advance payments recorded upon job creation are included.
    val displayPayments: List<PaymentDisplayModel> = remember(payments, jobs, workspaceMembers, partners, ownerName) {
        val list = mutableListOf<PaymentDisplayModel>()
        val seenPaymentIds = mutableSetOf<Long>()
        val seenJobEntryIds = mutableSetOf<Long>()

        // 1. Authoritative explicit payment entities
        payments.forEach { p ->
            seenPaymentIds.add(p.id)
            if (p.jobEntryId > 0L) {
                seenJobEntryIds.add(p.jobEntryId)
            }
            list.add(
                PaymentDisplayModel(
                    id = p.id,
                    customerName = p.customerName.ifBlank { "Customer" },
                    amount = p.amount,
                    paymentMethod = p.paymentMethod,
                    collectedAt = p.collectedAt,
                    collectorUid = p.collectedByUid,
                    collectorName = p.collectedByName,
                    collectorRole = p.collectedByRole,
                    notes = p.notes,
                    tractorId = p.tractorId,
                    tractorLabel = p.tractorLabel,
                    isExplicitPayment = true,
                    createdAt = if (p.createdAt > 0L) p.createdAt else p.collectedAt
                )
            )
        }

        // 2. Standalone synthetic payment jobs not backed by explicit PaymentEntity
        val syntheticJobs = jobs.filter { com.example.ui.util.FinancialCalculationEngine.isSyntheticPayment(it) && it.amountReceived > 0.0 }
        syntheticJobs.forEach { j ->
            val isAlreadyRepresented = seenJobEntryIds.contains(j.id) || 
                seenPaymentIds.contains(j.id) ||
                payments.any { p ->
                    p.customerId == j.customerId &&
                    p.amount == j.amountReceived &&
                    (p.jobEntryId == j.id || kotlin.math.abs(p.collectedAt - (j.startTimeMillis.takeIf { t -> t > 0 } ?: j.createdAt)) < 120_000L)
                }
            if (!isAlreadyRepresented) {
                val (resolvedRole, resolvedName) = RoleUtils.resolveCreatorRoleAndName(j, ownerName, workspaceMembers, partners)
                list.add(
                    PaymentDisplayModel(
                        id = j.id,
                        customerName = j.customerName.ifBlank { "Customer" },
                        amount = j.amountReceived,
                        paymentMethod = extractPaymentMethodFromNotes(j.notes),
                        collectedAt = j.startTimeMillis.takeIf { it > 0 } ?: j.createdAt,
                        collectorUid = j.createdByUid,
                        collectorName = resolvedName,
                        collectorRole = j.createdByRole.ifBlank { resolvedRole },
                        notes = j.notes,
                        tractorId = j.tractorId,
                        tractorLabel = j.tractorLabel,
                        isExplicitPayment = false
                    )
                )
            }
        }

        // 3. Initial collections at job creation time for work jobs
        val workJobsWithReceived = jobs.filter { !com.example.ui.util.FinancialCalculationEngine.isSyntheticPayment(it) && it.amountReceived > 0.0 }
        workJobsWithReceived.forEach { j ->
            // Correctly attribute the initial collection to the person who created the job
            val (resolvedRole, resolvedName) = RoleUtils.resolveCreatorRoleAndName(j, ownerName, workspaceMembers, partners)
            
            // Subtract all explicit payments linked to this job to find the true initial advance
            val explicitPaymentsForJob = payments.filter { it.jobEntryId == j.id }.sumOf { it.amount }
            val initialAdvance = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(j.amountReceived - explicitPaymentsForJob)
            
            if (initialAdvance > 0.0) {
                list.add(
                    PaymentDisplayModel(
                        // Use a negative ID for initial advance cards to ensure they never collide with
                        // explicit payment IDs (which are positive random longs) or synthetic job IDs.
                        id = -j.id,
                        customerName = j.customerName.ifBlank { "Customer" },
                        amount = initialAdvance,
                        paymentMethod = extractPaymentMethodFromNotes(j.notes),
                        collectedAt = j.startTimeMillis.takeIf { it > 0 } ?: j.createdAt,
                        collectorUid = j.createdByUid,
                        collectorName = resolvedName,
                        collectorRole = j.createdByRole.ifBlank { resolvedRole },
                        notes = j.notes,
                        tractorId = j.tractorId,
                        tractorLabel = j.tractorLabel,
                        isExplicitPayment = false
                    )
                )
            }
        }

        list.sortedByDescending { it.collectedAt }
    }

    var selectedMethodFilter by remember { mutableStateOf("All") }
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

    val filteredPayments = remember(displayPayments, selectedMethodFilter, selectedDatePreset, customDateMillis, searchQuery, selectedCollectorFilter, selectedTractorFilter) {
        displayPayments.filter { payment ->
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
                DatePreset.TODAY -> isDateInPreset(payment.collectedAt, DatePreset.TODAY)
                DatePreset.THIS_MONTH -> isDateInPreset(payment.collectedAt, DatePreset.THIS_MONTH)
                DatePreset.CUSTOM -> {
                    val calP = Calendar.getInstance().apply { timeInMillis = payment.collectedAt }
                    val calCustom = Calendar.getInstance().apply { timeInMillis = customDateMillis }
                    calP.get(Calendar.YEAR) == calCustom.get(Calendar.YEAR) &&
                    calP.get(Calendar.DAY_OF_YEAR) == calCustom.get(Calendar.DAY_OF_YEAR)
                }
                else -> isDateInPreset(payment.collectedAt, selectedDatePreset)
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

            matchesSearch && matchesMethod && matchesDate && matchesCollector && matchesTractor
        }
    }

    val totalCollected = filteredPayments.sumOf { it.amount }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
                            text = if (isTamil) "${filteredPayments.size} கட்டணங்கள்" else "${filteredPayments.size} Payments",
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
                                        val cal = Calendar.getInstance().apply { timeInMillis = customDateMillis }
                                        DatePickerDialog(
                                            context,
                                            { _, y, m, d ->
                                                val nc = Calendar.getInstance().apply { set(y, m, d) }
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
                            text = "${if (isTamil) "தேர்ந்தெடுக்கப்பட்ட தேதி:" else "Selected Date:"} ${formatDate(customDateMillis)}",
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
            items(filteredPayments, key = { it.id }) { item ->
                CollectionHistoryCard(
                    payment = item,
                    settings = settings,
                    workspaceMembers = workspaceMembers,
                    partners = partners,
                    ownerName = ownerName,
                    isTamil = isTamil,
                    isOwner = isOwner,
                    actorUid = actorUid,
                    currentUserRole = currentUserRole,
                    onDeleteCollection = onDeleteCollection
                )
            }
        }
    }
}

private data class PaymentDisplayModel(
    val id: Long,
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
    val isExplicitPayment: Boolean = true,
    val createdAt: Long = collectedAt
)

private fun extractPaymentMethodFromNotes(notes: String): String {
    return when {
        notes.contains("UPI", ignoreCase = true) || notes.contains("GPay", ignoreCase = true) -> "UPI / GPay"
        notes.contains("Bank", ignoreCase = true) || notes.contains("Transfer", ignoreCase = true) -> "Bank Transfer"
        notes.contains("Cheque", ignoreCase = true) -> "Cheque"
        else -> "Cash"
    }
}

@Composable
private fun CollectionHistoryCard(
    payment: PaymentDisplayModel,
    settings: AppSettingsEntity,
    workspaceMembers: List<WorkspaceMember>,
    partners: List<PartnerEntity>,
    ownerName: String,
    isTamil: Boolean,
    isOwner: Boolean = false,
    actorUid: String = "",
    currentUserRole: String = "",
    onDeleteCollection: ((Long, String, Long) -> Unit)? = null
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        val dateStr = remember(payment.collectedAt) {
            java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(payment.collectedAt))
        }
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(
                    text = if (isTamil) "வசூலை நீக்கவா?" else "Delete Collection?",
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
                        text = "${if (isTamil) "தொகை" else "Amount"}: +${com.example.ui.components.formatInr(payment.amount, settings.currency)}",
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
                        text = if (isTamil) "இந்த வசூல் பதிவு நிரந்தரமாக நீக்கப்படும். வேலைப் பதிவு நீக்கப்படாது." else "This collection record will be permanently removed. The job entry itself will not be deleted.",
                        fontSize = 12.sp,
                        color = Color(0xFF64748B)
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteCollection?.invoke(payment.id, payment.collectorUid, payment.collectedAt)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = com.example.ui.theme.AlertDueRed)
                ) {
                    Text(
                        text = if (isTamil) "நீக்கு" else "Delete",
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
        val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
        sdf.format(date)
    }

    val collectorDisplay = remember(payment, workspaceMembers, partners, ownerName, isTamil) {
        val role = RoleUtils.normalizeRole(payment.collectorRole)
        val name = payment.collectorName.ifBlank { "Owner" }
        RoleUtils.formatRoleAndName(role, name, isTamil)
    }

    var showDetailDialog by remember { mutableStateOf(false) }

    if (showDetailDialog) {
        AlertDialog(
            onDismissRequest = { showDetailDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Paid,
                        contentDescription = null,
                        tint = SuccessPaidGreen,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isTamil) "வசூல் விவரங்கள்" else "Collection Details",
                        fontWeight = FontWeight.Bold,
                        color = ForestGreenHeader,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = if (isTamil) "வாடிக்கையாளர்:" else "Customer:", color = Color(0xFF64748B), fontSize = 13.sp)
                        Text(text = payment.customerName, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A), fontSize = 13.5.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = if (isTamil) "தொகை:" else "Amount:", color = Color(0xFF64748B), fontSize = 13.sp)
                        Text(text = "+${formatInr(payment.amount, settings.currency)}", fontWeight = FontWeight.Bold, color = SuccessPaidGreen, fontSize = 15.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = if (isTamil) "தேதி & நேரம்:" else "Date & Time:", color = Color(0xFF64748B), fontSize = 13.sp)
                        Text(text = dateTimeStr, color = Color(0xFF334155), fontSize = 13.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = if (isTamil) "பেমென்ட் முறை:" else "Payment Method:", color = Color(0xFF64748B), fontSize = 13.sp)
                        Text(text = payment.paymentMethod.ifBlank { "Cash" }, fontWeight = FontWeight.SemiBold, color = DeepSageGreen, fontSize = 13.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = if (isTamil) "வசூலித்தவர்:" else "Collected By:", color = Color(0xFF64748B), fontSize = 13.sp)
                        Text(text = collectorDisplay, color = Color(0xFF334155), fontSize = 13.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = if (isTamil) "பதிவு வகை:" else "Record Type:", color = Color(0xFF64748B), fontSize = 13.sp)
                        Text(text = if (payment.isExplicitPayment) (if (isTamil) "நேரடி வசூல்" else "Direct Payment") else (if (isTamil) "வேலை முன்பணம்" else "Job Advance/Collection"), color = Color(0xFF334155), fontSize = 13.sp)
                    }
                    if (payment.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = if (isTamil) "குறிப்புகள்:" else "Notes:", color = Color(0xFF64748B), fontSize = 13.sp)
                        Text(text = payment.notes, color = Color(0xFF334155), fontSize = 13.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDetailDialog = false }) {
                    Text(text = if (isTamil) "மூடு" else "Close", fontWeight = FontWeight.Bold, color = ForestGreenHeader)
                }
            },
            containerColor = Color.White
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { showDetailDialog = true }
            .testTag("collection_history_card_${payment.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SageCardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Top Row: Customer Name, Amount & (Owner/Authorized Partner) Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SuccessPaidGreenBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Paid,
                            contentDescription = null,
                            tint = SuccessPaidGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = payment.customerName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ForestGreenHeader
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "+${formatInr(payment.amount, settings.currency)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = SuccessPaidGreen
                    )
                    val canDelete = when {
                        onDeleteCollection == null -> false
                        else -> com.example.data.auth.AuthorizationManager.canDeleteCollection(
                            collectionCreatedByUid = payment.collectorUid,
                            collectionCreatedAt = payment.createdAt,
                            isOwner = isOwner,
                            currentUid = actorUid.ifBlank { com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "" },
                            role = currentUserRole
                        )
                    }
                    if (canDelete) {
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("delete_collection_${payment.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = if (isTamil) "வசூலை நீக்கு" else "Delete Collection",
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
