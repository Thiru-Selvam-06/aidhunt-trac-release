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
    emulator = false, project = "trac-aidhunt", send = async message => pushes.push(message) } = {}) => publishOwnerActivity({
    db, messaging: { send }, fieldValue: { serverTimestamp: () => stamp }, emulator, now: () => clock,
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
