package com.example
import com.example.data.firebase.WorkspaceDiscoveryPolicy
import org.junit.Assert.*
import org.junit.Test
class WorkspaceSelectionPolicyTest {
    @Test fun multipleWorkspacesRequireChoice() { assertNull(WorkspaceDiscoveryPolicy.selectedWorkspace(listOf("a", "b"), null, null)) }
    @Test fun singleWorkspaceOpens() { assertEquals("a", WorkspaceDiscoveryPolicy.selectedWorkspace(listOf("a"), null, null)) }
    @Test fun rememberedSelectionMustRemainAuthorized() { assertNull(WorkspaceDiscoveryPolicy.selectedWorkspace(listOf("a", "b"), null, "removed")); assertEquals("b", WorkspaceDiscoveryPolicy.selectedWorkspace(listOf("a", "b"), null, "b")) }
    @Test(expected = IllegalArgumentException::class) fun invalidExplicitSelectionNeverFallsBack() { WorkspaceDiscoveryPolicy.selectedWorkspace(listOf("a"), "removed", "a") }
    @Test fun explicitRoleWorkspaceSelectionWinsOverRemembered() { assertEquals("b", WorkspaceDiscoveryPolicy.selectedWorkspace(listOf("a", "b"), "b", "a")) }
}
