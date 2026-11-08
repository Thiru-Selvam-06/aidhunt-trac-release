const fs=require('node:fs'),path=require('node:path'),test=require('node:test'),assert=require('node:assert/strict');
const {initializeTestEnvironment,assertSucceeds,assertFails}=require('@firebase/rules-unit-testing');
const {doc,setDoc,getDoc,updateDoc,writeBatch}=require('firebase/firestore');
let env;const ws='operator-profile-workspace',now=Date.now()-1000;
const database=actor=>env.authenticatedContext(actor).firestore();
const ref=(db,collection,id=7)=>doc(db,`workspaces/${ws}/${collection}/${id}`);
const command=(actor,operationId='create-basic')=>({workspaceId:ws,actorUid:actor,operationId,actionTime:now,
 protocolVersion:2,operationType:'UPSERT',dependencies:[],isDeleted:false});
const customer=(actor='operator')=>({...command(actor),id:7,name:'தமிழ் Customer ?',phone:'',location:'Raw location',
 createdByUid:actor,createdBy:null,createdAt:now,updatedAt:now,totalBilled:0,totalPaid:0,balanceDue:0});
const projection=(source=customer())=>({...command(source.actorUid,source.operationId),id:source.id,name:source.name,
 phone:source.phone,location:source.location,createdByUid:source.createdByUid,createdAt:source.createdAt,
 updatedAt:source.updatedAt,creationOperationId:source.operationId});
const create=(actor='operator',source=customer(actor),basic=projection(source))=>{
 const db=database(actor),batch=writeBatch(db);batch.set(ref(db,'customers'),source);batch.set(ref(db,'customerDirectory'),basic);return batch.commit();
};
test.before(async()=>{env=await initializeTestEnvironment({projectId:'demo-aidhunt-v16',firestore:{host:'127.0.0.1',port:18180,
 rules:fs.readFileSync(path.join(__dirname,'../firestore.rules'),'utf8')}});});
test.after(async()=>env?.cleanup());
test.beforeEach(async()=>{await env.clearFirestore();await env.withSecurityRulesDisabled(async c=>{
 const db=c.firestore();await setDoc(doc(db,`workspaces/${ws}`),{workspaceId:ws,ownerUid:'owner',syncCapabilities:{basicCustomerProfilesV1:true}});
 for(const [uid,role,status] of [['owner','Owner','ACTIVE'],['partner','Partner','ACTIVE'],['operator','Operator','ACTIVE'],['removed','Operator','REMOVED'],['fakeOwner','Owner','ACTIVE']])
  await setDoc(doc(db,`workspaces/${ws}/members/${uid}`),{uid,workspaceId:ws,role,status});
});});

test('Operator creates an atomic zero-finance profile and basic directory; reads no canonical financial profile',async()=>{
 await assertSucceeds(create());
 const basic=(await getDoc(ref(database('operator'),'customerDirectory'))).data();
 assert.equal(basic.name,'தமிழ் Customer ?');assert.equal(basic.creationOperationId,'create-basic');
 assert.ok(!Object.hasOwn(basic,'totalPaid'));assert.ok(!Object.hasOwn(basic,'balanceDue'));
 const source=(await getDoc(ref(database('owner'),'customers'))).data();
 assert.equal(source.totalBilled,0);assert.equal(source.totalPaid,0);assert.equal(source.balanceDue,0);
 assert.equal(source.createdByUid,'operator');assert.equal(source.createdAt,now);
 await assertFails(getDoc(ref(database('operator'),'customers')));
 await assertFails(getDoc(ref(database('operator'),'collections')));
});

test('missing activation preserves legacy finance profiles while new collections remain inaccessible',async()=>{
 await env.withSecurityRulesDisabled(c=>setDoc(doc(c.firestore(),`workspaces/${ws}`),{workspaceId:ws,ownerUid:'owner'}));
 const db=database('owner'),source={...customer('owner'),id:9};
 await assertSucceeds(setDoc(ref(db,'customers',9),source));
 await assertSucceeds(updateDoc(ref(db,'customers',9),{...command('owner','legacy-edit'),name:'Legacy raw name'}));
 assert.equal((await getDoc(ref(db,'customers',9))).data().name,'Legacy raw name');
 await assertFails(getDoc(ref(db,'customerDirectory')));
 await assertFails(create('operator'));
 assert.equal((await getDoc(ref(db,'customers'))).exists(),false);
});
test('ordinary clients cannot activate or remove workspace protocol capabilities',async()=>{
 for(const actor of ['owner','partner','operator']) {
  await assertFails(updateDoc(doc(database(actor),`workspaces/${ws}`),{syncCapabilities:{basicCustomerProfilesV1:true,expenseCategoriesV1:true}}));
  await assertFails(updateDoc(doc(database(actor),`workspaces/${ws}`),{syncCapabilities:{}}));
 }
 await assertSucceeds(updateDoc(doc(database('owner'),`workspaces/${ws}`),{name:'Owner settings still work'}));
});
test('activation refuses an old client customer create without an atomic metadata projection',async()=>{
 await assertFails(setDoc(ref(database('owner'),'customers'),customer('owner')));
 assert.equal((await getDoc(ref(database('owner'),'customers'))).exists(),false);
 await assertSucceeds(create('owner'));
});
test('Operator source without directory and directory without source are both denied',async()=>{
 await assertFails(setDoc(ref(database('operator'),'customers'),customer()));
 await assertFails(setDoc(ref(database('operator'),'customerDirectory'),projection()));
 assert.equal((await getDoc(ref(database('owner'),'customers'))).exists(),false);
});
for(const patch of [{totalBilled:1},{totalPaid:1},{balanceDue:1},{amount:1},{allocationsJson:'[]'},
 {createdByUid:'owner'},{workspaceId:'foreign'},{name:''},{phone:'123'}, {updatedAt:now-1}])
 test('invalid Operator basic profile denied atomically: '+Object.keys(patch).join(),async()=>{
  const source={...customer(),...patch};await assertFails(create('operator',source,projection(source)));
  assert.equal((await getDoc(ref(database('owner'),'customers'))).exists(),false);
  assert.equal((await getDoc(ref(database('owner'),'customerDirectory'))).exists(),false);
 });
