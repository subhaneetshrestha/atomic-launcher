#!/usr/bin/env bash
# HARNESS_V1 — Phase 5 acceptance checks against a booted emulator or device (debug build).
#   scripts/verify-badges.sh <adb serial>
# Drives the badge settings, the notification-access disclosure and the restricted-settings help
# through uiautomator, then counts real notifications: SMS through the emulator console (Messages
# has a launcher activity, so its badge is on a home row) and a clock timer for the rule that a
# channel which forbids badges never gets one.
#
# The badge is drawn, not written, so every count is read back from the row's content description
# (which the app sets for exactly this reason: a screen reader could not see a Paint either).
set -u
SERIAL=${1:?usage: verify-badges.sh <serial>}
SDK=${ANDROID_HOME:-/opt/android-sdk}
ADB="$SDK/platform-tools/adb -s $SERIAL"
PKG=io.github.subhaneetshrestha.atomic.debug
CMP=$PKG/io.github.subhaneetshrestha.atomic.home.HomeActivity
LISTENER=$PKG/io.github.subhaneetshrestha.atomic.notifications.BadgeNotificationListener
MESSAGES=com.google.android.apps.messaging
CLOCK=com.google.android.deskclock
APK=app/build/outputs/apk/debug/app-debug.apk
pass=0; fail=0
ok() { echo "PASS  $1"; pass=$((pass + 1)); }
ko() { echo "FAIL  $1"; fail=$((fail + 1)); }
info() { echo "INFO  $1"; }
sh() { $ADB shell "$@" 2>/dev/null | tr -d '\r'; }
wait_s() { $ADB shell sleep "$1" >/dev/null 2>&1; }
dump() { $ADB shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; $ADB shell cat /sdcard/ui.xml 2>/dev/null | tr -d '\r'; }
has_text() { grep -qiE "text=\"$1(\"|&#10;)" <<<"$2"; }
node_with_text() { grep -oiE "<node[^>]*text=\"$1(\"|&#10;)[^>]*>" <<<"$2" | head -1; }
bounds_of() { node_with_text "$1" "$2" | grep -o 'bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | head -1 | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/'; }
centre() { read -r x1 y1 x2 y2 <<<"$1"; echo "$(( (10#$x1 + 10#$x2) / 2 )) $(( (10#$y1 + 10#$y2) / 2 ))"; }
tap_text() { local b; b=$(bounds_of "$1" "$(dump)"); [ -z "$b" ] && return 1; sh input tap $(centre "$b") >/dev/null; wait_s 2; }
# Scrolls a list until the row is on screen, then taps it. The app list is longer than one screen.
scroll_to_tap() {
  local i
  for i in 1 2 3 4 5 6; do
    tap_text "$1" && return 0
    sh input swipe $((width / 2)) $((height * 3 / 4)) $((width / 2)) $((height / 4)) 300 >/dev/null
    wait_s 1
  done
  return 1
}
# The count a row is carrying, read from its content description ("Messages, 2 notifications").
row_desc() { grep -oE "<node[^>]*text=\"$1\"[^>]*>" <<<"$2" | grep -o 'content-desc="[^"]*"' | head -1 | sed 's/content-desc="\(.*\)"/\1/'; }
badge_of() { local d; d=$(row_desc "$1" "$(dump)"); grep -oE '[0-9]+ notification' <<<"$d" | grep -oE '^[0-9]+' | head -1; }
settings_json() { sh run-as "$PKG" cat files/settings.json; }
# The document has several "enabled" keys (the clock, the date, the battery), so the badge one is
# read from its own block. Keys are written in declaration order, so this is exact.
badges_enabled() { settings_json | tr -d ' \n' | grep -q '"notifications":{"enabled":true'; }
crash_count() { $ADB logcat -d -b crash 2>/dev/null | tr -d '\r' | grep -c "$PKG" || true; }
go_home() { sh am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1; wait_s 2; }
launcher_component() { sh pm dump "$1" | grep -B6 'category.LAUNCHER' | grep -oE "$1/[A-Za-z0-9_.\$]+" | head -1; }
grant_access() {
  if [ "$api" -ge 28 ]; then sh cmd notification allow_listener "$LISTENER" >/dev/null
  else sh settings put secure enabled_notification_listeners "$LISTENER" >/dev/null; fi
  wait_s 2
}
revoke_access() {
  if [ "$api" -ge 28 ]; then sh cmd notification disallow_listener "$LISTENER" >/dev/null
  else sh settings put secure enabled_notification_listeners '""' >/dev/null; fi
  wait_s 2
}
# Waits up to 8 s for a row's badge to read $2 ("" = no badge at all), and records how many
# looks it took: the first look happens straight away, so "1" means it was already right.
await_badge() {
  local row=$1 want=$2 got n
  for n in 1 2 3 4 5 6 7 8; do
    got=$(badge_of "$row")
    if [ "${got:-}" = "$want" ]; then echo "${got}|$n"; return 0; fi
    wait_s 1
  done
  echo "${got:-}|$n"; return 1
}
# Reads a row's badge into $badge and how many looks it took into $looks (a look is about a
# second). await_badge runs in a subshell, so the two come back down one pipe.
badge=""; looks=0
look_for() {
  local r; r=$(await_badge "$1" "$2"); badge=${r%%|*}; looks=${r##*|}
  [ "$badge" = "$2" ]
}

api=$(sh getprop ro.build.version.sdk)
read -r width height < <(sh wm size | awk -F'[ x]' '/Physical/{print $3, $4}')
# Long-press on empty space is the way into settings. Which part of the screen is empty depends
# on the theme (a top-aligned one puts the full-width clock where the first guess lands), so try
# a few heights and check we arrived.
open_settings() {
  local y
  for y in $((height / 10)) $((height * 17 / 20)) $((height * 2 / 5)) $((height * 3 / 5)); do
    sh input swipe $((width / 2)) "$y" $((width / 2)) "$y" 1500 >/dev/null
    wait_s 2
    has_text "Home apps" "$(dump)" && return 0
    sh input keyevent KEYCODE_BACK >/dev/null
    wait_s 1
  done
  return 1
}
echo "== $SERIAL: API $api, ${width}x${height} =="

$ADB install -r -t "$APK" >/dev/null 2>&1 && ok "install $APK" || ko "install $APK"
sh pm clear "$MESSAGES" >/dev/null
if [ "$api" -ge 29 ]; then sh cmd role add-role-holder --user 0 android.app.role.HOME "$PKG" >/dev/null; else sh cmd package set-home-activity "$CMP" >/dev/null; fi
revoke_access
$ADB logcat -c >/dev/null 2>&1
crashes_before=$(crash_count)

# The two apps whose notifications we can raise from here, pinned to the home list so their
# badges are on screen, with the first-run setup marked done. The launcher is the home app, so
# the system relaunches it the moment it dies: clearing its data and replacing the document has
# to happen in one shell command, or the relaunched process writes its defaults over ours.
msg_cmp=$(launcher_component "$MESSAGES")
clock_cmp=$(launcher_component "$CLOCK")
[ -n "$msg_cmp" ] && [ -n "$clock_cmp" ] && ok "found $msg_cmp and $clock_cmp" || ko "found the messages and clock components"
tmp=$(mktemp)
printf '{"schema":1,"app":{"setupDone":true},"home":{"entries":[{"component":"%s"},{"component":"%s"}]}}' \
  "$msg_cmp" "$clock_cmp" > "$tmp"
$ADB push "$tmp" /data/local/tmp/atomic-settings.json >/dev/null 2>&1
rm -f "$tmp"
sh chmod 644 /data/local/tmp/atomic-settings.json >/dev/null
pin_apps() {
  local attempt ui
  for attempt in 1 2 3; do
    sh "pm clear $PKG; run-as $PKG sh -c 'mkdir -p files; cp /data/local/tmp/atomic-settings.json files/settings.json'" >/dev/null
    go_home; wait_s 1
    ui=$(dump)
    has_text "Messages" "$ui" && has_text "Clock" "$ui" && return 0
    info "the pinned rows did not take on attempt $attempt; retrying"
  done
  return 1
}
pin_apps && ok "home shows the two pinned rows" || ko "home shows the two pinned rows"

# 1. The service is declared for the system alone. Read from the compiled manifest: API 26's
# dumpsys does not print a component's permission, so the device is no help here.
aapt=$(ls "$SDK"/build-tools/*/aapt2 2>/dev/null | tail -1)
if [ -n "$aapt" ]; then
  "$aapt" dump xmltree --file AndroidManifest.xml "$APK" 2>/dev/null |
    grep -A8 'E: service' | grep -A2 'BadgeNotificationListener' | grep -q 'BIND_NOTIFICATION_LISTENER_SERVICE' &&
    ok "listener service is guarded by BIND_NOTIFICATION_LISTENER_SERVICE" ||
    ko "listener service is guarded by BIND_NOTIFICATION_LISTENER_SERVICE"
else
  info "no aapt2 found; skipped the manifest guard check"
fi
sh dumpsys package "$PKG" | grep -q 'BadgeNotificationListener' && ok "the system knows the listener service" \
  || ko "the system knows the listener service"

# 2. Badges are off out of the box, and say what they need.
open_settings || ko "long press on empty space opens settings"
tap_text "Notification badges" || ko "Notification badges row in settings"
ui=$(dump)
has_text "Show notification badges" "$ui" && ok "badge settings screen" || ko "badge settings screen"
grep -qi 'Needs notification access' <<<"$ui" && ok "the switch says access is needed" || ko "the switch says access is needed"
badges_enabled && ko "badges are off out of the box" || ok "badges are off out of the box"

# 3. Turning them on shows the disclosure first, and grants nothing by itself.
tap_text "Show notification badges" || ko "tap the badge switch"
ui=$(dump)
grep -qi 'atomic reads which app posted a notification' <<<"$ui" && ok "disclosure says what is read" || ko "disclosure says what is read"
grep -qi 'Nothing is written down and nothing leaves your phone' <<<"$ui" && ok "disclosure says what the limits are" || ko "disclosure says what the limits are"
has_text "Continue to Settings" "$ui" && has_text "Not now" "$ui" && ok "disclosure asks before Settings" || ko "disclosure asks before Settings"
listeners=$(sh settings get secure enabled_notification_listeners)
grep -q "$PKG" <<<"$listeners" && ko "nothing is granted by showing the disclosure" || ok "nothing is granted by showing the disclosure"
if [ "$api" -ge 33 ]; then
  has_text "If the switch does nothing" "$ui" && ok "sideload help offered on API $api" || ko "sideload help offered on API $api"
else
  has_text "If the switch does nothing" "$ui" && ko "no sideload help below API 33" || ok "no sideload help below API 33"
fi

# 4. Not now leaves everything as it was.
tap_text "Not now" || ko "Not now"
ui=$(dump)
has_text "Show notification badges" "$ui" && ok "Not now returns to the badge screen" || ko "Not now returns to the badge screen"
badges_enabled && ko "Not now leaves badges off" || ok "Not now leaves badges off"

# 5. Continue hands over to the right Settings page.
tap_text "Show notification badges" || ko "tap the badge switch again"
tap_text "Continue to Settings" || ko "Continue to Settings"
wait_s 2
front=$(sh dumpsys activity activities | grep -m1 -E 'mResumedActivity|topResumedActivity|ResumedActivity:')
grep -q 'com.android.settings' <<<"$front" && ok "Continue opens Settings ($(grep -oE 'com.android.settings/[A-Za-z0-9_.]+' <<<"$front" | head -1))" \
  || ko "Continue opens Settings (front: $front)"
ui=$(dump)
if [ "$api" -ge 30 ]; then
  # The detail route names this app, because it is the page for this one listener.
  grep -qiE 'atomic' <<<"$ui" && ok "Settings opens the page for atomic itself" \
    || ko "Settings opens the page for atomic itself"
else
  grep -qiE 'text="(Notification access|Notification Access)"' <<<"$ui" && ok "Settings opens the notification-access list" \
    || ko "Settings opens the notification-access list (titles: $(grep -o 'text="[^"]*"' <<<"$ui" | head -4 | tr '\n' ' '))"
fi
grep -q 'notification_access' <<<"$(settings_json)" && ok "the moment of consent is written down" || ko "the moment of consent is written down"

# 6. Coming back without granting says so, and offers the sideload help where it applies.
sh input keyevent KEYCODE_BACK >/dev/null; wait_s 3
ui=$(dump)
if [ "$api" -ge 33 ]; then
  has_text "Open App info" "$ui" && ok "returning ungranted offers the sideload help" || ko "returning ungranted offers the sideload help"
  grep -qi 'Allow restricted settings' <<<"$ui" && ok "the help names the switch to look for" || ko "the help names the switch to look for"
  sh input keyevent KEYCODE_BACK >/dev/null; wait_s 2
else
  has_text "Continue to Settings" "$ui" && ok "returning ungranted leaves the disclosure open to retry" \
    || ko "returning ungranted leaves the disclosure open to retry"
  sh input keyevent KEYCODE_BACK >/dev/null; wait_s 2
fi
badges_enabled && ko "badges stay off until access is real" || ok "badges stay off until access is real"

# 7. The grant arrives. The user has already said what they wanted by tapping Continue, so the
# launcher honours it on the way back rather than making them find the switch again.
grant_access
go_home
badges_enabled && ok "a grant the user went to Settings for turns badges on by itself" \
  || ko "a grant the user went to Settings for turns badges on by itself"
open_settings || ko "long press on empty space opens settings"
tap_text "Notification badges" || ko "Notification badges row (after granting)"
ui=$(dump)
grep -qi 'Notification access is on' <<<"$ui" && ok "the screen sees the grant" || ko "the screen sees the grant"
# And the switch is a switch: off, then on again with no disclosure, because access is already there.
tap_text "Show notification badges" || ko "turn badges off"
badges_enabled && ko "the switch turns badges off" || ok "the switch turns badges off"
tap_text "Show notification badges" || ko "turn badges on again"
badges_enabled && ok "and on again, with no second disclosure" || ko "and on again, with no second disclosure"
has_text "Continue to Settings" "$(dump)" && ko "no disclosure when access is already granted" \
  || ok "no disclosure when access is already granted"
go_home

# 8. Counting: two conversations, two notifications, one badge of 2.
row_width() { local b; b=$(bounds_of "$1" "$(dump)"); [ -z "$b" ] && { echo 0; return; }; read -r x1 _ x2 _ <<<"$b"; echo $(( 10#$x2 - 10#$x1 )); }
bare_row=$(row_width Messages)
$ADB emu sms send 5551234567 "first message" >/dev/null 2>&1
wait_s 1
look_for Messages 1 && ok "one notification, one badge (after $looks look(s) of about a second)" \
  || ko "one notification, one badge (read: '${badge:-none}')"
$ADB emu sms send 5559876543 "second message" >/dev/null 2>&1
look_for Messages 2 && ok "a second conversation counts too" || ko "a second conversation counts too (read: '${badge:-none}')"
desc=$(row_desc Messages "$(dump)")
grep -qi 'Messages, 2 notifications' <<<"$desc" && ok "the count is spoken as well as drawn" || ko "the count is spoken as well as drawn (desc: '$desc')"
# The badge lives inside the row, beside the name: the row is wider with a badge than without
# one, is nothing like the width of the screen, and is still centred.
b=$(bounds_of "Messages" "$(dump)")
if [ -n "$b" ]; then
  read -r x1 _ x2 _ <<<"$b"
  roww=$(( 10#$x2 - 10#$x1 ))
  off=$(( (10#$x1 + 10#$x2) / 2 - width / 2 ))
  [ "$bare_row" -gt 0 ] && [ "$roww" -gt $((bare_row + 20)) ] \
    && ok "the badge is inside the row, beside the name (row ${bare_row}px bare, ${roww}px badged)" \
    || ko "the badge is inside the row, beside the name (row ${bare_row}px bare, ${roww}px badged)"
  [ "$roww" -lt $((width * 7 / 10)) ] && ok "the row is as wide as its name, not the screen ($roww of $width)" \
    || ko "the row is as wide as its name, not the screen ($roww of $width)"
  [ "${off#-}" -lt $((width / 20)) ] && ok "and it is still centred (off by ${off}px)" \
    || ko "and it is still centred (off by ${off}px)"
else
  ko "the Messages row is on screen"
fi

# 9. A channel that forbids badges never gets one: the clock's own timer channel.
sh am start -a android.intent.action.SET_TIMER --ei android.intent.extra.alarm.LENGTH 600 --ez android.intent.extra.alarm.SKIP_UI true >/dev/null
wait_s 3
go_home
timer_shown=$(sh dumpsys notification | grep -c "pkg=$CLOCK")
[ "${timer_shown:-0}" -ge 1 ] && info "the clock has $timer_shown notification(s) showing" || info "the clock posted nothing; skipping"
got=$(badge_of Clock)
[ -z "$got" ] && ok "a channel that forbids badges gets none" || ko "a channel that forbids badges gets none (read: '$got')"

# 10. Per-app: the count is kept, the badge is not shown.
open_settings || ko "long press on empty space opens settings"
tap_text "Notification badges" || ko "Notification badges row (per-app)"
tap_text "Apps without badges" || ko "Apps without badges row"
scroll_to_tap "Messages" || ko "Messages row in the per-app list"
wait_s 1
grep -q '"pkg": "com.google.android.apps.messaging"' <<<"$(settings_json)" && ok "the app is written down as silenced" || ko "the app is written down as silenced"
go_home
look_for Messages "" && ok "a silenced app shows no badge" || ko "a silenced app shows no badge (read: '$badge')"
open_settings || ko "long press on empty space opens settings"
tap_text "Notification badges" >/dev/null; tap_text "Apps without badges" >/dev/null; scroll_to_tap "Messages" >/dev/null
go_home
look_for Messages 2 && ok "unsilencing brings the badge back at once (after $looks look(s))" \
  || ko "unsilencing brings the badge back at once (read: '${badge:-none}')"

# 11. Access taken away: badges go, notifications stay.
revoke_access
look_for Messages "" && ok "revoking access clears the badges (after $looks look(s))" \
  || ko "revoking access clears the badges (read: '$badge')"
still=$(sh dumpsys notification | grep -c "pkg=$MESSAGES")
[ "${still:-0}" -ge 1 ] && ok "the notifications themselves are untouched" || ko "the notifications themselves are untouched"

# 12. Granted again: the counts come back without a new notification.
grant_access
look_for Messages 2 && ok "granting again rebuilds the counts (after $looks look(s))" \
  || ko "granting again rebuilds the counts (read: '${badge:-none}')"

# 13. The launcher's process dies the way it dies under memory pressure: Android restarts the
# service, the listener reconnects and the counts are rebuilt from what is still showing.
# (am force-stop is not the same thing: it also cancels the service restart, and on API 26
# nothing rebinds afterwards until the grant is touched again.)
pid=$(sh pidof "$PKG")
[ -n "$pid" ] && sh "run-as $PKG kill -9 $pid" >/dev/null || info "no pid for $PKG"
wait_s 3
go_home
look_for Messages 2 && ok "the counts come back after the process dies (after $looks look(s))" \
  || ko "the counts come back after the process dies (read: '${badge:-none}')"

# 14. Notifications dismissed: the badge goes away.
sh pm clear "$MESSAGES" >/dev/null
look_for Messages "" && ok "dismissing the last notification removes the badge" \
  || ko "dismissing the last notification removes the badge (read: '$badge')"

# 15. Big text: nothing breaks and the badge is still there.
$ADB emu sms send 5551234567 "big text run" >/dev/null 2>&1
look_for Messages 1 >/dev/null
sh settings put system font_scale 2.0 >/dev/null; wait_s 3
go_home
look_for Messages 1 && ok "the badge survives 200% font scale" || ko "the badge survives 200% font scale (read: '${badge:-none}')"
ui=$(dump); has_text "Messages" "$ui" && ok "rows still readable at 200%" || ko "rows still readable at 200%"
sh settings put system font_scale 1.0 >/dev/null; wait_s 2

# 16. Another theme: rows start at the left and the badge is a plain number, not a circle.
open_settings || ko "long press on empty space opens settings"
tap_text "Theme" || ko "Theme row"
scroll_to_tap "Terminal" || ko "Terminal theme row"
wait_s 2
go_home
restore_ink() {
  open_settings || return 1
  tap_text "Theme" || return 1
  scroll_to_tap "Ink" || return 1
  wait_s 2
  go_home
}
look_for Messages 1 && ok "a start-aligned theme with a plain-number badge still counts" \
  || ko "a start-aligned theme with a plain-number badge still counts (read: '${badge:-none}')"
ui=$(dump)
b=$(bounds_of "Messages" "$ui")
if [ -n "$b" ]; then
  read -r x1 _ _ _ <<<"$b"
  [ "$((10#$x1))" -lt "$((width / 3))" ] && ok "the row is aligned to the start" \
    || ko "the row is aligned to the start (left edge $x1 of $width)"
else
  ko "the Messages row is on screen under the terminal theme"
fi

restore_ink && ok "the theme goes back to ink" || ko "the theme goes back to ink"

# 17. A paused app has nothing to show. Pausing hides notifications rather than removing them,
# so the only word the launcher gets is a ranking update. (Android 9 and later.)
if [ "$api" -ge 28 ]; then
  sh pm suspend "$MESSAGES" >/dev/null
  look_for Messages "" && ok "pausing an app takes its badge away" || ko "pausing an app takes its badge away (read: '$badge')"
  sh pm unsuspend "$MESSAGES" >/dev/null
  look_for Messages 1 && ok "unpausing brings it back" || ko "unpausing brings it back (read: '${badge:-none}')"
else
  info "no pm suspend on API $api; the pause rule is covered by BadgeHubTest"
fi

# 18. The grant is honoured even when the screen that asked for it is gone. Leaving Settings with
# the Home key finishes it, so the launcher's own onResume is the only thing left to notice.
revoke_access
open_settings || ko "long press on empty space opens settings"
tap_text "Notification badges" || ko "Notification badges row (grant-outlives-screen)"
tap_text "Show notification badges" || ko "turn badges off again"
badges_enabled && ko "badges off before the second grant" || ok "badges off before the second grant"
tap_text "Show notification badges" || ko "tap the switch with access revoked"
tap_text "Continue to Settings" || ko "Continue to Settings (second time)"
wait_s 2
grant_access
go_home
wait_s 2
badges_enabled && ok "the grant is honoured after the asking screen is gone" \
  || ko "the grant is honoured after the asking screen is gone"
look_for Messages 1 && ok "and the badge appears without another trip through settings" \
  || ko "and the badge appears without another trip through settings (read: '${badge:-none}')"

# 19. Nothing crashed along the way.
sh pm clear "$MESSAGES" >/dev/null
sh pm clear "$CLOCK" >/dev/null
crashes_after=$(crash_count)
[ "$crashes_after" = "$crashes_before" ] && ok "no crashes ($crashes_after lines)" || ko "no crashes (before $crashes_before, after $crashes_after)"

echo "== $SERIAL: $pass passed, $fail failed =="
[ "$fail" -eq 0 ]
