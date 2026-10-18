# AIDHUNT Trac release verification — 2026-10-15

Status: NOT RELEASE READY. Authenticated live staging mutations and physical workflows A–D remain NOT VERIFIED.

## Staging deployment — verified

- Target: `aidhunt-trac-trusted-staging`, account `70d29191fa1fbf347c9d64b9b6691947`.
- URL: https://aidhunt-trac-trusted-staging.aidhuntinformatics.workers.dev
- Previous active revision independently retrieved: `351fab9c-132f-4155-a0f3-108d71a2b656`.
- New active revision: `335666df-d851-4bf0-8564-1ff253ec9a2d`, 100% staging traffic.
- Deployment: `aa8fe7c9-1bb7-4203-bd2a-da854cbbb09f`.
- Verified bindings: Firebase project `trac-aidhunt`; workspace `ws_cf_staging_beb841c2f4d9475a`; inherited encrypted `GOOGLE_SERVICE_ACCOUNT_JSON`. No secret read or re-upload.
- `cf deploy --dry-run` succeeded. CLI itself was unauthenticated, so the authenticated Cloudflare MCP version-upload/deployment API published the generated bundle. No production routing changed.
- Health returned HTTP 200, correct project and credentialConfigured=true. This confirms secret presence, not successful privileged Google API use.
- All four endpoints returned HTTP 401/UNAUTHENTICATED for both missing and malformed Bearer tokens: eight non-mutating live checks passed.
- Candidate supports atomic eligible Entry/linked Expense/sole ACTIVE Collection deletion, shared-allocation protection, audit receipt, exact int64 identifiers, customer-total recalculation, authenticated role/workspace checks and duplicate command identity. Local tests verify these contracts; live authenticated persistence remains unverified.

| Operation | Local regression | Live missing/invalid auth | Live authenticated mutation |
|---|---|---|---|
| beginWorkspaceInvitation | PASS | PASS | NOT VERIFIED |
| redeemWorkspaceInvitation | PASS | PASS | NOT VERIFIED |
| deleteWorkspaceCustomer | PASS | PASS | NOT VERIFIED |
| deleteWorkspaceEntry | PASS | PASS | NOT VERIFIED |

## Test results

1. `node --test cloudflare/google-auth.test.mjs cloudflare/firestore-rest.test.mjs cloudflare/operations.test.mjs functions/invitation-policy.test.js functions/entry-deletion-policy.test.js`: 35 passed, 0 failed/skipped.
2. `node --test --test-concurrency=1 security-tests/architecture.test.cjs security-tests/listener-queries.test.cjs`: 166 passed, 0 failed/skipped. Includes 19 listener cases. Previous connection refusal was caused by the required local service being stopped; no source or production configuration change was necessary.
3. Local service: existing cached Firestore Emulator 1.22.0, `--host 127.0.0.1 --port 18180 --project_id demo-aidhunt-v16 --single_project_mode true --single_project_mode_error true --rules firestore.rules`. Only disposable demo fixtures were cleared/written.
4. With `FIRESTORE_EMULATOR_HOST=127.0.0.1:18180` and `GCLOUD_PROJECT=demo-aidhunt-v16`, `node --test --test-concurrency=1 functions/security.integration.test.js`: 6 passed, 0 failed/skipped.
5. `gradlew.bat :app:testDebugUnitTest --offline --console=plain`: BUILD SUCCESSFUL; tasks UP-TO-DATE. Existing current XML results: 313 tests, 42 suites, 0 failures/errors/skips. These were not a newly executed 313-test run this turn.
6. Previously completed current APK build: `gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assemblePhysicalTest --offline --console=plain`, BUILD SUCCESSFUL. No Android source changed during this verification turn.
7. `git diff --check`: passed (line-ending notices only).

## Physical installation and preserved queue

- ADB: serial `10BE3C1T530006H`, model I2304, online.
- Updated only `com.aistudio.aidhunttrac.cedlir.physicaltest`, versionName `1.0.4-physical-test`, versionCode 5.
- Existing and candidate APK signing SHA-256 matched: `ae954fdb33bb43c6781a6e91f615ea8c7861e4a8c5d65a5c0ea878c693db20a5`.
- `adb install -r app/build/outputs/apk/physicalTest/app-physicalTest.apk`: Success. No uninstall, storage clearing, account switch or production package update.
- APK built 2026-10-15 15:37:52 IST; isolated database `aidhunt_physical_test_v18.db`.
- Before/after: 76 queue rows: 63 ACKNOWLEDGED, 13 INVALIDATED, zero pending/rejected. Full ordered-row SHA-256 both times: `3b2eab16a547db63a4776a79d5dd41f8eae9d34d8aeaad531a56b9b451eb3426`.
- Private snapshots (not committed, not included in report payloads): `%TEMP%/aidhunt-release-verify-fsxxaip_`, before and after. Original operation IDs, action times and payloads compared unchanged.
- App was left stopped after update. Per user's manual-testing instruction, no UI taps or business mutations were performed. No new workflow Logcat evidence exists yet.

