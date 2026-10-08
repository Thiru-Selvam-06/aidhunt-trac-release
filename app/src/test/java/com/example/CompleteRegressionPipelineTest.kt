package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthorizationManager
import com.example.data.auth.RoleUtils
import com.example.data.database.AppDatabase
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.firebase.WorkspaceMember
import com.example.data.firebase.toFirestoreMap
import com.example.ui.util.FinancialCalculationEngine
import com.example.ui.util.FinancialScope
import com.example.ui.util.FinancialScopeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CompleteRegressionPipelineTest {

    private lateinit var database: AppDatabase
    private val workspaceA = "workspace_alpha_101"
    private val workspaceB = "workspace_beta_202"

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createJob(
        id: Long,
        wsId: String = workspaceA,
        customerName: String = "Test Customer",
        total: Double = 1000.0,
        received: Double = 0.0,
        pending: Double = 1000.0
    ): JobEntryEntity = JobEntryEntity(
        id = id,
        workspaceId = wsId,
        customerName = customerName,
        customerPhone = "9876543210",
        operatorName = "Operator",
        tractorLabel = "Tractor 1",
        workType = "Rotavator",
        startTimeMillis = 1000L,
        endTimeMillis = 5000L,
        durationMinutes = 60L,
        totalAmount = total,
        amountReceived = received,
        pendingAmount = pending,
        addedByPartner = "Owner",
        createdAt = 1000L
    )

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 7: FINANCIAL INVARIANTS TEST
    // Invariant: Total Recorded - Total Due = Total Collected
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testFinancialInvariantTotals() {
        val job1 = createJob(id = 1L, total = 25000.0, received = 5000.0, pending = 20000.0)
        val job2 = createJob(id = 2L, total = 15000.0, received = 15000.0, pending = 0.0)
        val job3 = createJob(id = 3L, total = 10000.0, received = 0.0, pending = 10000.0)

        val jobs = listOf(job1, job2, job3)
        val summary = FinancialCalculationEngine.calculateSummary(
            jobs = jobs,
            expenses = emptyList(),
            withdrawals = emptyList(),
            scope = FinancialScope(FinancialScopeMode.OVERALL)
        )

        // Total Recorded = 25000 + 15000 + 10000 = 50000
        assertEquals(50000.0, summary.totalRecorded, 0.01)
        // Total Collected = 5000 + 15000 + 0 = 20000
        assertEquals(20000.0, summary.totalReceived, 0.01)
        // Total Due = 20000 + 0 + 10000 = 30000
        assertEquals(30000.0, summary.totalDue, 0.01)

        // Strict Invariant: Total Recorded - Total Due == Total Collected
        assertEquals(summary.totalReceived, summary.totalRecorded - summary.totalDue, 0.01)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 8: OWNER / PARTNER / OPERATOR ROLE AUTHORIZATION TESTS
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testRoleAuthorizationRules() {
        val now = System.currentTimeMillis()
        val within24h = now - (2 * 3600 * 1000) // 2 hours ago
        val past24h = now - (25 * 3600 * 1000)   // 25 hours ago

        // 1. OWNER
        // Full authority: no 24h limit, can delete any member's collection
        assertTrue(
            "Owner can delete partner payment within 24h",
            AuthorizationManager.canDeleteCollection(
                collectionCreatedByUid = "partner_uid",
                collectionCreatedAt = within24h,
                isOwner = true,
                currentUid = "owner_uid",
                currentTimeMillis = now,
                role = RoleUtils.ROLE_OWNER
            )
        )
        assertTrue(
            "Owner can delete partner payment even after 24h",
            AuthorizationManager.canDeleteCollection(
                collectionCreatedByUid = "partner_uid",
                collectionCreatedAt = past24h,
                isOwner = true,
                currentUid = "owner_uid",
                currentTimeMillis = now,
                role = RoleUtils.ROLE_OWNER
            )
        )
        assertTrue("Owner can view business overview", AuthorizationManager.canViewBusinessOverview(isOwner = true))
        assertTrue("Owner can view withdrawals", AuthorizationManager.canViewWithdrawals(isOwner = true))
        assertTrue("Owner can delete customer", AuthorizationManager.canDeleteCustomer(isOwner = true))

        // 2. PARTNER
        // Can delete own collection within 24h
        assertTrue(
            "Partner can delete own payment within 24h",
            AuthorizationManager.canDeleteCollection(
                collectionCreatedByUid = "partner_uid",
                collectionCreatedAt = within24h,
                isOwner = false,
                currentUid = "partner_uid",
                currentTimeMillis = now,
                role = RoleUtils.ROLE_PARTNER
            )
        )
        // Cannot delete own payment after 24h
        assertFalse(
            "Partner CANNOT delete own payment after 24h",
            AuthorizationManager.canDeleteCollection(
                collectionCreatedByUid = "partner_uid",
                collectionCreatedAt = past24h,
                isOwner = false,
                currentUid = "partner_uid",
                currentTimeMillis = now,
                role = RoleUtils.ROLE_PARTNER
            )
        )
        // Cannot delete another user's collection
        assertFalse(
            "Partner CANNOT delete another user payment",
            AuthorizationManager.canDeleteCollection(
                collectionCreatedByUid = "other_partner_uid",
                collectionCreatedAt = within24h,
                isOwner = false,
                currentUid = "partner_uid",
                currentTimeMillis = now,
                role = RoleUtils.ROLE_PARTNER
            )
        )
        assertFalse("Partner cannot delete customer", AuthorizationManager.canDeleteCustomer(isOwner = false))

        // 3. OPERATOR
        // Operator cannot delete collection under any circumstances
        assertFalse(
            "Operator CANNOT delete any collection",
            AuthorizationManager.canDeleteCollection(
                collectionCreatedByUid = "operator_uid",
                collectionCreatedAt = within24h,
                isOwner = false,
                currentUid = "operator_uid",
                currentTimeMillis = now,
                role = RoleUtils.ROLE_OPERATOR
            )
        )
        assertFalse("Operator cannot delete customer", AuthorizationManager.canDeleteCustomer(isOwner = false))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 9: WORKSPACE ISOLATION TESTS
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testWorkspaceIsolation() = runBlocking {
        val jobDao = database.jobEntryDao()
        val customerDao = database.customerDao()
        val expenseDao = database.expenseDao()
        val paymentDao = database.paymentDao()
        val withdrawalDao = database.withdrawalDao()

        // Insert records in Workspace A
        customerDao.insertCustomer(CustomerEntity(id = 1L, workspaceId = workspaceA, name = "Customer in A", phone = "1111111111"))
        jobDao.insertJob(createJob(id = 101L, wsId = workspaceA, customerName = "Customer in A", total = 1000.0, pending = 1000.0))
        expenseDao.insertExpense(ExpenseEntity(id = 201L, workspaceId = workspaceA, expenseType = "Diesel", amount = 500.0, tractorLabel = "T1", addedByPartner = "Owner"))
        paymentDao.insertPayment(PaymentEntity(id = 301L, workspaceId = workspaceA, jobEntryId = 101L, customerId = 1L, customerName = "Customer in A", amount = 500.0))
        withdrawalDao.insertWithdrawal(WithdrawalEntity(id = 401L, workspaceId = workspaceA, partnerName = "Owner", amount = 200.0))

        // Insert records in Workspace B
        customerDao.insertCustomer(CustomerEntity(id = 2L, workspaceId = workspaceB, name = "Customer in B", phone = "2222222222"))
        jobDao.insertJob(createJob(id = 102L, wsId = workspaceB, customerName = "Customer in B", total = 2000.0, pending = 2000.0))
        expenseDao.insertExpense(ExpenseEntity(id = 202L, workspaceId = workspaceB, expenseType = "Repair", amount = 700.0, tractorLabel = "T2", addedByPartner = "Owner"))
        paymentDao.insertPayment(PaymentEntity(id = 302L, workspaceId = workspaceB, jobEntryId = 102L, customerId = 2L, customerName = "Customer in B", amount = 1000.0))
        withdrawalDao.insertWithdrawal(WithdrawalEntity(id = 402L, workspaceId = workspaceB, partnerName = "Partner", amount = 300.0))

        // Verify Workspace A query never returns Workspace B records
        val jobsInA = jobDao.getJobsForWorkspace(workspaceA).first()
        val customersInA = customerDao.getCustomersForWorkspace(workspaceA).first()
        val expensesInA = expenseDao.getExpensesForWorkspace(workspaceA).first()
        val paymentsInA = paymentDao.getPaymentsForWorkspace(workspaceA).first()
        val withdrawalsInA = withdrawalDao.getWithdrawalsForWorkspace(workspaceA).first()

        assertEquals(1, jobsInA.size)
        assertEquals(101L, jobsInA[0].id)
        assertEquals(1, customersInA.size)
        assertEquals("Customer in A", customersInA[0].name)
        assertEquals(1, expensesInA.size)
        assertEquals(201L, expensesInA[0].id)
        assertEquals(1, paymentsInA.size)
        assertEquals(301L, paymentsInA[0].id)
        assertEquals(1, withdrawalsInA.size)
        assertEquals(401L, withdrawalsInA[0].id)

        // Verify Workspace B query never returns Workspace A records
        val jobsInB = jobDao.getJobsForWorkspace(workspaceB).first()
        val paymentsInB = paymentDao.getPaymentsForWorkspace(workspaceB).first()

        assertEquals(1, jobsInB.size)
        assertEquals(102L, jobsInB[0].id)
        assertEquals(1, paymentsInB.size)
        assertEquals(302L, paymentsInB[0].id)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 10: OWNER / PARTNER FINANCIAL ATTRIBUTION BY STABLE UID
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testOwnerPartnerFinancialAttribution() {
        val ownerMember = WorkspaceMember(uid = "owner_uid_1", role = "owner", displayName = "Owner One", status = "active")
        val partnerMember = WorkspaceMember(uid = "partner_uid_2", role = "partner", displayName = "Partner Two", status = "active")

        val ownerInvestment = ExpenseEntity(
            id = 11L,
            workspaceId = workspaceA,
            expenseType = "Investment",
            amount = 14000.0,
            tractorLabel = "Tractor 1",
            addedByPartner = "Owner One",
            paidBy = "I Paid",
            paidByUid = "owner_uid_1",
            createdByUid = "owner_uid_1",
            createdAt = 1000L
        )

        val partnerInvestment = ExpenseEntity(
            id = 12L,
            workspaceId = workspaceA,
            expenseType = "Investment",
            amount = 4000.0,
            tractorLabel = "Tractor 1",
            addedByPartner = "Partner Two",
            paidBy = "I Paid",
            paidByUid = "partner_uid_2",
            createdByUid = "partner_uid_2",
            createdAt = 2000L
        )

        val expenses = listOf(ownerInvestment, partnerInvestment)
        val members = listOf(ownerMember, partnerMember)

        val statuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = emptyList(),
            expenses = expenses,
            withdrawals = emptyList(),
            workspaceMembers = members,
            businessName = "Test Farm"
        )

        val ownerStatus = statuses.find { it.partnerUid == "owner_uid_1" }
        val partnerStatus = statuses.find { it.partnerUid == "partner_uid_2" }

        assertNotNull(ownerStatus)
        assertNotNull(partnerStatus)
        assertEquals(14000.0, ownerStatus!!.totalInvestment, 0.01)
        assertEquals(4000.0, partnerStatus!!.totalInvestment, 0.01)
        assertEquals(18000.0, ownerStatus.totalInvestment + partnerStatus.totalInvestment, 0.01)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 11: CUSTOMER IDENTITY ISOLATION (SAME NAME, DIFFERENT IDS)
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testCustomerIdentityIsolation() = runBlocking {
        val customerDao = database.customerDao()
        val paymentDao = database.paymentDao()

        // Two different customers with the exact same name "Rahul"
        val customerA = CustomerEntity(id = 101L, workspaceId = workspaceA, name = "Rahul", phone = "9876500001", balanceDue = 5000.0)
        val customerB = CustomerEntity(id = 102L, workspaceId = workspaceA, name = "Rahul", phone = "9876500002", balanceDue = 8000.0)
        customerDao.insertCustomer(customerA)
        customerDao.insertCustomer(customerB)

        // Record payment for Customer B
        val paymentForB = PaymentEntity(
            id = 501L,
            workspaceId = workspaceA,
            jobEntryId = 0L,
            customerId = 102L,
            customerName = "Rahul",
            amount = 8000.0,
            collectedByUid = "collector_1",
            collectedAt = 1000L
        )
        paymentDao.insertPayment(paymentForB)

        // Verify payment is strictly attached to customerId 102L and never 101L
        val paymentsForA = paymentDao.getPaymentsForCustomer(101L)
        val paymentsForB = paymentDao.getPaymentsForCustomer(102L)

        assertEquals("Customer A must have 0 payments", 0, paymentsForA.size)
        assertEquals("Customer B must have exactly 1 payment", 1, paymentsForB.size)
        assertEquals(102L, paymentsForB[0].customerId)
        assertEquals(8000.0, paymentsForB[0].amount, 0.01)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 12 & 13: PAYMENT EVENTS & COLLECTION HISTORY MULTI-PARTIAL PAYMENTS
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testMultipleIndependentPaymentEvents() = runBlocking {
        val jobDao = database.jobEntryDao()
        val paymentDao = database.paymentDao()

        // Job = ₹25,000
        val job = createJob(id = 10L, customerName = "Selvam", total = 25000.0, received = 25000.0, pending = 0.0)
        jobDao.insertJob(job)

        // Payment A = ₹5,000, Payment B = ₹20,000
        val paymentA = PaymentEntity(
            id = 1001L,
            workspaceId = workspaceA,
            jobEntryId = 10L,
            customerId = 1L,
            customerName = "Selvam",
            amount = 5000.0,
            collectedByUid = "uid_1",
            collectedAt = 1000L
        )
        val paymentB = PaymentEntity(
            id = 1002L,
            workspaceId = workspaceA,
            jobEntryId = 10L,
            customerId = 1L,
            customerName = "Selvam",
            amount = 20000.0,
            collectedByUid = "uid_2",
            collectedAt = 2000L
        )
        paymentDao.insertPayment(paymentA)
        paymentDao.insertPayment(paymentB)

        val payments = paymentDao.getPaymentsForCustomer(1L)
        assertEquals("Must have 2 independent payment events", 2, payments.size)
        assertEquals(20000.0, payments[0].amount, 0.01)
        assertEquals(5000.0, payments[1].amount, 0.01)

        // Delete Payment B
        paymentDao.deletePayment(paymentB.id)
        val updatedJob = job.copy(
            amountReceived = 5000.0,
            pendingAmount = 20000.0
        )
        jobDao.insertJob(updatedJob)

        val paymentsAfterDelete = paymentDao.getPaymentsForCustomer(1L)
        assertEquals(1, paymentsAfterDelete.size)
        assertEquals(1001L, paymentsAfterDelete[0].id)

        val reloadedJob = jobDao.getJobById(10L)!!
        assertEquals(20000.0, reloadedJob.pendingAmount, 0.01)
        assertEquals(5000.0, reloadedJob.amountReceived, 0.01)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 15, 16, 17: OFFLINE-FIRST, MULTI-DEVICE SYNC & NO RESURRECTION
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testOfflineSyncQueueAndNoResurrection() = runBlocking {
        val paymentDao = database.paymentDao()

        // 1. Create offline payment with isSynced = false
        val offlinePayment = PaymentEntity(
            id = 701L,
            workspaceId = workspaceA,
            jobEntryId = 10L,
            customerId = 5L,
            customerName = "Murugan",
            amount = 3500.0,
            isSynced = false,
            collectedByUid = "partner_uid",
            collectedByName = "Partner",
            collectedAt = 1000L
        )
        paymentDao.insertPayment(offlinePayment)

        val unsyncedBefore = paymentDao.getUnsyncedPaymentsForWorkspace(workspaceA)
        assertEquals(1, unsyncedBefore.size)
        assertEquals(701L, unsyncedBefore[0].id)
        assertFalse(unsyncedBefore[0].isSynced)

        // 2. Simulate online sync: mark isSynced = true
        paymentDao.markPaymentsSynced(listOf(offlinePayment.id))
        val unsyncedAfter = paymentDao.getUnsyncedPaymentsForWorkspace(workspaceA)
        assertEquals(0, unsyncedAfter.size)

        // 3. Simulate deletion after sync (tombstoned/deleted locally)
        paymentDao.deletePayment(offlinePayment.id)
        val paymentAfterDelete = paymentDao.getPaymentById(offlinePayment.id)
        assertNull("Payment must be removed", paymentAfterDelete)

        // 4. Repeated query confirms no resurrecting zombie record
        val allPayments = paymentDao.getPaymentsForWorkspace(workspaceA).first()
        assertFalse("Deleted payment must not return", allPayments.any { it.id == 701L })
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 20: SYNC IDENTITY INVARIANTS IN MAP SERIALIZATION
    // ─────────────────────────────────────────────────────────────────────────
    @Test
    fun testSyncIdentityInvariants() {
        val payment = PaymentEntity(
            id = 888L,
            workspaceId = workspaceA,
            jobEntryId = 44L,
            customerId = 22L,
            customerName = "Ganesh",
            amount = 4500.0,
            collectedByUid = "uid_collector_9",
            collectedByName = "Collector",
            collectedByRole = "Partner",
            collectedAt = 5000L
        )

        val map = payment.toFirestoreMap()

        // All identity invariants must be preserved
        assertEquals(888L, map["id"])
        assertEquals(workspaceA, map["workspaceId"])
        assertEquals(44L, map["jobEntryId"])
        assertEquals(22L, map["customerId"])
        assertEquals(4500.0, (map["amount"] as Number).toDouble(), 0.01)
        assertEquals("uid_collector_9", map["collectedByUid"])
        assertEquals("Collector", map["collectedByName"])
        assertEquals("Partner", map["collectedByRole"])
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SECTION 21: MULTI-USER / OFFLINE-SYNC MATRIX SUITE (CASES A, B, C, D)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Simulated Independent Client Store representing Phone A (Owner) or Phone B (Partner)
     * with isolated in-memory Room database and pending sync queue.
     */
    private class SimulatedDeviceStore(
        val actorUid: String,
        val actorRole: String,
        val context: Context
    ) {
        val database: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        var isOnline: Boolean = true

        fun close() {
            database.close()
        }
    }

    /**
     * Simulated Central Cloud Backend (e.g. Firebase Firestore Emulator abstraction)
     */
    private class SimulatedCloudBackend {
        val jobs = mutableMapOf<Long, JobEntryEntity>()
        val expenses = mutableMapOf<Long, ExpenseEntity>()
        val payments = mutableMapOf<Long, PaymentEntity>()
        val withdrawals = mutableMapOf<Long, WithdrawalEntity>()

        fun publishFromDevice(device: SimulatedDeviceStore, workspaceId: String) = runBlocking {
            if (!device.isOnline) return@runBlocking
            
            // Push unsynced jobs
            val unsyncedJobs = device.database.jobEntryDao().getUnsyncedJobsForWorkspace(workspaceId)
            unsyncedJobs.forEach { job ->
                jobs[job.id] = job.copy(isSynced = true)
                device.database.jobEntryDao().insertJob(job.copy(isSynced = true))
            }

            // Push unsynced expenses
            val unsyncedExpenses = device.database.expenseDao().getUnsyncedExpensesForWorkspace(workspaceId)
            unsyncedExpenses.forEach { exp ->
                expenses[exp.id] = exp.copy(isSynced = true)
                device.database.expenseDao().insertExpense(exp.copy(isSynced = true))
            }

            // Push unsynced payments
            val unsyncedPayments = device.database.paymentDao().getUnsyncedPaymentsForWorkspace(workspaceId)
            unsyncedPayments.forEach { pay ->
                payments[pay.id] = pay.copy(isSynced = true)
                device.database.paymentDao().markPaymentsSynced(listOf(pay.id))
            }

            // Push unsynced withdrawals
            val unsyncedWithdrawals = device.database.withdrawalDao().getUnsyncedWithdrawalsForWorkspace(workspaceId)
            unsyncedWithdrawals.forEach { wth ->
                withdrawals[wth.id] = wth.copy(isSynced = true)
                device.database.withdrawalDao().insertWithdrawal(wth.copy(isSynced = true))
            }
        }

        fun pullToDevice(device: SimulatedDeviceStore, workspaceId: String) = runBlocking {
            if (!device.isOnline) return@runBlocking

            jobs.values.filter { it.workspaceId == workspaceId }.forEach {
                device.database.jobEntryDao().insertJob(it)
            }
            expenses.values.filter { it.workspaceId == workspaceId }.forEach {
                device.database.expenseDao().insertExpense(it)
            }
            payments.values.filter { it.workspaceId == workspaceId }.forEach {
                device.database.paymentDao().insertPayment(it)
            }
            withdrawals.values.filter { it.workspaceId == workspaceId }.forEach {
                device.database.withdrawalDao().insertWithdrawal(it)
            }
        }

        fun syncDevice(device: SimulatedDeviceStore, workspaceId: String) {
            publishFromDevice(device, workspaceId)
            pullToDevice(device, workspaceId)
        }
    }

    @Test
    fun testCaseA_OwnerOnline_PartnerOnline_RealRecordCreationAndConvergence() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ownerPhone = SimulatedDeviceStore("owner_uid", RoleUtils.ROLE_OWNER, context)
        val partnerPhone = SimulatedDeviceStore("partner_uid", RoleUtils.ROLE_PARTNER, context)
        val cloud = SimulatedCloudBackend()

        ownerPhone.isOnline = true
        partnerPhone.isOnline = true

        // Owner creates Job O1 (₹14,000) and Investment OI1 (₹14,000)
        val jobO1 = createJob(id = 1001L, wsId = workspaceA, customerName = "Owner Customer", total = 14000.0, pending = 14000.0).copy(
            createdByUid = "owner_uid", createdByRole = RoleUtils.ROLE_OWNER, isSynced = false
        )
        val expOI1 = ExpenseEntity(
            id = 2001L, workspaceId = workspaceA, expenseType = "Investment", amount = 14000.0,
            tractorLabel = "T1", addedByPartner = "Owner", paidBy = "I Paid", paidByUid = "owner_uid", createdByUid = "owner_uid", isSynced = false
        )
        ownerPhone.database.jobEntryDao().insertJob(jobO1)
        ownerPhone.database.expenseDao().insertExpense(expOI1)

        // Partner creates Job P1 (₹4,000), Investment PI1 (₹4,000), and Payment PP1 (₹2,000)
        val jobP1 = createJob(id = 1002L, wsId = workspaceA, customerName = "Partner Customer", total = 4000.0, pending = 2000.0, received = 2000.0).copy(
            createdByUid = "partner_uid", createdByRole = RoleUtils.ROLE_PARTNER, isSynced = false
        )
        val expPI1 = ExpenseEntity(
            id = 2002L, workspaceId = workspaceA, expenseType = "Investment", amount = 4000.0,
            tractorLabel = "T1", addedByPartner = "Partner", paidBy = "I Paid", paidByUid = "partner_uid", createdByUid = "partner_uid", isSynced = false
        )
        val payPP1 = PaymentEntity(
            id = 3001L, workspaceId = workspaceA, jobEntryId = 1002L, customerId = 88L, customerName = "Partner Customer", amount = 2000.0,
            collectedByUid = "partner_uid", collectedByName = "Partner", isSynced = false
        )
        partnerPhone.database.jobEntryDao().insertJob(jobP1)
        partnerPhone.database.expenseDao().insertExpense(expPI1)
        partnerPhone.database.paymentDao().insertPayment(payPP1)

        // Both sync online
        cloud.syncDevice(ownerPhone, workspaceA)
        cloud.syncDevice(partnerPhone, workspaceA)
        // Repeat sync to ensure full 2-way propagation
        cloud.syncDevice(ownerPhone, workspaceA)

        // Convergence verification
        val ownerJobs = ownerPhone.database.jobEntryDao().getJobsForWorkspace(workspaceA).first()
        val partnerJobs = partnerPhone.database.jobEntryDao().getJobsForWorkspace(workspaceA).first()
        assertEquals(2, ownerJobs.size)
        assertEquals(2, partnerJobs.size)

        val ownerExpenses = ownerPhone.database.expenseDao().getExpensesForWorkspace(workspaceA).first()
        val partnerExpenses = partnerPhone.database.expenseDao().getExpensesForWorkspace(workspaceA).first()
        assertEquals(2, ownerExpenses.size)
        assertEquals(2, partnerExpenses.size)

        // Investment attribution on both devices
        val members = listOf(
            WorkspaceMember(uid = "owner_uid", role = "owner", displayName = "Owner"),
            WorkspaceMember(uid = "partner_uid", role = "partner", displayName = "Partner")
        )
        val ownerStatuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = emptyList(), expenses = ownerExpenses, withdrawals = emptyList(), partners = emptyList(), workspaceMembers = members, businessName = "Farm"
        )
        val partnerStatuses = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = emptyList(), expenses = partnerExpenses, withdrawals = emptyList(), partners = emptyList(), workspaceMembers = members, businessName = "Farm"
        )

        assertEquals(14000.0, ownerStatuses.find { it.partnerUid == "owner_uid" }!!.totalInvestment, 0.01)
        assertEquals(4000.0, ownerStatuses.find { it.partnerUid == "partner_uid" }!!.totalInvestment, 0.01)
        assertEquals(14000.0, partnerStatuses.find { it.partnerUid == "owner_uid" }!!.totalInvestment, 0.01)
        assertEquals(4000.0, partnerStatuses.find { it.partnerUid == "partner_uid" }!!.totalInvestment, 0.01)

        ownerPhone.close()
        partnerPhone.close()
    }

    @Test
    fun testCaseB_OwnerOnline_PartnerOffline_LocalPendingQueueAndPostReconnectSync() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ownerPhone = SimulatedDeviceStore("owner_uid", RoleUtils.ROLE_OWNER, context)
        val partnerPhone = SimulatedDeviceStore("partner_uid", RoleUtils.ROLE_PARTNER, context)
        val cloud = SimulatedCloudBackend()

        ownerPhone.isOnline = true
        partnerPhone.isOnline = false // Partner is offline

        // Partner creates Payment while OFFLINE
        val offlinePay = PaymentEntity(
            id = 5001L, workspaceId = workspaceA, jobEntryId = 99L, customerId = 12L, customerName = "Offline Customer", amount = 6500.0,
            collectedByUid = "partner_uid", collectedByName = "Partner", isSynced = false
        )
        partnerPhone.database.paymentDao().insertPayment(offlinePay)

        // Verify Partner local Room has record marked unsynced
        val partnerUnsyncedBefore = partnerPhone.database.paymentDao().getUnsyncedPaymentsForWorkspace(workspaceA)
        assertEquals(1, partnerUnsyncedBefore.size)
        assertEquals(5001L, partnerUnsyncedBefore[0].id)
        assertFalse(partnerUnsyncedBefore[0].isSynced)

        // Attempt sync while offline -> nothing reaches cloud or owner
        cloud.syncDevice(partnerPhone, workspaceA)
        cloud.syncDevice(ownerPhone, workspaceA)

        val ownerPaymentsBefore = ownerPhone.database.paymentDao().getPaymentsForWorkspace(workspaceA).first()
        assertEquals(0, ownerPaymentsBefore.size)

        // Partner comes ONLINE and syncs
        partnerPhone.isOnline = true
        cloud.syncDevice(partnerPhone, workspaceA)
        cloud.syncDevice(ownerPhone, workspaceA)

        // Verify Owner receives record exactly once and Partner isSynced = true
        val ownerPaymentsAfter = ownerPhone.database.paymentDao().getPaymentsForWorkspace(workspaceA).first()
        assertEquals(1, ownerPaymentsAfter.size)
        assertEquals(5001L, ownerPaymentsAfter[0].id)
        assertEquals(6500.0, ownerPaymentsAfter[0].amount, 0.01)
        assertEquals("partner_uid", ownerPaymentsAfter[0].collectedByUid)

        val partnerUnsyncedAfter = partnerPhone.database.paymentDao().getUnsyncedPaymentsForWorkspace(workspaceA)
        assertEquals(0, partnerUnsyncedAfter.size)

        ownerPhone.close()
        partnerPhone.close()
    }

    @Test
    fun testCaseC_OwnerOffline_PartnerOnline_OwnerPendingQueueAndPostReconnectSync() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ownerPhone = SimulatedDeviceStore("owner_uid", RoleUtils.ROLE_OWNER, context)
        val partnerPhone = SimulatedDeviceStore("partner_uid", RoleUtils.ROLE_PARTNER, context)
        val cloud = SimulatedCloudBackend()

        ownerPhone.isOnline = false // Owner offline
        partnerPhone.isOnline = true

        // Owner creates Job while OFFLINE
        val ownerJob = createJob(id = 7001L, wsId = workspaceA, customerName = "Owner Offline Cust", total = 12000.0, pending = 12000.0).copy(
            createdByUid = "owner_uid", createdByRole = RoleUtils.ROLE_OWNER, isSynced = false
        )
        ownerPhone.database.jobEntryDao().insertJob(ownerJob)

        val ownerUnsyncedBefore = ownerPhone.database.jobEntryDao().getUnsyncedJobsForWorkspace(workspaceA)
        assertEquals(1, ownerUnsyncedBefore.size)

        // Partner syncs while owner offline -> partner gets 0 owner jobs
        cloud.syncDevice(partnerPhone, workspaceA)
        val partnerJobsBefore = partnerPhone.database.jobEntryDao().getJobsForWorkspace(workspaceA).first()
        assertEquals(0, partnerJobsBefore.size)

        // Owner comes ONLINE and syncs
        ownerPhone.isOnline = true
        cloud.syncDevice(ownerPhone, workspaceA)
        cloud.syncDevice(partnerPhone, workspaceA)

        // Partner now receives Owner's offline job
        val partnerJobsAfter = partnerPhone.database.jobEntryDao().getJobsForWorkspace(workspaceA).first()
        assertEquals(1, partnerJobsAfter.size)
        assertEquals(7001L, partnerJobsAfter[0].id)
        assertEquals("owner_uid", partnerJobsAfter[0].createdByUid)

        ownerPhone.close()
        partnerPhone.close()
    }

    @Test
    fun testCaseD_BothOffline_IndependentQueues_And_TwoWayConvergence() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val ownerPhone = SimulatedDeviceStore("owner_uid", RoleUtils.ROLE_OWNER, context)
        val partnerPhone = SimulatedDeviceStore("partner_uid", RoleUtils.ROLE_PARTNER, context)
        val cloud = SimulatedCloudBackend()

        ownerPhone.isOnline = false
        partnerPhone.isOnline = false

        // Owner creates Job O_OFF (₹14,000) and Investment OI_OFF (₹14,000)
        val jobO = createJob(id = 8001L, wsId = workspaceA, customerName = "Cust O", total = 14000.0, pending = 14000.0).copy(
            createdByUid = "owner_uid", isSynced = false
        )
        val expO = ExpenseEntity(
            id = 8002L, workspaceId = workspaceA, expenseType = "Investment", amount = 14000.0,
            tractorLabel = "T1", addedByPartner = "Owner", paidBy = "I Paid", paidByUid = "owner_uid", createdByUid = "owner_uid", isSynced = false
        )
        ownerPhone.database.jobEntryDao().insertJob(jobO)
        ownerPhone.database.expenseDao().insertExpense(expO)

        // Partner creates Job P_OFF (₹4,000) and Investment PI_OFF (₹4,000)
        val jobP = createJob(id = 9001L, wsId = workspaceA, customerName = "Cust P", total = 4000.0, pending = 4000.0).copy(
            createdByUid = "partner_uid", isSynced = false
        )
        val expP = ExpenseEntity(
            id = 9002L, workspaceId = workspaceA, expenseType = "Investment", amount = 4000.0,
            tractorLabel = "T1", addedByPartner = "Partner", paidBy = "I Paid", paidByUid = "partner_uid", createdByUid = "partner_uid", isSynced = false
        )
        partnerPhone.database.jobEntryDao().insertJob(jobP)
        partnerPhone.database.expenseDao().insertExpense(expP)

        // Step 1: Owner comes online first and syncs
        ownerPhone.isOnline = true
        cloud.syncDevice(ownerPhone, workspaceA)

        // Step 2: Partner comes online later and syncs
        partnerPhone.isOnline = true
        cloud.syncDevice(partnerPhone, workspaceA)

        // Step 3: Owner syncs once more to receive Partner's records
        cloud.syncDevice(ownerPhone, workspaceA)

        // Final convergence check: both devices have 2 jobs and 2 expenses
        val finalOwnerJobs = ownerPhone.database.jobEntryDao().getJobsForWorkspace(workspaceA).first()
        val finalPartnerJobs = partnerPhone.database.jobEntryDao().getJobsForWorkspace(workspaceA).first()
        assertEquals(2, finalOwnerJobs.size)
        assertEquals(2, finalPartnerJobs.size)

        val finalOwnerExp = ownerPhone.database.expenseDao().getExpensesForWorkspace(workspaceA).first()
        val finalPartnerExp = partnerPhone.database.expenseDao().getExpensesForWorkspace(workspaceA).first()
        assertEquals(2, finalOwnerExp.size)
        assertEquals(2, finalPartnerExp.size)

        ownerPhone.close()
        partnerPhone.close()
    }
}
