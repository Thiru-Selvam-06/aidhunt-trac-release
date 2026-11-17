"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const { confirmedActivity, eventIdentity, publishOwnerActivity } = require("./owner-activity");

const workspaceId = "ws_test";
const base = { workspaceId, id: 123n, actorUid: "partner", operationId: "op-1", operationType: "UPSERT", protocolVersion: 2 };
const proof = { ...base, recordId: "123", operationType: "DELETE" };

test("PUSH-001 to 007: only confirmed supported changes generate activity", () => {
  assert.equal(confirmedActivity("entries", null, base).type, "JOB_CREATED");
  assert.equal(confirmedActivity("entries", null, { ...base, isHistorical: true }).type, "OLD_ENTRY_CREATED");
  assert.equal(confirmedActivity("entries", { operationId: "old", isHistorical: true, createdByUid: "original" },
    { ...base, notes: "new notes", isHistorical: true, createdByUid: "original" }).type, "JOB_EDITED");
  assert.equal(confirmedActivity("entries", { operationId: "old" }, base), null);
  assert.equal(confirmedActivity("entries", { amountReceived: 0 },
    { ...base, collectionId: "7", amountReceived: 700 }), null);
  assert.equal(confirmedActivity("collections", null, { ...base, status: "ACTIVE" }).type, "COLLECTION");
  assert.equal(confirmedActivity("collections", { ...base, status: "ACTIVE" },
    { ...base, status: "REVERSED", operationId: "reverse" }).type, "COLLECTION_REVERSED");
  assert.equal(confirmedActivity("entryDeletionProofs", null, proof).type, "JOB_DELETED");
  assert.equal(confirmedActivity("cashDeleteProofs", null, { ...proof, kind: "EXPENSE" }).type, "EXPENSE_DELETED");
  assert.equal(confirmedActivity("expenses", { ...proof, isDeleted: true }, null).type, "EXPENSE_DELETED");
  assert.equal(confirmedActivity("expenses", base, { ...proof, isDeleted: true }), null);
  assert.equal(confirmedActivity("expenses", null, base).type, "EXPENSE");
  assert.equal(confirmedActivity("expenses", { ...base, amount: 5 }, { ...base, operationId: "edit", amount: 7 }).type, "EXPENSE_EDITED");
  assert.equal(confirmedActivity("withdrawals", null, base).type, "WITHDRAWAL");
  assert.equal(confirmedActivity("withdrawals", null, { ...base, borrowingRequestId: 5n }), null);
  assert.equal(confirmedActivity("expenses", null, { ...base, fundingSource: "EXTERNAL_LOAN" }), null);
  assert.equal(confirmedActivity("notifications", null, base), null);
  assert.equal(confirmedActivity("entries", base, base), null);
  assert.equal(confirmedActivity("entries", base, { ...base, isDeleted: true }), null);
  assert.notEqual(eventIdentity(workspaceId, "op-1", "EXPENSE"), eventIdentity("ws_other", "op-1", "EXPENSE"));
});

