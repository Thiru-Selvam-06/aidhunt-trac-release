const fs = require('node:fs');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, updateDoc, deleteDoc, getDoc, writeBatch, serverTimestamp, Timestamp } = require('firebase/firestore');
const test = require('node:test');
const assert = require('node:assert/strict');
let env;
const now = Date.now();
const ws = 'workspace-a';
const uid = { owner:'owner-a', partner:'partner-a', operator:'operator-a', other:'partner-b', outsider:'outsider' };
function db(role) { return env.authenticatedContext(uid[role]).firestore(); }
function path(collection, id='1') { return `workspaces/${ws}/${collection}/${id}`; }
function envelope(actor='partner', actionTime=now) { return { protocolVersion:2,operationType:'UPSERT',dependencies:[],authorizationTimeStatus:'CLIENT_ORIGINAL_V2', workspaceId:ws, actorUid:uid[actor], operationId:`op-${actor}-${actionTime}`, actionTime, isDeleted:false }; }
function entry(actor='partner', extra={}) { return { id:1, ...envelope(actor), createdByUid:uid[actor], createdByRole:actor.toUpperCase(), createdAt:now-1000, authorizationCreatedAt:Timestamp.fromMillis(now-1000),serverReceivedAt:serverTimestamp(), isHistorical:false, entryForUid:uid[actor], financialAttributionUid:uid[actor], customerId:2, totalAmount:50000, amountReceived:0, pendingAmount:50000, ...extra }; }
async function seed(collection, data, id='1') {
 const trusted = Object.prototype.hasOwnProperty.call(data,'authorizationCreatedAt') && data.authorizationCreatedAt === null
   ? data : {...data, authorizationCreatedAt: data.authorizationCreatedAt instanceof Timestamp ? data.authorizationCreatedAt : Timestamp.fromMillis(now-1000)};
 await env.withSecurityRulesDisabled(async c => setDoc(doc(c.firestore(),path(collection,id)),trusted));
}
async function collect(actor, id, amount, before, status='ACTIVE', original=null) {
 const database=db(actor); const batch=writeBatch(database); const opId=`collection-${id}-${status}`;
 const persisted=await getDoc(doc(db('owner'),path('collections',id)));
 const collection={...envelope(actor), operationId:opId, id:Number(id), jobEntryId:1, customerId:2, amount, collectedByUid:uid[actor], collectedByName:actor, collectedByRole:actor, collectedAt:now-1000, createdAt:now-1000, allocationsJson:`[{"jobId":1,"amount":${amount}}]`, status, ...(original||{}), authorizationCreatedAt:persisted.exists()?persisted.data().authorizationCreatedAt:Timestamp.fromMillis(now-1000),serverReceivedAt:persisted.exists()?persisted.data().serverReceivedAt:serverTimestamp(), status, actorUid:uid[actor],operationId:opId};
 if(status==='REVERSED') { collection.reversedByUid=uid[actor];collection.reversedAt=now; }
 const delta=status==='ACTIVE'?amount:-amount;
 batch.set(doc(database,path('collections',id)),collection);
 batch.update(doc(database,path('entries')), {...envelope(actor),operationId:opId,collectionId:id,amountReceived:before.amountReceived+delta,pendingAmount:before.pendingAmount-delta});
 return { commit:()=>batch.commit(), collection };
}
test.before(async()=>{ env=await initializeTestEnvironment({projectId:'demo-aidhunt-v16',firestore:{host:'127.0.0.1',port:18180,rules:fs.readFileSync(require('node:path').join(__dirname, '../firestore.rules'),'utf8')}}); });
test.after(async()=>env?.cleanup());
test.beforeEach(async()=>{await env.clearFirestore();await env.withSecurityRulesDisabled(async c=>{
 const database=c.firestore();await setDoc(doc(database,`workspaces/${ws}`),{workspaceId:ws,ownerUid:uid.owner});
 await setDoc(doc(database,path('customers','2')), {...envelope('owner'),id:2,createdByUid:uid.owner,createdAt:now-1000});
 for(const [role,id] of Object.entries(uid)) if(role!=='outsider') await setDoc(doc(database,`workspaces/${ws}/members/${id}`),{uid:id,workspaceId:ws,role:role==='other'?'PARTNER':role.toUpperCase(),status:'ACTIVE'});
});});
for(const role of ['owner','partner','operator']) test(`${role} creates own ordinary entry`,async()=>assertSucceeds(setDoc(doc(db(role),path('entries')),entry(role))));
for(const role of ['owner','partner']) test(`${role} creates tractor job for another active partner`,async()=>{
 await seed('tractors',{workspaceId:ws,isDeleted:false},'3');
 await assertSucceeds(setDoc(doc(db(role),path('entries')),entry(role,{tractorId:3,entryForUid:uid.other,financialAttributionUid:uid.other})));
});
for(const invalid of [null,{workspaceId:ws,isDeleted:true},{workspaceId:'foreign',isDeleted:false}])
 test(`tractor reference remains denied for ${JSON.stringify(invalid)}`,async()=>{
  if(invalid) await seed('tractors',invalid,'3');
  await assertFails(setDoc(doc(db('owner'),path('entries')),entry('owner',{tractorId:3,entryForUid:uid.other,financialAttributionUid:uid.other})));
 });
for(const role of ['partner','operator']) {
 test(`${role} entry-for permission follows role`,async()=>{
   const result=setDoc(doc(db(role),path('entries')),entry(role,{entryForUid:uid.other,financialAttributionUid:uid.other}));
   await (role==='partner'?assertSucceeds(result):assertFails(result));
 });
 test(`${role} edits own ordinary entry before deadline`,async()=>{await seed('entries',entry(role));await assertSucceeds(updateDoc(doc(db(role),path('entries')),{totalAmount:60000,pendingAmount:60000}));});
 test(`${role} cannot bypass checked Entry deletion with a direct tombstone`,async()=>{await seed('entries',entry(role));await assertFails(updateDoc(doc(db(role),path('entries')),{isDeleted:true,authorizationDeletedAt:serverTimestamp()}));});
 test(`${role} cannot mutate historical entry`,async()=>{await seed('entries',entry(role,{isHistorical:true}));await assertFails(updateDoc(doc(db(role),path('entries')),{totalAmount:60000,pendingAmount:60000}));});
 test(`${role} cannot create historical entry`,async()=>assertFails(setDoc(doc(db(role),path('entries')),entry(role,{isHistorical:true}))));
 test(`${role} cannot reset creation time`,async()=>{await seed('entries',entry(role));await assertFails(updateDoc(doc(db(role),path('entries')),{createdAt:now}));});
 test(`${role} cannot edit another creator's entry`,async()=>{await seed('entries',entry('other'));await assertFails(updateDoc(doc(db(role),path('entries')),{...envelope(role),totalAmount:60000,pendingAmount:60000}));});
}
test('Owner creates eligible member historical entry',async()=>assertSucceeds(setDoc(doc(db('owner'),path('entries')),entry('owner',{isHistorical:true,entryForUid:uid.partner,financialAttributionUid:uid.partner}))));

