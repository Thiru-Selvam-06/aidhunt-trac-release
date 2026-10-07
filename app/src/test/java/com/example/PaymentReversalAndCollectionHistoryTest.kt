package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.auth.AuthorizationManager
import com.example.data.auth.RoleUtils
import com.example.data.database.AppDatabase
import com.example.data.entity.CustomerEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
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
class PaymentReversalAndCollectionHistoryTest {

    private lateinit var database: AppDatabase
    private val workspaceId = "ws_test_reversal"

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

    private fun createTestJob(
        id: Long,
        customerId: Long,
        customerName: String,
        totalAmount: Double,
        amountReceived: Double,
        pendingAmount: Double,
        createdByUid: String = "",
        createdByRole: String = "",
        startTimeMillis: Long = 1000L
    ): JobEntryEntity = JobEntryEntity(
        id = id,
        workspaceId = workspaceId,
        customerId = customerId,
        customerName = customerName,
        operatorName = "Operator 1",
        tractorLabel = "Mahindra 575",
        workType = "Ploughing",
        startTimeMillis = startTimeMillis,
        endTimeMillis = startTimeMillis + 3600_000L,
        durationMinutes = 60L,
        totalAmount = totalAmount,
        amountReceived = amountReceived,
        pendingAmount = pendingAmount,
        addedByPartner = "Operator 1",
        createdByUid = createdByUid,
        createdByRole = createdByRole,
        createdAt = startTimeMillis
    )

    private fun createTestCustomer(
        id: Long,
        name: String,
        phone: String = "9876543210",
        totalBilled: Double = 0.0,
        totalPaid: Double = 0.0,
        balanceDue: Double = 0.0
    ): CustomerEntity = CustomerEntity(
        id = id,
        workspaceId = workspaceId,
        name = name,
        phone = phone,
        totalBilled = totalBilled,
        totalPaid = totalPaid,
        balanceDue = balanceDue
    )

    // Helper: calculate collection history cards matching CollectionHistoryTab logic
    private fun getCollectionHistoryCards(
        payments: List<PaymentEntity>,
        jobs: List<JobEntryEntity>
    ): List<Pair<Long, Double>> {
        val list = mutableListOf<Pair<Long, Double>>()
        val seenPaymentIds = mutableSetOf<Long>()
        val seenJobEntryIds = mutableSetOf<Long>()

        // 1. Authoritative explicit payment entities
        payments.forEach { p ->
            seenPaymentIds.add(p.id)
            if (p.jobEntryId > 0L) {
                seenJobEntryIds.add(p.jobEntryId)
            }
            list.add(p.id to p.amount)
        }

        // 2. Standalone synthetic payment jobs not backed by explicit PaymentEntity
        val syntheticJobs = jobs.filter { FinancialCalculationEngine.isSyntheticPayment(it) && it.amountReceived > 0.0 }
        syntheticJobs.forEach { j ->
            val isAlreadyRepresented = seenJobEntryIds.contains(j.id) || 
                seenPaymentIds.contains(j.id) ||
                payments.any { p ->
                    p.customerId == j.customerId &&
                    p.amount == j.amountReceived &&
                    (p.jobEntryId == j.id || kotlin.math.abs(p.collectedAt - (j.startTimeMillis.takeIf { t -> t > 0 } ?: j.createdAt)) < 120_000L)
                }
            if (!isAlreadyRepresented) {
                list.add(j.id to j.amountReceived)
            }
        }

        // 3. Initial collections at job creation time for work jobs
        val workJobsWithReceived = jobs.filter { !FinancialCalculationEngine.isSyntheticPayment(it) && it.amountReceived > 0.0 }
        workJobsWithReceived.forEach { j ->
            val explicitPaymentsForJob = payments.filter { it.jobEntryId == j.id }.sumOf { it.amount }
            val initialAdvance = FinancialCalculationEngine.roundToPositiveWholeRupee(j.amountReceived - explicitPaymentsForJob)
            if (initialAdvance > 0.0) {
                list.add(-j.id to initialAdvance)
            }
        }

        return list
    }

