package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.entity.ExpenseEntity
import com.example.ui.components.*
import com.example.ui.screens.report.ExpenseListItemRow
import com.example.ui.screens.report.PartnerShareInvestmentCard
import com.example.ui.screens.report.WithdrawalTab
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.PartnerShareInvestmentStatus
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w600dp-h1000dp", application = LedgerUiTestApplication::class)
class ReportResponsiveUiTest {
    @get:Rule val compose = createComposeRule()

    private fun assertWholeAmount(tag: String, amount: String) {
        val node = compose.onNodeWithTag(tag, useUnmergedTree = true).assertTextEquals(amount)
        val layout = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
        assertEquals(1, layout.single().lineCount)
        assertFalse("Amount must not be clipped: $amount tag=$tag size=${layout.single().size} widthOverflow=${layout.single().didOverflowWidth} heightOverflow=${layout.single().didOverflowHeight}", layout.single().hasVisualOverflow)
    }

    @Test fun compactTamilLargeTextStacksMetricsAndKeepsEveryPaisaVisible() {
        compose.setContent { MyApplicationTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                Box(Modifier.width(320.dp).padding(16.dp)) {
                    ReportMetrics(listOf(ReportMetric("கிடைக்கும் ரொக்கம்", "₹5,786.67", tag = "cash"),
                        ReportMetric("மொத்த எடுப்புகள்", "₹12,643.33", tag = "total")))
                }
            }
        } }
        assertWholeAmount("report_metric_value_cash", "₹5,786.67")
        assertWholeAmount("report_metric_value_total", "₹12,643.33")
        val first = compose.onNodeWithTag("report_metric_cash").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("report_metric_total").fetchSemanticsNode().boundsInRoot
        assertTrue(second.top > first.bottom)
    }

    @Test fun wideMetricsUseAvailableSpaceWithoutChangingValues() {
        compose.setContent { MyApplicationTheme {
            Box(Modifier.width(580.dp)) { ReportMetrics(listOf(ReportMetric("Cash", "₹5,786.67", tag = "cash"),
                ReportMetric("Withdrawals", "₹12,643.33", tag = "total"))) }
        } }
        assertWholeAmount("report_metric_value_cash", "₹5,786.67")
        assertWholeAmount("report_metric_value_total", "₹12,643.33")
        val first = compose.onNodeWithTag("report_metric_cash").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("report_metric_total").fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 0.1f)
        assertTrue(second.left > first.right)
    }

    @Test fun sevenFigureAmountsWithPaiseNeverLoseDigitsInHalfWidthCards() {
        compose.setContent { MyApplicationTheme {
            Box(Modifier.width(380.dp).padding(16.dp)) {
                ReportMetrics(listOf(ReportMetric("Recorded", "₹1,060,816.67", tag = "large"),
                    ReportMetric("Received", "₹180,100.01", tag = "received")))
            }
        } }
        assertWholeAmount("report_metric_value_large", "₹1,060,816.67")
        assertWholeAmount("report_metric_value_received", "₹180,100.01")
    }

    @Test fun partnerBalancesDoNotOverlapAndOtherMemberCardRemainsReadOnly() {
        compose.setContent { MyApplicationTheme {
            Box(Modifier.width(320.dp)) { PartnerShareInvestmentCard(
                PartnerShareInvestmentStatus("Partner Alpha", partnerUid = "alpha", totalShare = 6143.34,
                    remainingShare = 6143.34, totalToGet = 6143.34, businessFundedCost = 53890.0),
                isTamil = false, isOwnCard = false, onTakeAmount = { fail("Other member action") }) }
        } }
        assertWholeAmount("share_alpha_remaining", "₹6,143.34")
        assertWholeAmount("investment_alpha_remaining", "₹0")
        compose.onNodeWithTag("partner_get_alpha").assertHasNoClickAction()
        val share = compose.onNodeWithTag("share_alpha").fetchSemanticsNode().boundsInRoot
        val investment = compose.onNodeWithTag("investment_alpha").fetchSemanticsNode().boundsInRoot
        val action = compose.onNodeWithTag("partner_get_alpha").fetchSemanticsNode().boundsInRoot
        assertTrue(investment.top > share.bottom)
        assertTrue(action.top > investment.bottom)
    }

    @Test fun compactExpenseRetainsFullAmountUserDataAndAuthorizedMenu() {
        var edited = false
        compose.setContent { MyApplicationTheme {
            Box(Modifier.width(320.dp)) { ExpenseListItemRow(
                ExpenseEntity(id = 77, expenseType = "Diesel", amount = 12643.33, description = "Original user wording",
                    tractorLabel = "Test Tractor", addedByPartner = "Partner Alpha", paidBy = "Business Paid"), currency = "₹", isTamil = true,
                onClick = {}, onEdit = { edited = true }, onDelete = {}, onShare = {}, canDelete = false) }
        } }
        assertWholeAmount("expense_amount_77", "₹12,643.33")
        compose.onNodeWithText("Original user wording", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("விருப்பங்கள்").performClick()
        compose.onNodeWithText("நீக்கு").assertDoesNotExist()
        compose.onNodeWithText("செலவைத் திருத்து").performClick()
        compose.runOnIdle { assertTrue(edited) }
    }

    @Test fun cashDraftAndRejectedOperationIdentitySurviveStateRestoration() {
        val restoration = StateRestorationTester(compose)
        val ids = mutableListOf<String>()
        restoration.setContent { MyApplicationTheme {
            CashBalanceEditor(com.example.data.firebase.CashBalanceState("synthetic", enabled = true, loaded = true, confirmed = true),
                1286.67, false, true, true, {}, { _, _, _, reason, id, done ->
                    assertEquals("Original cash reason", reason)
                    ids.add(id)
                    done("Authorization denied; original balance retained")
                })
        } }
        compose.onNodeWithTag("cash_balance_amount").performTextReplacement("2000.25")
        compose.onNodeWithTag("cash_balance_reason").performTextInput("Original cash reason")
        compose.onNodeWithTag("cash_balance_save").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("cash_balance_amount").assertTextContains("2000.25")
        compose.onNodeWithTag("cash_balance_reason").assertTextContains("Original cash reason")
        compose.onNodeWithTag("cash_balance_save").performClick()
        compose.runOnIdle { assertEquals(2, ids.size); assertEquals(ids[0], ids[1]) }
    }

    @Test fun compactLargeTextActionsStackWithoutSplittingWords() {
        compose.setContent { MyApplicationTheme {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                Box(Modifier.width(320.dp)) { WithdrawalTab(
                    com.example.data.entity.AppSettingsEntity(workspaceId = "synthetic"), emptyList(), emptyList(),
                    5786.67, 0.0, {}, {}, currentUid = "actor") }
            }
        } }
        val first = compose.onNodeWithTag("btn_take_amount").fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithTag("btn_export_withdrawal_pdf").fetchSemanticsNode().boundsInRoot
        assertTrue(second.top > first.bottom)
        listOf("Take Amount", "PDF Report").forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(label, useUnmergedTree = true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertEquals(1, layouts.single().lineCount)
            assertFalse("$label size=${layouts.single().size} width=${layouts.single().didOverflowWidth} height=${layouts.single().didOverflowHeight} lineBottom=${layouts.single().getLineBottom(0)}", layouts.single().hasVisualOverflow)
        }
    }
}