for(const target of ['owner','other']) test('Partner creates Entry and linked Expense for '+target,async()=>{
 const database=db('partner'), batch=writeBatch(database);
 batch.set(doc(database,path('entries')),entry('partner',{entryForUid:uid[target],financialAttributionUid:uid[target]}));
 batch.set(doc(database,path('expenses')), {...envelope(),id:1,createdByUid:uid.partner,createdByRole:'PARTNER',
   createdAt:now-1000,authorizationCreatedAt:Timestamp.fromMillis(now-1000),serverReceivedAt:serverTimestamp(),
   expenseForUid:uid[target],financialAttributionUid:uid[target],relatedJobId:1,paidBy:'I Paid',paidByUid:uid[target],amount:100});
 await assertSucceeds(batch.commit());
});
test('Partner cannot use another creator job to add expense for others',async()=>{
 await seed('entries',entry('owner'));
 await assertFails(setDoc(doc(db('partner'),path('expenses')),{...envelope(),id:1,createdByUid:uid.partner,
   createdByRole:'PARTNER',createdAt:now-1000,authorizationCreatedAt:Timestamp.fromMillis(now-1000),serverReceivedAt:serverTimestamp(),
   expenseForUid:uid.owner,financialAttributionUid:uid.owner,relatedJobId:1,amount:100}));
});
test('Partner cannot select an inactive or foreign workspace beneficiary',async()=>{
 await env.withSecurityRulesDisabled(async c=>updateDoc(doc(c.firestore(),path('members',uid.other)),{status:'REMOVED'}));
 await assertFails(setDoc(doc(db('partner'),path('entries')),entry('partner',{entryForUid:uid.other,financialAttributionUid:uid.other})));
 await assertFails(setDoc(doc(db('partner'),path('entries')),entry('partner',{entryForUid:'foreign',financialAttributionUid:'foreign'})));
});
test('Partner cannot create standalone expense for another member',async()=>{
 await assertFails(setDoc(doc(db('partner'),path('expenses')),{...envelope(),id:1,createdByUid:uid.partner,
   createdByRole:'PARTNER',createdAt:now-1000,authorizationCreatedAt:Timestamp.fromMillis(now-1000),serverReceivedAt:serverTimestamp(),
   expenseForUid:uid.owner,financialAttributionUid:uid.owner,amount:100}));
});
test('Partner cannot add another member expense through a historical job',async()=>{
 await seed('entries',entry('partner',{entryForUid:uid.owner,financialAttributionUid:uid.owner,isHistorical:true}));
 await assertFails(setDoc(doc(db('partner'),path('expenses')),{...envelope(),id:1,createdByUid:uid.partner,
   createdByRole:'PARTNER',createdAt:now-1000,authorizationCreatedAt:Timestamp.fromMillis(now-1000),serverReceivedAt:serverTimestamp(),
   expenseForUid:uid.owner,financialAttributionUid:uid.owner,relatedJobId:1,amount:100}));
});
test('accepted limitation: backdated offline action can arrive after deadline',async()=>{let record=entry('partner',{createdAt:now-53*3600000,authorizationCreatedAt:Timestamp.fromMillis(now-53*3600000),actionTime:now-48*3600000});await seed('entries',record);await assertSucceeds(updateDoc(doc(db('partner'),path('entries')),{totalAmount:60000,pendingAmount:60000}));});
test('offline action after deadline rejected',async()=>{let record=entry('partner',{createdAt:now-53*3600000,authorizationCreatedAt:Timestamp.fromMillis(now-53*3600000),actionTime:now-27*3600000});await seed('entries',record);await assertFails(updateDoc(doc(db('partner'),path('entries')),{totalAmount:60000,pendingAmount:60000}));});
test('future action timestamp rejected',async()=>assertFails(setDoc(doc(db('partner'),path('entries')),entry('partner',{actionTime:now+3600000}))));

