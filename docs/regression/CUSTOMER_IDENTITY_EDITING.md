# Customer profile edits and individual job reassignment

Development branch: `codex/customer-identity-editing`, based on `ed4c8a1`.
This is a development change, not authorization to deploy Rules, recover live
operations, merge production, or distribute a new APK.

## Confirmed diagnosis

1. The old job form cleared `matchedCustomerId` when a name was typed, then fell
   back to `originalCustomerId` on Save. The repository accepted that ID with the
   newly typed job-level name. Customer Dues resolved the canonical customer row,
   while other displays used job/payment name snapshots. This explains conflicting
   current names without requiring a duplicate customer or a financial error.
2. `WorkspaceRepository.updateCustomer` queued a whole CustomerEntity snapshot.
   `FirestoreRepository.applyPendingOperation` uploaded it with `set`, replacing
   the document. CustomerEntity cannot retain server-only creation provenance.
   The deployed `original()` validator rejects losing fields such as
   `createdByRole`, `authorizationCreatedAt`, `serverReceivedAt` and
   `authorizationTimeStatus`. The isolated Rules reproduction confirms replacement
   denial and metadata-patch acceptance for both Owner and Partner accounts.
   Documents without those extra fields can behave differently.
3. Whole customer snapshots also carried unchanged stale profile fields. They had
   no customer-specific optimistic edit base. A phone-only edit could overwrite a
   concurrent rename. A listener received while a profile was pending was ignored,
   and acknowledgement previously marked the local copy synced without retrieving
   the independent confirmed fields.
4. The old job selector could apply another customer's latest hourly rate while
   editing a job. Reopening the existing minute/whole-rupee form could also round
   untouched source times/rates/amounts. A relationship-only edit must retain them.
   The form also regenerated linked-expense descriptions/dates even when only the
   customer changed. It now retains the captured expense history, and the repository
   does not queue an unchanged linked Expense as a new expense edit.
5. The deployed Rules permit an authorized customer reassignment, but require an
   existing receipt's immutable customer ID to equal the job's current customer ID
   during reversal. Partner compound deletion has the same assumption. A paid move
   could therefore succeed and later make reversal/deletion fail. Synthetic tests
   reproduce this without changing real records.

The reported phone is signed in as Muniyappan, version 1.0.9. Its message says
the edit was saved locally but not synced. The original UID, operation payload,
customer/job IDs, Android build and exact backend error are unavailable. The
conditions above are source-confirmed/reproduced defects, **not proof of which
condition rejected that particular original phone operation**. Existing creator,
active-member, historical-entry and 24-hour restrictions remain intentional.

Read-only deployed Rules inspection on 2026-10-23:
`04507f3f22acc160cef4025e72f924ae9478cdf4d5f1d770d95361c4b5974890`.
The development candidate contains earlier approved prototype changes as well;
the customer replacement failure reproduced against both policies. A policy
mismatch alone is not asserted as the original phone's cause.

## Resulting data flow

- Customer identity remains `(workspaceId, customerId)`. No schema/database
  migration, name-based merge, normalized stored customer name, new database or
  replacement synchronization engine is introduced.
- Profile dialog edits name and optional phone. It captures before/after values
  only for fields actually changed. Room and the existing durable operation queue
  are updated in one transaction. Metadata updates preserve financial fields and
  original provenance. Unchanged fields can merge; a changed field with a different
  server value is explicitly rejected for review. The original payload is retained.
- Firestore uses an existing-queue customer transaction with an operation-ID
  acknowledgement check. Profile changes use `update`, never whole-document
  replacement. Older pending customer edits without an edit base fail closed for
  review instead of overwriting an existing customer. Rejected actions are not
  automatically retried or rewritten.
- After a profile patch is confirmed, the acknowledgement reads back the canonical
  customer. A newer pending local profile protects its row from replacement.
  Authoritative customer listeners can repair an unsynced marker with no pending
  profile edit, while continuing to derive financial totals from jobs.
- `CustomerIdentity` projects current names/phones/villages for UI jobs and current
  receipt displays using scoped IDs. Stored snapshots remain untouched. Home,
  reports, details, Collection History and newly generated PDFs consume these
  ViewModel projections. Search uses Unicode NFC and case folding for comparisons
  only. Same-name customers remain separate records.
- Editing a job requires selecting its customer ID or explicitly choosing Create
  New Customer. A new identity is allocated once in the draft and created inside
  the existing job transaction; its queued customer operation precedes the job.
  Reassignment does not globally rename a customer. Existing work fields remain
  original unless the user actually edited those fields.
