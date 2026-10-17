package com.example

import com.example.data.firebase.WorkspaceDiscoveryPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkspaceDiscoveryPolicyTest {
    @Test fun personalWorkspaceRecoveredWhenIndexAndDefaultOmitIt() {
        assertTrue(WorkspaceDiscoveryPolicy.candidates("fixture-partner", null, "invited", listOf("invited"), emptyList()).contains("ws_fixturepartner"))
    }
    @Test fun allOwnedAndInvitedWorkspacesRemainCandidatesWithoutStagingInjection() {
        assertEquals(listOf("selected", "deleted", "personal", "invited", "second-owned", "ws_fixture"),
            WorkspaceDiscoveryPolicy.candidates("fixture", "selected", "deleted", listOf("personal", "selected"), listOf("invited", "second-owned")))
    }
    @Test fun malformedPathsCannotSelectAnotherDocument() {
        assertEquals(listOf("ws_fixture"), WorkspaceDiscoveryPolicy.candidates("fixture", "../other", "a/b", listOf("..", ""), emptyList()))
    }
    @Test fun staleIndexCannotReviveRemovedMember() {
        assertNull(role("PARTNER", "REMOVED")); assertNull(role("PARTNER", "INACTIVE"))
    }
    @Test fun roleIsWorkspaceScoped() {
        assertEquals("Owner", role("OWNER")); assertEquals("Partner", role("PARTNER", owner = "another"))
        assertEquals("Operator", role("OPERATOR", owner = "another"))
        assertNull(role("OWNER", owner = "another")); assertNull(role("ADMIN"))
    }
    @Test fun identityAndWorkspaceMismatchRejected() {
        assertNull(role("PARTNER", fields = mapOf("uid" to "other")))
        assertNull(role("PARTNER", fields = mapOf("workspaceId" to "other")))
    }
    @Test fun legacyMissingRedundantFieldsKeepPathScopedAuthority() { assertEquals("Partner", role("partner", "active")) }
    @Test fun personalIdentityStableAcrossRetries() {
        assertEquals(WorkspaceDiscoveryPolicy.personalId("fixture"), WorkspaceDiscoveryPolicy.personalId("fixture"))
        assertNotEquals(WorkspaceDiscoveryPolicy.personalId("fixture"), WorkspaceDiscoveryPolicy.personalId("another"))
    }
    @Test fun syntheticAccountShapesPreserveWorkspaceAuthority() {
        val json = javaClass.classLoader!!.getResourceAsStream("workspace-access-audit.json")!!.bufferedReader().use { it.readText() }
        val rows = JSONArray(json)
        for (i in 0 until rows.length()) {
            val data = rows.getJSONObject(i)
            val uid = data.getString("uid")
            fun ids(key: String): List<String> {
                val arr = data.getJSONArray(key)
                return (0 until arr.length()).map { arr.getString(it) }
            }
            val candidates = WorkspaceDiscoveryPolicy.candidates(uid, null, data.optString("defaultId").takeIf { it.isNotBlank() }, ids("profileIds"), ids("indexIds"))
            val docsArr = data.getJSONArray("documents")
            val docs = (0 until docsArr.length()).map { docsArr.getJSONObject(it) }.associateBy { it.getString("id") }
            val actual = candidates.filter { id ->
                val doc = docs[id]
                if (doc == null) false else {
                    val memberObj = doc.getJSONObject("member")
                    val fields = memberObj.keys().asSequence().associateWith { memberObj.getString(it) }
                    WorkspaceDiscoveryPolicy.role(uid, id, fields, doc.getString("ownerUid")) != null
                }
            }.sorted()
            assertEquals("Synthetic discovery fixture for UID $uid", ids("expected"), actual)
        }
    }

    private fun role(role: String, status: String = "ACTIVE", owner: String = "fixture", fields: Map<String, Any?> = emptyMap()): String? =
        WorkspaceDiscoveryPolicy.role("fixture", "workspace", mapOf("role" to role, "status" to status) + fields, owner)
}
