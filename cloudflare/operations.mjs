import { SERVER_TIMESTAMP } from './firestore-rest.mjs';
import invitationPolicy from '../functions/invitation-policy.js';
import deletionPolicy from '../functions/entry-deletion-policy.js';

export class OperationError extends Error {
  constructor(status, message, details) { super(message); this.status = status; this.details = details; }
}
const fail = (status, message, details) => { throw new OperationError(status, message, details); };
const maxId = 9223372036854775807n;
const path = (ws, collection, id) => `workspaces/${ws}/${collection}/${id}`;
function required(data, key, identity = false) {
  const value = data?.[key];
  if (typeof value !== 'string' || !value.trim()) fail('INVALID_ARGUMENT', `${key} is required`);
  if (identity && (value.includes('/') || value.length > 512 || value === '.' || value === '..')) fail('INVALID_ARGUMENT', 'Invalid document identity');
  return value.trim();
}
function recordId(data, key) {
  const id = required(data, key, true);
  if (!/^[1-9][0-9]{0,18}$/.test(id) || BigInt(id) > maxId) fail('INVALID_ARGUMENT', `Invalid ${key}`);
  return id;
}
function upper(value) { return typeof value === 'string' ? value.toUpperCase() : ''; }
function memberActive(member, ws) { return member.exists && upper(member.get('status')) === 'ACTIVE' &&
  (member.get('workspaceId') == null || member.get('workspaceId') === ws); }
function ownerActive(workspace, member, ws, uid) {
  return workspace.exists && memberActive(member, ws) && upper(member.get('role')) === 'OWNER' && workspace.get('ownerUid') === uid;
}
async function ownerInTransaction(tx, ws, uid) {
  const [workspace, member] = await Promise.all([tx.get(`workspaces/${ws}`), tx.get(path(ws, 'members', uid))]);
  if (!ownerActive(workspace, member, ws, uid)) fail('PERMISSION_DENIED', 'Only the active workspace Owner may perform this action');
}
function protocol(data, uid, type) {
  if (!data || data.protocolVersion !== 2 || !Number.isSafeInteger(data.actionTime) || data.actionTime <= 0 ||
      !data.operationId || !data.workspaceId || !data.actorContext || !data.operationType || !Array.isArray(data.dependencies))
    fail('FAILED_PRECONDITION', 'Update AIDHUNT Trac and review retained pending actions; original timestamps must not be recreated.',
      { code: 'UPGRADE_REQUIRED', requiredProtocolVersion: 2 });
  if (data.actorContext !== uid) fail('PERMISSION_DENIED', 'Authenticated actor does not match operation context');
  if (data.operationType !== type || data.actionTime > Date.now() || !/^[A-Za-z0-9-]{1,128}$/.test(data.operationId) ||
      data.dependencies.length > 100 || data.dependencies.some(dependency => !dependency ||
        !['JOB', 'CUSTOMER', 'EXPENSE', 'PAYMENT', 'TRACTOR', 'EXTENSION'].includes(dependency.entityType) ||
        typeof dependency.entityId !== 'string' || !/^[1-9][0-9]{0,18}$/.test(dependency.entityId) ||
        BigInt(dependency.entityId) > maxId)) fail('INVALID_ARGUMENT', 'Invalid operation protocol fields');
}

