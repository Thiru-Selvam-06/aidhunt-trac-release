package com.example

import com.example.data.firebase.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import okio.Buffer
import java.io.IOException
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
class WorkspaceCommandProtocolTest {
    private fun data() = WorkspaceCommandProtocol.entryData("workspace-a", "9223372036854775807", "operation-one", 12345L, "actor")
    private fun success() = JSONObject().put("result", JSONObject(data().toString()).put("deleted",true)).toString()
    private fun customerData() = WorkspaceCommandProtocol.entryData("workspace-a", "9223372036854775807", "operation-customer", 12345L, "actor")
        .apply { remove("entryId"); put("customerId", "9223372036854775807"); put("operationType", "DELETE_CUSTOMER") }
    @Test fun productionRequestUsesCallableEnvelopeAndBearerWithExactLongIdentity() {
        val request = WorkspaceCommandProtocol.request("http://localhost/deleteWorkspaceEntry", "test-token", data())
        assertEquals("Bearer test-token",request.header("Authorization"))
        val buffer=Buffer();request.body!!.writeTo(buffer)
        val json=JSONObject(buffer.readUtf8());assertEquals(1,json.length())
        assertEquals("9223372036854775807",json.getJSONObject("data").getString("entryId"))
        assertFalse(json.getJSONObject("data").has("actorUid"))
        assertEquals("application/json; charset=utf-8",request.body!!.contentType().toString())
    }
    @Test fun structuredUpgradeErrorIsPermanentAndActionable() {
        try{WorkspaceCommandProtocol.verifyResponse(412,"{\"error\":{\"status\":\"FAILED_PRECONDITION\",\"details\":{\"code\":\"UPGRADE_REQUIRED\"}}}",data());fail("Upgrade not detected")}catch(e:WorkspaceUpgradeRequiredException){assertTrue(e.message!!.contains("pending actions"))}
    }
    @Test fun exactServerAcknowledgementSucceeds() { WorkspaceCommandProtocol.verifyResponse(200,success(),data()) }
    @Test fun customerAcknowledgementMustMatchTheQueuedCommand() {
        val command = customerData()
        val acknowledgement = JSONObject(command.toString()).put("deleted", true)
        WorkspaceCommandProtocol.verifyResponse(200, JSONObject().put("result", acknowledgement).toString(), command)
        for(field in listOf("workspaceId", "customerId", "operationId", "actionTime")) {
            for(value in listOf(null, "wrong")) {
                val result = JSONObject(acknowledgement.toString())
                if(value == null) result.remove(field) else result.put(field, value)
                try { WorkspaceCommandProtocol.verifyResponse(200, JSONObject().put("result", result).toString(), command)
                    fail("Unmatched $field acknowledged") } catch (_: IOException) {}
            }
        }
    }
    @Test fun malformedCustomerSuccessRetainsPendingCommand() {
        for(body in listOf("", "{}", "{\"result\":{\"deleted\":true}}", "{\"result\":{\"deleted\":\"true\"}}")) {
            try { WorkspaceCommandProtocol.verifyResponse(200, body, customerData()); fail("Malformed acknowledgement") }
            catch (_: IOException) {}
        }
    }
    @Test fun fractionalActionTimeCannotAcknowledgeAnIntegerTimestamp() {
        val result = JSONObject(customerData().toString()).put("deleted", true).put("actionTime", 12345.9)
        try { WorkspaceCommandProtocol.verifyResponse(200, JSONObject().put("result", result).toString(), customerData())
            fail("Fractional action time acknowledged") } catch (_: IOException) {}
    }
    @Test fun eachMismatchedIdentityRemainsPending() {
        for(field in listOf("workspaceId","entryId","operationId","actionTime")) {
            val result=JSONObject(data().toString()).put("deleted",true).put(field,"wrong")
            try { WorkspaceCommandProtocol.verifyResponse(200,JSONObject().put("result",result).toString(),data());fail("Unmatched acknowledgement") } catch (_: IOException) {}
        }
    }
    @Test fun ambiguousAndCoercedAcknowledgementsRemainPending() {
        for(body in listOf("", "not-json", "{}", "{\"result\":{\"deleted\":\"true\"}}", "{\"result\":{\"deleted\":false}}")) {
            try { WorkspaceCommandProtocol.verifyResponse(200,body,data());fail("Ambiguous acknowledgement") } catch (_: IOException) {}
        }
    }
    @Test fun unauthenticatedResponseRequiresLoginAndPreservesQueue() {
        try { WorkspaceCommandProtocol.verifyResponse(401,"{\"error\":{\"status\":\"UNAUTHENTICATED\"}}",data());fail("Expected authentication state") }
        catch (e: WorkspaceAuthenticationRequiredException) { assertTrue(e.message!!.contains("Sign in again")) }
    }
    @Test fun permanentServerRejectionsReachRecoveryWithActionableReason() {
        for(status in listOf("PERMISSION_DENIED","FAILED_PRECONDITION","INVALID_ARGUMENT")) {
            try { WorkspaceCommandProtocol.verifyResponse(403,JSONObject().put("error",JSONObject().put("status",status).put("message","Reverse active Collections first")).toString(),data());fail("Expected rejection") }
            catch (e: IllegalArgumentException) { assertTrue(e.message!!.contains("HTTP 403 $status")); assertTrue(e.message!!.contains("Reverse active Collections first")) }
        }
    }
    @Test fun temporaryFailureAndUnknownResponseDoNotAcknowledge() {
        for(code in listOf(404,429,500,503)) {
            try { WorkspaceCommandProtocol.verifyResponse(code,"{}",data());fail("Expected retry") } catch (_: IOException) {}
        }
    }
    @Test fun missingTokenAndChangedSessionRemainRecoverable() {
        try { WorkspaceCommandProtocol.request("http://localhost/", "",data());fail("Missing token") } catch (_: WorkspaceAuthenticationRequiredException) {}
        assertTrue(WorkspaceSessionChangedException() is IOException)
    }
    @Test fun expiredTokenRefreshesExactlyOnceAndReusesOriginalRequest() = runBlocking {
        val refreshes=mutableListOf<Boolean>();val tokens=mutableListOf<String>()
        WorkspaceCommandProtocol.withAuthenticatedToken("actor", { "actor" }, { refresh -> refreshes.add(refresh); if(refresh) "fresh" else "expired" }) { token ->
            tokens.add(token); if(token=="expired") throw WorkspaceAuthenticationRequiredException()
        }
        assertEquals(listOf(false,true),refreshes);assertEquals(listOf("expired","fresh"),tokens)
    }
    @Test fun repeatedAuthenticationFailureStopsAfterOneRefresh() = runBlocking {
        val refreshes=mutableListOf<Boolean>()
        try { WorkspaceCommandProtocol.withAuthenticatedToken("actor", { "actor" }, { refresh -> refreshes.add(refresh); "invalid" }) { throw WorkspaceAuthenticationRequiredException() };fail("Expected login state") }
        catch (_: WorkspaceAuthenticationRequiredException) {}
        assertEquals(listOf(false,true),refreshes)
    }
    @Test fun tokenFetchCannotReassignOperationDuringAccountSwitch() = runBlocking {
        var uid="actor";var sent=false
        try { WorkspaceCommandProtocol.withAuthenticatedToken("actor", { uid }, { uid="other"; "test-token" }) { sent=true };fail("Expected session change") }
        catch (_: WorkspaceSessionChangedException) {}
        assertFalse(sent)
    }
    @Test fun wrongOrMissingAccountCannotFetchOrSendToken() = runBlocking {
        var fetched=false
        try { WorkspaceCommandProtocol.withAuthenticatedToken("actor", { "other" }, { fetched=true; "token" }) { fail("Must not send") };fail("Expected wrong-account state") }
        catch (_: WorkspaceSessionChangedException) {}
        assertFalse(fetched)
        try { WorkspaceCommandProtocol.withAuthenticatedToken("actor", { null }, { fetched=true; "token" }) { fail("Must not send") };fail("Expected login") }
        catch (_: WorkspaceAuthenticationRequiredException) {}
        assertFalse(fetched)
    }
    @Test fun permissionRejectionDoesNotRefreshOrAcknowledge() = runBlocking {
        val refreshes=mutableListOf<Boolean>()
        try { WorkspaceCommandProtocol.withAuthenticatedToken("actor", { "actor" }, { refresh -> refreshes.add(refresh); "token" }) { throw IllegalArgumentException("Reverse collections first") };fail("Expected rejection") }
        catch (_: IllegalArgumentException) {}
        assertEquals(listOf(false),refreshes)
    }

    @Test fun absentBackendRouteIsNotPermissionFailureOrAcknowledgement() {
        for (body in listOf("", "{}", "<html>Not Found private server details</html>")) {
            try {
                WorkspaceCommandProtocol.verifyResponse(404, body, data())
                fail("Missing backend acknowledged")
            } catch (e: WorkspaceBackendUnavailableException) {
                assertTrue(e is IOException)
                assertTrue(e.message!!.contains("remains pending"))
                assertFalse(e.message!!.contains("private server details"))
            }
        }
    }
    @Test fun structuredRecordNotFoundIsNotMistakenForAbsentEndpoint() {
        try {
            WorkspaceCommandProtocol.verifyResponse(404,
                JSONObject().put("error", JSONObject().put("status", "NOT_FOUND").put("message", "Record not found")).toString(), data())
            fail("Not-found response acknowledged")
        } catch (e: IOException) {
            assertFalse(e is WorkspaceBackendUnavailableException)
        }
    }

}
