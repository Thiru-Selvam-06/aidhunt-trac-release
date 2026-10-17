package com.example

import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthorizationManager
import com.example.data.firebase.FirestoreRepository
import com.example.data.firebase.WorkspaceMember
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PendingInvitationCleanupContractTest {

    @Test
    fun waitingMember_isDistinctFromConnectedMember() {
        val pending = WorkspaceMember(
            uid = "",
            status = "waiting_for_registration",
            phoneNumber = "+918925624885",
            addedByUid = "owner_uid"
        )
        val connected = pending.copy(uid = "partner_uid", status = "active")

        assertTrue(pending.status.equals("waiting_for_registration", ignoreCase = true))
        assertTrue(pending.uid.isBlank())
        assertFalse(connected.status.equals("waiting_for_registration", ignoreCase = true))
        assertEquals("partner_uid", connected.uid)
    }

    @Test
    fun nonOwner_cannotManagePartners_orPendingInvitations() {
        assertFalse(AuthorizationManager.canManagePartners(isOwner = false))
    }

    @Test
    fun invalidPendingIdentity_isRejectedBeforeCloudAccess() = runBlocking {
        val repository = FirestoreRepository(
            ApplicationProvider.getApplicationContext()
        )

        val result = repository.deletePendingPartnerInvitation(
            workspaceId = "",
            normalizedPhone = "",
            actorUid = ""
        )

        assertTrue(result.isFailure)
    }
}
