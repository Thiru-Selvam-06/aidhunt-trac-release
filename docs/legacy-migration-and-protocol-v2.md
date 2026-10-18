# Legacy migration and operation protocol v2

Local implementation only. No production migration, deployment, or data writes were performed. Worktree: C:\Users\user\.codex\worktrees\v16-implementation\aidhunt-trac.

## Migration behavior

Utility: functions/migration/legacy-timestamps.js (existing Firebase Admin SDK). Scans entries, expenses, withdrawals, collections, customers, tractors and extensions in one explicitly selected workspace, using 100-document read pages. Manifest memory grows with the workspace inventory; split very large migrations by workspace and assess memory before production use.

| Classification | Behavior |
|---|---|
| SAFE_RETAIN | Retains validated records with an Admin migration receipt, matching signed evidence, or permissions without a creation-time limit. |
| VERIFIED_BACKFILL | Restores only an original creation timestamp established by signed, independently reviewed evidence; marks VERIFIED. |
| OWNER_REVIEW | Preserves existing timestamps; proposes OWNER_REVIEW_REQUIRED. Partner/Operator timed mutations stay denied. |
| MANUAL_MAPPING | Requires creator, beneficiary, membership, attribution or historical classification review. Does not invent identities or historical status. |
| REFERENCE_RECONCILIATION | Requires financial/reference repair and a new reviewed plan. Never rewrites financial payloads or fixes totals automatically. |

A syntactically valid client timestamp and the present Firestore document creation time are not evidence of original business-record authorization time. No missing timestamp is replaced with now. The accepted client action-time/clock-manipulation policy remains unchanged for current protocol operations.

Each proposed change is bound to document revision and full-record fingerprint. Apply rereads the document and its referenced records in a transaction, reclassifies it, and refuses stale plans. Receipts make a commit followed by a crash before checkpoint writing recoverable. Checkpoints are saved atomically after each item; rerunning the same manifest returns ALREADY_APPLIED or reports conflicts without overwriting concurrent changes. Before/after classifications, document counts and per-item outcomes support reconciliation. Manifests contain paths, fingerprints, reasons and timestamp metadata, not customer details or financial payloads. Paths remain sensitive identifiers. New private output directories and files receive owner-only permissions; Windows uses the current user's SID. Output and checkpoint files must remain outside commits.

## Commands and reviewed provenance

Run from the release worktree with existing authorized Admin credentials. No credentials belong in flags, logs or manifests. These commands are preparation examples, not permission to execute against production:

~~~powershell
node functions/migration/legacy-timestamps.js --mode=inventory --project=trac-aidhunt --workspace=WORKSPACE_ID --out=migration-private/inventory.json
node functions/migration/legacy-timestamps.js --mode=dry-run --project=trac-aidhunt --workspace=WORKSPACE_ID --out=migration-private/plan.json --evidence=PRIVATE_EVIDENCE_FILE --evidence-key=PINNED_PUBLIC_KEY_FILE --evidence-sha256=REVIEWED_FILE_SHA256
~~~

Without evidence, inventory/dry-run can classify records but cannot approve timestamp backfill. Evidence is JSON with body and a Base64 signature. Sign the UTF-8 bytes of JSON.stringify(body) using SHA-256 and an administrator-controlled private key; supply its independently pinned public key and reviewed evidence-file digest. body contains projectId, workspaceId, and records keyed by full record path. Each record attestation requires sourceKind VERIFIED_ORIGINAL_CREATION, documentUpdateTime as milliseconds:nanoseconds, originalCreatedAt in epoch milliseconds, reviewedBy, sourceReference and sourceSha256. The signature verifies the reviewer attestation, not the underlying historical truth. The reviewer must independently establish original creation provenance from a trustworthy audit/export source. Never sign an unverified client field. Keep keys and source evidence outside Git.

Apply requires --mode=apply, the same project/workspace/evidence arguments, --manifest=REVIEWED_PLAN, --out=PRIVATE_CHECKPOINT, and --confirm=APPLY:PROJECT:WORKSPACE:EXACT_MANIFEST_FILE_SHA256. Non-Emulator apply additionally requires --allow-production-apply=APPROVED. This software gate does not replace separate human approval. Only demo projects on local 127.0.0.1:18180 are permitted when FIRESTORE_EMULATOR_HOST is set.

Before future production apply: confirm project trac-aidhunt/898996717587 and authorized IAM identity; take and verify a recoverable backup; review signed original-time evidence and identity/historical/financial exceptions; review the complete dry-run manifest and digest; protect private artifacts; arrange write coordination and conflict handling; approve migration separately. Conflicts and unresolved records require a new inventory and reviewed plan. Rules, Functions and clients require a coordinated rollout review because old clients will be incompatible. No migration repairs identity mappings or financial inconsistencies automatically.

## Compatibility contract

