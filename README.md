# AIDHUNT Trac

Kotlin Android workspace and tractor-job ledger following Architecture v1.6 and documented amendments.

For the selected TRAC 1.0.18 configuration and protected release-signing handoff, start with [the developer handoff](handoff/README.md). App source remains the `924a6d0` snapshot; setup documentation does not authorize deployment or financial recovery.

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

## Developer Documentation & UI Handoff

For comprehensive developer guides, architectural references, testing procedures, and release handoff:

- **[Developer Setup Guide](docs/DEVELOPER_SETUP.md)**: Full local onboarding, required JDK 21 / Android SDK 36 toolchains, build variants (`debug`, `deviceTest`, `liveTest`), physical device testing via ADB, and troubleshooting.
- **[Testing Guide](docs/TESTING.md)**: Unit and Jetpack Compose UI testing patterns, Robolectric execution, animation testing via Compose clock, responsive matrix coverage, and CI test partitioning.
- **[GitHub Release Handoff](docs/GITHUB_RELEASE_HANDOFF.md)**: Production-quality handoff specification for the next developer, covering UI preservation, Git audit, and verification evidence.
- **CI Workflow**: Configured at [`.github/workflows/android-ci.yml`](.github/workflows/android-ci.yml) for automated static analysis, partitioned regression testing, and security rule audits.

### Quick Build & Test Commands

```powershell
# Build isolated development APK
.\gradlew.bat :app:assembleDebug

# Run UI Redesign & Regression Tests
.\gradlew.bat :app:testDebugUnitTest --tests com.example.HomeDashboardRedesignTest --tests com.example.BusinessOverviewRedesignTest --tests com.example.ExpenseReportRedesignTest --tests com.example.ResponsiveLayoutUiTest --tests com.example.ExpenseRedesignUiTest

# Check interaction inventory consistency
python scripts/interaction_inventory.py --check
```
