import test from 'node:test';
import assert from 'node:assert/strict';
import { FirestoreRest, SERVER_TIMESTAMP } from './firestore-rest.mjs';

test('Firestore transaction keeps 64-bit IDs exact and uses one atomic commit', async () => {
  const requests = [];
  const project = 'trac-aidhunt', root = `projects/${project}/databases/(default)/documents`;
  const fetcher = async function (url, options) {
    assert.equal(this, undefined, 'Fetch must be invoked without the adapter as receiver');
    const body = JSON.parse(options.body);
    requests.push({ url, body });
    if (url.endsWith(':beginTransaction')) return new Response(JSON.stringify({ transaction: 'transaction-token' }));
    if (url.endsWith(':batchGet')) return new Response(JSON.stringify([{ found: { name: body.documents[0],
      fields: { customerId: { integerValue: '9007199254740993' } } } }]));
    if (url.endsWith(':runQuery')) return new Response(JSON.stringify([{ document: {
      name: `${root}/workspaces/ws/entries/9007199254740993`, fields: { customerId: { integerValue: '9007199254740993' } }
    } }]));
    if (url.endsWith(':commit')) return new Response(JSON.stringify({ writeResults: [{}, {}] }));
    throw new Error(`Unexpected Firestore path: ${url}`);
  };
  const db = new FirestoreRest(project, async () => 'short-lived-token', fetcher);
  await db.transaction(async tx => {
    const doc = await tx.get('workspaces/ws/customers/9007199254740993');
    assert.equal(doc.get('customerId'), 9007199254740993n);
    const entries = await tx.list('workspaces/ws/entries', 'customerId', 9007199254740993n);
    assert.equal(entries[0].id, '9007199254740993');
    tx.update('workspaces/ws/customers/9007199254740993', { totalBilled: 9007199254740993n });
    tx.create('workspaces/ws/entryDeleteReceipts/test-op', { deletedAt: SERVER_TIMESTAMP });
  });
  const query = requests.find(request => request.url.endsWith(':runQuery'));
  assert.equal(query.body.structuredQuery.where.fieldFilter.value.integerValue, '9007199254740993');
  const commit = requests.find(request => request.url.endsWith(':commit'));
  assert.equal(commit.body.transaction, 'transaction-token');
  assert.equal(commit.body.writes[0].update.fields.totalBilled.integerValue, '9007199254740993');
  assert.equal(commit.body.writes[1].updateTransforms[0].setToServerValue, 'REQUEST_TIME');
});


test('compound deletion serializes nested Collection audit objects without losing 64-bit references', async () => {
  let commit;
  const db = new FirestoreRest('trac-aidhunt', async () => 'test-token', async (url, options) => {
    if (url.endsWith(':beginTransaction')) return Response.json({ transaction: 'test-transaction' });
    if (url.endsWith(':commit')) { commit = JSON.parse(options.body); return Response.json({ writeResults: [{}] }); }
    throw Error('Unexpected request');
  });
  await db.transaction(async tx => tx.create('workspaces/ws/entryDeleteReceipts/test-op', {
    deletedCollectionAudit: [{ id: '9007199254740993', amount: 100n, customerId: 9007199254740995n,
      metadata: { status: 'ACTIVE' } }], deletedAt: SERVER_TIMESTAMP
  }));
  const audit = commit.writes[0].update.fields.deletedCollectionAudit.arrayValue.values[0].mapValue.fields;
  assert.equal(audit.customerId.integerValue, '9007199254740995');
  assert.equal(audit.metadata.mapValue.fields.status.stringValue, 'ACTIVE');
  assert.equal(commit.writes[0].updateTransforms[0].setToServerValue, 'REQUEST_TIME');
});
