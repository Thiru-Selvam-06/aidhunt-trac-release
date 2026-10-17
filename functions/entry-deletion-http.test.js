"use strict";
const test=require("node:test"),assert=require("node:assert/strict");
if(process.env.FIRESTORE_EMULATOR_HOST!=="127.0.0.1:18180" || process.env.GCLOUD_PROJECT!=="demo-aidhunt-v16" || process.env.FIREBASE_AUTH_EMULATOR_HOST || process.env.FUNCTIONS_EMULATOR) throw Error("HTTP tests require local Firestore and SDK authentication verification enabled");
const endpoints=require("./index"),express=require("express");
const {getFirestore,Timestamp}=require("firebase-admin/firestore"),{getAuth}=require("firebase-admin/auth");
const db=getFirestore(),auth=getAuth();const originalVerify=auth.verifyIdToken;let verified=[];
// Only the verification dependency is doubled: the actual callable HTTP wrapper runs.
auth.verifyIdToken=async token=>{verified.push(token);if(token==="invalid" || token==="expired") throw Object.assign(Error("Local test token rejected"),{code:token==="expired"?"auth/id-token-expired":"auth/argument-error"});if(!token.startsWith("test-")) throw Error("Unknown local token");return {uid:token.substring(5),aud:"demo-aidhunt-v16",exp:Math.floor(Date.now()/1000)+3600};};
let server,url,count=0;
test.before(async()=>{const app=express();app.use(express.json());for(const name of ["deleteWorkspaceEntry","deleteWorkspaceCustomer","beginWorkspaceInvitation","redeemWorkspaceInvitation"])app.post("/"+name,endpoints[name]);server=app.listen(0,"127.0.0.1");await new Promise(resolve=>server.once("listening",resolve));url="http://127.0.0.1:"+server.address().port;});
test.after(async()=>{auth.verifyIdToken=originalVerify;await new Promise(resolve=>server.close(resolve));await db.terminate();});
async function fixture(extra={}) {
 const wsId="http-delete-"+process.pid+"-"+(++count),ws=db.doc("workspaces/"+wsId);
 await ws.set({workspaceId:wsId,ownerUid:"owner"});
 for(const [uid,role] of [["owner","OWNER"],["actor","PARTNER"],["other","PARTNER"],["operator","OPERATOR"]])await ws.collection("members").doc(uid).set({workspaceId:wsId,uid,role,status:"ACTIVE"});
 await ws.collection("entries").doc("1").set({id:1n,workspaceId:wsId,customerId:2n,createdByUid:"actor",entryForUid:"actor",financialAttributionUid:"actor",isHistorical:false,isDeleted:false,authorizationTimeStatus:"CLIENT_ORIGINAL_V2",createdAt:Date.now()-1000,authorizationCreatedAt:Timestamp.now(),totalAmount:100,amountReceived:0,pendingAmount:100,...extra});
 await ws.collection("customers").doc("2").set({workspaceId:wsId,totalBilled:100,totalPaid:0,balanceDue:100,isDeleted:false});
 await ws.collection("collections").doc("20").set({workspaceId:wsId,jobEntryId:9n,allocationsJson:"[]",status:"REVERSED",amount:5});
 await ws.collection("expenses").doc("30").set({workspaceId:wsId,relatedJobId:9n,createdByUid:"other",amount:5});
 return {ws,wsId,data:{protocolVersion:2,actorContext:"actor",operationType:"DELETE_ENTRY",dependencies:[],workspaceId:wsId,entryId:"1",operationId:"http-delete-one",actionTime:Date.now()}};
}
async function snapshot(f) {return Promise.all(["entries","customers","collections","expenses","entryDeleteReceipts"].map(async name=>(await f.ws.collection(name).get()).docs.map(d=>[d.id,d.data()])));}
async function call(data,token="test-actor",endpoint="deleteWorkspaceEntry",extraHeaders={},raw=false) {const response=await fetch(url+"/"+endpoint,{method:"POST",headers:{"Content-Type":"application/json",...(token===null?{}:{Authorization:"Bearer "+token}),...extraHeaders},body:JSON.stringify(raw?data:{data})});return {code:response.status,body:await response.json()};}
for(const token of [null,"invalid","expired"]) for(const endpoint of ["deleteWorkspaceEntry","deleteWorkspaceCustomer","beginWorkspaceInvitation","redeemWorkspaceInvitation"]) test(endpoint+" rejects "+token+" authentication without writes",async()=>{const f=await fixture(),before=await snapshot(f);const r=await call(f.data,token,endpoint);assert.equal(r.code,401);assert.equal(r.body.error.status,"UNAUTHENTICATED");assert.deepEqual(await snapshot(f),before);if(token!==null)assert.ok(verified.includes(token));});
test("Android callable schema succeeds, echoes identity and retries the acknowledged operation",async()=>{const f=await fixture();let r=await call(f.data);assert.equal(r.code,200);assert.deepEqual(r.body.result,{deleted:true,workspaceId:f.wsId,entryId:f.data.entryId,operationId:f.data.operationId,actionTime:f.data.actionTime});r=await call(f.data);assert.equal(r.code,200);assert.deepEqual(r.body.result,{deleted:true,workspaceId:f.wsId,entryId:f.data.entryId,operationId:f.data.operationId,actionTime:f.data.actionTime});assert.equal((await f.ws.collection("entryDeleteReceipts").get()).size,1);});
for(const field of ["actorUid","createdByUid","collectedByUid","role"]) test("forged "+field+" is rejected and leaves all financial records unchanged",async()=>{const f=await fixture(),before=await snapshot(f);const r=await call({...f.data,actorContext:"other",[field]:field==="actionTime"?1:"owner"},"test-other");assert.equal(r.code,400);assert.equal(r.body.error.status,"INVALID_ARGUMENT");assert.deepEqual(await snapshot(f),before);});
test("forged callable auth headers cannot impersonate Owner",async()=>{const f=await fixture(),before=await snapshot(f);const r=await call({...f.data,actorContext:"other"},"test-other","deleteWorkspaceEntry",{"X-Callable-Context-Auth":JSON.stringify({uid:"owner"})});assert.equal(r.code,403);assert.deepEqual(await snapshot(f),before);});
test("cross-workspace membership is denied; same role elsewhere confers no access",async()=>{const f=await fixture(),before=await snapshot(f);await f.ws.collection("members").doc("actor").delete();const other=db.doc("workspaces/http-other-"+process.pid);await other.collection("members").doc("actor").set({role:"OWNER",status:"ACTIVE"});const r=await call(f.data);assert.equal(r.code,403);assert.deepEqual(await snapshot(f),before);});
for(const mode of ["expired","historical","inactive","linked-expense","active-collection","missing-customer","deleted-customer","foreign-customer"])test("HTTP "+mode+" failure rolls back all business records",async()=>{const f=await fixture();const entry=f.ws.collection("entries").doc("1"),customer=f.ws.collection("customers").doc("2");if(mode==="expired")await entry.update({createdAt:Date.now()-72*3600000,authorizationCreatedAt:Timestamp.fromMillis(Date.now()-72*3600000)});if(mode==="historical")await entry.update({isHistorical:true});if(mode==="inactive")await f.ws.collection("members").doc("actor").update({status:"REMOVED"});if(mode==="linked-expense")await f.ws.collection("expenses").doc("30").update({relatedJobId:1n});if(mode==="active-collection")await f.ws.collection("collections").doc("20").update({jobEntryId:1n,status:"ACTIVE"});if(mode==="missing-customer")await customer.delete();if(mode==="deleted-customer")await customer.update({isDeleted:true});if(mode==="foreign-customer")await customer.update({workspaceId:"other"});const before=await snapshot(f),r=await call(f.data);assert.ok([403,400].includes(r.code));assert.deepEqual(await snapshot(f),before);});
test("numeric and legacy string references reconcile one Customer consistently",async()=>{const f=await fixture();await f.ws.collection("entries").doc("3").set({workspaceId:f.wsId,customerId:"2",totalAmount:200,amountReceived:50,pendingAmount:150,isDeleted:false});const r=await call(f.data);assert.equal(r.code,200);const c=(await f.ws.collection("customers").doc("2").get()).data();assert.equal(Number(c.totalBilled),200);assert.equal(Number(c.balanceDue),150);});
test("raw JSON without callable data envelope is rejected",async()=>{const f=await fixture(),before=await snapshot(f);const r=await call(f.data,"test-actor","deleteWorkspaceEntry",{},true);assert.equal(r.code,400);assert.deepEqual(await snapshot(f),before);});

