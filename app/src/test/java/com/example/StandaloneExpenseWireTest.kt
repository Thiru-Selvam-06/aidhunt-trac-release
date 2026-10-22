package com.example

import com.example.data.entity.ExpenseEntity
import com.example.data.firebase.expenseFromFirestoreMap
import com.example.data.firebase.toFirestoreMap
import org.junit.Assert.*
import org.junit.Test

/** Existing Rules require numeric zero for a source-free expense, never explicit null. */
class StandaloneExpenseWireTest {
    @Test fun standalonePersonalAndBusinessPayloadsUseZeroWithoutChangingFinancialOrAuditFields() {
        for (payer in listOf("I Paid", "Business Paid")) {
            val original = ExpenseEntity(id = 71, workspaceId = "synthetic-a", expenseType = "Diesel", amount = 900.0,
                paidBy = payer, paidByUid = if (payer == "I Paid") "partner" else "",
                paidByPartner = if (payer == "I Paid") "Partner" else "", expenseForUid = "partner",
                financialAttributionUid = "partner", createdByUid = "owner", createdByRole = "OWNER", tractorLabel = "Tractor", addedByPartner = "Owner",
                dateTimestamp = 1000, createdAt = 2000, description = "Entered தமிழ்", relatedJobId = null)
            val payload = original.toFirestoreMap()
            assertEquals(0L, payload["relatedJobId"])
            val restored = expenseFromFirestoreMap(payload)
            assertNull(restored.relatedJobId)
            assertEquals(original.copy(isSynced = true), restored)
            assertNull(original.relatedJobId)
        }
    }

    @Test fun linkedPositiveJobIdentityAndLegacyMissingLinkRemainCompatible() {
        val linked = ExpenseEntity(id = 72, workspaceId = "synthetic-a", expenseType = "Diesel", amount = 1.0,
            relatedJobId = 9007199254740995L, tractorLabel = "Tractor", addedByPartner = "Owner")
        assertEquals(9007199254740995L, linked.toFirestoreMap()["relatedJobId"])
        assertEquals(linked.relatedJobId, expenseFromFirestoreMap(linked.toFirestoreMap()).relatedJobId)
        assertNull(expenseFromFirestoreMap(linked.toFirestoreMap() - "relatedJobId").relatedJobId)
    }
}
