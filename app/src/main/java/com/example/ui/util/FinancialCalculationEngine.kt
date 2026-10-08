package com.example.ui.util

import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.firebase.ProfitShareAllocation
import com.example.data.firebase.WorkspaceMember
import java.util.Calendar

enum class FinancialScopeMode(val labelEn: String, val labelTa: String) {
    OVERALL("Overall", "மொத்தம்"),
    DAILY("Daily", "தினசரி"),
    MONTHLY("Monthly", "மாதாந்திர"),
    YEARLY("Yearly", "வருடாந்திர")
}

data class FinancialScope(
    val mode: FinancialScopeMode = FinancialScopeMode.OVERALL,
    val dateMillis: Long = System.currentTimeMillis(),
    val year: Int = Calendar.getInstance().get(Calendar.YEAR),
    val month: Int = Calendar.getInstance().get(Calendar.MONTH) + 1 // 1..12
)

data class FinancialSummary(
    val totalRecorded: Double = 0.0,
    val totalReceived: Double = 0.0,
    val totalDue: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val totalWithdrawals: Double = 0.0,
    val availableBalance: Double = 0.0,
    val jobCount: Int = 0,
    val expenseCount: Int = 0,
    val withdrawalCount: Int = 0
)

data class PartnerFinancialBreakdown(
    val partnerUid: String = "",
    val partnerName: String,
    val role: String = "Partner",
    val recorded: Double = 0.0,
    val received: Double = 0.0,
    val due: Double = 0.0,
    val expenses: Double = 0.0,
    val withdrawals: Double = 0.0
)

data class PartnerShareInvestmentStatus(
    val partnerName: String,
    val role: String = "Partner",
    val totalShare: Double = 0.0,
    val takenShare: Double = 0.0,
    val remainingShare: Double = 0.0,
    val totalInvestment: Double = 0.0,
    val takenInvestment: Double = 0.0,
    val remainingInvestment: Double = 0.0,
    val takenPersonalUse: Double = 0.0,
    val totalToGet: Double = 0.0,
    val partnerUid: String = "",
    val phone: String = "",
    val percentage: Int = 0
)

object FinancialCalculationEngine {

