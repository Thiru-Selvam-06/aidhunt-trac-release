# Workspace chooser and Business Overview chart — 2026-10-18

**Local fixes implemented; physical retest NOT VERIFIED. Release gate remains NOT READY.** This pass addresses the two reported UI/chart issues, without backend/data/authorization changes.

## Repository and preservation

Root `C:/Users/user/.codex/worktrees/v16-implementation/aidhunt-trac`; branch `codex/comprehensive-regression-2026-10-17`; HEAD `937088dff3115d4e995dbf6d1dfbed1158ccbe8d`. No commit, push, staging, branch change, reset, stash or Desktop edit. Pre-edit copies/hashes, this pass's diff and final Git status are under [evidence](test-artifacts/workspace-chart-fix-2026-10-18/). All prior uncommitted changes remain. The comparison against the preflight's478 protected dirty/untracked files changes only two intentionally edited files (the chooser and its existing test); the chart and engine were clean tracked files outside that dirty-file list and have separate pre-edit copies/hashes in this run. New tests/reports are additional files. No original file is missing.

## Confirmed paths and corrections

1. `WorkspaceRecoveryScreen.kt`: chooser was a single unbounded plain column with differently sized action buttons. Add safe screen/keyboard insets, a centered maximum-width scrollable column, consistently padded full-width workspace cards and aligned recovery/create/sign-out controls. Preserve the exact workspace-selection callback, workspace IDs, role text, create conditions and recovery actions.
2. `FinancialCalculationEngine.kt`: the breakdown mapped every financial attribution UID absent from the supplied member list to an empty UID. This collapses distinct explicitly attributed records into the same `Unattributed (migration required)` row even when the identity is present but membership display metadata is unavailable. Add an explicitly selected chart-presentation mode retaining recorded UIDs. Use the current workspace's UID-bound cached Partner name when no resolved member name is available; ambiguous or foreign cached names are not chosen. Missing member metadata is labelled `Member details unavailable`; truly missing attribution is labelled `Attribution unavailable`. No role is invented for an unresolved UID. Default non-chart member-mapping behavior is preserved.
3. `BusinessOverviewScreen.kt`: opt into retained-UID presentation, bind financial scope and job filtering to settings.workspaceId, reset person selection on workspace changes and include unresolved-but-explicit UIDs in the person selector. Display exact collected/due values beneath the circle, with room for wrapping names, rather than requiring a details tap. Existing financial sums/formulas remain in the same calculation engine.

Identity semantics remain unchanged: record beneficiary/financialAttributionUid (falling back to entryForUid) determines the person's job balance. Authenticated creator is a separate audit identity. An Owner entering a Partner's job does not acquire that Partner's revenue or due. `Collected` on this earnings circle means received against those attributed jobs; it does not establish who physically collected the payment. Collection History retains separate collector/recorded-by identities. No attribution is inferred merely from a matching display name or the current login.

**Important unresolved data limit:** records genuinely missing beneficiary IDs cannot safely be assigned to named people by this UI fix. Their amounts stay visible without asserting migration is definitely required. Current PMS rows were not exported or rewritten; the actual cause for each unnamed production amount remains unverified. A scoped, authorized identity/data reconciliation is needed before any proposed production repair, which requires separate approval. This is not a claim that all historical circles will immediately have names.

## Current-run regression and build evidence

Final command: `gradlew.bat :app:testDebugUnitTest :app:assembleDebug --init-script scripts/ci-tests.gradle --offline --console=plain --max-workers=1 --no-daemon` with JDK21,1GB Gradle heap/SerialGC/2 active CPUs. **Exit0;498 tests in64 suites;0 failures,0 errors,0 JUnit skips.** Gradle's Roborazzi finalizer was skipped; that is not a skipped JUnit assertion. Full JUnit copies, method results and log: [test-summary](test-artifacts/workspace-chart-fix-2026-10-18/test-summary.json), [final log](test-artifacts/workspace-chart-fix-2026-10-18/final-tests-and-build.log).

New regression coverage:

