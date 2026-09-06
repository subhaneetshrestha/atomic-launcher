# Phase 1 acceptance — 2026-09-06

Build: commit `899350a` plus harness fixes; debug APK, `./gradlew build` green (JVM tests, lint, ktlint), release APK 116,694 bytes unsigned, `gmsGuard` clean (43 components).
Harness: `scripts/verify-home.sh <serial> [--locale]`, run against headless emulators (Pixel profile, 420 dpi, SwiftShader GPU).

| Check | API 26 (Android 8.0) | API 36 (Android 16) |
|---|---|---|
| Install debug APK | pass | pass |
| Banner shown while not default; hidden once default | pass | pass |
| Default home resolves to us (`set-home-activity` / `RoleManager`) | pass | pass |
| HOME intent brings the activity to the front (home stack) | pass | pass |
| Home key → `onNewIntent`, no recreation, one activity record, focused | pass | pass |
| Back ×3 keeps HomeActivity in front | pass | pass |
| Rotation, 200 % font scale: no crash; rows ≥ 48 dp | pass (min 64 dp) | pass (min 48 dp) |
| Package callbacks: installed app appears, uninstalled app vanishes | pass | pass |
| Default home restored after another launcher was installed | pass | pass |
| Tap on a row launches the app | pass | pass |
| No runtime hidden-API or StrictMode reports from our process | pass | pass |
| Labels re-read and re-sorted after a locale change | skipped (see below) | pass (per-app locale de-DE) |

Numbers (debug build, software GPU, informational):
- First frame (`ActivityTaskManager: Displayed`): 365–650 ms on API 26, 456–777 ms on API 36.
- Steady-state PSS: about 23 MB on API 26, 39–42 MB on API 36.

## What the run taught us

- **Starting the launcher with `am start -n` creates a second task.** The system launches HOME intents into the home stack; an explicit component start from adb lands in the ordinary app stack, so both instances coexist and `dumpsys activity top` lists the app stack first. Tests must launch through `am start -a MAIN -c HOME` and read the focused activity (`topResumedActivity` / `mResumedActivity`).
- **Below API 29, installing another HOME-capable app clears the default-home preference** and the next Home press shows the chooser. Expected platform behaviour, but any test that installs a launcher-like APK must re-apply the default afterwards. On API 29+ the role holder is unaffected.
- **Emulator `google_apis` images ignore the persisted system locale** (`system_locales` setting and `persist.sys.locale`, even across a reboot), so a system-wide language change cannot be automated there. The per-app locale command (`cmd locale set-app-locales`, API 33+) exercises the same path: the activity is recreated once, the repository re-snapshots, and labels come back German and re-sorted with German collation.
- **Launcher labels follow the caller's locale**, including a per-app locale of the launcher itself (answers open question 6 of the research note).
- `logcat -c` is unreliable on the API 26 image; count log lines before and after an action instead. `dex2oat` prints compile-time `hiddenapi` notes for AndroidX classes at install time; only runtime lines from the app's own pid matter.
- The `sdkmanager` script in command-line tools 23 is a shim that re-downloads the `android` CLI; drive installs through the CLI directly (`scripts/setup-toolchain.fish`).

## Still to verify on hardware

A system-wide language change on API 26–32, gesture navigation on a real device (Phase 3), and the items listed under "verify on a device early" in the build plan.
