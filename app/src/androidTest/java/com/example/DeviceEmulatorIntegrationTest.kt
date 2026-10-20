package com.example

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.database.AppDatabase
import com.example.data.entity.PendingOperationEntity
import com.example.data.firebase.*
import com.example.data.sync.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Runs production repository/protocol code on the phone against demo Emulators only. */
@RunWith(AndroidJUnit4::class)
class DeviceEmulatorIntegrationTest {
 private val context=InstrumentationRegistry.getInstrumentation().targetContext
 private val auth=FirebaseAuth.getInstance()
 private val cloud=FirebaseFirestore.getInstance()
 private val ws="device-ws"
 private suspend fun login(role:String){check(BuildConfig.DEVICE_TEST);check(com.google.firebase.FirebaseApp.getInstance().options.projectId==FirebaseEnvironment.DEMO_PROJECT);auth.signInWithEmailAndPassword(role+"@device.invalid","EmulatorOnly123!").await();assertEquals("device-"+role,auth.currentUser!!.uid)}
 private suspend fun <T> tracedRoleRead(stage: String, action: suspend () -> T): T {
  android.util.Log.i("DEVICE_ROLE_TRACE", "START " + stage)
  return try { kotlinx.coroutines.withTimeout(20000) { action() } }
  finally { android.util.Log.i("DEVICE_ROLE_TRACE", "END " + stage) }
 }
 @Test(timeout = 90000) fun aRoleReadsAndWorkspaceIsolation()=runBlocking {
  assertEquals("com.aistudio.aidhunttrac.cedlir.devicetest",context.packageName)
  for(role in listOf("owner","partner","operator")) {
   tracedRoleRead(role + ":login") { login(role) }
   assertFalse(tracedRoleRead(role + ":extensions") { cloud.collection("workspaces/$ws/extensions").get(Source.SERVER).await() }.isEmpty)
   tracedRoleRead(role + ":entries") { (if(role=="operator") cloud.collection("workspaces/$ws/entries").whereEqualTo("isHistorical",false) else cloud.collection("workspaces/$ws/entries")).get(Source.SERVER).await() }
   if(role=="operator") {
    try { tracedRoleRead(role + ":collections-denied") { cloud.collection("workspaces/$ws/collections").get(Source.SERVER).await() }; fail("Operator financial access") }
    catch(e:com.google.firebase.firestore.FirebaseFirestoreException) { assertEquals(com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED,e.code) }
   } else tracedRoleRead(role + ":collections") { cloud.collection("workspaces/$ws/collections").get(Source.SERVER).await() }
   try { tracedRoleRead(role + ":other-workspace-denied") { cloud.collection("workspaces/other-device-ws/entries").get(Source.SERVER).await() }; fail("Workspace leak") }
   catch(e:com.google.firebase.firestore.FirebaseFirestoreException) { assertEquals(com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED,e.code) }
  }
 }
 @Test(timeout = 90000) fun bOfflineHour18SurvivesHour26AndDuplicateRetries()=runBlocking {
  login("partner");val repo=FirestoreRepository(context);val db=AppDatabase.getInstance(context);val initial=cloud.document("workspaces/$ws/entries/10").get(Source.SERVER).await().data!!;val origin=(initial["createdAt"] as Number).toLong();
  val a=PendingOperationEntity("device-A-"+java.util.UUID.randomUUID(),ws,"device-partner","JOB",10,"UPSERT",db.pendingOperationDao().nextSequence(),origin+18*3600000,JSONObject(initial).put("operationProtocolVersion",2).put("expectedBaseJson",JSONObject(jobEntryFromFirestoreMap(initial).toFirestoreMap()).toString()).put("totalAmount",200.0).put("pendingAmount",200.0).toString());
  android.util.Log.i("DEVICE_TEST_STAGE","B offline Room enqueue");cloud.disableNetwork().await();db.pendingOperationDao().insert(a);db.jobEntryDao().insertJob(jobEntryFromFirestoreMap(JSONObject(a.payloadJson).let { j->j.keys().asSequence().associateWith { j.get(it) } }));assertEquals(a.actionTime,db.pendingOperationDao().nextBatch(ws,"device-partner").first{it.operationId==a.operationId}.actionTime);cloud.enableNetwork().await();android.util.Log.i("DEVICE_TEST_STAGE","B replay");repo.applyPendingOperation(a);repo.applyPendingOperation(a);db.pendingOperationDao().acknowledge(a.operationId);
  val confirmed=cloud.document("workspaces/$ws/entries/10").get(Source.SERVER).await().data!!;assertEquals(200.0,(confirmed["totalAmount"] as Number).toDouble(),0.0);assertEquals(a.operationId,confirmed["operationId"]);assertEquals(a.actionTime,(confirmed["actionTime"] as Number).toLong());
  val b=a.copy(operationId="device-B-"+java.util.UUID.randomUUID(),sequence=db.pendingOperationDao().nextSequence(),actionTime=origin+26*3600000,payloadJson=JSONObject(a.payloadJson).put("expectedBaseJson",JSONObject(jobEntryFromFirestoreMap(confirmed).toFirestoreMap()).toString()).put("totalAmount",300.0).put("pendingAmount",300.0).toString());db.pendingOperationDao().insert(b);try{repo.applyPendingOperation(b);fail("Expired edit accepted")}catch(e:com.google.firebase.firestore.FirebaseFirestoreException){assertEquals(com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED,e.code);db.pendingOperationDao().failed(b.operationId,"REJECTED","Expired edit: original action exceeds 24 hours; local action retained")};android.util.Log.i("DEVICE_TEST_STAGE","B recovery");assertTrue(OfflineEntryRecovery.restore(db,b,confirmed,{true},{}));assertEquals(200.0,db.jobEntryDao().getJobById(ws,10)!!.totalAmount,0.0);assertEquals(200.0,(cloud.document("workspaces/$ws/entries/10").get(Source.SERVER).await().get("totalAmount") as Number).toDouble(),0.0);assertEquals(b.payloadJson,db.pendingOperationDao().observeActor(ws,"device-partner").first().first{it.operationId==b.operationId}.payloadJson);
  android.util.Log.i("DEVICE_TEST_STAGE","B account switch");login("owner");try{repo.applyPendingOperation(a);fail("Wrong actor uploaded")}catch(_:SecurityException){};login("partner");android.util.Log.i("DEVICE_TEST_STAGE","B done");Unit
 }
 @Test(timeout = 90000) fun cLegacyQueueAndCallableUpgradeRequired()=runBlocking {
  login("partner");val db=AppDatabase.getInstance(context);val op=PendingOperationEntity("legacy-"+java.util.UUID.randomUUID(),ws,"device-partner","JOB",10,"UPSERT",db.pendingOperationDao().nextSequence(),1000,"{}");db.pendingOperationDao().insert(op);try{FirestoreRepository(context).applyPendingOperation(op);fail("Legacy accepted")}catch(e:WorkspaceUpgradeRequiredException){db.pendingOperationDao().failed(op.operationId,"REJECTED",e.message!!)};assertEquals(op.payloadJson,db.pendingOperationDao().observeActor(ws,"device-partner").first().first{it.operationId==op.operationId}.payloadJson);
  val token=auth.currentUser!!.getIdToken(false).await().token!!;val request=WorkspaceCommandProtocol.request(FirebaseEnvironment.commandUrl(FirebaseEnvironment.DEMO_PROJECT,"deleteWorkspaceEntry"),token,JSONObject().put("workspaceId",ws).put("entryId","10"));okhttp3.OkHttpClient().newCall(request).execute().use{r->try{WorkspaceCommandProtocol.verifyResponse(r.code,r.body!!.string());fail("Legacy callable accepted")}catch(_:WorkspaceUpgradeRequiredException){}}
 }
 @Test(timeout = 90000) fun dFreshOwnerCreateDeleteAndRetry()=runBlocking {
  login("owner");val now=System.currentTimeMillis();val data=cloud.document("workspaces/$ws/entries/10").get(Source.SERVER).await().data!!.toMutableMap();data["id"]=20L;data["createdAt"]=now;data["createdByUid"]="device-owner";data["createdByRole"]="OWNER";data["entryForUid"]="device-owner";data["financialAttributionUid"]="device-owner";data.remove("authorizationCreatedAt");data.remove("serverReceivedAt");val op=PendingOperationEntity("fresh-"+java.util.UUID.randomUUID(),ws,"device-owner","JOB",20,"UPSERT",1000,now,JSONObject(data).put("operationProtocolVersion",2).toString());val repo=FirestoreRepository(context);repo.applyPendingOperation(op);assertTrue(cloud.document("workspaces/$ws/entries/20").get(Source.SERVER).await().exists());val deletion=op.copy(operationId="delete-"+java.util.UUID.randomUUID(),mutationType="DELETE",actionTime=System.currentTimeMillis());repo.applyPendingOperation(deletion);repo.applyPendingOperation(deletion);assertFalse(cloud.document("workspaces/$ws/entries/20").get(Source.SERVER).await().exists());login("partner")
 }

