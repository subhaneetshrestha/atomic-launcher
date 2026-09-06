> Snapshot of the approved v1 build plan (2026-09-06). Paths under `~/.claude` refer to the planning session and are not needed to build the project.

# justa-launcher — v1 build plan

Status: research complete (previous session), scoping decisions made (this session), detailed design done by three planners (this session). Pending before execution: the three user answers in "Open decisions" below. No code exists yet.

## Context

**What**: a text-only, minimalist Android home launcher. The home screen shows a curated list of app names; the user controls list position, alignment, text size and font; binds gestures to apps and actions; opens a fuzzy app search by gesture; sees a notification count beside an app name; rotates the home background from a URL image collection; shares community JSON themes. Lightweight is a hard requirement (release APK ≤ 2.5 MiB, small resident heap: the home process is kept alive at oom-adj 600).

**How we got here**
- Session 1 (2026-09-05/06) ran three primary-source research agents and wrote `/home/hyzii/.claude/plans/i-want-to-create-mighty-swan.md` (research findings with citations, customization catalog, stack recommendation, architecture, phases). It ended on four unanswered scoping questions.
- Session 2 (this one) got the answers, found the machine has no Android toolchain, and ran three Plan agents whose full reports are in `~/.claude/projects/-home-hyzii-Projects-justa-launcher/60ef4762-0942-43d4-9c20-8ac4b7ece712/subagents/` (`agent-ac8685b14c8b6e55e` scaffold/CI/home skeleton, `agent-ac709c4f2cecbcaee` data model/actions/gestures/search, `agent-a1a2f495916687cc4` badges/accessibility/background/release). The research reports from session 1 are in `.../5e2081a0-0df9-4263-90e1-c1b00691d174/subagents/` plus `.../tool-results/bvouz6pzm.txt`. Phase 0 copies all of these into the repo so nothing depends on chat transcripts.

**Decisions (fixed, 2026-09-06)**
1. Kotlin + Android Views, minSdk 26, targetSdk 36, compileSdk 37 if the SDK offers it (else 36). Single Activity, AndroidX limited to `activity` + `core`, kotlinx-serialization-json, R8 full mode, no native code, no coroutines dependency in v1 (seam kept).
2. v1 includes: accessibility-powered gestures (optional service, own process, off by default), home info lines (clock, date, battery, screen time), app shortcuts + launcher-local rename + hide. v1 excludes work profile and private space; every app identity is still keyed by (component, user serial) so profiles can be added later. No `ACCESS_HIDDEN_PROFILES`.
3. Background images are drawn inside the launcher window. No `WallpaperManager` writes, no `SET_WALLPAPER`.
4. Distribution: Google Play (AAB) + F-Droid/GitHub (APK), GMS-free. No product flavors (the channels differ only in artifact type and signing). Requires: Play accessibility declaration, in-app prominent disclosures, "Allow restricted settings" help for sideloads, reproducible-build hygiene, no `QUERY_ALL_PACKAGES`.
5. License Apache-2.0. No code copied from GPL launchers (Olauncher, KISS, mLauncher, Kvaesitso, Lawnchair); algorithms re-implemented from the cited descriptions. Package name: see Open decisions (placeholder `org.justa.launcher` = `PKG` below).

**Local environment (checked read-only)**: CachyOS, 16 CPUs, 14 GiB RAM, ~400 GB free, KVM usable. Installed: `git` only. Not installed: JDK, Android SDK, Gradle, adb, emulator, Android Studio. Repos offer `jdk17-openjdk`, `android-tools`, `android-udev`, `gradle 9.7.1`, `kotlin 2.4.10`; `paru` is available for AUR. `sudo` needs a password, so the user runs the install commands.

## Decisions from this session's follow-up questions (answered 2026-09-06)
- **Package name / applicationId**: placeholder `org.justa.launcher` for now. Must be replaced by a reverse-domain id the user controls before the first Play upload (mechanical refactor until then).
- **Toolchain route**: install with `paru` (official repos + AUR); `sdkmanager` only for pieces the AUR does not package well (system images, build-tools 36.0.0). Details in Phase −1.
- **Scope of the first implementation run**: Phases −1 to 1, then stop for review. Phases 2–9 follow in later runs, each approved separately.

Assumptions unless the user objects: default built-in theme is `ink` (black background, white sans-serif); Play App Signing enrolment uses our own release key so Play, F-Droid and GitHub builds share one certificate (decide finally in Phase 9); GitHub hosts the repo, CI and releases.

---

## Architecture

**Gradle modules**
- `:core:theme` — pure Kotlin: `Settings`/`Theme`/`Action` schema (kotlinx.serialization), lint, sanitizer, migrations, built-in themes, codec. JVM tests.
- `:core:search` — pure Kotlin: normalizer, subsequence scorer, index. JVM tests.
- `:core:collections` (added in Phase 6) — pure Kotlin: image-collection parsers and source detection. JVM tests.
- `:app` — Android. Packages under `PKG`: `home`, `apps`, `settings`, `gestures`, `actions`, `search`, `notifications`, `background`, `system`, `share`.

**Process and threading**: main process hosts everything except the accessibility service (`:system` process, alive only while enabled). `Threads` object = `HandlerThread("justa-apps")` for all `LauncherApps` binder work + main `Handler`; the background engine has its own single-thread executor. `JustaApp.onCreate` skips settings/badge/job setup when running in `:system`.

