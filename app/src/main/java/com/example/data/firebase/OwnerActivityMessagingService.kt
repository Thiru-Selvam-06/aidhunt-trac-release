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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.BuildConfig
import com.example.MainActivity
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

object OwnerActivityPush {
    const val ACTION_OPEN = "com.example.OWNER_ACTIVITY"
    const val CHANNEL = "owner_activity"
    private val client = OkHttpClient.Builder().callTimeout(10L, TimeUnit.SECONDS).build()
    private fun prefs(context: Context) = context.getSharedPreferences("owner_activity_push", Context.MODE_PRIVATE)
    private fun registrationKey(workspaceId: String) = "owner:$workspaceId"
    private fun checkpointKey(uid: String, workspaceId: String) = "owner-history:$uid:$workspaceId"
    private fun currentUid() = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
    fun enabled(context: Context) = OwnerActivityPolicy.enabled(BuildConfig.OWNER_ACTIVITY_PUSH,
        runCatching { FirebaseApp.getInstance().options.projectId }.getOrNull(), context.packageName)

    fun observeAuthentication(context: Context) {
        if (!enabled(context)) return
        FirebaseAuth.getInstance().addAuthStateListener { auth ->
            val uid = auth.currentUser?.uid
            discardOtherAccounts(context, uid)
            if (uid == null) FirebaseMessaging.getInstance().isAutoInitEnabled = false
        }
    }

