# Approved disposable staging provisioning — 2026-10-15

Workspace: `ws_cf_staging_20261015_7361_7f4c92`.
Project: `trac-aidhunt`. Purpose: isolated release verification, not business data.

## Resources created

- One new workspace document, explicitly marked `isTestWorkspace=true`.
- Three new workspace-scoped ACTIVE memberships: staging Owner and staging Partners 1/2.
- Three new discovery-index documents under the approved users, keyed only by the new workspace ID.
- No existing user profile/default, phone directory, membership, workspace or business record was changed.
- Existing business memberships were read before and after provisioning and compared equal, including their update timestamps.
- All three new memberships were read back with the intended workspace, status and exact OWNER/PARTNER/PARTNER roles. This is an administrative configuration verification, not yet an authenticated client-login test.

## Staging deployment

Worker: `aidhunt-trac-trusted-staging`.
URL: https://aidhunt-trac-trusted-staging.aidhuntinformatics.workers.dev
Revision: `8fad1c33-6779-4482-ac15-ded5bc67e75f`.
Deployment: `92dc5a55-330f-4edb-8dc9-51d9e74afccf`.
Staging traffic: 100%. Production routing unchanged.
Read-back confirms Firebase project and new workspace binding. The existing encrypted service-account secret was inherited without reading its value. The script ETag is unchanged from the prior staging deployment: only the configured workspace changed.
Health: HTTP 200, correct project, credential presence true. Unauthenticated deletion: HTTP 401.

## Local changes

- `cloudflare/cloudflare.config.ts`: bind staging Worker to the new workspace.
- `app/src/main/java/com/example/data/firebase/FirebaseEnvironment.kt`: isolated app staging workspace constant.
- `app/src/test/java/com/example/TrustedCommandRoutingTest.kt`: new workspace routing and regression rejecting former staging workspace for physicalTest commands.
- This report. Every pre-existing source change preserved.

## Validation

- `cf deploy --dry-run`: PASS.
- `node --test google-auth.test.mjs firestore-rest.test.mjs operations.test.mjs` in cloudflare: 20 PASS, 0 failed/skipped.
- Previous verified local checks: 166 Rules/listener cases, 6 server integration cases and 35 combined Worker/policy cases; see release-verification-2026-10-15.md. These were not rerun solely for a workspace binding change.
- Android build/install and post-update queue check: completed; see final validation below.

## Remaining acceptance

Authenticated phone-OTP login as each of the three approved accounts and role-specific client/server access are NOT VERIFIED yet. Administrative membership reads do not prove that login or client selection works.

Physical tests remain NOT VERIFIED: unpaid Entry plus linked Expense deletion; solely paid Entry plus linked Collection/Expense deletion; shared-allocation protection; Owner actual payer versus Partner beneficiary; cash versus UPI; business-paid Expense; Partner withdrawal once; negative cash; interruption/restart and matching outbox acknowledgement.

Only the isolated physicalTest package may be used. Preserve existing Room rows, identities and timestamps. Never replay older rejected/invalidated operations merely to test the new workspace. Use fresh disposable records inside this workspace. No production Rules, production Worker, billing, commits or pushes were changed.


## Final validation and installation

- `./gradlew.bat :app:assemblePhysicalTest --offline --console=plain`: physicalTest APK assembly completed in the combined build before its unit-test portion was interrupted. Artifact: `app/build/outputs/apk/physicalTest/app-physicalTest.apk`, 32,060,763 bytes, built 2026-10-15 16:13:13 IST.
- Rerun: `./gradlew.bat :app:testDebugUnitTest --offline --console=plain --max-workers=1`: BUILD SUCCESSFUL, 314 tests across 42 suites, zero failures/errors/skips. Routing suite contains all four current tests, including rejection of the previous staging workspace.
- APK signing certificate matched the installed isolated app. `adb -s 10BE3C1T530006H install -r app/build/outputs/apk/physicalTest/app-physicalTest.apk`: Success. No uninstall or data clear.
- Isolated app: `com.aistudio.aidhunttrac.cedlir.physicaltest`, versionName `1.0.4-physical-test`, versionCode 5. Device: I2304, serial `10BE3C1T530006H` at installation.
- Private before/after Room snapshots show identical complete ordered outbox rows: 76 total, 63 ACKNOWLEDGED and 13 INVALIDATED; zero pending/rejected. Row SHA-256: `3b2eab16a547db63a4776a79d5dd41f8eae9d34d8aeaad531a56b9b451eb3426`. Existing operation IDs, timestamps and payloads were not changed. These counts describe this isolated installation only.
- Final `adb devices -l`: empty device list. Fresh Logcat and authenticated manual workflow results could not be obtained. All physical acceptance cases listed above remain NOT VERIFIED.
- Final Git status: 42 modified tracked files, one pre-existing tracked deletion, 134 untracked files. Branch and HEAD unchanged. `git diff --check`: passed (line-ending notices only). No commit, push, reset, stash or branch switch.

## Manual acceptance gate

Open the isolated app and select the new disposable workspace. Verify all three approved accounts via phone OTP and confirm Owner/Partner/Partner access. With fresh staging records, test Entry deletion and linked records, shared Collection protection, actual payer versus beneficiary attribution, cash collection and UPI separation, business expenses, Partner withdrawals, and restart/retry reconciliation. Confirm both persisted backend state and the matching local acknowledgement. Do not replay older invalidated operations or use business workspaces for these mutations.

No authenticated live mutation or manual financial acceptance pass is claimed by this report. The original production-configured app and its queue were not modified. Existing business records/memberships, production Rules/Worker routing and billing remain unchanged.
