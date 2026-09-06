> Provenance: written by a background research agent on 2026-09-05/06 from primary sources (developer.android.com, AOSP source, Google Play policy pages, project repositories). Copied verbatim from the agent transcript into the repo on 2026-09-06. Claims marked UNVERIFIED were not confirmed against a primary source.

# justa-launcher — Primary-source research report (slice: reference launchers, tech stack, minimal dependencies, fuzzy search, theming & sharing, toolchain)

Research date: 2026-09-05/06. Everything below was read directly from the cited page (WebFetch or `curl` of the page text); numbered references map to the **Sources** list at the end. Claims I could not confirm from a primary source are marked **UNVERIFIED** and collected in the final section. Artifact sizes labelled "AAR/JAR bytes" are *unshrunk* Maven artifact sizes (Content-Length from Google Maven / directory listing on Maven Central), not APK impact after R8.

---

## A. Reference open-source launchers

### A.1 Summary table

| Launcher | Language / UI | minSdk / target / compile | Release APK size | License | Persistence | App enumeration | Fuzzy search | Notif. badges | Lock method |
|---|---|---|---|---|---|---|---|---|---|
| **Olauncher** | Kotlin, Views/XML + ViewBinding, Fragments + Navigation [2] | 24 / 36 / 36 [2] | F-Droid v6.7.19: 2.2 MiB [15]; GitHub v6.7.1 APK 2,231,668 B (2.13 MiB) [16] | GPL-3.0 [1][15] | SharedPreferences file `app.olauncher` [8] | `LauncherApps.getActivityList(null, profile)` over `userManager.userProfiles` [5] | No — case-insensitive `contains` + diacritic/separator-stripped `contains` [11] | None (no listener in manifest) [4] | DevicePolicyManager `lockNow()` **and** AccessibilityService `GLOBAL_ACTION_LOCK_SCREEN` (API≥28) [7][6] |
| **mLauncher (Multi Launcher)** | Kotlin, Views + Compose 1.11.4 (settings), Navigation, Room, Moshi, WorkManager [19][20] | 28 / 37 / 37 [19] | F-Droid 1.12.0: 6.7 MiB [31]; GitHub 1.12.0.1 APK 7,077,090 B (6.75 MiB), AAB 7,898,377 B [32] | GPL-3.0 [17][18] | SharedPreferences (+ Room for widgets, Moshi JSON backup) [27][22] | (fork of Olauncher; same approach) [17] | Yes — custom `FuzzyFinder.kt` subsequence scorer [23] | Yes — `NotificationListenerService` counting active notifications per package [25][26] | AccessibilityService `ActionService` (lock, recents, notifications, quick settings, power dialog, screenshot) [24] + DeviceAdmin receiver [21] |
| **Unlauncher** | Kotlin, Views, Hilt, Proto DataStore (protobuf-javalite), Room (legacy) [36] | 21 / 35 / 35 [36] | F-Droid 2.1.1: 1.9 MiB [40]; GitHub 2.1.1 APK 1,977,026 B (1.89 MiB) [41] | MIT [33][34] | Proto DataStore with `.proto` schemas [35][39] | UNVERIFIED (not read) | No — punctuation-stripped `contains(ignoreCase)`; `startsWith` ranked first [38] | None (no services in manifest) [37] | None (no accessibility/admin components) [37] |
| **KISS** | Java, Views (AppCompat, Preference, RecyclerView), errorprone [42] | 21 / 36 / 36 [42] | F-Droid 3.26.0: 2.1 MiB [59] | GPL-3.0 [59] | SharedPreferences (notifications) [52]; DB for history (not read) | `LauncherApps.getActivityList(null, profile)` per `UserManager.getUserProfiles()` [51] | Yes — `FuzzyScoreV1`/`V2` (Sublime-Text-style) [45][46][47] | Yes — `NotificationListener` → SharedPreferences string-sets [52] | AccessibilityService `LockAccessibilityService` (API≥28), no DPM fallback [53] |
| **Kvaesitso** | Kotlin, Jetpack Compose (1.13.0-alpha01 in catalog), Koin, Room, kotlinx.serialization, Ktor, Coil [60][62] | Android 8.0+ (F-Droid) [73]; build file not read | F-Droid 1.40.2: 13 MiB [73]; GitHub v1.40.2 APK 13,812,724 B (13.16 MiB) [74] | GPL-3.0 (plugin SDK Apache-2.0) [60][61] | Room (themes) [68]; DataStore 1.2.1 in catalog [62] | `LauncherApps.getActivityList` + `LauncherApps.Callback`, per-profile lists [70] | Similarity score with 0.8 cutoff (`ResultScore`); `string-similarity-kotlin` in catalog [70][62] | `data/notifications` module exists [63] (impl. not read) | UNVERIFIED |
| **Lawnchair** | Java + Kotlin, Launcher3 (Android 16 base) + Compose (BOM 2026.08.00), Hilt/Dagger, Room, protobuf, kotlinx.serialization, Retrofit/OkHttp, Coil [77][80][81] | 26 / 37 / 37 (minor 1) [80] | Not on F-Droid [77][87]; GitHub 15 Beta 3 APK 17,314,003 B (16.51 MiB); nightly debug 18,081,062 B [86] | LICENSE.txt: Apache-2.0 (GitHub API: NOASSERTION) [79][78] | protobuf/DataStore (catalog) [81] | Launcher3 model (not read) | `me.xdrop:fuzzywuzzy` `WeightedRatio`, cutoff 65 [84][81] | Launcher3 `NotificationListener` (PackageUserKey, canShowBadge filter) [85] | n/a (not read) |
| **Lunar Launcher** | Kotlin, Views + ViewBinding, Material [89] | 26 / 34 / 34 [89] | F-Droid 2.8.2: 2.5 MiB [91] | GPL-3.0 [88][91] | not read | uses `<queries>` (MAIN intent), **no** QUERY_ALL_PACKAGES [90] | not read | None (no listener) [90] | AccessibilityService `LockService` + `AdminReceiver` [90] |

