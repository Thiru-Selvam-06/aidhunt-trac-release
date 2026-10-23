// Synthetic fixtures only. These tests deliberately retain the current authorization policy.
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const {initializeTestEnvironment, assertSucceeds, assertFails} = require('@firebase/rules-unit-testing');
const {doc, setDoc, updateDoc, getDoc, writeBatch, Timestamp, serverTimestamp} = require('firebase/firestore');
const ws='customer-editing', time=Date.now()-10000;
let env;
const db = actor => env.authenticatedContext(actor).firestore();
const ref = (database, kind, id) => doc(database,`workspaces/${ws}/${kind}/${id}`);
const command = (actor, operationId) => ({workspaceId:ws,actorUid:actor,operationId,
  actionTime:time+1000,protocolVersion:2,operationType:'UPSERT',dependencies:[],isDeleted:false});
const customer = id => ({...command('owner',`create-${id}`),id,name:'saga jothi',phone:'9876543210',location:'Village',
  createdByUid:'owner',createdByRole:'OWNER',createdAt:time,authorizationCreatedAt:Timestamp.fromMillis(time),
  serverReceivedAt:Timestamp.fromMillis(time),authorizationTimeStatus:'VERIFIED',totalBilled:2300,totalPaid:0,balanceDue:2300});
test.before(async()=> {env=await initializeTestEnvironment({projectId:'demo-aidhunt-v16',firestore:{host:'127.0.0.1',port:18180,
  rules:fs.readFileSync(process.env.CUSTOMER_EDIT_RULES_FILE || path.join(__dirname,'../firestore.rules'),'utf8')}});});
test.after(async()=>env?.cleanup());
test.beforeEach(async()=>{await env.clearFirestore();await env.withSecurityRulesDisabled(async context=>{
  const database=context.firestore();await setDoc(doc(database,`workspaces/${ws}`),{workspaceId:ws,ownerUid:'owner'});
  for(const [uid,role] of [['owner','OWNER'],['partner','PARTNER'],['operator','OPERATOR']])
    await setDoc(ref(database,'members',uid),{workspaceId:ws,uid,role,status:'ACTIVE'});
  await setDoc(ref(database,'customers','2'),customer(2));await setDoc(ref(database,'customers','3'),customer(3));
});});
for(const actor of ['owner','partner']) {
  test(`${actor}: old full Room replacement loses protected provenance and is denied`,async()=>{
    const oldRoomPayload={...customer(2),...command(actor,`old-replacement-${actor}`),name:'Saga Jothi'};
    for(const key of ['createdByRole','authorizationCreatedAt','serverReceivedAt','authorizationTimeStatus']) delete oldRoomPayload[key];
    await assertFails(setDoc(ref(db(actor),'customers','2'),oldRoomPayload));
    assert.equal((await getDoc(ref(db('owner'),'customers','2'))).data().name,'saga jothi');
  });
  test(`${actor}: metadata patch preserves creation, totals and independent profile changes`,async()=>{
    const database=db(actor), target=ref(database,'customers','2');
    await assertSucceeds(updateDoc(target,{...command(actor,`rename-${actor}`),name:'Saga Jothi'}));
    await assertSucceeds(updateDoc(target,{...command(actor,`phone-${actor}`),phone:'9123456780'}));
    const confirmed=(await getDoc(target)).data();
    assert.equal(confirmed.name,'Saga Jothi');assert.equal(confirmed.phone,'9123456780');
    for(const key of ['createdByUid','createdByRole','createdAt','totalBilled','totalPaid','balanceDue'])
      assert.equal(confirmed[key],customer(2)[key],key);
    for(const key of ['authorizationCreatedAt','serverReceivedAt']) assert.ok(confirmed[key].isEqual(customer(2)[key]),key);
    assert.equal(confirmed.operationId,`phone-${actor}`);
  });
}
for(const actor of ['operator','outsider']) test(`${actor}: profile patch remains denied`,async()=>
  assertFails(updateDoc(ref(db(actor),'customers','2'),{...command(actor,'forbidden'),name:'Changed'})));
test('foreign workspace and forged actor remain denied',async()=>{
  await assertFails(updateDoc(ref(db('partner'),'customers','2'),{...command('partner','foreign'),workspaceId:'foreign',name:'Changed'}));
  await assertFails(updateDoc(ref(db('partner'),'customers','2'),{...command('owner','forged'),name:'Changed'}));
});
test('single-source receipt retains history through reassignment and atomic reversal',async()=>{
  const database=db('owner'), job=ref(database,'entries','8'), receipt=ref(database,'collections','9');
  const original={...command('owner','job-create'),id:8,customerId:2,createdByUid:'owner',createdByRole:'OWNER',
    createdAt:time,authorizationCreatedAt:Timestamp.fromMillis(time),serverReceivedAt:serverTimestamp(),
    authorizationTimeStatus:'CLIENT_ORIGINAL_V2',isHistorical:false,entryForUid:'owner',financialAttributionUid:'owner',
    totalAmount:1000,amountReceived:0,pendingAmount:1000};
  await assertSucceeds(setDoc(job,original));
  async function move(id,operationId) {
    const before=(await getDoc(job)).data();
    const audit={fromCustomerId:before.customerId,toCustomerId:id,actorUid:'owner',operationId,
      actionTime:time+1000,totalAmount:before.totalAmount,amountReceived:before.amountReceived,pendingAmount:before.pendingAmount};
    await assertSucceeds(updateDoc(job,{...command('owner',operationId),customerId:id,
      ...(process.env.CUSTOMER_EDIT_EXPECT_LEGACY_DENIAL==='1'?{}:{customerReassignmentHistory:[...(before.customerReassignmentHistory||[]),audit]})}));
  }
  await move(3,'unpaid-move');await move(2,'unpaid-return');
  const payment={...command('owner','collect'),id:9,jobEntryId:8,customerId:2,amount:200,status:'ACTIVE',
    collectedByUid:'owner',collectedByName:'Owner',collectedByRole:'OWNER',collectedAt:time,createdAt:time,
    authorizationCreatedAt:Timestamp.fromMillis(time),serverReceivedAt:serverTimestamp(),
    authorizationTimeStatus:'CLIENT_ORIGINAL_V2',allocationsJson:'[{"jobId":8,"amount":200}]'};
  const collect=writeBatch(database);collect.set(receipt,payment);
  collect.update(job,{...command('owner','collect'),collectionId:'9',amountReceived:200,pendingAmount:800});
  await assertSucceeds(collect.commit());
  await move(3,'paid-move');
  const reverse=writeBatch(database);reverse.update(receipt,{...command('owner','reverse'),status:'REVERSED',reversedByUid:'owner',reversedAt:time+1000});
  reverse.update(job,{...command('owner','reverse'),collectionId:'9',amountReceived:0,pendingAmount:1000});
  const legacy=process.env.CUSTOMER_EDIT_EXPECT_LEGACY_DENIAL==='1';
  await (legacy?assertFails(reverse.commit()):assertSucceeds(reverse.commit()));
  assert.equal((await getDoc(receipt)).data().amount,200);
  assert.equal((await getDoc(receipt)).data().customerId,2);
  assert.equal((await getDoc(job)).data().amountReceived,legacy?200:0);
  if(!legacy) {
    const confirmed=(await getDoc(job)).data();assert.equal(confirmed.customerReassignmentHistory.length,3);
    await assertFails(updateDoc(job,{...command('owner','erase-audit'),customerReassignmentHistory:[]}));
    await assertFails(updateDoc(receipt,{...command('owner','rewrite-receipt'),customerId:3}));
  }
});
