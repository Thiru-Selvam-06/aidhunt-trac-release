package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.firebase.WorkspaceMember
import com.example.ui.screens.report.*
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.FinancialScope
import com.example.ui.util.FinancialScopeMode
import com.example.ui.util.PartnerFinancialBreakdown
import com.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "ta-rIN-w411dp-h891dp", application = LedgerUiTestApplication::class)
class TamilLocalizationUiTest {
    @get:Rule val compose = createComposeRule()

    private fun capture(name: String) {
        System.getProperty("trac.localization.captureDir")?.let { directory ->
            File(directory).mkdirs()
            compose.onRoot().captureRoboImage(filePath = File(directory, "$name.png").path)
        }
    }

    @Test fun customerDuesStatusesSwitchBothWaysWithoutTranslatingEnteredValuesOrAmounts() {
        val tamil = mutableStateOf(false)
        val customers = listOf(
            CustomerEntity(id = 1, name = "Rotavator May & Sons", phone = "9876543210", location = "All Town",
                totalBilled = 100.0, totalPaid = 100.0, balanceDue = 0.0),
            CustomerEntity(id = 2, name = "Other தமிழ்", phone = "", totalBilled = 250.0, balanceDue = 250.0),
            CustomerEntity(id = 3, name = "All", phone = "")
        )
        val original = customers.map { it.copy() }
        compose.setContent { MyApplicationTheme { Column {
            customers.forEach { customer ->
                CustomerCreditDueCard(customer, tamil.value, {}, {}, {}, {})
            }
        } } }
        listOf("PAID", "Due", "No Due").forEach { compose.onNodeWithText(it).assertExists() }
        compose.runOnIdle { tamil.value = true }
        listOf("செலுத்தப்பட்டது", "நிலுவை", "நிலுவை இல்லை").forEach { compose.onNodeWithText(it).assertExists() }
        listOf("Rotavator May & Sons", "Other தமிழ்", "All").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("All Town", substring = true).assertExists()
        compose.onNodeWithText("9876543210", substring = true).assertExists()
        compose.onNodeWithText("₹250").assertExists()
        capture("customer-dues-tamil")
        compose.runOnIdle { tamil.value = false }
        listOf("PAID", "Due", "No Due").forEach { compose.onNodeWithText(it).assertExists() }
        assertEquals(original, customers)
    }

    @Test fun overviewDateLabelUpdatesImmediatelyOnLanguageSwitch() {
        val language = mutableStateOf("EN")
        compose.setContent { MyApplicationTheme {
            BusinessOverviewScreen(AppSettingsEntity(language = language.value), emptyList(), emptyList())
        } }
        compose.onNodeWithText("Overall").assertIsDisplayed()
        compose.runOnIdle { language.value = "TA" }
        compose.onNodeWithText("அனைத்தும்").assertIsDisplayed()
        compose.onNodeWithText("மொத்த பதிவு").assertExists()
        compose.onNodeWithText("செலவைக் காட்டு").assertIsDisplayed()
        capture("business-overview-tamil")
        compose.runOnIdle { language.value = "EN" }
        compose.onNodeWithText("Overall").assertIsDisplayed()
        compose.onNodeWithText("அனைத்தும்").assertDoesNotExist()
    }

    @Test fun overviewKeepsSelectedMonthWhileTranslatingTheDateLabel() {
        val language = mutableStateOf("EN")
        val scope = FinancialScope(mode = FinancialScopeMode.MONTHLY, month = 1, year = 2026)
        compose.setContent { MyApplicationTheme {
            BusinessOverviewScreen(AppSettingsEntity(language = language.value), emptyList(), emptyList(), initialScope = scope)
        } }
        compose.onNodeWithText("Jan 2026").assertIsDisplayed()
        compose.runOnIdle { language.value = "TA" }
        compose.onNodeWithText("ஜன 2026").assertIsDisplayed()
        compose.runOnIdle { language.value = "EN" }
        compose.onNodeWithText("Jan 2026").assertIsDisplayed()
    }

    @Test fun overviewCalendarsShowTamilDaysMonthsAndActions() {
        compose.setContent { MyApplicationTheme {
            BusinessOverviewScreen(AppSettingsEntity(language = "TA"), emptyList(), emptyList())
        } }
        val choices = listOf("தினசரி" to "நாளைத் தேர்ந்தெடுக்கவும்",
            "மாதாந்திர" to "மாதத்தைத் தேர்ந்தெடுக்கவும்", "வருடாந்திர" to "ஆண்டைத் தேர்ந்தெடுக்கவும்")
        choices.forEach { (choice, title) ->
            compose.onNodeWithTag("business_overview_date_pill").performClick()
            compose.onNodeWithText(choice).performClick()
            compose.onNodeWithText(title).assertExists()
            compose.onNodeWithText("பயன்படுத்து").assertExists()
            if (choice == "தினசரி") compose.onNodeWithText("ஞா").assertExists()
            if (choice == "மாதாந்திர") compose.onNodeWithText("ஜன").assertExists()
            compose.onNodeWithText("ரத்து செய்").performClick()
        }
    }

