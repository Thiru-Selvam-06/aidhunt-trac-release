# Expense categories and refund-safe reporting — development phase

This worktree starts from core source `0b02a84` on
`codex/expense-category-enhancements`. The separately published core branch has
additional test-evidence documentation. Neither branch authorizes production
deployment, live balance initialization or recovery of rejected customer actions.

## Behavior and storage

- Reuse the existing standalone expense form and job expense sheet. Expand built-in
  categories, retain historical Other/Others and expose + Add Category in the app.
- Custom names are user data. They are not translated. Only built-in app labels and
  controls use Tamil. The job expense sheet's previously English-only labels now
  receive the app language without changing stored funding values or person names.
- Category identity normalizes case, Unicode NFC and repeated whitespace. A stable
  positive SHA-256-derived 63-bit identifier fits the existing outbox/entity model.
  Mapping checks the full normalized key and hash; a collision fails closed rather
  than overwriting another category. No existing expense type is rewritten.
- Room **20 → 21** adds `expense_categories` only. The category and its original
  actor/workspace/operation/time are saved in one Room transaction. The existing
  durable queue and reconnect worker send it; there is no second queue or service.
- New server categories require an active Owner/Partner and a valid command.
  Operators may read choices but cannot add or acknowledge categories. Immutable
  name/key/creator/time cannot be edited or deleted, even by an Owner.
- Concurrent equivalent additions use the same document. A transaction preserves
  the first name/creator and changes only command acknowledgement metadata. An
  authoritative server read precedes atomic local ACK and canonical-row update.
- Workspace-scoped listeners download categories, protect outstanding local rows
  and participate in sync health. Removed memberships cannot expose cached choices.
  Categories belong to the workspace and remain usable after their creator leaves.
- Rules validate authorization, bounded shape, immutable metadata and command
  identity; Kotlin additionally enforces Unicode normalization/hash consistency.
  Rules do not claim to calculate the client's SHA-256-derived numeric ID.

## Money and reports

The old standalone editor used `amount.toInt().toString()`, dropping paise when
reopened; its positive-only check also prevented saving a retained refund. The
editor now round-trips paise. New expenses remain positive. A retained negative
refund may keep its exact amount while its description/category is corrected;
new or enlarged negative amounts remain denied. Review found current Rules also
blocked unchanged legacy refunds, so a narrow local Rule permits only retaining
the same negative amount under the existing role/time/identity protections.
This does not change investment, profit, beneficiary or cash calculations.

The expense form now waits for the Room/outbox transaction before navigating.
Local failures keep typed values visible with an English/Tamil error and explicit
retry; duplicate Save clicks are disabled while queuing. The actor/workspace are
captured before dispatch. The form's original edit snapshot enters the outbox,
so a later confirmed change received while it was open causes an explicit conflict
instead of silently replacing its starting point with a newer Room row. Rejected
payloads retain original IDs, actor and action time; cloud money is not overwritten.
An Android regression checks this using the actual repository/Room/server records.

An optional compact breakdown uses the same filtered rows as the expense list.
Category and funding totals reconcile charges minus refunds. Pie slices show
positive charges **before refunds**, explicitly labelled; refunds and signed net
amounts remain visible separately, including refund-only periods. Unsupported
funding is shown as unavailable rather than assigned to a person. Pending and
invalid-amount counts are disclosed. Existing chart rendering is reused; no
charting or monitoring dependency was added.

The existing From/To controls previously updated state that the filter never read.
Selecting a date now activates Custom range, including the final millisecond of
the selected day. Daily/month-to-date/year-to-date/custom filters use IST;
undated history remains overall-only. The list and chart share this same filter.

## Current evidence and open gates

- New focused category Rules tests: **21/21 pass**.
- Full Rules suite with the new cases: **331/331 pass, zero skips**.
- First focused Kotlin/Room run: **26 cases, 25 pass, one fixture failure**.
  The generated v20 fixture filled every TEXT column with an empty string, including
  audit history. The fixture now stores a real history event and asserts its exact
  preservation after 20 → 21, alongside original money and rejected operations.
- Expanded run: **32 cases, 31 pass**; only the new separate-window Tamil dialog
  timed out in Robolectric at `setContent`, before any financial/input assertion.
  The two real expense-editor journeys passed. The complete dialog journey and
  every assertion were moved into the existing mandatory Android Tamil suite,
  following the established real-Android coverage policy for these dialog windows.
  It is not optional and its actual execution is still pending.
