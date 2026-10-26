# AGENTS.md

## What this repo is

- **Blackbox** is a sideloaded home-screen workaround for a **Fire TV Stick 2nd gen (AFTT)**, tested on **Fire OS 5.2.9.5 / Android 5.1 / SDK 22**.
- It is **not** a Fire OS replacement or a system debloater. Amazon's launcher stays installed and remains the system HOME.
- The app is plain Java in `launcher/`; scripts are in `scripts/`; the no-DNS design and device limits are documented in `README.md`, `docs/DESIGN.md`, and `TROUBLESHOOTING.md`.
- The upstream projects are credited by link in `CREDITS.md`; no third-party repositories are vendored here. Do not re-add or run their device scripts.

## Non-negotiable device constraints

- Never root, write `/system`, factory reset, or disable/uninstall system packages. On this firmware `pm disable-user` is refused and `pm hide` does not work for Amazon system apps.
- Do not claim bloatware is removed or storage freed. Blackbox only hides tiles; the apps remain installed and may still run.
- Do not add DNS, router changes, OTA blocking, telemetry blocking, or SmartTube unless the owner explicitly asks. The current build has **no DNS filter**, keeps stock YouTube (with ads), and does not block OTA.
- Do not add `CATEGORY_HOME` to the manifest. HOME is taken over by a logcat watcher plus a draw-over-apps cover; declaring HOME creates a chooser and breaks the design.
- Keep `dev.anirudh.blackbox`, `minSdk 22`, `targetSdk 22`, and **no `INTERNET` permission**.
- Preserve `READ_LOGS`, `SYSTEM_ALERT_WINDOW`, `READ_EXTERNAL_STORAGE`, and `RECEIVE_BOOT_COMPLETED`; their install grants are required.
- Preserve `launcher/blackbox.keystore` (git-ignored). Updates must be signed with the same key.

## Commands

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

./launcher/build.sh                              # aapt2/javac/d8/apksigner, no Gradle
ADB_SERIAL=<ip>:5555 ./launcher/install.sh       # build, install, grant, start
ADB_SERIAL=<ip>:5555 ./scripts/verify.sh         # device health/settings check
ADB_SERIAL=<ip>:5555 ./scripts/screenshot.sh     # writes report/*.png, git-ignored
```

- Set `ADB_SERIAL` whenever more than one adb device is possible; this firmware accepts one ADB connection at a time.
- `verify.sh` retries the foreground service for ~5 s. A failure immediately after install usually means it ran too early.
- There is no automated test suite. The verification is `bash -n scripts/*.sh launcher/*.sh`, `aapt2 dump permissions launcher/build/blackbox.apk`, and `scripts/verify.sh` against the TV.
- `launcher/install.sh --remove` only removes the app. Full undo is `./scripts/restore.sh` (prompts; restores settings and animation scales, deletes the alarm override, uninstalls the app).
- Run `./scripts/backup.sh` before changing device state. `report/` is private, git-ignored, and may contain identifiers.

## Architecture and quirks

- `HomeWatcher.java`: foreground service follows `ActivityManager` HOME lines with `READ_LOGS`, launches via `AlarmManager`, and shows a non-focusable black transition cover. `BootReceiver` starts the watcher and directly opens `HomeActivity` on `BOOT_COMPLETED`; `USER_PRESENT` only starts the watcher.
- Boot on this stick can take **1–2 minutes** because Fire OS serializes boot broadcasts. A “cannot detect your remote” dialog may appear; press OK. Do not try to “fix” either by disabling Amazon services.
- `HomeActivity.shown()` hides the cover and cancels the delayed boot launch. Keep that behavior when editing the watcher.
- `install.sh` sets `alarm_manager_constants min_futurity=0`; `restore.sh` deletes it. Removing it without device testing can reintroduce the multi-second Amazon-home flash.
- `Config.java` writes curated `DEFAULT_HIDDEN`/`DEFAULT_ORDER` when `home.conf` is missing. If you change the curated grid, update all three: `Config.java`, `launcher/home.conf.example`, and `lists/fireos5/hidden-apps.txt`.
- Fire OS makes the app-owned `/sdcard/Android/data/dev.anirudh.blackbox/files/` unwritable from `adb shell`. To edit config over ADB, push to `/sdcard/Download/blackbox-home.conf`, then use **Settings → Reload config from Download**. Do not add direct-push instructions.
- The app is deliberately dependency-free Android views. Keep API 22 compatibility; no AndroidX, Compose, Kotlin, downloaded fonts, or live wallpapers.
- Reference/credits files are intentional: keep `README.md`, `CREDITS.md`, `THIRD_PARTY_NOTICES.md`, and the MIT notices accurate when changing code or attribution.
- Only the updated home-screen reference in `docs/screenshots/home.png` is committed; do not add screenshots containing account names, device IDs, or other personal data.
