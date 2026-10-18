package com.example.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.AppSettingsDao
import com.example.data.dao.CustomerDao
import com.example.data.dao.ExpenseDao
import com.example.data.dao.JobEntryDao
import com.example.data.dao.PartnerDao
import com.example.data.dao.TractorDao
import com.example.data.dao.PaymentDao
import com.example.data.dao.WithdrawalDao
import com.example.data.dao.ChecklistItemDao
import com.example.data.dao.WorkTypeExtensionDao
import com.example.data.entity.AppSettingsEntity
import com.example.data.entity.CustomerEntity
import com.example.data.entity.ExpenseEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PartnerEntity
import com.example.data.entity.PaymentEntity
import com.example.data.entity.TractorEntity
import com.example.data.entity.WithdrawalEntity
import com.example.data.entity.ChecklistItemEntity
import com.example.data.entity.WorkTypeExtensionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        PartnerEntity::class,
        TractorEntity::class,
        CustomerEntity::class,
        JobEntryEntity::class,
        ExpenseEntity::class,
        WithdrawalEntity::class,
        AppSettingsEntity::class,
        PaymentEntity::class,
        ChecklistItemEntity::class,
        WorkTypeExtensionEntity::class,
        com.example.data.entity.PendingOperationEntity::class,
        com.example.data.entity.DeletedMemberEntity::class
    ],
    version = 19,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun partnerDao(): PartnerDao
    abstract fun tractorDao(): TractorDao
    abstract fun customerDao(): CustomerDao
    abstract fun jobEntryDao(): JobEntryDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun withdrawalDao(): WithdrawalDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun paymentDao(): PaymentDao
    abstract fun checklistItemDao(): ChecklistItemDao
    abstract fun workTypeExtensionDao(): WorkTypeExtensionDao

    abstract fun pendingOperationDao(): com.example.data.dao.PendingOperationDao

    abstract fun deletedMemberDao(): com.example.data.dao.DeletedMemberDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "partners", "workspaceId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "tractors", "workspaceId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "customers", "workspaceId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "job_entries", "workspaceId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "expenses", "workspaceId", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "withdrawals", "workspaceId", "TEXT NOT NULL DEFAULT ''")

                // Recreate app_settings table with workspaceId as primary key
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS app_settings_new (
                        workspaceId TEXT NOT NULL PRIMARY KEY,
                        businessName TEXT NOT NULL,
                        ownerName TEXT NOT NULL,
                        businessPhone TEXT NOT NULL,
                        businessAddress TEXT NOT NULL,
                        gstNumber TEXT NOT NULL,
                        defaultHourlyRate REAL NOT NULL,
                        currency TEXT NOT NULL,
                        language TEXT NOT NULL,
                        sharedAccountId TEXT NOT NULL,
                        isLoggedIn INTEGER NOT NULL,
                        activePartnerName TEXT NOT NULL,
                        activePartnerPhone TEXT NOT NULL,
                        profilePhotoUri TEXT NOT NULL,
                        lockedTractorLabel TEXT NOT NULL,
                        lastSyncTime INTEGER NOT NULL
                    )
                """.trimIndent())

                db.execSQL("""
                    INSERT OR REPLACE INTO app_settings_new (
                        workspaceId, businessName, ownerName, businessPhone, businessAddress, gstNumber,
                        defaultHourlyRate, currency, language, sharedAccountId, isLoggedIn,
                        activePartnerName, activePartnerPhone, profilePhotoUri, lockedTractorLabel, lastSyncTime
                    )
                    SELECT '', businessName, ownerName, businessPhone, businessAddress, gstNumber,
                           defaultHourlyRate, currency, language, sharedAccountId, isLoggedIn,
                           activePartnerName, activePartnerPhone, profilePhotoUri, lockedTractorLabel, lastSyncTime
                    FROM app_settings
                """.trimIndent())

                db.execSQL("DROP TABLE app_settings")
                db.execSQL("ALTER TABLE app_settings_new RENAME TO app_settings")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "job_entries", "createdByUid", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "job_entries", "createdByRole", "TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "job_entries", "editedByUid", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "job_entries", "editedByName", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "job_entries", "editedByRole", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "job_entries", "updatedAt", "INTEGER NOT NULL DEFAULT 0")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS payments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workspaceId TEXT NOT NULL DEFAULT '',
                        jobEntryId INTEGER NOT NULL DEFAULT 0,
                        customerId INTEGER NOT NULL DEFAULT 0,
                        customerName TEXT NOT NULL DEFAULT '',
                        tractorId INTEGER NOT NULL DEFAULT 0,
                        tractorLabel TEXT NOT NULL DEFAULT '',
                        amount REAL NOT NULL DEFAULT 0.0,
                        paymentMethod TEXT NOT NULL DEFAULT 'Cash',
                        notes TEXT NOT NULL DEFAULT '',
                        collectedByUid TEXT NOT NULL DEFAULT '',
                        collectedByName TEXT NOT NULL DEFAULT '',
                        collectedByRole TEXT NOT NULL DEFAULT '',
                        collectedAt INTEGER NOT NULL DEFAULT 0,
                        isSynced INTEGER NOT NULL DEFAULT 1
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS checklist_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workspaceId TEXT NOT NULL DEFAULT '',
                        text TEXT NOT NULL DEFAULT '',
                        isChecked INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS work_type_extensions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        workspaceId TEXT NOT NULL DEFAULT '',
                        name TEXT NOT NULL DEFAULT '',
                        createdAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "expenses", "paidBy", "TEXT NOT NULL DEFAULT 'I Paid'")
                addColumnIfMissing(db, "expenses", "paidByPartner", "TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Authorization identity fields for WithdrawalEntity
                // Safe defaults (empty string) preserve all existing withdrawal rows
                addColumnIfMissing(db, "withdrawals", "targetPartnerUid", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "withdrawals", "createdByUid", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "withdrawals", "createdByRole", "TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Authorization identity fields for ExpenseEntity
                addColumnIfMissing(db, "expenses", "paidByUid", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "expenses", "createdByUid", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "expenses", "createdByRole", "TEXT NOT NULL DEFAULT ''")
            }
        }

        private fun addColumnIfMissing(db: SupportSQLiteDatabase, table: String, name: String, definition: String) {
            val found = db.query("PRAGMA table_info(`$table`)").use { cursor ->
                var exists = false
                while (cursor.moveToNext()) if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == name) exists = true
                exists
            }
            if (!found) db.execSQL("ALTER TABLE `$table` ADD COLUMN `$name` $definition")
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "job_entries", "isHistorical", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "job_entries", "entryForUid", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "job_entries", "financialAttributionUid", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "expenses", "expenseForUid", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "expenses", "financialAttributionUid", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "tractors", "createdByUid", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "customers", "createdByUid", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "work_type_extensions", "createdByUid", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "payments", "status", "TEXT NOT NULL DEFAULT 'ACTIVE'")
                addColumnIfMissing(db, "payments", "reversedAt", "INTEGER NOT NULL DEFAULT 0")
                addColumnIfMissing(db, "payments", "reversedByUid", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "payments", "allocationsJson", "TEXT NOT NULL DEFAULT '[]'")
                // Preserve legacy rows. UID/historical backfills require verified identity, never name/date guesses.
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS pending_operations (operationId TEXT NOT NULL PRIMARY KEY, workspaceId TEXT NOT NULL, actorUid TEXT NOT NULL, entityType TEXT NOT NULL, entityId INTEGER NOT NULL, mutationType TEXT NOT NULL, sequence INTEGER NOT NULL, actionTime INTEGER NOT NULL, payloadJson TEXT NOT NULL, state TEXT NOT NULL, attempts INTEGER NOT NULL, failureReason TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_pending_operations_workspaceId_actorUid_sequence ON pending_operations (workspaceId, actorUid, sequence)")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Release14 lacks the local14 identity/history and durable queue additions.
                MIGRATION_12_13.migrate(db)
                MIGRATION_13_14.migrate(db)
                db.execSQL("CREATE TABLE IF NOT EXISTS deleted_members (workspaceId TEXT NOT NULL, uid TEXT NOT NULL, displayName TEXT NOT NULL, role TEXT NOT NULL, removedAt INTEGER NOT NULL, removedByUid TEXT NOT NULL, purgeState TEXT NOT NULL, backupPath TEXT NOT NULL, backupSha256 TEXT NOT NULL, PRIMARY KEY(workspaceId, uid))")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "partners", "firebaseUid", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "payments", "createdAt", "INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Rebuild keys without changing IDs or deleting records; duplicate IDs may coexist across workspaces. */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (table in listOf("partners", "tractors", "customers", "job_entries", "payments", "expenses", "withdrawals", "checklist_items", "work_type_extensions")) {
                    val columns = mutableListOf<String>()
                    val definitions = mutableListOf<String>()
                    db.query("PRAGMA table_info(`$table`)").use { cursor ->
                        while (cursor.moveToNext()) {
                            val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                            val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                            val notNull = cursor.getInt(cursor.getColumnIndexOrThrow("notnull")) != 0
                            val defaultIndex = cursor.getColumnIndexOrThrow("dflt_value")
                            val default = if (cursor.isNull(defaultIndex)) "" else " DEFAULT ${cursor.getString(defaultIndex)}"
                            columns.add("`$name`")
                            definitions.add("`$name` $type${if (notNull) " NOT NULL" else ""}$default")
                        }
                    }
                    require(columns.isNotEmpty()) { "Missing table during workspace-key migration: $table" }
                    db.execSQL("CREATE TABLE `${table}_scoped` (${definitions.joinToString(", ")}, PRIMARY KEY(`workspaceId`, `id`))")
                    val names = columns.joinToString(", ")
                    db.execSQL("INSERT INTO `${table}_scoped` ($names) SELECT $names FROM `$table`")
                    val before = db.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getLong(0) }
                    val after = db.query("SELECT COUNT(*) FROM `${table}_scoped`").use { it.moveToFirst(); it.getLong(0) }
                    check(before == after) { "Migration row count differs for $table" }
                    db.execSQL("DROP TABLE `$table`")
                    db.execSQL("ALTER TABLE `${table}_scoped` RENAME TO `$table`")
                }
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                addColumnIfMissing(db, "tractors", "createdByRole", "TEXT NOT NULL DEFAULT ''")
                addColumnIfMissing(db, "work_type_extensions", "createdByRole", "TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                for (column in listOf("collectorUid", "collectorName", "collectorRole"))
                    addColumnIfMissing(db, "payments", column, "TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    when {
                        com.example.BuildConfig.DEVICE_TEST -> "aidhunt_device_test_v18.db"
                        com.example.BuildConfig.LIVE_TEST -> "aidhunt_live_test_v18.db"
                        com.example.BuildConfig.PHYSICAL_TEST -> "aidhunt_physical_test_v18.db"
                        else -> "aidhunt_trac_v4.db"
                    }
                ).addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15, MIGRATION_15_16, MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
