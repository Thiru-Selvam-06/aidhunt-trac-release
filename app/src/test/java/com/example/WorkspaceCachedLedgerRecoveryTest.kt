package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.data.firebase.FirestoreRepository
import com.example.data.repository.WorkspaceRepository
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Real repository/cache paths, isolated SQLite and preferences; no Firebase network requests. */
@RunWith(RobolectricTestRunner::class)
class WorkspaceCachedLedgerRecoveryTest {
    private fun fixture(test: (WorkspaceRepository, Context) -> Unit) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            context.getSharedPreferences("workspace_percentages_prefs", 0).edit().clear()
                .putString("pct_a", "{\"member\":60}").commit()
            context.getSharedPreferences("workspace_profit_share_prefs", 0).edit().clear()
                .putString("psa_a", "{\"member\":{\"participantUid\":\"member\",\"percentage\":60,\"displayName\":\"Synthetic\",\"role\":\"PARTNER\"}}").commit()
            test(WorkspaceRepository(context, db, FirestoreRepository(context, scope), scope), context)
        } finally { scope.cancel(); db.close() }
    }
    @Test fun switchingToUncachedWorkspaceCannotRetainPreviousPercentages() = fixture { repo, _ ->
        repo.loadCachedPercentages("a"); assertEquals(60, repo.partnerPercentages.value["member"])
        repo.loadCachedPercentages("b"); assertTrue(repo.partnerPercentages.value.isEmpty())
    }
    @Test fun switchingToUncachedWorkspaceCannotRetainPreviousProfitShares() = fixture { repo, _ ->
        repo.loadCachedProfitShareAllocations("a"); assertEquals(60, repo.profitShareAllocations.value["member"])
        repo.loadCachedProfitShareAllocations("b")
        assertTrue(repo.profitShareAllocations.value.isEmpty()); assertTrue(repo.profitShareAllocationDetails.value.isEmpty())
    }
    @Test fun malformedCacheCannotLeaveAnotherWorkspacesValuesVisible() = fixture { repo, context ->
        repo.loadCachedPercentages("a"); repo.loadCachedProfitShareAllocations("a")
        context.getSharedPreferences("workspace_percentages_prefs",0).edit().putString("pct_b", "invalid").commit()
        context.getSharedPreferences("workspace_profit_share_prefs",0).edit().putString("psa_b", "invalid").commit()
        repo.loadCachedPercentages("b"); repo.loadCachedProfitShareAllocations("b")
        assertTrue(repo.partnerPercentages.value.isEmpty()); assertTrue(repo.profitShareAllocations.value.isEmpty())
        assertTrue(repo.profitShareAllocationDetails.value.isEmpty())
    }
}
