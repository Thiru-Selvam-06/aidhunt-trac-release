package com.example.ui.screens.report

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.entity.*
import com.example.data.firebase.WorkspaceMember
import com.example.ui.components.formatDate
import com.example.ui.components.formatDateTime
import com.example.ui.components.formatInr
import com.example.ui.components.openDialer
import com.example.ui.components.buildJobWhatsAppMessage
import com.example.ui.components.sendWhatsAppMessage
import com.example.ui.screens.home.CustomerSummaryPopup
import com.example.ui.theme.*
import com.example.ui.util.*
import java.util.Calendar
import kotlin.math.max

enum class OverviewChartMetric(val labelEn: String, val labelTa: String) {
    AMOUNT("Amount", "தொகை"),
    HOURS("Hours", "நேரம்"),
    JOBS("Jobs", "பணிகள்")
}

data class DriverMetricItem(
    val uid: String,
    val name: String,
    val role: String,
    val amount: Double,
    val hours: Double,
    val durationMinutes: Long,
    val jobCount: Int,
    val due: Double = 0.0,
    val collected: Double = 0.0
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun BusinessOverviewScreen(
    settings: AppSettingsEntity,
    jobs: List<JobEntryEntity>,
    expenses: List<ExpenseEntity>,
    withdrawals: List<WithdrawalEntity> = emptyList(),
    partners: List<PartnerEntity> = emptyList(),
    workspaceMembers: List<WorkspaceMember> = emptyList(),
    customers: List<CustomerEntity> = emptyList(),
    payments: List<PaymentEntity> = emptyList(),
    totalSales: Double = 0.0,
    totalExpenses: Double = 0.0,
    netBalance: Double = 0.0,
    initialScope: FinancialScope = FinancialScope(mode = FinancialScopeMode.OVERALL),
    onUpdateFinancialScope: ((FinancialScope) -> Unit)? = null,
    onNavigateToExpenses: (() -> Unit)? = null,
    onNavigateToWithdrawals: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onEditJob: ((JobEntryEntity) -> Unit)? = null,
    onDeleteJob: ((JobEntryEntity) -> Unit)? = null,
    isOwner: Boolean = false,
    currentUid: String = ""
) {
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    val hasPartnership = rememberBusinessPartnership(settings.workspaceId, workspaceMembers)
    val context = LocalContext.current
    val responsive = rememberResponsiveDimensions()

    var selectedMode by remember(initialScope) { mutableStateOf(initialScope.mode) }
    var selectedDateMillis by remember(initialScope) { mutableStateOf(initialScope.dateMillis) }
    var selectedYear by remember(initialScope) { mutableStateOf(initialScope.year) }
    var selectedMonth by remember(initialScope) { mutableStateOf(initialScope.month) }

    // Chart metric
    var selectedChartMetric by remember { mutableStateOf(OverviewChartMetric.AMOUNT) }
    var showMetricDropdown by remember { mutableStateOf(false) }

    // High level date filter controls
    var showOverallMenu by remember { mutableStateOf(false) }
    var showDayPicker by remember { mutableStateOf(false) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var showYearPicker by remember { mutableStateOf(false) }

    // Other Controls
    var isExpenseVisible by remember { mutableStateOf(true) }
    var selectedMemberFilter by remember(settings.workspaceId, currentUid) { mutableStateOf("All") }
    var showPersonSelectorDialog by remember(settings.workspaceId, currentUid) { mutableStateOf(false) }
    var selectedJobForDetail by remember(settings.workspaceId, currentUid) { mutableStateOf<JobEntryEntity?>(null) }

    val scope = remember(selectedMode, selectedDateMillis, selectedYear, selectedMonth, settings.workspaceId) {
        val s = FinancialScope(
            mode = selectedMode,
            dateMillis = selectedDateMillis,
            year = selectedYear,
            month = selectedMonth,
            workspaceId = settings.workspaceId
        )
        onUpdateFinancialScope?.invoke(s)
        s
    }

    // ─── REAL DATA from FinancialCalculationEngine ───
    val summary = remember(jobs, expenses, withdrawals, payments, scope) {
        FinancialCalculationEngine.calculateSummary(jobs, expenses, withdrawals, scope, payments)
    }

    val partnerBreakdowns = remember(jobs, expenses, withdrawals, partners, workspaceMembers, scope, hasPartnership) {
        FinancialCalculationEngine.calculatePartnerBreakdown(jobs, expenses, withdrawals, partners, workspaceMembers, scope,
            preserveUnresolvedUids = true).map { it.copy(role = businessDisplayRole(it.role, hasPartnership)) }
    }

    val scopedJobs = remember(jobs, scope) {
        jobs.filter { j ->
            val ts = if (j.startTimeMillis > 0) j.startTimeMillis else j.createdAt
            j.workspaceId == settings.workspaceId && FinancialCalculationEngine.isTimestampInScope(ts, scope)
        }
    }

    val selectedMemberLabel = if (selectedMemberFilter == "All") (if (isTamil) "அனைவரும்" else "All") else
        partnerBreakdowns.firstOrNull { it.partnerUid == selectedMemberFilter }?.partnerName
            ?: workspaceMembers.firstOrNull { it.uid == selectedMemberFilter && it.workspaceId == settings.workspaceId }?.displayName
            ?: if (isTamil) "அடையாளம் கிடைக்கவில்லை" else "Unattributed"
    val displayedJobs = remember(scopedJobs, selectedMemberFilter) {
        if (selectedMemberFilter == "All") scopedJobs else scopedJobs.filter {
            it.financialAttributionUid.ifBlank { it.entryForUid } == selectedMemberFilter
        }
    }

    // Date display text for top selector pill
    val dateSelectorText = remember(selectedMode, selectedDateMillis, selectedYear, selectedMonth, isTamil) {
        when (selectedMode) {
            FinancialScopeMode.OVERALL -> if (isTamil) "அனைத்தும்" else "Overall"
            FinancialScopeMode.DAILY -> formatDate(selectedDateMillis)
            FinancialScopeMode.MONTHLY -> {
                val months = if (isTamil) listOf("ஜன", "பிப்", "மார்ச்", "ஏப்", "மே", "ஜூன்", "ஜூலை", "ஆக", "செப்", "அக்", "நவ", "டிச")
                else listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
                "${months.getOrElse(selectedMonth - 1) { "Sep" }} $selectedYear"
            }
            FinancialScopeMode.YEARLY -> "$selectedYear"
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 10.dp, bottom = 100.dp)
        ) {
            // ─────────────────────────────────────────────────────────────
            // 1. TOP BAR DATE FILTER — matches reference: single Overall pill, no duplicate header
            // ─────────────────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Date Selector Pill with anchored DropdownMenu
                    Box {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, Color(0xFF86EFAC).copy(alpha = 0.6f)),
                            modifier = Modifier
                                .clickable { showOverallMenu = true }
                                .testTag("business_overview_date_pill")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = dateSelectorText,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = null,
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Anchored Overall Dropdown (Image 2 Panel 2)
                        DropdownMenu(
                            expanded = showOverallMenu,
                            onDismissRequest = { showOverallMenu = false },
                            modifier = Modifier
                                .background(Color.White)
                                .clip(RoundedCornerShape(14.dp))
                        ) {
                            // Overall option
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isTamil) "அனைத்தும்" else "Overall",
                                        fontWeight = if (selectedMode == FinancialScopeMode.OVERALL) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.5.sp,
                                        color = if (selectedMode == FinancialScopeMode.OVERALL) Color(0xFF15803D) else Color(0xFF0F172A)
                                    )
                                },
                                leadingIcon = {
                                    Text(
                                        text = "₹",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color(0xFF15803D),
                                        modifier = Modifier.padding(start = 2.dp)
                                    )
                                },
                                trailingIcon = {
                                    if (selectedMode == FinancialScopeMode.OVERALL) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(18.dp))
                                    }
                                },
                                onClick = {
                                    selectedMode = FinancialScopeMode.OVERALL
                                    showOverallMenu = false
                                }
                            )

                            // Day option
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isTamil) "தினசரி" else "Day",
                                        fontWeight = if (selectedMode == FinancialScopeMode.DAILY) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.5.sp,
                                        color = if (selectedMode == FinancialScopeMode.DAILY) Color(0xFF15803D) else Color(0xFF0F172A)
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(17.dp))
                                },
                                trailingIcon = {
                                    if (selectedMode == FinancialScopeMode.DAILY) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(18.dp))
                                    }
                                },
                                onClick = {
                                    showOverallMenu = false
                                    showDayPicker = true
                                }
                            )

                            // Month option
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isTamil) "மாதாந்திர" else "Month",
                                        fontWeight = if (selectedMode == FinancialScopeMode.MONTHLY) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.5.sp,
                                        color = if (selectedMode == FinancialScopeMode.MONTHLY) Color(0xFF15803D) else Color(0xFF0F172A)
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DateRange, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (selectedMode == FinancialScopeMode.MONTHLY) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(18.dp))
                                    }
                                },
                                onClick = {
                                    showOverallMenu = false
                                    showMonthPicker = true
                                }
                            )

                            // Year option
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (isTamil) "வருடாந்திர" else "Year",
                                        fontWeight = if (selectedMode == FinancialScopeMode.YEARLY) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 13.5.sp,
                                        color = if (selectedMode == FinancialScopeMode.YEARLY) Color(0xFF15803D) else Color(0xFF0F172A)
                                    )
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.BarChart, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (selectedMode == FinancialScopeMode.YEARLY) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(18.dp))
                                    }
                                },
                                onClick = {
                                    showOverallMenu = false
                                    showYearPicker = true
                                }
                            )
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 2. SIX COMPACT SUMMARY CARDS (2 Columns x 3 Rows) — ALL REAL DATA
            // ─────────────────────────────────────────────────────────────
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Row 1: Total Recorded & Total Collected
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Card 1: Total Recorded — from FinancialCalculationEngine.calculateSummary
                        OverviewSummaryCard(
                            title = if (isTamil) "மொத்த பதிவு" else "Total Recorded",
                            amount = formatInr(summary.totalRecorded, settings.currency),
                            subtitle = "${summary.jobCount} ${if (isTamil) "பணிகள்" else "Jobs"}",
                            icon = Icons.Default.ConfirmationNumber,
                            iconBgColor = Color(0xFF16A34A),
                            cardBgColor = Color(0xFFF0FDF4),
                            borderColor = Color(0xFFBBF7D0).copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        )

                        // Card 2: Total Collected — from FinancialCalculationEngine (totalReceived = sum of amountReceived)
                        OverviewSummaryCard(
                            title = if (isTamil) "மொத்த வசூல்" else "Total Collected",
                            amount = formatInr(summary.totalReceived, settings.currency),
                            subtitle = null,
                            icon = Icons.Default.Savings,
                            iconBgColor = Color(0xFF0284C7),
                            cardBgColor = Color(0xFFF0F9FF),
                            borderColor = Color(0xFFBAE6FD).copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 2: Total Due & Available Business Balance
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Card 3: Total Due — from FinancialCalculationEngine (sum of pending amounts)
                        OverviewSummaryCard(
                            title = if (isTamil) "மொத்த பாக்கி" else "Total Due",
                            amount = formatInr(summary.totalDue, settings.currency),
                            subtitle = null,
                            icon = Icons.Default.PriorityHigh,
                            iconBgColor = Color(0xFFD97706),
                            cardBgColor = Color(0xFFFFFBEB),
                            borderColor = Color(0xFFFDE68A).copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        )

                        // Card 4: Available Business Balance — from FinancialCalculationEngine (received - expenses - withdrawals)
                        OverviewSummaryCard(
                            title = if (isTamil) "கிடைக்கும் இருப்பு" else "Available Business\nBalance",
                            amount = formatInr(summary.availableBalance, settings.currency),
                            subtitle = null,
                            icon = Icons.Default.AccountBalanceWallet,
                            iconBgColor = Color(0xFF9333EA),
                            cardBgColor = Color(0xFFFAF5FF),
                            borderColor = Color(0xFFE9D5FF).copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 3: Expenses & Partner Withdrawals — clickable action cards
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Card 5: Expenses — REAL total from FinancialCalculationEngine
                        OverviewActionCard(
                            title = if (isTamil) "செலவுகள்" else "Expenses",
                            amount = formatInr(summary.totalExpenses, settings.currency),
                            subtitle = if (isTamil) "செலவுகளைப் பார்க்க தட்டவும்" else "Tap to view expenses",
                            icon = Icons.Default.ReceiptLong,
                            iconBgColor = Color(0xFFEF4444),
                            cardBgColor = Color(0xFFFEF2F2),
                            borderColor = Color(0xFFFECACA).copy(alpha = 0.7f),
                            isEyeActive = isExpenseVisible,
                            onToggleEye = { isExpenseVisible = !isExpenseVisible },
                            onClick = { onNavigateToExpenses?.invoke() },
                            modifier = Modifier.weight(1f)
                        )

                        // Card 6: Partner Withdrawals — REAL total from FinancialCalculationEngine
                        OverviewActionCard(
                            title = if (isTamil) "பங்குதாரர் எடுப்புகள்" else "Partner Withdrawals",
                            amount = formatInr(summary.totalWithdrawals, settings.currency),
                            subtitle = if (isTamil) "எடுப்புகளைப் பார்க்க தட்டவும்" else "Tap to view withdrawals",
                            icon = Icons.Default.ArrowDownward,
                            iconBgColor = Color(0xFF7C3AED),
                            cardBgColor = Color(0xFFF5F3FF),
                            borderColor = Color(0xFFDDD6FE).copy(alpha = 0.7f),
                            isEyeActive = true,
                            onToggleEye = {},
                            onClick = { onNavigateToWithdrawals?.invoke() },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 3. DRIVER / OPERATOR EARNINGS CHART — REAL partner breakdown data
            // ─────────────────────────────────────────────────────────────
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Title row — matches reference: chart icon + title/subtitle + Working Metric Dropdown
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = null,
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (isTamil) "இயக்குநர் / ஆபரேட்டர் வருவாய்" else "Driver / Operator Earnings",
                                        fontSize = 14.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = when (selectedChartMetric) {
                                            OverviewChartMetric.AMOUNT -> if (isTamil) "ஒவ்வொரு இயக்குநரின் பதிவு செய்யப்பட்ட தொகை" else "Total recorded amount by each driver/operator"
                                            OverviewChartMetric.HOURS -> if (isTamil) "ஒவ்வொரு இயக்குநரின் வேலை நேரம்" else "Total hours worked by each driver/operator"
                                            OverviewChartMetric.JOBS -> if (isTamil) "ஒவ்வொரு இயக்குநரின் பணிகள் எண்ணிக்கை" else "Total jobs completed by each driver/operator"
                                        },
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Metric dropdown pill (Image 2 Panel 7)
                            Box {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color(0xFFF0FDF4),
                                    border = BorderStroke(1.dp, Color(0xFF86EFAC).copy(alpha = 0.5f)),
                                    modifier = Modifier.clickable { showMetricDropdown = true }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isTamil) selectedChartMetric.labelTa else selectedChartMetric.labelEn,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF15803D)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = Color(0xFF15803D),
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = showMetricDropdown,
                                    onDismissRequest = { showMetricDropdown = false },
                                    modifier = Modifier
                                        .background(Color.White)
                                        .clip(RoundedCornerShape(12.dp))
                                ) {
                                    OverviewChartMetric.values().forEach { metric ->
                                        val isSelected = selectedChartMetric == metric
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = if (isTamil) metric.labelTa else metric.labelEn,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    fontSize = 13.sp,
                                                    color = if (isSelected) Color(0xFF15803D) else Color(0xFF0F172A)
                                                )
                                            },
                                            leadingIcon = {
                                                when (metric) {
                                                    OverviewChartMetric.AMOUNT -> {
                                                        Text(
                                                            text = "₹",
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 15.sp,
                                                            color = Color(0xFF15803D),
                                                            modifier = Modifier.padding(start = 2.dp)
                                                        )
                                                    }
                                                    OverviewChartMetric.HOURS -> {
                                                        Icon(
                                                            imageVector = Icons.Default.Schedule,
                                                            contentDescription = null,
                                                            tint = Color(0xFF15803D),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                    OverviewChartMetric.JOBS -> {
                                                        Icon(
                                                            imageVector = Icons.AutoMirrored.Filled.List,
                                                            contentDescription = null,
                                                            tint = Color(0xFF15803D),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            trailingIcon = {
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = Color(0xFF15803D),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            },
                                            onClick = {
                                                selectedChartMetric = metric
                                                showMetricDropdown = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Controls: Show Expenses Toggle & Legend — matches reference layout
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // Legend dots
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF15803D))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = when (selectedChartMetric) {
                                        OverviewChartMetric.AMOUNT -> if (isTamil) "பணித் தொகை" else "Job Amount"
                                        OverviewChartMetric.HOURS -> if (isTamil) "வேலை நேரம்" else "Working Hours"
                                        OverviewChartMetric.JOBS -> if (isTamil) "பணிகள்" else "Job Count"
                                    },
                                    fontSize = 10.5.sp,
                                    color = Color(0xFF475569)
                                )

                                if (selectedChartMetric == OverviewChartMetric.AMOUNT && isExpenseVisible) {
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEF4444))
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isTamil) "செலவுகள் (மொத்தம்)" else "Expenses (Overall)",
                                        fontSize = 10.5.sp,
                                        color = Color(0xFF475569)
                                    )
                                }
                            }

                            // Show Expenses Switch with eye icon (Only in Amount mode)
                            if (selectedChartMetric == OverviewChartMetric.AMOUNT) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { isExpenseVisible = !isExpenseVisible }
                                ) {
                                    Text(
                                        text = if (isTamil) "செலவைக் காட்டு" else "Show Expenses",
                                        fontSize = 11.sp,
                                        color = Color(0xFF64748B),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = isExpenseVisible,
                                        onCheckedChange = { isExpenseVisible = it },
                                        modifier = Modifier.height(24.dp),
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFF15803D),
                                            uncheckedThumbColor = Color(0xFF94A3B8),
                                            uncheckedTrackColor = Color(0xFFE2E8F0)
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = if (isExpenseVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = "Toggle Expense",
                                        tint = Color(0xFF15803D),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Responsive Bar Chart — REAL data
                        DriverEarningsBarChart(
                            partnerBreakdowns = businessOverviewChartMembers(partnerBreakdowns, workspaceMembers, currentUid, settings.workspaceId),
                            workspaceMembers = workspaceMembers,
                            scopedJobs = scopedJobs,
                            totalExpenses = summary.totalExpenses,
                            showExpenses = isExpenseVisible,
                            currency = settings.currency,
                            selectedMember = selectedMemberFilter,
                            selectedMetric = selectedChartMetric,
                            isTamil = isTamil,
                            onSelectMember = { memberName ->
                                selectedMemberFilter = if (selectedMemberFilter == memberName) "All" else memberName
                            }
                        )
                        val unassigned = partnerBreakdowns.filter { row -> row.partnerUid.isBlank() || workspaceMembers.none {
                            it.workspaceId == settings.workspaceId && it.uid == row.partnerUid &&
                                (it.status.equals("ACTIVE", true) || it.status.equals("REMOVED", true))
                        } }
                        if (unassigned.isNotEmpty()) {
                            Text(
                                text = if (isTamil) "சில பழைய பதிவுகளின் பங்குதாரர் அடையாளத்தைச் சரிபார்க்க வேண்டும். மொத்தத் தொகையில் அவை சேர்க்கப்பட்டுள்ளன."
                                    else "Some older records need a verified partner assignment. Their amounts remain included in the business totals.",
                                color = TextMutedDark,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 12.dp).testTag("unassigned_records_notice")
                            )
                        }
                    }
                }
            }

            // ─────────────────────────────────────────────────────────────
            // 4. RECORDS SECTION — REAL job records filtered by person/scope
            // ─────────────────────────────────────────────────────────────
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Records Header Row — matches reference: list icon + title/subtitle + person pill
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.List,
                                    contentDescription = null,
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (isTamil) "பதிவுகள்" else "Records",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = if (isTamil) {
                                            if (selectedMemberFilter == "All") "அனைத்து பதிவுகளையும் காட்டுகிறது"
                                            else "$selectedMemberLabel-க்கான பதிவுகள்"
                                        } else {
                                            "Showing records for $selectedMemberLabel"
                                        },
                                        fontSize = 11.5.sp,
                                        color = Color(0xFF64748B)
                                    )
                                }
                            }

                            // Person Dropdown Pill — matches reference
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFFF0FDF4),
                                border = BorderStroke(1.dp, Color(0xFF86EFAC).copy(alpha = 0.6f)),
                                modifier = Modifier
                                    .clickable { showPersonSelectorDialog = true }
                                    .testTag("records_person_filter_pill")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedMemberLabel,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF15803D)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        tint = Color(0xFF15803D),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Real Records List — each record is a REAL JobEntryEntity
                        if (displayedJobs.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isTamil) "பதிவுகள் எதுவும் இல்லை" else "No records found",
                                    fontSize = 13.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                displayedJobs.forEach { job ->
                                    val jobTotal = if (job.totalAmount > 0) job.totalAmount else (job.amountReceived + job.pendingAmount)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFF8FAFC),
                                        border = BorderStroke(0.8.dp, Color(0xFFE2E8F0)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedJobForDetail = job }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                // Tractor icon in light green badge — matches reference
                                                Box(
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(Color(0xFFE8F5E9)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Agriculture,
                                                        contentDescription = null,
                                                        tint = Color(0xFF15803D),
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(10.dp))

                                                Column {
                                                    Text(
                                                        text = job.customerName.ifBlank { "Unknown Customer" },
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF0F172A),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    val ts = if (job.startTimeMillis > 0) job.startTimeMillis else job.createdAt
                                                    Text(
                                                        text = formatDateTime(ts),
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF64748B)
                                                    )
                                                }
                                            }

                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = formatInr(jobTotal, settings.currency),
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFF0F172A)
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Icon(
                                                    imageVector = Icons.Default.ChevronRight,
                                                    contentDescription = null,
                                                    tint = Color(0xFF94A3B8),
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
            }

            // Decorative footer spacing
            item {
                Spacer(modifier = Modifier.height(20.dp))
            }
        }

        // ─────────────────────────────────────────────────────────────
        // 5. PERSON SELECTOR MODAL WITH SEARCH — REAL members from app data
        // ─────────────────────────────────────────────────────────────
        if (showPersonSelectorDialog) {
            val allMembersList = remember(partnerBreakdowns, workspaceMembers, settings.workspaceId) {
                listOf("All" to "All") + (workspaceMembers.filter { it.uid.isNotBlank() && it.workspaceId == settings.workspaceId }
                    .map { it.uid to it.displayName.orEmpty().ifBlank { "Member" } } +
                    partnerBreakdowns.map { it.partnerUid to it.partnerName }).distinctBy { it.first }
            }

            PersonSelectorModal(
                members = allMembersList,
                selectedMember = selectedMemberFilter,
                isTamil = isTamil,
                onDismiss = { showPersonSelectorDialog = false },
                onSelectMember = { member ->
                    selectedMemberFilter = member
                    showPersonSelectorDialog = false
                }
            )
        }

        // ─────────────────────────────────────────────────────────────
        // 6. HIGH-LEVEL DATE PICKER DIALOGS (Day, Month, Year) — Image 2 Panels 3, 4, 5
        // ─────────────────────────────────────────────────────────────
        if (showDayPicker) {
            DayPickerDialog(
                initialDateMillis = selectedDateMillis,
                isTamil = isTamil,
                onDismiss = { showDayPicker = false },
                onApply = { chosenMillis, chosenYear, chosenMonth ->
                    selectedMode = FinancialScopeMode.DAILY
                    selectedDateMillis = chosenMillis
                    selectedYear = chosenYear
                    selectedMonth = chosenMonth
                    showDayPicker = false
                }
            )
        }

        if (showMonthPicker) {
            MonthPickerDialog(
                initialYear = selectedYear,
                initialMonth = selectedMonth,
                isTamil = isTamil,
                onDismiss = { showMonthPicker = false },
                onApply = { chosenYear, chosenMonth ->
                    selectedMode = FinancialScopeMode.MONTHLY
                    selectedYear = chosenYear
                    selectedMonth = chosenMonth
                    showMonthPicker = false
                }
            )
        }

        if (showYearPicker) {
            YearPickerDialog(
                initialYear = selectedYear,
                isTamil = isTamil,
                onDismiss = { showYearPicker = false },
                onApply = { chosenYear ->
                    selectedMode = FinancialScopeMode.YEARLY
                    selectedYear = chosenYear
                    showYearPicker = false
                }
            )
        }

        // ─────────────────────────────────────────────────────────────
        // 7. EXISTING JOB DETAILS POPUP — reuses existing CustomerSummaryPopup
        // ─────────────────────────────────────────────────────────────
        selectedJobForDetail?.let { selected -> jobs.find { it.workspaceId == selected.workspaceId && it.id == selected.id } }?.let { job ->
            CustomerSummaryPopup(
                job = job,
                settings = settings,
                expenses = expenses,
                isOwner = isOwner,
                currentUid = currentUid,
                workspaceMembers = workspaceMembers,
                partners = partners,
                onDismiss = { selectedJobForDetail = null },
                onCallCustomer = {
                    if (job.customerPhone.isNotBlank()) {
                        openDialer(context, job.customerPhone)
                    } else {
                        Toast.makeText(context, if (isTamil) "தொலைபேசி எண் இல்லை" else "No phone number available", Toast.LENGTH_SHORT).show()
                    }
                },
                onShareWhatsApp = {
                    val msg = buildJobWhatsAppMessage(job, settings.businessName, isTamil)
                    sendWhatsAppMessage(context, job.customerPhone, msg)
                },
                onSharePdf = {},
                onDownloadPdf = {},
                onDeleteRequest = {
                    onDeleteJob?.invoke(job)
                    selectedJobForDetail = null
                },
                onEditJob = { edited ->
                    onEditJob?.invoke(edited)
                    selectedJobForDetail = null
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENT: OVERVIEW SUMMARY CARD (TOP 4 CARDS)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun OverviewSummaryCard(
    title: String,
    amount: String,
    subtitle: String?,
    icon: ImageVector,
    iconBgColor: Color,
    cardBgColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier.height(108.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            Column {
                Text(
                    text = title,
                    fontSize = 11.5.sp,
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    lineHeight = 14.sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = amount,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A)
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            fontSize = 10.5.sp,
                            color = Color(0xFF64748B),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENT: OVERVIEW ACTION CARD (EXPENSES & WITHDRAWALS)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun OverviewActionCard(
    title: String,
    amount: String,
    subtitle: String,
    icon: ImageVector,
    iconBgColor: Color,
    cardBgColor: Color,
    borderColor: Color,
    isEyeActive: Boolean,
    onToggleEye: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBgColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .height(112.dp)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                }

                IconButton(
                    onClick = onToggleEye,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = if (isEyeActive) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = "Toggle Visibility",
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 11.5.sp,
                    color = Color(0xFF475569),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )
                Text(
                    text = amount,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A),
                    maxLines = 1,
                    lineHeight = 19.sp
                )
                Text(
                    text = subtitle,
                    fontSize = 9.5.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 12.sp
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENT: HORIZONTAL DRIVER EARNINGS BAR CHART — REAL data with horizontal bars
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DriverEarningsBarChart(
    partnerBreakdowns: List<PartnerFinancialBreakdown>,
    workspaceMembers: List<WorkspaceMember>,
    scopedJobs: List<JobEntryEntity>,
    totalExpenses: Double,
    showExpenses: Boolean,
    currency: String,
    selectedMember: String,
    selectedMetric: OverviewChartMetric,
    isTamil: Boolean,
    onSelectMember: (String) -> Unit
) {
    if (selectedMetric == OverviewChartMetric.AMOUNT) {
        DriverEarningsCircularChart(partnerBreakdowns, showExpenses, currency, isTamil)
        return
    }
    val metricItems = remember(partnerBreakdowns, workspaceMembers, scopedJobs) {
        if (partnerBreakdowns.isNotEmpty()) {
            partnerBreakdowns.map { pb ->
                val pJobs = scopedJobs.filter { it.financialAttributionUid.ifBlank { it.entryForUid } == pb.partnerUid }
                val minutes = pJobs.sumOf { it.durationMinutes }
                DriverMetricItem(
                    uid = pb.partnerUid,
                    name = pb.partnerName,
                    role = pb.role,
                    amount = pb.recorded,
                    due = pb.due,
                    collected = pb.received,
                    hours = minutes / 60.0,
                    durationMinutes = minutes,
                    jobCount = pJobs.size
                )
            }
        } else {
            workspaceMembers.filter { it.uid.isNotBlank() }.distinctBy { it.uid }.map { m ->
                val pName = m.displayName ?: m.phoneNumber ?: "Member"
                val pJobs = scopedJobs.filter { it.financialAttributionUid.ifBlank { it.entryForUid } == m.uid }
                val minutes = pJobs.sumOf { it.durationMinutes }
                val totalAmt = pJobs.sumOf { it.totalAmount }
                val receivedAmt = pJobs.sumOf { it.amountReceived }
                val pendingAmt = pJobs.sumOf { it.pendingAmount }
                DriverMetricItem(
                    uid = m.uid,
                    name = pName,
                    role = m.role,
                    amount = totalAmt,
                    hours = minutes / 60.0,
                    durationMinutes = minutes,
                    jobCount = pJobs.size,
                    due = pendingAmt,
                    collected = receivedAmt
                )
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        metricItems.forEach { item ->
            val isSelected = selectedMember == item.uid
            val isAmountMode = selectedMetric == OverviewChartMetric.AMOUNT

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp)
                    .clickable { onSelectMember(item.uid) }
            ) {
                Text(
                    text = item.name,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color(0xFF0D5E3A) else Color(0xFF0F172A),
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFF1F5F9))
                    ) {
                        if (isAmountMode && item.amount > 0) {
                            val fractions = com.example.ui.util.earningsBarSegments(item.amount, item.due, item.collected)
                            val colors = listOf(AlertDueRed, SuccessPaidGreen, Color(0xFF2563EB))
                            Row(Modifier.fillMaxSize()) {
                                fractions.forEachIndexed { index, fraction ->
                                    if (fraction > 0f) {
                                        Box(
                                            modifier = Modifier
                                                .weight(fraction)
                                                .fillMaxHeight()
                                                .background(colors[index]),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val pct = (fraction * 100).toInt()
                                            if (pct >= 15) {
                                                Text(
                                                    text = "$pct%",
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (!isAmountMode) {
                            val maxVal = when (selectedMetric) {
                                OverviewChartMetric.HOURS -> metricItems.maxOfOrNull { it.hours } ?: 1.0
                                OverviewChartMetric.JOBS -> metricItems.maxOfOrNull { it.jobCount }?.toDouble() ?: 1.0
                                else -> 1.0
                            }
                            val itemVal = when (selectedMetric) {
                                OverviewChartMetric.HOURS -> item.hours
                                OverviewChartMetric.JOBS -> item.jobCount.toDouble()
                                else -> 0.0
                            }
                            val ratio = if (maxVal > 0) (itemVal / maxVal).coerceIn(0.0, 1.0).toFloat() else 0f
                            Row(Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(ratio)
                                        .fillMaxHeight()
                                        .background(if (isSelected) Color(0xFF0D5E3A) else Color(0xFF22C55E))
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = when (selectedMetric) {
                            OverviewChartMetric.AMOUNT -> formatInr(item.amount, currency)
                            OverviewChartMetric.HOURS -> if (item.durationMinutes > 0) WorkBillingCalculator.formatDuration(item.durationMinutes) else "0h"
                            OverviewChartMetric.JOBS -> "${item.jobCount}"
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        modifier = Modifier.width(60.dp),
                        textAlign = TextAlign.End
                    )
                }

                if (isAmountMode && item.amount > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (item.collected > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(SuccessPaidGreen, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${if (isTamil) "வசூல்" else "Collected"}: ${formatInr(item.collected, currency)}",
                                    fontSize = 9.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                        if (item.due > 0) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(AlertDueRed, RoundedCornerShape(2.dp))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "${if (isTamil) "பாக்கி" else "Due"}: ${formatInr(item.due, currency)}",
                                    fontSize = 9.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
// COMPONENT: PERSON SELECTOR MODAL WITH SEARCH — searches REAL member data
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun PersonSelectorModal(
    members: List<Pair<String, String>>,
    selectedMember: String,
    isTamil: Boolean,
    onDismiss: () -> Unit,
    onSelectMember: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filteredMembers = remember(members, searchQuery, isTamil) {
        if (searchQuery.isBlank()) members
        else members.filter {
            val label = if (it.first == "All" && isTamil) "அனைவரும்" else it.second
            label.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isTamil) "நபரைத் தேர்ந்தெடுக்கவும்" else "Select Person",
                        modifier = Modifier.weight(1f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF64748B))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search field — supports partial, case-insensitive search of REAL members
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text(if (isTamil) "நபரைத் தேடுங்கள்..." else "Search person...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF15803D), modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF15803D),
                        unfocusedBorderColor = Color(0xFFE2E8F0)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Member List — REAL members
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredMembers) { member ->
                        val isSelected = selectedMember == member.first
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) Color(0xFFF0FDF4) else Color.Transparent,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectMember(member.first) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (member.first == "All") Icons.Default.Groups else Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isSelected) Color(0xFF15803D) else Color(0xFF64748B),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (member.first == "All" && isTamil) "அனைவரும்" else member.second,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color(0xFF15803D) else Color(0xFF0F172A)
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = Color(0xFF15803D),
                                        modifier = Modifier.size(18.dp)
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

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENT: DAY PICKER DIALOG — Image 2 Panel 3
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun DayPickerDialog(
    initialDateMillis: Long,
    isTamil: Boolean,
    onDismiss: () -> Unit,
    onApply: (Long, Int, Int) -> Unit
) {
    val initialCal = remember(initialDateMillis) {
        Calendar.getInstance().apply {
            if (initialDateMillis > 0) timeInMillis = initialDateMillis
        }
    }
    var viewYear by remember { mutableStateOf(initialCal.get(Calendar.YEAR)) }
    var viewMonth by remember { mutableStateOf(initialCal.get(Calendar.MONTH)) } // 0..11
    var chosenDay by remember { mutableStateOf(initialCal.get(Calendar.DAY_OF_MONTH)) }
    var chosenMonth by remember { mutableStateOf(initialCal.get(Calendar.MONTH)) }
    var chosenYear by remember { mutableStateOf(initialCal.get(Calendar.YEAR)) }

    val monthNamesEn = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    val monthNamesTa = listOf("ஜனவரி", "பிப்ரவரி", "மார்ச்", "ஏப்ரல்", "மே", "ஜூன்", "ஜூலை", "ஆகஸ்ட்", "செப்டம்பர்", "அக்டோபர்", "நவம்பர்", "டிசம்பர்")
    val dayHeadersEn = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val dayHeadersTa = listOf("ஞா", "தி", "செ", "பு", "வி", "வெ", "ச")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header (matching reference Image 2 Panel 3)
                Surface(
                    color = Color(0xFF0D3B23),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isTamil) "நாளைத் தேர்ந்தெடுக்கவும்" else "Select Day",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Column(modifier = Modifier.padding(18.dp)) {
                    // Month & Year navigator: < September 2026 >
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (viewMonth == 0) {
                                    viewMonth = 11
                                    viewYear--
                                } else {
                                    viewMonth--
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous Month",
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Text(
                            text = "${if (isTamil) monthNamesTa[viewMonth] else monthNamesEn[viewMonth]} $viewYear",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )

                        IconButton(
                            onClick = {
                                if (viewMonth == 11) {
                                    viewMonth = 0
                                    viewYear++
                                } else {
                                    viewMonth++
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next Month",
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Weekday headers
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        val headers = if (isTamil) dayHeadersTa else dayHeadersEn
                        headers.forEach { h ->
                            Box(
                                modifier = Modifier.weight(1f),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = h,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Calendar grid computation
                    val cal = remember(viewYear, viewMonth) {
                        Calendar.getInstance().apply {
                            set(Calendar.YEAR, viewYear)
                            set(Calendar.MONTH, viewMonth)
                            set(Calendar.DAY_OF_MONTH, 1)
                        }
                    }
                    val firstDayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
                    val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                    val leadingBlanks = (firstDayOfWeek - Calendar.SUNDAY + 7) % 7
                    val totalSlots = leadingBlanks + daysInMonth
                    val rowCount = (totalSlots + 6) / 7

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (r in 0 until rowCount) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                for (c in 0 until 7) {
                                    val slotIndex = (r * 7) + c
                                    val dayNum = slotIndex - leadingBlanks + 1
                                    if (slotIndex < leadingBlanks || dayNum > daysInMonth) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    } else {
                                        val isSelected = (dayNum == chosenDay && viewMonth == chosenMonth && viewYear == chosenYear)
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .aspectRatio(1f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(34.dp)
                                                    .clip(CircleShape)
                                                    .background(if (isSelected) Color(0xFF15803D) else Color.Transparent)
                                                    .clickable {
                                                        chosenDay = dayNum
                                                        chosenMonth = viewMonth
                                                        chosenYear = viewYear
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "$dayNum",
                                                    fontSize = 12.5.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (isSelected) Color.White else Color(0xFF0F172A)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Bottom action buttons: Cancel & Apply
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF475569)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = if (isTamil) "ரத்து செய்" else "Cancel", fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = {
                                val targetCal = Calendar.getInstance().apply {
                                    set(chosenYear, chosenMonth, chosenDay, 0, 0, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }
                                onApply(targetCal.timeInMillis, chosenYear, chosenMonth + 1)
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = if (isTamil) "பயன்படுத்து" else "Apply", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENT: MONTH PICKER DIALOG — Image 2 Panel 4
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun MonthPickerDialog(
    initialYear: Int,
    initialMonth: Int, // 1..12
    isTamil: Boolean,
    onDismiss: () -> Unit,
    onApply: (Int, Int) -> Unit
) {
    var chosenYear by remember { mutableStateOf(initialYear) }
    var chosenMonth by remember { mutableStateOf(initialMonth.coerceIn(1, 12)) }

    val monthNamesEn = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val monthNamesTa = listOf("ஜன", "பிப்", "மார்ச்", "ஏப்", "மே", "ஜூன்", "ஜூலை", "ஆக", "செப்", "அக்", "நவ", "டிச")

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header (matching reference Image 2 Panel 4)
                Surface(
                    color = Color(0xFF0D3B23),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isTamil) "மாதத்தைத் தேர்ந்தெடுக்கவும்" else "Select Month",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Column(modifier = Modifier.padding(18.dp)) {
                    // Year navigator: < 2026 >
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { chosenYear-- },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous Year",
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "$chosenYear",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        IconButton(
                            onClick = { chosenYear++ },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next Year",
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 12 months grid (4 columns x 3 rows)
                    val months = if (isTamil) monthNamesTa else monthNamesEn
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (row in 0 until 3) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (col in 0 until 4) {
                                    val monthIdx = (row * 4) + col // 0..11
                                    val monthNum = monthIdx + 1 // 1..12
                                    val isSelected = chosenMonth == monthNum

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) Color(0xFF15803D) else Color(0xFFF8FAFC),
                                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF15803D) else Color(0xFFE2E8F0)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { chosenMonth = monthNum }
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 12.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = months[monthIdx],
                                                fontSize = 13.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else Color(0xFF0F172A)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF475569)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = if (isTamil) "ரத்து செய்" else "Cancel", fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = { onApply(chosenYear, chosenMonth) },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = if (isTamil) "பயன்படுத்து" else "Apply", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENT: YEAR PICKER DIALOG — Image 2 Panel 5
// ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun YearPickerDialog(
    initialYear: Int,
    isTamil: Boolean,
    onDismiss: () -> Unit,
    onApply: (Int) -> Unit
) {
    var pageBaseYear by remember { mutableStateOf((initialYear / 9) * 9) }
    var chosenYear by remember { mutableStateOf(initialYear) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header (matching reference Image 2 Panel 5)
                Surface(
                    color = Color(0xFF0D3B23),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = if (isTamil) "ஆண்டைத் தேர்ந்தெடுக்கவும்" else "Select Year",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                Column(modifier = Modifier.padding(18.dp)) {
                    // Page navigation: < 2024 - 2032 >
                    val startYear = pageBaseYear
                    val endYear = pageBaseYear + 8
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { pageBaseYear -= 9 },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Previous Page",
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Text(
                            text = "$startYear - $endYear",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )

                        IconButton(
                            onClick = { pageBaseYear += 9 },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Next Page",
                                tint = Color(0xFF15803D),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 9 years grid (3 columns x 3 rows)
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (row in 0 until 3) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                for (col in 0 until 3) {
                                    val y = pageBaseYear + (row * 3) + col
                                    val isSelected = chosenYear == y

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) Color(0xFF15803D) else Color(0xFFF8FAFC),
                                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF15803D) else Color(0xFFE2E8F0)),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { chosenYear = y }
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 14.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "$y",
                                                fontSize = 14.sp,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.White else Color(0xFF0F172A)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    // Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF475569)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = if (isTamil) "ரத்து செய்" else "Cancel", fontWeight = FontWeight.SemiBold)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Button(
                            onClick = { onApply(chosenYear) },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF15803D)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = if (isTamil) "பயன்படுத்து" else "Apply", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DriverEarningsCircularChart(breakdowns: List<PartnerFinancialBreakdown>, showExpenses: Boolean, currency: String, isTamil: Boolean) {
    val data = remember(breakdowns, showExpenses) {
        breakdowns.map { MemberFinancialData(it.partnerUid, it.partnerName, it.role, it.recorded, it.received, it.due, if (showExpenses) it.totalExpenses else 0.0) }
    }
    var selected by remember(data) { mutableStateOf<MemberFinancialData?>(null) }
    androidx.compose.foundation.lazy.LazyRow(Modifier.fillMaxWidth().heightIn(min = 180.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)) {
        items(data, key = { it.uid }) { item -> MemberCircularDiagram(item, currency, isTamil) { selected = item } }
    }
    selected?.let { MemberDetailBottomSheet(it, currency, { selected = null }, isTamil) }
}

@Composable
private fun MemberCircularDiagram(
    data: MemberFinancialData,
    currency: String,
    isTamil: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(128.dp)
            .testTag("business_member_circle_${data.uid}")
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        // Radial Chart
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(76.dp)) {
            Canvas(modifier = Modifier.fillMaxSize().padding(6.dp)) {
                val strokeWidth = 7.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2

                // Background circle
                drawCircle(
                    color = Color(0xFFF1F5F9),
                    radius = radius,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth)
                )

                // Segments
                var startAngle = -90f

                // Collected (Green)
                if (data.collectedPercent > 0) {
                    val sweep = (data.collectedPercent / 100f) * 360f
                    drawArc(
                        color = Color(0xFF16A34A),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    startAngle += sweep
                }
                // Due (Orange)
                if (data.duePercent > 0) {
                    val sweep = (data.duePercent / 100f) * 360f
                    drawArc(
                        color = Color(0xFFD97706),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    startAngle += sweep
                }
                // Expenses (Red)
                if (data.expensesPercent > 0) {
                    val sweep = (data.expensesPercent / 100f) * 360f
                    drawArc(
                        color = Color(0xFFDC2626),
                        startAngle = startAngle,
                        sweepAngle = sweep,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
            }

            // Percentage in middle (Primary metric: Collection success)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${data.collectedPercent}%",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = if (isTamil) "வசூல்" else "Collected",
                    fontSize = 8.sp,
                    color = Color(0xFF64748B),
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Identity & Values
        Text(
            text = formatInr(data.total, currency),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF0F172A),
            maxLines = 1
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = data.name,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF334155),
            maxLines = 2,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${if (isTamil) "வசூல்" else "Collected"}: ${formatInr(data.collected, currency)}",
            fontSize = 11.sp, color = Color(0xFF15803D), textAlign = TextAlign.Center
        )
        Text(
            text = "${if (isTamil) "பாக்கி" else "Due"}: ${formatInr(data.due, currency)}",
            fontSize = 11.sp, color = Color(0xFFB45309), textAlign = TextAlign.Center
        )

        // Role Badge/Label
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = when(data.role.lowercase()) {
                "owner" -> Color(0xFFFEF2F2)
                "partner" -> Color(0xFFF0FDF4)
                else -> Color(0xFFF1F5F9)
            },
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Text(
                text = if (data.role.isBlank()) "" else com.example.data.auth.RoleUtils.getRoleDisplayName(data.role, isTamil),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = when(data.role.lowercase()) {
                    "owner" -> Color(0xFF991B1B)
                    "partner" -> Color(0xFF166534)
                    else -> Color(0xFF475569)
                },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemberDetailBottomSheet(
    data: MemberFinancialData,
    currency: String,
    onDismiss: () -> Unit,
    isTamil: Boolean
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(modifier = Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .navigationBarsPadding()
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = data.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = if (data.role.isBlank()) "" else com.example.data.auth.RoleUtils.getRoleDisplayName(data.role, isTamil),
                        fontSize = 14.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatInr(data.total, currency),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFF16A34A)
                    )
                    Text(
                        text = if (isTamil) "மொத்த பதிவு" else "Total Recorded",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = if (isTamil) "நிதி முறிவு" else "Financial Breakdown",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF475569),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Detail items with visual indicators (using reconciled percentages)
                    DetailBreakdownRow(
                        label = if (isTamil) "வசூலிக்கப்பட்டது" else "Collected",
                        value = formatInr(data.collected, currency),
                        percent = "${data.collectedPercent}%",
                        color = Color(0xFF16A34A)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    DetailBreakdownRow(
                        label = if (isTamil) "பாக்கி" else "Due",
                        value = formatInr(data.due, currency),
                        percent = "${data.duePercent}%",
                        color = Color(0xFFD97706)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    DetailBreakdownRow(
                        label = if (isTamil) "செலவுகள்" else "Expenses",
                        value = formatInr(data.expenses, currency),
                        percent = "${data.expensesPercent}%",
                        color = Color(0xFFDC2626)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DetailBreakdownRow(
    label: String,
    value: String,
    percent: String,
    color: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = Color(0xFF334155),
            fontWeight = FontWeight.Medium
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = value,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A)
            )
            Text(
                text = percent,
                fontSize = 11.sp,
                color = color,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, amount: String, percentage: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = label, fontSize = 14.sp, color = Color(0xFF475569))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = amount, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(text = percentage, fontSize = 12.sp, color = Color(0xFF64748B))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// COMPONENT: PERSON SELECTOR MODAL WITH SEARCH — searches REAL member data
// ─────────────────────────────────────────────────────────────────────────────
