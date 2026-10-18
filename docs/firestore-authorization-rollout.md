# Approved offline policy update — 2026-10-11

[Addendum v1.0](offline-action-time-addendum-v1.0-2026-10-11.md) supersedes the server-arrival deadline policy below. Immutable original createdAt and per-operation actionTime now determine eligibility. Client backdating is explicitly accepted. Delete Due requires stable actionTime; receipts bind retries to it. No deployment or production migration has occurred. Prior results below describe the earlier implementation.

## Previous decision and validation retained for traceability

# Firestore authorization rollout constraints

Local rules use request.time and immutable authorizationCreatedAt (Firestore Timestamp), established on creation by equality with request.time. Client actionTime and createdAt remain audit/local data, not authorization evidence. Business dates and Historical Entry classification remain separate.

The client requests FieldValue.serverTimestamp() only for a new timed record. Updates preserve the existing timestamp; already committed queue operation IDs are acknowledged without writing again. A server-authorized tombstone records authorizationDeletedAt; its physical deletion can finish later without trusting a client action timestamp. Current membership and ownership must still authorize deletion.

A new offline-created record obtains its authorization timestamp when Firebase first commits it. A queued edit arriving after the trusted 24-hour deadline is rejected. The current architecture cannot prove a fully offline action happened earlier; accepting a claimed actionTime would restore the vulnerability. Preserving delayed actions would require trusted authorization of the exact mutation before the deadline, not a timestamp guessed by a device.

## Required legacy preparation before production approval

No production documents were inspected or migrated in this task. Existing models contain client Long timestamps and no established server authorization timestamp. Missing/null trusted timestamps deny non-Owner time-limited mutations. Owner permissions remain subject to existing lifecycle and financial checks.

The older deployed rules permitted broad workspace-member writes. Therefore, even a pre-existing field named authorizationCreatedAt or authorizationDeletedAt cannot be assumed to have been server-established. A controlled Admin SDK migration must inventory and sanitize such fields, with backup and write coordination, before treating the new rules as authoritative. Do not copy old createdAt/actionTime values or use a fresh serverTimestamp to reopen old edit windows. Firestore server document metadata can establish cloud creation time, but it does not prove the historical author, client action time, or validity of a legacy deletion instruction. Records whose provenance is uncertain must remain Owner-managed.

Migration execution and any policy for reinstating non-Owner legacy mutation authority require a separate approved step. Client rollout must also account for old/import write paths which omit the new server field and will be denied by timed creation rules. No automatic production backfill or permissive legacy fallback is implemented.

## Local validation

Use the existing installed cloud-firestore-emulator-v1.22.0.jar with project demo-aidhunt-v16, host 127.0.0.1, port 18180, and this worktree's firestore.rules. Keep single-project-mode errors enabled. The unchanged security-tests/firebase.json references ../firestore.rules; Firebase CLI 15.33.0 rejects that relative path as outside its config directory. Direct emulator startup reuses the same rules, project, port, dependencies, and test framework without altering deployment configuration.

Run:

node --test --test-concurrency=1 security-tests/architecture.test.cjs security-tests/listener-queries.test.cjs

Deployment is not authorized by this document. Local passing tests do not certify production legacy provenance or real-device queue behavior.
