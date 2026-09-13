# Release signing

One key signs every atomic release. There is no Play App Signing and no upload key: the certificate
in the release is the certificate users check ([ADR 0004](../decisions/0004-github-only-distribution.md)).
Generate it **before the first build is handed to anyone** — a debug-signed APK cannot be updated by
a release-signed one, and an APK signed with a different key cannot update this one either.

## 1. Generate the keystore (maintainer, offline, once)

```sh
mkdir -p ~/.config/atomic && chmod 700 ~/.config/atomic
keytool -genkeypair -v \
  -keystore ~/.config/atomic/release.jks -storetype PKCS12 \
  -alias atomic -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=atomic launcher, O=Subhaneet Shrestha"
```

Use one strong password for the store and the key (PKCS12 keystores share it). **Back the file up to
at least one offline location.** Losing it means every existing install becomes un-updatable and the
only way forward is a new application id — a different app, as far as Android is concerned.

Record the certificate fingerprint; it is what anyone verifying a downloaded APK compares against:

```sh
keytool -list -v -keystore ~/.config/atomic/release.jks -alias atomic | grep -A1 'SHA256'
```

The keystore, its password and any `.jks`/`.p12` file must never enter the repository (`.gitignore`
blocks the usual extensions).

## 2. Local signed builds

`app/build.gradle.kts` attaches the release signing config only when these variables are set:

```fish
set -x ATOMIC_KEYSTORE ~/.config/atomic/release.jks
set -x ATOMIC_KEYSTORE_PASSWORD '...'
set -x ATOMIC_KEY_ALIAS atomic
set -x ATOMIC_KEY_PASSWORD '...'
./gradlew assembleRelease checkReleaseApkSize gmsGuard
```

Without them the release APK is unsigned, which is what CI produces on pull requests. Verify a
signed APK with `apksigner verify --print-certs app/build/outputs/apk/release/*.apk`; the printed
fingerprint must be the one from step 1.

## 3. GitHub Actions secrets (`.github/workflows/release.yml`)

| Secret | Value |
|---|---|
| `ATOMIC_KEYSTORE_B64` | `base64 -w0 ~/.config/atomic/release.jks` |
| `ATOMIC_KEYSTORE_PASSWORD` | the store password |
| `ATOMIC_KEY_ALIAS` | `atomic` |
| `ATOMIC_KEY_PASSWORD` | the key password |

Add them under Settings → Secrets and variables → Actions. The release workflow decodes the keystore
into the runner's temp directory for the duration of the job, signs with the v2 and v3 schemes (v1
is off: `minSdk` is 26), and publishes the APK with its SHA-256 and the R8 mapping file.

## 4. Versioning

`versionCode = MAJOR × 10000 + MINOR × 100 + PATCH`, `versionName = MAJOR.MINOR.PATCH`, git tag
`vMAJOR.MINOR.PATCH`. Both values are literals in `app/build.gradle.kts` and change in the release
commit that is tagged. See [checklist.md](checklist.md) for the release itself.

## 5. If the key is ever compromised

Rotating a signing key is not possible for installs already in the field. Publish the fact, stop
using the key, and ship the replacement under a new application id so that no attacker-signed build
can pose as an update. This is the reason the keystore lives offline and nowhere else.
