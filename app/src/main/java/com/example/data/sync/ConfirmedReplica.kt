package com.example.data.sync

import com.example.data.entity.*

/** Only an authoritative server snapshot may call these checks. Pending/rejected edits win.
 * Missing identity fields may be restored from the exact same source ID, never a name match.
 * Every business field must still agree before a stale unsynced flag can be acknowledged. */
object ConfirmedReplica {
    fun job(local: JobEntryEntity, remote: JobEntryEntity, outstanding: Boolean): Boolean = !outstanding &&
        remote.createdByUid.isNotBlank() && remote.entryForUid.isNotBlank() &&
        local.copy(isSynced = true, updatedAt = remote.updatedAt,
            createdByUid = local.createdByUid.ifBlank { remote.createdByUid },
            entryForUid = local.entryForUid.ifBlank { remote.entryForUid },
            financialAttributionUid = local.financialAttributionUid.ifBlank { remote.financialAttributionUid },
            createdByRole = local.createdByRole.ifBlank { remote.createdByRole }) == remote.copy(isSynced = true)

    fun expense(local: ExpenseEntity, remote: ExpenseEntity, outstanding: Boolean): Boolean = !outstanding &&
        local.copy(isSynced = true) == remote.copy(isSynced = true)
    fun withdrawal(local: WithdrawalEntity, remote: WithdrawalEntity, outstanding: Boolean): Boolean = !outstanding &&
        local.copy(isSynced = true) == remote.copy(isSynced = true)
    fun payment(local: PaymentEntity, remote: PaymentEntity, outstanding: Boolean): Boolean = !outstanding &&
        local.copy(isSynced = true) == remote.copy(isSynced = true)
}
