package com.example.ui.util

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Locale

/** A paisa can belong to only one member; assigning it never changes the equal percentage. */
fun equalMoneyShares(amount: Double, memberUids: Collection<String>): Map<String, Double> {
    val ids = memberUids.filter { it.isNotBlank() }.distinct().sorted()
    if (ids.isEmpty()) return emptyMap()
    val safeAmount = amount.takeIf { it.isFinite() && it > 0 } ?: 0.0
    val paisa = BigDecimal.valueOf(safeAmount).setScale(2, RoundingMode.HALF_UP)
        .movePointRight(2).toBigIntegerExact()
    val (base, remainder) = paisa.divideAndRemainder(BigInteger.valueOf(ids.size.toLong()))
    return ids.mapIndexed { index, uid ->
        val units = base + if (index < remainder.toInt()) BigInteger.ONE else BigInteger.ZERO
        uid to BigDecimal(units).movePointLeft(2).toDouble()
    }.toMap()
}

/** Allocate the actual paisa once; independently rounding many percentage shares can overspend it. */
fun percentageMoneyShares(amount: Double, percentages: Map<String, Int>): Map<String, Double> {
    require(percentages.isNotEmpty() && percentages.keys.all { it.isNotBlank() } &&
        percentages.values.all { it in 0..100 } && percentages.values.sum() == 100) { "A complete percentage allocation is required" }
    val paisa = BigDecimal.valueOf(amount.takeIf { it.isFinite() && it > 0 } ?: 0.0)
        .setScale(2, RoundingMode.HALF_UP).movePointRight(2).toBigIntegerExact()
    val divisor = BigInteger.valueOf(100)
    val portions = percentages.mapValues { (_, weight) -> paisa.multiply(BigInteger.valueOf(weight.toLong())).divideAndRemainder(divisor) }
    val undistributed = paisa - portions.values.fold(BigInteger.ZERO) { sum, part -> sum + part[0] }
    // Each recipient loses less than one paisa to flooring, so this count fits in Int.
    // Check before using the conversion supported on the app's older Android versions.
    check(undistributed.signum() >= 0 && undistributed < BigInteger.valueOf(portions.size.toLong()))
    val remaining = undistributed.toInt()
    val extra = portions.entries.sortedWith(compareByDescending<Map.Entry<String, Array<BigInteger>>> { it.value[1] }
        .thenBy { it.key }).take(remaining).map { it.key }.toSet()
    return portions.mapValues { (uid, part) -> BigDecimal(part[0] + if (uid in extra) BigInteger.ONE else BigInteger.ZERO)
        .movePointLeft(2).toDouble() }
}

/** Older equal-share presets used integer 34/33/33 solely to make percentages add to 100. */
fun isEqualShareAllocation(memberUids: Set<String>, allocation: Map<String, Int>): Boolean {
    if (memberUids.isEmpty()) return false
    if (allocation.isEmpty()) return true
    if (allocation.keys != memberUids || allocation.values.sum() != 100) return false
    val base = 100 / memberUids.size
    return allocation.values.all { it == base || it == base + 1 }
}

fun formatSharePercentage(value: Double): String =
    String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.') + "%"
