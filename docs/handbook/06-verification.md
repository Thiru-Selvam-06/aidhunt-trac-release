# 6. Verification and CI

One Android CI workflow runs on main/master/codex pushes, PRs and manual dispatch using demo-aidhunt-v16, loopback emulators and no production secrets/deployments.

| Job | Evidence scope |
|---|---|
| inventory | Python guards, control candidates, explicit suite assignments |
| static-checks | Synthetic-config debug build/lint |
| android unit | Domain policies, arithmetic, validation |
| android database | Real SQLite/Robolectric, workspace scoping, synthetic migration fixtures |
| android compose | Selected Robolectric controls/dialogs |
| android outbox | Commands, protocol, retries, dependencies, recovery |
| backend | Worker/HTTP/policies/migration tests |
| rules | Firestore Emulator authorization and trusted integration |
| device integration | Android emulator, Room, demo Auth/Firestore/Functions, selected controls |
| aggregate | Commit/run identity, actual JUnit and job outcomes |

None alone proves all UI→backend journeys or deployed services. Coverage reports are per partition, not additive percentages. Required missing JUnit artifacts and failed jobs fail the aggregate. Skipped/unmapped scenarios remain visible.

interaction_inventory.py indexes callbacks/clickable candidates and test references; no source row is automatically PASS. Runtime-generated controls, navigation reachability, nested callbacks and gestures still need review. Regenerate after adding controls.

suite_inventory.py requires each backend test file in exactly one partition and every instrumentation class either runnable or explicitly blocked. instrumentation-plan.json is consumed by the device runner. Blocked classes are not executed/skipped test results; they are unexecuted coverage gaps.

ci-tests.gradle derives Kotlin package names and assigns disjoint unit/database/compose/outbox groups. Add a regression when changing discovery so suites cannot silently disappear.

For a complete scenario: map requirement/control ID, test allowed/denied roles, Room transaction, command identity/dependencies, backend persisted result, acknowledgement, displayed totals, reopen/restart and failure recovery. Independently calculate expected money. UI visibility is not backend denial evidence.

PhoneRegistrationDeviceTest, PurgeProtocolDeviceTest and WorkspaceSelectionDeviceTest require additional fixtures/auth and remain blocked in the representative runner. Real SMS, physical-device acceptance and all-controls coverage are not claimed.