    @Test fun overviewMetricLabelsSwitchLanguagesAndKeepEnteredMemberData() {
        val language = mutableStateOf("EN")
        val member = WorkspaceMember(uid = "fixture-member", workspaceId = "fixture", displayName = "All", role = "PARTNER", status = "ACTIVE")
        // The real screen receives the ViewModel's stable scope, rather than a new timestamp on recomposition.
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
        compose.onNodeWithTag("records_person_filter_pill").assertTextContains("அனைவரும்")
        // The complete dialog journey and its assertions run in mandatory Android instrumentation.
        // Approved after CI run 39755775953: Android 19/19 pass; Robolectric dialog never idles.
        compose.runOnIdle { language.value = "EN" }
        compose.onNodeWithTag("records_person_filter_pill").assertTextContains("All")
        assertEquals("All", member.displayName)
    }

    @Test fun overviewRoleBadgeAndDetailsTranslateWithoutChangingTheMemberName() {
        val tamil = mutableStateOf(false)
        compose.setContent { MyApplicationTheme {
            DriverEarningsCircularChart(listOf(PartnerFinancialBreakdown(partnerUid = "member",
                partnerName = "May Services தமிழ்", role = "PARTNER", recorded = 100.0, received = 40.0, due = 60.0)),
                false, "₹", tamil.value)
        } }
        compose.onNodeWithText("Partner").assertExists()
        compose.runOnIdle { tamil.value = true }
        compose.onNodeWithText("பங்குதாரர்").assertExists()
        compose.onNodeWithText("May Services தமிழ்").assertExists()
        compose.onNodeWithTag("business_member_circle_member").performClick()
        compose.onAllNodesWithText("பங்குதாரர்").assertCountEquals(2)
        compose.runOnIdle { tamil.value = false }
        compose.onAllNodesWithText("Partner").assertCountEquals(2)
    }

    private val expenseTime = Calendar.getInstance().apply {
        set(2026, Calendar.OCTOBER, 21, 10, 5, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val expense = ExpenseEntity(id = 42, workspaceId = "fixture", expenseType = "Diesel", amount = 900.0,
        tractorLabel = "May Tractor தமிழ்", addedByPartner = "Other", description = "Do not translate my note",
        dateTimestamp = expenseTime, createdAt = expenseTime)

    @Test fun expenseListDateUsesSelectedLanguageAndUpdatesWithoutReopening() {
        val tamil = mutableStateOf(false)
        compose.setContent { MyApplicationTheme {
            ExpenseListItemRow(expense, currency = "₹", isTamil = tamil.value, onClick = {}, onEdit = {}, onDelete = {}, onShare = {})
        } }
        compose.onNodeWithText("21 Oct 2026, 10:05 AM").assertExists()
        compose.runOnIdle { tamil.value = true }
        compose.onNodeWithText("அக்", substring = true).assertExists()
        compose.onNodeWithText("May Tractor தமிழ்", substring = true).assertExists()
        compose.runOnIdle { tamil.value = false }
        compose.onNodeWithText("21 Oct 2026, 10:05 AM").assertExists()
    }

    @Test fun expenseDetailsDateSwitchesWithoutChangingTheSavedDescription() {
        val language = mutableStateOf("EN")
        compose.setContent { MyApplicationTheme {
            ExpenseDetailsScreen(expense, settings = AppSettingsEntity(workspaceId = "fixture", language = language.value),
                onBack = {}, onEdit = {}, onDelete = {})
        } }
        compose.onAllNodesWithText("21 Oct 2026, 10:05 AM").onFirst().assertExists()
        compose.runOnIdle { language.value = "TA" }
        compose.onAllNodesWithText("அக்", substring = true).onFirst().assertExists()
        compose.onNodeWithText("Do not translate my note").assertExists()
        compose.runOnIdle { language.value = "EN" }
        compose.onAllNodesWithText("21 Oct 2026, 10:05 AM").onFirst().assertExists()
        assertEquals("Do not translate my note", expense.description)
    }

    @Test fun withdrawalRoleLabelsTranslateButPartnerNamesStayAsEntered() {
        val tamil = mutableStateOf(false)
        val members = listOf(
            WorkspaceMember(uid = "owner", role = "OWNER", status = "ACTIVE", displayName = "May Services"),
            WorkspaceMember(uid = "partner", role = "PARTNER", status = "ACTIVE", displayName = "Other தமிழ்")
        )
        compose.setContent { MyApplicationTheme {
            TakeAmountDialog(partners = emptyList(), workspaceMembers = members, isTamil = tamil.value, isOwner = true,
                currentUid = "owner", availableAmount = 1000.0, onDismiss = {}, onConfirm = {})
        } }
        compose.onNodeWithText("May Services").performClick()
        compose.onNodeWithText("May Services (Owner)").assertExists()
        compose.onNodeWithText("Other தமிழ் (Partner)").assertExists()
        compose.runOnIdle { tamil.value = true }
        compose.onNodeWithText("May Services (உரிமையாளர்)").assertExists()
        compose.onNodeWithText("Other தமிழ் (பங்குதாரர்)").assertExists()
        compose.runOnIdle { tamil.value = false }
        compose.onNodeWithText("May Services (Owner)").assertExists()
    }
}
