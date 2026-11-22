# Owner-confirmed legacy receipt metadata and cash notification repair

## Applied financial correction —22 November2026

The user authorized exactly seven existing received portions totaling16900, confirmed Cash and Muniyappan as collector, and confirmed the original TRAC recording timestamps as actual collection times. For the overnight/upload-delayed cases, the chosen dates are6October and12October. This confirmation is recorded as user-supplied evidence; no independent bank/cashbook verification is claimed.

A separate manual+4500 cash adjustment was discovered, with a reason explicitly covering the same unknown-method amount. The user reversed it in the app; the agent verified revision6/history6/cumulative zero before performing the receipt correction. The agent did not delete or create any manual cash adjustment.

Seven synthetic Firebase Emulator rehearsal cases passed: exact seven-receipt conversion with preserved source/audits, retry idempotency, stale-source atomic rejection, nonzero-cash-adjustment rejection, new-receipt duplicate prevention, inactive Owner rejection, original timestamp preservation and cross-workspace rejection (the successful case also verifies replay and exact63-bit IDs). No financial test data was written to the live workspace.

The approved production transaction then created only seven canonical Cash receipts plus one protected confirmation audit. No existing financial document was updated or deleted. It used deterministic receipt IDs, create-only preconditions, original-source update-time checks, exact residual allocation checks, active original Owner identity and reconciled zero cash-adjustment history. A linked900 receipt dated9November was retained exactly; only its separate800 legacy remainder was normalized. The receipt documents carry historical provenance and user-confirmed dates; they do not claim to be newly collected income or newly authenticated client operations.

Fresh authoritative read-back and amount-level recalculation verified:

| Measure | Before correction | After correction |
|---|---:|---:|
| Total received |180100|180100|
| Dated collections |163200|180100|
| Undated legacy received |16900|0|
| Cash-classified receipts |175600|180100|
| Expenses |161670|161670|
| Withdrawals |12643.33|12643.33|
| Net Balance before withdrawals |18430|18430|
| Cash after the user's separate reversal |1286.67|5786.67|
| October dated collections |77100|94000|
| November dated collections |86100|86100|

All266 original job documents, all94 pre-existing receipts, all26 expenses, the withdrawal, membership and deletion proofs were byte-identical in before/after checks. Total recorded1060816 and dues880716 were unchanged. Those current totals include a job already present before this correction; they must not be mistaken for changes made by this transaction. Private before/after backups, exact plan, execution/read-back, raw dates, original job IDs and new receipt IDs remain outside Git under protected-release/legacy-confirmation-20261122. The private OWNER-CORRECTED-RECEIPTS.md is the detailed user register. The original21November evidence is unchanged.

## Cash adjustment notifications — current development

Root cause confirmed: OwnerVisitCloud never queried cashBalanceAdjustments, and OwnerVisitActivity had no supported event for it. The user chose to preserve the original recipient policy: confirmed edits by active Partners notify the original workspace Owner; own Owner actions stay in cash audit history, other Partners do not receive this Owner feed.

Implemented a capability-guarded immutable cash-history reader using the server confirmedAt Timestamp, strict event identity/arithmetic/reason validation and stable operation-scoped notification IDs. Reads are verified under the actual Owner membership and workspace. New event uses existing Room notification fields/metadata, so no database migration, financial projection or new service is needed. EN/TA presentation shows Partner, original before/after cash, original reason and server-confirmed IST time. Customer/member names and user text are not translated. Native FCM remains disabled.

Extended existing activity, emulator and Room tests for confirmation-only visibility, old phone action time, server Timestamp query types, inactive rollout, original Owner routing/no self-alert, idempotent refresh, tampered arithmetic/type/time/identity rejection, bilingual reason preservation and cached read-state/privacy.

Focused Debug host/Firestore integration tests are running against the exact deployed compatibility Rules, SHA90eb895bc114a1e22ab58248c339e49e422c598c94b0e9c60d0e5b6fb54931c2, using demo-aidhunt-v16 only. No runtime pass is claimed yet. The first emulator-launch command failed from shell argument quoting before tests; the corrected file-based command retains all assertions. New package version1.0.18/code19 is reserved for this notification fix. It has not been packaged or delivered yet.

No additional production Rules, capabilities, billing, backend or unrelated financial data changed during notification implementation. Git publication remains blocked by credentials/connector403. Full release CI and physical verification remain separate gates. Cash pilot remains in its user-approved state; no automatic live financial reversal or rejected-operation recovery was performed.

## First notification test run and focused correction