for(const patch of [{totalPaid:0},{amount:0},{createdByUid:'owner'},{name:'Different name'},
 {creationOperationId:'different-command'},{workspaceId:'foreign'}])
 test('invalid directory cannot accompany a valid source: '+Object.keys(patch).join(),async()=>{
  await assertFails(create('operator',customer(),{...projection(),...patch}));
  assert.equal((await getDoc(ref(database('owner'),'customers'))).exists(),false);
 });
for(const actor of ['removed','outsider','fakeOwner']) test(actor+' cannot create or read basic profiles',async()=>{
 await assertFails(create(actor));await assertFails(getDoc(ref(database(actor),'customerDirectory')));
});
test('Operator cannot overwrite an existing canonical source or edit directory metadata',async()=>{
 await create();
 await assertFails(create('operator',{...customer(),name:'Overwritten',operationId:'another-create'},
  {...projection(),name:'Overwritten',operationId:'another-create',creationOperationId:'another-create'}));
 await assertFails(updateDoc(ref(database('operator'),'customerDirectory'),{name:'Changed by Operator'}));
 assert.equal((await getDoc(ref(database('owner'),'customers'))).data().name,customer().name);
});
for(const actor of ['owner','partner']) test(actor+' mirrors a profile edit while keeping original creation receipt',async()=>{
 await create();const db=database(actor),batch=writeBatch(db),next={...command(actor,'profile-edit'),name:'Updated raw name',updatedAt:now+1};
 batch.update(ref(db,'customers'),next);batch.update(ref(db,'customerDirectory'),next);
 await assertSucceeds(batch.commit());
 const basic=(await getDoc(ref(database('operator'),'customerDirectory'))).data();
 assert.equal(basic.name,'Updated raw name');assert.equal(basic.createdByUid,'operator');
 assert.equal(basic.creationOperationId,'create-basic');assert.equal(basic.createdAt,now);
 await assertFails(updateDoc(ref(db,'customers'),{...next,name:'Unmirrored'}));
 await assertFails(updateDoc(ref(db,'customerDirectory'),{...next,creationOperationId:'rewritten-receipt'}));
});
test('checked Owner deletion must remove the basic choice atomically with source and immutable proof',async()=>{
 await create();const db=database('owner');
 const proof={workspaceId:ws,recordId:'7',actorUid:'owner',operationId:'checked-delete',actionTime:now,
  protocolVersion:2,sourceCreatedAt:now};
 let batch=writeBatch(db);batch.delete(ref(db,'customers'));batch.set(ref(db,'customerDeletionProofs'),proof);
 await assertFails(batch.commit());
 assert.equal((await getDoc(ref(db,'customerDirectory'))).exists(),true);
 batch=writeBatch(db);batch.delete(ref(db,'customers'));batch.delete(ref(db,'customerDirectory'));
 batch.set(ref(db,'customerDeletionProofs'),proof);await assertSucceeds(batch.commit());
 assert.equal((await getDoc(ref(database('operator'),'customerDirectory'))).exists(),false);
 assert.equal((await getDoc(ref(db,'customers'))).exists(),false);
 assert.equal((await getDoc(ref(db,'customerDeletionProofs'))).data().operationId,'checked-delete');
});
test('directory-only deletion cannot hide a live customer and creator cannot delete the source',async()=>{
 await create();
 for(const actor of ['owner','partner','operator']) {
  const db=database(actor),batch=writeBatch(db);batch.delete(ref(db,'customerDirectory'));await assertFails(batch.commit());
 }
 const db=database('operator'),batch=writeBatch(db);batch.delete(ref(db,'customers'));batch.delete(ref(db,'customerDirectory'));
 await assertFails(batch.commit());assert.equal((await getDoc(ref(database('owner'),'customers'))).exists(),true);
});
test('legacy missing identity timestamps stay unknown while metadata mirrors without copying balances',async()=>{
 const original={...customer('owner'),createdByUid:null,createdAt:null,updatedAt:null,phone:null,location:null,
  totalBilled:1234.56,totalPaid:100.25,balanceDue:1134.31};
 await env.withSecurityRulesDisabled(c=>setDoc(ref(c.firestore(),'customers'),original));
 const db=database('owner'),batch=writeBatch(db),next={...command('owner','legacy-profile-edit'),phone:'9876543210',updatedAt:now};
 batch.update(ref(db,'customers'),next);
 batch.set(ref(db,'customerDirectory'),{...projection(),...command('owner','legacy-profile-edit'),name:original.name,
  createdByUid:'',createdAt:0,updatedAt:now,phone:'9876543210',location:'',creationOperationId:'legacy-profile-edit'});
 await assertSucceeds(batch.commit());const source=(await getDoc(ref(db,'customers'))).data();
 assert.equal(source.createdByUid,null);assert.equal(source.createdAt,null);
 assert.equal(source.totalBilled,1234.56);assert.equal(source.totalPaid,100.25);assert.equal(source.balanceDue,1134.31);
 const basic=(await getDoc(ref(database('operator'),'customerDirectory'))).data();
 assert.equal(basic.createdByUid,'');assert.equal(basic.createdAt,0);assert.equal(basic.phone,'9876543210');
 assert.ok(!Object.hasOwn(basic,'balanceDue'));
});