**Persistence**: one `filesDir/settings.json` (`Settings` root embedding the shareable `Theme`), loaded synchronously at startup, written with `android.util.AtomicFile`, debounced 300 ms, flushed in `onStop`. Engine-private churn (current image, fetch state, badge cache) lives in separate small files. Never persists the app list.

**Manifest (Phase 1)**: home activity with `exported="true"`, `launchMode="singleTask"`, `clearTaskOnLaunch`, `stateNotNeeded`, `resumeWhilePausing`, `taskAffinity=""`, `excludeFromRecents`, Launcher3's `configChanges` (orientation/screen size, not locale/fontScale/uiMode), intent filter MAIN + HOME + DEFAULT + LAUNCHER; `<queries>` with one MAIN/LAUNCHER intent; `android:enableOnBackInvokedCallback="true"`; zero `<uses-permission>`. Theme `Theme.Justa` from `Theme.DeviceDefault(.Light).NoActionBar`, `windowBackground=@color/home_background`, `forceDarkAllowed=false`. Must never contain `QUERY_ALL_PACKAGES`, `ACCESS_HIDDEN_PROFILES`, `SET_WALLPAPER`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, or GMS/Firebase components (CI gate).

**Permissions and protected components by phase**

| Item | Level | Phase |
|---|---|---|
| `REQUEST_DELETE_PACKAGES` | normal | 2 |
| NLS service with `BIND_NOTIFICATION_LISTENER_SERVICE` | special access "Notification access" | 5 |
| `INTERNET`, `ACCESS_NETWORK_STATE`; `JobService` with `BIND_JOB_SERVICE` | normal | 6 |
| Theme import intent filters + `FileProvider` | none | 7 |
| Accessibility service (`:system`, `BIND_ACCESSIBILITY_SERVICE`, `isAccessibilityTool=false`) | special access "Accessibility" | 8 |
| Device-admin receiver (`BIND_DEVICE_ADMIN`, `force-lock`) | user activation | 8 |
| `EXPAND_STATUS_BAR` | normal | 8 |
| `PACKAGE_USAGE_STATS` | special access "Usage access" | 8 |

**Build**: AGP 9.4.0, Gradle 9.7.1 wrapper with `distributionSha256Sum`, Kotlin 2.4.10, kotlinx-serialization-json 1.11.0, androidx.activity 1.13.0, androidx.core 1.19.0 (no `-ktx`), JUnit 5 via `kotlin-test` + `junit-bom` (version confirmed on first build), JDK 17 with toolchain auto-download off. `buildConfig=false`, `localeFilters=["en"]`, `dependenciesInfo` off, literal `versionCode`/`versionName`, debug `applicationIdSuffix=".debug"`. Release: minify + shrink, `mapping.txt` kept, signing config attached only when `JUSTA_KEYSTORE` env is set. Custom Gradle tasks: `checkReleaseApkSize` (fail > 2,621,440 bytes, via the Variant API), `gmsGuard` (fail on `com.google.android.gms|firebase|play` on the release classpath). R8: AGP generates keeps for manifest components; add the documented kotlinx.serialization full-mode rules (sealed `Action` hierarchy) and `SourceFile,LineNumberTable`. Verify on first build whether AGP 9 built-in Kotlin picks up the serialization compiler plugin; fallback `android.builtInKotlin=false` + `kotlin.android` plugin.

**CI (GitHub Actions)**: one job, one Gradle invocation (`test lintRelease assembleRelease bundleRelease checkReleaseApkSize gmsGuard`), wrapper validation, actions pinned by SHA, artifacts (APK, AAB, mapping, lint, tests, size report), size in the step summary. `release.yml` on `v*` tags signs from secrets and publishes APK + sha256 to a GitHub Release; AAB kept as artifact for manual Play upload. Determinism check: two clean builds → identical unsigned APK hash.

---

## Design by subsystem

