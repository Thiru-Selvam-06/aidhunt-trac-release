"use strict";
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getAuth } = require("firebase-admin/auth");
const { validateInvitation } = require("./invitation-policy");
initializeApp();
const db = getFirestore();
db.settings({ useBigInt: true }); // Android record IDs are 63-bit Longs, beyond JavaScript's exact Number range.
function requiredString(data, key) {
  const value = data?.[key];
  if (typeof value !== "string" || !value.trim()) throw new HttpsError("invalid-argument", `${key} is required`);
  if (["workspaceId", "invitationId", "customerId"].includes(key) && (value.includes("/") || value.length > 512)) throw new HttpsError("invalid-argument", "Invalid document identity");
  return value.trim();
}
function authenticatedUid(request) {
  const uid=request.auth?.uid;
  if(typeof uid!=="string" || !uid) throw new HttpsError("unauthenticated", "Sign in first");
  return uid;
}
async function requireOwner(uid, wsId) {
  if (!uid) throw new HttpsError("unauthenticated", "Sign in first");
  const [member, ws] = await Promise.all([db.doc(`workspaces/${wsId}/members/${uid}`).get(), db.doc(`workspaces/${wsId}`).get()]);
  if (!member.exists || String(member.get("status")).toUpperCase() !== "ACTIVE" || String(member.get("role")).toUpperCase() !== "OWNER" || ws.get("ownerUid") !== uid) {
    throw new HttpsError("permission-denied", "Only the active workspace Owner can invite members");
  }
}
exports.beginWorkspaceInvitation = onCall(async request => {
  authenticatedUid(request);
  const wsId = requiredString(request.data, "workspaceId");
  const phone = requiredString(request.data, "phone");
  const role = requiredString(request.data, "role").toUpperCase();
  const name = requiredString(request.data, "name");
  await requireOwner(request.auth?.uid, wsId);
  if (!/^\+[1-9][0-9]{7,14}$/.test(phone) || !["PARTNER", "OPERATOR"].includes(role)) throw new HttpsError("invalid-argument", "Choose a valid phone and Partner or Operator role");
  const operationId = request.data.operationId;
  if (operationId != null && (typeof operationId !== "string" || !/^[A-Za-z0-9-]{1,128}$/.test(operationId)))
    throw new HttpsError("invalid-argument", "Invalid invitation operation identity");
  const ref = operationId == null ? db.collection(`workspaces/${wsId}/invitations`).doc() : db.doc(`workspaces/${wsId}/invitations/${operationId}`);
  return db.runTransaction(async tx => {
    const [workspace, owner, prior] = await Promise.all([tx.get(db.doc(`workspaces/${wsId}`)),
      tx.get(db.doc(`workspaces/${wsId}/members/${request.auth.uid}`)), tx.get(ref)]);
    if (workspace.get("ownerUid") !== request.auth.uid || String(owner.get("role")).toUpperCase() !== "OWNER" ||
        String(owner.get("status")).toUpperCase() !== "ACTIVE") throw new HttpsError("permission-denied", "Owner access changed");
    const now = Date.now();
    if (prior.exists) {
      if (prior.get("workspaceId") !== wsId || prior.get("ownerUid") !== request.auth.uid || prior.get("phone") !== phone ||
          prior.get("role") !== role || prior.get("name") !== name) throw new HttpsError("permission-denied", "Invitation operation identity mismatch");
      if (prior.get("status") !== "PENDING" || Number(prior.get("expiresAt")) <= now)
        throw new HttpsError("failed-precondition", "Invitation expired or was already used. Start a new invitation.");
      return { invitationId: ref.id, expiresAt: Number(prior.get("expiresAt")) };
    }
    tx.create(ref, { workspaceId: wsId, ownerUid: request.auth.uid, phone, role, name, status: "PENDING", createdAt: now, expiresAt: now + 300000 });
    return { invitationId: ref.id, expiresAt: now + 300000 };
  });
});
exports.redeemWorkspaceInvitation = onCall(async request => {
  authenticatedUid(request);
  const wsId = requiredString(request.data, "workspaceId");
  const id = requiredString(request.data, "invitationId");
  const token = requiredString(request.data, "targetIdToken");
  await requireOwner(request.auth?.uid, wsId);
  const claims = await getAuth().verifyIdToken(token, true);
  const invitationRef = db.doc(`workspaces/${wsId}/invitations/${id}`);
  return db.runTransaction(async tx => {
    const invitationDoc = await tx.get(invitationRef);
    if (!invitationDoc.exists) throw new HttpsError("not-found", "Invitation not found");
    const storedInvitation = invitationDoc.data();
    const invitation = { ...storedInvitation, createdAt: Number(storedInvitation.createdAt), expiresAt: Number(storedInvitation.expiresAt) };
    if (invitation.workspaceId !== wsId) throw new HttpsError("permission-denied", "Invitation workspace mismatch");
    if (invitation.status === "USED") {
      const [receipt, member, ownerMember, ownerWorkspace] = await Promise.all([
        tx.get(db.doc(`invitationProofReceipts/${claims.uid}_${claims.auth_time}`)),
        tx.get(db.doc(`workspaces/${wsId}/members/${claims.uid}`)),
        tx.get(db.doc(`workspaces/${wsId}/members/${request.auth.uid}`)),
        tx.get(db.doc(`workspaces/${wsId}`))
      ]);
      if (String(ownerMember.get("status")).toUpperCase() !== "ACTIVE" ||
          String(ownerMember.get("role")).toUpperCase() !== "OWNER" || ownerWorkspace.get("ownerUid") !== request.auth.uid)
        throw new HttpsError("permission-denied", "Owner access changed");
      try { return require("./invitation-policy").validateAcceptedInvitation(invitation, request.auth.uid, claims,
        receipt.data(), member.data(), wsId, id); }
      catch (error) { throw new HttpsError("permission-denied", error.message); }
    }
    let target;
    try { target = validateInvitation(invitation, request.auth.uid, claims, Date.now(), (process.env.FUNCTIONS_EMULATOR === "true" || (process.env.INVITATION_SMS_ENABLED === "false" && (process.env.INVITATION_TEST_PHONES || "").split(",").map(p => p.trim()).includes(invitation.phone)))); }
    catch (error) { throw new HttpsError("permission-denied", error.message); }
    const memberRef = db.doc(`workspaces/${wsId}/members/${target.uid}`);
    const removedRef = db.doc(`workspaces/${wsId}/deletedUsers/${target.uid}`);
    const ownerRef = db.doc(`workspaces/${wsId}/members/${request.auth.uid}`);
    const proofRef = db.doc(`invitationProofReceipts/${target.uid}_${claims.auth_time}`);
    const directoryRef = db.doc(`phoneDirectory/${invitation.phone}`);
    const [existing, removed, owner, proof, directory] = await Promise.all([
      tx.get(memberRef), tx.get(removedRef), tx.get(ownerRef), tx.get(proofRef), tx.get(directoryRef)]);
    if (owner.get("status").toUpperCase() !== "ACTIVE" || owner.get("role").toUpperCase() !== "OWNER") throw new HttpsError("permission-denied", "Owner access changed");
    if (removed.exists || (existing.exists && String(existing.get("status")).toUpperCase() !== "ACTIVE"))
      throw new HttpsError("already-exists", "Removed membership cannot be reactivated in this workspace");
    if (existing.exists && String(existing.get("role")).toUpperCase() !== target.role)
      throw new HttpsError("permission-denied", "An invitation cannot change an existing member role");
    if (proof.exists) throw new HttpsError("permission-denied", "This OTP handshake was already consumed");
    if (directory.exists && (directory.get("uid") !== target.uid ||
        (directory.get("phone") != null && directory.get("phone") !== invitation.phone) ||
        (directory.get("phoneNumber") != null && directory.get("phoneNumber") !== invitation.phone)))
      throw new HttpsError("permission-denied", "Phone identity is bound to another account");
    const now = Date.now();
    if (!existing.exists) tx.create(memberRef, { uid: target.uid, workspaceId: wsId, role: target.role, status: "ACTIVE", displayName: invitation.name,
      phoneNumber: invitation.phone, joinedAt: now, addedByUid: request.auth.uid, invitedByUid: request.auth.uid });
    tx.set(db.doc(`userWorkspaceMemberships/${target.uid}/workspaces/${wsId}`), { workspaceId: wsId, ownerUid: request.auth.uid, role: target.role, status: "ACTIVE", joinedAt: now });
    // Directory maps phone to immutable UID; roles remain in workspace memberships.
    if (!directory.exists) tx.create(directoryRef, { uid: target.uid, phone: invitation.phone });
    tx.update(invitationRef, { status: "USED", acceptedByUid: target.uid, usedAt: now });
    tx.create(proofRef, { targetUid: target.uid, authTime: claims.auth_time, workspaceId: wsId, invitationId: id, usedAt: now });
    return { uid: target.uid, workspaceId: wsId, role: target.role };
  });
});

