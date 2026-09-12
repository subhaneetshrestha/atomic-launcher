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

# One pixel of the screen as "R G B". The screenshot is a PNG, which is unpacked here rather than
# on the phone: adb is the only thing installed on every machine this might run on.
pixel() {
  $ADB exec-out screencap -p > "$TMP/shot.png" 2>/dev/null
  python3 - "$TMP/shot.png" "$1" "$2" <<'PY'
import sys, zlib, struct
path, wanted_x, wanted_y = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
data = open(path, 'rb').read()
if data[:8] != b'\x89PNG\r\n\x1a\n':
    print("0 0 0"); raise SystemExit
pos, idat, w, h, colour = 8, b'', 0, 0, 6
while pos < len(data) - 8:
    length, typ = struct.unpack('>I4s', data[pos:pos + 8]); pos += 8
    chunk = data[pos:pos + length]; pos += length + 4
    if typ == b'IHDR':
        w, h, _depth, colour = struct.unpack('>IIBB', chunk[:10])
    elif typ == b'IDAT':
        idat += chunk
    elif typ == b'IEND':
        break
channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
stride = w * channels
raw = zlib.decompress(idat)
prev = bytearray(stride)
index = 0
x = min(max(wanted_x, 0), w - 1)
y = min(max(wanted_y, 0), h - 1)
for row in range(h):
    filt = raw[index]; index += 1
    line = bytearray(raw[index:index + stride]); index += stride
    if filt == 1:
        for k in range(channels, stride):
            line[k] = (line[k] + line[k - channels]) & 255
    elif filt == 2:
        for k in range(stride):
            line[k] = (line[k] + prev[k]) & 255
    elif filt == 3:
        for k in range(stride):
            left = line[k - channels] if k >= channels else 0
            line[k] = (line[k] + ((left + prev[k]) >> 1)) & 255
    elif filt == 4:
        for k in range(stride):
            a = line[k - channels] if k >= channels else 0
            b = prev[k]
            c = prev[k - channels] if k >= channels else 0
            p = a + b - c
            pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
            line[k] = (line[k] + (a if pa <= pb and pa <= pc else (b if pb <= pc else c))) & 255
    if row == y:
        o = x * channels
        print(line[o], line[o + 1] if channels > 2 else line[o], line[o + 2] if channels > 2 else line[o])
        break
    prev = line
PY
}
brightness() { read -r r g b <<<"$1"; echo $(( (10#$r * 30 + 10#$g * 59 + 10#$b * 11) / 100 )); }
# The window flags Android is actually drawing the home screen with.
window_flags() { sh dumpsys window windows | grep -A4 "$PKG/.*HomeActivity" | grep -o 'fl=[^ ]*' | head -1; }
meminfo() { sh dumpsys meminfo "$PKG" | grep -E "^\s+$1" | head -1 | awk '{print $2}'; }

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
ink=$(pixel $((W / 2)) $((H / 2)))
[ "$(brightness "$ink")" -lt 20 ] && ok "the ink theme paints the screen black ($ink)" || ko "the ink theme paints the screen black ($ink)"
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
topc=$(pixel $((W / 2)) $((H / 8)))
botc=$(pixel $((W / 2)) $((H * 7 / 8)))
diff=$(( $(brightness "$topc") - $(brightness "$botc") ))
[ "${diff#-}" -gt 8 ] && ok "the gradient runs from $topc to $botc" || ko "the gradient runs top to bottom (top $topc, bottom $botc)"

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
  bg=$(pixel $((W / 2)) $((H / 6)))
  info "the screen behind the names is now $bg"
  [ "$(brightness "$bg")" -gt 40 ] && ok "the image is on screen" || info "the dark image may be showing; asking for the next one"
  if [ "$(brightness "$bg")" -lt 40 ]; then
    open_background_settings; scroll_to_tap "Change now"; wait_s 6; home
    bg=$(pixel $((W / 2)) $((H / 6)))
    [ "$(brightness "$bg")" -gt 40 ] && ok "the image is on screen ($bg)" || ko "the image is on screen ($bg)"
  fi
  # The names are drawn over it: on the bright image they have to be dark to be read at all.
  ui=$(dump)
  row=$(grep -o "<node[^>]*class=\"android.widget.TextView\"[^>]*package=\"$PKG\"[^>]*>" <<<"$ui" | grep -o 'text="[^"]\+"' | sed 's/text="\(.*\)"/\1/' | grep -vE '^[0-9]{1,2}:[0-9]{2}|%|Set atomic' | head -1)
  if [ -n "$row" ]; then
    b=$(bounds_of "$row" "$ui"); read -r x1 y1 x2 y2 <<<"$b"
    text_pixel=$(pixel $(( (10#$x1 + 10#$x2) / 2 )) $(( (10#$y1 + 10#$y2) / 2 )))
    around=$(pixel $(( 10#$x1 - 8 )) $(( (10#$y1 + 10#$y2) / 2 )))
    info "the name '$row' is drawn at $text_pixel on $around"
    [ "$(brightness "$text_pixel")" -lt "$(brightness "$around")" ] &&
      ok "the names went dark over the bright image" || ko "the names went dark over the bright image"
  else
    info "no app row to read the text colour from"
  fi
  # 8. Clearing caches must not take away the wallpaper that is on screen.
  sh pm trim-caches 9999999999 >/dev/null 2>&1
  sh run-as "$PKG" ls files/background | grep -q current.img &&
    ok "trimming caches leaves the current image" || ko "trimming caches leaves the current image"
  # 9. What it costs. Recorded first, tightened once there are numbers from both emulators.
  graphics=$(meminfo Graphics)
  java=$(meminfo "Java Heap")
  total=$(sh dumpsys meminfo "$PKG" | grep -E 'TOTAL PSS|TOTAL' | head -1 | awk '{print $2}')
  budget=$(( W * H * 4 * 125 / 100 / 1024 ))
  info "Graphics ${graphics:-?} KB (window budget ${budget} KB), Java heap ${java:-?} KB, total ${total:-?} KB"
  if [ -n "${graphics:-}" ]; then
    [ "$graphics" -le $(( budget * 2 )) ] && ok "graphics memory is within twice the window" ||
      ko "graphics memory is $graphics KB against a ${budget} KB window"
  fi
  if [ -n "${total:-}" ]; then
    [ "$total" -le 51200 ] && ok "total memory is under 50 MB (${total} KB)" || ko "total memory is ${total} KB"
  fi
fi

# 10. Turning the collection off puts the theme colour back and cancels the job.
open_background_settings || ko "reopening the background settings"
tap_text "Theme colour" || ko "choosing the theme colour again"
home
back=$(pixel $((W / 2)) $((H / 6)))
[ "$(brightness "$back")" -lt 20 ] && ok "the theme colour comes back ($back)" || ko "the theme colour comes back ($back)"
sh dumpsys jobscheduler | grep -q "$PKG.*$JOB" && ko "the job is cancelled with it" || ok "the job is cancelled with it"

# 11. A restart shows the same thing it was showing, without fetching again.
sh am force-stop "$PKG" >/dev/null; wait_s 2
home
[ "$(brightness "$(pixel $((W / 2)) $((H / 2)))")" -lt 20 ] && ok "a restart paints the same background" || ko "a restart paints the same background"

[ "$(crashes)" = "0" ] && ok "no crashes during the run" || ko "$(crashes) crash(es) logged"
echo "== $pass passed, $fail failed =="
[ "$fail" = "0" ]