- Focused rerun: **31/31 pass**. Full local regression: **699/699 pass, zero
  failures/errors/skips**, on the category checkpoint before merging the later
  replay-transport correction. These are JVM/Room/Compose results.
- Review found the app's canonical roles are title-case (`Owner`/`Partner`), while
  the first new category Rule accepted only uppercase snapshots. The local Rule
  now accepts the same-role case variants already supported by the app, still
  checking actual workspace membership and root Owner identity. Two new Rules
  tests use the actual Android role format. Updated full Rules result:
  **333/333 pass, zero failures or skips**.
- Mandatory Android instrumentation now includes offline category creation,
  exact operation ACK/idempotency and download into another authorized actor's
  empty Room database. Compilation/execution results must be recorded separately.
- Full unit, build/lint, CI, independent review and manual/automated physical-device
  verification remain release gates for this phase. Physical tests: **NOT TESTED**.

### Final merged-source local checks — 8 November 2026

- Full app JVM/Room/Compose regression: **706/706 pass**, 93 classes, zero
  failures/errors/skips. The focused edit/form run passed **33/33** before the
  final unchanged-refund boundary was added; the full run includes that correction.
- Full Rules: **337/337 pass**, zero failures/skips. Existing positive-to-negative
  denial remains. Historical-refund metadata preserves the exact negative amount;
  enlarging/changing the negative amount, foreign/other-Partner writes, and new
  negative creations are denied.
- Final `assembleDebug`, `lintDebug`, and `compileDeviceTestAndroidTestKotlin`
  passed. Lint: **102 warnings, zero errors**. Compilation includes the new category
  and stale-expense conflict journeys.
- Interaction inventory: **927** candidates; suite inventory: **93** unit classes,
  **18** backend files, **6** runnable Android classes and **3** pre-existing blocked
  classes. No drift. Inventories are source checks, not runtime UI passes.
- These results are archived separately from core results. New Android journeys
  must still execute on the expense branch in mandatory GitHub CI. Manual physical
  and automated physical instrumentation are **NOT TESTED**.

Self-review retained strict workspace/member authority, original operation IDs/
times/creator/beneficiary, immutable category provenance and server conflict checks.
No new service/dependency or financial formula change was added. The Room migration
is additive and the existing rejected-action recovery policy is unchanged. No
production records, pending live actions, Rules or deployments were modified.

### First GitHub run and verified follow-up

Published checkpoint: `6a247ce`; draft PR #7 targets the core development branch.
GitHub run **42472073633 failed**. Backend/Rules/build-only Worker jobs passed.
The first harness failure was `test_new_operation_entities_require_explicit_role_matrix_expectations`:
the explicit permission matrix lacked the newly added `EXPENSE_CATEGORY` entity.
The existing guard remains unchanged. The matrix now explicitly accepts only
Owner/Partner UPSERT, denies every category DELETE and Operator write, and tests
forged category creators under every role. Updated focused Kotlin run:
**54/54 passed**, zero failures/errors/skips. The unchanged Python suite now passes
**110/110**; the first run correctly reported109/110.

The Android compilation, four partitions and emulator job did not reach app tests:
the Gradle9.3.1 distribution download failed with `SocketTimeoutException` at the
wrapper's10000ms network limit. Only `gradle-wrapper.properties` download timeout
is now120000ms; the distribution/version and URL validation stay unchanged. No
application synchronization or test timeout was increased and no gate was disabled.
A fresh exact-head GitHub run must prove the corrected download and all tests.

## Approved later financial decisions

New external-loan destinations are Business Cash, Business Bank or Direct to
Supplier. Old Cash/UPI/bank records stay unchanged; no balances are silently seeded
or reclassified. Direct supplier funding must link to exactly one expense without
increasing business-held cash. Principal is liability movement, not customer
revenue or a repeated operating expense; interest is entered separately.

Business borrowing protects currently withdrawable partner entitlements and
approved commitments first. Example: 20,000 confirmed cash minus 16,000 entitlements
minus 1,000 commitments leaves 3,000 available. Named-partner lending remains a
separate approval/reservation path. There is no automatic future-profit offset.
Business repayments may be confirmed by the authorized payer; direct repayment
to a lending Partner still requires that lender's confirmation. External-loan
repayments use Business Cash/Bank only for now; personal principal contributions
are not enabled. Operators may add basic customer profiles in the next phase,
without access to customer balances, collections or financial reports.
These policies are approved; the borrowing/loan/settlement code is **not implemented
by this category phase**. Shared-expense personal debts remain deferred.
