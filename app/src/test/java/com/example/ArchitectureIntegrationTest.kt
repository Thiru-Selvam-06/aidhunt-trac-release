package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.PendingOperationEntity
import com.example.data.sync.OperationDependencies
import com.example.data.auth.RoleUtils
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ArchitectureIntegrationTest {
    private fun row(db: SupportSQLiteDatabase, table: String, overrides: Map<String, Any>) {
        val cols = mutableListOf<String>(); val values = mutableListOf<Any>()
        db.query("PRAGMA table_info(`$table`)").use { cursor -> while (cursor.moveToNext()) {
            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
            val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
            cols.add("`$name`"); values.add(overrides[name] ?: when(type) { "TEXT" -> ""; "REAL" -> 0.0; else -> 0L })
        } }
        db.execSQL("INSERT INTO `$table` (${cols.joinToString()}) VALUES (${cols.joinToString { "?" }})", values.toTypedArray())
    }
    private suspend fun upgrade(version: Int, fixture: String) {
        val context = ApplicationProvider.getApplicationContext<Context>(); val name = "contract-$version.db"
        context.deleteDatabase(name)
        val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    javaClass.classLoader!!.getResourceAsStream(fixture)!!.bufferedReader().useLines { lines ->
                        lines.filter { it.startsWith("CREATE ") }.forEach { db.execSQL(it.removeSuffix(";")) }
                    }
                    row(db, "job_entries", mapOf("id" to 1L, "workspaceId" to "a", "createdAt" to 5000L, "createdByUid" to "actor", "totalAmount" to 50000.0, "pendingAmount" to 30000.0, "amountReceived" to 20000.0))
                    row(db, "tractors", mapOf("id" to 1L, "workspaceId" to "a", "createdByUid" to "actor", "createdByRole" to "PARTNER"))
                    if (version == 17) row(db, "pending_operations", mapOf("operationId" to "saved-op", "workspaceId" to "a", "actorUid" to "actor", "sequence" to 1L, "entityId" to 1L, "state" to "PENDING", "payloadJson" to "{}"))
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Fixture never upgrades")
            }).build())
        helper.writableDatabase; helper.close()
        val db = Room.databaseBuilder(context, AppDatabase::class.java, name).addMigrations(AppDatabase.MIGRATION_12_13, AppDatabase.MIGRATION_13_14, AppDatabase.MIGRATION_14_15, AppDatabase.MIGRATION_15_16, AppDatabase.MIGRATION_16_17, AppDatabase.MIGRATION_17_18, AppDatabase.MIGRATION_18_19).build()
        try {
            val entry = db.jobEntryDao().getJobById("a", 1)!!
            assertEquals(5000L, entry.createdAt); assertEquals(50000.0, entry.totalAmount, 0.0); assertEquals(30000.0, entry.pendingAmount, 0.0)
            assertEquals("actor", entry.createdByUid)
            if (version == 14) assertEquals("PARTNER", db.tractorDao().getTractorsForWorkspaceOnce("a").single().createdByRole)
            else if (version == 17) assertEquals("saved-op", db.pendingOperationDao().nextBatch("a", "actor").single().operationId)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun actualRelease14PreservesIdentityMoneyAndRoleSnapshot() = runBlocking { upgrade(14, "release-v14-schema.sql") }
    @Test fun release13SameVersionLineageUpgrades() = runBlocking { upgrade(13, "release-v14-schema.sql") }
    @Test fun knownLocal17PreservesPendingQueue() = runBlocking { upgrade(17, "local-v17-schema.sql") }
    @Test fun rejectedDependencyDoesNotHideIndependentPendingCommands() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            val dao=db.pendingOperationDao()
            dao.insert(PendingOperationEntity("failed","a","actor","JOB",1,"UPSERT",1,5000,"{}",state="REJECTED"))
            dao.insert(PendingOperationEntity("dependent","a","actor","PAYMENT",3,"UPSERT",2,5000,"{\"jobEntryId\":1,\"customerId\":2}"))
            dao.insert(PendingOperationEntity("independent","a","actor","JOB",4,"UPSERT",3,5000,"{\"customerId\":5}"))
            assertEquals(listOf("dependent","independent"),dao.pendingAfter("a","actor",0).map { it.operationId })
            assertEquals(1,dao.unresolvedPredecessors("a","JOB",1,2)); assertEquals(0,dao.unresolvedPredecessors("a","JOB",4,3))
            assertTrue(OperationDependencies.references(dao.nextBatch("a","actor").first()).contains("JOB" to 1L))
            assertTrue(dao.pendingAfter("b","actor",0).isEmpty());assertTrue(dao.pendingAfter("a","other",0).isEmpty())
        } finally { db.close() }
    }
    @Test fun membershipLabelsUseUidNotMatchingName() {
        assertEquals("",RoleUtils.normalizeRole("Co-Owner"));assertEquals("Owner",RoleUtils.normalizeRole("OWNER"))
        assertEquals("Thiru",RoleUtils.formatRoleAndName("", "Thiru", false))
    }
    @Test fun largeQueueDrainsBoundedWithoutCrossingActorOrWorkspace() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            val dao = db.pendingOperationDao()
            db.withTransaction {
                for (i in 1..600) dao.insert(PendingOperationEntity("load-$i", "a", "actor", "JOB", i.toLong(), "UPSERT", i.toLong(), 5000, "{}"))
                dao.insert(PendingOperationEntity("foreign-workspace", "b", "actor", "JOB", 1, "UPSERT", 601, 5000, "{}"))
                dao.insert(PendingOperationEntity("foreign-account", "a", "other", "JOB", 1, "UPSERT", 602, 5000, "{}"))
            }
            val start = System.nanoTime()
            var acknowledged = 0
            var priorSequence = 0L
            while (true) {
                val batch = dao.nextBatch("a", "actor")
                if (batch.isEmpty()) break
                assertTrue(batch.size <= 20)
                for (command in batch) {
                    assertEquals("a", command.workspaceId); assertEquals("actor", command.actorUid)
                    assertTrue(command.sequence > priorSequence); priorSequence = command.sequence
                    dao.acknowledge(command.operationId); acknowledged++
                }
            }
            assertEquals(600, acknowledged)
            assertEquals(1, dao.nextBatch("b", "actor").size)
            assertEquals(1, dao.nextBatch("a", "other").size)
            println("Room fixture: 600 ordered commands acknowledged in ${(System.nanoTime()-start)/1_000_000}ms; maximum batch 20. This is a DAO fixture, not a cloud-sync or device-UI benchmark.")
        } finally { db.close() }
    }

}
