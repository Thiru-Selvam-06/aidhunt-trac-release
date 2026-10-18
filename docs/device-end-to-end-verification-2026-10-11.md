# AIDHUNT Trac v1.6 — End-to-end repair and verification, 2026-10-11–12

This is evidence for the tested paths, not a claim that the entire application is complete. All business mutations were confined to the demo Emulator project. SDK/repository tests executed on the physical phone are distinguished below from actual UI taps.

## Final verified outcome — 2026-10-12

**The locally repaired paths passed final validation; the entire application is not declared complete or production-ready.**

- Physical phone integration: **10 passed**, zero failures, 9.079 seconds (`aidhunt-e2e-device-recovery-final.log`). Includes role/isolation reads, original offline action/retry/rejection preservation, trusted deletion financial prerequisites, member revocation/history, logout/account switch, cached-startup listener recovery, and confirmed-source/balance recovery with stale Customer and Entry snapshots.
- Exact final Android source: **253 passed**, zero failures/errors/skips; lint **0 errors, 94 warnings**; build successful in 5m18s (`aidhunt-e2e-recovery-android-final.log`). Final isolated APK/instrumentation build succeeded in 4m50s. GitHub CI and release compilation were not executed.
- Final actual UI deletion: Owner deleted disposable Entry **76** for Customer **176**. After server response and local ACK, Entry was absent; Room and emulator totalBilled/totalPaid/balanceDue were all **0**, Room isSynced=true, operation `434de8e7-9809-4430-a21c-ce2e2da049ef` ACKNOWLEDGED with original actionTime `1791794240381`. Force-stop/relaunch preserved those results. Evidence: `customer-final-after.json`, `customer-final-ui.png`, private Room snapshot. This supersedes the explicitly failed intermediate balance attempts below.
- Reopened Home matched independent Room aggregation: **12** all-time records, **₹962,323** recorded, **₹962,183** due; today's preview showed **5**, explicitly labelled today. Additional disposable records from successive regressions explain the different count from the initial seven-record fixture.
- Rules/server suites: **258 passed**, zero failures/skips; migration suite: **16 passed**, zero failures/skips. They were run on local emulators, not production; source for those suites remained unchanged after their passing run, except local fixture diagnostics.
- Git branch/HEAD/origin unchanged; 244 initial files checked, **221 unchanged / 23 intentional incremental edits / 0 missing**; final Git status has **105** entries including pre-existing changes, new isolated setup/tests/report, and untouched generated crash artifacts. Exact final status is appended below. `git diff --check` passed.
- **External blocker:** the earlier production package is absent from the phone's current package list. Our commands did not uninstall or clear it. Its current pending-data preservation cannot be verified. Earlier private baseline snapshots/comparisons remain available; no recovery or production reinstallation was attempted. Networking remains disabled.
- Remaining NOT VERIFIED workflows include the complete GUI role matrix, reports/PDF parity, permanent purge/recovery, large-queue performance, automatic reconnect convergence without a manual retry, and full expired-session/concurrent-revocation scenarios. Nothing committed, pushed, deployed or migrated in production.

## Environment and preservation

- Worktree: `C:\Users\user\.codex\worktrees\v16-implementation\aidhunt-trac`.
- Branch: `codex/v1.6-implementation`; baseline and final HEAD: `0bf772b9dd3a7d7ebfaae7ebcd1e96ca6f1fe141`.
- Device: `10BE3C1T530006H`, Vivo I2304, Android 16/API 36.
- Test app: `com.aistudio.aidhunttrac.cedlir.devicetest`, 1.0.4-emulator/versionCode 5; installed alongside, not over, the existing app.
- Test backend: `demo-aidhunt-v16`; Auth 127.0.0.1:19099, Firestore 127.0.0.1:18180, Functions 127.0.0.1:15001, USB reverse routing. Owner/Partner/Operator disposable identities only.
- Separate Room database: `aidhunt_device_test_v18.db`, additionally isolated by Android package sandbox. Initialization checks the demo project and package suffix before enabling emulators; the test network security configuration excludes external TLS trust and permits loopback endpoints only. Broken emulator routes cannot fall back to production.
- Initial recorded Git status contained 90 entries. Later status contains 104 entries before this report. These include extensive pre-existing implementation changes and generated JVM crash logs; they are not 104 changes newly made for this task.
- Of 244 initially hashed files, 221 are unchanged, 23 have intentional incremental changes, and none are missing. Detailed lists and exact before/after status are recorded below and in private evidence.
- No reset, clean, stash, branch switch, commit, push, deployment, production migration, or intentional production business write was performed. The original Desktop checkout was not edited.
- Production app remained stopped during test mutations. External activity twice enabled phone networking; it was disabled again when discovered. We cannot claim preservation relative to an older previous-run snapshot: that older database differed before this test baseline. Relative to the restored baseline captured in this run, all 14 tables and all six pending operations compare identically. Final Wi-Fi/mobile data are both 0; production app has no running PID.

## Three reported failures and repairs

### Home amount with zero records

The amount was an all-time total, while the count came from today's limited preview. A large older Entry therefore produced a nonzero all-time amount beside zero today records. HomeRecordSummary now calculates amount, due and count from the same authorized workspace Room rows, including the intended Historical Entries. The preview is explicitly labelled today's entries. Positive-value fallbacks were removed so a legitimate zero is not replaced by another scope's amount.

Actual phone UI and Room agreed on seven all-time Entries, ₹958,823 recorded and ₹958,683 due, with zero today Entries. A disposable ₹700 Entry changed this to eight/₹959,523/₹959,383, and deletion restored the previous summary. This establishes scope consistency for the fixture; it does not establish the provenance of the production ₹9 lakh or independently reconcile every report.