    fun calculatePartnerShareAndInvestment(
        jobs: List<JobEntryEntity>,
        expenses: List<ExpenseEntity>,
        withdrawals: List<WithdrawalEntity>,
        partners: List<PartnerEntity> = emptyList(),
        workspaceMembers: List<WorkspaceMember> = emptyList(),
        profitShareAllocations: Map<String, Int> = emptyMap(),
        profitShareAllocationDetails: Map<String, ProfitShareAllocation> = emptyMap(),
        scope: FinancialScope = FinancialScope(FinancialScopeMode.OVERALL),
        businessName: String = ""
    ): List<PartnerShareInvestmentStatus> {
        val scopedJobs = jobs.filter { job ->
            val ts = if (job.startTimeMillis > 0) job.startTimeMillis else job.createdAt
            isTimestampInScope(ts, scope)
        }
        val scopedExpenses = expenses.filter { exp ->
            val ts = if (exp.dateTimestamp > 0) exp.dateTimestamp else exp.createdAt
            isTimestampInScope(ts, scope)
        }
        val scopedWithdrawals = withdrawals.filter { w ->
            val ts = if (w.timestamp > 0) w.timestamp else w.createdAt
            isTimestampInScope(ts, scope)
        }

        // 1. Distributable Profit calculation:
        // Gross business amount (total received from jobs) - Total operating expenses
        val totalReceived = roundToPositiveWholeRupee(scopedJobs.sumOf { it.amountReceived })
        val totalExpenses = roundToPositiveWholeRupee(scopedExpenses.sumOf { it.amount })
        val distributableAmount = maxOf(0.0, totalReceived - totalExpenses)

        // 2. Determine if a valid custom allocation exists (total sum must be 100)
        val hasCustomAllocation = profitShareAllocations.isNotEmpty() && profitShareAllocations.values.sum() == 100

        // 3. Discover authoritative eligible participants from workspace members (and local partner fallback)
        // Explicitly exclude Operators and Business Name
        val eligibleParticipants = mutableListOf<ParticipantInfo>()
        val processedUids = mutableSetOf<String>()
        val processedNames = mutableSetOf<String>()
        val bizName = businessName.trim()

        workspaceMembers.forEach { member ->
            if (member.status.isBlank() || member.status.equals("active", ignoreCase = true)) {
                val normalizedRole = com.example.data.auth.RoleUtils.normalizeRole(member.role)
                if (normalizedRole != com.example.data.auth.RoleUtils.ROLE_OPERATOR) {
                    val isOwnerRole = normalizedRole == com.example.data.auth.RoleUtils.ROLE_OWNER || normalizedRole == com.example.data.auth.RoleUtils.ROLE_CO_OWNER
                    val detail = profitShareAllocationDetails[member.uid]
                    val rawName = detail?.displayName?.takeIf { it.isNotBlank() } ?: member.displayName ?: member.email ?: if (isOwnerRole) "Owner" else "Partner"
                    val cleanName = rawName.trim()
                    if (cleanName.isNotBlank() && (bizName.isBlank() || !cleanName.equals(bizName, ignoreCase = true))) {
                        val uid = member.uid.ifBlank { "member_${member.phoneNumber ?: cleanName}" }
                        if (!processedUids.contains(uid)) {
                            processedUids.add(uid)
                            processedNames.add(cleanName.lowercase())
                            val configuredPct = if (hasCustomAllocation) (profitShareAllocations[uid] ?: 0) else 0
                            eligibleParticipants.add(
                                ParticipantInfo(
                                    uid = uid,
                                    displayName = cleanName,
                                    role = if (isOwnerRole) "Owner" else "Partner",
                                    percentage = configuredPct,
                                    phoneNumber = member.phoneNumber,
                                    isActive = detail?.isActive ?: (configuredPct > 0 || !hasCustomAllocation)
                                )
                            )
                        }
                    }
                }
            }
        }

        // Local partners fallback (if not in workspace members, excluding operators and business name)
        partners.forEach { partner ->
            val pName = partner.name.trim()
            val isOperator = partner.role.equals("Operator", ignoreCase = true)
            if (pName.isNotBlank() && !isOperator && (bizName.isBlank() || !pName.equals(bizName, ignoreCase = true))) {
                val matchingMember = workspaceMembers.find { m ->
                    m.phoneNumber?.filter { it.isDigit() }?.takeLast(10) == partner.phone.filter { it.isDigit() }?.takeLast(10)
                }
                if (matchingMember == null && !processedNames.contains(pName.lowercase())) {
                    val pUid = "partner_${partner.id}"
                    if (!processedUids.contains(pUid)) {
                        processedUids.add(pUid)
                        processedNames.add(pName.lowercase())
                        val configuredPct = if (hasCustomAllocation) (profitShareAllocations[pUid] ?: 0) else 0
                        eligibleParticipants.add(
                            ParticipantInfo(
                                uid = pUid,
                                displayName = pName,
                                role = partner.role.ifBlank { "Partner" },
                                percentage = configuredPct,
                                phoneNumber = partner.phone,
                                isActive = configuredPct > 0 || !hasCustomAllocation
                            )
                        )
                    }
                }
            }
        }

        // 4. Calculate shares:
        // When valid custom allocation exists:
        //   - Participant share = distributableAmount * percentage / 100
        //   - Unselected participants (percentage == 0) receive exactly 0% share
        //   - Exact monetary reconciliation ensures total allocated equals distributable amount exactly
        // When no custom allocation exists (legacy equal split):
        //   - Divide equally among all eligible participants with whole-rupee reconciliation
        val participantsWithShares = mutableListOf<ParticipantInfo>()

        if (hasCustomAllocation) {
            eligibleParticipants.forEach { info ->
                val pct = maxOf(0, info.percentage)
                val shareAmount = if (distributableAmount > 0 && pct > 0) {
                    roundToWholeRupee(distributableAmount * pct / 100.0)
                } else {
                    0.0
                }
                participantsWithShares.add(info.copy(calculatedShare = shareAmount, effectivePercentage = pct))
            }

            // Monetary reconciliation for rounding differences
            val distributedSum = participantsWithShares.filter { it.effectivePercentage > 0 }.sumOf { it.calculatedShare }
            val diff = if (distributableAmount > 0) distributableAmount - distributedSum else 0.0
            if (diff != 0.0) {
                val targetIndex = participantsWithShares.indices
                    .filter { participantsWithShares[it].effectivePercentage > 0 }
                    .maxByOrNull { participantsWithShares[it].effectivePercentage }
                if (targetIndex != null) {
                    val target = participantsWithShares[targetIndex]
                    participantsWithShares[targetIndex] = target.copy(
                        calculatedShare = maxOf(0.0, target.calculatedShare + diff)
                    )
                }
            }
        } else {
            val count = eligibleParticipants.size
            val basePct = if (count > 0) 100 / count else 0
            val remPct = if (count > 0) 100 % count else 0
            val baseShare = if (count > 0 && distributableAmount > 0) roundToWholeRupee(distributableAmount / count) else 0.0
            val rawShares = MutableList(count) { baseShare }
            val diff = if (distributableAmount > 0) distributableAmount - rawShares.sum() else 0.0
            if (diff != 0.0 && count > 0) {
                rawShares[0] = maxOf(0.0, rawShares[0] + diff)
            }

            eligibleParticipants.forEachIndexed { index, info ->
                val effectivePct = basePct + (if (index < remPct) 1 else 0)
                participantsWithShares.add(info.copy(calculatedShare = rawShares[index], effectivePercentage = effectivePct))
            }
        }

        // 5. Build PartnerShareInvestmentStatus for each participant
        return participantsWithShares.map { participant ->
            val pName = participant.displayName
            val pRole = participant.role
            val pUid = participant.uid
            val pPhone = participant.phoneNumber ?: ""
            val totalShare = participant.calculatedShare
            val effectivePct = participant.effectivePercentage

            // Total Investment: Sum of all expenses paid personally by this partner ("I Paid")
            // Match by paidByUid first, then by display name
            val pInvestments = scopedExpenses.filter { exp ->
                val paidByMatches = if (exp.paidByUid.isNotBlank() && pUid.isNotBlank()) {
                    exp.paidByUid == pUid
                } else if (pUid.isNotBlank()) {
                    exp.paidByPartner.isNotBlank() && exp.paidByPartner.equals(pName, ignoreCase = true)
                } else {
                    exp.paidByPartner.equals(pName, ignoreCase = true) ||
                    exp.addedByPartner.equals(pName, ignoreCase = true) ||
                    exp.operatorName.equals(pName, ignoreCase = true)
                }
                exp.paidBy.equals("I Paid", ignoreCase = true) && paidByMatches
            }
            val totalInvestment = roundToPositiveWholeRupee(pInvestments.sumOf { it.amount })

            // Partner withdrawals - match by UID if available, otherwise by name
            val pWithdrawals = scopedWithdrawals.filter { w ->
                if (pUid.isNotBlank()) {
                    w.targetPartnerUid.equals(pUid, ignoreCase = true) ||
                    w.partnerName.equals(pName, ignoreCase = true)
                } else {
                    w.partnerName.equals(pName, ignoreCase = true)
                }
            }

            // Strict category-based deductions:
            // 1. "Share" -> deductions ONLY from Profit Share
            // 2. "Investment" -> deductions ONLY from Investment
            // 3. "Share and Investment" -> strict 50/50 split between Profit Share and Investment
            //    For odd rupee amounts, deterministic reconciliation: ceil(amt/2) to Share, rest to Investment
            // 4. "Personal Use" / "Advance" -> tracks takenPersonalUse, reduces totalToGet, does NOT reduce remainingShare/remainingInvestment
            var pTakenShare = 0.0
            var pTakenInvestment = 0.0
            var pTakenPersonalUse = 0.0

            pWithdrawals.forEach { w ->
                val cat = w.category.trim().lowercase()
                when {
                    cat == "share & investment" || cat == "share and investment" || cat.contains("share & investment") -> {
                        val sharePart = Math.ceil(w.amount / 2.0)
                        val invPart = w.amount - sharePart
                        pTakenShare += sharePart
                        pTakenInvestment += invPart
                    }
                    cat.contains("investment") || cat.contains("reimburse") -> {
                        pTakenInvestment += w.amount
                    }
                    cat.contains("share") || cat.contains("withdrawal") -> {
                        pTakenShare += w.amount
                    }
                    cat.contains("personal use") || cat.contains("advance") -> {
                        pTakenPersonalUse += w.amount
                    }
                    else -> {
                        pTakenShare += w.amount
                    }
                }
            }

            val takenShare = roundToPositiveWholeRupee(pTakenShare)
            val takenInvestment = roundToPositiveWholeRupee(pTakenInvestment)
            val takenPersonalUse = roundToPositiveWholeRupee(pTakenPersonalUse)

            val remainingShare = maxOf(0.0, totalShare - takenShare)
            val remainingInvestment = maxOf(0.0, totalInvestment - takenInvestment)
            val totalToGet = maxOf(0.0, remainingShare + remainingInvestment - takenPersonalUse)

            PartnerShareInvestmentStatus(
                partnerName = pName,
                role = pRole,
                totalShare = totalShare,
                takenShare = takenShare,
                remainingShare = remainingShare,
                totalInvestment = totalInvestment,
                takenInvestment = takenInvestment,
                remainingInvestment = remainingInvestment,
                takenPersonalUse = takenPersonalUse,
                totalToGet = totalToGet,
                partnerUid = pUid,
                phone = pPhone,
                percentage = effectivePct
            )
        }
    }

