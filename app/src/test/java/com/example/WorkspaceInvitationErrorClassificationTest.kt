package com.example

import com.example.data.firebase.WorkspaceInvitationService
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkspaceInvitationErrorClassificationTest {
    private fun error(status: Int, body: String) = WorkspaceInvitationService.mapBackendError(status, body)
    @Test fun http403IsAuthorizationRegardlessOfBody() {
        for (body in listOf("", "malformed", "{}")) {
            val e = error(403, body)
            assertTrue(e is SecurityException)
            assertEquals(WorkspaceInvitationService.OWNER_ONLY_ERROR, e.message)
        }
    }
    @Test fun explicitPermissionStatusIsAuthorizationWithoutOwnerWords() {
        for (status in listOf("PERMISSION_DENIED", "permission-denied")) {
            val e = error(400, """{"error":{"status":"$status","message":"Not allowed"}}""")
            assertTrue(e is SecurityException)
            assertEquals(WorkspaceInvitationService.OWNER_ONLY_ERROR, e.message)
        }
    }
    @Test fun ownerWordIn400MessageIsStillValidation() {
        val e = error(400, """{"error":{"status":"INVALID_ARGUMENT","message":"Owner display name is too long"}}""")
        assertTrue(e is IllegalArgumentException)
        assertNotEquals(WorkspaceInvitationService.OWNER_ONLY_ERROR, e.message)
    }
    @Test fun permissionWordIn500MessageIsStillServiceFailure() {
        val e = error(500, """{"error":{"status":"INTERNAL","message":"Storage permission token=SECRET"}}""")
        assertTrue(e is IllegalStateException)
        assertEquals("Invitation service is unavailable. Try again later.", e.message)
        assertFalse(e.message.orEmpty().contains("SECRET"))
    }
    @Test fun http401HasSafeSignInRecoveryInsteadOfOwnerDenial() {
        val e = error(401, """{"error":{"message":"Owner session token=SECRET"}}""")
        assertTrue(e is SecurityException)
        assertEquals("Your session could not be verified. Sign in again and retry the invitation.", e.message)
    }
    @Test fun emptyMalformedAndWrongShapeBodiesNeverEscape() {
        for (body in listOf("", "not json token=SECRET", "[]", "null", "{}", "{", "{\"error\":\"SECRET\"}")) {
            val e = error(500, body)
            assertTrue(e is IllegalStateException)
            assertEquals("Invitation service is unavailable. Try again later.", e.message)
        }
    }
    @Test fun validationDoesNotExposeUntrustedBackendDetails() {
        val e = error(400, """{"error":{"status":"INVALID_ARGUMENT","message":"token=SECRET uid=PRIVATE"}}""")
        assertTrue(e is IllegalArgumentException)
        assertEquals("Check the invitation name, phone number and role, then try again.", e.message)
    }
    @Test fun explicitUnauthenticatedStatusRequestsSignIn() {
        val e = error(400, """{"error":{"status":"UNAUTHENTICATED"}}""")
        assertTrue(e is SecurityException)
        assertEquals("Your session could not be verified. Sign in again and retry the invitation.", e.message)
    }
    @Test fun missingEndpointDoesNotBlameOwnerOrInviteeRole() {
        for (body in listOf("", "{}", "<html>Private route details SECRET</html>")) {
            val e = error(404, body)
            assertTrue(e is IllegalStateException)
            assertEquals(WorkspaceInvitationService.ENDPOINT_UNAVAILABLE_ERROR, e.message)
            assertFalse(e.message.orEmpty().contains("SECRET"))
        }
    }
    @Test fun structuredMissingInvitationStillExplainsInvitationRecovery() {
        val e = error(404, """{"error":{"status":"NOT_FOUND","message":"private"}}""")
        assertNotEquals(WorkspaceInvitationService.ENDPOINT_UNAVAILABLE_ERROR, e.message)
        assertEquals("This invitation is no longer valid. Refresh and start a new invitation.", e.message)
    }
}
