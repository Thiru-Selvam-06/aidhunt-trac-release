> **Subsequent security/runtime recovery, 2026-10-17:** Gate remains **NOT READY**. New confirmed denial-cache fix; final local tests: 461 Android / 248 Rules / 119 backend / 20 validator, plus 15 real virtual-device tests. Current matrix: 11 automated + 2 virtual + 116 incomplete + 195 NOT VERIFIED + 3 blocked = 327. See [new evidence report](security-runtime-evidence-recovery-2026-10-17.md). The historical reconciliation below is preserved, including its earlier counts and withdrawn claims.

# Architecture v1.6 release-gate reconciliation — 2026-10-17

Decision: **NOT READY**.

This report supersedes the previous release-ready and “zero P0/P1” conclusions. Structural completeness is distinct from behavioral verification. No completion percentage is assigned.

## Repository and source versions

- Root: `C:\Users\user\.codex\worktrees\v16-implementation\aidhunt-trac`.
- Branch: `codex/comprehensive-regression-2026-10-17`.
- HEAD: `937088dff3115d4e995dbf6d1dfbed1158ccbe8d`.
- Upstream: `origin/codex/comprehensive-regression-2026-10-17`; ahead/behind `0/0` at inspection. No staged changes. The worktree is dirty; HEAD alone does not identify tested source.
- Exact status and input hashes: [baseline.json](test-artifacts/release-gate-2026-10-17/baseline.json). Final status and hashes: [final-state.json](test-artifacts/release-gate-2026-10-17/final-state.json).
- [Tested application/test source hashes](test-artifacts/release-gate-2026-10-17/tested-source-sha256.json) identify uncommitted code included in these executions. This local run is not a new GitHub Actions run.
- The Desktop checkout was not used as a source, inspected for substitution, or modified. Its differences therefore remain unknown.

## Exact requirement reconciliation

Independent heading/table extraction found **327 source IDs and 327 matrix IDs**, each unique. Missing IDs: `[]`. Extra IDs: `[]`. Source duplicate IDs: `[]`. Matrix duplicate IDs: `[]`. No placeholder requirements or missing-row padding was added.

The claim of a 255-row uploaded copy cannot be reproduced from the current repository. No separate 255-row matrix file accompanies this request, so its byte content/hash and exact differences are unavailable. Do not infer a missing trailing block of 72 IDs. The actual pre-edit matrix SHA256 was `f3e3b93b9b9d7dc91927728da95b3ec548db99d8e69a8abf7d6b99643bb9a14f`; source audit SHA256 was `39fcc8ab02b1e0f37ab62db77cb28e32cd9e6fafe9d61fb570c77ff17360f9f4`. The source audit is unchanged and remains historical, dated 2026-10-09. Its old failure observations are not newly executed failures.

The previous validator would detect a genuinely missing matrix ID against a complete source set. Its demonstrated blind spots were elsewhere: duplicate source headings were overwritten by a dictionary; indented/malformed rows could be ignored; missing summary categories/zero totals were not required; most links and cited test methods were not checked; PNG existence was confused with readable runtime evidence; structural success was described as evidence-grounded behavior.

The strengthened validator checks these cases and reports structural success only. [Its pre-reconciliation rejection](test-artifacts/release-gate-2026-10-17/matrix-before-errors.json) and [final exact reconciliation](test-artifacts/release-gate-2026-10-17/matrix-integrity.json) preserve hashes. Missing-middle-ID, duplicate, invented, malformed, wrong-status/summary, bad line/path, nonexistent assertion, and corrupted-image fixtures are covered by **20 passing validator tests**, including valid CLI exit 0 and invalid CLI exit 1. It does not prove semantics, remote URLs, or deployed behavior.

## Evidence status correction

The actual original 327-row matrix counted: automated 192, virtual-device 13, implementation-incomplete 111, accepted-limitation 8, authorization-blocked 3. The supplied 192+13+39+8+3 arithmetic totals 255, but describes a different/unavailable artifact.

A per-row review found **150 rows citing nonexistent test assertions**. Other mappings used unrelated/partial UI tests, generic “rules pass” labels or screenshots to claim unobserved permissions. For example, a workspace-picker rendering test does not prove new-account workspace creation; a direct Customer delete denial does not prove the trusted dependency transaction. The Owner Account screenshot cannot prove Operator permissions.

Final matrix arithmetic:

| Status | Count |
|---|---:|
| VERIFIED — Automated Test | 2 |
| VERIFIED — Instrumented/Virtual Device | 0 |
| IMPLEMENTED — Verification Incomplete | 111 |
| DOCUMENTED — Accepted Limitation | 0 |
| BLOCKED — Authorization Required | 3 |
| FAILED | 0 |
| NOT VERIFIED | 211 |
| Total | 327 |

