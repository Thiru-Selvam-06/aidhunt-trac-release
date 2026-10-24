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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/** Runs production repository/protocol code on Android against demo Emulators only.
 * The test class does not itself establish whether Android is an emulator or physical hardware.
 */
@RunWith(AndroidJUnit4::class)
@org.junit.FixMethodOrder(org.junit.runners.MethodSorters.NAME_ASCENDING)
class DeviceEmulatorIntegrationTest {
 companion object {
  private val replicaApps=mutableListOf<com.google.firebase.FirebaseApp>()
  @org.junit.AfterClass @JvmStatic fun closeReplicaApps() {
   // Auth emulator configuration keeps app callbacks; retire apps after all clients are configured.
   replicaApps.forEach{it.delete()};replicaApps.clear()
  }
 }
 private val context=InstrumentationRegistry.getInstrumentation().targetContext
 private val auth=FirebaseAuth.getInstance()
 private val cloud=FirebaseFirestore.getInstance()
 private val ws="ws_device_test"
 // Synthetic, demo-only evidence. Never log tokens or alter/retry a failed operation.
 @org.junit.Rule @JvmField val failureEvidence=object:org.junit.rules.TestWatcher(){
  override fun failed(error:Throwable,description:org.junit.runner.Description){
   if(!BuildConfig.DEVICE_TEST || com.google.firebase.FirebaseApp.getInstance().options.projectId!=FirebaseEnvironment.DEMO_PROJECT)return
   val evidence=JSONObject().put("test",description.methodName).put("deviceTime",System.currentTimeMillis())
    .put("authenticatedUid",auth.currentUser?.uid).put("error",error.toString())
   runBlocking(kotlinx.coroutines.Dispatchers.IO){
    runCatching{kotlinx.coroutines.withTimeout(10000){
     val db=AppDatabase.getInstance(context)
     evidence.put("operations",org.json.JSONArray().apply{
      db.pendingOperationDao().observe(ws).first().filter{it.state!="ACKNOWLEDGED"}.forEach{op->
       val payload=JSONObject(op.payloadJson)
       put(JSONObject().put("id",op.operationId).put("entity",op.entityType).put("entityId",op.entityId)
        .put("actor",op.actorUid).put("workspace",op.workspaceId).put("state",op.state).put("attempts",op.attempts)
        .put("actionTime",op.actionTime).put("reason",op.failureReason).put("identity",JSONObject().apply{
         for(key in listOf("createdAt","createdByUid","createdByRole","entryForUid","customerId","tractorId","jobEntryId","relatedJobId"))
          if(payload.has(key))put(key,payload.get(key))
        }))
      }
     })
     val user=auth.currentUser?:return@withTimeout
     val token=user.getIdToken(false).await()
     evidence.put("tokenUid",token.claims["user_id"]?:token.claims["sub"])
     val before=System.currentTimeMillis()
     val request=okhttp3.Request.Builder().url("http://127.0.0.1:18180/v1/projects/${FirebaseEnvironment.DEMO_PROJECT}/databases/(default)/documents:batchGet")
      .header("Authorization","Bearer ${token.token}")
      .post(JSONObject().put("documents",org.json.JSONArray().put("projects/${FirebaseEnvironment.DEMO_PROJECT}/databases/(default)/documents/workspaces/$ws/members/${user.uid}"))
       .toString().toRequestBody("application/json".toMediaType())).build()
     okhttp3.OkHttpClient.Builder().callTimeout(5,java.util.concurrent.TimeUnit.SECONDS).build().newCall(request).execute().use{response->
      val after=System.currentTimeMillis()
      evidence.put("serverRead",JSONObject().put("deviceBefore",before).put("deviceAfter",after).put("httpStatus",response.code).apply{
       if(response.isSuccessful){val rows=org.json.JSONArray(response.body!!.string());val row=rows.getJSONObject(0)
        put("readTime",row.optString("readTime"));val fields=row.optJSONObject("found")?.optJSONObject("fields")
        put("memberRole",fields?.optJSONObject("role")?.optString("stringValue"));put("memberStatus",fields?.optJSONObject("status")?.optString("stringValue"))}
      })
     }
    }}.onFailure{evidence.put("diagnosticError",it.toString())}
   }
   android.util.Log.e("DEVICE_SYNC_EVIDENCE",evidence.toString())
  }
 }
 private suspend fun login(role:String){check(BuildConfig.DEVICE_TEST);check(com.google.firebase.FirebaseApp.getInstance().options.projectId==FirebaseEnvironment.DEMO_PROJECT);auth.signInWithEmailAndPassword(role+"@device.invalid","EmulatorOnly123!").await();assertEquals("device-"+role,auth.currentUser!!.uid)}
 private suspend fun <T> tracedRoleRead(stage: String, action: suspend () -> T): T {
  android.util.Log.i("DEVICE_ROLE_TRACE", "START " + stage)
  return try { kotlinx.coroutines.withTimeout(20000) { action() } }
  finally { android.util.Log.i("DEVICE_ROLE_TRACE", "END " + stage) }
 }
 private suspend fun awaitCustomerCommand(db:AppDatabase, operation:PendingOperationEntity, stage:String) {
  val rows=kotlinx.coroutines.withTimeout(60000){db.pendingOperationDao().observe(ws).first{rows->
   rows.any{it.operationId==operation.operationId && it.state in setOf("ACKNOWLEDGED","REJECTED")}
  }}
  val actual=rows.single{it.operationId==operation.operationId}
  assertEquals("$stage: ${actual.entityType} ${actual.operationId}; actor=${actual.actorUid}; ${actual.failureReason}","ACKNOWLEDGED",actual.state)
 }
 private suspend fun awaitSyncedJob(db:AppDatabase,id:Long,stage:String){
  kotlinx.coroutines.withTimeout(60000){while(true){
   val customerId=db.jobEntryDao().getJobById(ws,id)?.customerId
   val rejected=db.pendingOperationDao().observe(ws).first().filter{it.state=="REJECTED" &&
    ((it.entityType=="JOB" && it.entityId==id) || (it.entityType=="CUSTOMER" && it.entityId==customerId))}
   assertTrue("$stage: retained job rejection: $rejected",rejected.isEmpty())
   if(db.jobEntryDao().getJobById(ws,id)?.isSynced==true)return@withTimeout
   kotlinx.coroutines.delay(100)
  }}
 }
 private suspend fun deletionPayload(source:Map<String,Any?>):JSONObject {
  val entry=jobEntryFromFirestoreMap(source)
  val expenses=cloud.collection("workspaces/$ws/expenses").get(Source.SERVER).await().documents
   .map{expenseFromFirestoreMap(it.data!!)}.filter{it.relatedJobId==entry.id}
  val linked=cloud.collection("workspaces/$ws/collections").get(Source.SERVER).await().documents
   .map{paymentFromFirestoreMap(it.data!!)}.filter{EntryDeletionPolicy.references(it,entry.id)}
  val collections=EntryDeletionPolicy.plannedCollectionDeletes(entry,linked,true,"device-owner","OWNER",System.currentTimeMillis())
  return JSONObject(source).put("operationProtocolVersion",2)
   .put("linkedExpenseIds",org.json.JSONArray(expenses.map{it.id.toString()}))
   .put("linkedExpenseSnapshots",org.json.JSONArray(expenses.map{JSONObject(it.toFirestoreMap())}))
   .put("linkedCollectionIds",org.json.JSONArray(collections.map{it.id.toString()}))
   .put("linkedCollectionSnapshots",org.json.JSONArray(collections.map{JSONObject(it.toFirestoreMap())}))
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
  login("owner");val now=System.currentTimeMillis();val data=cloud.document("workspaces/$ws/entries/10").get(Source.SERVER).await().data!!.toMutableMap();data["id"]=20L;data["createdAt"]=now;data["createdByUid"]="device-owner";data["createdByRole"]="OWNER";data["entryForUid"]="device-owner";data["financialAttributionUid"]="device-owner";data.remove("authorizationCreatedAt");data.remove("serverReceivedAt");val op=PendingOperationEntity("fresh-"+java.util.UUID.randomUUID(),ws,"device-owner","JOB",20,"UPSERT",1000,now,JSONObject(data).put("operationProtocolVersion",2).toString());val repo=FirestoreRepository(context);repo.applyPendingOperation(op);assertTrue(cloud.document("workspaces/$ws/entries/20").get(Source.SERVER).await().exists());val deletion=op.copy(operationId="delete-"+java.util.UUID.randomUUID(),mutationType="DELETE",actionTime=System.currentTimeMillis(),payloadJson=deletionPayload(data).toString());repo.applyPendingOperation(deletion);repo.applyPendingOperation(deletion);assertFalse(cloud.document("workspaces/$ws/entries/20").get(Source.SERVER).await().exists());login("partner")
 }

