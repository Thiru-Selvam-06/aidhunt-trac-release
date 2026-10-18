package com.example

import com.example.data.repository.WorkspaceRequestFence
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceRequestFenceTest {
    @Test fun delayedWorkspaceAResponseCannotPublishOverB() = runTest {
        var uid: String? = "owner"
        val fence = WorkspaceRequestFence { uid }
        val delayed = CompletableDeferred<String>()
        var visibleWorkspace = "cached"
        var oldRejected = false
        val old = launch {
            val request = fence.begin("owner")
            val workspace = delayed.await()
            try { fence.check(request); visibleWorkspace = workspace }
            catch (_: CancellationException) { oldRejected = true }
        }
        runCurrent()
        val newer = fence.begin("owner"); fence.check(newer); visibleWorkspace = "b"
        delayed.complete("a"); old.join()
        assertTrue(oldRejected); assertEquals("b", visibleWorkspace)
    }
    @Test fun accountSwitchRejectsOldWorkspaceResponse() {
        var uid: String? = "a"; val fence = WorkspaceRequestFence { uid }; val request = fence.begin("a")
        uid = "b"
        assertThrows(CancellationException::class.java) { fence.check(request) }
    }
    @Test fun logoutInvalidatesEvenWhenSameAccountSignsInAgain() {
        var uid: String? = "a"; val fence = WorkspaceRequestFence { uid }; val request = fence.begin("a")
        uid = null; fence.invalidate(); uid = "a"
        assertThrows(CancellationException::class.java) { fence.check(request) }
    }
    @Test fun noIdentityOrWrongIdentityCannotBeginSelection() {
        val fence = WorkspaceRequestFence { null }
        assertThrows(SecurityException::class.java) { fence.begin("owner") }
        assertThrows(SecurityException::class.java) { fence.begin("") }
    }
    @Test fun authorizedCurrentSelectionCanPublish() {
        val fence = WorkspaceRequestFence { "owner" }; fence.check(fence.begin("owner"))
    }
}