    private fun discardOtherAccounts(context: Context, keepUid: String?) = synchronized(this) {
        val settings = prefs(context)
        val obsolete = settings.all.filter { (key, uid) -> key.startsWith("owner:") && uid != keepUid }
        if (obsolete.isNotEmpty() || keepUid == null) {
            settings.edit().apply {
                obsolete.keys.forEach { key ->
                    remove(key)
                    remove("language:" + key.removePrefix("owner:"))
                }
                remove("installation")
            }.commit()
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        val legacyTags = settings.getString("displayed", "").orEmpty().lineSequence().toSet()
        manager.activeNotifications.filter { notification ->
            val tag = notification.tag ?: return@filter false
            when {
                tag.startsWith("owner:") -> keepUid == null || !tag.startsWith("owner:$keepUid:")
                tag in legacyTags -> keepUid == null || !tag.startsWith("$keepUid:")
                else -> false
            }
        }.forEach { manager.cancel(it.tag, it.id) }
    }

    fun permissionMissing(context: Context): Boolean =
        Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun shouldRequestPermission(context: Context, uid: String): Boolean =
        enabled(context) && currentUid() == uid && permissionMissing(context) &&
            !prefs(context).getBoolean("permission-asked:$uid", false)

    fun markPermissionAsked(context: Context, uid: String) {
        prefs(context).edit().putBoolean("permission-asked:$uid", true).apply()
    }

    fun wasRegistered(context: Context, workspaceId: String, uid: String): Boolean =
        currentUid() == uid && prefs(context).getString(registrationKey(workspaceId), null) == uid

    private suspend fun call(workspaceId: String, uid: String, action: String, data: JSONObject = JSONObject()): JSONObject =
        withContext(Dispatchers.IO) {
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser?.takeIf { it.uid == uid } ?: throw SecurityException("Activity account changed")
            var result: JSONObject? = null
            WorkspaceCommandProtocol.withAuthenticatedToken(uid, { auth.currentUser?.uid },
                { refresh -> user.getIdToken(refresh).await().token }) { idToken ->
                val body = data.put("workspaceId", workspaceId).put("action", action)
                val url = "https://asia-south1-trac-aidhunt.cloudfunctions.net/ownerActivitySession"
                client.newCall(WorkspaceCommandProtocol.request(url, idToken, body)).execute().use { response ->
                    val json = runCatching { JSONObject(response.body?.string().orEmpty()) }.getOrNull()
                    val status = json?.optJSONObject("error")?.optString("status")
                    if (response.code == 401) throw WorkspaceAuthenticationRequiredException()
                    if (status in setOf("PERMISSION_DENIED", "INVALID_ARGUMENT", "NOT_FOUND")) {
                        throw SecurityException("Activity request is not authorized or no longer available")
                    }
                    if (response.isSuccessful) result = json?.optJSONObject("result")
                }
            }
            if (auth.currentUser?.uid != uid) throw SecurityException("Activity account changed")
            result ?: throw IOException("Owner activity service unavailable")
        }

    internal suspend fun register(context: Context, workspaceId: String, uid: String, language: String) {
        if (!enabled(context) || !OwnerActivityPolicy.validIdentity(workspaceId) || currentUid() != uid) return
        val settings = prefs(context)
        val installation = synchronized(this) {
            settings.getString("installation", null) ?: UUID.randomUUID().toString().also {
                settings.edit().putString("installation", it).commit()
            }
        }
        val localLanguage = if (language.equals("TA", true)) "TA" else "EN"
        val token = FirebaseMessaging.getInstance().token.await()
        if (currentUid() != uid || settings.getString("installation", null) != installation) return
        val response = call(workspaceId, uid, "register", JSONObject().put("installationId", installation)
            .put("token", token).put("language", localLanguage))
        if (!response.optBoolean("ok")) throw IOException("Owner registration was not confirmed")
        synchronized(this) {
            if (currentUid() != uid || settings.getString("installation", null) != installation) return
            settings.edit().putString(registrationKey(workspaceId), uid).putString("language:$workspaceId", localLanguage).commit()
        }
        FirebaseMessaging.getInstance().isAutoInitEnabled = true
        if (!permissionMissing(context)) reconcile(context, workspaceId, uid)
    }

    private suspend fun reconcile(context: Context, workspaceId: String, uid: String) {
        val settings = prefs(context)
        val key = checkpointKey(uid, workspaceId)
        var afterId = settings.getString(key, null)
        while (wasRegistered(context, workspaceId, uid)) {
            val response = call(workspaceId, uid, "history", JSONObject().put("afterId", afterId))
            val items = response.optJSONArray("items") ?: throw IOException("Incomplete activity history response")
            for (i in 0 until items.length()) {
                val item = OwnerActivityItem.fromJson(items.getJSONObject(i))
                    ?: throw IOException("Incomplete activity item")
                if (!show(context, uid, workspaceId, item)) return
            }
            val nextId = response.optString("nextId").takeIf(OwnerActivityPolicy::validEvent)
            if (nextId != null && nextId != afterId && settings.getString(key, null) == afterId) {
                settings.edit().putString(key, nextId).commit()
            }
            if (!response.optBoolean("more") || nextId == null || nextId == afterId) return
            afterId = nextId
        }
    }

    internal suspend fun fetch(context: Context, destination: OwnerActivityDestination): OwnerActivityItem? {
        if (!enabled(context) || !wasRegistered(context, destination.workspaceId, destination.uid)) return null
        val result = call(destination.workspaceId, destination.uid, "event", JSONObject().put("eventId", destination.eventId))
        val data = result.optJSONObject("item") ?: return null
        return OwnerActivityItem.fromJson(data)?.takeIf {
            it.id == destination.eventId && wasRegistered(context, destination.workspaceId, destination.uid)
        }
    }

    fun scheduleRegister(context: Context, workspaceId: String, uid: String, language: String) {
        if (!enabled(context) || !OwnerActivityPolicy.validIdentity(workspaceId) || currentUid() != uid) return
        enqueue(context, "register:$uid:$workspaceId", workDataOf(
            "uid" to uid, "workspace" to workspaceId, "language" to language
        ), ExistingWorkPolicy.REPLACE)
    }

    fun refreshToken(context: Context) {
        if (!enabled(context)) return
        val uid = currentUid() ?: return
        val settings = prefs(context)
        settings.all.filter { (key, value) -> key.startsWith("owner:") && value == uid }.keys.forEach { key ->
            val workspaceId = key.removePrefix("owner:")
            scheduleRegister(context, workspaceId, uid, settings.getString("language:$workspaceId", "EN").orEmpty())
        }
    }

    internal fun scheduleEvent(context: Context, destination: OwnerActivityDestination) {
        if (!enabled(context) || !wasRegistered(context, destination.workspaceId, destination.uid)) return
        enqueue(context, "event:${destination.uid}:${destination.workspaceId}:${destination.eventId}", workDataOf(
            "uid" to destination.uid, "workspace" to destination.workspaceId, "event" to destination.eventId
        ), ExistingWorkPolicy.KEEP)
    }

    private fun enqueue(context: Context, name: String, data: androidx.work.Data, policy: ExistingWorkPolicy) {
        val request = OneTimeWorkRequestBuilder<OwnerActivityWorker>().setInputData(data)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15L, TimeUnit.SECONDS)
        if (data.getString("event") != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            request.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        WorkManager.getInstance(context).enqueueUniqueWork("owner-activity:$name", policy, request.build())
    }

    suspend fun unregister(context: Context) {
        if (!enabled(context)) return
        val uid = currentUid() ?: return
        val settings = prefs(context)
        val installation = settings.getString("installation", null)
        val keys = settings.all.filter { (key, value) -> key.startsWith("owner:") && value == uid }.keys
        discardOtherAccounts(context, null)
        FirebaseMessaging.getInstance().isAutoInitEnabled = false
        if (installation == null) return
        withTimeoutOrNull(2500L) {
            for (key in keys) {
                try {
                    call(key.removePrefix("owner:"), uid, "unregister", JSONObject().put("installationId", installation))
                } catch (error: kotlinx.coroutines.CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // A signed-out installation ignores any remaining data-only wakeups.
                }
            }
        }
    }

    internal fun show(context: Context, uid: String, workspaceId: String, item: OwnerActivityItem): Boolean {
        if (!enabled(context) || !wasRegistered(context, workspaceId, uid)) return false
        val settings = prefs(context)
        val locale = settings.getString("language:$workspaceId", "EN") == "TA"
        return synchronized(this) {
            if (!wasRegistered(context, workspaceId, uid)) return@synchronized false
            val tag = "owner:$uid:$workspaceId:${item.id}"
            val previousTag = "$uid:$workspaceId:${item.id}"
            if (settings.getBoolean("shown:$tag", false) ||
                settings.getString("displayed", "").orEmpty().lineSequence().any { it == previousTag }) return@synchronized true
            if (!post(context, OwnerActivityDestination(uid, workspaceId, item.id), item, locale, tag)) return@synchronized false
            settings.edit().putBoolean("shown:$tag", true).commit()
        }
    }

    internal fun post(context: Context, destination: OwnerActivityDestination, item: OwnerActivityItem,
        tamil: Boolean, tag: String): Boolean {
        if (permissionMissing(context)) return false
        val label = item.label(tamil) ?: return false
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL,
                if (tamil) "வணிகச் செயல்பாடு" else "Business activity", NotificationManager.IMPORTANCE_DEFAULT))
            if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE) return false
        }
        val tap = Intent(context, MainActivity::class.java).setAction(ACTION_OPEN)
            .setData(android.net.Uri.Builder().scheme("aidhunt").authority("owner-activity")
                .appendPath(destination.workspaceId).appendPath(destination.eventId).build())
            .putExtra("recipientUid", destination.uid)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(context, 0, tap, PendingIntent.FLAG_IMMUTABLE)
        manager.notify(tag, 1, NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("AIDHUNT Trac").setContentText(label)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setOnlyAlertOnce(true)
            .setAutoCancel(true).setContentIntent(pendingIntent).build())
        return true
    }

    internal fun destination(intent: Intent): OwnerActivityDestination? {
        val uri = intent.data ?: return null
        if (intent.action != ACTION_OPEN || uri.scheme != "aidhunt" || uri.authority != "owner-activity" ||
            uri.pathSegments.size != 2) return null
        return OwnerActivityPolicy.destination(intent.getStringExtra("recipientUid").orEmpty(),
            uri.pathSegments[0], uri.pathSegments[1])
    }
}

class OwnerActivityWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uid = inputData.getString("uid") ?: return Result.failure()
        val workspaceId = inputData.getString("workspace") ?: return Result.failure()
        return try {
            val eventId = inputData.getString("event")
            if (eventId == null) {
                OwnerActivityPush.register(applicationContext, workspaceId, uid, inputData.getString("language").orEmpty())
            } else {
                val destination = OwnerActivityPolicy.destination(uid, workspaceId, eventId) ?: return Result.failure()
                val item = OwnerActivityPush.fetch(applicationContext, destination) ?: return Result.success()
                OwnerActivityPush.show(applicationContext, uid, workspaceId, item)
            }
            Result.success()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: SecurityException) {
            Result.success()
        } catch (_: Exception) {
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }
}

class OwnerActivityMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) = OwnerActivityPush.refreshToken(this)

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["kind"] != "TRAC_OWNER_ACTIVITY") return
        val destination = OwnerActivityPolicy.destination(data["recipientUid"].orEmpty(),
            data["workspaceId"].orEmpty(), data["eventId"].orEmpty()) ?: return
        OwnerActivityPush.scheduleEvent(this, destination)
    }
}
