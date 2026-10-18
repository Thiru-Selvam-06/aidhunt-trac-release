package com.example.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.Blob
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.GeoPoint
import org.json.JSONArray
import org.json.JSONObject

/** Lossless JSON backup values. Unknown Firestore types fail before destructive work. */
object FirestoreBackupCodec {
    fun encode(value: Any?): JSONObject {
        fun tagged(type: String, data: Any?) = JSONObject().put("type", type).put("value", data ?: JSONObject.NULL)
        return when (value) {
            null -> tagged("null", null)
            is String -> tagged("string", value)
            is Boolean -> tagged("boolean", value)
            is Int, is Long -> tagged("integer", value.toString())
            is Float, is Double -> tagged("double", value.toString())
            is Timestamp -> tagged("timestamp", JSONObject().put("seconds", value.seconds.toString()).put("nanoseconds", value.nanoseconds))
            is GeoPoint -> tagged("geopoint", JSONObject().put("latitude", value.latitude).put("longitude", value.longitude))
            is Blob -> tagged("blob", android.util.Base64.encodeToString(value.toBytes(), android.util.Base64.NO_WRAP))
            is DocumentReference -> tagged("reference", value.path)
            is Map<*, *> -> tagged("map", JSONObject().also { result -> value.forEach { (key, entry) ->
                require(key is String) { "Backup map keys must be strings" }; result.put(key, encode(entry))
            } })
            is List<*> -> tagged("list", JSONArray().also { result -> value.forEach { result.put(encode(it)) } })
            else -> throw IllegalArgumentException("Unsupported backup type: ${value.javaClass.name}")
        }
    }

    fun decode(value: JSONObject, reference: (String) -> DocumentReference): Any? = when (value.getString("type")) {
        "null" -> null
        "string" -> value.getString("value")
        "boolean" -> value.getBoolean("value")
        "integer" -> value.getString("value").toLong()
        "double" -> value.getString("value").toDouble()
        "timestamp" -> value.getJSONObject("value").let { Timestamp(it.getString("seconds").toLong(), it.getInt("nanoseconds")) }
        "geopoint" -> value.getJSONObject("value").let { GeoPoint(it.getDouble("latitude"), it.getDouble("longitude")) }
        "blob" -> Blob.fromBytes(android.util.Base64.decode(value.getString("value"), android.util.Base64.NO_WRAP))
        "reference" -> reference(value.getString("value"))
        "map" -> value.getJSONObject("value").let { data -> data.keys().asSequence().associateWith { decode(data.getJSONObject(it), reference) } }
        "list" -> value.getJSONArray("value").let { data -> (0 until data.length()).map { decode(data.getJSONObject(it), reference) } }
        else -> throw IllegalArgumentException("Unsupported backup encoding")
    }
}