 @Test(timeout=90000) fun eOwnerFinancialDeletionPreconditions()=runBlocking {
  login("owner");val repo=FirestoreRepository(context)
  for(id in listOf(40L,43L,44L)){
   val ref=cloud.document("workspaces/$ws/entries/$id");val before=ref.get(Source.SERVER).await().data!!
   val op=PendingOperationEntity("blocked-"+java.util.UUID.randomUUID(),ws,"device-owner","JOB",id,"DELETE",2000,System.currentTimeMillis(),JSONObject(before).put("operationProtocolVersion",2).toString())
   try{repo.applyPendingOperation(op);fail("Financial precondition bypass $id")}catch(e:IllegalArgumentException){assertTrue(e.message!!.isNotBlank())}
   assertEquals(before,ref.get(Source.SERVER).await().data)
  }
  // An active Partner's linked Expense does not block an authorized Owner compound deletion.
  val linked=cloud.document("workspaces/$ws/entries/42").get(Source.SERVER).await().data!!
  val linkedExpense=cloud.document("workspaces/$ws/expenses/142")
  assertTrue(linkedExpense.get(Source.SERVER).await().exists())
  val compound=PendingOperationEntity("compound-"+java.util.UUID.randomUUID(),ws,"device-owner","JOB",42,"DELETE",2001,System.currentTimeMillis(),JSONObject(linked).put("operationProtocolVersion",2).toString())
  repo.applyPendingOperation(compound);repo.applyPendingOperation(compound)
  assertFalse(cloud.document("workspaces/$ws/entries/42").get(Source.SERVER).await().exists())
  assertFalse(linkedExpense.get(Source.SERVER).await().exists())
  val reversed=cloud.document("workspaces/$ws/entries/41").get(Source.SERVER).await().data!!
  val op=PendingOperationEntity("reversed-delete-"+java.util.UUID.randomUUID(),ws,"device-owner","JOB",41,"DELETE",2001,System.currentTimeMillis(),JSONObject(reversed).put("operationProtocolVersion",2).toString());repo.applyPendingOperation(op);repo.applyPendingOperation(op)
  assertFalse(cloud.document("workspaces/$ws/entries/41").get(Source.SERVER).await().exists());assertEquals("REVERSED",cloud.document("workspaces/$ws/collections/141").get(Source.SERVER).await().getString("status"))
 }
 @Test(timeout=90000) fun fMemberRemovalRetainsHistoryAndRevokesAccess()=runBlocking {
  login("owner");val repo=FirestoreRepository(context);val before=cloud.document("workspaces/$ws/entries/60").get(Source.SERVER).await().data!!
  val archive=repo.archiveMember(ws,"device-removal");assertEquals("device-removal",archive.uid);repo.archiveMember(ws,"device-removal")
  assertEquals("REMOVED",cloud.document("workspaces/$ws/members/device-removal").get(Source.SERVER).await().getString("status"));assertEquals("ARCHIVED",cloud.document("workspaces/$ws/deletedUsers/device-removal").get(Source.SERVER).await().getString("purgeState"));assertEquals(before,cloud.document("workspaces/$ws/entries/60").get(Source.SERVER).await().data)
  // A separate client cannot reuse the Owner's already-authorized listener snapshot.
  val revokedApp=com.google.firebase.FirebaseApp.initializeApp(context,com.google.firebase.FirebaseApp.getInstance().options,"revoked-"+java.util.UUID.randomUUID())!!
  val revokedAuth=FirebaseAuth.getInstance(revokedApp).also{it.useEmulator("127.0.0.1",19099)}
  val revokedCloud=FirebaseFirestore.getInstance(revokedApp).also{it.useEmulator("127.0.0.1",18180)}
  try {
   revokedAuth.signInWithEmailAndPassword("removal@device.invalid","EmulatorOnly123!").await();assertEquals("device-removal",revokedAuth.currentUser!!.uid)
   try{revokedCloud.collection("workspaces/$ws/entries").get(Source.SERVER).await();fail("Removed member retains access")}catch(e:com.google.firebase.firestore.FirebaseFirestoreException){assertEquals(com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED,e.code)}
  } finally {revokedCloud.terminate().await();revokedApp.delete()}
  login("partner");try{repo.archiveMember(ws,"device-operator");fail("Partner removes member")}catch(_:SecurityException){};login("owner")
 }

