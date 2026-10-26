#!/usr/bin/env bash
# Save the state needed to undo the Blackbox work. Read-only on the TV.
set -euo pipefail
source "$(dirname "${BASH_SOURCE[0]}")/lib.sh"
need_device

STAMP=$(date +%Y%m%d-%H%M%S)
OUT=$ROOT/report/backup-$STAMP
mkdir -p "$OUT"
chmod 700 "$ROOT/report" "$OUT"

{
  printf 'Blackbox backup\n'
  printf 'created %s\n' "$(date)"
  printf 'serial %s\n' "$SERIAL"
  printf 'model %s\n' "$(prop ro.product.model)"
  printf 'device %s\n' "$(prop ro.product.device)"
  printf 'firmware %s\n' "$(prop ro.build.display.id)"
  printf 'android %s (sdk %s)\n' "$(prop ro.build.version.release)" "$(prop ro.build.version.sdk)"
} > "$OUT/README.txt"

adb_ shell getprop > "$OUT/props.txt"
adb_ shell pm list packages > "$OUT/packages-all.txt"
adb_ shell pm list packages -d > "$OUT/packages-disabled.txt"
adb_ shell pm list packages -3 > "$OUT/packages-thirdparty.txt"
adb_ shell pm list packages -s > "$OUT/packages-system.txt"
# Fire OS 5's `settings` command has no list subcommand; query the provider instead.
adb_ shell content query --uri content://settings/secure > "$OUT/settings-secure.txt" 2>/dev/null || true
adb_ shell content query --uri content://settings/global > "$OUT/settings-global.txt" 2>/dev/null || true
adb_ shell content query --uri content://settings/system > "$OUT/settings-system.txt" 2>/dev/null || true
adb_ shell dumpsys activity > "$OUT/activity.txt"
adb_ shell dumpsys window > "$OUT/window.txt"
adb_ shell dumpsys meminfo > "$OUT/meminfo.txt"
adb_ shell df > "$OUT/df.txt"
adb_ shell wm size > "$OUT/wm-size.txt"
adb_ shell wm density > "$OUT/wm-density.txt"

# Save screenshots for visual comparison; they are private and git-ignored.
adb_ exec-out screencap -p > "$OUT/before.png" 2>/dev/null || rm -f "$OUT/before.png"

printf 'saved to %s\n' "$OUT"
printf 'keep this folder private; it may contain device identifiers.\n'
