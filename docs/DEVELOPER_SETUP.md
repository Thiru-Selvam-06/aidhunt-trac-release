# AIDHUNT TRAC — DEVELOPER SETUP GUIDE
## Onboarding, Local Environment, Builds, Testing & Architecture Reference

---

## 1. Project Overview

**AIDHUNT TRAC** is an offline-first, multi-user Android application and financial ledger for tractor and agricultural machinery businesses. It enables tractor owners, business partners, and operators to record agricultural jobs (e.g. Rotavator, Cultivator, Harvester), track fuel and maintenance expenses, manage customer accounts and dues, calculate driver earnings, and maintain an immutable financial ledger across distributed offline devices with cloud synchronization.

### Key Application Architecture
- **Language & UI Framework**: 100% Kotlin with Jetpack Compose (Material 3).
- **Architecture Pattern**: MVVM with unidirectional data flow (StateFlow, Compose runtime state).
- **Local Persistence**: Room SQLite database (`aidhunt_live_test_v18.db` / `aidhunt_dev_v18.db`) with reactive DAOs and offline queueing outbox.
- **Remote Synchronization**: Google Cloud Firestore & Firebase Auth (with Credential Manager) backed by cloud functions and staging Cloudflare workers.
- **Financial Engine**: `FinancialCalculationEngine.kt` — pure, deterministic calculation engine responsible for financial summaries, dues, partner shares, and operator attribution.
- **Localization**: Full English (EN) and Tamil (TA) dynamic runtime localization without Activity recreation.

