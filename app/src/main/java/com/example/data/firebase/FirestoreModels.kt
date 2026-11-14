package com.example.data.firebase

import androidx.annotation.Keep
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.WorkTypeExtensionEntity
import com.example.data.entity.ExternalLoanEntity
import com.example.data.entity.ExternalLoanRepaymentEntity
import com.example.data.entity.BorrowingRequestEntity
import com.example.data.entity.SettlementObligationEntity
import com.example.data.entity.SettlementRepaymentEntity
import com.example.data.entity.AppNotificationEntity

/**
 * Standard phone normalization function used across Phone Directory registration,
 * Direct Partner Lookup, and Account management.
 * Examples:
 *   "8925624885" -> "+918925624885"
 *   "+918925624885" -> "+918925624885"
 *   "+91 89256-24885" -> "+918925624885"
 */
fun normalizePhoneNumber(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""
    val digits = trimmed.filter { it.isDigit() }
    if (digits.isBlank()) return ""
    if (trimmed.startsWith("+")) {
        return "+$digits"
    }
    if (digits.length == 12 && digits.startsWith("91")) {
        return "+$digits"
    }
    val clean10 = digits.takeLast(10)
    return "+91$clean10"
}

@Keep
data class CollaborationGroup(
    val groupId: String = "",
    val ownerUid: String = "",
    val ownerWorkspaceId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "groupId" to groupId,
        "ownerUid" to ownerUid,
        "ownerWorkspaceId" to ownerWorkspaceId,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): CollaborationGroup {
            return CollaborationGroup(
                groupId = map["groupId"] as? String ?: "",
                ownerUid = map["ownerUid"] as? String ?: "",
                ownerWorkspaceId = map["ownerWorkspaceId"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}

@Keep
data class CollaborationGroupMember(
    val uid: String = "",
    val workspaceId: String = "",
    val role: String = "partner", // "owner", "partner"
    val status: String = "active", // "active", "removed"
    val joinedAt: Long = System.currentTimeMillis(),
    val phoneNumber: String? = null,
    val displayName: String? = null
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "workspaceId" to workspaceId,
        "role" to role,
        "status" to status,
        "joinedAt" to joinedAt,
        "phoneNumber" to phoneNumber,
        "displayName" to displayName
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): CollaborationGroupMember {
            return CollaborationGroupMember(
                uid = map["uid"] as? String ?: "",
                workspaceId = map["workspaceId"] as? String ?: "",
                role = map["role"] as? String ?: "partner",
                status = map["status"] as? String ?: "active",
                joinedAt = (map["joinedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                phoneNumber = map["phoneNumber"] as? String,
                displayName = map["displayName"] as? String
            )
        }
    }
}

@Keep
data class UserCollaborationGroupIndex(
    val groupId: String = "",
    val ownerUid: String = "",
    val ownerWorkspaceId: String = "",
    val role: String = "partner",
    val status: String = "active",
    val joinedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "groupId" to groupId,
        "ownerUid" to ownerUid,
        "ownerWorkspaceId" to ownerWorkspaceId,
        "role" to role,
        "status" to status,
        "joinedAt" to joinedAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): UserCollaborationGroupIndex {
            return UserCollaborationGroupIndex(
                groupId = map["groupId"] as? String ?: "",
                ownerUid = map["ownerUid"] as? String ?: "",
                ownerWorkspaceId = map["ownerWorkspaceId"] as? String ?: "",
                role = map["role"] as? String ?: "partner",
                status = map["status"] as? String ?: "active",
                joinedAt = (map["joinedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}

@Keep
data class PendingPartnerPhone(
    val normalizedPhone: String = "",
    val displayName: String = "",
    val role: String = "partner",
    val addedByUid: String = "",
    val groupId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "waiting_for_registration"
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "normalizedPhone" to normalizedPhone,
        "displayName" to displayName,
        "role" to role,
        "addedByUid" to addedByUid,
        "groupId" to groupId,
        "createdAt" to createdAt,
        "status" to status
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): PendingPartnerPhone {
            return PendingPartnerPhone(
                normalizedPhone = map["normalizedPhone"] as? String ?: "",
                displayName = map["displayName"] as? String ?: "",
                role = map["role"] as? String ?: "partner",
                addedByUid = map["addedByUid"] as? String ?: "",
                groupId = map["groupId"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                status = map["status"] as? String ?: "waiting_for_registration"
            )
        }
    }
}

@Keep
data class Workspace(
    val workspaceId: String = "",
    val name: String = "",
    val ownerUid: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    @get:com.google.firebase.firestore.Exclude val accessRole: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "workspaceId" to workspaceId,
        "name" to name,
        "ownerUid" to ownerUid,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): Workspace {
            return Workspace(
                workspaceId = map["workspaceId"] as? String ?: "",
                name = map["name"] as? String ?: "",
                ownerUid = map["ownerUid"] as? String ?: "",
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}

@Keep
data class WorkspaceMember(
    val uid: String = "",
    val workspaceId: String = "",
    val role: String = "", // "owner", "partner"
    val status: String = "", // "active"
    val phoneNumber: String? = null,
    val joinedAt: Long = System.currentTimeMillis(),
    val addedByUid: String? = null,
    val invitedByUid: String? = null,
    val displayName: String? = null,
    val email: String? = null,
    val defaultHourlyRate: Double? = null,
    // Local provenance for pending cleanup; not a Room column or a persisted membership field.
    val pendingWorkspaceId: String = ""
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "uid" to uid,
        "workspaceId" to workspaceId,
        "role" to role,
        "status" to status,
        "phoneNumber" to phoneNumber,
        "joinedAt" to joinedAt,
        "addedByUid" to (addedByUid ?: invitedByUid),
        "invitedByUid" to (invitedByUid ?: addedByUid),
        "displayName" to displayName,
        "email" to email,
        "defaultHourlyRate" to defaultHourlyRate,
        "hourlyRate" to defaultHourlyRate
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): WorkspaceMember {
            return WorkspaceMember(
                uid = map["uid"] as? String ?: "",
                workspaceId = map["workspaceId"] as? String ?: "",
                role = map["role"] as? String ?: "",
                status = map["status"] as? String ?: "",
                phoneNumber = map["phoneNumber"] as? String,
                joinedAt = (map["joinedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                addedByUid = map["addedByUid"] as? String ?: map["invitedByUid"] as? String,
                invitedByUid = map["invitedByUid"] as? String ?: map["addedByUid"] as? String,
                displayName = map["displayName"] as? String,
                email = map["email"] as? String,
                defaultHourlyRate = (map["defaultHourlyRate"] as? Number)?.toDouble()
                    ?: (map["hourlyRate"] as? Number)?.toDouble()
            )
        }
    }
}

@Keep
data class WorkspaceInvitation(
    val invitationId: String = "",
    val workspaceId: String = "",
    val workspaceName: String = "",
    val invitedByUid: String = "",
    val ownerUid: String = "",
    val ownerName: String = "",
    val ownerPhone: String = "",
    val invitedPhoneNumber: String = "",
    val inviteePhone: String = "",
    val inviteeName: String = "",
    val role: String = "partner",
    val status: String = "pending", // "pending", "accepted", "declined"
    val acceptedByUid: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "invitationId" to invitationId,
        "workspaceId" to workspaceId,
        "workspaceName" to workspaceName,
        "invitedByUid" to (invitedByUid.ifBlank { ownerUid }),
        "ownerUid" to (ownerUid.ifBlank { invitedByUid }),
        "ownerName" to ownerName,
        "ownerPhone" to ownerPhone,
        "invitedPhoneNumber" to (invitedPhoneNumber.ifBlank { inviteePhone }),
        "inviteePhone" to (inviteePhone.ifBlank { invitedPhoneNumber }),
        "inviteeName" to inviteeName,
        "role" to role,
        "status" to status,
        "acceptedByUid" to acceptedByUid,
        "createdAt" to createdAt,
        "updatedAt" to updatedAt
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): WorkspaceInvitation {
            val invBy = map["invitedByUid"] as? String ?: map["ownerUid"] as? String ?: ""
            val invPhone = map["invitedPhoneNumber"] as? String ?: map["inviteePhone"] as? String ?: ""
            return WorkspaceInvitation(
                invitationId = map["invitationId"] as? String ?: "",
                workspaceId = map["workspaceId"] as? String ?: "",
                workspaceName = map["workspaceName"] as? String ?: "",
                invitedByUid = invBy,
                ownerUid = map["ownerUid"] as? String ?: invBy,
                ownerName = map["ownerName"] as? String ?: "",
                ownerPhone = map["ownerPhone"] as? String ?: "",
                invitedPhoneNumber = invPhone,
                inviteePhone = map["inviteePhone"] as? String ?: invPhone,
                inviteeName = map["inviteeName"] as? String ?: "",
                role = map["role"] as? String ?: "partner",
                status = map["status"] as? String ?: "pending",
                acceptedByUid = map["acceptedByUid"] as? String,
                createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
                updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
        }
    }
}

@Keep
data class MemberMigration(
    val partnerUid: String = "",
    val sourceWorkspaceId: String = "",
    val destinationWorkspaceId: String = "",
    val status: String = "pending", // "pending", "in_progress", "completed", "failed"
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val recordsMigrated: Int = 0
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "partnerUid" to partnerUid,
        "sourceWorkspaceId" to sourceWorkspaceId,
        "destinationWorkspaceId" to destinationWorkspaceId,
        "status" to status,
        "startedAt" to startedAt,
        "completedAt" to completedAt,
        "recordsMigrated" to recordsMigrated
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): MemberMigration {
            return MemberMigration(
                partnerUid = map["partnerUid"] as? String ?: "",
                sourceWorkspaceId = map["sourceWorkspaceId"] as? String ?: "",
                destinationWorkspaceId = map["destinationWorkspaceId"] as? String ?: "",
                status = map["status"] as? String ?: "pending",
                startedAt = (map["startedAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                completedAt = (map["completedAt"] as? Number)?.toLong(),
                recordsMigrated = (map["recordsMigrated"] as? Number)?.toInt() ?: 0
            )
        }
    }
}

@Keep
data class ProfitShareAllocation(
    val participantUid: String = "",
    val percentage: Int = 0,
    val displayName: String = "",
    val role: String = "partner",
    val isActive: Boolean = true
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "participantUid" to participantUid,
        "percentage" to percentage,
        "displayName" to displayName,
        "role" to role,
        "isActive" to isActive
    )

    companion object {
        fun fromMap(map: Map<String, Any?>): ProfitShareAllocation {
            return ProfitShareAllocation(
                participantUid = map["participantUid"] as? String ?: "",
                percentage = (map["percentage"] as? Number)?.toInt() ?: 0,
                displayName = map["displayName"] as? String ?: "",
                role = map["role"] as? String ?: "partner",
                isActive = (map["isActive"] as? Boolean) ?: true
            )
        }
    }
}

// Extension functions for Document / Map mapping

fun JobEntryEntity.toFirestoreMap(createdByUid: String? = null): Map<String, Any?> = mapOf(
    "id" to id,
    "workspaceId" to workspaceId,
    "isHistorical" to isHistorical,
    "jobEditHistory" to com.example.data.sync.JobEditAudit.read(jobEditHistoryJson),
    "entryForUid" to entryForUid,
    "financialAttributionUid" to financialAttributionUid,

    "customerId" to customerId,
    "customerName" to customerName,
    "customerPhone" to customerPhone,
    "customerLocation" to customerLocation,
    "operatorName" to operatorName,
    "tractorId" to tractorId,
    "tractorLabel" to tractorLabel,
    "workType" to workType,
    "startTimeMillis" to startTimeMillis,
    "endTimeMillis" to endTimeMillis,
    "durationMinutes" to durationMinutes,
    "hourlyRate" to hourlyRate,
    "totalAmount" to totalAmount,
    "amountReceived" to amountReceived,
    "pendingAmount" to pendingAmount,
    "addedByPartner" to addedByPartner,
    "notes" to notes,
    "createdBy" to (this.createdByUid.ifBlank { createdByUid ?: addedByPartner }),
    "createdByUid" to (this.createdByUid.ifBlank { createdByUid ?: "" }),
    "createdByName" to addedByPartner,
    "createdByRole" to createdByRole,
    "createdAt" to createdAt,
    "editedByUid" to editedByUid,
    "editedByName" to editedByName,
    "editedByRole" to editedByRole,
    "updatedAt" to (if (updatedAt > 0) updatedAt else System.currentTimeMillis())
)

fun jobEntryFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0, fallbackWorkspaceId: String = ""): JobEntryEntity {
    val id = (map["id"] as? Number)?.toLong() ?: fallbackId
    val wsId = map["workspaceId"] as? String ?: fallbackWorkspaceId
    val resolvedCreatedByUid = map["createdByUid"] as? String ?: map["createdBy"] as? String ?: ""
    val resolvedCreatedByRole = map["createdByRole"] as? String ?: ""
    return JobEntryEntity(
        id = id,
        isHistorical = map["isHistorical"] as? Boolean ?: false,
        jobEditHistoryJson = when (val history = map["jobEditHistory"]) {
            null -> "[]"
            is org.json.JSONArray -> history.toString()
            is List<*> -> if (history.isEmpty()) "[]" else org.json.JSONArray(history).toString()
            else -> throw IllegalArgumentException("Invalid job edit history; original action retained")
        },
        entryForUid = map["entryForUid"] as? String ?: "",
        financialAttributionUid = map["financialAttributionUid"] as? String ?: "",
        workspaceId = wsId,
        customerId = (map["customerId"] as? Number)?.toLong() ?: 0L,
        customerName = map["customerName"] as? String ?: "",
        customerPhone = map["customerPhone"] as? String ?: "",
        customerLocation = map["customerLocation"] as? String ?: "",
        operatorName = map["operatorName"] as? String ?: "",
        tractorId = (map["tractorId"] as? Number)?.toLong() ?: 0L,
        tractorLabel = map["tractorLabel"] as? String ?: "",
        workType = map["workType"] as? String ?: "Ploughing",
        startTimeMillis = (map["startTimeMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        endTimeMillis = (map["endTimeMillis"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        durationMinutes = (map["durationMinutes"] as? Number)?.toLong() ?: 0L,
        hourlyRate = (map["hourlyRate"] as? Number)?.toDouble() ?: 1100.0,
        totalAmount = (map["totalAmount"] as? Number)?.toDouble() ?: 0.0,
        amountReceived = (map["amountReceived"] as? Number)?.toDouble() ?: 0.0,
        pendingAmount = (map["pendingAmount"] as? Number)?.toDouble() ?: 0.0,
        addedByPartner = map["createdByName"] as? String ?: map["addedByPartner"] as? String ?: (map["createdBy"] as? String ?: "Partner"),
        notes = map["notes"] as? String ?: "",
        isSynced = true,
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
        createdByUid = resolvedCreatedByUid,
        createdByRole = resolvedCreatedByRole,
        editedByUid = map["editedByUid"] as? String ?: "",
        editedByName = map["editedByName"] as? String ?: "",
        editedByRole = map["editedByRole"] as? String ?: "",
        updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L
    )
}

fun PaymentEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "workspaceId" to workspaceId,
    "status" to status,
    "reversedAt" to reversedAt,
    "reversedByUid" to reversedByUid,
    "allocationsJson" to allocationsJson,

    "jobEntryId" to jobEntryId,
    "customerId" to customerId,
    "customerName" to customerName,
    "tractorId" to tractorId,
    "tractorLabel" to tractorLabel,
    "amount" to amount,
    "paymentMethod" to paymentMethod,
    "notes" to notes,
    "collectedByUid" to collectedByUid,
    "collectedByName" to collectedByName,
    "collectedByRole" to collectedByRole,
    "createdAt" to createdAt,
    "collectedAt" to collectedAt
) + if (collectorUid.isNotBlank()) mapOf("collectorUid" to collectorUid, "collectorName" to collectorName, "collectorRole" to collectorRole) else emptyMap()

fun exactCollectionDocumentId(documentId: String, fields: Map<String, Any?>): Long? {
    val pathId = documentId.toLongOrNull()?.takeIf { it > 0 } ?: return null
    val declared = fields["id"] ?: return pathId
    val declaredId = when (declared) {
        is Long -> declared
        is Int -> declared.toLong()
        is String -> declared.toLongOrNull()
        else -> null // Floating-point identities cannot preserve every 64-bit integer.
    }
    return pathId.takeIf { declaredId == it }
}

/** Mutable command fields only; delete staging must retain Firestore's original record provenance. */
fun deleteTombstoneFields(
    operationId: String, actorUid: String, actionTime: Long,
    dependencies: Any, authorizationDeletedAt: Any
): Map<String, Any> = mapOf(
    "protocolVersion" to 2,
    "operationType" to "DELETE",
    "dependencies" to dependencies,
    "operationId" to operationId,
    "actorUid" to actorUid,
    "actionTime" to actionTime,
    "isDeleted" to true,
    "authorizationDeletedAt" to authorizationDeletedAt
)

fun paymentFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0, fallbackWorkspaceId: String = ""): PaymentEntity {
    return PaymentEntity(
        id = (map["id"] as? Number)?.toLong() ?: fallbackId,
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
        status = map["status"] as? String ?: "ACTIVE",
        reversedAt = (map["reversedAt"] as? Number)?.toLong() ?: 0L,
        reversedByUid = map["reversedByUid"] as? String ?: "",
        allocationsJson = map["allocationsJson"] as? String ?: "[]",
        workspaceId = map["workspaceId"] as? String ?: fallbackWorkspaceId,
        jobEntryId = (map["jobEntryId"] as? Number)?.toLong() ?: 0L,
        customerId = (map["customerId"] as? Number)?.toLong() ?: 0L,
        customerName = map["customerName"] as? String ?: "",
        tractorId = (map["tractorId"] as? Number)?.toLong() ?: 0L,
        tractorLabel = map["tractorLabel"] as? String ?: "",
        amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
        paymentMethod = map["paymentMethod"] as? String ?: "Cash",
        notes = map["notes"] as? String ?: "",
        collectorUid = map["collectorUid"] as? String ?: "",
        collectorName = map["collectorName"] as? String ?: "",
        collectorRole = map["collectorRole"] as? String ?: "",
        collectedByUid = map["collectedByUid"] as? String ?: "",
        collectedByName = map["collectedByName"] as? String ?: "",
        collectedByRole = map["collectedByRole"] as? String ?: "",
        collectedAt = (map["collectedAt"] as? Number)?.toLong() ?: 0L,
        isSynced = true
    )
}

fun ExpenseEntity.toFirestoreMap(createdByUid: String? = null): Map<String, Any?> = mapOf(
    "id" to id,
    "workspaceId" to workspaceId,
    "expenseForUid" to expenseForUid,
    "financialAttributionUid" to financialAttributionUid,

    "expenseType" to expenseType,
    "amount" to amount,
    "tractorId" to tractorId,
    "tractorLabel" to tractorLabel,
    "operatorName" to operatorName,
    "description" to description,
    "addedByPartner" to addedByPartner,
    "dateTimestamp" to dateTimestamp,
    // The deployed Rules use numeric zero for an expense without a source Job.
    // Keep Room's nullable relationship; normalize only the new serialized payload.
    "relatedJobId" to (relatedJobId ?: 0L),
    "createdBy" to this.createdByUid,
    "createdByUid" to this.createdByUid,
    "createdByRole" to createdByRole,
    "createdByName" to (addedByPartner.ifBlank { operatorName }),
    "paidBy" to paidBy,
    "paidByPartner" to paidByPartner,
    "paidByUid" to paidByUid,
    "fundingSource" to fundingSource,
    "externalLoanId" to externalLoanId,
    "loanFundingMode" to loanFundingMode,
    "createdAt" to createdAt,
    "updatedAt" to System.currentTimeMillis()
)

fun expenseFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0, fallbackWorkspaceId: String = ""): ExpenseEntity {
    val id = (map["id"] as? Number)?.toLong() ?: fallbackId
    val wsId = map["workspaceId"] as? String ?: fallbackWorkspaceId
    val cUid = map["createdByUid"] as? String ?: map["createdBy"] as? String ?: ""
    val cRole = map["createdByRole"] as? String ?: ""
    val pUid = map["paidByUid"] as? String ?: ""
    return ExpenseEntity(
        id = id,
        expenseForUid = map["expenseForUid"] as? String ?: "",
        financialAttributionUid = map["financialAttributionUid"] as? String ?: "",
        workspaceId = wsId,
        expenseType = map["expenseType"] as? String ?: "Diesel",
        amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
        tractorId = (map["tractorId"] as? Number)?.toLong() ?: 0L,
        tractorLabel = map["tractorLabel"] as? String ?: "",
        operatorName = map["operatorName"] as? String ?: map["createdByName"] as? String ?: "",
        description = map["description"] as? String ?: "",
        addedByPartner = map["createdByName"] as? String ?: map["addedByPartner"] as? String ?: "Partner",
        dateTimestamp = (map["dateTimestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        relatedJobId = (map["relatedJobId"] as? Number)?.toLong()?.takeIf { it > 0 },
        isSynced = true,
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
        paidBy = map["paidBy"] as? String ?: "I Paid",
        paidByPartner = map["paidByPartner"] as? String ?: "",
        paidByUid = pUid,
        createdByUid = cUid,
        createdByRole = cRole,
        fundingSource = map["fundingSource"] as? String ?: "",
        externalLoanId = (map["externalLoanId"] as? Number)?.toLong() ?: 0L,
        loanFundingMode = map["loanFundingMode"] as? String ?: ""
    )
}

fun CustomerEntity.toFirestoreMap(createdByUid: String? = null): Map<String, Any?> = mapOf(
    "id" to id,
    "workspaceId" to workspaceId,
    "createdByUid" to this.createdByUid,

    "name" to name,
    "phone" to phone,
    "location" to location,
    "totalBilled" to totalBilled,
    "totalPaid" to totalPaid,
    "balanceDue" to balanceDue,
    "createdBy" to createdByUid,
    "createdAt" to createdAt,
    "updatedAt" to updatedAt
)

fun customerFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0, fallbackWorkspaceId: String = ""): CustomerEntity {
    val id = (map["id"] as? Number)?.toLong() ?: fallbackId
    val wsId = map["workspaceId"] as? String ?: fallbackWorkspaceId
    return CustomerEntity(
        id = id,
        createdByUid = map["createdByUid"] as? String ?: "",
        workspaceId = wsId,
        name = map["name"] as? String ?: "",
        phone = map["phone"] as? String ?: "",
        location = map["location"] as? String ?: "",
        totalBilled = (map["totalBilled"] as? Number)?.toDouble() ?: 0.0,
        totalPaid = (map["totalPaid"] as? Number)?.toDouble() ?: 0.0,
        balanceDue = (map["balanceDue"] as? Number)?.toDouble() ?: 0.0,
        isSynced = true,
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
        updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )
}

fun TractorEntity.toFirestoreMap(createdByUid: String? = null): Map<String, Any?> = mapOf(
    "id" to id,
    "workspaceId" to workspaceId,
    "createdByUid" to this.createdByUid,
    "createdByRole" to this.createdByRole,

    "label" to label,
    "chassisNo" to chassisNo,
    "modelYear" to modelYear,
    "operatorName" to operatorName,
    "isActive" to isActive,
    "createdBy" to createdByUid,
    "createdAt" to createdAt,
    "updatedAt" to System.currentTimeMillis()
)

fun tractorFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0, fallbackWorkspaceId: String = ""): TractorEntity {
    val id = (map["id"] as? Number)?.toLong() ?: fallbackId
    val wsId = map["workspaceId"] as? String ?: fallbackWorkspaceId
    return TractorEntity(
        id = id,
        createdByUid = map["createdByUid"] as? String ?: "",
        createdByRole = map["createdByRole"] as? String ?: "",
        workspaceId = wsId,
        label = map["label"] as? String ?: "Tractor",
        chassisNo = map["chassisNo"] as? String ?: "",
        modelYear = map["modelYear"] as? String ?: "",
        operatorName = map["operatorName"] as? String ?: "",
        isActive = map["isActive"] as? Boolean ?: true,
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
    )
}

fun PartnerEntity.toFirestoreMap(createdByUid: String? = null): Map<String, Any?> = mapOf(
    "id" to id,
    "firebaseUid" to firebaseUid,
    "workspaceId" to workspaceId,
    "name" to name,
    "phone" to phone,
    "role" to role,
    "avatarColorHex" to avatarColorHex,
    "photoUri" to photoUri,
    "isCurrentActive" to isCurrentActive,
    "createdBy" to createdByUid,
    "createdAt" to createdAt,
    "updatedAt" to System.currentTimeMillis()
)

fun partnerFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0, fallbackWorkspaceId: String = ""): PartnerEntity {
    val id = (map["id"] as? Number)?.toLong() ?: fallbackId
    val wsId = map["workspaceId"] as? String ?: fallbackWorkspaceId
    return PartnerEntity(
        id = id,
        workspaceId = wsId,
        firebaseUid = map["firebaseUid"] as? String ?: "",
        name = map["name"] as? String ?: "Partner",
        phone = map["phone"] as? String ?: "",
        role = map["role"] as? String ?: "Partner",
        avatarColorHex = map["avatarColorHex"] as? String ?: "#1E4D2B",
        photoUri = map["photoUri"] as? String ?: "",
        isCurrentActive = map["isCurrentActive"] as? Boolean ?: false,
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
    )
}

fun WithdrawalEntity.toFirestoreMap(createdByUid: String? = null): Map<String, Any?> = mapOf(
    "id" to id,
    "workspaceId" to workspaceId,
    "partnerId" to partnerId,
    "partnerName" to partnerName,
    "amount" to amount,
    "category" to category,
    "note" to note,
    "timestamp" to timestamp,
    "createdBy" to (createdByUid ?: this.createdByUid.ifBlank { partnerName }),
    "createdByUid" to (createdByUid ?: this.createdByUid.ifBlank { "" }),
    "createdByName" to partnerName,
    "createdByRole" to createdByRole,
    "targetPartnerUid" to targetPartnerUid,
    "createdAt" to createdAt,
    "updatedAt" to System.currentTimeMillis()
)

fun withdrawalFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0, fallbackWorkspaceId: String = ""): WithdrawalEntity {
    val id = (map["id"] as? Number)?.toLong() ?: fallbackId
    val wsId = map["workspaceId"] as? String ?: fallbackWorkspaceId
    return WithdrawalEntity(
        id = id,
        workspaceId = wsId,
        partnerId = (map["partnerId"] as? Number)?.toLong() ?: 0L,
        partnerName = map["partnerName"] as? String ?: map["createdByName"] as? String ?: "Partner",
        amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
        category = map["category"] as? String ?: "Personal Use",
        note = map["note"] as? String ?: "",
        timestamp = (map["timestamp"] as? Number)?.toLong() ?: System.currentTimeMillis(),
        isSynced = true,
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
        targetPartnerUid = map["targetPartnerUid"] as? String ?: "",
        createdByUid = map["createdByUid"] as? String ?: map["createdBy"] as? String ?: "",
        createdByRole = map["createdByRole"] as? String ?: ""
    )
}

