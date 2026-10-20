import test from 'node:test';
import assert from 'node:assert/strict';
import { beginWorkspaceInvitation, redeemWorkspaceInvitation, deleteWorkspaceCustomer, deleteWorkspaceEntry } from './operations.mjs';
import { handleRequest, isAuthorizedStagingWorkspace, AUTHORIZED_STAGING_WORKSPACES } from './worker.mjs';

class MemoryStore {
  constructor(records = {}) { this.records = new Map(Object.entries(records)); }
  async transaction(fn) {
    const records = this.records, writes = [];
    const snapshot = (path, data) => ({ exists: data !== undefined, id: path.split('/').at(-1), data,
      get(key) { return this.data?.[key]; } });
    const tx = {
      async get(path) { return snapshot(path, records.get(path)); },
      async list(collection, field, equalTo) {
        return [...records].filter(([path, data]) => path.startsWith(`${collection}/`) &&
          path.split('/').length === collection.split('/').length + 1 &&
          (field === undefined || data[field] === equalTo)).map(([path, data]) => snapshot(path, data));
      },
      create(path, data) { writes.push(() => { assert.equal(records.has(path), false); records.set(path, data); }); },
      set(path, data) { writes.push(() => records.set(path, data)); },
      update(path, data) { writes.push(() => records.set(path, { ...records.get(path), ...data })); },
      delete(path) { writes.push(() => records.delete(path)); },
    };
    const result = await fn(tx);
    writes.forEach(write => write());
    return result;
  }
}
const ws = 'ws-test';
const owner = 'owner-test';
const large = '9007199254740993';
function base() { return {
  [`workspaces/${ws}`]: { ownerUid: owner },
  [`workspaces/${ws}/members/${owner}`]: { workspaceId: ws, role: 'OWNER', status: 'ACTIVE' },
}; }
function protocol(type, fields = {}) { return { workspaceId: ws, protocolVersion: 2, actionTime: Date.now(),
  operationId: 'op-test', actorContext: owner, operationType: type, dependencies: [], ...fields }; }

