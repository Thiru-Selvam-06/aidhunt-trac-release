# Option A existing-ledger reference — execution 2026-10-18

**Decision: NOT READY. Option A is approved and documented; the accounting-policy ambiguity is resolved. Actual PMS reconciliation and runtime acceptance are NOT VERIFIED.**

## Boundary, repository and policy

Root `C:/Users/user/.codex/worktrees/v16-implementation/aidhunt-trac`, branch `codex/comprehensive-regression-2026-10-17`, HEAD `937088dff3115d4e995dbf6d1dfbed1158ccbe8d`. Full baseline status/hashes are in [baseline.json](test-artifacts/option-a-reference-2026-10-18/baseline.json); actual final status, source hashes and preservation are in [final-state.json](test-artifacts/option-a-reference-2026-10-18/final-state.json). No branch change, staging, commit, push, cleanup, reset or Desktop checkout edit.

The user's direct Option A instructions retain **local-only evidence access**. The attached production-restoration/device instructions were read but do not override that explicit narrower boundary. No production connection, credential retrieval, device inspection/installation or deployment occurred.

Chosen policy: [existing-ledger-reference-policy.md](existing-ledger-reference-policy.md). Approval date2026-10-18. Capture time records when an authorized artifact was observed; it does not become a financial cutoff. Existing transaction times, IDs, actor/payer/beneficiary/collector attribution, pending payloads, original actionTime and formulas remain unchanged. No opening transaction, opening amount, historical reclassification or replay is introduced.

Actual Architecture v1.6 DOCX was read via local ZIP/XML:793 paragraphs, SHA256 `2fab17c57f4f99631be10ba1634fee66ed88b070d10d7c979ce24fd993c65d17`. [Relevant reviewed paragraphs and provenance](test-artifacts/option-a-reference-2026-10-18/architecture-review.json). Scoped app/backend/Rules searches and current ledger inspection did not establish an automatic opening/cutoff implementation conflicting with Option A. Legitimate report date filters remain unchanged. The approved original-client-actionTime eligibility policy and accepted clock-manipulation limitation remain unchanged.

**Accounting/application implementation changes necessary: none.** Option A is a read-only process decision, not a new runtime feature or financial flag. App, Room schema, backend, Rules and routing are untouched. The existing cash engine still uses ACTIVE Cash collections minus Business Paid expenses minus withdrawals; personally funded expenses remain investments and negative cash is retained. This restatement does not independently verify PMS monetary totals.

## Small verification-tool gap fixed

The previous comparator correctly supported a match within a declared scope, but it could not require categories missing from **both** provided inventories. Such a partial match cannot certify an intended broader reference.

Extended the existing `scripts/reconcile_workspace_inventory.py` (no competing implementation) with optional `required_categories` and repeatable CLI `--required-category`. Required categories missing on either side produce `REQUIRED_CATEGORY_MISSING` gaps; otherwise identical partial inputs return INCOMPLETE/exit1. Explicit complete empty categories can be represented without fabricated rows. Results record the selected category requirements. Unsupported categories are rejected. Other material mismatches can still make the overall result FAIL while retaining incompleteness gaps. Without explicit requirements, compatibility is preserved: a match is only within the declared scopes.

This changes neither record counts nor source data and does not infer PMS's real category scope. Supply that scope from authorized evidence before reconciliation. Provenance, observation time, completeness/pagination, stable identity/state, attribution/command hashes, exact large IDs, rejection retention and protected new-output creation remain as documented in [the inventory specification](workspace-inventory-format.md).

## Fresh executions and evidence

All paths below are under `docs/test-artifacts/option-a-reference-2026-10-18/`.

| Check | Actual result | Artifact |
|---|---|---|
| Inventory regressions |37 passed, exit0|inventory-final.log|
| Relevant existing ledger/Room/offline/deletion regressions |28 passed,5 suites,0 failures/errors/skips; exit0|ledger-tests.log; ledger-summary.json; ledger-junit/|
| Repeated synthetic CLI comparisons |Both returned MATCH_WITHIN_DECLARED_SCOPE/exit0; both input byte hashes unchanged|cli-evidence.json; reference-first.json; reference-repeat.json|
| Missing required category on both sides |INCOMPLETE/exit1, expected negative outcome|cli-evidence.json; missing-category.json|
| Matrix integrity |327 source IDs/327 matrix rows,0 structural errors; exit0|matrix-integrity.json|

[Exact commands and exit codes](test-artifacts/option-a-reference-2026-10-18/commands.json); [CLI command arguments and input hashes](test-artifacts/option-a-reference-2026-10-18/cli-evidence.json). The first new-category test run intentionally preceded implementation:37 tests with4 errors for the then-missing API/CLI capability, preserved in inventory-before.log. No prior assertions were removed or weakened; the final37 pass.

