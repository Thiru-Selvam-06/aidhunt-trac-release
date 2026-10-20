package com.example

import com.example.data.firebase.*
import org.junit.Assert.*
import org.junit.Test

class CloudListenerHealthTest {
    @Test fun onlyConfirmedServerSnapshotsCanReconcileTheLedger() {
        assertFalse(authoritativeLedgerSnapshot(fromCache = true, pendingWrites = false))
        assertFalse(authoritativeLedgerSnapshot(fromCache = false, pendingWrites = true))
        assertFalse(authoritativeLedgerSnapshot(fromCache = true, pendingWrites = true))
        assertTrue(authoritativeLedgerSnapshot(fromCache = false, pendingWrites = false))
    }
    @Test fun emptyQueueCannotMaskListenerFailure() {
        val health = CloudListenerHealth()
        health.begin("a", setOf("extensions", "payments"))
        health.snapshot("a", "extensions", false)
        health.failed("a", "payments")
        assertEquals("Cloud error", cloudSyncLabel(health.states.value.getValue("a"), true, false, 0))
        health.snapshot("a", "payments", true)
        assertEquals(CloudConnectionState.ERROR, health.states.value["a"])
    }
    @Test fun allListenersNeedServerConfirmation() {
        val health = CloudListenerHealth()
        health.begin("a", setOf("extensions", "payments"))
        health.snapshot("a", "extensions", false)
        health.snapshot("a", "payments", true)
        assertEquals("Connecting...", cloudSyncLabel(health.states.value.getValue("a"), true, false, 0))
        health.snapshot("a", "payments", false)
        assertEquals("Synced", cloudSyncLabel(health.states.value.getValue("a"), true, false, 0))
    }
    @Test fun failureCanRecoverOnlyWithServerEvidence() {
        val health = CloudListenerHealth()
        health.begin("a", setOf("extensions"))
        health.failed("a", "extensions")
        health.snapshot("a", "extensions", false)
        assertEquals(CloudConnectionState.CONNECTED, health.states.value["a"])
    }
    @Test fun workspaceErrorsDoNotContaminateAnotherWorkspace() {
        val health = CloudListenerHealth()
        health.begin("a", setOf("extensions")); health.begin("b", setOf("extensions"))
        health.failed("a", "extensions"); health.snapshot("b", "extensions", false)
        assertEquals(CloudConnectionState.ERROR, health.states.value["a"])
        assertEquals(CloudConnectionState.CONNECTED, health.states.value["b"])
        health.remove("a"); health.snapshot("a", "extensions", false)
        assertNull(health.states.value["a"])
        health.clear(); assertTrue(health.states.value.isEmpty())
    }
    @Test fun rejectedActionsSurviveConnectedAndOfflineLabels() {
        assertEquals("2 Need review", cloudSyncLabel(CloudConnectionState.CONNECTED, true, false, 0, rejected = 2))
        assertEquals("1 Need review", cloudSyncLabel(CloudConnectionState.CONNECTED, false, false, 0, rejected = 1))
    }
    @Test fun backendBlockedActionsDoNotMasqueradeAsManualReview() {
        assertEquals("1 Blocked", cloudSyncLabel(CloudConnectionState.ERROR, true, false, 1, rejected = 1, blocked = 1))
        assertEquals(com.example.ui.util.CloudStatusAction.OPEN_RETAINED_ACTIONS,
            com.example.ui.util.cloudStatusAction(1, CloudConnectionState.ERROR))
    }
    @Test fun uploadAndConnectionAreIndependent() {
        assertEquals("3 Pending", cloudSyncLabel(CloudConnectionState.CONNECTED, true, false, 3))
        assertEquals("Offline (0)", cloudSyncLabel(CloudConnectionState.CONNECTED, false, false, 0))
        assertEquals("Connecting...", cloudSyncLabel(CloudConnectionState.IDLE, true, false, 0))
        assertEquals("Cloud error", cloudSyncLabel(CloudConnectionState.ERROR, true, true, 0))
    }
}
