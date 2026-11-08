package com.example.data.expense

import java.math.BigDecimal

/** Editing notes/category must never truncate paise or flip a historical refund's sign. */
object ExpenseAmountInput {
    fun format(value: Double): String {
        require(value.isFinite()) { "Invalid expense amount" }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    }
    fun parse(text: String): Double? = text.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it != 0.0 }
}
