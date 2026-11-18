# AIDHUNT TRAC — Development Progress & Final Reconciliation Log

## Phase 5.3 — Owner refresh repair and staged Partner deletion

- Baseline 6afbc862ee939bb188f480563399d35ae3e34cfb, dedicated codex/phase53-owner-activity-deletion branch. Normal product candidate 1.0.14/code15.
- Confirmed legacy membership-shape mismatch: notification verification required a redundant workspaceId omitted from the live Owner membership, although normal discovery accepted its verified path. Fixed path-derived identity with explicit mismatch rejection. Added safe diagnostics and partial refresh handling that preserves the last successful checkpoint and saved history.
- Prepared default-off shared Partner job deletion across creators/ages/New/Old Entries. Current financial-child, source-version, membership, outbox and immutable-proof safeguards remain. Operator UI/outbox eligibility now agrees with the already restricted guarded backend.
- Separate firestore.phase53.rules is derived from the active Rules export; only job lock/proof Partner restrictions change. Standard firestore.rules and live Rules are unchanged. See docs/verification/phase53-owner-refresh-and-partner-deletion.md and the adjacent Rules diff for evidence, rollout boundary and phone checklist.
- Proposed Rules 15/15 and focused CI/publication harness 17/17 passed; inventory has119 JVM classes/24 backend files without declaration errors. Initial293 Android run had4 failures; follow-up49/50 passed including actual staged native transport and duplicate acknowledgement. Corrected synthetic edit identity metadata; final refresh rerun, build/lint, signing and exact-source CI remain pending at this development commit. Original evidence retained outside Git. No physical verification, production writes/recovery, Rules deployment, native push activation, billing change or merge performed.
- Exact next action: finish isolated notification/Room/Compose/outbox/deletion tests and candidate Rules tests, build/lint/sign and verify the product APK, publish the dedicated branch, verify exact-source GitHub CI, then stop for separate Rules deployment approval. Broad Partner deletion must stay disabled in the delivered APK until the reviewed policy is deployed and an activation build is approved.

## Phase 4 original Owner notifications

- Baseline `2a986651bc45c4e854892a7b886ef43a138f5b3e`, published working branch `codex/phase4-owner-notifications` after local validation.
- Read-only production evidence showed disabled billing, no deployed Functions sender, zero Owner registrations and zero Owner activity documents in the inspected workspace. Deployed Rules do not expose either push collection to direct Android access.
- Actual original Owner is selected by workspace owner UID and current active membership. Existing confirmed ledger writes feed the trusted sender. Android authenticates every private history/event request, handles permission and localization, and scopes tap and logout to the authenticated session. Emulator/synthetic builds remain isolated.
- Added sender, Auth, Firestore and Functions Emulator checks, Android native-output tests and an explicit controlled live-test flag. All 101 focused Android tests and 143 backend tests passed. Demo build, lint with zero errors, one actual Functions Emulator trigger, and the offline and paginated history integration test passed. Generated BuildConfigs verified gated Live Test and isolated Device/Physical Test. See docs/phase4-owner-notifications.md for the infrastructure, rollback and physical tests still requiring approval or execution.
- Exact next action: verify the Phase 4 branch's GitHub tests and stop before production billing, deployment, merge or APK distribution.

## Phase 3 financial reconciliation — 16 November 2026

- Base `c2469b7c6f5af44650ac2f9c63c395e797f5a5db` on `codex/phase2-job-editing-report-filters`. Actions run 43851231931 passed on that exact commit. Phase branch `codex/phase3-financial-reconciliation`.
- Authorized read-only transaction reconciliation confirmed that the yearly difference is undated embedded receipts and the cash-versus-net difference is the subset with no recorded payment method. The private reconciliation report contains the transaction-level evidence. No record date, payment method, investment or withdrawal eligibility was changed.
- Balance Sheet now shares one report projection with PDF export, preserves undated transactions in the overall figure, identifies provisional/review states and retains every PDF period across pages. Expenses analytics explains funding and refunds in English and Tamil. No changes to Rules, sync, Room schemas, production configuration or production records.
- Added synthetic FIN-001 to FIN-024 coverage and expanded the report UI and reopened Room checks. Regenerated interaction and test asset inventories. Demo debug build, lint (0 errors, 74 existing warnings), 150 focused tests across 14 classes, and 111 harness checks passed. Three Room tests needed a shorter Windows scratch path on the first local run, then passed with all assertions intact.
- Exact next action: verify the Phase 3 branch validation and review any dated receipt/channel evidence or financial-policy change with the business before acting on it. Physical-device verification is pending. Stop before Phase 4.

## Phase 2 job editing and report filters — 16 November 2026