| Manual scenario | Physical-device status |
|---|---|
| A: unpaid Entry + linked Expense deletion; sync and restart | NOT VERIFIED |
| B: paid Entry + linked Expense + sole Collection deletion; shared allocation blocked | NOT VERIFIED |
| C: Owner pays for Partner job; Owner payer/investment and Partner beneficiary | NOT VERIFIED |
| D: cash vs UPI, business-paid Expense, Partner withdrawal once, negative cash | NOT VERIFIED |

Expected manual results: A removes parent and linked eligible Expense permanently after confirmed sync; B removes solely allocated ACTIVE payment atomically, protects shared allocations and reconciles dues/cash; C attributes actual payment to Owner and job benefit to Partner; D only CASH Collections add physical cash, BUSINESS PAID Expenses/withdrawals subtract once, personally funded Expenses do not reduce business cash, negative cash remains visible.

## Account access blocker

Read-only membership checks confirmed accounts ending 7361/0605/5171 have active Owner/Partner/Partner roles in their existing business workspace. None has membership in the restricted staging workspace. That staging workspace has a different Owner UID. Their denial is the staging isolation guard, not evidence that ordinary phone login or production roles are broken.

Approval requested, not yet received: create a NEW disposable staging workspace with 7361 as Owner and 0605/5171 as Partners, preserve existing memberships, and update only staging Worker/test-app workspace bindings. Do not make a second Owner in the existing staging workspace or relabel existing business records. A new test APK would be needed if the staging workspace constant changes; preserve all existing queue rows during that update.

Phone OTP login for these three accounts, their role-specific live staging operations, Room acknowledgement/recovery under interruption, and resulting live Firestore state are NOT VERIFIED in this turn. No credentials/tokens are included here.

## Repository safety and changes

- Worktree: `C:/Users/user/.codex/worktrees/v16-implementation/aidhunt-trac`.
- Branch unchanged: `codex/v1.6-implementation`.
- HEAD unchanged: `0bf772b9dd3a7d7ebfaae7ebcd1e96ca6f1fe141`.
- Before this report: 42 modified tracked files, 1 pre-existing tracked deletion, 132 individual untracked files. Earlier short status grouped untracked directories into fewer rows; these are not comparable file counts.
- No application/rules/test/config source changes were made this turn. Generated ignored staging build output was rebuilt; this report is the only new tracked-source-tree artifact.
- Earlier local payer/cash/deletion corrections remain intact. Original Desktop checkout was untouched this turn.
- No Firebase Rules deployment, production data mutation, membership write, billing change, commit or push.

Exact pre-report status follows (all pre-existing paths retained):

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
 M app/src/main/java/com/example/ui/screens/auth/LoginScreen.kt
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
 M app/src/test/java/com/example/NewEntryExpenseAndTakingCategoriesTest.kt
 M app/src/test/java/com/example/PartnerEarningsChartTest.kt
 M app/src/test/java/com/example/PartnerWithdrawalsProfitShareTest.kt
 M app/src/test/java/com/example/PaymentReversalAndCollectionHistoryTest.kt
 M firebase.json
 M firestore.rules
