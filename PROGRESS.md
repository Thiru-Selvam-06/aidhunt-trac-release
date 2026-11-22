# AIDHUNT TRAC — Development Progress & Final Reconciliation Log

## Approved cash-permission deployment — 22 November 2026

- User approved the Firebase update, then confirmed other partner phones are not updated and required normal functions remain unaffected. The narrow tested compatibility Rules were activated and independently read back: dd21da1c-69cd-4978-9279-39f3760c3c0d, SHA90eb895bc114a1e22ab58248c339e49e422c598c94b0e9c60d0e5b6fb54931c2. Existing policy is byte-identical after removing only cash additions; zero server validation errors/four retained warnings. Transport errors were resolved by inspecting actual active release/source, not by assuming success or replaying financial operations.
- Real shared cash/atomic/Partner empty-customer capabilities remain FALSE; no new financial state exists. No financial record, pending action, attribution, default workspace or billing change. Fresh configuration: billing disabled, zero deployed Functions. Partner empty-profile cleanup still needs its trusted endpoint; don't confuse this with already-enabled eligible Partner job deletion.
- Signed1.0.17/code18 APK and PMS icon unchanged;34 release/42 Rules/7 native-emulator checks remain actual evidence. Customer release/physical-phone/full CI gate are not complete. See docs/verification/cash-permissions-live-rollout.md for current live state, preserved policy, rollback and remaining prerequisites.
- Exact next action: publish this controlled branch for existing CI and inspect real results; do not create any test workspace (user declined). Await confirmation that all partner activity can pause before any temporary live cash test. A test must reverse its exact difference and retain both audit records, not reset to a remembered target. Keep live cash OFF until all relevant devices are updated and activation is explicitly authorized. Never auto-correct disputed4500 or recover rejected transactions. No production merge or paid deployment.

## Shared available-cash corrections and PMS launcher — 22 November 2026

- User superseded display-only behavior: audited corrections must affect shared cash and cash withdrawal checks while preserving historical financial records, receipts, customer dues, earned profit and member entitlements. New local branch codex/available-cash-adjustments from0a7ee67. Product entry point now uses CashBalanceEditor/CashBalanceAdjustmentRepository, not the older display prototype. No live correction or historical reclassification has been authorized or performed.
- Confirmed live policy/source mismatch by GET-only read22Nov15:01IST: active Rulesc84da8eb-0fda-4318-a7ef-d16d8b9bd7d1 contain neither the old editor permissions nor new cash adjustment permissions. Minimal compatibility candidate adds only paired cash state/immutable audit paths, keeps protected clauses byte-identical and leaves the obsolete display-only API disabled. Existing atomic-cash mode remains coupled to its actual state/receipt transactions in the separate development policy. No production Rules/flag/backend/billing change.
- New actor/workspace-bound online transaction preserves original operation UUID, reason, real member identity, revision and server time; shared signed corrections move with future cash flows. Cache/history must reconcile before confirmed status. UI/withdrawal validation use corrected cash; original ledger/outbox/Room data and financial attribution remain unchanged. Existing aggregate server withdrawal model is not silently replaced or claimed atomic.
- Original user-supplied PMS Tractor JPEG is the launcher artwork, unchanged bytes. Adaptive/legacy wrappers protect its safe area. Actual icon hash/render verification is mandatory in the new Android suite. No package/config/native-push change.
- Existing infrastructure verification COMPLETE:32 host tests,42 Rules checks and7 actual API36 Android instrumentation cases passed, zero final failures/skips. Debug assembly/lint passed (0 errors/120 warnings; six icon-related additions explained in the report). Inventory/localization checks passed. The process restart is genuine: two Android SDK processes, peer process killed/rebound, source cash1200 + correction400 =1600 confirmed on both clients, cached history retained offline and logout denied. New non-exported peer service/test application exist only in the DeviceTest source set; normal demo startup is retained in its main process. Production app source and Rules were not relaxed to make tests pass. First in-process FirebaseApp recreation failed with Auth's deleted-instance references; failed traces retained. Actual launcher PNG inspected; supplied artwork verified byte-for-byte. Publication scan found no credentials.
- Functional checkpoint6850bc91f1793b66b153cf30b800f9667200128b is committed locally. Signed1.0.17/code18 owner-test APK passed34 release checks, assembly and release lint0 errors/120 warnings. Certificate matches previous product, original Firebase resources and false test/push flags verified; actual PMS artwork present and test-only components absent. Artifact SHA25687cbf661bdfc7c3291944c077af2c2bdfd3f1eab290e0b81c9a2f4d0b2da0985. Only later app change is version metadata; final verification report/progress are updated. No push performed.
- Exact next action: deliver the signed candidate and private mismatch verification references. Request separate approval for minimal Rules deployment; keep the cash capability OFF until all relevant partner apps are upgraded and activation is explicitly authorized. Recheck live baseline before rollout. Physical-phone tests and full GitHub release tests remain NOT RUN. Stop before production activation, merge or customer release. Do not recover retained live operations or use a cash correction as evidence for disputed receipts.