- Base `3a29c08a5ceb3f12ef6875817a9a35869a0ed3f9` on `codex/phase1-ui-restoration`. GitHub run 43752094050 passed on that exact commit. Phase branch `codex/phase2-job-editing-report-filters`.
- Customer Dues had Owner-only Edit controls. The save handler applied the new-job creator check to cross-creator edits, and the form required missing legacy work duration or tractor data even for an unchanged record. Fixed these paths, sent the original draft snapshot into the existing Room transaction, and prevented a stale or missing source from being overwritten or recreated. The record's original creator and the latest editor remain separate. Existing audit, member, payment and queue safeguards still apply.
- Collection History sorts latest matching collection dates or totals of matching allocations and retains the oldest-first payment timeline. All three reports offer IST month/year and inclusive date ranges, plus existing compatible filters. Undated transactions remain visible in All Time. Expense and withdrawal decoding preserves absent transaction dates for display.
- Verified the exact active live Rules revision `e7981704-cf72-453b-a867-ad3faa10c75f` read-only. Eight targeted Firebase emulator tests passed against that exact source. Demo Android build, lint (0 errors, 74 warnings), 153 focused tests across 15 suites with no skips, and the Git/inventory checks passed. English and Tamil report journeys and Phase 1 focused regressions were included. The test runner had stalled in the earlier keyboard-based picker, and the final fixture supplies a synthetic user to the Expense screen.
- The extended Android integration test exercises a Partner offline edit of an Owner's historical job, stale-form rejection, receipt preservation, idempotent retry and download into another authenticated Room database. Check the push workflow for its result. Physical-device verification is pending. No Rules, production configuration, customer data, Balance Sheet formulas, sync protocol or production release changed.
- Exact next action: verify the Phase 2 push workflow and stop before financial reconciliation and notifications.
- The first Phase 2 CI run 43849313887 passed all 38 Android emulator tests, including the named offline historical edit and download into a second authenticated Room database. Its broader Compose job found two Expense date regression fixtures that used the runner timezone and an English AM/PM casing difference. The follow-up anchors both fixtures in IST and preserves English uppercase AM/PM. All 13 targeted follow-up tests passed, including the two original localization assertions and all new Phase 2 tests. Verify the follow-up CI run before customer release.

## Phase 1 UI restoration — 16 November 2026

- Base `3629296b5dff45fd036cdada6123ad654b45c4aa` on GitHub `codex/stabilization-release`. Baseline Actions run 43664989563 reported 11 successful jobs and one optional manual job skipped. Working branch `codex/phase1-ui-restoration`.
- Restored compact Home job cards and a single Entry For picker. Job displays use name-only attribution. The Home card shows the work date and actual creation time in IST, with a separate recording label for historical jobs or differing dates. Existing job snapshots, creator, selected UID and financial attribution fields remain authoritative.
- Paused new loan, borrowing and repayment actions in Compose and ViewModel. Reports show Coming Soon cards and provide access to existing records when available. Already-recorded repayment confirmations and pending request rejection/cancellation remain under the existing authorization checks. Existing linked loan expenses are preserved in job edits.
- `assembleDebug` passed with a synthetic Firebase configuration. All 43 focused Kotlin/Compose tests passed across seven suites with no skips, including a 320 dp layout, English/Tamil, creator/beneficiary IDs, personal and business expense funding, loan gates, and existing record access. The first test run found a Tamil job-label expectation that still counted a removed role prefix. The updated test asserts name-only job labels and retains collector-role coverage. Interaction inventory and `git diff --check` passed.
- Remaining verification: physical devices and the final release-wide regression. No Firestore Rules, Firebase configuration, sync engine, financial formulas, production data, or Room schema changes. No release or production merge.
- Exact next action: keep Phase 1 on its own branch for review and wait for direction before Phase 2.

## Stabilization development checkpoint — 15 November 2026

- Base: GitHub main `894c38b4172118471e3c32adc38bbf13d8cbb51d`. Working branch: `codex/stabilization-release`.
- Restored the tracked demo Firebase file and generated inventories, restored runner script modes, and returned debug variants to Android's default debug signing.
- Added compatibility for older Rules that deny the optional cash-state read, while leaving enabled cash controls and financial deletion checks in force. Exact live rejection causes and deployed versions are still pending verification. No retained production operation was retried or changed.
- Enabled Partner Old Entry creation in Android, queue policy and source Rules. Recorded operator display is independent from the existing financial attribution selection on new jobs. Home offers a compact sync warning with the existing diagnostics destination.
- Added owner activity messaging service, Firestore events and device registration, and server triggers based on confirmed writes and deletion proofs. Native delivery still needs isolated FCM setup and Android runtime evidence.
- Local evidence: 111 Python harness checks, 222 focused Firestore Rules checks (synthetic emulator), and 2 owner activity sender tests passed. The first Rules attempt ran suites against shared fixtures in parallel. The serial rerun passed. The first Kotlin run found a Partner creation time-window regression, which was corrected; final source verification is in progress.
- Next: verify CI against this branch, verify old and new Rules compatibility plus original-operation retention, then obtain the missing device and active backend evidence. Production deployment, recovery, and APK distribution follow the separate controls in the stabilization brief.

