# AIDHUNT TRAC — Development Progress & Final Reconciliation Log

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
