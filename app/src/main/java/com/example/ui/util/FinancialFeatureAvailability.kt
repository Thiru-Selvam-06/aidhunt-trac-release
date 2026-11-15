package com.example.ui.util

import com.example.data.entity.ExpenseEntity

/** Existing records and their sync stay available while new loan transactions are paused. */
object FinancialFeatureAvailability {
    val newLoanTransactionsEnabled: Boolean = false

    fun comingSoon(isTamil: Boolean): String = if (isTamil) "விரைவில்" else "Coming Soon"

    fun canChangeExpense(expense: ExpenseEntity): Boolean = newLoanTransactionsEnabled ||
        !(expense.fundingSource.equals("EXTERNAL_LOAN", ignoreCase = true) ||
            expense.paidBy.trim().equals("Loan Funded", ignoreCase = true) ||
            expense.externalLoanId != 0L)
}
