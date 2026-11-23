package com.example

import com.example.data.firebase.BalanceDisplayPolicy
import com.example.data.auth.AuthorizationManager
import org.junit.Assert.*
import org.junit.Test

class BalanceDisplayPolicyTest {
    @Test fun exactSignedPaisePreservesDecimalInputAndRejectsSilentRounding() {
        assertEquals(128667L, BalanceDisplayPolicy.parsePaise("1286.67"))
        assertEquals(-1L, BalanceDisplayPolicy.parsePaise("-0.01"))
        assertEquals("5000.50", BalanceDisplayPolicy.amount(500050))
        for (value in listOf("1.001", "NaN", "Infinity", "100000000000.00", ""))
            assertTrue(value, runCatching { BalanceDisplayPolicy.parsePaise(value) }.isFailure)
    }
    @Test fun reasonAndFinancialRoleAreRequiredWithoutChangingOwnerOnlyDefaults() {
        assertEquals("Cash count", BalanceDisplayPolicy.reason("  Cash count  "))
        assertTrue(runCatching { BalanceDisplayPolicy.reason(" \n ") }.isFailure)
        assertTrue(runCatching { BalanceDisplayPolicy.reason("x".repeat(501)) }.isFailure)
        assertTrue(AuthorizationManager.canDeleteCustomer(false, "Partner"))
        assertTrue(AuthorizationManager.canDeleteCustomer(true, "owner"))
        assertFalse(AuthorizationManager.canDeleteCustomer(false, "Operator"))
        assertFalse(AuthorizationManager.canDeleteCustomer(true, "Operator"))
        assertFalse(AuthorizationManager.canDeleteCustomer(false))
    }
}
