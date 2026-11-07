package com.example.data.sync

import org.json.JSONArray
import org.json.JSONObject

/** Original operation identity binds each immutable before/after event to one job edit. */
object JobEditAudit {
    const val MAX_HISTORY_BYTES = 196608
    val fields = listOf("customerId", "customerName", "customerPhone", "customerLocation", "operatorName", "tractorId", "tractorLabel", "workType", "startTimeMillis", "endTimeMillis", "durationMinutes", "hourlyRate", "totalAmount", "notes")
    fun equivalent(a: Any?, b: Any?): Boolean = when {
        a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet() == b.keys().asSequence().toSet() && a.keys().asSequence().all { equivalent(a.opt(it), b.opt(it)) }
        a is JSONArray && b is JSONArray -> a.length() == b.length() && (0 until a.length()).all { equivalent(a.opt(it), b.opt(it)) }
        a is Number && b is Number -> a.toString().toBigDecimal().compareTo(b.toString().toBigDecimal()) == 0
        else -> a == b
    }
    fun snapshot(data: Map<String, Any?>): Map<String, Any?> = fields.associateWith { data[it] }
    fun read(raw: String): List<Map<String, Any?>> {
        if (raw == "[]") return emptyList()
        fun value(v: Any?): Any? = when(v) {
            JSONObject.NULL -> null
            is JSONObject -> v.keys().asSequence().associateWith { value(v.opt(it)) }
            else -> v
        }
        val array = JSONArray(raw)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            obj.keys().asSequence().associateWith { value(obj.opt(it)) }
        }
    }
    fun append(previous: List<*>, before: Map<String, Any?>, after: Map<String, Any?>, actor: String, operation: String, time: Long): List<Any?> {
        require(previous.size < 100) { "Job edit history needs archival review before another edit" }
        val result = previous + mapOf("actorUid" to actor, "operationId" to operation, "actionTime" to time,
            "before" to snapshot(before), "after" to snapshot(after))
        require(JSONArray(result).toString().toByteArray(Charsets.UTF_8).size <= MAX_HISTORY_BYTES) {
            "Job edit history needs archival review before another edit"
        }
        return result
    }
}
