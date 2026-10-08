package com.example.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.annotation.Keep
import org.json.JSONArray
import org.json.JSONObject

@Keep
data class AuditEvent(
    val eventId: String = java.util.UUID.randomUUID().toString(),
    val actorUid: String = "",
    val actorName: String = "",
    val actorRole: String = "",
    val primaryOwnerUid: String = "",
    val workspaceId: String = "",
    val action: String = "", // CREATE, EDIT, DELETE, PAYMENT, EXPENSE, WITHDRAWAL, TRACTOR, PARTNER
    val entityType: String = "", // JOB, CUSTOMER, PAYMENT, EXPENSE, WITHDRAWAL, TRACTOR, PARTNER
    val recordId: Long = 0L,
    val humanReadableReference: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val source: String = "MOBILE_APP"
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "eventId" to eventId,
        "actorUid" to actorUid,
        "actorName" to actorName,
        "actorRole" to actorRole,
        "primaryOwnerUid" to primaryOwnerUid,
        "workspaceId" to workspaceId,
        "action" to action,
        "entityType" to entityType,
        "recordId" to recordId,
        "humanReadableReference" to humanReadableReference,
        "timestamp" to timestamp,
        "source" to source
    )

    fun toJson(): JSONObject = JSONObject().apply {
        put("eventId", eventId)
        put("actorUid", actorUid)
        put("actorName", actorName)
        put("actorRole", actorRole)
        put("primaryOwnerUid", primaryOwnerUid)
        put("workspaceId", workspaceId)
        put("action", action)
        put("entityType", entityType)
        put("recordId", recordId)
        put("humanReadableReference", humanReadableReference)
        put("timestamp", timestamp)
        put("source", source)
    }

    companion object {
        fun fromJson(obj: JSONObject): AuditEvent {
            return AuditEvent(
                eventId = obj.optString("eventId", java.util.UUID.randomUUID().toString()),
                actorUid = obj.optString("actorUid", ""),
                actorName = obj.optString("actorName", ""),
                actorRole = obj.optString("actorRole", ""),
                primaryOwnerUid = obj.optString("primaryOwnerUid", ""),
                workspaceId = obj.optString("workspaceId", ""),
                action = obj.optString("action", ""),
                entityType = obj.optString("entityType", ""),
                recordId = obj.optLong("recordId", 0L),
                humanReadableReference = obj.optString("humanReadableReference", ""),
                timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                source = obj.optString("source", "MOBILE_APP")
            )
        }
    }
}

class PendingAuditManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val lock = Any()

    fun recordAuditEvent(event: AuditEvent) {
        synchronized(lock) {
            val list = loadAllInternal().toMutableList()
            list.removeAll { it.eventId == event.eventId }
            list.add(event)
            saveAllInternal(list)
            Log.d(TAG, "Recorded pending audit: action=${event.action} type=${event.entityType} ref=${event.humanReadableReference}")
        }
    }

    fun removePendingAudit(eventId: String) {
        synchronized(lock) {
            val list = loadAllInternal().toMutableList()
            val removed = list.removeAll { it.eventId == eventId }
            if (removed) {
                saveAllInternal(list)
                Log.d(TAG, "Removed pending audit: eventId=$eventId")
            }
        }
    }

    fun getAllPendingAudits(): List<AuditEvent> {
        synchronized(lock) {
            return loadAllInternal()
        }
    }

    fun clearAll() {
        synchronized(lock) {
            prefs.edit().clear().apply()
        }
    }

    private fun loadAllInternal(): List<AuditEvent> {
        val raw = prefs.getString(KEY_AUDITS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<AuditEvent>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(AuditEvent.fromJson(obj))
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading pending audits: ${e.message}", e)
            emptyList()
        }
    }

    private fun saveAllInternal(list: List<AuditEvent>) {
        try {
            val jsonArray = JSONArray()
            for (item in list) {
                jsonArray.put(item.toJson())
            }
            prefs.edit().putString(KEY_AUDITS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving pending audits: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "PendingAuditManager"
        private const val PREFS_NAME = "trac_pending_audits"
        private const val KEY_AUDITS = "pending_audits_json"

        @Volatile
        private var INSTANCE: PendingAuditManager? = null

        fun getInstance(context: Context): PendingAuditManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PendingAuditManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
