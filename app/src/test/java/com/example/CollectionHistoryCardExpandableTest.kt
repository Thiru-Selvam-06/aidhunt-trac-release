package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
import com.example.ui.screens.report.CollectionHistoryTab
import com.example.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class CollectionHistoryCardExpandableTest {
    @get:Rule val compose = createComposeRule()

    private val ws = "test_workspace"

    private fun sampleJob(id: Long, customer: String, total: Double, received: Double, pending: Double) =
        JobEntryEntity(
            id = id,
            workspaceId = ws,
            customerId = 100,
            customerName = customer,
            operatorName = "Operator",
            tractorLabel = "Tractor 1",
            workType = "Rotavator",
            startTimeMillis = 1000,
            endTimeMillis = 2000,
            durationMinutes = 60,
            totalAmount = total,
            amountReceived = received,
            pendingAmount = pending,
            addedByPartner = "Owner",
            createdAt = 1000
        )

    private fun samplePayment(id: Long, jobId: Long, customer: String, amount: Double, status: String = "ACTIVE") =
        PaymentEntity(
            id = id,
            workspaceId = ws,
            jobEntryId = jobId,
            customerId = 100,
            customerName = customer,
            amount = amount,
            collectedAt = 1500,
            collectedByName = "Muniyappan",
            collectedByRole = "Owner",
            status = status
        )

    @Test
    fun largeJobIdStaysInternalWhenHistoryIsExpanded() {
        val id = 9007199254740995L
        compose.setContent {
            MyApplicationTheme {
                CollectionHistoryTab(
                    settings = AppSettingsEntity(workspaceId = ws),
                    payments = listOf(samplePayment(33L, id, "Customer", 100.0)),
                    jobs = listOf(sampleJob(id, "Customer", 300.0, 100.0, 200.0)),
                    isOwner = true, currentUid = "owner_uid", onDeleteCollection = {}
                )
            }
        }
        compose.onNodeWithText("Job #$id").assertDoesNotExist()
        compose.onNodeWithTag("expand_job_card_$id").performClick()
        compose.onNodeWithTag("collection_history_card_33").assertExists()
        compose.onAllNodes(hasText(id.toString(), substring = true), useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag("collection_history_card_33").performScrollTo().performClick()
        compose.onNodeWithText("Collection Details").assertExists()
        compose.onAllNodes(hasText(id.toString(), substring = true), useUnmergedTree = true).assertCountEquals(0)
    }

    @Test
    fun cardStartsCollapsedAndShowsSummaryTotalsWithoutTimeline() {
        val job = sampleJob(101L, "Raman", 5000.0, 2000.0, 3000.0)
        val payment = samplePayment(1L, 101L, "Raman", 2000.0)

        compose.setContent {
            MyApplicationTheme {
                CollectionHistoryTab(
                    settings = AppSettingsEntity(workspaceId = ws),
                    payments = listOf(payment),
                    jobs = listOf(job),
                    isOwner = true,
                    currentUid = "owner_uid",
                    onDeleteCollection = {}
                )
            }
        }

        // Summary totals are visible in collapsed state
        compose.onNodeWithText("Raman").assertExists()
        compose.onNodeWithText("Job #101").assertDoesNotExist()
        compose.onNodeWithText("Rotavator").assertExists()
        compose.onNodeWithText("₹5,000").assertExists() // Billed
        compose.onAllNodesWithText("₹2,000").assertCountEquals(2) // Total Collections + Card Collected
        compose.onNodeWithText("₹3,000").assertExists() // Outstanding

        // Payment timeline card is hidden in collapsed state
        compose.onNodeWithTag("collection_history_card_1").assertDoesNotExist()

        // Expand control exists
        compose.onNodeWithTag("expand_job_card_101").assertExists()
    }

    @Test
    fun expandingCardRevealsTimelineAndCollapsingHidesIt() {
        val job = sampleJob(201L, "Kannan", 4000.0, 1500.0, 2500.0)
        val payment = samplePayment(2L, 201L, "Kannan", 1500.0)

        compose.setContent {
            MyApplicationTheme {
                CollectionHistoryTab(
                    settings = AppSettingsEntity(workspaceId = ws),
                    payments = listOf(payment),
                    jobs = listOf(job),
                    isOwner = true,
                    currentUid = "owner_uid",
                    onDeleteCollection = {}
                )
            }
        }

        // Initially collapsed: timeline card absent
        compose.onNodeWithTag("collection_history_card_2").assertDoesNotExist()

        // Click expand toggle
        compose.onNodeWithTag("expand_job_card_201").performClick()

        // Expanded: timeline card now exists with reversal option and payment details
        compose.onNodeWithTag("collection_history_card_2").assertExists()
        compose.onNodeWithTag("reverse_collection_2", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Payment timeline · oldest first").assertExists()

        // Click collapse toggle
        compose.onNodeWithTag("expand_job_card_201").performClick()

        // Collapsed again: timeline card hidden
        compose.onNodeWithTag("collection_history_card_2").assertDoesNotExist()
    }

    @Test
    fun multipleCardsMaintainIndependentExpandCollapseState() {
        val job1 = sampleJob(301L, "Customer Alpha", 3000.0, 1000.0, 2000.0)
        val payment1 = samplePayment(10L, 301L, "Customer Alpha", 1000.0)

        val job2 = sampleJob(302L, "Customer Beta", 6000.0, 2500.0, 3500.0)
        val payment2 = samplePayment(20L, 302L, "Customer Beta", 2500.0)

        compose.setContent {
            MyApplicationTheme {
                CollectionHistoryTab(
                    settings = AppSettingsEntity(workspaceId = ws),
                    payments = listOf(payment1, payment2),
                    jobs = listOf(job1, job2),
                    isOwner = true,
                    currentUid = "owner_uid",
                    onDeleteCollection = {}
                )
            }
        }

        // Both cards start collapsed
        compose.onNodeWithTag("collection_history_card_10").assertDoesNotExist()
        compose.onNodeWithTag("collection_history_card_20").assertDoesNotExist()

        // Expand only Card 1 (Alpha)
        compose.onNodeWithTag("expand_job_card_301").performScrollTo().performClick()

        // Card 1 is expanded, Card 2 remains strictly collapsed
        compose.onNodeWithTag("collection_history_card_10").assertExists()
        compose.onNodeWithTag("collection_history_card_20").assertDoesNotExist()

        // Now scroll to Card 2 (Beta) and expand it
        // Scroll LazyColumn to Card 2 and expand it
        compose.onNodeWithTag("collection_history_list").performScrollToNode(hasTestTag("expand_job_card_302"))
        compose.onNodeWithTag("expand_job_card_302").performClick()

        // Both cards are now expanded independently
        compose.onNodeWithTag("collection_history_card_10").assertExists()
        compose.onNodeWithTag("collection_history_card_20").assertExists()

        // Collapse Card 1
        compose.onNodeWithTag("collection_history_list").performScrollToNode(hasTestTag("expand_job_card_301"))
        compose.onNodeWithTag("expand_job_card_301").performClick()

        // Card 1 is collapsed, Card 2 remains expanded
        compose.onNodeWithTag("collection_history_card_10").assertDoesNotExist()
        compose.onNodeWithTag("collection_history_card_20").assertExists()
    }

    @Test
    fun longCustomerNameDoesNotOverflowOrBreakLayout() {
        val veryLongName = "Thiruvenkatachariar Radhakrishnan Srinivasa Raghavan Family Farm Estate"
        val job = sampleJob(401L, veryLongName, 10000.0, 5000.0, 5000.0)
        val payment = samplePayment(30L, 401L, veryLongName, 5000.0)

        compose.setContent {
            MyApplicationTheme {
                CollectionHistoryTab(
                    settings = AppSettingsEntity(workspaceId = ws),
                    payments = listOf(payment),
                    jobs = listOf(job),
                    isOwner = true,
                    currentUid = "owner_uid",
                    onDeleteCollection = {}
                )
            }
        }

        // Layout renders without crash
        compose.onNodeWithText(veryLongName).assertExists()
        compose.onNodeWithTag("expand_job_card_401").assertExists()
        compose.onNodeWithTag("expand_job_card_401").performClick()
        compose.onNodeWithTag("collection_history_card_30").assertExists()
    }
}
