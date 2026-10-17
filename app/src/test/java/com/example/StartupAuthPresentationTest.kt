package com.example

import com.example.data.firebase.AuthState
import com.example.data.firebase.UserProfile
import com.example.ui.util.shouldShowStartupSplash
import org.junit.Assert.*
import org.junit.Test

class StartupAuthPresentationTest {
    @Test fun interactiveLoadingKeepsRegistrationAndOtpFormMounted() {
        assertFalse(shouldShowStartupSplash(true, AuthState.Loading, false))
        assertFalse(shouldShowStartupSplash(true, AuthState.CodeSent("fixture-session", "fixture-phone"), false))
        assertFalse(shouldShowStartupSplash(true, AuthState.Unauthenticated("Account not registered"), false))
    }
    @Test fun coldStartStillWaitsForInitialSessionResolution() {
        assertTrue(shouldShowStartupSplash(false, AuthState.Loading, false))
    }
    @Test fun failedWorkspaceOffersRecoveryWithoutLoggingOut() {
        assertFalse(shouldShowStartupSplash(true, AuthState.Authenticated(UserProfile(uid = "fixture")), false, workspaceFailed = true))
    }
    @Test fun authenticatedAccountStillWaitsForAuthorizedWorkspace() {
        val state = AuthState.Authenticated(UserProfile(uid = "fixture"))
        assertTrue(shouldShowStartupSplash(true, state, false))
        assertFalse(shouldShowStartupSplash(true, state, true))
    }
}