### Data model (`:core:theme`)
- Rule: **theme = how it looks (shareable); settings = what is shown and what it does (device-local)**. Theme export serialises only `settings.theme`; backup export serialises everything.
- `Settings` (`schema`, `app`, `theme`, `home.entries[{component,user,label?}]` ≤ 16, `hidden[AppRef]`, `renames`, `gestures{bindings: GestureId→Action, haptics, edgeExclusion none|both}`, `homeInfo{position top|bottom; clock/date/battery/screenTime: {enabled, format?, onTap?, onLongPress?}}`, `search{autoLaunchSingle, autoShowKeyboard, webSearchFallback}`, `notifications{enabled, includeOngoing, perAppDisabled}`, `backgroundPolicy{unmeteredOnly, skipWhenBatteryLow}`, `features{accessibilityService, deviceAdminLock, shadeReflection, screenTime}`, `consents{*At}`, `appearance.nightMode`, `userFont{uri,name}?`).
- `Theme` (`schema`, `minAppVersion`, `meta{id,name,author,license,homepage,description}`, `colors` roles background/text/textSecondary/accent/badgeBackground/badgeText/shadow/outline/scrim as `#AARRGGBB`, `#RRGGBB`, `@android:color/system_*` token or `auto` for text roles, `darkColors` partial override, `typography{family, weight, italic, letterSpacingEm, lineHeightMult, case, fontVariation?, sizes{homeSp,drawerSp,clockSp,infoSp,badgeSp}}`, `layout{hAlign, vAlign, paddingDp{h,v}, rowGapDp, infoAlign?}`, `legibility{mode none|shadow|outline|scrim, …}`, `badge{style circle|dot|number, position start|end}`, `background{mode wallpaper|color|gradient|collection, color, gradient{angleDeg, stops 2–8}, collection{url, format auto|text|json|rss|atom|direct|wallhaven, intervalMinutes ≥15, fit, order random|sequential}, dimAlpha, transition none|fade}`, `systemBars{statusIcons, navIcons, hideStatusBar}`).
- `AppRef(component: String, user: Long = 0)`: user serial via `UserManager.getSerialNumberForUser`, resolved back with `getUserForSerialNumber`.
- `Action` sealed class with `@SerialName` types `none | app | shortcut | url | builtin | app_info | uninstall`; unknown types decode to `Action.Unknown(raw)` and re-encode verbatim (forward compatibility across F-Droid version skew).
- `ThemeJson`: `ignoreUnknownKeys`, `coerceInputValues`, `explicitNulls=false`, `encodeDefaults`, `classDiscriminator="type"`, `allowTrailingComma`/`allowComments` for hand-written themes; not lenient.
- Load = lint on the JSON tree (schema/minAppVersion/enum/colour/URL warnings) → migrations (JSON-tree step chain, fixtures per step) → typed decode → sanitizer (clamps, dedupe). Never throws; corrupt file → renamed `settings.json.corrupt-<ts>`, defaults used. `schema` bumps only for breaking changes.
- Validation: colour regex or allowlisted token; `homeSp` 10–64, `clockSp` 12–140, `weight` 100–900 rounded to 100s, `intervalMinutes` ≥ 15, `collection.url` must be `https` (else mode → `color` + warning), component regex, labels ≤ 40 chars, `home.entries` ≤ 16.
- Token resolution: `@android:color/system_*` via `context.getColor()` on API 31+; on 26–30 the default theme's colour for that role. `auto` text colour comes from the background engine's legibility sample (unknown → white).
- Built-in themes as Kotlin constants: `ink` (default), `paper`, `terminal`, `you` (Material You tokens + wallpaper passthrough). A golden test keeps `docs/themes/<id>.justatheme` identical to their serialisation.
- Sharing: Phase 2 backup via SAF (`application/json`). Phase 7 theme export `<id>.justatheme` (plain JSON; loader sniffs magic bytes so zip can be added later), `ACTION_SEND` + `FileProvider`, receive via `ACTION_VIEW`/`ACTION_SEND` on JSON/octet-stream MIME + `pathSuffix=".justatheme"`, deep links `justa://theme?d=<base64url(deflate(json))>` (≤ 64 KB) and `justa://theme?url=<https…>` (≤ 256 KB, confirm first). Every inbound theme: lint → preview (name, author, warnings, "loads images from <host>") → confirm → replace `settings.theme` (missing fields take defaults).
- `SettingsStore` (`:app`): in-memory source of truth, `update{}` + synchronous main-thread listeners, `AtomicFile` writes on one background thread.

### App repository and home list (`apps`, `home`)
- `AppKey(packageName, activityName, userSerial)`, `AppEntry(key, component, user, label, isSuspended)`. `AppRepository` enumerates `ProfileSource.profiles()` (v1: `listOf(Process.myUserHandle())`) via `LauncherApps.getActivityList(null, user)`, filters our package and synthesized `android.app.AppDetailsActivity` rows, sorts with `Collator`, publishes immutable snapshots; `LauncherApps.Callback` on the apps thread requeries only the affected package; label cache invalidated on package change and `ACTION_LOCALE_CHANGED` (runtime receiver, `RECEIVER_NOT_EXPORTED`) plus `ensureFresh(locales)` on resume. All binder calls in try/catch (a crashing home silently loses default status).
- `AppLauncher.launch(entry, sourceBounds)` → `startMainActivity(component, user, bounds, clip-reveal options)`; failure → refresh + toast.
- `HomeActivity : ComponentActivity`: `enableEdgeToEdge`, cutout mode ALWAYS/SHORT_EDGES, insets padding = `systemBars() | displayCutout()`, always-enabled back callback (dismiss overlay else no-op, so Back never finishes Home), `onNewIntent` with `CATEGORY_HOME` → reset to home, `onResume` → default-home check + repository freshness. Role prompt: `RoleManager` (29+) or `Settings.ACTION_HOME_SETTINGS`, shown as a one-line banner only, never auto-launched.
- `HomeListView : LinearLayout(VERTICAL)` of `TextView` rows (1–16 rows; no ListView/RecyclerView: no adapter, no nested scrolling to fight swipes, zero deps). Rows `MATCH_PARENT` wide, `minHeight` 48 dp, sizes in sp via `TypedValue.applyDimension`, never derived from `fontScale`. `HomeListModel` (pure Kotlin) maps snapshot + settings → rows; empty config → first 6 alphabetical apps. `ThemeApplier` grows across phases (typeface incl. SAF font with fallback, sizes, gravity, padding, legibility, badge style, system-bar appearance).
- Long-press menu (`AppMenu`): shortcut rows ≤ 4 (only while `hasShortcutHostPermission()`), Add/Remove from home, Rename, Hide/Unhide, App info (`startAppDetailsActivity`), Uninstall (`ACTION_DELETE` + `REQUEST_DELETE_PACKAGES`, hidden for system apps). Not default launcher → hint row "Set as default to see shortcuts". Label resolution: `entry.label ?: renames[app] ?: getLabel()`.

