const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, getDocs, collection, query, where, updateDoc, deleteDoc } = require('firebase/firestore');

if (process.env.GCLOUD_PROJECT !== 'demo-aidhunt-v16' ||
    process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:18180' ||
    process.env.GOOGLE_APPLICATION_CREDENTIALS) {
  throw Error('Local synthetic emulator only');
}

let env;
let dbCache = new Map();
const ws = 'ws_stage4_notif_rules';
const now = Date.now() - 5000;
const db = uid => {
  if (!dbCache.has(uid)) dbCache.set(uid, env.authenticatedContext(uid).firestore());
  return dbCache.get(uid);
};

async function seedWorkspace() {
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
  });
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

test('notifications rules enforce recipient isolation, anti-self-notification, read-only updates, and delete denial', async () => {
  await seedWorkspace();

  const notifOwner = {
    id: 'op-job-1_OWNER_JOB_CREATED',
    workspaceId: ws,
    operationId: 'op-job-1',
    recipientUid: 'owner',
    targetAudience: 'OWNER_ONLY',
    actorUid: 'partnerA',
    actorName: 'Partner A',
    eventType: 'JOB_CREATED',
    entityType: 'JOB',
    entityId: 101,
    amount: 1800,
    counterpartyName: 'Farmer Ravi',
    metadataJson: '{}',
    createdAt: now,
    isRead: false,
    readAt: 0
  };
  await assertSucceeds(setDoc(doc(db('partnerA'), `workspaces/${ws}/notifications/${notifOwner.id}`), notifOwner));

  // Self-notification (actor == recipient) is blocked
  await assertFails(setDoc(doc(db('owner'), `workspaces/${ws}/notifications/self-notif`), {
    ...notifOwner,
    id: 'self-notif',
    actorUid: 'owner',
    recipientUid: 'owner'
  }));

  // Operator cannot create or read notifications
  await assertFails(setDoc(doc(db('operator'), `workspaces/${ws}/notifications/op-notif`), {
    ...notifOwner,
    id: 'op-notif',
    actorUid: 'operator'
  }));
  await assertFails(getDoc(doc(db('operator'), `workspaces/${ws}/notifications/${notifOwner.id}`)));

  // Partner B cannot read Owner's notification
  await assertFails(getDoc(doc(db('partnerB'), `workspaces/${ws}/notifications/${notifOwner.id}`)));

  // Partner A creates targeted notification for Partner B
  const notifB = {
    ...notifOwner,
    id: 'op-borr-2_PARTNER_partnerB_BORROWING_REQUESTED',
    operationId: 'op-borr-2',
    recipientUid: 'partnerB',
    targetAudience: 'PARTNER_TARGETED',
    eventType: 'BORROWING_REQUESTED',
    entityType: 'BORROWING_REQUEST',
    entityId: 202,
    amount: 700
  };
  await assertSucceeds(setDoc(doc(db('partnerA'), `workspaces/${ws}/notifications/${notifB.id}`), notifB));

  // Partner B can query only recipientUid == partnerB; Partner A cannot read Partner B's targeted alert
  const partnerBSnap = await assertSucceeds(
    getDocs(query(collection(db('partnerB'), `workspaces/${ws}/notifications`), where('recipientUid', '==', 'partnerB')))
  );
  assert.equal(partnerBSnap.size, 1);
  await assertFails(getDoc(doc(db('partnerA'), `workspaces/${ws}/notifications/${notifB.id}`)));

  // Owner reads all notifications cleanly
  const ownerSnap = await assertSucceeds(getDocs(collection(db('owner'), `workspaces/${ws}/notifications`)));
  assert.equal(ownerSnap.size, 2);

  // Only recipient (Partner B) or Owner can mark read; actor cannot overwrite prose/amounts or delete
  await assertFails(updateDoc(doc(db('partnerB'), `workspaces/${ws}/notifications/${notifB.id}`), {
    amount: 9999,
    isRead: true,
    readAt: now + 100
  }));
  await assertFails(updateDoc(doc(db('partnerA'), `workspaces/${ws}/notifications/${notifB.id}`), {
    isRead: true,
    readAt: now + 100
  }));
  await assertSucceeds(updateDoc(doc(db('partnerB'), `workspaces/${ws}/notifications/${notifB.id}`), {
    isRead: true,
    readAt: now + 100
  }));
  await assertSucceeds(updateDoc(doc(db('owner'), `workspaces/${ws}/notifications/${notifOwner.id}`), {
    isRead: true,
    readAt: now + 150
  }));

  // Deletes are always denied
  await assertFails(deleteDoc(doc(db('owner'), `workspaces/${ws}/notifications/${notifOwner.id}`)));
});

test('auditEvents collection enforces member create, owner-only read, and strict immutability', async () => {
  await seedWorkspace();

  const evt = {
    eventId: 'evt-1',
    actorUid: 'partnerA',
    actorName: 'Partner A',
    actorRole: 'PARTNER',
    primaryOwnerUid: 'owner',
    workspaceId: ws,
    action: 'CREATE',
    entityType: 'JOB',
    recordId: 501,
    humanReadableReference: 'Job #501',
    timestamp: now,
    source: 'MOBILE_APP'
  };

  await assertSucceeds(setDoc(doc(db('partnerA'), `workspaces/${ws}/auditEvents/evt-1`), evt));
  // Forged actorUid blocked
  await assertFails(setDoc(doc(db('partnerA'), `workspaces/${ws}/auditEvents/evt-forged`), {
    ...evt,
    eventId: 'evt-forged',
    actorUid: 'owner'
  }));
  // Partner & Operator cannot read auditEvents; Owner can
  await assertFails(getDoc(doc(db('partnerA'), `workspaces/${ws}/auditEvents/evt-1`)));
  await assertFails(getDoc(doc(db('operator'), `workspaces/${ws}/auditEvents/evt-1`)));
  const ownerRead = await assertSucceeds(getDoc(doc(db('owner'), `workspaces/${ws}/auditEvents/evt-1`)));
  assert.equal(ownerRead.data().recordId, 501);

  // Immutable: update & delete denied even for Owner
  await assertFails(updateDoc(doc(db('owner'), `workspaces/${ws}/auditEvents/evt-1`), { action: 'DELETE' }));
  await assertFails(deleteDoc(doc(db('owner'), `workspaces/${ws}/auditEvents/evt-1`)));
});
