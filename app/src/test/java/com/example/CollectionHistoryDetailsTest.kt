package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.JobEntryEntity
import com.example.ui.screens.report.CollectionHistoryTab
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class CollectionHistoryDetailsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun reversedCollectionIsHiddenByDefaultButAvailableInAuditWithoutAffectingActiveTotal() {
        compose.setContent {
            MyApplicationTheme {
                CollectionHistoryTab(
                    settings = AppSettingsEntity(workspaceId = "staging"),
                    jobs = listOf(10L,42L).map { id -> JobEntryEntity(id=id,workspaceId="staging",customerId=2,customerName="Synthetic source",operatorName="Owner",tractorLabel="T",workType="Work",startTimeMillis=1,endTimeMillis=2,durationMinutes=1,totalAmount=if(id==10L)100.0 else 50.0,amountReceived=if(id==10L)100.0 else 0.0,pendingAmount=if(id==10L)0.0 else 50.0,addedByPartner="Owner",createdByUid="owner",entryForUid="owner") },
                    payments = listOf(
                        PaymentEntity(id = 10, workspaceId = "staging", jobEntryId = 10, customerName = "Active Test", amount = 100.0,
                            collectedAt = 200, collectedByName = "Owner Test"),
                        PaymentEntity(id = 11, workspaceId = "staging", customerName = "Reversed Test", amount = 50.0,
                            jobEntryId = 42, collectedAt = 100, collectedByName = "Owner Test", status = "REVERSED",
                            reversedAt = 300, reversedByUid = "owner")
                    ),
                    isOwner = true,
                    currentUid = "owner",
                    onDeleteCollection = {}
                )
            }
        }
        // Active total reflects only active payments
        compose.onAllNodesWithText("₹100").onFirst().assertExists()
        compose.onNodeWithText("₹150").assertDoesNotExist()

        // In collapsed state, payment timeline cards are not displayed
        compose.onNodeWithTag("collection_history_card_10").assertDoesNotExist()
        compose.onNodeWithTag("collection_history_card_11").assertDoesNotExist()

        // Switch to reversed filter
        compose.onNodeWithTag("collection_filter_reversed").performClick()
        compose.onNodeWithTag("collection_history_card_10").assertDoesNotExist()

        // Expand reversed card
        compose.onNodeWithTag("expand_job_card_42").performScrollTo().performClick()
        compose.onNodeWithTag("collection_history_card_11").performScrollTo().assertExists()
        compose.onNodeWithTag("reversed_collection_11", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag("reverse_collection_11", useUnmergedTree = true).assertDoesNotExist()

        // Switch back to active filter
        compose.onNodeWithTag("collection_filter_active").performClick()
        // Expand active card
        compose.onNodeWithTag("expand_job_card_10").performScrollTo().performClick()
        compose.onNodeWithTag("collection_history_card_10").performScrollTo().assertExists()
        compose.onNodeWithTag("reverse_collection_10", useUnmergedTree = true).assertExists()

        // Switch to reversed filter and open details dialog (card 42 remains expanded via rememberSaveable)
        compose.onNodeWithTag("collection_filter_reversed").performClick()
        compose.onNodeWithTag("collection_history_card_11").performScrollTo().performClick()
        compose.onNodeWithText("Collection Details").assertExists()
        compose.onNodeWithText("Status: REVERSED").assertExists()
        compose.onNodeWithText("Entry: 42").assertDoesNotExist()
        compose.onNodeWithText("Reversed by: owner").assertExists()
    }
}
