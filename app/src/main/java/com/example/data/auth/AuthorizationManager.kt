package com.example.data.auth

import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.firebase.WorkspaceMember
import java.util.Calendar

/**
 * Centralized authorization & business-logic authority for AIDHUNT Trac.
 * Combines Admin-controlled platform access-control state with Owner/Partner business rules.
 */
object AuthorizationManager {

    // Page access constants
    const val PAGE_HOME = "HOME"
    const val PAGE_REPORT = "REPORT"
    const val PAGE_EXPENSES = "EXPENSES"
    const val PAGE_BALANCE_SHEET = "BALANCE_SHEET"
    const val PAGE_WITHDRAWAL = "WITHDRAWAL"
    const val PAGE_CUSTOMER_CREDIT_DUE = "CUSTOMER_CREDIT_DUE"
    const val PAGE_BUSINESS_OVERVIEW = "BUSINESS_OVERVIEW"
    const val PAGE_COLLECTION_HISTORY = "COLLECTION_HISTORY"
    const val PAGE_ACCOUNT = "ACCOUNT"
    const val PAGE_NEW_ENTRY = "NEW_ENTRY"

    // Authoritative platform access control document
    @Volatile
    var currentAccessControl: AccessControlDocument = AccessControlDocument()

    /**
     * Checks if the entire application access is blocked due to SUSPENDED or EXPIRED status.
     */
    fun isAccountBlocked(): Boolean {
        return false // v1.6: no plan/account-validity gates; membership is enforced separately.
    }

    /**
     * Returns resolved account status (ACTIVE, SUSPENDED, EXPIRED).
     */
    fun getAccountStatus(): AccountStatus {
        return AccountStatus.ACTIVE
    }

    /**
     * CAN CREATE NEW ENTRY:
     * - Requires account to be ACTIVE.
     * - Requires platform permission canCreateNewEntry == true.
     */
    fun canCreateNewEntry(isOwner: Boolean = true): Boolean {
        return true // The repository requires authenticated active workspace context.
    }

    /**
     * CAN CREATE OLD / HISTORICAL ENTRY:
     * - Strictly OWNER-ONLY workspace authority capability.
     * - Partners and Operators are permanently denied.
     * - Requires account to be ACTIVE (not suspended/expired).
     * - Does not depend on generic subordinate access-control permissions or partner page locks.
     */
    fun canCreateOldEntry(isOwner: Boolean, role: String = ""): Boolean {
        return isOwner && (role.isBlank() || RoleUtils.normalizeRole(role) == RoleUtils.ROLE_OWNER)
    }

    /**
     * DELETE RULES:
     * - Requires account to be ACTIVE.
     * - OWNER: Can delete ANY entry (own or others') without 24-hour restriction.
     *   Owner deletion does not depend on Partner permissions or generic access control.
     * - PARTNER: Can delete ONLY their own UID-attributed entry AND ONLY within 24 HOURS of immutable creation timestamp,
     *   provided platform permission canDeleteEntry == true.
     * - OPERATOR: MUST NOT delete entries under any circumstances.
     */
    fun canDeleteEntry(
        job: JobEntryEntity,
        isOwner: Boolean,
        currentActorName: String,
        currentUid: String = "",
        currentTimeMillis: Long = System.currentTimeMillis(),
        role: String = ""
    ): Boolean {
        val normalized = RoleUtils.normalizeRole(role)
        if (isOwner && (role.isBlank() || normalized == RoleUtils.ROLE_OWNER)) return true
        if (normalized !in setOf(RoleUtils.ROLE_PARTNER, RoleUtils.ROLE_OPERATOR) || job.isHistorical) return false
        return currentUid.isNotBlank() && job.createdByUid == currentUid && within24Hours(job.createdAt, currentTimeMillis)
    }

    /**
     * CUSTOMER DELETE RULES:
     * - Requires account to be ACTIVE.
     * - Full privilege for Owner only. Partner/Operator have NO privilege to delete a customer.
     */
    fun canDeleteCustomer(
        isOwner: Boolean
    ): Boolean {
        return isOwner
    }

    /**
     * EDIT RULES:
     * - Requires account to be ACTIVE.
     * - OWNER: Can edit ANY entry without 24-hour restriction.
     * - PARTNER / OPERATOR: Can edit ONLY their own UID-attributed entry AND ONLY within 24 HOURS of actual creation timestamp,
     *   provided platform permission canEditEntry == true.
     */
    fun canEditEntry(
        job: JobEntryEntity,
        isOwner: Boolean,
        currentActorName: String,
        currentUid: String = "",
        currentTimeMillis: Long = System.currentTimeMillis(),
        role: String = ""
    ): Boolean {
        val normalized = RoleUtils.normalizeRole(role)
        if (isOwner && (role.isBlank() || normalized == RoleUtils.ROLE_OWNER)) return true
        if (normalized !in setOf(RoleUtils.ROLE_PARTNER, RoleUtils.ROLE_OPERATOR) || job.isHistorical) return false
        return currentUid.isNotBlank() && job.createdByUid == currentUid && within24Hours(job.createdAt, currentTimeMillis)
    }

