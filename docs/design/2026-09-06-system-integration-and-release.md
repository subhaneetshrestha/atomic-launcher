> Provenance: planning-time design written by a Plan agent on 2026-09-06 from the research note and the scoping decisions in ADR 0001, before any code existed. Copied verbatim from the agent transcript. Where the code and this document disagree, the code and its tests are authoritative; update this file or add an ADR.

# justa-launcher — System integration & compliance plan (Phases 5, 6, 8, 9)

Conventions used below
- `APP` = `/home/hyzii/Projects/justa-launcher/app/src/main/kotlin/org/justa/launcher` (placeholder package `org.justa.launcher`; rename when the package name is confirmed).
- `RES` = `/home/hyzii/Projects/justa-launcher/app/src/main/res`.
- Citations: "Plan §1.4" = the synthesis file; "Platform §C2" = agent-afae… report; "Background §C3" = agent-a1dc… report; "Stack §C.7" = bvouz6pzm.txt.
- Everything here runs in the main `:app` process unless stated; only the accessibility service runs in `:system`.

## 0. What I need from the other planners (interfaces, not designs)

From the scaffold/CI owner (Phase 0–1)
- A pure-Kotlin module `:core:collections` (`/home/hyzii/Projects/justa-launcher/core/collections`) depending only on `kotlinx-serialization-json`, with JVM tests. If a third core module is unwanted, the parsers can live in `:app` with `unitTests.isReturnDefaultValues = true`, but SAX/JSON parsing is easier to test outside android.jar.
- Manifest permissions (all normal or app-op): `INTERNET`, `ACCESS_NETWORK_STATE`, `EXPAND_STATUS_BAR`, `PACKAGE_USAGE_STATS` (with `tools:ignore="ProtectedPermissions"`). Not declared: `SET_WALLPAPER`, `QUERY_ALL_PACKAGES`, `ACCESS_HIDDEN_PROFILES`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` (see §8, disagreement 1).
- `Application` subclass with a process-name guard (`Application.getProcessName()` on 28+, `ActivityManager.getRunningAppProcesses()` on 26–27) so the `:system` process skips settings load, badge store and job scheduling.
- Home theme: `windowShowWallpaper=true` + `windowBackground=@android:color/transparent` (Launcher3 pattern, Plan §1.2/§1.7); `BackgroundController` toggles the flag at runtime (§3.1).
- CI hooks: run `scripts/check-apk-size.sh`, `scripts/check-manifest-policy.sh`, `scripts/reproducible-check.sh` (§6).

From the settings/theme owner (settings document + theme JSON)
- Settings (user data, not in shared themes): `badges.enabled: Boolean`, `badges.includeOngoing: Boolean` (default false), `badges.perApp: Map<packageName, Boolean>`; `background.collection.url: String?`, `background.collection.intervalMinutes: Int` (≥15, default 60), `background.collection.unmeteredOnly: Boolean` (default true), `background.collection.order: random|sequential`; `info.screenTime.enabled`, `info.battery.enabled`, `info.clock.enabled`, `info.date.enabled`; `system.accessibilityConsented: Boolean`, `system.deviceAdminConsented: Boolean`, `grants.<kind>.disclosureAcceptedAt: Long`.
- Theme tokens: `badge.style: circle|dot|number|none`, `badge.scale: Float` (default 0.62), `badge.position: start|end`, `badge.background: color|auto`, `badge.text: color|auto`; `background.mode: system|solid|gradient|image`, `background.solid.color`, `background.gradient.colors[2..4]`, `background.gradient.angle`, `background.dim: 0..1`, `background.scrim: color?`, `background.fallbackColor`, `background.transition: none|fade`, `text.color: color|auto`; per info line (`clock`, `date`, `battery`, `screenTime`): `visible`, `format`, `textSize`, `color|auto`, `font`, `placement: above|below`, `order`.
- Theme import must treat `background.collection.url` (if a theme carries one) as a suggestion requiring an explicit tap ("Use this image source?"); it triggers network fetches.

From the action-registry/gestures owner
- Action IDs implemented by `:app/system` (`SystemActions.perform(id)`): `system.lock_screen`, `system.notifications`, `system.quick_settings`, `system.recents`, `system.power_dialog`, `system.screenshot`, `system.dismiss_shade`; by `:app/background`: `background.next`; by `:app/settings`: `settings.grant.<kind>` (opens the grant flow). Intent actions I rely on for defaults: `intent.alarms`, `intent.calendar`, `intent.battery_settings` (`ACTION_POWER_USAGE_SUMMARY`), `intent.usage_access_settings`.
- Info lines are gesture surfaces named `clock`, `date`, `battery`, `screenTime` with `tap`/`longPress` bindings; the registry must expose `ActionAvailability(id) -> Available | NeedsGrant(kind) | Unsupported(api)` so the gesture editor can show "needs Accessibility" inline.

From the home owner
- `HomeRow` accepts a `BadgeDrawable?` as the start/end compound drawable (rows are `wrap_content` width so the badge hugs the label), and `HomeActivity` forwards `onStart/onStop/onTrimMemory` and window-inset callbacks to `BackgroundController`, `BadgeStore`, `BatteryLine`, `ScreenTimeLine`.

## 1. Notification badges (`APP/notifications/`)

Files
- `BadgeNotificationListener.kt` — the `NotificationListenerService`; owns nothing but forwarding to `BadgeCounter`.
- `BadgeCounter.kt` — pure counting rules over a small `NotificationFacts` value type (flags, channelId, canShowBadge, isSuspended, hasTitleOrText, number, isOngoing, category, packageName, userId); JVM-tested.
- `BadgeStore.kt` — main-thread `Map<PackageUserKey, Int>` + listeners + coalescing.
- `BadgeDrawable.kt` — Paint-only drawable (circle/pill, dot, plain number).
- `NotificationAccess.kt` — grant check, Settings intents, low-RAM detection, rebind backoff.

Manifest (follows the reference example verbatim, Platform §C1; runs in the main process so the store is shared in memory):
```xml
<service android:name=".notifications.BadgeNotificationListener"
    android:label="@string/notification_listener_label"
    android:exported="false"
    android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">
  <intent-filter>
    <action android:name="android.service.notification.NotificationListenerService"/>
  </intent-filter>
