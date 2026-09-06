# Release signing

One release key signs every channel: Google Play (through Play App Signing with our own key uploaded), F-Droid (reproducible build, our signature copied onto their verified rebuild) and GitHub Releases. Users can therefore move between stores without uninstalling. Generate the key **before the first build is handed to anyone**; a debug-signed APK cannot be updated by a release-signed one.

## 1. Generate the keystore (maintainer, offline, once)

```sh
mkdir -p ~/.config/atomic && chmod 700 ~/.config/atomic
keytool -genkeypair -v \
  -keystore ~/.config/atomic/release.jks -storetype PKCS12 \
  -alias atomic -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=atomic launcher, O=Subhaneet Shrestha"
```

Use one strong password for the store and the key (PKCS12 keystores share it). Back the file up to at least one offline location; losing it means every existing install becomes un-updatable.

Record the certificate fingerprint; F-Droid's metadata (`AllowedAPKSigningKeys`) and the Play Console both need it:

```sh
keytool -list -v -keystore ~/.config/atomic/release.jks -alias atomic | grep -A1 'SHA256'
```

The keystore, its password and any `.jks`/`.p12` file must never enter the repository (`.gitignore` blocks the usual extensions).

## 2. Local signed builds

`app/build.gradle.kts` attaches the release signing config only when these variables are set:

```fish
set -x ATOMIC_KEYSTORE ~/.config/atomic/release.jks
set -x ATOMIC_KEYSTORE_PASSWORD '...'
set -x ATOMIC_KEY_ALIAS atomic
set -x ATOMIC_KEY_PASSWORD '...'
./gradlew assembleRelease checkReleaseApkSize gmsGuard
```

Without them the release APK is unsigned, which is what CI on pull requests and F-Droid's build server produce. Verify a signed APK with `apksigner verify --print-certs app/build/outputs/apk/release/*.apk`.

## 3. GitHub Actions secrets (`.github/workflows/release.yml`)

| Secret | Value |
|---|---|
| `ATOMIC_KEYSTORE_B64` | `base64 -w0 ~/.config/atomic/release.jks` |
| `ATOMIC_KEYSTORE_PASSWORD` | the store password |
| `ATOMIC_KEY_ALIAS` | `atomic` |
| `ATOMIC_KEY_PASSWORD` | the key password |

The release workflow decodes the keystore into the runner's temp directory for the duration of the job, signs with v2 + v3 schemes, publishes the APK and its SHA-256 to the GitHub Release, and keeps the AAB as an artifact for manual Play upload.

## 4. Play App Signing with our key

In Play Console → Setup → App signing choose **Use an existing key** (export from a Java keystore). The console supplies a one-time encryption key and the `pepk` tool; run it against `~/.config/atomic/release.jks`, alias `atomic`, and upload the produced file. Play then distributes APKs signed with our certificate, so Play, F-Droid and GitHub installs update each other. The uploaded AAB itself must also be signed with this key (the release workflow does that).

## 5. F-Droid

Because builds are reproducible, F-Droid rebuilds from the tagged source, compares against our GitHub Release APK, and publishes our signed APK when they match. The fdroiddata recipe needs `AllowedAPKSigningKeys` set to the SHA-256 fingerprint from step 1 and `Binaries` pointing at the GitHub Release asset. See the release-engineering phase of the build plan for the metadata skeleton.

## 6. Versioning

`versionCode = MAJOR × 10000 + MINOR × 100 + PATCH`, `versionName = MAJOR.MINOR.PATCH`, git tag `vMAJOR.MINOR.PATCH`. Both values are literals in `app/build.gradle.kts` and change in the release commit that is tagged.