### Important Source Directories
| Directory | Description |
|---|---|
| `app/src/main/java/com/example/ui/screens/home/` | Home Dashboard (`HomeScreen.kt`, Today's Job Entries, Workspace Banner) |
| `app/src/main/java/com/example/ui/screens/report/` | Financial Reports (`BusinessOverviewScreen.kt`, `ExpensesTab.kt`, `ReportScreen.kt`) |
| `app/src/main/java/com/example/ui/screens/entry/` | Job Entry & Customer workflows (`NewEntryScreen.kt`) |
| `app/src/main/java/com/example/ui/components/` | Reusable UI components (`ExpenseUiComponents.kt`, `CashBalanceEditor.kt`, `ReportDateFilterControl.kt`) |
| `app/src/main/java/com/example/ui/viewmodel/` | ViewModels (`MainViewModel.kt`, `ReportViewModel.kt`) |
| `app/src/main/java/com/example/data/repository/` | Local & Remote Repositories (`WorkspaceRepository.kt`, `FirestoreRepository.kt`) |
| `app/src/main/java/com/example/data/entity/` | Room Database Entities (`JobEntryEntity`, `ExpenseEntity`, `CustomerEntity`) |
| `app/src/main/java/com/example/data/dao/` | Room Data Access Objects (`JobEntryDao`, `ExpenseDao`, `CustomerDao`) |
| `app/src/main/java/com/example/ui/util/` | Domain helpers (`FinancialCalculationEngine.kt`, `WorkspaceBannerStorage.kt`, `RoleUtils.kt`) |
| `app/src/test/java/com/example/` | Unit, Robolectric, and Compose UI automated tests |
| `.github/workflows/` | GitHub Actions CI workflows (`android-ci.yml`) |

---

## 2. Prerequisites & Toolchain Versions

All toolchain versions are derived directly from `build.gradle.kts`, `app/build.gradle.kts`, and `gradle-wrapper.properties`:

- **Operating System**: Windows 10/11, macOS (Apple Silicon or Intel), or Linux (Ubuntu 22.04+).
- **JDK**: Java 21 LTS (Oracle JDK 21 or Eclipse Temurin 21). *Gradle 9.3.1 requires JDK 17+; JDK 21 is the project standard.*
- **Android SDK**:
  - `compileSdk`: **36** (Android 16 / Baklava release)
  - `targetSdk`: **36**
  - `minSdk`: **24** (Android 7.0 Nougat)
  - Build Tools: **36.0.0**
- **Gradle**: **9.3.1** (via included `gradlew` / `gradlew.bat` wrapper).
- **Android Studio**: Android Studio Ladybug (2024.2.1) or Meerkat (2024.3.1)+ with Kotlin 2.1 support.
- **Python**: Python 3.10+ (for CI scripts, inventory verification, and publication guards).
- **Node.js**: Node 22 LTS (for Firebase Functions emulator and security rule checks).

---

## 3. Clone and Setup

```powershell
# Clone the repository
git clone https://github.com/Thiru-Selvam-06/aidhunt-trac-release.git
cd aidhunt-trac-release

# Verify JDK 21 is active
java -version

# Verify Git status
git status
```

### Initial Configuration
1. Open the project folder in Android Studio: `File -> Open -> aidhunt-trac-release`.
2. Allow Android Studio to sync Gradle using the bundled Gradle wrapper.
3. If prompted, confirm Android SDK Platform 36 is installed via Android Studio SDK Manager.
4. **Firebase Configuration**: The repository contains `app/src/deviceTest/google-services.json` for isolated local and CI testing. No real production credentials are required for development or running automated test suites.

---

## 4. Build Commands

Always use the Gradle wrapper (`gradlew.bat` on Windows, `./gradlew` on macOS/Linux).

```powershell
# Windows PowerShell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:assembleDeviceTest
.\gradlew.bat :app:assembleLiveTest
```

```bash
# macOS / Linux
./gradlew :app:assembleDebug
./gradlew :app:assembleDeviceTest
./gradlew :app:assembleLiveTest
```

### Build Variants Guide
- **`debug`**: Standard development build variant. Uses local SQLite Room database and mock/emulator endpoints.
- **`deviceTest`**: Isolated testing variant (`.devicetest` application ID suffix). Configured with `DEVICE_TEST=true` for safe local emulators and automated CI without connecting to production.
- **`liveTest`**: Production-mirrored test variant (`.livetest` application ID suffix). Targets live Firebase and Room DB `aidhunt_live_test_v18.db` for physical device validation. *Do not use in automated CI pipelines.*
- **`release`**: Signed production build. Requires protected `keystore.properties` (never committed to repository).

---

## 5. Running Automated Tests

The test suite runs on Robolectric and JUnit with Jetpack Compose UI testing rules. No emulator is required to run Compose UI tests.

```powershell
# 1. Run all newly created UI Redesign test suites:
.\gradlew.bat :app:testDebugUnitTest --tests com.example.HomeDashboardRedesignTest --tests com.example.BusinessOverviewRedesignTest --tests com.example.ExpenseReportRedesignTest --tests com.example.ResponsiveLayoutUiTest --tests com.example.ExpenseRedesignUiTest --tests com.example.ui.WorkspaceBannerStorageTest

# 2. Run partitioned CI suites:
.\gradlew.bat :app:testDebugUnitTest --init-script scripts/ci-tests.gradle -PciSuite=compose
.\gradlew.bat :app:testDebugUnitTest --init-script scripts/ci-tests.gradle -PciSuite=unit
.\gradlew.bat :app:testDebugUnitTest --init-script scripts/ci-tests.gradle -PciSuite=database

# 3. Check interaction inventory drift:
python scripts/interaction_inventory.py --check
```

### Test Reports Location
- **HTML Report**: `app/build/reports/tests/testDebugUnitTest/index.html`
- **JUnit XML Results**: `app/build/test-results/testDebugUnitTest/`

---

## 6. Physical Device Testing

1. **Enable Developer Options**: On your Android device, navigate to `Settings -> About Phone` and tap `Build Number` 7 times.
2. **Enable USB Debugging**: In `Settings -> Developer Options`, enable `USB Debugging` and `Install via USB`.
3. **Connect & Authorize**: Connect via USB and verify device authorization:
   ```powershell
   adb devices -l
   ```
4. **Install Isolated Test APK**:
   ```powershell
   # Install DeviceTest APK (safe, separate package)
   adb install -r -t app/build/outputs/apk/deviceTest/app-deviceTest.apk

   # Or install LiveTest APK
   adb install -r -d -t app/build/outputs/apk/liveTest/app-liveTest.apk
   ```
5. **Capture Logcat Diagnostics**:
   ```powershell
   adb logcat -d -t 1000 *:E | Select-String "FATAL|AndroidRuntime|aidhunt"
   ```

---

## 7. GitHub Actions CI Architecture

The repository uses `.github/workflows/android-ci.yml` for automated continuous integration:
- **Triggers**: Push to `main`, `master`, feature branches, and pull requests.
- **Jobs**:
  1. `static-checks`: Validates Gradle wrapper, compiles `:app:assembleDebug`, runs `:app:lintDebug`.
  2. `inventory`: Verifies test suite inventory, script unit tests, and publication guards (no leaked secrets).
  3. `android-tests`: Runs parallel partitioned suites (`unit`, `database`, `compose`, `outbox`) using JDK 21 and headless test runners.
  4. `aggregate`: Combines test reports and publishes downloadable artifacts.
- **Zero Secrets Requirement**: The workflow runs entirely on isolated test data and emulators. No production secrets or Firebase service accounts are needed or permitted in GitHub Actions.

---

## 8. UI Implementation Map

| Feature Area | Source Files | Description |
|---|---|---|
| **Home Dashboard** | `HomeScreen.kt` | Header, Total Recorded, Total Due, Available Cash, FAB |
| **Workspace Banner** | `HomeScreen.kt`, `WorkspaceBannerStorage.kt` | Default tractor banner, local custom banner selection, per-workspace storage |
| **Today's Job Entries** | `HomeScreen.kt` (lines 580–1050) | Redesigned job card, initials avatar, amount badge, machinery icon, payment status pill |
| **New Job Animation** | `HomeScreen.kt` (lines 970–990) | Red-to-pale-pink 1000ms color transition with `onAnimationComplete` hook |
| **Business Overview** | `BusinessOverviewScreen.kt` | 6 financial cards, driver donut charts, financial scope filters |
| **Records Swipeable Cards** | `BusinessOverviewScreen.kt` (lines 2368–2580) | Style 4 swipe-to-reveal View & Share actions with top Z-order hit-testing |
| **Expense Report** | `ExpensesTab.kt` | Fleet Expenses header, back button, date/operator/tractor/type filters, search bar |
| **Expense Breakdown** | `ExpensesTab.kt` (lines 300–435) | 4 compact tiles + partial 5th, horizontal scroll, full-tile pastel wave fill |
| **Expense Entry Card** | `ExpensesTab.kt`, `ExpenseUiComponents.kt` | Mint category icon container, tractor ref, payment badges, amount pill, date/time |
| **Expense Details** | `ExpensesTab.kt`, `ExpenseUiComponents.kt`, `MainActivity.kt` | Consolidated single header, top summary card with wave accent, compact ID copy, metadata grid |

---

## 9. Protected Logic and Development Rules

1. **`FinancialCalculationEngine` is Protected**: Never reimplement financial arithmetic inside Composable UI functions. All totals, dues, balances, and shares must be derived from `FinancialCalculationEngine.kt`.
2. **Never Commit Credentials**: Never commit production `google-services.json`, keystores, private keys, or API tokens.
3. **Never Test Against Live Production in CI**: CI pipelines must only execute against isolated mock/emulator configurations (`deviceTest` or `debug`).
4. **Preserve Record IDs and Callbacks**: When displaying shortened visual identifiers (such as the Expense Reference card), always retain the complete 64-bit Long ID in data models, callbacks, and clipboard copy operations.
5. **No Force Pushes**: Never force push (`git push -f`) or rewrite published commit history.

---

## 10. Troubleshooting

| Symptom | Cause | Solution |
|---|---|---|
| `Unsupported class file major version 65` | Using JDK 21 with outdated Gradle | Ensure using bundled Gradle 9.3.1 wrapper (`.\gradlew.bat`). |
| `SDK XML version 4 encountered` | Newer Android SDK tools XML schema | Harmless warning; compile and tests proceed normally. |
| `Interaction inventory drift` | Callbacks added or changed in UI | Run `python scripts/interaction_inventory.py` to regenerate manifests. |
| `Device unauthorized` | Phone prompted for USB debugging key | Disconnect USB, unlock phone, reconnect, and tap "Always allow from this computer". |
| `Missing Google Services file` | Compiling without google-services.json | `app/build.gradle.kts` sets `missingGoogleServicesStrategy = WARN`. `deviceTest` includes safe test config. |
