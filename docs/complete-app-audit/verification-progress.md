# Complete application verification — live evidence, 2026-10-12

This is an in-progress verification record, not a completion certificate. Critical workflows remain NOT VERIFIED. The interaction inventory is a source discovery index (850 bindings across 92 composables), not 850 completed device tests.

## Environment and preservation

Worktree: C:/Users/user/.codex/worktrees/v16-implementation/aidhunt-trac. Branch codex/v1.6-implementation; HEAD 0bf772b9dd3a7d7ebfaae7ebcd1e96ca6f1fe141. Initial status and 259 baseline file hashes are preserved privately in %TEMP%/aidhunt-complete-app-audit-20261012/source-baseline.json. Existing changes are uncommitted. No reset, clean, stash, branch switch, commit, push or deployment was performed.

Physical device 10BE3C1T530006H, Vivo I2304, Android 16. Isolated app com.aistudio.aidhunttrac.cedlir.devicetest, version 1.0.4-emulator/code 5, Room aidhunt_device_test_v18.db. Auth/Firestore/Functions route exclusively to demo-aidhunt-v16, loopback USB reverse 19099/18180/15001. Tests use disposable audit identities, not supplied production accounts. Production app/data/configuration are not mutated; package inspection of Android user 10 is denied, so installation preservation across every Android user is not independently certified.

Screenshots/XML, minimal fixture snapshots, action log and device logs are private under C:/Users/user/AppData/Local/Temp/aidhunt-complete-app-audit-20261012. Credentials/tokens are excluded from this report.

## Verified findings and fixes

| Workflow | Evidence / root cause | Change | Automated / SDK result | Actual UI result |
|---|---|---|---|---|
| Collection reversal | Client full-set omitted immutable authorizationTimeStatus; own reversal denied | Preserve stored provenance when updating Collection | Collection round-trip, balances, Customer ACK, duplicate delivery PASS on phone SDK | Earlier UI failed; newest UI retry pending |
| False Pending badge | Customer cache stayed unsynced after confirmed Collection; recovered terminal rejection blocked source ACK | Recalculate after ACK; dependency helper distinguishes recoverable terminal rejection; pending count uses actor outbox | Phone SDK round-trip PASS; focused ACK tests PASS | Home now has no false Pending badge; retained rejected action remains visible |
| Workspace selection | Profile refresh resolved default workspace and discarded verified selection | Prefer cached selection only for same verified UID, validate membership server-side | Owned/shared workspaces, refresh and restart PASS on phone SDK | Picker A Partner/B Owner PASS; newest profile-save UI retest pending |
| Account Entry draft | Old identity remained in draft across logout/account change | Reset draft on UID boundary, logout, workspace switch | Phone SDK PASS, preserved queue IDs checked | Fresh Operator form shows own UID; full manual Owner-to-Partner draft round-trip pending |
| Asset editor | Partner could tap another creator's Tractor card despite disabled Edit icon | Apply existing canMutateAsset gate to card | Four Compose cases PASS | Newest card-tap retest pending |
| Collection history | Existing Room/cloud ACTIVE 50 + 20 hidden by legacy joinedAt cutoff; screen showed reversed 20 only | Remove obsolete date restriction; require active same-workspace Partner identity | 22 focused tests PASS including existing ClientDeliveryRequirements; no broad permission grant | Before failure captured; updated app UI retest pending |
| Operator Cloud error | Operator attached financial Customer listener, correctly rejected by Rules | Omit Customer listener and its health requirement for Operator | New device listener regression added; rerun pending | Before PERMISSION_DENIED captured; updated app UI retest pending |
| Operator cash | Home used received 70 but omitted forbidden Expenses 5 and Withdrawal 10, showing 70 instead of 55 | Show business cash card only to existing financial roles; ordinary Entry totals retained | Three real Compose role cases PASS | Before discrepancy captured; updated app UI retest pending |
| Operator Add Tractor | Enabled button opened prohibited asset creation form | Apply existing asset-create capability to button | Five Tractor Compose cases PASS including disabled Add | Before form captured without saving; updated app UI retest pending |
| Security information | Dialog falsely claimed separately encrypted Room SQLite | Describe private app storage and membership-based access accurately | Compilation/device retest pending | Before dialog captured; unencrypted SQLite independently readable from isolated run-as snapshot |

