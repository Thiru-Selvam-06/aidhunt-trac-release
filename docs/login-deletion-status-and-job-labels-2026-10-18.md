# Login warning, cloud deletion status and job labels — 2026-10-18

## Result
Client fixes implemented and standard debug APK built. **65 targeted tests passed**, zero failures/errors/skips. PMS cloud deletion remains **BLOCKED** by missing production trusted endpoints; physical-device retest remains **NOT VERIFIED**. Release gate remains **NOT READY**.

## Preserved baseline
Root: `C:\Users\user\.codex\worktrees\v16-implementation\aidhunt-trac`
Branch: `codex/comprehensive-regression-2026-10-17`
HEAD: `937088dff3115d4e995dbf6d1dfbed1158ccbe8d`
Pre-existing modifications and untracked files preserved. Original bytes of the eight edited files were captured before their edits. See `docs/test-artifacts/login-delete-status-20261018/` for before-images, a pass-only diff, final source hashes and complete final Git status. No staging, commit, reset, clean, stash, branch switch or push.

## 1. Login workspace warning
Source-confirmed cause: authentication observation, network recovery and OTP completion each initialize the workspace. The existing request fence correctly cancels stale requests, using "Workspace request superseded or authenticated identity changed". Broad login exception handling forwarded that cancellation to the error toast. That message does not establish an actual workspace suspension.

Changes:
- `WorkspaceRequestFence.kt`: a distinct internal cancellation type preserves the identity/epoch fence.
- New `WorkspaceRequestOutcome.kt`: converts only superseded internal requests to silent stale outcomes, propagates genuine coroutine cancellation and preserves real authorization failures.
- `MainViewModel.kt`: all eleven initialization calls and workspace switching use this outcome handling; eight initialization/switch failure callbacks do not display supersession as an error. Auth exception handlers now propagate real cancellation rather than mapping it to a login toast. Workspace discovery also propagates cancellation.

New deterministic tests demonstrate delayed old response versus new selection, account switching, real SecurityException delivery, actual coroutine cancellation and later reconnection after supersession. This is simulated coroutine evidence, not a live OTP/device login test. No account status or permission was overridden.

## 2. Cloud deletion pending
Actual source path: UI deletion -> ViewModel -> repository Room transaction/outbox -> automatic upload -> FirestoreRepository.applyPendingOperation -> WorkspaceCommandService -> FirebaseEnvironment.commandUrl -> trusted backend -> exact acknowledgement validation -> Room acknowledgement.

The repository deliberately queues deletion before cloud confirmation. The existing local-pending message is accurate at that point; removing it or claiming cloud success would be false.

Read-only backend discovery confirmed the connected Firebase environment is `trac-aidhunt` in this worktree. `functions_list_functions` returned **no deployed functions**. The standard app routes trusted deletion to `https://us-central1-trac-aidhunt.cloudfunctions.net/{operation}`. The staging Worker explicitly rejects PMS and other production workspaces.

A bounded existing Logcat read from the connected I2304, serial `10BE3C1T530006H`, process 20195, found ten UnknownHostException signatures among the latest 2000 lines. It did not capture the exact reported cancellation or a relevant deletion rejection. These signatures do not establish which endpoint/action failed. No raw payloads, tokens or personal log content were published. The user subsequently removed the device.

Changes:
- `WorkspaceCommandProtocol.kt`: absent HTTP route (404 without a structured backend error) produces a safe, actionable backend-unavailable error. A structured record-not-found result remains distinct. It never acknowledges deletion.
- `WorkspaceRepository.kt`: exposes that backend issue in sync results while retaining the original command as PENDING. Original IDs, timestamps, target/workspace and existing recovery/acknowledgement checks remain intact.
- Protocol regressions verify missing-route HTML/empty/JSON responses, sensitive-body suppression, no false acknowledgement and preservation of structured record errors. Existing mismatch, duplicate/recovery, authorization and routing regressions also passed.

