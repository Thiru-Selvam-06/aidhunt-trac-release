package com.example.data.firebase

import org.json.JSONObject

internal data class OwnerActivityDestination(val uid: String, val workspaceId: String, val eventId: String) {
    fun matches(currentUid: String?, currentWorkspace: String?, originalOwner: Boolean): Boolean =
        originalOwner && uid == currentUid && workspaceId == currentWorkspace
}

internal data class OwnerActivityItem(val id: String, val type: String, val actorName: String) {
    fun label(tamil: Boolean): String? {
        val actor = actorName.ifBlank { if (tamil) "பங்குதாரர்" else "A partner" }
        val action = when (type) {
            "JOB_CREATED" -> if (tamil) "புதிய வேலைப் பதிவைச் சேர்த்தார்." else "added a new job."
            "OLD_ENTRY_CREATED" -> if (tamil) "பழைய வேலைப் பதிவைச் சேர்த்தார்." else "added an old entry."
            "JOB_EDITED" -> if (tamil) "வேலைப் பதிவைத் திருத்தினார்." else "edited a job."
            "JOB_DELETED" -> if (tamil) "வேலைப் பதிவை நீக்கினார்." else "deleted a job."
            "COLLECTION" -> if (tamil) "வாடிக்கையாளர் வசூலைப் பதிவு செய்தார்." else "recorded a collection."
            "COLLECTION_REVERSED" -> if (tamil) "வசூலை ரத்து செய்தார்." else "reversed a collection."
            "EXPENSE" -> if (tamil) "செலவைப் பதிவு செய்தார்." else "recorded an expense."
            "EXPENSE_EDITED" -> if (tamil) "செலவுப் பதிவைத் திருத்தினார்." else "edited an expense."
            "EXPENSE_DELETED" -> if (tamil) "செலவுப் பதிவை நீக்கினார்." else "deleted an expense."
            "WITHDRAWAL" -> if (tamil) "பணம் எடுத்ததைப் பதிவு செய்தார்." else "recorded a withdrawal."
            else -> return null
        }
        return "$actor $action"
    }

    companion object {
        fun fromJson(data: JSONObject): OwnerActivityItem? {
            val id = data.optString("id").takeIf(OwnerActivityPolicy::validEvent) ?: return null
            val item = OwnerActivityItem(id, data.optString("type"), data.optString("actorName"))
            return item.takeIf { it.label(false) != null }
        }
    }
}

internal object OwnerActivityPolicy {
    private val identity = Regex("[A-Za-z0-9:_-]{1,160}")
    private val event = Regex("[a-f0-9]{64}")
    fun validIdentity(value: String) = identity.matches(value)
    fun validEvent(value: String) = event.matches(value)

    fun enabled(flag: Boolean, projectId: String?, packageName: String): Boolean =
        flag && projectId == "trac-aidhunt" &&
            packageName in setOf("com.aistudio.aidhunttrac.cedlir", "com.aistudio.aidhunttrac.cedlir.livetest")

    fun destination(uid: String, workspaceId: String, eventId: String): OwnerActivityDestination? =
        if (validIdentity(uid) && validIdentity(workspaceId) && validEvent(eventId)) {
            OwnerActivityDestination(uid, workspaceId, eventId)
        } else null
}