Protocol v2 requires a stable operationId, workspaceId, actor context, operation type, dependency list and original positive actionTime. Queue JSON stores operationProtocolVersion=2; Firestore envelopes use protocolVersion=2. Callable Delete Entry/Delete Customer carry DELETE_ENTRY/DELETE_CUSTOMER respectively; direct document mutations carry UPSERT/DELETE. Authentication determines server UID; actorContext only asserts that the queued identity matches it. Retries preserve IDs and timestamps.

Callable legacy requests receive FAILED_PRECONDITION with details.code=UPGRADE_REQUIRED and requiredProtocolVersion=2 (Firebase callable HTTP status 400). Missing/invalid authentication remains rejected through existing authentication handling. Firestore Rules deny unsupported envelopes; they cannot emit custom structured errors. Current Android queue processing recognizes incompatible legacy rows before upload, marks them permanently rejected, retains their payload/ID/time, and exposes the actionable failure reason through existing pending-operation UI. Rejected rows are excluded from automatic retry batches. Updating the app does not recreate the missing historical action timestamp or silently discard/reauthorize that action; the original account must review retained records and resolve them through an explicitly authorized fresh action where appropriate.

New timestamp-limited records receive CLIENT_ORIGINAL_V2 at creation. Rules preserve that marker and original creation metadata on mutation. Legacy records without recognized provenance remain Owner-managed; only Admin verified migration can mark VERIFIED. Owner authority, historical-entry separation, workspace isolation, Customer integrity and collection-reversal restrictions remain enforced.

## Files changed for this task

- functions/migration/legacy-timestamps.js and legacy-timestamps.test.js: migration CLI, classifications, receipts, checkpoints and regression tests.
- functions/operation-protocol.js and functions/index.js: required v2 requests, authenticated actor context, legacy provenance restriction.
- firestore.rules: v2 envelopes and immutable provenance gates.
- app/src/main/java/com/example/data/firebase/WorkspaceCommandProtocol.kt, WorkspaceCommandService.kt and FirestoreRepository.kt: wire contract, structured upgrade error and preserved retry context.
- app/src/main/java/com/example/data/sync/OperationPolicy.kt and data/repository/WorkspaceRepository.kt: durable protocol metadata and recoverable incompatible queue entries.
- security-tests/architecture.test.cjs; functions/entry-deletion-http.test.js; functions/entry-deletion-emulator.test.js; app/src/test/java/com/example/OfflineActionTimeTest.kt and WorkspaceCommandProtocolTest.kt: regression fixtures and tests.
- .gitignore: excludes migration-private output.

Existing uncommitted implementation changes were preserved; no schema migration, original Desktop edit, commit or push was performed.

## Local validation (2026-10-11)

Rules compiled and were exercised against the demo Firestore Emulator on 127.0.0.1:18180, never production. The combined run passed 264/264 tests; after additional CLI, signature and resource-reference coverage, the migration suite passed 16/16 (268 distinct targeted Node tests across the final suite versions).

~~~powershell
$env:FIRESTORE_EMULATOR_HOST='127.0.0.1:18180'
$env:GCLOUD_PROJECT='demo-aidhunt-v16'
node --test --test-concurrency=1 security-tests/architecture.test.cjs security-tests/listener-queries.test.cjs functions/entry-deletion-policy.test.js functions/entry-deletion-emulator.test.js functions/entry-deletion-http.test.js functions/invitation-policy.test.js functions/migration/legacy-timestamps.test.js
node --test --test-concurrency=1 functions/migration/legacy-timestamps.test.js
.\gradlew.bat compileDebugKotlin testDebugUnitTest --tests com.example.OfflineActionTimeTest --tests com.example.WorkspaceCommandProtocolTest --tests com.example.EntryDeletionReconciliationTest --tests com.example.RoomScopeTest --tests com.example.PaymentReversalAndCollectionHistoryTest --tests com.example.EntryMutationAuthorizationTest --tests com.example.ArchitectureIntegrationTest --tests com.example.EntryCreationTimestampPersistenceTest --no-daemon --no-configuration-cache
~~~

Android compilation and 83 selected unit/Robolectric regressions passed. Existing compiler deprecation/opt-in warnings remain. Initial test failures in HTTP response expectations and Windows SID formatting were corrected and rerun; no known failure remains in these targeted suites. git diff --check passed. Branch remains codex/v1.6-implementation at 0bf772b9dd3a7d7ebfaae7ebcd1e96ca6f1fe141.

Not exercised: production credentials/IAM, live migration, production deployment, full lint/release build, connected-phone upgrade-warning display or offline cold start. Local test doubles verify callable authentication/identity; the local Emulator verifies actual Rules and transaction behavior. Private signed provenance and source mapping for actual legacy records still require administrator review. The output directory contains identifiers and must be handled privately even though business payloads are excluded.
