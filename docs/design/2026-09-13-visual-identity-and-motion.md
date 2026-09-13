# Visual identity and motion

> Written 2026-09-13, after v1 was feature-complete, from a read of the shipped UI code. Phases 1,
> 2, 3 and 4 were implemented the same day; §8 records what the code does differently from the plan
> above it, and why. Where this and the code disagree, the code is authoritative.

atomic is a launcher made entirely of text. There is no chrome, no icon grid, no card, no colour
beyond four roles. That means every identity decision the product has is a typographic one, and
every one it has not made is being made for it by the OEM's font.

## 1. What is there now

**Home** is atomic's own: `ThemeApplier` puts the theme's family, weight, size, colour and
alignment on every row, the clock and the info lines (`home/ThemeApplier.kt:33`).

**Settings is not.** `RowAdapter.style()` sets a colour and a size and never touches the typeface
(`settings/RowAdapter.kt:95`), over `android.R.layout.simple_list_item_2` and friends
(`settings/RowAdapter.kt:60`). So the theme's serif or monospace stops at the home screen, and
eighteen screens of the app are drawn in whatever the vendor ships — Roboto here, One UI Sans
there. Half the product does not look like the product.

**Type scale**, as built: settings title 22sp, row 18sp, detail 14sp, header 13sp uppercase;
home 24sp, drawer 20sp, clock 48sp, info 14sp. Two unrelated scales with no ratio between them,
and no tracking anywhere (`grep letterSpacing` returns nothing).

**Motion**, in the whole app: one crossfade on the wallpaper (`background/BackgroundView.kt:91`).
Everything else is a hard cut.

- A settings screen change is `removeAllViews()` then `addView()` (`settings/SettingsActivity.kt:253`)
  — eighteen screens deep and nothing ever tells you which direction you went.
- Editing any theme colour calls `recreate()` on the whole activity
  (`settings/SettingsActivity.kt:130`), which flashes, and needed a `restoreScroll` field to stop
  the list jumping to the top (`settings/SettingsActivity.kt:112`). The worst moment in the app is
  in the screen whose whole job is to look good.
- Search — the most-used surface there is — appears with `visibility = VISIBLE`.

**Smaller things.** Disabled rows sit at `alpha = 0.45` over text that is already secondary, which
lands under 3:1 on Ink and Paper. `row.label.uppercase()` uses the device locale, so a Turkish
phone renders "Info lines" as "İNFO LİNES".

**Budget.** The release APK is **348 KiB of the 2.5 MiB ceiling**. There is 2.1 MiB of room, and
the reason to spend some of it is on this page.

**The mark already exists.** The launcher icon is three rounded lines, 40/28/34 wide on a 12-unit
pitch (`res/drawable/ic_launcher_foreground.xml`) — the home list, drawn. The identity below is
that mark's logic applied to the rest of the app, not a new one.

## 2. The one decision: the app has a typeface

A text-only launcher cannot have its own identity while borrowing the system's font. Everything
else in this document is a refinement; this is the decision.

**Recommended: the IBM Plex superfamily, OFL 1.1.** It falls out of the theme schema that already
exists — `Typography.family` is `sans-serif | serif | monospace`, and Plex ships Sans, Serif and
Mono drawn as one family. Ink becomes Plex Sans, Paper becomes Plex Serif, Terminal becomes Plex
Mono, You keeps the system font because that is its whole point. One choice, four themes, no new
schema.

Ship **Plex Sans first, alone**, as a variable font (`wght` 100–700) subset to Latin, punctuation
and digits: one file, every weight the editor offers, ≈60–90 KB. Serif and Mono follow only if
Paper and Terminal look worse without them. The system families stay in the list; a user who wants
their OEM font keeps it.

Alternatives, rejected: a neutral grotesque (Inter, Public Sans) buys neutrality, not identity, and
neutrality is what the OEM font already gives; shipping nothing and doing this with rhythm alone
(§3–§5) is real, costs zero bytes, and still leaves the product looking like four different phones.

Mechanics: `res/font/atomic_sans.ttf`, a branch in `ThemeApplier.typeface()` for the bundled ids
built with `Typeface.Builder(...).setFontVariationSettings("'wght' $weight")` (API 26, so it works
at minSdk), a `Typeface` cache keyed by weight + slant, the new ids appended to `FAMILIES`, the
licence credited on the About screen beside the existing ones, and `checkReleaseApkSize` re-run to
show what it cost. Themes that name a family we do not ship still resolve through `Typeface.create`
exactly as today, so no existing `.atomictheme` file breaks.

## 3. Type system

One scale for the whole app, in the ratio the icon already uses (the three lines are 6 tall on a
12 pitch; everything here is a multiple of 4).

| Role | Size | Weight | Tracking | Where |
|---|---|---|---|---|
| Clock | 48sp | 300 | −0.02em | home |
| Screen title | 24sp | 400 | −0.01em | settings header |
| Home row | 24sp | 400 | 0 | home list |
| Row | 18sp | 400 | 0 | settings, drawer |
| Detail | 14sp | 400 | 0 | settings second line |
| Section header | 13sp | 500 | **+0.08em** | settings headers |
| Info line | 14sp | 400 | 0 | date, battery, screen time |

Two changes carry most of the effect: negative tracking on the large sizes, which is the difference
between a clock that looks set and one that looks typed, and positive tracking on the uppercase
headers, which is the single most common tell of an unstyled Android app. Both are one line on a
`TextView` (`letterSpacing`, in ems, API 21).

Prose keeps its 1.2 line spacing but gains a measure: `maxWidth` of about 66 characters, so
`page()` does not run to 100 characters on a tablet (`settings/SettingsActivity.kt:282`).

