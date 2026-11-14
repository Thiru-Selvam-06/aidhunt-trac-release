package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.entity.AppNotificationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppNotificationDao {

    @Query(
        """
        SELECT * FROM app_notifications
        WHERE workspaceId = :workspaceId
          AND actorUid != :currentUid
          AND (
            (:isOwner = 1 AND (targetAudience = 'OWNER_ONLY' OR recipientUid = :currentUid OR recipientUid = ''))
            OR (:isOwner = 0 AND targetAudience = 'PARTNER_TARGETED' AND recipientUid = :currentUid)
          )
        ORDER BY createdAt DESC
        """
    )
    fun observeVisibleNotifications(
        workspaceId: String,
        currentUid: String,
        isOwner: Boolean
    ): Flow<List<AppNotificationEntity>>

    @Query("SELECT * FROM app_notifications WHERE workspaceId = :workspaceId AND id = :id LIMIT 1")
    suspend fun getById(workspaceId: String, id: String): AppNotificationEntity?

    @Query("SELECT * FROM app_notifications WHERE workspaceId = :workspaceId ORDER BY createdAt DESC")
    suspend fun getAllOnce(workspaceId: String): List<AppNotificationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<AppNotificationEntity>)

    @Transaction
    suspend fun upsertPreservingReadState(items: List<AppNotificationEntity>) {
        if (items.isEmpty()) return
        val merged = items.map { incoming ->
            val existing = getById(incoming.workspaceId, incoming.id)
            if (existing != null && existing.isRead && !incoming.isRead) {
                incoming.copy(isRead = true, readAt = maxOf(existing.readAt, incoming.readAt))
            } else if (existing != null && incoming.isRead && existing.readAt > incoming.readAt) {
                incoming.copy(readAt = existing.readAt)
            } else {
                incoming
            }
        }
        upsertAll(merged)
    }

    @Query("UPDATE app_notifications SET isRead = 1, readAt = :readAt WHERE workspaceId = :workspaceId AND id = :id")
    suspend fun markRead(workspaceId: String, id: String, readAt: Long)

    @Query(
        """
        UPDATE app_notifications SET isRead = 1, readAt = :readAt
        WHERE workspaceId = :workspaceId
          AND isRead = 0
          AND actorUid != :currentUid
          AND (
            (:isOwner = 1 AND (targetAudience = 'OWNER_ONLY' OR recipientUid = :currentUid OR recipientUid = ''))
            OR (:isOwner = 0 AND targetAudience = 'PARTNER_TARGETED' AND recipientUid = :currentUid)
          )
        """
    )
    suspend fun markAllRead(workspaceId: String, currentUid: String, isOwner: Boolean, readAt: Long)

    @Query("DELETE FROM app_notifications WHERE workspaceId = :workspaceId AND isRead = 1 AND createdAt < :cutoffMillis")
    suspend fun pruneReadBefore(workspaceId: String, cutoffMillis: Long)
}
