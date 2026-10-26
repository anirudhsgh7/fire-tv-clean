# Design and customization

Blackbox is deliberately plain Java and Android views: no AndroidX, Compose, Kotlin, web view, or downloaded fonts. That keeps the APK around 34 KB and leaves the 1 GB stick's memory for video apps.

## Layout

At 1920×1080 and 320 dpi the TV reports a **960×540 dp** screen. Blackbox uses:

- 48 dp header inset; 8 dp left / 40 dp right grid inset; 12 dp tile gap.
- A large clock, a smaller date, a divider, the app grid, and a bottom action bar.
- Five columns by default. The same responsive grid is used for the home and **All Apps** screens; **Hide Apps** uses a D-pad-friendly list.
- 8 dp card corners, a 1 dp normal border, a 2 dp white focus border, and a small focus scale.
- Pure `#000000` background and tile surfaces; no wallpaper, blur, or live effects.

When you change the layout, check it on the TV with `./launcher/screenshot.sh`. The TV's overscan settings can hide the outermost pixels; the deliberately small left grid inset may need adjustment for displays with significant overscan.

## Design tokens

All base colors and sizes are in `launcher/src/dev/anirudh/blackbox/Theme.java`:

| Token | Current | Meaning |
|---|---|---|
| `BG` | `#000000` | whole-screen background |
| `CARD` | `#000000` | app tile |
| `CARD_FOCUS` | `#000000` | focused app tile; focus is shown by the white border |
| `BORDER` | `#202020` | unfocused tile / pill border |
| `BORDER_FOCUS` | `#FFFFFF` | focused tile border |
| `TEXT` | `#FFFFFF` | focused and primary text |
| `TEXT_DIM` | `#8A8A8A` | unfocused labels, date |
| `TEXT_FAINT` | `#5C5C5C` | helper text |
| `WHITE` / `BLACK` | `#FFFFFF` / `#000000` | focus fills |

Sizes live in the layout code and `Theme.dp`:

- clock 64 sp `sans-serif-medium`
- date 16 sp `sans-serif` (regular)
- app labels 12 sp `sans-serif`
- settings rows 16 sp, 50 dp high
- bottom pills 16 sp, 22 dp corner radius
- app card radius 8 dp, gap 12 dp

To try a theme, change the constants and rebuild. For example, a warm amber accent can use `BORDER_FOCUS = 0xFFFFB86B` and `TEXT = 0xFFFFF4E6`; a cool cyan theme can use `0xFF85E7FF`. Keep the background dark on this stick; large bright surfaces cause more GPU work.

## Where things live

| File | Controls |
|---|---|
| `Theme.java` | colors, dp helper, card/pill/row shapes |
| `HomeActivity.java` | clock header, grid, bottom bar, All Apps, Settings, Hide Apps, About |
| `AppTile.java` | per-app card, artwork, label, focus animation |
| `AppCatalog.java` | which launchable apps are found and how artwork is chosen |
| `Config.java` | `home.conf` parsing/saving |
| `HomeWatcher.java` | HOME detection, transition cover, alarm launch |
| `BootReceiver.java` | starts the watcher at boot |
| `launcher/res/drawable/` | app icon and TV banner |
| `launcher/home.conf.example` | the default curated grid and hidden list |

## home.conf

The app reads a plain text file from its own external files directory:

```text
/sdcard/Android/data/dev.anirudh.blackbox/files/home.conf
```

Fire OS gives that directory to the app, so `adb` cannot write into it directly. To edit on a computer, copy `launcher/home.conf.example` to `/sdcard/Download/blackbox-home.conf`, edit it there, then choose **Settings → Reload config from Download** in Blackbox. The app imports it and writes its own copy.

Example:

```ini
columns=5
clock24=1
show_date=1
animations=1
hidden=com.amazon.hedwig,com.amazon.bueller.music
order=com.amazon.firetv.youtube,com.netflix.ninja,in.startv.hotstar
```

| Key | Values |
|---|---|
| `columns` | 4, 5, or 6 |
| `clock24` | `1` = 24-hour, `0` = 12-hour |
| `show_date` | `1` or `0` |
| `animations` | `1` or `0` |
| `hidden` | comma-separated package names, no spaces |
| `order` | comma-separated package names; first is leftmost |

The app reloads the file when it returns to the foreground. Hidden apps are **not** uninstalled or disabled; they simply do not get a home tile. `All Apps` still lists them, and **Settings → Hide apps** toggles them. **Settings → Reload config from Download** imports the Download copy; without it, the app uses the config in its private folder or creates the curated first-run defaults.

## Build and screenshot loop

```sh
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools

./launcher/build.sh
ADB_SERIAL=<stick-ip>:5555 ./launcher/install.sh
ADB_SERIAL=<stick-ip>:5555 ./launcher/screenshot.sh
```

`install.sh` reuses the signing key in `launcher/blackbox.keystore` (git-ignored). Keep that file; a future update must be signed with the same key to install over the old one. If you lose it, uninstall and reinstall, which loses the app's config.

## Performance notes

- Do not add AndroidX/RecyclerView/Compose for this device; the system's own launcher already uses about 96 MB.
- Avoid live/video wallpapers and large full-screen bitmaps.
- Keep focus effects to a simple scale/border; the stick's GPU is a Mali-450.
- The transition cover is a full-screen window; it is only visible during HOME transitions and is removed on Blackbox resume.
- Loading app banners/icons is the slowest part of a cold start. The current code loads only launchable apps and defers the work until after the first frame. If you add more artwork, keep an eye on cold-start time.

## Transition cover

`HomeWatcher` adds a non-focusable, non-touchable black window with the wordmark and a spinner. It is shown when an Amazon HOME line is seen and hidden from `HomeActivity.onResume()`. If the activity fails, a 10-second timeout removes the cover.

The cover requires the draw-over-other-apps app-op. `launcher/install.sh` grants it with `appops set`. Without it, Blackbox still works but Amazon's home may be visible during the transition.

## Adding a new screen

1. Add a method in `HomeActivity` that calls `newOverlay()` and fills a `LinearLayout`.
2. Use `row()` for focusable list entries or `pill()` for the bottom bar.
3. Use `AppTile` for app cards and `fillGrid()` for a grid.
4. Call `closeOverlay()` from Back/Close actions.
5. Build and screenshot on the TV; check D-pad focus, not just the visual.

Keep the code free of network APIs. The manifest deliberately has no `INTERNET` permission.
