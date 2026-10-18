package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.entity.*
import com.example.data.firebase.WorkspaceMember
import com.example.ui.screens.report.RecordCustomerPaymentDialog
import com.example.ui.screens.report.CollectionHistoryTab
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class CollectionFormDeviceTest {
    @get:Rule val compose = createComposeRule()
    private fun settle() { compose.waitForIdle() }
    private val members = listOf(
        WorkspaceMember(uid="owner",workspaceId="a",role="OWNER",status="ACTIVE",displayName="Owner"),
        WorkspaceMember(uid="partner-a",workspaceId="a",role="PARTNER",status="ACTIVE",displayName="Partner A"),
        WorkspaceMember(uid="partner-b",workspaceId="a",role="PARTNER",status="ACTIVE",displayName="Partner B"))
    private fun job(created:Long) = JobEntryEntity(id=1,workspaceId="a",customerId=2,customerName="Fixture",operatorName="Owner",tractorLabel="T",
        workType="Work",startTimeMillis=1,endTimeMillis=2,durationMinutes=1,totalAmount=100.0,amountReceived=0.0,pendingAmount=100.0,addedByPartner="Owner",createdAt=created)
    @Test fun ownerSelectionEmitsPartnerUidInsteadOfPhoneOrRecorderIdentity() {
        var result = ""
        val customer = CustomerEntity(id=2,workspaceId="a",name="Fixture",phone="",balanceDue=100.0)
        val settings = AppSettingsEntity(workspaceId="a")
        val sources = listOf(job(System.currentTimeMillis()-86400000))
        compose.setContent { MyApplicationTheme { RecordCustomerPaymentDialog(
            customer=customer,settings=settings,
            actorUid="owner",actorName="Owner",isOwner=true,workspaceMembers=members,sourceJobs=sources,onDismiss={},
            onConfirm={ _,_,_,_,uid,_,_ -> result=uid }) } }
        settle()
        compose.onNodeWithText("Partner A (Partner)").performClick()
        settle()
        compose.onNodeWithText("Partner B (Partner)").performClick()
        settle()
        compose.onNodeWithTag("btn_confirm_record_payment").assertIsEnabled().performClick()
        assertEquals("partner-b",result)
    }
    @Test fun invalidSourceDateDisablesSaveAndDoesNotInvokeTheCallback() {
        var submitted = false
        val customer = CustomerEntity(id=2,workspaceId="a",name="Fixture",phone="",balanceDue=100.0)
        val settings = AppSettingsEntity(workspaceId="a")
        val sources = listOf(job(0))
        compose.setContent { MyApplicationTheme { RecordCustomerPaymentDialog(
            customer=customer,settings=settings,
            actorUid="owner",isOwner=true,workspaceMembers=members,sourceJobs=sources,onDismiss={},
            onConfirm={ _,_,_,_,_,_,_ -> submitted=true }) } }
        settle()
        compose.onNodeWithTag("payment_date_error").assertExists()
        compose.onNodeWithTag("btn_confirm_record_payment").assertIsNotEnabled().performClick()
        assertFalse(submitted)
    }
}
