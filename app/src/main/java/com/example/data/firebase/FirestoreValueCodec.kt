package com.example.data.firebase

import org.json.JSONArray
import org.json.JSONObject

/** Lossless REST values for guarded deletion. Unsupported values fail closed. */
internal object FirestoreValueCodec {
    private data class TimestampValue(val text: String)

    fun decode(value: JSONObject): Any? = when {
        value.has("integerValue") -> value.getString("integerValue").toLong()
        value.has("doubleValue") -> value.getDouble("doubleValue")
        value.has("stringValue") -> value.getString("stringValue")
        value.has("booleanValue") -> value.getBoolean("booleanValue")
        value.has("nullValue") -> null
        value.has("timestampValue") -> TimestampValue(value.getString("timestampValue"))
        value.has("arrayValue") -> value.getJSONObject("arrayValue").optJSONArray("values").let { rows ->
            if (rows == null) emptyList<Any?>() else (0 until rows.length()).map { decode(rows.getJSONObject(it)) }
        }
        value.has("mapValue") -> value.getJSONObject("mapValue").optJSONObject("fields").let { fields ->
            if (fields == null) emptyMap<String, Any?>() else fields.keys().asSequence().associateWith { decode(fields.getJSONObject(it)) }
        }
        else -> throw IllegalArgumentException("Unsupported Firestore deletion value; original action retained")
    }

    fun encode(value: Any?): JSONObject = when (value) {
        null, JSONObject.NULL -> JSONObject().put("nullValue", JSONObject.NULL)
        is String -> JSONObject().put("stringValue", value)
        is Boolean -> JSONObject().put("booleanValue", value)
        is Int, is Long -> JSONObject().put("integerValue", value.toString())
        is Number -> JSONObject().put("doubleValue", value.toDouble())
        is TimestampValue -> JSONObject().put("timestampValue", value.text)
        is Map<*, *> -> JSONObject().put("mapValue", JSONObject().put("fields", JSONObject().apply {
            value.forEach { (key, item) -> require(key is String); put(key, encode(item)) }
        }))
        is Collection<*> -> JSONObject().put("arrayValue", JSONObject().put("values", JSONArray().apply {
            value.forEach { put(encode(it)) }
        }))
        else -> throw IllegalArgumentException("Unsupported Firestore deletion field; original action retained")
    }
}
