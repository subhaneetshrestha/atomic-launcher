# Phase 3 acceptance — 2026-09-06

Scope: the gesture engine and the action registry. Ten gestures and the tap and hold of each info line can be bound to any of 44 built-in actions, an app, a link, or nothing, and the launcher says why when a binding cannot run.

## JVM tests (every push, `./gradlew test`)

| Seam | Tests | What they pin |
|---|---|---|
| `Action` and the settings schema | 13 | every variant's stored form; an action or a gesture key from a newer version kept verbatim; bindings merged with the shipped defaults so a hand-edited file does not unbind the rest; an explicit "none" unbinding; actions whose target is malformed unbound with a reason; `intent:`, `android-app:`, `file:` and `content:` links refused while custom app schemes stay bindable |
| `BindingSurface` and edits | 3 (in the edits suite) | one concept for gesture, info-line tap and hold; binding, unbinding and clearing back to the default; a stable key per surface |
| `GestureRecognizer` | 17 | four short swipes and four long ones; the axis locking when the drag begins; a flick counting on speed where distance falls short; arming and disarming with hysteresis; double tap and its two negative cases; the hold firing on a timeout with no touch of its own; a firm press skipping the wait; a second finger abandoning the gesture; cancel; a touch that starts on a row keeping its swipes but not its taps; the drag flag the touch layer depends on |
| `ActionAvailability` | 9 | the Android version each action needs; the torch on a device without one; a surface this version has not built; an action from a newer version; the accessibility grant, and the lock taking whichever of its two routes is open; the home role for shortcuts; a target that has been uninstalled; an intent nothing answered |
| `GestureDispatcher` | 7 | run, offer the grant, explain, or ignore; an unbound long swipe falling back to the short one of the same direction; arming being feedback rather than an action; info-line surfaces |

90 JVM tests in total across the modules, all green; ktlint and Android lint clean.

## Emulator checks (`scripts/verify-gestures.sh`, debug build)

| Check | API 26 | API 36 |
|---|---|---|
| Swipe left and swipe right open the camera and the phone, and differ | pass | pass |
| Holding empty space opens atomic's settings | pass | pass |
| Swipes bound to search and to the shade leave the home screen up | pass | pass |
| The gesture list shows every surface, what each does, and the reason where there is one | pass | pass |
| The picker offers nothing, apps, built-in actions, and marks what needs the service | pass | pass |
| Rebinding through the picker is persisted and returns to the list | pass | pass |
| The rebound double tap opens what it was bound to | pass | pass |
| Unbinding is written down, and the gesture then does nothing | pass | pass |
| A long swipe with no binding falls back to the short one | pass | pass |
| A swipe that starts on an app row swipes instead of opening the row | pass | pass |
| Tapping the clock opens the alarms | pass | pass |
| Both toggles are persisted | pass | pass |
| No crashes during the run | pass | pass |

24 of 24 on both levels.

Not verified: the haptics. A headless emulator has nothing to feel, and the code picks its feedback by API level, so the constants used on Android 14 and later have not been exercised at all. This needs a phone, and is the first thing to check when one is available.

Phase 1 and Phase 2 regressions after Phase 3, on the same builds:

| Harness | API 26 | API 36 |
|---|---|---|
| `verify-home.sh` (Phase 1) | 18 of 18 | 21 of 21 |
| `verify-settings.sh` (Phase 2) | 29 of 29 | 29 of 29 |

Release APK after Phase 3: 264,346 bytes unsigned, against the 2.5 MiB budget. Steady-state PSS on the debug build: 36 MB on API 26, 42 MB on API 36. First frame: 380 ms and 757 ms.

## What this build cannot do yet, on purpose

The six actions behind the accessibility service (lock, shade, quick settings, recents, screenshot, power menu) and the three launcher surfaces that later phases build (search, the app drawer, the next background) report "arrives in a later version of atomic" rather than asking the user to allow a service this build does not contain. The shipped default for swipe down is the notification shade, which therefore explains itself until Phase 8 adds the service; that default is the product decision recorded in ADR 0002 and is correct from that phase on.

## What the run taught us

- **Two adb taps are never a double tap.** Each `adb shell input tap` is a separate round trip, well beyond the 300 ms double-tap timeout, so the harness sends both taps in one call.
- **`uiautomator dump` only reports what is on screen**, and a list searched later may sit above where the last search left off, so the harness scrolls to the top before looking for a row.
- **Exact text matching breaks on an ellipsis**: "Open an app…" does not match a pattern anchored at the end of "app", which is why the picker's first two rows are matched loosely.
- **A swipe that begins on an app row must reach the gesture engine**, and the row must not also open. Watching every touch on its way through the root, and taking over once the drag passes the slop, gives both.
