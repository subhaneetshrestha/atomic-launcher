> Provenance: planning-time design written by a Plan agent on 2026-09-06 from the research note and the scoping decisions in ADR 0001, before any code existed. Copied verbatim from the agent transcript. Where the code and this document disagree, the code and its tests are authoritative; update this file or add an ADR.

# justa-launcher — Phase 0 (repo, build, CI) and Phase 1 (home skeleton) plan

Scope: Part 6 phases 0–1 of `/home/hyzii/.claude/plans/i-want-to-create-mighty-swan.md` (the "synthesis"), plus the local-environment checklist and the release-engineering choices that must be right from day one. Citations: "synthesis §x.y / line N" = the synthesis file; "R1 §A1" = platform-fundamentals report; "R2 §D5" = gestures/typography report; "R3 §C.7" = tech-stack report.

Environment finding that shapes section E: the machine has **git 2.55.0 only** — no JDK, Gradle, Android SDK, adb, Android Studio, or AVDs. `/dev/kvm` is present and world-rw (emulator acceleration works), 16 cores, 14 GB RAM, 401 GB free. Arch repos offer `jdk17-openjdk 17.0.20`, `android-tools 37.0.0`, `android-udev`.

---

## A. Repo layout — every file created in Phase 0

Placeholder identifiers (confirm before first release, see Open questions): namespace + applicationId `app.justa.launcher`; repo name `justa-launcher`.

```
/home/hyzii/Projects/justa-launcher/
├── .editorconfig
├── .gitignore
├── LICENSE                                   Apache-2.0 full text (decision 5)
├── README.md                                 stub: what/why, build prereqs (JDK 17), ./gradlew commands, size badge text, license
├── settings.gradle.kts
├── build.gradle.kts                          root: plugin aliases with apply false, nothing else
├── gradle.properties
├── gradle/libs.versions.toml
├── gradle/wrapper/gradle-wrapper.jar         binary, validated in CI
├── gradle/wrapper/gradle-wrapper.properties  9.7.1 -bin + distributionSha256Sum
├── gradlew, gradlew.bat
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/AndroidManifest.xml          (Phase 1 content, section C)
│   └── src/main/kotlin/app/justa/launcher/…  (Phase 1 classes, section D)
│   └── src/main/res/{values,values-night}/{themes,colors,strings}.xml, mipmap-anydpi/ic_launcher.xml, drawable/ic_launcher_foreground.xml
├── core/theme/build.gradle.kts               pure Kotlin JVM + serialization plugin; content owned by the theme planner
│   └── src/test/kotlin/…/ModuleWiringTest.kt smoke test (plugin + Json round-trip of a throwaway @Serializable class)
├── core/search/build.gradle.kts              pure Kotlin JVM; content owned by the search planner
│   └── src/test/kotlin/…/ModuleWiringTest.kt
├── docs/research/2026-09-05-android-launcher-capabilities.md              synthesis Parts 1–2 (the research note the previous plan promised, synthesis line 14)
├── docs/research/2026-09-05-android-launcher-capabilities-appendix-a-platform.md      full R1 text
├── docs/research/2026-09-05-android-launcher-capabilities-appendix-b-gestures-background-typography.md   full R2 text
├── docs/research/2026-09-05-android-launcher-capabilities-appendix-c-stack-theming.md  full R3 text
├── docs/decisions/0001-v1-scope-stack-distribution.md   the five 2026-09-06 decisions, verbatim, dated (ADR style)
└── .github/workflows/ci.yml                  section B
    .github/workflows/release.yml             section B (tag-triggered, signing from secrets)
```

One 340 KB Markdown file is unusable in review; the appendices split keeps the promised note as the index. `docs/decisions/` exists because decisions 1–5 are otherwise only in a chat transcript.

### A.1 `gradle/libs.versions.toml` (exact)

```toml
[versions]
agp = "9.4.0"                      # R3 §F: Sept 2026, JDK 17, min Gradle 9.6.0, max API 37
kotlin = "2.4.10"                  # R3 §F
kotlinxSerialization = "1.11.0"    # R3 §C.5: json-jvm 294 KB + core-jvm 405 KB unshrunk; 1.12.0 was RC at research time
androidxActivity = "1.13.0"        # R3 §C.1 / §F
androidxCore = "1.19.0"            # R3 §F
junit = "6.0.0"                    # NOT in the research; use latest JUnit 6.x line at implementation time

[libraries]
androidx-activity = { module = "androidx.activity:activity", version.ref = "androidxActivity" }
androidx-core = { module = "androidx.core:core", version.ref = "androidxCore" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinxSerialization" }
kotlin-test = { module = "org.jetbrains.kotlin:kotlin-test", version.ref = "kotlin" }
junit-bom = { module = "org.junit:junit-bom", version.ref = "junit" }
junit-jupiter = { module = "org.junit.jupiter:junit-jupiter" }
junit-platform-launcher = { module = "org.junit.platform:junit-platform-launcher" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-jvm = { id = "org.jetbrains.kotlin.jvm", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }   # only if AGP built-in Kotlin is disabled, see A.4
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

No `-ktx` artifacts: `activity` 1.13 contains `ComponentActivity`, `enableEdgeToEdge`, `registerForActivityResult`, `OnBackPressedDispatcher`; `core` contains `ViewCompat`, `WindowInsetsControllerCompat`, `ContextCompat.registerReceiver`, `FileProvider` (Phase 7). `core-ktx` is 5.5 KB (R3 §B.1) but unnecessary.

### A.2 Build files (key content, not full dumps)

**`settings.gradle.kts`**: `pluginManagement { repositories { google { content { includeGroupAndSubgroups("androidx"); includeGroupAndSubgroups("com.android"); includeGroupAndSubgroups("com.google") } }; mavenCentral(); gradlePluginPortal() } }`; `dependencyResolutionManagement { repositoriesMode = FAIL_ON_PROJECT_REPOS; repositories { google {…same filter…}; mavenCentral() } }`; `rootProject.name = "justa-launcher"`; `include(":app", ":core:theme", ":core:search")`. No toolchain-resolver plugin (see gradle.properties).

**`gradle.properties`**:
`org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8` · `org.gradle.parallel=true` · `org.gradle.caching=true` · `org.gradle.configuration-cache=true` · `org.gradle.java.installations.auto-download=false` (build must use the installed JDK 17 — reproducibility, F-Droid) · `android.useAndroidX=true` · `android.nonTransitiveRClass=true` · `kotlin.code.style=official` · `kotlin.daemon.jvmargs=-Xmx1g`. Do **not** set `android.enableR8.fullMode` (default true, R3 §C.7).

**`gradle/wrapper/gradle-wrapper.properties`**: `distributionUrl=https\://services.gradle.org/distributions/gradle-9.7.1-bin.zip`, `distributionSha256Sum=<value of gradle-9.7.1-bin.zip.sha256 from services.gradle.org>`, `validateDistributionUrl=true`, `networkTimeout=10000`.

