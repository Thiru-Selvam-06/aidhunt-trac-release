package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.data.entity.*
import com.example.ui.screens.home.JobEntryItemCard
import com.example.ui.screens.report.ExpenseCategoryTileData
import com.example.ui.screens.report.ExpenseCategoryTileItem
import com.example.ui.screens.report.ExpenseListItemRow
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = LedgerUiTestApplication::class)
class ResponsiveLayoutUiTest {
    @get:Rule val compose = createComposeRule()

    private val sampleJob = JobEntryEntity(
        id = 801L,
        workspaceId = "ws-resp",
        customerId = 301L,
        customerName = "Thiru Selvam Farming Works",
        operatorName = "Muniyappan",
        tractorLabel = "4710 - PowerSteering",
        workType = "Rotavator",
        startTimeMillis = 1700000000000L,
        endTimeMillis = 1700003600000L,
        durationMinutes = 60L,
        totalAmount = 5400.0,
        amountReceived = 5400.0,
        pendingAmount = 0.0,
        addedByPartner = "Muniyappan",
        createdAt = 1700000000000L,
        createdByUid = "uid-1"
    )

    private val sampleExpense = ExpenseEntity(
        id = 802L,
        workspaceId = "ws-resp",
        expenseType = "Diesel",
        amount = 5400.0,
        tractorLabel = "4710",
        operatorName = "Muniyappan",
        description = "oil service and maintenance",
        addedByPartner = "Muniyappan",
        dateTimestamp = 1700000000000L,
        createdAt = 1700000000000L
    )

    private val sampleTile = ExpenseCategoryTileData(
        key = "Diesel",
        name = "Diesel",
        amount = 5400.0,
        percentage = 50
    )

    @Test
    fun jobEntryCardRendersCleanlyOnSmallDeviceWithHighFontScaleTamil() {
        val settings = AppSettingsEntity(workspaceId = "ws-resp", currency = "₹", language = "TA")

        compose.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 1.30f)
            ) {
                MyApplicationTheme {
                    Box(modifier = Modifier.width(320.dp)) {
                        JobEntryItemCard(
                            job = sampleJob,
                            settings = settings,
                            workspaceMembers = emptyList(),
                            partners = emptyList(),
                            isNewlyCreated = false,
                            onClick = {},
                            onShareWhatsApp = {},
                            onSharePdf = {}
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("job_item_801").assertIsDisplayed()
        compose.onNodeWithText("₹5,400").assertIsDisplayed()
    }

    @Test
    fun jobEntryCardRendersCleanlyOnStandardDeviceEnglish() {
        val settings = AppSettingsEntity(workspaceId = "ws-resp", currency = "₹", language = "EN")

        compose.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 1.0f)
            ) {
                MyApplicationTheme {
                    Box(modifier = Modifier.width(412.dp)) {
                        JobEntryItemCard(
                            job = sampleJob,
                            settings = settings,
                            workspaceMembers = emptyList(),
                            partners = emptyList(),
                            isNewlyCreated = false,
                            onClick = {},
                            onShareWhatsApp = {},
                            onSharePdf = {}
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("job_item_801").assertIsDisplayed()
        compose.onNodeWithText("₹5,400").assertIsDisplayed()
    }

    @Test
    fun expenseEntryCardRendersCleanlyOnSmallDeviceWithHighFontScaleTamil() {
        compose.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 1.30f)
            ) {
                MyApplicationTheme {
                    Box(modifier = Modifier.width(320.dp)) {
                        ExpenseListItemRow(
                            expense = sampleExpense,
                            currency = "₹",
                            isTamil = true,
                            onClick = {},
                            onEdit = {},
                            onDelete = {},
                            onShare = {},
                            canEdit = true,
                            canDelete = true
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("expense_item_802").assertIsDisplayed()
        compose.onNodeWithText("₹5,400").assertIsDisplayed()
    }

    @Test
    fun expenseEntryCardRendersCleanlyOnStandardDeviceEnglish() {
        compose.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 1.0f)
            ) {
                MyApplicationTheme {
                    Box(modifier = Modifier.width(412.dp)) {
                        ExpenseListItemRow(
                            expense = sampleExpense,
                            currency = "₹",
                            isTamil = false,
                            onClick = {},
                            onEdit = {},
                            onDelete = {},
                            onShare = {},
                            canEdit = true,
                            canDelete = true
                        )
                    }
                }
            }
        }

        compose.onNodeWithTag("expense_item_802").assertIsDisplayed()
        compose.onNodeWithText("₹5,400").assertIsDisplayed()
    }

    @Test
    fun expenseCategoryTileRendersCleanlyAcrossFontScales() {
        compose.setContent {
            val baseDensity = LocalDensity.current
            Column {
                CompositionLocalProvider(
                    LocalDensity provides Density(density = baseDensity.density, fontScale = 1.0f)
                ) {
                    MyApplicationTheme {
                        ExpenseCategoryTileItem(
                            tile = sampleTile,
                            tileWidth = 92.dp,
                            tileHeight = 96.dp,
                            isTamil = false,
                            currencySymbol = "₹"
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                CompositionLocalProvider(
                    LocalDensity provides Density(density = baseDensity.density, fontScale = 1.30f)
                ) {
                    MyApplicationTheme {
                        ExpenseCategoryTileItem(
                            tile = sampleTile,
                            tileWidth = 92.dp,
                            tileHeight = 96.dp,
                            isTamil = false,
                            currencySymbol = "₹"
                        )
                    }
                }
            }
        }

        compose.onAllNodesWithText("50%").assertCountEquals(2)
        compose.onAllNodesWithText("₹5,400", substring = true).assertCountEquals(2)
    }
}
