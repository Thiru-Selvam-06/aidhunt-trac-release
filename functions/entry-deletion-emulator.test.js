"use strict";
const test=require("node:test"),assert=require("node:assert/strict");
if(process.env.FIRESTORE_EMULATOR_HOST!=="127.0.0.1:18180" || process.env.GCLOUD_PROJECT!=="demo-aidhunt-v16") throw Error("Deletion tests require the existing local demo Emulator");
const command=require("./index").deleteWorkspaceEntry;
const customerCommand=require("./index").deleteWorkspaceCustomer;
const {getFirestore,Timestamp}=require("firebase-admin/firestore");
const db=getFirestore();let counter=0;
test.after(()=>db.terminate());
async function fixture(role="PARTNER",extra={}) {
 const wsId="delete-due-"+process.pid+"-"+(++counter),ws=db.doc("workspaces/"+wsId);
 const creator=role==="OWNER"?"owner":"actor";
 await ws.set({workspaceId:wsId,ownerUid:"owner"});
 for(const [uid,r] of [["owner","OWNER"],["actor",role==="OWNER"?"PARTNER":role],["other","PARTNER"]]) await ws.collection("members").doc(uid).set({uid,workspaceId:wsId,role:r,status:"ACTIVE"});
 const entry={workspaceId:wsId,id:1n,customerId:2n,createdByUid:creator,entryForUid:creator,financialAttributionUid:creator,isHistorical:false,isDeleted:false,authorizationTimeStatus:"CLIENT_ORIGINAL_V2",createdAt:Date.now()-1000,authorizationCreatedAt:Timestamp.now(),totalAmount:100,amountReceived:0,pendingAmount:100,...extra};
 if(extra.authorizationCreatedAt!==undefined) entry.createdAt=extra.authorizationCreatedAt?.toMillis() ?? 0;
 await ws.collection("entries").doc("1").set(entry);
 await ws.collection("customers").doc("2").set({workspaceId:wsId,totalBilled:100,totalPaid:0,balanceDue:100});
 const actionTime=Date.now();
 const run=(uid=creator,operationId="delete-one",time=actionTime)=>command.run({auth:{uid},data:{workspaceId:wsId,protocolVersion:2,actorContext:uid,operationType:"DELETE_ENTRY",dependencies:[],entryId:"1",operationId,actionTime:time}});
 return {ws,wsId,entry,run,creator};
}
for(const role of ["OWNER","PARTNER","OPERATOR"]) test(role+" hard-deletes authorized unpaid source and clears final due",async()=>{
 const f=await fixture(role);await f.run();assert.equal((await f.ws.collection("entries").doc("1").get()).exists,false);
 const customer=await f.ws.collection("customers").doc("2").get();
 if(role==="OWNER") assert.equal(customer.exists,false);
 else {assert.equal(customer.exists,true);assert.equal(Number(customer.get("balanceDue")),0);}
});
test("duplicate acknowledged command returns same result; missing unacknowledged command fails",async()=>{const f=await fixture();await f.run();await f.run();await assert.rejects(f.run(f.creator,"another"));await assert.rejects(f.run("other"));});
for(const extra of [{isHistorical:true},{authorizationCreatedAt:Timestamp.fromMillis(Date.now()-72*3600000)},{authorizationCreatedAt:null}]) test("non-Owner historical, expired or untrusted creation blocks deletion "+JSON.stringify(extra),async()=>{const f=await fixture("PARTNER",extra);await assert.rejects(f.run());assert.equal((await f.ws.collection("entries").doc("1").get()).exists,true);});
test("Owner may delete historical source",async()=>{const f=await fixture("OWNER",{isHistorical:true});await f.run();});

