package com.example.data.expense

import com.example.data.entity.ExpenseEntity
import java.math.BigDecimal

data class ExpenseDistribution(val key: String, val name: String, val charged: Double, val refunded: Double) {
    val net: Double get() = BigDecimal.valueOf(charged).subtract(BigDecimal.valueOf(refunded)).toDouble()
}

data class ExpenseAnalytics(
    val categories: List<ExpenseDistribution>, val funding: List<ExpenseDistribution>,
    val invalidCount: Int, val pendingCount: Int
) {
    val charged: Double get() = categories.fold(BigDecimal.ZERO) { n, row -> n + BigDecimal.valueOf(row.charged) }.toDouble()
    val refunded: Double get() = categories.fold(BigDecimal.ZERO) { n, row -> n + BigDecimal.valueOf(row.refunded) }.toDouble()
    val net: Double get() = BigDecimal.valueOf(charged).subtract(BigDecimal.valueOf(refunded)).toDouble()

    companion object {
        fun from(expenses: List<ExpenseEntity>): ExpenseAnalytics {
            val valid = expenses.filter { it.amount.isFinite() }
            fun group(key: (ExpenseEntity) -> String, name: (ExpenseEntity) -> String) =
                valid.groupBy(key).map { (key, rows) ->
                    val charged = rows.filter { it.amount > 0 }.fold(BigDecimal.ZERO) { n, e -> n + BigDecimal.valueOf(e.amount) }
                    val refunded = rows.filter { it.amount < 0 }.fold(BigDecimal.ZERO) { n, e -> n - BigDecimal.valueOf(e.amount) }
                    ExpenseDistribution(key, name(rows.first()), charged.toDouble(), refunded.toDouble())
                }.sortedWith(compareByDescending<ExpenseDistribution> { it.charged }.thenBy { it.key })
            return ExpenseAnalytics(
                group({ ExpenseCategories.key(it.expenseType).ifBlank { "unknown-category" } }, { it.expenseType.ifBlank { "Unknown category" } }),
                group({ fundingKey(it) }, { fundingKey(it) }),
                expenses.size - valid.size, valid.count { !it.isSynced }
            )
        }

        fun fundingKey(expense: ExpenseEntity): String = when {
            expense.paidBy.equals("Business Paid", true) -> "BUSINESS"
            expense.paidBy.equals("I Paid", true) -> "PERSONAL"
            else -> "UNKNOWN" // Never silently classify unsupported or missing historical funding.
        }
    }
}
