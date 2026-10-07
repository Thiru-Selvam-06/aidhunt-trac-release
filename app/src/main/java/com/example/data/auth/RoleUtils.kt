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
     * Normalizes any input role string into one of the four business roles:
     * Owner, Co-Owner, Partner, or Operator.
     */
    fun normalizeRole(roleInput: String?): String {
        if (roleInput.isNullOrBlank()) return ROLE_PARTNER
        val trimmed = roleInput.trim()
        return when {
            trimmed.contains("co", ignoreCase = true) || trimmed.contains("இணை", ignoreCase = true) -> ROLE_CO_OWNER
            trimmed.contains("owner", ignoreCase = true) || trimmed.contains("உரிமையாளர்", ignoreCase = true) -> ROLE_OWNER
            trimmed.contains("operator", ignoreCase = true) || trimmed.contains("இயக்குனர்", ignoreCase = true) || trimmed.contains("இயக்குபவர்", ignoreCase = true) -> ROLE_OPERATOR
            else -> ROLE_PARTNER
        }
    }

    fun isOwner(roleInput: String?): Boolean {
        val norm = normalizeRole(roleInput)
        return norm == ROLE_OWNER || norm == ROLE_CO_OWNER
    }

    /**
     * Returns the localized display name for a business role.
     */
    fun getRoleDisplayName(roleInput: String?, isTamil: Boolean): String {
        return when (normalizeRole(roleInput)) {
            ROLE_OWNER -> if (isTamil) "உரிமையாளர்" else "Owner"
            ROLE_CO_OWNER -> if (isTamil) "இணை உரிமையாளர்" else "Co-Owner"
            ROLE_OPERATOR -> if (isTamil) "இயக்குனர்" else "Operator"
            else -> if (isTamil) "பங்குதாரர்" else "Partner"
        }
    }

    /**
     * Formats role and name into standard "Role — Name" format.
     * Example: "Owner — Thiru", "Co-Owner — Kumar", "Partner — Ravi", "Operator — Mani"
     */
    fun formatRoleAndName(roleInput: String?, name: String, isTamil: Boolean): String {
        val roleLabel = getRoleDisplayName(roleInput, isTamil)
        val cleanName = name.trim()
        return if (cleanName.isNotBlank()) "$roleLabel — $cleanName" else roleLabel
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

        // 3. Name match with owner
        if (ownerName.isNotBlank() && creatorName.equals(ownerName.trim(), ignoreCase = true)) {
            return Pair(ROLE_OWNER, creatorName)
        }

        // 4. Name match in workspaceMembers
        val matchedMember = workspaceMembers.find {
            it.displayName?.trim()?.equals(creatorName, ignoreCase = true) == true ||
            it.phoneNumber?.filter { ch -> ch.isDigit() }?.takeLast(10) == creatorName.filter { ch -> ch.isDigit() }.takeLast(10)
        }
        if (matchedMember != null && matchedMember.role.isNotBlank()) {
            return Pair(normalizeRole(matchedMember.role), creatorName)
        }

        // 5. Name match in local partners
        val matchedPartner = partners.find { it.name.trim().equals(creatorName, ignoreCase = true) }
        if (matchedPartner != null && matchedPartner.role.isNotBlank()) {
            return Pair(normalizeRole(matchedPartner.role), creatorName)
        }

        // 6. Fallback: Default to Owner if name matches owner or is blank, otherwise Partner
        val isDefaultOwner = creatorName.isBlank() || (ownerName.isNotBlank() && creatorName.equals(ownerName, ignoreCase = true))
        val fallbackRole = if (isDefaultOwner) ROLE_OWNER else ROLE_PARTNER
        return Pair(fallbackRole, creatorName)
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
        isTamil: Boolean = false
    ): String? {
        val entryForName = job.operatorName.trim()
        if (entryForName.isBlank()) return null

        val entryForRole = when {
            ownerName.isNotBlank() && entryForName.equals(ownerName.trim(), ignoreCase = true) -> ROLE_OWNER
            else -> {
                val member = workspaceMembers.find {
                    it.displayName?.trim()?.equals(entryForName, ignoreCase = true) == true
                }
                if (member != null && member.role.isNotBlank()) {
                    normalizeRole(member.role)
                } else {
                    val partner = partners.find { it.name.trim().equals(entryForName, ignoreCase = true) }
                    if (partner != null && partner.role.isNotBlank()) {
                        normalizeRole(partner.role)
                    } else {
                        ROLE_OPERATOR
                    }
                }
            }
        }

        val roleAndName = formatRoleAndName(entryForRole, entryForName, isTamil)
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