test('Functions direct Customer deletion removes basic directory atomically and preserves command identity',async()=>{
 const f=await fixture('OWNER');await f.ws.collection('entries').doc('1').delete();
 const directory=f.ws.collection('customerDirectory').doc('2');await directory.set({workspaceId:f.wsId,creationOperationId:'original-basic'});
 const actionTime=Date.now(),data={workspaceId:f.wsId,protocolVersion:2,actorContext:'owner',operationType:'DELETE_CUSTOMER',
  dependencies:[],customerId:'2',operationId:'delete-profile',actionTime};
 const first=await customerCommand.run({auth:{uid:'owner'},data});
 assert.equal((await directory.get()).exists,false);assert.equal((await f.ws.collection('customers').doc('2').get()).exists,false);
 const receipt=(await f.ws.collection('customerDeleteReceipts').doc('delete-profile').get()).data();
 assert.equal(receipt.operationId,'delete-profile');assert.equal(Number(receipt.actionTime),actionTime);
 assert.equal(receipt.deletedCustomerDirectoryCreationOperationId,'original-basic');
 const proof=(await f.ws.collection('customerDeletionProofs').doc('2').get()).data();
 assert.equal(proof.operationId,'delete-profile');assert.equal(proof.actorUid,'owner');assert.equal(proof.recordId,'2');
 assert.deepEqual(await customerCommand.run({auth:{uid:'owner'},data}),first);
});
for(const retained of [false,true])test('Functions Entry deletion '+(retained?'keeps referenced':'removes unused')+' basic directory',async()=>{
 const f=await fixture('OWNER'),directory=f.ws.collection('customerDirectory').doc('2');
 await directory.set({workspaceId:f.wsId,creationOperationId:'original-basic'});
 if(retained) await f.ws.collection('entries').doc('3').set({...f.entry,id:3n,totalAmount:200,pendingAmount:200});
 await f.run();assert.equal((await directory.get()).exists,retained);
 const customer=await f.ws.collection('customers').doc('2').get();assert.equal(customer.exists,retained);
 if(retained) assert.equal(Number(customer.get('balanceDue')),200);
 else assert.equal((await f.ws.collection('entryDeleteReceipts').doc('delete-one').get()).get('deletedCustomerDirectoryId'),'2');
 const proof=(await f.ws.collection('entryDeletionProofs').doc('1').get()).data();
 assert.equal(proof.operationId,'delete-one');assert.equal(proof.sourceCreatorUid,'owner');
 assert.equal(Number(proof.totalAmount),100);assert.equal(Number(proof.pendingAmount),100);
 assert.equal((await f.ws.collection('customerDeletionProofs').doc('2').get()).exists,!retained);
});
test('Functions mismatched directory blocks the whole financial deletion',async()=>{
 const f=await fixture('OWNER');await f.ws.collection('customerDirectory').doc('2').set({workspaceId:'foreign'});
 await assert.rejects(f.run());assert.equal((await f.ws.collection('entries').doc('1').get()).exists,true);
 assert.equal((await f.ws.collection('customers').doc('2').get()).exists,true);
 assert.equal((await f.ws.collection('entryDeleteReceipts').doc('delete-one').get()).exists,false);
});
test('Functions deletion marker preserves nested native Timestamp audit precision and original command',async()=>{
 const stamp=new Timestamp(1794135600,123456789);
 const history=[{operationId:'original-edit',actorUid:'owner',before:{observedAt:stamp,amount:100},after:{amount:100}}];
 const f=await fixture('OWNER',{jobEditHistory:history,customerReassignmentHistory:[{operationId:'original-move',fromCustomerId:'3',toCustomerId:'2'}]});
 // Compare the actual persisted source, not an input precision the datastore may canonicalize on creation.
 const original=(await f.ws.collection('entries').doc('1').get()).get('jobEditHistory')[0].before.observedAt;
 const first=await f.run();const ref=f.ws.collection('entryDeletionProofs').doc('1');
 const proof=(await ref.get()).data();assert.equal(proof.operationId,'delete-one');assert.equal(proof.recordId,'1');
 console.log(JSON.stringify({syntheticTimestampEvidence:true,inputNanos:stamp.nanoseconds,sourceNanos:original.nanoseconds,
  proofNanos:proof.deletedJobEditHistory[0].before.observedAt.nanoseconds}));
 assert.equal(proof.deletedJobEditHistory[0].before.observedAt.isEqual(original),true);
 assert.equal(proof.deletedJobEditHistory[0].operationId,'original-edit');
 assert.equal(proof.deletedCustomerReassignmentHistory[0].fromCustomerId,'3');
 assert.deepEqual(await f.run(),first);assert.equal((await ref.get()).get('deletedJobEditHistory')[0].before.observedAt.isEqual(original),true);
});
test("other creator and inactive membership are denied",async()=>{const f=await fixture();await assert.rejects(f.run("other"));await f.ws.collection("members").doc(f.creator).update({status:"REMOVED"});await assert.rejects(f.run());});
for(const creator of ["actor","other"]) test("linked Expense owned by "+creator+" is retained and blocks Entry deletion",async()=>{const f=await fixture();await f.ws.collection("expenses").doc("10").set({workspaceId:f.wsId,relatedJobId:1n,createdByUid:creator,amount:5});await assert.rejects(f.run());assert.equal((await f.ws.collection("expenses").doc("10").get()).exists,true);assert.equal((await f.ws.collection("entries").doc("1").get()).exists,true);});
for(const paid of [40,100]) test("paid Entry "+paid+" blocks without changing balances",async()=>{const f=await fixture("PARTNER",{amountReceived:paid,pendingAmount:100-paid});await assert.rejects(f.run());assert.equal(Number((await f.ws.collection("entries").doc("1").get()).get("amountReceived")),paid);});
test("active split allocation blocks; reversed history survives hard delete",async()=>{const f=await fixture();const ref=f.ws.collection("collections").doc("20");await ref.set({workspaceId:f.wsId,jobEntryId:9n,allocationsJson:'[{"jobId":1,"amount":40}]',status:"ACTIVE"});await assert.rejects(f.run());await ref.update({status:"REVERSED"});await f.run();assert.equal((await ref.get()).get("status"),"REVERSED");});
test("workspace roles and identical record IDs remain isolated",async()=>{const a=await fixture("PARTNER"),b=await fixture("OPERATOR",{createdByUid:"other",entryForUid:"other"});await a.run();await assert.rejects(b.run("actor"));assert.equal((await b.ws.collection("entries").doc("1").get()).exists,true);});
test("retained Entries determine Customer totals",async()=>{const f=await fixture();await f.ws.collection("entries").doc("3").set({...f.entry,id:3n,totalAmount:200,amountReceived:50,pendingAmount:150});await f.run();const c=(await f.ws.collection("customers").doc("2").get()).data();assert.equal(Number(c.totalBilled),200);assert.equal(Number(c.totalPaid),50);assert.equal(Number(c.balanceDue),150);});

test("legacy string Expense references still protect source Entry",async()=>{const f=await fixture();await f.ws.collection("expenses").doc("10").set({workspaceId:f.wsId,relatedJobId:"1",createdByUid:"other",amount:5});await assert.rejects(f.run());assert.equal((await f.ws.collection("entries").doc("1").get()).exists,true);});

for(const offset of [18,24,26])test('late queued delete action hour '+offset+' uses original window',async()=>{
 const origin=Date.now()-30*3600000,f=await fixture('PARTNER',{createdAt:origin});
 const operation=f.run(f.creator,'late-delete',origin+offset*3600000);
 if(offset<24){await operation;assert.equal((await f.ws.collection('entries').doc('1').get()).exists,false);await f.run(f.creator,'late-delete',origin+offset*3600000);await assert.rejects(f.run(f.creator,'late-delete',origin+19*3600000));}
 else{await assert.rejects(operation);assert.equal((await f.ws.collection('entries').doc('1').get()).exists,true);}
});
