const test = require('node:test'), assert = require('node:assert/strict');
const fs = require('node:fs'), path = require('node:path');
const {initializeTestEnvironment, assertSucceeds, assertFails} = require('@firebase/rules-unit-testing');
const {doc, setDoc, getDoc, deleteDoc, updateDoc, writeBatch, disableNetwork, enableNetwork, getDocFromCache} = require('firebase/firestore');
if (process.env.GCLOUD_PROJECT !== 'demo-aidhunt-v16' || process.env.FIRESTORE_EMULATOR_HOST !== '127.0.0.1:18180' || process.env.GOOGLE_APPLICATION_CREDENTIALS)
  throw Error('REFUSED: isolated loopback demo emulator without credentials required');
let env, source, time;
const ws = 'ws_phase53', id = '1', customer = '2';
const p = (col, key = id, space = ws) => `workspaces/${space}/${col}/${key}`;
const clients = new Map();
const db = uid => { if(!clients.has(uid)) clients.set(uid,env.authenticatedContext(uid).firestore()); return clients.get(uid); };
const seed = async (col, data, key = id) => env.withSecurityRulesDisabled(c => setDoc(doc(c.firestore(), p(col,key)), data));
const envelope = actor => ({workspaceId:ws, protocolVersion:2, operationType:'UPSERT', actorUid:actor,
  operationId:'original-delete', actionTime:time, dependencies:[], isDeleted:false});
test.before(async () => { env = await initializeTestEnvironment({projectId:'demo-aidhunt-v16', firestore:{host:'127.0.0.1',port:18180,
  rules:fs.readFileSync(path.join(__dirname,'../firestore.phase53.rules'),'utf8')}}); });
test.after(async () => env?.cleanup());
test.beforeEach(async () => {
  await env.clearFirestore(); time = Date.now()-5000;
  await env.withSecurityRulesDisabled(async c => {
    const d=c.firestore(); await setDoc(doc(d,`workspaces/${ws}`),{workspaceId:ws,ownerUid:'owner'});
    for (const [uid,role,status] of [['owner','OWNER','ACTIVE'],['perumal','PARTNER','ACTIVE'],['sriram','PARTNER','ACTIVE'],['operator','OPERATOR','ACTIVE'],['removed','PARTNER','REMOVED']])
      await setDoc(doc(d,p('members',uid)),{uid,role,status});
  });
  source={...envelope('sriram'),id:1,customerId:2,createdByUid:'sriram',entryForUid:'sriram',financialAttributionUid:'sriram',
    createdAt:time-365*86400000,isHistorical:false,authorizationTimeStatus:'CLIENT_ORIGINAL_V2',totalAmount:100.25,amountReceived:0,pendingAmount:100.25,
    jobEditHistory:[{operationId:'original-edit',actorUid:'sriram',actionTime:time-1000,before:{notes:'before'},after:{notes:'after'}}]};
  await seed('entries',source);
  await seed('customers',{...envelope('owner'),id:2,createdByUid:'owner',createdAt:time-365*86400000,
    totalBilled:100.25,totalPaid:0,balanceDue:100.25},customer);
});
const proof = (actor, extra={}) => ({workspaceId:ws,recordId:id,actorUid:actor,operationId:'original-delete',actionTime:time,protocolVersion:2,
  sourceCreatedAt:source.createdAt,customerId:2,sourceCreatorUid:source.createdByUid,sourceBeneficiaryUid:source.entryForUid,
  totalAmount:source.totalAmount,amountReceived:source.amountReceived,pendingAmount:source.pendingAmount,
  collectionIds:[],deletedJobEditHistory:source.jobEditHistory,...extra});
function guards(actor) {
  const d=db(actor), b=writeBatch(d);
  for(const [kind,key] of [['ENTRY',id],['CUSTOMER',customer]]) b.set(doc(d,p('ledgerDeletionLocks',kind+'_'+key)),{
    workspaceId:ws,recordId:key,kind,actorUid:actor,operationId:'original-delete',actionTime:time,jobId:id,
    sourceCreatedAt:source.createdAt});
  return b.commit();
}
function removal(actor,extra={},children=[]) {
  const d=db(actor), b=writeBatch(d);
  b.delete(doc(d,p('entries'))); b.set(doc(d,p('entryDeletionProofs')),proof(actor,extra));
  b.update(doc(d,p('customers',customer)),{...envelope(actor),totalBilled:0,totalPaid:0,balanceDue:0});
  for(const [col,key] of children) b.delete(doc(d,p(col,key)));
  b.delete(doc(d,p('ledgerDeletionLocks','ENTRY_'+id))); b.delete(doc(d,p('ledgerDeletionLocks','CUSTOMER_'+customer)));
  return b.commit();
}
for (const [actor,creator,old] of [['owner','sriram',false],['owner','perumal',true],['perumal','sriram',false],['sriram','perumal',true]])
  test(`${actor} guards and atomically deletes year-old ${old?'Old':'New'} Entry by ${creator}`, async () => {
    source={...source,createdByUid:creator,isHistorical:old}; await seed('entries',source);
    await assertSucceeds(guards(actor)); await assertSucceeds(removal(actor));
    assert.equal((await getDoc(doc(db('owner'),p('entries')))).exists(),false);
    const saved=(await getDoc(doc(db('owner'),p('entryDeletionProofs')))).data();
    assert.deepEqual(saved,proof(actor)); assert.equal(saved.operationId,'original-delete'); assert.equal(saved.actionTime,time);
    assert.equal((await getDoc(doc(db('owner'),p('customers',customer)))).data().balanceDue,0);
    await assertFails(updateDoc(doc(db(actor),p('entryDeletionProofs')),{actorUid:'other'}));
    await assertFails(deleteDoc(doc(db(actor),p('entryDeletionProofs'))));
  });
