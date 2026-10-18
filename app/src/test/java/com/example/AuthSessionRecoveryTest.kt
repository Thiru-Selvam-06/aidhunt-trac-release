package com.example

import com.example.data.firebase.*
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class AuthSessionRecoveryTest {
    private class Harness(val scope: CoroutineScope) {
        var uid: String? = "a"
        var cache: UserProfile? = null
        var profile: UserProfile? = null
        var state: AuthState = AuthState.Idle
        var cleared = 0
        var revoked = 0
        var loader: suspend (String) -> UserProfile? = { UserProfile(uid = it) }
        val coordinator: AuthSessionRecovery = AuthSessionRecovery(scope, { uid }, { cache }, { loader(it) },
            { cleared++; cache = null }, { revoked++; uid = null; coordinatorInvalidate() },
            { p, s -> profile = p; state = s })
        fun coordinatorInvalidate(): Unit = coordinator.invalidate()
    }

    @Test fun delayedRecoveryCannotRestoreAfterLogout() = runTest {
        val h = Harness(backgroundScope)
        val response = CompletableDeferred<UserProfile>()
        h.loader = { withContext(NonCancellable) { response.await() } }
        h.coordinator.refresh("a"); runCurrent()
        h.uid = null; h.coordinator.invalidate()
        response.complete(UserProfile(uid = "a")); runCurrent()
        assertNull(h.profile); assertTrue(h.state is AuthState.Unauthenticated)
    }

    @Test fun lateAccountAResponseCannotReplaceB() = runTest {
        val h = Harness(backgroundScope); val response = CompletableDeferred<UserProfile>()
        h.loader = { uid -> if (uid == "a") withContext(NonCancellable) { response.await() } else UserProfile(uid = uid) }
        h.coordinator.refresh("a"); runCurrent()
        h.uid = "b"; h.coordinator.refresh("b"); runCurrent()
        response.complete(UserProfile(uid = "a")); runCurrent()
        assertEquals("b", h.profile?.uid); assertEquals("b", (h.state as AuthState.Authenticated).profile.uid)
    }

    @Test fun olderResponseForSameUidCannotReplaceNewRefresh() = runTest {
        val h = Harness(backgroundScope); val response = CompletableDeferred<UserProfile>()
        h.loader = { withContext(NonCancellable) { response.await() } }
        h.coordinator.refresh("a"); runCurrent()
        h.loader = { UserProfile(uid = it, displayName = "fresh") }
        h.coordinator.refresh("a"); runCurrent()
        response.complete(UserProfile(uid = "a", displayName = "stale")); runCurrent()
        assertEquals("fresh", h.profile?.displayName)
    }

    @Test fun signedOutFirebaseIdentityCannotUseRoomOrProfileCache() = runTest {
        val h = Harness(backgroundScope); h.uid = null; h.cache = UserProfile(uid = "a")
        var requested = false; h.loader = { requested = true; UserProfile(uid = it) }
        h.coordinator.refresh("a"); runCurrent()
        assertFalse(requested); assertNull(h.profile); assertTrue(h.state is AuthState.Unauthenticated)
    }

    @Test fun differentUidCacheIsNeverDisplayedOnOfflineStartup() = runTest {
        val h = Harness(backgroundScope); h.cache = UserProfile(uid = "b")
        h.loader = { throw IOException("offline") }
        h.coordinator.refresh("a"); runCurrent()
        assertNull(h.profile); assertTrue(h.state is AuthState.Error)
    }

    @Test fun validUidCacheSurvivesNetworkLossWithoutInventingRole() = runTest {
        val h = Harness(backgroundScope); h.cache = UserProfile(uid = "a")
        h.loader = { throw IOException("offline") }
        h.coordinator.refresh("a"); runCurrent()
        assertEquals("a", h.profile?.uid); assertNull(h.profile?.role)
        assertTrue(h.state is AuthState.Authenticated); assertEquals(0, h.cleared)
    }

    @Test fun onlineVerificationReplacesCachedProfile() = runTest {
        val h = Harness(backgroundScope); h.cache = UserProfile(uid = "a", displayName = "cached")
        h.loader = { UserProfile(uid = it, displayName = "verified") }
        h.coordinator.refresh("a"); runCurrent()
        assertEquals("verified", h.profile?.displayName)
    }

    @Test fun permissionDeniedClearsCacheAndAuthenticatedState() = denial(FirebaseFirestoreException.Code.PERMISSION_DENIED)
    @Test fun unauthenticatedClearsCacheAndAuthenticatedState() = denial(FirebaseFirestoreException.Code.UNAUTHENTICATED)
    private fun denial(code: FirebaseFirestoreException.Code) = runTest {
        val h = Harness(backgroundScope); h.cache = UserProfile(uid = "a")
        h.loader = { throw FirebaseFirestoreException("denied", code) }
        h.coordinator.refresh("a"); runCurrent()
        assertNull(h.profile); assertNull(h.cache); assertEquals(1, h.cleared); assertTrue(h.state is AuthState.Error)
    }

    @Test fun mismatchedServerProfileCannotAuthenticateOrFallback() = runTest {
        val h = Harness(backgroundScope); h.cache = UserProfile(uid = "a")
        h.loader = { UserProfile(uid = "b") }
        h.coordinator.refresh("a"); runCurrent()
        assertNull(h.profile); assertEquals(1, h.cleared); assertTrue(h.state is AuthState.Error)
    }

    @Test fun cancellationDoesNotPublishNetworkErrorOrRecoverCache() = runTest {
        val h = Harness(backgroundScope); h.loader = { throw CancellationException("cancelled") }
        h.coordinator.refresh("a"); runCurrent()
        assertNull(h.profile); assertTrue(h.state is AuthState.Loading); assertEquals(0, h.cleared)
    }
    @Test fun disabledUserRevokesSessionInsteadOfRecoveringCache() = runTest {
        val h = Harness(backgroundScope); h.cache = UserProfile(uid = "a")
        h.loader = { throw com.google.firebase.auth.FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "disabled") }
        h.coordinator.refresh("a"); runCurrent()
        assertEquals(1, h.revoked); assertNull(h.uid); assertNull(h.profile)
        assertTrue(h.state is AuthState.Unauthenticated)
    }

    @Test fun missingProfileDoesNotInventOwnerOrWorkspace() = runTest {
        val h = Harness(backgroundScope); h.loader = { null }
        h.coordinator.refresh("a"); runCurrent()
        assertNull(h.profile); assertTrue(h.state is AuthState.Unauthenticated)
    }

    @Test fun verificationTimeoutLeavesLoadingAndOffersRecovery() = runTest {
        val h = Harness(backgroundScope); h.loader = { awaitCancellation() }
        h.coordinator.refresh("a"); runCurrent(); advanceTimeBy(10_001); runCurrent()
        assertNull(h.profile); assertTrue(h.state is AuthState.Error)
    }

    @Test fun verificationTimeoutPreservesValidOfflineSession() = runTest {
        val h = Harness(backgroundScope); h.cache = UserProfile(uid = "a")
        h.loader = { awaitCancellation() }
        h.coordinator.refresh("a"); runCurrent(); advanceTimeBy(10_001); runCurrent()
        assertEquals("a", h.profile?.uid); assertTrue(h.state is AuthState.Authenticated)
        assertEquals(0, h.cleared)
    }

}
