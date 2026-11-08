package com.example.ui.screens.report

import com.example.data.entity.*
import com.example.ui.util.FinancialCalculationEngine
import java.text.SimpleDateFormat
import java.util.*

data class ReceiptPeriod(val key: String, val title: String, val timestamp: Long,
    val payments: List<PaymentEntity>, val expenses: List<ExpenseEntity>) {
    val received get() = FinancialCalculationEngine.roundMoney(payments.sumOf { it.amount })
    val spent get() = FinancialCalculationEngine.roundMoney(expenses.sumOf { it.amount })
}

/** Report-only projection. Reversals restate their original period; funding never becomes revenue. */
object ReceiptPeriodReport {
    fun withUndated(rows: List<com.example.pdf.BalanceSheetRow>, received: Double, expenses: Double, tamil: Boolean): List<com.example.pdf.BalanceSheetRow> =
        if (received == 0.0 && expenses == 0.0) rows else rows + com.example.pdf.BalanceSheetRow(
            if (tamil) "தேதி தெரியாதவை / மொத்தத்தில் மட்டும்" else "Date unknown / overall only", received, expenses,
            FinancialCalculationEngine.roundMoney(received - expenses))

    fun periods(workspace: String, payments: List<PaymentEntity>, expenses: List<ExpenseEntity>, mode: BalanceSheetTabMode, tamil: Boolean = false): List<ReceiptPeriod> {
        require(workspace.isNotBlank())
        val zone = TimeZone.getTimeZone("Asia/Kolkata")
        fun bucket(time: Long): Pair<Long, String> {
            val c = Calendar.getInstance(zone).apply { timeInMillis = time
                if (mode == BalanceSheetTabMode.YEARLY) set(Calendar.MONTH, 0)
                if (mode != BalanceSheetTabMode.DAILY) set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val pattern = when(mode) { BalanceSheetTabMode.DAILY -> "dd MMM yyyy (EEE)"; BalanceSheetTabMode.MONTHLY -> "MMMM yyyy"; BalanceSheetTabMode.YEARLY -> "yyyy" }
            return c.timeInMillis to SimpleDateFormat(pattern, if (tamil) Locale.forLanguageTag("ta-IN") else Locale.ENGLISH).apply { timeZone = zone }.format(c.time)
        }
        val receipts = payments.filter { it.workspaceId == workspace && it.status.equals("ACTIVE", true) && it.collectedAt > 0 }
            .groupBy { bucket(it.collectedAt).first }
        val costs = expenses.filter { it.workspaceId == workspace && (it.dateTimestamp > 0 || it.createdAt > 0) }
            .groupBy { bucket(if (it.dateTimestamp > 0) it.dateTimestamp else it.createdAt).first }
        return (receipts.keys + costs.keys).sortedDescending().map { key ->
            ReceiptPeriod(key.toString(), bucket(key).second, key, receipts[key].orEmpty().sortedBy { it.collectedAt }, costs[key].orEmpty())
        }
    }
    fun undatedReceived(workspace: String, payments: List<PaymentEntity>, jobs: List<JobEntryEntity>): Double =
        FinancialCalculationEngine.roundMoney(reconciledLegacyReceiptJobs(workspace, payments, jobs).sumOf { it.amountReceived } +
            payments.filter { it.workspaceId == workspace && it.status.equals("ACTIVE", true) && it.collectedAt <= 0 }.sumOf { it.amount })
}
