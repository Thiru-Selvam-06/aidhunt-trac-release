package com.example

import com.example.data.auth.AuthorizationManager
import com.example.data.entity.*
import com.example.data.firebase.WorkspaceMember
import com.example.data.sync.*
import com.example.ui.util.*
import com.example.ui.screens.report.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
class CoreEnhancementTest {
    @Test fun auditCapacityLimitsRetainOriginalEventsAndRefuseOversizedNewHistory() {
        val event=mapOf("actorUid" to "p","operationId" to "old","actionTime" to 1)
        val history=List(100) { event }
        try {JobEditAudit.append(history,emptyMap(),emptyMap(),"p","new",2);fail("History truncated at limit")}
        catch (_:IllegalArgumentException) {}
        assertEquals(100,history.size);assertEquals("old",history.first()["operationId"])
        val large=mapOf<String,Any?>("notes" to "க".repeat(40000))
        try {JobEditAudit.append(emptyList<Any>(),large,large,"p","large",2);fail("Oversized audit accepted")}
        catch (_:IllegalArgumentException) {}
    }
    @Test fun undatedRefundReconcilesScreenAndPdfRows() {
        val dated=listOf(com.example.pdf.BalanceSheetRow("dated",5000.0,1000.0,4000.0))
        val exported=ReceiptPeriodReport.withUndated(dated,0.0,-200.0,false)
        assertEquals(2,exported.size);assertEquals(800.0,exported.sumOf { it.expenses },0.0)
        assertEquals(4200.0,exported.sumOf { it.balance },0.0)
        assertEquals(-200.0,exported.last().expenses,0.0)
        assertEquals(1,ReceiptPeriodReport.withUndated(dated,0.0,0.0,false).size)
        assertTrue(ReceiptPeriodReport.withUndated(dated,0.0,-200.0,true).last().periodLabel.contains("தேதி"))
    }
    @Test fun collectionPeriodsCrossYearAndMonthWithoutUsingWorkDate() {
        val a=PaymentEntity(id=1,workspaceId="w",amount=500.0,collectedAt=at("2026-12-31T18:29:00Z"))
        val b=a.copy(id=2,amount=4500.0,collectedAt=at("2026-12-31T18:31:00Z"))
        for(mode in listOf(BalanceSheetTabMode.DAILY,BalanceSheetTabMode.MONTHLY,BalanceSheetTabMode.YEARLY)) {
            val periods=ReceiptPeriodReport.periods("w",listOf(a,b),emptyList(),mode)
            assertEquals(2,periods.size);assertEquals(listOf(4500.0,500.0),periods.map { it.received })
        }
        val unpaidNextDay=b.copy(amount=7000.0)
        assertEquals(7000.0,ReceiptPeriodReport.periods("w",listOf(unpaidNextDay),emptyList(),BalanceSheetTabMode.DAILY).single().received,0.0)
    }
    @Test fun tamilCanonicalEquivalentSearchPreservesRawCustomerData() {
        val raw="கொடி";val decomposed=java.text.Normalizer.normalize(raw,java.text.Normalizer.Form.NFD)
        assertTrue(CustomerIdentity.matches(raw,decomposed));assertTrue(CustomerIdentity.matches(decomposed,raw))
        val original=CustomerEntity(id=1,workspaceId="w",name=decomposed,phone="")
        assertEquals(decomposed,CustomerIdentity.ranked(listOf(original),raw,true).single().name)
    }
    private fun at(raw: String) = Instant.parse(raw).toEpochMilli()
    @Test fun lateReceiptsGroupByIstCollectionDayAndReversalsRestate() {
        val a = PaymentEntity(id=1,workspaceId="w",amount=500.0,collectedAt=at("2026-11-07T18:29:00Z"))
        val b = a.copy(id=2,amount=4500.0,collectedAt=at("2026-11-07T18:31:00Z"))
        val rows = ReceiptPeriodReport.periods("w",listOf(a,b,b.copy(id=3,workspaceId="foreign",amount=999.0)),emptyList(),BalanceSheetTabMode.DAILY)
        assertEquals(listOf(4500.0,500.0),rows.map { it.received })
        assertEquals(5000.0,rows.sumOf { it.received },0.0)
        assertEquals(500.0,ReceiptPeriodReport.periods("w",listOf(a,b.copy(status="REVERSED")),emptyList(),BalanceSheetTabMode.MONTHLY).single().received,0.0)
    }
    @Test fun personalAndBusinessCostsRemainCostsWithoutBecomingCollections() {
        val costs = listOf(ExpenseEntity(id=1,workspaceId="w",expenseType="Diesel",amount=1000.0,tractorLabel="T",addedByPartner="A",paidBy="I Paid",dateTimestamp=at("2026-11-07T01:00:00Z")),
            ExpenseEntity(id=2,workspaceId="w",expenseType="Repair",amount=2000.0,tractorLabel="T",addedByPartner="B",paidBy="Business Paid",dateTimestamp=at("2026-11-07T01:00:00Z")))
        val period=ReceiptPeriodReport.periods("w",emptyList(),costs,BalanceSheetTabMode.YEARLY).single()
        assertEquals(3000.0,period.spent,0.0);assertEquals(0.0,period.received,0.0)
    }
    @Test fun prefixBeforeSubstringLimitAndStableSameNameIdentities() {
        val rows=listOf("Zara","Ravi","Ram","Raja","Rajesh").mapIndexed { i,n -> CustomerEntity(id=i+1L,workspaceId="w",name=n,phone="") }
        assertEquals(listOf("Raja","Rajesh","Ram","Ravi"),CustomerIdentity.ranked(rows,"r").take(4).map { it.name })
        assertEquals(2,CustomerIdentity.ranked(listOf(rows[1],rows[1].copy(id=99)),"R").size)
        assertTrue(CustomerIdentity.matches("ரவி","ர"))
    }
    @Test fun partnerEditsExistingHistoricalJobButCannotCreateOrGainDeleteRights() {
        val job=JobEntryEntity(id=1,workspaceId="w",customerName="C",operatorName="Owner",tractorLabel="T",workType="R",startTimeMillis=1,endTimeMillis=2,durationMinutes=1,totalAmount=500.0,amountReceived=0.0,pendingAmount=500.0,addedByPartner="Owner",createdByUid="owner",createdAt=1,isHistorical=true,entryForUid="owner")
        assertTrue(AuthorizationManager.canEditEntry(job,false,"P","p",9999999999L,"Partner"))
        assertFalse(AuthorizationManager.canCreateOldEntry(false,"Partner"))
        assertFalse(AuthorizationManager.canEditEntry(job,false,"O","o",2,"Operator"))
        assertFalse(AuthorizationManager.canDeleteEntry(job,false,"P","p",2,"Partner"))
        val members=listOf(WorkspaceMember(uid="p",workspaceId="w",role="PARTNER",status="ACTIVE"),WorkspaceMember(uid="owner",workspaceId="w",role="OWNER",status="ACTIVE"))
        val payload=org.json.JSONObject().put("operationProtocolVersion",2).put("workspaceId","w").put("createdByUid","owner").put("entryForUid","owner").put("isHistorical",true).put("createdAt",1).put("expectedBaseJson","{}")
        val op=PendingOperationEntity("edit","w","p","JOB",1,"UPSERT",1,9999999999L,payload.toString())
        OperationPolicy.requireAuthorized(op,"PARTNER",members)
        try { OperationPolicy.requireAuthorized(op.copy(mutationType="DELETE"),"PARTNER",members); fail("Historical deletion accepted") } catch (_:SecurityException) {}
    }
    @Test fun auditRetainsPreviousValuesAndOriginalOperation() {
        val before=mapOf<String,Any?>("notes" to "old","totalAmount" to 500L)
        val after=before+mapOf("notes" to "new")
        val first=JobEditAudit.append(emptyList<Any>(),before,after,"p","original-id",7)
        val next=JobEditAudit.append(first,after,after+mapOf("notes" to "third"),"q","second-id",8)
        assertEquals(first[0],next[0]);assertEquals(2,next.size)
        val restored=JobEditAudit.read(org.json.JSONArray(next).toString())
        assertEquals("original-id",restored[0]["operationId"])
        assertEquals("old",(restored[0]["before"] as Map<*,*>)["notes"])
        assertEquals("—",JobAuditPresentation.instant(0))
        assertEquals("07 Nov 2026, 10:30 AM IST",JobAuditPresentation.instant(at("2026-11-07T05:00:00Z")))
    }
    @Test fun additiveHistoryMigrationKeepsLegacyRowsAndDefaultHistory() {
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val name="core-migration-"+java.util.UUID.randomUUID().toString().take(8)+".db"
        val helper=androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object:androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(19) {
                    override fun onCreate(db:androidx.sqlite.db.SupportSQLiteDatabase) {
                        db.execSQL("CREATE TABLE job_entries (id INTEGER NOT NULL, workspaceId TEXT NOT NULL, notes TEXT NOT NULL, PRIMARY KEY(workspaceId,id))")
                        db.execSQL("INSERT INTO job_entries VALUES (1, 'w', 'original')")
                    }
                    override fun onUpgrade(db:androidx.sqlite.db.SupportSQLiteDatabase, oldVersion:Int, newVersion:Int) {}
                }).build())
        try {
            val database=helper.writableDatabase
            com.example.data.database.AppDatabase.MIGRATION_19_20.migrate(database)
            database.query("SELECT notes, jobEditHistoryJson FROM job_entries WHERE id=1").use {
                assertTrue(it.moveToFirst());assertEquals("original",it.getString(0));assertEquals("[]",it.getString(1));assertFalse(it.moveToNext())
            }
        } finally {helper.close();context.deleteDatabase(name)}
    }
    @Test fun roomReopenRetainsExactAuditAndCreationIdentity() { kotlinx.coroutines.runBlocking {
        val context=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val name="core-audit-"+java.util.UUID.randomUUID().toString().take(8)+".db"
        val event=JobEditAudit.append(emptyList<Any>(),mapOf("notes" to "before"),mapOf("notes" to "after"),"p","original-operation",7)
        val job=JobEntryEntity(id=1,workspaceId="w",customerName="C",operatorName="P",tractorLabel="T",workType="R",startTimeMillis=1,endTimeMillis=2,durationMinutes=1,totalAmount=100.0,amountReceived=0.0,pendingAmount=100.0,addedByPartner="Owner",createdByUid="owner",createdAt=1,jobEditHistoryJson=org.json.JSONArray(event).toString())
        var database=androidx.room.Room.databaseBuilder(context,com.example.data.database.AppDatabase::class.java,name).build()
        try {
            database.jobEntryDao().insertJob(job);database.close()
            database=androidx.room.Room.databaseBuilder(context,com.example.data.database.AppDatabase::class.java,name).build()
            assertEquals(job,database.jobEntryDao().getJobById("w",1))
        } finally {database.close();context.deleteDatabase(name)}
    } }

    @Test fun invoiceCorrectionCannotEraseGenuineReceivedMoney() {
        com.example.data.auth.EntryFinancialMutationPolicy.requireBalancedAmounts(2000.0,200.0,1800.0)
        for(values in listOf(listOf(100.0,200.0,0.0),listOf(2000.0,200.0,0.0),listOf(Double.NaN,0.0,0.0))) {
            try {com.example.data.auth.EntryFinancialMutationPolicy.requireBalancedAmounts(values[0],values[1],values[2]);fail("Invalid financial edit accepted")}
            catch (_:IllegalArgumentException) {}
        }
    }

}
