package com.example.ui.util

import com.example.data.entity.CustomerEntity
import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
import java.text.Normalizer
import java.util.Locale

/** Current display projection only. Stored job/receipt snapshots remain historical evidence. */
object CustomerIdentity {
    fun searchKey(value: String): String = Normalizer.normalize(value.trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT)
    fun matches(name: String, query: String): Boolean = searchKey(name).contains(searchKey(query))
    private fun index(customers: List<CustomerEntity>) = customers.filter { it.id > 0 && it.workspaceId.isNotBlank() }
        .associateBy { it.workspaceId to it.id }

    fun jobs(rows: List<JobEntryEntity>, customers: List<CustomerEntity>): List<JobEntryEntity> {
        val profiles = index(customers)
        return rows.map { row -> profiles[row.workspaceId to row.customerId]?.let { profile ->
            row.copy(customerName = profile.name, customerPhone = profile.phone, customerLocation = profile.location)
        } ?: row }
    }

    fun payments(rows: List<PaymentEntity>, customers: List<CustomerEntity>, jobs: List<JobEntryEntity> = emptyList()): List<PaymentEntity> {
        val profiles = index(customers)
        val sources = jobs.associateBy { it.workspaceId to it.id }
        return rows.map { row ->
            val job = sources[row.workspaceId to row.jobEntryId]
            val currentCustomerId = if (job != null && com.example.data.sync.JobCustomerReassignment.singleSource(row, job)) job.customerId else row.customerId
            profiles[row.workspaceId to currentCustomerId]?.let { profile ->
            row.copy(customerId = currentCustomerId, customerName = profile.name)
        } ?: row }
    }
}
