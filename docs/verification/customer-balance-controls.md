# Display balance history and unused customer controls

Baseline: TRAC1.0.15, commit `6aba5b95d5e13f23d6406e75afc4cb11ad96f2ae`. Development branch: `codex/customer-balance-controls`.

## Approved behavior

The user explicitly selected a **display-only** amount, with a mandatory reason and history identifying who edited it and when. This is presentation metadata, not a financial adjustment or payment. No received amount, due, expense, withdrawal limit, Balance Sheet or financial calculation is changed.

Active Owners and Partners can edit the shared presentation setting online. Each edit stores the original override, before/after displayed paise, difference, mandatory reason, authenticated editor UID, member-name snapshot, server time, unique operation ID and sequential revision. History cannot be edited or deleted by clients. A transaction couples each current setting to exactly one history document; stale revisions cannot overwrite another person's edit. A repeated operation identity is checked, not applied twice. Restore calculated display also requires a reason and produces a history event.

The manual value applies to Home and Overall Business Overview with an explicit Edited display only label. Historical report scopes keep their calculated values. The editor also shows the real calculated balance. Withdrawals and financial reports continue consuming the existing calculation flow. The calculated amount recorded in history is the device-observed context, not independent evidence that cash physically exists. If the calculation or shared revision changes while the form is open, save is disabled until it is reopened.

The new non-financial collections are `balanceDisplay/current` and append-only `balanceDisplayEdits/{operationId}`, scoped beneath the authorized workspace. Only active financial members can read/write them; the UID/name/time/reason/revision and paired history are validated by narrow additive local policy. The editor shows the latest100 records, while the full history remains stored. Firestore cache supplies offline views; saving an absolute shared display value requires a server-confirmed setting and internet, and is not silently queued offline. No Room migration or outbox rewrite is introduced. Both existing local Rules variants receive the same additive policy; existing financial/deletion Rules are unchanged.

## Customers without records

The Customer Dues screen provides a separate Customers without records picker to active Owners/Partners, without inflating customer-ledger counts or dues. It uses all workspace jobs and collection statuses, not a selected person/date subset, and protects customers referenced by pending/rejected local operations. Both the UI and repository refuse a referenced customer. Malformed retained dependency data fails closed. User-entered names and reasons are not translated.

Owners retain their existing Firebase-only deletion path. Partners use the existing trusted `deleteWorkspaceCustomer` callable after `trustedEmptyCustomerDeletionV1` is explicitly activated. That server transaction rechecks the original Owner or active enabled Partner, workspace membership, exact63-bit customer ID, retained jobs and all receipts including reversed receipts, then writes the original-operation acknowledgement/deletion proof and removes only the unused profile/directory. Retries keep the same IDs/times and return the same acknowledgement. Direct Partner customer deletion in Firestore Rules remains denied.

The Partner gate defaults FALSE and is cached only for the verified actor/workspace. Before activation, the repository returns an explicit activation message BEFORE removing anything locally. This prevents the visible button from silently deleting a local customer and later discovering the server cannot authorize it.

## Verification

| Check | Actual result |
|---|---|
| Firebase Emulator: display authorization/history/concurrency plus trusted customer command and existing Phase53 deletion cases |29 PASS,0 fail/skip|
| Firebase Emulator: additive display policy against general Rules variant |5 PASS,0 fail/skip|
| Final Android host tests, exact current source |37 PASS,0 fail/error/skip;6 classes|
| Isolated API36 Android Emulator, mandatory BalanceDisplayDeviceTest |7 PASS,0 fail/skip;55.04 seconds; .devicetest/demo guards passed|
| Debug and isolated demo builds and lint |assembleDebug/assembleDeviceTest/assembleDeviceTestAndroidTest PASS;lintDebug0 errors,114 warnings|
| Inventory/publication/localization guards |PASS:1101 control identities;122 host classes;25 backend files;7 mandatory Android classes;0 inventory errors;2 localization checks;staged scan0 findings|
| GitHub Actions for this development change |NOT RUN; no commit/push yet|
| Automated physical-phone tests |NOT TESTED|
| Manual physical-phone/multi-partner tests |NOT TESTED|

