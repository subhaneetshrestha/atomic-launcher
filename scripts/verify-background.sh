#!/usr/bin/env bash
# HARNESS_V2 — Phase 6 acceptance checks against a booted emulator or device (debug build).
#   scripts/verify-background.sh <adb serial>
# Drives the background settings, then looks at what is actually on the screen: the colour behind
# the names, a gradient that is not the same at both ends, the wallpaper showing through, and an
# image that flips the names from white to black.
#
# Colours are read from a screenshot, because nothing else can see them: uiautomator reports what
# a view says, never how it is painted.
set -u
SERIAL=${1:?usage: verify-background.sh <serial>}
SDK=${ANDROID_HOME:-/opt/android-sdk}
ADB="$SDK/platform-tools/adb -s $SERIAL"
PKG=io.github.subhaneetshrestha.atomic.debug
CMP=$PKG/io.github.subhaneetshrestha.atomic.home.HomeActivity
JOB=4201
APK=app/build/outputs/apk/debug/app-debug.apk
RAW=https://raw.githubusercontent.com/subhaneetshrestha/atomic-launcher/main/scripts/fixtures
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
  for i in 1 2 3 4 5 6; do
    tap_text "$1" && return 0
    sh input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 4)) 300 >/dev/null; wait_s 1
  done
  return 1
}
home() { sh am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1; wait_s 2; }
crashes() { $ADB logcat -d -b crash 2>/dev/null | tr -d '\r' | grep -c "$PKG" || true; }
hold_at() { sh input swipe "$1" "$2" "$1" "$2" 1200 >/dev/null; wait_s 2; }
settings_json() { sh run-as "$PKG" cat files/settings.json | tr -d ' \n'; }
state_json() { sh run-as "$PKG" cat files/background/state.json | tr -d ' \n'; }
open_background_settings() { home; hold_at $((W / 2)) $((H / 8)); scroll_to_tap "Background"; }

