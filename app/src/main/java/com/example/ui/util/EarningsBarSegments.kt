package com.example.ui.util

/** Presentation fractions only. Never changes the amounts supplied by the financial engine. */
internal fun earningsBarSegments(total: Double, due: Double, collected: Double): List<Float> {
    if (!total.isFinite() || total <= 0.0) return listOf(0f, 0f, 0f)
    fun fraction(value: Double) = if (value.isFinite() && value > 0.0) (value / total).coerceAtMost(1.0) else 0.0
    val dueFraction = fraction(due)
    val collectedFraction = fraction(collected)
    val sum = dueFraction + collectedFraction
    // Inconsistent/overlapping inputs are bounded for drawing only, not written back.
    val scale = maxOf(1.0, sum)
    val first = (dueFraction / scale).toFloat()
    val second = (collectedFraction / scale).toFloat().coerceAtMost(1f - first)
    return listOf(first, second, (1f - first - second).coerceAtLeast(0f))
}