## Actual device workflows performed in this resumed session

| Scenario | Role | Expected | Persisted result and UI | Status |
|---|---|---|---|---|
| Cold launch existing session | Partner A | Restore UID/workspace and correct financial source | 1 Entry/200 total/130 due/55 cash; retained rejection warned | PASS |
| Reports menu | Partner A | Six sections, no Historical creation | Six permitted report cards captured | PASS |
| Part-paid Entry deletion | Owner A | Block active collections and preserve balances | TRAC_ENTRY: Reverse all active Collections using Collection History, then retry Delete Due. Room/cloud Entry retained 200/70/130 | PASS persistence; error toast not captured reliably |
| Partner remove confirmation and Cancel | Owner A | Retain records in Stage 1; distinguish later purge | Explicit retain-records/revoke-access message; Cancel performed | PASS confirmation/cancel only; removal NOT VERIFIED in this session |
| Email logout/login | Owner -> Operator A | Correct role and clean self Entry draft | Operator session restored; own identity in form; queue not cleared | PASS basic UI; broader restart/retry matrix pending |
| Financial reports denial | Operator A | No reports | Tap remains Home; no financial report opened | PASS basic navigation |
| Existing Customer mismatch | Operator A | Deny new/unknown Customer | Missing recorded village rejected; no cloud Entry created | PASS rejection |
| Ordinary self Entry | Operator A | Existing customer, self only, durable synced Entry | Exact name/village succeeded; Entry 3757711774555513293 creator/target audit-operator, 1100 unpaid; emulator independently inspected | PASS |
| Own unpaid Entry delete <24h | Operator A | Hard-delete trusted workflow, financial restoration | Entry removed from emulator; Home returned 1 Entry/200/130; other financial records retained | PASS; ACK history reinspection pending |
| Receipt PDF | Owner A | Correct 200 billed/0 paid/200 due at export time | Actual device PDF copied, one-page text/render reviewed | PASS export/render; unrelated report PDFs NOT VERIFIED |

## Executed validations

- Established testDebugUnitTest full suite: 264 tests, zero failures/errors/skips before the latest history/Operator changes.
- New history focused testDebugUnitTest --tests com.example.CollectionVisibilityRegressionTest --tests com.example.ClientDeliveryRequirementsTest: 22 PASS.
- Operator focused testDebugUnitTest --tests com.example.HomeFinancialVisibilityTest --tests com.example.TractorCardAuthorizationTest --tests com.example.CollectionVisibilityRegressionTest: 10 PASS.
- Physical AndroidJUnitRunner WorkspaceSelectionDeviceTest: 3 PASS in 7.367 seconds (SDK integration, not tap-driven UI tests).
- New Operator listener SDK case: added, NOT YET RUN.
- Latest isolated app/test build: ongoing. Older builds succeeded; do not substitute them for this candidate.
- Earlier full deviceTestUnitTest variant failed 114/257 from Firebase initialization in its Robolectric setup. Established debug unit suite succeeds; the variant-specific failure remains explicit.
- Latest full Rules/server/migration regression and lint after this batch: NOT YET RUN. Earlier results are historical evidence only. No production security tests were run.

## Remaining scope

The complete action-inventory.json/csv and component-traceability.json remain the source inventory. Most individual controls and failure scenarios still require evidence. Priority remaining cases: newest app installation/guard verification, Operator listener/UI retest, Partner reversal UI and full balances, Owner unpaid/reversed/linked-Expense deletion, member removal and archive/purge, invitations/fresh phone onboarding, Historical creation, Expense/Withdrawal CRUD and target cases, filters/all PDF reports, preferences/profile/avatar/legal actions, offline force-stop/reconnect/rejected edits, revocation/wrong-UID queues, retry interruptions and cross-feature regression. No area is declared complete merely because it compiles or earlier narrow tests passed.

## Resumed verification update