export async function beginWorkspaceInvitation(db, uid, data) {
  const ws = required(data, 'workspaceId', true), phone = required(data, 'phone');
  const role = upper(required(data, 'role')), name = required(data, 'name');
  if (!/^\+[1-9][0-9]{7,14}$/.test(phone) || !['PARTNER', 'OPERATOR'].includes(role))
    fail('INVALID_ARGUMENT', 'Choose a valid phone and Partner or Operator role');
  const invitationId = data.operationId == null ? crypto.randomUUID() : required(data, 'operationId', true);
  if (!/^[A-Za-z0-9-]{1,128}$/.test(invitationId)) fail('INVALID_ARGUMENT', 'Invalid invitation operation identity');
  const now = Date.now(), expiresAt = now + 300000;
  return db.transaction(async tx => {
    await ownerInTransaction(tx, ws, uid);
    const prior = await tx.get(path(ws, 'invitations', invitationId));
    if (prior.exists) {
      if (prior.get('workspaceId') !== ws || prior.get('ownerUid') !== uid || prior.get('phone') !== phone ||
          prior.get('role') !== role || prior.get('name') !== name)
        fail('PERMISSION_DENIED', 'Invitation operation identity mismatch');
      if (prior.get('status') !== 'PENDING' || Number(prior.get('expiresAt')) <= now)
        fail('FAILED_PRECONDITION', 'Invitation expired or was already used. Start a new invitation.');
      return { invitationId, expiresAt: Number(prior.get('expiresAt')) };
    }
    tx.create(path(ws, 'invitations', invitationId), { workspaceId: ws, ownerUid: uid, phone, role, name,
      status: 'PENDING', createdAt: now, expiresAt });
    return { invitationId, expiresAt };
  });
}

export async function redeemWorkspaceInvitation(db, uid, data, targetClaims) {
  const ws = required(data, 'workspaceId', true), id = required(data, 'invitationId', true);
  return db.transaction(async tx => {
    await ownerInTransaction(tx, ws, uid);
    const invitationDoc = await tx.get(path(ws, 'invitations', id));
    if (!invitationDoc.exists) fail('NOT_FOUND', 'Invitation not found');
    const invitation = invitationDoc.data;
    if (invitation.workspaceId !== ws) fail('PERMISSION_DENIED', 'Invitation workspace mismatch');
    if (invitation.status === 'USED') {
      const [receipt, membership] = await Promise.all([
        tx.get(`invitationProofReceipts/${targetClaims.uid}_${targetClaims.auth_time}`),
        tx.get(path(ws, 'members', targetClaims.uid))
      ]);
      try { return invitationPolicy.validateAcceptedInvitation(invitation, uid, targetClaims, receipt.data, membership.data, ws, id); }
      catch (error) { fail('PERMISSION_DENIED', error.message); }
    }
    let target;
    try { target = invitationPolicy.validateInvitation({ ...invitation,
      createdAt: Number(invitation.createdAt), expiresAt: Number(invitation.expiresAt) }, uid, targetClaims, Date.now()); }
    catch (error) { fail('PERMISSION_DENIED', error.message); }
    const [existing, removed, proof, directory] = await Promise.all([
      tx.get(path(ws, 'members', target.uid)), tx.get(path(ws, 'deletedUsers', target.uid)),
      tx.get(`invitationProofReceipts/${target.uid}_${targetClaims.auth_time}`),
      tx.get(`phoneDirectory/${invitation.phone}`)
    ]);
    if (proof.exists) fail('PERMISSION_DENIED', 'This OTP handshake was already consumed');
    if (directory.exists && (directory.get('uid') !== target.uid ||
        (directory.get('phone') != null && directory.get('phone') !== invitation.phone) ||
        (directory.get('phoneNumber') != null && directory.get('phoneNumber') !== invitation.phone)))
      fail('PERMISSION_DENIED', 'Phone identity is bound to another account');
    const now = Date.now();

    // v1.6 forbids role changes and reactivation. Keep removed-member audit history intact.
    if (removed.exists || (existing.exists && !memberActive(existing, ws)))
      fail('ALREADY_EXISTS', 'Removed membership cannot be reactivated in this workspace');
    if (existing.exists) {
      if (upper(existing.get('role')) !== target.role)
        fail('PERMISSION_DENIED', 'An invitation cannot change an existing member role');
      tx.set(`userWorkspaceMemberships/${target.uid}/workspaces/${ws}`, {
        workspaceId: ws, ownerUid: uid, role: target.role, status: 'ACTIVE', joinedAt: existing.get('joinedAt') || now
      });
      tx.update(path(ws, 'invitations', id), { status: 'USED', acceptedByUid: target.uid, usedAt: now });
      tx.create(`invitationProofReceipts/${target.uid}_${targetClaims.auth_time}`, {
        targetUid: target.uid, authTime: targetClaims.auth_time, workspaceId: ws, invitationId: id, usedAt: now
      });
      return { uid: target.uid, workspaceId: ws, role: target.role, alreadyActive: true };
    }

    // 3. Fresh member: create membership
    tx.create(path(ws, 'members', target.uid), { uid: target.uid, workspaceId: ws, role: target.role,
      status: 'ACTIVE', displayName: invitation.name, phoneNumber: invitation.phone, joinedAt: now,
      addedByUid: uid, invitedByUid: uid });
    tx.set(`userWorkspaceMemberships/${target.uid}/workspaces/${ws}`, { workspaceId: ws,
      ownerUid: uid, role: target.role, status: 'ACTIVE', joinedAt: now });
    // Phone-to-UID identity is global; role and status belong only to workspace membership.
    // Preserve existing legacy directory metadata when this UID joins another workspace.
    if (!directory.exists) tx.create(`phoneDirectory/${invitation.phone}`, { uid: target.uid, phone: invitation.phone });
    tx.update(path(ws, 'invitations', id), { status: 'USED', acceptedByUid: target.uid, usedAt: now });
    tx.create(`invitationProofReceipts/${target.uid}_${targetClaims.auth_time}`, { targetUid: target.uid,
      authTime: targetClaims.auth_time, workspaceId: ws, invitationId: id, usedAt: now });
    return { uid: target.uid, workspaceId: ws, role: target.role };
  });
}

