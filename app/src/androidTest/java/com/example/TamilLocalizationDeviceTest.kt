package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.firebase.WorkspaceMember
import com.example.ui.screens.report.BusinessOverviewScreen
import com.example.ui.screens.report.CustomerCreditDueCard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.FinancialScope
import com.example.ui.util.FinancialScopeMode
import com.google.firebase.FirebaseApp
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic UI journeys only; the CI plan requires the separate demo application. */
@RunWith(AndroidJUnit4::class)
class TamilLocalizationDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Before fun requireIsolatedDemoApplication() {
        check(BuildConfig.DEVICE_TEST)
        check(InstrumentationRegistry.getInstrumentation().targetContext.packageName.endsWith(".devicetest"))
        check(FirebaseApp.getInstance().options.projectId == "demo-aidhunt-v16")
    }

    @Test fun overviewMetricAndPersonFiltersPreserveEnteredMemberNamesAcrossLanguages() {
        val language = mutableStateOf("EN")
        val member = WorkspaceMember(uid = "fixture-member", workspaceId = "fixture", displayName = "All", role = "PARTNER", status = "ACTIVE")
        val scope = FinancialScope(mode = FinancialScopeMode.OVERALL, dateMillis = 0, year = 2026, month = 1, workspaceId = "fixture")
        compose.setContent { MyApplicationTheme {
            BusinessOverviewScreen(AppSettingsEntity(workspaceId = "fixture", language = language.value), emptyList(), emptyList(),
                workspaceMembers = listOf(member), currentUid = member.uid, initialScope = scope)
        } }
        compose.runOnIdle { language.value = "TA" }
        compose.onNodeWithText("தொகை").performScrollTo().performClick()
        compose.onNodeWithText("நேரம்").assertExists()
        compose.onNodeWithText("பணிகள்").performClick()
        compose.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasTestTag("records_person_filter_pill"))
        compose.onNodeWithTag("records_person_filter_pill").assertTextContains("அனைவரும்").performTouchInput { click() }
        compose.onNode(hasText("அனைவரும்") and hasAnyAncestor(isDialog()), useUnmergedTree = true).assertExists()
        compose.onNode(hasText("All") and hasAnyAncestor(isDialog()), useUnmergedTree = true).performClick()
        compose.onNodeWithTag("records_person_filter_pill").assertTextContains("All")
        compose.runOnIdle { language.value = "EN" }
        compose.onNodeWithTag("records_person_filter_pill").assertTextContains("All")
    }

    @Test fun customerStatusLabelsTranslateAndEnteredFieldsAndAmountsRemainExact() {
        val tamil = mutableStateOf(false)
        val customers = listOf(
            CustomerEntity(id = 1, name = "Rotavator May & Sons", phone = "9876543210", location = "All Town",
                totalBilled = 100.0, totalPaid = 100.0, balanceDue = 0.0),
            CustomerEntity(id = 2, name = "Other தமிழ்", phone = "", totalBilled = 250.0, balanceDue = 250.0),
            CustomerEntity(id = 3, name = "All", phone = "")
        )
        val original = customers.map { it.copy() }
        compose.setContent { MyApplicationTheme { Column {
            customers.forEach { CustomerCreditDueCard(it, tamil.value, {}, {}, {}, {}) }
        } } }
        listOf("PAID", "Due", "No Due").forEach { compose.onNodeWithText(it).assertExists() }
        compose.runOnIdle { tamil.value = true }
        listOf("செலுத்தப்பட்டது", "நிலுவை", "நிலுவை இல்லை").forEach { compose.onNodeWithText(it).assertExists() }
        listOf("Rotavator May & Sons", "Other தமிழ்", "All").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("All Town", substring = true).assertExists()
        compose.onNodeWithText("9876543210", substring = true).assertExists()
        compose.onNodeWithText("₹250").assertExists()
        compose.runOnIdle { tamil.value = false }
        listOf("PAID", "Due", "No Due").forEach { compose.onNodeWithText(it).assertExists() }
        assertEquals(original, customers)
    }

    @Test fun customerDuesPartnerFilterAggregatesOnlyBeneficiaryJobsAndKeepsCollectionAccountComplete() {
        val language = mutableStateOf("EN")
        val people = listOf("owner" to "Owner", "partner-a" to "Perumal", "partner-b" to "Sriram").map { (uid, name) ->
            WorkspaceMember(uid = uid, workspaceId = "fixture", displayName = name,
                role = if (uid == "owner") "OWNER" else "PARTNER", status = "ACTIVE")
        }
        fun job(id: Long, target: String, total: Double, paid: Double) = com.example.data.entity.JobEntryEntity(
            id = id, workspaceId = "fixture", customerId = 10, customerName = "Raman", customerPhone = "",
            operatorName = target, tractorLabel = "Tractor", workType = "Rotavator", startTimeMillis = 1000,
            endTimeMillis = 2000, durationMinutes = 1, totalAmount = total, amountReceived = paid,
            pendingAmount = total - paid, addedByPartner = "Owner", createdAt = 1000,
            createdByUid = "owner", createdByRole = "OWNER", entryForUid = target, financialAttributionUid = target)
        val jobs = listOf(job(1, "partner-a", 500.0, 200.0), job(2, "partner-b", 800.0, 100.0))
        compose.setContent { MyApplicationTheme {
            com.example.ui.screens.report.CustomerCreditDueTab(
                settings = AppSettingsEntity(workspaceId = "fixture", language = language.value),
                customers = listOf(CustomerEntity(id = 10, workspaceId = "fixture", name = "Raman", phone = "")), jobs = jobs,
                workspaceMembers = people, actorUid = "owner", isOwner = true, onRecordPaymentWithCollector = { _, _, _, _, _, _, _, _ -> })
        } }
        compose.onAllNodesWithText("₹1,000").onFirst().assertExists()
        compose.onNodeWithTag("dues_partner_partner-a").performScrollTo().performClick()
        compose.onAllNodesWithText("₹300").onFirst().assertExists()
        compose.onNodeWithTag("dues_partner_partner-b").performScrollTo().performClick()
        compose.onAllNodesWithText("₹700").onFirst().assertExists()
        compose.runOnIdle { language.value = "TA" }
        compose.onNodeWithText("Sriram").assertExists()
        compose.onAllNodesWithText("₹700").onFirst().assertExists()
        compose.onNodeWithTag("btn_add_payment_card_10").performScrollTo().performClick()
        compose.onNode(hasText("₹1,000", substring = true) and hasAnyAncestor(isDialog()), useUnmergedTree = true).assertExists()
        assertEquals(listOf(300.0, 700.0), jobs.map { it.pendingAmount })
        assertEquals(listOf("partner-a", "partner-b"), jobs.map { it.entryForUid })
    }
}