Latest app SHA256 d0feee35e4aed12488ade4caf80f9b3270bb55ffec58c41a47bce0785545eaa6 installed successfully with -r (no clear/uninstall). Four WorkspaceSelectionDeviceTest phone SDK cases PASS in 8.699s. Operator Home, cloud status, disabled Tractor creation/card editing verified via actual taps. Partner Collection History now4 records/70 active; real50 reversal leaves other20 ACTIVE, Entry20/180, Homecash5, Customer Dues180. Removal verified REMOVED/ARCHIVED; retained5 Collection histories/Expense/Withdrawal. Removed Partner login selects only B as Owner. Explicit typed purge of disposable Partner completed PURGED, retained shared Customer and Owner Entry, restored source0/200. Owner subsequently deleted unpaid Entry: backend Entry absent, Customer0/0/0, Home0records/0totals. Physical-results.csv contains detailed evidence rows.

Latest testDebugUnitTest + lintDebug SUCCESS in5m29s:270 tests,0 failures/errors/skips. A combined backend run had incorrect environment guards for2 suites,6 skipped integration cases, and1 cancelled Rules fixture reset (189 reported tests,180passed,3failed,6skipped). Recorded in %TEMP%/aidhunt-audit-final-security-tests.log; corrected-environment rerun ongoing. No assertions/rules weakened. Exported emulator fixtures before reset to private emulator-after-device-workflows directory; test app stopped during reset suites.

Baseline preservation comparison:259 files,250 unchanged,9 intentionally edited during audit,0missing. Changed baseline files:OperationDependencies.kt,ClientDeliveryRequirementsTest.kt,MainActivity.kt,FirestoreRepository.kt,WorkspaceRepository.kt,AccountScreen.kt,HomeScreen.kt,CollectionHistoryTab.kt,MainViewModel.kt. New regression/helper/report files are additional. HEAD unchanged. Git status113entries at latest comparison.

Cloud Customer financial cache fields were stale between source mutations, while Home and Customer Dues independently derived correct balances from Entries; final trusted Entry deletion recomputed stored Customer to0/0/0. Cross-client cache contract and every report path still require explicit validation; do not claim all persisted aggregate fields were continuously synchronized.
## Onboarding findings (verification still active)

Actual local phone OTP succeeded but MainActivity disposed LoginScreen while AuthState.Loading had a Firebase UID; callback updated a discarded registration dialog. Added StartupAuthPresentation.kt and3 regression tests (PASS), retaining verified Authenticated + Ready gate for Home. An isolated PhoneRegistrationDeviceTest then reproduced forbidden phoneDirectory write:1 FAIL, PERMISSION_DENIED at Rules L113, in1.141s. It first asserted demo project and non-anonymous disposable verified phone, so backend selection was independently verified. Removed only the client directory write; trusted invitation services remain its writer. Strengthened new device regression to require a genuinely fresh verified fixture and inspect server Owner UID/membership plus absent private-directory client creation. Fixed candidate is not yet device-verified.

Logcat did not provide a startup marker for one process despite successful demo-project SDK assertion. Added PID/package/project metadata written only by DEVICE_TEST after emulator routing, and the temporary tester accepts only a matching current PID. No production fallback or Firebase permission was added. Latest full Android/lint validation for this onboarding batch is ongoing; do not substitute the prior270-test result.

Backend corrected-environment runs now252+22=274 PASS,0fail/skip;22 includes16 migration and6 server-security tests. Existing migration CLI inventory/dry-run/rerun verified5 fixture documents, unchanged revisions:SAFE_RETAIN2,OWNER_REVIEW1,MANUAL_MAPPING1,REFERENCE_RECONCILIATION1. No fabricated times or production apply. Exported device fixture state restored after security resets.

## Recovery continuation — phone-only signup policy and current device blocker

The latest user clarification permits new account creation through phone OTP only; existing Email/Google sign-in access remains. LoginScreen routes the Email signup shortcut and unregistered Google-account prompt to the existing phone OTP workflow. PhoneOnlySignupRoutingTest verifies both paths without invoking unsupported signup callbacks. No theme/navigation redesign or production authentication setting change was made.

Current full testDebugUnitTest XML results: 275 tests, 0 failures, 0 errors, 0 skipped. lintDebug is still running at this update. Exact command: `./gradlew.bat testDebugUnitTest lintDebug --no-daemon --no-configuration-cache --max-workers=1 "-Dorg.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=768m"`. Private log: `%TEMP%/aidhunt-resumed-phone-policy-validation.log`. Candidate build/install status will be appended after completion; the older installed APK does not establish verification of the newest routing change.