- Single-job receipts retain their original IDs, customer snapshots, amounts,
  collectors and timestamps. Current display grouping follows the job's current
  customer. No receipt is recreated. Old/new customer dues derive from the same
  unchanged job amounts; the financial calculation engine is unchanged.
- Shared allocations, missing/inconsistent payment history and unresolved local
  payments block reassignment. They require separate review; no automatic repair
  or retry occurs. An embedded legacy paid amount without receipts is also blocked.

## Local prototype Rules correction

User approved preparing/testing this correction, not production deployment.
New collection creation still requires the current job customer. An unchanged
single-source receipt can be reversed/deleted by its immutable workspace/job link
after reassignment. Amount deltas, collector authorization, time limits, locks,
immutable receipt fields and the atomic job/receipt write remain enforced.

Future client moves append old/new customer ID, actor UID, original action time,
operation ID and source financial totals to `customerReassignmentHistory`. The
Rules validate the append and preserve existing history. Older clients/records
without this field retain the existing valid reassignment behavior. Once history
exists it cannot be removed or rewritten by later edits. No historical records
are migrated. Existing audit coverage on older actions is not retroactively invented.

I've set up prototype Security Rules to keep the data in Firestore safe. They are
designed to be secure for workspace-isolated, authorized single-job receipt
reversal and protected reassignment history. However, you should review and verify
them before broadly sharing your app. If you'd like, I can help you harden these rules.

## Changed files and responsibilities

| File | Reason |
| --- | --- |
| `data/sync/CustomerProfileEdit.kt` | Changed-field intent, idempotent merge, same-field conflict detection. |
| `data/sync/JobCustomerReassignment.kt` | Single-job allocation and unresolved-payment safeguards. |
| `data/sync/ServerActionTimeBarrier.kt` | Confirm server eligibility before writing without changing original timestamps. |
| `data/firebase/FirestoreServerTime.kt` | Read-only scoped Firebase/Auth/App Check server readTime, with API 24/25 timestamp parsing. |
| `ui/util/CustomerIdentity.kt` | Scoped current display projections and Unicode search. |
| `data/repository/WorkspaceRepository.kt` | Atomic profile edits, explicit new identity, reassignment validation, safe acknowledgement/reconciliation and current-job reversal totals. |
| `data/firebase/FirestoreRepository.kt` | Profile transaction/patch, protected provenance, cloud receipt checks and append-only reassignment audit. |
| `data/sync/EntryDeletionPolicy.kt` | Validate exact single-job receipt links after a move without rewriting historical customer identity. |
| `ui/viewmodel/MainViewModel.kt` | Reactive canonical display flows and explicit editing parameters. |
| `ui/viewmodel/NewEntryDraft.kt` | Explicit new-customer intent and preservation of untouched work values. |
| `ui/screens/entry/NewEntryScreen.kt` | Scoped suggestions, explicit selection/create action, no edit-time rate replacement. |
| `ui/screens/report/CustomerCreditDueTab.kt` | Existing dialog extended to name/phone, validation and local-save callback; Unicode search. |
| `ui/screens/report/ReportScreen.kt`, `MainActivity.kt` | Forward the edit base and callback; truthful local-save wording. |
| `firestore.rules` | Approved local single-source reversal/deletion correction and reassignment-audit validation. |
| `CustomerIdentityEditingTest.kt` | Identity, conflicts, financial invariants, preserved snapshots and 100/1,000/10,000 customer projections. |
| `ServerActionTimeBarrierTest.kt` | Small/large skew, stalled/unavailable clock, restart, strict UTC parsing and no invented server time. |
| `OfflineSyncReliabilityTest.kt` | Room restart preserves a pending future-clock job and its dependent payment unchanged. |
| `DeviceEmulatorIntegrationTest.kt` | Actual Android Room/repository/Auth/Firestore offline profile merge and paid reassignment/reversal journeys. |
| `CustomerEditingDeviceTest.kt` | Mandatory English/Tamil dialog, validation and callback journey. |
| `security-tests/customer-editing.test.cjs` | Reproduce old replacement denial, valid patching and paid-move policy behavior; negative authorization checks. |
| `scripts/run_backend_suite.mjs`, `scripts/instrumentation-plan.json` | Make added tests mandatory in existing CI. |
| `.github/workflows/android-ci.yml`, `scripts/run_device_ci.sh` | Required build-only Worker validation and retained demo-only authorization evidence. |
| `cloudflare/package.json` | Build the existing cf/Vite project from its correct directory; no dependency or deployed setting changes. |
| `docs/handbook/test-suite-inventory.json` | Updated source inventory; does not claim execution. |

