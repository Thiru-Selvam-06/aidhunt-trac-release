package com.example

import com.example.data.entity.*
import com.example.ui.screens.report.collectionJobHistory
import com.example.data.auth.CollectionPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CollectionHistoryGroupingTest {
    private val jobId = 9007199254740995L
    private fun job(id: Long = jobId, ws: String = "a") = JobEntryEntity(id=id,workspaceId=ws,customerId=7,
        customerName="Same customer",operatorName="Owner",tractorLabel="T",workType="Work",startTimeMillis=100,
        endTimeMillis=200,durationMinutes=1,totalAmount=50000.0,amountReceived=15000.0,pendingAmount=35000.0,
        addedByPartner="Owner",createdAt=50)
    private fun payment(id: Long, amount: Double, at: Long = id, entry: Long = jobId) = PaymentEntity(
        id=id,workspaceId="a",jobEntryId=entry,customerId=7,customerName="Same customer",amount=amount,collectedAt=at,
        collectorUid="partner",collectorName="Partner",collectorRole="PARTNER",collectedByUid="owner",collectedByName="Owner")
    @Test fun threePartialPaymentsBecomeOneChronologicalJobAndLaterPaymentUpdatesSameKey() {
        val rows = listOf(payment(3,6000.0),payment(1,4000.0),payment(2,5000.0))
        val first = collectionJobHistory("a",rows,listOf(job())).single()
        assertEquals(listOf(1L,2L,3L),first.events.map { it.payment.id })
        assertEquals(15000.0,first.events.sumOf { it.amount },0.0)
        assertEquals(50000.0,first.job!!.totalAmount,0.0)
        assertEquals(35000.0,first.job.pendingAmount,0.0)
        val later = collectionJobHistory("a",rows+payment(4,2000.0),listOf(job().copy(amountReceived=17000.0,pendingAmount=33000.0))).single()
        assertEquals(first.key,later.key);assertEquals(4,later.events.size)
        assertEquals(33000.0,later.job!!.pendingAmount,0.0)
    }
    @Test fun sameCustomerDifferentJobsStaySeparateAndExactLongIdsAreRetained() {
        val groups=collectionJobHistory("a",listOf(payment(1,4000.0),payment(2,5000.0,entry=jobId+1)),listOf(job(),job(jobId+1)))
        assertEquals(setOf(jobId,jobId+1),groups.map { it.jobId }.toSet())
        assertEquals(2,groups.size)
    }
    @Test fun foreignWorkspaceRowsAndJobsNeverJoinAndDuplicateCacheRowsAreNotSummed() {
        val p=payment(1,4000.0)
        val group=collectionJobHistory("a",listOf(p,p,p.copy(workspaceId="b",amount=9000.0)),listOf(job(ws="b"))).single()
        assertNull(group.job);assertEquals(1,group.events.size);assertEquals(4000.0,group.events.single().amount,0.0)
        assertTrue(collectionJobHistory("",listOf(p),listOf(job())).isEmpty())
    }
    @Test fun reversalAndDeletedAuditEventsNeverChangeCanonicalJobBalances() {
        val rows=listOf(payment(1,4000.0),payment(2,5000.0).copy(status="REVERSED"),payment(3,6000.0).copy(status="DELETED"))
        val group=collectionJobHistory("a",rows,listOf(job().copy(amountReceived=4000.0,pendingAmount=46000.0))).single()
        assertEquals(3,group.events.size)
        assertEquals(4000.0,group.events.filter { it.payment.status=="ACTIVE" }.sumOf { it.amount },0.0)
        assertEquals(46000.0,group.job!!.pendingAmount,0.0)
        assertEquals(rows.toSet(),group.events.map { it.payment }.toSet())
    }
    @Test fun multiJobAllocationProjectsOnlyItsShareWithoutChangingThePaymentIdentity() {
        val p=payment(1,9000.0).copy(allocationsJson="""[{"jobId":"9007199254740995","amount":4000},{"jobId":"9007199254740996","amount":5000}]""")
        val groups=collectionJobHistory("a",listOf(p),listOf(job(),job(jobId+1)))
        assertEquals(2,groups.size);assertEquals(9000.0,groups.sumOf { it.events.sumOf { e->e.amount } },0.0)
        assertEquals(setOf(4000.0,5000.0),groups.map { it.events.single().amount }.toSet())
        assertTrue(groups.all { it.events.single().payment==p })
    }
    @Test fun invalidOrMissingRelationshipsStayRecoverableWithoutMergingByCustomer() {
        val bad=payment(1,100.0).copy(allocationsJson="""[{"jobId":1,"amount":99}]""")
        val groups=collectionJobHistory("a",listOf(bad,payment(2,20.0,entry=0),payment(3,30.0,entry=0)),emptyList())
        assertEquals(3,groups.size);assertTrue(groups.first { it.events.single().payment.id==1L }.allocationNeedsReview)
        assertEquals(150.0,groups.sumOf { it.events.sumOf { e->e.amount } },0.0)
    }
    @Test fun syncAcknowledgementDoesNotChangeGroupOrBusinessCollectorAndAuditIdentity() {
        val p=payment(1,40.0).copy(isSynced=false)
        val before=collectionJobHistory("a",listOf(p),listOf(job())).single()
        val after=collectionJobHistory("a",listOf(p.copy(isSynced=true)),listOf(job())).single()
        assertEquals(before.key,after.key)
        assertEquals("partner",CollectionPolicy.collector(after.events.single().payment,emptyList()).uid)
        assertEquals("owner",CollectionPolicy.recorder(after.events.single().payment,emptyList()).uid)
        assertFalse(before.events.single().payment.isSynced);assertTrue(after.events.single().payment.isSynced)
    }
    @Test fun validDecimalSplitDoesNotLoseItsJobRelationshipsToDoubleRepresentationNoise() {
        val p=payment(1,0.1+0.2).copy(allocationsJson="""[{"jobId":"9007199254740995","amount":0.1},{"jobId":"9007199254740996","amount":0.2}]""")
        val groups=collectionJobHistory("a",listOf(p),listOf(job(),job(jobId+1)))
        assertEquals(2,groups.size);assertTrue(groups.none { it.allocationNeedsReview })
        assertEquals(p.amount,groups.sumOf { it.events.sumOf { e->e.amount } },0.000001)
    }

}
