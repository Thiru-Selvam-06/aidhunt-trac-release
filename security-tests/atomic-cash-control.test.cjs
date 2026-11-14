const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, updateDoc, deleteDoc, getDoc, writeBatch, runTransaction, serverTimestamp, Timestamp } = require('firebase/firestore');

if (process.env.GCLOUD_PROJECT !== 'demo-aidhunt-v16' ||
    process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:18180' ||
    process.env.GOOGLE_APPLICATION_CREDENTIALS) {
  throw Error('Local synthetic emulator only');
}

let env;
let dbCache = new Map();
const ws = 'ws_atomic_cash';
const now = Date.now() - 5000;
const db = uid => {
  if (!dbCache.has(uid)) dbCache.set(uid, env.authenticatedContext(uid).firestore());
  return dbCache.get(uid);
};
const command = (actor, opId, type = 'UPSERT') => ({
  workspaceId: ws,
  protocolVersion: 2,
  operationType: type,
  dependencies: [],
  operationId: opId,
  actorUid: actor,
  actionTime: now,
  isDeleted: false
});

async function seedWorkspace({ confirmedCash = 1000, reservedCash = 0, version = 1, includeBaseline = true, atomicFlag = true } = {}) {
  dbCache.clear();
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx => {
    const d = ctx.firestore();
    await setDoc(doc(d, `workspaces/${ws}`), {
      workspaceId: ws,
      ownerUid: 'owner',
      syncCapabilities: atomicFlag ? { atomicCashControlV1: true } : {}
    });
    for (const [uid, role, status, name] of [
      ['owner', 'OWNER', 'ACTIVE', 'Owner'],
      ['partnerA', 'PARTNER', 'ACTIVE', 'Partner A'],
      ['partnerB', 'PARTNER', 'ACTIVE', 'Partner B'],
      ['operator', 'OPERATOR', 'ACTIVE', 'Operator'],
      ['removed', 'PARTNER', 'REMOVED', 'Removed']
    ]) {
      await setDoc(doc(d, `workspaces/${ws}/members/${uid}`), {
        workspaceId: ws, uid, role, status, displayName: name
      });
    }
    await setDoc(doc(d, `workspaces/${ws}/customers/10`), {
      id: 10, workspaceId: ws, name: 'Synthetic Customer', totalBilled: 2000, totalPaid: 0, balanceDue: 2000,
      createdAt: now - 1000, createdByUid: 'owner', isDeleted: false, ...command('owner', 'seed-cust')
    });
    await setDoc(doc(d, `workspaces/${ws}/entries/100`), {
      id: 100, workspaceId: ws, customerId: 10, customerName: 'Synthetic Customer',
      totalAmount: 2000, amountReceived: 0, pendingAmount: 2000, isHistorical: false,
      createdAt: now - 1000, createdByUid: 'partnerA', entryForUid: 'partnerA', financialAttributionUid: 'partnerA',
      authorizationTimeStatus: 'CLIENT_ORIGINAL_V2', authorizationCreatedAt: Timestamp.fromMillis(now - 1000),
      serverReceivedAt: serverTimestamp(), isDeleted: false, ...command('partnerA', 'seed-job')
    });
    if (includeBaseline) {
      await setDoc(doc(d, `workspaces/${ws}/financialState/current`), {
        workspaceId: ws,
        version,
        confirmedCash,
        reservedCash,
        confirmedBank: 0,
        baselineSource: 'SERVER_COMPUTED',
        lastOperationId: 'seed-baseline',
        lastEntityType: 'BASELINE',
        lastEntityId: 'current',
        lastActorUid: 'owner',
        actionTime: now - 1000,
        updatedAt: serverTimestamp()
      });
    }
  });
}

function withdrawalDoc(actor, id, amount, opId, t = now) {
  return {
    ...command(actor, opId),
    id: Number(id),
    amount,
    partnerName: actor === 'partnerA' ? 'Partner A' : 'Partner B',
    targetPartnerUid: actor,
    createdByUid: actor,
    createdByRole: 'PARTNER',
    createdAt: t,
    dateTimestamp: t,
    notes: 'Synthetic withdrawal',
    authorizationTimeStatus: 'CLIENT_ORIGINAL_V2',
    authorizationCreatedAt: Timestamp.fromMillis(t),
    serverReceivedAt: serverTimestamp()
  };
}

