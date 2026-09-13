# ADR 0005 — the app may look for its own updates

Date: 2026-09-13. Status: accepted. Supersedes decision 5 of
[ADR 0004](0004-github-only-distribution.md) ("Updates are not automatic … the app never updates
itself") and amends the opening claim of [`docs/privacy.md`](../privacy.md) ("It makes no network
request of its own").

## Context

One channel means one way in: a person hears there is a new version, opens a browser, finds the
release page, downloads an APK and installs it. [Obtainium](https://github.com/ImranR98/Obtainium)
does this for people who have heard of Obtainium. Everyone else runs whatever they installed in
whichever month they installed it, which for a launcher — the app that is on screen every time the
phone is unlocked — is a long time to sit on a fixed bug.

ADR 0004 said the app never updates itself. That was the right call for a v1 whose release
engineering did not exist yet. It is the wrong call now that a signed release is one tag away, and
the maintainer has asked for the app to carry its own updates.

Two things constrain what "auto update" can mean here, and neither is negotiable:

- **Android will not let a sideloaded app update silently.** Installing an APK requires
  `REQUEST_INSTALL_PACKAGES` and a `PackageInstaller` session, and the system shows its own
  confirmation every time. There is no unattended path outside a device owner or an app store.
- **atomic refuses `POST_NOTIFICATIONS`** — it is on the forbidden list in
  `scripts/check-manifest-policy.sh` and the build fails if it appears. So a finished check cannot
  tap anyone on the shoulder. Whatever it learns has to wait until the app is looked at.

## Decision

1. **The app may ask GitHub whether there is a newer release**, and may download and hand that APK
   to the system installer. It never installs anything without the person confirming Android's own
   dialog, because it cannot.
2. **Two ways to ask.** A `Check for updates` row in About, which asks once, when tapped. And an
   opt-in periodic check — off until turned on — reusing the existing `JobScheduler` plumbing with
   its own job id, on unmetered networks, at most daily.
3. **What it may say, and where.** A found update shows as one quiet line at the foot of the home
   screen, the same mechanism `DefaultHomeBanner` already uses for "set atomic as your home app",
   and as a row in Settings. Never a notification; never an interruption.
4. **`REQUEST_INSTALL_PACKAGES` joins the manifest allowlist** in `check-manifest-policy.sh`, with
   its reason written beside the other five. It is the only new permission, and the gate is updated
   deliberately rather than being taught to look away.
5. **The channel follows the build.** A release build asks for `/releases/latest`; an `.edge` build
   asks for the `edge` prerelease. The two have different application ids and different version
   code lineages and must never offer each other.
6. **What is downloaded is checked before it is offered**: the `SHA256SUMS` published beside the APK
   is fetched and compared, and the installer enforces that the signature matches the installed app.
   A mismatch is reported and the file is deleted.
7. **The promises are rewritten rather than quietly dropped.** `docs/privacy.md` gains the update
   check by name in "What leaves the device" — what is sent (a plain GET to `api.github.com`, no
   identifiers beyond what any HTTP request carries), when, and how to turn it off. The README stops
   saying the app never checks for updates. Obtainium stays documented and supported; this is an
   alternative for people who do not use it, not a replacement.

## Consequences

An unattended update is still impossible, so the feature is honestly "tell me, then make it two
taps" rather than "keep itself current". That is the whole of what Android allows here, and the
wording in the UI says so rather than implying more.

The app now makes a network request that no one asked for, on a schedule, if the periodic check is
enabled. That is a real change to what this app is, which is why it is off by default, why the
disclosure lives in `privacy.md` beside the wallpaper fetcher rather than in a changelog line, and
why the manual check exists at all — someone who wants updates but no background traffic can have
exactly that.

`api.github.com` is rate limited to 60 requests an hour per IP for unauthenticated callers. One
check a day is nowhere near it, and the check carries no token — a token would be a credential in a
settings file that the backup feature exports.
