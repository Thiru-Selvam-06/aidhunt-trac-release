package com.example

import com.example.data.repository.initialWorkspaceSettings
import org.junit.Assert.*
import org.junit.Test

class InitialWorkspaceSettingsTest {
    @Test fun ownerRegistrationRetainsSeparateBusinessAndOwnerNames() {
        val settings = initialWorkspaceSettings("workspace-a", "Farm Business", true, "Verified Owner", "+919000001113")
        assertEquals("workspace-a", settings.workspaceId)
        assertEquals("Farm Business", settings.businessName)
        assertEquals("Verified Owner", settings.ownerName)
        assertEquals("+919000001113", settings.businessPhone)
        assertFalse(settings.ownerName.isBlank() || settings.businessName.isBlank())
    }
    @Test fun partnerDoesNotBecomeTheWorkspaceOwnerInFirstLocalSettings() {
        val settings = initialWorkspaceSettings("workspace-b", "Shared Farm", false, "Partner Actor", "+919000000101")
        assertEquals("Shared Farm", settings.businessName)
        assertEquals("", settings.ownerName)
        assertEquals("", settings.businessPhone)
    }
    @Test fun unresolvedBusinessNameStillRequiresOwnerSetup() {
        val settings = initialWorkspaceSettings("workspace-c", "", true, "Verified Owner", "")
        assertTrue(settings.businessName.isBlank())
        assertEquals("Verified Owner", settings.ownerName)
    }
}