fun AppSettingsEntity.toFirestoreMap(updatedByUid: String? = null): Map<String, Any?> = mapOf(
    "workspaceId" to workspaceId,
    "businessName" to businessName,
    "ownerName" to ownerName,
    "businessPhone" to businessPhone,
    "businessAddress" to businessAddress,
    "gstNumber" to gstNumber,
    "defaultHourlyRate" to defaultHourlyRate,
    "currency" to currency,
    "sharedAccountId" to sharedAccountId,
    "lockedTractorLabel" to lockedTractorLabel,
    "updatedBy" to updatedByUid,
    "updatedAt" to System.currentTimeMillis()
)

fun appSettingsFromFirestoreMap(map: Map<String, Any?>, currentSettings: AppSettingsEntity, fallbackWorkspaceId: String = ""): AppSettingsEntity {
    val wsId = map["workspaceId"] as? String ?: fallbackWorkspaceId.ifBlank { currentSettings.workspaceId }
    return currentSettings.copy(
        workspaceId = wsId,
        businessName = map["businessName"] as? String ?: currentSettings.businessName,
        ownerName = map["ownerName"] as? String ?: currentSettings.ownerName,
        businessPhone = map["businessPhone"] as? String ?: currentSettings.businessPhone,
        businessAddress = map["businessAddress"] as? String ?: currentSettings.businessAddress,
        gstNumber = map["gstNumber"] as? String ?: currentSettings.gstNumber,
        defaultHourlyRate = (map["defaultHourlyRate"] as? Number)?.toDouble() ?: currentSettings.defaultHourlyRate,
        currency = map["currency"] as? String ?: currentSettings.currency,
        language = currentSettings.language,
        sharedAccountId = map["sharedAccountId"] as? String ?: currentSettings.sharedAccountId,
        lockedTractorLabel = map["lockedTractorLabel"] as? String ?: currentSettings.lockedTractorLabel,
        lastSyncTime = (map["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
    )
}

fun WorkTypeExtensionEntity.toFirestoreMap(createdByUid: String? = null): Map<String, Any?> = mapOf(
    "id" to id,
    "workspaceId" to workspaceId,
    "createdByUid" to this.createdByUid,
    "createdByRole" to this.createdByRole,

    "name" to name,
    "createdBy" to (createdByUid ?: ""),
    "createdAt" to createdAt,
    "updatedAt" to System.currentTimeMillis()
)

fun extensionFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0, fallbackWorkspaceId: String = ""): WorkTypeExtensionEntity {
    val id = (map["id"] as? Number)?.toLong() ?: fallbackId
    val wsId = map["workspaceId"] as? String ?: fallbackWorkspaceId
    return WorkTypeExtensionEntity(
        id = id,
        createdByUid = map["createdByUid"] as? String ?: "",
        createdByRole = map["createdByRole"] as? String ?: "",
        workspaceId = wsId,
        name = map["name"] as? String ?: "",
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L
    )
}

fun ExternalLoanEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "workspaceId" to workspaceId,
    "lenderName" to lenderName,
    "principalAmount" to principalAmount,
    "interestAmount" to interestAmount,
    "totalRepaidPrincipal" to totalRepaidPrincipal,
    "totalRepaidInterest" to totalRepaidInterest,
    "destination" to destination,
    "linkedExpenseId" to linkedExpenseId,
    "status" to status,
    "note" to note,
    "loanDateMillis" to loanDateMillis,
    "createdAt" to createdAt,
    "updatedAt" to (if (updatedAt > 0L) updatedAt else System.currentTimeMillis()),
    "createdByUid" to createdByUid,
    "createdByRole" to createdByRole
)

