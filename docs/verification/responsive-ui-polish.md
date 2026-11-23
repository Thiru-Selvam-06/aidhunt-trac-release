# Responsive reports and full-page cash editor — TRAC 1.0.19

Status: IMPLEMENTED AND TARGETED AUTOMATION VERIFIED. Original-key signed 1.0.19 UI test candidate prepared. Physical-phone acceptance remains NOT TESTED; this is not a claim that the comprehensive customer-release gate has run.

Baseline: `924a6d00f2ea90ee533fa7eaf6808e670073fda6`. Development branch: `codex/responsive-ui-polish`. Version:1.0.19/code20. GitHub publishing remains deferred by the user; no production merge/deployment is part of this change.

## What changed and why

- Cash editing now occupies normal app Scaffold content, with the existing header/back behavior, scrolling audit history and pinned Save/Close actions. It no longer opens an Android dialog. The original tab/report remains selected when closing. Back and Close are blocked during an in-flight save, and bottom navigation is hidden while editing; existing header sync/profile actions are retained. Required reason, original logical operation UUID, stale-base/revision check, online/server confirmation, active role/workspace checks, validation errors and immutable history remain. Amount/reason/original UUID survive screen recreation.
- Withdrawal summary previously put a fixed icon beside a number in each half-width card. Its partner cards put Share, Investment and Total to get into three competing columns, including unweighted Taken/Remaining subrows. Long labels could be squeezed to nearly zero width, producing split words/numbers and very tall blank-looking sections. Shared responsive metrics now give amounts their own line; share/investment are separate sections with a full-width authorized action. Legends wrap below headings; the donut stacks on compact or larger-text screens. Values, percentages, entitlement formulas and own-card permissions are unchanged.
- Collection cards use the same16dp corner/spacing/border treatment, responsive financial totals, separate payment status/amount lines, wrapping method/date metadata and48dp expand/reversal controls. Grouping, receipt dates, filters, sorting, reversal permissions and original customer/member/note text are unchanged.
- Expense amount/date occupy the complete card width, below the category/description/menu row. The48dp action target remains reachable; all original view/edit/share/delete permissions and callbacks are preserved. Summary metrics reflow rather than squeezing three cards into one phone-width row.
- Business Overview removes forced summary heights and the competing amount/subtitle row. Its card pairs adapt to available width and text scale. Available balance still uses the existing financial summary and shared formatter.
- Balance Sheet had equivalent unweighted two/three-column amounts. Summary and period totals now use the same responsive metrics; period/PDF controls can wrap. All receipt/expense/date-scope/PDF projections and mathematical formulas are unchanged.
- Shared filter headings reserve label width and use normal touch targets. Report bottom padding avoids duplicating the surrounding navigation inset. Existing app palette and Home/Customer Dues design were used as references. Source review also covered report routing, Account and New Entry; unrelated working forms/illustrations were retained.
- Motion is limited to160ms tab crossfades and180ms progress changes, plus existing expansion animations. Monetary amounts do not count or animate through temporary values.

## Thousands separator

The source formatter already explicitly used `Locale.US`; it does not choose thousands punctuation from the phone locale. Comma is the thousands separator and period is the paise separator: `₹5,786.67`, `₹12,643.33`. Do not turn the paise separator into a comma or alter numeric values. Added exact checks under US, German, Tamil/India and English/India locales and an actual Android overview fixture based on synthetic receipts/withdrawals. The reported dot-for-thousands was not reproduced in the current formatter; the new layout prevents splitting/crowding the whole formatted amount.

## Modified files