for (const role of ['owner','partner']) test(`${role} offline collection accepts legacy path-scoped member without workspaceId`, async()=>{
 await env.withSecurityRulesDisabled(async c=>{
   await setDoc(doc(c.firestore(),path('members',uid[role])), {role:role==='owner'?'owner':'Partner',status:'active'});
 });
 const before=entry(role);await seed('entries',before);
 const action=await collect(role,'91',1250,before);
 await assertSucceeds(action.commit());
 const confirmed=(await getDoc(doc(db(role),path('entries')))).data();
 assert.equal(confirmed.amountReceived,1250);assert.equal(confirmed.pendingAmount,48750);
 assert.equal((await getDoc(doc(db(role),path('collections','91')))).data().collectedByUid,uid[role]);
});
test('collection rejects explicitly foreign workspace membership metadata',async()=>{
 await env.withSecurityRulesDisabled(async c=>updateDoc(doc(c.firestore(),path('members',uid.partner)),{workspaceId:'foreign'}));
 const before=entry();await seed('entries',before);
 await assertFails((await collect('partner','92',1250,before)).commit());
 assert.equal((await getDoc(doc(db('owner'),path('entries')))).data().amountReceived,0);
 assert.equal((await getDoc(doc(db('owner'),path('collections','92')))).exists(),false);
});
test('outsider cannot read or write workspace',async()=>{await seed('entries',entry());await assertFails(getDoc(doc(db('outsider'),path('entries'))));await assertFails(setDoc(doc(db('outsider'),path('entries','2')),entry('outsider')));});
test('cross-workspace envelope rejected',async()=>assertFails(setDoc(doc(db('partner'),path('entries')),entry('partner',{workspaceId:'workspace-b'}))));
test('removed membership denied',async()=>{await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),`workspaces/${ws}/members/${uid.partner}`),{status:'REMOVED'}));await assertFails(setDoc(doc(db('partner'),path('entries')),entry()));});
test('Co-Owner never grants Owner authority',async()=>{await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),`workspaces/${ws}/members/${uid.partner}`),{role:'Co-Owner'}));await assertFails(setDoc(doc(db('partner'),path('entries')),entry('partner',{isHistorical:true})));});
for(const role of ['partner','owner']) test(`${role} creates expense`,async()=>assertSucceeds(setDoc(doc(db(role),path('expenses')),{...envelope(role),id:1,createdByUid:uid[role],createdByRole:role,createdAt:now-1000,authorizationCreatedAt:Timestamp.fromMillis(now-1000),serverReceivedAt:serverTimestamp(),expenseForUid:uid[role],financialAttributionUid:uid[role],amount:100})));
test('Partner edits and deletes own expense before deadline',async()=>{const record={...envelope(),id:1,createdByUid:uid.partner,createdByRole:'PARTNER',createdAt:now-1000,authorizationCreatedAt:Timestamp.fromMillis(now-1000),serverReceivedAt:serverTimestamp(),expenseForUid:uid.partner,financialAttributionUid:uid.partner,amount:100};await seed('expenses',record);await assertSucceeds(updateDoc(doc(db('partner'),path('expenses')),{amount:200}));await assertSucceeds(updateDoc(doc(db('partner'),path('expenses')),{isDeleted:true,authorizationDeletedAt:serverTimestamp()}));});
for(const collection of ['expenses','withdrawals','collections']) test(`Operator cannot read ${collection}`,async()=>{await seed(collection,{...envelope('owner')});await assertFails(getDoc(doc(db('operator'),path(collection))));});
test('Operator cannot create asset',async()=>assertFails(setDoc(doc(db('operator'),path('tractors')),{...envelope('operator'),createdByUid:uid.operator,createdAt:now})));
test('Partner cannot change another creator asset',async()=>{await seed('tractors',{...envelope('owner'),createdByUid:uid.owner,createdAt:now});await assertFails(updateDoc(doc(db('partner'),path('tractors')),{...envelope(),label:'Changed'}));});
test('Partner asset deletion has no 24-hour restriction',async()=>{await seed('tractors',{...envelope(),createdByUid:uid.partner,createdAt:now-99*3600000});await assertSucceeds(updateDoc(doc(db('partner'),path('tractors')),{isDeleted:true,authorizationDeletedAt:serverTimestamp()}));});
test('withdrawal direct edit forbidden to Owner',async()=>{await seed('withdrawals',{...envelope('owner'),id:1,createdByUid:uid.owner,createdByRole:'OWNER',createdAt:now-1000,authorizationCreatedAt:Timestamp.fromMillis(now-1000),serverReceivedAt:serverTimestamp(),targetPartnerUid:uid.owner,amount:100});await assertFails(updateDoc(doc(db('owner'),path('withdrawals')),{amount:200}));});
test('Owner cannot change member role',async()=>assertFails(updateDoc(doc(db('owner'),`workspaces/${ws}/members/${uid.partner}`),{role:'OPERATOR'})));
test('Owner cannot bypass invitation verification by inserting Partner membership',async()=>assertFails(setDoc(doc(db('owner'),`workspaces/${ws}/members/another`),{uid:'another',workspaceId:ws,role:'PARTNER',status:'ACTIVE'})));
test('Owner 20k collection unchanged when Partner reverses own 30k',async()=>{
 const initial=entry('owner');await seed('entries',initial);
 const one=await collect('owner','20',20000,initial);await assertSucceeds(one.commit());
 const afterOne={...initial,amountReceived:20000,pendingAmount:30000,collectionId:'20',operationId:'collection-20-ACTIVE',actorUid:uid.owner};
 const two=await collect('partner','30',30000,afterOne);await assertSucceeds(two.commit());
 const afterTwo={...afterOne,amountReceived:50000,pendingAmount:0,collectionId:'30',operationId:'collection-30-ACTIVE',actorUid:uid.partner};
 const reverse=await collect('partner','30',30000,afterTwo,'REVERSED',two.collection);await assertSucceeds(reverse.commit());
 const oneDoc=await getDoc(doc(db('owner'),path('collections','20')));assert.equal(oneDoc.data().status,'ACTIVE');assert.equal(oneDoc.data().amount,20000);
 const twoDoc=await getDoc(doc(db('owner'),path('collections','30')));assert.equal(twoDoc.data().status,'REVERSED');
 const final=await getDoc(doc(db('owner'),path('entries')));assert.equal(final.data().pendingAmount,30000);assert.equal(final.data().amountReceived,20000);
 await assertFails(deleteDoc(doc(db('partner'),path('collections','30'))));
});
test('financial amount cannot be changed without collection',async()=>{await seed('entries',entry('partner'));await assertFails(updateDoc(doc(db('partner'),path('entries')),{amountReceived:100,pendingAmount:49900}));});

for (const actor of ['owner','partner','operator']) test(`${actor} cannot physically delete an Entry outside the trusted reconciliation command`,async()=>{
 await seed('entries',entry(actor));
 await assertFails(updateDoc(doc(db(actor),path('entries')),{isDeleted:true,authorizationDeletedAt:serverTimestamp()}));
 await assertFails(deleteDoc(doc(db(actor),path('entries'))));
 assert.equal((await getDoc(doc(db(actor),path('entries')))).exists(),true);
});

