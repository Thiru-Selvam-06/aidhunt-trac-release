package com.example

import com.example.data.auth.canCleanupPendingInvitation
import com.example.data.firebase.WorkspaceMember
import com.example.ui.util.earningsBarSegments
import org.junit.Assert.*
import org.junit.Test

class FinalTwoClientFixesTest {
    private val pending = WorkspaceMember(pendingWorkspaceId = "w1", role = "Operator",
        status = "waiting_for_registration", addedByUid = "p1")

    @Test fun ownerCanCleanFailedPartnerEvenWhenCreatedByAnotherActor() {
        assertTrue(canCleanupPendingInvitation(pending.copy(role = "Partner"), "w1", "owner", "Owner"))
        assertTrue(canCleanupPendingInvitation(pending.copy(addedByUid = null), "w1", "owner", "Owner"))
    }
    @Test fun creatingPartnerCanCleanOnlyTheirFailedOperator() {
        assertTrue(canCleanupPendingInvitation(pending, "w1", "p1", "Partner"))
        assertFalse(canCleanupPendingInvitation(pending, "w1", "p2", "Partner"))
        assertFalse(canCleanupPendingInvitation(pending.copy(role = "Partner"), "w1", "p1", "Partner"))
        assertFalse(canCleanupPendingInvitation(pending.copy(addedByUid = null), "w1", "p1", "Partner"))
    }
    @Test fun activeAndUidBoundRecordsAreNeverPendingCleanupTargets() {
        for (role in listOf("Owner", "Partner")) {
            assertFalse(canCleanupPendingInvitation(pending.copy(status = "active"), "w1", "p1", role))
            assertFalse(canCleanupPendingInvitation(pending.copy(uid = "registered"), "w1", "p1", role))
        }
    }
    @Test fun otherWorkspaceAndMissingProvenanceDeniedEvenToOwner() {
        assertFalse(canCleanupPendingInvitation(pending, "w2", "p1", "Owner"))
        assertFalse(canCleanupPendingInvitation(pending.copy(pendingWorkspaceId = ""), "w1", "p1", "Owner"))
        assertFalse(canCleanupPendingInvitation(pending, "w1", "", "Owner"))
        assertFalse(canCleanupPendingInvitation(pending, "w1", "p1", "Operator"))
    }
    @Test fun percentageWidthsUseActualTotal() {
        val parts = earningsBarSegments(144985.0, 144985.0 * .25, 144985.0 * .10)
        assertEquals(.25f, parts[0], .00001f)
        assertEquals(.10f, parts[1], .00001f)
        assertEquals(.65f, parts[2], .00001f)
        assertEquals(1f, parts.sum(), .00001f)
    }
    @Test fun zeroAndInvalidTotalsNeverDivideOrRenderInvalidWeights() {
        for (total in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY))
            assertEquals(listOf(0f, 0f, 0f), earningsBarSegments(total, 25.0, 10.0))
    }
    @Test fun invalidAmountsAndOverAllocationRemainBoundedForDrawing() {
        for ((due, paid) in listOf(-1.0 to 10.0, Double.NaN to Double.POSITIVE_INFINITY, 300.0 to 400.0, 1.0 to 2.0)) {
            val parts = earningsBarSegments(3.0, due, paid)
            assertTrue(parts.all { it.isFinite() && it in 0f..1f })
            assertEquals(1f, parts.sum(), .00001f)
        }
    }
}
