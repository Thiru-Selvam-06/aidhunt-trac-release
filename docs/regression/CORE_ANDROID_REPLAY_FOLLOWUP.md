# Android replay verification follow-up — 8 November 2026

The local core source at `0b02a84` passed 686 JVM/Room tests, 310 Rules tests,
131 backend tests, 110 Python tests, build/lint and instrumentation compilation.
The documentation head `8fdccd3` was published and draft PR #6 targets
`codex/customer-identity-editing`, not main.

## Genuine Android CI failure

GitHub push run **42382477747**, job **128432821191**, started 31 instrumentation
tests on Android API 35. It finished with **8 failures and zero skips**. The
non-device jobs passed. PR run **42383882281** also failed; it is not counted as a
successful retry. The release gate remains failed.

The first failing journey was
`DeviceEmulatorIntegrationTest.bOfflineHour18SurvivesHour26AndDuplicateRetries`.
The actual Firestore response states:

> Property editedByUid is undefined on object.

This is from `validJobEditAudit` evaluating an existing-job update: the transport
appended a new audit event but relied on the queued map already having editor
fields. That older/legacy-shaped command had none. The Rule correctly rejected
the incomplete audit; Owner privilege is not the explanation and is not bypassed.

The other failed journeys were l, n, o, p, q, s and t, ending in synchronization
timeouts. Their precise causality must be reverified after correction; do not
claim every timeout is proven to be a cascade from the first rejection.

## Narrow client corrections

- `FirestoreRepository.saveTimedDocument` reads the actual actor's member document
  inside the existing transaction before appending audit history. Editor UID and
  edit time come from the retained operation, not the original creator/beneficiary
  or current wall clock. Missing/removed/foreign/invalid memberships fail closed.
- `JobEditorMetadata` preserves an original editor-name snapshot only when bound
  to this same operation actor; otherwise it uses that actor's canonical member
  label. No creator, beneficiary, amount, operation ID or original timestamp is
  rewritten. Role/name labels never grant authorization.
- `FirestoreQueuedJson` converts nested JSONObject/JSONArray values into native
  maps/lists/nulls before calling the real Firebase SDK. The old shallow conversion
  passed JSON container objects directly, including new-job history arrays.
  Payment snapshot extraction uses the same conversion. Allocations stored as
  strings remain strings; exact 63-bit IDs remain Longs.
- `FirestoreQueuedJsonTest` exercises the actual Firebase SDK serializer, including
  nested audit events, exact large IDs, explicit nulls and raw Tamil/user text.
  It also checks editor identity, original action-name preservation and membership
  denial cases. Emulator instrumentation remains mandatory for the wire behavior.

No Rule has been relaxed, no test assertion has been removed and no timeout has
been extended to hide the failure. The original live phone failure still lacks
its retained actor/payload evidence and is not retrospectively declared proven.
Previously rejected live financial commands remain untouched; this follow-up
does not automatically reopen them.

## Verification gate

The first SDK conversion checks passed locally before the final editor-binding
guard was factored. Run and record the complete corrected source, then publish
the development revision so Android instrumentation can genuinely execute again.
Do not equate compilation with emulator execution or emulator execution with
physical-device testing. Physical manual and automated verification: **NOT TESTED**.

The separate local software-emulator attempt never reached usable ADB state and
executed no tests. Its owned test processes were stopped. Production Rules,
customer records, existing SQLite files and live pending operations were not
modified by this investigation or these synthetic tests.

## Corrected run and final fixture alignment

At `ce0b6a6`, the full local run passed **691/691 tests with zero skips**. Android
push run **42452607705**, job **128644840364**, finished all **31 tests** with
**29 pass / 2 fail / zero skips**. All previous synchronization timeout journeys
passed, including offline jobs/collections/expenses, independent Partner readback,
profile editing, paid reassignment, original-clock confirmation and the new Partner
historical-job audit journey. Non-device CI jobs passed.

The two remaining failures do not justify changing financial or authorization rules:

1. Journey b still expected a Partner's 26-hour edit to fail, contrary to the newly
   approved unrestricted existing-job edit policy. It now verifies successful
   Partner edits/retries and original creator/time, then separately verifies a
   seeded existing Operator's 26-hour edit is denied. The original rejected-payload,
   actor/time, unchanged server state and Room recovery assertions remain mandatory.
2. Journey d copied the newly populated audit from its template into a different,
   supposedly fresh job. A fresh synthetic fixture now starts with no inherited
   history/editor fields. Production transport does not erase audit data. A new
   Rules case explicitly rejects importing another job's history on creation.

The updated Rules suite passed **311/311**. Updated Android instrumentation
compilation passed. The corrected runtime journey must still execute in CI; it
is not counted as a pass at this checkpoint. No timeout or required gate was
removed, and no expected financial amount was changed merely to hide a failure.

## Verified final core Android result

At `ae16cdf85949d93357cdaf0b12b505240377c569`, both GitHub runs
**42454493370** and **42454497425** completed successfully, including the mandatory
aggregate gate. Android job **128650518601** actually ran **31/31 API35 emulator
tests, zero failed and zero skipped**, with BUILD SUCCESSFUL. This closes the
core runtime gate above. It does not cover later expense-category Android changes
or physical phones. Physical manual/automated tests remain **NOT TESTED**.

The separate Cloudflare-managed check still fails in its automatic preview command;
its dashboard error is documented in `CLOUDFLARE_BUILD_FAILURE.md`. The repository's
build-only Worker job passed; the external deployment integration was not modified.
