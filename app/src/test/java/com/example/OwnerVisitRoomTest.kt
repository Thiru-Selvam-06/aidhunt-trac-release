package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.AppNotificationEntity
import com.example.data.sync.OwnerVisitActivity
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class OwnerVisitRoomTest {
    @Test fun cachedUnreadSurvivesRestartAndReadingOnOneDeviceDoesNotChangeAnother() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val firstName = UUID.randomUUID().toString() + ".db"
        val secondName = UUID.randomUUID().toString() + ".db"
        suspend fun withDatabase(name: String, action: suspend (AppDatabase) -> Unit) {
            val db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            try {
                action(db)
            } finally {
                db.close()
            }
        }
        val event = AppNotificationEntity(id = "verified-1", workspaceId = "one",
            recipientUid = "owner", targetAudience = OwnerVisitActivity.AUDIENCE, actorUid = "partner", createdAt = 100,
            eventType = AppNotificationEntity.EVENT_CASH_BALANCE_ADJUSTED, entityType = "CASH_BALANCE",
            amount = 1.0, metadataJson = "{\"reason\":\"original cash reason\",\"deltaPaise\":100}")
        val otherEvent = event.copy(id = "verified-2", createdAt = 100, eventType = AppNotificationEntity.EVENT_JOB_CREATED)
        try {
            withDatabase(firstName) { it.appNotificationDao().upsertPreservingReadState(listOf(event, otherEvent)) }
            withDatabase(secondName) { it.appNotificationDao().upsertPreservingReadState(listOf(event, otherEvent)) }
            withDatabase(firstName) { db ->
                val dao = db.appNotificationDao()
                val first = dao.observeVisibleNotifications("one", "owner", true).first()
                assertEquals(listOf("verified-1", "verified-2"), first.map { it.id })
                assertTrue(first.none { it.isRead })
                assertEquals(event.metadataJson, first.first().metadataJson)
                assertEquals(event.eventType, first.first().eventType)
                assertTrue(dao.observeVisibleNotifications("one", "another-owner", true).first().isEmpty())
                assertTrue(dao.observeVisibleNotifications("other-workspace", "owner", true).first().isEmpty())
                assertTrue(dao.observeVisibleNotifications("one", "partner", false).first().isEmpty())
                dao.markRead("one", event.id, 200)
                dao.upsertPreservingReadState(listOf(event, otherEvent))
            }
            withDatabase(firstName) { db ->
                val restored = db.appNotificationDao().observeVisibleNotifications("one", "owner", true).first()
                assertTrue(restored[0].isRead)
                assertFalse(restored[1].isRead)
            }
            withDatabase(secondName) { db ->
                assertTrue(db.appNotificationDao().observeVisibleNotifications("one", "owner", true).first().none { it.isRead })
            }
        } finally {
            context.deleteDatabase(firstName)
            context.deleteDatabase(secondName)
        }
        Unit
    }
}
