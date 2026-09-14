#!/usr/bin/env bash
# HARNESS_V2 — Phase 3 acceptance checks against a booted emulator or device (debug build).
#   scripts/verify-gestures.sh <adb serial>
# Drives real swipes, holds and taps through adb and reads back what opened, plus the gesture
# settings screen and the action picker. Haptics cannot be observed from here.
set -u
SERIAL=${1:?usage: verify-gestures.sh <serial>}
SDK=${ANDROID_HOME:-/opt/android-sdk}
ADB="$SDK/platform-tools/adb -s $SERIAL"
PKG=io.github.subhaneetshrestha.atomic.debug
CMP=$PKG/io.github.subhaneetshrestha.atomic.home.HomeActivity
APK=app/build/outputs/apk/debug/app-debug.apk
pass=0; fail=0
ok() { echo "PASS  $1"; pass=$((pass + 1)); }
ko() { echo "FAIL  $1"; fail=$((fail + 1)); }
info() { echo "INFO  $1"; }
sh() { $ADB shell "$@" 2>/dev/null | tr -d '\r'; }
wait_s() { $ADB shell sleep "$1" >/dev/null 2>&1; }
dump() { $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; $ADB shell cat /sdcard/ui.xml 2>/dev/null | tr -d '\r'; }
has_text() { grep -qiE "text=\"$1(\"|&#10;)" <<<"$2"; }
bounds_of() { grep -oiE "<node[^>]*text=\"$1(\"|&#10;)[^>]*>" <<<"$2" | head -1 | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1 | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/'; }
centre() { read -r x1 y1 x2 y2 <<<"$1"; echo "$(( (10#$x1 + 10#$x2) / 2 )) $(( (10#$y1 + 10#$y2) / 2 ))"; }
tap_text() { local b; b=$(bounds_of "$1" "$(dump)"); [ -z "$b" ] && return 1; sh input tap $(centre "$b") >/dev/null; wait_s 2; }
# uiautomator only reports what is on screen, so a long list has to be walked. Searching always
# starts from the top, because a row looked for later may sit above where the last one left off.
contains_text() { grep -qiE "text=\"[^\"]*$1" <<<"$2"; }
scroll_top() {
  local i
  for i in 1 2 3 4 5 6 7 8; do sh input swipe $((W / 2)) $((H / 3)) $((W / 2)) $((H * 3 / 4)) 120 >/dev/null; done
  wait_s 1
}
scroll_to() {
  local i
  scroll_top
  for i in 1 2 3 4 5 6 7; do
    has_text "$1" "$(dump)" && return 0
    sh input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 3)) 200 >/dev/null
    wait_s 1
  done
  has_text "$1" "$(dump)"
}
find_and_tap() { scroll_to "$1" && tap_text "$1"; }

settings_json() { sh run-as "$PKG" cat files/settings.json; }
top() {
  local a
  a=$(sh dumpsys activity activities | grep -m1 -o 'topResumedActivity=ActivityRecord{[^}]*}')
  [ -z "$a" ] && a=$(sh dumpsys activity activities | grep -m1 -o 'mResumedActivity: ActivityRecord{[^}]*}')
  sed -E 's/.* u0 ([^ }]+).*/\1/' <<<"$a"
}
# Without -W: on older Android, waiting for a launch whose activity is already resumed can block
# for a minute at a time.
# Swipe down opens the notification shade for real since Phase 8, and an open shade holds the
# focus and swallows every touch after it — a long press that should open settings does nothing.
# Closing it belongs here rather than at the one call site, because every step that follows a
# gesture goes through home().
home() {
  sh cmd statusbar collapse >/dev/null 2>&1
  sh am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1
  wait_s 2
}
crashes() { $ADB logcat -d -b crash 2>/dev/null | tr -d '\r' | grep -c "$PKG" || true; }
# A swipe the recogniser will see: adb sends intermediate moves over the duration given.
swipe() { sh input swipe "$1" "$2" "$3" "$4" "${5:-250}" >/dev/null; wait_s 3; }
hold() { sh input swipe "$1" "$2" "$1" "$2" 1200 >/dev/null; wait_s 2; }
# Both taps have to go in one call: two adb round trips are further apart than the double-tap
# timeout, so they would only ever read as two separate taps.
double_tap() { sh "input tap $1 $2; input tap $1 $2" >/dev/null; wait_s 3; }
open_gesture_settings() {
  home
  hold $((W / 2)) $((H / 8))
  tap_text "Gestures" || return 1
}