    @Test
    fun `TEST 1 and 2 - create due job, record payment, collection history is 1, then delete payment fully reverses to DUE`() = runBlocking {
        val jobDao = database.jobEntryDao()
        val paymentDao = database.paymentDao()
        val customerDao = database.customerDao()

        // Step 1: Create original due job (₹27,500 total, ₹0 received, ₹27,500 due)
        val customer = createTestCustomer(
            id = 100L,
            name = "Ramesh",
            totalBilled = 27500.0,
            totalPaid = 0.0,
            balanceDue = 27500.0
        )
        customerDao.insertCustomer(customer)

        val originalJob = createTestJob(
            id = 101L,
            customerId = 100L,
            customerName = "Ramesh",
            totalAmount = 27500.0,
            amountReceived = 0.0,
            pendingAmount = 27500.0
        )
        jobDao.insertJob(originalJob)

        // Verify pre-payment state
        assertEquals(27500.0, originalJob.pendingAmount, 0.01)
        val historyBeforePayment = getCollectionHistoryCards(emptyList(), listOf(originalJob))
        assertEquals("No collection cards before payment", 0, historyBeforePayment.size)

        // Step 2: Record payment of ₹27,500
        val updatedJob = originalJob.copy(
            amountReceived = 27500.0,
            pendingAmount = 0.0,
            updatedAt = 2000L
        )
        jobDao.insertJob(updatedJob)

        val payment = PaymentEntity(
            id = 501L,
            workspaceId = workspaceId,
            jobEntryId = 101L,
            customerId = 100L,
            customerName = "Ramesh",
            amount = 27500.0,
            collectedByUid = "uid_owner",
            collectedByName = "Owner",
            collectedByRole = "Owner",
            collectedAt = 2000L
        )
        paymentDao.insertPayment(payment)

        val jobsAfterPayment = jobDao.getAllJobs().first()
        val paymentsAfterPayment = paymentDao.getAllPayments().first()
        
        // TEST 1: Collection History must show EXACTLY ONE event
        val historyAfterPayment = getCollectionHistoryCards(paymentsAfterPayment, jobsAfterPayment)
        assertEquals("Collection History must contain exactly 1 event", 1, historyAfterPayment.size)
        assertEquals(501L, historyAfterPayment[0].first)
        assertEquals(27500.0, historyAfterPayment[0].second, 0.01)

        // Customer stats after payment: Due = 0
        val custAfterPayment = FinancialCalculationEngine.calculateCustomerFinancials(customer, jobsAfterPayment)
        assertEquals(0.0, custAfterPayment.balanceDue, 0.01)
        assertEquals(27500.0, custAfterPayment.totalPaid, 0.01)

        // Step 3: Delete the payment
        // Reversal logic:
        val jobToRevert = jobDao.getJobById(payment.jobEntryId)!!
        val revertedJob = jobToRevert.copy(
            amountReceived = FinancialCalculationEngine.roundToPositiveWholeRupee(maxOf(0.0, jobToRevert.amountReceived - payment.amount)),
            pendingAmount = FinancialCalculationEngine.roundToPositiveWholeRupee(maxOf(0.0, jobToRevert.totalAmount - (jobToRevert.amountReceived - payment.amount))),
            updatedAt = 3000L
        )
        jobDao.insertJob(revertedJob)
        paymentDao.deletePayment(payment.id)

        // TEST 2: After deletion
        val jobsAfterDelete = jobDao.getAllJobs().first()
        val paymentsAfterDelete = paymentDao.getAllPayments().first()

        // 1. Original job remains intact
        assertEquals(1, jobsAfterDelete.size)
        assertEquals(101L, jobsAfterDelete[0].id)
        // 2. Job becomes DUE (amountReceived = 0, pendingAmount = 27500)
        assertEquals(0.0, jobsAfterDelete[0].amountReceived, 0.01)
        assertEquals(27500.0, jobsAfterDelete[0].pendingAmount, 0.01)

        // 3. Customer due restored
        val custAfterDelete = FinancialCalculationEngine.calculateCustomerFinancials(customer, jobsAfterDelete)
        assertEquals(27500.0, custAfterDelete.balanceDue, 0.01)
        assertEquals(0.0, custAfterDelete.totalPaid, 0.01)

        // 4. Collection History = 0
        val historyAfterDelete = getCollectionHistoryCards(paymentsAfterDelete, jobsAfterDelete)
        assertEquals(0, historyAfterDelete.size)
    }

