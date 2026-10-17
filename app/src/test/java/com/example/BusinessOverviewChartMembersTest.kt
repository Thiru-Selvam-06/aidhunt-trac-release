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
        assertEquals("PARTNER", result.first().role)
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
}
