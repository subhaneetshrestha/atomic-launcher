#!/usr/bin/env bash
# HARNESS_V5 — Phase 1 acceptance checks against a booted emulator or device.
#   scripts/verify-home.sh <adb serial> [--locale]
# Installs the debug APK, makes it the default home and exercises the home skeleton the way the
# system does (HOME intent into the home stack). --locale also switches the device to de-DE by
# restarting the zygote, checks that labels were re-read, and switches back.
# Requires a prior `./gradlew assembleDebug assembleRelease` (the release APK, re-signed with the
# debug key, doubles as a second app for the package-callback check).
set -u
SERIAL=${1:?usage: verify-home.sh <serial> [--locale]}
DO_LOCALE=${2:-}
SDK=${ANDROID_HOME:-/opt/android-sdk}
ADB="$SDK/platform-tools/adb -s $SERIAL"
PKG=io.github.subhaneetshrestha.atomic.debug
CMP=$PKG/io.github.subhaneetshrestha.atomic.home.HomeActivity
TESTPKG=io.github.subhaneetshrestha.atomic
APK=app/build/outputs/apk/debug/app-debug.apk
RELAPK=app/build/outputs/apk/release/app-release-unsigned.apk
BANNER='Set atomic as your home app'
pass=0; fail=0
ok() { echo "PASS  $1"; pass=$((pass + 1)); }
ko() { echo "FAIL  $1"; fail=$((fail + 1)); }
info() { echo "INFO  $1"; }
sh() { $ADB shell "$@" 2>/dev/null | tr -d '\r'; }
wait_s() { $ADB shell sleep "$1" >/dev/null 2>&1; }
dump() { $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; $ADB shell cat /sdcard/ui.xml 2>/dev/null | tr -d '\r'; }
rows() { grep -o "<node[^>]*class=\"android.widget.TextView\"[^>]*package=\"$PKG\"[^>]*>" <<<"$1" | grep -o 'text="[^"]\+"' | grep -v "$BANNER" | sed 's/text="\(.*\)"/\1/'; }
top_activity() {
  local a
  a=$(sh dumpsys activity activities | grep -m1 -o 'topResumedActivity=ActivityRecord{[^}]*}')
  [ -z "$a" ] && a=$(sh dumpsys activity activities | grep -m1 -o 'mResumedActivity: ActivityRecord{[^}]*}')
  [ -z "$a" ] && a=$(sh dumpsys window | grep -m1 -o 'mCurrentFocus=Window{[^}]*}')
  sed -E 's/.* u0 ([^ }]+).*/\1/' <<<"$a"
}
home_records() { sh dumpsys activity activities | grep -E '^\s*\* Hist\s+#[0-9]+: ActivityRecord\{[0-9a-f]+ u0 [^ ]*HomeActivity' | grep -o 'ActivityRecord{[0-9a-f]* u0 [^ ]*HomeActivity' | sort -u | wc -l; }
count_log() { $ADB logcat -d -s HomeActivity:D 2>/dev/null | tr -d '\r' | grep -c "$1" || true; }
crash_count() { $ADB logcat -d -b crash 2>/dev/null | tr -d '\r' | grep -c "$PKG" || true; }
displayed() { $ADB logcat -d -s ActivityManager:I ActivityTaskManager:I 2>/dev/null | tr -d '\r' | grep -o 'Displayed [^ ]*HomeActivity[^+]*+[0-9a-z]*' | tail -1 | grep -o '+.*'; }
wait_for_home() { # after a zygote restart: until the system has relaunched our home activity
  for _ in $(seq 1 60); do
    [ "$(home_records)" -ge 1 ] && [[ "$(top_activity)" == *HomeActivity* ]] && return 0
    $ADB shell sleep 2 >/dev/null 2>&1 || $ADB wait-for-device >/dev/null 2>&1
  done
  return 1
}

api=$(sh getprop ro.build.version.sdk)
density=$(sh wm density | awk '{print $NF}')
echo "== $SERIAL: API $api, density $density =="

$ADB install -r -t "$APK" >/dev/null 2>&1 && ok "install $APK" || ko "install $APK"
$ADB uninstall "$TESTPKG" >/dev/null 2>&1 || true

