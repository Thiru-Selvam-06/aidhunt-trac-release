# TRAC 1.0.18 developer configuration handoff

The selected application source is `924a6d00f2ea90ee533fa7eaf6808e670073fda6`, restored on main by `767ec8aba5876ee187d13252c932c0733866807a`. This handoff adds configuration references only. It does not change app code, version, calculations, synchronization, Rules or customer data.

## What is included

| File | Purpose |
|---|---|
| [config/.env.demo.example](config/.env.demo.example) | Exact existing local emulator ports and synthetic project. |
| [config/google-services.release.json.example](config/google-services.release.json.example) | Actual release project's public Android identifiers, with API-key values replaced by explicit placeholders. Not usable until completed from Firebase. |
| [config/keystore.properties.example](config/keystore.properties.example) | The four property names consumed by the current release-signing Gradle code. No passwords or private key. |
| [config/functions.env.example](config/functions.env.example) | Existing notification/invitation parameter names, not a copy of deployed settings. |
| [config/worker.dev.vars.example](config/worker.dev.vars.example) | Existing staging Worker binding names, not credentials. |
| [required-private-files.json](required-private-files.json) | Required files, their purpose/authority and how to obtain them. No values. |

There is no active root `.env` in the verified release checkout. The Secrets Gradle Plugin falls back to the existing `.env.example`. `FIREBASE_API_KEY`, emulator hosts, `GCLOUD_PROJECT` and invitation environment settings are explicitly excluded from generated Android constants in `app/build.gradle.kts`. Filling an API key into `.env` does not configure Android Firebase sign-in: the correct variant's `google-services.json` is required.

## Toolchain and isolated development

Use JDK 21, Node 22, Python 3.11+, the checked-in Gradle 9.3.1 wrapper, Android SDK 36.1 and build-tools 36.0.0 as requested by the pinned build. Keep AGP/Kotlin/Room/Firebase versions in `gradle/libs.versions.toml`; do not upgrade them as a setup shortcut. Set your own `JAVA_HOME` and `ANDROID_HOME`. `local.properties` is machine-specific and stays ignored.

For a fresh, separate demo clone:

```sh
git clone https://github.com/Thiru-Selvam-06/aidhunt-trac-release.git
cd aidhunt-trac-release
git switch main
git rev-parse HEAD
python scripts/bootstrap_demo.py
npm ci --prefix functions
npm ci --prefix security-tests
```

`bootstrap_demo.py` creates synthetic debug configuration and refuses to replace a different existing configuration. The tracked `app/src/deviceTest/google-services.json` remains the demo project `demo-aidhunt-v16`. Demo emulators require no production key or service-account credentials. Use `firebase.ci.json`, `firebase.device-test.json`, the existing [CI workflow](../.github/workflows/android-ci.yml) and [setup chapter](../docs/handbook/01-setup.md); always specify the demo project explicitly because `.firebaserc` defaults to the live project.

On Windows use `gradlew.bat`; on POSIX make `gradlew` executable. Representative checks:

```sh
python -m unittest discover -s scripts -p "test_*.py"
python scripts/interaction_inventory.py --check
python scripts/suite_inventory.py --check
./gradlew :app:testDebugUnitTest --init-script scripts/ci-tests.gradle -PciSuite=unit --max-workers=2 --no-configuration-cache
./gradlew -PdeviceTest=true :app:assembleDeviceTest :app:assembleDeviceTestAndroidTest
```

Repeat the host partition with `-PciSuite=database` and `-PciSuite=compose`. Outbox integration requires local Firestore; for POSIX, use the same explicit scope as CI:

```sh
export GCLOUD_PROJECT=demo-aidhunt-v16
export FIRESTORE_EMULATOR_HOST=127.0.0.1:18180
npx --yes firebase-tools@15.33.0 emulators:exec --project demo-aidhunt-v16 --only firestore --config firebase.ci.json "./gradlew :app:testDebugUnitTest --init-script scripts/ci-tests.gradle -PciSuite=outbox --max-workers=2 --no-configuration-cache"
```

PowerShell uses `$env:GCLOUD_PROJECT='demo-aidhunt-v16'` and `$env:FIRESTORE_EMULATOR_HOST='127.0.0.1:18180'`. `FIREBASE_AUTH_EMULATOR_HOST` must remain unset/empty for the existing backend HTTP/Rules tests; set it to `127.0.0.1:19099` only for the Auth/device integration workflow with Auth, Firestore and Functions emulators. Do not globally force `FUNCTIONS_EMULATOR=true`; the Firebase CLI supplies it inside the Functions emulator. Use the existing CI commands for the full integration journey, including its disposable-AVD network fixture. No suite is optional merely because these shorter commands are shown.

