package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.firebase.Workspace
import com.example.ui.screens.auth.WorkspaceRecoveryScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36])
class WorkspacePickerUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun identicalNamesKeepWorkspaceAndRoleSelectionDistinct() {
        var selected = ""
        compose.setContent {
            MyApplicationTheme {
                WorkspaceRecoveryScreen(
                    message = "Choose",
                    workspaces = listOf(
                        Workspace(workspaceId = "personal", name = "Business", accessRole = "Owner"),
                        Workspace(workspaceId = "shared", name = "Business", accessRole = "Partner")
                    ),
                    canCreate = false,
                    onRetry = {},
                    onSwitch = { selected = it },
                    onCreate = { _, _ -> },
                    onLogout = {}
                )
            }
        }
        compose.onNodeWithTag("btn_select_workspace_personal").assertExists()
        compose.onNodeWithText("Owner").assertExists()
        compose.onNodeWithTag("btn_select_workspace_shared").performScrollTo().performClick()
        assertEquals("shared", selected)
        compose.onNodeWithText("Create or recover personal workspace").assertDoesNotExist()
    }

    @Test fun threeWorkspacesRenderDistinctOptionsAndCanBeSelected() {
        var selected = ""
        val threeWorkspaces = listOf(
            Workspace(workspaceId = "ws_G7RZespbU6cwIUha", name = "PMS Production", accessRole = "Owner"),
            Workspace(workspaceId = "ws_cf_staging_20261015_7361_7f4c92", name = "AIDHUNT Disposable Staging", accessRole = "Owner"),
            Workspace(workspaceId = "ws_cf_staging_20261015_login_a62e71", name = "AIDHUNT Login Retest", accessRole = "Owner")
        )

        compose.setContent {
            MyApplicationTheme {
                WorkspaceRecoveryScreen(
                    message = "Choose the workspace and role you want to use.",
                    workspaces = threeWorkspaces,
                    canCreate = true,
                    onRetry = {},
                    onSwitch = { selected = it },
                    onCreate = { _, _ -> },
                    onLogout = {}
                )
            }
        }

        // Verify all three workspace options are displayed
        compose.onNodeWithTag("btn_select_workspace_ws_G7RZespbU6cwIUha").assertExists()
        compose.onNodeWithTag("btn_select_workspace_ws_cf_staging_20261015_7361_7f4c92").assertExists()
        compose.onNodeWithTag("btn_select_workspace_ws_cf_staging_20261015_login_a62e71").assertExists()

        // Select the second workspace
        compose.onNodeWithTag("btn_select_workspace_ws_cf_staging_20261015_7361_7f4c92").performScrollTo().performClick()
        assertEquals("ws_cf_staging_20261015_7361_7f4c92", selected)

        // Select the third workspace
        compose.onNodeWithTag("btn_select_workspace_ws_cf_staging_20261015_login_a62e71").performScrollTo().performClick()
        assertEquals("ws_cf_staging_20261015_login_a62e71", selected)

        // Select the first workspace
        compose.onNodeWithTag("btn_select_workspace_ws_G7RZespbU6cwIUha").performScrollTo().performClick()
        assertEquals("ws_G7RZespbU6cwIUha", selected)
    }

    @Test fun singleWorkspaceDoesNotDisplayWorkspaceRecoveryButtonsWhenNoneGiven() {
        compose.setContent {
            MyApplicationTheme {
                WorkspaceRecoveryScreen(
                    message = "No active workspace.",
                    workspaces = emptyList(),
                    canCreate = true,
                    onRetry = {},
                    onSwitch = {},
                    onCreate = { _, _ -> },
                    onLogout = {}
                )
            }
        }
        compose.onNodeWithText("Create or recover personal workspace").assertExists()
        compose.onNodeWithText("Retry workspace verification").assertExists()
    }
    @Test @Config(qualifiers = "w320dp-h640dp")
    fun longWorkspaceNamesWrapAndActionsKeepTheSameWidthOnSmallPhone() {
        var selected = ""
        val title = "A long workspace name that must wrap without pushing its role or action outside the screen"
        compose.setContent { MyApplicationTheme {
            WorkspaceRecoveryScreen("Choose the workspace to open.",
                listOf(Workspace(workspaceId = "shared", name = title, accessRole = "Partner")), false,
                {}, { selected = it }, { _, _ -> }, {})
        } }
        val card = compose.onNodeWithTag("btn_select_workspace_shared").performScrollTo().assertIsDisplayed()
        val cardBounds = card.fetchSemanticsNode().boundsInRoot
        val retryBounds = compose.onNodeWithText("Retry workspace verification").performScrollTo().fetchSemanticsNode().boundsInRoot
        assertEquals(cardBounds.left, retryBounds.left, 1f)
        assertEquals(cardBounds.width, retryBounds.width, 1f)
        assertTrue(cardBounds.width > 200f)
        card.performScrollTo().performClick()
        assertEquals("shared", selected)
        compose.onNodeWithText("Create or recover personal workspace").assertDoesNotExist()
    }

    @Test fun recoveryActionsRemainAvailableAfterChooserLayoutChange() {
        var retries = 0
        var logouts = 0
        compose.setContent { MyApplicationTheme {
            WorkspaceRecoveryScreen("Could not verify workspace access.", emptyList(), false,
                { retries++ }, {}, { _, _ -> }, { logouts++ })
        } }
        compose.onNodeWithText("Retry workspace verification").performScrollTo().performClick()
        compose.onNodeWithText("Sign out").performScrollTo().performClick()
        assertEquals(1, retries)
        assertEquals(1, logouts)
    }

}
