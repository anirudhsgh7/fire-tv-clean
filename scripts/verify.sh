#!/usr/bin/env bash
# Check the Blackbox install and the settings this project owns.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
need_device
require_fireos5

fail=0
check() {
  local label=$1 value=$2 expected=$3
  if [[ $value == "$expected" ]]; then
    printf 'ok       %s\n' "$label"
  else
    printf 'FAIL     %s (expected %s, got %s)\n' "$label" "$expected" "$value"
    fail=1
  fi
}

pkg=$(adb_ shell pm path dev.anirudh.blackbox 2>/dev/null | tr -d '\r')
[[ -n $pkg ]] && printf 'ok       Blackbox installed\n' || { printf 'FAIL     Blackbox not installed\n'; fail=1; }

perms=$(adb_ shell dumpsys package dev.anirudh.blackbox 2>/dev/null | sed -n '/grantedPermissions:/,/requested permissions:/p')
grep -q 'android.permission.READ_LOGS' <<< "$perms" && printf 'ok       READ_LOGS granted\n' || { printf 'FAIL     READ_LOGS not granted\n'; fail=1; }
grep -q 'android.permission.SYSTEM_ALERT_WINDOW' <<< "$perms" && printf 'ok       draw-over-apps granted\n' || { printf 'FAIL     draw-over-apps not granted\n'; fail=1; }
grep -q 'android.permission.READ_EXTERNAL_STORAGE' <<< "$perms" && printf 'ok       Download import permission\n' || { printf 'WARN     READ_EXTERNAL_STORAGE not granted\n'; }

service=""
for _ in 1 2 3 4 5; do
  service=$(adb_ shell dumpsys activity services dev.anirudh.blackbox 2>/dev/null | tr -d '\r')
  grep -q 'HomeWatcher' <<< "$service" && grep -q 'isForeground=true' <<< "$service" && break
  sleep 1
done
grep -q 'HomeWatcher' <<< "$service" && grep -q 'isForeground=true' <<< "$service" \
  && printf 'ok       HomeWatcher foreground service\n' \
  || { printf 'FAIL     HomeWatcher is not running foreground\n'; fail=1; }

check 'ad tracking limited' "$(adb_ shell settings get secure limit_ad_tracking | tr -d '\r')" 1
check 'Amazon ad tracking limited' "$(adb_ shell settings get secure amz_limit_ad_tracking | tr -d '\r')" 1
check 'interest-based ads off' "$(adb_ shell settings get secure advertisingIdApp:interestBasedAds:value | tr -d '\r')" 0
check 'usage upload off' "$(adb_ shell settings get secure USAGE_METRICS_UPLOAD_ENABLED | tr -d '\r')" 0
check 'app usage reporting off' "$(adb_ shell settings get secure settingsApp:appUsageData:value | tr -d '\r')" 0
check 'device usage reporting off' "$(adb_ shell settings get secure settingsApp:deviceUsageData:value | tr -d '\r')" 0
check 'Alexa wake word off' "$(adb_ shell settings get secure alexa_wake_word_disabled | tr -d '\r')" 1
check 'window animation scale' "$(adb_ shell settings get global window_animation_scale | tr -d '\r')" 0.5
check 'transition animation scale' "$(adb_ shell settings get global transition_animation_scale | tr -d '\r')" 0.5
check 'animator duration scale' "$(adb_ shell settings get global animator_duration_scale | tr -d '\r')" 0.5

if adb_ shell test -f /sdcard/Android/data/dev.anirudh.blackbox/files/home.conf >/dev/null 2>&1; then
  printf 'ok       home.conf present\n'
else
  printf 'WARN     home.conf not created yet (defaults are in use)\n'
fi

home=$(adb_ shell dumpsys window 2>/dev/null | sed -n 's/.*mCurrentFocus=Window{[^ ]* [^ ]* \([^}]*\)}.*/\1/p' | head -1)
printf 'info     current focus: %s\n' "${home:-unknown}"

if [[ $fail == 0 ]]; then
  echo "all checks passed"
else
  echo "one or more checks failed"
  exit 1
fi
