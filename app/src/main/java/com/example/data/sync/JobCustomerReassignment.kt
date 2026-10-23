package com.example.data.sync

import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.PendingOperationEntity
import org.json.JSONArray

/** Reassignment changes a job relationship, never a receipt's historical identity. */
object JobCustomerReassignment {
    fun singleSource(payment: PaymentEntity, job: JobEntryEntity): Boolean = runCatching {
        val allocations = JSONArray(payment.allocationsJson)
        payment.workspaceId == job.workspaceId && payment.jobEntryId == job.id &&
            payment.customerId > 0 && payment.amount.isFinite() && payment.amount > 0 &&
            allocations.length() == 1 && allocations.getJSONObject(0).optLong("jobId") == job.id &&
            kotlin.math.abs(allocations.getJSONObject(0).getDouble("amount") - payment.amount) < 0.001
    }.getOrDefault(false)

    fun requireSafe(job: JobEntryEntity, payments: List<PaymentEntity>, operations: List<PendingOperationEntity>) {
        val linked = payments.filter { EntryDeletionPolicy.references(it, job.id) }
        require(linked.all { singleSource(it, job) && it.status in setOf("ACTIVE", "REVERSED") }) {
            "Reassignment is protected: a shared or inconsistent Collection needs separate review. No records changed."
        }
        require(operations.none { it.entityType == "PAYMENT" && OperationDependencies.references(it).contains("JOB" to job.id) }) {
            "Reassignment is protected: resolve the pending or rejected Collection first. Its original action is retained."
        }
        require(job.amountReceived.isFinite() && job.amountReceived >= 0 &&
            kotlin.math.abs(linked.filter { it.status == "ACTIVE" }.sumOf { it.amount } - job.amountReceived) < 0.001) {
            "Reassignment is protected: the job and its Collection history need reconciliation first."
        }
    }
}
