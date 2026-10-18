const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc, getDocs, collection, writeBatch } = require('firebase/firestore');
let env;
const p = `discovery-${Date.now()}`;
const ws = `${p}-personal`, invited = `${p}-invited`, owner = `${p}-owner`, other = `${p}-other`;
test.before(async () => {
 env = await initializeTestEnvironment({projectId:'demo-aidhunt-v16',firestore:{host:'127.0.0.1',port:18180,rules:fs.readFileSync(path.join(__dirname,'../firestore.rules'),'utf8')}});
 await env.withSecurityRulesDisabled(async c => {
  const db=c.firestore();
  await setDoc(doc(db,`workspaces/${invited}`),{workspaceId:invited,ownerUid:other});
  await setDoc(doc(db,`workspaces/${invited}/members/${other}`),{uid:other,workspaceId:invited,role:'OWNER',status:'ACTIVE'});
  await setDoc(doc(db,`workspaces/${invited}/members/${owner}`),{uid:owner,workspaceId:invited,role:'PARTNER',status:'ACTIVE'});
  await setDoc(doc(db,`workspaces/${invited}/invitations/private`),{ownerUid:other,status:'PENDING'});
 });
});
test.after(async()=>env?.cleanup());
function db(uid=owner){ return env.authenticatedContext(uid,{phone_number:'+919000000111'}).firestore(); }
test('own discovery index can be empty without denying authenticated profile access',async()=>{
 await assertSucceeds(getDoc(doc(db(),`users/${owner}`)));
 await assertSucceeds(getDocs(collection(db(),`userWorkspaceMemberships/${owner}/workspaces`)));
});
test('Owner bootstrap creates workspace and its Owner membership atomically',async()=>{
 const d=db(), batch=writeBatch(d);
 batch.set(doc(d,`workspaces/${ws}`),{workspaceId:ws,ownerUid:owner,name:'Disposable'});
 batch.set(doc(d,`workspaces/${ws}/members/${owner}`),{uid:owner,workspaceId:ws,role:'OWNER',status:'ACTIVE',joinedAt:1});
 await assertSucceeds(batch.commit());
});
test('committed Owner can publish profile and own index together and retry without duplicates',async()=>{
 const d=db();
 const publish=()=>{const batch=writeBatch(d);
 batch.set(doc(d,`userWorkspaceMemberships/${owner}/workspaces/${ws}`),{workspaceId:ws,ownerUid:owner,role:'OWNER',status:'ACTIVE'});
 batch.set(doc(d,`users/${owner}`),{uid:owner,defaultWorkspaceId:ws,workspaces:[ws,invited]}); return batch.commit();};
 await assertSucceeds(publish()); await assertSucceeds(publish());
 assert.equal((await getDocs(collection(d,`userWorkspaceMemberships/${owner}/workspaces`))).size,1);
 assert.equal((await getDoc(doc(d,`workspaces/${ws}`))).data().ownerUid,owner);
});
test('personal Owner remains Partner in invited workspace',async()=>{
 assert.equal((await getDoc(doc(db(),`workspaces/${ws}/members/${owner}`))).data().role,'OWNER');
 assert.equal((await getDoc(doc(db(),`workspaces/${invited}/members/${owner}`))).data().role,'PARTNER');
 await assertFails(setDoc(doc(db(),`workspaces/${invited}/members/forged`),{uid:'forged',workspaceId:invited,role:'OWNER',status:'ACTIVE'}));
});
test('other user cannot read discovery index or change profile',async()=>{
 await assertFails(getDocs(collection(db(other),`userWorkspaceMemberships/${owner}/workspaces`)));
 await assertFails(setDoc(doc(db(other),`users/${owner}`),{defaultWorkspaceId:invited}));
});
test('invitee cannot bypass trusted invitation or reactivate own membership',async()=>{
 await assertFails(getDoc(doc(db(),`workspaces/${invited}/invitations/private`)));
 await assertFails(setDoc(doc(db(),`workspaces/${invited}/members/${owner}`),{uid:owner,workspaceId:invited,role:'OWNER',status:'ACTIVE'}));
});
test('cached or indexed role cannot restore removed financial access',async()=>{
 await env.withSecurityRulesDisabled(async c=>{
  await setDoc(doc(c.firestore(),`workspaces/${invited}/members/${owner}`),{uid:owner,workspaceId:invited,role:'PARTNER',status:'REMOVED'});
  await setDoc(doc(c.firestore(),`userWorkspaceMemberships/${owner}/workspaces/${invited}`),{role:'OWNER',status:'ACTIVE'});
 });
 await assertFails(getDocs(collection(db(),`workspaces/${invited}/collections`)));
 await assertSucceeds(getDoc(doc(db(),`workspaces/${ws}`)));
});
