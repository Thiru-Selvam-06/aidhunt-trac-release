# AIDHUNT Trac v1.6 — security and runtime evidence recovery, 2026-10-17

Release gate: **NOT READY**.

Nine narrow trusted Rules invariants and two offline persistence/eligibility requirements now have reviewed, executed evidence. A confirmed authentication-recovery defect is repaired. This does not establish all 327 requirements or production readiness. Mandatory full authentication/OTP/session-race, broad workflow acceptance and authentic historical migration evidence remain incomplete. No production service was accessed or changed.

## Baseline and provenance

- Actual root: `C:\Users\user\.codex\worktrees\v16-implementation\aidhunt-trac`.
- Branch: `codex/comprehensive-regression-2026-10-17`.
- HEAD: `937088dff3115d4e995dbf6d1dfbed1158ccbe8d`; upstream0 ahead/0 behind at baseline. Dirty changes preserved; staged baseline empty. No commit, push, reset, clean, stash or branch switch.
- [Baseline](test-artifacts/security-recovery-2026-10-17/baseline.json) records153 individual dirty/untracked paths, relevant ignored directories,150 hashes and no mismatch against the previous tested-source manifest. Final status/preservation are recorded separately below; dirty state is part of the tested candidate.
- Read the actual architecture DOCX from Downloads, SHA256 `2fab17c57f4f99631be10ba1634fee66ed88b070d10d7c979ce24fd993c65d17`; [793-paragraph extraction](test-artifacts/security-recovery-2026-10-17/architecture-extract.txt). Read the current audit, matrix, execution report and findings before editing.
- **Missing authority:** `.agents/AIDHUNT_Trac_Production_Readiness_Remediation_Plan.md` does not exist in this worktree. No Desktop copy was substituted. Reconciliation against that exact plan remains blocked by its absence.
- Existing release-gate results and all 12 invalid legacy PNGs are preserved. The old gallery is still invalid, not repaired or reused. No new claim relies on it.
- [Tested source hashes](test-artifacts/security-recovery-2026-10-17/tested-source-sha256.json), [commands and exits](test-artifacts/security-recovery-2026-10-17/commands.json), [matrix before this phase](test-artifacts/security-recovery-2026-10-17/matrix-before.md) and [status changes](test-artifacts/security-recovery-2026-10-17/matrix-changes.json).

## Confirmed defect and minimal fix

`recoverVerifiedProfile` previously treated Firestore `PERMISSION_DENIED` and `UNAUTHENTICATED` like network unavailability. Its cached-profile fallback returned a previously verified user after explicit server denial. The auth listener's generic exception branch could also fall back, so returning another generic exception would not fix the full path.

Two new assertions reproduced this on the original implementation:8 properly configured Robolectric tests,2 assertion failures. The fix converts these two terminal Firestore codes into a SecurityException before any cache read. The existing listener already clears verified profile state on SecurityException. Genuine UNAVAILABLE/IOException recovery remains supported; cancellation and disabled Firebase users propagate.

- Changed production source: [VerifiedProfileRecovery.kt](../app/src/main/java/com/example/data/firebase/VerifiedProfileRecovery.kt),7 inserted lines.
- Changed regression source: [VerifiedProfileRecoveryTest.kt](../app/src/test/java/com/example/VerifiedProfileRecoveryTest.kt), five new tests and Robolectric runner.
- [Before, proper harness](test-artifacts/security-recovery-2026-10-17/auth-reproduced.log), [failed JUnit](test-artifacts/security-recovery-2026-10-17/auth-before.xml), [after](test-artifacts/security-recovery-2026-10-17/auth-after.log), [final eight-case JUnit](test-artifacts/security-recovery-2026-10-17/android-junit/TEST-com.example.VerifiedProfileRecoveryTest.xml).
- The earlier `auth-before.log` includes four Android-stub harness failures before Robolectric was added. Those are retained and are not counted as behavioral reproductions.
- A-09 remains IMPLEMENTED — Verification Incomplete: helper tests do not establish every invalid-token, full session callback or navigation case. A-12 remains incomplete: cancellation propagation and observed account-change clearing do not prove every delayed completion race.