    // Internal data class for participant info with allocation
    private data class ParticipantInfo(
        val uid: String,
        val displayName: String,
        val role: String,
        val percentage: Int,
        val phoneNumber: String?,
        val isActive: Boolean,
        val calculatedShare: Double = 0.0,
        val effectivePercentage: Int = 0
    ) {
        fun copy(
            calculatedShare: Double = this.calculatedShare,
            effectivePercentage: Int = this.effectivePercentage
        ) = ParticipantInfo(
            uid = this.uid,
            displayName = this.displayName,
            role = this.role,
            percentage = this.percentage,
            phoneNumber = this.phoneNumber,
            isActive = this.isActive,
            calculatedShare = calculatedShare,
            effectivePercentage = effectivePercentage
        )
    }

    fun isTimestampInScope(timestamp: Long, scope: FinancialScope): Boolean {
        if (scope.mode == FinancialScopeMode.OVERALL) return true
        if (timestamp <= 0) return false

        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val targetCal = Calendar.getInstance()

        return when (scope.mode) {
            FinancialScopeMode.OVERALL -> true
            FinancialScopeMode.DAILY -> {
                targetCal.timeInMillis = scope.dateMillis
                cal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR) &&
                        cal.get(Calendar.DAY_OF_YEAR) == targetCal.get(Calendar.DAY_OF_YEAR)
            }
            FinancialScopeMode.MONTHLY -> {
                cal.get(Calendar.YEAR) == scope.year &&
                        (cal.get(Calendar.MONTH) + 1) == scope.month
            }
            FinancialScopeMode.YEARLY -> {
                cal.get(Calendar.YEAR) == scope.year
            }
        }
    }

    fun roundToWholeRupee(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        val rounded = Math.round(value).toDouble()
        return if (rounded == -0.0) 0.0 else rounded
    }

    fun roundToPositiveWholeRupee(value: Double): Double {
        val rounded = roundToWholeRupee(value)
        return if (rounded <= 0.0) 0.0 else rounded
    }

    fun isSyntheticPayment(job: JobEntryEntity): Boolean {
        return job.tractorLabel.equals("Payment", ignoreCase = true) ||
                job.workType.equals("Payment Received", ignoreCase = true)
    }

    fun calculateSummary(
        jobs: List<JobEntryEntity>,
        expenses: List<ExpenseEntity>,
        withdrawals: List<WithdrawalEntity>,
        scope: FinancialScope,
        payments: List<com.example.data.entity.PaymentEntity> = emptyList()
    ): FinancialSummary {
        val scopedJobs = jobs.filter { job ->
            val ts = if (job.startTimeMillis > 0) job.startTimeMillis else job.createdAt
            isTimestampInScope(ts, scope)
        }

        val scopedExpenses = expenses.filter { exp ->
            val ts = if (exp.dateTimestamp > 0) exp.dateTimestamp else exp.createdAt
            isTimestampInScope(ts, scope)
        }

        val scopedWithdrawals = withdrawals.filter { w ->
            val ts = if (w.timestamp > 0) w.timestamp else w.createdAt
            isTimestampInScope(ts, scope)
        }

        val scopedPayments = payments.filter { p ->
            val ts = p.collectedAt
            isTimestampInScope(ts, scope)
        }

        val totalRecorded = roundToPositiveWholeRupee(scopedJobs.sumOf { job ->
            if (isSyntheticPayment(job)) {
                0.0
            } else if (job.totalAmount > 0.0) {
                job.totalAmount
            } else {
                job.amountReceived + job.pendingAmount
            }
        })

        val rawJobReceived = scopedJobs.sumOf { j -> j.amountReceived }
        val rawPaymentReceived = scopedPayments.sumOf { p -> p.amount }

        val totalReceived = if (payments.isNotEmpty()) {
            val paymentJobIds = payments.map { it.jobEntryId }.filter { it > 0L }.toSet()
            val initialAdvanceFromJobsNoPayment = scopedJobs.sumOf { j ->
                if (!paymentJobIds.contains(j.id) && !isSyntheticPayment(j)) j.amountReceived else 0.0
            }
            roundToPositiveWholeRupee(rawPaymentReceived + initialAdvanceFromJobsNoPayment)
        } else {
            roundToPositiveWholeRupee(rawJobReceived)
        }

        val totalDue = roundToPositiveWholeRupee(scopedJobs.sumOf { job ->
            if (isSyntheticPayment(job)) {
                0.0
            } else if (job.pendingAmount > 0.0) {
                job.pendingAmount
            } else {
                maxOf(0.0, job.totalAmount - job.amountReceived)
            }
        })

        val totalExpenses = roundToPositiveWholeRupee(scopedExpenses.sumOf { it.amount })

        val totalWithdrawals = roundToPositiveWholeRupee(scopedWithdrawals.sumOf { it.amount })

        val availableBalance = maxOf(0.0, roundToWholeRupee(totalReceived - totalExpenses - totalWithdrawals))

        val jobCount = scopedJobs.count { !isSyntheticPayment(it) }

        return FinancialSummary(
            totalRecorded = totalRecorded,
            totalReceived = totalReceived,
            totalDue = totalDue,
            totalExpenses = totalExpenses,
            totalWithdrawals = totalWithdrawals,
            availableBalance = availableBalance,
            jobCount = jobCount,
            expenseCount = scopedExpenses.size,
            withdrawalCount = scopedWithdrawals.size
        )
    }

    fun calculatePartnerBreakdown(
        jobs: List<JobEntryEntity>,
        expenses: List<ExpenseEntity>,
        withdrawals: List<WithdrawalEntity>,
        partners: List<PartnerEntity>,
        workspaceMembers: List<WorkspaceMember>,
        scope: FinancialScope,
        payments: List<com.example.data.entity.PaymentEntity> = emptyList()
    ): List<PartnerFinancialBreakdown> {
        val scopedJobs = jobs.filter { job ->
            val ts = if (job.startTimeMillis > 0) job.startTimeMillis else job.createdAt
            isTimestampInScope(ts, scope)
        }
        val scopedExpenses = expenses.filter { exp ->
            val ts = if (exp.dateTimestamp > 0) exp.dateTimestamp else exp.createdAt
            isTimestampInScope(ts, scope)
        }
        val scopedWithdrawals = withdrawals.filter { w ->
            val ts = if (w.timestamp > 0) w.timestamp else w.createdAt
            isTimestampInScope(ts, scope)
        }

        // Discover authoritative active participants strictly from the current workspace
        data class MemberIdentity(
            val uid: String,
            val currentName: String,
            val role: String,
            val phone: String?
        )

        val activeParticipants = mutableListOf<MemberIdentity>()
        val seenUids = mutableSetOf<String>()

        // 1. Authoritative: workspaceMembers (active members only)
        workspaceMembers.forEach { m ->
            if (m.status.isBlank() || m.status.equals("active", ignoreCase = true)) {
                val dName = m.displayName?.takeIf { it.isNotBlank() } ?: m.phoneNumber?.takeIf { it.isNotBlank() } ?: m.email ?: ""
                val normalizedRole = com.example.data.auth.RoleUtils.normalizeRole(m.role)
                val roleDisplay = if (normalizedRole == com.example.data.auth.RoleUtils.ROLE_OWNER || normalizedRole == com.example.data.auth.RoleUtils.ROLE_CO_OWNER) {
                    "Owner"
                } else if (normalizedRole == com.example.data.auth.RoleUtils.ROLE_OPERATOR) {
                    "Operator"
                } else {
                    "Partner"
                }
                val uid = m.uid.ifBlank { "member_${m.phoneNumber ?: dName}" }
                if (dName.isNotBlank() && !seenUids.contains(uid)) {
                    seenUids.add(uid)
                    activeParticipants.add(MemberIdentity(uid = uid, currentName = dName, role = roleDisplay, phone = m.phoneNumber))
                }
            }
        }

        // 2. Fallback/Inclusion: local partners (even if workspaceMembers not empty)
        partners.forEach { p ->
            val pName = p.name.trim()
            if (pName.isNotBlank()) {
                val uid = "partner_${p.id}"
                if (!seenUids.contains(uid)) {
                    seenUids.add(uid)
                    activeParticipants.add(MemberIdentity(uid = uid, currentName = pName, role = p.role.ifBlank { "Partner" }, phone = p.phone))
                }
            }
        }

        // 3. Fallback: discover participants from records if both workspaceMembers and partners are empty
        if (activeParticipants.isEmpty()) {
            scopedJobs.forEach { j ->
                val pName = j.operatorName.ifBlank { j.addedByPartner }.trim()
                if (pName.isNotBlank() && !seenUids.contains(pName.lowercase())) {
                    seenUids.add(pName.lowercase())
                    activeParticipants.add(MemberIdentity(uid = j.createdByUid.ifBlank { pName }, currentName = pName, role = "Partner", phone = null))
                }
            }
            scopedExpenses.forEach { exp ->
                val pName = (if (exp.paidBy == "I Paid" && exp.paidByPartner.isNotBlank()) exp.paidByPartner else exp.operatorName.ifBlank { exp.addedByPartner }).trim()
                if (pName.isNotBlank() && !seenUids.contains(pName.lowercase())) {
                    seenUids.add(pName.lowercase())
                    activeParticipants.add(MemberIdentity(uid = exp.paidByUid.ifBlank { exp.createdByUid.ifBlank { pName } }, currentName = pName, role = "Partner", phone = null))
                }
            }
            scopedWithdrawals.forEach { w ->
                val pName = w.partnerName.trim()
                if (pName.isNotBlank() && !seenUids.contains(pName.lowercase())) {
                    seenUids.add(pName.lowercase())
                    activeParticipants.add(MemberIdentity(uid = w.targetPartnerUid.ifBlank { pName }, currentName = pName, role = "Partner", phone = null))
                }
            }
        }

        // Resolve job attribution by UID first, then matching active member
        fun resolveJobMember(j: JobEntryEntity): MemberIdentity? {
            if (j.createdByUid.isNotBlank()) {
                val match = activeParticipants.find { it.uid == j.createdByUid }
                if (match != null) return match
            }
            val opName = j.operatorName.ifBlank { j.addedByPartner }.trim()
            if (opName.isNotBlank()) {
                val match = activeParticipants.find { it.currentName.equals(opName, ignoreCase = true) }
                if (match != null) return match
            }
            return null
        }

        // Resolve expense attribution by UID first, then matching active member
        fun resolveExpenseMember(e: ExpenseEntity): MemberIdentity? {
            val effectiveUid = if (e.paidBy == "I Paid" && e.paidByUid.isNotBlank()) e.paidByUid else e.createdByUid
            if (effectiveUid.isNotBlank()) {
                val match = activeParticipants.find { it.uid == effectiveUid }
                if (match != null) return match
            }
            val pName = (if (e.paidBy == "I Paid" && e.paidByPartner.isNotBlank()) e.paidByPartner else e.operatorName.ifBlank { e.addedByPartner }).trim()
            if (pName.isNotBlank()) {
                val match = activeParticipants.find { it.currentName.equals(pName, ignoreCase = true) }
                if (match != null) return match
            }
            return null
        }

        // Resolve withdrawal attribution by UID first, then matching active member
        fun resolveWithdrawalMember(w: WithdrawalEntity): MemberIdentity? {
            if (w.targetPartnerUid.isNotBlank()) {
                val match = activeParticipants.find { it.uid == w.targetPartnerUid }
                if (match != null) return match
            }
            if (w.partnerName.isNotBlank()) {
                val match = activeParticipants.find { it.currentName.equals(w.partnerName.trim(), ignoreCase = true) }
                if (match != null) return match
            }
            return null
        }

        return activeParticipants.map { member ->
            val pJobs = scopedJobs.filter { resolveJobMember(it)?.uid == member.uid }
            val pExp = scopedExpenses.filter { resolveExpenseMember(it)?.uid == member.uid }
            val pWth = scopedWithdrawals.filter { resolveWithdrawalMember(it)?.uid == member.uid }

            val recorded = roundToPositiveWholeRupee(pJobs.sumOf {
                if (isSyntheticPayment(it)) 0.0
                else if (it.totalAmount > 0.0) it.totalAmount
                else (it.amountReceived + it.pendingAmount)
            })
            val received = roundToPositiveWholeRupee(pJobs.sumOf { it.amountReceived })
            val due = roundToPositiveWholeRupee(pJobs.sumOf {
                if (isSyntheticPayment(it)) 0.0
                else if (it.pendingAmount > 0.0) it.pendingAmount
                else maxOf(0.0, it.totalAmount - it.amountReceived)
            })
            val exp = roundToPositiveWholeRupee(pExp.sumOf { it.amount })
            val wth = roundToPositiveWholeRupee(pWth.sumOf { it.amount })

            PartnerFinancialBreakdown(
                partnerUid = member.uid,
                partnerName = member.currentName,
                role = member.role,
                recorded = recorded,
                received = received,
                due = due,
                expenses = exp,
                withdrawals = wth
            )
        }.filter { it.recorded > 0 || it.received > 0 || it.due > 0 || it.expenses > 0 || it.withdrawals > 0 }
         .sortedByDescending { it.recorded + it.received }
    }

    data class CustomerFinancialBreakdown(
        val totalBilled: Double = 0.0,
        val totalPaid: Double = 0.0,
        val balanceDue: Double = 0.0
    )

    fun calculateCustomerFinancials(
        customer: CustomerEntity,
        jobs: List<JobEntryEntity>
    ): CustomerFinancialBreakdown {
        val customerJobs = jobs.filter {
            it.customerId == customer.id ||
            (customer.phone.isNotBlank() && it.customerPhone == customer.phone) ||
            (customer.name.isNotBlank() && it.customerName.equals(customer.name, ignoreCase = true))
        }

        if (customerJobs.isEmpty()) {
            return CustomerFinancialBreakdown(
                totalBilled = roundToPositiveWholeRupee(customer.totalBilled),
                totalPaid = roundToPositiveWholeRupee(customer.totalPaid),
                balanceDue = roundToPositiveWholeRupee(customer.balanceDue)
            )
        }

        val workJobs = customerJobs.filterNot { isSyntheticPayment(it) }
        val billed = workJobs.sumOf { job ->
            if (job.totalAmount > 0.0) job.totalAmount else (job.amountReceived + job.pendingAmount)
        }
        val paid = customerJobs.sumOf { it.amountReceived }
        val rawDue = workJobs.sumOf { job ->
            if (job.pendingAmount > 0.0) job.pendingAmount else maxOf(0.0, job.totalAmount - job.amountReceived)
        }
        val effectiveDue = maxOf(0.0, minOf(rawDue, billed - paid))

        return CustomerFinancialBreakdown(
            totalBilled = roundToPositiveWholeRupee(billed),
            totalPaid = roundToPositiveWholeRupee(paid),
            balanceDue = roundToPositiveWholeRupee(effectiveDue)
        )
    }

    fun synchronizeCustomerWithJobs(
        customer: CustomerEntity,
        jobs: List<JobEntryEntity>
    ): CustomerEntity {
        val breakdown = calculateCustomerFinancials(customer, jobs)
        return customer.copy(
            totalBilled = breakdown.totalBilled,
            totalPaid = breakdown.totalPaid,
            balanceDue = breakdown.balanceDue
        )
    }

    fun synchronizeCustomers(
        customers: List<CustomerEntity>,
        jobs: List<JobEntryEntity>
    ): List<CustomerEntity> {
        return customers.map { synchronizeCustomerWithJobs(it, jobs) }
    }

    fun countConsolidatedCollectionRecords(
        payments: List<com.example.data.entity.PaymentEntity>,
        jobs: List<JobEntryEntity>
    ): Int {
        var count = payments.size
        val seenPaymentIds = payments.map { it.id }.toSet()
        val seenJobEntryIds = payments.mapNotNull { if (it.jobEntryId > 0L) it.jobEntryId else null }.toSet()

        val syntheticJobs = jobs.filter { isSyntheticPayment(it) && it.amountReceived > 0.0 }
        syntheticJobs.forEach { j ->
            if (!seenJobEntryIds.contains(j.id) && !seenPaymentIds.contains(j.id)) {
                count++
            }
        }

        val workJobsWithReceived = jobs.filter { !isSyntheticPayment(it) && it.amountReceived > 0.0 }
        workJobsWithReceived.forEach { j ->
            val explicitPaymentsForJob = payments.filter { it.jobEntryId == j.id }.sumOf { it.amount }
            val initialAdvance = roundToPositiveWholeRupee(j.amountReceived - explicitPaymentsForJob)
            if (initialAdvance > 0.0) {
                count++
            }
        }

        return count
    }
}
