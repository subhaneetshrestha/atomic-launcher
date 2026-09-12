# atomic

A text-only, minimalist Android home launcher. The home screen is a short list of app names you choose; you decide where the list sits, how big the text is, which gestures open which apps or actions, and what the background shows. A fuzzy search finds any app in two or three keystrokes. Themes are plain JSON files you can share.

Status: early development, six of nine phases done. Usable as a launcher, but shareable themes and
the accessibility-powered actions are still to come, and no signed build has been published yet.

## What works today

- A home list of one to sixteen app names you choose, renamed or hidden as you like, aligned and
  sized by the theme, with a clock, the date and the battery above it.
- Ten gestures (four swipes, four long swipes, double tap, long press) and the two info lines, each
  bound to any of 44 built-in actions, an app, a link or nothing.
- A fuzzy search over app names that opens the only match by itself, and a drawer of every app.
- Notification badges: a count beside a name, off until you turn them on, with what would be read
  explained before Android's grant screen.
- A background of your choosing: the theme's colour, a gradient, your own wallpaper showing through
  the window, or images fetched from an address you give it — a list, a feed, a JSON document, a
  Wallhaven search or a single picture — changed on a schedule, on wi-fi only unless you say
  otherwise, with the names going black or white to stay readable over whatever arrives.
- Four built-in themes, day/night, a skippable first-run setup, backup and restore through the
  system file picker, and an on-device crash report you can email.

## Goals

- Lightweight: release APK budget 2.5 MiB, no third-party UI or DI libraries, platform APIs only.
- Crash-free: a crashing home app silently loses its default status, so every system call is guarded.
- Honest about permissions: every privileged feature (notification access, accessibility, usage access) is off by default and explained before the system grant screen.
- GMS-free: one APK, published on GitHub Releases and built by GitHub Actions ([ADR 0004](docs/decisions/0004-github-only-distribution.md)). Installs update through [Obtainium](https://github.com/ImranR98/Obtainium), which watches releases; the app never updates itself.

## Build

Prerequisites: JDK 17 and an Android SDK with platform 37 (or 36) and build-tools 36.0.0. On Arch/CachyOS run `fish scripts/setup-toolchain.fish` once.

```sh
git config core.hooksPath .githooks   # once per clone: ktlint formats staged Kotlin on commit
./gradlew build                        # compile all modules, run JVM tests, ktlint, lint
./gradlew assembleRelease checkReleaseApkSize gmsGuard
./gradlew installDebug                 # on a connected device or emulator
./gradlew ktlintFormat                 # format everything
scripts/verify-home.sh emulator-5554 --locale   # per-phase acceptance on a booted emulator
scripts/verify-settings.sh emulator-5554        # and verify-gestures, -search, -badges, -background
```

Modules: `:app` (Android), `:core:theme` (settings/theme schema, pure Kotlin), `:core:search` (fuzzy matching, pure Kotlin), `:core:collections` (image collections and the rotation rules, pure Kotlin). Application id: `io.github.subhaneetshrestha.atomic`.

## Documentation

- [`docs/design/`](docs/design/README.md) — the v1 build plan and subsystem designs.
- [`docs/research/`](docs/research/2026-09-05-android-launcher-capabilities.md) — what Android lets a launcher do, from primary sources.
- [`docs/decisions/`](docs/decisions/) — architecture decision records.
- [`docs/verification/`](docs/verification/) — acceptance runs per phase.

## Support

Crash reports and questions: shresthasubhaneet+atomic@gmail.com

## License

Apache License 2.0. See [LICENSE](LICENSE).