fun externalLoanFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0L, fallbackWorkspaceId: String = ""): ExternalLoanEntity =
    ExternalLoanEntity(
        id = (map["id"] as? Number)?.toLong() ?: fallbackId,
        workspaceId = map["workspaceId"] as? String ?: fallbackWorkspaceId,
        lenderName = map["lenderName"] as? String ?: "",
        principalAmount = (map["principalAmount"] as? Number)?.toDouble() ?: 0.0,
        interestAmount = (map["interestAmount"] as? Number)?.toDouble() ?: 0.0,
        totalRepaidPrincipal = (map["totalRepaidPrincipal"] as? Number)?.toDouble() ?: 0.0,
        totalRepaidInterest = (map["totalRepaidInterest"] as? Number)?.toDouble() ?: 0.0,
        destination = map["destination"] as? String ?: "BUSINESS_CASH",
        linkedExpenseId = (map["linkedExpenseId"] as? Number)?.toLong() ?: 0L,
        status = map["status"] as? String ?: "ACTIVE",
        note = map["note"] as? String ?: "",
        loanDateMillis = (map["loanDateMillis"] as? Number)?.toLong() ?: 0L,
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
        updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
        createdByUid = map["createdByUid"] as? String ?: "",
        createdByRole = map["createdByRole"] as? String ?: "",
        isSynced = true
    )

fun ExternalLoanRepaymentEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "workspaceId" to workspaceId,
    "loanId" to loanId,
    "principalPaid" to principalPaid,
    "interestPaid" to interestPaid,
    "source" to source,
    "note" to note,
    "repaidAt" to repaidAt,
    "createdAt" to createdAt,
    "createdByUid" to createdByUid,
    "createdByRole" to createdByRole
)

fun externalLoanRepaymentFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0L, fallbackWorkspaceId: String = ""): ExternalLoanRepaymentEntity =
    ExternalLoanRepaymentEntity(
        id = (map["id"] as? Number)?.toLong() ?: fallbackId,
        workspaceId = map["workspaceId"] as? String ?: fallbackWorkspaceId,
        loanId = (map["loanId"] as? Number)?.toLong() ?: 0L,
        principalPaid = (map["principalPaid"] as? Number)?.toDouble() ?: 0.0,
        interestPaid = (map["interestPaid"] as? Number)?.toDouble() ?: 0.0,
        source = map["source"] as? String ?: "BUSINESS_CASH",
        note = map["note"] as? String ?: "",
        repaidAt = (map["repaidAt"] as? Number)?.toLong() ?: 0L,
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
        createdByUid = map["createdByUid"] as? String ?: "",
        createdByRole = map["createdByRole"] as? String ?: "",
        isSynced = true
    )