function cashOpDoc(actor, opId, entityType, entityId, mutationType, cashDelta, expectedVersion, confirmedCashBefore, extra = {}) {
  return {
    workspaceId: ws,
    operationId: opId,
    entityType,
    entityId: String(entityId),
    mutationType,
    actorUid: actor,
    actionTime: now,
    cashDelta,
    reservedDelta: 0,
    expectedVersion,
    newVersion: expectedVersion + 1,
    confirmedCashAfter: confirmedCashBefore + cashDelta,
    createdAt: serverTimestamp(),
    ...extra
  };
}

function fsUpdateDoc(actor, opId, entityType, entityId, newVersion, nextCash, reservedCash = 0) {
  return {
    version: newVersion,
    confirmedCash: nextCash,
    reservedCash,
    confirmedBank: 0,
    lastOperationId: opId,
    lastEntityType: entityType,
    lastEntityId: String(entityId),
    lastActorUid: actor,
    actionTime: now,
    updatedAt: serverTimestamp()
  };
}

test.before(async () => {
  env = await initializeTestEnvironment({
    projectId: 'demo-aidhunt-v16',
    firestore: {
      host: '127.0.0.1',
      port: 18180,
      rules: fs.readFileSync(path.join(__dirname, '../firestore.rules'), 'utf8')
    }
  });
});

test.after(async () => env?.cleanup());

test('concurrent Rs 700 withdrawals against Rs 1000 shared cash allow strictly one and block negative cash', async () => {
  await seedWorkspace({ confirmedCash: 1000, version: 1 });

  async function tryWithdraw(actor, id, amount, opId) {
    const database = db(actor);
    return runTransaction(database, async tx => {
      const fsRef = doc(database, `workspaces/${ws}/financialState/current`);
      const fsSnap = await tx.get(fsRef);
      const data = fsSnap.data();
      const avail = data.confirmedCash - (data.reservedCash || 0);
      if (avail < amount) throw new Error('INSUFFICIENT');
      tx.set(doc(database, `workspaces/${ws}/withdrawals/${id}`), withdrawalDoc(actor, id, amount, opId));
      tx.set(doc(database, `workspaces/${ws}/cashOperations/${opId}`), cashOpDoc(actor, opId, 'WITHDRAWAL', id, 'UPSERT', -amount, data.version, data.confirmedCash));
      tx.update(fsRef, fsUpdateDoc(actor, opId, 'WITHDRAWAL', id, data.version + 1, data.confirmedCash - amount));
    });
  }

  const results = await Promise.allSettled([
    tryWithdraw('partnerA', 501, 700, 'op-withdraw-a'),
    tryWithdraw('partnerB', 502, 700, 'op-withdraw-b')
  ]);

  const fulfilled = results.filter(r => r.status === 'fulfilled');
  const rejected = results.filter(r => r.status === 'rejected');
  assert.equal(fulfilled.length, 1);
  assert.equal(rejected.length, 1);

  await env.withSecurityRulesDisabled(async ctx => {
    const d = ctx.firestore();
    const state = (await getDoc(doc(d, `workspaces/${ws}/financialState/current`))).data();
    assert.equal(state.confirmedCash, 300);
    assert.equal(state.version, 2);
  });

  // Even bypassing client check directly against Rules rejects -Rs 400 withdrawal
  const malicious = writeBatch(db('partnerB'));
  malicious.set(doc(db('partnerB'), `workspaces/${ws}/withdrawals/503`), withdrawalDoc('partnerB', 503, 700, 'op-withdraw-bypass'));
  malicious.set(doc(db('partnerB'), `workspaces/${ws}/cashOperations/op-withdraw-bypass`), cashOpDoc('partnerB', 'op-withdraw-bypass', 'WITHDRAWAL', 503, 'UPSERT', -700, 2, 300));
  malicious.update(doc(db('partnerB'), `workspaces/${ws}/financialState/current`), fsUpdateDoc('partnerB', 'op-withdraw-bypass', 'WITHDRAWAL', 503, 3, -400));
  await assertFails(malicious.commit());
});

test('missing baseline cannot be fabricated by ordinary clients and blocks shared-cash mutations', async () => {
  await seedWorkspace({ includeBaseline: false, atomicFlag: true });
  await assertFails(setDoc(doc(db('owner'), `workspaces/${ws}/financialState/current`), {
    workspaceId: ws, version: 1, confirmedCash: 99999, reservedCash: 0, confirmedBank: 0,
    baselineSource: 'CLIENT_FAKE', lastOperationId: 'fake', lastEntityType: 'WITHDRAWAL',
    lastEntityId: '1', lastActorUid: 'owner', actionTime: now, updatedAt: serverTimestamp()
  }));
  await assertFails(setDoc(doc(db('partnerA'), `workspaces/${ws}/withdrawals/510`), withdrawalDoc('partnerA', 510, 100, 'op-no-baseline')));
});

