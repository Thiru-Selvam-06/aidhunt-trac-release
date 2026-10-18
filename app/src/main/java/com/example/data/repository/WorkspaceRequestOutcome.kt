package com.example.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A newer authorized request owns publication. Supersession is not an access denial. */
internal suspend fun <T> awaitWorkspaceRequest(block: suspend () -> Result<T>): Result<T> =
    try { block() }
    catch (superseded: WorkspaceRequestSupersededException) {
        currentCoroutineContext().ensureActive()
        Result.failure(superseded)
    }

/** Keep stale callbacks silent; preserve genuine cancellation and real access errors. */
internal inline fun <T> Result<T>.onWorkspaceFailure(action: (Throwable) -> Unit): Result<T> {
    exceptionOrNull()?.let { error ->
        when (error) {
            is WorkspaceRequestSupersededException -> Unit
            is CancellationException -> throw error
            else -> action(error)
        }
    }
    return this
}
