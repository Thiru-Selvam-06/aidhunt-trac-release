package com.example

import com.example.data.entity.PendingOperationEntity
import com.example.ui.util.RetainedOperationWarning
import org.junit.Assert.*
import org.junit.Test

class RetainedOperationWarningTest {
    private fun action(state: String = "REJECTED", reason: String = "") = PendingOperationEntity(
        operationId = "stable-op", workspaceId = "test-a", actorUid = "owner", entityType = "JOB",
        entityId = 1, mutationType = "UPSERT", sequence = 1, actionTime = 100,
        payloadJson = "private retained payload", state = state, failureReason = reason
    )
    @Test fun confirmedAndPendingWorkDoesNotAppearAsRejected() {
        assertEquals("", RetainedOperationWarning.from(listOf(action("ACKNOWLEDGED"), action("PENDING"))))
    }
    @Test fun authorizationErrorDoesNotExposeInternalRuleDiagnosticsOrPayload() {
        val op = action(reason = "Confirmed state restored; PERMISSION_DENIED: evaluation error at L198:26 for update; secret diagnostic")
        val warning = RetainedOperationWarning.from(listOf(op))
        assertTrue(warning.startsWith("1 action requires review."))
        assertTrue(warning.contains("workspace access"))
        assertFalse(warning.contains("L198"))
        assertFalse(warning.contains("private"))
        assertEquals("private retained payload", op.payloadJson)
        assertTrue(op.failureReason.contains("L198"))
    }
    @Test fun compatibilityErrorGivesUpgradeGuidance() {
        assertTrue(RetainedOperationWarning.from(listOf(action(reason = "UPGRADE_REQUIRED"))).contains("Update the app"))
    }
    @Test fun timeWindowErrorAndUnknownFailureHaveActionableGuidance() {
        assertTrue(RetainedOperationWarning.from(listOf(action(reason = "OUT_OF_WINDOW"))).contains("allowed time"))
        assertTrue(RetainedOperationWarning.from(listOf(action(), action())).contains("2 actions require review."))
        assertTrue(RetainedOperationWarning.from(listOf(action())).contains("Room SQLite & Cloud Push"))
    }
    @Test fun rejectedCollectionExplainsBackendAccessWithoutBlamingMembership() {
        val collection = action(reason = "PERMISSION_DENIED: Missing or insufficient permissions.")
            .copy(entityType = "PAYMENT")
        val warning = RetainedOperationWarning.from(listOf(collection))
        assertTrue(warning.contains("Collection"))
        assertTrue(warning.contains("deployed Rules"))
        assertFalse(warning.contains("Ask the Owner to review your workspace access"))
    }

    @Test fun rejectedExpenseDeleteDoesNotTellOwnerToAskOwner() {
        val expenseDelete = action(reason = "Confirmed state restored; PERMISSION_DENIED: Missing or insufficient permissions")
            .copy(entityType = "EXPENSE", mutationType = "DELETE")
        val warning = RetainedOperationWarning.from(listOf(expenseDelete), "OWNER")
        assertTrue(warning.contains("earlier Expense deletion"))
        assertTrue(warning.contains("remains"))
        assertFalse(warning.contains("Ask the Owner"))
    }

    @Test fun otherDeniedOwnerActionIdentifiesSpecificReviewPath() {
        val warning = RetainedOperationWarning.from(listOf(action(reason = "PERMISSION_DENIED")), "OWNER")
        assertTrue(warning.contains("previous action was denied"))
        assertFalse(warning.contains("Ask the Owner"))
    }
}
