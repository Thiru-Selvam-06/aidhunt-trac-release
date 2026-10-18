package com.example

import com.example.data.auth.RecordIdentityPolicy
import com.example.data.auth.AuthorizationManager
import com.example.data.auth.AccessControlDocument
import com.example.data.auth.AccountStatus
import com.example.data.firebase.WorkspaceMember
import org.junit.Assert.*
import org.junit.Test

class RecordIdentityPolicyTest {
    private val owner = WorkspaceMember(uid = "owner", workspaceId = "a", role = "OWNER", status = "ACTIVE")
    private val partner = WorkspaceMember(uid = "partner", workspaceId = "a", role = "PARTNER", status = "active")

    @Test fun creatorAndBeneficiaryMayBeDifferentActiveMembers() {
        RecordIdentityPolicy.requireActive("a", "owner", "partner", listOf(owner, partner))
    }
    @Test fun matchingIdentityInAnotherWorkspaceCannotAuthorizeARecord() {
        val error = assertThrows(SecurityException::class.java) {
            RecordIdentityPolicy.requireActive("a", "owner", "partner", listOf(owner, partner.copy(workspaceId = "b")))
        }
        assertEquals(RecordIdentityPolicy.MEMBERSHIP_REQUIRED, error.message)
    }
    @Test fun eitherMissingIdentityRequiresRepairEvenForOwner() {
        for ((creator, target) in listOf("" to "owner", "owner" to "", "" to "")) {
            val error = assertThrows(SecurityException::class.java) {
                RecordIdentityPolicy.requireActive("a", creator, target, listOf(owner))
            }
            assertEquals(RecordIdentityPolicy.repairMessage(creator, target), error.message)
        }
    }
    @Test fun removedMemberIsNotMadeActiveByAnActiveCopyFromAnotherWorkspace() {
        assertThrows(SecurityException::class.java) {
            RecordIdentityPolicy.requireActive("a", "owner", "partner",
                listOf(owner, partner.copy(status = "REMOVED"), partner.copy(workspaceId = "b")))
        }
    }
    @Test fun missingWorkspaceNeverFallsBackToMemberWorkspace() {
        assertThrows(IllegalArgumentException::class.java) {
            RecordIdentityPolicy.requireActive("", "owner", "owner", listOf(owner))
        }
    }
    @Test fun legacySuspensionCacheDoesNotEnableTheRemovedSubscriptionGate() {
        val previous = AuthorizationManager.currentAccessControl
        try {
            AuthorizationManager.currentAccessControl = AccessControlDocument(accountStatus = "SUSPENDED", validUntil = 1L)
            assertFalse(AuthorizationManager.isAccountBlocked())
            assertEquals(AccountStatus.ACTIVE, AuthorizationManager.getAccountStatus())
            // Account validity is independent from actual membership authorization.
            assertThrows(SecurityException::class.java) {
                RecordIdentityPolicy.requireActive("a", "owner", "owner", emptyList())
            }
        } finally { AuthorizationManager.currentAccessControl = previous }
    }
    @Test fun knownCreatorAndMissingBeneficiaryAreReportedSeparately() {
        assertEquals(listOf("beneficiary"), RecordIdentityPolicy.missingIdentityFields("owner", ""))
        assertTrue(RecordIdentityPolicy.repairMessage("owner", "")!!.startsWith("Missing beneficiary identity."))
        assertNull(RecordIdentityPolicy.repairMessage("owner", "owner"))
    }
    @Test fun displayedOwnerNameDoesNotBecomeVerifiedCreatorIdentity() {
        assertEquals("Owner (creator identity missing)", RecordIdentityPolicy.savedCreatorLabel("Owner", ""))
        assertEquals("Owner", RecordIdentityPolicy.savedCreatorLabel("Owner", "owner-uid"))
        assertEquals("Name not recorded", RecordIdentityPolicy.savedCreatorLabel("", "owner-uid"))
    }

}
