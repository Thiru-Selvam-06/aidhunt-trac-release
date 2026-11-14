const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, runTransaction, serverTimestamp, Timestamp } = require('firebase/firestore');

if (process.env.GCLOUD_PROJECT !== 'demo-aidhunt-v16' ||
    process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:18180' ||
    process.env.GOOGLE_APPLICATION_CREDENTIALS) {
  throw Error('Local synthetic emulator only');
}

let env;
let dbCache = new Map();
const ws = 'ws_stage3_rules';
const now = Date.now() - 5000;
const db = uid => {
  if (!dbCache.has(uid)) dbCache.set(uid, env.authenticatedContext(uid).firestore());
  return dbCache.get(uid);
};
const envDoc = (actor, opId, type = 'UPSERT') => ({
  workspaceId: ws,
  protocolVersion: 2,
  operationType: type,
  dependencies: [],
  operationId: opId,
  actorUid: actor,
  actionTime: now,
  isDeleted: false
});

async function seedWorkspace({ confirmedCash = 2000, reservedCash = 0, confirmedBank = 1000, version = 1 } = {}) {
  dbCache.clear();
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async ctx => {
    const d = ctx.firestore();
    await setDoc(doc(d, `workspaces/${ws}`), {
      workspaceId: ws,
      ownerUid: 'owner',
      syncCapabilities: { atomicCashControlV1: true }
    });
    for (const [uid, role, status, name] of [
      ['owner', 'OWNER', 'ACTIVE', 'Owner'],
      ['partnerA', 'PARTNER', 'ACTIVE', 'Partner A'],
      ['partnerB', 'PARTNER', 'ACTIVE', 'Partner B'],
      ['operator', 'OPERATOR', 'ACTIVE', 'Operator']
    ]) {
      await setDoc(doc(d, `workspaces/${ws}/members/${uid}`), {
        workspaceId: ws, uid, role, status, displayName: name
      });
    }
    await setDoc(doc(d, `workspaces/${ws}/financialState/current`), {
      workspaceId: ws,
      version,
      confirmedCash,
      reservedCash,
      confirmedBank,
      baselineSource: 'SERVER_COMPUTED',
      lastOperationId: 'seed-baseline',
      lastEntityType: 'BASELINE',
      lastEntityId: 'current',
      lastActorUid: 'owner',
      actionTime: now - 1000,
      updatedAt: serverTimestamp()
    });
  });
}

function cashOpDoc(actor, opId, entityType, entityId, mutationType, cashDelta, expectedVersion, cashBefore, { reservedDelta = 0, reservedBefore = 0, bankDelta = 0, bankBefore = 1000 } = {}) {
  const docData = {
    workspaceId: ws,
    operationId: opId,
    entityType,
    entityId: String(entityId),
    mutationType,
    actorUid: actor,
    actionTime: now,
    cashDelta,
    reservedDelta,
    reservedCashAfter: reservedBefore + reservedDelta,
    expectedVersion,
    newVersion: expectedVersion + 1,
    confirmedCashAfter: cashBefore + cashDelta,
    createdAt: serverTimestamp()
  };
  if (entityType === 'EXTERNAL_LOAN' || entityType === 'EXTERNAL_LOAN_REPAYMENT') {
    docData.bankDelta = bankDelta;
    docData.confirmedBankAfter = bankBefore + bankDelta;
  }
  return docData;
}

