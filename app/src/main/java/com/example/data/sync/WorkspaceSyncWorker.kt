package com.example.data.sync

import android.content.Context
import androidx.work.*
import com.example.data.database.AppDatabase
import com.example.data.repository.WorkspaceRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit

/** Scheduling only: Room remains the sole command queue; rejected actions are never reopened. */
object WorkspaceSyncScheduler {
    fun start(context: Context) {
        val app = context.applicationContext
        val manager = WorkManager.getInstance(app)
        manager.enqueueUniquePeriodicWork("trac-sync-safety-net", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WorkspaceSyncWorker>(15, TimeUnit.MINUTES)
                .addTag("trac-ledger-sync")
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            AppDatabase.getInstance(app).pendingOperationDao().observePending()
                .map { rows -> rows.groupBy { it.actorUid to it.workspaceId }.mapValues { it.value.maxOf { row -> row.sequence } } }
                .distinctUntilChanged().collect { groups ->
                    groups.forEach { (identity, _) ->
                        val (uid, ws) = identity
                        // Also schedules other actors safely: the worker verifies Auth before any network write.
                        manager.enqueueUniqueWork("trac-sync-$uid-$ws", ExistingWorkPolicy.APPEND_OR_REPLACE,
                            OneTimeWorkRequestBuilder<WorkspaceSyncWorker>()
                                .addTag("trac-ledger-sync")
                                .setInputData(workDataOf("actorUid" to uid, "workspaceId" to ws))
                                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build())
                    }
                }
        }
    }
}

class WorkspaceSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        val expectedUid = inputData.getString("actorUid") ?: uid
        if (expectedUid != uid) return Result.success()
        val db = AppDatabase.getInstance(applicationContext)
        val repository = WorkspaceRepository.getInstance(applicationContext, db)
        return try {
            val selected = repository.restoreBackgroundSyncSession(uid) ?: return Result.success()
            val ws = inputData.getString("workspaceId") ?: selected
            if (ws != selected || repository.verifiedWorkspaceContext.value != (uid to ws)) return Result.success()
            withTimeout(110_000) { repository.pushUnsyncedToCloud(ws) }
            val pending = db.pendingOperationDao().outstandingOperations(ws).filter { it.actorUid == uid && it.state == "PENDING" }
            // Blocked dependents need human resolution, not endless automatic financial attempts.
            if (pending.any { !it.failureReason.startsWith("Blocked by a rejected earlier action") }) Result.retry() else Result.success()
        } catch (_: TimeoutCancellationException) { Result.retry()
        } catch (cancelled: CancellationException) { throw cancelled
        } catch (_: Exception) { Result.retry() }
    }
}
