const test=require('node:test'), assert=require('node:assert/strict'), fs=require('node:fs'), path=require('node:path');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,updateDoc,deleteDoc,writeBatch,serverTimestamp,runTransaction}=require('firebase/firestore');
if(process.env.GCLOUD_PROJECT!=='demo-aidhunt-v16'||process.env.FIRESTORE_EMULATOR_HOST!=='127.0.0.1:18180'||process.env.GOOGLE_APPLICATION_CREDENTIALS)
 throw Error('Local synthetic emulator only');
let env; const ws='ws_display', p=(c,id='current',w=ws)=>`workspaces/${w}/${c}/${id}`;
const db=uid=>env.authenticatedContext(uid).firestore();
test.before(async()=>{env=await initializeTestEnvironment({projectId:'demo-aidhunt-v16',firestore:{host:'127.0.0.1',port:18180,
 rules:fs.readFileSync(path.join(__dirname,'..',process.env.BALANCE_RULES||'firestore.phase53.rules'),'utf8')}})});
test.after(()=>env.cleanup());
test.beforeEach(async()=>{await env.clearFirestore();await env.withSecurityRulesDisabled(async c=>{
 const d=c.firestore();await setDoc(doc(d,`workspaces/${ws}`),{workspaceId:ws,ownerUid:'owner'});
 for(const [uid,role,status] of [['owner','OWNER','ACTIVE'],['partner','PARTNER','ACTIVE'],['operator','OPERATOR','ACTIVE'],['removed','PARTNER','REMOVED']])
  await setDoc(doc(d,p('members',uid)),{uid,role,status,displayName:uid});
 await setDoc(doc(d,p('entries','1')),{workspaceId:ws,amountReceived:2500,pendingAmount:1000,totalAmount:3500});
})});
function pair(actor='partner',extra={},historyExtra={}){
 const common={workspaceId:ws,amountPaise:500050,revision:1,operationId:'display-1',reason:'Counted display; receipt not reclassified',
 editedByUid:actor,editedByName:actor,editedAt:serverTimestamp(),...extra};
 return [common,{...common,previousOverridePaise:null,beforeDisplayedPaise:450000,afterDisplayedPaise:common.amountPaise??450000,
 calculatedPaiseAtEdit:450000,differencePaise:(common.amountPaise??450000)-450000,...historyExtra}];
}
function save(actor='partner',extra={},historyExtra={},omitHistory=false){
 const d=db(actor),b=writeBatch(d),[current,h]=pair(actor,extra,historyExtra);
 b.set(doc(d,p('balanceDisplay')),current);
 if(!omitHistory)b.set(doc(d,p('balanceDisplayEdits',current.operationId)),h);
 return b.commit();
}
test('Owner and Partner edits retain exact paise and leave financial records unchanged',async()=>{
 await assertSucceeds(save());const d=db('owner');
 assert.equal((await getDoc(doc(d,p('balanceDisplay')))).get('amountPaise'),500050);
 const h=(await getDoc(doc(d,p('balanceDisplayEdits','display-1')))).data();
 assert.equal(h.differencePaise,50050);assert.equal(h.editedByUid,'partner');assert.ok(h.editedAt);
 assert.deepEqual((await getDoc(doc(d,p('entries','1')))).data(),{workspaceId:ws,amountReceived:2500,pendingAmount:1000,totalAmount:3500});
 await assertSucceeds(save('owner',{amountPaise:null,operationId:'restore',revision:2},
  {previousOverridePaise:500050,beforeDisplayedPaise:500050,afterDisplayedPaise:450000,differencePaise:-50050}));
 assert.equal((await getDoc(doc(d,p('balanceDisplay')))).get('amountPaise'),null);
});
test('Operator, removed and foreign accounts cannot read or edit display balances',async()=>{
 for(const actor of ['operator','removed','foreign']){
  await assertFails(save(actor));await assertFails(getDoc(doc(db(actor),p('balanceDisplay'))));
 }
 const d=db('partner');await assertFails(getDoc(doc(d,p('balanceDisplay','current','ws_foreign'))));
});
test('Reason, real editor, timestamp, paise range and exact history are mandatory',async()=>{
 for(const change of [{reason:''},{reason:'   '},{reason:'x'.repeat(501)},{editedByUid:'owner'},{editedByName:'owner'},
  {editedAt:0},{amountPaise:1.1},{amountPaise:1000000000001},{workspaceId:'other'},{unexpectedFinancialField:123}])
  await assertFails(save('partner',change));
 await assertFails(save('partner',{}, {},true));
 await assertFails(save('partner',{}, {differencePaise:1}));
 await assertFails(save('partner',{}, {previousOverridePaise:5}));
});
test('History is append only; replay and stale revision cannot overwrite another edit',async()=>{
 await assertSucceeds(save());await assertFails(save());
 const d=db('owner'),ref=doc(d,p('balanceDisplayEdits','display-1'));
 await assertFails(updateDoc(ref,{reason:'rewritten'}));await assertFails(deleteDoc(ref));
 await assertFails(save('owner',{operationId:'stale',revision:1}));
 await assertFails(deleteDoc(doc(d,p('balanceDisplay'))));
 assert.equal((await getDoc(doc(d,p('balanceDisplay')))).get('revision'),1);
});
test('Two partners using the same revision have exactly one successful edit',async()=>{
 const results=await Promise.allSettled([save('partner',{operationId:'race-a'}),save('owner',{operationId:'race-b'})]);
 assert.equal(results.filter(x=>x.status==='fulfilled').length,1);
 assert.equal((await getDoc(doc(db('owner'),p('balanceDisplay')))).get('revision'),1);
});
