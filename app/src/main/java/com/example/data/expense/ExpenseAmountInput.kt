package com.example.data.expense

import java.math.BigDecimal

/** Editing notes/category must never truncate paise or flip a historical refund's sign. */
object ExpenseAmountInput {
    fun format(value: Double): String {
        require(value.isFinite()) { "Invalid expense amount" }
        return BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
    }
    fun parse(text: String, allowRefund: Boolean = true): Double? = text.trim().toDoubleOrNull()
        ?.takeIf { it.isFinite() && it != 0.0 && (allowRefund || it > 0.0) }

    /** Existing refund values may be retained, never newly created or enlarged by this editor. */
    fun parseForRecord(text: String, originalAmount: Double? = null): Double? = parse(text)
        ?.takeIf { it > 0.0 || (originalAmount != null && originalAmount.isFinite() && originalAmount < 0.0 && it == originalAmount) }
}
