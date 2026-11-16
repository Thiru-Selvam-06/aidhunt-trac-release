package com.example

import android.Manifest
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.firebase.OwnerActivityDestination
import com.example.data.firebase.OwnerActivityItem
import com.example.data.firebase.OwnerActivityPush
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class OwnerActivityNativeNotificationTest {
    private val context = ApplicationProvider.getApplicationContext<Application>()
    private val eventId = "b".repeat(64)
    private val manager = context.getSystemService(NotificationManager::class.java)
    private val destination = OwnerActivityDestination("owner", "business", eventId)
    private val item = OwnerActivityItem(eventId, "JOB_CREATED", "Perumal")

    @Test fun push017PermissionDeniedDoesNotPostAndPermissionGrantedCreatesPrivateNativeNotification() {
        shadowOf(context).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertTrue(OwnerActivityPush.permissionMissing(context))
        assertFalse(OwnerActivityPush.post(context, destination, item, false, "case"))
        assertEquals(0, shadowOf(manager).size())

        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertFalse(OwnerActivityPush.permissionMissing(context))
        assertTrue(OwnerActivityPush.post(context, destination, item, false, "case"))
        val alert = shadowOf(manager).getNotification("case", 1)
        assertEquals("Perumal added a new job.", alert.extras.getString(Notification.EXTRA_TEXT))
        assertEquals(Notification.VISIBILITY_PRIVATE, alert.visibility)
        assertEquals(OwnerActivityPush.CHANNEL, alert.channelId)
        assertTrue(alert.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertNotNull(manager.getNotificationChannel(OwnerActivityPush.CHANNEL))
    }

    @Test fun push021NativeTapParsesTheVerifiedScopeAndKeepsDistinctPendingIntentsForDifferentEvents() {
        shadowOf(context).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        val another = destination.copy(eventId = "c".repeat(64))
        assertTrue(OwnerActivityPush.post(context, destination, item, false, "first"))
        assertTrue(OwnerActivityPush.post(context, another, item.copy(id = another.eventId), false, "second"))
        val first = shadowOf(manager).getNotification("first", 1).contentIntent
        val second = shadowOf(manager).getNotification("second", 1).contentIntent
        assertTrue(first.isImmutable)
        assertNotEquals(first, second)
        val original = shadowOf(first).savedIntent
        assertEquals(destination, OwnerActivityPush.destination(original))
        assertFalse(requireNotNull(OwnerActivityPush.destination(original)).matches("partner", "business", false))
        assertEquals(another, OwnerActivityPush.destination(shadowOf(second).savedIntent))
        assertNull(OwnerActivityPush.destination(Intent().setAction(OwnerActivityPush.ACTION_OPEN)))
    }
}
