package com.example

import com.example.data.entity.*
import com.example.data.sync.*
import com.example.data.auth.AuthorizationManager
import com.example.data.database.AppDatabase
import com.example.data.firebase.toFirestoreMap
import com.example.data.firebase.exactCollectionDocumentId
import com.example.data.firebase.deleteTombstoneFields
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class EntryDeletionReconciliationTest {
    @Test fun expenseDeleteTombstoneRetainsImmutableCreationAndPayerFields() {
        val fields = deleteTombstoneFields("same-operation", "owner", 1234L, emptyList<Any>(), "server-time")
        assertEquals("same-operation", fields["operationId"])
        assertEquals("owner", fields["actorUid"])
        assertEquals(1234L, fields["actionTime"])
        assertEquals(true, fields["isDeleted"])
        assertFalse(fields.keys.any { it in setOf("createdAt", "createdByUid", "authorizationCreatedAt",
            "authorizationTimeStatus", "serverReceivedAt", "expenseForUid", "paidByUid") })
    }
    @Test fun collectionListenerRejectsHashedOrImpreciseDocumentIds() {
        assertNull(exactCollectionDocumentId("staging-protected-payment", mapOf("status" to "ACTIVE")))
        assertEquals(9007199254740995L, exactCollectionDocumentId("9007199254740995", mapOf("id" to 9007199254740995L)))
        assertNull(exactCollectionDocumentId("9007199254740995", mapOf("id" to 9007199254740994L)))
        assertNull(exactCollectionDocumentId("9007199254740995", mapOf("id" to 9007199254740995.0)))
    }
    private fun entry() = JobEntryEntity(id=1, workspaceId="a", customerId=2, customerName="Customer", operatorName="Partner", tractorLabel="T", workType="Work", startTimeMillis=1, endTimeMillis=2, durationMinutes=1, totalAmount=100.0, amountReceived=0.0, pendingAmount=100.0, addedByPartner="Partner", createdByUid="actor", entryForUid="actor")
    private fun collection(status: String="ACTIVE", primary: Long=1, allocations: String="[]") = PaymentEntity(id=10, workspaceId="a", jobEntryId=primary, amount=40.0, status=status, allocationsJson=allocations)
    private fun denied(block: () -> Unit) { try { block(); fail("Expected rejection") } catch (_: IllegalArgumentException) {} }
    @Test fun unpaidAndReversedHistoryPermitDeletion() {
        EntryDeletionPolicy.requireUnpaid(entry(), emptyList())
        EntryDeletionPolicy.requireUnpaid(entry(), listOf(collection("REVERSED")))
        denied { EntryDeletionPolicy.requireUnpaid(entry(), listOf(collection().copy(amount=0.0))) }
        denied { EntryDeletionPolicy.requireUnpaid(entry(), listOf(collection().copy(amount=0.0,
            allocationsJson="[{\"jobId\":1,\"amount\":1}]"))) }
    }
    @Test fun partialFullAndInconsistentBalancesBlockDeletion() {
        for (paid in listOf(40.0,100.0)) denied { EntryDeletionPolicy.requireUnpaid(entry().copy(amountReceived=paid,pendingAmount=100-paid), emptyList()) }
        denied { EntryDeletionPolicy.requireUnpaid(entry().copy(pendingAmount=20.0),emptyList()) }
    }
    @Test fun activeAndSplitCollectionsBlockWithoutMutatingHistory() {
        val split=collection(primary=9,allocations="[{\"jobId\":1,\"amount\":40}]")
        assertTrue(EntryDeletionPolicy.references(split,1))
        try {
            EntryDeletionPolicy.requireUnpaid(entry(), listOf(split))
            fail("Active Collection must block deletion")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message.orEmpty().contains("1 active Collection"))
            assertTrue(error.message.orEmpty().contains("linked Expenses"))
        }
        assertEquals("ACTIVE",split.status)
    }
    @Test fun ownerCanDeletePaidEntryWithSingleLinkedCollectionButSharedAllocationFails() {
        val paid = entry().copy(amountReceived = 40.0, pendingAmount = 60.0)
        val own = collection(allocations = "[{\"jobId\":\"1\",\"amount\":40}]").copy(customerId = 2)
        assertEquals(listOf(own), EntryDeletionPolicy.plannedCollectionDeletes(paid, listOf(own), true,
            "owner", "OWNER", 2000L))
        denied { EntryDeletionPolicy.plannedCollectionDeletes(paid, listOf(own.copy(
            allocationsJson = "[{\"jobId\":\"1\",\"amount\":20},{\"jobId\":\"3\",\"amount\":20}]")),
            true, "owner", "OWNER", 2000L) }
        denied { EntryDeletionPolicy.plannedCollectionDeletes(paid, listOf(own), false,
            "operator", "OPERATOR", 2000L) }
    }
    @Test fun foreignWorkspaceAndUnknownStatusFailClosed() {
        denied { EntryDeletionPolicy.requireUnpaid(entry(), listOf(collection("REVERSED").copy(workspaceId="b"))) }
        denied { EntryDeletionPolicy.requireUnpaid(entry(), listOf(collection("UNKNOWN"))) }
    }
    @Test fun deleteWaitsForReversalAndExpenseAcknowledgements() {
        val op=PendingOperationEntity("delete","a","actor","JOB",1,"DELETE",3,1,"{\"deleteDependencies\":[{\"type\":\"PAYMENT\",\"id\":10},{\"type\":\"EXPENSE\",\"id\":20}]}")
        val refs=OperationDependencies.references(op)
        assertTrue(refs.contains("PAYMENT" to 10L)); assertTrue(refs.contains("EXPENSE" to 20L))
    }
    @Test fun linkedExpensesCanJoinEntryDeletionOnlyWhenEachIsAuthorized() {
        val own = ExpenseEntity(id=20,workspaceId="a",expenseType="Diesel",amount=5.0,tractorLabel="T",addedByPartner="Partner",relatedJobId=1,createdByUid="actor",expenseForUid="actor")
        EntryDeletionPolicy.requireNoLinkedExpenses(entry(),listOf(own),false,"actor","PARTNER")
        EntryDeletionPolicy.requireNoLinkedExpenses(entry(),listOf(own),true,"owner","OWNER")
        for (expense in listOf(own.copy(createdByUid="other"),own.copy(workspaceId="b"))) {
            denied { EntryDeletionPolicy.requireNoLinkedExpenses(entry(),listOf(expense),false,"actor","PARTNER") }
        }
        denied { EntryDeletionPolicy.requireNoLinkedExpenses(entry(),listOf(own),false,"actor","OPERATOR") }
        EntryDeletionPolicy.requireNoLinkedExpenses(entry(),emptyList(),false,"actor","PARTNER")
    }

    @Test fun existingRoleOwnershipAndHistoricalRestrictionsRemain() {
        for(role in listOf("PARTNER","OPERATOR")) {
            assertTrue(AuthorizationManager.canDeleteEntry(entry(),false,"", "actor",role=role))
            assertFalse(AuthorizationManager.canDeleteEntry(entry(),false,"", "other",role=role))
            assertFalse(AuthorizationManager.canDeleteEntry(entry().copy(isHistorical=true),false,"", "actor",role=role))
        }
    }

    @Test fun terminalRejectionsDoNotStrandCustomerAcknowledgement() {
        val op=PendingOperationEntity("edit","a","other-actor","JOB",1,"UPSERT",1,1,"{\"customerId\":2}")
        assertTrue(OperationDependencies.blocksCustomerAcknowledgement(op,2))
        assertFalse(OperationDependencies.blocksCustomerAcknowledgement(op.copy(state="REJECTED",failureReason="Confirmed state restored; expired"),2))
        assertTrue(OperationDependencies.blocksCustomerAcknowledgement(op.copy(state="REJECTED",failureReason="Local reconciliation pending"),2))
        assertFalse(OperationDependencies.blocksCustomerAcknowledgement(op.copy(state="ACKNOWLEDGED"),2))
        assertFalse(OperationDependencies.blocksCustomerAcknowledgement(op,3))
    }

    @Test fun freshCompoundDeletePreservesPendingChildrenAndSupersedesOnlyItsRejectedDelete() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            val dao = db.pendingOperationDao()
            val old = PendingOperationEntity("old-delete", "a", "actor", "JOB", 1, "DELETE", 1, 100,
                "{}", state = "REJECTED", failureReason = "Unsupported Firestore field")
            val child = PendingOperationEntity("collection-upload", "a", "actor", "PAYMENT", 10, "UPSERT", 2, 200,
                "{\"jobEntryId\":1,\"customerId\":2}")
            val rejectedFinancial = child.copy(operationId = "retained-financial", entityId = 11,
                sequence = 3, state = "REJECTED", failureReason = "PERMISSION_DENIED")
            val foreign = old.copy(operationId = "foreign-delete", workspaceId = "b")
            val fresh = old.copy(operationId = "fresh-delete", sequence = 4, actionTime = 400,
                state = "PENDING", failureReason = "", payloadJson =
                    "{\"deleteDependencies\":[{\"type\":\"PAYMENT\",\"id\":10}]}")
            db.withTransaction {
                listOf(old, child, rejectedFinancial, foreign).forEach { dao.insert(it) }
                dao.invalidateRejectedEntryDelete("a", "actor", 1)
                dao.insert(fresh)
            }
            assertEquals("INVALIDATED", dao.getOperation(old.operationId)!!.state)
            assertEquals(child, dao.getOperation(child.operationId))
            assertEquals(rejectedFinancial, dao.getOperation(rejectedFinancial.operationId))
            assertEquals(foreign, dao.getOperation(foreign.operationId))
            assertEquals(listOf(child.operationId, fresh.operationId), dao.nextBatch("a", "actor").map { it.operationId })
            assertTrue(OperationDependencies.references(fresh).contains("PAYMENT" to 10L))
        } finally { db.close() }
    }

    @Test fun rejectedCompoundDeleteRestoresEntryAndLinkedExpensesInOriginalWorkspace() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            val source = entry().copy(createdAt = 1000)
            val expense = ExpenseEntity(id = 20, workspaceId = "a", expenseType = "Diesel", amount = 5.0,
                tractorLabel = "T", addedByPartner = "Partner", relatedJobId = 1, createdByUid = "actor", expenseForUid = "actor")
            val payment = collection(allocations = "[{\"jobId\":\"1\",\"amount\":40}]").copy(customerId = 2)
            val payload = JSONObject(source.toFirestoreMap())
                .put("linkedExpenseIds", org.json.JSONArray().put("20"))
                .put("linkedExpenseSnapshots", org.json.JSONArray().put(JSONObject(expense.toFirestoreMap())))
                .put("linkedCollectionIds", org.json.JSONArray().put("10"))
                .put("linkedCollectionSnapshots", org.json.JSONArray().put(JSONObject(payment.toFirestoreMap()))).toString()
            val op = PendingOperationEntity("delete", "a", "actor", "JOB", 1, "DELETE", 1, 2000, payload, state = "REJECTED")
            db.withTransaction { db.pendingOperationDao().insert(op) }
            assertTrue(OfflineEntryRecovery.restore(db, op, source.toFirestoreMap(), { true }, { }))
            assertNotNull(db.jobEntryDao().getJobById("a", 1))
            assertEquals(5.0, db.expenseDao().getExpenseById("a", 20)!!.amount, 0.0)
            assertEquals("ACTIVE", db.paymentDao().getPaymentById("a", 10)!!.status)
            assertNull(db.expenseDao().getExpenseById("b", 20))
        } finally { db.close() }
    }
}
