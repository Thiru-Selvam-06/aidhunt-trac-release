package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import com.example.ui.components.AppTopHeader
import com.example.ui.components.AppBottomNav
import com.example.ui.viewmodel.BottomTab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.entity.*
import com.example.data.firebase.*
import com.example.ui.components.CashBalanceEditor
import com.example.ui.screens.report.*
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Android layout evidence only. Never connects to the real workspace. */
@RunWith(AndroidJUnit4::class)
class ReportLayoutDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val settings = AppSettingsEntity(workspaceId = "ui_synthetic", ownerName = "Owner Test", businessName = "UI Test")
    private val members = listOf(
        WorkspaceMember(uid = "owner", workspaceId = "ui_synthetic", role = "Owner", status = "ACTIVE", displayName = "Owner Test"),
        WorkspaceMember(uid = "alpha", workspaceId = "ui_synthetic", role = "Partner", status = "ACTIVE", displayName = "Partner Alpha"),
        WorkspaceMember(uid = "beta", workspaceId = "ui_synthetic", role = "Partner", status = "ACTIVE", displayName = "Partner Beta"))
    private val job = JobEntryEntity(id = 1, workspaceId = "ui_synthetic", customerId = 10, customerName = "Synthetic Customer",
        operatorName = "Partner Alpha", tractorLabel = "Test Tractor", workType = "Rotavator", startTimeMillis = 1793514600000,
        endTimeMillis = 1793518200000, durationMinutes = 60, totalAmount = 1060816.67, amountReceived = 18430.0, pendingAmount = 1042386.67,
        addedByPartner = "Partner Alpha", createdByUid = "alpha", createdByRole = "Partner", entryForUid = "alpha", financialAttributionUid = "alpha")

    @Before fun syntheticOnly() {
        check(BuildConfig.DEVICE_TEST)
        check(InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".devicetest"))
        check(com.google.firebase.FirebaseApp.getInstance().options.projectId == "demo-aidhunt-v16")
    }

    private fun show(title: String = "UI verification", showNavigation: Boolean = true, content: @Composable () -> Unit) {
        val scale = InstrumentationRegistry.getArguments().getString("uiFontScale", "1.0").toFloat()
        compose.setContent { MyApplicationTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, scale)) {
                Scaffold(topBar = {
                    AppTopHeader(title = title, showBack = true, onBackClick = {}, settings = settings, isSyncing = false)
                }, bottomBar = {
                    if (showNavigation) AppBottomNav(BottomTab.REPORT, {}, false)
                }) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) { content() }
                }
            }
        } }
    }
    private fun snapshot(name: String) {
        compose.waitForIdle()
        // Compose idle can precede the platform activity transition's first committed surface.
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        android.os.SystemClock.sleep(300)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        java.io.File(context.cacheDir, "ui-$name.png").outputStream().use {
            assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
    }

    @Test fun cashIsAnInlineFullPageWithPinnedActionsAndOriginalAuditText() {
        show("Adjust available cash", false) { CashBalanceEditor(CashBalanceState("ui_synthetic", enabled = true, loaded = true, confirmed = true, revision = 1, totalAdjustmentPaise = 100,
            edits = listOf(CashBalanceEdit("original", "ui_synthetic", 1, 578567, 578667, 100, "Original reason unchanged", "alpha", "Partner Alpha", 1793514600000))),
            5785.67, false, true, true, {}, { _, _, _, _, _, _ -> fail("Read-only UI fixture") }) }
        compose.onNode(isDialog()).assertDoesNotExist()
        compose.onNodeWithTag("cash_balance_page").assertIsDisplayed()
        compose.onNodeWithTag("cash_balance_save").assertIsDisplayed().assertIsNotEnabled()
        snapshot("cash-page")
        compose.onNodeWithTag("cash_balance_content").performScrollToNode(hasText("Original reason unchanged"))
        compose.onNodeWithText("Original reason unchanged").assertIsDisplayed()
        compose.onNodeWithText("Partner Alpha", substring = true).assertIsDisplayed()
        snapshot("cash-history")
    }

    @Test fun withdrawalCardsRetainAllBalancesAndHistoryNavigation() {
        show("Partner Withdrawals") { WithdrawalTab(settings, listOf(WithdrawalEntity(id = 77, workspaceId = "ui_synthetic", partnerName = "Partner Beta",
            amount = 12643.33, category = "Withdrawal", targetPartnerUid = "beta", createdByUid = "beta")), emptyList(),
            5786.67, 12643.33, {}, {}, isOwner = true, currentUserName = "Owner Test", currentUid = "owner",
            workspaceMembers = members, jobs = listOf(job)) }
        compose.onNodeWithTag("report_metric_value_withdrawal_cash", useUnmergedTree = true).assertTextEquals("₹5,786.67")
        snapshot("withdrawal-summary")
        val list = compose.onNodeWithTag("withdrawal_report_list")
        list.performScrollToNode(hasTestTag("partner_share_card_alpha"))
        compose.onNodeWithTag("partner_share_card_alpha").assertIsDisplayed()
        snapshot("withdrawal-partner")
        list.performScrollToNode(hasTestTag("view_withdrawal_records"))
        compose.onNodeWithTag("view_withdrawal_records").performClick()
        compose.onNodeWithTag("withdrawal_card_77").assertExists()
        snapshot("withdrawal-history")
    }

    @Test fun collectionExpansionPreservesExactCashReceiptAndCustomerName() {
        show("Collection History") { CollectionHistoryTab(settings, listOf(PaymentEntity(id = 20, workspaceId = "ui_synthetic", jobEntryId = 1,
            customerId = 10, customerName = "Synthetic Customer", amount = 18430.0, collectedAt = 1793514600000, paymentMethod = "Cash",
            collectedByUid = "alpha", collectedByName = "Partner Alpha")), listOf(job), workspaceMembers = members,
            isOwner = true, currentUid = "owner") }
        val list = compose.onNodeWithTag("collection_history_list")
        list.performScrollToNode(hasTestTag("collection_job_card_ui_synthetic:job:1"))
        snapshot("collection-card")
        list.performScrollToNode(hasTestTag("expand_job_card_1"))
        compose.onNodeWithTag("expand_job_card_1").performClick()
        list.performScrollToNode(hasTestTag("collection_history_card_20"))
        compose.onNodeWithText("+₹18,430").assertExists()
        compose.onNode(hasText("Cash") and hasAnyAncestor(hasTestTag("collection_history_card_20")), useUnmergedTree = true).assertExists()
        snapshot("collection-timeline")
    }

    @Test fun overviewCashUsesCommaGroupingWithExactPaise() {
        show("Business Overview") { BusinessOverviewScreen(settings, listOf(job), emptyList(), workspaceMembers = members,
            currentUid = "owner", isOwner = true,
            payments = listOf(PaymentEntity(id = 20, workspaceId = "ui_synthetic", jobEntryId = 1, customerId = 10,
                customerName = "Synthetic Customer", amount = 18430.0, paymentMethod = "Cash", collectedAt = 1793514600000)),
            withdrawals = listOf(WithdrawalEntity(id = 77, workspaceId = "ui_synthetic", partnerName = "Partner Beta",
                amount = 12643.33, category = "Withdrawal", targetPartnerUid = "beta", createdByUid = "beta"))) }
        compose.onNodeWithTag("business_overview_list").performScrollToNode(hasText("Available Business Balance"))
        compose.onNodeWithText("₹5,786.67").assertExists()
        snapshot("overview-cash")
    }

    @Test fun expensePageReflowsSummariesAndRetainsTheCompleteRecord() {
        show("Expenses") { ExpensesTab(settings, listOf(ExpenseEntity(id = 91, workspaceId = "ui_synthetic",
            expenseType = "Diesel", amount = 12643.33, tractorLabel = "Test Tractor", addedByPartner = "Owner Test",
            createdByUid = "owner", expenseForUid = "owner", financialAttributionUid = "owner", paidBy = "Business Paid",
            description = "Original expense detail")), tractors = emptyList(), partners = emptyList(),
            onAddExpense = {}, onUpdateExpense = {}, onDeleteExpense = {}, isOwner = true, actorName = "Owner Test",
            workspaceMembers = members, currentUid = "owner") }
        val list = compose.onNodeWithTag("expense_report_list")
        list.performScrollToNode(hasTestTag("report_metric_expense_total"))
        compose.onNodeWithTag("report_metric_value_expense_total", useUnmergedTree = true).assertTextEquals("₹12,643.33")
        snapshot("expenses-summary")
        list.performScrollToNode(hasTestTag("expense_item_91"))
        compose.onNodeWithTag("expense_amount_91", useUnmergedTree = true).assertTextEquals("₹12,643.33")
        compose.onNodeWithText("Original expense detail", useUnmergedTree = true).assertExists()
        snapshot("expenses-record")
    }

    @Test fun balanceSheetPreservesReceiptExpenseAndNetValuesOnSmallScreens() {
        show("Balance Sheet") { BalanceSheetTab(settings, listOf(job), listOf(ExpenseEntity(id = 92, workspaceId = "ui_synthetic",
            expenseType = "Diesel", amount = 12643.33, tractorLabel = "Test Tractor", addedByPartner = "Owner Test", paidBy = "Business Paid")),
            payments = listOf(PaymentEntity(id = 20, workspaceId = "ui_synthetic", jobEntryId = 1, customerId = 10,
                customerName = "Synthetic Customer", amount = 18430.0, paymentMethod = "Cash", collectedAt = 1793514600000))) }
        val list = compose.onNodeWithTag("screen_balance_sheet")
        list.performScrollToNode(hasTestTag("report_metric_sheet_received"))
        compose.onNodeWithTag("report_metric_value_sheet_received", useUnmergedTree = true).assertTextEquals("₹18,430")
        snapshot("balance-sheet-received")
        list.performScrollToNode(hasTestTag("report_metric_sheet_net"))
        compose.onNodeWithTag("report_metric_value_sheet_net", useUnmergedTree = true).assertTextEquals("₹5,786.67")
        snapshot("balance-sheet-net")
    }

    @Test fun expenseAmountAndMenuRemainAccessibleInTamil() {
        show("Expenses") { Column(Modifier.fillMaxWidth()) {
            ExpenseListItemRow(ExpenseEntity(id = 90, expenseType = "Diesel", amount = 12643.33,
                tractorLabel = "Test Tractor", addedByPartner = "Partner Alpha", paidBy = "Business Paid", description = "Original user description"),
                currency = "₹", isTamil = true, onClick = {}, onEdit = {}, onDelete = {}, onShare = {}, canDelete = false)
        } }
        compose.onNodeWithTag("expense_amount_90", useUnmergedTree = true).assertTextEquals("₹12,643.33")
        compose.onNodeWithText("Original user description", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("விருப்பங்கள்").performClick()
        compose.onNodeWithText("செலவைத் திருத்து").assertIsDisplayed()
        compose.onNodeWithText("நீக்கு").assertDoesNotExist()
        snapshot("expense-menu")
    }
}
