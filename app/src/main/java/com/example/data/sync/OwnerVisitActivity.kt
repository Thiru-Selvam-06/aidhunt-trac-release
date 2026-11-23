package com.example.data.sync

import com.example.data.auth.RoleUtils
import com.example.data.entity.AppNotificationEntity
import com.example.data.entity.AppNotificationEntity.Companion as Event
import com.example.data.firebase.WorkspaceMember
import org.json.JSONObject

internal data class OwnerVisitKey(val workspaceId: String, val ownerUid: String) {
    val preferenceKey: String get() = ownerUid.length.toString() + ":" + ownerUid + workspaceId
}

internal enum class OwnerVisitStatus { LOADING, OFFLINE, READY, PARTIAL, FAILED }

internal data class OwnerVisit(
    val key: OwnerVisitKey,
    val since: Long?,
    val lastUpdated: Long?,
    val status: OwnerVisitStatus,
    val recentIds: Set<String> = emptySet(),
    val firstVisit: Boolean = since == null,
    val diagnostics: Set<String> = emptySet()
) {
    // Keep the same boundary throughout the foreground visit, including reconnects.
    fun refreshed(serverTime: Long, recent: Set<String>) = copy(
        since = since ?: serverTime,
        lastUpdated = maxOf(lastUpdated ?: 0L, serverTime),
        status = OwnerVisitStatus.READY,
        recentIds = recentIds + recent,
        diagnostics = emptySet()
    )
}

internal data class OwnerActivityEvidence(
    val collection: String,
    val documentId: String,
    val data: Map<String, Any?>,
    val serverCreated: String,
    val serverUpdated: String,
    val availableAt: Long
)

internal data class ConfirmedOwnerActivity(val item: AppNotificationEntity, val availableAt: Long)

internal object OwnerVisitActivity {
    const val ID_PREFIX = "verified-owner:"
    const val AUDIENCE = "OWNER_VISIT"
    const val OLD_ENTRY = "OLD_ENTRY_CREATED"
    val eventTypes = setOf(Event.EVENT_JOB_CREATED, OLD_ENTRY, Event.EVENT_JOB_EDITED,
        Event.EVENT_JOB_DELETED, Event.EVENT_PAYMENT_RECEIVED, Event.EVENT_PAYMENT_REVERSED,
        Event.EVENT_EXPENSE_ADDED, Event.EVENT_EXPENSE_EDITED, Event.EVENT_EXPENSE_DELETED,
        Event.EVENT_WITHDRAWAL_RECORDED, Event.EVENT_CASH_BALANCE_ADJUSTED)
    private val operationIdPattern = Regex("[A-Za-z0-9:_-]{1,160}")

