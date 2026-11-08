package com.example

import com.example.data.entity.CustomerEntity
import com.example.data.entity.PendingOperationEntity
import com.example.data.firebase.*
import org.junit.Assert.*
import org.junit.Test

class BasicCustomerDirectoryTest {
    private val id = 9007199254740995L
    private val source = CustomerEntity(id = id, workspaceId = "synthetic", name = "தமிழ் Customer ?",
        phone = "", location = "Entered location", createdByUid = "original-partner", createdAt = 1000,
        updatedAt = 1000, totalBilled = 1000.25, totalPaid = 100.10, balanceDue = 900.15)
    private fun operation(opId: String = "original-operation", actor: String = "original-partner") =
        PendingOperationEntity(opId, "synthetic", actor, "CUSTOMER", id, "UPSERT", 1, 2000, "{}")

    @Test fun projectionExcludesEveryFinancialFieldAndPreservesRawIdentityAndText() {
        val map = basicCustomerProjection(source.toFirestoreMap(), null, operation())
        assertFalse(map.keys.any { it in setOf("totalBilled", "totalPaid", "balanceDue", "allocationsJson", "amount") })
        val row = basicCustomerFromFirestoreMap(map, "synthetic", id)
        assertEquals(id, row.id); assertEquals(source.name, row.name); assertEquals(source.location, row.location)
        assertEquals(source.createdAt, row.createdAt); assertEquals(source.createdByUid, row.createdByUid)
        assertEquals("original-operation", row.creationOperationId)
        assertTrue(row.isSynced)
    }

    @Test fun laterProfileMirrorPreservesImmutableCreateReceiptAndOriginalCreator() {
        val first = basicCustomerProjection(source.toFirestoreMap(), null, operation())
        val next = basicCustomerProjection(source.copy(name = "Corrected raw name", updatedAt = 3000).toFirestoreMap(),
            first, operation("profile-edit-operation", "owner"))
        assertEquals("original-operation", next["creationOperationId"])
        assertEquals(source.createdByUid, next["createdByUid"]); assertEquals(source.createdAt, next["createdAt"])
        assertEquals("owner", next["actorUid"]); assertEquals("profile-edit-operation", next["operationId"])
        assertEquals("Corrected raw name", next["name"])
    }

    @Test fun legacyUnknownCreatorIsPreservedWithoutAssigningItToTheCurrentActor() {
        val map = basicCustomerProjection(source.copy(createdByUid = "", createdAt = 0).toFirestoreMap(), null,
            operation("legacy-projection", "owner"))
        val row = basicCustomerFromFirestoreMap(map, "synthetic", id)
        assertEquals("", row.createdByUid); assertEquals(0L, row.createdAt)
        assertEquals("owner", map["actorUid"])
    }

    @Test fun directoryMapperFailsClosedForFinancialFieldsOrForeignIdentities() {
        val valid = basicCustomerProjection(source.toFirestoreMap(), null, operation())
        for (patch in listOf(mapOf("totalPaid" to 0.0), mapOf("balanceDue" to 0.0),
            mapOf("workspaceId" to "foreign"), mapOf("id" to id + 1), mapOf("id" to id.toDouble()),
            mapOf("createdAt" to "1000"))) {
            assertThrows(IllegalArgumentException::class.java) {
                basicCustomerFromFirestoreMap(valid + patch, "synthetic", id)
            }
        }
    }
}