function fsUpdate(actor, opId, entityType, entityId, newVersion, nextCash, nextReserved = 0, nextBank = 1000) {
  return {
    version: newVersion,
    confirmedCash: nextCash,
    reservedCash: nextReserved,
    confirmedBank: nextBank,
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

test('external loans allow Owner/Partner atomic cash/bank/direct-to-supplier, deny Operator and unreceipted bypass', async () => {
  await seedWorkspace({ confirmedCash: 500, confirmedBank: 300, version: 1 });

  await assertFails(setDoc(doc(db('operator'), `workspaces/${ws}/externalLoans/101`), {
    ...envDoc('operator', 'op-loan-op'),
    id: 101, lenderName: 'Co-op Bank', principalAmount: 1000, interestAmount: 100,
    totalRepaidPrincipal: 0, totalRepaidInterest: 0, destination: 'DIRECT_TO_SUPPLIER',
    linkedExpenseId: 0, status: 'ACTIVE', note: '', loanDateMillis: now, createdAt: now, updatedAt: now,
    createdByUid: 'operator', createdByRole: 'OPERATOR'
  }));

  // Old-client bypass without cashOp for BUSINESS_CASH must fail
  await assertFails(setDoc(doc(db('partnerA'), `workspaces/${ws}/externalLoans/102`), {
    ...envDoc('partnerA', 'op-loan-bypass'),
    id: 102, lenderName: 'Local Lender', principalAmount: 1000, interestAmount: 150,
    totalRepaidPrincipal: 0, totalRepaidInterest: 0, destination: 'BUSINESS_CASH',
    linkedExpenseId: 0, status: 'ACTIVE', note: '', loanDateMillis: now, createdAt: now, updatedAt: now,
    createdByUid: 'partnerA', createdByRole: 'PARTNER'
  }));

  // Valid BUSINESS_CASH loan updates confirmedCash atomically
  await assertSucceeds(runTransaction(db('partnerA'), async tx => {
    const fsRef = doc(db('partnerA'), `workspaces/${ws}/financialState/current`);
    const snap = await tx.get(fsRef);
    const d = snap.data();
    tx.set(doc(db('partnerA'), `workspaces/${ws}/externalLoans/103`), {
      ...envDoc('partnerA', 'op-loan-cash'),
      id: 103, lenderName: 'Local Lender', principalAmount: 1000, interestAmount: 150,
      totalRepaidPrincipal: 0, totalRepaidInterest: 0, destination: 'BUSINESS_CASH',
      linkedExpenseId: 0, status: 'ACTIVE', note: 'Cash loan', loanDateMillis: now, createdAt: now, updatedAt: now,
      createdByUid: 'partnerA', createdByRole: 'PARTNER'
    });
    tx.set(doc(db('partnerA'), `workspaces/${ws}/cashOperations/op-loan-cash`),
      cashOpDoc('partnerA', 'op-loan-cash', 'EXTERNAL_LOAN', 103, 'UPSERT', 1000, d.version, d.confirmedCash, { bankBefore: d.confirmedBank }));
    tx.update(fsRef, fsUpdate('partnerA', 'op-loan-cash', 'EXTERNAL_LOAN', 103, d.version + 1, d.confirmedCash + 1000, d.reservedCash, d.confirmedBank));
  }));

  // Cash repayment reduces confirmedCash and moves loan to SETTLED
  await assertSucceeds(runTransaction(db('owner'), async tx => {
    const fsRef = doc(db('owner'), `workspaces/${ws}/financialState/current`);
    const snap = await tx.get(fsRef);
    const d = snap.data();
    tx.set(doc(db('owner'), `workspaces/${ws}/externalLoanRepayments/201`), {
      ...envDoc('owner', 'op-loan-rep-1'),
      id: 201, loanId: 103, principalPaid: 1000, interestPaid: 150, source: 'BUSINESS_CASH',
      note: 'Full payoff', repaidAt: now, createdAt: now, createdByUid: 'owner', createdByRole: 'OWNER'
    });
    tx.update(doc(db('owner'), `workspaces/${ws}/externalLoans/103`), {
      totalRepaidPrincipal: 1000, totalRepaidInterest: 150, status: 'SETTLED',
      updatedAt: now, operationId: 'op-loan-rep-1', actorUid: 'owner', actionTime: now
    });
    tx.set(doc(db('owner'), `workspaces/${ws}/cashOperations/op-loan-rep-1`),
      cashOpDoc('owner', 'op-loan-rep-1', 'EXTERNAL_LOAN_REPAYMENT', 201, 'UPSERT', -1150, d.version, d.confirmedCash, { bankBefore: d.confirmedBank }));
    tx.update(fsRef, fsUpdate('owner', 'op-loan-rep-1', 'EXTERNAL_LOAN_REPAYMENT', 201, d.version + 1, d.confirmedCash - 1150, d.reservedCash, d.confirmedBank));
  }));
});

test('direct-to-supplier loan links 1:1 to a single Loan Funded expense without cash change and rejects double-linking or over-principal', async () => {
  await seedWorkspace({ confirmedCash: 800, version: 1 });
  await assertSucceeds(setDoc(doc(db('owner'), `workspaces/${ws}/externalLoans/301`), {
    ...envDoc('owner', 'op-dts-301'),
    id: 301, lenderName: 'Diesel Dealer', principalAmount: 5000, interestAmount: 250,
    totalRepaidPrincipal: 0, totalRepaidInterest: 0, destination: 'DIRECT_TO_SUPPLIER',
    linkedExpenseId: 0, status: 'ACTIVE', note: '', loanDateMillis: now, createdAt: now, updatedAt: now,
    createdByUid: 'owner', createdByRole: 'OWNER'
  }));

  // Over-principal expense must fail
  await assertFails(runTransaction(db('partnerA'), async tx => {
    tx.set(doc(db('partnerA'), `workspaces/${ws}/expenses/401`), {
      ...envDoc('partnerA', 'op-exp-over'),
      id: 401, category: 'Fuel / Diesel', amount: 5500, notes: '', paidBy: 'Loan Funded', paidByUid: '',
      fundingSource: 'EXTERNAL_LOAN', externalLoanId: 301, loanFundingMode: 'DIRECT_TO_SUPPLIER',
      expenseForUid: 'partnerA', financialAttributionUid: 'partnerA', createdByUid: 'partnerA', createdByRole: 'PARTNER',
      createdAt: now, updatedAt: now, dateTimestamp: now, relatedJobId: 0,
      authorizationTimeStatus: 'CLIENT_ORIGINAL_V2', authorizationCreatedAt: Timestamp.fromMillis(now), serverReceivedAt: serverTimestamp()
    });
    tx.update(doc(db('partnerA'), `workspaces/${ws}/externalLoans/301`), {
      linkedExpenseId: 401, updatedAt: now, operationId: 'op-exp-over', actorUid: 'partnerA', actionTime: now
    });
  }));

  // Valid 1:1 link succeeds
  await assertSucceeds(runTransaction(db('partnerA'), async tx => {
    tx.set(doc(db('partnerA'), `workspaces/${ws}/expenses/402`), {
      ...envDoc('partnerA', 'op-exp-link'),
      id: 402, category: 'Fuel / Diesel', amount: 4500, notes: '', paidBy: 'Loan Funded', paidByUid: '',
      fundingSource: 'EXTERNAL_LOAN', externalLoanId: 301, loanFundingMode: 'DIRECT_TO_SUPPLIER',
      expenseForUid: 'partnerA', financialAttributionUid: 'partnerA', createdByUid: 'partnerA', createdByRole: 'PARTNER',
      createdAt: now, updatedAt: now, dateTimestamp: now, relatedJobId: 0,
      authorizationTimeStatus: 'CLIENT_ORIGINAL_V2', authorizationCreatedAt: Timestamp.fromMillis(now), serverReceivedAt: serverTimestamp()
    });
    tx.update(doc(db('partnerA'), `workspaces/${ws}/externalLoans/301`), {
      linkedExpenseId: 402, updatedAt: now, operationId: 'op-exp-link', actorUid: 'partnerA', actionTime: now
    });
  }));

  // Second expense linking same loan must fail
  await assertFails(runTransaction(db('owner'), async tx => {
    tx.set(doc(db('owner'), `workspaces/${ws}/expenses/403`), {
      ...envDoc('owner', 'op-exp-dup'),
      id: 403, category: 'Fuel / Diesel', amount: 100, notes: '', paidBy: 'Loan Funded', paidByUid: '',
      fundingSource: 'EXTERNAL_LOAN', externalLoanId: 301, loanFundingMode: 'DIRECT_TO_SUPPLIER',
      expenseForUid: 'owner', financialAttributionUid: 'owner', createdByUid: 'owner', createdByRole: 'OWNER',
      createdAt: now, updatedAt: now, dateTimestamp: now, relatedJobId: 0,
      authorizationTimeStatus: 'CLIENT_ORIGINAL_V2', authorizationCreatedAt: Timestamp.fromMillis(now), serverReceivedAt: serverTimestamp()
    });
    tx.update(doc(db('owner'), `workspaces/${ws}/externalLoans/301`), {
      linkedExpenseId: 403, updatedAt: now, operationId: 'op-exp-dup', actorUid: 'owner', actionTime: now
    });
  }));
});

test('named-partner borrowing enforces lender-only approval, reserves cash against concurrent withdrawal, and settles via recipient confirmation', async () => {
  await seedWorkspace({ confirmedCash: 1000, reservedCash: 0, version: 1 });

  await assertSucceeds(setDoc(doc(db('partnerA'), `workspaces/${ws}/borrowingRequests/501`), {
    ...envDoc('partnerA', 'op-borrow-create'),
    id: 501, borrowerUid: 'partnerA', borrowerName: 'Partner A',
    lendingPartnerUid: 'partnerB', lendingPartnerName: 'Partner B',
    sourceType: 'NAMED_PARTNER', amount: 700, note: 'Need urgent cash',
    status: 'PENDING_APPROVAL', reservedAmount: 0, withdrawalId: 0, obligationId: 0,
    requestedByUid: 'partnerA', requestedByRole: 'PARTNER', createdAt: now, updatedAt: now
  }));

  // Owner or Borrower CANNOT approve Partner B's lending request
  await assertFails(runTransaction(db('owner'), async tx => {
    const fsRef = doc(db('owner'), `workspaces/${ws}/financialState/current`);
    const d = (await tx.get(fsRef)).data();
    tx.update(doc(db('owner'), `workspaces/${ws}/borrowingRequests/501`), {
      ...envDoc('owner', 'op-illegal-approve'), status: 'APPROVED_RESERVED', reservedAmount: 700, approvedByUid: 'owner', approvedAt: now, updatedAt: now
    });
    tx.set(doc(db('owner'), `workspaces/${ws}/cashOperations/op-illegal-approve`),
      cashOpDoc('owner', 'op-illegal-approve', 'BORROWING_REQUEST', 501, 'APPROVE_RESERVE', 0, d.version, d.confirmedCash, { reservedDelta: 700 }));
    tx.update(fsRef, fsUpdate('owner', 'op-illegal-approve', 'BORROWING_REQUEST', 501, d.version + 1, d.confirmedCash, 700));
  }));

  // Partner B approves & reserves Rs 700
  await assertSucceeds(runTransaction(db('partnerB'), async tx => {
    const fsRef = doc(db('partnerB'), `workspaces/${ws}/financialState/current`);
    const d = (await tx.get(fsRef)).data();
    tx.update(doc(db('partnerB'), `workspaces/${ws}/borrowingRequests/501`), {
      ...envDoc('partnerB', 'op-approve-501'), status: 'APPROVED_RESERVED', reservedAmount: 700, approvedByUid: 'partnerB', approvedAt: now, updatedAt: now
    });
    tx.set(doc(db('partnerB'), `workspaces/${ws}/cashOperations/op-approve-501`),
      cashOpDoc('partnerB', 'op-approve-501', 'BORROWING_REQUEST', 501, 'APPROVE_RESERVE', 0, d.version, d.confirmedCash, { reservedDelta: 700 }));
    tx.update(fsRef, fsUpdate('partnerB', 'op-approve-501', 'BORROWING_REQUEST', 501, d.version + 1, d.confirmedCash, 700));
  }));

  // Concurrent withdrawal of Rs 500 by Owner exceeds unreserved Rs 300 -> MUST FAIL
  await assertFails(runTransaction(db('owner'), async tx => {
    const fsRef = doc(db('owner'), `workspaces/${ws}/financialState/current`);
    const d = (await tx.get(fsRef)).data();
    tx.set(doc(db('owner'), `workspaces/${ws}/withdrawals/601`), {
      ...envDoc('owner', 'op-w-601'),
      id: 601, amount: 500, partnerName: 'Owner', targetPartnerUid: 'owner', createdByUid: 'owner', createdByRole: 'OWNER',
      createdAt: now, dateTimestamp: now, notes: '', authorizationTimeStatus: 'CLIENT_ORIGINAL_V2',
      authorizationCreatedAt: Timestamp.fromMillis(now), serverReceivedAt: serverTimestamp()
    });
    tx.set(doc(db('owner'), `workspaces/${ws}/cashOperations/op-w-601`),
      cashOpDoc('owner', 'op-w-601', 'WITHDRAWAL', 601, 'UPSERT', -500, d.version, d.confirmedCash, { reservedBefore: d.reservedCash }));
    tx.update(fsRef, fsUpdate('owner', 'op-w-601', 'WITHDRAWAL', 601, d.version + 1, d.confirmedCash - 500, d.reservedCash));
  }));

  // Partner A executes approved borrow -> releases reservation, deducts Rs 700 cash, creates obligation
  await assertSucceeds(runTransaction(db('partnerA'), async tx => {
    const fsRef = doc(db('partnerA'), `workspaces/${ws}/financialState/current`);
    const d = (await tx.get(fsRef)).data();
    tx.update(doc(db('partnerA'), `workspaces/${ws}/borrowingRequests/501`), {
      ...envDoc('partnerA', 'op-exec-501'), status: 'EXECUTED', reservedAmount: 0, withdrawalId: 701, obligationId: 801, executedAt: now, updatedAt: now
    });
    tx.set(doc(db('partnerA'), `workspaces/${ws}/withdrawals/701`), {
      ...envDoc('partnerA', 'op-exec-501'),
      id: 701, amount: 700, partnerName: 'Partner A', targetPartnerUid: 'partnerA', createdByUid: 'partnerA', createdByRole: 'PARTNER',
      category: 'Borrow from Partner', borrowingRequestId: 501,
      createdAt: now, dateTimestamp: now, notes: '', authorizationTimeStatus: 'CLIENT_ORIGINAL_V2',
      authorizationCreatedAt: Timestamp.fromMillis(now), serverReceivedAt: serverTimestamp()
    });
    tx.set(doc(db('partnerA'), `workspaces/${ws}/settlementObligations/801`), {
      ...envDoc('partnerA', 'op-exec-501'),
      id: 801, borrowerUid: 'partnerA', borrowerName: 'Partner A', creditorType: 'PARTNER', creditorUid: 'partnerB',
      creditorName: 'Partner B', sourceRequestId: 501, principalAmount: 700, repaidAmount: 0, directSettledAmount: 0,
      status: 'OPEN', note: '', createdAt: now, updatedAt: now, createdByUid: 'partnerA'
    });
    tx.set(doc(db('partnerA'), `workspaces/${ws}/cashOperations/op-exec-501`),
      cashOpDoc('partnerA', 'op-exec-501', 'BORROWING_REQUEST', 501, 'EXECUTE', -700, d.version, d.confirmedCash, { reservedDelta: -700, reservedBefore: d.reservedCash }));
    tx.update(fsRef, fsUpdate('partnerA', 'op-exec-501', 'BORROWING_REQUEST', 501, d.version + 1, d.confirmedCash - 700, 0));
  }));

  // Partner A records DIRECT_TO_PARTNER repayment; Owner CANNOT confirm it (only Partner B!)
  await assertSucceeds(setDoc(doc(db('partnerA'), `workspaces/${ws}/settlementRepayments/901`), {
    ...envDoc('partnerA', 'op-set-rep-901'),
    id: 901, obligationId: 801, payerUid: 'partnerA', payerName: 'Partner A', creditorType: 'PARTNER',
    creditorUid: 'partnerB', creditorName: 'Partner B', amount: 700, mode: 'DIRECT_TO_PARTNER',
    status: 'PENDING_CONFIRMATION', note: '', paidAt: now, createdAt: now, createdByUid: 'partnerA'
  }));
  await assertFails(runTransaction(db('owner'), async tx => {
    tx.update(doc(db('owner'), `workspaces/${ws}/settlementRepayments/901`), {
      ...envDoc('owner', 'op-illegal-conf'), status: 'CONFIRMED', confirmedByUid: 'owner', confirmedAt: now
    });
    tx.update(doc(db('owner'), `workspaces/${ws}/settlementObligations/801`), {
      ...envDoc('owner', 'op-illegal-conf'), repaidAmount: 700, directSettledAmount: 700, status: 'SETTLED', updatedAt: now
    });
  }));

  // Partner B confirms DIRECT_TO_PARTNER repayment; confirmedCash stays 300
  await assertSucceeds(runTransaction(db('partnerB'), async tx => {
    tx.update(doc(db('partnerB'), `workspaces/${ws}/settlementRepayments/901`), {
      ...envDoc('partnerB', 'op-conf-901'), status: 'CONFIRMED', confirmedByUid: 'partnerB', confirmedAt: now
    });
    tx.update(doc(db('partnerB'), `workspaces/${ws}/settlementObligations/801`), {
      ...envDoc('partnerB', 'op-conf-901'), repaidAmount: 700, directSettledAmount: 700, status: 'SETTLED', updatedAt: now
    });
  }));

  const stateSnap = await getDoc(doc(db('owner'), `workspaces/${ws}/financialState/current`));
  assert.equal(stateSnap.data().confirmedCash, 300);
  assert.equal(stateSnap.data().reservedCash, 0);
});