test('Partner completes an already server-authorized tombstone after the deadline',async()=>{
 await seed('expenses',{...envelope(),id:1,createdByUid:uid.partner,createdByRole:'PARTNER',createdAt:now-53*3600000,authorizationCreatedAt:Timestamp.fromMillis(now-53*3600000),authorizationDeletedAt:Timestamp.fromMillis(now-48*3600000),isDeleted:true,actionTime:now-48*3600000,expenseForUid:uid.partner,financialAttributionUid:uid.partner,amount:100});
 await assertSucceeds(deleteDoc(doc(db('partner'),path('expenses'))));
});
test('expired ordinary delete remains denied',async()=>{
 await seed('entries',entry('operator',{createdAt:now-26*3600000,authorizationCreatedAt:Timestamp.fromMillis(now-26*3600000)}));
 await assertFails(updateDoc(doc(db('operator'),path('entries')),{isDeleted:true,authorizationDeletedAt:serverTimestamp()}));
 await assertFails(deleteDoc(doc(db('operator'),path('entries'))));
});
test('active collections protect source from ordinary deletion',async()=>{
 const original=entry('owner');await seed('entries',original);const payment=await collect('owner','20',20000,original);await payment.commit();
 await assertFails(updateDoc(doc(db('owner'),path('entries')),{isDeleted:true,authorizationDeletedAt:serverTimestamp()}));
});
test('missing or foreign Customer reference is denied',async()=>assertFails(setDoc(doc(db('operator'),path('entries')),entry('operator',{customerId:999}))));
test('negative expense edit is denied',async()=>{
 await seed('expenses',{...envelope(),id:1,createdByUid:uid.partner,createdByRole:'PARTNER',createdAt:now-1000,authorizationCreatedAt:Timestamp.fromMillis(now-1000),serverReceivedAt:serverTimestamp(),expenseForUid:uid.partner,financialAttributionUid:uid.partner,amount:100});
 await assertFails(updateDoc(doc(db('partner'),path('expenses')),{amount:-100}));
});
test('a Customer delete must use the trusted dependency check',async()=>{
 await assertFails(deleteDoc(doc(db('owner'),path('customers','2'))));
});
async function archive(actor='partner') {
 const database=db('owner'); const batch=writeBatch(database);
 batch.update(doc(database,`workspaces/${ws}/members/${uid[actor]}`),{status:'REMOVED'});
 batch.set(doc(database,`workspaces/${ws}/deletedUsers/${uid[actor]}`),{workspaceId:ws,uid:uid[actor],displayName:actor,role:actor.toUpperCase(),removedAt:now,removedByUid:uid.owner,purgeState:'ARCHIVED'});
 await assertSucceeds(batch.commit());
}
test('Stage 1 revokes member access but keeps its due collectible',async()=>{
 const due=entry('partner');await seed('entries',due);await archive();
 await assertFails(getDoc(doc(db('partner'),path('entries'))));
 await assertFails(updateDoc(doc(db('owner'),path('entries')),{...envelope('owner'),totalAmount:60000,pendingAmount:60000}));
 const payment=await collect('owner','20',20000,due);await assertSucceeds(payment.commit());
 assert.equal((await getDoc(doc(db('owner'),path('entries')))).data().pendingAmount,30000);
});
test('Stage 2 includes an Owner-created Entry attributed to the removed Partner',async()=>{
 await seed('entries',entry('owner',{entryForUid:uid.partner,financialAttributionUid:uid.partner}));await archive();
 await assertFails(deleteDoc(doc(db('owner'),path('entries'))));
 await assertSucceeds(updateDoc(doc(db('owner'),`workspaces/${ws}/deletedUsers/${uid.partner}`),{purgeState:'PURGE_PENDING',backupReady:true,backupSha256:'verified-test-backup',purgeManifest:[path('entries')]}));
 await assertSucceeds(deleteDoc(doc(db('owner'),path('entries'))));
 await assertFails(setDoc(doc(db('owner'),path('entries')),entry('owner',{entryForUid:uid.partner,financialAttributionUid:uid.partner})));
});
test('Partner cannot view Deleted Users',async()=>{
 await archive('other');await assertFails(getDoc(doc(db('partner'),`workspaces/${ws}/deletedUsers/${uid.other}`)));
});
test('Owner purge reconciliation works after an existing collectionId',async()=>{
 const original=entry('owner');await seed('entries',original);const payment=await collect('partner','30',30000,original);await payment.commit();await archive();
 await updateDoc(doc(db('owner'),`workspaces/${ws}/deletedUsers/${uid.partner}`),{purgeState:'PURGE_PENDING',backupReady:true,backupSha256:'verified-test-backup',purgeManifest:[path('collections','30')]});
 await assertSucceeds(updateDoc(doc(db('owner'),path('entries')),{...envelope('owner'),purgeUid:uid.partner,amountReceived:0,pendingAmount:50000}));
 await assertSucceeds(deleteDoc(doc(db('owner'),path('collections','30'))));
});
test('Owner can update an active member display name without changing identity',async()=>assertSucceeds(updateDoc(doc(db('owner'),`workspaces/${ws}/members/${uid.partner}`),{displayName:'Updated Partner',updatedAt:now})));
test('Partner cannot rename another member',async()=>assertFails(updateDoc(doc(db('partner'),`workspaces/${ws}/members/${uid.other}`),{displayName:'Changed',updatedAt:now})));
test('member cannot change phone without verified invitation',async()=>assertFails(updateDoc(doc(db('partner'),`workspaces/${ws}/members/${uid.partner}`),{phone:'+919999999999'})));
test('removed member can read own revocation document but cannot read workspace records',async()=>{await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),`workspaces/${ws}/members/${uid.partner}`),{status:'REMOVED'}));await assertSucceeds(getDoc(doc(db('partner'),`workspaces/${ws}/members/${uid.partner}`)));await assertFails(getDoc(doc(db('partner'),path('entries'))));});

// Read permissions are tested independently of the already-covered mutation permissions.
for (const role of ['owner','partner','operator']) test(`${role} reads permitted ordinary Entry`, async()=>{
 await seed('entries',entry('owner'));await assertSucceeds(getDoc(doc(db(role),path('entries'))));
});
for (const role of ['owner','partner']) test(`${role} reads historical Entry`,async()=>{
 await seed('entries',entry('owner',{isHistorical:true}));await assertSucceeds(getDoc(doc(db(role),path('entries'))));
});
test('Operator cannot read historical Entry',async()=>{
 await seed('entries',entry('owner',{isHistorical:true}));await assertFails(getDoc(doc(db('operator'),path('entries'))));
});
test('Unknown ACTIVE membership role cannot read workspace records',async()=>{
 await seed('entries',entry());await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),`workspaces/${ws}/members/${uid.partner}`),{role:'Co-Owner'}));
 await assertFails(getDoc(doc(db('partner'),path('entries'))));
});
test('Operator ordinary-only query succeeds and unfiltered query denies',async()=>{
 const {collection,query,where,getDocs}=require('firebase/firestore');await seed('entries',entry('operator'));await seed('entries',entry('owner',{isHistorical:true}),'99');
 await assertSucceeds(getDocs(query(collection(db('operator'),`workspaces/${ws}/entries`),where('isHistorical','==',false))));
 await assertFails(getDocs(collection(db('operator'),`workspaces/${ws}/entries`)));
});
test('Purge backup chunks are immutable Owner-only workspace records',async()=>{
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),`workspaces/${ws}/deletedUsers/${uid.other}`),{workspaceId:ws,uid:uid.other,purgeState:'ARCHIVED'}));
 const location=`workspaces/${ws}/deletedUsers/${uid.other}/backupChunks/hash_0`;
 await assertSucceeds(setDoc(doc(db('owner'),location),{workspaceId:ws,memberUid:uid.other,data:'backup',index:0,backupId:'hash'}));
 await assertSucceeds(getDoc(doc(db('owner'),location)));await assertFails(getDoc(doc(db('partner'),location)));await assertFails(getDoc(doc(db('operator'),location)));
 await assertFails(updateDoc(doc(db('owner'),location),{data:'replacement'}));
});

