# ADR 0004 — GitHub Releases is the only channel for v1

Date: 2026-09-12. Status: accepted. Supersedes decision 4 of
[ADR 0001](0001-v1-scope-stack-distribution.md) (Google Play and F-Droid alongside GitHub).

## Context

ADR 0001 planned three channels for the same GMS-free build: Google Play as an app bundle, F-Droid
and GitHub as an APK. That decision carried work that has nothing to do with the launcher itself —
a Play accessibility declaration with a demo video, a data-safety form, a privacy policy page,
fastlane listing text, F-Droid metadata and the reproducible-build hygiene F-Droid's verification
needs — and it made the first release wait on a Play Console account.

The maintainer has decided to publish on GitHub only, and to build the APK in GitHub Actions.

## Decision

1. **One channel.** A tag `vX.Y.Z` builds and publishes a signed APK, its SHA-256 and the R8
   mapping file to a GitHub Release. `.github/workflows/release.yml` already does exactly this.
2. **One key, held by the maintainer.** The keystore lives outside the repository and reaches CI
   as four secrets. There is no Play App Signing and no upload key: the certificate in the release
   is the certificate users check. Losing the keystore makes every install un-updatable, so it is
   backed up offline.
3. **Dropped from v1**: the app bundle, the Play accessibility declaration and demo video, the
   data-safety form, fastlane text, `fdroid/metadata/`, and the reproducible double-build gate that
   only F-Droid's rebuild-and-compare would have used.
4. **Kept**: everything that is about the app rather than a shop. The APK size gate, the GMS-free
   classpath gate, the manifest policy gate, no `QUERY_ALL_PACKAGES`, and a disclosure screen
   before every privileged grant. These are the reasons the launcher is worth installing, not
   paperwork for a reviewer.

## Consequences

- Updates are not automatic. The README points at Obtainium, which watches GitHub Releases; the app
  never updates itself.
- Sideloaded installs meet Android's restricted-settings guard when they turn on notification
  access or accessibility, so the help flow built in Phase 5 stays and matters more, not less.
- Play and F-Droid remain open later. Nothing in the build is Play-specific, the application id is
  a reverse-domain name the maintainer controls, and reproducibility hygiene (pinned versions, no
  build-time values, `dependenciesInfo` off) is still in place because it costs nothing to keep.
  Publishing on Play later would mean generating an upload key or enrolling this one.
