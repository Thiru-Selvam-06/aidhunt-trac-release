package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.entity.JobEntryEntity
import com.example.data.repository.effectiveEntryStartTimeForEdit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class EntryCreationTimestampPersistenceTest {
    @Test
    fun createdAtSurvivesDatabaseReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "entry-timestamp-${UUID.randomUUID()}.db"
        val originalCreatedAt = 1_000_000L
        val entry = JobEntryEntity(
            id = 123L,
            workspaceId = "workspace-1",
            customerName = "Customer",
            operatorName = "Partner",
            tractorLabel = "Tractor",
            workType = "Ploughing",
            startTimeMillis = originalCreatedAt,
            endTimeMillis = originalCreatedAt + 60_000L,
            durationMinutes = 1L,
            totalAmount = 100.0,
            amountReceived = 0.0,
            pendingAmount = 100.0,
            addedByPartner = "Partner",
            createdAt = originalCreatedAt,
            createdByUid = "partner-uid",
            createdByRole = "Partner",
            isSynced = false
        )

        context.getDatabasePath(databaseName).parentFile?.mkdirs()
        val firstOpen = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        try {
            firstOpen.jobEntryDao().insertJob(entry)
        } finally {
            firstOpen.close()
        }

        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(originalCreatedAt, reopened.jobEntryDao().getJobById(entry.workspaceId, entry.id)?.createdAt)
        } finally {
            reopened.close()
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun legacyEffectiveCreationTimeSurvivesEditAndDatabaseReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val databaseName = "legacy-${UUID.randomUUID().toString().take(8)}.db"
        val originalStartTime = 1_000_000L
        val persistedEntry = JobEntryEntity(
            id = 456L,
            workspaceId = "workspace-1",
            customerName = "Customer",
            operatorName = "Partner",
            tractorLabel = "Tractor",
            workType = "Ploughing",
            startTimeMillis = originalStartTime,
            endTimeMillis = originalStartTime + 60_000L,
            durationMinutes = 1L,
            totalAmount = 100.0,
            amountReceived = 0.0,
            pendingAmount = 100.0,
            addedByPartner = "Partner",
            createdAt = 0L,
            createdByUid = "partner-uid",
            createdByRole = "Partner",
            isSynced = false
        )
        val submittedEdit = persistedEntry.copy(
            startTimeMillis = originalStartTime + 30 * 60 * 60 * 1000L,
            endTimeMillis = originalStartTime + 30 * 60 * 60 * 1000L + 60_000L
        )
        val locallySaved = submittedEdit.copy(
            startTimeMillis = effectiveEntryStartTimeForEdit(persistedEntry, submittedEdit)
        )

        context.getDatabasePath(databaseName).parentFile?.mkdirs()
        val firstOpen = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        try {
            firstOpen.jobEntryDao().insertJob(persistedEntry)
            firstOpen.jobEntryDao().insertJob(locallySaved)
        } finally {
            firstOpen.close()
        }

        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, databaseName)
            .allowMainThreadQueries()
            .build()
        try {
            val savedEntry = reopened.jobEntryDao().getJobById(persistedEntry.workspaceId, persistedEntry.id)
            assertEquals(0L, savedEntry?.createdAt)
            assertEquals(originalStartTime, savedEntry?.startTimeMillis)
        } finally {
            reopened.close()
            context.deleteDatabase(databaseName)
        }
    }
}
