# Design notes

Planning-time documents for v1, in reading order:

1. [v1 build plan](2026-09-06-v1-build-plan.md) — decisions, architecture, phases, acceptance criteria (the umbrella document).
2. [Build scaffold and home skeleton](2026-09-06-build-scaffold-and-home-skeleton.md) — repo layout, Gradle/CI, manifest, Phase 0–1 classes.
3. [Data model, actions, gestures, search](2026-09-06-data-model-actions-gestures-search.md) — settings/theme JSON schema, action registry, gesture state machine, fuzzy search, app menu, profile seam.
4. [System integration and release](2026-09-06-system-integration-and-release.md) — notification badges, accessibility service and fallbacks, background engine, home info lines, consent flows, release engineering.

5. [Visual identity and motion](2026-09-13-visual-identity-and-motion.md) — the typeface, the icon, the motion system, written after v1 shipped.
6. [Settings, for 1.1](2026-09-13-settings-1-1.md) — what is wrong with the settings screens and what 1.1 does about it.

7. [Asked for, not yet designed](2026-09-13-research-queue.md) — the queue: wallpaper sources, widgets, a Material settings menu, a scrollable home list, letter gestures and locked hidden apps.

Written after v1: items 5 to 7 describe the shipped code, the work planned on top of it, and what has
been asked for but not yet researched.

Research behind them: [`docs/research/`](../research/). Decisions: [`docs/decisions/`](../decisions/).

Note: these documents were written when the project was still called "justa" with the placeholder package `org.justa.launcher`. The product is now **atomic** with application id `io.github.subhaneetshrestha.atomic` (see [ADR 0002](../decisions/0002-product-identity-defaults-and-conventions.md)); identifiers inside the design and research documents are pre-rename and kept verbatim.
