package com.example.data.auth

import com.example.data.firebase.WorkspaceMember

/** Record provenance is separate from the current actor's permission to issue a command. */
object RecordIdentityPolicy {
    const val REPAIR_REQUIRED = "A verified legacy-data repair is required. The record and queued actions have been retained."
    const val MEMBERSHIP_REQUIRED = "The record's creator or beneficiary has no active membership in this workspace. Refresh workspace access; removed members' records remain read-only under Account > Deleted Users."

    fun missingIdentityFields(creatorUid: String, targetUid: String): List<String> = buildList {
        if (creatorUid.isBlank()) add("creator")
        if (targetUid.isBlank()) add("beneficiary")
    }

    fun repairMessage(creatorUid: String, targetUid: String): String? {
        val missing = missingIdentityFields(creatorUid, targetUid)
        return if (missing.isEmpty()) null else "Missing ${missing.joinToString(" and ")} identity. $REPAIR_REQUIRED"
    }

    /** A saved name is display data, never proof of creator UID or current membership. */
    fun savedCreatorLabel(savedName: String, creatorUid: String, isTamil: Boolean = false): String {
        val label = savedName.trim().ifBlank { if (isTamil) "பெயர் பதிவு செய்யப்படவில்லை" else "Name not recorded" }
        return if (creatorUid.isBlank()) "$label (${if (isTamil) "அடையாளம் இல்லை" else "creator identity missing"})" else label
    }

    fun requireActive(workspaceId: String, creatorUid: String, targetUid: String,
        members: List<WorkspaceMember>) {
        require(workspaceId.isNotBlank()) { "Select and verify a workspace before changing a record" }
        repairMessage(creatorUid, targetUid)?.let { throw SecurityException(it) }
        if (listOf(creatorUid, targetUid).any { uid ->
            members.none { it.workspaceId == workspaceId && it.uid == uid && it.status.equals("ACTIVE", true) }
        }) throw SecurityException(MEMBERSHIP_REQUIRED)
    }
}