**Base Repository:** `Thiru-Selvam-06/aidhunt-trac-release` (`main`)  
**Target Customer Package ID:** `com.aistudio.aidhunttrac.cedlir` (`app/google-services.json` preserved, `INVITATION_TEST_PHONES` kept empty, release signing configs untouched)  
**Environment Limitations (Recorded Honestly):**
- `.git` metadata was stripped during AI Studio import, so local Git commits, `git diff`, and Draft PR creation cannot execute inside AI Studio (`scripts/publication_guard.py` exits with code 128). File changes remain reviewable directly in the workspace.
- No Android Emulator or physical Android device (`adb`) is attached in this execution environment. Manual physical-device tests and automated physical-device instrumentation tests (`androidTest`) are **NOT TESTED**.
- All automated cloud tests use isolated local Firebase emulators (`demo-aidhunt-v16`, synthetic fixtures only); zero production deployments or production data mutations have been performed (`productionDeployment: "NOT AUTHORIZED"`).

---

## Stage 1 — Durable Partner-Label Behavior, Owner Permissions, & Active Selector Exclusions

### Status: COMPLETED & VERIFIED (JVM / Compose / Database / Outbox-Emulator + Debug APK)

### Completed Work
1. **Build Configuration Compatibility:**
   - Updated `app/build.gradle.kts` (`secrets.ignoreList`) to ignore empty server/emulator keys in `.env.example` (`FIREBASE_AUTH_EMULATOR_HOST`, `FIRESTORE_EMULATOR_HOST`, `FUNCTIONS_EMULATOR`, `GCLOUD_PROJECT`, `INVITATION_SMS_ENABLED`) so `BuildConfig.java` compiles cleanly without modifying `.env.example` or `INVITATION_TEST_PHONES`.
2. **Retained Membership Data Flow (Fresh-Device & Offline Persistence):**
   - Updated `WorkspaceRepository.kt` so `_workspaceMembers` stays synced with retained `REMOVED` member documents across `initializeForUser` and `pushUnsyncedToCloud` (in addition to `cacheVerifiedMembership`/`restoreVerifiedMembership`).
   - Updated `MainActivity.kt` so `HomeScreen`, `ReportScreen` (all report tabs + Business Overview), and `NewEntryScreen` receive `workspaceMembers` (containing retained `REMOVED` Partner history) rather than `activeWorkspaceMembers`, ensuring a fresh device recognizes prior partnership even after the last Partner leaves.
3. **Owner -> Partner Durable Display Transformation (EN/TA) & Owner Permission Preservation:**
   - Updated `RoleUtils.kt` (`getCreatedByDisplay`, `getEditedByDisplay`) to accept `hasBusinessPartnership` and render `Partner`/`பங்குதாரர்` instead of `Owner`/`உரிமையாளர்` while keeping `RoleUtils.isOwner` and `AuthorizationManager` admin/old-entry/customer-delete/partner-management permissions intact.
   - Updated `HomeScreen.kt`, `NewEntryScreen.kt`, `CollectionHistoryTab.kt`, `CustomerCreditDueTab.kt`, `WithdrawalTab.kt`, `BusinessOverviewScreen.kt`, and `AccountScreen.kt` to use `businessDisplayRole` / `businessRoleDisplayName` / `rememberBusinessPartnership` / `hasBusinessPartnershipHistory`.
   - Verified `Operator`/`இயக்குனர்` retains the Operator label and remains restricted.
4. **Exclusion of `REMOVED` Members from Active Financial Selectors:**
   - Confirmed and tested that `REMOVED` Partners remain visible for historical attribution/partnership-history detection while excluded from active financial selectors (`getEligibleProfitShareParticipants`, `duesFilterPartners`, `CollectionPolicy.eligibleCollectors`, `NewEntryScreen` active selectors, `ExpensesTab` paid-by selectors, `WithdrawalTab` `TakeAmountDialog` & `distributionMembers`).
5. **Automated Regression Coverage Added:**
   - Added `app/src/test/java/com/example/DurablePartnerLabelUiTest.kt` (3 Robolectric Compose + unit tests clearing SharedPreferences to simulate a fresh device).

---

## Stage 2 — Authoritative Atomic Shared-Cash Control & Concurrency Prevention

### Status: COMPLETED & VERIFIED (Firestore Rules / Backend / Kotlin-Emulator / Unit / Inventory + Debug APK)

### Completed Work
1. **Shared-Funds Writer Inventory & Rollout Capability Flag:**
   - Inventoried all cash, entitlement, and commitment writers across Android (`AtomicCashControl.kt`, `FirestoreRepository.kt`, `FirebaseLedgerDeletion.kt`, `WorkspaceRepository.kt`), Firestore Security Rules (`firestore.rules`), and command/sync protocols (`WorkspaceSyncCapabilities.kt`).
   - Added `atomicCashControlV1` capability flag to `WorkspaceSyncCapabilities` and `VerifiedWorkspaceCapabilityCache`.
