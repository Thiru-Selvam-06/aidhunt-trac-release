package com.example.ui.util

import com.example.data.entity.JobEntryEntity
import java.util.Calendar

/** Returns the timestamp that should order a job which is visible in today's list. */
fun recentJobDisplayTimestamp(job: JobEntryEntity, nowMillis: Long = System.currentTimeMillis()): Long {
    return job.createdAt
}

/** Immutable creation order; reconciliation and edits cannot reorder timestamp ties. */
val RecentJobOrder = compareByDescending<JobEntryEntity> { it.createdAt }.thenByDescending { it.id }

/** A job is recent when either its work timestamp or its immutable creation timestamp is today. */
fun isRecentJobToday(job: JobEntryEntity, nowMillis: Long = System.currentTimeMillis()): Boolean {
    return (job.startTimeMillis > 0L && isSameCalendarDay(job.startTimeMillis, nowMillis)) ||
        (job.createdAt > 0L && isSameCalendarDay(job.createdAt, nowMillis))
}

private fun isSameCalendarDay(firstMillis: Long, secondMillis: Long): Boolean {
    val first = Calendar.getInstance().apply { timeInMillis = firstMillis }
    val second = Calendar.getInstance().apply { timeInMillis = secondMillis }
    return first.get(Calendar.ERA) == second.get(Calendar.ERA) &&
        first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)
}
