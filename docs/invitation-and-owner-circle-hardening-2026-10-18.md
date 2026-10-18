# Invitation and Owner-circle hardening — 2026-10-18

**Decision: NOT READY. Local fixes and regressions pass; no live PMS attribution repair, production invitation, SMS or physical-device acceptance is claimed.**

Repository: `C:/Users/user/.codex/worktrees/v16-implementation/aidhunt-trac`.
Branch: `codex/comprehensive-regression-2026-10-17`.
HEAD: `937088dff3115d4e995dbf6d1dfbed1158ccbe8d`.

Evidence directory: [owner-circle-20261018-152221](test-artifacts/owner-circle-20261018-152221/). Baseline, focused diffs, complete final tracked diff, JUnit, source hashes and final Git status are preserved there. Pre-existing changes were retained, including invitation work found when the follow-up request arrived.

## Invitation defect and fix

`WorkspaceInvitationService.mapBackendError` classified arbitrary message words (Owner/permission) as authorization failures and returned raw backend messages for other errors. Eight new regressions produced six failures before the fix; the before-fix XML/log are preserved.

The mapper now uses HTTP codes and normalized explicit `error.status`/`error.code` only:

- HTTP403 or explicit PERMISSION_DENIED/permission-denied: SecurityException with the fixed Owner-only message.
- HTTP401 or explicit UNAUTHENTICATED: safe sign-in recovery.
- HTTP400/INVALID_ARGUMENT: IllegalArgumentException with safe name/phone/role validation guidance.
- HTTP5xx: safe service-unavailable guidance, even when a backend message contains permission.
- Already-used/invalid invitations and throttling retain fixed recovery messages. Malformed, empty and wrong-shape bodies do not escape or reveal backend content.

The Account dialog now localizes only the exact canonical Owner-only error, instead of searching SecurityException text for Owner. The existing validation test now asserts the new explicit validation type and fixed safe message; assertions were not removed. Synthetic secret sentinels prove backend details are not displayed.

### Authorization and existing management

Source inspection confirms that new-invitation `allowedRoles` is empty for non-Owners and the submit handler returns before dispatch when `initialPartner == null && !isCollaborationOwner`. The actual caller supplies the workspace-scoped Owner state; ViewModel partner-management methods also guard dispatch. These are source/policy assertions, not a live invitation UI run.

Cloudflare's transaction checks active same-workspace membership, OWNER role and workspace.ownerUid independently of client controls. New in-memory tests reject Partner, Operator, removed Owner, role-only ownership impostor and foreign membership requests, including forged actor fields, and assert the whole record map is unchanged. Existing authenticated success/replay tests pass. Functions' `requireOwner` and transaction rechecks were inspected; no backend permission implementation was changed or deployed.

Existing-member editing/removal branches were not modified by this pass. The sole AccountScreen change relative to the invitation baseline is the error-message comparison. Requested cleanup and client regressions pass; this does not prove live member removal.

## Owner-only repair policy and circle behavior

The later Owner-attribution request explicitly supersedes creator-first repair. `plan_entry_attribution_repair.py` now proposes the verified workspace Owner for BOTH empty attribution fields, even for a verified Partner creator. It retains existing partial/conflicting assignments and all creator/audit, payer/collector, money, time and operation data. Only the two approved beneficiary fields enter a proposed patch.

Missing/inactive/unverified ownership produces no repair. Malformed finance/creation metadata is excluded; unresolved commands block matching targets; incomplete pagination/outbox inventories are rejected. Proposals include workspace/exact string ID, source fingerprint/version, original attribution, owner-evidence references, queue status and independent-review status. Source verification flags are input assertions: the offline planner cannot authenticate an export or establish current ownership itself. It has no network or apply mode.

The financial engine already uses explicit financial/beneficiary UID; no workspace-wide or viewer-based financial fallback was found in this path. We did not change its formula or make unresolved historical Entries look repaired.

A real Compose screen regression identified stale person selection when UID changed within the same workspace. Selection, open person picker and job detail are now reset on UID/workspace changes. Fallback name lookup is workspace-scoped. The regression failed at the post-account-switch All assertion before the source fix and passes after it. Two earlier test-harness failures (lazy-list scrolling and ambiguous label selection) are separately preserved, not reported as product defects.

Executed value assertions:

| Synthetic scenario | Actual asserted result |
|---|---|
| Owner-attributed job, then Owner → Partner → Owner | Owner recorded100/collected80/due20; idle Partner0/0/0; returning Owner100/80/20 |
| Partner-attributed unpaid job created by Owner | Partner collected0, due100; creator does not receive its attribution |
| Unattributed job | Remains under empty attribution identity; not assigned to the viewer/Partner |
| Same job ID across workspaces | A100/40/60; B700/0/700; switching back restores A values only |
| Actual circle UI | Exact collected/due labels visible; idle Partner displays Collected ₹0, Due ₹0 |
| Actual screen person filter | Owner → Partner → Owner and workspace change reset selected person to All |

Existing collection-policy serialization tests in the full run preserve distinct selected collector, authenticated recorder and exact IDs. The chart's Collected metric still comes from receipts against attributed jobs, not the physical collector identity. No payer/beneficiary/audit field or accounting formula was redefined.

## PMS evidence gate — live assignment remains unapplied

An existing derived snapshot was found at `docs/test-artifacts/entry-attribution-proposal-2026-10-18/source-snapshot.json` (SHA256 `7658ba482e97142efd4a075c8be787f13be189a31d11236ae84916a75b784e50`). It contains88 Entry rows,3 member rows and0 operation rows; these are counts of that local artifact, not verified current PMS counts.