exports.deleteWorkspaceCustomer = onCall(async request => {
  const uid=authenticatedUid(request);
  require('./operation-protocol').validate(request.data,uid,'DELETE_CUSTOMER');
  const wsId = requiredString(request.data, "workspaceId");
  const id = requiredString(request.data, "customerId");
  if (!/^[0-9]{1,19}$/.test(id) || BigInt(id) > 9223372036854775807n) throw new HttpsError("invalid-argument", "Invalid Customer identity");
  await requireOwner(request.auth?.uid, wsId);
  const operationId = requiredString(request.data, "operationId");
  const actionTime = request.data.actionTime;
  const acknowledgement = { deleted: true, workspaceId: wsId, customerId: id, operationId, actionTime };
  return db.runTransaction(async tx => {
    const ws = db.doc(`workspaces/${wsId}`);
    const ref = ws.collection("customers").doc(id);
    const receipt = ws.collection("customerDeleteReceipts").doc(operationId);
    const [owner, customer, prior, entries, collections] = await Promise.all([
      tx.get(ws.collection("members").doc(request.auth.uid)), tx.get(ref), tx.get(receipt),
      tx.get(ws.collection("entries").where("customerId", "==", BigInt(id))),
      tx.get(ws.collection("collections").where("customerId", "==", BigInt(id)))
    ]);
    if (String(owner.get("role")).toUpperCase() !== "OWNER" || String(owner.get("status")).toUpperCase() !== "ACTIVE") throw new HttpsError("permission-denied", "Owner access changed");
    if (prior.exists) {
      if (prior.get("actorUid") !== uid || prior.get("customerId") !== id ||
          prior.get("workspaceId") !== wsId || Number(prior.get("actionTime")) !== actionTime)
        throw new HttpsError("permission-denied", "Customer deletion receipt mismatch");
      return acknowledgement;
    }
    if (!customer.exists) throw new HttpsError("failed-precondition", "Customer is missing without a confirmed deletion receipt");
    if (customer.get("workspaceId") !== wsId) throw new HttpsError("permission-denied", "Customer workspace mismatch");
    if (!entries.empty || !collections.empty) throw new HttpsError("failed-precondition", "Customer is referenced by retained business records");
    tx.delete(ref);
    tx.create(receipt, { workspaceId: wsId, customerId: id, actorUid: uid, operationId, actionTime,
      deletedAt: require("firebase-admin/firestore").FieldValue.serverTimestamp() });
    return acknowledgement;
  });
});