First actual focused run:40 tests,37 passing/3 failing,0 skips. Two unit cases required Android JSONObject's real implementation; the same assertions now run in the existing Robolectric36 runtime without any new library. The actual Rules/Firestore integration case found that FirestoreValueCodec preserves a lossless private Timestamp wrapper for financial mutations. The new history parser needed a Long timestamp. OwnerVisitCloud now explicitly converts only confirmedAt's timestampValue for notification presentation; no shared financial timestamp codec is changed. All first-failure XML/logs are preserved. A retry is running against the exact deployed compatibility policy; no pass is claimed yet. Inventory checks passed unchanged (1103 controls/123 host classes/26 backend files/9 runnable Android classes/3 pre-existing blocked,0 errors).

## Final focused verification — PASS

All40 selected Debug tests passed with0 failures/errors/skips in the final run. Classes: OwnerVisitActivity7, OwnerVisitCloudEmulator2, OwnerVisitRoom1, OwnerVisitCompose2, CashBalanceAdjustment9, FinancialReconciliation11, WorkspaceSyncCapabilities6, Phase53Activation2. The emulator loaded the exact deployed compatibility Rules. It verified a real authorized paired cash write, server Timestamp range query/decoding, one Owner event on repeated refresh, no Owner self-alert, and no cash query when the capability is off. Room reopen/read-state/privacy and EN/TA original reasons passed. This is host/Robolectric plus Firebase Emulator evidence, not physical-device or Android-instrumentation evidence.

The second run had39 passing/1 failing case: a test compared JSON's escaped serialized slash with unescaped text. The final assertion parses the reason and checks exact original user text; it preserves the business expectation rather than depending on JSON escape formatting. No production parser change or assertion removal was used for that correction. All initial failure logs/XML are retained. The final40 pass is a complete run, not a sum of partial runs.

Signed Release1.0.18/code19 is now being built with the original protected key/configuration,41 relevant release-variant tests, assembly and release lint. No new APK pass/delivery is claimed until artifact inspection succeeds. Inventory/source review passed; no production Rules, billing, backend, Room schema, financial formulas or unrelated UI changes were made for this notification fix.

Changed source: AppNotificationEntity event constant; OwnerVisitCloud masked confirmed-time query/projection; OwnerVisitActivity validation/routing/dedupe; NotificationPresenter bilingual cash details. Changed tests: existing OwnerVisitActivity/CloudEmulator/Room coverage retained and extended. app/build.gradle.kts carries the higher-version update; PROGRESS and this report record actual evidence. No new dependencies/services. Private repair code and all real IDs/evidence remain outside Git.

## Verified signed1.0.18 test candidate

Original-key signed1.0.18/code19 is ready for controlled testing. Package com.aistudio.aidhunttrac.cedlir, non-debuggable, original Firebase project/app/API resources verified. APK v2 signature matches the original1.0.15 certificate. The PMS launcher still contains the original JPEG bytes, manifest icon references verified. New cash activity marker and reader are in DEX; test-only components are absent. Native push/pilot/test flags remain false; existing shared Partner job deletion remains enabled. No signing secret/configuration was committed.

Actual Release results:41 tests PASS/0 failures/errors/skips (Activation2, OwnerVisitActivity7, CashAdjustment9, Reconciliation11, Capabilities6, Withdrawals6). Signed assembly and lint passed in8m17s; lint0 errors/120 prior warnings. These are repeated variant checks and must not be added to40 Debug as unique scenarios. Final APK is 23966762 bytes, SHA-256 `e2b935d9d5db6068e0792b19fc2065616d24287cb5729fe77738af9778e56ad9`. Evidence in outputs/cash-notifications-1018/apk-verification.json, source-manifest.json, host-results and release log; raw failed runs retained. Localization2 checks, source inventories and changed-file publication scan passed,0 findings.

Install as an update without clearing/uninstalling existing TRAC. Owner should open the notification history and refresh. Partner-confirmed cash adjustments produce Owner-only in-app events with before/after values, original reason and IST time. Original Owner's own adjustments remain cash audit entries and do not self-notify, as explicitly requested. Previous Partner activity can appear in history; Since Last Visit reflects its actual confirmation boundary. A fresh manual notification check can use the already-approved paused-activity test: a Partner records a small difference and reverses the exact same difference, then Owner refreshes. Do not invent an extra receipt or new16900/4500 collection.

Physical notification acceptance and full GitHub release CI are NOT TESTED / NOT RUN. No source push is claimed: existing Git credentials and connector write access remain blocked. A development commit preserves the source; no production merge or general customer release. Partner empty-customer profile deletion remains its separate inactive backend gate; this change does not activate paid billing, native FCM, loans, borrowing or settlements.

The seven financial corrections are already live and can be seen with the previous APK after authoritative sync. This new APK is needed for the notification reader only. No further financial change was made while packaging. Corrected records are user-confirmed; original21November evidence is retained as a before-state, not rewritten. Private full register: protected-release/legacy-confirmation-20261122/OWNER-CORRECTED-RECEIPTS.md.
