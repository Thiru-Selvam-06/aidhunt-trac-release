package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.screens.auth.AuthMethod
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class PhoneOnlySignupRoutingTest {
    @get:Rule val compose = createComposeRule()
    @Test fun emailSignupShortcutUsesPhoneOtpAndNeverInvokesEmailSignup() {
        var unsupportedSignup = false
        compose.setContent { MyApplicationTheme {
            LoginScreen(partners = emptyList(), isTamil = false, initialAuthMethod = AuthMethod.EMAIL,
                onLoginSuccess = { _, _ -> }, onEmailSignUpRequested = { _, _ -> unsupportedSignup = true })
        } }
        compose.onNodeWithText("Phone signup").performClick()
        compose.onNodeWithText("Phone Number").assertExists()
        compose.onNodeWithText("Get Login OTP").assertExists()
        assertFalse(unsupportedSignup)
    }
    @Test fun missingGoogleAccountUsesPhoneOtpWithoutGoogleWorkspaceCreation() {
        var unsupportedSignup = false
        var cancelled = false
        compose.setContent { MyApplicationTheme {
            LoginScreen(partners = emptyList(), isTamil = false, onLoginSuccess = { _, _ -> },
                isGoogleAccountNotFound = true, onGoogleCreateAccountRequested = { unsupportedSignup = true },
                onGoogleCancelRequested = { cancelled = true })
        } }
        compose.onNodeWithText("Create via Phone OTP").performClick()
        compose.onNodeWithText("Phone Number").assertExists()
        assertFalse(unsupportedSignup)
        assertTrue(cancelled)
    }
}
