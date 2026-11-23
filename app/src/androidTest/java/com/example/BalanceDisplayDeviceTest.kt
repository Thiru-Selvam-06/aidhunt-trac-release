package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.firebase.BalanceDisplayEdit
import com.example.data.firebase.BalanceDisplayState
import com.example.ui.components.BalanceDisplayEditor
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class BalanceDisplayDeviceTest {
    @get:Rule val compose = createComposeRule()
    @org.junit.Before fun requireSyntheticApplication() {
        check(BuildConfig.DEVICE_TEST)
        check(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".devicetest"))
        check(com.google.firebase.FirebaseApp.getInstance().options.projectId == "demo-aidhunt-v16")
    }
    @Test fun unusedProfileSelectionExcludesReversedReceiptsAndRejectedOfflineActionsWithoutCrossWorkspaceMixing() {
        val customers=(1L..3L).map { com.example.data.entity.CustomerEntity(id=it,workspaceId="ws_test",name="Synthetic $it",phone="") }
        val reversed=com.example.data.entity.PaymentEntity(id=1,workspaceId="ws_test",customerId=1,customerName="Synthetic 1",amount=10.0,status="REVERSED")
        val retained=com.example.data.entity.PendingOperationEntity("original-job","ws_test","partner","JOB",9,"UPSERT",1,123,
            """{"customerId":2}""",state="REJECTED")
        assertEquals(listOf(3L),com.example.ui.components.UnusedCustomerPolicy.candidates("ws_test",customers,emptyList(),
            listOf(reversed,reversed.copy(workspaceId="foreign",customerId=3)),listOf(retained)).map { it.id })
        assertTrue(com.example.ui.components.UnusedCustomerPolicy.candidates("ws_test",customers,emptyList(),emptyList(),
            listOf(retained.copy(payloadJson="malformed"))).isEmpty())
    }
    @Test fun requiredReasonAndExactAmountReachOneSaveWithoutChangingAccountingBalance() {
        var saved: Pair<Long?, String>? = null
        compose.setContent { MyApplicationTheme { BalanceDisplayEditor(BalanceDisplayState("ws_test", confirmed=true), 1286.67,
            false, true, true, {}, { amount, calculated, revision, reason, id, done ->
                assertEquals(128667L, calculated); assertEquals(0L, revision); assertTrue(id.isNotBlank())
                saved = amount to reason; done(null)
            }) } }
        compose.onNodeWithTag("balance_display_save").assertIsNotEnabled()
        compose.onNodeWithTag("balance_display_amount").performTextReplacement("5000.50")
        compose.onNodeWithTag("balance_display_reason").performTextInput("Counted display")
        compose.onNodeWithTag("balance_display_save").assertIsEnabled().performClick()
        assertEquals(500050L to "Counted display", saved)
    }
    @Test fun offlineCachedValueCannotBeSavedAndTamilDoesNotTranslateUserReasonOrEditor() {
        val state=BalanceDisplayState("ws_test", overridePaise=500050, confirmed=false, history=listOf(
            BalanceDisplayEdit("one",128667,500050,"Original user reason","partner","Perumal",1795241623375,false)))
        compose.setContent { MyApplicationTheme { BalanceDisplayEditor(state,1286.67,true,false,true,{}, {_,_,_,_,_,_-> fail("Offline save")}) } }
        compose.onNodeWithTag("balance_display_reason").performTextInput("எண்ணிய தொகை")
        compose.onNodeWithTag("balance_display_save").assertIsNotEnabled()
        compose.onNodeWithText("Perumal",substring=true).assertExists()
        compose.onNodeWithText("Original user reason",substring=true).assertExists()
        compose.onNodeWithText("காரணம் (கட்டாயம்)").assertExists()
    }
    @Test fun unusedCustomerDeletionRequiresExplicitConfirmationAndKeepsUserNameUntranslated() {
        val customer=com.example.data.entity.CustomerEntity(id=7,workspaceId="ws_test",name="Original customer name",phone="")
        var deleted=false
        compose.setContent { MyApplicationTheme {
            com.example.ui.components.UnusedCustomersDialog(listOf(customer),true,{}, { assertEquals(customer,it);deleted=true })
        } }
        compose.onNodeWithTag("delete_unused_customer_7").performClick()
        assertFalse(deleted)
        compose.onAllNodesWithText("Original customer name",substring=true).assertCountEquals(2)
        compose.onNodeWithTag("confirm_unused_customer_delete").performClick()
        assertTrue(deleted)
    }
    @Test fun aChangedUnderlyingBalanceRequiresRefreshBeforeSavingTheRecordedBeforeAmount() {
        val calculated = androidx.compose.runtime.mutableStateOf(1286.67)
        compose.setContent { MyApplicationTheme { BalanceDisplayEditor(BalanceDisplayState("ws_test",confirmed=true),
            calculated.value,false,true,true,{}, {_,_,_,_,_,_->fail("Stale display edit")}) } }
        compose.onNodeWithTag("balance_display_reason").performTextInput("Counted display")
        compose.onNodeWithTag("balance_display_save").assertIsEnabled()
        compose.runOnIdle { calculated.value=1500.0 }
        compose.onNodeWithTag("balance_display_save").assertIsNotEnabled()
        compose.onNodeWithText("The balance changed. Close and reopen to review the latest amount.").assertExists()
    }
    @Test fun uneditedDisplayRequiresAReasonAndShowsTheAccountingDisclaimer() {
        compose.setContent { MyApplicationTheme { BalanceDisplayEditor(BalanceDisplayState("ws_test",confirmed=true),1286.67,
            false,true,true,{}, {_,_,_,_,_,_->fail("No reason provided")}) } }
        compose.onNodeWithTag("balance_display_save").assertIsNotEnabled()
        compose.onNodeWithText("Display only. Accounting balance, withdrawal limits and financial reports do not change.").assertExists()
    }
    @Test fun tamilReadOnlyHistoryKeepsTheOriginalEditorAndReason() {
        val state=BalanceDisplayState("ws_test",overridePaise=500050,confirmed=false,history=listOf(
            BalanceDisplayEdit("one",128667,500050,"Original user reason","partner","Perumal",1795241623375,false)))
        compose.setContent { MyApplicationTheme { BalanceDisplayEditor(state,1286.67,true,false,false,{}, {_,_,_,_,_,_->fail("Read only")}) } }
        compose.onNodeWithText("Perumal",substring=true).assertExists()
        compose.onNodeWithText("Original user reason",substring=true).assertExists()
        compose.onNodeWithTag("balance_display_save").assertDoesNotExist()
    }
}
