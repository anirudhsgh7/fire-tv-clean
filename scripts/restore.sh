#!/usr/bin/env bash
# Undo the Blackbox changes: settings, cover permission, app, config, HOME.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
need_device

printf 'Restore privacy settings, animations, and remove Blackbox? [y/N] '
read -r answer
[[ $answer == y || $answer == Y ]] || { echo "cancelled"; exit 0; }

echo "Restoring privacy settings..."
adb_ shell settings put secure limit_ad_tracking 0
adb_ shell settings put secure amz_limit_ad_tracking 0
adb_ shell settings put secure non_amz_limit_ad_tracking 0
adb_ shell settings put secure advertisingIdApp:interestBasedAds:value 1
adb_ shell settings put secure USAGE_METRICS_UPLOAD_ENABLED 1
adb_ shell settings put secure usage_metrics_marketing_enabled 1
adb_ shell settings put secure settingsApp:appUsageData:value 1
adb_ shell settings put secure settingsApp:deviceUsageData:value 1
adb_ shell settings put secure alexa_wake_word_disabled 0
adb_ shell settings delete secure voice_disabled >/dev/null 2>&1 || true

echo "Restoring stock animation values..."
adb_ shell settings delete global window_animation_scale >/dev/null 2>&1 || true
adb_ shell settings delete global transition_animation_scale >/dev/null 2>&1 || true
adb_ shell settings delete global animator_duration_scale >/dev/null 2>&1 || true

echo "Removing the alarm timing override..."
adb_ shell settings delete global alarm_manager_constants >/dev/null 2>&1 || true

echo "Removing Blackbox..."
adb_ shell pm uninstall dev.anirudh.blackbox >/dev/null 2>&1 || true
adb_ shell rm -rf /sdcard/Android/data/dev.anirudh.blackbox >/dev/null 2>&1 || true

echo "Returning HOME to Amazon..."
adb_ shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1 || \
  adb_ shell input keyevent KEYCODE_HOME >/dev/null 2>&1 || true

echo "Restore complete. Reboot if HOME still shows BLACKBOX."
