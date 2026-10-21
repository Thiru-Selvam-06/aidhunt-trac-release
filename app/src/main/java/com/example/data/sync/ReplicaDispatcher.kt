package com.example.data.sync

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Preserve listener delivery order while Room applies full authoritative snapshots. */
class ReplicaDispatcher(scope: CoroutineScope, onFailure: (Exception) -> Unit) {
    private val updates = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    init {
        scope.launch {
            for (update in updates) try { update() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { onFailure(failure) }
        }
    }
    fun submit(update: suspend () -> Unit) { updates.trySend(update) }
}
