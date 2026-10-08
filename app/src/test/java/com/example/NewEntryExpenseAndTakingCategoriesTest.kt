package com.example

import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.firebase.expenseFromFirestoreMap
import com.example.data.firebase.toFirestoreMap
import com.example.data.firebase.WorkspaceMember
import com.example.ui.util.FinancialCalculationEngine
import com.example.ui.viewmodel.DraftExpenseItem
import com.example.ui.viewmodel.NewEntryDraft
import org.junit.Assert.*
import org.junit.Test

class NewEntryExpenseAndTakingCategoriesTest {

    private fun createJob(received: Double): JobEntryEntity =
        JobEntryEntity(
            id = 42L,
            customerName = "Yuki",
            customerPhone = "9876543210",
            operatorName = "Driver John",
            tractorLabel = "TN-01-1234",
            workType = "Rotavator",
            startTimeMillis = 1000L,
            endTimeMillis = 5000L,
            durationMinutes = 300L,
            totalAmount = received,
            amountReceived = received,
            pendingAmount = 0.0,
            hourlyRate = 1100.0,
            addedByPartner = "Owner",
            createdAt = 1000L
        )

    private fun createExpense(
        id: Long = 1L,
        amount: Double,
        expenseType: String = "Diesel",
        description: String = "",
        paidBy: String = "I Paid",
        paidByPartner: String = "",
        paidByUid: String = "",
        createdByUid: String = "",
        createdByRole: String = "",
        relatedJobId: Long? = null
    ): ExpenseEntity =
        ExpenseEntity(
            id = id,
            expenseType = expenseType,
            amount = amount,
            tractorLabel = "TN-01-1234",
            description = description,
            addedByPartner = paidByPartner.ifBlank { "Owner" },
            paidBy = paidBy,
            paidByPartner = paidByPartner,
            paidByUid = paidByUid,
            createdByUid = createdByUid,
            createdByRole = createdByRole,
            relatedJobId = relatedJobId,
            createdAt = 1000L
        )

    @Test
    fun testNewEntryDraft_fromJobEntry_populatesLinkedExpenses() {
        val job = createJob(5500.0)

        val linkedExpenses = listOf(
            createExpense(
                id = 101L,
                amount = 500.0,
                expenseType = "Diesel",
                description = "Diesel for rotavator",
                paidBy = "Partner",
                paidByPartner = "Partner Raj",
                paidByUid = "partner_uid_raj",
                createdByUid = "operator_uid",
                createdByRole = "operator",
                relatedJobId = 42L
            ),
            createExpense(
                id = 102L,
                amount = 150.0,
                expenseType = "Maintenance",
                description = "Grease",
                paidBy = "Cash",
                relatedJobId = 42L
            )
        )

        val draft = NewEntryDraft.fromJobEntry(job, linkedExpenses)

        assertEquals("Yuki", draft.customerNameInput)
        assertFalse("includeLinkedExpense should be reset to false so new expenses aren't auto-checked", draft.includeLinkedExpense)
        assertEquals(2, draft.expensesList.size)

        val firstItem = draft.expensesList[0]
        assertEquals(101L, firstItem.id)
        assertEquals("Diesel", firstItem.expenseType)
        assertEquals(500.0, firstItem.amount, 0.001)
        assertEquals("Partner", firstItem.paidBy)
        assertEquals("Partner Raj", firstItem.paidByPartner)
        assertEquals("partner_uid_raj", firstItem.paidByUid)

        val secondItem = draft.expensesList[1]
        assertEquals(102L, secondItem.id)
        assertEquals(150.0, secondItem.amount, 0.001)
        assertEquals("Cash", secondItem.paidBy)
    }

