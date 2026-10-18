"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const enabled = process.env.FIRESTORE_EMULATOR_HOST === "127.0.0.1:18180" && process.env.GCLOUD_PROJECT === "demo-aidhunt-v16";
// Never run this destructive fixture cleanup against a live project.
const integration = enabled ? test : test.skip;
let db, commands, auth;
if (enabled) {
  commands = require("./index");
  db = require("firebase-admin/firestore").getFirestore();
  auth = require("firebase-admin/auth").getAuth();
}
const ws = "workspace-server-tests";
async function setup() {
  await fetch("http://127.0.0.1:18180/emulator/v1/projects/demo-aidhunt-v16/databases/(default)/documents", { method: "DELETE" });
  await db.doc(`workspaces/${ws}`).set({ workspaceId: ws, ownerUid: "owner" });
  await db.doc(`workspaces/${ws}/members/owner`).set({ uid: "owner", role: "OWNER", status: "ACTIVE" });
  await db.doc(`workspaces/${ws}/members/partner`).set({ uid: "partner", role: "PARTNER", status: "ACTIVE" });
}
function request(uid, data) { return { auth: uid ? { uid } : undefined, data: data.customerId ? {protocolVersion:2,actionTime:Date.now(),operationId:'customer-test',actorContext:uid,operationType:'DELETE_CUSTOMER',dependencies:[],...data} : data }; }
integration("Customer deletion protects references with exact 63-bit Android IDs", async () => {
  await setup();
  const id = "3757519999999999999";
  await db.doc(`workspaces/${ws}/customers/${id}`).set({ id: BigInt(id), workspaceId: ws });
  await db.doc(`workspaces/${ws}/entries/1`).set({ customerId: BigInt(id), workspaceId: ws });
  await assert.rejects(commands.deleteWorkspaceCustomer.run(request("owner", { workspaceId: ws, customerId: id })), /referenced/);
  assert.equal((await db.doc(`workspaces/${ws}/customers/${id}`).get()).exists, true);
  await db.doc(`workspaces/${ws}/entries/1`).delete();
  const command = request("owner", { workspaceId: ws, customerId: id });
  const first = await commands.deleteWorkspaceCustomer.run(command);
  assert.deepEqual(first, { deleted: true, workspaceId: ws, customerId: id,
    operationId: command.data.operationId, actionTime: command.data.actionTime });
  assert.equal((await db.doc(`workspaces/${ws}/customers/${id}`).get()).exists, false);
  assert.deepEqual(await commands.deleteWorkspaceCustomer.run(command), first);
  assert.equal((await db.collection(`workspaces/${ws}/customerDeleteReceipts`).get()).size, 1);
  await assert.rejects(commands.deleteWorkspaceCustomer.run({ ...command,
    data: { ...command.data, actionTime: command.data.actionTime - 1 } }), /receipt mismatch/);
});
integration("Partner and foreign-workspace owners cannot delete Customers", async () => {
  await setup();
  await assert.rejects(commands.deleteWorkspaceCustomer.run(request("partner", { workspaceId: ws, customerId: "2" })), /Owner/);
  await assert.rejects(commands.deleteWorkspaceCustomer.run(request("owner", { workspaceId: "another", customerId: "2" })), /Owner/);
  await assert.rejects(commands.deleteWorkspaceCustomer.run(request("owner", { workspaceId: `${ws}/members/owner`, customerId: "2" })), /identity/);
});
integration("Reversed collection history still protects a retained Customer", async () => {
  await setup();
  await db.doc(`workspaces/${ws}/customers/2`).set({ id: 2n, workspaceId: ws });
  await db.doc(`workspaces/${ws}/collections/3`).set({ customerId: 2n, status: "REVERSED" });
  await assert.rejects(commands.deleteWorkspaceCustomer.run(request("owner", { workspaceId: ws, customerId: "2" })), /referenced/);
});
integration("Invitation is single-use, preserves Owner identity and creates only its locked role", async () => {
  await setup();
  const invitation = await commands.beginWorkspaceInvitation.run(request("owner", { workspaceId: ws, phone: "+919876543210", role: "OPERATOR", name: "Worker" }));
  // Stub only Firebase's external token verifier; the real service transaction and membership gates run below.
  const original = auth.verifyIdToken;
  let verifiedAt = Math.floor(Date.now() / 1000);
  auth.verifyIdToken = async (token, revoked) => {
    assert.equal(token, "verified-phone-token"); assert.equal(revoked, true);
    return { uid: "target", phone_number: "+919876543210", auth_time: verifiedAt, firebase: { sign_in_provider: "phone" } };
  };
  const body = { workspaceId: ws, invitationId: invitation.invitationId, targetIdToken: "verified-phone-token" };
  try {
    const joined = await commands.redeemWorkspaceInvitation.run(request("owner", body));
    assert.equal(joined.uid, "target"); assert.equal(joined.role, "OPERATOR");
    assert.equal((await db.doc(`workspaces/${ws}/members/owner`).get()).get("role"), "OWNER");
    assert.equal((await db.doc(`workspaces/${ws}/members/target`).get()).get("role"), "OPERATOR");
    const memberBefore = await db.doc(`workspaces/${ws}/members/target`).get();
    const proofRef = db.doc(`invitationProofReceipts/target_${verifiedAt}`);
    const proofBefore = await proofRef.get();
    const retry = await commands.redeemWorkspaceInvitation.run(request("owner", body));
    assert.equal(retry.alreadyAccepted, true);
    assert.equal(retry.uid, "target");
    assert.equal(retry.role, "OPERATOR");
    assert.equal((await db.doc(`workspaces/${ws}/members/target`).get()).updateTime.toMillis(), memberBefore.updateTime.toMillis());
    assert.equal((await proofRef.get()).updateTime.toMillis(), proofBefore.updateTime.toMillis());
    const second = await commands.beginWorkspaceInvitation.run(request("owner", { workspaceId: ws, phone: "+919876543210", role: "PARTNER", name: "Changed" }));
    // This is a NEW invitation; obtain a fresh OTP proof rather than reusing the first invitation's auth_time.
    // Otherwise a one-second boundary tests proof expiry instead of the intended role-change rejection.
    verifiedAt = Math.floor(Date.now() / 1000);
    await assert.rejects(commands.redeemWorkspaceInvitation.run(request("owner", { ...body, invitationId: second.invitationId })), /cannot change an existing member role/);
  } finally { auth.verifyIdToken = original; }
});
integration("Removed memberships cannot be reactivated through invitations", async () => {
  await setup();
  const invitation = await commands.beginWorkspaceInvitation.run(request("owner", { workspaceId: ws, phone: "+919876543210", role: "PARTNER", name: "Removed" }));
  await db.doc(`workspaces/${ws}/deletedUsers/target`).set({ uid: "target", purgeState: "ARCHIVED" });
  const original = auth.verifyIdToken;
  auth.verifyIdToken = async () => ({ uid: "target", phone_number: "+919876543210", auth_time: Math.floor(Date.now() / 1000), firebase: { sign_in_provider: "phone" } });
  try {
    await assert.rejects(commands.redeemWorkspaceInvitation.run(request("owner", { workspaceId: ws, invitationId: invitation.invitationId, targetIdToken: "proof" })), /cannot be reactivated/);
    assert.equal((await db.doc(`workspaces/${ws}/members/target`).get()).exists, false);
  } finally { auth.verifyIdToken = original; }
});

integration("Legacy Customer deletion is rejected before mutation",async()=>{await setup();await db.doc('workspaces/'+ws+'/customers/2').set({id:2n,workspaceId:ws});await assert.rejects(commands.deleteWorkspaceCustomer.run({auth:{uid:'owner'},data:{workspaceId:ws,customerId:'2'}}),e=>e.details?.code==='UPGRADE_REQUIRED');assert.equal((await db.doc('workspaces/'+ws+'/customers/2').get()).exists,true);});
