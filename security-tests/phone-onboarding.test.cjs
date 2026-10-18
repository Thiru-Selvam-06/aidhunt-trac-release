const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { doc, setDoc, getDoc } = require('firebase/firestore');
let env;
const prefix = `phone-bootstrap-${Date.now()}`;
test.before(async () => {
 env = await initializeTestEnvironment({projectId:'demo-aidhunt-v16',firestore:{host:'127.0.0.1',port:18180,rules:fs.readFileSync(path.join(__dirname,'../firestore.rules'),'utf8')}});
});
test.after(async () => env?.cleanup());
function create(uid, claims, suffix) {
 const workspaceId = `${prefix}-${suffix}`;
 return setDoc(doc(env.authenticatedContext(uid, claims).firestore(), `workspaces/${workspaceId}`), {workspaceId,ownerUid:uid,name:'Disposable phone policy fixture'});
}
test('new account cannot bootstrap workspace without a verified Firebase phone claim', () => assertFails(create('no-phone-bootstrap', {}, 'missing')));
test('empty phone claim cannot authorize account bootstrap', () => assertFails(create('empty-phone-bootstrap', {phone_number:''}, 'empty')));
test('verified Firebase phone identity can bootstrap its own workspace', () => assertSucceeds(create('verified-phone-bootstrap', {phone_number:'+919000001114'}, 'verified')));
test('phone bootstrap cannot impersonate a different Owner UID', () => {
 const workspaceId=`${prefix}-forged`;
 return assertFails(setDoc(doc(env.authenticatedContext('verified-phone-bootstrap',{phone_number:'+919000001114'}).firestore(),`workspaces/${workspaceId}`),{workspaceId,ownerUid:'other-uid'}));
});
test('existing Owner retains access without requiring phone claim on every sign-in', async () => {
 const workspaceId=`${prefix}-legacy`;
 await env.withSecurityRulesDisabled(async c => {
  await setDoc(doc(c.firestore(),`workspaces/${workspaceId}`),{workspaceId,ownerUid:'legacy-email-owner'});
  await setDoc(doc(c.firestore(),`workspaces/${workspaceId}/members/legacy-email-owner`),{uid:'legacy-email-owner',workspaceId,role:'OWNER',status:'ACTIVE'});
 });
 await assertSucceeds(getDoc(doc(env.authenticatedContext('legacy-email-owner').firestore(),`workspaces/${workspaceId}`)));
});
