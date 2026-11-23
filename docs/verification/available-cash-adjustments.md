# Available cash corrections and PMS launcher — development verification

Baseline: `0a7ee67e405f98a9cd8d30d907a1be95221e9bfb`.
Development branch: `codex/available-cash-adjustments`.

Update22 November: the minimal cash-permission candidate is now live after explicit approval; real-workspace cash activation remains OFF because partner phones are not updated. The earlier predeployment checkpoints below are historical. See [current rollout evidence](cash-permissions-live-rollout.md).

The user superseded the previous display-only decision. A correction must change shared available cash, cash-related reports and cash withdrawal checks, while retaining the reason, authenticated editor, server-confirmed time and before/after/difference history. A correction does not create a customer collection, sales, profit, expense, or partner investment.

## Confirmed findings

The shipped 1.0.16 editor used separate display metadata; the financial engine and withdrawal validation intentionally did not read it. That explains the unchanged limits. On 22 November 2026 at 15:01:07 IST, a GET-only check found active Rules `c84da8eb-0fda-4318-a7ef-d16d8b9bd7d1`, canonical SHA-256 `4b0880cb108fe97440cee9a008826e5498807b77243fbc292c47313cd06a5874`. That policy has no balance-display or cash-adjustment collection permissions. The feature/policy mismatch explains the editor's authorization rejection; unrelated financial rejections must still be diagnosed individually.

## Implementation

- `CashBalanceAdjustmentRepository.kt`: server-confirmed paired current state and immutable adjustment receipts; exact integer paise, mandatory reason, real membership identity, original operation UUID, revision conflict protection, idempotent acknowledgement, and server timestamps. Read listeners reconcile the complete revision/history chain; cache misses are not zero balances. Cached confirmed history can be viewed offline; saving is online-only. No original Room or outbox row is modified.
- `CashBalanceEditor.kt`: corrected cash target, difference, reason and readable English/Tamil history. User text is preserved. Stale forms, offline/unconfirmed state and inactive rollout cannot save. A failed save keeps its logical operation identity and does not report success.
- `WorkspaceRepository.kt`: membership-bound cash state, authoritative correction reads for cash withdrawal validation, original-operation protection, and corresponding cash inputs for existing borrowing calculations. Deferred features remain deferred. Correction creation is held when this device retains unresolved financial operations.
- `MainViewModel.kt`, `MainActivity.kt`, Home, Business Overview and Report: shared adjusted cash flows into Home, report cash totals, withdrawal dialogs and their existing PDF input. Cash history errors/unconfirmed state cannot be labelled Synced. Unknown balance is shown as verification in progress. Sales, receipts, dues, profit and existing partner entitlement limits remain unchanged.
- `FinancialCalculationEngine.kt`: an explicit confirmed cash-adjustment projection, scoped to the authorized workspace and adjustment period; optional summary input affects only available cash. No receipt, expense, profit-sharing or historical method/date formula is changed.
- `WorkspaceSyncCapabilities.kt`: strict boolean `cashBalanceAdjustmentsV1`, default off, persisted in the existing actor/workspace-isolated capability cache. All relevant partner clients must be upgraded before activation.
- `firestore.phase53.rules`: minimal additive cash correction policy on the actual deployed compatibility baseline. Removing the new functions and matches restores that baseline byte for byte. It does not grant the obsolete display-only API or change existing financial/deletion clauses. Atomic mode is rejected by this compatibility variant.
- `firestore.rules`: corresponding development policy, with adjustment receipts coupled to existing atomic-cash state/receipt transactions when that protocol is enabled. In atomic mode, audited before/after values must match authoritative cash state. Existing opening-balance initialization restrictions remain.
- The old display-only prototype and its tests remain for development compatibility, but the product entry point no longer uses it. Its security tests run against the development policy; the live compatibility candidate does not activate it.
- Original PMS JPEG copied byte-for-byte into `drawable-nodpi/pms_app_icon.jpg`; SHA-256 `a8c5c46f12902c5235051186be79419297979b355f7fb3fb7e388687030c7be9`. Added legacy/adaptive launcher wrappers and changed manifest icon references. No AI regeneration, in-app business UI redesign or Firebase configuration change.
- New unit, Rules and mandatory Android tests are registered in the existing suite and instrumentation plans; inventory files are regenerated. Existing assertions/gates are retained. `app/src/deviceTest/AndroidManifest.xml`, `CashTestApplication.kt` and `CashCachePeerService.kt` provide a non-exported test-only peer process. Normal demo startup is preserved in the test host; the peer uses only guarded local Firebase clients. These components are excluded from customer builds.

