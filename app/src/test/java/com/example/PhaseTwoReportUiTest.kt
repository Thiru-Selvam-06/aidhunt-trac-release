package com.example

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.entity.*
import com.example.data.firebase.WorkspaceMember
import com.example.ui.screens.entry.NewEntryScreen
import com.example.ui.screens.report.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.reportCalendar
import com.example.ui.util.reportDay
import com.example.ui.viewmodel.NewEntryDraft
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp", application = LedgerUiTestApplication::class)
class PhaseTwoReportUiTest {
    @get:Rule val compose = createComposeRule()
    private val settings = AppSettingsEntity(workspaceId = "a", ownerName = "Original Owner")
    private val original = JobEntryEntity(id = 1, workspaceId = "a", customerId = 10, customerName = "Farm Field One",
        operatorName = "Original Owner", tractorLabel = "Unavailable Tractor", tractorId = 44, workType = "Rotavator",
        startTimeMillis = 0, endTimeMillis = 0, durationMinutes = 0, totalAmount = 1100.0, amountReceived = 100.0,
        pendingAmount = 1000.0, addedByPartner = "Original Owner", createdAt = reportDay(2026, 9, 8),
        createdByUid = "owner", createdByRole = "Owner", entryForUid = "owner", financialAttributionUid = "owner",
        isHistorical = true)
    private val customer = CustomerEntity(id = 10, workspaceId = "a", name = original.customerName, phone = "")
    private val owner = WorkspaceMember(uid = "owner", workspaceId = "a", role = "OWNER", status = "ACTIVE", displayName = "Original Owner")
    private val partner = WorkspaceMember(uid = "partner", workspaceId = "a", role = "PARTNER", status = "ACTIVE", displayName = "Second Member")
    private val currentYear = reportCalendar(System.currentTimeMillis()).get(java.util.Calendar.YEAR)
    private val selectedYear = currentYear - 1
    private val oct = reportDay(selectedYear, 9, 15)
    private val nov = reportDay(selectedYear, 10, 13)

