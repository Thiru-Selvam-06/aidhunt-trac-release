package com.example.data.auth

import com.example.data.entity.JobEntryEntity
import com.example.data.entity.PaymentEntity
import com.example.data.firebase.WorkspaceMember
import java.util.Calendar
import java.util.TimeZone

/** Collection business dates use India time; authorization still uses the original action time. */
object CollectionPolicy {
    val timeZone: TimeZone get() = TimeZone.getTimeZone("Asia/Kolkata")
    fun calendar(millis: Long): Calendar = Calendar.getInstance(timeZone).apply { timeInMillis = millis }
    fun startOfDay(millis: Long): Long {
        require(millis > 0) { "Source Entry creation date needs review before collecting payment" }
        return calendar(millis).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    fun requireDate(collectedAt: Long, entryCreatedAt: Long) {
        require(collectedAt > 0 && startOfDay(collectedAt) >= startOfDay(entryCreatedAt)) {
            "Collection date cannot be before the source Entry creation date (India time)"
        }
    }
    fun allocatedJobs(jobs: List<JobEntryEntity>, workspaceId: String, customerId: Long, amount: Double): List<JobEntryEntity> {
        if (!amount.isFinite() || amount <= 0) return emptyList()
        var remaining = amount
        return jobs.filter { it.workspaceId == workspaceId && it.customerId == customerId && it.pendingAmount > 0 }
            .sortedWith(compareBy<JobEntryEntity> { it.startTimeMillis }.thenBy { it.id })
            .filter { job -> (remaining > 0).also { if (it) remaining -= minOf(remaining, job.pendingAmount) } }
    }
    fun earliestDate(jobs: List<JobEntryEntity>): Long {
        require(jobs.isNotEmpty()) { "No source Entries available for this collection" }
        return jobs.maxOf { startOfDay(it.createdAt) }
    }
    fun eligibleCollectors(workspaceId: String, actorUid: String, owner: Boolean, members: List<WorkspaceMember>): List<WorkspaceMember> =
        members.filter { it.workspaceId == workspaceId && it.uid.isNotBlank() && it.status.equals("ACTIVE", true) &&
            it.role.uppercase() in setOf("OWNER", "PARTNER") && (owner || it.uid == actorUid) }
            .distinctBy { it.uid }.sortedBy { if (it.uid == actorUid) 0 else 1 }

    fun requireCollector(workspaceId: String, actorUid: String, owner: Boolean, selectedUid: String, members: List<WorkspaceMember>): WorkspaceMember =
        eligibleCollectors(workspaceId, actorUid, owner, members).firstOrNull { it.uid == selectedUid.ifBlank { actorUid } }
            ?: throw SecurityException("Selected collector is not an eligible active workspace member")

    data class Identity(val uid: String, val name: String, val role: String)
    private fun identity(ws: String, uid: String, name: String, role: String, members: List<WorkspaceMember>): Identity {
        val member = members.firstOrNull { it.workspaceId == ws && it.uid == uid && uid.isNotBlank() }
        return Identity(uid, member?.displayName?.takeIf { it.isNotBlank() } ?: name.ifBlank { "Unknown member" }, member?.role ?: role)
    }
    fun collector(payment: PaymentEntity, members: List<WorkspaceMember>): Identity =
        if (payment.collectorUid.isNotBlank()) identity(payment.workspaceId, payment.collectorUid, payment.collectorName, payment.collectorRole, members)
        else recorder(payment, members) // Legacy records retain their original identity; never fabricate a selected collector.
    fun recorder(payment: PaymentEntity, members: List<WorkspaceMember>): Identity =
        identity(payment.workspaceId, payment.collectedByUid, payment.collectedByName, payment.collectedByRole, members)
}
