package com.example

import androidx.compose.ui.test.*
import androidx.compose.runtime.mutableStateOf
import com.example.data.entity.AppSettingsEntity
import com.example.data.firebase.WorkspaceMember
import com.example.ui.screens.report.BusinessOverviewScreen
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.ui.screens.report.DriverEarningsCircularChart
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.PartnerFinancialBreakdown
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w320dp-h640dp")
class BusinessOverviewCircleUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun circleShowsMemberAndExactCollectedAndDueWithoutOpeningDetails() {
        compose.setContent { MyApplicationTheme {
            DriverEarningsCircularChart(listOf(PartnerFinancialBreakdown(partnerUid = "partner", partnerName = "Partner Name",
                role = "PARTNER", recorded = 100.0, received = 40.0, due = 60.0)), false, "₹", false)
        } }
        compose.onNodeWithTag("business_member_circle_partner").assertIsDisplayed()
        compose.onNodeWithText("Partner Name", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Collected: ₹40", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Due: ₹60", useUnmergedTree = true).assertIsDisplayed()
    }
    @Test fun missingIdentityDetailsDoNotClaimAMigrationIsRequired() {
        compose.setContent { MyApplicationTheme {
            DriverEarningsCircularChart(listOf(PartnerFinancialBreakdown(partnerUid = "saved-uid",
                partnerName = "Member details unavailable", role = "", recorded = 100.0, received = 0.0, due = 100.0)), false, "₹", false)
        } }
        compose.onNodeWithTag("business_member_circle_saved-uid").assertIsDisplayed()
        compose.onNodeWithText("Member details unavailable", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Due: ₹100", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Unattributed (migration required)", useUnmergedTree = true).assertDoesNotExist()
    }
    @Test fun partnerZeroCircleShowsExactZeroAmounts() {
        compose.setContent { MyApplicationTheme {
            DriverEarningsCircularChart(listOf(PartnerFinancialBreakdown(partnerUid = "partner", partnerName = "Partner", role = "PARTNER")), false, "₹", false)
        } }
        compose.onNodeWithTag("business_member_circle_partner").assertIsDisplayed()
        compose.onNodeWithText("Collected: ₹0", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithText("Due: ₹0", useUnmergedTree = true).assertIsDisplayed()
    }
    @Test fun accountAndWorkspaceSwitchResetActualScreenPersonSelection() {
        val uid = mutableStateOf("owner")
        val workspace = mutableStateOf("a")
        val members = listOf("owner", "partner").map { WorkspaceMember(uid = it, workspaceId = "a", displayName = it, status = "ACTIVE", role = if (it == "owner") "OWNER" else "PARTNER") }
        compose.setContent { MyApplicationTheme {
            BusinessOverviewScreen(settings = AppSettingsEntity(workspaceId = workspace.value), jobs = emptyList(), expenses = emptyList(), workspaceMembers = members, currentUid = uid.value)
        } }
        fun selectOwner() {
            compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("records_person_filter_pill"))
            compose.onNodeWithTag("records_person_filter_pill").performClick()
            compose.onNode(hasText("owner") and hasAnyAncestor(isDialog()), useUnmergedTree = true).performClick()
            compose.onNodeWithText("Showing records for owner").assertExists()
        }
        selectOwner()
        compose.runOnIdle { uid.value = "partner" }
        compose.onNodeWithText("Showing records for All").assertExists()
        selectOwner()
        compose.runOnIdle { uid.value = "owner" }
        compose.onNodeWithText("Showing records for All").assertExists()
        selectOwner()
        compose.runOnIdle { workspace.value = "b" }
        compose.onNodeWithText("Showing records for All").assertExists()
    }

    @Test fun ownerCanScrollToEveryIdlePartnerAndSeeZeroCollectedAndDue() {
        val members = listOf("owner", "idle-one", "idle-two").map { WorkspaceMember(uid = it, workspaceId = "a", displayName = it, status = "ACTIVE", role = if (it == "owner") "OWNER" else "PARTNER") }
        val circles = com.example.ui.util.businessOverviewChartMembers(emptyList(), members, "owner", "a")
        compose.setContent { MyApplicationTheme { DriverEarningsCircularChart(circles, false, "INR ", false) } }
        compose.onNodeWithTag("business_member_circle_owner").assertIsDisplayed()
        compose.onNode(hasScrollToIndexAction()).performScrollToIndex(2)
        val idle = compose.onNodeWithTag("business_member_circle_idle-two")
        idle.assertIsDisplayed()
        compose.onNode(hasText("Collected: INR 0") and hasAnyAncestor(hasTestTag("business_member_circle_idle-two")), useUnmergedTree = true).assertExists()
        compose.onNode(hasText("Due: INR 0") and hasAnyAncestor(hasTestTag("business_member_circle_idle-two")), useUnmergedTree = true).assertExists()
    }

}
