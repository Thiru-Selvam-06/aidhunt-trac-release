package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.*
import com.example.data.firebase.FirestoreBackupCodec
import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomScopeTest {
    private fun entry(ws: String) = JobEntryEntity(id = 1, workspaceId = ws, customerId = 2, customerName = ws,
        operatorName = "Same Name", tractorLabel = "T", workType = "Work", startTimeMillis = 1, endTimeMillis = 2,
        durationMinutes = 1, totalAmount = 100.0, amountReceived = 0.0, pendingAmount = 100.0, addedByPartner = "Same Name",
        createdAt = 5000, createdByUid = "actor", entryForUid = "actor", isSynced = false)

    @Test fun ownerPendingDeletionsAreSeparateFromPartnerQueueAndNotUploads() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            val dao = db.pendingOperationDao()
            val originals = (1L..3L).map { n ->
                PendingOperationEntity("delete-$n", "business", "owner", "JOB", n, "DELETE", n, 1234L,
                    """{"workspaceId":"business","original":"unchanged"}""")
            }
            originals.forEach { dao.insert(it) }
            dao.insert(PendingOperationEntity("partner-done", "business", "partner", "JOB", 99, "UPSERT", 4, 1200,
                "retained", state = "ACKNOWLEDGED"))
            dao.insert(PendingOperationEntity("foreign", "other", "owner", "JOB", 99, "UPSERT", 5, 1200, "foreign"))
            val ownerRows = dao.observeActor("business", "owner").first()
            val partnerRows = dao.observeActor("business", "partner").first()
            assertEquals(3, ownerRows.count { it.state == "PENDING" })
            assertTrue(ownerRows.all { it.mutationType == "DELETE" })
            assertEquals(0, ownerRows.count { it.mutationType == "UPSERT" })
            assertTrue(partnerRows.isEmpty())
            assertEquals(3, dao.nextBatch("business", "owner").size)
            assertTrue(dao.nextBatch("business", "partner").isEmpty())
            assertEquals("3 Pending", com.example.data.firebase.cloudSyncLabel(
                com.example.data.firebase.CloudConnectionState.CONNECTED, true, false, 3))
            assertEquals("Synced", com.example.data.firebase.cloudSyncLabel(
                com.example.data.firebase.CloudConnectionState.CONNECTED, true, false, 0))
            originals.forEach { assertEquals(it, dao.getOperation(it.operationId)) }
        } finally { db.close() }
    }

    @Test fun collectionSnapshotPrunesOnlyConfirmedAbsentRowsInItsWorkspace() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            db.paymentDao().insertPayments(listOf(
                PaymentEntity(id = 99, workspaceId = "a", jobEntryId = 1, amount = 0.0, status = "ACTIVE", isSynced = true),
                PaymentEntity(id = 2, workspaceId = "a", status = "REVERSED", isSynced = true),
                PaymentEntity(id = 3, workspaceId = "a", isSynced = false),
                PaymentEntity(id = 99, workspaceId = "b", isSynced = true)
            ))
            db.paymentDao().deleteSyncedNotIn("a", listOf(2))
            assertNull(db.paymentDao().getPaymentById("a", 99))
            assertNotNull(db.paymentDao().getPaymentById("a", 2))
            assertNotNull(db.paymentDao().getPaymentById("a", 3))
            assertNotNull(db.paymentDao().getPaymentById("b", 99))
            db.paymentDao().deleteAllSyncedForWorkspace("a")
            assertNull(db.paymentDao().getPaymentById("a", 2))
            assertNotNull(db.paymentDao().getPaymentById("a", 3))
        } finally { db.close() }
    }

    @Test fun confirmedAbsentRejectedExpenseKeepsPayloadButStopsReviewWarning() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            val op = PendingOperationEntity("old-expense-delete", "a", "owner", "EXPENSE", 20, "DELETE", 1, 1000,
                "retained original payload", state = "REJECTED", failureReason = "Confirmed state restored; PERMISSION_DENIED")
            db.pendingOperationDao().insert(op)
            assertEquals(0, db.pendingOperationDao().resolveAbsentExpenseDelete(op.operationId, "b"))
            assertEquals(1, db.pendingOperationDao().resolveAbsentExpenseDelete(op.operationId, "a"))
            val retained = db.pendingOperationDao().getOperation(op.operationId)!!
            assertEquals("INVALIDATED", retained.state)
            assertEquals(op.payloadJson, retained.payloadJson)
            assertTrue(retained.failureReason.contains("confirmed absent"))
        } finally { db.close() }
    }

    @Test fun sameIdsRemainIndependentAcrossWorkspaces() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            db.jobEntryDao().insertJob(entry("a")); db.jobEntryDao().insertJob(entry("b"))
            assertEquals("a", db.jobEntryDao().getJobById("a", 1)?.customerName)
            assertEquals("b", db.jobEntryDao().getJobById("b", 1)?.customerName)
            db.jobEntryDao().markJobsSynced("a", listOf(1))
            assertTrue(db.jobEntryDao().getJobById("a", 1)!!.isSynced)
            assertFalse(db.jobEntryDao().getJobById("b", 1)!!.isSynced)
            db.jobEntryDao().deleteJob(entry("a"))
            assertNull(db.jobEntryDao().getJobById("a", 1))
            assertNotNull(db.jobEntryDao().getJobById("b", 1))
        } finally { db.close() }
    }

    @Test fun rejectedAndInvalidatedCommandsNeverBecomeAcknowledged() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            db.withTransaction {
                for (index in 1..25) db.pendingOperationDao().insert(PendingOperationEntity("op$index", "a", "actor", "JOB", index.toLong(),
                    "UPSERT", index.toLong(), 6000, "{}"))
                db.pendingOperationDao().insert(PendingOperationEntity("other", "b", "actor", "JOB", 1, "UPSERT", 26, 6000, "{}"))
            }
            assertEquals(20, db.pendingOperationDao().nextBatch("a", "actor").size)
            db.pendingOperationDao().failed("op1", "REJECTED", "Role changed")
            assertEquals("REJECTED", db.pendingOperationDao().observeActor("a", "actor").first().first().state)
            assertTrue(db.pendingOperationDao().nextBatch("a", "actor").none { it.operationId == "op1" })
            db.pendingOperationDao().invalidateActor("a", "actor")
            assertTrue(db.pendingOperationDao().nextBatch("a", "actor").isEmpty())
            assertEquals(1, db.pendingOperationDao().nextBatch("b", "actor").size)
        } finally { db.close() }
    }

    @Test fun backupRoundTripPreservesFirestoreValueTypes() {
        val data = mapOf("uid" to "unchanged", "timestamp" to Timestamp(123456789L, 987654321),
            "geo" to GeoPoint(12.34, 56.78), "blob" to Blob.fromBytes(byteArrayOf(0, 1, -1)),
            "nested" to mapOf("type" to "timestamp", "value" to 9007199254740993L), "list" to listOf(null, true, 123.5))
        val json = JSONObject(FirestoreBackupCodec.encode(data).toString())
        val restored = FirestoreBackupCodec.decode(json) { throw AssertionError("Unexpected reference") }
        assertEquals(data, restored)
    }

    @Test fun keyMigrationPreservesExistingRecordsAndCreationTimes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration17.db"
        context.deleteDatabase(name)
        context.getDatabasePath(name).parentFile?.mkdirs()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        db.jobEntryDao().insertJob(entry("a"))
        val sqlite = db.openHelper.writableDatabase
        // Construct the prior single-ID schema with the exact columns/defaults already verified by Room.
        sqlite.beginTransaction()
        try {
            for (table in listOf("partners", "tractors", "customers", "job_entries", "payments", "expenses", "withdrawals", "checklist_items", "work_type_extensions")) {
                val names = mutableListOf<String>(); val columns = mutableListOf<String>()
                sqlite.query("PRAGMA table_info(`$table`)").use { cursor -> while (cursor.moveToNext()) {
                    val column = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                    val nullClause = if (cursor.getInt(cursor.getColumnIndexOrThrow("notnull")) != 0) " NOT NULL" else ""
                    val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
                    val default = if (cursor.isNull(defaultIndex)) "" else " DEFAULT ${cursor.getString(defaultIndex)}"
                    names.add("`$column`"); columns.add("`$column` $type${if (column == "id") " PRIMARY KEY AUTOINCREMENT" else ""}$nullClause$default")
                } }
                sqlite.execSQL("CREATE TABLE `${table}_prior` (${columns.joinToString(",")})")
                sqlite.execSQL("INSERT INTO `${table}_prior` (${names.joinToString(",")}) SELECT ${names.joinToString(",")} FROM `$table`")
                sqlite.execSQL("DROP TABLE `$table`"); sqlite.execSQL("ALTER TABLE `${table}_prior` RENAME TO `$table`")
            }
            sqlite.version = 16
            sqlite.setTransactionSuccessful()
        } finally { sqlite.endTransaction(); db.close() }
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_16_17, AppDatabase.MIGRATION_17_18, AppDatabase.MIGRATION_18_19).build()
        try {
            assertEquals(5000L, reopened.jobEntryDao().getJobById("a", 1)?.createdAt)
            reopened.jobEntryDao().insertJob(entry("b"))
            assertNotNull(reopened.jobEntryDao().getJobById("a", 1))
            assertNotNull(reopened.jobEntryDao().getJobById("b", 1))
        } finally { reopened.close(); context.deleteDatabase(name) }
    }
    @Test fun deletionRetryInvalidatesOnlyItsOwnRejectedDelete() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.pendingOperationDao()
            dao.insert(PendingOperationEntity("old", "a", "actor", "JOB", 1, "DELETE", 1, 1, "{}", state="REJECTED"))
            dao.insert(PendingOperationEntity("other", "a", "other", "JOB", 1, "DELETE", 2, 1, "{}", state="REJECTED"))
            dao.insert(PendingOperationEntity("edit", "a", "actor", "JOB", 1, "UPSERT", 3, 1, "{}", state="REJECTED"))
            dao.invalidateRejectedEntryDelete("a", "actor", 1)
            val records=dao.observe("a").first().associateBy { it.operationId }
            assertEquals("INVALIDATED", records.getValue("old").state)
            assertEquals("REJECTED", records.getValue("other").state)
            assertEquals("REJECTED", records.getValue("edit").state)
        } finally { db.close() }
    }

    @Test fun sourceDeletionWaitsForIndividualReversalAcknowledgement() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao=db.pendingOperationDao()
            dao.insert(PendingOperationEntity("reverse", "a", "actor", "PAYMENT", 10, "UPSERT", 1, 1, "{}"))
            dao.insert(PendingOperationEntity("delete", "a", "actor", "JOB", 1, "DELETE", 2, 1, "{}"))
            assertEquals(1,dao.unresolvedPredecessors("a", "PAYMENT", 10, 2))
            dao.failed("reverse", "PENDING", "Connection interrupted")
            assertEquals(1,dao.unresolvedPredecessors("a", "PAYMENT", 10, 2))
            assertEquals(0,dao.unresolvedPredecessors("b", "PAYMENT", 10, 2))
            dao.acknowledge("reverse")
            assertEquals(0,dao.unresolvedPredecessors("a", "PAYMENT", 10, 2))
            assertEquals("delete",dao.nextBatch("a", "actor").single().operationId)
        } finally { db.close() }
    }

    @Test fun rejectedCollectionPredecessorIsDistinguishedFromTransientWait() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.pendingOperationDao()
            dao.insert(PendingOperationEntity("create", "a", "actor", "PAYMENT", 10, "UPSERT", 1, 100, "{}", state = "REJECTED"))
            dao.insert(PendingOperationEntity("reverse", "a", "actor", "PAYMENT", 10, "UPSERT", 2, 101, "{}"))
            assertEquals(1, dao.rejectedPredecessors("a", "PAYMENT", 10, 2))
            assertEquals(0, dao.rejectedPredecessors("b", "PAYMENT", 10, 2))
            dao.acknowledge("create")
            assertEquals(0, dao.rejectedPredecessors("a", "PAYMENT", 10, 2))
        } finally { db.close() }
    }

    @Test fun manualRetryRetainsOriginalCollectionIdentityAndTime() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val dao = db.pendingOperationDao()
            val original = PendingOperationEntity("stable-collection", "a", "partner", "PAYMENT", 10,
                "UPSERT", 1, 123456789L, "{\"operationProtocolVersion\":2}", state = "REJECTED",
                attempts = 1, failureReason = "PERMISSION_DENIED")
            dao.insert(original)
            assertEquals(1, dao.requeueRejected("stable-collection"))
            val retried = dao.getOperation("stable-collection")!!
            assertEquals("PENDING", retried.state)
            assertEquals(original.operationId, retried.operationId)
            assertEquals(original.actorUid, retried.actorUid)
            assertEquals(original.workspaceId, retried.workspaceId)
            assertEquals(original.actionTime, retried.actionTime)
            assertEquals(original.payloadJson, retried.payloadJson)
            assertTrue(retried.failureReason.contains("PERMISSION_DENIED"))
            assertEquals(0, dao.requeueRejected("stable-collection"))
        } finally { db.close() }
    }

}
