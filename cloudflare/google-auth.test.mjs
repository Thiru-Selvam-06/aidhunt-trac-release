import test from 'node:test';
import assert from 'node:assert/strict';
import { verifyFirebaseIdToken, googleAccessToken, verifyCurrentUser } from './google-auth.mjs';

const project = 'trac-aidhunt';
const now = () => Math.floor(Date.now() / 1000);
const b64 = value => Buffer.from(value).toString('base64url');

test('Firebase ID token signature, project, expiry, and current account are required', async () => {
  const pair = await crypto.subtle.generateKey({ name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048,
    publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' }, true, ['sign', 'verify']);
  const jwk = await crypto.subtle.exportKey('jwk', pair.publicKey);
  const fetcher = async () => new Response(JSON.stringify({ keys: [{ ...jwk, kid: 'test-key' }] }),
    { status: 200, headers: { 'cache-control': 'max-age=300' } });
  const signed = async changes => {
    const header = b64(JSON.stringify({ alg: 'RS256', kid: 'test-key' }));
    const claims = b64(JSON.stringify({ aud: project, iss: `https://securetoken.google.com/${project}`,
      sub: 'verified-uid', auth_time: now() - 30, iat: now() - 30, exp: now() + 300, ...changes }));
    const message = `${header}.${claims}`;
    const signature = await crypto.subtle.sign('RSASSA-PKCS1-v1_5', pair.privateKey, new TextEncoder().encode(message));
    return `${message}.${b64(signature)}`;
  };
  const valid = await signed();
  assert.equal((await verifyFirebaseIdToken(valid, project, fetcher)).uid, 'verified-uid');
  await assert.rejects(verifyFirebaseIdToken(`${valid.slice(0, -2)}xx`, project, fetcher), { status: 'UNAUTHENTICATED' });
  await assert.rejects(verifyFirebaseIdToken(await signed({ aud: 'other-project' }), project, fetcher), { status: 'UNAUTHENTICATED' });
  await assert.rejects(verifyFirebaseIdToken(await signed({ exp: now() - 1 }), project, fetcher), { status: 'UNAUTHENTICATED' });
  const claims = await verifyFirebaseIdToken(valid, project, fetcher);
  const account = async user => new Response(JSON.stringify({ users: [user] }), { status: 200 });
  await assert.rejects(verifyCurrentUser(claims, 'temporary-oauth', project,
    () => account({ localId: 'verified-uid', disabled: true })), { status: 'UNAUTHENTICATED' });
  await assert.rejects(verifyCurrentUser(claims, 'temporary-oauth', project,
    () => account({ localId: 'verified-uid', validSince: String(now()) })), { status: 'UNAUTHENTICATED' });
  assert.equal((await verifyCurrentUser(claims, 'temporary-oauth', project,
    () => account({ localId: 'verified-uid', validSince: String(now() - 100) }))).uid, 'verified-uid');
});

test('service account credential is project-bound and exchanged for a short-lived OAuth token', async () => {
  const pair = await crypto.subtle.generateKey({ name: 'RSASSA-PKCS1-v1_5', modulusLength: 2048,
    publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-256' }, true, ['sign', 'verify']);
  const exported = await crypto.subtle.exportKey('pkcs8', pair.privateKey);
  const pem = `-----BEGIN PRIVATE KEY-----\n${Buffer.from(exported).toString('base64')}\n-----END PRIVATE KEY-----`;
  const account = { type: 'service_account', project_id: project,
    client_email: `test-worker@${project}.iam.gserviceaccount.com`, private_key: pem };
  let calls = 0;
  const token = await googleAccessToken({ GOOGLE_SERVICE_ACCOUNT_JSON: JSON.stringify(account) }, project,
    async (_url, options) => {
      calls++;
      const assertion = options.body.get('assertion');
      const claims = JSON.parse(Buffer.from(assertion.split('.')[1], 'base64url').toString());
      assert.equal(claims.iss, account.client_email);
      assert.match(claims.scope, /identitytoolkit/);
      return new Response(JSON.stringify({ access_token: 'temporary-test-token', expires_in: 3600 }), { status: 200 });
    });
  assert.equal(token, 'temporary-test-token');
  assert.equal(calls, 1);
  await assert.rejects(googleAccessToken({ GOOGLE_SERVICE_ACCOUNT_JSON: JSON.stringify({ ...account, project_id: 'wrong' }) }, project),
    { status: 'UNAVAILABLE' });
});
