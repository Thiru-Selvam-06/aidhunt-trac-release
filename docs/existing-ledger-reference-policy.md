# Approved existing-ledger reference policy — Option A

Decision date: **2026-10-18** (Asia/Kolkata). Authority: the user's explicit Option A instruction in this task. This documents the selected reference process under Architecture v1.6; it introduces no new accounting mechanism or permission.

Preserve the existing PMS ledger as the reference. No financial cutoff/effective date, opening transaction, opening amount, automatic replay, historical reclassification, timestamp rewrite, operation-ID replacement, attribution rewrite or workspace reassignment is introduced. Current formulas and existing authorized transaction/reversal/deletion rules remain unchanged. Selecting this documented policy performs no application/database mutation.

A **reference snapshot capture time** is the observation time of an authorized evidence artifact. It is not a transaction timestamp, authorization time, accounting boundary, or instruction to filter out earlier records. Later uploads keep their original business occurrence time and original actionTime. The accepted original-client-actionTime offline eligibility policy and its documented clock-manipulation limitation remain unchanged.

Current financial totals continue to use the existing architecture/application rules. In the current canonical cash calculation, only ACTIVE Cash collections fund business cash; Business Paid expenses and withdrawals consume it; personally funded expenses remain investments, not cash-drawer deductions. Negative balances remain visible. This restates the current implementation, not a verification of PMS amounts or a change to accounting formulas. Preserve the distinction between creator, payer, beneficiary, collector and authenticated audit actor.

The current PMS ledger's contents are not newly verified by this decision. No complete authorized sanitized authoritative/Room inventories have been supplied. Current access, membership, the reported183 records, live financial totals, phone OTP and device online/offline/deletion acceptance remain NOT VERIFIED. The release gate stays **NOT READY**.

## Read-only reference procedure

1. Accept only an explicitly provided, authorized and appropriately sanitized inventory; do not fetch production data or credentials. Document who authorized its scope, source artifact, project/workspace, observation time, provenance, category mapping, query/status/date filters and completeness/pagination. Unknown completeness remains incomplete. Source assertions and hashes do not independently prove authenticity.
2. Define the required category scope before comparison. Do not infer that183 combines different record types. A whole-workspace reference should declare every modeled category covered by its approved scope, including explicitly confirmed empty categories. Omitted categories are unknown, not zero. Do not copy a synthetic fixture to populate PMS.
3. Use the existing [inventory format](workspace-inventory-format.md) and `scripts/reconcile_workspace_inventory.py`. Supply the approved source as expected and a corresponding sanitized local snapshot as observed. Repeat `--required-category CATEGORY` for each approved required category. A match with no required-category list remains limited to the inventories' declared scopes; it cannot certify a full-workspace reference.
4. Compare original identities and state, not just aggregate counts. Preserve active/reversed/deleted/archived/sync metadata and durable commands, including original operationId/actionTime/actor/workspace, payload-byte digest and dependencies. Required categories absent on both sides produce INCOMPLETE; material record/state differences still fail. Preserve records with unknown metadata; never fill missing dates with today.
5. Keep input files and prior reports immutable. Use a new output filename per run; record exact input hashes, command, result and source-state limitations. Repeating comparison must not create business transactions or acknowledge commands.
6. Reconcile financial figures separately only when authorized source data and the existing formula inputs are adequate. The metadata-only inventory utility does not calculate money or create a financial baseline. No matching count or metadata digest alone proves ledger totals.

## Evidence and continuing scope

The original Architecture v1.6 DOCX was inspected read-only; its hash and relevant financial/offline/deletion paragraphs are recorded in `test-artifacts/option-a-reference-2026-10-18/architecture-review.json`. No conflicting automatic baseline/opening implementation was established by scoped source inspection. No application, backend, Rules or Room schema change is required to select Option A.

Synthetic Room/ledger tests cover retained original IDs/times/attribution/payload, same-ID retry/upsert, retained reversal history and scoped calculations. Inventory tests cover repeated read-only comparisons, capture-time independence, exact identity/state mismatch, pagination and required-category gaps. These establish local behavior only; they do not establish actual PMS records or live access. See [execution report](option-a-reference-execution-2026-10-18.md).

Prior dated reports describing the baseline choice as unresolved remain historical evidence; this approval resolves the policy decision only. It does not retroactively validate their other outstanding gates or grant production access, deployment, installation or membership-change permission.
