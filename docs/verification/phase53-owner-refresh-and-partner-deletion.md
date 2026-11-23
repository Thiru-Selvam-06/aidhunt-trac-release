# Phase 5.3 — Owner activity refresh and staged Partner deletion

Baseline: `6afbc862ee939bb188f480563399d35ae3e34cfb`, branch `codex/phase52-real-workspace-candidate`. Work is isolated on `codex/phase53-owner-activity-deletion`. Product candidate: normal package `com.aistudio.aidhunttrac.cedlir`, version 1.0.14 / code 15.

## Confirmed refresh defect

The original Owner's live membership has `uid`, `role`, `displayName` and `status` but no redundant `workspaceId`. Existing workspace discovery accepts this legacy shape using its authenticated document path. The previous `OwnerVisitCloud.load` instead populated a missing `workspaceId` as an empty string, then required that string to equal the selected workspace. It therefore failed the active-Owner assertion before any activity-source query. Partner memberships with that shape also could not enter the verified activity projection. MainViewModel swallowed the exception and showed only the generic failed-refresh message.

The corrected membership conversion derives identity from the already validated `workspaces/{selectedWorkspace}/members/{documentUid}` path. Explicit conflicting `uid` or `workspaceId` fields remain rejected. The workspace's original `ownerUid`, active membership and Owner role are still required. This does not change permissions or rewrite membership documents.

Read-only production inspection used the existing authenticated Firebase administration connection to verify the document shape, current Rules release, source field types and the nine indexed query shapes. All nine returned HTTP 200. That establishes schema/query availability with administrative access; it is not a test of the Owner phone's Firebase token, App Check or network. No live customer record was created, edited, deleted or recovered. The first confirmed failing source-code stage is the active-Owner membership assertion. Any additional device-specific failure must be checked on the updated phone using the new safe diagnostic codes.

## Refresh and history behavior

Each activity source now fails independently. Available verified activities still merge into existing Room notifications. An incomplete refresh is visibly marked Partial and does not advance the last successful visit checkpoint. A later complete refresh clears diagnostics and updates Last Updated from the server read timestamp. Failed identity verification prevents all projection. Cancellation and account-switch protections remain in place. Diagnostics contain only fixed source/category/status codes, without credentials, customer text, UIDs or amounts.

Existing foreground/reconnect and authoritative-listener refreshes, stable event IDs, original-Owner-only visibility, Partner actor selection, saved read/unread state, English/Tamil and IST presentation are retained. Native background FCM is still disabled. These are in-app notifications when TRAC opens or remains foregrounded.

Existing evidence limitations remain: overwritten job-creation/collection operation IDs and hard-deleted expenses without a retained proof cannot be reconstructed. Job edit history and deletion-proof history can be projected when retained. This update does not fabricate missing history or add new write-side audit infrastructure.

## Partner deletion: prepared, not activated in this APK

`SHARED_PARTNER_JOB_DELETION` defaults to false in every build. Explicitly enabled test paths permit active Partners to request eligible ordinary/historical jobs across creators and ages. Owner privileges remain intact. Existing creator/beneficiary membership and financial-dependency checks remain: removed linked identities, unreviewed legacy timestamps, another person's receipt/expense, stale source data, more than five Partner receipts and rejected dependent financial writes may require resolution instead of deletion. Creating Old Entries and editing eligible Old/New Entries already support active Partners and were preserved.

Operators cannot queue job deletion. This aligns UI/outbox policy with the existing guarded native backend, which already required Owner/Partner; it fixes a previously misleading Operator eligibility result rather than granting broader access.

The native path still authenticates the actor, acquires Entry/Customer guards, reads authoritative dependencies, compares the original snapshot, performs a version-checked atomic commit and preserves immutable deletion identity/history. No direct unguarded-delete workaround, financial cleanup or automatic retry of rejected writes was added.

`firestore.phase53.rules` is a separate candidate derived from the freshly exported active policy, release `e7981704-cf72-453b-a867-ad3faa10c75f`. Its only changes remove own-creator, ordinary-only and 24-hour restrictions from Partner **job lock/proof** eligibility. It retains active membership, workspace isolation, reviewed creation provenance, non-future action times, beneficiary checks, receipt accounting, protected financial children, immutable proofs and guards. See `phase53-partner-deletion.rules.diff`. Standard `firestore.rules`, Firebase configuration and deployment targets are untouched. Do not replace live Rules with the unrelated larger development policy.