function fixture() {
  const clock = 1760000000000;
  const stamp = { toMillis: () => clock };
  const root = "workspaces/" + workspaceId;
  const records = new Map([
    [root, { ownerUid: "original" }],
    [root + "/members/partner", { role: "PARTNER", status: "ACTIVE", displayName: "Perumal" }],
    [root + "/members/original", { role: "OWNER", status: "ACTIVE" }],
    [root + "/members/other", { role: "PARTNER", status: "ACTIVE" }],
    [root + "/members/inactive", { role: "PARTNER", status: "REMOVED" }],
    [root + "/ownerPushDevices/phone-a", { uid: "original", token: "owner-a", updatedAt: stamp }],
    [root + "/ownerPushDevices/phone-a-duplicate", { uid: "original", token: "owner-a", updatedAt: stamp }],
    [root + "/ownerPushDevices/phone-b", { uid: "original", token: "owner-b", updatedAt: stamp }],
    [root + "/ownerPushDevices/foreign", { uid: "other", token: "partner-token", updatedAt: stamp }],
    [root + "/ownerPushDevices/expired", { uid: "original", token: "old", updatedAt: { toMillis: () => 0 } }]
  ]);
  const pushes = [];
  const snapshot = ref => ({ ref, exists: records.has(ref.path), get: field => records.get(ref.path)?.[field] });
  const collection = path => ({
    doc: id => document(path + "/" + id),
    where: (field, op, value) => ({ get: async () => ({
      docs: [...records].filter(([key, data]) => key.startsWith(path + "/") && data[field] === value)
        .map(([key]) => snapshot(document(key)))
    }) })
  });
  const update = (ref, fields) => records.set(ref.path, { ...records.get(ref.path), ...fields });
  const document = path => ({ path, collection: name => collection(path + "/" + name),
    update: async fields => update({ path }, fields) });
  let transactions = Promise.resolve();
  const db = { collection, runTransaction: fn => {
    const next = transactions.then(() => fn({ get: async ref => snapshot(ref), set: (ref, data) => records.set(ref.path, data),
      create: (ref, data) => { assert.ok(!records.has(ref.path)); records.set(ref.path, data); },
      delete: ref => records.delete(ref.path), update }));
    transactions = next.catch(() => {});
    return next;
  } };
  const run = ({ after = base, before, collection = "entries", recordId = "123", authId = after?.actorUid,
    emulator = false, project = "trac-aidhunt", expectedOwnerUid, send = async message => pushes.push(message) } = {}) => publishOwnerActivity({
    db, messaging: { send }, fieldValue: { serverTimestamp: () => stamp }, emulator, expectedOwnerUid, now: () => clock,
    collection, event: { authType: "unknown", authId, project, params: { wsId: workspaceId, recordId },
      data: { before: { data: () => before }, after: { data: () => after } } }
  });
  const events = () => [...records.entries()].filter(([key]) => key.includes("/ownerActivityEvents/") && !key.includes("/deliveries/"));
  return { run, events, records, pushes, root, stamp };
}

test("PUSH-008 to 012 and 014: rejects wrong actor/workspace, retains one confirmed event and sends only to Owner tokens", async () => {
  const f = fixture();
  await f.run({ after: null });
  await f.run({ after: { ...base, workspaceId: "foreign" } });
  await f.run({ after: { ...base, actorUid: "original" } });
  await f.run({ after: { ...base, actorUid: "inactive" } });
  await f.run({ authId: "other" });
  await f.run({ recordId: "124" });
  await f.run({ after: { ...base, isDeleted: true } });
  assert.equal(f.events().length, 0);
  await f.run();
  await f.run();
  assert.deepEqual(f.pushes.map(push => push.token).sort(), ["owner-a", "owner-b"]);
  assert.equal(f.pushes[0].data.recipientUid, "original");
  assert.deepEqual(Object.keys(f.pushes[0].data).sort(), ["eventId", "kind", "recipientUid", "workspaceId"]);
  assert.equal(f.events().length, 1);
  assert.equal(f.events()[0][1].actorName, "Perumal");
  assert.equal(f.events()[0][1].operationId, "op-1");
});

test("PUSH-007: a proof only alerts after the target is deleted; tombstone and proof share one logical event", async () => {
  const f = fixture();
  const expense = f.root + "/expenses/123";
  f.records.set(expense, base);
  const after = { ...proof, kind: "EXPENSE" };
  await f.run({ after, collection: "cashDeleteProofs", recordId: "EXPENSE_123" });
  assert.equal(f.events().length, 0);
  f.records.delete(expense);
  await f.run({ after, collection: "cashDeleteProofs", recordId: "EXPENSE_123" });
  await f.run({ after: null, before: { ...proof, isDeleted: true }, collection: "expenses" });
  assert.equal(f.events().length, 1);
  assert.equal(f.events()[0][1].eventType, "EXPENSE_DELETED");
});