fun BorrowingRequestEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "workspaceId" to workspaceId,
    "borrowerUid" to borrowerUid,
    "borrowerName" to borrowerName,
    "lendingPartnerUid" to lendingPartnerUid,
    "lendingPartnerName" to lendingPartnerName,
    "sourceType" to sourceType,
    "amount" to amount,
    "note" to note,
    "status" to status,
    "reservedAmount" to reservedAmount,
    "withdrawalId" to withdrawalId,
    "obligationId" to obligationId,
    "requestedByUid" to requestedByUid,
    "requestedByRole" to requestedByRole,
    "approvedByUid" to approvedByUid,
    "approvedAt" to approvedAt,
    "rejectedByUid" to rejectedByUid,
    "rejectedAt" to rejectedAt,
    "rejectionReason" to rejectionReason,
    "executedAt" to executedAt,
    "createdAt" to createdAt,
    "updatedAt" to (if (updatedAt > 0L) updatedAt else System.currentTimeMillis())
)

fun borrowingRequestFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0L, fallbackWorkspaceId: String = ""): BorrowingRequestEntity =
    BorrowingRequestEntity(
        id = (map["id"] as? Number)?.toLong() ?: fallbackId,
        workspaceId = map["workspaceId"] as? String ?: fallbackWorkspaceId,
        borrowerUid = map["borrowerUid"] as? String ?: "",
        borrowerName = map["borrowerName"] as? String ?: "",
        lendingPartnerUid = map["lendingPartnerUid"] as? String ?: "",
        lendingPartnerName = map["lendingPartnerName"] as? String ?: "",
        sourceType = map["sourceType"] as? String ?: "BUSINESS_ONLY",
        amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
        note = map["note"] as? String ?: "",
        status = map["status"] as? String ?: "PENDING_APPROVAL",
        reservedAmount = (map["reservedAmount"] as? Number)?.toDouble() ?: 0.0,
        withdrawalId = (map["withdrawalId"] as? Number)?.toLong() ?: 0L,
        obligationId = (map["obligationId"] as? Number)?.toLong() ?: 0L,
        requestedByUid = map["requestedByUid"] as? String ?: "",
        requestedByRole = map["requestedByRole"] as? String ?: "",
        approvedByUid = map["approvedByUid"] as? String ?: "",
        approvedAt = (map["approvedAt"] as? Number)?.toLong() ?: 0L,
        rejectedByUid = map["rejectedByUid"] as? String ?: "",
        rejectedAt = (map["rejectedAt"] as? Number)?.toLong() ?: 0L,
        rejectionReason = map["rejectionReason"] as? String ?: "",
        executedAt = (map["executedAt"] as? Number)?.toLong() ?: 0L,
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
        updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
        isSynced = true
    )

