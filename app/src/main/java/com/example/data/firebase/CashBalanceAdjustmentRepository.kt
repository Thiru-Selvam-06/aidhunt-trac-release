package com.example.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** A cash reconciliation is not sales, a collection, an expense, or partner profit. */
data class CashBalanceEdit(val id: String, val workspaceId: String, val revision: Long,
    val beforePaise: Long, val afterPaise: Long, val deltaPaise: Long, val reason: String,
    val actorUid: String, val actorName: String, val confirmedAt: Long)

data class CashBalanceState(val workspaceId: String = "", val enabled: Boolean = false,
    val totalAdjustmentPaise: Long = 0, val revision: Long = 0, val loaded: Boolean = false,
    val confirmed: Boolean = false, val edits: List<CashBalanceEdit> = emptyList(), val error: String = "")

object CashBalanceAdjustmentPolicy {
    fun requireContext(workspace: String, actor: String, activeWorkspace: String?, activeActor: String?, verified: Pair<String, String>?) {
        require(workspace.isNotBlank() && actor.isNotBlank() && workspace == activeWorkspace && actor == activeActor &&
            verified == (actor to workspace)) { "Workspace or account changed. Refresh the original workspace before adjusting cash." }
    }
    fun connection(cloud: CloudConnectionState, localLoaded: Boolean, cash: CashBalanceState): CloudConnectionState = when {
        cash.enabled && cash.error.isNotBlank() -> CloudConnectionState.ERROR
        cloud == CloudConnectionState.CONNECTED && (!localLoaded || (cash.enabled && !cash.confirmed)) -> CloudConnectionState.CONNECTING
        else -> cloud
    }
    fun newTotal(basePaise: Long, targetPaise: Long): Long = Math.subtractExact(targetPaise, basePaise).also {
        require(it in -BalanceDisplayPolicy.MAX_PAISE..BalanceDisplayPolicy.MAX_PAISE) { "Adjustment is too large" }
    }
    fun delta(previousTotal: Long, newTotal: Long): Long = Math.subtractExact(newTotal, previousTotal).also {
        require(it != 0L) { "The balance has not changed" }
        require(it in -BalanceDisplayPolicy.MAX_PAISE..BalanceDisplayPolicy.MAX_PAISE) { "Adjustment is too large" }
    }
}

/** Online-only corrections. Firestore's existing persistent cache retains confirmed state offline.
 * Original financial records and retained outbox operations are never rewritten or acknowledged here.
 */