test('Purge preparation freezes the source without authorizing deletion before verified backup',async()=>{
 const target=uid.partner;await seed('entries',entry('owner',{entryForUid:target,financialAttributionUid:target,isDeleted:true}));
 await env.withSecurityRulesDisabled(async c=>{
  await updateDoc(doc(c.firestore(),`workspaces/${ws}/members/${target}`),{status:'REMOVED'});
  await setDoc(doc(c.firestore(),`workspaces/${ws}/deletedUsers/${target}`),{workspaceId:ws,uid:target,purgeState:'PURGE_PENDING',backupReady:false});
 });
 await assertFails(deleteDoc(doc(db('owner'),path('entries'))));
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),`workspaces/${ws}/deletedUsers/${target}`),{backupReady:true}));
 await assertSucceeds(deleteDoc(doc(db('owner'),path('entries'))));
});

// Server provenance, reference integrity, reversal atomicity, and per-workspace role regression.
for (const actor of ['partner','operator']) {
 test(`${actor} accepted limitation: backdated edit is allowed; direct Entry tombstone still denied`,async()=>{
  await seed('entries',entry(actor,{authorizationCreatedAt:Timestamp.fromMillis(now-72*3600000),createdAt:now-72*3600000}));
  const forged={actionTime:now-71*3600000,operationId:'forged-early-action'};
  await assertSucceeds(updateDoc(doc(db(actor),path('entries')),{...forged,totalAmount:60000,pendingAmount:60000}));
  await assertFails(updateDoc(doc(db(actor),path('entries')),{...forged,isDeleted:true,authorizationDeletedAt:serverTimestamp()}));
 });
 test(`${actor} original local creation timestamp must match and remains immutable`,async()=>{
  await assertFails(setDoc(doc(db(actor),path('entries')),entry(actor,{authorizationCreatedAt:Timestamp.fromMillis(now-2000)})));
  await seed('entries',entry(actor));
  await assertFails(updateDoc(doc(db(actor),path('entries')),{authorizationCreatedAt:Timestamp.fromMillis(now-1000),serverReceivedAt:serverTimestamp()}));
 });
 test(`${actor} legacy missing authorization mirror retains original createdAt authority`,async()=>{
  await seed('entries',entry(actor,{authorizationCreatedAt:null}));
  await assertSucceeds(updateDoc(doc(db(actor),path('entries')),{totalAmount:60000,pendingAmount:60000}));
  await assertFails(updateDoc(doc(db(actor),path('entries')),{authorizationCreatedAt:Timestamp.fromMillis(now-1000),serverReceivedAt:serverTimestamp()}));
 });
}
test('Owner retains authority over legacy and expired Entries',async()=>{
 await seed('entries',entry('partner',{authorizationCreatedAt:null}));
 await assertSucceeds(updateDoc(doc(db('owner'),path('entries')),{...envelope('owner'),totalAmount:60000,pendingAmount:60000}));
});
test('old business date does not expire a newly server-created ordinary Entry',async()=>{
 await assertSucceeds(setDoc(doc(db('partner'),path('entries')),entry('partner',{date:now-365*86400000,createdAt:now-1000})));
 await assertSucceeds(updateDoc(doc(db('partner'),path('entries')),{totalAmount:60000,pendingAmount:60000}));
});
for(const mode of ['missing','deleted','foreign-path','wrong-workspace']) for(const operation of ['create','update']) test(`${operation} rejects ${mode} Customer reference`,async()=>{
 const target=999;
 await env.withSecurityRulesDisabled(async c=>{
  const location=mode==='foreign-path'?`workspaces/workspace-b/customers/${target}`:path('customers',String(target));
  if(mode!=='missing')await setDoc(doc(c.firestore(),location),{workspaceId:mode==='wrong-workspace'?'workspace-b':mode==='foreign-path'?'workspace-b':ws,isDeleted:mode==='deleted'});
 });
 if(operation==='create')await assertFails(setDoc(doc(db('partner'),path('entries')),entry('partner',{customerId:target})));
 else {await seed('entries',entry());await assertFails(updateDoc(doc(db('partner'),path('entries')),{customerId:target}));}
});
test('Entry can change to an eligible same-workspace Customer',async()=>{
 await seed('customers',{workspaceId:ws,isDeleted:false},'3');await seed('entries',entry());
 await assertSucceeds(updateDoc(doc(db('partner'),path('entries')),{customerId:3}));
});
test('unchanged legacy Customer reference does not block unrelated Entry edit',async()=>{
 await seed('entries',entry('partner',{customerId:999}));
 await assertSucceeds(updateDoc(doc(db('partner'),path('entries')),{totalAmount:60000,pendingAmount:60000}));
});
function withdrawal(actor,target) {return {...envelope(actor),createdByUid:uid[actor],createdByRole:actor.toUpperCase(),createdAt:now-1000,authorizationCreatedAt:Timestamp.fromMillis(now-1000),serverReceivedAt:serverTimestamp(),targetPartnerUid:target,amount:100};}
for(const target of ['owner','partner','other']) test(`Owner creates Withdrawal for permitted ${target}`,()=>assertSucceeds(setDoc(doc(db('owner'),path('withdrawals')),withdrawal('owner',uid[target]))));
for(const target of ['operator','outsider']) test(`Owner cannot create Withdrawal for ${target}`,()=>assertFails(setDoc(doc(db('owner'),path('withdrawals')),withdrawal('owner',uid[target]))));
test('Partner creates Withdrawal for self',()=>assertSucceeds(setDoc(doc(db('partner'),path('withdrawals')),withdrawal('partner',uid.partner))));
for(const target of ['owner','other','operator'])test(`Partner cannot create Withdrawal for ${target}`,()=>assertFails(setDoc(doc(db('partner'),path('withdrawals')),withdrawal('partner',uid[target]))));
for(const status of ['INACTIVE','REMOVED'])test(`Withdrawal rejects ${status} target and caller`,async()=>{
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),`workspaces/${ws}/members/${uid.partner}`),{status}));
 await assertFails(setDoc(doc(db('owner'),path('withdrawals')),withdrawal('owner',uid.partner)));
 await assertFails(setDoc(doc(db('partner'),path('withdrawals')),withdrawal('partner',uid.partner)));
});
test('Withdrawal rejects unknown role target',async()=>{
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),`workspaces/${ws}/members/${uid.other}`),{role:'Co-Owner'}));
 await assertFails(setDoc(doc(db('owner'),path('withdrawals')),withdrawal('owner',uid.other)));
});
test('Withdrawal rejects target belonging only to another workspace',async()=>{
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),'workspaces/workspace-b/members/foreign-partner'),{workspaceId:'workspace-b',role:'PARTNER',status:'ACTIVE'}));
 await assertFails(setDoc(doc(db('owner'),path('withdrawals')),withdrawal('owner','foreign-partner')));
});
for(const kind of ['expenses','withdrawals','collections'])test(`Operator cannot create ${kind}`,()=>assertFails(setDoc(doc(db('operator'),path(kind)),withdrawal('operator',uid.operator))));
async function reversalFixture(age=1000) {
 const initial=entry('owner');await seed('entries',initial);
 const payment=await collect('partner','30',30000,initial);await assertSucceeds(payment.commit());
 if(age!==1000) await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),path('collections','30')),{authorizationCreatedAt:Timestamp.fromMillis(now-age),createdAt:now-age}));
 const original=(await getDoc(doc(db('owner'),path('collections','30')))).data();
 const before=(await getDoc(doc(db('owner'),path('entries')))).data();
 return {original,before};
}
async function rejectedReversal(actor,changes={},age=1000) {
 const {original,before}=await reversalFixture(age);
 const database=db(actor),batch=writeBatch(database),operationId='reversal-negative';
 batch.update(doc(database,path('collections','30')),{...envelope(actor),operationId,status:'REVERSED',reversedByUid:uid[actor],reversedAt:now,...changes});
 batch.update(doc(database,path('entries')),{...envelope(actor),operationId,collectionId:'30',amountReceived:before.amountReceived-30000,pendingAmount:before.pendingAmount+30000});
 await assertFails(batch.commit());
 const unchanged=(await getDoc(doc(db('owner'),path('entries')))).data();
 assert.equal(unchanged.pendingAmount,before.pendingAmount);assert.equal(unchanged.amountReceived,before.amountReceived);
 const retained=(await getDoc(doc(db('owner'),path('collections','30')))).data();
 assert.equal(retained.status,'ACTIVE');assert.equal(retained.allocationsJson,original.allocationsJson);
}
for(const actor of ['other','operator','outsider'])test(`${actor} cannot reverse Partner collection and failed batch preserves due`,()=>rejectedReversal(actor));
test('accepted limitation: Partner can backdate reversal of own expired Collection',async()=>{
 const {original,before}=await reversalFixture(72*3600000); const command=await collect('partner','30',30000,before,'REVERSED',{...original,actionTime:now-71*3600000});
 // collect's envelope preserves the original explicit action time.
 await assertSucceeds(command.commit());
});
test('forged reversedByUid is denied atomically',()=>rejectedReversal('partner',{reversedByUid:uid.owner}));
for(const [field,value]of [['amount',1],['customerId',999],['jobEntryId',999],['allocationsJson','[]'],['collectedByUid',uid.owner],['authorizationCreatedAt',serverTimestamp()]])test(`reversal cannot change immutable ${field}`,()=>rejectedReversal('partner',{[field]:value}));
test('repeated reversal is denied without a second financial effect',async()=>{
 const {original,before}=await reversalFixture();const reverse=await collect('partner','30',30000,before,'REVERSED',original);await assertSucceeds(reverse.commit());
 const after=(await getDoc(doc(db('owner'),path('entries')))).data();
 const again=await collect('partner','30',30000,after,'REVERSED',original);await assertFails(again.commit());
 assert.equal((await getDoc(doc(db('owner'),path('entries')))).data().pendingAmount,after.pendingAmount);
});
test('Owner may reverse another member expired Collection',async()=>{
 const {original,before}=await reversalFixture(72*3600000);const reverse=await collect('owner','30',30000,before,'REVERSED',original);await assertSucceeds(reverse.commit());
});
async function secondWorkspace(role) {
 await env.withSecurityRulesDisabled(async c=>{
 const database=c.firestore();await setDoc(doc(database,'workspaces/workspace-b'),{workspaceId:'workspace-b',ownerUid:uid.other});
 await setDoc(doc(database,`workspaces/workspace-b/members/${uid.owner}`),{uid:uid.owner,workspaceId:'workspace-b',role,status:'ACTIVE'});
 await setDoc(doc(database,'workspaces/workspace-b/customers/2'),{workspaceId:'workspace-b',isDeleted:false});
 await setDoc(doc(database,'workspaces/workspace-b/collections/1'),{workspaceId:'workspace-b'});
 await setDoc(doc(database,'workspaces/workspace-b/extensions/1'),{workspaceId:'workspace-b'});
 });
}
test('same UID Owner in A and Operator in B retains only B Operator authority',async()=>{
 await secondWorkspace('OPERATOR');const database=db('owner');
 await assertSucceeds(getDoc(doc(database,path('collections'))));
 await assertFails(getDoc(doc(database,'workspaces/workspace-b/collections/1')));
 await assertSucceeds(getDoc(doc(database,'workspaces/workspace-b/extensions/1')));
 const ordinary={...entry('owner'),workspaceId:'workspace-b'};
 await assertSucceeds(setDoc(doc(database,'workspaces/workspace-b/entries/1'),ordinary));
 await assertFails(setDoc(doc(database,'workspaces/workspace-b/entries/2'),{...ordinary,isHistorical:true}));
 await assertFails(setDoc(doc(database,'workspaces/workspace-b/withdrawals/1'),{...withdrawal('owner',uid.owner),workspaceId:'workspace-b'}));
});
test('same UID Owner in A and Partner in B cannot use Owner targets in B',async()=>{
 await secondWorkspace('PARTNER');const database=db('owner');
 await assertSucceeds(getDoc(doc(database,'workspaces/workspace-b/collections/1')));
 await assertFails(setDoc(doc(database,'workspaces/workspace-b/entries/1'),{...entry('owner'),workspaceId:'workspace-b',entryForUid:uid.other,financialAttributionUid:uid.other}));
 await assertSucceeds(setDoc(doc(database,'workspaces/workspace-b/withdrawals/1'),{...withdrawal('owner',uid.owner),workspaceId:'workspace-b'}));
});
for(const status of ['INACTIVE','REMOVED']) test(`${status} collector cannot reverse its Collection`,async()=>{
 const {before}=await reversalFixture();
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),`workspaces/${ws}/members/${uid.partner}`),{status}));
 const database=db('partner'),batch=writeBatch(database),operationId='revoked-reversal';
 batch.update(doc(database,path('collections','30')),{...envelope(),operationId,status:'REVERSED',reversedByUid:uid.partner});
 batch.update(doc(database,path('entries')),{...envelope(),operationId,collectionId:'30',amountReceived:0,pendingAmount:50000});
 await assertFails(batch.commit());assert.equal((await getDoc(doc(db('owner'),path('entries')))).data().pendingAmount,before.pendingAmount);
});
test('unknown-role caller cannot create Withdrawal',async()=>{
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),`workspaces/${ws}/members/${uid.partner}`),{role:'Co-Owner'}));
 await assertFails(setDoc(doc(db('partner'),path('withdrawals')),withdrawal('partner',uid.partner)));
});
test('exact original-creation/action-time boundary is excluded',async()=>{
 await seed('entries',entry('partner',{createdAt:now-24*3600000,authorizationCreatedAt:Timestamp.fromMillis(now-24*3600000)}));
 await assertFails(updateDoc(doc(db('partner'),path('entries')),{totalAmount:60000,pendingAmount:60000}));
});
test('legacy server receipt time does not replace immutable original local creation',async()=>{
 await seed('entries',entry('partner',{createdAt:now-1000,authorizationCreatedAt:Timestamp.fromMillis(now-72*3600000)}));
 await assertSucceeds(updateDoc(doc(db('partner'),path('entries')),{totalAmount:60000,pendingAmount:60000}));
});
test('backdated delete authorization evidence cannot be forged',async()=>{
 await seed('entries',entry());
 await assertFails(updateDoc(doc(db('partner'),path('entries')),{isDeleted:true,authorizationDeletedAt:Timestamp.fromMillis(now-1000)}));
});
test('full document queue snapshot preserves immutable server time',async()=>{
 await seed('entries',entry());const snapshot=(await getDoc(doc(db('partner'),path('entries')))).data();
 await assertSucceeds(setDoc(doc(db('partner'),path('entries')),{...snapshot,totalAmount:60000,pendingAmount:60000}));
 assert.deepEqual((await getDoc(doc(db('partner'),path('entries')))).data().authorizationCreatedAt,snapshot.authorizationCreatedAt);
});