</service>
```
No `default_filter_types`/`disabled_filter_types` meta-data in v1: declaring `disabled_filter_types="ongoing"` would make the in-app `badges.includeOngoing` toggle impossible (Platform §C1: disabled types appear "off and disabled" in the UI).

Grant flow (`NotificationAccess`)
- API 30+: `Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` with `EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME` = flattened component; on `ActivityNotFoundException` or API < 30: `ACTION_NOTIFICATION_LISTENER_SETTINGS` (Platform §C1).
- Granted check: `NotificationManager.isNotificationListenerAccessGranted(cn)` (API 27); on API 26 only, parse `Settings.Secure.getString(cr, "enabled_notification_listeners")` (public method, undocumented key; confined to one function).
- Low-RAM: if `ActivityManager.isLowRamDevice()` and API ≤ 29, the badges setting is disabled with the explanation that the system does not bind listeners on such devices (Platform §C1).

Lifecycle
- `onListenerConnected` → `connected = true`; `getActiveNotifications()` + `getCurrentRanking()` inside try/catch(SecurityException) → `BadgeCounter.rebuild` → `BadgeStore.replaceAll` (one publish).
- `onNotificationPosted(sbn, rankingMap)` / `onNotificationRemoved(sbn, rankingMap, reason)` → update the key map → recompute the affected `(package, user)` → `BadgeStore.update`.
- `onNotificationRankingUpdate(rankingMap)` → re-evaluate `canShowBadge` for all tracked keys (the user can toggle a channel's badge while we run).
- `onListenerDisconnected` → `connected = false`; `BadgeStore.clear()`; one `requestRebind(cn)`; `NotificationAccess` retries at most once per minute from `HomeActivity.onStart` while the grant check still says granted.
- Process death / boot: the system rebinds granted listeners itself and `onListenerConnected` rebuilds everything from `getActiveNotifications()`; nothing is persisted and no boot receiver exists.
- Revoked at runtime: disconnect → counts cleared → `requestRebind` fails silently (Platform §C1: fails "for listeners that have not been granted the permission") → on next `onStart` the grant check is false → badges hidden, settings row shows "Notification access: off — tap to re-enable"; `badges.enabled` keeps the user's preference; the home screen never nags.

Counting rules (Launcher3, Platform §C2; implemented in `BadgeCounter.isCountable`)
1. drop if `flags & FLAG_GROUP_SUMMARY`;
2. drop if no ranking for the key or `!ranking.canShowBadge()` (this is also the CDD `setShowBadge` requirement);
3. drop if `channel.id == NotificationChannel.DEFAULT_CHANNEL_ID` and `flags & FLAG_ONGOING_EVENT` (legacy "miscellaneous" channel);
4. drop if `EXTRA_TITLE` and `EXTRA_TEXT` are both empty (only emptiness is checked; text is never stored — Android 15 OTP redaction is irrelevant);
5. additions from Platform §C2's recommendation: drop if `ranking.isSuspended()` (API 28); drop ongoing/`FLAG_FOREGROUND_SERVICE`/`CATEGORY_TRANSPORT` unless `badges.includeOngoing` (ongoing notifications fluctuate more since Android 14 made them dismissable, Platform §C5).
Count per `(packageName, sbn.user)` = `min(999, Σ max(1, notification.number))`. `BadgeStore.publish` applies `badges.perApp` (false → 0) so the UI is dumb.

Store → UI path
- All NLS callbacks arrive on the main thread on API 24+ (Platform §C1), so `BadgeStore` is unsynchronized main-thread state. Updates coalesce through one `Handler` runnable (`removeCallbacks` + `postDelayed(16 ms)`); the connect-time rebuild publishes immediately. Listeners (`HomeActivity`) register in `onStart` (receiving a snapshot) and unregister in `onStop`. Lookup: `BadgeStore.countFor(packageName, user)`; the home list is keyed by `(ComponentName, UserHandle)`, badges by package, so a package with two home rows shows the same count on both (Launcher3 behavior).

Rendering (`BadgeDrawable`)
- Intrinsic size derives from the row's resolved text size in px (so it follows the 200 % font scale without touching `fontScale`, Plan §1.8): number text = `textPx × badge.scale`; circle diameter = number height + 2 × 0.2·textPx, widening into a pill for 2–3 digits; dot diameter = 0.35·textPx; plain number uses the row typeface. Colors from `badge.background/text`, `auto` = swap of the resolved row text color and a contrasting fill. No image assets, `ANTI_ALIAS_FLAG`, one `Paint` reused. Row `minHeight` stays 48 dp because the badge never exceeds ~1.1·textPx unless text itself is taller than 48 dp.
- Optional, best-effort: if `Settings.Secure.getInt(cr, "notification_badging", 1) == 0` (system "notification dots" off; undocumented key Launcher3 uses), hide badges.

## 2. Accessibility service, device-admin fallback, shade reflection (`APP/system/`)

Files
- `SystemActionsService.kt` (`AccessibilityService`, process `:system`) — receives explicit `startService` intents and calls `performGlobalAction`; nothing else.
- `SystemActions.kt` (main process) — routes each `system.*` action: capability check → route → fallback → `NeedsGrant`.
- `AccessibilityState.kt` — enabled detection, Settings intent, `AccessibilityStateChangeListener`.
- `StatusBarShade.kt` — reflection on `android.app.StatusBarManager.expandNotificationsPanel`.
- `LockAdminReceiver.kt`, `DeviceAdminLock.kt`, `RES/xml/device_admin_policies.xml`.
- `RES/xml/accessibility_service_config.xml`, `ProcessUtil.kt`.

Manifest (Platform §D1 declaration pattern + Olauncher's own-process pattern, Stack §A.2):
```xml
<service android:name=".system.SystemActionsService"
    android:label="@string/accessibility_service_label"
    android:process=":system"
    android:exported="true"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
  <intent-filter>
    <action android:name="android.accessibilityservice.AccessibilityService"/>
  </intent-filter>
  <meta-data android:name="android.accessibilityservice"
      android:resource="@xml/accessibility_service_config"/>
</service>
```
```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_description"
    android:summary="@string/accessibility_service_summary"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault"
    android:canRetrieveWindowContent="false"
    android:packageNames="org.justa.launcher"
    android:isAccessibilityTool="false"/>
