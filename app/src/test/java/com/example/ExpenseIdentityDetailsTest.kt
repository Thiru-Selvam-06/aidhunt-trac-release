package com.example

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.ExpenseEntity
import com.example.ui.screens.report.ExpenseDetailsScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ExpenseIdentityDetailsTest {
    @get:Rule val compose = createComposeRule()
    private fun expense(name: String) = ExpenseEntity(id = 9007199254740995L, workspaceId = "fixture-a",
        expenseType = "Fuel", amount = 100.0, tractorLabel = "T", addedByPartner = name,
        createdAt = 1L)

    @Test fun legacyOwnerLabelDoesNotClaimVerifiedIdentityAndExactRecordIdIsShown() {
        compose.setContent { MyApplicationTheme {
            ExpenseDetailsScreen(expense("Owner"), settings = AppSettingsEntity(workspaceId = "fixture-a"),
                onBack = {}, onEdit = {}, onDelete = {})
        } }
        compose.onNodeWithText("Owner (creator identity missing)").assertExists()
        compose.onNodeWithText("Expense Reference").assertExists()
        compose.onNodeWithTag("expense_record_identity").assertTextEquals("900719...40995")
        compose.onNodeWithTag("btn_copy_expense_id").performClick()
        compose.runOnIdle {
            val clipboard = ApplicationProvider.getApplicationContext<Context>()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals("9007199254740995", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }
    }
    @Test fun currentAccountNameIsNeverSubstitutedForMissingRecordCreator() {
        compose.setContent { MyApplicationTheme {
            ExpenseDetailsScreen(expense(""), settings = AppSettingsEntity(workspaceId = "fixture-a", activePartnerName = "Current Account"),
                onBack = {}, onEdit = {}, onDelete = {})
        } }
        compose.onNodeWithText("Name not recorded (creator identity missing)").assertExists()
        compose.onNodeWithText("Current Account").assertDoesNotExist()
    }
    @Test fun collidingJobFromAnotherWorkspaceIsNotDisplayedAsExpenseSource() {
        val foreign = com.example.data.entity.JobEntryEntity(id = 42L, workspaceId = "foreign", customerId = 1L,
            customerName = "Foreign Customer", operatorName = "Operator", tractorLabel = "T", workType = "Work",
            startTimeMillis = 1L, endTimeMillis = 2L, durationMinutes = 1, totalAmount = 100.0,
            amountReceived = 0.0, pendingAmount = 100.0, addedByPartner = "Member")
        compose.setContent { MyApplicationTheme {
            ExpenseDetailsScreen(expense("Owner").copy(relatedJobId = 42L), jobs = listOf(foreign),
                settings = AppSettingsEntity(workspaceId = "fixture-a"), onBack = {}, onEdit = {}, onDelete = {})
        } }
        compose.onNodeWithText("Foreign Customer").assertDoesNotExist()
    }

}
