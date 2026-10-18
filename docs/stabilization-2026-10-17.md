# AIDHUNT Trac stabilization — 2026-10-17

Local changes implemented. Fresh OTP login, physical-device recovery and live authenticated mutations remain NOT VERIFIED. No completion percentage or production readiness claim.

## Environment

Release worktree: C:\Users\user\.codex\worktrees\v16-implementation\aidhunt-trac. Branch codex/v1.6-implementation; HEAD 0bf772b9dd3a7d7ebfaae7ebcd1e96ca6f1fe141.
App, CLI and MCP identify trac-aidhunt / 898996717587; billing disabled. Staging and production share Auth but have separate workspace permissions.
Deployed staging revision 919ce27d-6562-449d-9392-a6d1eea5f0b0 (100%); deployment 03529acc-5789-4fbd-b686-b52dd5edc480. URL https://aidhunt-trac-trusted-staging.aidhuntinformatics.workers.dev . No deployment made.
Deployed source and binding confirm exact workspace ws_cf_staging_20261015_login_a62e71. The older ws_cf_staging_20261015_7361_7f4c92 is rejected. Local Worker accepts three explicit staging IDs but is NOT deployed. The isolated client picker now offers only the verified deployed target. Old queues remain in their original workspace.
Physical-test package com.aistudio.aidhunttrac.cedlir.physicaltest; separate aidhunt_physical_test_v18.db, schema19. ADB detected no phone; no installation or data clearing performed.
PMS metadata updateTime remains 2026-10-03T13:12:55.439904Z. Its reported 182 business records were not counted or modified. Desktop checkout untouched.

## Root causes and local corrections

- Authentication was coupled to hardcoded staging membership and profile narrowing. Verified authentication is now independent; missing workspace/profile leads to recovery rather than fabricated authority.
- Global profile roles conflict with workspace membership, and discovery indexes are incomplete/stale. Candidate discovery unions profile/default/index plus UID-derived legacy personal ID, then checks authoritative active membership and Owner UID. Phone/name/global role do not authorize access.
- Startup errors could leave splash indefinitely. Bounded verification, recovery screen and distinct CachedReady state preserve offline sessions without claiming fresh validation. Confirmed denial removes cached authority, retaining Room/outbox.
- Multiple valid workspaces previously opened the first silently. Fresh sessions without a valid remembered choice get name/role selection; switching revalidates membership. An invalid explicit selection fails closed, never rerouting pending commands.
- Lost invitation acknowledgement/retry and unsupported role/reactivation handling: stable client attempt ID and retained target SDK session; exact server receipt/current membership validation; idempotent acceptance without rewriting member/proof; role changes and reactivation denied.
- Sync UI hid operation context and mislabelled superseded rows as permanently deleted. It now displays operation ID/type/target/workspace/original actionTime/attempts, failure details, retry classification and safe next action. HTTP/backend status is retained. No missing/mismatched acknowledgement can mark success.

## Account matrix: metadata verified, fresh OTP NOT VERIFIED

Phone sign-in enabled. Exactly these seven configured Firebase test numbers; codes not printed/stored. All seven Auth identities enabled. Each row's actual login, workspace opening and physical role result remains NOT VERIFIED.

| Test number | UID | Active production/personal roles | Current deployed staging | Removed membership |
|---|---|---|---|---|
| 9443107361 | G7RZespbU6cwIUhaB0sl7Du6tNV2 | ws_G7RZespbU6cwIUha: owner | ws_cf_staging_20261015_login_a62e71: OWNER | None |
| 8939889471 | QlVAEPVTtYSxLeWCH5nE2d8yoNo2 | None | No membership | None |
| 8925624885 | VZ088WCZ61SL8kN3qmAIyaW6xiH3 | ws_VZ088WCZ61SL8kN3: owner | No membership | None |
| 8072167070 | j2UalBQUeKb7BX1hYYmoj84lpAU2 | ws_j2UalBQUeKb7BX1h: owner | No membership | None |
| 8489575171 | VxzKAUg2wIbJbQDImBz0VoWcIo13 | ws_G7RZespbU6cwIUha: Partner; ws_VxzKAUg2wIbJbQDI: owner | ws_cf_staging_20261015_login_a62e71: PARTNER | ws_cf_staging_20261015_7361_7f4c92: PARTNER |
| 9489930605 | aCfxnG25LlOeywq5VTNYlPZDF4c2 | ws_G7RZespbU6cwIUha: Partner; ws_aCfxnG25LlOeywq5: owner | ws_cf_staging_20261015_login_a62e71: PARTNER | ws_cf_staging_20261015_7361_7f4c92: PARTNER |
| 7092476678 | otLn7bjmTpbG3A59YMH27jrjpqn2 | ws_7092476678: owner; ws_VZ088WCZ61SL8kN3: Partner | No membership | None |

