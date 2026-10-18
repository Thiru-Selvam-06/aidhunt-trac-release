# Collection History grouped by Job — 2026-10-16

Release worktree: `C:/Users/user/.codex/worktrees/v16-implementation/aidhunt-trac`; branch `codex/v1.6-implementation`; HEAD `0bf772b9dd3a7d7ebfaae7ebcd1e96ca6f1fe141`.

## Implementation

Grouping is a read-only UI-state projection in `CollectionHistoryModel.kt`, not a repository aggregation or data migration. A stable workspace + exact Long Job ID identifies each card. Payment IDs and original PaymentEntity values are retained. Valid multi-Job allocations produce per-Job timeline projections containing only that Job's share, with a small tolerance for Double representation noise. Reversal still targets the entire original Collection and the confirmation explicitly says so.

The header shows Customer, exact Job reference, original creation date in India time, billed, collected and outstanding. Header balances use `FinancialCalculationEngine.calculateSummary` on the scoped Entry, so filters do not redefine financial balances. The header labels them as all-payment Job totals. Events run oldest first, with amount, selected collector, date/time, method and pending-sync state. Existing detail and reversal dialogs remain per payment. Recorded-by audit identity remains separate from the selected collector.

Duplicate payment cache rows are not summed twice. Unknown Job relationships remain separate per Collection; missing source Entries show unavailable balances rather than fabricated zeros. Malformed allocations retain the original Collection and display an explanatory state. Existing active/reversed filters are retained; reversed/deleted statuses are excluded from active totals. Legacy Job-derived summaries remain read-only because they have no independent Payment row to reverse.

No Room schema, DAO, outbox envelope, operation ID, action timestamp, backend, permission or routing change was needed for this grouping. Prior collector and Asia/Kolkata date-validation implementation remains in place, including repository/Firestore transaction validation and local Rules helpers. No Rules were deployed; enforcement by the live deployed Rules has NOT been verified in this task.

## Changed files in this task

- `app/src/main/java/com/example/ui/screens/report/CollectionHistoryModel.kt` (new pure projection).
- `app/src/main/java/com/example/ui/screens/report/CollectionHistoryTab.kt` (group headers and compact payment timeline).
- `app/src/test/java/com/example/CollectionHistoryGroupingTest.kt` (new projection regressions).
- `app/src/test/java/com/example/CollectionFormRegressionTest.kt` (rendered grouping and legacy-policy assertions).
- `app/src/test/java/com/example/CollectionPersistenceTest.kt` (three offline payments retain their group through Room reopen and sync; durable command remains unchanged).
- This report.

## Validation and evidence

Initial full Android unit/Robolectric run: **345 tests, 48 suites, zero failures/errors/skips**, followed by successful `assemblePhysicalTest` (7m51s). This preceded the final decimal-allocation and legacy-read-only refinements; the final focused results below cover those refinements. Counts overlap and must not be added together.

Final clean focused run: **72 tests across 15 suites, 0 failures, 0 errors, 0 skipped**. Includes the final decimal split and legacy-read-only regressions. Final `:app:assemblePhysicalTest` succeeded (clean confirmation invocation: 10m25s).

