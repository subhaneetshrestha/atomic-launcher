#!/usr/bin/env bash
# HARNESS_V2 — Phase 7 acceptance checks against a booted emulator or device (debug build).
#   scripts/verify-themes.sh <adb serial>
# Picks the built-in themes, edits one into the user's own, and then takes themes in the three ways
# they arrive: a file, a share, and a link. Colours are read off a screenshot, because that is the
# only place a theme is visible.
set -u
SERIAL=${1:?usage: verify-themes.sh <serial>}
SDK=${ANDROID_HOME:-/opt/android-sdk}
ADB="$SDK/platform-tools/adb -s $SERIAL"
PKG=io.github.subhaneetshrestha.atomic.debug
CMP=$PKG/io.github.subhaneetshrestha.atomic.home.HomeActivity
IMPORT=$PKG/io.github.subhaneetshrestha.atomic.share.ImportActivity
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
scroll_to_tap() {
  local i
  for i in 1 2 3 4 5 6 7 8; do
    tap_text "$1" && return 0
    sh input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 4)) 300 >/dev/null; wait_s 1
  done
  return 1
}
# The device shell would read a # as the start of a comment, and a colour is mostly #.
type_text() { $ADB shell "input text '$1'" >/dev/null 2>&1; wait_s 1; }
# One key press per call: several keycodes in one `input keyevent` are accepted and then not all
# delivered. The cursor sits at the end of a field, so backspace is the only direction needed.
clear_field() {
  local i
  for i in $(seq 1 "${1:-16}"); do sh input keyevent KEYCODE_DEL >/dev/null; done
}
home() { sh am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1; wait_s 2; }
hold_at() { sh input swipe "$1" "$2" "$1" "$2" 1200 >/dev/null; wait_s 2; }
crashes() { $ADB logcat -d -b crash 2>/dev/null | tr -d '\r' | grep -c "$PKG" || true; }
settings_json() { sh run-as "$PKG" cat files/settings.json | tr -d ' \n'; }
# The long press that opens settings has to land on nothing. Where that is depends on the theme:
# Terminal puts its names at the top, Paper at the bottom, so the spot is worked out from the screen.
empty_spot() {
  dump > "$TMP/ui.xml"
  python3 - "$TMP/ui.xml" "$W" "$H" <<'SPOT'
import re, sys
xml = open(sys.argv[1], encoding='utf-8', errors='replace').read()
w, h = int(sys.argv[2]), int(sys.argv[3])
# Only what is written on the screen blocks the spot. The containers behind it all but fill the
# window, so counting those would leave nowhere at all.
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
open_theme_settings() { open_settings; scroll_to_tap "Theme"; }
top() { sh dumpsys activity activities | grep -m1 -oE 'topResumedActivity=ActivityRecord\{[^}]*\}' | sed -E 's/.* u0 ([^ }]+).*/\1/'; }

# The screen as "min median max" brightness over a band of rows, and the colour at its middle.
shot() { $ADB exec-out screencap > "$TMP/shot.raw" 2>/dev/null; }
band() {
  python3 - "$TMP/shot.raw" "$1" "${2:-$1}" <<'SCAN'
import sys, struct
path, first, last = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
data = open(path, 'rb').read()
if len(data) < 16:
    print("0 0 0 0 0 0"); raise SystemExit
w, h, fmt = struct.unpack('<III', data[:12])
header = 12 if w * h * 4 + 12 == len(data) else 16
if w == 0 or w * h * 4 + header != len(data):
    print("0 0 0 0 0 0"); raise SystemExit
values, reds, greens, blues = [], [], [], []
for y in range(max(0, min(first, h - 1)), max(0, min(last, h - 1)) + 1):
    row = header + y * w * 4
    for x in range(0, w, 2):
        o = row + x * 4
        values.append((data[o] * 30 + data[o + 1] * 59 + data[o + 2] * 11) // 100)
        reds.append(data[o]); greens.append(data[o + 1]); blues.append(data[o + 2])
values.sort(); reds.sort(); greens.sort(); blues.sort()
half = len(values) // 2
print(values[0], values[half], values[-1], reds[half], greens[half], blues[half])
SCAN
}
band_of() { shot; band "$1" "${2:-$1}"; }
median() { read -r lo mid hi r g b <<<"$1"; echo "$mid"; }
rgb() { read -r lo mid hi r g b <<<"$1"; echo "$r $g $b"; }
darkest() { read -r lo mid hi r g b <<<"$1"; echo "$lo"; }

# A theme document, and the link that carries it.
write_theme() { # <file> <name> <background> <text>
  cat > "$1" <<JSON
{
  "schema": 1,
  "meta": { "id": "harness", "name": "$2", "author": "the harness", "license": "CC0-1.0",
            "description": "A theme made by scripts/verify-themes.sh" },
  "colors": { "background": "$3", "text": "$4", "textSecondary": "$4", "accent": "$4" },
  "typography": { "family": "monospace", "weight": 400, "sizes": { "homeSp": 26 } },
  "layout": { "hAlign": "center", "vAlign": "center" }
}
JSON
}
link_for() {
  python3 - "$1" <<'PACK'
import base64, sys, zlib
data = open(sys.argv[1], 'rb').read()
print("atomic://theme?d=" + base64.urlsafe_b64encode(zlib.compress(data, 9)).decode().rstrip("="))
PACK
}
open_link() { sh am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d "$1" >/dev/null 2>&1; wait_s 3; }
# The redirect has to survive two shells, so the whole command goes over as one string.
put_theme_in_app() {
  $ADB push "$1" /data/local/tmp/theme.json >/dev/null 2>&1
  $ADB shell "run-as $PKG sh -c 'cat /data/local/tmp/theme.json > files/$2'" >/dev/null 2>&1
  sh run-as "$PKG" ls files | grep -q "$2"
}

api=$(sh getprop ro.build.version.sdk)
read -r W H < <(sh wm size | awk -F'[ x]' '/Physical/{print $3, $4}')
echo "== $SERIAL: API $api, ${W}x${H} =="

$ADB install -r -t "$APK" >/dev/null 2>&1 && ok "install $APK" || ko "install $APK"
sh pm clear "$PKG" >/dev/null
if [ "$api" -ge 29 ]; then sh cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" >/dev/null
else sh cmd package set-home-activity "$CMP" >/dev/null; fi
$ADB logcat -c >/dev/null 2>&1
home; tap_text "Skip" >/dev/null 2>&1 || true; wait_s 1

# 1. The four built-ins are offered, and each one is drawn the way it says it is.
open_theme_settings || ko "opening the theme settings"
ui=$(dump)
has_text "Ink" "$ui" && has_text "Paper" "$ui" && has_text "Terminal" "$ui" && has_text "You" "$ui" &&
  ok "the four built-in themes are offered" || ko "the four built-in themes are offered"

open_theme_settings; tap_text "Paper" || ko "choosing Paper"
home
paper=$(band_of $((H / 3)) $((H / 2)))
[ "$(median "$paper")" -gt 180 ] && ok "Paper is light ($(rgb "$paper"))" || ko "Paper is light ($(rgb "$paper"))"
[ "$(darkest "$paper")" -lt 80 ] && ok "and its names are dark on it" || ko "and its names are dark on it ($paper)"

open_theme_settings; tap_text "Terminal" || ko "choosing Terminal"
home
terminal=$(band_of $((H / 8)) $((H / 4)))
read -r r g b <<<"$(rgb "$terminal")"
[ "$g" -le 20 ] && [ "$(median "$terminal")" -lt 30 ] && ok "Terminal is nearly black ($r $g $b)" ||
  ko "Terminal is nearly black ($r $g $b)"

open_theme_settings; tap_text "Ink" || ko "choosing Ink"
grep -q '"id":"ink"' <<<"$(settings_json)" && ok "the choice is written into the document" || ko "the choice is written into the document"

# 2. The Material You theme resolves on Android 12 and later, and falls back below it.
open_theme_settings; tap_text "You" || ko "choosing You"
home
you=$(band_of $((H / 3)) $((H / 2)))
read -r r g b <<<"$(rgb "$you")"
if [ "$api" -ge 31 ]; then
  [ $(( r + g + b )) -gt 0 ] && ok "You takes its colours from the device ($r $g $b)" ||
    ko "You takes its colours from the device ($r $g $b)"
else
  [ $(( r + g + b )) -eq 0 ] && ok "You falls back to black where there are no tokens ($r $g $b)" ||
    ko "You falls back to black where there are no tokens ($r $g $b)"
fi

# 3. Editing a built-in makes it the user's own, and the edit shows on the home screen.
open_theme_settings || ko "reopening the theme settings"
tap_text "Ink" || ko "going back to Ink to edit it"
grep -q '"id":"ink"' <<<"$(settings_json)" || ko "Ink is the theme being edited"
open_theme_settings || ko "reopening the theme settings"
scroll_to_tap "Edit this theme" || ko "opening the editor"
ui=$(dump)
has_text "Colours" "$ui" && has_text "Background" "$ui" && ok "the editor lists what a theme holds" ||
  ko "the editor lists what a theme holds"
tap_text "Background" || ko "opening the background colour"
clear_field 40
type_text "#FF102030"
tap_text "OK" || ko "saving the colour"
wait_s 2
grep -q '"id":"custom"' <<<"$(settings_json)" && ok "editing a built-in makes the theme your own" ||
  ko "editing a built-in makes the theme your own"
home
edited=$(band_of $((H / 8)) $((H / 4)))
read -r r g b <<<"$(rgb "$edited")"
[ "$b" -gt "$r" ] && [ "$b" -gt 30 ] && ok "and the home screen is painted with it ($r $g $b)" ||
  ko "and the home screen is painted with it ($r $g $b)"

# 4. A size typed with one digit too many is pulled into range as it is typed.
open_theme_settings; scroll_to_tap "Edit this theme" || ko "reopening the editor"
scroll_to_tap "App names" || ko "opening the name size"
clear_field 8
type_text "999"
tap_text "OK" || ko "saving the size"
wait_s 2
grep -q '"homeSp":64' <<<"$(settings_json)" && ok "a size out of range is pulled into it" ||
  ko "a size out of range is pulled into it ($(settings_json | grep -o '"homeSp":[0-9.]*'))"

# 5. A theme arriving as a file: shown, explained, and applied only when told.
write_theme "$TMP/harness.atomictheme" "Harness Amber" "#FF241B00" "#FFFFC107"
put_theme_in_app "$TMP/harness.atomictheme" "incoming.atomictheme"
sh am start -a android.intent.action.VIEW -t application/json \
  -d "file:///data/data/$PKG/files/incoming.atomictheme" >/dev/null 2>&1
wait_s 3
ui=$(dump)
[[ "$(top)" == *ImportActivity* ]] && ok "a theme file opens the import screen" || ko "a theme file opens the import screen (top: $(top))"
contains_text "Harness Amber" "$ui" && ok "the theme says what it is called" || ko "the theme says what it is called"
contains_text "the harness" "$ui" && ok "and who wrote it" || ko "and who wrote it"
home
before=$(median "$(band_of $((H / 3)) $((H / 2)))")
sh am start -a android.intent.action.VIEW -t application/json \
  -d "file:///data/data/$PKG/files/incoming.atomictheme" >/dev/null 2>&1
wait_s 3
grep -q 'Harness Amber' <<<"$(settings_json)" && ko "nothing is applied by opening it" || ok "nothing is applied by opening it"
tap_text "Use this theme" || ko "applying the theme"
wait_s 2
grep -q '"name":"HarnessAmber"' <<<"$(settings_json)" && ok "applying it writes it into the document" ||
  ko "applying it writes it into the document"
home
applied=$(band_of $((H / 8)) $((H / 4)))
read -r r g b <<<"$(rgb "$applied")"
[ "$r" -gt "$b" ] && ok "and the home screen is amber-brown ($r $g $b)" || ko "and the home screen changed ($r $g $b)"

# 6. A theme arriving as a link, packed into it.
write_theme "$TMP/link.atomictheme" "Harness Green" "#FF04240A" "#FF7CFF9A"
open_link "$(link_for "$TMP/link.atomictheme")"
ui=$(dump)
contains_text "Harness Green" "$ui" && ok "a link carries the whole theme" || ko "a link carries the whole theme"
tap_text "Use this theme" || ko "applying the theme from the link"
wait_s 2
home
green=$(band_of $((H / 8)) $((H / 4)))
read -r r g b <<<"$(rgb "$green")"
[ "$g" -ge "$r" ] && ok "and it is applied ($r $g $b)" || ko "and it is applied ($r $g $b)"

# 7. A link that is not a theme, and a theme from a newer app, are refused in words.
open_link "atomic://theme?d=this-is-not-base64%21"
ui=$(dump)
contains_text "not readable|not a theme|damaged" "$ui" && ok "a link that carries nothing usable says so" ||
  ko "a link that carries nothing usable says so"
tap_text "Close" >/dev/null 2>&1
printf '{"schema":99,"meta":{"name":"From the future"}}' > "$TMP/future.atomictheme"
put_theme_in_app "$TMP/future.atomictheme" "future.atomictheme"
sh am start -a android.intent.action.VIEW -t application/json \
  -d "file:///data/data/$PKG/files/future.atomictheme" >/dev/null 2>&1
wait_s 3
ui=$(dump)
contains_text "newer version" "$ui" && ok "a theme from a newer atomic is refused, not guessed at" ||
  ko "a theme from a newer atomic is refused, not guessed at"
tap_text "Close" >/dev/null 2>&1

# 8. A theme that would download images says where from, before it is applied.
cat > "$TMP/collection.atomictheme" <<'JSON'
{ "meta": { "id": "walls", "name": "Harness Walls" },
  "background": { "mode": "collection", "collection": { "url": "https://images.example.org/list.txt" } } }
JSON
put_theme_in_app "$TMP/collection.atomictheme" "walls.atomictheme"
sh am start -a android.intent.action.VIEW -t application/json \
  -d "file:///data/data/$PKG/files/walls.atomictheme" >/dev/null 2>&1
wait_s 3
ui=$(dump)
contains_text "images.example.org" "$ui" && ok "a theme that downloads images names the host" ||
  ko "a theme that downloads images names the host"
tap_text "Not now" >/dev/null 2>&1

# 9. A theme carrying a plaintext address has it taken away, and says so.
cat > "$TMP/insecure.atomictheme" <<'JSON'
{ "meta": { "id": "insecure", "name": "Harness Insecure" },
  "background": { "mode": "collection", "collection": { "url": "http://images.example.org/list.txt" } } }
JSON
put_theme_in_app "$TMP/insecure.atomictheme" "insecure.atomictheme"
sh am start -a android.intent.action.VIEW -t application/json \
  -d "file:///data/data/$PKG/files/insecure.atomictheme" >/dev/null 2>&1
wait_s 3
ui=$(dump)
contains_text "corrected" "$ui" && ok "a plaintext address is corrected, and the correction is shown" ||
  ko "a plaintext address is corrected, and the correction is shown"
contains_text "images.example.org" "$ui" && ko "and no host is named, because nothing will be downloaded" ||
  ok "and no host is named, because nothing will be downloaded"
tap_text "Not now" >/dev/null 2>&1

# 10. Every built-in still lays out at twice the text size.
sh settings put system font_scale 2.0 >/dev/null; wait_s 2
for name in Ink Paper Terminal You; do
  open_theme_settings; tap_text "$name" >/dev/null 2>&1
  rows=0
  for attempt in 1 2 3; do
    home
    # A dump is one long line, so occurrences have to be counted, not lines.
    rows=$(grep -o "<node[^>]*class=\"android.widget.TextView\"[^>]*package=\"$PKG\"" <<<"$(dump)" | wc -l)
    [ "${rows:-0}" -ge 3 ] && break
    wait_s 2
  done
  [ "${rows:-0}" -ge 3 ] && ok "$name still lays out at 200 % text ($rows rows)" ||
    ko "$name still lays out at 200 % text ($rows rows)"
done
sh settings put system font_scale 1.0 >/dev/null; wait_s 2

[ "$(crashes)" = "0" ] && ok "no crashes during the run" || ko "$(crashes) crash(es) logged"
echo "== $pass passed, $fail failed =="
[ "$fail" = "0" ]
