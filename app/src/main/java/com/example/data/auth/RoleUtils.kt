package com.example.data.auth

import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.firebase.WorkspaceMember

object RoleUtils {

    const val ROLE_OWNER = "Owner"
    const val ROLE_CO_OWNER = "Co-Owner"
    const val ROLE_PARTNER = "Partner"
    const val ROLE_OPERATOR = "Operator"

    /**
     * Normalizes the three supported roles. Unknown and legacy Co-Owner roles have no authority.
     */
    fun normalizeRole(roleInput: String?): String {
        return when (roleInput?.trim()?.lowercase()) {
            "owner", "உரிமையாளர்" -> ROLE_OWNER
            "partner", "பங்குதாரர்" -> ROLE_PARTNER
            "operator", "இயக்குனர்", "இயக்குபவர்" -> ROLE_OPERATOR
            else -> ""
        }
    }

    fun isOwner(roleInput: String?): Boolean {
        return normalizeRole(roleInput) == ROLE_OWNER
    }

    /**
     * Returns the localized display name for a business role.
     */
    fun getRoleDisplayName(roleInput: String?, isTamil: Boolean): String {
        return when (normalizeRole(roleInput)) {
            ROLE_OWNER -> if (isTamil) "உரிமையாளர்" else "Owner"
            ROLE_CO_OWNER -> if (isTamil) "இணை உரிமையாளர்" else "Co-Owner"
            ROLE_OPERATOR -> if (isTamil) "இயக்குனர்" else "Operator"
            ROLE_PARTNER -> if (isTamil) "பங்குதாரர்" else "Partner"
            else -> if (isTamil) "சரிபார்க்கப்படாதது" else "Unverified"
        }
    }

    /**
     * Formats role and name into standard "Role — Name" format.
     * Example: "Owner — Thiru", "Co-Owner — Kumar", "Partner — Ravi", "Operator — Mani"
     */
    fun formatRoleAndName(roleInput: String?, name: String, isTamil: Boolean): String {
        val roleLabel = getRoleDisplayName(roleInput, isTamil)
        val cleanName = name.trim()
        return if (normalizeRole(roleInput).isBlank()) cleanName.ifBlank { roleLabel } else if (cleanName.isNotBlank()) "$roleLabel — $cleanName" else roleLabel
    }

    /**
     * Resolves the creator's role and name for a job entry.
     */
    fun resolveCreatorRoleAndName(
        job: JobEntryEntity,
        ownerName: String = "",
        workspaceMembers: List<WorkspaceMember> = emptyList(),
        partners: List<PartnerEntity> = emptyList()
    ): Pair<String, String> {
        val creatorName = job.addedByPartner.ifBlank { ownerName.ifBlank { "Owner" } }.trim()

        // 1. Explicit createdByRole stored on job
        if (job.createdByRole.isNotBlank()) {
            return Pair(normalizeRole(job.createdByRole), creatorName)
        }

        // 2. UID lookup in workspaceMembers
        if (job.createdByUid.isNotBlank()) {
            val member = workspaceMembers.find { it.uid == job.createdByUid }
            if (member != null && member.role.isNotBlank()) {
                return Pair(normalizeRole(member.role), creatorName)
            }
        }

        val matchedPartner = partners.firstOrNull { it.firebaseUid.isNotBlank() && it.firebaseUid == job.createdByUid }
        return Pair(matchedPartner?.role?.let(::normalizeRole).orEmpty(), creatorName)

    }

    fun getCreatedByName(
        job: JobEntryEntity,
        workspaceMembers: List<WorkspaceMember>,
        partners: List<PartnerEntity>,
        isTamil: Boolean
    ): String = memberName(job.workspaceId, job.createdByUid, workspaceMembers, partners)
        .ifBlank { job.addedByPartner.trim() }
        .ifBlank { if (isTamil) "பெயர் பதிவு செய்யப்படவில்லை" else "Name not recorded" }

    fun getCreatedByDisplay(
        job: JobEntryEntity,
        workspaceMembers: List<WorkspaceMember> = emptyList(),
        partners: List<PartnerEntity> = emptyList(),
        isTamil: Boolean = false
    ): String {
        val prefix = if (isTamil) "உருவாக்கியவர்" else "Entry by"
        return "$prefix: ${getCreatedByName(job, workspaceMembers, partners, isTamil)}"
    }

    fun getEntryForName(
        job: JobEntryEntity,
        workspaceMembers: List<WorkspaceMember>,
        partners: List<PartnerEntity>
    ): String? {
        if (job.entryForUid.isBlank()) return null
        val savedName = job.operatorName.trim()
        val name = memberName(job.workspaceId, job.entryForUid, workspaceMembers, partners)
        // An older job may contain a separately recorded driver. Only normalize casing for that snapshot.
        return (if (savedName.isBlank() || savedName.equals(name, ignoreCase = true)) name else savedName)
            .takeIf { it.isNotBlank() }
    }

    fun getEntryForDisplay(
        job: JobEntryEntity,
        workspaceMembers: List<WorkspaceMember> = emptyList(),
        partners: List<PartnerEntity> = emptyList(),
        isTamil: Boolean = false
    ): String? {
        val name = getEntryForName(job, workspaceMembers, partners) ?: return null
        val prefix = if (isTamil) "வேலை இயக்குபவர்" else "Entry for Operator"
        return "$prefix: $name"
    }

    private fun memberName(
        workspaceId: String,
        uid: String,
        workspaceMembers: List<WorkspaceMember>,
        partners: List<PartnerEntity>
    ): String {
        if (workspaceId.isBlank() || uid.isBlank()) return ""
        return workspaceMembers.firstOrNull { it.uid == uid && it.workspaceId == workspaceId }
            ?.displayName?.trim().orEmpty().ifBlank {
                partners.firstOrNull { it.firebaseUid == uid && it.workspaceId == workspaceId }
                    ?.name?.trim().orEmpty()
            }
    }

    /**
     * Returns formatted "Edited by: Role — Name" string, or null if job was not edited.
     */
    fun getEditedByDisplay(
        job: JobEntryEntity,
        isTamil: Boolean = false,
        hasBusinessPartnership: Boolean = false
    ): String? {
        val editedName = job.editedByName.trim()
        if (editedName.isBlank()) return null

        val role = normalizeRole(job.editedByRole)
        val displayRole = if (hasBusinessPartnership && role == ROLE_OWNER) ROLE_PARTNER else role
        val roleAndName = formatRoleAndName(displayRole, editedName, isTamil)
        val prefix = if (isTamil) "திருத்தியவர்" else "Edited by"
        return "$prefix: $roleAndName"
    }
}
