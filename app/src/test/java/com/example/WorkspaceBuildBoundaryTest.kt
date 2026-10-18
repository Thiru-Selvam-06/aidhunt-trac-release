package com.example

import com.example.data.firebase.FirebaseEnvironment
import com.example.data.firebase.WorkspaceDiscoveryPolicy
import com.example.data.firebase.normalizePhoneNumber
import org.junit.Assert.*
import org.junit.Test

class WorkspaceBuildBoundaryTest {
    private fun available(uid: String, ws: String, role: String, status: String = "ACTIVE",
        memberUid: String = uid, physical: Boolean = true): String? {
        val resolved = WorkspaceDiscoveryPolicy.role(uid, ws,
            mapOf("uid" to memberUid, "workspaceId" to ws, "role" to role, "status" to status),
            if (role == "OWNER") uid else "fixture-owner") ?: return null
        return resolved.takeIf { FirebaseEnvironment.permitsWorkspaceDiscovery(ws, physical) }
    }

    @Test fun everyApprovedStagingMembershipSurvivesDiscoveryForEveryRole() {
        for (ws in FirebaseEnvironment.AUTHORIZED_STAGING_WORKSPACES) {
            for ((stored, expected) in listOf("OWNER" to "Owner", "PARTNER" to "Partner", "OPERATOR" to "Operator")) {
                assertEquals("Approved workspace $ws role $stored", expected, available("fixture-user", ws, stored))
            }
        }
    }

    @Test fun businessPersonalAndUnapprovedStagingWorkspacesStayOutsideIsolatedBuild() {
        for (ws in listOf("ws_fixturepersonal", "ws_fixturebusiness", "ws_cf_staging_unapproved")) {
            assertNull(available("fixture-user", ws, "OWNER"))
        }
    }

    @Test fun standardBuildRetainsAuthorizedPersonalAndBusinessRoles() {
        assertEquals("Owner", available("fixture-user", "ws_fixturepersonal", "OWNER", physical = false))
        assertEquals("Partner", available("fixture-user", "ws_fixturebusiness", "PARTNER", physical = false))
        assertEquals("Operator", available("fixture-user", "ws_fixturebusiness", "OPERATOR", physical = false))
    }

    @Test fun stagingWorkspacesStayOutsideStandardBuild() {
        for (ws in FirebaseEnvironment.AUTHORIZED_STAGING_WORKSPACES) {
            assertNull("Staging workspace $ws must stay outside standard build",
                available("fixture-user", ws, "OWNER", physical = false))
        }
        assertNull(available("fixture-user", "ws_cf_staging_unapproved", "OWNER", physical = false))
    }

    @Test fun approvedWorkspaceNeverRevivesRemovedOrForgedMembership() {
        for (ws in FirebaseEnvironment.AUTHORIZED_STAGING_WORKSPACES) {
            for (status in listOf("REMOVED", "ARCHIVED", "INACTIVE", "")) {
                assertNull(available("fixture-user", ws, "PARTNER", status))
            }
            assertNull(available("fixture-user", ws, "PARTNER", memberUid = "other-user"))
            assertNull(available("fixture-user", ws, "ADMIN"))
        }
    }

    @Test fun noMembershipDoesNotBecomeAccessJustBecauseWorkspaceIsApproved() {
        for (ws in FirebaseEnvironment.AUTHORIZED_STAGING_WORKSPACES) {
            assertNull(WorkspaceDiscoveryPolicy.role("fixture-user", ws, emptyMap(), "fixture-user"))
        }
    }

    @Test fun localCountryCodeAndInternationalPhoneFormsAgreeWithoutMergingDistinctNumbers() {
        val expected = "+919000000111"
        for (phone in listOf("9000000111", "919000000111", "+91 90000-00111", "91 9000000111", "+919000000111")) {
            assertEquals(expected, normalizePhoneNumber(phone))
        }
        assertNotEquals(expected, normalizePhoneNumber("9000000222"))
        assertEquals("", normalizePhoneNumber(""))
    }

