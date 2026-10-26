#!/usr/bin/env bash
# Apply the reversible Fire OS 5 privacy and animation settings used by Blackbox.
# Usage: ./scripts/privacy.sh [--dry-run]
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
need_device
require_fireos5

DRY=0
[[ ${1:-} == --dry-run ]] && DRY=1

SECURE_KEYS=(
  limit_ad_tracking
  amz_limit_ad_tracking
  non_amz_limit_ad_tracking
  advertisingIdApp:interestBasedAds:value
  USAGE_METRICS_UPLOAD_ENABLED
  usage_metrics_marketing_enabled
  settingsApp:appUsageData:value
  settingsApp:deviceUsageData:value
  alexa_wake_word_disabled
  voice_disabled
)
GLOBAL_KEYS=(
  window_animation_scale
  transition_animation_scale
  animator_duration_scale
)

show() {
  local key
  for key in "${SECURE_KEYS[@]}"; do
    printf 'secure %-45s %s\n' "$key" "$(adb_ shell settings get secure "$key" | tr -d '\r')"
  done
  for key in "${GLOBAL_KEYS[@]}"; do
    printf 'global %-45s %s\n' "$key" "$(adb_ shell settings get global "$key" | tr -d '\r')"
  done
}

if [[ $DRY == 1 ]]; then
  echo "Current values (dry-run; nothing will change):"
  show
  exit 0
fi

STAMP=$(date +%Y%m%d-%H%M%S)
OUT=$ROOT/report/privacy-before-$STAMP.txt
mkdir -p "$ROOT/report"
chmod 700 "$ROOT/report"
show > "$OUT"
echo "saved original values to $OUT"

adb_ shell settings put secure limit_ad_tracking 1
adb_ shell settings put secure amz_limit_ad_tracking 1
adb_ shell settings put secure non_amz_limit_ad_tracking 1
adb_ shell settings put secure advertisingIdApp:interestBasedAds:value 0
adb_ shell settings put secure USAGE_METRICS_UPLOAD_ENABLED 0
adb_ shell settings put secure usage_metrics_marketing_enabled 0
adb_ shell settings put secure settingsApp:appUsageData:value 0
adb_ shell settings put secure settingsApp:deviceUsageData:value 0
adb_ shell settings put secure alexa_wake_word_disabled 1
adb_ shell settings put secure voice_disabled 1
adb_ shell settings put global window_animation_scale 0.5
adb_ shell settings put global transition_animation_scale 0.5
adb_ shell settings put global animator_duration_scale 0.5

echo
echo "Applied:"
show
echo
echo "Restore with: ./scripts/restore.sh"
