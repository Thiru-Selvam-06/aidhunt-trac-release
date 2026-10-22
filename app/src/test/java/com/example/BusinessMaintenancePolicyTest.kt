package com.example

import com.example.data.auth.CollectionPolicy
import com.example.data.auth.RoleUtils
import com.example.data.entity.*
import com.example.data.firebase.WorkspaceMember
import com.example.ui.screens.report.customerDuesForPartner
import com.example.ui.screens.report.duesFilterPartners
import com.example.ui.util.*
import org.junit.Assert.*
import org.junit.Test

class BusinessMaintenancePolicyTest {
    private val ws = "synthetic-business"
    private val members = listOf("owner", "partner-a", "partner-b").map { uid ->
        WorkspaceMember(uid = uid, workspaceId = ws, displayName = "Same name", status = "ACTIVE",
            role = if (uid == "owner") "OWNER" else "PARTNER")
    }
    private fun job(id: Long, target: String, total: Double, received: Double = 0.0) = JobEntryEntity(
        id = id, workspaceId = ws, customerId = 10, customerName = "Raman", customerPhone = "",
        operatorName = "Saved name", tractorLabel = "Tractor", workType = "Rotavator", startTimeMillis = 1000,
        endTimeMillis = 2000, durationMinutes = 1, totalAmount = total, amountReceived = received,
        pendingAmount = total - received, createdAt = 1000, addedByPartner = "Recorder",
        createdByUid = "owner", createdByRole = "OWNER", entryForUid = target, financialAttributionUid = target)
    private fun expense(amount: Double, payer: String, uid: String = "owner", id: Long = 1) = ExpenseEntity(
        id = id, workspaceId = ws, expenseType = "Diesel", amount = amount, tractorLabel = "Tractor", addedByPartner = "Recorder",
        paidBy = payer, paidByUid = if (payer == "I Paid") uid else "", createdByUid = "owner",
        createdByRole = "OWNER", expenseForUid = uid, financialAttributionUid = uid, createdAt = 1000, dateTimestamp = 1000)
    private val scope get() = FinancialScope(workspaceId = ws)

    @Test fun rolePresentationNeverChangesAuthorizationAndOnlyActiveExactWorkspacePartnerStartsTransition() {
        val owner = members.first()
        val operator = owner.copy(uid = "operator", role = "OPERATOR")
        assertFalse(hasActiveBusinessPartner(ws, listOf(owner, operator)))
        assertEquals("Owner", businessDisplayRole(owner.role, false))
        assertTrue(hasActiveBusinessPartner(ws, members))
        assertEquals("Partner", businessDisplayRole(owner.role, true))
        assertEquals("Operator", businessDisplayRole(operator.role, true))
        for (status in listOf("REMOVED", "INACTIVE", "SUSPENDED"))
            assertFalse(hasActiveBusinessPartner(ws, listOf(owner, members[1].copy(status = status))))
        assertFalse(hasActiveBusinessPartner(ws, members.map { it.copy(workspaceId = "foreign") }))
        assertFalse(hasActiveBusinessPartner(ws, listOf(members[1].copy(uid = ""))))
        assertEquals("Partner", businessDisplayRole(owner.role, true)) // Previously observed partnership stays visible.
        assertTrue(RoleUtils.isOwner(owner.role))
        assertEquals("OWNER", owner.role)
    }

    @Test fun partnerDuesFilterScopesJobsBeforeGroupingAndNeverUsesCreatorOrNames() {
        val customers = listOf(CustomerEntity(id = 10, workspaceId = ws, name = "Raman", phone = ""))
        val jobs = listOf(job(1, "partner-a", 500.0, 200.0), job(2, "partner-b", 800.0, 100.0))
        assertEquals(300.0, customerDuesForPartner(ws, customers, jobs, "partner-a").single().balanceDue, 0.0)
        assertEquals(700.0, customerDuesForPartner(ws, customers, jobs, "partner-b").single().balanceDue, 0.0)
        assertEquals(1000.0, customerDuesForPartner(ws, customers, jobs, null).single().balanceDue, 0.0)
        assertTrue(customerDuesForPartner(ws, customers, jobs, "owner").isEmpty())
        val foreign = job(3, "partner-a", 50000.0).copy(workspaceId = "foreign")
        assertEquals(300.0, customerDuesForPartner(ws, customers, jobs + foreign, "partner-a").single().balanceDue, 0.0)
        assertEquals(3, duesFilterPartners(ws, members + members + members[1].copy(uid = "removed", status = "REMOVED") +
            members[1].copy(uid = "operator", role = "OPERATOR")).size)
    }

    @Test fun entryForDisplayResolvesExactWorkspaceBeneficiaryNotCreatorCollectorOrDuplicateNames() {
        val labels = members.map { it.copy(displayName = it.uid) }
        val source = job(1, "partner-b", 500.0)
        assertEquals("Entry For: Partner — partner-b", RoleUtils.getEntryForDisplay(source, workspaceMembers = labels))
        assertEquals("owner", source.createdByUid)
        assertEquals("partner-b", source.entryForUid)
        assertNull(RoleUtils.getEntryForDisplay(source.copy(entryForUid = ""), workspaceMembers = labels))
        assertFalse(RoleUtils.getEntryForDisplay(source, workspaceMembers = labels.map { it.copy(workspaceId = "foreign") })!!.contains("partner-b"))
    }

