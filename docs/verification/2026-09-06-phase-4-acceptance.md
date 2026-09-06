# Phase 4 acceptance — 2026-09-06

Scope: fuzzy search over app names and the list of every app, and the two launcher actions they turn on. Swipe up opens the search with the keyboard; the drawer action opens the same list without it.

## JVM tests (every push, `./gradlew test`)

| Seam | Tests | What they pin |
|---|---|---|
| `Normalizer` | 6 | Latin accents come off; case folds the same whatever language the phone is set to, so a Turkish phone does not turn I into a dotless one; other scripts pass through, and marks that are letters in their own right, such as the vowel signs of Devanagari, are kept; punctuation becomes one space; words start after a space, at a capital and at a digit; every character remembers where it came from |
| `Scorer` | 7 | every letter typed must appear in order; a label the query begins beats scattered word initials, which beat letters buried inside words; two letters from inside a word score below the floor and do not match at all; the same match earlier in the label wins; longer queries reach inside words; accents and case do not matter |
| `SearchIndex` | 6 | nothing typed lists every app in the order the caller sorted them; results are best-first and capped; a hidden app stays out until its whole name is typed; a renamed app answers to both names and appears once; equal matches come out in a settled order; ten queries over a thousand apps take about five milliseconds |
| `SearchConfig` | 1 | the decided defaults: one match opens itself, the keyboard comes up, no web search |

20 tests in `:core:search` and one in `:core:theme`, and 110 across the project, all green; ktlint and Android lint clean. Release APK after Phase 4: 273,490 bytes unsigned, against the 2.5 MiB budget; the classpath is still free of Google Play services.

## Emulator checks (`scripts/verify-search.sh`, debug build)

| Check | API 26 | API 36 |
|---|---|---|
| Swipe up opens the field, with the keyboard | pass | pass |
| Nothing typed lists every app | pass | pass |
| Two letters narrow the list and drop what cannot match | pass | pass |
| One match left opens by itself, with no tap | pass | pass |
| Enter opens the best match when several are left | pass | pass |
| Back puts the keyboard away, then closes the search | pass | pass |
| A swipe inside the search fires no gesture | pass | pass |
| The drawer action opens the same list without the keyboard | pass | pass |
| A hidden app stays out of a part-way match, and its whole name still finds it | pass | pass |
| A renamed app answers to the new name and the one it came with | pass | pass |
| No crashes during the run | pass | pass |

19 of 19 on both levels. Phase 1, 2 and 3 harnesses re-run on the same build, with no failures anywhere:

| Harness | API 26 | API 36 |
|---|---|---|
| `verify-gestures.sh` (Phase 3) | 24 of 24 | 24 of 24 |
| `verify-settings.sh` (Phase 2) | 29 of 29 | 29 of 29 |
| `verify-home.sh` (Phase 1) | 16 of 16 | 19 of 19 |

The home harness runs fewer checks on API 26: the per-app locale it uses to prove labels are
re-read needs Android 13, and one check needs a signed second app it did not have to hand.

## Ranking, and why

"gm" gives Gmail, then GroupMe, then Google Maps, and does not offer Instagram or Telegram at all. The order follows one rule: a label the query begins is the strongest match, then the initials of its words taken in order, then letters buried inside words. The last of those is worth so little that a two-letter query cannot reach the floor on it alone, which is what stops two letters from dragging in half the phone.

The plan drafted before this phase guessed that "gm" should put Google Maps first. Typing two letters that begin an installed app's name almost always means that app, so a prefix now wins, and the guess is recorded here as changed rather than quietly dropped.

## Two things worth knowing

Marks are not all decoration. A rule that strips every combining mark turns नेपाल into नपल, because Devanagari vowel signs are letters written as their own characters. Only the marks that fall out of pulling a precomposed letter apart, which is what an accented Latin letter is, are dropped.

Case folding cannot use the ordinary string call. In Turkish, lowercasing I gives a dotless ı, so on a Turkish phone "instagram" would stop finding Instagram. Folding one character at a time avoids the phone's language entirely.

## What a review of this phase found

The code was read as well as run, by an agent with no stake in having written it, and each finding
was then handed to a second agent asked to refute it. Five survived, all real, all in code written
this phase:

- The drawer showed only the first thirty apps: the match limit was applied to the empty query too,
  and nothing could scroll past it.
- Laying the list out from the bottom does not reverse it, so the best match sat farthest from the
  field and scrolled off the top as soon as the results overflowed.
- A rotation forced the keyboard open even on a drawer opened deliberately without one.
- A run of letters lost to scattered word initials whenever the first word was a single letter:
  "tm" gave T-Mobile before Tmall, and with one match left that opens the wrong app.
- Accents were folded only when they arrived precomposed, so a decomposed query never matched a
  precomposed label, and a hidden app with a decomposed label could not be reached at all.

The first three needed no test to see and none to fix. The last two are pinned by tests now: marks
are judged by their Unicode script, so an accent comes off however it was typed while a Devanagari
vowel sign stays, and continuing a run of letters is worth more than reaching a word initial
across a gap.

A sixth problem came out of the device runs rather than the review: the home list stayed in the
view hierarchy behind the overlay, so it was still reachable by a screen reader and still being
drawn. It is hidden while the search is up.

## What the harness taught us

Auto-launch fires the moment one match is left, which is sooner than a test naturally assumes:
typing three letters had already opened an app and closed the search, so six checks were asserting
against the wrong screen. Where a query narrows to one app, the proof that it was found is that the
app opened, not that a row is on screen.

Back with the keyboard up belongs to the keyboard. Android gives the first press to the input
method, so closing the search takes two, which is the same as every other search field on the
phone.
