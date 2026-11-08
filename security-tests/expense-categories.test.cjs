const fs=require('node:fs'), path=require('node:path'), test=require('node:test'), assert=require('node:assert/strict');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,updateDoc,getDoc,deleteDoc,writeBatch,runTransaction}=require('firebase/firestore');
if(process.env.GCLOUD_PROJECT!=='demo-aidhunt-v16'||process.env.FIRESTORE_EMULATOR_HOST!=='127.0.0.1:18180'||process.env.GOOGLE_APPLICATION_CREDENTIALS)
  throw Error('Local synthetic emulator only');
let env; const ws='expense-category-tests', now=Date.now()-2000;
const db=actor=>env.authenticatedContext(actor).firestore();
const ref=(database,id='1')=>doc(database,`workspaces/${ws}/expenseCategories/${id}`);
const command=(actor,op)=>({workspaceId:ws,protocolVersion:2,operationType:'UPSERT',dependencies:[],operationId:op,actorUid:actor,actionTime:now,isDeleted:false});
const category=actor=>({...command(actor,'create-'+actor),id:1,name:'Engine Oil',nameKey:'engine oil',createdAt:now,createdByUid:actor,createdByRole:actor==='owner'?'OWNER':'PARTNER'});
test.before(async()=>{env=await initializeTestEnvironment({projectId:'demo-aidhunt-v16',firestore:{host:'127.0.0.1',port:18180,rules:fs.readFileSync(path.join(__dirname,'../firestore.rules'),'utf8')}});});
test.after(async()=>env?.cleanup());
test.beforeEach(async()=>{await env.clearFirestore();await env.withSecurityRulesDisabled(async context=>{
  const d=context.firestore();await setDoc(doc(d,`workspaces/${ws}`),{workspaceId:ws,ownerUid:'owner',syncCapabilities:{expenseCategoriesV1:true}});
  for(const [uid,role,status] of [['owner','OWNER','ACTIVE'],['partner','PARTNER','ACTIVE'],['operator','OPERATOR','ACTIVE'],['removed','PARTNER','REMOVED'],['fakeOwner','OWNER','ACTIVE']])
    await setDoc(doc(d,`workspaces/${ws}/members/${uid}`),{workspaceId:ws,uid,role,status});
});});

test('disabled categories cannot be read, created or acknowledged by an ordinary client',async()=>{
 await assertSucceeds(setDoc(ref(db('owner')),category('owner')));
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),`workspaces/${ws}`),{syncCapabilities:{}}));
 await assertFails(getDoc(ref(db('owner'))));
 await assertFails(setDoc(ref(db('partner'),'2'),{...category('partner'),id:2}));
 await assertFails(updateDoc(ref(db('owner')),command('owner','later-ack')));
 await env.withSecurityRulesDisabled(async c=>{
  const kept=(await getDoc(ref(c.firestore()))).data();assert.equal(kept.operationId,'create-owner');assert.equal(kept.name,'Engine Oil');
 });
});
for(const actor of ['owner','partner']) test(`${actor}: valid category is committed once with original identity`,async()=>{
  const value=category(actor),database=db(actor);await assertSucceeds(setDoc(ref(database),value));
  await assertSucceeds(setDoc(ref(database),value));assert.deepEqual((await getDoc(ref(database))).data(),value);
});
for(const [actor,role] of [['owner','Owner'],['partner','Partner']]) test(`${actor}: actual Android normalized role is accepted without expanding authority`,async()=>{
  const value={...category(actor),createdByRole:role};await assertSucceeds(setDoc(ref(db(actor)),value));
  assert.equal((await getDoc(ref(db(actor)))).data().createdByRole,role);
});
test('equivalent concurrent additions preserve first display/creator and acknowledge both operations',async()=>{
  await setDoc(ref(db('owner')),category('owner'));
  const acknowledge=(actor,op)=>{const database=db(actor);return runTransaction(database,async tx=>{
    const target=ref(database);const snapshot=await tx.get(target);assert.equal(snapshot.data().nameKey,'engine oil');
    tx.update(target,{operationId:op,actorUid:actor,actionTime:now});
  });};
  await Promise.all([acknowledge('partner','duplicate-partner'),acknowledge('owner','duplicate-owner')]);
  const value=(await getDoc(ref(db('owner')))).data();
  assert.equal(value.name,'Engine Oil');assert.equal(value.nameKey,'engine oil');assert.equal(value.createdByUid,'owner');
  assert.equal(value.createdAt,now);assert.ok(['duplicate-partner','duplicate-owner'].includes(value.operationId));
});
for(const actor of ['operator','removed','outsider','fakeOwner']) test(`${actor}: cannot create or acknowledge categories`,async()=>{
  await assertFails(setDoc(ref(db(actor)),category(actor)));
  await env.withSecurityRulesDisabled(async c=>setDoc(ref(c.firestore()),category('owner')));
  await assertFails(updateDoc(ref(db(actor)),{operationId:'forged-ack',actorUid:actor,actionTime:now}));
});
test('active Operators can read choices but outsiders and removed members cannot',async()=>{
  await setDoc(ref(db('owner')),category('owner'));await assertSucceeds(getDoc(ref(db('operator'))));
  for(const actor of ['outsider','removed']) await assertFails(getDoc(ref(db(actor))));
});
for(const patch of [{workspaceId:'foreign'},{actorUid:'owner'},{id:2},{name:''},{nameKey:''},{createdByUid:'owner'},
  {createdByRole:'OWNER'},{createdAt:now-1},{actionTime:Date.now()+86400000},{amount:2000}])
  test(`invalid Partner category rejected: ${Object.keys(patch).join()}`,async()=>
    assertFails(setDoc(ref(db('partner')),{...category('partner'),...patch})));
test('category provenance/name/identity cannot be rewritten or physically deleted, even by Owner',async()=>{
  await setDoc(ref(db('owner')),category('owner'));
  for(const actor of ['owner','partner']) {
    for(const patch of [{name:'Different'},{nameKey:'different'},{createdByUid:'partner'},{createdAt:now+1},{id:2},{isDeleted:true}])
      await assertFails(updateDoc(ref(db(actor)),{...command(actor,'edit'),...patch}));
    await assertFails(deleteDoc(ref(db(actor))));
  }
  assert.deepEqual((await getDoc(ref(db('owner')))).data(),category('owner'));
});
test('category creation does not authorize a financial write in another workspace; whole batch rolls back',async()=>{
  const database=db('partner'),batch=writeBatch(database);
  batch.set(ref(database),category('partner'));
  batch.set(doc(database,'workspaces/foreign/expenses/2'),{workspaceId:'foreign',amount:1});
  await assertFails(batch.commit());assert.equal((await getDoc(ref(db('owner')))).exists(),false);
});
test('legacy role capitalization still writes the canonical immutable role',async()=>{
  await env.withSecurityRulesDisabled(async c=>updateDoc(doc(c.firestore(),`workspaces/${ws}/members/partner`),{role:'Partner'}));
  await assertSucceeds(setDoc(ref(db('partner')),category('partner')));
});