 @Test(timeout=90000) fun eOwnerFinancialDeletionPreconditions()=runBlocking {
  login("owner");val repo=FirestoreRepository(context)
  for(id in listOf(43L,44L)){
   val ref=cloud.document("workspaces/$ws/entries/$id");val before=ref.get(Source.SERVER).await().data!!
   val op=PendingOperationEntity("blocked-"+java.util.UUID.randomUUID(),ws,"device-owner","JOB",id,"DELETE",2000,System.currentTimeMillis(),JSONObject(before).put("operationProtocolVersion",2).toString())
   try{repo.applyPendingOperation(op);fail("Financial precondition bypass $id")}catch(e:IllegalArgumentException){assertTrue(e.message!!.isNotBlank())}
   assertEquals(before,ref.get(Source.SERVER).await().data)
  }
  // An active Partner's linked Expense does not block an authorized Owner compound deletion.
  val linked=cloud.document("workspaces/$ws/entries/42").get(Source.SERVER).await().data!!
  val linkedExpense=cloud.document("workspaces/$ws/expenses/142")
  assertTrue(linkedExpense.get(Source.SERVER).await().exists())
  val compound=PendingOperationEntity("compound-"+java.util.UUID.randomUUID(),ws,"device-owner","JOB",42,"DELETE",2001,System.currentTimeMillis(),deletionPayload(linked).toString())
  repo.applyPendingOperation(compound);repo.applyPendingOperation(compound)
  assertFalse(cloud.document("workspaces/$ws/entries/42").get(Source.SERVER).await().exists())
  assertFalse(linkedExpense.get(Source.SERVER).await().exists())
  val reversed=cloud.document("workspaces/$ws/entries/41").get(Source.SERVER).await().data!!
  val op=PendingOperationEntity("reversed-delete-"+java.util.UUID.randomUUID(),ws,"device-owner","JOB",41,"DELETE",2001,System.currentTimeMillis(),deletionPayload(reversed).toString());repo.applyPendingOperation(op);repo.applyPendingOperation(op)
  assertFalse(cloud.document("workspaces/$ws/entries/41").get(Source.SERVER).await().exists());assertEquals("REVERSED",cloud.document("workspaces/$ws/collections/141").get(Source.SERVER).await().getString("status"))
  val paid=cloud.document("workspaces/$ws/entries/40").get(Source.SERVER).await().data!!
  val paidDelete=PendingOperationEntity("paid-delete-"+java.util.UUID.randomUUID(),ws,"device-owner","JOB",40,"DELETE",2002,System.currentTimeMillis(),deletionPayload(paid).toString())
  repo.applyPendingOperation(paidDelete);repo.applyPendingOperation(paidDelete)
  assertFalse(cloud.document("workspaces/$ws/entries/40").get(Source.SERVER).await().exists())
  assertFalse(cloud.document("workspaces/$ws/collections/140").get(Source.SERVER).await().exists())
  assertEquals(paidDelete.operationId,cloud.document("workspaces/$ws/entryDeletionProofs/40").get(Source.SERVER).await().getString("operationId"))
 }
 @Test(timeout=90000) fun fMemberRemovalRetainsHistoryAndRevokesAccess()=runBlocking {
  login("owner");val repo=FirestoreRepository(context);val before=cloud.document("workspaces/$ws/entries/60").get(Source.SERVER).await().data!!
  val archive=repo.archiveMember(ws,"device-removal");assertEquals("device-removal",archive.uid);repo.archiveMember(ws,"device-removal")
  assertEquals("REMOVED",cloud.document("workspaces/$ws/members/device-removal").get(Source.SERVER).await().getString("status"));assertEquals("ARCHIVED",cloud.document("workspaces/$ws/deletedUsers/device-removal").get(Source.SERVER).await().getString("purgeState"));assertEquals(before,cloud.document("workspaces/$ws/entries/60").get(Source.SERVER).await().data)
  // A separate client cannot reuse the Owner's already-authorized listener snapshot.
  val revokedApp=com.google.firebase.FirebaseApp.initializeApp(context,com.google.firebase.FirebaseApp.getInstance().options,"revoked-"+java.util.UUID.randomUUID())!!
  replicaApps.add(revokedApp)
  val revokedAuth=FirebaseAuth.getInstance(revokedApp).also{it.useEmulator("127.0.0.1",19099)}
  val revokedCloud=FirebaseFirestore.getInstance(revokedApp).also{it.useEmulator("127.0.0.1",18180)}
  try {
   revokedAuth.signInWithEmailAndPassword("removal@device.invalid","EmulatorOnly123!").await();assertEquals("device-removal",revokedAuth.currentUser!!.uid)
   try{revokedCloud.collection("workspaces/$ws/entries").get(Source.SERVER).await();fail("Removed member retains access")}catch(e:com.google.firebase.firestore.FirebaseFirestoreException){assertEquals(com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED,e.code)}
  } finally {revokedCloud.terminate().await()}
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
  val deletion=create.copy(operationId="ack-delete-"+java.util.UUID.randomUUID(),mutationType="DELETE",sequence=db.pendingOperationDao().nextSequence(),payloadJson=deletionPayload(data).toString())
  db.pendingOperationDao().insert(deletion);workspace.pushUnsyncedToCloud(isOnline=true)
  assertEquals("ACKNOWLEDGED",db.pendingOperationDao().observe(ws).first().first{it.operationId==deletion.operationId}.state)
  assertNull(db.jobEntryDao().getJobById(ws,id))
  assertNull("The final source deletion removes its customer too",db.customerDao().getCustomerById(ws,id))
  assertFalse(cloud.document("workspaces/$ws/customers/$id").get(Source.SERVER).await().exists())
  assertEquals(deletion.operationId,cloud.document("workspaces/$ws/customerDeletionProofs/$id").get(Source.SERVER).await().getString("operationId"))
  val retainedCustomers=cloud.collection("workspaces/$ws/customers").get(Source.SERVER).await().documents.map{customerFromFirestoreMap(it.data!!)}
  workspace.syncRemoteCustomersToLocal(retainedCustomers+customerFromFirestoreMap(customer),ws)
  assertNull("Stale customer snapshot cannot resurrect the deleted customer",db.customerDao().getCustomerById(ws,id))
  assertNotNull("Customers with other retained jobs must survive",db.customerDao().getCustomerById(ws,2))
  val retained=cloud.collection("workspaces/$ws/entries").get(Source.SERVER).await().documents.map{jobEntryFromFirestoreMap(it.data!!)}
  workspace.syncRemoteJobsToLocal(retained+jobEntryFromFirestoreMap(data),ws)
  assertNull("Delayed Entry snapshot cannot resurrect a confirmed deleted source",db.jobEntryDao().getJobById(ws,id))
  assertNull(db.customerDao().getCustomerById(ws,id))
 }
 @Test(timeout=90000) fun kBackgroundColdStartRestoresOnlyOriginalVerifiedSession()=runBlocking {
  login("owner")
  val db=AppDatabase.getInstance(context)
  val profile=com.example.data.repository.AuthRepository.getInstance(context).let { authRepo ->
   authRepo.startListening();kotlinx.coroutines.withTimeout(15000){authRepo.currentUserProfile.first{it?.uid=="device-owner"}}!!
  }
  val warm=com.example.data.repository.WorkspaceRepository.getInstance(context,db)
  assertTrue(warm.initializeForUser(profile).isSuccess)
  val cold=com.example.data.repository.WorkspaceRepository(context,db,FirestoreRepository(context))
  assertEquals("device-owner" to ws,cold.verifiedWorkspaceContext.value)
  assertEquals(ws,cold.activeWorkspaceId.value)
  try {
   login("partner")
   val other=com.example.data.repository.WorkspaceRepository(context,db,FirestoreRepository(context))
   assertNull("Another account cannot restore the Owner's saved selection",other.verifiedWorkspaceContext.value)
   assertNull(other.activeWorkspaceId.value)
  } finally {login("owner")}
 }
 @Test(timeout=120000) fun lOfflineNewJobCollectionExpenseAndIndependentPartnerReplica()=runBlocking {
  login("owner")
  val db=AppDatabase.getInstance(context)
  val authRepo=com.example.data.repository.AuthRepository.getInstance(context);authRepo.startListening()
  val profile=kotlinx.coroutines.withTimeout(15000){authRepo.currentUserProfile.first{it?.uid=="device-owner"}}!!
  val workspace=com.example.data.repository.WorkspaceRepository.getInstance(context,db)
  assertTrue(workspace.initializeForUser(profile).isSuccess)
  val now=System.currentTimeMillis()
  val background=androidx.work.WorkManager.getInstance(context)
  val previousWork=background.getWorkInfosByTagFlow("trac-ledger-sync").first().map{it.id}.toSet()
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
  // No manual push: Room scheduling/reconnect must drive the existing queue automatically.
  // observeActor intentionally excludes ACKNOWLEDGED rows; completion requires the durable history.
  kotlinx.coroutines.withTimeout(90000){db.pendingOperationDao().observe(ws).first{rows ->
   original.all{op->rows.any{it.operationId==op.operationId && it.state=="ACKNOWLEDGED"}} ||
    original.any{op->rows.any{it.operationId==op.operationId && it.state=="REJECTED"}}
  }}
  val history=db.pendingOperationDao().observe(ws).first().filter{it.actorUid=="device-owner"}
  for(op in original) {
   val ack=history.first{it.operationId==op.operationId};assertEquals(ack.failureReason,"ACKNOWLEDGED",ack.state)
   assertEquals(op.payloadJson,ack.payloadJson);assertEquals(op.actionTime,ack.actionTime)
  }
  kotlinx.coroutines.withTimeout(15000){background.getWorkInfosByTagFlow("trac-ledger-sync").first{work ->
   work.any{it.id !in previousWork && it.state==androidx.work.WorkInfo.State.SUCCEEDED}
  }}
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
  replicaApps.add(app)
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
   ServerActionTimeBarrier({FirestoreServerTime.read(ws,"device-partner",app)}).awaitEligible(now)
   otherCloud.document("workspaces/$ws/entries/$secondId").set(second).await()
   kotlinx.coroutines.withTimeout(15000){db.jobEntryDao().getJobsForWorkspace(ws).first{jobs->jobs.any{it.id==secondId}}}
   assertEquals("device-partner",db.jobEntryDao().getJobById(ws,secondId)!!.createdByUid)
   assertEquals(confirmed.data,otherCloud.document("workspaces/$ws/entries/$id").get(Source.SERVER).await().data)
  } finally {otherCloud.terminate().await()}
 }

 @Test(timeout=180000) fun mStandaloneExpensesKeepOriginalOutboxAcrossRoomReloadAndSynchronizeOnceForBothActors()=runBlocking {
  for(role in listOf("owner","partner")) {
   login(role); val actor="device-"+role;val db=AppDatabase.getInstance(context)
   val authRepo=com.example.data.repository.AuthRepository.getInstance(context);authRepo.startListening()
   val profile=kotlinx.coroutines.withTimeout(15000){authRepo.currentUserProfile.first{it?.uid==actor}}!!
   val workspace=com.example.data.repository.WorkspaceRepository.getInstance(context,db)
   assertTrue(workspace.initializeForUser(profile).isSuccess)
   val ids=mutableListOf<Long>();val originals:List<PendingOperationEntity>
   cloud.disableNetwork().await()
   try {
    for((index,amount) in listOf(1.0,1.0,2.0).withIndex()) {
     val funding=if(index==0) "I Paid" else "Business Paid"
     ids.add(workspace.addExpense(com.example.data.entity.ExpenseEntity(workspaceId=ws,expenseType="Diesel",amount=amount,
      tractorLabel="Disposable Tractor",addedByPartner="Synthetic recorder",paidBy=funding,
      paidByUid=if(index==0) actor else "",paidByPartner=if(index==0) "Synthetic payer" else "",
      expenseForUid=actor,financialAttributionUid=actor,description="Synthetic standalone "+System.currentTimeMillis())))
    }
    originals=db.pendingOperationDao().observeActor(ws,actor).first().filter{it.entityType=="EXPENSE" && it.entityId in ids}
    assertEquals(3,originals.size);assertTrue(originals.all{it.state=="PENDING" && it.actorUid==actor})
    // A second Room connection reads persisted disk state, without clearing or closing the live database.
    val reopened=androidx.room.Room.databaseBuilder(context,AppDatabase::class.java,db.openHelper.databaseName!!).build()
    try {for(op in originals){
     assertEquals(op,reopened.pendingOperationDao().getOperation(op.operationId))
     assertEquals(db.expenseDao().getExpenseById(ws,op.entityId),reopened.expenseDao().getExpenseById(ws,op.entityId))
     assertNull(reopened.expenseDao().getExpenseById(ws,op.entityId)!!.relatedJobId)
     assertEquals(0L,JSONObject(op.payloadJson).getLong("relatedJobId"))
    }} finally {reopened.close()}
   } finally {cloud.enableNetwork().await()}
   val cold=com.example.data.repository.WorkspaceRepository(context,db,FirestoreRepository(context))
   assertEquals(actor to ws,cold.verifiedWorkspaceContext.value)
   cold.pushUnsyncedToCloud(isOnline=true)
   kotlinx.coroutines.withTimeout(60000){db.pendingOperationDao().observe(ws).first{rows->originals.all{op->rows.any{it.operationId==op.operationId && it.state=="ACKNOWLEDGED"}}}}
   val app=com.google.firebase.FirebaseApp.initializeApp(context,com.google.firebase.FirebaseApp.getInstance().options,"expense-replica-"+java.util.UUID.randomUUID())!!
   replicaApps.add(app)
   val secondAuth=FirebaseAuth.getInstance(app).also{it.useEmulator("127.0.0.1",19099)}
   val secondCloud=FirebaseFirestore.getInstance(app).also{it.useEmulator("127.0.0.1",18180)}
   try {
    secondAuth.signInWithEmailAndPassword((if(role=="owner") "partner" else "owner")+"@device.invalid","EmulatorOnly123!").await()
    for(op in originals) {
     val payload=JSONObject(op.payloadJson);val path="workspaces/$ws/expenses/${op.entityId}"
     val before=cloud.document(path).get(Source.SERVER).await()
     assertEquals(op.operationId,before.getString("operationId"));assertEquals(actor,before.getString("createdByUid"))
     assertEquals(actor,before.getString("expenseForUid"));assertEquals(payload.getString("paidByUid"),before.getString("paidByUid"))
     assertEquals(payload.getDouble("amount"),before.getDouble("amount")!!,0.0)
     assertEquals(0L,before.getLong("relatedJobId"));assertEquals(before.data,secondCloud.document(path).get(Source.SERVER).await().data)
     FirestoreRepository(context).applyPendingOperation(op)
     assertEquals(before.data,cloud.document(path).get(Source.SERVER).await().data)
     val ack=db.pendingOperationDao().getOperation(op.operationId)!!
     assertEquals(op.payloadJson,ack.payloadJson);assertEquals(op.actionTime,ack.actionTime)
    }
   } finally {secondCloud.terminate().await()}
  }
 }

 @Test(timeout=180000) fun nInitialCollectorFollowsBeneficiaryForPartnerNewAndOwnerHistoricalEntries()=runBlocking {
  for(role in listOf("owner","partner")) {
   login(role);val actor="device-"+role;val target=if(role=="owner") "device-partner" else "device-owner"
   val db=AppDatabase.getInstance(context);val authRepo=com.example.data.repository.AuthRepository.getInstance(context);authRepo.startListening()
   val profile=kotlinx.coroutines.withTimeout(15000){authRepo.currentUserProfile.first{it?.uid==actor}}!!
   val workspace=com.example.data.repository.WorkspaceRepository.getInstance(context,db);assertTrue(workspace.initializeForUser(profile).isSuccess)
   val now=System.currentTimeMillis();val historical=role=="owner";val created=if(historical) now-3*86400000L else now
   val total=if(historical) 2000.0 else 1100.0;val received=if(historical) 1200.0 else 900.0
   val id:Long;val originals:List<PendingOperationEntity>
   cloud.disableNetwork().await()
   try {
    id=workspace.saveJobEntry(com.example.data.entity.JobEntryEntity(workspaceId=ws,customerName="Initial receipt synthetic "+now,
     operatorName="Selected Partner",tractorId=3,tractorLabel="Disposable Tractor",workType="Rotavator",
     startTimeMillis=created-60000,endTimeMillis=created,durationMinutes=1,totalAmount=total,amountReceived=received,
     pendingAmount=total-received,addedByPartner="Recorder",createdAt=created,entryForUid=target,isHistorical=historical))
    originals=db.pendingOperationDao().observeActor(ws,actor).first().filter{it.entityType=="JOB" && it.entityId==id ||
     it.entityType=="PAYMENT" && JSONObject(it.payloadJson).optLong("jobEntryId")==id}
    assertEquals(setOf("JOB","PAYMENT"),originals.map{it.entityType}.toSet())
    val payment=JSONObject(originals.single{it.entityType=="PAYMENT"}.payloadJson)
    assertEquals(target,payment.getString("collectorUid"));assertEquals(actor,payment.getString("collectedByUid"))
    assertEquals(originals.single{it.entityType=="JOB"}.operationId,payment.getString("initialJobOperationId"))
   } finally {cloud.enableNetwork().await()}
   workspace.pushUnsyncedToCloud(isOnline=true)
   kotlinx.coroutines.withTimeout(60000){db.pendingOperationDao().observe(ws).first{rows->originals.all{op->rows.any{it.operationId==op.operationId && it.state=="ACKNOWLEDGED"}}}}
   val source=cloud.document("workspaces/$ws/entries/$id").get(Source.SERVER).await()
   assertEquals(created,source.getLong("createdAt"));assertEquals(actor,source.getString("createdByUid"));assertEquals(target,source.getString("entryForUid"))
   assertEquals(received,source.getDouble("amountReceived")!!,0.0);assertEquals(total-received,source.getDouble("pendingAmount")!!,0.0)
   val payment=originals.single{it.entityType=="PAYMENT"};val receipt=cloud.document("workspaces/$ws/collections/${payment.entityId}").get(Source.SERVER).await()
   assertEquals(target,receipt.getString("collectorUid"));assertEquals(actor,receipt.getString("collectedByUid"))
   FirestoreRepository(context).applyPendingOperation(payment)
   assertEquals(received,cloud.document("workspaces/$ws/entries/$id").get(Source.SERVER).await().getDouble("amountReceived")!!,0.0)
   assertEquals(1,cloud.collection("workspaces/$ws/collections").whereEqualTo("jobEntryId",id).get(Source.SERVER).await().size())
  }
 }

 @Test(timeout=180000) fun pOfflineProfileEditSurvivesRoomReloadMergesIndependentPartnerEditAndConfirms()=runBlocking {
  login("owner");val actor="device-owner";val db=AppDatabase.getInstance(context)
  val authRepo=com.example.data.repository.AuthRepository.getInstance(context);authRepo.startListening()
  val profile=kotlinx.coroutines.withTimeout(15000){authRepo.currentUserProfile.first{it?.uid==actor}}!!
  val workspace=com.example.data.repository.WorkspaceRepository.getInstance(context,db)
  assertTrue(workspace.initializeForUser(profile).isSuccess)
  val now=System.currentTimeMillis()
  val id=workspace.saveJobEntry(com.example.data.entity.JobEntryEntity(workspaceId=ws,customerName="saga jothi $now",
   operatorName="Partner",tractorId=3,tractorLabel="Disposable Tractor",workType="Rotavator",startTimeMillis=now-60000,
   endTimeMillis=now,durationMinutes=1,totalAmount=1000.0,amountReceived=200.0,pendingAmount=800.0,
   addedByPartner="Owner",createdAt=now,entryForUid="device-partner"))
  workspace.pushUnsyncedToCloud(isOnline=true)
  awaitSyncedJob(db,id,"initial profile job")
  val originalJob=db.jobEntryDao().getJobById(ws,id)!!
  val originalCustomer=db.customerDao().getCustomerById(ws,originalJob.customerId)!!
  val originalReceipt=cloud.collection("workspaces/$ws/collections").whereEqualTo("jobEntryId",id).get(Source.SERVER).await().documents.single()
  val originalCloud=cloud.document("workspaces/$ws/customers/${originalCustomer.id}").get(Source.SERVER).await().data!!
  val app=com.google.firebase.FirebaseApp.initializeApp(context,com.google.firebase.FirebaseApp.getInstance().options,"customer-replica-"+now)!!
  replicaApps.add(app)
  val otherAuth=FirebaseAuth.getInstance(app).also{it.useEmulator("127.0.0.1",19099)}
  val otherCloud=FirebaseFirestore.getInstance(app).also{it.useEmulator("127.0.0.1",18180)}
  val desired=originalCustomer.copy(name="Saga Jothi $now")
  val operation:PendingOperationEntity
  try {
   otherAuth.signInWithEmailAndPassword("partner@device.invalid","EmulatorOnly123!").await()
   cloud.disableNetwork().await()
   try {
    workspace.updateCustomer(desired,originalCustomer)
    operation=db.pendingOperationDao().observe(ws).first().last{it.entityType=="CUSTOMER" && it.entityId==originalCustomer.id}
    assertEquals("PENDING",operation.state);assertTrue(JSONObject(operation.payloadJson).has(CustomerProfileEdit.PAYLOAD_KEY))
    assertEquals(desired.name,db.customerDao().getCustomerById(ws,originalCustomer.id)!!.name)
    assertEquals(originalJob,db.jobEntryDao().getJobById(ws,id))
    val reopened=androidx.room.Room.databaseBuilder(context,AppDatabase::class.java,db.openHelper.databaseName!!).build()
    try {assertEquals(operation,reopened.pendingOperationDao().getOperation(operation.operationId));
     assertEquals(desired.name,reopened.customerDao().getCustomerById(ws,originalCustomer.id)!!.name)} finally {reopened.close()}
    val independentActionTime=System.currentTimeMillis()
    ServerActionTimeBarrier({FirestoreServerTime.read(ws,"device-partner",app)}).awaitEligible(independentActionTime)
    otherCloud.document("workspaces/$ws/customers/${originalCustomer.id}").update(mapOf(
     "phone" to "9876543210","actorUid" to "device-partner","operationId" to "independent-phone-$now",
     "actionTime" to independentActionTime,"protocolVersion" to 2,"operationType" to "UPSERT","dependencies" to emptyList<Any>(),"isDeleted" to false)).await()
   } finally {cloud.enableNetwork().await()}
   val result=workspace.pushUnsyncedToCloud(isOnline=true)
   android.util.Log.i("CUSTOMER_EDIT_TEST","profile reconnect: $result; "+db.pendingOperationDao().getOperation(operation.operationId))
   awaitCustomerCommand(db,operation,"profile reconnect")
   val confirmed=cloud.document("workspaces/$ws/customers/${originalCustomer.id}").get(Source.SERVER).await()
   assertEquals(desired.name,confirmed.getString("name"));assertEquals("9876543210",confirmed.getString("phone"))
   for(key in listOf("createdAt","createdByUid","createdByRole","authorizationCreatedAt","serverReceivedAt","authorizationTimeStatus"))
    assertEquals(key,originalCloud[key],confirmed.data!![key])
   assertEquals(confirmed.data,otherCloud.document(confirmed.reference.path).get(Source.SERVER).await().data)
   val room=db.customerDao().getCustomerById(ws,originalCustomer.id)!!
   assertTrue(room.isSynced);assertEquals(desired.name,room.name);assertEquals("9876543210",room.phone)
   assertEquals(800.0,com.example.ui.util.FinancialCalculationEngine.calculateCustomerFinancials(room,listOf(originalJob)).balanceDue,0.0)
   assertEquals(originalReceipt.data,cloud.document(originalReceipt.reference.path).get(Source.SERVER).await().data)
   assertEquals(originalJob.customerName,cloud.document("workspaces/$ws/entries/$id").get(Source.SERVER).await().getString("customerName"))
   FirestoreRepository(context).applyPendingOperation(operation)
   assertEquals(confirmed.data,cloud.document(confirmed.reference.path).get(Source.SERVER).await().data)
   assertEquals(operation.payloadJson,db.pendingOperationDao().getOperation(operation.operationId)!!.payloadJson)
  } finally {cloud.enableNetwork().await();otherCloud.terminate().await()}
 }

 @Test(timeout=180000) fun qOfflinePaidReassignmentKeepsReceiptAndAllowsAuthorizedReversal()=runBlocking {
  login("owner");val db=AppDatabase.getInstance(context);val authRepo=com.example.data.repository.AuthRepository.getInstance(context);authRepo.startListening()
  val profile=kotlinx.coroutines.withTimeout(15000){authRepo.currentUserProfile.first{it?.uid=="device-owner"}}!!
  val workspace=com.example.data.repository.WorkspaceRepository.getInstance(context,db);assertTrue(workspace.initializeForUser(profile).isSuccess)
  for(paid in listOf(200.0,1000.0)) {
  val now=System.currentTimeMillis()
  val id=workspace.saveJobEntry(com.example.data.entity.JobEntryEntity(workspaceId=ws,customerName="Source move $now",operatorName="Owner",
   tractorId=3,tractorLabel="Disposable Tractor",workType="Rotavator",startTimeMillis=now-60000,endTimeMillis=now,durationMinutes=1,
   totalAmount=1000.0,amountReceived=paid,pendingAmount=1000.0-paid,addedByPartner="Owner",createdAt=now,entryForUid="device-owner"),
   linkedExpenses=listOf(com.example.data.entity.ExpenseEntity(workspaceId=ws,expenseType="Diesel",amount=900.0,
    tractorLabel="Disposable Tractor",addedByPartner="Owner",paidBy="Business Paid")))
  workspace.pushUnsyncedToCloud(isOnline=true)
  awaitSyncedJob(db,id,"initial reassignment job")
  val original=db.jobEntryDao().getJobById(ws,id)!!
  val originalExpenses=db.expenseDao().getExpensesForJob(ws,id)
  val expenseCloud=cloud.document("workspaces/$ws/expenses/${originalExpenses.single().id}").get(Source.SERVER).await().data!!
  val receipt=cloud.collection("workspaces/$ws/collections").whereEqualTo("jobEntryId",id).get(Source.SERVER).await().documents.single()
  val targetId=com.example.data.util.IdGenerator.generateId();val op:PendingOperationEntity
  cloud.disableNetwork().await()
  try {
   workspace.saveJobEntry(original.copy(customerId=targetId,customerName="Target move $now"),linkedExpenses=originalExpenses,createNewCustomer=true)
   op=db.pendingOperationDao().observe(ws).first().last{it.entityType=="JOB" && it.entityId==id}
   assertEquals("PENDING",op.state)
   assertEquals(original.copy(customerId=targetId,customerName="Target move $now").totalAmount,db.jobEntryDao().getJobById(ws,id)!!.totalAmount,0.0)
   assertEquals(1000.0-paid,db.customerDao().getCustomerById(ws,targetId)!!.balanceDue,0.0)
   assertEquals(originalExpenses,db.expenseDao().getExpensesForJob(ws,id))
   assertEquals(0.0,db.customerDao().getCustomerById(ws,original.customerId)!!.balanceDue,0.0)
  } finally {cloud.enableNetwork().await()}
  val result=workspace.pushUnsyncedToCloud(isOnline=true)
  android.util.Log.i("CUSTOMER_EDIT_TEST","move reconnect: $result; "+db.pendingOperationDao().observe(ws).first().filter{it.entityId in setOf(id,targetId)})
  awaitCustomerCommand(db,op,"move reconnect")
  val confirmed=cloud.document("workspaces/$ws/entries/$id").get(Source.SERVER).await()
  assertEquals(targetId,confirmed.getLong("customerId"));assertEquals(original.createdAt,confirmed.getLong("createdAt"))
  assertEquals(original.createdByUid,confirmed.getString("createdByUid"));assertEquals(original.entryForUid,confirmed.getString("entryForUid"))
  assertEquals(receipt.data,cloud.document(receipt.reference.path).get(Source.SERVER).await().data)
  assertEquals(expenseCloud,cloud.document("workspaces/$ws/expenses/${originalExpenses.single().id}").get(Source.SERVER).await().data)
  val audit=(confirmed.get("customerReassignmentHistory") as List<*>).single() as Map<*,*>
  assertEquals(original.customerId,(audit["fromCustomerId"] as Number).toLong());assertEquals(targetId,(audit["toCustomerId"] as Number).toLong())
  assertEquals(op.operationId,audit["operationId"])
  FirestoreRepository(context).applyPendingOperation(op)
  assertEquals(confirmed.data,cloud.document(confirmed.reference.path).get(Source.SERVER).await().data)
  workspace.deletePayment(receipt.id.toLong());workspace.pushUnsyncedToCloud(isOnline=true)
  kotlinx.coroutines.withTimeout(60000){db.paymentDao().getPaymentsForWorkspace(ws).first{rows->rows.any{it.id==receipt.id.toLong() && it.status=="REVERSED" && it.isSynced}}}
  val reversed=cloud.document(receipt.reference.path).get(Source.SERVER).await()
  assertEquals("REVERSED",reversed.getString("status"));assertEquals(receipt.getLong("customerId"),reversed.getLong("customerId"))
  assertEquals(receipt.getDouble("amount"),reversed.getDouble("amount"))
  assertEquals(1000.0,cloud.document("workspaces/$ws/entries/$id").get(Source.SERVER).await().getDouble("pendingAmount")!!,0.0)
  }
 }

 @Test(timeout=120000) fun oZeroInitialReceiptCreatesNoPaymentRowOrCommand()=runBlocking {
  login("partner");val actor="device-partner";val db=AppDatabase.getInstance(context)
  val authRepo=com.example.data.repository.AuthRepository.getInstance(context);authRepo.startListening()
  val profile=kotlinx.coroutines.withTimeout(15000){authRepo.currentUserProfile.first{it?.uid==actor}}!!
  val workspace=com.example.data.repository.WorkspaceRepository.getInstance(context,db)
  assertTrue(workspace.initializeForUser(profile).isSuccess)
  val now=System.currentTimeMillis();val id:Long;val original:PendingOperationEntity
  cloud.disableNetwork().await()
  try {
   id=workspace.saveJobEntry(com.example.data.entity.JobEntryEntity(workspaceId=ws,customerName="Zero receipt synthetic "+now,
    operatorName="Selected Partner",tractorId=3,tractorLabel="Disposable Tractor",workType="Rotavator",
    startTimeMillis=now-60000,endTimeMillis=now,durationMinutes=1,totalAmount=1100.0,amountReceived=0.0,
    pendingAmount=1100.0,addedByPartner="Recorder",createdAt=now,entryForUid="device-owner"))
   val commands=db.pendingOperationDao().observeActor(ws,actor).first()
   original=commands.single{it.entityType=="JOB" && it.entityId==id}
   assertTrue(commands.none{it.entityType=="PAYMENT" && JSONObject(it.payloadJson).optLong("jobEntryId")==id})
   assertTrue(db.paymentDao().getPaymentsForWorkspace(ws).first().none{it.jobEntryId==id})
  } finally {cloud.enableNetwork().await()}
  workspace.pushUnsyncedToCloud(isOnline=true)
  kotlinx.coroutines.withTimeout(60000){db.pendingOperationDao().observe(ws).first{rows->rows.any{it.operationId==original.operationId && it.state=="ACKNOWLEDGED"}}}
  val source=cloud.document("workspaces/$ws/entries/$id").get(Source.SERVER).await()
  assertEquals(0.0,source.getDouble("amountReceived")!!,0.0);assertEquals(1100.0,source.getDouble("pendingAmount")!!,0.0)
  assertEquals(0,cloud.collection("workspaces/$ws/collections").whereEqualTo("jobEntryId",id).get(Source.SERVER).await().size())
 }
 @Test(timeout=90000) fun rFutureClockPreservesPendingRoomJobAndBlocksOnlyItsPayment()=runBlocking {
  login("owner");val actor="device-owner";val db=AppDatabase.getInstance(context)
  val authRepo=com.example.data.repository.AuthRepository.getInstance(context);authRepo.startListening()
  val profile=kotlinx.coroutines.withTimeout(15000){authRepo.currentUserProfile.first{it?.uid==actor}}!!
  val workspace=com.example.data.repository.WorkspaceRepository.getInstance(context,db);assertTrue(workspace.initializeForUser(profile).isSuccess)
  val future=FirestoreServerTime.read(ws,actor)+3600000L;val id=com.example.data.util.IdGenerator.generateId()
  val job=jobEntryFromFirestoreMap(cloud.document("workspaces/$ws/entries/50").get(Source.SERVER).await().data!!)
   .copy(id=id,createdAt=future,totalAmount=700.0,amountReceived=0.0,pendingAmount=700.0,isSynced=false)
  val op=PendingOperationEntity("future-clock-"+java.util.UUID.randomUUID(),ws,actor,"JOB",id,"UPSERT",db.pendingOperationDao().nextSequence(),future,
   JSONObject(job.toFirestoreMap()).put("operationProtocolVersion",2).toString())
  val receipt=com.example.data.entity.PaymentEntity(id=com.example.data.util.IdGenerator.generateId(),workspaceId=ws,jobEntryId=id,
   customerId=job.customerId,customerName=job.customerName,amount=125.0,collectedByUid=actor,collectedByRole="OWNER",createdAt=future,collectedAt=future,isSynced=false)
  db.jobEntryDao().insertJob(job);db.pendingOperationDao().insert(op);db.paymentDao().insertPayment(receipt)
  val payment=PendingOperationEntity("future-payment-"+java.util.UUID.randomUUID(),ws,actor,"PAYMENT",receipt.id,"UPSERT",db.pendingOperationDao().nextSequence(),future,
   JSONObject(receipt.toFirestoreMap()).put("operationProtocolVersion",2).toString())
  db.pendingOperationDao().insert(payment)
  workspace.pushUnsyncedToCloud(isOnline=true)
  val pending=db.pendingOperationDao().getOperation(op.operationId)!!
  assertEquals("PENDING",pending.state);assertTrue(pending.failureReason.contains("CLOCK_AHEAD"))
  assertEquals(op.payloadJson,pending.payloadJson);assertEquals(op.actionTime,pending.actionTime)
  assertEquals(job,db.jobEntryDao().getJobById(ws,id));assertEquals(receipt,db.paymentDao().getPaymentById(ws,receipt.id))
  val blocked=db.pendingOperationDao().getOperation(payment.operationId)!!
  assertEquals("PENDING",blocked.state);assertEquals(0,blocked.attempts);assertEquals(payment.payloadJson,blocked.payloadJson)
  assertTrue(blocked.failureReason.contains("earlier related action"))
  assertFalse(cloud.document("workspaces/$ws/entries/$id").get(Source.SERVER).await().exists())
  assertFalse(cloud.document("workspaces/$ws/collections/${receipt.id}").get(Source.SERVER).await().exists())
 }
 @Test(timeout=90000) fun sSmallFutureClockConfirmsOriginalJobExactlyOnce()=runBlocking {
  login("owner");val actor="device-owner";val db=AppDatabase.getInstance(context)
  val authRepo=com.example.data.repository.AuthRepository.getInstance(context);authRepo.startListening()
  val profile=kotlinx.coroutines.withTimeout(15000){authRepo.currentUserProfile.first{it?.uid==actor}}!!
  val workspace=com.example.data.repository.WorkspaceRepository.getInstance(context,db);assertTrue(workspace.initializeForUser(profile).isSuccess)
  val future=FirestoreServerTime.read(ws,actor)+200L
  val id=com.example.data.util.IdGenerator.generateId()
  val job=jobEntryFromFirestoreMap(cloud.document("workspaces/$ws/entries/50").get(Source.SERVER).await().data!!)
   .copy(id=id,createdAt=future,totalAmount=700.0,amountReceived=0.0,pendingAmount=700.0)
  val op=PendingOperationEntity("small-clock-"+java.util.UUID.randomUUID(),ws,actor,"JOB",id,"UPSERT",db.pendingOperationDao().nextSequence(),future,
   JSONObject(job.toFirestoreMap()).put("operationProtocolVersion",2).toString())
  db.jobEntryDao().insertJob(job.copy(isSynced=false));db.pendingOperationDao().insert(op)
  workspace.pushUnsyncedToCloud(isOnline=true);awaitCustomerCommand(db,op,"small clock original job")
  val ref=cloud.document("workspaces/$ws/entries/$id");val first=ref.get(Source.SERVER).await()
  assertEquals(op.operationId,first.getString("operationId"));assertEquals(future,first.getLong("createdAt"))
  assertEquals(future,first.getLong("actionTime"));assertEquals(actor,first.getString("createdByUid"))
  assertTrue(first.getTimestamp("serverReceivedAt")!!.toDate().time>=future)
  assertEquals(700.0,first.getDouble("totalAmount")!!,0.0);assertEquals(0.0,first.getDouble("amountReceived")!!,0.0)
  assertTrue(db.jobEntryDao().getJobById(ws,id)!!.isSynced)
  val ack=db.pendingOperationDao().getOperation(op.operationId)!!
  assertEquals(op.payloadJson,ack.payloadJson);assertEquals(future,ack.actionTime)
  FirestoreRepository(context).applyPendingOperation(op);assertEquals(first.data,ref.get(Source.SERVER).await().data)
  assertEquals(1,cloud.collection("workspaces/$ws/entries").whereEqualTo("operationId",op.operationId).get(Source.SERVER).await().size())
  db.pendingOperationDao().observe(ws).first().firstOrNull{it.operationId.startsWith("future-clock-") }?.let{
   assertEquals("Unrelated future job must stay pending", "PENDING",it.state)
   assertTrue(it.failureReason.contains("CLOCK_AHEAD"))
  }
 }
}