7361: PMS omitted from index but profile identifies it; also Owner in old staging. 5171/0605: old staging index falsely ACTIVE despite removed membership, and personal Owner workspace omitted. 9471: no active membership; directory points to absent ws_QlVAEPVTtYSxLeWC. Standard app offers explicit create/recover flow; no absent workspace revived.
Eight additional phone Auth identities are not configured test numbers. They have no index rows or membership/ownership in the eight enumerated workspaces; not silently promoted to test accounts:
- UID 20nY22VP8BaBZDUHnsQwXQk9T692, number ending 1392: enabled; no configured test-code flow; access NOT VERIFIED.
- UID GzCBjwhzYzUw5mommk9ci0ydfLm1, number ending 2218: enabled; no configured test-code flow; access NOT VERIFIED.
- UID ML5aJUUAcxbsksPGhKG0TUlICry2, number ending 1361: enabled; no configured test-code flow; access NOT VERIFIED.
- UID NxBFqG34uzUVyXa8aAc3yaHaXY52, number ending 3210: enabled; no configured test-code flow; access NOT VERIFIED.
- UID bwK0SUF7xpPCEnmxj22FMkX4qkH2, number ending 8882: enabled; no configured test-code flow; access NOT VERIFIED.
- UID kP8oJ0XQDOXYrBGomnIHpy57AEj1, number ending 6565: enabled; no configured test-code flow; access NOT VERIFIED.
- UID wqrEEnu6eEdQHOHg0PLrjOVz0k42, number ending 1241: enabled; no configured test-code flow; access NOT VERIFIED.
- UID yiEuvDfZU6aoPlUYm9RVvjrbO262, number ending 5630: enabled; no configured test-code flow; access NOT VERIFIED.

No live Operator membership found. Operator restrictions tested locally. Rules prohibit general workspace listing: workspaces absent from all authorized discovery candidates require separately approved index repair.

## Invitation and role policy

Two modern PENDING invitations in old staging are expired by timestamps; no valid current pending/USED/revoked invitation observed across eight workspaces. Earlier legacy inventory found eight pendingPhones docs/four unregistered identities including unsupported Co-Owner; none accepted/rebound.
v1.6 uses Owner-assisted target OTP and prohibits role changes/suspension/reactivation. Removed memberships are not revived merely to pass login. Personal workspace creation is explicit and UID-idempotent: atomic workspace+Owner membership, then atomic own profile/index after committed Owner authority; retries reuse the workspace. No live execution.
Test-number unlimited invitation lifetime is not newly enabled on live Worker; production five-minute policy remains. Any test-only exemption requires reviewed configuration/deployment, not a global bypass.

## Needs review, SQLite and deletion

| Evidence | Disposition |
|---|---|
| Older staging ID versus deployed exact guard | Proven mismatch; local isolated picker aligned. Old commands not redirected/retried. |
| Supplied EXPENSE DELETE / PERMISSION_DENIED | Server authorization rejection, not evidence of SQLite corruption. Exact original rule/request root cause NOT VERIFIED without device payload/logs; retained. |
| Cancelled rows labelled permanently deleted | Corrected retained-cancellation label and detailed reason. |
| Missing/mismatched acknowledgement | Exact command/workspace/time/64-bit target validation retained; no false success. |
| Lost backend status | HTTP/backend error details retained in diagnostics. |
| Room migrations/keys/scoping | Existing schema19, composite-key and migration regressions pass; no destructive migration. Unknown device-specific errors NOT VERIFIED. |

Home and Business Overview call MainViewModel.deleteJob -> WorkspaceRepository.deleteJob -> one durable compound JOB DELETE -> trusted transaction -> matching acknowledgement -> local reconciliation. Linked eligible Expense/Collection snapshots remain in the command; no redundant child Expense DELETE commands. Shared allocation/dependency/audit restrictions retained.
Local tests cover paid/unpaid/reversed/linked deletion, rollback, duplicate/retry, exact IDs above 2^53 and rejection recovery. Actual Home/Recent Jobs/Business Overview behavior and current Room counts NOT VERIFIED without phone. Old 76-row counts not reused.

## Collection History

Existing compact expandable implementation retained: workspace/job grouping, canonical totals, independent expansion, chronological payment timeline, collector versus recorder identity, allocations/reversal/audit details. Grouping does not change schema/payment IDs. Old form tests now expand cards before timeline assertions.
Passing UI/model counts before final diagnostics rerun: expandable4, details1, grouping8, form3, reversal/history19. Physical layout and live persistence NOT VERIFIED.

## Validation

