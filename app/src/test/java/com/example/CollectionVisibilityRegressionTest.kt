package com.example

import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
import com.example.data.firebase.WorkspaceMember
import com.example.ui.screens.report.authorizedCollectionSource
import org.junit.Assert.*
import org.junit.Test

class CollectionVisibilityRegressionTest {
    private val payment = PaymentEntity(id = 1L, workspaceId = "a", amount = 50.0, collectedAt = 100L)
    private fun source(member: WorkspaceMember) = authorizedCollectionSource(
        "a", listOf(payment, payment.copy(id = 2, workspaceId = "b")), emptyList(), listOf(member), false, "actor"
    )
    @Test fun partnerRetainsPreMembershipCollectionsButCannotSeeAnotherWorkspace() {
        val result = source(WorkspaceMember(uid = "actor", workspaceId = "a", role = "PARTNER", status = "ACTIVE", joinedAt = 200L))
        assertEquals(listOf(1L), result.payments.map { it.id })
        assertEquals(50.0, result.payments.sumOf { it.amount }, 0.001)
    }
    @Test fun inactiveOperatorUnknownAndWrongWorkspaceMembershipsHaveNoFinancialHistory() {
        val active = WorkspaceMember(uid = "actor", workspaceId = "a", role = "PARTNER", status = "ACTIVE")
        for (member in listOf(active.copy(status = "REMOVED"), active.copy(role = "OPERATOR"), active.copy(role = "UNKNOWN"), active.copy(workspaceId = "b"), active.copy(uid = "other"))) {
            assertTrue(source(member).payments.isEmpty())
            assertTrue(source(member).legacyJobs.isEmpty())
        }
    }
}
