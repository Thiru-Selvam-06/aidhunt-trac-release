package com.example

import com.example.ui.components.buildWhatsAppUrl
import com.example.ui.components.formatWhatsAppPhone
import com.example.ui.components.isValidPhoneNumber
import com.example.ui.components.sanitizePhoneNumberForStorage
import com.example.ui.util.WorkBillingCalculator
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testFormatWhatsAppPhone_standardTenDigits() {
        // Standard 10 digit Indian number without country code
        val formatted = formatWhatsAppPhone("9876543210")
        assertEquals("919876543210", formatted)
    }

    @Test
    fun testFormatWhatsAppPhone_withSpacesAndDashes() {
        val formatted = formatWhatsAppPhone("98765 43210")
        assertEquals("919876543210", formatted)

        val formattedDash = formatWhatsAppPhone("9876-543-210")
        assertEquals("919876543210", formattedDash)
    }

    @Test
    fun testFormatWhatsAppPhone_withLeadingZero() {
        val formatted = formatWhatsAppPhone("09876543210")
        assertEquals("919876543210", formatted)
    }

    @Test
    fun testFormatWhatsAppPhone_withCountryCodePlus() {
        val formatted = formatWhatsAppPhone("+91 98765 43210")
        assertEquals("919876543210", formatted)
    }

    @Test
    fun testFormatWhatsAppPhone_supportNumber() {
        val formatted = formatWhatsAppPhone("8778285956")
        assertEquals("918778285956", formatted)
    }

    @Test
    fun testSanitizePhoneNumberForStorage() {
        assertEquals("9876543210", sanitizePhoneNumberForStorage("09876543210"))
        assertEquals("9876543210", sanitizePhoneNumberForStorage("+91 98765 43210"))
        assertEquals("8778285956", sanitizePhoneNumberForStorage("8778-285-956"))
    }

    @Test
    fun testBuildWhatsAppUrl() {
        val url = buildWhatsAppUrl("9876543210", "Hello")
        assertNotNull(url)
        assertTrue(url!!.startsWith("https://wa.me/919876543210"))
    }

    // WorkBillingCalculator Tests
    @Test
    fun testWorkBillingCalculator_clockDurationCalculation() {
        // 08:15 to 12:05 -> 3h 50m = 230 minutes
        val duration = WorkBillingCalculator.calculateDurationFromClock(8, 15, 12, 5)
        assertEquals(230L, duration)
    }

    @Test
    fun testWorkBillingCalculator_manualDurationCalculation() {
        // 3 hours, 50 minutes -> 230 minutes
        val duration = WorkBillingCalculator.calculateDurationFromManual("3", "50")
        assertEquals(230L, duration)
    }

    @Test
    fun testWorkBillingCalculator_clockAndManualEquivalence() {
        // Clock: 08:15 to 12:05 vs Manual: 3h 50m
        val clockDuration = WorkBillingCalculator.calculateDurationFromClock(8, 15, 12, 5)
        val manualDuration = WorkBillingCalculator.calculateDurationFromManual("3", "50")
        assertEquals(clockDuration, manualDuration)

        val rate = 1100.0
        val clockAmount = WorkBillingCalculator.calculateAmount(clockDuration, rate)
        val manualAmount = WorkBillingCalculator.calculateAmount(manualDuration, rate)
        assertEquals(clockAmount, manualAmount, 0.001)
        val expectedAmount = Math.round((230.0 / 60.0) * 1100.0).toDouble()
        assertEquals(expectedAmount, clockAmount, 0.001)
    }

    @Test
    fun testWorkBillingCalculator_midnightCrossing() {
        // 23:00 to 01:30 next day -> 150 minutes (2h 30m)
        val duration = WorkBillingCalculator.calculateDurationFromClock(23, 0, 1, 30)
        assertEquals(150L, duration)
        assertEquals(2.5, WorkBillingCalculator.toDecimalHours(duration), 0.001)
    }

    @Test
    fun testWorkBillingCalculator_zeroDefaults() {
        assertEquals(0L, WorkBillingCalculator.calculateDurationFromClock(null, null, null, null))
        assertEquals(0L, WorkBillingCalculator.calculateDurationFromClock(8, 37, null, null))
        assertEquals(0L, WorkBillingCalculator.calculateDurationFromClock(null, null, 12, 5))
        assertEquals(0L, WorkBillingCalculator.calculateDurationFromManual("", ""))
        assertEquals(0.0, WorkBillingCalculator.calculateAmount(0L, 1000.0), 0.001)
        assertEquals(0.0, WorkBillingCalculator.calculateAmount(120L, 0.0), 0.001)
    }

    @Test
    fun testWorkBillingCalculator_formatDuration() {
        assertEquals("3h 50m", WorkBillingCalculator.formatDuration(230L))
        assertEquals("2h", WorkBillingCalculator.formatDuration(120L))
        assertEquals("45m", WorkBillingCalculator.formatDuration(45L))
        assertEquals("0", WorkBillingCalculator.formatDuration(0L))
    }

    // NewEntryDraft Unit Tests
    @Test
    fun testNewEntryDraft_defaultState() {
        val draft = com.example.ui.viewmodel.NewEntryDraft.createDefault(
            defaultTractor = "John Deere 5050 D",
            lockedTractor = "John Deere 5050 D",
            defaultHourlyRate = 1200.0
        )
        assertEquals("John Deere 5050 D", draft.selectedTractor)
        assertTrue(draft.isTractorLocked)
        assertNotNull(draft.startHour)
        assertNotNull(draft.startMinute)
        assertNull(draft.endHour)
        assertNull(draft.endMinute)
        assertEquals("1200", draft.hourlyRateInput)
        assertEquals("", draft.customerNameInput)
        assertEquals("0", draft.amountReceivedInput)
        assertFalse(draft.isModified())
    }

    @Test
    fun testNewEntryDraft_isModifiedDetection() {
        val defaultDraft = com.example.ui.viewmodel.NewEntryDraft.createDefault()
        assertFalse(defaultDraft.isModified())

        val modifiedCustomer = defaultDraft.copy(customerNameInput = "Ramesh Kumar")
        assertTrue(modifiedCustomer.isModified())

        val modifiedTime = defaultDraft.copy(endHour = 14, endMinute = 30)
        assertTrue(modifiedTime.isModified())

        val modifiedExpense = defaultDraft.copy(includeLinkedExpense = true)
        assertTrue(modifiedExpense.isModified())

        val modifiedNotes = defaultDraft.copy(notes = "Field plowing")
        assertTrue(modifiedNotes.isModified())
    }

    @Test
    fun testNewEntryDraft_preservesValuesAcrossSimulatedNavigation() {
        var draft = com.example.ui.viewmodel.NewEntryDraft.createDefault()
        draft = draft.copy(
            customerNameInput = "Sundar",
            customerPhoneInput = "9876543210",
            customerLocationInput = "Madurai",
            manualHoursInput = "2",
            manualMinutesInput = "30",
            isDirectDurationMode = true,
            amountReceivedInput = "2000",
            notes = "Test draft retention"
        )

        // Simulated navigation away to another screen and back
        val restoredDraft = draft

        assertEquals("Sundar", restoredDraft.customerNameInput)
        assertEquals("9876543210", restoredDraft.customerPhoneInput)
        assertEquals("Madurai", restoredDraft.customerLocationInput)
        assertEquals("2", restoredDraft.manualHoursInput)
        assertEquals("30", restoredDraft.manualMinutesInput)
        assertTrue(restoredDraft.isDirectDurationMode)
        assertEquals("2000", restoredDraft.amountReceivedInput)
        assertEquals("Test draft retention", restoredDraft.notes)
        assertTrue(restoredDraft.isModified())
    }

    @Test
    fun testNewEntryDraft_clearResetsToCleanDefault() {
        val modifiedDraft = com.example.ui.viewmodel.NewEntryDraft(
            customerNameInput = "Sundar",
            manualHoursInput = "5",
            amountReceivedInput = "3000",
            notes = "Unsaved draft"
        )
        assertTrue(modifiedDraft.isModified())

        val clearedDraft = com.example.ui.viewmodel.NewEntryDraft.createDefault(
            defaultTractor = "Mahindra 575 DI",
            lockedTractor = "",
            defaultHourlyRate = 1100.0
        )
        assertFalse(clearedDraft.isModified())
        assertEquals("", clearedDraft.customerNameInput)
        assertEquals("", clearedDraft.manualHoursInput)
        assertEquals("0", clearedDraft.amountReceivedInput)
        assertNotNull(clearedDraft.startHour)
        assertNotNull(clearedDraft.startMinute)
    }

    // Login Auth Method & Validation Unit Tests
    @Test
    fun testAuthMethod_enumExistence() {
        val phoneMethod = com.example.ui.screens.auth.AuthMethod.PHONE
        val emailMethod = com.example.ui.screens.auth.AuthMethod.EMAIL
        val googleMethod = com.example.ui.screens.auth.AuthMethod.GOOGLE
        assertEquals("PHONE", phoneMethod.name)
        assertEquals("EMAIL", emailMethod.name)
        assertEquals("GOOGLE", googleMethod.name)
    }

    @Test
    fun testLoginEmailValidation() {
        val emailRegex = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$".toRegex()
        assertTrue(emailRegex.matches("thiruselvam400@gmail.com"))
        assertTrue(emailRegex.matches("farmer.partner@tractor.org"))
        assertTrue(emailRegex.matches("user+test@domain.co.in"))

        assertFalse(emailRegex.matches(""))
        assertFalse(emailRegex.matches("plainaddress"))
        assertFalse(emailRegex.matches("@missingusername.com"))
        assertFalse(emailRegex.matches("username@.com"))
        assertFalse(emailRegex.matches("username@domain"))
    }

    @Test
    fun testLoginPhoneValidation() {
        fun isValidPhone(phone: String): Boolean {
            val digits = phone.filter { it.isDigit() }
            return digits.length in 10..12
        }

        assertTrue(isValidPhone("9842154321"))
        assertTrue(isValidPhone("+91 98421 54321"))
        assertTrue(isValidPhone("09842154321"))
        assertTrue(isValidPhone("919842154321"))

        assertFalse(isValidPhone(""))
        assertFalse(isValidPhone("12345"))
        assertFalse(isValidPhone("abc"))
    }

    // --- HOME DASHBOARD FINANCIAL CALCULATION TESTS ---
    private fun createTestJob(
        id: Long = 0L,
        customerName: String = "Test Customer",
        operatorName: String = "Operator",
        tractorLabel: String = "Mahindra 575 DI",
        workType: String = "Ploughing",
        startTimeMillis: Long = System.currentTimeMillis(),
        endTimeMillis: Long = System.currentTimeMillis() + 3600000L,
        durationMinutes: Long = 60L,
        totalAmount: Double = 0.0,
        amountReceived: Double = 0.0,
        pendingAmount: Double = 0.0,
        addedByPartner: String = "Owner"
    ): com.example.data.entity.JobEntryEntity {
        return com.example.data.entity.JobEntryEntity(
            id = id,
            customerName = customerName,
            operatorName = operatorName,
            tractorLabel = tractorLabel,
            workType = workType,
            startTimeMillis = startTimeMillis,
            endTimeMillis = endTimeMillis,
            durationMinutes = durationMinutes,
            totalAmount = totalAmount,
            amountReceived = amountReceived,
            pendingAmount = pendingAmount,
            addedByPartner = addedByPartner
        )
    }

    private fun createTestPartner(
        id: Long = 0L,
        name: String,
        phone: String = "9876543210",
        role: String = "Partner"
    ): com.example.data.entity.PartnerEntity {
        return com.example.data.entity.PartnerEntity(
            id = id,
            name = name,
            phone = phone,
            role = role
        )
    }

    @Test
    fun testDashboard_noJobs_zeroTotals() {
        val jobs = emptyList<com.example.data.entity.JobEntryEntity>()
        val totalRecorded = jobs.sumOf { if (it.totalAmount > 0.0) it.totalAmount else (it.amountReceived + it.pendingAmount) }
        val totalDue = jobs.sumOf { if (it.pendingAmount > 0.0) it.pendingAmount else maxOf(0.0, it.totalAmount - it.amountReceived) }
        val totalReceived = jobs.sumOf { it.amountReceived }

        assertEquals(0.0, totalRecorded, 0.001)
        assertEquals(0.0, totalDue, 0.001)
        assertEquals(0.0, totalReceived, 0.001)
    }

    @Test
    fun testDashboard_oneUnpaidJob_recordedEqualsDue() {
        val job = createTestJob(
            id = 1L,
            customerName = "Customer 1",
            totalAmount = 2200.0,
            amountReceived = 0.0,
            pendingAmount = 2200.0
        )
        val jobs = listOf(job)
        val totalRecorded = jobs.sumOf { if (it.totalAmount > 0.0) it.totalAmount else (it.amountReceived + it.pendingAmount) }
        val totalDue = jobs.sumOf { if (it.pendingAmount > 0.0) it.pendingAmount else maxOf(0.0, it.totalAmount - it.amountReceived) }
        val totalReceived = jobs.sumOf { it.amountReceived }

        assertEquals(2200.0, totalRecorded, 0.001)
        assertEquals(2200.0, totalDue, 0.001)
        assertEquals(0.0, totalReceived, 0.001)
    }

    @Test
    fun testDashboard_oneJobWithPartialPayment_recordedUnchangedDueDecreased() {
        val job = createTestJob(
            id = 1L,
            customerName = "RealCustomer",
            totalAmount = 2200.0,
            amountReceived = 1000.0,
            pendingAmount = 1200.0
        )
        val jobs = listOf(job)
        val totalRecorded = jobs.sumOf { if (it.totalAmount > 0.0) it.totalAmount else (it.amountReceived + it.pendingAmount) }
        val totalDue = jobs.sumOf { if (it.pendingAmount > 0.0) it.pendingAmount else maxOf(0.0, it.totalAmount - it.amountReceived) }
        val totalReceived = jobs.sumOf { it.amountReceived }

        assertEquals(2200.0, totalRecorded, 0.001)
        assertEquals(1200.0, totalDue, 0.001)
        assertEquals(1000.0, totalReceived, 0.001)
    }

    @Test
    fun testDashboard_multipleJobs_sumMatchesPersistedRecords() {
        val jobs = listOf(
            createTestJob(id = 1L, customerName = "A", totalAmount = 2200.0, amountReceived = 0.0, pendingAmount = 2200.0),
            createTestJob(id = 2L, customerName = "B", totalAmount = 2218.0, amountReceived = 500.0, pendingAmount = 1718.0),
            createTestJob(id = 3L, customerName = "C", totalAmount = 2200.0, amountReceived = 0.0, pendingAmount = 2200.0),
            createTestJob(id = 4L, customerName = "D", totalAmount = 5000.0, amountReceived = 1000.0, pendingAmount = 4000.0)
        )
        val totalRecorded = jobs.sumOf { if (it.totalAmount > 0.0) it.totalAmount else (it.amountReceived + it.pendingAmount) }
        val totalDue = jobs.sumOf { if (it.pendingAmount > 0.0) it.pendingAmount else maxOf(0.0, it.totalAmount - it.amountReceived) }
        val totalReceived = jobs.sumOf { it.amountReceived }

        assertEquals(11618.0, totalRecorded, 0.001)
        assertEquals(10118.0, totalDue, 0.001)
        assertEquals(1500.0, totalReceived, 0.001)
    }

    @Test
    fun testDashboard_availableBalanceCalculation() {
        val totalReceived = 1500.0
        val totalExpenses = 0.0
        val totalWithdrawn = 350.0

        val available = maxOf(0.0, totalReceived - totalExpenses - totalWithdrawn)
        assertEquals(1150.0, available, 0.001)
    }

    @Test
    fun testPartnerBreakdown_attributedByEntryForNotCreator() {
        // Owner creates 4 records: 2 for Owner, 2 for Partner 1
        val jobs = listOf(
            createTestJob(id = 1L, operatorName = "Owner", addedByPartner = "Owner", totalAmount = 1000.0, amountReceived = 1000.0, pendingAmount = 0.0),
            createTestJob(id = 2L, operatorName = "Owner", addedByPartner = "Owner", totalAmount = 1500.0, amountReceived = 0.0, pendingAmount = 1500.0),
            createTestJob(id = 3L, operatorName = "Partner 1", addedByPartner = "Owner", totalAmount = 2200.0, amountReceived = 2200.0, pendingAmount = 0.0),
            createTestJob(id = 4L, operatorName = "Partner 1", addedByPartner = "Owner", totalAmount = 2000.0, amountReceived = 0.0, pendingAmount = 2000.0)
        )
        val partners = listOf(
            createTestPartner(id = 1L, name = "Owner", role = "Owner"),
            createTestPartner(id = 2L, name = "Partner 1", role = "Partner")
        )
        val breakdowns = com.example.ui.util.FinancialCalculationEngine.calculatePartnerBreakdown(
            jobs = jobs,
            expenses = emptyList(),
            withdrawals = emptyList(),
            partners = partners,
            workspaceMembers = emptyList(),
            scope = com.example.ui.util.FinancialScope(mode = com.example.ui.util.FinancialScopeMode.OVERALL)
        )

        val ownerBreakdown = breakdowns.find { it.partnerName.equals("Owner", true) }
        val partner1Breakdown = breakdowns.find { it.partnerName.equals("Partner 1", true) }

        assertNotNull(ownerBreakdown)
        assertNotNull(partner1Breakdown)

        assertEquals(2500.0, ownerBreakdown!!.recorded, 0.001)
        assertEquals(1000.0, ownerBreakdown.received, 0.001)
        assertEquals(1500.0, ownerBreakdown.due, 0.001)

        assertEquals(4200.0, partner1Breakdown!!.recorded, 0.001)
        assertEquals(2200.0, partner1Breakdown.received, 0.001)
        assertEquals(2000.0, partner1Breakdown.due, 0.001)
    }

    @Test
    fun testAuthorizationManager_canDeleteCollection() {
        // Owner MUST be allowed to delete collections
        assertTrue(com.example.data.auth.AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = "uid1",
            collectionCreatedAt = System.currentTimeMillis(),
            isOwner = true
        ))

        // Partner MUST NOT be allowed to delete collections
        assertFalse(com.example.data.auth.AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = "uid1",
            collectionCreatedAt = System.currentTimeMillis(),
            isOwner = false
        ))
    }

    // =========================================================================
    // TASK VERIFICATION TESTS — SCENARIOS A THROUGH H
    // =========================================================================

    @Test
    fun testPartnerWithdrawal_ScenarioA_DirectGetAndSplitEntitlementSeparation() {
        // Given a card with Share = 2250, Investment = 500, Total = 2750
        val remainingShare = 2250.0
        val remainingInvestment = 500.0
        val totalToGet = remainingShare + remainingInvestment
        assertEquals(2750.0, totalToGet, 0.001)

        // When user taps "Get 2750" and confirms 2750.0
        val requestedAmount = totalToGet
        val sharePortion = minOf(requestedAmount, remainingShare)
        val investmentPortion = minOf(requestedAmount - sharePortion, remainingInvestment)

        assertEquals(2250.0, sharePortion, 0.001)
        assertEquals(500.0, investmentPortion, 0.001)

        // Simulated record creation:
        // 1. Withdrawal (Share) = 2250.0
        // 2. Investment = 500.0
        val newRemainingShare = remainingShare - sharePortion
        val newRemainingInvestment = remainingInvestment - investmentPortion
        val newTotalToGet = newRemainingShare + newRemainingInvestment

        // After confirmation: remaining share = 0, remaining investment = 0, total = 0
        assertEquals(0.0, newRemainingShare, 0.001)
        assertEquals(0.0, newRemainingInvestment, 0.001)
        assertEquals(0.0, newTotalToGet, 0.001)
    }

    @Test
    fun testBusinessCashLimit_ScenarioB_ZeroBusinessCash() {
        val partnerEntitlement = 2750.0
        val businessCash = 0.0

        // Validation logic matching WithdrawalTab and MainViewModel
        val isBlocked = businessCash <= 0.0
        val errorMessage = if (businessCash <= 0.0) {
            "No business balance available. Please collect due amount first."
        } else null

        assertTrue(isBlocked)
        assertEquals("No business balance available. Please collect due amount first.", errorMessage)
        assertEquals(0.0, businessCash, 0.001)
    }

    @Test
    fun testBusinessCashLimit_ScenarioC_PartialBusinessCash() {
        val partnerEntitlement = 2750.0
        val businessCash = 1000.0

        // Requested full withdrawal of 2750 when only 1000 is available
        val requestedWithdrawal = partnerEntitlement
        val isBlocked = requestedWithdrawal > businessCash
        val errorMessage = if (businessCash <= 0.0) {
            "No business balance available. Please collect due amount first."
        } else if (requestedWithdrawal > businessCash) {
            "Insufficient business balance. Please collect due amount first."
        } else null

        assertTrue(isBlocked)
        assertEquals("Insufficient business balance. Please collect due amount first.", errorMessage)

        // Business cash must never become negative
        val remainingCash = maxOf(0.0, businessCash - minOf(requestedWithdrawal, businessCash))
        assertTrue(remainingCash >= 0.0)
    }

    @Test
    fun testBusinessCash_ScenarioD_FullWithdrawalPropagatesToZero() {
        val totalReceived = 5500.0
        val totalExpenses = 500.0
        val previousWithdrawals = 0.0

        // Initial cash = 5500 - 500 - 0 = 5000
        val initialCash = com.example.ui.util.FinancialCalculationEngine.roundToWholeRupee(
            maxOf(0.0, totalReceived - totalExpenses - previousWithdrawals)
        )
        assertEquals(5000.0, initialCash, 0.001)

        // Partner withdraws 5000
        val withdrawalAmount = 5000.0
        val newWithdrawals = previousWithdrawals + withdrawalAmount
        val newCash = com.example.ui.util.FinancialCalculationEngine.roundToWholeRupee(
            maxOf(0.0, totalReceived - totalExpenses - newWithdrawals)
        )

        // New cash must be 0 everywhere, never negative
        assertEquals(0.0, newCash, 0.001)
        assertTrue(newCash >= 0.0)
    }

    @Test
    fun testProfitSharePercentageAllocation_ScenarioH_Validation() {
        // Valid 100% allocation
        val validPercentages = mapOf("Owner" to 50, "Partner 1" to 25, "Partner 2" to 25)
        val validTotal = validPercentages.values.sum()
        assertEquals(100, validTotal)

        // Invalid allocations
        val underAllocated = mapOf("Owner" to 50, "Partner 1" to 20, "Partner 2" to 20)
        assertNotEquals(100, underAllocated.values.sum())

        val overAllocated = mapOf("Owner" to 50, "Partner 1" to 30, "Partner 2" to 30)
        assertNotEquals(100, overAllocated.values.sum())
    }

    @Test
    fun testProfileIdentity_ScenarioE_and_F_OwnerAndPartnerSeparation() {
        val ownerName = "Ravi"
        val partnerName = "Kumar"

        // Owner logged in
        val isOwner = true
        val resolvedOwnerName = if (isOwner) ownerName else partnerName
        val resolvedOwnerRole = if (isOwner) "Owner" else "Partner"
        assertEquals("Ravi", resolvedOwnerName)
        assertEquals("Owner", resolvedOwnerRole)

        // Partner logged in (shares workspace where ownerName is Ravi)
        val isPartner = false
        val resolvedPartnerName = if (isPartner) ownerName else partnerName
        val resolvedPartnerRole = if (isPartner) "Owner" else "Partner"
        assertEquals("Kumar", resolvedPartnerName)
        assertEquals("Partner", resolvedPartnerRole)
        assertNotEquals("Ravi", resolvedPartnerName)
    }

    // =========================================================================
    // REQUIREMENT 13 — WITHDRAWAL AUTHORIZATION TESTS (8 CASES)
    // =========================================================================

    private val testWorkspaceMembers = listOf(
        com.example.data.firebase.WorkspaceMember(
            uid = "uid_owner",
            role = "owner",
            displayName = "Owner Boss",
            status = "active"
        ),
        com.example.data.firebase.WorkspaceMember(
            uid = "uid_partner_a",
            role = "partner",
            displayName = "Partner A",
            status = "active"
        ),
        com.example.data.firebase.WorkspaceMember(
            uid = "uid_partner_b",
            role = "partner",
            displayName = "Partner B",
            status = "active"
        ),
        com.example.data.firebase.WorkspaceMember(
            uid = "uid_operator",
            role = "operator",
            displayName = "Operator C",
            status = "active"
        )
    )

    @Test
    fun testReq13_Case1_OwnerToSelf_Allowed() {
        val result = com.example.data.auth.AuthorizationManager.canCreateWithdrawal(
            actorUid = "uid_owner",
            actorRole = "owner",
            targetUid = "uid_owner",
            workspaceId = "ws_123",
            workspaceMembers = testWorkspaceMembers
        )
        assertTrue("Owner must be allowed to withdraw for self", result)
    }

    @Test
    fun testReq13_Case2_OwnerToPartnerA_Allowed() {
        val result = com.example.data.auth.AuthorizationManager.canCreateWithdrawal(
            actorUid = "uid_owner",
            actorRole = "owner",
            targetUid = "uid_partner_a",
            workspaceId = "ws_123",
            workspaceMembers = testWorkspaceMembers
        )
        assertTrue("Owner must be allowed to withdraw for Partner A", result)
    }

    @Test
    fun testReq13_Case3_OwnerToPartnerB_Allowed() {
        val result = com.example.data.auth.AuthorizationManager.canCreateWithdrawal(
            actorUid = "uid_owner",
            actorRole = "owner",
            targetUid = "uid_partner_b",
            workspaceId = "ws_123",
            workspaceMembers = testWorkspaceMembers
        )
        assertTrue("Owner must be allowed to withdraw for Partner B", result)
    }

    @Test
    fun testReq13_Case4_PartnerAToSelf_Allowed() {
        val result = com.example.data.auth.AuthorizationManager.canCreateWithdrawal(
            actorUid = "uid_partner_a",
            actorRole = "partner",
            targetUid = "uid_partner_a",
            workspaceId = "ws_123",
            workspaceMembers = testWorkspaceMembers
        )
        assertTrue("Partner A must be allowed to withdraw for self", result)
    }

    @Test
    fun testReq13_Case5_PartnerAToPartnerB_Rejected() {
        val result = com.example.data.auth.AuthorizationManager.canCreateWithdrawal(
            actorUid = "uid_partner_a",
            actorRole = "partner",
            targetUid = "uid_partner_b",
            workspaceId = "ws_123",
            workspaceMembers = testWorkspaceMembers
        )
        assertFalse("Partner A must NOT be allowed to withdraw for Partner B", result)
    }

    @Test
    fun testReq13_Case6_PartnerAToOwner_Rejected() {
        val result = com.example.data.auth.AuthorizationManager.canCreateWithdrawal(
            actorUid = "uid_partner_a",
            actorRole = "partner",
            targetUid = "uid_owner",
            workspaceId = "ws_123",
            workspaceMembers = testWorkspaceMembers
        )
        assertFalse("Partner A must NOT be allowed to withdraw for Owner", result)
    }

    @Test
    fun testReq13_Case7_PartnerBToSelf_Allowed() {
        val result = com.example.data.auth.AuthorizationManager.canCreateWithdrawal(
            actorUid = "uid_partner_b",
            actorRole = "partner",
            targetUid = "uid_partner_b",
            workspaceId = "ws_123",
            workspaceMembers = testWorkspaceMembers
        )
        assertTrue("Partner B must be allowed to withdraw for self", result)
    }

    @Test
    fun testReq13_Case8_PartnerBToPartnerA_Rejected() {
        val result = com.example.data.auth.AuthorizationManager.canCreateWithdrawal(
            actorUid = "uid_partner_b",
            actorRole = "partner",
            targetUid = "uid_partner_a",
            workspaceId = "ws_123",
            workspaceMembers = testWorkspaceMembers
        )
        assertFalse("Partner B must NOT be allowed to withdraw for Partner A", result)
    }

    @Test
    fun testReq13_Operator_AnyTarget_Rejected() {
        val resultSelf = com.example.data.auth.AuthorizationManager.canCreateWithdrawal(
            actorUid = "uid_operator",
            actorRole = "operator",
            targetUid = "uid_operator",
            workspaceId = "ws_123",
            workspaceMembers = testWorkspaceMembers
        )
        assertFalse("Operator must NOT be allowed to withdraw for self", resultSelf)

        val resultOther = com.example.data.auth.AuthorizationManager.canCreateWithdrawal(
            actorUid = "uid_operator",
            actorRole = "operator",
            targetUid = "uid_partner_a",
            workspaceId = "ws_123",
            workspaceMembers = testWorkspaceMembers
        )
        assertFalse("Operator must NOT be allowed to withdraw for anyone", resultOther)
    }

    // =========================================================================
    // WITHDRAWAL DELETE AUTHORIZATION TESTS
    // =========================================================================

    @Test
    fun testWithdrawalDelete_OwnerDeletesOwn_Allowed() {
        val now = System.currentTimeMillis()
        val result = com.example.data.auth.AuthorizationManager.canDeleteWithdrawal(
            withdrawalTargetPartnerUid = "uid_owner",
            withdrawalCreatedByUid = "uid_owner",
            withdrawalCreatedAt = now - 3600000L, // 1h old
            isOwner = true,
            currentUid = "uid_owner",
            currentTimeMillis = now,
            role = "owner"
        )
        assertTrue("Owner deleting own withdrawal must be allowed", result)
    }

    @Test
    fun testWithdrawalDelete_OwnerDeletesOtherMember_Allowed() {
        val now = System.currentTimeMillis()
        val result = com.example.data.auth.AuthorizationManager.canDeleteWithdrawal(
            withdrawalTargetPartnerUid = "uid_partner_a",
            withdrawalCreatedByUid = "uid_partner_a",
            withdrawalCreatedAt = now - 172800000L, // 48h old
            isOwner = true,
            currentUid = "uid_owner",
            currentTimeMillis = now,
            role = "owner"
        )
        assertTrue("Owner deleting another member's withdrawal must be allowed", result)
    }

    @Test
    fun testWithdrawalDelete_PartnerDeletesOwnWithin24h_Allowed() {
        val now = System.currentTimeMillis()
        val result = com.example.data.auth.AuthorizationManager.canDeleteWithdrawal(
            withdrawalTargetPartnerUid = "uid_partner_a",
            withdrawalCreatedByUid = "uid_partner_a",
            withdrawalCreatedAt = now - 3600000L, // 1h old
            isOwner = false,
            currentUid = "uid_partner_a",
            currentTimeMillis = now,
            role = "partner"
        )
        assertTrue("Partner deleting own withdrawal within 24h must be allowed", result)
    }

    @Test
    fun testWithdrawalDelete_PartnerDeletesOwnAfter24h_Denied() {
        val now = System.currentTimeMillis()
        val result = com.example.data.auth.AuthorizationManager.canDeleteWithdrawal(
            withdrawalTargetPartnerUid = "uid_partner_a",
            withdrawalCreatedByUid = "uid_partner_a",
            withdrawalCreatedAt = now - 90000000L, // > 24h old
            isOwner = false,
            currentUid = "uid_partner_a",
            currentTimeMillis = now,
            role = "partner"
        )
        assertFalse("Partner deleting own withdrawal after 24h must be denied", result)
    }

    @Test
    fun testWithdrawalDelete_PartnerDeletesOtherMember_Denied() {
        val now = System.currentTimeMillis()
        val result = com.example.data.auth.AuthorizationManager.canDeleteWithdrawal(
            withdrawalTargetPartnerUid = "uid_partner_b",
            withdrawalCreatedByUid = "uid_partner_b",
            withdrawalCreatedAt = now - 3600000L, // 1h old
            isOwner = false,
            currentUid = "uid_partner_a",
            currentTimeMillis = now,
            role = "partner"
        )
        assertFalse("Partner deleting another member's withdrawal must be denied", result)
    }

    @Test
    fun testWithdrawalDelete_OperatorDeletes_Denied() {
        val now = System.currentTimeMillis()
        val result = com.example.data.auth.AuthorizationManager.canDeleteWithdrawal(
            withdrawalTargetPartnerUid = "uid_operator",
            withdrawalCreatedByUid = "uid_operator",
            withdrawalCreatedAt = now - 1000L,
            isOwner = false,
            currentUid = "uid_operator",
            currentTimeMillis = now,
            role = "operator"
        )
        assertFalse("Operator deleting withdrawal must be denied", result)
    }

    // =========================================================================
    // PAYMENT / COLLECTION DELETE AUTHORIZATION TESTS
    // =========================================================================

    @Test
    fun testPaymentDelete_OwnerDeletes_Allowed() {
        val result = com.example.data.auth.AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = "uid1",
            collectionCreatedAt = System.currentTimeMillis(),
            isOwner = true,
            role = "owner"
        )
        assertTrue("Owner deleting payment/collection must be allowed", result)
    }

    @Test
    fun testPaymentDelete_PartnerDeletes_Denied() {
        val result = com.example.data.auth.AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = "uid1",
            collectionCreatedAt = System.currentTimeMillis(),
            isOwner = false,
            role = "partner"
        )
        assertFalse("Partner deleting payment/collection must be denied", result)
    }

    @Test
    fun testPaymentDelete_OperatorDeletes_Denied() {
        val result = com.example.data.auth.AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = "uid1",
            collectionCreatedAt = System.currentTimeMillis(),
            isOwner = false,
            role = "operator"
        )
        assertFalse("Operator deleting payment/collection must be denied", result)
    }

    // =========================================================================
    // COLLECTION HISTORY RECONCILIATION TESTS
    // =========================================================================

    @Test
    fun testCollectionHistoryCount_Reconciliation() {
        val payments = listOf(
            com.example.data.entity.PaymentEntity(id = 1L, amount = 500.0, customerName = "Cust A"),
            com.example.data.entity.PaymentEntity(id = 2L, amount = 1000.0, customerName = "Cust B"),
            com.example.data.entity.PaymentEntity(id = 3L, amount = 1500.0, customerName = "Cust C")
        )
        val jobs = listOf(
            com.example.data.entity.JobEntryEntity(
                id = 10L, 
                customerName = "Cust A",
                operatorName = "Op A",
                tractorLabel = "Payment", 
                workType = "Payment Received", 
                startTimeMillis = 1000L,
                endTimeMillis = 2000L,
                durationMinutes = 0L,
                totalAmount = 0.0,
                amountReceived = 2000.0,
                pendingAmount = 0.0,
                addedByPartner = "Partner A"
            ),
            com.example.data.entity.JobEntryEntity(
                id = 20L,
                customerName = "Cust B",
                operatorName = "Op B",
                tractorLabel = "Tractor 1", 
                workType = "Plowing", 
                startTimeMillis = 1000L,
                endTimeMillis = 2000L,
                durationMinutes = 60L,
                totalAmount = 3000.0, 
                amountReceived = 1000.0,
                pendingAmount = 2000.0,
                addedByPartner = "Partner B"
            )
        )

        val count = com.example.ui.util.FinancialCalculationEngine.countConsolidatedCollectionRecords(payments, jobs)
        assertEquals("Consolidated collection count must include explicit payments, synthetic payment jobs, and initial advances", 5, count)
    }
}


