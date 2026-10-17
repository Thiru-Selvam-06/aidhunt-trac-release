package com.example.data.sync

import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.entity.PendingOperationEntity
import com.example.data.firebase.jobEntryFromFirestoreMap
import com.example.data.firebase.expenseFromFirestoreMap
import com.example.data.firebase.paymentFromFirestoreMap
import org.json.JSONObject

/** Applies an explicit server result, never the rejected optimistic snapshot. */
object OfflineEntryRecovery {
    suspend fun restore(db: AppDatabase, op: PendingOperationEntity, confirmed: Map<String, Any?>?,
        contextMatches: () -> Boolean, recalculate: suspend (Long) -> Unit): Boolean = db.withTransaction {
        if (!contextMatches()) return@withTransaction false
        require(op.entityType == "JOB")
        require(confirmed == null || confirmed["workspaceId"] == op.workspaceId) { "Recovery workspace mismatch" }
        if (db.pendingOperationDao().outstandingOperations(op.workspaceId).any {
            it.entityType == "JOB" && it.entityId == op.entityId && it.sequence > op.sequence && it.state == "PENDING"
        }) return@withTransaction false
        val current = db.jobEntryDao().getJobById(op.workspaceId, op.entityId)
        val restored = confirmed?.let { jobEntryFromFirestoreMap(it, op.entityId, op.workspaceId) }
        if (restored != null) db.jobEntryDao().insertJob(restored)
        else if (current != null) db.jobEntryDao().deleteJob(current)
        if (op.mutationType == "DELETE" && restored != null) {
            val payload = JSONObject(op.payloadJson)
            val snapshots = payload.optJSONArray("linkedExpenseSnapshots")
            if (snapshots != null) for (index in 0 until snapshots.length()) {
                val snapshot = snapshots.getJSONObject(index)
                val id = snapshot.getString("id").toLong()
                require(snapshot.getString("workspaceId") == op.workspaceId && snapshot.getString("relatedJobId").toLong() == op.entityId) {
                    "Linked Expense recovery identity mismatch"
                }
                val fields = snapshot.keys().asSequence().associateWith { key -> snapshot.opt(key).takeUnless { it == JSONObject.NULL } }
                db.expenseDao().insertExpense(expenseFromFirestoreMap(fields, id, op.workspaceId).copy(isSynced = true))
            }
            val collections = payload.optJSONArray("linkedCollectionSnapshots")
            if (collections != null) for (index in 0 until collections.length()) {
                val snapshot = collections.getJSONObject(index)
                val id = snapshot.getString("id").toLong()
                require(snapshot.getString("workspaceId") == op.workspaceId &&
                    snapshot.getString("jobEntryId").toLong() == op.entityId && snapshot.getString("status") == "ACTIVE") {
                    "Linked Collection recovery identity mismatch"
                }
                val fields = snapshot.keys().asSequence().associateWith { key -> snapshot.opt(key).takeUnless { it == JSONObject.NULL } }
                db.paymentDao().insertPayment(paymentFromFirestoreMap(fields, id, op.workspaceId).copy(isSynced = true))
            }
        }
        listOfNotNull(current?.customerId, restored?.customerId).toSet().forEach { recalculate(it) }
        true
    }
}
