# Isolated Partner login recovery — 2026-10-15

## Proven cause and architecture constraint

Both dedicated Partner memberships in `ws_cf_staging_20261015_7361_7f4c92` were REMOVED during manual member-removal testing. Their Deleted Users tombstones identify the staging Owner as remover. The login error therefore correctly reflects current workspace authorization. The invitation handler rejects an existing membership or Deleted Users tombstone with ALREADY_EXISTS; v1.6 explicitly forbids membership reactivation. The previous auth-cache-denial fix remains in place; no authentication or membership check was weakened.

## Recovery implemented

Created a NEW disposable workspace, `ws_cf_staging_20261015_login_a62e71`, in project `trac-aidhunt`, marked `isTestWorkspace=true`. Provisioned the approved existing Owner and two Partner accounts with exact OWNER/PARTNER/PARTNER roles and ACTIVE status, plus their new workspace discovery indexes. Used existing profile display names without changing global profiles. Created seven new documents only. No phone directory, existing workspace, prior membership, Deleted Users record, business record or pending command was changed. Readback verified all three memberships and discovery indexes. Prior staging membership update timestamps remained unchanged.

The Partner ending 5171 is already an ACTIVE member of this NEW workspace; the Owner must not add them again there. In the previous workspace their removal is retained and adding them again remains correctly blocked. Old business and test records were not moved into the new workspace; it begins empty. This is test-fixture recovery, not a change to business membership lifecycle semantics.

## Staging configuration

Updated only `cloudflare/cloudflare.config.ts`, `FirebaseEnvironment.STAGING_WORKSPACE`, and routing regression coverage to select the new disposable workspace. Added a regression rejecting physicalTest command routing to the previous removed-membership workspace. Existing operation IDs, action timestamps and outbox rows are not rewritten or replayed across workspaces.

Staging Worker: https://aidhunt-trac-trusted-staging.aidhuntinformatics.workers.dev
Revision: `919ce27d-6562-449d-9392-a6d1eea5f0b0`.
Deployment: `03529acc-5789-4fbd-b686-b52dd5edc480`, 100% staging only.
Worker source readback exactly matches the source before this configuration-only update. Firebase project is still `trac-aidhunt`; workspace binding is the new disposable ID; existing encrypted credential binding inherited without reading its value. Health HTTP 200 and unauthenticated deletion HTTP 401 passed. No production routing, Rules, billing or secret change.

## Validation and remaining gate

`node --test cloudflare/google-auth.test.mjs cloudflare/firestore-rest.test.mjs cloudflare/operations.test.mjs functions/entry-deletion-policy.test.js`: 26 passed, zero failures/skips.
Android targeted routing/cache-denial tests and isolated APK build are running; final result recorded below when complete. Existing earlier full Android result was 319 passes; it is not counted as a new full regression run.

Phone OTP login and role-specific UI on this new workspace remain NOT VERIFIED until the matching isolated APK is installed and the user manually retests. ADB detected no device at the installation availability check. No data was cleared, app uninstalled, account switched automatically, old rejected operation retried, commit, push or deployment to production performed.

Final local validation: targeted Android suites (TrustedCommandRoutingTest and VerifiedProfileRecoveryTest) passed 8 tests, zero failures/errors/skips. `:app:assemblePhysicalTest` BUILD SUCCESSFUL. APK: `app/build/outputs/apk/physicalTest/app-physicalTest.apk`. `git diff --check` passed with existing line-ending notices. Installation and actual phone login are still pending device reconnection.

Device reconnected: I2304, serial 10BE3C1T530006H. Matching physicalTest APK installed with `adb install -r`: Success. Version 1.0.4-physical-test (5), package com.aistudio.aidhunttrac.cedlir.physicaltest. Only the isolated app was force-stopped for the queue snapshot. Before/after ordered outbox rows match exactly: 90 total, 68 ACKNOWLEDGED, 22 INVALIDATED, SHA-256 20940d31cc6f9d2107154e85e1c1ffdcc45095f8e38308e4464c641d3807045e. Private snapshots in C:\Users\user\AppData\Local\Temp\aidhunt-login-install-xioqtmc5. No data clear, uninstall, automatic UI navigation or production app change. Actual OTP login and member-list acceptance remain pending user manual retest.