**`app/build.gradle.kts`** decisions:
- `namespace = "app.justa.launcher"`, `compileSdk = 37` (fall back to 36 if `platforms;android-37` is not offered; AGP 9.4 max API is 37, R3 §F), `minSdk = 26`, `targetSdk = 36` (Play requirement, synthesis line 25), `versionCode = 1`, `versionName = "0.1.0"` — literal constants, never derived from git/date (reproducibility).
- `debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }` so a debug home can coexist with the release one on a device (Olauncher needs the same for its accessibility config, R1 §D1).
- `release { isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"); signingConfig = env-driven (see B) }`. R3 §C.7 calls this the legacy DSL for AGP < 9.3; if AGP 9.4 warns, migrate to the 9.3 `optimization {}` DSL / `src/<variant>/keepRules/*.keep` — same R8 input either way.
- `buildFeatures { buildConfig = false; viewBinding = false; resValues = false }` — Phase 1 UI is programmatic (R3 §B.7 "programmatic/minimal-XML UI"); no `BuildConfig` (debug detection uses `ApplicationInfo.FLAG_DEBUGGABLE`).
- `androidResources { localeFilters += listOf("en") }` (AGP 9 name; formerly `resourceConfigurations`) — drops ~80 locales of androidx.core/activity strings from the APK; extend when translations land.
- `dependenciesInfo { includeInApk = false; includeInBundle = false }` — removes the Google-encrypted dependency block F-Droid objects to (F-Droid reproducible-builds guidance; not in the research excerpt, verify wording).
- `packaging.resources.excludes` — decide after the first `apkanalyzer files list` of the release APK (candidates: `kotlin/**`, `DebugProbesKt.bin`, `META-INF/*.version`); don't guess.
- `lint { abortOnError = true; warningsAsErrors = false; htmlReport = true; textReport = true }`.
- `compileOptions` Java 17 + Kotlin `jvmTarget = 17` (DSL block depends on A.4).
- Two custom tasks: `checkReleaseApkSize` (section B.2) and `gmsGuard` (section B.3).
- Dependencies: `implementation(libs.androidx.activity, libs.androidx.core, libs.kotlinx.serialization.json, project(":core:theme"), project(":core:search"))`; tests as in A.5. No `kotlinx-coroutines` (A.6).

**`core/theme/build.gradle.kts`, `core/search/build.gradle.kts`**: `plugins { alias(libs.plugins.kotlin.jvm); alias(libs.plugins.kotlin.serialization) /* theme only */ }`, `kotlin { compilerOptions { jvmTarget = JVM_17 } }`, `java { sourceCompatibility = targetCompatibility = 17 }`, `dependencies { implementation(libs.kotlinx.serialization.json) /* theme */; testImplementation(libs.kotlin.test); testImplementation(platform(libs.junit.bom)); testImplementation(libs.junit.jupiter); testRuntimeOnly(libs.junit.platform.launcher) }`, `tasks.test { useJUnitPlatform() }`. Neither module may depend on `android.*`.

### A.3 `app/proguard-rules.pro`

- **Manifest-declared components need no manual rule**: AGP generates `-keep` rules for every class named in the merged manifest (aapt2 → `aapt_rules.txt`) and R8 keeps overrides of platform abstract methods (`LauncherApps.Callback`). Do not add redundant keeps — they hide real breakage.
- **kotlinx.serialization**: the runtime ships consumer rules, but R3 §C.5 notes separate rule sets for R8 full mode and named companions. Paste the library README's "R8 full mode" block verbatim (the `-if @kotlinx.serialization.Serializable class ** … -keepclassmembers … Companion / serializer(...) / INSTANCE` rules, `-keepattributes RuntimeVisibleAnnotations,AnnotationDefault`, `-dontnote kotlinx.serialization.**`). Cheap insurance because the theme planner may use polymorphic `sealed` hierarchies.
- `-keepattributes SourceFile,LineNumberTable` + `-renamesourcefileattribute SourceFile` — readable crash traces (a crashing home silently loses default status, synthesis line 33; we need traces). Upload `mapping.txt` as a CI artifact.
- Nothing else. Phase 8's `StatusBarManager` reflection targets a platform class — no rule.

### A.4 AGP 9 built-in Kotlin (verify on the first build — not covered by the research)

AGP 9.x compiles Kotlin without `org.jetbrains.kotlin.android`. Plan for it: `:app` applies only `com.android.application` (+ `kotlin.serialization` only if Phase 2 puts `@Serializable` classes in `:app`); the Kotlin version comes from `libs.plugins.kotlin.jvm` on the root classpath. If the first build shows built-in Kotlin is unavailable or the serialization compiler plugin isn't picked up, set `android.builtInKotlin=false` and apply `libs.plugins.kotlin.android`. Record the outcome in README.

