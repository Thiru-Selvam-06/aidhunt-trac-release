package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.data.entity.PendingOperationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingOperationDao {
    @Query("SELECT * FROM pending_operations WHERE state = 'PENDING' ORDER BY sequence")
    fun observePending(): Flow<List<PendingOperationEntity>>
    @Insert suspend fun insert(operation: PendingOperationEntity)
    @Query("SELECT * FROM pending_operations WHERE operationId = :id LIMIT 1")
    suspend fun getOperation(id: String): PendingOperationEntity?
    @Query("UPDATE pending_operations SET state = 'PENDING', failureReason = 'Manual retry after access check; previous: ' || failureReason WHERE operationId = :id AND state = 'REJECTED'")
    suspend fun requeueRejected(id: String): Int
    @Query("SELECT COALESCE(MAX(sequence), 0) + 1 FROM pending_operations") suspend fun nextSequence(): Long
    @Query("SELECT * FROM pending_operations WHERE workspaceId = :wsId AND actorUid = :uid AND state = 'PENDING' ORDER BY sequence LIMIT 20")
    suspend fun nextBatch(wsId: String, uid: String): List<PendingOperationEntity>
    @Query("SELECT * FROM pending_operations WHERE workspaceId = :wsId AND actorUid = :uid AND state = 'PENDING' AND sequence > :after ORDER BY sequence LIMIT 20")
    suspend fun pendingAfter(wsId: String, uid: String, after: Long): List<PendingOperationEntity>
    @Query("SELECT COUNT(*) FROM pending_operations WHERE workspaceId = :wsId AND entityType = :type AND entityId = :id AND sequence < :before AND state NOT IN ('INVALIDATED', 'ACKNOWLEDGED')")
    suspend fun unresolvedPredecessors(wsId: String, type: String, id: Long, before: Long): Int
    @Query("SELECT COUNT(*) FROM pending_operations WHERE workspaceId = :wsId AND entityType = :type AND entityId = :id AND sequence < :before AND state = 'REJECTED'")
    suspend fun rejectedPredecessors(wsId: String, type: String, id: Long, before: Long): Int
    @Query("UPDATE pending_operations SET failureReason = :reason WHERE operationId = :id AND state = 'PENDING'")
    suspend fun waiting(id: String, reason: String)
    @Query("SELECT * FROM pending_operations WHERE workspaceId = :wsId ORDER BY sequence")
    fun observe(wsId: String): Flow<List<PendingOperationEntity>>
    @Query("SELECT * FROM pending_operations WHERE workspaceId = :wsId AND actorUid = :uid AND state != 'ACKNOWLEDGED' ORDER BY sequence")
    fun observeActor(wsId: String, uid: String): Flow<List<PendingOperationEntity>>
    @Query("UPDATE pending_operations SET attempts = attempts + 1, state = :state, failureReason = :reason WHERE operationId = :id")
    suspend fun failed(id: String, state: String, reason: String)
    @Query("UPDATE pending_operations SET state = 'ACKNOWLEDGED', failureReason = '' WHERE operationId = :id") suspend fun acknowledge(id: String)
    @Query("SELECT COUNT(*) FROM pending_operations WHERE workspaceId = :wsId AND actorUid = :uid AND state = 'REJECTED'")
    suspend fun rejectedCount(wsId: String, uid: String): Int
    @Query("SELECT COUNT(*) FROM pending_operations WHERE workspaceId = :wsId AND entityType = :type AND entityId = :entityId AND state NOT IN ('ACKNOWLEDGED', 'INVALIDATED')")
    suspend fun outstanding(wsId: String, type: String, entityId: Long): Int
    @Query("SELECT * FROM pending_operations WHERE workspaceId = :wsId AND state NOT IN ('INVALIDATED', 'ACKNOWLEDGED') ORDER BY sequence")
    suspend fun outstandingOperations(wsId: String): List<PendingOperationEntity>
    @Query("UPDATE pending_operations SET failureReason = :reason WHERE operationId = :id AND state = 'REJECTED'")
    suspend fun waitingRecovery(id: String, reason: String)
    @Query("SELECT * FROM pending_operations WHERE workspaceId = :wsId AND actorUid = :uid AND entityType = :type AND entityId = :id AND state = 'ACKNOWLEDGED' AND sequence < :before ORDER BY sequence DESC LIMIT 1")
    suspend fun lastAcknowledged(wsId: String, uid: String, type: String, id: Long, before: Long): PendingOperationEntity?
    @Query("UPDATE pending_operations SET state = 'INVALIDATED', failureReason = failureReason || '; Superseded by a fresh authorized action' WHERE workspaceId = :wsId AND actorUid = :uid AND entityType = :type AND entityId = :id AND state = 'REJECTED' AND failureReason LIKE 'Confirmed state restored; %' AND NOT EXISTS (SELECT 1 FROM pending_operations p WHERE p.workspaceId = :wsId AND p.entityType = :type AND p.entityId = :id AND p.state = 'PENDING')")
    suspend fun supersedeReconciledRejections(wsId: String, uid: String, type: String, id: Long)
    @Query("UPDATE pending_operations SET state = 'INVALIDATED', failureReason = failureReason || '; Linked Expense deleted by confirmed Entry transaction' WHERE workspaceId = :wsId AND entityType = 'EXPENSE' AND entityId = :id AND mutationType = 'DELETE' AND state = 'REJECTED'")
    suspend fun supersedeExpenseDeleteAfterEntryDelete(wsId: String, id: Long)
    @Query("UPDATE pending_operations SET state = 'INVALIDATED', failureReason = failureReason || '; Record confirmed absent in Cloud and Room' WHERE operationId = :operationId AND workspaceId = :wsId AND entityType = 'EXPENSE' AND mutationType = 'DELETE' AND state = 'REJECTED'")
    suspend fun resolveAbsentExpenseDelete(operationId: String, wsId: String): Int
    @Query("UPDATE pending_operations SET state = 'INVALIDATED', failureReason = 'Deletion retried after reconciliation' WHERE workspaceId = :wsId AND actorUid = :uid AND entityType = 'JOB' AND entityId = :id AND mutationType = 'DELETE' AND state = 'REJECTED'")
    suspend fun invalidateRejectedEntryDelete(wsId: String, uid: String, id: Long)
    @Query("UPDATE pending_operations SET state = 'INVALIDATED', failureReason = 'Member permanently deleted' WHERE workspaceId = :wsId AND actorUid = :uid")
    suspend fun invalidateActor(wsId: String, uid: String)
    @Query("UPDATE pending_operations SET state = 'INVALIDATED', failureReason = 'Record permanently deleted' WHERE workspaceId = :wsId AND entityType = :type AND entityId = :id")
    suspend fun invalidateEntity(wsId: String, type: String, id: Long)
}
