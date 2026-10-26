#!/usr/bin/env bash
# Capture the TV screen into report/ (git-ignored).
set -euo pipefail
cd "$(dirname "$0")/.."
ADB=${ADB:-adb}
SERIAL=${ADB_SERIAL:-}
mkdir -p report
out="report/blackbox-$(date +%Y%m%d-%H%M%S).png"
if [[ -n $SERIAL ]]; then
  "$ADB" -s "$SERIAL" exec-out screencap -p > "$out"
else
  "$ADB" exec-out screencap -p > "$out"
fi
echo "$out"
