"use strict";
// Restore rehearsal is deliberately restricted to a separate, local demo project.
const fs = require("node:fs");
const assert = require("node:assert/strict");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, Timestamp, GeoPoint } = require("firebase-admin/firestore");
async function main() {
  if (process.env.FIRESTORE_EMULATOR_HOST !== "127.0.0.1:18180") throw new Error("Restore verification requires the local emulator on 127.0.0.1:18180");
  const file = process.argv[2];
  if (!file) throw new Error("Usage: node functions/verify-workspace-backup.js TYPED_EXPORT.json");
  const backup = JSON.parse(fs.readFileSync(file, "utf8"));
  if (backup.format !== "aidhunt-firestore-typed-v1" || !Array.isArray(backup.records)) throw new Error("Invalid export format");
  initializeApp({ projectId: "demo-aidhunt-restore" });
  const db = getFirestore();
  db.settings({ useBigInt: true });
  function decode(item) {
    switch (item.type) {
      case "int64": return BigInt(item.value);
      case "timestamp": return new Timestamp(item.seconds, item.nanoseconds);
      case "geoPoint": return new GeoPoint(item.latitude, item.longitude);
      case "reference": return db.doc(item.path);
      case "bytes": return Buffer.from(item.value, "base64");
      case "array": return item.value.map(decode);
      case "map": return Object.fromEntries(Object.entries(item.value).map(([key, value]) => [key, decode(value)]));
      case "scalar": return item.value;
      default: throw new Error("Unsupported export type");
    }
  }
  for (let offset = 0; offset < backup.records.length; offset += 20) {
    const batch = db.batch();
    for (const record of backup.records.slice(offset, offset + 20)) {
      if (!record.path.startsWith(`workspaces/${backup.workspaceId}/` ) && record.path !== `workspaces/${backup.workspaceId}`) throw new Error("Export crossed workspace boundary");
      batch.set(db.doc(record.path), decode(record.data));
    }
    await batch.commit();
  }
  for (const record of backup.records) {
    const restored = await db.doc(record.path).get();
    assert.equal(restored.exists, true, record.path);
    assert.deepEqual(restored.data(), decode(record.data), record.path);
  }
  console.log(`Restore rehearsal passed for ${backup.records.length} documents in demo-aidhunt-restore. No live project was modified.`);
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
