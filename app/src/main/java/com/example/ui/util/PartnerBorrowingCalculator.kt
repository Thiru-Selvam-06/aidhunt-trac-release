package com.example.ui.util

import com.example.data.entity.BorrowingRequestEntity
import com.example.data.entity.SettlementObligationEntity

data class PartnerBorrowingSummary(
    val partnerUid: String,
    val baseEntitlement: Double,
    val unsettledPartnerLent: Double,
    val directSettledRecovery: Double,
    val withdrawableEntitlement: Double,
    val openBorrowingDebt: Double,
    val pendingRequestedAmount: Double
)

data class WorkspaceBorrowingOverview(
    val confirmedCash: Double,
    val reservedCash: Double,
    val unreservedCash: Double,
    val protectedEntitlementsTotal: Double,
    val availableForBusinessBorrowing: Double,
    val partnerSummaries: Map<String, PartnerBorrowingSummary>
)

object PartnerBorrowingCalculator {

    fun calculate(
        confirmedCash: Double,
        reservedCash: Double,
        statuses: List<PartnerShareInvestmentStatus>,
        obligations: List<SettlementObligationEntity>,
        borrowingRequests: List<BorrowingRequestEntity> = emptyList()
    ): WorkspaceBorrowingOverview {
        val cleanConfirmed = FinancialCalculationEngine.roundNonNegativeMoney(confirmedCash)
        val cleanReserved = FinancialCalculationEngine.roundNonNegativeMoney(reservedCash)
        val unreservedCash = FinancialCalculationEngine.roundNonNegativeMoney(cleanConfirmed - cleanReserved)

        val summaries = linkedMapOf<String, PartnerBorrowingSummary>()
        statuses.forEach { status ->
            val uid = status.partnerUid
            val unsettledLent = if (uid.isBlank()) 0.0 else FinancialCalculationEngine.roundNonNegativeMoney(
                obligations.filter {
                    it.creditorType == "PARTNER" && it.creditorUid == uid && it.status != "SETTLED"
                }.sumOf { it.remainingAmount }
            )
            val directSettled = if (uid.isBlank()) 0.0 else FinancialCalculationEngine.roundNonNegativeMoney(
                obligations.filter {
                    it.creditorType == "PARTNER" && it.creditorUid == uid
                }.sumOf { it.directSettledAmount }
            )
            val withdrawable = FinancialCalculationEngine.roundNonNegativeMoney(
                status.totalToGet - unsettledLent - directSettled
            )
            val openDebt = if (uid.isBlank()) 0.0 else FinancialCalculationEngine.roundNonNegativeMoney(
                obligations.filter { it.borrowerUid == uid && it.status != "SETTLED" }.sumOf { it.remainingAmount }
            )
            val pendingReq = if (uid.isBlank()) 0.0 else FinancialCalculationEngine.roundNonNegativeMoney(
                borrowingRequests.filter {
                    it.borrowerUid == uid && it.status in setOf("PENDING_APPROVAL", "APPROVED_RESERVED")
                }.sumOf { it.amount }
            )
            if (uid.isNotBlank()) {
                summaries[uid] = PartnerBorrowingSummary(
                    partnerUid = uid,
                    baseEntitlement = status.totalToGet,
                    unsettledPartnerLent = unsettledLent,
                    directSettledRecovery = directSettled,
                    withdrawableEntitlement = withdrawable,
                    openBorrowingDebt = openDebt,
                    pendingRequestedAmount = pendingReq
                )
            }
        }

        val protectedTotal = FinancialCalculationEngine.roundNonNegativeMoney(
            summaries.values.sumOf { it.withdrawableEntitlement }
        )
        val businessBorrowingLimit = FinancialCalculationEngine.roundNonNegativeMoney(
            unreservedCash - protectedTotal
        )

        return WorkspaceBorrowingOverview(
            confirmedCash = cleanConfirmed,
            reservedCash = cleanReserved,
            unreservedCash = unreservedCash,
            protectedEntitlementsTotal = protectedTotal,
            availableForBusinessBorrowing = businessBorrowingLimit,
            partnerSummaries = summaries
        )
    }

    fun openDebtForPartner(
        partnerUid: String,
        obligations: List<SettlementObligationEntity>
    ): Double {
        if (partnerUid.isBlank()) return 0.0
        return FinancialCalculationEngine.roundNonNegativeMoney(
            obligations.filter { it.borrowerUid == partnerUid && it.status != "SETTLED" }.sumOf { it.remainingAmount }
        )
    }
}
