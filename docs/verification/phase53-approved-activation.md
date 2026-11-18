# Phase 5.3 approved Partner deletion activation

On 19 November 2026 the user approved the reviewed Rules deployment and requested a signed APK to test Partner deletion of Old Entries and the original Owner's in-app notifications. This is a controlled product update for their testing, not a production merge or automatic customer release.

Baseline: `42f1ddf620e73d8517d4ebe51e9c2841bac88b2e`. Dedicated branch: `codex/phase53-partner-deletion-activation`. Product: `1.0.15`, Android version code `16`, unchanged package `com.aistudio.aidhunttrac.cedlir`.

## Minimal application change

Only the release build overrides `SHARED_PARTNER_JOB_DELETION` to true. Debug, Device Test, Live Test and Physical Test retain their isolation gate. Native FCM remains disabled. The notification repair, financial calculations, Room schema/outbox, authentication/workspace discovery and runtime deletion protocol are unchanged. Version and build gate are the only application changes.

`Phase53ActivationTest` runs against actual generated flags: it checks the release defaults permit eligible cross-creator/year-old New/Old jobs and the isolated debug default remains disabled, while Operators, removed memberships and foreign workspaces remain denied. Existing comprehensive debug regression tests stay mandatory and unchanged.

## Approved Rules

Fresh preflight found active Rules `e7981704-cf72-453b-a867-ad3faa10c75f` unchanged from the reviewed baseline. Canonical baseline SHA-256: `cfe5ca5bc1d8cc8d25985826cc09998ef3090f7ad0f07c4a9c6a79872b40fe30`. Server validation of the reviewed candidate returned zero errors and two warnings.

Only `firestore.phase53.rules` is eligible for this rollout. Canonical candidate SHA-256: `4b0880cb108fe97440cee9a008826e5498807b77243fbc292c47313cd06a5874`. Its diff against the live baseline changes only Partner job-lock/proof creator, age and historical-entry restrictions. Standard development `firestore.rules`, indexes, billing, authentication configuration, Functions and Workers are not deployment targets.

Active actor/workspace verification, eligible source provenance, beneficiary membership, financial children, immutable proofs, original operation identity and version-checked guarded deletion remain mandatory. A job containing another person's receipt/expense, inconsistent legacy balances, removed linked identities or unresolved rejected operations can still require safe resolution. Activation does not authorize financial erasure or automatic recovery of old rejected actions.

Before activation, save a fresh live source/release backup, verify no intervening changes, run the exact candidate Emulator cases and compile validation, then update only `cloud.firestore`. Read back the active release and compare its full canonical source hash. Keep the prior Ruleset available for rollback. No live financial test transaction or deletion is authorized during preparation.

## Verification and release boundary

Targeted release-variant tests, signed build/lint, exact-source GitHub CI, active Rules verification and final packaging results must be completed and recorded in the delivery report before APK handoff. The previous Phase 5.3 exact-source gate passed all 11 applicable jobs, including 846 JVM tests, 409 Rules, 146 backend and 38 Android Emulator checks. That baseline result is not a claim that this activation build has already passed its new verification.

Rollback uses the saved prior Rules release only after inspecting any pending guarded operations; restore the app gate with a higher-version update if needed. Never uninstall, clear Room/pending actions, force a downgrade or mutate financial data as rollback. A Rules deployment alone does not change an older APK's disabled gate.

Physical automated and manual two-phone acceptance remain NOT TESTED until performed. Install this signed normal-package update over the compatible original app without uninstalling. Confirm the Owner bell gets a successful Last Updated and a subsequent authorized Partner activity; then test an eligible Partner Old Entry deletion only when intentionally authorized by the user, preserving the operation/proof/balance evidence. No USB is needed for manual testing. Use the full checklist in the Phase 5.3 report, and retain limitations for overwritten or absent historical notification evidence. Native background push is not enabled by this update.
