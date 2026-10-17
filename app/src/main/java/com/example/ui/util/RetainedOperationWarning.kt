package com.example.ui.util

import com.example.data.entity.PendingOperationEntity

/** Short user guidance; the durable operation keeps the complete diagnostic and payload. */
object RetainedOperationWarning {
    fun from(operations: List<PendingOperationEntity>, role: String = ""): String {
        val rejected = operations.filter { it.state == "REJECTED" }
        if (rejected.isEmpty()) return ""
        val reasons = rejected.map { it.failureReason.uppercase() }
        val guidance = when {
            reasons.any { "UPGRADE_REQUIRED" in it || "PROTOCOL" in it } ->
                "Update the app before retrying these actions."
            reasons.any { "24" in it && ("HOUR" in it || "WINDOW" in it) || "ACTION_TIME" in it || "OUT_OF_WINDOW" in it } ->
                "An action was outside its allowed time. Ask the Owner to review it."
            rejected.any { it.entityType == "PAYMENT" && "PERMISSION_DENIED" in it.failureReason.uppercase() } ->
                "Collection access was denied. Check workspace membership and deployed Rules; retry only after access is corrected."
            rejected.any { it.entityType == "EXPENSE" && it.mutationType == "DELETE" &&
                "PERMISSION_DENIED" in it.failureReason.uppercase() } ->
                "An earlier Expense deletion was denied; that Expense remains in the original data. Review the retained action in Account > Room SQLite & Cloud Push."
            reasons.any { "PERMISSION_DENIED" in it || "UNAUTHORIZED" in it || "MEMBERSHIP" in it } ->
                if (role.equals("OWNER", ignoreCase = true))
                    "A previous action was denied. Review its specific failure in Account > Room SQLite & Cloud Push; your Owner role alone does not explain it."
                else "The server did not authorize an action. Ask the Owner to review your workspace access."
            else -> "Open Account > Room SQLite & Cloud Push to review the retained actions."
        }
        val count = rejected.size
        val collectionBlocked = rejected.all { it.entityType == "PAYMENT" && "PERMISSION_DENIED" in it.failureReason.uppercase() }
        val label = if (collectionBlocked) {
            if (count == 1) "1 collection action blocked." else "$count collection actions blocked."
        } else if (count == 1) "1 action requires review." else "$count actions require review."
        return "$label Their original data is retained. $guidance"
    }
}
