package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.database.AppDatabase
import com.example.data.firebase.FirebaseEnvironment
import com.example.data.repository.AuthRepository
import com.example.data.repository.WorkspaceRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceSelectionDeviceTest {
    @Test(timeout = 90000) fun operatorListenersConnectWithoutQueryingFinancialCustomers() = runBlocking {
        check(BuildConfig.DEVICE_TEST)
        check(FirebaseApp.getInstance().options.projectId == FirebaseEnvironment.DEMO_PROJECT)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        FirebaseAuth.getInstance().signInWithEmailAndPassword("operator@audit.invalid", "EmulatorOnly123!").await()
        val authRepo = AuthRepository.getInstance(context).also { it.startListening() }
        val profile = withTimeout(15000) { authRepo.currentUserProfile.first { it?.uid == "audit-operator" } }!!
        val workspace = WorkspaceRepository.getInstance(context, AppDatabase.getInstance(context))
        assertTrue(workspace.initializeForUser(profile).isSuccess)
        withTimeout(15000) { workspace.cloudConnectionState.first { it == com.example.data.firebase.CloudConnectionState.CONNECTED } }
        val cloud = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        assertFalse(cloud.collection("workspaces/full-audit-a/extensions").get(com.google.firebase.firestore.Source.SERVER).await().isEmpty)
        try {
            cloud.collection("workspaces/full-audit-a/customers").get(com.google.firebase.firestore.Source.SERVER).await()
            fail("Operator Customer financial access must remain denied")
        } catch (e: com.google.firebase.firestore.FirebaseFirestoreException) {
            assertEquals(com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
        }
    }
    @Test(timeout = 90000) fun discoversOwnedAndSharedWorkspacesWithIndependentRoles() = runBlocking {
        check(BuildConfig.DEVICE_TEST)
        check(FirebaseApp.getInstance().options.projectId == FirebaseEnvironment.DEMO_PROJECT)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val auth = FirebaseAuth.getInstance()
        auth.signInWithEmailAndPassword("partner@audit.invalid", "EmulatorOnly123!").await()
        val authRepo = AuthRepository.getInstance(context).also { it.startListening() }
        val profile = withTimeout(15000) { authRepo.currentUserProfile.first { it?.uid == "audit-partner" } }!!
        val workspace = WorkspaceRepository.getInstance(context, AppDatabase.getInstance(context))
        assertTrue(workspace.initializeForUser(profile).isSuccess)
        val available = workspace.getAvailableWorkspaces(profile)
        assertEquals(setOf("full-audit-a", "full-audit-b"), available.map { it.workspaceId }.toSet())
        assertTrue(workspace.switchActiveWorkspace("full-audit-b", profile).isSuccess)
        assertTrue(workspace.isOwnerOfWorkspace("full-audit-b"))
        assertTrue(workspace.initializeForUser(profile.copy(displayName = "Profile refreshed")).isSuccess)
        assertEquals("full-audit-b", workspace.activeWorkspaceId.value)
        workspace.stopWorkspaceListeners()
        assertTrue(workspace.initializeForUser(profile).isSuccess)
        assertEquals("full-audit-b", workspace.activeWorkspaceId.value)
        assertTrue(workspace.isOwnerOfWorkspace("full-audit-b"))
        assertTrue(workspace.switchActiveWorkspace("full-audit-a", profile).isSuccess)
        assertFalse(workspace.isOwnerOfWorkspace("full-audit-a"))
        assertTrue(workspace.switchActiveWorkspace("other-device-ws", profile).isFailure)
        assertEquals("full-audit-a", workspace.activeWorkspaceId.value)
    }
    @Test(timeout = 90000) fun accountChangesDoNotReuseThePreviousEntryDraft() = runBlocking {
        check(BuildConfig.DEVICE_TEST)
        check(FirebaseApp.getInstance().options.projectId == FirebaseEnvironment.DEMO_PROJECT)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val auth = FirebaseAuth.getInstance()
        auth.signInWithEmailAndPassword("partner@audit.invalid", "EmulatorOnly123!").await()
        android.util.Log.i("AUDIT_TEST", "Draft case: constructing ViewModel on main thread")
        lateinit var model: com.example.ui.viewmodel.MainViewModel
        val ready = java.util.concurrent.CountDownLatch(1)
        val constructionError = java.util.concurrent.atomic.AtomicReference<Throwable?>()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try { model = com.example.ui.viewmodel.MainViewModel(context.applicationContext as android.app.Application) }
            catch (e: Throwable) { constructionError.set(e) }
            finally { ready.countDown() }
        }
        assertTrue("ViewModel construction completed", ready.await(10, java.util.concurrent.TimeUnit.SECONDS))
        assertNull(constructionError.get())
        android.util.Log.i("AUDIT_TEST", "Draft case: ViewModel ready")
        val store = androidx.lifecycle.ViewModelStore().apply { put("draft-test", model) }
        val observer = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { model.settings.collect {} }
        val db = AppDatabase.getInstance(context)
        android.util.Log.i("AUDIT_TEST", "Draft case: reading preserved queue")
        val before = withTimeout(10000) { db.pendingOperationDao().observeActor("full-audit-a", "audit-partner").first() }.map { it.operationId }.toSet()
        android.util.Log.i("AUDIT_TEST", "Draft case: queue read completed")
        try {
            withTimeout(15000) { model.currentUserProfile.first { it?.uid == "audit-partner" } }
            android.util.Log.i("AUDIT_TEST", "Draft case: Partner profile verified; selecting fixture workspace")
            val partnerProfile = model.currentUserProfile.value!!
            assertTrue(withTimeout(15000) { WorkspaceRepository.getInstance(context, db).switchActiveWorkspace("full-audit-a", partnerProfile) }.isSuccess)
            withTimeout(15000) { model.currentUserRole.first { it == com.example.data.auth.RoleUtils.ROLE_PARTNER } }
            android.util.Log.i("AUDIT_TEST", "Draft case: Partner verified")
            model.updateNewEntryDraft(com.example.ui.viewmodel.NewEntryDraft(selectedPersonUid = "audit-partner", selectedPersonName = "Old identity"))
            withTimeout(15000) { model.settings.first { it.workspaceId == "full-audit-a" && it.isLoggedIn } }
            val signedOut = java.util.concurrent.CountDownLatch(1)
            android.os.Handler(android.os.Looper.getMainLooper()).post { model.logout { signedOut.countDown() } }
            assertTrue("Logout completed", signedOut.await(10, java.util.concurrent.TimeUnit.SECONDS))
            assertNull(model.newEntryDraft.value)
            android.util.Log.i("AUDIT_TEST", "Draft case: logout confirmed")
            auth.signInWithEmailAndPassword("owner@audit.invalid", "EmulatorOnly123!").await()
            withTimeout(15000) { model.currentUserProfile.first { it?.uid == "audit-owner" } }
            withTimeout(15000) { model.currentUserRole.first { it == com.example.data.auth.RoleUtils.ROLE_OWNER } }
            assertNull(model.newEntryDraft.value)
            android.util.Log.i("AUDIT_TEST", "Draft case: Owner verified")
            model.clearNewEntryDraft()
            assertEquals("audit-owner", model.newEntryDraft.value!!.selectedPersonUid)
            assertNotEquals("Old identity", model.newEntryDraft.value!!.selectedPersonName)
            assertEquals(before, db.pendingOperationDao().observeActor("full-audit-a", "audit-partner").first().map { it.operationId }.toSet())
        } finally {
            observer.cancel()
            val cleared = java.util.concurrent.CountDownLatch(1)
            android.os.Handler(android.os.Looper.getMainLooper()).post { store.clear(); cleared.countDown() }
            assertTrue("ViewModel lifecycle cleanup completed", cleared.await(5, java.util.concurrent.TimeUnit.SECONDS))
        }
    }

    @Test(timeout = 90000) fun collectionRoundTripRetainsProvenanceAndAcknowledgesCustomerCache() = runBlocking {
        check(BuildConfig.DEVICE_TEST)
        check(FirebaseApp.getInstance().options.projectId == FirebaseEnvironment.DEMO_PROJECT)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val auth = FirebaseAuth.getInstance()
        auth.signInWithEmailAndPassword("partner@audit.invalid", "EmulatorOnly123!").await()
        val authRepo = AuthRepository.getInstance(context).also { it.startListening() }
        val profile = withTimeout(15000) { authRepo.currentUserProfile.first { it?.uid == "audit-partner" } }!!
        val db = AppDatabase.getInstance(context)
        val workspace = WorkspaceRepository.getInstance(context, db)
        assertTrue(workspace.initializeForUser(profile).isSuccess)
        val cloud = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val source = cloud.collection("workspaces/full-audit-a/entries").get(com.google.firebase.firestore.Source.SERVER).await().documents.single()
        val sourceBefore = source.data!!
        val customerId = source.getLong("customerId")!!
        val localCustomer = withTimeout(15000) { db.customerDao().getCustomersForWorkspace("full-audit-a").first { it.any { row -> row.id == customerId } } }.first { it.id == customerId }
        val id = workspace.recordCustomerPayment(customer = localCustomer, amount = 20.0, dateTimestamp = System.currentTimeMillis(), paymentMethod = "Cash", note = "round-trip regression", operatorName = "Audit partner")
        workspace.pushUnsyncedToCloud()
        val ref = cloud.document("workspaces/full-audit-a/collections/$id")
        val original = ref.get(com.google.firebase.firestore.Source.SERVER).await()
        assertEquals("ACTIVE", original.getString("status"))
        assertTrue(db.customerDao().getCustomerById("full-audit-a", customerId)!!.isSynced)
        workspace.deletePayment(id)
        workspace.pushUnsyncedToCloud()
        val reversed = ref.get(com.google.firebase.firestore.Source.SERVER).await()
        assertEquals("REVERSED", reversed.getString("status"))
        assertEquals(original.getString("authorizationTimeStatus"), reversed.getString("authorizationTimeStatus"))
        assertEquals(original.getTimestamp("authorizationCreatedAt"), reversed.getTimestamp("authorizationCreatedAt"))
        val finalSource = source.reference.get(com.google.firebase.firestore.Source.SERVER).await()
        assertEquals((sourceBefore["amountReceived"] as Number).toDouble(), finalSource.getDouble("amountReceived")!!, 0.001)
        assertEquals((sourceBefore["pendingAmount"] as Number).toDouble(), finalSource.getDouble("pendingAmount")!!, 0.001)
        assertTrue(db.customerDao().getCustomerById("full-audit-a", customerId)!!.isSynced)
        val operation = db.pendingOperationDao().observe("full-audit-a").first().last { it.entityId == id && it.actorUid == "audit-partner" }
        assertEquals("ACKNOWLEDGED", operation.state)
        com.example.data.firebase.FirestoreRepository(context).applyPendingOperation(operation)
        assertEquals(finalSource.data, source.reference.get(com.google.firebase.firestore.Source.SERVER).await().data)
    }

}
