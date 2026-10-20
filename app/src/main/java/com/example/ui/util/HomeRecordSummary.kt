package com.example.ui.util

import com.example.data.entity.JobEntryEntity
import com.example.data.entity.CustomerEntity

/** All-time authorized Room rows, including Historical Entries; independent of today's preview. */
data class HomeRecordSummary(val count: Int, val recorded: Double, val due: Double, val customerCount: Int = 0) {
    companion object {
        fun from(records: List<JobEntryEntity>, customers: List<CustomerEntity> = emptyList()): HomeRecordSummary {
            val summary = FinancialCalculationEngine.calculateSummary(records, emptyList(), emptyList(), FinancialScope())
            return HomeRecordSummary(summary.jobCount, summary.totalRecorded, summary.totalDue,
                FinancialCalculationEngine.customersWithLedger(customers, records).size)
        }
    }
}
