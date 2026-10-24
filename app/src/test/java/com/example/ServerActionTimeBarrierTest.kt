package com.example

import com.example.data.firebase.FirestoreServerTime
import com.example.data.sync.ActionClockAheadException
import com.example.data.sync.ServerActionTimeBarrier
import com.example.data.sync.SyncFailurePolicy
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[36],application=android.app.Application::class)
class ServerActionTimeBarrierTest {
    @Test fun confirmedLowerBoundServesOlderOfflineActionsWithoutAnotherRead()=runBlocking {
        var reads=0
        val barrier=ServerActionTimeBarrier({reads++;2000L},{fail("Unnecessary wait")})
        barrier.awaitEligible(1500);barrier.awaitEligible(1600);barrier.awaitEligible(2000)
        assertEquals(1,reads)
    }
    @Test fun smallSkewWaitsOnceThenRequiresFreshServerEvidence()=runBlocking {
        var server=1000L;var reads=0;val pauses=mutableListOf<Long>()
        val original=1101L
        val barrier=ServerActionTimeBarrier({reads++;server},{pauses.add(it);server+=it})
        barrier.awaitEligible(original)
        assertEquals(listOf(102L),pauses);assertEquals(2,reads);assertEquals(1101L,original)
    }
    @Test fun stalledClockCannotBeAssumedToHaveAdvancedAfterADelay()=runBlocking {
        var reads=0;var pauses=0
        val barrier=ServerActionTimeBarrier({reads++;1000L},{pauses++})
        try{barrier.awaitEligible(1100);fail("Unconfirmed clock accepted")}
        catch(error:ActionClockAheadException){assertEquals(100L,error.aheadMillis);assertFalse(SyncFailurePolicy.permanent(error))}
        assertEquals(2,reads);assertEquals(1,pauses)
    }
    @Test fun largeSkewRemainsPendingWithoutWaitingOrChangingTheAction()=runBlocking {
        val original=4000L
        val barrier=ServerActionTimeBarrier({1000L},{fail("Large skew must not block the worker")})
        try{barrier.awaitEligible(original);fail("Future action accepted")}
        catch(error:ActionClockAheadException){assertEquals(3000L,error.aheadMillis);assertTrue(error.message!!.contains("CLOCK_AHEAD"));assertFalse(SyncFailurePolicy.permanent(error))}
        assertEquals(4000L,original)
    }
    @Test fun unavailableReadDoesNotInventServerTimeOrPermanentlyReject()=runBlocking {
        val failure=IOException("Network unavailable")
        val barrier=ServerActionTimeBarrier({throw failure})
        try{barrier.awaitEligible(1000);fail("Unavailable read accepted")}
        catch(error:IOException){assertSame(failure,error);assertFalse(SyncFailurePolicy.permanent(error))}
        try{ServerActionTimeBarrier({0L}).awaitEligible(1000);fail("Invalid read accepted")}
        catch(error:IOException){assertFalse(SyncFailurePolicy.permanent(error))}
    }
    @Test fun restartRechecksTheSameOriginalActionWithoutAStoredClockGuess()=runBlocking {
        var server=1000L;val original=10000L
        try{ServerActionTimeBarrier({server}).awaitEligible(original);fail("Future action accepted")}
        catch(_:ActionClockAheadException){}
        server=original
        ServerActionTimeBarrier({server}).awaitEligible(original)
        assertEquals(10000L,original)
    }
    @Test fun readTimeFractionsAreFlooredAndWorkWithoutJavaTime() {
        val exact=FirestoreServerTime.parseReadTime("2026-10-24T02:41:16.250Z")
        assertEquals(exact,FirestoreServerTime.parseReadTime("2026-10-24T02:41:16.250024Z"))
        assertEquals(exact,FirestoreServerTime.parseReadTime("2026-10-24T02:41:16.250999999Z"))
        assertEquals(exact-50,FirestoreServerTime.parseReadTime("2026-10-24T02:41:16.2Z"))
        assertEquals(exact-250,FirestoreServerTime.parseReadTime("2026-10-24T02:41:16Z"))
    }
    @Test fun invalidServerDatesFailClosed() {
        for(raw in listOf("","2026-02-31T02:41:16Z","2026-10-24","2026-10-24T02:41:16.0000000000Z")){
            try{FirestoreServerTime.parseReadTime(raw);fail("Invalid timestamp accepted: $raw")}
            catch(_:IOException){}
        }
    }
}
