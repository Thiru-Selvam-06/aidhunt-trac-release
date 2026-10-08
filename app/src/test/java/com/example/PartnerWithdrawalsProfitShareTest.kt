package com.example

import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.firebase.ProfitShareAllocation
import com.example.data.firebase.WorkspaceMember
import com.example.ui.util.FinancialCalculationEngine
import org.junit.Assert.*
import org.junit.Test

class PartnerWithdrawalsProfitShareTest {

    private fun createJob(received: Double): JobEntryEntity =
        JobEntryEntity(
            id = 1L,
            customerName = "Customer",
            operatorName = "Operator",
            tractorLabel = "Tractor 1",
            workType = "Ploughing",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            durationMinutes = 60L,
            totalAmount = received,
            amountReceived = received,
            pendingAmount = 0.0,
            addedByPartner = "Owner",
            createdAt = System.currentTimeMillis()
        )

    private fun createExpense(amount: Double, paidBy: String = "Cash", partnerName: String = ""): ExpenseEntity =
        ExpenseEntity(
            id = 1L,
            expenseType = "Diesel",
            amount = amount,
            tractorLabel = "Tractor 1",
            addedByPartner = partnerName,
            paidBy = paidBy,
            paidByPartner = partnerName,
            createdAt = System.currentTimeMillis()
        )

    @Test
    fun testCase1_Owner5Percent_Partner95Percent() {
        val jobs = listOf(createJob(10000.0))
        val expenses = emptyList<ExpenseEntity>()
        val withdrawals = emptyList<WithdrawalEntity>()

        val members = listOf(
            WorkspaceMember(uid = "owner_uid", role = "owner", displayName = "Owner of 89", status = "active"),
            WorkspaceMember(uid = "partner_uid", role = "partner", displayName = "Test Partner", status = "active")
        )

        val allocations = mapOf(
            "owner_uid" to 5,
            "partner_uid" to 95
        )

        val details = mapOf(
            "owner_uid" to ProfitShareAllocation("owner_uid", 5, "Owner of 89", "owner", true),
            "partner_uid" to ProfitShareAllocation("partner_uid", 95, "Test Partner", "partner", true)
        )

        val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = jobs,
            expenses = expenses,
            withdrawals = withdrawals,
            workspaceMembers = members,
            profitShareAllocations = allocations,
            profitShareAllocationDetails = details,
            businessName = "AIDHUNT Trac"
        )

        assertEquals(2, statuses.size)
        val ownerStatus = statuses.find { it.partnerUid == "owner_uid" }
        val partnerStatus = statuses.find { it.partnerUid == "partner_uid" }

        assertNotNull(ownerStatus)
        assertNotNull(partnerStatus)

        // Distributable Profit = 10,000
        // Owner 5% = 500, Partner 95% = 9,500
        assertEquals(500.0, ownerStatus!!.totalShare, 0.01)
        assertEquals(9500.0, partnerStatus!!.totalShare, 0.01)
        assertEquals(500.0, ownerStatus.totalToGet, 0.01)
        assertEquals(9500.0, partnerStatus.totalToGet, 0.01)

