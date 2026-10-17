package com.example

import com.example.data.firebase.UserProfile
import com.example.data.firebase.recoverVerifiedProfile
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

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
}
