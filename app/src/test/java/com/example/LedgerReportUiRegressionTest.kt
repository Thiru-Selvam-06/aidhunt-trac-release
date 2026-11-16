package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.entity.*
import com.example.ui.screens.report.CollectionHistoryTab
import com.example.ui.screens.report.CustomerCreditDueTab
import com.example.ui.screens.entry.NewEntryScreen
import com.example.data.firebase.WorkspaceMember
import com.example.ui.viewmodel.NewEntryDraft
import com.example.ui.viewmodel.DraftExpenseItem
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf

/** Registers only the Compose host when these tests run without a packaged test manifest. */
class LedgerUiTestApplication : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        shadowOf(packageManager).addActivityIfNotPresent(
            android.content.ComponentName(this, androidx.activity.ComponentActivity::class.java))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp", application = LedgerUiTestApplication::class)
class LedgerReportUiRegressionTest {
    @get:Rule val compose = createComposeRule()
    private val job = JobEntryEntity(id = 1, workspaceId = "a", customerId = 10, customerName = "Older Customer",
        operatorName = "Owner", tractorLabel = "T", workType = "Work", startTimeMillis = 1000,
        endTimeMillis = 2000, durationMinutes = 1, totalAmount = 100.0, amountReceived = 40.0,
        pendingAmount = 60.0, addedByPartner = "Owner")

    @Test fun allTimeCollectionsDisplayBothOlderEntryReceiptAndNewCollection() {
        compose.setContent { MyApplicationTheme {
            CollectionHistoryTab(settings = AppSettingsEntity(workspaceId = "a"), isOwner = true,
                jobs = listOf(job, job.copy(id = 2, customerId = 11, customerName = "New Customer", amountReceived = 25.0)), payments = listOf(PaymentEntity(id = 20, workspaceId = "a",
                    jobEntryId = 2, customerId = 11, customerName = "New Customer", amount = 25.0)), currentUid = "owner")
        } }
        val history = compose.onNodeWithTag("collection_history_list")
        history.performScrollToNode(hasTestTag("expand_job_card_1"))
        compose.onNodeWithTag("collection_job_card_a:job:1").assertExists()
        compose.onNodeWithTag("expand_job_card_1").performClick()
        history.performScrollToNode(hasTestTag("collection_history_card_-1"))
        compose.onNodeWithTag("collection_history_card_-1").assertExists()
        compose.onNodeWithTag("reverse_collection_-1", useUnmergedTree = true).assertDoesNotExist()
        history.performScrollToNode(hasTestTag("collection_job_card_a:job:2"))
        compose.onNodeWithTag("collection_job_card_a:job:2").assertExists()
    }

    @Test fun deletingLastEntryRemovesCustomerFromDuesAndClosesTheStaleStatement() {
        val jobs = mutableStateOf(listOf(job))
        compose.setContent { MyApplicationTheme {
            CustomerCreditDueTab(settings = AppSettingsEntity(workspaceId = "a"), isOwner = true,
                customers = listOf(CustomerEntity(id = 10, workspaceId = "a", name = "Older Customer", phone = "",
                    totalBilled = 100.0, totalPaid = 40.0, balanceDue = 60.0)), jobs = jobs.value)
        } }
        compose.onNodeWithText("Older Customer").performScrollTo().performClick()
        compose.runOnIdle { jobs.value = emptyList() }
        compose.onAllNodesWithText("Older Customer").assertCountEquals(0)
        compose.onNodeWithTag("btn_delete_customer").assertDoesNotExist()
    }

    @Test fun deletedJobDoesNotLeaveAnActiveCollectionHistoryCard() {
        compose.setContent { MyApplicationTheme {
            CollectionHistoryTab(settings = AppSettingsEntity(workspaceId = "a"), isOwner = true,
                jobs = emptyList(), payments = listOf(PaymentEntity(id = 20, workspaceId = "a",
                    jobEntryId = 2, customerId = 11, customerName = "Deleted Customer", amount = 25.0)), currentUid = "owner")
        } }
        compose.onAllNodesWithText("Deleted Customer").assertCountEquals(0)
        compose.onNodeWithTag("collection_job_card_a:job:2").assertDoesNotExist()
    }

    @Test fun equalPresetShowsThirdsInsteadOfGivingOnePartnerAnExtraPercent() {
        compose.setContent { MyApplicationTheme {
            com.example.ui.screens.report.ConfigurePercentagesDialog(
                eligibleParticipants = listOf("owner", "p1", "p2").map {
                    com.example.ui.screens.report.ProfitShareParticipant(it, it, "Partner", it == "owner") },
                currentAllocations = mapOf("owner" to 34, "p1" to 33, "p2" to 33), currentDetails = emptyMap(),
                isTamil = false, isReadOnly = true, onDismiss = {})
        } }
        compose.onAllNodesWithText("33.33%").assertCountEquals(3)
        compose.onAllNodesWithText("34%").assertCountEquals(0)
    }

