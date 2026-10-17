package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

@RunWith(AndroidJUnit4::class)
class DatabaseWorkspaceDeviceTest {
    private fun job(ws: String, customer: Long) = JobEntryEntity(id=9007199254740995L, workspaceId=ws,
        customerId=customer, customerName="Synthetic", operatorName="Member", tractorLabel="T", workType="Work",
        startTimeMillis=1, endTimeMillis=2, durationMinutes=1, totalAmount=100.0, amountReceived=0.0,
        pendingAmount=100.0, addedByPartner="Member")
    private fun expense(ws: String, id: Long, jobId: Long?) = ExpenseEntity(id=id, workspaceId=ws,
        expenseType="Fuel", amount=5.0, tractorLabel="T", addedByPartner="Member", relatedJobId=jobId)
    @Test fun compoundExpenseDeleteCannotTouchCollidingForeignJobOrStandaloneExpense() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            for(ws in listOf("a","b")) {
                db.jobEntryDao().insertJob(job(ws,2))
                db.expenseDao().insertExpense(expense(ws,1,9007199254740995L))
                db.expenseDao().insertExpense(expense(ws,2,null))
            }
            repeat(2) { db.expenseDao().deleteExpensesForJob("a",9007199254740995L) }
            assertNull(db.expenseDao().getExpenseById("a",1))
            assertNotNull(db.expenseDao().getExpenseById("a",2))
            assertNotNull(db.expenseDao().getExpenseById("b",1))
            assertNotNull(db.expenseDao().getExpenseById("b",2))
        } finally { db.close() }
    }
    @Test fun customerExpenseReadMustNotFollowForeignJobCollision() = runBlocking {
        val db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java).build()
        try {
            db.jobEntryDao().insertJob(job("a",2))
            db.jobEntryDao().insertJob(job("b",999))
            db.expenseDao().insertExpense(expense("a",1,9007199254740995L))
            db.expenseDao().insertExpense(expense("b",1,9007199254740995L))
            assertEquals(listOf("a"), db.expenseDao().getExpensesForCustomer("a",2).map { it.workspaceId })
        } finally { db.close() }
    }
}
