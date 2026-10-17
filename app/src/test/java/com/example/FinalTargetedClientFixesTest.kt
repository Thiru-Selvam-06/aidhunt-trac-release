package com.example

import com.example.data.entity.JobEntryEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.firebase.WorkspaceMember
import com.example.data.firebase.toFirestoreMap
import com.example.data.firebase.jobEntryFromFirestoreMap
import com.example.ui.util.*
import org.junit.Assert.*
import org.junit.Test

class FinalTargetedClientFixesTest {
    private val now = System.currentTimeMillis()
    private fun job(id: Long, created: Long, start: Long = now) = JobEntryEntity(
        id = id, workspaceId = "w1", customerName = "Test", customerPhone = "",
        operatorName = "Partner", tractorLabel = "Test", workType = "Test",
        startTimeMillis = start, endTimeMillis = start + 60000, durationMinutes = 1,
        hourlyRate = 100.0, totalAmount = 100.0, amountReceived = 0.0, pendingAmount = 100.0,
        addedByPartner = "Partner", createdAt = created, createdByUid = "p", createdByRole = "Partner"
    )
    private val members = listOf(
        WorkspaceMember(workspaceId = "w1", status = "ACTIVE", role = "OWNER", uid = "o", displayName = "owner of 89999"),
        WorkspaceMember(workspaceId = "w1", status = "ACTIVE", role = "PARTNER", uid = "a", displayName = "adam"),
        WorkspaceMember(workspaceId = "w1", status = "ACTIVE", role = "PARTNER", uid = "b", displayName = "inbha"),
        WorkspaceMember(workspaceId = "w1", status = "ACTIVE", role = "PARTNER", uid = "c", displayName = "mi")
    )
    private fun withdrawal(id: Long, uid: String, amount: Double, name: String = uid, ws: String = "w1") =
        WithdrawalEntity(id = id, workspaceId = ws, partnerName = name, amount = amount,
            targetPartnerUid = uid, createdByUid = "o", category = "Withdrawal")

    @Test fun recentJobsUseCreationNotWorkTime() {
        val a = job(1, now - 600000, now)
        val b = job(2, now - 300000, now - 3600000)
        val c = job(3, now, now - 7200000)
        assertEquals(listOf(3L, 2L, 1L), listOf(a, c, b).sortedWith(RecentJobOrder).map { it.id })
    }
    @Test fun offlineCreationSurvivesCloudMappingWithoutDateOrIdentityChanges() {
        val offline = job(3, now, now - 3 * 86400000L).copy(isSynced = false)
        val remote = jobEntryFromFirestoreMap(offline.toFirestoreMap())
        assertEquals(offline.id, remote.id)
        assertEquals(offline.createdAt, remote.createdAt)
        assertEquals(offline.createdByUid, remote.createdByUid)
        assertEquals(offline.operatorName, remote.operatorName)
        assertTrue(isRecentJobToday(remote, now))
        assertEquals(3L, listOf(job(2, now - 300000), remote).sortedWith(RecentJobOrder).first().id)
    }
    @Test fun newJobPrecedesOlderJobsAndTimestampTiesAreStable() {
        val older = listOf(job(1, now - 300000), job(2, now - 300000))
        assertEquals(listOf(2L, 1L), older.sortedWith(RecentJobOrder).map { it.id })
        assertEquals(listOf(3L, 2L, 1L), (older + job(3, now)).sortedWith(RecentJobOrder).map { it.id })
    }
    @Test fun ownerRenamingDoesNotLose2050AndTotalRemains8700() {
        val records = listOf(withdrawal(1, "o", 2050.0, "owner of 89"), withdrawal(2, "o", 500.0, "owner of 89999"),
            withdrawal(3, "a", 2050.0), withdrawal(4, "b", 2050.0), withdrawal(5, "c", 2050.0))
        val result = withdrawalDistribution("w1", records, members)
        assertEquals(4, result.participants.size)
        assertEquals(2550.0, result.participants.single { it.uid == "o" }.amount, 0.0)
        assertEquals(8700.0, result.total, 0.0)
        assertEquals(result.total, result.participants.sumOf { it.amount }, 0.0)
        assertEquals(0.0, result.unresolvedAmount, 0.0)
    }
    @Test fun sameNamesStayDistinctAndSameUidCannotDuplicate() {
        val people = listOf(members[0].copy(displayName = "Same"), members[1].copy(displayName = "Same"), members[0])
        val result = withdrawalDistribution("w1", listOf(withdrawal(1, "o", 500.0), withdrawal(2, "a", 600.0)), people)
        assertEquals(setOf("o", "a"), result.participants.map { it.uid }.toSet())
        assertEquals(2, result.participants.size)
    }
    @Test fun foreignWorkspaceAndUnknownUidCannotBecomeParticipants() {
        val records = listOf(withdrawal(1, "o", 500.0), withdrawal(2, "o", 999.0, ws = "w2"),
            withdrawal(3, "foreign", 40.0, "adam"), withdrawal(4, "", 60.0, "owner of 89999"))
        val result = withdrawalDistribution("w1", records, members)
        assertEquals(listOf("o"), result.participants.map { it.uid })
        assertEquals(600.0, result.total, 0.0)
        assertEquals(100.0, result.unresolvedAmount, 0.0)
        assertEquals(result.total, result.participants.sumOf { it.amount } + result.unresolvedAmount, 0.0)
        assertEquals(0.0, withdrawalDistribution("", records, members).total, 0.0)
    }
    @Test fun inactiveMembersAreUnresolvedNotReassignedToOwner() {
        val result = withdrawalDistribution("w1", listOf(withdrawal(1, "a", 500.0)), members.map { if (it.uid == "a") it.copy(status = "inactive") else it })
        assertTrue(result.participants.isEmpty())
        assertEquals(500.0, result.unresolvedAmount, 0.0)
    }
    @Test fun blankAndWhitespacePhonesAreOptionalAndNormalizedEmpty() {
        listOf("", "   ").forEach { assertTrue(isOptionalContactPhoneValid(it)); assertEquals("", requireOptionalContactPhone(it)) }
    }
    @Test fun partialPhonesAreRejectedBeforePersistence() {
        listOf("3", "34", "9876", "987654").forEach {
            assertFalse(isOptionalContactPhoneValid(it))
            assertThrows(IllegalArgumentException::class.java) { requireOptionalContactPhone(it) }
        }
    }
    @Test fun supportedFormatsKeepExistingNormalization() {
        listOf("9876543210", "+91 98765 43210", "09876543210", "00919876543210").forEach {
            assertTrue(isOptionalContactPhoneValid(it))
            assertEquals("9876543210", requireOptionalContactPhone(it))
        }
    }
    @Test fun clearingPartialPhoneClearsValidationError() {
        assertFalse(isOptionalContactPhoneValid("9876"))
        assertTrue(isOptionalContactPhoneValid(""))
    }
    @Test fun phoneInvitationsStillRequireCompleteNumber() {
        listOf("", "   ", "9876").forEach { assertFalse(isInvitationPhoneValid(it)) }
        assertTrue(isInvitationPhoneValid("9876543210"))
        assertTrue(isInvitationPhoneValid("+91 98765 43210"))
    }
}
