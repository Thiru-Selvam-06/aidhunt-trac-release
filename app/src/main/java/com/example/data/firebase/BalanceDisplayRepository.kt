package com.example.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.math.BigDecimal
import java.math.RoundingMode

/** Presentation only. Never consumed by the financial engine, outbox or withdrawals. */
object BalanceDisplayPolicy {
    const val MAX_PAISE = 1_000_000_000_000L
    fun parsePaise(text: String): Long = text.trim().toBigDecimal().setScale(2, RoundingMode.UNNECESSARY)
        .movePointRight(2).longValueExact().also { require(it in -MAX_PAISE..MAX_PAISE) { "Amount is too large" } }
    fun reason(text: String): String = text.trim().also { require(it.isNotEmpty() && it.length <= 500) { "Enter a reason (1–500 characters)" } }
    fun canEdit(role: String) = role.uppercase() in setOf("OWNER", "PARTNER")
    fun amount(paise: Long) = BigDecimal.valueOf(paise, 2).toPlainString()
}

data class BalanceDisplayEdit(val id: String, val before: Long, val after: Long, val reason: String,
    val editorUid: String, val editorName: String, val editedAt: Long, val restored: Boolean)
data class BalanceDisplayState(val workspaceId: String = "", val overridePaise: Long? = null,
    val revision: Long = 0, val confirmed: Boolean = false, val history: List<BalanceDisplayEdit> = emptyList(), val error: String = "")

class BalanceDisplayRepository(private val db: FirebaseFirestore, private val auth: FirebaseAuth) {
    fun observe(workspaceId: String, actorUid: String) = callbackFlow {
        var state = BalanceDisplayState(workspaceId)
        val ws = db.collection("workspaces").document(workspaceId)
        fun emit() { if (auth.currentUser?.uid == actorUid) trySend(state) }
        val current = ws.collection("balanceDisplay").document("current").addSnapshotListener(MetadataChanges.INCLUDE) { doc, error ->
            state = if (error != null) state.copy(confirmed = false, error = error.message.orEmpty()) else state.copy(
                overridePaise = doc?.getLong("amountPaise"), revision = doc?.getLong("revision") ?: 0,
                confirmed = doc != null && !doc.metadata.isFromCache && !doc.metadata.hasPendingWrites(), error = "")
            emit()
        }
        val edits = ws.collection("balanceDisplayEdits").orderBy("revision", Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener { docs, error ->
                state = if (error != null) state.copy(error = error.message.orEmpty()) else state.copy(history = docs?.documents.orEmpty().mapNotNull { d ->
                    val at = d.getTimestamp("editedAt") ?: return@mapNotNull null
                    BalanceDisplayEdit(d.id, d.getLong("beforeDisplayedPaise") ?: return@mapNotNull null,
                        d.getLong("afterDisplayedPaise") ?: return@mapNotNull null, d.getString("reason").orEmpty(),
                        d.getString("editedByUid").orEmpty(), d.getString("editedByName").orEmpty(), at.toDate().time,
                        d.get("amountPaise") == null)
                })
                emit()
            }
        awaitClose { current.remove(); edits.remove() }
    }

    /** Online transaction: do not queue a stale absolute display value offline. */
    suspend fun save(workspaceId: String, actorUid: String, operationId: String, expectedRevision: Long,
        overridePaise: Long?, calculatedPaise: Long, rawReason: String) {
        require(auth.currentUser?.uid == actorUid && actorUid.isNotBlank()) { "Sign in again" }
        require(workspaceId.matches(Regex("ws_[A-Za-z0-9_-]{1,124}")))
        require(operationId.matches(Regex("[A-Za-z0-9-]{1,128}")))
        require(overridePaise == null || overridePaise in -BalanceDisplayPolicy.MAX_PAISE..BalanceDisplayPolicy.MAX_PAISE)
        require(calculatedPaise in -BalanceDisplayPolicy.MAX_PAISE..BalanceDisplayPolicy.MAX_PAISE)
        val reason = BalanceDisplayPolicy.reason(rawReason)
        val ws = db.collection("workspaces").document(workspaceId)
        val current = ws.collection("balanceDisplay").document("current")
        val edit = ws.collection("balanceDisplayEdits").document(operationId)
        db.runTransaction { tx ->
            check(auth.currentUser?.uid == actorUid) { "Account changed" }
            val member = tx.get(ws.collection("members").document(actorUid))
            val previous = tx.get(current)
            val existing = tx.get(edit)
            require(member.getString("status").equals("ACTIVE", true) && BalanceDisplayPolicy.canEdit(member.getString("role").orEmpty())) { "Active Owner or Partner required" }
            if (existing.exists()) {
                require(existing.getString("editedByUid") == actorUid && existing.getLong("amountPaise") == overridePaise && existing.getString("reason") == reason) { "Edit identity mismatch" }
            } else {
                val revision = previous.getLong("revision") ?: 0
                check(revision == expectedRevision) { "Another person edited this display balance. Refresh before saving." }
                val before = previous.getLong("amountPaise") ?: calculatedPaise
                val after = overridePaise ?: calculatedPaise
                val shared = mapOf("workspaceId" to workspaceId, "amountPaise" to overridePaise, "revision" to revision + 1,
                    "operationId" to operationId, "reason" to reason, "editedByUid" to actorUid,
                    "editedByName" to member.getString("displayName").orEmpty(), "editedAt" to FieldValue.serverTimestamp())
                tx.set(current, shared)
                tx.set(edit, shared + mapOf("previousOverridePaise" to previous.getLong("amountPaise"),
                    "beforeDisplayedPaise" to before, "afterDisplayedPaise" to after,
                    "calculatedPaiseAtEdit" to calculatedPaise, "differencePaise" to after - before))
            }
            null
        }.await()
        // Never acknowledge the display edit to a different signed-in account.
        check(auth.currentUser?.uid == actorUid) { "Account changed; refresh the workspace" }
    }
}