## Acceptance and test evidence

The existing tests and their business expectations are preserved. New tests are
added to the existing mandatory gate. No tool, paid service, monitoring platform,
dependency, workflow bypass or optional replacement test is added.

| Requirement | Added coverage / required evidence |
| --- | --- |
| CUST-001/002/010 | Same-ID capitalization rename; independent same-name IDs; no name merges. |
| CUST-003/004 | Scoped current display projections; raw jobs/receipts and money unchanged. |
| CUST-005–009 | Actual Android offline profile edit, second Room connection, independent authenticated Partner phone edit, reconnect/ACK/server equality and replay. |
| CUST-011/012 | Denied roles/workspaces/actors, invalid name, phone-only field merge. |
| JOB-001/002/008/014 | Explicit scoped IDs, only chosen job moves, 800/1,500/2,300 invariant and same selection. |
| JOB-003/010/013 | Stable explicit new ID, actual repository transaction, pending operation dependencies and idempotent replay. |
| JOB-004/005 | Unicode/case search, no automatic merge, explicit selection. |
| JOB-006/007 | Partial/full payment policies, original receipts unchanged, actual partial receipt reversal and Rules checks. |
| JOB-009 | Partial/fully paid actual-repository moves with a linked business expense; its exact server fields and local history must remain unchanged. |
| JOB-011/012 | Existing role/deadline restrictions and shared-allocation blocking. |

Execution results are recorded separately in the final engineering report. A test
source or checklist is **not** a passing result. Original phone failures remain
unverified until their retained operation and authorization context can be inspected.

## Remaining limitations and release safeguards

The mandatory Android-emulator run at `ccc8d21` exposed two additional defects
before release. Profile patch writes reached the server but acknowledgement called
an explicit server-read helper that did not yet support `CUSTOMER`. That helper now
uses the existing workspace-scoped `customers` path. Paid reassignment copied the
server's whole-number audit amounts using `getDouble`; Firestore Rules compare
the complete audit map and reject the changed integer/double representation.
A local REST transaction reproduced the rejection with converted values and
accepted the exact original numeric types. The client now copies those raw values;
the authorization validator and financial amounts are unchanged. The existing
mandatory reconnect journeys retain every assertion, with an additional Rules
wire-format regression. Final rerun results must be recorded separately.

- No automatic duplicate/orphan cleanup. No current production-customer inventory
  was downloaded for this change; legacy duplicate/orphan counts are NOT VERIFIED.
- Operators cannot read canonical financial customer profiles under existing
  policy. Their fallback job snapshots remain available. Extending live canonical
  metadata access to Operators would require a separately reviewed data-access plan.
- A job reassignment scans the workspace collection list to detect shared legacy
  allocations, then transactionally verifies only relevant receipts and the job's
  unchanged base. Large legacy collection sets have an additional read cost;
  customer projection benchmarks do not measure this cloud cost.
- Production still runs the inspected deployed Rules. New paid reassignment must
  not be released before its compatible reviewed Rules are deployed with approval.
  Old APKs editing a record with new audit history may need the update to preserve
  that history; they are rejected rather than allowed to erase it.
- An original rejected edit must remain untouched. A future recovery needs the
  original actor/workspace, payload/base, current cloud state and authorization
  verified first; then a reviewed new action or explicitly authorized retry. Never
  change original IDs/timestamps/amounts or clear the queue.
- Rollback: do not blindly reinstall an APK that drops audit history. Preserve
  Room/outbox, keep compatible Rules, and pause new customer moves while a fix is
  reviewed. A Rules rollback needs a separate review for newly reassigned records.

## Physical-device verification — NOT TESTED

### Follow-up verification after 7c12a1f

The required PR run 40010088695 passed all 1,123 JUnit cases, including all 28
Android API 35 cases. The earlier same-source push run 40009468083 failed four
Android cases. Follow-up commit 24f4dc6 added diagnostics: its push run 40054802905
passed, while PR run 40054806456 failed five Android cases. The retained Rules
evaluations now prove the condition for that failed PR run:

| Synthetic request | Original action ahead of actual Rules request.time |
| --- | --- |
| Fresh Owner job, case d | 101 ms |
| Owner deletion guard, case e | 47 ms |
| Cold-start job, case i | 88 ms |
| Customer creation before profile journey p | 34 ms |
| Customer creation before reassignment journey q | 31 ms |

Request Auth UID and actor were both `device-owner`; active Owner membership was
confirmed. The strict future-action predicate correctly denied these writes.
Customer rejection blocked its dependent job/payment, causing setup timeouts.
This proves the intermittent CI failure, not the unavailable historical phone
operation. Its original payload and exact policy failure remain unverified.

