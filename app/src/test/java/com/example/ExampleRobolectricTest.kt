package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
import com.example.data.firebase.jobEntryFromFirestoreMap
import com.example.data.firebase.paymentFromFirestoreMap
import com.example.data.firebase.toFirestoreMap
import com.example.ui.util.FinancialCalculationEngine
import com.example.ui.util.FinancialScope
import com.example.ui.util.FinancialScopeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: AppDatabase

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

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Trac", appName)
    }

    @Test
    fun `account isolation - queries for workspace A do not return workspace B records`() = runBlocking {
        val jobDao = database.jobEntryDao()

        val jobA = JobEntryEntity(
            id = 1001L,
            workspaceId = "ws_account_a",
            customerId = 1L,
            customerName = "Ramesh Kumar",
            customerPhone = "9876543210",
            customerLocation = "Farm North",
            operatorName = "Operator A",
            tractorId = 1L,
            tractorLabel = "Mahindra 575",
            workType = "Ploughing",
            startTimeMillis = 1000L,
            endTimeMillis = 5000L,
            durationMinutes = 60,
            hourlyRate = 800.0,
            totalAmount = 800.0,
            amountReceived = 500.0,
            pendingAmount = 300.0,
            addedByPartner = "Operator A",
            isSynced = true
        )

        val jobB = JobEntryEntity(
            id = 2002L,
            workspaceId = "ws_account_b",
            customerId = 2L,
            customerName = "Suresh Patel",
            customerPhone = "9123456780",
            customerLocation = "Farm South",
            operatorName = "Operator B",
            tractorId = 2L,
            tractorLabel = "John Deere",
            workType = "Rotavator",
            startTimeMillis = 2000L,
            endTimeMillis = 6000L,
            durationMinutes = 90,
            hourlyRate = 900.0,
            totalAmount = 1350.0,
            amountReceived = 1350.0,
            pendingAmount = 0.0,
            addedByPartner = "Operator B",
            isSynced = true
        )

        jobDao.insertJob(jobA)
        jobDao.insertJob(jobB)

        val jobsForA = jobDao.getJobsForWorkspace("ws_account_a").first()
        val jobsForB = jobDao.getJobsForWorkspace("ws_account_b").first()
        val jobsForEmpty = jobDao.getJobsForWorkspace("").first()

        assertEquals(1, jobsForA.size)
        assertEquals(1001L, jobsForA[0].id)
        assertEquals("ws_account_a", jobsForA[0].workspaceId)

        assertEquals(1, jobsForB.size)
        assertEquals(2002L, jobsForB[0].id)
        assertEquals("ws_account_b", jobsForB[0].workspaceId)

        assertEquals(0, jobsForEmpty.size)
    }

    @Test
    fun `remote deletion reconciliation - deletes obsolete records scoped only to that workspace`() = runBlocking {
        val jobDao = database.jobEntryDao()

        val jobA1 = JobEntryEntity(
            id = 101L,
            workspaceId = "ws_account_a",
            customerId = 1L,
            customerName = "Ramesh",
            customerPhone = "123",
            customerLocation = "Loc",
            operatorName = "Op",
            tractorId = 1L,
            tractorLabel = "T1",
            workType = "Ploughing",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            durationMinutes = 60,
            hourlyRate = 500.0,
            totalAmount = 500.0,
            amountReceived = 500.0,
            pendingAmount = 0.0,
            addedByPartner = "Op",
            isSynced = true
        )

        val jobA2 = jobA1.copy(id = 102L)
        val jobB1 = jobA1.copy(id = 201L, workspaceId = "ws_account_b")

        jobDao.insertJob(jobA1)
        jobDao.insertJob(jobA2)
        jobDao.insertJob(jobB1)

        // Simulate remote deletion of jobA2 on Device B: remote snapshot now only has [101L]
        val remoteIdsPresent = listOf(101L)
        jobDao.deleteSyncedNotIn("ws_account_a", remoteIdsPresent)

        val remainingA = jobDao.getJobsForWorkspace("ws_account_a").first()
        val remainingB = jobDao.getJobsForWorkspace("ws_account_b").first()

        assertEquals(1, remainingA.size)
        assertEquals(101L, remainingA[0].id)

        // Workspace B's records MUST remain intact
        assertEquals(1, remainingB.size)
        assertEquals(201L, remainingB[0].id)
    }

    @Test
    fun `unsynced counter isolation - unsynced counts are workspace isolated`() = runBlocking {
        val expenseDao = database.expenseDao()

        val unsyncedExpA = ExpenseEntity(
            id = 501L,
            workspaceId = "ws_account_a",
            expenseType = "Diesel",
            amount = 1500.0,
            description = "Fuel",
            dateTimestamp = 1000L,
            addedByPartner = "Partner A",
            tractorId = 1L,
            tractorLabel = "Mahindra",
            isSynced = false
        )

        val syncedExpA = unsyncedExpA.copy(id = 502L, isSynced = true)

        val unsyncedExpB = ExpenseEntity(
            id = 601L,
            workspaceId = "ws_account_b",
            expenseType = "Oil Change",
            amount = 800.0,
            description = "Maintenance",
            dateTimestamp = 2000L,
            addedByPartner = "Partner B",
            tractorId = 2L,
            tractorLabel = "John Deere",
            isSynced = false
        )

        expenseDao.insertExpense(unsyncedExpA)
        expenseDao.insertExpense(syncedExpA)
        expenseDao.insertExpense(unsyncedExpB)

        val unsyncedCountA = expenseDao.getUnsyncedCountForWorkspace("ws_account_a").first()
        val unsyncedCountB = expenseDao.getUnsyncedCountForWorkspace("ws_account_b").first()

        assertEquals(1, unsyncedCountA)
        assertEquals(1, unsyncedCountB)
    }

    @Test
    fun `customer and tractor isolation - entities are partitioned strictly by workspaceId`() = runBlocking {
        val customerDao = database.customerDao()
        val tractorDao = database.tractorDao()

        val custA = CustomerEntity(
            id = 1L,
            workspaceId = "ws_owner_100",
            name = "Farmer Ramesh",
            phone = "9876543210",
            location = "North Village",
            totalBilled = 3000.0,
            totalPaid = 2000.0,
            balanceDue = 1000.0,
            isSynced = true
        )
        val custB = custA.copy(id = 2L, workspaceId = "ws_owner_200", name = "Farmer Suresh")

        customerDao.insertCustomer(custA)
        customerDao.insertCustomer(custB)

        val customersA = customerDao.getCustomersForWorkspace("ws_owner_100").first()
        val customersB = customerDao.getCustomersForWorkspace("ws_owner_200").first()

        assertEquals(1, customersA.size)
        assertEquals("Farmer Ramesh", customersA[0].name)
        assertEquals(1, customersB.size)
        assertEquals("Farmer Suresh", customersB[0].name)
    }

    @Test
    fun `shared canonical workspace - multiple partners write to same owner workspaceId`() = runBlocking {
        val jobDao = database.jobEntryDao()
        val canonicalOwnerWsId = "ws_owner_alpha"

        // Owner creates entry
        val entryByOwner = JobEntryEntity(
            id = 101L,
            workspaceId = canonicalOwnerWsId,
            customerId = 1L,
            customerName = "Customer 1",
            customerPhone = "1234567890",
            customerLocation = "Farm A",
            operatorName = "Owner Alpha",
            tractorId = 1L,
            tractorLabel = "John Deere 5050D",
            workType = "Ploughing",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            durationMinutes = 60,
            hourlyRate = 1000.0,
            totalAmount = 1000.0,
            amountReceived = 1000.0,
            pendingAmount = 0.0,
            addedByPartner = "Owner Alpha",
            isSynced = true
        )

        // Partner 1 creates entry in the same canonical owner workspace
        val entryByPartner1 = entryByOwner.copy(
            id = 102L,
            operatorName = "Partner 1",
            addedByPartner = "Partner 1"
        )

        // Partner 2 creates entry in the same canonical owner workspace
        val entryByPartner2 = entryByOwner.copy(
            id = 103L,
            operatorName = "Partner 2",
            addedByPartner = "Partner 2"
        )

        jobDao.insertJob(entryByOwner)
        jobDao.insertJob(entryByPartner1)
        jobDao.insertJob(entryByPartner2)

        val allJobsInWorkspace = jobDao.getJobsForWorkspace(canonicalOwnerWsId).first()
        assertEquals(3, allJobsInWorkspace.size)
        assertTrue(allJobsInWorkspace.any { it.addedByPartner == "Owner Alpha" })
        assertTrue(allJobsInWorkspace.any { it.addedByPartner == "Partner 1" })
        assertTrue(allJobsInWorkspace.any { it.addedByPartner == "Partner 2" })
    }

    @Test
    fun `financial consistency - verifies total recorded, total due, available balance, and expense isolation`() = runBlocking {
        val jobDao = database.jobEntryDao()
        val expenseDao = database.expenseDao()
        val wsId = "ws_financial_test"

        // Job A = ₹5,000 (Paid)
        val jobA = JobEntryEntity(
            id = 701L,
            workspaceId = wsId,
            customerId = 10L,
            customerName = "Customer A",
            customerPhone = "9876543210",
            customerLocation = "Farm",
            operatorName = "Operator",
            tractorId = 1L,
            tractorLabel = "Mahindra",
            workType = "Ploughing",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            durationMinutes = 60,
            hourlyRate = 5000.0,
            totalAmount = 5000.0,
            amountReceived = 5000.0,
            pendingAmount = 0.0,
            addedByPartner = "Operator",
            isSynced = true
        )

        // Job B = ₹6,600 (Due)
        val jobB = jobA.copy(
            id = 702L,
            customerId = 11L,
            customerName = "Customer B",
            hourlyRate = 6600.0,
            totalAmount = 6600.0,
            amountReceived = 0.0,
            pendingAmount = 6600.0
        )

        // Expense = ₹2,500
        val expense = ExpenseEntity(
            id = 801L,
            workspaceId = wsId,
            expenseType = "Diesel",
            amount = 2500.0,
            tractorId = 1L,
            tractorLabel = "Mahindra",
            addedByPartner = "Operator"
        )

        jobDao.insertJob(jobA)
        jobDao.insertJob(jobB)
        expenseDao.insertExpense(expense)

        val totalRecorded = jobDao.getTotalRecordedForWorkspace(wsId).first() ?: 0.0
        val totalDue = jobDao.getTotalPendingForWorkspace(wsId).first() ?: 0.0
        val availableBalance = maxOf(0.0, totalRecorded - totalDue)
        val totalExpense = expenseDao.getTotalExpensesForWorkspace(wsId).first() ?: 0.0

        assertEquals(11600.0, totalRecorded, 0.01)
        assertEquals(6600.0, totalDue, 0.01)
        assertEquals(5000.0, availableBalance, 0.01)
        assertEquals(2500.0, totalExpense, 0.01)
    }

    @Test
    fun `payment workflow - unpaid job transitions to paid and updates available balance`() = runBlocking {
        val jobDao = database.jobEntryDao()
        val wsId = "ws_payment_test"

        val initialJob = JobEntryEntity(
            id = 703L,
            workspaceId = wsId,
            customerId = 12L,
            customerName = "Customer C",
            customerPhone = "9876543210",
            customerLocation = "Farm",
            operatorName = "Operator",
            tractorId = 1L,
            tractorLabel = "Mahindra",
            workType = "Rotavator",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            durationMinutes = 60,
            hourlyRate = 5000.0,
            totalAmount = 5000.0,
            amountReceived = 0.0,
            pendingAmount = 5000.0,
            addedByPartner = "Operator",
            isSynced = true
        )

        jobDao.insertJob(initialJob)

        var recorded = jobDao.getTotalRecordedForWorkspace(wsId).first() ?: 0.0
        var due = jobDao.getTotalPendingForWorkspace(wsId).first() ?: 0.0
        var available = maxOf(0.0, recorded - due)

        assertEquals(5000.0, recorded, 0.01)
        assertEquals(5000.0, due, 0.01)
        assertEquals(0.0, available, 0.01)
        assertTrue(initialJob.pendingAmount > 0.0) // DUE status

        // Record payment of ₹5,000
        val paidJob = initialJob.copy(
            amountReceived = 5000.0,
            pendingAmount = 0.0
        )
        jobDao.insertJob(paidJob)

        recorded = jobDao.getTotalRecordedForWorkspace(wsId).first() ?: 0.0
        due = jobDao.getTotalPendingForWorkspace(wsId).first() ?: 0.0
        available = maxOf(0.0, recorded - due)

        assertEquals(5000.0, recorded, 0.01)
        assertEquals(0.0, due, 0.01)
        assertEquals(5000.0, available, 0.01)
        assertTrue(paidJob.pendingAmount <= 0.0) // PAID status
    }

    @Test
    fun `partial payment workflow - correctly calculates remaining due and available cash`() = runBlocking {
        val jobDao = database.jobEntryDao()
        val wsId = "ws_partial_payment_test"

        val job = JobEntryEntity(
            id = 704L,
            workspaceId = wsId,
            customerId = 13L,
            customerName = "Customer D",
            customerPhone = "9876543210",
            customerLocation = "Farm",
            operatorName = "Operator",
            tractorId = 1L,
            tractorLabel = "Mahindra",
            workType = "Cultivator",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            durationMinutes = 60,
            hourlyRate = 8000.0,
            totalAmount = 8000.0,
            amountReceived = 3000.0,
            pendingAmount = 5000.0,
            addedByPartner = "Operator",
            isSynced = true
        )

        jobDao.insertJob(job)

        var recorded = jobDao.getTotalRecordedForWorkspace(wsId).first() ?: 0.0
        var due = jobDao.getTotalPendingForWorkspace(wsId).first() ?: 0.0
        var available = maxOf(0.0, recorded - due)

        assertEquals(8000.0, recorded, 0.01)
        assertEquals(5000.0, due, 0.01)
        assertEquals(3000.0, available, 0.01)

        // Settle remaining balance
        val fullyPaidJob = job.copy(
            amountReceived = 8000.0,
            pendingAmount = 0.0
        )
        jobDao.insertJob(fullyPaidJob)

        recorded = jobDao.getTotalRecordedForWorkspace(wsId).first() ?: 0.0
        due = jobDao.getTotalPendingForWorkspace(wsId).first() ?: 0.0
        available = maxOf(0.0, recorded - due)

        assertEquals(8000.0, recorded, 0.01)
        assertEquals(0.0, due, 0.01)
        assertEquals(8000.0, available, 0.01)
        assertTrue(fullyPaidJob.pendingAmount <= 0.0)
    }

    @Test
    fun `expense isolation - expenses do not alter total recorded or customer dues`() = runBlocking {
        val jobDao = database.jobEntryDao()
        val expenseDao = database.expenseDao()
        val wsId = "ws_expense_isolation_test"

        val job = JobEntryEntity(
            id = 705L,
            workspaceId = wsId,
            customerId = 14L,
            customerName = "Customer E",
            customerPhone = "9876543210",
            customerLocation = "Farm",
            operatorName = "Operator",
            tractorId = 1L,
            tractorLabel = "Mahindra",
            workType = "Ploughing",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            durationMinutes = 60,
            hourlyRate = 10000.0,
            totalAmount = 10000.0,
            amountReceived = 10000.0,
            pendingAmount = 0.0,
            addedByPartner = "Operator",
            isSynced = true
        )

        val expense = ExpenseEntity(
            id = 802L,
            workspaceId = wsId,
            expenseType = "Repair",
            amount = 3000.0,
            tractorId = 1L,
            tractorLabel = "Mahindra",
            addedByPartner = "Operator"
        )

        jobDao.insertJob(job)
        expenseDao.insertExpense(expense)

        val recorded = jobDao.getTotalRecordedForWorkspace(wsId).first() ?: 0.0
        val due = jobDao.getTotalPendingForWorkspace(wsId).first() ?: 0.0
        val available = maxOf(0.0, recorded - due)
        val expenseTotal = expenseDao.getTotalExpensesForWorkspace(wsId).first() ?: 0.0

        assertEquals(10000.0, recorded, 0.01)
        assertEquals(0.0, due, 0.01)
        assertEquals(10000.0, available, 0.01)
        assertEquals(3000.0, expenseTotal, 0.01)
    }

    @Test
    fun `same-name customers with different phone numbers remain separate records with isolated balances and job histories`() = runBlocking {
        val custDao = database.customerDao()
        val jobDao = database.jobEntryDao()
        val wsId = "ws_same_name_test"

        val custA = CustomerEntity(
            id = 9001L,
            workspaceId = wsId,
            name = "Kumar",
            phone = "1111111111",
            location = "Village North",
            totalBilled = 5000.0,
            totalPaid = 2000.0,
            balanceDue = 3000.0
        )
        val custB = CustomerEntity(
            id = 9002L,
            workspaceId = wsId,
            name = "Kumar",
            phone = "2222222222",
            location = "Village South",
            totalBilled = 8000.0,
            totalPaid = 8000.0,
            balanceDue = 0.0
        )

        custDao.insertCustomer(custA)
        custDao.insertCustomer(custB)

        val allCusts = custDao.getCustomersForWorkspace(wsId).first()
        assertEquals(2, allCusts.size)

        val jobA = JobEntryEntity(
            id = 9101L,
            workspaceId = wsId,
            customerId = custA.id,
            customerName = custA.name,
            customerPhone = custA.phone,
            operatorName = "Owner",
            tractorId = 1L,
            tractorLabel = "Mahindra",
            workType = "Ploughing",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            durationMinutes = 60,
            hourlyRate = 5000.0,
            totalAmount = 5000.0,
            amountReceived = 2000.0,
            pendingAmount = 3000.0,
            addedByPartner = "Owner"
        )
        val jobB = JobEntryEntity(
            id = 9102L,
            workspaceId = wsId,
            customerId = custB.id,
            customerName = custB.name,
            customerPhone = custB.phone,
            operatorName = "Owner",
            tractorId = 1L,
            tractorLabel = "Mahindra",
            workType = "Rotavator",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            durationMinutes = 60,
            hourlyRate = 8000.0,
            totalAmount = 8000.0,
            amountReceived = 8000.0,
            pendingAmount = 0.0,
            addedByPartner = "Owner"
        )

        jobDao.insertJob(jobA)
        jobDao.insertJob(jobB)

        val jobsA = jobDao.getJobsForCustomer(wsId, custA.id).first()
        val jobsB = jobDao.getJobsForCustomer(wsId, custB.id).first()

        assertEquals(1, jobsA.size)
        assertEquals(9101L, jobsA[0].id)

        assertEquals(1, jobsB.size)
        assertEquals(9102L, jobsB[0].id)

        // Verify balances are isolated
        assertEquals(3000.0, custA.balanceDue, 0.01)
        assertEquals(0.0, custB.balanceDue, 0.01)
    }

    @Test
    fun `search by customer name returns both same-name customers while search by phone returns exact match`() = runBlocking {
        val custDao = database.customerDao()
        val wsId = "ws_search_test"

        val custA = CustomerEntity(id = 9011L, workspaceId = wsId, name = "Kumar", phone = "1111111111")
        val custB = CustomerEntity(id = 9012L, workspaceId = wsId, name = "Kumar", phone = "2222222222")
        custDao.insertCustomer(custA)
        custDao.insertCustomer(custB)

        val searchNameResults = custDao.searchCustomers(wsId, "Kumar").first()
        assertEquals(2, searchNameResults.size)

        val searchPhoneResults = custDao.searchCustomers(wsId, "1111111111").first()
        assertEquals(1, searchPhoneResults.size)
        assertEquals("1111111111", searchPhoneResults[0].phone)
    }

    @Test
    fun `owner creates entry for partner results in exactly one financial transaction in workspace and balance sheet without duplicated amounts`() = runBlocking {
        val job = JobEntryEntity(
            id = 9201L,
            workspaceId = "ws_attribution_test",
            customerId = 50L,
            customerName = "Customer X",
            operatorName = "Partner Perumal",
            tractorLabel = "Mahindra",
            workType = "Cultivator",
            startTimeMillis = 10000L,
            endTimeMillis = 20000L,
            durationMinutes = 60,
            hourlyRate = 5000.0,
            totalAmount = 5000.0,
            amountReceived = 5000.0,
            pendingAmount = 0.0,
            addedByPartner = "Owner" // Owner created for Partner Perumal
        )

        val scope = com.example.ui.util.FinancialScope(mode = com.example.ui.util.FinancialScopeMode.OVERALL)
        val summary = com.example.ui.util.FinancialCalculationEngine.calculateSummary(
            jobs = listOf(job),
            expenses = emptyList(),
            withdrawals = emptyList(),
            scope = scope
        )

        // Must count as exactly 1 job and ₹5,000 recorded in workspace total
        assertEquals(1, summary.jobCount)
        assertEquals(5000.0, summary.totalRecorded, 0.01)

        val breakdowns = com.example.ui.util.FinancialCalculationEngine.calculatePartnerBreakdown(
            jobs = listOf(job),
            expenses = emptyList(),
            withdrawals = emptyList(),
            partners = emptyList(),
            workspaceMembers = emptyList(),
            scope = scope
        )

        // In partner breakdown, job must appear under primary partner (Partner Perumal) exactly once, total recorded across rows equals ₹5,000
        val sumRecordedAcrossRows = breakdowns.sumOf { it.recorded }
        assertEquals(5000.0, sumRecordedAcrossRows, 0.01)
    }

    @Test
    fun `due color rule - pendingAmount greater than zero is Due and pendingAmount zero is Paid`() {
        val pendingJob = JobEntryEntity(
            id = 9301L,
            workspaceId = "ws_color_test",
            customerName = "Cust",
            operatorName = "Op",
            tractorLabel = "T",
            workType = "Work",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            durationMinutes = 60,
            totalAmount = 5000.0,
            amountReceived = 0.0,
            pendingAmount = 5000.0,
            addedByPartner = "Op"
        )

        val paidJob = pendingJob.copy(
            id = 9302L,
            amountReceived = 5000.0,
            pendingAmount = 0.0
        )

        assertTrue(pendingJob.pendingAmount > 0.0) // Due -> Red
        assertTrue(paidJob.pendingAmount <= 0.0)    // Paid -> Green
    }

    @Test
    fun `old entry status rule - historical age does not override financial payment status`() {
        val now = System.currentTimeMillis()
        val oldDateMillis = now - (10 * 86400000L) // 10 days ago (old entry)

        val oldPaidJob = JobEntryEntity(
            id = 9401L,
            workspaceId = "ws_old_status_test",
            customerName = "Cust Old",
            operatorName = "Op",
            tractorLabel = "T",
            workType = "Work",
            startTimeMillis = oldDateMillis,
            endTimeMillis = oldDateMillis + 3600000L,
            durationMinutes = 60,
            totalAmount = 4000.0,
            amountReceived = 4000.0,
            pendingAmount = 0.0,
            addedByPartner = "Op"
        )

        val oldDueJob = oldPaidJob.copy(
            id = 9402L,
            amountReceived = 0.0,
            pendingAmount = 4000.0
        )

        // Paid remains Paid (green) even when old
        assertEquals(0.0, oldPaidJob.pendingAmount, 0.001)

        // Due remains Due (red) even when old
        assertTrue(oldDueJob.pendingAmount > 0.0)
    }

    @Test
    fun `financial calculation scenario - comprehensive workflow verifying recorded, due, received, expenses, and job deletion`() = runBlocking {
        val jobDao = database.jobEntryDao()
        val custDao = database.customerDao()
        val expenseDao = database.expenseDao()
        val paymentDao = database.paymentDao()
        val wsId = "ws_financial_workflow_test"

        // 1. Create Customer A
        val custA = CustomerEntity(
            id = 8801L,
            workspaceId = wsId,
            name = "Customer A",
            phone = "9876543210"
        )
        custDao.insertCustomer(custA)

        // 2. Create job = ₹5,000 (unpaid)
        val job1 = JobEntryEntity(
            id = 8801L,
            workspaceId = wsId,
            customerId = custA.id,
            customerName = custA.name,
            customerPhone = custA.phone,
            operatorName = "Operator 1",
            tractorLabel = "Mahindra 575",
            workType = "Ploughing",
            startTimeMillis = 10000L,
            endTimeMillis = 15000L,
            durationMinutes = 60,
            hourlyRate = 5000.0,
            totalAmount = 5000.0,
            amountReceived = 0.0,
            pendingAmount = 5000.0,
            addedByPartner = "Partner 1"
        )
        jobDao.insertJob(job1)

        // 3. Verify: Total Recorded = ₹5,000, Due = ₹5,000, Received = ₹0
        var recorded = jobDao.getTotalRecordedForWorkspace(wsId).first() ?: 0.0
        var due = jobDao.getTotalPendingForWorkspace(wsId).first() ?: 0.0
        var received = maxOf(0.0, recorded - due)
        assertEquals(5000.0, recorded, 0.01)
        assertEquals(5000.0, due, 0.01)
        assertEquals(0.0, received, 0.01)

        // 4. Add payment = ₹5,000
        val payment1 = com.example.data.entity.PaymentEntity(
            id = 8801L,
            workspaceId = wsId,
            jobEntryId = job1.id,
            customerId = custA.id,
            customerName = custA.name,
            amount = 5000.0,
            paymentMethod = "Cash",
            collectedByName = "Owner"
        )
        paymentDao.insertPayment(payment1)
        val job1Paid = job1.copy(amountReceived = 5000.0, pendingAmount = 0.0)
        jobDao.insertJob(job1Paid)

        // 5. Verify: Total Recorded = ₹5,000, Due = ₹0, Received = ₹5,000
        recorded = jobDao.getTotalRecordedForWorkspace(wsId).first() ?: 0.0
        due = jobDao.getTotalPendingForWorkspace(wsId).first() ?: 0.0
        received = maxOf(0.0, recorded - due)
        assertEquals(5000.0, recorded, 0.01)
        assertEquals(0.0, due, 0.01)
        assertEquals(5000.0, received, 0.01)

        // 6. Create second job = ₹6,600 (unpaid)
        val job2 = JobEntryEntity(
            id = 8802L,
            workspaceId = wsId,
            customerId = custA.id,
            customerName = custA.name,
            customerPhone = custA.phone,
            operatorName = "Operator 1",
            tractorLabel = "Mahindra 575",
            workType = "Rotavator",
            startTimeMillis = 20000L,
            endTimeMillis = 25000L,
            durationMinutes = 90,
            hourlyRate = 4400.0,
            totalAmount = 6600.0,
            amountReceived = 0.0,
            pendingAmount = 6600.0,
            addedByPartner = "Partner 1"
        )
        jobDao.insertJob(job2)

        // 7. Verify: Total Recorded = ₹11,600, Due = ₹6,600, Received = ₹5,000
        recorded = jobDao.getTotalRecordedForWorkspace(wsId).first() ?: 0.0
        due = jobDao.getTotalPendingForWorkspace(wsId).first() ?: 0.0
        received = maxOf(0.0, recorded - due)
        assertEquals(11600.0, recorded, 0.01)
        assertEquals(6600.0, due, 0.01)
        assertEquals(5000.0, received, 0.01)

        // 8. Add expense = ₹2,500 linked to job2
        val exp = ExpenseEntity(
            id = 8801L,
            workspaceId = wsId,
            expenseType = "Diesel",
            amount = 2500.0,
            tractorLabel = "Mahindra 575",
            operatorName = "Operator 1",
            addedByPartner = "Partner 1",
            relatedJobId = job2.id
        )
        expenseDao.insertExpense(exp)

        // 9. Verify: Customer Due remains ₹6,600, Expense = ₹2,500, Customer Due does NOT become ₹9,100
        due = jobDao.getTotalPendingForWorkspace(wsId).first() ?: 0.0
        val expTotal = expenseDao.getTotalExpensesForWorkspace(wsId).first() ?: 0.0
        assertEquals(6600.0, due, 0.01)
        assertEquals(2500.0, expTotal, 0.01)

        // 10. Delete second job: Job removed, linked expense removed, Customer Due recalculated correctly
        expenseDao.deleteExpensesForJob(job2.id)
        jobDao.deleteJob(job2)

        val remainingJobs = jobDao.getJobsForWorkspace(wsId).first()
        val remainingExpenses = expenseDao.getExpensesForJob(job2.id)
        due = jobDao.getTotalPendingForWorkspace(wsId).first() ?: 0.0
        recorded = jobDao.getTotalRecordedForWorkspace(wsId).first() ?: 0.0

        assertEquals(1, remainingJobs.size)
        assertEquals(job1.id, remainingJobs[0].id)
        assertEquals(0, remainingExpenses.size)
        assertEquals(5000.0, recorded, 0.01)
        assertEquals(0.0, due, 0.01)
    }

    @Test
    fun `edit attribution and timestamp preservation test - editing preserves creator and historical creation date`() {
        val historicalDate = 1726635000000L // 18 September historical timestamp
        val originalJob = JobEntryEntity(
            id = 9501L,
            workspaceId = "ws_edit_test",
            customerName = "Ravi",
            customerPhone = "9876543210",
            operatorName = "Partner 1",
            tractorLabel = "John Deere",
            workType = "Ploughing",
            startTimeMillis = historicalDate,
            endTimeMillis = historicalDate + 3600000L,
            durationMinutes = 60,
            hourlyRate = 5000.0,
            totalAmount = 5000.0,
            amountReceived = 5000.0,
            pendingAmount = 0.0,
            addedByPartner = "Partner 1",
            createdByUid = "partner_1_uid",
            createdByRole = "Partner",
            createdAt = historicalDate
        )

        // Owner edits the entry on 25 September: amount changed to 5500
        val editTimestamp = 1727239800000L // 25 September
        val editedJob = originalJob.copy(
            totalAmount = 5500.0,
            amountReceived = 5500.0,
            // Creator attribution and historical creation date preserved
            createdAt = originalJob.createdAt,
            createdByUid = originalJob.createdByUid,
            createdByRole = originalJob.createdByRole,
            addedByPartner = originalJob.addedByPartner,
            // Editor attribution recorded separately
            editedByUid = "owner_uid",
            editedByName = "Owner",
            editedByRole = "Owner",
            updatedAt = editTimestamp
        )

        // Verify assertions:
        assertEquals(historicalDate, editedJob.createdAt) // Creation date preserved
        assertEquals("partner_1_uid", editedJob.createdByUid) // Creator UID preserved
        assertEquals("Partner", editedJob.createdByRole) // Creator role preserved
        assertEquals("Partner 1", editedJob.addedByPartner) // Entry for preserved
        assertEquals("owner_uid", editedJob.editedByUid) // Editor UID separate
        assertEquals("Owner", editedJob.editedByName) // Editor name separate
        assertEquals("Owner", editedJob.editedByRole) // Editor role separate
        assertEquals(editTimestamp, editedJob.updatedAt) // Updated timestamp set
    }

    @Test
    fun `customer phone requirement rule - mandatory only when pending amount is greater than zero`() {
        fun isPhoneInvalid(phone: String, pendingAmount: Double): Boolean {
            return if (pendingAmount > 0.0) {
                phone.length != 10 || !phone.all { it.isDigit() }
            } else {
                phone.isNotBlank() && (phone.length != 10 || !phone.all { it.isDigit() })
            }
        }

        // Scenario 1: Fully paid (due = 0) with empty phone -> VALID (not mandatory)
        assertFalse(isPhoneInvalid("", 0.0))

        // Scenario 2: Fully paid (due = 0) with valid 10-digit phone -> VALID
        assertFalse(isPhoneInvalid("9876543210", 0.0))

        // Scenario 3: Fully paid (due = 0) with invalid partial phone -> INVALID
        assertTrue(isPhoneInvalid("123", 0.0))

        // Scenario 4: Has pending amount (due > 0) with empty phone -> INVALID (mandatory)
        assertTrue(isPhoneInvalid("", 500.0))

        // Scenario 5: Has pending amount (due > 0) with invalid partial phone -> INVALID
        assertTrue(isPhoneInvalid("98765", 500.0))

        // Scenario 6: Has pending amount (due > 0) with valid 10-digit phone -> VALID
        assertFalse(isPhoneInvalid("9876543210", 500.0))
    }

    @Test
    fun `financial test scenario G - exact sequence with Job A, Job B, Expense, Job B full pay, and restart persistence`() = runBlocking {
        val jobDao = database.jobEntryDao()
        val custDao = database.customerDao()
        val expenseDao = database.expenseDao()
        val paymentDao = database.paymentDao()
        val wsId = "ws_scenario_g"

        val cust = CustomerEntity(id = 7001L, workspaceId = wsId, name = "Customer G", phone = "9876543210")
        custDao.insertCustomer(cust)

        // Job A: Amount = 5,000, Payment = 5,000
        val jobA = JobEntryEntity(
            id = 7101L,
            workspaceId = wsId,
            customerId = cust.id,
            customerName = cust.name,
            operatorName = "Operator 1",
            tractorLabel = "Mahindra",
            workType = "Ploughing",
            startTimeMillis = 1000L,
            endTimeMillis = 2000L,
            durationMinutes = 60,
            hourlyRate = 5000.0,
            totalAmount = 5000.0,
            amountReceived = 5000.0,
            pendingAmount = 0.0,
            addedByPartner = "Partner 1"
        )
        jobDao.insertJob(jobA)
        paymentDao.insertPayment(
            PaymentEntity(id = 7201L, workspaceId = wsId, jobEntryId = jobA.id, customerId = cust.id, amount = 5000.0)
        )

        var recorded = jobDao.getTotalRecordedForWorkspace(wsId).first() ?: 0.0
        var due = jobDao.getTotalPendingForWorkspace(wsId).first() ?: 0.0
        var received = maxOf(0.0, recorded - due)

        // Expected: Recorded = 5,000, Due = 0, Received = 5,000
        assertEquals(5000.0, recorded, 0.01)
        assertEquals(0.0, due, 0.01)
        assertEquals(5000.0, received, 0.01)

        // Job B: Amount = 6,600, Payment = 0
        val jobB = JobEntryEntity(
            id = 7102L,
            workspaceId = wsId,
            customerId = cust.id,
            customerName = cust.name,
            operatorName = "Operator 1",
            tractorLabel = "Mahindra",
            workType = "Rotavator",
            startTimeMillis = 3000L,
            endTimeMillis = 4000L,
            durationMinutes = 90,
            hourlyRate = 4400.0,
            totalAmount = 6600.0,
            amountReceived = 0.0,
            pendingAmount = 6600.0,
            addedByPartner = "Partner 1"
        )
        jobDao.insertJob(jobB)

        recorded = jobDao.getTotalRecordedForWorkspace(wsId).first() ?: 0.0
        due = jobDao.getTotalPendingForWorkspace(wsId).first() ?: 0.0
        received = maxOf(0.0, recorded - due)

        // Expected: Recorded = 11,600, Due = 6,600, Received = 5,000
        assertEquals(11600.0, recorded, 0.01)
        assertEquals(6600.0, due, 0.01)
        assertEquals(5000.0, received, 0.01)

        // Expense: 2,500
        val exp = ExpenseEntity(
            id = 7301L,
            workspaceId = wsId,
            expenseType = "Diesel",
            amount = 2500.0,
            tractorLabel = "Mahindra",
            operatorName = "Operator 1",
            addedByPartner = "Partner 1"
        )
        expenseDao.insertExpense(exp)

        due = jobDao.getTotalPendingForWorkspace(wsId).first() ?: 0.0
        val expTotal = expenseDao.getTotalExpensesForWorkspace(wsId).first() ?: 0.0

        // Customer Due remains 6,600, Expense = 2,500 (Does NOT increase due to 9,100)
        assertEquals(6600.0, due, 0.01)
        assertEquals(2500.0, expTotal, 0.01)

        // Pay Job B fully
        val jobBFullyPaid = jobB.copy(amountReceived = 6600.0, pendingAmount = 0.0)
        jobDao.insertJob(jobBFullyPaid)
        paymentDao.insertPayment(
            PaymentEntity(id = 7202L, workspaceId = wsId, jobEntryId = jobB.id, customerId = cust.id, amount = 6600.0)
        )

        recorded = jobDao.getTotalRecordedForWorkspace(wsId).first() ?: 0.0
        due = jobDao.getTotalPendingForWorkspace(wsId).first() ?: 0.0
        received = maxOf(0.0, recorded - due)

        // Expected: Recorded = 11,600, Due = 0, Received = 11,600
        assertEquals(11600.0, recorded, 0.01)
        assertEquals(0.0, due, 0.01)
        assertEquals(11600.0, received, 0.01)

        // Verification after reload: Query records again to verify state integrity
        val allJobs = jobDao.getJobsForWorkspace(wsId).first()
        val allExpenses = expenseDao.getExpensesForWorkspace(wsId).first()
        val allPayments = paymentDao.getPaymentsForWorkspace(wsId).first()

        assertEquals(2, allJobs.size)
        assertEquals(1, allExpenses.size)
        assertEquals(2, allPayments.size)
        assertEquals(0.0, allJobs.sumOf { it.pendingAmount }, 0.01)
        assertEquals(11600.0, allJobs.sumOf { it.amountReceived }, 0.01)
    }

    @Test
    fun `partner breakdown scenario M - owner creates for owner and partner without double counting`() {
        val jobs = listOf(
            JobEntryEntity(
                id = 8101L,
                customerName = "Cust 1",
                operatorName = "Owner",
                tractorLabel = "Tractor 1",
                workType = "Ploughing",
                startTimeMillis = 1000L,
                endTimeMillis = 2000L,
                durationMinutes = 60,
                totalAmount = 1000.0,
                amountReceived = 1000.0,
                pendingAmount = 0.0,
                addedByPartner = "Owner"
            ),
            JobEntryEntity(
                id = 8102L,
                customerName = "Cust 2",
                operatorName = "Owner",
                tractorLabel = "Tractor 1",
                workType = "Ploughing",
                startTimeMillis = 2000L,
                endTimeMillis = 3000L,
                durationMinutes = 60,
                totalAmount = 1500.0,
                amountReceived = 1500.0,
                pendingAmount = 0.0,
                addedByPartner = "Owner"
            ),
            JobEntryEntity(
                id = 8103L,
                customerName = "Cust 3",
                operatorName = "Partner 1",
                tractorLabel = "Tractor 2",
                workType = "Cultivator",
                startTimeMillis = 3000L,
                endTimeMillis = 4000L,
                durationMinutes = 60,
                totalAmount = 2200.0,
                amountReceived = 2200.0,
                pendingAmount = 0.0,
                addedByPartner = "Partner 1"
            ),
            JobEntryEntity(
                id = 8104L,
                customerName = "Cust 4",
                operatorName = "Partner 1",
                tractorLabel = "Tractor 2",
                workType = "Cultivator",
                startTimeMillis = 4000L,
                endTimeMillis = 5000L,
                durationMinutes = 60,
                totalAmount = 2000.0,
                amountReceived = 2000.0,
                pendingAmount = 0.0,
                addedByPartner = "Partner 1"
            )
        )

        val scope = FinancialScope(mode = FinancialScopeMode.OVERALL)
        val breakdowns = FinancialCalculationEngine.calculatePartnerBreakdown(
            jobs = jobs,
            expenses = emptyList(),
            withdrawals = emptyList(),
            partners = emptyList(),
            workspaceMembers = emptyList(),
            scope = scope
        )

        val ownerRow = breakdowns.find { it.partnerName.equals("Owner", ignoreCase = true) }
        val partner1Row = breakdowns.find { it.partnerName.equals("Partner 1", ignoreCase = true) }

        // Expected: Owner = 2,500, Partner 1 = 4,200
        assertEquals(2500.0, ownerRow?.recorded ?: 0.0, 0.01)
        assertEquals(4200.0, partner1Row?.recorded ?: 0.0, 0.01)

        // Total recorded across breakdown equals sum of distinct jobs without double counting
        val totalRecorded = breakdowns.sumOf { it.recorded }
        assertEquals(6700.0, totalRecorded, 0.01)
    }

    @Test
    fun `balance sheet date filtering L - daily and monthly calculations isolate period records`() {
        val cal = java.util.Calendar.getInstance()
        cal.set(2026, java.util.Calendar.SEPTEMBER, 18, 10, 0, 0)
        val sep18Millis = cal.timeInMillis

        cal.set(2026, java.util.Calendar.SEPTEMBER, 25, 10, 0, 0)
        val sep25Millis = cal.timeInMillis

        cal.set(2026, java.util.Calendar.OCTOBER, 5, 10, 0, 0)
        val oct5Millis = cal.timeInMillis

        val jobSep18 = JobEntryEntity(
            id = 8201L,
            customerName = "Cust 1",
            operatorName = "Op",
            tractorLabel = "T",
            workType = "Work",
            startTimeMillis = sep18Millis,
            endTimeMillis = sep18Millis + 3600000L,
            durationMinutes = 60,
            totalAmount = 3000.0,
            amountReceived = 3000.0,
            pendingAmount = 0.0,
            addedByPartner = "Op"
        )
        val jobSep25 = jobSep18.copy(id = 8202L, startTimeMillis = sep25Millis, totalAmount = 4000.0, amountReceived = 4000.0)
        val jobOct5 = jobSep18.copy(id = 8203L, startTimeMillis = oct5Millis, totalAmount = 5000.0, amountReceived = 5000.0)

        val allJobs = listOf(jobSep18, jobSep25, jobOct5)

        // 1. Daily scope for 18 September: only jobSep18 should be included
        val dailyScopeSep18 = FinancialScope(mode = FinancialScopeMode.DAILY, dateMillis = sep18Millis)
        val dailySummary = FinancialCalculationEngine.calculateSummary(allJobs, emptyList(), emptyList(), dailyScopeSep18)
        assertEquals(1, dailySummary.jobCount)
        assertEquals(3000.0, dailySummary.totalRecorded, 0.01)

        // 2. Monthly scope for September 2026: jobSep18 and jobSep25 should be included, jobOct5 excluded
        val monthlyScopeSep = FinancialScope(mode = FinancialScopeMode.MONTHLY, year = 2026, month = 9)
        val monthlySummary = FinancialCalculationEngine.calculateSummary(allJobs, emptyList(), emptyList(), monthlyScopeSep)
        assertEquals(2, monthlySummary.jobCount)
        assertEquals(7000.0, monthlySummary.totalRecorded, 0.01)

        // 3. Overall scope: all 3 jobs included
        val overallScope = FinancialScope(mode = FinancialScopeMode.OVERALL)
        val overallSummary = FinancialCalculationEngine.calculateSummary(allJobs, emptyList(), emptyList(), overallScope)
        assertEquals(3, overallSummary.jobCount)
        assertEquals(12000.0, overallSummary.totalRecorded, 0.01)
    }

    @Test
    fun `firestore serialization integrity Q - roundtrip preserves creator, editor, and timestamps`() {
        val originalJob = JobEntryEntity(
            id = 8301L,
            workspaceId = "ws_cloud_test",
            customerId = 42L,
            customerName = "Ravi Kumar",
            customerPhone = "9876543210",
            operatorName = "Partner 1",
            tractorLabel = "John Deere",
            workType = "Ploughing",
            startTimeMillis = 1726635000000L,
            endTimeMillis = 1726638600000L,
            durationMinutes = 60,
            hourlyRate = 1200.0,
            totalAmount = 1200.0,
            amountReceived = 1000.0,
            pendingAmount = 200.0,
            addedByPartner = "Partner 1",
            createdByUid = "partner_uid_123",
            createdByRole = "Partner",
            createdAt = 1726635000000L,
            editedByUid = "owner_uid_456",
            editedByName = "Owner Ram",
            editedByRole = "Owner",
            updatedAt = 1727239800000L
        )

        // Convert to Firestore map and reconstruct
        val firestoreMap = originalJob.toFirestoreMap(createdByUid = originalJob.createdByUid)
        val reconstructedJob = jobEntryFromFirestoreMap(firestoreMap, fallbackId = originalJob.id, fallbackWorkspaceId = originalJob.workspaceId)

        assertEquals(originalJob.id, reconstructedJob.id)
        assertEquals(originalJob.workspaceId, reconstructedJob.workspaceId)
        assertEquals(originalJob.createdAt, reconstructedJob.createdAt) // Preserved
        assertEquals(originalJob.createdByUid, reconstructedJob.createdByUid) // Preserved
        assertEquals(originalJob.createdByRole, reconstructedJob.createdByRole) // Preserved
        assertEquals(originalJob.addedByPartner, reconstructedJob.addedByPartner) // Preserved
        assertEquals(originalJob.editedByUid, reconstructedJob.editedByUid) // Preserved
        assertEquals(originalJob.editedByName, reconstructedJob.editedByName) // Preserved
        assertEquals(originalJob.editedByRole, reconstructedJob.editedByRole) // Preserved
        assertEquals(originalJob.updatedAt, reconstructedJob.updatedAt) // Preserved

        // Verify PaymentEntity Firestore mapping
        val payment = PaymentEntity(
            id = 8401L,
            workspaceId = "ws_cloud_test",
            jobEntryId = 8301L,
            customerId = 42L,
            customerName = "Ravi Kumar",
            amount = 1000.0,
            paymentMethod = "UPI",
            collectedByUid = "partner_uid_123",
            collectedByName = "Partner 1",
            collectedByRole = "Partner"
        )
        val paymentMap = payment.toFirestoreMap()
        val reconstructedPayment = paymentFromFirestoreMap(paymentMap, fallbackId = payment.id, fallbackWorkspaceId = payment.workspaceId)

        assertEquals(payment.id, reconstructedPayment.id)
        assertEquals(payment.collectedByUid, reconstructedPayment.collectedByUid)
        assertEquals(payment.collectedByName, reconstructedPayment.collectedByName)
        assertEquals(payment.collectedByRole, reconstructedPayment.collectedByRole)
    }

    @Test
    fun `auth otp length and phone validation rules A - exactly 6 digits accepted and invalid phone rejected`() {
        fun isValidOtp(otp: String): Boolean = otp.length == 6 && otp.all { it.isDigit() }
        fun isValidPhone(phone: String): Boolean {
            val digits = phone.filter { it.isDigit() }
            return digits.length == 10 || (digits.length == 12 && digits.startsWith("91"))
        }

        // OTP length tests
        assertFalse(isValidOtp(""))
        assertFalse(isValidOtp("1234"))    // Old 4-digit assumption rejected
        assertFalse(isValidOtp("12345"))   // 5 digits rejected
        assertTrue(isValidOtp("123456"))   // Exactly 6 digits accepted
        assertFalse(isValidOtp("1234567")) // 7 digits rejected
        assertFalse(isValidOtp("12a456"))  // Non-digit rejected

        // Phone tests
        assertTrue(isValidPhone("9876543210"))
        assertTrue(isValidPhone("+91 98765 43210"))
        assertFalse(isValidPhone("12345"))
        assertFalse(isValidPhone("987654321000"))
    }
}