### Owner Entry/Delete Due

The UI previously showed deletion success immediately, before repository authorization/result. It now reports the actual result; offline local removal is described as pending cloud confirmation. Paid/active-Collection and linked-Expense prerequisites remain enforced rather than being bypassed.

A real UI deletion of unpaid Entry 70 removed it from Room and the emulator, with retained Collection history. Actual partial-paid Entry 40 UI deletion showed the actionable reversal requirement and preserved the Entry/balances without adding a deletion command. Physical repository tests also denied active partial/full/split Collections and a separately protected Expense, and deleted the already-reversed Entry while retaining REVERSED history.

A second real UI deletion, Entry 72, was performed with all three USB backend routes removed. After force-stop/relaunch the Owner session remained usable, the Entry stayed removed locally, and its DELETE operation remained PENDING. Restoring routes and retrying through Shared Cloud Sync acknowledged the same operation ID `445f6dd7-0afa-4604-b9c6-ccc9d1e80af5` and original actionTime `1791732722389`; the server Entry was absent. Immediate automatic reconnection alone was not proven to finish this deletion; the tested recovery included the explicit sync button.

Customer acknowledgement previously treated every retained rejected payload, including another actor's terminal rejection, as pending financial work. It now distinguishes terminal rejection from PENDING and unfinished local reconciliation. The shared fixture still contains an older genuinely PENDING Owner payment, so its Customer isSynced remains false. A separate clean-customer retest exposed an additional stale local-balance issue, documented in the final updates. The server's canonical Customer due field is balanceDue, not the fixture's extraneous pendingDue field.

### Partner removal

MainViewModel.deletePartner invoked completion even after an exception, and the screen displayed success before server confirmation. Success callbacks now run only after archive/revocation completes; failures are surfaced. Confirmation explains that records are retained and permanent deletion is separate. Duplicate archive handling checks that membership is actually REMOVED instead of accepting an archive alone as success. Account member count now uses active canonical workspace memberships rather than the unrelated legacy Partner list.

Actual Owner UI removed the selected Device PARTNER: emulator membership became REMOVED, deletedUsers state ARCHIVED, and the attributed Entry remained. Physical integration verified duplicate removal, retained records, non-Owner denial and removed-member denial using a fresh authenticated Firestore instance. No permanent purge was executed.

## Additional reproduced defects repaired

- Real logout crashed because it signed out and then attempted an authorized shared-workspace settings write. Removed that write; logout is per account and retains the queue. Actual UI reached login afterwards; physical regression verified retained operations.
- A previous account's profile remained visible while a new uncached UID was being verified. The auth transition now clears the previous profile immediately; physical test passed with Firestore disabled.
- Durable rejected actions could coexist with a misleading Synced badge. Home/Account now show Need review and explain that original payloads are retained. Actual UI confirmed the warning. Detailed emulator rule messages are still verbose; further presentation refinement is outstanding.
- The Owner person picker used Locked for a saved default without being locked. Renamed the preference to Default/Set default while preserving permission and layout behavior.
- Invitation server requests were incorrectly required to carry Delete Customer protocol fields, while the Delete Customer endpoint lacked that check. Moved the validator to its intended endpoint; authentication, Owner authorization, invitation OTP and role checks remain intact. Legacy Customer deletion now receives UPGRADE_REQUIRED.
- Three existing architecture test fixtures lacked protocol v2. Updated only their payload version; ownership, membership, timing and denial assertions were retained.

## Verification matrix

