package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.entity.ExpenseEntity
import com.example.data.expense.ExpenseAnalytics
import com.example.data.expense.ExpenseCategories

/** Same filtered records as the list. Refunds stay signed; pie slices are charges only. */
@Composable
fun ExpenseAnalyticsCard(expenses: List<ExpenseEntity>, isTamil: Boolean) {
    val summary = remember(expenses) { ExpenseAnalytics.from(expenses) }
    var expanded by remember { mutableStateOf(false) }
    var byFunding by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().testTag("expense-analytics"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TextButton(onClick = { expanded = !expanded }, modifier = Modifier.testTag("expense-analytics-toggle")) {
            Text(if (expanded) (if (isTamil) "பகுப்பாய்வை மறை" else "Hide expense breakdown")
                else (if (isTamil) "செலவு பகுப்பாய்வைக் காட்டு" else "Show expense breakdown"))
        }
        if (expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !byFunding, onClick = { byFunding = false },
                    label = { Text(if (isTamil) "வகை" else "Category") })
                FilterChip(selected = byFunding, onClick = { byFunding = true },
                    label = { Text(if (isTamil) "பணம் செலுத்திய மூலம்" else "Funding source") })
            }
            Text((if (isTamil) "நிகரச் செலவு: " else "Net expense: ") + formatInr(summary.net))
            Text((if (isTamil) "செலுத்தியது: " else "Charges: ") + formatInr(summary.charged) +
                (if (isTamil) " · திரும்பப் பெற்றது: " else " · Refunds: ") + formatInr(summary.refunded))
            if (summary.pendingCount > 0) Text(if (isTamil) "ஒத்திசைவு நிலுவையில் உள்ள ${summary.pendingCount} செலவுகள் அடங்கும்"
                else "Includes ${summary.pendingCount} expenses pending sync")
            if (summary.invalidCount > 0) Text(if (isTamil) "${summary.invalidCount} தவறான தொகைகளுக்கு மதிப்பாய்வு தேவை"
                else "${summary.invalidCount} invalid amounts need review", color = MaterialTheme.colorScheme.error)
            val rows = if (byFunding) summary.funding else summary.categories
            fun label(key: String, name: String): String = if (byFunding) when (key) {
                "BUSINESS" -> if (isTamil) "வணிகம் செலுத்தியது" else "Business Paid"
                "PERSONAL" -> if (isTamil) "தனிப்பட்ட பணம் / முதலீடு" else "Personally Paid / Investment"
                "EXTERNAL_LOAN" -> if (isTamil) "கடன் நிதி (நேரடி)" else "Loan Funded (Direct)"
                else -> if (isTamil) "மூலம் தெரியவில்லை" else "Funding unavailable"
            } else if (key == "unknown-category") (if (isTamil) "வகை தெரியவில்லை" else "Category unavailable")
                else ExpenseCategories.label(name, isTamil)
            val slices = rows.mapIndexed { index, row -> PieChartSlice(label(row.key, row.name), row.charged,
                ChartPalette[index % ChartPalette.size],
                (if (isTamil) "நிகரம் " else "Net ") + formatInr(row.net)) }
            PartnerWithdrawalPieChart(slices, summary.charged, isTamil = isTamil,
                title = if (isTamil) "திருப்பித் தரப்பட்ட தொகைக்கு முன் செலவுப் பகிர்வு" else "Expense distribution before refunds",
                emptyText = if (isTamil) "இந்த வடிகட்டலில் நேர்மறைச் செலவுகள் இல்லை" else "No positive charges in this filter")
            rows.filter { it.refunded > 0 }.forEach { row ->
                Text(label(row.key, row.name) + (if (isTamil) " · திரும்பப் பெற்றது " else " · Refund ") +
                    formatInr(row.refunded) + (if (isTamil) " · நிகரம் " else " · Net ") + formatInr(row.net))
            }
        }
    }
}
