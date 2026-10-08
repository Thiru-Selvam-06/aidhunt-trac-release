package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.entity.ChecklistItemEntity
import com.example.data.entity.WorkTypeExtensionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PartnerDao {
    @Query("SELECT * FROM partners WHERE workspaceId = :workspaceId ORDER BY id ASC")
    fun getPartnersForWorkspace(workspaceId: String): Flow<List<PartnerEntity>>

    @Query("SELECT * FROM partners WHERE workspaceId IN (:workspaceIds) ORDER BY id ASC")
    fun getPartnersForWorkspaces(workspaceIds: List<String>): Flow<List<PartnerEntity>>

    @Query("SELECT * FROM partners WHERE workspaceId = :workspaceId ORDER BY id ASC")
    suspend fun getPartnersForWorkspaceOnce(workspaceId: String): List<PartnerEntity>

    @Query("SELECT * FROM partners WHERE workspaceId IN (:workspaceIds) ORDER BY id ASC")
    suspend fun getPartnersForWorkspacesOnce(workspaceIds: List<String>): List<PartnerEntity>

    @Query("SELECT * FROM partners ORDER BY id ASC")
    fun getAllPartners(): Flow<List<PartnerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPartner(partner: PartnerEntity): Long

    @Update
    suspend fun updatePartner(partner: PartnerEntity)

    @Delete
    suspend fun deletePartner(partner: PartnerEntity)

    @Query("DELETE FROM partners WHERE workspaceId = :workspaceId AND id NOT IN (:validIds)")
    suspend fun deleteNotIn(workspaceId: String, validIds: List<Long>)

    @Query("DELETE FROM partners WHERE id NOT IN (:validIds)")
    suspend fun deleteNotIn(validIds: List<Long>)

    @Query("DELETE FROM partners WHERE workspaceId = :workspaceId")
    suspend fun deleteAllForWorkspace(workspaceId: String)

    @Query("DELETE FROM partners WHERE workspaceId IN (:workspaceIds)")
    suspend fun deleteAllForWorkspaces(workspaceIds: List<String>)

    @Query("DELETE FROM partners")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM partners WHERE workspaceId = :workspaceId")
    suspend fun getCount(workspaceId: String): Int

    @Query("SELECT COUNT(*) FROM partners WHERE workspaceId IN (:workspaceIds)")
    suspend fun getCount(workspaceIds: List<String>): Int

    @Query("SELECT COUNT(*) FROM partners")
    suspend fun getCount(): Int
}

@Dao
interface TractorDao {
    @Query("SELECT * FROM tractors WHERE workspaceId = :workspaceId ORDER BY id ASC")
    fun getTractorsForWorkspace(workspaceId: String): Flow<List<TractorEntity>>

    @Query("SELECT * FROM tractors WHERE workspaceId IN (:workspaceIds) ORDER BY id ASC")
    fun getTractorsForWorkspaces(workspaceIds: List<String>): Flow<List<TractorEntity>>

    @Query("SELECT * FROM tractors WHERE workspaceId = :workspaceId ORDER BY id ASC")
    suspend fun getTractorsForWorkspaceOnce(workspaceId: String): List<TractorEntity>

    @Query("SELECT * FROM tractors WHERE workspaceId IN (:workspaceIds) ORDER BY id ASC")
    suspend fun getTractorsForWorkspacesOnce(workspaceIds: List<String>): List<TractorEntity>

    @Query("SELECT * FROM tractors ORDER BY id ASC")
    fun getAllTractors(): Flow<List<TractorEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTractor(tractor: TractorEntity): Long

    @Update
    suspend fun updateTractor(tractor: TractorEntity)

    @Delete
    suspend fun deleteTractor(tractor: TractorEntity)

    @Query("DELETE FROM tractors WHERE workspaceId = :workspaceId AND id NOT IN (:validIds)")
    suspend fun deleteNotIn(workspaceId: String, validIds: List<Long>)

    @Query("DELETE FROM tractors WHERE id NOT IN (:validIds)")
    suspend fun deleteNotIn(validIds: List<Long>)

    @Query("DELETE FROM tractors WHERE workspaceId = :workspaceId")
    suspend fun deleteAllForWorkspace(workspaceId: String)

    @Query("DELETE FROM tractors WHERE workspaceId IN (:workspaceIds)")
    suspend fun deleteAllForWorkspaces(workspaceIds: List<String>)

    @Query("DELETE FROM tractors")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM tractors WHERE workspaceId = :workspaceId")
    suspend fun getCount(workspaceId: String): Int

