package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.firebase.WorkspaceMember
import com.example.ui.screens.report.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.PartnerFinancialBreakdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp", application = LedgerUiTestApplication::class)
class BusinessOverviewRedesignTest {
    @get:Rule val compose = createComposeRule()

    private val settings = AppSettingsEntity(
        workspaceId = "ws-biz-1",
        businessName = "AIDHUNT TRAC",
        currency = "₹",
        language = "EN"
    )

    private val members = listOf(
        WorkspaceMember(uid = "uid-1", workspaceId = "ws-biz-1", displayName = "Ravi", role = "OWNER", status = "ACTIVE"),
        WorkspaceMember(uid = "uid-2", workspaceId = "ws-biz-1", displayName = "Suresh", role = "PARTNER", status = "ACTIVE")
    )

    private val sampleJobs = listOf(
        JobEntryEntity(
            id = 501L,
            workspaceId = "ws-biz-1",
            customerId = 101L,
            customerName = "Karthik Farm",
            operatorName = "Ravi",
            tractorLabel = "4710",
            workType = "Rotavator",
            startTimeMillis = 1700000000000L,
            endTimeMillis = 1700003600000L,
            durationMinutes = 60L,
            addedByPartner = "Ravi",
            totalAmount = 60000.0,
            amountReceived = 40000.0,
            pendingAmount = 20000.0,
            createdAt = 1700000000000L,
            createdByUid = "uid-1",
            entryForUid = "uid-1",
            financialAttributionUid = "uid-1"
        ),
        JobEntryEntity(
            id = 502L,
            workspaceId = "ws-biz-1",
            customerId = 102L,
            customerName = "Murugan Land",
            operatorName = "Suresh",
            tractorLabel = "855",
            workType = "Cultivator",
            startTimeMillis = 1700005000000L,
            endTimeMillis = 1700008600000L,
            durationMinutes = 60L,
            addedByPartner = "Suresh",
            totalAmount = 40000.0,
            amountReceived = 20000.0,
            pendingAmount = 20000.0,
            createdAt = 1700005000000L,
            createdByUid = "uid-2",
            entryForUid = "uid-2",
            financialAttributionUid = "uid-2"
        )
    )

    private val sampleExpenses = listOf(
        ExpenseEntity(
            id = 601L,
            workspaceId = "ws-biz-1",
            expenseType = "Diesel",
            tractorLabel = "855",
            addedByPartner = "Ravi",
            amount = 15000.0,
            createdAt = 1700000000000L
        )
    )

    @Test
    fun sixFinancialSummaryCardsRenderCorrectLabelsValuesAndCallbacks() {
        var navigatedToExpenses = false
        var navigatedToWithdrawals = false

        compose.setContent {
            MyApplicationTheme {
                BusinessOverviewScreen(
                    settings = settings,
                    jobs = sampleJobs,
                    expenses = sampleExpenses,
                    workspaceMembers = members,
                    currentUid = "uid-1",
                    onNavigateToExpenses = { navigatedToExpenses = true },
                    onNavigateToWithdrawals = { navigatedToWithdrawals = true }
                )
            }
        }

        // 1. Total Recorded Card
        compose.onNodeWithText("Total Recorded", substring = true).assertIsDisplayed()
        compose.onNodeWithText("₹100,000", substring = true).assertIsDisplayed()

        // 2. Total Collected Card
        compose.onNodeWithText("Total Collected", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("₹60,000", substring = true)[0].assertIsDisplayed()

        // 3. Total Due Card
        compose.onNodeWithText("Total Due", substring = true).assertIsDisplayed()
        compose.onAllNodesWithText("₹40,000", substring = true)[0].assertIsDisplayed()

        // 4. Available Business Balance Card
        compose.onNodeWithText("Available Business", substring = true).assertIsDisplayed()

        // 5. Expenses Card - Click invokes onNavigateToExpenses
        compose.onAllNodesWithText("Expenses", substring = true)[0].assertIsDisplayed()
        compose.onAllNodesWithText("₹15,000", substring = true)[0].assertIsDisplayed()
        compose.onNodeWithText("Tap to view expenses", substring = true).performClick()
        assertTrue(navigatedToExpenses)

        // 6. Partner Withdrawals Card - Click invokes onNavigateToWithdrawals
        compose.onAllNodesWithText("Partner Withdrawals", substring = true)[0].assertIsDisplayed()
        compose.onNodeWithText("Tap to view withdrawals", substring = true).performClick()
        assertTrue(navigatedToWithdrawals)
    }

    @Test
    fun driverEarningsChartsDisplayMembersPercentagesAndLegends() {
        val breakdowns = listOf(
            PartnerFinancialBreakdown(partnerUid = "uid-1", partnerName = "Ravi", role = "OWNER", recorded = 60000.0, received = 40000.0, due = 20000.0),
            PartnerFinancialBreakdown(partnerUid = "uid-2", partnerName = "Suresh", role = "PARTNER", recorded = 40000.0, received = 20000.0, due = 20000.0)
        )

        compose.setContent {
            MyApplicationTheme {
                DriverEarningsCircularChart(
                    breakdowns = breakdowns,
                    isTamil = false,
                    currency = "₹",
                    showExpenses = false
                )
            }
        }

        // Member names and amounts visible
        compose.onNodeWithText("Ravi", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Suresh", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Collected: ₹40,000", useUnmergedTree = true).assertIsDisplayed()
        compose.onAllNodesWithText("Due: ₹20,000", substring = true).assertCountEquals(2)
    }

    @Test
    fun recordsListNormalStateClickInvokesOnView() {
        var viewClicked = false
        val job = sampleJobs[0]

        compose.setContent {
            MyApplicationTheme {
                SwipeableRecordCard(
                    job = job,
                    settings = settings,
                    currency = "₹",
                    isTamil = false,
                    isSwiped = false,
                    onSwipe = {},
                    onView = { viewClicked = true },
                    onShare = {}
                )
            }
        }

        compose.onNodeWithText("Karthik Farm").assertIsDisplayed()
        compose.onNodeWithText("₹60,000").assertIsDisplayed()
        compose.onNodeWithText("Karthik Farm").performClick()
        assertTrue(viewClicked)
    }

    @Test
    fun recordsListSwipedStateRevealsViewAndShareActionsAndDirectHitTestingWorks() {
        val job = sampleJobs[0]
        var revealedViewClicked = false
        var revealedShareClicked = false

        compose.setContent {
            MyApplicationTheme {
                SwipeableRecordCard(
                    job = job,
                    settings = settings,
                    currency = "₹",
                    isTamil = false,
                    isSwiped = true,
                    onSwipe = {},
                    onView = { revealedViewClicked = true },
                    onShare = { revealedShareClicked = true }
                )
            }
        }

        // Regression test for foreground hit-testing:
        // View and Share buttons sit in LAYER 2 (top of Z-order) and directly capture touch events.
        compose.onNodeWithText("View").assertIsDisplayed().performClick()
        assertTrue(revealedViewClicked)

        compose.onNodeWithText("Share").assertIsDisplayed().performClick()
        assertTrue(revealedShareClicked)
    }
}