- BusinessOverviewAttributionTest8: distinct retained UIDs despite missing membership details; same-workspace UID-bound cached names without granting role; Owner recorder versus Partner beneficiary; no guess from creator/name when beneficiary absent; colliding foreign records/name exclusion; ambiguous cached names; stable identity and exact recorded/received/due amounts across member refresh; unchanged default member-mapping behavior.
- BusinessOverviewCircleUiTest2: actual circle component displays the correct member, collected40 and due60 without a details tap; missing display metadata retains its explicit UID, shows due100 and does not claim migration is required.
- WorkspacePickerUiTest2 new,5 total:320dp-screen long-name wrapping/clickability and equal card/retry widths; retry/sign-out actions. Existing workspace-ID/role and create-flow assertions retained, with scroll-to interaction for the taller card layout.
- Existing BusinessOverviewChartMembersTest4 and V16ArchitectureContractTest10 passed, as did the full affected Android suite.

These are local JVM/Robolectric/Compose tests using synthetic data, not physical-device or deployed-service proof. The first build attempt overlapped the last chart edit and failed test compilation against the previously private component. It is preserved as intermediate evidence; the final completed frozen-source rerun is the accepted result. No assertions were removed or weakened. No backend/Rules tests were rerun because their sources/contracts were unchanged.

Standard debug APK: `app/build/outputs/apk/debug/app-debug.apk`, package `com.aistudio.aidhunttrac.cedlir`,1.0.4/code5,projecttrac-aidhunt. SHA256 `be5da348bea6fe3aa9da3f3af67fc300396880bdd86afbe0d1895551707f4fac`. APK signature verification exit0; debug certificate matches the installed preflight candidate. Source/API/build/signature hashes and commands are in the run directory. No release-signing/rollout claim.

## Actual phone observation and installation boundary

DeviceI2304,serialending006H. User navigates manually; agent only reads accessibility metadata and redacted Logcat. No automatic taps, Retry, Sync, private Room inspection, clear-data or queue changes. Raw UI hierarchy was processed in memory and discarded; evidence contains only diagnostic labels/counts and package identity, not phone/OTP/token values.

The latest diagnostics capture at09:18:16UTC (14:48:16India time) confirms the **standard** package, `1 Pending`, `Pending synchronization · Retryable`, `Network connected`, and `An action could not sync; check pending actions`. It does not expose an authoritative acknowledged/rejected count or the precise pending command. Earlier captures showed Home rather than the requested diagnostics panel; they are preserved and not treated as queue evidence.

User was asked to turn off Wi-Fi/mobile data and not press Retry/Sync. No connectivity setting was changed by the agent. Current pending work remains unresolved. **New APK not installed over the populated app**: the task's queue/data-safety boundary requires a safe authorized path before update/online relaunch. Signature equality and unchanged Room schema19 alone do not prove preservation or authorize replay. Physical validation of the new chooser/chart is NOT VERIFIED. Previous preflight installation is a different APK and is not misreported as this update.

The redacted log monitor remains UID-scoped to standard-app UID10136, including process restarts. Network unavailable/name-resolution errors were seen earlier; these do not prove the chart's production attribution cause. No new runtime image or production financial acceptance is claimed.

## Files and next step

Edited: WorkspaceRecoveryScreen.kt, BusinessOverviewScreen.kt, FinancialCalculationEngine.kt, WorkspacePickerUiTest.kt. Added: BusinessOverviewAttributionTest.kt, BusinessOverviewCircleUiTest.kt, this report and current-run evidence. No Room schema, outbox contract, operationId/actionTime, backend, Rules, live membership, financial record, endpoint or accounting-policy change.

Next: inspect the one pending action's supported diagnostic details without retrying it; establish a specifically authorized queue-preserving update path, then install and let the user manually retest the chooser and circles. Do not erase/reassign/retry the pending action merely to remove the warning. No production SDK/Admin/REST calls, credentials, deployments, billing changes, production record edits or membership modifications were performed by the agent in this pass. User-driven app activity is not represented as independently reconciled server evidence.
