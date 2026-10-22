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

    /**
     * Returns formatted "Created by: Role — Name" string.
     */
    fun getCreatedByDisplay(
        job: JobEntryEntity,
        ownerName: String = "",
        workspaceMembers: List<WorkspaceMember> = emptyList(),
        partners: List<PartnerEntity> = emptyList(),
        isTamil: Boolean = false
    ): String {
        val (role, name) = resolveCreatorRoleAndName(job, ownerName, workspaceMembers, partners)
        val roleAndName = formatRoleAndName(role, name, isTamil)
        val prefix = if (isTamil) "உருவாக்கியவர்" else "Created by"
        return "$prefix: $roleAndName"
    }

    /**
     * Returns formatted "Entry For: Role — Name" string, or null if no entry-for is specified.
     */
    fun getEntryForDisplay(
        job: JobEntryEntity,
        ownerName: String = "",
        workspaceMembers: List<WorkspaceMember> = emptyList(),
        partners: List<PartnerEntity> = emptyList(),
        isTamil: Boolean = false,
        hasBusinessPartnership: Boolean = false
    ): String? {
        val member = workspaceMembers.firstOrNull { it.uid == job.entryForUid && it.workspaceId == job.workspaceId && it.uid.isNotBlank() }
        val entryForName = member?.displayName?.takeIf { it.isNotBlank() } ?: job.operatorName.trim()
        if (entryForName.isBlank() || job.entryForUid.isBlank()) return null

        val entryForRole = member?.role?.let(::normalizeRole)
            ?: partners.firstOrNull { it.workspaceId == job.workspaceId && it.firebaseUid.isNotBlank() && it.firebaseUid == job.entryForUid }?.role?.let(::normalizeRole)
            ?: ""

        val displayRole = if (hasBusinessPartnership && entryForRole == ROLE_OWNER) ROLE_PARTNER else entryForRole
        val roleAndName = formatRoleAndName(displayRole, entryForName, isTamil)
        val prefix = if (isTamil) "யாருக்காக" else "Entry For"
        return "$prefix: $roleAndName"
    }

    /**
     * Returns formatted "Edited by: Role — Name" string, or null if job was not edited.
     */
    fun getEditedByDisplay(
        job: JobEntryEntity,
        isTamil: Boolean = false
    ): String? {
        val editedName = job.editedByName.trim()
        if (editedName.isBlank()) return null

        val role = normalizeRole(job.editedByRole)
        val roleAndName = formatRoleAndName(role, editedName, isTamil)
        val prefix = if (isTamil) "திருத்தியவர்" else "Edited by"
        return "$prefix: $roleAndName"
    }
}