### A.5 Decision: JUnit 5 (Jupiter) via `kotlin-test` for the JVM modules

Chosen over JUnit 4: Kotlin's own JVM testing guidance defaults to `kotlin("test")` + `useJUnitPlatform()`; `@ParameterizedTest`/`@CsvSource` fits the scorer ranking tables and theme parsing cases (synthesis Part 7); Gradle 9 has first-class JUnit Platform support but requires `junit-platform-launcher` on `testRuntimeOnly` explicitly (Gradle 9 removed automatic loading). Cost: two extra test-only coordinates, zero APK impact. Trade-off accepted: Phase 9 instrumented tests in `:app` will use JUnit 4 + AndroidJUnitRunner — a different module and configuration. `:app` unit tests also use Jupiter (`testOptions.unitTests.all { it.useJUnitPlatform() }`) and stay Android-free (see `AppKey` design in D).

### A.6 Decision: no kotlinx-coroutines dependency in Phase 0–1 (seam provided)

Evidence: `kotlinx-coroutines-core` 1.9.0 is already a transitive *runtime* dependency of `androidx.activity` (via `navigationevent`) and `core` (R3 §C.6, POM [121]); `coroutines-core-jvm` is ~1.58 MB unshrunk, `-android` 18 KB (R3 §C.6). So:
- Pre-R8: +0 bytes to declare it (already on the classpath); post-R8 today: only the members `activity`/`navigationevent` touch (StateFlow etc.), estimated 30–80 KB DEX.
- If Phase 1 *used* coroutines (`launch`, `Dispatchers`, `withContext`, `delay`): estimated +150–250 KB uncompressed DEX, roughly +70–120 KB compressed APK, plus `kotlinx-coroutines-android` for `Dispatchers.Main`. These are estimates (the reports have no post-R8 numbers); measure with `-printusage` in the Phase 1 size report.
- Complexity: Phase 1 background work is one `LauncherApps.getActivityList` load and per-package requeries; a single `HandlerThread` + main `Handler` is fewer concepts than scopes/dispatchers/cancellation. Phase 6 (HTTP + decode inside a `JobService`, which runs on the main thread and needs a worker anyway) is the first place structured concurrency would pay; decide there with real numbers.
- Seam: `Threads` object (`apps: Handler` on a `HandlerThread("justa-apps")`, `main: Handler`) — the only place a coroutine dispatcher would later be introduced. If adopted later, coroutines ships its own consumer R8 rules — no manual keep rules.

### A.7 Decision: no product flavors in Phase 0 (disagrees with synthesis Part 3/4 `play`/`foss`)

The flavor's only justification in the research was isolating GMS code for F-Droid (synthesis line 107). Decision 4 removes GMS everywhere, so the two channels differ only in (a) artifact type — `bundleRelease` vs `assembleRelease`, a task, not a source set; (b) signing key — a signing config, not code; (c) the "Allow restricted settings" help flow — a *runtime* condition (installer/`getInstallSourceInfo()` or the toggle being greyed), because a Play-signed APK sideloaded from a mirror is also restricted (R1 §C4). Flavors would double CI build time and the emulator matrix and invite source-set divergence. Adding `productFlavors {}` later is a build-file-only change. F-Droid's recipe then uses `gradle: - yes`.

### A.8 `.gitignore`, `.editorconfig`, docs

`.gitignore`: `.gradle/`, `build/`, `**/build/`, `.kotlin/`, `local.properties`, `.idea/`, `*.iml`, `captures/`, `.cxx/`, `*.jks`, `*.keystore`, `*.p12`, `app/release/`, `.DS_Store`.
`.editorconfig`: `root = true`; `[*]` utf-8, lf, final newline, trim whitespace, 4-space indent; `[*.{kt,kts}] max_line_length = 120`; `[*.{yml,yaml,toml}] indent_size = 2`; `[*.xml] indent_size = 4`.

---

## B. CI (GitHub Actions) and reproducible-build hygiene

### B.1 `.github/workflows/ci.yml` — one job, one Gradle invocation

- Triggers: `push` to `main`, `pull_request`, `workflow_dispatch`. `permissions: contents: read`. `concurrency: ci-${{ github.ref }}` with cancel-in-progress. `timeout-minutes: 30`. Runner `ubuntu-latest` (Android SDK preinstalled; AGP auto-downloads `platforms;android-37` / `build-tools;36.0.0` if missing — licenses are pre-accepted on GitHub runners).
- Steps (pin each action to a commit SHA; current majors are the v5 lines of `actions/checkout`, `actions/setup-java` (temurin 17), `gradle/actions/setup-gradle` with `validate-wrappers: true` and `cache-read-only: ${{ github.ref != 'refs/heads/main' }}`, `actions/upload-artifact`).
- Build step: `./gradlew --no-daemon --stacktrace test lintRelease assembleRelease bundleRelease checkReleaseApkSize gmsGuard`. One invocation shares the configuration cache; `test` covers `:core:*` and `:app` JVM tests.
- Artifacts (always, `retention-days: 14`): `app/build/outputs/apk/release/*.apk`, `app/build/outputs/bundle/release/*.aab`, `app/build/outputs/mapping/release/mapping.txt`, `app/build/reports/lint-results-release.html`, `**/build/reports/tests/**`, `app/build/reports/apk-size.txt`.
- Size and download-size go to `$GITHUB_STEP_SUMMARY`.

### B.2 APK size gate: Gradle task `checkReleaseApkSize`