for(const [field,value] of [['createdAt',now],['collectedAt',now],['collectedByName','forged'],['collectedByRole','OWNER'],['id',999],['workspaceId','workspace-b']])
 test(`reversal preserves immutable ${field} and financial balances`,()=>rejectedReversal('partner',{[field]:value}));
for(const kind of ['expenses','withdrawals']) test(`accepted limitation: backdated own ${kind} deletion is allowed`,async()=>{
 const record={...withdrawal('partner',uid.partner),createdAt:now-72*3600000,authorizationCreatedAt:Timestamp.fromMillis(now-72*3600000),actionTime:now-71*3600000,expenseForUid:uid.partner,financialAttributionUid:uid.partner};
 await seed(kind,record);
 if(kind==='expenses')await assertSucceeds(updateDoc(doc(db('partner'),path(kind)),{amount:200}));
 await assertSucceeds(updateDoc(doc(db('partner'),path(kind)),{isDeleted:true,authorizationDeletedAt:serverTimestamp()}));
});
test('legacy Collection uses immutable original creation, not absent server receipt',async()=>{
 const {before}=await reversalFixture();
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),path('collections','30')),{authorizationCreatedAt:null}));
 const database=db('partner'),batch=writeBatch(database),operationId='legacy-reversal';
 batch.update(doc(database,path('collections','30')),{...envelope(),operationId,status:'REVERSED',reversedByUid:uid.partner});
 batch.update(doc(database,path('entries')),{...envelope(),operationId,collectionId:'30',amountReceived:0,pendingAmount:50000});
 await assertSucceeds(batch.commit());assert.equal((await getDoc(doc(db('owner'),path('entries')))).data().pendingAmount,50000);
});
test('Owner authority in A cannot edit another creator ordinary Entry while Operator in B',async()=>{
 await secondWorkspace('OPERATOR');
 await env.withSecurityRulesDisabled(async c=>{
  await setDoc(doc(c.firestore(),`workspaces/workspace-b/members/${uid.other}`),{uid:uid.other,workspaceId:'workspace-b',role:'OWNER',status:'ACTIVE'});
  await setDoc(doc(c.firestore(),'workspaces/workspace-b/entries/9'),{...entry('other'),workspaceId:'workspace-b',authorizationCreatedAt:Timestamp.fromMillis(now-1000)});
 });
 await assertFails(updateDoc(doc(db('owner'),'workspaces/workspace-b/entries/9'),{...envelope('owner'),workspaceId:'workspace-b',totalAmount:60000,pendingAmount:60000}));
});

