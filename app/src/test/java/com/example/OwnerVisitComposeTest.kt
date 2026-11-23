package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.data.sync.OwnerVisit
import com.example.data.sync.OwnerVisitKey
import com.example.data.sync.OwnerVisitStatus
import com.example.ui.components.OwnerVisitHeader
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class OwnerVisitComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test fun successfulAndPartialRefreshShowActualCheckpointAndSafeDiagnostic() {
        val ready = OwnerVisit(OwnerVisitKey("workspace", "owner"), 123, 1794992400000L, OwnerVisitStatus.READY)
        compose.setContent { Column {
            OwnerVisitHeader(ready, false, false)
            OwnerVisitHeader(ready.copy(status = OwnerVisitStatus.PARTIAL, diagnostics = setOf("EXPENSES/PERMISSION_OR_APP_CHECK/403")), false, true)
        } }
        compose.onNodeWithText("Last Updated", substring = true).assertIsDisplayed()
        compose.onNodeWithText("கடைசியாக புதுப்பித்தது", substring = true).assertIsDisplayed()
        compose.onNodeWithText("EXPENSES/PERMISSION_OR_APP_CHECK/403").assertIsDisplayed()
    }

    @Test fun cachedStateAndDefinitiveEmptyHaveDistinctLocalizedLabels() {
        val visit = OwnerVisit(OwnerVisitKey("workspace", "owner"), 123, null, OwnerVisitStatus.OFFLINE)
        compose.setContent {
            Column {
                OwnerVisitHeader(visit, false, false)
                OwnerVisitHeader(visit.copy(status = OwnerVisitStatus.READY), false, true)
            }
        }
        compose.onNodeWithText("Since Your Last Visit").assertIsDisplayed()
        compose.onNodeWithText("Offline · Showing saved notifications").assertIsDisplayed()
        compose.onNodeWithText("கடைசி வருகைக்குப் பிறகு").assertIsDisplayed()
        compose.onNodeWithText("கடைசி வருகைக்குப் பிறகு புதிய பங்குதாரர் செயல்பாடு இல்லை.").assertIsDisplayed()
        compose.onNodeWithText("No new Partner activity since your last visit.").assertDoesNotExist()
    }
}
