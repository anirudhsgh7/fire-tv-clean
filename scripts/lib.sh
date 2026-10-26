#!/usr/bin/env bash
# Shared helpers for the Blackbox Fire OS 5 scripts.
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
ADB=${ADB:-adb}
SERIAL=${ADB_SERIAL:-}

adb_() {
  if [[ -n $SERIAL ]]; then "$ADB" -s "$SERIAL" "$@"; else "$ADB" "$@"; fi
}

need_device() {
  local devices
  devices=$("$ADB" devices | awk 'NR > 1 && $2 == "device" {print $1}')
  if [[ -z $devices ]]; then
    echo "No authorised device. Connect with: adb connect <stick-ip>:5555" >&2
    echo "Accept 'Allow USB debugging?' on the TV, then run this again." >&2
    exit 1
  fi
  if [[ -z $SERIAL ]]; then
    if [[ $(printf '%s\n' "$devices" | wc -l | tr -d ' ') -gt 1 ]]; then
      echo "More than one device. Set ADB_SERIAL to the Fire TV serial." >&2
      printf '%s\n' "$devices" >&2
      exit 1
    fi
    SERIAL=$devices
  fi
  adb_ get-state >/dev/null
}

prop() { adb_ shell getprop "$1" | tr -d '\r'; }

require_fireos5() {
  local sdk model
  sdk=$(prop ro.build.version.sdk)
  model=$(prop ro.product.model)
  if [[ $sdk != 22 ]]; then
    echo "Expected Fire OS 5 / SDK 22, found SDK $sdk on model $model." >&2
    echo "Stop: this plan is only for the 2nd-gen AFTT path." >&2
    exit 1
  fi
  echo "device: $model / SDK $sdk / $(prop ro.build.display.id)"
}
