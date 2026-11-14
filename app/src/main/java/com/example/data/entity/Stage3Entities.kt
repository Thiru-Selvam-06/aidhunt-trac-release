package com.example.data.entity

import androidx.room.Entity

@Entity(tableName = "external_loans", primaryKeys = ["workspaceId", "id"])
data class ExternalLoanEntity(
    val id: Long = 0L,
    val workspaceId: String = "",
    val lenderName: String = "",
    val principalAmount: Double = 0.0,
    val interestAmount: Double = 0.0,
    val totalRepaidPrincipal: Double = 0.0,
    val totalRepaidInterest: Double = 0.0,
    val destination: String = "BUSINESS_CASH", // BUSINESS_CASH, BUSINESS_BANK, DIRECT_TO_SUPPLIER
    val linkedExpenseId: Long = 0L,
    val status: String = "ACTIVE", // ACTIVE, SETTLED
    val note: String = "",
    val loanDateMillis: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdByUid: String = "",
    val createdByRole: String = "",
    val isSynced: Boolean = true
) {
    val outstandingPrincipal: Double
        get() = maxOf(0.0, principalAmount - totalRepaidPrincipal)
    val remainingPrincipal: Double
        get() = outstandingPrincipal
    val outstandingInterest: Double
        get() = maxOf(0.0, interestAmount - totalRepaidInterest)
    val remainingInterest: Double
        get() = outstandingInterest
    val totalOutstanding: Double
        get() = outstandingPrincipal + outstandingInterest
}

@Entity(tableName = "external_loan_repayments", primaryKeys = ["workspaceId", "id"])
data class ExternalLoanRepaymentEntity(
    val id: Long = 0L,
    val workspaceId: String = "",
    val loanId: Long = 0L,
    val principalPaid: Double = 0.0,
    val interestPaid: Double = 0.0,
    val source: String = "BUSINESS_CASH", // BUSINESS_CASH, BUSINESS_BANK
    val note: String = "",
    val repaidAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val createdByUid: String = "",
    val createdByRole: String = "",
    val isSynced: Boolean = true
) {
    val totalPaid: Double
        get() = principalPaid + interestPaid
}

@Entity(tableName = "borrowing_requests", primaryKeys = ["workspaceId", "id"])
data class BorrowingRequestEntity(
    val id: Long = 0L,
    val workspaceId: String = "",
    val borrowerUid: String = "",
    val borrowerName: String = "",
    val lendingPartnerUid: String = "",
    val lendingPartnerName: String = "",
    val sourceType: String = "BUSINESS_ONLY", // BUSINESS_ONLY, NAMED_PARTNER
    val amount: Double = 0.0,
    val note: String = "",
    val status: String = "PENDING_APPROVAL", // PENDING_APPROVAL, APPROVED_RESERVED, EXECUTED, REJECTED, CANCELLED
    val reservedAmount: Double = 0.0,
    val withdrawalId: Long = 0L,
    val obligationId: Long = 0L,
    val requestedByUid: String = "",
    val requestedByRole: String = "",
    val approvedByUid: String = "",
    val approvedAt: Long = 0L,
    val rejectedByUid: String = "",
    val rejectedAt: Long = 0L,
    val rejectionReason: String = "",
    val executedAt: Long = 0L,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isSynced: Boolean = true
)

@Entity(tableName = "settlement_obligations", primaryKeys = ["workspaceId", "id"])
data class SettlementObligationEntity(
    val id: Long = 0L,
    val workspaceId: String = "",
    val borrowerUid: String = "",
    val borrowerName: String = "",
    val creditorType: String = "BUSINESS", // BUSINESS, PARTNER
    val creditorUid: String = "",
    val creditorName: String = "",
    val sourceRequestId: Long = 0L,
    val principalAmount: Double = 0.0,
    val repaidAmount: Double = 0.0,
    val directSettledAmount: Double = 0.0,
    val status: String = "OPEN", // OPEN, SETTLED
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val createdByUid: String = "",
    val isSynced: Boolean = true
) {
    val remainingAmount: Double
        get() = maxOf(0.0, principalAmount - repaidAmount)
}

@Entity(tableName = "settlement_repayments", primaryKeys = ["workspaceId", "id"])
data class SettlementRepaymentEntity(
    val id: Long = 0L,
    val workspaceId: String = "",
    val obligationId: Long = 0L,
    val payerUid: String = "",
    val payerName: String = "",
    val creditorType: String = "BUSINESS", // BUSINESS, PARTNER
    val creditorUid: String = "",
    val creditorName: String = "",
    val amount: Double = 0.0,
    val mode: String = "INTO_BUSINESS", // INTO_BUSINESS, INTO_BUSINESS_FOR_PARTNER, DIRECT_TO_PARTNER
    val status: String = "PENDING_CONFIRMATION", // PENDING_CONFIRMATION, CONFIRMED, REJECTED
    val note: String = "",
    val paidAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val createdByUid: String = "",
    val confirmedByUid: String = "",
    val confirmedAt: Long = 0L,
    val rejectedByUid: String = "",
    val rejectedAt: Long = 0L,
    val rejectionReason: String = "",
    val isSynced: Boolean = true
)
