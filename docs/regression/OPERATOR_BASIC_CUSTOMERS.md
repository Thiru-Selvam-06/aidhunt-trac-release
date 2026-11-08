# Operator basic customer profiles — development only

The user approved basic customer creation by active Operators. Customer balances,
receipts, expenses, withdrawals and financial reports remain finance-only. This
phase is not deployed and does not backfill or modify any live profile.

## Confirmed current boundaries

- `WorkspaceRepository.saveJobEntry` and `addOrFindCustomer` reject new Operator
  customers; `OperationPolicy` also denies their CUSTOMER commands.
- The entry form hides explicit new-customer selection for Operators.
- Canonical customer documents contain billed/paid/due fields. Their existing
  finance-only read must not be opened to Operators.
- Current entry Rules already allow an Operator to get a missing entry before
  creating it, while denying historical-entry reads. No new missing-entry read
  permission is needed for this phase.

## Minimal coordinated extension

Use a metadata-only `customerDirectory` projection in the same Firestore workspace
and an additive Room table. It contains only identity, name, optional phone/location
and creation/command provenance. Never copy balance or collection fields into it.

An Operator's new CUSTOMER command writes the zero-balance canonical customer and
its basic projection in one batch. Rules require both records to agree and deny
Operator updates to existing canonical profiles. Before retrying, read only the
projection's immutable creation operation ID to verify the original create was
already committed. Keep the existing durable CUSTOMER dependency for its job.

Finance-authorized profile edits mirror basic fields atomically, preserving
creation identity. Both direct customer deletion and unused-customer cleanup
during checked job deletion remove the projection in the same guarded commit.
Directory reads remain workspace/member scoped. Pending local profiles are
protected from incoming snapshots; authoritative absence removes only confirmed
projection rows. A metadata-only Room/flow feeds Operator customer choices.

No automatic historical backfill is authorized. Existing ordinary-job customer
snapshots remain available as before. New protocols are default-off and require a
reviewed workspace activation after compatible Rules/APKs are ready; see
[WORKSPACE_PROTOCOL_ROLLOUT.md](WORKSPACE_PROTOCOL_ROLLOUT.md). No activation,
historical backfill, production deployment or live transaction recovery was run.

## Required evidence before release

1. Rules: valid Operator zero-balance create succeeds only with matching projection;
   nonzero totals, added financial fields, foreign workspace, wrong creator,
   existing-profile overwrite and missing projection are denied atomically.
2. Rules: Operator reads directory but cannot read canonical finances/collections;
   removed and foreign members cannot read either projection or source.
3. Android: offline basic profile plus own job survives close/reopen, uploads in
   CUSTOMER then JOB order, retains IDs/actor/time, and appears on a finance peer.
4. Android: ACK-loss replay resolves from immutable creation receipt with one
   profile/job; a conflicting identity fails closed without modifying the source.
5. Room: additive migration preserves exact money, job audits and rejected payloads;
   listener reconciliation does not replace pending work or retain deleted ghosts.
6. Checked deletion: both customer delete paths remove the directory atomically;
   referenced customers, unauthorised actors and dependency conflicts remain blocked.

Implementation and runtime results must be recorded separately. Manual physical
tests and automated physical instrumentation are both **NOT TESTED**.

## Current local evidence

- Before the compatibility additions: full app723/723, focused48/48, Rules368/368,
  backend136/136, debug build/lint/Android-test compilation passed. Two later audit
  tests passed in a targeted65/65 run; the initial source-nanosecond fixture failure
  was retained and corrected to compare actual stored source to deletion proof.
- After compatibility additions: full Rules373/373 and backend137/137 passed with
  zero failures/skips; Python110/110 passed after a writable temporary-folder setup.
  Fresh full app727/727 passed (97 classes, zero failures/errors/skips). Debug build,
  lint and Android-test compilation passed; lint0 errors/103 warnings. The actual
  first Android runtime failure and its required corrected run are recorded below.
- Prior expense branch802489e separately passed mandatory GitHub runs42473311844
  and42473313823 (attempt2), including34 Android-emulator tests. Those results do
  not establish runtime coverage of the new Operator/capability code.
- Physical checklist: [OPERATOR_PHYSICAL_CHECKLIST.md](OPERATOR_PHYSICAL_CHECKLIST.md).

The first Android runtime run42498687272 failed because the added legacy fixture
gave the original Owner a second workspace, correctly triggering explicit selection.
See OPERATOR_CHANGE_REVIEW.md for the dedicated-account fixture correction and
remaining fresh Android runtime gate. No production app selection policy changed.
