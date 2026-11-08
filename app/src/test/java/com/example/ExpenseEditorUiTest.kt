package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.runtime.mutableStateOf
import com.example.data.entity.*
import com.example.data.firebase.WorkspaceMember
import com.example.ui.screens.report.AddOrEditExpenseScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ExpenseEditorUiTest {
    @get:Rule val compose = createComposeRule()
    private fun editor(amount: Double) {
        val original = ExpenseEntity(id = 7, workspaceId = "w", expenseType = "Engine Oil", amount = amount,
            tractorLabel = "T", addedByPartner = "Original", operatorName = "Original", createdByUid = "owner",
            createdByRole = "OWNER", paidBy = "Business Paid", createdAt = 1000)
        var saved: ExpenseEntity? = null
        compose.setContent { MyApplicationTheme { AddOrEditExpenseScreen(true, original, emptyList(), emptyList(),
            AppSettingsEntity(workspaceId = "w"), isOwner = true, currentUid = "owner",
            workspaceMembers = listOf(WorkspaceMember(uid = "owner", workspaceId = "w", role = "OWNER", status = "ACTIVE", displayName = "Original")),
            onBack = {}, onSaveExpense = { saved = it }) } }
        compose.onNodeWithTag("input_expense_amount").assertTextContains(amount.toString())
        compose.onAllNodesWithText("Save").onFirst().performClick()
        compose.runOnIdle {
            assertNotNull(saved); assertEquals(amount, saved!!.amount, 0.0)
            assertEquals(original.createdAt, saved!!.createdAt); assertEquals(original.createdByUid, saved!!.createdByUid)
            assertEquals("Engine Oil", saved!!.expenseType); assertEquals("Business Paid", saved!!.paidBy)
        }
    }
    @Test fun reopeningExpenseDoesNotTruncatePaise() = editor(1234.56)
    @Test fun reopeningRefundKeepsOriginalNegativeAmount() = editor(-25.75)
    @Test fun newExpenseDoesNotClaimToSaveARefundThatExistingRulesReject() {
        var saved = false
        compose.setContent { MyApplicationTheme { AddOrEditExpenseScreen(false, null, emptyList(), emptyList(),
            AppSettingsEntity(workspaceId = "w"), isOwner = true, currentUid = "owner",
            workspaceMembers = listOf(WorkspaceMember(uid = "owner", workspaceId = "w", role = "OWNER", status = "ACTIVE")),
            onBack = {}, onSaveExpense = { saved = true }) } }
        compose.onNodeWithTag("input_expense_amount").performTextInput("-25.75")
        compose.onAllNodesWithText("Save").onFirst().performClick()
        compose.onNodeWithText("Enter a valid amount greater than 0").assertExists()
        compose.runOnIdle { assertFalse(saved) }
    }

    @Test fun failedLocalSaveRetainsTypedAmountAndAllowsAnExplicitRetry() {
        val saving = mutableStateOf(false)
        val failed = mutableStateOf(false)
        var requests = 0
        compose.setContent { MyApplicationTheme { AddOrEditExpenseScreen(false, null, emptyList(), emptyList(),
            AppSettingsEntity(workspaceId = "w"), isOwner = true, currentUid = "owner",
            workspaceMembers = listOf(WorkspaceMember(uid = "owner", workspaceId = "w", role = "OWNER", status = "ACTIVE")),
            isSaving = saving.value, saveFailed = failed.value, onBack = {},
            onSaveExpense = { requests++; saving.value = true }) } }
        compose.onNodeWithTag("input_expense_amount").performTextInput("1234.56")
        compose.onAllNodesWithText("Save").onFirst().performClick()
        compose.onAllNodesWithText("Save").onFirst().performClick()
        compose.runOnIdle { assertEquals(1, requests); saving.value = false; failed.value = true }
        compose.onNodeWithTag("expense_save_failed").performScrollTo().assertExists()
        compose.onNodeWithTag("input_expense_amount").performScrollTo().assertTextContains("1234.56")
        compose.onAllNodesWithText("Save").onFirst().performClick()
        compose.runOnIdle { assertEquals(2, requests) }
    }
}