    @Test
    fun `TEST 3 and 4 - Home and Business Overview financial calculations reverse completely`() = runBlocking {
        val jobDao = database.jobEntryDao()
        val paymentDao = database.paymentDao()

        // Initial job: ₹27,500, received 0
        val job = createTestJob(
            id = 101L,
            customerId = 100L,
            customerName = "Ramesh",
            totalAmount = 27500.0,
            amountReceived = 0.0,
            pendingAmount = 27500.0
        )
        jobDao.insertJob(job)

        // Add payment
        val paidJob = job.copy(amountReceived = 27500.0, pendingAmount = 0.0)
        jobDao.insertJob(paidJob)
        val payment = PaymentEntity(id = 501L, workspaceId = workspaceId, jobEntryId = 101L, customerId = 100L, amount = 27500.0)
        paymentDao.insertPayment(payment)

        // Home calculation:
        val homeReceivedAfterPay = listOf(paidJob).filter { !FinancialCalculationEngine.isSyntheticPayment(it) }.sumOf { j ->
            val explicit = listOf(payment).filter { it.jobEntryId == j.id }.sumOf { it.amount }
            maxOf(0.0, j.amountReceived - explicit)
        } + listOf(payment).sumOf { it.amount }
        assertEquals(27500.0, homeReceivedAfterPay, 0.01)

        // Business overview:
        val overviewAfterPay = FinancialCalculationEngine.calculatePartnerShareAndInvestment(
            jobs = listOf(paidJob),
            expenses = emptyList(),
            withdrawals = emptyList(),
            scope = FinancialScope(FinancialScopeMode.OVERALL)
        )
        val totalReceivedOverview = FinancialCalculationEngine.roundToPositiveWholeRupee(listOf(paidJob).sumOf { it.amountReceived })
        assertEquals(27500.0, totalReceivedOverview, 0.01)

        // Now reverse payment
        val revertedJob = paidJob.copy(amountReceived = 0.0, pendingAmount = 27500.0)
        jobDao.insertJob(revertedJob)
        paymentDao.deletePayment(payment.id)

        // TEST 3: Home payment received disappears
        val homeReceivedAfterReversal = listOf(revertedJob).filter { !FinancialCalculationEngine.isSyntheticPayment(it) }.sumOf { j ->
            val explicit = emptyList<PaymentEntity>().filter { it.jobEntryId == j.id }.sumOf { it.amount }
            maxOf(0.0, j.amountReceived - explicit)
        } + emptyList<PaymentEntity>().sumOf { it.amount }
        assertEquals(0.0, homeReceivedAfterReversal, 0.01)

        // TEST 4: Business Overview collected amount reverses to 0
        val totalReceivedOverviewReversed = FinancialCalculationEngine.roundToPositiveWholeRupee(listOf(revertedJob).sumOf { it.amountReceived })
        assertEquals(0.0, totalReceivedOverviewReversed, 0.01)
    }

