package com.example

import com.example.data.auth.AuthorizationManager
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
import com.example.data.firebase.expenseFromFirestoreMap
import com.example.data.firebase.withdrawalFromFirestoreMap
import com.example.ui.screens.report.*
import com.example.ui.util.*
import java.time.Instant
import org.junit.Assert.*
import org.junit.Test

class PhaseTwoPolicyTest {
    private fun at(iso: String) = Instant.parse(iso).toEpochMilli()
    private val now = at("2026-11-15T10:00:00Z")
    private val job = JobEntryEntity(id = 1, workspaceId = "a", customerName = "Field", operatorName = "Driver",
        tractorLabel = "T", workType = "Rotavator", startTimeMillis = at("2026-10-08T10:00:00Z"),
        endTimeMillis = at("2026-10-08T11:00:00Z"), durationMinutes = 60L, totalAmount = 1000.0,
        amountReceived = 300.0, pendingAmount = 700.0, addedByPartner = "Owner",
        createdByUid = "owner", entryForUid = "operator", financialAttributionUid = "operator")

    @Test fun ownerAndPartnerMayEditAcrossCreatorsAndAgesWhileOperatorsRemainRestricted() {
        for (days in listOf(2L, 7L, 31L, 365L)) for (historical in listOf(false, true)) {
            for (creator in listOf("owner", "partner", "other")) {
                val source = job.copy(createdByUid = creator, createdAt = now - days * 86400000L, isHistorical = historical)
                assertTrue(AuthorizationManager.canEditEntry(source, true, "Owner", "owner", now, "Owner"))
                assertTrue(AuthorizationManager.canEditEntry(source, false, "Partner", "partner", now, "Partner"))
                assertFalse(AuthorizationManager.canEditEntry(source, false, "Operator", "operator", now, "Operator"))
                assertFalse(AuthorizationManager.canEditEntry(source.copy(createdByUid = "operator"), false, "Operator", "operator", now, "Operator"))
            }
        }
        assertTrue(AuthorizationManager.canEditEntry(job.copy(createdByUid = "operator", createdAt = now - 1000), false, "Operator", "operator", now, "Operator"))
        assertFalse(AuthorizationManager.canEditEntry(job.copy(createdByUid = "operator", createdAt = now - 1000, isHistorical = true), false, "Operator", "operator", now, "Operator"))
    }

    @Test fun wholeMonthAndRelativePresetsUseIstBoundaries() {
        val october = ReportDateFilter(ReportPeriod.MONTH_YEAR, reportDay(2026, 9, 1)).bounds(now)
        val first = at("2026-09-30T18:30:00Z"); val next = at("2026-10-31T18:30:00Z")
        assertFalse(reportDateMatches(first - 1, october)); assertTrue(reportDateMatches(first, october))
        assertTrue(reportDateMatches(next - 1, october)); assertFalse(reportDateMatches(next, october))
        assertEquals(october, ReportDateFilter(ReportPeriod.LAST_MONTH).bounds(now))
        assertTrue(reportDateMatches(at("2026-11-30T17:00:00Z"), ReportDateFilter(ReportPeriod.THIS_MONTH).bounds(now)))
        val todayStart = at("2026-11-14T18:30:00Z")
        assertTrue(reportDateMatches(todayStart, ReportDateFilter(ReportPeriod.TODAY).bounds(now)))
        assertTrue(reportDateMatches(todayStart - 1, ReportDateFilter(ReportPeriod.YESTERDAY).bounds(now)))
        assertFalse(reportDateMatches(todayStart, ReportDateFilter(ReportPeriod.YESTERDAY).bounds(now)))
    }

    @Test fun rangeIncludesBothDaysAcrossMonthsAndYearsAndRejectsReversedDays() {
        fun check(from: Long, to: Long, days: Long) {
            val bounds = ReportDateFilter(ReportPeriod.CUSTOM_RANGE, from, to).bounds(now)!!
            assertEquals(from, bounds.first); assertEquals(days * 86400000L, bounds.last - bounds.first + 1)
            assertTrue(reportDateMatches(to + 86400000L - 1, bounds))
            assertFalse(reportDateMatches(from - 1, bounds))
            assertFalse(reportDateMatches(to + 86400000L, bounds))
        }
        check(reportDay(2026, 10, 5), reportDay(2026, 10, 6), 2)
        check(reportDay(2026, 10, 5), reportDay(2026, 10, 8), 4)
        check(reportDay(2026, 9, 20), reportDay(2026, 10, 10), 22)
        check(reportDay(2026, 11, 29), reportDay(2027, 0, 3), 6)
        assertTrue(ReportDateFilter(ReportPeriod.CUSTOM_RANGE, reportDay(2026, 10, 6), reportDay(2026, 10, 5)).bounds(now)!!.isEmpty())
        assertEquals(1L, ReportDateFilter(ReportPeriod.SPECIFIC_DATE, reportDay(2026, 10, 5)).bounds(now)!!.let {
            (it.last - it.first + 1) / 86400000L
        })
    }

    @Test fun missingTransactionDatesAreNeverInferredFromCreation() {
        val expense = expenseFromFirestoreMap(mapOf("createdAt" to now))
        val withdrawal = withdrawalFromFirestoreMap(mapOf("createdAt" to now))
        val monthly = ReportDateFilter(ReportPeriod.THIS_MONTH).bounds(now)
        for (date in listOf(expense.dateTimestamp, withdrawal.timestamp, 0L)) {
            assertEquals(0L, date); assertFalse(reportDateMatches(date, monthly))
            assertTrue(reportDateMatches(date, ReportDateFilter().bounds(now)))
        }
    }

    @Test fun sortUsesLatestMatchingCollectionAndAllocatedAmountsAfterSearchFiltering() {
        val october = at("2026-10-09T10:00:00Z")
        val november = at("2026-11-13T10:00:00Z")
        val groups = (1L..3L).map { id ->
            CollectionJobHistory("a-$id", id, job.copy(id = id), emptyList())
        }
        fun row(group: Int, id: Long, date: Long, amount: Double, fullAmount: Double = amount) =
            PaymentDisplayModel(id, "a-$group", fullAmount, true, "Field", amount, "Cash",
                date, "collector", "Collector", "Partner", "")
        val splitA = row(1, 8, october, 80.0, 120.0)
        val splitB = row(2, 8, october, 40.0, 120.0)
        val later = row(2, 9, november, 20.0)
        val undated = row(3, 10, 0, 5.0)
        val kept = listOf(splitA, splitB, later, undated).groupBy { it.groupKey }
        fun order(sort: CollectionSort, filtered: Map<String, List<PaymentDisplayModel>> = kept) =
            sortCollectionHistory(groups, filtered, sort).map { it.jobId }
        assertEquals(listOf(2L, 1L, 3L), order(CollectionSort.NEWEST))
        assertEquals(listOf(1L, 2L, 3L), order(CollectionSort.OLDEST))
        assertEquals(listOf(1L, 2L, 3L), order(CollectionSort.HIGHEST))
        assertEquals(listOf(3L, 2L, 1L), order(CollectionSort.LOWEST))
        assertEquals(listOf(1L, 2L), order(CollectionSort.NEWEST, listOf(splitA, splitB).groupBy { it.groupKey }))
        assertEquals(2, listOf(splitA, splitB, later).distinctBy { it.id }.size)
        assertEquals(140.0, listOf(splitA, splitB, later).sumOf { it.amount }, 0.0)
    }
}