## Authentication, roles and invitations

The architecture defines workspace-scoped OWNER/PARTNER/OPERATOR only. Root ownerUid plus active Owner membership grants Owner authority; Partner/Operator authority comes from active membership. Global token role fields do not grant workspace privileges. Creator, beneficiary, actual payer and collector remain separate identities. Original client actionTime remains the approved offline eligibility input; possible clock manipulation is accepted, not a secure wall-clock attestation.

The new [security-recovery.test.cjs](../security-tests/security-recovery.test.cjs) adds14 top-level tests with paired allowed/denied assertions and persisted-state checks. It requires the demo project, exact loopback host and no credential file. The existing CI Rules runner includes it; no parallel/redundant workflow was introduced.

| Threat / boundary | Executed assertion | Scope / result |
|---|---|---|
| Unauthenticated read | Seven operational/financial collections deny; same paths allow Owner and return correct workspace marker | PASS, local Rules |
| Forged global Owner | Outsider claiming OWNER denied in A and B; actual Partner allowed | PASS, local Rules |
| Membership revocation | Same SDK context loses A access after REMOVED/SUSPENDED/EXPIRED/INACTIVE/empty status; B remains accessible | Five PASS tests, local Rules |
| Role legitimacy / root owner | ADMIN/CO-OWNER/unknown/forged OWNER denied; legitimate roles read assets, Operator cannot read finance | PASS, local Rules |
| Same UID different roles | Owner in A can manage A; same UID as Partner in B cannot use global OWNER claim to update B | PASS, local Rules |
| Viewer-independent roles | All viewers read identical roles; even Owner cannot directly rewrite Partner into Operator | PASS, local Rules |
| Forged workspace / actor | Valid extension persists; wrong path workspace, actor or creator denied and forged document absent | PASS for representative protocol-v2 path; SEC-06 stays incomplete |
| Alternate paths | Unknown root/subcollection and legacy collaboration paths reject reads/writes | PASS, local Rules |
| Token signature / revocation | Existing Worker auth unit test uses generated cryptographic keys and fake Google responses; rejects signature, wrong audience, expired, disabled/revoked user | PASS in backend119; not deployed Google integration |
| Invitations | Existing policy/Worker tests cover locked roles, Owner-only creation, identity mismatch, expiry, replay receipt, revoked membership and no role upgrade | PASS locally; not carrier SMS or complete secondary-auth UI lifecycle |

Actual virtual-device role reads: Owner/Partner read collections; Operator denied collections; all roles allowed intended assets and ordinary entries; foreign workspace denied. Actual Owner member removal retains source/history and revokes a separately authenticated removed client; Partner removal attempt denied. These assertions supplement, rather than substitute for, the full role/action matrix still awaiting complete acceptance.

Phase1's **complete** session/OTP gate remains open. Later Room, financial and virtual-device tasks were independent local checks using explicit synthetic memberships; they do not imply that the missing phone-OTP or callback-race acceptance passed.

## Workspace isolation and build boundaries

Two synthetic workspaces have identical names, overlapping ID `9007199254740995` and distinct data markers. New Rules assertions prove bidirectional foreign read/write/query denial. Android unit tests also exercise membership validation, selection, stale indexes and environment allowlists. The physical-test environment predicate uses an explicit approved set plus membership validation, not a naming prefix as authority. Standard discovery rejects staging prefixes but still requires authoritative membership before access; knowing an ID does not grant access.

Actual `ExpenseDao.deleteExpensesForJob(workspaceId,jobId)` already includes both workspace and job ID. It was not defective in this baseline and was not modified. Two existing JVM tests and two real Android SQLite tests passed, including colliding large IDs and preservation of standalone expenses. Nine parameterized real SQLite table tests verify composite identity, duplicate insertion, rollback and scoped pruning. These do not establish that every application query/report/listener path is correctly scoped; W-02/W-06 remain incomplete.