Final Android validation: **375 passed, 0 failures, 0 errors, 0 skipped**. Full testDebugUnitTest + assemblePhysicalTest succeeded in 7m16s. APK: app/build/outputs/apk/physicalTest/app-physicalTest.apk; applicationId com.aistudio.aidhunttrac.cedlir.physicaltest; versionName 1.0.4-physical-test; versionCode5; 32,092,348 bytes; SHA256 781A104EA2AB105706A278B7971ADA7973E77E7B61099C41F0DE6C540BEC1FFD. No installation: final adb devices -l is empty.
Backend: 119 passed, zero failed/skipped. Rules/trusted integration: 234 passed, zero failed/skipped. Earlier Android full run: 374 passed with zero failures/errors/skips and assemblePhysicalTest succeeded; final rerun includes latest diagnostic regression.
Earlier failed attempts preserved: Windows memory shortage; classes.jar file lock; three obsolete collapsed-card assertions; two obsolete invitation expectations. Updated invitation regression verifies exact retry with unchanged persisted membership/proof timestamps plus role/reactivation denial. Final security rerun passed.
Live /health returned ok / trac-aidhunt / credentialConfigured true. All four operation endpoints rejected missing Authorization with HTTP401 UNAUTHENTICATED. Authenticated live business operations NOT VERIFIED.

Commands from release worktree (demo project only):

```powershell
$env:GCLOUD_PROJECT='demo-aidhunt-v16'; $env:FIRESTORE_EMULATOR_HOST='127.0.0.1:18180'
node --test --test-concurrency=1 cloudflare/operations.test.mjs cloudflare/google-auth.test.mjs cloudflare/firestore-rest.test.mjs functions/invitation-policy.test.js functions/entry-deletion-policy.test.js functions/entry-deletion-http.test.js functions/migration/legacy-timestamps.test.js
node --test --test-concurrency=1 security-tests/architecture.test.cjs security-tests/listener-queries.test.cjs security-tests/phone-onboarding.test.cjs security-tests/workspace-discovery.test.cjs security-tests/purge-protocol.test.cjs functions/security.integration.test.js functions/entry-deletion-emulator.test.js
.\gradlew.bat :app:testDebugUnitTest :app:assemblePhysicalTest --init-script "$env:TEMP\aidhunt-auth-small-tests.gradle" --offline --console=plain --max-workers=1 --no-daemon "-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=512m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8"
```

Existing Firestore Emulator v1.22.0 compiled complete local rules at 127.0.0.1:18180/demo-aidhunt-v16; stopped after tests. Temp Gradle init bounds tests to 512MB/forkEvery10; no project dependency/config changes for these limits.

## Changed files in this logical stabilization task

- MainActivity.kt; FirebaseAuthService.kt; FirestoreRepository.kt; FirestoreModels.kt; WorkspaceRepository.kt; WorkspaceDiscoveryPolicy.kt; WorkspaceInvitationService.kt; WorkspaceCommandProtocol.kt.
- MainViewModel.kt; WorkspaceRecoveryScreen.kt; AccountScreen.kt; StartupAuthPresentation.kt; OperationDiagnostic.kt.
- cloudflare/operations.mjs and operations.test.mjs; functions/index.js, invitation-policy.js, invitation-policy.test.js, security.integration.test.js. Local only.
- WorkspaceDiscoveryPolicyTest.kt + workspace-access-audit.json; WorkspaceSelectionPolicyTest.kt; WorkspacePickerUiTest.kt; StartupAuthPresentationTest.kt; WorkspaceCommandProtocolTest.kt; OperationDiagnosticTest.kt; CollectionFormRegressionTest.kt; security-tests/workspace-discovery.test.cjs.
- This report, sanitized access evidence, status snapshots and validation logs. Existing unrelated changes/Collection History implementation retained. firestore.rules NOT edited in this task.

## Remaining gates / separate approvals

1. Connect phone; install isolated APK in place, preserve Room/outbox. Manually retest OTP, picker, role/offline/reconnect, deletion and diagnostics with redacted Logcat.
2. Four configured accounts lack current staging membership: authorization boundary, not OTP failure. Isolated invitations/membership additions need separate approval; no automatic access.
3. Review/approve staging-only deployment of local invitation protections. Current Worker remains unchanged.
4. Local/deployed Rules differ in pre-existing collector attribution, India-calendar collection date checks and collector immutability. Auth/index predicates match. Separate reviewed Rules deployment needed for newer live checks; none performed.
5. Separately approve authoritative index repair for missing Owner/personal rows and stale removed staging rows. Do not revive missing 9471 workspace or move synthetic records to PMS.
6. Standard production trusted-command routing unchanged. Authenticated production endpoint persistence and physical workflows unverified; no production-readiness claim.

No live data, memberships, Rules, Worker configuration, billing, Auth identities, invitations or production routing changed. No OTP/private key/token logged or committed.
## Preservation and evidence files

All 149 baseline changed/untracked Git status paths remain present. Branch and HEAD unchanged. This verifies path preservation, not that requested edits left those files byte-identical. Baseline/final snapshots are stabilization-baseline-2026-10-17.txt and stabilization-final-status-2026-10-17.txt. Commands: git status --short; git branch --show-current; git rev-parse HEAD; compare baseline/current path sets. No queue database or existing application storage was changed.

Evidence: stabilization-access-evidence-2026-10-17.json; stabilization-android-2026-10-17.log; stabilization-backend-2026-10-17.log; stabilization-rules-2026-10-17.log. Failed attempts remain in the task TEMP logs.
