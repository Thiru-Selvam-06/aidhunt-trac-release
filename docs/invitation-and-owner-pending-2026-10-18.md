# Invitation failure and Owner pending count — 2026-10-18

## Finding
Owning a personal workspace does not prohibit joining another workspace as Partner. The Owner's three-pending badge is not an upload counter: pending deletion commands also count, and each account sees only its own workspace-scoped outbox.

PMS invitation and cloud-deletion acceptance remain **BLOCKED** by unavailable production trusted endpoints. Do not treat these local test results as proof of a deployed service. Release gate remains **NOT READY**.

## Baseline and preservation
Root: `C:\Users\user\.codex\worktrees\v16-implementation\aidhunt-trac`.
Branch: `codex/comprehensive-regression-2026-10-17`.
HEAD remains `937088dff3115d4e995dbf6d1dfbed1158ccbe8d`.
All previous changes retained. Four edited files have before-images, a pass-only patch and hashes under `docs/test-artifacts/invitation-and-owner-pending-20261018/`. No staging, commit, push, deployment, membership change or production business mutation.

## Actual phone evidence
User confirmed the invitation error appears immediately on Send invitation OTP, before OTP dispatch. Code creates the backend invitation before starting secondary phone authentication; the invitee's separate workspace ownership is not examined in that initial request.

User explicitly opened SQLite & Cloud Sync and requested inspection. A read-only screenshot was captured and decoded successfully at 1080x2400. No taps, scrolls, retries, sign-in changes or private database reads occurred. UIAutomator's first attempt did not return a usable XML hierarchy; no claim is based on that attempt.

Visible screenshot:
- Badge: **3 Pending**, PMS workspace.
- First visible JOB DELETE: waiting to sync, 20 attempts, explicitly reports "Cloud deletion service is not available. Your deletion remains pending on this device; contact support to restore the backend."
- Second visible JOB DELETE: waiting to sync, 13 attempts.
- Third pending item is below the viewport and **NOT VERIFIED**. Its type/error must not be inferred.
- Full operational identity details are retained in `physical-room-observation.json` where transcribed; the private screenshot is ignored by Git.

This confirms real retained deletion commands, not phantom uploads. The same-session preceding evidence recorded no deployed Firebase Functions, while standard command routing uses cloudfunctions.net and the staging Worker forbids production workspaces. No backend or routing change was made here.

## Correct role and queue semantics
`cloudflare/operations.mjs` validates the inviter as the active Owner of the target workspace. Invitation redemption grants Partner/Operator only in that workspace; it does not rewrite the invitee's personal Owner membership, user default workspace or existing phone-directory identity. Target membership conflicts and consumed proof checks remain enforced.

`PendingOperationDao.observeActor(workspaceId, uid)` and MainViewModel pending counts deliberately separate actor and workspace. An Owner may have pending deletions while a Partner has no pending actions; "Synced" on the Partner view does not acknowledge the Owner's commands.

No counter was forcibly reset and no queued operation was acknowledged, erased, redirected or retried.

## Changes and exact regressions
1. `WorkspaceInvitationService.kt`: a missing HTTP endpoint (404 without a structured backend error) now explains that the invitation service is unavailable and no member was added, instead of a generic invitation failure. Structured NOT_FOUND still means invitation recovery; permissions, auth and validation remain distinct.
2. `WorkspaceInvitationErrorClassificationTest.kt`: two new tests for missing endpoint and structured missing invitation. Raw HTML/server details never become UI text.
3. `RoomScopeTest.kt`: a new real isolated Room test seeds three Owner deletion commands, an acknowledged Partner command and a foreign workspace command. Asserts Owner has exactly three PENDING DELETEs and zero UPSERT uploads, Partner has zero pending rows, proper existing status labels, scoped next batches and every original command remains byte-for-byte equivalent at the entity level.
4. `cloudflare/operations.test.mjs`: strengthens existing multi-workspace invitation test with an actual synthetic personal workspace, OWNER membership and user default. Asserts these records and legacy directory remain unchanged when joining another workspace as PARTNER; proves that personal Owner cannot then invite people into the second workspace without authority.

## Fresh execution
Backend:
`node --test cloudflare/operations.test.mjs functions/invitation-policy.test.js`
Exit 0: **48 tests passed**, zero failed/cancelled/skipped. Controlled MemoryStore/policy tests only, no live invitation or real SMS.

Android (JAVA_HOME JDK21.0.10):
```text
.\gradlew.bat :app:testDebugUnitTest
 --tests com.example.WorkspaceInvitationErrorClassificationTest
 --tests com.example.WorkspaceInvitationPolicyTest
 --tests com.example.PendingInvitationCleanupContractTest
 --tests com.example.RoomScopeTest
 --tests com.example.TrustedCommandRoutingTest
 :app:assembleDebug --init-script scripts/ci-tests.gradle
 --offline --console=plain --max-workers=1 --no-daemon
 -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=512m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -Dfile.encoding=UTF-8"
```
One actual invocation; exit 0, BUILD SUCCESSFUL in 4m4s.
**36 tests passed** across five suites (10 + 5 + 3 + 11 + 7); zero failures/errors/skips.
Actual XML preserved under `junit/`, summary in `verified-results.json`, raw redacted test output in Android/backend log files.
`git diff --check`: exit 0.

New standard APK built at `app/build/outputs/apk/debug/app-debug.apk`; **not installed during this pass**, so the user's current diagnostics screen was not interrupted.
SHA256: `18c96ac2ba929699c4d7154bfebe15a830e271c1e1c86495db0daddf32b3da1b`.

## Remaining blocker
A production trusted backend must be deployed and standard routing explicitly aligned through a separately authorized change. The staging Worker must retain its isolation. Until then, changing the invitee's role or clearing the Owner's pending count would neither repair the missing service nor safely persist the intended actions.

Live invitation completion, OTP delivery, actual cloud acknowledgements, the unseen third pending command and independent Partner device state remain unverified. No production financial/membership writes, Rules deployment, Worker deployment, billing change or automatic rejected-command retry occurred.