class CashBalanceAdjustmentRepository(private val db: FirebaseFirestore, private val auth: FirebaseAuth) {
    fun observe(workspaceId: String, actorUid: String) = callbackFlow {
        val ws = db.collection("workspaces").document(workspaceId)
        var state = CashBalanceState(workspaceId, enabled = true)
        var total: Long? = null; var revision: Long? = null
        var rows: List<CashBalanceEdit>? = null
        var currentConfirmed = false; var historyConfirmed = false
        var currentFailure: Exception? = null; var historyFailure: Exception? = null
        fun emit() {
            if (auth.currentUser?.uid != actorUid) return
            if (currentFailure != null || historyFailure != null) {
                state = state.copy(confirmed = false, error = "Cash balance access or history requires verification. No correction can be saved.")
                trySend(state); return
            }
            val history = rows
            val complete = history != null && total != null && revision != null &&
                history.size.toLong() == revision && history.sumOf { it.deltaPaise } == total &&
                history.map { it.revision }.sorted() == (1L..(revision ?: 0L)).toList()
            if (complete) state = state.copy(totalAdjustmentPaise = total!!, revision = revision!!,
                edits = history!!, loaded = true, confirmed = currentConfirmed && historyConfirmed, error = "")
            else state = state.copy(confirmed = false)
            trySend(state)
        }
        fun failure(e: Exception) {
            state = state.copy(confirmed = false, error = if (e is FirebaseFirestoreException &&
                e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                "Cash adjustment access was denied. Verify active membership and the deployed cash-adjustment rules. No balance was changed."
                else e.message.orEmpty())
            trySend(state)
        }
        val current = ws.collection("cashBalanceState").document("current")
            .addSnapshotListener(MetadataChanges.INCLUDE) { doc, error ->
                if (error != null) { currentFailure = error; currentConfirmed = false; failure(error) }
                else if (doc != null && doc.metadata.hasPendingWrites()) { currentConfirmed = false; emit() }
                else if (doc != null) {
                    // A cache miss is not proof that no adjustments exist on the server.
                    if (doc.exists() || !doc.metadata.isFromCache) {
                        total = doc.getLong("totalAdjustmentPaise") ?: 0L
                        revision = doc.getLong("revision") ?: 0L
                        currentFailure = null; currentConfirmed = !doc.metadata.isFromCache; emit()
                    }
                }
            }
        val history = ws.collection("cashBalanceAdjustments").addSnapshotListener(MetadataChanges.INCLUDE) { docs, error ->
            if (error != null) { historyFailure = error; historyConfirmed = false; failure(error) }
            else if (docs != null && docs.metadata.hasPendingWrites()) { historyConfirmed = false; emit() }
            else if (docs != null) {
                val parsed = runCatching { docs.documents.map { d ->
                    CashBalanceEdit(d.id, workspaceId, requireNotNull(d.getLong("revision")),
                        requireNotNull(d.getLong("beforePaise")), requireNotNull(d.getLong("afterPaise")),
                        requireNotNull(d.getLong("deltaPaise")), requireNotNull(d.getString("reason")),
                        requireNotNull(d.getString("actorUid")), d.getString("actorName").orEmpty(),
                        requireNotNull(d.getTimestamp("confirmedAt")).toDate().time)
                } }
                parsed.onSuccess { rows = it; historyFailure = null; historyConfirmed = !docs.metadata.isFromCache; emit() }
                    .onFailure {
                        val error = IllegalStateException("Cash adjustment history requires verification", it)
                        historyFailure = error; historyConfirmed = false; failure(error)
                    }
            }
        }
        awaitClose { current.remove(); history.remove() }
    }

    suspend fun readTotal(workspaceId: String, actorUid: String): Long {
        require(auth.currentUser?.uid == actorUid) { "Account changed" }
        val current = db.collection("workspaces").document(workspaceId).collection("cashBalanceState")
            .document("current").get().await()
        check(auth.currentUser?.uid == actorUid) { "Account changed" }
        check(!current.metadata.hasPendingWrites() && (current.exists() || !current.metadata.isFromCache)) {
            "Connect to verify the shared cash balance before withdrawing"
        }
        return current.getLong("totalAdjustmentPaise") ?: 0L
    }

    suspend fun save(workspaceId: String, actorUid: String, operationId: String, expectedRevision: Long,
        targetPaise: Long, basePaise: Long, rawReason: String) {
        require(auth.currentUser?.uid == actorUid && actorUid.isNotBlank()) { "Sign in again" }
        require(workspaceId.matches(Regex("ws_[A-Za-z0-9_-]{1,124}")))
        require(operationId.matches(Regex("[A-Za-z0-9-]{1,128}")))
        require(targetPaise in -BalanceDisplayPolicy.MAX_PAISE..BalanceDisplayPolicy.MAX_PAISE)
        require(basePaise in -BalanceDisplayPolicy.MAX_PAISE..BalanceDisplayPolicy.MAX_PAISE)
        val reason = BalanceDisplayPolicy.reason(rawReason)
        val ws = db.collection("workspaces").document(workspaceId)
        val current = ws.collection("cashBalanceState").document("current")
        val edit = ws.collection("cashBalanceAdjustments").document(operationId)
        db.runTransaction { tx ->
            check(auth.currentUser?.uid == actorUid) { "Account changed" }
            val workspace = tx.get(ws)
            val member = tx.get(ws.collection("members").document(actorUid))
            val previous = tx.get(current)
            val existing = tx.get(edit)
            val flags = workspace.get("syncCapabilities") as? Map<*, *> ?: emptyMap<Any, Any>()
            require(flags["cashBalanceAdjustmentsV1"] == true) { "Shared cash adjustments require activation after all partner apps are updated" }
            require(member.getString("status").equals("ACTIVE", true) && BalanceDisplayPolicy.canEdit(member.getString("role").orEmpty())) { "Active Owner or Partner required" }
            if (member.getString("role").equals("OWNER", true)) require(workspace.getString("ownerUid") == actorUid)
            if (existing.exists()) {
                require(existing.getString("actorUid") == actorUid && existing.getLong("afterPaise") == targetPaise &&
                    existing.getLong("basePaise") == basePaise && existing.getString("reason") == reason) { "Adjustment identity mismatch" }
            } else {
                val revision = previous.getLong("revision") ?: 0L
                check(revision == expectedRevision) { "Another person adjusted the balance. Refresh before saving." }
                val oldTotal = previous.getLong("totalAdjustmentPaise") ?: 0L
                val newTotal = CashBalanceAdjustmentPolicy.newTotal(basePaise, targetPaise)
                val delta = CashBalanceAdjustmentPolicy.delta(oldTotal, newTotal)
                val actionTime = System.currentTimeMillis()
                val atomic = flags["atomicCashControlV1"] == true
                val financialRef = AtomicCashControl.fsRef(db, workspaceId)
                // Read before writes. Existing atomic control must never be bypassed by an adjustment.
                if (atomic) {
                    val financial = tx.get(financialRef)
                    val cashBefore = financial.getDouble("confirmedCash") ?: throw FinancialBaselinePendingException()
                    check(AtomicCashControl.sameMoney(cashBefore, Math.addExact(basePaise, oldTotal) / 100.0)) {
                        "Confirmed cash changed. Refresh before adjusting the balance."
                    }
                    AtomicCashControl.writeCashStateAndReceipt(tx, financial, financialRef,
                        AtomicCashControl.cashOpRef(db, workspaceId, operationId), workspaceId, operationId,
                        "BALANCE_ADJUSTMENT", operationId, "UPSERT", actorUid, actionTime, delta / 100.0)
                }
                tx.set(current, mapOf("workspaceId" to workspaceId, "revision" to revision + 1,
                    "totalAdjustmentPaise" to newTotal, "operationId" to operationId,
                    "actorUid" to actorUid, "confirmedAt" to FieldValue.serverTimestamp()))
                tx.set(edit, mapOf("workspaceId" to workspaceId, "revision" to revision + 1, "operationId" to operationId,
                    "previousTotalPaise" to oldTotal, "totalAdjustmentPaise" to newTotal,
                    "basePaise" to basePaise, "beforePaise" to Math.addExact(basePaise, oldTotal),
                    "afterPaise" to targetPaise, "deltaPaise" to delta, "reason" to reason,
                    "actorUid" to actorUid, "actorName" to member.getString("displayName").orEmpty(),
                    "actionTime" to actionTime, "confirmedAt" to FieldValue.serverTimestamp()))
            }
            null
        }.await()
        check(auth.currentUser?.uid == actorUid) { "Account changed; refresh the workspace" }
    }
}