api=$(sh getprop ro.build.version.sdk)
read -r W H < <(sh wm size | awk -F'[ x]' '/Physical/{print $3, $4}')
echo "== $SERIAL: API $api, ${W}x${H} =="
MIDX=$((W / 2)); MIDY=$((H / 2))

$ADB install -r -t "$APK" >/dev/null 2>&1 && ok "install $APK" || ko "install $APK"
sh pm clear "$PKG" >/dev/null
if [ "$api" -ge 29 ]; then sh cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" >/dev/null
else sh cmd package set-home-activity "$CMP" >/dev/null; fi
$ADB logcat -c >/dev/null 2>&1
home
tap_text "Skip" >/dev/null 2>&1 || true
wait_s 1

# 1. The shipped bindings: sideways swipes open the camera and the phone.
swipe $MIDX $MIDY $((MIDX - 250)) $MIDY
left=$(top)
[ -n "$left" ] && [[ "$left" != *"$PKG"* ]] && ok "swipe left opened $left" || ko "swipe left opened something (top: '$left')"
home
swipe $MIDX $MIDY $((MIDX + 250)) $MIDY
right=$(top)
[ -n "$right" ] && [[ "$right" != *"$PKG"* ]] && ok "swipe right opened $right" || ko "swipe right opened something (top: '$right')"
[ "$left" != "$right" ] && ok "the two directions do different things" || ko "the two directions do different things"

# 2. A hold on empty space opens the launcher's own settings.
home
hold $MIDX $((H / 8))
has_text "Gestures" "$(dump)" && ok "holding empty space opens atomic settings" || ko "holding empty space opens atomic settings"
sh input keyevent KEYCODE_BACK >/dev/null; wait_s 1

# 3. Actions this version has not built are refused, and the home screen stays put.
home
swipe $MIDX $((H - H / 4)) $MIDX $((H / 4))
[[ "$(top)" == *"$PKG"* ]] && ok "swipe up, bound to search, leaves the home screen up" || ko "swipe up leaves the home screen up (top: $(top))"
home
swipe $MIDX $((H / 4)) $MIDX $((H - H / 4))
[[ "$(top)" == *"$PKG"* ]] && ok "swipe down, bound to the shade, leaves the home screen up" || ko "swipe down leaves the home screen up"

# 4. The gesture list shows what is bound, and nothing else — a fresh install binds eight of the
# eighteen surfaces (four swipes, hold, and a tap on the clock, date and battery); the other ten
# are one tap away behind "Add a gesture" rather than listed as rows that say "nothing".
open_gesture_settings || ko "opening the gesture settings"
for surface in "Swipe up" "Swipe down" "Swipe left" "Swipe right" "Hold" "Tap the clock"; do
  scroll_to "$surface" || ko "the bound list shows '$surface'"
done
ok "the bound gesture list shows what is already set"
# Phase 3 left the six accessibility actions and three launcher surfaces saying they would arrive
# later; Phase 8 built every one of them, so nothing on this screen may still say so. What an
# action needs now is a permission or a newer Android, and the picker below says which.
scroll_top; ui=$(dump)
if grep -qi 'later version of atomic' <<<"$ui"; then
  ko "no surface is still waiting for a later version ($(grep -oi 'text="[^"]*later version[^"]*"' <<<"$ui" | head -1))"
else
  ok "no surface is still waiting for a later version"
fi
grep -qi 'Camera' <<<"$ui" && ok "the list shows what each gesture does" || ko "the list shows what each gesture does"

# 4b. Everything not yet bound is grouped, one tap away, behind "Add a gesture".
find_and_tap "Add a gesture" || ko "opening the add-a-gesture screen"
ui=$(dump)
has_text "Long swipes" "$ui" && has_text "Double tap and long press" "$ui" && has_text "Info lines" "$ui" &&
  ok "unbound surfaces are grouped by kind" || ko "unbound surfaces are grouped by kind"
scroll_to "Long swipe up" && ok "an unbound long swipe is offered" || ko "an unbound long swipe is offered"
scroll_to "Double tap" && ok "double tap is offered" || ko "double tap is offered"