fun SettlementObligationEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "workspaceId" to workspaceId,
    "borrowerUid" to borrowerUid,
    "borrowerName" to borrowerName,
    "creditorType" to creditorType,
    "creditorUid" to creditorUid,
    "creditorName" to creditorName,
    "sourceRequestId" to sourceRequestId,
    "principalAmount" to principalAmount,
    "repaidAmount" to repaidAmount,
    "directSettledAmount" to directSettledAmount,
    "status" to status,
    "note" to note,
    "createdAt" to createdAt,
    "updatedAt" to (if (updatedAt > 0L) updatedAt else System.currentTimeMillis()),
    "createdByUid" to createdByUid
)

fun settlementObligationFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0L, fallbackWorkspaceId: String = ""): SettlementObligationEntity =
    SettlementObligationEntity(
        id = (map["id"] as? Number)?.toLong() ?: fallbackId,
        workspaceId = map["workspaceId"] as? String ?: fallbackWorkspaceId,
        borrowerUid = map["borrowerUid"] as? String ?: "",
        borrowerName = map["borrowerName"] as? String ?: "",
        creditorType = map["creditorType"] as? String ?: "BUSINESS",
        creditorUid = map["creditorUid"] as? String ?: "",
        creditorName = map["creditorName"] as? String ?: "",
        sourceRequestId = (map["sourceRequestId"] as? Number)?.toLong() ?: 0L,
        principalAmount = (map["principalAmount"] as? Number)?.toDouble() ?: 0.0,
        repaidAmount = (map["repaidAmount"] as? Number)?.toDouble() ?: 0.0,
        directSettledAmount = (map["directSettledAmount"] as? Number)?.toDouble() ?: 0.0,
        status = map["status"] as? String ?: "OPEN",
        note = map["note"] as? String ?: "",
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
        updatedAt = (map["updatedAt"] as? Number)?.toLong() ?: 0L,
        createdByUid = map["createdByUid"] as? String ?: "",
        isSynced = true
    )

