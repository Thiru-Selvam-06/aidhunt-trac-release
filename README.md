# AIDHUNT Trac

Kotlin Android workspace and tractor-job ledger following Architecture v1.6 and documented amendments.

**Release gate: NOT READY.** The standard app still routes trusted operations to Firebase Functions; the latest authorized inspection found none deployed. The Cloudflare service is staging-only. A green CI run does not restore production services.

Read the [Developer Handbook](docs/handbook/README.md): fresh-computer setup, architecture, roles, calculations, offline recovery, tests, CI, security, operations and limitations.

## Safe fresh-clone setup

Install Git, Python 3.11+, Node 22, JDK 21 and Android Studio / Command-line Tools with SDK 34+, platform tools and an API 35 Google APIs emulator or physical Android phone with USB debugging. Keep wrapper/catalog versions pinned.

### One-Command Windows Bootstrap and USB Deployment

```powershell
# Clone the repository
git clone https://github.com/Thiru-Selvam-06/aidhunt-trac-release.git
cd aidhunt-trac-release

# One-command build of isolated development APK
.\scripts\setup-windows.ps1 -Mode Build

# Connect your Android phone via USB and install safely
.\scripts\setup-windows.ps1 -Mode InstallUsb

# Run all test suites (Android, backend policy, Cloudflare, inventory)
.\scripts\run-tests.ps1
```

For complete documentation on environment variables, secrets classification, and architecture isolation, see [ENVIRONMENT_SETUP.md](docs/ENVIRONMENT_SETUP.md).

### Manual Setup Steps

```sh
# Select the reviewed portable-verification branch or exact commit.
python scripts/bootstrap_demo.py
npm ci --prefix functions
npm ci --prefix security-tests
./gradlew :app:testDebugUnitTest --init-script scripts/ci-tests.gradle
./gradlew -PdeviceTest=true :app:assembleDeviceTest :app:assembleDeviceTestAndroidTest
```

Windows uses gradlew.bat. Set JAVA_HOME and ANDROID_HOME; Android Studio creates ignored local.properties. First builds download dependencies. Demo tests require no production credentials.

Use the separate .devicetest package and demo emulators; never install over business data just to try the repository. Follow the [setup chapter](docs/handbook/01-setup.md) for runtime commands.

One [GitHub Actions workflow](.github/workflows/android-ci.yml) runs layered checks and representative Android-emulator journeys. Explicit exclusions and manual gaps remain. No workflow deploys. Private keys, signing files, device captures and business snapshots are not portable source dependencies.
