package com.example

import com.example.data.auth.AuthorizationManager
import com.example.data.entity.*
import com.example.data.firebase.WorkspaceMember
import com.example.data.firebase.toFirestoreMap
import com.example.data.repository.enforceEntryIdentity
import com.example.data.sync.OperationPolicy
import com.example.data.sync.resolveExpenseAuthorizationJob
import kotlinx.coroutines.runBlocking
import com.example.ui.util.*
import com.example.ui.viewmodel.NewEntryDraft
import com.example.ui.viewmodel.linkedExpensePayerUid
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PartnerLedgerFollowupTest {
    private val scope = FinancialScope(workspaceId = "a")
    private val members = listOf("owner", "partner", "other").map {
        WorkspaceMember(uid = it, workspaceId = "a", role = if (it == "owner") "OWNER" else "PARTNER",
            status = "ACTIVE", displayName = it)
    }
    private fun job(id: Long = 1, target: String = "other") = JobEntryEntity(id = id, workspaceId = "a",
        customerId = 10, customerName = "Customer", operatorName = target, tractorLabel = "T", workType = "Work",
        startTimeMillis = 1000, endTimeMillis = 2000, durationMinutes = 1, totalAmount = 100.0,
        amountReceived = 40.0, pendingAmount = 60.0, createdAt = 1000, addedByPartner = "partner",
        createdByUid = "partner", createdByRole = "PARTNER", entryForUid = target, financialAttributionUid = target)
    private fun op(entity: String, target: String = "other", linkedId: Long = 1, created: Long = 1000,
                   action: Long = 2000, creator: String = "partner") = PendingOperationEntity(
        "op", "a", "partner", entity, 5, "UPSERT", 1, action,
        JSONObject().put("operationProtocolVersion", 2).put("workspaceId", "a")
            .put("createdAt", created).put("createdByUid", creator).put("isHistorical", false)
            .put("entryForUid", target).put("expenseForUid", target).put("relatedJobId", linkedId).toString())

    @Test fun partnerCanSelectOwnerOrPartnerWhileOperatorRemainsSelfOnly() {
        for (target in listOf("owner", "other")) {
            assertTrue(AuthorizationManager.canCreateEntryFor(false, "partner", target, "PARTNER"))
            val saved = enforceEntryIdentity(job(target = target), "partner", "PARTNER", "Partner Recorder")
            assertEquals(target, saved.entryForUid)
            assertEquals(target, saved.financialAttributionUid)
            assertEquals("partner", saved.createdByUid)
            assertEquals("Partner Recorder", saved.addedByPartner)
            assertEquals(target, saved.operatorName)
            OperationPolicy.requireAuthorized(op("JOB", target), "PARTNER", members)
        }
        assertFalse(AuthorizationManager.canCreateEntryFor(false, "operator", "owner", "OPERATOR"))
        val operator = enforceEntryIdentity(job(), "operator", "OPERATOR", "Operator")
        assertEquals("operator", operator.entryForUid)
        assertEquals("operator", operator.financialAttributionUid)
    }

    @Test fun linkedOtherMemberExpenseRequiresMatchingOwnJobNotJustAPositiveId() {
        val expense = op("EXPENSE")
        OperationPolicy.requireAuthorized(expense, "PARTNER", members, job())
        val invalid = listOf(null, job().copy(id = 9), job().copy(workspaceId = "b"),
            job().copy(createdByUid = "owner"), job().copy(entryForUid = "owner"), job().copy(isHistorical = true))
        invalid.forEach { source ->
            assertThrows(SecurityException::class.java) { OperationPolicy.requireAuthorized(expense, "PARTNER", members, source) }
        }
        assertThrows(SecurityException::class.java) { OperationPolicy.requireAuthorized(op("EXPENSE", linkedId = 0), "PARTNER", members) }
        assertThrows(SecurityException::class.java) { OperationPolicy.requireAuthorized(op("JOB", target = "foreign"), "PARTNER", members) }
        assertThrows(SecurityException::class.java) { OperationPolicy.requireAuthorized(op("JOB", creator = "owner"), "PARTNER", members) }
        assertThrows(SecurityException::class.java) { OperationPolicy.requireAuthorized(op("JOB", action = 86401000), "PARTNER", members) }
        assertThrows(SecurityException::class.java) { OperationPolicy.requireAuthorized(op("JOB"), "PARTNER", members.map { it.copy(status = "REMOVED") }) }
    }

    @Test fun personalExpenseFollowsPayerInOverviewAndInvestmentDespiteDifferentCreatorAndJobPerson() {
        val expense = ExpenseEntity(id = 2, workspaceId = "a", expenseType = "Diesel", amount = 30.0,
            tractorLabel = "T", paidBy = "I Paid", paidByUid = "other", paidByPartner = "other",
            addedByPartner = "owner", createdByUid = "owner", expenseForUid = "partner",
            financialAttributionUid = "partner", relatedJobId = 1)
        val overview = FinancialCalculationEngine.calculatePartnerBreakdown(listOf(job()), listOf(expense),
            emptyList(), emptyList(), members, scope)
        assertEquals(30.0, overview.single { it.partnerUid == "other" }.expenses, 0.0)
        assertTrue(overview.none { it.partnerUid == "owner" })
        val investment = FinancialCalculationEngine.calculatePartnerShareAndInvestment(listOf(job()), listOf(expense),
            emptyList(), workspaceMembers = members, scope = scope)
        assertEquals(30.0, investment.single { it.partnerUid == "other" }.totalInvestment, 0.0)
        val restored = NewEntryDraft.fromJobEntry(job(), listOf(expense)).expensesList.single()
        assertEquals("other", restored.paidByUid)
        assertEquals("other", linkedExpensePayerUid("I Paid", restored.paidByUid, "owner"))
        assertEquals("", linkedExpensePayerUid("Business Paid", restored.paidByUid, "owner"))
    }

    @Test fun missingPayerIsUnresolvedRatherThanAssignedToCreator() {
        val expense = ExpenseEntity(workspaceId = "a", expenseType = "Fuel", amount = 30.0, tractorLabel = "T",
            addedByPartner = "Owner", createdByUid = "owner", expenseForUid = "owner", paidByUid = "")
        val overview = FinancialCalculationEngine.calculatePartnerBreakdown(emptyList(), listOf(expense),
            emptyList(), emptyList(), members, scope)
        assertEquals("", overview.single().partnerUid)
        assertEquals("Attribution unavailable", overview.single().partnerName)
    }

    @Test fun missingCustomerMetadataDoesNotHideRealDuesAndDeleteRemovesDisplayFallback() {
        val rows = listOf(job(), job(2).copy(customerId = 11, customerName = "Same Name"))
        val customers = FinancialCalculationEngine.customersWithLedger(emptyList(), rows)
        assertEquals(setOf(10L, 11L), customers.map { it.id }.toSet())
        assertEquals(HomeRecordSummary.from(rows).due, customers.sumOf { it.balanceDue }, 0.0)
        assertTrue(FinancialCalculationEngine.customersWithLedger(customers, emptyList()).isEmpty())
        val foreign = CustomerEntity(id = 10, workspaceId = "b", name = "Foreign", phone = "", balanceDue = 900.0)
        val resolved = FinancialCalculationEngine.customersWithLedger(listOf(foreign), listOf(job()))
        assertEquals("a", resolved.single().workspaceId)
        assertEquals(60.0, resolved.single().balanceDue, 0.0)
    }

    @Test fun pendingCacheAndFractionalRoundingCannotMakeHomeAndCustomerTotalsDisagree() {
        val rows = listOf(job().copy(totalAmount = 100.4, amountReceived = 40.5, pendingAmount = 1.0))
        val ledger = FinancialCalculationEngine.calculateSummary(rows, emptyList(), emptyList(), scope)
        assertEquals(59.0, ledger.totalDue, 0.0)
        assertEquals(ledger.totalRecorded - ledger.totalReceived, ledger.totalDue, 0.0)
        val customer = FinancialCalculationEngine.customersWithLedger(emptyList(), rows).single()
        assertEquals(ledger.totalDue, customer.balanceDue, 0.0)
        val overview = FinancialCalculationEngine.calculatePartnerBreakdown(rows, emptyList(), emptyList(), emptyList(), members, scope)
        assertEquals(ledger.totalDue, overview.single().due, 0.0)
    }

    @Test fun paymentOnlyLegacyRowsDoNotKeepDeletedCustomerOrInflateHomeJobCount() {
        val receipt = job().copy(workType = "Payment Received", totalAmount = 40.0, pendingAmount = 0.0)
        assertEquals(HomeRecordSummary(0, 0.0, 0.0), HomeRecordSummary.from(listOf(receipt)))
        assertEquals(0.0, FinancialCalculationEngine.calculateSummary(listOf(receipt), emptyList(), emptyList(), scope).totalReceived, 0.0)
        assertTrue(FinancialCalculationEngine.customersWithLedger(emptyList(), listOf(receipt)).isEmpty())
    }

    @Test fun queuedExpenseCanValidateActualServerJobAfterOptimisticLocalDeletion() = runBlocking {
        val expense = op("EXPENSE")
        var requested: PendingOperationEntity? = null
        val source = resolveExpenseAuthorizationJob(expense, null) { read ->
            requested = read
            job().toFirestoreMap()
        }
        assertEquals("JOB", requested!!.entityType)
        assertEquals(1L, requested!!.entityId)
        assertEquals(expense.workspaceId, requested!!.workspaceId)
        assertEquals(expense.actorUid, requested!!.actorUid)
        OperationPolicy.requireAuthorized(expense, "PARTNER", members, source)
        assertNull(resolveExpenseAuthorizationJob(expense, null) { null })
        assertNull(resolveExpenseAuthorizationJob(expense, null) { job().toFirestoreMap() + ("isDeleted" to true) })
        assertNull(resolveExpenseAuthorizationJob(expense, null) { job().copy(isHistorical = true).toFirestoreMap() })
    }

    @Test fun serverFallbackDoesNotManufactureAuthorityFromOtherJobOrWorkspace(): Unit = runBlocking {
        val expense = op("EXPENSE")
        for (invalid in listOf(job().copy(workspaceId = "b"), job().copy(id = 9))) {
            try {
                resolveExpenseAuthorizationJob(expense, null) { invalid.toFirestoreMap() }
                fail("Mismatched server source accepted")
            } catch (_: SecurityException) { }
        }
        val foreignCreator = resolveExpenseAuthorizationJob(expense, null) { job().copy(createdByUid = "owner").toFirestoreMap() }
        assertThrows(SecurityException::class.java) { OperationPolicy.requireAuthorized(expense, "PARTNER", members, foreignCreator) }
    }
}
