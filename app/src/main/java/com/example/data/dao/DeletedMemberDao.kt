package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.entity.DeletedMemberEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeletedMemberDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(member: DeletedMemberEntity)
    @Query("SELECT * FROM deleted_members WHERE workspaceId = :wsId ORDER BY removedAt DESC")
    fun observe(wsId: String): Flow<List<DeletedMemberEntity>>
    @Query("SELECT * FROM deleted_members WHERE workspaceId = :wsId AND uid = :uid")
    suspend fun get(wsId: String, uid: String): DeletedMemberEntity?
}
