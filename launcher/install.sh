#!/usr/bin/env bash
# Build, install, grant log access and start Blackbox. Undo: launcher/install.sh --remove
set -euo pipefail
cd "$(dirname "$0")"

ADB=${ADB:-adb}
SERIAL=${ADB_SERIAL:-}
adb_() {
  if [[ -n $SERIAL ]]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi
}

if [[ ${1:-} == --remove ]]; then
  adb_ shell pm uninstall dev.anirudh.blackbox >/dev/null 2>&1 || true
  adb_ shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true
  echo "removed Blackbox; HOME returns to Amazon's launcher"
  exit 0
fi

./build.sh
adb_ install -r build/blackbox.apk
if ! adb_ shell pm grant dev.anirudh.blackbox android.permission.READ_LOGS; then
  echo "warning: could not grant READ_LOGS; the watcher will not be able to follow HOME" >&2
fi
if ! adb_ shell appops set dev.anirudh.blackbox SYSTEM_ALERT_WINDOW allow; then
  echo "warning: could not grant draw-over-apps; transitions will show Amazon's home briefly" >&2
fi
# Make the immediate alarm launch possible on Fire OS 5. Restore removes it.
adb_ shell settings put global alarm_manager_constants min_futurity=0 >/dev/null 2>&1 || true
# Blackbox writes its curated default home.conf on first launch. Later edits are left alone.
adb_ shell am start -n dev.anirudh.blackbox/.HomeActivity
echo "installed Blackbox. Press HOME on the remote to test the takeover."
