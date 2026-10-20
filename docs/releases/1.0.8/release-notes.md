# AIDHUNT Trac 1.0.8 — signed update and deployed Rules

The signed APK is **AIDHUNT-Trac-1.0.8-Signed-Update.apk**, version 1.0.8, Android version code 9, built from tested source commit `3712901fba157138447cf7e8b8878518feb02211`. Its package and signing certificate match the existing 1.0.7 release. Install it as an **update**; uninstalling is unnecessary and would remove phone-local data. The distributed APK was built from that commit with version 1.0.8/code 9 applied at packaging time. This branch now declares the same version directly in app/build.gradle.kts; no application behavior changed during this publication follow-up.

## Deployment completed

With the user's explicit approval, only Firestore Security Rules were deployed to `trac-aidhunt`. Firebase's server-side dry run and compilation passed. The deployed source was read back and matched the tested candidate byte-for-byte.

- Previous Rules SHA-256: `c92b59e5139b8527b43af00c399490c8ea4fab31a4269a5226b0d7bf53df3caf`.
- Deployed Rules SHA-256: `04507f3f22acc160cef4025e72f924ae9478cdf4d5f1d770d95361c4b5974890`.
- The prior Rules source and release metadata are retained for rollback.
- Billing, indexes, Cloud Functions and Cloudflare configuration were not changed.
- No customer records were created, deleted or overwritten; no rejected live operation was retried or recovered.

## Fixes included

Legacy active memberships without redundant workspaceId metadata can collect payments under the existing workspace/role/collector controls. Legitimate Owner jobs use fewer repeated reference lookups. Original pending and rejected commands remain readable, the existing Room outbox receives durable background scheduling, conflicting job/expense edits fail explicitly, incoming financial snapshots apply in order, and exact server deletion proofs protect against stale customer resurrection. Rejected linked financial actions still protect a job from unsafe deletion.

## Verification

The complete [Android CI run](https://github.com/Thiru-Selvam-06/aidhunt-trac-release/actions/runs/39432672198) passed all 10 jobs, including 17 Android emulator tests. Executed local checks passed: 619 Android JVM/Room/Compose tests, 262 Rules tests, 129 backend tests and 108 Python harness tests.

This production release build and full release lint passed: **0 errors, 101 warnings**. APK signature v2, original certificate, version increase, package, production Firebase resources, disabled debug/test flags, current sync classes, ZIP integrity and 16 KB alignment were verified.

- APK size: 23,369,549 bytes.
- APK SHA-256: `b9f0be3548134103880663826cda1e79d47065046f0529107de9ca6abb8b0436`.
- All emulator/live-test/physical-test build flags are false.
- The app connects to the real Firebase project and does not require a local emulator.

## What remains manual

The user requested a downloadable file instead of USB verification, so this task did not install it, sign in on a phone or verify two physical devices. Automated emulator evidence is separate from those acceptance checks. The exact predicate rejecting the original inaccessible JOB remains unproven without its original payload/auth context. This release is not a claim that every original live transaction has been recovered.

Existing Needs Review actions remain retained. Corrected Rules do not automatically reopen rejected financial commands. Inspect their original amount, identity, dependencies and existing server records before explicitly authorizing recovery; never guess a payment amount from a displayed zero.

The existing Cloudflare staging preview build remains a separate configuration failure and was not changed. The draft PR remains unmerged; no production Git branch was merged in this task.

## GitHub download and evidence

The signed APK is published as a prerelease asset at [v1.0.8](https://github.com/Thiru-Selvam-06/aidhunt-trac-release/releases/tag/v1.0.8). It remains subject to physical-phone acceptance. [APK verification](apk-verification.json), [Rules deployment](rules-deployment.json), [security assessment](rules-assessment.json), and [checksums](SHA256SUMS) are retained beside these notes. Signing keys, authentication material and customer-data exports are excluded.