Registered in `app/build.gradle.kts` via `androidComponents.onVariants(selector().withBuildType("release"))`: inputs `variant.artifacts.get(SingleArtifact.APK)` + `getBuiltArtifactsLoader()`; the task resolves the APK file(s), reads `File.length()`, writes `build/reports/apk-size.txt`, prints the size, and fails with the byte count if `> 2_621_440` (2.5 MiB). Measuring through the Variant API avoids hard-coded output paths and works locally with the same command. Budget definition: **file size of the release APK as distributed**. CI builds unsigned (no keystore); v2/v3 signature blocks add ~2–3 KB, negligible against a MiB budget — noted in the task output. Informational (not gated): `apkanalyzer apk download-size` from `$ANDROID_HOME/cmdline-tools/latest/bin`.

### B.3 `gmsGuard`

Task that walks `releaseRuntimeClasspath`'s `resolutionResult` and fails if any component id starts with `com.google.android.gms`, `com.google.firebase`, or `com.google.android.play` (also catches `androidx.core:core-google-shortcuts`, which drags Firebase App Indexing). Enforces decision 4 mechanically; lint/no-code review needed.

### B.4 `.github/workflows/release.yml` (tags `v*`)

Same setup; decode `JUSTA_KEYSTORE_B64` secret to a temp file; export `JUSTA_KEYSTORE`, `JUSTA_KEYSTORE_PASSWORD`, `JUSTA_KEY_ALIAS`, `JUSTA_KEY_PASSWORD`; run `assembleRelease bundleRelease checkReleaseApkSize`; publish the signed APK + `sha256sum` + `mapping.txt` to a GitHub Release; keep the AAB as an artifact for manual Play upload (no Play publisher action in v1). `app/build.gradle.kts` attaches the release `signingConfig` **only when `JUSTA_KEYSTORE` is set**, so local and F-Droid builds produce unsigned APKs. Day-one key decisions: one APK release key (GitHub/F-Droid; must never change — F-Droid copies our signature onto its verified rebuild, R3 §C.7 [200]) and one Play upload key (Play App Signing); generated offline, backed up, never committed; same `applicationId` on both channels.

### B.5 Reproducible-build hygiene adopted from day one

- Pinned everything: AGP/Kotlin/deps via the catalog (no dynamic versions, no `+`), wrapper with `distributionSha256Sum`, actions by SHA, JDK 17 with toolchain auto-download off.
- No build-time variability: literal `versionCode`/`versionName`, `buildConfig=false`, no timestamps or git hashes injected, `dependenciesInfo` off, minSdk 26 means aapt2 never rasterizes vector drawables (the PNG-generation non-determinism F-Droid warns about, R3 §C.7 [200]).
- Determinism check in acceptance (E): two clean builds → identical `sha256sum` of the unsigned release APK.
- **Not adopted now**: Gradle dependency-verification metadata and lockfiles. With ~6 direct coordinates, immutable Maven repositories and no dynamic versions, resolution is already deterministic; verification metadata would add a multi-thousand-line generated file that must be regenerated on every AGP bump. Revisit in Phase 9 (open question).

---

## C. AndroidManifest for Phase 1

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <!-- optional; see Open questions: hides the app on devices without replaceable home (R1 §A1, semantics UNVERIFIED) -->
    <uses-feature android:name="android.software.home_screen" android:required="true" />

    <queries>
        <intent>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent>
    </queries>

    <application
        android:name=".JustaApp"
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:theme="@style/Theme.Justa"
        android:supportsRtl="true"
        android:enableOnBackInvokedCallback="true">
        <activity
            android:name=".home.HomeActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:clearTaskOnLaunch="true"
            android:stateNotNeeded="true"
            android:resumeWhilePausing="true"
            android:taskAffinity=""
            android:excludeFromRecents="true"
            android:configChanges="keyboard|keyboardHidden|mcc|mnc|navigation|orientation|screenSize|screenLayout|smallestScreenSize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.HOME" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

Reason per attribute (R1 §A1 unless noted): `singleTask` — the Home intent (MAIN/HOME + NEW_TASK) is routed to the existing instance via `onNewIntent()` (R1 §A2); `clearTaskOnLaunch` — any activity that landed in the home task is cleared on the next Home press (honoring depends on RESET_TASK_IF_NEEDED, UNVERIFIED — harmless to keep); `stateNotNeeded` — `onCreate(null)` is normal, saves save/restore IPC, forces us to keep state persistent (Phase 2); `resumeWhilePausing` — Home resumes before the previous app finishes pausing ("what makes Home feel instant"; we acquire no exclusive resources at launch); `taskAffinity=""` — prevents other apps' activities re-parenting into the home task; `excludeFromRecents` — Olauncher's addition, no Home card in Recents; `exported="true"` — mandatory for filtered components since Android 12; `configChanges` — Launcher3's list: rotation/size changes relayout instead of recreating; `locale`, `fontScale`, `uiMode` deliberately absent so those recreate the activity (labels/text size/night colors must be re-read anyway; `stateNotNeeded` makes recreation cheap). `CATEGORY_LAUNCHER` is included for two reasons: the app must be openable from another launcher to become default, and Android 12's "root launcher activity not finished on Back" protection keys off LAUNCHER, not HOME (R1 §A2). `android:enableOnBackInvokedCallback="true"` on `<application>` makes API 33–35 devices use the same OnBackInvoked path a target-36 app gets on Android 16 (R2 §A3). `windowSoftInputMode` is left for Phase 4 (Android 17 `stateAlwaysVisible` consideration, synthesis line 117). `ACTION_SHOW_WORK_APPS` is deferred to the profiles phase.

**Must NOT be present** (grep-checked in acceptance): `QUERY_ALL_PACKAGES` (synthesis line 40–41; `LauncherApps` honors `<queries>`, R1 §B2), `ACCESS_HIDDEN_PROFILES` (decision 2), `SET_WALLPAPER` (decision 3), any `com.google.android.gms`/Firebase metadata or providers (decision 4; enforced by `gmsGuard`). Phase 1 has **zero** `<uses-permission>` elements. The `<queries>` block stays a single intent: `startActivity` with implicit intents needs no visibility; only `resolveActivity`/`queryIntentActivities` do (R1 §B2). Phase 3 must catch `ActivityNotFoundException` instead of pre-resolving, so the synthesis Part 4 "+ targeted entries for browser/Play" is unnecessary.

