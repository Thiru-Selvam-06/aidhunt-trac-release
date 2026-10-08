
package com.example.ui.screens.report
import androidx.compose.foundation.ExperimentalFoundationApi
import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.ui.components.shareGenericText
import com.example.ui.theme.AppTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import com.example.ui.utils.trackFocusedField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Paid
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.compose.foundation.text.KeyboardOptions
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.JobEntryEntity
import com.example.pdf.PdfGeneratorHelper
import com.example.ui.components.DetailRow
import com.example.ui.components.FlatPdfIconButton
import com.example.ui.components.FlatShareIconButton
import com.example.ui.components.PdfOptionsDialog
import com.example.ui.components.StatusBadge
import com.example.ui.components.buildCustomerDueWhatsAppMessage
import com.example.ui.components.buildJobWhatsAppMessage
import com.example.ui.screens.home.CustomerSummaryPopup
import com.example.ui.components.formatDate
import com.example.ui.components.formatInr
import com.example.ui.components.formatWhatsAppPhone
import com.example.ui.components.isValidPhoneNumber
import com.example.ui.components.openDialer
import com.example.ui.components.openWhatsApp
import com.example.ui.components.sanitizePhoneNumberForStorage
import com.example.ui.components.sendWhatsAppMessage
import com.example.ui.theme.AlertDueRed
import com.example.ui.theme.AlertDueRedBg
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
import java.util.Calendar
import java.util.Locale
enum class CreditDueSortOrder(val label: String) {
    HIGH_TO_LOW("High to Low"),
    LOW_TO_HIGH("Low to High"),
    RECENT("Recent")
}
enum class CreditDueStatusFilter(val label: String) {
    ALL("All"),
    PENDING_DUE("Pending Due"),
    PAID("Fully Paid")
}
enum class CreditDueDateFilter(val label: String) {
    ALL("All Time"),
    TODAY("Today"),
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    LAST_30_DAYS("Last 30 Days"),
    THIS_YEAR("This Year"),
    CUSTOM("📅 Pick Dates")
}
fun getLocalizedSortOrder(order: CreditDueSortOrder, isTamil: Boolean): String {
    if (!isTamil) return order.label
    return when (order) {
        CreditDueSortOrder.HIGH_TO_LOW -> "அதிகம் முதல் குறைவு"
        CreditDueSortOrder.LOW_TO_HIGH -> "குறைவு முதல் அதிகம்"
        CreditDueSortOrder.RECENT -> "சமீபத்திய"
    }
}
fun getLocalizedStatusFilter(filter: CreditDueStatusFilter, isTamil: Boolean): String {
    if (!isTamil) return filter.label
    return when (filter) {
        CreditDueStatusFilter.ALL -> "அனைத்தும்"
        CreditDueStatusFilter.PENDING_DUE -> "நிலுவை உள்ளவை"
        CreditDueStatusFilter.PAID -> "முழுமையாக செலுத்தியவை"
    }
}
fun getLocalizedDateFilter(filter: CreditDueDateFilter, isTamil: Boolean): String {
    if (!isTamil) return filter.label
    return when (filter) {
        CreditDueDateFilter.ALL -> "அனைத்து காலம்"
        CreditDueDateFilter.TODAY -> "இன்று"
        CreditDueDateFilter.THIS_WEEK -> "இந்த வாரம்"
        CreditDueDateFilter.THIS_MONTH -> "இந்த மாதம்"
        CreditDueDateFilter.LAST_30_DAYS -> "கடந்த 30 நாட்கள்"
        CreditDueDateFilter.THIS_YEAR -> "இந்த ஆண்டு"
        CreditDueDateFilter.CUSTOM -> "📅 தேதியைத் தேர்ந்தெடு"
    }
}
fun getLocalizedPaymentMethod(method: String, isTamil: Boolean): String {
    if (!isTamil) return method
    return when (method) {
        "Cash" -> "ரொக்கம்"
        "UPI / GPay" -> "யுபிஐ / ஜிபே"
        "Bank Transfer" -> "வங்கி கணக்கு"
        "Cheque" -> "காசோலை"
        else -> method
    }
}
@Composable
fun CustomerCreditDueTab(
    settings: AppSettingsEntity,
    customers: List<CustomerEntity>,
    jobs: List<JobEntryEntity>,
    onUpdateCustomer: ((CustomerEntity) -> Unit)? = null,
    onRecordPayment: ((CustomerEntity, Double, Long, String, String) -> Unit)? = null,
    onRecordPaymentWithCollector: ((CustomerEntity, Double, Long, String, String, String, String, String) -> Unit)? = null,
    onEditJob: ((JobEntryEntity) -> Unit)? = null,
    onDeleteJob: ((JobEntryEntity) -> Unit)? = null,
    onDeleteCustomer: ((CustomerEntity) -> Unit)? = null,
    isOwner: Boolean = false,
    actorName: String = "",
    workspaceMembers: List<com.example.data.firebase.WorkspaceMember> = emptyList(),
    partners: List<com.example.data.entity.PartnerEntity> = emptyList(),
    expenses: List<com.example.data.entity.ExpenseEntity> = emptyList()
) {
    val context = LocalContext.current
    val isTamil = settings.language == "TA"
    val coroutineScope = rememberCoroutineScope()
    val searchRequester = remember { BringIntoViewRequester() }
    val amountRequester = remember { BringIntoViewRequester() }
    val dialogAmountRequester = remember { BringIntoViewRequester() }
    val dialogNotesRequester = remember { BringIntoViewRequester() }
    var searchQuery by remember { mutableStateOf("") }
    var selectedSortOrder by remember { mutableStateOf(CreditDueSortOrder.HIGH_TO_LOW) }
    var selectedStatusFilter by remember { mutableStateOf(CreditDueStatusFilter.PENDING_DUE) }
    var selectedDateFilter by remember { mutableStateOf(CreditDueDateFilter.ALL) }
    var isFilterExpanded by remember { mutableStateOf(false) }
    var customStartDateMillis by remember {
        mutableLongStateOf(Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -30) }.timeInMillis)
    }
    var customEndDateMillis by remember {
        mutableLongStateOf(Calendar.getInstance().timeInMillis)
    }
    var selectedCustomer by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerToEditPhone by remember { mutableStateOf<CustomerEntity?>(null) }
    var customerForPayment by remember { mutableStateOf<CustomerEntity?>(null) }
    var showBulkPdfOptionsDialog by remember { mutableStateOf(false) }
    var pdfCustomerSelected by remember { mutableStateOf<CustomerEntity?>(null) }
    // Map each customer to their most recent job timestamp
    val customerRecentJobTime = remember(jobs) {
        val map = mutableMapOf<Long, Long>()
        val nameMap = mutableMapOf<String, Long>()
        jobs.forEach { job ->
            val prevTime = map[job.customerId] ?: 0L
            if (job.startTimeMillis > prevTime) {
                map[job.customerId] = job.startTimeMillis
            }
            val prevNameTime = nameMap[job.customerName.trim().lowercase()] ?: 0L
            if (job.startTimeMillis > prevNameTime) {
                nameMap[job.customerName.trim().lowercase()] = job.startTimeMillis
            }
        }
        Pair(map, nameMap)
    }
    // Determine active date range
    val (dateFilterStart, dateFilterEnd) = remember(selectedDateFilter, customStartDateMillis, customEndDateMillis) {
        val cal = Calendar.getInstance()
        when (selectedDateFilter) {
            CreditDueDateFilter.ALL -> Pair(0L, Long.MAX_VALUE)
            CreditDueDateFilter.TODAY -> {
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
            CreditDueDateFilter.THIS_WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
            CreditDueDateFilter.THIS_MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
            CreditDueDateFilter.LAST_30_DAYS -> {
                cal.add(Calendar.DAY_OF_YEAR, -30)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
            CreditDueDateFilter.THIS_YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, Long.MAX_VALUE)
            }
            CreditDueDateFilter.CUSTOM -> {
                Pair(customStartDateMillis, customEndDateMillis)
            }
        }
    }
    val synchronizedCustomers = remember(customers, jobs) {
        com.example.ui.util.FinancialCalculationEngine.synchronizeCustomers(customers, jobs)
    }
    val filteredCustomers = remember(
        synchronizedCustomers,
        jobs,
        searchQuery,
        selectedSortOrder,
        selectedStatusFilter,
        selectedDateFilter,
        dateFilterStart,
        dateFilterEnd
    ) {
        synchronizedCustomers.filter { c ->
            val matchesSearch = searchQuery.isBlank() ||
                    c.name.contains(searchQuery, ignoreCase = true) ||
                    c.phone.contains(searchQuery) ||
                    c.location.contains(searchQuery, ignoreCase = true)
            
            val matchesStatus = when (selectedStatusFilter) {
                CreditDueStatusFilter.ALL -> true
                CreditDueStatusFilter.PENDING_DUE -> com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(c.balanceDue) > 0.0
                CreditDueStatusFilter.PAID -> com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(c.balanceDue) <= 0.0
            }
            val matchesDate = if (selectedDateFilter == CreditDueDateFilter.ALL) {
                true
            } else {
                val hasJobInDate = jobs.any { j ->
                    (j.customerId == c.id || (c.phone.isNotBlank() && j.customerPhone == c.phone)) &&
                            j.startTimeMillis in dateFilterStart..dateFilterEnd
                }
                val hasCustomerUpdatedInDate = c.updatedAt in dateFilterStart..dateFilterEnd || c.createdAt in dateFilterStart..dateFilterEnd
                hasJobInDate || hasCustomerUpdatedInDate
            }
            matchesSearch && matchesStatus && matchesDate
        }.let { list ->
            when (selectedSortOrder) {
                CreditDueSortOrder.HIGH_TO_LOW -> list.sortedByDescending { it.balanceDue }
                CreditDueSortOrder.LOW_TO_HIGH -> list.sortedBy { it.balanceDue }
                CreditDueSortOrder.RECENT -> list.sortedByDescending {
                    customerRecentJobTime.first[it.id]
                        ?: customerRecentJobTime.second[it.name.trim().lowercase()]
                        ?: 0L
                }
            }
        }
    }
    val totalOutstanding = filteredCustomers.map { com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(it.balanceDue) }.filter { it > 0.0 }.sum()
    fun showCustomDatePickers() {
        val cal = Calendar.getInstance()
        cal.timeInMillis = customStartDateMillis
        DatePickerDialog(
            context,
            { _, sYear, sMonth, sDay ->
                val startCal = Calendar.getInstance().apply {
                    set(sYear, sMonth, sDay, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                customStartDateMillis = startCal.timeInMillis
                // Open End Date picker
                val endCalInit = Calendar.getInstance()
                endCalInit.timeInMillis = customEndDateMillis
                DatePickerDialog(
                    context,
                    { _, eYear, eMonth, eDay ->
                        val endCal = Calendar.getInstance().apply {
                            set(eYear, eMonth, eDay, 23, 59, 59)
                            set(Calendar.MILLISECOND, 999)
                        }
                        customEndDateMillis = endCal.timeInMillis
                        selectedDateFilter = CreditDueDateFilter.CUSTOM
                    },
                    endCalInit.get(Calendar.YEAR),
                    endCalInit.get(Calendar.MONTH),
                    endCalInit.get(Calendar.DAY_OF_MONTH)
                ).apply {
                    setTitle("Select End Date")
                }.show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            setTitle("Select Start Date")
        }.show()
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 16.dp,
            bottom = 100.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Bulk Export & Summary Banner
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = AlertDueRedBg),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AlertDueRed.copy(alpha = 0.3f))),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(if (isTamil) "மொத்த நிலுவைத் தொகை" else "Total Outstanding Dues", fontSize = 11.sp, color = AlertDueRed, fontWeight = FontWeight.SemiBold)
                        Text(formatInr(totalOutstanding, settings.currency), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = AlertDueRed)
                    }
                }
                OutlinedButton(
                    onClick = {
                        showBulkPdfOptionsDialog = true
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.CenterVertically).testTag("btn_bulk_export_dues")
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = AlertDueRed, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isTamil) "PDF அறிக்கை" else "Bulk Export", color = ForestGreenHeader)
                }
            }
        }
        // 2. Search Field
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(if (isTamil) "வாடிக்கையாளர் பெயர், மொபைல், ஊர்..." else "Search customer name, phone, village...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = SageAccent) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .trackFocusedField(searchRequester, coroutineScope)
                    .testTag("customer_due_search_input"),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = DeepSageGreen,
                    unfocusedBorderColor = SageOutline
                )
            )
        }
        // 3. Sorting & Filtering Controls (Collapsible by default)
        item {
            val activeFilterCount = (if (selectedStatusFilter != CreditDueStatusFilter.PENDING_DUE) 1 else 0) +
                (if (selectedSortOrder != CreditDueSortOrder.HIGH_TO_LOW) 1 else 0) +
                (if (selectedDateFilter != CreditDueDateFilter.ALL) 1 else 0)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SageCardBg),
                border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(SageOutline.copy(alpha = 0.6f))),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Header Toggle Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isFilterExpanded = !isFilterExpanded }
                            .testTag("btn_toggle_customer_due_filters"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = if (isTamil) "வடிகட்டிகள்" else "Filters",
                                tint = ForestGreenHeader,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = if (isTamil) "வடிகட்டுதல் & வரிசைப்படுத்துதல்" else "Filters & Sorting",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ForestGreenHeader
                            )
                            if (activeFilterCount > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = AlertDueRed,
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = activeFilterCount.toString(),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (activeFilterCount > 0 && isFilterExpanded) {
                                TextButton(
                                    onClick = {
                                        selectedStatusFilter = CreditDueStatusFilter.PENDING_DUE
                                        selectedSortOrder = CreditDueSortOrder.HIGH_TO_LOW
                                        selectedDateFilter = CreditDueDateFilter.ALL
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(14.dp), tint = SageAccent)
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(if (isTamil) "மீட்டமை" else "Reset", fontSize = 11.sp, color = SageAccent, fontWeight = FontWeight.Bold)
                                }
                            }
                            IconButton(
                                onClick = { isFilterExpanded = !isFilterExpanded },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = if (isFilterExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isFilterExpanded) {
                                        if (isTamil) "வடிகட்டிகளை சுருக்குக" else "Collapse Filters"
                                    } else {
                                        if (isTamil) "வடிகட்டிகளை விரிக்கவும்" else "Expand Filters"
                                    },
                                    tint = ForestGreenHeader
                                )
                            }
                        }
                    }
                    AnimatedVisibility(
                        visible = isFilterExpanded,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Divider(color = SageOutline.copy(alpha = 0.4f), thickness = 0.8.dp)
                            // Status Filter Row (All / Pending Due / Fully Paid)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.FilterList, contentDescription = null, tint = ForestGreenHeader, modifier = Modifier.size(16.dp))
                                    Text(if (isTamil) "நிலை:" else "Status:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreenHeader)
                                }
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(CreditDueStatusFilter.values().toList()) { statusFilter ->
                                        val isSelected = selectedStatusFilter == statusFilter
                                        val count = when (statusFilter) {
                                            CreditDueStatusFilter.ALL -> synchronizedCustomers.size
                                            CreditDueStatusFilter.PENDING_DUE -> synchronizedCustomers.count { com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(it.balanceDue) > 0.0 }
                                            CreditDueStatusFilter.PAID -> synchronizedCustomers.count { com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(it.balanceDue) <= 0.0 }
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) DeepSageGreen else Color.White,
                                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, SageOutline.copy(alpha = 0.6f)),
                                            modifier = Modifier.clickable { selectedStatusFilter = statusFilter }
                                        ) {
                                            Text(
                                                text = "${getLocalizedStatusFilter(statusFilter, isTamil)} ($count)",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) Color.White else ForestGreenHeader,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Divider(color = SageOutline.copy(alpha = 0.4f), thickness = 0.8.dp)
                            // Sort Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(Icons.Default.Sort, contentDescription = null, tint = ForestGreenHeader, modifier = Modifier.size(16.dp))
                                    Text(if (isTamil) "வரிசைப்படுத்து:" else "Sort:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreenHeader)
                                }
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(CreditDueSortOrder.values().toList()) { sortOrder ->
                                        val isSelected = selectedSortOrder == sortOrder
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) DeepSageGreen else Color.White,
                                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, SageOutline.copy(alpha = 0.6f)),
                                            modifier = Modifier.clickable { selectedSortOrder = sortOrder }
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                            ) {
                                                val icon = when (sortOrder) {
                                                    CreditDueSortOrder.HIGH_TO_LOW -> Icons.Default.ArrowDownward
                                                    CreditDueSortOrder.LOW_TO_HIGH -> Icons.Default.ArrowUpward
                                                    CreditDueSortOrder.RECENT -> Icons.Default.Schedule
                                                }
                                                Icon(
                                                    imageVector = icon,
                                                    contentDescription = null,
                                                    tint = if (isSelected) Color.White else DeepSageGreen,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                                Text(
                                                    text = getLocalizedSortOrder(sortOrder, isTamil),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (isSelected) Color.White else ForestGreenHeader
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Divider(color = SageOutline.copy(alpha = 0.4f), thickness = 0.8.dp)
                            // Date Filter Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.clickable { showCustomDatePickers() }
                                ) {
                                    Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar Picker", tint = ForestGreenHeader, modifier = Modifier.size(16.dp))
                                    Text(if (isTamil) "காலம்:" else "Calendar:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = ForestGreenHeader)
                                }
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    items(CreditDueDateFilter.values().toList()) { filter ->
                                        val isSelected = selectedDateFilter == filter
                                        val label = if (filter == CreditDueDateFilter.CUSTOM && selectedDateFilter == CreditDueDateFilter.CUSTOM) {
                                            "${formatDate(customStartDateMillis)} - ${formatDate(customEndDateMillis)}"
                                        } else {
                                            getLocalizedDateFilter(filter, isTamil)
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) DeepSageGreen else Color.White,
                                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, SageOutline.copy(alpha = 0.6f)),
                                            modifier = Modifier.clickable {
                                                if (filter == CreditDueDateFilter.CUSTOM) {
                                                    showCustomDatePickers()
                                                } else {
                                                    selectedDateFilter = filter
                                                }
                                            }
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) Color.White else ForestGreenHeader,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        // Active Calendar Date Filter Banner
        if (selectedDateFilter != CreditDueDateFilter.ALL) {
            item {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SoftSageGreen.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = DeepSageGreen, modifier = Modifier.size(16.dp))
                            Text(
                                text = if (selectedDateFilter == CreditDueDateFilter.CUSTOM) {
                                    if (isTamil) "நாட்காட்டி: ${formatDate(customStartDateMillis)} – ${formatDate(customEndDateMillis)}" else "Calendar: ${formatDate(customStartDateMillis)} – ${formatDate(customEndDateMillis)}"
                                } else {
                                    if (isTamil) "வடிகட்டி: ${getLocalizedDateFilter(selectedDateFilter, isTamil)}" else "Calendar Filter: ${selectedDateFilter.label}"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = DeepSageGreen
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(
                                onClick = { showCustomDatePickers() },
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(if (isTamil) "மாற்று" else "Change", fontSize = 11.sp, color = DeepSageGreen, fontWeight = FontWeight.Bold)
                            }
                            IconButton(
                                onClick = { selectedDateFilter = CreditDueDateFilter.ALL },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = if (isTamil) "அழி" else "Clear", tint = ForestGreenHeader, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
        }
        // 4. Customers Due List Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val headerTitle = when (selectedStatusFilter) {
                    CreditDueStatusFilter.ALL -> if (isTamil) "அனைத்து வாடிக்கையாளர்கள் (${filteredCustomers.size})" else "All Customer Accounts (${filteredCustomers.size})"
                    CreditDueStatusFilter.PENDING_DUE -> if (isTamil) "நிலுவைத் தொகை உள்ளவர்கள் (${filteredCustomers.size})" else "Customers with Outstanding Due (${filteredCustomers.size})"
                    CreditDueStatusFilter.PAID -> if (isTamil) "முழுமையாக செலுத்தியவர்கள் (${filteredCustomers.size})" else "Fully Paid Customers (${filteredCustomers.size})"
                }
                Text(
                    text = headerTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForestGreenHeader
                )
                if (selectedDateFilter != CreditDueDateFilter.ALL || selectedSortOrder != CreditDueSortOrder.HIGH_TO_LOW || selectedStatusFilter != CreditDueStatusFilter.PENDING_DUE) {
                    Text(
                        text = if (isTamil) "வடிகட்டப்பட்டது" else "Filtered",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = SageAccent
                    )
                }
            }
        }
        if (filteredCustomers.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SageCardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (selectedStatusFilter == CreditDueStatusFilter.PAID) {
                            Text(if (isTamil) "வடிகட்டலில் முழுமையாக செலுத்திய வாடிக்கையாளர்கள் யாரும் இல்லை" else "No fully paid customers found in this filter", fontWeight = FontWeight.Bold, color = DeepSageGreen, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Text(if (isTamil) "₹0 இருப்பு உள்ள வாடிக்கையாளர்கள் இங்கு தோன்றுவர்." else "Customers with ₹0 balance will appear here.", fontSize = 12.sp, color = SageAccent, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        } else {
                            Text(if (isTamil) "பொருந்தும் பதிவுகள் எதுவும் இல்லை! 🎉" else "No matching records found! 🎉", fontWeight = FontWeight.Bold, color = DeepSageGreen, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Text(if (isTamil) "அனைத்து கணக்குகளும் தீர்க்கப்பட்டுள்ளன அல்லது வடித்தலுடன் பொருந்துகின்றன." else "All accounts are settled or match filter.", fontSize = 12.sp, color = SageAccent, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        } else {
            items(filteredCustomers, key = { it.id }) { customer ->
                CustomerCreditDueCard(
                    customer = customer,
                    isTamil = isTamil,
                    onClick = { selectedCustomer = customer },
                    onAddPayment = { customerForPayment = customer },
                    onShareWhatsApp = { shareCustomerDueWhatsApp(context, customer, settings.businessName, isTamil) },
                    onSharePdf = {
                        pdfCustomerSelected = customer
                    }
                )
            }
        }
    }
    // Customer Statement & Details Bottom Sheet
    selectedCustomer?.let { customer ->
        // Keep updated customer data if changed in list
        val currentCustomer = synchronizedCustomers.find { it.id == customer.id } ?: customer
        val customerJobs = jobs.filter {
            it.customerId == currentCustomer.id || it.customerName.equals(currentCustomer.name, ignoreCase = true)
        }
        CustomerDetailSheet(
            customer = currentCustomer,
            jobs = customerJobs,
            settings = settings,
            workspaceMembers = workspaceMembers,
            partners = partners,
            expenses = expenses,
            onDismiss = { selectedCustomer = null },
            onAddPayment = {
                customerForPayment = currentCustomer
            },
            onCall = {
                if (currentCustomer.phone.isNotBlank()) {
                    openDialer(context, currentCustomer.phone)
                } else {
                    Toast.makeText(context, if (isTamil) "${currentCustomer.name} கைபேசி எண் சேமிக்கப்படவில்லை" else "No phone number saved for ${currentCustomer.name}", Toast.LENGTH_SHORT).show()
                    customerToEditPhone = currentCustomer
                }
            },
            onWhatsApp = {
                val msg = buildCustomerDueWhatsAppMessage(currentCustomer, settings.businessName, isTamil)
                sendWhatsAppMessage(context, currentCustomer.phone, msg)
            },
            onSharePdf = {
                pdfCustomerSelected = currentCustomer
            },
            onEditPhone = {
                customerToEditPhone = currentCustomer
            },
            onEditJob = onEditJob,
            onDeleteJob = onDeleteJob,
            onDeleteCustomer = onDeleteCustomer,
            isOwner = isOwner,
            actorName = actorName
        )
    }
    // Dialog to Add Payment for Customer
    customerForPayment?.let { customer ->
        val currentCustomer = synchronizedCustomers.find { it.id == customer.id } ?: customer
        RecordCustomerPaymentDialog(
            customer = currentCustomer,
            settings = settings,
            actorName = actorName,
            isOwner = isOwner,
            workspaceMembers = workspaceMembers,
            partners = partners,
            onDismiss = { customerForPayment = null },
            onConfirm = { amount, dateMillis, method, note, cUid, cName, cRole ->
                if (onRecordPaymentWithCollector != null) {
                    onRecordPaymentWithCollector(customer, amount, dateMillis, method, note, cUid, cName, cRole)
                } else {
                    onRecordPayment?.invoke(customer, amount, dateMillis, method, note)
                }
                customerForPayment = null
                // Also update selectedCustomer if open
                if (selectedCustomer?.id == customer.id) {
                    val updated = customers.find { it.id == customer.id }
                    if (updated != null) {
                        selectedCustomer = updated
                    }
                }
            }
        )
    }
    // Dialog to Add or Edit Customer Phone Number
    customerToEditPhone?.let { customer ->
        var phoneInput by remember { mutableStateOf(customer.phone) }
        var phoneError by remember { mutableStateOf(false) }
        val phoneRequester = remember { BringIntoViewRequester() }
        val coroutineScope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { customerToEditPhone = null },
            title = {
                Text(
                    text = if (isTamil) (if (customer.phone.isBlank()) "கைபேசி எண் சேர்க்க" else "கைபேசி எண் திருத்த") else (if (customer.phone.isBlank()) "Add Phone Number" else "Edit Phone Number"),
                    fontWeight = FontWeight.Bold,
                    color = ForestGreenHeader
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (isTamil) "வாடிக்கையாளர்: ${customer.name}" else "Customer: ${customer.name}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DeepSageGreen
                    )
                    Text(
                        text = if (isTamil) "வாட்ஸ்அப் மற்றும் அறிக்கை விவரங்களை அனுப்ப செல்லுபடியாகும் 10 இலக்க மொபைல் எண்ணை உள்ளிடவும்." else "Enter a valid 10-digit mobile number for WhatsApp messaging and statements.",
                        fontSize = 12.sp,
                        color = TextSecondaryDark
                    )
                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = {
                            phoneInput = it
                            phoneError = false
                        },
                        label = { Text(if (isTamil) "10-இலக்க மொபைல் எண்" else "10-Digit Mobile Number") },
                        leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = DeepSageGreen) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        isError = phoneError,
                        supportingText = {
                            if (phoneError) {
                                Text(if (isTamil) "செல்லுபடியாகும் 10 இலக்க எண்ணை உள்ளிடவும்" else "Please enter a valid 10-digit number", color = AlertDueRed)
                            } else {
                                val clean = sanitizePhoneNumberForStorage(phoneInput)
                                if (clean.length == 10) {
                                    Text(if (isTamil) "சரியான மொபைல் எண்: +91 $clean" else "Valid mobile number: +91 $clean", color = SuccessPaidGreen)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth().trackFocusedField(phoneRequester, coroutineScope),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sanitized = sanitizePhoneNumberForStorage(phoneInput)
                        if (phoneInput.isNotBlank() && sanitized.length != 10 && sanitized.length !in 10..12) {
                            phoneError = true
                        } else {
                            val updatedCustomer = customer.copy(
                                phone = sanitized,
                                updatedAt = System.currentTimeMillis()
                            )
                            onUpdateCustomer?.invoke(updatedCustomer)
                            if (selectedCustomer?.id == customer.id) {
                                selectedCustomer = updatedCustomer
                            }
                            customerToEditPhone = null
                            val msg = if (sanitized.isBlank()) {
                                if (isTamil) "கைபேசி எண் நீக்கப்பட்டது" else "Phone number removed"
                            } else {
                                if (isTamil) "கைபேசி எண் $sanitized ஆக மாற்றப்பட்டது" else "Phone number updated to $sanitized"
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen)
                ) {
                    Text(if (isTamil) "சேமி" else "Save Phone")
                }
            },
            dismissButton = {
                TextButton(onClick = { customerToEditPhone = null }) {
                    Text(if (isTamil) "ரத்து" else "Cancel")
                }
            }
        )
    }

    if (showBulkPdfOptionsDialog) {
        PdfOptionsDialog(
            title = if (isTamil) "மொத்த நிலுவை அறிக்கை PDF" else "Bulk Customer Dues Report PDF",
            subtitle = if (isTamil) "வாடிக்கையாளர் நிலுவை அறிக்கையை பகிரவும் அல்லது சேமிக்கவும்" else "Share or save customer credit dues report",
            isTamil = isTamil,
            onSharePdf = {
                val file = PdfGeneratorHelper.generateBulkCustomerDuesPdf(
                    context = context,
                    settings = settings,
                    customers = filteredCustomers
                )
                file?.let {
                    PdfGeneratorHelper.sharePdf(context, it, "Customer Credit Dues Report - ${settings.businessName}")
                }
            },
            onDownloadPdf = {
                val file = PdfGeneratorHelper.generateBulkCustomerDuesPdf(
                    context = context,
                    settings = settings,
                    customers = filteredCustomers
                )
                file?.let {
                    val displayName = "Customer_Credit_Dues_${settings.businessName.ifBlank { "Business" }}"
                    PdfGeneratorHelper.downloadPdfToDownloads(context, it, displayName, isTamil)
                }
            },
            onDismiss = { showBulkPdfOptionsDialog = false }
        )
    }

    pdfCustomerSelected?.let { customer ->
        val currentCustomer = synchronizedCustomers.find { it.id == customer.id } ?: customer
        val custJobs = jobs.filter {
            it.customerId == currentCustomer.id || it.customerName.equals(currentCustomer.name, ignoreCase = true)
        }
        PdfOptionsDialog(
            title = if (isTamil) "வாடிக்கையாளர் அறிக்கை PDF" else "Customer Statement PDF",
            subtitle = if (isTamil) "${currentCustomer.name} அறிக்கை" else "Statement for ${currentCustomer.name}",
            isTamil = isTamil,
            onSharePdf = {
                val file = PdfGeneratorHelper.generateCustomerStatementPdf(
                    context = context,
                    settings = settings,
                    customer = currentCustomer,
                    jobs = custJobs
                )
                file?.let {
                    PdfGeneratorHelper.sharePdf(
                        context = context,
                        file = it,
                        subject = if (isTamil) "டிராக்டர் அறிக்கை - ${customer.name}" else "Tractor Statement - ${customer.name}"
                    )
                }
            },
            onDownloadPdf = {
                val file = PdfGeneratorHelper.generateCustomerStatementPdf(
                    context = context,
                    settings = settings,
                    customer = customer,
                    jobs = custJobs
                )
                file?.let {
                    val displayName = "Statement_${customer.name}"
                    PdfGeneratorHelper.downloadPdfToDownloads(context, it, displayName, isTamil)
                }
            },
            onDismiss = { pdfCustomerSelected = null }
        )
    }
}
fun shareCustomerDueWhatsApp(
    context: Context,
    customer: CustomerEntity,
    businessName: String = "AIDHUNT Trac Services",
    isTamil: Boolean = false
) {
    val msg = buildCustomerDueWhatsAppMessage(customer, businessName, isTamil)
    sendWhatsAppMessage(context, customer.phone, msg)
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
        CustomerAvatarColor(bg = Color(0xFFFFEDD5), text = Color(0xFFC2410C)), // Light Warm Orange
        CustomerAvatarColor(bg = Color(0xFFE0E7FF), text = Color(0xFF3730A3)), // Light Indigo
        CustomerAvatarColor(bg = Color(0xFFFCE7F3), text = Color(0xFF9D174D)), // Light Rose
        CustomerAvatarColor(bg = Color(0xFFE0F2FE), text = Color(0xFF0369A1)), // Light Sky
        CustomerAvatarColor(bg = Color(0xFFFEF3C7), text = Color(0xFF92400E))  // Light Amber
    )
    val index = kotlin.math.abs(name.hashCode()) % palette.size
    return palette[index]
}

@Composable
fun CustomerCreditDueCard(
    customer: CustomerEntity,
    isTamil: Boolean,
    onClick: () -> Unit,
    onAddPayment: () -> Unit,
    onShareWhatsApp: () -> Unit,
    onSharePdf: () -> Unit
) {
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()
    val isPaid = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(customer.balanceDue) <= 0.0
    val avatarColor = getCustomerAvatarColor(customer.name)
    val initials = getCustomerInitials(customer.name)

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(
                AppTheme.colors.cardBorder.copy(alpha = 0.6f)
            )
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("customer_due_card_${customer.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Top Section: Avatar | Customer Name & Phone/Location | Due Amount / PAID Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // 1. Customer Avatar Circle
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(avatarColor.bg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initials,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = avatarColor.text
                    )
                }

                // 2. Customer Name & Secondary Info (Phone / Village)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    Text(
                        text = customer.name,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = AppTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    val secondaryText = buildString {
                        if (customer.location.isNotBlank()) append(customer.location)
                        if (customer.phone.isNotBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(customer.phone)
                        }
                    }

                    if (secondaryText.isNotBlank()) {
                        Text(
                            text = secondaryText,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Normal,
                            color = AppTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }

                // 3. Right Side: Due Amount or PAID Badge (Unconstrained width for money so it never ellipsizes)
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    if (isPaid) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = SuccessPaidGreenBg,
                            border = androidx.compose.foundation.BorderStroke(1.dp, SuccessPaidGreen.copy(alpha = 0.3f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = SuccessPaidGreen,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = if (isTamil) "செலுத்தப்பட்டது" else "PAID",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuccessPaidGreen
                                )
                            }
                        }
                    } else {
                        Text(
                            text = formatInr(customer.balanceDue),
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = AlertDueRed
                        )
                        Text(
                            text = if (isTamil) "நிலுவை" else "Due",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = AlertDueRed
                        )
                    }
                }
            }

            // Middle Section: Compact Financial Metadata Row (No nested card/surface)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isTamil) "மொத்த பில் ${formatInr(customer.totalBilled)}" else "Total Billed ${formatInr(customer.totalBilled)}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = AppTheme.colors.textSecondary
                )
                Text(
                    text = "   •   ",
                    fontSize = 11.5.sp,
                    color = AppTheme.colors.textMuted
                )
                Text(
                    text = if (isTamil) "செலுத்தியது ${formatInr(customer.totalPaid)}" else "Paid ${formatInr(customer.totalPaid)}",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SuccessPaidGreen
                )
            }

            Divider(color = AppTheme.colors.cardBorder.copy(alpha = 0.4f), thickness = 0.5.dp)

            // Bottom Actions Row: Compact Share & PDF + Primary Action
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Share with label
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onShareWhatsApp)
                            .testTag("btn_share_customer_card_${customer.id}")
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = if (isTamil) "வாட்ஸ்அப் மூலம் பகிரவும்" else "Share via WhatsApp",
                            tint = SuccessPaidGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (isTamil) "பகிர்" else "Share",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SuccessPaidGreen
                        )
                    }

                    // PDF with label
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onSharePdf)
                            .testTag("btn_pdf_customer_card_${customer.id}")
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureAsPdf,
                            contentDescription = if (isTamil) "அறிக்கை PDF ஆக பகிரவும்" else "Share Statement PDF",
                            tint = DeepSageGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "PDF",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DeepSageGreen
                        )
                    }
                }

                if (isPaid) {
                    TextButton(
                        onClick = onClick,
                        modifier = Modifier.testTag("btn_view_statement_card_${customer.id}"),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Text(if (isTamil) "அறிக்கை" else "Statement", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DeepSageGreen)
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(14.dp), tint = DeepSageGreen)
                    }
                } else {
                    Button(
                        onClick = onAddPayment,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SuccessPaidGreen),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier
                            .height(30.dp)
                            .testTag("btn_add_payment_card_${customer.id}")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(if (isTamil) "பணம் சேர்க்க" else "Add Payment", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerDetailSheet(
    customer: CustomerEntity,
    jobs: List<JobEntryEntity>,
    settings: AppSettingsEntity,
    onDismiss: () -> Unit,
    onAddPayment: () -> Unit,
    onCall: () -> Unit,
    onWhatsApp: () -> Unit,
    onSharePdf: () -> Unit,
    onEditPhone: () -> Unit = {},
    onEditJob: ((JobEntryEntity) -> Unit)? = null,
    onDeleteJob: ((JobEntryEntity) -> Unit)? = null,
    onDeleteCustomer: ((CustomerEntity) -> Unit)? = null,
    isOwner: Boolean = false,
    actorName: String = "",
    workspaceMembers: List<com.example.data.firebase.WorkspaceMember> = emptyList(),
    partners: List<com.example.data.entity.PartnerEntity> = emptyList(),
    expenses: List<com.example.data.entity.ExpenseEntity> = emptyList()
) {
    val context = LocalContext.current
    val isTamil = settings.language == "TA"
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val workJobs = jobs.filterNot { com.example.ui.util.FinancialCalculationEngine.isSyntheticPayment(it) }
    val totalMinutes = workJobs.sumOf { it.durationMinutes }
    val tractorsUsed = workJobs.map { it.tractorLabel }.distinct().joinToString(", ").ifBlank { "N/A" }
    val operators = workJobs.map { it.operatorName }.distinct().joinToString(", ").ifBlank { "N/A" }
    var selectedJobForDetail by remember { mutableStateOf<JobEntryEntity?>(null) }
    var jobToDelete by remember { mutableStateOf<JobEntryEntity?>(null) }
    var showDeleteCustomerDialog by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.White
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = customer.name,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreenHeader
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (customer.phone.isNotBlank()) {
                                    "${if (isTamil) "கைபேசி" else "Phone"}: ${customer.phone} ${if (customer.location.isNotBlank()) "• " + customer.location else ""}"
                                } else {
                                    if (isTamil) "கைபேசி எண் சேமிக்கப்படவில்லை" else "No phone number saved"
                                },
                                fontSize = 12.sp,
                                color = if (customer.phone.isNotBlank()) TextSecondaryDark else AlertDueRed
                            )
                            IconButton(
                                onClick = onEditPhone,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = if (isTamil) "கைபேசி எண்ணைத் திருத்து" else "Edit Phone",
                                    tint = DeepSageGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                    val roundedCustomerDue = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(customer.balanceDue)
                    StatusBadge(isPaid = roundedCustomerDue <= 0.0, pendingAmount = roundedCustomerDue)
                }
            }
            // Quick Actions: Add Payment, Call, WhatsApp, Share PDF Statement
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val roundedCustomerDue = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(customer.balanceDue)
                    if (roundedCustomerDue > 0.0) {
                        Button(
                            onClick = onAddPayment,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("btn_sheet_add_payment"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SuccessPaidGreen)
                        ) {
                            Icon(Icons.Default.Paid, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isTamil) "பணம் சேர்க்க (நிலுவை வசூல்)" else "Add Payment (Collect Due)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onCall,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isTamil) "அழை" else "Call", fontSize = 12.sp, maxLines = 1)
                        }
                        Button(
                            onClick = onWhatsApp,
                            modifier = Modifier
                                .weight(1.1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DeepSageGreen),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isTamil) "வாட்ஸ்அப்" else "WhatsApp", fontSize = 12.sp, maxLines = 1)
                        }
                        Button(
                            onClick = onSharePdf,
                            modifier = Modifier
                                .weight(1.2f)
                                .height(44.dp)
                                .testTag("btn_share_customer_statement_pdf"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ForestGreenHeader),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isTamil) "பகிர்க (PDF)" else "Share PDF", fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
            // Summary Card
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SageCardBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DetailRow(label = if (isTamil) "மொத்த பில் தொகை" else "Total Work Billed", value = formatInr(customer.totalBilled))
                        DetailRow(label = if (isTamil) "மொத்த செலுத்திய தொகை" else "Total Amount Paid", value = formatInr(customer.totalPaid))
                        DetailRow(label = if (isTamil) "வேலை செய்த மொத்த நேரம்" else "Total Hours Worked", value = com.example.ui.util.WorkBillingCalculator.formatDuration(totalMinutes))
                        DetailRow(label = if (isTamil) "பயன்படுத்தப்பட்ட டிராக்டர்கள்" else "Tractors Used", value = tractorsUsed)
                        DetailRow(label = if (isTamil) "இயக்குநர்கள்" else "Operators", value = operators)
                        Divider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (isTamil) "நிலுவைத் தொகை:" else "Outstanding Balance Due:", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (customer.balanceDue > 0) AlertDueRed else SuccessPaidGreen)
                            Text(formatInr(customer.balanceDue), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (customer.balanceDue > 0) AlertDueRed else SuccessPaidGreen)
                        }
                    }
                }
            }
            // Transaction / Job History
            item {
                Text(
                    text = if (isTamil) "பரிவர்த்தனை & கட்டண வரலாறு (${jobs.size})" else "Transaction & Payment History (${jobs.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = ForestGreenHeader
                )
            }
            if (jobs.isEmpty()) {
                item {
                    Text(if (isTamil) "இந்த வாடிக்கையாளருக்கு எந்த வேலை பதிவும் இல்லை." else "No job records recorded under this customer.", fontSize = 12.sp, color = TextMutedDark)
                }
            } else {
                items(jobs) { job ->
                    val isPayment = com.example.ui.util.FinancialCalculationEngine.isSyntheticPayment(job)
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPayment) SuccessPaidGreenBg else Color.White
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (isPayment) SuccessPaidGreen.copy(alpha = 0.4f) else SageOutline.copy(alpha = 0.5f)
                            )
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedJobForDetail = job }
                            .testTag("job_history_item_${job.id}")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (isPayment) {
                                        Icon(
                                            imageVector = Icons.Default.Paid,
                                            contentDescription = null,
                                            tint = SuccessPaidGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Text(
                                        text = if (isTamil && (job.workType == "Payment Received" || job.tractorLabel == "Payment")) {
                                            "கட்டணம் பெறப்பட்டது"
                                        } else {
                                            job.workType
                                        },
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPayment) SuccessPaidGreen else ForestGreenHeader
                                    )
                                }
                                Text(
                                    text = if (isPayment) "+ ${formatInr(job.amountReceived)}" else formatInr(job.totalAmount),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPayment) SuccessPaidGreen else Color.Black
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isPayment) {
                                    "${formatDate(job.startTimeMillis)} • ${job.notes.ifBlank { if (isTamil) "நேரடி பணம் பெறப்பட்டது" else "Direct Payment Received" }}"
                                } else {
                                    "${formatDate(job.startTimeMillis)} • ${com.example.ui.util.WorkBillingCalculator.formatDuration(job.durationMinutes)} • ${job.tractorLabel}"
                                },
                                fontSize = 11.sp,
                                color = TextMutedDark
                             )
                            if (!isPayment) {
                                val createdByDisplay = com.example.data.auth.RoleUtils.getCreatedByDisplay(job, settings.ownerName, workspaceMembers, partners, isTamil)
                                val entryForDisplay = com.example.data.auth.RoleUtils.getEntryForDisplay(job, settings.ownerName, workspaceMembers, partners, isTamil)
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(createdByDisplay, fontSize = 11.sp, color = Color(0xFF374151), fontWeight = FontWeight.Medium)
                                if (entryForDisplay != null) {
                                    Text(entryForDisplay, fontSize = 10.5.sp, color = Color(0xFF4B5563))
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val isOldJob = job.notes.contains("Old", ignoreCase = true) || job.customerName.contains("old", ignoreCase = true) || job.notes.contains("Historical", ignoreCase = true) || job.workType.contains("Old", ignoreCase = true)
                                    val dueTextColor = when {
                                        job.pendingAmount <= 0 -> SuccessPaidGreen
                                        isOldJob -> Color(0xFF991B1B)
                                        else -> AlertDueRed
                                    }
                                    Text("${if (isTamil) "நிலுவை" else "Due"}: ${formatInr(job.pendingAmount)}", fontSize = 11.sp, color = dueTextColor, fontWeight = FontWeight.SemiBold)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                val canEdit = com.example.data.auth.AuthorizationManager.canEditEntry(
                                    job = job,
                                    isOwner = isOwner,
                                    currentActorName = actorName
                                )
                                val canDelete = com.example.data.auth.AuthorizationManager.canDeleteEntry(
                                    job = job,
                                    isOwner = isOwner,
                                    currentActorName = actorName
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            val file = PdfGeneratorHelper.generateJobReceiptPdf(context, settings, job)
                                            file?.let {
                                                PdfGeneratorHelper.sharePdf(
                                                    context = context,
                                                    file = it,
                                                    subject = if (isTamil) "வேலை ரசீது - ${job.customerName}" else "Work Receipt - ${job.customerName}"
                                                )
                                            }
                                        },
                                        modifier = Modifier.size(32.dp).testTag("btn_share_job_pdf_${job.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = if (isTamil) "ரசீது பகிரவும்" else "Share Receipt",
                                            tint = DeepSageGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val file = PdfGeneratorHelper.generateJobReceiptPdf(context, settings, job)
                                            file?.let {
                                                val displayName = "Receipt_${job.id}_${job.customerName}"
                                                PdfGeneratorHelper.downloadPdfToDownloads(context, it, displayName, isTamil)
                                            }
                                        },
                                        modifier = Modifier.size(32.dp).testTag("btn_download_job_pdf_${job.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FileDownload,
                                            contentDescription = if (isTamil) "பதிவிறக்கு" else "Download Receipt",
                                            tint = DeepSageGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    if (canEdit) {
                                        IconButton(
                                            onClick = {
                                                onDismiss()
                                                onEditJob?.invoke(job)
                                            },
                                            modifier = Modifier.size(32.dp).testTag("btn_edit_job_${job.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = if (isTamil) "திருத்து" else "Edit Job",
                                                tint = DeepSageGreen,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    if (canDelete) {
                                        IconButton(
                                            onClick = {
                                                jobToDelete = job
                                            },
                                            modifier = Modifier.size(32.dp).testTag("btn_delete_job_${job.id}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = if (isTamil) "நீக்கு" else "Delete Job",
                                                tint = AlertDueRed,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (isOwner) {
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showDeleteCustomerDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("btn_delete_customer"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertDueRed),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AlertDueRed.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = AlertDueRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isTamil) "வாடிக்கையாளரை நீக்கு" else "Delete Customer",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AlertDueRed
                        )
                    }
                }
            }
            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    selectedJobForDetail?.let { job ->
        CustomerSummaryPopup(
            job = job,
            settings = settings,
            expenses = expenses,
            isOwner = isOwner,
            workspaceMembers = workspaceMembers,
            partners = partners,
            onDismiss = { selectedJobForDetail = null },
            onCallCustomer = {
                if (job.customerPhone.isNotBlank()) {
                    openDialer(context, job.customerPhone)
                } else {
                    Toast.makeText(
                        context,
                        if (isTamil) "${job.customerName}-க்கான தொலைபேசி எண் இல்லை" else "No phone number available for ${job.customerName}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onShareWhatsApp = {
                val msg = buildJobWhatsAppMessage(job, settings.businessName, isTamil)
                sendWhatsAppMessage(context, job.customerPhone, msg)
            },
            onSharePdf = {
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
                val file = PdfGeneratorHelper.generateJobReceiptPdf(
                    context = context,
                    settings = settings,
                    job = job
                )
                file?.let {
                    val displayName = "Receipt_${job.customerName}_${job.id}"
                    PdfGeneratorHelper.downloadPdfToDownloads(context, it, displayName, isTamil)
                }
            },
            onDeleteRequest = {
                jobToDelete = job
            },
            onEditJob = { editJob ->
                selectedJobForDetail = null
                onDismiss()
                onEditJob?.invoke(editJob)
            }
        )
    }

    jobToDelete?.let { targetJob ->
        AlertDialog(
            onDismissRequest = { jobToDelete = null },
            title = {
                Text(
                    text = if (isTamil) "வேலைப் பதிவை நீக்கவா?" else "Delete Job Entry?",
                    fontWeight = FontWeight.Bold,
                    color = AlertDueRed
                )
            },
            text = {
                Text(
                    text = if (isTamil)
                        "வேலைப் பதிவு (${targetJob.customerName} - ${targetJob.workType}) நிரந்தரமாக நீக்கப்படும். தொடர விரும்புகிறீர்களா?"
                    else
                        "Job entry (${targetJob.customerName} - ${targetJob.workType}) will be permanently deleted. Do you want to proceed?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val toDelete = targetJob
                        jobToDelete = null
                        selectedJobForDetail = null
                        onDeleteJob?.invoke(toDelete)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertDueRed),
                    modifier = Modifier.testTag("btn_confirm_delete_job")
                ) {
                    Text(if (isTamil) "நீக்கு" else "Delete", color = Color.White)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { jobToDelete = null },
                    modifier = Modifier.testTag("btn_cancel_delete_job")
                ) {
                    Text(if (isTamil) "ரத்து" else "Cancel")
                }
            }
        )
    }

    if (showDeleteCustomerDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteCustomerDialog = false },
            title = {
                Text(
                    text = if (isTamil) "வாடிக்கையாளரை நீக்கவா?" else "Delete Customer?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = AlertDueRed
                )
            },
            text = {
                Text(
                    text = if (isTamil)
                        "இது இந்த வாடிக்கையாளரையும் அவர்களது அனைத்து தொடர்புடைய வேலை மற்றும் கணக்கு பதிவுகளையும் நிரந்தரமாக நீக்கும்."
                    else
                        "This will permanently delete this customer and their associated records.",
                    fontSize = 13.5.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteCustomerDialog = false
                        onDismiss()
                        onDeleteCustomer?.invoke(customer)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AlertDueRed),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("btn_confirm_delete_customer")
                ) {
                    Text(if (isTamil) "நீக்கு" else "Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showDeleteCustomerDialog = false },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("btn_cancel_delete_customer")
                ) {
                    Text(if (isTamil) "ரத்து" else "Cancel")
                }
            }
        )
    }
}
data class CollectorOption(val uid: String, val name: String, val role: String)

@Composable
fun RecordCustomerPaymentDialog(
    customer: CustomerEntity,
    settings: AppSettingsEntity,
    actorName: String = "",
    isOwner: Boolean = false,
    workspaceMembers: List<com.example.data.firebase.WorkspaceMember> = emptyList(),
    partners: List<com.example.data.entity.PartnerEntity> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, dateMillis: Long, method: String, note: String, collectorUid: String, collectorName: String, collectorRole: String) -> Unit
) {
    val context = LocalContext.current
    val isTamil = settings.language == "TA"
    val coroutineScope = rememberCoroutineScope()
    val dialogAmountRequester = remember { BringIntoViewRequester() }
    val dialogNotesRequester = remember { BringIntoViewRequester() }
    val roundedCustomerBalance = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(customer.balanceDue)
    var amountInput by remember { mutableStateOf(if (roundedCustomerBalance > 0.0) String.format(Locale.US, "%.0f", roundedCustomerBalance) else "") }
    var paymentDateMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var selectedPaymentMethod by remember { mutableStateOf("Cash") }
    var noteInput by remember { mutableStateOf("") }
    var hasAttemptedSubmit by remember { mutableStateOf(false) }
    val amountValue = amountInput.toDoubleOrNull() ?: 0.0
    val roundedAmountValue = com.example.ui.util.FinancialCalculationEngine.roundToPositiveWholeRupee(amountValue)
    val isAmountInvalid = amountValue <= 0.0 || roundedAmountValue > roundedCustomerBalance
    val paymentMethods = listOf("Cash", "UPI / GPay", "Bank Transfer", "Cheque")

    val collectorOptions = remember(isOwner, actorName, settings.ownerName, settings.activePartnerName, partners, workspaceMembers) {
        if (isOwner) {
            val list = mutableListOf<CollectorOption>()
            val ownerOptionName = actorName.ifBlank { settings.ownerName.ifBlank { "Owner" } }
            list.add(CollectorOption(uid = "", name = ownerOptionName, role = "Owner"))
            partners.forEach { p ->
                if (p.name.isNotBlank() && !p.name.equals(ownerOptionName, ignoreCase = true)) {
                    list.add(CollectorOption(uid = p.phone, name = p.name, role = "Partner"))
                }
            }
            workspaceMembers.forEach { m ->
                val mName = m.displayName?.takeIf { it.isNotBlank() } ?: m.phoneNumber.orEmpty()
                if (mName.isNotBlank() && list.none { it.name.equals(mName, ignoreCase = true) }) {
                    list.add(CollectorOption(uid = m.uid, name = mName, role = m.role.ifBlank { "Partner" }))
                }
            }
            list
        } else {
            val partnerOptionName = actorName.ifBlank { settings.activePartnerName.ifBlank { "Partner" } }
            listOf(CollectorOption(uid = "", name = partnerOptionName, role = "Partner"))
        }
    }
    var selectedCollector by remember { mutableStateOf(collectorOptions.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SuccessPaidGreenBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Paid,
                        contentDescription = null,
                        tint = SuccessPaidGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = if (isTamil) "கட்டணம் சேர்க்க" else "Add Payment",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = ForestGreenHeader
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Customer Summary Info
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = SageCardBg,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = if (isTamil) "வாடிக்கையாளர்: ${customer.name}" else "Customer: ${customer.name}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = ForestGreenHeader
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(if (isTamil) "நிலுவைத் தொகை:" else "Pending Due:", fontSize = 12.sp, color = TextSecondaryDark)
                            Text(
                                formatInr(customer.balanceDue),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (customer.balanceDue > 0) AlertDueRed else SuccessPaidGreen
                            )
                        }
                    }
                }

                // Collector Selection (Owner can choose; Partner locked to self)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isTamil) "வசூல் செய்தவர்:" else "Collected By:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondaryDark
                    )
                    if (isOwner && collectorOptions.size > 1) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(collectorOptions) { option ->
                                FilterChip(
                                    selected = selectedCollector == option,
                                    onClick = { selectedCollector = option },
                                    label = {
                                        Text(
                                            "${option.name} (${if (option.role.equals("Owner", ignoreCase = true)) (if (isTamil) "உரிமையாளர்" else "Owner") else (if (isTamil) "பங்குதாரர்" else "Partner")})",
                                            fontSize = 11.sp
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = DeepSageGreen,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = SoftSageGreen.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    selectedCollector.name,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ForestGreenHeader
                                )
                                Text(
                                    if (selectedCollector.role.equals("Owner", ignoreCase = true)) (if (isTamil) "உரிமையாளர்" else "Owner") else (if (isTamil) "பங்குதாரர்" else "Partner"),
                                    fontSize = 11.sp,
                                    color = DeepSageGreen,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                // 1. Amount Received (₹) - Mandatory with red validation outline and helper text
                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it },
                    label = { Text(if (isTamil) "பெறப்பட்ட தொகை (₹) *" else "Amount Received (₹) *") },
                    leadingIcon = { Icon(Icons.Default.Paid, contentDescription = null, tint = DeepSageGreen) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = hasAttemptedSubmit && isAmountInvalid,
                    supportingText = {
                        if (hasAttemptedSubmit && isAmountInvalid) {
                            Text(
                                text = if (amountInput.isBlank()) {
                                    if (isTamil) "இந்தத் துறை கட்டாயமாகும்" else "This field is required"
                                } else if (amountValue <= 0.0) {
                                    if (isTamil) "பூஜ்ஜியத்தை விட அதிகமான தொகையை உள்ளிடவும்" else "Please enter an amount greater than 0"
                                } else {
                                    if (isTamil) "தொகை நிலுவைத் தொகையை விட அதிகமாக இருக்க முடியாது" else "Amount cannot exceed outstanding due"
                                },
                                color = AlertDueRed,
                                fontSize = 11.sp
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .trackFocusedField(dialogAmountRequester, coroutineScope)
                        .testTag("input_payment_amount"),
                    shape = RoundedCornerShape(10.dp)
                )
    // 2. Date Selection (Defaults to Today, Editable)
                val cal = Calendar.getInstance().apply { timeInMillis = paymentDateMillis }
                OutlinedButton(
                    onClick = {
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val now = Calendar.getInstance()
                                val newCal = Calendar.getInstance().apply {
                                    set(year, month, dayOfMonth)
                                    // If historical date, set to 12:00 PM to avoid "end of day" edge cases and ensure consistency
                                    if (year < now.get(Calendar.YEAR) || 
                                        (year == now.get(Calendar.YEAR) && month < now.get(Calendar.MONTH)) ||
                                        (year == now.get(Calendar.YEAR) && month == now.get(Calendar.MONTH) && dayOfMonth < now.get(Calendar.DAY_OF_MONTH))) {
                                        set(Calendar.HOUR_OF_DAY, 12)
                                        set(Calendar.MINUTE, 0)
                                        set(Calendar.SECOND, 0)
                                        set(Calendar.MILLISECOND, 0)
                                    } else {
                                        // If today, preserve current time
                                        set(Calendar.HOUR_OF_DAY, now.get(Calendar.HOUR_OF_DAY))
                                        set(Calendar.MINUTE, now.get(Calendar.MINUTE))
                                        set(Calendar.SECOND, now.get(Calendar.SECOND))
                                        set(Calendar.MILLISECOND, now.get(Calendar.MILLISECOND))
                                    }
                                }
                                paymentDateMillis = newCal.timeInMillis
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("btn_payment_date"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = DeepSageGreen, modifier = Modifier.size(18.dp))
                            Text(if (isTamil) "கட்டணம் செலுத்திய தேதி:" else "Payment Date:", fontSize = 12.sp, color = TextSecondaryDark)
                        }
                        Text(
                            formatDate(paymentDateMillis),
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = ForestGreenHeader
                        )
                    }
                }
                // 3. Payment Method (Optional)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isTamil) "பணம் செலுத்தும் முறை (விருப்பத்தேர்வு)" else "Payment Method (Optional)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextSecondaryDark
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        paymentMethods.take(2).forEach { method ->
                            FilterChip(
                                selected = selectedPaymentMethod == method,
                                onClick = { selectedPaymentMethod = method },
                                label = { Text(getLocalizedPaymentMethod(method, isTamil), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DeepSageGreen,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        paymentMethods.drop(2).forEach { method ->
                            FilterChip(
                                selected = selectedPaymentMethod == method,
                                onClick = { selectedPaymentMethod = method },
                                label = { Text(getLocalizedPaymentMethod(method, isTamil), fontSize = 11.sp) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = DeepSageGreen,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }
                // 4. Note (Optional)
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text(if (isTamil) "குறிப்பு / விபரம் (விருப்பத்தேர்வு)" else "Note / Remarks (Optional)") },
                    placeholder = { Text(if (isTamil) "எ.கா. அறுவடை இருப்பு கணக்கு தீர்க்கப்பட்டது" else "e.g. Cleared harvest balance") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .trackFocusedField(dialogNotesRequester, coroutineScope),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    hasAttemptedSubmit = true
                    if (!isAmountInvalid) {
                        onConfirm(
                            roundedAmountValue,
                            paymentDateMillis,
                            selectedPaymentMethod,
                            noteInput.trim(),
                            selectedCollector.uid,
                            selectedCollector.name,
                            selectedCollector.role
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SuccessPaidGreen),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_confirm_record_payment")
            ) {
                Text(if (isTamil) "கட்டணத்தைச் சேமி" else "Save Payment", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isTamil) "ரத்து" else "Cancel", color = TextMutedDark)
            }
        }
    )
}

@Composable
fun RecordCustomerPaymentDialog(
    customer: CustomerEntity,
    settings: AppSettingsEntity,
    actorName: String = "",
    isOwner: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (amount: Double, dateMillis: Long, method: String, note: String) -> Unit
) {
    RecordCustomerPaymentDialog(
        customer = customer,
        settings = settings,
        actorName = actorName,
        isOwner = isOwner,
        workspaceMembers = emptyList(),
        partners = emptyList(),
        onDismiss = onDismiss,
        onConfirm = { amount, dateMillis, method, note, _, _, _ ->
            onConfirm(amount, dateMillis, method, note)
        }
    )
}