| File | Purpose |
|---|---|
| `app/build.gradle.kts` | Candidate version only:1.0.19/code20. |
| `app/src/main/java/com/example/MainActivity.kt` | Inline cash page, parent header/back, save navigation guard, retained origin, shorter tab transition. |
| `ui/components/CashBalanceEditor.kt` | Full page, lazy history and pinned actions; preserve every cash safeguard. |
| `ui/components/ReportLayout.kt` | New reusable grid/metrics and wrapping section heading using actual width/text scale. |
| `ui/components/CommonComponents.kt` | Responsive donut legend/stacking and common filter heading/touch targets. |
| `ui/screens/report/WithdrawalTab.kt` | Summary/cards/legends/action layout and restrained progress motion. |
| `ui/screens/report/CollectionHistoryTab.kt` | Responsive cards/totals/payment metadata and reachable actions. |
| `ui/screens/report/ExpensesTab.kt` | Responsive summaries/full-width amounts/action alignment. |
| `ui/screens/report/BusinessOverviewScreen.kt` | Adaptive summary/action cards; no fixed financial card height. |
| `ui/screens/report/BalanceSheetTab.kt` | Responsive totals and wrapping period/export controls only. |
| `app/src/test/java/com/example/ReportResponsiveUiTest.kt` | Real font layout/whole amount bounds, scale/width, card permissions and original user text. |
| `app/src/test/java/com/example/ReportCurrencyFormatTest.kt` | Locale-independent grouping and exact paise. |
| `app/src/androidTest/java/com/example/ReportLayoutDeviceTest.kt` | Synthetic actual-Android report/page journeys and screenshots. |
| `app/src/test/java/com/example/BusinessOverviewCircleUiTest.kt` | Register the existing isolated Compose test application for release-host tests; every original assertion remains. |
| `app/src/androidTest/java/com/example/CashBalanceDeviceTest.kt` | Retain all original safety assertions; scroll the now-lazy history. |
| `scripts/instrumentation-plan.json` | New Android class is mandatory, not optional. Existing cases retained. |
| `docs/regression/*inventory*`, `PROGRESS.md`, this report | Current source catalog, actual evidence, remaining steps and handoff. Vendored dependency tests are excluded from the source asset catalog. |

UI paths in this table are under `app/src/main/java/com/example/` unless otherwise specified. No financial entity/DAO/repository, sync engine, Rules, backend, workspace capability or Firebase configuration changed.

## Verification evidence

- Broad focused host run:59 executed,58 passed/1 newly introduced layout failure. Original Tamil9, overview5 and related financial/cash/collection tests passed. The remaining assertion showed a partner amount received only10px. Fixed the manual width allocation using ReportGrid, without weakening the assertion.
- Final affected Debug host run:13 passed,0 failures/errors/skips (native-font layout4, locale formatting1, withdrawal navigation2, partner profit/share6). Native font rendering is used for visual bounds.
- Debug assembly/lint succeeded8m39s:0 errors/fatal,120 warnings, same count as the baseline. These Debug results preceded only the final Balance Sheet presentation addition; release/native gates must cover that addition.
- Initial compile/import and FlowRow intrinsic-measurement failures were retained and corrected. BoxWithConstraints children cannot be measured through FlowRow intrinsic queries; a normal measured grid replaces that path. No test was disabled, assertion removed, or financial expected result changed. Original Tamil tests remain unchanged; the overview host suite only adds its existing test-application registration.
- Final affected release host check:7 responsive/native-font/restoration tests passed,0 failures/errors/skips. The preceding full focused release run passed23 tests; its17 unaffected cases plus the latest7 produce24 distinct release checks across Balance Sheet, overview, Tamil, formatting and responsive layout. Across the staged focused runs,64 distinct host tests in11 classes passed. This is a targeted aggregate, not one execution of the entire project suite. Unchanged broader suites were not repeatedly rerun.
- Final signed release assembly and lint succeeded:0 errors/fatal,120 existing warnings (same count as baseline). Debug assembly/lint also passed. The first release test-host run had5 failures because ComponentActivity was not registered in the release test application; the existing isolated LedgerUiTestApplication registration fixed the host setup without changing assertions. Release/native checks cover the final Balance Sheet addition and final action layout.
- Actual Android API36 synthetic emulator:39 PASS,0 failures:320dp/font1.3,412dp/font1.0,600dp/font1.0,13 tests/profile (6 original cash safeguards and7 report/page journeys). Final screenshots exposed compact button/expense-heading wrapping, corrected before the final7 release checks, final APK and these39 emulator checks. Native-font assertions verify complete labels and whole paise values. Default Compose prefetch/scroll/rendering remains enabled.
- Two initial cold instrumentation launches failed before tests. Captured exit-info records process-startup ANRs; the system also recorded startup ANRs for system apps and emulator Bluetooth HAL failures. No TRAC Java/native exception was established. On the isolated warmed/AOT-compiled test AVD, with its crashing Bluetooth service disabled, the same final app passed all39 checks. The exact causal link between emulator service failures and startup ANR is not proven. No physical device or production app was changed.
- Visual review accepted compact cash/withdrawal/overview, standard collection/expenses, and wide partner/Balance Sheet images. Two supplemental overview-only executions also passed. The standard retake is valid; the wide retake again captured an empty platform transition frame and is explicitly NOT accepted as visual evidence. Wide overview semantic assertions passed, but that particular screenshot remains a capture limitation. Only window/activity transitions were disabled for those two supplemental captures; Compose animator scale stayed unchanged and settings were restored. Supplemental runs are separate from the39-check total.
- Localization/publication/inventory unit checks:11 passed. Current source inventory:1104 interaction candidates,125 host classes,26 backend files,10 mandatory runnable Android classes,3 pre-existing blocked Android classes,0 inventory errors. Exact staged-index publication guard and diff check are recorded with the final local commit checkpoint.
- Physical automated device tests: NOT TESTED. Manual physical-device tests: NOT TESTED. GitHub Actions for this branch: NOT RUN (publishing deferred).

