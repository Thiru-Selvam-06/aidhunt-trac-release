package com.example.data.sync

import com.example.data.entity.ExpenseEntity
import com.example.data.auth.AuthorizationManager
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
import org.json.JSONArray

/** A reversal is retained history, never an instruction to delete a Collection. */
object EntryDeletionPolicy {
    fun references(payment: PaymentEntity, entryId: Long): Boolean {
        if (payment.jobEntryId == entryId) return true
        val allocations = JSONArray(payment.allocationsJson)
        return (0 until allocations.length()).any { allocations.getJSONObject(it).getLong("jobId") == entryId }
    }

    fun requireNoLinkedExpenses(entry: JobEntryEntity, expenses: List<ExpenseEntity>, owner: Boolean, uid: String, role: String) {
        require(expenses.all { it.workspaceId == entry.workspaceId && it.relatedJobId == entry.id &&
            AuthorizationManager.canDeleteExpense(it, owner, currentUid = uid, role = role) }) {
            "A linked Expense must first be deleted by its authorized member"
        }
        // Eligible linked Expenses are deleted by the trusted Entry transaction.
        // Do not enqueue separate child deletes: they can be rejected after the Entry succeeds.
    }

    fun requireUnpaid(entry: JobEntryEntity, linked: List<PaymentEntity>) {
        require(linked.all { it.workspaceId == entry.workspaceId }) { "Collection workspace mismatch" }
        val activeCount = linked.count { it.status == "ACTIVE" }
        require(linked.all { it.status == "REVERSED" }) {
            if (activeCount > 0) "This Entry has $activeCount active Collection${if (activeCount == 1) "" else "s"}. Reverse in Collection History first; then Delete Due also removes its eligible linked Expenses."
            else "Collection history needs review before this Entry can be deleted"
        }
        require(entry.totalAmount.isFinite() && entry.amountReceived.isFinite() && entry.pendingAmount.isFinite() &&
            entry.amountReceived == 0.0 && entry.totalAmount >= 0 && kotlin.math.abs(entry.pendingAmount - entry.totalAmount) < 0.01) {
            "Entry balances are not reconciled; sync or reverse its payments before deleting"
        }
    }

    /** Plan linked Collection deletion with the source. Shared split payments remain protected
     * because deleting one source must not change another customer's obligation. */
    fun plannedCollectionDeletes(entry: JobEntryEntity, linked: List<PaymentEntity>, owner: Boolean,
        actorUid: String, role: String, actionTime: Long): List<PaymentEntity> {
        require(linked.all { it.workspaceId == entry.workspaceId && it.status in setOf("ACTIVE", "REVERSED") }) {
            "Collection history needs reconciliation before deletion"
        }
        val active = linked.filter { it.status == "ACTIVE" }
        for (payment in active) {
            require(AuthorizationManager.canReverseCollection(payment, owner, actorUid, role, actionTime)) {
                "You cannot delete this linked Collection; ask its authorized collector or Owner"
            }
            val allocations = JSONArray(payment.allocationsJson)
            require(allocations.length() == 1 && allocations.getJSONObject(0).getString("jobId").toLongOrNull() == entry.id &&
                payment.customerId == entry.customerId && payment.amount > 0 &&
                kotlin.math.abs(allocations.getJSONObject(0).getDouble("amount") - payment.amount) < 0.01) {
                "Shared or inconsistent Collection allocations need separate reconciliation"
            }
        }
        val paid = active.sumOf { it.amount }
        require(entry.totalAmount.isFinite() && entry.amountReceived.isFinite() && entry.pendingAmount.isFinite() &&
            entry.totalAmount >= 0 && kotlin.math.abs(entry.amountReceived - paid) < 0.01 &&
            kotlin.math.abs(entry.totalAmount - entry.amountReceived - entry.pendingAmount) < 0.01) {
            "Entry and Collection balances need reconciliation before deletion"
        }
        return active
    }
}