**Unresolved infrastructure gate:** PMS cannot complete trusted cloud deletion until an authorized production backend exists and production client routing is deliberately aligned with it. No backend deployment or routing change was performed. The isolated staging Worker was not repurposed. Current Room/outbox counts, phone acknowledgement and real remote deletion were not inspected or tested. No existing pending or rejected action was manually retried.

## 3. Remove long job IDs from UI
`CollectionHistoryTab.kt`: replaces the Job #numeric-ID header with the work type (or localized Job label), and removes the raw linked Entry ID from the Collection Details dialog. Customer, job date, financial totals, collector/audit display and timeline controls remain. Stable workspace-scoped IDs, grouping keys, outbox payloads and internal test tags are unchanged. No new public record numbering was invented.

Updated `CollectionHistoryCardExpandableTest.kt` and `CollectionHistoryDetailsTest.kt` to assert the newly requested UI behavior. A new case uses ID 9007199254740995: it remains the stable expansion target while absent from visible text in collapsed/expanded history and the detail dialog. No Home job ID text control was found; IDs in internal keys and receipt/PDF machinery were preserved.

## Fresh validation
Command (JAVA_HOME = C:\Program Files\Java\jdk-21.0.10):
```text
.\gradlew.bat :app:testDebugUnitTest
 --tests com.example.WorkspaceRequestOutcomeTest
 --tests com.example.WorkspaceRequestFenceTest
 --tests com.example.WorkspaceCommandProtocolTest
 --tests com.example.TrustedCommandRoutingTest
 --tests com.example.CollectionHistoryCardExpandableTest
 --tests com.example.CollectionHistoryDetailsTest
 --tests com.example.CollectionHistoryGroupingTest
 --tests com.example.EntryDeletionReconciliationTest
 --tests com.example.CustomerDeletionDialogTest
 :app:assembleDebug --init-script scripts/ci-tests.gradle
 --offline --console=plain --max-workers=1 --no-daemon
 -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=512m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8"
```
Actual arguments were supplied as one invocation; JVM arguments were one quoted argument.
Exit 0; BUILD SUCCESSFUL in 3m25s. Nine suites: 5 + 5 + 19 + 7 + 5 + 1 + 8 + 13 + 2 = **65 tests**, zero failures/errors/skips. Eight Compose/Robolectric UI cases are virtual local tests, not phone evidence. Roborazzi finalizer task was skipped; no screenshots claimed.
`git diff --check`: exit 0.

Artifacts:
- `android-tests-build.log`
- `test-results.json` and `junit/` (actual JUnit XML)
- `verified-results.json` (APK and gate metadata)
- `backend-and-logcat-observations.json`
- `baseline-hashes.json`, `final-source-hashes.json`, `pass-only.patch`, `git-status-final.txt`

APK: `app/build/outputs/apk/debug/app-debug.apk`
Package: `com.aistudio.aidhunttrac.cedlir`; version 1.0.4, code 5.
SHA256: `0e7624466ae7fb231bb02c1f667503535ad08a11bb7ee7090ef874a9e419f795`.
Built only; **not installed**, following the user's instruction to reconnect later.

## Manual acceptance still needed
After in-place installation without clearing storage:
1. Sign in/select a valid workspace and reconnect: no superseded-request error; genuine denied workspace still denied.
2. Open Collection History, expand separate jobs and open payment details: work type/date/customer distinguish jobs, no raw numeric job ID, totals unchanged.
3. Inspect original pending commands and errors without pressing Retry on historical rejected actions.
4. Real PMS cloud deletion acceptance remains blocked until separately authorized production backend deployment/routing; no production deletion should be inferred from these local tests.

No production business records, memberships, authentication configuration, Rules, Worker deployment or billing were changed in this pass. No app navigation, app launch, data clear, uninstall, account switch or queue manipulation. Desktop checkout untouched.
