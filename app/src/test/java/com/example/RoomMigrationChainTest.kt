package com.example

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomMigrationChainTest {

    private fun getTableColumns(db: SupportSQLiteDatabase, tableName: String): Set<String> {
        val columns = mutableSetOf<String>()
        db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            while (cursor.moveToNext()) {
                columns.add(cursor.getString(nameIndex))
            }
        }
        return columns
    }

    private fun createHelper(context: Context, dbName: String, version: Int, onCreateDb: (SupportSQLiteDatabase) -> Unit): SupportSQLiteOpenHelper {
        context.deleteDatabase(dbName)
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : SupportSQLiteOpenHelper.Callback(version) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    onCreateDb(db)
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config)
    }

    @Test
    fun testMigration4To5_AddsWorkspaceIdAndRecreatesAppSettings() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_mig_4_5.db"
        val helper = createHelper(context, dbName, 4) { db ->
            db.execSQL("CREATE TABLE partners (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL, sharePercentage REAL NOT NULL, isActive INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE tractors (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, label TEXT NOT NULL, chassisNo TEXT NOT NULL, modelYear TEXT NOT NULL, operatorName TEXT NOT NULL, isActive INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL, isActive INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE job_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, customerId INTEGER NOT NULL, tractorId INTEGER NOT NULL, workType TEXT NOT NULL, ratePerHour REAL NOT NULL, acresWorked REAL NOT NULL, hoursWorked REAL NOT NULL, totalAmount REAL NOT NULL, paidAmount REAL NOT NULL, balanceAmount REAL NOT NULL, date INTEGER NOT NULL, notes TEXT NOT NULL, isSynced INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, category TEXT NOT NULL, amount REAL NOT NULL, description TEXT NOT NULL, date INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE withdrawals (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, partnerName TEXT NOT NULL, amount REAL NOT NULL, date INTEGER NOT NULL, notes TEXT NOT NULL, isSynced INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE app_settings (id INTEGER PRIMARY KEY NOT NULL, businessName TEXT NOT NULL, ownerName TEXT NOT NULL, businessPhone TEXT NOT NULL, businessAddress TEXT NOT NULL, gstNumber TEXT NOT NULL, defaultHourlyRate REAL NOT NULL, currency TEXT NOT NULL, language TEXT NOT NULL, sharedAccountId TEXT NOT NULL, isLoggedIn INTEGER NOT NULL, activePartnerName TEXT NOT NULL, activePartnerPhone TEXT NOT NULL, profilePhotoUri TEXT NOT NULL, lockedTractorLabel TEXT NOT NULL, lastSyncTime INTEGER NOT NULL)")

            db.execSQL("INSERT INTO tractors (id, label, chassisNo, modelYear, operatorName, isActive, createdAt) VALUES (1, 'Swaraj 744', 'SW-123', '2021', 'Ramesh', 1, 5000)")
            db.execSQL("INSERT INTO app_settings (id, businessName, ownerName, businessPhone, businessAddress, gstNumber, defaultHourlyRate, currency, language, sharedAccountId, isLoggedIn, activePartnerName, activePartnerPhone, profilePhotoUri, lockedTractorLabel, lastSyncTime) VALUES (1, 'Trac Farm', 'Owner A', '9443107361', 'Salem', 'GST123', 800.0, 'INR', 'en', '', 1, '', '', '', '', 1000)")
        }
        val db = helper.writableDatabase
        try {
            AppDatabase.MIGRATION_4_5.migrate(db)

            assertTrue(getTableColumns(db, "partners").contains("workspaceId"))
            assertTrue(getTableColumns(db, "tractors").contains("workspaceId"))
            assertTrue(getTableColumns(db, "customers").contains("workspaceId"))
            assertTrue(getTableColumns(db, "job_entries").contains("workspaceId"))
            assertTrue(getTableColumns(db, "expenses").contains("workspaceId"))
            assertTrue(getTableColumns(db, "withdrawals").contains("workspaceId"))

            db.query("SELECT id, label, workspaceId FROM tractors WHERE id = 1").use { c ->
                assertTrue(c.moveToNext())
                assertEquals(1L, c.getLong(0))
                assertEquals("Swaraj 744", c.getString(1))
                assertEquals("", c.getString(2))
            }
            db.query("SELECT workspaceId, businessName FROM app_settings").use { c ->
                assertTrue(c.moveToNext())
                assertEquals("", c.getString(0))
                assertEquals("Trac Farm", c.getString(1))
            }
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun testMigration5To6_AddsCreatedByFieldsToJobEntries() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_mig_5_6.db"
        val helper = createHelper(context, dbName, 5) { db ->
            db.execSQL("CREATE TABLE job_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, workspaceId TEXT NOT NULL DEFAULT '', customerId INTEGER NOT NULL, tractorId INTEGER NOT NULL, workType TEXT NOT NULL, ratePerHour REAL NOT NULL, acresWorked REAL NOT NULL, hoursWorked REAL NOT NULL, totalAmount REAL NOT NULL, paidAmount REAL NOT NULL, balanceAmount REAL NOT NULL, date INTEGER NOT NULL, notes TEXT NOT NULL, isSynced INTEGER NOT NULL)")
        }
        val db = helper.writableDatabase
        try {
            AppDatabase.MIGRATION_5_6.migrate(db)
            val columns = getTableColumns(db, "job_entries")
            assertTrue(columns.contains("createdByUid"))
            assertTrue(columns.contains("createdByRole"))
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun testMigration6To7_AddsEditedFieldsAndPaymentsTable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_mig_6_7.db"
        val helper = createHelper(context, dbName, 6) { db ->
            db.execSQL("CREATE TABLE job_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, workspaceId TEXT NOT NULL DEFAULT '', customerId INTEGER NOT NULL, tractorId INTEGER NOT NULL, workType TEXT NOT NULL, ratePerHour REAL NOT NULL, acresWorked REAL NOT NULL, hoursWorked REAL NOT NULL, totalAmount REAL NOT NULL, paidAmount REAL NOT NULL, balanceAmount REAL NOT NULL, date INTEGER NOT NULL, notes TEXT NOT NULL, isSynced INTEGER NOT NULL, createdByUid TEXT NOT NULL DEFAULT '', createdByRole TEXT NOT NULL DEFAULT '')")
        }
        val db = helper.writableDatabase
        try {
            AppDatabase.MIGRATION_6_7.migrate(db)
            val jobCols = getTableColumns(db, "job_entries")
            assertTrue(jobCols.contains("editedByUid"))
            assertTrue(jobCols.contains("editedByName"))
            assertTrue(jobCols.contains("editedByRole"))
            assertTrue(jobCols.contains("updatedAt"))

            val paymentCols = getTableColumns(db, "payments")
            assertTrue(paymentCols.contains("id"))
            assertTrue(paymentCols.contains("amount"))
            assertTrue(paymentCols.contains("collectedByUid"))
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun testMigration7To8_8To9_ChecklistsAndWorkTypeExtensions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_mig_7_9.db"
        val helper = createHelper(context, dbName, 7) { }
        val db = helper.writableDatabase
        try {
            AppDatabase.MIGRATION_7_8.migrate(db)
            assertTrue(getTableColumns(db, "checklist_items").contains("isChecked"))

            AppDatabase.MIGRATION_8_9.migrate(db)
            assertTrue(getTableColumns(db, "work_type_extensions").contains("name"))
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun testMigration9To10_10To11_11To12_ExpenseAndWithdrawalIdentities() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_mig_9_12.db"
        val helper = createHelper(context, dbName, 9) { db ->
            db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, workspaceId TEXT NOT NULL DEFAULT '', category TEXT NOT NULL, amount REAL NOT NULL, description TEXT NOT NULL, date INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE withdrawals (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, workspaceId TEXT NOT NULL DEFAULT '', partnerName TEXT NOT NULL, amount REAL NOT NULL, date INTEGER NOT NULL, notes TEXT NOT NULL, isSynced INTEGER NOT NULL)")
        }
        val db = helper.writableDatabase
        try {
            AppDatabase.MIGRATION_9_10.migrate(db)
            val expCols910 = getTableColumns(db, "expenses")
            assertTrue(expCols910.contains("paidBy"))
            assertTrue(expCols910.contains("paidByPartner"))

            AppDatabase.MIGRATION_10_11.migrate(db)
            val wCols = getTableColumns(db, "withdrawals")
            assertTrue(wCols.contains("targetPartnerUid"))
            assertTrue(wCols.contains("createdByUid"))
            assertTrue(wCols.contains("createdByRole"))

            AppDatabase.MIGRATION_11_12.migrate(db)
            val expCols1112 = getTableColumns(db, "expenses")
            assertTrue(expCols1112.contains("paidByUid"))
            assertTrue(expCols1112.contains("createdByUid"))
            assertTrue(expCols1112.contains("createdByRole"))
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun testMigration12To13_AddsHistoricalAndFinancialAttributionFields() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_mig_12_13.db"
        val helper = createHelper(context, dbName, 12) { db ->
            db.execSQL("CREATE TABLE job_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            db.execSQL("CREATE TABLE tractors (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            db.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            db.execSQL("CREATE TABLE work_type_extensions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            db.execSQL("CREATE TABLE payments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
        }
        val db = helper.writableDatabase
        try {
            AppDatabase.MIGRATION_12_13.migrate(db)
            val jobCols = getTableColumns(db, "job_entries")
            assertTrue(jobCols.contains("isHistorical"))
            assertTrue(jobCols.contains("entryForUid"))
            assertTrue(jobCols.contains("financialAttributionUid"))

            val expCols = getTableColumns(db, "expenses")
            assertTrue(expCols.contains("expenseForUid"))
            assertTrue(expCols.contains("financialAttributionUid"))

            val payCols = getTableColumns(db, "payments")
            assertTrue(payCols.contains("status"))
            assertTrue(payCols.contains("reversedAt"))
            assertTrue(payCols.contains("allocationsJson"))
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun testMigration13To14_14To15_15To16_QueueAndDeletedMembers() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_mig_13_16.db"
        val helper = createHelper(context, dbName, 13) { db ->
            db.execSQL("CREATE TABLE partners (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            db.execSQL("CREATE TABLE payments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            db.execSQL("CREATE TABLE job_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            db.execSQL("CREATE TABLE tractors (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            db.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
            db.execSQL("CREATE TABLE work_type_extensions (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL)")
        }
        val db = helper.writableDatabase
        try {
            AppDatabase.MIGRATION_13_14.migrate(db)
            val queueCols = getTableColumns(db, "pending_operations")
            assertTrue(queueCols.contains("operationId"))
            assertTrue(queueCols.contains("actionTime"))
            assertTrue(queueCols.contains("payloadJson"))

            AppDatabase.MIGRATION_14_15.migrate(db)
            val memberCols = getTableColumns(db, "deleted_members")
            assertTrue(memberCols.contains("workspaceId"))
            assertTrue(memberCols.contains("uid"))
            assertTrue(memberCols.contains("purgeState"))

            AppDatabase.MIGRATION_15_16.migrate(db)
            assertTrue(getTableColumns(db, "partners").contains("firebaseUid"))
            assertTrue(getTableColumns(db, "payments").contains("createdAt"))
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun testMigration16To17_CompositePrimaryKeyTransformationPreservesDuplicateIdsAcrossWorkspaces() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_mig_16_17.db"
        val helper = createHelper(context, dbName, 16) { db ->
            for (table in listOf("partners", "tractors", "customers", "job_entries", "payments", "expenses", "withdrawals", "checklist_items", "work_type_extensions")) {
                db.execSQL("CREATE TABLE `$table` (`id` INTEGER NOT NULL, `workspaceId` TEXT NOT NULL, `label` TEXT NOT NULL DEFAULT '', PRIMARY KEY(`id`))")
            }
            // Insert two tractors with ID 100 in DIFFERENT workspaces before migration
            // In v16 single-primary-key, this was forbidden. In v17 composite key (workspaceId, id), it must be supported!
            db.execSQL("INSERT INTO `tractors` (`id`, `workspaceId`, `label`) VALUES (100, 'ws_prod', 'Prod Tractor')")
        }
        val db = helper.writableDatabase
        try {
            AppDatabase.MIGRATION_16_17.migrate(db)

            // Now in v17, insert the identical ID 100 in staging workspace:
            db.execSQL("INSERT INTO `tractors` (`id`, `workspaceId`, `label`) VALUES (100, 'ws_staging', 'Staging Tractor')")

            db.query("SELECT id, workspaceId, label FROM tractors WHERE id = 100 ORDER BY workspaceId").use { c ->
                assertTrue(c.moveToNext())
                assertEquals(100L, c.getLong(0))
                assertEquals("ws_prod", c.getString(1))
                assertEquals("Prod Tractor", c.getString(2))

                assertTrue(c.moveToNext())
                assertEquals(100L, c.getLong(0))
                assertEquals("ws_staging", c.getString(1))
                assertEquals("Staging Tractor", c.getString(2))

                assertFalse(c.moveToNext())
            }
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun testFullSequentialMigrationChainFromV4ThroughV19_AllDataPreserved() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_full_chain_v4_to_v18.db"
        val helper = createHelper(context, dbName, 4) { db ->
            db.execSQL("CREATE TABLE partners (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL, sharePercentage REAL NOT NULL, isActive INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE tractors (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, label TEXT NOT NULL, chassisNo TEXT NOT NULL, modelYear TEXT NOT NULL, operatorName TEXT NOT NULL, isActive INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE customers (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, phone TEXT NOT NULL, address TEXT NOT NULL, isActive INTEGER NOT NULL, createdAt INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE job_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, customerId INTEGER NOT NULL, tractorId INTEGER NOT NULL, workType TEXT NOT NULL, ratePerHour REAL NOT NULL, acresWorked REAL NOT NULL, hoursWorked REAL NOT NULL, totalAmount REAL NOT NULL, paidAmount REAL NOT NULL, balanceAmount REAL NOT NULL, date INTEGER NOT NULL, notes TEXT NOT NULL, isSynced INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, category TEXT NOT NULL, amount REAL NOT NULL, description TEXT NOT NULL, date INTEGER NOT NULL, isSynced INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE withdrawals (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, partnerName TEXT NOT NULL, amount REAL NOT NULL, date INTEGER NOT NULL, notes TEXT NOT NULL, isSynced INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE app_settings (id INTEGER PRIMARY KEY NOT NULL, businessName TEXT NOT NULL, ownerName TEXT NOT NULL, businessPhone TEXT NOT NULL, businessAddress TEXT NOT NULL, gstNumber TEXT NOT NULL, defaultHourlyRate REAL NOT NULL, currency TEXT NOT NULL, language TEXT NOT NULL, sharedAccountId TEXT NOT NULL, isLoggedIn INTEGER NOT NULL, activePartnerName TEXT NOT NULL, activePartnerPhone TEXT NOT NULL, profilePhotoUri TEXT NOT NULL, lockedTractorLabel TEXT NOT NULL, lastSyncTime INTEGER NOT NULL)")

            db.execSQL("INSERT INTO tractors (id, label, chassisNo, modelYear, operatorName, isActive, createdAt) VALUES (42, 'Mahindra DI 745', 'MH-999', '2020', 'Murugan', 1, 10000)")
            db.execSQL("INSERT INTO customers (id, name, phone, address, isActive, createdAt) VALUES (88, 'Karthik', '9876543210', 'Village South', 1, 12000)")
            db.execSQL("INSERT INTO expenses (id, category, amount, description, date, isSynced) VALUES (7, 'Diesel', 2500.0, '50L Diesel', 15000, 1)")
        }
        val db = helper.writableDatabase
        try {
            // Apply all 14 migrations sequentially from v4 to v18
            AppDatabase.MIGRATION_4_5.migrate(db)
            AppDatabase.MIGRATION_5_6.migrate(db)
            AppDatabase.MIGRATION_6_7.migrate(db)
            AppDatabase.MIGRATION_7_8.migrate(db)
            AppDatabase.MIGRATION_8_9.migrate(db)
            AppDatabase.MIGRATION_9_10.migrate(db)
            AppDatabase.MIGRATION_10_11.migrate(db)
            AppDatabase.MIGRATION_11_12.migrate(db)
            AppDatabase.MIGRATION_12_13.migrate(db)
            AppDatabase.MIGRATION_13_14.migrate(db)
            AppDatabase.MIGRATION_14_15.migrate(db)
            AppDatabase.MIGRATION_15_16.migrate(db)
            AppDatabase.MIGRATION_16_17.migrate(db)
            AppDatabase.MIGRATION_17_18.migrate(db)
            AppDatabase.MIGRATION_18_19.migrate(db)

            // Verify pre-existing v4 data is completely preserved
            db.query("SELECT id, label, createdByRole, workspaceId FROM tractors WHERE id = 42").use { c ->
                assertTrue("Tractor 42 must survive migration", c.moveToNext())
                assertEquals(42L, c.getLong(0))
                assertEquals("Mahindra DI 745", c.getString(1))
                assertEquals("", c.getString(2)) // Default from 17->18
                assertEquals("", c.getString(3)) // Default from 4->5
            }

            db.query("SELECT id, name, createdByUid, workspaceId FROM customers WHERE id = 88").use { c ->
                assertTrue("Customer 88 must survive migration", c.moveToNext())
                assertEquals(88L, c.getLong(0))
                assertEquals("Karthik", c.getString(1))
                assertEquals("", c.getString(2)) // Default from 12->13
                assertEquals("", c.getString(3)) // Default from 4->5
            }

            db.query("SELECT id, category, amount, paidBy, paidByUid, expenseForUid FROM expenses WHERE id = 7").use { c ->
                assertTrue("Expense 7 must survive migration", c.moveToNext())
                assertEquals(7L, c.getLong(0))
                assertEquals("Diesel", c.getString(1))
                assertEquals(2500.0, c.getDouble(2), 0.001)
                assertEquals("I Paid", c.getString(3)) // Default from 9->10
                assertEquals("", c.getString(4)) // Default from 11->12
                assertEquals("", c.getString(5)) // Default from 12->13
            }

            // Verify all new tables are queryable
            db.query("SELECT count(*) FROM pending_operations").use { c ->
                assertTrue(c.moveToNext())
                assertEquals(0L, c.getLong(0))
            }
            db.query("SELECT count(*) FROM deleted_members").use { c ->
                assertTrue(c.moveToNext())
                assertEquals(0L, c.getLong(0))
            }
            db.query("SELECT count(*) FROM payments").use { c ->
                assertTrue(c.moveToNext())
                assertEquals(0L, c.getLong(0))
            }
            val paymentCols = getTableColumns(db, "payments")
            assertTrue("payments must have collectorUid after v19", paymentCols.contains("collectorUid"))
            assertTrue("payments must have collectorName after v19", paymentCols.contains("collectorName"))
            assertTrue("payments must have collectorRole after v19", paymentCols.contains("collectorRole"))
            db.query("SELECT count(*) FROM checklist_items").use { c ->
                assertTrue(c.moveToNext())
                assertEquals(0L, c.getLong(0))
            }
            db.query("SELECT count(*) FROM work_type_extensions").use { c ->
                assertTrue(c.moveToNext())
                assertEquals(0L, c.getLong(0))
            }
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }
    @Test
    fun testMigration18To19_AddsCollectorFieldsToPayments() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_mig_18_19.db"
        context.deleteDatabase(dbName)
        val helper = createHelper(context, dbName, 18) { db ->
            db.execSQL("CREATE TABLE payments (id INTEGER NOT NULL, workspaceId TEXT NOT NULL, amount REAL NOT NULL, PRIMARY KEY(workspaceId, id))")
            db.execSQL("INSERT INTO payments (id, workspaceId, amount) VALUES (99, 'ws_test', 500.0)")
        }
        val db = helper.writableDatabase
        try {
            AppDatabase.MIGRATION_18_19.migrate(db)
            val cols = getTableColumns(db, "payments")
            assertTrue(cols.contains("collectorUid"))
            assertTrue(cols.contains("collectorName"))
            assertTrue(cols.contains("collectorRole"))

            db.query("SELECT id, workspaceId, amount, collectorUid, collectorName, collectorRole FROM payments WHERE id = 99").use { c ->
                assertTrue(c.moveToNext())
                assertEquals(99L, c.getLong(0))
                assertEquals("ws_test", c.getString(1))
                assertEquals(500.0, c.getDouble(2), 0.001)
                assertEquals("", c.getString(3))
                assertEquals("", c.getString(4))
                assertEquals("", c.getString(5))
            }
        } finally {
            db.close()
            context.deleteDatabase(dbName)
        }
    }
}
