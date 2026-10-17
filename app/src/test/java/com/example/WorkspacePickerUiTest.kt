package com.example
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.firebase.Workspace
import com.example.ui.screens.auth.WorkspaceRecoveryScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36])
class WorkspacePickerUiTest {
    @get:Rule val compose=createComposeRule()
    @Test fun identicalNamesKeepWorkspaceAndRoleSelectionDistinct() {
        var selected=""
        compose.setContent { MyApplicationTheme { WorkspaceRecoveryScreen("Choose", listOf(
            Workspace(workspaceId="personal",name="Business",accessRole="Owner"),
            Workspace(workspaceId="shared",name="Business",accessRole="Partner")), false,
            {}, { selected=it }, { _, _ -> }, {}) } }
        compose.onNodeWithText("Business · Owner").assertExists()
        compose.onNodeWithText("Business · Partner").performClick()
        assertEquals("shared", selected)
        compose.onNodeWithText("Create or recover personal workspace").assertDoesNotExist()
    }
}
