package com.example

import com.example.data.entity.JobEntryEntity
import com.example.ui.screens.report.customerDuesForPartner
import com.example.ui.util.FinancialCalculationEngine
import com.example.ui.util.FinancialScope
import org.junit.Assert.*
import org.junit.Test

/** Synthetic calculation baseline, not an Android startup/frame benchmark or a capacity guarantee. */
class BusinessPerformanceTest {
    @Test fun syntheticLedgerTiersMeasureLatencyAndRetainIndependentMoneyInvariants() {
        val allowed = setOf(100, 1000, 10000, 50000, 100000)
        val tiers = (System.getProperty("trac.performance.tiers") ?: "100,1000").split(',').map { it.toInt() }
        require(tiers.isNotEmpty() && tiers.all { it in allowed })
        for (count in tiers.distinct()) {
            val runtime = Runtime.getRuntime()
            val start = System.nanoTime()
            val jobs = (1..count).map { number -> JobEntryEntity(id = number.toLong(), workspaceId = "performance-demo",
                customerId = ((number - 1) % 100 + 1).toLong(), customerName = "Synthetic ${number % 100}", customerPhone = "",
                operatorName = "Synthetic member", tractorLabel = "Synthetic tractor", workType = "Rotavator",
                startTimeMillis = 1000, endTimeMillis = 61000, durationMinutes = 1, totalAmount = 1100.0,
                amountReceived = 900.0, pendingAmount = 200.0, addedByPartner = "Recorder", createdAt = 1000,
                createdByUid = "owner", entryForUid = if (number % 2 == 0) "partner-a" else "partner-b") }
            val generated = System.nanoTime()
            val summary = FinancialCalculationEngine.calculateSummary(jobs, emptyList(), emptyList(), FinancialScope(workspaceId = "performance-demo"))
            val summarized = System.nanoTime()
            val dues = customerDuesForPartner("performance-demo", emptyList(), jobs, "partner-a")
            val filtered = System.nanoTime()
            assertEquals(1100.0 * count, summary.totalRecorded, 0.0)
            assertEquals(900.0 * count, summary.totalReceived, 0.0)
            assertEquals(200.0 * count, summary.totalDue, 0.0)
            assertEquals(200.0 * (count / 2), dues.sumOf { it.balanceDue }, 0.0)
            assertEquals(count, summary.jobCount)
            println("TRAC_PERFORMANCE {\"jobs\":$count,\"customers\":100,\"generateMs\":${(generated-start)/1000000.0}," +
                "\"summaryMs\":${(summarized-generated)/1000000.0},\"partnerFilterMs\":${(filtered-summarized)/1000000.0}," +
                "\"heapUsedBytes\":${runtime.totalMemory()-runtime.freeMemory()},\"java\":\"${System.getProperty("java.version")}\",\"os\":\"${System.getProperty("os.name")}\"}")
        }
    }
}
