# Approved offline action-time addendum v1.0 — 2026-10-11

The user's explicit offline product decision supersedes the prior server-arrival authorization decision. Original v1.6 documentation is preserved.

Immutable record createdAt (epoch milliseconds) is the original local creation time and authorization window origin. First upload authorizationCreatedAt mirrors it as a Firestore Timestamp. Legacy authorizationCreatedAt remains immutable even when it differs; eligibility uses original createdAt, never a legacy receipt timestamp. New serverReceivedAt records first server commit. Delete Due receipts preserve original actionTime and server deletedAt. Firestore commit metadata provides server update time independently.

Each Room operation retains UUID, sequence, workspace, authenticated actor, type, target, immutable payload, dependencies and original actionTime. Mutation and enqueue share a Room transaction. Separate snapshots deliver in order, never coalesce. ACKNOWLEDGED snapshots are retained locally and excluded from pending counts, dependencies and UI. Earlier acknowledged rows already removed before this version require cloud recovery.

Non-Owner eligibility: createdAt <= actionTime < createdAt + 86400000. Upload arrival may be later. Current identity, membership, role, reference and financial checks remain mandatory. Owner authority, Historical Owner-only mutations and Partner unrestricted asset ownership remain unchanged.

Expired actions are REJECTED with payload, UUID, time and record-specific explanation; never automatically upload/retry. Earlier accepted and independent writes continue. Dependent snapshots remain blocked rather than smuggling rejected fields. Recovery reads server state, restores Room and recalculates Customer totals. If recovery cannot complete, a persistent warning identifies pending local reconciliation; retry recovery on next sync. Do not overwrite newer pending actions or cross session/workspace boundaries. Collection recovery restores source Entry too. A fresh authorized action may supersede its own reconciled rejection only when no related pending command remains; the original payload and failure explanation remain in history. Unrecovered or another actor's rejected work is not silently discarded.

Required hour 0/18/26/30: preserve creation 0, accept A at 18, reject B at 26, restore A at reconnect 30 for same/different fields. Existing local controls reject expired attempts immediately; queue tests exercise persisted expired commands independently.

## Explicit accepted limitation

Client clocks/actionTime are manipulable. Forged backdating inside the original window can authorize an otherwise expired own-record operation. This accepted product tradeoff is not cryptographic evidence of offline action time. It cannot change immutable creation/identity, impersonate roles, override revocation, allow Historical mutations, bypass Customer validation or waive financial integrity. Review legacy createdAt provenance before rollout; no production sanitization/migration performed.

## Rollout

Review Rules, callable actionTime contract and Android together. Old Delete Due clients omitting actionTime are rejected. Legacy pending IDs/timestamps remain unchanged. Separate deployment approval, backup and legacy inventory required. No deployment authorized. Real-device cold starts, clock changes, intermittent network and live callable authentication require device verification.