## Evidence checkpoint

- Final host: **32 passed**, zero failures/errors/skips: correction9, reconciliation11, withdrawal/share6, capability6. Actual XML is retained in the local verification evidence, not inferred from UI messages.
- Final Rules: **42 passed**, zero failures/skips: full policy20 (new7/existing atomic8/preserved legacy prototype5), compatibility22 (new7/existing Partner deletion15). Exact compatibility candidate canonical SHA-256: `90eb895bc114a1e22ab58248c339e49e422c598c94b0e9c60d0e5b6fb54931c2`.
- Debug compilation, assembly and lint passed. Lint: **0 errors,120 warnings**; 114 prior warnings plus five unused retired-icon resources and one optional monochrome-icon warning. Retaining the full-colour uploaded image avoids an incorrect solid monochrome silhouette. No detector or gate was disabled.
- Final Android API36 emulator: **7 passed** in23.471s: six UI/icon cases plus the two-process SDK scenario. It verifies real authenticated cloud documents, exact operation IDs, one logical receipt after replay, stale-revision rejection, two recipients' confirmed state, actual source cash1200 plus adjustment400 =1600, original job paid1000 retained, peer-process kill/restart, cached history while offline, and logout privacy. A real process restart replaces the unsupported attempt to delete/recreate a live FirebaseApp inside one process. No expected business assertion was weakened.
- The first in-process SDK attempt returned PERMISSION_DENIED. Added diagnostics subsequently passed membership and all writes, then proved Firebase Auth24.1.0 retained a deleted-app reference during the simulated restart. Failed attempts and traces are preserved; the original isolated denial is not relabelled as a proven production-rule defect. Final independent-process testing passed without changing production code or relaxing Rules.
- Initial lint hit the actual ExperimentalDetector/FIR class-cast tool failure. Explicit state type/opt-in and a single-worker retry passed. A new isolation regression initially ran against a class built39 seconds before its guard was added; the fresh build passed. A mixed device/debug-unit task invocation failed before executing tests; correct build configurations are used. These failed attempts are retained.
- Uploaded image SHA was verified inside the installed synthetic APK. The actual Android launcher render was extracted and visually inspected: tractor, PMS, TRACTOR and By Aidhunt remain visible inside the circular mask. Native/test APK hashes are saved in the local evidence.
- Python localization/inventory tests:8 passed; suite inventory123 host classes/26 backend files/9 runnable Android classes/3 existing explicitly blocked; no inventory errors. Interaction inventory1103 candidates; this is a source inventory, not a claim of1103 runtime tests. Publication scan found no credentials in the staged index.
- Automated physical-phone, manual two-phone, real OTP, production activation and full GitHub release regression: **NOT TESTED / NOT RUN** for this revision. The separately signed1.0.17 owner-test package passed the checks below; no final customer release is claimed.

## Production boundary and remaining limitations

No live financial correction, pending-operation recovery, Rules deployment, billing change, backend deployment, production merge or customer APK distribution has been performed. The disputed legacy receipts are not altered or reclassified. Read-only private financial evidence remains outside Git.

The current compatibility policy still has its original withdrawal authorization model. This change applies corrections to the application's actual cash limits; it does not claim to retrofit global aggregate withdrawal locking into that policy. The separately reviewed atomic-cash rollout and an approved authoritative baseline are required for server-serialized aggregate withdrawals. No opening balance is fabricated here.

