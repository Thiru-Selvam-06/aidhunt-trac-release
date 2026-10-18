package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.PendingOperationEntity
import com.example.data.firebase.WorkspaceMember
import com.example.data.sync.OperationPolicy
import com.example.data.sync.ExpiredOperationException
import com.example.data.sync.OfflineEntryRecovery
import com.example.data.firebase.jobEntryFromFirestoreMap
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineActionTimeTest {
    private val origin = 1000000L
    private fun snapshot(amount: Double, name: String = "original") = mapOf<String, Any?>("operationProtocolVersion" to 2,"id" to 1L, "workspaceId" to "a", "customerId" to 2L,
        "createdAt" to origin, "createdByUid" to "actor", "createdByRole" to "PARTNER", "entryForUid" to "actor", "financialAttributionUid" to "actor",
        "isHistorical" to false, "totalAmount" to amount, "pendingAmount" to amount, "amountReceived" to 0.0, "customerName" to name)
    private fun op(id: String, sequence: Long, hour: Long, payload: Map<String, Any?>) = PendingOperationEntity(id,"a","actor","JOB",1,"UPSERT",sequence,origin+hour*3600000,JSONObject(payload).toString())
    private fun members(role: String = "PARTNER", status: String = "ACTIVE") = listOf(WorkspaceMember(uid="actor",workspaceId="a",role=role,status=status))
    @Test fun hour0Creation18Accepted26RejectedAndRoomRestoresAForSameAndDifferentFields() = runBlocking {
        for (different in listOf(false,true)) {
            val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build()
            try {
                val create=op("create",1,0,snapshot(100.0));val A=op("A",2,18,snapshot(200.0))
                val B=op("B",3,26,if(different)snapshot(200.0,"expired B") else snapshot(300.0))
                db.withTransaction { for(command in listOf(create,A,B))db.pendingOperationDao().insert(command);db.jobEntryDao().insertJob(jobEntryFromFirestoreMap(JSONObject(B.payloadJson).let { j -> j.keys().asSequence().associateWith { j.get(it) } })) }
                OperationPolicy.requireAuthorized(create,"PARTNER",members());db.pendingOperationDao().acknowledge(create.operationId)
                OperationPolicy.requireAuthorized(A,"PARTNER",members());db.pendingOperationDao().acknowledge(A.operationId)
                try { OperationPolicy.requireAuthorized(B,"PARTNER",members());fail("Expired B accepted") } catch(e: ExpiredOperationException){db.pendingOperationDao().failed(B.operationId,"REJECTED",e.message!!)}
                val recalculated=mutableListOf<Long>()
                assertTrue(OfflineEntryRecovery.restore(db,B,snapshot(200.0),{true},{recalculated.add(it)}))
                val restored=db.jobEntryDao().getJobById("a",1)!!;assertEquals(200.0,restored.totalAmount,0.0);assertEquals("original",restored.customerName);assertEquals(origin,restored.createdAt);assertTrue(restored.isSynced)
                assertEquals(listOf(2L),recalculated);assertEquals("A",db.pendingOperationDao().lastAcknowledged("a","actor","JOB",1,3)!!.operationId)
                assertEquals(B,db.pendingOperationDao().observeActor("a","actor").first().single().copy(state="PENDING",attempts=0,failureReason=""))
                assertTrue(db.pendingOperationDao().nextBatch("a","actor").isEmpty());assertEquals(0,db.pendingOperationDao().unresolvedPredecessors("a","JOB",1,3))
            } finally {db.close()}
        }
    }
    @Test fun originalTimesAndIdsSurviveDatabaseCloseAndRetry() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>();val name="offline-"+java.util.UUID.randomUUID()+".db"
        val command=op("stable",1,18,snapshot(200.0));var db=Room.databaseBuilder(context,AppDatabase::class.java,name).build()
        try {
            db.pendingOperationDao().insert(command);db.pendingOperationDao().failed(command.operationId,"PENDING","Network unavailable");db.close()
            db=Room.databaseBuilder(context,AppDatabase::class.java,name).build()
            val restored=db.pendingOperationDao().nextBatch("a","actor").single();assertEquals(command.operationId,restored.operationId);assertEquals(command.actionTime,restored.actionTime);assertEquals(command.payloadJson,restored.payloadJson);assertEquals(1,restored.attempts)
            assertTrue(db.pendingOperationDao().nextBatch("b","actor").isEmpty());assertTrue(db.pendingOperationDao().nextBatch("a","other").isEmpty())
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun freshActionSupersedesOnlyReconciledOwnRejectionWithoutLosingEvidence() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build()
        try {
            val rejected=op("expired",1,26,snapshot(300.0)).copy(state="REJECTED",failureReason="Confirmed state restored; original action expired")
            db.pendingOperationDao().insert(rejected)
            db.pendingOperationDao().insert(rejected.copy(operationId="unrecovered",entityId=2,failureReason="Local reconciliation pending"))
            db.pendingOperationDao().supersedeReconciledRejections("a","other","JOB",1)
            assertEquals(1,db.pendingOperationDao().unresolvedPredecessors("a","JOB",1,2))
            db.pendingOperationDao().supersedeReconciledRejections("a","actor","JOB",1)
            assertEquals(0,db.pendingOperationDao().unresolvedPredecessors("a","JOB",1,2))
            val history=db.pendingOperationDao().observe("a").first().first();assertEquals("INVALIDATED",history.state);assertEquals(rejected.payloadJson,history.payloadJson);assertEquals(rejected.actionTime,history.actionTime)
            db.pendingOperationDao().supersedeReconciledRejections("a","actor","JOB",2)
            assertEquals(1,db.pendingOperationDao().unresolvedPredecessors("a","JOB",2,2))
        } finally{db.close()}
    }
    @Test fun legacyOperationWithoutVersionRemainsRecoverableAndCannotUpload() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build()
        try {
            val legacy=op("legacy",1,18,snapshot(100.0)-"operationProtocolVersion")
            db.pendingOperationDao().insert(legacy)
            try{OperationPolicy.requireAuthorized(legacy,"PARTNER",members());fail("Legacy uploaded")}catch(e:com.example.data.firebase.WorkspaceUpgradeRequiredException){db.pendingOperationDao().failed(legacy.operationId,"REJECTED",e.message!!)}
            val retained=db.pendingOperationDao().observeActor("a","actor").first().single();assertEquals(legacy.actionTime,retained.actionTime);assertEquals(legacy.payloadJson,retained.payloadJson);assertEquals("REJECTED",retained.state);assertTrue(retained.failureReason.contains("UPGRADE_REQUIRED"));assertTrue(db.pendingOperationDao().nextBatch("a","actor").isEmpty())
        }finally{db.close()}
    }
    @Test fun boundariesRevocationHistoricalAndRoleExceptions() {
        for (hour in listOf(0L,18L,24L,26L)) {
            val command=op("boundary",1,hour,snapshot(100.0))
            if(hour<24)OperationPolicy.requireAuthorized(command,"PARTNER",members())
            else try {OperationPolicy.requireAuthorized(command,"PARTNER",members());fail("Boundary accepted")}catch(_:ExpiredOperationException){}
        }
        OperationPolicy.requireAuthorized(op("owner",1,72,snapshot(100.0)),"OWNER",members("OWNER"))
        for(command in listOf(op("historical",1,18,snapshot(100.0)+("isHistorical" to true)),op("other",1,18,snapshot(100.0)+("createdByUid" to "other")))) {
            try{OperationPolicy.requireAuthorized(command,"PARTNER",members());fail("Unauthorized")}catch(_:SecurityException){}
        }
        try{OperationPolicy.requireAuthorized(op("revoked",1,18,snapshot(100.0)),"PARTNER",members(status="REMOVED"));fail("Revoked")}catch(_:SecurityException){}
        val asset=op("asset",1,72,snapshot(100.0)).copy(entityType="TRACTOR");OperationPolicy.requireAuthorized(asset,"PARTNER",members())
    }
    @Test fun recoveryRejectsWorkspaceMismatchOrSessionSwitchAndPreservesNewerPendingActions() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),AppDatabase::class.java).build()
        try {
            val B=op("B",2,26,snapshot(300.0));db.jobEntryDao().insertJob(jobEntryFromFirestoreMap(snapshot(300.0)))
            assertFalse(OfflineEntryRecovery.restore(db,B,snapshot(200.0),{false},{}))
            try{OfflineEntryRecovery.restore(db,B,snapshot(200.0)+("workspaceId" to "b"),{true},{});fail("Foreign recovery")}catch(_:IllegalArgumentException){}
            db.pendingOperationDao().insert(op("later",3,18,snapshot(400.0)))
            assertFalse(OfflineEntryRecovery.restore(db,B,snapshot(200.0),{true},{}));assertEquals(300.0,db.jobEntryDao().getJobById("a",1)!!.totalAmount,0.0)
        } finally{db.close()}
    }
}
