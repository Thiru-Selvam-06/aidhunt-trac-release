package com.example

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.firebase.WorkspaceMember
import com.example.ui.components.formatShortExpenseId
import com.example.ui.screens.report.ExpenseDetailsScreen
import com.example.ui.screens.report.ExpenseListItemRow
import com.example.ui.screens.report.ExpensesTab
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp", application = LedgerUiTestApplication::class)
class ExpenseRedesignUiTest {
    @get:Rule val compose = createComposeRule()

    private val istTime = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
        set(2026, Calendar.NOVEMBER, 27, 7, 10, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val createdTime = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata")).apply {
        set(2026, Calendar.NOVEMBER, 27, 7, 11, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val sampleExpense = ExpenseEntity(
        id = 3765941234567879155L,
        workspaceId = "ws-1",
        expenseType = "Diesel",
        amount = 5400.0,
        tractorId = 1L,
        tractorLabel = "4710",
        operatorName = "Sriram",
        description = "oil service",
        addedByPartner = "Muniyappan",
        dateTimestamp = istTime,
        createdAt = createdTime,
        paidBy = "Business Paid",
        createdByUid = "uid-owner"
    )

    @Test
    fun expenseEntryCardDisplaysAllVisualFieldsBadgesAndOverflowActions() {
        var clicked = 0
        var edited = 0
        var deleted = 0
        var shared = 0
        compose.setContent {
            MyApplicationTheme {
                ExpenseListItemRow(
                    expense = sampleExpense,
                    currency = "₹",
                    isTamil = false,
                    onClick = { clicked++ },
                    onEdit = { edited++ },
                    onDelete = { deleted++ },
                    onShare = { shared++ },
                    canEdit = true,
                    canDelete = true
                )
            }
        }
        compose.onNodeWithText("Diesel").assertExists()
        compose.onNodeWithText("4710").assertExists()
        compose.onNodeWithText("Business Paid").assertExists()
        compose.onNodeWithText("Shared expense").assertExists()
        compose.onNodeWithText("₹5,400").assertExists()
        compose.onNodeWithText("27 Nov 2026, 07:10 AM").assertExists()
        compose.onNodeWithText("oil service").assertExists()

        // Card tap opens details without triggering menu actions
        compose.onNodeWithTag("expense_item_${sampleExpense.id}").performClick()
        compose.runOnIdle { assertEquals(1, clicked) }

        // Overflow menu preserves all 4 actions
        compose.onNodeWithTag("btn_expense_menu_${sampleExpense.id}").performClick()
        compose.onNodeWithText("View Details").assertExists()
        compose.onNodeWithText("Edit Expense").performClick()
        compose.runOnIdle { assertEquals(1, edited) }

        compose.onNodeWithTag("btn_expense_menu_${sampleExpense.id}").performClick()
        compose.onNodeWithText("Share WhatsApp").performClick()
        compose.runOnIdle { assertEquals(1, shared) }

        compose.onNodeWithTag("btn_expense_menu_${sampleExpense.id}").performClick()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals(1, deleted) }
    }

    @Test
    fun expenseEntryCardRespectsPermissionsAndEmptyDescriptionSuppression() {
        val jobExpense = sampleExpense.copy(
            id = 88L,
            relatedJobId = 9L,
            description = "Expense for Kannan's job",
            fundingSource = "EXTERNAL_LOAN",
            paidBy = "Loan Funded",
            externalLoanId = 5L
        )
        val job = JobEntryEntity(
            id = 9L,
            workspaceId = "ws-1",
            customerId = 1L,
            customerName = "Kannan",
            operatorName = "Sriram",
            tractorLabel = "4710",
            workType = "Rotavator",
            startTimeMillis = istTime,
            endTimeMillis = istTime,
            durationMinutes = 30,
            totalAmount = 1000.0,
            amountReceived = 500.0,
            pendingAmount = 500.0,
            addedByPartner = "Muniyappan"
        )
        compose.setContent {
            MyApplicationTheme {
                ExpenseListItemRow(
                    expense = jobExpense,
                    jobs = listOf(job),
                    currency = "₹",
                    isTamil = false,
                    onClick = {},
                    onEdit = {},
                    onDelete = {},
                    onShare = {},
                    canEdit = false,
                    canDelete = false
                )
            }
        }
        compose.onNodeWithText("For: Kannan").assertExists()
        compose.onNodeWithText("Loan Funded").assertExists()
        compose.onNodeWithText("Direct to Supplier").assertExists()
        compose.onNodeWithText("Expense for Kannan's job").assertDoesNotExist()

        compose.onNodeWithTag("btn_expense_menu_88").performClick()
        compose.onNodeWithText("View Details").assertExists()
        compose.onNodeWithText("Share WhatsApp").assertExists()
        compose.onNodeWithText("Edit Expense").assertDoesNotExist()
        compose.onNodeWithText("Delete").assertDoesNotExist()
    }

    @Test
    fun expenseDetailsDoesNotHardcodeCashAndCopiesCompleteOriginalId() {
        val deletedCalled = mutableStateOf(false)
        compose.setContent {
            MyApplicationTheme {
                ExpenseDetailsScreen(
                    expense = sampleExpense,
                    settings = AppSettingsEntity(workspaceId = "ws-1"),
                    onBack = {},
                    onEdit = {},
                    onDelete = { deletedCalled.value = true },
                    canEdit = true,
                    canDelete = true
                )
            }
        }
        compose.onNodeWithTag("expense_details_header").assertExists()
        compose.onNodeWithText("Expense Details").assertExists()
        compose.onNodeWithText("Cash").assertDoesNotExist()
        compose.onNodeWithTag("expense_payment_method_badge").assertDoesNotExist()

        // Shortened visual ID + full-ID clipboard copy
        compose.onNodeWithTag("expense_record_identity").assertTextEquals("376594...79155")
        compose.onNodeWithTag("expense_reference_card").performClick()
        compose.runOnIdle {
            val clipboard = ApplicationProvider.getApplicationContext<Context>()
                .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals("3765941234567879155", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }

        // All structured detail fields exist
        compose.onNodeWithText("Tractor: 4710").assertExists()
        compose.onNodeWithText("Sriram").performScrollTo().assertExists()
        compose.onNodeWithText("Business Paid · Shared expense").performScrollTo().assertExists()
        compose.onNodeWithText("Muniyappan").performScrollTo().assertExists()
        compose.onNodeWithText("27 Nov 2026, 07:11 AM").performScrollTo().assertExists()

        // Delete button opens confirmation dialog and triggers onDelete
        compose.onNodeWithTag("btn_delete_expense_details").performScrollTo().performClick()
        compose.onNodeWithText("Delete Expense?").assertExists()
        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertTrue(deletedCalled.value) }
    }

    @Test
    fun expenseDetailsShowsRealPaymentMethodWhenProvidedAndFullShortId() {
        val shortExpense = sampleExpense.copy(id = 42L)
        assertEquals("42", formatShortExpenseId(42L))
        compose.setContent {
            MyApplicationTheme {
                ExpenseDetailsScreen(
                    expense = shortExpense,
                    settings = AppSettingsEntity(workspaceId = "ws-1"),
                    onBack = {},
                    onEdit = {},
                    onDelete = {},
                    paymentMethod = "UPI / GPay / PhonePe"
                )
            }
        }
        compose.onNodeWithTag("expense_payment_method_badge").assertExists()
        compose.onNodeWithText("UPI / GPay / PhonePe").assertExists()
        compose.onNodeWithTag("expense_record_identity").assertTextEquals("42")
    }

    @Test
    fun expensesTabNotifiesSubViewActivationForConsolidatedHeader() {
        val subViewActive = mutableStateOf(false)
        val owner = WorkspaceMember(uid = "uid-owner", workspaceId = "ws-1", role = "OWNER", status = "ACTIVE", displayName = "Muniyappan")
        compose.setContent {
            MyApplicationTheme {
                ExpensesTab(
                    settings = AppSettingsEntity(workspaceId = "ws-1"),
                    expenses = listOf(sampleExpense),
                    tractors = emptyList(),
                    partners = emptyList(),
                    onAddExpense = {},
                    onUpdateExpense = {},
                    onDeleteExpense = {},
                    isOwner = true,
                    currentUid = "uid-owner",
                    workspaceMembers = listOf(owner),
                    onSubViewActiveChanged = { subViewActive.value = it }
                )
            }
        }
        compose.runOnIdle { assertFalse(subViewActive.value) }
        compose.onNodeWithTag("expense_report_list").performScrollToNode(hasTestTag("expense_item_${sampleExpense.id}"))
        compose.onNodeWithTag("expense_item_${sampleExpense.id}").performClick()
        compose.runOnIdle { assertTrue(subViewActive.value) }
        compose.onNodeWithTag("btn_back_expense_details").performClick()
        compose.runOnIdle { assertFalse(subViewActive.value) }
    }

    @Test
    fun expenseCardAndDetailsRenderWithoutHiddenFieldsAcrossWidthsFontScalesAndLanguages() {
        val widthState = mutableStateOf(320)
        val fontScaleState = mutableStateOf(1.0f)
        val languageState = mutableStateOf("EN")
        val showDetailsState = mutableStateOf(false)

        compose.setContent {
            MyApplicationTheme {
                val baseDensity = LocalDensity.current
                CompositionLocalProvider(
                    LocalDensity provides Density(baseDensity.density, fontScale = fontScaleState.value)
                ) {
                    Box(modifier = Modifier.width(widthState.value.dp)) {
                        if (!showDetailsState.value) {
                            ExpenseListItemRow(
                                expense = sampleExpense,
                                currency = "₹",
                                isTamil = languageState.value == "TA",
                                onClick = {},
                                onEdit = {},
                                onDelete = {},
                                onShare = {}
                            )
                        } else {
                            ExpenseDetailsScreen(
                                expense = sampleExpense,
                                settings = AppSettingsEntity(workspaceId = "ws-1", language = languageState.value),
                                onBack = {},
                                onEdit = {},
                                onDelete = {}
                            )
                        }
                    }
                }
            }
        }

        val widths = listOf(320, 360, 393, 412, 480)
        val scales = listOf(1.0f, 1.15f, 1.30f)
        val languages = listOf("EN", "TA")

        for (lang in languages) {
            for (width in widths) {
                for (scale in scales) {
                    compose.runOnIdle {
                        languageState.value = lang
                        widthState.value = width
                        fontScaleState.value = scale
                        showDetailsState.value = false
                    }
                    compose.waitForIdle()
                    compose.onNodeWithTag("expense_item_${sampleExpense.id}").assertIsDisplayed()
                    compose.onNodeWithText("₹5,400").assertIsDisplayed()
                    compose.onNodeWithText(if (lang == "TA") "பகிரப்பட்ட செலவு" else "Shared expense").assertIsDisplayed()
                    compose.onNodeWithText(if (lang == "TA") "நவ" else "27 Nov 2026, 07:10 AM", substring = true).assertIsDisplayed()

                    compose.runOnIdle { showDetailsState.value = true }
                    compose.waitForIdle()
                    compose.onNodeWithText(if (lang == "TA") "செலவு விவரங்கள்" else "Expense Details").assertIsDisplayed()
                    compose.onNodeWithTag("expense_record_identity").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithText("Muniyappan").performScrollTo().assertIsDisplayed()
                    compose.onNodeWithTag("btn_delete_expense_details").performScrollTo().assertIsDisplayed()
                }
            }
        }
    }
}
