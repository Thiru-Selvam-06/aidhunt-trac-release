package com.example

import com.example.data.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceRequestOutcomeTest {
    @Test fun competingLoginRequestDoesNotShowFalseAccessToastOrPublishOldWorkspace() = runTest {
        val fence = WorkspaceRequestFence { "owner" }
        val paused = CompletableDeferred<Unit>()
        val errors = mutableListOf<String>()
        var workspace = ""
        val old = launch {
            val result = awaitWorkspaceRequest {
                val ticket = fence.begin("owner")
                paused.await()
                fence.check(ticket)
                Result.success("old")
            }
            result.onSuccess { workspace = it }.onWorkspaceFailure { errors.add(it.message.orEmpty()) }
        }
        runCurrent()
        val newest = fence.begin("owner")
        fence.check(newest)
        workspace = "current"
        paused.complete(Unit)
        old.join()
        assertEquals("current", workspace)
        assertTrue(errors.isEmpty())
        assertTrue(currentCoroutineContext().isActive)
    }
    @Test fun accountChangeCannotPublishSuccessOrFalseErrorForOldAccount() = runTest {
        var uid: String? = "old"
        val fence = WorkspaceRequestFence { uid }
        val result = awaitWorkspaceRequest {
            val ticket = fence.begin("old")
            uid = "new"
            fence.check(ticket)
            Result.success("old-workspace")
        }
        var success = false
        var error = false
        result.onSuccess { success = true }.onWorkspaceFailure { error = true }
        assertFalse(success)
        assertFalse(error)
        assertTrue(result.exceptionOrNull() is WorkspaceRequestSupersededException)
    }
    @Test fun realDenialStillReachesErrorCallback() = runTest {
        val denied = SecurityException("Workspace access removed")
        var seen: Throwable? = null
        awaitWorkspaceRequest { Result.failure<String>(denied) }.onWorkspaceFailure { seen = it }
        assertSame(denied, seen)
    }
    @Test fun actualCoroutineCancellationIsRethrown() = runTest {
        val cancellation = CancellationException("screen closed")
        try {
            awaitWorkspaceRequest<String> { throw cancellation }
            fail("Cancellation swallowed")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
        try {
            Result.failure<String>(cancellation).onWorkspaceFailure { fail("Cancellation became a toast") }
            fail("Failed result cancellation swallowed")
        } catch (actual: CancellationException) {
            assertSame(cancellation, actual)
        }
    }
    @Test fun cancellationDoesNotTerminateLaterNetworkRecovery() = runTest {
        val results = mutableListOf<String>()
        for (first in listOf(true, false)) {
            awaitWorkspaceRequest {
                if (first) throw WorkspaceRequestSupersededException()
                Result.success("reconnected")
            }.onSuccess { results.add(it) }.onWorkspaceFailure { fail("Unexpected access error") }
        }
        assertEquals(listOf("reconnected"), results)
    }
}