    @Query("SELECT COUNT(*) FROM tractors WHERE workspaceId IN (:workspaceIds)")
    suspend fun getCount(workspaceIds: List<String>): Int

    @Query("SELECT COUNT(*) FROM tractors")
    suspend fun getCount(): Int
}

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE workspaceId = :workspaceId ORDER BY updatedAt DESC")
    fun getCustomersForWorkspace(workspaceId: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE workspaceId IN (:workspaceIds) ORDER BY updatedAt DESC")
    fun getCustomersForWorkspaces(workspaceIds: List<String>): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE workspaceId = :workspaceId ORDER BY updatedAt DESC")
    suspend fun getCustomersForWorkspaceOnce(workspaceId: String): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE workspaceId IN (:workspaceIds) ORDER BY updatedAt DESC")
    suspend fun getCustomersForWorkspacesOnce(workspaceIds: List<String>): List<CustomerEntity>

    @Query("SELECT * FROM customers ORDER BY updatedAt DESC")
    fun getAllCustomers(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE workspaceId = :workspaceId AND balanceDue > 0 ORDER BY balanceDue DESC")
    fun getCustomersWithDueForWorkspace(workspaceId: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE workspaceId IN (:workspaceIds) AND balanceDue > 0 ORDER BY balanceDue DESC")
    fun getCustomersWithDueForWorkspaces(workspaceIds: List<String>): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE balanceDue > 0 ORDER BY balanceDue DESC")
    fun getCustomersWithDue(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE workspaceId = :workspaceId AND id = :id LIMIT 1")
    suspend fun getCustomerById(workspaceId: String, id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE workspaceId = :workspaceId AND (LOWER(name) LIKE '%' || LOWER(:query) || '%' OR phone LIKE '%' || :query || '%')")
    fun searchCustomers(workspaceId: String, query: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE workspaceId IN (:workspaceIds) AND (LOWER(name) LIKE '%' || LOWER(:query) || '%' OR phone LIKE '%' || :query || '%')")
    fun searchCustomers(workspaceIds: List<String>, query: String): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%' OR phone LIKE '%' || :query || '%'")
    fun searchCustomers(query: String): Flow<List<CustomerEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: CustomerEntity): Long

    @Update
    suspend fun updateCustomer(customer: CustomerEntity)

    @Delete
    suspend fun deleteCustomer(customer: CustomerEntity)

    @Query("DELETE FROM customers WHERE workspaceId = :workspaceId AND isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedNotIn(workspaceId: String, validIds: List<Long>)

    @Query("DELETE FROM customers WHERE isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedNotIn(validIds: List<Long>)

    @Query("DELETE FROM customers WHERE workspaceId = :workspaceId AND isSynced = 1")
    suspend fun deleteAllSyncedForWorkspace(workspaceId: String)

    @Query("DELETE FROM customers WHERE isSynced = 1")
    suspend fun deleteAllSynced()

    @Query("SELECT * FROM customers WHERE workspaceId = :workspaceId AND isSynced = 0")
    suspend fun getUnsyncedCustomersForWorkspace(workspaceId: String): List<CustomerEntity>

    @Query("SELECT * FROM customers WHERE isSynced = 0")
    suspend fun getUnsyncedCustomers(): List<CustomerEntity>

    @Query("UPDATE customers SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markCustomersSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM customers WHERE workspaceId = :workspaceId AND isSynced = 0")
    fun getUnsyncedCountForWorkspace(workspaceId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM customers WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>
}

@Dao
interface JobEntryDao {
    @Query("SELECT * FROM job_entries WHERE workspaceId = :workspaceId ORDER BY createdAt DESC")
    fun getJobsForWorkspace(workspaceId: String): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE workspaceId IN (:workspaceIds) ORDER BY createdAt DESC")
    fun getJobsForWorkspaces(workspaceIds: List<String>): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE workspaceId = :workspaceId ORDER BY createdAt DESC")
    suspend fun getJobsForWorkspaceOnce(workspaceId: String): List<JobEntryEntity>

    @Query("SELECT * FROM job_entries WHERE workspaceId IN (:workspaceIds) ORDER BY createdAt DESC")
    suspend fun getJobsForWorkspacesOnce(workspaceIds: List<String>): List<JobEntryEntity>

    @Query("SELECT * FROM job_entries WHERE id = :id LIMIT 1")
    suspend fun getJobById(id: Long): JobEntryEntity?

    @Query("SELECT * FROM job_entries ORDER BY createdAt DESC")
    fun getAllJobs(): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE workspaceId = :workspaceId AND customerId = :customerId ORDER BY startTimeMillis DESC")
    fun getJobsForCustomer(workspaceId: String, customerId: Long): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE workspaceId IN (:workspaceIds) AND customerId = :customerId ORDER BY startTimeMillis DESC")
    fun getJobsForCustomer(workspaceIds: List<String>, customerId: Long): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE customerId = :customerId ORDER BY startTimeMillis DESC")
    fun getJobsForCustomer(customerId: Long): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE workspaceId = :workspaceId AND (LOWER(customerName) LIKE '%' || LOWER(:query) || '%' OR LOWER(operatorName) LIKE '%' || LOWER(:query) || '%' OR LOWER(tractorLabel) LIKE '%' || LOWER(:query) || '%')")
    fun searchJobs(workspaceId: String, query: String): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE workspaceId IN (:workspaceIds) AND (LOWER(customerName) LIKE '%' || LOWER(:query) || '%' OR LOWER(operatorName) LIKE '%' || LOWER(:query) || '%' OR LOWER(tractorLabel) LIKE '%' || LOWER(:query) || '%')")
    fun searchJobsForWorkspaces(workspaceIds: List<String>, query: String): Flow<List<JobEntryEntity>>

    @Query("SELECT * FROM job_entries WHERE (LOWER(customerName) LIKE '%' || LOWER(:query) || '%' OR LOWER(operatorName) LIKE '%' || LOWER(:query) || '%' OR LOWER(tractorLabel) LIKE '%' || LOWER(:query) || '%')")
    fun searchJobs(query: String): Flow<List<JobEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: JobEntryEntity): Long

    @Update
    suspend fun updateJob(job: JobEntryEntity)

    @Delete
    suspend fun deleteJob(job: JobEntryEntity)

    @Query("DELETE FROM job_entries WHERE workspaceId = :workspaceId AND isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedNotIn(workspaceId: String, validIds: List<Long>)

    @Query("DELETE FROM job_entries WHERE isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedNotIn(validIds: List<Long>)

    @Query("DELETE FROM job_entries WHERE workspaceId = :workspaceId AND isSynced = 1")
    suspend fun deleteAllSyncedForWorkspace(workspaceId: String)

    @Query("DELETE FROM job_entries WHERE workspaceId IN (:workspaceIds) AND isSynced = 1")
    suspend fun deleteAllSyncedForWorkspaces(workspaceIds: List<String>)

    @Query("DELETE FROM job_entries WHERE isSynced = 1")
    suspend fun deleteAllSynced()

    @Query("SELECT SUM(amountReceived) FROM job_entries WHERE workspaceId = :workspaceId")
    fun getTotalReceivedForWorkspace(workspaceId: String): Flow<Double?>

    @Query("SELECT SUM(amountReceived) FROM job_entries WHERE workspaceId IN (:workspaceIds)")
    fun getTotalReceivedForWorkspaces(workspaceIds: List<String>): Flow<Double?>

    @Query("SELECT SUM(amountReceived) FROM job_entries")
    fun getTotalReceived(): Flow<Double?>

    @Query("SELECT SUM(CASE WHEN totalAmount > 0 THEN totalAmount ELSE (amountReceived + pendingAmount) END) FROM job_entries WHERE workspaceId = :workspaceId")
    fun getTotalRecordedForWorkspace(workspaceId: String): Flow<Double?>

    @Query("SELECT SUM(CASE WHEN totalAmount > 0 THEN totalAmount ELSE (amountReceived + pendingAmount) END) FROM job_entries WHERE workspaceId IN (:workspaceIds)")
    fun getTotalRecordedForWorkspaces(workspaceIds: List<String>): Flow<Double?>

    @Query("SELECT SUM(CASE WHEN totalAmount > 0 THEN totalAmount ELSE (amountReceived + pendingAmount) END) FROM job_entries")
    fun getTotalRecorded(): Flow<Double?>

    @Query("SELECT SUM(CASE WHEN pendingAmount > 0 THEN pendingAmount ELSE 0.0 END) FROM job_entries WHERE workspaceId = :workspaceId")
    fun getTotalPendingForWorkspace(workspaceId: String): Flow<Double?>

    @Query("SELECT SUM(CASE WHEN pendingAmount > 0 THEN pendingAmount ELSE 0.0 END) FROM job_entries WHERE workspaceId IN (:workspaceIds)")
    fun getTotalPendingForWorkspaces(workspaceIds: List<String>): Flow<Double?>

    @Query("SELECT SUM(CASE WHEN pendingAmount > 0 THEN pendingAmount ELSE 0.0 END) FROM job_entries")
    fun getTotalPending(): Flow<Double?>

    @Query("SELECT * FROM job_entries WHERE workspaceId = :workspaceId AND isSynced = 0")
    suspend fun getUnsyncedJobsForWorkspace(workspaceId: String): List<JobEntryEntity>

    @Query("SELECT * FROM job_entries WHERE isSynced = 0")
    suspend fun getUnsyncedJobs(): List<JobEntryEntity>

    @Query("UPDATE job_entries SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markJobsSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM job_entries WHERE workspaceId = :workspaceId AND isSynced = 0")
    fun getUnsyncedCountForWorkspace(workspaceId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM job_entries WHERE workspaceId IN (:workspaceIds) AND isSynced = 0")
    fun getUnsyncedCountForWorkspaces(workspaceIds: List<String>): Flow<Int>

    @Query("SELECT COUNT(*) FROM job_entries WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>
}

@Dao
interface ExpenseDao {
    @Query("SELECT * FROM expenses WHERE workspaceId = :workspaceId ORDER BY dateTimestamp DESC")
    fun getExpensesForWorkspace(workspaceId: String): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE workspaceId IN (:workspaceIds) ORDER BY dateTimestamp DESC")
    fun getExpensesForWorkspaces(workspaceIds: List<String>): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE workspaceId = :workspaceId ORDER BY dateTimestamp DESC")
    suspend fun getExpensesForWorkspaceOnce(workspaceId: String): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE workspaceId IN (:workspaceIds) ORDER BY dateTimestamp DESC")
    suspend fun getExpensesForWorkspacesOnce(workspaceIds: List<String>): List<ExpenseEntity>

    @Query("SELECT * FROM expenses ORDER BY dateTimestamp DESC")
    fun getAllExpenses(): Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE relatedJobId IN (SELECT id FROM job_entries WHERE customerId = :customerId) ORDER BY dateTimestamp DESC")
    suspend fun getExpensesForCustomer(customerId: Long): List<ExpenseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExpense(expense: ExpenseEntity): Long

    @Update
    suspend fun updateExpense(expense: ExpenseEntity)

    @Delete
    suspend fun deleteExpense(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE workspaceId = :workspaceId AND isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedNotIn(workspaceId: String, validIds: List<Long>)

    @Query("DELETE FROM expenses WHERE isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedNotIn(validIds: List<Long>)

    @Query("DELETE FROM expenses WHERE workspaceId = :workspaceId AND isSynced = 1")
    suspend fun deleteAllSyncedForWorkspace(workspaceId: String)

    @Query("DELETE FROM expenses WHERE workspaceId IN (:workspaceIds) AND isSynced = 1")
    suspend fun deleteAllSyncedForWorkspaces(workspaceIds: List<String>)

    @Query("DELETE FROM expenses WHERE isSynced = 1")
    suspend fun deleteAllSynced()

    @Query("SELECT * FROM expenses WHERE relatedJobId = :jobId")
    suspend fun getExpensesForJob(jobId: Long): List<ExpenseEntity>

    @Query("DELETE FROM expenses WHERE relatedJobId = :jobId")
    suspend fun deleteExpensesForJob(jobId: Long)

    @Query("SELECT SUM(amount) FROM expenses WHERE workspaceId = :workspaceId")
    fun getTotalExpensesForWorkspace(workspaceId: String): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses WHERE workspaceId IN (:workspaceIds)")
    fun getTotalExpensesForWorkspaces(workspaceIds: List<String>): Flow<Double?>

    @Query("SELECT SUM(amount) FROM expenses")
    fun getTotalExpenses(): Flow<Double?>

    @Query("SELECT * FROM expenses WHERE workspaceId = :workspaceId AND isSynced = 0")
    suspend fun getUnsyncedExpensesForWorkspace(workspaceId: String): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE isSynced = 0")
    suspend fun getUnsyncedExpenses(): List<ExpenseEntity>

    @Query("UPDATE expenses SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markExpensesSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM expenses WHERE workspaceId = :workspaceId AND isSynced = 0")
    fun getUnsyncedCountForWorkspace(workspaceId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM expenses WHERE workspaceId IN (:workspaceIds) AND isSynced = 0")
    fun getUnsyncedCountForWorkspaces(workspaceIds: List<String>): Flow<Int>

    @Query("SELECT COUNT(*) FROM expenses WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>
}

@Dao
interface WithdrawalDao {
    @Query("SELECT * FROM withdrawals WHERE workspaceId = :workspaceId ORDER BY timestamp DESC")
    fun getWithdrawalsForWorkspace(workspaceId: String): Flow<List<WithdrawalEntity>>

    @Query("SELECT * FROM withdrawals WHERE workspaceId IN (:workspaceIds) ORDER BY timestamp DESC")
    fun getWithdrawalsForWorkspaces(workspaceIds: List<String>): Flow<List<WithdrawalEntity>>

    @Query("SELECT * FROM withdrawals WHERE workspaceId = :workspaceId ORDER BY timestamp DESC")
    suspend fun getWithdrawalsForWorkspaceOnce(workspaceId: String): List<WithdrawalEntity>

    @Query("SELECT * FROM withdrawals WHERE workspaceId IN (:workspaceIds) ORDER BY timestamp DESC")
    suspend fun getWithdrawalsForWorkspacesOnce(workspaceIds: List<String>): List<WithdrawalEntity>

    @Query("SELECT * FROM withdrawals ORDER BY timestamp DESC")
    fun getAllWithdrawals(): Flow<List<WithdrawalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWithdrawal(withdrawal: WithdrawalEntity): Long

    @Update
    suspend fun updateWithdrawal(withdrawal: WithdrawalEntity)

    @Delete
    suspend fun deleteWithdrawal(withdrawal: WithdrawalEntity)

    @Query("DELETE FROM withdrawals WHERE workspaceId = :workspaceId AND isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedNotIn(workspaceId: String, validIds: List<Long>)

    @Query("DELETE FROM withdrawals WHERE isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedNotIn(validIds: List<Long>)

    @Query("DELETE FROM withdrawals WHERE workspaceId = :workspaceId AND isSynced = 1")
    suspend fun deleteAllSyncedForWorkspace(workspaceId: String)

    @Query("DELETE FROM withdrawals WHERE workspaceId IN (:workspaceIds) AND isSynced = 1")
    suspend fun deleteAllSyncedForWorkspaces(workspaceIds: List<String>)

    @Query("DELETE FROM withdrawals WHERE isSynced = 1")
    suspend fun deleteAllSynced()

    @Query("SELECT SUM(amount) FROM withdrawals WHERE workspaceId = :workspaceId")
    fun getTotalWithdrawnForWorkspace(workspaceId: String): Flow<Double?>

    @Query("SELECT SUM(amount) FROM withdrawals WHERE workspaceId IN (:workspaceIds)")
    fun getTotalWithdrawnForWorkspaces(workspaceIds: List<String>): Flow<Double?>

    @Query("SELECT SUM(amount) FROM withdrawals")
    fun getTotalWithdrawn(): Flow<Double?>

    @Query("SELECT * FROM withdrawals WHERE workspaceId = :workspaceId AND isSynced = 0")
    suspend fun getUnsyncedWithdrawalsForWorkspace(workspaceId: String): List<WithdrawalEntity>

    @Query("SELECT * FROM withdrawals WHERE isSynced = 0")
    suspend fun getUnsyncedWithdrawals(): List<WithdrawalEntity>

    @Query("UPDATE withdrawals SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markWithdrawalsSynced(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM withdrawals WHERE workspaceId = :workspaceId AND isSynced = 0")
    fun getUnsyncedCountForWorkspace(workspaceId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM withdrawals WHERE workspaceId IN (:workspaceIds) AND isSynced = 0")
    fun getUnsyncedCountForWorkspaces(workspaceIds: List<String>): Flow<Int>

    @Query("SELECT COUNT(*) FROM withdrawals WHERE isSynced = 0")
    fun getUnsyncedCount(): Flow<Int>
}

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE workspaceId = :workspaceId LIMIT 1")
    fun getSettingsForWorkspace(workspaceId: String): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings WHERE workspaceId = :workspaceId LIMIT 1")
    suspend fun getSettingsForWorkspaceOnce(workspaceId: String): AppSettingsEntity?

    @Query("SELECT * FROM app_settings LIMIT 1")
    fun getSettings(): Flow<AppSettingsEntity?>

    @Query("SELECT * FROM app_settings LIMIT 1")
    suspend fun getSettingsOnce(): AppSettingsEntity?

    @Query("SELECT * FROM app_settings")
    suspend fun getAllSettingsOnce(): List<AppSettingsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSettings(settings: AppSettingsEntity)

    @Query("DELETE FROM app_settings WHERE workspaceId = :workspaceId")
    suspend fun deleteSettingsForWorkspace(workspaceId: String)
}

@Dao
interface PaymentDao {
    @Query("SELECT * FROM payments WHERE workspaceId = :workspaceId ORDER BY collectedAt DESC")
    fun getPaymentsForWorkspace(workspaceId: String): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE workspaceId IN (:workspaceIds) ORDER BY collectedAt DESC")
    fun getPaymentsForWorkspaces(workspaceIds: List<String>): Flow<List<PaymentEntity>>

    @Query("SELECT * FROM payments WHERE customerId = :customerId ORDER BY collectedAt DESC")
    suspend fun getPaymentsForCustomer(customerId: Long): List<PaymentEntity>

    @Query("SELECT * FROM payments WHERE id = :id")
    suspend fun getPaymentById(id: Long): PaymentEntity?

    @Query("SELECT * FROM payments ORDER BY collectedAt DESC")
    fun getAllPayments(): Flow<List<PaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPayments(payments: List<PaymentEntity>)

    @Query("DELETE FROM payments WHERE id = :id")
    suspend fun deletePayment(id: Long)

    @Delete
    suspend fun deletePayment(payment: PaymentEntity)

    @Query("DELETE FROM payments WHERE workspaceId = :workspaceId AND isSynced = 1 AND id NOT IN (:validIds)")
    suspend fun deleteSyncedNotIn(workspaceId: String, validIds: List<Long>)

    @Query("DELETE FROM payments WHERE workspaceId = :workspaceId AND isSynced = 1")
    suspend fun deleteAllSyncedForWorkspace(workspaceId: String)

    @Query("DELETE FROM payments WHERE workspaceId = :workspaceId")
    suspend fun deleteAllForWorkspace(workspaceId: String)

    @Query("SELECT * FROM payments WHERE workspaceId = :workspaceId AND isSynced = 0")
    suspend fun getUnsyncedPaymentsForWorkspace(workspaceId: String): List<PaymentEntity>

    @Query("UPDATE payments SET isSynced = 1 WHERE id IN (:ids)")
    suspend fun markPaymentsSynced(ids: List<Long>)
}

@Dao
interface ChecklistItemDao {
    @Query("SELECT * FROM checklist_items WHERE workspaceId = :workspaceId ORDER BY createdAt DESC")
    fun getChecklistForWorkspace(workspaceId: String): Flow<List<ChecklistItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChecklistItem(item: ChecklistItemEntity): Long

    @Update
    suspend fun updateChecklistItem(item: ChecklistItemEntity)

    @Delete
    suspend fun deleteChecklistItem(item: ChecklistItemEntity)

    @Query("DELETE FROM checklist_items WHERE id = :id")
    suspend fun deleteChecklistItemById(id: Long)

    @Query("DELETE FROM checklist_items WHERE workspaceId = :workspaceId")
    suspend fun deleteAllForWorkspace(workspaceId: String)
}

@Dao
interface WorkTypeExtensionDao {
    @Query("SELECT * FROM work_type_extensions WHERE workspaceId = :workspaceId ORDER BY name ASC")
    fun getExtensionsForWorkspace(workspaceId: String): Flow<List<WorkTypeExtensionEntity>>

    @Query("SELECT * FROM work_type_extensions WHERE workspaceId IN (:workspaceIds) ORDER BY name ASC")
    fun getExtensionsForWorkspaces(workspaceIds: List<String>): Flow<List<WorkTypeExtensionEntity>>

    @Query("SELECT * FROM work_type_extensions WHERE workspaceId = :workspaceId ORDER BY name ASC")
    suspend fun getExtensionsForWorkspaceOnce(workspaceId: String): List<WorkTypeExtensionEntity>

    @Query("SELECT * FROM work_type_extensions ORDER BY name ASC")
    fun getAllExtensions(): Flow<List<WorkTypeExtensionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExtension(extension: WorkTypeExtensionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(extensions: List<WorkTypeExtensionEntity>)

    @Delete
    suspend fun deleteExtension(extension: WorkTypeExtensionEntity)

    @Query("DELETE FROM work_type_extensions WHERE workspaceId = :workspaceId AND id NOT IN (:validIds)")
    suspend fun deleteNotIn(workspaceId: String, validIds: List<Long>)

    @Query("DELETE FROM work_type_extensions WHERE workspaceId = :workspaceId")
    suspend fun deleteAllForWorkspace(workspaceId: String)
}

