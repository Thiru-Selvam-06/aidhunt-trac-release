package com.example.ui.screens.report

import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.PendingOperationEntity
import com.example.pdf.BalanceSheetRow
import com.example.ui.util.FinancialCalculationEngine
import java.text.SimpleDateFormat
import java.util.*

data class ReceiptPeriod(val key: String, val title: String, val timestamp: Long,
    val payments: List<PaymentEntity>, val expenses: List<ExpenseEntity>) {
    val received get() = FinancialCalculationEngine.roundMoney(payments.sumOf { it.amount })
    val spent get() = FinancialCalculationEngine.roundMoney(expenses.sumOf { it.amount })
}

internal data class BalanceSheetProjection(
    val periods: List<ReceiptPeriod>,
    val totalReceived: Double,
    val totalExpenses: Double,
    val datedReceived: Double,
    val embeddedUndatedReceived: Double,
    val explicitUndatedReceived: Double,
    val undatedExpenses: Double,
    val hasPendingChanges: Boolean,
    val needsReview: Boolean
) {
    val netBalance get() = FinancialCalculationEngine.roundMoney(totalReceived - totalExpenses)

    fun pdfRows(isTamil: Boolean): List<BalanceSheetRow> = ReceiptPeriodReport.withUndated(
        periods.map { BalanceSheetRow(it.title, it.received, it.spent, FinancialCalculationEngine.roundMoney(it.received - it.spent)) },
        FinancialCalculationEngine.roundMoney(embeddedUndatedReceived + explicitUndatedReceived),
        undatedExpenses,
        isTamil
    )

    fun syncNotices(isTamil: Boolean): List<String> = buildList {
        if (hasPendingChanges) add(if (isTamil) "ஒத்திசைவு நிலுவையில் உள்ள மாற்றங்கள் இந்த அறிக்கையில் உள்ளன."
            else "This report includes local changes awaiting sync.")
        if (needsReview) add(if (isTamil) "சில நிதி நடவடிக்கைகளுக்கு ஒத்திசைவு ஆய்வு தேவை."
            else "Some financial actions need review in SQLite & Cloud Sync.")
    }
}

/** Report-only projection. Reversals restate their original period; funding never becomes revenue. */
object ReceiptPeriodReport {
    fun withUndated(rows: List<BalanceSheetRow>, received: Double, expenses: Double, tamil: Boolean): List<BalanceSheetRow> =
        if (received == 0.0 && expenses == 0.0) rows else rows + BalanceSheetRow(
            if (tamil) "தேதி தெரியாதவை / மொத்தத்தில் மட்டும்" else "Date unknown / overall only", received, expenses,
            FinancialCalculationEngine.roundMoney(received - expenses))

    internal fun project(
        workspace: String,
        payments: List<PaymentEntity>,
        expenses: List<ExpenseEntity>,
        jobs: List<JobEntryEntity>,
        mode: BalanceSheetTabMode,
        tamil: Boolean = false,
        actions: List<PendingOperationEntity> = emptyList()
    ): BalanceSheetProjection {
        val receipts = paymentRecords(workspace, payments)
        val active = receipts.filter { it.status.equals("ACTIVE", true) }
        val costs = expenses.filter { workspace.isNotBlank() && it.workspaceId == workspace }
        val ledgerJobs = jobs.filter { workspace.isNotBlank() && it.workspaceId == workspace }
        val embedded = reconciledLegacyReceiptJobs(workspace, receipts, ledgerJobs).sumOf { it.amountReceived }
        val financialActions = actions.filter {
            workspace.isNotBlank() && it.workspaceId == workspace && it.entityType in setOf("JOB", "PAYMENT", "EXPENSE")
        }
        return BalanceSheetProjection(
            periods = periods(workspace, receipts, costs, mode, tamil),
            totalReceived = FinancialCalculationEngine.roundMoney(active.sumOf { it.amount } + embedded),
            totalExpenses = FinancialCalculationEngine.roundMoney(costs.sumOf { it.amount }),
            datedReceived = FinancialCalculationEngine.roundMoney(active.filter { it.collectedAt > 0 }.sumOf { it.amount }),
            embeddedUndatedReceived = FinancialCalculationEngine.roundMoney(embedded),
            explicitUndatedReceived = FinancialCalculationEngine.roundMoney(active.filter { it.collectedAt <= 0 }.sumOf { it.amount }),
            undatedExpenses = FinancialCalculationEngine.roundMoney(costs.filter { it.dateTimestamp <= 0 }.sumOf { it.amount }),
            hasPendingChanges = receipts.any { !it.isSynced } || costs.any { !it.isSynced } ||
                ledgerJobs.any { !it.isSynced } || financialActions.any { it.state == "PENDING" },
            needsReview = financialActions.any { it.state in setOf("BLOCKED", "REJECTED") }
        )
    }

    private fun paymentRecords(workspace: String, payments: List<PaymentEntity>): List<PaymentEntity> =
        payments.filter { workspace.isNotBlank() && it.workspaceId == workspace }.distinctBy { it.id }

    fun periods(workspace: String, payments: List<PaymentEntity>, expenses: List<ExpenseEntity>, mode: BalanceSheetTabMode, tamil: Boolean = false): List<ReceiptPeriod> {
        if (workspace.isBlank()) return emptyList()
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
        val receipts = paymentRecords(workspace, payments).filter { it.status.equals("ACTIVE", true) && it.collectedAt > 0 }
            .groupBy { bucket(it.collectedAt).first }
        val costs = expenses.filter { it.workspaceId == workspace && it.dateTimestamp > 0 }
            .groupBy { bucket(it.dateTimestamp).first }
        return (receipts.keys + costs.keys).sortedDescending().map { key ->
            ReceiptPeriod(key.toString(), bucket(key).second, key, receipts[key].orEmpty().sortedBy { it.collectedAt }, costs[key].orEmpty())
        }
    }
}
