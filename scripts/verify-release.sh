#!/usr/bin/env bash
# HARNESS_V1 — Phase 9: the minified release build, on a device.
#   scripts/verify-release.sh <adb serial>
#
# Every other harness installs the debug APK, so nothing until now has run the code R8 actually
# ships. The risk this covers is specific: kotlinx.serialization resolves serializers by reflection,
# R8 full mode strips what it cannot see being used, and a document that encodes and decodes
# perfectly on the JVM can come back empty — or not at all — from a shrunk APK. That failure is
# invisible until the home screen is blank on someone's phone.
#
# Requires ./gradlew assembleRelease, and apksigner (the unsigned APK is re-signed with the debug
# key so it can be installed; debug signing does not make it debuggable, so run-as stays out of
# reach and every assertion here reads the screen or the system, never the file).
set -u
SERIAL=${1:?usage: verify-release.sh <serial>}
SDK=${ANDROID_HOME:-/opt/android-sdk}
ADB="$SDK/platform-tools/adb -s $SERIAL"
PKG=io.github.subhaneetshrestha.atomic
CMP=$PKG/io.github.subhaneetshrestha.atomic.home.HomeActivity
APK=app/build/outputs/apk/release/app-release-unsigned.apk
BANNER='Set atomic as your home app'
pass=0; fail=0
ok() { echo "PASS  $1"; pass=$((pass + 1)); }
ko() { echo "FAIL  $1"; fail=$((fail + 1)); }
info() { echo "INFO  $1"; }
sh() { $ADB shell "$@" 2>/dev/null | tr -d '\r'; }
wait_s() { $ADB shell sleep "$1" >/dev/null 2>&1; }
dump() { $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; $ADB shell cat /sdcard/ui.xml 2>/dev/null | tr -d '\r'; }
rows() { grep -o "<node[^>]*class=\"android.widget.TextView\"[^>]*package=\"$PKG\"[^>]*>" <<<"$1" | grep -o 'text="[^"]\+"' | grep -v "$BANNER" | sed 's/text="\(.*\)"/\1/' | grep -vE '^[0-9]{1,2}:[0-9]{2}|^[0-9]{1,3}%|^(Mon|Tues|Wednes|Thurs|Fri|Satur|Sun)day'; }
top() {
  local a
  a=$(sh dumpsys activity activities | grep -m1 -o 'topResumedActivity=ActivityRecord{[^}]*}')
  [ -z "$a" ] && a=$(sh dumpsys activity activities | grep -m1 -o 'mResumedActivity: ActivityRecord{[^}]*}')
  [ -z "$a" ] && a=$(sh dumpsys window | grep -m1 -o 'mCurrentFocus=Window{[^}]*}')
  sed -E 's/.* u0 ([^ }]+).*/\1/' <<<"$a"
}
tap_text() { # tap the first node whose text starts with $1; returns 1 if there is none.
  # A prefix, not an exact match: list rows carry their description after a newline in the same
  # text attribute ("Terminal&#10;Monospace with a phosphor green accent.").
  local b
  b=$(grep -oE "<node[^>]*text=\"$1[^\"]*\"[^>]*>" <<<"$(dump)" | head -1 | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/')
  [ -z "$b" ] && return 1
  read -r x1 y1 x2 y2 <<<"$b"; sh input tap $(( (10#$x1 + 10#$x2) / 2 )) $(( (10#$y1 + 10#$y2) / 2 )) >/dev/null; wait_s 2
}
home() { sh am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null; wait_s 2; }
relaunch() { sh am force-stop "$PKG" >/dev/null; wait_s 1; home; wait_s 2; }
# The median colour of a band, never one pixel: a pixel is as likely to be a letter as the ground.
band_colour() {
  local png; png=$(mktemp)
  $ADB exec-out screencap -p > "$png" 2>/dev/null
  python3 - "$png" <<'PY'
import sys, zlib, struct
def read(p):
    d = open(p, 'rb').read()
    if d[:8] != b'\x89PNG\r\n\x1a\n': return None
    i, idat, w, h, bd, ct = 8, b'', 0, 0, 0, 0
    while i < len(d):
        ln = struct.unpack('>I', d[i:i+4])[0]; typ = d[i+4:i+8]
        if typ == b'IHDR': w, h, bd, ct = *struct.unpack('>II', d[i+8:i+16]), d[i+16], d[i+17]
        elif typ == b'IDAT': idat += d[i+8:i+8+ln]
        i += 12 + ln
    if bd != 8 or ct not in (2, 6): return None
    ch = 3 if ct == 2 else 4
    raw, stride, out, prev = zlib.decompress(idat), w * ch, [], bytearray(w * ch)
    for y in range(h):
        f = raw[y*(stride+1)]; line = bytearray(raw[y*(stride+1)+1:(y+1)*(stride+1)])
        for x in range(stride):
            a = line[x-ch] if x >= ch else 0; b = prev[x]; c = prev[x-ch] if x >= ch else 0
            if f == 1: line[x] = (line[x]+a) & 255
            elif f == 2: line[x] = (line[x]+b) & 255
            elif f == 3: line[x] = (line[x]+(a+b)//2) & 255
            elif f == 4:
                p = a+b-c; pa, pb, pc = abs(p-a), abs(p-b), abs(p-c)
                line[x] = (line[x]+(a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        out.append(bytes(line)); prev = line
    return w, h, ch, out
r = read(sys.argv[1])
if not r: print('?'); sys.exit()
w, h, ch, rowsx = r
# A band across the lower third, away from the status bar and the app names.
px = [tuple(rowsx[y][x*ch:x*ch+3]) for y in range(int(h*0.80), int(h*0.88)) for x in range(0, w, 7)]
med = lambda i: sorted(p[i] for p in px)[len(px)//2]
print('#%02X%02X%02X' % (med(0), med(1), med(2)))
PY
  rm -f "$png"
}

api=$(sh getprop ro.build.version.sdk)
W=$(sh wm size | sed -E 's/.*: ([0-9]+)x[0-9]+/\1/'); H=$(sh wm size | sed -E 's/.*: [0-9]+x([0-9]+)/\1/')
echo "=== release build on $SERIAL (API $api, ${W}x${H}) ==="

[ -f "$APK" ] || { echo "FAIL  no $APK — run ./gradlew assembleRelease"; exit 1; }
signer=$(ls -d "$SDK"/build-tools/*/apksigner 2>/dev/null | sort -V | tail -1)
[ -n "$signer" ] || { echo "FAIL  no apksigner under $SDK/build-tools"; exit 1; }

tmp=$(mktemp -d)
"$signer" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android \
  --ks-key-alias androiddebugkey --key-pass pass:android \
  --out "$tmp/atomic-release.apk" "$APK" || { echo "FAIL  could not sign $APK"; rm -rf "$tmp"; exit 1; }

$ADB uninstall "$PKG" >/dev/null 2>&1
$ADB install -r "$tmp/atomic-release.apk" >/dev/null 2>&1 && ok "the minified release APK installs" || ko "the minified release APK installs"
rm -rf "$tmp"
$ADB logcat -c >/dev/null 2>&1

if [ "$api" -ge 29 ]; then sh cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" >/dev/null
else sh cmd package set-home-activity "$CMP" >/dev/null; fi
home; wait_s 3

# The first-run setup appears on a fresh install; skip it to reach the home list.
for _ in 1 2 3; do tap_text "Skip" && break; wait_s 1; done
home; wait_s 2

# 1. It starts at all. R8 renames everything the manifest does not pin; a wrong keep rule shows up
#    here as a ClassNotFoundException before the first frame.
[[ "$(top)" == *HomeActivity* ]] && ok "the release build starts and holds the home screen" || ko "the release build starts (top: $(top))"

# 2. The default document decoded. Six alphabetical app names is what the shipped defaults produce;
#    a failed decode is an empty list, not a wrong one.
list=$(rows "$(dump)")
n=$(grep -c . <<<"$list")
[ "$n" -ge 1 ] && ok "the default document decoded: $n app rows ($(tr '\n' ' ' <<<"$list"))" || ko "the default document decoded (no app rows)"

# 3. The built-in theme constants survived. ink is a black ground; a theme that failed to resolve
#    would leave the window background from the manifest, not the theme's colour.
bg=$(band_colour)
[ "$bg" = "#000000" ] && ok "the ink theme resolved: background $bg" || ko "the ink theme resolved (background $bg, expected #000000)"

# 4. Encode: a change made through the UI is written back. 5. Decode: it survives a restart.
#    Together this round-trips the whole Settings document — every gesture binding included, because
#    the codec encodes defaults — through the minified codec in both directions.
sh input swipe $((W / 2)) $((H / 10)) $((W / 2)) $((H / 10)) 1500 >/dev/null; wait_s 2   # long press the empty top → settings
if [[ "$(top)" == *SettingsActivity* ]] || grep -q 'text="Theme"' <<<"$(dump)"; then
  ok "settings opens from the release build"
  if tap_text "Theme"; then
    if tap_text "Terminal"; then
      wait_s 2; home; wait_s 2
      bg2=$(band_colour)
      [ "$bg2" != "#000000" ] && ok "a theme change applies and is encoded: background $bg2" || ko "a theme change applies (background still $bg2)"
      relaunch
      bg3=$(band_colour)
      [ "$bg3" = "$bg2" ] && ok "the change survives a restart: decoded back as $bg3" || ko "the change survives a restart (was $bg2, now $bg3)"
      list2=$(rows "$(dump)")
      [ "$(grep -c . <<<"$list2")" -ge 1 ] && ok "the app list decoded again after the restart" || ko "the app list decoded again after the restart"
    else ko "the Terminal theme is offered in the theme list"; fi
  else ko "the theme screen is reachable"; fi
else ko "settings opens from the release build (top: $(top))"; fi

# 6. A gesture bound to a built-in action still runs it. Action is a sealed hierarchy with a
#    tolerant polymorphic serializer — the part of the document R8 full mode is most likely to
#    break, and a break decodes as Action.Unknown, which does nothing at all.
home; wait_s 2
#    "Is the launcher still on top?" would pass either way here: the shade is a window over the
#    home activity, not an activity of its own. Read the focused window instead — StatusBar on
#    API 26, NotificationShade from API 28 — which is also the only proof that the reflective call
#    into android.app.StatusBarManager still resolves after R8.
sh input swipe $((W / 2)) $((H / 4)) $((W / 2)) $((H - H / 4)) 250 >/dev/null; wait_s 3
focus=$(sh dumpsys window | grep -m1 'mCurrentFocus=')
if grep -qE 'StatusBar|NotificationShade' <<<"$focus"; then
  ok "swipe down opened the shade: the sealed Action decoded and the reflective call resolved"
else
  ko "swipe down opened the shade (focus: $(sed -E 's/.*mCurrentFocus=//' <<<"$focus"))"
fi
sh cmd statusbar collapse >/dev/null 2>&1; wait_s 1
grep -qE 'StatusBar|NotificationShade' <<<"$(sh dumpsys window | grep -m1 'mCurrentFocus=')" &&
  ko "the shade closes again" || ok "the shade closes again"

# 7. Nothing crashed, and nothing failed to serialize, through any of it.
crashes=$($ADB logcat -d -b crash 2>/dev/null | tr -d '\r' | grep -c "$PKG")
[ "$crashes" -eq 0 ] && ok "no crash in the release build" || ko "no crash in the release build ($crashes lines)"
serr=$($ADB logcat -d 2>/dev/null | tr -d '\r' | grep -ciE 'SerializationException|Serializer for class|kotlinx\.serialization.*not found')
[ "$serr" -eq 0 ] && ok "no serialization failure in the log" || ko "no serialization failure in the log ($serr lines)"

echo
echo "release: $pass passed, $fail failed"
[ "$fail" -eq 0 ]
