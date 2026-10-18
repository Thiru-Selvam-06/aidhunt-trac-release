# Controlled online/offline stabilization — 2026-10-18

Decision: **NOT READY**. Bounded automated online/offline assertions pass; full runtime acceptance remains open. No production operation occurred.

## Baseline

Actual root `C:\Users\user\.codex\worktrees\v16-implementation\aidhunt-trac`; branch `codex/comprehensive-regression-2026-10-17`; HEAD `937088dff3115d4e995dbf6d1dfbed1158ccbe8d`. Upstream0 ahead/0 behind, staged empty,265 dirty/untracked paths and413 hashes at baseline. [Baseline](test-artifacts/controlled-stabilization-2026-10-18/baseline.json); final preservation is recorded separately. All existing work and queues were preserved.

The exact `.agents/AIDHUNT_Trac_Production_Readiness_Remediation_Plan.md` remains absent from the authorized tree and accessible path history. No Desktop substitute was used. Authentic released Room schema exports remain unavailable; no production database was accessed to obtain them.

## Confirmed defects and changed files

| Source | Root cause and correction | Verification boundary |
|---|---|---|
| `app/build.gradle.kts` | Dirty baseline made debug/test variants inherit the release key and hardcoded password fallbacks. Restore standard debug signing; release uses protected external properties only; missing credentials gate packaging explicitly. | Configuration assertions, fake fixture and non-release builds; no real release signed |
| `FirebaseAuthService.kt`, new `AuthSessionRecovery.kt` | Direct sign-in/auto-OTP callbacks could publish/cache after supersession, and cancellation entered generic error handling. Fence attempts/cache writes/publication; preserve cancellation. Move actual Auth-listener state coordination into an injected, deterministic coordinator. Verification timeout now offers recovery without cache and retains matching cached access when offline. | Actual production coordinator tested with controlled loaders; actual Firebase/Google SDK completion and complete navigation races remain incomplete |
| `WorkspaceRepository.kt`, new `WorkspaceRequestFence.kt` | Initialization/selection lacked freshness checks after awaits; UNAUTHENTICATED could recover cached authority. Reject stale requests and both explicit denial codes; do not initialize authority from profile.role. Missing/malformed B allocation cache retained A values: clear old percentages/share details first. Cloud share publication also checks actor/workspace. | Five guard tests plus three actual repository cache tests; full asynchronous Firestore membership integration is not established |
| New `AuthSessionRecoveryTest.kt`, `WorkspaceRequestFenceTest.kt`, `WorkspaceCachedLedgerRecoveryTest.kt` | Add23 deterministic regressions, including actual coordinator StateFlow publication and real repository/isolated SQLite cache paths. | JVM/Robolectric, not physical-device/live-service evidence |
| New `scripts/signing-isolation.init.gradle` | Assert all four debug/test variants use debug signing, release uses release, package IDs/suffixes remain, packaging credential gate attached. | Config/model only; synthetic values never sign a release |

Original actionTime/operationId/outbox payloads, approved offline eligibility policy, accounting formulas, Firebase routes, application IDs and Room version19 were preserved. No installed data or queue was read/modified.

## Executed tests

**484 Android tests,61 suites,0 failures/errors/skips**, exit0. [Full JUnit](test-artifacts/controlled-stabilization-2026-10-18/android-junit/), [summary](test-artifacts/controlled-stabilization-2026-10-18/android-summary.json), [method-by-method results](test-artifacts/controlled-stabilization-2026-10-18/scenario-results.json).

- AuthSessionRecoveryTest15: `delayedRecoveryCannotRestoreAfterLogout`, `lateAccountAResponseCannotReplaceB`, `olderResponseForSameUidCannotReplaceNewRefresh` pause loaders using Deferred/NonCancellable and assert actual profile/AuthState stays signed out or B/newer. Missing/wrong UID cache, online refresh, explicit PERMISSION_DENIED/UNAUTHENTICATED, invalid/disabled identity, mismatched returned profile, absent profile, cancellation and verification timeout assert final states. True IOException/timeout retains only matching cached access; uncached timeout leaves Loading for Error. No sleeps.
- VerifiedProfileRecoveryTest8: prior denial/cancellation/disabled-user versus genuine-unavailable recovery assertions remain passing.
- WorkspaceRequestFenceTest5: current request allowed; missing/wrong identity denied; older workspace/account/logout-relogin requests cancelled. These guard tests do not prove the full repository/network flow.
- WorkspaceCachedLedgerRecoveryTest3: actual repository initially loads A's60% values, then clears percentages, shares and member details for uncached or malformed B. Isolated SQLite/preferences, no network requests.
- Existing OfflineActionTimeTest6, WorkspaceCommandProtocolTest17 and RoomScopeTest10 retain original identity/times across reopen/retry, reject mismatched acknowledgements, retain rejected data/dependencies, and enforce scoped operations. CashBalanceRegressionTest5 and ExpenseWorkspaceIsolationTest2 pass. These do not certify every business flow online/offline.
- Existing RoomMigrationChainTest10 and CollectionPersistenceTest2 pass on **synthetic** fixtures. Full chain seeds Tractor42/Customer88/Expense7, not every historic job/payment/outbox row. Synthetic18→19 preserves collection/outbox identity/time/payload. Full authentic historical coverage stays NOT VERIFIED; approved sanitized released schemas or reproducible release fixtures are required.
- Validator regression suite20 passed, exit0. Structural validation cannot certify semantic completeness.

