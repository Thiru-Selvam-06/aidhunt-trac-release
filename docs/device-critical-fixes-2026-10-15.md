# Device-reproduced deletion and investment fixes — 2026-10-15

## Evidence and root causes

The user reproduced Owner-created Partner expenses crediting Owner investment and Home deletions returning. The redacted Logcat recorded JOB deletion markers followed by marker removal, then repeated dependency-blocking messages. A private read-only Room snapshot established the actual rejection: operation `28f2228f-8e56-4300-acdc-b5864a0c826c`, Entry `3758232037490356607`, reported `Confirmed state restored; Unsupported Firestore field`. Pending Collection writes were blocked behind that rejected deletion. No authorization mismatch is inferred from this error.

The deployed Firestore REST encoder compared a nested object's prototype to `Object`, rather than `Object.prototype`. Paid Entry deletion writes an array of Collection audit maps into its transaction receipt. The encoder rejected those maps before commit. A new adapter regression reproduced exactly this error before the fix and passed afterwards. Deployed source was compared with the built candidate: the only Worker change is the corrected prototype comparison.

New Entry also used the selected member's name for personal funding but filled the payer UID with the signed-in actor; the repository independently overrode the new payer with that actor. Investment aggregation correctly uses payer UID, so the inconsistent input credited Owner. Both construction and repository resolution now use the authorized selected Entry member. Authenticated creator attribution remains unchanged. Existing recorded payers remain intact on edits; no old financial records were rewritten.

## Changes

- `cloudflare/firestore-rest.mjs`: correct plain-object serialization while retaining strict unsupported-value handling and exact BigInt encoding.
- `cloudflare/firestore-rest.test.mjs`: nested Collection audit map/int64/server timestamp regression.
- `app/src/main/java/com/example/ui/viewmodel/NewEntryDraft.kt`: define selected-member personal funding semantics.
- `app/src/main/java/com/example/ui/screens/entry/NewEntryScreen.kt`: selected member payer UID/name and explicit named payer label.
- `app/src/main/java/com/example/data/repository/WorkspaceRepository.kt`: authoritative selected-member payer resolution; allow earlier pending children to upload before the compound deletion through existing dependency metadata/scheduler. Rejected financial writes remain blocked; no automatic retry or deletion of those payloads was added. A fresh authorized delete continues to supersede its own prior rejected delete through the existing retained audit state.
- `app/src/test/java/com/example/NewEntryExpenseAndTakingCategoriesTest.kt`: payer retention and Firestore round-trip investment regression.
- `app/src/test/java/com/example/EntryDeletionReconciliationTest.kt`: Room regression preserving pending children, original IDs/times and unrelated/rejected commands during a fresh deletion request.

## Validation

- Before fix: `node --test cloudflare/firestore-rest.test.mjs`: one pass, one failure reproducing `Unsupported Firestore field`.
- After fix: `node --test cloudflare/google-auth.test.mjs cloudflare/firestore-rest.test.mjs cloudflare/operations.test.mjs functions/entry-deletion-policy.test.js`: 26 passed, zero failed/skipped.
- `./gradlew.bat :app:testDebugUnitTest :app:assemblePhysicalTest --offline --console=plain --max-workers=1`: BUILD SUCCESSFUL.
- After adding final regression cases: `./gradlew.bat :app:testDebugUnitTest --offline --console=plain --max-workers=1`: BUILD SUCCESSFUL; 316 tests, zero failures/errors/skips.
- `cf deploy --dry-run`: passed.
- `git diff --check`: passed with existing line-ending notices.

## Staging deployment

Only `aidhunt-trac-trusted-staging` was updated. Revision `9b9ab080-a89e-41a4-bb2d-9f0115922e2e`, deployment `74e59fbb-d7ca-4bb0-b64c-4933d95cd13e`, staging traffic 100%. URL: https://aidhunt-trac-trusted-staging.aidhuntinformatics.workers.dev

Readback confirms the encoder fix, project `trac-aidhunt`, workspace `ws_cf_staging_20261015_7361_7f4c92` and inherited secret binding. Secret values were not read. Health HTTP 200 and unauthenticated delete HTTP 401 passed with the explicit verification user agent. Python's default user agent initially received Cloudflare edge HTTP 403/code 1010; that response was not an application authorization result.

## Device installation and remaining gate

