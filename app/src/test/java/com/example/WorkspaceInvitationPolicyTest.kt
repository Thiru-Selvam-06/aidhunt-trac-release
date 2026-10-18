package com.example

import com.example.data.auth.AuthorizationManager
import com.example.data.auth.RoleUtils
import com.example.data.firebase.WorkspaceInvitationService
import com.example.ui.util.isInvitationPhoneValid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WorkspaceInvitationPolicyTest {

    @Test
    fun authoritativePolicy_onlyWorkspaceOwnerCanManagePartnersOrInvitations() {
        assertTrue(AuthorizationManager.canManagePartners(isOwner = true))
        assertFalse(AuthorizationManager.canManagePartners(isOwner = false))
    }

    @Test
    fun backendErrorMapping_mapsPermissionDeniedToAuthoritativeOwnerOnlyMessage() {
        // Functions HttpsError PERMISSION_DENIED format
        val errorJson = "{\"error\":{\"message\":\"Only the active workspace Owner can invite members\",\"status\":\"PERMISSION_DENIED\"}}"
        val ex1 = WorkspaceInvitationService.mapBackendError(403, errorJson)
        assertTrue(ex1 is SecurityException)
        assertEquals(WorkspaceInvitationService.OWNER_ONLY_ERROR, ex1.message)

        // Cloudflare Worker PERMISSION_DENIED format
        val cfError = "{\"error\":{\"status\":\"PERMISSION_DENIED\",\"message\":\"Only the active workspace Owner may perform this action\"}}"
        val ex2 = WorkspaceInvitationService.mapBackendError(403, cfError)
        assertTrue(ex2 is SecurityException)
        assertEquals(WorkspaceInvitationService.OWNER_ONLY_ERROR, ex2.message)

        // Generic HTTP 403
        val ex3 = WorkspaceInvitationService.mapBackendError(403, "")
        assertTrue(ex3 is SecurityException)
        assertEquals(WorkspaceInvitationService.OWNER_ONLY_ERROR, ex3.message)
    }

    @Test
    fun backendErrorMapping_distinguishesInputAndServerErrors() {
        // 400 Invalid Argument
        val invalidArg = "{\"error\":{\"message\":\"Choose a valid phone and Partner or Operator role\",\"status\":\"INVALID_ARGUMENT\"}}"
        val ex400 = WorkspaceInvitationService.mapBackendError(400, invalidArg)
        assertTrue(ex400 is IllegalArgumentException)
        assertEquals(WorkspaceInvitationService.VALIDATION_ERROR, ex400.message)

        // 500 Server Error
        val ex500 = WorkspaceInvitationService.mapBackendError(500, "")
        assertTrue(ex500 is IllegalStateException)
        assertEquals(WorkspaceInvitationService.SERVICE_ERROR, ex500.message)
    }

    @Test
    fun targetRoles_onlyPartnerAndOperatorPermitted() {
        val permitted = listOf(RoleUtils.ROLE_PARTNER, RoleUtils.ROLE_OPERATOR)
        assertTrue(permitted.contains("Partner"))
        assertTrue(permitted.contains("Operator"))
        assertFalse(permitted.contains("Owner"))
        assertFalse(permitted.contains("Co-Owner"))
        assertFalse(permitted.contains("Admin"))
    }

    @Test
    fun phoneValidation_rejectsMissingOrMalformedNumbers() {
        assertFalse(isInvitationPhoneValid(""))
        assertFalse(isInvitationPhoneValid("   "))
        assertFalse(isInvitationPhoneValid("12345"))
        assertFalse(isInvitationPhoneValid("abcdefghij"))

        // Configured test-number and E.164 formats
        assertTrue(isInvitationPhoneValid("9000000001"))
        assertTrue(isInvitationPhoneValid("+919000000001"))
        assertTrue(isInvitationPhoneValid("+91 90000 00001"))
    }
}