    fun verified(key: OwnerVisitKey, members: List<WorkspaceMember>,
                 evidence: List<OwnerActivityEvidence>): List<ConfirmedOwnerActivity> {
        val partners = members.filter {
            it.workspaceId == key.workspaceId && it.uid != key.ownerUid &&
                it.status.equals("ACTIVE", true) && RoleUtils.normalizeRole(it.role) == RoleUtils.ROLE_PARTNER
        }.associateBy { it.uid }
        val activities = linkedMapOf<String, ConfirmedOwnerActivity>()
        for (document in evidence) {
            val row = document.data
            if (row["workspaceId"] != key.workspaceId || document.availableAt <= 0) continue
            val directId = document.documentId.toLongOrNull()
            fun emit(type: String, entity: String, id: Long?, operation: Any?, actor: Any?, time: Any?,
                     reference: Any? = null, availableAt: Long = document.availableAt,
                     amount: Double = 0.0, metadataJson: String = "{}") {
                val op = operation as? String ?: return
                val member = partners[actor as? String] ?: return
                val recordedAt = (time as? Long)?.takeIf { it in 1..document.availableAt } ?: return
                if (id == null || id <= 0 || !operationIdPattern.matches(op)) return
                val keyId = ID_PREFIX + key.ownerUid.length + ":" + key.ownerUid + ":" + op + ":" + type
                val notification = AppNotificationEntity(
                    id = keyId, workspaceId = key.workspaceId, operationId = op, recipientUid = key.ownerUid,
                    targetAudience = AUDIENCE, actorUid = member.uid, actorName = member.displayName.orEmpty(),
                    eventType = type, entityType = entity, entityId = id,
                    counterpartyName = (reference as? String).orEmpty(), createdAt = recordedAt,
                    amount = amount, metadataJson = metadataJson
                )
                activities.putIfAbsent(keyId, ConfirmedOwnerActivity(notification, availableAt))
            }

            val current = row["protocolVersion"] == 2L && row["isDeleted"] == false &&
                row["operationType"] == "UPSERT"
            val creation = document.serverCreated.isNotBlank() &&
                document.serverCreated == document.serverUpdated && row["authorizationTimeStatus"] == "CLIENT_ORIGINAL_V2"
            val op = row["operationId"]
            val actor = row["actorUid"]
            val time = row["actionTime"]
            val creatorMatches = actor == row["createdByUid"]
            fun jobEdits(id: Long?, history: Any?) {
                (history as? List<*>)?.forEach { value ->
                    val edit = value as? Map<*, *> ?: return@forEach
                    val after = edit["after"] as? Map<*, *> ?: return@forEach
                    if (edit["before"] !is Map<*, *>) return@forEach
                    val recordedAt = edit["actionTime"] as? Long ?: return@forEach
                    emit(Event.EVENT_JOB_EDITED, "JOB", id, edit["operationId"], edit["actorUid"],
                        recordedAt, after["customerName"], availableAt = recordedAt)
                }
            }

            when (document.collection) {
                "cashBalanceAdjustments" -> {
                    val revision = row["revision"] as? Long
                    val before = row["beforePaise"] as? Long
                    val after = row["afterPaise"] as? Long
                    val delta = row["deltaPaise"] as? Long
                    val confirmed = row["confirmedAt"] as? Long
                    val reason = row["reason"] as? String
                    val bounds = -1_000_000_000_000L..1_000_000_000_000L
                    if (op == document.documentId && document.serverCreated.isNotBlank() &&
                        document.serverCreated == document.serverUpdated && revision != null && revision > 0 &&
                        before != null && before in bounds && after != null && after in bounds &&
                        delta != null && delta in bounds && delta != 0L && after - before == delta &&
                        confirmed != null && reason != null && reason.isNotBlank() && reason.length <= 500) {
                        emit(Event.EVENT_CASH_BALANCE_ADJUSTED, "CASH_BALANCE", revision, op, actor, confirmed,
                            amount = delta / 100.0, metadataJson = JSONObject().put("beforePaise", before)
                                .put("afterPaise", after).put("deltaPaise", delta).put("reason", reason).toString())
                    }
                }
                "entries" -> if (row["id"] == directId && row["isDeleted"] == false) {
                    if (current && creation && creatorMatches && row["isHistorical"] is Boolean)
                        emit(if (row["isHistorical"] == true) OLD_ENTRY else Event.EVENT_JOB_CREATED,
                            "JOB", directId, op, actor, time, row["customerName"])
                    jobEdits(directId, row["jobEditHistory"])
                }
                "entryDeletionProofs" -> if (row["recordId"] == document.documentId && row["protocolVersion"] == 2L) {
                    emit(Event.EVENT_JOB_DELETED, "JOB", directId, op, actor, time)
                    jobEdits(directId, row["deletedJobEditHistory"])
                }
                "collections" -> if (row["id"] == directId && row["protocolVersion"] == 2L && row["isDeleted"] == false) {
                    if (current && row["status"] == "ACTIVE" && actor == row["collectedByUid"])
                        emit(Event.EVENT_PAYMENT_RECEIVED, "PAYMENT", directId, op, actor, time, row["customerName"])
                    if (row["status"] == "REVERSED" && actor == row["reversedByUid"] &&
                        row["operationType"] in setOf("UPSERT", "REVERSE"))
                        emit(Event.EVENT_PAYMENT_REVERSED, "PAYMENT", directId, op, actor, time, row["customerName"])
                }
                "expenses" -> if (current && row["id"] == directId && creatorMatches &&
                    row["authorizationTimeStatus"] == "CLIENT_ORIGINAL_V2" && row["paidBy"] != "Loan Funded" &&
                    row["fundingSource"] != "EXTERNAL_LOAN" && ((row["externalLoanId"] as? Long) ?: 0L) == 0L &&
                    document.serverCreated.isNotBlank() && document.serverUpdated.isNotBlank()) {
                    emit(if (creation) Event.EVENT_EXPENSE_ADDED else Event.EVENT_EXPENSE_EDITED,
                        "EXPENSE", directId, op, actor, time, row["expenseType"])
                }
                "cashOperations" -> if (op == document.documentId) {
                    val id = (row["entityId"] as? String)?.toLongOrNull()
                    val type = row["entityType"]
                    when {
                        type == "PAYMENT" && row["mutationType"] == "UPSERT" ->
                            emit(Event.EVENT_PAYMENT_RECEIVED, "PAYMENT", id, op, actor, time)
                        type == "PAYMENT" && row["mutationType"] == "REVERSE" ->
                            emit(Event.EVENT_PAYMENT_REVERSED, "PAYMENT", id, op, actor, time)
                        type == "WITHDRAWAL" && row["mutationType"] == "UPSERT" ->
                            emit(Event.EVENT_WITHDRAWAL_RECORDED, "WITHDRAWAL", id, op, actor, time)
                    }
                }
                "cashDeleteProofs" -> if (row["kind"] == "EXPENSE" && document.documentId == "EXPENSE_" + row["recordId"])
                    emit(Event.EVENT_EXPENSE_DELETED, "EXPENSE", (row["recordId"] as? String)?.toLongOrNull(), op, actor, time)
                "withdrawals" -> if (current && row["id"] == directId && creatorMatches &&
                    row["targetPartnerUid"] == actor && row["borrowingRequestId"] in setOf(null, 0L) &&
                    row["isBorrowing"] != true && row["withdrawalType"] in setOf(null, "", "ORDINARY"))
                    emit(Event.EVENT_WITHDRAWAL_RECORDED, "WITHDRAWAL", directId, op, actor, time)
            }
        }
        return activities.values.toList()
    }

    fun sameOperation(verified: AppNotificationEntity, legacy: AppNotificationEntity): Boolean =
        legacy.targetAudience == Event.AUDIENCE_OWNER_ONLY && legacy.recipientUid == verified.recipientUid &&
            legacy.workspaceId == verified.workspaceId && legacy.operationId == verified.operationId &&
            legacy.actorUid == verified.actorUid && legacy.entityType == verified.entityType &&
            legacy.entityId == verified.entityId &&
            (legacy.eventType == verified.eventType ||
                (verified.eventType == OLD_ENTRY && legacy.eventType == Event.EVENT_JOB_CREATED))
}
