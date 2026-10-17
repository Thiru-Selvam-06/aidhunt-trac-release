package com.example.data.auth

object EntryFinancialMutationPolicy {
    fun requireAuthorized(role: String, previousReceived: Double?, received: Double) {
        if (previousReceived != null && kotlin.math.abs(received - previousReceived) > 0.001)
            throw SecurityException("Received amounts must be changed through Collections")
        if (previousReceived == null && RoleUtils.normalizeRole(role) == RoleUtils.ROLE_OPERATOR && received > 0)
            throw SecurityException("Operators cannot record collections")
    }
}
