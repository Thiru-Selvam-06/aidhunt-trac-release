# Collection attribution and collection-date fixes — 2026-10-16

Scope: release worktree `C:/Users/user/.codex/worktrees/v16-implementation/aidhunt-trac`, branch `codex/v1.6-implementation`, HEAD `0bf772b9dd3a7d7ebfaae7ebcd1e96ca6f1fe141`. Existing extensive uncommitted implementation changes are retained. The Desktop checkout is untouched.

## Proven causes and contract

- `WorkspaceRepository.recordCustomerPayment` accepted the form's collector arguments but unconditionally persisted the authenticated actor in all collectedBy fields. The form additionally mixed partner phone numbers with membership UIDs and deduplicated by display name. Selection could survive an account/workspace change.
- The dialog had no minimum date and the repository allocated/saved collections without checking source creation dates. The existing collection Rules checked the timestamp type, not its calendar lower bound.
- The user explicitly chose **Asia/Kolkata for all collection business dates**. This does not change operation action timestamps or authorization windows.
- New `collectorUid`, `collectorName`, `collectorRole` fields identify the selected business collector. Existing `collectedBy*` fields remain the authenticated recorder/audit identity, preserving reversal and operation authorization. The persisted operation actor remains the authenticated user.
- Eligible collector choices come exclusively from ACTIVE OWNER/PARTNER memberships in the record's workspace, deduplicated by UID. A Partner can select only self. The repository resolves name/role from that membership rather than trusting form strings. History resolves display names by record workspace + UID and separately shows Recorded by when different. Existing financial formulas are unchanged.
- Room migration 18→19 adds three empty-default columns without rewriting legacy identity, timestamps, amounts, allocations or outbox rows. Database filenames are unchanged. Legacy rows remain readable; absent date fields remain unknown rather than being fabricated from the current time.
- The picker lower bound is the latest creation day among source Entries actually allocated by the existing oldest-first allocation order for the entered amount. UI and repository validate the same India-calendar rule; all allocated source dates are validated before the first local mutation/enqueue. Missing source creation dates block new collection until reviewed.
- The sync transaction revalidates the date against the actual server Entry, preserving original operation IDs, action timestamps and transaction balances. Local Rules independently enforce the India-day lower bound and collector membership/role. Reversal retains immutable collector metadata and its existing authorization; legacy reversals do not acquire invented collector fields.

## Rules review and rollout boundary

The changed local collection Rules have **not been deployed**. Direct remote enforcement remains incomplete until a separately approved Rules review/deployment. No Worker deployment is needed: collections use the existing atomic Firestore collection/Entry transaction, not a new Worker endpoint. Old queued creations with an invalid/missing source date will remain recoverable but be rejected after enforcement; they must be reviewed, never silently rewritten or discarded. A member rename/role change while a collection waits offline can require review because the new selected-collector snapshot must match current membership. Legacy collection reversals retain the existing policy and do not revalidate historic business dates.

The adjacent wildcard asset match is limited to tractors/extensions and cannot bypass collection checks. Authentication, finance role requirements, allocation deltas, server receipt provenance and immutable original fields remain enforced. Negative tests include other members, Operators, unknown/foreign/inactive members, missing source dates, previous India dates, and altered collector metadata during reversal.

I've set up prototype Security Rules to keep the data in Firestore safe. They are designed to be secure for authenticated workspace collection writes by validating current collector membership, retaining the recorder identity, enforcing the source Entry date bound, and preserving atomic allocation and reversal checks. However, you should review and verify them before broadly sharing your app. If you'd like, I can help you harden these rules.

## Final local validation

- `:app:compileDebugKotlin --offline --console=plain --max-workers=1`: passed before the final history-timezone follow-up.
- `node --test --test-concurrency=1 security-tests/architecture.test.cjs security-tests/listener-queries.test.cjs`: **176 passed, 0 failed**, disposable `demo-aidhunt-v16` on localhost:18180. No production test writes.
- First Android full-suite attempt: failed because two test helpers did not register migration 18→19 (three fixture upgrades), then a new form test timed out waiting for UI idle and the test JVM exhausted memory. Interrupted; not counted as passed. Migration helper registrations corrected. The form idle timeout persisted with stable fixtures, native graphics and manual clock control. Those two form assertions are retained in `app/src/androidTest/java/com/example/CollectionFormDeviceTest.kt` for actual device execution, NOT counted as passed or skipped unit tests. History attribution remains in the unit suite. The final full unit run uses a temporary Gradle init script (`%TEMP%/aidhunt-collection-test-runtime.gradle`, 1024 MB test heap and a new test JVM every 10 classes) without changing repository build configuration. Final rerun: **337 tests passed, 0 failures, 0 errors, 0 skipped, 47 suites**.
- Device: ADB returned an empty device list. User subsequently requested implementation only until they connect the phone. No APK installation, physical-device workflow, live staging collection mutation, Room-on-phone verification, or Antigravity device result is claimed.

## Files extended for this task

Main: `data/auth/CollectionPolicy.kt`, `data/entity/Entities.kt`, `data/database/AppDatabase.kt`, `data/firebase/FirestoreModels.kt`, `data/firebase/FirestoreRepository.kt`, `data/repository/WorkspaceRepository.kt`, `ui/components/CommonComponents.kt` (optional timezone argument, other callers unchanged), `ui/screens/report/CustomerCreditDueTab.kt`, `ui/screens/report/CollectionHistoryTab.kt`, `firestore.rules`.
Tests: `CollectionPolicyTest.kt`, `CollectionPersistenceTest.kt`, `CollectionFormRegressionTest.kt`, migration registration in `ArchitectureIntegrationTest.kt` and `RoomScopeTest.kt`, existing `security-tests/architecture.test.cjs` transaction regressions, and `app/src/androidTest/java/com/example/CollectionFormDeviceTest.kt` (two form tests, NOT RUN).

