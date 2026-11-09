package com.example.data.sync

import com.example.data.entity.JobEntryEntity
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode

data class PlannedCollectionAmount(val job: JobEntryEntity, val amount: Double,
    val amountReceived: Double, val pendingAmount: Double)

class SavedCollectionAmountsNeedReview : IllegalArgumentException(
    "Review the saved job amounts before collecting this payment")

private fun paisa(value: Double): BigInteger {
    if (!value.isFinite() || value < 0) throw SavedCollectionAmountsNeedReview()
    val rounded = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP)
    if (kotlin.math.abs(value - rounded.toDouble()) > 0.000001) throw SavedCollectionAmountsNeedReview()
    return rounded.movePointRight(2).toBigIntegerExact()
}

private fun money(value: BigInteger) = BigDecimal(value).movePointLeft(2).toDouble()

private fun JobEntryEntity.collectionBalanceInPaisa(): Pair<BigInteger, BigInteger> {
    val received = paisa(amountReceived)
    val due = paisa(pendingAmount)
    if (received + due != paisa(totalAmount)) throw SavedCollectionAmountsNeedReview()
    return received to due
}

/** Plan the complete payment in integer paisa before any Room row or command changes. */
fun planCollectionAmounts(jobs: List<JobEntryEntity>, workspaceId: String, customerId: Long,
    amount: Double): List<PlannedCollectionAmount> {
    var remaining = paisa(amount)
    require(remaining > BigInteger.ZERO) { "Collection amount must be positive" }
    val result = mutableListOf<PlannedCollectionAmount>()
    for (job in jobs.filter { it.workspaceId == workspaceId && it.customerId == customerId && it.pendingAmount > 0 }
        .sortedWith(compareBy<JobEntryEntity> { it.startTimeMillis }.thenBy { it.id })) {
        if (remaining == BigInteger.ZERO) break
        val (received, due) = job.collectionBalanceInPaisa()
        if (due == BigInteger.ZERO) continue
        val allocated = remaining.min(due)
        result.add(PlannedCollectionAmount(job, money(allocated), money(received + allocated), money(due - allocated)))
        remaining -= allocated
    }
    require(remaining == BigInteger.ZERO) { "Collection exceeds the outstanding job dues" }
    return result
}

/** A reversal reuses the original receipt amount and exact source balances. */
fun planCollectionReversal(job: JobEntryEntity, amount: Double): PlannedCollectionAmount {
    val (received, due) = job.collectionBalanceInPaisa()
    val reversed = paisa(amount)
    require(reversed > BigInteger.ZERO && reversed <= received) { "Invalid collection amount for reversal" }
    return PlannedCollectionAmount(job, amount, money(received - reversed), money(due + reversed))
}