```
`accessibilityEventTypes` is deliberately omitted so `eventTypes` parses to 0 and no events are ever dispatched; the guide documents no explicit minimum (Platform §D1, UNVERIFIED), so the Phase 8 acceptance test verifies binding + `performGlobalAction` with this config on API 26/33/36 and, if it fails, falls back to `typeViewClicked` scoped by `packageNames` (Olauncher's config). `isAccessibilityTool="false"` is what Play requires for launchers and makes the system post a periodic privacy notification about the service (Platform §D1) — the disclosure text must mention that notification. Also `setServiceInfo` in `onServiceConnected` to reassert `eventTypes = 0`.

Global actions and gates (Platform §D1): `GLOBAL_ACTION_NOTIFICATIONS` 16, `QUICK_SETTINGS` 17, `RECENTS` 16, `POWER_DIALOG` 21, `LOCK_SCREEN` 28, `TAKE_SCREENSHOT` 28 (the global action, not the `takeScreenshot()` API, so no `canTakeScreenshot`), `DISMISS_NOTIFICATION_SHADE` 31. With minSdk 26: `system.lock_screen` and `system.screenshot` are `Unsupported(api)` for the accessibility route on 26–27; `system.dismiss_shade` on < 31.

Cross-process invocation (recommendation: explicit `startService`)
- `SystemActions` calls `context.startService(Intent(context, SystemActionsService::class.java).setAction(ACTION_PERFORM).putExtra(EXTRA_GLOBAL_ACTION, id).putExtra(EXTRA_ISSUED_AT, elapsedRealtime))`; the service's `onStartCommand` drops intents older than 2 s, calls `performGlobalAction`, then `stopSelf(startId)` and returns `START_NOT_STICKY`, so it returns to a purely system-bound state and dies when the user disables it. Same-UID callers bypass the signature `permission` check, other apps cannot start it.
- Why not a static instance: impossible across processes. Why not a broadcast: works (`RECEIVER_NOT_EXPORTED` receiver registered in `onServiceConnected`) but adds a second component and Android 14 broadcast queuing semantics; keep as fallback if `startService` shows latency > 100 ms on API 36.
- Trade-off of `:system`: +1 resident process (~5–10 MB PSS) only while the user has the service enabled, one extra `Application.onCreate`, IPC latency of one binder hop; in exchange a crash in privileged code cannot kill the HOME process, which would silently drop default-launcher status (Plan §1.2).
- `SystemActions` never calls `startService` unless `AccessibilityState.isEnabled()`; an unbound instance's `performGlobalAction` would just fail.

Enable flow and detection
- `Settings.ACTION_ACCESSIBILITY_SETTINGS` (no public per-component detail intent) + on-screen steps "Accessibility → Downloaded/Installed apps → justa → On → Allow".
- Detect: `AccessibilityManager.getEnabledAccessibilityServiceList(FEEDBACK_ALL_MASK)` matching our component; cross-check `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`; live updates via `addAccessibilityStateChangeListener` for the settings screen.

Routing (`SystemActions`)
- `system.notifications`: accessibility if enabled → else `StatusBarShade.expand()` → else `NeedsGrant(accessibility)`. Rationale: use the supported API whenever the user has enabled it; the hidden API only serves users who have not (Plan §1.6: "works today… conditionally blocked in future"; flags `test-api,unsupported` in the 15 and 16 lists).
- `system.lock_screen`: API ≥ 28 and accessibility enabled → `GLOBAL_ACTION_LOCK_SCREEN`; else device admin active → `DevicePolicyManager.lockNow()` in try/catch(SecurityException); else a chooser explaining both options.
- `quick_settings`, `recents`, `power_dialog`, `screenshot`, `dismiss_shade`: accessibility only.

`StatusBarShade` (reflection): `context.getSystemService("statusbar")`, `Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)`; holds `EXPAND_STATUS_BAR` (normal); catches `Throwable` (NoSuchMethodException on blocklisting, InvocationTargetException, SecurityException) and returns false. Debug builds set `StrictMode.VmPolicy.detectNonSdkApiUsage().penaltyLog()` (API 28) in `Application.onCreate`; release builds never enable StrictMode. No R8 keep rule is needed because the target is a framework class, not app code. Expect the Play pre-launch report to list the call (Plan §7); that is the known cost of keeping this fallback.

Device admin (Platform §D2)
```xml
<receiver android:name=".system.LockAdminReceiver"
    android:label="@string/device_admin_label"
    android:description="@string/device_admin_description"
    android:permission="android.permission.BIND_DEVICE_ADMIN"
    android:exported="true">
  <meta-data android:name="android.app.device_admin" android:resource="@xml/device_admin_policies"/>
  <intent-filter><action android:name="android.app.action.DEVICE_ADMIN_ENABLED"/></intent-filter>