2. **Authoritative Spark-Compatible Multi-Document Atomic Control (`AtomicCashControl.kt`, `FirestoreRepository.kt`, `FirebaseLedgerDeletion.kt`):**
   - Implemented `workspaces/{ws}/financialState/current` (version-fenced server balance tracking `confirmedCash`, `reservedCash`, `confirmedBank`, `version`), immutable `cashOperations/{opId}` idempotent receipts, and `cashDeleteProofs/{kind}_{id}` single-step deletion proofs.
   - Guarded `WITHDRAWAL` (`UPSERT` requiring `confirmedCash - reservedCash >= amount` and single-step `DELETE`), `PAYMENT` (`UPSERT` Cash credit / `REVERSE` Cash reversal), `EXPENSE` (`UPSERT` with `QueuedWriteConflict` & signed `cashDelta` / `DELETE`, preserving Business Paid negative-balance allowance), `JOB` `DELETE` (REST `batchGet` + `updateTransforms` `REQUEST_TIME` + Partner `<= 3` verified expenses), and `PURGE` chunk reconciliation.
   - Added `InsufficientSharedFundsException` (permanent rejection triggering local optimistic withdrawal rollback while retaining original `REJECTED` outbox item in `Needs Review`) and `FinancialBaselinePendingException` (transient hold refusing to fabricate opening balances when baseline is uninitialized).
3. **Hardened Firestore Security Rules (`firestore.rules`) & Diagnostics (`OperationDiagnostic.kt`, `RetainedOperationWarning.kt`):**
   - Enforced atomic multi-doc write coupling, monotonic version increment, anti-replay (`!exists(cashOpPath)`), canonical `paymentMethod` and `paidBy` validation, and old-client single-doc write/two-step tombstone blocking when `atomicCashControlV1` or `financialState/current` is active.
4. **Automated Concurrency & Emulator Regression Coverage:**
   - Added `security-tests/atomic-cash-control.test.cjs` (8 Node `@firebase/rules-unit-testing` specs).
   - Added `app/src/test/java/com/example/AtomicCashOperationEmulatorTest.kt` (4 Robolectric + live Firestore emulator + Room specs).
   - Updated `app/src/test/java/com/example/WorkspaceSyncCapabilitiesTest.kt`.

---

## Stage 3 — External Loans, Business & Partner Borrowing, Approvals, Reservations & Settlements

### Status: COMPLETED & VERIFIED (Firestore Rules / Backend / Unit / Database / Compose / Outbox-Emulator + Debug APK)

### Completed Work
1. **Room Schema v23 & Stage 3 Entities/DAOs (`Stage3Entities.kt`, `Stage3Dao.kt`, `AppDatabase.kt`):**
   - Added Room entities, DAOs, and `MIGRATION_22_23` for `ExternalLoanEntity`, `ExternalLoanRepaymentEntity`, `BorrowingRequestEntity`, `SettlementObligationEntity`, and `SettlementRepaymentEntity`, plus `fundingSource`, `externalLoanId`, and `loanFundingMode` support on `ExpenseEntity` for 1:1 `Loan Funded` expenses.
2. **Authoritative Stage 3 Atomic Controls & Security Rules (`Stage3AtomicControl.kt`, `firestore.rules`, `AuthorizationManager.kt`):**
   - Enforced `OWNER`/`PARTNER` external loan creation & repayment (`operatorAllowed: false`), separate manual principal & interest fields, destination modes (`BUSINESS_CASH`, `BUSINESS_BANK`, `DIRECT_TO_SUPPLIER`), and `Loan Funded` expense 1:1 linkage with zero shared cash/bank delta.
   - Enforced Business Borrowing (`sourceType = BUSINESS`, `automaticProfitOffset = false`, no approval popup, capped by confirmed available shared cash/bank after protecting withdrawable partner entitlements and approved reservations).
   - Enforced Named-Partner Borrowing & Split-Lender Borrowing with explicit lender approval, atomic `BorrowingRequest` cash reservation, execution, rejection, and cancellation.
   - Enforced Settlement & Repayment Confirmation rules (`INTO_BUSINESS` creditor confirmation & authorized business payer checks; `DIRECT_TO_PARTNER` named lending Partner confirmation without increasing shared business cash).
3. **Financial Calculation & Outbox Integration (`FinancialCalculationEngine.kt`, `PartnerBorrowingCalculator.kt`, `OperationDependencies.kt`, `OperationPolicy.kt`, `FirestoreRepository.kt`, `WorkspaceRepository.kt`, `MainViewModel.kt`):**
   - Integrated Stage 3 operations into the durable outbox queue, dependency graph, and optimistic/authoritative reconciliation flows without auto-retrying permanently rejected operations or modifying historical Cash/UPI/bank records.
4. **Material 3 Stage 3 UI (`ExternalLoansScreen.kt`, `PartnerSettlementsScreen.kt`, `BorrowDialog.kt`, `ExpensesTab.kt`, `WithdrawalTab.kt`, `ReportScreen.kt`, `MainActivity.kt`):**
   - Built External Loans management, Loan Funded expense linking, Business & Partner Borrow dialog, lender approval/reservation/execution workflow, and Partner Settlements & repayment confirmation UIs with EN/TA labels.
5. **Automated Regression Coverage Added:**
   - `security-tests/loans-borrowing-settlements.test.cjs`
   - `app/src/test/java/com/example/PartnerBorrowingCalculatorTest.kt`
   - `app/src/test/java/com/example/Stage3RoomDatabaseTest.kt`
   - `app/src/test/java/com/example/Stage3AtomicOperationEmulatorTest.kt`

---

## Stage 4 — Bilingual Cloud-Confirmed Notifications, Loan Analytics Chart, Audit Retention, & IST/PDF Polish

