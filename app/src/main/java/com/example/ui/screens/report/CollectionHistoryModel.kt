package com.example.ui.screens.report

import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
import org.json.JSONArray
import java.math.BigDecimal

internal data class CollectionHistoryEvent(val payment: PaymentEntity, val amount: Double)
internal data class CollectionJobHistory(
    val key: String,
    val jobId: Long?,
    val job: JobEntryEntity?,
    val events: List<CollectionHistoryEvent>,
    val allocationNeedsReview: Boolean = false
)

/** History cards require a retained source job; stored receipts and reversals remain audit data. */
internal fun visibleCollectionJobHistory(groups: List<CollectionJobHistory>): List<CollectionJobHistory> =
    groups.filter { it.job != null && it.events.isNotEmpty() }

/** One projection for report history and cash balances; explicit receipts are never counted twice. */
internal fun reconciledLegacyReceiptJobs(workspaceId: String, payments: List<PaymentEntity>, jobs: List<JobEntryEntity>): List<JobEntryEntity> {
    if (workspaceId.isBlank()) return emptyList()
    val history = collectionJobHistory(workspaceId, payments, jobs)
    val represented = history.filter { it.jobId != null }.associate { group ->
        group.jobId to group.events.filter { it.payment.status == "ACTIVE" }.sumOf { it.amount }
    }
    val ambiguous = history.filter { it.jobId == null }.flatMap { it.events }
        .map { it.payment.customerId }.filter { it > 0 }.toSet()
    return jobs.filter { it.workspaceId == workspaceId && it.amountReceived > 0 }.mapNotNull { job ->
        if (job.customerId in ambiguous) null else (job.amountReceived - (represented[job.id] ?: 0.0))
            .takeIf { it > 0.000001 }?.let { job.copy(amountReceived = it) }
    }
}

internal fun recordedLegacyPaymentMethod(notes: String): String = when {
    notes.contains("UPI", true) || notes.contains("GPay", true) -> "UPI / GPay"
    notes.contains("Bank", true) || notes.contains("Transfer", true) -> "Bank Transfer"
    notes.contains("Cheque", true) -> "Cheque"
    notes.contains("Cash", true) -> "Cash"
    else -> "Method not recorded"
}

/** Read-only presentation projection. No payment, allocation, or Entry balance is mutated.
 * Missing/invalid relationships stay separate rather than grouping unrelated customer payments.
 */
internal fun collectionJobHistory(
    workspaceId: String,
    payments: List<PaymentEntity>,
    jobs: List<JobEntryEntity>
): List<CollectionJobHistory> {
    if (workspaceId.isBlank()) return emptyList()
    val scopedJobs = jobs.filter { it.workspaceId == workspaceId }.associateBy { it.id }
    data class Projected(val jobId: Long?, val event: CollectionHistoryEvent, val invalid: Boolean)
    val projected = payments.filter { it.workspaceId == workspaceId }.distinctBy { it.id }.flatMap { payment ->
        val allocations = runCatching {
            val array = JSONArray(payment.allocationsJson)
            (0 until array.length()).map { index ->
                val row = array.getJSONObject(index)
                // Parse the decimal identifier directly, never through Double (IDs exceed 2^53).
                val id = row.get("jobId").toString().toLong()
                val amount = BigDecimal(row.get("amount").toString())
                require(id > 0 && amount > BigDecimal.ZERO)
                id to amount
            }.also { rows ->
                if (rows.isNotEmpty()) {
                    require(rows.map { it.first }.distinct().size == rows.size)
                    // Stored amounts are Doubles; tolerate representation noise, not missing money.
                    require((rows.fold(BigDecimal.ZERO) { sum, row -> sum + row.second } - BigDecimal.valueOf(payment.amount)).abs() <= BigDecimal("0.000001"))
                    require(payment.jobEntryId <= 0 || rows.any { it.first == payment.jobEntryId })
                }
            }
        }.getOrNull()
        when {
            allocations == null -> listOf(Projected(null, CollectionHistoryEvent(payment, payment.amount), true))
            allocations.isNotEmpty() -> allocations.map { (id, amount) -> Projected(id, CollectionHistoryEvent(payment, amount.toDouble()), false) }
            else -> listOf(Projected(payment.jobEntryId.takeIf { it > 0 }, CollectionHistoryEvent(payment, payment.amount), false))
        }
    }
    return projected.groupBy { it.jobId?.let { id -> "$workspaceId:job:$id" } ?: "$workspaceId:unlinked:${it.event.payment.id}" }
        .map { (key, rows) ->
            val id = rows.first().jobId
            CollectionJobHistory(key, id, id?.let(scopedJobs::get),
                rows.map { it.event }.sortedWith(compareBy({ it.payment.collectedAt }, { it.payment.id })), rows.any { it.invalid })
        }.sortedWith(compareByDescending<CollectionJobHistory> { it.events.maxOf { event -> event.payment.collectedAt } }.thenBy { it.key })
}
