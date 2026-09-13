# Phase 9 acceptance — release engineering

Date: 2026-09-13. Scope per [ADR 0004](../decisions/0004-github-only-distribution.md): GitHub
Releases only. Dropped from the approved plan and not built: the app bundle, Play declarations,
fastlane text, F-Droid metadata, and the reproducible double-build gate.

## What shipped in this phase

| Item | Where |
|---|---|
| Manifest and DEX policy gate | `scripts/check-manifest-policy.sh`, wired into both workflows |
| The minified build, on a device | `scripts/verify-release.sh` |
| Final R8 config | `app/proguard-rules.pro` — 32 lines shorter than it was |
| Privacy statement | `docs/privacy.md` |
| Release routine | `docs/release/checklist.md` |
| Signing, corrected for ADR 0004 | `docs/release/signing.md` |
| App bundle removed from CI and release | `.github/workflows/{ci,release}.yml` |

## The R8 config got smaller, not bigger

`app/proguard-rules.pro` carried a hand-copied block of kotlinx.serialization keep rules, taken from
the library README during Phase 2. kotlinx-serialization-core 1.11.0 ships those rules itself, in
`META-INF/com.android.tools/r8/kotlinx-serialization-{common,r8}.pro`, and R8 merges them: the
merged configuration (`app/build/outputs/mapping/release/configuration.txt`) contained both copies.

The library's version is a superset. It adds three rules ours never had — the `NamedCompanion`
lookup rules, the `$$serializer` `descriptor` field exemption for a ProGuard optimizer bug, and the
full-mode `-if @Serializable class ** -keep,allowshrinking,… class <1>` that keeps the annotation
readable. Ours was a snapshot that could only go stale.

Deleted. The release APK is **364,678 bytes before and after**, byte-identical in size, which is
what a fully subsumed rule set looks like. `verify-release.sh` then proved it on two devices rather
than by reading — see below.

Reviewing the rest of `analyzeReleaseR8Config`: every wildcard keep in the merged configuration
comes from AGP's `proguard-android-optimize.txt` or a library's own consumer rules (androidx
activity, core, lifecycle, savedstate, versionedparcelable, profileinstaller; kotlinx
serialization and coroutines). Nothing atomic writes keeps anything. The widest of them —
`androidx.core.view.ViewCompat$Api*` and friends, 140 seeds — are
`-keepclassmembernames,allowobfuscation,allowshrinking`, which block neither shrinking nor
optimization.

## Acceptance

| Criterion (from the build plan) | Result |
|---|---|
| Size gate fails on an oversized build | **Pass.** A 2.5 MiB incompressible asset took the APK to 2,987,062 B; `checkReleaseApkSize` failed it against the 2,621,440 B budget. (The plan said a 1 MiB asset; 1 MiB lands at ~1.4 MiB, inside the budget, so the negative test needs a real overshoot.) |
| Policy gate fails when `QUERY_ALL_PACKAGES` is added | **Pass.** Added to the manifest, rebuilt: 2 of 11 checks failed — the named forbidden permission and the allowlist. Reverted. |
| Release build decodes the default document on API 26 and 36 | **Pass.** `verify-release.sh`: 12/12 on both. |
| `analyzeReleaseR8Config` shows no unexpected keeps | **Pass.** See above. |
| Two clean-container builds compare equal | **Dropped** by ADR 0004 with the F-Droid channel. |
| StrictMode absent from the release DEX | **Criterion was wrong.** See below. |

### StrictMode is in the release DEX, on purpose

The plan assumed StrictMode was debug-only instrumentation. It is not, any more: `AtomicApp` and
`SettingsRepository` use `StrictMode.allowThreadDiskWrites()`, and `Processes` uses
`allowThreadDiskReads()`, to scope the one deliberate disk touch at startup — the settings file is
read synchronously so the first frame is final. That is production code, so
`Landroid/os/StrictMode` is necessarily in the shipped DEX, and so are `detectAll` and `penaltyLog`
from `installStrictMode()`, which R8 cannot remove because its guard
(`applicationInfo.flags and FLAG_DEBUGGABLE`) is read at runtime rather than compiled away.

