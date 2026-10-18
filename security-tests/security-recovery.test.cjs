const test=require('node:test'), assert=require('node:assert/strict'), fs=require('node:fs'), path=require('node:path');
const {initializeTestEnvironment,assertFails,assertSucceeds}=require('@firebase/rules-unit-testing');
const {doc,getDoc,getDocs,collection,setDoc,updateDoc,serverTimestamp}=require('firebase/firestore');
if(process.env.GCLOUD_PROJECT!=='demo-aidhunt-v16'||process.env.FIRESTORE_EMULATOR_HOST!=='127.0.0.1:18180'||process.env.GOOGLE_APPLICATION_CREDENTIALS) throw new Error('REFUSED: demo loopback emulator without credential files required');
let env;
const A='recovery-a', B='recovery-b';
const member=(ws,uid)=>`workspaces/${ws}/members/${uid}`;
const record=(ws,col,id='9007199254740995')=>`workspaces/${ws}/${col}/${id}`;
const db=(uid,claims={})=>env.authenticatedContext(uid,claims).firestore();
const read=(d,ws,col)=>getDoc(doc(d,record(ws,col)));
const admin=(p,data)=>env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),p),data));
const asset=(ws,uid,extra={})=>({workspaceId:ws,actorUid:uid,createdByUid:uid,createdByRole:'PARTNER',createdAt:Date.now()-1000,
 serverReceivedAt:serverTimestamp(),authorizationTimeStatus:'CLIENT_ORIGINAL_V2',authorizationCreatedAt:serverTimestamp(),
 operationId:'synthetic-operation',protocolVersion:2,operationType:'UPSERT',actionTime:Date.now()-1,dependencies:[],isDeleted:false,...extra});
