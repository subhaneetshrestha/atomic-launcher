# atomic

A text-only, minimalist Android home launcher. The home screen is a short list of app names you choose; you decide where the list sits, how big the text is, which gestures open which apps or actions, and what the background shows. A fuzzy search finds any app in two or three keystrokes. Themes are plain JSON files you can share.

Status: early development (Phase 1, home skeleton). Not yet usable as a daily launcher.

## Goals

- Lightweight: release APK budget 2.5 MiB, no third-party UI or DI libraries, platform APIs only.
- Crash-free: a crashing home app silently loses its default status, so every system call is guarded.
- Honest about permissions: every privileged feature (notification access, accessibility, usage access) is off by default and explained before the system grant screen.
- GMS-free: the same build ships to Google Play, F-Droid and GitHub Releases. GitHub installs update through [Obtainium](https://github.com/ImranR98/Obtainium) or F-Droid; the app never updates itself.

## Build

Prerequisites: JDK 17 and an Android SDK with platform 37 (or 36) and build-tools 36.0.0. On Arch/CachyOS run `fish scripts/setup-toolchain.fish` once.

```sh
git config core.hooksPath .githooks   # once per clone: ktlint formats staged Kotlin on commit
./gradlew build                        # compile all modules, run JVM tests, ktlint, lint
./gradlew assembleRelease checkReleaseApkSize gmsGuard
./gradlew installDebug                 # on a connected device or emulator
./gradlew ktlintFormat                 # format everything
scripts/verify-home.sh emulator-5554 --locale   # Phase 1 acceptance on a booted emulator
```

Modules: `:app` (Android), `:core:theme` (settings/theme schema, pure Kotlin), `:core:search` (fuzzy matching, pure Kotlin). Application id: `io.github.subhaneetshrestha.atomic`.

## Documentation

- [`docs/design/`](docs/design/README.md) — the v1 build plan and subsystem designs.
- [`docs/research/`](docs/research/2026-09-05-android-launcher-capabilities.md) — what Android lets a launcher do, from primary sources.
- [`docs/decisions/`](docs/decisions/) — architecture decision records.
- [`docs/verification/`](docs/verification/) — acceptance runs per phase.

## Support

Crash reports and questions: shresthasubhaneet+atomic@gmail.com

## License

Apache License 2.0. See [LICENSE](LICENSE).
