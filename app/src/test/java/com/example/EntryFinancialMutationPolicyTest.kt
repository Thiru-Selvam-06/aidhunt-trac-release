package com.example

import com.example.data.auth.EntryFinancialMutationPolicy
import org.junit.Assert.assertThrows
import org.junit.Test

class EntryFinancialMutationPolicyTest {
    @Test fun operatorEditPreservesExistingOwnerOrPartnerCollection() {
        EntryFinancialMutationPolicy.requireAuthorized("OPERATOR", 20000.0, 20000.0)
        EntryFinancialMutationPolicy.requireAuthorized("OPERATOR", null, 0.0)
    }
    @Test fun entryEditsCannotCreateOrChangeCollections() {
        assertThrows(SecurityException::class.java) { EntryFinancialMutationPolicy.requireAuthorized("OPERATOR", null, 20000.0) }
        for (role in listOf("OWNER", "PARTNER", "OPERATOR")) {
            assertThrows(SecurityException::class.java) { EntryFinancialMutationPolicy.requireAuthorized(role, 20000.0, 30000.0) }
            assertThrows(SecurityException::class.java) { EntryFinancialMutationPolicy.requireAuthorized(role, 20000.0, 0.0) }
        }
    }
}