Never set `GOOGLE_APPLICATION_CREDENTIALS` for synthetic financial tests. A separate package is not sufficient backend isolation: keep the existing project/workspace guards and emulator ports. Physical Android hardware is not required for automated emulator tests.

## Reproduce the existing Firebase-backed product

1. Use the existing project **`trac-aidhunt`**, Android package **`com.aistudio.aidhunttrac.cedlir`**. Do not create another Firebase project or automatically create a business workspace.
2. An authorized project member downloads this Android application's `google-services.json` from Firebase Console → Project settings → Your apps. Place it at **`app/src/release/google-services.json`**. Keep the original file ignored. The redacted example preserves identifiers for comparison, but its placeholder key is deliberately not runnable.
3. Do not copy production configuration over the demo `deviceTest` file. Debug/emulator and release configurations are intentionally different. `.livetest` and `.physicaltest` variants require their own matching application registration and explicit scope; do not substitute the normal release JSON or enable them automatically.
4. Verify the project/package without printing API-key values. Real account authentication, active workspace membership, roles and existing server policy remain mandatory. An API key alone does not authorize business access.
5. No new Auth/Firestore/Billing/API/Rules/Functions/Worker configuration is required merely to transfer these source files. Console changes, live tests, backend deployment and recovery of retained financial operations need their own approval. Never replay the completed seven legacy-receipt correction.

Firebase documents its Android configuration as public project/app identifiers, whereas Admin service-account private keys are sensitive. This repository's example still redacts client API-key values because this handoff did not inspect their current API restrictions or other uses. Obtain the complete original client configuration through the existing project rather than assuming a general Google API key is safe to publish. See [Firebase Android setup](https://firebase.google.com/docs/android/setup) and [Firebase security checklist](https://firebase.google.com/support/guides/security-checklist).

## Produce an update signed by the original owner

The product is **1.0.18 / versionCode 19**. Release signing is mandatory; the build has no debug-key fallback. The verified original certificate SHA256 is:

```text
ae954fdb33bb43c6781a6e91f615ea8c7861e4a8c5d65a5c0ea878c693db20a5
```

The release owner can sign the tested source without transferring the private key to every developer. If an authorized release maintainer receives it through a protected process, keep the original key and filled signing properties outside Git. Fill the example privately; `storeFile` points to the original key, with the original alias/passwords. Example PowerShell invocation using a local path only:

```powershell
$env:TRAC_SIGNING_PROPERTIES = 'C:/protected-trac/keystore.properties'
.\gradlew.bat :app:assembleRelease :app:lintRelease "-PreleaseSigningProperties=$env:TRAC_SIGNING_PROPERTIES" --max-workers=2 --no-configuration-cache
```

Verify the resulting package, version, certificate, Firebase project, normal release flags and checksum before installation. Install as an update; do not uninstall or clear data. If a phone already has a higher version code, a separately reviewed version-only update is needed; do not force a destructive downgrade.

No private signing file, password, Admin service-account key, Worker credential, provider credential or reusable account/session token is included. The original key can sign trusted app updates, while backend service-account keys can grant privileged server access. They must be supplied through the release/backend owner's protected process, not this public Git history. No private archive was created or uploaded by this handoff.

## Verification and known limits

The baseline APK was rebuilt from restored main: 41 focused release tests passed; signed assembly/lint passed with 0 errors and 120 existing warnings. APK SHA256: `e2b935d9d5db6068e0792b19fc2065616d24287cb5729fe77738af9778e56ad9`.

[The exact-source GitHub run](https://github.com/Thiru-Selvam-06/aidhunt-trac-release/actions/runs/45203874735) passed nine mandatory jobs but failed one of 52 Android emulator tests: `CashBalanceCloudDeviceTest` reported `FirebaseApp was deleted` during Auth emulator setup after earlier replica-app cleanup. The final regression gate correctly failed. This handoff preserves that exact application/test source; it does not silently fix, skip or weaken the case. Full CI is not claimed green.

Automated physical-device testing: **NOT TESTED**. Manual physical-device testing: **NOT TESTED**. Native FCM remains disabled in the normal product; existing Owner in-app notifications are a separate feature. Deferred features and existing capability gates remain unchanged. Source configuration does not prove production backend deployment, live OTP delivery or customer acceptance; verify those separately.
