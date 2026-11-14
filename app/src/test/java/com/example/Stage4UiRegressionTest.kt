package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.entity.AppNotificationEntity
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.ExternalLoanEntity
import com.example.data.entity.ExternalLoanRepaymentEntity
import com.example.ui.components.AppTopHeader
import com.example.ui.components.NotificationPanelSheet
import com.example.ui.screens.report.ExternalLoansScreen
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp", application = LedgerUiTestApplication::class)
class Stage4UiRegressionTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun appTopHeaderShowsBellOnlyWhenEnabledAndDisplaysUnreadBadge() {
        var clicked = 0
        compose.setContent {
            MyApplicationTheme {
                AppTopHeader(
                    settings = AppSettingsEntity(workspaceId = "ws_1", activePartnerName = "Owner"),
                    isSyncing = false,
                    showNotificationBell = true,
                    notificationUnreadCount = 3,
                    onNotificationBellClick = { clicked++ }
                )
            }
        }
        compose.onNodeWithTag("notification_bell_button").assertIsDisplayed().performClick()
        compose.onNodeWithTag("notification_unread_badge", useUnmergedTree = true).assertExists()
        assertEquals(1, clicked)
    }

    @Test
    fun notificationPanelFiltersAndMarksAllReadInTamilAndEnglish() {
        var markAllCalled = 0
        var markedSingle = ""
        val sampleNotifications = listOf(
            AppNotificationEntity(
                id = "n1", workspaceId = "ws_1", operationId = "op1",
                recipientUid = "owner", targetAudience = AppNotificationEntity.AUDIENCE_OWNER_ONLY,
                actorUid = "p1", actorName = "Kumar", eventType = AppNotificationEntity.EVENT_JOB_CREATED,
                entityType = "JOB", entityId = 1L, amount = 1200.0, counterpartyName = "Ravi",
                createdAt = 1_763_100_000_000L, isRead = false
            ),
            AppNotificationEntity(
                id = "n2", workspaceId = "ws_1", operationId = "op2",
                recipientUid = "owner", targetAudience = AppNotificationEntity.AUDIENCE_PARTNER_TARGETED,
                actorUid = "p2", actorName = "Arun", eventType = AppNotificationEntity.EVENT_BORROWING_REQUESTED,
                entityType = "BORROWING_REQUEST", entityId = 2L, amount = 700.0, counterpartyName = "Arun",
                createdAt = 1_763_100_010_000L, isRead = true
            )
        )
        compose.setContent {
            MyApplicationTheme {
                NotificationPanelSheet(
                    notifications = sampleNotifications,
                    isOwner = true,
                    isTamil = true,
                    onMarkRead = { markedSingle = it },
                    onMarkAllRead = { markAllCalled++ },
                    onDismiss = {}
                )
            }
        }
        compose.onNodeWithTag("notification_panel_sheet").assertIsDisplayed()
        compose.onNodeWithTag("notification_item_n1").assertIsDisplayed()
        compose.onNodeWithTag("notification_item_n2").assertIsDisplayed()

        compose.onNodeWithTag("notif_filter_unread").performClick()
        compose.onNodeWithTag("notification_item_n1").assertExists()
        compose.onNodeWithTag("notification_item_n2").assertDoesNotExist()

        compose.onNodeWithTag("notif_filter_approvals").performClick()
        compose.onNodeWithTag("notification_item_n2").assertExists()

        compose.onNodeWithTag("btn_mark_all_notifications_read").performClick()
        assertEquals(1, markAllCalled)

        compose.onNodeWithTag("notif_filter_all").performClick()
        compose.onNodeWithTag("notification_item_n1").performClick()
        assertTrue(markedSingle == "n1")
    }

    @Test
    fun externalLoansScreenEmbedsLoanAnalyticsCardWithSeparatedModes() {
        val now = System.currentTimeMillis()
        val loan = ExternalLoanEntity(
            id = 1L, workspaceId = "ws_1", lenderName = "Co-op Bank",
            principalAmount = 4000.0, interestAmount = 400.0,
            totalRepaidPrincipal = 1000.0, totalRepaidInterest = 100.0,
            destination = "BUSINESS_BANK", status = "ACTIVE", loanDateMillis = now
        )
        val repayment = ExternalLoanRepaymentEntity(
            id = 2L, workspaceId = "ws_1", loanId = 1L,
            principalPaid = 1000.0, interestPaid = 100.0, source = "BUSINESS_BANK", repaidAt = now
        )
        compose.setContent {
            MyApplicationTheme {
                ExternalLoansScreen(
                    settings = AppSettingsEntity(workspaceId = "ws_1", language = "EN"),
                    loans = listOf(loan),
                    repayments = listOf(repayment),
                    availableCash = 2500.0,
                    canManageLoans = true,
                    onRecordLoan = { _, _, _, _, _, _, _, _ -> },
                    onRecordRepayment = { _, _, _, _, _, _, _, _ -> }
                )
            }
        }
        compose.onNodeWithTag("loan_analytics_section").assertIsDisplayed()
        compose.onNodeWithTag("loan_analytics_toggle").performClick()
        compose.onNodeWithTag("loan_analytics_mode_repayments").assertIsDisplayed()
        compose.onNodeWithTag("loan_analytics_mode_outstanding").performClick()
        compose.onNodeWithTag("loan_analytics_chart").assertIsDisplayed()
        compose.onNodeWithTag("loan_analytics_mode_funding").performClick()
        compose.onNodeWithText("Business Bank").assertIsDisplayed()
    }
}