// A Room deletion is provisional until this transaction acknowledges the exact operation.
exports.deleteWorkspaceEntry = onCall(async request => {
  const uid = authenticatedUid(request);
  require('./operation-protocol').validate(request.data,uid,'DELETE_ENTRY');
  if (!request.data || typeof request.data!=="object" || Array.isArray(request.data) || Object.keys(request.data).some(key=>!["workspaceId","entryId","operationId","actionTime","protocolVersion","actorContext","operationType","dependencies"].includes(key))) throw new HttpsError("invalid-argument", "Delete Due accepts only workspaceId, entryId, operationId and actionTime");
  const wsId = requiredString(request.data, "workspaceId");
  const id = requiredString(request.data, "entryId");
  const operationId = requiredString(request.data, "operationId");
  if (!/^[1-9][0-9]{0,18}$/.test(id) || BigInt(id)>9223372036854775807n || !/^[a-zA-Z0-9-]{1,128}$/.test(operationId)) throw new HttpsError("invalid-argument", "Invalid deletion identity");
  const actionTime=request.data.actionTime;
  if(!Number.isSafeInteger(actionTime) || actionTime<=0 || actionTime>Date.now()) throw new HttpsError("invalid-argument", "Valid original actionTime required");
  const acknowledgement = {deleted:true,workspaceId:wsId,entryId:id,operationId,actionTime};
  return db.runTransaction(async tx => {
    const ws=db.doc(`workspaces/${wsId}`), ref=ws.collection("entries").doc(id);
    const receipt=ws.collection("entryDeleteReceipts").doc(operationId);
    const [workspace, member, job, prior] = await Promise.all([tx.get(ws),tx.get(ws.collection("members").doc(uid)),tx.get(ref),tx.get(receipt)]);
    const role=String(member.get("role")).toUpperCase();
    if (!workspace.exists || !member.exists || (member.get("workspaceId") != null && member.get("workspaceId")!==wsId) || String(member.get("status")).toUpperCase()!=="ACTIVE" || !["OWNER","PARTNER","OPERATOR"].includes(role)) throw new HttpsError("permission-denied", "Active workspace membership required");
    if (prior.exists) {
      if(prior.get("actorUid")!==uid || prior.get("entryId")!==id || Number(prior.get("actionTime"))!==actionTime) throw new HttpsError("permission-denied", "Deletion receipt mismatch");
      return acknowledgement;
    }
    if(!job.exists) throw new HttpsError("failed-precondition", "Entry is missing without a confirmed deletion receipt");
    const data=job.data(), created=Number(data.createdAt);
    const owner=role==="OWNER" && workspace.get("ownerUid")===uid;
    if(data.workspaceId!==wsId || (!owner && (!["PARTNER","OPERATOR"].includes(role) || data.isHistorical!==false || data.createdByUid!==uid || !["CLIENT_ORIGINAL_V2","VERIFIED"].includes(data.authorizationTimeStatus) || !Number.isSafeInteger(created) || created<=0 || actionTime<created || actionTime-created>=86400000))) throw new HttpsError("permission-denied", "Entry deletion is outside your permissions or deadline");
    if (typeof data.createdByUid!=="string" || !data.createdByUid || typeof data.entryForUid!=="string" || !data.entryForUid) throw new HttpsError("permission-denied", "Entry member identities must be verified before deletion");
    const targets=[...new Set([data.createdByUid,data.entryForUid])];
    const members=await Promise.all(targets.map(target=>tx.get(ws.collection("members").doc(target))));
    if(members.some(m=>!m.exists || String(m.get("status")).toUpperCase()!=="ACTIVE" || (m.get("workspaceId") != null && m.get("workspaceId")!==wsId))) throw new HttpsError("permission-denied", "Archived records require the member lifecycle workflow");
    const [collections, expenses]=await Promise.all([tx.get(ws.collection("collections")),tx.get(ws.collection("expenses"))]);
    if(expenses.docs.some(expense=>typeof expense.get("relatedJobId")==="number" && !Number.isSafeInteger(expense.get("relatedJobId")))) throw new HttpsError("failed-precondition", "Legacy Expense references require identity reconciliation");
    const linkedExpenses=expenses.docs.filter(expense=>String(expense.get("relatedJobId"))===id);
    for(const expense of linkedExpenses) {
      if(expense.get("workspaceId")!==wsId || !/^[1-9][0-9]{0,18}$/.test(expense.id) || BigInt(expense.id)>9223372036854775807n) throw new HttpsError("failed-precondition", "Linked Expense identity must be reconciled");
      const creator=expense.get("createdByUid"), target=expense.get("expenseForUid");
      if(!creator || !target) throw new HttpsError("permission-denied", "Linked Expense member identity is missing");
      const [creatorMember,targetMember]=await Promise.all([tx.get(ws.collection("members").doc(creator)),tx.get(ws.collection("members").doc(target))]);
      if([creatorMember,targetMember].some(m=>!m.exists || String(m.get("status")).toUpperCase()!=="ACTIVE" || (m.get("workspaceId")!=null && m.get("workspaceId")!==wsId))) throw new HttpsError("permission-denied", "Linked Expense member is no longer active");
      const expenseCreated=Number(expense.get("createdAt"));
      if(!owner && (role!=="PARTNER" || creator!==uid || !["CLIENT_ORIGINAL_V2","VERIFIED"].includes(expense.get("authorizationTimeStatus")) || !Number.isSafeInteger(expenseCreated) || expenseCreated<=0 || actionTime<expenseCreated || actionTime-expenseCreated>=86400000)) throw new HttpsError("permission-denied", "You cannot delete a linked Expense owned by another member or outside 24 hours");
    }
    let collectionDeletes;
    const collectionData=collections.docs.map(d=>d.data());
    try { collectionDeletes=require("./entry-deletion-policy").plannedCollectionDeletes(data,collectionData,id,
      owner ? "OWNER" : role, uid, actionTime); }
    catch(error) { throw new HttpsError("failed-precondition",error.message); }
    const collectionDeleteDocs=collections.docs.filter((_,index)=>collectionDeletes.includes(collectionData[index]));
    const customerId=String(data.customerId);
    if (!/^[1-9][0-9]{0,18}$/.test(customerId) || BigInt(customerId)>9223372036854775807n || (typeof data.customerId==="number" && !Number.isSafeInteger(data.customerId))) throw new HttpsError("failed-precondition", "Source Customer identity must be reconciled");
    const customerRef=ws.collection("customers").doc(customerId);
    const [customer,customerEntries]=await Promise.all([tx.get(customerRef),tx.get(ws.collection("entries"))]);
    if(!customer.exists || customer.get("workspaceId")!==wsId || (customer.get("isDeleted") ?? false)!==false) throw new HttpsError("failed-precondition", "Source Customer must exist in this workspace");
    let totals;
    try { totals=require("./entry-deletion-policy").customerTotals(customerEntries.docs.filter(d=>d.id!==id && String(d.get("customerId"))===customerId).map(d=>{ if(d.get("workspaceId")!==wsId) throw Error("Customer Entry workspace mismatch"); return d.data(); })); }
    catch(error) { throw new HttpsError("failed-precondition",error.message); }
    tx.update(customerRef,totals);
    collectionDeleteDocs.forEach(collection=>tx.delete(collection.ref));
    linkedExpenses.forEach(expense=>tx.delete(expense.ref));
    tx.delete(ref);
    tx.create(receipt,{workspaceId:wsId,entryId:id,actorUid:uid,operationId,actionTime,deletedExpenseIds:linkedExpenses.map(expense=>expense.id),deletedCollectionIds:collectionDeleteDocs.map(collection=>collection.id),deletedCollectionAudit:collectionDeleteDocs.map(collection=>({id:collection.id,amount:collection.get("amount")??null,customerId:collection.get("customerId")??null,jobEntryId:collection.get("jobEntryId")??null,collectedByUid:collection.get("collectedByUid")??null,createdAt:collection.get("createdAt")??null,allocationsJson:collection.get("allocationsJson")??null})),createdAt:data.createdAt ?? null,deletedAt:require("firebase-admin/firestore").FieldValue.serverTimestamp()});
    return acknowledgement;
  });
});