### C.1 Every permission/protected component v1 will eventually declare

| Manifest item | Protection level | Phase | Notes |
|---|---|---|---|
| `REQUEST_DELETE_PACKAGES` | normal | 2 | uninstall via `ACTION_DELETE` (synthesis line 42) |
| `<service …BadgeListenerService android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE">` + `SERVICE_INTERFACE` filter + `default_filter_types` meta-data | signature (bind permission) + special app access "Notification access" | 5 | prominent disclosure before the grant; restricted-settings help for sideloads (synthesis §1.4) |
| `INTERNET` | normal | 6 | image collections |
| `ACCESS_NETWORK_STATE` | normal | 6 | JobScheduler network constraint on 14+, Data Saver check (synthesis line 67) |
| `<service …ImageRotationJobService android:permission="android.permission.BIND_JOB_SERVICE">` | signature (system binds) | 6 | no `RECEIVE_BOOT_COMPLETED`: the home app starts at boot; reschedule idempotently from `JustaApp.onCreate` instead of `setPersisted(true)` |
| Theme import `<intent-filter>` (ACTION_VIEW/SEND, MIME + `pathSuffix`, `justa://theme`) on a small `share.ImportActivity`; `<provider androidx.core.content.FileProvider exported="false" grantUriPermissions="true">` | none | 7 | synthesis §1.12 |
| `<service …GestureAccessibilityService android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE" android:process=":a11y">` + `@xml/accessibility_service_config` (`isAccessibilityTool="false"`, no event types, `canRetrieveWindowContent="false"`) | signature (bind) + special app access "Accessibility" | 8 | Play declaration + demo video + in-app disclosure; off by default (R1 §D1) |
| `<receiver …LockAdminReceiver android:permission="android.permission.BIND_DEVICE_ADMIN">` + `@xml/device_admin_policies` (`force-lock`) | signature (bind) + user activation via `ACTION_ADD_DEVICE_ADMIN` | 8 | strong-auth caveat (R1 §D2) |
| `EXPAND_STATUS_BAR` | normal | 8 | only if the reflection shade fallback is kept (synthesis line 60) |
| `PACKAGE_USAGE_STATS` (`tools:ignore="ProtectedPermissions"`) | signature\|privileged\|development\|appop\|retailDemo → special app access "Usage access" | 8 | gate on `AppOpsManager.OPSTR_GET_USAGE_STATS`; disclosure (R1 §E) |

Explicitly **not** declared in v1: `ACCESS_NOTIFICATION_POLICY` (DND not in the v1 list), `POST_NOTIFICATIONS` (we post none), `VIBRATE` (`performHapticFeedback` needs none, synthesis line 53), `RECEIVE_BOOT_COMPLETED`, `READ_CONTACTS`, `CAMERA`, `READ_MEDIA_IMAGES` (SAF instead), `com.android.alarm.permission.SET_ALARM` (only `ACTION_SET_ALARM` needs it; show-alarms does not), `ACCESS_LOCAL_NETWORK` (runtime permission only when targeting 37; revisit at the target bump), `FOREGROUND_SERVICE`, `WAKE_LOCK`.

---

## D. Phase 1 class inventory (`:app`, namespace `app.justa.launcher`, packages per synthesis Part 4)

Root package
- `JustaApp : Application` — owns process singletons (`AppRepository`, `SettingsSource`); when `applicationInfo.flags and FLAG_DEBUGGABLE != 0` enables `StrictMode` with `detectNonSdkApiUsage()` + `penaltyLog()` (synthesis Part 7); `onCreate` does < 5 ms of main-thread work.
- `Threads` (object) — `apps: Handler` on `HandlerThread("justa-apps")`, `main: Handler(Looper.getMainLooper())`. The concurrency seam (A.6).

`apps`
- `AppKey(packageName: String, activityName: String, userSerial: Long)` — pure Kotlin identity keyed by (component, user); the user is carried as `UserManager.getSerialNumberForUser` because `LauncherActivityInfo.getUser()` docs say not to store the `UserHandle` (R1 §B3), and serials persist (Phase 2 JSON) while staying profile-ready (decision 2).
- `AppEntry(key: AppKey, component: ComponentName, user: UserHandle, label: String, isSuspended: Boolean)` — live snapshot row; label is the cached `getLabel().toString()`.
- `AppRepository(context)` — `LauncherApps` façade. `start()` registers a `LauncherApps.Callback` with `Threads.apps` (callbacks delivered on the apps thread), registers an `ACTION_LOCALE_CHANGED` receiver via `ContextCompat.registerReceiver(..., RECEIVER_NOT_EXPORTED)` (protected system broadcast; delivery verified in E), and schedules the initial load. `load()` iterates `profiles` (v1: `listOf(Process.myUserHandle())`; later `getProfiles()`), calls `getActivityList(null, user)`, filters synthesized `android.app.AppDetailsActivity` rows (R1 §B1) and our own package, sorts with `Collator.getInstance(locale)`, publishes an immutable `Snapshot(entries, locales, generation)` via `@Volatile` and posts listeners on `Threads.main`. Package callbacks (`onPackageAdded/Removed/Changed`, `onPackagesAvailable/Unavailable`, `onPackagesSuspended/Unsuspended`) requery only that package/user (`getActivityList(pkg, user)`) and republish. `ensureFresh(locales)` reloads when the locale list differs (belt-and-braces with the receiver). Single writer thread → no locks. All binder calls wrapped in try/catch (`SecurityException`, `IllegalStateException`).
- `AppLauncher(launcherApps)` — `launch(entry, sourceBounds: Rect?)` → `startMainActivity(component, user, sourceBounds, ActivityOptions.makeClipRevealAnimation(view…).toBundle())`; on `ActivityNotFoundException`/`SecurityException` (app removed between snapshot and tap) triggers `repository.refresh()` and shows a platform `Toast`. Uses the entry's `UserHandle`, never the current user implicitly (synthesis line 39).

