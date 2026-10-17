package com.example

import com.example.data.firebase.FirebaseEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedCommandRoutingTest {
    private val stagingWorkspaceLogin = "ws_cf_staging_20261015_login_a62e71"
    private val stagingWorkspace7361 = "ws_cf_staging_20261015_7361_7f4c92"
    private val stagingWorkspaceBeb = "ws_cf_staging_beb841c2f4d9475a"
    private val canonicalPmsWorkspace = "ws_QlVAEPVTtYSxLeWC"
    private val legacyPmsWorkspace = "ws_G7RZespbU6cwIUha"

    @Test fun onlyIsolatedLiveTestWorkspaceUsesStagingWorker() {
        val staging = FirebaseEnvironment.resolveCommandUrl("trac-aidhunt", "deleteWorkspaceEntry",
            stagingWorkspaceLogin, deviceTest = false, liveTest = true)
        assertEquals("https://aidhunt-trac-trusted-staging.aidhuntinformatics.workers.dev/deleteWorkspaceEntry", staging)
        val other = FirebaseEnvironment.resolveCommandUrl("trac-aidhunt", "deleteWorkspaceEntry",
            canonicalPmsWorkspace, deviceTest = false, liveTest = true)
        assertEquals("https://us-central1-trac-aidhunt.cloudfunctions.net/deleteWorkspaceEntry", other)
        assertEquals(other, FirebaseEnvironment.resolveCommandUrl("trac-aidhunt", "deleteWorkspaceEntry",
            stagingWorkspaceLogin, deviceTest = false, liveTest = false))
    }

    @Test fun stagingRoutingRejectsWrongProjectAndMalformedOperation() {
        assertThrows(IllegalStateException::class.java) {
            FirebaseEnvironment.resolveCommandUrl("other-project", "deleteWorkspaceEntry",
                stagingWorkspaceLogin, deviceTest = false, liveTest = true)
        }
        assertThrows(IllegalArgumentException::class.java) {
            FirebaseEnvironment.resolveCommandUrl("trac-aidhunt", "../deleteWorkspaceEntry",
                stagingWorkspaceLogin, deviceTest = false, liveTest = true)
        }
    }

    @Test fun physicalTestCommandsCannotRouteOutsideDisposableWorkspace() {
        val staging = FirebaseEnvironment.resolveCommandUrl("trac-aidhunt", "deleteWorkspaceCustomer",
            stagingWorkspaceLogin, deviceTest = false, liveTest = false, physicalTest = true)
        assertEquals("https://aidhunt-trac-trusted-staging.aidhuntinformatics.workers.dev/deleteWorkspaceCustomer", staging)
        assertThrows(IllegalStateException::class.java) {
            FirebaseEnvironment.resolveCommandUrl("trac-aidhunt", "deleteWorkspaceCustomer",
                canonicalPmsWorkspace, deviceTest = false, liveTest = false, physicalTest = true)
        }
        assertThrows(IllegalStateException::class.java) {
            FirebaseEnvironment.resolveCommandUrl("trac-aidhunt", "deleteWorkspaceCustomer",
                legacyPmsWorkspace, deviceTest = false, liveTest = false, physicalTest = true)
        }
    }

    @Test fun allAuthorizedStagingWorkspacesCanReceivePhysicalTestCommands() {
        for (ws in listOf(stagingWorkspaceLogin, stagingWorkspace7361, stagingWorkspaceBeb)) {
            val staging = FirebaseEnvironment.resolveCommandUrl("trac-aidhunt", "deleteWorkspaceEntry",
                ws, deviceTest = false, liveTest = false, physicalTest = true)
            assertEquals("https://aidhunt-trac-trusted-staging.aidhuntinformatics.workers.dev/deleteWorkspaceEntry", staging)
            assertTrue(FirebaseEnvironment.isStagingWorkspace(ws))
        }
    }

    @Test fun nonStagingWorkspaceCannotReceivePhysicalTestCommands() {
        assertThrows(IllegalStateException::class.java) {
            FirebaseEnvironment.resolveCommandUrl("trac-aidhunt", "deleteWorkspaceEntry",
                "ws_other_workspace", deviceTest = false, liveTest = false, physicalTest = true)
        }
    }

    @Test fun unapprovedStagingWorkspaceCannotReceivePhysicalTestCommands() {
        val unapproved = "ws_cf_staging_unapproved_fake"
        assertFalse(FirebaseEnvironment.isStagingWorkspace(unapproved))
        assertThrows(IllegalStateException::class.java) {
            FirebaseEnvironment.resolveCommandUrl("trac-aidhunt", "deleteWorkspaceEntry",
                unapproved, deviceTest = false, liveTest = false, physicalTest = true)
        }
    }

    @Test fun productionWorkspacesAreNeverStagingWorkspaces() {
        assertFalse(FirebaseEnvironment.isStagingWorkspace(canonicalPmsWorkspace))
        assertFalse(FirebaseEnvironment.isStagingWorkspace(legacyPmsWorkspace))
        assertFalse(FirebaseEnvironment.isStagingWorkspace(null))
        assertFalse(FirebaseEnvironment.isStagingWorkspace(""))
    }
}