</receiver>
```
`device_admin_policies.xml` is exactly `<device-admin><uses-policies><force-lock/></uses-policies></device-admin>`. Enable via `ACTION_ADD_DEVICE_ADMIN` + `EXTRA_DEVICE_ADMIN` + `EXTRA_ADD_EXPLANATION`; check `isAdminActive(cn)`; disable via `removeActiveAdmin(cn)` from our settings (an active admin blocks uninstall, so the settings row must offer "Deactivate"). Disclosure must state the strong-auth consequence: after `lockNow()` the device requires PIN/pattern/password, biometrics will not unlock (Platform §D2). When to offer: API 26–27 always (only lock route); API ≥ 28 as the alternative for users who decline the accessibility service. Device admin is partially deprecated since Android 9 (Plan §1.6); `force-lock`'s deprecation status is unverified (Background report open question 11).

Play accessibility declaration checklist (Platform §D1; policy answer/10964491)
- `isAccessibilityTool=false` in the config; Play Console → App content → Accessibility API declaration: purpose ("perform user-initiated global actions bound to home-screen gestures: lock screen, open notifications/quick settings/recents/power menu, screenshot; reads no screen content: `canRetrieveWindowContent=false`, no event types"), target users (all users; not an assistive tool), and a demo video showing disclosure → consent → enabling in Settings → a gesture performing the action.
- Listing text documents the use ("Optional: uses the Accessibility API to lock the screen or open the notification shade from gestures. Off by default.") — the policy requires the use to be "documented in the Google Play listing".
- In-app prominent disclosure with affirmative consent before opening Settings (§5); narrower APIs used where they exist (device admin for lock, the `EXPAND_STATUS_BAR` path for the shade).

## 3. In-window background engine (`APP/background/` + `:core:collections`)

### 3.1 Modes and window switching (`BackgroundController`, `BackgroundView`)
- Theme keeps `windowShowWallpaper=true` + transparent `windowBackground`; a transparent background already yields a translucent surface. `BackgroundController.applyMode()` runs in `onCreate` before `setContentView` and on theme change: `system` → `window.addFlags(FLAG_SHOW_WALLPAPER)`, view draws nothing; `solid|gradient|image` → `window.clearFlags(FLAG_SHOW_WALLPAPER)` and `window.setBackgroundDrawable(ColorDrawable(fallbackColor))` so the first frame does not flash the system wallpaper.
- `BackgroundView : View` is the root's first child; `draw()` paints color, `LinearGradient` (angle → shader on size change), or a center-crop bitmap via matrix, then `dim` (black with alpha) and optional `scrim`. `transition=fade` crossfades old/new bitmaps for 250 ms via `paint.alpha`; the old bitmap is dropped at the end.
- Auto colors: `ColorSampler` produces `Legibility(darkTextForList, lightStatusIcons, lightNavIcons)` from three strips (top 12 %, list band, bottom 12 %) of a ≤ 64×64 software thumbnail; `ThemeApplier` (theme owner) consumes it when `text.color = auto`; system bars via `WindowInsetsControllerCompat.setAppearanceLightStatusBars/NavigationBars` (androidx.core handles API 26–29). Passthrough mode on API 27+: `WallpaperManager.getWallpaperColors(FLAG_SYSTEM)` off the main thread + `addOnColorsChangedListener`, using `HINT_SUPPORTS_DARK_TEXT` on 31+ and primary-color luminance below (Background §C1/§D3; API 26: no auto color, theme default applies).

### 3.2 Collection sources (`/home/hyzii/Projects/justa-launcher/core/collections/src/main/kotlin/org/justa/collections/`)
- `ImageRef(url, mime?, sizeHint?)`, `Collection(items, kind, refreshedAt)`, `CollectionParser` implementations: `TextListParser` (one URL per line, `#` comments, relative URLs resolved against the list URL), `JsonListParser` (array of strings, or of objects with `url`), `FeedParser` (SAX via `javax.xml.parsers`; RSS 2.0 `<enclosure url type=image/*>`, Media RSS `<media:content url medium=image|type=image/*>` then `<media:thumbnail>` as last resort, Atom `<link rel="enclosure" type="image/*" href>`; `<img>` inside HTML bodies is not parsed), `WallhavenParser` (`data[].path`), `WallhavenUrl` (rewrites `https://wallhaven.cc/search?…` to `/api/v1/search?…`, forces `purity=100`, keeps `categories/sorting/atleast/ratios/seed/page`), `UrlRules` (https only, max 2048 chars, no userinfo).
- `SourceDetector.detect(url, contentType, headBytes)`: (1) host `wallhaven.cc` with `/api/v1/` or `/search` → Wallhaven; (2) `Content-Type: image/*` or magic bytes (JPEG `FF D8 FF`, PNG `89 50 4E 47`, `RIFF….WEBP`, ISO-BMFF `ftyp` with `heic/heix/mif1/avif/avis`) → direct image; (3) first non-whitespace byte `[`/`{` → JSON (object with `data[].path` → Wallhaven shape); (4) `<` → XML, root `rss` or `feed`; (5) else plain text; zero valid URLs → `EmptyCollection`. Items are deduplicated and capped at 500.
- Index documents are cached (`cacheDir/background/index.json`, TTL 6 h or until exhausted) so a rotation is normally one request; a per-host minimum interval of 2 s keeps Wallhaven far under 45 req/min (Background §C5).
- JVM tests: `core/collections/src/test/kotlin/...` with fixtures `src/test/resources/collections/{list.txt,list.json,rss.xml,mediarss.xml,atom.xml,wallhaven.json,garbage.bin}`, detection-order cases, relative-URL resolution, http rejection, NSFW purity stripping.

### 3.3 Scheduling and constraints (`BackgroundScheduler`, `BackgroundJobService`, `NetworkPolicy`)
- `JobInfo.Builder(JOB_ID_ROTATE, cn)`: `setPeriodic(max(intervalMs, JobInfo.getMinPeriodMillis()), flex = period/5)`, `setRequiredNetworkType(UNMETERED if unmeteredOnly else ANY)`, `setRequiresBatteryNotLow(true)`, `setPrefetch(true)` on 28+ (`PRIORITY_LOW` on 33+); not persisted (see §8.1); rescheduled idempotently at app start and when settings change (`getPendingJob(id)` compared to desired params; `schedule()` is throttled if spammed, Background §C3). `ACCESS_NETWORK_STATE` is mandatory for network-constrained jobs on 14+ (Background §C3).
- `BackgroundJobService.onStartJob` hands off to `BackgroundEngine.refresh(reason=JOB, network = params.network on 28+)` on the engine's single-thread executor and calls `jobFinished(params, false)`; `onStopJob` sets the cancel flag, closes the connection, returns true. Manifest: `<service android:name=".background.BackgroundJobService" android:permission="android.permission.BIND_JOB_SERVICE" android:exported="false"/>`.
- On-resume path: `HomeActivity.onStart` → `engine.onForeground()`; if `now - state.lastChangeAt ≥ interval` (or the clock went backwards) → `refresh(RESUME)`. This covers App Standby buckets that cut jobs to once a day with no network (Rare/Restricted, Background §C3) because the Home app is resumed constantly.
- `NetworkPolicy.allows(reason)`: skip if `ConnectivityManager.getRestrictBackgroundStatus() == RESTRICT_BACKGROUND_STATUS_ENABLED` and the active network is metered (JOB only; the RESUME path is foreground); skip if `unmeteredOnly && isActiveNetworkMetered()`; skip RESUME refreshes when `PowerManager.isPowerSaveMode()` or battery ≤ 15 %. All skips record `lastSkipReason` for the settings status line.