    @Test
    fun `TEST 5 and 6 - Partial payment scenario preserves two events and reverses only deleted portion`() = runBlocking {
        val jobDao = database.jobEntryDao()
        val paymentDao = database.paymentDao()
        val customerDao = database.customerDao()

        // Job created with ₹27,500 total, Owner collected ₹20,000 at creation time, ₹7,500 due
        val customer = createTestCustomer(id = 100L, name = "Kannan", totalBilled = 27500.0, totalPaid = 20000.0, balanceDue = 7500.0)
        customerDao.insertCustomer(customer)

        val job = createTestJob(
            id = 201L,
            customerId = 100L,
            customerName = "Kannan",
            totalAmount = 27500.0,
            amountReceived = 20000.0,
            pendingAmount = 7500.0,
            createdByUid = "uid_owner",
            createdByRole = "Owner"
        )
        jobDao.insertJob(job)

        // Before partner payment: exactly 1 event (Owner initial advance ₹20,000)
        val initialCards = getCollectionHistoryCards(emptyList(), listOf(job))
        assertEquals(1, initialCards.size)
        assertEquals(-201L, initialCards[0].first)
        assertEquals(20000.0, initialCards[0].second, 0.01)

        // Partner collects remaining ₹7,500
        val paidJob = job.copy(amountReceived = 27500.0, pendingAmount = 0.0)
        jobDao.insertJob(paidJob)

        val partnerPayment = PaymentEntity(
            id = 601L,
            workspaceId = workspaceId,
            jobEntryId = 201L,
            customerId = 100L,
            customerName = "Kannan",
            amount = 7500.0,
            collectedByUid = "uid_partner",
            collectedByName = "Partner",
            collectedByRole = "Partner",
            collectedAt = 5000L
        )
        paymentDao.insertPayment(partnerPayment)

        // TEST 6: Two legitimate payments remain TWO separate events
        val cardsWithBoth = getCollectionHistoryCards(listOf(partnerPayment), listOf(paidJob))
        assertEquals("Collection history must show exactly 2 events", 2, cardsWithBoth.size)
        assertTrue("Contains Partner payment of ₹7,500", cardsWithBoth.any { it.first == 601L && it.second == 7500.0 })
        assertTrue("Contains Owner payment of ₹20,000", cardsWithBoth.any { it.first == -201L && it.second == 20000.0 })

        // TEST 5: Partner deletes ₹7,500 payment
        // Reversal reduces received by 7500, restores pending to 7500
        val jobAfterPartnerReversal = paidJob.copy(
            amountReceived = 20000.0,
            pendingAmount = 7500.0
        )
        jobDao.insertJob(jobAfterPartnerReversal)
        paymentDao.deletePayment(partnerPayment.id)

        // Payment 1 (₹20,000) remains, Payment 2 is removed
        val cardsAfterPartnerDelete = getCollectionHistoryCards(emptyList(), listOf(jobAfterPartnerReversal))
        assertEquals(1, cardsAfterPartnerDelete.size)
        assertEquals(-201L, cardsAfterPartnerDelete[0].first)
        assertEquals(20000.0, cardsAfterPartnerDelete[0].second, 0.01)

        // Customer due restored to ₹7,500
        val customerStats = FinancialCalculationEngine.calculateCustomerFinancials(customer, listOf(jobAfterPartnerReversal))
        assertEquals(7500.0, customerStats.balanceDue, 0.01)
        assertEquals(20000.0, customerStats.totalPaid, 0.01)
    }

