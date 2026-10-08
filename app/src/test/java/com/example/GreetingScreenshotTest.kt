package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.ui.screens.auth.LoginScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun capture_login_screen_phone_mode() {
    composeTestRule.setContent {
      MyApplicationTheme {
        LoginScreen(
          isTamil = false,
          onLoginSuccess = { _, _ -> }
        )
      }
    }
    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/login_phone_screen.png")
  }

  @Test
  fun capture_login_screen_otp_mode() {
    composeTestRule.setContent {
      MyApplicationTheme {
        LoginScreen(
          isTamil = false,
          onLoginSuccess = { _, _ -> },
          onSendOtpRequested = { _, onSuccess, _ ->
            onSuccess()
          }
        )
      }
    }
    composeTestRule.onNodeWithTag("login_phone_input").performTextInput("8939889471")
    composeTestRule.onNodeWithTag("send_otp_button").performClick()
    composeTestRule.waitForIdle()

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/login_otp_screen.png")
  }

  @Test
  fun capture_login_screen_otp_filled_mode() {
    composeTestRule.setContent {
      MyApplicationTheme {
        LoginScreen(
          isTamil = false,
          onLoginSuccess = { _, _ -> },
          onSendOtpRequested = { _, onSuccess, _ ->
            onSuccess()
          }
        )
      }
    }
    composeTestRule.onNodeWithTag("login_phone_input").performTextInput("8939889471")
    composeTestRule.onNodeWithTag("send_otp_button").performClick()
    composeTestRule.waitForIdle()

    // Enter digits to fill circles
    composeTestRule.onNodeWithTag("login_otp_input").performTextInput("123456")
    composeTestRule.waitForIdle()

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/login_otp_filled_screen.png")
  }
}
