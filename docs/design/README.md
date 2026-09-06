# Design notes

Planning-time documents for v1, in reading order:

1. [v1 build plan](2026-09-06-v1-build-plan.md) — decisions, architecture, phases, acceptance criteria (the umbrella document).
2. [Build scaffold and home skeleton](2026-09-06-build-scaffold-and-home-skeleton.md) — repo layout, Gradle/CI, manifest, Phase 0–1 classes.
3. [Data model, actions, gestures, search](2026-09-06-data-model-actions-gestures-search.md) — settings/theme JSON schema, action registry, gesture state machine, fuzzy search, app menu, profile seam.
4. [System integration and release](2026-09-06-system-integration-and-release.md) — notification badges, accessibility service and fallbacks, background engine, home info lines, consent flows, release engineering.

Research behind them: [`docs/research/`](../research/). Decisions: [`docs/decisions/`](../decisions/).

Note: these documents were written when the project was still called "justa" with the placeholder package `org.justa.launcher`. The product is now **atomic** with application id `io.github.subhaneetshrestha.atomic` (see [ADR 0002](../decisions/0002-product-identity-defaults-and-conventions.md)); identifiers inside the design and research documents are pre-rename and kept verbatim.
