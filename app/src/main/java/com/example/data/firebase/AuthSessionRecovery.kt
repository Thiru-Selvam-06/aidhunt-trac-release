package com.example.data.firebase

import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong

/** Coordinates the real Auth listener. A cancelled loader may still finish; identity and epoch both fence publication. */
internal class AuthSessionRecovery(
    private val scope: CoroutineScope,
    private val currentUid: () -> String?,
    private val cached: (String) -> UserProfile?,
    private val load: suspend (String) -> UserProfile?,
    private val clearCache: () -> Unit,
    private val revoke: suspend () -> Unit,
    private val publish: (UserProfile?, AuthState) -> Unit
) {
    private val epoch = AtomicLong(0)
    private var refresh: Job? = null

    fun invalidate() {
        epoch.incrementAndGet()
        refresh?.cancel()
        publish(null, AuthState.Unauthenticated())
    }

    fun refresh(uid: String?) {
        val ticket = epoch.incrementAndGet()
        refresh?.cancel()
        if (uid.isNullOrBlank() || currentUid() != uid) {
            publish(null, AuthState.Unauthenticated())
            return
        }
        val restored = cached(uid)?.takeIf { it.uid == uid }
        publish(restored, restored?.let { AuthState.Authenticated(it) } ?: AuthState.Loading)
        refresh = scope.launch {
            fun active() = epoch.get() == ticket && currentUid() == uid
            try {
                val profile = try {
                    withTimeout(10_000L) { load(uid) }
                } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
                    // A verification timeout is connectivity failure, not explicit logout/cancellation.
                    throw java.io.IOException("Profile verification timed out", timeout)
                }
                if (!active()) return@launch
                if (profile != null && profile.uid != uid) throw SecurityException("Authenticated profile identity mismatch")
                publish(profile, profile?.let { AuthState.Authenticated(it) }
                    ?: AuthState.Unauthenticated("Account not registered"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!active()) return@launch
                if (e is FirebaseAuthInvalidUserException) {
                    revoke()
                    return@launch
                }
                try {
                    val profile = recoverVerifiedProfile(e) { cached(uid)?.takeIf { it.uid == uid } }
                    if (active()) publish(profile, AuthState.Authenticated(profile))
                } catch (denied: SecurityException) {
                    clearCache()
                    publish(null, AuthState.Error(denied.message ?: "Workspace access denied"))
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (unavailable: Exception) {
                    publish(null, AuthState.Error("Connect once to verify workspace access"))
                }
            }
        }
    }
}
