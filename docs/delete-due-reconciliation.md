# Approved offline policy update — 2026-10-11

[Addendum v1.0](offline-action-time-addendum-v1.0-2026-10-11.md) supersedes the server-arrival deadline policy below. Immutable original createdAt and per-operation actionTime now determine eligibility. Client backdating is explicitly accepted. Delete Due requires stable actionTime; receipts bind retries to it. No deployment or production migration has occurred. Prior results below describe the earlier implementation.

## Previous decision and validation retained for traceability

# Delete Due reconciliation

Normal Delete Due hard-deletes its source Entry. Collection records are never deleted by this workflow; all linked active Collections (including split allocations) must first be reversed through Collection History. Linked Expenses must be deleted independently through their existing authorization checks, then acknowledged before source deletion. Customers remain retained.

Room removes the Entry and recalculates Customer balances in the same transaction that inserts its actor/workspace-bound DELETE snapshot. The snapshot includes pending linked operation dependencies. Remote snapshots consult the durable queue to avoid resurrecting a pending deletion after a restart. Permanent rejection restores the current server Entry when readable, otherwise its preserved snapshot, and recalculates local totals. The rejected operation remains visible; an authorized retry invalidates only that actor's rejected DELETE, preserving other pending work.

The deleteWorkspaceEntry callable transaction revalidates active membership, actual Owner identity, creator/target membership, Historical Entry restrictions and the immutable server creation timestamp. Partner/Operator eligibility uses server time, never client actionTime. The transaction reads workspace Collections and Expenses, blocks unresolved dependencies, hard-deletes the Entry, recalculates retained Customer totals, and writes an immutable operation receipt. Repeating the same acknowledged actor/operation returns success without applying financial effects again.

Normal direct Firestore Entry deletion and Entry tombstones are denied. The separate existing authorized member purge remains unchanged. Collection reversals, other record deletion proofs, Customer-reference and creation-timestamp safeguards remain in force.

## Release prerequisites and limits

Nothing was deployed. The callable function, rules and updated client require a coordinated, separately approved release. Existing clients using direct Entry deletion will be denied by the new rules; the new client retains unconfirmed deletion in its durable queue if the callable is unavailable. No database schema migration or live-record backfill was performed.

The command scans workspace Collection/Expense history to safely handle existing JSON split allocations and numeric/string Expense references. Cost/contention at large workspace sizes has not been benchmarked. Local tests now cover both direct handlers and the real callable HTTP wrapper. The HTTP tests double only Admin token verification and use the local Firestore Emulator. Real-device Firebase token issuance/verification, airplane-mode process restarts and ambiguous network acknowledgements still require device validation.

A fully disconnected client cannot prove an earlier action time. Non-Owner deletion first reaching the service after its trusted 24-hour deadline is rejected and recovered; a server-confirmed receipt permits later acknowledgement retries.

## Changed files in this task

- app/src/main/java/com/example/data/repository/WorkspaceRepository.kt
- app/src/main/java/com/example/data/firebase/FirestoreRepository.kt
- app/src/main/java/com/example/data/firebase/WorkspaceCommandService.kt
- app/src/main/java/com/example/data/dao/PendingOperationDao.kt
- app/src/main/java/com/example/data/sync/OperationDependencies.kt
- app/src/main/java/com/example/data/sync/EntryDeletionPolicy.kt
- app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt
- app/src/main/java/com/example/ui/screens/report/CustomerCreditDueTab.kt
- app/src/main/java/com/example/ui/screens/report/ReportScreen.kt
- app/src/test/java/com/example/RoomScopeTest.kt
- app/src/test/java/com/example/EntryDeletionReconciliationTest.kt
- firestore.rules
- functions/index.js
- functions/entry-deletion-policy.js
- functions/entry-deletion-policy.test.js
- functions/entry-deletion-emulator.test.js
- security-tests/architecture.test.cjs
- docs/delete-due-reconciliation.md

## Local validation

The existing architecture/listener Rules command passed 154 tests with no failures:

    node --test --test-concurrency=1 security-tests/architecture.test.cjs security-tests/listener-queries.test.cjs

The new transaction/policy command passed 22 tests with no failures:

    node --test --test-concurrency=1 functions/entry-deletion-policy.test.js functions/entry-deletion-emulator.test.js

Both use FIRESTORE_EMULATOR_HOST=127.0.0.1:18180 and GCLOUD_PROJECT=demo-aidhunt-v16. The Emulator compiled the complete local rules. The callable tests execute real Firestore transactions against this Emulator, with test authentication passed to the callable handler; they do not validate production HTTP token verification.

Android compilation and the selected application regressions use:

    .\gradlew.bat compileDebugKotlin testDebugUnitTest --tests com.example.EntryDeletionReconciliationTest --tests com.example.RoomScopeTest --tests com.example.PaymentReversalAndCollectionHistoryTest --tests com.example.EntryMutationAuthorizationTest --tests com.example.ArchitectureIntegrationTest --no-daemon --no-configuration-cache

