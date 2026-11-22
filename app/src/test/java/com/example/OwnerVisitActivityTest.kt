package com.example

import com.example.data.entity.AppNotificationEntity.Companion as Event
import com.example.data.firebase.FirebaseEnvironment
import com.example.data.firebase.WorkspaceMember
import com.example.data.sync.OwnerActivityEvidence
import com.example.data.sync.OwnerVisit
import com.example.data.sync.OwnerVisitActivity
import com.example.data.sync.OwnerVisitKey
import com.example.data.sync.OwnerVisitStatus
import com.example.ui.util.NotificationPresenter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class OwnerVisitActivityTest {
    private val key = OwnerVisitKey("workspace", "original-owner")
    private val time = 1_767_225_600_000L
    private val members = listOf(
        WorkspaceMember(uid = "m", workspaceId = "workspace", role = "PARTNER", status = "ACTIVE", displayName = "Perumal"),
        WorkspaceMember(uid = "s", workspaceId = "workspace", role = "PARTNER", status = "active", displayName = "Sriram"),
        WorkspaceMember(uid = "original-owner", workspaceId = "workspace", role = "OWNER", status = "ACTIVE"),
        WorkspaceMember(uid = "removed", workspaceId = "workspace", role = "PARTNER", status = "REMOVED")
    )

    private fun record(source: String, number: Long, op: String, actor: String = "m",
                       fields: Map<String, Any?> = emptyMap(), created: Boolean = true,
                       docId: String = number.toString()) = OwnerActivityEvidence(
        source, docId, mapOf(
            "workspaceId" to "workspace", "id" to number, "protocolVersion" to 2L,
            "operationId" to op, "actorUid" to actor, "createdByUid" to actor,
            "actionTime" to time, "operationType" to "UPSERT", "isDeleted" to false,
            "authorizationTimeStatus" to "CLIENT_ORIGINAL_V2"
        ) + fields, "create", if (created) "create" else "update", time + 2000
    )

    @Test fun onlyVerifiedSupportedActivitiesFromActivePartnersAppear() {
        val audit = mapOf("before" to mapOf("notes" to "old"), "after" to mapOf("customerName" to "Customer 1"),
            "actorUid" to "s", "operationId" to "edit-job", "actionTime" to time)
        val job = record("entries", 1, "job", fields = mapOf("isHistorical" to false))
        val rows = listOf(
            job, job,
            record("entries", 2, "old-entry", fields = mapOf("isHistorical" to true)),
            record("entries", 3, "overwrite", "s", mapOf("isHistorical" to true, "jobEditHistory" to listOf(audit)), false),
            record("entryDeletionProofs", 3, "delete-job", "s", mapOf("recordId" to "3", "deletedJobEditHistory" to listOf(audit))),
            record("collections", 5, "collection", fields = mapOf("status" to "ACTIVE", "collectedByUid" to "m")),
            record("collections", 6, "reverse", "s", mapOf("status" to "REVERSED", "reversedByUid" to "s")),
            record("expenses", 7, "expense"),
            record("expenses", 8, "expense-edit", created = false),
            record("cashDeleteProofs", 8, "expense-delete", fields = mapOf("kind" to "EXPENSE", "recordId" to "8"), docId = "EXPENSE_8"),
            record("withdrawals", 9, "withdraw", fields = mapOf("targetPartnerUid" to "m")),
            record("cashBalanceAdjustments", 11, "cash-adjust", docId = "cash-adjust", fields = mapOf(
                "revision" to 1L, "beforePaise" to 100000L, "afterPaise" to 100001L,
                "deltaPaise" to 1L, "confirmedAt" to time, "reason" to "Partner test")),
            record("withdrawals", 10, "borrowed", fields = mapOf("targetPartnerUid" to "m", "borrowingRequestId" to 8L)),
            record("expenses", 11, "tombstone", fields = mapOf("isDeleted" to true, "operationType" to "DELETE")),
            record("expenses", 12, "loan-expense", fields = mapOf("paidBy" to "Loan Funded")),
            record("expenses", 13, "owner-action", "original-owner"),
            record("expenses", 14, "removed-partner", "removed"),
            record("expenses", 15, "foreign", fields = mapOf("workspaceId" to "different")),
            record("notifications", 1, "unauthenticated-report"),
            record("auditEvents", 1, "forged-assertion"),
        )
        val results = OwnerVisitActivity.verified(key, members, rows)
        assertEquals(11, results.size)
        assertEquals(OwnerVisitActivity.eventTypes, results.map { it.item.eventType }.toSet())
        assertEquals(setOf("Perumal", "Sriram"), results.map { it.item.actorName }.toSet())
        assertTrue(results.all { it.item.createdAt == time && it.item.recipientUid == key.ownerUid && !it.item.isRead })
        assertEquals(results.size, results.map { it.item.id }.toSet().size)
        val otherOwner = OwnerVisitActivity.verified(key.copy(ownerUid = "another-owner"), members, listOf(job)).single()
        assertNotEquals(results.first().item.id, otherOwner.item.id)
        assertTrue(OwnerVisitActivity.verified(key.copy(workspaceId = "different"), members, listOf(job)).isEmpty())
    }

    @Test fun confirmedCashAdjustmentsPreserveOwnerOnlyPolicyIdentityAndPaisa() {
        val row = OwnerActivityEvidence("cashBalanceAdjustments", "cash-op", mapOf(
            "workspaceId" to key.workspaceId, "operationId" to "cash-op", "actorUid" to "m",
            "revision" to 3L, "beforePaise" to 128667L, "afterPaise" to 128767L,
            "deltaPaise" to 100L, "confirmedAt" to time, "actionTime" to time - 7 * 86400000L,
            "reason" to "சோதனை / original user text"
        ), "confirmed", "confirmed", time + 2000)
        val result = OwnerVisitActivity.verified(key, members, listOf(row, row))
        assertEquals(1, result.size)
        val item = result.single().item
        assertEquals(Event.EVENT_CASH_BALANCE_ADJUSTED, item.eventType)
        assertEquals(key.ownerUid, item.recipientUid)
        assertEquals("m", item.actorUid)
        assertEquals(3L, item.entityId)
        assertEquals(time, item.createdAt)
        assertEquals(1.0, item.amount, 0.0)
        val english = NotificationPresenter.present(item, false)
        val tamil = NotificationPresenter.present(item, true)
        assertEquals("Perumal adjusted available cash", english.title)
        assertEquals("Perumal கிடைக்கும் ரொக்க இருப்பைத் திருத்தினார்", tamil.title)
        assertTrue(english.body.contains("1,286.67") && english.body.contains("1,287.67"))
        assertTrue(english.body.contains("சோதனை / original user text"))
        assertTrue(tamil.body.contains("சோதனை / original user text"))
        val invalid = listOf(
            row.copy(data = row.data + ("actorUid" to key.ownerUid)),
            row.copy(data = row.data + ("actorUid" to "removed")),
            row.copy(data = row.data + ("actorUid" to "operator")),
            row.copy(data = row.data + ("workspaceId" to "different")),
            row.copy(documentId = "wrong-operation"), row.copy(serverUpdated = "edited"),
            row.copy(data = row.data - "confirmedAt"),
            row.copy(data = row.data + ("confirmedAt" to time + 2001)),
            row.copy(data = row.data + ("deltaPaise" to 0L)),
            row.copy(data = row.data + ("afterPaise" to 128768L)),
            row.copy(data = row.data + ("beforePaise" to 128667.0)),
            row.copy(data = row.data + ("reason" to "  "))
        )
        for (bad in invalid) assertTrue(OwnerVisitActivity.verified(key, members, listOf(bad)).isEmpty())
    }

    @Test fun firstVisitAnchorsOnlyAfterSuccessfulCloudReadAndRemainsFixedWithinSession() {
        val first = OwnerVisit(key, null, null, OwnerVisitStatus.OFFLINE)
        assertNull(first.since)
        assertNull(first.copy(status = OwnerVisitStatus.FAILED).lastUpdated)
        val established = first.refreshed(time, emptySet())
        val reconnected = established.copy(status = OwnerVisitStatus.OFFLINE).refreshed(time + 20, setOf("one", "two"))
        assertEquals(time, reconnected.since)
        assertEquals(time + 20, reconnected.lastUpdated)
        assertEquals(setOf("one", "two"), reconnected.recentIds)
        assertEquals(time + 20, reconnected.refreshed(time - 1, emptySet()).lastUpdated)
        assertNotEquals(key.preferenceKey, key.copy(workspaceId = "other").preferenceKey)
        assertNotEquals(key.preferenceKey, key.copy(ownerUid = "other").preferenceKey)
    }

    @Test fun localizedDisplayUsesIstAndPreservesNames() {
        val item = OwnerVisitActivity.verified(key, members, listOf(
            record("entries", 1, "job", fields = mapOf("isHistorical" to false, "customerName" to "விவசாயி"))
        )).single().item
        val english = NotificationPresenter.present(item, false)
        val tamil = NotificationPresenter.present(item, true)
        assertEquals("Perumal added a new job", english.title)
        assertEquals("Perumal புதிய வேலைப் பதிவைச் சேர்த்தார்", tamil.title)
        assertEquals("விவசாயி", english.body)
        assertEquals(english.body, tamil.body)
        assertTrue(english.timestampLabel.contains("05:30 AM IST"))
        assertTrue(tamil.timestampLabel.endsWith("IST"))
    }

    @Test fun legacyMemberIdentityComesOnlyFromVerifiedDocumentPath() {
        val legacy = record("members", 1, "unused", fields = mapOf(
            "uid" to "original-owner", "role" to "OWNER", "status" to "ACTIVE"
        ), docId = "original-owner").let { it.copy(data = it.data - "workspaceId") }
        assertFalse(legacy.data.containsKey("workspaceId"))
        val owner = com.example.data.firebase.OwnerVisitCloud.verifiedMember(legacy, key.workspaceId)
        assertEquals(key.ownerUid, owner?.uid)
        assertEquals(key.workspaceId, owner?.workspaceId)
        assertNull(com.example.data.firebase.OwnerVisitCloud.verifiedMember(
            legacy.copy(data = legacy.data + ("workspaceId" to "foreign")), key.workspaceId))
        assertNull(com.example.data.firebase.OwnerVisitCloud.verifiedMember(
            legacy.copy(data = legacy.data + ("uid" to "another-person")), key.workspaceId))
    }

    @Test fun safeRefreshDiagnosticsDoNotIncludeExceptionContent() {
        val payload = "private customer content or bearer token must stay out of logs"
        val classified = com.example.data.firebase.OwnerVisitCloud.safeFailure(java.io.IOException(payload))
        assertEquals("NETWORK/UNAVAILABLE", classified)
        assertFalse(classified.contains(payload))
        val visit = OwnerVisit(key, time, time, OwnerVisitStatus.READY)
        val partial = visit.copy(status = OwnerVisitStatus.PARTIAL, diagnostics = setOf("EXPENSES/PERMISSION"))
        assertEquals(time, partial.lastUpdated)
        assertEquals(time, partial.since)
    }

    @Test fun physicalPilotPermitsOnlyDedicatedWorkspace() {
        assertTrue(FirebaseEnvironment.permitsWorkspaceDiscovery("ws_notify_pilot_4a2ff95fafcb8b", false, true))
        assertFalse(FirebaseEnvironment.permitsWorkspaceDiscovery("normal-customer-workspace", false, true))
        assertFalse(FirebaseEnvironment.permitsWorkspaceDiscovery(FirebaseEnvironment.STAGING_WORKSPACE, false, true))
    }
}
