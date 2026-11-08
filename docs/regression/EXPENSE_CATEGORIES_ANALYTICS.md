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
editor now round-trips the exact finite non-zero amount and retains its sign.
This does not change investment, profit, beneficiary or cash calculations.

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
These policies are approved; the borrowing/loan/settlement code is **not implemented
by this category phase**. Shared-expense personal debts remain deferred.