No membership or outbox record was redirected into another workspace. Runtime used only `device-ws`/`other-device-ws` in `demo-aidhunt-v16`; no PMS or other business workspace was read or written.

## Room migration and durable-state evidence

Source schema is19. All15 transitions4→5 through18→19 are registered in AppDatabase. Stable filenames containing v4/v18 intentionally do not equal the current schema version; changing their names would risk data loss and was not done.

- RoomMigrationChainTest:10 passing tests. The sequential test really calls all 15 transitions on one fresh synthetic v4 database, preserving seeded Tractor42, Customer88 and Expense7 with asserted keys/defaults/amounts; it checks newly available tables and collector columns.
- That full-chain fixture has no seeded Job, pre-existing collection or pending operation at v4. Its name/comments mention v18 although the executed chain includes18→19. Thus its title does not prove all row shapes or complete production preservation.
- CollectionPersistenceTest:2 passing tests. Current Room reopen retains selected collector versus recorder, exact large IDs and original queue snapshot; synthetic18→19 invokes the actual migration twice and asserts retained payment UID/time, blank unknown collector fields and unchanged outbox payload/actionTime.
- Nine workspace table tests use real SQLite; two additional Android emulator DAO cases passed. Runtime actual database PRAGMA user_version=19.
- Repository files and available Git history for `app/schemas` / instrumentation assets contain no authentic exported historical Room schemas. No authentic sanitized released database fixture was found. Hand-built schemas remain synthetic; full released-schema validation and all historical row/relationship/rollback combinations are NOT VERIFIED. No real database was requested or read.

## Offline, acknowledgement and financial invariants

Executed unit assertions preserve original actionTime/operationId across Room close/reopen and retry, select only scoped PENDING rows, retain rejected legacy payloads, reject malformed/mismatched acknowledgements, stop request send after token-fetch account change, and restore an eligible earlier edit after rejection. These tests are individually linked in the evidence register; generic green suite counts are not used to certify whole requirements.

Actual emulator sequence: entry created30h earlier; action at18h accepted/replayed twice; action at26h rejected by Rules. Room and cloud retain amount200 and the original rejected payload. Wrong authenticated actor cannot upload that earlier action. Real force-stop/relaunch preserves all four queue rows byte-for-byte at the summarized field/payload-hash level: Owner ACKNOWLEDGED1; Partner ACKNOWLEDGED1, REJECTED2; no pending work. These are disposable-run counts, not counts from the user's physical installation.

Actual pending deletion goes through WorkspaceRepository sync and trusted local callable, becomes ACKNOWLEDGED, removes source, reconciles Customer totals to0 locally/remotely, and stale Entry/Customer snapshots do not resurrect it. Other test cases deliberately reject shared/financial dependencies without partial mutation; eligible linked Expense deletion is atomic; reversed history remains.

CashBalanceRegressionTest's five assertions pass: active cash only; UPI/reversed/foreign records excluded; personally funded expense does not consume business cash; Business Paid expense and withdrawal reduce cash; deficit remains negative; payer is independent from creator/beneficiary; mixed unscoped workspaces throw. These are synthetic engine assertions, not proof of every authorized data feed or live finance workflow. Stable financial retry behavior is supported by existing Worker/Firestore transaction tests; all financial operation combinations are not certified.

## Runtime acceptance: real virtual device, no physical phone