The final run passed Kotlin compilation and all 61 selected tests with zero failures, errors or skipped tests (BUILD SUCCESSFUL, 1m 44s). Device tests, large-history performance, production Firebase configuration and live migrations were not run. No deployment, commit or push was performed.

## Cloud Function / Rules / client integration completion

Completed locally on codex/v1.6-implementation with HEAD unchanged at 0bf772b9dd3a7d7ebfaae7ebcd1e96ca6f1fe141. The local app and CLI both select trac-aidhunt, project number 898996717587. No production requests, deployment, commit, push, live migration or original-checkout changes were made.

The endpoint is a Firebase v2 callable, not an unauthenticated custom HTTP handler. Its installed SDK verifies the Authorization bearer token and constructs request.auth; business authorization uses that UID. Authentication is checked before request-field validation on all existing callable endpoints. Delete Due accepts exactly workspaceId, entryId and operationId; client actor/creator/collector/role/time overrides are rejected.

The server validates source Customer identity, same-workspace ownership and the non-deleted state with the same boolean semantics as Rules. Numeric and legacy string Customer references are reconciled consistently without migrating records. A successful deletion or receipt retry returns deleted=true plus workspaceId, entryId and operationId. Android checks the exact acknowledgement before acknowledging its queue item.

Android obtains its Firebase ID token, binds transmission to the original actor, refreshes once following UNAUTHENTICATED/401, and preserves pending work when reauthentication or account switching is required. Permanent permission/precondition/schema errors continue through existing rejection recovery. Login/session failures now supply actionable sync messages instead of being silently treated as generic network errors. The client and server never use client actionTime to establish the protected deadline.

The Rules source remains unchanged in this integration task (SHA256 18A9FB290E932413CC40496A4D80D2CDBBA7726CCD2D1DBF2855C8D6E543C48D). New emulator tests prove that Owner, Partner and Operator cannot forge, edit or delete trusted operation receipts. Direct ordinary Entry deletion remains denied; the Admin transaction is the supported path.

### Files changed in this integration task

- functions/index.js
- functions/entry-deletion-http.test.js (new)
- app/src/main/java/com/example/data/firebase/WorkspaceCommandProtocol.kt (new)
- app/src/main/java/com/example/data/firebase/WorkspaceCommandService.kt
- app/src/main/java/com/example/data/firebase/FirestoreRepository.kt
- app/src/main/java/com/example/data/repository/WorkspaceRepository.kt
- app/src/test/java/com/example/WorkspaceCommandProtocolTest.kt (new)
- security-tests/architecture.test.cjs
- docs/delete-due-reconciliation.md

### Final local results

- Rules compilation and architecture/listener suites: 157 passed, 0 failed/skipped.
- Deletion policy and direct transaction tests: 22 passed.
- Callable HTTP tests: 41 passed (real HTTP wrapper with local token-verification doubles).
- Existing invitation/security policy tests: 10 passed.
- Combined server suites: 73 passed, 0 failed/skipped.
- Kotlin compilation and six Android regression suites: 74 passed, 0 failures/errors/skipped; BUILD SUCCESSFUL.
- Initial server run: three missing-authentication HTTP tests failed because existing endpoints validated fields first. Authentication ordering was corrected and the assertions retained; final reruns have zero failures.
- Source whitespace checks and Node syntax checks passed.

Commands (local FIRESTORE_EMULATOR_HOST=127.0.0.1:18180, GCLOUD_PROJECT=demo-aidhunt-v16):

    node --test --test-concurrency=1 security-tests/architecture.test.cjs security-tests/listener-queries.test.cjs
    node --test --test-concurrency=1 functions/entry-deletion-policy.test.js functions/entry-deletion-emulator.test.js functions/entry-deletion-http.test.js functions/invitation-policy.test.js
    .\gradlew.bat compileDebugKotlin testDebugUnitTest --tests com.example.WorkspaceCommandProtocolTest --tests com.example.EntryDeletionReconciliationTest --tests com.example.RoomScopeTest --tests com.example.PaymentReversalAndCollectionHistoryTest --tests com.example.EntryMutationAuthorizationTest --tests com.example.ArchitectureIntegrationTest --no-daemon --no-configuration-cache

### Remaining rollout gates

A coordinated function/Rules/client rollout requires separate approval. The deployed environment was not queried or changed during this task. Hosting/IAM/endpoint access, real Firebase-issued token verification on a device, device cold starts/network interruption and large-history transaction costs remain unverified. HTTP authentication tests exercise the actual callable SDK wrapper but substitute its token-verification dependency; they do not claim production cryptographic or IAM verification. Fully offline actions reaching the server beyond the trusted deadline still cannot be accepted based on a client timestamp.