test.before(async()=>{env=await initializeTestEnvironment({projectId:'demo-aidhunt-v16',firestore:{host:'127.0.0.1',port:18180,rules:fs.readFileSync(path.join(__dirname,'../firestore.rules'),'utf8')}});});
test.beforeEach(async()=>{
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async c=>{
  const d=c.firestore();
  for(const [ws,owner] of [[A,'multi'],[B,'owner-b']]) {
   await setDoc(doc(d,`workspaces/${ws}`),{workspaceId:ws,ownerUid:owner,name:'Identical synthetic business'});
   for(const [uid,role] of [[owner,'OWNER'],['partner','PARTNER'],['operator','OPERATOR']])
    await setDoc(doc(d,member(ws,uid)),{uid,workspaceId:ws,role,status:'ACTIVE',joinedAt:1});
   for(const col of ['extensions','tractors','entries','customers','collections','expenses','withdrawals'])
    await setDoc(doc(d,record(ws,col)),{workspaceId:ws,id:'9007199254740995',createdByUid:owner,isHistorical:false,marker:ws});
  }
  await setDoc(doc(d,member(B,'multi')),{uid:'multi',workspaceId:B,role:'PARTNER',status:'ACTIVE'});
 });
});
test.after(async()=>env?.cleanup());
test('SEC-01 unauthenticated reads deny while the same paths allow authorized Owner',async()=>{
 for(const col of ['extensions','tractors','entries','customers','collections','expenses','withdrawals']) {
  await assertFails(read(env.unauthenticatedContext().firestore(),A,col));
  assert.equal((await assertSucceeds(read(db('multi'),A,col))).data().marker,A);
 }
});
test('SEC-02 forged global Owner claim grants no exact workspace membership',async()=>{
 for(const ws of [A,B]) for(const col of ['extensions','collections']) await assertFails(read(db('outsider',{role:'OWNER',workspaceId:A}),ws,col));
 assert.equal((await assertSucceeds(read(db('partner'),A,'collections'))).data().marker,A);
});
for(const status of ['REMOVED','SUSPENDED','EXPIRED','INACTIVE','']) test(`SEC-03 status ${status||'empty'} denies after prior ACTIVE access`,async()=>{
 const d=db('partner'); await assertSucceeds(read(d,A,'extensions'));
 await admin(member(A,'partner'),{uid:'partner',workspaceId:A,role:'PARTNER',status});
 await assertFails(read(d,A,'extensions'));await assertFails(read(d,A,'collections'));await assertSucceeds(read(d,B,'collections'));
});
test('SEC-04 recognized role plus root ownership required; Operator remains nonfinancial',async()=>{
 for(const role of ['ADMIN','CO-OWNER','unknown','OWNER']) {
  await admin(member(A,'forged'),{uid:'forged',workspaceId:A,role,status:'ACTIVE'});
  await assertFails(read(db('forged'),A,'extensions'));
 }
 for(const [uid,financial] of [['multi',true],['partner',true],['operator',false]]) {
  await assertSucceeds(read(db(uid),A,'extensions'));
  if(financial) await assertSucceeds(read(db(uid),A,'collections')); else await assertFails(read(db(uid),A,'collections'));
 }
});
test('I-02 same UID is Owner in A and Partner in B without global privilege carryover',async()=>{
 const d=db('multi',{role:'OWNER'});
 await assertSucceeds(updateDoc(doc(d,`workspaces/${A}`),{name:'Owner authorized change'}));
 await assertFails(updateDoc(doc(d,`workspaces/${B}`),{name:'Forged Owner change'}));
 assert.equal((await getDoc(doc(d,`workspaces/${B}`))).data().name,'Identical synthetic business');
 assert.equal((await getDoc(doc(d,member(A,'multi')))).data().role,'OWNER');
 assert.equal((await getDoc(doc(d,member(B,'multi')))).data().role,'PARTNER');
});
test('I-06 roles are viewer invariant and even Owner cannot rewrite Partner to Operator',async()=>{
 const roles={multi:'OWNER',partner:'PARTNER',operator:'OPERATOR'};
 for(const viewer of Object.keys(roles)) for(const [uid,role] of Object.entries(roles)) assert.equal((await getDoc(doc(db(viewer),member(A,uid)))).data().role,role);
 for(const viewer of Object.keys(roles)) await assertFails(updateDoc(doc(db(viewer),member(A,'partner')),{role:'OPERATOR'}));
 assert.equal((await getDoc(doc(db('multi'),member(A,'partner')))).data().role,'PARTNER');
});
test('SEC-06 envelope workspace and actor must match path and verified request UID',async()=>{
 const d=db('partner');await assertSucceeds(setDoc(doc(d,record(A,'extensions','new')),asset(A,'partner')));
 const p=record(A,'extensions','forged');
 await assertFails(setDoc(doc(d,p),asset(B,'partner')));
 await assertFails(setDoc(doc(d,p),asset(A,'partner',{actorUid:'multi'})));
 await assertFails(setDoc(doc(d,p),asset(A,'partner',{createdByUid:'multi'})));
 assert.equal((await getDoc(doc(d,p))).exists(),false);
});
test('SEC-11 unmatched paths deny even to Owner while matched assets remain readable',async()=>{
 const d=db('multi');await assertSucceeds(read(d,A,'extensions'));
 for(const p of [`workspaces/${A}/arbitrary/x`,'arbitrary/x']) {
  await assertFails(getDoc(doc(d,p)));await assertFails(setDoc(doc(d,p),{workspaceId:A}));
 }
});
test('SEC-12 legacy collaboration and partnerPermissions cannot bypass authorization',async()=>{
 for(const uid of ['multi','partner','outsider']) for(const p of ['collaborationGroups/legacy','userCollaborationGroups/multi/groups/legacy',`workspaces/${A}/partnerPermissions/legacy`]) {
  await assertFails(getDoc(doc(db(uid),p)));await assertFails(setDoc(doc(db(uid),p),{role:'OWNER',workspaceId:A}));
 }
});
test('W-06 foreign reads writes and listener queries deny bidirectionally for colliding IDs',async()=>{
 await admin(member(A,'only-a'),{uid:'only-a',workspaceId:A,role:'PARTNER',status:'ACTIVE'});
 await admin(member(B,'only-b'),{uid:'only-b',workspaceId:B,role:'PARTNER',status:'ACTIVE'});
 for(const [uid,own,other] of [['only-a',A,B],['only-b',B,A]]) {
  assert.equal((await assertSucceeds(read(db(uid),own,'entries'))).data().marker,own);
  await assertFails(read(db(uid),other,'entries'));
  await assertFails(setDoc(doc(db(uid),record(other,'extensions','new')),asset(other,uid)));
  await assertFails(getDocs(collection(db(uid),`workspaces/${other}/collections`)));
 }
});