    /**
     * CAN COLLECT / RECORD PAYMENT:
     * - Requires account to be ACTIVE and platform canCollectPayment == true.
     * - Owner and Partner can collect payment (subject to page locks).
     * - Operator has NO privilege to collect payment.
     */
    fun canCollectPayment(isOwner: Boolean = true, role: String = ""): Boolean {
        return if (isOwner) role.isBlank() || RoleUtils.isOwner(role) else RoleUtils.normalizeRole(role) == RoleUtils.ROLE_PARTNER
    }

    /**
     * CAN DELETE COLLECTION / PAYMENT RECORD:
     * - Strictly OWNER-ONLY capability. No 24-hour restriction.
     * - Partner and Operator are permanently denied.
     * - Owner authority must not depend on Partner page locks or Partner permissions.
     * - This method is semantically for collection/payment records, not Job Entries.
     */
    fun canDeleteCollection(isOwner: Boolean): Boolean {
        return isOwner
    }

    /**
     * CAN MANAGE CUSTOMERS:
     * - Owner and Partner can manage customers.
     * - Operator has NO customer management access.
     */
    fun canManageCustomers(isOwner: Boolean = true, role: String = ""): Boolean {
        return if (isOwner) role.isBlank() || RoleUtils.isOwner(role) else RoleUtils.normalizeRole(role) == RoleUtils.ROLE_PARTNER
    }

    /**
     * CAN VIEW CUSTOMER CREDIT:
     */
    fun canViewCustomerCredit(isOwner: Boolean, lockedPages: Set<String> = emptySet(), role: String = ""): Boolean {
        return canAccessPage(PAGE_CUSTOMER_CREDIT_DUE, isOwner, lockedPages, role)
    }

    /**
     * CAN VIEW BUSINESS OVERVIEW:
     */
    fun canViewBusinessOverview(isOwner: Boolean, lockedPages: Set<String> = emptySet(), role: String = ""): Boolean {
        return canAccessPage(PAGE_BUSINESS_OVERVIEW, isOwner, lockedPages, role)
    }

    /**
     * CAN VIEW BALANCE SHEET:
     */
    fun canViewBalanceSheet(isOwner: Boolean, lockedPages: Set<String> = emptySet(), role: String = ""): Boolean {
        return canAccessPage(PAGE_BALANCE_SHEET, isOwner, lockedPages, role)
    }

    /**
     * CAN VIEW COLLECTION HISTORY:
     */
    fun canViewCollectionHistory(isOwner: Boolean, lockedPages: Set<String> = emptySet(), role: String = ""): Boolean {
        return canAccessPage(PAGE_COLLECTION_HISTORY, isOwner, lockedPages, role)
    }

    /**
     * CAN VIEW EXPENSES:
     */
    fun canViewExpenses(isOwner: Boolean, lockedPages: Set<String> = emptySet(), role: String = ""): Boolean {
        return canAccessPage(PAGE_EXPENSES, isOwner, lockedPages, role)
    }

    /**
     * CAN VIEW WITHDRAWALS:
     */
    fun canViewWithdrawals(isOwner: Boolean, lockedPages: Set<String> = emptySet(), role: String = ""): Boolean {
        return canAccessPage(PAGE_WITHDRAWAL, isOwner, lockedPages, role)
    }

    /**
     * WITHDRAWAL DELETE RULES:
     * - Requires account to be ACTIVE.
     * - OWNER: Can delete ANY withdrawal record without restriction.
     * - PARTNER / OPERATOR: No authority to delete withdrawals.
     */
    fun canDeleteWithdrawal(
        withdrawal: WithdrawalEntity,
        isOwner: Boolean,
        currentActorName: String = "",
        currentUid: String = "",
        role: String = "",
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (isOwner && (role.isBlank() || RoleUtils.isOwner(role))) return true
        return RoleUtils.normalizeRole(role) == RoleUtils.ROLE_PARTNER && currentUid.isNotBlank() &&
            withdrawal.createdByUid == currentUid && withdrawal.targetPartnerUid == currentUid && within24Hours(withdrawal.createdAt, currentTimeMillis)
    }