It asserts complete pagination/outbox coverage and identity verification, but its accompanying report explicitly says the device outbox was unresolved. The original declared backup was not located in this worktree for independent hash/provenance verification. Current ownership, capture time/scope and queue completeness are therefore not independently established. The prior88 eligible proposals are NOT accepted as a reviewed live repair manifest. Eligibility counts remain unverified; the183-record target was not reconciled. No source rows or prior artifacts were overwritten.

[Existing-proposal evidence review](test-artifacts/owner-circle-20261018-152221/existing-proposal-evidence-review.json) records these gaps without copying personal data. An in-memory copy with operationsComplete=false is rejected by the planner. Earlier live-evidence-gate.json records the absence of an accepted complete source; this later review adds the discovered historical/derived artifact, not a complete current source.

Local Rules freeze Entry attribution during ordinary updates (`firestore.rules:172`) and Expense attribution (`:195`). Deployed Rules were not fetched or presumed identical. No existing trusted repair endpoint was established, and the timestamp-migration tool was not repurposed.

A separate trusted repair would need: a complete authorized snapshot/backup and current queue gate; authenticated current Owner verification; exact workspace/path/ID allowlisting; empty-field and eligible-state checks; fingerprint/update-time preconditions; a patch limited to the two fields; atomic durable before/after audit receipt and idempotent replay; and independent post-write verification. Unknown queues must block execution. Implementation/deployment and live execution require separate approval. No such mechanism was implemented or deployed here.

## Fresh validation

| Execution | Exit | Tests | Failures/errors/skips |
|---|---:|---:|---|
| Combined focused Android suites | 0 |54|0/0/0|
| Full Android unit/Robolectric regression |0|516 across66 suites|0/0/0|
| Backend invitation policy + Worker operations (in-memory/local harness)|0|48 (18+30)|0/0/0|
| Offline attribution planner |0|14|0 failures|
| Existing inventory comparator |0|37|0 failures|
| Debug APK build |0|assembleDebug|Successful; not installed|
| git diff --check |0|—|No whitespace errors|

Requested targeted suites: WorkspaceInvitationPolicyTest5; PendingInvitationCleanupContractTest3; FinalTargetedClientFixesTest12; FinalTwoClientFixesTest7. Additional focused suites: WorkspaceInvitationErrorClassificationTest8; BusinessOverviewAttributionTest11; BusinessOverviewCircleUiTest4; BusinessOverviewChartMembersTest4. All pass. Focused54 is a subset of full516, not additional Android coverage.

Commands (run from the worktree):

```powershell
python -m unittest discover -s scripts -p test_plan_entry_attribution_repair.py -v
python -m unittest discover -s scripts -p test_reconcile_workspace_inventory.py -q
node --test --test-concurrency=1 --test-reporter=junit cloudflare/operations.test.mjs functions/invitation-policy.test.js
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug --init-script scripts/ci-tests.gradle --offline --console=plain --max-workers=1 --no-daemon '-Dorg.gradle.jvmargs=-Xmx1024m -XX:MaxMetaspaceSize=512m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8'
git diff --check
```

Focused invocation used the same Gradle runner flags with `:app:testDebugUnitTest` and one `--tests com.example.<Suite>` for each of the eight listed suites. Before-fix invocation used only WorkspaceInvitationErrorClassificationTest; chart reproduction used BusinessOverviewCircleUiTest. Actual executables: bundled Python runtime, Node24.14.1 and JDK21.0.10. No production credentials or Firebase emulator were needed for these in-memory backend tests. No Rules change or Rules redeployment occurred. Roborazzi's finalizer task was skipped; JUnit reports zero skipped tests. This is not screenshot/device evidence.

See [final-results.json](test-artifacts/owner-circle-20261018-152221/final-results.json), focused-summary.json, junit-full-final/, junit-combined-focused-final/, backend-invitation-operations.xml and the named final logs in the evidence directory. All169 frozen source hashes match the tested final files.

APK: `app/build/outputs/apk/debug/app-debug.apk`; standard package `com.aistudio.aidhunttrac.cedlir`; version1.0.4/code5. SHA256: `f86ab829ccbd3f9489d4e83ccd5f49dda2e17f7e052ac935b3e9ad4ededc7e15`. No installation/launch in this pass.

## Changed files and boundaries

This pass changes: WorkspaceInvitationService.kt (structured safe errors), AccountScreen.kt (exact error classification only), WorkspaceInvitationPolicyTest.kt (explicit safe contract), new WorkspaceInvitationErrorClassificationTest.kt; BusinessOverviewScreen.kt (session/workspace state reset), BusinessOverviewAttributionTest.kt and BusinessOverviewCircleUiTest.kt (value/control regressions); cloudflare/operations.test.mjs (five denied-role/ownership regressions); scripts/plan_entry_attribution_repair.py and its tests (Owner-only proposal and evidence gates); this report and new test artifacts. FinancialCalculationEngine.kt and BusinessOverviewChartMembers.kt were already dirty/implemented and are unchanged by this pass.

Actual final Git status is saved in final-git-status.txt; final-source-check.json records unchanged branch/HEAD and source hashes. All prior work remains uncommitted. No reset, clean, stash, stage, branch switch, commit, push, Desktop edit, production request/write, membership change, credential retrieval, queue retry/clear, billing change, application/backend/Rules deployment occurred. Production Entries remain unchanged, so their unresolved-attribution label can remain until a separately authorized evidence-backed repair is applied.
