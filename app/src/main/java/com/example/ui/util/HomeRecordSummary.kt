package com.example.ui.util

import com.example.data.entity.JobEntryEntity

/** All-time authorized Room rows, including Historical Entries; independent of today's preview. */
data class HomeRecordSummary(val count: Int, val recorded: Double, val due: Double) {
    companion object {
        fun from(records: List<JobEntryEntity>) = HomeRecordSummary(
            records.size,
            records.sumOf { if (it.totalAmount > 0.0) it.totalAmount else it.amountReceived + it.pendingAmount },
            records.sumOf { if (it.pendingAmount > 0.0) it.pendingAmount else maxOf(0.0, it.totalAmount - it.amountReceived) }
        )
    }
}
