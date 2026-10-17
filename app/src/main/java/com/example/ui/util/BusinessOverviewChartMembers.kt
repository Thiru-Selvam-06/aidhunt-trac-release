package com.example.ui.util

import com.example.data.firebase.WorkspaceMember

/** Chart-only presentation: include the verified viewer even with no activity, without changing totals. */
internal fun businessOverviewChartMembers(
    breakdowns: List<PartnerFinancialBreakdown>,
    members: List<WorkspaceMember>,
    currentUid: String,
    workspaceId: String
): List<PartnerFinancialBreakdown> {
    if (currentUid.isBlank() || workspaceId.isBlank()) return breakdowns
    val self = members.firstOrNull {
        it.uid == currentUid && it.workspaceId == workspaceId && it.status.equals("ACTIVE", true)
    } ?: return breakdowns
    val existing = breakdowns.firstOrNull { it.partnerUid == currentUid }
    val ownCircle = existing ?: PartnerFinancialBreakdown(
        partnerUid = self.uid,
        partnerName = self.displayName?.takeIf { it.isNotBlank() }
            ?: self.email?.takeIf { it.isNotBlank() } ?: "Member",
        role = if (self.role.equals("OWNER", true)) "Owner" else self.role
    )
    // The horizontal chart may extend beyond the viewport; self must be visible immediately.
    return listOf(ownCircle) + breakdowns.filterNot { it.partnerUid == currentUid }
}
