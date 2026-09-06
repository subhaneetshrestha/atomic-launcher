# ADR 0001 — v1 scope, stack and distribution

Date: 2026-09-06. Status: accepted; superseded in part by [ADR 0002](0002-product-identity-defaults-and-conventions.md) (product name, application id, package placeholder).

## Context

justa-launcher is a text-only, minimalist Android home launcher: a curated list of app names, user-controlled position/alignment/text size/font, gesture bindings, fuzzy app search, a notification count beside app names, a rotating home background from a URL image collection, and shareable community themes. Lightweight is a hard requirement. Before choosing a stack, three primary-source research reports established what Android lets a launcher do and at what permission, policy and weight cost (see [`docs/research/`](../research/2026-09-05-android-launcher-capabilities.md)).

## Decisions

1. **Stack**: Kotlin + Android Views, single Activity, minSdk 26, targetSdk 36, compileSdk 37 (36 if the platform is unavailable). AndroidX limited to `activity` and `core`; kotlinx-serialization-json for settings and themes; R8 full mode with resource shrinking; no native code; no coroutines dependency in v1 (a threading seam is kept). Release APK budget: 2.5 MiB.
2. **v1 feature scope**: core (home list, gestures, fuzzy search, notification badges, in-window backgrounds, JSON themes) plus accessibility-powered gestures (optional service in its own process, off by default), home info lines (clock, date, battery, screen time) and app shortcuts with launcher-local rename and hide. Work profile and private space are excluded from v1; app identity is nonetheless keyed by (component, user serial) so they can be added without a schema break. `ACCESS_HIDDEN_PROFILES` is not declared.
3. **Background**: image collections are drawn inside the launcher window. No `WallpaperManager` writes and no `SET_WALLPAPER` permission in v1.
4. **Distribution**: Google Play (AAB) and F-Droid/GitHub (APK), GMS-free. No product flavors: the channels differ only in artifact type and signing. Consequences: Play Accessibility API declaration with in-app prominent disclosure, an "Allow restricted settings" help flow for sideloaded installs, reproducible-build hygiene, and no `QUERY_ALL_PACKAGES` (scoped `<queries>` instead).
5. **License and provenance**: Apache-2.0. No code is copied from GPL launchers (Olauncher, KISS, mLauncher, Kvaesitso, Lawnchair); algorithms are re-implemented from cited descriptions.

Follow-up decisions taken the same day:
- **Package name**: `org.justa.launcher` is a placeholder. It must be replaced with a reverse-domain id the maintainer controls before the first Play upload (immutable on Play afterwards).
- **Toolchain**: installed with `paru` (official repos and AUR) into `/opt/android-sdk`; `sdkmanager` only for system images and build-tools 36.0.0. See `scripts/setup-toolchain.fish`.
- **Delivery**: implementation proceeds in phases (toolchain, scaffold, home skeleton, then settings, gestures, search, badges, background, themes, system integration, release), each phase reviewed before the next starts.

## Consequences

- Every subsystem uses platform APIs with zero third-party dependencies beyond kotlinx-serialization; pure-Kotlin cores (`:core:theme`, `:core:search`, later `:core:collections`) carry the JVM tests.
- Crash-freedom is a product requirement: a crashing third-party home app silently loses its default status.
- Privileged behaviour (notification access, accessibility, usage access, device admin) is always behind a disclosure screen with affirmative consent, and every such feature degrades gracefully when the grant is missing.