    // Section 10 - Requirement 1: 9443107361-equivalent synthetic Owner with three authorized workspaces
    @Test fun syntheticOwnerEnding361RecoversAllThreeWorkspacesAcrossEnvironments() {
        val ownerUid = "fixture_G7RZespbU6cwIUhaB0sl7Du6tNV2"
        val prodWs = "ws_G7RZespbU6cwIUha"
        val stagingWs1 = "ws_cf_staging_20261015_7361_7f4c92"
        val stagingWs2 = "ws_cf_staging_20261015_login_a62e71"

        // In physical test: discovers staging workspaces, blocks prod
        val physicalWorkspaces = listOf(prodWs, stagingWs1, stagingWs2).filter { ws ->
            available(ownerUid, ws, "OWNER", physical = true) != null
        }
        assertEquals(listOf(stagingWs1, stagingWs2), physicalWorkspaces)
        // With 2 staging workspaces, multiple options require selection (selector displayed)
        assertNull(WorkspaceDiscoveryPolicy.selectedWorkspace(physicalWorkspaces, null, null))
        assertEquals(stagingWs1, WorkspaceDiscoveryPolicy.selectedWorkspace(physicalWorkspaces, stagingWs1, null))
        assertEquals(stagingWs2, WorkspaceDiscoveryPolicy.selectedWorkspace(physicalWorkspaces, stagingWs2, null))

        // In standard application: discovers prod workspace, blocks staging
        val standardWorkspaces = listOf(prodWs, stagingWs1, stagingWs2).filter { ws ->
            available(ownerUid, ws, "OWNER", physical = false) != null
        }
        assertEquals(listOf(prodWs), standardWorkspaces)
        // Exactly 1 production workspace auto-opens without unnecessary prompt
        assertEquals(prodWs, WorkspaceDiscoveryPolicy.selectedWorkspace(standardWorkspaces, null, null))
    }

    // Section 10 - Requirement 2: Partner with appropriate subset of those workspaces
    @Test fun syntheticPartnerRecoversOnlyActiveSubsetInApplicableEnvironment() {
        val partnerUid = "fixture_VxzKAUg2wIbJbQDImBz0VoWcIo13"
        val prodSharedWs = "ws_G7RZespbU6cwIUha"
        val prodPersonalWs = "ws_VxzKAUg2wIbJbQDI"
        val stagingActiveWs = "ws_cf_staging_20261015_login_a62e71"
        val stagingRemovedWs = "ws_cf_staging_20261015_7361_7f4c92"

        // In physical test: only active staging membership survives; removed membership is rejected
        assertNotNull(available(partnerUid, stagingActiveWs, "PARTNER", status = "ACTIVE", physical = true))
        assertNull(available(partnerUid, stagingRemovedWs, "PARTNER", status = "REMOVED", physical = true))
        assertNull(available(partnerUid, prodSharedWs, "PARTNER", status = "ACTIVE", physical = true))

        val stagingChoices = listOf(stagingActiveWs, stagingRemovedWs).filter { ws ->
            val status = if (ws == stagingActiveWs) "ACTIVE" else "REMOVED"
            available(partnerUid, ws, "PARTNER", status = status, physical = true) != null
        }
        assertEquals(listOf(stagingActiveWs), stagingChoices)
        // Single staging workspace auto-opens
        assertEquals(stagingActiveWs, WorkspaceDiscoveryPolicy.selectedWorkspace(stagingChoices, null, null))

        // In standard app: discovers both shared and personal workspaces; staging blocked
        val prodChoices = listOf(prodSharedWs, prodPersonalWs).filter { ws ->
            val role = if (ws == prodPersonalWs) "OWNER" else "PARTNER"
            available(partnerUid, ws, role, physical = false) != null
        }
        assertEquals(listOf(prodSharedWs, prodPersonalWs), prodChoices)
        assertNull(available(partnerUid, stagingActiveWs, "PARTNER", physical = false))
    }

    // Section 10 - Requirement 3: Operator with only assigned workspaces
    @Test fun syntheticOperatorSeesOnlyAssignedWorkspacesWithOperatorRole() {
        val opUid = "fixture_operator_uid"
        val assignedWs = "ws_cf_staging_20261015_7361_7f4c92"
        val unassignedWs = "ws_cf_staging_20261015_login_a62e71"

        assertEquals("Operator", available(opUid, assignedWs, "OPERATOR", physical = true))
        assertNull(available(opUid, unassignedWs, "OPERATOR", memberUid = "other", physical = true))
    }

    // Section 10 - Requirement 4 & 5: Standard app production and physical test staging isolation
    @Test fun standardAndPhysicalTestEnforceStrictEnvironmentIsolation() {
        val prodWs = "ws_VZ088WCZ61SL8kN3"
        val stagingWs = "ws_cf_staging_beb841c2f4d9475a"

        // Production accessible only in standard build
        assertEquals("Owner", available("user", prodWs, "OWNER", physical = false))
        assertNull(available("user", prodWs, "OWNER", physical = true))

        // Staging accessible only in physical test build
        assertEquals("Owner", available("user", stagingWs, "OWNER", physical = true))
        assertNull(available("user", stagingWs, "OWNER", physical = false))
    }

    // Section 10 - Requirement 6: Production-only account signing into isolated build
    @Test fun productionOnlyAccountSigningIntoIsolatedBuildDiscoversNoWorkspaces() {
        val prodWorkspaces = listOf("ws_VZ088WCZ61SL8kN3", "ws_j2UalBQUeKb7BX1h", "ws_7092476678")
        val discoveredInPhysical = prodWorkspaces.filter { ws ->
            available("prod_user", ws, "OWNER", physical = true) != null
        }
        assertTrue("Production-only account must discover zero workspaces in physical test", discoveredInPhysical.isEmpty())
    }