ADB `devices -l` currently returns an empty device list. Serial 10BE3C1T530006H is unavailable; read-only Logcat retrieval could not run. User has been asked to reconnect and authorize USB debugging. Fresh phone registration, current candidate installation, real UI security-copy retest, and the remaining comprehensive physical-device matrix are NOT VERIFIED in this continuation. The previous generic signup failure is unresolved; no speculative timestamp/Auth/Rules fix has been applied without its exact error.

The existing demo emulators remain running on localhost Auth19099/Firestore18180/Functions15001. An attempted second startup was rejected because the existing ports are occupied; it did not replace/reset the backend. A read-only probe against demo-aidhunt-v16, with no clearFirestore, seed, or document writes, passed four checks: own missing users document read, own empty membership-index list, denial of another UID's profile, denial of another UID's membership list. This is emulator authorization evidence only, not physical OTP verification or proof of the signup root cause. Private probe/results: `%TEMP%/aidhunt-fresh-onboarding-readonly.cjs` and `aidhunt-fresh-onboarding-readonly-results.log`.

Preservation recheck: baseline 259 files, 248 identical, 11 intentionally modified across the resumed audit, 0 missing. Branch codex/v1.6-implementation and HEAD 0bf772b9dd3a7d7ebfaae7ebcd1e96ca6f1fe141 remain unchanged. Exact SHA256/status comparison is in private `resumed-preservation-check.json`. No reset, stash, commit, push, deployment, production data mutation, app clear/uninstall, account switch, or production network enabling was performed during this continuation. Original Desktop checkout was not used for edits.

Remaining full-app results retain PASS/FAIL/NOT VERIFIED distinctions in physical-results.csv and the action/component inventory. The app is not declared fully functional. The single immediate device prerequisite is restoring ADB connectivity; then capture the exact fresh-phone FirebaseAuthService failure before modifying that path.

### Completed local gates for recovery continuation

`testDebugUnitTest lintDebug` completed successfully in 4m26s:275 tests,0failures/errors/skips. Lint reports0errors and94warnings (warnings remain explicit; successful gate is not a warning-free claim). The read-only emulator probe passed4 checks without changing device fixtures. The existing broad274-test backend result remains earlier evidence and was not rerun/reset during this continuation.

The latest isolated app/test APK build is running through the existing `assembleDeviceTest assembleDeviceTestAndroidTest -PdeviceTest=true` tasks, same bounded Gradle options. Source configuration confirms project `demo-aidhunt-v16`, package `com.aistudio.aidhunttrac.cedlir.devicetest`. Until the build finishes and a phone reconnects, neither a new installation nor newest-candidate physical tests are claimed.

Inventory limitation identified: component-traceability has source-regex candidates, including an import falsely labelled a gesture and callbacks grouped under nested local functions. These are discovery candidates, not a reliable exhaustive reachable-control matrix. Manual control/parent-component tracing remains required. Existing detailed physical evidence is retained; no candidate is promoted to PASS from source presence.

### Latest candidate built; physical continuation blocked

Both `assembleDeviceTest` and `assembleDeviceTestAndroidTest` completed successfully in3m22s,71tasks(8executed/63up-to-date). Build log: `%TEMP%/aidhunt-resumed-phone-policy-device-build.log`. APK: `app/build/outputs/apk/deviceTest/app-deviceTest.apk`; aapt confirms application ID `com.aistudio.aidhunttrac.cedlir.devicetest`, version1.0.4-emulator/code5, label AIDHUNT Trac Emulator. SHA256:8e7f17ef8328e2a6b5d9057992362396ea0e313a0530c47ee639652fe383c80b. Config project is demo-aidhunt-v16. This candidate has not been installed in this continuation because final `adb devices -l` remains empty. Device signing compatibility and runtime routing must be reconfirmed upon connection before installing alongside production.