# What a band of the screen looks like, as "min median max" brightness. A screenshot is the only
# way to see a colour: uiautomator reports what a view says, never how it is painted. The raw
# screencap buffer is used rather than a PNG so that unpacking it needs nothing but python.
shot() { $ADB exec-out screencap > "$TMP/shot.raw" 2>/dev/null; }
band() {
  python3 - "$TMP/shot.raw" "$1" "${2:-$1}" <<'SCAN'
import sys, struct
path, first, last = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
data = open(path, 'rb').read()
if len(data) < 16:
    print("0 0 0"); raise SystemExit
w, h, fmt = struct.unpack('<III', data[:12])
header = 12 if w * h * 4 + 12 == len(data) else 16
if w == 0 or w * h * 4 + header != len(data):
    print("0 0 0"); raise SystemExit
values = []
for y in range(max(0, min(first, h - 1)), max(0, min(last, h - 1)) + 1):
    row = header + y * w * 4
    for x in range(0, w, 2):
        o = row + x * 4
        values.append((data[o] * 30 + data[o + 1] * 59 + data[o + 2] * 11) // 100)
values.sort()
print(values[0], values[len(values) // 2], values[-1])
SCAN
}
band_of() { shot; band "$1" "${2:-$1}"; }
median() { read -r lo mid hi <<<"$1"; echo "$mid"; }
darkest() { read -r lo mid hi <<<"$1"; echo "$lo"; }
# The window flags Android is actually drawing the home screen with.
window_flags() { sh dumpsys window windows | grep -A12 "$PKG/.*HomeActivity" | grep -m1 'fl=' | sed 's/^ *//'; }
meminfo() { sh dumpsys meminfo "$PKG" | sed -n '/App Summary/,/TOTAL SWAP/p' | grep -m1 "$1" | grep -oE '[0-9]+' | head -1; }

api=$(sh getprop ro.build.version.sdk)
read -r W H < <(sh wm size | awk -F'[ x]' '/Physical/{print $3, $4}')
echo "== $SERIAL: API $api, ${W}x${H} =="

$ADB install -r -t "$APK" >/dev/null 2>&1 && ok "install $APK" || ko "install $APK"
sh pm clear "$PKG" >/dev/null
if [ "$api" -ge 29 ]; then sh cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" >/dev/null
else sh cmd package set-home-activity "$CMP" >/dev/null; fi
$ADB logcat -c >/dev/null 2>&1
home; tap_text "Skip" >/dev/null 2>&1 || true; wait_s 1

# 1. Out of the box: the theme colour, nothing fetched, no job scheduled.
home
ink=$(band_of $((H / 3)) $((H / 2)))
[ "$(median "$ink")" -lt 20 ] && ok "the ink theme paints the screen black (min median max: $ink)" ||
  ko "the ink theme paints the screen black (min median max: $ink)"
sh dumpsys jobscheduler | grep -q "$PKG.*$JOB" && ko "nothing is scheduled before a collection is set" || ok "nothing is scheduled before a collection is set"

# 2. The settings screen offers the four backgrounds.
open_background_settings || ko "opening the background settings"
ui=$(dump)
has_text "Theme colour" "$ui" && has_text "Gradient" "$ui" && has_text "Your wallpaper" "$ui" &&
  has_text "Image collection" "$ui" && ok "all four backgrounds are offered" || ko "all four backgrounds are offered"

# 3. A gradient is painted, and it is not the same colour at both ends.
tap_text "Gradient" || ko "choosing the gradient"
ui=$(dump)
has_text "Starts with" "$ui" && ok "the gradient colours appear when it is chosen" || ko "the gradient colours appear"
home
topc=$(median "$(band_of $((H / 8)))")
botc=$(median "$(band_of $((H * 7 / 8)))")
diff=$(( topc - botc ))
[ "${diff#-}" -gt 8 ] && ok "the gradient runs from $topc at the top to $botc at the bottom" ||
  ko "the gradient runs top to bottom (top $topc, bottom $botc)"

# 4. The device wallpaper shows through the window rather than being copied into it.
open_background_settings || ko "reopening the background settings"
tap_text "Your wallpaper" || ko "choosing the wallpaper"
home
flags=$(window_flags)
[[ "$flags" == *SHOW_WALLPAPER* ]] && ok "the window asks for the wallpaper behind it ($flags)" || ko "the window asks for the wallpaper behind it ($flags)"
grep -q '"mode":"wallpaper"' <<<"$(settings_json)" && ok "and the choice is written down" || ko "and the choice is written down"

# 5. A collection address is checked, and the host is named before it is accepted.
open_background_settings || ko "reopening the background settings"
tap_text "Image collection" || ko "choosing the collection"
ui=$(dump)
has_text "Collection address" "$ui" && has_text "Change every" "$ui" && has_text "Only on wi‑fi" "$ui" &&
  ok "the collection settings appear" || ko "the collection settings appear"
tap_text "Collection address" || ko "opening the address dialog"
sh input text "http://example.org/list.txt" >/dev/null; wait_s 1
tap_text "OK" || ko "confirming the bad address"
wait_s 2
grep -q '"url":"http://' <<<"$(settings_json)" && ko "a plaintext address is refused" || ok "a plaintext address is refused"

tap_text "Collection address" >/dev/null 2>&1 || open_background_settings
tap_text "Collection address" >/dev/null 2>&1 || true
sh input keyevent KEYCODE_MOVE_END >/dev/null
for i in $(seq 1 40); do sh input keyevent KEYCODE_DEL >/dev/null; done
sh input text "$RAW/collection.txt" >/dev/null; wait_s 1
tap_text "OK" || ko "confirming the address"
ui=$(dump)
contains_text "raw.githubusercontent.com" "$ui" && ok "the host it would download from is named" || ko "the host it would download from is named"
tap_text "OK" || ko "accepting the host"
wait_s 2
grep -q 'raw.githubusercontent.com' <<<"$(settings_json)" && ok "the address is stored" || ko "the address is stored"

# 6. Setting a collection schedules the job; Android decides when it actually runs.
sh dumpsys jobscheduler | grep -q "$PKG" && ok "a periodic job is scheduled" || ko "a periodic job is scheduled"

# 7. The whole way through, if this machine's emulator can reach the internet: fetch, decode,
# show, and flip the names to black on the bright image.
online=no
sh ping -c 1 -W 2 raw.githubusercontent.com >/dev/null 2>&1 && online=yes
if [ "$online" = "no" ]; then
  info "no route to raw.githubusercontent.com from this emulator; skipping the fetch checks"
else
  home
  tap_text "Change now" >/dev/null 2>&1 || { open_background_settings; scroll_to_tap "Change now"; }
  for i in $(seq 1 20); do grep -q '"currentUrl"' <<<"$(state_json)" && break; wait_s 2; done
  state=$(state_json)
  grep -q 'currentUrl' <<<"$state" && ok "an image was fetched" || ko "an image was fetched ($state)"
  grep -q '"urls":\["https://raw' <<<"$state" && ok "the list was read and cached" || ko "the list was read and cached"
  home
  bg=$(median "$(band_of $((H / 8)) $((H / 5)))")
  info "the screen behind the names reads $bg"
  if [ "$bg" -lt 40 ]; then
    info "the dark image is showing; asking for the bright one"
    open_background_settings; scroll_to_tap "Change now"; wait_s 8; home
    bg=$(median "$(band_of $((H / 8)) $((H / 5)))")
  fi
  [ "$bg" -gt 40 ] && ok "the image is on screen ($bg)" || ko "the image is on screen ($bg)"
  # The names are drawn over it: on the bright image they have to be dark to be read at all.
  ui=$(dump)
  row=$(grep -o "<node[^>]*class=\"android.widget.TextView\"[^>]*package=\"$PKG\"[^>]*>" <<<"$ui" | grep -o 'text="[^"]\+"' | sed 's/text="\(.*\)"/\1/' | grep -vE '^[0-9]{1,2}:[0-9]{2}|%|Set atomic' | head -1)
  if [ -n "$row" ]; then
    b=$(bounds_of "$row" "$ui"); read -r x1 y1 x2 y2 <<<"$b"
    stats=$(band_of $(( 10#$y1 + 4 )) $(( 10#$y2 - 4 )))
    info "the band holding '$row' reads $stats (min median max)"
    [ "$(darkest "$stats")" -lt $(( $(median "$stats") - 40 )) ] &&
      ok "the names went dark over the bright image" ||
      ko "the names went dark over the bright image ($stats)"
  else
    info "no app row to read the text colour from"
  fi
  # 8. Clearing caches must not take away the wallpaper that is on screen.
  sh pm trim-caches 9999999999 >/dev/null 2>&1
  sh run-as "$PKG" ls files/background | grep -q current.img &&
    ok "trimming caches leaves the current image" || ko "trimming caches leaves the current image"
  # 9. What it costs. Recorded first, tightened once there are numbers from both emulators.
  graphics=$(meminfo "Graphics:")
  native=$(meminfo "Native Heap:")
  java=$(meminfo "Java Heap:")
  with_image=$(meminfo "TOTAL PSS:")
  budget=$(( W * H * 4 / 1024 ))
  info "with the image up: graphics ${graphics:-?} KB, native ${native:-?} KB, java ${java:-?} KB, total PSS ${with_image:-?} KB"
  info "one screenful of pixels is ${budget} KB; without a real GPU it is held in the native heap, not in graphics"
  # A decode that is never let go of would show here: the image is decoded again on every rotation,
  # so six of them would be carrying six screenfuls if anything held on to them.
  sh settings put system accelerometer_rotation 0 >/dev/null
  for i in 1 2 3 4 5 6; do sh settings put system user_rotation $((i % 2)) >/dev/null; wait_s 3; done
  sh settings put system user_rotation 0 >/dev/null; wait_s 2
  after_rotations=$(meminfo "TOTAL PSS:")
  drift=$(( after_rotations - with_image ))
  info "after six rotations, each one a fresh decode: ${after_rotations} KB (${drift} KB more)"
  [ "${drift#-}" -le "$budget" ] && ok "decoding the image again does not add up" ||
    ko "six decodes added ${drift} KB, about $(( drift / budget )) screenfuls"

  # 10. The image is the one thing here worth megabytes, so it is the first thing given back.
  native_before=$(meminfo "Native Heap:")
  pid=$(sh pidof "$PKG")
  sh am start -a android.settings.SETTINGS >/dev/null 2>&1; wait_s 3
  sh am send-trim-memory "$pid" COMPLETE >/dev/null 2>&1; wait_s 4
  native_after=$(meminfo "Native Heap:")
  given_back=$(( native_before - native_after ))
  info "a system short of memory got back ${given_back} KB of ${native_before} KB"
  [ "$given_back" -ge $(( budget / 2 )) ] && ok "the image is handed back when the system is short" ||
    ko "only ${given_back} KB was handed back of a ${budget} KB screenful"
  home
  wait_s 5
  again=$(median "$(band_of $((H / 8)) $((H / 5)))")
  [ "$again" -gt 40 ] && ok "and is decoded again when the launcher comes back ($again)" ||
    ko "and is decoded again when the launcher comes back ($again)"
fi

# 10. Turning the collection off puts the theme colour back and cancels the job.
open_background_settings || ko "reopening the background settings"
tap_text "Theme colour" || ko "choosing the theme colour again"
home
back=$(median "$(band_of $((H / 8)) $((H / 5)))")
[ "$back" -lt 20 ] && ok "the theme colour comes back ($back)" || ko "the theme colour comes back ($back)"
if [ "$online" = "yes" ]; then
  # Recorded rather than gated: this is a debug build with no R8 and StrictMode on, drawn by a
  # software renderer that keeps every pixel in the native heap. A release build on a phone puts
  # the image in graphics memory instead, and that number needs a phone to read.
  info "without an image the launcher is $(meminfo "TOTAL PSS:") KB of total PSS"
fi
sh dumpsys jobscheduler | grep -q "$PKG.*$JOB" && ko "the job is cancelled with it" || ok "the job is cancelled with it"

# 11. A restart shows the same thing it was showing, without fetching again.
sh am force-stop "$PKG" >/dev/null; wait_s 2
home
restarted=$(median "$(band_of $((H / 3)) $((H / 2)))")
[ "$restarted" -lt 20 ] && ok "a restart paints the same background ($restarted)" ||
  ko "a restart paints the same background ($restarted)"

[ "$(crashes)" = "0" ] && ok "no crashes during the run" || ko "$(crashes) crash(es) logged"
echo "== $pass passed, $fail failed =="
[ "$fail" = "0" ]
