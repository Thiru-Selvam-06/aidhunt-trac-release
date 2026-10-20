package com.example

import com.example.data.auth.AuthorizationManager
import com.example.data.auth.RoleUtils
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.firebase.WorkspaceMember
import com.example.ui.screens.account.pendingInvitationForUnresolvedPartner
import com.example.ui.util.isRecentJobToday
import com.example.ui.util.recentJobDisplayTimestamp
import com.example.data.repository.enforceEntryIdentity
import com.example.ui.screens.report.WithdrawalCategories
import com.example.ui.screens.report.authorizedCollectionSource
import com.example.ui.util.FinancialCalculationEngine
import org.junit.Assert.*
import org.junit.Test

class ClientDeliveryRequirementsTest {

    private fun createJob(
        id: Long = 42L,
        received: Double = 10000.0,
        createdAt: Long = 1000L,
        operatorName: String = "Partner One",
        addedByPartner: String = "Partner One",
        createdByUid: String = "partner_1",
        createdByRole: String = RoleUtils.ROLE_PARTNER
    ): JobEntryEntity =
        JobEntryEntity(
            id = id,
            customerName = "Cust",
            customerPhone = "9876543210",
            operatorName = operatorName,
            tractorLabel = "TN-01-1234",
            workType = "Rotavator",
            startTimeMillis = createdAt,
            endTimeMillis = createdAt + 5000L,
            durationMinutes = 300L,
            totalAmount = received,
            amountReceived = received,
            pendingAmount = 0.0,
            hourlyRate = 1100.0,
            addedByPartner = addedByPartner,
            createdAt = createdAt,
            createdByUid = createdByUid,
            entryForUid = createdByUid,
            financialAttributionUid = createdByUid,
            createdByRole = createdByRole
        )

    private fun createExpense(
        id: Long = 1L,
        amount: Double = 1000.0,
        expenseType: String = "Diesel",
        description: String = "",
        paidBy: String = "I Paid",
        paidByPartner: String = "Partner One",
        paidByUid: String = "partner_1",
        createdByUid: String = "partner_1",
        createdByRole: String = RoleUtils.ROLE_PARTNER,
        createdAt: Long = 1000L
    ): ExpenseEntity =
        ExpenseEntity(
            id = id,
            expenseType = expenseType,
            amount = amount,
            tractorLabel = "TN-01-1234",
            description = description,
            addedByPartner = paidByPartner,
            operatorName = paidByPartner,
            paidBy = paidBy,
            paidByPartner = paidByPartner,
            paidByUid = paidByUid,
            createdByUid = createdByUid,
            createdByRole = createdByRole,
            createdAt = createdAt
        )

    // -------------------------------------------------------------
    // Requirement 4: Take Amount Categories
    // -------------------------------------------------------------
    @Test
    fun testTakeAmountCategories_containsOnlyWithdrawalAndInvestment() {
        assertEquals(2, WithdrawalCategories.size)
        assertEquals(listOf("Withdrawal", "Investment"), WithdrawalCategories)
        assertFalse(WithdrawalCategories.contains("Share Investment"))
        assertFalse(WithdrawalCategories.contains("Share & Investment"))
        assertFalse(WithdrawalCategories.contains("Personal Use"))
        assertFalse(WithdrawalCategories.contains("Personal Use / Advance"))
    }

    @Test
    fun testFinancialCalculationEngine_withdrawalCategory_deductsFromShare() {
        val members = listOf(
            WorkspaceMember(uid = "owner_uid", role = "owner", displayName = "Owner", status = "active"),
            WorkspaceMember(uid = "partner_1", role = "partner", displayName = "Partner One", status = "active")
        )
        val allocations = mapOf("owner_uid" to 50, "partner_1" to 50)
        val jobs = listOf(createJob(id = 1L, received = 10000.0, createdAt = 1000L))

        // Partner takes 1500 with category = "Withdrawal"
        val withdrawals = listOf(
            WithdrawalEntity(
                id = 1L,
                partnerName = "Partner One",
                amount = 1500.0,
                category = "Withdrawal",
                targetPartnerUid = "partner_1",
                createdAt = 2000L
            )
        )

        val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = jobs,
            expenses = emptyList(),
            withdrawals = withdrawals,
            workspaceMembers = members,
            profitShareAllocations = allocations
        )

