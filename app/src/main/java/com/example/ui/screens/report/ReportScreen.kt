package com.example.ui.screens.report

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.PendingActions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity
import com.example.ui.components.formatInr
import com.example.ui.theme.AlertDueRed
import com.example.ui.theme.AlertDueRedBg
import com.example.ui.theme.AppTheme
import com.example.ui.theme.DeepSageGreen
import com.example.ui.theme.EarthGold
import com.example.ui.theme.EarthGoldSoft
import com.example.ui.theme.ForestGreenHeader
import com.example.ui.theme.SageAccent
import com.example.ui.theme.SageCardBg
import com.example.ui.theme.SoftSageGreen
import com.example.ui.theme.SuccessPaidGreen
import com.example.ui.theme.SuccessPaidGreenBg
import com.example.ui.viewmodel.ReportSubPage

@Composable
fun ReportScreen(
    currentSubPage: ReportSubPage,
    onSubPageSelected: (ReportSubPage) -> Unit,
    settings: AppSettingsEntity,
    expenses: List<ExpenseEntity>,
    jobs: List<JobEntryEntity>,
    customers: List<CustomerEntity>,
    withdrawals: List<WithdrawalEntity>,
    partners: List<PartnerEntity>,
    tractors: List<TractorEntity>,
    totalSales: Double,
    totalExpenses: Double,
    netBalance: Double,
    availableAmount: Double,
    totalWithdrawn: Double,
    onAddExpense: (ExpenseEntity) -> Unit,
    onUpdateExpense: (ExpenseEntity) -> Unit,
    onDeleteExpense: (ExpenseEntity) -> Unit,
    canDeleteExpense: ((ExpenseEntity) -> Boolean)? = null,
    onAddWithdrawal: (WithdrawalEntity) -> Unit,
    onDeleteWithdrawal: (WithdrawalEntity) -> Unit,
    onUpdateCustomer: (CustomerEntity) -> Unit = {},
    onDeleteCustomer: ((CustomerEntity) -> Unit)? = null,
    onRecordPayment: ((CustomerEntity, Double, Long, String, String) -> Unit)? = null,
    onRecordPaymentWithCollector: ((CustomerEntity, Double, Long, String, String, String, String, String) -> Unit)? = null,
    onEditJob: ((JobEntryEntity) -> Unit)? = null,
    onDeleteJob: ((JobEntryEntity) -> Unit)? = null,
    isOwner: Boolean = false,
    actorName: String = "",
    actorUid: String = "",
    currentUserRole: String = "",
    workspaceMembers: List<com.example.data.firebase.WorkspaceMember> = emptyList(),
    payments: List<com.example.data.entity.PaymentEntity> = emptyList(),
    onStartOldEntry: ((Long) -> Unit)? = null,
    initialScope: com.example.ui.util.FinancialScope = com.example.ui.util.FinancialScope(mode = com.example.ui.util.FinancialScopeMode.OVERALL),
    onUpdateFinancialScope: ((com.example.ui.util.FinancialScope) -> Unit)? = null,
    onDeleteCollection: ((Long, String, Long) -> Unit)? = null,
    partnerPercentages: Map<String, Int> = emptyMap(),
    onUpdatePartnerPercentages: (Map<String, Int>) -> Unit = {},
    profitShareAllocations: Map<String, Int> = emptyMap(),
    profitShareAllocationDetails: Map<String, com.example.data.firebase.ProfitShareAllocation> = emptyMap(),
    onUpdateProfitShareAllocations: (Map<String, Int>, Map<String, com.example.data.firebase.ProfitShareAllocation>) -> Unit = { _, _ -> }
) {
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colors.background)
    ) {
        when (currentSubPage) {
            ReportSubPage.MENU, ReportSubPage.OLD_ENTRY -> {
                val synchronizedCustomers = remember(customers, jobs) {
                    com.example.ui.util.FinancialCalculationEngine.synchronizeCustomers(customers, jobs)
                }
                ReportMenuDashboard(
                    settings = settings,
                    expensesCount = expenses.size,
                    totalExpenses = totalExpenses,
                    netBalance = netBalance,
                    totalWithdrawn = totalWithdrawn,
                    totalPendingDue = synchronizedCustomers.filter { it.balanceDue > 0 }.sumOf { it.balanceDue },
                    pendingCustomersCount = synchronizedCustomers.count { it.balanceDue > 0 },
                    payments = payments,
                    jobs = jobs,
                    isOwner = isOwner,
                    initiateOldDatePick = currentSubPage == ReportSubPage.OLD_ENTRY && isOwner && com.example.data.auth.AuthorizationManager.canCreateOldEntry(isOwner),
                    onStartOldEntry = onStartOldEntry,
                    onNavigate = onSubPageSelected
                )
            }
            ReportSubPage.EXPENSES -> {
                ExpensesTab(
                    settings = settings,
                    expenses = expenses,
                    jobs = jobs,
                    tractors = tractors,
                    partners = partners,
                    onAddExpense = onAddExpense,
                    onUpdateExpense = onUpdateExpense,
                    onDeleteExpense = onDeleteExpense,
                    canDeleteExpense = { exp -> canDeleteExpense?.invoke(exp) ?: true }
                )
            }
            ReportSubPage.BALANCE_SHEET -> {
                BalanceSheetTab(
                    settings = settings,
                    jobs = jobs,
                    expenses = expenses,
                    withdrawals = withdrawals,
                    partners = partners,
                    workspaceMembers = workspaceMembers,
                    customers = customers,
                    payments = payments,
                    totalSales = totalSales,
                    totalExpenses = totalExpenses,
                    netBalance = netBalance,
                    initialScope = initialScope,
                    onUpdateFinancialScope = onUpdateFinancialScope
                )
            }
            ReportSubPage.BUSINESS_OVERVIEW -> {
                BusinessOverviewScreen(
                    settings = settings,
                    jobs = jobs,
                    expenses = expenses,
                    withdrawals = withdrawals,
                    partners = partners,
                    workspaceMembers = workspaceMembers,
                    customers = customers,
                    payments = payments,
                    totalSales = totalSales,
                    totalExpenses = totalExpenses,
                    netBalance = netBalance,
                    initialScope = initialScope,
                    onUpdateFinancialScope = onUpdateFinancialScope,
                    onNavigateToExpenses = { onSubPageSelected(ReportSubPage.EXPENSES) },
                    onNavigateToWithdrawals = { onSubPageSelected(ReportSubPage.WITHDRAWAL) },
                    onBack = { onSubPageSelected(ReportSubPage.BALANCE_SHEET) },
                    onEditJob = onEditJob,
                    onDeleteJob = onDeleteJob,
                    isOwner = isOwner
                )
            }
            ReportSubPage.WITHDRAWAL -> {
                WithdrawalTab(
                    settings = settings,
                    withdrawals = withdrawals,
                    partners = partners,
                    availableAmount = availableAmount,
                    totalWithdrawn = totalWithdrawn,
                    onAddWithdrawal = onAddWithdrawal,
                    onDeleteWithdrawal = onDeleteWithdrawal,
                    expenses = expenses,
                    jobs = jobs,
                    workspaceMembers = workspaceMembers,
                    isOwner = isOwner,
                    currentUserName = actorName,
                    partnerPercentages = partnerPercentages,
                    onUpdatePartnerPercentages = onUpdatePartnerPercentages,
                    profitShareAllocations = profitShareAllocations,
                    profitShareAllocationDetails = profitShareAllocationDetails,
                    onUpdateProfitShareAllocations = onUpdateProfitShareAllocations
                )
            }
            ReportSubPage.CUSTOMER_CREDIT_DUE -> {
                CustomerCreditDueTab(
                    settings = settings,
                    customers = customers,
                    jobs = jobs,
                    onUpdateCustomer = onUpdateCustomer,
                    onRecordPayment = onRecordPayment,
                    onRecordPaymentWithCollector = onRecordPaymentWithCollector,
                    onEditJob = onEditJob,
                    onDeleteJob = onDeleteJob,
                    onDeleteCustomer = onDeleteCustomer,
                    isOwner = isOwner,
                    actorName = actorName,
                    workspaceMembers = workspaceMembers,
                    partners = partners,
                    expenses = expenses
                )
            }
            ReportSubPage.COLLECTION_HISTORY -> {
                CollectionHistoryTab(
                    settings = settings,
                    payments = payments,
                    jobs = jobs,
                    workspaceMembers = workspaceMembers,
                    partners = partners,
                    isOwner = isOwner,
                    ownerName = actorName,
                    actorUid = actorUid,
                    currentUserRole = currentUserRole,
                    onDeleteCollection = onDeleteCollection
                )
            }
        }
    }
}

