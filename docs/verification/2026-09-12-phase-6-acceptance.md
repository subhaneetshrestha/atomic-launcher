# Phase 6 acceptance — 2026-09-12

Scope: what is behind the app names. Four answers — the theme's colour, a gradient, the device
wallpaper showing through the window, and images fetched from an address the user chose — plus the
machinery the last one needs: reading a collection, choosing what comes next, fetching it without
spending anybody's mobile data, decoding it without spending the phone's memory, and deciding
whether the names over it should be black or white.

Nothing is fetched until an address is entered. The launcher declares `INTERNET` and
`ACCESS_NETWORK_STATE` from this phase on, and makes no request at all with the setting untouched.

## JVM tests (every push, `./gradlew test`)

| Seam | Tests | What they pin |
|---|---|---|
| `UrlRules` | 6 | only https is fetched, and a plaintext, file or relative address is refused; credentials in the authority and an address over 2048 characters are refused; cleaning trims, drops, deduplicates and cuts to 500; a relative address resolves against the document it was found in, and resolving cannot smuggle in a plaintext hop; an image is recognised by its extension, not by its query string |
| `SourceDetector` | 5 | the server's `Content-Type` is believed first; an unhelpful one falls through to the bytes, where JPEG, PNG, GIF, WebP and AVIF announce themselves; text documents are told apart by their first character; a byte-order mark does not hide it; an address ending in an image extension needs no fetch to be recognised |
| `CollectionParser` | 9 | a text list takes one address a line and ignores comments; a JSON array is read as it stands; Wallhaven's answer needs no parser of its own; a document of extensionless addresses falls back to the fields that name addresses, and says so; JSON that is not JSON comes back empty with a reason; RSS enclosures, Media RSS items and Atom enclosure links are all found while the article link and the audio enclosure are not; a feed that declares a document type is not read at all; a feed that breaks halfway keeps what it read; a direct image is a collection of one |
| `Wallhaven` | 6 | a pasted search keeps its query and is forced to the tame images; an API key in the address is never carried over and the pasted purity is replaced rather than merged; the listing pages carry their own sorting; a single wallpaper page becomes the request for that wallpaper; an address that needs an account is refused rather than guessed at; a parameter that could reshape the request refuses the whole address |
| `Rotation` | 13 | an index is trusted for six hours and only for the address it came from; the first run is always due and a clock that went backwards does not stall it; in order the list is walked and wraps; shuffled, what was shown lately is skipped until there is nothing else; an address that failed for good is never offered again; a success clears the failure count; history keeps the last twenty without repeats; a permanent failure remembers the address and a passing one only delays; the bad list is a ring; waiting doubles with each failure and stops at a day; a stored retry time far in the future does not park the background forever |
| `ImageFetcher` | 13 | against a real https server: a list comes back with what the server called it; 404 is permanent and 500 is not; five redirects are followed and a loop is not; a redirect off https is refused; a list over 1 MiB and an image over 15 MiB are refused, by what the server declares and by what it actually sends; an image arrives whole; an empty answer is a failure rather than an empty wallpaper; an unchanged list costs only its headers; a download that stops short of the length the server promised is the network's fault and not the address's; the first bytes are kept as they arrived so a signature survives; an address the rules refuse never reaches the network |
| `ColorSampler` | 7 | a white image asks for black text and dark icons and a black one the opposite; each strip is asked separately, so a bright sky can want dark status-bar icons while the names below it still need white; the dim the launcher draws is part of what the text sits on; only the part of the image that survives the centre crop is read; an empty or mismatched buffer is no answer at all |
| `Background` theme block | 6 | a theme with no background section gets the plain colour one; a collection round-trips through the document; an address the fetcher would refuse is cleared, and says so; an interval below what Android will schedule is pulled up to it; dim stays between none and black and an angle is turned back into a circle; an unknown mode falls back to the plain colour rather than failing the document |

65 tests added, 222 across the project, all green. ktlint and Android lint clean. Release APK
329,318 bytes unsigned against the 2.5 MiB budget, up 36 KB; the release classpath is still free of
Google Play services.

The fetcher is tested against a real TLS server (`com.sun.net.httpserver.HttpsServer` with a
keystore in the test resources), because every rule it enforces is a rule about how somebody else's
server behaves. That needed `unitTests.isReturnDefaultValues`, so that the one Android call in it —
a log line — answers instead of throwing.

## Emulator checks (`scripts/verify-background.sh`, debug build)

Colours are read off a screenshot: uiautomator reports what a view says, never how it is painted.
Each check takes the median brightness of a band of the screen, so a glyph does not stand in for
the background behind it. The collection is two solid images and a list pointing at them, served
from this repository over https, so fetch, parse, decode and display are tested against a real
server rather than a mock.

| Check | API 26 | API 36 |
|---|---|---|
| The ink theme paints the screen black | pass | pass |
| Nothing is scheduled before a collection is set | pass | pass |
| All four backgrounds are offered | pass | pass |
| Choosing the gradient offers its two colours and a direction | pass | pass |
| The gradient runs top to bottom and is not the same at both ends | pass | pass |
| The wallpaper choice sets `FLAG_SHOW_WALLPAPER` on the window | pass | pass |
| The choice is written into the document | pass | pass |
| The collection settings appear when it is chosen | pass | pass |
| A plaintext address is refused and never stored | pass | pass |
| The host the images would come from is named before the address is accepted | pass | pass |
| The address is stored once accepted | pass | pass |
| Setting a collection schedules a periodic job | pass | pass |
| An image is fetched from a real https server | pass | pass |
| The list is read and cached in the state document | pass | pass |
| The image is on screen | pass | pass |
| The names go dark over the bright image | pass | pass |
| Trimming caches leaves the current image alone | pass | pass |
| Six rotations, six fresh decodes, do not add up | pass | pass |
| The image is let go of when the system says it is short | pass | pass |
| And is decoded again when the launcher comes back | pass | pass |
| Turning the collection off brings the theme colour back | pass | pass |
| And cancels the job | pass | pass |
| A restart paints the same background | pass | pass |
| No crashes anywhere in the run | pass | pass |

