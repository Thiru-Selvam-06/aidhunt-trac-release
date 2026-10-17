package com.example
import com.example.data.entity.JobEntryEntity
import com.example.ui.util.HomeRecordSummary
import org.junit.Assert.*
import org.junit.Test
class HomeRecordSummaryTest {
 private fun entry(id:Long=1)=JobEntryEntity(id=id,workspaceId="a",customerId=2,customerName="Test",operatorName="Owner",tractorLabel="T",workType="Work",startTimeMillis=1,endTimeMillis=2,durationMinutes=1,totalAmount=958123.0,amountReceived=0.0,pendingAmount=958123.0,addedByPartner="Owner")
 @Test fun oldRecordsRemainInAllTimeCountAndAmount(){val s=HomeRecordSummary.from(listOf(entry()));assertEquals(1,s.count);assertEquals(958123.0,s.recorded,0.0)}
 @Test fun historicalAndPendingRecordsUseTheSameSetWithoutPreviewCap(){val rows=(1L..25L).map{entry(it).copy(totalAmount=100.0,pendingAmount=100.0,isHistorical=it==1L,isSynced=it!=2L)};val s=HomeRecordSummary.from(rows);assertEquals(25,s.count);assertEquals(2500.0,s.recorded,0.0);assertEquals(2500.0,s.due,0.0)}
 @Test fun paidRecordsAndTrueZeroStayZero(){val s=HomeRecordSummary.from(listOf(entry().copy(amountReceived=958123.0,pendingAmount=0.0)));assertEquals(0.0,s.due,0.0);assertEquals(HomeRecordSummary(0,0.0,0.0),HomeRecordSummary.from(emptyList()))}
 @Test fun legacyBilledFallbackIsPreserved(){val s=HomeRecordSummary.from(listOf(entry().copy(totalAmount=0.0,amountReceived=20.0,pendingAmount=30.0)));assertEquals(50.0,s.recorded,0.0);assertEquals(30.0,s.due,0.0)}
}