    // Section 10 - Requirement 7: Staging-only account signing into standard build
    @Test fun stagingOnlyAccountSigningIntoStandardBuildDiscoversNoWorkspaces() {
        val stagingWorkspaces = FirebaseEnvironment.AUTHORIZED_STAGING_WORKSPACES.toList()
        val discoveredInStandard = stagingWorkspaces.filter { ws ->
            available("staging_user", ws, "OWNER", physical = false) != null
        }
        assertTrue("Staging-only account must discover zero workspaces in standard build", discoveredInStandard.isEmpty())
    }

    // Section 10 - Requirement 8, 9, 10, 11: Edge cases (empty, revoked, stale, unauthorized)
    @Test fun edgeCasesEmptyRevokedStaleAndUnauthorized() {
        // Zero active memberships
        val emptyChoices = emptyList<String>()
        assertNull(WorkspaceDiscoveryPolicy.selectedWorkspace(emptyChoices, null, null))

        // Revoked membership cannot be selected or discovered
        assertNull(available("user", "ws_cf_staging_20261015_login_a62e71", "PARTNER", status = "REMOVED", physical = true))

        // Stale profile index does not grant authority if membership doc is inactive
        val candidates = WorkspaceDiscoveryPolicy.candidates("user", null, null, listOf("ws_stale"), listOf("ws_stale"))
        assertTrue(candidates.contains("ws_stale"))
        assertNull(available("user", "ws_stale", "PARTNER", status = "REMOVED", physical = false))

        // Unauthorized workspace selection fails closed
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceDiscoveryPolicy.selectedWorkspace(listOf("ws_authorized"), "ws_unauthorized", null)
        }
    }

    // Section 10 - Requirement 17, 18, 19: Selection rules and role preservation
    @Test fun selectionRulesAndRolePreservation() {
        // Single workspace auto-opens
        assertEquals("ws_one", WorkspaceDiscoveryPolicy.selectedWorkspace(listOf("ws_one"), null, null))

        // Multiple workspaces require selection
        assertNull(WorkspaceDiscoveryPolicy.selectedWorkspace(listOf("ws_one", "ws_two"), null, null))

        // Owner/Partner pair in same workspace retain distinct roles
        val ownerRole = WorkspaceDiscoveryPolicy.role("owner_uid", "ws_shared",
            mapOf("uid" to "owner_uid", "workspaceId" to "ws_shared", "role" to "OWNER", "status" to "ACTIVE"),
            "owner_uid")
        assertEquals("Owner", ownerRole)

        val partnerRole = WorkspaceDiscoveryPolicy.role("partner_uid", "ws_shared",
            mapOf("uid" to "partner_uid", "workspaceId" to "ws_shared", "role" to "PARTNER", "status" to "ACTIVE"),
            "owner_uid")
        assertEquals("Partner", partnerRole)

        // Partner cannot claim Owner in shared workspace
        val forgedOwner = WorkspaceDiscoveryPolicy.role("partner_uid", "ws_shared",
            mapOf("uid" to "partner_uid", "workspaceId" to "ws_shared", "role" to "OWNER", "status" to "ACTIVE"),
            "owner_uid")
        assertNull(forgedOwner)
    }
    @Test fun repeatedWorkspaceSwitchingAndCrossWorkspaceAccessIntegrity() {
        val ws1 = "ws_cf_staging_20261015_7361_7f4c92"
        val ws2 = "ws_cf_staging_20261015_login_a62e71"
        val unauthorizedWs = "ws_unauthorized_999"

        val authorizedList = listOf(ws1, ws2)

        // Select ws1 initially
        var active = WorkspaceDiscoveryPolicy.selectedWorkspace(authorizedList, ws1, null)
        assertEquals(ws1, active)

        // Switch to ws2
        active = WorkspaceDiscoveryPolicy.selectedWorkspace(authorizedList, ws2, active)
        assertEquals(ws2, active)

        // Switch back to ws1
        active = WorkspaceDiscoveryPolicy.selectedWorkspace(authorizedList, ws1, active)
        assertEquals(ws1, active)

        // Repeat switch to ws2
        active = WorkspaceDiscoveryPolicy.selectedWorkspace(authorizedList, ws2, active)
        assertEquals(ws2, active)

        // Attempt to switch to unauthorized workspace fails closed
        assertThrows(IllegalArgumentException::class.java) {
            WorkspaceDiscoveryPolicy.selectedWorkspace(authorizedList, unauthorizedWs, active)
        }

        // Active workspace remains unmutated
        assertEquals(ws2, active)
    }
}
