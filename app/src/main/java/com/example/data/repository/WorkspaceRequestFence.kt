package com.example.data.repository

import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicLong

internal class WorkspaceRequestSupersededException : CancellationException("Workspace request superseded or authenticated identity changed")

/** Fences asynchronous workspace selection independently of cancellation of Firebase Tasks. */
internal class WorkspaceRequestFence(private val currentUid: () -> String?) {
    private val epoch = AtomicLong(0)
    data class Ticket internal constructor(val uid: String, internal val epoch: Long)
    fun begin(uid: String): Ticket {
        if (uid.isBlank() || currentUid() != uid) throw SecurityException("Authenticated identity mismatch")
        return Ticket(uid, epoch.incrementAndGet())
    }
    fun invalidate() { epoch.incrementAndGet() }
    fun check(ticket: Ticket) {
        if (epoch.get() != ticket.epoch || currentUid() != ticket.uid)
            throw WorkspaceRequestSupersededException()
    }
}