What the criterion was actually protecting against — debug instrumentation running in a shipped
build — is covered, and better, by the guard itself plus the policy gate's `not debuggable` check:
a release APK has `FLAG_DEBUGGABLE` clear, so `installStrictMode()` never runs, and if an APK ever
were debuggable the build now fails before it can be published. Making the method itself
shrinkable would need a debug-only source set and a compile-time constant, for a code path that is
already provably dead and costs about a kilobyte.

## The gap this phase found: nothing had ever run the shipped build

All eight phase harnesses install `app-debug.apk`. The debug build is not minified, not shrunk and
not obfuscated, so until this phase no test — JVM or device — had executed the code R8 produces.
That is precisely where kotlinx.serialization fails: serializers are resolved reflectively, full
mode strips what it cannot see used, and a document that round-trips perfectly in a unit test can
come back empty from a shrunk APK. The symptom is a blank home screen on a stranger's phone.

`scripts/verify-release.sh` closes it. It re-signs the unsigned release APK with the debug key
(which does not make it debuggable, so `run-as` is out of reach and every assertion reads the screen
or the window manager instead of the file), then:

| # | Check | Why it is the one that matters |
|---|---|---|
| 1 | The minified APK installs and holds the home screen | a wrong keep rule is a `ClassNotFoundException` before the first frame |
| 2 | The default document decoded — six app rows | a failed decode is an empty list, not a wrong one |
| 3 | The `ink` theme resolved, background `#000000` | the built-in theme constants survived shrinking |
| 4 | A theme change applies and is written back | encode, through the minified codec |
| 5 | It survives a force-stop and relaunch | decode, of the whole document — every gesture binding included, because the codec encodes defaults |
| 6 | Swipe down opens the shade | the sealed `Action` hierarchy decoded to the right subtype rather than `Unknown`, **and** the reflective call into `android.app.StatusBarManager` still resolves |
| 7 | No crash, no `SerializationException` in the log | |

Result: **12/12 on API 26 and 12/12 on API 36.**

Check 6 was weak when first written — it asserted only that the home screen was still on top, which
passes whether or not the shade opened, because a shade is a window over the home activity and not
an activity of its own. It now reads `mCurrentFocus`, which is `StatusBar` on API 26 and
`NotificationShade` on API 36, and then asserts the shade closes again. This is the same mistake
Phase 8 found in three harnesses; it is worth stating that it is easy to make twice.

Two other harness defects, both found by running it: the theme rows are titled `Terminal`, with the
description in the same text attribute after a newline, so exact text matching found nothing (it
matches on a prefix now); and the long press that opens settings was aimed at a hardcoded
coordinate, which is empty on API 36 and not on API 26 (it is computed from `wm size` now, the way
`verify-settings.sh` does it).

## What the policy gate checks

Against the built APK, not the sources, because a merged manifest can declare what no source file
does:

1. None of `QUERY_ALL_PACKAGES`, `ACCESS_HIDDEN_PROFILES`, `SET_WALLPAPER`, `POST_NOTIFICATIONS`,
   `RECEIVE_BOOT_COMPLETED`.
2. Every declared permission is on an allowlist of six, each with its reason written beside it in
   the script. The sixth is androidx.core's per-app signature-level
   `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`.
3. The APK is not debuggable.
4. No `Lcom/google/android/gms/`, `Lcom/google/firebase/` or `Lcom/google/android/play/` in the DEX.
   `gmsGuard` already checks the Gradle classpath; this checks what actually landed.
5. No GMS, Firebase or Play component in the manifest.

It also refuses to run if `aapt2` reads no permissions at all — an empty dump would have made every
check in group 1 pass for the wrong reason, and a gate that cannot fail is worse than no gate.

`scripts/check-apk-size.sh` from the plan was not written: `checkReleaseApkSize` is a Gradle task
that already enforces the same budget, is wired into both workflows, and was verified failing. A
shell duplicate would be a second implementation of one rule.

## The release workflow, rehearsed

`.github/workflows/release.yml` had never run — there are no tags — and a tag cannot be reused, so
a first release that fails costs the version number. The whole path was therefore rehearsed locally
with a throwaway keystore, running the workflow's own commands:

| Step | Result |
|---|---|
| `ktlintCheck test lintRelease assembleRelease checkReleaseApkSize gmsGuard`, with the four `ATOMIC_*` variables set | the signing config attaches: the output is **`app-release.apk`**, not `app-release-unsigned.apk`. 372,870 B signed, against a 2,621,440 B budget |
| `apksigner verify --print-certs` | **Verifies.** v2 `true`, v3 `true`, v1 `false` (correct: `minSdk` 26), one signer |
| `scripts/check-manifest-policy.sh` on the signed APK | 11/11 |
| Rename and checksum | `atomic-launcher-v1.0.0.apk` + `SHA256SUMS`, `mapping.txt` present |
| Install the renamed APK on API 26 | installs, launches, `versionCode=10000`, no crash |

The throwaway keystore was deleted and the release APK rebuilt unsigned afterwards. Untested, and
untestable without the maintainer's key: the secret values themselves and `gh release create`.

Four defects in that workflow were found and fixed by reading it against this rehearsal:

1. **It could publish an unsigned APK.** Gradle attaches the signing config only when
   `ATOMIC_KEYSTORE` reaches it. If that ever failed quietly the build would still *succeed*,
   produce `app-release-unsigned.apk`, and the next step would rename whatever `ls *.apk` found and
   publish it — and an unsigned APK installs nowhere. A step now refuses an `*unsigned*` filename
   and runs `apksigner verify --print-certs`, which also puts the certificate fingerprint in the log
   to compare against `docs/release/signing.md` §1.
2. **A tag could disagree with the version it ships.** Tagging `v1.0.0` on a commit still reading
   `0.9.0` would publish `atomic-launcher-v1.0.0.apk` containing 0.9.0, and `versionCode` cannot be
   walked back. A guard compares `${GITHUB_REF_NAME#v}` against `versionName`, and runs first, so a
   bad tag fails in seconds rather than after the build.
3. **A re-run was impossible.** `gh release create` with files attached fails if the release already
   exists, so a half-finished publish left the tag unusable. It is now `view || create` followed by
   `upload --clobber`, and it prints the published asset names.
4. **A good build could vanish with the runner.** The APK, `SHA256SUMS` and `mapping.txt` are now
   also uploaded as a workflow artifact with `if: always()` and 90-day retention, so a failing
   publish step does not mean rebuilding under a new tag.

The app bundle and its `play-bundle` artifact were removed from both workflows: ADR 0004 dropped the
bundle, and CI was still spending a `bundleRelease` on every push to produce it.

## Regression: every harness, one build, one emulator at a time

Against the 1.0.0 build (`versionCode` 10000, release APK 364,678 B unsigned):

| Harness | API 26 | API 36 |
|---|---|---|
| home | 18/18 | 18/18 |
| settings | 29/29 | 29/29 |
| gestures | 24/24 | 24/24 |
| search | 19/19 | 19/19 |
| badges | 49/49 | 52/52 |
| background | 25/25 | 25/25 |
| themes | 29/29 | 29/29 |
| system | 25/25 | 24/24 |
| **release (new)** | **12/12** | **12/12** |

No failures on either level. The badge and system totals differ by level because some checks only
run on one of them, as in Phase 8.

### One emulator at a time

The first attempt ran both emulators at once and produced failures that were not real: gestures
20/24 on API 26, home 17/18 on API 36. Both came back clean on a solo re-run. This machine has
14 GB and two headless emulators leave nothing free, so the harnesses begin reading screens that
have not settled — the CPU was never the constraint (16 cores, load 3.4). `docs/release/checklist.md`
now says to run them one after the other. The cost of the mistake was not the wasted hour but the
near-miss: those numbers would have gone into this note as defects.

### One genuine defect, in a harness

`verify-settings.sh` asserted `text="atomic 0.1.0-debug (1)"`, a literal that the version bump to
1.0.0 broke — and that would have broken again at every future release. It now reads `versionName`
and `versionCode` out of `app/build.gradle.kts`, so the check is about About reporting what was
actually built. 29/29 after the fix, on both levels.
