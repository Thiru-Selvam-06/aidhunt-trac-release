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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class CollectionFormRegressionTest {
    @get:Rule val compose = createComposeRule()
    private fun settle() { compose.waitForIdle() }
    private val members = listOf(
        WorkspaceMember(uid="owner",workspaceId="a",role="OWNER",status="ACTIVE",displayName="Owner"),
        WorkspaceMember(uid="partner-a",workspaceId="a",role="PARTNER",status="ACTIVE",displayName="Partner A"),
        WorkspaceMember(uid="partner-b",workspaceId="a",role="PARTNER",status="ACTIVE",displayName="Partner B"))
    private fun job(created:Long) = JobEntryEntity(id=1,workspaceId="a",customerId=2,customerName="Fixture",operatorName="Owner",tractorLabel="T",
        workType="Work",startTimeMillis=1,endTimeMillis=2,durationMinutes=1,totalAmount=100.0,amountReceived=0.0,pendingAmount=100.0,addedByPartner="Owner",createdAt=created)
    @Test fun historyShowsSelectedPartnerAndDistinctAuthenticatedRecorder() {
        compose.setContent { MyApplicationTheme { CollectionHistoryTab(
            settings=AppSettingsEntity(workspaceId="a"),workspaceMembers=members,isOwner=true,currentUid="owner",payments=listOf(
                PaymentEntity(id=10,workspaceId="a",customerName="Fixture",amount=100.0,collectorUid="partner-b",collectorName="Stale snapshot",
                    collectorRole="PARTNER",collectedByUid="owner",collectedByName="Owner",collectedByRole="OWNER"))) } }
        settle()
        compose.onNodeWithTag("expand_job_card_10").performScrollTo().performClick()
        compose.onNodeWithTag("collection_history_card_10").performScrollTo().performClick()
        settle()
        compose.onNodeWithText("Collected by: Partner B").assertExists()
        compose.onNodeWithText("Recorded by: Owner").assertExists()
    }
    @Test fun historyRendersOneJobCardWithThreeIndividuallyActionablePayments() {
        compose.setContent { MyApplicationTheme { CollectionHistoryTab(
            settings=AppSettingsEntity(workspaceId="a"),workspaceMembers=members,isOwner=true,currentUid="owner",
            jobs=listOf(job(1).copy(totalAmount=50000.0,amountReceived=15000.0,pendingAmount=35000.0)),
            payments=listOf(4000.0,5000.0,6000.0).mapIndexed { index, value ->
                PaymentEntity(id=(index+1).toLong(),workspaceId="a",jobEntryId=1,customerName="Fixture",amount=value,
                    collectedAt=(index+2).toLong(),collectorUid="partner-b",collectorName="Partner B",collectorRole="PARTNER",
                    collectedByUid="owner",collectedByName="Owner")
            }) } }
        compose.onAllNodesWithTag("collection_job_card_a:job:1").assertCountEquals(1)
        compose.onNodeWithTag("expand_job_card_1").performScrollTo().performClick()
        for (id in 1..3) compose.onNodeWithTag("collection_history_card_$id").performScrollTo().assertExists()
        compose.onNodeWithTag("collection_history_card_3").performClick()
        compose.onNodeWithText("Amount: ₹6,000").assertExists()
        compose.onNodeWithText("Collected by: Partner B").assertExists()
        compose.onNodeWithText("Recorded by: Owner").assertExists()
    }

    @Test fun legacyJobSummaryDoesNotOfferAReversalForANonexistentPaymentRecord() {
        compose.setContent { MyApplicationTheme { CollectionHistoryTab(
            settings=AppSettingsEntity(workspaceId="a"),isOwner=true,currentUid="owner",
            jobs=listOf(job(1).copy(amountReceived=40.0,pendingAmount=60.0)),payments=emptyList(),onDeleteCollection={}) } }
        compose.onNodeWithTag("expand_job_card_1").performScrollTo().performClick()
        compose.onNodeWithTag("collection_history_card_1").performScrollTo().assertExists()
        compose.onNodeWithTag("reverse_collection_1",useUnmergedTree=true).assertDoesNotExist()
    }

}
