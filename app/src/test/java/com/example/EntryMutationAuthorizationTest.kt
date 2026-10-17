package com.example

import com.example.data.auth.AccessControlDocument
import com.example.data.auth.AuthorizationManager
import com.example.data.auth.RoleUtils
import com.example.data.entity.JobEntryEntity
import com.example.data.repository.effectiveEntryStartTimeForEdit
import com.example.data.firebase.jobEntryFromFirestoreMap
import com.example.data.firebase.toFirestoreMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EntryMutationAuthorizationTest {
    private val createdAt = 1_000_000L
    private val windowMillis = 24 * 60 * 60 * 1000L

    @Before
    fun resetAuthorizationState() {
        AuthorizationManager.currentAccessControl = AccessControlDocument()
    }

    private fun job(
        creatorUid: String = "partner-uid",
        creatorRole: String = RoleUtils.ROLE_PARTNER,
        createdAtMillis: Long = createdAt,
        startTimeMillis: Long = createdAt
    ) = JobEntryEntity(
        workspaceId = "workspace-1",
        customerName = "Customer",
        operatorName = "Partner",
        tractorLabel = "Tractor",
        workType = "Ploughing",
        startTimeMillis = startTimeMillis,
        endTimeMillis = startTimeMillis + 60_000L,
        durationMinutes = 1L,
        totalAmount = 100.0,
        amountReceived = 0.0,
        pendingAmount = 100.0,
        addedByPartner = "Partner",
        createdAt = createdAtMillis,
        createdByUid = creatorUid,
        createdByRole = creatorRole
    )

    private fun canEdit(entry: JobEntryEntity, uid: String, role: String, now: Long) =
        AuthorizationManager.canEditEntry(entry, false, "Partner", uid, now, role)

    private fun canDelete(entry: JobEntryEntity, uid: String, role: String, now: Long) =
        AuthorizationManager.canDeleteEntry(entry, false, "Partner", uid, now, role)

    @Test
    fun testPartnerCanEditOwnEntryWithin24Hours() {
        assertTrue(canEdit(job(), "partner-uid", RoleUtils.ROLE_PARTNER, createdAt + windowMillis - 1L))
    }

    @Test
    fun testPartnerCannotEditOwnEntryAfter24Hours() {
        assertFalse(canEdit(job(), "partner-uid", RoleUtils.ROLE_PARTNER, createdAt + windowMillis + 1L))
    }

    @Test
    fun testPartnerCanDeleteOwnEntryWithin24Hours() {
        assertTrue(canDelete(job(), "partner-uid", RoleUtils.ROLE_PARTNER, createdAt + windowMillis - 1L))
    }

    @Test
    fun testPartnerCannotDeleteOwnEntryAfter24Hours() {
        assertFalse(canDelete(job(), "partner-uid", RoleUtils.ROLE_PARTNER, createdAt + windowMillis + 1L))
    }

    @Test
    fun testPartnerCannotEditAnotherUsersEntry() {
        assertFalse(canEdit(job(), "another-uid", RoleUtils.ROLE_PARTNER, createdAt + 1L))
    }

    @Test
    fun testPartnerCannotDeleteAnotherUsersEntry() {
        assertFalse(canDelete(job(), "another-uid", RoleUtils.ROLE_PARTNER, createdAt + 1L))
    }

    @Test
    fun testPartnerOwnershipUsesCreatorUidInsteadOfStoredCreatorRole() {
        val entryCreatedUnderFormerOwnerRole = job(creatorRole = RoleUtils.ROLE_OWNER)
        assertTrue(canEdit(entryCreatedUnderFormerOwnerRole, "partner-uid", RoleUtils.ROLE_PARTNER, createdAt + 1L))
        assertTrue(canDelete(entryCreatedUnderFormerOwnerRole, "partner-uid", RoleUtils.ROLE_PARTNER, createdAt + 1L))
    }

    @Test
    fun testOperatorCanEditOwnEntryWithin24Hours() {
        assertTrue(canEdit(job(creatorRole = RoleUtils.ROLE_OPERATOR), "partner-uid", RoleUtils.ROLE_OPERATOR, createdAt + 1L))
    }

    @Test
    fun testOperatorCannotEditOwnEntryAfter24Hours() {
        assertFalse(canEdit(job(creatorRole = RoleUtils.ROLE_OPERATOR), "partner-uid", RoleUtils.ROLE_OPERATOR, createdAt + windowMillis + 1L))
    }

    @Test
    fun testOperatorCannotEditOwnEntryAt24Hours() {
        assertFalse(canEdit(job(creatorRole = RoleUtils.ROLE_OPERATOR), "partner-uid", RoleUtils.ROLE_OPERATOR, createdAt + windowMillis))
    }

    @Test
    fun testOperatorCanDeleteOwnOrdinaryEntryBefore24Hours() {
        assertTrue(canDelete(job(creatorRole = RoleUtils.ROLE_OPERATOR), "partner-uid", RoleUtils.ROLE_OPERATOR, createdAt + 1L))
    }

    @Test
    fun testOperatorCannotEditAnotherUsersEntry() {
        assertFalse(canEdit(job(creatorRole = RoleUtils.ROLE_OPERATOR), "another-uid", RoleUtils.ROLE_OPERATOR, createdAt + 1L))
    }

    @Test
    fun testOwnerCanEditAnyEntryAfter24Hours() {
        assertTrue(
            AuthorizationManager.canEditEntry(
                job(), true, "Owner", "owner-uid", createdAt + windowMillis + 1L, RoleUtils.ROLE_OWNER
            )
        )
    }

    @Test
    fun testOwnerCanDeleteAnyEntryAfter24Hours() {
        assertTrue(
            AuthorizationManager.canDeleteEntry(
                job(), true, "Owner", "owner-uid", createdAt + windowMillis + 1L, RoleUtils.ROLE_OWNER
            )
        )
    }

    @Test
    fun testLegacyCoOwnerRoleGrantsNoMutationAuthority() {
        val oldEntry = job()
        val now = createdAt + windowMillis + 1L
        assertFalse(AuthorizationManager.canEditEntry(oldEntry, true, "Co-Owner", "co-owner-uid", now, RoleUtils.ROLE_CO_OWNER))
        assertFalse(AuthorizationManager.canDeleteEntry(oldEntry, true, "Co-Owner", "co-owner-uid", now, RoleUtils.ROLE_CO_OWNER))
    }

    @Test
    fun testMutationDeniedAtExactly24Hours() {
        val exactlyAtBoundary = createdAt + windowMillis
        assertFalse(canEdit(job(), "partner-uid", RoleUtils.ROLE_PARTNER, exactlyAtBoundary))
        assertFalse(canDelete(job(), "partner-uid", RoleUtils.ROLE_PARTNER, exactlyAtBoundary))
    }

    @Test
    fun testLegacyEntryWithoutCreationTimeRequiresVerifiedMigration() {
        val legacyEntry = job(createdAtMillis = 0L, startTimeMillis = createdAt)
        assertFalse(canEdit(legacyEntry, "partner-uid", RoleUtils.ROLE_PARTNER, createdAt + windowMillis - 1L))
        assertFalse(canEdit(legacyEntry, "partner-uid", RoleUtils.ROLE_PARTNER, createdAt + windowMillis))
    }

    @Test
    fun testLegacyFirestoreEntryNeverUsesBusinessDateAsAuthorizationTime() {
        val legacyMap = job(createdAtMillis = 0L).toFirestoreMap().toMutableMap().apply {
            remove("createdAt")
        }
        val mappedEntry = jobEntryFromFirestoreMap(legacyMap)

        assertFalse(canEdit(mappedEntry, "partner-uid", RoleUtils.ROLE_PARTNER, createdAt + windowMillis - 1L))
        assertFalse(canEdit(mappedEntry, "partner-uid", RoleUtils.ROLE_PARTNER, createdAt + windowMillis))
    }

    @Test
    fun testLegacyOfflineEditAndPendingSyncKeepOriginalEffectiveCreationTimestamp() {
        val persistedEntry = job(createdAtMillis = 0L, startTimeMillis = createdAt)
        val submittedEdit = persistedEntry.copy(
            startTimeMillis = createdAt + 30 * 60 * 60 * 1000L,
            updatedAt = createdAt + 30 * 60 * 60 * 1000L,
            notes = "edited offline",
            isSynced = false
        )

        val locallySaved = submittedEdit.copy(
            startTimeMillis = effectiveEntryStartTimeForEdit(persistedEntry, submittedEdit)
        )
        val pendingSyncPayload = locallySaved.toFirestoreMap()
        val synchronized = jobEntryFromFirestoreMap(pendingSyncPayload)

        assertEquals(createdAt, locallySaved.startTimeMillis)
        assertEquals(createdAt, pendingSyncPayload["startTimeMillis"])
        assertEquals(createdAt, synchronized.startTimeMillis)
        assertFalse(canEdit(synchronized, "partner-uid", RoleUtils.ROLE_PARTNER, createdAt + windowMillis))
    }

    @Test
    fun testValidCreatedAtAllowsNormalWorkTimeEditWithoutResettingCreationTime() {
        val persistedEntry = job(createdAtMillis = createdAt, startTimeMillis = createdAt)
        val submittedEdit = persistedEntry.copy(startTimeMillis = createdAt + 60 * 60 * 1000L)

        assertEquals(
            submittedEdit.startTimeMillis,
            effectiveEntryStartTimeForEdit(persistedEntry, submittedEdit)
        )
        assertEquals(createdAt, submittedEdit.createdAt)
        assertFalse(canEdit(submittedEdit, "partner-uid", RoleUtils.ROLE_PARTNER, createdAt + windowMillis))
    }

    @Test
    fun testRecentEditDoesNotResetCreationWindow() {
        val editedEntry = job().copy(updatedAt = createdAt + windowMillis - 1L)
        assertFalse(canEdit(editedEntry, "partner-uid", RoleUtils.ROLE_PARTNER, createdAt + windowMillis))
    }

    @Test
    fun testOfflineCopyAndFirestoreSynchronizationPreserveCreationTimestamp() {
        val offlineEdit = job().copy(
            notes = "Updated offline",
            updatedAt = createdAt + windowMillis - 1L,
            isSynced = false
        )
        val synchronized = jobEntryFromFirestoreMap(offlineEdit.toFirestoreMap()).copy(isSynced = true)

        assertTrue(canEdit(offlineEdit, "partner-uid", RoleUtils.ROLE_PARTNER, createdAt + windowMillis - 1L))
        assertEquals(createdAt, offlineEdit.createdAt)
        assertEquals(createdAt, synchronized.createdAt)
        assertFalse(canEdit(synchronized, "partner-uid", RoleUtils.ROLE_PARTNER, createdAt + windowMillis))
    }

    @Test
    fun testMissingCreatorUidCannotAuthorizeOfflineMutation() {
        val legacyEntryWithoutUid = job(creatorUid = "")
        assertFalse(canEdit(legacyEntryWithoutUid, "partner-uid", RoleUtils.ROLE_PARTNER, createdAt + 1L))
        assertFalse(canDelete(legacyEntryWithoutUid, "partner-uid", RoleUtils.ROLE_PARTNER, createdAt + 1L))
    }
}
