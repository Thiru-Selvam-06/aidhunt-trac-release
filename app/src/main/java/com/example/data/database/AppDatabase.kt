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
        WorkTypeExtensionEntity::class
    ],
    version = 14,
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE partners ADD COLUMN workspaceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tractors ADD COLUMN workspaceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE customers ADD COLUMN workspaceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE job_entries ADD COLUMN workspaceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN workspaceId TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE withdrawals ADD COLUMN workspaceId TEXT NOT NULL DEFAULT ''")

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
                db.execSQL("ALTER TABLE job_entries ADD COLUMN createdByUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE job_entries ADD COLUMN createdByRole TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE job_entries ADD COLUMN editedByUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE job_entries ADD COLUMN editedByName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE job_entries ADD COLUMN editedByRole TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE job_entries ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")

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
                db.execSQL("ALTER TABLE expenses ADD COLUMN paidBy TEXT NOT NULL DEFAULT 'I Paid'")
                db.execSQL("ALTER TABLE expenses ADD COLUMN paidByPartner TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Authorization identity fields for WithdrawalEntity
                // Safe defaults (empty string) preserve all existing withdrawal rows
                db.execSQL("ALTER TABLE withdrawals ADD COLUMN targetPartnerUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE withdrawals ADD COLUMN createdByUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE withdrawals ADD COLUMN createdByRole TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Authorization identity fields for ExpenseEntity
                db.execSQL("ALTER TABLE expenses ADD COLUMN paidByUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN createdByUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE expenses ADD COLUMN createdByRole TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Fix missing columns in payments table
                db.execSQL("ALTER TABLE payments ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")

                // Fix missing columns in tractors table
                db.execSQL("ALTER TABLE tractors ADD COLUMN createdByUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE tractors ADD COLUMN createdByRole TEXT NOT NULL DEFAULT ''")

                // Fix missing columns in work_type_extensions table
                db.execSQL("ALTER TABLE work_type_extensions ADD COLUMN createdByUid TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE work_type_extensions ADD COLUMN createdByRole TEXT NOT NULL DEFAULT ''")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema alignment for version 14
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "aidhunt_trac_v4.db"
                ).addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
                .fallbackToDestructiveMigration(true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
