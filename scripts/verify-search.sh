#!/usr/bin/env bash
# HARNESS_V2 — Phase 4 acceptance checks against a booted emulator or device (debug build).
#   scripts/verify-search.sh <adb serial>
# Opens the search by gesture, types, and reads back what narrowed and what opened; then hides and
# renames an app through its menu and checks how search treats it.
set -u
SERIAL=${1:?usage: verify-search.sh <serial>}
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
contains_text() { grep -qiE "text=\"[^\"]*$1" <<<"$2"; }
bounds_of() { grep -oiE "<node[^>]*text=\"$1(\"|&#10;)[^>]*>" <<<"$2" | head -1 | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1 | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/'; }
centre() { read -r x1 y1 x2 y2 <<<"$1"; echo "$(( (10#$x1 + 10#$x2) / 2 )) $(( (10#$y1 + 10#$y2) / 2 ))"; }
tap_text() { local b; b=$(bounds_of "$1" "$(dump)"); [ -z "$b" ] && return 1; sh input tap $(centre "$b") >/dev/null; wait_s 2; }
top() {
  local a
  a=$(sh dumpsys activity activities | grep -m1 -o 'topResumedActivity=ActivityRecord{[^}]*}')
  [ -z "$a" ] && a=$(sh dumpsys activity activities | grep -m1 -o 'mResumedActivity: ActivityRecord{[^}]*}')
  sed -E 's/.* u0 ([^ }]+).*/\1/' <<<"$a"
}
home() { sh am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1; wait_s 2; }
crashes() { $ADB logcat -d -b crash 2>/dev/null | tr -d '\r' | grep -c "$PKG" || true; }
swipe() { sh input swipe "$1" "$2" "$3" "$4" "${5:-250}" >/dev/null; wait_s 2; }
hold_at() { sh input swipe "$1" "$2" "$1" "$2" 1200 >/dev/null; wait_s 2; }
keyboard_shown() { sh dumpsys input_method | grep -o 'mInputShown=[a-z]*' | head -1 | cut -d= -f2; }
open_search() { home; swipe $MIDX $((H - H / 4)) $MIDX $((H / 4)) 250; }
type_text() { sh input text "$1" >/dev/null; wait_s 2; }
# The rows of the search list, which are our TextViews inside the overlay.
result_rows() { grep -o "<node[^>]*class=\"android.widget.TextView\"[^>]*package=\"$PKG\"[^>]*>" <<<"$1" | grep -o 'text="[^"]\+"' | sed 's/text="\(.*\)"/\1/'; }

api=$(sh getprop ro.build.version.sdk)
read -r W H < <(sh wm size | awk -F'[ x]' '/Physical/{print $3, $4}')
MIDX=$((W / 2))
echo "== $SERIAL: API $api, ${W}x${H} =="

$ADB install -r -t "$APK" >/dev/null 2>&1 && ok "install $APK" || ko "install $APK"
sh pm clear "$PKG" >/dev/null
if [ "$api" -ge 29 ]; then sh cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" >/dev/null
else sh cmd package set-home-activity "$CMP" >/dev/null; fi
$ADB logcat -c >/dev/null 2>&1
home; tap_text "Skip" >/dev/null 2>&1 || true; wait_s 1

# 1. Swipe up opens the search, with the field and the keyboard.
open_search
ui=$(dump)
contains_text "Search apps" "$ui" && ok "swipe up opens the search field" || ko "swipe up opens the search field"
shown=$(keyboard_shown)
if [ -n "$shown" ]; then
  [ "$shown" = "true" ] && ok "the keyboard comes up with it" || ko "the keyboard comes up with it (mInputShown=$shown)"
else
  info "this Android does not report whether the keyboard is up"
fi
rows=$(result_rows "$ui" | grep -v 'Search apps' | wc -l)
[ "$rows" -gt 3 ] && ok "every app is listed when nothing is typed ($rows rows)" || ko "every app is listed when nothing is typed ($rows rows)"

# 2. Typing narrows it. Two letters still leave several apps, so nothing opens by itself yet.
type_text "ca"
ui=$(dump)
has_text "Camera" "$ui" && ok "typing ca keeps the Camera" || ko "typing ca keeps the Camera"
has_text "Chrome" "$ui" && ko "and drops what has no a in it" || ok "and drops what has no a in it"

# 3. One match left opens itself, without a tap.
type_text "m"
wait_s 2
launched=$(top)
[ -n "$launched" ] && [[ "$launched" != *"$PKG"* ]] && ok "the last match opens by itself ($launched)" || ko "the last match opens by itself (top: '$launched')"

# 4. Enter opens the best match when several are left.
open_search
type_text "ca"
sh input keyevent KEYCODE_ENTER >/dev/null; wait_s 3
entered=$(top)
[ -n "$entered" ] && [[ "$entered" != *"$PKG"* ]] && ok "enter opens the best match ($entered)" || ko "enter opens the best match (top: '$entered')"

# 5. Back closes the search: the first press puts the keyboard away, the second closes the search,
# which is how Android hands the key over.
open_search
sh input keyevent KEYCODE_BACK >/dev/null; wait_s 1
sh input keyevent KEYCODE_BACK >/dev/null; wait_s 2
ui=$(dump)
contains_text "Search apps" "$ui" && ko "back closes the search" || ok "back closes the search"
[[ "$(top)" == *"$PKG"* ]] && ok "and stays on the home screen" || ko "and stays on the home screen"

# 6. Gestures stand down while the search is up.
open_search
swipe $MIDX $((H / 2)) $((MIDX - 300)) $((H / 2)) 250
[[ "$(top)" == *"$PKG"* ]] && ok "a swipe inside the search does not fire a gesture" || ko "a swipe inside the search does not fire a gesture (top: $(top))"
sh input keyevent KEYCODE_BACK >/dev/null; wait_s 1

# 7. The list without the keyboard, through the drawer action.
home
hold_at $MIDX $((H / 8))
tap_text "Gestures" || ko "opening the gesture settings"
for i in 1 2 3 4 5 6; do has_text "Double tap" "$(dump)" && break; sh input swipe $MIDX $((H * 3 / 4)) $MIDX $((H / 3)) 200 >/dev/null; wait_s 1; done
tap_text "Double tap" || ko "opening the picker"
for i in 1 2 3 4 5 6; do has_text "All apps" "$(dump)" && break; sh input swipe $MIDX $((H * 3 / 4)) $MIDX $((H / 3)) 200 >/dev/null; wait_s 1; done
tap_text "All apps" && ok "the drawer action can be chosen now that it exists" || ko "the drawer action can be chosen"
home
sh "input tap $MIDX $((H / 8)); input tap $MIDX $((H / 8))" >/dev/null; wait_s 3
ui=$(dump)
contains_text "Search apps" "$ui" && ok "the drawer opens the same list" || ko "the drawer opens the same list"
shown=$(keyboard_shown)
[ -z "$shown" ] || [ "$shown" = "false" ] && ok "without the keyboard in the way" || ko "without the keyboard in the way (mInputShown=$shown)"
sh input keyevent KEYCODE_BACK >/dev/null; wait_s 1

# 8. A hidden app keeps out of the way until its whole name is typed. One letter matches several
# apps, so the list stays up and can be read; the whole name matches only the hidden app, so it
# opens by itself, which is the proof it was found.
home
ui=$(dump)
row=$(result_rows "$ui" | grep -vE '^[0-9]{1,2}:[0-9]{2}|%|day|Set atomic' | head -1)
info "working with the app '$row'"
b=$(bounds_of "$row" "$ui"); read -r x1 y1 x2 y2 <<<"$b"
hold_at $(( (10#$x1 + 10#$x2) / 2 )) $(( (10#$y1 + 10#$y2) / 2 ))
tap_text "Hide from search" || ko "hiding the app"
open_search
type_text "$(cut -c1-1 <<<"$row")"
has_text "$row" "$(dump)" && ko "a hidden app stays out of a part-way match" || ok "a hidden app stays out of a part-way match"
sh input keyevent KEYCODE_BACK >/dev/null; sh input keyevent KEYCODE_BACK >/dev/null; wait_s 1
open_search
type_text "$row"
wait_s 2
opened=$(top)
[ -n "$opened" ] && [[ "$opened" != *"$PKG"* ]] && ok "but its whole name still finds it ($opened)" || ko "but its whole name still finds it (top: '$opened')"

# 9. A renamed app answers to both names; each match is the only one, so each opens by itself.
home
b=$(bounds_of "$row" "$(dump)"); read -r x1 y1 x2 y2 <<<"$b"
hold_at $(( (10#$x1 + 10#$x2) / 2 )) $(( (10#$y1 + 10#$y2) / 2 ))
tap_text "Show in search" || ko "unhiding the app"
b=$(bounds_of "$row" "$(dump)"); read -r x1 y1 x2 y2 <<<"$b"
hold_at $(( (10#$x1 + 10#$x2) / 2 )) $(( (10#$y1 + 10#$y2) / 2 ))
tap_text "Rename" || ko "opening the rename dialog"
sh input keyevent KEYCODE_MOVE_END >/dev/null
for i in $(seq 1 44); do sh input keyevent KEYCODE_DEL >/dev/null; done
type_text "Zebra"
tap_text "OK" || ko "saving the new name"
open_search
type_text "zebra"
wait_s 2
opened=$(top)
[ -n "$opened" ] && [[ "$opened" != *"$PKG"* ]] && ok "the new name finds it ($opened)" || ko "the new name finds it (top: '$opened')"
home
open_search
type_text "$row"
wait_s 2
opened=$(top)
[ -n "$opened" ] && [[ "$opened" != *"$PKG"* ]] && ok "and so does the name it came with" || ko "and so does the name it came with (top: '$opened')"
home

[ "$(crashes)" = "0" ] && ok "no crashes during the run" || ko "$(crashes) crash(es) logged"
echo "== $pass passed, $fail failed =="
[ "$fail" = "0" ]