for(const actor of ['removed','operator','outsider']) test(`${actor} cannot acquire guards or forge a deletion proof`,async()=>{
  await assertFails(guards(actor)); await assertFails(removal(actor));
  assert.equal((await getDoc(doc(db('owner'),p('entries')))).exists(),true);
});
test('foreign workspace and forged original Owner claim cannot delete',async()=>{
  const d=env.authenticatedContext('outsider',{role:'OWNER',workspaceId:ws}).firestore();
  await assertFails(deleteDoc(doc(d,p('entries'))));
  await assertFails(deleteDoc(doc(db('perumal'),p('entries',id,'ws_other'))));
});
test('missing source and mismatched source proof fail closed',async()=>{
  await assertSucceeds(guards('perumal'));
  await assertFails(removal('perumal',{totalAmount:100}));
  await assertFails(removal('perumal',{deletedJobEditHistory:[]}));
  await env.withSecurityRulesDisabled(c=>deleteDoc(doc(c.firestore(),p('entries'))));
  await assertFails(removal('perumal'));
});
for (const reason of ['unverified','inactive-target','future-action']) test('Partner cannot delete '+reason,async()=>{
  if(reason==='unverified'){source={...source,authorizationTimeStatus:'UNKNOWN'};await seed('entries',source);}
  if(reason==='inactive-target')await seed('members',{uid:'sriram',role:'PARTNER',status:'REMOVED'},'sriram');
  if(reason==='future-action') time=Date.now()+86400000;
  if(reason==='inactive-target'){await assertSucceeds(guards('perumal'));await assertFails(removal('perumal'));}
  else await assertFails(guards('perumal'));
});
test('linked other-collector receipt blocks the entire financial deletion',async()=>{
  source={...source,amountReceived:40,pendingAmount:60.25};await seed('entries',source);
  await seed('collections',{workspaceId:ws,id:3,jobEntryId:1,customerId:2,amount:40,collectedByUid:'sriram',status:'ACTIVE',
    createdAt:time-1000,authorizationTimeStatus:'CLIENT_ORIGINAL_V2',allocationsJson:'[{"jobId":1,"amount":40}]'},'3');
  await assertSucceeds(guards('perumal'));await assertFails(removal('perumal',{collectionIds:['3']},[['collections','3']]));
  assert.equal((await getDoc(doc(db('owner'),p('entries')))).data().amountReceived,40);
  assert.equal((await getDoc(doc(db('owner'),p('collections','3')))).data().amount,40);
});
test('linked other-creator expense remains protected',async()=>{
  await seed('expenses',{workspaceId:ws,id:4,relatedJobId:1,createdByUid:'sriram',createdAt:time-1000,
    authorizationTimeStatus:'CLIENT_ORIGINAL_V2',expenseForUid:'sriram',amount:10},'4');
  await assertSucceeds(guards('perumal'));await assertFails(removal('perumal',{},[['expenses','4']]));
  assert.equal((await getDoc(doc(db('owner'),p('expenses','4')))).data().amount,10);
  assert.equal((await getDoc(doc(db('owner'),p('entries')))).exists(),true);
});
test('unpaid year-old job queued offline confirms once after reconnection',async()=>{
  await assertSucceeds(guards('perumal'));const d=db('perumal');await disableNetwork(d);
  let settled=false;const pending=removal('perumal').then(()=>{settled=true;},error=>{throw error;});
  try {
    for(let n=0;n<20;n++) {const local=await getDocFromCache(doc(d,p('entries'))).catch(()=>null);if(local?.metadata.hasPendingWrites)break;await new Promise(r=>setTimeout(r,20));}
    assert.equal(settled,false);
    assert.equal((await getDoc(doc(db('owner'),p('entries')))).exists(),true);
  } finally {await enableNetwork(d);}
  await pending;
  assert.equal((await getDoc(doc(db('owner'),p('entries')))).exists(),false);
  const audit=(await getDoc(doc(db('owner'),p('entryDeletionProofs')))).data();
  await assertFails(removal('perumal'));assert.deepEqual((await getDoc(doc(db('owner'),p('entryDeletionProofs')))).data(),audit);
});