Commands (PowerShell, from the release worktree):

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assemblePhysicalTest --init-script "$env:TEMP\aidhunt-collection-test-runtime.gradle" --offline --console=plain --max-workers=1
.\gradlew.bat :app:testDebugUnitTest --tests '*Collection*' --tests '*WorkspaceCommandProtocolTest' :app:assemblePhysicalTest --init-script "$env:TEMP\aidhunt-collection-test-runtime.gradle" --offline --console=plain --max-workers=1
```

The reused temporary test init script sets a 1024 MB test JVM heap and forks after 10 classes; it does not change repository build configuration. Initial compile attempt found an unresolved AuthorizationManager reference, which was corrected before the successful full run. An optional Robolectric screenshot-capture attempt subsequently timed out at `captureToImage`; that focused attempt was stopped. The capture hook was removed, all functional UI assertions retained, and a clean focused rerun requested. No valid screenshot is claimed.

Evidence logs in `%TEMP%`: `aidhunt-grouping-validation.log` (initial compile failure), `aidhunt-grouping-validation-final.log` (full run/build), `aidhunt-grouping-full-test-summary.json`, `aidhunt-grouping-focused-final.log` (optional capture failure), `aidhunt-grouping-confirmed-validation.log` (clean focused rerun).

| Scenario | Automated result | Physical/live status |
|---|---|---|
| Three payments 4,000 / 5,000 / 6,000 against one 50,000 Job | PASS — one group, three ordered events; 15,000 collected, 35,000 due; rendered one card with individually actionable events | NOT VERIFIED |
| Later partial payment | PASS — same group key, new event, canonical updated balance | NOT VERIFIED |
| Same Customer, two Jobs | PASS — two distinct exact-Long Job keys | NOT VERIFIED |
| Workspace isolation and duplicate caches | PASS — foreign rows excluded; duplicate Payment IDs not summed | NOT VERIFIED |
| Split allocations, including decimals | PASS — per-Job shares; original Payment identity retained; malformed data retained separately | NOT VERIFIED |
| Reversed/deleted records | PASS — active totals and canonical balances preserved; existing reversed audit UI regression retained | NOT VERIFIED |
| Offline/restart/sync | PASS (Room fixture) — three Room payments remain one group after close/reopen and sync flags; original queued command bytes retained | NOT VERIFIED on phone |
| Collector versus recorder | PASS — selected Partner name distinct from authenticated Owner audit identity | NOT VERIFIED on phone |
| Pre-creation date rejection | PASS — existing CollectionPolicy date regressions included in focused suite | Live deployed Rules NOT VERIFIED |
| Permissions, calculations, protocol | PASS — existing full Android suite plus focused visibility/reversal/protocol tests | Live backend NOT VERIFIED |

## Staging artifact and device gate

APK: `app/build/outputs/apk/physicalTest/app-physicalTest.apk`. Inspected package: `com.aistudio.aidhunttrac.cedlir.physicaltest`, version `1.0.4-physical-test`, versionCode `5`; PHYSICAL_TEST=true; generated Firebase project ID `trac-aidhunt`. No production Android routing was changed.

Final APK SHA-256: `ab19319493d0afd1d25bed3b9d0a4ecfa3eacec825d66e2fa4d8666d7ef0eb4f`; size 32,084,007 bytes.

ADB `devices -l` returned an empty device list on both checks. No connected physical serial, installation, manual taps, Logcat, live fixture mutation, phone Room verification or backend before/after verification is claimed. No Antigravity device result is available in this session.

When the phone is connected, install this isolated APK in place without clearing data. In a disposable staging workspace, create three partial Collections for one Job, verify the single card/timeline and exact stored Collection IDs, add a fourth, compare canonical balances, then test a second Job for the same Customer, permitted reversal, offline/restart and sync. Verify no outbox identity changes and capture actual device evidence. Do not use real business records.

## Preservation

All pre-existing worktree changes are retained; no reset, clean, stash, branch switch, commit, push, membership change, production data write, Rules/Worker deployment or billing change was performed. The Desktop checkout and installed apps were not modified. No live queue was accessed, cleared or retried. Baseline status is recorded in `%TEMP%/aidhunt-grouping-baseline-status.txt`.

Final verification: branch `codex/v1.6-implementation`, HEAD `0bf772b9dd3a7d7ebfaae7ebcd1e96ca6f1fe141` unchanged. Baseline status had 146 entries; final status has 149. Baseline entries no longer present: 0. Final status saved to `%TEMP%/aidhunt-grouping-final-status.txt`. Commands: `git branch --show-current`, `git rev-parse HEAD`, `git status --porcelain=v1`; no Git mutation was executed. This comparison records status preservation, not a claim that intended source edits have identical file hashes.
