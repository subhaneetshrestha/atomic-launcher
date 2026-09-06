# Android launcher capabilities — consolidated research note

Date: 2026-09-05/06. Synthesis of three primary-source research reports (appendices A–C in this directory), written on 2026-09-06 before any code existed. API levels: Android 11=30, 12=31, 13=33, 14=34, 15=35, 16=36, 17=37. "UNVERIFIED" marks claims not confirmed against a primary source.

Appendices (full reports, verbatim):
- [A — Launcher platform fundamentals](2026-09-05-android-launcher-capabilities-appendix-a-platform.md): being the home app, package visibility, LauncherApps, labels, notification counting, restricted settings, accessibility policy, Android 12→17 changes.
- [B — Gestures, background images, typography, widgets and shortcuts](2026-09-05-android-launcher-capabilities-appendix-b-gestures-background-typography.md).
- [C — Tech stack, reference launchers, theming and sharing, toolchain](2026-09-05-android-launcher-capabilities-appendix-c-stack-theming.md).

The "Proposal" tiers in Part 2 were suggestions at research time. The decisions actually taken are recorded in [ADR 0001](../decisions/0001-v1-scope-stack-distribution.md).

## Part 1 — Research findings (cited)

Legend: API levels are Android API levels (Android 11=30, 12=31, 13=33, 14=34, 15=35, 16=36, 17=37). "UNVERIFIED" marks claims the agents could not confirm from a primary source.

### 1.1 Platform baseline and deadlines
- Android 17 (API 37) is listed as "now available" on developer.android.com/about/versions/17, with 37.1/37.2 minor releases in beta; the SDK Platforms release-notes page still listed API 36 as newest at research time, so treat the API 37 platform as "install if the SDK Manager offers it". Reference docs now stamp APIs with minor versions like "Added in version 36.1".
- Google Play: new apps and updates must target API 36+ from 2026-08-31 (extension to 2026-11-01 available). https://developer.android.com/google/play/requirements/target-sdk
- 16 KB page sizes: Java/Kotlin-only apps already comply; only matters if native libs are added. https://developer.android.com/guide/practices/page-sizes
- Toolchain: AGP 9.4.0 (Sept 2026, JDK 17, max API 37), Gradle 9.7.1, Kotlin 2.4.10 (K2), Compose BOM 2026.08.00 (Compose 1.12.0), Android Studio Quail 4 (2026.1.4). https://developer.android.com/build/releases/gradle-plugin , https://kotlinlang.org/docs/releases.html

### 1.2 Being the home app
- Manifest pattern from AOSP Launcher3: MAIN + HOME + DEFAULT filter; `launchMode="singleTask"`, `clearTaskOnLaunch`, `stateNotNeeded`, `resumeWhilePausing` (lets Home resume before the previous app finishes pausing), empty `taskAffinity`, `exported="true"`. Olauncher adds `excludeFromRecents="true"`. https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/AndroidManifest.xml , https://developer.android.com/guide/topics/manifest/activity-element
- Home key delivers ACTION_MAIN/CATEGORY_HOME with FLAG_ACTIVITY_NEW_TASK to the existing instance via `onNewIntent()` (AOSP `RootWindowContainer`, `ActivityTaskManagerService`).
- The home process is kept at oom-adj 600 ("we want to try avoiding killing it… because the user interacts with it so much"), so steady-state memory matters more than cold start. https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/services/core/java/com/android/server/am/ProcessList.java
- A crashing third-party home silently loses its default status (AOSP `AppErrors.handleAppCrashLSPB`). Crash-freedom is a product requirement.
- Back: with predictive back on by default for target 36, `onBackPressed()` is never called; use `OnBackInvokedCallback` to dismiss overlays and otherwise no-op. https://developer.android.com/about/versions/16/behavior-changes-16
- Default launcher: `RoleManager.ROLE_HOME` (`isRoleHeld`, `createRequestRoleIntent`, API 29); fallback `Settings.ACTION_HOME_SETTINGS` (API 21). The system defines "default launcher" as the ROLE_HOME holder (AOSP `ShortcutService`). https://developer.android.com/reference/android/app/role/RoleManager
- The home activity never gets a system splash screen (AOSP `ActivityRecord.getStartingWindowType`); match `windowBackground` to the home background anyway.

