#!/usr/bin/env bash
# Only this disposable-emulator wrapper configures synthetic network validation.
# The production APK's WorkManager constraints and all financial assertions stay intact.
set -euo pipefail
test "${CI:-}" = true
test "${GITHUB_ACTIONS:-}" = true
test "${GCLOUD_PROJECT:-}" = demo-aidhunt-v16
test -z "${GOOGLE_APPLICATION_CREDENTIALS:-}"
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
mkdir -p ci-reports/device-sync
node functions/device-network-fixture.cjs > ci-reports/device-sync/network-fixture.log 2>&1 &
fixture_pid=$!
trap 'kill "$fixture_pid" 2>/dev/null || true' EXIT
for i in $(seq 1 30); do
 if curl --fail --silent --max-time 1 http://127.0.0.1:13080/generate_204 > /dev/null; then break; fi
 sleep 1
done
curl --fail --silent --max-time 2 http://127.0.0.1:13080/generate_204 > /dev/null
probe=http://10.0.2.2:13080/generate_204
adb shell settings put global captive_portal_http_url "$probe"
adb shell settings put global captive_portal_fallback_url "$probe"
adb shell settings put global captive_portal_other_fallback_urls "$probe"
adb shell settings put global captive_portal_use_https 0
adb shell device_config put connectivity captive_portal_use_https 0
# Recreate the emulator network so NetworkMonitor reads the fixture configuration.
adb shell cmd connectivity airplane-mode enable
sleep 2
adb shell cmd connectivity airplane-mode disable
sleep 5
adb shell dumpsys connectivity > ci-reports/device-sync/connectivity-before.txt
# The first instrumentation case asserts actual active-network VALIDATED capability.
npx --yes firebase-tools@15.33.0 emulators:exec --project demo-aidhunt-v16 --only auth,firestore,functions --config firebase.device-test.json "bash scripts/run_device_ci.sh"
