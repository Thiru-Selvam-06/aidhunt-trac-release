"use strict";

const { HttpsError } = require("firebase-functions/v2/https");
const { FieldPath, Timestamp, FieldValue } = require("firebase-admin/firestore");
const { activeMember, eventForOwner, validId } = require("./owner-activity");

const installationPattern = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/;
const eventPattern = /^[a-f0-9]{64}$/;
const tokenPattern = /^[A-Za-z0-9:_-]{1,1024}$/;
const pageSize = 50;

async function ownerActivitySession(db, request) {
  const uid = request.auth?.uid;
  if (!validId(uid)) throw new HttpsError("unauthenticated", "Sign in first");
  const { workspaceId, action, installationId, token, language, eventId, afterId } = request.data ?? {};
  if (!validId(workspaceId) || !["register", "unregister", "history", "event"].includes(action))
    throw new HttpsError("invalid-argument", "Invalid activity request");
  const workspace = db.collection("workspaces").doc(workspaceId);
  const events = workspace.collection("ownerActivityEvents");
  return db.runTransaction(async tx => {
    const [root, member] = await Promise.all([tx.get(workspace), tx.get(workspace.collection("members").doc(uid))]);
    if (root.get("ownerUid") !== uid || !activeMember(member, workspaceId, uid, "OWNER"))
      throw new HttpsError("permission-denied", "Original Owner access required");
    if (action === "register" || action === "unregister") {
      if (typeof installationId !== "string" || !installationPattern.test(installationId))
        throw new HttpsError("invalid-argument", "Invalid installation");
      const ref = workspace.collection("ownerPushDevices").doc(installationId);
      const registration = await tx.get(ref);
      if (registration.exists && registration.get("uid") !== uid)
        throw new HttpsError("permission-denied", "Installation belongs to another account");
      if (action === "unregister") {
        if (registration.exists) tx.delete(ref);
      } else {
        if (typeof token !== "string" || !tokenPattern.test(token) || !["EN", "TA"].includes(language))
          throw new HttpsError("invalid-argument", "Invalid registration");
        tx.set(ref, { uid, token, language, updatedAt: FieldValue.serverTimestamp() });
      }
      return { ok: true };
    }
    if (action === "event") {
      if (typeof eventId !== "string" || !eventPattern.test(eventId)) throw new HttpsError("invalid-argument", "Invalid event");
      const item = eventForOwner(await tx.get(events.doc(eventId)), uid);
      if (!item) throw new HttpsError("not-found", "Activity unavailable");
      return { item };
    }
    let query = events.orderBy("recordedAt").orderBy(FieldPath.documentId());
    if (afterId) {
      if (typeof afterId !== "string" || !eventPattern.test(afterId)) throw new HttpsError("invalid-argument", "Invalid cursor");
      const anchor = await tx.get(events.doc(afterId));
      if (anchor.get("recipientUid") === uid && anchor.get("recordedAt")) query = query.startAfter(anchor.get("recordedAt"), anchor.id);
      else throw new HttpsError("not-found", "History cursor unavailable");
    } else {
      // A fresh installation can reconcile recent activity without replaying years of system alerts.
      query = query.startAt(Timestamp.fromMillis(Date.now() - 7 * 86400000));
    }
    const page = await tx.get(query.limit(pageSize));
    return { items: page.docs.map(doc => eventForOwner(doc, uid)).filter(Boolean),
      nextId: page.docs.at(-1)?.id ?? null, more: page.size === pageSize };
  });
}
module.exports = { ownerActivitySession };