Initial host failures were retained in logs: Windows native test runtime used a percent-encoded user-home path; a local test init script corrects only the runtime paths. A new duplicate-name locator was corrected to assert both unchanged customer-name nodes. New interactive dialog checks hit host AppNotIdle even with deterministic clock handling, so every journey/assertion is preserved in the mandatory Android instrumentation plan. Host tests retain permission, financial, dependency and selector checks. Every new editor render/interaction assertion is retained in the mandatory Android suite; no new editor-render test is falsely reported as a host pass. The manual clock experiment followed [Android Compose synchronization guidance](https://developer.android.com/develop/ui/compose/testing/synchronization); native Android journeys use its normal synchronization. No existing business assertion or release gate was removed or weakened. Android emulator results are not physical-phone results.

## Production activation boundary

Read-only configuration check on21Nov2026 at18:35:59 IST: project billing disabled; deployed Cloud Functions list empty, with no further pages. No billing, API enablement, deployment or capability change was performed.

Display synchronization can use the existing Spark project after separately approved deployment of ONLY the additive presentation Rules based on the current active reviewed policy. Before deployment, export/read back the actual active policy, compare it with the preserved Phase53 baseline, apply the additive blocks only, rerun policy tests, and retain rollback source. Do not replace working financial Rules with a different historical variant.

Source routing was also verified: the normal product command route is Cloud Functions. The existing Cloudflare Worker is staging-only and explicitly denies production workspaces; it must not be repointed or have its allowlist bypassed. A Spark-compatible production Worker alternative would need a separate approved IAM/credential/routing review and is not introduced here. No new paid backend is assumed or enabled.

Partner empty-customer deletion needs a trusted absence-of-records check: Firestore Rules cannot perform arbitrary queries proving there are no jobs/receipts. Widening deletion proofs to Partners alone would lose that safeguard. The prepared callable therefore remains disabled until an approved backend plan is activated. Firebase currently requires Blaze for production Functions deployment ([official Firebase setup guide](https://firebase.google.com/docs/functions/get-started)). The empty Functions backend and disabled billing are therefore a blocker; do not turn on paid billing or deploy all exported functions automatically. Any future Cloud Functions deployment must target **only `functions:deleteWorkspaceCustomer`**, following separate approval, cost/security review and synthetic-workspace verification. Enable `trustedEmptyCustomerDeletionV1` only after the exact reviewed endpoint works. Do not deploy notification senders, deferred features or unrelated functions.

Rollback: disable the Partner capability first; restore the exported pre-change Rules if needed; preserve all display/history documents and customer deletion proofs. Keep the known TRAC1.0.15 APK. No financial data reset or restoration from a stale snapshot is part of rollback. Test multiple devices after activation before preparing a customer release.

## Manual acceptance after approved activation

1. In an authorized synthetic workspace, Owner sets a display amount with paise and a reason. Verify current/history records, actual UID/name, server IST time, old/new/difference and one operation ID. Another Partner sees the same display.
2. Partner edits it. An Owner device holding the prior revision must refresh instead of overwriting it. Confirm old history is immutable.
3. Open offline: cached display/history remain viewable and saving is disabled. Reopen online and verify server acknowledgement.
4. Change English/Tamil. Application labels localize; original editor names/customer names/reasons stay unchanged.
5. Restore calculated display with a reason; verify another immutable event. Change a financial record only in the emulator/synthetic test workspace and confirm an already-open display editor requires refresh.
6. Confirm reports, historical scopes, dues, receipts, withdrawals and withdrawable-funds limits are numerically unchanged by a display edit.
7. Owner deletes a synthetic unused customer; Partner does the same after trusted backend activation. Verify exact proof/acknowledgement, no remaining directory choice and no other record removed.
8. Attempt deletion with a retained job, reversed receipt or rejected local operation: the customer and original operations must remain. Operator, removed member and foreign workspace attempts remain denied.
9. Restart both devices and verify shared confirmed state/history. Never use this checklist as permission to modify real customer records or recover old rejected operations.

## Financial investigation remains separate

The masked Phase6 evidence report and protected full identity register were produced from a read-only21Nov2026 Firebase snapshot. The disputed legacy portions remain unchanged, with their unknown dates and payment methods still unresolved. No balancing transaction, date/method correction or live financial recovery was performed. The original product APK and ongoing physical-phone testing remain untouched.

## Remaining verification limits

These are targeted development checks, not a complete release regression. Live Kotlin-client metadata persistence/propagation across two real accounts/devices and secure Partner-customer endpoint activation are NOT TESTED in production; both await the separate approved staging/live-test plan. A server cannot see another device’s still-offline unpublished operations; customer cleanup must account for that device’s retained queue before live testing. No such original operation was retried, cleared or rebound.

The native Android results above are emulator results, not automated or manual physical-phone verification. Raw logs and37 host JUnit results are preserved locally in outputs/customer-balance-evidence and adjacent customer-balance logs. The original product APK hash remains unchanged. No APK was distributed.

## Tools and changed files

Reused Kotlin/JUnit/Robolectric, the existing Firebase Emulator and policy/server test libraries, the AndroidSDK/ADB and a new isolated API36 AVD. No new library, monitoring service, Firebase project or production configuration was introduced. New backend source remains undeployed. Full production financial data and its identity register stay outside Git.

| Repository file | Why changed |
|---|---|
| `PROGRESS.md` | Persist implementation, executed checks, activation blockers and exact next action. |
| `app/src/main/java/com/example/MainActivity.kt` | Wire authorized display state/editor into the existing app without touching financial consumers. |
| `app/src/main/java/com/example/data/auth/AuthorizationManager.kt` | Allow an explicitly verified financial role to request deletion of an unused customer. |
| `app/src/main/java/com/example/data/firebase/FirestoreRepository.kt` | Route enabled Partner profile deletions through the trusted server command; preserve Owner route. |
| `app/src/main/java/com/example/data/firebase/WorkspaceSyncCapabilities.kt` | Default-off, strictly typed, actor/workspace-isolated trusted deletion capability. |
| `app/src/main/java/com/example/data/repository/WorkspaceRepository.kt` | Protect retained source/history/queue references and reject inactive rollout before local removal. |
| `app/src/main/java/com/example/ui/screens/home/HomeScreen.kt` | Display labeled override/edit control while retaining the calculated funds input. |
| `app/src/main/java/com/example/ui/screens/report/BusinessOverviewScreen.kt` | Apply the presentation override only in Overall; preserve historical report scopes. |
| `app/src/main/java/com/example/ui/screens/report/CustomerCreditDueTab.kt` | Expose unused customers to financial members, independent of person/date filters and ledger counts. |
| `app/src/main/java/com/example/ui/screens/report/ReportScreen.kt` | Pass display controls, full receipts and retained operations to the existing report sections. |
| `app/src/main/java/com/example/ui/viewmodel/MainViewModel.kt` | Bind display listeners/saves to the verified actor/workspace and pass the real financial role. |
| `app/src/test/java/com/example/CustomerDeletionDialogTest.kt` | Retain Owner/unverified checks; add active Partner empty/reference cases. |
| `app/src/test/java/com/example/WorkspaceSyncCapabilitiesTest.kt` | Strict activation typing, persistence and verified-actor isolation. |
| `docs/regression/asset-inventory.json` | Refresh source/test inventory; no assertion or runtime result is removed. |
| `docs/regression/interaction-inventory.csv` | Refresh control identities and source locations; source inventory is not a pass claim. |
| `docs/regression/interaction-inventory.json` | Refresh control identities and source locations; source inventory is not a pass claim. |
| `firestore.phase53.rules` | Same additive display policy on the working Phase53 candidate; existing financial/Partner job policy preserved. |
| `firestore.rules` | Add only presentation-document/current-history validation; original policy byte-equivalent after removing additions. |
| `functions/index.js` | Recheck active original Owner or enabled Partner and zero references within the trusted customer-deletion transaction. |
| `functions/security.integration.test.js` | Real emulator tests for enabled Partner, exact IDs, replay, retained jobs/reversed receipts and invalid actors. |
| `scripts/instrumentation-plan.json` | Keep every new dialog assertion mandatory in native Android coverage. |
| `scripts/run_backend_suite.mjs` | Keep new policy tests mandatory in the existing Rules partition. |
| `app/src/androidTest/java/com/example/BalanceDisplayDeviceTest.kt` | Seven mandatory synthetic native-Android cases; actual API36 emulator PASS. |
| `app/src/main/java/com/example/data/firebase/BalanceDisplayRepository.kt` | Shared presentation state and atomic, idempotent, authenticated edit history; no financial writes. |
| `app/src/main/java/com/example/ui/components/BalanceDisplayEditor.kt` | English/Tamil reason/amount/history UI, signed editor/time, restore and stale-form protection. |
| `app/src/main/java/com/example/ui/components/UnusedCustomersDialog.kt` | Separate unreferenced-profile selector and explicit delete confirmation, preserving raw user text. |
| `app/src/test/java/com/example/BalanceDisplayPolicyTest.kt` | Exact paise, required reason, and verified financial-role acceptance/denial. |
| `app/src/test/java/com/example/UnusedCustomerPolicyTest.kt` | Preserve rejected queue/reversed history references and prevent cross-workspace mixing. |
| `docs/verification/customer-balance-controls.md` | Evidence report, limitations, production gates, rollback and per-file explanation. |
| `security-tests/balance-display.test.cjs` | Test presentation permissions, required paired immutable history, exact amounts and stale/concurrent edits. |
