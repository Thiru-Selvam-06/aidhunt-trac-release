package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsNotEnabled
import com.example.data.auth.AuthorizationManager
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.TractorEntity
import com.example.ui.screens.account.ManageTractorsPage
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class TractorCardAuthorizationTest {
    @get:Rule val compose = createComposeRule()
    private fun show(role: String, creator: String) {
        compose.setContent {
            MyApplicationTheme {
                ManageTractorsPage(
                    settings = AppSettingsEntity(workspaceId = "a"),
                    tractors = listOf(TractorEntity(id = 1, workspaceId = "a", label = "Test Tractor", chassisNo = "TEST", createdByUid = creator)),
                    onAddTractor = {}, onUpdateTractor = {}, onDeleteTractor = {},
                    canAddExtension = role != "OPERATOR",
                    canMutateAsset = { AuthorizationManager.canMutateAsset(role == "OWNER", role, "actor", it) }
                )
            }
        }
        compose.onNodeWithText("Test Tractor").performTouchInput { click() }
    }
    @Test fun partnerCannotOpenAnotherMembersTractorEditorByTappingTheCard() {
        show("PARTNER", "owner")
        compose.onNodeWithText("Edit Tractor").assertDoesNotExist()
    }
    @Test fun operatorCardRemainsReadOnlyEvenForAnOwnLegacyAsset() {
        show("OPERATOR", "actor")
        compose.onNodeWithText("Edit Tractor").assertDoesNotExist()
    }
    @Test fun operatorCannotStartTractorCreation() {
        show("OPERATOR", "actor")
        compose.onNodeWithText("Add Tractor").assertIsNotEnabled()
    }
    @Test fun partnerCanOpenOwnTractorEditor() {
        show("PARTNER", "actor")
        compose.onNodeWithText("Edit Tractor").assertExists()
    }
    @Test fun ownerCanOpenAnotherMembersTractorEditor() {
        show("OWNER", "partner")
        compose.onNodeWithText("Edit Tractor").assertExists()
    }
}
