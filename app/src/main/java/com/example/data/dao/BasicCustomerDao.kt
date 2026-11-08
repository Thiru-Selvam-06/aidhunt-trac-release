package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.BasicCustomerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BasicCustomerDao {
    @Query("SELECT * FROM basic_customers WHERE workspaceId = :workspaceId ORDER BY name, id")
    fun observe(workspaceId: String): Flow<List<BasicCustomerEntity>>

    @Query("SELECT * FROM basic_customers WHERE workspaceId = :workspaceId AND id = :id")
    suspend fun find(workspaceId: String, id: Long): BasicCustomerEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(row: BasicCustomerEntity)

    @Query("DELETE FROM basic_customers WHERE workspaceId = :workspaceId AND id = :id")
    suspend fun remove(workspaceId: String, id: Long)

    @Query("DELETE FROM basic_customers WHERE workspaceId = :workspaceId AND isSynced = 1 AND id NOT IN (:retainedIds)")
    suspend fun removeConfirmedAbsent(workspaceId: String, retainedIds: List<Long>)

    @Query("DELETE FROM basic_customers WHERE workspaceId = :workspaceId AND isSynced = 1")
    suspend fun removeAllConfirmed(workspaceId: String)

    @Query("SELECT COUNT(*) FROM basic_customers WHERE workspaceId = :workspaceId AND isSynced = 0")
    suspend fun unsyncedCount(workspaceId: String): Int
}
