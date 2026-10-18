package com.example.ui.util

import com.example.data.entity.WithdrawalEntity
import com.example.data.firebase.WorkspaceMember

data class WithdrawalParticipantAmount(val uid: String, val displayName: String, val amount: Double, val count: Int)
data class WithdrawalDistribution(
    val participants: List<WithdrawalParticipantAmount>,
    val unresolvedAmount: Double,
    val total: Double
)

/** Members must come from the existing active-workspace membership projection.
 * A missing target UID is unresolved, never inferred from a name or the creator.
 */
fun withdrawalDistribution(
    workspaceId: String,
    withdrawals: List<WithdrawalEntity>,
    activeMembers: List<WorkspaceMember>
): WithdrawalDistribution {
    val records = withdrawals.filter { workspaceId.isNotBlank() && it.workspaceId == workspaceId }
    val members = activeMembers.filter { it.uid.isNotBlank() && it.workspaceId == workspaceId && (it.status.equals("active", true) || it.status.equals("removed", true)) }
        .distinctBy { it.uid }.associateBy { it.uid }
    val grouped = records.groupBy { it.targetPartnerUid }
    val participants = members.values.mapNotNull { member ->
        val owned = grouped[member.uid] ?: return@mapNotNull null
        WithdrawalParticipantAmount(member.uid, member.displayName?.trim().orEmpty().ifBlank { member.uid },
            owned.sumOf { it.amount }, owned.size)
    }
    return WithdrawalDistribution(participants,
        records.filter { it.targetPartnerUid !in members }.sumOf { it.amount },
        records.sumOf { it.amount })
}