test('duplicate operationId, stale expectedVersion, and tampered delta/confirmedCash are rejected', async () => {
  await seedWorkspace({ confirmedCash: 1000, version: 3 });

  // Stale expectedVersion = 2 (current is 3)
  const stale = writeBatch(db('partnerA'));
  stale.set(doc(db('partnerA'), `workspaces/${ws}/withdrawals/511`), withdrawalDoc('partnerA', 511, 200, 'op-stale'));
  stale.set(doc(db('partnerA'), `workspaces/${ws}/cashOperations/op-stale`), cashOpDoc('partnerA', 'op-stale', 'WITHDRAWAL', 511, 'UPSERT', -200, 2, 1000));
  stale.update(doc(db('partnerA'), `workspaces/${ws}/financialState/current`), fsUpdateDoc('partnerA', 'op-stale', 'WITHDRAWAL', 511, 3, 800));
  await assertFails(stale.commit());

  // Tampered confirmedCashAfter (+500 instead of -200)
  const tampered = writeBatch(db('partnerA'));
  tampered.set(doc(db('partnerA'), `workspaces/${ws}/withdrawals/512`), withdrawalDoc('partnerA', 512, 200, 'op-tamper'));
  tampered.set(doc(db('partnerA'), `workspaces/${ws}/cashOperations/op-tamper`), cashOpDoc('partnerA', 'op-tamper', 'WITHDRAWAL', 512, 'UPSERT', -200, 3, 1000));
  tampered.update(doc(db('partnerA'), `workspaces/${ws}/financialState/current`), fsUpdateDoc('partnerA', 'op-tamper', 'WITHDRAWAL', 512, 4, 1500));
  await assertFails(tampered.commit());

  // Valid commit succeeds
  const valid = writeBatch(db('partnerA'));
  valid.set(doc(db('partnerA'), `workspaces/${ws}/withdrawals/513`), withdrawalDoc('partnerA', 513, 200, 'op-valid-513'));
  valid.set(doc(db('partnerA'), `workspaces/${ws}/cashOperations/op-valid-513`), cashOpDoc('partnerA', 'op-valid-513', 'WITHDRAWAL', 513, 'UPSERT', -200, 3, 1000));
  valid.update(doc(db('partnerA'), `workspaces/${ws}/financialState/current`), fsUpdateDoc('partnerA', 'op-valid-513', 'WITHDRAWAL', 513, 4, 800));
  await assertSucceeds(valid.commit());

  // Replaying duplicate operationId cannot debit twice
  const replay = writeBatch(db('partnerA'));
  replay.set(doc(db('partnerA'), `workspaces/${ws}/withdrawals/514`), withdrawalDoc('partnerA', 514, 200, 'op-valid-513'));
  replay.set(doc(db('partnerA'), `workspaces/${ws}/cashOperations/op-valid-513`), cashOpDoc('partnerA', 'op-valid-513', 'WITHDRAWAL', 514, 'UPSERT', -200, 4, 800));
  replay.update(doc(db('partnerA'), `workspaces/${ws}/financialState/current`), fsUpdateDoc('partnerA', 'op-valid-513', 'WITHDRAWAL', 514, 5, 600));
  await assertFails(replay.commit());

  // Cash receipt & financialState cannot be edited or deleted directly
  await assertFails(updateDoc(doc(db('owner'), `workspaces/${ws}/cashOperations/op-valid-513`), { cashDelta: 0 }));
  await assertFails(deleteDoc(doc(db('owner'), `workspaces/${ws}/cashOperations/op-valid-513`)));
  await assertFails(deleteDoc(doc(db('owner'), `workspaces/${ws}/financialState/current`)));
});

