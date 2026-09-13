# Privacy

atomic has no account, no analytics, no advertising identifier, no crash-reporting service and no
telemetry of any kind. It makes no network request of its own. Everything below is checkable from
outside the app — the last section says how.

## What leaves the device

Nothing, unless you ask for it, and then only to a host you typed yourself:

| When | Where to | What is sent |
|---|---|---|
| You set a background collection address | that address, and the image URLs it lists | an HTTPS GET, `User-Agent: atomic-launcher/io.github.subhaneetshrestha.atomic (Android <release>)`, plus `If-None-Match`/`If-Modified-Since` from the previous fetch |
| You open an `atomic://theme?url=…` link and confirm it | that address | the same GET |
| You send a crash report from About | your mail app, addressed to the maintainer | the report text, which you can read and edit first |

Every hop must be `https`; a redirect to plain `http` is refused, as is any address that is not
HTTPS. No request carries a device identifier, a model name, an install id or anything about which
apps you have. Wallhaven collections use its keyless public API, so no account and no token.

If you never configure a collection and never open a theme link, atomic never opens a socket.

## What the app reads, and why

**The list of installed apps.** Through `LauncherApps`, the API Android gives launchers, scoped by a
single `<queries>` entry for MAIN/LAUNCHER activities. `QUERY_ALL_PACKAGES` is not declared and a CI
gate fails the build if it ever is. The list is held in memory and never written to disk.

**Notifications** (only after you grant notification access, and only while badges are on). The
listener asks one question per notification — is there anything to show — and keeps only a count per
app. To answer it the service touches the notification's text fields to test whether they are blank;
the text itself is neither stored, logged nor copied anywhere. Turning badges off unbinds the
service, so it stops receiving anything at all.

**Screen time** (only after you grant usage access, and only while screen time is on). Daily
foreground totals from `UsageStatsManager`, read when the home screen draws the line and not kept.

**The accessibility service** (only if you turn it on). It is configured to receive no events and
cannot read window content: `canRetrieveWindowContent="false"`, no event types, scoped to atomic's
own package. It exists to perform the six global actions Android reserves — lock, shade, recents and
so on — and can do nothing else. It runs in its own process, which exits when you turn it off.

**Device administrator** (only if you activate it). Requests one policy, `force-lock`, which is what
lets a gesture lock the screen on Android 8. It cannot wipe, cannot set password rules and cannot
see anything.

Each of these asks first, on a screen that says what is read, what for and what the limits are.
Nothing is turned on by an update.

## What is stored, and where

All of it in atomic's private directory, readable by no other app:

- `settings.json` — your apps, layout, theme, gestures, which features you turned on, and the apps
  you turned badges off for.
- `background/` — the current background image and the fetch state for the collection.
- the crash log — stack traces from atomic itself, kept until you send or clear them.

Notification counts are never written down at all. They live in memory for as long as the listener
is bound and are rebuilt from scratch when Android binds it again.

Uninstalling deletes all of it. Settings → Backup writes the same data to a file you choose, and
sharing a theme writes only the theme: colours, typography, layout. Neither contains your app list.

Android's own Auto Backup is left on, so this private directory is included in the device backup
your phone already makes (Google account backup on most phones, or a device-to-device transfer).
That copy is Android's, under your account, not something atomic sends. Turning it off is a system
setting: Settings → Google → Backup, or your device's equivalent.

## Verifying this yourself

The APK is built in public by GitHub Actions from the tagged commit, and the build fails if any of
this stops being true:

```sh
scripts/check-manifest-policy.sh            # permissions allowlist, no QUERY_ALL_PACKAGES, no GMS in the DEX
./gradlew gmsGuard                          # no Google Play services on the release classpath
aapt2 dump permissions atomic-launcher.apk  # the five permissions, and nothing else
```

The five declared permissions are `REQUEST_DELETE_PACKAGES` (the uninstall dialog), `INTERNET` and
`ACCESS_NETWORK_STATE` (background collections), `EXPAND_STATUS_BAR` (opening the shade) and
`PACKAGE_USAGE_STATS` (which only puts atomic in Android's Usage Access list; it grants nothing on
its own). Notification access, accessibility and device administration are not permissions at all —
they are switches in Android's own settings, and you can see and revoke them there.

Questions: shresthasubhaneet+atomic@gmail.com.
