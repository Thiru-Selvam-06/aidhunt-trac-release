package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.PaymentEntity
import com.example.data.entity.PendingOperationEntity
import com.example.data.firebase.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectionPersistenceTest {
    @Test fun roomReloadAndQueuedSnapshotKeepSelectedCollectorSeparateFromRecorder() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "collection-reload-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var db = open()
        val payment = PaymentEntity(id = 9007199254740995L, workspaceId = "a", jobEntryId = 9007199254740997L,
            amount = 75.0, collectedByUid = "owner", collectedByName = "Owner", collectedByRole = "OWNER",
            collectorUid = "partner-b", collectorName = "Partner B", collectorRole = "PARTNER", isSynced = false)
        val payload = org.json.JSONObject(payment.toFirestoreMap()).toString()
        val command = PendingOperationEntity("original-operation", "a", "owner", "PAYMENT", payment.id, "UPSERT", 1, 1234, payload)
        try {
            val sameJobPayments = listOf(payment, payment.copy(id = payment.id + 1, amount = 20.0), payment.copy(id = payment.id + 2, amount = 30.0))
            val groupBefore = com.example.ui.screens.report.collectionJobHistory("a", sameJobPayments, emptyList()).single()
            db.paymentDao().insertPayments(sameJobPayments)
            db.pendingOperationDao().insert(command)
            db.close(); db = open()
            assertEquals(payment, db.paymentDao().getPaymentById("a", payment.id))
            val groupReloaded = com.example.ui.screens.report.collectionJobHistory("a", db.paymentDao().getPaymentsForCustomer("a", payment.customerId), emptyList()).single()
            assertEquals(groupBefore, groupReloaded)
            assertNull(db.paymentDao().getPaymentById("b", payment.id))
            val retained = db.pendingOperationDao().getOperation(command.operationId)!!
            assertEquals(command, retained)
            val json = org.json.JSONObject(retained.payloadJson)
            assertEquals("partner-b", json.getString("collectorUid"))
            assertEquals("owner", json.getString("collectedByUid"))
            assertEquals(9007199254740995L, json.getLong("id"))
            db.paymentDao().markPaymentsSynced("a", sameJobPayments.map { it.id })
            val syncedGroup = com.example.ui.screens.report.collectionJobHistory("a", db.paymentDao().getPaymentsForCustomer("a", payment.customerId), emptyList()).single()
            assertEquals(groupBefore.key, syncedGroup.key)
            assertEquals(3, syncedGroup.events.size)
            assertTrue(syncedGroup.events.all { it.payment.isSynced })
            assertEquals(payment.copy(isSynced = true), db.paymentDao().getPaymentById("a", payment.id))
            assertEquals(command, db.pendingOperationDao().getOperation(command.operationId))
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun balanceSheetProjectionSurvivesRoomReloadWithoutInventingDates() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "balance-sheet-reload-test.db"
        context.deleteDatabase(name)
        fun open() = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        var db = open()
        val recorded = java.time.Instant.parse("2026-11-13T01:00:00Z").toEpochMilli()
        val collection = PaymentEntity(id = 10, workspaceId = "a", customerId = 1, amount = 600.25,
            collectedAt = recorded - 86400000L, createdAt = recorded)
        val undated = collection.copy(id = 11, amount = 400.0, collectedAt = 0)
        val rejected = PendingOperationEntity("rejected", "a", "partner", "PAYMENT", 12, "UPSERT", 1,
            recorded, """{"amount":4500.0}""", state = "REJECTED")
        fun project(receipts: List<PaymentEntity>, action: PendingOperationEntity) =
            com.example.ui.screens.report.ReceiptPeriodReport.project("a", receipts, emptyList(), emptyList(),
                com.example.ui.screens.report.BalanceSheetTabMode.DAILY, actions = listOf(action))
        try {
            db.paymentDao().insertPayments(listOf(collection, undated))
            db.pendingOperationDao().insert(rejected)
            db.close(); db = open()
            val stored = db.paymentDao().getPaymentsForCustomer("a", 1)
            val restored = requireNotNull(db.pendingOperationDao().getOperation(rejected.operationId))
            val result = project(stored, restored)
            val before = project(listOf(collection, undated), rejected)
            assertEquals(before.pdfRows(false), result.pdfRows(false))
            assertEquals(1000.25, result.totalReceived, 0.0)
            assertEquals(400.0, result.explicitUndatedReceived, 0.0)
            assertEquals(recorded, stored.single { it.id == collection.id }.createdAt)
            assertEquals(0L, stored.single { it.id == undated.id }.collectedAt)
            assertEquals(rejected, restored)
            assertTrue(result.needsReview)
            assertFalse(result.hasPendingChanges)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun migrationPreservesLegacyRowsAndEveryOutboxByteWithoutInventingCollectorIdentity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "collection-v18-migration-test.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(18) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE payments (workspaceId TEXT NOT NULL, id INTEGER NOT NULL, collectedByUid TEXT NOT NULL, collectedAt INTEGER NOT NULL, PRIMARY KEY(workspaceId,id))")
                    db.execSQL("CREATE TABLE pending_operations (operationId TEXT NOT NULL PRIMARY KEY, payloadJson TEXT NOT NULL, actionTime INTEGER NOT NULL)")
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, old: Int, new: Int) = Unit
            }).build())
        try {
            val sql = helper.writableDatabase
            sql.execSQL("INSERT INTO payments VALUES ('a',9007199254740995,'owner',1234)")
            sql.execSQL("INSERT INTO pending_operations VALUES ('retained','original payload',1000)")
            AppDatabase.MIGRATION_18_19.migrate(sql)
            AppDatabase.MIGRATION_18_19.migrate(sql)
            sql.query("SELECT * FROM payments").use {
                assertTrue(it.moveToFirst()); assertEquals(9007199254740995L,it.getLong(it.getColumnIndexOrThrow("id")))
                assertEquals("owner",it.getString(it.getColumnIndexOrThrow("collectedByUid")))
                assertEquals(1234L,it.getLong(it.getColumnIndexOrThrow("collectedAt")))
                for (field in listOf("collectorUid","collectorName","collectorRole")) assertEquals("",it.getString(it.getColumnIndexOrThrow(field)))
                assertFalse(it.moveToNext())
            }
            sql.query("SELECT * FROM pending_operations").use {
                assertTrue(it.moveToFirst());assertEquals("retained",it.getString(0));assertEquals("original payload",it.getString(1));assertEquals(1000L,it.getLong(2))
            }
        } finally { helper.close();context.deleteDatabase(name) }
    }
}