    @Test fun partnerCanOpenOwnerHistoricalJobFromCustomerDues() {
        var selected: JobEntryEntity? = null
        compose.setContent { MyApplicationTheme {
            CustomerDetailSheet(customer, listOf(original), settings, onDismiss = {}, onAddPayment = {},
                onCall = {}, onWhatsApp = {}, onSharePdf = {}, onEditJob = { selected = it },
                actorUid = "partner", workspaceMembers = listOf(owner, partner))
        } }
        compose.onNodeWithTag("customer_job_history").performScrollToNode(hasTestTag("btn_edit_job_1"))
        compose.onNodeWithTag("btn_edit_job_1").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(original, selected) }
    }

    @Test fun partnerCanReviewAndSaveLegacyJobWithoutReplacingMissingTractorOrWorkTime() {
        val draft = mutableStateOf(NewEntryDraft.fromJobEntry(original).copy(notes = "Corrected spelling"))
        var saved: JobEntryEntity? = null
        compose.setContent { MyApplicationTheme {
            NewEntryScreen(settings, emptyList(), listOf(customer), draft = draft.value,
                workspaceMembers = listOf(owner, partner), currentUid = "partner",
                onUpdateDraft = { draft.value = it }, onClearDraft = {}, onSaveJob = { job, _, _ -> saved = job })
        } }
        compose.onNodeWithTag("new_entry_form").performScrollToNode(hasTestTag("btn_review_and_save"))
        compose.onNodeWithTag("btn_review_and_save").performClick()
        compose.onNodeWithTag("btn_confirm_final_save").performScrollTo().performClick()
        compose.runOnIdle {
            val job = requireNotNull(saved)
            assertEquals(original.id, job.id); assertTrue(job.isHistorical)
            assertEquals(original.createdByUid, job.createdByUid); assertEquals(original.addedByPartner, job.addedByPartner)
            assertEquals(original.createdAt, job.createdAt); assertEquals("partner", job.editedByUid)
            assertEquals(original.tractorId, job.tractorId); assertEquals(original.startTimeMillis, job.startTimeMillis)
            assertEquals(original.entryForUid, job.entryForUid); assertEquals(original.financialAttributionUid, job.financialAttributionUid)
            assertEquals(original.amountReceived, job.amountReceived, 0.0)
            assertEquals(original.pendingAmount, job.pendingAmount, 0.0)
            assertEquals("Corrected spelling", job.notes)
        }
    }

    @Test fun collectionMonthAndPaymentMethodFilterCombineWithSearchAndSort() {
        val jobs = listOf(original, original.copy(id = 2, customerName = "Farm Field Two", customerId = 11))
        fun pay(id: Long, job: JobEntryEntity, amount: Double, date: Long, method: String) = PaymentEntity(
            id = id, workspaceId = "a", jobEntryId = job.id, customerId = job.customerId, customerName = job.customerName,
            amount = amount, collectedAt = date, paymentMethod = method, collectedByUid = "owner")
        val payments = listOf(pay(11, jobs[0], 100.0, nov, "Cash"), pay(12, jobs[0], 50.0, oct, "Cash"),
            pay(13, jobs[1], 100.0, oct + 86400000L, "UPI"))
        compose.setContent { MyApplicationTheme {
            CollectionHistoryTab(settings, payments, jobs, workspaceMembers = listOf(owner), isOwner = true, currentUid = "owner")
        } }
        val list = compose.onNodeWithTag("collection_history_list")
        selectOctober(list)
        list.performScrollToNode(hasTestTag("search_payments_input"))
        compose.onNodeWithTag("search_payments_input").performTextInput("Farm Field")
        list.performScrollToNode(hasTestTag("collection_sort"))
        compose.onNodeWithTag("collection_sort").performClick()
        compose.onNodeWithTag("collection_sort_HIGHEST").performClick()
        list.performScrollToNode(hasTestTag("filter_chip_Cash"))
        compose.onNodeWithTag("filter_chip_Cash").performClick()
        list.performScrollToNode(hasText("1 Jobs · 1 Payments"))
        compose.onNodeWithText("₹50").assertExists()
        list.performScrollToNode(hasTestTag("collection_job_card_a:job:1"))
        compose.onNodeWithText("Latest matching collection: 15 Oct $selectedYear", substring = true).assertExists()
    }

    @Test fun expenseMonthUsesExpenseDateForExactReportSubtotal() {
        val expense = ExpenseEntity(id = 1, workspaceId = "a", expenseType = "Diesel", amount = 350.0,
            tractorLabel = "T", addedByPartner = "Owner", dateTimestamp = oct, createdAt = nov)
        val rows = listOf(expense, expense.copy(id = 2, amount = 900.0, dateTimestamp = nov),
            expense.copy(id = 3, amount = 200.0, dateTimestamp = 0))
        compose.setContent { MyApplicationTheme {
            ExpensesTab(settings, rows, tractors = emptyList(), partners = emptyList(), isOwner = true,
                currentUid = "owner", workspaceMembers = listOf(owner),
                onAddExpense = {}, onUpdateExpense = {}, onDeleteExpense = {})
        } }
        val list = compose.onNodeWithTag("expense_report_list")
        selectOctober(list)
        list.performScrollToNode(hasText("Total Expenses"))
        compose.onAllNodesWithText("₹350").onFirst().assertExists()
        list.performScrollToNode(hasTestTag("expense_item_1"))
        compose.onNodeWithTag("expense_item_2").assertDoesNotExist()
        compose.onNodeWithTag("expense_item_3").assertDoesNotExist()
    }

    @Test fun withdrawalMonthKeepsMemberFilterAndShowsMatchingLedgerAndTotal() {
        val october = WithdrawalEntity(id = 1, workspaceId = "a", partnerName = "Second Member", amount = 120.0,
            timestamp = oct, createdAt = nov, targetPartnerUid = "partner")
        val rows = listOf(october, october.copy(id = 2, amount = 900.0, timestamp = nov),
            october.copy(id = 3, amount = 300.0, targetPartnerUid = "owner"))
        compose.setContent { MyApplicationTheme {
            WithdrawalTab(settings, rows, emptyList(), 1000.0, 1320.0, {}, {}, workspaceMembers = listOf(owner, partner),
                isOwner = true, currentUid = "owner", currentRole = "Owner")
        } }
        val list = compose.onNodeWithTag("withdrawal_report_list")
        list.performScrollToNode(hasText("Filter Records"))
        compose.onNodeWithText("Filter Records").performClick()
        selectOctober(list)
        val member = hasText("Second Member") and hasClickAction()
        list.performScrollToNode(member)
        compose.onNode(member).performClick()
        list.performScrollToNode(hasText("Apply Filters"))
        compose.onNodeWithText("Apply Filters").performClick()
        list.performScrollToNode(hasTestTag("view_withdrawal_records"))
        compose.onNodeWithTag("view_withdrawal_records").performClick()
        compose.onNodeWithTag("withdrawal_filtered_summary").assertTextContains("1 record · ₹120")
        compose.onNodeWithTag("withdrawal_card_1").assertExists()
        compose.onNodeWithTag("withdrawal_card_2").assertDoesNotExist()
        compose.onNodeWithTag("withdrawal_card_3").assertDoesNotExist()
    }

    @Test fun tamilDateOptionsAndCollectionSortHaveLocalizedLabels() {
        val payment = PaymentEntity(id = 11, workspaceId = "a", jobEntryId = original.id, customerId = original.customerId,
            customerName = original.customerName, amount = 100.0, collectedAt = oct)
        compose.setContent { MyApplicationTheme {
            CollectionHistoryTab(settings.copy(language = "TA"), listOf(payment), listOf(original),
                workspaceMembers = listOf(owner), isOwner = true, currentUid = "owner")
        } }
        val list = compose.onNodeWithTag("collection_history_list")
        list.performScrollToNode(hasTestTag("report_date_filter"))
        compose.onNodeWithTag("report_date_filter").assertTextContains("எல்லா தேதிகளும்").performClick()
        compose.onNodeWithTag("report_period_MONTH_YEAR").assertTextContains("மாதம் மற்றும் ஆண்டைத் தேர்ந்தெடுக்கவும்")
        compose.onNodeWithTag("report_period_CUSTOM_RANGE").assertTextContains("தேதி வரம்பு").performClick()
        compose.onNodeWithTag("report_from").assertTextContains("தொடக்கம்", substring = true)
        compose.onNodeWithTag("report_to").assertTextContains("முடிவு", substring = true)
        compose.onNodeWithText("ரத்து").performClick()
        compose.onNodeWithTag("collection_sort").assertTextContains("தேதி — புதியவை முதலில்", substring = true)
    }

    private fun selectOctober(list: SemanticsNodeInteraction) {
        list.performScrollToNode(hasTestTag("report_date_filter"))
        compose.onNodeWithTag("report_date_filter").performClick()
        compose.onNodeWithTag("report_period_MONTH_YEAR").performClick()
        compose.onNodeWithTag("report_year").assertTextContains(currentYear.toString())
        compose.onNodeWithTag("report_previous_year").performClick()
        compose.onNodeWithTag("report_year").assertTextContains(selectedYear.toString())
        compose.onNodeWithTag("report_month_9").performClick()
        compose.onNodeWithTag("apply_report_dates").performClick()
        list.performScrollToNode(hasText("October $selectedYear"))
    }
}
