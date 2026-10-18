package com.example

import com.example.data.firebase.UserProfile
import com.example.data.firebase.recoverVerifiedProfile
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class VerifiedProfileRecoveryTest {
    @Test fun confirmedRemovalNeverReturnsPreviouslyVerifiedProfile() {
        val denial = SecurityException("Membership removed")
        var cacheRead = false
        try {
            recoverVerifiedProfile(denial) { cacheRead = true; UserProfile(uid = "removed-partner") }
            fail("Denied membership must not recover from cache")
        } catch (actual: SecurityException) { assertSame(denial, actual) }
        assertFalse(cacheRead)
    }

    @Test fun temporaryNetworkFailureRetainsPreviouslyVerifiedOfflineSession() {
        val profile = UserProfile(uid = "active-partner", defaultWorkspaceId = "test-workspace")
        assertSame(profile, recoverVerifiedProfile(IOException("Offline")) { profile })
    }

    @Test fun unavailableVerificationWithoutCachePreservesOriginalFailure() {
        val failure = IOException("Offline")
        try {
            recoverVerifiedProfile(failure) { null }
            fail("Unverified account cannot enter")
        } catch (actual: IOException) { assertSame(failure, actual) }
    }
    @Test fun firestorePermissionDeniedNeverUsesCacheAsNetworkRecovery() {
        val denial = com.google.firebase.firestore.FirebaseFirestoreException("denied",
            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED)
        var cacheRead = false
        try {
            recoverVerifiedProfile(denial) { cacheRead = true; UserProfile(uid = "fixture") }
            fail("Explicit authorization denial must not recover a cached profile")
        } catch (actual: SecurityException) { assertSame(denial, actual.cause) }
        assertFalse(cacheRead)
    }

    @Test fun firestoreUnauthenticatedNeverUsesCacheAsAuthentication() {
        val denial = com.google.firebase.firestore.FirebaseFirestoreException("signed out",
            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAUTHENTICATED)
        var cacheRead = false
        try {
            recoverVerifiedProfile(denial) { cacheRead = true; UserProfile(uid = "fixture") }
            fail("Rejected authentication must not recover a cached profile")
        } catch (actual: SecurityException) { assertSame(denial, actual.cause) }
        assertFalse(cacheRead)
    }

    @Test fun firestoreUnavailableRetainsOfflineRecoveryWithoutClaimingFreshVerification() {
        val profile = UserProfile(uid = "fixture", role = null)
        val offline = com.google.firebase.firestore.FirebaseFirestoreException("offline",
            com.google.firebase.firestore.FirebaseFirestoreException.Code.UNAVAILABLE)
        assertSame(profile, recoverVerifiedProfile(offline) { profile })
    }

    @Test fun cancelledSessionCannotRecoverEvenIfOldProfileExists() {
        var cacheRead = false
        val cancelled = kotlinx.coroutines.CancellationException("old session")
        try { recoverVerifiedProfile(cancelled) { cacheRead = true; UserProfile(uid = "old") }; fail("Cancellation must propagate") }
        catch (actual: kotlinx.coroutines.CancellationException) { assertSame(cancelled, actual) }
        assertFalse(cacheRead)
    }

    @Test fun disabledFirebaseUserCannotRecoverFromCache() {
        val invalid = com.google.firebase.auth.FirebaseAuthInvalidUserException("ERROR_USER_DISABLED", "disabled fixture")
        var cacheRead = false
        try { recoverVerifiedProfile(invalid) { cacheRead = true; UserProfile(uid = "fixture") }; fail("Disabled Auth user must not recover") }
        catch (actual: com.google.firebase.auth.FirebaseAuthInvalidUserException) { assertSame(invalid, actual) }
        assertFalse(cacheRead)
    }
}
