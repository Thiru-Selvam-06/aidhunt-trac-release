package com.example.ui.screens.report

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.*
import com.example.data.firebase.WorkspaceMember
import com.example.pdf.BalanceSheetRow
import com.example.pdf.PdfGeneratorHelper
import com.example.ui.components.PdfOptionsDialog
import com.example.ui.components.formatDate
import com.example.ui.components.formatInr
import com.example.ui.theme.*
import com.example.ui.util.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class BalanceSheetTabMode(val labelEn: String, val labelTa: String) {
    DAILY("Daily", "தினசரி"),
    MONTHLY("Monthly", "மாதாந்திர"),
    YEARLY("Yearly", "வருடாந்திர")
}

data class BreakdownPeriodGroup(
    val key: String,
    val title: String,
    val timestamp: Long,
    val sales: Double,
    val expenses: Double,
    val netMargin: Double,
    val jobs: List<JobEntryEntity>,
    val expensesList: List<ExpenseEntity>,
    val receipts: List<PaymentEntity> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BalanceSheetTab(
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
    onUpdateFinancialScope: ((FinancialScope) -> Unit)? = null
) {
    val isTamil = settings.language.equals("TA", ignoreCase = true)
    val context = LocalContext.current
    val responsive = com.example.ui.theme.rememberResponsiveDimensions()

    var selectedTabMode by remember { mutableStateOf(BalanceSheetTabMode.DAILY) }
    var showPdfDialog by remember { mutableStateOf(false) }

    // Track expanded state for breakdown cards by their unique key
    val expandedCards = remember { mutableStateMapOf<String, Boolean>() }

    // Customer receipts are dated by their collection transaction, not by job work date.
    val periodGroups = remember(payments, expenses, selectedTabMode, settings.workspaceId, isTamil) {
        ReceiptPeriodReport.periods(settings.workspaceId, payments, expenses, selectedTabMode, isTamil).map { period ->
            BreakdownPeriodGroup(period.key, period.title, period.timestamp, period.received, period.spent,
                period.received - period.spent, emptyList(), period.expenses, period.payments)
        }
    }
    val unknownReceived = ReceiptPeriodReport.undatedReceived(settings.workspaceId, payments, jobs)
    val totalSalesReceived = FinancialCalculationEngine.roundMoney(periodGroups.sumOf { it.sales } + unknownReceived)
    val totalExpenseAmount = FinancialCalculationEngine.roundMoney(expenses.filter { it.workspaceId == settings.workspaceId }.sumOf { it.amount })
    val unknownExpenses = FinancialCalculationEngine.roundMoney(totalExpenseAmount - periodGroups.sumOf { it.expenses })
    val netProfitMargin = totalSalesReceived - totalExpenseAmount

    // Auto-expand first item if none expanded yet
    LaunchedEffect(selectedTabMode, periodGroups) {
        if (periodGroups.isNotEmpty() && expandedCards.isEmpty()) {
            expandedCards[periodGroups.first().key] = true
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF6F8F6))
            .testTag("screen_balance_sheet"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Top Controls Row: [Daily] [Monthly] [Yearly] on Left + [Export PDF] on Right
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Segmented Pills
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    BalanceSheetTabMode.values().forEach { mode ->
                        val isSelected = selectedTabMode == mode
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isSelected) Color(0xFF0F5132) else Color(0xFFF1F5F9),
                            modifier = Modifier
                                .clickable {
                                    selectedTabMode = mode
                                    expandedCards.clear()
                                }
                                .testTag("tab_mode_${mode.name}")
                        ) {
                            Text(
                                text = if (isTamil) mode.labelTa else mode.labelEn,
                                fontSize = 12.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFF0F5132),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Export PDF Button
                OutlinedButton(
                    onClick = { showPdfDialog = true },
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("btn_export_balance_pdf")
                ) {
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "PDF",
                        tint = Color(0xFFDC2626),
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (isTamil) "PDF ஏற்றுமதி" else "Export PDF",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0F172A)
                    )
                }
            }
        }

        // 2. Balance Summary Card (Exact visual structure from reference screenshot)
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("balance_summary_card")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isTamil) "இருப்புநிலைச் சுருக்கம்" else "Balance Summary",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F5132)
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = if (isTamil) "பெறப்பட்ட தொகை" else "Amount Received",
                                fontSize = 12.5.sp,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = formatInr(totalSalesReceived, settings.currency),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F9D58)
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = if (isTamil) "மொத்த செலவுகள்" else "Total Expenses",
                                fontSize = 12.5.sp,
                                color = Color(0xFF64748B)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = formatInr(totalExpenseAmount, settings.currency),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isTamil) "நிகர இருப்பு:" else "Net Balance:",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0F172A)
                        )
                        Text(
                            text = formatInr(netProfitMargin, settings.currency),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (netProfitMargin >= 0) Color(0xFF0F9D58) else Color(0xFFDC2626)
                        )
                    }
                }
            }
        }

        if (unknownReceived != 0.0 || unknownExpenses != 0.0) item {
            Text((if (isTamil) "தேதி தெரியாத பழைய வசூல் (மொத்தத்தில் மட்டும்): " else "Legacy receipts — date unknown (overall only): ") + formatInr(unknownReceived, settings.currency) + (if (isTamil) " · தேதி தெரியாத செலவுகள்: " else " · Undated expenses: ") + formatInr(unknownExpenses, settings.currency), fontSize = 12.sp)
        }
        // 3. Section Title ("Daily Breakdown" / "Monthly Breakdown" / "Yearly Breakdown")
        item {
            Text(
                text = when (selectedTabMode) {
                    BalanceSheetTabMode.DAILY -> if (isTamil) "தினசரி விவரங்கள்" else "Daily Breakdown"
                    BalanceSheetTabMode.MONTHLY -> if (isTamil) "மாதாந்திர விவரங்கள்" else "Monthly Breakdown"
                    BalanceSheetTabMode.YEARLY -> if (isTamil) "வருடாந்திர விவரங்கள்" else "Yearly Breakdown"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0F172A),
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        // 4. Breakdown Cards (Collapsible date cards with Sales, Expenses, Net Margin & Jobs/Collections)
        if (periodGroups.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isTamil) "பதிவுகள் எதுவும் இல்லை" else "No records found",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        } else {
            items(periodGroups, key = { it.key }) { group ->
                val isExpanded = expandedCards[group.key] ?: false
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandedCards[group.key] = !isExpanded }
                        .testTag("card_period_${group.key}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Card Header: Calendar Icon + Date Text + Chevron
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = Color(0xFF0F5132),
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = group.title,
                                    fontSize = 14.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A)
                                )
                            }

                            Icon(
                                imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Card Summary Metrics: Sales, Expenses, Net Margin
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(if (isTamil) "பெறப்பட்ட தொகை" else "Amount Received", fontSize = 11.5.sp, color = Color(0xFF64748B))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(formatInr(group.sales, settings.currency), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F9D58))
                            }
                            Column {
                                Text(if (isTamil) "செலவு" else "Expenses", fontSize = 11.5.sp, color = Color(0xFF64748B))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(formatInr(group.expenses, settings.currency), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFDC2626))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(if (isTamil) "நிகர இருப்பு" else "Net Balance", fontSize = 11.5.sp, color = Color(0xFF64748B))
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    formatInr(group.netMargin, settings.currency),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (group.netMargin >= 0) Color(0xFF0F9D58) else Color(0xFFDC2626)
                                )
                            }
                        }

                        // Expanded View: Jobs & Collections List
                        if (isExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Divider(color = Color(0xFFF1F5F9), thickness = 1.dp)
                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = if (isTamil) "வசூல்கள் & செலவுகள்:" else "Collections & Expenses:",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF0F5132)
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            if (group.receipts.isEmpty() && group.expensesList.isEmpty()) {
                                Text(
                                    text = if (isTamil) "இந்த காலகட்டத்தில் பதிவுகள் எதுவும் இல்லை" else "No collections or expenses recorded",
                                    fontSize = 12.sp,
                                    color = Color(0xFF94A3B8),
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    group.receipts.forEach { receipt ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val implement = com.example.ui.util.JobAuditPresentation.date(receipt.collectedAt)
                                            Text(
                                                text = "• ${receipt.customerName} ($implement)",
                                                fontSize = 12.5.sp,
                                                color = Color(0xFF334155),
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Text(
                                                text = "+${formatInr(receipt.amount, settings.currency)}",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF0F9D58)
                                            )
                                        }
                                    }

                                    group.expensesList.forEach { exp ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val desc = exp.description.ifBlank { exp.expenseType }
                                            Text(
                                                text = "• $desc (${exp.expenseType})",
                                                fontSize = 12.5.sp,
                                                color = Color(0xFF334155),
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            Text(
                                                text = "-${formatInr(exp.amount, settings.currency)}",
                                                fontSize = 12.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFFDC2626)
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
    }

    if (showPdfDialog) {
        PdfOptionsDialog(
            title = if (isTamil) "இருப்புநிலை அறிக்கை PDF" else "Balance Sheet Report PDF",
            subtitle = if (isTamil) "இருப்புநிலை அறிக்கையை பகிரவும் அல்லது பதிவிறக்கவும்" else "Share or download balance sheet report",
            isTamil = isTamil,
            onSharePdf = {
                val periodSummary = periodGroups.map { g ->
                    BalanceSheetRow(
                        periodLabel = g.title,
                        sales = g.sales,
                        expenses = g.expenses,
                        balance = g.netMargin
                    )
                }
                val completeSummary = ReceiptPeriodReport.withUndated(periodSummary, unknownReceived, unknownExpenses, isTamil)
                val file = PdfGeneratorHelper.generateBalanceSheetPdf(
                    context = context,
                    settings = settings,
                    totalSales = totalSalesReceived,
                    totalExpenses = totalExpenseAmount,
                    netBalance = netProfitMargin,
                    periodSummary = completeSummary
                )
                file?.let {
                    PdfGeneratorHelper.sharePdf(context, it, "Balance Sheet Report - ${settings.businessName}")
                }
                showPdfDialog = false
            },
            onDownloadPdf = {
                val periodSummary = periodGroups.map { g ->
                    BalanceSheetRow(
                        periodLabel = g.title,
                        sales = g.sales,
                        expenses = g.expenses,
                        balance = g.netMargin
                    )
                }
                val completeSummary = ReceiptPeriodReport.withUndated(periodSummary, unknownReceived, unknownExpenses, isTamil)
                val file = PdfGeneratorHelper.generateBalanceSheetPdf(
                    context = context,
                    settings = settings,
                    totalSales = totalSalesReceived,
                    totalExpenses = totalExpenseAmount,
                    netBalance = netProfitMargin,
                    periodSummary = completeSummary
                )
                file?.let {
                    val displayName = "Balance_Sheet_${selectedTabMode.name}_${System.currentTimeMillis()}"
                    PdfGeneratorHelper.downloadPdfToDownloads(context, it, displayName, isTamil)
                }
                showPdfDialog = false
            },
            onDismiss = { showPdfDialog = false }
        )
    }
}
