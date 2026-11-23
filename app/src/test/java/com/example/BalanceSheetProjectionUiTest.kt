package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
import com.example.ui.components.ExpenseAnalyticsCard
import com.example.ui.components.formatInr
import com.example.ui.screens.report.BalanceSheetTab
import com.example.ui.theme.MyApplicationTheme
import java.time.Instant
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp", application = LedgerUiTestApplication::class)
class BalanceSheetProjectionUiTest {
    @get:Rule val compose = createComposeRule()
    private val date = Instant.parse("2026-11-13T01:00:00Z").toEpochMilli()
    private val expense = ExpenseEntity(1, "w", "Diesel", 200.0, tractorLabel = "T", addedByPartner = "Partner",
        dateTimestamp = date, paidBy = "Business Paid")

    @Test fun datedAndUndatedReceiptsAndPendingChangesAreVisibleInBothLanguages() {
        val tamil = mutableStateOf(false)
        val job = JobEntryEntity(id = 1, workspaceId = "w", customerName = "Legacy field", operatorName = "Partner",
            tractorLabel = "T", workType = "Rotavator", startTimeMillis = date, endTimeMillis = date + 3600000,
            durationMinutes = 60, totalAmount = 625.25, amountReceived = 625.25, pendingAmount = 0.0, addedByPartner = "Partner")
        val payment = PaymentEntity(id = 2, workspaceId = "w", amount = 1000.0, collectedAt = date, isSynced = false)
        compose.setContent { MyApplicationTheme {
            BalanceSheetTab(AppSettingsEntity(workspaceId = "w", language = if (tamil.value) "TA" else "EN"),
                listOf(job), listOf(expense), payments = listOf(payment))
        } }
        val screen = compose.onNodeWithTag("screen_balance_sheet")
        compose.onNodeWithTag("tab_mode_YEARLY").performClick()
        compose.onNodeWithText(formatInr(1625.25)).assertExists()
        screen.performScrollToNode(hasText("Historical receipts — date unverified:", substring = true))
        compose.onNodeWithText("Historical receipts — date unverified: " + formatInr(625.25)).assertExists()
        screen.performScrollToNode(hasTestTag("balance_sync_notice"))
        compose.onNodeWithTag("balance_sync_notice").assertTextContains("This report includes local changes awaiting sync.")
        compose.runOnIdle { tamil.value = true }
        screen.performScrollToNode(hasText("பழைய வசூல்கள்", substring = true))
        compose.onNodeWithText("பழைய வசூல்கள்", substring = true).assertExists()
        screen.performScrollToNode(hasText("2026", substring = false))
        compose.onNodeWithText("2026").assertExists()
    }

    @Test fun expenseBreakdownShowsWhoPaidAndRefundMeaningInBothLanguages() {
        val tamil = mutableStateOf(false)
        compose.setContent { MyApplicationTheme {
            ExpenseAnalyticsCard(listOf(expense, expense.copy(id = 2, amount = -25.0)), tamil.value)
        } }
        compose.onNodeWithTag("expense-analytics-toggle").performClick()
        compose.onNodeWithText("Expense Type").assertExists()
        compose.onNodeWithText("Who Paid?").performClick()
        compose.onNodeWithText("Net expense: " + formatInr(175.0)).assertExists()
        compose.onNodeWithText("Net expense is spending minus refunds.", substring = true).assertExists()
        compose.runOnIdle { tamil.value = true }
        compose.onNodeWithText("யார் செலுத்தியது?").assertExists()
        compose.onNodeWithText("நிகரச் செலவு: " + formatInr(175.0)).assertExists()
    }
}
