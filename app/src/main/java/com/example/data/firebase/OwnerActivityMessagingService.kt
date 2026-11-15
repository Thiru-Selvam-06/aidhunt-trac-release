package com.example.data.firebase

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.BuildConfig
import com.example.MainActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

object OwnerActivityPush {
    const val ACTION_OPEN = "com.example.OWNER_ACTIVITY"
    const val CHANNEL = "owner_activity"
    private fun prefs(context: Context) = context.getSharedPreferences("owner_activity_push", Context.MODE_PRIVATE)
    private fun registrationKey(workspaceId: String) = "owner:$workspaceId"
    private fun enabled() = !BuildConfig.DEVICE_TEST && !BuildConfig.LIVE_TEST && !BuildConfig.PHYSICAL_TEST

    fun wasRegistered(context: Context, workspaceId: String, uid: String): Boolean =
        prefs(context).getString(registrationKey(workspaceId), null) == uid

    suspend fun register(context: Context, workspaceId: String, uid: String, language: String) {
        if (!enabled() || workspaceId.isBlank() || uid.isBlank()) return
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser?.uid != uid) return
        val settings = prefs(context)
        val installation = settings.getString("installation", null) ?: UUID.randomUUID().toString().also {
            settings.edit().putString("installation", it).apply()
        }
        val workspace = FirebaseFirestore.getInstance().collection("workspaces").document(workspaceId)
        if (workspace.get(Source.SERVER).await().getString("ownerUid") != uid) return
        val token = FirebaseMessaging.getInstance().token.await()
        if (auth.currentUser?.uid != uid) return
        val registration = workspace.collection("ownerPushDevices").document(installation)
        registration.set(mapOf("uid" to uid, "token" to token, "language" to if (language.equals("TA", true)) "TA" else "EN",
            "updatedAt" to FieldValue.serverTimestamp())).await()
        if (auth.currentUser?.uid != uid) return
        settings.edit().putString(registrationKey(workspaceId), uid)
            .putString("language:$workspaceId", if (language.equals("TA", true)) "TA" else "EN")
            .apply()
        val now = registration.get(Source.SERVER).await().getTimestamp("updatedAt")?.toDate()?.time ?: return
        val checkpointKey = "last-open:$uid:$workspaceId"
        val previous = settings.getLong(checkpointKey, 0L)
        if (previous > 0L) {
            val query = workspace.collection("ownerActivityEvents")
                .whereGreaterThanOrEqualTo("recordedAt", com.google.firebase.Timestamp(java.util.Date(previous)))
                .whereLessThan("recordedAt", com.google.firebase.Timestamp(java.util.Date(now)))
                .orderBy("recordedAt").limit(50)
            var cursor: com.google.firebase.firestore.DocumentSnapshot? = null
            do {
                if (auth.currentUser?.uid != uid) return
                val page = (cursor?.let { query.startAfter(it) } ?: query).get(Source.SERVER).await()
                for (event in page.documents) {
                    if (event.getString("recipientUid") == uid)
                        show(context, workspaceId, uid, event.id, event.getString("eventType").orEmpty())
                }
                cursor = page.documents.lastOrNull()
            } while (page.size() == 50)
        }
        if (auth.currentUser?.uid == uid) {
            settings.edit().putLong(checkpointKey, now).apply()
        }
    }

    fun refreshToken(context: Context, token: String) {
        if (!enabled()) return
        val uid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull() ?: return
        val settings = prefs(context)
        val installation = settings.getString("installation", null) ?: return
        settings.all.filter { (key, value) -> key.startsWith("owner:") && value == uid }.keys.forEach { key ->
            val workspaceId = key.removePrefix("owner:")
            FirebaseFirestore.getInstance().collection("workspaces").document(workspaceId)
                .collection("ownerPushDevices").document(installation)
                .set(mapOf("uid" to uid, "token" to token, "language" to settings.getString("language:$workspaceId", "EN"),
                    "updatedAt" to FieldValue.serverTimestamp()))
        }
    }

    suspend fun unregister(context: Context) {
        if (!enabled()) return
        val uid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull() ?: return
        val settings = prefs(context)
        val keys = settings.all.filter { (key, value) -> key.startsWith("owner:") && value == uid }.keys
        settings.edit().apply { keys.forEach { remove(it) } }.apply()
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications.filter { it.tag?.startsWith("$uid:") == true }
            .forEach { manager.cancel(it.tag, it.id) }
        val installation = settings.getString("installation", null) ?: return
        withTimeoutOrNull(1500L) {
            for (key in keys) {
                FirebaseFirestore.getInstance().collection("workspaces").document(key.removePrefix("owner:"))
                    .collection("ownerPushDevices").document(installation).delete().await()
            }
        }
    }

    fun show(context: Context, workspaceId: String, uid: String, eventId: String, type: String) {
        if (!enabled() || eventId.isBlank() || workspaceId.isBlank() || uid.isBlank()) return
        if (runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull() != uid) return
        val settings = prefs(context)
        if (settings.getString(registrationKey(workspaceId), null) != uid) return
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return
        val tag = "$uid:$workspaceId:$eventId"
        val tamil = settings.getString("language:$workspaceId", "EN") == "TA"
        val label = when (type) {
            "JOB_CREATED" -> if (tamil) "பங்குதாரர் வேலை பதிவைச் சேர்த்தார்" else "A partner added a job"
            "JOB_EDITED" -> if (tamil) "பங்குதாரர் வேலை பதிவைத் திருத்தினார்" else "A partner edited a job"
            "JOB_DELETED" -> if (tamil) "பங்குதாரர் வேலை பதிவை நீக்கினார்" else "A partner deleted a job"
            "COLLECTION" -> if (tamil) "வாடிக்கையாளர் வசூல் புதுப்பிக்கப்பட்டது" else "A partner updated a collection"
            "EXPENSE" -> if (tamil) "செலவுப் பதிவு புதுப்பிக்கப்பட்டது" else "A partner updated an expense"
            "WITHDRAWAL" -> if (tamil) "பங்குதாரர் பணம் எடுத்தார்" else "A partner recorded a withdrawal"
            else -> return
        }
        synchronized(this) {
            val recent = settings.getString("displayed", "").orEmpty().lineSequence().filter(String::isNotEmpty).toList()
            if (tag in recent) return
            if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(
                CHANNEL, if (tamil) "வணிகச் செயல்பாடு" else "Business activity", NotificationManager.IMPORTANCE_DEFAULT))
            val destination = Intent(context, MainActivity::class.java).setAction(ACTION_OPEN)
                .setData(android.net.Uri.Builder().scheme("aidhunt").authority("owner-activity")
                    .appendPath(workspaceId).appendPath(eventId).build())
                .putExtra("workspaceId", workspaceId).putExtra("recipientUid", uid)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val intent = PendingIntent.getActivity(context, 0, destination, PendingIntent.FLAG_IMMUTABLE)
            manager.notify(tag, 1, NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("AIDHUNT Trac")
                .setContentText(label)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setOnlyAlertOnce(true).setAutoCancel(true).setContentIntent(intent).build())
            settings.edit().putString("displayed", (recent.takeLast(255) + tag).joinToString("\n")).apply()
        }
    }
}

class OwnerActivityMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = OwnerActivityPush.refreshToken(this, token)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["kind"] != "TRAC_OWNER_ACTIVITY") return
        OwnerActivityPush.show(this, data["workspaceId"].orEmpty(), data["recipientUid"].orEmpty(),
            data["eventId"].orEmpty(), data["eventType"].orEmpty())
    }
}
