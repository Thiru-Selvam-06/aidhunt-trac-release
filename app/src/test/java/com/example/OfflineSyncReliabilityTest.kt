package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.PendingOperationEntity
import com.example.data.firebase.*
import com.example.data.sync.*
import com.example.ui.util.RetainedActionDetails
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class OfflineSyncReliabilityTest {
    private fun snapshot() = mapOf<String,Any?>("id" to 3759101127671304813L,"workspaceId" to "test-workspace",
        "customerId" to 2L,"customerName" to "Synthetic retained customer","workType" to "Rotavator", "createdAt" to 1000L,
        "createdByUid" to "original-partner","createdByRole" to "PARTNER","entryForUid" to "beneficiary",
        "financialAttributionUid" to "beneficiary","totalAmount" to 10358.0,"amountReceived" to 0.0,"pendingAmount" to 10358.0)
    private fun op(type: String = "JOB", sequence: Long = 1) = PendingOperationEntity("original-$type","test-workspace",
        "original-partner",type,if(type=="JOB")3759101127671304813L else 19L,"UPSERT",sequence,1000L,
        JSONObject(snapshot()).put("operationProtocolVersion",2).apply {
            if(type=="PAYMENT") { put("jobEntryId",3759101127671304813L);put("amount",1250.0);put("collectedByUid","original-partner") }
        }.toString())

    @Test fun retainedJobPaymentSurviveReopen() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        val name="synthetic-offline-${java.util.UUID.randomUUID()}.db"
        fun open()=Room.databaseBuilder(context,AppDatabase::class.java,name).build()
        var db=open()
        val job=op();val payment=op("PAYMENT",2)
        try {
            db.pendingOperationDao().insert(job);db.pendingOperationDao().insert(payment)
            db.jobEntryDao().insertJob(jobEntryFromFirestoreMap(snapshot()))
            db.pendingOperationDao().failed(job.operationId,"REJECTED","PERMISSION_DENIED")
            db.pendingOperationDao().waiting(payment.operationId,"Blocked by a rejected earlier action. Resolve that action before retrying.")
            assertTrue(OfflineEntryRecovery.restore(db,job,null,{true},{}))
            assertNull(db.jobEntryDao().getJobById(job.workspaceId,job.entityId))
            db.close();db=open()
            val retained=db.pendingOperationDao().observeActor(job.workspaceId,job.actorUid).first()
            assertEquals(2,retained.size)
            val original=retained.first();assertEquals(job.payloadJson,original.payloadJson)
            assertEquals(job.operationId,original.operationId);assertEquals(1000L,original.actionTime)
            assertEquals("REJECTED",original.state)
            assertTrue(RetainedActionDetails.text(original).contains("Synthetic retained customer"))
            val blocked=retained.last();assertEquals(payment.payloadJson,blocked.payloadJson);assertEquals(0,blocked.attempts)
            assertTrue(RetainedActionDetails.text(blocked).contains("1250"))
            assertEquals(1,db.pendingOperationDao().rejectedPredecessors(job.workspaceId,"JOB",job.entityId,2))
            assertTrue(db.pendingOperationDao().nextBatch(job.workspaceId,"different-account").isEmpty())
        } finally {db.close();context.deleteDatabase(name)}
    }
    @Test fun temporaryNetworkErrorsNeverBecomePermanentBasedOnAttemptCount() {
        for(code in listOf(FirebaseFirestoreException.Code.UNAVAILABLE,FirebaseFirestoreException.Code.ABORTED,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED))
            assertFalse(SyncFailurePolicy.permanent(FirebaseFirestoreException("temporary",code)))
        assertTrue(SyncFailurePolicy.permanent(FirebaseFirestoreException("denied",FirebaseFirestoreException.Code.PERMISSION_DENIED)))
    }
    @Test fun rejectedPaymentIsReviewAndOnlyDependentPendingActionsAreBlocked() {
        assertEquals("Needs Review", RetainedActionDetails.status(op("PAYMENT").copy(state="REJECTED",failureReason="PERMISSION_DENIED")))
        assertFalse(RetainedActionDetails.blocked(op("PAYMENT").copy(state="REJECTED",failureReason="PERMISSION_DENIED")))
        assertTrue(RetainedActionDetails.blocked(op("PAYMENT").copy(failureReason="Blocked by a rejected earlier action")))
        assertEquals("Pending Sync",RetainedActionDetails.status(op()))
    }
    @Test fun signedOutBackgroundWorkerCannotUploadOrReopenAnyFinancialAction() = runBlocking {
        val context=ApplicationProvider.getApplicationContext<Context>()
        if (com.google.firebase.FirebaseApp.getApps(context).isEmpty()) com.google.firebase.FirebaseApp.initializeApp(context,
            com.google.firebase.FirebaseOptions.Builder().setApplicationId("1:123:android:synthetic")
                .setApiKey("emulator-only-key").setProjectId("demo-aidhunt-v16").build())
        assertNull(com.google.firebase.auth.FirebaseAuth.getInstance().currentUser)
        val worker=androidx.work.testing.TestListenableWorkerBuilder<WorkspaceSyncWorker>(context)
            .setInputData(androidx.work.workDataOf("actorUid" to "original-partner","workspaceId" to "test-workspace")).build()
        assertEquals(androidx.work.ListenableWorker.Result.success(),worker.doWork())
    }
    @Test fun staleOfflineEditAndMissingOriginalBaseCannotOverwriteConfirmedChanges() {
        val base=jobEntryFromFirestoreMap(snapshot()).toFirestoreMap();val json=JSONObject(base).toString()
        QueuedWriteConflict.requireUnchanged("JOB",json,base,op().entityId,op().workspaceId)
        for(changed in listOf(base+mapOf("totalAmount" to 10500.0,"pendingAmount" to 10500.0),base+mapOf("customerName" to "Other partner edit"))) {
            try {QueuedWriteConflict.requireUnchanged("JOB",json,changed,op().entityId,op().workspaceId);fail("Stale write allowed")}catch(_:IllegalArgumentException){}
        }
        try {QueuedWriteConflict.requireUnchanged("JOB",null,base,op().entityId,op().workspaceId);fail("Missing base allowed")}catch(_:IllegalArgumentException){}
        QueuedWriteConflict.requireUnchanged("JOB",null,null,op().entityId,op().workspaceId)
    }
    @Test fun createCollisionAndExact64BitIdentityAreProtected() {
        val base=jobEntryFromFirestoreMap(snapshot()).toFirestoreMap()
        try {QueuedWriteConflict.requireUnchanged("JOB","null",base,op().entityId,op().workspaceId);fail("Collision allowed")}catch(_:IllegalArgumentException){}
        try {QueuedWriteConflict.requireUnchanged("JOB",JSONObject(base).toString(),base+mapOf("id" to op().entityId+1),op().entityId,op().workspaceId);fail("Long IDs rounded")}catch(_:IllegalArgumentException){}
    }
    @Test fun confirmedCustomerProofPreventsStaleResurrectionOnlyForExactIdentity() {
        val customer=com.example.data.entity.CustomerEntity(id=9007199254740995L,workspaceId="workspace-a",name="Synthetic",createdAt=1000)
        val proof=mapOf<String,Any?>("workspaceId" to customer.workspaceId,"recordId" to customer.id.toString(),"sourceCreatedAt" to 1000L)
        assertTrue(ConfirmedEntryDeletion.matchesCustomerProof(customer,proof))
        assertFalse(ConfirmedEntryDeletion.matchesCustomerProof(customer,proof+mapOf("workspaceId" to "foreign")))
        assertFalse(ConfirmedEntryDeletion.matchesCustomerProof(customer,proof+mapOf("recordId" to (customer.id+1).toString())))
        assertFalse(ConfirmedEntryDeletion.matchesCustomerProof(customer,proof+mapOf("sourceCreatedAt" to 1001L)))
    }
    @Test fun deletingOneSourceDoesNotSuppressACustomerWithoutServerDeletionProof() {
        val customer=com.example.data.entity.CustomerEntity(id=2,workspaceId="workspace-a",name="Retained customer",createdAt=1000)
        assertFalse(ConfirmedEntryDeletion.matchesCustomerProof(customer,null))
        assertFalse(ConfirmedEntryDeletion.matchesCustomerProof(customer,emptyMap()))
    }
}
