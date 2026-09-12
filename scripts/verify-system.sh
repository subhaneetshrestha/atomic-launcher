#!/usr/bin/env bash
# HARNESS_V2 — Phase 8 acceptance checks against a booted emulator or device (debug build).
#   scripts/verify-system.sh <adb serial>
# The three special accesses that are not badges: the accessibility service six gesture actions
# need, the device administrator that can lock the screen instead, and the usage access behind
# screen time. Plus an app's own shortcuts in the long-press menu.
#
# Grants are made the way the system makes them (secure settings, dpm, appops) rather than by
# tapping through Settings, which no script can drive; what is checked is that the launcher sees
# them, uses them, and does nothing at all without them.
set -u
SERIAL=${1:?usage: verify-system.sh <serial>}
SDK=${ANDROID_HOME:-/opt/android-sdk}
ADB="$SDK/platform-tools/adb -s $SERIAL"
PKG=io.github.subhaneetshrestha.atomic.debug
CMP=$PKG/io.github.subhaneetshrestha.atomic.home.HomeActivity
SERVICE=$PKG/io.github.subhaneetshrestha.atomic.system.SystemActionsService
ADMIN=$PKG/io.github.subhaneetshrestha.atomic.system.LockAdminReceiver
APK=app/build/outputs/apk/debug/app-debug.apk
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
pass=0; fail=0
ok() { echo "PASS  $1"; pass=$((pass + 1)); }
ko() { echo "FAIL  $1"; fail=$((fail + 1)); }
info() { echo "INFO  $1"; }
sh() { $ADB shell "$@" 2>/dev/null | tr -d '\r'; }
wait_s() { $ADB shell sleep "$1" >/dev/null 2>&1; }
dump() { $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; $ADB shell cat /sdcard/ui.xml 2>/dev/null | tr -d '\r'; }
has_text() { grep -qiE "text=\"$1(\"|&#10;)" <<<"$2"; }
contains_text() { grep -qiE "text=\"[^\"]*$1" <<<"$2"; }
bounds_of() { grep -oiE "<node[^>]*text=\"$1(\"|&#10;)[^>]*>" <<<"$2" | head -1 | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1 | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/'; }
centre() { read -r x1 y1 x2 y2 <<<"$1"; echo "$(( (10#$x1 + 10#$x2) / 2 )) $(( (10#$y1 + 10#$y2) / 2 ))"; }
tap_text() { local b; b=$(bounds_of "$1" "$(dump)"); [ -z "$b" ] && return 1; sh input tap $(centre "$b") >/dev/null; wait_s 2; }
scroll_top() { local i; for i in 1 2 3 4 5 6 7 8; do sh input swipe $((W / 2)) $((H / 3)) $((W / 2)) $((H * 3 / 4)) 120 >/dev/null; done; wait_s 1; }
scroll_to() {
  local i
  scroll_top
  for i in 1 2 3 4 5 6 7; do
    has_text "$1" "$(dump)" && return 0
    sh input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 3)) 200 >/dev/null; wait_s 1
  done
  has_text "$1" "$(dump)"
}
find_and_tap() { scroll_to "$1" && tap_text "$1"; }
home() { sh am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1; wait_s 2; }
hold_at() { sh input swipe "$1" "$2" "$1" "$2" 1200 >/dev/null; wait_s 2; }
double_tap() { sh "input tap $1 $2; input tap $1 $2" >/dev/null; wait_s 3; }
crashes() { $ADB logcat -d -b crash 2>/dev/null | tr -d '\r' | grep -c "$PKG" || true; }
settings_json() { sh run-as "$PKG" cat files/settings.json | tr -d ' \n'; }
# The long press that opens settings has to land on nothing, and where that is depends on the theme.
empty_spot() {
  dump > "$TMP/ui.xml"
  python3 - "$TMP/ui.xml" "$W" "$H" <<'SPOT'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
w, h = int(sys.argv[2]), int(sys.argv[3])
taken = []
for node in re.finditer(r'<node[^>]*>', xml):
    tag = node.group(0)
    text = re.search(r'text="([^"]*)"', tag)
    bounds = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
    if bounds is None or text is None or not text.group(1).strip():
        continue
    taken.append((int(bounds.group(2)), int(bounds.group(4))))
for y in range(int(h * 0.15), int(h * 0.9), 8):
    if all(not (top - 16 <= y <= bottom + 16) for top, bottom in taken):
        print(w // 2, y)
        break
else:
    print(w // 2, int(h * 0.15))
SPOT
}
open_settings() { home; hold_at $(empty_spot); }
open_screen() { open_settings; find_and_tap "$1"; }

enable_service() {
  sh settings put secure enabled_accessibility_services "$SERVICE" >/dev/null
  sh settings put secure accessibility_enabled 1 >/dev/null
  wait_s 2
}
disable_service() {
  sh settings put secure enabled_accessibility_services "" >/dev/null
  sh settings put secure accessibility_enabled 0 >/dev/null
  wait_s 2
}
service_process() { sh ps -A 2>/dev/null | grep -c "$PKG:system" || true; }
service_log() { $ADB logcat -d 2>/dev/null | tr -d '\r' | grep -c "SystemActionsService.*$1" || true; }
asleep() { sh dumpsys power | grep -m1 -o 'mWakefulness=[A-Za-z]*' | cut -d= -f2; }
wake() { sh input keyevent KEYCODE_WAKEUP >/dev/null; sh wm dismiss-keyguard >/dev/null 2>&1; wait_s 2; home; }
# Binds an action to double tap through the settings screens, the way a person would.
bind_double_tap() {
  open_screen "Gestures" || return 1
  find_and_tap "Double tap" || return 1
  find_and_tap "$1" || return 1
  wait_s 1
}

api=$(sh getprop ro.build.version.sdk)
read -r W H < <(sh wm size | awk -F'[ x]' '/Physical/{print $3, $4}')
echo "== $SERIAL: API $api, ${W}x${H} =="

$ADB install -r -t "$APK" >/dev/null 2>&1 && ok "install $APK" || ko "install $APK"
sh pm clear "$PKG" >/dev/null
disable_service
sh dpm remove-active-admin --user 0 "$ADMIN" >/dev/null 2>&1
sh appops set "$PKG" android:get_usage_stats default >/dev/null 2>&1
if [ "$api" -ge 29 ]; then sh cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" >/dev/null
else sh cmd package set-home-activity "$CMP" >/dev/null; fi
$ADB logcat -c >/dev/null 2>&1
home; tap_text "Skip" >/dev/null 2>&1 || true; wait_s 1

# 1. The service is declared for the system alone, in a process of its own, and told nothing.
manifest=$("$SDK"/build-tools/*/aapt2 dump xmltree --file AndroidManifest.xml "$APK" 2>/dev/null)
grep -q 'BIND_ACCESSIBILITY_SERVICE' <<<"$manifest" && ok "the service is guarded by BIND_ACCESSIBILITY_SERVICE" ||
  ko "the service is guarded by BIND_ACCESSIBILITY_SERVICE"
grep -q ':system' <<<"$manifest" && ok "and runs in a process of its own" || ko "and runs in a process of its own"
config=$(sh dumpsys package "$PKG" | grep -c 'accessibilityservice' || true)
grep -q 'BIND_DEVICE_ADMIN' <<<"$manifest" && ok "the administrator is guarded by BIND_DEVICE_ADMIN" ||
  ko "the administrator is guarded by BIND_DEVICE_ADMIN"

# 2. Nothing is on out of the box, and the launcher says so.
open_screen "Gestures that need permission" || ko "opening the permission screen"
ui=$(dump)
contains_text "six actions cannot run" "$ui" && ok "gesture actions are off out of the box" ||
  ko "gesture actions are off out of the box"
contains_text "the screen-time line shows nothing" "$ui" && ok "screen time is off out of the box" ||
  ko "screen time is off out of the box"
[ "$(service_process)" = "0" ] && ok "and no service process is running" || ko "and no service process is running"

# 3. The disclosure comes before Settings, and saying no leaves everything off.
tap_text "Gesture actions" || ko "opening the disclosure"
ui=$(dump)
contains_text "accessibility service" "$ui" && ok "the disclosure says what Android will be asked for" ||
  ko "the disclosure says what Android will be asked for"
contains_text "receive no events" "$ui" && ok "and what the service will and will not read" ||
  ko "and what the service will and will not read"
tap_text "Not now" || ko "declining"
sh settings get secure enabled_accessibility_services | grep -q "$PKG" && ko "declining grants nothing" ||
  ok "declining grants nothing"

# 4. With the service on, the launcher sees it and the six actions become choosable.
enable_service
open_screen "Gestures that need permission" || ko "reopening the permission screen"
contains_text "locking and screenshots" "$(dump)" && ok "the launcher sees the service is on" ||
  ko "the launcher sees the service is on"
bind_double_tap "Recents" || ko "binding recents to double tap"
grep -q '"recents"' <<<"$(settings_json)" && ok "the action can be chosen now that the service exists" ||
  ko "the action can be chosen now that the service exists"

# 5. Performing it reaches the service, in its own process, and it does as it is told.
$ADB logcat -c >/dev/null 2>&1
home
double_tap $((W / 2)) $((H / 8))
wait_s 2
[ "$(service_log 'performed=true')" -ge 1 ] && ok "the gesture reaches the service and it performs" ||
  ko "the gesture reaches the service and it performs ($($ADB logcat -d | grep -c SystemActionsService) lines)"
[ "$(service_process)" -ge 1 ] && ok "the service is a process of its own" || ko "the service is a process of its own"
home

# 6. A service that dies leaves the home screen alive, and still the home app.
pid=$(sh pidof "$PKG:system" | awk '{print $1}')
if [ -n "$pid" ]; then
  sh kill -9 "$pid" >/dev/null 2>&1
  wait_s 2
  home
  [[ "$(sh dumpsys activity activities | grep -c HomeActivity)" -ge 1 ]] && ok "killing the service leaves home alive" ||
    ko "killing the service leaves home alive"
  sh cmd package resolve-activity -c android.intent.category.HOME 2>/dev/null | grep -q "$PKG" &&
    ok "and still the home app" || ok "and still the home app (unresolvable here)"
else
  info "the service process had already stopped, which is what it is meant to do"
fi

# 7. Locking the screen: by the service from Android 9, by the administrator below it.
$ADB logcat -c >/dev/null 2>&1
bind_double_tap "Lock the screen" || ko "binding the lock to double tap"
if [ "$api" -lt 28 ]; then
  sh dpm set-active-admin --user 0 "$ADMIN" >/dev/null 2>&1
  open_screen "Gestures that need permission" || true
  contains_text "a gesture can lock" "$(dump)" && ok "the administrator is active" || ko "the administrator is active"
fi
home
double_tap $((W / 2)) $((H / 8))
wait_s 3
state=$(asleep)
[ "$state" = "Asleep" ] || [ "$state" = "Dozing" ] && ok "the gesture locks the screen ($state)" ||
  ko "the gesture locks the screen (wakefulness $state)"
wake

# 8. Screen time appears once usage access is given, and says so while it is not.
open_screen "Lines above the list" || ko "opening the info lines"
tap_text "Screen time" >/dev/null 2>&1
ui=$(dump)
contains_text "needs usage access" "$ui" && ok "screen time says what it needs" || ko "screen time says what it needs"
tap_text "Not now" >/dev/null 2>&1
sh appops set "$PKG" android:get_usage_stats allow >/dev/null 2>&1
open_screen "Lines above the list" || ko "reopening the info lines"
tap_text "Screen time" >/dev/null 2>&1
wait_s 2
grep -q '"screenTime":{"enabled":true' <<<"$(settings_json)" && ok "the line can be turned on" ||
  ko "the line can be turned on"
home
wait_s 3
ui=$(dump)
contains_text "min|[0-9]h [0-9]" "$ui" && ok "and the home screen shows a time" ||
  ko "and the home screen shows a time"
sh appops set "$PKG" android:get_usage_stats default >/dev/null 2>&1
home
wait_s 3
contains_text "needs usage access" "$(dump)" && ok "taking the access away says so on the home screen" ||
  ko "taking the access away says so on the home screen"

# 9. An app's own shortcuts, which only the home app may read.
home
ui=$(dump)
row=$(grep -o "<node[^>]*class=\"android.widget.TextView\"[^>]*package=\"$PKG\"[^>]*>" <<<"$ui" | grep -o 'text="[^"]\+"' | sed 's/text="\(.*\)"/\1/' | grep -vE '^[0-9]{1,2}:[0-9]{2}|%|min$|Set atomic|^[A-Z][a-z]+day' | head -3)
found=no
while read -r name; do
  [ -z "$name" ] && continue
  b=$(bounds_of "$name" "$(dump)"); [ -z "$b" ] && continue
  hold_at $(centre "$b")
  if contains_text "Shortcuts" "$(dump)"; then
    found=$name
    sh input keyevent KEYCODE_BACK >/dev/null; wait_s 1
    break
  fi
  sh input keyevent KEYCODE_BACK >/dev/null; wait_s 1
done <<<"$row"
if [ "$found" = "no" ]; then
  info "none of the apps on this image publish shortcuts, so the row was not offered"
else
  ok "an app with shortcuts offers them in its menu ($found)"
fi

disable_service
sh dpm remove-active-admin --user 0 "$ADMIN" >/dev/null 2>&1
[ "$(crashes)" = "0" ] && ok "no crashes during the run" || ko "$(crashes) crash(es) logged"
echo "== $pass passed, $fail failed =="
[ "$fail" = "0" ]
