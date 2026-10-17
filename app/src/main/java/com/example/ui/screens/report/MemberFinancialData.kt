package com.example.ui.screens.report

data class MemberFinancialData(
    val uid: String,
    val name: String,
    val role: String,
    val total: Double,
    val collected: Double,
    val due: Double,
    val expenses: Double
) {
    val compositionTotal = collected + due + expenses

    // Consistent Reconciled Percentages (sum to exactly 100%)
    val collectedPercent: Int
    val duePercent: Int
    val expensesPercent: Int

    init {
        if (compositionTotal > 0.0) {
            // Round first two, reconcile the third to ensure 100% total
            val p1 = kotlin.math.round((collected / compositionTotal) * 100.0).toInt()
            val p2 = kotlin.math.round((due / compositionTotal) * 100.0).toInt()
            val p3 = (100 - p1 - p2).coerceAtLeast(0)

            collectedPercent = p1
            duePercent = p2
            expensesPercent = p3
        } else {
            collectedPercent = 0
            duePercent = 0
            expensesPercent = 0
        }
    }

    val colRatio = if (compositionTotal > 0) collected / compositionTotal else 0.0
    val dueRatio = if (compositionTotal > 0) due / compositionTotal else 0.0
    val expRatio = if (compositionTotal > 0) expenses / compositionTotal else 0.0
}
