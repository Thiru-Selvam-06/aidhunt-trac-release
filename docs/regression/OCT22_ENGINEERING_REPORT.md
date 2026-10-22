# October 22 maintenance — engineering and verification report

Status: development candidate; final regression run still in progress. Not a production release approval. Current scope builds on offline-sync development parent `8fe06c0` and Tamil commit `0f6c529`; main and production records are untouched.

## What failed and why

### Confirmed standalone expense defect

Standalone expense forms correctly store an absent source job as Room `relatedJobId=null`. The old `ExpenseEntity.toFirestoreMap()` also sent an explicit Firestore null. The deployed `retainedExpenseSource()` rule accepts the standalone sentinel numeric zero (or an omitted field using its zero default), or a positive retained source job. An explicit null is neither. Linked job expenses send a positive ID, explaining their different behavior.

The frozen serialization regression failed against the previously compiled application: expected numeric zero, received null. Emulator cases independently denied the null payload and accepted the otherwise identical zero payload for both financial roles and both funding sources. New serialization sends `relatedJobId ?: 0L`; decoding zero restores Room null. Positive 64-bit source IDs, recorded money, actors and dates remain intact.

The three original live expenses (1, 1 and 2) were not individually inspected because their diagnostics were unavailable. This confirms a source defect and synthetic reproduction, not the exact rejection reason of each unseen live operation. Existing rejected payloads are not rewritten or retried.

### Deployed policy comparison

Read-only inspection found that deployed rules matched the pre-candidate local rules, SHA256 `04507f3f22acc160cef4025e72f924ae9478cdf4d5f1d770d95361c4b5974890`. Therefore an old/local collection-rule mismatch is not assumed to explain the current defect. Project edition was verified as Firestore Standard; no edition, billing or production policy was changed.

### Initial collection attribution

The repository previously assigned the initial payment's collector to the authenticated recorder, even when Entry For selected another financial member. The corrected factory resolves the beneficiary's exact active workspace UID for the business collector while retaining the authenticated recorder in collectedBy/creator fields. The parent job's original operation UUID is captured when the new command is created; it is not invented during retry. The initial receipt carries that parent reference and an initial-collection marker.

The approved local rules exception permits this only for an ordinary job created by the authenticated Partner, its selected beneficiary, the original matching job operation/action time, and a previously unpaid balance. Existing financial membership, collector role/name, date, amount and atomic balance checks remain mandatory. General later Partner collections remain self-only. Owner historical-entry authority is preserved. No historical collection or attribution is migrated.

### Payer validation defect found during security review

Four negative emulator assertions originally failed because invalid new expenses were accepted: foreign payer, missing personal payer, unknown funding type, and Business Paid with a personal UID. The approved candidate now validates new funding and changes to funding fields. A personal payer must be an active eligible member in the exact workspace and authorized for the recorder/source-job relationship; Business Paid has no personal UID. The user explicitly confirmed that an Owner may keep recording active Operators as payers, as the existing form permits. Operators gain no expense-creation authority. Creator, beneficiary and actual payer remain separate identities. Unchanged historical funding fields survive ordinary edits; this does not grant permission to create a new malformed expense.

Two existing positive creation fixtures now provide valid funding fields; their access expectations and financial amounts are unchanged. Existing malformed legacy edit fixtures remain unchanged and continue to verify compatibility. The first candidate hit Firestore's 1000-expression budget in two existing compound job/expense cases (295/297 passed). Repeated source lookups and party checks were consolidated for a full retest, without removing authorization checks.

### Tamil presentation defects

See `../TAMIL_LOCALIZATION_FIX.md`. Overview source had reversible UTF-8/Windows-1252 corruption; three Customer Dues labels had already been replaced by literal question marks; date caches did not include language. App text was repaired in source. Entered customer/business/member names, phone numbers, notes, locations and custom work types are preserved. No runtime translator or database text conversion was introduced.

## Data flow and intended boundaries

Room stores local domain rows plus the existing durable outbox. Each command remains bound to original workspace, actor, operation UUID, action time and payload. Existing sync order, dependency protection, WorkManager scheduling, server acknowledgement and conflict checks are retained. New standalone wire encoding corrects the payload contract; it does not reset the queue or treat internet connectivity as proof of persistence.

Confirmed cloud records remain authoritative. Existing rejected financial commands remain retained for explicit review; a blocked dependent receipt is not a successful collection. A stale/current displayed balance cannot prove the original rejected payment amount. Deletion protection around unresolved financial dependencies is not bypassed by this change.

Customer Dues filters source jobs by beneficiary UID before customer aggregation. Creator and collector names are not filter keys. The existing whole-customer collection workflow remains explicit when viewing a filtered due. Shared business costs are an informational allocation, not another expense, personal investment or withdrawal. Existing cash/profit/withdrawal formulas and explicit unequal profit settings are retained; aggregate equal allocation conserves paisa deterministically.

## Changed files and reasons

Paths are relative to the repository. The earlier Tamil-only file list is in its linked report.

