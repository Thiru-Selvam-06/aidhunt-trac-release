package com.example.data.sync

import androidx.room.withTransaction
import com.example.data.database.AppDatabase
import com.example.data.entity.PendingOperationEntity
import org.json.JSONObject
import kotlinx.coroutines.flow.first

/** Only call after the trusted server has acknowledged this exact deletion.
 * Provisional reversals stay recoverable until then; receipts retain the audit.
 */
object ConfirmedEntryDeletion {
    suspend fun apply(db: AppDatabase, op: PendingOperationEntity, allowCustomerCleanup: Boolean = false) = db.withTransaction {
        require(op.entityType == "JOB" && op.mutationType == "DELETE")
        require(db.pendingOperationDao().getOperation(op.operationId)?.state == "ACKNOWLEDGED") {
            "Entry deletion must be acknowledged before final local cleanup"
        }
        val source = JSONObject(op.payloadJson)
        require(source.getString("workspaceId") == op.workspaceId)
        val pending = db.pendingOperationDao().outstandingOperations(op.workspaceId)
        val newer = pending.any { it.entityType == "JOB" && it.entityId == op.entityId &&
            it.mutationType == "UPSERT" && it.state == "PENDING" && it.sequence > op.sequence }
        if (newer) return@withTransaction
        db.jobEntryDao().getJobById(op.workspaceId, op.entityId)?.takeIf {
            it.createdAt == source.optLong("createdAt")
        }?.let { db.jobEntryDao().deleteJob(it) }
        val collectionIds = source.optJSONArray("linkedCollectionIds")
        if (collectionIds != null) for (index in 0 until collectionIds.length()) {
            val id = collectionIds.getString(index).toLong()
            db.paymentDao().getPaymentById(op.workspaceId, id)?.takeIf {
                EntryDeletionPolicy.references(it, op.entityId)
            }?.let { db.paymentDao().deletePayment(it) }
        }
        val customerId = source.getLong("customerId")
        val hasJobs = db.jobEntryDao().getJobsForWorkspaceOnce(op.workspaceId).any { it.customerId == customerId }
        val hasCollections = db.paymentDao().getPaymentsForWorkspace(op.workspaceId).first().any {
            it.customerId == customerId || EntryDeletionPolicy.references(it, op.entityId)
        }
        val hasPending = pending.any { it.operationId != op.operationId &&
            (OperationDependencies.blocksCustomerAcknowledgement(it, customerId) ||
                (it.entityType == "CUSTOMER" && it.entityId == customerId)) }
        if (allowCustomerCleanup && !hasJobs && !hasCollections && !hasPending) {
            db.customerDao().getCustomerById(op.workspaceId, customerId)?.let { db.customerDao().deleteCustomer(it) }
        }
    }
}