| Workflow | Role / fixture | Expected and observed result | Evidence level / status |
|---|---|---|---|
| Emulator package/project/database isolation | All / demo | Separate package/db and guarded loopback backend | Physical installation/configuration: PASS |
| Persisted offline Owner cold start | Owner / Entry 72 | Auth/workspace restored; cached Home usable after force-stop | Actual UI: PASS |
| Home all-time totals vs today's count | Owner/Partner / older Entries | Same Room set for totals/count; explicitly zero today | Actual UI + private Room snapshot: PASS |
| Unpaid Delete Due | Owner / 70,72 | Hard-delete source; retain financial history | Actual UI + Room + emulator: PASS |
| Partial paid deletion | Owner / 40 | Block with reversal instruction; no false success | Actual UI + retained state: PASS |
| Full paid and split Collection blocking | Owner /43,44 | Block without modifying financial records | Physical SDK/repository: PASS; actual UI NOT VERIFIED |
| Already reversed Entry deletion | Owner /41 | Entry removed; reversed Collection retained | Physical SDK/repository: PASS; actual UI NOT VERIFIED |
| Independently protected linked Expense | Owner /42 | Entry remains; linked Expense not silently deleted | Physical SDK/repository: PASS; actual UI NOT VERIFIED |
| Duplicate deletion delivery | Owner /20 | Repeated command idempotent | Physical repository + server tests: PASS |
| Interrupted deletion/restart/retry | Owner /72 | Original command/time retained; ACK after retry | Actual UI + Room/emulator: PASS; automatic eventual completion alone NOT VERIFIED |
| Post-ACK financial and stale-snapshot recovery | Owner /76,176 and unique SDK fixtures | Source absent, zero local/server totals, synced after restart; old snapshots cannot resurrect it | Final actual UI + Room/emulator + physical SDK: PASS |
| Member KEEP RECORDS | Owner /Device PARTNER | Correct UID archived, access revoked, Entry retained | Actual UI + emulator: PASS |
| Repeated removal and unauthorized removal | Owner/Partner /device-removal | Idempotent Owner removal; Partner denied | Physical SDK/repository: PASS |
| Revoked membership read | Removed member /device-removal | Denied from fresh client | Physical SDK: PASS |
| Deleted Users navigation / permanent purge | Owner | Separate permanent confirmation/prerequisites | Actual navigation/confirmation inspected and cancelled; destructive UI/server purge NOT VERIFIED |
| Extensions listener permissions | Owner/Partner/Operator | Authorized member reads; wrong workspace denied | Physical SDK + Rules tests: PASS |
| Collections listener permissions | Owner/Partner/Operator | Owner/Partner read; Operator denied | Physical SDK + Rules tests: PASS |
| Home/Reports/New Entry/Account navigation | Persisted Partner, Owner | Cached screens open; self-only Entry form | Actual UI: PASS for inspected screens; every subpage NOT VERIFIED |
| Ordinary Entry create/sync | Owner /20 | Current protocol creates and persists Entry | Physical repository: PASS; actual create form submission NOT VERIFIED |
| Hour-0/18/26/30 offline policy | Partner /10 | 18h action accepted at 30h; 26h rejected; confirmed 18h state retained | Physical repository + Room/Rules: PASS; real-time 30-hour UI test NOT VERIFIED |
| Rejected payload / UPGRADE_REQUIRED | Partner /legacy | Permanent rejection retained, upgrade response surfaced | Physical repository + server tests: PASS; outdated installed-app UI NOT VERIFIED |
| Logout | Owner | No post-signout business mutation; queue retained | Actual UI + physical integration: PASS |
| Wrong account queued operation | Owner vs Partner | Cannot process original actor's operation | Physical repository: PASS |
| Offline new-account profile transition | Owner→uncached removed UID | Previous Owner profile cleared while verification unavailable | Physical integration: PASS |
| Collection creation/reversal totals | Financial fixtures | Existing authorization/allocation regressions preserved | Automated suites: PASS; complete fresh GUI financial scenario NOT VERIFIED |
| Expenses/Withdrawals/resource CRUD | Role fixtures | Locked permissions and workspace boundaries | Automated relevant suites: PASS; full actual UI CRUD NOT VERIFIED |
| Reports/PDF/export reconciliation | All | Same verified records and amounts | NOT VERIFIED end to end |
| Permanent purge/large queues/migration recovery | Disposable fixtures | Preserve references and history; safe retries | Selected automated tests only; full physical workflow NOT VERIFIED |
| Migration inventory/dry-run | Local demo fixture | No writes/fabricated timestamps | CLI + automated local tests: PASS; production apply not executed |
| Expired Auth/session and concurrent membership change mid-write | All | No unauthorized upload | Partial automated coverage; complete physical scenarios NOT VERIFIED |

## Commands and results

All Firebase commands used local emulator environment variables; none targeted production.

1. `node --test --test-concurrency=1 security-tests/architecture.test.cjs security-tests/listener-queries.test.cjs functions/entry-deletion-policy.test.js functions/entry-deletion-emulator.test.js functions/entry-deletion-http.test.js functions/invitation-policy.test.js functions/security.integration.test.js`: 258 passed, zero failed/skipped; 94.929 seconds. Actual emulator Rules compilation and server cases executed. Earlier two invitation regressions were repaired before this final run.
2. `node --test --test-concurrency=1 functions/migration/legacy-timestamps.test.js`: 16 passed, zero failed/skipped; 34.575 seconds. Local inventory and dry-run CLI also completed; 16 records classified SAFE_RETAIN 3, OWNER_REVIEW 8, MANUAL_MAPPING 1, REFERENCE_RECONCILIATION 4. No production apply.
3. `gradlew.bat assembleDeviceTest assembleDeviceTestAndroidTest -PdeviceTest=true --no-daemon --no-configuration-cache`: final isolated build successful, 3m39s. Installed both isolated APKs using `adb -s 10BE3C1T530006H install -r`; production package not installed/uninstalled.
4. `adb -s 10BE3C1T530006H shell am instrument -w -e class com.example.DeviceEmulatorIntegrationTest com.aistudio.aidhunttrac.cedlir.devicetest.test/androidx.test.runner.AndroidJUnitRunner`: eight passed, 7.353 seconds. These are physical SDK/repository tests, not simulated UI taps.
5. Earlier focused debug tests: 80 passed across eight classes. Full serial debug suite subsequently ran 253 tests with three protocol-fixture failures; assertions were preserved and fixtures corrected. Final retry result is appended below.
6. Initial overlapping Gradle checks failed due to native JVM memory exhaustion (`hs_err_pid8564.log`); serial retry uses `--max-workers=1 -Dorg.gradle.jvmargs="-Xmx2048m -XX:MaxMetaspaceSize=768m"`. This was a host validation failure, not an app crash. Crash artifacts remain untouched.
7. `git diff --check`: passed. `git status --short`, `git branch --show-current`, `git rev-parse HEAD` and initial-file SHA256 comparison recorded state without changing Git.

## Evidence

Private evidence directory: `C:\Users\user\AppData\Local\Temp\aidhunt-emulator-device-evidence`.

Screenshots: `home-owner.png` actually shows the Partner session despite its older filename; `entry-partner.png`, `member-removal-confirmation.png`, `owner-delete-after.png`, `partial-delete-denied.png`, `account-final.png`. Room snapshots and manifests remain private, outside Git. `offline-72-before.json` and `offline-72-after.json` show preserved operation/time and acknowledgement; `production-preservation-final.json` establishes identical restored-baseline tables; `source-preservation-final.json` identifies intentional edits/no missing files.

Logs in TEMP: `aidhunt-e2e-security-final.log`, `aidhunt-e2e-migration.log`, `aidhunt-e2e-device-final.log`, `aidhunt-e2e-auth-safety-build.log`, `aidhunt-e2e-serial-validation-final.log`. Raw Emulator logs may contain disposable phone verification codes and must not be published. No real credentials, tokens, private database contents or customer payloads are included here.

