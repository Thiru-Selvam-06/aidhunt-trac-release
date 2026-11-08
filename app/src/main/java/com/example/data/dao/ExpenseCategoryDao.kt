package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.ExpenseCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExpenseCategoryDao {
    @Query("SELECT * FROM expense_categories WHERE workspaceId = :workspaceId ORDER BY nameKey")
    fun observe(workspaceId: String): Flow<List<ExpenseCategoryEntity>>

    @Query("SELECT * FROM expense_categories WHERE workspaceId = :workspaceId AND id = :id")
    suspend fun find(workspaceId: String, id: Long): ExpenseCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(category: ExpenseCategoryEntity)

    @Query("UPDATE expense_categories SET isSynced = 1 WHERE workspaceId = :workspaceId AND id = :id")
    suspend fun acknowledge(workspaceId: String, id: Long)
}