### 1.3 Listing and launching apps (package visibility)
- Use `LauncherApps.getActivityList(null, user)` per profile plus `LauncherApps.Callback` for package add/remove/change; launch with `startMainActivity(component, user, …)`. https://developer.android.com/reference/android/content/pm/LauncherApps
- Package visibility (target 30+): `LauncherApps` filters by the *caller's* UID (AOSP `LauncherAppsService` → `AppsFilter`), and holding ROLE_HOME grants no exemption. Declaring `<queries><intent><action MAIN/><category LAUNCHER/></intent></queries>` makes every exported launcher activity visible; `QUERY_ALL_PACKAGES` is not needed for the core list. Apps with no launcher activity (synthesized "app details" rows) may need it (UNVERIFIED by device test). https://developer.android.com/training/package-visibility
- Play policy: `QUERY_ALL_PACKAGES` permitted uses name "device search, antivirus apps, file managers, and browsers" (launchers not named); "apps that have a core purpose to launch, search, or interoperate with other apps… may obtain scope-appropriate visibility" and may not use QUERY_ALL_PACKAGES when a scoped declaration works. https://support.google.com/googleplay/android-developer/answer/10158779 , https://support.google.com/googleplay/android-developer/answer/13986130
- Labels: `LauncherActivityInfo.getLabel()`; invalidate caches on `ACTION_LOCALE_CHANGED` and package callbacks. Uninstall via `ACTION_DELETE` + `REQUEST_DELETE_PACKAGES`; app info via `startAppDetailsActivity`. A launcher cannot disable or rename other apps at the platform level (rename/hide are launcher-local data).

### 1.4 Notification counts (the "number in a circle")
- `NotificationListenerService` with `BIND_NOTIFICATION_LISTENER_SERVICE`; user grants "Notification access" (`Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` + component extra, API 30). Wait for `onListenerConnected()`, then `getActiveNotifications()`; handle `onListenerDisconnected` → `requestRebind`. Not bound on low-RAM devices ≤ Android 10; ignored when running in a work profile. https://developer.android.com/reference/android/service/notification/NotificationListenerService
- Counting rules used by AOSP Launcher3: drop `FLAG_GROUP_SUMMARY`; require `Ranking.canShowBadge()`; drop ongoing notifications on the legacy default channel; drop notifications with no title and no text; count per (package, user) with `max(1, Notification.number)`, capped at 999 (`DotInfo.MAX_COUNT`). The CDD requires badging launchers to respect `NotificationChannel.setShowBadge()`. https://android.googlesource.com/platform/packages/apps/Launcher3/+/refs/heads/main/src/com/android/launcher3/notification/NotificationListener.java
- Play policy: no notification-listener-specific section; the User Data policy's prominent disclosure + affirmative consent applies before sending the user to the grant screen. https://support.google.com/googleplay/android-developer/answer/10144311
- Android 13+ "restricted settings": apps installed with package source LOCAL_FILE/DOWNLOADED_FILE (and, on 15+, via non-trusted installers under Enhanced Confirmation Mode) cannot be granted notification access or accessibility until the user opens App info → ⋮ → "Allow restricted settings". Play-installed builds are unaffected; sideloaded/F-Droid builds need a help flow. https://support.google.com/android/answer/12623953 , AOSP `InstallPackageHelper`, `EnhancedConfirmationService`