test('old-client bypasses are blocked across withdrawals, collections, expenses, and entry deletion', async () => {
  await seedWorkspace({ confirmedCash: 1000, version: 1 });

  // Direct withdrawal create without receipt
  await assertFails(setDoc(doc(db('partnerA'), `workspaces/${ws}/withdrawals/601`), withdrawalDoc('partnerA', 601, 100, 'op-old-w')));

  // Direct Cash collection without receipt AND non-canonical 'cash' method bypass
  for (const method of ['Cash', 'cash', 'Cash ', 'UPI / GPay ']) {
    const colBatch = writeBatch(db('partnerA'));
    colBatch.set(doc(db('partnerA'), `workspaces/${ws}/collections/701`), {
      ...command('partnerA', `op-old-c-${method}`),
      id: 701, jobEntryId: 100, customerId: 10, customerName: 'Synthetic Customer', amount: 500,
      paymentMethod: method, status: 'ACTIVE', collectedByUid: 'partnerA', collectorUid: 'partnerA',
      collectorName: 'Partner A', collectorRole: 'PARTNER', collectedAt: now, createdAt: now,
      allocationsJson: JSON.stringify([{ jobId: 100, amount: 500 }]),
      authorizationTimeStatus: 'CLIENT_ORIGINAL_V2', authorizationCreatedAt: Timestamp.fromMillis(now), serverReceivedAt: serverTimestamp()
    });
    colBatch.update(doc(db('partnerA'), `workspaces/${ws}/entries/100`), {
      protocolVersion: 2, operationType: 'UPSERT', dependencies: [], collectionId: '701',
      operationId: `op-old-c-${method}`, actorUid: 'partnerA', actionTime: now, isDeleted: false,
      amountReceived: 500, pendingAmount: 1500
    });
    await assertFails(colBatch.commit());
  }

  // Direct Business Paid expense without receipt AND non-canonical paidBy
  for (const paidBy of ['Business Paid', 'business paid']) {
    await assertFails(setDoc(doc(db('partnerA'), `workspaces/${ws}/expenses/801`), {
      ...command('partnerA', `op-old-e-${paidBy}`),
      id: 801, workspaceId: ws, amount: 250, expenseType: 'Fuel', paidBy, paidByUid: '', paidByPartner: '',
      createdByUid: 'partnerA', createdByRole: 'PARTNER', expenseForUid: 'partnerA', financialAttributionUid: 'partnerA',
      createdAt: now, authorizationTimeStatus: 'CLIENT_ORIGINAL_V2', authorizationCreatedAt: Timestamp.fromMillis(now), serverReceivedAt: serverTimestamp()
    }));
  }

  // Entry deletion proof without cashOperations receipt
  const delBatch = writeBatch(db('owner'));
  delBatch.delete(doc(db('owner'), `workspaces/${ws}/entries/100`));
  delBatch.set(doc(db('owner'), `workspaces/${ws}/entryDeletionProofs/100`), {
    workspaceId: ws, recordId: '100', actorUid: 'owner', operationId: 'op-old-job-del', actionTime: now,
    protocolVersion: 2, sourceCreatedAt: now - 1000, customerId: 10, sourceCreatorUid: 'partnerA',
    sourceBeneficiaryUid: 'partnerA', totalAmount: 2000, amountReceived: 0, pendingAmount: 2000,
    collectionIds: [], deletedCollectionAuditJson: '[]', deletedExpenseAuditJson: '[]',
    deletedJobEditHistory: [], deletedCustomerReassignmentHistory: []
  });
  await assertFails(delBatch.commit());
});