Other text-first/minimal launchers surfaced by F-Droid search "minimal launcher" [92]: **Slate** (`com.slate.launcher`, "Minimal text-only Android home screen. No icons. No analytics. Zero network", v1.4, 2.0 MiB, MIT, Android 8.0+, source github.com/roufsyed/Slate-Minimal-Launcher) [93]; **Easy Launcher** (DroidWorksStudio, 0.3.3, 18 MiB, GPL-3.0) [94]; **Fokus Launcher** (1.9.3, 3.8 MiB, GPL-3.0, Android 8.0+) [95]; also listed: Slauncher (`app.slauncher`), Slate (`com.braniik.slate`) [92]. Slim Launcher (Unlauncher's parent) is not on F-Droid (404) [96].

### A.2 Olauncher — details (the closest product analogue)

- **Build**: `apply plugin: 'com.android.application'`, `'kotlin-android'`; `minSdkVersion 24`, `targetSdkVersion 36`, `compileSdk 36`; `viewBinding = true`, `buildConfig = true`; release `minifyEnabled true`, **no** `shrinkResources` [2]. Catalog: `appcompat 1.7.0`, `coreKtx 1.16.0`, AGP `gradle = 8.9.1`, `kotlinStdlib 2.1.20`, `lifecycleExtensions 2.2.0` (deprecated artifact per AndroidX [133]), `lifecycleViewmodelKtx 2.9.0`, `material 1.12.0`, `navigationFragmentKtx 2.9.0`, `recyclerview 1.4.0`, `workRuntimeKtx 2.10.1` [3].
- **Manifest**: `SET_WALLPAPER`, `EXPAND_STATUS_BAR`, `QUERY_ALL_PACKAGES`, `REQUEST_DELETE_PACKAGES`, `INTERNET`, `com.android.alarm.permission.SET_ALARM`, `PACKAGE_USAGE_STATS`, `ACCESS_HIDDEN_PROFILES`; **no `<queries>`**; `MainActivity` with HOME/DEFAULT/LAUNCHER; disabled `FakeHomeActivity` with the same HOME filter; `MyAccessibilityService` (`BIND_ACCESSIBILITY_SERVICE`, `android:process=":serviceProcess"`); `DeviceAdmin` receiver; `PinItemActivity`; no notification listener [4].
- **App enumeration**: `getAppsList()` iterates `userManager.userProfiles`, calls `launcherApps.getActivityList(null, profile)`, filters hidden apps, skips itself, excludes private-space profiles (`isPrivateSpaceProfile()`), appends pinned shortcuts [5].
- **Notification shade**: `expandNotificationDrawer()` gets `getSystemService("statusbar")`, reflectively loads `android.app.StatusBarManager` and invokes `expandNotificationsPanel` [5]. (See §C on why this is a non-SDK interface.)
- **Wallpaper**: `WallpaperManager.setBitmap(scaledBitmap, null, false, FLAG_SYSTEM)` and again with `FLAG_LOCK`; plain wallpapers via `createBitmap()` + `eraseColor()` [5]. Daily wallpaper = `WallpaperWorker : CoroutineWorker` (WorkManager) → `getTodaysWallpaper(wallType, prefs.firstOpenTime)` → skip if URL unchanged else `setWallpaper(context, url)` [13]. Source URLs: a GitHub Gist raw JSON (`gist.githubusercontent.com/tanujnotes/85e2d0343ace71e76615ac346fbff82b/raw`) and two Unsplash defaults [9].
- **Default-launcher flow**: `Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS`; for API<29 or when already default, "reset via fake activity" trick, else `showLauncherSelector(...)`; **no RoleManager** [14].
- **Lock**: `HomeFragment` uses `DevicePolicyManager.lockNow()` with `SecurityException` handling [7]; `MyAccessibilityService` additionally performs `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` when `SDK_INT >= P` and the event source is a `FrameLayout` whose content description equals `lock_layout_description`; `START_STICKY`; sets `lockModeOn = true` on connect [6].
- **Gestures**: `OnSwipeTouchListener : OnTouchListener` with `GestureDetector(c, GestureListener())`; `SWIPE_THRESHOLD = 100`, `SWIPE_VELOCITY_THRESHOLD = 100`; `onDoubleTap → onDoubleClick()`; long press via coroutine `delay(LONG_PRESS_DELAY_MS)` (= `500L` [9]); open callbacks `onSwipeRight/Left/onLongClick/onDoubleClick/onClick` [10]. Mapping: swipe left → app (fallback camera), right → app (fallback dialer), up → app list, down → search or notification drawer, double-click → lock, long-click → settings [7].
- **Home list rendering**: eight `TextView`s (`homeApp1..8`) in a `LinearLayout`; alignment via `binding.homeAppsLayout.gravity = horizontalGravity or verticalGravity` (bottom or center-vertical) [7]. Prefs: `HOME_APPS_NUM` (default 4), `HOME_ALIGNMENT` (default `Gravity.START`), `HOME_BOTTOM_ALIGNMENT`, `TEXT_SIZE_SCALE` (float 1.0), `BOLD_FONT`, `APP_THEME` (default `MODE_NIGHT_YES`), swipe toggles, `SWIPE_DOWN_ACTION`, `DAILY_WALLPAPER(_URL)`; home apps stored as 8 indexed slots × (`appPackageN`, `appActivityClassNameN`, `appUserN`) [8].
- **Search** (`AppDrawerAdapter` Filter): stage 1 `appLabel.contains(charSearch.trim(), true)`; stage 2 `appLabel.normalizeForSearch().contains(query.normalizeForSearch())` (removes diacritics and separators); auto-launch when `itemCount == 1 && autoLaunch && !isBangSearch && flag == FLAG_LAUNCH_APP`; `ListAdapter` + `submitList` [11]. Fragment: `adapter.allowAutoLaunch = !isSearchComposing()` (guards IME composition using `BaseInputConnection.getComposingSpanStart/End`), `isCjkKeyboard()` (zh/ja/ko subtype), keyboard shown in `onStart` per `prefs.autoShowKeyboard`, hidden while scrolling; submit → `launchFirstInList()`; `!` prefix → DuckDuckGo bang search [12].

### A.3 mLauncher (Multi Launcher) — details

- **Build**: `compileSdk = 37`, `minSdk = 28`, `targetSdk = 37`; plugins `android.application`, `kotlin.compose`, `ksp`; release `isMinifyEnabled = true`, `isShrinkResources = true`; deps: core-ktx, appcompat, activity-ktx, Compose (material/android/ui/foundation), Room (runtime/ktx/compiler via KSP), Moshi (+ codegen), Navigation fragment/ui, lifecycle-extensions/viewmodel-ktx, work-runtime-ktx, constraintlayout, biometric-ktx, material [19]. Catalog: `agp 9.3.2`, `kotlin 2.4.10-380` (as read), `core-ktx 1.19.0`, `appcompat 1.8.0`, `recyclerview 1.4.0`, `material 1.14.0`, `lifecycle-viewmodel-ktx 2.11.0`, `navigation 2.9.8`, `work-runtime-ktx 2.11.2`, `constraintlayout 2.2.2`, Compose `1.11.4`, `moshi 1.15.2`, `room 2.8.4`, `ksp 2.3.11` [20].
- **Manifest**: `QUERY_ALL_PACKAGES` (`tools:ignore="PackageVisibilityPolicy,QueryAllPackagesPermission"`), `INTERNET`, location/biometric/contacts/device-admin; **no `<queries>`**; `.services.ActionService` (AccessibilityService), `.listener.NotificationManager` (`NotificationListenerService` intent filter), `.helper.receivers.DeviceAdmin`, several app-widget receivers, `app.mlauncher.APPLY_ICONS` icon-pack intent; no ACTION_VIEW filters for theme/backup files [21].
- **Search** (`FuzzyFinder.kt`, custom, no library): `uppercase(Locale.getDefault())`; `Normalizer.normalize(input, NFD)` + regex strip of combining marks; query strips `[-_+,. ]`, targets replace `[-_+,.]` with spaces; **subsequence** match (`haystack[i] == needle[needleIdx]`); scoring per matched char: base 100, +90 consecutive, +80 word boundary (after space/period/underscore/hyphen/comma), +15 if index < 3; normalized to 0–1 by a perfect score that excludes spaces; sort by exact-prefix, word-start, score, first index, original position; drop if `score <= prefs.filterStrength` [23].
- **Gestures**: `GestureManager` extends `SimpleOnGestureListener`; distance normalised by `Constants.USR_DPIX/Y`; `isLongSwipe = distance > longThreshold`, short = in `shortThreshold..longThreshold`; 4 directions × short/long + single tap, double tap, long press [28].
- **Accessibility actions**: `performGlobalAction` for `GLOBAL_ACTION_LOCK_SCREEN`, `RECENTS`, `NOTIFICATIONS`, `QUICK_SETTINGS`, `POWER_DIALOG`, `TAKE_SCREENSHOT`; 1.5 s cooldown; `WeakReference` singleton [24].
- **Badges**: `NotificationManager : NotificationListenerService` counts `activeNotifications.groupingBy { it.packageName }.eachCount()` and calls `NotificationDotManager.setCount(pkg, count)`; **no** filtering of ongoing/group-summary [25]. `NotificationDotManager`: `mutableMapOf<String, Int>` + listener set; `registerListener` immediately delivers a snapshot [26].
- **Prefs/theming/export**: SharedPreferences (`prefsNormal`, `prefsOnboarding`); home apps as `${APP_NAME}_$id, ${APP_PACKAGE}_$id, ${APP_ACTIVITY}_$id, ${APP_USER}_$id`; enum alignments (home/clock/date); sizes `appSize 18`, `clockSize 42`, `dateSize 22`; colors (`backgroundColor, appColor, clockColor, batteryColor`), `fontFamily: Constants.FontFamily`; `saveToString()`/`loadFromString()` (Moshi JSON of all prefs), `saveToTheme(colorNames)` / `loadFromTheme(json)` validating `^#([A-Fa-f0-9]{8})$` ARGB hex [27]. Settings UI theme: Compose `CompositionLocalProvider(LocalReplacementTypography…, LocalReplacementColor…)`, dark/light only [29]. `Migration.kt` only migrates internal formats (`apps_cache.json`) [30].

### A.4 Unlauncher — details

- MIT; fork of Slim Launcher (six home apps + searchable drawer) [33][34]. Default branch `master`, last push 2026-01-24 [34].
- **Build**: `compileSdk = 35`, `minSdk = 21`, `targetSdk = 35`; release minify + shrinkResources; plugins Hilt, KSP, protobuf, ktlint, kover; deps `kotlin-stdlib 2.1.0`, `appcompat 1.7.0`, `recyclerview 1.3.2`, `constraintlayout 2.2.0`, `datastore 1.1.1` + `protobuf-javalite 4.29.2`, `core-ktx 1.15.0`, `fragment-ktx 1.8.5`, `lifecycle-livedata-ktx 2.8.7`, `room 2.6.1`, `hilt-android 2.54` [36].
- **Manifest**: `SET_ALARM`, `QUERY_ALL_PACKAGES`, `REQUEST_DELETE_PACKAGES`, `EXPAND_STATUS_BAR`, `SET_WALLPAPER`; `MainActivity` `singleTask`, `stateNotNeeded="true"`, HOME/DEFAULT/LAUNCHER; no `<queries>`, services or receivers [37].
- **Persistence**: Proto DataStore — `core_preferences.proto` (`activate_keyboard_in_drawer`, `keep_device_wallpaper`, `show_search_bar`, `search_bar_position` top/bottom, `show_drawer_headings`, `search_all_apps_in_drawer`, `clock_type`, `alignment_format` left/center/right, `time_format`, `theme` enum of 9 named themes, `hide_status_bar`) [39]; also `quick_button_preferences.proto`, `unlauncher_apps.proto` [35].
- **Search**: regex strips `[!@#$%^&*()_+\-=\[\]{};':"\\|,.<>/? ]` from both sides, then `contains(filterQuery, ignoreCase = true)`; apps whose display name `startsWith` the query sort first, else alphabetical; `getFirstApp()` exposed for the fragment (no auto-launch in adapter) [38].

### A.5 KISS — details

- **Build**: `minSdk 21`, `targetSdk 36`, `compileSdk 36`; plugins `com.android.application`, `net.ltgt.errorprone`; `minifyEnabled true` + `shrinkResources true` in **both** release and debug; deps `annotation 1.10.0`, `appcompat 1.7.1`, `preference 1.2.1`, `recyclerview 1.4.0`, `core 1.17.0`, `activity 1.11.0`, `fragment 1.8.9`, `desugar_jdk_libs 2.1.5`, errorprone [42].
- **Manifest**: `READ_CONTACTS`, `CALL_PHONE`, `READ_PHONE_STATE`, `EXPAND_STATUS_BAR`, `REQUEST_DELETE_PACKAGES`, `QUERY_ALL_PACKAGES` (`tools:ignore`), `ACCESS_HIDDEN_PROFILES`, `SET_ALARM`; no `<queries>`; `MainActivity` and `DummyActivity` both declare HOME; services `NotificationListener`, `LockAccessibilityService`, `IncomingCallScreeningService`, providers `AppProvider/ContactsProvider/ShortcutsProvider` [43].
- **Enumeration**: `for (UserHandle profile : manager.getUserProfiles()) … launcherApps.getActivityList(null, profile)`; quiet-mode check `isQuietModeEnabled(profile)` on N+ [51].
- **Fuzzy scoring** (`utils/fuzzy/`: `FuzzyFactory`, `FuzzyScore` interface, `FuzzyScoreV1`, `FuzzyScoreV2`, `MatchInfo`) [44]. Interface: setters for full-word/adjacency/separator/camel/first-letter bonuses and leading/unmatched penalties; `MatchInfo match(CharSequence)` / `match(int[] codepoints)` [48]. **V1** (greedy, Sublime-inspired): full-word bonus 100, adjacency 10, separator 5, camel 10, leading-letter penalty −3 (max −9), unmatched −1; tracks a "best letter" candidate for rematches; returns `match`, `score`, `matchedIndices` [45]. **V2** (default: pref `use-fuzzy-score-v1` default `false` [47]) is a port of forrestthewoods `lib_fts` `fuzzy_match.md` with **recursive** search of alternative matches: adjacency 15, separator 30, camel 30, first-letter 15, leading penalty −5 (max −30), unmatched −2, base 100 [46].
- **Normalization**: `Normalizer.normalize(buffer, NFKD)`, drop `NON_SPACING_MARK`/`COMBINING_SPACING_MARK`, skip `DASH_PUNCTUATION`, `Character.toLowerCase(codepoint)`, keep a position map for highlighting; ASCII fast path `codepoint <= 'z'` [49].
- **Query flow** (`AppProvider.requestResults`): `StringNormalizer.normalizeWithResult(query, false)` → `FuzzyFactory.createFuzzyScore(this, queryNormalized.codePoints)` → `fuzzyScore.match(pojo.normalizedName.codePoints)` → `pojo.updateMatchingRelevance(matchInfo, false)`; tags matched too; no numeric threshold beyond `match` [50].
- **Badges**: `NotificationListener` keeps `packageKey = sbn.getUser().hashCode() + "|" + packageName` → `HashSet` of notification ids, persisted with `editor.putStringSet(packageKey, …)`; filters `priority <= PRIORITY_MIN`, `FLAG_ONGOING_EVENT`, `FLAG_GROUP_SUMMARY`; on O+ channel badge settings override [52].
- **Lock**: `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` when `SDK_INT >= P`, triggered by `startService` intent `fr.neamar.kiss.LOCK` in `onStartCommand`; no DPM fallback [53].
- **Notification shade**: neither `SystemUiVisibilityHelper` (immersive/status-bar flags only) [54] nor `MainActivity` [55] contains the reflection call; where KISS expands the shade is **UNVERIFIED**.
- **Keyboard/IME UX**: nullable pref `showKeyboardOnFocus`; hardware QWERTY → request focus; Enter launches `adapter.getCount() - 1` (last item = top of the reversed list) or a web search when `always-default-web-search-on-enter` [55].
- **Icon packs**: `IconsHandler` queries `pm.queryIntentActivities(new Intent("fr.neamar.kiss.THEMES"))` and `new Intent("org.adw.launcher.THEMES")` with `GET_META_DATA` [56]; `IconPackXML.parseAppFilterXML()` handles `iconback` (attributes starting with `img`), `iconmask img1` (applied `PorterDuff.Mode.DST_OUT`), `iconupon img1`, `scale factor`, `item component/drawable`, `calendar prefix`; resources via `getResourcesForApplication` + `getIdentifier(name, "drawable", pkg)` [57].

### A.6 Kvaesitso — details

- Search-focused Compose launcher; GPL-3.0; 5.1k stars; modules `app/`, `core/` (base, compat, crashreporter, devicepose, i18n, ktx, permissions, preferences, profiles, shared), `data/` (applications, appshortcuts, calculator, calendar, contacts, currencies, customattrs, database, files, i18n, locations, **notifications**, plugins, search-actions, searchable, **themes**, unitconverter, weather, websites, widgets, wikipedia), `plugins/sdk` (Apache-2.0) [60][61][63][64].
- Catalog: `androidx-compose 1.13.0-alpha01`, `kotlin 2.4.10`, `android-gradle-plugin 9.3.1`, `androidx-room 2.8.4`, `koin 4.2.2`, `kotlinx-serialization 1.11.0`, `coil 2.7.0`, `ktor 3.5.2` (OkHttp engine), `androidx-datastore 1.2.1`, `androidx-work 2.11.2`, `com.aallam.similarity:string-similarity-kotlin:0.1.0` [62].
- **Theme model** (`data/themes`): sub-packages `colors/`, `shapes/`, `transparencies/`, `typography/`; files `DefaultThemes.kt`, `LegacySerialization.kt`, `Module.kt`, `Serialization.kt`, `ThemeBundle.kt`, `ThemeRepository.kt` [65]. `ThemeBundle(name: String, author: String?, colors: Colors?, typography: Typography?, shapes: Shapes?, transparencies: Transparencies?, version: Int = 2)`; `ThemeJson.encodeToString(this)`; on parse `if (version != 2) return fromLegacyJson(jsonElement)` (migrates `corePalette`, `lightColorScheme`, `darkColorScheme`); catches `SerializationException`/`IllegalArgumentException` and returns null [66]. `ThemeJson = Json { encodeDefaults = true; ignoreUnknownKeys = true; explicitNulls = false; isLenient = true; coerceInputValues = true; serializersModule … }`; custom string serializers for `Color`, `Shape`, `FontWeight` ("+"-prefixed relative weights vs absolute ints) with fallback defaults on failure [67]. Persistence: Room via `ColorsRepository(context, database)` etc.; backup writes `colors.0000`-style JSON files with `Json.Lenient`, restore deletes and re-inserts [68]. The user-facing share/import extension and MIME are **UNVERIFIED** (UI module not read; the docs page on color schemes has no import/export section [72]).
- **App search** (`AppRepository`): `launcherApps.getActivityList(packageName, userHandle)`, `LauncherApps.Callback` for package events, profiles tracked via `runningFold` add/remove; `stringNormalizer.normalize(query/label)` with cached results; `ResultScore.from(query, primaryFields = …)` and `if (score.score < 0.8f) return@mapNotNull null` [70]. The concrete similarity algorithm inside `ResultScore` is **UNVERIFIED** (class not read; the catalog's `string-similarity-kotlin` offers Levenshtein/Damerau/Jaro-Winkler/N-gram/Cosine/Jaccard etc., MIT [75][76]).

### A.7 Lawnchair — details

- "Taking Launcher3 … as a starting point"; based on Launcher3 from Android 16; default branch `16-dev`; not on F-Droid (IzzyOnDroid/Play/GitHub) [77][78]. `LICENSE.txt` is Apache License 2.0 [79].
- Build: `minSdk = 26`, `targetSdk = 37`, `compileSdk { version = release(37) { minorApiLevel = 1 } }`; `minifyEnabled true`, `shrinkResources true`; `implementation libs.xdrop.fuzzywuzzy`, Compose BOM, Room bundle + KSP, Hilt, Coil, protobuf-javalite, kotlinx-serialization-json, Retrofit bundle [80]. Catalog: `me.xdrop:fuzzywuzzy:1.4.0`, `compose-bom 2026.08.00`, `androidx-room 2.8.4`, `dagger 2.60.1`, `coil-compose 2.7.0`, `protobuf 4.36.1`, `kotlinx-serialization-json 1.11.0`, `retrofit 3.0.0`, `okhttp 5.5.0`, `kotlin 2.4.10`, `agp 9.4.0` [81].
- Search: `search/algorithms/{LawnchairASISearchAlgorithm, LawnchairAppSearchAlgorithm, LawnchairLocalSearchAlgorithm, LawnchairSearchAlgorithm, SearchUtils}.kt` [82]; `SearchUtils.fuzzySearch` → `FuzzySearch.extractSorted(queryTextLower, filteredApps, { it.sectionName + it.title }, WeightedRatio(), 65)`; `normalSearch` → `StringMatcherUtility.matches()` prefix/word matching; lowercase with `Locale.getDefault()`; hidden-app modes [84]; `enableFuzzySearch` preference selects; `maxResultsCount`; `createMarketSearchTarget(query)` Play-search fallback; shortcuts shown for a single app result [83].
- Badges (Launcher3 `NotificationListener`): static instance + `sIsConnected`; `Pair.create(PackageUserKey.fromNotification(sbn), NotificationKeyData.fromNotification(sbn))`; `notificationIsValidForUI()` rejects `!mTempRanking.canShowBadge()`, `FLAG_ONGOING_EVENT` (default channel), `FLAG_GROUP_SUMMARY`, missing title and text; `NotificationsChangedListener.onNotificationPosted/Removed/onNotificationFullRefresh`; `requestUnbind()` when dots disabled [85].

### A.8 Lunar Launcher — details

Exists (544 stars, Kotlin, GPL-3.0; weather, todo, RSS, Material 3) [88]. `minSdk 26`, `targetSdk/compileSdk 34`, ViewBinding, no Compose; deps `appcompat 1.6.1`, `biometric-ktx 1.2.0-alpha05`, `browser 1.8.0`, `core-ktx 1.12.0`, `core-splashscreen 1.0.1`, `lifecycle-runtime-ktx 2.7.0`, `material 1.10.0`, `ExpandableLayout 2.9.2` [89]. Manifest: `INTERNET`, **no QUERY_ALL_PACKAGES**, `<queries><intent><action android:name="android.intent.action.MAIN"/></intent></queries>`, `LockService` accessibility, `AdminReceiver` device admin; no notification listener [90].

### A.9 License compatibility for borrowing

- GPL-3.0-only: Olauncher [15], mLauncher [18], KISS [59], Kvaesitso (app; plugin SDK Apache-2.0) [60], Lunar [91], Fokus [95], Easy Launcher [94]. Copying code from these forces GPL-3 on justa-launcher; *ideas/algorithms/constants* can be re-implemented.
- Permissive: Unlauncher MIT [34], Slate MIT [93], Lawnchair Apache-2.0 per LICENSE.txt [79], `string-similarity-kotlin` MIT [76].
- `me.xdrop:fuzzywuzzy` POM license is **"GPL 2"** (gnu.org old-licenses/gpl-2.0) with no dependencies [175][174] — a copyleft dependency; avoid unless the whole app is GPL-2-compatible.

---

## B. Tech-stack comparison (evidence)

### B.1 Kotlin + Android Views (XML/programmatic, ViewBinding)

- View binding "generates a binding class for each XML layout file"; enabled with `buildFeatures { viewBinding = true }`; null- and type-safe; "view binding requires no annotation processing, so compile times are faster" [117]. Olauncher, Lunar use it [2][89].
- Real-world APKs of Views-based text launchers: Olauncher 2.13 MiB [16], Unlauncher 1.89 MiB [41], KISS 2.1 MiB [59], Slate 2.0 MiB [93], Lunar 2.5 MiB [91] — versus mLauncher (Views + Compose settings + Room + Moshi + Work) 6.75 MiB [32], Kvaesitso (Compose) 13.16 MiB [74], Lawnchair (Launcher3 + Compose) 16.5 MiB [86]. Feature scope differs, so treat as indicative only.
- Unshrunk AAR sizes of a minimal Views stack (Google Maven Content-Length) [118]: `activity 1.13.0` 222,940 B; `core 1.19.0` 1,604,981 B; `core-ktx 1.19.0` 5,530 B; `lifecycle-runtime-android 2.11.0` 85,066 B; `lifecycle-viewmodel-android 2.11.0` 68,783 B; `recyclerview 1.4.0` 452,493 B; optional `appcompat 1.8.0` 1,197,540 B + `appcompat-resources` 67,369 B; `fragment 1.9.0` 431,224 B; `constraintlayout 2.2.2` 507,990 B + `constraintlayout-core 1.1.2` 585,546 B; `material 1.14.0` 2,741,429 B.

### B.2 Kotlin + Jetpack Compose

- Current stable: Compose BOM **2026.08.00** → `ui`/`foundation`/`runtime`/`animation` **1.12.0**, `material3` **1.4.0** [109]; 1.12.0 released August 26, 2026 [110]. Compose compiler ships in the Kotlin Gradle plugin for Kotlin 2.0+ ("you don't have to check Compose to Kotlin compatibility") [111]. Strong skipping mode "is enabled by default in Kotlin 2.0.20": unstable params compared by `===`, lambdas auto-`remember`ed [99].
- Performance guidance: run in release mode with R8 ("enable optimizing and shrinking with the R8 compiler"); "Compose includes a default profile [Baseline Profile], but ideally, you should create an app-specific one"; "Since Compose 1.9.0, Compose and Views have the same jank rate" [97].
- **Hero benchmarks** (Pokedex app, Pixel 3a, Android 12, release + R8, fully precompiled): "Compose 1.11 is 2.5% slower than Views for TTID with a cold start"; "Compose 1.11 is 13.0% slower than Views for TTFD with a cold start"; scroll "Compose and Views achieve the same performance of 0.21% jank since Compose 1.9.0 … 1 out of 485 frames were janky" [98].
- First-party blog claims (label: engineering blog): Compose 1.12 (Aug '26) "Time to Initial Display … comparable to Views in our benchmarks" [102]; Compose 1.10 (Dec '25) "internal scroll benchmarks show that Compose now matches the performance you would see if using Views" [103]; "From version 1.9 of Jetpack Compose, scroll jank has dropped to 0.2%"; Trello −25% startup and Meta up to 40% metric improvements with Baseline Profiles [104]; Compose 1.6 "~20% improvement in scroll performance … ~12% improvement to startup time" [108]; the 1.8/1.9/1.11 posts contain no quantified numbers [106][105][107].
- **APK size**: Chris Banes' "Jetpack Compose — Before and after" (Tivi, minified R8 release with resource shrinking, APK Analyzer "APK file size"): "46% reduction in APK size, and 17% reduction in method count" (adjusted) / "41% reduction in APK size" (unadjusted); XML lines −76%; he attributes it to "how little minification tools can help when you need to keep all View classes around" [101]. Caveat: that app was a large Material/Views app; for a tiny text-only app the *floor* matters more. Unshrunk Compose AARs [118]: `ui-android` 4,047,839 B; `foundation-android` 4,441,825 B; `runtime-android` 1,997,054 B; `ui-graphics` 755,768; `ui-text` 826,233; `ui-unit` 100,428; `ui-geometry` 59,373; `ui-util` 22,858; `foundation-layout` 686,659; `runtime-saveable` 103,620; `animation` 687,085; `animation-core` 428,057; `activity-compose` 144,321 → ≈14.3 MB of AARs before R8 (plus `material3-android 1.4.0` 5,167,765 B if used; `material-ripple` 93,950). R8 removes most of this, but the retained Compose runtime/UI core is non-trivial and is the reason Compose apps ship Baseline Profiles.
- Baseline Profiles: "improve code execution speed by about 30% from the first launch"; installed by `androidx.profileinstaller` "on the first run when the app module defines this dependency"; shipped as `assets/dexopt/baseline.prof`; API 24–27 partial AOT via ProfileInstaller, API 28+ Play also uses Cloud Profiles; profile generation must use unobfuscated builds [100]. `profileinstaller 1.4.1` AAR 54,044 B, deps `annotation`, `concurrent-futures 1.1.0`, `startup-runtime 1.1.1` (19,371 B), `listenablefuture 1.0` [141][118].
- Memory: no first-party Compose-vs-Views memory numbers found — **UNVERIFIED**.
- Data-driven theming: trivially expressible via `CompositionLocal`s (mLauncher does exactly this for its settings theme [29]); Kvaesitso's whole theme model is Compose-native [65].

### B.3 Flutter

- Official FAQ: minimal app (single `Center`, `--split-per-abi`, release APK) "approximately 4.3 MB for ARM32, and 4.8 MB for ARM64" (March 2021); "core engine is approximately 3.4 MB (compressed)" (ARM32) / "4.0 MB" (ARM64); `classes.dex` 120 KB [112]. That floor alone exceeds every Views launcher above.
- Every launcher-critical API (LauncherApps, NotificationListenerService, AccessibilityService, WallpaperManager, RoleManager) must be reached through platform channels with host code in Kotlin/Java; "whenever you invoke a channel method, you must invoke that method on the platform's main thread" [113]. Native-only components (services) would be written in Kotlin anyway.

### B.4 React Native (Hermes)

- "Hermes is used by default by React Native"; "For many apps, using Hermes will result in improved start-up time, decreased memory usage, and smaller app size when compared to JavaScriptCore" — no numbers [114]. Platform APIs "that aren't provided by React Native" require Turbo Native Modules with Codegen and native Kotlin/Java implementations [115]. Same structural objection as Flutter, plus a JS engine in a resident home process.

### B.5 Kotlin Multiplatform / Compose Multiplatform

"Compose Multiplatform shares its API with Jetpack Compose"; Android is served "via Jetpack Compose"; iOS Stable, Web Beta [116]. For an Android-only launcher it adds nothing over Jetpack Compose and is irrelevant.

### B.6 Plain Java

KISS proves Java + Views yields a 2.1 MiB search launcher [42][59], but Java brings no size advantage over Kotlin once R8 is on (kotlin-stdlib 2.4.10 jar is 1,841,933 B unshrunk [173] and shrinks heavily), and Kotlin is the ecosystem default (Compose compiler is a Kotlin plugin [111]).

### B.7 Recommendation

**Primary ("lightest + most reliable"): Kotlin + Android Views, programmatic/minimal-XML UI, single Activity, no Fragments/Navigation/AppCompat/Material, R8 full mode + resource shrinking, minSdk 26–28.**
Rationale: the smallest shipping analogues are all Views-based (1.9–2.5 MiB) [16][41][59][93][91]; a text-only home screen is ~10 `TextView`s in a `LinearLayout` (Olauncher's exact approach [7]) plus one list; Views need no Baseline Profile to reach parity (Compose's own hero benchmark still shows +13% TTFD vs Views on 1.11 [98]); every hard feature (LauncherApps, NotificationListenerService, AccessibilityService, WallpaperManager, JobScheduler) is a platform API with zero dependencies. Data-driven theming in Views is a small "ThemeApplier" that sets `Typeface`, colors, `textSize`, gravity and padding on the few views at bind time (mLauncher stores exactly such tokens: alignment enums, sizes, ARGB colors, font family [27]).
Trade-offs: more imperative code; list diffing/animation by hand; you write your own settings UI (no Material widgets) — accept, or pull AppCompat only for the settings Activity.

**Fallback: Kotlin + Jetpack Compose (BOM 2026.08.00; `foundation` only, no `material3`) with an app-specific Baseline Profile.** Choose it if the team strongly prefers declarative UI or expects the settings/theme editor to become rich. Cost: several MB of unshrunk Compose AARs [118] (R8 mitigates), mandatory Baseline Profile/profileinstaller for startup [97][100], and a bigger resident heap (unquantified — UNVERIFIED).
Not recommended: Flutter/RN (engine floor 4.3–4.8 MB [112]; all launcher logic is native anyway [113][115]); KMP (irrelevant [116]); Java (no benefit).

---

## C. Minimal-dependency choices

### C.1 UI foundation

- `androidx.appcompat:appcompat:1.8.0` (Aug 12, 2026 [120]) POM pulls 18 artifacts: `activity 1.8.0`, `fragment 1.5.4`, `core 1.13.0`, `core-ktx`, `drawerlayout`, `cursoradapter`, `emoji2 1.3.0` + `emoji2-views-helper`, `lifecycle-runtime/viewmodel 2.6.1`, `savedstate`, `collection`, `resourceinspection-annotation`, `profileinstaller 1.4.0`, `appcompat-resources`, `kotlin-stdlib 2.1.20`, `jspecify`, `annotation` [119]. Its AAR is 1,197,540 B [118]. For a text-only launcher with data-driven theming, AppCompat's value (backported theming/night mode, Material-compatible widgets) is mostly unused → **skip it**.
- `androidx.activity:activity:1.13.0` (Mar 11, 2026 [122]) POM: `annotation`, `core-ktx 1.18.0`, `core-viewtree`, `lifecycle-common/runtime/viewmodel/viewmodel-savedstate 2.6.1`, `savedstate 1.2.1`, `tracing 1.0.0` (runtime), `navigationevent 1.0.0`, `profileinstaller 1.4.0` (runtime), `kotlinx-coroutines-core 1.9.0` (runtime), `kotlin-stdlib 2.1.20` [121]; AAR 222,940 B [118]. Use it (predictive back / result APIs) **or** use platform `android.app.Activity` for zero deps.
- `androidx.fragment:fragment:1.9.0` (Aug 12, 2026 [124]) pulls `activity-ktx 1.8.1`, `loader`, `viewpager`, lifecycle-*, `savedstate`, `collection`, `tracing 2.0.0`, `profileinstaller 1.4.0`, `arch.core-common` [123]; AAR 431,224 B [118]. Not needed for a single-screen launcher.
- Lists: `recyclerview 1.4.0` (Jan 15, 2025 [128]) POM: `annotation`, `collection`, `core 1.13.0`, `customview 1.0.0`, `customview-poolingcontainer`, `profileinstaller 1.3.1` [127]; AAR 452,493 B [118]. For 5–20 home rows use plain `TextView`s in a `LinearLayout` (Olauncher [7]); for 100–500 drawer rows a platform `ListView` with a view-holder adapter has zero dependency cost, while RecyclerView buys `ListAdapter`/`DiffUtil` (Olauncher uses `ListAdapter.submitList` [11]). Either is fine; ListView is the zero-dep default.
- `constraintlayout 2.2.2` (Jul 29, 2026 [130]) pulls `appcompat 1.2.0`, `constraintlayout-core 1.1.2`, `core 1.3.2`, `profileinstaller 1.4.0` [129]; 507,990 + 585,546 B [118] — unnecessary for a vertical text stack.
- Navigation `2.10.0` (Aug 26, 2026) [131] — unnecessary (single Activity).
- Lifecycle `2.11.0` (Jun 17, 2026); "The APIs in lifecycle-extensions have been deprecated" [133]. `lifecycle-viewmodel 2.11.0` POM: `annotation 1.9.1`, `atomicfu 0.28.0`, `collection 1.5.0`, `kotlinx-coroutines-core 1.9.0`, `kotlin-stdlib 2.1.20` [132]; AAR 68,783 B [118]. A `ViewModel` is optional; a plain retained object works for one Activity.

### C.2 Preferences / persistence

- `SharedPreferences` is **not deprecated**, but the reference says: "The Android team strongly recommends against using SharedPreferences for new data storage needs. Instead, consider using Jetpack DataStore … or Room … There are several significant drawbacks … UI Thread Blocking and ANRs: Since SharedPreferences provides a synchronous API…" [143]. Olauncher, mLauncher and KISS all still use it [8][27][52].
- DataStore **1.2.1** (Mar 11, 2026); Preferences DataStore "does not require a predefined schema … has a SharedPreferences-like API but doesn't have the drawbacks"; Proto DataStore needs `.proto` schemas [139][140]. Transitives (POMs): `datastore-preferences 1.2.1` → `datastore-preferences-android` (aar), `datastore-preferences-core`, `datastore`, `kotlinx-coroutines-core 1.9.0`, `kotlin-stdlib 2.0.21` [138]; `datastore-preferences-core 1.2.1` → `okio 3.9.1`, `kotlinx-serialization-core 1.7.3`, **`kotlinx-serialization-protobuf 1.7.3`**, `datastore-core`, `datastore-core-okio` [138]; `datastore-core 1.2.1` → `kotlinx-coroutines-core 1.9.0`, `atomicfu 0.27.0`, `annotation 1.9.1` [138]. Sizes: `datastore-core-android` 210,313 B, `datastore-preferences-core-jvm` 40,046 B, `datastore-preferences-android` 18,741 B [118] plus Okio (`okio-jvm 3.15.0` 376,949 B) and serialization runtime. So Preferences DataStore ≠ protobuf-javalite anymore, but it does drag in Okio + kotlinx-serialization-core/protobuf.
- Plain JSON file: zero AndroidX deps; pairs naturally with the theme format (one serializer). Unlauncher shows the opposite extreme (Proto DataStore + protobuf-javalite + Hilt) at 1.89 MiB [36][41], so any of these fit the size budget; the decision is about main-thread safety and simplicity.
- Room `2.8.4` (Nov 19, 2025) [137]; `room-runtime` POM → `sqlite 2.6.2`, `sqlite-framework 2.6.2`, `room-common`, `kotlinx-coroutines-core 1.8.1`, `atomicfu`, `collection 1.5.0`, `annotation` [136]; `room-runtime-android` AAR 568,456 B + `sqlite-framework-android` 60,894 B [118]. **Not needed**: the app list should come from `LauncherApps` at startup and stay in memory; only user settings persist.

**Recommendation**: `SharedPreferences` (`apply()`, read once at startup) for scalars if you want zero deps, **or** a single versioned JSON settings document written atomically — the latter is what I'd pick because it doubles as backup/export and reuses the theme serializer. Avoid Room and DataStore.

### C.3 Background work (wallpaper rotation)

- `work-runtime 2.11.2` (Mar 25, 2026; "requires compileSdk 33 or higher") [135] POM: compile `annotation-experimental`, `jspecify`, `listenablefuture 1.0`, `kotlin-stdlib 2.1.20`, `startup-runtime 1.1.1`, `core 1.12.0`, `lifecycle-livedata 2.6.2`, `kotlinx-coroutines-android 1.9.0`; runtime `tracing-ktx 1.2.0`, **`room-runtime 2.7.0`**, `concurrent-futures-ktx 1.1.0`, `lifecycle-service 2.6.2` [134] (Room in turn brings `androidx.sqlite`, see above). AAR 1,915,957 B [118]. Olauncher and mLauncher use it for the daily wallpaper [13][19].
- Platform `JobScheduler` (API 21, zero dependency): "an API for scheduling various types of jobs … executed in your application's own process … When the criteria declared are met, the system will execute this job on your application's JobService" [144]; `JobInfo.getMinPeriodMillis()` (API 24) "Query the minimum interval allowed for periodic scheduled jobs. Attempting to declare a smaller period … will run with this effective period" [145] (the concrete 15-minute value was not in the text I read — UNVERIFIED). Guidance: "In most cases, the best option … is to use WorkManager, though in some cases it may be appropriate to use the platform JobScheduler API" [146].
- **Recommendation**: `JobScheduler` periodic job with network constraint for "fetch next image and set wallpaper" — saves WorkManager + Room + SQLite + startup + livedata.

### C.4 Networking and images

- `HttpURLConnection` (platform) suffices for one GET; OkHttp `okhttp-jvm 5.5.0` jar is 961,167 B + `okio-jvm 3.15.0` 376,949 B [173] (OkHttp docs site returned 404 during research). Coil 3 "only depends on Kotlin, Coroutines, and Okio" and needs a networking library (`coil-compose 3.6.2`, `coil-network-okhttp`) [182] — overkill for one wallpaper.
- Decode with `ImageDecoder` (API 28; "A class for converting encoded images (like PNG, JPEG, WEBP, GIF, or HEIF) into Drawable or Bitmap objects"; `createSource(File)`/`(ContentResolver, Uri)`) [157] or `BitmapFactory` below 28. Apply with `WallpaperManager.setStream(InputStream, Rect, boolean, int which)` (API 24; "Requires Manifest.permission.SET_WALLPAPER"; "This data can be in any format handled by BitmapRegionDecoder"; data "is copied into persistent storage"; 3-arg form: "Currently the data must be either a JPEG or PNG image"; `FLAG_SYSTEM`/`FLAG_LOCK` API 24) [165] — no decoding in-process at all for the common case. `SET_WALLPAPER` is a normal permission [167]. Olauncher instead decodes and calls `setBitmap(...)` per flag [5].

### C.5 JSON (theme schema)

- `org.json` (platform): zero deps, no schema/versioning help.
- **kotlinx.serialization**: compiler plugin + runtime; "reflectionless serialization"; ships ProGuard rules (caveat: classes with *named* companion objects need extra rules; separate rule sets for R8 full mode) [176]. `Json` options: `ignoreUnknownKeys` ("By default, unknown keys … produce an error"), `coerceInputValues` (treats null for non-null and unknown enum values as missing → default), `explicitNulls`, `isLenient`, `encodeDefaults` [177]. Jar sizes: `kotlinx-serialization-json-jvm 1.11.0` 294,166 B + `core-jvm` 404,509 B [173]; latest on Maven Central `1.12.0-RC`, README states 1.12.0 current [173][176]. Kvaesitso's theme JSON uses exactly this with `ignoreUnknownKeys/coerceInputValues/explicitNulls=false/isLenient` [67].
- Moshi 1.15.2 (jar 162,258 B [173]): codegen via KSP "generates a small and fast adapter"; the reflection adapter "transitively depends on the kotlin-reflect library which is a 2.5 MiB .jar" (actual `kotlin-reflect 2.4.10` jar 3,750,100 B [173]); keep rules needed for reflective classes [178]. mLauncher uses Moshi + codegen [19].
- Gson 2.14.0 (jar 313,604 B [173]): reflection-based; R8 renames fields ("JSON properties have seemingly random names such as a, b"), needs keep rules or `@SerializedName`; no-args constructor/`Unsafe` caveats [179].
- **Recommendation**: kotlinx.serialization JSON for a versioned theme/settings schema (compile-time adapters, no reflection, tolerant decoding flags, R8-friendly). Validation of ranges/enums is done in code after decoding.

### C.6 Coroutines and DI

- Coroutines are already runtime deps of `activity`, `core`, `lifecycle-viewmodel` (coroutines-core 1.9.0) [121][125][132]; `kotlinx-coroutines-core-jvm 1.11.0` 1,577,052 B, `-android` 17,819 B unshrunk [173]. Using them costs little extra; still, a text launcher can run on `Handler`/`Executor` plus `LauncherApps` callbacks.
- DI: Hilt needs the `com.google.dagger.hilt.android` plugin (2.57.1 in docs), KSP, `hilt-android` + `hilt-android-compiler`, Java 17 [180]. Koin: "pure Kotlin framework … doesn't impact your compilation time, nor require any extra plugin configuration" [181]; `koin-android 4.2.2` 277,522 B + `koin-core-jvm` 435,897 B [173]. **Recommendation: none (manual constructor wiring)**.

### C.7 Build configuration

- R8: "R8 full mode … is enabled by default. You can opt out using `android.enableR8.fullMode=false`"; AGP 9.1+: "R8 repackages classes … eliminating the need to specify `-repackageclasses`"; enable `isMinifyEnabled = true` + `isShrinkResources = true` (legacy DSL for AGP < 9.3); AGP 9.3 adds an optimization DSL where "Turning on optimization enables both code optimization and optimized resource shrinking", `src/<variant>/keepRules/*.keep` source sets, and `:app:analyzeReleaseR8Config` [147][208].
- APK vs AAB: "From August 2021, new apps are required to publish with the Android App Bundle on Google Play"; 4 GB compressed limit [150]. F-Droid builds from source and publishes APKs; reproducible builds let F-Droid ship the developer-signed APK (same toolchain, no timestamps, disable PNG generation from vector drawables) [200]. Plan: `bundleRelease` for Play, `assembleRelease` APK for GitHub/F-Droid.
- Baseline Profiles: see §B.2; Compose "includes a default profile" [97]; for Views optional. `profileinstaller` weight ≈54 KB AAR + `startup-runtime` 19 KB [118][141].
- Core library desugaring is for "a subset of java.time" etc. when minSdk is below the API [149]; `java.time.LocalDate` is "Added in API level 26" [169] → unnecessary at minSdk ≥ 26 (KISS with minSdk 21 needs `desugar_jdk_libs 2.1.5` [42]).
- 16 KB pages: Play requires 16 KB support for apps targeting Android 15+ (from Nov 1, 2025; updates blocked from Feb 1, 2027) but "If your app only uses code written in the Java programming language or in Kotlin, including all libraries or SDKs, then your app already supports 16 KB devices" [148] → irrelevant if we ship no `.so`.
- Play target-SDK: "New apps and app updates must target Android 16 (API level 36) or higher" (from Aug 31, 2026; extension to Nov 1, 2026) [215].

### C.8 Package visibility and launcher permissions

- Apps targeting API 30+ get filtered package queries; declare `<queries>`; "In the rare cases where the `<queries>` element doesn't provide adequate package visibility, you can use the `QUERY_ALL_PACKAGES` permission … subject to approval" on Play [151]. Play policy lists permitted uses as "device search, antivirus apps, file managers, and browsers" — launchers are **not** named — and requires the Permissions Declaration Form; undeclared use can lead to removal/suspension [152]. `QUERY_ALL_PACKAGES` is API 30, protection level normal [167].
- `LauncherApps` (API 21): "Class for retrieving a list of launchable activities for the current user and any associated managed profiles that are visible to the current user … This is mainly for use by launchers"; `getActivityList(String packageName, UserHandle user)` "Retrieves a list of activities that specify Intent.ACTION_MAIN and Intent.CATEGORY_LAUNCHER, across all apps, for a specified user"; `startMainActivity` API 21; `getProfiles` API 26; `getLauncherUserInfo` API 35 [162]. Whether `LauncherApps` results are subject to package-visibility filtering for a non-QUERY_ALL_PACKAGES app is not stated in what I read — **UNVERIFIED**; empirically Lunar Launcher ships with only `<queries><intent><action MAIN/></intent></queries>` [90] while Olauncher/KISS/mLauncher/Unlauncher declare `QUERY_ALL_PACKAGES` [4][43][21][37].
- Private space (Android 15): launcher must hold `ROLE_HOME` and declare `ACCESS_HIDDEN_PROFILES` (normal, API 35 [167]); separate container via `getLauncherUserInfo()`; lock/unlock via `UserManager.requestQuietModeEnabled()`; hide apps while locked; listen for `ACTION_PROFILE_AVAILABLE/UNAVAILABLE` (API 35 [171]) [154]. `requestQuietModeEnabled(boolean, UserHandle)` API 28, `isQuietModeEnabled` API 24, `getUserProfiles` API 21 [168].

### C.9 minSdk gating (verified API levels)

| Feature | API | Source |
|---|---|---|
| `RoleManager` / `ROLE_HOME` = `"android.app.role.HOME"`, `createRequestRoleIntent`, `isRoleAvailable/isRoleHeld` | 29 | [155] |
| `AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN` (=8), `GLOBAL_ACTION_TAKE_SCREENSHOT` | 28 | [156] |
| `GLOBAL_ACTION_NOTIFICATIONS`, `GLOBAL_ACTION_RECENTS` | 16; `GLOBAL_ACTION_POWER_DIALOG` 21 | [156] |
| `ImageDecoder` | 28 | [157] |
| `WallpaperColors` (HINT_SUPPORTS_DARK_TEXT / DARK_THEME) | 27 | [158] |
| `NotificationChannel` / `canShowBadge()` "whether notifications posted to this channel can appear as badges in a Launcher application" | 26 | [159] |
| `Typeface.Builder` (File/AssetManager/FileDescriptor) | 26; `Typeface.createFromFile` 4 | [160][161] |
| `java.time.LocalDate` | 26 | [169] |
| `java.text.Normalizer` | 9 | [170] |
| `LauncherApps` / `getActivityList` / `startMainActivity` | 21; `getProfiles` 26; `getLauncherUserInfo` 35 | [162] |
| `NotificationListenerService` (needs `BIND_NOTIFICATION_LISTENER_SERVICE`, signature-level, + `SERVICE_INTERFACE` filter) | 18 | [163][167] |
| `JobScheduler` | 21; `JobInfo.getMinPeriodMillis` 24 | [144][145] |
| `WallpaperManager.setStream(…, int which)`, `FLAG_LOCK/FLAG_SYSTEM` | 24 | [165] |
| `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT` | 19 | [171] |
| `ACCESS_HIDDEN_PROFILES` (private space) | 35 | [167] |
| `StatusBarManager` public class | 29 — public surface has no `expandNotificationsPanel` (only e.g. `showPowerMenu`, API 37) | [164] |
| `DevicePolicyManager.lockNow()` | 8 — "After this method is called, the device must be unlocked using strong authentication (PIN, pattern, or password). This API is intended for use only by device admins. From version R onwards, the caller must either have the LOCK_DEVICE permission or the device must have the device admin feature" | [166] |

Implications: **minSdk 26** covers fonts/badges/java.time; **minSdk 28** additionally makes lock-via-accessibility, `ImageDecoder`, quiet-mode toggling unconditional (mLauncher chose 28 [19]; Kvaesitso/Lunar/Lawnchair 26 [73][89][80]; Olauncher 24 [2]; KISS/Unlauncher 21 [42][36]). `RoleManager` (29) must stay a runtime check. Version-distribution shares: no primary web source found — **UNVERIFIED** (Android Studio's new-project wizard chart is offline).

- Notification shade: `EXPAND_STATUS_BAR` is a normal permission (API 1) [167] but `expandNotificationsPanel` is not part of the public `StatusBarManager` API [164]; Olauncher's reflection [5] therefore targets a non-SDK interface, where restricted members yield `NoSuchMethodException` and the "unsupported" list can be conditionally blocked in future releases [153]. Safer: `GLOBAL_ACTION_NOTIFICATIONS` via the accessibility service (mLauncher [24]) with reflection as an optional fallback.
- Lock: prefer `GLOBAL_ACTION_LOCK_SCREEN` (API 28) over device-admin `lockNow()` because the latter forces strong-auth unlock and is admin-only [166]; Olauncher keeps both [7][6], KISS and Lunar use accessibility only [53][90].

---

## D. Fuzzy search for short app names

### D.1 Algorithms

- **fzf** (`algo.go` header): V1 is O(n) — "finds the first 'fuzzy' occurrence of the pattern … then traverses backwards to see if there's a shorter substring"; V2 is "a modified version of Smith-Waterman algorithm to find the optimal solution (highest score)" where "omission or mismatch of a character in the pattern is not allowed", O(nm) when a match exists; constants `scoreMatch=16`, `scoreGapStart=-3`, `scoreGapExtension=-1`, `bonusBoundary=8`, `bonusNonWord=8`, `bonusCamel123=7`, `bonusConsecutive=4`, `bonusFirstCharMultiplier=2` ("the first character in the typed pattern has more significance") [183].
- **Sublime-Text-like** (`lib_fts/fuzzy_match.md`, the basis of KISS V2): letters score points, non-matching letters lose points, bonuses for sequential matches, after separators, and camelCase; author reports ~30 ms for 355,000 strings on a Core i5; "Fuzzy matching by its very nature requires looking at every byte" [184]. KISS V1/V2 constants are in §A.5 [45][46]; mLauncher's simpler variant (100/+90/+80/+15) in §A.3 [23].
- **Levenshtein / Damerau / Jaro-Winkler / n-gram / cosine / Jaccard**: all available in `string-similarity-kotlin` (MIT, 36,276 B jvm jar) [75][76][173]; `fuzzywuzzy` implements ratio/partialRatio/tokenSort/tokenSet/weightedRatio on Levenshtein (GPL-2, 38,616 B jar, no deps) [174][175][173]. Edit-distance ratios penalise abbreviations ("gm" vs "Google Maps") because distance ≈ length difference — that is why Lawnchair pairs `WeightedRatio` with a 65 cutoff *and* a separate prefix/word matcher [84], and why KISS/mLauncher/fzf use subsequence scoring with word-initial bonuses instead (reasoning from the definitions above, not a cited benchmark).
- **Normalization**: `java.text.Normalizer.normalize(src, Form.NFD|NFKD)` decomposes "A-acute" into `U+0041 U+0301` [185]; strip `Character.getType()` ∈ {NON_SPACING_MARK, COMBINING_SPACING_MARK} and dashes as KISS does [49]; lower-case per code point / `Locale.ROOT` (mLauncher's `uppercase(Locale.getDefault())` [23] and Lawnchair's `lowercase(Locale.getDefault())` [84] are locale-sensitive — a Turkish-locale pitfall; KISS's `Character.toLowerCase(codepoint)` is locale-independent [49]). CJK: code-point subsequence matching works on CJK labels as-is; transliteration (pinyin/romaji) needs tables — out of scope; Olauncher's IME-composition guard for zh/ja/ko [12] is the relevant UX lesson.

### D.2 Who uses what

| Launcher | Class | Algorithm | Library |
|---|---|---|---|
| KISS | `FuzzyScoreV2` (default) / `V1` | recursive Sublime-style (lib_fts port) / greedy Sublime-style; NFKD normalization | none [45][46][47][49] |
| mLauncher | `FuzzyFinder.kt` | subsequence with consecutive/word-boundary/early bonuses, normalized 0–1, prefix/word-start sort keys, `filterStrength` cutoff | none [23] |
| Lawnchair | `SearchUtils.fuzzySearch` | `FuzzySearch.extractSorted(..., WeightedRatio(), 65)`; `normalSearch` prefix/word matching | `me.xdrop:fuzzywuzzy:1.4.0` [84][81] |
| Kvaesitso | `AppRepository` → `ResultScore.from` | similarity score, cutoff 0.8 (algorithm class not read) | catalog: `string-similarity-kotlin 0.1.0` [70][62] |
| Olauncher | `AppDrawerAdapter` filter | `contains` (raw, then diacritic/separator-stripped) | none [11] |
| Unlauncher | `AppDrawerAdapter` | punctuation-stripped `contains`, `startsWith` first | none [38] |

### D.3 Recommendation and performance expectation

Hand-roll a ~150-line scorer: normalize labels once (NFD → strip marks → lower per code point, cached per app), then per keystroke run an fzf-V2/Sublime-style subsequence match with bonuses for word-initials (space/`-`/`_`/camel), consecutive runs, prefix, and a first-character multiplier; reject non-subsequences. Complexity is O(n·m) per label (fzf V2 [183]) with n ≈ label length (≤ ~30 code points) and m ≈ query (≤ ~10): ≤ 300 cell updates per label, ≈150k for 500 labels — comfortably on the main thread per keystroke (arithmetic from the cited complexity; not a measured benchmark). Skip `fuzzywuzzy` (GPL-2 [175]; Levenshtein-centric [174]).

### D.4 UX behaviours seen in references

- Auto-open single match: Olauncher (`itemCount == 1 && autoLaunch && !bang`, suppressed while the IME is composing) [11][12].
- Keyboard auto-show: Olauncher pref `autoShowKeyboard`, shown in `onStart` and when scrolled to top [12]; KISS pref `showKeyboardOnFocus`, hardware-keyboard focus [55]; Unlauncher pref `activate_keyboard_in_drawer` [39].
- IME action launches top result: Olauncher `launchFirstInList()` on submit [12]; KISS Enter → last adapter item (top of reversed list) [55].
- Fallback web/store search: Olauncher `!` bangs → DuckDuckGo [12]; KISS `always-default-web-search-on-enter` [55]; Lawnchair `createMarketSearchTarget(query)` [83].

---

## E. Theming & sharing (community themes)

### E.1 Existing formats and lessons

- **Icon packs (ADW convention)**: discovered via `queryIntentActivities(new Intent("org.adw.launcher.THEMES"))` (KISS also `fr.neamar.kiss.THEMES`) [56]; content is an APK whose resources are read with `getResourcesForApplication` + `getIdentifier`, driven by `appfilter.xml` (`iconback`, `iconmask`, `iconupon`, `scale`, `item component="…" drawable="…"`, `calendar prefix`) [57]. Lesson: a *declarative manifest + assets* package resolved by name is robust and needs no code execution — but APK packaging forces a developer account/signing on theme authors; a data file is friendlier.
- **Kustom**: the documented "KAPK walls" distribution is a cloud JSON list (`author`, `url`, `name`, optional `thumbnail`) hosted on GitHub/BitBucket and consumed by the APK Maker; "The format is the same used by Polar Dashboard" [202]. The `.klwp/.kwgt` internal structure is **UNVERIFIED** (not in the pages read). Lesson: a static JSON index on a Git host is an accepted community-gallery pattern.
- **Kvaesitso**: `ThemeBundle` JSON with `version: 2`, optional `colors/typography/shapes/transparencies`, `name/author`; tolerant `Json` config; legacy-version migration path; string-encoded colors/shapes/font weights with fallbacks [66][67]. Lesson: version field + tolerant decoder + explicit legacy migration.
- **mLauncher**: full settings dump via Moshi and a colour-only theme export validated with `^#([A-Fa-f0-9]{8})$` [27]. Lesson: validate token syntax on import.
- **Unlauncher**: nine fixed named themes in a proto enum [39] — the non-extensible baseline to beat.
- Lawnchair/Niagara/Nova/Smart Launcher/Total Launcher official theme-format docs: **not researched/UNVERIFIED**.

### E.2 Proposed declarative theme format (design)

Package = either a single `theme.json` or a zip (`*.justatheme`) containing `theme.json` + `fonts/*.ttf|otf` (+ optional `background/*.jpg|png`). Schema sketch (kotlinx.serialization; `ignoreUnknownKeys=true`, `coerceInputValues=true` for forward compatibility [177]):

```json
{
  "schema": 1,                    // schema version; refuse if > supported
  "minAppVersion": 10,            // versionCode gate, like Kvaesitso's version check [66]
  "meta": {"name": "Paper", "author": "…", "license": "CC0-1.0", "homepage": "…"},
  "colors": {"background":"#FF101010","text":"#FFEDEDED","textMuted":"#80EDEDED",
             "accent":"#FFFFC107","badgeBg":"#FFFFC107","badgeText":"#FF000000"},   // #AARRGGBB, validated by regex as mLauncher does [27]
  "typography": {"family":"sans-serif|serif|monospace|file:fonts/Inter.ttf",
                 "weight":400, "sizeSp":24, "letterSpacingEm":0.0, "lineHeightMult":1.3,
                 "transform":"none|lowercase|uppercase"},
  "layout": {"hAlign":"start|center|end","vAlign":"top|center|bottom",
             "paddingDp":{"h":24,"v":48},"rowGapDp":8,"listMaxRows":8},
  "background": {"mode":"wallpaper|color|gradient|image",
                 "color":"#FF000000","gradient":{"angle":90,"stops":["#FF000000","#FF202020"]},
                 "image":"background/paper.jpg","dimAlpha":0.2},
  "badge": {"style":"circle|dot|number","sizeDp":18,"position":"end|start"}
}
```

- Fonts: system families via `Typeface.create(name, style)`; bundled files via `Typeface.Builder(File)` (API 26) [160] or `Typeface.createFromFile` (API 4) [161]; downloadable Google Fonts need the Google Play services provider ("A device must have Google Play services version 11 or higher") [201] → GMS-only, forbidden as a hard dependency on F-Droid [199]; keep optional.
- Security of untrusted fonts/images: Android's font stack has had real bugs: CVE-2016-0808 and CVE-2016-2414 ("An attacker could cause an untrusted font to be loaded and cause an overflow in the Minikin component which leads to a crash", High, Android 5.0–6.0.1) [203][204]; CVE-2016-10244 "A remote code execution vulnerability in Freetype could enable a local malicious application to load a specially crafted font to cause memory corruption in an unprivileged process" (High, Android 4.4.4–7.0) [205]. Mitigations: cap file sizes (e.g., ≤ 2 MB per font, ≤ 5 MB image), check magic bytes/extensions, decode images with bounds-first `BitmapFactory`/`ImageDecoder` target sizing [157], and offer "fonts from community themes" as an opt-in; running validation in an isolated process is an idea I could not source (UNVERIFIED). Note the 2016–17 CVEs are patched on any supported device; the risk today is unknown future bugs, hence limits + opt-in.
- Compatibility: unknown fields ignored, missing fields defaulted (`coerceInputValues`) [177]; `schema` bump only for breaking changes; keep a legacy-migration function like Kvaesitso's `fromLegacyJson` [66]; `minAppVersion` lets new themes fail gracefully on old apps. Font licensing for bundled fonts: OFL 1.1 permits fonts to "be bundled, redistributed and/or sold with any software" provided the copyright notice and license are included; Reserved Font Names apply to modified versions; fonts "may not be sold by itself" [198] → require a `license` field and ship the OFL text alongside embedded fonts.

### E.3 Sharing mechanisms

- **SAF export/import**: `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT` (API 19 [171]) with `CATEGORY_OPENABLE`, `type = <MIME>`, `EXTRA_TITLE`, optional `EXTRA_INITIAL_URI`; "this mechanism doesn't require any system permissions"; `takePersistableUriPermission` for durable access; `ACTION_CREATE_DOCUMENT` "cannot overwrite an existing file … appends a number" [186]. Use `application/json` for `theme.json` and `application/zip` for packages; a vendor type like `application/vnd.justa.theme+json` may not be understood by document providers (UNVERIFIED).
- **Share sheet (send)**: `ACTION_SEND` + `EXTRA_STREAM` content URI + `type`, wrapped in `Intent.createChooser`; provide the file through `FileProvider` with per-URI grants and `FLAG_GRANT_READ_URI_PERMISSION` [190]. `FileProvider` declaration: `androidx.core.content.FileProvider`, `android:authorities`, `android:grantUriPermissions="true"`, `android:exported="false"`, `<meta-data android:name="android.support.FILE_PROVIDER_PATHS" android:resource="@xml/filepaths"/>` [192] (needs `androidx.core`, already in the stack).
- **Receive**: `<intent-filter>` with `ACTION_SEND` + `DEFAULT` + `<data android:mimeType="…"/>`, read via `IntentCompat.getParcelableExtra(intent, EXTRA_STREAM, Uri::class.java)` [191]; for files opened from file managers add `ACTION_VIEW` filters. `<data>` rules: `mimeType` matching "is case-sensitive … always specify MIME types using lowercase"; "If the filter has a data type set … but no scheme, the content: and file: schemes are assumed"; `pathPattern` wildcards are limited ("No Backtracking", "`.*` is Lazy", "`*` is Greedy"; double-escape `\\.`); `pathAdvancedPattern` API 31+; `pathSuffix` available; "safer … to use `android:pathPrefix`" for prefixes [187]. Plan: match on MIME (`application/json`, `application/zip`, `application/octet-stream`) *and* a `pathSuffix=".justatheme"` filter on `content`/`file` schemes.
- **Deep links / App Links**: custom scheme `justa://theme?d=<base64url>` requires `ACTION_VIEW` + `DEFAULT` + `BROWSABLE` + `<data android:scheme="justa" …/>`; custom schemes "can still trigger the disambiguation dialog if another app registers the same custom scheme"; `http(s)` links "on Android 12 and higher … will almost always trigger the disambiguation dialog … likely to be handled by the user's web browser by default" unless verified App Links [188]. App Links need `android:autoVerify="true"` (API 23+), HTTPS, and `https://hostname/.well-known/assetlinks.json` [189] — i.e., a domain you control (GitHub Pages can serve static files [195], but hosting `.well-known` there is UNVERIFIED).
- **QR codes**: capacity table not extractable from qrcode.com (page confirms versions 1–40 and per-version capacity by data type and EC level, but the figures were not in the fetched text) — the commonly cited maxima are **UNVERIFIED** here [206]. Practical design: encode a short gallery URL/ID, not the theme itself. Generation: ZXing `core 3.5.4` is a 610,364 B jar with no runtime deps [173] (license block absent from the POM read — Apache-2.0 status UNVERIFIED); a minimal in-app QR encoder is feasible but not sourced. Scanning: Google code scanner "provides a complete solution for scanning code without requiring your app to request camera permission", `com.google.android.gms:play-services-code-scanner:16.1.0`, min API 23, "an unbundled library that must be downloaded" [193] — GMS-only (F-Droid anti-feature territory [199]); FOSS alternative = `CAMERA` permission + ZXing/ML-free decoder, or simply "paste the link".
- **Community gallery**: static `index.json` on GitHub Pages / raw.githubusercontent (Pages: ~1 GB repo, soft 100 GB/month bandwidth, soft 10 builds/hour, no commercial transactions) [195]; do **not** hit the GitHub REST API from clients ("60 requests per hour" unauthenticated, 5,000 authenticated) [194].
- **Play policy**: "An app may not download executable code (such as dex, JAR, .so files) from a source other than Google Play … This restriction does not apply to code that runs in a virtual machine or an interpreter … (such as JavaScript in a webview or browser)" [196] → JSON/zip themes are data and fine; never make themes scriptable in a way that downloads code. Play category "Personalization: Wallpapers, live wallpapers, home screen, lock screen, ringtones" [197].
- **F-Droid**: all apps must be FLOSS; "proprietary tracking or advertising libraries and analytics tools such as Google Play Services and Firebase and Crashlytics … are strictly forbidden" (use a flavour without them); 100 % FLOSS toolchain; non-functional assets may use less restrictive licences but "must allow redistribution"; Anti-Features labels; prebuilt binaries only from trusted sources like Maven Central [199]. Reproducible builds: keep the toolchain identical, avoid timestamps, disable PNG generation from vector drawables, F-Droid verifies by copying the developer signature onto its rebuild [200].

---

## F. Toolchain as of 2026-09-06

| Component | Current stable | Source |
|---|---|---|
| Android Gradle Plugin | **9.4.0** (September 2026); min Gradle 9.6.0; JDK 17; SDK Build Tools 36.0.0; NDK 28.2.13676358; max API level 37 | [207] |
| (previous) AGP 9.3.0 | July 2026; min Gradle 9.5.0; JDK 17; new optimization DSL, keepRules source sets, `analyzeReleaseR8Config` | [208] |
| Gradle | **9.7.1** (Aug 19, 2026); 9.7.0 Aug 6; 9.6.1 Jun 26 | [209] |
| Kotlin | **2.4.10** (Jul 14, 2026; bug-fix for 2.4.0 of Jun 3, 2026); K2 default since 2.0.0 (May 21, 2024) | [210] |
| Compose | BOM **2026.08.00** → ui/foundation/runtime/animation **1.12.0**, material3 **1.4.0** (Aug 26, 2026); compiler = Kotlin plugin | [109][110][111] |
| AndroidX | core 1.19.0 (Jun 3, 2026); appcompat 1.8.0 (Aug 12, 2026); activity 1.13.0 (Mar 11, 2026); fragment 1.9.0 (Aug 12, 2026); lifecycle 2.11.0 (Jun 17, 2026); recyclerview 1.4.0 (Jan 15, 2025); constraintlayout 2.2.2 (Jul 29, 2026); navigation 2.10.0 (Aug 26, 2026); work 2.11.2 (Mar 25, 2026); datastore 1.2.1 (Mar 11, 2026); room 2.8.4 (Nov 19, 2025); profileinstaller 1.4.1 (Oct 2, 2024) | [126][120][122][124][133][128][130][131][135][139][137][142] |
| Android Studio | Download page: "Android Studio Quail 4 \| 2026.1.4"; release-notes archive's newest entry: "Quail 3 \| 2026.1.3 Patch 1 (August 2026)"; bundled AGP mapping not obtained | [211][212] |
| compileSdk/targetSdk | SDK Platform release notes list **Android 16 (API 36)** as newest; the API 37 SDK exists (AGP 9.4 supports API 37; reference docs show "Added in API level 37"/"37.2"; mLauncher and Lawnchair compile against 37) but developer.android.com/about/versions/17 still uses Beta/QPR-beta wording → **stable status of Android 17 UNVERIFIED** | [213][207][167][164][19][80][214] |
| Play requirement | Target API 36+ for new apps/updates from Aug 31, 2026 (existing apps ≥ 35; extension to Nov 1, 2026) | [215] |
| Minimum JDK for AGP | 17 | [207] |

Practical choice: `compileSdk 36` (stable SDK), `targetSdk 36` (Play requirement [215]), AGP 9.4.0 + Gradle 9.7.1 + Kotlin 2.4.10, JDK 17; move to 37 once the platform release notes list it.

---

## Consolidated recommendation

1. **Stack**: Kotlin 2.4.10, Views (programmatic + ViewBinding for the few layouts), `androidx.activity` + `androidx.core` only (or pure platform `Activity`); no AppCompat/Fragment/Navigation/Material/ConstraintLayout; platform `ListView` (or RecyclerView if DiffUtil is wanted). Fallback: Compose 1.12 `foundation` with a Baseline Profile.
2. **Data**: `LauncherApps` + `LauncherApps.Callback` for the app list (no persistence); one JSON settings/theme document via kotlinx.serialization (or SharedPreferences); no Room/DataStore.
3. **Background**: `JobScheduler` periodic job → `HttpURLConnection` → `WallpaperManager.setStream`.
4. **System integrations**: `NotificationListenerService` with Launcher3/KISS-style filters (`canShowBadge`, ongoing, group summary); `AccessibilityService` for lock (API 28) and notification shade (`GLOBAL_ACTION_NOTIFICATIONS`) with reflection as optional fallback; `RoleManager` on 29+ else settings intent; `<queries>` first, `QUERY_ALL_PACKAGES` only if proven necessary (Play flavour without it).
5. **Search**: hand-rolled subsequence scorer with word-initial/consecutive/prefix bonuses, NFD normalization, locale-independent lowercasing; UX: auto-launch single match (guard IME composition), IME action launches top result, optional web-search fallback.
6. **Themes**: versioned JSON (+ optional zip with OFL fonts) with tolerant decoding and size limits; share via SAF/ACTION_SEND/FileProvider, receive via MIME + `pathSuffix` filters and a `justa://` deep link; static JSON gallery on GitHub Pages; no downloaded code.
7. **Build**: R8 full mode + `isShrinkResources`,