Latest recorded Git status118entries, same branch/HEAD, baseline259files preserved (248identical/11intentional changes/0missing). Report-only updates additionally recorded this continuation; no application/rules edit was guessed to address the unresolved OTP error. The latest signup routing code is locally validated, but fresh-account creation and the comprehensive remaining physical matrix are NOT VERIFIED. Restore USB/ADB visibility first, then inspect the exact auth failure and continue isolated UI/backend tests. Nothing committed, pushed, deployed, migrated live, or written to production; no production app state cleared or network enabled.

## Test wrap-up — 2026-10-13

User requested wrapping up testing. Further exploration and repairs are paused at this boundary; this is an evidence summary, not a declaration that the app is complete or release-ready.

### Latest installed isolated candidate

Physical Vivo I2304, Android16/API36, serial10BE3C1T530006H. App com.aistudio.aidhunttrac.cedlir.devicetest, label AIDHUNT Trac Emulator, version1.0.4-emulator/code5. Final SHA256:a40a14212768ba84113b5ccd8c29877baa4353b753b5f5d26476238820d62276. Final app and runner installed successfully with adb install -r, preserving data. Demo project demo-aidhunt-v16, USB reverse Auth19099/Firestore18180/Functions15001. Final physical SDK role/read-isolation smoke:1 PASS4.945s. This final smoke covers reads; latest phone guard's full fresh-signup UI path was not repeated after this final packaging.

Final app build command: `./gradlew.bat assembleDeviceTest assembleDeviceTestAndroidTest -PdeviceTest=true --no-daemon --no-configuration-cache --max-workers=1 "-Dorg.gradle.jvmargs=-Xmx2048m -XX:MaxMetaspaceSize=768m"`; SUCCESS1m57s. Private log:%TEMP%/aidhunt-wrapup-final-device-build.log.

### Repairs and demonstrated findings

- WorkspaceRepository.initialWorkspaceSettings now takes the verified workspace business name and the genuine Owner profile name/phone, rather than incorrectly using the Owner's name as business name and leaving ownerName empty. Before: actual fresh signup required a second setup dialog. After: native registration1 PASS2.013s, actual fixed-phone-first-home/account-profile show distinct names with no duplicate setup. InitialWorkspaceSettingsTest adds3 regressions; existing rows are retained.
- Phone-only signup routing and accurate registration copy remain in LoginScreen; existing Email/Google sign-in callbacks were retained. Fresh OTP and actual form submission were exercised, server Owner UID and membership independently checked, and cold restart succeeded. The earlier isolated generic OTP error was not reproduced; its original exact root cause remains unproven.
- Phone bootstrap regression reproduced two unauthorized non-phone workspace creates before correction (5cases:3pass/2fail). firestore.rules now requires the nonempty phone_number claim signed by Firebase Auth for new workspace creation. This is not a client form timestamp/phone trust decision. Existing Owner/member permissions and sign-in access are unchanged. MainViewModel rejects anonymous/missing-phone account creation before writes; FirebaseAuthService gives an explicit phone-OTP-only signup response. After:5/5emulator policy regressions PASS, including existing Owner access without a phone claim on each sign-in. No production Rules deployment occurred.
- DeviceEmulatorIntegrationTest now logs each role-read stage and applies bounded20s awaits without weakening permission assertions. Initial full run9/10 with one role/isolation timeout in315.573s; fresh targeted retry1 PASS1.677s; traced complete run10 PASS9.494s. The initial intermittent failure is retained in aidhunt-recovery-ten-device-tests.log; no proven application root cause is claimed from the successful retry.

### Final automated validation

