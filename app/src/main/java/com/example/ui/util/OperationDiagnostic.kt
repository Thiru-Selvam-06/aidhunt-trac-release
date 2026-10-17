package com.example.ui.util

import com.example.data.entity.PendingOperationEntity

/** Presentation only: never retries, acknowledges or edits a durable operation. */
object OperationDiagnostic {
    data class Guidance(val category: String, val retryable: Boolean, val nextAction: String)
    fun guidance(action: PendingOperationEntity): Guidance {
        val reason = action.failureReason.uppercase()
        return when {
            action.state == "ACKNOWLEDGED" -> Guidance("Server confirmed", false, "No action needed.")
            action.state == "INVALIDATED" -> Guidance("Retained cancelled action", false, "Inspect the cancellation reason; this action will not upload.")
            "UPGRADE_REQUIRED" in reason || "PROTOCOL" in reason -> Guidance("Client compatibility", false, "Update the app; retain this original action for recovery.")
            ("ACKNOWLEDG" in reason || "ACK" in reason && ("MISMATCH" in reason || "MISSING" in reason || "INVALID" in reason)) -> Guidance("Backend acknowledgement", action.state == "PENDING", "No success was confirmed. A pending retry keeps the same operation ID and time so the backend can reconcile its receipt safely.")
            "PERMISSION_DENIED" in reason || "UNAUTHORIZED" in reason || "MEMBERSHIP" in reason -> Guidance("Server authorization", false, "Verify current membership, role, target and deployed backend policy. Owner status does not override dependencies.")
            "DEPENDENC" in reason || "BLOCKED BY" in reason || "ACTIVE COLLECTION" in reason -> Guidance("Dependency protection", false, "Resolve the named dependency before authorizing another attempt.")
            action.state == "REJECTED" -> Guidance("Rejected operation", false, "Review the exact error and retained payload; no automatic retry.")
            else -> Guidance("Pending synchronization", true, "Reconnect in the original account and workspace; retry preserves the operation ID and action time.")
        }
    }
}
