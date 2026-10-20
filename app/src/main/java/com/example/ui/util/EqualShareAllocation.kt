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