`settings` (the Phase-2 seam — no schema here)
- `HomeSettings(homeApps: List<AppKey>, homeAppCount: Int, horizontalAlignment: START|CENTER|END, verticalPosition: TOP|CENTER|BOTTOM, textSizeSp: Float, font: FontSpec(family, weight, italic), rowMinHeightDp: Int = 48, horizontalPaddingDp: Int, lightSystemBars: Boolean?)` — the *projection* Phase 1 reads; Phase 2's JSON document (owned by the other planner) maps onto it.
- `SettingsSource` (interface) — `val current: HomeSettings`, `addListener/removeListener(SettingsListener)`. Phase 1 ships `DefaultSettingsSource` (hardcoded constants; `homeApps` empty → fallback below). Phase 2 replaces it with a JSON-backed implementation; nothing else changes.

`home`
- `HomeActivity : ComponentActivity` — single activity. `onCreate`: `enableEdgeToEdge(statusBarStyle, navigationBarStyle)` chosen from `lightSystemBars` with transparent colors (forces transparency on API 26–28 too, where the default applies a nav scrim); sets `layoutInDisplayCutoutMode = ALWAYS` (API 30+) / `SHORT_EDGES` (28–29) so API 28–34 devices behave like target-35 enforcement (R2 §D5); builds `HomeRootView`; registers the back callback and the role-request `ActivityResultLauncher` (must happen before STARTED); renders from the current snapshot (possibly empty until the first load lands). `onStart/onStop`: add/remove repository and settings listeners. `onResume`: `defaultHomePrompt.refresh()`, `repository.ensureFresh(resources.configuration.locales)`, cheap re-render. `onNewIntent(intent)`: `setIntent(intent)`; if `intent.hasCategory(CATEGORY_HOME)` → `resetToHome()` (dismiss overlays — none in Phase 1 — scroll to top; Phase 6 hooks its "refresh if interval elapsed" here). No `onSaveInstanceState` reliance (`stateNotNeeded`); the role-request result may be lost on process death, which is fine because `onResume` recomputes default status. Override the single-arg `onNewIntent(Intent)` (API 35 added a `ComponentCaller` overload).
- `HomeBackCallback : OnBackPressedCallback(enabled = true)` — always enabled so `ComponentActivity` keeps an `OnBackInvokedCallback` registered on API 33+: back dismisses overlays (Phase 4/2) else no-ops. This makes the UNVERIFIED "system default back on the home task" question (R1 open item 11, R2 §A3) irrelevant and guarantees Back never finishes the activity; on API 26–32 the same callback intercepts `onBackPressed`.
- `HomeRootView : FrameLayout` — owns the inset listener: `ViewCompat.setOnApplyWindowInsetsListener` → padding = `insets.getInsets(systemBars() or displayCutout())` (synthesis line 76); returns the insets unconsumed for Phase 4's IME handling; hosts `HomeListView` (positioned by `layout_gravity` = vertical position) and `DefaultHomeBanner`.
- `HomeListView : LinearLayout(VERTICAL)` — **recommended over ListView/RecyclerView for 1–10 rows**: no adapter/recycling for a fixed handful of rows; per-view theming (typeface, shadow, letter spacing) without rebind plumbing; no nested scroll container to fight Phase 3's vertical swipes (a scrolling list would steal them or need `requestDisallowInterceptTouchEvent` gymnastics); zero dependency (RecyclerView = 452 KB AAR + `customview`/`collection`/`profileinstaller`, R3 §C.1); TextView rows are natively focusable/clickable for TalkBack; it is Olauncher's own approach (R3 §A.2, "eight TextViews in a LinearLayout"). Keeps a pool of `TextView` rows, adds/removes children as `homeAppCount` changes; `gravity` = horizontal alignment; rows are `MATCH_PARENT` wide (whole row is the tap target), `minHeight` 48 dp via `TypedValue.applyDimension(COMPLEX_UNIT_DIP)`, `setTextSize(COMPLEX_UNIT_SP, …)`, single line, `ellipsize=END`; never derives sizes from `fontScale` (R2 §D2). Overflow at extreme sizes clips — see Open questions. `ListView` remains the Phase 4 drawer choice (R3 §C.1).
- `HomeListModel` (pure Kotlin, unit-tested) — `(Snapshot, HomeSettings) → List<HomeRow>`: configured `homeApps` in order, dropping keys absent from the snapshot; if the configured list is empty, the first `homeAppCount` (default 6) alphabetical entries so Phase 1 is not a blank screen.
- `ThemeApplier` — applies `HomeSettings` to views (`Typeface.create(Typeface.create(family, NORMAL), weight, italic)` on API 28+, `Typeface.create(family, style)` on 26–27; text size; gravity; padding; colors from `@color/home_text`). Named now so Phase 7 grows it rather than replacing it (synthesis Part 4).
- `DefaultHomePrompt` — `isDefaultHome()`: API 29+ `RoleManager.isRoleAvailable(ROLE_HOME) && isRoleHeld(ROLE_HOME)`; API 26–28 `packageManager.resolveActivity(Intent(ACTION_MAIN).addCategory(CATEGORY_HOME), MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName`. `requestIntent()`: API 29+ `createRequestRoleIntent(ROLE_HOME)` launched through the activity-result launcher (`RESULT_OK` → hide banner); otherwise `Settings.ACTION_HOME_SETTINGS` (CDD-mandated, R1 §A3) with `ActivityNotFoundException` fallbacks to `ACTION_MANAGE_DEFAULT_APPS_SETTINGS` then `ACTION_SETTINGS`. Olauncher's disabled `FakeHomeActivity` trick is not needed at minSdk 26. **When shown**: a single text row (`DefaultHomeBanner : TextView`) at the bottom inset edge whenever `isDefaultHome()` is false on every `onResume`; the role dialog is never auto-launched (Phase 2 adds a persisted "asked once" flag).