    @Test fun partnerCanSelectOwnerAndAnotherPartnerWithoutChangingRecorder() {
        val draft = mutableStateOf(NewEntryDraft(customerNameInput = "Field Customer", matchedCustomerId = 10,
            selectedTractor = "T", isDirectDurationMode = true, manualHoursInput = "1", hourlyRateInput = "1100",
            expensesList = listOf(DraftExpenseItem(id = 4L, expenseType = "Diesel", amount = 200.0))))
        var saved: JobEntryEntity? = null
        var savedExpenses: List<ExpenseEntity> = emptyList()
        val members = listOf(
            WorkspaceMember(uid = "owner", workspaceId = "a", role = "OWNER", status = "ACTIVE", displayName = "Owner Account"),
            WorkspaceMember(uid = "partner", workspaceId = "a", role = "PARTNER", status = "ACTIVE", displayName = "Recorder"),
            WorkspaceMember(uid = "other", workspaceId = "a", role = "PARTNER", status = "ACTIVE", displayName = "Other Partner"),
            WorkspaceMember(uid = "removed", workspaceId = "a", role = "PARTNER", status = "REMOVED", displayName = "Former Member"))
        compose.setContent { MyApplicationTheme {
            NewEntryScreen(settings = AppSettingsEntity(workspaceId = "a", ownerName = "Owner"),
                tractors = listOf(TractorEntity(id = 2, workspaceId = "a", label = "T", chassisNo = "test")),
                customers = listOf(CustomerEntity(id = 10, workspaceId = "a", name = "Field Customer", phone = "")),
                draft = draft.value, currentUid = "partner", workspaceMembers = members,
                onUpdateDraft = { draft.value = it }, onClearDraft = {}, onSaveJob = { job, _, expenses ->
                    saved = job
                    savedExpenses = expenses
                })
        } }
        compose.onNodeWithText("Entry For").assertExists()
        compose.onNodeWithTag("entry_for_dropdown").assertTextContains("Recorder").performClick()
        compose.onNodeWithText("Former Member").assertDoesNotExist()
        compose.onNodeWithText("Owner Account", useUnmergedTree = true).performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals("owner", draft.value.selectedPersonUid) }
        compose.onNodeWithTag("entry_for_dropdown").performClick()
        compose.onNodeWithText("Other Partner", useUnmergedTree = true).performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals("other", draft.value.selectedPersonUid) }
        compose.onNodeWithTag("new_entry_form").performScrollToNode(hasTestTag("btn_review_and_save"))
        compose.onNodeWithTag("btn_review_and_save").performClick()
        compose.onNodeWithTag("btn_confirm_final_save").performScrollTo().performClick()
        compose.runOnIdle {
            val result = requireNotNull(saved)
            org.junit.Assert.assertEquals("partner", result.createdByUid)
            org.junit.Assert.assertEquals("Recorder", result.addedByPartner)
            org.junit.Assert.assertEquals("other", result.entryForUid)
            org.junit.Assert.assertEquals("Other Partner", result.operatorName)
            org.junit.Assert.assertEquals("other", result.financialAttributionUid)
            org.junit.Assert.assertEquals("other", savedExpenses.single().paidByUid)
        }
    }

    @Test @Config(sdk = [36], qualifiers = "w320dp-h640dp")
    fun longExpenseRowsKeepDeleteButtonVisibleAndDeleteOnlyChosenItem() {
        val draft = mutableStateOf(NewEntryDraft(expensesList = (1L..3L).map {
            DraftExpenseItem(id = it, expenseType = "Very long custom expense category with many words",
                amount = 1234567.0, paidByPartner = "A partner with a very long display name")
        }))
        compose.setContent { MyApplicationTheme {
            NewEntryScreen(settings = AppSettingsEntity(workspaceId = "a"), tractors = emptyList(), customers = emptyList(),
                draft = draft.value, currentUid = "partner", workspaceMembers = listOf(
                    WorkspaceMember(uid = "partner", workspaceId = "a", role = "PARTNER", status = "ACTIVE", displayName = "Partner")),
                onUpdateDraft = { draft.value = it }, onClearDraft = {}, onSaveJob = { _, _, _ -> })
        } }
        compose.onNodeWithTag("new_entry_form").performScrollToNode(hasTestTag("delete_draft_expense_2"))
        compose.onNodeWithTag("delete_draft_expense_2").assertIsDisplayed().performClick()
        compose.runOnIdle { org.junit.Assert.assertEquals(listOf(1L, 3L), draft.value.expensesList.map { it.id }) }
    }
}
