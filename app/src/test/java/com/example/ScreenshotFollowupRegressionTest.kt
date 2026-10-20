package com.example

import com.example.data.entity.*
import com.example.data.firebase.*
import com.example.ui.screens.report.*
import com.example.ui.util.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class ScreenshotFollowupRegressionTest {
    private fun job(id: Long, customerId: Long = id) = JobEntryEntity(
        id = id, workspaceId = "a", customerId = customerId, customerName = "Customer $customerId",
        operatorName = "Owner", tractorLabel = "Tractor", workType = "Work", startTimeMillis = 1000,
        endTimeMillis = 2000, durationMinutes = 1, totalAmount = 100.0, amountReceived = 100.0,
        pendingAmount = 0.0, addedByPartner = "Owner", createdAt = 1000,
        createdByUid = "owner", entryForUid = "owner")
    private val members = listOf("owner", "p1", "p2").map {
        WorkspaceMember(uid = it, workspaceId = "a", displayName = it, status = "ACTIVE",
            role = if (it == "owner") "OWNER" else "PARTNER") }
    private fun operation(id: Long, state: String = "PENDING", type: String = "JOB", ws: String = "a") =
        PendingOperationEntity("$ws:$type:$id:$state", ws, "owner", type, id, "DELETE", 1, 1000, "{}", state)

    @Test fun homeCountsCustomersSeparatelyFromJobsAndUsesTheDuesLedger() {
        val customers = (1L..80L).map { CustomerEntity(id = it, workspaceId = "a", name = "Customer $it", phone = "") }
        val jobs = (1L..89L).map { job(it, (it - 1) % 80 + 1) }
        val summary = HomeRecordSummary.from(jobs, customers)
        assertEquals(80, summary.customerCount)
        assertEquals(89, summary.count)
        assertEquals(FinancialCalculationEngine.customersWithLedger(customers, jobs).size, summary.customerCount)
        assertEquals(79, HomeRecordSummary.from(jobs.filterNot { it.customerId == 80L }, customers).customerCount)
    }

    @Test fun equalThirdsUsePaisaAndPreserveTotalRegardlessOfMemberOrder() {
        val shares = equalMoneyShares(100.0, members.map { it.uid })
        assertEquals(listOf(33.33, 33.33, 33.34), shares.values.sorted())
        assertEquals(100.0, shares.values.sum(), 0.000001)
        assertEquals(shares, equalMoneyShares(100.0, members.reversed().map { it.uid }))
        assertEquals(100.01, equalMoneyShares(100.01, shares.keys).values.sum(), 0.000001)
        assertEquals("33.33%", formatSharePercentage(100.0 / 3))
        assertEquals("50%", formatSharePercentage(50.0))
    }

    @Test fun legacyEqualPresetIsEqualButCustomAllocationsRemainCustom() {
        fun shares(config: Map<String, Int>) = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            listOf(job(1)), emptyList(), emptyList(), workspaceMembers = members,
            profitShareAllocations = config, scope = FinancialScope(workspaceId = "a"))
        val equal = shares(mapOf("owner" to 34, "p1" to 33, "p2" to 33))
        assertTrue(equal.all { kotlin.math.abs(it.percentage - 100.0 / 3) < 0.000001 })
        assertEquals(listOf(33.33, 33.33, 33.34), equal.map { it.totalShare }.sorted())
        assertEquals(listOf(25.0, 25.0, 50.0), shares(mapOf("owner" to 50, "p1" to 25, "p2" to 25)).map { it.totalShare }.sorted())
    }

    @Test fun unresolvedAmountIsRetainedInTotalsWithoutInventingAFourthPartner() {
        val rows = listOf(PartnerFinancialBreakdown(partnerName = "Attribution unavailable", recorded = 2200.0),
            PartnerFinancialBreakdown(partnerUid = "p1", partnerName = "p1", recorded = 100.0))
        val chart = businessOverviewChartMembers(rows, members, "owner", "a")
        assertEquals(setOf("owner", "p1", "p2"), chart.map { it.partnerUid }.toSet())
        assertEquals(2300.0, rows.sumOf { it.recorded }, 0.0)
        assertEquals(100.0, chart.sumOf { it.recorded }, 0.0)
        assertTrue(businessOverviewChartMembers(rows, members, "foreign", "a").isEmpty())
    }

    @Test fun deletedJobReceiptsAreExcludedFromActiveHistoryWhileAuditIsRetained() {
        val payment = PaymentEntity(id = 1, workspaceId = "a", customerId = 1, jobEntryId = 1,
            amount = 100.0, allocationsJson = """[{"jobId":"1","amount":100}]""")
        val raw = collectionJobHistory("a", listOf(payment), emptyList())
        assertEquals(1, raw.size)
        assertTrue(visibleCollectionJobHistory(raw).isEmpty())
        assertEquals(1, visibleCollectionJobHistory(collectionJobHistory("a", listOf(payment), listOf(job(1)))).size)
        assertTrue(visibleCollectionJobHistory(collectionJobHistory("a", listOf(payment.copy(status = "REVERSED")), emptyList())).isEmpty())
        assertEquals(1, visibleCollectionJobHistory(collectionJobHistory("a", listOf(payment.copy(status = "REVERSED")), listOf(job(1)))).size)
    }

    @Test fun syncStatusIncludesUnqueuedLocalRowsAndDoesNotClaimSynced() {
        val bigId = 3758959838990728744L
        val local = LocalUnsyncedRecords("a", setOf(SyncRecordKey("JOB", bigId), SyncRecordKey("EXPENSE", bigId)))
        val status = LocalRecordSyncStatus.from("a", local, listOf(operation(bigId)))
        assertEquals(1, status.count("JOB"))
        assertEquals(1, status.count("EXPENSE"))
        assertEquals(1, status.pendingRecords.size)
        assertEquals(1, status.reviewCount)
        assertEquals("1 Need review", cloudSyncLabel(CloudConnectionState.CONNECTED, true, false,
            status.pendingRecords.size, rejected = status.reviewCount))
    }

    @Test fun rejectedDeleteRemainsVisibleEvenWithoutItsLocalRowAndOtherWorkspaceIsExcluded() {
        val status = LocalRecordSyncStatus.from("a", LocalUnsyncedRecords("a"),
            listOf(operation(1, "REJECTED"), operation(2, ws = "b"), operation(3, "ACKNOWLEDGED")))
        assertTrue(status.loaded)
        assertEquals(1, status.count("JOB"))
        assertEquals(1, status.reviewCount)
        assertTrue(status.pendingRecords.isEmpty())
        val switched = LocalRecordSyncStatus.from("b", LocalUnsyncedRecords("a", setOf(SyncRecordKey("JOB", 99))), emptyList())
        assertFalse(switched.loaded)
        assertTrue(switched.records.isEmpty())
    }

    @Test fun serverIdentityCanOnlyRepairTheSameUnmodifiedSourceWithoutPendingWork() {
        val remote = job(3758959838990728744L)
        val stale = remote.copy(createdByUid = "", entryForUid = "", financialAttributionUid = "", isSynced = false)
        assertTrue(com.example.data.sync.ConfirmedReplica.job(stale, remote, false))
        assertFalse(com.example.data.sync.ConfirmedReplica.job(stale, remote, true))
        assertFalse(com.example.data.sync.ConfirmedReplica.job(stale.copy(totalAmount = 101.0), remote, false))
        assertFalse(com.example.data.sync.ConfirmedReplica.job(stale.copy(customerId = 22), remote, false))
        assertFalse(com.example.data.sync.ConfirmedReplica.job(stale, remote.copy(workspaceId = "b"), false))
        assertFalse(com.example.data.sync.ConfirmedReplica.job(stale.copy(entryForUid = "p1"), remote, false))
    }

    @Test fun fractionalSharePayoutIsCountedIdenticallyInCashSummaryAndPartnerBalance() {
        val withdrawal = WithdrawalEntity(id = 1, workspaceId = "a", partnerName = "p1", targetPartnerUid = "p1",
            amount = 33.33, category = "Share")
        val source = job(1)
        val receipt = PaymentEntity(id = 2, workspaceId = "a", amount = 100.0, paymentMethod = "Cash")
        val scope = FinancialScope(workspaceId = "a")
        val summary = FinancialCalculationEngine.calculateSummary(listOf(source), emptyList(), listOf(withdrawal), scope)
        val cash = FinancialCalculationEngine.cashAvailable(listOf(receipt), emptyList(), listOf(withdrawal), scope)
        val status = FinancialCalculationEngine.calculatePartnerShareAndInvestment(listOf(source), emptyList(), listOf(withdrawal),
            workspaceMembers = members, scope = scope).single { it.partnerUid == "p1" }
        assertEquals(33.33, summary.totalWithdrawals, 0.000001)
        assertEquals(66.67, summary.availableBalance, 0.000001)
        assertEquals(summary.availableBalance, cash, 0.000001)
        assertEquals(33.33, status.takenShare, 0.000001)
        assertEquals(0.0, status.remainingShare, 0.000001)
    }

    @Test fun recordedLegacyCashIsIncludedWithoutDoubleCountingNewCollectionsOrGuessingUnknownMethods() {
        val scope = FinancialScope(workspaceId = "a")
        val legacy = job(1).copy(amountReceived = 70.0, pendingAmount = 30.0, notes = "Payment Method: Cash")
        val receipt = PaymentEntity(id = 2, workspaceId = "a", customerId = 1, jobEntryId = 1, amount = 30.0,
            allocationsJson = """[{"jobId":"1","amount":30}]""", paymentMethod = "Cash")
        val unknown = job(3).copy(notes = "")
        val upi = job(4).copy(notes = "Payment Method: UPI")
        val cash = FinancialCalculationEngine.cashAvailable(listOf(receipt), emptyList(), emptyList(), scope, listOf(legacy, unknown, upi))
        assertEquals(70.0, cash, 0.000001)
        val remaining = reconciledLegacyReceiptJobs("a", listOf(receipt), listOf(legacy)).single()
        assertEquals(40.0, remaining.amountReceived, 0.000001)
        assertEquals("Method not recorded", recordedLegacyPaymentMethod(unknown.notes))
        assertEquals("UPI / GPay", recordedLegacyPaymentMethod(upi.notes))
    }
}