### Gestures (`gestures`)
- `GestureRecognizer`: pure Kotlin, no Android imports, `onEvent(TouchSample) → List<GestureEvent>` + timer deadlines; JVM-tested with scripted sequences. Config from `ViewConfiguration` (slop, double-tap slop, fling velocities, tap/double-tap/long-press timeouts) plus `swipeMinPx = 48 dp` and `longSwipePx = clamp(0.35 × view extent on the axis, 140 dp, 320 dp)` with 85 % hysteresis.
- Events: `Swipe(dir, long)` × 4 directions, `DoubleTap`, `LongPress` (empty area), `LongSwipeArmed/Disarmed`; multi-pointer ignored; CANCEL silent.
- `HomeView : FrameLayout` feeds every event via `dispatchTouchEvent`, intercepts only once DRAGGING (children get CANCEL, so a swipe over a name works); rows stay native clickable/long-clickable; touches starting on an interactive child suppress tap/long-press events.
- System gestures: no exclusion rects by default; `edgeExclusion=both` registers left/right full-height rects (home app is exempt from the 200 dp cap); never fights the bottom mandatory zone.
- Haptics via `performHapticFeedback` (no permission), gated by `gestures.haptics`: long press, long-swipe arm/disarm (API 34 constants with fallbacks), double tap CONFIRM, action failure REJECT.
- `GestureDispatcher`: event → `GestureId` → `settings.gestures.bindings` → `ActionRegistry.execute`.

### Action registry (`actions`)
- `BuiltinId` enum with groups: INTENT (assistant, camera, dialer, messages/email/contacts/browser/gallery/music/maps/app_market via `makeMainSelectorActivity`, alarms, timers, calendar_today, web_search, wallpaper_picker, settings pages, Settings panels API 29), LAUNCHER (open_search, open_drawer, launcher_settings, next_background, default_launcher_chooser), DEVICE (flashlight_toggle via `CameraManager.setTorchMode`, volume_ui, media_play_pause/next/previous via `dispatchMediaKeyEvent`), SYSTEM (lock_screen, notification_shade, quick_settings, recents, screenshot, power_menu). DND deferred.
- `ActionRegistry`: `spec`, `availability` (`Available | NeedsGrant(req) | Unsupported(api/hardware) | NoHandler | NotDefaultLauncher | Missing`), `execute(action, ExecContext)`, `pickerItems()`. Implicit intents are assumed available (visibility filtering makes pre-resolution unreliable); `ActivityNotFoundException` → toast and greyed for the session.
- `SystemActionsBridge` (implemented in `system`): notification shade = accessibility if enabled → reflection `StatusBarManager.expandNotificationsPanel` if `features.shadeReflection` (try/catch, `EXPAND_STATUS_BAR`) → `NeedsGrant`; lock = accessibility (API ≥ 28) → device admin `lockNow()` → chooser explaining both; other SYSTEM actions accessibility-only. `NeedsGrant` always opens the disclosure flow, never a bare toast.
- Default bindings: swipe_up → open_search, swipe_down → notification_shade, swipe_left → camera, swipe_right → dialer, long_press → launcher_settings, double_tap → none. Info lines: clock → alarms, date → calendar_today, battery → battery settings, screenTime → usage-access settings.

### Search (`:core:search`, `search`)
- `Normalizer`: NFD → drop combining marks → per-code-point `Character.toLowerCase` (locale-independent) → separators collapse; word boundaries from original casing/digits; index map for highlighting.
- `Scorer`: subsequence DP (fzf-v2 style, re-implemented): match 16, word-initial 16 (×2 for first char), consecutive 4, prefix 6, gap −3/−1; keep iff `score ≥ 16·m`. Greedy subsequence pre-check; buffers reused (zero allocation per label). Target: 1,000 labels × 10 queries < 50 ms on CI.
- `SearchIndex<K>`: entries with display + original labels (renamed apps match both), tie-breaks score → first match index → shorter label → alphabetical → input order; empty query → alphabetical drawer.
- `SearchOverlay` in the single Activity (dismissed by the back callback): `EditText` + platform `ListView`, excludes hidden apps; keyboard auto-show; soft-input mode set to `STATE_ALWAYS_VISIBLE|ADJUST_RESIZE` while open and restored on close (Android 17 IME-restore behaviour; fallback is a separate `SearchActivity` if a device test fails); auto-launch on exactly one match guarded by IME composition state; IME action launches top result; optional web-search fallback; shortcuts sub-list for a single result only when auto-launch is off.

