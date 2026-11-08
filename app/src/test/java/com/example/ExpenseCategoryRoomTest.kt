package com.example

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.ExpenseCategoryEntity
import com.example.data.entity.PendingOperationEntity
import com.example.data.expense.ExpenseCategories
import com.example.data.firebase.toFirestoreMap
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = android.app.Application::class)
class ExpenseCategoryRoomTest {
    @Test fun pendingCategoryAndExactOperationSurviveReopenAndStayWorkspaceScoped() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "cat.db"
        var db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        val category = ExpenseCategoryEntity(ExpenseCategories.id("Engine Oil"), "w", "Engine Oil", "engine oil", 1000, "p", "PARTNER")
        val operation = PendingOperationEntity("original-category-op", "w", "p", "EXPENSE_CATEGORY", category.id, "UPSERT", 1, 1000,
            JSONObject(category.toFirestoreMap()).put("operationProtocolVersion", 2).toString())
        try {
            db.withTransaction { db.expenseCategoryDao().put(category); db.pendingOperationDao().insert(operation) }
            db.close(); db = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
            assertEquals(category, db.expenseCategoryDao().observe("w").first().single())
            assertTrue(db.expenseCategoryDao().observe("other").first().isEmpty())
            assertEquals(operation, db.pendingOperationDao().getOperation(operation.operationId))
            assertTrue(db.pendingOperationDao().nextBatch("w", "other-actor").isEmpty())
            db.pendingOperationDao().failed(operation.operationId, "REJECTED", "PERMISSION_DENIED")
            assertEquals(operation.payloadJson, db.pendingOperationDao().getOperation(operation.operationId)!!.payloadJson)
            assertFalse(db.expenseCategoryDao().find("w", category.id)!!.isSynced)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