## 1.0.16 controlled APK testing checkpoint

- User requested signed APK for personal testing and advice on the unresolved amount. New branch codex/customer-controls-test-apk from e10c96f; only version metadata changed to1.0.16/code17, plus verification/progress documentation. No production activation or financial correction authorized.
- Original-key signed APK prepared locally; package/version/non-debuggable/real Firebase resources/new DEX classes and certificate match verified.4 actual release checks, signed assembly and release lint passed (0 errors/114 warnings). Prior78 implementation checks retained; no broad regression rerun. Artifact/evidence in host outputs/customer-controls-1016. Existing product1.0.15 unchanged.
- New shared display save/history and Partner unused-profile deletion remain pending their separate backend permissions/activation. Existing Partner job deletion gate remains ON. Native push/test/pilot flags remain OFF. No agent phone installation; physical testing NOT TESTED; GitHub full release gate NOT RUN.
- Exact next action: deliver the test APK with activation limits; Owner verifies legacy method/date evidence privately before any separately approved correction. Do not create duplicate receipts, deploy Rules/backend, enable billing, flip capabilities, modify financial records or merge/release automatically. See docs/verification/customer-controls-1016-test-apk.md.

## Display balance history and unused customer controls — 21 November 2026

- Development branch codex/customer-balance-controls starts from TRAC1.0.15 product6aba5b95d5e13f23d6406e75afc4cb11ad96f2ae. User chose DISPLAY ONLY, with required reason, actual editor, date/time and old/new/difference history. No real financial adjustment, method/date correction, ledger change, migration or production activation authorized.
- Implemented shared display preference plus immutable paired history, active financial membership/identity checks, online server-confirmed save, revision conflict detection, replay protection and restore-calculated history. Home and Overall Business Overview only; historical scopes, financial reports and withdrawable funds keep their original calculations. Original financial engine/report projection/Room schema/Entities/build configuration match baseline exactly.
- Added Owner/Partner Customers without records picker. All receipts including reversed, all workspace jobs and retained local dependencies protect profiles. Existing Owner Firebase-only deletion preserved. Partner command uses trusted server absence checks and original-operation acknowledgements behind trustedEmptyCustomerDeletionV1, default FALSE. No local deletion occurs for a Partner before secure activation. Direct Partner Firestore profile deletion remains denied.
- Actual final targeted checks:37 host tests PASS,34 Firebase/backend/Rules checks PASS,7 native API36 Android Emulator tests PASS. Debug/demo APK and instrumentation APK build PASS; Debug lint0 errors/114 warnings. All native cases guard .devicetest package + demo Firebase project; no physical phone was used. Initial native-runtime path issue fixed outside the source tree; new editor host render timeouts retain ALL assertions in the mandatory Android instrumentation plan. No business assertion/test gate removed.
- Read-only configuration preflight: billing disabled, no deployed Functions. No production Rules/Functions/capability activation, financial writes/recovery, billing change, APK distribution or production merge. Original product APK unchanged. Independent financial evidence investigation was completed locally; protected source/identity files are not committed.
- Verification, file list, actual limits, activation and rollback plan: docs/verification/customer-balance-controls.md. Physical automated/manual tests, live two-device metadata propagation and production endpoint activation remain NOT TESTED. GitHub Actions for this branch not run yet; targeted checks above are local/emulator results.
- Exact next action: review/activate the display-only policy only with explicit approval. Partner deletion additionally requires an approved trusted-backend plan (current Firebase Functions deployment requires Blaze); do not change billing or flip its capability automatically. Run staged client-to-cloud/two-device acceptance and full release checks before packaging/distributing a new product APK. Never correct disputed financial amounts without independent proof and explicit approval.

