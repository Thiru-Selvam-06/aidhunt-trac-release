package com.example.data.sync

import com.example.data.entity.AppNotificationEntity
import com.example.data.entity.PendingOperationEntity
import com.example.data.firebase.WorkspaceMember
import org.json.JSONObject

/**
 * Generates deterministic, idempotent, structured notification payloads strictly from
 * cloud-confirmed operations inside WorkspaceRepository.acknowledge(op).
 *
 * Guarantees:
 * - Rejected or offline unconfirmed operations never pass through acknowledge(op), so no false notification is created.
 * - Deterministic notification IDs prevent duplicates across retries or replays.
 * - Original owner never receives self-notifications for owner-performed actions.
 * - Partner-targeted borrowing/settlement notifications only reach relevant counterparties (plus owner when not the actor).
 */
object NotificationEmitter {

    fun buildForConfirmedOperation(
        op: PendingOperationEntity,
        workspaceMembers: List<WorkspaceMember>,
        ownerUid: String,
        fallbackActorName: String = ""
    ): List<AppNotificationEntity> {
        if (op.workspaceId.isBlank() || op.operationId.isBlank() || op.actorUid.isBlank()) {
            return emptyList()
        }
        val payload = runCatching { JSONObject(op.payloadJson) }.getOrElse { JSONObject() }
        val resolvedActorName = resolveMemberName(op.actorUid, workspaceMembers, fallbackActorName).ifBlank {
            payload.optString("addedByPartner").ifBlank {
                payload.optString("collectedByName").ifBlank {
                    payload.optString("editedByName").ifBlank { "Partner" }
                }
            }
        }
        val ts = op.actionTime.takeIf { it > 0L } ?: System.currentTimeMillis()
        val out = mutableListOf<AppNotificationEntity>()

        fun addOwnerNotification(
            eventType: String,
            amount: Double = 0.0,
            counterpartyName: String = "",
            meta: JSONObject = JSONObject()
        ) {
            if (ownerUid.isBlank() || op.actorUid == ownerUid) return
            val id = "${op.operationId}_OWNER_${eventType}"
            out += AppNotificationEntity(
                id = id,
                workspaceId = op.workspaceId,
                operationId = op.operationId,
                recipientUid = ownerUid,
                targetAudience = AppNotificationEntity.AUDIENCE_OWNER_ONLY,
                actorUid = op.actorUid,
                actorName = resolvedActorName,
                eventType = eventType,
                entityType = op.entityType,
                entityId = op.entityId,
                amount = amount,
                counterpartyName = counterpartyName,
                metadataJson = meta.toString(),
                createdAt = ts,
                isRead = false,
                readAt = 0L
            )
        }

        fun addPartnerNotification(
            recipientUid: String,
            eventType: String,
            amount: Double = 0.0,
            counterpartyName: String = "",
            meta: JSONObject = JSONObject()
        ) {
            if (recipientUid.isBlank() || recipientUid == op.actorUid) return
            val id = "${op.operationId}_PARTNER_${recipientUid}_${eventType}"
            out += AppNotificationEntity(
                id = id,
                workspaceId = op.workspaceId,
                operationId = op.operationId,
                recipientUid = recipientUid,
                targetAudience = AppNotificationEntity.AUDIENCE_PARTNER_TARGETED,
                actorUid = op.actorUid,
                actorName = resolvedActorName,
                eventType = eventType,
                entityType = op.entityType,
                entityId = op.entityId,
                amount = amount,
                counterpartyName = counterpartyName,
                metadataJson = meta.toString(),
                createdAt = ts,
                isRead = false,
                readAt = 0L
            )
        }

        when (op.entityType) {
            "JOB" -> {
                val customer = payload.optString("customerName")
                val amount = payload.optDouble("totalAmount", 0.0).takeIf { !it.isNaN() } ?: 0.0
                if (op.mutationType == "DELETE") {
                    addOwnerNotification(AppNotificationEntity.EVENT_JOB_DELETED, amount, customer)
                } else {
                    val isEdit = payload.optString("editedByUid").isNotBlank() ||
                        (payload.optLong("createdAt", 0L) > 0L && payload.optLong("updatedAt", 0L) > payload.optLong("createdAt", 0L))
                    addOwnerNotification(
                        if (isEdit) AppNotificationEntity.EVENT_JOB_EDITED else AppNotificationEntity.EVENT_JOB_CREATED,
                        amount,
                        customer
                    )
                }
            }

            "PAYMENT" -> {
                val customer = payload.optString("customerName")
                val amount = payload.optDouble("amount", 0.0).takeIf { !it.isNaN() } ?: 0.0
                val isReverse = op.mutationType == "REVERSE" || payload.optString("status").equals("REVERSED", ignoreCase = true)
                addOwnerNotification(
                    if (isReverse) AppNotificationEntity.EVENT_PAYMENT_REVERSED else AppNotificationEntity.EVENT_PAYMENT_RECEIVED,
                    amount,
                    customer
                )
            }

            "EXPENSE" -> {
                val category = payload.optString("expenseType")
                val amount = payload.optDouble("amount", 0.0).takeIf { !it.isNaN() } ?: 0.0
                if (op.mutationType == "DELETE") {
                    addOwnerNotification(AppNotificationEntity.EVENT_EXPENSE_DELETED, amount, category)
                } else {
                    val created = payload.optLong("createdAt", 0L)
                    val isEdit = created > 0L && op.actionTime > created + 1000L && payload.optLong("relatedJobId", 0L) <= 0L
                    addOwnerNotification(
                        if (isEdit) AppNotificationEntity.EVENT_EXPENSE_EDITED else AppNotificationEntity.EVENT_EXPENSE_ADDED,
                        amount,
                        category
                    )
                }
            }

            "WITHDRAWAL" -> if (op.mutationType != "DELETE") {
                val amount = payload.optDouble("amount", 0.0).takeIf { !it.isNaN() } ?: 0.0
                val targetUid = payload.optString("targetPartnerUid")
                val targetName = payload.optString("partnerName").ifBlank { resolveMemberName(targetUid, workspaceMembers, "") }
                addOwnerNotification(AppNotificationEntity.EVENT_WITHDRAWAL_RECORDED, amount, targetName)
                if (targetUid.isNotBlank() && targetUid != op.actorUid && targetUid != ownerUid) {
                    addPartnerNotification(targetUid, AppNotificationEntity.EVENT_WITHDRAWAL_RECORDED, amount, targetName)
                }
            }

            "EXTERNAL_LOAN" -> if (op.mutationType != "DELETE") {
                val amount = payload.optDouble("principalAmount", 0.0).takeIf { !it.isNaN() } ?: 0.0
                val lender = payload.optString("lenderName")
                val meta = JSONObject().put("destination", payload.optString("destination"))
                addOwnerNotification(AppNotificationEntity.EVENT_EXTERNAL_LOAN_CREATED, amount, lender, meta)
            }

            "EXTERNAL_LOAN_REPAYMENT" -> if (op.mutationType != "DELETE") {
                val principal = payload.optDouble("principalPaid", 0.0).takeIf { !it.isNaN() } ?: 0.0
                val interest = payload.optDouble("interestPaid", 0.0).takeIf { !it.isNaN() } ?: 0.0
                val total = principal + interest
                val meta = JSONObject()
                    .put("principalPaid", principal)
                    .put("interestPaid", interest)
                    .put("source", payload.optString("source"))
                addOwnerNotification(
                    AppNotificationEntity.EVENT_EXTERNAL_LOAN_REPAID,
                    total,
                    "Loan #${payload.optLong("loanId")}",
                    meta
                )
            }

            "BORROWING_REQUEST" -> {
                val action = payload.optString("stage3Action")
                val amount = payload.optDouble("amount", 0.0).takeIf { !it.isNaN() } ?: 0.0
                val borrowerUid = payload.optString("borrowerUid")
                val borrowerName = payload.optString("borrowerName").ifBlank { resolveMemberName(borrowerUid, workspaceMembers, "Partner") }
                val lenderUid = payload.optString("lendingPartnerUid")
                val lenderName = payload.optString("lendingPartnerName").ifBlank { resolveMemberName(lenderUid, workspaceMembers, "Partner") }
                val sourceType = payload.optString("sourceType")
                val meta = JSONObject()
                    .put("borrowerName", borrowerName)
                    .put("lendingPartnerName", lenderName)
                    .put("sourceType", sourceType)
                    .put("rejectionReason", payload.optString("rejectionReason"))

                when (action) {
                    "CREATE" -> {
                        if (lenderUid.isNotBlank()) {
                            addPartnerNotification(
                                lenderUid,
                                AppNotificationEntity.EVENT_BORROWING_REQUESTED,
                                amount,
                                borrowerName,
                                meta
                            )
                        }
                        if (ownerUid != op.actorUid && ownerUid != lenderUid) {
                            addOwnerNotification(
                                AppNotificationEntity.EVENT_BORROWING_REQUESTED,
                                amount,
                                "$borrowerName → ${lenderName.ifBlank { "Business" }}",
                                meta
                            )
                        }
                    }

                    "APPROVE_RESERVE" -> {
                        addPartnerNotification(
                            borrowerUid,
                            AppNotificationEntity.EVENT_BORROWING_APPROVED,
                            amount,
                            lenderName,
                            meta
                        )
                        if (ownerUid != op.actorUid && ownerUid != borrowerUid) {
                            addOwnerNotification(AppNotificationEntity.EVENT_BORROWING_APPROVED, amount, borrowerName, meta)
                        }
                    }

                    "REJECT" -> {
                        addPartnerNotification(
                            borrowerUid,
                            AppNotificationEntity.EVENT_BORROWING_REJECTED,
                            amount,
                            lenderName,
                            meta
                        )
                        if (ownerUid != op.actorUid && ownerUid != borrowerUid) {
                            addOwnerNotification(AppNotificationEntity.EVENT_BORROWING_REJECTED, amount, borrowerName, meta)
                        }
                    }

                    "CANCEL" -> {
                        if (lenderUid.isNotBlank()) {
                            addPartnerNotification(
                                lenderUid,
                                AppNotificationEntity.EVENT_BORROWING_CANCELLED,
                                amount,
                                borrowerName,
                                meta
                            )
                        }
                        if (ownerUid != op.actorUid && ownerUid != lenderUid) {
                            addOwnerNotification(AppNotificationEntity.EVENT_BORROWING_CANCELLED, amount, borrowerName, meta)
                        }
                    }

                    "EXECUTE", "EXECUTE_BUSINESS" -> {
                        if (lenderUid.isNotBlank() && lenderUid != op.actorUid) {
                            addPartnerNotification(
                                lenderUid,
                                AppNotificationEntity.EVENT_BORROWING_EXECUTED,
                                amount,
                                borrowerName,
                                meta
                            )
                        }
                        if (ownerUid != op.actorUid) {
                            addOwnerNotification(
                                AppNotificationEntity.EVENT_BORROWING_EXECUTED,
                                amount,
                                borrowerName,
                                meta
                            )
                        }
                    }
                }
            }

            "SETTLEMENT_REPAYMENT" -> {
                val action = payload.optString("stage3Action")
                val amount = payload.optDouble("amount", 0.0).takeIf { !it.isNaN() } ?: 0.0
                val payerUid = payload.optString("payerUid")
                val payerName = payload.optString("payerName").ifBlank { resolveMemberName(payerUid, workspaceMembers, "Partner") }
                val creditorUid = payload.optString("creditorUid")
                val creditorName = payload.optString("creditorName").ifBlank { "Business" }
                val meta = JSONObject()
                    .put("payerName", payerName)
                    .put("creditorName", creditorName)
                    .put("mode", payload.optString("mode"))
                    .put("rejectionReason", payload.optString("rejectionReason"))

                when (action) {
                    "CREATE" -> {
                        if (creditorUid.isNotBlank()) {
                            addPartnerNotification(
                                creditorUid,
                                AppNotificationEntity.EVENT_SETTLEMENT_REPAYMENT_RECORDED,
                                amount,
                                payerName,
                                meta
                            )
                        }
                        if (ownerUid != op.actorUid && ownerUid != creditorUid) {
                            addOwnerNotification(
                                AppNotificationEntity.EVENT_SETTLEMENT_REPAYMENT_RECORDED,
                                amount,
                                "$payerName → $creditorName",
                                meta
                            )
                        }
                    }

                    "CONFIRM" -> {
                        addPartnerNotification(
                            payerUid,
                            AppNotificationEntity.EVENT_SETTLEMENT_REPAYMENT_CONFIRMED,
                            amount,
                            creditorName,
                            meta
                        )
                        if (ownerUid != op.actorUid && ownerUid != payerUid) {
                            addOwnerNotification(
                                AppNotificationEntity.EVENT_SETTLEMENT_REPAYMENT_CONFIRMED,
                                amount,
                                payerName,
                                meta
                            )
                        }
                    }

                    "REJECT" -> {
                        addPartnerNotification(
                            payerUid,
                            AppNotificationEntity.EVENT_SETTLEMENT_REPAYMENT_REJECTED,
                            amount,
                            creditorName,
                            meta
                        )
                        if (ownerUid != op.actorUid && ownerUid != payerUid) {
                            addOwnerNotification(
                                AppNotificationEntity.EVENT_SETTLEMENT_REPAYMENT_REJECTED,
                                amount,
                                payerName,
                                meta
                            )
                        }
                    }
                }
            }
        }
        return out
    }

    private fun resolveMemberName(uid: String, members: List<WorkspaceMember>, fallback: String): String {
        if (uid.isBlank()) return fallback
        val m = members.firstOrNull { it.uid == uid }
        return m?.displayName?.takeIf { it.isNotBlank() }
            ?: m?.phoneNumber?.takeIf { it.isNotBlank() }
            ?: fallback
    }
}
