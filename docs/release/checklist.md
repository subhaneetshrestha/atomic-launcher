# Cutting a release

GitHub Releases is the only channel ([ADR 0004](../decisions/0004-github-only-distribution.md)).
A tag does the work: `.github/workflows/release.yml` builds, signs, gates and publishes. Everything
below is what happens either side of pushing it.

## Once, before the first release

- [ ] Run `scripts/setup-release-signing.sh`. It generates the keystore, sets the four repository
      secrets and prints the certificate fingerprint — [signing.md](signing.md) covers what it does
      and why. The release workflow fails on its first step without `ATOMIC_KEYSTORE_B64`, so a
      missing secret costs a tag rather than publishing something broken.
- [ ] Back the keystore and its password up offline. Losing them makes every install un-updatable;
      there is no Play App Signing to recover from.

## Every release

**1. Decide the version.** `versionCode = MAJOR × 10000 + MINOR × 100 + PATCH`,
`versionName = "MAJOR.MINOR.PATCH"`, tag `vMAJOR.MINOR.PATCH`. Both are literals in
`app/build.gradle.kts` — never derived from git or the clock, so the same commit always builds the
same APK. `versionCode` may only ever go up.

**2. Check the tree is releasable.**

```sh
./gradlew ktlintCheck test lintRelease assembleRelease checkReleaseApkSize gmsGuard
scripts/check-manifest-policy.sh
```

Green means: every module's JVM tests pass, lint has nothing fatal, the APK is inside the 2.5 MiB
budget, no Google Play services reached the classpath, and the shipped manifest declares only the
five permissions atomic promises.

**3. Run the acceptance harnesses** on a fresh API 26 boot and on API 36, against **one** build:

```sh
./gradlew assembleDebug assembleRelease
for h in home settings gestures search badges background themes system
    scripts/verify-$h.sh $SERIAL
end
scripts/verify-release.sh $SERIAL    # and the minified build that actually ships
```

The eight phase harnesses install the debug APK, which is not minified. `verify-release.sh` is the
only one that runs what users get: it catches an R8 rule that breaks kotlinx.serialization, which
looks like an empty home screen and shows up in no JVM test.

Run the two emulators one after the other, not at once — two on swiftshader starve each other and
the harnesses start reading a screen that has not settled. A long-lived API 26 image also stops
binding the notification listener, which shows up as badges collapsing to "none"; reboot it rather
than chasing it. Never rebuild the APK or edit a harness while one is running.

**4. Write the acceptance note** in `docs/verification/` — what passed, what the emulators could not
answer, what a review of the changes found.

**5. The release commit.** Bump the two version literals, update the README if what it claims has
changed, commit, push, and wait for CI to go green on `main`. A tag on a red commit publishes a
broken APK.

**6. Tag and push.**

```sh
git tag -a v1.0.0 -m "atomic v1.0.0"
git push origin v1.0.0
```

**7. Watch the run.** `gh run watch` — it signs with the four secrets, re-runs the tests, the size
gate, the GMS guard and the policy gate, then publishes the APK, `SHA256SUMS` and `mapping.txt` to
the release. The mapping file is what makes a future crash report readable; it is published on
purpose and reveals nothing the APK does not.

**8. Check what shipped.**

```sh
gh release view v1.0.0
apksigner verify --print-certs atomic-launcher-v1.0.0.apk   # the fingerprint from signing.md §1
sha256sum -c SHA256SUMS
```

Install it over the previous release on a device — same certificate means an update, not a refused
install. An install that asks you to uninstall first means the APK was signed with the wrong key;
do not publish that, and do not tell anyone to uninstall.

## If a release is wrong

Delete the GitHub Release and the tag, fix, and cut the next patch version. Never re-tag: someone
may already have the old APK, and a `versionCode` that stands still cannot update them.

```sh
gh release delete v1.0.0 --yes
git push origin :refs/tags/v1.0.0 && git tag -d v1.0.0
```
