# Execution results — 17 October 2026

This is evidence for the regression branch, not a declaration that every application workflow is complete.

## Source and publication

- Baseline: codex/v1.6-implementation at 0bf772b9dd3a7d7ebfaae7ebcd1e96ca6f1fe141, with 167 dirty status entries / 211 expanded paths.
- Published branch: codex/comprehensive-regression-2026-10-17.
- Tested commit: 937088dff3115d4e995dbf6d1dfbed1158ccbe8d.
- Latest run: https://github.com/Thiru-Selvam-06/aidhunt-trac-release/actions/runs/39024341077
- PR creation failed with GitHub HTTP 403: Resource not accessible by integration. No PR exists; no denied write was retried through another connection.
- All 211 baseline paths still exist. See worktree-preservation-evidence.json for names, changed paths and remaining local status. Pre-existing signing configuration, private evidence and queues were not discarded. release.jks remains locally and was removed only from the new Git tree; old history exposure needs separate key review.

## Actual local results

| Suite / command | Result |
|---|---|
| :app:testDebugUnitTest --init-script scripts/ci-tests.gradle | 428 passed; 0 failed/errors/skipped |
| :app:assembleDebug :app:lintDebug | PASS |
| :app:assembleDeviceTest :app:assembleDeviceTestAndroidTest -PdeviceTest=true | PASS before latest instrumentation-host correction; latest correction is executed in CI |
| node scripts/run_backend_suite.mjs backend, demo Firestore emulator | 119 passed; 0 failed/skipped |
| node scripts/run_backend_suite.mjs rules, demo Firestore emulator | 234 passed; 0 failed/skipped |
| python -m unittest discover -s scripts -p "test_*.py" | 15 passed |
| python scripts/interaction_inventory.py --check | 891 source candidates match; not 891 executed controls |
| Actual-report aggregation with duplicate JUnit copies | 781 tests counted once; 0 failures/errors/skips |

Fresh expanded Android results are in 56 JUnit suites. Baseline Android's 375 reports were cached; that initial invocation was UP-TO-DATE. Backend baseline 119 was freshly executed. The accepted offline policy is unchanged: original client actionTime remains usable after delayed upload and clock manipulation is an acknowledged limitation, not a secure time guarantee.

## Reproductions and fixes

1. Expense isolation: two targeted tests initially had one failure. getExpensesForCustomer lacked workspace scope in both tables. Requiring workspaceId and scoping the outer expenses query and inner jobs query makes both pass; repeated scoped job-expense deletion preserves foreign 64-bit collisions and standalone expenses. This unused API fix is not evidence of fixing a physical UI defect.
2. Reporting integrity: missing critical JUnit mappings were previously reported PASS, missing reports crashed, and similarly named tests could count as evidence. Four regressions now enforce exact identities, NOT_VERIFIED gaps and separate skipped/failure results. Aggregate regressions prevent duplicated report totals, missing partitions and flat/nested Node JUnit omissions.
3. Invitation fixture: a second invitation reused an earlier OTP proof. Refreshing test proof time preserves the intended role-change denial; no production authorization changed.
4. CI isolation: root firebase.ci.json avoids Firebase CLI's forbidden ../firestore.rules path. Auth emulator routing belongs only to device integration; HTTP authentication tests correctly refuse that setting.
5. Instrumentation: demo-only Compose host Activity added; Owner compound deletion now verifies deletion of an Entry plus an active Partner-linked Expense and duplicate delivery. Invalid/shared financial dependency denials remain asserted.

## Scope and remaining gaps

The inventories cover 16 screen/component files, two ViewModel files, three repository files, three DAO files, three entity files, four trusted endpoints and 172 SQL query declarations. These are source-indexed assets, not proof of every runtime path. The interaction matrix has 891 callback/modifier candidates and explicit manual procedures; every runtime scenario remains NOT VERIFIED until evidence is mapped. The role matrix has 42 locally executed policy cases, with UI/backend per-row coverage labelled separately.

Complete historical v4–v15 migration fixtures are missing (current database does not export historical schemas). Complete UI-driven failure/cancellation/restart coverage, real OTP, all live test-account memberships, real Worker behavior and physical-device flows are NOT VERIFIED. ADB returned no attached device. No phone data, business workspace, membership, invitation, Firebase Rules, deployed Worker, billing setting or production routing was modified. No deployment or merge occurred. Dedicated regression commits were pushed as requested.