### 1.5 Gestures: detection and what they can trigger
- Detection: `GestureDetector` for single-finger tap/double-tap/long-press/fling; custom `MotionEvent` handling for multi-finger swipes, short vs long swipes (mLauncher does 4 directions × short/long), pinch via `ScaleGestureDetector`; thresholds from `ViewConfiguration` (touch slop, fling velocities, timeouts). https://developer.android.com/reference/android/view/ViewConfiguration
- System gesture nav: the bottom home/quick-switch zone cannot be reclaimed; back-edge zones can be excluded with `setSystemGestureExclusionRects`, and the 200 dp cap "does not apply… to the input method and home activity"; mandatory gesture insets cannot be overridden. https://developer.android.com/reference/android/view/View#setSystemGestureExclusionRects(java.util.List%3Candroid.graphics.Rect%3E) , https://developer.android.com/develop/ui/views/touch-and-input/gestures/gesturenav
- Haptics: `View.performHapticFeedback` (`CONFIRM`/`REJECT`/`GESTURE_START` API 30; `TOGGLE_ON/OFF`, `SEGMENT_TICK` API 34), no permission. https://developer.android.com/reference/android/view/HapticFeedbackConstants
- Public actions available to any app (no special permission unless noted): open app (`LauncherApps`); app info; Settings pages and Settings panels (Wi-Fi/Internet/Volume/NFC, API 29); assistant (`ACTION_ASSIST`/`ACTION_VOICE_COMMAND`); camera (`MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA`); dialer (`ACTION_DIAL`); SMS (`ACTION_SENDTO sms:`); alarms/timers (`AlarmClock.ACTION_SHOW_ALARMS` 19, `ACTION_SHOW_TIMERS` 26); calendar (`content://com.android.calendar/time/<ms>`); flashlight (`CameraManager.setTorchMode`, API 23, no permission); volume UI (`AudioManager.adjustVolume(ADJUST_SAME, FLAG_SHOW_UI)`); media keys (`AudioManager.dispatchMediaKeyEvent`, API 19); Do Not Disturb (needs `ACCESS_NOTIFICATION_POLICY` + user grant; Android 15 target must use `AutomaticZenRule`); wallpaper picker (`ACTION_SET_WALLPAPER`); web search (`ACTION_WEB_SEARCH`); Play search (documented form is the `https://play.google.com/store/search?q=` URL; `market://search` is undocumented); contacts search (`READ_CONTACTS`, dangerous); default-launcher chooser; per-app notification settings (`ACTION_APP_NOTIFICATION_SETTINGS`, API 26); pause work apps (`UserManager.requestQuietModeEnabled`, API 28).
- Not available without an accessibility service: lock screen (public alternative is device-admin `lockNow()`, which forces PIN/pattern re-auth), notification shade, quick settings, recents, screenshot, power menu.

### 1.6 Privileged actions and policy
- `AccessibilityService.performGlobalAction`: `NOTIFICATIONS` (16), `QUICK_SETTINGS` (17), `RECENTS` (16), `POWER_DIALOG` (21), `LOCK_SCREEN` (28), `TAKE_SCREENSHOT` (28), `DISMISS_NOTIFICATION_SHADE` (31). https://developer.android.com/reference/android/accessibilityservice/AccessibilityService
- Play's Accessibility API policy lists "launchers" among apps that are *not* accessibility tools: they must set `isAccessibilityTool=false`, complete the Play Console declaration (purpose, demo video), show in-app prominent disclosure with affirmative consent, document it in the listing, and prefer narrower APIs where possible. Olauncher's pattern: optional service in its own process for double-tap lock, device admin as fallback. https://support.google.com/googleplay/android-developer/answer/10964491
- Hidden `StatusBarManager.expandNotificationsPanel()` (used via reflection by Olauncher with `EXPAND_STATUS_BAR`) is flagged `test-api,unsupported` in the Android 15 and 16 non-SDK lists: works today for all target SDKs, "expected to be conditionally blocked in future versions". Wrap in try/catch, fall back to the accessibility action. https://dl.google.com/developers/android/baklava/non-sdk/hiddenapi-flags.csv , https://developer.android.com/guide/app-compatibility/restrictions-non-sdk-interfaces
- `DevicePolicyManager.lockNow()` needs a device-admin receiver with `force-lock`; "the device must be unlocked using strong authentication (PIN, pattern, or password)" afterwards; device admin has been partially deprecated since Android 9. https://developer.android.com/reference/android/app/admin/DevicePolicyManager
- Screen time: `UsageStatsManager` + `PACKAGE_USAGE_STATS` (user grant via `ACTION_USAGE_ACCESS_SETTINGS`), check with `AppOpsManager.OPSTR_GET_USAGE_STATS`; disclosure applies.

