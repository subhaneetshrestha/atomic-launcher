#!/usr/bin/env bash
#
# Manifest and DEX policy gate.
#
# atomic makes promises that are checkable from the outside of the APK: it never asks to see every
# installed app, never takes the wallpaper or the boot broadcast, never ships Google Play services,
# and never leaves a release build debuggable. A library, a merged manifest or a careless commit can
# break any of those without a single line of our own code changing, so the gate reads the built
# artifact rather than the sources.
#
# Usage: scripts/check-manifest-policy.sh [path/to/app.apk]
# Default APK: app/build/outputs/apk/release/*.apk
set -u

repo="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

apk="${1:-}"
if [ -z "$apk" ]; then
    apk=$(ls "$repo"/app/build/outputs/apk/release/*.apk 2>/dev/null | head -1)
fi
if [ -z "$apk" ] || [ ! -f "$apk" ]; then
    echo "FAIL: no release APK found. Run ./gradlew assembleRelease first." >&2
    exit 1
fi

# aapt2 lives in the SDK's build-tools; take the newest installed.
sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-/opt/android-sdk}}"
aapt2=$(ls -d "$sdk"/build-tools/*/aapt2 2>/dev/null | sort -V | tail -1)
if [ -z "$aapt2" ]; then
    echo "FAIL: no aapt2 under $sdk/build-tools." >&2
    exit 1
fi

# The permissions atomic declares, and the reason each one is defensible. Anything outside this list
# fails the gate: adding a permission is a decision, not a merge artifact.
#   REQUEST_DELETE_PACKAGES  normal   uninstall through the system dialog
#   INTERNET                 normal   fetching a background from a collection the user configured
#   ACCESS_NETWORK_STATE     normal   whether that fetch is allowed to run now
#   EXPAND_STATUS_BAR        normal   opening the shade where the platform still permits it
#   PACKAGE_USAGE_STATS      special  puts atomic in the Usage Access list; grants nothing itself
#   DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION  androidx.core defines this one per-app, signature-level
allowed_permissions="
android.permission.REQUEST_DELETE_PACKAGES
android.permission.INTERNET
android.permission.ACCESS_NETWORK_STATE
android.permission.EXPAND_STATUS_BAR
android.permission.PACKAGE_USAGE_STATS
io.github.subhaneetshrestha.atomic.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION
"

# Named separately from the allowlist so the failure says which promise was broken, not just
# "unexpected permission". These are the ones a launcher is most often tempted by.
forbidden_permissions="
android.permission.QUERY_ALL_PACKAGES
android.permission.ACCESS_HIDDEN_PROFILES
android.permission.SET_WALLPAPER
android.permission.POST_NOTIFICATIONS
android.permission.RECEIVE_BOOT_COMPLETED
"

# Type descriptors as they appear in the DEX string table.
forbidden_classes="
Lcom/google/android/gms/
Lcom/google/firebase/
Lcom/google/android/play/
"

failures=0
checks=0

fail() {
    failures=$((failures + 1))
    echo "  FAIL  $1"
}

pass() {
    echo "  ok    $1"
}

check() { checks=$((checks + 1)); }

echo "Manifest policy: $(basename "$apk")"

manifest_permissions=$("$aapt2" dump permissions "$apk" | sed -n "s/^uses-permission: name='\([^']*\)'.*/\1/p")

# A gate that cannot fail is worse than no gate: if the dump came back empty every "no such
# permission" check below would pass for the wrong reason. atomic always declares at least one.
if [ -z "$manifest_permissions" ]; then
    echo "FAIL: aapt2 read no permissions from $apk — the dump failed, the checks below would be meaningless." >&2
    exit 1
fi

# 1. Nothing atomic promised never to declare.
for perm in $forbidden_permissions; do
    check
    if printf '%s\n' $manifest_permissions | grep -qx "$perm"; then
        fail "$perm is declared; atomic promises it never will be"
    else
        pass "no $perm"
    fi
done

# 2. Nothing outside the allowlist, however it arrived.
check
unexpected=""
for perm in $manifest_permissions; do
    printf '%s\n' $allowed_permissions | grep -qx "$perm" || unexpected="$unexpected $perm"
done
if [ -n "$unexpected" ]; then
    fail "permissions outside the allowlist:$unexpected"
    echo "        (if one of these is intended, add it to allowed_permissions with its reason)"
else
    pass "every declared permission is on the allowlist"
fi

# 3. A release build is not debuggable. aapt2 prints application-debuggable only when it is.
check
if "$aapt2" dump badging "$apk" | grep -q '^application-debuggable'; then
    fail "the APK is debuggable"
else
    pass "not debuggable"
fi

# 4. No Google Play services, Firebase or Play library in the shipped code. gmsGuard checks the
#    Gradle classpath; this checks what actually landed in the DEX.
dex=$(mktemp)
unzip -p "$apk" 'classes*.dex' > "$dex" 2>/dev/null
for class in $forbidden_classes; do
    check
    if grep -aqF "$class" "$dex"; then
        fail "the DEX references $class"
    else
        pass "no $class in the DEX"
    fi
done
rm -f "$dex"

# 5. No GMS or Firebase component declared in the manifest.
check
if "$aapt2" dump xmltree --file AndroidManifest.xml "$apk" |
    grep -qE 'com\.google\.(android\.gms|firebase|android\.play)'; then
    fail "a GMS, Firebase or Play component is declared in the manifest"
else
    pass "no GMS, Firebase or Play component in the manifest"
fi

echo
if [ "$failures" -gt 0 ]; then
    echo "Manifest policy: $((checks - failures))/$checks checks passed, $failures FAILED"
    exit 1
fi
echo "Manifest policy: $checks/$checks checks passed"
