package com.example

import com.example.data.entity.*
import com.example.data.firebase.WorkspaceMember
import com.example.ui.util.*
import org.junit.Assert.*
import org.junit.Test

class BusinessOverviewAttributionTest {
    private fun job(id: Long = 1, beneficiary: String = "partner", received: Double = 40.0) = JobEntryEntity(
        id = id, workspaceId = "a", customerName = "Synthetic", operatorName = "Same display name",
        tractorLabel = "T", workType = "Work", startTimeMillis = 1000, endTimeMillis = 2000,
        durationMinutes = 1, totalAmount = 100.0, amountReceived = received, pendingAmount = 100.0 - received,
        addedByPartner = "Owner recorder", createdByUid = "owner", createdAt = 1000,
        entryForUid = beneficiary, financialAttributionUid = beneficiary)
    private fun member(uid: String, name: String) = WorkspaceMember(uid = uid, workspaceId = "a", role = "PARTNER", status = "ACTIVE", displayName = name)
    private fun cached(uid: String, name: String) = PartnerEntity(workspaceId = "a", name = name, phone = "", firebaseUid = uid)
    private fun rows(jobs: List<JobEntryEntity>, members: List<WorkspaceMember> = emptyList(), partners: List<PartnerEntity> = emptyList()) =
        FinancialCalculationEngine.calculatePartnerBreakdown(jobs, emptyList(), emptyList(), partners, members,
            FinancialScope(workspaceId = "a"), preserveUnresolvedUids = true)