### 1.7 Home background from a URL collection
- Two architectures. (1) Set the *system* wallpaper via `WallpaperManager.setStream(stream, null, allowBackup, FLAG_SYSTEM|FLAG_LOCK)` (API 24, normal `SET_WALLPAPER`) and show it behind a translucent launcher window with `android:windowShowWallpaper`; the wallpaper service owns the bitmap; affects the lock screen and replaces live wallpapers; setting FLAG_SYSTEM only splits a shared wallpaper into lock-only. (2) Draw the image inside the launcher's own window: no permission, no lock-screen effect, but the launcher process owns a screen-sized bitmap (use `Bitmap.Config.HARDWARE` / `ImageDecoder.setTargetSize`). https://developer.android.com/reference/android/app/WallpaperManager , https://developer.android.com/reference/android/R.attr#windowShowWallpaper
- Reading the current wallpaper back is impossible since Android 13 without `MANAGE_EXTERNAL_STORAGE` (SecurityException on 14+). `WallpaperColors` (API 27; `HINT_SUPPORTS_DARK_TEXT` API 31) can be computed from our own bitmap to auto-pick text color.
- Scheduling: platform `JobScheduler` periodic job (zero dependencies; minimum period enforced, 15 min per the WorkManager guide; Android 14+ needs `ACCESS_NETWORK_STATE` for network-constrained jobs; jobs may be shifted to unmetered networks). WorkManager 2.11.2 transitively pulls `room-runtime` + SQLite. App Standby buckets can cut a rarely-used app to one run per day with no network, so also refresh on resume when the interval has elapsed. https://developer.android.com/reference/android/app/job/JobInfo.Builder , https://dl.google.com/dl/android/maven2/androidx/work/work-runtime/2.11.2/work-runtime-2.11.2.pom , https://developer.android.com/topic/performance/appstandby
- Fetching: `HttpURLConnection` is enough; cleartext HTTP is blocked by default since API 28 and `usesCleartextTraffic` is on a deprecation path in Android 17; a LAN URL needs the `ACCESS_LOCAL_NETWORK` runtime permission when targeting 37. Respect Data Saver (`getRestrictBackgroundStatus`) and metered networks. Formats: JPEG/PNG/WebP/HEIF (8.0+)/AVIF (mandatory decode from 14); `setStream` accepts JPEG/PNG, so decode other formats first.
- Collection sources: plain-text URL list, JSON array, RSS/Media RSS/Atom enclosures, and direct URLs are keyless and stable. Wallhaven API: keyless for SFW, 45 req/min. Unsplash: API key required, 50 req/h in demo, and its guidelines forbid "wallpaper applications". Reddit: OAuth required, unauthenticated JSON may be blocked. Bing daily image: no official docs (UNVERIFIED). GitHub REST API: 60 req/h unauthenticated, so serve lists from raw/Pages, not the API. https://wallhaven.cc/help/api , https://help.unsplash.com/en/articles/2511245-unsplash-api-guidelines , https://support.reddithelp.com/hc/en-us/articles/16160319875092-Reddit-Data-API-Wiki

