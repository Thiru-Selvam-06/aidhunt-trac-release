package com.example.ui.util

import com.example.data.auth.RoleUtils
import com.example.data.firebase.WorkspaceMember

/** Chart-only rows: every authorized financial member remains visible, including zero activity. */
internal fun businessOverviewChartMembers(
    breakdowns: List<PartnerFinancialBreakdown>,
    members: List<WorkspaceMember>,
    currentUid: String,
    workspaceId: String
): List<PartnerFinancialBreakdown> {
    if (currentUid.isBlank() || workspaceId.isBlank()) return emptyList()
    val eligible = members.filter {
        it.uid.isNotBlank() && it.workspaceId == workspaceId && it.status.equals("ACTIVE", true) &&
            RoleUtils.normalizeRole(it.role) in setOf(RoleUtils.ROLE_OWNER, RoleUtils.ROLE_PARTNER)
    }.distinctBy { it.uid }
    val self = eligible.firstOrNull { it.uid == currentUid } ?: return emptyList()
    val knownIds = members.filter { it.workspaceId == workspaceId &&
        (it.status.equals("ACTIVE", true) || it.status.equals("REMOVED", true)) }.map { it.uid }.toSet()
    val rows = breakdowns.filter { it.partnerUid.isNotBlank() && it.partnerUid in knownIds }
        .distinctBy { it.partnerUid }.toMutableList()
    eligible.forEach { member ->
        if (rows.none { it.partnerUid == member.uid }) rows.add(PartnerFinancialBreakdown(
            partnerUid = member.uid,
            partnerName = member.displayName?.takeIf { it.isNotBlank() } ?: "Member",
            role = RoleUtils.normalizeRole(member.role)
        ))
    }
    // Never replace existing figures or assign unresolved records to an idle member.
    return rows.filter { it.partnerUid == self.uid } + rows.filterNot { it.partnerUid == self.uid }
}
