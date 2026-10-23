package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.entity.*
import com.example.ui.screens.report.CustomerCreditDueTab
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** UI coverage only; backend authorization is exercised by DeviceEmulatorIntegrationTest. */
@RunWith(AndroidJUnit4::class)
class CustomerEditingDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val original=CustomerEntity(id=7,workspaceId="stage",name="saga jothi",phone="9876543210",location="Village",
        totalBilled=1000.0,totalPaid=200.0,balanceDue=800.0)
    private var saved:Pair<CustomerEntity,CustomerEntity>?=null
    private var acknowledge:(()->Unit)?=null
    private fun show(tamil:Boolean=false) {
        val source=JobEntryEntity(id=8,workspaceId="stage",customerId=7,customerName=original.name,
            operatorName="Partner",tractorLabel="Tractor",workType="Rotavator",startTimeMillis=100,endTimeMillis=200,
            durationMinutes=1,totalAmount=1000.0,amountReceived=200.0,pendingAmount=800.0,addedByPartner="Partner",entryForUid="owner")
        compose.setContent { MyApplicationTheme {
            CustomerCreditDueTab(settings=AppSettingsEntity(workspaceId="stage",language=if(tamil) "TA" else "EN"),
                customers=listOf(original),jobs=listOf(source),isOwner=true,actorUid="owner",
                onEditCustomerProfile={before,after,onSaved->saved=before to after;acknowledge=onSaved})
        } }
        compose.onNodeWithTag("customer_due_card_7").performClick()
        compose.onNodeWithContentDescription(if(tamil) "வாடிக்கையாளர் விவரங்களைத் திருத்து" else "Edit Customer Details").performClick()
    }
    @Test fun globalNameEditKeepsIdPhoneAndWaitsForLocalSaveCallback() {
        show();compose.onNodeWithTag("customer_profile_name").performTextReplacement("Saga Jothi")
        compose.onNodeWithText("Save",useUnmergedTree=true).performClick()
        compose.runOnIdle { assertEquals(original,saved!!.first);assertEquals(original.copy(name="Saga Jothi"),saved!!.second.copy(updatedAt=original.updatedAt)) }
        compose.onNodeWithText("Edit Customer Details").assertExists()
        compose.runOnIdle { acknowledge!!.invoke() }
        compose.onNodeWithText("Edit Customer Details").assertDoesNotExist()
    }
    @Test fun blankNameCannotQueueProfileEdit() {
        show();compose.onNodeWithTag("customer_profile_name").performTextReplacement(" ")
        compose.onNodeWithText("Save",useUnmergedTree=true).performClick()
        compose.onNodeWithText("Customer name is required").assertExists();compose.runOnIdle { assertNull(saved) }
    }
    @Test fun tamilDialogTranslatesAppLabelsAndKeepsCustomerEnteredName() {
        show(true);compose.onNodeWithTag("customer_profile_name").assertTextContains("saga jothi")
        compose.onNodeWithText("வாடிக்கையாளர் விவரங்களைத் திருத்து").assertExists()
        compose.onNodeWithTag("customer_profile_name").performTextReplacement("Saga Jothi")
        compose.onNodeWithText("சேமி",useUnmergedTree=true).performClick()
        compose.runOnIdle { assertEquals("Saga Jothi",saved!!.second.name);assertEquals(original.id,saved!!.second.id) }
    }
}
