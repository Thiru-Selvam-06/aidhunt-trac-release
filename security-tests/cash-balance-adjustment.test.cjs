const test=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,updateDoc,deleteDoc,writeBatch,serverTimestamp}=require('firebase/firestore');
if(process.env.GCLOUD_PROJECT!=='demo-aidhunt-v16'||process.env.FIRESTORE_EMULATOR_HOST!=='127.0.0.1:18180'||process.env.GOOGLE_APPLICATION_CREDENTIALS)
 throw Error('Synthetic local emulator only');
let env;const ws='ws_cash_correction', p=(c,id='current',w=ws)=>`workspaces/${w}/${c}/${id}`;
const db=uid=>env.authenticatedContext(uid).firestore();
const full=(process.env.BALANCE_RULES||'firestore.phase53.rules')==='firestore.rules';
test.before(async()=>{env=await initializeTestEnvironment({projectId:'demo-aidhunt-v16',firestore:{host:'127.0.0.1',port:18180,
 rules:fs.readFileSync(path.join(__dirname,'..',process.env.BALANCE_RULES||'firestore.phase53.rules'),'utf8')}})});
test.after(()=>env.cleanup());
async function seed({enabled=true,atomic=false,baseline=false}={}){
 await env.clearFirestore();await env.withSecurityRulesDisabled(async ctx=>{
  const d=ctx.firestore();await setDoc(doc(d,`workspaces/${ws}`),{workspaceId:ws,ownerUid:'owner',syncCapabilities:{cashBalanceAdjustmentsV1:enabled,atomicCashControlV1:atomic}});
  for(const [uid,role,status] of [['owner','OWNER','ACTIVE'],['partner','PARTNER','ACTIVE'],['operator','OPERATOR','ACTIVE'],['removed','PARTNER','REMOVED'],['impostor','OWNER','ACTIVE']])
   await setDoc(doc(d,p('members',uid)),{uid,role,status,displayName:uid});
  await setDoc(doc(d,p('entries','123')),{workspaceId:ws,totalAmount:2000,amountReceived:1000,pendingAmount:1000,customerName:'Unchanged synthetic customer'});
  if(baseline)await setDoc(doc(d,p('financialState')),{workspaceId:ws,version:1,confirmedCash:1000,reservedCash:0,confirmedBank:0,protectedEntitlements:0,baselineSource:'synthetic reviewed baseline'});
 });
}
test.beforeEach(()=>seed());
function pair(actor='partner',extra={},historyExtra={}){
 const current={workspaceId:ws,revision:1,totalAdjustmentPaise:50000,operationId:'cash-1',actorUid:actor,confirmedAt:serverTimestamp(),...extra};
 const edit={...current,previousTotalPaise:0,basePaise:100000,beforePaise:100000,afterPaise:150000,deltaPaise:50000,
  reason:'Physical cash counted',actorName:actor,actionTime:Date.now()-2000,...historyExtra};return [current,edit];
}
function save(actor='partner',extra={},h={},options={}){
 const d=db(actor), b=writeBatch(d),[current,edit]=pair(actor,extra,h);
 if(!options.onlyHistory)b.set(doc(d,p('cashBalanceState')),current);
 if(!options.onlyState)b.set(doc(d,p('cashBalanceAdjustments',current.operationId)),edit);
 if(options.atomic){
  b.update(doc(d,p('financialState')),{version:2,confirmedCash:1500,reservedCash:0,confirmedBank:0,protectedEntitlements:0,lastOperationId:current.operationId,
   lastEntityType:'BALANCE_ADJUSTMENT',lastEntityId:current.operationId,lastActorUid:actor,actionTime:edit.actionTime,updatedAt:serverTimestamp()});
  b.set(doc(d,p('cashOperations',current.operationId)),{workspaceId:ws,operationId:current.operationId,entityType:'BALANCE_ADJUSTMENT',entityId:current.operationId,
   mutationType:'UPSERT',actorUid:actor,actionTime:edit.actionTime,cashDelta:500,reservedDelta:0,expectedVersion:1,newVersion:2,confirmedCashAfter:1500,createdAt:serverTimestamp()});
 }
 return b.commit();
}
test('Owner and Partner share one confirmed correction with reason/editor/time, preserving customer records',async()=>{
 await assertSucceeds(save());
 for(const actor of ['owner','partner']){
  const d=db(actor), current=(await getDoc(doc(d,p('cashBalanceState')))).data(), h=(await getDoc(doc(d,p('cashBalanceAdjustments','cash-1')))).data();
  assert.equal(current.totalAdjustmentPaise,50000);assert.equal(h.beforePaise,100000);assert.equal(h.afterPaise,150000);
  assert.equal(h.deltaPaise,50000);assert.equal(h.actorUid,'partner');assert.equal(h.reason,'Physical cash counted');assert.ok(h.confirmedAt);
  assert.equal((await getDoc(doc(d,p('entries','123')))).get('amountReceived'),1000);
 }
 await assertSucceeds(save('owner',{operationId:'cash-2',revision:2,totalAdjustmentPaise:-20000},
  {previousTotalPaise:50000,basePaise:120000,beforePaise:170000,afterPaise:100000,deltaPaise:-70000}));
 assert.equal((await getDoc(doc(db('partner'),p('cashBalanceState')))).get('totalAdjustmentPaise'),-20000);
 await assertSucceeds(save('partner',{operationId:'cash-3',revision:3,totalAdjustmentPaise:0},
  {previousTotalPaise:-20000,basePaise:120000,beforePaise:100000,afterPaise:120000,deltaPaise:20000,reason:'எண்ணிய ரொக்க இருப்பு'}));
 assert.equal((await getDoc(doc(db('owner'),p('cashBalanceAdjustments','cash-3')))).get('reason'),'எண்ணிய ரொக்க இருப்பு');
});
test('Operator, removed, foreign and forged Owner cannot read or adjust shared cash',async()=>{
 for(const actor of ['operator','removed','foreign','impostor']){
  await assertFails(save(actor));await assertFails(getDoc(doc(db(actor),p('cashBalanceState'))));
 }
 await assertFails(getDoc(doc(db('partner'),p('cashBalanceState','current','ws_foreign'))));
});
test('Disabled rollout rejects both reads and writes; original records are unaffected',async()=>{
 await seed({enabled:false});await assertFails(save());await assertFails(getDoc(doc(db('owner'),p('cashBalanceState'))));
 assert.equal((await getDoc(doc(db('owner'),p('entries','123')))).get('totalAmount'),2000);
});
test('Exact paise arithmetic, reason, actor and server time are mandatory',async()=>{
 for(const change of [{reason:''},{reason:'  '},{reason:'x'.repeat(501)},{actorName:'owner'},{actorUid:'owner'},
  {confirmedAt:0},{basePaise:100000.1},{deltaPaise:1},{afterPaise:150001},{previousTotalPaise:1},{unrelatedField:999}])
  await assertFails(save('partner',{},change));
 await assertFails(save('partner',{totalAdjustmentPaise:1000000000001}));
 await assertFails(save('partner',{actorUid:'owner'}));
});
test('State and immutable audit must commit together; no history rewrite, deletion or no-op adjustment',async()=>{
 await assertFails(save('partner',{}, {},{onlyState:true}));await assertFails(save('partner',{}, {},{onlyHistory:true}));
 await assertFails(save('partner',{totalAdjustmentPaise:0},{afterPaise:100000,deltaPaise:0}));
 await assertSucceeds(save());const d=db('owner');
 await assertFails(updateDoc(doc(d,p('cashBalanceAdjustments','cash-1')),{reason:'rewrite'}));
 await assertFails(deleteDoc(doc(d,p('cashBalanceAdjustments','cash-1'))));await assertFails(deleteDoc(doc(d,p('cashBalanceState'))));
});
test('Concurrent target edits accept one revision; duplicate operation cannot double-adjust cash',async()=>{
 const r=await Promise.allSettled([save('partner',{operationId:'race-a'}),save('owner',{operationId:'race-b'})]);
 assert.equal(r.filter(x=>x.status==='fulfilled').length,1);
 await assertFails(save());const s=(await getDoc(doc(db('owner'),p('cashBalanceState')))).data();
 assert.equal(s.revision,1);assert.equal(s.totalAdjustmentPaise,50000);
});
test('Existing atomic cash cannot be bypassed by a standalone correction',async()=>{
 await seed({atomic:true,baseline:true});await assertFails(save());
 // Compatibility rules intentionally cannot accept full-protocol writes.
 if(full){
  await assertFails(save('partner',{}, {basePaise:200000,beforePaise:200000,afterPaise:250000},{atomic:true}));
  await assertSucceeds(save('partner',{}, {},{atomic:true}));
  assert.equal((await getDoc(doc(db('owner'),p('financialState')))).get('confirmedCash'),1500);
  await assertFails(save('partner',{}, {},{atomic:true}));
 } else await assertFails(save('partner',{}, {},{atomic:true}));
});