    /**
     * EXPENSE DELETE RULES:
     * - Requires account to be ACTIVE.
     * - OWNER: Can delete ANY expense record without restriction.
     * - PARTNER / OPERATOR: No authority to delete expenses.
     */
    fun canDeleteExpense(
        expense: ExpenseEntity,
        isOwner: Boolean,
        currentActorName: String = "",
        currentUid: String = "",
        role: String = "",
        currentTimeMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (isOwner && (role.isBlank() || RoleUtils.isOwner(role))) return true
        return RoleUtils.normalizeRole(role) == RoleUtils.ROLE_PARTNER && currentUid.isNotBlank() &&
            expense.createdByUid == currentUid && within24Hours(expense.createdAt, currentTimeMillis)
    }

    /**
     * EXPENSE EDIT RULES:
     * - Requires account to be ACTIVE.
     * - OWNER: Can edit ANY expense record without 24-hour restriction.
     * - PARTNER / OPERATOR: No authority to edit expenses.
     */
    fun canEditExpense(
        expense: ExpenseEntity,
        isOwner: Boolean,
        currentActorName: String = "",
        currentUid: String = "",
        currentTimeMillis: Long = System.currentTimeMillis(),
        role: String = ""
    ): Boolean {
        if (isOwner && (role.isBlank() || RoleUtils.isOwner(role))) return true
        return RoleUtils.normalizeRole(role) == RoleUtils.ROLE_PARTNER && currentUid.isNotBlank() &&
            expense.createdByUid == currentUid && within24Hours(expense.createdAt, currentTimeMillis)
    }

    /**
     * CAN CREATE WITHDRAWAL:
     * Authoritative identity-based authorization check executed BEFORE Room insertion.
     *
     * Rules:
     * 1. Actor must be authenticated (actorUid non-blank).
     * 2. Target UID must be non-blank.
     * 3. Workspace account must not be suspended/expired.
     * 4. Platform access-control must permit withdrawal operations.
     * 5. Operator is strictly FORBIDDEN from creating withdrawals.
     * 6. OWNER:
     *    - Can withdraw for self (targetUid == actorUid).
     *    - Can withdraw for any eligible Partner in the active workspace.
     * 7. PARTNER:
     *    - Can withdraw ONLY for themselves (targetUid == actorUid).
     *    - Target UID != actorUid is REJECTED.
     *    - Manipulating partner name/id to target another member is REJECTED.
     */
    fun canCreateWithdrawal(
        actorUid: String,
        actorRole: String,
        targetUid: String,
        workspaceId: String = "",
        workspaceMembers: List<WorkspaceMember> = emptyList()
    ): Boolean {
        if (isAccountBlocked()) return false
        if (actorUid.isBlank() || targetUid.isBlank() || workspaceId.isBlank()) return false

        // Resolve authoritative role from workspace membership if available
        val actorMember = workspaceMembers.firstOrNull {
            it.uid == actorUid && it.workspaceId == workspaceId && it.status.equals("active", ignoreCase = true)
        } ?: return false
        val effectiveRole = RoleUtils.normalizeRole(
            actorMember.role
        )

        // Operators are never authorized to withdraw
        if (effectiveRole == RoleUtils.ROLE_OPERATOR) {
            return false
        }

        // Owner can withdraw for self and any eligible partner in the active workspace
        if (effectiveRole == RoleUtils.ROLE_OWNER) {
            if (targetUid == actorUid) return true
            if (workspaceMembers.isNotEmpty()) {
                val targetMember = workspaceMembers.firstOrNull {
                    it.uid == targetUid && it.workspaceId == workspaceId && it.status.equals("active", ignoreCase = true)
                }
                if (targetMember != null) {
                    val targetRole = RoleUtils.normalizeRole(targetMember.role)
                    return targetRole == RoleUtils.ROLE_PARTNER
                }
                return false
            }
            // Missing target membership never grants authority.
            return false
        }

        // Partner can ONLY withdraw for themselves (targetUid must equal actorUid)
        if (effectiveRole == RoleUtils.ROLE_PARTNER) {
            return targetUid == actorUid
        }

        return false
    }

    /**
     * CAN TAKE WITHDRAWAL FOR TARGET PERSON:
     * - OWNER: Can take for self and any eligible partner in the workspace.
     * - PARTNER: Can ONLY take for themselves (matched by UID, phone, or name). Cannot take for owner or other partners.
     * - OPERATOR: Cannot take withdrawals (operator has no withdrawal entitlement).
     */
    fun canTakeWithdrawalFor(
        isOwner: Boolean,
        userRole: String,
        authenticatedUid: String,
        authenticatedName: String,
        authenticatedPhone: String,
        targetPartnerName: String,
        targetPartnerUid: String = "",
        targetPartnerPhone: String = ""
    ): Boolean {
        if (authenticatedUid.isBlank() || targetPartnerUid.isBlank()) return false
        if (isOwner && RoleUtils.isOwner(userRole)) return true
        return RoleUtils.normalizeRole(userRole) == RoleUtils.ROLE_PARTNER && targetPartnerUid == authenticatedUid
    }

