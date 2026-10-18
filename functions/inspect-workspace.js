"use strict";
// Read-only live inventory. Credentials come from Application Default Credentials;
// this program never changes Firestore or tries to infer legacy identities.
const fs = require("node:fs");
const path = require("node:path");
const crypto = require("node:crypto");
const { initializeApp, applicationDefault } = require("firebase-admin/app");
const { getFirestore, Timestamp, GeoPoint, DocumentReference } = require("firebase-admin/firestore");

function encode(value) {
  if (typeof value === "bigint") return { type: "int64", value: value.toString() };
  if (value instanceof Timestamp) return { type: "timestamp", seconds: value.seconds, nanoseconds: value.nanoseconds };
  if (value instanceof GeoPoint) return { type: "geoPoint", latitude: value.latitude, longitude: value.longitude };
  if (value instanceof DocumentReference) return { type: "reference", path: value.path };
  if (Buffer.isBuffer(value)) return { type: "bytes", value: value.toString("base64") };
  if (Array.isArray(value)) return { type: "array", value: value.map(encode) };
  if (value !== null && typeof value === "object") return { type: "map", value: Object.fromEntries(Object.entries(value).map(([key, item]) => [key, encode(item)])) };
  return { type: "scalar", value };
}

async function main() {
  const [projectId, workspaceId, outputDirectory] = process.argv.slice(2);
  if (!projectId || !workspaceId || workspaceId.includes("/") || !outputDirectory)
    throw new Error("Usage: node functions/inspect-workspace.js PROJECT WORKSPACE OUTPUT_DIRECTORY");
  initializeApp({ projectId, credential: applicationDefault() });
  const db = getFirestore();
  db.settings({ useBigInt: true });
  const records = [], findings = [];
  async function visit(ref) {
    const snapshot = await ref.get();
    if (snapshot.exists) {
      const data = snapshot.data();
      records.push({ path: ref.path, data: encode(data) });
      const collection = ref.parent.id;
      if (["entries", "expenses", "withdrawals", "tractors", "extensions", "customers"].includes(collection)) {
        for (const field of ["workspaceId", "createdByUid", "createdAt"])
          if (!data[field]) findings.push({ path: ref.path, issue: `Missing ${field}; explicit backfill required` });
      }
      if (collection === "entries" && (typeof data.isHistorical !== "boolean" || !data.entryForUid))
        findings.push({ path: ref.path, issue: "Missing explicit historical marker or beneficiary UID; do not infer from date/name" });
      if (collection === "payments") findings.push({ path: ref.path, issue: "Legacy payment path; reconcile allocations before migration to collections" });
      if (collection === "collections" && (!data.status || !data.collectedByUid || !data.jobEntryId))
        findings.push({ path: ref.path, issue: "Collection status, collector, or source identity missing" });
      if (collection === "members" && (!["OWNER", "PARTNER", "OPERATOR"].includes(data.role) || !data.status))
        findings.push({ path: ref.path, issue: "Noncanonical membership; explicit role/status mapping required" });
    }
    for (const collection of await ref.listCollections())
      for (const child of await collection.listDocuments()) await visit(child);
  }
  await visit(db.doc(`workspaces/${workspaceId}`));
  if (!records.length) throw new Error("Workspace missing or inaccessible");
  const output = path.resolve(outputDirectory);
  fs.mkdirSync(output, { recursive: true });
  const backup = path.join(output, `${workspaceId}-${Date.now()}.json`);
  const content = JSON.stringify({ format: "aidhunt-firestore-typed-v1", projectId, workspaceId, records }, null, 2);
  fs.writeFileSync(backup, content, { flag: "wx" });
  const reread = fs.readFileSync(backup, "utf8");
  if (reread !== content || JSON.parse(reread).records.length !== records.length) throw new Error("Export verification failed");
  const report = { backup, sha256: crypto.createHash("sha256").update(reread).digest("hex"), documentCount: records.length,
    findings, warning: "Byte verification is not a restore rehearsal. Restore this typed export into an isolated emulator before live cutover. Export is sequential, not a point-in-time snapshot; freeze writes or use a managed backup for cutover." };
  fs.writeFileSync(`${backup}.inventory.json`, JSON.stringify(report, null, 2), { flag: "wx" });
  console.log(`Read-only export saved: ${backup}; ${records.length} documents; ${findings.length} findings. No database writes performed.`);
}
main().catch(error => { console.error(error.message); process.exitCode = 1; });