### Status: COMPLETED & VERIFIED (Firestore Rules / Unit / Database / Compose / Outbox-Emulator + Debug APK)

### Completed Work
1. **Room Schema v24 & Notification Storage (`AppNotificationEntity.kt`, `AppNotificationDao.kt`, `AppDatabase.kt`):**
   - Added `AppNotificationEntity` (`app_notifications` table keyed by `workspaceId, id`), `AppNotificationDao`, and `MIGRATION_23_24`.
   - Stored structured notification fields (`eventType`, `entityType`, `entityId`, `amount`, `counterpartyName`, `metadataJson`, `actorUid`, `actorName`, `recipientUid`, `targetAudience`) rather than pre-rendered sender-language strings so each recipient sees notifications in their own active language (English or Tamil).
   - Preserved local `isRead`/`readAt` state across incoming cloud snapshots (`upsertPreservingReadState`) and added 90-day read-notification pruning (`pruneReadBefore`).
2. **Idempotent Cloud-Confirmed Notification Emission (`NotificationEmitter.kt`, `FirestoreRepository.kt`, `WorkspaceRepository.kt`, `firestore.rules`):**
   - Wired `NotificationEmitter.buildForConfirmedOperation(...)` strictly inside `WorkspaceRepository.acknowledge(op)` so local unconfirmed or permanently rejected operations never emit false notifications.
   - Used deterministic IDs (`${operationId}_OWNER_${eventType}` and `${operationId}_PARTNER_${recipientUid}_${eventType}`) and `publishNotificationsIfAbsent` (`!existing.exists()` server check) to prevent duplicate notifications across retries.
   - Blocked self-notifications (`actorUid != recipientUid` in both `NotificationEmitter` and `firestore.rules`).
   - Added `match /notifications/{notifId}` and immutable `match /auditEvents/{eventId}` rules in `firestore.rules`, plus real-time notification listener and pending audit flush during `pushUnsyncedToCloud()`.
3. **Bilingual Notification Presenter, Bell Badge, & Panel Sheet (`NotificationPresenter.kt`, `NotificationPanel.kt`, `CommonComponents.kt`, `MainViewModel.kt`, `MainActivity.kt`):**
   - Implemented `NotificationPresenter.present(notification, isTamil, currency)` covering all 19 owner-monitoring and partner-targeted borrowing/settlement events with IST timestamps (`JobAuditPresentation.instant`).
   - Added unread notification badge + `48.dp` touch target bell button (`notification_bell_button`) to `AppTopHeader` for Owner and Partner roles (hidden for Operator) and wired `NotificationPanelSheet` with `All`, `Unread`, and `Loans/Settle` filters plus single and bulk mark-read actions.
4. **Loan Repayment Analytics Chart (`LoanAnalytics.kt`, `LoanAnalyticsCard.kt`, `ExternalLoansScreen.kt`):**
   - Implemented `LoanAnalytics` and `LoanAnalyticsCard` with `All` / `Daily` / `Monthly` / `Yearly` filters and three separated chart modes (`Loan Repayments Made`, `Loan Principal Outstanding`, `Loan Funding`) embedded in `ExternalLoansScreen`.
5. **IST Timezone & Historical vs Current Job Timestamp Polish (`HomeScreen.kt`, `PdfGeneratorHelper.kt`):**
   - Updated `HomeScreen.kt` job detail modal to display full IST work timestamp (`JobAuditPresentation.instant`) for non-historical jobs and date-only (`JobAuditPresentation.date`) for historical jobs alongside recorded/last-edited IST timestamps and expandable edit history.
   - Updated `PdfGeneratorHelper.kt` to enforce `Asia/Kolkata` (`IST`) on `dateFormat` and `dateTimeFormat`, format historical job receipts as date-only, and include recorded IST timestamp on job receipts.
6. **Automated Regression Coverage Added:**
   - `security-tests/notifications-audit.test.cjs`
   - `app/src/test/java/com/example/Stage4NotificationUnitTest.kt`
   - `app/src/test/java/com/example/Stage4NotificationRoomTest.kt`
   - `app/src/test/java/com/example/Stage4UiRegressionTest.kt`
   - `app/src/test/java/com/example/Stage4NotificationOperationEmulatorTest.kt`

