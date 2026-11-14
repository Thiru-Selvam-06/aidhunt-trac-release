package com.example.data.entity

import androidx.room.Entity

/**
 * Structured cloud-confirmed notification record.
 * Never store pre-rendered prose from the sender's language here:
 * NotificationPresenter renders localized EN/TA copy in the recipient's active language.
 */
@Entity(
    tableName = "app_notifications",
    primaryKeys = ["workspaceId", "id"]
)
data class AppNotificationEntity(
    val id: String = "",
    val workspaceId: String = "",
    val operationId: String = "",
    val recipientUid: String = "", // Specific UID for targeted notifications; "" for OWNER_ONLY audience
    val targetAudience: String = AUDIENCE_OWNER_ONLY, // OWNER_ONLY, PARTNER_TARGETED
    val actorUid: String = "",
    val actorName: String = "",
    val eventType: String = "",
    val entityType: String = "",
    val entityId: Long = 0L,
    val amount: Double = 0.0,
    val counterpartyName: String = "",
    val metadataJson: String = "{}",
    val createdAt: Long = 0L,
    val isRead: Boolean = false,
    val readAt: Long = 0L
) {
    fun toFirestoreMap(): Map<String, Any?> = mapOf(
        "id" to id,
        "workspaceId" to workspaceId,
        "operationId" to operationId,
        "recipientUid" to recipientUid,
        "targetAudience" to targetAudience,
        "actorUid" to actorUid,
        "actorName" to actorName,
        "eventType" to eventType,
        "entityType" to entityType,
        "entityId" to entityId,
        "amount" to amount,
        "counterpartyName" to counterpartyName,
        "metadataJson" to metadataJson,
        "createdAt" to createdAt,
        "isRead" to isRead,
        "readAt" to readAt
    )

    companion object {
        const val AUDIENCE_OWNER_ONLY = "OWNER_ONLY"
        const val AUDIENCE_PARTNER_TARGETED = "PARTNER_TARGETED"

        const val EVENT_JOB_CREATED = "JOB_CREATED"
        const val EVENT_JOB_EDITED = "JOB_EDITED"
        const val EVENT_JOB_DELETED = "JOB_DELETED"
        const val EVENT_PAYMENT_RECEIVED = "PAYMENT_RECEIVED"
        const val EVENT_PAYMENT_REVERSED = "PAYMENT_REVERSED"
        const val EVENT_EXPENSE_ADDED = "EXPENSE_ADDED"
        const val EVENT_EXPENSE_EDITED = "EXPENSE_EDITED"
        const val EVENT_EXPENSE_DELETED = "EXPENSE_DELETED"
        const val EVENT_WITHDRAWAL_RECORDED = "WITHDRAWAL_RECORDED"
        const val EVENT_EXTERNAL_LOAN_CREATED = "EXTERNAL_LOAN_CREATED"
        const val EVENT_EXTERNAL_LOAN_REPAID = "EXTERNAL_LOAN_REPAID"
        const val EVENT_BORROWING_REQUESTED = "BORROWING_REQUESTED"
        const val EVENT_BORROWING_APPROVED = "BORROWING_APPROVED"
        const val EVENT_BORROWING_REJECTED = "BORROWING_REJECTED"
        const val EVENT_BORROWING_CANCELLED = "BORROWING_CANCELLED"
        const val EVENT_BORROWING_EXECUTED = "BORROWING_EXECUTED"
        const val EVENT_SETTLEMENT_REPAYMENT_RECORDED = "SETTLEMENT_REPAYMENT_RECORDED"
        const val EVENT_SETTLEMENT_REPAYMENT_CONFIRMED = "SETTLEMENT_REPAYMENT_CONFIRMED"
        const val EVENT_SETTLEMENT_REPAYMENT_REJECTED = "SETTLEMENT_REPAYMENT_REJECTED"
    }
}
