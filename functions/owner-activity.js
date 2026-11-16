"use strict";

const { createHash } = require("node:crypto");

const REGION = "asia-south1";
const jobFields = ["customerId", "customerName", "customerPhone", "customerLocation", "operatorName",
  "tractorId", "tractorLabel", "workType", "startTimeMillis", "endTimeMillis", "durationMinutes", "hourlyRate",
  "totalAmount", "notes"];
const expenseFields = ["expenseType", "amount", "description", "paidBy", "paidByUid", "paidByPartner",
  "dateTimestamp", "tractorId", "tractorLabel", "operatorName", "relatedJobId", "expenseForUid"];
const changes = (before, after, fields) => fields.some(field => (before[field] ?? null) !== (after[field] ?? null));
const validId = value => typeof value === "string" && /^[A-Za-z0-9:_-]{1,160}$/.test(value);
const supportedTypes = new Set(["JOB_CREATED", "OLD_ENTRY_CREATED", "JOB_EDITED", "JOB_DELETED",
  "COLLECTION", "COLLECTION_REVERSED", "EXPENSE", "EXPENSE_EDITED", "EXPENSE_DELETED", "WITHDRAWAL"]);
const invalidToken = new Set(["messaging/registration-token-not-registered", "messaging/invalid-registration-token"]);
const permanentSendFailure = new Set(["messaging/mismatched-credential", "messaging/sender-id-mismatch"]);
const leaseMs = 120000;
const tokenAgeMs = 45 * 86400000;

function confirmedActivity(collection, before, after) {
  if (collection === "entryDeletionProofs" || collection === "cashDeleteProofs") {
    if (before || !after || !/^[1-9][0-9]*$/.test(after.recordId ?? "")) return null;
    if (collection === "entryDeletionProofs") return { data: after, type: "JOB_DELETED", deletedRecord: ["entries", after.recordId] };
    return after.kind === "EXPENSE" ? { data: after, type: "EXPENSE_DELETED", deletedRecord: ["expenses", after.recordId] } : null;
  }
  // Legacy Rules authorize the final expense delete only after validating its DELETE tombstone.
  if (!after) return collection === "expenses" && before?.isDeleted === true && before.operationType === "DELETE"
    ? { data: before, type: "EXPENSE_DELETED" } : null;
  if (after.isDeleted || after.operationType !== "UPSERT" || after.operationId === before?.operationId) return null;
  if (collection === "entries") {
    if (!before) return { data: after, type: after.isHistorical === true ? "OLD_ENTRY_CREATED" : "JOB_CREATED" };
    if (changes(before, after, jobFields)) return { data: after, type: "JOB_EDITED" };
  }
  if (collection === "collections") {
    if (!before && after.status === "ACTIVE") return { data: after, type: "COLLECTION" };
    if (before?.status === "ACTIVE" && after.status === "REVERSED") return { data: after, type: "COLLECTION_REVERSED" };
  }
  if (collection === "expenses" && after.paidBy !== "Loan Funded" && after.fundingSource !== "EXTERNAL_LOAN") {
    if (!before) return { data: after, type: "EXPENSE" };
    if (changes(before, after, expenseFields)) return { data: after, type: "EXPENSE_EDITED" };
  }
  if (collection === "withdrawals" && !before && !after.borrowingRequestId && after.operationSource !== "BORROWING") {
    return { data: after, type: "WITHDRAWAL" };
  }
  return null;
}

function digest(value) {
  return createHash("sha256").update(JSON.stringify(value)).digest("hex");
}

function eventIdentity(workspaceId, operationId, type) {
  // Preserve existing durable identifiers for old trigger versions and both paths of expense deletion.
  const previousType = { OLD_ENTRY_CREATED: "JOB_CREATED", COLLECTION_REVERSED: "COLLECTION",
    EXPENSE_EDITED: "EXPENSE", EXPENSE_DELETED: "EXPENSE" }[type] ?? type;
  return digest([workspaceId, operationId, previousType]);
}

function activeMember(snapshot, workspace, uid, role) {
  return snapshot.exists && (snapshot.get("workspaceId") ?? workspace) === workspace &&
    (snapshot.get("uid") ?? uid) === uid && String(snapshot.get("role")).toUpperCase() === role &&
    String(snapshot.get("status")).toUpperCase() === "ACTIVE";
}

function safeActorName(value) {
  if (typeof value !== "string") return "";
  const label = value.replace(/[\p{Cc}\p{Cf}]/gu, " ").trim();
  return /\p{L}/u.test(label) && !/[@+]|[0-9]{5}/u.test(label) ? [...label].slice(0, 50).join("") : "";
}

function eventForOwner(snapshot, uid) {
  if (!snapshot.exists || snapshot.get("recipientUid") !== uid || !supportedTypes.has(snapshot.get("eventType"))) return null;
  return { id: snapshot.id, type: snapshot.get("eventType"), actorName: safeActorName(snapshot.get("actorName")) };
}