test("PUSH-015 and 022: failed token retry does not resend accepted tokens; invalid duplicates are cleaned without deleting refreshed registration", async () => {
  const f = fixture();
  await assert.rejects(f.run({ send: async message => {
    f.pushes.push(message);
    if (message.token === "owner-b") throw Object.assign(new Error("Unavailable"), { code: "messaging/server-unavailable" });
  }}), /needs retry/);
  assert.equal(f.events().length, 1);
  assert.equal(f.events()[0][1].deliverySweepCompletedAt, undefined);
  await f.run({ send: async message => {
    f.pushes.push(message);
    assert.equal(message.token, "owner-b");
    f.records.set(f.root + "/ownerPushDevices/phone-b", { uid: "original", token: "fresh-b", updatedAt: f.stamp });
    throw Object.assign(new Error("Unregistered"), { code: "messaging/registration-token-not-registered" });
  }});
  await f.run();
  assert.deepEqual(f.pushes.map(push => push.token).sort(), ["owner-a", "owner-b", "owner-b"]);
  assert.equal(f.records.get(f.root + "/ownerPushDevices/phone-b").token, "fresh-b");
  assert.ok(f.events()[0][1].deliverySweepCompletedAt);
  await f.run({ after: { ...base, operationId: "op-2" }, send: async message => {
    if (message.token === "owner-a") throw Object.assign(new Error(), { code: "messaging/invalid-registration-token" });
  }});
  assert.ok(!f.records.has(f.root + "/ownerPushDevices/phone-a"));
  assert.ok(!f.records.has(f.root + "/ownerPushDevices/phone-a-duplicate"));
});

test("PUSH-012: overlapping deliveries use a per-token lease", async () => {
  const f = fixture();
  let release;
  const wait = new Promise(resolve => { release = resolve; });
  let entered;
  const inFlight = new Promise(resolve => { entered = resolve; });
  const first = f.run({ send: async message => { f.pushes.push(message); entered(); await wait; } });
  await inFlight;
  await assert.rejects(f.run(), /needs retry/);
  release();
  await first;
  await f.run();
  assert.equal(f.events().length, 1);
  assert.deepEqual(f.pushes.map(push => push.token).sort(), ["owner-a", "owner-b"]);
});


test("PUSH-020: the fixed Functions Emulator principal only applies to the isolated demo project", async () => {
  const f = fixture();
  const authId = "fake-auth-id@gmail.com";
  await f.run({ authId, project: "demo-aidhunt-v16" });
  await f.run({ authId, emulator: true });
  assert.equal(f.events().length, 0);
  await f.run({ authId, project: "demo-aidhunt-v16", emulator: true });
  assert.equal(f.events().length, 1);
  assert.equal(f.pushes.length, 0);
});


test("pilot scope fails closed and accepts only the exact project, workspace, and pinned Owner", () => {
  const { ownerActivityPilotScope } = require("./owner-activity-pilot");
  const config = { projectId: "trac-aidhunt", emulator: false, enabled: true,
    pilotWorkspaceId: "ws_pilot", pilotOwnerUid: "pilotOwner" };
  assert.deepEqual(ownerActivityPilotScope(config, "ws_pilot"), { expectedOwnerUid: "pilotOwner" });
  for (const change of [
    { enabled: false }, { pilotWorkspaceId: undefined }, { pilotWorkspaceId: "*" },
    { pilotOwnerUid: "" }, { pilotOwnerUid: "owner/foreign" }, { projectId: "foreign" }, { emulator: true }
  ]) {
    assert.equal(ownerActivityPilotScope({ ...config, ...change }, "ws_pilot"), null);
  }
  assert.equal(ownerActivityPilotScope(config, "ws_pilot_suffix"), null);
  assert.equal(ownerActivityPilotScope(config, "ws_pilot", "foreign"), null);
  assert.equal(ownerActivityPilotScope({ projectId: "demo-aidhunt-v16" }, "ws_test"), null);
  assert.deepEqual(ownerActivityPilotScope({ projectId: "demo-aidhunt-v16", emulator: true }, "ws_test"),
    { expectedOwnerUid: null });
});

