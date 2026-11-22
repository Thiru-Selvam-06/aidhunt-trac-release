package com.example

import android.content.*
import android.os.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.firebase.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Two Android SDK processes; the peer is genuinely killed/restarted, not FirebaseApp.delete(). */
@RunWith(AndroidJUnit4::class)
class CashBalanceCloudDeviceTest {
    private class Peer(private val context: Context) {
        private val connected=CompletableDeferred<IBinder>()
        private var response=CompletableDeferred<Bundle>()
        private val inbox=Messenger(Handler(Looper.getMainLooper()) { message ->
            val data=message.data
            if(data.containsKey("error")) response.completeExceptionally(AssertionError(data.getString("error"))) else response.complete(data)
            true
        })
        private val connection=object:ServiceConnection {
            override fun onServiceConnected(name:ComponentName,binder:IBinder) { connected.complete(binder) }
            override fun onServiceDisconnected(name:ComponentName) {}
        }
        suspend fun bind() {
            check(context.bindService(Intent().setClassName(context.packageName,"com.example.CashCachePeerService"),connection,
                Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT))
            withTimeout(30000) { connected.await() }
        }
        suspend fun call(command:Int, args:Bundle.()->Unit={}):Bundle {
            response=CompletableDeferred()
            Messenger(connected.await()).send(Message.obtain().apply { what=command;data=Bundle().apply(args);replyTo=inbox })
            return withTimeout(65000) { response.await() }
        }
        suspend fun kill(ws:String) {
            val pid=call(5) { putString("ws",ws) }.getInt("pid")
            check(pid>0 && pid!=Process.myPid())
            val died=CompletableDeferred<Unit>()
            connected.await().linkToDeath({ died.complete(Unit) },0)
            context.unbindService(connection)
            Process.killProcess(pid)
            withTimeout(15000) { died.await() }
        }
        fun unbind() = context.unbindService(connection)
    }
    @Test fun twoAuthenticatedClientsConfirmOneAdjustmentRetainOfflineCashAndRejectStaleEdits() = runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        check(BuildConfig.DEVICE_TEST && context.packageName.endsWith(".devicetest"))
        val options=FirebaseApp.getInstance().options;check(options.projectId=="demo-aidhunt-v16")
        val suffix=java.util.UUID.randomUUID().toString().replace("-","")
        val ws="ws_cash_native_$suffix";val peerName="cash-partner-$suffix"
        val ownerApp=FirebaseApp.initializeApp(context,options,"cash-owner-$suffix")
        val ownerAuth=FirebaseAuth.getInstance(ownerApp).apply { useEmulator("127.0.0.1",19099) }
        val ownerDb=FirebaseFirestore.getInstance(ownerApp).apply { useEmulator("127.0.0.1",18180) }
        val peer=Peer(context);peer.bind();var peerBound=true
        try {
            val ownerUid=ownerAuth.createUserWithEmailAndPassword("owner-$suffix@example.test","SyntheticOnly123!").await().user!!.uid
            val partnerUid=peer.call(1) { putString("app",peerName);putBoolean("create",true);putString("email","partner-$suffix@example.test") }.getString("uid")!!
            fun seed(collection:String?,id:String?,values:Map<String,Any?>) {
                val name="projects/demo-aidhunt-v16/databases/(default)/documents/workspaces/$ws"+
                    if(collection==null) "" else "/$collection/$id"
                val body=JSONObject().put("fields",FirestoreValueCodec.encode(values).getJSONObject("mapValue").getJSONObject("fields"))
                val request=okhttp3.Request.Builder().url("http://127.0.0.1:18180/v1/$name").header("Authorization","Bearer owner")
                    .patch(body.toString().toRequestBody("application/json".toMediaType())).build()
                okhttp3.OkHttpClient().newCall(request).execute().use { assertTrue("Synthetic fixture status ${it.code}",it.isSuccessful) }
            }
            seed(null,null,mapOf("workspaceId" to ws,"ownerUid" to ownerUid,"syncCapabilities" to mapOf("cashBalanceAdjustmentsV1" to true)))
            for((uid,role,name) in listOf(Triple(ownerUid,"OWNER","Synthetic Owner"),Triple(partnerUid,"PARTNER","Synthetic Partner")))
                seed("members",uid,mapOf("uid" to uid,"workspaceId" to ws,"role" to role,"status" to "ACTIVE","displayName" to name))
            seed("entries","123",mapOf("workspaceId" to ws,"totalAmount" to 2000,"amountReceived" to 1000,"pendingAmount" to 1000))
            seed("collections","1",mapOf("workspaceId" to ws,"jobEntryId" to 123,"amount" to 1000,"paymentMethod" to "Cash","status" to "ACTIVE"))
            val owner=CashBalanceAdjustmentRepository(ownerDb,ownerAuth);val operation="cash-$suffix"
            suspend fun savePeer()=peer.call(2) { putString("ws",ws);putString("operation",operation);putLong("revision",0)
                putLong("target",150000);putLong("base",100000);putString("reason","Synthetic cash count") }
            assertEquals(50000L,savePeer().getLong("total"))
            val first=withTimeout(60000) { owner.observe(ws,ownerUid).first { it.confirmed && it.revision==1L } }
            assertEquals(partnerUid,first.edits.single().actorUid);assertEquals(operation,first.edits.single().id)
            assertEquals(50000L,owner.readTotal(ws,ownerUid));savePeer()
            assertEquals(1,ownerDb.collection("workspaces").document(ws).collection("cashBalanceAdjustments").get().await().size())
            // An independent new job/receipt changes actual source cash before the next correction.
            seed("entries","124",mapOf("workspaceId" to ws,"totalAmount" to 200,"amountReceived" to 200,"pendingAmount" to 0))
            seed("collections","2",mapOf("workspaceId" to ws,"jobEntryId" to 124,"amount" to 200,"paymentMethod" to "Cash","status" to "ACTIVE"))
            assertTrue(runCatching { owner.save(ws,ownerUid,"stale-$suffix",0,160000,120000,"Stale revision") }.isFailure)
            owner.save(ws,ownerUid,"second-$suffix",1,160000,120000,"Second verified cash count")
            val remote=peer.call(3) { putString("ws",ws);putLong("revision",2) }
            assertEquals(40000L,remote.getLong("total"));assertEquals(2,remote.getInt("count"))
            assertEquals(partnerUid,remote.getString("firstActor"));assertEquals(160000L,remote.getLong("cash"))
            assertEquals(1000L,ownerDb.collection("workspaces").document(ws).collection("entries").document("123").get().await().getLong("amountReceived"))
            val sourceCash=ownerDb.collection("workspaces").document(ws).collection("collections").get().await().documents.sumOf { it.getDouble("amount")!! }
            assertEquals(1600.0,sourceCash+owner.readTotal(ws,ownerUid)/100.0,0.0)
            assertEquals(40000L,peer.call(4) { putString("ws",ws) }.getLong("total"))
            peer.kill(ws);peerBound=false
            val reopened=Peer(context);reopened.bind()
            try {
                val restored=reopened.call(1) { putString("app",peerName) }
                assertEquals(partnerUid,restored.getString("uid"))
                val cached=reopened.call(4) { putString("ws",ws) }
                assertEquals(40000L,cached.getLong("total"));assertEquals(2,cached.getInt("count"));assertFalse(cached.getBoolean("confirmed"))
                assertTrue(reopened.call(6) { putString("ws",ws) }.getBoolean("denied"))
                reopened.kill(ws)
            } catch(error:Throwable) { reopened.unbind();throw error }
        } finally {
            if(peerBound) peer.unbind()
            ownerDb.terminate().await()
            // Test process teardown, rather than deleting a live FirebaseApp, ends Auth listeners.
        }
    }
}