## 4. Motion

Rules, in order of importance:

1. **Motion explains where you went.** Nothing moves for decoration; every animation below answers
   "what just happened" or "which way is back".
2. **Nothing waits for motion.** Every value below is under 260ms, and every animation is on
   transform and alpha only.
3. **Reduced motion is free and must not be skipped.** `ViewPropertyAnimator` and `ValueAnimator`
   already honour the system animator scale, so a phone with animations off gets 0ms — but any
   animation whose *listener* does the real work must also run its end state under
   `ValueAnimator.areAnimatorsEnabled()`.

A single `ui/Motion.kt` holds the constants and three extension functions. No library, no
framework, no per-screen curves.

| Where | What | Duration | Curve |
|---|---|---|---|
| Settings push | new screen in from +12dp x, alpha 0→1; title crossfades | 180ms | `linear_out_slow_in` |
| Settings pop | old screen out to +12dp x, alpha 1→0 | 140ms | `fast_out_linear_in` |
| Theme colour edit | every colour role tweens to its new value | 220ms | `fast_out_slow_in` |
| Search open | overlay alpha 0→1, field +8dp→0 | 160ms | `linear_out_slow_in` |
| Search close | alpha 1→0 | 120ms | `fast_out_linear_in` |
| Row press | alpha to 0.6 and back, with the haptic already there | 120ms | `linear` |
| Badge change | scale 1→1.15→1 on the drawable | 140ms | `fast_out_slow_in` |

Deliberately still: the clock, the date, the battery and the screen-time line. A launcher whose
numbers move is a launcher you keep looking at, which is the opposite of this one's point.

## 5. Settings, beyond motion

- **The title becomes the way back.** Eighteen screens deep, the only way out is the system
  gesture. Prefix the title with `‹` whenever `stack.size > 1` and make it tap to `pop()`:
  three lines in `show()`, and the header stops being decoration.
- **Stop recreating the activity to change a colour.** Tween the four roles, restyle the visible
  rows, and the `restoreScroll` field and its `onSaveInstanceState` entry delete themselves.
- **The rows get the theme's typeface** — `RowAdapter` takes the `FontSpec` and sets it, so the
  editor previews the font while you pick it.
- **Disabled rows** move from `alpha = 0.45` to the secondary colour blended 55% toward the
  background, which keeps them under 4.5:1 on purpose but above 3:1 everywhere.
- **`uppercase(Locale.ROOT)`**, for the Turkish dotted I.

## 6. Order of work

| Phase | Contents | New bytes | Status |
|---|---|---|---|
| 1 | `Motion.kt`, push/pop, title-as-back, theme typeface in settings, tracking, `Locale.ROOT`, disabled contrast | 0 | done |
| 2 | Colour tween replaces `recreate()` | 0 | done |
| 3 | Bundled typeface, `FAMILIES`, About credit, size gate re-run | +69 KB in the APK | done |
| 4 | Search open/close, row press, badge pop | 0 | done |

The release APK went from 348 KiB to 434 KiB, against a 2.5 MiB ceiling.

Acceptance goes where the rest does: an added section in a `docs/verification/` note, screenshots of
Ink/Paper/Terminal/You before and after, and one pass of each existing harness on a device with
animations forced off, since that is the configuration where a motion bug hides a missing end state.

## 7. Not doing

Compose, RecyclerView, a motion framework, custom easing curves, a new icon, shadows, elevation,
rounded rectangles, an accent colour with a second hue in it, and a splash screen.

## 8. What the code does differently

**The bundled face is Android 10 and up.** A `Typeface` built from a file has no fallback chain of
its own: an app named in Japanese, Greek or Hindi would come back as a row of empty boxes, and only
`Typeface.CustomFallbackBuilder` (API 29) can give one back. Below that, `ThemeApplier.bundled()`
returns null and the device's own sans is used, which is what every theme did until today. The Ink
theme therefore looks slightly different on Android 8 and 9, and nothing else changes there.

**The font in the tree is 124 KB and adds 69 KB to the APK.** IBM Plex Sans Var Roman, `wdth` pinned
to 100 (nothing here asks for condensed), subset to Latin, Latin-1, Latin Extended-A, punctuation
and currency, unhinted. Everything outside that subset comes from the system fallback, so the subset
costs coverage nowhere. The licence ships beside it in `res/raw/ofl_ibm_plex_sans.txt` and is
credited on the About screen. `docs/themes/ink.atomictheme` was regenerated by its own test.

**Italic on the bundled face is synthetic.** The italic variable font is another 60 KB for a setting
that is off by default; `Typeface.create(face, weight, true)` slants it instead. Ship the real one if
an italic theme ever looks wrong enough to care about.

**`restoreScroll` stayed.** A theme edit no longer rebuilds the activity, but a rotation and a
night-mode change still do, and the list still owes the reader its place.

**A press scales, it does not fade.** A suspended app is already drawn at `alpha = 0.5`, so an alpha
press would have fought it. `Motion.pressScale()` is a `StateListAnimator` on scale: the framework
handles press, release, and the cancel that a swipe gesture turns a press into.

**Only a person's close animates.** Back and a tap on the empty space fade the search overlay out;
`onStop` and the Home key take it away at once, because nothing is watching a fade on a screen that
is already going. `isOpen` goes false the moment a close is asked for either way, so the home screen
is ready for the next gesture before the pixels have caught up.

**Still owed:** the acceptance note, and an assertion in `verify-badges.sh` for the arrival pop.
Everything here was checked with `ktlintCheck test lintRelease assembleRelease checkReleaseApkSize
gmsGuard` and `scripts/check-manifest-policy.sh`, which is not the same as having been looked at on
a phone.
