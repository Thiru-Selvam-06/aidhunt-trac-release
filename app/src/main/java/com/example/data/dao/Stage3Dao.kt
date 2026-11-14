package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.BorrowingRequestEntity
import com.example.data.entity.ExternalLoanEntity
import com.example.data.entity.ExternalLoanRepaymentEntity
import com.example.data.entity.SettlementObligationEntity
import com.example.data.entity.SettlementRepaymentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface Stage3Dao {

    // External Loans
    @Query("SELECT * FROM external_loans WHERE workspaceId = :workspaceId ORDER BY loanDateMillis DESC, id DESC")
    fun observeExternalLoans(workspaceId: String): Flow<List<ExternalLoanEntity>>

    @Query("SELECT * FROM external_loans WHERE workspaceId = :workspaceId ORDER BY loanDateMillis DESC, id DESC")
    suspend fun getExternalLoansOnce(workspaceId: String): List<ExternalLoanEntity>

    @Query("SELECT * FROM external_loans WHERE workspaceId = :workspaceId AND id = :id LIMIT 1")
    suspend fun getExternalLoanById(workspaceId: String, id: Long): ExternalLoanEntity?

    @Query("SELECT * FROM external_loans WHERE workspaceId = :workspaceId AND isSynced = 0")
    suspend fun getUnsyncedExternalLoans(workspaceId: String): List<ExternalLoanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExternalLoan(loan: ExternalLoanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExternalLoans(loans: List<ExternalLoanEntity>)

    @Query("UPDATE external_loans SET isSynced = 1 WHERE workspaceId = :workspaceId AND id IN (:ids)")
    suspend fun markExternalLoansSynced(workspaceId: String, ids: List<Long>)

    @Query("DELETE FROM external_loans WHERE workspaceId = :workspaceId AND id = :id")
    suspend fun deleteExternalLoanById(workspaceId: String, id: Long)

    @Query("DELETE FROM external_loans WHERE workspaceId = :workspaceId AND isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedExternalLoansNotIn(workspaceId: String, validIds: List<Long>)

    @Query("DELETE FROM external_loans WHERE workspaceId = :workspaceId AND isSynced = 1")
    suspend fun deleteAllSyncedExternalLoans(workspaceId: String)

    // External Loan Repayments
    @Query("SELECT * FROM external_loan_repayments WHERE workspaceId = :workspaceId ORDER BY repaidAt DESC, id DESC")
    fun observeExternalLoanRepayments(workspaceId: String): Flow<List<ExternalLoanRepaymentEntity>>

    @Query("SELECT * FROM external_loan_repayments WHERE workspaceId = :workspaceId ORDER BY repaidAt DESC, id DESC")
    suspend fun getExternalLoanRepaymentsOnce(workspaceId: String): List<ExternalLoanRepaymentEntity>

    @Query("SELECT * FROM external_loan_repayments WHERE workspaceId = :workspaceId AND id = :id LIMIT 1")
    suspend fun getExternalLoanRepaymentById(workspaceId: String, id: Long): ExternalLoanRepaymentEntity?

    @Query("SELECT * FROM external_loan_repayments WHERE workspaceId = :workspaceId AND isSynced = 0")
    suspend fun getUnsyncedExternalLoanRepayments(workspaceId: String): List<ExternalLoanRepaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExternalLoanRepayment(repayment: ExternalLoanRepaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExternalLoanRepayments(repayments: List<ExternalLoanRepaymentEntity>)

    @Query("UPDATE external_loan_repayments SET isSynced = 1 WHERE workspaceId = :workspaceId AND id IN (:ids)")
    suspend fun markExternalLoanRepaymentsSynced(workspaceId: String, ids: List<Long>)

    @Query("DELETE FROM external_loan_repayments WHERE workspaceId = :workspaceId AND id = :id")
    suspend fun deleteExternalLoanRepaymentById(workspaceId: String, id: Long)

    @Query("DELETE FROM external_loan_repayments WHERE workspaceId = :workspaceId AND isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedExternalLoanRepaymentsNotIn(workspaceId: String, validIds: List<Long>)

    @Query("DELETE FROM external_loan_repayments WHERE workspaceId = :workspaceId AND isSynced = 1")
    suspend fun deleteAllSyncedExternalLoanRepayments(workspaceId: String)

    // Borrowing Requests
    @Query("SELECT * FROM borrowing_requests WHERE workspaceId = :workspaceId ORDER BY createdAt DESC, id DESC")
    fun observeBorrowingRequests(workspaceId: String): Flow<List<BorrowingRequestEntity>>

    @Query("SELECT * FROM borrowing_requests WHERE workspaceId = :workspaceId ORDER BY createdAt DESC, id DESC")
    suspend fun getBorrowingRequestsOnce(workspaceId: String): List<BorrowingRequestEntity>

    @Query("SELECT * FROM borrowing_requests WHERE workspaceId = :workspaceId AND id = :id LIMIT 1")
    suspend fun getBorrowingRequestById(workspaceId: String, id: Long): BorrowingRequestEntity?

    @Query("SELECT * FROM borrowing_requests WHERE workspaceId = :workspaceId AND isSynced = 0")
    suspend fun getUnsyncedBorrowingRequests(workspaceId: String): List<BorrowingRequestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBorrowingRequest(request: BorrowingRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBorrowingRequests(requests: List<BorrowingRequestEntity>)

    @Query("UPDATE borrowing_requests SET isSynced = 1 WHERE workspaceId = :workspaceId AND id IN (:ids)")
    suspend fun markBorrowingRequestsSynced(workspaceId: String, ids: List<Long>)

    @Query("DELETE FROM borrowing_requests WHERE workspaceId = :workspaceId AND id = :id")
    suspend fun deleteBorrowingRequestById(workspaceId: String, id: Long)

    @Query("DELETE FROM borrowing_requests WHERE workspaceId = :workspaceId AND isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedBorrowingRequestsNotIn(workspaceId: String, validIds: List<Long>)

    @Query("DELETE FROM borrowing_requests WHERE workspaceId = :workspaceId AND isSynced = 1")
    suspend fun deleteAllSyncedBorrowingRequests(workspaceId: String)

    // Settlement Obligations
    @Query("SELECT * FROM settlement_obligations WHERE workspaceId = :workspaceId ORDER BY createdAt DESC, id DESC")
    fun observeSettlementObligations(workspaceId: String): Flow<List<SettlementObligationEntity>>

    @Query("SELECT * FROM settlement_obligations WHERE workspaceId = :workspaceId ORDER BY createdAt DESC, id DESC")
    suspend fun getSettlementObligationsOnce(workspaceId: String): List<SettlementObligationEntity>

    @Query("SELECT * FROM settlement_obligations WHERE workspaceId = :workspaceId AND id = :id LIMIT 1")
    suspend fun getSettlementObligationById(workspaceId: String, id: Long): SettlementObligationEntity?

    @Query("SELECT * FROM settlement_obligations WHERE workspaceId = :workspaceId AND isSynced = 0")
    suspend fun getUnsyncedSettlementObligations(workspaceId: String): List<SettlementObligationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlementObligation(obligation: SettlementObligationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlementObligations(obligations: List<SettlementObligationEntity>)

    @Query("UPDATE settlement_obligations SET isSynced = 1 WHERE workspaceId = :workspaceId AND id IN (:ids)")
    suspend fun markSettlementObligationsSynced(workspaceId: String, ids: List<Long>)

    @Query("DELETE FROM settlement_obligations WHERE workspaceId = :workspaceId AND id = :id")
    suspend fun deleteSettlementObligationById(workspaceId: String, id: Long)

    @Query("DELETE FROM settlement_obligations WHERE workspaceId = :workspaceId AND isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedSettlementObligationsNotIn(workspaceId: String, validIds: List<Long>)

    @Query("DELETE FROM settlement_obligations WHERE workspaceId = :workspaceId AND isSynced = 1")
    suspend fun deleteAllSyncedSettlementObligations(workspaceId: String)

    // Settlement Repayments
    @Query("SELECT * FROM settlement_repayments WHERE workspaceId = :workspaceId ORDER BY createdAt DESC, id DESC")
    fun observeSettlementRepayments(workspaceId: String): Flow<List<SettlementRepaymentEntity>>

    @Query("SELECT * FROM settlement_repayments WHERE workspaceId = :workspaceId ORDER BY createdAt DESC, id DESC")
    suspend fun getSettlementRepaymentsOnce(workspaceId: String): List<SettlementRepaymentEntity>

    @Query("SELECT * FROM settlement_repayments WHERE workspaceId = :workspaceId AND id = :id LIMIT 1")
    suspend fun getSettlementRepaymentById(workspaceId: String, id: Long): SettlementRepaymentEntity?

    @Query("SELECT * FROM settlement_repayments WHERE workspaceId = :workspaceId AND isSynced = 0")
    suspend fun getUnsyncedSettlementRepayments(workspaceId: String): List<SettlementRepaymentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlementRepayment(repayment: SettlementRepaymentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSettlementRepayments(repayments: List<SettlementRepaymentEntity>)

    @Query("UPDATE settlement_repayments SET isSynced = 1 WHERE workspaceId = :workspaceId AND id IN (:ids)")
    suspend fun markSettlementRepaymentsSynced(workspaceId: String, ids: List<Long>)

    @Query("DELETE FROM settlement_repayments WHERE workspaceId = :workspaceId AND id = :id")
    suspend fun deleteSettlementRepaymentById(workspaceId: String, id: Long)

    @Query("DELETE FROM settlement_repayments WHERE workspaceId = :workspaceId AND isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedSettlementRepaymentsNotIn(workspaceId: String, validIds: List<Long>)

    @Query("DELETE FROM settlement_repayments WHERE workspaceId = :workspaceId AND isSynced = 1")
    suspend fun deleteAllSyncedSettlementRepayments(workspaceId: String)
}