    @Test
    fun testExpenseEntity_uidSerialization_roundTrip() {
        val original = createExpense(
            id = 55L,
            amount = 1200.0,
            expenseType = "Spare Parts",
            description = "Blade replacement",
            paidBy = "Partner",
            paidByPartner = "Murugan",
            paidByUid = "uid_murugan_123",
            createdByUid = "uid_creator_456",
            createdByRole = "partner",
            relatedJobId = 99L
        )

        val map = original.toFirestoreMap()
        assertEquals("uid_murugan_123", map["paidByUid"])
        assertEquals("uid_creator_456", map["createdByUid"])
        assertEquals("partner", map["createdByRole"])
        assertEquals(99L, map["relatedJobId"])

        val deserialized = expenseFromFirestoreMap(map)
        assertEquals(original.paidByUid, deserialized.paidByUid)
        assertEquals(original.createdByUid, deserialized.createdByUid)
        assertEquals(original.createdByRole, deserialized.createdByRole)
        assertEquals(original.relatedJobId, deserialized.relatedJobId)
    }

    @Test
    fun testCategoryDeduction_ShareOnly() {
        val members = listOf(
            WorkspaceMember(uid = "owner_uid", role = "owner", displayName = "Owner", status = "active"),
            WorkspaceMember(uid = "p1", role = "partner", displayName = "Partner One", status = "active")
        )
        val allocations = mapOf("owner_uid" to 50, "p1" to 50)

        // 1 Job with 10,000 received
        val jobs = listOf(createJob(10000.0))

        // Partner invested 2000 in diesel
        val expenses = listOf(
            createExpense(
                amount = 2000.0,
                paidBy = "I Paid",
                paidByPartner = "Partner One",
                paidByUid = "p1"
            )
        )

        // Partner takes 1500 with category = "Share"
        val withdrawals = listOf(
            WithdrawalEntity(
                id = 10L,
                partnerName = "Partner One",
                amount = 1500.0,
                category = "Share",
                targetPartnerUid = "p1",
                createdAt = 3000L
            )
        )

        val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = jobs,
            expenses = expenses,
            withdrawals = withdrawals,
            workspaceMembers = members,
            profitShareAllocations = allocations
        )

