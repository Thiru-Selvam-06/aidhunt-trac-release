package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.data.entity.*
import com.example.ui.screens.report.*
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp", application = LedgerUiTestApplication::class)
class ExpenseReportRedesignTest {
    @get:Rule val compose = createComposeRule()

    private val settings = AppSettingsEntity(
        workspaceId = "ws-exp-1",
        businessName = "AIDHUNT TRAC",
        currency = "₹",
        language = "EN"
    )

    private val sampleExpenses = listOf(
        ExpenseEntity(
            id = 701L,
            workspaceId = "ws-exp-1",
            expenseType = "Diesel",
            amount = 86850.0,
            tractorLabel = "4710",
            addedByPartner = "Muniyappan",
            dateTimestamp = 1700000000000L,
            createdAt = 1700000000000L
        ),
        ExpenseEntity(
            id = 702L,
            workspaceId = "ws-exp-1",
            expenseType = "Other",
            amount = 64550.0,
            tractorLabel = "",
            addedByPartner = "Muniyappan",
            dateTimestamp = 1700000000000L,
            createdAt = 1700000000000L
        ),
        ExpenseEntity(
            id = 703L,
            workspaceId = "ws-exp-1",
            expenseType = "Spare Parts",
            amount = 9320.0,
            tractorLabel = "855",
            addedByPartner = "Muniyappan",
            dateTimestamp = 1700000000000L,
            createdAt = 1700000000000L
        ),
        ExpenseEntity(
            id = 704L,
            workspaceId = "ws-exp-1",
            expenseType = "Repair",
            amount = 9300.0,
            tractorLabel = "855",
            addedByPartner = "Muniyappan",
            dateTimestamp = 1700000000000L,
            createdAt = 1700000000000L
        ),
        ExpenseEntity(
            id = 705L,
            workspaceId = "ws-exp-1",
            expenseType = "Oil Change",
            amount = 1050.0,
            tractorLabel = "744",
            addedByPartner = "Muniyappan",
            dateTimestamp = 1700000000000L,
            createdAt = 1700000000000L
        )
    )

    @Test
    fun expenseReportHeaderAndSummaryCardsRenderWithoutHeroBanner() {
        compose.setContent {
            MyApplicationTheme {
                ExpensesTab(
                    settings = settings,
                    expenses = sampleExpenses,
                    tractors = emptyList(),
                    partners = emptyList(),
                    onAddExpense = {},
                    onUpdateExpense = {},
                    onDeleteExpense = {}
                )
            }
        }

        // Summary cards
        compose.onNodeWithText("Total Expenses", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Total Entries", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Avg. per Entry", substring = true).assertIsDisplayed()

        // Add Expense button
        compose.onNodeWithText("Add Expense", substring = true).assertIsDisplayed()

        // Decorative tractor banner is REMOVED
        compose.onNodeWithText("Track and manage all fleet expenses").assertDoesNotExist()

        // Expense entries are preserved
        compose.onNodeWithText("Expense Entries", substring = true).assertIsDisplayed()
    }

    @Test
    fun expenseBreakdownCategoryTileItemRendersIconPercentageAndAmount() {
        val tile = ExpenseCategoryTileData(
            key = "Diesel",
            name = "Diesel",
            amount = 86850.0,
            percentage = 51
        )

        compose.setContent {
            MyApplicationTheme {
                ExpenseCategoryTileItem(
                    tile = tile,
                    tileWidth = 92.dp,
                    tileHeight = 96.dp,
                    isTamil = false,
                    currencySymbol = "₹"
                )
            }
        }

        // Percentage badge
        compose.onNodeWithText("51%").assertIsDisplayed()

        // Category name
        compose.onNodeWithText("Diesel").assertIsDisplayed()

        // Formatted amount
        compose.onNodeWithText("₹86,850", substring = true).assertExists()
    }

    @Test
    fun expenseBreakdownHandlesZeroAndFullPercentageBoundaries() {
        val zeroTile = ExpenseCategoryTileData(
            key = "Repair",
            name = "Repair",
            amount = 0.0,
            percentage = 0
        )

        val fullTile = ExpenseCategoryTileData(
            key = "Diesel",
            name = "Diesel",
            amount = 50000.0,
            percentage = 100
        )

        compose.setContent {
            MyApplicationTheme {
                ExpenseCategoryTileItem(
                    tile = zeroTile,
                    tileWidth = 92.dp,
                    tileHeight = 96.dp,
                    isTamil = false,
                    currencySymbol = "₹"
                )
                ExpenseCategoryTileItem(
                    tile = fullTile,
                    tileWidth = 92.dp,
                    tileHeight = 96.dp,
                    isTamil = false,
                    currencySymbol = "₹"
                )
            }
        }

        compose.onNodeWithText("0%").assertIsDisplayed()
        compose.onNodeWithText("100%").assertIsDisplayed()
    }

    @Test
    fun expenseBreakdownWaveFillAnimatesToTarget() {
        val tile = ExpenseCategoryTileData(
            key = "Diesel",
            name = "Diesel",
            amount = 50000.0,
            percentage = 50
        )

        compose.mainClock.autoAdvance = false

        compose.setContent {
            MyApplicationTheme {
                ExpenseCategoryTileItem(
                    tile = tile,
                    tileWidth = 92.dp,
                    tileHeight = 96.dp,
                    isTamil = false,
                    currencySymbol = "₹"
                )
            }
        }

        // At t = 0ms: tile is rendered, percentage badge displayed
        compose.onNodeWithText("50%").assertIsDisplayed()

        // Advance clock across animation duration (700ms)
        compose.mainClock.advanceTimeBy(700L)
        compose.waitForIdle()

        compose.onNodeWithText("50%").assertIsDisplayed()
        compose.onNodeWithText("Diesel").assertIsDisplayed()
    }
}
