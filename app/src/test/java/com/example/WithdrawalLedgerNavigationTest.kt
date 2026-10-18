package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.firebase.WorkspaceMember
import com.example.ui.screens.report.WithdrawalTab
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class WithdrawalLedgerNavigationTest {
    @get:Rule val compose = createComposeRule()

    private fun show(owner: Boolean) {
        compose.setContent {
            MyApplicationTheme {
                WithdrawalTab(
                    settings = AppSettingsEntity(workspaceId = "w1", ownerName = "Owner Test", businessName = "Test Business"),
                    withdrawals = listOf(WithdrawalEntity(id = 77, workspaceId = "w1", partnerName = "Other Partner", amount = 100.0, targetPartnerUid = "other", createdByUid = "other")),
                    partners = emptyList(), availableAmount = 500.0, totalWithdrawn = 100.0,
                    onAddWithdrawal = {}, onDeleteWithdrawal = {}, isOwner = owner,
                    currentUid = if (owner) "owner" else "self", currentUserName = if (owner) "Owner Test" else "Self",
                    workspaceMembers = listOf(WorkspaceMember(uid = "owner", role = "Owner", displayName = "Owner Test"), WorkspaceMember(uid = "other", displayName = "Other Partner"))
                )
            }
        }
    }

    @Test fun mainPageHidesCardsAndViewAllOpensHistoryWithOwnerActions() {
        show(true)
        compose.onNodeWithTag("withdrawal_card_77").assertDoesNotExist()
        compose.onNodeWithText("View All >").performScrollTo().performClick()
        compose.onNodeWithTag("withdrawal_history").assertIsDisplayed()
        compose.onNodeWithTag("withdrawal_card_77").assertIsDisplayed()
        compose.onNodeWithTag("btn_delete_withdrawal_77").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("withdrawal_card_77").assertDoesNotExist()
    }

    @Test fun viewAllDoesNotGivePartnerDeleteAccessToOtherMembersRecords() {
        show(false)
        compose.onNodeWithText("View All >").performScrollTo().performClick()
        compose.onNodeWithTag("withdrawal_card_77").assertIsDisplayed()
        compose.onNodeWithTag("btn_delete_withdrawal_77").assertDoesNotExist()
    }
}
