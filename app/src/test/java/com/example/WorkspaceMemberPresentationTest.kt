package com.example

import com.example.data.entity.PartnerEntity
import com.example.data.firebase.WorkspaceMember
import com.example.ui.screens.account.workspacePartnerDisplayRole
import org.junit.Assert.*
import org.junit.Test

class WorkspaceMemberPresentationTest {
    private val local = PartnerEntity(id = 1, workspaceId = "invited", name = "Target Partner", phone = "9876543210", role = "Owner", firebaseUid = "target")
    private val member = WorkspaceMember(uid = "target", workspaceId = "invited", role = "PARTNER", status = "ACTIVE")
    @Test fun ownWorkspaceRoleDoesNotOverrideInvitedWorkspaceRole() {
        assertEquals("Partner", workspacePartnerDisplayRole(local, member, "invited"))
        assertEquals("Owner", workspacePartnerDisplayRole(local.copy(workspaceId = "personal"),
            member.copy(workspaceId = "personal", role = "OWNER"), "personal"))
    }
    @Test fun foreignWorkspaceCannotSupplyDisplayRole() {
        assertEquals("", workspacePartnerDisplayRole(local, member.copy(workspaceId = "foreign"), "invited"))
    }
    @Test fun removedAndUnknownRolesAreNotDisplayedAsActivePartners() {
        assertEquals("", workspacePartnerDisplayRole(local, member.copy(status = "REMOVED"), "invited"))
        assertEquals("", workspacePartnerDisplayRole(local, member.copy(role = "ADMIN"), "invited"))
    }
    @Test fun unresolvedLocalRoleRequiresSameWorkspace() {
        assertEquals("", workspacePartnerDisplayRole(local.copy(workspaceId = "foreign"), null, "invited"))
    }
}
