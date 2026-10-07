package com.example

import com.example.data.entity.JobEntryEntity
import com.example.data.firebase.WorkspaceMember
import com.example.ui.util.FinancialCalculationEngine
import com.example.ui.util.FinancialScope
import com.example.ui.util.FinancialScopeMode
import org.junit.Test
import org.junit.Assert.*

class PartnerEarningsChartTest {

    @Test
    fun testPartnerEarningsChartAttribution() {
        val ownerUid = "owner_uid"
        val partnerUid1 = "partner_uid_1"
        val partnerUid2 = "partner_uid_2"

        val workspaceMembers = listOf(
            WorkspaceMember(uid = ownerUid, role = "Owner", status = "active", displayName = "Owner"),
            WorkspaceMember(uid = partnerUid1, role = "Partner", status = "active", displayName = "Partner1"),
            WorkspaceMember(uid = partnerUid2, role = "Partner", status = "active", displayName = "Partner2")
        )

        val job1 = JobEntryEntity(id = 1, createdByUid = ownerUid, amountReceived = 1000.0, customerName = "C1", operatorName = "O1", tractorLabel = "T1", workType = "W1", startTimeMillis = 1000L, endTimeMillis = 2000L, durationMinutes = 60, totalAmount = 1000.0, pendingAmount = 0.0, addedByPartner = "O1")
        val job2 = JobEntryEntity(id = 2, createdByUid = partnerUid1, amountReceived = 500.0, customerName = "C2", operatorName = "O2", tractorLabel = "T2", workType = "W2", startTimeMillis = 1000L, endTimeMillis = 2000L, durationMinutes = 60, totalAmount = 500.0, pendingAmount = 0.0, addedByPartner = "O2")
        val job3 = JobEntryEntity(id = 3, createdByUid = partnerUid2, amountReceived = 300.0, customerName = "C3", operatorName = "O3", tractorLabel = "T3", workType = "W3", startTimeMillis = 1000L, endTimeMillis = 2000L, durationMinutes = 60, totalAmount = 300.0, pendingAmount = 0.0, addedByPartner = "O3")
        val job4 = JobEntryEntity(id = 4, createdByUid = partnerUid1, amountReceived = 200.0, customerName = "C4", operatorName = "O4", tractorLabel = "T4", workType = "W4", startTimeMillis = 1000L, endTimeMillis = 2000L, durationMinutes = 60, totalAmount = 200.0, pendingAmount = 0.0, addedByPartner = "O4")

        val jobs = listOf(job1, job2, job3, job4)
        
        val breakdowns = FinancialCalculationEngine.calculatePartnerBreakdown(
            jobs = jobs,
            expenses = emptyList(),
            withdrawals = emptyList(),
            partners = emptyList(),
            workspaceMembers = workspaceMembers,
            scope = FinancialScope(FinancialScopeMode.OVERALL)
        )

        assertEquals("Should have 3 participants", 3, breakdowns.size)
        assertTrue(breakdowns.any { it.partnerUid == ownerUid })
        assertTrue(breakdowns.any { it.partnerUid == partnerUid1 && it.received == 700.0 }) // 500 + 200
        assertTrue(breakdowns.any { it.partnerUid == partnerUid2 && it.received == 300.0 })
    }
}
