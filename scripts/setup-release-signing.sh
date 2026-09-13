#!/usr/bin/env bash
#
# Generates the release keystore and hands the four values to GitHub as secrets, so that a
# `vX.Y.Z` tag can build and publish a signed APK. Run once, on the maintainer's machine.
#
#   scripts/setup-release-signing.sh
#
# The keystore never enters the repository and never leaves this machine except as a secret.
# Losing it makes every existing install un-updatable — there is no Play App Signing to recover
# from (ADR 0004) — so the last thing this script does is tell you to back it up.
set -eu

KEYSTORE="${ATOMIC_KEYSTORE:-$HOME/.config/atomic/release.jks}"
ALIAS="${ATOMIC_KEY_ALIAS:-atomic}"

command -v keytool >/dev/null || { echo "keytool not found — install a JDK 17."; exit 1; }
command -v gh >/dev/null || { echo "gh not found — install github-cli and run 'gh auth login'."; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "gh is not logged in — run 'gh auth login' first."; exit 1; }

if [ -f "$KEYSTORE" ]; then
    echo "Using the keystore already at $KEYSTORE."
    echo "(Delete it only if you are certain no release was ever signed with it.)"
    printf 'Store password: '; read -rs STOREPASS; echo
else
    echo "No keystore at $KEYSTORE — generating one."
    echo "Choose a strong password. PKCS12 uses it for both the store and the key, and you will"
    echo "need it again only if you ever move the keystore to another machine."
    printf 'New password (min 6 chars): '; read -rs STOREPASS; echo
    printf 'Again: '; read -rs CONFIRM; echo
    [ "$STOREPASS" = "$CONFIRM" ] || { echo "They do not match."; exit 1; }
    [ ${#STOREPASS} -ge 6 ] || { echo "keytool requires at least 6 characters."; exit 1; }

    mkdir -p "$(dirname "$KEYSTORE")"; chmod 700 "$(dirname "$KEYSTORE")"
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" -storetype PKCS12 \
        -alias "$ALIAS" -keyalg RSA -keysize 4096 -validity 10000 \
        -storepass "$STOREPASS" -keypass "$STOREPASS" \
        -dname "CN=atomic launcher, O=Subhaneet Shrestha" >/dev/null
    chmod 600 "$KEYSTORE"
    echo "Generated $KEYSTORE."
fi

# Prove the password works before handing anything to GitHub: a wrong one here becomes a failed
# release later, and a tag cannot be reused.
keytool -list -keystore "$KEYSTORE" -alias "$ALIAS" -storepass "$STOREPASS" >/dev/null 2>&1 || {
    echo "That password does not open $KEYSTORE (alias $ALIAS)."; exit 1; }

FINGERPRINT=$(keytool -list -v -keystore "$KEYSTORE" -alias "$ALIAS" -storepass "$STOREPASS" |
    grep -m1 'SHA256:' | sed 's/.*SHA256: *//')

echo "Setting the four repository secrets…"
base64 -w0 "$KEYSTORE" | gh secret set ATOMIC_KEYSTORE_B64
printf '%s' "$STOREPASS" | gh secret set ATOMIC_KEYSTORE_PASSWORD
printf '%s' "$ALIAS"     | gh secret set ATOMIC_KEY_ALIAS
printf '%s' "$STOREPASS" | gh secret set ATOMIC_KEY_PASSWORD

echo
gh secret list
echo
echo "Certificate SHA-256: $FINGERPRINT"
echo "This is what a downloader sees. The release workflow prints it too, so they can be compared."
echo
echo "BACK UP $KEYSTORE AND ITS PASSWORD, OFFLINE, NOW."
echo "Without them no future version can ever update an install of this one."
echo
echo "Then cut the release:"
echo "    git tag -a v1.0.0 -m 'atomic v1.0.0' && git push origin v1.0.0"