- Device `emulator-5580`, `sdk_gphone64_x86_64`, Android16/API36, ranchu; fresh disposable data image, existing AVD data/snapshots untouched.
- Package `com.aistudio.aidhunttrac.cedlir.devicetest`, variant deviceTest, versionCode5, `1.0.4-emulator`.
- APK SHA256 `bd57fcad4331ea0b7377c72e8f428bf9b8423a3f7c0de74b7c43ffe6e3449deb`.
- Runtime-written environment JSON confirms demo-aidhunt-v16/package. Auth127.0.0.1:19099, Firestore18180, Functions15001; three ADB reverse mappings. Build guards require demo project/package and contain no production fallback. Room filename `aidhunt_device_test_v18.db`, actual schema 19.
- Existing synthetic Owner/Partner/Operator fixtures authenticated using emulator email/password SDK flow. This is **not** phone OTP, a predefined phone-number flow, live SMS or deployed Worker acceptance.
- **15/15 instrumentation tests passed**, including actual Compose taps for selected collector and invalid date. [Raw runner](test-artifacts/security-recovery-2026-10-17/runtime/adb-instrumentation.log), [individual result/claim transcript](test-artifacts/security-recovery-2026-10-17/runtime/scenario-transcript.json).
- Independent Firestore reads after execution passed13 assertions: expected protected sources retained, Entries20/41/42 and Expense142 absent, Entry10=200 with actionTime-createdAt18h, REVERSED history retained, REMOVED membership and ARCHIVED audit retained. [Persisted result](test-artifacts/security-recovery-2026-10-17/runtime/firestore-after.json).
- [Room snapshot summary](test-artifacts/security-recovery-2026-10-17/runtime/room-after-force-stop.json), [real restart assertion](test-artifacts/security-recovery-2026-10-17/runtime/restart-result.json), [installed environment/version](test-artifacts/security-recovery-2026-10-17/runtime/app-runtime.json).
- Actual Home screenshot and UI dump agree with Room:7 jobs, recorded959423, due959283; visible cash120; two deliberately rejected actions retain a warning. This warning is expected test data, not erased to improve appearance. No claim that this synthetic amount represents a real business balance.
- Three new PNGs decode1080×2400. One captured after instrumentation shows Android launcher and is explicitly excluded as application evidence. Two show actual Home after relaunch/restart. [Decoded gallery manifest](test-artifacts/security-recovery-2026-10-17/runtime/gallery-integrity.json), [Home](test-artifacts/security-recovery-2026-10-17/runtime/app-relaunch.png), [restart](test-artifacts/security-recovery-2026-10-17/runtime/restart-preserved-queue.png).
- Logcat contains emulator system ANRs in com.android.phone, Google Play services and TTS during boot. No app-package ANR/FATAL EXCEPTION appears in the captured selected error log. This is limited log evidence, not a no-ANR guarantee; it further limits SMS/performance acceptance. [Errors](test-artifacts/security-recovery-2026-10-17/runtime/crash-logcat.log), [scenario tags](test-artifacts/security-recovery-2026-10-17/runtime/selected-logcat.log).
- No physical phone was connected. Physical-device acceptance, real phone auth, multi-client conflicts, UI workspace switching totals and deployed Worker integration remain NOT VERIFIED. No prior physical-app data or queue was touched.

## Commands, actual results and failures

Exact invocation arguments, environment and exits are in [commands.json](test-artifacts/security-recovery-2026-10-17/commands.json). All commands ran from the approved worktree. Java21.0.10, Node24.14.1, Firebase CLI15.33.0; Functions package requests Node22 and local emulator warned it used24. SDK XML/deprecation warnings remain in build logs.

| Run | Exit | Tests / result | Evidence |
|---|---:|---|---|
| Original auth recovery, proper harness |1|8 tests;2 failures reproduced|auth-reproduced.log / auth-before.xml|
| Fixed auth recovery |0|8 passed|auth-after.log|
| Full Android unit/Robolectric |0|461 passed;0 failures/errors/skips;58 suites|android-final.log / android-junit/|
| New focused Rules |0|14 passed;0 failures/skips|security-focused.log|
| Full Firestore/security |0|248 passed;0 failures/skips|rules-final.log / rules.xml|
| Worker/backend/policy/migration |0|119 passed;0 failures/skips|backend-final.log / backend.xml|
| Validator regressions |0|20 passed|validator-tests.log|
| deviceTest app + test APK assembly |0|BUILD SUCCESSFUL|device-build.log|
| Gradle connected-device launcher |1|0 executed; uncached UTP32.1.1 dependency prevented launch|runtime/instrumentation.log|
| Same built APKs, standard Android runner |0|15 passed;0 failures/skips|runtime/adb-instrumentation.log|
| Independent demo Firestore checks |0|13 assertions passed, not counted as extra test-suite cases|runtime/firestore-after.json|
| Actual restart/queue comparison |0|all 4 rows unchanged, schema 19, UI/Room totals match|runtime/restart-result.json|

