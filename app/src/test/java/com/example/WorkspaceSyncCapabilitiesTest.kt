package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.entity.PendingOperationEntity
import com.example.data.firebase.*
import com.example.data.sync.SyncFailurePolicy
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class WorkspaceSyncCapabilitiesTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    @Before fun resetSyntheticCache() {
        context.getSharedPreferences("verified_workspace_capabilities", Context.MODE_PRIVATE).edit().clear().commit()
    }
    @Test fun missingMalformedAndTruthyStringsNeverEnableNewProtocols() {
        for (data in listOf(emptyMap(), mapOf("syncCapabilities" to "enabled"),
            mapOf("syncCapabilities" to mapOf("expenseCategoriesV1" to "true", "basicCustomerProfilesV1" to 1))))
            assertEquals(WorkspaceSyncCapabilities(), WorkspaceSyncCapabilities.fromWorkspace(data))
        assertEquals(WorkspaceSyncCapabilities(true, false), WorkspaceSyncCapabilities.fromWorkspace(
            mapOf("syncCapabilities" to mapOf("expenseCategoriesV1" to true))))
    }
    @Test fun verifiedActivationSurvivesRestartAndCannotLeakToAnotherActorOrWorkspace() {
        val enabled = WorkspaceSyncCapabilities(true, true)
        VerifiedWorkspaceCapabilityCache(context).record("original-partner", "workspace-a", enabled)
        val reopened = VerifiedWorkspaceCapabilityCache(context)
        assertEquals(enabled, reopened.restore("original-partner", "workspace-a"))
        assertEquals(WorkspaceSyncCapabilities(), reopened.restore("other-partner", "workspace-a"))
        assertEquals(WorkspaceSyncCapabilities(), reopened.restore("original-partner", "workspace-b"))
        assertEquals(WorkspaceSyncCapabilities(), reopened.restore("", "workspace-a"))
    }
    @Test fun freshDisabledServerStateReplacesOldEnabledCache() {
        val cache = VerifiedWorkspaceCapabilityCache(context)
        cache.record("actor", "workspace", WorkspaceSyncCapabilities(true, true))
        cache.record("actor", "workspace", WorkspaceSyncCapabilities())
        assertEquals(WorkspaceSyncCapabilities(), VerifiedWorkspaceCapabilityCache(context).restore("actor", "workspace"))
    }
    @Test fun disabledProtocolWaitIsTransientAndPreservesExactOriginalAction() {
        val basic = PendingOperationEntity("original-op", "workspace", "operator", "CUSTOMER", 9007199254740995L,
            "UPSERT", 1, 123456789, """{"basicProfileCreate":true,"name":"Raw தமிழ் ?","totalPaid":0}""")
        for (operation in listOf(basic, basic.copy(entityType = "EXPENSE_CATEGORY"))) {
            val before = operation.copy()
            val error = assertThrows(WorkspaceCapabilityPendingException::class.java) {
                WorkspaceSyncCapabilities().requireSupported(operation)
            }
            assertFalse(SyncFailurePolicy.permanent(error))
            assertEquals(before, operation)
            WorkspaceSyncCapabilities(true, true).requireSupported(operation)
        }
        assertEquals(WorkspaceSyncCapabilities(true, false, true), WorkspaceSyncCapabilities.fromWorkspace(
            mapOf("syncCapabilities" to mapOf("expenseCategoriesV1" to true, "atomicCashControlV1" to true))))
        assertEquals(WorkspaceSyncCapabilities(), WorkspaceSyncCapabilities.fromWorkspace(
            mapOf("syncCapabilities" to mapOf("atomicCashControlV1" to "true"))))
        val atomicCached = VerifiedWorkspaceCapabilityCache(context)
        atomicCached.record("atomic-user", "ws-atomic", WorkspaceSyncCapabilities(false, false, true))
        assertTrue(VerifiedWorkspaceCapabilityCache(context).restore("atomic-user", "ws-atomic").atomicCashControlV1)
        WorkspaceSyncCapabilities().requireSupported(basic.copy(payloadJson = "{}"))
        WorkspaceSyncCapabilities().requireSupported(basic.copy(entityType = "JOB"))
    }
}