Candidate published Git content (LF) SHA-256: `4b0880cb108fe97440cee9a008826e5498807b77243fbc292c47313cd06a5874`. The local Windows CRLF file SHA-256 is `2c4c79364faec2ba644cc0c30624912e228702a007bdffb9612be4d80a58f1b6`; only line endings differ. Compare canonical Git content during deployment preflight.

Activation requires separate approval: re-read/export current live Rules, confirm no intervening change, apply only this reviewed diff, validate/deploy that policy, then build a separately reviewed higher-version APK with the gate enabled. A Rules deployment alone does not activate the gate in this candidate. Rollback disables the gate in a subsequent higher-version build and restores the saved pre-deployment policy after assessing pending guarded deletes. Do not erase pending actions or proofs.

## Verification record

Proposed policy: all 15 Rules Emulator scenarios passed with zero failures/skips. Source inventory has 119 JVM classes and 24 backend/Rules files with no declaration errors. All 17 focused CI/publication harness tests passed. Interaction inventory contains 1085 source candidates; that is not a UI coverage claim.

The first targeted Android run executed 293 tests: 289 passed, four failed, none skipped. Failures were an old Operator-allowed expectation, two incomplete synthetic fixtures (an unintentionally paid deletion source and a job create without isDeleted), and a transport timeout in an existing emulator test. Assertions/fixtures were corrected while preserving all financial and authorization protections. A 50-test follow-up passed 49, including the timed-out original test and actual staged guarded deletion/idempotency. The remaining synthetic edit lacked editedByUid/Name/Role, which the unchanged Rules correctly rejected. The fixture now supplies those required editor fields. Original failure evidence is retained outside Git.

The final original-shape Owner REST/Rules integration test passed against the exported active policy after supplying valid editor metadata. Across retained targeted runs, 294 unique tests now have passing final outcomes, with zero unresolved failures or skips. Signed normal-package release assembly and release lint passed; lint has zero errors. Local protected release configuration and signing credentials remain outside Git.

