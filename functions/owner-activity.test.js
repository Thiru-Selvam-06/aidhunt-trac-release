"use strict";

const assert = require("node:assert/strict");
const test = require("node:test");
const { confirmedActivity, eventIdentity, publishOwnerActivity } = require("./owner-activity");

const record = { workspaceId: "ws_test", actorUid: "partner", operationId: "op-1", operationType: "UPSERT" };

test("classifies committed jobs, collections, expenses and ordinary withdrawals", () => {
  assert.equal(confirmedActivity("entries", null, record).type, "JOB_CREATED");
  assert.equal(confirmedActivity("entries", { operationId: "old" }, { ...record, notes: "new notes" }).type, "JOB_EDITED");
  assert.equal(confirmedActivity("entries", { operationId: "old" }, record), null);
  assert.equal(confirmedActivity("entries", { amountReceived: 0 },
    { ...record, collectionId: "7", amountReceived: 700 }), null);
  assert.equal(confirmedActivity("collections", null, { ...record, status: "ACTIVE" }).type, "COLLECTION");
  assert.equal(confirmedActivity("entryDeletionProofs", null, record).type, "JOB_DELETED");
  assert.equal(confirmedActivity("cashDeleteProofs", null, { ...record, kind: "EXPENSE" }).type, "EXPENSE");
  assert.equal(confirmedActivity("expenses", null, record).type, "EXPENSE");
  assert.equal(confirmedActivity("withdrawals", null, record).type, "WITHDRAWAL");
  assert.equal(confirmedActivity("withdrawals", null, { ...record, borrowingRequestId: 5n }), null);
  assert.equal(confirmedActivity("notifications", null, record), null);
  assert.equal(confirmedActivity("entries", record, record), null);
  assert.equal(confirmedActivity("entries", record, { ...record, isDeleted: true }), null);
  assert.notEqual(eventIdentity("ws_test", "op-1", "EXPENSE"), eventIdentity("ws_other", "op-1", "EXPENSE"));
});

test("delivers only to registered original-owner devices and records one event", async () => {
  const root = "workspaces/ws_test";
  const collection = (path) => ({
    doc: id => document(`${path}/${id}`),
    where: (field, op, value) => ({ get: async () => ({
      docs: [...records].filter(([key, data]) => key.startsWith(path + "/") && data[field] === value)
        .map(([key]) => snapshot(document(key)))
    }) })
  });
  const document = path => ({
    path,
    collection: name => collection(`${path}/${name}`),
    update: async fields => records.set(path, { ...records.get(path), ...fields })
  });
  const snapshot = ref => ({
    ref, exists: records.has(ref.path), get: field => records.get(ref.path)?.[field]
  });
  const records = new Map([
    [root, { ownerUid: "original" }],
    [root + "/members/partner", { role: "PARTNER", status: "ACTIVE" }],
    [root + "/members/original", { role: "OWNER", status: "ACTIVE" }],
    [root + "/ownerPushDevices/phone-a", { uid: "original", token: "owner-token" }],
    [root + "/ownerPushDevices/phone-b", { uid: "partner", token: "partner-token" }]
  ]);
  const pushes = [];
  const db = {
    collection,
    runTransaction: fn => fn({
      get: async ref => snapshot(ref),
      create: (ref, data) => records.set(ref.path, data),
      delete: ref => records.delete(ref.path)
    })
  };
  const run = (data = record) => publishOwnerActivity({
    db, messaging: { send: async message => pushes.push(message) },
    fieldValue: { serverTimestamp: () => new Date() }, emulator: false,
    collection: "entries", event: { params: { wsId: "ws_test" }, data: { after: { data: () => data } } }
  });
  await run();
  await run();
  await run({ ...record, actorUid: "original", operationId: "own" });
  await run({ ...record, workspaceId: "ws_other", operationId: "foreign" });
  assert.equal(pushes.length, 1);
  assert.equal(pushes[0].token, "owner-token");
  assert.equal(pushes[0].data.recipientUid, "original");
  assert.equal([...records.keys()].filter(path => path.includes("/ownerActivityEvents/")).length, 1);
});
