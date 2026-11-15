"use strict";

const { createHash } = require("node:crypto");

function confirmedActivity(collection, before, after) {
  if (collection === "entryDeletionProofs" || collection === "cashDeleteProofs") {
    if (before || !after) return null;
    if (collection === "entryDeletionProofs") return { data: after, type: "JOB_DELETED" };
    return after.kind === "EXPENSE" ? { data: after, type: "EXPENSE" } : null;
  }
  if (!after) {
    return collection === "expenses" && before?.isDeleted === true && before.operationType === "DELETE"
      ? { data: before, type: "EXPENSE" } : null;
  }
  if (after.isDeleted || after.operationType !== "UPSERT" || after.operationId === before?.operationId) return null;
  if (collection === "entries" && after.collectionId && before &&
      (after.amountReceived !== before.amountReceived || after.pendingAmount !== before.pendingAmount)) return null;
  if (collection === "entries") return { data: after, type: before ? "JOB_EDITED" : "JOB_CREATED" };
  if (collection === "collections" && (!before || (before.status === "ACTIVE" && after.status === "REVERSED")))
    return { data: after, type: "COLLECTION" };
  if (collection === "expenses") return { data: after, type: "EXPENSE" };
  if (collection === "withdrawals" && !before && !after.borrowingRequestId && after.operationSource !== "BORROWING")
    return { data: after, type: "WITHDRAWAL" };
  return null;
}

function eventIdentity(workspaceId, operationId, type) {
  return createHash("sha256").update(JSON.stringify([workspaceId, operationId, type])).digest("hex");
}

async function publishOwnerActivity({ db, messaging, event, collection, fieldValue, emulator }) {
  const { wsId } = event.params;
  const activity = confirmedActivity(collection, event.data?.before?.data(), event.data?.after?.data());
  if (!activity) return;
  const { data, type } = activity;
  if (data.workspaceId !== wsId || typeof data.actorUid !== "string" || typeof data.operationId !== "string" ||
      !data.actorUid || !data.operationId || data.actorUid.includes("/") || data.operationId.length > 160) return;
  const workspace = db.collection("workspaces").doc(wsId);
  const id = eventIdentity(wsId, data.operationId, type);
  const eventRef = workspace.collection("ownerActivityEvents").doc(id);
  const recipientUid = await db.runTransaction(async tx => {
    const [root, actor, existing] = await Promise.all([
      tx.get(workspace), tx.get(workspace.collection("members").doc(data.actorUid)), tx.get(eventRef)
    ]);
    const ownerUid = root.get("ownerUid");
    if (typeof ownerUid !== "string" || !ownerUid || ownerUid.includes("/") || ownerUid === data.actorUid) return null;
    if (String(actor.get("role")).toUpperCase() !== "PARTNER" || String(actor.get("status")).toUpperCase() !== "ACTIVE") return null;
    const owner = await tx.get(workspace.collection("members").doc(ownerUid));
    if (String(owner.get("role")).toUpperCase() !== "OWNER" || String(owner.get("status")).toUpperCase() !== "ACTIVE") return null;
    if (existing.exists) {
      return existing.get("recipientUid") === ownerUid && !existing.get("deliveredAt") ? ownerUid : null;
    }
    tx.create(eventRef, { recipientUid: ownerUid, eventType: type, recordedAt: fieldValue.serverTimestamp() });
    return ownerUid;
  });
  if (!recipientUid || emulator) return;
  const registrations = await workspace.collection("ownerPushDevices").where("uid", "==", recipientUid).get();
  const byToken = new Map(registrations.docs.filter(doc => typeof doc.get("token") === "string" && doc.get("token"))
    .map(doc => [doc.get("token"), doc]));
  for (const [token, registration] of byToken) {
    try {
      await messaging.send({
        token,
        android: { priority: "high" },
        data: { kind: "TRAC_OWNER_ACTIVITY", workspaceId: wsId, recipientUid, eventId: id, eventType: type }
      });
    } catch (error) {
      if (error.code !== "messaging/registration-token-not-registered") throw error;
      await db.runTransaction(async tx => {
        const fresh = await tx.get(registration.ref);
        if (fresh.get("token") === token) tx.delete(registration.ref);
      });
    }
  }
  await eventRef.update({ deliveredAt: fieldValue.serverTimestamp() });
}

module.exports = { confirmedActivity, eventIdentity, publishOwnerActivity };
