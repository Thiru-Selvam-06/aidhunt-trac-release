package com.example.data.expense

import com.example.data.entity.ExternalLoanEntity
import com.example.data.entity.ExternalLoanRepaymentEntity
import java.math.BigDecimal

data class LoanChartSliceSummary(
    val key: String,
    val label: String,
    val principalAmount: Double,
    val interestAmount: Double,
    val totalAmount: Double
)

data class LoanAnalytics(
    val totalRepaid: Double,
    val totalPrincipalRepaid: Double,
    val totalInterestRepaid: Double,
    val totalPrincipalOutstanding: Double,
    val totalInterestOutstanding: Double,
    val totalLoanFunding: Double,
    val repaymentsByLoan: List<LoanChartSliceSummary>,
    val outstandingByLoan: List<LoanChartSliceSummary>,
    val fundingByDestination: List<LoanChartSliceSummary>,
    val pendingCount: Int
) {
    companion object {
        fun from(
            loans: List<ExternalLoanEntity>,
            repayments: List<ExternalLoanRepaymentEntity>,
            period: ExpenseDateRange.Period = ExpenseDateRange.Period.ALL_TIME,
            nowMillis: Long = System.currentTimeMillis(),
            customStart: Long = 0L,
            customEnd: Long = 0L
        ): LoanAnalytics {
            val filteredRepayments = repayments.filter { r ->
                r.principalPaid.isFinite() && r.interestPaid.isFinite() &&
                    ExpenseDateRange.contains(r.repaidAt, period, nowMillis, customStart, customEnd)
            }
            val filteredLoans = loans.filter { l ->
                l.principalAmount.isFinite() && l.interestAmount.isFinite() &&
                    ExpenseDateRange.contains(l.loanDateMillis, period, nowMillis, customStart, customEnd)
            }

            val loansById = loans.associateBy { it.id }

            val repaymentsByLoan = filteredRepayments
                .filter { it.totalPaid > 0.0 }
                .groupBy { it.loanId }
                .map { (loanId, rows) ->
                    val loan = loansById[loanId]
                    val lenderLabel = loan?.lenderName?.ifBlank { "Loan #$loanId" } ?: "Loan #$loanId"
                    val principal = rows.fold(BigDecimal.ZERO) { acc, r -> acc + BigDecimal.valueOf(r.principalPaid) }.toDouble()
                    val interest = rows.fold(BigDecimal.ZERO) { acc, r -> acc + BigDecimal.valueOf(r.interestPaid) }.toDouble()
                    val total = BigDecimal.valueOf(principal).add(BigDecimal.valueOf(interest)).toDouble()
                    LoanChartSliceSummary(
                        key = "loan_$loanId",
                        label = lenderLabel,
                        principalAmount = principal,
                        interestAmount = interest,
                        totalAmount = total
                    )
                }.sortedByDescending { it.totalAmount }

            val outstandingByLoan = filteredLoans
                .filter { it.status != "SETTLED" && it.outstandingPrincipal > 0.0 }
                .groupBy { it.id }
                .map { (loanId, rows) ->
                    val loan = rows.first()
                    LoanChartSliceSummary(
                        key = "out_$loanId",
                        label = loan.lenderName.ifBlank { "Loan #$loanId" },
                        principalAmount = loan.outstandingPrincipal,
                        interestAmount = loan.outstandingInterest,
                        totalAmount = loan.outstandingPrincipal
                    )
                }.sortedByDescending { it.totalAmount }

            val fundingByDestination = filteredLoans
                .filter { it.principalAmount > 0.0 }
                .groupBy { it.destination.ifBlank { "BUSINESS_CASH" } }
                .map { (dest, rows) ->
                    val principal = rows.fold(BigDecimal.ZERO) { acc, l -> acc + BigDecimal.valueOf(l.principalAmount) }.toDouble()
                    val interest = rows.fold(BigDecimal.ZERO) { acc, l -> acc + BigDecimal.valueOf(l.interestAmount) }.toDouble()
                    LoanChartSliceSummary(
                        key = dest,
                        label = dest,
                        principalAmount = principal,
                        interestAmount = interest,
                        totalAmount = principal
                    )
                }.sortedByDescending { it.totalAmount }

            val totalPrincipalRepaid = filteredRepayments.fold(BigDecimal.ZERO) { acc, r -> acc + BigDecimal.valueOf(r.principalPaid) }.toDouble()
            val totalInterestRepaid = filteredRepayments.fold(BigDecimal.ZERO) { acc, r -> acc + BigDecimal.valueOf(r.interestPaid) }.toDouble()
            val totalRepaid = BigDecimal.valueOf(totalPrincipalRepaid).add(BigDecimal.valueOf(totalInterestRepaid)).toDouble()
            val totalPrincipalOutstanding = filteredLoans.filter { it.status != "SETTLED" }
                .fold(BigDecimal.ZERO) { acc, l -> acc + BigDecimal.valueOf(l.outstandingPrincipal) }.toDouble()
            val totalInterestOutstanding = filteredLoans.filter { it.status != "SETTLED" }
                .fold(BigDecimal.ZERO) { acc, l -> acc + BigDecimal.valueOf(l.outstandingInterest) }.toDouble()
            val totalLoanFunding = filteredLoans.fold(BigDecimal.ZERO) { acc, l -> acc + BigDecimal.valueOf(l.principalAmount) }.toDouble()

            return LoanAnalytics(
                totalRepaid = totalRepaid,
                totalPrincipalRepaid = totalPrincipalRepaid,
                totalInterestRepaid = totalInterestRepaid,
                totalPrincipalOutstanding = totalPrincipalOutstanding,
                totalInterestOutstanding = totalInterestOutstanding,
                totalLoanFunding = totalLoanFunding,
                repaymentsByLoan = repaymentsByLoan,
                outstandingByLoan = outstandingByLoan,
                fundingByDestination = fundingByDestination,
                pendingCount = filteredLoans.count { !it.isSynced } + filteredRepayments.count { !it.isSynced }
            )
        }
    }
}