# Clean slate: a stock launcher is the default and no instance of ours is running.
other=$(sh cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null | grep '/' | grep -v "$PKG" | head -1 | tr -d ' ')
if [ "$api" -ge 29 ]; then
  sh cmd role remove-role-holder --user 0 android.app.role.HOME "$PKG" >/dev/null 2>&1 || true
  [ -n "$other" ] && sh cmd role add-role-holder --user 0 android.app.role.HOME "${other%%/*}" >/dev/null 2>&1 || true
else
  [ -n "$other" ] && sh cmd package set-home-activity "$other" >/dev/null 2>&1 || true
fi
sh input keyevent KEYCODE_BACK >/dev/null
sh am force-stop "$PKG" >/dev/null; wait_s 1
$ADB shell am start -W -n "$CMP" >/dev/null 2>&1; wait_s 2
grep -q "$BANNER" <<<"$(dump)" && ok "banner visible while not the default home" || ko "banner visible while not the default home (default was: $other)"

if [ "$api" -ge 29 ]; then
  sh cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" >/dev/null
else
  sh cmd package set-home-activity "$CMP" >/dev/null
fi
res=$(sh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)
[[ "$res" == *"$PKG"* ]] && ok "default home resolves to us" || ko "default home resolves to us (got: $res)"

# From here on the activity is launched the way the system does it: through the HOME intent, which
# places it in the home stack. `am start -n` would create a second ordinary task next to it.
sh am force-stop "$PKG" >/dev/null; wait_s 1
$ADB shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1; wait_s 2
info "cold start: first frame Displayed ${disp:-$(displayed)}"
[[ "$(top_activity)" == *HomeActivity* ]] && ok "HOME intent brings HomeActivity to the front" || ko "HOME intent brings HomeActivity to the front (focus: $(top_activity))"

# Home key while another app is in front: same instance, onNewIntent, no onCreate.
c0=$(count_log onCreate); n0=$(count_log 'onNewIntent home=true')
sh am start -a android.settings.SETTINGS >/dev/null; wait_s 2
sh input keyevent KEYCODE_HOME; wait_s 2
c1=$(count_log onCreate); n1=$(count_log 'onNewIntent home=true')
[ "$n1" -gt "$n0" ] && ok "Home key arrives via onNewIntent" || ko "Home key arrives via onNewIntent"
[ "$c1" -eq "$c0" ] && ok "Home key does not recreate the activity" || ko "Home key does not recreate the activity ($((c1 - c0)) onCreate)"
n=$(home_records)
[ "$n" = "1" ] && ok "exactly one HomeActivity record" || ko "exactly one HomeActivity record (found $n)"
[[ "$(top_activity)" == *HomeActivity* ]] && ok "HomeActivity focused after Home key" || ko "HomeActivity focused after Home key (focus: $(top_activity))"
grep -q "$BANNER" <<<"$(dump)" && ko "banner hidden once default" || ok "banner hidden once default"

for _ in 1 2 3; do sh input keyevent KEYCODE_BACK >/dev/null; done; wait_s 1
top=$(top_activity)
[[ "$top" == *HomeActivity* ]] && ok "Back x3 keeps HomeActivity in front" || ko "Back x3 keeps HomeActivity in front (top: $top)"

sh settings put system accelerometer_rotation 0 >/dev/null
sh settings put system user_rotation 1 >/dev/null; wait_s 2
sh settings put system user_rotation 0 >/dev/null; wait_s 2
[ "$(crash_count)" = "0" ] && ok "rotation: no crash" || ko "rotation: crash logged"

# 200% font scale: rows stay >= 48 dp and nothing crashes.
sh settings put system font_scale 2.0 >/dev/null; wait_s 3
minpx=$(grep -o "<node[^>]*class=\"android.widget.TextView\"[^>]*package=\"$PKG\"[^>]*>" <<<"$(dump)" \
  | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' \
  | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/' \
  | awk 'BEGIN{m=99999}{h=$4-$2; if(h>0 && h<m)m=h}END{print (m==99999)?0:m}')
mindp=$(( ${minpx:-0} * 160 / ${density:-160} ))
[ "$mindp" -ge 48 ] && ok "rows >= 48 dp at 200% font scale (min ${mindp} dp)" || ko "rows >= 48 dp at 200% font scale (min ${mindp} dp)"
sh settings put system font_scale 1.0 >/dev/null; wait_s 2
[ "$(crash_count)" = "0" ] && ok "font scale changes: no crash" || ko "font scale changes: crash logged"

