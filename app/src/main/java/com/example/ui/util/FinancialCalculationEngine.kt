package com.example.ui.util

import com.example.data.auth.RoleUtils
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.PaymentEntity
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
    val month: Int = Calendar.getInstance().get(Calendar.MONTH) + 1, // 1..12
    val workspaceId: String = ""
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
    val withdrawals: Double = 0.0,
    val businessExpenses: Double = 0.0
) {
    val totalExpenses: Double get() = expenses + businessExpenses
}

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
    val percentage: Double = 0.0,
    val businessFundedCost: Double = 0.0
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
        val workspaceIds = (jobs.map { it.workspaceId } + expenses.map { it.workspaceId } + withdrawals.map { it.workspaceId }).filter { it.isNotBlank() }.distinct()
        if (scope.workspaceId.isBlank() && workspaceIds.size > 1) throw SecurityException("Financial calculations require one authorized workspace")
        val authorizedWorkspace = scope.workspaceId.ifBlank { workspaceIds.singleOrNull().orEmpty() }
        val scopedJobs = jobs.filter { job ->
            val ts = if (job.startTimeMillis > 0) job.startTimeMillis else job.createdAt
            (authorizedWorkspace.isBlank() || job.workspaceId == authorizedWorkspace) && isTimestampInScope(ts, scope)
        }
        val scopedExpenses = expenses.filter { exp ->
            val ts = if (exp.dateTimestamp > 0) exp.dateTimestamp else exp.createdAt
            (authorizedWorkspace.isBlank() || exp.workspaceId == authorizedWorkspace) && isTimestampInScope(ts, scope)
        }
        val scopedWithdrawals = withdrawals.filter { w ->
            val ts = if (w.timestamp > 0) w.timestamp else w.createdAt
            (authorizedWorkspace.isBlank() || w.workspaceId == authorizedWorkspace) && isTimestampInScope(ts, scope)
        }

        // 1. Distributable Profit calculation:
        // Gross business amount (total received from jobs) - Total operating expenses
        val totalReceived = calculateSummary(scopedJobs, emptyList(), emptyList(), scope).totalReceived
        val totalExpenses = roundMoney(scopedExpenses.sumOf { it.amount }).coerceAtLeast(0.0)
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
            if (member.uid.isNotBlank() && (authorizedWorkspace.isBlank() || member.workspaceId == authorizedWorkspace) && member.status.equals("active", ignoreCase = true)) {
                val normalizedRole = com.example.data.auth.RoleUtils.normalizeRole(member.role)
                if (normalizedRole in setOf(com.example.data.auth.RoleUtils.ROLE_OWNER, com.example.data.auth.RoleUtils.ROLE_PARTNER)) {
                    val isOwnerRole = normalizedRole == com.example.data.auth.RoleUtils.ROLE_OWNER
                    val detail = profitShareAllocationDetails[member.uid]
                    // Membership is the authoritative, current workspace display identity.
                    // Allocation details retain historical metadata and must not resurrect a stale name.
                    val rawName = member.displayName?.takeIf { it.isNotBlank() }
                        ?: detail?.displayName?.takeIf { it.isNotBlank() }
                        ?: member.email
                        ?: if (isOwnerRole) "Owner" else "Partner"
                    val cleanName = rawName.trim()
                    if (cleanName.isNotBlank() && (bizName.isBlank() || !cleanName.equals(bizName, ignoreCase = true))) {
                        val uid = member.uid
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

        // Local name/phone mirrors never manufacture financial participant identities.

        // 4. Calculate shares:
        // When valid custom allocation exists:
        //   - Participant share = distributableAmount * percentage / 100
        //   - Unselected participants (percentage == 0) receive exactly 0% share
        //   - Exact monetary reconciliation ensures total allocated equals distributable amount exactly
        // When no custom allocation exists (legacy equal split):
        //   - Divide equally among all eligible participants with whole-rupee reconciliation
        val participantsWithShares = mutableListOf<ParticipantInfo>()

        val equalAllocation = isEqualShareAllocation(eligibleParticipants.map { it.uid }.toSet(), profitShareAllocations)
        if (hasCustomAllocation && !equalAllocation) {
            eligibleParticipants.forEach { info ->
                val pct = maxOf(0, info.percentage)
                val shareAmount = if (distributableAmount > 0 && pct > 0) {
                    roundToWholeRupee(distributableAmount * pct / 100.0)
                } else {
                    0.0
                }
                participantsWithShares.add(info.copy(calculatedShare = shareAmount, effectivePercentage = pct.toDouble()))
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
            val percentage = if (count > 0) 100.0 / count else 0.0
            val amounts = equalMoneyShares(distributableAmount, eligibleParticipants.map { it.uid })
            eligibleParticipants.forEach { info ->
                participantsWithShares.add(info.copy(calculatedShare = amounts[info.uid] ?: 0.0,
                    effectivePercentage = percentage))
            }
        }

        // 5. Build PartnerShareInvestmentStatus for each participant
        // Informational shared costs are neither personal investment nor an additional cash outflow.
        val sharedBusinessCosts = equalMoneyShares(scopedExpenses.filter { it.paidBy.trim().equals("Business Paid", true) }
            .sumOf { it.amount }, eligibleParticipants.map { it.uid })
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
                exp.paidBy.equals("I Paid", ignoreCase = true) && exp.paidByUid == pUid
            }
            val totalInvestment = roundMoney(pInvestments.sumOf { it.amount }).coerceAtLeast(0.0)
            val pWithdrawals = scopedWithdrawals.filter { it.targetPartnerUid == pUid }

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
                    cat.contains("share") || cat.contains("withdrawal") -> {
                        pTakenShare += w.amount
                    }
                    cat.contains("investment") || cat.contains("reimburse") -> {
                        pTakenInvestment += w.amount
                    }
                    cat.contains("personal use") || cat.contains("advance") -> {
                        pTakenPersonalUse += w.amount
                    }
                    else -> {
                        pTakenShare += w.amount
                    }
                }
            }

            val takenShare = roundMoney(pTakenShare).coerceAtLeast(0.0)
            val takenInvestment = roundMoney(pTakenInvestment).coerceAtLeast(0.0)
            val takenPersonalUse = roundMoney(pTakenPersonalUse).coerceAtLeast(0.0)

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
                percentage = effectivePct,
                businessFundedCost = sharedBusinessCosts[pUid] ?: 0.0
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
        val effectivePercentage: Double = 0.0
    ) {
        fun copy(
            calculatedShare: Double = this.calculatedShare,
            effectivePercentage: Double = this.effectivePercentage
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

    /** Partner shares and payouts retain paisa; invoice rounding remains its existing policy. */
    fun roundMoney(value: Double): Double = if (!value.isFinite()) 0.0 else
        java.math.BigDecimal.valueOf(value).setScale(2, java.math.RoundingMode.HALF_UP).toDouble()

    fun isSyntheticPayment(job: JobEntryEntity): Boolean {
        return job.tractorLabel.equals("Payment", ignoreCase = true) ||
                job.workType.equals("Payment Received", ignoreCase = true)
    }

    fun cashAvailable(
        payments: List<PaymentEntity>, expenses: List<ExpenseEntity>,
        withdrawals: List<WithdrawalEntity>, scope: FinancialScope,
        jobs: List<JobEntryEntity> = emptyList()
    ): Double {
        val ids = (jobs.map { it.workspaceId } + payments.map { it.workspaceId } + expenses.map { it.workspaceId } +
            withdrawals.map { it.workspaceId }).filter { it.isNotBlank() }.distinct()
        if (scope.workspaceId.isBlank() && ids.size > 1) throw SecurityException("Cash balance requires one authorized workspace")
        val workspace = scope.workspaceId.ifBlank { ids.singleOrNull().orEmpty() }
        val legacyCash = com.example.ui.screens.report.reconciledLegacyReceiptJobs(workspace, payments, jobs).filter {
            com.example.ui.screens.report.recordedLegacyPaymentMethod(it.notes) == "Cash" &&
                isTimestampInScope(if (it.startTimeMillis > 0) it.startTimeMillis else it.createdAt, scope)
        }.sumOf { it.amountReceived }
        val cash = legacyCash + payments.filter { (workspace.isBlank() || it.workspaceId == workspace) &&
            it.status.equals("ACTIVE", true) && it.paymentMethod.trim().equals("Cash", true) &&
            isTimestampInScope(it.collectedAt, scope) }.sumOf { it.amount }
        // Personally funded "I Paid" expenses are member investments, not
        // withdrawals from the workspace's cash drawer.
        val spent = expenses.filter { (workspace.isBlank() || it.workspaceId == workspace) &&
            it.paidBy.trim().equals("Business Paid", true) &&
            isTimestampInScope(if (it.dateTimestamp > 0) it.dateTimestamp else it.createdAt, scope) }.sumOf { it.amount }
        val withdrawn = withdrawals.filter { (workspace.isBlank() || it.workspaceId == workspace) &&
            isTimestampInScope(if (it.timestamp > 0) it.timestamp else it.createdAt, scope) }.sumOf { it.amount }
        require(listOf(cash, spent, withdrawn).all { it.isFinite() }) { "Cash balance contains invalid amounts" }
        return roundMoney(cash - spent - withdrawn)
    }

    fun calculateSummary(
        jobs: List<JobEntryEntity>,
        expenses: List<ExpenseEntity>,
        withdrawals: List<WithdrawalEntity>,
        scope: FinancialScope,
        payments: List<PaymentEntity>? = null
    ): FinancialSummary {
        val workspaceIds = (jobs.map { it.workspaceId } + expenses.map { it.workspaceId } + withdrawals.map { it.workspaceId }).filter { it.isNotBlank() }.distinct()
        if (scope.workspaceId.isBlank() && workspaceIds.size > 1) throw SecurityException("Financial calculations require one authorized workspace")
        val authorizedWorkspace = scope.workspaceId.ifBlank { workspaceIds.singleOrNull().orEmpty() }
        val scopedJobs = jobs.filter { job ->
            val ts = if (job.startTimeMillis > 0) job.startTimeMillis else job.createdAt
            (authorizedWorkspace.isBlank() || job.workspaceId == authorizedWorkspace) && isTimestampInScope(ts, scope)
        }

        val scopedExpenses = expenses.filter { exp ->
            val ts = if (exp.dateTimestamp > 0) exp.dateTimestamp else exp.createdAt
            (authorizedWorkspace.isBlank() || exp.workspaceId == authorizedWorkspace) && isTimestampInScope(ts, scope)
        }

        val scopedWithdrawals = withdrawals.filter { w ->
            val ts = if (w.timestamp > 0) w.timestamp else w.createdAt
            (authorizedWorkspace.isBlank() || w.workspaceId == authorizedWorkspace) && isTimestampInScope(ts, scope)
        }

        // Use the same per-customer ledger and rounding as Customer Dues.
        // Legacy payment-only entries settle an obligation, never add sales.
        val ledgers = scopedJobs.groupBy { Triple(it.workspaceId, it.customerId, if (it.customerId > 0) 0L else it.id) }
            .values.map(::customerLedger)
        val totalRecorded = ledgers.sumOf { it.totalBilled }
        val totalReceived = ledgers.sumOf { it.totalPaid }
        val totalDue = ledgers.sumOf { it.balanceDue }

        val totalExpenses = roundMoney(scopedExpenses.sumOf { it.amount }).coerceAtLeast(0.0)

        val totalWithdrawals = roundMoney(scopedWithdrawals.sumOf { it.amount }).coerceAtLeast(0.0)

        val availableBalance = if (payments == null) roundMoney(totalReceived - totalExpenses - totalWithdrawals)
            else cashAvailable(payments, expenses, withdrawals, scope.copy(workspaceId = authorizedWorkspace), jobs)

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
        preserveUnresolvedUids: Boolean = false
    ): List<PartnerFinancialBreakdown> {
        val workspaceIds = (jobs.map { it.workspaceId } + expenses.map { it.workspaceId } + withdrawals.map { it.workspaceId }).filter { it.isNotBlank() }.distinct()
        if (scope.workspaceId.isBlank() && workspaceIds.size > 1) throw SecurityException("Financial calculations require one authorized workspace")
        val authorizedWorkspace = scope.workspaceId.ifBlank { workspaceIds.singleOrNull().orEmpty() }
        val scopedJobs = jobs.filter { job ->
            val ts = if (job.startTimeMillis > 0) job.startTimeMillis else job.createdAt
            (authorizedWorkspace.isBlank() || job.workspaceId == authorizedWorkspace) && isTimestampInScope(ts, scope)
        }
        val scopedExpenses = expenses.filter { exp ->
            val ts = if (exp.dateTimestamp > 0) exp.dateTimestamp else exp.createdAt
            (authorizedWorkspace.isBlank() || exp.workspaceId == authorizedWorkspace) && isTimestampInScope(ts, scope)
        }
        val scopedWithdrawals = withdrawals.filter { w ->
            val ts = if (w.timestamp > 0) w.timestamp else w.createdAt
            (authorizedWorkspace.isBlank() || w.workspaceId == authorizedWorkspace) && isTimestampInScope(ts, scope)
        }

        // A participant is an active member UID, never a record display name.
        // This keeps the report inside the active workspace membership boundary,
        // preserves distinct UIDs with the same name, and collapses case-only
        // display-name differences for one UID.
        val eligibleMembers = workspaceMembers
            .asSequence()
            .filter { it.uid.isNotBlank() && (authorizedWorkspace.isBlank() || it.workspaceId == authorizedWorkspace) && (it.status.equals("active", true) || it.status.equals("removed", true)) }
            .distinctBy { it.uid }
            .associateBy { it.uid }

        // Unmapped legacy amounts remain visible without guessing their beneficiary.
        // Chart presentation may retain explicit record identities while member metadata is unavailable.
        // This never supplies membership, role, or mutation authority; missing UIDs remain unresolved.
        fun beneficiary(uid: String): String = uid.takeIf { it in eligibleMembers || preserveUnresolvedUids }.orEmpty()
        val cachedNames = partners.filter { it.workspaceId == authorizedWorkspace && it.firebaseUid.isNotBlank() }
            .groupBy { it.firebaseUid }
            .mapValues { (_, rows) -> rows.map { it.name.trim() }.filter { it.isNotBlank() }.distinct().singleOrNull() }

        val participantUids = linkedSetOf<String>()
        val businessTotal = scopedExpenses.filter { it.paidBy.trim().equals("Business Paid", true) }.sumOf { it.amount }
        val businessParticipants = eligibleMembers.values.filter {
            it.status.equals("active", true) && RoleUtils.normalizeRole(it.role) in setOf(RoleUtils.ROLE_OWNER, RoleUtils.ROLE_PARTNER)
        }.map { it.uid }.sorted()
        val businessShares = equalMoneyShares(businessTotal, businessParticipants)
            .ifEmpty { if (businessTotal > 0) mapOf("" to businessTotal) else emptyMap() }
        if (businessTotal > 0) participantUids.addAll(businessShares.keys)
        scopedJobs.forEach { participantUids.add(beneficiary(it.financialAttributionUid.ifBlank { it.entryForUid })) }
        scopedExpenses.filter { it.paidBy.equals("I Paid", ignoreCase = true) }
            .forEach { participantUids.add(beneficiary(it.paidByUid)) }
        scopedWithdrawals.forEach { participantUids.add(beneficiary(it.targetPartnerUid)) }

        return participantUids.map { uid ->
            val member = eligibleMembers[uid]
            val pName = if (member == null) {
                if (uid.isBlank()) "Attribution unavailable"
                else cachedNames[uid] ?: "Member details unavailable"
            } else member.displayName.orEmpty().trim().ifBlank { member.email.orEmpty().trim() }.ifBlank { "Member" }
            val pRole = if (member == null) "" else if (member.role.equals("owner", true)) "Owner" else member.role
            val pJobs = scopedJobs.filter { beneficiary(it.financialAttributionUid.ifBlank { it.entryForUid }) == uid }
            val pExp = scopedExpenses.filter {
                it.paidBy.equals("I Paid", ignoreCase = true) &&
                beneficiary(it.paidByUid) == uid
            }
            val pWth = scopedWithdrawals.filter { beneficiary(it.targetPartnerUid) == uid }

            val ledger = calculateSummary(pJobs, emptyList(), emptyList(), scope)
            val recorded = ledger.totalRecorded
            val received = ledger.totalReceived
            val due = ledger.totalDue
            val exp = roundMoney(pExp.sumOf { it.amount }).coerceAtLeast(0.0)
            val wth = roundMoney(pWth.sumOf { it.amount }).coerceAtLeast(0.0)

            PartnerFinancialBreakdown(
                partnerUid = uid,
                partnerName = pName,
                role = pRole,
                recorded = recorded,
                received = received,
                due = due,
                expenses = exp,
                withdrawals = wth,
                businessExpenses = businessShares[uid] ?: 0.0
            )
        }.filter { it.recorded > 0 || it.received > 0 || it.due > 0 || it.totalExpenses > 0 || it.withdrawals > 0 }
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
        val customerJobs = jobs.filter { it.workspaceId == customer.workspaceId && it.customerId == customer.id }

        return customerLedger(customerJobs)
    }

    private fun customerLedger(customerJobs: List<JobEntryEntity>): CustomerFinancialBreakdown {
        val workJobs = customerJobs.filterNot { isSyntheticPayment(it) }
        if (workJobs.isEmpty()) return CustomerFinancialBreakdown()
        val billed = workJobs.sumOf { job ->
            if (job.totalAmount > 0.0) job.totalAmount else (job.amountReceived + job.pendingAmount)
        }
        val paid = customerJobs.sumOf { it.amountReceived }
        // pendingAmount is a denormalized cache. Derive the current obligation
        // from billed minus received so a stale cache cannot understate dues.
        val displayedBilled = roundToPositiveWholeRupee(billed)
        val displayedPaid = roundToPositiveWholeRupee(paid)

        return CustomerFinancialBreakdown(
            totalBilled = displayedBilled,
            totalPaid = displayedPaid,
            balanceDue = maxOf(0.0, displayedBilled - displayedPaid)
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
        val rows = customers.associateBy { it.workspaceId to it.id }.toMutableMap()
        jobs.filterNot(::isSyntheticPayment).filter { it.customerId > 0 }
            .groupBy { it.workspaceId to it.customerId }.forEach { (key, sources) ->
                if (key !in rows) {
                    val source = sources.maxBy { it.createdAt }
                    rows[key] = CustomerEntity(id = source.customerId, workspaceId = source.workspaceId,
                        name = source.customerName, phone = source.customerPhone, location = source.customerLocation,
                        createdAt = source.createdAt, updatedAt = source.updatedAt)
                }
            }
        return rows.values.map { synchronizeCustomerWithJobs(it, jobs) }
    }

    fun customersWithLedger(customers: List<CustomerEntity>, jobs: List<JobEntryEntity>): List<CustomerEntity> {
        val sources = jobs.filterNot(::isSyntheticPayment).map { it.workspaceId to it.customerId }.toSet()
        return synchronizeCustomers(customers, jobs).filter { (it.workspaceId to it.id) in sources }
    }
}