- Android: `testDebugUnitTest lintDebug` with the same bounded Gradle options:SUCCESS4m33s;278tests,0failures/errors/skips. Lint succeeded; warnings remain in app/build/reports/lint-results-debug.html. Private log:aidhunt-wrapup-android-validation.log.
- Combined backend command: `node --test --test-concurrency=1 security-tests/architecture.test.cjs security-tests/listener-queries.test.cjs security-tests/phone-onboarding.test.cjs functions/entry-deletion-policy.test.js functions/entry-deletion-http.test.js functions/entry-deletion-emulator.test.js functions/invitation-policy.test.js`:257tests,256pass/1fail/0skip. Failure was before the Owner-entry assertion, in clearFirestore fixture setup:HTTP499/CANCELLED, call already cancelled. Private log:aidhunt-wrapup-security-validation.log. It is not reported as a clean initial run.
- Rerun affected Rules/listener/phone files through existing framework:171PASS,0fail/skip,21.744s. Private log:aidhunt-wrapup-rules-retry.log. Remaining86deletion/invitation cases in the combined run passed; not rerun unnecessarily. All279distinct backend cases across these gates and the separate22 below have passing evidence after the retry, but they were not one single clean invocation.
- `node --test --test-concurrency=1 functions/security.integration.test.js functions/migration/legacy-timestamps.test.js`:22PASS,0fail/skip,21.747s (including16migration cases). Private log:aidhunt-wrapup-server-migration.log. All backend invocations used explicit demo emulator hosts; production apply was not executed.
- Device SDK integration covers role reads, workspace isolation, hour18/h26 retry/rejection, stable ID/time preservation, old protocol rejection, Owner delete preconditions/reversed history, member revocation, logout queue retention, UID changes, cached offline startup/reconnect and deletion acknowledgement. These are physical SDK tests, not evidence that every UI control or every failure scenario was tapped.

### Physical UI evidence and unresolved financial issue

physical-results.csv contains the updated case-level evidence. Physical UI verified signup, restart, corrected security copy, Owner tractor/extension creation and selection, historical date selection/review/save, Home1all-time/0today/1800billed/due, Expense report UI100/1record, and explicit zero-cash withdrawal rejection. A server snapshot independently confirmed historical Entry3757728268199702843 and resources. Expense UI success is not promoted to complete backend verification because the emulator stopped before an independent post-Expense snapshot was captured.

FAIL remains open: consistent stopped-app Room snapshot recovery-room-1791867079555.db shows Entry50 paid958123/due0, while emulator document shows paid0/due958123. The Home displayed local cash based on that divergent state. Relevant collection requests include terminal REJECTED journal entries marked Confirmed state restored, and local Entry rows still isSynced=false. syncRemoteJobsToLocal currently preserves every such unsynced row before considering whether its mutation is genuinely pending. Old static device fixtures and preserved outbox histories were reused, so a fresh uniquely scoped fixture is needed to separate fixture-replacement effects from normal production synchronization. No balance was forcibly zeroed, no payload discarded, and no unverified fix was shipped for this discrepancy. Financial reconciliation is a release blocker.

NOT VERIFIED includes the remaining comprehensive action matrix, full revocation UI recovery without an alternate workspace, every filter/dialog/report PDF, Google credential-manager sign-in, all role-specific mutation screens and all intermittent/large-queue scenarios. The source-regex inventory remains provisional and contains grouping/import false positives; it is not an exhaustive verified-control matrix. No completion percentage is assigned.

### Preservation and final environment

Branch codex/v1.6-implementation; HEAD0bf772b9dd3a7d7ebfaae7ebcd1e96ca6f1fe141 unchanged. Baseline259files:245identical,14intentional changes,0missing. Git status119entries before this report-only update; exact full status and per-file changes saved privately in final-preservation-check.json. All pre-existing worktree changes retained; original Desktop checkout untouched. New/changed items in the resumed batch include WorkspaceRepository.kt, MainViewModel.kt, FirebaseAuthService.kt, LoginScreen.kt, firestore.rules, InitialWorkspaceSettingsTest.kt, DeviceEmulatorIntegrationTest.kt, security-tests/phone-onboarding.test.cjs and evidence reports; earlier v1.6 changes remain intact.

Pre-final-gate Auth/Firestore fixtures exported successfully to private emulator-before-final-gates and restored after tests. Current demo server uses this snapshot and exports on exit to emulator-final-preserved. Local forensic Room/cloud snapshots and rejected-operation payloads are preserved. Test app left stopped after final read-only smoke. Final radio check:Wifi is disabled; mobile_data0; temporary screen-awake setting restored0; production package had no running PID. Networking had been manually enabled between interruptions and was restored to the required offline state; no claim is made about external activity outside this verification session.

No production Firebase writes, live migration, Rules/Functions deployment, app-data clear/uninstall, commit, push, reset, clean, stash or branch switch was performed. Existing production installation and its pending work were not modified by this testing. Result:local test wrap-up completed; application not approved for release because financial consistency and remaining critical workflows are unresolved/unverified.