| File | Reason |
| --- | --- |
| `app/src/main/java/com/example/data/firebase/FirestoreModels.kt` | Standalone source sentinel/Room round-trip compatibility. |
| `app/src/main/java/com/example/data/repository/WorkspaceRepository.kt` | Correct new initial collector and capture new parent operation UUID; retain actor/audit boundaries. |
| `app/src/main/java/com/example/data/auth/CollectionPolicy.kt` | Resolve eligible initial beneficiary collector by exact UID/workspace. |
| `app/src/main/java/com/example/data/auth/RoleUtils.kt` | UID/workspace-based Entry For display; do not invent a role for missing identity. |
| `app/src/main/java/com/example/data/sync/SyncAuthorizationDiagnostic.kt` | Include retained expense beneficiary/payer/funding/source evidence without tokens/contact details. |
| `app/src/main/java/com/example/ui/util/BusinessRolePresentation.kt` | Display-only Owner→Partner transition with local sticky preference; no authorization change. |
| `app/src/main/java/com/example/ui/screens/home/HomeScreen.kt` | Business/beneficiary display labels. |
| `app/src/main/java/com/example/ui/screens/account/AccountScreen.kt` | Manage-members display labels. |
| `app/src/main/java/com/example/ui/screens/report/BusinessOverviewScreen.kt` | Presentation roles and constrained person-dialog title. |
| `app/src/main/java/com/example/ui/screens/report/CollectionHistoryTab.kt` | Compact Entry For label; payment timeline retained. |
| `app/src/main/java/com/example/ui/screens/report/CustomerDuesPartnerFilter.kt` | Active financial member choices and scoped aggregation helper. |
| `app/src/main/java/com/example/ui/screens/report/CustomerCreditDueTab.kt` | UID filter, matching summaries/details, explicit full-account collection workflow. |
| `app/src/main/java/com/example/ui/screens/report/ExpensesTab.kt` | Preserve entered names containing parentheses; select by UID; localized role label. |
| `app/src/main/java/com/example/ui/screens/report/WithdrawalTab.kt` | Display roles/Tamil labels and informational shared business-cost row. |
| `app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt` | Expose existing equal shared-cost allocation as a separate readonly result. |
| `firestore.rules` | Approved initial-collector exception and expense funding validation; local prototype only. |
| `security-tests/architecture.test.cjs` | Independent sentinel, identity, financial authorization and compatibility regressions. |
| `app/src/test/java/com/example/StandaloneExpenseWireTest.kt` | Old null failure/new zero contract, original fields and 64-bit round trip. |
| `app/src/test/java/com/example/BusinessMaintenancePolicyTest.kt` | Frozen role/UID/filter/cash/investment/paisa/unequal-share expectations. |
| `app/src/test/java/com/example/BusinessPerformanceTest.kt` | Synthetic calculation latency/heap baseline with independent totals. |
| `app/src/test/java/com/example/TamilLocalizationUiTest.kt` | Keep metrics/language assertions in Robolectric; approved full dialog journey remains mandatory Android coverage. |
| `app/src/androidTest/java/com/example/TamilLocalizationDeviceTest.kt` | Mandatory person journey and filtered-dues/full-account collection UI assertions. |
| `app/src/androidTest/java/com/example/DeviceEmulatorIntegrationTest.kt` | Actual repository/Room/outbox/Firestore expense, initial collector and zero-receipt journeys. |
| `.github/workflows/android-ci.yml`, `scripts/performance-tests.gradle` | Reuse mandatory CI; separate manual synthetic performance tiers. No deploy job. |
| `docs/regression/interaction-inventory.json`, `.csv`, `asset-inventory.json` | Regenerate source inventory after added controls; not a claim of runtime coverage. |
| `docs/regression/OCT22_EXPECTATIONS.md` | Freeze independent acceptance expectations and original live-evidence limits. |
| This report, physical checklist and security assessment | Evidence, risks, safe recovery and review record. |

## Verification record