        // Verify Business Name never appears
        assertTrue(statuses.none { it.partnerName.equals("AIDHUNT Trac", ignoreCase = true) })
    }

    @Test
    fun testCase2_Partner100Percent_Owner0Percent() {
        val jobs = listOf(createJob(5000.0))
        val expenses = listOf(createExpense(amount = 1000.0, paidBy = "I Paid", partnerName = "Owner of 89"))

        val members = listOf(
            WorkspaceMember(uid = "owner_uid", role = "owner", displayName = "Owner of 89", status = "active"),
            WorkspaceMember(uid = "partner_uid", role = "partner", displayName = "Test Partner", status = "active")
        )

        val allocations = mapOf(
            "owner_uid" to 0,
            "partner_uid" to 100
        )

        val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = jobs,
            expenses = expenses,
            withdrawals = emptyList(),
            workspaceMembers = members,
            profitShareAllocations = allocations,
            businessName = "My Business"
        )

        val ownerStatus = statuses.find { it.partnerUid == "owner_uid" }
        val partnerStatus = statuses.find { it.partnerUid == "partner_uid" }

        assertNotNull(ownerStatus)
        assertNotNull(partnerStatus)

        // Distributable Profit = 5,000 - 1,000 = 4,000
        // Partner gets 100% = 4,000
        assertEquals(4000.0, partnerStatus!!.totalShare, 0.01)
        // Owner receives 0% share
        assertEquals(0.0, ownerStatus!!.totalShare, 0.01)
        // Owner investment remains 1,000
        assertEquals(1000.0, ownerStatus.totalInvestment, 0.01)
        assertEquals(1000.0, ownerStatus.totalToGet, 0.01)
    }

    @Test
    fun testCase3_ThreeParticipants_EqualShare_RoundingReconciliation() {
        val jobs = listOf(createJob(5000.0)) // Distributable = 5000

        val members = listOf(
            WorkspaceMember(uid = "owner_uid", role = "owner", displayName = "Owner", status = "active"),
            WorkspaceMember(uid = "p1_uid", role = "partner", displayName = "Partner A", status = "active"),
            WorkspaceMember(uid = "p2_uid", role = "partner", displayName = "Partner B", status = "active")
        )

        // Equal Share configuration for 3 participants: 34%, 33%, 33%
        val allocations = mapOf(
            "owner_uid" to 34,
            "p1_uid" to 33,
            "p2_uid" to 33
        )

        val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = jobs,
            expenses = emptyList(),
            withdrawals = emptyList(),
            workspaceMembers = members,
            profitShareAllocations = allocations
        )

        assertEquals(3, statuses.size)
        val totalDistributed = statuses.sumOf { it.totalShare }
        // Verify total distributed amount reconciles exactly to distributable amount
        assertEquals(5000.0, totalDistributed, 0.001)

        val owner = statuses.find { it.partnerUid == "owner_uid" }!!
        val p1 = statuses.find { it.partnerUid == "p1_uid" }!!
        val p2 = statuses.find { it.partnerUid == "p2_uid" }!!

        assertEquals(1700.0, owner.totalShare, 0.01)
        assertEquals(1650.0, p1.totalShare, 0.01)
        assertEquals(1650.0, p2.totalShare, 0.01)
    }

    @Test
    fun testCase4_FourPartners_SelectiveAllocation() {
        val jobs = listOf(createJob(10000.0))

        val members = listOf(
            WorkspaceMember(uid = "owner_uid", role = "owner", displayName = "Owner", status = "active"),
            WorkspaceMember(uid = "pA_uid", role = "partner", displayName = "Partner A", status = "active"),
            WorkspaceMember(uid = "pB_uid", role = "partner", displayName = "Partner B", status = "active"),
            WorkspaceMember(uid = "pC_uid", role = "partner", displayName = "Partner C", status = "active"),
            WorkspaceMember(uid = "pD_uid", role = "partner", displayName = "Partner D", status = "active")
        )

        // Owner excludes Partner B and themselves. Allocates: A = 50%, C = 25%, D = 25%
        val allocations = mapOf(
            "owner_uid" to 0,
            "pA_uid" to 50,
            "pB_uid" to 0,
            "pC_uid" to 25,
            "pD_uid" to 25
        )

        val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = jobs,
            expenses = emptyList(),
            withdrawals = emptyList(),
            workspaceMembers = members,
            profitShareAllocations = allocations
        )

        val owner = statuses.find { it.partnerUid == "owner_uid" }!!
        val pA = statuses.find { it.partnerUid == "pA_uid" }!!
        val pB = statuses.find { it.partnerUid == "pB_uid" }!!
        val pC = statuses.find { it.partnerUid == "pC_uid" }!!
        val pD = statuses.find { it.partnerUid == "pD_uid" }!!

        assertEquals(0.0, owner.totalShare, 0.01)
        assertEquals(0.0, pB.totalShare, 0.01)
        assertEquals(5000.0, pA.totalShare, 0.01)
        assertEquals(2500.0, pC.totalShare, 0.01)
        assertEquals(2500.0, pD.totalShare, 0.01)

        val totalDistributed = statuses.sumOf { it.totalShare }
        assertEquals(10000.0, totalDistributed, 0.001)
    }

    @Test
    fun testBusinessNameAndOperatorStrictExclusion() {
        val jobs = listOf(createJob(6000.0))

        val members = listOf(
            WorkspaceMember(uid = "owner_uid", role = "owner", displayName = "Owner of 89", status = "active"),
            WorkspaceMember(uid = "partner_uid", role = "partner", displayName = "Test Partner", status = "active"),
            WorkspaceMember(uid = "operator_uid", role = "operator", displayName = "Operator Mike", status = "active"),
            WorkspaceMember(uid = "biz_uid", role = "partner", displayName = "Tractor Company Ltd", status = "active")
        )

        val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = jobs,
            expenses = emptyList(),
            withdrawals = emptyList(),
            workspaceMembers = members,
            businessName = "Tractor Company Ltd"
        )

        // Only Owner and Test Partner should be participants
        assertEquals(2, statuses.size)
        assertTrue(statuses.any { it.partnerName == "Owner of 89" })
        assertTrue(statuses.any { it.partnerName == "Test Partner" })
        assertFalse(statuses.any { it.partnerName == "Operator Mike" })
        assertFalse(statuses.any { it.partnerName == "Tractor Company Ltd" })
    }

    @Test
    fun testCase8_AllocationUpdateReflectedImmediately() {
        val jobs = listOf(createJob(10000.0))

        val members = listOf(
            WorkspaceMember(uid = "owner_uid", role = "owner", displayName = "Owner of 89", status = "active"),
            WorkspaceMember(uid = "partner_uid", role = "partner", displayName = "Test Partner", status = "active")
        )

        // 1. Initial 50/50
        val initialAlloc = mapOf("owner_uid" to 50, "partner_uid" to 50)
        val initialStatuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = jobs,
            expenses = emptyList(),
            withdrawals = emptyList(),
            workspaceMembers = members,
            profitShareAllocations = initialAlloc
        )
        assertEquals(5000.0, initialStatuses.find { it.partnerUid == "owner_uid" }!!.totalShare, 0.01)
        assertEquals(5000.0, initialStatuses.find { it.partnerUid == "partner_uid" }!!.totalShare, 0.01)

        // 2. Updated to 5/95
        val updatedAlloc = mapOf("owner_uid" to 5, "partner_uid" to 95)
        val updatedStatuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = jobs,
            expenses = emptyList(),
            withdrawals = emptyList(),
            workspaceMembers = members,
            profitShareAllocations = updatedAlloc
        )
        assertEquals(500.0, updatedStatuses.find { it.partnerUid == "owner_uid" }!!.totalShare, 0.01)
        assertEquals(9500.0, updatedStatuses.find { it.partnerUid == "partner_uid" }!!.totalShare, 0.01)
    }
}
