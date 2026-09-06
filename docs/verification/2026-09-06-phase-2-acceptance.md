# Phase 2 acceptance — 2026-09-06

Scope: settings and theme persistence, the home screen driven by the settings document (theme colours, clock/date/battery block, night mode), the app long-press menu, the settings screens, the first-run setup, the crash recorder. Delivered test-first at the seven seams agreed before the first test was written.

## JVM tests (every push, `./gradlew test`)

| Seam | Tests | What they pin |
|---|---|---|
| `SettingsCodec` (`:core:theme`) | 11 | defaults round-trip; unknown keys ignored; corrupt input reported, never thrown; numbers clamped to the design ranges with path warnings; colour grammar (`#RRGGBB` → `#AARRGGBB`, `system_*` tokens, `auto` on text roles only); app lists validated, de-duplicated, capped at 16; newer theme refused, newer settings loaded best-effort and re-stamped; `minAppVersion` warning; four built-in themes valid with zero corrections and equal to `docs/themes/*.atomictheme` |
| `SettingsStore` core | 6 | defaults when nothing is stored; update visible and announced at once; one write per burst after the debounce; `flush` writes now; no-op updates; corrupt file → defaults and a kept copy; newer-app file → best-effort and copied aside before the first write-back |
| `SettingsEdits` | 5 | add (once, capped), remove, move with bounds, rename with the shared label rule (blank clears), hide toggle leaves the home list, materialise the alphabetical fallback before the first edit |
| `ThemeResolver` | 3 | hex → ARGB; night overrides; tokens via lookup with ink fallback; `auto` text by WCAG luminance (0.179) keeping the role's alpha |
| Projection + `HomeListModel` (`:app`) | 7 | component parsing incl. relative classes; label overrides (row label > rename > system); hidden apps skipped by the fallback but kept when placed |
| Info-line formatters | 3 | date from a theme pattern or the locale default, broken patterns fall back; battery template with clamped level and charging mark |
| `CrashRecorder` | 2 | report text with environment and stack; last report kept and clearable; installed handler chains to the previous one |

41 tests in total (with the wiring tests), all green; ktlint and lint clean apart from advisory warnings (target API not the newest, tools-only view constructors, KTX suggestions we decline by design).

## Emulator checks (`scripts/verify-settings.sh`, debug build, headless Pixel profile at 420 dpi)

| Check | API 26 | API 36 |
|---|---|---|
| Setup offered on first run; apps → theme → default steps; recognises we are already default; completion and theme choice persisted; not offered again | pass | pass |
| Clock, today's date and battery lines shown | pass | pass |
| Long-press on empty space opens Settings | pass | pass |
| Theme picker and appearance (night mode) persist their choice, screen kept after recreation | pass | pass |
| Home apps: removing a row pins the alphabetical six and drops to five explicit entries; re-adding restores six | pass | pass |
| App menu: rename shows and persists, reset restores the system label; hide persists and the row stays on the home list; unhide persists | pass | pass |
| Explicit entries survive a process restart | pass | pass |
| About shows the version and "no crash recorded" | pass | pass |
| No crashes during the run | pass | pass |

29 of 29 on both levels. Backup export and import go through the system document picker and are not covered by the harness: verified by driving the system document picker on API 36 by hand. Export wrote `atomic-settings-2026-09-06.json` to Downloads, byte-identical to the stored `settings.json`. The theme was then switched to Paper in Settings and the exported file imported back; the stored theme returned to Ink with no crash, so the import both reads and applies the document.

Release APK after Phase 2: 227,526 bytes unsigned (budget 2,621,440); `gmsGuard` clean (43 components).

## Phase 1 regression (`scripts/verify-home.sh`)

The Phase 1 harness was re-run after Phase 2 landed. It now clears the app first (Phase 2 persists edits, and its checks assume the alphabetical fallback) and skips the first-run setup when it appears.

| Level | Result | Notes |
|---|---|---|
| API 26 | 18 of 18 | first frame Displayed +397 ms, PSS 35 MB (debug); locale check needs API 33+ |
| API 36 | 21 of 21 | first frame Displayed +560 ms, PSS 39 MB (debug); per-app locale re-read confirmed |

Memory grew from about 23 MB and 39 MB in Phase 1 to 35 MB and 39 MB with the settings document, theme resolution, the info-line block and StrictMode's debug bookkeeping resident.

## What the run taught us

- **`recreate()` loses UI state unless it is saved.** Choosing a theme or night mode recreates the activity; the settings screen stack and the setup step and selection are now saved in the instance state, otherwise the user lands back on the menu (and the harness's next Back closed Settings).
- **Hiding from an unpinned home list changed the home screen.** With no explicit entries, the alphabetical fallback skips hidden apps, so "hide" removed the row. Hiding now pins the visible rows first; the same rule already protected add and remove.
- **Platform themes upper-case button text on old API levels**, so exact text matching failed on API 26; the setup buttons use sentence case and the harness matches case-insensitively.
- **`InputStream.readNBytes` is API 33+.** Lint's NewApi check caught it in the backup import; the read is now a bounded loop. The commit gate now checks the Gradle exit status rather than the presence of an APK, which had let a stale artifact through once.
- **The clock dominated the screen at 200 % font scale** (128 dp tall, pushing the sixth row off a 1920-pixel screen). Decorative text now follows the font-size setting only up to 1.3×; rows and the other lines scale fully. Sixteen rows plus three info lines will still not fit at 200 % on small screens; the overflow policy is an open item for the theme editor phase.
- **Main-thread disk work at startup and on flush is deliberate** (private directory creation, a few-kilobyte settings read and write) and is declared to StrictMode so real regressions stay visible in the harness's StrictMode check.
- **Back on the setup screen counts as Skip** (it marks setup done). The Phase 1 harness tripped over this when it cleared the app while it was still the default home: the system relaunched it, the setup appeared, and a Back press meant for a stale chooser completed it. The harness now clears the app only after moving the default away.
- **`--longpress KEYCODE_DEL` deletes one character on API 26**; the harness clears fields with one delete per possible character.
