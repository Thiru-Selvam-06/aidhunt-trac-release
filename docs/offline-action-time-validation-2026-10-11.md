# Offline action-time implementation and local validation — 2026-10-11

Worktree: C:\Users\user\.codex\worktrees\v16-implementation\aidhunt-trac
Branch: codex/v1.6-implementation
HEAD remains 0bf772b9dd3a7d7ebfaae7ebcd1e96ca6f1fe141. Existing worktree changes preserved; no commits, pushes, branch changes or production operations. Original Desktop checkout untouched.

## Implemented

Original immutable createdAt defines the authorization window. Each operation freezes actual actor and action time before local work; mutations and queue records commit together in Room. Original creation survives first upload. Stable UUID, sequence, snapshots and dependencies survive retries/restart. Acknowledged snapshots remain in local history and do not block pending work. The cloud mirrors original creation as authorizationCreatedAt and records serverReceivedAt separately; deletion receipts retain server deletedAt plus original actionTime. Rules and trusted deletion functions use the same strict action-time window.

Expired operations reject before upload, retain an identifying explanation and payload, and do not retry as mutations. Explicit server recovery restores Room and Customer totals. Reversal recovery restores source financial state atomically. Recovery failure remains visible and retries independently. Reconciled own rejections can be superseded by a fresh authorized action while retaining history; dependent or unrecovered work is not silently discarded. Workspace/actor changes stop mutation or recovery.

## Demonstrated

Emulator and production Room recovery component tests cover create hour 0, A hour 18, rejected B hour 26, reconnect hour 30. Original creation stays hour 0; accepted A remains the final shared and restored local state, for same-field and different-field B edits. Exact hour 24 rejects. Restart preserves operation identity, timestamp, payload and retry count. Backdating tests intentionally demonstrate the accepted clock-manipulation limitation while other-actor, revoked-member, workspace, historical, Customer and financial checks remain enforced.

## Changed files

- firestore.rules
- functions/index.js
- functions/entry-deletion-emulator.test.js
- functions/entry-deletion-http.test.js
- security-tests/architecture.test.cjs
- app/src/main/java/com/example/data/auth/AuthorizationManager.kt
- app/src/main/java/com/example/data/dao/PendingOperationDao.kt
- app/src/main/java/com/example/data/firebase/FirestoreRepository.kt
- app/src/main/java/com/example/data/firebase/WorkspaceCommandProtocol.kt
- app/src/main/java/com/example/data/firebase/WorkspaceCommandService.kt
- app/src/main/java/com/example/data/repository/WorkspaceRepository.kt
- app/src/main/java/com/example/data/sync/OperationPolicy.kt
- app/src/main/java/com/example/data/sync/OfflineEntryRecovery.kt
- app/src/test/java/com/example/WorkspaceCommandProtocolTest.kt
- app/src/test/java/com/example/OfflineActionTimeTest.kt
- docs/offline-action-time-addendum-v1.0-2026-10-11.md
- docs/firestore-authorization-rollout.md
- docs/delete-due-reconciliation.md
- docs/offline-action-time-validation-2026-10-11.md (this report)

## Tested and passed

Firestore Emulator v1.22.0, 127.0.0.1:18180, project demo-aidhunt-v16, complete local firestore.rules. No production tests. Emulator stopped after validation.

Combined command:

node --test --test-concurrency=1 security-tests/architecture.test.cjs security-tests/listener-queries.test.cjs functions/entry-deletion-policy.test.js functions/entry-deletion-emulator.test.js functions/entry-deletion-http.test.js functions/invitation-policy.test.js

245 passed, 0 failed: 164 Rules/listener tests and 81 server/deletion/invitation tests. Loading the full rules compiled successfully. HTTP tests execute actual callable wrappers and local Firestore transactions with only token verification doubled; they do not prove live JWT/IAM integration.

Android command:

.\gradlew.bat compileDebugKotlin testDebugUnitTest --tests com.example.OfflineActionTimeTest --tests com.example.WorkspaceCommandProtocolTest --tests com.example.EntryDeletionReconciliationTest --tests com.example.RoomScopeTest --tests com.example.PaymentReversalAndCollectionHistoryTest --tests com.example.EntryMutationAuthorizationTest --tests com.example.ArchitectureIntegrationTest --tests com.example.EntryCreationTimestampPersistenceTest --no-daemon --no-configuration-cache

Final build successful in 2m 13s; 81 tests, 0 failures/errors, verified from XML. Includes five focused offline tests and existing bounded queue/migration, financial, deletion and authorization regressions. Existing compiler deprecation/opt-in warnings remain. git diff --check passed.

## Tested and failed during implementation, then corrected

Collection fixture receipt timestamps incorrectly changed on reversal; expectations for missing legacy receipt fields were updated to the explicitly approved createdAt policy while inactive/removed membership assertions continue to deny. An acknowledgement compared equivalent integer values using Java boxed types; numeric action-time comparison was corrected. Final suites above have zero failures.

## Not yet tested and rollout prerequisites

No connected-device cold start, process kill during live sync, real account switching mid-sync, UI responsiveness benchmark, or production callable authentication test in this task. Room/emulator tests are component evidence, not claims of full real-device verification. No production records inventoried, backed up, migrated or sanitized.

Legacy original createdAt provenance and a coordinated compatible Android/Rules/function rollout require review. Older Delete Due clients omit required actionTime. Acknowledged history retention increases local storage; no long-term storage growth benchmark performed. Later snapshots depending on rejected actions remain blocked for review instead of automatically rebasing potentially contaminated full snapshots.

Client clock/backdating manipulation is expressly accepted, not cryptographic proof. No production deployment authorized or performed. Local candidate is ready for deployment review subject to these rollout/device checks.