fun SettlementRepaymentEntity.toFirestoreMap(): Map<String, Any?> = mapOf(
    "id" to id,
    "workspaceId" to workspaceId,
    "obligationId" to obligationId,
    "payerUid" to payerUid,
    "payerName" to payerName,
    "creditorType" to creditorType,
    "creditorUid" to creditorUid,
    "creditorName" to creditorName,
    "amount" to amount,
    "mode" to mode,
    "status" to status,
    "note" to note,
    "paidAt" to paidAt,
    "createdAt" to createdAt,
    "createdByUid" to createdByUid,
    "confirmedByUid" to confirmedByUid,
    "confirmedAt" to confirmedAt,
    "rejectedByUid" to rejectedByUid,
    "rejectedAt" to rejectedAt,
    "rejectionReason" to rejectionReason
)

fun settlementRepaymentFromFirestoreMap(map: Map<String, Any?>, fallbackId: Long = 0L, fallbackWorkspaceId: String = ""): SettlementRepaymentEntity =
    SettlementRepaymentEntity(
        id = (map["id"] as? Number)?.toLong() ?: fallbackId,
        workspaceId = map["workspaceId"] as? String ?: fallbackWorkspaceId,
        obligationId = (map["obligationId"] as? Number)?.toLong() ?: 0L,
        payerUid = map["payerUid"] as? String ?: "",
        payerName = map["payerName"] as? String ?: "",
        creditorType = map["creditorType"] as? String ?: "BUSINESS",
        creditorUid = map["creditorUid"] as? String ?: "",
        creditorName = map["creditorName"] as? String ?: "",
        amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
        mode = map["mode"] as? String ?: "INTO_BUSINESS",
        status = map["status"] as? String ?: "PENDING_CONFIRMATION",
        note = map["note"] as? String ?: "",
        paidAt = (map["paidAt"] as? Number)?.toLong() ?: 0L,
        createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
        createdByUid = map["createdByUid"] as? String ?: "",
        confirmedByUid = map["confirmedByUid"] as? String ?: "",
        confirmedAt = (map["confirmedAt"] as? Number)?.toLong() ?: 0L,
        rejectedByUid = map["rejectedByUid"] as? String ?: "",
        rejectedAt = (map["rejectedAt"] as? Number)?.toLong() ?: 0L,
        rejectionReason = map["rejectionReason"] as? String ?: "",
        isSynced = true
    )

