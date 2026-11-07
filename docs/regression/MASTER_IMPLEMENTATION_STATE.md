# Master implementation decisions and gates

Development continues from `365c0a4` on `codex/core-enhancements-phase1-5`.
On 7 November 2026 the user approved continuing after the master Phase Zero report.
This authorizes development, local Rules/backend preparation, synthetic tests,
development publishing and review. It does not authorize production deployment,
main merge, live rejected-operation recovery or customer APK rollout.

## Confirmed business decisions

- Preserve current investment accounting. **Shared-expense personal debts are
  deferred**; do not add an obligation on top of a full existing investment credit.
- Business borrowing has the business as creditor, no named-partner debt and no
  automatic offset against future profits. Check confirmed available funds after
  existing commitments; no lender-approval popup for this path.
- Named-partner borrowing requires the affected lender's approval and protected
  entitlement/reservation. Multiple lenders require all approvals before a complete
  disbursement; no partial-execution policy is authorized.
- Active Owners and Partners may record external loans and repayments; Operators
  may not. Principal and interest are manually entered separately; no automatic
  interest/amortization calculation is authorized.
- Direct repayment to a lending Partner requires recipient confirmation before
  reducing debt. Repayment into the business requires confirmation by an authorized
  business member. A pending request/repayment is not final money movement.
- Retain all-cost Balance Sheet expenses (including I Paid), receipt-date reporting,
  original-period reversal restatement and overall-only undated legacy receipts.
- Entry for Operator is a scoped label; keep its existing financial beneficiary and
  initial collector semantics. Do not globally rename financial Partners.
- Business Paid expense flexibility below zero remains; this is separate from the
  prohibition on withdrawals above actual available business funds.

## Current core follow-up

The candidate is version **1.0.11 / code 12**, Room20. Core changes include Partner
existing-job edits, immutable original creator, IST/audit presentation, prefix
search, receipt-date reporting and signed-undated PDF reconciliation. The deletion
REST codec now preserves nested history and timestamp precision; immutable deletion
proofs must retain job-edit and customer-reassignment history exactly.

Existing Home retained-action cards and SQLite/Cloud original-payload details were
verified in source and preserved. They were already implemented in the customer
editing base; no duplicate recovery UI is being added. The master audit's suggested
retained preview was an incomplete assessment of that existing functionality.
The original phone's specific actor/payload failure remains unverified.

No old financial operation is automatically reopened. The user explicitly confirmed
that work-date corrections remain allowed: Owner-only historical creation means
creating through Old Entry. Preserve the existing Operator own/24-hour restrictions
and immutable historical flag, creator and recording time.

## Test evidence must remain honest

The master audit baseline was 676 unit cases / 27 failures. Missing legacy test
migration registration and primitive-only fixture encoding caused those failures.
After correcting them, the first expanded run was 686 cases / 10 failures: deletion
history fields were checked but omitted from the proof's key allowlist. Both sides
are now corrected; new Rules/deletion/full results must be recorded after execution.
No assertion was removed and no financial expected amount was changed for a pass.

Final local core run at source `0b02a84`: **686 Kotlin/Room cases, 686 pass,
zero failures/errors/skips**; **310 Rules cases, 310 pass, zero skips**;
**131 backend cases, 131 pass**; **110 Python harness cases, 110 pass**.
The first backend rerun failed its CLI fixture because the default protected
temporary directory is outside the migration tool's permitted output roots.
The unchanged suite passed with a workspace-owned temporary directory.
These are local JVM/Firestore-emulator/tooling results, not Android instrumentation
or physical-device verification. Build/lint and current-branch GitHub gates must
be recorded independently after completion.

The five-receipt Partner deletion boundary originally hit Firestore's **1,000
expression evaluation limit**, not a missing Owner privilege. Full proof checks
are evaluated on immutable proof creation; the source deletion verifies matching
guards once, and each child still requires the fresh proof, absent source and
the same active role/creator/original-time restrictions. Caching the actor's role
within each child check removes repeated equivalent evaluations. Five receipts
remain supported; no financial limit or authorization assertion was removed.

Audit history is never silently truncated. The current field implementation refuses
further edits at 100 events or a 192KiB serialized history budget, retaining the
existing record. Archival beyond this capacity remains an explicit limitation;
large-history acceptance and a coordinated old-client rollout are release gates.

## Remaining phased work

1. Finish core regression, Rules, build/lint, Android-emulator CI and review.
2. Persistent custom categories, existing precise expense editing and refund-safe
   category/funding analytics, reusing current forms and charts.
3. Authoritative atomic cash/entitlement controls and operation receipts before any
   borrowing feature. A phone-side balance alone is insufficient.
4. Separate external loan/disbursement/principal/interest/repayment model and UI,
   then business/Partner borrowing and confirmation-based settlement.
5. Durable confirmed activity/involved-party notifications, recipient language and
   idempotency; optional push needs its own justified setup/activation decision.

Every money command must preserve original actor/workspace/operation/time, avoid
duplicates and update shared controls atomically. No production opening-balance
initialization is authorized. Staging is isolated only if its backend/project/data
are actually isolated, not merely named staging.

Physical automated and manual tests remain **NOT TESTED** until actual hardware
evidence exists. Manual multi-phone testing does not require USB. Android emulator
instrumentation results are a separate evidence category.
