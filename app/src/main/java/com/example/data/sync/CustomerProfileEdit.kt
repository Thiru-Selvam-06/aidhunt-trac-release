package com.example.data.sync

import com.example.data.entity.CustomerEntity
import org.json.JSONObject

/** An edit carries intent for each changed profile field, never a stale ledger snapshot. */
object CustomerProfileEdit {
    const val PAYLOAD_KEY = "customerProfilePatchJson"
    private val fields = setOf("name", "phone", "location")

    fun capture(before: CustomerEntity, after: CustomerEntity): String {
        require(before.id == after.id && before.workspaceId == after.workspaceId) { "Customer identity cannot change in a profile edit" }
        require(after.name.isNotBlank()) { "Customer name is required" }
        val result = JSONObject()
        val old = values(before); val new = values(after)
        for (field in fields) if (old[field] != new[field])
            result.put(field, JSONObject().put("before", old.getValue(field)).put("after", new.getValue(field)))
        return result.toString()
    }

    fun changes(patch: String, current: Map<String, Any?>): Map<String, String> {
        val json = JSONObject(patch)
        require(json.keys().asSequence().all { it in fields }) { "Invalid customer profile field" }
        return json.keys().asSequence().associateWith { field ->
            val edit = json.getJSONObject(field)
            val before = edit.getString("before"); val after = edit.getString("after")
            val stored = current[field] as? String ?: ""
            require(stored == before || stored == after) { "Customer conflict: $field changed on another device; original edit retained for review" }
            if (field == "name") require(after.isNotBlank()) { "Customer name is required" }
            after
        }
    }

    fun apply(current: CustomerEntity, patch: String): CustomerEntity {
        val edits = changes(patch, values(current))
        return current.copy(name = edits["name"] ?: current.name,
            phone = edits["phone"] ?: current.phone, location = edits["location"] ?: current.location)
    }

    private fun values(customer: CustomerEntity): Map<String, String> = mapOf(
        "name" to customer.name, "phone" to customer.phone, "location" to customer.location)
}
