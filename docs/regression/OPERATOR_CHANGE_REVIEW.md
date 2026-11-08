# Operator basic profiles — change review and remaining gates

## Findings and correction

Canonical customer documents include financial balances, so granting Operators a
general customer read/create permission would expose or permit money fields.
The approved basic-profile workflow instead uses a strict metadata projection and
an atomic zero-finance create, with the existing durable CUSTOMER→JOB dependency.
Source inspection also found unconditional new-collection access incompatible with
older Rules/clients. Default-off verified capabilities now protect that rollout.
This does not prove the payload or amount of the original inaccessible live phone
operations; none was recovered, modified or automatically retried.

Checked deletions must remove optional metadata in every supported transport, or
other devices could retain deleted customer choices. Worker/Functions now do this
atomically and leave immutable ID proofs to block late resurrection. Timestamp
provenance preserves exact stored source timestamp values through the Worker REST
adapter; native Functions timestamps remain native. Historical IDs, financial
amounts, attribution and edit journals are copied to proofs without rewriting them.
Existing authorization, linked-collection checks and dependency protection remain.

## Modified/created files and purpose

Paths are relative to the repository. Generated test evidence remains outside Git.

| File | Purpose |
|---|---|
| app/src/main/java/com/example/data/entity/BasicCustomerEntity.kt | Metadata-only Room row; no money columns. |
| app/src/main/java/com/example/data/dao/BasicCustomerDao.kt | Workspace-scoped persistence and confirmed-absence reconciliation. |
| app/src/main/java/com/example/data/database/AppDatabase.kt | Additive Room21→22 migration and DAO; no destructive migration. |
| app/src/main/java/com/example/data/firebase/BasicCustomerMaps.kt | Strict field allowlist, immutable create receipt, raw names and unknown legacy provenance. |
| app/src/main/java/com/example/data/firebase/WorkspaceSyncCapabilities.kt | Default-off protocols, verified actor/workspace offline cache and transient pending wait. |
| app/src/main/java/com/example/data/firebase/FirestoreRepository.kt | Atomic projection transport, receipt replay, scoped listeners/health, authoritative capability checks and legacy path preservation. |
| app/src/main/java/com/example/data/firebase/FirebaseLedgerDeletion.kt | Remove projection in both checked customer deletion paths only when protocol is enabled. |
| app/src/main/java/com/example/data/repository/WorkspaceRepository.kt | Atomic local profile/outbox, dependency ordering, server ACK, retained pending data and scoped reconciliation. |
| app/src/main/java/com/example/data/sync/OperationPolicy.kt | Allow only the original active Operator's zero-finance basic create; preserve all financial denials. |
| app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt | Feed Operator choices from metadata and expose verified capabilities. |
| app/src/main/java/com/example/MainActivity.kt | Gate new controls on verified workspace support. |
| app/src/main/java/com/example/ui/screens/entry/NewEntryScreen.kt | Gate Operator new-profile control; hide financial dues in Operator suggestions; keep entered text raw. |
| firestore.rules | Exact projection/zero-balance/role/workspace validation, immutable receipts, atomic deletion and protected activation. |
| cloudflare/firestore-rest.mjs | Preserve recursive timestamp wire provenance separately from decoded values. |
| cloudflare/operations.mjs | Atomic directory cleanup and immutable source/job deletion proofs in trusted transport. |
| functions/index.js | Equivalent trusted deletion cleanup/proofs with native timestamps. |
| app/src/test/java/com/example/BasicCustomerDirectoryTest.kt | Projection privacy, exact identity, immutable create receipt and malformed-field rejection. |
| app/src/test/java/com/example/BasicCustomerOperationPolicyTest.kt | Original Operator create and removed/foreign/changed-role denials. |
| app/src/test/java/com/example/BasicCustomerRoomTest.kt | Durable original operations and additive migration preserving historical values/audits. |
| app/src/test/java/com/example/WorkspaceSyncCapabilitiesTest.kt | Missing flags, exact cache isolation/reopen, fresh deactivation and unchanged transient pending actions. |
| app/src/test/java/com/example/FirebaseOnlyDeletionEmulatorTest.kt | Both directory deletion paths and retained five-receipt Partner scenario against actual Rules. |
| app/src/test/java/com/example/ArchitectureIntegrationTest.kt | Register additive migration in existing architecture tests. |
| app/src/test/java/com/example/RoomScopeTest.kt | Register additive migration in existing Room tests. |
| app/src/androidTest/java/com/example/DeviceEmulatorIntegrationTest.kt | Mandatory w Operator/peer journey and x disabled-protocol legacy path; independent client uses atomic projection edits. |
| security-tests/operator-customer-profiles.test.cjs | Real Rules role/privacy/atomicity/receipt/activation/legacy protocol regression cases. |
| security-tests/expense-categories.test.cjs | Enabled category fixtures and disabled read/create/ACK denial with original row preserved. |
| cloudflare/firestore-rest.test.mjs | Timestamp type/path/precision provenance and existing integer behavior. |
| cloudflare/operations.test.mjs | Cleanup/retention, cross-workspace rejection, immutable proofs and exact replay/audit tests. |
| functions/entry-deletion-emulator.test.js | Trusted deletion atomicity, proofs, immutable audit timestamps and replay. |
| functions/device-test-fixtures.cjs | Synthetic second Operator and no-capability workspace; demo emulators only. |
| scripts/run_backend_suite.mjs | Include new Rules suite in mandatory existing gate. |
| docs/regression/asset-inventory.json | Regenerated source/test inventory. |
| docs/regression/interaction-inventory.csv | Regenerated interaction line references; expected behaviors preserved. |
| docs/regression/interaction-inventory.json | Regenerated interaction line references; expected behaviors preserved. |
| docs/regression/OPERATOR_BASIC_CUSTOMERS.md | Architecture, authorization boundaries and actual evidence status. |
| docs/regression/WORKSPACE_PROTOCOL_ROLLOUT.md | Compatibility defect, activation order and rollback restrictions. |
| docs/regression/OPERATOR_PHYSICAL_CHECKLIST.md | Separate manual/automated physical NOT TESTED scenarios and expected evidence. |
| docs/regression/OPERATOR_CHANGE_REVIEW.md | This review, file purposes and remaining gates. |

## Review boundaries and release status

No new service, database product, monitoring SDK or billing change. Room gains one
additive metadata table. Customer/business text is not translated. No profit,
investment, entitlement or repayment formula is changed. This phase does not
implement the remaining approved loans/borrowing/notifications/audit-archival work.

Local app727/727 (97 classes), Rules373/373, backend137/137 and Python110/110
passed, with zero failures/errors/skips. Final debug build/lint/instrumentation compile,
publication scan and exact-source GitHub/Android runtime gates are pending at this
checkpoint. Do not treat earlier expense CI as proof of this branch.

Production configuration/deployed Rules/installed APK compatibility still require
read-only verification before any approved rollout. No live customer record, queue,
capability flag, production Rule, Worker or Function was changed. No main merge or
customer APK installation occurred. Both physical testing categories are NOT TESTED.
