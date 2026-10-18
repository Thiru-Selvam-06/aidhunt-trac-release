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
./gradlew -PdeviceTest=true :app:connectedDeviceTestAndroidTest --stacktrace --max-workers=2 \
 "-Pandroid.testInstrumentationRunnerArguments.class=$classes"
