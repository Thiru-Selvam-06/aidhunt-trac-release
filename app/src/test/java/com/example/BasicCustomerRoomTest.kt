package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class BasicCustomerRoomTest {
    @Test fun migrationAddsOnlyBasicTableAndPreservesMoneyAuditAndRejectedAction() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="basic-migration-synthetic.db"
        var db=Room.databaseBuilder(context,AppDatabase::class.java,name).build()
        val job=JobEntryEntity(id=9,workspaceId="w",customerId=7,customerName="Original raw name",operatorName="Original",
            tractorLabel="T",workType="Rotavator",startTimeMillis=1000,endTimeMillis=2000,durationMinutes=1,
            totalAmount=1234.56,amountReceived=100.25,pendingAmount=1134.31,addedByPartner="Original",createdAt=1000,
            createdByUid="original",entryForUid="beneficiary",financialAttributionUid="beneficiary",
            jobEditHistoryJson="""[{"operationId":"keep-exact-history","before":{"amount":1234.56}}]""")
        val rejected=PendingOperationEntity("original-rejected","w","original","PAYMENT",10,"UPSERT",1,1000,
            """{"amount":100.25,"originalText":"தமிழ் ?"}""",state="REJECTED",attempts=2,failureReason="PERMISSION_DENIED")
        try {
            db.jobEntryDao().insertJob(job);db.pendingOperationDao().insert(rejected)
            db.openHelper.writableDatabase.apply {
                beginTransaction()
                try {
                    execSQL("DROP TABLE basic_customers");execSQL("DROP TABLE room_master_table");version=21
                    setTransactionSuccessful()
                } finally {endTransaction()}
            }
            db.close()
            db=Room.databaseBuilder(context,AppDatabase::class.java,name).addMigrations(AppDatabase.MIGRATION_21_22, AppDatabase.MIGRATION_22_23, AppDatabase.MIGRATION_23_24).build()
            assertTrue(db.basicCustomerDao().observe("w").first().isEmpty())
            assertEquals(job,db.jobEntryDao().getJobById("w",9))
            assertEquals(rejected,db.pendingOperationDao().getOperation(rejected.operationId))
        } finally {db.close();context.deleteDatabase(name)}
    }

    @Test fun basicProfileAndBoundCommandSurviveReopenAndConfirmedAbsencePreservesPendingRows() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val name="basic-pending-synthetic.db"
        var db=Room.databaseBuilder(context,AppDatabase::class.java,name).build()
        val row=BasicCustomerEntity(7,"w","Raw தமிழ் ?","","Entered location","operator",1000,"original-create",1000)
        val op=PendingOperationEntity("original-create","w","operator","CUSTOMER",7,"UPSERT",1,1000,
            """{"basicProfileCreate":true,"name":"Raw தமிழ் ?"}""")
        try {
            db.withTransaction {db.basicCustomerDao().put(row);db.pendingOperationDao().insert(op)}
            db.basicCustomerDao().put(row.copy(id=8,isSynced=true))
            db.basicCustomerDao().put(row.copy(workspaceId="other",isSynced=true))
            db.close();db=Room.databaseBuilder(context,AppDatabase::class.java,name).build()
            assertEquals(op,db.pendingOperationDao().getOperation(op.operationId))
            assertTrue(db.pendingOperationDao().nextBatch("w","other-actor").isEmpty())
            db.basicCustomerDao().removeAllConfirmed("w")
            assertEquals(listOf(row),db.basicCustomerDao().observe("w").first())
            assertEquals(1,db.basicCustomerDao().observe("other").first().size)
            assertEquals(1,db.basicCustomerDao().unsyncedCount("w"))
            assertEquals(op,db.pendingOperationDao().getOperation(op.operationId))
        } finally {db.close();context.deleteDatabase(name)}
    }
}
