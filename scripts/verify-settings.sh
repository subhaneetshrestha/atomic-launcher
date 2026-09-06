#!/usr/bin/env bash
# HARNESS_V2 — Phase 2 acceptance checks against a booted emulator or device (debug build).
#   scripts/verify-settings.sh <adb serial>
# Drives the first-run setup, the settings screens and the app menu through uiautomator, and
# reads the persisted document back with run-as. Backup export/import needs the document picker
# and stays a manual check.
set -u
SERIAL=${1:?usage: verify-settings.sh <serial>}
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
# Text matching is case-insensitive (older platform themes upper-case button labels) and a row
# whose text continues with a detail line ("Label&#10;detail") counts as "Label".
has_text() { grep -qiE "text=\"$1(\"|&#10;)" <<<"$2"; }
node_with_text() { grep -oiE "<node[^>]*text=\"$1(\"|&#10;)[^>]*>" <<<"$2" | head -1; }
bounds_of() { node_with_text "$1" "$2" | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1 | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/'; }
centre() { read -r x1 y1 x2 y2 <<<"$1"; echo "$(( (10#$x1 + 10#$x2) / 2 )) $(( (10#$y1 + 10#$y2) / 2 ))"; }
tap_text() { # tap the centre of the first node with this text; returns 1 when absent
  local b; b=$(bounds_of "$1" "$(dump)"); [ -z "$b" ] && return 1
  sh input tap $(centre "$b") >/dev/null; wait_s 2
}
long_press_text() { # hold on the node with this text; returns 1 when absent
  local b c; b=$(bounds_of "$1" "$(dump)"); [ -z "$b" ] && return 1
  c=$(centre "$b"); sh input swipe $c $c 1200 >/dev/null; wait_s 2
}
# Text of every node of a class, independent of attribute order inside the node.
texts_of_class() { grep -o "<node[^>]*class=\"$1\"[^>]*>" <<<"$2" | grep -o 'text="[^"]*"' | sed 's/text="\(.*\)"/\1/' | grep -v '^$'; }
# Empties a focused text field: cursor to the end, then one delete per possible character.
# (--longpress DEL is not a delete-all on every API level; a label is at most 40 characters.)
clear_field() { sh input keyevent KEYCODE_MOVE_END >/dev/null; local dels=""; for _ in $(seq 1 44); do dels="$dels KEYCODE_DEL"; done; sh input keyevent $dels >/dev/null 2>&1 || for _ in $(seq 1 44); do sh input keyevent KEYCODE_DEL >/dev/null; done; }
settings_json() { sh run-as "$PKG" cat files/settings.json; }
home_rows() { grep -o "<node[^>]*package=\"$PKG\"[^>]*>" <<<"$1" | grep 'class="android.widget.TextView"' | grep -o 'text="[^"]\+"' | sed 's/text="\(.*\)"/\1/'; }
crash_count() { $ADB logcat -d -b crash 2>/dev/null | tr -d '\r' | grep -c "$PKG" || true; }
go_home() { sh am start -W -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1; wait_s 2; }

api=$(sh getprop ro.build.version.sdk)
read -r width height < <(sh wm size | awk -F'[ x]' '/Physical/{print $3, $4}')
echo "== $SERIAL: API $api, ${width}x${height} =="

$ADB install -r -t "$APK" >/dev/null 2>&1 && ok "install $APK" || ko "install $APK"
sh pm clear "$PKG" >/dev/null
if [ "$api" -ge 29 ]; then sh cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" >/dev/null; else sh cmd package set-home-activity "$CMP" >/dev/null; fi
$ADB logcat -c >/dev/null 2>&1
go_home; wait_s 2

# 1. First run: the setup appears, walks through apps → theme → default, and is not offered again.
ui=$(dump)
has_text "Pick the apps for your home screen" "$ui" && ok "setup offered on first run" || ko "setup offered on first run"
tap_text "Next" || ko "Next button on the apps step"
ui=$(dump); has_text "Pick a look" "$ui" && ok "setup step 2: theme" || ko "setup step 2: theme (screen: $(home_rows "$ui" | head -3 | tr '\n' ','))"
tap_text "Paper" || ko "Paper theme row"
wait_s 1; tap_text "Next" || ko "Next button on the theme step"
ui=$(dump); has_text "Make atomic your home app" "$ui" && ok "setup step 3: default home" || ko "setup step 3: default home"
has_text "atomic is your home app" "$ui" && ok "setup recognises we are already the default" || ko "setup recognises we are already the default"
tap_text "Done" || ko "Done button"
json=$(settings_json)
grep -q '"setupDone": true' <<<"$json" && ok "setup completion is persisted" || ko "setup completion is persisted"
grep -q '"id": "paper"' <<<"$json" && ok "theme choice is persisted" || ko "theme choice is persisted"
sh am force-stop "$PKG" >/dev/null; wait_s 1; go_home
has_text "Pick the apps for your home screen" "$(dump)" && ko "setup is not offered again" || ok "setup is not offered again"

# 2. Home shows info lines (paper: block at the bottom) and rows.
ui=$(dump); rows=$(home_rows "$ui")
grep -qE '^[0-9]{1,2}:[0-9]{2}' <<<"$rows" && ok "clock line shown" || ko "clock line shown (rows: $(tr '\n' ',' <<<"$rows"))"
weekday=$(sh date +%A)
grep -q "$weekday" <<<"$rows" && ok "date line shows today ($weekday)" || ko "date line shows today ($weekday)"
grep -qE '^[0-9]{1,3}%' <<<"$rows" && ok "battery line shown" || ko "battery line shown"

# 3. Long-press on empty space opens Settings.
sh input swipe $((width / 2)) $((height / 10)) $((width / 2)) $((height / 10)) 1500 >/dev/null; wait_s 2
ui=$(dump)
has_text "Home apps" "$ui" && has_text "Settings" "$ui" && ok "long-press on empty space opens Settings" || ko "long-press on empty space opens Settings"

# 4. Theme and appearance screens persist their choice.
tap_text "Theme" || ko "Theme row"
tap_text "Ink" || ko "Ink row"
wait_s 1; grep -q '"id": "ink"' <<<"$(settings_json)" && ok "theme picker persists ink" || ko "theme picker persists ink"
sh input keyevent KEYCODE_BACK >/dev/null; wait_s 1
tap_text "Appearance" || ko "Appearance row"
tap_text "Dark" || ko "Dark row"
wait_s 1; grep -q '"nightMode": "dark"' <<<"$(settings_json)" && ok "appearance persists dark" || ko "appearance persists dark"
tap_text "Follow the system" || ko "Follow the system row"
wait_s 1; sh input keyevent KEYCODE_BACK >/dev/null; wait_s 1

# 5. Home apps: removing a row pins the alphabetical six and drops one.
tap_text "Home apps" || ko "Home apps row"
ui=$(dump); has_text "6 of 16 on the home screen. Tap to add or remove, hold to reorder." "$ui" && ok "home apps summary shows 6 of 16" || ko "home apps summary shows 6 of 16"
first=$(texts_of_class "android.widget.CheckedTextView" "$ui" | head -1)
info "first home app row: '$first'"
{ [ -n "$first" ] && tap_text "$first"; } || ko "tap first home app row"
ui=$(dump); has_text "5 of 16 on the home screen. Tap to add or remove, hold to reorder." "$ui" && ok "removing a row leaves 5 of 16" || ko "removing a row leaves 5 of 16"
json=$(settings_json); n=$(grep -c '"component":' <<<"$(sed -n '/"entries"/,/\]/p' <<<"$json")")
[ "$n" = "5" ] && ok "five explicit entries persisted" || ko "five explicit entries persisted (found $n)"
tap_text "$first" || ko "re-add the row"
has_text "6 of 16 on the home screen. Tap to add or remove, hold to reorder." "$(dump)" && ok "re-adding restores 6 of 16" || ko "re-adding restores 6 of 16"
sh input keyevent KEYCODE_HOME >/dev/null; wait_s 2

# 6. App menu: rename, reset, hide, unhide.
ui=$(dump); row=$(home_rows "$ui" | grep -vE '^[0-9]{1,2}:[0-9]{2}|%|'"$weekday" | tail -1)
info "menu target row: '$row'"
if [ -z "$row" ]; then ko "no app row found for the menu checks"; else
long_press_text "$row" || ko "long-press on the row"
ui=$(dump); has_text "Rename" "$ui" && has_text "App info" "$ui" && ok "long-press on a row opens the app menu" || ko "long-press on a row opens the app menu"
tap_text "Rename" || ko "Rename item"
clear_field; sh input text "Renamed" >/dev/null; wait_s 1
tap_text "OK" || ko "OK button of the rename dialog"
has_text "Renamed" "$(dump)" && ok "renamed row shows the new label" || ko "renamed row shows the new label"
grep -q '"label": "Renamed"' <<<"$(settings_json)" && ok "rename persisted" || ko "rename persisted"
long_press_text "Renamed" || ko "long-press on the renamed row"
tap_text "Rename" || ko "Rename item (second time)"
tap_text "Use original name" || ko "Use original name button"
has_text "$row" "$(dump)" && ok "rename reset restores the system label" || ko "rename reset restores the system label"
long_press_text "$row" || ko "long-press for hide"
tap_text "Hide from search" || ko "Hide item"
grep -A3 '"hidden": \[' <<<"$(settings_json)" | grep -q '"component"' && ok "hide persisted" || ko "hide persisted"
has_text "$row" "$(dump)" && ok "a hidden app stays on the home list" || ko "a hidden app stays on the home list"
long_press_text "$row" || ko "long-press for unhide"
tap_text "Show in search" || ko "Show in search item"
grep -q '"hidden": \[\]' <<<"$(settings_json)" && ok "unhide persisted" || ko "unhide persisted"
fi

# 7. Persistence across a process restart.
sh am force-stop "$PKG" >/dev/null; wait_s 1; go_home
n=$(grep -c '"component":' <<<"$(sed -n '/"entries"/,/\]/p' <<<"$(settings_json)")")
[ "$n" = "6" ] && has_text "$row" "$(dump)" && ok "explicit entries survive a restart" || ko "explicit entries survive a restart (entries $n)"

# 8. About screen.
sh input swipe $((width / 2)) $((height / 10)) $((width / 2)) $((height / 10)) 1500 >/dev/null; wait_s 2
tap_text "About" || ko "About row"
ui=$(dump); has_text "No crash recorded" "$ui" && ok "About reports no crash" || ko "About reports no crash"
grep -q 'text="atomic 0.1.0-debug (1)"' <<<"$ui" && ok "About shows the version" || ko "About shows the version"
sh input keyevent KEYCODE_HOME >/dev/null; wait_s 1
info "backup export/import go through the document picker: verify manually"

[ "$(crash_count)" = "0" ] && ok "no crashes during the run" || ko "$(crash_count) crash(es) logged"
echo "== $pass passed, $fail failed =="
[ "$fail" = "0" ]
