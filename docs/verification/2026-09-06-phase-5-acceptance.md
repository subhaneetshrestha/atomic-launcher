# Phase 5 acceptance — 2026-09-06

Scope: the number of waiting notifications beside an app's name, and the first special access the
launcher ever asks for. Badges are off until the user turns them on; turning them on shows what
would be read and what it is used for, and hands over to Settings only when the user says so.
While the setting is off the listener asks Android to unbind it, so a launcher with badges off is
not receiving notifications at all.

## JVM tests (every push, `./gradlew test`)

| Seam | Tests | What they pin |
|---|---|---|
| `BadgeCounter` | 7 | the group summary is skipped in favour of the notifications under it; a channel the user silenced does not count; a suspended app, and a notification with neither title nor text, do not count; an ongoing notification, a foreground service and a playing track are left out unless the user asks for them; an ongoing notification on the channel apps had before channels existed never counts; a count is the sum of what the apps say, at least one each, capped at 999; the same app in another profile counts separately |
| `BadgeStore` | 6 | a count can be read the instant it arrives, while the screen is told one frame later, so five arrivals cost one redraw; nothing is redrawn when nothing changed; losing access clears everything; an app the user silenced reads nought while its count is kept; a listener can be removed |
| `BadgeHub` | 7 | everything already showing becomes counts the moment access is granted; one arrival changes one app; dismissing the last notification takes the badge away; turning on ongoing notifications recounts what is already there, with no new notification needed; silencing a channel stops its count without dismissing anything; pausing an app takes its badge away and unpausing brings it back, both from a ranking update alone; losing access forgets the notifications too |
| `BadgeGeometry` | 5 | a single digit sits in a circle sized from the row's own text size; three digits stretch it into a pill without changing the row's height; a dot says only that something is waiting; a plain number is only the digits; a badge never grows past one and a fifth of the name it sits beside |
| `NotificationAccess` | 4 | on Android 8 the grant is read from the list the system keeps, in either spelling of the component and with spaces around it; an app whose name merely contains ours is not us; Android 11 and later open the page for this one app, older versions the list of every listener; a small phone on an old Android cannot do this at all |
| `InstallSource` | 4 | nothing is restricted before Android 13; an app from the Play Store is never restricted; an app installed from a file is; anything else is a maybe, and is worded as one |
| `Badge` theme block | 4 | a badge is a number in a circle after the name; left to itself it takes the name's colour with the background showing through, so it needs no palette of its own; a theme may name both colours; a scale that would not fit is pulled back and an unreadable colour falls back, both with warnings |
| `NotificationConfig` | 3 | badges and ongoing notifications are both off out of the box; an exception is stored by package, deduplicated and checked; a badge is shown only while the feature is on and the app is not excepted |
| `SettingsEdits` | 3 | silencing one app leaves the others alone and is idempotent; the moment of consent is written down once and never rewritten; a consent written by a newer version is kept as it is |

43 tests added, 157 across the project, all green. ktlint and Android lint clean. Release APK
293,226 bytes unsigned against the 2.5 MiB budget; the release classpath is still free of Google
Play services.

## Emulator checks (`scripts/verify-badges.sh`, debug build)

Notifications are raised the way a phone raises them: an SMS through the emulator console, which
Google Messages posts as an ordinary notification, and a clock timer, which Deskclock posts on a
channel that forbids badges. Messages has a launcher activity, so its badge is on a home row.

The badge is drawn with a Paint, not written as text, so every count below is read back from the
row's content description — which the app sets for exactly that reason: a screen reader cannot see
a Paint either.

| Check | API 26 | API 36 |
|---|---|---|
| The listener service is declared for the system alone, guarded by `BIND_NOTIFICATION_LISTENER_SERVICE` | pass | pass |
| Badges are off out of the box, and the switch says access is needed | pass | pass |
| The switch shows the disclosure first: what is read, what it is used for, what the limits are | pass | pass |
| Showing the disclosure grants nothing | pass | pass |
| "Not now" returns to the badge screen and leaves badges off | pass | pass |
| "Continue" opens the notification-access list (API 26) or this app's own page (API 30+) | pass | pass |
| The moment of consent is written into the document | pass | pass |
| Coming back without the grant says so, and leaves the disclosure open to try again | pass | pass |
| Coming back without the grant offers the restricted-settings help where Android guards it | n/a | pass |
| The help names the switch to look for ("Allow restricted settings") | n/a | pass |
| Badges stay off until access is real | pass | pass |
| A grant the user went to Settings for turns badges on by itself, and the screen sees it | pass | pass |
| The switch then turns badges off, and on again with no second disclosure | pass | pass |
| The grant is honoured even after the screen that asked for it is gone (left by the Home key) | pass | pass |
| One notification, one badge, at the first look (about a second) | pass | pass |
| A second conversation counts too | pass | pass |
| The count is spoken as well as drawn | pass | pass |
| The badge is inside the row beside the name: wider with a badge (347 px) than without (289 px), nothing like the width of the screen, still centred | pass | pass |
| A channel that forbids badges gets none | pass (nothing posted) | pass (2 posted) |
| Silencing one app is written down and its badge goes | pass | pass |
| Unsilencing brings the badge back at the first look | pass | pass |
| Taking access away clears the badges at the first look, leaving the notifications alone | pass | pass |
| Granting again rebuilds the counts at the first look | pass | pass |
| The counts come back after the process dies | pass | pass |
| Dismissing the last notification removes the badge | pass | pass |
| The badge survives a 200 % font scale, and rows stay readable | pass | pass |
| A start-aligned theme with a plain-number badge still counts, and the row is still at the start | pass | pass |
| Pausing an app takes its badge away; unpausing brings it back | n/a | pass |
| No crashes anywhere in the run | pass | pass |