test('UPI collections stay non-cash while Business Paid expenses allow negative cash and funding flips update cash cleanly', async () => {
  await seedWorkspace({ confirmedCash: 200, version: 1 });

  // 1. UPI collection does NOT increase confirmedCash (and is NOT withdrawable as cash)
  const upi = writeBatch(db('partnerA'));
  upi.set(doc(db('partnerA'), `workspaces/${ws}/collections/710`), {
    ...command('partnerA', 'op-upi-710'),
    id: 710, jobEntryId: 100, customerId: 10, customerName: 'Synthetic Customer', amount: 1500,
    paymentMethod: 'UPI / GPay', status: 'ACTIVE', collectedByUid: 'partnerA', collectorUid: 'partnerA',
    collectorName: 'Partner A', collectorRole: 'PARTNER', collectedAt: now, createdAt: now,
    allocationsJson: JSON.stringify([{ jobId: 100, amount: 1500 }]),
    authorizationTimeStatus: 'CLIENT_ORIGINAL_V2', authorizationCreatedAt: Timestamp.fromMillis(now), serverReceivedAt: serverTimestamp()
  });
  upi.update(doc(db('partnerA'), `workspaces/${ws}/entries/100`), {
    protocolVersion: 2, operationType: 'UPSERT', dependencies: [], collectionId: '710',
    operationId: 'op-upi-710', actorUid: 'partnerA', actionTime: now, isDeleted: false,
    amountReceived: 1500, pendingAmount: 500
  });
  await assertSucceeds(upi.commit());

  // Withdrawing 300 against 200 confirmedCash (+ 1500 UPI) MUST fail
  const overdraw = writeBatch(db('partnerA'));
  overdraw.set(doc(db('partnerA'), `workspaces/${ws}/withdrawals/520`), withdrawalDoc('partnerA', 520, 300, 'op-overdraw-upi'));
  overdraw.set(doc(db('partnerA'), `workspaces/${ws}/cashOperations/op-overdraw-upi`), cashOpDoc('partnerA', 'op-overdraw-upi', 'WITHDRAWAL', 520, 'UPSERT', -300, 1, 200));
  overdraw.update(doc(db('partnerA'), `workspaces/${ws}/financialState/current`), fsUpdateDoc('partnerA', 'op-overdraw-upi', 'WITHDRAWAL', 520, 2, -100));
  await assertFails(overdraw.commit());

  // 2. Business Paid expense of Rs 500 against Rs 200 cash IS allowed (leaves -300)
  const exp = writeBatch(db('partnerA'));
  exp.set(doc(db('partnerA'), `workspaces/${ws}/expenses/810`), {
    ...command('partnerA', 'op-exp-810'),
    id: 810, amount: 500, expenseType: 'Diesel', paidBy: 'Business Paid', paidByUid: '', paidByPartner: '',
    createdByUid: 'partnerA', createdByRole: 'PARTNER', expenseForUid: 'partnerA', financialAttributionUid: 'partnerA',
    createdAt: now, authorizationTimeStatus: 'CLIENT_ORIGINAL_V2', authorizationCreatedAt: Timestamp.fromMillis(now), serverReceivedAt: serverTimestamp()
  });
  exp.set(doc(db('partnerA'), `workspaces/${ws}/cashOperations/op-exp-810`), cashOpDoc('partnerA', 'op-exp-810', 'EXPENSE', 810, 'UPSERT', -500, 1, 200));
  exp.update(doc(db('partnerA'), `workspaces/${ws}/financialState/current`), fsUpdateDoc('partnerA', 'op-exp-810', 'EXPENSE', 810, 2, -300));
  await assertSucceeds(exp.commit());

  // 3. Flipping Business Paid (500) -> I Paid (500) atomically refunds +500 back to +200
  const flip = writeBatch(db('partnerA'));
  flip.update(doc(db('partnerA'), `workspaces/${ws}/expenses/810`), {
    ...command('partnerA', 'op-exp-flip'),
    paidBy: 'I Paid', paidByUid: 'partnerA', paidByPartner: 'Partner A'
  });
  flip.set(doc(db('partnerA'), `workspaces/${ws}/cashOperations/op-exp-flip`), cashOpDoc('partnerA', 'op-exp-flip', 'EXPENSE', 810, 'UPSERT', 500, 2, -300));
  flip.update(doc(db('partnerA'), `workspaces/${ws}/financialState/current`), fsUpdateDoc('partnerA', 'op-exp-flip', 'EXPENSE', 810, 3, 200));
  await assertSucceeds(flip.commit());

  // 4. Deleting an I Paid expense uses cashDeleteProofs with requiresCashOp: false and leaves cash at 200
  const delExp = writeBatch(db('partnerA'));
  delExp.delete(doc(db('partnerA'), `workspaces/${ws}/expenses/810`));
  delExp.set(doc(db('partnerA'), `workspaces/${ws}/cashDeleteProofs/EXPENSE_810`), {
    workspaceId: ws, kind: 'EXPENSE', recordId: '810', actorUid: 'partnerA',
    operationId: 'op-exp-del', actionTime: now, requiresCashOp: false, createdAt: serverTimestamp()
  });
  await assertSucceeds(delExp.commit());
});

