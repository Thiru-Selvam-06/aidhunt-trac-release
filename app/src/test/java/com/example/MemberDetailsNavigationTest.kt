package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.PartnerEntity
import com.example.ui.screens.account.ManagePartnersPage
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class MemberDetailsNavigationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tappingMemberOpensMatchingDetailsAndBackReturnsToList() {
        compose.setContent {
            MyApplicationTheme {
                ManagePartnersPage(
                    settings = AppSettingsEntity(workspaceId = "stage", ownerName = "Owner Test"),
                    partners = listOf(PartnerEntity(id = 71, workspaceId = "stage", name = "Disposable Partner", phone = "9000000001")),
                    activePartnerName = "",
                    onAddPartner = {}, onUpdatePartner = {}, onDeletePartner = {}, onSwitchActivePartner = {}
                )
            }
        }
        compose.onNodeWithTag("partner_card_71").performScrollTo().performClick()
        compose.onNodeWithText("Member Details").assertExists()
        compose.onNodeWithText("Role: Partner").assertExists()
        compose.onNodeWithText("Phone: 9000000001").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithText("Member Details").assertDoesNotExist()
        compose.onNodeWithTag("partner_card_71").assertExists()
    }
}
