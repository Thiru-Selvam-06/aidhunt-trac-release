package com.example.data.firebase

import org.json.JSONArray
import org.json.JSONObject

/** org.json containers are not Firestore SDK values. Preserve Longs, nulls and nested arrays. */
internal object FirestoreQueuedJson {
    fun read(json: JSONObject): MutableMap<String, Any?> = json.keys().asSequence()
        .associateWith { native(json.opt(it)) }.toMutableMap()

    private fun native(value: Any?): Any? = when (value) {
        null, JSONObject.NULL -> null
        is JSONObject -> read(value)
        is JSONArray -> (0 until value.length()).map { native(value.opt(it)) }
        is String, is Number, is Boolean -> value
        else -> throw IllegalArgumentException("Unsupported queued Firestore value; original action retained")
    }
}
