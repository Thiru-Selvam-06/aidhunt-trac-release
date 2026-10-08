package com.example.data.auth

import com.example.data.entity.JobEntryEntity
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
        return currentAccessControl.isAccountBlocked
    }

    /**
     * Returns resolved account status (ACTIVE, SUSPENDED, EXPIRED).
     */
    fun getAccountStatus(): AccountStatus {
        return currentAccessControl.getResolvedStatus()
    }

    /**
     * CAN CREATE NEW ENTRY:
     * - Requires account to be ACTIVE.
     * - Requires platform permission canCreateNewEntry == true.
     */
    fun canCreateNewEntry(isOwner: Boolean = true): Boolean {
        if (isAccountBlocked()) return false
        return currentAccessControl.permissions.canCreateNewEntry
    }

    /**
     * CAN CREATE OLD / HISTORICAL ENTRY:
     * - Strictly OWNER-ONLY workspace authority capability.
     * - Partners and Operators are permanently denied.
     * - Requires account to be ACTIVE (not suspended/expired).
     * - Does not depend on generic subordinate access-control permissions or partner page locks.
     */
    fun canCreateOldEntry(isOwner: Boolean, role: String = ""): Boolean {
        if (isAccountBlocked()) return false
        if (!isOwner) return false
        if (role.isNotBlank()) {
            val normalizedRole = RoleUtils.normalizeRole(role)
            if (normalizedRole == RoleUtils.ROLE_PARTNER || normalizedRole == RoleUtils.ROLE_OPERATOR) {
                return false
            }
        }
        return true
    }

    /**
     * DELETE RULES:
     * - Requires account to be ACTIVE.
     * - OWNER: Can delete ANY entry (own or others') without 24-hour restriction.
     *   Owner deletion does not depend on Partner permissions or generic access control.
     * - PARTNER: Can delete ONLY their own entry AND ONLY within 24 HOURS of immutable creation timestamp,
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
        if (isAccountBlocked()) return false

        // OWNER: Unrestricted deletion for workspace authority
        if (isOwner && (role.isBlank() || RoleUtils.normalizeRole(role) != RoleUtils.ROLE_OPERATOR)) {
            return true
        }

        // Subordinate delete requires platform permission
        if (!currentAccessControl.permissions.canDeleteEntry) return false

        // Operator has NO delete permission under authoritative rules
        if (RoleUtils.normalizeRole(role) == RoleUtils.ROLE_OPERATOR) return false

        // Partner cannot delete Owner-created entries
        if (job.createdByRole.equals(RoleUtils.ROLE_OWNER, ignoreCase = true)) return false

        // Partner must be the creator of the entry
        if (currentUid.isNotBlank() && job.createdByUid.isNotBlank()) {
            if (job.createdByUid != currentUid) return false
        } else {
            if (currentActorName.isBlank()) return false
            val isSelf = isCreatedByActor(job, currentActorName)
            if (!isSelf) return false
        }

        // Partner 24-hour creation timestamp window (currentTime - job.createdAt < 24 hours)
        val ageMillis = currentTimeMillis - job.createdAt
        return ageMillis in 0 until (24 * 60 * 60 * 1000L)
    }

    /**
     * CUSTOMER DELETE RULES:
     * - Requires account to be ACTIVE.
     * - Full privilege for Owner only. Partner/Operator have NO privilege to delete a customer.
     */
    fun canDeleteCustomer(
        isOwner: Boolean
    ): Boolean {
        if (isAccountBlocked()) return false
        return isOwner
    }

    /**
     * EDIT RULES:
     * - Requires account to be ACTIVE.
     * - OWNER: Can edit ANY entry without 24-hour restriction.
     * - PARTNER / OPERATOR: Can edit ONLY their own entry AND ONLY within 24 HOURS of actual creation timestamp,
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
        if (isAccountBlocked()) return false

        // OWNER: Unrestricted edit for workspace authority
        if (isOwner && (role.isBlank() || RoleUtils.normalizeRole(role) != RoleUtils.ROLE_OPERATOR)) {
            return true
        }

        if (!currentAccessControl.permissions.canEditEntry) return false

        // Partner / Operator cannot edit Owner-created entries
        if (job.createdByRole.equals(RoleUtils.ROLE_OWNER, ignoreCase = true)) return false

        // Partner / Operator must be the creator of the entry
        if (currentUid.isNotBlank() && job.createdByUid.isNotBlank()) {
            if (job.createdByUid != currentUid) return false
        } else {
            if (currentActorName.isBlank()) return false
            val isSelf = isCreatedByActor(job, currentActorName)
            if (!isSelf) return false
        }
        val ageMillis = currentTimeMillis - job.createdAt
        return ageMillis in 0 until (24 * 60 * 60 * 1000L)
    }

    /**
     * CAN COLLECT / RECORD PAYMENT:
     * - Requires account to be ACTIVE and platform canCollectPayment == true.
     * - Owner and Partner can collect payment (subject to page locks).
     * - Operator has NO privilege to collect payment.
     */
    fun canCollectPayment(isOwner: Boolean = true, role: String = ""): Boolean {
        if (isAccountBlocked()) return false
        if (RoleUtils.normalizeRole(role) == RoleUtils.ROLE_OPERATOR) return false
        return currentAccessControl.permissions.canCollectPayment
    }

    /**
     * CAN DELETE COLLECTION / PAYMENT RECORD:
     * - Strictly OWNER-ONLY capability. No 24-hour restriction.
     * - Partner and Operator are permanently denied.
     * - Owner authority must not depend on Partner page locks or Partner permissions.
     * - This method is semantically for collection/payment records, not Job Entries.
     */

    /**
     * CAN MANAGE CUSTOMERS:
     * - Owner and Partner can manage customers.
     * - Operator has NO customer management access.
     */
    fun canManageCustomers(isOwner: Boolean = true, role: String = ""): Boolean {
        if (isAccountBlocked()) return false
        if (RoleUtils.normalizeRole(role) == RoleUtils.ROLE_OPERATOR) return false
        return true
    }

    /**
     * CAN VIEW CUSTOMER CREDIT:
     */
    fun canViewCustomerCredit(isOwner: Boolean, lockedPages: Set<String> = emptySet(), role: String = ""): Boolean {
        if (isAccountBlocked()) return false
        if (isOwner) return true
        if (!currentAccessControl.permissions.canViewCustomerCredit) return false
        return canAccessPage(PAGE_CUSTOMER_CREDIT_DUE, isOwner, lockedPages, role)
    }

    /**
     * CAN VIEW BUSINESS OVERVIEW:
     */
    fun canViewBusinessOverview(isOwner: Boolean, lockedPages: Set<String> = emptySet(), role: String = ""): Boolean {
        if (isAccountBlocked()) return false
        if (isOwner) return true
        if (!currentAccessControl.permissions.canViewBusinessOverview) return false
        return canAccessPage(PAGE_BUSINESS_OVERVIEW, isOwner, lockedPages, role)
    }

    /**
     * CAN VIEW BALANCE SHEET:
     */
    fun canViewBalanceSheet(isOwner: Boolean, lockedPages: Set<String> = emptySet(), role: String = ""): Boolean {
        if (isAccountBlocked()) return false
        if (isOwner) return true
        if (!currentAccessControl.permissions.canViewBalanceSheet) return false
        return canAccessPage(PAGE_BALANCE_SHEET, isOwner, lockedPages, role)
    }

    /**
     * CAN VIEW COLLECTION HISTORY:
     */
    fun canViewCollectionHistory(isOwner: Boolean, lockedPages: Set<String> = emptySet(), role: String = ""): Boolean {
        if (isAccountBlocked()) return false
        if (isOwner) return true
        if (!currentAccessControl.permissions.canViewCollectionHistory) return false
        return canAccessPage(PAGE_COLLECTION_HISTORY, isOwner, lockedPages, role)
    }

    /**
     * CAN VIEW EXPENSES:
     */
    fun canViewExpenses(isOwner: Boolean, lockedPages: Set<String> = emptySet(), role: String = ""): Boolean {
        if (isAccountBlocked()) return false
        if (isOwner) return true
        if (!currentAccessControl.permissions.canViewExpenses) return false
        return canAccessPage(PAGE_EXPENSES, isOwner, lockedPages, role)
    }

    /**
     * CAN VIEW WITHDRAWALS:
     */
    fun canViewWithdrawals(isOwner: Boolean, lockedPages: Set<String> = emptySet(), role: String = ""): Boolean {
        if (isAccountBlocked()) return false
        if (isOwner) return true
        if (!currentAccessControl.permissions.canViewWithdrawals) return false
        return canAccessPage(PAGE_WITHDRAWAL, isOwner, lockedPages, role)
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
        if (!currentAccessControl.permissions.canViewWithdrawals) return false
        if (actorUid.isBlank() || targetUid.isBlank()) return false

        // Resolve authoritative role from workspace membership if available
        val actorMember = workspaceMembers.firstOrNull {
            it.uid == actorUid && (it.status.isBlank() || it.status.equals("active", ignoreCase = true))
        }
        val effectiveRole = RoleUtils.normalizeRole(
            actorMember?.role?.takeIf { it.isNotBlank() } ?: actorRole
        )

        // Operators are never authorized to withdraw
        if (effectiveRole == RoleUtils.ROLE_OPERATOR) {
            return false
        }

        // Owner can withdraw for self and any eligible partner in the active workspace
        if (effectiveRole == RoleUtils.ROLE_OWNER || effectiveRole == RoleUtils.ROLE_CO_OWNER) {
            if (targetUid == actorUid) return true
            if (workspaceMembers.isNotEmpty()) {
                val targetMember = workspaceMembers.firstOrNull {
                    it.uid == targetUid && (it.status.isBlank() || it.status.equals("active", ignoreCase = true))
                }
                if (targetMember != null) {
                    val targetRole = RoleUtils.normalizeRole(targetMember.role)
                    return targetRole != RoleUtils.ROLE_OPERATOR
                }
                return false
            }
            // If workspaceMembers is empty (e.g. offline fallback), allow owner
            return true
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
        if (isAccountBlocked()) return false
        val normalizedRole = RoleUtils.normalizeRole(userRole)
        if (normalizedRole == RoleUtils.ROLE_OPERATOR) return false

        // Owner can take for self and any partner in the workspace
        if (isOwner) return true

        // Partner can ONLY take for themselves
        if (normalizedRole == RoleUtils.ROLE_PARTNER) {
            // Strict UID match takes priority
            if (targetPartnerUid.isNotBlank() && authenticatedUid.isNotBlank()) {
                return targetPartnerUid == authenticatedUid
            }
            // If targetPartnerUid was blank, check phone
            val cleanUserPhone = authenticatedPhone.filter { it.isDigit() }.takeLast(10)
            val cleanTargetPhone = targetPartnerPhone.filter { it.isDigit() }.takeLast(10)
            if (cleanUserPhone.isNotBlank() && cleanTargetPhone.isNotBlank()) {
                return cleanUserPhone == cleanTargetPhone
            }
            // Name match (case-insensitive) only if UID was not specified
            if (authenticatedName.isNotBlank() && targetPartnerName.isNotBlank()) {
                return authenticatedName.trim().equals(targetPartnerName.trim(), ignoreCase = true)
            }
            return false
        }

        return false
    }

    /**
     * CAN EXPORT PDF:
     */
    fun canExportPdf(): Boolean {
        if (isAccountBlocked()) return false
        return currentAccessControl.permissions.canExportPdf
    }

    /**
     * CAN MANAGE TRACTORS:
     * - Owner and Partner can manage tractors.
     * - Operator cannot manage tractors.
     */
    fun canManageTractors(isOwner: Boolean = true, role: String = ""): Boolean {
        if (isAccountBlocked()) return false
        if (RoleUtils.normalizeRole(role) == RoleUtils.ROLE_OPERATOR) return false
        return currentAccessControl.permissions.canManageTractors
    }

    /**
     * CAN MANAGE PARTNERS:
     */
    fun canManagePartners(isOwner: Boolean): Boolean {
        if (isAccountBlocked()) return false
        if (!isOwner) return false
        return currentAccessControl.permissions.canManagePartners
    }

    /**
     * CAN ADD EXTENSIONS:
     * - Owner: YES
     * - Partner: YES
     * - Operator: NO
     */
    fun canAddExtension(isOwner: Boolean, role: String = ""): Boolean {
        if (isAccountBlocked()) return false
        if (RoleUtils.normalizeRole(role) == RoleUtils.ROLE_OPERATOR) return false
        return true
    }

    /**
     * CAN EDIT BUSINESS NAME:
     * - Owner: YES
     * - Partner: NO
     * - Operator: NO
     */
    fun canEditBusinessName(isOwner: Boolean): Boolean {
        if (isAccountBlocked()) return false
        return isOwner
    }

    /**
     * ENTRY FOR PERMISSION RULE:
     * - OWNER: Can create entry for self or any valid workspace member.
     * - PARTNER: Self only.
     * - OPERATOR: Self only.
     */
    fun canCreateEntryFor(isOwner: Boolean, currentActorUid: String, targetPersonUid: String): Boolean {
        if (isOwner) return true
        if (currentActorUid.isBlank()) return true
        return targetPersonUid.isBlank() || targetPersonUid == currentActorUid
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
        if (isAccountBlocked()) return false

        // Operator role restrictions
        val normalizedRole = RoleUtils.normalizeRole(role)
        if (normalizedRole == RoleUtils.ROLE_OPERATOR) {
            when (pageKey) {
                PAGE_REPORT,
                PAGE_EXPENSES,
                PAGE_BALANCE_SHEET,
                PAGE_BUSINESS_OVERVIEW,
                PAGE_WITHDRAWAL,
                PAGE_CUSTOMER_CREDIT_DUE,
                PAGE_COLLECTION_HISTORY -> return false
            }
        }

        // Owner has access to all workspace pages (bypasses partner page locks and subordinate permissions)
        if (isOwner) return true

        // Check platform permission for specific page (for non-owner collaborators)
        when (pageKey) {
            PAGE_EXPENSES -> if (!currentAccessControl.permissions.canViewExpenses) return false
            PAGE_BALANCE_SHEET -> if (!currentAccessControl.permissions.canViewBalanceSheet) return false
            PAGE_BUSINESS_OVERVIEW -> if (!currentAccessControl.permissions.canViewBusinessOverview) return false
            PAGE_WITHDRAWAL -> if (!currentAccessControl.permissions.canViewWithdrawals) return false
            PAGE_CUSTOMER_CREDIT_DUE -> if (!currentAccessControl.permissions.canViewCustomerCredit) return false
            PAGE_COLLECTION_HISTORY -> if (!currentAccessControl.permissions.canViewCollectionHistory) return false
            PAGE_NEW_ENTRY -> if (!currentAccessControl.permissions.canCreateNewEntry && !currentAccessControl.permissions.canCreateOldEntry) return false
        }
        
        // New Job Entry (PAGE_NEW_ENTRY) must always be accessible unless platform locked
        if (pageKey == PAGE_NEW_ENTRY) return true
        
        // Check if page is locked for partners by Owner
        return pageKey !in lockedPages
    }

    fun canDeleteTractor(
        tractorCreatedByUid: String,
        tractorCreatedAt: Long,
        isOwner: Boolean,
        currentUid: String = "",
        currentTimeMillis: Long = System.currentTimeMillis(),
        role: String = ""
    ): Boolean {
        if (isAccountBlocked()) return false
        if (isOwner && (role.isBlank() || RoleUtils.normalizeRole(role) != RoleUtils.ROLE_OPERATOR)) {
            return true
        }
        if (!currentAccessControl.permissions.canDeleteEntry) return false
        if (RoleUtils.normalizeRole(role) == RoleUtils.ROLE_OPERATOR) return false

        if (tractorCreatedByUid.isBlank()) {
            return isOwner
        }

        if (currentUid.isNotBlank() && tractorCreatedByUid != currentUid) {
            return false
        }

        val ageMillis = currentTimeMillis - tractorCreatedAt
        return ageMillis in 0 until (24 * 60 * 60 * 1000L)
    }

    fun canDeleteExtension(
        extensionCreatedByUid: String,
        extensionCreatedAt: Long,
        isOwner: Boolean,
        currentUid: String = "",
        currentTimeMillis: Long = System.currentTimeMillis(),
        role: String = ""
    ): Boolean {
        if (isAccountBlocked()) return false
        if (isOwner && (role.isBlank() || RoleUtils.normalizeRole(role) != RoleUtils.ROLE_OPERATOR)) {
            return true
        }
        if (!currentAccessControl.permissions.canDeleteEntry) return false
        if (RoleUtils.normalizeRole(role) == RoleUtils.ROLE_OPERATOR) return false

        if (extensionCreatedByUid.isBlank()) {
            return isOwner
        }

        if (currentUid.isNotBlank() && extensionCreatedByUid != currentUid) {
            return false
        }

        val ageMillis = currentTimeMillis - extensionCreatedAt
        return ageMillis in 0 until (24 * 60 * 60 * 1000L)
    }

    fun canEditExpense(
        expenseCreatedByUid: String,
        expensePaidByUid: String,
        expenseCreatedAt: Long,
        isOwner: Boolean,
        currentUid: String = "",
        currentTimeMillis: Long = System.currentTimeMillis(),
        role: String = ""
    ): Boolean {
        if (isAccountBlocked()) return false
        if (isOwner && (role.isBlank() || RoleUtils.normalizeRole(role) != RoleUtils.ROLE_OPERATOR)) {
            return true
        }
        if (!currentAccessControl.permissions.canEditEntry) return false
        if (RoleUtils.normalizeRole(role) == RoleUtils.ROLE_OPERATOR) return false

        val ownerUid = expenseCreatedByUid.ifBlank { expensePaidByUid }
        if (currentUid.isNotBlank() && ownerUid.isNotBlank() && ownerUid != currentUid) {
            return false
        }

        val ageMillis = currentTimeMillis - expenseCreatedAt
        return ageMillis in 0 until (24 * 60 * 60 * 1000L)
    }

    fun canDeleteExpense(
        expenseCreatedByUid: String,
        expensePaidByUid: String,
        expenseCreatedAt: Long,
        isOwner: Boolean,
        currentUid: String = "",
        currentTimeMillis: Long = System.currentTimeMillis(),
        role: String = ""
    ): Boolean {
        if (isAccountBlocked()) return false
        val normalizedRole = RoleUtils.normalizeRole(role)
        if (normalizedRole == RoleUtils.ROLE_OPERATOR) return false

        if (isOwner) {
            return true
        }

        if (!currentAccessControl.permissions.canDeleteEntry) return false

        // PARTNER: Ownership requires createdByUid == currentUid OR paidByUid == currentUid
        val isSelf = currentUid.isNotBlank() && (
            (expenseCreatedByUid.isNotBlank() && expenseCreatedByUid == currentUid) ||
            (expensePaidByUid.isNotBlank() && expensePaidByUid == currentUid)
        )
        if (!isSelf) {
            return false
        }

        val ageMillis = currentTimeMillis - expenseCreatedAt
        return ageMillis in 0 until (24 * 60 * 60 * 1000L)
    }

    fun canDeleteCollection(
        collectionCreatedByUid: String,
        collectionCreatedAt: Long,
        isOwner: Boolean,
        currentUid: String = "",
        currentTimeMillis: Long = System.currentTimeMillis(),
        role: String = ""
    ): Boolean {
        if (isAccountBlocked()) return false
        val normalizedRole = RoleUtils.normalizeRole(role)
        if (normalizedRole == RoleUtils.ROLE_OPERATOR) return false

        if (isOwner || RoleUtils.isOwner(normalizedRole) || role.equals("owner", ignoreCase = true)) {
            return true
        }

        if (!currentAccessControl.permissions.canDeleteEntry) return false

        // PARTNER: Ownership requires createdByUid == currentUid
        val isSelf = currentUid.isNotBlank() && collectionCreatedByUid.isNotBlank() && collectionCreatedByUid == currentUid
        if (!isSelf) {
            return false
        }

        val ageMillis = currentTimeMillis - collectionCreatedAt
        return ageMillis in 0..(24 * 60 * 60 * 1000L)
    }

    fun canDeleteWithdrawal(
        withdrawalTargetPartnerUid: String,
        withdrawalCreatedByUid: String,
        withdrawalCreatedAt: Long,
        isOwner: Boolean,
        currentUid: String = "",
        currentTimeMillis: Long = System.currentTimeMillis(),
        role: String = ""
    ): Boolean {
        if (isAccountBlocked()) return false
        val normalizedRole = RoleUtils.normalizeRole(role)
        if (normalizedRole == RoleUtils.ROLE_OPERATOR) return false

        if (isOwner) {
            return true
        }

        if (!currentAccessControl.permissions.canDeleteEntry) return false

        // PARTNER: Ownership requires targetPartnerUid == currentUid OR createdByUid == currentUid
        val isSelf = currentUid.isNotBlank() && (
            (withdrawalTargetPartnerUid.isNotBlank() && withdrawalTargetPartnerUid == currentUid) ||
            (withdrawalCreatedByUid.isNotBlank() && withdrawalCreatedByUid == currentUid)
        )
        if (!isSelf) {
            return false
        }

        val ageMillis = currentTimeMillis - withdrawalCreatedAt
        return ageMillis in 0 until (24 * 60 * 60 * 1000L)
    }
}