### 1.8 Typography, layout, color and system UI surfaces
- Fonts: system families (`sans-serif`, `sans-serif-condensed`, `serif`, `monospace`, `casual`, `cursive`, plus weight aliases) via `Typeface.create(family, weight, italic)` (API 28); AOSP now says apps should use `SystemFonts.getAvailableFonts()` (API 29) rather than parse fonts.xml; user font files via SAF + `Typeface.Builder(File)` (API 26) with persisted URI permission; variable fonts via `fontVariationSettings` (API 26). Downloadable Fonts need Google Play services ≥ v11 (not on GMS-free devices; forbidden as a hard dependency on F-Droid). https://developer.android.com/reference/android/graphics/Typeface.Builder , https://developer.android.com/develop/ui/views/text-and-emoji/downloadable-fonts
- Sizing: Android 14 non-linear font scaling up to 200% — store sizes in sp, convert with `TypedValue.applyDimension`, keep `lineHeight` in sp, never derive from `fontScale`; rows must keep a 48 dp touch target; contrast ≥ 4.5:1 for text under 18 sp. https://developer.android.com/about/versions/14/features#non-linear-font-scaling
- Legibility over images: `setShadowLayer` (works under hardware acceleration), stroke outline via a custom view, scrim/gradient, auto black/white text from `WallpaperColors.HINT_SUPPORTS_DARK_TEXT`.
- Color: Material You tokens `android.R.color.system_accent1_*`, `system_neutral*` (API 31; 227 `system_*` resources incl. tonal roles from API 34) are readable with `context.getColor()` and no library. Night mode: `UiModeManager.setApplicationNightMode` (API 31). https://developer.android.com/reference/android/R.color
- Window: edge-to-edge is enforced at target 35 and cannot be opted out at target 36; `setStatusBarColor`/`setNavigationBarColor` deprecated (35); cutout mode forced to ALWAYS; use `WindowInsetsController.setSystemBarsAppearance` for light/dark bar icons and pad by `systemBars()|displayCutout()` insets. Hiding the status bar is possible (API 30 `hide` + transient-bars-by-swipe) but hides the clock/notification icons on Home. Orientation locks are ignored on sw≥600 dp for target 36 and the opt-out disappears at 37. https://developer.android.com/about/versions/15/behavior-changes-15 , https://developer.android.com/about/versions/16/behavior-changes-16
- Clock/date/battery: platform `TextClock` (locale-aware, honors 24h setting, styleable via Spanned); `ACTION_BATTERY_CHANGED` is sticky and runtime-registered only; `java.time` is available from API 26 without desugaring.
- Widgets hosting: `BIND_APPWIDGET` is `signature|privileged`; the default launcher gets *no* automatic bind permission; each bind goes through the `ACTION_APPWIDGET_BIND` dialog. Heavy (RemoteViews, foreign views) — contradicts "text-only, lightweight". https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/main/core/res/AndroidManifest.xml
- App shortcuts: `LauncherApps.getShortcuts`/`startShortcut` (API 25) work only while we hold ROLE_HOME (`hasShortcutHostPermission`); feasible as a text sub-menu on long-press.

### 1.9 Profiles: work and private space
- Work profile: enumerate `LauncherApps.getProfiles()`, mark with `PackageManager.getUserBadgedLabel` ("Work Email"), listen to `ACTION_MANAGED_PROFILE_AVAILABLE/UNAVAILABLE` (runtime receivers only), toggle quiet mode with `requestQuietModeEnabled` (API 28). A DPC may block work notifications from reaching our listener.
- Private space (Android 15): only visible when we hold ROLE_HOME **and** declare the normal `ACCESS_HIDDEN_PROFILES` permission; must render a separate container, let the user hide/show and lock/unlock it (quiet mode), react to `ACTION_PROFILE_AVAILABLE/UNAVAILABLE` (API 35), hide the entry point when `LauncherUserInfo.getUserConfig()` says `PRIVATE_SPACE_ENTRYPOINT_HIDDEN` (API 36), and offer `getPrivateSpaceSettingsIntent()` (API 36). https://developer.android.com/about/versions/15/behavior-changes-all

### 1.10 Reference launchers (release APK size, stack)
| Launcher | Stack | APK | License | Notes |
|---|---|---|---|---|
| Olauncher | Kotlin, Views + ViewBinding, Fragments/Navigation, WorkManager | 2.13 MiB | GPL-3.0 | 8 TextViews in a LinearLayout; `contains` search; lock via device admin + accessibility; reflection for the shade; declares QUERY_ALL_PACKAGES |
| Unlauncher | Kotlin, Views, Hilt, Proto DataStore | 1.89 MiB | MIT | `contains` search; no services |
| KISS | Java, Views | 2.1 MiB | GPL-3.0 | Sublime-style fuzzy scorer (`FuzzyScoreV2`), NFKD normalization; NLS badges; accessibility lock |
| Slate | text-only, zero network | 2.0 MiB | MIT | |
| Lunar | Kotlin, Views | 2.5 MiB | GPL-3.0 | ships `<queries>` only, no QUERY_ALL_PACKAGES |
| mLauncher | Views + Compose settings, Room, Moshi, WorkManager | 6.75 MiB | GPL-3.0 | custom subsequence scorer; NLS per-package counts (no filtering); accessibility actions incl. screenshot; hex-validated color theme export |
| Kvaesitso | Compose, Koin, Room, kotlinx.serialization | 13.2 MiB | GPL-3.0 | versioned tolerant JSON `ThemeBundle` (version 2) with legacy migration |
| Lawnchair | Launcher3 + Compose, Hilt | 16.5 MiB | Apache-2.0 | fuzzywuzzy (GPL-2) WeightedRatio ≥65 + prefix matcher |
Sources: F-Droid pages and GitHub releases/raw files (full list in the agent report). Copying code from GPL projects would force GPL on this project; algorithms/constants can be re-implemented.

