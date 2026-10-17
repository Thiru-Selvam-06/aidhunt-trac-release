# Regression suite and release gate — 17 October 2026

This branch starts at release HEAD `0bf772b9dd3a7d7ebfaae7ebcd1e96ca6f1fe141`.
The stabilization implementation was present as 167 status entries (211 modified/untracked files after expanding directories). It is included as the source baseline, rather than testing the older committed Android application.

## Evidence and scope

- Baseline: existing Android JUnit reports contained 375 passing tests; the baseline Gradle invocation was UP-TO-DATE, not a fresh 375-test execution. Backend baseline was freshly executed: 119 passed, zero skipped.
- New targeted run: 44 Android tests passed, zero failed/skipped (42 parameterized role/action cases, two expense isolation cases).
- Python harness: fifteen tests pass, including rejection of live-project or credential-bearing backend test execution and refusal to pass the report gate without JUnit.
- Full Rules integration run: 234 passed, zero skipped, against local demo-aidhunt-v16. An initial run was 233/234 because an invitation test reused an old OTP proof for a new invitation.
- Fresh full Android run: 428 passed, zero failures/errors/skips, including nine real-SQLite table contracts for scoped pruning, rollback, duplicate identity and 64-bit IDs. Debug build/lint and isolated instrumentation APK compilation passed locally. Remote evidence is separate below.

## Confirmed defect and regression

`ExpenseDao.deleteExpensesForJob(workspaceId, jobId)` was already scoped correctly. A new regression proves repeated deletion does not touch a foreign workspace's colliding 64-bit job/expense IDs or standalone expenses.

The adjacent `getExpensesForCustomer(customerId)` query was not scoped on either the expenses table or its job subquery. The reproduction inserted the same job ID (9007199254740995) in two workspaces, with different customers: it returned both workspaces' expenses. Before correction, two new tests ran and one failed. The corrected method requires workspaceId and scopes both tables. Both now pass. No production caller currently uses this previously unsafe DAO API; this is an integrity fix, not evidence of a device UI repair.

The invitation integration test's second invitation now obtains a fresh test proof timestamp. This preserves the intended existing-role-change denial instead of intermittently failing an earlier OTP freshness gate. No production authentication restriction was changed.

## Test architecture

| Layer | Implemented execution | Limits |
|---|---|---|
| Unit/domain | Existing financial, discovery, role, allocation, grouping and validation suites | Not proof of UI invocation |
| Room/SQLite | Robolectric real SQLite tests, v16/v18 migration fixtures; device-side workspace collision regression | No exported historical schemas; complete v4–v15 migration evidence remains missing |
| Repository/outbox | Existing acknowledgement, dependency, rollback, queue retention and routing regressions; new 42-case role matrix | Not every HTTP failure has been exercised through actual UI |
| Compose | Existing Robolectric controls and device CollectionForm tests | 891 source candidates are not 891 verified actions |
| Backend/Rules | Seven backend/Worker suites and seven Rules/trusted integration suites, serial fixtures | All synthetic/local; no live service or real OTP claim |
| Android integration | Existing ten DeviceEmulatorIntegrationTest journeys, CollectionFormDeviceTest, new DatabaseWorkspaceDeviceTest | CI Android emulator, not a physical phone; email/password emulator fixtures do not verify phone OTP |

`interaction-inventory.json/csv` gives source locations, enclosing component, callback ID, related test references, and explicit manual checks. This is a candidate inventory generated from UI callbacks and clickable modifiers, including forwarded callbacks. Comments, nested lambdas, runtime-generated menus, accessibility gestures and destination reachability still require human review. It deliberately marks every control's runtime status NOT VERIFIED until evidence is mapped. The drift guard prevents newly discovered callback identities from being silently omitted; it does not prove every actionable control has been discovered or tested.

`asset-inventory.json` lists actual screens, ViewModels, repositories, DAOs, entities, suites and workflow sources. `role-action-matrix.csv` records the outbox policy matrix separately from UI/backend coverage. Existing test references are not converted into PASS results.

## CI

One workflow is extended, with:
1. Inventory and harness safety checks.
2. Android compilation and lint, using generated demo configuration on disposable CI only.
3. Disjoint unit/database/Compose/outbox jobs; a test class belongs to one partition.
4. Separate backend and Rules jobs, each with its own demo Firestore emulator.
5. Android emulator instrumentation: isolated application ID, demo Auth/Firestore/Functions only, synthetic fixtures.
6. Always-running artifact aggregation and a failing release gate when a required job fails or test evidence is missing.

