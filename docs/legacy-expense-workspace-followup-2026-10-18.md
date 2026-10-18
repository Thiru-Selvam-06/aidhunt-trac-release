# Legacy Expense deletion and workspace follow-up — 2026-10-18

Decision: NOT READY. Client hardening and picker changes do not repair existing PMS documents.

## Proven observations

Worktree: `C:/Users/user/.codex/worktrees/v16-implementation/aidhunt-trac`.
Branch: `codex/comprehensive-regression-2026-10-17`; HEAD: `937088dff3115d4e995dbf6d1dfbed1158ccbe8d`.
Pre-existing tracked and untracked work retained. Baseline copies and evidence: `docs/test-artifacts/legacy-record-followup-20261018/`.

The connected I2304 standard installation is package `com.aistudio.aidhunttrac.cedlir`, 1.0.4/code 5, last updated 2026-10-18 14:55:09. Version alone cannot identify the source revision. Only package metadata and known-signature counts from the already-running PID 14398 were read. Ten missing-record-identity failures were present. The sampled PID log had zero suspension/permission-denied/crash/ANR signature matches; this does not prove a previous toast did not occur. No navigation, app launch, install, private database inspection, account change or sync action occurred.

## Client changes

1. Extracted `RecordIdentityPolicy` from the repository guard and constrained creator/beneficiary membership checks to the active workspace. Previously the guard matched UID and status only. A matching active row in another workspace cannot authorize changes now.
2. Missing identities remain blocked before local mutation/enqueue. The message now explains that legacy record repair is needed, separately from Owner access. Removed/inactive members retain the existing read-only policy. Neither missing creators nor beneficiaries are fabricated.
3. Workspace picker separates name, role, workspace reference and action; cards retain equal full width, safe-area padding, scrolling and existing selection callbacks.
4. Added six identity-policy/suspension-cache regressions; updated picker assertions for the separated labels. Existing financial formulas, attribution, command IDs, queues and server authorization are unchanged.

## Remaining data and runtime blockers

The Expense failure is thrown before enqueue by `requireRecordMembersActive` when creator or beneficiary is empty. Local Rules also require active creator/beneficiary identities for Expense tombstone updates/deletes (`activeRecord`), so bypassing the UI guard would move failure to the server rather than complete deletion. Owner role does not by itself establish missing historical record identity.

The Entry attribution proposal changes only beneficiary/financial-attribution fields. It cannot repair an Expense creator, and assigning the current Owner as historical creator would falsify audit provenance. Existing assignments, actual payer/collector and creation identity must be preserved. The current Worker has no trusted attribution-repair operation. No production connection or repair was attempted. Complete verified Owner/membership, document-version and unresolved-outbox evidence remains necessary before a reviewed, separately authorized trusted repair. Historical local snapshots are not current production verification.

Current `AuthorizationManager.isAccountBlocked()` returns false under v1.6 and `getAccountStatus()` returns ACTIVE. A new regression covers a stale SUSPENDED/expired cache without weakening real membership checks. The reported suspension toast is NOT REPRODUCED; it must not be described as resolved or as proof that PMS is staging. No live workspace environment/status metadata was changed.

## Validation

Results are recorded in `docs/test-artifacts/legacy-record-followup-20261018/results.json` after execution. Initial Gradle command failed configuration because `--tests` followed `assembleDebug`; the corrected command places test filters directly after `testDebugUnitTest`. Both logs are retained. No assertions were removed to overcome a product failure.

No Rules/Worker deployment, production data access/write, membership repair, billing action, commit or push occurred. No APK installation or runtime acceptance of this candidate is claimed.

## Executed final results

- Corrected Gradle command: `:app:testDebugUnitTest --tests com.example.RecordIdentityPolicyTest --tests com.example.WorkspacePickerUiTest --tests com.example.BusinessOverviewAttributionTest --tests com.example.BusinessOverviewChartMembersTest --tests com.example.BusinessOverviewCircleUiTest --tests com.example.ArchivedRecordAuthorizationTest --tests com.example.ExpenseWorkspaceIsolationTest :app:assembleDebug --init-script scripts/ci-tests.gradle --offline --console=plain --max-workers=1 --no-daemon` (JDK 21, 1GB Gradle heap): exit 0, BUILD SUCCESSFUL.
- 33 tests across seven suites: zero failures, errors or skipped JUnit tests. Roborazzi finalizer task SKIPPED; no runtime screenshots claimed.
- Six new record-identity tests establish distinct active creator/beneficiary support, foreign-workspace denial, missing-identity rejection, removed-member protection, missing-workspace rejection, and stale suspension-cache nonblocking with real membership still required.
- Five picker Compose/Robolectric tests cover distinct roles and IDs, three choices, empty recovery, small-width wrapping/alignment, retry and logout. These are local synthetic UI tests, not physical-device acceptance.
- Existing chart/attribution tests: 19 passed; archive policy: one; Room Expense isolation: two.
- `git diff --check`: exit 0. All five changed-source hashes match the tested files.
- APK: `app/build/outputs/apk/debug/app-debug.apk`; SHA256: `0b526c1f3e457ddbc18f78d45b5cfd8822dca76f5ba0e202e09f33168f8abfca`. Built, not installed.
- Redacted connected-device observations and all seven JUnit XML reports preserved in the evidence directory. No production requests were issued.
