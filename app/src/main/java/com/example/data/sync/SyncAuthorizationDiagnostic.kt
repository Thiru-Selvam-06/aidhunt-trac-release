package com.example.data.sync

import com.example.data.entity.PendingOperationEntity
import org.json.JSONObject

/** Retained locally, without tokens or customer contact data. This is evidence, not a Rules verdict. */
object SyncAuthorizationDiagnostic {
    fun describe(op: PendingOperationEntity, authUid: String?, stage: String): String = runCatching {
        val data = JSONObject(op.payloadJson)
        val created = data.optLong("createdAt")
        "stage=$stage; app=${com.example.BuildConfig.VERSION_NAME}; protocol=${data.optInt("operationProtocolVersion")}; " +
            "authUid=${authUid.orEmpty()}; actorUid=${op.actorUid}; workspace=${op.workspaceId}; " +
            "creator=${data.optString("createdByUid")}; beneficiary=${data.optString("entryForUid")}; " +
            "collector=${data.optString("collectorUid", data.optString("collectedByUid"))}; " +
            "createdAt=$created; actionTime=${op.actionTime}; createdAfterAction=${created > op.actionTime}"
    }.getOrDefault("stage=$stage; original payload retained")
}
