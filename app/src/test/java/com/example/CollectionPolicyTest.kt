package com.example

import com.example.data.auth.CollectionPolicy as Policy
import com.example.data.entity.*
import com.example.data.firebase.*
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.TimeZone

class CollectionPolicyTest {
    private fun member(uid: String, role: String = "PARTNER", ws: String = "a", status: String = "ACTIVE", name: String = uid) =
        WorkspaceMember(uid = uid, workspaceId = ws, role = role, status = status, displayName = name)
    private val members = listOf(member("owner", "OWNER"), member("a"), member("b"))
    private fun time(value: String) = Instant.parse(value).toEpochMilli()
    private fun payment(collector: String) = PaymentEntity(id = 9007199254740995L, workspaceId = "a", amount = 40.0,
        collectedByUid = "owner", collectedByName = "Owner audit snapshot", collectedByRole = "OWNER",
        collectorUid = collector, collectorName = "Snapshot $collector", collectorRole = "PARTNER", createdAt = 1000L)
    private fun denied(block: () -> Unit) { try { block(); fail("Expected validation failure") } catch (_: IllegalArgumentException) {} }
    @Test fun ownerSelectsBothPartnersWithoutChangingTheAuthenticatedRecorderOrFinancialFields() {
        for (uid in listOf("a", "b")) {
            val selected = Policy.requireCollector("a", "owner", true, uid, members)
            val stored = payment(selected.uid)
            assertEquals(uid, Policy.collector(stored, members).name)
            assertEquals("owner", Policy.recorder(stored, members).uid)
            assertEquals(40.0, stored.amount, 0.0)
        }
    }
    @Test fun partnerIsLockedToSelfAndAnOwnerCannotSelectInactiveForeignOrOperatorMembers() {
        assertEquals("a", Policy.requireCollector("a", "a", false, "a", members).uid)
        val invalid = listOf(member("inactive", status = "ARCHIVED"), member("foreign", ws = "b"), member("op", role = "OPERATOR"))
        for (uid in listOf("b", "missing", "inactive", "foreign", "op")) {
            try { Policy.requireCollector("a", "a", false, uid, members + invalid); fail("Partner selected $uid") } catch (_: SecurityException) {}
        }
        for (uid in listOf("inactive", "foreign", "op")) {
            try { Policy.requireCollector("a", "owner", true, uid, members + invalid); fail("Owner selected $uid") } catch (_: SecurityException) {}
        }
    }
    @Test fun sameNamesDoNotCollapseDifferentUidsAndForeignWorkspaceNamesAreNeverUsed() {
        val choices = listOf(member("a", name = "Same"), member("b", name = "Same"), member("a", ws = "foreign", name = "Wrong workspace"))
        assertEquals(listOf("a", "b"), Policy.eligibleCollectors("a", "owner", true, choices).map { it.uid })
        assertEquals("Same", Policy.collector(payment("a"), choices).name)
        assertEquals("Snapshot a", Policy.collector(payment("a"), choices.filter { it.workspaceId != "a" }).name)
    }
    @Test fun serializationRetainsSelectedCollectorRecorderAmountsAndExactIds() {
        val source = payment("b").copy(jobEntryId = 9007199254740997L, allocationsJson = "[{\"jobId\":\"9007199254740997\",\"amount\":40}]")
        val restored = paymentFromFirestoreMap(source.toFirestoreMap())
        assertEquals(source.copy(isSynced = true), restored)
        assertEquals("b", Policy.collector(restored, members).uid)
        assertEquals("owner", restored.collectedByUid)
    }
    @Test fun legacyCollectionHasNoFabricatedSelectedCollectorAndRemainsReadable() {
        val old = payment("a").copy(collectorUid = "", collectorName = "", collectorRole = "")
        assertFalse(old.toFirestoreMap().containsKey("collectorUid"))
        val restored = paymentFromFirestoreMap(old.toFirestoreMap())
        assertEquals("owner", Policy.collector(restored, members).uid)
        assertEquals("", restored.collectorUid)
    }
    @Test fun missingLegacyCollectionDateIsNotReplacedWithNow() {
        assertEquals(0L, paymentFromFirestoreMap(mapOf("id" to 1L)).collectedAt)
    }
    @Test fun earlierHourOnCreationDayAndLaterDaysAreAccepted() {
        val created = time("2026-10-15T17:00:00Z")
        Policy.requireDate(time("2026-10-14T18:30:00Z"), created)
        Policy.requireDate(time("2026-10-16T00:00:00Z"), created)
    }
    @Test fun previousIndiaDayAndMissingSourceCreationDateAreRejected() {
        denied { Policy.requireDate(time("2026-10-14T18:29:59Z"), time("2026-10-15T17:00:00Z")) }
        denied { Policy.requireDate(time("2026-10-15T00:00:00Z"), 0) }
        denied { Policy.requireDate(0, time("2026-10-15T00:00:00Z")) }
    }
    @Test fun indiaMidnightBoundaryIsIndependentOfThePhoneTimezone() {
        val original = TimeZone.getDefault()
        try {
            for (zone in listOf("America/Los_Angeles", "UTC", "Pacific/Auckland")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone))
                Policy.requireDate(time("2026-10-15T18:30:00Z"), time("2026-10-16T06:00:00Z"))
                denied { Policy.requireDate(time("2026-10-15T18:29:59Z"), time("2026-10-16T06:00:00Z")) }
            }
        } finally { TimeZone.setDefault(original) }
    }
    @Test fun splitAllocationUsesEveryAllocatedSourceAndIgnoresOtherCustomersAndWorkspaces() {
        val first = JobEntryEntity(id = 1, workspaceId = "a", customerId = 2, customerName = "Test", operatorName = "a", tractorLabel = "T",
            workType = "Work", startTimeMillis = 1, endTimeMillis = 2, durationMinutes = 1, totalAmount = 100.0,
            amountReceived = 0.0, pendingAmount = 100.0, addedByPartner = "a", createdAt = time("2026-10-14T01:00:00Z"))
        val later = first.copy(id = 2, startTimeMillis = 2, createdAt = time("2026-10-16T01:00:00Z"))
        val jobs = listOf(first, later, later.copy(workspaceId = "b"), later.copy(customerId = 3))
        assertEquals(listOf(first), Policy.allocatedJobs(jobs, "a", 2, 100.0))
        val split = Policy.allocatedJobs(jobs, "a", 2, 150.0)
        assertEquals(listOf(first, later), split)
        assertEquals(Policy.startOfDay(later.createdAt), Policy.earliestDate(split))
        denied { Policy.requireDate(time("2026-10-15T01:00:00Z"), Policy.earliestDate(split)) }
    }
}
