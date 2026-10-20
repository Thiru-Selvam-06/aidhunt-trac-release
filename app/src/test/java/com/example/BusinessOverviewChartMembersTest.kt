package com.example

import com.example.data.firebase.WorkspaceMember
import com.example.ui.util.PartnerFinancialBreakdown
import com.example.ui.util.businessOverviewChartMembers
import org.junit.Assert.*
import org.junit.Test

class BusinessOverviewChartMembersTest {
    private val owner = WorkspaceMember(workspaceId = "test", uid = "owner", role = "OWNER", status = "ACTIVE", displayName = "Owner Name")
    private val partner = WorkspaceMember(workspaceId = "test", uid = "partner", role = "PARTNER", status = "ACTIVE", displayName = "Partner Name")

    @Test fun ownerWithoutActivityStillHasOwnCircleWithoutChangingOtherAmounts() {
        val other = PartnerFinancialBreakdown(partnerUid = "partner", partnerName = "Partner Name", recorded = 100.0, due = 100.0)
        val result = businessOverviewChartMembers(listOf(other), listOf(owner, partner), "owner", "test")
        assertEquals(listOf("owner", "partner"), result.map { it.partnerUid })
        assertEquals("Owner Name", result.first().partnerName)
        assertEquals("Owner", result.first().role)
        assertEquals(0.0, result.first().recorded, 0.0)
        assertSame(other, result.last())
    }

    @Test fun partnerWithoutActivityGetsOwnCircleEvenWhenOtherMembersHaveRecords() {
        val other = PartnerFinancialBreakdown(partnerUid = "owner", partnerName = "Owner Name", recorded = 200.0)
        val result = businessOverviewChartMembers(listOf(other), listOf(owner, partner), "partner", "test")
        assertEquals("partner", result.first().partnerUid)
        assertEquals("Partner Name", result.first().partnerName)
        assertEquals("Partner", result.first().role)
        assertEquals(0.0, result.first().due, 0.0)
        assertSame(other, result.last())
    }

    @Test fun existingSelfIsVisibleFirstWithExactFinancialValuesAndNoDuplicate() {
        val own = PartnerFinancialBreakdown(partnerUid = "partner", partnerName = "Partner Name", recorded = 250.0, received = 150.0, due = 100.0, expenses = 30.0)
        val other = PartnerFinancialBreakdown(partnerUid = "owner", partnerName = "Owner Name", recorded = 500.0)
        val result = businessOverviewChartMembers(listOf(other, own), listOf(owner, partner), "partner", "test")
        assertSame(own, result.first())
        assertEquals(1, result.count { it.partnerUid == "partner" })
        assertEquals(listOf(other), result.drop(1))
    }

    @Test fun missingRemovedOrForeignMembershipCannotCreateSelfCircle() {
        val empty = emptyList<PartnerFinancialBreakdown>()
        for (members in listOf(emptyList(), listOf(partner.copy(status = "REMOVED")), listOf(partner.copy(workspaceId = "other")))) {
            assertSame(empty, businessOverviewChartMembers(empty, members, "partner", "test"))
        }
    }
    @Test fun ownerSeesEveryIdlePartnerWithZeroAmounts() {
        val second = partner.copy(uid = "partner-two", displayName = "Partner Two")
        val own = PartnerFinancialBreakdown(partnerUid = "owner", partnerName = "Owner Name", recorded = 100.0, received = 40.0, due = 60.0)
        val result = businessOverviewChartMembers(listOf(own), listOf(owner, partner, second), "owner", "test")
        assertEquals(listOf("owner", "partner", "partner-two"), result.map { it.partnerUid })
        assertSame(own, result.first())
        result.drop(1).forEach { assertEquals(0.0, it.recorded, 0.0); assertEquals(0.0, it.received, 0.0); assertEquals(0.0, it.due, 0.0) }
    }
    @Test fun removedForeignAndOperatorMembersCannotBeAddedAsIdleFinancialCircles() {
        val excluded = listOf(partner.copy(uid = "removed", status = "REMOVED"), partner.copy(uid = "foreign", workspaceId = "b"), partner.copy(uid = "operator", role = "OPERATOR"))
        val result = businessOverviewChartMembers(emptyList(), listOf(owner, partner) + excluded, "owner", "test")
        assertEquals(listOf("owner", "partner"), result.map { it.partnerUid })
    }
    @Test fun zeroParticipantsDoNotAbsorbUnattributedFinancialRecords() {
        val unknown = PartnerFinancialBreakdown(partnerUid = "", partnerName = "Attribution unavailable", recorded = 90.0, due = 90.0)
        val result = businessOverviewChartMembers(listOf(unknown), listOf(owner, partner, partner), "owner", "test")
        // Approved chart policy: only known members, with no invented attribution or source mutation.
        assertEquals(listOf("owner", "partner"), result.map { it.partnerUid })
        assertTrue(result.none { it.partnerUid.isBlank() })
        assertEquals(0.0, result.sumOf { it.recorded }, 0.0)
        assertEquals(90.0, unknown.recorded, 0.0)
        assertEquals(90.0, unknown.due, 0.0)
        assertEquals(0.0, result.first { it.partnerUid == "partner" }.due, 0.0)
    }

}