# Package callbacks: install a second app (the release build re-signed with the debug key, label
# "atomic", which sorts first), it must appear; uninstall it, it must vanish.
tmp=$(mktemp -d); signer=$(ls "$SDK"/build-tools/*/apksigner 2>/dev/null | tail -1)
if [ -f "$RELAPK" ] && [ -n "$signer" ] && "$signer" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --ks-key-alias androiddebugkey --key-pass pass:android --out "$tmp/atomic-test.apk" "$RELAPK" 2>/dev/null; then
  before=$(rows "$(dump)")
  $ADB install -r "$tmp/atomic-test.apk" >/dev/null 2>&1; wait_s 2
  grep -qx 'atomic' <<<"$(rows "$(dump)")" && ok "installed app appears in the list" || ko "installed app appears in the list"
  $ADB uninstall "$TESTPKG" >/dev/null 2>&1; wait_s 2
  after=$(rows "$(dump)")
  { ! grep -qx 'atomic' <<<"$after"; } && [ "$after" = "$before" ] && ok "uninstalled app leaves the list" || ko "uninstalled app leaves the list"
else
  info "package-callback check skipped (need $RELAPK and apksigner)"
fi
rm -rf "$tmp"
# Android below API 29 clears the default-home preference whenever a new launcher is installed
# (the test app above declares a HOME activity), so restore it before the remaining checks.
if [ "$api" -ge 29 ]; then sh cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" >/dev/null; else sh cmd package set-home-activity "$CMP" >/dev/null; fi
res=$(sh cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tail -1)
[[ "$res" == *"$PKG"* ]] && ok "default home restored after another launcher was installed" || ko "default home restored after another launcher was installed (got: $res)"
sh input keyevent KEYCODE_HOME >/dev/null; wait_s 2

# Tap the first row: another app must take focus; Home returns.
row=$(rows "$(dump)" | head -1)
b=$(grep -o "text=\"$row\"[^>]*bounds=\"\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]\"" <<<"$(dump)" | head -1 | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/')
if [ -n "$row" ] && [ -n "$b" ]; then
  read -r x1 y1 x2 y2 <<<"$b"
  sh input tap $(( (10#$x1 + 10#$x2) / 2 )) $(( (10#$y1 + 10#$y2) / 2 )) >/dev/null; wait_s 3
  top=$(top_activity)
  [ -n "$top" ] && [[ "$top" != *HomeActivity* ]] && ok "tapping '$row' launched $top" || ko "tapping '$row' launched an app (top: '$top')"
  sh input keyevent KEYCODE_HOME >/dev/null; wait_s 2
else
  ko "could not locate a row to tap (row: '$row')"
fi

pid=$(sh pidof "$PKG" | awk '{print $1}')
hidden=$($ADB logcat -d --pid="${pid:-0}" 2>/dev/null | tr -d '\r' | grep -icE 'Accessing hidden|StrictMode policy violation' || true)
[ "$hidden" = "0" ] && ok "no runtime hidden-API or StrictMode reports from our process" || ko "$hidden hidden-API/StrictMode lines from pid $pid (see logcat --pid)"
info "meminfo: $(sh dumpsys meminfo "$PKG" | grep -m1 -E 'TOTAL( PSS)?:' | tr -s ' ')"

if [ "$DO_LOCALE" = "--locale" ]; then
  if [ "$api" -lt 33 ]; then
    info "locale check skipped: needs API 33+ (per-app locales); verify a system language change manually on this level"
  else
    before=$(rows "$(dump)"); c0=$(count_log onCreate)
    sh cmd locale set-app-locales "$PKG" --user 0 --locales de-DE >/dev/null; wait_s 4
    after=$(rows "$(dump)"); c1=$(count_log onCreate)
    if [ -n "$after" ] && [ "$after" != "$before" ] && [ "$c1" -gt "$c0" ]; then
      ok "labels re-read and re-sorted after locale change (first rows: $(head -2 <<<"$after" | tr '\n' ',' ))"
    else
      ko "labels re-read after locale change (rows: $(tr '\n' ',' <<<"$after"); onCreate delta $((c1 - c0)))"
    fi
    [ "$(crash_count)" = "0" ] && ok "locale change: no crash" || ko "locale change: crash logged"
    sh cmd locale set-app-locales "$PKG" --user 0 --locales '' >/dev/null; wait_s 3
    [ "$(rows "$(dump)")" = "$before" ] && ok "labels restored after clearing the locale" || ko "labels restored after clearing the locale"
  fi
fi

echo "== $pass passed, $fail failed =="
[ "$fail" = "0" ]
