# Workspace protocol compatibility and release gate

## Confirmed development compatibility defect

The new metadata projection and custom-category features use collections that an
older Rules release does not recognize. Unconditionally reading/writing those
collections would reject otherwise valid customer operations or mark listeners
unhealthy. Once a directory exists, older clients also cannot maintain its metadata
atomically. This is a source-confirmed rollout defect, **not proof of the exact
original phone's rejected payload or payment amount**.

`syncCapabilities` on the workspace root controls the additive protocols:
`basicCustomerProfilesV1` and `expenseCategoriesV1`. Missing, malformed and nonboolean
values are disabled. Ordinary client workspace creation cannot enable flags;
ordinary client updates cannot change/remove them. Activation requires a separately
approved trusted administration operation after validation. No activation is included
in this development change.

## Client behavior

- Disabled: existing finance-authorized CUSTOMER creates/edits and checked deletions
  avoid the directory collection completely. New category/directory listeners are
  absent from actual registrations and health expectations. Existing expense
  categories/forms and ordinary job workflows remain available.
- Enabled: customer metadata and source are committed atomically; directory entries
  have no financial fields. Operator creates require a zero-finance source plus its
  matching projection, while canonical finance reads remain denied.
- Capability values are cached only after authoritative membership/root reads with
  the same authenticated UID. Offline restoration is bound to that exact UID and
  workspace. Cache values control local UI, never server authorization.
- Every new-protocol upload rechecks the workspace root from SERVER. If disabled,
  its original action stays PENDING with a rollout-wait reason. IDs, timestamps,
  payloads and actor remain unchanged. REJECTED financial actions are not reopened.
- A fresh verified protocol change replaces listener registrations and invalidates
  already-dispatched callbacks using the existing epoch guard. It does not infer a
  cloud deletion from disabled or malformed listeners.

## Controlled release order (requires separate production approval)

1. Complete exact-source Rules/backend/JUnit/Android-emulator gates and review.
2. Confirm actual deployed Rules and all installed versions; do not infer these
   from this repository. Preserve current Rules/configuration and recovery plan.
3. Validate the new APK against a synthetic workspace with no flags and with flags
   enabled. New collections must remain denied when disabled; ordinary customer
   creation, profile edit, job creation and checked deletion must still succeed.
4. Deploy reviewed additive Rules only after explicit approval. Keep live workspace
   flags absent. Install approved compatible APKs on **every** partner device.
5. Confirm no old APK/other writer remains. Audit all trusted customer mutation and
   deletion paths. Approve any historical metadata backfill separately; none runs
   automatically and missing historical profiles are not invented.
6. With explicit approval, enable only the validated flags in the intended workspace.
   Reopen/refresh each app to obtain a fresh verified membership/capability read.
7. Verify original actor, receipt IDs, amounts, listener health and cross-partner
   records with synthetic/staging data before any customer rollout decision.

Do not switch capabilities off or reinstall an older APK once directory records
exist as an improvised rollback: older clients cannot preserve the projection.
Pause new feature use, preserve queued actions, and review a forward-compatible
repair/migration first. Keep Rules denying unmirrored changes. Backend deletion
proofs and command receipts are immutable; no old data is automatically backfilled,
deleted or retried by this release.

## Verification status

Local capability-aware full Rules suite: **373/373 passed**, zero failures/skips.
This includes legitimate disabled-workspace customer edits, denied new collections,
client activation/removal denial, active projection atomicity and all existing
financial/workspace isolation assertions. Fresh full local Kotlin/Room/Firestore-emulator app suite: **727/727 passed**,
97 classes, zero failures/errors/skips. Final build and Android runtime still require
their own results.
New Android journeys w (Operator replication) and x (disabled-protocol legacy
create/edit/delete) are mandatory instrumentation coverage, not yet runtime-tested
at this checkpoint. Physical manual and automated tests remain NOT TESTED.
