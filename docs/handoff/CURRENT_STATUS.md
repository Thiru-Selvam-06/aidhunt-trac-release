# TRAC development handoff — 14 November 2026

This is a checkpoint for continued development, not a release approval. The
complete original request is in `ORIGINAL_MASTER_PROMPT.md`; subsequent approved
decisions are in `APPROVED_POLICIES.json` and below. Preserve both. Do not treat
the numbered sections of the master prompt as 30 completed implementation phases:
the implementation phases are 0–22, with additional process/deliverable sections.

## Exact source state

- Repository: `Thiru-Selvam-06/aidhunt-trac-release`.
- `main` remains at `24d7b91e0898a1eacb8a1daf6ff1ce64779e28ce`.
- Draft, unmerged PR stack: offline sync #3, Tamil #4, customer identity #5,
  core edits #6, expense categories #7, Operator customers #8, paise precision
  #9. PR #9's verified source commit is
  `ca57a4b5dbb2612ad94e3167be6e07b6ea6e7b07` and includes all earlier
  stacked branches. Its exact-head push and PR CI succeeded. The earlier Android
  API-24 lint failure was fixed without weakening checks.
- `codex/durable-partner-label` is a separate pushed **WIP** branch based on PR
  #9. Its source is not release-ready: affected report screens receive only
  active members, so a retained removed Partner is absent on a fresh device.
  Two files changed; 19 focused tests passed, but Android integration and full
  CI are pending. Fix the data flow and verify before a PR or release claim.
- Production Rules, Functions, Worker, APK, live records and local pending
  operations were not changed by this work. No branch above is merged to main.

## Progress by original phase

- Phase 0 investigation completed.
- Phases 2–4 (identity, profile editing, individual-job reassignment), 7 (job
  terminology), and 10 (expense categories) implemented on development branches.
- Phase 1 is partial because the exact original rejected phone operation is
  unavailable; source-based and synthetic scenarios exist but cannot prove the
  original live authorization failure.
- Phases 5–6, 8–9, 11, 13–14 and 18–22 are partial. Job edits, some audit,
  permission, search/report, precision, expense/investment and test work exists;
  full original acceptance and integrated release verification remain open.
- Phase 12 and phases 15–17 (external loans, partner borrowing approvals,
  settlements and notifications) remain pending.
- User explicitly deferred new shared-expense personal debts; preserve existing
  investment accounting and trace this exception without silently claiming
  completion of unrelated funding behavior.

## Critical prerequisite and original incident

- A local Firestore-emulator test reproduced concurrent shared-funds overspend:
  two authorized partners each withdrew Rs 700 against the same Rs 1,000,
  leaving minus Rs 400. Current device-local validation is insufficient.
  Implement an atomic, authoritative control across every cash/claim writer,
  with versioned receipts/reservations and old-client bypass protection, before
  enabling loans or borrowing. Do not invent opening Cash/Bank balances.
- The original rejected job and payment payload, authenticated UID and exact
  failed Rule condition were not recoverable from screenshots. Do not claim a
  confirmed cause for that live operation or auto-retry it.
- Preserve Room/outbox command IDs, actor/workspace, amounts, original time,
  attribution and dependency order. Do not rewrite customer history.

## Additional approved decisions

- Original creator displays Owner until a first Partner joins; thereafter all
  people display Partner even if the last Partner leaves. Actual Owner
  administration permissions remain unchanged. Operator remains Operator.
- Owner-only historical-job creation means the Old Entry flow. Authorized
  Partners may correct an ordinary job's work date.
- Active Operators may create basic customer profiles but gain no customer
  balance, collection or financial-report permission.
- Use consistent two-decimal paisa precision. Three equal shares display as
  33.33 percent each; assign indivisible paisa deterministically so amounts
  reconcile exactly. Do not alter historical recorded amounts.
- External loans: active Owners/Partners, manual principal/interest, Business
  Cash, Business Bank or Direct to Supplier destination; repayment only from
  Business Cash/Bank for now. A direct supplier loan links one expense and
  creates no fictitious cash movement. Preserve old Cash/UPI/bank records.
- Business borrowing protects current partner entitlements and approved
  commitments. Business is its only creditor; no automatic future-profit
  deduction. Named-partner borrowing needs lender approval/reservation.
- Direct repayment to a lending Partner requires recipient confirmation;
  authorized payer may confirm a repayment into the business.

## Required next implementation sequence

1. Verify Git and WIP Partner-label source; route verified retained membership
   into presentation without adding removed people to active financial selectors.
   Add Android coverage and focused tests, then publish a reviewed PR.
2. Inventory all shared-funds writers; reproduce and close the two-device race
   in demo Firebase with strict Rules and atomic backend receipts. Include old
   APK/protocol compatibility and reviewed opening-control design.
3. Extend durable Room/outbox/ACK and two-device reconciliation for new money
   commands. Then implement external loans, borrowing, approvals and settlements
   in dependency order, with targeted tests per change.
4. Complete notifications, bilingual UI, analytics/reporting and audit archival.
   Reconcile every original master-prompt acceptance criterion explicitly.
5. Run full exact-source Android/Room/JUnit, Rules, backend, Firebase-emulator,
   GitHub Actions and cross-device synthetic regression at integration milestones
   and final release gate. Build installable demo APK checkpoints after coherent
   milestones. Manual and automated physical-device testing are **NOT TESTED**.

## Release boundaries

Use the existing demo/staging test infrastructure and synthetic data. Never
deploy live Rules, Functions, Workers or APKs, initialize live balances, merge
to main, or recover/retry rejected live transactions without separate explicit
approval. The existing Cloudflare staging preview is not proven isolated from
live Firebase. A final APK must be compatible with its deployed protocol and
original signing identity before updating the customer app. Keep actual test
failures and skipped hardware checks visible.

Relevant checked-in sources include `firestore.rules`, `firebase.ci.json`,
`firebase.device-test.json`, `docs/OFFLINE_SYNC_RELIABILITY.md`,
`docs/PAISE_PRECISION.md`, `docs/regression/WORKSPACE_PROTOCOL_ROLLOUT.md`,
and the GitHub Actions workflows. The original private release key, production
Firebase configuration, account credentials and customer exports are intentionally
not part of this handoff.