Local logs, XML, source/artifact hashes and synthetic screenshots are retained in the host `outputs/responsive-ui-evidence` directory. They are local evidence, not claimed as GitHub CI results.

## Signed artifact and next steps

- File: `outputs/AIDHUNT-TRAC-1.0.19-UI-Test-Candidate.apk` in the workspace root outside this Git worktree;23,983,146 bytes.
- SHA256: `8052d0077e939e26dc4bc527a3896196e4a1129fac8a1547d55a6f4566552038`.
- Package: `com.aistudio.aidhunttrac.cedlir`; version1.0.19/code20; non-debuggable; v2 signature verified and certificate matched the previous1.0.18 product. Original PMS artwork and existing protected Firebase resource configuration matched. Synthetic test-only components are excluded; test/pilot/native-push flags remain false.
-343 frozen source/build/test file hashes were verified against the final build. No signing key, signing passwords or protected Firebase configuration is included in this change.
- Install as an update; do not uninstall or clear storage. Run the phone checklist below before customer distribution. No new financial write is needed to accept these UI changes. GitHub publishing is deferred until the user resumes authentication; no GitHub Actions result is claimed.
- UI rollback: revert this UI commit and build a new, higher-version-code update with the original key. Do not uninstall, clear data, or attempt a destructive downgrade. This change adds no migration.

## Read-only phone acceptance checklist

All scenarios below remain NOT TESTED until the Owner actually performs them; none requires USB.

1. Install the original-key candidate as an update, without uninstalling or clearing app storage. Verify the existing account/workspace and pending operations remain present.
2. Open Adjust cash/history from Home and Business Overview. Verify it is a normal page, header Back/Close return to the originating page, and history shows original reasons, people, amounts and IST times. Fill fields and cancel to review layout without changing real balances.
3. With the keyboard open, verify amount/reason remain reachable and Save/Close stay usable. Blank reason, offline/unconfirmed/stale state must not permit saving. Actual writes require the existing coordinated live-test policy; no new financial test is required for UI acceptance.
4. At normal and larger Android text sizes, inspect withdrawal cash/total, each partner Share/Investment/Taken/Remaining, the authorized Get action, distribution, allocation legend and past ledger. Check values against the previous version; no amount or entitlement should change.
5. Expand Collection History on long customer/member names; inspect full amount, Cash/method/date, attribution, filters and reversal action. Do not reverse a real receipt merely to test appearance.
6. Open Expenses with long descriptions, inspect total/count/average, full amount/date and view/edit/share menu. Confirm other-member/unauthorized delete remains unavailable.
7. Check Business Overview and Balance Sheet in English and Tamil, period filters and PDF controls. Confirm comma thousands/decimal paise and unchanged confirmed totals. Original customer/business/member text and notes must remain as entered.
8. Repeat in portrait/landscape and a second phone size if available. Confirm bottom navigation, scrolling, keyboard and subtle transitions remain responsive; record model, Android version, width/text size, screenshot and result for each.

## Safety and boundaries

Production financial records/pending operations changed: **No**. Firebase Rules/backend/configuration/billing deployed or changed: **No**. Financial formulas/attribution/method/date evidence changed: **No**. Original PMS artwork/package/signing identity must match the prior product in the artifact gate. No database migration or added dependency. No live receipt repair repeated. Native FCM remains as previously configured; this UI work does not activate it. No production merge or customer-release claim.
