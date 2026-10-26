#!/usr/bin/env bash
# Read-only device identification. Saves output under report/, changes nothing.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
need_device

STAMP=$(date +%Y%m%d-%H%M%S)
OUT=$ROOT/report/probe-$STAMP
mkdir -p "$OUT"
chmod 700 "$ROOT/report" "$OUT"

printf 'model        %s\n' "$(prop ro.product.model)"
printf 'device       %s\n' "$(prop ro.product.device)"
printf 'manufacturer %s\n' "$(prop ro.product.manufacturer)"
printf 'fire os      %s\n' "$(prop ro.build.display.id)"
printf 'android      %s (sdk %s)\n' "$(prop ro.build.version.release)" "$(prop ro.build.version.sdk)"
printf 'abi          %s\n' "$(prop ro.product.cpu.abi)"
printf 'adb secure   %s\n' "$(prop ro.adb.secure)"
printf 'serial       %s\n' "$SERIAL"
printf 'screen       '; adb_ shell wm size | tr -d '\r'
printf 'density      '; adb_ shell wm density | tr -d '\r'

adb_ shell getprop > "$OUT/props.txt"
adb_ shell pm list packages > "$OUT/packages-all.txt" 2>/dev/null || true
adb_ shell pm list packages -d > "$OUT/packages-disabled.txt" 2>/dev/null || true
adb_ shell pm list packages -3 > "$OUT/packages-thirdparty.txt" 2>/dev/null || true
adb_ shell pm list packages -s > "$OUT/packages-system.txt" 2>/dev/null || true
adb_ shell content query --uri content://settings/secure > "$OUT/settings-secure.txt" 2>/dev/null || true
adb_ shell content query --uri content://settings/global > "$OUT/settings-global.txt" 2>/dev/null || true
adb_ shell content query --uri content://settings/system > "$OUT/settings-system.txt" 2>/dev/null || true
adb_ shell dumpsys activity > "$OUT/activity.txt" 2>/dev/null || true
adb_ shell dumpsys window > "$OUT/window.txt" 2>/dev/null || true
adb_ shell dumpsys meminfo > "$OUT/meminfo.txt" 2>/dev/null || true
adb_ shell df > "$OUT/df.txt" 2>/dev/null || true
adb_ shell getprop > "$OUT/props.txt" 2>/dev/null || true

{
  printf 'Home process:\n'
  adb_ shell dumpsys activity 2>/dev/null | grep -E 'mHomeProcess|mFocusedActivity' | tr -d '\r' || true
  printf '\nDNS:\n'
  adb_ shell getprop 2>/dev/null | grep -E 'dns|net\.' | tr -d '\r' || true
} > "$OUT/launcher-and-dns.txt"

printf '\npackage counts: all %s, disabled %s, third-party %s\n' \
  "$(wc -l < "$OUT/packages-all.txt" | tr -d ' ')" \
  "$(wc -l < "$OUT/packages-disabled.txt" | tr -d ' ')" \
  "$(wc -l < "$OUT/packages-thirdparty.txt" | tr -d ' ')"
printf 'saved to %s\n' "$OUT"