Resources: `values/themes.xml` `Theme.Justa` parent `@android:style/Theme.DeviceDefault.Light.NoActionBar`, `values-night/themes.xml` parent `@android:style/Theme.DeviceDefault.NoActionBar` (API 26 has no `DayNight` variant; `DeviceDefault` gives OEM/Material-You-consistent platform dialogs and Phase 2 widgets without AppCompat). Items: `android:windowBackground=@color/home_background` (must match the home background because the home activity never gets a splash window and cold starts otherwise flash, R1 §A4), `android:windowShowWallpaper=false` (Phase 6 toggles `FLAG_SHOW_WALLPAPER` + null background at runtime for passthrough mode), `android:forceDarkAllowed=false` (we own our colors). Colors: `home_background` black/white, `home_text` white/black by qualifier. Night mode Phase 1 = follow system (recreation via `uiMode`); Phase 7 exposes `UiModeManager.setApplicationNightMode` (API 31) and `createConfigurationContext` override for 26–30. Icon: adaptive `mipmap-anydpi/ic_launcher.xml` with a vector foreground (a few KB; required for Play and other launchers' drawers).

Threading summary: apps thread = all `LauncherApps` binder calls, callback handling, snapshot writes; main thread = listener notification, rendering, launches; no other threads in Phase 1.

---

## E. Acceptance criteria, verification commands, SDK components, local checklist

### E.1 Phase 0 acceptance
1. Fresh clone + JDK 17 + SDK: `./gradlew build` green (compiles 3 modules, runs `ModuleWiringTest` in both `:core` modules, lint passes).
2. `./gradlew assembleRelease checkReleaseApkSize` passes and prints the size; `apkanalyzer apk file-size` and `download-size` recorded in README. Expectation for the Phase 1 skeleton: well under the budget (Olauncher with far more code and AppCompat ships at 2.13 MiB, synthesis §1.10; estimate 0.9–1.4 MiB — measure, don't assume).
3. `./gradlew gmsGuard` passes; `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` shows only androidx.activity/core/lifecycle/savedstate/navigationevent/tracing/profileinstaller, kotlin-stdlib, kotlinx-serialization, kotlinx-coroutines-core (transitive) — record the list.
4. Determinism: `rm -rf build */build core/*/build .gradle && ./gradlew assembleRelease` twice → identical `sha256sum app/build/outputs/apk/release/app-release-unsigned.apk`.
5. CI green on a PR; wrapper validation passes; APK/AAB/mapping/lint/test artifacts present; step summary shows the size.
6. `apkanalyzer manifest print <apk> | grep -E 'uses-permission|queries|QUERY_ALL|HIDDEN_PROFILES|SET_WALLPAPER'` → zero `uses-permission`, one `<queries>` intent, no forbidden names.

### E.2 Phase 1 acceptance (run on API 26 and API 36 AVDs; API 37 image if offered)
7. Startup: `adb shell am start -W -n app.justa.launcher/.home.HomeActivity` → `TotalTime` recorded (informational baseline, emulator variance; no gate).
8. Set default — API 29+: `adb shell cmd role add-role-holder --user 0 android.app.role.HOME app.justa.launcher`; API 26–28: `adb shell cmd package set-home-activity app.justa.launcher/.home.HomeActivity` (fallback: Settings → Home app). Verify: `adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME` prints our component.
9. Home key returns to the existing instance: open another app, `adb shell input keyevent KEYCODE_HOME`; logcat shows the debug `onNewIntent` line, no `onCreate`; `adb shell dumpsys activity activities | grep -c 'app.justa.launcher/.home.HomeActivity'` = 1.
10. Back does not finish: `adb shell input keyevent KEYCODE_BACK` ×3 → `adb shell dumpsys activity top | grep ACTIVITY` still shows HomeActivity; no `onDestroy` in logcat. Repeat with gesture navigation on the API 36 image.
11. Rotation: `adb shell settings put system accelerometer_rotation 0; adb shell settings put system user_rotation 1` then `0` → no crash (`adb logcat -b crash -d` empty), list re-laid out with correct insets in landscape (cutout side padded).
12. Locale change: emulator Settings → System → Languages (or `adb shell "setprop persist.sys.locale de-DE; setprop ctl.restart zygote"`) → labels re-sorted, no crash; logcat shows the repository reload (proves `RECEIVER_NOT_EXPORTED` delivery or the `ensureFresh` path).
13. Dark toggle `adb shell cmd uimode night yes|no` → colors flip, no crash. Font scale `adb shell settings put system font_scale 2.0` → rows ≥ 48 dp, no clipped glyphs with 6 rows at default size; restore `1.0`.
14. Package callbacks: `adb install` a sample APK / `adb uninstall` it / `adb shell pm disable-user --user 0 <pkg>` while Home is visible → list updates without restart.
15. Tap a row → app launches with the clip-reveal animation from the row bounds; uninstall an app then tap its stale row → toast, no crash, list refreshes.
16. Default-home banner visible when not default; tap → role dialog (29+) or Home settings (26–28); banner disappears after becoming default.
17. Hygiene: `adb logcat -d | grep -i 'Accessing hidden'` empty; no StrictMode violations in the debug build during 7–16; `adb shell dumpsys meminfo app.justa.launcher` PSS recorded as the steady-state baseline (synthesis line 32 rationale).
18. Release APK ≤ 2.5 MiB (gate); actual number recorded.

### E.3 SDK components to install locally (do not install now)
- Arch packages: `jdk17-openjdk` (17.0.20), `android-udev` (physical devices); `android-tools` optional (prefer the SDK's `platform-tools` to match versions).
- Android command-line tools zip from developer.android.com → `~/Android/Sdk/cmdline-tools/latest/`; then `sdkmanager --licenses`.
- `sdkmanager`: `platform-tools`, `build-tools;36.0.0` (AGP 9.4 default, R3 §F), `platforms;android-36`, `platforms;android-37` (compileSdk; verify availability), `emulator`, `system-images;android-26;google_apis;x86_64`, `system-images;android-36;google_apis;x86_64`; optional for the Part 7 matrix: `android-30`, `-33`, `-34`, `-35`, `-37` images, and `sources;android-36`.
- AVDs: `avdmanager create avd -n api26 -k "system-images;android-26;google_apis;x86_64" -d pixel` and the same for `api36`; run with `-memory 2048` given 14 GB RAM.
- Shell (fish): `set -Ux JAVA_HOME /usr/lib/jvm/java-17-openjdk`, `set -Ux ANDROID_HOME ~/Android/Sdk`, prepend `$ANDROID_HOME/platform-tools`, `$ANDROID_HOME/cmdline-tools/latest/bin`, `$ANDROID_HOME/emulator` to `fish_user_paths`; `local.properties` with `sdk.dir` (gitignored).
- Gradle bootstrap without a system Gradle: download `gradle-9.7.1-bin.zip` + `.sha256` from services.gradle.org, verify, run `bin/gradle wrapper --gradle-version 9.7.1 --distribution-type bin --gradle-distribution-sha256-sum <sha>` in the repo, delete the local unzip. (Or `pacman -S gradle` if the repo version is ≥ 9.x.)
- Optional: `gh` CLI for releases; Android Studio Quail 4 (2026.1.4) is not required for CLI builds.

---

## Disagreements with the previous plan (explicit)
1. **No `play`/`foss` flavors in Phase 0** (synthesis Part 3 line 240, Part 4 line 251) — reason in A.7.
2. **Manifest list in Part 4 (line 250)** included `ACCESS_HIDDEN_PROFILES` and `SET_WALLPAPER`; both are excluded by decisions 2 and 3, and the "+ targeted `<queries>` entries for browser/Play" are unnecessary (C).
3. **Part 3 (line 235) offers ListView or RecyclerView for "lists"**: for the 1–10-row home list neither; LinearLayout of TextViews (D). ListView stays the drawer choice.
4. **Part 6 Phase 0** listed "assemble + JVM tests + APK size check"; add lint and the GMS guard — both are one Gradle task each.
5. **R1 summary** recommends handling `ACTION_SHOW_WORK_APPS`; deferred to the profiles phase (excluded from v1 by decision 2).

## Open questions
1. AGP 9.4 built-in Kotlin: does the serialization compiler plugin apply without `org.jetbrains.kotlin.android`? (A.4 — verify on the first build; fallback documented.)
2. `<uses-feature android:name="android.software.home_screen" android:required="true">`: confirm Play device-catalog reach isn't reduced unexpectedly (R1 §A1 marks the semantics UNVERIFIED). Ship without it if unsure.
3. Final `applicationId`/namespace (`app.justa.launcher` placeholder). Namespace should be fixed before Phase 1 code lands (moving source dirs later); `applicationId` is immutable once on Play.
4. Transitive `androidx.profileinstaller` (via `activity`) adds a startup `InitializationProvider`, receiver and `assets/dexopt/baseline.prof`. Measure in the Phase 1 size report; decide in Phase 9 whether to exclude the group (verify no startup `NoClassDefFoundError`) or keep for the small AndroidX startup benefit.
5. Gradle dependency-verification metadata and lockfiles: deferred (B.5); adopt in Phase 9 if the supply-chain posture warrants the maintenance cost.
6. Home-list overflow policy when `homeAppCount × row height` exceeds the inset-padded height at 200% font scale (clip vs. auto-cap the count vs. shrink spacing) — belongs to Phase 2 settings validation.
7. JUnit 6.x exact version and `kotlin-test-junit5` 2.4.10 compatibility (not in the research; check on first `./gradlew test`).
8. `RECEIVER_NOT_EXPORTED` delivery of `ACTION_LOCALE_CHANGED` on Android 14+ (protected system broadcast — should deliver; acceptance item 12 verifies; `ensureFresh` covers the gap regardless).
9. Whether `clearTaskOnLaunch` is honored for the home task (R1 open item 2) — observe in acceptance item 9 after Phase 2 adds a settings activity in the home task.
10. Copying the three ~100 KB report texts into `docs/research/` verbatim vs. trimming their source lists — decide with the user when creating the note.

### Critical Files for Implementation
- /home/hyzii/Projects/justa-launcher/app/build.gradle.kts — SDK levels, R8/shrink config, localeFilters, dependenciesInfo, env-driven signing, `checkReleaseApkSize` and `gmsGuard` tasks
- /home/hyzii/Projects/justa-launcher/gradle/libs.versions.toml — every pinned coordinate (AGP 9.4.0, Kotlin 2.4.10, activity 1.13.0, core 1.19.0, kotlinx-serialization-json 1.11.0, JUnit 6.x)
- /home/hyzii/Projects/justa-launcher/app/src/main/AndroidManifest.xml — Launcher3/Olauncher home-activity attributes, single `<queries>` intent, zero permissions in Phase 1
- /home/hyzii/Projects/justa-launcher/app/src/main/kotlin/app/justa/launcher/apps/AppRepository.kt — LauncherApps snapshot keyed by (component, user serial), callbacks on the apps thread, locale invalidation
- /home/hyzii/Projects/justa-launcher/app/src/main/kotlin/app/justa/launcher/home/HomeActivity.kt — edge-to-edge insets, always-enabled back callback, onNewIntent/onResume lifecycle, default-home prompt wiring