### Notification badges (`notifications`)
- `BadgeNotificationListener` (main process) forwards to pure `BadgeCounter` (JVM-tested): drop group summaries, require `Ranking.canShowBadge()`, drop ongoing on the legacy default channel, drop empty title+text, drop suspended, drop ongoing/foreground-service/transport unless `includeOngoing`; count per (package, user) = `min(999, Σ max(1, number))`; `perAppDisabled` → 0.
- Lifecycle: `onListenerConnected` → rebuild from `getActiveNotifications()`; posted/removed/ranking-update → incremental; `onListenerDisconnected` → clear + one `requestRebind`; revoked → badges hidden, settings row shows "off". Nothing persisted, no boot receiver. Low-RAM devices ≤ API 29: setting disabled with explanation.
- Grant: `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` + component extra (30+), list page fallback; granted check via `isNotificationListenerAccessGranted` (27+), secure-settings string on 26.
- `BadgeStore`: main-thread map, coalesced publish (16 ms). `BadgeDrawable`: Paint-only circle/pill, dot, or plain number, sized from the row's resolved text size; compound drawable at start/end; rows stay ≥ 48 dp.

### Accessibility service, device admin, shade (`system`)
- `SystemActionsService` in `:system`: config XML `isAccessibilityTool=false`, `canRetrieveWindowContent=false`, `packageNames=PKG`, no event types (verify binding on API 26/33/36; fallback `typeViewClicked` scoped to our package). Invoked via explicit `startService` with the global-action id and an issue timestamp (drop if older than 2 s), `stopSelf` after each action, `START_NOT_STICKY`. Gates: NOTIFICATIONS/RECENTS 16, QUICK_SETTINGS 17, POWER_DIALOG 21, LOCK_SCREEN/TAKE_SCREENSHOT 28.
- Enable flow: disclosure → `ACTION_ACCESSIBILITY_SETTINGS` + on-screen steps; detect via `getEnabledAccessibilityServiceList` + `ENABLED_ACCESSIBILITY_SERVICES`; live listener for the settings screen.
- `LockAdminReceiver` + `device_admin_policies.xml` (`force-lock`); enable via `ACTION_ADD_DEVICE_ADMIN`; disclosure states the strong-auth consequence and that it must be deactivated before uninstall; offered always on API 26–27, as the alternative on 28+.
- `StatusBarShade`: reflection, catches `Throwable`; debug builds enable `StrictMode.detectNonSdkApiUsage().penaltyLog()`; release never touches StrictMode. Play pre-launch will list the call: accepted cost.
- Play declaration: purpose text (user-initiated global actions bound to gestures; reads no screen content), demo video, listing sentence ("Optional: uses the Accessibility API … Off by default."), in-app disclosure first.

### Background engine (`background`, `:core:collections`)
- Modes: `wallpaper` (passthrough: `FLAG_SHOW_WALLPAPER` + transparent window background, applied in `onCreate` before `setContentView`), `color`, `gradient`, `collection`. `BackgroundView` is the root's first child; paints colour/`LinearGradient`/center-crop bitmap + dim + scrim, 250 ms crossfade. `ColorSampler` samples top/list/bottom strips of a ≤ 64×64 software thumbnail → `Legibility(darkText, lightStatusIcons, lightNavIcons)`; passthrough mode uses `WallpaperManager.getWallpaperColors` (27+).
- Parsers (`:core:collections`): plain-text list, JSON array, RSS/Media RSS/Atom enclosures (SAX), direct image, Wallhaven keyless (`purity=100` forced, ≤ 45 req/min respected via per-host spacing). `SourceDetector` by host → `Content-Type`/magic bytes → first byte → root element → text. HTTPS only, ≤ 2048 chars, dedupe, cap 500. Index cached 6 h.
- Scheduling: `JobScheduler` periodic `max(interval, getMinPeriodMillis())`, flex period/5, `UNMETERED`/`ANY` per policy, `setRequiresBatteryNotLow`, `setPrefetch` (28+), not persisted; rescheduled idempotently at app start and on settings change. On-resume refresh when the interval elapsed (covers App Standby buckets). `NetworkPolicy`: skip on Data Saver (metered), on `unmeteredOnly` with metered network, on power-save/low battery for resume refreshes; reasons recorded for the settings status line.
- Fetch: `HttpURLConnection`, manual redirects ≤ 5 (HTTPS every hop), 10 s/20 s timeouts, caps 15 MiB image / 1 MiB index, `ETag`/`Last-Modified`, custom `User-Agent`. Download to cache tmp → atomic rename to `filesDir/background/current.img` (+ `previous.img`); `state.json` (current URL, timestamps, history of 20, bad URLs ring of 50, failures, backoff, last error).
- Decode: `ImageDecoder` + `setTargetSize` + `ALLOCATOR_HARDWARE` (28+), `BitmapFactory` + `inSampleSize` + EXIF below; unsupported MIME → mark bad. One hardware bitmap ≤ window×4 bytes (two during fade), engine Java heap < 1 MB, bitmap dropped on `onTrimMemory`. Failures back off `min(interval × 2^n, 24 h)`; `next_background` bypasses backoff but not the network policy.