Before activation: finish exact-source testing; review the compatibility Rules diff; inventory and update all authorized partner clients; re-read the active Rules to detect intervening changes; obtain explicit approval for the minimal Rules deployment and the single workspace capability. Do not deploy the broader development policy as a shortcut. Physical two-phone cash consistency, restart/offline behavior and withdrawal limits remain a separate acceptance gate.

Rollback before any correction can leave the feature disabled and restore the saved Rules. After any confirmed correction, do not downgrade to clients that ignore it or delete history. Preserve cash-aware reads, use a forward repair, and reverse a mistaken adjustment only by a separately authorized new audited adjustment. A cash correction is not proof of a historical receipt or payment method.

## Exact changed-file inventory

Functional checkpoint: `6850bc91f1793b66b153cf30b800f9667200128b`. The implementation groups and reasons are explained above. This is the complete tracked inventory for that checkpoint; private evidence, credentials and APK binaries are excluded.

- `PROGRESS.md`
- `app/src/androidTest/java/com/example/CashBalanceCloudDeviceTest.kt`
- `app/src/androidTest/java/com/example/CashBalanceDeviceTest.kt`
- `app/src/deviceTest/AndroidManifest.xml`
- `app/src/deviceTest/java/com/example/CashCachePeerService.kt`
- `app/src/deviceTest/java/com/example/CashTestApplication.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/example/MainActivity.kt`
- `app/src/main/java/com/example/data/firebase/CashBalanceAdjustmentRepository.kt`
- `app/src/main/java/com/example/data/firebase/WorkspaceSyncCapabilities.kt`
- `app/src/main/java/com/example/data/repository/WorkspaceRepository.kt`
- `app/src/main/java/com/example/ui/components/CashBalanceEditor.kt`
- `app/src/main/java/com/example/ui/screens/home/HomeScreen.kt`
- `app/src/main/java/com/example/ui/screens/report/BusinessOverviewScreen.kt`
- `app/src/main/java/com/example/ui/screens/report/ReportScreen.kt`
- `app/src/main/java/com/example/ui/util/FinancialCalculationEngine.kt`
- `app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt`
- `app/src/main/res/drawable-nodpi/pms_app_icon.jpg`
- `app/src/main/res/drawable/pms_launcher_background.xml`
- `app/src/main/res/drawable/pms_launcher_foreground.xml`
- `app/src/main/res/mipmap-anydpi-v26/pms_launcher.xml`
- `app/src/main/res/mipmap-anydpi/pms_launcher.xml`
- `app/src/test/java/com/example/CashBalanceAdjustmentTest.kt`
- `app/src/test/java/com/example/WorkspaceSyncCapabilitiesTest.kt`
- `docs/regression/asset-inventory.json`
- `docs/regression/interaction-inventory.csv`
- `docs/regression/interaction-inventory.json`
- `docs/verification/available-cash-adjustments.md`
- `firestore.phase53.rules`
- `firestore.rules`
- `scripts/instrumentation-plan.json`
- `scripts/run_backend_suite.mjs`
- `security-tests/balance-display.test.cjs`
- `security-tests/cash-balance-adjustment.test.cjs`

## Separate physical acceptance — NOT TESTED

Automated physical-device testing: NOT TESTED. Manual physical-device testing: NOT TESTED. API36 instrumentation results are emulator evidence only. No USB connection is required for the following manual procedure.