    @Test fun initialCollectorUsesSelectedEligibleMemberWhileLaterCollectionPolicyRemainsSelfOnly() {
        for (uid in members.map { it.uid }) assertEquals(uid, CollectionPolicy.requireInitialCollector(ws, uid, members).uid)
        for (target in listOf("unknown", "operator", "removed")) assertThrows(SecurityException::class.java) {
            CollectionPolicy.requireInitialCollector(ws, target, members + members[1].copy(uid = "operator", role = "OPERATOR") +
                members[1].copy(uid = "removed", status = "REMOVED"))
        }
        assertThrows(SecurityException::class.java) { CollectionPolicy.requireCollector(ws, "partner-a", false, "partner-b", members) }
    }

    @Test fun personalAndBusinessFundingKeepCashInvestmentSharedCostAndWithdrawalsDistinct() {
        val jobs = listOf(job(1, "partner-a", 10000.0, 10000.0))
        val cash = listOf(PaymentEntity(id = 9, workspaceId = ws, jobEntryId = 1, customerId = 10, amount = 10000.0,
            collectedByUid = "owner", paymentMethod = "Cash", allocationsJson = "[{\"jobId\":1,\"amount\":10000}]"))
        for (uid in members.map { it.uid }) {
            val personal = listOf(expense(900.0, "I Paid", uid))
            assertEquals(10000.0, FinancialCalculationEngine.cashAvailable(cash, personal, emptyList(), scope, jobs), 0.0)
            val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(jobs, personal, emptyList(), workspaceMembers = members, scope = scope)
            assertEquals(900.0, statuses.single { it.partnerUid == uid }.totalInvestment, 0.0)
            assertEquals(900.0, statuses.sumOf { it.totalInvestment }, 0.0)
            assertEquals(0.0, statuses.sumOf { it.businessFundedCost + it.takenShare + it.takenInvestment }, 0.0)
        }
        val business = listOf(expense(900.0, "Business Paid"))
        assertEquals(9100.0, FinancialCalculationEngine.cashAvailable(cash, business, emptyList(), scope, jobs), 0.0)
        val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(jobs, business, emptyList(), workspaceMembers = members, scope = scope)
        statuses.forEach { assertEquals(300.0, it.businessFundedCost, 0.0); assertEquals(0.0, it.totalInvestment, 0.0) }
        assertEquals(900.0, statuses.sumOf { it.businessFundedCost }, 0.0)
        assertEquals(0.0, statuses.sumOf { it.takenShare + it.takenInvestment + it.takenPersonalUse }, 0.0)
        assertEquals(9100.0, statuses.sumOf { it.totalToGet }, 0.000001)
    }

    @Test fun tinyAndLargeCostAllocationsConservePaisaForOneThroughFiveParticipants() {
        val amounts = listOf(0.0, 0.01, 0.02, 0.03, 1.0, 2.0, 3.0, 10.0, 100.0, 900.0, 1000.0, 10000.0, 1000000.0)
        for (count in 1..5) for (amount in amounts) {
            val people = (1..count).map { WorkspaceMember(uid = "uid-$it", workspaceId = ws, role = "PARTNER", status = "ACTIVE", displayName = "Person $it") }
            val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(emptyList(), listOf(expense(amount, "Business Paid")), emptyList(), workspaceMembers = people, scope = scope)
            val paise = statuses.map { java.math.BigDecimal.valueOf(it.businessFundedCost).movePointRight(2).toLong() }
            assertEquals(java.math.BigDecimal.valueOf(amount).movePointRight(2).toLong(), paise.sum())
            assertTrue(paise.max() - paise.min() <= 1)
            assertEquals(0.0, statuses.sumOf { it.totalInvestment + it.takenShare + it.takenInvestment }, 0.0)
        }
        val repeated = (1L..300L).map { expense(1.0, "Business Paid", id = it) }
        val cumulative = FinancialCalculationEngine.calculatePartnerBreakdown(emptyList(), repeated, emptyList(), emptyList(), members, scope)
        cumulative.forEach { assertEquals(100.0, it.businessExpenses, 0.0) }
    }

    @Test fun explicitUnequalProfitPercentagesStayConfiguredWhileCostsRemainDistinct() {
        val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(listOf(job(1, "owner", 1000.0, 1000.0)), emptyList(), emptyList(),
            workspaceMembers = members, profitShareAllocations = mapOf("owner" to 60, "partner-a" to 30, "partner-b" to 10), scope = scope)
        assertEquals(listOf(600.0, 300.0, 100.0), members.map { m -> statuses.single { it.partnerUid == m.uid }.totalShare })
        assertEquals(listOf(60.0, 30.0, 10.0), members.map { m -> statuses.single { it.partnerUid == m.uid }.percentage })
    }
}
