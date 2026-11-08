package com.example.data.firebase

import android.content.Context
import com.example.data.entity.PendingOperationEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Additive protocols are enabled only after Rules and every workspace client are ready. */
data class WorkspaceSyncCapabilities(
    val expenseCategoriesV1: Boolean = false,
    val basicCustomerProfilesV1: Boolean = false
) {
    fun requireSupported(operation: PendingOperationEntity) {
        if ((operation.entityType == "EXPENSE_CATEGORY" && !expenseCategoriesV1) ||
            (operation.entityType == "CUSTOMER" && JSONObject(operation.payloadJson)
                .optBoolean("basicProfileCreate") && !basicCustomerProfilesV1))
            throw WorkspaceCapabilityPendingException()
    }

    companion object {
        fun fromWorkspace(data: Map<String, Any?>): WorkspaceSyncCapabilities {
            val flags = data["syncCapabilities"] as? Map<*, *> ?: emptyMap<Any, Any>()
            return WorkspaceSyncCapabilities(flags["expenseCategoriesV1"] == true,
                flags["basicCustomerProfilesV1"] == true)
        }
    }
}

/** Transient: retain the original queue action; never turn a rollout wait into rejection. */
class WorkspaceCapabilityPendingException : java.io.IOException(
    "Workspace upgrade is not enabled yet; original action remains pending without changes")

/** This cache enables offline UI only. Server membership, capability and Rules still guard uploads. */
class VerifiedWorkspaceCapabilityCache(context: Context) {
    private val prefs = context.getSharedPreferences("verified_workspace_capabilities", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow<Map<Pair<String, String>, WorkspaceSyncCapabilities>>(emptyMap())
    val states = mutable.asStateFlow()

    private fun key(uid: String, workspace: String) = java.security.MessageDigest.getInstance("SHA-256")
        .digest((uid + '\u0000' + workspace).toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    @Synchronized fun restore(uid: String, workspace: String): WorkspaceSyncCapabilities {
        if (uid.isBlank() || workspace.isBlank()) return WorkspaceSyncCapabilities()
        val data = runCatching { JSONObject(prefs.getString(key(uid, workspace), "{}").orEmpty()) }.getOrNull()
        val value = if (data?.optString("uid") == uid && data.optString("workspace") == workspace)
            WorkspaceSyncCapabilities(data.opt("expenseCategoriesV1") == true, data.opt("basicCustomerProfilesV1") == true)
        else WorkspaceSyncCapabilities()
        mutable.value = mutable.value + ((uid to workspace) to value)
        return value
    }

    /** Call only after a fresh, validated server membership and unchanged authenticated UID. */
    @Synchronized fun record(uid: String, workspace: String, value: WorkspaceSyncCapabilities) {
        require(uid.isNotBlank() && workspace.isNotBlank())
        val data = JSONObject().put("uid", uid).put("workspace", workspace)
            .put("expenseCategoriesV1", value.expenseCategoriesV1)
            .put("basicCustomerProfilesV1", value.basicCustomerProfilesV1)
        check(prefs.edit().putString(key(uid, workspace), data.toString()).commit()) { "Cannot persist workspace compatibility state" }
        mutable.value = mutable.value + ((uid to workspace) to value)
    }
}