### 1.11 Tech-stack evidence
- Views vs Compose: Google's own hero benchmark (Compose 1.11, release + R8 + precompiled) shows Compose 2.5% slower TTID and 13% slower TTFD than Views, with scroll jank parity since 1.9. Compose's unshrunk core AARs total ≈14 MB before R8 and Compose relies on a Baseline Profile (`profileinstaller`) for startup. Views apps in this niche ship at 1.9–2.5 MiB. https://developer.android.com/develop/ui/compose/performance/herobenchmark , https://developer.android.com/develop/ui/compose/performance
- Flutter's release floor is 4.3–4.8 MB for a minimal app and every launcher API needs platform channels; React Native similarly needs native modules plus a JS engine in a resident process. https://docs.flutter.dev/resources/faq
- Dependency weights (POMs): `appcompat 1.8.0` pulls 18 artifacts (~1.2 MB AAR); `work-runtime 2.11.2` pulls `room-runtime`; `datastore-preferences 1.2.1` pulls Okio + `kotlinx-serialization-protobuf`; `kotlinx-serialization-json 1.11` is ~0.3 MB + 0.4 MB core, reflection-free, R8-friendly; Moshi/Gson need reflection or keep rules; `me.xdrop:fuzzywuzzy` is GPL-2; ZXing core is 610 KB.
- Build: R8 full mode is default; AGP 9.1+ repackages classes automatically; AGP 9.3 adds an optimization DSL and `keepRules` source sets. AAB is required on Play; F-Droid builds APKs from source and can ship developer-signed APKs when builds are reproducible. https://developer.android.com/build/shrink-code , https://f-droid.org/docs/Reproducible_Builds/

### 1.12 Theming and sharing evidence
- Prior art: ADW icon-pack convention (declarative `appfilter.xml` + resources inside an APK — robust but forces theme authors to build APKs); Kustom distributes wallpaper lists as a static JSON index on Git hosting; Kvaesitso's `ThemeBundle` JSON uses `version`, `ignoreUnknownKeys`, `coerceInputValues`, `explicitNulls=false`, string-encoded colors with fallbacks and a legacy migration path; mLauncher validates `#AARRGGBB` with a regex on import.
- Sharing primitives: SAF `ACTION_CREATE_DOCUMENT`/`ACTION_OPEN_DOCUMENT` (API 19, no permission); `ACTION_SEND` + `FileProvider`; receive via `ACTION_VIEW`/`ACTION_SEND` filters on MIME plus `pathSuffix` (pathPattern wildcards are limited); custom scheme deep links (`justa://…`) need no domain, while verified App Links need HTTPS + `/.well-known/assetlinks.json`. https://developer.android.com/guide/topics/manifest/data-element , https://developer.android.com/training/app-links/verify-android-applinks
- Policy: Play forbids downloading executable code but data files are fine; Play category "Personalization". F-Droid forbids GMS/Firebase dependencies (use a flavor) and wants reproducible builds. https://support.google.com/googleplay/android-developer/answer/9888379 , https://f-droid.org/docs/Inclusion_Policy/
- Untrusted fonts: historical Minikin/FreeType CVEs (CVE-2016-0808, CVE-2016-2414, CVE-2016-10244) argue for size caps, magic-byte checks and opt-in for fonts embedded in community themes. OFL 1.1 permits bundling fonts with software if the license text ships along.
- QR: Google's code scanner is GMS-only; a FOSS path needs `CAMERA` + a decoder; encode a gallery URL, not the theme.

