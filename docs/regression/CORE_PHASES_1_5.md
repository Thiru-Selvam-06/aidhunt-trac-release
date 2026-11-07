# Approved core enhancements — Phases 1–5

Based on development revision `365c0a4`, branch `codex/core-enhancements-phase1-5`.
The user approved the Phase 0 recommendations on 7 November 2026. This scope is
development and isolated verification, not production Rules deployment, live
recovery, main merge or customer rollout.

## Resulting behavior

- Active Partners can edit an existing ordinary/historical job across creators
  and ages. Historical creation stays Owner-only. Operators retain own ordinary
  job/24-hour restrictions; deletion, expense and Collection rights are separate.
- Original creator UID/role/time and financial beneficiary remain unchanged.
  Received money changes only through Collections. Invalid invoice corrections
  below received money are rejected before persistence.
- Job context says Entry by and Entry for Operator. This is a wording change;
  the selected UID still has its existing financial attribution and initial
  collector meaning. No new Operator membership privileges are introduced.
- Recording/edit instants use explicit India time, 12-hour AM/PM and IST. Work
  dates on historical jobs remain date-only. Missing evidence displays a dash.
- Each new-client edit retains operation ID, actor, original action time and
  before/after business fields in append-only job history. Room caches the history;
  server transactions build exact snapshots and the ACK reads them back when
  needed. A later pending edit protects its own local row.
- Balance Sheet is Amount Received, Expenses and Net Balance. ACTIVE receipts
  group by actual collection date in IST; reversals restate that period. Every
  business cost stays included, including personal funding. This net is distinct
  from cash and profit entitlement. Existing profit/investment formulas are unchanged.
- Receipt-only periods appear. Legacy receipts without a reliable date stay in
  overall totals and are disclosed as undated; they are not allocated to fictional
  dates. PDF uses the same period figures, including an undated row when needed.
- Customer search ranks prefix names before substring matches and alphabetizes
  before the suggestion limit; IDs/raw names are unchanged. Empty search retains
  existing Dues/History ordering. English/Tamil app labels do not translate data.

## Storage and compatibility

Room migration **19 → 20** adds only `job_entries.jobEditHistoryJson`, non-null,
default `[]`. It deletes no row/table and rewrites no existing financial value.
Firestore's additive `jobEditHistory` array contains primitive-valued before/after
maps. New creates have an empty history. The current local Rules validate each
append, original actor and action time, prevent erasure, and preserve all existing
workspace/receipt/purge locks. Customer-reassignment history remains independent.

History is bounded at 100 events to avoid unbounded growth of a Firestore job
document. At the bound the next edit is refused for archival review; history is
never silently discarded. Historical events are not fabricated. A separately
reviewed archival mechanism is needed for jobs reaching the limit.

Old records without history remain readable. Older clients can be denied when
editing a job that now has protected history rather than being allowed to erase
it. Production needs a reviewed matching APK/Rules rollout to all editing Partners.
Do not distribute this code as a working production permission expansion while
the old Rules still enforce the prior policy.

This phase does not implement shared withdrawal solvency, loans, borrowing,
settlements, custom expense categories or activity notifications. They remain
separate approved-scope/financial-design work. The existing Business Paid negative
cash flexibility and current investment/profit math are unchanged.

## Test policy changes versus defects

Existing Partner-denial assertions for old/cross-creator/historical **edits**
conflict with the newly approved requirement. Their edit expectations are updated;
the original 24-hour and missing-provenance denial cases remain covered for
Operators. Partner historical creation/deletion/forgery denials remain mandatory.
No financial expected amount is changed to make an incorrect result pass.

The first local focused run had 58 cases / 8 failures: obsolete Partner assertions,
a retained missing-creator safety expectation, JSON parsing in plain JVM mapping
and Windows test-path limits. These are recorded, not counted as passing. The
missing-creator guard remains denied; empty-history mapping does not require an
Android JSON call; the local test temp root and timestamp fixture name are shortened
without changing timestamp assertions. A subsequent result must be recorded for
the final source before release readiness is claimed.

The initial Rules run also exposed obsolete Partner-deadline denials. Those cases
now preserve the unchanged Operator deadline behavior. The subsequent local demo
Rules run executed **310 tests, 310 pass, zero failures/skips**. This does not
certify an unexecuted Android journey or authorize production deployment.

`CoreEnhancementTest` covers collection-date/midnight/restatement math, expense
inclusion, prefix ranking, separate create/edit/delete policies, immutable audit,
explicit additive migration, Room reopen and invalid invoice corrections.
`DeviceEmulatorIntegrationTest.tPartnerOfflineEditsOwnerHistoricalJobWithoutChangingReceiptOrCreator`
is mandatory in the existing instrumentation class: it checks retained offline
operation, ACK, original receipt/creator/money, audit ID and independent Owner view.
Its actual execution is separate from compilation and must be reported honestly.

## Acceptance and rollback

Manual physical tests: **NOT TESTED**. Automated physical instrumentation: **NOT TESTED**.
Use approved isolated staging accounts/storage; manual two-phone testing needs no USB.

1. Record exact app/Rules revision, signed-in UID and workspace on both phones.
2. Owner creates a historical paid job; Partner edits only notes/customer-permitted
   job fields offline, closes/reopens, reconnects. Verify original creator/date,
   receipt ID/amount/collector and due stay unchanged; only approved fields change.
3. Another Partner and Owner see the confirmed edit and identical audit operation.
   Operator cannot edit the Owner job; Partner still cannot create a historical job
   or gain a new deletion privilege.
4. Collect 500 then 4,500 on different days/months. Verify receipt-only period,
   total 5,000, correct reversal period and identical PDF rows. Test IST midnight
   on a phone set to another timezone. Keep expense/net distinct from cash.
5. Search R/Pa and Tamil prefixes on forms, Dues and History. Prefixes appear
   before substrings; same-name people keep distinct IDs and entered text.
6. Inspect full audit, missing legacy times, Tamil, compact cards and large font.

Rollback must preserve Room v20, outbox and history. Do not clear data, uninstall,
blindly downgrade to v19 or restore Rules that permit history erasure. Use a
reviewed forward-compatible recovery build and compatible policy. No live pending
operation is automatically reopened by this phase.
