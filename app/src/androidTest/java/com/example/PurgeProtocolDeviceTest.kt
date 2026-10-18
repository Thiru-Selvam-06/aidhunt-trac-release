package com.example
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.firebase.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class PurgeProtocolDeviceTest {
 @Test(timeout=120000) fun retainedLegacyEntryPurgeAndRetry()=runBlocking {
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  check(BuildConfig.DEVICE_TEST && context.packageName=="com.aistudio.aidhunttrac.cedlir.devicetest")
  check(FirebaseApp.getInstance().options.projectId=="demo-aidhunt-v16")
  val auth=FirebaseAuth.getInstance();auth.signInWithEmailAndPassword("owner@device.invalid","EmulatorOnly123!").await();assertEquals("device-owner",auth.currentUser!!.uid)
  val cloud=FirebaseFirestore.getInstance();val ws="purge-v2-device-20261013";val target="purge-v2-removed"
  val repo=FirestoreRepository(context);val entry=cloud.document("workspaces/$ws/entries/1");val original=entry.get(Source.SERVER).await().data!!
  repo.archiveMember(ws,target)
  val backup=repo.permanentlyDeleteMember(ws,target)
  val after=entry.get(Source.SERVER).await().data!!
  assertEquals(10.0,(after["amountReceived"] as Number).toDouble(),0.0);assertEquals(90.0,(after["pendingAmount"] as Number).toDouble(),0.0)
  for(key in listOf("createdAt","authorizationCreatedAt","financialAttributionUid","entryForUid","createdByUid","totalAmount"))assertEquals(key,original[key],after[key])
  assertEquals(2L,after["protocolVersion"]);assertEquals("UPSERT",after["operationType"])
  val customer=cloud.document("workspaces/$ws/customers/2").get(Source.SERVER).await()
  assertEquals(10.0,customer.getDouble("totalPaid")!!,0.0);assertEquals(90.0,customer.getDouble("balanceDue")!!,0.0)
  assertFalse(cloud.document("workspaces/$ws/collections/3").get(Source.SERVER).await().exists())
  assertEquals("ACTIVE",cloud.document("workspaces/$ws/collections/4").get(Source.SERVER).await().getString("status"))
  assertEquals("REVERSED",cloud.document("workspaces/$ws/collections/5").get(Source.SERVER).await().getString("status"))
  val saved=org.json.JSONObject(java.io.File(backup).readText());assertEquals(ws,saved.getString("workspaceId"));assertEquals(3,saved.getJSONArray("collections").length())
  repo.permanentlyDeleteMember(ws,target);assertEquals(after,entry.get(Source.SERVER).await().data);assertEquals(customer.data,cloud.document("workspaces/$ws/customers/2").get(Source.SERVER).await().data)
  val state=cloud.document("workspaces/$ws/deletedUsers/$target").get(Source.SERVER).await();assertEquals(state.getLong("purgeActionTime"),after["actionTime"]);assertEquals(state.getString("purgeOperationId")+":1",after["operationId"])
  repo.completeMemberPurge(ws,target);assertEquals("PURGED",cloud.document("workspaces/$ws/deletedUsers/$target").get(Source.SERVER).await().getString("purgeState"))
  android.util.Log.i("PURGE_V2_DEVICE","PASS retained Entry paid=10 due=90; Customer paid=10 due=90; history backup verified; duplicate unchanged")
 }
}
