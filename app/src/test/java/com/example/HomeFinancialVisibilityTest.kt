package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.example.data.entity.AppSettingsEntity
import com.example.ui.screens.home.HomeScreen
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
class HomeFinancialVisibilityTest {
    @get:Rule val compose = createComposeRule()
    private fun show(role: String) {
        compose.setContent {
            MyApplicationTheme {
                HomeScreen(settings = AppSettingsEntity(workspaceId = "a"), recentJobs = emptyList(),
                    isOwner = role == "OWNER", authenticatedUserRole = role, availableBalance = 70.0, onDeleteJob = {})
            }
        }
    }
    @Test fun operatorDoesNotSeeCashCalculatedWithoutExpensesAndWithdrawals() {
        show("OPERATOR")
        compose.onNodeWithText("Available Business Balance").assertDoesNotExist()
        compose.onNodeWithText("Total Recorded").assertExists()
    }
    @Test fun ownerRetainsBusinessCashCard() {
        show("OWNER")
        compose.onNodeWithText("Available Business Balance").assertExists()
    }
    @Test fun partnerRetainsBusinessCashCard() {
        show("PARTNER")
        compose.onNodeWithText("Available Business Balance").assertExists()
    }
}
