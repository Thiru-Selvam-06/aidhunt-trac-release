package com.example

import com.example.data.sync.ReplicaDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReplicaSyncOrderingTest {
    @Test fun slowEarlierSnapshotCannotOverwriteNewerSnapshot() = runTest {
        val applied=mutableListOf<Int>();val release=CompletableDeferred<Unit>()
        val dispatcher=ReplicaDispatcher(backgroundScope){throw it}
        dispatcher.submit {release.await();applied.add(1)}
        dispatcher.submit {applied.add(2)}
        runCurrent();assertTrue(applied.isEmpty())
        release.complete(Unit);runCurrent();assertEquals(listOf(1,2),applied)
    }
    @Test fun failedSnapshotDoesNotStopLaterAuthoritativeUpdates() = runTest {
        val failures=mutableListOf<Exception>();var latest=0
        val dispatcher=ReplicaDispatcher(backgroundScope){failures.add(it)}
        dispatcher.submit {throw IllegalStateException("synthetic failed snapshot")}
        dispatcher.submit {latest=2}
        runCurrent();assertEquals(1,failures.size);assertEquals(2,latest)
    }
}
