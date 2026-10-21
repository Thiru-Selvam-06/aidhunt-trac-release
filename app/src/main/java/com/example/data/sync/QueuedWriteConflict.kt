package com.example.data.sync

import com.example.data.firebase.*
import org.json.JSONObject

/** Fail closed when an offline edit's original business state has changed on another device. */
object QueuedWriteConflict {
    fun requireUnchanged(type: String, expected: String?, current: Map<String, Any?>?, id: Long, ws: String) {
        // Pre-upgrade new creates can still upload; pre-upgrade edits cannot overwrite an existing row blindly.
        if (expected.isNullOrBlank() && current == null) return
        if (expected == "null") {
            require(current == null) { "Conflict: this record already exists; original action retained for review" }
            return
        }
        require(!expected.isNullOrBlank() && current != null) { "Conflict: original edit base is unavailable; action retained for review" }
        val base = JSONObject(expected)
        val normalized = when (type) {
            "JOB" -> jobEntryFromFirestoreMap(current, id, ws).toFirestoreMap()
            "EXPENSE" -> expenseFromFirestoreMap(current, id, ws).toFirestoreMap()
            else -> error("Unsupported conflict entity")
        }
        val changed = base.keys().asSequence().filter { it != "updatedAt" }.any { key ->
            val left = base.opt(key).takeUnless { it == JSONObject.NULL }
            val right = normalized[key]
            if (left is Number && right is Number) left.toString().toBigDecimal().compareTo(right.toString().toBigDecimal()) != 0
            else left != right
        }
        require(!changed) { "Conflict: confirmed record changed after this offline edit; original action retained for review" }
    }
}