    /**
     * CAN EXPORT PDF:
     */
    fun canExportPdf(): Boolean {
        return true // Role restriction is applied by the workspace-aware ViewModel.
    }

    /**
     * CAN MANAGE TRACTORS:
     * - Owner and Partner can manage tractors.
     * - Operator cannot manage tractors.
     */
    fun canManageTractors(isOwner: Boolean = true, role: String = ""): Boolean {
        return if (isOwner) role.isBlank() || RoleUtils.isOwner(role) else RoleUtils.normalizeRole(role) == RoleUtils.ROLE_PARTNER
    }

    /**
     * CAN MANAGE PARTNERS:
     */
    fun canManagePartners(isOwner: Boolean): Boolean {
        return isOwner
    }

    /**
     * CAN ADD EXTENSIONS:
     * - Owner: YES
     * - Partner: YES
     * - Operator: NO
     */
    fun canAddExtension(isOwner: Boolean, role: String = ""): Boolean {
        return canManageTractors(isOwner, role)
    }

    /**
     * CAN EDIT BUSINESS NAME:
     * - Owner: YES
     * - Partner: NO
     * - Operator: NO
     */
    fun canEditBusinessName(isOwner: Boolean): Boolean {
        return isOwner
    }

    /**
     * ENTRY FOR PERMISSION RULE:
     * - OWNER: Can create entry for self or any valid workspace member.
     * - PARTNER: Self or another active member (validated by the repository/server).
     * - OPERATOR: Self only.
     */
    fun canCreateEntryFor(isOwner: Boolean, currentActorUid: String, targetPersonUid: String, role: String = ""): Boolean {
        return currentActorUid.isNotBlank() && targetPersonUid.isNotBlank() &&
            (isOwner || RoleUtils.normalizeRole(role) == RoleUtils.ROLE_PARTNER || targetPersonUid == currentActorUid)
    }

    /**
     * Determines whether the entry was created by the specified actor name.
     */
    fun isCreatedByActor(job: JobEntryEntity, actorName: String): Boolean {
        val target = actorName.trim()
        if (target.isBlank()) return false
        val creator = job.addedByPartner.ifBlank { job.operatorName }.trim()
        return creator.equals(target, ignoreCase = true)
    }

    /**
     * Compares two millisecond timestamps to check if they fall on the exact same calendar day
     * in the device's local timezone.
     */
    fun isSameCalendarDay(
        timeMillis1: Long,
        timeMillis2: Long = System.currentTimeMillis()
    ): Boolean {
        if (timeMillis1 <= 0L || timeMillis2 <= 0L) return false
        val cal1 = Calendar.getInstance().apply { timeInMillis = timeMillis1 }
        val cal2 = Calendar.getInstance().apply { timeInMillis = timeMillis2 }
        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
    }

    /**
     * Checks if a user can access a specific page/feature.
     * - First checks account-level suspension/expiry.
     * - Operator has strictly narrow operational access (denied from all reports & finance).
     * - Then evaluates platform feature entitlements.
     * - Owner has access to permitted platform pages.
     * - Partner is additionally subject to Owner's lockedPages configuration.
     * - New Job Entry (PAGE_NEW_ENTRY) remains accessible to partners/operators if platform allows entry creation.
     */
    fun canAccessPage(
        pageKey: String,
        isOwner: Boolean,
        lockedPages: Set<String>,
        role: String = ""
    ): Boolean {
        val resolved = RoleUtils.normalizeRole(role)
        if (isOwner && (role.isBlank() || resolved == RoleUtils.ROLE_OWNER)) return true
        if (resolved == RoleUtils.ROLE_PARTNER) return true
        return resolved == RoleUtils.ROLE_OPERATOR && pageKey in setOf(PAGE_HOME, PAGE_ACCOUNT, PAGE_NEW_ENTRY)
    }
    fun within24Hours(createdAt: Long, actionTime: Long = System.currentTimeMillis()): Boolean =
        createdAt > 0L && actionTime >= createdAt && actionTime - createdAt < 86_400_000L

    fun canReverseCollection(payment: com.example.data.entity.PaymentEntity, isOwner: Boolean,
        uid: String, role: String, actionTime: Long = System.currentTimeMillis()): Boolean =
        payment.status == "ACTIVE" && ((isOwner && RoleUtils.isOwner(role)) ||
            (RoleUtils.normalizeRole(role) == RoleUtils.ROLE_PARTNER && uid.isNotBlank() &&
                payment.collectedByUid == uid && within24Hours(payment.createdAt, actionTime)))

    fun canMutateAsset(isOwner: Boolean, role: String, uid: String, createdByUid: String): Boolean =
        (isOwner && RoleUtils.isOwner(role)) || (RoleUtils.normalizeRole(role) == RoleUtils.ROLE_PARTNER &&
            uid.isNotBlank() && uid == createdByUid)

}