### 1.13 Behavior changes that shape the design (Android 12→17)
- 12: root launcher activities no longer finished on Back; `exported` mandatory; `ACTION_CLOSE_SYSTEM_DIALOGS` deprecated.
- 13: `POST_NOTIFICATIONS` (only if we post); `ACTION_SHOW_WORK_APPS` (launchers should handle it); restricted settings for sideloads; `onBackPressed` deprecated.
- 14: non-linear font scaling to 200%; runtime receivers must declare `RECEIVER_(NOT_)EXPORTED`; implicit intents only to exported components; ongoing notifications become dismissable.
- 15: private space; edge-to-edge enforced at target 35; DND only via `AutomaticZenRule`; OTP redaction for untrusted listeners; `TextView.setUseBoundsForWidth` default.
- 16: predictive back default (no `onBackPressed`); edge-to-edge opt-out removed; orientation restrictions ignored on large screens; `elegantTextHeight` ignored; JobScheduler quota changes.
- 17: RAM-based app memory limits (keep the resident launcher lean); IME visibility not restored after unhandled config changes unless `windowSoftInputMode="stateAlwaysVisible"` (affects the search screen); `ACCESS_LOCAL_NETWORK` runtime permission; `static final` fields immutable via reflection; BAL restrictions extend to `IntentSender`. https://developer.android.com/about/versions/17/behavior-changes-17

### 1.14 Open questions to verify on a device (from the agents' UNVERIFIED lists)
1. Whether `<queries>` MAIN/LAUNCHER alone surfaces synthesized "app details" entries for apps without a launcher activity.
2. Exact back behavior on the home task under predictive back with no callback registered.
3. Whether the `test-api,unsupported` flag on `expandNotificationsPanel()` still permits reflection on Android 17 (empirically yes on 16 per Olauncher).
4. Whether F-Droid-installed builds trip "restricted settings" on Android 13/14/15 devices.
5. Live-wallpaper replacement behavior when setting a static image with `setStream`.
6. `LauncherActivityInfo.getLabel()` vs the target app's per-app language.

---

## Part 2 — Customization catalog (choose from this)

"Cost" = permission / policy / weight. "Proposal" = suggested tier: **Core** (v1), **Opt** (optional, off by default or later phase), **Skip** (not recommended).

### 2.1 Home list
| Customization | How | Cost | Proposal |
|---|---|---|---|
| Which apps appear, order, count (e.g. 1–10) | launcher-local list of (component, user) | none | Core |
| Horizontal alignment start/center/end | gravity | none | Core |
| Vertical position top/center/bottom | gravity | none | Core |
| Padding/margins, row gap | theme tokens (dp) | none | Core (theme) |
| Text size | sp; respects 200% font scaling | none | Core |
| Font: system family + weight; user font file; variable-font axes | `Typeface.create`, `Typeface.Builder(File)` via SAF | none; file fonts opt-in | Core (system), Opt (files) |
| Letter spacing, line height, case transform | TextView setters | none | Core (theme) |
| Text color, shadow / outline / scrim, auto text color from image | Paint/WallpaperColors | none | Core (theme) |
| Rename apps (launcher-local) | data | none | Opt |
| Hide apps from drawer | data | none | Opt |
| Work-profile badge label ("Work Email") | `getUserBadgedLabel` | none | Core if profiles in scope |
| Per-app long-press menu: app info, uninstall, rename, hide, shortcuts | intents; shortcuts need ROLE_HOME | `REQUEST_DELETE_PACKAGES` | Core (info/uninstall), Opt (shortcuts) |

### 2.2 Gestures
| Trigger | Detectable | Cost |
|---|---|---|
| Swipe up/down/left/right (short vs long) | yes, custom `MotionEvent` state machine | none |
| Double tap, long press, tap on clock/date/battery | yes | none |
| Two-finger swipes, pinch | yes | none |
| Edge swipes | yes; home app exempt from the 200 dp exclusion cap | conflicts with system back gesture |

