package com.example
import com.example.data.firebase.*
import org.junit.Assert.*
import org.junit.Test
class PurgeReconciliationProtocolTest {
 private val manifest=mapOf<String,Any?>("purgeOperationId" to "stable", "purgeActorUid" to "owner", "purgeActionTime" to 12345L)
 private val entry=mapOf<String,Any?>("workspaceId" to "a", "createdAt" to 1000L, "financialAttributionUid" to "partner", "totalAmount" to 100.0,"amountReceived" to 40.0,"pendingAmount" to 60.0)
 private val dependencies=listOf(mapOf("entityType" to "PAYMENT","entityId" to "3"))
 @Test fun retriesReusePersistedActionAndPreserveCreationAndAttribution() {
  val first=PurgeReconciliationProtocol.fromManifest(manifest,"owner").entryUpdate("a","removed","workspaces/a/entries/1",entry,30.0,dependencies)
  val retry=PurgeReconciliationProtocol.fromManifest(manifest.toMap(),"owner").entryUpdate("a","removed","workspaces/a/entries/1",entry,30.0,dependencies)
  assertEquals(first,retry);assertEquals(2,first["protocolVersion"]);assertEquals("UPSERT",first["operationType"]);assertEquals("stable:1",first["operationId"]);assertEquals(12345L,first["actionTime"]);assertEquals(dependencies,first["dependencies"])
  assertFalse(first.containsKey("createdAt"));assertFalse(first.containsKey("authorizationCreatedAt"));assertFalse(first.containsKey("financialAttributionUid"));assertEquals(100.0,(first["amountReceived"] as Double)+(first["pendingAmount"] as Double),0.0)
 }
 @Test fun missingLegacyMetadataRequiresReviewWithoutInventingTime(){for(key in manifest.keys)try{PurgeReconciliationProtocol.fromManifest(manifest-key,"owner");fail(key)}catch(_:WorkspaceUpgradeRequiredException){}}
 @Test fun anotherActorCannotResume(){try{PurgeReconciliationProtocol.fromManifest(manifest,"other");fail()}catch(_:IllegalArgumentException){}}
 @Test fun invalidScopeIsRejected(){for(path in listOf("workspaces/b/entries/1","workspaces/a/customers/1","workspaces/a/entries/1/nested/2"))try{PurgeReconciliationProtocol.fromManifest(manifest,"owner").entryUpdate("a","removed",path,entry,30.0,dependencies);fail(path)}catch(_:IllegalArgumentException){}}
 @Test fun invalidAmountsAreRejected(){for(amount in listOf(-1.0,0.0,41.0,Double.NaN,Double.POSITIVE_INFINITY))try{PurgeReconciliationProtocol.fromManifest(manifest,"owner").entryUpdate("a","removed","workspaces/a/entries/1",entry,amount,dependencies);fail()}catch(_:IllegalArgumentException){}}
 @Test fun staleTotalsAndWrongWorkspaceAreRejected(){for(change in listOf(mapOf("totalAmount" to 99.0),mapOf("workspaceId" to "b"),mapOf("isDeleted" to true)))try{PurgeReconciliationProtocol.fromManifest(manifest,"owner").entryUpdate("a","removed","workspaces/a/entries/1",entry+change,30.0,dependencies);fail()}catch(_:IllegalArgumentException){}}
}