Intermediate attempts remain in artifacts: initial test-harness compilation errors, a28-test run with one timeout assertion failure, an incorrectly quoted PowerShell properties argument and a configuration-cache issue in the test init script. Source changed during these early iterations; they are not final-source evidence. The frozen-source484 run is accepted. No failures/assertions were removed to obtain that result. The Roborazzi finalizer task is skipped by Gradle; JUnit skipped count is0.

## Signing and build commands

All Gradle commands use JDK21; `--offline --console=plain --max-workers=1 --no-daemon`; JVM `-Xmx1024m -XX:MaxMetaspaceSize=512m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8`.

| Command | Result / log |
|---|---|
| `gradlew.bat :app:validateReleaseCredentials` | Expected exit1, clear missing-credentials message; signing-missing.log |
| `gradlew.bat :app:verifySigningIsolation :app:validateReleaseCredentials --init-script scripts/signing-isolation.init.gradle -PreleaseSigningProperties=docs/test-artifacts/controlled-stabilization-2026-10-18/signing-fixture.properties` | Exit0; signing-clean.log; fixture/model only |
| `gradlew.bat :app:testDebugUnitTest --init-script scripts/ci-tests.gradle` | Exit0; android-full.log;484 tests |
| `gradlew.bat :app:testDebugUnitTest --tests com.example.AuthSessionRecoveryTest --tests com.example.VerifiedProfileRecoveryTest --tests com.example.WorkspaceRequestFenceTest --tests com.example.WorkspaceCachedLedgerRecoveryTest --init-script scripts/ci-tests.gradle :app:assembleDebug :app:assemblePhysicalTest` | Exit0; focused-and-build.log;31 focused tests and both APKs built |
| `python -m unittest discover -s scripts -p test_audit_matrix_integrity.py -v` | Exit0; validator-tests.log;20 tests |

Logs are under [this run](test-artifacts/controlled-stabilization-2026-10-18/). Synthetic properties refer to an existing non-key file with explicitly fake values: configuration presence only, no real signing proof. No release key was regenerated/deleted/modified or exposed. Standard debug signing may be incompatible with a previously release-key-signed installed test package: check certificates before installation; never uninstall/clear data to force compatibility.

## Requirements, provenance and remaining gates

[Exact requirement/assertion mapping](test-artifacts/controlled-stabilization-2026-10-18/requirement-evidence.json): A-09 and A-12 remain IMPLEMENTED — Verification Incomplete. W-04 stays NOT VERIFIED as a whole. Their evidence text is strengthened without promoting partial proofs.

Counts remain **327 total:11 VERIFIED Automated,2 VERIFIED Virtual Device,116 IMPLEMENTED incomplete,195 NOT VERIFIED,3 authorization-blocked**. No completion percentage. Prior virtual-device statuses remain tied to their historical evidence.

[Backend provenance](test-artifacts/controlled-stabilization-2026-10-18/server-provenance.json):38 server/Rules/test source files match the previous executed manifest. Prior248 Rules and119 backend passes are preserved, not rerun or represented as deployed evidence. No server/Rules change required repetition.

ADB listed no connected phone/emulator. No APK installed, phone OTP, actual account login, live backend request or runtime screenshot was performed. Prior15 virtual-device cases remain historical evidence for the old source. All12 invalid legacy PNGs remain invalid and untouched.

Remaining: actual phone/OTP and SDK-level sign-in/logout completion races; full asynchronous membership/navigation/outbox acceptance on this candidate; certificate-compatible installation preserving queues; authentic historical schemas; missing remediation plan; existing backup/purge authorization gates. Both online/offline have passing bounded automated assertions, **not complete runtime acceptance**.

No production reads/writes, membership/Auth configuration changes, Rules/Worker deployment, billing/routing changes, real data migration/purge, Desktop edits, commit/push/merge/reset/clean/stash or branch switching occurred.


## Final build evidence

Both debug and physicalTest APKs built successfully without external release credentials, exit0. Final focused rerun31/31 passed with0 errors/failures/skips. APK signatures verify, exit0, using standard debug signing. [APK identity/hash results](test-artifacts/controlled-stabilization-2026-10-18/apk-results.json), [focused results](test-artifacts/controlled-stabilization-2026-10-18/focused-summary.json). Physical-test package remains `com.aistudio.aidhunttrac.cedlir.physicaltest`, version5/1.0.4-physical-test, project `trac-aidhunt`; this is a build identity check, not a live connection or authorization test. No installation was performed. The final configuration-only signing check was rerun with --no-configuration-cache and succeeded.


## Final preservation and status

[Final Git/preservation snapshot](test-artifacts/controlled-stabilization-2026-10-18/final-state.json): 366 dirty/untracked paths, staged0; branch/HEAD unchanged. All baseline files remain; 211 tested source hashes match final files. All12 original invalid PNG hashes are unchanged. [Commands/exits](test-artifacts/controlled-stabilization-2026-10-18/commands.json), [matrix integrity](test-artifacts/controlled-stabilization-2026-10-18/matrix-integrity.json):327 IDs, zero structural errors, status counts unchanged. These reports do not establish full runtime completeness.
