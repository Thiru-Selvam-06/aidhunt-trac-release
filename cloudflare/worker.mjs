export const AUTHORIZED_STAGING_WORKSPACES = new Set([
  'ws_cf_staging_beb841c2f4d9475a',
  'ws_cf_staging_20261015_7361_7f4c92',
  'ws_cf_staging_20261015_login_a62e71',
]);

export function isAuthorizedStagingWorkspace(workspaceId, envStagingId) {
  if (typeof workspaceId !== 'string') {
    return false;
  }
  // Production workspaces are never accepted by staging Worker
  if (workspaceId === 'ws_G7RZespbU6cwIUha' || workspaceId === 'ws_QlVAEPVTtYSxLeWC') {
    return false;
  }
  if (envStagingId && workspaceId === envStagingId) {
    return true;
  }
  return workspaceId.startsWith('ws_cf_staging_') && AUTHORIZED_STAGING_WORKSPACES.has(workspaceId);
}

import { verifyFirebaseIdToken, googleAccessToken, verifyCurrentUser, AuthError } from './google-auth.mjs';
import { FirestoreRest, FirestoreError } from './firestore-rest.mjs';
import { OperationError, beginWorkspaceInvitation, redeemWorkspaceInvitation,
  deleteWorkspaceCustomer, deleteWorkspaceEntry } from './operations.mjs';

const PROJECT = 'trac-aidhunt';
const handlers = { beginWorkspaceInvitation, redeemWorkspaceInvitation,
  deleteWorkspaceCustomer, deleteWorkspaceEntry };
const HTTP_STATUS = { INVALID_ARGUMENT: 400, UNAUTHENTICATED: 401, PERMISSION_DENIED: 403,
  NOT_FOUND: 404, ALREADY_EXISTS: 409, FAILED_PRECONDITION: 412, ABORTED: 409,
  UNAVAILABLE: 503 };
function reply(body, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store', 'X-Content-Type-Options': 'nosniff' } });
}
function errorReply(error) {
  const known = error instanceof OperationError || error instanceof AuthError || error instanceof FirestoreError;
  const code = known ? error.status : 'INTERNAL';
  return reply({ error: { status: code, message: known ? error.message : 'Backend operation failed',
    ...(known && error.details ? { details: error.details } : {}) } }, HTTP_STATUS[code] || 500);
}
export async function handleRequest(request, env, dependencies = {}) {
  const fetcher = dependencies.fetcher || fetch;
  if (env.FIREBASE_PROJECT_ID !== PROJECT) return errorReply(new AuthError('Backend project is not configured', 'UNAVAILABLE'));
  const url = new URL(request.url);
  if (url.pathname === '/health' && request.method === 'GET') {
    return reply({ status: 'ok', projectId: PROJECT,
      credentialConfigured: Boolean(env.GOOGLE_SERVICE_ACCOUNT_JSON) });
  }
  const operation = url.pathname.slice(1);
  if (request.method !== 'POST' || !Object.hasOwn(handlers, operation) || url.pathname !== `/${operation}`)
    return reply({ error: { status: 'NOT_FOUND', message: 'Unknown operation' } }, 404);
  try {
    const header = request.headers.get('Authorization') || '';
    if (!/^Bearer [^\s]+$/.test(header)) throw new AuthError();
    const claims = await verifyFirebaseIdToken(header.slice(7), PROJECT, fetcher);
    const token = await googleAccessToken(env, PROJECT, fetcher);
    await verifyCurrentUser(claims, token, PROJECT, fetcher);
    if (Number(request.headers.get('Content-Length')) > 65536) throw new OperationError('INVALID_ARGUMENT', 'Request is too large');
    let payload;
    try { payload = await request.json(); } catch { throw new OperationError('INVALID_ARGUMENT', 'Invalid JSON body'); }
    if (!payload || typeof payload !== 'object' || Array.isArray(payload) ||
        Object.keys(payload).length !== 1 || !payload.data || typeof payload.data !== 'object' || Array.isArray(payload.data))
      throw new OperationError('INVALID_ARGUMENT', 'Expected callable data envelope');
    if (!isAuthorizedStagingWorkspace(payload.data.workspaceId, env.STAGING_WORKSPACE_ID))
      throw new OperationError('PERMISSION_DENIED', 'This staging Worker accepts only its isolated test workspace');
    const db = dependencies.db || new FirestoreRest(PROJECT, () => googleAccessToken(env, PROJECT, fetcher), fetcher);
    let targetClaims;
    if (operation === 'redeemWorkspaceInvitation') {
      if (typeof payload.data.targetIdToken !== 'string') throw new OperationError('INVALID_ARGUMENT', 'targetIdToken is required');
      targetClaims = await verifyFirebaseIdToken(payload.data.targetIdToken, PROJECT, fetcher);
      await verifyCurrentUser(targetClaims, token, PROJECT, fetcher);
    }
    const result = await handlers[operation](db, claims.uid, payload.data, targetClaims);
    return reply({ result });
  } catch (error) { return errorReply(error); }
}

export default { fetch: handleRequest };