        val p1Status = statuses.find { it.partnerUid == "p1" }
        assertNotNull(p1Status)
        // Net profit = 10000 - 2000 = 8000. p1 share = 50% = 4000.
        // Total investment = 2000.
        // Withdrawal = 1500 from Share.
        // remainingShare should be 4000 - 1500 = 2500.
        // remainingInvestment should remain 2000!
        assertEquals(4000.0, p1Status!!.totalShare, 0.001)
        assertEquals(1500.0, p1Status.takenShare, 0.001)
        assertEquals(2500.0, p1Status.remainingShare, 0.001)
        assertEquals(2000.0, p1Status.totalInvestment, 0.001)
        assertEquals(0.0, p1Status.takenInvestment, 0.001)
        assertEquals(2000.0, p1Status.remainingInvestment, 0.001)
        assertEquals(4500.0, p1Status.totalToGet, 0.001)
    }

    @Test
    fun testCategoryDeduction_InvestmentOnly() {
        val members = listOf(
            WorkspaceMember(uid = "owner_uid", role = "owner", displayName = "Owner", status = "active"),
            WorkspaceMember(uid = "p1", role = "partner", displayName = "Partner One", status = "active")
        )
        val allocations = mapOf("owner_uid" to 50, "p1" to 50)

        val jobs = listOf(createJob(10000.0))

        val expenses = listOf(
            createExpense(
                amount = 3000.0,
                paidBy = "I Paid",
                paidByUid = "p1"
            )
        )

        // Partner takes 1000 with category = "Investment"
        val withdrawals = listOf(
            WithdrawalEntity(
                id = 11L,
                partnerName = "Partner One",
                amount = 1000.0,
                category = "Investment",
                targetPartnerUid = "p1",
                createdAt = 3000L
            )
        )

        val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = jobs,
            expenses = expenses,
            withdrawals = withdrawals,
            workspaceMembers = members,
            profitShareAllocations = allocations
        )

        val p1Status = statuses.find { it.partnerUid == "p1" }
        assertNotNull(p1Status)
        // Net profit = 10000 - 3000 = 7000. 50% = 3500.
        // Investment = 3000.
        // Withdrawal = 1000 from Investment.
        // remainingShare should remain 3500.
        // remainingInvestment should be 3000 - 1000 = 2000.
        assertEquals(3500.0, p1Status!!.totalShare, 0.001)
        assertEquals(0.0, p1Status.takenShare, 0.001)
        assertEquals(3500.0, p1Status.remainingShare, 0.001)
        assertEquals(3000.0, p1Status.totalInvestment, 0.001)
        assertEquals(1000.0, p1Status.takenInvestment, 0.001)
        assertEquals(2000.0, p1Status.remainingInvestment, 0.001)
        assertEquals(5500.0, p1Status.totalToGet, 0.001)
    }

    @Test
    fun testCategoryDeduction_ShareAndInvestment_oddRupeeSplit() {
        val members = listOf(
            WorkspaceMember(uid = "owner_uid", role = "owner", displayName = "Owner", status = "active"),
            WorkspaceMember(uid = "p1", role = "partner", displayName = "Partner One", status = "active")
        )
        val allocations = mapOf("owner_uid" to 50, "p1" to 50)

        val jobs = listOf(createJob(10000.0))

        val expenses = listOf(
            createExpense(
                amount = 2000.0,
                paidBy = "I Paid",
                paidByUid = "p1"
            )
        )

        // Odd rupee amount: 501
        // sharePart = ceil(501 / 2) = 251.0
        // investmentPart = 501 - 251 = 250.0
        val withdrawals = listOf(
            WithdrawalEntity(
                id = 12L,
                partnerName = "Partner One",
                amount = 501.0,
                category = "Share and Investment",
                targetPartnerUid = "p1",
                createdAt = 3000L
            )
        )

        val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = jobs,
            expenses = expenses,
            withdrawals = withdrawals,
            workspaceMembers = members,
            profitShareAllocations = allocations
        )

        val p1Status = statuses.find { it.partnerUid == "p1" }
        assertNotNull(p1Status)
        assertEquals(251.0, p1Status!!.takenShare, 0.001)
        assertEquals(250.0, p1Status.takenInvestment, 0.001)
        assertEquals(4000.0 - 251.0, p1Status.remainingShare, 0.001)
        assertEquals(2000.0 - 250.0, p1Status.remainingInvestment, 0.001)
    }

    @Test
    fun testCategoryDeduction_PersonalUseAdvance() {
        val members = listOf(
            WorkspaceMember(uid = "owner_uid", role = "owner", displayName = "Owner", status = "active"),
            WorkspaceMember(uid = "p1", role = "partner", displayName = "Partner One", status = "active")
        )
        val allocations = mapOf("owner_uid" to 50, "p1" to 50)

        val jobs = listOf(createJob(10000.0))

        val expenses = listOf(
            createExpense(
                amount = 2000.0,
                paidBy = "I Paid",
                paidByUid = "p1"
            )
        )

        // Partner takes 1000 with category = "Personal Use / Advance"
        val withdrawals = listOf(
            WithdrawalEntity(
                id = 13L,
                partnerName = "Partner One",
                amount = 1000.0,
                category = "Personal Use / Advance",
                targetPartnerUid = "p1",
                createdAt = 3000L
            )
        )

        val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = jobs,
            expenses = expenses,
            withdrawals = withdrawals,
            workspaceMembers = members,
            profitShareAllocations = allocations
        )

        val p1Status = statuses.find { it.partnerUid == "p1" }
        assertNotNull(p1Status)
        // remainingShare and remainingInvestment should be completely untouched
        assertEquals(4000.0, p1Status!!.remainingShare, 0.001)
        assertEquals(2000.0, p1Status.remainingInvestment, 0.001)
        assertEquals(0.0, p1Status.takenShare, 0.001)
        assertEquals(0.0, p1Status.takenInvestment, 0.001)
        // takenPersonalUse should track 1000.0
        assertEquals(1000.0, p1Status.takenPersonalUse, 0.001)
        // totalToGet is (remainingShare + remainingInvestment - takenPersonalUse) = 4000 + 2000 - 1000 = 5000.0
        assertEquals(5000.0, p1Status.totalToGet, 0.001)
    }
}