## Remote results

Run 39023688660 completed successfully: 790 tests, 0 failures/errors/skips (422 Android, 119 backend, 234 Rules, 15 Android emulator instrumentation). Downloaded artifacts exposed six omitted DriverEarningsConsistencyTest cases: its declared package differs from the partition script assumption. Discovery now reads actual packages. Those six cases passed locally; final run 39024341077 checks the corrected commit. Its conclusion is recorded below only after completion.

## New/changed regression implementation files

- app/src/test/java/com/example/OperationRoleMatrixTest.kt: 42 parameterized role/entity/mutation cases and membership/workspace/time checks.
- app/src/test/java/com/example/ExpenseWorkspaceIsolationTest.kt: actual Room cross-workspace expense query and delete regressions.
- app/src/test/java/com/example/WorkspaceDatabaseContractTest.kt: nine table contracts for collisions, duplicates, rollback and pruning.
- app/src/androidTest/java/com/example/DatabaseWorkspaceDeviceTest.kt: two real-device/emulator Room isolation cases.
- app/src/androidTest/java/com/example/DeviceEmulatorIntegrationTest.kt: corrected Owner compound-deletion persistence/duplicate assertions.
- app/src/deviceTest/AndroidManifest.xml: isolated Compose instrumentation host.
- app/src/test/java/com/example/WorkspaceDiscoveryPolicyTest.kt and its workspace-access-audit.json: preserved discovery shapes using synthetic identities, no live account fixture publication.
- functions/security.integration.test.js: fresh second-invitation proof fixture.
- scripts/test_interaction_inventory.py, test_regression_harness.py, test_ci_scorecard.py: 15 safety/evidence tests.
- scripts/interaction_inventory.py, run_backend_suite.mjs, ci-tests.gradle, run_device_ci.sh, prepare_ci.py, aggregate_regression.py, generate_ci_report.py: isolated execution, actual package discovery, honest reports and required-artifact checks.
- .github/workflows/android-ci.yml and firebase.ci.json: full isolated CI orchestration.
- app/src/main/java/com/example/data/dao/Daos.kt: scoped Customer expense query.

Existing stabilization source/backend/tests were also preserved in the first branch commit. See Git history and the baseline preservation evidence for the exact broader source set. The original local signing settings and pre-existing firebase.json difference remain uncommitted intentionally.

## Final verified GitHub result

Run 39024341077 completed SUCCESS for commit 937088dff3115d4e995dbf6d1dfbed1158ccbe8d. All ten jobs passed, including the aggregate gate. Downloaded aggregate artifact 12098321532 independently confirms no required JUnit artifact was missing.

| Executed partition | Tests | Failures/errors/skips |
|---|---:|---:|
| Android unit/domain | 195 | 0/0/0 |
| Android Room/database | 114 | 0/0/0 |
| Android Compose (Robolectric) | 28 | 0/0/0 |
| Android outbox/policy/protocol | 91 | 0/0/0 |
| Backend/Worker | 119 | 0/0/0 |
| Firestore Rules/trusted integration | 234 | 0/0/0 |
| Android emulator instrumentation | 15 | 0/0/0 |
| Total JUnit | 796 | 0/0/0 |

Harness/inventory Python job also passed all 15 tests; they are separate from the 796 JUnit total. Android build/lint and per-partition coverage generation passed. Unit/domain count is now 195, restoring the six driver tests missing from the preceding run. See github-run-39024341077.json for exact per-file evidence and job conclusions; the GitHub run publishes JUnit, HTML, lint, coverage, scenario and aggregate artifacts.

The full application acceptance request remains incomplete: this passing execution gate does not supply control-by-control runtime evidence for 891 source candidates, all supported historical migrations, physical-device OTP, or live services. The known PR blocker is GitHub integration HTTP 403 Resource not accessible by integration; no PR URL exists. No main merge or deployment was attempted.

Final local preservation check: all 211 baseline paths still exist. Original Desktop checkout, production data/configuration, memberships, devices and outboxes were not modified. Existing private reports and signing configuration remain local. The branch contains the authorized regression commits; this execution report and downloaded evidence are local uncommitted artifacts so the verified source commit remains unchanged.
