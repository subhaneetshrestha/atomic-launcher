> Provenance: planning-time design written by a Plan agent on 2026-09-06 from the research note and the scoping decisions in ADR 0001, before any code existed. Copied verbatim from the agent transcript. Where the code and this document disagree, the code and its tests are authoritative; update this file or add an ADR.

# justa-launcher — Data model & interaction architecture (Persistence, Actions, Gestures, Search, App menu, Profile seam) + Phases 2/3/4/7

Citations: `[plan §x]` = /home/hyzii/.claude/plans/i-want-to-create-mighty-swan.md; `[platform Bx]` = platform-fundamentals report; `[gestures Ax]` = gestures/actions report; `[tech §x]` = tech-stack/theming text report. Package root placeholder: `app.justa.launcher` (`PKG`); Kotlin sources under `src/main/kotlin`. Gradle paths `:core:theme` → `/home/hyzii/Projects/justa-launcher/core/theme`, `:core:search` → `.../core/search`, `:app` → `.../app`.

Guiding rule used throughout: **theme = how it looks (shareable); settings = what is shown and what it does (device-local).**

## 1. Persistence (`:core:theme`, pure Kotlin + kotlinx-serialization-json)

### 1.1 One file, two types (recommendation)
One on-disk document `filesDir/settings.json` whose root type `Settings` embeds a `theme: Theme` field, where `Theme` is *exactly* the shareable `.justatheme` document type. Justification: one atomic write, one load, one migration entry point (as the previous plan wanted, `[plan Part 3]`), but the share boundary is a **type boundary**, so exporting a theme can never leak the home list, hidden apps, renames or font URIs. Kvaesitso (`ThemeBundle` distinct from prefs) and mLauncher (full prefs dump vs colour-only theme) both separate the two `[tech §A.6, §A.3]`. Refinement of the previous plan: "the settings document doubles as backup/export" holds for *backup*; a *theme* export serialises only `settings.theme`.

Files:
- `core/theme/src/main/kotlin/PKG/core/theme/Settings.kt` — `Settings` root + device-local sub-objects (all `@Serializable`, all fields defaulted).
- `.../Theme.kt` — `Theme` and its sub-objects; `ColorValue` typealias + grammar.
- `.../Action.kt` — sealed `Action` (see §2) and its tolerant wrapper serializer.
- `.../AppRef.kt` — `AppRef(component: String, user: Long = 0)` (flattened `ComponentName` + user serial `[platform B3]` "do not store the UserHandle").
- `.../JsonConfig.kt` — `ThemeJson`: `ignoreUnknownKeys=true, coerceInputValues=true, explicitNulls=false, encodeDefaults=true, classDiscriminator="type"`, plus experimental `allowTrailingComma`/`allowComments` for hand-written themes; `prettyPrint` only for export. Not `isLenient` (keeps colours/enums strictly quoted).
- `.../Validation.kt` — `SettingsLint` (JsonElement pass) + `Sanitizer` (typed clamp pass) → `Validated(value, warnings: List<Warning>)`.
- `.../Migrations.kt` — `SettingsMigrations`, `ThemeMigrations`: `(JsonObject, from: Int) -> JsonObject` step chains on the JSON tree, never on typed classes.
- `.../BuiltinThemes.kt` — Kotlin constants (see 1.6). `.../Defaults.kt` — `Settings()` defaults.
- `.../SettingsCodec.kt` — `decodeSettings(text) / encodeSettings`, `decodeTheme(text) / encodeTheme`; runs lint → migrate → typed decode → sanitize; never throws (returns `DecodeResult.Corrupt(reason)`).

### 1.2 Field catalogue
`Settings` (device-local root, `schema = 1`):

| Field | Shape | Notes |
|---|---|---|
| `schema` | Int | settings document version |
| `app` | `{lastVersionCode, firstRunAt?, onboardingDone}` | downgrade detection, first run |
| `theme` | `Theme` | the shareable sub-document (below) |
| `home.entries` | `[HomeEntry{component, user=0, label?}]` | ordered list, max 16, deduped by (component,user). `label` is a per-row override, **schema-present but no UI in v1** (avoids a later break); resolution order fixed now: `entry.label ?: renames[app] ?: LauncherActivityInfo.getLabel()` |
| `hidden` | `[AppRef]` | excluded from drawer & search; still bindable to gestures |
| `renames` | `[{component, user, label}]` | global launcher-local rename `[platform B6]`; ≤ 500 |
| `gestures` | `{bindings: {GestureId: Action}, haptics=true, edgeExclusion: none\|both = none}` | GestureId = `swipe_up/down/left/right`, `long_swipe_*` (4), `double_tap`, `long_press` |
| `homeInfo` | `{position: top\|bottom, clock, date, battery, screenTime}`, each `InfoLine{enabled, format?, onTap: Action?, onLongPress: Action?}` | formats: `clock` = TextClock 12h/24h pattern pair (`{h12, h24}`), `date` = skeleton for `DateFormat.getBestDateTimePattern` `[gestures D6]`; the home-info planner reads this, I only define it |
| `search` | `{autoLaunchSingle=true, autoShowKeyboard=true, webSearchFallback=false}` | |
| `notifications` | `{enabled=false, includeOngoing=false, perAppDisabled: [AppRef]}` | NLS planner's input; grant state is never persisted (queried) |
| `backgroundPolicy` | `{unmeteredOnly=true, skipWhenBatteryLow=true}` | device constraints `[plan §2.5]`; Data-Saver respect is unconditional, not a setting |
| `features` | `{accessibilityService=false, deviceAdminLock=false, shadeReflection=true, screenTime=false}` | user *intent*; actual grant checked live |
| `consents` | `{notificationAccessAt?, accessibilityAt?, usageAccessAt?}` (epoch ms) | disclosure-accepted records for Play policy `[plan §1.4, §1.6]`; cleared on backup import |
| `appearance.nightMode` | `system\|light\|dark` | applied via `UiModeManager.setApplicationNightMode` (31+) `[gestures D4]` |
| `userFont` | `{uri, name}?` | SAF-picked font; the persisted URI permission is system state re-checked at load (`persistedUriPermissions`), not a field |

