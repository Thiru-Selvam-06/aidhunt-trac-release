package com.example.data.auth

object EntryFinancialMutationPolicy {
    fun requireBalancedAmounts(total: Double, received: Double, due: Double) {
        require(listOf(total, received, due).all { it.isFinite() && it >= 0.0 } &&
            received <= total && kotlin.math.abs(total - received - due) < 0.000001) {
            "Job amount cannot be below confirmed receipts; correct Collections separately"
        }
    }
    fun requireAuthorized(role: String, previousReceived: Double?, received: Double) {
        if (previousReceived != null && kotlin.math.abs(received - previousReceived) > 0.001)
            throw SecurityException("Received amounts must be changed through Collections")
        if (previousReceived == null && RoleUtils.normalizeRole(role) == RoleUtils.ROLE_OPERATOR && received > 0)
            throw SecurityException("Operators cannot record collections")
    }
}