### 3.4 Fetch, decode, cache (`ImageFetcher`, `ImageDecoders`, `ImageStore`)
- `HttpURLConnection` with `instanceFollowRedirects=false` and manual redirects (max 5, HTTPS only on every hop), connect 10 s / read 20 s, `User-Agent: justa-launcher/<versionName> (Android <sdk>)`, `Accept: image/avif,image/webp,image/*;q=0.8`, `Content-Length` precheck and streamed byte counting with caps 15 MiB (image) / 1 MiB (index), conditional GET (`ETag`/`Last-Modified`) for direct-image sources (304 = no change). Cleartext is never attempted: `http://` fails in `UrlRules` with a user-facing message; no network-security-config exceptions (Background §C4).
- Download to `cacheDir/background/tmp-<nonce>.part`, then atomic rename into `filesDir/background/current.img` (previous becomes `previous.img`), because `cacheDir` may be purged by the system (Background §C4). `state.json` (kotlinx.serialization): `currentUrl`, `lastCheckAt`, `lastChangeAt`, `etag`, `lastModified`, `history` (last 20 URL hashes for random-without-repeat), `badUrls` (ring of 50), `failures`, `nextAllowedAt`, `lastError`, `exifRotation`. Eviction: only `current.img`, `previous.img` and the tmp file exist; total ≤ 32 MiB and ≤ `StorageManager.getCacheQuotaBytes` for the cache part.
- Decode on the engine thread from the file: API 28+ `ImageDecoder.createSource(file)` + `setTargetSize(cover window bounds)` + `ALLOCATOR_HARDWARE` + `setOnPartialImageListener { false }` (truncated → bad URL), `MEMORY_POLICY_LOW_RAM` on `isLowRamDevice()`; API 26–27 `BitmapFactory` with `inJustDecodeBounds` → power-of-two `inSampleSize` → `inPreferredConfig = HARDWARE`, plus `android.media.ExifInterface` for rotation (ImageDecoder applies EXIF itself). Formats: JPEG/PNG/WebP everywhere, HEIF from API 26 (codec-dependent), AVIF mandatory from 34 (Background §C4); `ImageDecoder.isMimeTypeSupported` on 29+ pre-checks the `Content-Type`; unsupported → mark bad, pick another.
- `WallpaperColors.fromBitmap` and any pixel read need a software bitmap (pixel access throws on `Config.HARDWARE`), so `ColorSampler` decodes a separate ≤ 64×64 software thumbnail and recycles it.
- Memory budget: one hardware bitmap ≤ windowW×windowH×4 bytes (≈ 10 MB at 1080×2400, in graphics memory), two only during the 250 ms fade; Java-heap footprint of the engine < 1 MB; the bitmap is dropped on `onTrimMemory(TRIM_MEMORY_BACKGROUND|COMPLETE)` and re-decoded on `onStart`. Rotation re-decodes only if the bitmap no longer covers the window.
- Failures: any fetch/parse/decode failure increments `failures`, sets `nextAllowedAt = now + min(interval × 2^failures, 24 h)`, stores `lastError`; the periodic job still fires on schedule but bails early until `nextAllowedAt`; after 3 decode failures the URL enters `badUrls`. First run without an image shows `background.fallbackColor`. Errors surface only in the settings status line; nothing is posted as a notification.
- `background.next` → `engine.refresh(USER)` (ignores `nextAllowedAt`, still respects `unmeteredOnly` with a Toast "Waiting for Wi-Fi").

## 4. Home info lines (`APP/home/info/`)
- `ClockLine.kt`: platform `TextClock`; `format = auto` leaves both formats null so the locale default and the system 12/24 h setting apply; otherwise `clock.format12`/`format24` (validated: pattern letters, punctuation, quoted literals, ≤ 32 chars) via `setFormat12Hour/24Hour` — TextClock still selects by the system setting (Background §D6).
- `DateLine.kt`: `DateTimeFormatter.ofPattern(theme.date.format ?: DateFormat.getBestDateTimePattern(locale, "EEEEMMMMd"), locale)` on `LocalDate.now()`; refreshed in `onStart` and by one `Handler` post scheduled for the next local midnight while started; also on `ACTION_LOCALE_CHANGED`/`ACTION_TIMEZONE_CHANGED` (runtime receiver, `RECEIVER_NOT_EXPORTED`).
- `BatteryLine.kt`: sticky `ACTION_BATTERY_CHANGED` via `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` in `onStart`, unregistered in `onStop`; level = `EXTRA_LEVEL×100/EXTRA_SCALE`; format tokens `{level}`, `{charging}` (theme supplies the charging suffix text).
- `ScreenTimeLine.kt` + pure `ScreenTimeCalculator.kt` (JVM-tested): "today's total" = union of foreground intervals of all packages except our own, clipped to [local midnight, now], from `UsageStatsManager.queryEvents(midnight, now)` pairing `ACTIVITY_RESUMED`(1)/`ACTIVITY_PAUSED`(2)/`ACTIVITY_STOPPED`(23) per package (daily buckets are avoided for accuracy, Platform §E). Runs on a background executor, refreshed on `onStart` and once per minute while visible; `queryEvents` returning null while locked (API 30+) keeps the last value. `UsageAccess.kt`: manifest `PACKAGE_USAGE_STATS`, grant via `Settings.ACTION_USAGE_ACCESS_SETTINGS`, check `AppOpsManager.checkOpNoThrow(OPSTR_GET_USAGE_STATS, uid, pkg)` (`MODE_ALLOWED`, or `MODE_DEFAULT` + `checkSelfPermission`) in a single `@Suppress("DEPRECATION")` helper because the preferred method name flips between API 29 and 36 (Platform §E).
- Theming/tapping: each line is a `TextView` styled from its token group and registered with the gesture owner as surface `clock|date|battery|screenTime`; defaults `clock.tap = intent.alarms`, `date.tap = intent.calendar`, `battery.tap = intent.battery_settings`, `screenTime.tap = intent.usage_access_settings`. Lines pad by the same insets as the list and keep ≥ 48 dp height.