let sharedAuth;
async function getSharedAuth() {
  if (!sharedAuth) {
    const pair = await crypto.subtle.generateKey({ name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048,
      publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' }, true, ['sign', 'verify']);
    const jwk = await crypto.subtle.exportKey('jwk', pair.publicKey);
    const pem = `-----BEGIN PRIVATE KEY-----\n${Buffer.from(await crypto.subtle.exportKey('pkcs8', pair.privateKey)).toString('base64')}\n-----END PRIVATE KEY-----`;
    sharedAuth = { pair, jwk, pem };
  }
  return sharedAuth;
}

test('staging handler rejects unauthenticated mutations before reading the credential', async () => {
  const response = await handleRequest(new Request('https://worker.example/deleteWorkspaceEntry', { method: 'POST', body: '{}',
    headers: { 'Content-Type': 'application/json' } }), { FIREBASE_PROJECT_ID: 'trac-aidhunt' });
  assert.equal(response.status, 401);
  assert.equal((await response.json()).error.status, 'UNAUTHENTICATED');
});
test('staging handler rejects a wrong Firebase project even for health', async () => {
  const response = await handleRequest(new Request('https://worker.example/health'), { FIREBASE_PROJECT_ID: 'wrong-project' });
  assert.equal(response.status, 503);
});
test('HTTP handler binds a signed actor to staging workspace membership and role', async () => {
  const { pair, jwk, pem } = await getSharedAuth();
  const b64 = value => Buffer.from(value).toString('base64url');
  const issued = Math.floor(Date.now() / 1000);
  const sign = async uid => {
    const input = `${b64(JSON.stringify({ alg: 'RS256', kid: 'handler-test-key' }))}.${b64(JSON.stringify({
      aud: 'trac-aidhunt', iss: 'https://securetoken.google.com/trac-aidhunt', sub: uid,
      auth_time: issued - 10, iat: issued - 10, exp: issued + 300 }))}`;
    return `${input}.${b64(await crypto.subtle.sign('RSASSA-PKCS1-v1_5', pair.privateKey,
      new TextEncoder().encode(input)))}`;
  };
  const env = { FIREBASE_PROJECT_ID: 'trac-aidhunt', STAGING_WORKSPACE_ID: ws,
    GOOGLE_SERVICE_ACCOUNT_JSON: JSON.stringify({ type: 'service_account', project_id: 'trac-aidhunt',
      client_email: 'handler-test@trac-aidhunt.iam.gserviceaccount.com', private_key: pem }) };
  const fetcher = async (url, options) => {
    if (url.includes('/jwk/')) return new Response(JSON.stringify({ keys: [{ ...jwk, kid: 'handler-test-key' }] }));
    if (url.includes('oauth2.googleapis.com')) return new Response(JSON.stringify({ access_token: 'handler-test-oauth', expires_in: 3600 }));
    if (url.includes('accounts:lookup')) {
      const uid = JSON.parse(options.body).localId[0];
      return new Response(JSON.stringify({ users: [{ localId: uid, validSince: String(issued - 100) }] }));
    }
    throw Error('Unexpected external request');
  };
  const db = new MemoryStore({ ...base(), [`workspaces/${ws}/members/partner-test`]: {
    workspaceId: ws, role: 'PARTNER', status: 'ACTIVE' } });
  const call = async (uid, workspaceId) => handleRequest(new Request('https://worker.example/beginWorkspaceInvitation', {
    method: 'POST', headers: { Authorization: `Bearer ${await sign(uid)}` },
    body: JSON.stringify({ data: { workspaceId, phone: '+919999999999', role: 'OPERATOR', name: 'Test' } })
  }), env, { fetcher, db });
  const wrongWorkspace = await call(owner, 'another-workspace');
  assert.equal(wrongWorkspace.status, 403);
  assert.equal((await wrongWorkspace.json()).error.status, 'PERMISSION_DENIED');
  const wrongRole = await call('partner-test', ws);
  assert.equal(wrongRole.status, 403);
  assert.equal((await wrongRole.json()).error.status, 'PERMISSION_DENIED');
  assert.equal([...db.records.keys()].some(path => path.includes('/invitations/')), false);
  const allowed = await call(owner, ws);
  assert.equal(allowed.status, 200);
  assert.equal([...db.records.keys()].filter(path => path.includes('/invitations/')).length, 1);
});
test('invitation creation requires the active workspace owner', async () => {
  const db = new MemoryStore(base());
  const invite = { workspaceId: ws, phone: '+919999999999', role: 'PARTNER', name: 'Test Partner' };
  await assert.rejects(beginWorkspaceInvitation(db, 'stranger', invite), { status: 'PERMISSION_DENIED' });
  const result = await beginWorkspaceInvitation(db, owner, invite);
  assert.equal(db.records.get(`workspaces/${ws}/invitations/${result.invitationId}`).ownerUid, owner);
});
test('redemption uses the verified phone identity and consumes the proof once', async () => {
  const invitationId = 'invite-test', phone = '+919999999999', now = Date.now();
  const db = new MemoryStore({ ...base(), [`workspaces/${ws}/invitations/${invitationId}`]: {
    workspaceId: ws, ownerUid: owner, phone, name: 'Test Partner', role: 'PARTNER',
    status: 'PENDING', createdAt: now - 1000, expiresAt: now + 100000,
  } });
  const claims = { uid: 'new-member', phone_number: phone, auth_time: Math.floor(now / 1000),
    firebase: { sign_in_provider: 'phone' } };
  const command = { workspaceId: ws, invitationId };
  const result = await redeemWorkspaceInvitation(db, owner, command, claims);
  assert.deepEqual(result, { uid: claims.uid, workspaceId: ws, role: 'PARTNER' });
  assert.equal(db.records.get(`workspaces/${ws}/members/${claims.uid}`).status, 'ACTIVE');
  assert.equal(db.records.get(`workspaces/${ws}/invitations/${invitationId}`).status, 'USED');
  assert.equal((await redeemWorkspaceInvitation(db, owner, command, claims)).alreadyAccepted, true);
  await assert.rejects(redeemWorkspaceInvitation(db, owner, command, { ...claims, auth_time: claims.auth_time + 1 }), { status: 'PERMISSION_DENIED' });
  assert.equal([...db.records.keys()].filter(key => key.startsWith('invitationProofReceipts/')).length, 1);
  assert.equal(db.records.get(`invitationProofReceipts/${claims.uid}_${claims.auth_time}`).targetUid, claims.uid);
});
test('joining another workspace preserves an existing phone identity and its legacy metadata', async () => {
  const invitationId = 'second-workspace', phone = '+919999999999', now = Date.now();
  const directoryPath = `phoneDirectory/${phone}`;
  const originalDirectory = { uid: 'new-member', workspaceId: 'first-workspace', role: 'OWNER', phoneNumber: phone };
  const personalWorkspace = { ownerUid: 'new-member', name: 'Personal workspace' };
  const personalMembership = { workspaceId: 'personal', uid: 'new-member', role: 'OWNER', status: 'ACTIVE' };
  const user = { uid: 'new-member', defaultWorkspaceId: 'personal', displayName: 'Existing Owner' };
  const db = new MemoryStore({ ...base(), [directoryPath]: originalDirectory,
    'workspaces/personal': personalWorkspace,
    'workspaces/personal/members/new-member': personalMembership,
    'users/new-member': user,
    [`workspaces/${ws}/invitations/${invitationId}`]: { workspaceId: ws, ownerUid: owner,
      phone, name: 'Test Partner', role: 'PARTNER', status: 'PENDING',
      createdAt: now - 1000, expiresAt: now + 100000 } });
  const claims = { uid: 'new-member', phone_number: phone, auth_time: Math.floor(now / 1000),
    firebase: { sign_in_provider: 'phone' } };
  await redeemWorkspaceInvitation(db, owner, { workspaceId: ws, invitationId }, claims);
  assert.deepEqual(db.records.get(directoryPath), originalDirectory);
  assert.equal(db.records.get(`workspaces/${ws}/members/new-member`).role, 'PARTNER');
  assert.deepEqual(db.records.get('workspaces/personal'), personalWorkspace);
  assert.deepEqual(db.records.get('workspaces/personal/members/new-member'), personalMembership);
  assert.deepEqual(db.records.get('users/new-member'), user);
  assert.equal(db.records.get(`userWorkspaceMemberships/new-member/workspaces/${ws}`).role, 'PARTNER');
  await assert.rejects(beginWorkspaceInvitation(db, 'new-member', {
    workspaceId: ws, phone: '+919999999998', role: 'PARTNER', name: 'Not authorized here'
  }), { status: 'PERMISSION_DENIED' });

});
test('invitation cannot rebind a phone directory belonging to a different UID', async () => {
  const invitationId = 'conflicting-phone', phone = '+919999999999', now = Date.now();
  const directoryPath = `phoneDirectory/${phone}`;
  const db = new MemoryStore({ ...base(), [directoryPath]: { uid: 'another-person', phone },
    [`workspaces/${ws}/invitations/${invitationId}`]: { workspaceId: ws, ownerUid: owner,
      phone, name: 'Test Partner', role: 'PARTNER', status: 'PENDING',
      createdAt: now - 1000, expiresAt: now + 100000 } });
  const claims = { uid: 'new-member', phone_number: phone, auth_time: Math.floor(now / 1000),
    firebase: { sign_in_provider: 'phone' } };
  await assert.rejects(redeemWorkspaceInvitation(db, owner, { workspaceId: ws, invitationId }, claims),
    { status: 'PERMISSION_DENIED' });
  assert.equal(db.records.get(directoryPath).uid, 'another-person');
  assert.equal(db.records.has(`workspaces/${ws}/members/new-member`), false);
});
test('old protocol remains recoverable and does not delete a Customer', async () => {
  const db = new MemoryStore({ ...base(), [`workspaces/${ws}/customers/${large}`]: { workspaceId: ws } });
  await assert.rejects(deleteWorkspaceCustomer(db, owner, { workspaceId: ws, customerId: large }),
    { status: 'FAILED_PRECONDITION', details: { code: 'UPGRADE_REQUIRED', requiredProtocolVersion: 2 } });
  assert.equal(db.records.has(`workspaces/${ws}/customers/${large}`), true);
});
test('Customer deletion preserves exact int64 ID and blocks retained references', async () => {
  const customerPath = `workspaces/${ws}/customers/${large}`;
  const db = new MemoryStore({ ...base(), [customerPath]: { workspaceId: ws },
    [`workspaces/${ws}/entries/1`]: { customerId: BigInt(large) } });
  const command = protocol('DELETE_CUSTOMER', { customerId: large });
  await assert.rejects(deleteWorkspaceCustomer(db, owner, command), { status: 'FAILED_PRECONDITION' });
  assert.equal(db.records.has(customerPath), true);
  db.records.get(`workspaces/${ws}/entries/1`).customerId = large;
  await assert.rejects(deleteWorkspaceCustomer(db, owner, command), { status: 'FAILED_PRECONDITION' });
  db.records.delete(`workspaces/${ws}/entries/1`);
  const acknowledgement = { deleted: true, workspaceId: ws, customerId: large,
    operationId: command.operationId, actionTime: command.actionTime };
  assert.deepEqual(await deleteWorkspaceCustomer(db, owner, command), acknowledgement);
  assert.equal(db.records.has(customerPath), false);
  const receiptPath = `workspaces/${ws}/customerDeleteReceipts/${command.operationId}`;
  const receipt = { ...db.records.get(receiptPath) };
  assert.equal(receipt.actorUid, owner);
  assert.deepEqual(await deleteWorkspaceCustomer(db, owner, command), acknowledgement);
  assert.deepEqual(db.records.get(receiptPath), receipt);
  await assert.rejects(deleteWorkspaceCustomer(db, owner, { ...command, actionTime: command.actionTime - 1 }),
    { status: 'PERMISSION_DENIED' });
  await assert.rejects(deleteWorkspaceCustomer(db, owner, { ...command, customerId: '2' }),
    { status: 'PERMISSION_DENIED' });
  await assert.rejects(deleteWorkspaceCustomer(db, 'stranger', { ...command, actorContext: 'stranger' }),
    { status: 'PERMISSION_DENIED' });
  assert.deepEqual(db.records.get(receiptPath), receipt);
});
test('Missing Customer without a matching receipt never receives success acknowledgement', async () => {
  const command = protocol('DELETE_CUSTOMER', { customerId: '42' });
  const db = new MemoryStore(base());
  await assert.rejects(deleteWorkspaceCustomer(db, owner, command), { status: 'FAILED_PRECONDITION' });
  assert.equal([...db.records.keys()].some(path => path.includes('customerDeleteReceipts')), false);
});
test('Owner deletion removes an active payment and source in one transaction with an audit receipt', async () => {
  const now = Date.now(), entryId = '9007199254740995', customerId = '90';
  const entryPath = `workspaces/${ws}/entries/${entryId}`;
  const customerPath = `workspaces/${ws}/customers/${customerId}`;
  const collectionPath = `workspaces/${ws}/collections/payment`;
  const db = new MemoryStore({ ...base(), [customerPath]: { workspaceId: ws, isDeleted: false },
    [entryPath]: { workspaceId: ws, customerId: BigInt(customerId), createdAt: now - 1000,
      createdByUid: owner, entryForUid: owner, isHistorical: false, totalAmount: 100,
      amountReceived: 100, pendingAmount: 0 },
    [collectionPath]: { workspaceId: ws, status: 'ACTIVE', jobEntryId: BigInt(entryId), customerId: BigInt(customerId),
      amount: 100, collectedByUid: owner, createdAt: now - 1000,
      allocationsJson: `[{"jobId":"${entryId}","amount":100}]` } });
  const command = protocol('DELETE_ENTRY', { entryId, actionTime: now });
  const first = await deleteWorkspaceEntry(db, owner, command);
  assert.equal(first.deleted, true);
  assert.equal(db.records.has(entryPath), false);
  assert.equal(db.records.has(collectionPath), false);
  const receipt = db.records.get(`workspaces/${ws}/entryDeleteReceipts/${command.operationId}`);
  assert.deepEqual(receipt.deletedCollectionIds, ['payment']);
  assert.equal(receipt.deletedCollectionAudit[0].amount, 100);
  assert.deepEqual(await deleteWorkspaceEntry(db, owner, command), first);
  assert.equal(db.records.has(customerPath), false);
});
test('last-entry deletion preserves a Customer referenced by reversed collection audit', async () => {
  const now = Date.now(), entryId = '991', customerId = '992';
  const customerPath = `workspaces/${ws}/customers/${customerId}`;
  const entryPath = `workspaces/${ws}/entries/${entryId}`;
  const collectionPath = `workspaces/${ws}/collections/993`;
  const db = new MemoryStore({ ...base(), [customerPath]: { workspaceId: ws, totalBilled: 100, balanceDue: 100 },
    [entryPath]: { workspaceId: ws, customerId, createdAt: now - 1000, createdByUid: owner,
      entryForUid: owner, isHistorical: false, totalAmount: 100, amountReceived: 0, pendingAmount: 100 },
    [collectionPath]: { workspaceId: ws, customerId, jobEntryId: entryId, status: 'REVERSED', amount: 40 } });
  await deleteWorkspaceEntry(db, owner, protocol('DELETE_ENTRY', { entryId, actionTime: now }));
  assert.equal(db.records.has(entryPath), false);
  assert.equal(db.records.get(customerPath).balanceDue, 0);
  assert.equal(db.records.get(customerPath).totalBilled, 0);
  assert.equal(db.records.get(collectionPath).status, 'REVERSED');
});
test('deleting one of two Entries recalculates the shared Customer and isolates another workspace', async () => {
  const now = Date.now(), entryId = '994', customerId = '995';
  const customerPath = `workspaces/${ws}/customers/${customerId}`;
  const entry = { workspaceId: ws, customerId, createdAt: now - 1000, createdByUid: owner,
    entryForUid: owner, isHistorical: false, totalAmount: 100, amountReceived: 0, pendingAmount: 100 };
  const foreignPath = `workspaces/foreign/customers/${customerId}`;
  const db = new MemoryStore({ ...base(), [customerPath]: { workspaceId: ws, totalBilled: 200, balanceDue: 200 },
    [`workspaces/${ws}/entries/${entryId}`]: entry, [`workspaces/${ws}/entries/996`]: { ...entry, createdByUid: 'partner' },
    [foreignPath]: { workspaceId: 'foreign', totalBilled: 123, balanceDue: 123 } });
  await deleteWorkspaceEntry(db, owner, protocol('DELETE_ENTRY', { entryId, actionTime: now }));
  assert.equal(db.records.get(customerPath).totalBilled, 100);
  assert.equal(db.records.get(customerPath).balanceDue, 100);
  assert.equal(db.records.get(foreignPath).balanceDue, 123);
  assert.equal(db.records.has(`workspaces/${ws}/entries/996`), true);
});
test('Deleting a shared or foreign Collection never partially deletes an Entry', async () => {
  const now = Date.now(), entryId = '91', customerId = '90';
  const entryPath = `workspaces/${ws}/entries/${entryId}`;
  const collectionPath = `workspaces/${ws}/collections/92`;
  const foundation = { ...base(),
    [`workspaces/${ws}/customers/${customerId}`]: { workspaceId: ws, isDeleted: false },
    [entryPath]: { workspaceId: ws, customerId, createdAt: now - 1000, createdByUid: owner,
      entryForUid: owner, isHistorical: false, totalAmount: 100, amountReceived: 50, pendingAmount: 50 },
    [collectionPath]: { workspaceId: ws, status: 'ACTIVE', customerId, jobEntryId: entryId,
      amount: 50, collectedByUid: owner, createdAt: now - 1000,
      allocationsJson: '[{"jobId":"91","amount":25},{"jobId":"93","amount":25}]' } };
  const command = protocol('DELETE_ENTRY', { entryId, actionTime: now });
  for (const invalid of [foundation[collectionPath], { ...foundation[collectionPath], workspaceId: 'other' }]) {
    const db = new MemoryStore({ ...foundation, [collectionPath]: invalid });
    await assert.rejects(deleteWorkspaceEntry(db, owner, command), { status: 'FAILED_PRECONDITION' });
    assert.equal(db.records.has(entryPath), true);
    assert.equal(db.records.has(collectionPath), true);
    assert.equal([...db.records.keys()].some(path => path.includes('entryDeleteReceipts')), false);
  }
});
test('Partner cannot delete an Entry paid by another collector', async () => {
  const now = Date.now(), partner = 'partner-test', entryId = '94', customerId = '95';
  const entryPath = `workspaces/${ws}/entries/${entryId}`;
  const collectionPath = `workspaces/${ws}/collections/96`;
  const db = new MemoryStore({ ...base(),
    [`workspaces/${ws}/members/${partner}`]: { workspaceId: ws, role: 'PARTNER', status: 'ACTIVE' },
    [`workspaces/${ws}/customers/${customerId}`]: { workspaceId: ws, isDeleted: false },
    [entryPath]: { workspaceId: ws, customerId, createdAt: now - 1000, createdByUid: partner,
      entryForUid: partner, isHistorical: false, authorizationTimeStatus: 'CLIENT_ORIGINAL_V2',
      totalAmount: 100, amountReceived: 100, pendingAmount: 0 },
    [collectionPath]: { workspaceId: ws, status: 'ACTIVE', customerId, jobEntryId: entryId,
      amount: 100, collectedByUid: owner, createdAt: now - 1000,
      allocationsJson: '[{"jobId":"94","amount":100}]' } });
  const command = { ...protocol('DELETE_ENTRY', { entryId, actionTime: now }), actorContext: partner };
  await assert.rejects(deleteWorkspaceEntry(db, partner, command), { status: 'FAILED_PRECONDITION' });
  assert.equal(db.records.has(entryPath), true);
  assert.equal(db.records.has(collectionPath), true);
});
test('Owner deletes Entry and two eligible linked Expenses atomically, preserving unrelated records', async () => {
  const now = Date.now(), entryId = '51', customerId = '91';
  const entryPath = `workspaces/${ws}/entries/${entryId}`;
  const first = `workspaces/${ws}/expenses/71`, second = `workspaces/${ws}/expenses/72`;
  const unrelated = `workspaces/${ws}/expenses/73`, otherWorkspace = 'workspaces/other/expenses/74';
  const db = new MemoryStore({ ...base(),
    [`workspaces/${ws}/customers/${customerId}`]: { workspaceId: ws, isDeleted: false },
    [entryPath]: { workspaceId: ws, customerId, createdAt: now - 1000, createdByUid: owner,
      entryForUid: owner, isHistorical: false, totalAmount: 100, amountReceived: 0, pendingAmount: 100 },
    [first]: { workspaceId: ws, relatedJobId: entryId, createdByUid: owner, expenseForUid: owner },
    [second]: { workspaceId: ws, relatedJobId: entryId, createdByUid: owner, expenseForUid: owner },
    [unrelated]: { workspaceId: ws, relatedJobId: '52', createdByUid: owner, expenseForUid: owner },
    [otherWorkspace]: { workspaceId: 'other', relatedJobId: entryId } });
  const command = protocol('DELETE_ENTRY', { entryId, actionTime: now });
  const acknowledgement = await deleteWorkspaceEntry(db, owner, command);
  assert.deepEqual(acknowledgement, { deleted: true, workspaceId: ws, entryId,
    operationId: command.operationId, actionTime: command.actionTime });
  assert.equal(db.records.has(entryPath), false);
  assert.equal(db.records.has(first), false);
  assert.equal(db.records.has(second), false);
  assert.equal(db.records.has(unrelated), true);
  assert.equal(db.records.has(otherWorkspace), true);
  assert.deepEqual(db.records.get(`workspaces/${ws}/entryDeleteReceipts/${command.operationId}`).deletedExpenseIds,
    ['71', '72']);
  assert.deepEqual(await deleteWorkspaceEntry(db, owner, command), acknowledgement);
  await assert.rejects(deleteWorkspaceEntry(db, owner, { ...command, actionTime: now - 1 }),
    { status: 'PERMISSION_DENIED' });
});
test('Linked Expense authorization failure leaves Entry and every Expense intact', async () => {
  const now = Date.now(), entryId = '61', customerId = '92', partner = 'partner-test';
  const entryPath = `workspaces/${ws}/entries/${entryId}`, expensePath = `workspaces/${ws}/expenses/81`;
  const db = new MemoryStore({ ...base(),
    [`workspaces/${ws}/members/${partner}`]: { workspaceId: ws, role: 'PARTNER', status: 'ACTIVE' },
    [`workspaces/${ws}/customers/${customerId}`]: { workspaceId: ws, isDeleted: false },
    [entryPath]: { workspaceId: ws, customerId, createdAt: now - 1000, createdByUid: partner,
      entryForUid: partner, isHistorical: false, authorizationTimeStatus: 'CLIENT_ORIGINAL_V2',
      totalAmount: 100, amountReceived: 0, pendingAmount: 100 },
    [expensePath]: { workspaceId: ws, relatedJobId: entryId, createdByUid: owner,
      expenseForUid: owner, createdAt: now - 1000, authorizationTimeStatus: 'CLIENT_ORIGINAL_V2' } });
  const command = { ...protocol('DELETE_ENTRY', { entryId, actionTime: now }), actorContext: partner };
  await assert.rejects(deleteWorkspaceEntry(db, partner, command), { status: 'PERMISSION_DENIED' });
  assert.equal(db.records.has(entryPath), true);
  assert.equal(db.records.has(expensePath), true);
  assert.equal([...db.records.keys()].some(key => key.includes('entryDeleteReceipts')), false);
});
test('Partner may delete own timely linked Expense; Operator may not delete any Expense', async () => {
  const now = Date.now(), partner = 'partner-test', operator = 'operator-test';
  const jobId = '62', expenseId = '82', customerId = '93';
  const records = { ...base(),
    [`workspaces/${ws}/members/${partner}`]: { workspaceId: ws, role: 'PARTNER', status: 'ACTIVE' },
    [`workspaces/${ws}/members/${operator}`]: { workspaceId: ws, role: 'OPERATOR', status: 'ACTIVE' },
    [`workspaces/${ws}/customers/${customerId}`]: { workspaceId: ws, isDeleted: false },
    [`workspaces/${ws}/entries/${jobId}`]: { workspaceId: ws, customerId, createdAt: now - 1000,
      createdByUid: partner, entryForUid: partner, isHistorical: false,
      authorizationTimeStatus: 'CLIENT_ORIGINAL_V2', totalAmount: 100, amountReceived: 0, pendingAmount: 100 },
    [`workspaces/${ws}/expenses/${expenseId}`]: { workspaceId: ws, relatedJobId: jobId,
      createdByUid: partner, expenseForUid: partner, createdAt: now - 1000,
      authorizationTimeStatus: 'CLIENT_ORIGINAL_V2' } };
  const partnerCommand = { ...protocol('DELETE_ENTRY', { entryId: jobId, actionTime: now }), actorContext: partner };
  const expired = new MemoryStore(structuredClone(records));
  expired.records.get(`workspaces/${ws}/expenses/${expenseId}`).createdAt = now - 90000000;
  await assert.rejects(deleteWorkspaceEntry(expired, partner, partnerCommand), { status: 'PERMISSION_DENIED' });
  const allowed = new MemoryStore(structuredClone(records));
  assert.equal((await deleteWorkspaceEntry(allowed, partner, partnerCommand)).deleted, true);
  assert.equal(allowed.records.has(`workspaces/${ws}/expenses/${expenseId}`), false);
  const operatorRecords = structuredClone(records);
  operatorRecords[`workspaces/${ws}/entries/${jobId}`].createdByUid = operator;
  operatorRecords[`workspaces/${ws}/entries/${jobId}`].entryForUid = operator;
  const operatorStore = new MemoryStore(operatorRecords);
  const operatorCommand = { ...partnerCommand, actorContext: operator };
  await assert.rejects(deleteWorkspaceEntry(operatorStore, operator, operatorCommand),
    { status: 'PERMISSION_DENIED' });
  assert.equal(operatorStore.records.has(`workspaces/${ws}/entries/${jobId}`), true);
});
test('Partner deletion is limited to own ordinary Entry in the current workspace and 24-hour window', async () => {
  const partner = 'partner-test', entryId = '46', customerId = '91', createdAt = Date.now() - 3600000;
  const records = { ...base(), [`workspaces/${ws}/members/${partner}`]: {
    workspaceId: ws, role: 'PARTNER', status: 'ACTIVE' },
    [`workspaces/${ws}/customers/${customerId}`]: { workspaceId: ws, isDeleted: false },
    [`workspaces/${ws}/entries/${entryId}`]: { workspaceId: ws, customerId: BigInt(customerId),
      createdAt, createdByUid: partner, entryForUid: partner, isHistorical: false,
      authorizationTimeStatus: 'CLIENT_ORIGINAL_V2', totalAmount: 100, amountReceived: 0,
      pendingAmount: 100 } };
  const command = { ...protocol('DELETE_ENTRY', { entryId, actionTime: Date.now() }), actorContext: partner };
  const expired = new MemoryStore(structuredClone(records));
  expired.records.get(`workspaces/${ws}/entries/${entryId}`).createdAt = Date.now() - 90000000;
  await assert.rejects(deleteWorkspaceEntry(expired, partner, command), { status: 'PERMISSION_DENIED' });
  const historical = new MemoryStore(structuredClone(records));
  historical.records.get(`workspaces/${ws}/entries/${entryId}`).isHistorical = true;
  await assert.rejects(deleteWorkspaceEntry(historical, partner, command), { status: 'PERMISSION_DENIED' });
  const wrongWorkspace = new MemoryStore(structuredClone(records));
  wrongWorkspace.records.get(`workspaces/${ws}/members/${partner}`).workspaceId = 'other-workspace';
  await assert.rejects(deleteWorkspaceEntry(wrongWorkspace, partner, command), { status: 'PERMISSION_DENIED' });
  const allowed = new MemoryStore(structuredClone(records));
  assert.equal((await deleteWorkspaceEntry(allowed, partner, command)).deleted, true);
});

test('Re-inviting an already-active member is idempotent and avoids duplicate memberships', async () => {
  const partner = 'partner-active', phone = '+919876543210', now = Date.now();
  const invitationId = 'invite-active';
  const records = {
    ...base(),
    [`workspaces/${ws}/members/${partner}`]: { uid: partner, workspaceId: ws, role: 'PARTNER', status: 'ACTIVE', phoneNumber: phone },
    [`workspaces/${ws}/invitations/${invitationId}`]: { workspaceId: ws, ownerUid: owner, phone, role: 'PARTNER', name: 'Active Partner', status: 'PENDING', createdAt: now, expiresAt: now + 300000 },
    [`phoneDirectory/${phone}`]: { uid: partner, phone }
  };
  const db = new MemoryStore(records);
  const targetClaims = { uid: partner, phone_number: phone, auth_time: Math.floor(now / 1000), firebase: { sign_in_provider: 'phone' } };
  const result = await redeemWorkspaceInvitation(db, owner, { workspaceId: ws, invitationId }, targetClaims);
  assert.equal(result.uid, partner);
  assert.equal(result.role, 'PARTNER');
  assert.equal(result.alreadyActive, true);
  assert.equal(db.records.get(`workspaces/${ws}/invitations/${invitationId}`).status, 'USED');
});

test('Re-inviting a removed member cannot reactivate membership or erase audit history', async () => {
  const partner = 'partner-removed', phone = '+919876543210', now = Date.now();
  const invitationId = 'invite-reactivate';
  const records = {
    ...base(),
    [`workspaces/${ws}/members/${partner}`]: { uid: partner, workspaceId: ws, role: 'PARTNER', status: 'REMOVED', phoneNumber: phone },
    [`workspaces/${ws}/deletedUsers/${partner}`]: { uid: partner, purgeState: 'ARCHIVED' },
    [`workspaces/${ws}/invitations/${invitationId}`]: { workspaceId: ws, ownerUid: owner, phone, role: 'PARTNER', name: 'Reactivated Partner', status: 'PENDING', createdAt: now, expiresAt: now + 300000 },
    [`phoneDirectory/${phone}`]: { uid: partner, phone }
  };
  const db = new MemoryStore(records);
  const targetClaims = { uid: partner, phone_number: phone, auth_time: Math.floor(now / 1000), firebase: { sign_in_provider: 'phone' } };
  await assert.rejects(redeemWorkspaceInvitation(db, owner, { workspaceId: ws, invitationId }, targetClaims), { status: 'ALREADY_EXISTS' });
  assert.equal(db.records.get(`workspaces/${ws}/members/${partner}`).status, 'REMOVED');
  assert.equal(db.records.has(`workspaces/${ws}/deletedUsers/${partner}`), true);
  assert.equal(db.records.get(`workspaces/${ws}/invitations/${invitationId}`).status, 'PENDING');
});

test('Owner can delete an entry created by a removed member while non-owner cannot', async () => {
  const removedPartner = 'partner-former', activePartner = 'partner-current', entryId = '101', customerId = '201', now = Date.now();
  const records = {
    ...base(),
    [`workspaces/${ws}/members/${removedPartner}`]: { uid: removedPartner, workspaceId: ws, role: 'PARTNER', status: 'REMOVED' },
    [`workspaces/${ws}/members/${activePartner}`]: { uid: activePartner, workspaceId: ws, role: 'PARTNER', status: 'ACTIVE' },
    [`workspaces/${ws}/customers/${customerId}`]: { workspaceId: ws, isDeleted: false },
    [`workspaces/${ws}/entries/${entryId}`]: {
      workspaceId: ws, customerId: BigInt(customerId), createdAt: now - 3600000,
      createdByUid: removedPartner, entryForUid: removedPartner, isHistorical: false,
      authorizationTimeStatus: 'CLIENT_ORIGINAL_V2', totalAmount: 500, amountReceived: 0, pendingAmount: 500
    }
  };
  const partnerStore = new MemoryStore(structuredClone(records));
  const partnerCmd = { ...protocol('DELETE_ENTRY', { entryId, actionTime: now }), actorContext: activePartner };
  await assert.rejects(deleteWorkspaceEntry(partnerStore, activePartner, partnerCmd), { status: 'PERMISSION_DENIED' });

  const ownerStore = new MemoryStore(structuredClone(records));
  const ownerCmd = { ...protocol('DELETE_ENTRY', { entryId, actionTime: now }), actorContext: owner };
  const result = await deleteWorkspaceEntry(ownerStore, owner, ownerCmd);
  assert.equal(result.deleted, true);
  assert.equal(ownerStore.records.has(`workspaces/${ws}/entries/${entryId}`), false);
});


test('accepted invitation retry is idempotent and cannot restore revoked access', async () => {
 const target='new-partner', phone='+919000000123', now=Date.now();
 const db=new MemoryStore({...base(),
  [`workspaces/${ws}/invitations/once`]:{workspaceId:ws,ownerUid:owner,phone,role:'PARTNER',name:'Fixture',status:'PENDING',createdAt:now,expiresAt:now+300000}});
 const claims={uid:target,phone_number:phone,auth_time:Math.floor(now/1000),firebase:{sign_in_provider:'phone'}};
 const command={workspaceId:ws,invitationId:'once'};
 await redeemWorkspaceInvitation(db,owner,command,claims);
 const before=structuredClone([...db.records]);
 assert.equal((await redeemWorkspaceInvitation(db,owner,command,claims)).alreadyAccepted,true);
 assert.deepEqual([...db.records],before);
 db.records.get(`workspaces/${ws}/members/${target}`).status='REMOVED';
 await assert.rejects(redeemWorkspaceInvitation(db,owner,command,claims),{status:'PERMISSION_DENIED'});
});
test('an invitation cannot upgrade an existing member role', async()=>{
 const now=Date.now(), target='operator-fixture', phone='+919000000124';
 const db=new MemoryStore({...base(),
 [`workspaces/${ws}/members/${target}`]:{uid:target,workspaceId:ws,status:'ACTIVE',role:'OPERATOR'},
 [`workspaces/${ws}/invitations/no-upgrade`]:{workspaceId:ws,ownerUid:owner,phone,role:'PARTNER',name:'Fixture',status:'PENDING',createdAt:now,expiresAt:now+300000}});
 await assert.rejects(redeemWorkspaceInvitation(db,owner,{workspaceId:ws,invitationId:'no-upgrade'},
 {uid:target,phone_number:phone,auth_time:Math.floor(now/1000),firebase:{sign_in_provider:'phone'}}),{status:'PERMISSION_DENIED'});
 assert.equal(db.records.get(`workspaces/${ws}/members/${target}`).role,'OPERATOR');
});

test('invitation request retry preserves one document and its original expiry',async()=>{
 const db=new MemoryStore(base());
 const command={workspaceId:ws,operationId:'stable-invitation',phone:'+919000000125',role:'PARTNER',name:'Fixture'};
 const first=await beginWorkspaceInvitation(db,owner,command);
 assert.deepEqual(await beginWorkspaceInvitation(db,owner,command),first);
 assert.equal([...db.records.keys()].filter(k=>k.startsWith(`workspaces/${ws}/invitations/`)).length,1);
 await assert.rejects(beginWorkspaceInvitation(db,owner,{...command,role:'OPERATOR'}),{status:'PERMISSION_DENIED'});
});

test('staging workspace allowlist admits only explicit staging workspaces and rejects production', () => {
  assert.equal(isAuthorizedStagingWorkspace('ws_cf_staging_beb841c2f4d9475a'), true);
  assert.equal(isAuthorizedStagingWorkspace('ws_cf_staging_20261015_7361_7f4c92'), true);
  assert.equal(isAuthorizedStagingWorkspace('ws_cf_staging_20261015_login_a62e71'), true);
  // Production workspaces strictly rejected
  assert.equal(isAuthorizedStagingWorkspace('ws_G7RZespbU6cwIUha'), false);
  assert.equal(isAuthorizedStagingWorkspace('ws_QlVAEPVTtYSxLeWC'), false);
  // Arbitrary unapproved staging prefix rejected
  assert.equal(isAuthorizedStagingWorkspace('ws_cf_staging_unapproved_123'), false);
  assert.equal(isAuthorizedStagingWorkspace(''), false);
  assert.equal(isAuthorizedStagingWorkspace(null), false);
  assert.equal(isAuthorizedStagingWorkspace(undefined), false);
});

test('HTTP handler routes all authorized staging workspaces when actor is member and rejects production', async () => {
  const { pair, jwk, pem } = await getSharedAuth();
  const b64 = value => Buffer.from(value).toString('base64url');
  const issued = Math.floor(Date.now() / 1000);
  const sign = async uid => {
    const input = `${b64(JSON.stringify({ alg: 'RS256', kid: 'handler-test-key' }))}.${b64(JSON.stringify({
      aud: 'trac-aidhunt', iss: 'https://securetoken.google.com/trac-aidhunt', sub: uid,
      auth_time: issued - 10, iat: issued - 10, exp: issued + 300 }))}`;
    return `${input}.${b64(await crypto.subtle.sign('RSASSA-PKCS1-v1_5', pair.privateKey,
      new TextEncoder().encode(input)))}`;
  };
  const env = { FIREBASE_PROJECT_ID: 'trac-aidhunt', STAGING_WORKSPACE_ID: 'ws_cf_staging_20261015_login_a62e71',
    GOOGLE_SERVICE_ACCOUNT_JSON: JSON.stringify({ type: 'service_account', project_id: 'trac-aidhunt',
      client_email: 'handler-test@trac-aidhunt.iam.gserviceaccount.com', private_key: pem }) };
  const fetcher = async (url, options) => {
    if (url.includes('/jwk/')) return new Response(JSON.stringify({ keys: [{ ...jwk, kid: 'handler-test-key' }] }));
    if (url.includes('oauth2.googleapis.com')) return new Response(JSON.stringify({ access_token: 'handler-multi-oauth', expires_in: 3600 }));
    if (url.includes('accounts:lookup')) {
      const uid = JSON.parse(options.body).localId[0];
      return new Response(JSON.stringify({ users: [{ localId: uid, validSince: String(issued - 100) }] }));
    }
    throw Error('Unexpected external request');
  };
  const ws7361 = 'ws_cf_staging_20261015_7361_7f4c92';
  const db = new MemoryStore({
    [`workspaces/${ws7361}`]: { ownerUid: owner },
    [`workspaces/${ws7361}/members/${owner}`]: { workspaceId: ws7361, role: 'OWNER', status: 'ACTIVE' },
  });
  const call = async (uid, workspaceId) => handleRequest(new Request('https://worker.example/beginWorkspaceInvitation', {
    method: 'POST', headers: { Authorization: `Bearer ${await sign(uid)}` },
    body: JSON.stringify({ data: { workspaceId, phone: '+919999999998', role: 'PARTNER', name: 'Test' } })
  }), env, { fetcher, db });

  // 1. Authorized staging workspace ws_cf_staging_20261015_7361_7f4c92 succeeds
  const res7361 = await call(owner, ws7361);
  assert.equal(res7361.status, 200);

  // 2. Production workspace strictly rejected with 403 PERMISSION_DENIED
  const resProd = await call(owner, 'ws_G7RZespbU6cwIUha');
  assert.equal(resProd.status, 403);
  assert.equal((await resProd.json()).error.status, 'PERMISSION_DENIED');

  // 3. Unapproved staging workspace rejected with 403 PERMISSION_DENIED
  const resUnapproved = await call(owner, 'ws_cf_staging_unapproved_fake');
  assert.equal(resUnapproved.status, 403);
  assert.equal((await resUnapproved.json()).error.status, 'PERMISSION_DENIED');
});

for (const [label, role, status, workspaceOwner, memberWorkspace] of [
  ['Partner cannot invite', 'PARTNER', 'ACTIVE', owner, ws],
  ['Operator cannot invite', 'OPERATOR', 'ACTIVE', owner, ws],
  ['Removed Owner cannot invite', 'OWNER', 'REMOVED', owner, ws],
  ['Role alone is not workspace ownership', 'OWNER', 'ACTIVE', 'different-owner', ws],
  ['Foreign membership cannot invite', 'OWNER', 'ACTIVE', owner, 'foreign-workspace'],
]) test(`invitation boundary: ${label}`, async () => {
  const db = new MemoryStore({
    [`workspaces/${ws}`]: {ownerUid: workspaceOwner},
    [`workspaces/${ws}/members/${owner}`]: {workspaceId: memberWorkspace, role, status},
  });
  const before = structuredClone([...db.records]);
  await assert.rejects(beginWorkspaceInvitation(db, owner, {
    workspaceId: ws, phone: '+919999999999', role: 'PARTNER', name: 'Synthetic',
    actorUid: owner, actorRole: 'OWNER',
  }), {status: 'PERMISSION_DENIED'});
  assert.deepEqual([...db.records], before);
});
