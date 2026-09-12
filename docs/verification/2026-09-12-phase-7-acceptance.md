# Phase 7 acceptance — 2026-09-12

Scope: themes as things people make and pass around. An editor for everything a theme holds, four
ways to move one between phones — a file, a share, a link, the document picker — and a screen that
shows what an arriving theme is before it is allowed to change every colour on the screen.

## JVM tests (every push, `./gradlew test`)

| Seam | Tests | What they pin |
|---|---|---|
| `ThemeLink` | 9 | a theme goes into a link and comes back out unchanged; the link is shorter than the document it carries; an address in a link is carried, not followed, and is unescaped once; a plaintext address in a link is refused; a link that is not ours is not ours; a link with nothing in it says so; something that is not base64, or not deflated, is refused; fifty compressed bytes that would inflate to a megabyte are refused |
| `ThemeImport` | 6 | a theme introduces itself by name, author and licence; one that would download images names the host; a collection the fetcher would refuse is cleared and the reason shown; a theme from a newer app is refused rather than guessed at; a theme made for a newer app *version* is applied with a warning; something that is not a theme at all is refused |
| `ThemeEdits` | 6 | a value typed out of range is pulled into it as it is typed; changing a built-in makes the theme the user's own while keeping its name; an edit that changes nothing leaves the built-in alone, including the same colour written another way; a colour that is not a colour is refused rather than stored; which built-in is showing, if any; cleaning says what it corrected |
| `BuiltinContrast` | 2 | every built-in is readable in the day and at night, names and info lines alike, at the 4.5:1 that small text needs; the ratio is the one WCAG defines |

23 tests added, 245 across the project, all green. ktlint and Android lint clean. Release APK
350,902 bytes unsigned against the 2.5 MiB budget, up 21 KB.

The contrast test found a real fault the moment it existed: **Paper's info lines were 4.4:1**, just
under readable, because dark ink on warm paper needs more of itself at 60 % than white on black
does. Paper's secondary colour is now 70 % ink, which is 5.9:1.

## Emulator checks (`scripts/verify-themes.sh`, debug build)

Colours are read off a screenshot. Themes arrive the three ways they will in the wild: a file
opened by a file manager, a link opened by a browser, and a document handed over by the picker.

| Check | API 26 | API 36 |
|---|---|---|
| The four built-in themes are offered | pass | pass |
| Paper is light, and its names are dark on it | pass | pass |
| Terminal is nearly black | pass | pass |
| The choice is written into the document | pass | pass |
| Material You resolves from the device on Android 12+, and falls back below it | pass | pass |
| The editor lists what a theme holds | pass | pass |
| Editing a built-in makes the theme your own | pass | pass |
| And the home screen is painted with the edit | pass | pass |
| A size out of range is pulled into it | pass | pass |
| A theme file opens the import screen | pass | pass |
| The theme says what it is called, and who wrote it | pass | pass |
| Nothing is applied by opening it | pass | pass |
| Applying it writes it into the document, and the home screen changes | pass | pass |
| A link carries the whole theme, and applies | pass | pass |
| A link that carries nothing usable says so | pass | pass |
| A theme from a newer atomic is refused, not guessed at | pass | pass |
| A theme that would download images names the host | pass | pass |
| A plaintext address is corrected, and the correction is shown | pass | pass |
| And no host is named, because nothing will be downloaded | pass | pass |
| Every built-in still lays out at 200 % text | pass | pass |
| No crashes anywhere in the run | pass | pass |

29 of 29 on both levels.

Phase 1 to 6 harnesses re-run on the same build:

| Harness | API 26 | API 36 |
|---|---|---|
| `verify-home.sh` (Phase 1) | PENDING26 | PENDING36 |
| `verify-settings.sh` (Phase 2) | PENDING26 | PENDING36 |
| `verify-gestures.sh` (Phase 3) | PENDING26 | PENDING36 |
| `verify-search.sh` (Phase 4) | PENDING26 | PENDING36 |
| `verify-badges.sh` (Phase 5) | PENDING26 | PENDING36 |
| `verify-background.sh` (Phase 6) | PENDING26 | PENDING36 |

## What the emulators could not answer

- **Writing a theme out through the document picker.** `CreateDocument` opens the system picker,
  which cannot be driven from a script the way the launcher's own screens can. The bytes it writes
  are the same ones the share and the link carry, and those are checked; the picker itself is
  verified by hand.
- **A share arriving from another app.** `ACTION_SEND` with a `content://` URI needs a second app
  holding a theme file. The same reader handles it, and the file and link paths exercise it.
- **A theme fetched from `atomic://theme?url=…`.** The confirmation naming the host is shown, but
  the fetch itself needs a server with a theme on it; the fetcher underneath is the one Phase 6
  tests against a real TLS server.

## What we learned

- **A soft keyboard swallows backspace.** `input keyevent KEYCODE_DEL` deletes nothing while an IME
  is up, so a harness that clears a field by pressing backspace forty times is really typing into
  whatever was already there — which is how a colour became `#FF000000#FF102030` and was rightly
  refused. Dialogs now open with their value selected, which fixes the harness and is what a person
  wants from a field holding a colour or a size.
- **`input keyevent` takes several keycodes and does not deliver them all.** One press per call.
- **A uiautomator dump is one line**, so occurrences have to be counted with `grep -o`, not `grep -c`.
- **Where the empty part of the screen is depends on the theme.** Terminal puts its names at the
  top and Paper at the bottom, so the long press that opens settings has to be aimed at a gap
  worked out from the screen rather than at a fixed point.