 @Test(timeout=90000) fun gLogoutRetainsQueueWithoutPostAuthBusinessWrites()=runBlocking {
  login("owner");val db=AppDatabase.getInstance(context);val retained=db.pendingOperationDao().observeActor(ws,"device-partner").first()
  val model=com.example.ui.viewmodel.MainViewModel(context.applicationContext as android.app.Application)
  val lifecycleStore=androidx.lifecycle.ViewModelStore().apply{put("logout-test",model)}
  val observer=kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { model.settings.collect{} }
  try {
   kotlinx.coroutines.withTimeout(15000){model.settings.first{it.workspaceId==ws && it.isLoggedIn}}
   val finished=java.util.concurrent.CountDownLatch(1)
   InstrumentationRegistry.getInstrumentation().runOnMainSync{model.logout{finished.countDown()}}
   assertTrue("Logout completion",finished.await(10,java.util.concurrent.TimeUnit.SECONDS));assertNull(auth.currentUser)
   assertEquals(retained,db.pendingOperationDao().observeActor(ws,"device-partner").first())
  } finally { observer.cancel();InstrumentationRegistry.getInstrumentation().runOnMainSync{lifecycleStore.clear()} }
 }

 @Test(timeout=90000) fun hAccountChangeClearsPreviousProfileDuringOfflineVerification()=runBlocking {
  login("owner");val repo=com.example.data.repository.AuthRepository.getInstance(context);repo.startListening()
  kotlinx.coroutines.withTimeout(15000){repo.currentUserProfile.first{it?.uid=="device-owner"}}
  cloud.disableNetwork().await()
  try {
   auth.signInWithEmailAndPassword("removal@device.invalid","EmulatorOnly123!").await()
   kotlinx.coroutines.withTimeout(2000){repo.currentUserProfile.first{it?.uid!="device-owner"}}
   assertFalse(repo.authState.value is AuthState.Authenticated && (repo.authState.value as AuthState.Authenticated).profile.uid=="device-owner")
  } finally {cloud.enableNetwork().await();login("owner")}
 }
 @Test(timeout=90000) fun iOfflineColdStartReconnectRestoresLiveRows()=runBlocking {
  login("owner");val db=AppDatabase.getInstance(context)
  val authRepo=com.example.data.repository.AuthRepository.getInstance(context);authRepo.startListening()
  val profile=kotlinx.coroutines.withTimeout(15000){authRepo.currentUserProfile.first{it?.uid=="device-owner"}}!!
  val workspace=com.example.data.repository.WorkspaceRepository.getInstance(context,db)
  assertTrue(workspace.initializeForUser(profile).isSuccess)
  workspace.stopWorkspaceListeners();cloud.disableNetwork().await()
  try {assertTrue("Cached workspace must start offline",workspace.initializeForUser(profile).isSuccess)} finally {cloud.enableNetwork().await()}
  val data=cloud.document("workspaces/$ws/entries/50").get(Source.SERVER).await().data!!.toMutableMap()
  val id=System.currentTimeMillis();val now=System.currentTimeMillis();data["id"]=id;data["createdAt"]=now;data["totalAmount"]=700.0;data["amountReceived"]=0.0;data["pendingAmount"]=700.0
  val op=PendingOperationEntity("live-"+java.util.UUID.randomUUID(),ws,"device-owner","JOB",id,"UPSERT",10000,now,JSONObject(data).put("operationProtocolVersion",2).toString())
  FirestoreRepository(context).applyPendingOperation(op)
  assertNull("No live listener attached during cached startup",db.jobEntryDao().getJobById(ws,id))
  workspace.pushUnsyncedToCloud(isOnline=true)
  kotlinx.coroutines.withTimeout(15000){db.jobEntryDao().getJobsForWorkspace(ws).first{rows->rows.any{it.id==id}}}
  assertEquals(700.0,db.jobEntryDao().getJobById(ws,id)!!.totalAmount,0.001)
 } @Test(timeout=90000) fun jDeletionAcknowledgementReconcilesStaleCustomerBalance()=runBlocking {
  login("owner");val db=AppDatabase.getInstance(context);val authRepo=com.example.data.repository.AuthRepository.getInstance(context);authRepo.startListening()
  val profile=kotlinx.coroutines.withTimeout(15000){authRepo.currentUserProfile.first{it?.uid=="device-owner"}}!!
  val workspace=com.example.data.repository.WorkspaceRepository.getInstance(context,db);assertTrue(workspace.initializeForUser(profile).isSuccess)
  val id=System.currentTimeMillis();val data=cloud.document("workspaces/$ws/entries/50").get(Source.SERVER).await().data!!.toMutableMap()
  val customer=cloud.document("workspaces/$ws/customers/2").get(Source.SERVER).await().data!!.toMutableMap()
  customer["id"]=id;customer["createdAt"]=id;customer["totalBilled"]=700.0;customer["totalPaid"]=0.0;customer["balanceDue"]=700.0
  FirestoreRepository(context).applyPendingOperation(PendingOperationEntity("ack-customer-"+java.util.UUID.randomUUID(),ws,"device-owner","CUSTOMER",id,"UPSERT",9999,id,JSONObject(customer).put("operationProtocolVersion",2).toString()))
  data["id"]=id;data["customerId"]=id;data["createdAt"]=id;data["totalAmount"]=700.0;data["amountReceived"]=0.0;data["pendingAmount"]=700.0
  val create=PendingOperationEntity("ack-create-"+java.util.UUID.randomUUID(),ws,"device-owner","JOB",id,"UPSERT",10000,id,JSONObject(data).put("operationProtocolVersion",2).toString())
  FirestoreRepository(context).applyPendingOperation(create)
  // Reproduce interrupted legacy queue recovery: stale source and customer remain until ACK.
  db.jobEntryDao().insertJob(jobEntryFromFirestoreMap(data).copy(isSynced=true))
  db.customerDao().insertCustomer(customerFromFirestoreMap(customer).copy(isSynced=false))
  val deletion=create.copy(operationId="ack-delete-"+java.util.UUID.randomUUID(),mutationType="DELETE",sequence=db.pendingOperationDao().nextSequence())
  db.pendingOperationDao().insert(deletion);workspace.pushUnsyncedToCloud(isOnline=true)
  assertEquals("ACKNOWLEDGED",db.pendingOperationDao().observe(ws).first().first{it.operationId==deletion.operationId}.state)
  assertNull(db.jobEntryDao().getJobById(ws,id))
  val local=db.customerDao().getCustomerById(ws,id)!!;assertTrue(local.isSynced);assertEquals(0.0,local.totalBilled,0.001);assertEquals(0.0,local.balanceDue,0.001)
  val remote=cloud.document("workspaces/$ws/customers/$id").get(Source.SERVER).await();assertEquals(0.0,remote.getDouble("totalBilled")!!,0.001);assertEquals(0.0,remote.getDouble("balanceDue")!!,0.001)
  workspace.syncRemoteCustomersToLocal(listOf(customerFromFirestoreMap(customer)),ws)
  val afterStale=db.customerDao().getCustomerById(ws,id)!!;assertTrue(afterStale.isSynced);assertEquals(0.0,afterStale.totalBilled,0.001);assertEquals(0.0,afterStale.balanceDue,0.001)
  val retained=cloud.collection("workspaces/$ws/entries").get(Source.SERVER).await().documents.map{jobEntryFromFirestoreMap(it.data!!)}
  workspace.syncRemoteJobsToLocal(retained+jobEntryFromFirestoreMap(data),ws)
  assertNull("Delayed Entry snapshot cannot resurrect a confirmed deleted source",db.jobEntryDao().getJobById(ws,id))
  val afterEntrySnapshot=db.customerDao().getCustomerById(ws,id)!!;assertEquals(0.0,afterEntrySnapshot.totalBilled,0.001);assertEquals(0.0,afterEntrySnapshot.balanceDue,0.001)
 }
 @Test(timeout=120000) fun lOfflineNewJobCollectionExpenseAndIndependentPartnerReplica()=runBlocking {
  login("owner")
  val db=AppDatabase.getInstance(context)
  val authRepo=com.example.data.repository.AuthRepository.getInstance(context);authRepo.startListening()
  val profile=kotlinx.coroutines.withTimeout(15000){authRepo.currentUserProfile.first{it?.uid=="device-owner"}}!!
  val workspace=com.example.data.repository.WorkspaceRepository.getInstance(context,db)
  assertTrue(workspace.initializeForUser(profile).isSuccess)
  val now=System.currentTimeMillis()
  cloud.disableNetwork().await()
  val id:Long
  val original:List<PendingOperationEntity>
  try {
   val draft=com.example.data.entity.JobEntryEntity(workspaceId=ws,customerName="Offline synthetic "+now,
    operatorName="Device PARTNER",tractorId=3,tractorLabel="Disposable Tractor",workType="Rotavator",
    startTimeMillis=now-60000,endTimeMillis=now,durationMinutes=1,totalAmount=10358.0,amountReceived=1250.0,
    pendingAmount=9108.0,addedByPartner="Device OWNER",createdAt=now,entryForUid="device-partner")
   val expense=com.example.data.entity.ExpenseEntity(expenseType="Diesel",amount=900.0,
    tractorLabel="Disposable Tractor",addedByPartner="Device OWNER",paidBy="Business Paid")
   id=workspace.saveJobEntry(draft,linkedExpenses=listOf(expense))
   original=db.pendingOperationDao().observeActor(ws,"device-owner").first().filter {
    it.entityType=="JOB" && it.entityId==id || it.entityType=="PAYMENT" && JSONObject(it.payloadJson).optLong("jobEntryId")==id ||
     it.entityType=="EXPENSE" && JSONObject(it.payloadJson).optLong("relatedJobId")==id }
   assertEquals(setOf("JOB","PAYMENT","EXPENSE"),original.map{it.entityType}.toSet())
   assertEquals(1250.0,db.jobEntryDao().getJobById(ws,id)!!.amountReceived,0.0)
   assertTrue(original.all{it.state=="PENDING" && it.actorUid=="device-owner"})
  } finally {cloud.enableNetwork().await()}
  workspace.pushUnsyncedToCloud(ws)
  val history=db.pendingOperationDao().observe(ws).first().filter{it.actorUid=="device-owner"}
  for(op in original) {
   val ack=history.first{it.operationId==op.operationId};assertEquals(ack.failureReason,"ACKNOWLEDGED",ack.state)
   assertEquals(op.payloadJson,ack.payloadJson);assertEquals(op.actionTime,ack.actionTime)
  }
  val confirmed=cloud.document("workspaces/$ws/entries/$id").get(Source.SERVER).await()
  assertEquals(1250.0,confirmed.getDouble("amountReceived")!!,0.0);assertEquals(9108.0,confirmed.getDouble("pendingAmount")!!,0.0)
  val payment=original.single{it.entityType=="PAYMENT"};val expense=original.single{it.entityType=="EXPENSE"}
  val receipts=cloud.collection("workspaces/$ws/collections").whereEqualTo("jobEntryId",id).get(Source.SERVER).await()
  assertEquals(1,receipts.size());assertEquals(payment.operationId,receipts.documents.single().getString("operationId"))
  assertEquals(900.0,cloud.document("workspaces/$ws/expenses/${expense.entityId}").get(Source.SERVER).await().getDouble("amount")!!,0.0)
  // Lost acknowledgement retries use original commands and must not apply financial deltas twice.
  val repo=FirestoreRepository(context);repo.applyPendingOperation(payment);repo.applyPendingOperation(expense)
  assertEquals(1250.0,cloud.document("workspaces/$ws/entries/$id").get(Source.SERVER).await().getDouble("amountReceived")!!,0.0)
  val app=com.google.firebase.FirebaseApp.initializeApp(context,com.google.firebase.FirebaseApp.getInstance().options,"partner-replica-"+now)!!
  val otherAuth=FirebaseAuth.getInstance(app).also{it.useEmulator("127.0.0.1",19099)}
  val otherCloud=FirebaseFirestore.getInstance(app).also{it.useEmulator("127.0.0.1",18180)}
  try {
   otherAuth.signInWithEmailAndPassword("partner@device.invalid","EmulatorOnly123!").await()
   val partnerCopy=otherCloud.document("workspaces/$ws/entries/$id").get(Source.SERVER).await()
   assertEquals(confirmed.data,partnerCopy.data)
   assertEquals(receipts.documents.single().data,otherCloud.document("workspaces/$ws/collections/${payment.entityId}").get(Source.SERVER).await().data)
   val secondId=now+1
   val second=confirmed.data!!.toMutableMap().apply {
    put("id",secondId);put("createdByUid","device-partner");put("createdByRole","PARTNER");put("actorUid","device-partner");put("createdByName","Device PARTNER");put("addedByPartner","Device PARTNER")
    put("createdAt",now);put("actionTime",now);put("operationId","independent-partner-"+now)
    put("authorizationCreatedAt",com.google.firebase.Timestamp(java.util.Date(now)))
    put("serverReceivedAt",com.google.firebase.firestore.FieldValue.serverTimestamp())
    put("totalAmount",500.0);put("amountReceived",0.0);put("pendingAmount",500.0);remove("collectionId")
   }
   otherCloud.document("workspaces/$ws/entries/$secondId").set(second).await()
   kotlinx.coroutines.withTimeout(15000){db.jobEntryDao().getJobsForWorkspace(ws).first{jobs->jobs.any{it.id==secondId}}}
   assertEquals("device-partner",db.jobEntryDao().getJobById(ws,secondId)!!.createdByUid)
   assertEquals(confirmed.data,otherCloud.document("workspaces/$ws/entries/$id").get(Source.SERVER).await().data)
  } finally {otherCloud.terminate().await();app.delete()}
 }
}
