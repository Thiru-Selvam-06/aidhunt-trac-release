package com.example

import com.example.data.firebase.FirestoreQueuedJson
import com.example.data.firebase.JobEditorMetadata
import com.example.data.entity.PendingOperationEntity
import com.google.firebase.firestore.util.CustomClassMapper
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class FirestoreQueuedJsonTest {
    @Test fun actualFirebaseSdkAcceptsNestedHistoryAndKeepsLongIdsAndNulls() {
        val json = JSONObject("""{"id":3759101127671304813,"jobEditHistory":[{"actorUid":"p","operationId":"original","actionTime":179479125656,"before":{"notes":null,"totalAmount":100.25},"after":{"notes":"தமிழ் raw name","totalAmount":100.25}}],"dependencies":[],"allocationsJson":"[{\"jobId\":1,\"amount\":2}]"}""")
        val original = json.toString()
        val native = FirestoreQueuedJson.read(json)
        // Exercise the real SDK serializer rather than a permissive REST test fixture.
        val stored = CustomClassMapper.convertToPlainJavaTypes(native) as Map<*, *>
        assertEquals(3759101127671304813L, stored["id"])
        val events = stored["jobEditHistory"] as List<*>
        val event = events.single() as Map<*, *>
        assertEquals("original", event["operationId"])
        assertNull((event["before"] as Map<*, *>)["notes"])
        assertEquals("தமிழ் raw name", (event["after"] as Map<*, *>)["notes"])
        assertEquals(100.25, (event["after"] as Map<*, *>)["totalAmount"])
        assertTrue((stored["dependencies"] as List<*>).isEmpty())
        assertEquals("[{\"jobId\":1,\"amount\":2}]", stored["allocationsJson"])
        assertEquals(original, json.toString())
    }

    @Test fun nestedPaymentJobSnapshotUsesSdkListsRatherThanJsonObjects() {
        val snapshot = JSONObject("""{"jobEditHistory":[],"notes":"customer text","createdAt":123,"nullable":null}""")
        val stored = CustomClassMapper.convertToPlainJavaTypes(FirestoreQueuedJson.read(snapshot)) as Map<*, *>
        assertTrue(stored["jobEditHistory"] is List<*>)
        assertTrue((stored["jobEditHistory"] as List<*>).isEmpty())
        assertEquals("customer text", stored["notes"]); assertNull(stored["nullable"])
    }

    private fun operation() = PendingOperationEntity("original-edit", "w", "partner", "JOB", 1, "UPSERT", 1, 1234, "{}")
    private fun member() = mapOf<String, Any?>("uid" to "partner", "workspaceId" to "w", "role" to "PARTNER",
        "status" to "ACTIVE", "displayName" to "Current member name")

    @Test fun legacyReplayAddsTheActualEditorWithoutChangingCreatorOrActionTime() {
        val queued = mapOf<String, Any?>("createdByUid" to "owner", "entryForUid" to "another-partner", "createdAt" to 100,
            "totalAmount" to 200.0)
        val original = queued.toMap()
        val metadata = JobEditorMetadata.fields(operation(), queued, member())
        assertEquals("partner", metadata["editedByUid"]); assertEquals("PARTNER", metadata["editedByRole"])
        assertEquals("Current member name", metadata["editedByName"]); assertEquals(1234L, metadata["updatedAt"])
        assertEquals(original, queued)
        assertTrue(metadata.keys.none { it in setOf("createdByUid", "createdByRole", "createdAt", "entryForUid", "totalAmount") })
    }

    @Test fun originalActorBoundNameSurvivesLaterRenameButAnotherPersonsLabelIsNotReused() {
        val queued = mapOf<String, Any?>("editedByUid" to "partner", "editedByName" to "Original action name")
        assertEquals("Original action name", JobEditorMetadata.fields(operation(), queued, member())["editedByName"])
        assertEquals("Current member name", JobEditorMetadata.fields(operation(), queued + ("editedByUid" to "owner"), member())["editedByName"])
        assertEquals("PARTNER", JobEditorMetadata.fields(operation(), emptyMap(), member() - "uid" - "workspaceId")["editedByRole"])
    }

    @Test fun missingRemovedForeignOrInvalidEditorMembershipDoesNotCreateAnAudit() {
        for (bad in listOf(emptyMap(), member() + ("status" to "REMOVED"), member() + ("uid" to "foreign"),
            member() + ("workspaceId" to "foreign"), member() + ("role" to "Co-Owner"))) {
            try { JobEditorMetadata.fields(operation(), emptyMap(), bad); fail("Invalid editor membership accepted") }
            catch (_: SecurityException) {}
        }
    }
}