export async function deleteWorkspaceCustomer(db, uid, data) {
  protocol(data, uid, 'DELETE_CUSTOMER');
  const ws = required(data, 'workspaceId', true), id = recordId(data, 'customerId');
  const operationId = required(data, 'operationId', true), actionTime = data.actionTime;
  const acknowledgement = { deleted: true, workspaceId: ws, customerId: id, operationId, actionTime };
  return db.transaction(async tx => {
    await ownerInTransaction(tx, ws, uid);
    const receiptPath = path(ws, 'customerDeleteReceipts', operationId);
    const [customer, prior, entries, collections] = await Promise.all([
      tx.get(path(ws, 'customers', id)), tx.get(receiptPath), tx.list(`workspaces/${ws}/entries`),
      tx.list(`workspaces/${ws}/collections`)
    ]);
    if (prior.exists) {
      if (prior.get('actorUid') !== uid || prior.get('customerId') !== id ||
          prior.get('workspaceId') !== ws || Number(prior.get('actionTime')) !== actionTime)
        fail('PERMISSION_DENIED', 'Customer deletion receipt mismatch');
      return acknowledgement;
    }
    if (!customer.exists) fail('FAILED_PRECONDITION', 'Customer is missing without a confirmed deletion receipt');
    if (customer.get('workspaceId') !== ws) fail('PERMISSION_DENIED', 'Customer workspace mismatch');
    const unsafeIdentity = item => typeof item.get('customerId') === 'number' && !Number.isSafeInteger(item.get('customerId'));
    if ([...entries, ...collections].some(unsafeIdentity))
      fail('FAILED_PRECONDITION', 'Legacy Customer references require identity reconciliation');
    if ([...entries, ...collections].some(item => String(item.get('customerId')) === id))
      fail('FAILED_PRECONDITION', 'Customer is referenced by retained business records');
    tx.delete(path(ws, 'customers', id));
    tx.create(receiptPath, { workspaceId: ws, customerId: id, actorUid: uid,
      operationId, actionTime, deletedAt: SERVER_TIMESTAMP });
    return acknowledgement;
  });
}

