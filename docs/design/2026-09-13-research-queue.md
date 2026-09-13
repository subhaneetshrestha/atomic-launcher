# Asked for, not yet designed

> Written 2026-09-13. Five things the maintainer has asked for that are not yet researched or
> planned. Each entry says what was asked, what it collides with, and the questions research has to
> answer before any of it is designed. Nothing here is a decision; two of them contradict decisions
> already written down, and those contradictions are the point of the entry.

Running order: wallpaper sources (in progress) → widgets → these.

## 1. Wallpaper sources — *research running*

A picker of named open-licensed sources instead of "paste a URL", and a way to point at your own
collection on a source without OAuth (an open-source app cannot hold a client secret).
Preliminary: Unsplash prohibits wallpaper apps outright; Pexels carves out an exception for an app
whose primary purpose is something else; Wallhaven's *public* collections need no key at all.

## 2. Widgets — *research queued*

Three routes weighed equally: host third-party AppWidgets, make hosted widgets look like they belong,
or draw first-party panels the launcher renders itself. The gate on route 1 is package visibility —
`getInstalledProviders()` is filtered on Android 11+ and this app refuses `QUERY_ALL_PACKAGES`.

## 3. A settings menu like Pixel's, in Material

**Asked for:** the settings screens redesigned to look like Android's own Settings app — Material,
grouped, familiar.

**What it collides with.** Two things, both load-bearing:

- The README's own goal: *"no third-party UI or DI libraries, platform APIs only"*. Material
  Components brings AppCompat with it, and together they are roughly 1–1.5 MiB — three to four times
  the entire current APK, against a 2.5 MiB ceiling that exists because "lightweight" is the first
  promise on the page.
- [The 1.1 settings plan](2026-09-13-settings-1-1.md) §6 specifies the opposite aesthetic: one 20dp
  grid, no dividers, no cards, values right-aligned as swatches and glyphs. Pixel Settings is
  rounded grouped cards with an icon on every row and a large collapsing title. Both cannot be built.

**What research must answer:**

- How much of the Pixel look is reachable with platform views and drawn backgrounds — a grouped
  rounded container is a `GradientDrawable`, a switch is a platform `Switch`, a collapsing title is
  a scroll listener. Is the result honestly close, or an uncanny near-miss?
- What Material Components actually costs after R8 and resource shrinking for an app that would use
  perhaps ten of its widgets — the 1–1.5 MiB figure is the unshrunk one and needs measuring, not
  quoting.
- Whether Material's look can carry a user's theme. Every screen here is drawn in four colour roles
  the user controls, including a phosphor-green terminal theme; Material components expect their own
  colour system, and a themed Material screen that ignores the user's palette is worse than a plain
  one that honours it.
- The identity question, which is not technical: the app just acquired a typeface and a constructed
  mark of its own. Does wearing Android's Settings look strengthen it or dissolve it? A minimalist
  launcher whose settings look exactly like every other app's settings has spent its distinctiveness
  on the screen nobody opens twice.

## 4. A scrollable home list past eight rows, behind a flag

**Asked for:** when the home list is longer than eight apps, that section scrolls instead of growing;
the threshold is a setting.

**What it collides with.** `HomeListView` says why it is a plain `LinearLayout` in its own class
comment: *"no adapter, no nested scrolling to fight swipe gestures"*. Swipe up and swipe down are
bound by default to search and the notification shade. A scrolling region inside a surface that
interprets vertical drags as gestures is the single hardest interaction problem in this app.

**What research must answer:**

- How a scrollable list and vertical swipe gestures coexist: does the list consume the drag only when
  it has somewhere to scroll, does the gesture win at the ends of travel, or does scrolling need its
  own start condition (two fingers, a slower drag, a long press)? Name the API —
  `requestDisallowInterceptTouchEvent`, `NestedScrollingChild`, or a hand-written conflict rule in
  the existing `GestureRecognizer`.
- What happens to the 16-row cap in `HomeLimits.MAX_ROWS` — scrolling implies the cap should rise,
  and the cap is what keeps the home screen a short list of names rather than a drawer.
- Whether the threshold is a number or a consequence: eight rows on a small phone at 32sp fills the
  screen, sixteen on a tablet at 18sp does not. A flag that says "scroll past 8" is a worse answer
  than "scroll when it does not fit", and the request may really be the second one.
- Whether a scrolled home screen can still return to rest when you come back to it, which is what
  makes a launcher feel like a home rather than a document.

## 5. Letter gestures, hidden apps, and a lock

**Asked for:** draw a letter — an *S* — to open a chosen app, including apps hidden from the list;
and put hidden apps behind biometrics, a pattern, a PIN or a password.

**What it collides with:** nothing yet, which makes it the most straightforwardly buildable of the
five. It does add a surface that has to share the screen with ten existing gestures.

**What research must answer:**

- Stroke recognition without a dependency: the platform's `android.gesture` package
  (`GestureOverlayView`, `GestureLibrary`, `GestureStore`) has existed since API 4 — is it still
  supported and does it behave on current Android? Against it, a `$1 Unistroke Recognizer` is about
  150 lines of pure Kotlin, would live in `core/` and be JVM-testable like `core:search`, and carries
  no platform risk. Which is the better answer here, and how accurate is each on single letters drawn
  by a thumb?
- How a drawing surface coexists with four swipes, four long swipes, a double tap and a long press.
  A letter starts as a drag, and a drag is already four different gestures. Does drawing need an
  explicit entry (a gesture that opens the canvas), or can a recogniser arbitrate a stroke after the
  fact?
- Authentication at minSdk 26: `BiometricPrompt` is platform API 28, `setAllowedAuthenticators` with
  `DEVICE_CREDENTIAL` is API 30, and API 26–27 needs
  `KeyguardManager.createConfirmDeviceCredentialIntent`. `androidx.biometric` unifies all of it and
  is the same family as `androidx.core` and `androidx.activity`, which this app already uses — what
  does it cost in bytes, and is that consistent with "no third-party UI libraries"?
- What "hidden" must mean once it is locked. Today a hidden app is absent from the home list and the
  drawer. If a letter gesture can open one, the lock has to sit on the *launch*, not on the list, or
  the letter becomes a way around the lock. Also: what leaks a hidden app anyway — the app menu,
  search results, the badge counts, the backup file, recents.
- Whether the lock is per-app or one lock over all hidden apps, and where the secret lives given the
  settings document is exported by the backup feature.
