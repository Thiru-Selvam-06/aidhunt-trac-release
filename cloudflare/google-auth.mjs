const FIREBASE_KEYS = 'https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com';
const GOOGLE_TOKEN = 'https://oauth2.googleapis.com/token';
const SCOPE = 'https://www.googleapis.com/auth/datastore https://www.googleapis.com/auth/identitytoolkit';
const encoder = new TextEncoder();
let publicKeys;
let publicKeysUntil = 0;
let serviceToken;
let serviceTokenUntil = 0;
let serviceTokenAccount;

export class AuthError extends Error {
  constructor(message = 'Authentication required', status = 'UNAUTHENTICATED') { super(message); this.status = status; }
}
function bytes(text) {
  const normalized = text.replace(/-/g, '+').replace(/_/g, '/');
  const decoded = atob(normalized + '='.repeat((4 - normalized.length % 4) % 4));
  return Uint8Array.from(decoded, character => character.charCodeAt(0));
}
function encoded(input) {
  const data = input instanceof Uint8Array ? input : encoder.encode(input);
  let result = '';
  for (const byte of data) result += String.fromCharCode(byte);
  return btoa(result).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}
function parsePart(part) { return JSON.parse(new TextDecoder().decode(bytes(part))); }
function cacheSeconds(headers) {
  const match = /(?:^|,)\s*max-age=(\d+)/i.exec(headers.get('cache-control') || '');
  return match ? Math.min(Number(match[1]), 3600) : 300;
}
async function keys(fetcher) {
  if (publicKeys && Date.now() < publicKeysUntil) return publicKeys;
  const response = await fetcher(FIREBASE_KEYS);
  if (!response.ok) throw new AuthError('Firebase signing keys unavailable', 'UNAVAILABLE');
  const body = await response.json();
  if (!Array.isArray(body.keys)) throw new AuthError('Firebase signing keys invalid', 'UNAVAILABLE');
  publicKeys = new Map(body.keys.filter(key => key.kid && key.kty === 'RSA').map(key => [key.kid, key]));
  publicKeysUntil = Date.now() + cacheSeconds(response.headers) * 1000;
  return publicKeys;
}
export async function verifyFirebaseIdToken(token, project, fetcher = fetch) {
  if (typeof token !== 'string' || token.length > 8192 || token.split('.').length !== 3) throw new AuthError();
  const [head, payload, signature] = token.split('.');
  let header, claims;
  try { header = parsePart(head); claims = parsePart(payload); } catch { throw new AuthError(); }
  const now = Math.floor(Date.now() / 1000);
  if (header.alg !== 'RS256' || typeof header.kid !== 'string' || !header.kid ||
      claims.aud !== project || claims.iss !== `https://securetoken.google.com/${project}` ||
      typeof claims.sub !== 'string' || !claims.sub || claims.sub.length > 128 ||
      !Number.isInteger(claims.exp) || claims.exp <= now ||
      !Number.isInteger(claims.iat) || claims.iat > now ||
      !Number.isInteger(claims.auth_time) || claims.auth_time > now) throw new AuthError();
  const jwk = (await keys(fetcher)).get(header.kid);
  if (!jwk) throw new AuthError();
  let verified = false;
  try {
    const key = await crypto.subtle.importKey('jwk', jwk, { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['verify']);
    verified = await crypto.subtle.verify('RSASSA-PKCS1-v1_5', key, bytes(signature), encoder.encode(`${head}.${payload}`));
  } catch { throw new AuthError(); }
  if (!verified) throw new AuthError();
  return { ...claims, uid: claims.sub };
}
function serviceAccount(env, project) {
  let account;
  try { account = JSON.parse(env.GOOGLE_SERVICE_ACCOUNT_JSON); } catch { throw new AuthError('Worker credential is unavailable', 'UNAVAILABLE'); }
  if (account?.type !== 'service_account' || account.project_id !== project ||
      typeof account.client_email !== 'string' || !account.client_email.endsWith(`@${project}.iam.gserviceaccount.com`) ||
      typeof account.private_key !== 'string' || !account.private_key.includes('BEGIN PRIVATE KEY'))
    throw new AuthError('Worker credential is invalid', 'UNAVAILABLE');
  return account;
}
export async function googleAccessToken(env, project, fetcher = fetch) {
  const account = serviceAccount(env, project);
  if (serviceToken && serviceTokenAccount === account.client_email && Date.now() < serviceTokenUntil) return serviceToken;
  const now = Math.floor(Date.now() / 1000);
  const header = encoded(JSON.stringify({ alg: 'RS256', typ: 'JWT' }));
  const payload = encoded(JSON.stringify({ iss: account.client_email, scope: SCOPE, aud: GOOGLE_TOKEN, iat: now, exp: now + 3600 }));
  const pem = account.private_key.replace(/-----BEGIN PRIVATE KEY-----|-----END PRIVATE KEY-----|\s/g, '');
  let key;
  try { key = await crypto.subtle.importKey('pkcs8', bytes(pem), { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' }, false, ['sign']); }
  catch { throw new AuthError('Worker credential is invalid', 'UNAVAILABLE'); }
  const assertion = `${header}.${payload}.${encoded(new Uint8Array(await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, encoder.encode(`${header}.${payload}`))))}`;
  const response = await fetcher(GOOGLE_TOKEN, { method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer', assertion }) });
  if (!response.ok) throw new AuthError('Google credential exchange failed', 'UNAVAILABLE');
  const result = await response.json();
  if (typeof result.access_token !== 'string' || !Number.isFinite(Number(result.expires_in))) throw new AuthError('Google credential exchange invalid', 'UNAVAILABLE');
  serviceToken = result.access_token;
  serviceTokenUntil = Date.now() + Math.max(0, Number(result.expires_in) - 120) * 1000;
  serviceTokenAccount = account.client_email;
  return serviceToken;
}
export async function verifyCurrentUser(claims, oauthToken, project, fetcher = fetch) {
  const response = await fetcher(`https://identitytoolkit.googleapis.com/v1/projects/${project}/accounts:lookup`, {
    method: 'POST', headers: { Authorization: `Bearer ${oauthToken}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ localId: [claims.uid] })
  });
  if (!response.ok) throw new AuthError('Firebase user lookup failed', 'UNAVAILABLE');
  const body = await response.json();
  const user = body.users?.find(item => item.localId === claims.uid);
  if (!user || user.disabled || (user.validSince != null && Number(user.validSince) > claims.auth_time)) throw new AuthError();
  return claims;
}