for(const mode of ["owner-impersonation","unknown-role","membership-workspace","entry-workspace","malformed-customer","malformed-customer-state","late-aggregation-failure"])test(mode+" is rejected with complete transaction rollback",async()=>{
 const f=await fixture();
 if(mode==="owner-impersonation")await f.ws.collection("members").doc("actor").update({role:"OWNER"});
 if(mode==="unknown-role")await f.ws.collection("members").doc("actor").update({role:"CO_OWNER"});
 if(mode==="membership-workspace")await f.ws.collection("members").doc("actor").update({workspaceId:"other"});
 if(mode==="entry-workspace")await f.ws.collection("entries").doc("1").update({workspaceId:"other"});
 if(mode==="malformed-customer")await f.ws.collection("entries").doc("1").update({customerId:"other/customers/2"});
 if(mode==="malformed-customer-state")await f.ws.collection("customers").doc("2").update({isDeleted:"false"});
 if(mode==="late-aggregation-failure")await f.ws.collection("entries").doc("3").set({workspaceId:f.wsId,customerId:2n,totalAmount:1,amountReceived:"invalid",pendingAmount:1});
 const before=await snapshot(f),r=await call(f.data);assert.ok([400,403].includes(r.code));assert.deepEqual(await snapshot(f),before);
});
test("verified Owner and own-record Operator succeed through HTTP",async()=>{
 const owner=await fixture({isHistorical:true,authorizationCreatedAt:Timestamp.fromMillis(Date.now()-72*3600000)});
 let r=await call({...owner.data,actorContext:"owner"},"test-owner");assert.equal(r.code,200);assert.deepEqual(r.body.result,{deleted:true,workspaceId:owner.wsId,entryId:owner.data.entryId,operationId:owner.data.operationId,actionTime:owner.data.actionTime});
 const operator=await fixture({createdByUid:"operator",entryForUid:"operator",financialAttributionUid:"operator"});
 r=await call({...operator.data,actorContext:"operator"},"test-operator");assert.equal(r.code,200);assert.deepEqual(r.body.result,{deleted:true,workspaceId:operator.wsId,entryId:operator.data.entryId,operationId:operator.data.operationId,actionTime:operator.data.actionTime});
});