The client now checks a scoped membership batchGet's server readTime before a
mutation, using its existing Firebase user token and available App Check token.
It caches only a confirmed lower bound per actor/workspace, never a device-clock
estimate. Skew up to two seconds waits once and reads again. Larger or persistent
skew throws a transient CLOCK_AHEAD error: the original Room row/outbox remains
PENDING, and related payments wait. No original timestamp/amount/ID changes, no
probe document, no Rules relaxation, no schema migration and no automatic replay
of previously REJECTED actions are introduced. The read adds at most one normal
membership read per newly timed group of actions, plus a second after a small wait;
older queued actions can reuse the confirmed bound. Network failures stay pending.
Very large original future timestamps may require separately approved review;
changing the phone's present clock cannot rewrite an already-recorded timestamp.

The mandatory device suite now records demo-only failure evidence: original
queued operation IDs, actors, workspace, timestamps, states and selected identity
fields, plus current Auth/token UID, read-only server readTime and membership.
Compact operation diagnostics omit tokens, customer names and phone numbers. The
separate Rules-coverage artifact contains demo fixture values and token claims,
and is collected only by the guarded synthetic CI harness, never from live data.
The artifact also retains host/guest clock observations.
Initial profile/move setup reports an actual retained job rejection rather than
waiting for a generic timeout. Rules and rejected-action retry policy are unchanged.

The separate Cloudflare build failed because its existing trigger runs Wrangler
preview from repository root, while this repository's existing cf/Vite project
is in `cloudflare/`. An explicit `npm run build` now invokes the existing `cf build`
there, and a required Linux CI job validates it without deployment or credentials.
The cloud trigger has not been changed. After local build verification, separately
review the trigger root (`cloudflare`), cf-compatible preview command and isolated
preview bindings/secrets before approving any trigger change. Do not blindly add
an empty Wrangler preview block or reuse customer resources for branch previews.

Automated physical-device testing: NOT TESTED. No phone/ADB instrumentation was
performed for this change. Android-emulator tests must be reported separately.

Manual three-phone testing: NOT TESTED. USB is not required. Use an isolated demo
or explicitly authorized staging workspace and synthetic data, not customer money.

1. On each phone record APK version/commit/hash, Android version, actual signed-in
   UID, workspace ID and verified role. Do not infer authorization from a name.
2. On Phone A create QA customer A with jobs 800 and 1,000 due. On Phone B create
   QA customer B with 500 due. Confirm both devices show the same IDs and 2,300 due.
3. On A rename A's profile `saga jothi` to `Saga Jothi`, keeping the phone. Capture
   the original ID, operation ID and Pending status. Restart while offline. Confirm
   the pending edit and old/new values survive; do not clear data.
4. While A is offline, B edits only A's phone. Reconnect A. Capture both operation
   acknowledgements and server-confirmed name/phone. Check A/B/C Dues, details,
   history, recent jobs, Collection History, search and a newly generated PDF.
   All current customer labels should agree; amounts and stored audit remain intact.
5. Repeat a conflicting same-name edit on two phones. Expect explicit review,
   retained original operation and confirmed shared state; no silent last-write win.
6. Move the 1,000-due job to existing B using selection. Expect A=800, B=1,500,
   total=2,300, same job ID and unchanged creator/beneficiary/tractor/work fields.
   Repeat offline and after restart. Record the job/customer operation IDs and audit.
7. Explicitly create a same-name different customer and move only one eligible job.
   Expect one distinct new ID. Repeated Save/reconnect must not duplicate it.
8. Test a partial and fully paid single-job receipt. Capture original receipt ID,
   amount, timestamp and collector. Reassign, then reverse as an authorized member.
   Confirm the receipt is unchanged until the explicit reversal; no duplicate cash.
9. Test a shared receipt and a rejected/pending payment. Expect a precise block,
   unchanged association, balances and retained actions. Never clear/retry them.
10. Verify authorized Owner/Partner versus restricted Operator, historical and
    expired edit windows. Test English/Tamil dialog, optional blank phone, invalid
    phone, blank name and no translation of entered business/customer text.
11. Save screenshots plus per-device operation status/IDs and approved read-only
    server evidence. Record PASS/FAIL only after each real scenario is executed.
12. Record automatic date/time settings and any CLOCK_AHEAD warning. A clock wait
    must retain the job and unchanged payment action; do not recreate it or edit its
    timestamp. Test normal offline reconnect first; do not change production phone
    clocks or recover a historical rejection as part of this checklist.