## Remaining limitations

The three repaired workflows have the specific evidence listed above. Full application acceptance is still outstanding: complete UI role matrix, PDF/report parity, automatic reconnect convergence without a manual retry, permanent purge/recovery, broader financial GUI scenarios, large queue performance and expired-session behavior. Existing pending test payloads remain recoverable. Production rollout still requires separate review/approval, backup/migration planning where applicable, and deployed Functions/Rules compatibility verification. No completion percentage is assigned.

## Files changed incrementally in this run

- `app/src/main/java/com/example/data/firebase/CloudListenerHealth.kt`
- `app/src/main/java/com/example/data/firebase/WorkspaceCommandService.kt`
- `app/src/main/java/com/example/data/firebase/WorkspaceInvitationService.kt`
- `app/src/main/java/com/example/data/sync/OperationDependencies.kt`
- `app/src/test/java/com/example/CloudListenerHealthTest.kt`
- `app/src/test/java/com/example/EntryDeletionReconciliationTest.kt`
- `app/src/test/java/com/example/V16ArchitectureContractTest.kt`
- `functions/index.js`
- `functions/security.integration.test.js`
- `app/build.gradle.kts`
- `app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt`
- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/main/java/com/example/TracApplication.kt`
- `app/src/main/java/com/example/data/database/AppDatabase.kt`
- `app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt`
- `app/src/main/java/com/example/data/firebase/FirestoreRepository.kt`
- `app/src/main/java/com/example/data/network/NetworkMonitor.kt`
- `app/src/main/java/com/example/data/repository/WorkspaceRepository.kt`
- `app/src/main/java/com/example/ui/components/CommonComponents.kt`
- `app/src/main/java/com/example/ui/screens/account/AccountScreen.kt`
- `app/src/main/java/com/example/ui/screens/entry/NewEntryScreen.kt`
- `app/src/main/java/com/example/ui/screens/home/HomeScreen.kt`
- `app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt`

New emulator setup, instrumentation, Home summary/test and this report are additional untracked artifacts; existing untracked implementation files were preserved. Full Git diff against HEAD also includes prior work and must not be attributed to this run.

## Exact initial Git status

```text
 M .github/workflows/android-ci.yml
 M .gitignore
 M app/src/main/java/com/example/MainActivity.kt
 M app/src/main/java/com/example/data/auth/AuthorizationManager.kt
 M app/src/main/java/com/example/data/auth/RoleUtils.kt
 M app/src/main/java/com/example/data/dao/Daos.kt
 M app/src/main/java/com/example/data/database/AppDatabase.kt
 M app/src/main/java/com/example/data/entity/Entities.kt
 M app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt
 M app/src/main/java/com/example/data/firebase/FirestoreModels.kt
 M app/src/main/java/com/example/data/firebase/FirestoreRepository.kt
 D app/src/main/java/com/example/data/repository/TractorRepository.kt
 M app/src/main/java/com/example/data/repository/WorkspaceRepository.kt
 M app/src/main/java/com/example/data/sync/PendingDeleteManager.kt
 M app/src/main/java/com/example/pdf/PdfGeneratorHelper.kt
 M app/src/main/java/com/example/ui/components/CommonComponents.kt
 M app/src/main/java/com/example/ui/screens/account/AccountScreen.kt
 M app/src/main/java/com/example/ui/screens/entry/NewEntryScreen.kt
 M app/src/main/java/com/example/ui/screens/home/HomeScreen.kt
 M app/src/main/java/com/example/ui/screens/report/BusinessOverviewScreen.kt
 M app/src/main/java/com/example/ui/screens/report/CollectionHistoryTab.kt
 M app/src/main/java/com/example/ui/screens/report/CustomerCreditDueTab.kt
 M app/src/main/java/com/example/ui/screens/report/ExpensesTab.kt
 M app/src/main/java/com/example/ui/screens/report/ReportScreen.kt
 M app/src/main/java/com/example/ui/screens/report/WithdrawalTab.kt
 M app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt
 M app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt
 M app/src/main/java/com/example/ui/viewmodel/NewEntryDraft.kt
 M app/src/main/res/xml/file_paths.xml
 M app/src/test/java/com/example/CompleteRegressionPipelineTest.kt
 M app/src/test/java/com/example/ExampleRobolectricTest.kt
 M app/src/test/java/com/example/ExampleUnitTest.kt
 M app/src/test/java/com/example/PartnerEarningsChartTest.kt
 M app/src/test/java/com/example/PartnerWithdrawalsProfitShareTest.kt
 M app/src/test/java/com/example/PaymentReversalAndCollectionHistoryTest.kt
 M firebase.json
 M firestore.rules
