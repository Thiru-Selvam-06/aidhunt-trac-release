package com.example.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

data class PendingDeletion(
    val entityType: String, // "JOB", "EXPENSE", "CUSTOMER", "WITHDRAWAL", "TRACTOR", "PARTNER"
    val recordId: Long,
    val workspaceId: String,
    val timestamp: Long = System.currentTimeMillis()
)

class PendingDeleteManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val lock = Any()

    fun recordPendingDelete(entityType: String, recordId: Long, workspaceId: String) {
        synchronized(lock) {
            val list = loadAllInternal().toMutableList()
            // Remove any existing identical entry first
            list.removeAll { it.entityType == entityType && it.recordId == recordId && it.workspaceId == workspaceId }
            list.add(PendingDeletion(entityType, recordId, workspaceId, System.currentTimeMillis()))
            saveAllInternal(list)
            Log.d(TAG, "Recorded pending deletion: type=$entityType id=$recordId wsId=$workspaceId")
        }
    }

    fun removePendingDelete(entityType: String, recordId: Long, workspaceId: String) {
        synchronized(lock) {
            val list = loadAllInternal().toMutableList()
            val removed = list.removeAll { it.entityType == entityType && it.recordId == recordId && it.workspaceId == workspaceId }
            if (removed) {
                saveAllInternal(list)
                Log.d(TAG, "Removed pending deletion: type=$entityType id=$recordId wsId=$workspaceId")
            }
        }
    }

    fun isPendingDelete(entityType: String, recordId: Long, workspaceId: String): Boolean {
        synchronized(lock) {
            val list = loadAllInternal()
            return list.any { it.entityType == entityType && it.recordId == recordId && (it.workspaceId == workspaceId || it.workspaceId.isBlank() || workspaceId.isBlank()) }
        }
    }

    fun getPendingDeleteIds(entityType: String, workspaceId: String): Set<Long> {
        synchronized(lock) {
            val list = loadAllInternal()
            return list
                .filter { it.entityType == entityType && (it.workspaceId == workspaceId || it.workspaceId.isBlank() || workspaceId.isBlank()) }
                .map { it.recordId }
                .toSet()
        }
    }

    fun getAllPendingDeletions(): List<PendingDeletion> {
        synchronized(lock) {
            return loadAllInternal()
        }
    }

    fun clearAll() {
        synchronized(lock) {
            prefs.edit().clear().apply()
        }
    }

    private fun loadAllInternal(): List<PendingDeletion> {
        val raw = prefs.getString(KEY_DELETIONS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(raw)
            val list = mutableListOf<PendingDeletion>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    PendingDeletion(
                        entityType = obj.getString("type"),
                        recordId = obj.getLong("id"),
                        workspaceId = obj.optString("wsId", ""),
                        timestamp = obj.optLong("ts", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Error loading pending deletions: ${e.message}", e)
            emptyList()
        }
    }

    private fun saveAllInternal(list: List<PendingDeletion>) {
        try {
            val jsonArray = JSONArray()
            for (item in list) {
                val obj = JSONObject()
                obj.put("type", item.entityType)
                obj.put("id", item.recordId)
                obj.put("wsId", item.workspaceId)
                obj.put("ts", item.timestamp)
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_DELETIONS, jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving pending deletions: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "PendingDeleteManager"
        private const val PREFS_NAME = "trac_pending_deletions"
        private const val KEY_DELETIONS = "pending_deletions_json"

        @Volatile
        private var INSTANCE: PendingDeleteManager? = null

        fun getInstance(context: Context): PendingDeleteManager {
            return INSTANCE ?: synchronized(this) {
                val instance = PendingDeleteManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