`2 + 0 + 111 + 0 + 3 + 0 + 211 = 327`. The two retained narrow mappings are `I-03` (recognized roles/Co-Owner denial) and `W-01` (composite key/workspace-scoped same-ID persistence), backed by assertions actually read and executed. They are local test evidence, not full device acceptance. The other statuses were not upgraded from source existence. All previous mappings, precise source requirements and disposition reasons are preserved in [requirement-evidence-review.json](test-artifacts/release-gate-2026-10-17/requirement-evidence-review.json).

The eight former “accepted limitations” mislabeled functional requirements: OTP expiry/single-use/secondary-session behavior, unrelated-command progress, pending-only selection and stress verification. They are now NOT VERIFIED. The separately approved **original client actionTime policy, including possible clock manipulation**, remains accepted and unchanged. It is not evidence that those eight requirements passed. The three destructive live backup/purge authorization blockers remain; `J-02` actually requires real-data inspection/restorable export, not additional test-phone provisioning.

## Room schema and migrations

`AppDatabase.kt:48` declares **version 19** for all variants. `getInstance` registers every migration `4→5` through `18→19` (15 transitions). Filenames are deliberately stable and do not express current schema:

| Variant | Filename | Schema |
|---|---|---:|
| Standard | `aidhunt_trac_v4.db` | 19 |
| physicalTest | `aidhunt_physical_test_v18.db` | 19 |
| liveTest | `aidhunt_live_test_v18.db` | 19 |
| deviceTest | `aidhunt_device_test_v18.db` | 19 |

`W-01`'s “version = 17” anchor was stale and is corrected to 19. `J-04` is a CI-preservation requirement and now points to the workflow, not Room. `J-06` is signing/configuration review and now points to the Gradle configuration, not Room. Historical v17/v18 migration names and stable filenames were retained.

Fresh Android JUnit includes **10 RoomMigrationChainTest tests** and **2 CollectionPersistenceTest tests**, all passing. The full-chain method explicitly invokes `MIGRATION_18_19` after the prior 14 migrations, verifies three seeded records (Tractor, Customer, Expense), new tables and collector columns. Its old temporary filename/comment mentioning v18 does not change the executed chain. The targeted 18→19 test preserves payment ID/workspace/amount and asserts three blank collector fields. CollectionPersistenceTest additionally runs 18→19 twice, preserving an ID above JavaScript precision, recorder/time and the original outbox payload/actionTime.

These are hand-built synthetic SQLite fixtures, not authenticated exported schemas from every deployed release. They **do not prove 100% preservation of all production data**, every historical row shape or full Room schema validation for every old release. `J-01` remains NOT VERIFIED for that broader requirement. No real database or outbox was edited.

## Fresh local execution and provenance

All following executions completed with exit 0 against the current local source. No production credentials were used for synthetic backend/Rules tests. The reports copied below establish summaries and actual assertions; source hashes disambiguate dirty code from HEAD.

| Suite | Tests | Failures/errors/skips | Evidence |
|---|---:|---|---|
| Android JVM/Room/Compose regression | 456 | 0/0/0 | [log](test-artifacts/release-gate-2026-10-17/android-regression.log), [JUnit directory](test-artifacts/release-gate-2026-10-17/android-junit/) |
| Firestore Rules/security integration | 234 | 0/0/0 | [JUnit](test-artifacts/release-gate-2026-10-17/rules.xml), [execution metadata](test-artifacts/release-gate-2026-10-17/rules-run.json) |
| Worker/backend/policy/migration harness | 119 | 0/0/0 | [JUnit](test-artifacts/release-gate-2026-10-17/backend.xml), [execution metadata](test-artifacts/release-gate-2026-10-17/backend-run.json) |
| Structural validator regressions | 20 | 0/0/0 | [log](test-artifacts/release-gate-2026-10-17/validator-tests.log) |
| physicalTest APK assembly | build | exit 0 | [build log](test-artifacts/release-gate-2026-10-17/physical-test-build.log) |

Android+Rules+backend = **809 tests**, separately **20 validator tests**. Node JUnit uses direct `testcase` nodes under `testsuites`; counts above were computed from actual testcases, not nonexistent aggregate attributes. Rules/backend invocation names include multiple integration/policy suites; “119 Cloudflare tests” would be misleading. They are local Node tests and Firestore emulator tests, not evidence of deployed Worker execution. Earlier stabilization logs exist and contain 234/119 pass summaries but were not used to certify later code; fresh reports supersede those snapshots.

Commands used from this worktree (JDK 21.0.10, Node 24.14.1, Firebase CLI 15.33.0, bundled Python 3):

```powershell
# JVM memory flags used on both Gradle commands:
# --offline --console=plain --max-workers=1 --no-daemon
# -Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=512m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8
.\gradlew.bat :app:testDebugUnitTest --init-script scripts/ci-tests.gradle [flags above]
.\gradlew.bat :app:assemblePhysicalTest [flags above]
# Environment: GCLOUD_PROJECT=demo-aidhunt-v16; FIRESTORE_EMULATOR_HOST=127.0.0.1:18180
# GOOGLE_APPLICATION_CREDENTIALS and FIREBASE_AUTH_EMULATOR_HOST absent.
node <existing firebase-tools>/lib/bin/firebase.js emulators:exec --only firestore --project demo-aidhunt-v16 --config firebase.ci.json "node scripts/run_backend_suite.mjs rules && node scripts/run_backend_suite.mjs backend"
python -m unittest discover -s scripts -p test_audit_matrix_integrity.py -v
python scripts/audit_matrix_integrity.py --json docs/test-artifacts/release-gate-2026-10-17/matrix-integrity.json
```

