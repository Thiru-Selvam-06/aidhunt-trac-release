# Current entry point

Use docs/handbook/README.md for the maintained setup and limitations. This supplemental Windows guide does not prove all tests or live backend availability.

# AIDHUNT Trac - Environment Setup & Deployment Guide

This document describes the environment architecture, dependency requirements, secret classifications, and setup procedures for cloning, building, testing, and installing AIDHUNT Trac on a Windows workstation or CI runner.

---

## 1. Quick Start: One-Command Bootstrap & USB Installation

For a developer with Git, a compatible JDK 21, and the Android SDK:

```powershell
# 1. Clone repository
git clone https://github.com/Thiru-Selvam-06/aidhunt-trac-release.git
cd aidhunt-trac-release

# 2. Build the isolated development APK
.\scripts\setup-windows.ps1 -Mode Build

# 3. Connect Android phone via USB (with USB Debugging enabled) and install
.\scripts\setup-windows.ps1 -Mode InstallUsb
```

Alternatively, you can use the shortcut scripts:
```powershell
.\scripts\build-device-debug.ps1
.\scripts\install-usb-debug.ps1
```

To run all unit, backend, and inventory test suites:
```powershell
.\scripts\run-tests.ps1
```

---

## 2. Toolchain Prerequisites

| Tool | Required Version | Purpose | Discovery / Path |
|------|------------------|---------|------------------|
| **Windows OS** | Windows 10/11 (64-bit) | Workstation OS | Native |
| **Java JDK** | OpenJDK 17 or 21 (Temurin / Microsoft / Corretto) | Android Gradle build | `JAVA_HOME` or system `PATH` |
| **Android SDK** | API 34+ (Build-Tools 34.0.0+) | Android compilation | `ANDROID_HOME` or `%LOCALAPPDATA%\Android\Sdk` |
| **Android Platform Tools** | `adb` 1.0.41+ | USB device communication & port forwarding | `%ANDROID_HOME%\platform-tools` |
| **Gradle** | 9.3.1 (via bundled wrapper) | Project build system | `./gradlew.bat` in project root |
| **Node.js** | v20+ or v22+ LTS | Backend tests, Cloud Functions, Cloudflare | `node` in system `PATH` |
| **Python** | 3.10+ | Suite inventory & audit tests | `python` in system `PATH` |

---

## 3. Environment Isolation & Safety

### Build Variants & Application IDs

| Variant | Application ID | Target Environment | Database / Storage | Safety Isolation |
|---------|----------------|-------------------|-------------------|------------------|
| **`deviceTest`** *(Default Development)* | `com.aistudio.aidhunttrac.cedlir.devicetest` | Local Firebase Emulator (`demo-aidhunt-v16`) | `aidhunt_device_test_v18.db` | **Complete Isolation**: Coexists with production without overwriting; strictly zero production data access. |
| **`debug`** | `com.aistudio.aidhunttrac.cedlir` | Development / Local | In-memory Room DB | Non-destructive debug profile. |
| **`release`** | `com.aistudio.aidhunttrac.cedlir` | Production PMS | Production Room DB | Production signing key required. |

### USB Safety Protection

The installation script (`scripts/setup-windows.ps1`) verifies the connected device before any deployment. If the production package `com.aistudio.aidhunttrac.cedlir` is already installed on the phone, the installer prints an informational notice and safely deploys `com.aistudio.aidhunttrac.cedlir.devicetest`. Production application state and local Room databases are NEVER overwritten or wiped.

### Loopback Port Forwarding (ADB Reverse)

When testing on a physical Android device over USB, the installer automatically configures port forwarding so the device can access the developer's local Firebase emulators without needing public IP exposure:
- **`19099`** : Firebase Authentication Emulator
- **`18180`** : Cloud Firestore Emulator
- **`15001`** : Cloud Functions Emulator

---

## 4. Configuration and Secrets Classification

| Configuration Key | Classification | Location | Required for Clean Clone / Dev? |
|-------------------|----------------|----------|--------------------------------|
| `app/src/deviceTest/google-services.json` | **Public Client Config** (Safe) | Tracked in Git | **Yes** (Bundled in repository) |
| `.env.example` | **Template** (Safe) | Tracked in Git | **Yes** (Copied automatically to `.env`) |
| `GCLOUD_PROJECT` | **Non-Sensitive Config** | `.env` / CI env | **No** (Defaults to `demo-aidhunt-v16`) |
| `FIRESTORE_EMULATOR_HOST` | **Non-Sensitive Config** | `.env` / CI env | **No** (Defaults to `127.0.0.1:18180`) |
| `FIREBASE_AUTH_EMULATOR_HOST` | **Non-Sensitive Config** | `.env` / CI env | **No** (Defaults to `127.0.0.1:19099`) |
| `app/src/physicalTest/google-services.json` | **Privileged / Production Config** | Local Only (Ignored in `.gitignore`) | **No** (Never committed) |
| `app/src/liveTest/google-services.json` | **Privileged / Production Config** | Local Only (Ignored in `.gitignore`) | **No** (Never committed) |
| `GOOGLE_APPLICATION_CREDENTIALS` | **Privileged Secret** | GitHub Secrets (CI only) | **No** (Never committed) |
| `CLOUDFLARE_API_TOKEN` | **Privileged Secret** | GitHub Secrets (CI only) | **No** (Never committed) |
| `RELEASE_KEYSTORE_PASSWORD` | **Privileged Secret** | GitHub Secrets (CI only) | **No** (Never committed) |

---

## 5. Local Firebase Emulator Setup (Optional)

If running the complete backend suite locally:

```powershell
# Start local emulators on isolated ports (19099, 18180, 15001)
.\scripts\start-dev-backend.ps1
```

This launches the Firebase Emulator Suite under project `demo-aidhunt-v16`. Synthetic demo fixtures are automatically loaded with zero production writes.