    @Test fun unavailableMemberListDoesNotCollapseDifferentRecordedUids() {
        val result = rows(listOf(job(), job(2, "other", 70.0)))
        assertEquals(setOf("partner", "other"), result.map { it.partnerUid }.toSet())
        assertEquals(200.0, result.sumOf { it.recorded }, 0.0)
        assertEquals(110.0, result.sumOf { it.received }, 0.0)
        assertEquals(90.0, result.sumOf { it.due }, 0.0)
        assertTrue(result.all { it.partnerName == "Member details unavailable" && it.role.isBlank() })
    }
    @Test fun uidBoundCachedNamesWorkOfflineWithoutGrantingARole() {
        val result = rows(listOf(job()), partners = listOf(cached("partner", "Partner Name"), cached("other", "Owner recorder")))
        assertEquals("partner", result.single().partnerUid)
        assertEquals("Partner Name", result.single().partnerName)
        assertEquals("", result.single().role)
        assertEquals(40.0, result.single().received, 0.0)
        assertEquals(60.0, result.single().due, 0.0)
    }
    @Test fun ownerRecordingForPartnerIsNotOwnerFinancialAttribution() {
        val result = rows(listOf(job()), listOf(member("owner", "Owner"), member("partner", "Partner")))
        assertEquals("partner", result.single().partnerUid)
        assertEquals("Partner", result.single().partnerName)
        assertEquals(100.0, result.single().recorded, 0.0)
        assertFalse(result.any { it.partnerUid == "owner" })
    }
    @Test fun absentBeneficiaryIsNeverInferredFromCreatorOrDisplayName() {
        val result = rows(listOf(job(beneficiary = "")), listOf(member("owner", "Owner recorder")))
        assertEquals("", result.single().partnerUid)
        assertEquals("Attribution unavailable", result.single().partnerName)
        assertEquals(100.0, result.single().recorded, 0.0)
    }
    @Test fun foreignNamesAndCollidingRecordsCannotLeakIntoSelectedWorkspace() {
        val result = rows(listOf(job(), job().copy(workspaceId = "b", totalAmount = 999.0)),
            listOf(member("partner", "Foreign Member").copy(workspaceId = "b")),
            listOf(cached("partner", "Foreign Partner").copy(workspaceId = "b")))
        assertEquals("Member details unavailable", result.single().partnerName)
        assertEquals(100.0, result.single().recorded, 0.0)
        assertEquals("", result.single().role)
    }
    @Test fun ambiguousCachedNameDoesNotChooseAnArbitraryPerson() {
        val result = rows(listOf(job()), partners = listOf(cached("partner", "One"), cached("partner", "Two")))
        assertEquals("Member details unavailable", result.single().partnerName)
        assertEquals("partner", result.single().partnerUid)
    }
    @Test fun membershipRefreshChangesLabelsWithoutChangingIdentityOrTotals() {
        val jobs = listOf(job(), job(2, "other", 70.0))
        val offline = rows(jobs)
        val online = rows(jobs, listOf(member("partner", "Partner Name"), member("other", "Other Name")))
        assertEquals(offline.map { it.partnerUid }, online.map { it.partnerUid })
        assertEquals(offline.map { Triple(it.recorded, it.received, it.due) }, online.map { Triple(it.recorded, it.received, it.due) })
        assertEquals(setOf("Partner Name", "Other Name"), online.map { it.partnerName }.toSet())
    }
    @Test fun defaultCalculationStillRequiresMemberMapping() {
        val result = FinancialCalculationEngine.calculatePartnerBreakdown(listOf(job()), emptyList(), emptyList(), emptyList(),
            emptyList(), FinancialScope(workspaceId = "a"))
        assertEquals("", result.single().partnerUid)
        assertEquals(100.0, result.single().recorded, 0.0)
    }
    @Test fun ownerPartnerOwnerSwitchKeepsOwnValuesAndZeroActivitySeparate() {
        val members = listOf(member("owner", "Owner").copy(role = "OWNER"), member("partner", "Partner"))
        val jobs = listOf(job(9007199254740995L, "owner", 80.0), job(2, "", 50.0))
        val breakdowns = rows(jobs, members)
        fun own(uid: String) = businessOverviewChartMembers(breakdowns, members, uid, "a").first()
        assertEquals("owner", own("owner").partnerUid)
        assertEquals(Triple(100.0, 80.0, 20.0), own("owner").let { Triple(it.recorded, it.received, it.due) })
        assertEquals("partner", own("partner").partnerUid)
        assertEquals(Triple(0.0, 0.0, 0.0), own("partner").let { Triple(it.recorded, it.received, it.due) })
        assertEquals(Triple(100.0, 80.0, 20.0), own("owner").let { Triple(it.recorded, it.received, it.due) })
        assertEquals(listOf(9007199254740995L), jobs.filter { it.entryForUid == "owner" }.map { it.id })
        assertEquals(50.0, breakdowns.single { it.partnerUid == "" }.received, 0.0)
    }
    @Test fun unpaidPartnerJobHasZeroCollectedAndActualDueRegardlessOfCreator() {
        val result = rows(listOf(job(9, "partner", 0.0)), listOf(member("owner", "Owner"), member("partner", "Partner")))
        assertEquals("partner", result.single().partnerUid)
        assertEquals(0.0, result.single().received, 0.0)
        assertEquals(100.0, result.single().due, 0.0)
    }
    @Test fun workspaceSwitchWithCollidingJobIdsDoesNotReusePriorAmounts() {
        val jobs = listOf(job(9, "partner", 40.0), job(9, "partner", 0.0).copy(workspaceId = "b", totalAmount = 700.0, pendingAmount = 700.0))
        val members = listOf(member("partner", "A"), member("partner", "B").copy(workspaceId = "b"))
        fun inWorkspace(ws: String) = FinancialCalculationEngine.calculatePartnerBreakdown(jobs, emptyList(), emptyList(), emptyList(), members, FinancialScope(workspaceId = ws), true).single()
        assertEquals(Triple(100.0, 40.0, 60.0), inWorkspace("a").let { Triple(it.recorded, it.received, it.due) })
        assertEquals(Triple(700.0, 0.0, 700.0), inWorkspace("b").let { Triple(it.recorded, it.received, it.due) })
        assertEquals("B", inWorkspace("b").partnerName)
        assertEquals(40.0, inWorkspace("a").received, 0.0)
    }

}
