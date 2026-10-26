# Credits

Blackbox is a personal project built on the work of several open-source projects. This file records what was used, where it came from, and the licences that apply.

## Projects used

### dumbtv — Fire TV cleanup and dumbtv home

- **Source:** https://github.com/humblemuzzu/dumbtv
- **Author:** Muzzammil (`@humblemuzzu`) and the dumbtv contributors
- **Licence:** MIT for the code, scripts, docs, and launcher; the device catalog is CC BY-SA 4.0
- **What Blackbox used:** the safety and reversibility model (backup first; never write `/system`; never uninstall system apps; keep Amazon's launcher as a fallback), Fire OS privacy-setting ideas, the alarm/PendingIntent and transition-cover approach used by its HOME guard, DNS blocklist composition concepts (not enabled in this build), and the general idea of a small launcher that shows only apps.
- **What was not used:** dumbtv's debloat scripts, because they explicitly refuse Fire OS 5 / SDK < 25. Blackbox does not run them or claim their package-disable results.

### cleanyourtv — Android TV / Google TV cleaner and launcher

- **Source:** https://github.com/Gathin23/cleanyourtv
- **Author:** Gathin (`@Gathin23`) and contributors
- **Licence:** MIT
- **What Blackbox used:** visual and interaction inspiration for a calm TV launcher (large clock, restrained focus, app tiles), the idea of a small JSON/config-driven theme, and Fire TV package/device notes. The black-and-white visual system itself was written for Blackbox.
- **What was not used:** cleanyourtv's launcher APK/source directly. Its current launcher has `minSdk 26` (Android 8) and does not install on the 2016 AFTT. Blackbox is an original `minSdk 22` Java implementation.

### anuanurag2410/cleanyourtv — community fork

- **Source:** https://github.com/anuanurag2410/cleanyourtv
- **Author:** Anurag (`@anuanurag2410`) and contributors; original project by Gathin and contributors
- **Licence:** MIT (fork retains the original MIT licence)
- **What Blackbox used:** documentation structure, setup-guide style, and the fork's explicit attribution wording as a model for this file. No Fire TV code was taken from it.

### debloat-fire-stick-claude-code / firehome

- **Source:** https://github.com/whyashthakker/debloat-fire-stick-claude-code
- **Author:** Whyash Thakker (`@whyashthakker`) and contributors; Fire OS work built on cleanyourtv
- **Licence:** MIT (the repository carries the original cleanyourtv MIT notice, copyright Gathin)
- **What Blackbox used:** the tested Fire OS **5.2.9.5 / AFTT** capability findings (`pm disable-user` refused, `pm hide` only for user apps, accessibility dropped, `READ_LOGS` allowed) and the **logcat HomeWatcher** technique. Blackbox's `HomeWatcher.java` is an expanded, independently adapted implementation of that approach; the black transition cover and alarm launch were added by Blackbox.
- **What was not used:** the Fire OS 6/7 package lists, the browser remote, and the rest of the tooling.

## Evaluated but not included

These were reviewed for the project but are not part of this no-DNS build:

- **personalDNSfilter** by Ingo Zenz — GPL-2.0 — on-device DNS filtering. Not installed.
- **HaGeZi DNS blocklists** — GPL-3.0 — Amazon tracker lists. Not downloaded or bundled.
- **SmartTube** by Yuriy Liskov — MIT — ad-free YouTube client. Not installed; stock YouTube is kept, with ads.

If any of these are added later, their licences and notices must be added to `THIRD_PARTY_NOTICES.md`.

## Build tools

- Android SDK platform-tools, platform 22, and build-tools 34.0.0 (Apache-2.0) are used to build and install the APK. They are not redistributed.
- Blackbox uses Android's system fonts. No third-party font is bundled.

## Upstream versions consulted

These projects were consulted during development on **2026-10-26**. No local copies are vendored in this repository; the upstream links above and below are the source of truth:

| Project | Upstream |
|---|---|
| dumbtv | https://github.com/humblemuzzu/dumbtv |
| cleanyourtv | https://github.com/Gathin23/cleanyourtv (newer snapshot, 316-model README) |
| anuanurag2410/cleanyourtv | https://github.com/anuanurag2410/cleanyourtv (fork with the OnePlus Y1S Pro/macOS guide) |
| debloat-fire-stick-claude-code | https://github.com/whyashthakker/debloat-fire-stick-claude-code |

The Fire OS 5 reference project was consulted online; it is not copied into this repository.

## Blackbox

Blackbox source, UI, scripts, and documentation are by Anirudh, with AI-assisted implementation, and are released under the MIT licence. If you reuse substantial parts of this project, keep the credits above and the notices in `THIRD_PARTY_NOTICES.md`.
