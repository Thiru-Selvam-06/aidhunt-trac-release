# 1. Development environment

Use Git, Python 3.11+, Node 22 and JDK 21. Pinned inputs: Gradle 9.3.1, AGP 9.1.1, Kotlin 2.2.10, Room 2.7.0, Compose BOM 2024.09.00, Firebase BOM 34.15.0. Check gradle/libs.versions.toml and wrapper properties before upgrading. Compile SDK is 36.1, target 36, minimum 24.

Install the SDK/build tools requested by Gradle, platform-tools and a Google APIs x86_64 API 35 AVD. Linux CI uses KVM. Set JAVA_HOME and ANDROID_HOME; use Android Studio or sdkmanager to accept licenses. Do not copy another developer's SDK path, caches, keys or database.

Clone the reviewed branch, record git rev-parse HEAD, then run python scripts/bootstrap_demo.py. This derives a synthetic compile configuration from the deviceTest template, is idempotent and refuses to overwrite different existing configuration. It creates no cloud resources.

Install locked dependencies with npm ci --prefix functions and npm ci --prefix security-tests. Worker tests use Node directly. Make gradlew executable on POSIX; Windows uses gradlew.bat. Initial downloads need connectivity; --offline works only with populated caches.

| Variant | Package suffix | Backend boundary |
|---|---|---|
| debug/release | none | Standard configured Firebase and Functions route; currently blocked |
| deviceTest | .devicetest | demo-aidhunt-v16; Auth 19099, Firestore 18180, Functions 15001, loopback |
| physicalTest | .physicaltest | Explicit approved live staging workspaces and Worker |
| liveTest | .livetest | Live project; workspace/build routing; not a safe general demo |

A separate package does not itself isolate the backend. FirebaseEnvironment enforces project/workspace checks. Never use live variants as CI fixtures.

## Local tests

```sh
python -m unittest discover -s scripts -p "test_*.py"
python scripts/suite_inventory.py --check
python scripts/interaction_inventory.py --check
./gradlew :app:testDebugUnitTest --init-script scripts/ci-tests.gradle
./gradlew :app:assembleDebug :app:lintDebug
```

POSIX Rules/backend integration:
```sh
export GCLOUD_PROJECT=demo-aidhunt-v16
export FIRESTORE_EMULATOR_HOST=127.0.0.1:18180
npx --yes firebase-tools@15.33.0 emulators:exec --project demo-aidhunt-v16 --only firestore --config firebase.ci.json "node scripts/run_backend_suite.mjs rules"
# Repeat with backend instead of rules.
```

PowerShell uses $env:GCLOUD_PROJECT='demo-aidhunt-v16' and $env:FIRESTORE_EMULATOR_HOST='127.0.0.1:18180'. Do not supply GOOGLE_APPLICATION_CREDENTIALS. Fixtures clear only disposable emulators.

## Android emulator journeys

On a disposable AVD with CI=true, the demo variables above and FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:19099:
```sh
npx --yes firebase-tools@15.33.0 emulators:exec --project demo-aidhunt-v16 --only auth,firestore,functions --config firebase.device-test.json "bash scripts/run_device_ci.sh"
```

The runner reverses loopback ports, seeds synthetic records and runs instrumentation-plan.json classes. It does not prove carrier SMS. Three other classes need additional fixtures and remain explicit gaps.

Release builds require protected keystore.properties or -PreleaseSigningProperties and fail without keys; no debug fallback. Obtain credentials through the release owner's secure process. Never clear installed app data to solve signing mismatch.
