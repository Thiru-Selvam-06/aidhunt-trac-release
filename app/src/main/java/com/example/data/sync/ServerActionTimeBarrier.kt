package com.example.data.sync

import java.io.IOException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** A read-only lower bound, never a replacement for an action's original timestamp.
 * Old offline actions share one read. Small clock skew waits once and is rechecked;
 * larger skew stays PENDING before any business write reaches authorization. */
internal class ServerActionTimeBarrier(
    private val readServerTime: suspend () -> Long,
    private val pause: suspend (Long) -> Unit = { delay(it) }
) {
    private val mutex = Mutex()
    private var confirmedTime = 0L

    suspend fun awaitEligible(actionTime: Long) = mutex.withLock {
        require(actionTime > 0)
        if (actionTime <= confirmedTime) return@withLock
        fun verify(time: Long): Long {
            if (time <= 0) throw IOException("Server time verification unavailable; original action remains pending")
            return time
        }
        confirmedTime = verify(readServerTime())
        if (actionTime > confirmedTime && actionTime - confirmedTime <= 2000L) {
            pause(actionTime - confirmedTime + 1L)
            confirmedTime = verify(readServerTime())
        }
        if (actionTime > confirmedTime) throw ActionClockAheadException(actionTime - confirmedTime)
    }
}

internal class ActionClockAheadException(val aheadMillis: Long) : IOException(
    "CLOCK_AHEAD: Pending sync; original action is ${aheadMillis}ms ahead of confirmed server time. " +
        "Check automatic date and time. Original data and timestamps are unchanged; no write was sent."
)