?? app/src/main/java/com/example/data/auth/ArchivedRecordAuthorization.kt
?? app/src/main/java/com/example/data/auth/EntryFinancialMutationPolicy.kt
?? app/src/main/java/com/example/data/auth/PdfExportAuthorization.kt
?? app/src/main/java/com/example/data/auth/PendingCleanupAuthorization.kt
?? app/src/main/java/com/example/data/dao/DeletedMemberDao.kt
?? app/src/main/java/com/example/data/dao/PendingOperationDao.kt
?? app/src/main/java/com/example/data/entity/DeletedMemberEntity.kt
?? app/src/main/java/com/example/data/entity/PendingOperationEntity.kt
?? app/src/main/java/com/example/data/firebase/CloudListenerHealth.kt
?? app/src/main/java/com/example/data/firebase/FirestoreBackupCodec.kt
?? app/src/main/java/com/example/data/firebase/WorkspaceCommandProtocol.kt
?? app/src/main/java/com/example/data/firebase/WorkspaceCommandService.kt
?? app/src/main/java/com/example/data/firebase/WorkspaceInvitationService.kt
?? app/src/main/java/com/example/data/repository/SyncResult.kt
?? app/src/main/java/com/example/data/sync/EntryDeletionPolicy.kt
?? app/src/main/java/com/example/data/sync/MemberPurgePlan.kt
?? app/src/main/java/com/example/data/sync/OfflineEntryRecovery.kt
?? app/src/main/java/com/example/data/sync/OperationDependencies.kt
?? app/src/main/java/com/example/data/sync/OperationPolicy.kt
?? app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt
?? app/src/main/java/com/example/ui/screens/report/MemberFinancialData.kt
?? app/src/main/java/com/example/ui/util/ContactPhoneValidation.kt
?? app/src/main/java/com/example/ui/util/EarningsBarSegments.kt
?? app/src/main/java/com/example/ui/util/RecentJobFilter.kt
?? app/src/main/java/com/example/ui/util/WithdrawalDistribution.kt
?? app/src/test/java/com/example/ArchitectureIntegrationTest.kt
?? app/src/test/java/com/example/ArchivedRecordAuthorizationTest.kt
?? app/src/test/java/com/example/ClientDeliveryRequirementsTest.kt
?? app/src/test/java/com/example/CloudListenerHealthTest.kt
?? app/src/test/java/com/example/EntryCreationTimestampPersistenceTest.kt
?? app/src/test/java/com/example/EntryDeletionReconciliationTest.kt
?? app/src/test/java/com/example/EntryFinancialMutationPolicyTest.kt
?? app/src/test/java/com/example/EntryMutationAuthorizationTest.kt
?? app/src/test/java/com/example/FinalTargetedClientFixesTest.kt
?? app/src/test/java/com/example/FinalTwoClientFixesTest.kt
?? app/src/test/java/com/example/OfflineActionTimeTest.kt
?? app/src/test/java/com/example/PdfAuthorizationTest.kt
?? app/src/test/java/com/example/PendingInvitationCleanupContractTest.kt
?? app/src/test/java/com/example/RoomScopeTest.kt
?? app/src/test/java/com/example/V16ArchitectureContractTest.kt
?? app/src/test/java/com/example/WithdrawalLedgerNavigationTest.kt
?? app/src/test/java/com/example/WorkspaceCommandProtocolTest.kt
?? app/src/test/resources/
?? docs/delete-due-reconciliation.md
?? docs/firestore-authorization-rollout.md
?? docs/legacy-migration-and-protocol-v2.md
?? docs/offline-action-time-addendum-v1.0-2026-10-11.md
?? docs/offline-action-time-validation-2026-10-11.md
?? docs/v16-implementation-status.md
?? functions/
?? scripts/validate-architecture.ps1
?? scripts/validate-architecture.py
?? security-tests/

```

## Exact final Git status

```text
 M .github/workflows/android-ci.yml
 M .gitignore
 M app/build.gradle.kts
 M app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt
 M app/src/main/java/com/example/MainActivity.kt
 M app/src/main/java/com/example/TracApplication.kt
 M app/src/main/java/com/example/data/auth/AuthorizationManager.kt
 M app/src/main/java/com/example/data/auth/RoleUtils.kt
 M app/src/main/java/com/example/data/dao/Daos.kt
 M app/src/main/java/com/example/data/database/AppDatabase.kt
 M app/src/main/java/com/example/data/entity/Entities.kt
 M app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt
 M app/src/main/java/com/example/data/firebase/FirestoreModels.kt
 M app/src/main/java/com/example/data/firebase/FirestoreRepository.kt
 M app/src/main/java/com/example/data/network/NetworkMonitor.kt
 D app/src/main/java/com/example/data/repository/TractorRepository.kt
 M app/src/main/java/com/example/data/repository/WorkspaceRepository.kt
 M app/src/main/java/com/example/data/sync/PendingDeleteManager.kt
 M app/src/main/java/com/example/pdf/PdfGeneratorHelper.kt
 M app/src/main/java/com/example/ui/components/CommonComponents.kt
 M app/src/main/java/com/example/ui/screens/account/AccountScreen.kt
 M app/src/main/java/com/example/ui/screens/entry/NewEntryScreen.kt
 M app/src/main/java/com/example/ui/screens/home/HomeScreen.kt
 M app/src/main/java/com/example/ui/screens/report/BusinessOverviewScreen.kt
 M app/src/main/java/com/example/ui/screens/report/CollectionHistoryTab.kt
 M app/src/main/java/com/example/ui/screens/report/CustomerCreditDueTab.kt
 M app/src/main/java/com/example/ui/screens/report/ExpensesTab.kt
 M app/src/main/java/com/example/ui/screens/report/ReportScreen.kt
 M app/src/main/java/com/example/ui/screens/report/WithdrawalTab.kt
 M app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt
 M app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt
 M app/src/main/java/com/example/ui/viewmodel/NewEntryDraft.kt
 M app/src/main/res/xml/file_paths.xml
 M app/src/test/java/com/example/CompleteRegressionPipelineTest.kt
 M app/src/test/java/com/example/ExampleRobolectricTest.kt
 M app/src/test/java/com/example/ExampleUnitTest.kt
 M app/src/test/java/com/example/PartnerEarningsChartTest.kt
 M app/src/test/java/com/example/PartnerWithdrawalsProfitShareTest.kt
 M app/src/test/java/com/example/PaymentReversalAndCollectionHistoryTest.kt
 M firebase.json
 M firestore.rules