async function publishOwnerActivity({ db, messaging, event, collection, fieldValue, emulator, now = Date.now }) {
  const { wsId, recordId } = event.params;
  const before = event.data?.before?.data();
  const after = event.data?.after?.data();
  const activity = confirmedActivity(collection, before, after);
  if (!activity || !validId(wsId)) return;
  const { data, type, deletedRecord } = activity;
  // Source paths are committed ledger writes with authenticated actor envelopes, or immutable deletion proofs.
  if (data.workspaceId !== wsId || !validId(data.actorUid) || !validId(data.operationId) ||
      (before?.paidBy === "Loan Funded" || before?.fundingSource === "EXTERNAL_LOAN" || data.fundingSource === "EXTERNAL_LOAN")) return;
  if (["unauthenticated", "system", "api_key"].includes(event.authType)) return;
  if (event.authType === "service_account" && collection !== "entryDeletionProofs") return;
  // The existing Rules bind the committed envelope to Firebase Auth. Firebase Tools
  // substitutes a fixed principal in Firestore events even for authenticated demo users.
  const demoPrincipal = emulator && event.project === "demo-aidhunt-v16" &&
    event.authType === "unknown" && event.authId === "fake-auth-id@gmail.com";
  if (!demoPrincipal && event.authType !== "service_account" && event.authId && event.authId !== data.actorUid) return;
  const proofId = deletedRecord?.[1];
  if (proofId && recordId !== (collection === "cashDeleteProofs" ? "EXPENSE_" + proofId : proofId)) return;
  if (!deletedRecord && (data.id == null || String(data.id) !== recordId || Number(data.protocolVersion) !== 2)) return;
  const workspace = db.collection("workspaces").doc(wsId);
  const id = eventIdentity(wsId, data.operationId, type);
  const eventRef = workspace.collection("ownerActivityEvents").doc(id);
  const recipientUid = await db.runTransaction(async tx => {
    const [root, actor, existing] = await Promise.all([
      tx.get(workspace), tx.get(workspace.collection("members").doc(data.actorUid)), tx.get(eventRef)
    ]);
    const ownerUid = root.get("ownerUid");
    if (!validId(ownerUid) || ownerUid === data.actorUid || !activeMember(actor, wsId, data.actorUid, "PARTNER")) return null;
    const owner = await tx.get(workspace.collection("members").doc(ownerUid));
    if (!activeMember(owner, wsId, ownerUid, "OWNER")) return null;
    if (existing.exists) return existing.get("recipientUid") === ownerUid &&
      (existing.get("actorUid") ?? data.actorUid) === data.actorUid && !existing.get("deliverySweepCompletedAt") &&
      !existing.get("deliveredAt") ? ownerUid : null;
    if (deletedRecord && (await tx.get(workspace.collection(deletedRecord[0]).doc(proofId))).exists) return null;
    tx.create(eventRef, { recipientUid: ownerUid, actorUid: data.actorUid, operationId: data.operationId,
      actorName: safeActorName(actor.get("displayName")), eventType: type, recordedAt: fieldValue.serverTimestamp() });
    return ownerUid;
  });
  if (!recipientUid || emulator) return;
  const registrations = await workspace.collection("ownerPushDevices").where("uid", "==", recipientUid).get();
  const byToken = new Map();
  for (const doc of registrations.docs) {
    const token = doc.get("token");
    if (typeof token !== "string" || !token || now() - (doc.get("updatedAt")?.toMillis?.() ?? 0) > tokenAgeMs) continue;
    if (!byToken.has(token)) byToken.set(token, []);
    byToken.get(token).push(doc.ref);
  }
  const attempts = await Promise.allSettled([...byToken].map(([token, refs]) =>
    sendToToken({ db, messaging, token, refs, eventRef, id, wsId, recipientUid, fieldValue, now })));
  if (attempts.some(attempt => attempt.status === "rejected")) throw new Error("Owner notification delivery needs retry");
  // This only means all eligible token attempts were handled. FCM acceptance is not device display.
  await eventRef.update({ deliverySweepCompletedAt: fieldValue.serverTimestamp() });
}

async function sendToToken({ db, messaging, token, refs, eventRef, id, wsId, recipientUid, fieldValue, now }) {
  const deliveryRef = eventRef.collection("deliveries").doc(digest(token));
  const claim = await db.runTransaction(async tx => {
    const [state, ...registrations] = await Promise.all([tx.get(deliveryRef), ...refs.map(ref => tx.get(ref))]);
    if (state.get("outcome") || !registrations.some(doc => doc.get("uid") === recipientUid && doc.get("token") === token)) return null;
    if (Number(state.get("leaseUntil")) > now()) throw new Error("Owner token attempt in progress");
    const generation = Number(state.get("generation") ?? 0) + 1;
    tx.set(deliveryRef, { generation, leaseUntil: now() + leaseMs });
    return generation;
  });
  if (claim == null) return;
  let outcome = "FCM_ACCEPTED";
  let failure;
  try {
    await messaging.send({ token, android: { priority: "high", ttl: 86400000 },
      data: { kind: "TRAC_OWNER_ACTIVITY", workspaceId: wsId, recipientUid, eventId: id } });
  } catch (error) {
    if (invalidToken.has(error.code)) outcome = "TOKEN_INVALID";
    else if (permanentSendFailure.has(error.code)) outcome = "SENDER_MISMATCH";
    else { outcome = null; failure = error; }
  }
  await db.runTransaction(async tx => {
    const [state, ...registrations] = await Promise.all([tx.get(deliveryRef), ...refs.map(ref => tx.get(ref))]);
    if (Number(state.get("generation")) !== claim) throw new Error("Owner token attempt lease expired");
    if (outcome === "TOKEN_INVALID") registrations.filter(doc => doc.get("uid") === recipientUid && doc.get("token") === token)
      .forEach(doc => tx.delete(doc.ref));
    if (outcome) tx.update(deliveryRef, { outcome, finishedAt: fieldValue.serverTimestamp(), leaseUntil: 0 });
    else tx.update(deliveryRef, { leaseUntil: 0 });
  });
  if (failure) throw new Error("FCM temporarily unavailable");
}

module.exports = { REGION, activeMember, confirmedActivity, eventForOwner, eventIdentity, publishOwnerActivity, validId };
