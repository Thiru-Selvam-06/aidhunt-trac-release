const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const { initializeTestEnvironment, assertSucceeds, assertFails } = require('@firebase/rules-unit-testing');
const { collection, getDocs, doc, setDoc } = require('firebase/firestore');
let env;
test.before(async () => {
 env = await initializeTestEnvironment({ projectId: 'demo-aidhunt-v16', firestore: { host: '127.0.0.1', port: 18180, rules: fs.readFileSync(path.join(__dirname, '../firestore.rules'), 'utf8') } });
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async c => {
  const db = c.firestore();
  for (const ws of ['a', 'b']) await setDoc(doc(db, `workspaces/${ws}`), { workspaceId: ws, ownerUid: `owner-${ws}` });
  for (const role of ['OWNER', 'PARTNER', 'OPERATOR']) await setDoc(doc(db, `workspaces/a/members/${role.toLowerCase()}-a`), { uid: `${role.toLowerCase()}-a`, workspaceId:'a', role, status:'ACTIVE' });
  await setDoc(doc(db, 'workspaces/a/members/inactive-a'), { uid:'inactive-a', workspaceId:'a', role:'PARTNER', status:'REMOVED' });
  await setDoc(doc(db, 'workspaces/a/members/unknown-a'), { uid:'unknown-a', workspaceId:'a', role:'Co-Owner', status:'ACTIVE' });
  await setDoc(doc(db, 'workspaces/b/members/partner-b'), { uid:'partner-b', workspaceId:'b', role:'PARTNER', status:'ACTIVE' });
  for (const ws of ['a', 'b']) for (const col of ['extensions','collections','partnerPermissions']) await setDoc(doc(db, `workspaces/${ws}/${col}/1`), { workspaceId:ws });
 });
});
test.after(async () => env?.cleanup());
function query(uid, ws, col) { return getDocs(collection(env.authenticatedContext(uid).firestore(), `workspaces/${ws}/${col}`)); }
for (const role of ['owner', 'partner', 'operator']) test(`${role} workspace Extensions listener succeeds`, () => assertSucceeds(query(`${role}-a`, 'a', 'extensions')));
for (const role of ['owner', 'partner']) test(`${role} workspace Collections listener succeeds`, () => assertSucceeds(query(`${role}-a`, 'a', 'collections')));
test('Operator Collections listener denied', () => assertFails(query('operator-a', 'a', 'collections')));
for (const role of ['owner', 'partner', 'operator']) test(`${role} legacy Partner Permissions listener denied (client must retire it)`, () => assertFails(query(`${role}-a`, 'a', 'partnerPermissions')));
for (const uid of ['outsider','inactive-a','unknown-a','partner-b']) for (const col of ['extensions','collections']) test(`${uid} cannot query workspace a ${col}`, () => assertFails(query(uid, 'a', col)));
for (const col of ['extensions','collections']) test(`workspace a actor cannot query workspace b ${col}`, () => assertFails(query('partner-a', 'b', col)));
