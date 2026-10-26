# Troubleshooting

## ADB

**`unauthorized`** — accept **Allow USB debugging?** on the TV and tick **Always allow from this computer**.

**`no device`** — confirm the stick's IP under **Settings → System → About → Network**, confirm both devices are on the same Wi-Fi, then:

```sh
adb kill-server
adb start-server
adb connect <stick-ip>:5555
adb devices
```

**More than one device** — set `ADB_SERIAL=<stick-ip>:5555` before running the project scripts.

If macOS Local Network privacy blocks `adb`, allow the terminal app under **System Settings → Privacy & Security → Local Network**, then restart the adb server.

## HOME still opens Amazon

First, after a reboot: Fire OS 5 can take a minute or two to deliver the boot broadcast. Blackbox's boot receiver starts the watcher and puts the home screen up when it arrives. Press **OK** on the remote if the “cannot detect your remote” dialog is showing, then HOME, or open **Blackbox** once from the Fire TV Apps list.

If it still happens after that, check the watcher:

```sh
adb shell dumpsys activity services dev.anirudh.blackbox | grep -E 'HomeWatcher|isForeground'
adb logcat -d -s BlackboxWatch:I '*:S'
```

If the service is missing, restart it:

```sh
adb shell am startservice -n dev.anirudh.blackbox/.HomeWatcher
```

Then press HOME. If it still fails, rerun:

```sh
./launcher/install.sh
```

The install script re-grants `READ_LOGS` and the draw-over app-op and starts the service.

## The BLACKBOX transition is slow

Expected on this stick. The cover is shown immediately; the activity can take several seconds to draw while Fire OS and Amazon's services are busy. If the cover stays for more than 10 seconds, the timeout removes it by design. Check `adb logcat -s BlackboxWatch` and the current focus:

```sh
adb shell dumpsys window | grep mCurrentFocus
```

## “We cannot detect your remote”

Press **OK** on the remote, then HOME. This is a Fire OS 5 post-reboot dialog; Blackbox cannot dismiss it without root.

If the remote is unresponsive, change its batteries, or use the Fire TV phone app to press OK.

## An app is missing

Open **All Apps**. If it is there, it is only hidden from the home grid. Use **Settings → Hide apps** to toggle it back on, or edit `hidden=` in `home.conf`.

If your edited `home.conf` did not apply, put it at `/sdcard/Download/blackbox-home.conf` and use **Settings → Reload config from Download**. ADB cannot write into the app-owned `Android/data/dev.anirudh.blackbox/files` directory on this firmware.

If a non-Amazon app is missing from All Apps, confirm it is installed:

```sh
adb shell pm list packages | grep <package>
```

## Amazon Home

Press **Amazon Home** in Blackbox. It pauses the watcher for 10 minutes and opens Amazon's launcher. To return before then, open Blackbox from the Fire TV app list, or wait for the pause to expire and press HOME.

## Settings reset after a reboot

Fire OS sometimes rewrites the Alexa wake-word key during boot. Re-run:

```sh
./scripts/privacy.sh
```

The other privacy settings and animation scales are generally retained. Verify with:

```sh
./scripts/verify.sh
```

## Restore

```sh
./scripts/restore.sh
```

This restores the recorded privacy settings, deletes the animation-scale and alarm overrides, uninstalls Blackbox, removes its external config, and opens Amazon's launcher. Reboot if the screen still shows BLACKBOX.

If you need to restore a single setting, use `adb shell settings get secure <key>` to inspect it and `adb shell settings put secure <key> <value>` to change it. The originals from this stick are in `report/backup-*/settings-before-blackbox.txt`.

## Emergency access

If Blackbox misbehaves and the remote still works, open **All Apps → Settings → Fire TV Settings**, or press **Amazon Home** for 10 minutes. If the screen is black, wait 10 seconds for the cover timeout. If the app is stuck, uninstall it over ADB:

```sh
adb uninstall dev.anirudh.blackbox
adb shell input keyevent KEYCODE_HOME
```

## Turn ADB off

When done:

**Settings → System → Developer Options → ADB debugging off**

and revoke debugging authorizations if available. ADB over TCP is full control of the stick; do not leave it enabled on an untrusted network.