First GitHub run [44365486505](https://github.com/Thiru-Selvam-06/aidhunt-trac-release/actions/runs/44365486505), application-source commit `4c4530105b515615611a6f3db4e9b466b415b30c`, passed Rules409/409, backend146/146, unit/Compose/outbox partitions, compilation/lint, Worker build and harness checks. Its database partition ran174 tests with one failure: V16ArchitectureContractTest still expected Operator deletion. The follow-up preserves the ordinary/historical and Operator editing assertions, denies the unsupported Operator deletion and adds a positive eligible Partner deletion assertion. It does not change application code, Rules or financial expectations. This genuine failure is retained and requires a fresh clean final run.

The final follow-up branch-head CI must pass all applicable jobs before handoff. Consult the exact SHA/run in the delivery report and [branch workflow](https://github.com/Thiru-Selvam-06/aidhunt-trac-release/actions/workflows/android-ci.yml?query=branch%3Acodex%2Fphase53-owner-activity-deletion). Final packaging/certificate checks and the actual final CI result are recorded in the local delivery evidence and persistent workspace progress file. Physical release gates remain closed.

## Physical acceptance checklist — NOT TESTED

Automated physical-device tests: NOT TESTED. Manual physical-device tests: NOT TESTED. Emulator results are recorded separately and do not establish phone delivery.

1. Preserve the existing app and record its version, workspace, balances, pending/rejected operation IDs and notification read state. Do not uninstall or clear storage. If unsynced data has no verified recoverable copy, defer the update on that device. Reports alone are not a restorable outbox backup.
2. Install the signed normal-package code-15 candidate over the compatible existing product. Cancel if Android requests uninstall, reports a signature conflict or rejects a downgrade. The signing certificate is compared with the known delivered product APK; the installed phone's certificate has not been inspected.
3. On Muniyappan's original account, open the existing authorized workspace and bell online. Record the actual Last Updated timestamp, any diagnostic code and screenshots. Confirm the workspace identity, original Owner role and unchanged balances. Do not assume Main's Synced badge establishes notification freshness; they describe separate operations.
4. Use an already authorized, intended real Partner activity, or obtain explicit approval for a clearly documented test transaction. After cloud confirmation, reopen/foreground the Owner app and confirm actor, action, IST timestamp and one logical notification. Mark it read, restart and verify read state. An Owner action must not create a Partner alert, and other Partners must not see the Owner-only history.
5. Separately verify an authorized edit, collection and eligible expense/withdrawal using actual confirmed records. Do not manufacture live finance merely for testing. Retained history limitations described above apply.
6. Disconnect the Owner phone, verify the saved/offline label, reconnect and confirm a complete refresh and advancing Last Updated. Keep rejected transactions untouched. If refresh fails, capture only the visible source/category/status code; never send tokens.
7. If a Partner already has an authorized pending action, confirm that it appears in Owner history only after successful server acknowledgement. Manual two-phone testing needs no USB. Record operation IDs, cloud confirmation and both phones' screenshots; no automatic recovery of old rejected finance is authorized.
8. Before approved rollout, verify this APK hides broader Partner deletion of another creator's, historical or expired job. Existing eligible deletion remains protected by current Rules and dependencies. Test expanded deletion only in the isolated Emulator until the separate rollout is approved.

Room remains schema 24, with the existing database filename and durable queue. Spark billing, native push deployment, authentication configuration, financial formulas, receipt classifications and production financial records are unchanged.

## Changed files

| File | Reason |
|---|---|
| `PROGRESS.md` | Persistent phase status and rollout boundary. |
| `app/build.gradle.kts` | Code15/version1.0.14 and default-off shared Partner deletion gate. |
| `app/src/main/java/com/example/data/auth/AuthorizationManager.kt` | Stage expanded Partner eligibility and align Operator denial with the guarded backend. |
| `app/src/main/java/com/example/data/firebase/FirebaseLedgerDeletion.kt` | Stage expanded job eligibility while keeping authoritative guarded financial checks. |
| `app/src/main/java/com/example/data/firebase/OwnerVisitCloud.kt` | Legacy membership-path verification, safe errors and independent source results. |
| `app/src/main/java/com/example/data/repository/WorkspaceRepository.kt` | Use permission-neutral deletion failure wording, since age/creator cease to be universal restrictions after rollout. |
| `app/src/main/java/com/example/data/sync/OperationPolicy.kt` | Stage queued Partner deletion and deny unsupported Operator deletion. |
| `app/src/main/java/com/example/data/sync/OwnerVisitActivity.kt` | Partial status and safe diagnostic state; clear on successful refresh. |
| `app/src/main/java/com/example/ui/components/NotificationPanel.kt` | Bilingual partial status and safe support codes. |
| `app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt` | Do not checkpoint incomplete refresh; log safe failures; use the shared delete precheck. |
| `app/src/test/java/com/example/EntryDeletionReconciliationTest.kt` | Align the Operator assertion with the backend; preserve Partner and financial assertions. |
| `app/src/test/java/com/example/EntryMutationAuthorizationTest.kt` | Assert the corrected Operator restriction; retain all other role/date checks. |
| `app/src/test/java/com/example/FirebaseLedgerDeletionTest.kt` | Test staged cross-creator historical deletion and paid-source protection. |
| `app/src/test/java/com/example/OperationRoleMatrixTest.kt` | Matrix expects Operator job deletion denied. |
| `app/src/test/java/com/example/OwnerVisitActivityTest.kt` | Legacy identity matching, explicit mismatch rejection and private diagnostic checks. |
| `app/src/test/java/com/example/OwnerVisitCloudEmulatorTest.kt` | Legacy live-schema reproduction; authorized Partner create/edit and partial-source refresh. |
| `app/src/test/java/com/example/OwnerVisitComposeTest.kt` | English/Tamil Last Updated and partial diagnostic rendering. |
| `app/src/test/java/com/example/Phase53DeletionEmulatorTest.kt` | Actual guarded Android HTTP transport, original operation acknowledgement, duplicate replay and totals under the candidate policy. |
| `app/src/test/java/com/example/V16ArchitectureContractTest.kt` | Keep ordinary/historical invariants and Partner deletion coverage while denying the Operator action already rejected by the guarded backend. |
| `app/src/test/java/com/example/Phase53DeletionPolicyTest.kt` | Staged/default-off role, age, creator and membership isolation regression cases. |
| `docs/regression/interaction-inventory.csv` | Regenerated source references after view-model line shifts; no UI coverage claim. |
| `docs/regression/interaction-inventory.json` | Matching machine-readable interaction inventory. |
| `docs/verification/phase53-owner-refresh-and-partner-deletion.md` | Evidence, limitations, rollout approval and physical acceptance checklist. |
| `docs/verification/phase53-partner-deletion.rules.diff` | Exact two-helper diff against the active policy. |
| `firestore.phase53.rules` | Separate minimal candidate based on the freshly exported live policy; not deployed. |
| `scripts/run_backend_suite.mjs` | Keep the new Rules suite mandatory in the existing CI partition. |
| `security-tests/phase53-partner-deletion.test.cjs` | 15 isolated Rules regression scenarios, including offline queue and protected financial children. |