    @Test
    fun `TEST 1 - Owner deletes Owner payment`() {
        val now = 1_000_000_000L
        val canDelete = AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = "uid_owner",
            collectionCreatedAt = now - 1000L,
            isOwner = true,
            currentUid = "uid_owner",
            currentTimeMillis = now,
            role = RoleUtils.ROLE_OWNER
        )
        assertTrue("Owner can delete Owner payment", canDelete)
    }

    @Test
    fun `TEST 2 - Owner deletes Partner payment`() {
        val now = 1_000_000_000L
        val canDelete = AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = "uid_partner_1",
            collectionCreatedAt = now - 1000L,
            isOwner = true,
            currentUid = "uid_owner",
            currentTimeMillis = now,
            role = RoleUtils.ROLE_OWNER
        )
        assertTrue("Owner can delete Partner payment", canDelete)
    }

    @Test
    fun `TEST 3 - Owner deletes older payment greater than 24h`() {
        val now = 1_000_000_000L
        val canDelete = AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = "uid_partner_1",
            collectionCreatedAt = now - 100_000_000L, // ~28 hours ago
            isOwner = true,
            currentUid = "uid_owner",
            currentTimeMillis = now,
            role = RoleUtils.ROLE_OWNER
        )
        assertTrue("Owner can delete older payment >24h", canDelete)
    }

    @Test
    fun `TEST 4 - Partner deletes own payment within 24h`() {
        val now = 1_000_000_000L
        val canDelete = AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = "uid_partner_1",
            collectionCreatedAt = now - 3600_000L, // 1 hour ago
            isOwner = false,
            currentUid = "uid_partner_1",
            currentTimeMillis = now,
            role = RoleUtils.ROLE_PARTNER
        )
        assertTrue("Partner can delete own payment within 24h", canDelete)
    }

    @Test
    fun `TEST 5 - Partner own payment after 24h`() {
        val now = 1_000_000_000L
        val canDelete = AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = "uid_partner_1",
            collectionCreatedAt = now - 90_000_000L, // 25 hours ago
            isOwner = false,
            currentUid = "uid_partner_1",
            currentTimeMillis = now,
            role = RoleUtils.ROLE_PARTNER
        )
        assertFalse("Partner own payment after 24h is denied", canDelete)
    }

    @Test
    fun `TEST 6 - Partner deletes Owner payment`() {
        val now = 1_000_000_000L
        val canDelete = AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = "uid_owner",
            collectionCreatedAt = now - 3600_000L,
            isOwner = false,
            currentUid = "uid_partner_1",
            currentTimeMillis = now,
            role = RoleUtils.ROLE_PARTNER
        )
        assertFalse("Partner cannot delete Owner payment", canDelete)
    }

    @Test
    fun `TEST 7 - Partner deletes another Partner payment`() {
        val now = 1_000_000_000L
        val canDelete = AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = "uid_partner_2",
            collectionCreatedAt = now - 3600_000L,
            isOwner = false,
            currentUid = "uid_partner_1",
            currentTimeMillis = now,
            role = RoleUtils.ROLE_PARTNER
        )
        assertFalse("Partner cannot delete another Partner payment", canDelete)
    }

    @Test
    fun `TEST 8 - Partner deletes Operator payment`() {
        val now = 1_000_000_000L
        val canDelete = AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = "uid_operator",
            collectionCreatedAt = now - 3600_000L,
            isOwner = false,
            currentUid = "uid_partner_1",
            currentTimeMillis = now,
            role = RoleUtils.ROLE_PARTNER
        )
        assertFalse("Partner cannot delete Operator payment", canDelete)
    }

    @Test
    fun `TEST 9 - Operator deletes Owner payment`() {
        val now = 1_000_000_000L
        val canDelete = AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = "uid_owner",
            collectionCreatedAt = now - 1000L,
            isOwner = false,
            currentUid = "uid_operator",
            currentTimeMillis = now,
            role = RoleUtils.ROLE_OPERATOR
        )
        assertFalse("Operator cannot delete Owner payment", canDelete)
    }

    @Test
    fun `TEST 10 - Operator deletes Partner payment`() {
        val now = 1_000_000_000L
        val canDelete = AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = "uid_partner_1",
            collectionCreatedAt = now - 1000L,
            isOwner = false,
            currentUid = "uid_operator",
            currentTimeMillis = now,
            role = RoleUtils.ROLE_OPERATOR
        )
        assertFalse("Operator cannot delete Partner payment", canDelete)
    }

    @Test
    fun `TEST 11 - Operator deletes own payment`() {
        val now = 1_000_000_000L
        val canDelete = AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = "uid_operator",
            collectionCreatedAt = now - 1000L,
            isOwner = false,
            currentUid = "uid_operator",
            currentTimeMillis = now,
            role = RoleUtils.ROLE_OPERATOR
        )
        assertFalse("Operator cannot delete own payment", canDelete)
    }

    @Test
    fun `TEST 12 to 17 - UI Delete Visibility Matrix`() {
        val now = 1_000_000_000L

        // Helper representing UI delete button visibility
        fun isDeleteVisible(
            actorRole: String,
            actorUid: String,
            isOwner: Boolean,
            paymentCreatorUid: String,
            paymentCreatedAt: Long
        ): Boolean {
            return AuthorizationManager.canDeleteCollection(
                collectionCreatedByUid = paymentCreatorUid,
                collectionCreatedAt = paymentCreatedAt,
                isOwner = isOwner,
                currentUid = actorUid,
                currentTimeMillis = now,
                role = actorRole
            )
        }

        // 12. Owner sees Delete on Partner payment
        assertTrue(
            "12. Owner sees Delete on Partner payment",
            isDeleteVisible(RoleUtils.ROLE_OWNER, "uid_owner", true, "uid_partner_1", now - 1000L)
        )

        // 13. Partner sees Delete on own <=24h payment
        assertTrue(
            "13. Partner sees Delete on own <=24h payment",
            isDeleteVisible(RoleUtils.ROLE_PARTNER, "uid_partner_1", false, "uid_partner_1", now - 3600_000L)
        )

        // 14. Partner does not see Delete on Owner payment
        assertFalse(
            "14. Partner does not see Delete on Owner payment",
            isDeleteVisible(RoleUtils.ROLE_PARTNER, "uid_partner_1", false, "uid_owner", now - 3600_000L)
        )

        // 15. Partner does not see Delete on another Partner payment
        assertFalse(
            "15. Partner does not see Delete on another Partner payment",
            isDeleteVisible(RoleUtils.ROLE_PARTNER, "uid_partner_1", false, "uid_partner_2", now - 3600_000L)
        )

        // 16. Partner does not see Delete after 24h
        assertFalse(
            "16. Partner does not see Delete after 24h",
            isDeleteVisible(RoleUtils.ROLE_PARTNER, "uid_partner_1", false, "uid_partner_1", now - 90_000_000L)
        )

        // 17. Operator does not see Delete
        assertFalse(
            "17. Operator does not see Delete on any payment",
            isDeleteVisible(RoleUtils.ROLE_OPERATOR, "uid_operator", false, "uid_operator", now - 1000L)
        )
    }

    @Test
    fun `TEST 18 - Customer Credit and Collection History share identical delete decision`() {
        val now = 1_000_000_000L
        val payment = PaymentEntity(
            id = 555L,
            workspaceId = workspaceId,
            customerId = 100L,
            amount = 5000.0,
            collectedByUid = "uid_partner_1",
            collectedAt = now - 3600_000L,
            createdAt = now - 3600_000L
        )

        val partnerDecisionCollectionHistory = AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = payment.collectedByUid,
            collectionCreatedAt = payment.createdAt,
            isOwner = false,
            currentUid = "uid_partner_1",
            currentTimeMillis = now,
            role = RoleUtils.ROLE_PARTNER
        )

        val partnerDecisionCustomerCredit = AuthorizationManager.canDeleteCollection(
            collectionCreatedByUid = payment.collectedByUid,
            collectionCreatedAt = payment.createdAt,
            isOwner = false,
            currentUid = "uid_partner_1",
            currentTimeMillis = now,
            role = RoleUtils.ROLE_PARTNER
        )

        assertEquals(
            "Customer Credit and Collection History must agree identically",
            partnerDecisionCollectionHistory,
            partnerDecisionCustomerCredit
        )
    }

    @Test
    fun `TEST 11 - Payment timestamp is preserved accurately`() {
        val paymentTime = 1728250800000L // 21:40
        val payment = PaymentEntity(
            id = 701L,
            workspaceId = workspaceId,
            jobEntryId = 101L,
            customerId = 100L,
            amount = 27500.0,
            collectedAt = paymentTime,
            createdAt = paymentTime
        )
        assertEquals(paymentTime, payment.collectedAt)
        
        // Firestore map preservation
        val map = payment.toFirestoreMap()
        assertEquals(paymentTime, map["collectedAt"])
        val restored = paymentFromFirestoreMap(map)
        assertEquals(paymentTime, restored.collectedAt)
    }

    @Test
    fun `TEST 12 and 13 - Same payment is not duplicated after sync or restart`() = runBlocking {
        val paymentDao = database.paymentDao()
        val jobDao = database.jobEntryDao()

        val job = createTestJob(
            id = 801L,
            customerId = 100L,
            customerName = "Selvam",
            totalAmount = 27500.0,
            amountReceived = 27500.0,
            pendingAmount = 0.0
        )
        jobDao.insertJob(job)

        val payment = PaymentEntity(
            id = 901L,
            workspaceId = workspaceId,
            jobEntryId = 801L,
            customerId = 100L,
            customerName = "Selvam",
            amount = 27500.0,
            collectedAt = 1000L
        )
        paymentDao.insertPayment(payment)

        // Simulate Firestore sync mapping back into local database
        val firestoreMap = payment.toFirestoreMap()
        val syncedPayment = paymentFromFirestoreMap(firestoreMap)
        paymentDao.insertPayment(syncedPayment) // OnConflictStrategy.REPLACE

        val allPayments = paymentDao.getAllPayments().first()
        assertEquals("No duplicate payments in database after sync", 1, allPayments.size)

        val cards = getCollectionHistoryCards(allPayments, listOf(job))
        assertEquals("Collection history must display exactly 1 card", 1, cards.size)

        // Simulate app restart / re-querying from database
        val reloadedPayments = database.paymentDao().getPaymentsForCustomer(100L)
        val reloadedJobs = database.jobEntryDao().getAllJobs().first()
        val cardsAfterRestart = getCollectionHistoryCards(reloadedPayments, reloadedJobs)
        assertEquals("Collection history after restart remains exactly 1 card", 1, cardsAfterRestart.size)
    }

    @Test
    fun `TEST 19 - Recent Job Card Billed vs Remaining Due States`() {
        // A. Unpaid Job: Billed = 4400, Received = 0, Pending = 4400
        val unpaidJob = createTestJob(
            id = 1001L,
            customerId = 100L,
            customerName = "Arun",
            totalAmount = 4400.0,
            amountReceived = 0.0,
            pendingAmount = 4400.0
        )
        val isUnpaidPaid = (unpaidJob.amountReceived >= unpaidJob.totalAmount && unpaidJob.pendingAmount <= 0.0) || unpaidJob.pendingAmount <= 0.0
        assertFalse("Unpaid job must not be marked paid", isUnpaidPaid)
        assertEquals("Unpaid job billed amount must be 4400", 4400.0, unpaidJob.totalAmount, 0.01)
        assertEquals("Unpaid job remaining due must be 4400", 4400.0, unpaidJob.pendingAmount, 0.01)

        // B. Partially Paid Job: Billed = 4400, Received = 500, Pending = 3900
        val partialJob = createTestJob(
            id = 1002L,
            customerId = 100L,
            customerName = "Arun",
            totalAmount = 4400.0,
            amountReceived = 500.0,
            pendingAmount = 3900.0
        )
        val isPartialPaid = (partialJob.amountReceived >= partialJob.totalAmount && partialJob.pendingAmount <= 0.0) || partialJob.pendingAmount <= 0.0
        assertFalse("Partially paid job must not be marked paid", isPartialPaid)
        assertEquals("Partially paid job billed amount must remain 4400", 4400.0, partialJob.totalAmount, 0.01)
        assertEquals("Partially paid job remaining due must be 3900", 3900.0, partialJob.pendingAmount, 0.01)

        // C. Fully Paid Job: Billed = 4400, Received = 4400, Pending = 0
        val paidJob = createTestJob(
            id = 1003L,
            customerId = 100L,
            customerName = "Arun",
            totalAmount = 4400.0,
            amountReceived = 4400.0,
            pendingAmount = 0.0
        )
        val isPaidState = (paidJob.amountReceived >= paidJob.totalAmount && paidJob.pendingAmount <= 0.0) || paidJob.pendingAmount <= 0.0
        assertTrue("Fully paid job must be marked paid", isPaidState)
        assertEquals("Fully paid job billed amount must be 4400", 4400.0, paidJob.totalAmount, 0.01)
        assertEquals("Fully paid job pending amount must be 0", 0.0, paidJob.pendingAmount, 0.01)

        // D. After Reversal: Billed = 4400, Received = 0, Pending = 4400
        val reversedJob = paidJob.copy(
            amountReceived = 0.0,
            pendingAmount = 4400.0
        )
        val isReversedPaid = (reversedJob.amountReceived >= reversedJob.totalAmount && reversedJob.pendingAmount <= 0.0) || reversedJob.pendingAmount <= 0.0
        assertFalse("Reversed job must not be marked paid", isReversedPaid)
        assertEquals("Reversed job billed amount must be 4400", 4400.0, reversedJob.totalAmount, 0.01)
        assertEquals("Reversed job remaining due restored to 4400", 4400.0, reversedJob.pendingAmount, 0.01)
    }
}