test('withdrawal single-step delete atomically refunds cash while two-step tombstone update is blocked', async () => {
  await seedWorkspace({ confirmedCash: 1000, version: 1 });
  const create = writeBatch(db('partnerA'));
  create.set(doc(db('partnerA'), `workspaces/${ws}/withdrawals/530`), withdrawalDoc('partnerA', 530, 400, 'op-w-530'));
  create.set(doc(db('partnerA'), `workspaces/${ws}/cashOperations/op-w-530`), cashOpDoc('partnerA', 'op-w-530', 'WITHDRAWAL', 530, 'UPSERT', -400, 1, 1000));
  create.update(doc(db('partnerA'), `workspaces/${ws}/financialState/current`), fsUpdateDoc('partnerA', 'op-w-530', 'WITHDRAWAL', 530, 2, 600));
  await assertSucceeds(create.commit());

  // Legacy 2-step tombstone update or raw delete without proof is blocked
  await assertFails(updateDoc(doc(db('partnerA'), `workspaces/${ws}/withdrawals/530`), {
    ...command('partnerA', 'op-w-tombstone', 'DELETE'), isDeleted: true, authorizationDeletedAt: serverTimestamp()
  }));
  await assertFails(deleteDoc(doc(db('partnerA'), `workspaces/${ws}/withdrawals/530`)));

  // Atomic 1-step delete refunds +400 -> 1000
  const del = writeBatch(db('partnerA'));
  del.delete(doc(db('partnerA'), `workspaces/${ws}/withdrawals/530`));
  del.set(doc(db('partnerA'), `workspaces/${ws}/cashDeleteProofs/WITHDRAWAL_530`), {
    workspaceId: ws, kind: 'WITHDRAWAL', recordId: '530', actorUid: 'partnerA',
    operationId: 'op-w-530-del', actionTime: now, requiresCashOp: true, createdAt: serverTimestamp()
  });
  del.set(doc(db('partnerA'), `workspaces/${ws}/cashOperations/op-w-530-del`), cashOpDoc('partnerA', 'op-w-530-del', 'WITHDRAWAL', 530, 'DELETE', 400, 2, 600));
  del.update(doc(db('partnerA'), `workspaces/${ws}/financialState/current`), fsUpdateDoc('partnerA', 'op-w-530-del', 'WITHDRAWAL', 530, 3, 1000));
  await assertSucceeds(del.commit());

  await env.withSecurityRulesDisabled(async ctx => {
    const state = (await getDoc(doc(ctx.firestore(), `workspaces/${ws}/financialState/current`))).data();
    assert.equal(state.confirmedCash, 1000);
    assert.equal(state.version, 3);
  });
});

test('partner job delete cannot fabricate positive cashDelta without real deleted Business Paid expenses', async () => {
  await seedWorkspace({ confirmedCash: 500, version: 1 });
  // Partner creates a fake positive cashDelta (+5000) while deleting job 100 which has no expenses
  const forge = writeBatch(db('partnerA'));
  forge.delete(doc(db('partnerA'), `workspaces/${ws}/entries/100`));
  forge.set(doc(db('partnerA'), `workspaces/${ws}/entryDeletionProofs/100`), {
    workspaceId: ws, recordId: '100', actorUid: 'partnerA', operationId: 'op-forge-job-del', actionTime: now,
    protocolVersion: 2, sourceCreatedAt: now - 1000, customerId: 10, sourceCreatorUid: 'partnerA',
    sourceBeneficiaryUid: 'partnerA', totalAmount: 2000, amountReceived: 0, pendingAmount: 2000,
    collectionIds: [], deletedCollectionAuditJson: '[]', deletedExpenseAuditJson: '[]',
    deletedJobEditHistory: [], deletedCustomerReassignmentHistory: []
  });
  forge.set(doc(db('partnerA'), `workspaces/${ws}/cashOperations/op-forge-job-del`), cashOpDoc('partnerA', 'op-forge-job-del', 'JOB', 100, 'DELETE', 5000, 1, 500));
  forge.update(doc(db('partnerA'), `workspaces/${ws}/financialState/current`), fsUpdateDoc('partnerA', 'op-forge-job-del', 'JOB', 100, 2, 5500));
  await assertFails(forge.commit());
});

test('unauthorized operators, removed members, and foreign actors cannot mutate shared cash', async () => {
  await seedWorkspace({ confirmedCash: 1000, version: 1 });
  for (const actor of ['operator', 'removed', 'outsider']) {
    await assertFails(getDoc(doc(db(actor), `workspaces/${ws}/financialState/current`)));
    const attempt = writeBatch(db(actor));
    attempt.set(doc(db(actor), `workspaces/${ws}/withdrawals/599`), withdrawalDoc(actor, 599, 100, `op-unauth-${actor}`));
    attempt.set(doc(db(actor), `workspaces/${ws}/cashOperations/op-unauth-${actor}`), cashOpDoc(actor, `op-unauth-${actor}`, 'WITHDRAWAL', 599, 'UPSERT', -100, 1, 1000));
    attempt.update(doc(db(actor), `workspaces/${ws}/financialState/current`), fsUpdateDoc(actor, `op-unauth-${actor}`, 'WITHDRAWAL', 599, 2, 900));
    await assertFails(attempt.commit());
  }
});