?? app/src/androidTest/java/com/example/DeviceEmulatorIntegrationTest.kt
?? app/src/deviceTest/
?? app/src/main/java/com/example/data/auth/ArchivedRecordAuthorization.kt
?? app/src/main/java/com/example/data/auth/EntryFinancialMutationPolicy.kt
?? app/src/main/java/com/example/data/auth/PdfExportAuthorization.kt
?? app/src/main/java/com/example/data/auth/PendingCleanupAuthorization.kt
?? app/src/main/java/com/example/data/dao/DeletedMemberDao.kt
?? app/src/main/java/com/example/data/dao/PendingOperationDao.kt
?? app/src/main/java/com/example/data/entity/DeletedMemberEntity.kt
?? app/src/main/java/com/example/data/entity/PendingOperationEntity.kt
?? app/src/main/java/com/example/data/firebase/CloudListenerHealth.kt
?? app/src/main/java/com/example/data/firebase/FirebaseEnvironment.kt
?? app/src/main/java/com/example/data/firebase/FirestoreBackupCodec.kt
?? app/src/main/java/com/example/data/firebase/WorkspaceCommandProtocol.kt
?? app/src/main/java/com/example/data/firebase/WorkspaceCommandService.kt
?? app/src/main/java/com/example/data/firebase/WorkspaceInvitationService.kt
?? app/src/main/java/com/example/data/repository/SyncResult.kt
?? app/src/main/java/com/example/data/sync/EntryDeletionPolicy.kt
?? app/src/main/java/com/example/data/sync/MemberPurgePlan.kt
?? app/src/main/java/com/example/data/sync/OfflineEntryRecovery.kt
?? app/src/main/java/com/example/data/sync/OperationDependencies.kt
?? app/src/main/java/com/example/data/sync/OperationPolicy.kt
?? app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt
?? app/src/main/java/com/example/ui/screens/report/MemberFinancialData.kt
?? app/src/main/java/com/example/ui/util/ContactPhoneValidation.kt
?? app/src/main/java/com/example/ui/util/EarningsBarSegments.kt
?? app/src/main/java/com/example/ui/util/HomeRecordSummary.kt
?? app/src/main/java/com/example/ui/util/RecentJobFilter.kt
?? app/src/main/java/com/example/ui/util/WithdrawalDistribution.kt
?? app/src/test/java/com/example/ArchitectureIntegrationTest.kt
?? app/src/test/java/com/example/ArchivedRecordAuthorizationTest.kt
?? app/src/test/java/com/example/ClientDeliveryRequirementsTest.kt
?? app/src/test/java/com/example/CloudListenerHealthTest.kt
?? app/src/test/java/com/example/EntryCreationTimestampPersistenceTest.kt
?? app/src/test/java/com/example/EntryDeletionReconciliationTest.kt
?? app/src/test/java/com/example/EntryFinancialMutationPolicyTest.kt
?? app/src/test/java/com/example/EntryMutationAuthorizationTest.kt
?? app/src/test/java/com/example/FinalTargetedClientFixesTest.kt
?? app/src/test/java/com/example/FinalTwoClientFixesTest.kt
?? app/src/test/java/com/example/HomeRecordSummaryTest.kt
?? app/src/test/java/com/example/OfflineActionTimeTest.kt
?? app/src/test/java/com/example/PdfAuthorizationTest.kt
?? app/src/test/java/com/example/PendingInvitationCleanupContractTest.kt
?? app/src/test/java/com/example/RoomScopeTest.kt
?? app/src/test/java/com/example/V16ArchitectureContractTest.kt
?? app/src/test/java/com/example/WithdrawalLedgerNavigationTest.kt
?? app/src/test/java/com/example/WorkspaceCommandProtocolTest.kt
?? app/src/test/resources/
?? docs/delete-due-reconciliation.md
?? docs/device-end-to-end-verification-2026-10-11.md
?? docs/firestore-authorization-rollout.md
?? docs/legacy-migration-and-protocol-v2.md
?? docs/offline-action-time-addendum-v1.0-2026-10-11.md
?? docs/offline-action-time-validation-2026-10-11.md
?? docs/v16-implementation-status.md
?? firebase.device-test.json
?? functions/
?? hs_err_pid8236.log
?? hs_err_pid8564.log
?? replay_pid8236.log
?? replay_pid8564.log
?? scripts/validate-architecture.ps1
?? scripts/validate-architecture.py
?? security-tests/
```


## Further validation updates

- Corrected full debug suite: **253 passed, zero failures/errors/skips**. Lint completed with **0 errors, 94 warnings**, build successful in 5m (`aidhunt-e2e-serial-validation-final.log`). This run preceded the final reconnect-listener repair, which is being revalidated separately.
- Actual Owner Deleted Users UI and permanent-deletion confirmation were inspected. Confirmation requires the literal PERMANENTLY DELETE, describes irreversible financial/history removal and preserved shared Customers/other workspaces. The dialog was cancelled; no purge executed. Evidence: `purge-confirmation-only.png`.
- New device-reproduced issue: after an offline cold start, cached membership made Room available, but successful synchronization did not attach the listeners omitted during startup. A newly seeded emulator Entry remained invisible. The reconnect path now restores listeners only after authoritative role/membership and unchanged actor/workspace checks. A physical regression test requires a newly persisted server Entry to reach Room following this recovery; final results are appended when completed.

## Resumed validation — 2026-10-12

The session interruption stopped the local emulators and ADB server. Networking was enabled externally again, and was restored to disabled before testing resumed. The isolated app was updated in place; no production APK was installed, replaced or uninstalled by our commands.

**New external preservation blocker:** the phone's package inventory now lists only `com.aistudio.aidhunttrac.cedlir.devicetest` and its test package. The earlier `com.aistudio.aidhunttrac.cedlir` package is absent; `run-as` reports `packagelist_parse failed`. The final fresh production-data comparison cannot run. The earlier successful 14-table/six-pending-operation comparison is historical evidence only, not proof of the current phone state. No attempt was made to reinstall, clear, recover or overwrite that app. The original private baseline snapshot and earlier successful comparison remain available; a failed new snapshot must not be used for recovery. The disappearance occurred outside this task's package operations.

- Final reconnect build: successful, 3m41s.
- Focused physical reconnect regression: one passed, 3.702s.
- The first nine-case run stalled and was stopped only in the isolated app; not counted as a pass. Fresh reseeded retry: **nine passed**, 8.281s.
- Post-reconnect Android validation: **253 passed, zero failures/errors/skips**; lint **0 errors, 94 warnings**; successful, 5m12s.
- New actual-UI clean-customer deletion test: Entry 73 was hard-deleted, its operation ACKNOWLEDGED, and emulator Customer 172 had totalBilled/totalPaid/balanceDue all zero. Room initially retained totalBilled=700 and balanceDue=700 while isSynced=true. This is a genuine cross-layer defect, not a passing balance result. A pre-delete customer snapshot can leave stale local aggregate values while deletion is in flight. The acknowledgement now recalculates from retained local Entries inside the existing transaction before marking Customer synced, provided no genuinely pending related operation remains. A new physical regression tests this precise state; its final result is recorded below when completed.

### Listener-race follow-up

The first acknowledgement-only repair did **not** resolve the actual UI balance mismatch: Entry 74 again reached ACKNOWLEDGED/server-deleted, while Room Customer 174 still showed ₹700 and the server showed zero. Do not count that attempt as a pass. Evidence is retained in `stale-customer-before.json` and the first `stale-customer-after.json`.

The Customer listener previously captured unsynced IDs and job rows outside a Room transaction and inserted calculated values later. A pre-delete snapshot could overwrite the transaction's reconciliation, and the financial engine intentionally retained remote legacy totals when no source jobs remained. The final repair makes snapshot merge atomic with deletion/acknowledgement and uses confirmed source-deletion history to clear that specific empty-source fallback. Unrelated no-job legacy balances are preserved. Authenticated actor/workspace are rechecked inside the transaction; malformed cross-workspace snapshots are rejected rather than reassigned. The new regression also injects the stale snapshot after ACK and requires it not to restore the deleted source balance.

An intermediate ten-case device run had eight passes/two failures: one malformed Customer setup bypassed the normal operation envelope (corrected to use the protocol-v2 repository), and the other used a deleted fixture after its local seeder failed. These results remain recorded as failed; the subsequent fixture preparation succeeded. Rules and business assertions were not weakened.

The acknowledgement-only Android suite completed successfully (253 tests; lint 0 errors/94 warnings; 5m33s), but it did not cover the later listener-race source change. The first listener-race build failed because of a missing Flow extension; corrected before final validation. These intermediate checks are not substitutes for final candidate validation.

The real UI Entry 75 retest initially showed zero while PENDING, but a later ACK snapshot again showed Room ₹700 against server zero. That intermediate result is **FAIL**, not a successful repair. Tracing every Customer writer exposed the Entry listener as a second nontransactional aggregate writer. The final fix makes both Entry and Customer merges atomic with deletion/acknowledgement, and rejects delayed snapshots of the confirmed deleted Entry version (same source ID and original createdAt); a different newly created source version is not suppressed. The regression now delivers both stale Customer and stale Entry snapshots after ACK and verifies absent source/zero balances. Final results follow.

The logout instrumentation fixture also now clears its manually constructed ViewModel via ViewModelStore, cancelling test-only background collectors that leaked into subsequent offline scenarios. Combined runs that stalled were aborted only in the isolated package and are not counted as passes.

The first completed atomic-merge phone run had **nine passes/one failure**. The financial regression showed that a stale source could still be retained from interrupted legacy local state when acknowledgement recalculated balances. Acknowledgement now removes only the confirmed deleted source version, and skips that cleanup if a newer PENDING UPSERT for the same source exists. The regression deliberately starts with retained source/customer rows and verifies trusted-server ACK repairs them before replaying both delayed snapshot types. This intermediate failure remains recorded; final candidate results follow.

## Final status after validation

```text
 M .github/workflows/android-ci.yml
 M .gitignore
 M app/build.gradle.kts
 M app/src/androidTest/java/com/example/ExampleInstrumentedTest.kt
 M app/src/main/java/com/example/MainActivity.kt
 M app/src/main/java/com/example/TracApplication.kt
 M app/src/main/java/com/example/data/auth/AuthorizationManager.kt
 M app/src/main/java/com/example/data/auth/RoleUtils.kt
 M app/src/main/java/com/example/data/dao/Daos.kt
 M app/src/main/java/com/example/data/database/AppDatabase.kt
 M app/src/main/java/com/example/data/entity/Entities.kt
 M app/src/main/java/com/example/data/firebase/FirebaseAuthService.kt
 M app/src/main/java/com/example/data/firebase/FirestoreModels.kt
 M app/src/main/java/com/example/data/firebase/FirestoreRepository.kt
 M app/src/main/java/com/example/data/network/NetworkMonitor.kt
 D app/src/main/java/com/example/data/repository/TractorRepository.kt
 M app/src/main/java/com/example/data/repository/WorkspaceRepository.kt
 M app/src/main/java/com/example/data/sync/PendingDeleteManager.kt
 M app/src/main/java/com/example/pdf/PdfGeneratorHelper.kt
 M app/src/main/java/com/example/ui/components/CommonComponents.kt
 M app/src/main/java/com/example/ui/screens/account/AccountScreen.kt
 M app/src/main/java/com/example/ui/screens/entry/NewEntryScreen.kt
 M app/src/main/java/com/example/ui/screens/home/HomeScreen.kt
 M app/src/main/java/com/example/ui/screens/report/BusinessOverviewScreen.kt
 M app/src/main/java/com/example/ui/screens/report/CollectionHistoryTab.kt
 M app/src/main/java/com/example/ui/screens/report/CustomerCreditDueTab.kt
 M app/src/main/java/com/example/ui/screens/report/ExpensesTab.kt
 M app/src/main/java/com/example/ui/screens/report/ReportScreen.kt
 M app/src/main/java/com/example/ui/screens/report/WithdrawalTab.kt
 M app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt
 M app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt
 M app/src/main/java/com/example/ui/viewmodel/NewEntryDraft.kt
 M app/src/main/res/xml/file_paths.xml
 M app/src/test/java/com/example/CompleteRegressionPipelineTest.kt
 M app/src/test/java/com/example/ExampleRobolectricTest.kt
 M app/src/test/java/com/example/ExampleUnitTest.kt
 M app/src/test/java/com/example/PartnerEarningsChartTest.kt
 M app/src/test/java/com/example/PartnerWithdrawalsProfitShareTest.kt
 M app/src/test/java/com/example/PaymentReversalAndCollectionHistoryTest.kt
 M firebase.json
 M firestore.rules