No workflow deploys anything. No PR job receives production service-account keys, Worker credentials, or OTP secrets. CI does not call live Firebase. Node tests are serial because existing fixture cleanup clears only the disposable emulator. Partitions publish JUnit, HTML and code coverage reports; coverage remains per partition, not a misleading summed percentage.

Coverage generation and instrumentation must actually pass remotely before being called verified. PhoneRegistrationDeviceTest, PurgeProtocolDeviceTest and WorkspaceSelectionDeviceTest currently require additional exact fixture setup and are not silently represented as executed by the representative CI journey job.

## Local commands

- `python scripts/interaction_inventory.py --check`
- `python -m unittest discover -s scripts -p "test_*.py"`
- `./gradlew :app:testDebugUnitTest --init-script scripts/ci-tests.gradle`
- `./gradlew :app:testDebugUnitTest --init-script scripts/ci-tests.gradle -PciSuite=database` (unit/compose/outbox are the other disjoint partitions)
- `GCLOUD_PROJECT=demo-aidhunt-v16 FIRESTORE_EMULATOR_HOST=127.0.0.1:18180 node scripts/run_backend_suite.mjs backend`
- Same command with `rules`; start the existing local emulator first.
- Device instrumentation uses `-PdeviceTest=true :app:connectedDeviceTestAndroidTest`. With that property AGP registers deviceTest unit tasks, so do not combine `testDebugUnitTest` in that invocation.
- CI coverage: `:app:createDebugUnitTestCoverageReport -PciCoverage=true`, using the same partition.

## Remaining release blockers

- All control-by-control role visibility, UI→repository→acknowledgement→reopened-screen paths are not yet evidenced. The source candidate inventory is not an exhaustive executed UI suite.
- Every migration from v4 through v15 needs authentic archived schema fixtures; no destructive migration substitution is acceptable.
- All HTTP 401/403/409/429/5xx, lost acknowledgements, force-stop and conflicting edits still need a complete UI-driven scenario mapping, beyond the existing protocol/repository tests.
- Current accepted offline policy uses original client actionTime and explicitly accepts clock manipulation. The new requirement forbidding client time as security authority conflicts with that prior policy. The user explicitly reconfirmed retention of the approved offline policy during this task; no permission/time-window change is made. Tests must not label this limitation secure.
- Current production account/workspace matrix and actual OTP, real phone, published Worker and live Firebase behavior are NOT VERIFIED in this CI-only task. No production data or membership repair occurs.
- A release signing key and hardcoded local signing settings pre-existed in tracked history. The proposed tree excludes that key and uses ignored properties for release signing and a standard debug key in CI. The original local signing file/configuration is preserved. Historical exposure requires separate key review; this branch does not rewrite history.
- No production readiness claim or completion percentage is warranted.

## Manual continuation

Use an isolated package/backend only. For each inventory row, record role, workspace, starting records and outbox IDs; tap the actual control and cancellation; verify Room transaction/envelope and server decision independently; reopen after acknowledgement and after restart. Repeat offline/permission rejection and confirm retained payload and original workspace. Attach evidence paths and change status only for that exact scenario. Never use canonical PMS records or existing pending production operations as fixtures.

## First remote run

Push run 39022689288 executed commit 1ec867a96596cff1fa6b2f7fb630f03e3521d554. Inventory passed. Both Node jobs failed before tests because Firebase CLI 15.33 rejects ../firestore.rules outside the configuration directory. A root-level firebase.ci.json fixes this; the exact CLI invocation then passed 119 backend tests locally. PR creation returned GitHub HTTP 403 Resource not accessible by integration; no PR was created or retried through another connection.

## Reporting-integrity regression

The pre-existing CI scorecard reported missing critical-case mappings as PASS, used substring matches as evidence, and crashed when JUnit was absent. Four new tests reproduce these failures and verify the correction: exact JUnit identity only, NOT_VERIFIED for missing cases, separate skipped/failed counts, and actual tool versions. Fifteen Python harness tests pass. The aggregate also deduplicates byte-identical JUnit report copies and requires evidence from every declared test partition. This prevents an all-green test job from being represented as exhaustive application verification.

The first remote instrumentation run executed 15 cases: 12 passed, three failed, zero skipped. Two Compose cases lacked their host Activity in the isolated deviceTest manifest; the demo-only host is now declared. One deletion scenario incorrectly expected an Owner to be blocked by an active Partner-linked Expense, despite the current approved compound-deletion policy. It now asserts persisted deletion of both Entry and Expense, duplicate delivery, and retains denials for malformed/shared collection fixtures. Production permissions were not changed. The corrected remote run must pass before these fixes count as executed instrumentation evidence.