| Environment / revision | Actual outcome |
| --- | --- |
| Local Python safety/inventory/localization checks | 110 passed after selecting writable workspace temp. Initial sandbox-default-temp invocation failed with permission errors; not an application assertion failure. |
| Published Tamil commit `0f6c529`, GitHub run `39755775953` | Compilation/lint, unit, Room, outbox, backend, rules and Android instrumentation passed. Compose failed; aggregate correctly failed. |
| Same run, Android emulator instrumentation | 19 passed, zero skipped, including full Tamil person-filter dialog. This was not a physical phone. |
| Same run, Robolectric Compose | 57 executed, 56 passed, one AppNotIdleException in Tamil person-dialog journey on Windows and Linux. User approved keeping that full journey/assertions mandatory on Android; metric/language checks remain in Robolectric. No ignore/optional gate added. |
| Local business policy tests before final candidate | 7 passed, zero failed/skipped. This is not final all-suite evidence. |
| Local initial-collector rules candidate before payer hardening | 281 passed, zero failed/skipped. Earlier candidate had two expression-budget failures, repaired before that rerun. |
| Payer-hardening baseline | Four negative cases failed because invalid writes were accepted, proving the validation gap. |
| First payer-hardening candidate | 297 executed, 295 passed, two existing linked compound writes failed expression budget. |
| Optimized payer-hardening candidate, full local rules suite | 297 passed, zero failures/cancellations/skips. 166.788 seconds. Includes existing compound-write and historical compatibility assertions. |
| Current selected Kotlin/JUnit/Robolectric run | 21 passed, zero failures/skips: business policies 7, performance 1, expense wire 2, Tamil UI 9, entered-data preservation 2. Build successful in 13m39s. |
| Final local Rules after retaining active Operator payer compatibility | 298 passed, zero failures/skips; 190.041 seconds. |
| Final local backend | 129 passed, zero failures/skips; 75.819 seconds. |
| Full local Android JVM/Room/Compose/outbox | 640 passed across 84 classes, zero failures/errors/skips; build successful in 5m17s. Demo configuration restored byte-for-byte afterward. |
| GitHub run `39781274614`, commit `404bc97` | Inventory, build/lint, all four JVM partitions, backend and rules passed. Android instrumentation ran 23 journeys with one failure: new dues test attempted a non-composed off-screen LazyRow chip with performScrollTo. The aggregate correctly failed. Parent-list navigation corrected; all original amount/identity assertions retained. Full retest pending. |
| Manual physical-device scenarios | NOT TESTED. See separate checklist. |
| Automated physical-device/ADB scenarios | NOT TESTED. No emulator result is represented as hardware evidence. |

The Windows machine lacks enabled Android x86 hardware acceleration. Existing GitHub Actions Android emulators provide the mandatory automated device environment. No driver, Windows security, hypervisor, billing or production configuration was changed. Existing test gates remain mandatory; source inventory counts are not UI pass counts.

## Tools, additional services and performance limits

Reused Kotlin/JUnit, Robolectric, Room SQLite, Firebase Emulator/Rules tests, existing Android instrumentation and GitHub Actions. Native Git UI is used only for normal development publication where the Windows command-line credential helper cannot run. No Sentry, new monitoring service, translation service, paid testing tool, database or dependency was added.

The added performance test uses existing JUnit and Gradle, costs no new service, and requires only test/CI code. It runs isolated synthetic data with fixed 1100 billed/900 received/200 due per job. All five local tiers passed on Windows 11 / Java 21.0.12.1:

| Jobs / 100 synthetic customers | Summary ms | Beneficiary filter ms | Observed JVM heap bytes |
| --- | ---: | ---: | ---: |
| 100 | 1.626 | 1.616 | 31,239,264 |
| 1,000 | 4.915 | 7.058 | 32,277,640 |
| 10,000 | 34.377 | 48.043 | 25,209,488 |
| 50,000 | 89.882 | 137.021 | 48,280,352 |
| 100,000 | 96.530 | 150.783 | 69,686,520 |

These are one synthetic JVM run, with garbage collection affecting observed heap; they are not Android frame time, CPU, startup, battery, physical-device memory or an application capacity guarantee. Android performance remains unmeasured. There is no invented timing pass threshold.

## Risks, recovery and release boundary

The sticky Owner→Partner label is retained on installations that observed an active Partner. A fresh install after all Partners were removed cannot reconstruct that historical fact from a local preference alone. No new shared historical flag or production migration is included; cross-install historical presentation remains unresolved and must not be claimed complete.

Publication: development commits `27dd45b` and `404bc97` were pushed and verified in Git's normal authenticated UI and the public GitHub comparison. Main was not changed. PR creation through the connector returns 403 (integration access). An attempted native-browser route was stopped by automatic approval review because the available Comet window contained an unrelated private spreadsheet. A separate GitHub-only tab shows the correct base/head comparison but is signed out. No PR has been created yet; browser sign-in on that dedicated tab or a connector with PR write access is required. No unrelated private sheet was read or changed.

Old rejected actions remain byte-for-byte retained. Before any live recovery, obtain explicit approval, back up the original operation/related rows privately, inspect authenticated actor and current verified membership, inspect exact authoritative server state and operation UUID, and establish whether any write already committed. Validate the original payload against the deployed policy in isolation. A null legacy source must not be silently rewritten/replayed; resolution needs a separate explicit recovery design preserving its original audit. Resolve the parent before authorizing its blocked receipt. If already committed, reconcile acknowledgement rather than creating another financial event. Ambiguous actor/amount/history stays under review. None of this live procedure was executed.

Review and rerun all required checks on the final commit; verify the manual physical checklist and staging endpoint/build; review the prototype rules and rollback target. Production rules/backend/APK deployment, merge to main, live queue recovery and customer distribution require separate explicit approval. Do not overwrite current local databases or uninstall the live app for rollback.

I've set up prototype Security Rules to keep the data in Firestore safe. They are designed to be secure by preserving authenticated actor/workspace boundaries, validating eligible payer identity, and limiting the new collector exception to the original initial receipt with atomic balances. However, you should review and verify them before broadly sharing your app. If you'd like, I can help you harden these rules. These rules remain local and have not been deployed.
