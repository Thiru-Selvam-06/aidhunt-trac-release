#!/usr/bin/env bash
# Intended only for a fresh GitHub-hosted Android emulator and demo Firebase emulators.
set -euo pipefail
test "${CI:-}" = "true"
test "${GCLOUD_PROJECT:-}" = "demo-aidhunt-v16"
test "${FIRESTORE_EMULATOR_HOST:-}" = "127.0.0.1:18180"
test "${FIREBASE_AUTH_EMULATOR_HOST:-}" = "127.0.0.1:19099"
for port in 18180 19099 15001; do adb reverse tcp:$port tcp:$port; done
python3 scripts/suite_inventory.py --check
classes=$(python3 -c 'import json; print(",".join(json.load(open("scripts/instrumentation-plan.json"))["runnable"]))')
node functions/device-test-fixtures.cjs
# Retain read-only demo diagnostics even when Gradle fails. Do not mask its exit code.
capture_sync_evidence() {
 mkdir -p ci-reports/device-sync
 date -u +'%Y-%m-%dT%H:%M:%S.%NZ' > ci-reports/device-sync/host-time.txt
 adb shell date -u > ci-reports/device-sync/android-time.txt 2>&1 || true
 # Gradle uninstalls the synthetic app before its task returns; logcat survives.
 adb logcat -d -s DEVICE_SYNC_EVIDENCE:I '*:S' \
  > ci-reports/device-sync/synthetic-sync-diagnostics.log 2>/dev/null || true
 curl --fail --silent --show-error --max-time 10 \
  http://127.0.0.1:18180/emulator/v1/projects/demo-aidhunt-v16:ruleCoverage \
  > ci-reports/device-sync/rule-coverage.json || true
}
trap capture_sync_evidence EXIT
./gradlew -PdeviceTest=true :app:connectedDeviceTestAndroidTest --stacktrace --max-workers=2 \
 "-Pandroid.testInstrumentationRunnerArguments.class=$classes"
