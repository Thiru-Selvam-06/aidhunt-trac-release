package com.example

import com.example.data.entity.ExpenseEntity
import com.example.data.firebase.toFirestoreMap
import com.example.data.sync.QueuedWriteConflict
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class StandaloneExpenseConflictCompatibilityTest {
    private val original = ExpenseEntity(id = 9007199254740995L, workspaceId = "synthetic-a", expenseType = "Diesel",
        amount = 900.0, tractorLabel = "Entered tractor", addedByPartner = "Recorder", createdByUid = "owner",
        expenseForUid = "partner", financialAttributionUid = "partner", paidBy = "I Paid", paidByUid = "partner",
        createdAt = 1000, dateTimestamp = 1000, relatedJobId = null)

    @Test fun originalNullSourceEditBaseMatchesUnchangedServerWithoutRewritingTheOriginal() {
        val oldBase = JSONObject(original.toFirestoreMap()).put("relatedJobId", JSONObject.NULL).toString()
        val savedOriginal = oldBase
        for (server in listOf(original.toFirestoreMap(), original.toFirestoreMap() - "relatedJobId",
            original.toFirestoreMap() + ("relatedJobId" to null))) {
            QueuedWriteConflict.requireUnchanged("EXPENSE", oldBase, server, original.id, original.workspaceId)
        }
        assertEquals(savedOriginal, oldBase)
        assertTrue(JSONObject(oldBase).isNull("relatedJobId"))
        assertEquals(900.0, JSONObject(oldBase).getDouble("amount"), 0.0)
        assertEquals(1000L, JSONObject(oldBase).getLong("createdAt"))
    }

    @Test fun legacyNullSourceDoesNotHideARealMoneyIdentityPayerOrSourceConflict() {
        val oldBase = JSONObject(original.toFirestoreMap()).put("relatedJobId", JSONObject.NULL).toString()
        for (changed in listOf(
            mapOf("amount" to 901.0), mapOf("paidByUid" to "other"), mapOf("expenseForUid" to "other"),
            mapOf("createdByUid" to "other"), mapOf("workspaceId" to "foreign"), mapOf("id" to original.id + 1),
            mapOf("relatedJobId" to 9007199254740997L), mapOf("relatedJobId" to "9007199254740997"),
            mapOf("relatedJobId" to "0"), mapOf("relatedJobId" to 1.5))) {
            assertThrows(IllegalArgumentException::class.java) {
                QueuedWriteConflict.requireUnchanged("EXPENSE", oldBase, original.toFirestoreMap() + changed, original.id, original.workspaceId)
            }
        }
    }
}
