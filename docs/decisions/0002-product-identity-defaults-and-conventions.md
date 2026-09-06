# ADR 0002 — Product identity, defaults and engineering conventions

Date: 2026-09-06. Status: accepted. Supersedes the product-name and package-name parts of [ADR 0001](0001-v1-scope-stack-distribution.md).

## Context

ADR 0001 fixed the stack, scope and distribution but left the product name, application id, out-of-box defaults and day-to-day engineering conventions implicit. A structured review surfaced every one of those assumptions and put each to the maintainer.

## Decisions

### Identity
- Product name **atomic**, lowercase in the app label and store listings. Theme files use the `.atomictheme` extension; share links use the `atomic://theme` scheme (a chooser on scheme collision is acceptable).
- Repository `subhaneetshrestha/atomic-launcher` on GitHub, public from the first push. Local folder `~/Projects/atomic-launcher`.
- Application id and Kotlin namespace **`io.github.subhaneetshrestha.atomic`**, final. The reverse of the maintainer's GitHub Pages host is a namespace the maintainer controls and F-Droid accepts, so no rename is needed before release.
- Support contact `shresthasubhaneet+atomic@gmail.com`, prefilled in shared crash reports and used as the store contact.
- v1 ships English strings only; resources stay translation-ready.

### Out-of-box experience
- Default theme `ink`. Text and list horizontally and vertically centred, 24 sp, six alphabetical apps until configured.
- Clock, date and battery lines are on by default, placed above the list. Screen time stays off until the user grants usage access.
- First run: a skippable setup screen (home apps, theme, set as default). Permission disclosures appear only when a privileged feature is switched on.
- Default gestures: swipe up = search, swipe down = notification shade (falls back to the app drawer if Play objects to the hidden-API path), swipe left = camera, swipe right = dialer, long press = launcher settings, double tap unbound.
- Search: auto-launch on a single match, keyboard shown automatically, no web-search fallback, hidden apps reachable by typing their exact name.

### Behaviour and policy
- On-device crash recorder: the last stack trace is written to a private file and can be shared as a prefilled email to the support contact. No telemetry of any kind.
- Android Auto Backup stays enabled for the settings file.
- No in-app update check; GitHub users are pointed at Obtainium or F-Droid.
- Wallhaven (keyless, safe-for-work forced) is a built-in image-collection source alongside the generic formats.
- Verification runs on emulators (API 26 and 36 at minimum); no physical device is required.

### Engineering conventions
- Commits are made at logical steps on `main` with conventional-commit messages, authored as Subhaneet Shrestha with the maintainer's Gmail address (repository-local git identity).
- ktlint with the official Kotlin style, enforced by `ktlintCheck` in CI, `ktlintFormat` locally and a versioned pre-commit hook in `.githooks/` (activated with `git config core.hooksPath .githooks`). detekt is not used in v1: its newest release (1.23.8, February 2025) is built against Kotlin 2.0.21 and predates Gradle 9.
- Automated tests: JVM tests for the pure-Kotlin cores plus a manual emulator matrix per phase; an instrumented smoke suite is added in Phase 9.
- Release signing: the maintainer generates the release key before any build is shared with anyone, and that key is uploaded to Play App Signing so Play, F-Droid and GitHub builds share one certificate.

### Phase adjustments
- Phase 2 gains the setup screen, the clock/date/battery lines and the crash recorder with the About screen. Screen time remains in Phase 8.
- Phase 0 gains ktlint and the pre-commit hook.
- A signing guide (`docs/release/signing.md`) is written right after Phase 1.

## Consequences

- Every identifier in the code base carries the final application id; nothing is left to rename before release.
- Defaults are decided once here rather than rediscovered per phase; changing one is a new ADR.
