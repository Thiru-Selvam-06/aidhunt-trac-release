package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner

/** Actual scoped DAO pruning against real SQLite, including duplicate composite keys and rollback. */
@RunWith(ParameterizedRobolectricTestRunner::class)
class WorkspaceDatabaseContractTest(private val table: String) {
    companion object {
        @JvmStatic @ParameterizedRobolectricTestRunner.Parameters(name="{0}")
        fun tables() = listOf("partners","tractors","customers","job_entries","expenses","withdrawals",
            "payments","checklist_items","work_type_extensions").map { arrayOf(it) }
    }
    private val id=9007199254740995L
    private fun seed(db: AppDatabase, ws: String, created: Long=1) {
        val columns=mutableListOf<String>()
        val values=mutableListOf<Any>()
        db.openHelper.writableDatabase.query("PRAGMA table_info(`$table`)").use { cursor ->
            while(cursor.moveToNext()) {
                val name=cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val type=cursor.getString(cursor.getColumnIndexOrThrow("type"))
                columns.add("`$name`")
                values.add(when(name) {
                    "workspaceId" -> ws
                    "id" -> id
                    "createdAt" -> created
                    else -> when(type) { "INTEGER" -> 1L; "REAL" -> 1.0; "BLOB" -> byteArrayOf(1); else -> "Synthetic" }
                })
            }
        }
        db.openHelper.writableDatabase.execSQL(
            "INSERT OR IGNORE INTO `$table` (${columns.joinToString()}) VALUES (${columns.joinToString { "?" }})",
            values.toTypedArray())
    }
    private fun count(db:AppDatabase,ws:String):Int =
        db.openHelper.writableDatabase.query("SELECT COUNT(*) FROM `$table` WHERE workspaceId=? AND id=?",arrayOf<Any>(ws,id)).use {
            it.moveToFirst();it.getInt(0)
        }
    private suspend fun prune(db:AppDatabase,ws:String) {
        when(table) {
            "partners" -> db.partnerDao().deleteNotIn(ws,listOf(44))
            "tractors" -> db.tractorDao().deleteNotIn(ws,listOf(44))
            "customers" -> db.customerDao().deleteSyncedNotIn(ws,listOf(44))
            "job_entries" -> db.jobEntryDao().deleteSyncedNotIn(ws,listOf(44))
            "expenses" -> db.expenseDao().deleteSyncedNotIn(ws,listOf(44))
            "withdrawals" -> db.withdrawalDao().deleteSyncedNotIn(ws,listOf(44))
            "payments" -> db.paymentDao().deleteSyncedNotIn(ws,listOf(44))
            "checklist_items" -> db.checklistItemDao().deleteChecklistItemById(ws,id)
            "work_type_extensions" -> db.workTypeExtensionDao().deleteNotIn(ws,listOf(44))
            else -> error("Unregistered database contract")
        }
    }
    @Test fun duplicateIdentityRollbackAndScopedPruningPreserveForeignRecords() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build()
        try {
            seed(db,"a");seed(db,"b");seed(db,"a",created=999)
            assertEquals(1,count(db,"a"));assertEquals(1,count(db,"b"))
            db.openHelper.writableDatabase.query("SELECT createdAt FROM `$table` WHERE workspaceId='a'").use {
                it.moveToFirst();assertEquals(1L,it.getLong(0))
            }
            prune(db,"unknown")
            assertEquals(1,count(db,"a"));assertEquals(1,count(db,"b"))
            try { db.withTransaction { prune(db,"a");error("Synthetic interruption") } }
            catch(_:IllegalStateException) {}
            assertEquals(1,count(db,"a"));assertEquals(1,count(db,"b"))
            repeat(2) { prune(db,"a") }
            assertEquals(0,count(db,"a"));assertEquals(1,count(db,"b"))
        } finally { db.close() }
    }
}