export async function deleteWorkspaceEntry(db, uid, data) {
  protocol(data, uid, 'DELETE_ENTRY');
  if (Object.keys(data).some(key => !['workspaceId', 'entryId', 'operationId', 'actionTime',
    'protocolVersion', 'actorContext', 'operationType', 'dependencies'].includes(key)))
    fail('INVALID_ARGUMENT', 'Delete Due contains unsupported fields');
  const ws = required(data, 'workspaceId', true), id = recordId(data, 'entryId');
  const operationId = required(data, 'operationId', true), actionTime = data.actionTime;
  const acknowledgement = { deleted: true, workspaceId: ws, entryId: id, operationId, actionTime };
  return db.transaction(async tx => {
    const [workspace, member, job, prior] = await Promise.all([
      tx.get(`workspaces/${ws}`), tx.get(path(ws, 'members', uid)), tx.get(path(ws, 'entries', id)),
      tx.get(path(ws, 'entryDeleteReceipts', operationId))
    ]);
    const role = upper(member.get('role'));
    if (!workspace.exists || !memberActive(member, ws) || !['OWNER', 'PARTNER', 'OPERATOR'].includes(role))
      fail('PERMISSION_DENIED', 'Active workspace membership required');
    if (prior.exists) {
      if (prior.get('actorUid') !== uid || prior.get('entryId') !== id || Number(prior.get('actionTime')) !== actionTime)
        fail('PERMISSION_DENIED', 'Deletion receipt mismatch');
      return acknowledgement;
    }
    if (!job.exists) fail('FAILED_PRECONDITION', 'Entry is missing without a confirmed deletion receipt');
    const entry = job.data, created = Number(entry.createdAt);
    const owner = role === 'OWNER' && workspace.get('ownerUid') === uid;
    if (entry.workspaceId !== ws || (!owner && (!['PARTNER', 'OPERATOR'].includes(role) ||
      entry.isHistorical !== false || entry.createdByUid !== uid ||
      !['CLIENT_ORIGINAL_V2', 'VERIFIED'].includes(entry.authorizationTimeStatus) ||
      !Number.isSafeInteger(created) || created <= 0 || actionTime < created || actionTime - created >= 86400000)))
      fail('PERMISSION_DENIED', 'Entry deletion is outside your permissions or deadline');
    if (!entry.createdByUid || !entry.entryForUid) fail('PERMISSION_DENIED', 'Entry member identities must be verified before deletion');
    const targets = [...new Set([entry.createdByUid, entry.entryForUid])];
    const members = await Promise.all(targets.map(target => tx.get(path(ws, 'members', target))));
    if (!owner && members.some(target => !memberActive(target, ws)))
      fail('PERMISSION_DENIED', 'Archived records require the member lifecycle workflow');
    const [collections, expenses] = await Promise.all([
      tx.list(`workspaces/${ws}/collections`), tx.list(`workspaces/${ws}/expenses`)
    ]);
    const linkedExpenses = expenses.filter(expense => String(expense.get('relatedJobId')) === id);
    if (expenses.some(expense => typeof expense.get('relatedJobId') === 'number' &&
      !Number.isSafeInteger(expense.get('relatedJobId'))))
      fail('FAILED_PRECONDITION', 'Legacy Expense references require identity reconciliation');
    for (const expense of linkedExpenses) {
      if (expense.get('workspaceId') !== ws || !/^[1-9][0-9]{0,18}$/.test(expense.id) || BigInt(expense.id) > maxId)
        fail('FAILED_PRECONDITION', 'Linked Expense identity must be reconciled');
      const creator = expense.get('createdByUid'), target = expense.get('expenseForUid');
      if (!creator || !target)
        fail('PERMISSION_DENIED', 'Linked Expense member identity is missing');
      if (!owner && (!(await memberActive(await tx.get(path(ws, 'members', creator)), ws)) ||
          !(await memberActive(await tx.get(path(ws, 'members', target)), ws))))
        fail('PERMISSION_DENIED', 'Linked Expense member is no longer active');
      const expenseCreated = Number(expense.get('createdAt'));
      if (!owner && (role !== 'PARTNER' || creator !== uid ||
          !['CLIENT_ORIGINAL_V2', 'VERIFIED'].includes(expense.get('authorizationTimeStatus')) ||
          !Number.isSafeInteger(expenseCreated) || expenseCreated <= 0 ||
          actionTime < expenseCreated || actionTime - expenseCreated >= 86400000))
        fail('PERMISSION_DENIED', 'You cannot delete a linked Expense owned by another member or outside 24 hours');
    }
    let collectionDeletes;
    try { collectionDeletes = deletionPolicy.plannedCollectionDeletes(entry, collections.map(item => item.data), id,
      owner ? 'OWNER' : role, uid, actionTime); }
    catch (error) { fail('FAILED_PRECONDITION', error.message); }
    const collectionDeleteIds = new Set(collections.filter(item => collectionDeletes.includes(item.data)).map(item => item.id));
    const customerId = String(entry.customerId);
    if (!/^[1-9][0-9]{0,18}$/.test(customerId) || BigInt(customerId) > maxId ||
      (typeof entry.customerId === 'number' && !Number.isSafeInteger(entry.customerId)))
      fail('FAILED_PRECONDITION', 'Source Customer identity must be reconciled');
    const [customer, customerEntries] = await Promise.all([
      tx.get(path(ws, 'customers', customerId)), tx.list(`workspaces/${ws}/entries`)
    ]);
    if (!customer.exists || customer.get('workspaceId') !== ws || (customer.get('isDeleted') ?? false) !== false)
      fail('FAILED_PRECONDITION', 'Source Customer must exist in this workspace');
    let totals;
    try { totals = deletionPolicy.customerTotals(customerEntries.filter(other => other.id !== id &&
      String(other.get('customerId')) === customerId).map(other => {
        if (other.get('workspaceId') !== ws) throw Error('Customer Entry workspace mismatch');
        return other.data;
      })); } catch (error) { fail('FAILED_PRECONDITION', error.message); }
    const customerPlan = deletionPolicy.customerAfterEntryDeletion(customerId,
      customerEntries.filter(other => other.id !== id).map(other => other.data),
      collections.filter(item => !collectionDeleteIds.has(item.id)).map(item => item.data), id);
    if (customerPlan.deleteCustomer && owner) tx.delete(path(ws, 'customers', customerId));
    else tx.update(path(ws, 'customers', customerId), totals);
    for (const collection of collections.filter(item => collectionDeleteIds.has(item.id)))
      tx.delete(path(ws, 'collections', collection.id));
    for (const expense of linkedExpenses) tx.delete(path(ws, 'expenses', expense.id));
    tx.delete(path(ws, 'entries', id));
    tx.create(path(ws, 'entryDeleteReceipts', operationId), { workspaceId: ws, entryId: id,
      actorUid: uid, operationId, actionTime, deletedExpenseIds: linkedExpenses.map(item => item.id),
      deletedCollectionIds: [...collectionDeleteIds],
      deletedCollectionAudit: collections.filter(item => collectionDeleteIds.has(item.id)).map(item => ({
        id: item.id, amount: item.get('amount') ?? null, customerId: item.get('customerId') ?? null,
        jobEntryId: item.get('jobEntryId') ?? null, collectedByUid: item.get('collectedByUid') ?? null,
        createdAt: item.get('createdAt') ?? null, allocationsJson: item.get('allocationsJson') ?? null })),
      sourceFinancialAudit: { totalAmount: entry.totalAmount, amountReceived: entry.amountReceived,
        pendingAmount: entry.pendingAmount, customerId: entry.customerId },
      createdAt: entry.createdAt ?? null, deletedAt: SERVER_TIMESTAMP });
    return acknowledgement;
  });
}