test("Android 63-bit string Entry identity is accepted without precision loss",async()=>{const f=await fixture(),id="9223372036854775807";const original=(await f.ws.collection("entries").doc("1").get()).data();await f.ws.collection("entries").doc(id).set({...original,id:BigInt(id)});const data={...f.data,entryId:id};const r=await call(data);assert.equal(r.code,200);assert.deepEqual(r.body.result,{deleted:true,workspaceId:data.workspaceId,entryId:data.entryId,operationId:data.operationId,actionTime:data.actionTime});assert.equal((await f.ws.collection("entries").doc(id).get()).exists,false);assert.equal((await f.ws.collection("entries").doc("1").get()).exists,true);});
test("malformed authorization header cannot use a supplied auth context",async()=>{const f=await fixture(),before=await snapshot(f);const r=await call(f.data,"test-owner","deleteWorkspaceEntry",{Authorization:"Basic test-owner"});assert.equal(r.code,401);assert.deepEqual(await snapshot(f),before);});
test("concurrent duplicate HTTP requests apply one financial deletion",async()=>{const f=await fixture();const responses=await Promise.all([call(f.data),call(f.data)]);for(const r of responses){assert.equal(r.code,200);assert.deepEqual(r.body.result,{deleted:true,workspaceId:f.wsId,entryId:f.data.entryId,operationId:f.data.operationId,actionTime:f.data.actionTime});}assert.equal((await f.ws.collection("entryDeleteReceipts").get()).size,1);assert.equal(Number((await f.ws.collection("customers").doc("2").get()).get("balanceDue")),0);});

for(const actionTime of [null,0,-1,'1000',Date.now()+86400000])test('HTTP rejects invalid operation time '+actionTime+' without writes',async()=>{
 const f=await fixture(),before=await snapshot(f);const r=await call({...f.data,actionTime});assert.ok([400,412].includes(r.code));assert.deepEqual(await snapshot(f),before);
});
test('accepted backdating limitation still requires actual authenticated creator',async()=>{
 const origin=Date.now()-72*3600000,f=await fixture({createdAt:origin});const data={...f.data,actionTime:origin+3600000};
 const before=await snapshot(f);const forgedActor=await call({...data,actorContext:'other'},'test-other');assert.equal(forgedActor.code,403);assert.deepEqual(await snapshot(f),before);
 const permitted=await call(data);assert.equal(permitted.code,200);assert.deepEqual(permitted.body.result,{deleted:true,workspaceId:data.workspaceId,entryId:data.entryId,operationId:data.operationId,actionTime:data.actionTime});
});

for(const field of ['actionTime','protocolVersion','dependencies','actorContext','operationType'])test('old request missing '+field+' receives permanent upgrade instruction without writes',async()=>{const f=await fixture(),before=await snapshot(f),data={...f.data};delete data[field];const r=await call(data);assert.equal(r.code,400);assert.equal(r.body.error.details.code,'UPGRADE_REQUIRED');assert.deepEqual(await snapshot(f),before);});