fun notificationFromFirestoreMap(
    map: Map<String, Any?>,
    fallbackId: String = "",
    fallbackWorkspaceId: String = ""
): AppNotificationEntity = AppNotificationEntity(
    id = (map["id"] as? String).orEmpty().ifBlank { fallbackId },
    workspaceId = (map["workspaceId"] as? String).orEmpty().ifBlank { fallbackWorkspaceId },
    operationId = map["operationId"] as? String ?: "",
    recipientUid = map["recipientUid"] as? String ?: "",
    targetAudience = map["targetAudience"] as? String ?: AppNotificationEntity.AUDIENCE_OWNER_ONLY,
    actorUid = map["actorUid"] as? String ?: "",
    actorName = map["actorName"] as? String ?: "",
    eventType = map["eventType"] as? String ?: "",
    entityType = map["entityType"] as? String ?: "",
    entityId = (map["entityId"] as? Number)?.toLong() ?: 0L,
    amount = (map["amount"] as? Number)?.toDouble() ?: 0.0,
    counterpartyName = map["counterpartyName"] as? String ?: "",
    metadataJson = map["metadataJson"] as? String ?: "{}",
    createdAt = (map["createdAt"] as? Number)?.toLong() ?: 0L,
    isRead = map["isRead"] as? Boolean ?: false,
    readAt = (map["readAt"] as? Number)?.toLong() ?: 0L
)