### Files Changed (Stage 4)
- `firestore.rules`
- `scripts/run_backend_suite.mjs`
- `security-tests/notifications-audit.test.cjs` (new)
- `app/src/main/java/com/example/data/entity/AppNotificationEntity.kt` (new)
- `app/src/main/java/com/example/data/dao/AppNotificationDao.kt` (new)
- `app/src/main/java/com/example/data/database/AppDatabase.kt`
- `app/src/main/java/com/example/data/sync/NotificationEmitter.kt` (new)
- `app/src/main/java/com/example/data/expense/LoanAnalytics.kt` (new)
- `app/src/main/java/com/example/data/firebase/FirestoreModels.kt`
- `app/src/main/java/com/example/data/firebase/FirestoreRepository.kt`
- `app/src/main/java/com/example/data/repository/WorkspaceRepository.kt`
- `app/src/main/java/com/example/ui/util/NotificationPresenter.kt` (new)
- `app/src/main/java/com/example/ui/components/NotificationPanel.kt` (new)
- `app/src/main/java/com/example/ui/components/LoanAnalyticsCard.kt` (new)
- `app/src/main/java/com/example/ui/components/CommonComponents.kt`
- `app/src/main/java/com/example/ui/screens/report/ExternalLoansScreen.kt`
- `app/src/main/java/com/example/ui/screens/home/HomeScreen.kt`
- `app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt`
- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/main/java/com/example/pdf/PdfGeneratorHelper.kt`
- `app/src/test/java/com/example/ArchitectureIntegrationTest.kt`
- `app/src/test/java/com/example/RoomScopeTest.kt`
- `app/src/test/java/com/example/BasicCustomerRoomTest.kt`
- `app/src/test/java/com/example/Stage4NotificationUnitTest.kt` (new)
- `app/src/test/java/com/example/Stage4NotificationRoomTest.kt` (new)
- `app/src/test/java/com/example/Stage4UiRegressionTest.kt` (new)
- `app/src/test/java/com/example/Stage4NotificationOperationEmulatorTest.kt` (new)
- `ci-reports/ui-interaction-inventory.json`
- `ci-reports/test-suite-inventory.json`

---

## Stage 5 — Full Acceptance Criteria Reconciliation & Final Regression Gate

### Status: COMPLETED & VERIFIED (All Emulator/JVM/Compose/Database/Outbox/Worker/Lint/APK Gates Passed; Physical Device Suites Marked NOT TESTED)

### Acceptance Criteria Reconciliation Matrix (`ORIGINAL_MASTER_PROMPT.md` + `APPROVED_POLICIES.json`)
1. **Customer Build Identity & Signing Preservation:**
   - `namespace = "com.example"`, `applicationId = "com.aistudio.aidhunttrac.cedlir"` in `app/build.gradle.kts`.
   - `app/google-services.json` preserved for `trac-aidhunt`; `.env.example` keeps `INVITATION_TEST_PHONES=` empty.
   - `signingConfigs` (`debugConfig` and `release` + `validateReleaseCredentials`) untouched.
2. **Stage 1 — Durable Partner Label & Removed Member Exclusions:**
   - Fresh devices and offline caches recognize prior partnership via retained `REMOVED` member records (`hasBusinessPartnershipHistory`), displaying `Partner` / `பங்குதாரர்` instead of `Owner` / `உரிமையாளர்` while keeping Owner permissions intact and excluding `REMOVED` members from active financial selectors.
   - Verified by `DurablePartnerLabelUiTest` and `WorkspaceMemberPresentationTest`.
3. **Stage 2 — Authoritative Atomic Shared-Cash Control & Concurrency Prevention:**
   - `workspaces/{ws}/financialState/current` + `cashOperations/{opId}` + `cashDeleteProofs/{kind}_{id}` prevent concurrent over-withdrawal (e.g., two simultaneous ₹700 withdrawals from ₹1,000 shared cash), reject stale versions/replays/old-client bypasses, refuse to fabricate opening balances, and reconcile rejected optimistic withdrawals while retaining the rejected operation in `Needs Review`.
   - Verified by `security-tests/atomic-cash-control.test.cjs` and `AtomicCashOperationEmulatorTest`.
4. **Stage 3 — External Loans, Business & Partner Borrowing, Approvals, Reservations & Settlements:**
   - External Loans restricted to `OWNER`/`PARTNER` (`operatorAllowed: false`), separate manual principal & interest, destinations `BUSINESS_CASH`, `BUSINESS_BANK`, `DIRECT_TO_SUPPLIER`, and 1:1 `Loan Funded` expense linkage.
   - Business Borrowing (`creditorType = BUSINESS`, `automaticProfitOffset = false`, no approval popup) capped by confirmed available funds after protecting withdrawable partner entitlements and approved reservations.
   - Named-Partner & Split-Lender Borrowing require lender approval before reservation/disbursement.
   - Repayments require recipient confirmation before debt reduction (`INTO_BUSINESS` confirmed by authorized business recipient; `DIRECT_TO_PARTNER` confirmed by named lender without increasing business cash).
   - Deferred policies (`sharedExpenseDebts`, `personalRepaymentPolicy`) respected.
   - Verified by `security-tests/loans-borrowing-settlements.test.cjs`, `PartnerBorrowingCalculatorTest`, `Stage3RoomDatabaseTest`, and `Stage3AtomicOperationEmulatorTest`.
5. **Stage 4 — Bilingual Notifications, Loan Analytics Chart, Audit Retention, IST & PDF Formatting:**
   - Cloud-confirmed notification emission only, deterministic idempotency, zero self-notifications, recipient-language EN/TA presentation, unread bell badge & sheet filters.
   - Loan Analytics card with `All`/`Daily`/`Monthly`/`Yearly` filters and separated `Loan Repayments Made`, `Loan Principal Outstanding`, and `Loan Funding` modes.
   - Immutable `auditEvents` retention, `jobEditHistoryJson` before/after display, and `Asia/Kolkata` (`IST`) timestamps across UI and PDF exports (with date-only rendering for historical jobs).
   - Verified by `security-tests/notifications-audit.test.cjs`, `Stage4NotificationUnitTest`, `Stage4NotificationRoomTest`, `Stage4UiRegressionTest`, and `Stage4NotificationOperationEmulatorTest`.

### Final Stage 5 Regression Results
- **Firestore Security Rules Suite (`node scripts/run_backend_suite.mjs rules` via `demo-aidhunt-v16` emulator):** **PASSED** (`391` passed, `0` failed, `0` skipped).
- **Backend / Functions / Cloudflare Worker Test Suite (`node scripts/run_backend_suite.mjs backend` via `demo-aidhunt-v16` emulator):** **PASSED** (`137` passed, `0` failed, `0` skipped).
- **Cloudflare Worker Bundle Build (`npm --prefix cloudflare run build`):** **PASSED** (`✓ 11 modules transformed`, `.cloudflare/output/v0/workers/default/bundle/index.js`).
- **Android JVM Unit Suite (`-PciSuite=unit`):** **PASSED** (`48` test classes, `359` tests, `0` failed).
- **Android Room Database & Migration Suite (`-PciSuite=database`):** **PASSED** (`24` test classes, `175` tests, `0` failed).
- **Android Robolectric Compose UI Suite (`-PciSuite=compose`):** **PASSED** (`19` test classes, `67` tests, `0` failed).
- **Android Outbox & Live Firestore Emulator Suite (`-PciSuite=outbox` via `demo-aidhunt-v16` emulator):** **PASSED** (`16` test classes, `0` failed).
- **Test Suite & Interaction Inventories (`suite_inventory.py --check`, `interaction_inventory.py --check`):** **PASSED** (`unitClasses: 107`, `backendFiles: 22`, `1069` interaction candidates, `errors: []`).
- **Android Lint (`gradle :app:lintDebug`):** **PASSED** (`BUILD SUCCESSFUL`).
- **Installable Debug APK Checkpoint (`gradle :app:assembleDebug`):** **PASSED** (`app/build/outputs/apk/debug/app-debug.apk`).
- **Physical Device & Instrumented `androidTest` Suites:** **NOT TESTED** (no Android emulator/device/ADB available in container).

---

## Release Gates 1–5 & Production Deployment Readiness Verification

### Gate 1 — GitHub Actions CI & Demo Test Configuration Fixes (`PASSED`)
- **`OperationPolicy.kt` & `OperationRoleMatrixTest.kt`:** Extracted inner `when (stage3Action)` blocks into helper functions so `OperationPolicy.kt` top-level entity switch keys strictly match `OperationRoleMatrixTest.kt` (`EXTERNAL_LOAN`, `EXTERNAL_LOAN_REPAYMENT`, `BORROWING_REQUEST`, `SETTLEMENT_REPAYMENT` added to `OperationRoleMatrixTest`).
- **`.gitignore` Restoration:** Added `!docs/regression/asset-inventory.json`, `!docs/regression/interaction-inventory.json`, and `!/app/src/deviceTest/google-services.json` so tracked regression inventories and the synthetic demo test configuration (`demo-aidhunt-v16`, `com.aistudio.aidhunttrac.cedlir.devicetest`) are never excluded when exporting or committing to GitHub, while keeping `/app/google-services.json` (`trac-aidhunt`, `com.aistudio.aidhunttrac.cedlir`) ignored from public git history per `publication_guard.py`.
- **`scripts/signing-isolation.init.gradle`:** Updated `verifySigningIsolation` to accept `debug` or `debugConfig` and inspect configured release signing gates (`PASS: 4 debug/test variants isolated; release credential gate attached; package IDs preserved`).
- **CI Script Unit Suite (`python3 -m unittest discover -s scripts -p "test_*.py" -v`):** **PASSED** (`110` tests, `0` failures).

### Gate 2 — Server-Boundary Protection of Partner Withdrawable Entitlements & Approved Commitments (`PASSED`)
- **`firestore.rules` (`validBorrowingRequestReceipt`, `validCashReceiptStates`, `financialState/current`, `borrowingRequests/{id}`):**
  - Added `protectedEntitlements` support on `financialState/current` (`>= 0`) and `protectedEntitlementsAfter` on `cashOperations/{opId}`.
  - Enforced at the server boundary for `BUSINESS_ONLY` borrowing (`op.mutationType == 'UPSERT'`):
    - `protectedAfter >= protectedBefore - 0.000001` (prevents tampering/zeroing out `protectedEntitlements` during business borrowing).
    - `fsAfter.confirmedCash - fsAfter.get('reservedCash', 0) - protectedBefore >= -0.000001`
    - `fsAfter.confirmedCash - fsAfter.get('reservedCash', 0) - protectedAfter >= -0.000001`
    - `fsAfter.confirmedCash - fsAfter.get('reservedCash', 0) - bAfter.get('protectedEntitlementsSnapshot', 0) >= -0.000001`
    - Verified linked `withdrawals/{withdrawalId}` and `settlementObligations/{obligationId}` match `operationId`, `amount`, and `creditorType == 'BUSINESS'`.
  - Enforced `lenderWithdrawableSnapshot >= amount` on `APPROVE_RESERVE`.
- **`AtomicCashControl.kt`, `Stage3AtomicControl.kt`, & `WorkspaceRepository.kt`:**
  - Enforced `minProtectedCash = max(serverProtected, snapshotProtected)` in `Stage3AtomicControl.applyBorrowingRequest` (`EXECUTE_BUSINESS`) and preserved `protectedEntitlements` across all `writeCashStateAndReceipt` updates.
- **Server-Boundary Tests Added & Verified:**
  - `security-tests/loans-borrowing-settlements.test.cjs` (`business borrowing protects all partners withdrawable entitlements and approved commitments at the server boundary`)
  - `app/src/test/java/com/example/Stage3AtomicOperationEmulatorTest.kt` (`businessBorrowingProtectsPartnerEntitlementsAndApprovedCommitmentsAtServerBoundary`)

### Gate 3 — Complete Regression Suites with Synthetic Data (`PASSED`)
- **Firestore Security Rules (`node scripts/run_backend_suite.mjs rules`):** **PASSED** (`392` passed, `0` failed).
- **Backend / Functions / Cloudflare Worker (`node scripts/run_backend_suite.mjs backend`):** **PASSED** (`137` passed, `0` failed).
- **Cloudflare Worker Production Build (`npm --prefix cloudflare run build`):** **PASSED** (`47.61 kB` bundle).
- **Android Unit Partition (`-PciSuite=unit`):** **PASSED** (`0` failed).
- **Android Database Partition (`-PciSuite=database`):** **PASSED** (`0` failed).
- **Android Compose Partition (`-PciSuite=compose`):** **PASSED** (`0` failed).
- **Android Outbox + Firestore Emulator Partition (`-PciSuite=outbox`):** **PASSED** (`0` failed).
- **Synthetic Performance Baseline (`BusinessPerformanceTest`):** **PASSED** (`0` failed).
- **Debug, Lint, & DeviceTest APK Builds (`assembleDebug`, `lintDebug`, `assembleDeviceTest`, `assembleDeviceTestAndroidTest`):** **PASSED**.

### Gate 4 — Backward Compatibility & Rollback Plan (`VERIFIED`)
- **Backward Compatibility:**
  - `firestore.rules` gates atomic cash enforcement behind `atomicCashEnabled(ws)` (`syncCapabilities.atomicCashControlV1 == true` or existence of `financialState/current`) and treats `reservedCash`, `confirmedBank`, and `protectedEntitlements` with `.get(..., 0)` defaults so existing workspaces and pending protocol v2 operations continue to work seamlessly.
  - `firestore.indexes.json` preserves the `pendingPhones` `COLLECTION_GROUP` index override.
  - Room migrations (`MIGRATION_22_23`, `MIGRATION_23_24`) are additive (`CREATE TABLE IF NOT EXISTS` / `ALTER TABLE ADD COLUMN` with defaults), preserving all local customer records and queued `pending_operations`.
- **Rollback Plan:**
  1. **Firestore Rules & Indexes:** Export currently deployed rules (`firebase firestore:rules:get` / Firebase Console Rules History) before deployment; if any regression occurs, immediately re-deploy the prior ruleset revision in Firebase Console (`trac-aidhunt`) or via `firebase deploy --only firestore:rules --project trac-aidhunt`.
  2. **Atomic Cash Capability Flag:** If an emergency bypass is needed for a specific workspace without rolling back rules, set `workspaces/{ws}.syncCapabilities.atomicCashControlV1 = false` (only if `financialState/current` has not been seeded).
  3. **Cloudflare Worker:** Use `npx cf versions list` / `npx cf rollback` in `cloudflare/` (or Cloudflare Dashboard -> Workers -> Deployments -> Rollback) to revert to the previous Worker deployment instantaneously.

### Gate 5 — Production Target Verification & Live Deployment Blocker (`STOPPED BEFORE DEPLOYMENT`)
- **Production Targets Verified in Config:**
  - `.firebaserc`: `default -> "trac-aidhunt"`
  - `app/google-services.json`: `project_id: "trac-aidhunt"`, `package_name: "com.aistudio.aidhunttrac.cedlir"`
  - `app/src/deviceTest/google-services.json`: `project_id: "demo-aidhunt-v16"`, `package_name: "com.aistudio.aidhunttrac.cedlir.devicetest"`
  - `cloudflare/package.json`: `"name": "aidhunt-trac-trusted-worker"`
- **Blocker Preventing Live Deployment from This Container:**
  - `npx --yes firebase-tools@15.33.0 login:list` -> `No authorized accounts, run "firebase login"` (`Failed to list Firebase projects`).
  - `cd cloudflare && npx cf whoami` -> `{"authenticated": false, "error": "Not logged in"}`.
  - No `FIREBASE_TOKEN`, `GOOGLE_APPLICATION_CREDENTIALS`, or `CLOUDFLARE_API_TOKEN` credentials are present in the container environment, so live version comparison and live deployment to `trac-aidhunt` and Cloudflare cannot be executed from inside the AI Studio sandbox.

