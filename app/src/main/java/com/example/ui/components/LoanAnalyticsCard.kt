package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.entity.ExternalLoanEntity
import com.example.data.entity.ExternalLoanRepaymentEntity
import com.example.data.expense.ExpenseDateRange
import com.example.data.expense.LoanAnalytics

enum class LoanAnalyticsChartMode {
    REPAYMENTS,
    OUTSTANDING_PRINCIPAL,
    LOAN_FUNDING
}

/**
 * Compact analytics card for external loans strictly separating:
 * - Loan Repayments Made
 * - Loan Principal Outstanding
 * - Loan Funding
 * Supports Daily / Monthly / Yearly / All filters and EN/TA labels.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoanAnalyticsCard(
    loans: List<ExternalLoanEntity>,
    repayments: List<ExternalLoanRepaymentEntity>,
    isTamil: Boolean,
    currency: String = "₹"
) {
    var expanded by remember { mutableStateOf(false) }
    var chartMode by remember { mutableStateOf(LoanAnalyticsChartMode.REPAYMENTS) }
    var period by remember { mutableStateOf(ExpenseDateRange.Period.ALL_TIME) }

    val summary = remember(loans, repayments, period) {
        LoanAnalytics.from(loans, repayments, period)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("loan_analytics_section"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(
            onClick = { expanded = !expanded },
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("loan_analytics_toggle")
        ) {
            Text(
                text = if (expanded) {
                    if (isTamil) "கடன் பகுப்பாய்வை மறை" else "Hide loan analytics"
                } else {
                    if (isTamil) "கடன் திருப்பிச் செலுத்தல் பகுப்பாய்வு" else "Show loan repayment analytics"
                },
                fontWeight = FontWeight.Bold
            )
        }

        if (expanded) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    ExpenseDateRange.Period.ALL_TIME to (if (isTamil) "அனைத்தும்" else "All"),
                    ExpenseDateRange.Period.TODAY to (if (isTamil) "தினசரி" else "Daily"),
                    ExpenseDateRange.Period.THIS_MONTH to (if (isTamil) "மாதாந்திர" else "Monthly"),
                    ExpenseDateRange.Period.THIS_YEAR to (if (isTamil) "வருடாந்திர" else "Yearly")
                ).forEach { (p, label) ->
                    FilterChip(
                        selected = period == p,
                        onClick = { period = p },
                        label = { Text(label, fontSize = 12.sp) },
                        modifier = Modifier.testTag("loan_analytics_period_${p.name}")
                    )
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = chartMode == LoanAnalyticsChartMode.REPAYMENTS,
                    onClick = { chartMode = LoanAnalyticsChartMode.REPAYMENTS },
                    label = {
                        Text(
                            if (isTamil) "கடன் திருப்பிச் செலுத்தல்கள்" else "Loan Repayments Made",
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier.testTag("loan_analytics_mode_repayments")
                )
                FilterChip(
                    selected = chartMode == LoanAnalyticsChartMode.OUTSTANDING_PRINCIPAL,
                    onClick = { chartMode = LoanAnalyticsChartMode.OUTSTANDING_PRINCIPAL },
                    label = {
                        Text(
                            if (isTamil) "நிலுவையிலுள்ள கடன் அசல்" else "Loan Principal Outstanding",
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier.testTag("loan_analytics_mode_outstanding")
                )
                FilterChip(
                    selected = chartMode == LoanAnalyticsChartMode.LOAN_FUNDING,
                    onClick = { chartMode = LoanAnalyticsChartMode.LOAN_FUNDING },
                    label = {
                        Text(
                            if (isTamil) "கடன் நிதி மூலம்" else "Loan Funding",
                            fontSize = 12.sp
                        )
                    },
                    modifier = Modifier.testTag("loan_analytics_mode_funding")
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = (if (isTamil) "திருப்பிச் செலுத்தியது: " else "Repaid: ") +
                        formatInr(summary.totalRepaid, currency),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = (if (isTamil) "அசல் பாக்கி: " else "Principal Due: ") +
                        formatInr(summary.totalPrincipalOutstanding, currency),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            if (summary.pendingCount > 0) {
                Text(
                    text = if (isTamil) "ஒத்திசைவு நிலுவையில் உள்ள ${summary.pendingCount} கடன் பதிவுகள் அடங்கும்"
                    else "Includes ${summary.pendingCount} loan records pending sync",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            fun destLabel(key: String): String = when (key) {
                "BUSINESS_CASH" -> if (isTamil) "வணிக ரொக்கம்" else "Business Cash"
                "BUSINESS_BANK" -> if (isTamil) "வணிக வங்கி" else "Business Bank"
                "DIRECT_TO_SUPPLIER" -> if (isTamil) "நேரடி சப்ளையர் நிதி" else "Direct to Supplier"
                else -> key
            }

            val (chartTitle, emptyText, totalForMode, slices) = when (chartMode) {
                LoanAnalyticsChartMode.REPAYMENTS -> {
                    val items = summary.repaymentsByLoan.mapIndexed { idx, row ->
                        val sub = (if (isTamil) "அசல் " else "Prin ") + formatInr(row.principalAmount, currency) +
                            (if (isTamil) " · வட்டி " else " · Int ") + formatInr(row.interestAmount, currency)
                        PieChartSlice(row.label, row.totalAmount, ChartPalette[idx % ChartPalette.size], sub)
                    }
                    Quad(
                        if (isTamil) "கடன் திருப்பிச் செலுத்தல்கள்" else "Loan Repayments Made",
                        if (isTamil) "இந்த காலகட்டத்தில் கடன் திருப்பிச் செலுத்தல்கள் இல்லை" else "No loan repayments recorded in this period",
                        summary.totalRepaid,
                        items
                    )
                }
                LoanAnalyticsChartMode.OUTSTANDING_PRINCIPAL -> {
                    val items = summary.outstandingByLoan.mapIndexed { idx, row ->
                        val sub = (if (isTamil) "வட்டி பாக்கி " else "Interest Due ") + formatInr(row.interestAmount, currency)
                        PieChartSlice(row.label, row.principalAmount, ChartPalette[idx % ChartPalette.size], sub)
                    }
                    Quad(
                        if (isTamil) "நிலுவையிலுள்ள கடன் அசல்" else "Loan Principal Outstanding",
                        if (isTamil) "நிலுவையிலுள்ள கடன் அசல் எதுவும் இல்லை" else "No outstanding loan principal in this period",
                        summary.totalPrincipalOutstanding,
                        items
                    )
                }
                LoanAnalyticsChartMode.LOAN_FUNDING -> {
                    val items = summary.fundingByDestination.mapIndexed { idx, row ->
                        val sub = (if (isTamil) "வட்டி " else "Interest ") + formatInr(row.interestAmount, currency)
                        PieChartSlice(destLabel(row.key), row.principalAmount, ChartPalette[idx % ChartPalette.size], sub)
                    }
                    Quad(
                        if (isTamil) "கடன் நிதி மூலம்" else "Loan Funding",
                        if (isTamil) "இந்த காலகட்டத்தில் கடன் நிதி பதிவுகள் இல்லை" else "No external loan funding recorded in this period",
                        summary.totalLoanFunding,
                        items
                    )
                }
            }

            PartnerWithdrawalPieChart(
                slices = slices,
                totalAmount = totalForMode,
                isTamil = isTamil,
                title = chartTitle,
                emptyText = emptyText,
                modifier = Modifier.testTag("loan_analytics_chart")
            )
        }
    }
}

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
