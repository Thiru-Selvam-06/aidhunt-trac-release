package com.example

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthorizationManager
import com.example.data.auth.CollectionPolicy
import com.example.data.auth.RoleUtils
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
import com.example.data.firebase.WorkspaceMember
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.report.CollectionHistoryTab
import com.example.ui.screens.report.duesFilterPartners
import com.example.ui.screens.report.getEligibleProfitShareParticipants
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.util.hasBusinessPartnershipHistory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w412dp-h915dp")
class DurablePartnerLabelUiTest {
    @get:Rule val compose = createComposeRule()

    private val wsId = "ws_fresh_test"
    private val owner = WorkspaceMember(uid = "owner_uid", workspaceId = wsId, role = "OWNER", status = "ACTIVE", displayName = "Ravi")
    private val activePartner = WorkspaceMember(uid = "partner_uid", workspaceId = wsId, role = "PARTNER", status = "ACTIVE", displayName = "Mani")
    private val removedPartner = activePartner.copy(status = "REMOVED")
    private val invitedPartner = activePartner.copy(uid = "invite_uid", status = "waiting_for_registration")
    private val operator = WorkspaceMember(uid = "op_uid", workspaceId = wsId, role = "OPERATOR", status = "ACTIVE", displayName = "Kumar")

    @Before
    fun clearFreshDevicePrefs() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("business_role_presentation", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun sampleJob(now: Long = System.currentTimeMillis()) = JobEntryEntity(
        id = 101L,
        workspaceId = wsId,
        customerId = 202L,
        customerName = "Murugan Farm",
        operatorName = "Ravi",
        tractorLabel = "TN01",
        workType = "Ploughing",
        startTimeMillis = now - 3600_000L,
        endTimeMillis = now,
        durationMinutes = 60L,
        totalAmount = 1200.0,
        amountReceived = 1200.0,
        pendingAmount = 0.0,
        addedByPartner = "Ravi",
        createdByUid = "owner_uid",
        createdByRole = "OWNER",
        entryForUid = "owner_uid",
        financialAttributionUid = "owner_uid",
        createdAt = now
    )

    @Test
    fun ownerDisplaysOwnerBeforeFirstPartnerJoinsIncludingPendingInvite() {
        assertFalse(hasBusinessPartnershipHistory(wsId, listOf(owner, operator)))
        assertFalse(hasBusinessPartnershipHistory(wsId, listOf(owner, invitedPartner, operator)))

        compose.setContent {
            MyApplicationTheme {
                HomeScreen(
                    settings = AppSettingsEntity(workspaceId = wsId, ownerName = "Ravi"),
                    recentJobs = listOf(sampleJob()),
                    isOwner = true,
                    currentUid = "owner_uid",
                    workspaceMembers = listOf(owner, invitedPartner, operator),
                    authenticatedUserRole = "OWNER",
                    onDeleteJob = {}
                )
            }
        }
        compose.onNodeWithText("Owner: Ravi").assertExists()
        compose.onAllNodesWithText("Owner", substring = true).assertCountEquals(2)
        compose.onNodeWithText("Entry by: Owner — Ravi").assertExists()
    }

    @Test
    fun freshDeviceShowsPartnerAfterLastPartnerRemovedWhilePreservingOwnerPermissionsAndExcludingRemoved() {
        val membersAfterRemoval = listOf(owner, removedPartner, operator)
        assertTrue(hasBusinessPartnershipHistory(wsId, membersAfterRemoval))

        // RoleUtils helpers reflect Partner display
        assertEquals("Entry by: Partner — Ravi", RoleUtils.getCreatedByDisplay(
            sampleJob(), "Ravi", membersAfterRemoval, isTamil = false, hasBusinessPartnership = true
        ))
        assertEquals("Entry for Operator: Partner — Ravi", RoleUtils.getEntryForDisplay(
            sampleJob(), "Ravi", membersAfterRemoval, isTamil = false, hasBusinessPartnership = true
        ))
        assertEquals("Edited by: Partner — Ravi", RoleUtils.getEditedByDisplay(
            sampleJob().copy(editedByName = "Ravi", editedByRole = "OWNER"), isTamil = false, hasBusinessPartnership = true
        ))

        // Owner administration permissions stay intact despite Partner label
        assertTrue(RoleUtils.isOwner(owner.role))
        assertTrue(AuthorizationManager.canCreateOldEntry(isOwner = true, role = "OWNER"))
        assertTrue(AuthorizationManager.canDeleteCustomer(isOwner = true))
        assertTrue(AuthorizationManager.canManagePartners(isOwner = true))

        // Active financial selectors exclude REMOVED partners and OPERATORS where required
        val shareParticipants = getEligibleProfitShareParticipants(
            membersAfterRemoval, emptyList(), AppSettingsEntity(workspaceId = wsId), isTamil = false
        )
        assertEquals(listOf("owner_uid"), shareParticipants.map { it.uid })
        assertEquals("Partner", shareParticipants.single().role)

        val filterPartners = duesFilterPartners(wsId, membersAfterRemoval)
        assertEquals(listOf("owner_uid"), filterPartners.map { it.uid })

        val collectors = CollectionPolicy.eligibleCollectors(wsId, "owner_uid", true, membersAfterRemoval)
        assertEquals(listOf("owner_uid"), collectors.map { it.uid })

        compose.setContent {
            MyApplicationTheme {
                HomeScreen(
                    settings = AppSettingsEntity(workspaceId = wsId, ownerName = "Ravi"),
                    recentJobs = listOf(sampleJob()),
                    isOwner = true,
                    currentUid = "owner_uid",
                    workspaceMembers = membersAfterRemoval,
                    authenticatedUserRole = "OWNER",
                    onDeleteJob = {}
                )
            }
        }
        compose.onNodeWithText("Partner: Ravi").assertExists()
        compose.onAllNodesWithText("Owner", substring = true).assertCountEquals(0)
        compose.onNodeWithText("Entry by: Partner — Ravi").assertExists()
    }

    @Test
    fun collectionHistoryDisplaysPartnerLabelForOwnerCollectorAfterLastPartnerLeavesInTamil() {
        val membersAfterRemoval = listOf(owner, removedPartner)
        val job = sampleJob()
        val payment = PaymentEntity(
            id = 55L,
            workspaceId = wsId,
            jobEntryId = job.id,
            customerId = job.customerId,
            customerName = job.customerName,
            amount = 500.0,
            collectedAt = job.createdAt,
            collectorUid = "owner_uid",
            collectorName = "Ravi",
            collectorRole = "OWNER",
            collectedByUid = "owner_uid",
            collectedByName = "Ravi",
            collectedByRole = "OWNER",
            status = "ACTIVE"
        )
        compose.setContent {
            MyApplicationTheme {
                CollectionHistoryTab(
                    settings = AppSettingsEntity(workspaceId = wsId, ownerName = "Ravi", language = "TA"),
                    payments = listOf(payment),
                    jobs = listOf(job),
                    workspaceMembers = membersAfterRemoval,
                    isOwner = true,
                    currentUid = "owner_uid",
                    ownerName = "Ravi"
                )
            }
        }
        compose.onNodeWithTag("expand_job_card_${job.id}").performClick()
        compose.onAllNodesWithText("பங்குதாரர் — Ravi", substring = true).assertCountEquals(3)
        compose.onAllNodesWithText("உரிமையாளர்", substring = true).assertCountEquals(0)
    }
}