49 of 49 on API 26 and 52 of 52 on API 36. API 36 has three checks API 26 cannot run: the two
restricted-settings rows, which Android only guards from 13, and the pause rule, which needs both
`Ranking.isSuspended` (Android 9) and `pm suspend`.

Phase 1 to 4 harnesses re-run on the same build, with no failures anywhere:

| Harness | API 26 | API 36 |
|---|---|---|
| `verify-home.sh` (Phase 1) | 18 of 18 | 18 of 18 |
| `verify-settings.sh` (Phase 2) | 29 of 29 | 29 of 29 |
| `verify-gestures.sh` (Phase 3) | 24 of 24 | 24 of 24 |
| `verify-search.sh` (Phase 4) | 19 of 19 | 19 of 19 |

The home harness counts a few checks conditionally (it skips the first-run setup and taps whichever
app is first alphabetically), so its total moves between runs; the set of checks is the same on both
levels.

## What the emulators could not answer

- **Counting ongoing notifications.** A clean emulator image has nothing that posts an ongoing
  notification on a badge-allowed channel and can be raised from adb: the clock's own timer channel
  sets `showBadge=false`, which turned into a check of its own. The rule itself is pinned by
  `BadgeCounter`, and the setting is verified to persist.
- **One redraw per burst.** Coalescing is not visible from outside the process; `BadgeStore` pins it.
- **A real sideloaded install.** Installing over adb reports its source as neither a store nor a
  file, so Android 13+ answers "maybe" and the help is worded as a maybe. The certain wording, and
  the switch actually being inert, need an APK installed from a file manager on a real phone.
- **The clock timer on API 26.** `SET_TIMER` with `SKIP_UI` posted nothing there, so the
  forbidden-channel row passed with nothing on screen. It is a real check on API 36.
- **A grant surviving process death.** The pending hand-off to Settings is process state, so a
  launcher killed while the user is still in Settings forgets that they went there. Coming back and
  tapping the switch then turns badges on straight away, since access is already granted.

## What we learned

- **A home app cannot be force-stopped in peace.** Android relaunches the home app the moment it
  dies, so a document written into place after `pm clear` is overwritten by the process that came
  back. The harness now clears the data and installs the document in one shell command.
- **`am force-stop` is the wrong way to simulate a process being killed.** It also cancels the
  service restart Android would otherwise schedule, and on API 26 nothing rebinds the listener
  afterwards until the grant is touched again. An ordinary death (`kill -9` from `run-as`) is what
  memory pressure looks like: Android restarts the service within a second, the listener
  reconnects, and the counts are rebuilt from what is still showing. Both levels pass that.
  `requestRebind` is only a promise to un-snooze a listener the app itself unbound, which is the
  path that matters here: badges turned off and on again.
- **The document has several `enabled` keys** (the clock, the date, the battery), so a check for
  the badge setting has to read its own block, not grep the file.
- **`cmd notification` does not exist on API 26**, so the harness grants access by writing
  `enabled_notification_listeners` — which is the same secure setting the app reads there, and
  writing it makes the framework bind the listener straight away.
- **API 26's `dumpsys package` does not print a component's permission**, so the guard on the
  service is checked in the compiled manifest with `aapt2 dump xmltree` instead of on the device.
- **A drawn badge is invisible to everything that reads the screen.** Giving the row a content
  description fixed that for screen readers and, as a side effect, made every count in this
  harness readable.

## What a review of this phase found

Four readers went over the phase from different angles and every finding was then handed to a
skeptic told to refute it. Three survived, and all three are fixed here. Four were refuted, two of
them convincingly enough to be worth recording: a claimed integer overflow in the counter needs an
app to post two notifications with `number` within 999 of `Integer.MAX_VALUE`, and the claim that
notifications keep arriving while the listener asks to be unbound is wrong about the framework,
whose base class stops dispatching to the subclass inside `requestUnbind()` itself.

1. **The badge was drawn against the edge of the screen** (high). A `TextView` puts a start or end
   compound drawable at its own padding edge and never consults the text's gravity, so a full-width
   row with the shipped centred default left about 300 pixels between the name and its count. Rows
   are now as wide as their name, which is [ADR 0003](../decisions/0003-home-rows-are-as-wide-as-their-name.md).
   The harness had not caught it because it reads counts from the content description and never
   looked at where the badge landed; it now measures the row.
2. **A paused app kept its badge** (medium). Pausing an app (a focus mode, an app timer, a managed
   policy) hides its notifications rather than removing them, and the only word a listener gets is a
   ranking update. `BadgeHub.reranked` refreshed `canShowBadge` from that update but not
   `isSuspended`, so the rule in `BadgeCounter` silently stopped applying after the first read and
   the dimmed row kept announcing a count for notifications the shade was hiding. A ranking update
   now carries both flags. Verified on API 36 with `pm suspend`.
3. **A grant could be observed and then ignored** (medium). The one-shot that turned badges on after
   the user came back from Settings was a field on the disclosure screen, and leaving Settings with
   the Home key finishes that screen. The user would grant access and find badges still off. The
   pending hand-off now lives on the process-wide controller and is spent by whichever screen
   notices the grant first, which also means the launcher honours it without the settings screen
   existing at all. Two harness checks cover it.