# 5. Rebinding a gesture through the picker — double tap starts unbound, so it is reached from the
# add screen just opened, not the bound list above it.
find_and_tap "Double tap" || ko "opening the picker"
ui=$(dump)
has_text "Nothing" "$ui" && contains_text "Open an app" "$ui" && ok "the picker offers nothing and apps" || ko "the picker offers its choices"
scroll_to "Torch" && ok "the picker lists the built-in actions" || ko "the picker lists the built-in actions"
scroll_to "Lock the screen" >/dev/null
grep -qi 'accessibility service' <<<"$(dump)" && ok "the picker says which actions need the service" || ko "the picker says which actions need the service"
find_and_tap "System settings" || ko "choosing an action"
wait_s 1
grep -q '"double_tap"' <<<"$(settings_json)" && ok "the new binding is persisted" || ko "the new binding is persisted"
scroll_to "Swipe up" && ok "choosing takes you back to the gesture list" || ko "choosing takes you back to the gesture list"

# 6. The rebound gesture works on the home screen.
home
double_tap $MIDX $((H / 8))
[[ "$(top)" == *"com.android.settings"* ]] && ok "double tap now opens the system settings" || ko "double tap now opens the system settings (top: $(top))"

# 7. Unbinding it again.
open_gesture_settings || ko "reopening the gesture settings"
find_and_tap "Double tap" || ko "reopening the picker"
find_and_tap "Nothing" || ko "choosing nothing"
wait_s 1
grep -A2 '"double_tap"' <<<"$(settings_json)" | grep -q '"none"' && ok "unbinding is written down" || ko "unbinding is written down"
home
double_tap $MIDX $((H / 8))
[[ "$(top)" == *"$PKG"* ]] && ok "the unbound double tap does nothing" || ko "the unbound double tap does nothing"

# 8. A long swipe with no binding of its own falls back to the short one.
home
swipe $MIDX $MIDY $((MIDX - 500)) $MIDY 400
[[ "$(top)" == "$left" ]] && ok "a long swipe left does what a short one does" || ko "a long swipe left falls back (top: $(top))"

# 9. A swipe that starts on an app row is a swipe, not a tap on the row.
home
ui=$(dump)
row=$(grep -o "<node[^>]*class=\"android.widget.TextView\"[^>]*package=\"$PKG\"[^>]*>" <<<"$ui" | grep -o 'text="[^"]\+"' | sed 's/text="\(.*\)"/\1/' | grep -vE '^[0-9]{1,2}:[0-9]{2}|%|day' | head -1)
b=$(bounds_of "$row" "$ui")
if [ -n "$b" ]; then
  read -r x1 y1 x2 y2 <<<"$b"
  cy=$(( (10#$y1 + 10#$y2) / 2 ))
  swipe $MIDX "$cy" $((MIDX - 250)) "$cy"
  [[ "$(top)" == "$left" ]] && ok "a swipe across the row '$row' swipes instead of opening it" || ko "a swipe across a row swipes (top: $(top))"
else
  ko "no app row found to swipe across"
fi

# 10. Tapping an info line runs its own binding.
home
ui=$(dump)
clock=$(grep -o 'text="[0-9]\{1,2\}:[0-9]\{2\}[^"]*"' <<<"$ui" | head -1 | sed 's/text="\(.*\)"/\1/')
if [ -n "$clock" ]; then
  b=$(bounds_of "$clock" "$ui"); sh input tap $(centre "$b") >/dev/null; wait_s 3
  [[ "$(top)" != *"$PKG"* ]] && ok "tapping the clock opened $(top)" || ko "tapping the clock opens the alarms"
else
  ko "no clock on screen to tap"
fi

# 11. The two toggles.
open_gesture_settings || ko "reopening the gesture settings for the toggles"
find_and_tap "Take over the side edges" || ko "the edge toggle"
wait_s 1
grep -q '"edgeExclusion": "both"' <<<"$(settings_json)" && ok "taking over the edges is persisted" || ko "taking over the edges is persisted"
find_and_tap "Buzz on gestures" || ko "the haptics toggle"
wait_s 1
grep -q '"haptics": false' <<<"$(settings_json)" && ok "turning off the buzz is persisted" || ko "turning off the buzz is persisted"

[ "$(crashes)" = "0" ] && ok "no crashes during the run" || ko "$(crashes) crash(es) logged"
echo "== $pass passed, $fail failed =="
[ "$fail" = "0" ]
