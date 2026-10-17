package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.WorkTypeExtensionEntity
import com.example.ui.screens.account.ManageTractorsPage
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ExtensionDetailsNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun sharedExtensionOpensDetailsWithoutGrantingMutationControls() {
        compose.setContent {
            MyApplicationTheme {
                ManageTractorsPage(
                    settings = AppSettingsEntity(workspaceId = "stage"),
                    tractors = emptyList(), onAddTractor = {}, onUpdateTractor = {}, onDeleteTractor = {},
                    canAddExtension = false,
                    workTypeExtensions = listOf(WorkTypeExtensionEntity(id = 17, workspaceId = "stage", name = "Shared Rotavator")),
                    canMutateAsset = { false }
                )
            }
        }
        compose.onNodeWithTag("extension_card_17").performScrollTo().performClick()
        compose.onNodeWithText("Extension Details").assertExists()
        compose.onNodeWithText("Shared workspace work type").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("extension_card_17").assertExists()
    }
}