### Home info lines (`home.info`)
- `ClockLine` (`TextClock`, follows system 12/24 h, optional pattern pair), `DateLine` (`java.time` + `getBestDateTimePattern`, refresh at local midnight and on locale/timezone change), `BatteryLine` (sticky `ACTION_BATTERY_CHANGED` runtime receiver, registered onStart/unregistered onStop), `ScreenTimeLine` + pure `ScreenTimeCalculator` (union of foreground intervals from `UsageStatsManager.queryEvents` since local midnight, excluding our package; refreshed on start and every minute while visible). `UsageAccess`: `PACKAGE_USAGE_STATS`, `ACTION_USAGE_ACCESS_SETTINGS`, `AppOpsManager` check. Each line is a themed `TextView` and a gesture surface (tap/long-press → actions).

### Consent and help flows (`settings.grant`)
- `GrantSpec(kind ∈ NOTIFICATION_ACCESS | ACCESSIBILITY | USAGE_ACCESS | DEVICE_ADMIN, texts, settingsIntents, isGranted, restrictedSettingsApplies)`; `GrantFlowScreen`: disclosure → Continue/Not now → first resolvable Settings intent → verify on return → success | retry | restricted-settings help. Records `consents.<kind>At`.
- Disclosure wording constraints (Play User Data policy): in-app, immediately before the Settings hand-off, describes data accessed and how it is used, affirmative action required, not buried or mixed; per kind: notification access (which app posted, how many, badge eligibility; text only checked for emptiness; never stored/transmitted), accessibility (reads no screen content, performs only bound actions, Android shows a periodic system notice), usage access (foreground history for today's screen time, on-device), device admin (force-lock, strong auth afterwards, deactivate before uninstall).
- `RestrictedSettingsHelpScreen` + `InstallSourceProbe` (API 33+: `getInstallSourceInfo().packageSource` LOCAL/DOWNLOADED → likely; `com.android.vending` → no; other/null → possibly, also on 35+ under Enhanced Confirmation Mode): steps "Settings → Apps → justa → ⋮ → Allow restricted settings" with a button to `ACTION_APPLICATION_DETAILS_SETTINGS`.

### Release engineering
- Gates: APK size (warn > 2.3 MiB, fail > 2.5 MiB), `gmsGuard`, manifest/DEX policy script (forbidden permissions, GMS packages), reproducible double-build compare (`apksigcopier compare` when signed).
- Reproducibility: pinned versions, wrapper sha256, no build-time values, `dependenciesInfo` off, `isCrunchPngs=false`, toolchain auto-download off. F-Droid metadata skeleton under `fdroid/metadata/<applicationId>.yml` (`Binaries` + `AllowedAPKSigningKeys` for reproducible publishing); fastlane text shared verbatim with the Play listing.
- Play: accessibility declaration, data safety (on-device processing only, HTTPS in transit), privacy policy page (`docs/privacy.md` on GitHub Pages), category Personalization. Signing: keystore outside the repo, env vars, CI secret; `versionCode = MAJOR×10000 + MINOR×100 + PATCH`, tags `vX.Y.Z`. Pre-release checklist in `docs/release/checklist.md`.

---

## Phases

### Phase −1 — Toolchain setup via `paru` (user runs the script; I write and verify it)
- I write `scripts/setup-toolchain.fish` (first file in the project; committed in Phase 0). Because `paru` and `sudo` prompt interactively, the user runs it with `! fish scripts/setup-toolchain.fish`. Steps:
  1. `paru -S --needed jdk17-openjdk android-udev gradle` (official repos; `gradle` 9.7.1 is only used once to generate the wrapper).
  2. `paru -S --needed android-sdk-cmdline-tools-latest android-sdk-platform-tools android-sdk-build-tools android-platform android-platform-36 android-emulator` (AUR, checked 2026-09-06: cmdline-tools 23.0, platform-tools 37.0.1, build-tools r37.0.0, `android-platform` = API 37 r02, `android-platform-36` = API 36 r02, emulator 37.1.11). These install into `/opt/android-sdk` and create the `android-sdk` group.
  3. `sudo gpasswd -a $USER android-sdk`, then re-login (or `newgrp android-sdk`) so `sdkmanager` can write to `/opt/android-sdk`.
  4. `sdkmanager --licenses`; `sdkmanager "build-tools;36.0.0" "system-images;android-26;google_apis;x86_64" "system-images;android-36;google_apis;x86_64"` (AGP 9.4's default build-tools plus the two emulator images). The AUR only packages the current image as `android-google-apis-x86-64-system-image` (API 36 r07 on 2026-09-06) and has no API 26 x86_64 Google APIs image, so `sdkmanager` is the reliable path for both.
  5. `avdmanager create avd -n api26 -k "system-images;android-26;google_apis;x86_64" -d pixel` and the same for `api36`.
  6. Fish environment (the AUR packages only drop `/etc/profile.d/*.sh`, which fish does not read): `set -Ux JAVA_HOME /usr/lib/jvm/java-17-openjdk`, `set -Ux ANDROID_HOME /opt/android-sdk`, add `$ANDROID_HOME/platform-tools`, `$ANDROID_HOME/cmdline-tools/latest/bin`, `$ANDROID_HOME/emulator` to `fish_user_paths`.
- Acceptance: `java -version` reports 17; `sdkmanager --list_installed` shows platforms 36 and 37, build-tools 36.0.0 and 37.0.0, platform-tools, emulator and both system images; `emulator -list-avds` shows `api26` and `api36`; `adb version` works; `/dev/kvm` is accessible; `emulator -avd api36 -no-window` boots to `sys.boot_completed=1`.

### Phase 0 — Repo, build, CI, docs
- `git init`, `.gitignore`, `.editorconfig`, `LICENSE` (Apache-2.0), `README.md`, `settings.gradle.kts`, root `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, wrapper, `:app`/`:core:theme`/`:core:search` build files with `ModuleWiringTest` smoke tests, `proguard-rules.pro`, `.github/workflows/{ci,release}.yml`, `checkReleaseApkSize` + `gmsGuard` tasks.
- Docs: `docs/research/2026-09-05-android-launcher-capabilities.md` (Parts 1–2 of the session-1 plan) + three appendices with the full research reports; `docs/decisions/0001-v1-scope-stack-distribution.md` (the five decisions, dated); `docs/design/` with the three planner reports from this session; `docs/themes/` placeholder.
- Acceptance: `./gradlew build` green; `assembleRelease checkReleaseApkSize gmsGuard` pass; two clean builds hash-identical; CI green; manifest grep shows zero `uses-permission`, one `<queries>` intent, no forbidden names.

### Phase 1 — Home skeleton
- `JustaApp`, `Threads`, `apps` (AppKey, AppEntry, AppRepository, AppLauncher), `settings` seam (`HomeSettings` projection + `DefaultSettingsSource`), `home` (HomeActivity, HomeBackCallback, HomeRootView, HomeListView, HomeListModel, ThemeApplier, DefaultHomePrompt/Banner), resources (themes, colors, strings, adaptive icon).
- Acceptance (API 26 + 36 AVDs): set as default via `cmd role add-role-holder` / `cmd package set-home-activity`; Home key → `onNewIntent`, one activity instance; Back ×3 never finishes; rotation, locale, night-mode and 200 % font-scale changes crash-free with correct insets and ≥ 48 dp rows; package add/remove/disable updates the list live; tap launches with clip-reveal; stale row → toast; banner logic; no hidden-API log lines; PSS and `am start -W` baselines recorded; release APK ≤ 2.5 MiB.

### Phase 2 — Settings + persistence
- `:core:theme` types, codec, lint/sanitizer, migrations skeleton, built-in themes; `SettingsStore`; settings screens (platform widgets): choose/order home apps, hidden apps, renames, night mode; long-press menu without shortcuts; backup export/import via SAF; `REQUEST_DELETE_PACKAGES`.
- Acceptance: fresh install writes defaults; kill during write bursts never corrupts (1,000-iteration script); corrupt file → defaults + `.corrupt-*`; backup round-trips on a second AVD with consents cleared; unknown keys/unknown `Action`/out-of-range values load with warnings and round-trip; release (minified) build decodes fixtures; settings change reaches the screen within one frame.

### Phase 3 — Gestures + action registry
- `GestureRecognizer`, `HomeView` touch ownership, `GestureDispatcher`, `Haptics`, `ActionRegistry` + all `BuiltinId` specs, picker UI, gesture settings, `edgeExclusion` opt-in, `SystemActionsBridge` stub (`Unsupported`).
- Acceptance: JVM scripted sequences for every event type; on device: swipe over a name works, tap/long-press on names behave, empty-area long press opens settings; gesture-nav edge behaviour per `edgeExclusion`; `Unsupported` actions hidden on API 26, present on 34; no-handler intents toast then grey out; SYSTEM actions open the disclosure when the service is off.

### Phase 4 — Search + drawer
- `:core:search` + `SearchOverlay` (drawer, keyboard behaviour, auto-launch guard, IME action, web-search fallback toggle, shortcuts sub-list).
- Acceptance: scorer test list ("gm" → Google Maps first, "cafe" ↔ "Café", CJK passthrough, Turkish-locale independence, empty/no-match, alias matching, determinism) + performance bound; CJK IME never auto-launches mid-composition; rotation on API 36/37 keeps the IME open; hidden apps never appear; renamed app found by both labels; 200 % font scale fine.

### Phase 5 — Notification badges (first user of the grant flow)
- `notifications` package, `GrantFlowScreen` + `GrantSpec(NOTIFICATION_ACCESS)`, `InstallSourceProbe`, `RestrictedSettingsHelpScreen`, badge rendering per theme.
- Acceptance: `BadgeCounterTest` matrix; grant lands on the right Settings screen on API 26 and 30+; counts within 1 s of grant; a burst of 5 posts → one re-render; revoke clears within 1 s; `am kill` + new notification repopulates; badge geometry at 100 %/200 % and all alignments; sideload simulation on 33/34 shows the help and succeeds after allowing restricted settings.

### Phase 6 — Background engine
- `:core:collections` + fixtures, `BackgroundEngine`, `BackgroundScheduler`/`BackgroundJobService`, `NetworkPolicy`, `ImageFetcher`, `ImageStore`, `ImageDecoders`, `ColorSampler`, `BackgroundView`, `BackgroundController`, settings status line, `next_background`; `INTERNET`, `ACCESS_NETWORK_STATE`.
- Acceptance: parser fixtures and detection order; `cmd jobscheduler run -f` rotates; unmetered/Data Saver skips recorded; 20 MiB image aborted; 6-hop redirect and https→http redirect fail; truncated JPEG marks bad and rotates on; `pm trim-caches` leaves the current image; light/dark images flip text colour and bar icons; Graphics delta ≤ 1.25 × window pixels × 4, Java heap delta < 1 MB, total PSS ≤ 50 MB at 1080×2400 (record, then tighten).

### Phase 7 — Themes
- `ThemeResolver`, full `ThemeApplier`, theme picker with 4 built-ins, token editor, theme export/share/receive/deep link with preview + confirm, golden `docs/themes/*.justatheme`; `share.ImportActivity` + `FileProvider`.
- Acceptance: each built-in renders on API 26 and 34 at 100 %/200 %; tokens resolve on 31+, fall back on 26–30; import via file manager, share sheet and `justa://theme?d=` all preview then apply; `schema: 99` refused, `minAppVersion` warns; collection URL asks confirmation naming the host, `http://` degrades with a warning; export → import byte-identical after normalisation; built-ins meet 4.5:1 contrast for text < 18 sp.

### Phase 8 — Accessibility, device admin, shade, home info, usage access, shortcuts
- `system` package (service in `:system`, config XML, `SystemActions`, `AccessibilityState`, `StatusBarShade`, `LockAdminReceiver`, `DeviceAdminLock`), `EXPAND_STATUS_BAR`; `home.info` lines + `UsageAccess` + `PACKAGE_USAGE_STATS`; `GrantSpec` for ACCESSIBILITY, DEVICE_ADMIN, USAGE_ACCESS; shortcuts in the long-press menu (`ShortcutProvider`); Play declaration draft + demo-video script.
- Acceptance: `:system` exists only while enabled, idle PSS < 15 MB, crashing it leaves Home alive and the role held; event-free config binds and performs on API 26/33/36 (else fallback adopted); gesture → action latency < 150 ms; lock via accessibility on ≥ 28 and device admin on 26; `removeActiveAdmin` then uninstall works; shade reflection succeeds with one hidden-API log line in debug, forced failure falls back correctly; info lines follow 12/24 h, midnight, `dumpsys battery set level`, and screen time within 1 s of grant; shortcuts appear only while default launcher.

### Phase 9 — Release engineering
- Final R8 config, `scripts/check-apk-size.sh`, `scripts/check-manifest-policy.sh`, `scripts/reproducible-check.sh`, `fdroid/metadata/<id>.yml`, fastlane text, `docs/release/{play-declarations,checklist}.md`, `docs/privacy.md`, signing env contract, Play App Signing decision.
- Acceptance: size gate fails with a 1 MiB dummy asset (negative test); two clean-container builds compare equal; policy gate fails when `QUERY_ALL_PACKAGES` is temporarily added; release build decodes default JSON on API 26 and 36; `analyzeReleaseR8Config` shows no unexpected keeps; StrictMode absent from the release DEX.

---

## Verification summary
- **JVM** (every push): `:core:theme` (round-trips, unknown keys, clamps, colour grammar, `Action` polymorphism + `Unknown`, migrations fixtures, built-ins golden, import merge rules), `:core:search` (ranking list, normalizer cases, tie-breaks, performance bound), `:core:collections` (fixtures, detection, URL rules), `GestureRecognizer` sequences, `HomeListModel`, `BadgeCounter`, `ScreenTimeCalculator`.
- **CI gates**: lint, size ≤ 2.5 MiB, `gmsGuard`, manifest/DEX policy, reproducible double-build, wrapper validation.
- **Emulator matrix** (v1 scope): API 26, 30, 33, 34, 35, 36 (+ 37 image if offered). Key rows: default-home + Home key + Back; insets/edge-to-edge (legacy flags on 26, enforced on 35+); 100 %/200 % font scale; NLS grant/count/revoke/rebind; restricted-settings help on 33/34 (ECM on 35+); accessibility binding + API-gated actions; device admin on 26/30/36; shade reflection on all; background job/policy/caps/cache/auto-colours (BitmapFactory on 26, ImageDecoder on 28+, memory limiter on 37); info lines; `dumpsys meminfo` and `am start -W` on 26/36/37.
- **Policy readiness**: disclosure screens exist before every grant; no `QUERY_ALL_PACKAGES`; accessibility declaration text + demo video prepared before the Play upload.

## Items to verify on a device early (unverified in research)
1. AGP 9.4 built-in Kotlin applies the serialization compiler plugin (Phase 0).
2. Event-free accessibility config binds and performs global actions on API 26/33/36 (Phase 8).
3. `<queries>` MAIN/LAUNCHER alone surfaces synthesized "app details" rows (Phase 1; class-name filter guards either way).
4. `RECEIVER_NOT_EXPORTED` delivery of `ACTION_LOCALE_CHANGED` on 14+ (Phase 1; `ensureFresh` covers the gap).
5. Single-Activity IME restore on the API 37 image (Phase 4; fallback `SearchActivity`).
6. `expandNotificationsPanel` reflection on API 37 (Phase 8).
7. F-Droid-installed builds tripping restricted settings on 13/14/15 (Phase 5).
8. `clearTaskOnLaunch` behaviour once a settings activity lives in the home task (Phase 2).

## Housekeeping when execution starts
- Save Claude memory notes for this project: decisions, plan file paths, toolchain state, and the location of the research/design reports.