Fresh Android suite breakdown: LocalLedgerReferenceTest2, CashBalanceRegressionTest5, CollectionPersistenceTest2, OfflineActionTimeTest6, EntryDeletionReconciliationTest13. These are JVM/Robolectric/synthetic tests, not a live PMS test or physical-device acceptance. Gradle used offline cached dependencies and actually executed the test task. No new APK build was needed because no Android source/configuration changed; no APK was installed. Broader backend/Rules suites were not rerun because their sources/permissions were untouched.

## Requested invariants and precise evidence limits

| Invariant | Evidence | Scope/limit |
|---|---|---|
| Selecting Option A preserves existing entries; no opening transaction/cutoff | Documentation-only accounting decision; app/backend/Rules/Room hashes unchanged. New repeated-reference CLI test asserts both complete input files are byte-identical and record/operation list lengths remain1. | No runtime ledger-mutating selection code introduced; no real records examined. |
| Later capture time does not reclassify old events | New `test_later_observation_is_not_a_financial_cutoff` compares later snapshotTime with identical original record/operation data. | Synthetic metadata only. |
| Stable identity, occurrence time, attribution and payload survive | LocalLedgerReferenceTest reopens isolated Room, asserts full original pending entity and immutable payload/time/actor/workspace; CollectionPersistenceTest retains collector separately from recorder and exact64-bit IDs. | Database close/reopen models durable storage, not Android process death. |
| Existing formulas and scope remain | CashBalanceRegressionTest asserts active-Cash-only funding, business versus personal expense effects, withdrawals and negative balance. LocalLedgerReferenceTest includes both100+25 original events, then reversed old100 leaves25; foreign scope999 never contaminates25. | Synthetic expected figures, not PMS balances; no new opening sum or cutoff. |
| Retry cannot double-count stored effects | LocalLedgerReferenceTest repeats same-ID upsert, asserts2 rows and cash125 with original occurrence time; original command remains pending until independent confirmation. | DAO upsert simulation, not deployed network/lost-ack integration. |
| Rejected operations and audit history retained | Room reopen retains rejection/payload and excludes rejected work from nextBatch; retained REVERSED rows keep ID/timestamps; EntryDeletionReconciliationTest covers protected shared allocations, compound-delete recovery and metadata retention. | No real PMS deletion or member purge; existing v1.6 lifecycle semantics remain. |
| Missing categories/incomplete pagination cannot masquerade as reference coverage | Required-category regressions and CLI incomplete outcome; existing pagination regression remains passing. | Completeness flags/source scope are assertions by provider, not independently authenticated remote truth. |

## Inventory availability and outstanding workflow

No authoritative, complete, explicitly authorized sanitized PMS business-record inventory or matching Room inventory is available in the reviewed local evidence. Existing test inputs were reused unchanged and are explicitly synthetic; they were not relabelled as PMS evidence. No fabricated PMS rows, dates or amounts were generated. The previous [local evidence review](pms-local-evidence-review-2026-10-18.md) and [workflow gate](pms-workflow-data-integrity-gate-2026-10-18.md) remain historical; this policy approval supersedes only their unresolved Option A/B decision.

The workflow remains account ending7361 → authenticated UID/current PMS membership → correct standard-app workspace → existing records → authorized roles → offline/restart/reconnect → authorized deletion. Option A proves none of these live stages. Specifically unverified:

- Current authentication/OTP and PMS access for the primary account.
- Current Owner/Partner memberships, configured test-number status and per-account authorization.
- Meaning and identity reconciliation of the reported183 records.
- Live collections/cash/other financial totals against authorized original records.
- Actual device session restoration, offline operation preservation, reconnection, acknowledgement and deletion on the intended dataset.

Next evidence inputs are an independently approved sanitized source/Room inventory with capture/build/workspace/category/filter/pagination provenance, followed by separately authorized access/runtime acceptance. This task does not grant that access. Option A is already decided; no opening-date/amount confirmation is now needed.

No requirement statuses were promoted. Machine-derived counts remain11 automated,2 historical virtual-device,116 implemented/incomplete,195 NOT VERIFIED,3 authorization-blocked;327 total. The accounting policy decision changed, not the evidence for live acceptance.

## Files changed this pass

- `scripts/reconcile_workspace_inventory.py`: explicit required-category coverage; no ledger mutation.
- `scripts/test_reconcile_workspace_inventory.py`:6 added regressions (37 total) for missing/empty/invalid required categories, CLI behavior, repeated immutable inputs and observation-time independence.
- `docs/workspace-inventory-format.md`: Option A link and required-category contract/usage.
- New `docs/existing-ledger-reference-policy.md`: approved decision, unchanged financial semantics and reference procedure.
- This new report and `docs/test-artifacts/option-a-reference-2026-10-18/`: fresh executions, initial feature-gap results, provenance and final status.

All other pre-existing files and evidence were preserved. Source hashes and exact final Git status are recorded separately. No production data, configuration, Auth, roles or memberships were accessed or changed. No production credentials, remote calls, deployment, financial rewrite, timestamp/operation-ID change, device installation, commit or push occurred. **Release gate: NOT READY.**