for(const paid of [20000,50000]) test(`Delete Due cannot tombstone a paid Entry (${paid})`,async()=>{
 await seed('entries',entry('partner',{amountReceived:paid,pendingAmount:50000-paid}));
 await assertFails(updateDoc(doc(db('partner'),path('entries')),{isDeleted:true,authorizationDeletedAt:serverTimestamp()}));
 const snapshot=await getDoc(doc(db('partner'),path('entries')));assert.equal(snapshot.data().amountReceived,paid);
});
test('independent split reversals preserve history and require checked Delete Due',async()=>{
 await seed('entries',entry('owner'));
 let first=await collect('owner','20',20000,{amountReceived:0,pendingAmount:50000});await assertSucceeds(first.commit());
 let second=await collect('partner','30',30000,{amountReceived:20000,pendingAmount:30000});await assertSucceeds(second.commit());
 await assertFails(updateDoc(doc(db('owner'),path('entries')),{isDeleted:true,authorizationDeletedAt:serverTimestamp()}));
 let reverse=await collect('partner','30',30000,{amountReceived:50000,pendingAmount:0},'REVERSED',second.collection);await assertSucceeds(reverse.commit());
 assert.equal((await getDoc(doc(db('owner'),path('collections','20')))).data().status,'ACTIVE');
 reverse=await collect('owner','20',20000,{amountReceived:20000,pendingAmount:30000},'REVERSED',first.collection);await assertSucceeds(reverse.commit());
 await assertFails(updateDoc(doc(db('owner'),path('entries')),{isDeleted:true,authorizationDeletedAt:serverTimestamp()}));
 await assertFails(deleteDoc(doc(db('owner'),path('entries'))));
 assert.equal((await getDoc(doc(db('owner'),path('entries')))).data().pendingAmount,50000);
 for(const id of ['20','30']) assert.equal((await getDoc(doc(db('owner'),path('collections',id)))).data().status,'REVERSED');
 assert.equal((await getDoc(doc(db('owner'),path('customers','2')))).exists(),true);
});

for(const actor of ['owner','partner','operator']) test(`${actor} cannot forge or alter trusted Entry deletion receipts`,async()=>{
 const receipt=doc(db(actor),path('entryDeleteReceipts','forged-operation'));
 await assertFails(setDoc(receipt,{workspaceId:ws,entryId:'1',actorUid:uid[actor],operationId:'forged-operation',deletedAt:serverTimestamp()}));
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),path('entryDeleteReceipts','forged-operation')),{workspaceId:ws,entryId:'1',actorUid:uid[actor],operationId:'forged-operation',deletedAt:Timestamp.now()}));
 await assertFails(updateDoc(receipt,{entryId:'2'}));await assertFails(deleteDoc(receipt));
});