25 of 25 on both levels.

What it costs, measured with the bright image on screen (a debug build with no R8, StrictMode on,
drawn by a software renderer that keeps every pixel in the native heap):

| | API 26 | API 36 |
|---|---|---|
| One screenful of pixels | 8,100 KB | 8,100 KB |
| Native heap with the image up | 25,676 KB | 22,892 KB |
| Total PSS with the image up | 51,948 KB | 67,591 KB |
| Growth over six decodes, each a fresh one | 6,932 KB | −51 KB |
| Handed back when the system said it was short | nothing (see below) | 4,808 KB |

Six decodes that were never let go of would be six screenfuls. Android 8 shows one, which is its
native allocator keeping the high-water mark of a bitmap it has already freed; Android 16 shows
none at all. Before the fix in this phase, the same six decodes carried 15,383 KB — two screenfuls
and climbing.

Phase 1 to 5 harnesses re-run on the same build:

| Harness | API 26 | API 36 |
|---|---|---|
| `verify-home.sh` (Phase 1) | 18 of 18 | 18 of 18 |
| `verify-settings.sh` (Phase 2) | 29 of 29 | 29 of 29 |
| `verify-gestures.sh` (Phase 3) | 24 of 24 | 24 of 24 |
| `verify-search.sh` (Phase 4) | 19 of 19 | 19 of 19 |
| `verify-badges.sh` (Phase 5) | 49 of 49 | 52 of 52 |

## What the emulators could not answer

- **Hardware bitmaps.** Both emulators draw with a software renderer, so the decoded image lands in
  the native heap and `Graphics` reads nought. On a phone from Android 9 the pixels go to graphics
  memory instead and never enter the app's heap at all. The budget in the plan — Graphics no more
  than 1.25 window-fuls — needs a phone to read.
- **Whether the allocator hands memory back.** Android 8 frees a recycled bitmap into its own heap
  and does not return the pages to the system, so the trim check reads the launcher's own word for
  it (a debug log line) rather than a number from `dumpsys`. Android 16 gives about half a
  screenful back immediately.
- **A metered connection and Data Saver.** An emulator reports wi-fi and cannot be made to report a
  metered mobile connection, so the two skip rules are pinned by the unit tests and the reasons
  they record, not by a device that refused to fetch.
- **A six-hour wait.** The scheduled job is verified to exist, to carry the right period and to be
  cancelled with the setting; Android decides when it actually fires, and forcing it with
  `cmd jobscheduler run -f` runs the engine, which then correctly says it is not yet due.
- **Timeouts.** Ten and twenty seconds are too long for a test suite to sit through; the values are
  set on the connection and read back only by inspection.

## What we learned

- **A screenshot is the only way to see a colour**, and a single pixel is not a colour: the first
  run of this harness sampled the middle of the screen and read 242, which was a letter of an app
  name, not the background. Every check now takes the median of a band, and the text checks take
  the darkest point in the band holding the row.
- **Android 8 prints window flags as one hex number** and calls the last row of its memory summary
  `TOTAL` rather than `TOTAL PSS`. Both are now read either way round.
- **`am send-trim-memory` needs the app to be off screen**, which is also the honest way to test it:
  the launcher is trimmed precisely when somebody is using another app.

## What reading the phase back found

Earlier phases were reviewed by several readers in parallel with each finding handed to a skeptic;
this one was read back once, carefully, by the same hand that wrote it. That is a weaker check and
worth saying so. Four defects came out of the emulator runs and four out of the reading; all eight
are fixed here.

From the emulator:

1. **The private directory was asked for on the main thread** while the home app was starting, which
   the Phase 1 harness caught as a StrictMode report. The store and the engine now wait until
   something needs them, which is always the I/O thread.
2. **An image handed back to a system short of memory never came back.** The run on resume only
   asked whether it was time for a new one, so the screen stayed bare until something else — a
   rotation, a settings change — happened to decode it again.
3. **It was handed back too readily.** Being off screen is not being short of memory, and the
   launcher is back the moment an app is closed. The image now goes when the device is actually
   short, or when this process is far enough down the list to be at risk.
4. **On Android 8 the pixels were never let go of.** A bitmap there is native memory freed only when
   the object is collected, so six rotations were carrying six screenfuls. The view now recycles an
   image the moment it stops showing it — which it can do safely, because by then the controller is
   already holding its replacement — and the home screen keeps listening while it is off screen, so
   a trim arriving then is acted on rather than queued.

From the reading:

5. **A signature could not survive being read as text.** The body was decoded as UTF-8 before the
   first bytes were sniffed, so an image served as `application/octet-stream` from an address with
   no file extension would have been read as a list of addresses and come back empty. The first
   bytes are now kept as they arrived.
6. **A download that stopped short was accepted.** A broken connection would have produced a file
   that failed to decode, and the address would have been marked permanently bad for it. The length
   is now checked against what the server promised, and a short read is the network's fault.
7. **Half an image could become the wallpaper.** `ImageDecoder` was told to accept a partial decode.
   It is now told not to, which is safe to do only because a truncated download no longer reaches
   it: a file that arrived whole and still will not decode is not an image.
8. **Changing the collection left the old image on disk** with no state pointing at it, where it
   would have sat until the app was uninstalled. The store now forgets the images with the state.