        val p1 = statuses.find { it.partnerUid == "partner_1" }
        assertNotNull(p1)
        assertEquals(5000.0, p1!!.totalShare, 0.001)
        assertEquals(1500.0, p1.takenShare, 0.001)
        assertEquals(3500.0, p1.remainingShare, 0.001)
        assertEquals(0.0, p1.takenInvestment, 0.001)
        assertEquals(3500.0, p1.totalToGet, 0.001)
    }

    @Test
    fun testFinancialCalculationEngine_investmentCategory_deductsFromInvestment() {
        val members = listOf(
            WorkspaceMember(uid = "owner_uid", role = "owner", displayName = "Owner", status = "active"),
            WorkspaceMember(uid = "partner_1", role = "partner", displayName = "Partner One", status = "active")
        )
        val allocations = mapOf("owner_uid" to 50, "partner_1" to 50)
        val jobs = listOf(createJob(id = 1L, received = 10000.0, createdAt = 1000L))

        // Partner paid 3000 expense ("I Paid") -> 3000 total investment
        val expenses = listOf(
            createExpense(
                id = 1L,
                amount = 3000.0,
                paidBy = "I Paid",
                paidByPartner = "Partner One",
                paidByUid = "partner_1",
                createdAt = 1500L
            )
        )
        // Partner takes 1000 with category = "Investment"
        val withdrawals = listOf(
            WithdrawalEntity(
                id = 1L,
                partnerName = "Partner One",
                amount = 1000.0,
                category = "Investment",
                targetPartnerUid = "partner_1",
                createdAt = 2000L
            )
        )

        val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = jobs,
            expenses = expenses,
            withdrawals = withdrawals,
            workspaceMembers = members,
            profitShareAllocations = allocations
        )

        val p1 = statuses.find { it.partnerUid == "partner_1" }
        assertNotNull(p1)
        assertEquals(3000.0, p1!!.totalInvestment, 0.001)
        assertEquals(1000.0, p1.takenInvestment, 0.001)
        assertEquals(2000.0, p1.remainingInvestment, 0.001)
        assertEquals(0.0, p1.takenShare, 0.001)
    }

    // -------------------------------------------------------------
    // Requirement 5: Partner Edit/Delete Authorization
    // -------------------------------------------------------------
    @Test
    fun testPartner_canEditAndDelete_onlyOwnNewEntryJob_within24Hours() {
        val now = System.currentTimeMillis()
        val ownJobRecent = createJob(
            id = 10L,
            createdAt = now - 10000L,
            operatorName = "Partner One",
            addedByPartner = "Partner One",
            createdByUid = "partner_1",
            createdByRole = RoleUtils.ROLE_PARTNER
        )

        // Partner CAN edit and delete their own record within 24h
        assertTrue(
            AuthorizationManager.canEditEntry(
                job = ownJobRecent,
                isOwner = false,
                currentActorName = "Partner One",
                currentUid = "partner_1",
                currentTimeMillis = now,
                role = RoleUtils.ROLE_PARTNER
            )
        )
        assertTrue(
            AuthorizationManager.canDeleteEntry(
                job = ownJobRecent,
                isOwner = false,
                currentActorName = "Partner One",
                currentUid = "partner_1",
                currentTimeMillis = now,
                role = RoleUtils.ROLE_PARTNER
            )
        )

        // Partner CANNOT edit or delete their own record after 24 hours
        val ownJobOld = ownJobRecent.copy(
            startTimeMillis = now - (25 * 3600 * 1000L),
            createdAt = now - (25 * 3600 * 1000L)
        )
        assertFalse(
            AuthorizationManager.canEditEntry(
                job = ownJobOld,
                isOwner = false,
                currentActorName = "Partner One",
                currentUid = "partner_1",
                currentTimeMillis = now,
                role = RoleUtils.ROLE_PARTNER
            )
        )
        assertFalse(
            AuthorizationManager.canDeleteEntry(
                job = ownJobOld,
                isOwner = false,
                currentActorName = "Partner One",
                currentUid = "partner_1",
                currentTimeMillis = now,
                role = RoleUtils.ROLE_PARTNER
            )
        )

        // Partner CANNOT edit or delete another partner's job record even within 24h
        val otherPartnerJob = ownJobRecent.copy(
            addedByPartner = "Partner Two",
            createdByUid = "partner_2"
        )
        assertFalse(
            AuthorizationManager.canEditEntry(
                job = otherPartnerJob,
                isOwner = false,
                currentActorName = "Partner One",
                currentUid = "partner_1",
                currentTimeMillis = now,
                role = RoleUtils.ROLE_PARTNER
            )
        )
        assertFalse(
            AuthorizationManager.canDeleteEntry(
                job = otherPartnerJob,
                isOwner = false,
                currentActorName = "Partner One",
                currentUid = "partner_1",
                currentTimeMillis = now,
                role = RoleUtils.ROLE_PARTNER
            )
        )
    }

    @Test
    fun testOwner_canEditAndDelete_jobsWithout24HourRestriction() {
        val now = System.currentTimeMillis()
        val oldJob = createJob(
            id = 20L,
            createdAt = now - (30 * 24 * 3600 * 1000L),
            operatorName = "Partner One",
            addedByPartner = "Partner One",
            createdByUid = "partner_1",
            createdByRole = RoleUtils.ROLE_PARTNER
        )

        // Owner CAN edit and delete even after 30 days
        assertTrue(
            AuthorizationManager.canEditEntry(
                job = oldJob,
                isOwner = true,
                currentActorName = "Owner",
                currentUid = "owner_uid",
                currentTimeMillis = now,
                role = RoleUtils.ROLE_OWNER
            )
        )
        assertTrue(
            AuthorizationManager.canDeleteEntry(
                job = oldJob,
                isOwner = true,
                currentActorName = "Owner",
                currentUid = "owner_uid",
                currentTimeMillis = now,
                role = RoleUtils.ROLE_OWNER
            )
        )
    }

    @Test
    fun testPartner_canEditAndDeleteOwnExpensesWithin24Hours() {
        val now = System.currentTimeMillis()
        val expense = createExpense(
            id = 30L,
            amount = 1000.0,
            expenseType = "Diesel",
            paidByPartner = "Partner One",
            createdAt = now,
            createdByUid = "partner_1",
            createdByRole = RoleUtils.ROLE_PARTNER
        )

        // v1.6 permits an own record inside its original creation window
        assertTrue(
            AuthorizationManager.canEditExpense(
                expense = expense,
                isOwner = false,
                currentActorName = "Partner One",
                currentUid = "partner_1",
                role = RoleUtils.ROLE_PARTNER
            )
        )
        assertTrue(
            AuthorizationManager.canDeleteExpense(
                expense = expense,
                isOwner = false,
                currentActorName = "Partner One",
                currentUid = "partner_1",
                role = RoleUtils.ROLE_PARTNER
            )
        )

        // Owner permitted
        assertTrue(
            AuthorizationManager.canEditExpense(
                expense = expense,
                isOwner = true,
                currentActorName = "Owner",
                currentUid = "owner_uid",
                role = RoleUtils.ROLE_OWNER
            )
        )
        assertTrue(
            AuthorizationManager.canDeleteExpense(
                expense = expense,
                isOwner = true,
                currentActorName = "Owner",
                currentUid = "owner_uid",
                role = RoleUtils.ROLE_OWNER
            )
        )
    }

    @Test
    fun testPartner_canDeleteOwnWithdrawalWithin24Hours() {
        val now = System.currentTimeMillis()
        val withdrawal = WithdrawalEntity(
            id = 40L,
            partnerName = "Partner One",
            amount = 500.0,
            category = "Withdrawal",
            targetPartnerUid = "partner_1",
            createdAt = now,
            createdByUid = "partner_1",
            createdByRole = RoleUtils.ROLE_PARTNER
        )

        // v1.6 permits an own record inside its original creation window
        assertTrue(
            AuthorizationManager.canDeleteWithdrawal(
                withdrawal = withdrawal,
                isOwner = false,
                currentActorName = "Partner One",
                currentUid = "partner_1",
                role = RoleUtils.ROLE_PARTNER
            )
        )

        // Owner permitted
        assertTrue(
            AuthorizationManager.canDeleteWithdrawal(
                withdrawal = withdrawal,
                isOwner = true,
                currentActorName = "Owner",
                currentUid = "owner_uid",
                role = RoleUtils.ROLE_OWNER
            )
        )
    }

    @Test
    fun partnerBreakdown_usesOnlyActiveMemberUids() {
        val members = listOf(
            WorkspaceMember(uid = "w1-owner", role = "owner", displayName = "Muniyappan", status = "active"),
            WorkspaceMember(uid = "w1-partner", role = "partner", displayName = "Perumal", status = "active"),
            WorkspaceMember(uid = "w2-partner", role = "partner", displayName = "Adam", status = "active")
        )
        val result = FinancialCalculationEngine.calculatePartnerBreakdown(
            jobs = listOf(
                createJob(id = 51, createdByUid = "w1-owner", operatorName = "Owner"),
                createJob(id = 52, createdByUid = "w2-partner", operatorName = "Adam")
            ),
            expenses = emptyList(),
            withdrawals = emptyList(),
            partners = emptyList(),
            workspaceMembers = members.take(2),
            scope = com.example.ui.util.FinancialScope()
        )
        assertEquals(setOf("w1-owner"), result.map { it.partnerUid }.filter { it.isNotBlank() }.toSet())
        assertEquals(10000.0, result.single { it.partnerUid.isBlank() }.recorded, 0.001)
        assertFalse(result.any { it.partnerName == "Adam" })
    }

    @Test
    fun partnerBreakdown_collapsesSameUidAndKeepsSameNameDifferentUids() {
        val members = listOf(
            WorkspaceMember(uid = "same", role = "owner", displayName = "Muniyappan", status = "active"),
            WorkspaceMember(uid = "same", role = "owner", displayName = "Owner", status = "active"),
            WorkspaceMember(uid = "a", role = "partner", displayName = "Adam", status = "active"),
            WorkspaceMember(uid = "b", role = "partner", displayName = "Adam", status = "active")
        )
        val result = FinancialCalculationEngine.calculatePartnerBreakdown(
            jobs = listOf(
                createJob(id = 61, createdByUid = "same"),
                createJob(id = 62, createdByUid = "a"),
                createJob(id = 63, createdByUid = "b")
            ),
            expenses = emptyList(),
            withdrawals = emptyList(),
            partners = emptyList(),
            workspaceMembers = members,
            scope = com.example.ui.util.FinancialScope()
        )
        assertEquals(3, result.size)
        assertEquals(1, result.count { it.partnerUid == "same" })
        assertEquals(setOf("a", "b"), result.filter { it.partnerName == "Adam" }.map { it.partnerUid }.toSet())
        assertFalse(result.any { it.partnerName == "Owner" })
    }

    @Test
    fun partnerBreakdown_doesNotInventParticipantFromLegacyDisplayName() {
        val result = FinancialCalculationEngine.calculatePartnerBreakdown(
            jobs = listOf(createJob(id = 71, createdByUid = "", operatorName = "Unknown Person")),
            expenses = emptyList(),
            withdrawals = emptyList(),
            partners = emptyList(),
            workspaceMembers = listOf(
                WorkspaceMember(uid = "known", role = "partner", displayName = "Known", status = "active")
            ),
            scope = com.example.ui.util.FinancialScope()
        )
        assertEquals(1, result.size)
        assertEquals("", result.single().partnerUid)
        assertEquals(10000.0, result.single().recorded, 0.001)
        assertFalse(result.any { it.partnerName == "Unknown Person" })
    }

    @Test
    fun testPartner_hasNoDeleteAuthority_forCustomerOrCollection() {
        // Partner denied
        assertFalse(AuthorizationManager.canDeleteCustomer(isOwner = false))
        assertFalse(AuthorizationManager.canDeleteCollection(isOwner = false))

        // Owner permitted
        assertTrue(AuthorizationManager.canDeleteCustomer(isOwner = true))
        assertTrue(AuthorizationManager.canDeleteCollection(isOwner = true))
    }

    @Test
    fun collectionHistorySource_usesLegacyJobsForSummaryAndDetailTogether() {
        val jobs = listOf(
            createJob(id = 80L, received = 2500.0, createdAt = 1_000L).copy(workspaceId = "ws-1"),
            createJob(id = 81L, received = 1000.0, createdAt = 2_000L).copy(workspaceId = "ws-1")
        )
        val source = authorizedCollectionSource(
            workspaceId = "ws-1",
            payments = emptyList(),
            jobs = jobs,
            workspaceMembers = emptyList(),
            isOwner = true,
            currentUid = "owner"
        )
        assertEquals(2, source.legacyJobs.size)
        assertEquals(0, source.payments.size)
        assertEquals(3500.0, source.legacyJobs.sumOf { it.amountReceived }, 0.001)
    }

    @Test
    fun collectionHistorySource_partnerSeesAllAuthorizedWorkspaceHistory() {
        val payments = listOf(
            PaymentEntity(id = 90L, workspaceId = "ws-1", amount = 900.0, collectedAt = 9_999L),
            PaymentEntity(id = 91L, workspaceId = "ws-1", amount = 500.0, collectedAt = 10_000L)
        )
        val source = authorizedCollectionSource(
            workspaceId = "ws-1",
            payments = payments,
            jobs = emptyList(),
            workspaceMembers = listOf(
                WorkspaceMember(uid = "partner", workspaceId = "ws-1", role = "partner", status = "active", joinedAt = 10_000L)
            ),
            isOwner = false,
            currentUid = "partner"
        )
        assertEquals(listOf(90L, 91L), source.payments.map { it.id })
        assertEquals(1400.0, source.payments.sumOf { it.amount }, 0.001)
    }

    @Test
    fun collectionHistorySource_excludesForeignWorkspaceForPartner_butOwnerKeepsValidHistory() {
        val payments = listOf(
            PaymentEntity(id = 100L, workspaceId = "ws-1", amount = 1000.0, collectedAt = 20_000L),
            PaymentEntity(id = 101L, workspaceId = "ws-2", amount = 2000.0, collectedAt = 20_000L)
        )
        val members = listOf(
            WorkspaceMember(uid = "partner", workspaceId = "ws-1", role = "partner", status = "active", joinedAt = 1_000L)
        )
        val partnerSource = authorizedCollectionSource(
            workspaceId = "ws-1", payments = payments, jobs = emptyList(),
            workspaceMembers = members, isOwner = false, currentUid = "partner"
        )
        val ownerSource = authorizedCollectionSource(
            workspaceId = "ws-1", payments = payments, jobs = emptyList(),
            workspaceMembers = emptyList(), isOwner = true, currentUid = "owner"
        )
        assertEquals(listOf(100L), partnerSource.payments.map { it.id })
        assertEquals(setOf(100L), ownerSource.payments.map { it.id }.toSet())
    }

    @Test
    fun collectionReversal_restoresCustomerDue_withoutDeletingWorkJob() {
        val customer = com.example.data.entity.CustomerEntity(
            id = 130L,
            workspaceId = "ws-collection",
            name = "Customer X",
            phone = "9876543210",
            totalBilled = 8000.0,
            totalPaid = 3000.0,
            balanceDue = 5000.0
        )
        val collectedJob = createJob(id = 131L, received = 3000.0, createdAt = 10_000L).copy(
            workspaceId = "ws-collection",
            customerId = customer.id,
            customerName = customer.name,
            customerPhone = customer.phone,
            totalAmount = 8000.0,
            amountReceived = 3000.0,
            pendingAmount = 5000.0
        )

        val before = FinancialCalculationEngine.calculateCustomerFinancials(customer, listOf(collectedJob))
        assertEquals(5000.0, before.balanceDue, 0.001)

        val reversedJob = collectedJob.copy(amountReceived = 0.0, pendingAmount = 8000.0)
        val after = FinancialCalculationEngine.calculateCustomerFinancials(customer, listOf(reversedJob))

        assertEquals(8000.0, after.balanceDue, 0.001)
        assertEquals(131L, reversedJob.id)
        assertEquals(8000.0, reversedJob.totalAmount, 0.001)
    }

    @Test
    fun partnerNewEntry_preservesSelectedPersonAndRealCreator() {
        val submitted = createJob(id = 140L, createdByUid = "mi_uid").copy(
            operatorName = "Perumal",
            entryForUid = "perumal_uid",
            addedByPartner = "Perumal",
            createdByRole = "Operator"
        )

        val saved = enforceEntryIdentity(
            job = submitted,
            actorUid = "mi_uid",
            actorRole = "Partner",
            actorDisplayName = "MI"
        )

        assertEquals("mi_uid", saved.createdByUid)
        assertEquals("Partner", saved.createdByRole)
        assertEquals("Perumal", saved.operatorName)
        assertEquals("perumal_uid", saved.entryForUid)
        assertEquals("MI", saved.addedByPartner)
    }

    @Test(expected = IllegalArgumentException::class)
    fun partnerNewEntry_identityFailsClosedWithoutAuthenticatedUid() {
        enforceEntryIdentity(
            job = createJob(id = 141L),
            actorUid = "",
            actorRole = "Partner",
            actorDisplayName = "MI"
        )
    }

    @Test
    fun offlineCreatedJob_remainsRecentWhenSyncedWorkTimestampIsOlder() {
        val now = System.currentTimeMillis()
        val job = createJob(id = 110L, createdAt = now).copy(
            startTimeMillis = now - 2L * 24L * 60L * 60L * 1000L,
            isSynced = true
        )
        assertTrue(isRecentJobToday(job, now))
        assertEquals(now, recentJobDisplayTimestamp(job, now))
        assertEquals(1, listOf(job).filter { isRecentJobToday(it, now) }.distinctBy { it.id }.size)
    }

    @Test
    fun participantName_prefersLatestMembershipNameForSameUid() {
        val result = FinancialCalculationEngine.calculatePartnerBreakdown(
            jobs = listOf(createJob(id = 120L, createdByUid = "owner_uid")),
            expenses = emptyList(),
            withdrawals = emptyList(),
            partners = emptyList(),
            workspaceMembers = listOf(
                WorkspaceMember(uid = "owner_uid", role = "owner", status = "active", displayName = "owner of 89")
            ),
            scope = com.example.ui.util.FinancialScope()
        )
        assertEquals(listOf("owner of 89"), result.map { it.partnerName })
        assertEquals(1, result.count { it.partnerUid == "owner_uid" })
    }

    @Test
    fun unresolvedLocalPartner_exposesDedicatedPendingInvitationAction() {
        val partner = PartnerEntity(
            id = 121L,
            name = "Pending Partner",
            phone = "+918925624885",
            role = "Partner",
            photoUri = ""
        )
        val pending = pendingInvitationForUnresolvedPartner(partner, matchedMember = null)
        assertNotNull(pending)
        assertTrue(pending!!.uid.isBlank())
        assertEquals("waiting_for_registration", pending.status)
        assertEquals(partner.phone, pending.phoneNumber)
    }
}
