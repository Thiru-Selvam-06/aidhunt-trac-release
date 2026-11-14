package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.AppNotificationEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class Stage4NotificationRoomTest {

    @Test
    fun migration23To24CreatesAppNotificationsTableAndIndex() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_mig_23_24.db"
        context.deleteDatabase(dbName)
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(23) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE IF NOT EXISTS _seed (id INTEGER PRIMARY KEY)")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                })
                .build()
        )
        val db = helper.writableDatabase
        try {
            AppDatabase.MIGRATION_23_24.migrate(db)
            val cols = mutableSetOf<String>()
            db.query("PRAGMA table_info(`app_notifications`)").use { c ->
                val idx = c.getColumnIndexOrThrow("name")
                while (c.moveToNext()) cols.add(c.getString(idx))
            }
            assertTrue(cols.containsAll(listOf("id", "workspaceId", "recipientUid", "targetAudience", "actorUid", "eventType", "isRead", "readAt")))
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun daoScopingReadPreservationAndRetentionWorkCleanly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val dao = db.appNotificationDao()
            val ws = "ws_stage4_room"
            val now = 2_000_000L
            val items = listOf(
                AppNotificationEntity(
                    id = "n_owner_job", workspaceId = ws, operationId = "op1",
                    recipientUid = "owner", targetAudience = AppNotificationEntity.AUDIENCE_OWNER_ONLY,
                    actorUid = "partnerA", actorName = "Partner A", eventType = AppNotificationEntity.EVENT_JOB_CREATED,
                    entityType = "JOB", entityId = 1L, amount = 500.0, counterpartyName = "Ravi", createdAt = now - 1000L
                ),
                AppNotificationEntity(
                    id = "n_partner_b", workspaceId = ws, operationId = "op2",
                    recipientUid = "partnerB", targetAudience = AppNotificationEntity.AUDIENCE_PARTNER_TARGETED,
                    actorUid = "partnerA", actorName = "Partner A", eventType = AppNotificationEntity.EVENT_BORROWING_REQUESTED,
                    entityType = "BORROWING_REQUEST", entityId = 2L, amount = 700.0, counterpartyName = "Partner A", createdAt = now
                ),
                AppNotificationEntity(
                    id = "n_owner_borr", workspaceId = ws, operationId = "op2",
                    recipientUid = "owner", targetAudience = AppNotificationEntity.AUDIENCE_OWNER_ONLY,
                    actorUid = "partnerA", actorName = "Partner A", eventType = AppNotificationEntity.EVENT_BORROWING_REQUESTED,
                    entityType = "BORROWING_REQUEST", entityId = 2L, amount = 700.0, counterpartyName = "Partner A -> Partner B", createdAt = now
                ),
                AppNotificationEntity(
                    id = "n_partner_a_target", workspaceId = ws, operationId = "op3",
                    recipientUid = "partnerA", targetAudience = AppNotificationEntity.AUDIENCE_PARTNER_TARGETED,
                    actorUid = "partnerB", actorName = "Partner B", eventType = AppNotificationEntity.EVENT_BORROWING_APPROVED,
                    entityType = "BORROWING_REQUEST", entityId = 2L, amount = 700.0, counterpartyName = "Partner B", createdAt = now + 500L
                )
            )
            dao.upsertPreservingReadState(items)

            val ownerVisible = dao.observeVisibleNotifications(ws, "owner", isOwner = true).first()
            assertEquals(listOf("n_owner_borr", "n_owner_job"), ownerVisible.map { it.id })

            val partnerBVisible = dao.observeVisibleNotifications(ws, "partnerB", isOwner = false).first()
            assertEquals(listOf("n_partner_b"), partnerBVisible.map { it.id })

            val partnerAVisible = dao.observeVisibleNotifications(ws, "partnerA", isOwner = false).first()
            assertEquals(listOf("n_partner_a_target"), partnerAVisible.map { it.id })

            // Mark partnerB read locally, then replay incoming unread snapshot -> read state must be preserved
            dao.markRead(ws, "n_partner_b", now + 100L)
            dao.upsertPreservingReadState(listOf(items[1].copy(isRead = false, readAt = 0L)))
            assertTrue(dao.getById(ws, "n_partner_b")!!.isRead)

            // Mark all read for owner & verify prune respects cutoff + read flag
            dao.markAllRead(ws, "owner", isOwner = true, readAt = now + 200L)
            assertTrue(dao.getById(ws, "n_owner_job")!!.isRead)
            assertTrue(dao.getById(ws, "n_owner_borr")!!.isRead)
            assertFalse(dao.getById(ws, "n_partner_a_target")!!.isRead)

            dao.pruneReadBefore(ws, now)
            assertEquals(null, dao.getById(ws, "n_owner_job"))
            assertFalse(dao.getById(ws, "n_partner_b") == null)
        } finally {
            db.close()
        }
    }
}