?? app/src/androidTest/java/com/example/DeviceEmulatorIntegrationTest.kt
?? app/src/androidTest/java/com/example/PhoneRegistrationDeviceTest.kt
?? app/src/androidTest/java/com/example/PurgeProtocolDeviceTest.kt
?? app/src/androidTest/java/com/example/WorkspaceSelectionDeviceTest.kt
?? app/src/deviceTest/AndroidManifest.xml
?? app/src/deviceTest/google-services.json
?? app/src/deviceTest/res/values/strings.xml
?? app/src/deviceTest/res/xml/device_test_network.xml
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
?? app/src/main/java/com/example/data/firebase/PurgeReconciliationProtocol.kt
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
?? app/src/main/java/com/example/ui/util/CloudStatusAction.kt
?? app/src/main/java/com/example/ui/util/ContactPhoneValidation.kt
?? app/src/main/java/com/example/ui/util/EarningsBarSegments.kt
?? app/src/main/java/com/example/ui/util/HomeRecordSummary.kt
?? app/src/main/java/com/example/ui/util/RecentJobFilter.kt
?? app/src/main/java/com/example/ui/util/RetainedOperationWarning.kt
?? app/src/main/java/com/example/ui/util/StartupAuthPresentation.kt
?? app/src/main/java/com/example/ui/util/WithdrawalDistribution.kt
?? app/src/physicalTest/google-services.json
?? app/src/test/java/com/example/ArchitectureIntegrationTest.kt
?? app/src/test/java/com/example/ArchivedRecordAuthorizationTest.kt
?? app/src/test/java/com/example/CashBalanceRegressionTest.kt
?? app/src/test/java/com/example/ClientDeliveryRequirementsTest.kt
?? app/src/test/java/com/example/CloudListenerHealthTest.kt
?? app/src/test/java/com/example/CollectionHistoryDetailsTest.kt
?? app/src/test/java/com/example/CollectionVisibilityRegressionTest.kt
?? app/src/test/java/com/example/CustomerDeletionDialogTest.kt
?? app/src/test/java/com/example/EntryAcknowledgementDependenciesTest.kt
?? app/src/test/java/com/example/EntryCreationTimestampPersistenceTest.kt
?? app/src/test/java/com/example/EntryDeletionReconciliationTest.kt
?? app/src/test/java/com/example/EntryFinancialMutationPolicyTest.kt
?? app/src/test/java/com/example/EntryMutationAuthorizationTest.kt
?? app/src/test/java/com/example/ExtensionDetailsNavigationTest.kt
?? app/src/test/java/com/example/FinalTargetedClientFixesTest.kt
?? app/src/test/java/com/example/FinalTwoClientFixesTest.kt
?? app/src/test/java/com/example/HomeFinancialVisibilityTest.kt
?? app/src/test/java/com/example/HomeRecordSummaryTest.kt
?? app/src/test/java/com/example/InitialWorkspaceSettingsTest.kt
?? app/src/test/java/com/example/MemberDetailsNavigationTest.kt
?? app/src/test/java/com/example/OfflineActionTimeTest.kt
?? app/src/test/java/com/example/PdfAuthorizationTest.kt
?? app/src/test/java/com/example/PendingInvitationCleanupContractTest.kt
?? app/src/test/java/com/example/PhoneOnlySignupRoutingTest.kt
?? app/src/test/java/com/example/PurgeReconciliationProtocolTest.kt
?? app/src/test/java/com/example/RetainedOperationWarningTest.kt
?? app/src/test/java/com/example/RoomScopeTest.kt
?? app/src/test/java/com/example/StartupAuthPresentationTest.kt
?? app/src/test/java/com/example/TractorCardAuthorizationTest.kt
?? app/src/test/java/com/example/TrustedCommandRoutingTest.kt
?? app/src/test/java/com/example/V16ArchitectureContractTest.kt
?? app/src/test/java/com/example/WithdrawalLedgerNavigationTest.kt
?? app/src/test/java/com/example/WorkspaceCommandProtocolTest.kt
?? app/src/test/resources/local-v17-schema.sql
?? app/src/test/resources/release-v14-schema.sql
?? cloudflare/.gitignore
?? cloudflare/cloudflare.config.ts
?? cloudflare/firestore-rest.mjs
?? cloudflare/firestore-rest.test.mjs
?? cloudflare/google-auth.mjs
?? cloudflare/google-auth.test.mjs
?? cloudflare/operations.mjs
?? cloudflare/operations.test.mjs
?? cloudflare/package-lock.json
?? cloudflare/package.json
?? cloudflare/vite.config.ts
?? cloudflare/worker.mjs
?? docs/complete-app-audit/2026-10-13-deployed-rules-backup.rules
?? docs/complete-app-audit/action-inventory.csv
?? docs/complete-app-audit/action-inventory.json
?? docs/complete-app-audit/component-traceability.json
?? docs/complete-app-audit/firebase-rules-rollout-2026-10-13.md
?? docs/complete-app-audit/phase15-live-retry-2026-10-13.md
?? docs/complete-app-audit/physical-results.csv
?? docs/complete-app-audit/verification-progress.md
?? docs/delete-due-reconciliation.md
?? docs/device-end-to-end-verification-2026-10-11.md
?? docs/firestore-authorization-rollout.md
?? docs/legacy-migration-and-protocol-v2.md
?? docs/offline-action-time-addendum-v1.0-2026-10-11.md
?? docs/offline-action-time-validation-2026-10-11.md
?? docs/v16-implementation-status.md
?? firebase.device-test.json
?? functions/device-test-fixtures.cjs
?? functions/entry-deletion-emulator.test.js
?? functions/entry-deletion-http.test.js
?? functions/entry-deletion-policy.js
?? functions/entry-deletion-policy.test.js
?? functions/index.js
?? functions/inspect-workspace.js
?? functions/invitation-policy.js
?? functions/invitation-policy.test.js
?? functions/migration/legacy-timestamps.js
?? functions/migration/legacy-timestamps.test.js
?? functions/operation-protocol.js
?? functions/package-lock.json
?? functions/package.json
?? functions/security.integration.test.js
?? functions/verify-workspace-backup.js
?? hs_err_pid8236.log
?? hs_err_pid8564.log
?? replay_pid8236.log
?? replay_pid8564.log
?? scripts/validate-architecture.ps1
?? scripts/validate-architecture.py
?? security-tests/architecture.test.cjs
?? security-tests/firebase.json
?? security-tests/listener-queries.test.cjs
?? security-tests/package-lock.json
?? security-tests/package.json
?? security-tests/phone-onboarding.test.cjs
?? security-tests/purge-protocol.test.cjs
```

APK SHA-256: `2f80bcccf20c2bcfb372a7bb6edb7155dfbcddca2712f6a39c72844641deebbd`.