?? app/src/androidTest/java/com/example/DeviceEmulatorIntegrationTest.kt
?? app/src/deviceTest/
?? app/src/main/java/com/example/data/auth/ArchivedRecordAuthorization.kt
?? app/src/main/java/com/example/data/auth/EntryFinancialMutationPolicy.kt
?? app/src/main/java/com/example/data/auth/PdfExportAuthorization.kt
?? app/src/main/java/com/example/data/auth/PendingCleanupAuthorization.kt
?? app/src/main/java/com/example/data/dao/DeletedMemberDao.kt
?? app/src/main/java/com/example/data/dao/PendingOperationDao.kt
?? app/src/main/java/com/example/data/entity/DeletedMemberEntity.kt
?? app/src/main/java/com/example/data/entity/PendingOperationEntity.kt
?? app/src/main/java/com/example/data/firebase/CloudListenerHealth.kt
?? app/src/main/java/com/example/data/firebase/FirebaseEnvironment.kt
?? app/src/main/java/com/example/data/firebase/FirestoreBackupCodec.kt
?? app/src/main/java/com/example/data/firebase/WorkspaceCommandProtocol.kt
?? app/src/main/java/com/example/data/firebase/WorkspaceCommandService.kt
?? app/src/main/java/com/example/data/firebase/WorkspaceInvitationService.kt
?? app/src/main/java/com/example/data/repository/SyncResult.kt
?? app/src/main/java/com/example/data/sync/EntryDeletionPolicy.kt
?? app/src/main/java/com/example/data/sync/MemberPurgePlan.kt
?? app/src/main/java/com/example/data/sync/OfflineEntryRecovery.kt
?? app/src/main/java/com/example/data/sync/OperationDependencies.kt
?? app/src/main/java/com/example/data/sync/OperationPolicy.kt
?? app/src/main/java/com/example/ui/screens/account/DeletedUsersPage.kt
?? app/src/main/java/com/example/ui/screens/report/MemberFinancialData.kt
?? app/src/main/java/com/example/ui/util/ContactPhoneValidation.kt
?? app/src/main/java/com/example/ui/util/EarningsBarSegments.kt
?? app/src/main/java/com/example/ui/util/HomeRecordSummary.kt
?? app/src/main/java/com/example/ui/util/RecentJobFilter.kt
?? app/src/main/java/com/example/ui/util/WithdrawalDistribution.kt
?? app/src/test/java/com/example/ArchitectureIntegrationTest.kt
?? app/src/test/java/com/example/ArchivedRecordAuthorizationTest.kt
?? app/src/test/java/com/example/ClientDeliveryRequirementsTest.kt
?? app/src/test/java/com/example/CloudListenerHealthTest.kt
?? app/src/test/java/com/example/EntryCreationTimestampPersistenceTest.kt
?? app/src/test/java/com/example/EntryDeletionReconciliationTest.kt
?? app/src/test/java/com/example/EntryFinancialMutationPolicyTest.kt
?? app/src/test/java/com/example/EntryMutationAuthorizationTest.kt
?? app/src/test/java/com/example/FinalTargetedClientFixesTest.kt
?? app/src/test/java/com/example/FinalTwoClientFixesTest.kt
?? app/src/test/java/com/example/HomeRecordSummaryTest.kt
?? app/src/test/java/com/example/OfflineActionTimeTest.kt
?? app/src/test/java/com/example/PdfAuthorizationTest.kt
?? app/src/test/java/com/example/PendingInvitationCleanupContractTest.kt
?? app/src/test/java/com/example/RoomScopeTest.kt
?? app/src/test/java/com/example/V16ArchitectureContractTest.kt
?? app/src/test/java/com/example/WithdrawalLedgerNavigationTest.kt
?? app/src/test/java/com/example/WorkspaceCommandProtocolTest.kt
?? app/src/test/resources/
?? docs/delete-due-reconciliation.md
?? docs/device-end-to-end-verification-2026-10-11.md
?? docs/firestore-authorization-rollout.md
?? docs/legacy-migration-and-protocol-v2.md
?? docs/offline-action-time-addendum-v1.0-2026-10-11.md
?? docs/offline-action-time-validation-2026-10-11.md
?? docs/v16-implementation-status.md
?? firebase.device-test.json
?? functions/
?? hs_err_pid8236.log
?? hs_err_pid8564.log
?? replay_pid8236.log
?? replay_pid8564.log
?? scripts/validate-architecture.ps1
?? scripts/validate-architecture.py
?? security-tests/
```