## 5. Consent and help flows (`APP/settings/grant/`)
- `GrantSpec(kind: NOTIFICATION_ACCESS|ACCESSIBILITY|USAGE_ACCESS|DEVICE_ADMIN, titleRes, dataRes, purposeRes, sharingRes, consequencesRes?, settingsIntents: List<() -> Intent>, isGranted: () -> Boolean, restrictedSettingsApplies: Boolean)`; `GrantFlowScreen` renders disclosure → "Continue"/"Not now" → launches the first intent that resolves (catching `ActivityNotFoundException`) → on the host's `onStart` verifies `isGranted()` → success (feature setting flips on) | not granted (retry, or the restricted-settings help when applicable). Copy lives in `RES/values/strings_grants.xml`; `grants.<kind>.disclosureAcceptedAt` is recorded.
- Wording constraints (Play User Data policy, answer/10144311, as quoted in Platform §C3): the disclosure is inside the app, describes the data accessed or collected, explains how it is used and/or shared, and consent requires an affirmative action (tap to accept or a checkbox). Additional constraints from the same policy page (not quoted in the report; re-read when writing final copy): shown immediately before the Settings hand-off, in normal app flow rather than buried in a menu, not only in the privacy policy/ToS, not mixed with unrelated disclosures, back/tap-away is not consent, no auto-dismissing text. Per kind the text must cover: notification access — reads which app posted a notification, how many, and whether it may be badged; checks only whether text exists; never stored, displayed or transmitted; accessibility — reads no screen content (`canRetrieveWindowContent=false`), performs only the actions the user binds to gestures, can be turned off any time, and Android will periodically show a system notification about the service; usage access — reads app usage history (which apps were in the foreground and for how long) to show today's screen time, on-device only; device admin — can force-lock the device, after which a PIN/pattern/password is required, and must be deactivated before uninstalling.
- Restricted settings help (`RestrictedSettingsHelpScreen`, `InstallSourceProbe`): applies when API ≥ 33 and the grant verification fails. `InstallSourceProbe.likelyRestricted()`: API 33+ `PackageManager.getInstallSourceInfo(pkg).packageSource ∈ {PACKAGE_SOURCE_LOCAL_FILE, PACKAGE_SOURCE_DOWNLOADED_FILE}` → yes; installer `com.android.vending` → no; any other or null installer (browser + system installer, F-Droid, GitHub download) → "possibly" on 33–34, and on 35+ also because ECM guards non-allowlisted installers (Platform §C4). API 30–32: `getInstallSourceInfo` exists but restricted settings do not apply. The screen shows Google's steps "Settings → Apps → justa → More (⋮) → Allow restricted settings" with a button opening `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` for `package:org.justa.launcher`, notes that the menu item may appear only after one failed attempt to enable the toggle in Settings, then returns to the grant step.
- GMS-free devices and F-Droid installs: identical flows (all intents are AOSP Settings actions; OEM Settings lacking `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` fall back to the list page); the probe reports `org.fdroid.fdroid`/`org.fdroid.basic` or null as installer, so the help is offered conditionally; the in-app disclosures show regardless of store. Whether F-Droid installs actually trip restricted settings is unverified (Plan §1.14 item 4) and is in the emulator matrix.

## 6. Release engineering (Phase 9)
- R8: full mode is the AGP 9 default (Stack §C.7); `isMinifyEnabled = true`, `isShrinkResources = true` (or the AGP 9.3+ optimization DSL with `app/src/release/keepRules/`). Keep rules actually needed: none expected — manifest components get aapt2-generated rules, kotlinx-serialization ships R8 consumer rules (Stack §C.5), and the shade reflection targets a framework class. Add only if the release smoke test (decode the default theme/settings JSON on a release build) fails, then the documented full-mode pair for `@Serializable` companions in `org.justa.**`. Run `:app:analyzeReleaseR8Config` once per release.
- Manifest/DEX policy gate (`scripts/check-manifest-policy.sh`): fail if the merged release manifest contains `QUERY_ALL_PACKAGES`, `SET_WALLPAPER`, `ACCESS_HIDDEN_PROFILES`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, or if `apkanalyzer dex packages` lists `com.google.android.gms`/`com.google.firebase` (GMS-free guarantee).
- Size gate (`scripts/check-apk-size.sh`): `assembleRelease` APK file size warn > 2.3 MiB, fail > 2,621,440 bytes; print `apkanalyzer apk file-size` and `download-size`.
- StrictMode: debug
-only `VmPolicy.detectNonSdkApiUsage().detectLeakedClosableObjects().penaltyLog()` and `ThreadPolicy.detectAll().penaltyLog()` in `Application.onCreate`, with the intentional startup settings read wrapped in `StrictMode.allowThreadDiskReads()`; release builds never touch StrictMode.
- Reproducible-build hygiene for F-Droid (Stack §C.7/E.3, f-droid.org/docs/Reproducible_Builds): pin AGP 9.4.0 / Gradle 9.7.1 (wrapper with `distributionSha256Sum`) / Kotlin 2.4.10 / build-tools in `gradle/libs.versions.toml`; no build-time values (`versionCode`/`versionName` literal in `app/build.gradle.kts`, no git hash or date in `BuildConfig`, `buildConfig` feature off if unused); `isCrunchPngs = false`; `dependenciesInfo { includeInApk = false; includeInBundle = false }` (Google-encrypted dependency metadata block is not reproducible); no PNG generation from vectors (moot at minSdk 26); `android.enableR8.fullMode` stated explicitly in `gradle.properties`; single flavor (§8.2). `scripts/reproducible-check.sh` builds `assembleRelease` twice in clean containers and compares with `apksigcopier compare` (signature-independent); F-Droid then rebuilds and, when identical, publishes our signed APK (`AllowedAPKSigningKeys` + `Binaries`).
- F-Droid metadata skeleton, kept at `/home/hyzii/Projects/justa-launcher/fdroid/metadata/org.justa.launcher.yml` for the fdroiddata merge request:
```yaml
Categories: [System]
License: Apache-2.0
SourceCode: https://github.com/<owner>/justa-launcher
IssueTracker: https://github.com/<owner>/justa-launcher/issues
Changelog: https://github.com/<owner>/justa-launcher/blob/HEAD/CHANGELOG.md
AutoName: justa
RepoType: git
Repo: https://github.com/<owner>/justa-launcher.git
Builds:
  - versionName: 1.0.0
    versionCode: 10000
    commit: v1.0.0
    subdir: app
    gradle: [yes]
AllowedAPKSigningKeys: <sha256 of the release signing certificate>
AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$
Binaries: https://github.com/<owner>/justa-launcher/releases/download/v%v/justa-launcher-%v.apk
```
  Listing text as fastlane files under `/home/hyzii/Projects/justa-launcher/fastlane/metadata/android/en-US/` (`short_description.txt`, `full_description.txt`, `changelogs/<versionCode>.txt`), shared verbatim with the Play listing so the accessibility sentence is identical in both stores.
