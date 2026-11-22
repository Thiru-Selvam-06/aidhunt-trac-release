# Cash-permission production rollout — 22 November 2026

The user approved the Firebase update and requested a complete testable APK. The user then confirmed the other partner phones are NOT updated, and requested deployment only if regular functions are unaffected. That authorizes the narrow permissions rollout, not unsafe mixed-version cash activation, historical financial corrections or paid billing.

## Actual result

- Project: `trac-aidhunt`. Active Rules: `dd21da1c-69cd-4978-9279-39f3760c3c0d`.
- Server release update: `2026-11-22T17:30:51.705747+05:30` IST. An independent later read-back matches the exact reviewed compatibility source, canonical SHA-256 `90eb895bc114a1e22ab58248c339e49e422c598c94b0e9c60d0e5b6fb54931c2`.
- Previous Rules: `c84da8eb-0fda-4318-a7ef-d16d8b9bd7d1`, SHA-256 `4b0880cb108fe97440cee9a008826e5498807b77243fbc292c47313cd06a5874`. Protected rollback source and release metadata are retained locally.
- Removing only the new cash helpers/matches reproduces the active previous policy byte-for-byte. No existing job, collection, expense, withdrawal, membership or deletion authorization clause changed. The obsolete display-only API was not enabled. The broader development Rules were not deployed.
- Firebase server validation: zero errors, four warnings. Two new compatibility-helper parameters are intentionally unused; two warnings are in the preserved baseline. All warnings remain recorded. The exact source had already passed22 compatibility emulator checks, including the15 existing Partner job-deletion cases; broader development/atomic checks also passed. A repeated full Android build is unnecessary for this server-only activation.
- Two rollout attempts reported transport errors. The created ruleset was reused after checking its exact source. Independent release/source read-back proves activation succeeded; a failed connection response was not treated as proof of failed deployment or grounds for rolling back a successful change. No financial operation was retried.

## Workspace state and compatibility

Read-only metadata inspection confirms original Owner recorded, cashBalanceAdjustmentsV1 FALSE, atomicCashControlV1 FALSE and trustedEmptyCustomerDeletionV1 FALSE. No cash-adjustment state or atomic baseline exists. No capability, financial row, customer, pending operation, identity, amount, historical date, payment method or default-workspace record was changed by this rollout. Normal application functions retain the prior policy; real-world phone operation is still NOT TESTED by the agent.

The existing1.0.17/code18 signed APK remains valid and byte-identical, SHA-256 `87cbf661bdfc7c3291944c077af2c2bdfd3f1eab290e0b81c9a2f4d0b2da0985`. It contains the actual cash editor and PMS launcher. No replacement APK is required for a server-side permission update. Its original-key/configuration verification,34 release tests,42 Rules checks and7 Android emulator tests remain the actual targeted evidence. Physical-phone testing and the exact-branch full GitHub release gate are not yet passed.

Cash saving in the real shared workspace remains OFF because older installed APKs ignore adjustments. The user may install/test the same candidate first; do not call cash saving or customer release fully ready while these constraints remain. An isolated empty test workspace can allow the same APK to test cash saving without changing live balances, but the specific account/setup requires the user's decision. No test workspace or membership has been created automatically.

## Separate Partner customer-profile deletion blocker

Fresh authenticated configuration reads show billing disabled and zero deployed Functions. The normal product routes Partner deletion of an unused customer profile to `deleteWorkspaceCustomer`. Its trusted query/transaction is required to prove no retained jobs or collections reference that customer. Direct Partner deletion is intentionally not opened by Rules.

The source and emulator tests exist, but the production endpoint is absent. Deploying Firebase Functions currently requires Blaze (see Firebase's official functions setup documentation). No billing was enabled, no endpoint was deployed and the existing staging-only Cloudflare Worker was not repointed to production. A Spark-compatible alternative requires a separate credential/IAM/routing review. This blocker concerns Partner empty-profile cleanup; existing eligible Partner job deletion remains enabled and unchanged.

## Remaining gates and rollback

1. Decide whether to use an isolated test workspace now; otherwise update every partner phone to1.0.17 before separately approved real-workspace cash activation. Do not fabricate balances or enable an uninitialized atomic-cash protocol.
2. Complete development-branch publication and the existing CI/release gate; investigate actual failures. Do not merge to production automatically.
3. Run the separate physical acceptance checklist with the exact APK. No agent phone testing has been claimed.
4. Decide the secure backend plan before enabling Partner empty-customer cleanup. Do not deploy all Functions or change billing under a generic permissions rollout.
5. Before any correction, the feature can remain OFF; the saved previous Rules can be restored if separately needed. After corrections exist, preserve cash-aware reads/history and use a forward repair or a new separately authorized inverse correction; never delete audits or downgrade to clients that ignore them.

Private evidence stays outside Git in `work/protected-release/cash-rules-20261122/`. Read-back, source backup and validation records are retained. No customer financial data was modified or reclassified by this work.

## User testing choice

The user declined a separate test workspace and proposed remembering/restoring the original amount. No workspace, account, membership or index was created. A remembered absolute target can cancel a legitimate transaction made meanwhile; it is not the prescribed reversal procedure. A controlled real-workspace test requires all partners to pause mutations, the exact small test delta to be reversed with a second reasoned audit, confirmed cumulative adjustment zero, and the feature disabled again until all clients are updated. Confirmation of the pause is pending; no feature flag or financial data has been changed. No agent-created balancing transaction is authorized.