The Gradle launcher failure is retained. The standard runner avoids the missing host dependency and executes the same built test APK; this is not a skipped assertion or mocked replacement. No new CI run or remote test claim is made.

## Requirement-specific security register

Each row's exact source text, preconditions, trusted enforcement, test name, actual assertion, execution result, evidence and remaining gap are in [requirement-evidence.json](test-artifacts/security-recovery-2026-10-17/requirement-evidence.json). Evidence is requirement-specific; partial evidence stays partial.

| ID | Exact requirement | New status / scope |
|---|---|---|
| SEC-01 | Authenticated UID required. | VERIFIED — Automated Test; Physical-device and deployed-service behavior are not established by this local assertion. |
| SEC-02 | Exact workspace membership required. | VERIFIED — Automated Test; Physical-device and deployed-service behavior are not established by this local assertion. |
| SEC-03 | ACTIVE status required. | VERIFIED — Automated Test; Physical-device and deployed-service behavior are not established by this local assertion. |
| SEC-04 | Only recognized workspace role grants access. | VERIFIED — Automated Test; Physical-device and deployed-service behavior are not established by this local assertion. |
| I-04 | Membership authorizes Partner/Operator; root ownerUid identifies Owner. | VERIFIED — Automated Test; Physical-device and deployed-service behavior are not established by this local assertion. |
| I-02 | Role is (memberUid,workspaceId), not global. | VERIFIED — Automated Test; Physical-device and deployed-service behavior are not established by this local assertion. |
| I-06 | Viewer never rewrites stored membership role assignments. | VERIFIED — Automated Test; Physical-device and deployed-service behavior are not established by this local assertion. |
| SEC-06 | Record workspace matches path. | IMPLEMENTED — Verification Incomplete; One representative shared-envelope path is exercised; this alone does not establish every record/path constraint. |
| SEC-11 | Unmatched paths default deny. | VERIFIED — Automated Test; Physical-device and deployed-service behavior are not established by this local assertion. |
| SEC-12 | Legacy collaborationGroups/alternate path denied. | VERIFIED — Automated Test; Physical-device and deployed-service behavior are not established by this local assertion. |
| W-06 | Bidirectional forged cross-workspace requests/references deny. | IMPLEMENTED — Verification Incomplete; Bidirectional read/write/query tested; all linked-reference entity combinations and actual workspace switching remain incompletely verified. |
| A-09 | Genuine invalid/revoked/signed-out session handled honestly; cache is not Auth. | IMPLEMENTED — Verification Incomplete; Profile helper only; token refresh, full callback races and real phone OTP remain separate. |
| A-12 | Fence delayed auth/profile callbacks after logout/account switch. | IMPLEMENTED — Verification Incomplete; Does not simulate a delayed Firebase callback completing after a newer session. |
| O-03 | Stable operationId survives retry/process recreation. | VERIFIED — Instrumented/Virtual Device; Scoped to the recorded synthetic device scenario; no physical handset, carrier SMS, deployed Worker or arbitrary production-state claim. |
| O-06 | Before-deadline offline action syncs later; after deadline denies. | VERIFIED — Instrumented/Virtual Device; Scoped to the recorded synthetic device scenario; no physical handset, carrier SMS, deployed Worker or arbitrary production-state claim. |
| O-11 | Individual cloud acknowledgement precedes synced mark. | IMPLEMENTED — Verification Incomplete; Protocol unit assertion alone does not observe repository acknowledgement ordering in Room. |
| O-15 | Process genuinely PENDING, not REJECTED commands. | IMPLEMENTED — Verification Incomplete; Direct DAO scenario, not every uploader path. |
| O-17 | Account/workspace changes during batch cannot leak/reassign. | IMPLEMENTED — Verification Incomplete; Mid-commit/mid-ack account and workspace switching races are not fully covered. |
| W-01 | Composite (workspaceId,id) keys isolate same-numbered records. | VERIFIED — Automated Test; Synthetic schema 19 rows; no authentic historical releases. |
| W-02 | Every local query/search/filter uses authorized active workspace. | IMPLEMENTED — Verification Incomplete; Only this DAO path; not proof that every application query is scoped. |
| I-12 | Financial engine uses only authorized workspace data. | IMPLEMENTED — Verification Incomplete; Dataset authorization feeding every financial/report path remains incompletely verified. |