- Play declarations (`/home/hyzii/Projects/justa-launcher/docs/release/play-declarations.md`): Accessibility API declaration (§2); Data safety: no data collected or shared (notification metadata and usage events are processed on-device and never transmitted; fetches go to user-chosen URLs — re-read the form's definition of "collected" before submitting), encryption in transit yes (HTTPS only); no `QUERY_ALL_PACKAGES` so no package-visibility form; `PACKAGE_USAGE_STATS` coverage by the Permissions Declaration Form is unverified (Platform §E) — check the console; privacy policy URL required (host `docs/privacy.md` on GitHub Pages, linked in-app from Settings → About); category Personalization; target API 36.
- Signing: release keystore outside the repo (`~/.config/justa/release.jks`), read via env vars `JUSTA_KEYSTORE_PATH/PASSWORD`, `JUSTA_KEY_ALIAS/PASSWORD`; CI stores the keystore base64 in a secret, decodes to a temp path, and signs with `apksigner` (v2+v3). Play requires Play App Signing; enrol with "use an existing key" (upload our release key) so Play- and GitHub/F-Droid-installed APKs share one certificate and can update each other — otherwise users must uninstall to switch stores (decision needed, Open questions).
- Versioning: `versionCode = MAJOR×10000 + MINOR×100 + PATCH`, `versionName = MAJOR.MINOR.PATCH`, git tag `vX.Y.Z`; both literal in the build file; the tag commit is what F-Droid builds.
- Pre-release checklist (`docs/release/checklist.md`): CI green (JVM tests, size gate, manifest/DEX policy gate, reproducible check); emulator matrix (§7.5) run on the release build; `adb logcat | grep "Accessing hidden"` shows only `expandNotificationsPanel`; `dumpsys meminfo` recorded; disclosures present before every grant; fastlane + Play text in sync; changelog written; tag pushed; GitHub release APK uploaded before the fdroiddata MR; Play AAB uploaded with the accessibility declaration and video current.

## 7. Phase deliverables, acceptance criteria, emulator matrix

### 7.1 Phase 5 — Notification badges (first consumer of the grant flow)
Deliverables: `BadgeNotificationListener`, `BadgeCounter` + JVM tests, `BadgeStore`, `BadgeDrawable`, `NotificationAccess`, `GrantFlowScreen` + `GrantSpec(NOTIFICATION_ACCESS)`, `InstallSourceProbe`, `RestrictedSettingsHelpScreen`, strings.
Acceptance:
- JVM: `BadgeCounterTest` covers group summary dropped, `canShowBadge=false` dropped, legacy-channel ongoing dropped, empty title+text dropped, suspended dropped, `number=0/1/7` → `1/1/7`, sum cap 999, per-user separation, per-app toggle, includeOngoing on/off.
- Emulator: granting via the detail intent (API 30+) and the list fallback (API 26) lands on the correct screen; after grant, counts appear within 1 s without restarting the app; posting 5 notifications in one burst produces one list re-render (log counter); revoking access clears badges within 1 s and the settings row shows "off"; killing the process (`am kill`) and posting a notification re-populates counts; 100 %/200 % font scale keeps rows ≥ 48 dp and the badge glued to the label at start/center/end alignment.
- Sideload simulation on API 33/34: with the toggle greyed out, returning ungranted shows the help screen; after "Allow restricted settings" the grant succeeds.

### 7.2 Phase 6 — Background engine
Deliverables: `:core:collections` parsers + `SourceDetector` + fixtures/tests; `BackgroundEngine`, `BackgroundScheduler`, `BackgroundJobService`, `NetworkPolicy`, `ImageFetcher`, `ImageStore`, `ImageDecoders`, `ColorSampler`, `BackgroundView`, `BackgroundController`; settings status line; `background.next`.
Acceptance:
- JVM: every fixture parses to the expected URL list; detection order cases; `http://` rejected; relative URLs resolved; Wallhaven purity forced to 100; 500-item cap; state round-trips through kotlinx.serialization.
- Emulator: switching mode system→solid→gradient→image at runtime needs no activity recreation and no wallpaper flash; `adb shell cmd jobscheduler run -f org.justa.launcher <JOB_ID>` rotates the image; with Wi-Fi off and cellular on plus `unmeteredOnly`, the job and the resume path skip and record the reason; `adb shell cmd netpolicy set restrict-background true` skips the JOB path; a 20 MiB image is aborted at the cap; a 6-hop redirect chain fails; an `https→http` redirect fails; a truncated JPEG marks the URL bad and the next rotation picks another; `adb shell pm trim-caches 999G` (cacheDir purge) leaves the current image intact; light and dark images flip text color and status-bar icon appearance; `dumpsys meminfo` Graphics delta between passthrough and image mode ≤ 1.25 × window pixels × 4 and Java heap delta < 1 MB; total `:app` PSS ≤ 50 MB at 1080×2400 (record, then tighten).

### 7.3 Phase 8 (my parts) — accessibility, device admin, shade, info lines, usage access
Deliverables: `SystemActionsService`, `accessibility_service_config.xml`, `SystemActions`, `AccessibilityState`, `StatusBarShade`, `LockAdminReceiver` + policies XML, `DeviceAdminLock`, `ProcessUtil`; `ClockLine`, `DateLine`, `BatteryLine`, `ScreenTimeLine` + `ScreenTimeCalculator` tests, `UsageAccess`; `GrantSpec` for ACCESSIBILITY, DEVICE_ADMIN, USAGE_ACCESS; Play declaration draft + demo-video script in `docs/release/play-declarations.md`.
Acceptance:
- `adb shell ps -A | grep org.justa.launcher` shows `:system` only while the service is enabled; its idle PSS < 15 MB; `adb shell am crash <pid of :system>` leaves the home process alive and `cmd role get-role-holders android.app.role.HOME` unchanged.
- Event-free config binds and performs actions on API 26, 33, 36 (else fallback config adopted and documented); gesture-to-action latency < 150 ms; `system.lock_screen` locks via accessibility on ≥ 28 and via device admin on 26; the strong-auth consequence is visible in the disclosure; `removeActiveAdmin` works and uninstall succeeds afterwards.
- Shade: reflection succeeds on all matrix APIs with exactly one "Accessing hidden method" logcat line per call in debug; forcing the reflection to fail (temporarily wrong method name) falls back to accessibility when enabled and to `NeedsGrant` otherwise; no crash in any branch.
- Info lines: 12/24 h follows the system toggle live; date rolls over at a simulated midnight (`adb shell date`/`settings put global auto_time 0`); battery updates within one `ACTION_BATTERY_CHANGED` (`adb shell dumpsys battery set level 42`); screen time shows within 1 s of grant and matches `ScreenTimeCalculatorTest` semantics for a scripted event sequence; usage-access grant flow verifies via the app-op check on return.

### 7.4 Phase 9 — release engineering
Deliverables: R8/shrinker config, `scripts/check-apk-size.sh`, `scripts/check-manifest-policy.sh`, `scripts/reproducible-check.sh`, `fdroid/metadata/org.justa.launcher.yml`, fastlane text, `docs/release/{play-declarations,checklist}.md`, `docs/privacy.md`, signing env contract.
Acceptance: release APK ≤ 2.5 MiB and gate fails when a 1 MiB dummy asset is added (negative test); two clean-container builds compare equal with `apksigcopier compare`; policy gate passes and fails when `QUERY_ALL_PACKAGES` is temporarily added; release build decodes the default settings/theme JSON on API 26 and 36; `analyzeReleaseR8Config` shows no unexpected keep rules; StrictMode absent from the release DEX (`apkanalyzer dex references | grep StrictMode` empty).

### 7.5 Emulator verification matrix (v1 scope; no work profile / private space)
| Check | 26 | 30 | 33 | 34 | 35 | 36 | 37 |
|---|---|---|---|---|---|---|---|
| NLS grant (list fallback / detail intent), counts, revoke, rebind | list | detail | detail | detail | detail | detail | if image |
| NLS counting: summary, canShowBadge off, ongoing legacy, empty, number, OTP-redacted text (35+) | x | x | x | x | x | x | x |
| Restricted-settings help (sideloaded install; `appops set … ACCESS_RESTRICTED_SETTINGS deny` recipe, unverified) | – | – | x | x | ECM | ECM | x |
| Accessibility: bind with event-free config; actions available per API gate | no lock/shot | lock | +dismiss | x | x | x | x |
| Device admin lock + strong-auth check + deactivate | x | x | – | – | – | x | – |
| Shade reflection success + single hidden-API log line | x | x | x | x | x | x | x |
| Background: mode switch, forced job, unmetered skip, Data Saver skip, size/redirect caps, cache purge, auto colors | BitmapFactory | ImageDecoder | x | ACCESS_NETWORK_STATE | x | quota | memory limiter |
| Info lines: 12/24 h, midnight, battery, screen time | x | x | – | – | x | x | – |
| Edge-to-edge insets under image mode; light/dark bar icons | legacy flags | controller | x | x | enforced | enforced | x |
| Steady-state `dumpsys meminfo`, `am start -W` | x | – | – | – | – | x | x |
"x" = run; "–" = not needed at that level. A low-RAM (Go) image at API 30, if available, additionally verifies the NLS low-RAM message.

## 8. Disagreements with the previous plan (with reasons)
1. No `setPersisted(true)` / `RECEIVE_BOOT_COMPLETED` (Plan §1.7, Background §C3 recommend it). The HOME app is started right after boot and reschedules the job idempotently; persistence only buys a permission that appears in store listings. Cost accepted: if the user switches default launcher, rotation pauses until the app is opened.
2. No `play`/`foss` flavors (Plan Parts 3–4). Decision 4 forbids GMS code anywhere, so the flavors would be byte-identical; one build, two artifacts (`bundleRelease`, `assembleRelease`) keeps F-Droid metadata to `gradle: [yes]`.
3. Manifest permission list in Plan Part 4 drops `SET_WALLPAPER` and `ACCESS_HIDDEN_PROFILES` per decisions 2–3.
4. Shade ordering: the synthesis implies reflection-first; the stack report prefers accessibility-first. I use accessibility-if-enabled → reflection → prompt (supported API whenever available; hidden API only for users who never enabled the service).
5. No NLS filter-type meta-data in v1 (Platform §C1 example declares them) to keep the ongoing toggle in-app.
6. Platform §C2's extra filters (`FLAG_FOREGROUND_SERVICE`/`CATEGORY_TRANSPORT`) are adopted as the default-off `badges.includeOngoing` toggle rather than hard-coded.

## Open questions
1. Package name and GitHub owner (all paths/IDs above use `org.justa.launcher` / `<owner>` placeholders).
2. Play App Signing: upload our release key (one certificate across Play/F-Droid/GitHub, key shared with Google) or use an upload key (users cannot switch stores without uninstalling)?
3. Event-free accessibility config (`accessibilityEventTypes` omitted): verify binding on API 26/33/36 before relying on it (Platform §D1 UNVERIFIED).
4. Do F-Droid-installed builds trip restricted settings on 13/14/15 (Plan §1.14 item 4)? Also whether `appops set … ACCESS_RESTRICTED_SETTINGS deny` reproduces the state on an emulator.
5. Does `expandNotificationsPanel` reflection still work on the API 37 image (Plan §1.14 item 3)?
6. Is `PACKAGE_USAGE_STATS` covered by Play's Permissions Declaration Form, and does Play have any stance on device-admin `lockNow()` for launchers (Platform open question 8)?
7. Whether `force-lock` is among the device-admin policies deprecated since Android 9 (Background report open question 11).
8. Should `system.quick_settings` also get the `expandSettingsPanel()` reflection fallback (flagged `unsupported`, same list) or stay accessibility-only? I propose accessibility-only to limit hidden-API surface.
9. Data-safety wording for on-device-only notification/usage processing — confirm against the current form text before submission.
10. Local toolchain: this machine has no JDK, Android SDK, Gradle, `apksigner` or `fdroid`; the scaffold owner must decide whether Phase 0 installs them or all verification stays in CI.
11. Target 37 later: `ACCESS_LOCAL_NETWORK` would be needed for LAN image URLs; v1 (target 36, HTTPS-only) effectively excludes LAN sources — confirm that is acceptable.

### Critical Files for Implementation
- /home/hyzii/Projects/justa-launcher/app/src/main/kotlin/org/justa/launcher/notifications/BadgeNotificationListener.kt
- /home/hyzii/Projects/justa-launcher/app/src/main/kotlin/org/justa/launcher/system/SystemActionsService.kt
- /home/hyzii/Projects/justa-launcher/app/src/main/kotlin/org/justa/launcher/background/BackgroundEngine.kt
- /home/hyzii/Projects/justa-launcher/app/src/main/kotlin/org/justa/launcher/settings/grant/GrantFlowScreen.kt
- /home/hyzii/Projects/justa-launcher/app/build.gradle.kts