No commit, push, production data write, membership change, Rules/Worker deployment, billing change, app data clearing or queue retry was performed.

Final local Rules SHA-256: `78beab28b4a4b7261d2f405a38a6f94c3cce43a635c0762e23451d97c4972551`. Final Rules/listener rerun: 176 passed, 0 failed, 0 skipped (30.700 seconds).

## Explicit checks still NOT VERIFIED

- The full authenticated `WorkspaceRepository.recordCustomerPayment` invocation rejecting an invalid date with no outbox/Room mutation is not separately exercised end to end. Its shared policy has unit coverage and is invoked for all allocated Entries before mutation; independent Rules transaction tests prove remote rejection leaves balances unchanged.
- Actual phone picker constraints, Owner→Partner A then Partner B selection, displayed identity after real cloud sync/restart, and Room/outbox acknowledgement after network retry remain physical/staging checks. Two equivalent pure-form assertions are retained as device instrumentation, compiled but not executed until an appropriate phone is connected. The earlier Robolectric rendering/idleness failure remains unresolved; its cause is not proven, and no conclusion about physical-phone behavior is drawn from it.
- No historical incorrectly labeled collection is automatically repaired: earlier records did not retain the selected collector UID, so that identity cannot safely be inferred.

## Final build and commands

- Android command: `.\gradlew.bat :app:testDebugUnitTest :app:assemblePhysicalTest :app:compileDebugAndroidTestKotlin --init-script "$env:TEMP\aidhunt-collection-test-runtime.gradle" --offline --console=plain --max-workers=1` — **BUILD SUCCESSFUL**, 11m 23s; 337 unit/Robolectric tests passed, 0 failed/errors/skipped. Both physical-test APK assembly and the existing Debug instrumentation Kotlin compilation passed. Instrumentation compilation is not execution; the existing build configuration does not expose a physical-test instrumentation target.
- The initial invocation requested nonexistent `compilePhysicalTestAndroidTestKotlin` and failed task discovery before execution; corrected to the configured Debug instrumentation target. No repository test-build configuration changed.
- Rules command: `node --test --test-concurrency=1 security-tests/architecture.test.cjs security-tests/listener-queries.test.cjs` with `FIRESTORE_EMULATOR_HOST=127.0.0.1:18180`, `GCLOUD_PROJECT=demo-aidhunt-v16` — **176 passed, 0 failed/skipped**. The temporary Emulator process has been stopped.
- `git diff --check` for the scoped source/Rules files — passed (line-ending conversion warnings only). Branch/HEAD unchanged. Final complete Git status captured privately at `%TEMP%/aidhunt-collection-final-git-status.txt`; 43 pre-existing tracked changes remain, with scoped additions/edits retained uncommitted. No claim of byte-identical touched files is made: the listed files intentionally extend the prior implementation.
- Final Android build log: `%TEMP%/aidhunt-collection-final-validation.log`; final Rules log: `%TEMP%/aidhunt-collection-rules-final.log`. Prior unsuccessful Android attempts remain in `%TEMP%/aidhunt-collection-android-validation.log`, `...focused-validation.log`, `...form-native.log`, `...form-clock.log` (each prefixed `aidhunt-collection-`).

APK: `app/build/outputs/apk/physicalTest/app-physicalTest.apk`.
Verified package: `com.aistudio.aidhunttrac.cedlir.physicaltest`; version `1.0.4-physical-test`, versionCode `5`; generated Firebase project `trac-aidhunt`; `PHYSICAL_TEST=true`, `DEVICE_TEST=false`, `LIVE_TEST=false`.

APK SHA-256: `a3c4ecdf0238215cfb905c059fa456938323bed385f29935e5bb5992de8798f6`; size 32063591 bytes.

## Acceptance status

| Check | Status | Evidence/limit |
|---|---|---|
| Selected collector vs authenticated recorder, both Partner choices, membership/workspace name resolution | PASS — local tests | CollectionPolicyTest, collection-history UI unit test, persisted demo Rules transaction |
| Same/later India day accepted; earlier/missing source date denied | PASS — policy and Rules tests | Day-boundary tests independent of phone timezone; failed remote batch preserves balance |
| Room persistence/reload, exact IDs, retained queue payload, migration replay | PASS — local tests | CollectionPersistenceTest plus existing ArchitectureIntegrationTest and RoomScopeTest |
| Android regression suite | PASS | 337/337, no skips |
| Rules/listener regression suite | PASS | 176/176, no skips; localhost disposable demo only |
| Staging APK and Android instrumentation compilation | PASS | Final successful Gradle command |
| Native form selector/picker and complete authenticated repository workflow on device | NOT VERIFIED | Two form assertions moved to existing device infrastructure after reproducible Robolectric idle failure; phone deferred by user |
| Live staging Firestore and matching phone Room/outbox acknowledgement after restart/retry | NOT VERIFIED | No live staging business mutation performed |
| New server-side invariant deployed | NOT DEPLOYED | Local Rules require separate explicit approval/review; existing deployed Rules unchanged |

When the phone is connected: install only this separate physical-test package in place, compare signing identity first, preserve all data/outbox rows, then collect with Partner A and Partner B selected and verify Collected by vs Recorded by, source-date lower bound/same-day acceptance, stored Room/cloud identities and amounts, and restart/retry retention. Do not use business workspace records. No installation or physical acceptance is claimed in this phase.