Nine newly verified automated IDs: **I-02, I-04, I-06, SEC-01, SEC-02, SEC-03, SEC-04, SEC-11, SEC-12**. Two newly verified virtual-device IDs: **O-03, O-06**. Existing I-03/W-01 remain automated; the same test is not counted as a new requirement merely because it supports several partial scenarios.

## Matrix and gate reconciliation

Machine-derived current327 rows: **11 automated verified,2 virtual-device verified,116 implemented/incomplete,195 NOT VERIFIED,3 authorization-blocked,0 limitation-only,0 FAILED**. Previous:2/0/111/211/3/0/0. This is nine more automated and two virtual-device requirement mappings; not an application completion percentage. Zero FAILED rows means the final executed assertions have no unresolved failure; it does not mean zero defects or complete coverage.

The previous matrix is preserved; old source audit remains historical. [Current matrix](architecture-v16-requirement-matrix.md), [change manifest](test-artifacts/security-recovery-2026-10-17/matrix-changes.json), [structural validator output](test-artifacts/security-recovery-2026-10-17/matrix-integrity.json). Validator success establishes structural/reference integrity, not semantic completeness. Its code/assertions were unchanged in this phase.

Gate remains **NOT READY** because complete lifecycle/security acceptance, phone authentication and broader role/workspace/UI acceptance, authentic historical migration safety, and existing release-critical evidence/approval gaps remain unresolved. D-16/D-CONFLICT/J-02 retain their authorization boundaries; no destructive backup/purge/live inspection was attempted.

Additional release concern from the complete diff review: the preserved local build-script changes embed signing-credential fallbacks and use the release keystore for debug signing. Values were masked during review and not copied into this report. This pre-existing configuration needs secret-handling/signing review before publishing the candidate; it was not changed as part of the denial-cache fix.

Next evidence required: restore the missing authoritative remediation plan; add deterministic full auth callback/late-response and membership restoration tests; run permitted/denied complete workflows and workspace-switch refresh assertions; supply approved sanitized released schema exports/fixtures; repeat physical-phone and phone-OTP acceptance when authorized infrastructure exists. No production deployment or data repair is requested as part of this local evidence task.

## Changed files and preservation

1. VerifiedProfileRecovery.kt — fail closed on explicit Firestore authentication/authorization denial.
2. VerifiedProfileRecoveryTest.kt — five missing negative/recovery assertions and correct Android-capable harness.
3. security-tests/security-recovery.test.cjs — fourteen paired security regressions with isolated synthetic tenants.
4. scripts/run_backend_suite.mjs — include those regressions in the existing Rules suite/CI path.
5. architecture-v16-requirement-matrix.md — only reviewed evidence/status entries and derived counts; original copied first.
6. This report, evidence/run manifests, fresh JUnit/log/runtime files — actual source/test/runtime provenance.
7. Existing execution report/findings — short recovery pointers added without deleting historical results.

[Final Git status, baseline hash preservation and source-state check](test-artifacts/security-recovery-2026-10-17/final-state.json). No unrelated source edit, destructive migration, app-data clearing, queue deletion, real account creation, production access/write, Rules/Worker publication, billing/routing change, commit, push, merge or deployment. Desktop checkout untouched. Local test service/AVD shutdown is recorded in the final run artifact. All 153 baseline paths remain,146 of 150 baseline hashes are unchanged, and the four differences are exactly the reviewed matrix, two report pointers and changed auth test. All 242 tested source hashes match the final source. A first preservation-script guard stopped because its expected-change list omitted that intentionally modified test; the corrected explicit list passed without accepting any unexpected difference.