1. Preserve the current production application/data and install the matching-key higher-version candidate as an update. If Android rejects the update, do not uninstall or clear storage; retain the error for diagnosis. Verify version1.0.17 and PMS launcher artwork.
2. With the cash capability still off, sign into the existing authorized workspace on each Owner/Partner phone. Verify the existing records and notification/history behavior. The cash editor must explain activation is pending and refuse to save. Operators must not be able to correct cash.
3. After every relevant partner device is upgraded and separate rollout approval is granted, recheck the active policy against the reviewed candidate, deploy only the minimal compatibility policy, and activate only the authorized workspace. No financial test record is created by deployment.
4. Before any live write testing, obtain approval for the exact cash target/reason. Prefer an isolated authorized synthetic workspace with the same reviewed policy. Record the server-confirmed starting cash, partner entitlement/withdrawal limit and each device's version/actor/workspace.
5. Save one approved correction on one device. Verify exactly one operation ID, before/after/delta, mandatory reason, authenticated editor and server time. Check Home, overall cash reports, existing withdrawal cash checks and PDF cash input; customer paid/due, profit, investments and historical receipts must remain unchanged. Confirm identical server state/history on the other device.
6. Open stale correction forms on both devices. Save an authorized adjustment on one; the other's stale form must require refresh and must not overwrite it. Any retry with the same logical operation must produce one history receipt. Do not use unrelated live funds as test transactions.
7. Restart, disconnect and reconnect each phone. Confirm cached approved cash/history remain visible offline, saving remains unavailable offline and Synced is not shown while verification is incomplete. Switch accounts/workspaces and check that old private data cannot be saved or displayed under the new context. Do not clear retained pending operations.
8. Record screenshots with sensitive customer fields masked, exact cash-history paths/operation IDs and server values, app versions, membership roles, observed withdrawal validation and timestamps. Mark individual results PASS/FAIL only after execution. Do not claim notification push delivery, full atomic withdrawal serialization or real-device acceptance from emulator results.

## Signed controlled-test package — completed22 November2026

Functional source is committed as `6850bc91f1793b66b153cf30b800f9667200128b`. The only later application change is version1.0.17/code18 in `app/build.gradle.kts`; this report and `PROGRESS.md` record the evidence. Branch remains `codex/available-cash-adjustments`; no push or production merge has been performed.

- Original-key signed `AIDHUNT-TRAC-1.0.17-Test-Candidate.apk`, 23966762 bytes; SHA-256 `87cbf661bdfc7c3291944c077af2c2bdfd3f1eab290e0b81c9a2f4d0b2da0985`. The package is `com.aistudio.aidhunttrac.cedlir`, non-debuggable, with a verified APK v2 signature matching the previous1.0.15 product certificate. Existing higher-version update behavior is intended; installed-device compatibility remains NOT TESTED.
- Actual protected release Firebase project/app/API resources match the original configuration. Test/pilot/native-push flags are false; existing shared Partner job-deletion flag remains true. New cash editor/repository and unused-customer dialog are present in DEX; test-only CashTestApplication/CashCachePeerService are absent from customer DEX and manifest. The manifest's ordinary/round launcher resources resolve to the PMS icon. Release optimization shortens the JPEG path, but the resolved resource retains the original image SHA exactly.
- Relevant release unit tests:34 passed,0 failures/errors/skips (activation2/correction9/reconciliation11/withdrawal6/capability6). Signed release assembly and release lint passed in15m12s,0 errors/120 warnings. The same32 functional host cases also passed in Debug; they are repeated variant checks, not66 distinct scenarios. No broad regression rerun was represented as complete.
- Local artifact evidence: `outputs/cash-controls-1017/apk-verification.json`, `outputs/cash-controls-1017-release.log`, checksum file and owner testing notes. The final changed-file publication scan has0 findings across35 changed files; protected financial snapshot hash remains unchanged. Private source evidence, release credentials, Firebase config and APK binaries are not committed.
- Cash adjustment live activation remains OFF. This package alone does not grant Firestore permissions. The live denial was caused by the deployed policy lacking the editor paths; the tested minimal compatibility candidate is prepared, not deployed. All relevant clients must be upgraded before the authorized workspace capability is enabled. No old disputed amount is added or classified automatically.
- Existing Partner unused-customer deletion remains a separate inactive trusted-backend gate. Physical-device automated/manual tests and full GitHub release regression remain NOT TESTED / NOT RUN. No production financial writes, pending-operation recovery, Rules/flag/backend/billing deployment or production merge.

Exact next action: deliver the controlled test artifact and private financial verification references; obtain explicit approval for the minimal Rules update while keeping cash activation off until all partner devices are upgraded. Re-read the active Rules immediately before any approved rollout and stop if its baseline differs. Do not install, activate or recover live operations automatically.
