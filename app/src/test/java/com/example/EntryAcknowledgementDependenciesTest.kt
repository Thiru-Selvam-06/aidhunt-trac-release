package com.example

import com.example.data.entity.PendingOperationEntity
import com.example.data.sync.OperationDependencies
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EntryAcknowledgementDependenciesTest {
    private fun payment(state: String, reason: String = "", source: Long = 1) = PendingOperationEntity(
        operationId = "op", workspaceId = "a", actorUid = "actor", entityType = "PAYMENT", entityId = 2,
        mutationType = "UPSERT", sequence = 1, actionTime = 100, payloadJson = "{\"jobEntryId\":$source,\"customerId\":3}",
        state = state, failureReason = reason
    )
    @Test fun recoveredRejectionIsRetainedWithoutBlockingAnAcknowledgedSource() {
        val rejected = payment("REJECTED", "Confirmed state restored; PERMISSION_DENIED")
        assertFalse(OperationDependencies.blocksEntryAcknowledgement(rejected, 1))
        assertEquals("REJECTED", rejected.state)
        assertTrue(rejected.failureReason.contains("PERMISSION_DENIED"))
    }
    @Test fun pendingMutationAndIncompleteRecoveryStillBlockTheAffectedSource() {
        assertTrue(OperationDependencies.blocksEntryAcknowledgement(payment("PENDING"), 1))
        assertTrue(OperationDependencies.blocksEntryAcknowledgement(payment("REJECTED", "Local reconciliation pending"), 1))
        assertFalse(OperationDependencies.blocksEntryAcknowledgement(payment("PENDING", source = 4), 1))
    }
    @Test fun splitAllocationAlsoBlocksItsReferencedSource() {
        val split = payment("PENDING", source = 9).copy(payloadJson = "{\"jobEntryId\":9,\"allocationsJson\":\"[{\\\"jobId\\\":1,\\\"amount\\\":20}]\"}")
        assertTrue(OperationDependencies.blocksEntryAcknowledgement(split, 1))
        assertFalse(OperationDependencies.blocksEntryAcknowledgement(split.copy(state = "ACKNOWLEDGED"), 1))
    }
}
