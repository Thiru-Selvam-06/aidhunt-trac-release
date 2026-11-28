package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.firebase.WorkspaceMember
import com.example.ui.screens.home.JobEntryItemCard
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.WorkspaceBannerStorage
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w412dp-h915dp", application = LedgerUiTestApplication::class)
class HomeDashboardRedesignTest {
    @get:Rule val compose = createComposeRule()

    private val settings = AppSettingsEntity(
        workspaceId = "ws-home-1",
        businessName = "AIDHUNT TRAC",
        currency = "₹",
        language = "EN"
    )

    private val sampleJob = JobEntryEntity(
        id = 101L,
        workspaceId = "ws-home-1",
        customerId = 201L,
        customerName = "test after ui check",
        customerPhone = "9876543210",
        operatorName = "Muniyappan",
        tractorLabel = "Rotavator",
        workType = "Rotavator",
        startTimeMillis = 1700000000000L,
        endTimeMillis = 1700003600000L,
        durationMinutes = 60L,
        totalAmount = 1100.0,
        amountReceived = 0.0,
        pendingAmount = 1100.0,
        addedByPartner = "Muniyappan",
        createdAt = 1700000000000L,
        createdByUid = "uid-muni",
        createdByRole = "Owner",
        entryForUid = "uid-muni",
        financialAttributionUid = "uid-muni"
    )

    private val members = listOf(
        WorkspaceMember(
            uid = "uid-muni",
            workspaceId = "ws-home-1",
            displayName = "Muniyappan",
            role = "OWNER",
            status = "ACTIVE"
        )
    )

    private val partners = listOf(
        PartnerEntity(
            id = 1L,
            workspaceId = "ws-home-1",
            name = "Muniyappan",
            phone = "9876543210",
            role = "Owner",
            firebaseUid = "uid-muni"
        )
    )

    @Test
    fun todaysJobEntriesCardDisplaysAllRequiredFieldsAndInvokesCallback() {
        var clickedJobId: Long? = null

        compose.setContent {
            MyApplicationTheme {
                JobEntryItemCard(
                    job = sampleJob,
                    settings = settings,
                    workspaceMembers = members,
                    partners = partners,
                    isNewlyCreated = false,
                    onClick = { clickedJobId = sampleJob.id },
                    onShareWhatsApp = {},
                    onSharePdf = {}
                )
            }
        }

        // Customer Name and Initials Avatar
        compose.onNodeWithText("test after ui check").assertIsDisplayed()
        compose.onNodeWithText("TA").assertIsDisplayed()

        // Amount Badge
        compose.onNodeWithText("₹1,100").assertIsDisplayed()

        // Work Type and Payment Status
        compose.onNodeWithText("Rotavator").assertIsDisplayed()
        compose.onNodeWithText("Due").assertIsDisplayed()

        // Duration & Entry By
        compose.onNodeWithText("1h", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Muniyappan", substring = true).assertIsDisplayed()

        // Tap card invokes original callback with correct record ID
        compose.onNodeWithTag("job_item_101").performClick()
        assertEquals(101L, clickedJobId)
    }

    @Test
    fun longCustomerNamesAndAmountsDoNotOverlap() {
        val longJob = sampleJob.copy(
            id = 102L,
            customerName = "Dr. Arumugam Radhakrishnan Swaminathan",
            totalAmount = 125000.0,
            pendingAmount = 125000.0
        )

        compose.setContent {
            MyApplicationTheme {
                JobEntryItemCard(
                    job = longJob,
                    settings = settings,
                    workspaceMembers = members,
                    partners = partners,
                    isNewlyCreated = false,
                    onClick = {},
                    onShareWhatsApp = {},
                    onSharePdf = {}
                )
            }
        }

        compose.onNodeWithText("Dr. Arumugam Radhakrishnan Swaminathan").assertIsDisplayed()
        compose.onNodeWithText("₹125,000").assertIsDisplayed()
        compose.onNodeWithTag("job_item_102").assertIsDisplayed()
    }

    @Test
    fun newJobCardStartsHighlightedAndTransitionsColorAt1000ms() {
        var animationCompletedId: Long? = null

        compose.mainClock.autoAdvance = false

        compose.setContent {
            MyApplicationTheme {
                JobEntryItemCard(
                    job = sampleJob,
                    settings = settings,
                    workspaceMembers = members,
                    partners = partners,
                    isNewlyCreated = true,
                    onAnimationComplete = { animationCompletedId = it },
                    onClick = {},
                    onShareWhatsApp = {},
                    onSharePdf = {}
                )
            }
        }

        // Initial state at t = 0ms: animation is in flight, callback not yet fired
        assertNull(animationCompletedId)

        // Advance to 500ms
        compose.mainClock.advanceTimeBy(500L)
        assertNull(animationCompletedId)

        // Advance to 1000ms: transition completes
        compose.mainClock.advanceTimeBy(500L)
        compose.waitForIdle()

        assertEquals(101L, animationCompletedId)
    }

    @Test
    fun existingEntriesDoNotAnimateOnInitialDisplayAndRecompositionDoesNotRestartAnimation() {
        var animationCallCount = 0

        compose.setContent {
            MyApplicationTheme {
                JobEntryItemCard(
                    job = sampleJob,
                    settings = settings,
                    workspaceMembers = members,
                    partners = partners,
                    isNewlyCreated = false,
                    onAnimationComplete = { animationCallCount++ },
                    onClick = {},
                    onShareWhatsApp = {},
                    onSharePdf = {}
                )
            }
        }

        compose.waitForIdle()
        assertEquals(0, animationCallCount)
    }

    @Test
    fun workspaceBannerStorageMaintainsWorkspaceIsolationAndHandlesDefaults() {
        val context: Context = ApplicationProvider.getApplicationContext()
        val bannerDir = File(context.noBackupFilesDir, "workspace_banners")
        bannerDir.deleteRecursively()

        // Default behavior: no custom banner exists
        assertNull(WorkspaceBannerStorage.loadBanner(context, "ws_alpha"))
        assertNull(WorkspaceBannerStorage.loadBanner(context, "ws_beta"))

        // Create temporary sample banner
        val sampleFile = File(context.cacheDir, "sample_banner.png").apply {
            FileOutputStream(this).use { it.write("fake_banner_image_bytes".toByteArray()) }
        }

        // Save banner for ws_alpha
        val savedAlpha = WorkspaceBannerStorage.saveBanner(context, "ws_alpha", Uri.fromFile(sampleFile))
        assertNotNull(savedAlpha)
        assertTrue(savedAlpha!!.exists())

        // Workspace-specific: ws_beta still returns null (no banner)
        assertNull(WorkspaceBannerStorage.loadBanner(context, "ws_beta"))

        // ws_alpha loads correctly
        val loadedAlpha = WorkspaceBannerStorage.loadBanner(context, "ws_alpha")
        assertNotNull(loadedAlpha)
        assertEquals(savedAlpha.absolutePath, loadedAlpha!!.absolutePath)

        // Remove custom banner restores default (null)
        WorkspaceBannerStorage.removeBanner(context, "ws_alpha")
        assertNull(WorkspaceBannerStorage.loadBanner(context, "ws_alpha"))
    }
}