| Action | Cost | Proposal |
|---|---|---|
| Open app / open search / open drawer | none | Core |
| App info, uninstall, Settings pages, Settings panels (API 29) | none | Core |
| Assistant, camera, dialer, SMS, alarms/timers, calendar, web search, Play search, open URL, wallpaper picker | none (implicit intents) | Core |
| Flashlight, volume UI, media play/pause/next | none | Core |
| Do Not Disturb toggle | `ACCESS_NOTIFICATION_POLICY` + grant; Android 15 target uses `AutomaticZenRule` | Opt |
| Next background image, pause work apps | none / API 28 | Core / Opt |
| Notification shade | hidden API (unsupported) or accessibility | Opt (reflection with fallback) |
| Lock screen, quick settings, recents, screenshot, power menu | accessibility service (Play declaration + disclosure) or device admin for lock | Opt (accessibility service, off by default) |
| Contacts search | `READ_CONTACTS` (dangerous) | Skip in v1 |

### 2.3 Search
| Customization | Proposal |
|---|---|
| Fuzzy subsequence scorer (word-initial, consecutive, prefix bonuses), NFD normalization, locale-independent lowercasing, cached normalized labels | Core |
| Auto-launch when one match (guarded against IME composition), IME action launches top result, keyboard auto-show setting | Core |
| Web-search fallback on no match | Opt |
| Alphabetical drawer with hidden apps excluded; show app shortcuts for a single result | Core / Opt |

### 2.4 Notification badges
| Customization | Proposal |
|---|---|
| Count in circle beside name; Launcher3-style filters; respect `canShowBadge`; cap 999 | Core |
| Badge style (number in circle / dot / plain number), size, position, colors | Core (theme) |
| Per-app badge toggle; include ongoing/media notifications toggle | Opt |
| Disclosure screen + grant flow + "restricted settings" help | Core |

### 2.5 Background
| Customization | Proposal |
|---|---|
| Modes: system wallpaper passthrough / solid color / gradient / image collection | Core |
| Image collection URL formats: text list, JSON array, RSS/Media RSS/Atom, direct URL, Wallhaven (keyless) | Core |
| Rotation interval (≥15 min; also on resume), unmetered-only, not-on-battery-low, Data Saver aware | Core |
| Dim/scrim over image; auto text color from `WallpaperColors` | Core (theme) |
| "Also set as system/lock wallpaper" | `SET_WALLPAPER` (normal) | Opt |
| Unsplash / Reddit / Bing sources | key/OAuth/undocumented; Unsplash forbids wallpaper apps | Skip |

### 2.6 Home information
| Customization | Cost | Proposal |
|---|---|---|
| Clock (`TextClock`), date, battery % | none | Opt |
| Screen time per app / total | `PACKAGE_USAGE_STATS` grant + disclosure | Opt |
| Weather | network + API keys | Skip |
| App widgets hosting | `ACTION_APPWIDGET_BIND` dialog per widget; RemoteViews; heavy | Skip |

### 2.7 System UI (theme-controlled)
| Customization | Proposal |
|---|---|
| Edge-to-edge with inset padding (mandatory) | Core |
| Light/dark status and nav bar icons | Core (theme) |
| Hide status bar | Opt (explicit) |
| Material You tokens usable in themes (`system_accent1_500` etc.) | Core (theme) |
| Night mode follow-system / force | Core |

### 2.8 Themes and sharing
| Customization | Proposal |
|---|---|
| Versioned JSON theme (`schema`, `minAppVersion`, colors `#AARRGGBB`, typography, layout, background, badge) with tolerant decoding | Core |
| Zip package with embedded OFL fonts and background image (size caps, magic-byte checks, opt-in) | Opt |
| Built-in themes; in-app editor (start with a token editor) | Core / Opt |
| Export/import via SAF, share sheet, receive via MIME + `.justatheme` suffix, `justa://theme` deep link | Core |
| Community gallery: static `index.json` on GitHub Pages fetched by the app | Opt (later) |
| QR sharing (encode gallery URL) | Opt (later; FOSS scanner needs CAMERA) |

### 2.9 Profiles
| Customization | Cost | Proposal |
|---|---|---|
| Work profile apps, badge, pause/resume | none (API 28 for quiet mode) | Opt |
| Private space container (Android 15) | `ACCESS_HIDDEN_PROFILES` + ROLE_HOME; required behaviors | Opt (needed to show private-space apps at all) |
