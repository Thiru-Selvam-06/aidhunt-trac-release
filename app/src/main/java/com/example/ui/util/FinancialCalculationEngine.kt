package com.example.ui.util

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
                exp.paidBy.equals("I Paid", ignoreCase = true) && exp.paidByUid == pUid
            }
            val totalInvestment = roundToPositiveWholeRupee(pInvestments.sumOf { it.amount })
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

    fun cashAvailable(
        payments: List<PaymentEntity>, expenses: List<ExpenseEntity>,
        withdrawals: List<WithdrawalEntity>, scope: FinancialScope
    ): Double {
        val ids = (payments.map { it.workspaceId } + expenses.map { it.workspaceId } +
            withdrawals.map { it.workspaceId }).filter { it.isNotBlank() }.distinct()
        if (scope.workspaceId.isBlank() && ids.size > 1) throw SecurityException("Cash balance requires one authorized workspace")
        val workspace = scope.workspaceId.ifBlank { ids.singleOrNull().orEmpty() }
        val cash = payments.filter { (workspace.isBlank() || it.workspaceId == workspace) &&
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
        return roundToWholeRupee(cash - spent - withdrawn)
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

        val totalRecorded = roundToPositiveWholeRupee(scopedJobs.sumOf { job ->
            if (isSyntheticPayment(job)) {
                0.0
            } else if (job.totalAmount > 0.0) {
                job.totalAmount
            } else {
                job.amountReceived + job.pendingAmount
            }
        })

        val totalReceived = roundToPositiveWholeRupee(scopedJobs.sumOf { j -> j.amountReceived })

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

        val availableBalance = if (payments == null) roundToWholeRupee(totalReceived - totalExpenses - totalWithdrawals)
            else cashAvailable(payments, expenses, withdrawals, scope.copy(workspaceId = authorizedWorkspace))

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
        scope: FinancialScope
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
        fun beneficiary(uid: String): String = uid.takeIf { it in eligibleMembers }.orEmpty()
        val participantUids = linkedSetOf<String>()
        scopedJobs.forEach { participantUids.add(beneficiary(it.financialAttributionUid.ifBlank { it.entryForUid })) }
        scopedExpenses.forEach { participantUids.add(beneficiary(it.financialAttributionUid.ifBlank { it.expenseForUid })) }
        scopedWithdrawals.forEach { participantUids.add(beneficiary(it.targetPartnerUid)) }

        return participantUids.map { uid ->
            val member = eligibleMembers[uid]
            val pName = if (member == null) "Unattributed (migration required)"
                else (member.displayName ?: member.email).orEmpty().trim().ifBlank { "Member" }
            val pRole = if (member == null) "" else if (member.role.equals("owner", true)) "Owner" else member.role
            val pJobs = scopedJobs.filter { beneficiary(it.financialAttributionUid.ifBlank { it.entryForUid }) == uid }
            val pExp = scopedExpenses.filter { beneficiary(it.financialAttributionUid.ifBlank { it.expenseForUid }) == uid }
            val pWth = scopedWithdrawals.filter { beneficiary(it.targetPartnerUid) == uid }

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
                partnerUid = uid,
                partnerName = pName,
                role = pRole,
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
        val customerJobs = jobs.filter { it.workspaceId == customer.workspaceId && it.customerId == customer.id }

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
}
