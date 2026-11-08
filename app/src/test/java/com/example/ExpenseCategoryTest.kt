package com.example

import com.example.data.expense.ExpenseCategories
import com.example.data.expense.ExpenseAmountInput
import com.example.data.entity.ExpenseCategoryEntity
import com.example.data.entity.PendingOperationEntity
import com.example.data.firebase.*
import com.example.data.sync.OperationPolicy
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class ExpenseCategoryTest {
    @Test fun equivalentNamesHaveOneIdentityWithoutTranslatingDisplayData() {
        assertEquals(ExpenseCategories.id("Engine Oil"), ExpenseCategories.id("  ENGINE\u00a0  OIL  "))
        assertEquals(ExpenseCategories.id("கொடை"), ExpenseCategories.id("கொ டை".replace(" ", "")))
        assertEquals("Engine Oil", ExpenseCategories.validate(" Engine Oil "))
        assertEquals("Engine Oil", ExpenseCategories.label("Engine Oil", true))
        assertNotEquals(ExpenseCategories.id("Engine Oil"), ExpenseCategories.id("Engine Parts"))
        assertTrue(ExpenseCategories.id("Engine Oil") > 0)
    }

    @Test fun builtInsAndHistoricalOtherStayReadableAndCustomNamesDeduplicate() {
        val names = ExpenseCategories.choices(listOf("Engine Oil", " engine   oil "), listOf("Others", "Legacy Kind"))
        assertEquals(1, names.count { ExpenseCategories.key(it) == "engine oil" })
        assertTrue(names.containsAll(listOf("Diesel", "Seeds", "Furniture", "Other", "Legacy Kind")))
        assertEquals("Other", ExpenseCategories.builtIn("OTHERS"))
        assertEquals("டீசல்", ExpenseCategories.label("Diesel", true))
        assertEquals("Legacy Kind", ExpenseCategories.label("Legacy Kind", true))
    }

    @Test fun invalidCategoryNamesAreRejectedBeforePersistence() {
        for (name in listOf("", "   ", "\u00a0", "name\nnext", "x".repeat(61))) {
            try { ExpenseCategories.validate(name); fail("Invalid category accepted") } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun mappingPreservesIdentityAndRejectsForeignOrCollidingData() {
        val name = "Engine Oil"
        val category = ExpenseCategoryEntity(ExpenseCategories.id(name), "w", name, ExpenseCategories.key(name), 10, "p", "PARTNER")
        val data = category.toFirestoreMap()
        assertEquals(category.copy(isSynced = true), expenseCategoryFromFirestoreMap(data, "w"))
        for (bad in listOf(data + ("workspaceId" to "foreign"), data + ("id" to 1L), data + ("nameKey" to "different"))) {
            try { expenseCategoryFromFirestoreMap(bad, "w"); fail("Category identity changed") } catch (_: IllegalArgumentException) {}
        }
    }

    @Test fun categoryPermissionsRemainActorAndWorkspaceBound() {
        val members = listOf(WorkspaceMember(uid = "p", workspaceId = "w", role = "PARTNER", status = "ACTIVE"))
        val payload = JSONObject(mapOf("operationProtocolVersion" to 2, "workspaceId" to "w", "createdByUid" to "p")).toString()
        val operation = PendingOperationEntity("original", "w", "p", "EXPENSE_CATEGORY", 1, "UPSERT", 1, 10, payload)
        OperationPolicy.requireAuthorized(operation, "PARTNER", members)
        for ((op, role, people) in listOf(
            Triple(operation.copy(mutationType = "DELETE"), "PARTNER", members),
            Triple(operation.copy(actorUid = "other"), "PARTNER", members),
            Triple(operation.copy(workspaceId = "foreign"), "PARTNER", members),
            Triple(operation, "OPERATOR", members.map { it.copy(role = "OPERATOR") })
        )) {
            try { OperationPolicy.requireAuthorized(op, role, people); fail("Unauthorized category accepted") } catch (_: SecurityException) {}
        }
    }

    @Test fun amountEditorPreservesPaiseAndSignedRefunds() {
        for (amount in listOf(1000.0, 1234.56, -25.75, -1000.0, 0.01))
            assertEquals(amount, ExpenseAmountInput.parse(ExpenseAmountInput.format(amount))!!, 0.0)
        assertEquals("1234.56", ExpenseAmountInput.format(1234.56))
        assertEquals("-25.75", ExpenseAmountInput.format(-25.75))
        for (text in listOf("", "0", "-0", "NaN", "Infinity", "1e999", "bad")) assertNull(ExpenseAmountInput.parse(text))
    }
}