// Approved offline arrival-independent policy: hour 0 / 18 / 26 / 30.
for (const field of ['totalAmount','customerName']) test('offline 0/18/26/30 retains A when rejected B changes '+field,async()=>{
 const origin=now-30*3600000, database=db('partner'),ref=doc(database,path('entries'));
 await assertSucceeds(setDoc(ref,entry('partner',{createdAt:origin,authorizationCreatedAt:Timestamp.fromMillis(origin),actionTime:origin,operationId:'offline-create',customerName:'original'})));
 await assertSucceeds(updateDoc(ref,{actionTime:origin+18*3600000,operationId:'offline-A',totalAmount:60000,pendingAmount:60000}));
 const B=field==='totalAmount'?{totalAmount:70000,pendingAmount:70000}:{customerName:'expired B'};
 await assertFails(updateDoc(ref,{actionTime:origin+26*3600000,operationId:'offline-B',...B}));
 const result=(await getDoc(ref)).data();assert.equal(result.createdAt,origin);assert.equal(result.authorizationCreatedAt.toMillis(),origin);assert.ok(result.serverReceivedAt.toMillis()>=now);assert.equal(result.totalAmount,60000);assert.equal(result.pendingAmount,60000);assert.equal(result.customerName,'original');assert.equal(result.operationId,'offline-A');
});
for(const offset of [-1,0,86399999,86400000])test('operation boundary '+offset,async()=>{
 const origin=now-30*3600000;await seed('entries',entry('partner',{createdAt:origin}));
 const command=updateDoc(doc(db('partner'),path('entries')),{actionTime:origin+offset,totalAmount:60000,pendingAmount:60000});
 await (offset>=0&&offset<86400000?assertSucceeds(command):assertFails(command));
});
test('late offline creation cannot reset original deadline on upload',async()=>{
 const origin=now-30*3600000;await assertFails(setDoc(doc(db('partner'),path('entries')),entry('partner',{createdAt:origin,authorizationCreatedAt:Timestamp.fromMillis(now-1000),actionTime:origin})));
});

test('old write without protocol version is denied',async()=>{const data=entry();delete data.protocolVersion;await assertFails(setDoc(doc(db('partner'),path('entries')),data));});
test('unreviewed legacy time restricts Partner but preserves Owner edit authority',async()=>{const data=entry();delete data.authorizationTimeStatus;await seed('entries',data);await assertFails(updateDoc(doc(db('partner'),path('entries')),{totalAmount:60000,pendingAmount:60000}));const update={...envelope('owner'),totalAmount:60000,pendingAmount:60000};delete update.authorizationTimeStatus;await assertSucceeds(updateDoc(doc(db('owner'),path('entries')),update));});

// Selected collector and India-calendar validation: recorder authentication is never substituted.
test('Owner records for Partner while recorder and financial delta remain Owner-authenticated', async()=>{
 const source=entry('owner');await seed('entries',source);
 const op=await collect('owner','91',100,source,'ACTIVE',{collectorUid:uid.partner,collectorName:'',collectorRole:'PARTNER'});
 await assertSucceeds(op.commit());
 const stored=(await getDoc(doc(db('owner'),path('collections','91')))).data();
 assert.equal(stored.collectorUid,uid.partner);assert.equal(stored.collectedByUid,uid.owner);assert.equal(stored.actorUid,uid.owner);
 assert.equal((await getDoc(doc(db('owner'),path('entries')))).data().pendingAmount,49900);
});
for(const selected of ['other','operator','outsider']) test(`Partner cannot attribute collection to ${selected}`,async()=>{
 const source=entry('partner');await seed('entries',source);
 const op=await collect('partner','92',100,source,'ACTIVE',{collectorUid:uid[selected],collectorName:'',collectorRole:selected==='operator'?'OPERATOR':'PARTNER'});
 await assertFails(op.commit());
 assert.equal((await getDoc(doc(db('owner'),path('entries')))).data().pendingAmount,50000);
 assert.equal((await getDoc(doc(db('owner'),path('collections','92')))).exists(),false);
});
test('Owner cannot select an inactive or wrong-workspace collector',async()=>{
 const source=entry('owner');await seed('entries',source);
 await env.withSecurityRulesDisabled(async c=>{
  await setDoc(doc(c.firestore(),path('members',uid.partner)),{uid:uid.partner,workspaceId:ws,role:'PARTNER',status:'ARCHIVED'});
  await setDoc(doc(c.firestore(),'workspaces/foreign/members/foreign-partner'),{uid:'foreign-partner',workspaceId:'foreign',role:'PARTNER',status:'ACTIVE'});
 });
 for(const selected of [uid.partner,'foreign-partner']) {
  const op=await collect('owner','93',100,source,'ACTIVE',{collectorUid:selected,collectorName:'',collectorRole:'PARTNER'});
  await assertFails(op.commit());
 }
});
for(const [label,date,allowed] of [
 ['same India day earlier hour','2026-02-01T18:30:00Z',true],
 ['later India day','2026-02-03T00:00:00Z',true],
 ['previous India day','2026-02-01T18:29:59Z',false]
]) test(`Collection date ${label}`,async()=>{
 const source=entry('owner',{createdAt:Date.parse('2026-02-02T17:00:00Z')});await seed('entries',source);
 const op=await collect('owner','94',100,source,'ACTIVE',{collectedAt:Date.parse(date)});
 if(allowed) await assertSucceeds(op.commit());else {
  await assertFails(op.commit());assert.equal((await getDoc(doc(db('owner'),path('entries')))).data().pendingAmount,50000);
 }
});
test('missing source creation time blocks new collection without mutating balances',async()=>{
 const source=entry('owner',{createdAt:0});await seed('entries',source);
 const op=await collect('owner','95',100,source);await assertFails(op.commit());
 assert.equal((await getDoc(doc(db('owner'),path('entries')))).data().pendingAmount,50000);
});
test('selected collector cannot be replaced during reversal',async()=>{
 const source=entry('owner');await seed('entries',source);
 const create=await collect('owner','96',100,source,'ACTIVE',{collectorUid:uid.partner,collectorName:'',collectorRole:'PARTNER'});await assertSucceeds(create.commit());
 const after={...source,amountReceived:100,pendingAmount:49900};
 const bad=await collect('owner','96',100,after,'REVERSED',{...create.collection,collectorUid:uid.other});await assertFails(bad.commit());
 const good=await collect('owner','96',100,after,'REVERSED',create.collection);await assertSucceeds(good.commit());
});