@Composable
fun ReportMenuDashboard(
    settings: AppSettingsEntity,
    expensesCount: Int,
    totalExpenses: Double,
    netBalance: Double,
    totalWithdrawn: Double,
    totalPendingDue: Double,
    pendingCustomersCount: Int,
    payments: List<com.example.data.entity.PaymentEntity> = emptyList(),
    jobs: List<com.example.data.entity.JobEntryEntity> = emptyList(),
    isOwner: Boolean = false,
    initiateOldDatePick: Boolean = false,
    onStartOldEntry: ((Long) -> Unit)? = null,
    onNavigate: (ReportSubPage) -> Unit
) {
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    val context = androidx.compose.ui.platform.LocalContext.current

    fun openDatePicker() {
        val cal = java.util.Calendar.getInstance()
        val picker = android.app.DatePickerDialog(
            context,
            { _, y, m, d ->
                val selected = java.util.Calendar.getInstance().apply {
                    set(y, m, d)
                }
                onStartOldEntry?.invoke(selected.timeInMillis)
            },
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH),
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
        picker.datePicker.maxDate = System.currentTimeMillis()
        picker.show()
    }

    androidx.compose.runtime.LaunchedEffect(initiateOldDatePick) {
        if (initiateOldDatePick) {
            openDatePicker()
        }
    }

    val totalCollected = payments.sumOf { it.amount }
    val collectionsCount = com.example.ui.util.FinancialCalculationEngine.countConsolidatedCollectionRecords(payments, jobs)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF072D18))
    ) {
        // Subtitle banner under dark green top app bar matching reference design
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isTamil) "உங்கள் வணிக செயல்திறனைக் கண்காணிக்கவும்" else "Track your business performance",
                fontSize = 12.sp,
                color = Color(0xFFA5D6A7),
                fontWeight = FontWeight.Normal
            )
        }

        // Curved light content surface holding the Business Overview and 2x3 Grid
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = Color(0xFFF6F8F6)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    horizontal = if (responsive.isSmallPhone) 12.dp else 16.dp,
                    vertical = 14.dp
                ),
                verticalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                // Business Overview Header Card
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(ReportSubPage.BUSINESS_OVERVIEW) }
                            .testTag("report_business_overview_card")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF0F5132)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.BarChart,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(
                                    text = if (isTamil) "வணிக மேலோட்டம்" else "Business Overview",
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isTamil) "முக்கிய தகவல்கள் & சுருக்கம்" else "Key insights and performance summary",
                                    fontSize = 11.5.sp,
                                    color = Color(0xFF64748B),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            // Sparkline trend curve
                            Box(
                                modifier = Modifier
                                    .width(60.dp)
                                    .height(28.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val path = Path().apply {
                                        moveTo(0f, size.height * 0.75f)
                                        cubicTo(
                                            size.width * 0.35f, size.height * 0.95f,
                                            size.width * 0.45f, size.height * 0.15f,
                                            size.width * 0.70f, size.height * 0.45f
                                        )
                                        cubicTo(
                                            size.width * 0.80f, size.height * 0.55f,
                                            size.width * 0.90f, size.height * 0.10f,
                                            size.width, size.height * 0.20f
                                        )
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color(0xFF10B981),
                                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = Color(0xFF0F172A),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Row 1: Expenses & Balance Sheet
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        ReportTileCard(
                            title = if (isTamil) "செலவுகள்" else "Expenses",
                            subtitle = if (isTamil) "டீசல், பராமரிப்பு & செலவுகள்" else "Diesel, repairs & costs",
                            icon = Icons.Default.LocalGasStation,
                            iconBgColor = Color(0xFFD1FAE5),
                            iconTint = Color(0xFF047857),
                            badgeText = if (isTamil) "$expensesCount பதிவுகள்" else "$expensesCount entries",
                            badgeBgColor = Color(0xFFECFDF5),
                            badgeTextColor = Color(0xFF065F46),
                            cornerAuraColor = Color(0xFF10B981).copy(alpha = 0.09f),
                            testTag = "report_menu_card_expenses",
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(ReportSubPage.EXPENSES) }
                        )

                        ReportTileCard(
                            title = if (isTamil) "இருப்புநிலை" else "Balance Sheet",
                            subtitle = if (isTamil) "லாப நஷ்ட அறிக்கை" else "Profit & loss statement",
                            icon = Icons.Default.BarChart,
                            iconBgColor = Color(0xFFDBEAFE),
                            iconTint = Color(0xFF2563EB),
                            badgeText = if (netBalance >= 0) (if (isTamil) "லாபம்" else "Profitable") else (if (isTamil) "நஷ்டம்" else "Loss"),
                            badgeBgColor = if (netBalance >= 0) Color(0xFFECFDF5) else Color(0xFFFEF2F2),
                            badgeTextColor = if (netBalance >= 0) Color(0xFF065F46) else Color(0xFFDC2626),
                            cornerAuraColor = Color(0xFF3B82F6).copy(alpha = 0.09f),
                            testTag = "report_menu_card_balance_sheet",
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(ReportSubPage.BALANCE_SHEET) }
                        )
                    }
                }

                // Row 2: Withdrawal & Customer Dues
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        ReportTileCard(
                            title = if (isTamil) "எடுப்பு" else "Withdrawal",
                            subtitle = if (isTamil) "பங்குதாரர் லாபப் பங்கீடு" else "Partner share distribution",
                            icon = Icons.Default.AccountBalanceWallet,
                            iconBgColor = Color(0xFFFEF3C7),
                            iconTint = Color(0xFFD97706),
                            badgeText = if (isTamil) "பங்குப் பிரிவு" else "Partner Split",
                            badgeBgColor = Color(0xFFFFFBEB),
                            badgeTextColor = Color(0xFFB45309),
                            cornerAuraColor = Color(0xFFF59E0B).copy(alpha = 0.09f),
                            testTag = "report_menu_card_withdrawal",
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(ReportSubPage.WITHDRAWAL) }
                        )

                        ReportTileCard(
                            title = if (isTamil) "வாடிக்கையாளர் பாக்கி" else "Customer Dues",
                            subtitle = if (isTamil) "நிலுவை பாக்கிகள்" else "Outstanding balances",
                            icon = Icons.Default.PendingActions,
                            iconBgColor = Color(0xFFFFE4E6),
                            iconTint = Color(0xFFE11D48),
                            badgeText = if (isTamil) "$pendingCustomersCount பாக்கிகள்" else "$pendingCustomersCount dues",
                            badgeBgColor = Color(0xFFFFF1F2),
                            badgeTextColor = Color(0xFFBE123C),
                            cornerAuraColor = Color(0xFFF43F5E).copy(alpha = 0.09f),
                            testTag = "report_menu_card_customer_dues",
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(ReportSubPage.CUSTOMER_CREDIT_DUE) }
                        )
                    }
                }

                // Row 3: Collection History & Old History
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(11.dp)
                    ) {
                        ReportTileCard(
                            title = if (isTamil) "வசூல் வரலாறு" else "Collection History",
                            subtitle = if (isTamil) "வாடிக்கையாளர் வசூல்கள்" else "Payment collections",
                            icon = Icons.Default.History,
                            iconBgColor = Color(0xFFCCFBF1),
                            iconTint = Color(0xFF0F766E),
                            badgeText = if (isTamil) "$collectionsCount வசூல்" else "$collectionsCount collected",
                            badgeBgColor = Color(0xFFF0FDFA),
                            badgeTextColor = Color(0xFF115E59),
                            cornerAuraColor = Color(0xFF14B8A6).copy(alpha = 0.09f),
                            testTag = "report_menu_card_collection_history",
                            modifier = Modifier.weight(1f),
                            onClick = { onNavigate(ReportSubPage.COLLECTION_HISTORY) }
                        )

                        val canShowOldEntry = isOwner && com.example.data.auth.AuthorizationManager.canCreateOldEntry(isOwner)
                        if (canShowOldEntry) {
                            ReportTileCard(
                                title = if (isTamil) "பழைய பதிவு" else "Old History",
                                subtitle = if (isTamil) "முந்தைய பதிவுகள்" else "Previous entries & records",
                                icon = Icons.Default.Description,
                                iconBgColor = Color(0xFFEDE9FE),
                                iconTint = Color(0xFF7C3AED),
                                badgeText = if (isTamil) "உரிமையாளர் மட்டும்" else "Owner Only",
                                badgeBgColor = Color(0xFFF5F3FF),
                                badgeTextColor = Color(0xFF6D28D9),
                                cornerAuraColor = Color(0xFF8B5CF6).copy(alpha = 0.09f),
                                testTag = "report_menu_card_old_entry",
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    openDatePicker()
                                }
                            )
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReportTileCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    badgeText: String,
    badgeBgColor: Color,
    badgeTextColor: Color,
    cornerAuraColor: Color,
    testTag: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(cornerAuraColor, Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(size.width * 0.95f, size.height * 0.95f),
                            radius = size.width * 0.65f
                        )
                    )
                }
                .padding(13.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Top Row: Icon container on left, Badge on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(iconBgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = iconTint,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = badgeBgColor
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = badgeTextColor,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(13.dp))

                // Middle: Title & Chevron
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF64748B),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Bottom: Subtitle
                Text(
                    text = subtitle,
                    fontSize = 11.5.sp,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ReportMenuCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBgColor: Color,
    iconTint: Color,
    statLabel: String,
    statValue: String,
    badgeText: String,
    badgeBgColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = AppTheme.colors.cardBg),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(AppTheme.colors.cardBorder.copy(alpha = 0.6f))),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (responsive.isSmallPhone) 12.dp else 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (responsive.isSmallPhone) 40.dp else 46.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(if (responsive.isSmallPhone) 22.dp else 24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = title,
                            fontSize = if (responsive.isSmallPhone) 14.5.sp else 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppTheme.colors.textPrimary,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = badgeBgColor
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = iconTint,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        fontSize = 11.5.sp,
                        color = AppTheme.colors.textMuted,
                        lineHeight = 15.sp
                    )
                }

                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open",
                    tint = AppTheme.colors.textMuted,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppTheme.colors.cardBg)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statLabel,
                    fontSize = 11.5.sp,
                    color = AppTheme.colors.textSecondary
                )
                Text(
                    text = statValue,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = AppTheme.colors.textPrimary
                )
            }
        }
    }
}