`Theme` (shareable, `schema = 1`, `minAppVersion`):

| Field | Shape / allowed values |
|---|---|
| `meta` | `{id: slug, name, author?, license?, homepage?, description?}` |
| `colors` | roles `background, text, textSecondary, accent, badgeBackground, badgeText, shadow, outline, scrim`; each a `ColorValue` string: `#AARRGGBB`, `#RRGGBB` (normalised to 8 digits on export), `@android:color/system_*` token, or `auto` (text roles only: black/white from the background's `WallpaperColors` `[gestures D3]`) |
| `darkColors` | optional partial `colors` override used when night mode is active (theme may be night-agnostic) |
| `typography` | `family` ∈ {sans-serif, sans-serif-condensed, serif, monospace, casual, cursive, custom} `[gestures D1]`; `weight` 100–900; `italic`; `letterSpacingEm`; `lineHeightMult` (applied as sp = sizeSp×mult, so non-linear scaling stays correct `[gestures D2]`); `case` none\|upper\|lower; `fontVariation?`; `customFontHint?` (name of the font the author used); `sizes {homeSp, drawerSp, clockSp, infoSp, badgeSp}` |
| `layout` | `hAlign start\|center\|end`, `vAlign top\|center\|bottom`, `paddingDp {h, v}`, `rowGapDp`, `infoAlign?` (row min-height 48 dp is enforced, not a field) |
| `legibility` | `mode none\|shadow\|outline\|scrim`, `shadow {radiusDp, dxDp, dyDp}`, `outlineWidthDp`, `scrimHeightFraction` |
| `badge` | `style circle\|dot\|number`, `position start\|end` (count cap 999 and filters are the NLS planner's, `[plan §1.4]`) |
| `background` | `mode wallpaper\|color\|gradient\|collection`; `color`; `gradient {angleDeg, stops: [ColorValue] (2–8)}`; `collection {url, format auto\|text\|json\|rss\|atom\|direct\|wallhaven, intervalMinutes ≥15, fit cover\|contain}` `[gestures C5]`; `dimAlpha` 0–1 |
| `systemBars` | `statusIcons auto\|light\|dark`, `navIcons auto\|light\|dark`, `hideStatusBar=false` (explicit opt-in per `[plan §2.7]`) |

Token resolution (`ThemeResolver`, `:core:theme`, pure): fixed allowlist map `token name → role` validated at lint time; in `:app` the map yields `android.R.color.*` ids read with `context.getColor()` (API 31+). On API 26–30 a token resolves to the **default built-in theme's colour for that role** — deterministic, no per-token fallback syntax needed (strings stay strings). `auto` resolves via `BackgroundState.supportsDarkText` supplied by the background engine (see interfaces, §8); if unknown → white.

Example theme (what a community author writes):
```json
{ "schema": 1, "minAppVersion": 1,
  "meta": { "id": "paper", "name": "Paper", "author": "…", "license": "CC0-1.0" },
  "colors": { "background": "#FFF6F1E7", "text": "#FF1B1B1B", "textSecondary": "#991B1B1B",
              "accent": "@android:color/system_accent1_600",
              "badgeBackground": "#FF1B1B1B", "badgeText": "#FFF6F1E7" },
  "typography": { "family": "serif", "weight": 400, "sizes": { "homeSp": 26, "clockSp": 48 },
                  "lineHeightMult": 1.35, "case": "lower" },
  "layout": { "hAlign": "start", "vAlign": "bottom", "paddingDp": { "h": 28, "v": 56 }, "rowGapDp": 6 },
  "legibility": { "mode": "none" },
  "badge": { "style": "number", "position": "end" },
  "background": { "mode": "color", "color": "#FFF6F1E7" },
  "systemBars": { "statusIcons": "auto", "navIcons": "auto" } }
```

### 1.3 Decoding, versioning, migration, validation
- Three-pass load: (1) `SettingsLint` on the `JsonElement` tree: check `schema` (if `> SUPPORTED` → for a *theme import* refuse with "made with a newer version"; for the *on-disk settings* load best-effort and copy the file to `settings.json.v<N>.bak` before the first write-back); check `minAppVersion > versionCode` → warn, allow; enum/colour/regex/URL lint producing `Warning(path, message)` for the import UI (needed because `coerceInputValues` silently defaults unknown enums). (2) `migrate(from → SUPPORTED)` step chain; the settings chain calls the theme chain for the nested object. (3) typed decode with `ThemeJson`, then `Sanitizer` clamps numerics and dedupes lists.
- `schema` bumps only for breaking changes (renames/semantics). Additive fields never bump. Every migration step ships with a fixture pair under `core/theme/src/test/resources/fixtures/`.
- Validation rules (clamp to bound unless noted): colour `^#([0-9A-Fa-f]{6}|[0-9A-Fa-f]{8})$` or allowlisted token or `auto` (else default for that role + warning); `homeSp` 10–64, `drawerSp` 10–40, `clockSp` 12–140, `infoSp` 8–40, `badgeSp` 8–24; `weight` rounded to nearest 100 in 100–900; `letterSpacingEm` −0.1–0.5; `lineHeightMult` 0.8–2.5; `paddingDp` 0–200; `rowGapDp` 0–64; `intervalMinutes` ≥ 15 (JobScheduler floor `[plan §1.7]`); `dimAlpha` 0–1; `angleDeg` 0–359; gradient stops 2–8; `collection.url` must parse and be `https` (else mode → `color` + warning — cleartext is blocked by default and deprecated in 17 `[plan §1.7, §1.13]`); `component` must `unflattenFromString`-parse (`^[A-Za-z][\w.]*/[\w.$]+$`); `user ≥ 0`; `fontVariation` `^'[A-Za-z0-9 ]{4}' -?\d+(\.\d+)?(, *'[A-Za-z0-9 ]{4}' -?\d+(\.\d+)?)*$`; labels ≤ 40 chars, no control chars; `home.entries` ≤ 16; `format` skeleton `^[A-Za-z ,./-]{1,24}$`; enum fallbacks are the declared defaults.
- Unknown `Action` variants are preserved, not dropped (see §2.1), so a backup from a newer app round-trips through an older app.

### 1.4 Store: atomic write, read-once, notification (`:app` `PKG.settings`)
- `app/src/main/kotlin/PKG/settings/SettingsStore.kt` — single in-memory source of truth: `val current: Settings`, `fun update(transform: (Settings) -> Settings)`, `fun addListener((old, new) -> Unit)`, `fun flush()`. Loaded **synchronously** in `Application.onCreate` (one ≤ 50 KB file; wrap in `StrictMode.allowThreadDiskReads`) so the home screen never shows a loading state. Corrupt file → renamed `settings.json.corrupt-<ts>`, defaults used, never a crash (crash = silent loss of default-home status `[plan §1.2]`).
- Writes: `android.util.AtomicFile` (platform, zero deps: write `.new`, fsync, rename), on a single background thread, debounced 300 ms to coalesce bursts, forced in `Activity.onStop`. Engine-private churn (current background image, last fetch time, badge cache) is **not** in this document — the background engine keeps its own tiny state file to avoid rewriting settings every 15 minutes.
- Listeners are invoked synchronously on the main thread after `update`; consumers compare sub-objects by reference/equality (`old.theme != new.theme`) — data classes make this cheap. No coroutines/Flow (keeps `:core:theme` and the store JVM-testable with plain listeners).

### 1.5 Export / import / share — phase placement
- **Phase 2**: settings backup export/import via SAF (`ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT`, MIME `application/json`, `CATEGORY_OPENABLE`) `[tech §E.3]`. Backup import replaces everything, clears `consents`, and re-checks `userFont` permission.
- **Phase 7**: theme export via SAF as `<id>.justatheme` (plain UTF-8 JSON in v1; loader sniffs the first bytes so a zip package can reuse the extension later without a break — a deliberate narrowing of `[tech §E.2]`); share via `ACTION_SEND` + `androidx.core.content.FileProvider` (`cache/share/`); receive via `ACTION_VIEW`/`ACTION_SEND` filters on `application/json`, `application/octet-stream` **and** `pathSuffix=".justatheme"` on `content`/`file` schemes `[tech §E.3]`; deep link `justa://theme?d=<base64url(deflate(json))>` (≤ 64 KB) and `justa://theme?url=<https…>` (fetch ≤ 256 KB, confirm first) with `BROWSABLE`. Every inbound theme goes through lint → preview screen (name, author, warnings, "this theme loads images from <host>") → confirm → `settings.update { it.copy(theme = imported) }`. Missing theme fields take **defaults**, not the previous theme's values (a theme is a whole document).

### 1.6 Built-in themes
Four, stored as Kotlin constants in `BuiltinThemes.kt` (typed, JVM-testable, no parse at startup); a golden-file test asserts `docs/themes/<id>.justatheme` equals their serialisation so the repo also ships example files for authors. `ink` (default: black background, white sans-serif, shadow off), `paper` (light serif), `terminal` (monospace, green accent, `badge.style=number`), `you` (Material You tokens, `background.mode=wallpaper`, `text=auto`, shadow legibility, `darkColors` set). Default = `ink` (guaranteed legible without wallpaper colour info); onboarding offers `you` for "keep my wallpaper".

## 2. Action registry (`:app` `PKG.actions`)

### 2.1 Serializable model (lives in `:core:theme/Action.kt` because it is persisted)
```kotlin
@Serializable sealed class Action {
  @Serializable @SerialName("none")     object None : Action()
  @Serializable @SerialName("app")      data class OpenApp(val component: String, val user: Long = 0) : Action()
  @Serializable @SerialName("shortcut") data class Shortcut(val pkg: String, val id: String, val user: Long = 0) : Action()
  @Serializable @SerialName("url")      data class OpenUrl(val url: String) : Action()
  @Serializable @SerialName("builtin")  data class Builtin(val id: BuiltinId) : Action()      // no default → unknown id fails → Unknown
  @Serializable @SerialName("app_info") data class AppInfo(val component: String, val user: Long = 0) : Action()  // menu-only
  @Serializable @SerialName("uninstall")data class Uninstall(val pkg: String, val user: Long = 0) : Action()      // menu-only
  data class Unknown(val raw: JsonObject) : Action()   // produced by ActionSerializer on decode failure; re-encoded verbatim
}
```
`BuiltinId` is one string-valued enum with a `group` property (INTENT / LAUNCHER / DEVICE / SYSTEM) rather than three enums — one picker list, one spec table, one JSON shape `{"type":"builtin","id":"flashlight_toggle"}`.
- INTENT (implicit intents from `[plan §1.5]`, `[gestures B]`): `assistant` (ACTION_ASSIST), `camera` (INTENT_ACTION_STILL_IMAGE_CAMERA), `dialer` (ACTION_DIAL), `messages`/`email`/`contacts`/`browser`/`gallery`/`music`/`maps`/`app_market` (`Intent.makeMainSelectorActivity(ACTION_MAIN, CATEGORY_APP_*)` — GMS-free, no `market://`), `alarms` (ACTION_SHOW_ALARMS), `timers` (ACTION_SHOW_TIMERS, 26), `calendar_today` (`content://com.android.calendar/time/<now>`), `web_search` (ACTION_WEB_SEARCH), `wallpaper_picker` (ACTION_SET_WALLPAPER), `settings`, `wifi_settings`, `bluetooth_settings`, `display_settings`, `sound_settings`, `battery` (ACTION_POWER_USAGE_SUMMARY), `notification_settings` (ACTION_ALL_APPS_NOTIFICATION_SETTINGS 33, else ACTION_SETTINGS), `panel_internet`/`panel_wifi`/`panel_volume`/`panel_nfc` (Settings.Panel, 29).
- LAUNCHER (coded): `open_search`, `open_drawer`, `launcher_settings`, `next_background`, `default_launcher_chooser` (RoleManager 29 / ACTION_HOME_SETTINGS).
- DEVICE (coded): `flashlight_toggle` (CameraManager.setTorchMode + registerTorchCallback), `volume_ui` (adjustVolume(ADJUST_SAME, FLAG_SHOW_UI)), `media_play_pause`/`media_next`/`media_previous` (dispatchMediaKeyEvent DOWN+UP).
- SYSTEM (accessibility-gated): `lock_screen`, `notification_shade`, `quick_settings`, `recents`, `screenshot`, `power_menu` `[plan §1.6]`.
- DND is not in v1 (not in the user's included groups); adding it later is one enum entry.

### 2.2 Registry classes
- `app/src/main/kotlin/PKG/actions/ActionRegistry.kt` — `spec(action)`, `availability(action): Availability`, `execute(action, ctx: ExecContext): ExecResult`, `pickerItems(): List<PickerSection>`. `ExecContext(activity, sourceView?, sourceBounds?)` (launch from a visible window satisfies BAL `[platform B7]`).
- `ActionSpec(labelRes, descriptionRes, group, requirement: Requirement, minApi, bindable: Boolean, executor)`; `Requirement` = `None | AccessibilityService | DeviceAdmin | RoleHome | UsageAccess`.
- `IntentActions.kt` (table id → intent factory), `DeviceActions.kt` (torch/volume/media), `LauncherActions.kt` (search/drawer/settings/next background via `BackgroundController` interface), `SystemActions.kt` (routes to the bridge below), `AppActions.kt` (OpenApp/Shortcut/AppInfo/Uninstall via `AppRepository`).
- `SystemActionsBridge` (interface, defined here, implemented by the accessibility planner in `PKG.system`): `state(): Enabled|Disabled|Unsupported(api)`, `perform(id: SystemActionId, done: (Boolean)->Unit)`, plus `LockFallback.isActive()/lockNow()` (device admin) and `ShadeFallback.tryExpand(): Boolean` (reflection, try/catch, `EXPAND_STATUS_BAR`). Routing: lock = service if enabled → device admin if active → `NeedsGrant`; shade = reflection first if `features.shadeReflection` (works today `[plan §1.6]`) → service if enabled → `NeedsGrant`; a session flag remembers reflection failure.

### 2.3 Availability → UI behaviour
| `Availability` | Picker | Binding list | On execute |
|---|---|---|---|
| `Available` | shown | normal | run; failure → `REJECT` haptic + toast |
| `NeedsGrant(req)` | shown with marker "Needs accessibility service / device admin / usage access" | marker | open the disclosure screen (affirmative consent → `consents.*At` → system grant page; sideload → "Allow restricted settings" help, other planner) — never a bare toast |
| `Unsupported(ApiTooLow \| NoHardware)` | hidden | greyed "Not available on this device" | toast |
| `NoHandler` | shown (cannot pre-check: `resolveActivity` is visibility-filtered without `<queries>` `[platform B2]`, so implicit intents are assumed available) | — | catch `ActivityNotFoundException` → toast; mark `NoHandler` for the session so the picker greys it |
| `NotDefaultLauncher` (shortcuts, `hasShortcutHostPermission()==false` `[gestures E2]`) | shortcuts hidden | marker | prompt to set default (RoleManager) |
| `Missing` (app/profile gone) | n/a | "App not installed" | toast |

Picker data: `PickerItem(action, label, description?, marker: RequiresGrant(kind) | Unavailable(reason) | Hidden)`; sections: Apps (searchable via `:core:search`, hidden apps included with "(hidden)"), Launcher, Device, Shortcuts to Settings, Advanced (SYSTEM group). Consumers: `GestureDispatcher`, the app long-press menu (§5), home-info `onTap/onLongPress`.

Default bindings: `swipe_up → open_search`, `swipe_down → notification_shade`, `swipe_left → camera`, `swipe_right → dialer`, `long_press → launcher_settings`, `double_tap → none` (lock needs a grant; suggested in settings), long swipes → none. Home-info defaults: clock → `alarms`, date → `calendar_today`, battery → `battery`, screenTime → usage-access settings.

## 3. Gestures (`:app` `PKG.gestures`)

- `GestureRecognizer.kt` — **pure Kotlin, no Android imports** (JVM-tested without Robolectric): `onEvent(s: TouchSample): List<GestureEvent>`, `onTimer(nowMs)`, `nextDeadlineMs(): Long?`. `TouchSample(action, x, y, timeMs, pointerCount, onInteractiveChild, deepPress, ambiguous, vx, vy)` (velocities valid on UP only, supplied by the wrapper's `VelocityTracker`).
- `GestureConfig` built in `HomeView` from `ViewConfiguration` `[gestures A1]`: `touchSlop`, `doubleTapSlop`, `minFlingVelocity`, `maxFlingVelocity` (clamp), `tapTimeout`, `doubleTapTimeout`, `longPressTimeout` (user-adjustable in accessibility settings, hence read not hard-coded), `ambiguousMultiplier` (30+), plus ours: `swipeMinPx = 48 dp`, `longSwipePx(axis) = clamp(0.35 × view extent on that axis, 140 dp, 320 dp)` recomputed in `onLayout` (the "long swipe" rule; mLauncher's short/long split `[tech §A.3]` re-specified in view-relative terms).
- States: `IDLE → PRESSED → {DRAGGING(axis, armed) | LONG_PRESSED | TAP_WAIT → SECOND_PRESSED} | IGNORED(multi-pointer)`. Transitions: DOWN → PRESSED (deadline = t0+longPressTimeout); move > slop → DRAGGING with axis locked (|dx|>|dy| ? H : V), long-press cancelled; timer in PRESSED and `!onInteractiveChild` (or `deepPress`) → emit `LongPress`; UP in PRESSED without slop → `TAP_WAIT` (deadline doubleTapTimeout) unless `onInteractiveChild` (child owns taps) → IDLE; DOWN within `doubleTapSlop` before deadline → SECOND_PRESSED; its UP without slop → `DoubleTap`; TAP_WAIT timeout → `SingleTap` (unbound in v1); DRAGGING: when |d| crosses `longSwipePx` → `LongSwipeArmed(dir)` / falls below 85 % → `Disarmed` (hysteresis); UP in DRAGGING → `Swipe(dir, long = |d| ≥ longSwipePx)` if `|d| ≥ swipeMinPx || (|d| ≥ 2·slop && |v_axis| ≥ minFling)`, else nothing; CANCEL → IDLE silently; second pointer → IGNORED until all up.
- Ownership: `HomeView : FrameLayout` overrides `dispatchTouchEvent` to feed **every** event once; `onInterceptTouchEvent` returns true only once the recognizer is DRAGGING (children then get CANCEL, so a swipe over an app name still works, as in Olauncher); `onTouchEvent` returns true. Rows and info lines are ordinary clickable/long-clickable `TextView`s (`minHeight 48 dp`), so taps/long-presses on them stay native; the recognizer suppresses tap/long-press events for touches that began on an interactive child (`onInteractiveChild`). Double tap therefore works on empty area only; long press on empty area → `long_press` binding; row gaps count as empty area.
- System gestures `[gestures A2]`: default **no** exclusion rects — the user's back gesture wins at the edges (we receive DOWN then CANCEL; recognizer ignores). `gestures.edgeExclusion = both` registers full-height left/right rects of `systemGestures()` inset width in `onLayout` (home activity is exempt from the 200 dp cap); never fight `mandatorySystemGestures()` (bottom). Setting copy: "Take over edge swipes (disables system back gesture on the home screen)".
- Haptics (`Haptics.kt`, `performHapticFeedback`, no permission `[gestures A4]`, gated by `gestures.haptics`): `LongPress → LONG_PRESS`; `LongSwipeArmed → GESTURE_THRESHOLD_ACTIVATE` (34) else `CLOCK_TICK`; `Disarmed → GESTURE_THRESHOLD_DEACTIVATE` (34) else none; `DoubleTap → CONFIRM` (30) else `VIRTUAL_KEY`; action failure → `REJECT` (30) else none; short swipe → none; torch toggle → `TOGGLE_ON/OFF` (34) else `CONFIRM`.
- `GestureDispatcher.kt` maps `GestureEvent → GestureId → settings.gestures.bindings → ActionRegistry.execute`. Deviation from `[plan §1.5]` ("GestureDetector for single-finger cases"): everything is hand-rolled in one pure recognizer so the whole state machine is unit-testable and there is one timing model.

## 4. Search

### 4.1 `:core:search` (pure Kotlin)
- `core/search/src/main/kotlin/PKG/core/search/Normalizer.kt` — `normalize(s): NormalizedText(cps: IntArray, boundary: BooleanArray, map: IntArray)`: `java.text.Normalizer.normalize(s, NFD)` → drop `Character.getType() ∈ {NON_SPACING_MARK, COMBINING_SPACING_MARK, ENCLOSING_MARK}` → `Character.toLowerCase(cp)` per code point (locale-independent; `[tech §D.1]` names the Turkish pitfall of `lowercase(Locale.getDefault())`) → separators `-_+.,/&()` and whitespace collapse to one space; `boundary[i]` = start, after separator, lower→Upper camel edge or letter↔digit edge (computed on the original before lowercasing); `map` gives original indices for highlighting.
- `Scorer.kt` — optimal subsequence DP (fzf-V2-style, fzf is MIT, `[tech §D.1]`), constants (initial, tuned by the test list): match 16, word-initial bonus 16 (×2 for the first query char), consecutive bonus 4, prefix bonus 6 (first query char at index 0), gap start −3, gap extend −1. Hard requirement: query must be a subsequence (O(n) greedy pre-check rejects most labels before DP). Threshold: keep iff `score ≥ 16·m` (bonuses must at least cancel gap penalties — "gm" vs "Instagram" = 28 < 32 → dropped; "gm" vs "Google Maps" ≈ 78 > "Gmail" ≈ 74 because word-initial outranks consecutive). DP buffers are allocated once per `SearchIndex` and reused: zero allocation per label.
- `SearchIndex.kt` — `SearchIndex<K>(entries: List<Entry<K>>)`, `Entry(key, labels: List<String>)` (display label + original label so a renamed "Gmail→Mail" still matches "gmail"); `query(q, limit=30): List<Match<K>>`, `Match(key, score, matchedOriginalIndices)`; empty query → all entries alphabetically (by normalized primary label), score 0; ranking tie-breaks: score desc → earlier first-match index → shorter label → alphabetical normalized → stable input order. Rebuilt only when the repository or renames change (labels cached per app identity); no usage history (would force a settings write per launch).
- Complexity target: n ≤ ~40 cps, m ≤ ~16 → ≤ 640 cells per label; JVM test asserts 1 000 labels × 10 queries < 50 ms on CI (arithmetic from `[tech §D.3]`, not a benchmark).

### 4.2 Drawer / search screen (`:app` `PKG.search`)
- `SearchOverlay.kt` — a themed view attached to the single Activity's root (dismissed by the `OnBackInvokedCallback` `[plan §1.2]`), `EditText` + platform `ListView` (fast-scroll on, view-holder adapter) with alphabetical drawer when the query is empty; excludes `settings.hidden`.
- Keyboard: `settings.search.autoShowKeyboard` → `requestFocus` + `WindowInsetsController.show(ime())` on open. Android 17 `[plan §1.13]`: while the overlay is open call `window.setSoftInputMode(SOFT_INPUT_STATE_ALWAYS_VISIBLE or SOFT_INPUT_ADJUST_RESIZE)` and restore `SOFT_INPUT_STATE_HIDDEN` on close — the manifest attribute cannot be used on the shared home Activity (it would show the IME on Home). The home Activity also declares `configChanges` for orientation/screenSize/uiMode/fontScale/locale/density and re-applies the theme itself (Launcher3 pattern), and re-requests the IME in `onConfigurationChanged` if the overlay is open. If device tests on 17 show the IME still lost, fall back to a separate `SearchActivity` with `windowSoftInputMode="stateAlwaysVisible"` (see Open questions).
- Auto-launch: exactly one match ∧ `autoLaunchSingle` ∧ not composing (`BaseInputConnection.getComposingSpanStart(editable) == -1`, Olauncher's guard `[tech §A.2]`). IME action (GO/SEARCH) → top result; if no results and `webSearchFallback` → `ACTION_WEB_SEARCH` with the query (a "Search the web for …" row is also shown on no-match when enabled).
- Shortcuts sub-list: when exactly one result remains **and auto-launch is off** (otherwise it would launch before the list is visible), fetch that app's shortcuts (`ShortcutQuery` dynamic|manifest|pinned, `setPackage`, ≤ 4) and render indented rows launched via `startShortcut`; only if `hasShortcutHostPermission()`. With auto-launch on, shortcuts remain reachable from the long-press menu.

## 5. Shortcuts / rename / hide (`:app` `PKG.apps` + `PKG.home`)

- `AppMenu.kt` (themed popup of text rows; platform `AlertDialog` for the rename input). Order: `[shortcut rows ≤ 4, "→ label"]`, `Add to home` / `Remove from home`, `Rename`, `Hide` / `Unhide`, `App info`, `Uninstall` (omitted for `FLAG_SYSTEM` apps). "Open" is omitted (tap opens). If not the default launcher, a hint row "Set as default launcher to see shortcuts" replaces the shortcut rows (tap → RoleManager request).
- Uninstall: `Intent(ACTION_DELETE, "package:<pkg>")` + `REQUEST_DELETE_PACKAGES` in the manifest `[platform B6]`. App info: `LauncherApps.startAppDetailsActivity(component, user, bounds, null)`.
- Shortcuts: `ShortcutProvider.kt` — fetched lazily on menu open (API 25+, `hasShortcutHostPermission()`), try/catch `SecurityException`/`IllegalStateException` (user locked); `LauncherApps.Callback.onShortcutsChanged` refreshes an open menu only; no pinning `[gestures E2]`.
- Storage: `settings.hidden`, `settings.renames` (§1.2). `LabelResolver.kt`: `entry.label ?: renames[app] ?: LauncherActivityInfo.getLabel()`; label cache keyed by `AppKey`, invalidated on `onPackageChanged` and `ACTION_LOCALE_CHANGED` `[platform B3]`. Hidden apps: excluded from drawer/search, kept on home if already placed, listed under Settings → "Hidden apps" for unhide.

## 6. Seam for work profile / private space (`:app` `PKG.apps`)

- `AppKey(component: ComponentName, user: UserHandle)` is the in-memory identity of every app everywhere (repository, badges, search index keys, menu); `AppRef` (§1) is its serialized twin. `UserSerials.kt`: `serialOf(handle) = UserManager.getSerialNumberForUser`, `handleOf(serial) = getUserForSerialNumber` (null → entry skipped, not deleted — a profile may be temporarily unavailable).
- `AppRepository.kt` enumerates `for (u in ProfileSource.profiles()) launcherApps.getActivityList(null, u)`, registers one `LauncherApps.Callback` (already per-`UserHandle`), skips our own package and the synthesized `android.app.AppDetailsActivity` entries `[platform B1]`, lists multiple launcher activities of one package individually. **v1**: `ProfileSource.profiles() = listOf(Process.myUserHandle())`.
- Not built now: `getProfiles()`, `getUserBadgedLabel`, quiet-mode state/broadcast receivers (`ACTION_(MANAGED_)PROFILE_AVAILABLE/UNAVAILABLE`, runtime-registered with `RECEIVER_NOT_EXPORTED`), `requestQuietModeEnabled`, private-space container/`getLauncherUserInfo`/`ACCESS_HIDDEN_PROFILES` `[platform B4, B5]`. Later change is confined to: `ProfileSource`, `AppEntry` gaining `badgedLabel` and `available`, a drawer section header per profile, a `profiles` settings object, and two `BuiltinId`s (`pause_work_apps`, `toggle_private_space`). No JSON already written in v1 changes shape.

## 7. Phases (deliverables → acceptance criteria)

**Phase 2 — Settings + persistence**
Deliverables: `:core:theme` types (`Settings`, `Theme`, `Action`, `AppRef`), `ThemeJson`, lint/sanitizer, migration skeleton (v1 identity + fixture), `BuiltinThemes`, `SettingsCodec`; `:app` `SettingsStore`, settings screens (platform widgets): home apps choose/order (up/down), hidden apps, renames, night mode; long-press menu without shortcuts; backup export/import via SAF.
Acceptance: (a) fresh install → `settings.json` equals `Settings()` defaults; (b) kill -9 during a burst of edits never yields a corrupt file (AtomicFile; test by killing mid-write with a 1 000-iteration script); (c) corrupt/truncated file → defaults + `.corrupt-*` copy, no crash; (d) backup export → import on a second device reproduces home list, hidden, renames; `consents` cleared; (e) a JSON with unknown keys, an unknown `Action` type and an out-of-range `homeSp` loads with warnings, round-trips the unknown action unchanged, clamps the size; (f) release (R8 full mode + shrinking) build round-trips the fixtures — instrumented smoke on the minified variant; (g) settings change reaches the home screen within one frame via the listener, no restart.

**Phase 3 — Gestures + action registry**
Deliverables: `GestureRecognizer`, `HomeView` touch ownership, `GestureDispatcher`, `Haptics`, `ActionRegistry` + specs for all `BuiltinId`s, picker UI, disclosure hook (`SystemActionsBridge` stubbed as `Unsupported`), gesture settings screen, `edgeExclusion` opt-in.
Acceptance: (a) JVM: recognizer emits exactly the expected event for each scripted sequence (short/long × 4 directions, double tap, long press, cancel, multi-pointer, slow drag under `swipeMinPx`, flick over `2·slop` with fling velocity, tap on child suppresses long-press); (b) device: swipe over an app name triggers the swipe, tap on a name launches, long-press on a name opens the menu, long-press on empty area opens settings; (c) gesture-nav device: edge swipe performs system back with `edgeExclusion=none`, our swipe with `both`; (d) every `Unsupported` action is hidden from the picker on API 26 emulator (panels, screenshot) and present on 34; (e) an intent action with no handler shows a toast and is greyed afterwards; (f) SYSTEM-group actions open the disclosure screen when the service is disabled; `lock_screen` uses device admin when active; (g) `long_swipe_*` haptic fires when crossing `longSwipePx`.

**Phase 4 — Search**
Deliverables: `:core:search` (normalizer, scorer, index), `SearchOverlay` with drawer, keyboard behaviour, auto-launch guard, IME action, web-search fallback (Opt toggle), shortcuts sub-list.
Acceptance: (a) JVM test list in §7.1 passes; (b) 1 000 synthetic labels × 10 queries < 50 ms on CI; (c) device: typing with a CJK IME never auto-launches mid-composition; (d) rotation on an API 36/37 emulator keeps the IME open with the overlay; (e) hidden apps never appear in drawer or results; (f) renamed app found by both old and new label; (g) 200 % font scale: rows ≥ 48 dp, no clipping.

**Phase 7 — Themes**
Deliverables: `ThemeResolver` (tokens, `auto`, night `darkColors`), `ThemeApplier` (`PKG.home`: typeface incl. SAF font via `Typeface.Builder` + `setFallback("sans-serif")`, sizes in sp via `TypedValue.applyDimension`, letter spacing, line height, case, gravity/padding, shadow/outline/scrim, badge style, system-bar appearance), theme picker with the 4 built-ins, token editor (colours/typography/layout/legibility/badge/background fields), theme export/share/receive/deep link + preview/confirm, golden `docs/themes/*.justatheme`.
Acceptance: (a) each built-in renders on API 26 and 34 emulators at 100 %/200 % font scale without clipping; (b) `@android:color/system_*` resolves on 31+, falls back to `ink` colours on 26–30; (c) `.justatheme` opened from a file manager, received via share sheet, and via `justa://theme?d=` each show the preview and apply on confirm; a theme with `schema: 99` is refused, `minAppVersion` above ours warns; (d) a theme with `collection.url` asks for confirmation naming the host; `http://` URL degrades to `color` with a warning; (e) export → import of the current theme is byte-identical after normalisation; (f) `family: custom` without a device font falls back to sans-serif with a one-time notice; (g) contrast: built-ins meet 4.5:1 for text < 18 sp.

### 7.1 JVM test lists
`:core:theme`: defaults round-trip; unknown keys ignored; missing fields → defaults; `explicitNulls` (null `onTap` omitted); colour grammar accept/reject table (6/8-digit, token allowlist, `auto` only on text roles); every numeric clamp; enum fallback + lint warning; component regex; `Action` polymorphism for all variants + `Unknown` round-trip; migration fixture v1→v1 identity and a synthetic v1→v2 rename; built-ins validate with zero warnings and match golden files; theme import merge (missing fields → defaults, not previous values); `schema > supported` refusal; `minAppVersion` warning.
`:core:search`: "gm" → Google Maps first among {Google Maps, Gmail, Google Meet, Instagram, Telegram, Games, GroupMe} and Instagram/Telegram dropped; "cafe" matches "Café"; "ubereats" matches "Uber Eats"; "wa" → WhatsApp before "Wallet"? (word-initial vs prefix — record expected order); CJK: "地图" matches "百度地图" and "Google 地图"; Turkish: results identical under `Locale("tr")` default and `Locale.ROOT`, "i" does not match "ı"; empty query → alphabetical all; no-match → empty; renamed alias matching; tie-break determinism; highlight indices map back to original string; performance bound.

## 8. Interfaces exposed to the other planners (summary)
- NLS/badges: reads `settings.notifications`, `theme.badge`, `theme.colors.badge*`, `typography.sizes.badgeSp`; provides `BadgeCounts { count(AppKey): Int; addListener }` consumed by `HomeListView`.
- Background engine: reads `theme.background`, `settings.backgroundPolicy`; provides `BackgroundController { next(); state(): BackgroundState(supportsDarkText: Boolean?, mode) ; addListener }` consumed by `ThemeResolver` (`auto`, `systemBars.auto`) and `LauncherActions.next_background`; keeps its own state file.
- Accessibility service / device admin: implements `SystemActionsBridge`, `LockFallback`, `ShadeFallback`; reads `features.accessibilityService/deviceAdminLock/shadeReflection`; writes `consents.accessibilityAt` via `SettingsStore.update`.
- Home info: reads `settings.homeInfo`, `theme.typography.sizes.clockSp/infoSp`, `theme.layout.infoAlign`, `colors.textSecondary`; executes `onTap/onLongPress` through `ActionRegistry`; screen time gated by `features.screenTime` + `consents.usageAccessAt`.
- Release engineering: `consents` timestamps and `features` provide the disclosure-before-grant evidence; `Action.Unknown` and fixtures give the backward-compat guarantee for F-Droid version skew.

## Disagreements with / refinements of the previous plan
1. "One JSON settings document doubles as backup/export" `[plan Part 3]` → true for backup; theme export is the nested `Theme` type only (privacy).
2. `[plan Part 4]` manifest lists `ACCESS_HIDDEN_PROFILES` and `SET_WALLPAPER` → excluded in v1 per the user's decisions; the seam in §6 keeps the schema ready.
3. `[plan §1.5]` suggests `GestureDetector` for single-finger gestures → replaced by one pure recognizer for testability.
4. `[tech §E.2]` `.justatheme` as zip → plain JSON in v1, magic-byte sniff reserves zip for later.
5. Auto-launch and the shortcuts sub-list conflict → sub-list only when auto-launch is off.
6. `windowSoftInputMode="stateAlwaysVisible"` → applied dynamically per overlay state, not in the manifest of the shared Activity.

## Open questions
1. Default theme: `ink` (black/white, always legible) vs `you` (wallpaper passthrough) — product taste.
2. Android 17 IME restore with the single-Activity overlay + handled config changes: verify on a 37 image; fallback is a `SearchActivity` with `stateAlwaysVisible`.
3. Token fallback on API 26–30 (default theme's colour) — acceptable, or should the grammar grow an explicit `token|#fallback` form later?
4. Should `hidden` apps be findable by an exact full-label search (Olauncher-style "secret" access) or strictly excluded?
5. Scorer constants: confirm the expected order for "wa" → WhatsApp vs Wallet and whether 2-letter in-word matches (Instagram for "gm") should be dropped or merely ranked last.
6. `swipe_down → notification_shade` as default relies on the reflection route; if Play pre-launch flags hidden-API use, default to `open_drawer` instead.
7. Do we want `home.entries[].label` UI (per-row rename) in v1, or keep it schema-only?
8. Synthesized "app details" entries: confirm on device whether `<queries>` MAIN/LAUNCHER surfaces them at all (`[plan §1.14 #1]`); the class-name filter is a guard either way.

### Critical Files for Implementation
- /home/hyzii/Projects/justa-launcher/core/theme/src/main/kotlin/app/justa/launcher/core/theme/Settings.kt (with Theme.kt, Action.kt, AppRef.kt alongside — the schema everything else depends on)
- /home/hyzii/Projects/justa-launcher/core/theme/src/main/kotlin/app/justa/launcher/core/theme/SettingsCodec.kt (lint → migrate → decode → sanitize; JsonConfig, Validation, Migrations)
- /home/hyzii/Projects/justa-launcher/app/src/main/kotlin/app/justa/launcher/actions/ActionRegistry.kt (specs, availability, execution, picker; `SystemActionsBridge` interface)
- /home/hyzii/Projects/justa-launcher/app/src/main/kotlin/app/justa/launcher/gestures/GestureRecognizer.kt (pure state machine; `HomeView` touch ownership built on it)
- /home/hyzii/Projects/justa-launcher/core/search/src/main/kotlin/app/justa/launcher/core/search/SearchIndex.kt (with Normalizer.kt and Scorer.kt)