## Phase 5.3 user-approved activation — 19 November 2026

- User approved deployment of the reviewed narrow Firebase Rules change and a higher-version signed testing APK. Baseline42f1ddf620e73d8517d4ebe51e9c2841bac88b2e, isolated branch codex/phase53-partner-deletion-activation. No production merge, real financial test writes or rejected-transaction recovery authorized.
- Product1.0.15/code16 enables SHARED_PARTNER_JOB_DELETION in release only. Isolated debug/test/pilot variants keep false. Native push stays disabled; Owner in-app fix, finance/Room/outbox/runtime deletion logic unchanged. Added actual build-variant default-policy and isolation tests in Phase53ActivationTest.
- Fresh preflight confirmed live e7981704-cf72-453b-a867-ad3faa10c75f unchanged from reviewed baseline; candidate canonical SHA2564b0880cb108fe97440cee9a008826e5498807b77243fbc292c47313cd06a5874. Firebase server validation0 errors/2 warnings; all15 exact candidate Rules Emulator cases passed again. Live Rules have not been activated at this publication point.
- Targeted release-unit verification is running with a local variant-enabling init script and isolated demo Firebase resources; the protected real release resources are restored before packaging. No shipped test dependency or existing CI gate is changed. The first attempt found no release-unit task; the local init exposes it without changing application behavior.
- Exact next action: finish targeted release tests, normal signed build/lint and exact-source CI; deploy only the approved candidate after rechecking the live baseline; read back the active source/hash; verify final APK certificate/package/config/flags, record deployment and evidence in the delivery report, and hand over the manual two-phone checklist. Do not create/delete live financial test records or auto-retry rejected operations. Physical acceptance remains NOT TESTED until the user runs it.

## Phase 5.3 — Owner refresh repair and staged Partner deletion

- Baseline 6afbc862ee939bb188f480563399d35ae3e34cfb, dedicated codex/phase53-owner-activity-deletion branch. Normal product candidate 1.0.14/code15.
- Confirmed legacy membership-shape mismatch: notification verification required a redundant workspaceId omitted from the live Owner membership, although normal discovery accepted its verified path. Fixed path-derived identity with explicit mismatch rejection. Added safe diagnostics and partial refresh handling that preserves the last successful checkpoint and saved history.
- Prepared default-off shared Partner job deletion across creators/ages/New/Old Entries. Current financial-child, source-version, membership, outbox and immutable-proof safeguards remain. Operator UI/outbox eligibility now agrees with the already restricted guarded backend.
- Separate firestore.phase53.rules is derived from the active Rules export; only job lock/proof Partner restrictions change. Standard firestore.rules and live Rules are unchanged. See docs/verification/phase53-owner-refresh-and-partner-deletion.md and the adjacent Rules diff for evidence, rollout boundary and phone checklist.
- Local final outcomes:294 unique targeted tests passing across retained runs,0 unresolved failures/skips; proposed Rules15/15 and harness17/17 passed. Signed release assembly and release lint passed with0 errors/81 warnings. Initial failures and fixture corrections are retained in delivery evidence. Source inventory has119 JVM classes/24 backend files without declaration errors.
- First GitHub CI44365486505 on4c45301 passed409 Rules and146 backend tests plus unit/Compose/outbox/build/harness jobs. Database174 tests had one stale Operator-allowed assertion in V16ArchitectureContractTest. Follow-up corrects that assertion and adds positive Partner coverage, preserving ordinary/historical and Operator editing checks. Application code and candidate Rules are unchanged. Fresh complete branch-head CI remains required.
- No physical verification, production financial writes/recovery, live Rules deployment, native push activation, billing change or merge performed.
- Exact next action: verify the fresh follow-up CI; finalize/check the clean signed normal-package code15 APK, certificate, Firebase config and disabled gates; hand over the delivery report and phone checklist; stop for separate Rules rollout approval. The broader Partner deletion gate must remain disabled until the reviewed policy is deployed and an activation build is approved.

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

