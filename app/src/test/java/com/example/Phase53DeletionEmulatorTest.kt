package com.example

import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PendingOperationEntity
import com.example.data.firebase.*
import java.io.File
import java.util.Base64
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Actual guarded Android deletion transport against the separately staged policy. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class Phase53DeletionEmulatorTest {
    private val client = okhttp3.OkHttpClient()
    private val project = "demo-aidhunt-v16"
    private val root = "projects/$project/databases/(default)/documents"
    private val now = System.currentTimeMillis() - 1000
    private var loaded = false
    private fun name(col: String, id: Any) = "$root/workspaces/ws_test/$col/$id"
    private fun rpc(path: String, method: String, body: JSONObject? = null, token: String = "owner"): Pair<Int,String> {
        val request = okhttp3.Request.Builder().url("http://127.0.0.1:18180/$path")
            .header("Authorization", "Bearer $token").method(method,
                if (method in setOf("GET", "DELETE")) null else (body ?: JSONObject()).toString().toRequestBody("application/json".toMediaType()))
        return client.newCall(request.build()).execute().use { it.code to it.body?.string().orEmpty() }
    }
    private fun jwt(uid: String): String {
        fun enc(text: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray())
        return enc("""{"alg":"none","typ":"JWT"}""") + "." + enc(JSONObject().put("sub",uid).put("user_id",uid)
            .put("aud",project).put("iss","https://securetoken.google.com/$project")
            .put("iat",now/1000).put("auth_time",now/1000).put("exp",now/1000+3600).toString()) + "."
    }
    private fun rules(file: String) {
        val source = listOf(File(file), File("../$file")).first { it.isFile }.readText()
        val result = rpc("emulator/v1/projects/$project:securityRules", "PUT", JSONObject().put("rules",
            JSONObject().put("files",JSONArray().put(JSONObject().put("content",source)))))
        assertEquals(result.second,200,result.first)
    }
    private fun seed(col: String, id: Any, values: Map<String,Any?>) {
        val fields = JSONObject().apply { values.forEach { (k,v) -> put(k,FirestoreValueCodec.encode(v)) } }
        val result=rpc("v1/${name(col,id)}", "PATCH", JSONObject().put("fields",fields))
        assertEquals(result.second,200,result.first)
    }
    private fun source(old: Boolean) = JobEntryEntity(id=1,workspaceId="ws_test",customerId=2,
        customerName="Synthetic",operatorName="Partner",tractorLabel="T",workType="Work",startTimeMillis=1,endTimeMillis=2,
        durationMinutes=1,totalAmount=100.25,amountReceived=0.0,pendingAmount=100.25,addedByPartner="other",
        createdAt=now-365*86400000L,createdByUid="other",entryForUid="partner",financialAttributionUid="partner",isHistorical=old)
    private fun op(job: JobEntryEntity)=PendingOperationEntity("original-delete","ws_test","partner","JOB",1,"DELETE",1,now,
        JSONObject(job.toFirestoreMap()).put("operationProtocolVersion",2).put("linkedExpenseIds",JSONArray())
            .put("linkedCollectionIds",JSONArray()).put("linkedExpenseSnapshots",JSONArray()).toString())
    @Before fun isolatedEmulatorOnly() {
        assumeTrue(System.getenv("FIRESTORE_EMULATOR_HOST")=="127.0.0.1:18180" && System.getenv("GCLOUD_PROJECT")==project && System.getenv("GOOGLE_APPLICATION_CREDENTIALS").isNullOrBlank())
        rules("firestore.phase53.rules");loaded=true
        assertEquals(200,rpc("emulator/v1/projects/$project/databases/(default)/documents","DELETE").first)
        val result=rpc("v1/$root/workspaces/ws_test","PATCH",JSONObject().put("fields",JSONObject()
            .put("workspaceId",FirestoreValueCodec.encode("ws_test")).put("ownerUid",FirestoreValueCodec.encode("owner"))))
        assertEquals(result.second,200,result.first)
        for((uid,role) in listOf("owner" to "OWNER","partner" to "PARTNER","other" to "PARTNER"))
            seed("members",uid,mapOf("uid" to uid,"role" to role,"status" to "ACTIVE"))
        seed("customers",2,mapOf("id" to 2L,"workspaceId" to "ws_test","createdByUid" to "owner","createdAt" to now-365*86400000L,
            "name" to "Synthetic","phone" to "","isDeleted" to false,"totalBilled" to 100.25,"totalPaid" to 0.0,"balanceDue" to 100.25))
    }
    @After fun restoreStandardPolicy() { if(loaded) rules("firestore.rules") }
    @Test fun guardedCrossCreatorYearOldNewAndOldDeletionConfirmsOriginalOperationExactlyOnce()=runBlocking {
        for(old in listOf(false,true)) {
            if(old) isolatedEmulatorOnly()
            val job=source(old);seed("entries",1,job.toFirestoreMap()+mapOf("protocolVersion" to 2L,"authorizationTimeStatus" to "CLIENT_ORIGINAL_V2"))
            val command=op(job)
            val service=FirebaseLedgerDeletion(FirebaseLedgerDeletion.HttpTransport(project,"http://127.0.0.1:18180",jwt("partner")) {},true)
            service.execute(command);service.execute(command)
            assertEquals(404,rpc("v1/${name("entries",1)}","GET").first)
            val audit=JSONObject(rpc("v1/${name("entryDeletionProofs",1)}","GET").second).getJSONObject("fields")
            assertEquals("original-delete",audit.getJSONObject("operationId").getString("stringValue"))
            assertEquals(now.toString(),audit.getJSONObject("actionTime").getString("integerValue"))
            assertEquals("partner",audit.getJSONObject("actorUid").getString("stringValue"))
            assertEquals(404,rpc("v1/${name("ledgerDeletionLocks","ENTRY_1")}","GET").first)
            val customer=JSONObject(rpc("v1/${name("customers",2)}","GET").second).getJSONObject("fields")
            assertEquals(0.0,(FirestoreValueCodec.decode(customer.getJSONObject("balanceDue")) as Number).toDouble(),0.0)
        }
    }
}