Device I2304, serial `10BE3C1T530006H`. Updated isolated package `com.aistudio.aidhunttrac.cedlir.physicaltest` using `adb install -r`: Success. Only that app was force-stopped for a consistent queue snapshot. No data clear, uninstall, account change or production app operation occurred.

Private before/after Room copies contained exactly identical ordered outbox rows: 90 total, 68 ACKNOWLEDGED and 22 INVALIDATED; zero pending/rejected at installation. SHA-256 `20940d31cc6f9d2107154e85e1c1ffdcc45095f8e38308e4464c641d3807045e`. These are installation-time observations; they do not establish why states changed after the earlier reproduction snapshot.

Post-fix physical workflow acceptance remains NOT VERIFIED until the user manually reopens the updated isolated app and repeats:
1. Owner creates a fresh Entry for Partner 2 with a personally funded Expense; confirm Partner investment and unchanged Owner investment in persisted records/reopened report.
2. Owner deletes an unpaid Entry and a paid Entry with solely linked Collections/eligible Expenses from Home; confirm no return after sync/restart and a matching receipt/acknowledgement.
3. Repeat offline deletion followed by reconnect. Shared allocations and independently unauthorized records must retain their protections and explain a genuine rejection.

Do not clear old records or fabricate a passing physical result. Existing financial misattributions are preserved pending evidence-based correction. No production Rules, business workspace data, production routing or billing changes. No commit, push, reset, stash or branch switch. Branch and HEAD remain `codex/v1.6-implementation` / `0bf772b9dd3a7d7ebfaae7ebcd1e96ca6f1fe141`.


## Follow-up: removed Partner access and Owner archive restrictions

Read-only Firestore verification on 2026-10-15 confirms both dedicated Partner memberships in the disposable staging workspace are REMOVED, with updates at 11:53:03Z and 11:53:05Z. Corresponding Deleted Users records identify the staging Owner as the remover. No membership was reactivated or changed during this investigation. The reported error, `Physical test requires active staging membership`, is the explicit test-build membership gate, not evidence of an OTP-provider failure.

The authoritative v1.6 DOCX requires removed members to lose workspace entry, retained source records to be read-only, and permanent purge to use the separate Owner-only Deleted Users flow. It explicitly forbids Owner suspend/reactivate. Arbitrary deletion of archived source records and silent restoration of removed Partner access would conflict with these requirements. A specific additional failed Owner record/error has not yet been supplied; do not infer that every Owner delete failure is an archive case.

A separate client defect was found: profile lookup and the auth listener could recover from a confirmed SecurityException using a previously verified cached profile. The update clears only the verified auth-profile cache upon a confirmed access denial, leaves Room/outbox untouched, and prevents fallback for denied, invalid-user or cancelled verification. Temporary offline failures retain the existing cached-session behavior. Login now distinguishes missing staging membership from removed/inactive access, and the local archived-record deletion error points the Owner to Account > Deleted Users.

Added `VerifiedProfileRecovery.kt` and `VerifiedProfileRecoveryTest.kt`; updated `FirebaseAuthService.kt` and the archived-record error in `WorkspaceRepository.kt`. No backend authorization, Rules, deployment, membership or business data change is part of this follow-up.

Before update: isolated app stopped, device online, outbox 90 rows (68 ACKNOWLEDGED, 22 INVALIDATED), SHA-256 20940d31cc6f9d2107154e85e1c1ffdcc45095f8e38308e4464c641d3807045e. Private evidence directory: C:\Users\user\AppData\Local\Temp\aidhunt-membership-fix-jj4m2ey7. Redacted UID-scoped Logcat: C:\Users\user\AppData\Local\Temp\aidhunt-access-logcat-nor5h2ju\redacted-app.log.

Follow-up validation: Android unit tests 319 passed, zero failures/errors/skips (three new cache-denial regressions). `:app:assemblePhysicalTest` BUILD SUCCESSFUL. `adb install -r` Success on the isolated package; after-install outbox row count, states and ordered-row hash match the before snapshot exactly (90 rows: 68 ACKNOWLEDGED, 22 INVALIDATED). No app launch/navigation was automated. Post-update login message and Owner deletion physical acceptance remain NOT VERIFIED pending manual retest. No new Worker revision or backend changes in this follow-up. `git diff --check` passed with existing line-ending notices.