test("an incorrectly pinned workspace Owner cannot generate events or token acceptance", async () => {
  const f = fixture();
  await f.run({ expectedOwnerUid: "differentOwner" });
  assert.equal(f.events().length, 0);
  assert.equal(f.pushes.length, 0);
  await f.run({ expectedOwnerUid: "original" });
  await f.run({ expectedOwnerUid: "original" });
  assert.equal(f.events().length, 1);
  assert.equal(f.pushes.length, 2);
});

test("all seven exported pilot handlers enforce the production scope before touching data", async () => {
  const keys = ["FIREBASE_CONFIG", "FUNCTIONS_EMULATOR", "OWNER_ACTIVITY_PILOT_ENABLED",
    "OWNER_ACTIVITY_PILOT_WORKSPACE_ID", "OWNER_ACTIVITY_PILOT_OWNER_UID"];
  const original = Object.fromEntries(keys.map(key => [key, process.env[key]]));
  process.env.FIREBASE_CONFIG = JSON.stringify({ projectId: "trac-aidhunt" });
  delete process.env.FUNCTIONS_EMULATOR;
  process.env.OWNER_ACTIVITY_PILOT_ENABLED = "true";
  process.env.OWNER_ACTIVITY_PILOT_WORKSPACE_ID = "ws_pilot";
  process.env.OWNER_ACTIVITY_PILOT_OWNER_UID = "pilotOwner";
  try {
    const functions = require("./index");
    const triggers = ["entries", "entryDeletionProofs", "collections", "expenses", "cashDeleteProofs", "withdrawals"];
    assert.deepEqual(Object.keys(functions).filter(key => key.startsWith("ownerActivity")).sort(),
      [...triggers.map(name => "ownerActivity_" + name), "ownerActivitySession"].sort());
    for (const collection of triggers) {
      const handler = functions["ownerActivity_" + collection];
      assert.deepEqual(handler.__endpoint.region, ["asia-south1"]);
      assert.equal(handler.__endpoint.serviceAccountEmail, "trac-notify-sender@trac-aidhunt.iam.gserviceaccount.com");
      assert.equal(handler.__endpoint.minInstances, 0);
      assert.equal(handler.__endpoint.maxInstances, 1);
      const unreadable = { before: { data: () => assert.fail("Read ledger data outside the pilot") } };
      await handler.run({ project: "trac-aidhunt", params: { wsId: "ws_customer" }, data: unreadable });
      await handler.run({ project: "foreign", params: { wsId: "ws_pilot" }, data: unreadable });
      await handler.run({ params: { wsId: "ws_pilot" }, data: unreadable });
    }
    const session = functions.ownerActivitySession;
    assert.deepEqual(session.__endpoint.region, ["asia-south1"]);
    assert.equal(session.__endpoint.serviceAccountEmail, "trac-notify-session@trac-aidhunt.iam.gserviceaccount.com");
    assert.equal(session.__endpoint.maxInstances, 1);
    for (const action of ["register", "unregister", "history", "event"]) {
      await assert.rejects(async () => session.run({ auth: { uid: "pilotOwner" },
        data: { workspaceId: "ws_customer", action } }), { code: "permission-denied" });
      await assert.rejects(async () => session.run({ auth: { uid: "partner" },
        data: { workspaceId: "ws_pilot", action } }), { code: "permission-denied" });
    }
    process.env.OWNER_ACTIVITY_PILOT_ENABLED = "false";
    await assert.rejects(async () => session.run({ auth: { uid: "pilotOwner" },
      data: { workspaceId: "ws_pilot", action: "register" } }), { code: "permission-denied" });
  } finally {
    for (const [key, value] of Object.entries(original)) {
      if (value === undefined) delete process.env[key];
      else process.env[key] = value;
    }
  }
});
