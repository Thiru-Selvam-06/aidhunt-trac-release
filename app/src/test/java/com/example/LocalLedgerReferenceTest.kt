package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.PaymentEntity
import com.example.data.entity.PendingOperationEntity
import com.example.ui.util.FinancialCalculationEngine
import com.example.ui.util.FinancialScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Synthetic reference snapshot only: no approved opening balance/cutoff is implied.
 * DB close/reopen models durable storage, not Android process death or cloud delivery.
 */
@RunWith(RobolectricTestRunner::class)
class LocalLedgerReferenceTest {
    private val beforeReference = 1700000000000L
    private val afterReference = beforeReference + 86400000L
    private val id = 9007199254740995L
    private fun payment(key: Long, amount: Double, time: Long) = PaymentEntity(
        id = key, workspaceId = "synthetic-pms", customerId = 1, jobEntryId = id,
        amount = amount, collectedAt = time, createdAt = time,
        collectedByUid = "synthetic-owner", collectorUid = "synthetic-partner",
        paymentMethod = "Cash", status = "ACTIVE", isSynced = false)

    @Test fun durableLedgerKeepsIdentityAfterReopenAndRetry() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "ledger.db"
        check(context.getDatabasePath(name).parentFile!!.let { it.isDirectory || it.mkdirs() })
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var db = open()
        val old = payment(id, 100.0, beforeReference)
        val newer = payment(id + 1, 25.0, afterReference)
        val payload = "{\"workspaceId\":\"synthetic-pms\",\"id\":\"$id\",\"collectedByUid\":\"synthetic-owner\",\"collectorUid\":\"synthetic-partner\",\"collectedAt\":$beforeReference}"
        val operation = PendingOperationEntity("synthetic-original", "synthetic-pms", "synthetic-owner",
            "PAYMENT", id, "UPSERT", 1, beforeReference + 1, payload)
        try {
            db.paymentDao().insertPayments(listOf(old, newer))
            db.pendingOperationDao().insert(operation)
            db.close(); db = open()
            assertEquals(operation, db.pendingOperationDao().getOperation(operation.operationId))
            assertEquals(old, db.paymentDao().getPaymentById("synthetic-pms", id))
            fun cash(rows: List<PaymentEntity>) = FinancialCalculationEngine.cashAvailable(
                rows, emptyList(), emptyList(), FinancialScope(workspaceId = "synthetic-pms"))
            assertEquals(125.0, cash(db.paymentDao().getPaymentsForCustomer("synthetic-pms", 1)), 0.0)
            // Model duplicate delivery as repeated upserts of the SAME authoritative ID.
            // This does not exercise HTTP retry/acknowledgement; those have separate tests.
            repeat(2) { db.paymentDao().insertPayment(old.copy(isSynced = true)) }
            val rows = db.paymentDao().getPaymentsForCustomer("synthetic-pms", 1)
            assertEquals(2, rows.size)
            assertEquals(125.0, cash(rows), 0.0)
            assertEquals(beforeReference, rows.single { it.id == id }.collectedAt)
            assertEquals("synthetic-owner", rows.single { it.id == id }.collectedByUid)
            assertEquals("synthetic-partner", rows.single { it.id == id }.collectorUid)
            // Upload flags or a local upsert must not falsely acknowledge the outbox.
            assertEquals(operation, db.pendingOperationDao().getOperation(operation.operationId))
            db.pendingOperationDao().failed(operation.operationId, "REJECTED", "synthetic membership revoked")
            db.close(); db = open()
            val rejected = db.pendingOperationDao().getOperation(operation.operationId)!!
            assertEquals("REJECTED", rejected.state)
            assertEquals(operation.payloadJson, rejected.payloadJson)
            assertEquals(operation.actionTime, rejected.actionTime)
            assertEquals(operation.workspaceId, rejected.workspaceId)
            assertEquals(operation.actorUid, rejected.actorUid)
            assertTrue(db.pendingOperationDao().nextBatch("synthetic-pms", "synthetic-owner").isEmpty())
            assertEquals(125.0, cash(db.paymentDao().getPaymentsForCustomer("synthetic-pms", 1)), 0.0)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun historicalReversalAndWorkspaceSwitchDoNotCreateAnOpeningTransaction() {
        val old = payment(id, 100.0, beforeReference)
        val newer = payment(id + 1, 25.0, afterReference)
        val foreign = payment(id, 999.0, beforeReference).copy(workspaceId = "synthetic-other")
        val retainedAudit = old.copy(status = "REVERSED", isSynced = true)
        val rows = listOf(retainedAudit, newer, foreign)
        fun cash(workspace: String) = FinancialCalculationEngine.cashAvailable(rows, emptyList(), emptyList(), FinancialScope(workspaceId = workspace))
        assertEquals(25.0, cash("synthetic-pms"), 0.0)
        assertEquals(999.0, cash("synthetic-other"), 0.0)
        assertEquals(25.0, cash("synthetic-pms"), 0.0)
        assertEquals("REVERSED", retainedAudit.status)
        assertEquals(beforeReference, retainedAudit.createdAt)
        assertEquals(beforeReference, retainedAudit.collectedAt)
        assertEquals(id, retainedAudit.id)
        assertEquals(3, rows.size)
    }
}
