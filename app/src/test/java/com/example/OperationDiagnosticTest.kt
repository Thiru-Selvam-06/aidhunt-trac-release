package com.example
import com.example.data.entity.PendingOperationEntity
import com.example.ui.util.OperationDiagnostic
import org.junit.Assert.*
import org.junit.Test
class OperationDiagnosticTest {
    private fun op(state: String, error: String = "") = PendingOperationEntity("stable", "original", "actor", "JOB", 9007199254740995L, "DELETE", 1, 100, "{}", state, 2, error)
    @Test fun permissionFailureIsNotSQLiteFailureOrAutomaticRetry() { val d=OperationDiagnostic.guidance(op("REJECTED", "PERMISSION_DENIED")); assertEquals("Server authorization",d.category); assertFalse(d.retryable) }
    @Test fun invalidatedActionIsNotClaimedAsSynced() { assertEquals("Retained cancelled action",OperationDiagnostic.guidance(op("INVALIDATED")).category) }
    @Test fun unknownRejectionRemainsActionableAndRetained() { val a=op("REJECTED", "unexpected"); assertFalse(OperationDiagnostic.guidance(a).retryable); assertEquals("stable",a.operationId); assertEquals("original",a.workspaceId) }
    @Test fun pendingOperationRetainsRetryIdentity() { assertTrue(OperationDiagnostic.guidance(op("PENDING", "UNAVAILABLE")).retryable) }
    @Test fun unconfirmedPendingAcknowledgementRemainsSafeToRetryWithoutClaimingSuccess() { val d=OperationDiagnostic.guidance(op("PENDING", "Deletion acknowledgement was not confirmed; retry safely")); assertEquals("Backend acknowledgement",d.category); assertTrue(d.retryable) }
    @Test fun upgradeAndMalformedAckRequireReview() { assertFalse(OperationDiagnostic.guidance(op("REJECTED","UPGRADE_REQUIRED")).retryable); assertEquals("Backend acknowledgement",OperationDiagnostic.guidance(op("PENDING","ACK_MISMATCH")).category) }
}