The Gradle log and metadata identify execution time; all 456 testcases are copied, including the 10+2 migration cases and 16 build-boundary cases. Actual SDK/tool binary paths are those already installed locally; no tooling/dependencies were installed for this gate. Flags are abbreviated above solely for readability. Complete argument arrays and environment are recorded in [commands.json](test-artifacts/release-gate-2026-10-17/commands.json).

## Gallery integrity and runtime limits

The actual gallery contains **12 `.png` files, zero readable PNGs**. All fail Pillow decoding and PNG byte validation. Their initial bytes are `ff fe fd ff 50 00 4e 00 47 00`, inconsistent with PNG and consistent with text-transcoded binary; the originating command is not available to prove cause. Existing files are preserved unchanged. The old “nine screenshots” assertion was also inconsistent with its own ten-row table.

[Gallery integrity manifest](test-artifacts/release-gate-2026-10-17/gallery-integrity.json) records every filename, byte count, hash and invalid signature. [Gallery README](test-artifacts/emulator-acceptance-2026-10-17/README.md) and HTML now identify invalid evidence and use repository-relative links. No captures were fabricated, repaired speculatively or imported from Desktop. Phone OTP, all-role runtime access, offline restart, real workspace switching, financial persistence and cloud sync remain NOT VERIFIED by this gallery. A valid screenshot alone would still not establish these backend properties.

ADB showed no connected device during this investigation. No physical acceptance or new installation was performed as part of reconciliation. The APK build is not a device test.

## Confirmed code defect carried into this gate

Before this gate request, the focused workspace investigation reproduced a discovery bug: the isolated build filtered membership candidates to only `STAGING_WORKSPACE` even though routing/switching supported three authorized staging IDs. The shared `permitsWorkspaceDiscovery` predicate now provides the intended build boundary. UID/current-membership validation remains separate. The standard build's business/personal path and isolated build's approved-staging path have synthetic regression coverage. No phone allowlist or membership bypass was added.

The first six-test regression run failed once against the original predicate; [failure log](test-artifacts/release-gate-2026-10-17/workspace-regression-before.log) is retained. The final 16-case boundary suite passes within the 456 tests. Actual failing-account login/recovery remains unverified without connected-device/account evidence. This source fix is not a promise that all test numbers can enter every environment.

## Changes, preservation and release blockers

- `FirebaseEnvironment.kt`, `WorkspaceRepository.kt`, `WorkspaceBuildBoundaryTest.kt`: the preceding confirmed discovery defect and focused boundary regressions. Existing additional selector/UI/session/migration work is preserved, not reset or replaced.
- `scripts/audit_matrix_integrity.py`, `scripts/test_audit_matrix_integrity.py`: structural fixes and 20 deterministic fixture tests.
- `architecture-v16-requirement-matrix.md`, this report, `architecture-v16-verification-findings.md`: exact IDs/counts, withdrawn unsupported passes, corrected schema facts and bounded evidence.
- Gallery README/HTML: invalid evidence notice; original PNGs untouched.
- `docs/test-artifacts/release-gate-2026-10-17/`: current logs, JUnit, source/input hashes, status snapshots, per-row reconciliation and integrity manifests.

All **211 historical baseline paths remain present**, with 16 changed hashes from authorized accumulated work; [preservation.json](test-artifacts/release-gate-2026-10-17/preservation.json) lists them. This is preservation of work, not a claim that all file contents were frozen unchanged. No saved records/pending payloads were accessed, deleted, reassigned or migrated during this gate. Pending phone queue counts are not newly observed.

Release remains **NOT READY** because the essential role/auth/security requirements are not fully mapped to executed behavior, runtime evidence is unreadable, authentic historical schema verification is incomplete, and live backup/purge approval blockers persist. No zero-P0/P1-risk conclusion is supported. Next action: re-establish requirement-specific security/acceptance evidence in isolated infrastructure, beginning with the withdrawn role/auth and migration mappings; do not deploy on structural success alone.

No production reads/writes or membership changes, deployments, Rules/Worker publishing, billing/routing changes, Git commits/pushes/merges or Desktop edits were performed for this gate.


## Controlled stabilization follow-up — 2026-10-18

Release gate remains **NOT READY**. Signing configuration, actual Auth-listener coordination, workspace request fencing and stale allocation caches received focused corrections. The final-source Android run passed484 tests; full phone/OTP and actual asynchronous membership acceptance remain incomplete. No requirement status was promoted. See [controlled evidence and limitations](controlled-stabilization-2026-10-18.md); previous evidence and results are preserved.
