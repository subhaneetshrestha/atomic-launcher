# Wallpaper sources

What each named source in Settings → Background → Source actually is, who runs it, what licence
its content carries, and what atomic sends to fetch from it. This is the "publish a web page"
recommendation Creative Commons' own attribution guidance makes for a medium — a phone home screen
— that has no room to print a credit line: [creativecommons.org/faq](https://creativecommons.org/faq/).

Every source below is fetched directly by your device. Nothing about your choice, your device, or
what you've viewed is sent anywhere but the source itself, and the request carries no account, no
token and no identifier beyond a plain User-Agent naming this app.

## Wallhaven — random search

**Runs:** [Wallhaven](https://wallhaven.cc), an independently operated wallpaper site.
**Request:** the keyless `/api/v1/search` endpoint, `categories=100` (general only), forced to
`purity=100` (safe content only, regardless of your Wallhaven account settings, because this app
has none), a portrait-only ratio filter and a minimum size, `sorting=random`.
**Licence:** none that Wallhaven asserts. Its own footer states images "remain property of their
original owners"; its terms of service license Wallhaven itself, not anyone downloading through
its API. atomic cannot show you a rights holder, a licence name, or a correct credit line for a
Wallhaven image, because that data does not exist anywhere in what Wallhaven publishes. Treat
these images as unlicensed unless you separately know otherwise.

## Wallhaven — your collection

Same site, same request shape, same licence position — the difference is which images: your own
public favourites or a named collection, read through Wallhaven's own keyless collections
endpoint. Only a *public* collection can be read this way; a private one needs an account key this
open-source app has no safe way to hold (see below), and there is no plan to add one.

## Your own server

Whatever you point it at: an nginx, Caddy or Apache directory listing of images you host, or that
anyone hosts and shares the address of. The licence is whatever the operator of that server
claims, or nothing at all if it's your own photos.

## A list file

A plain text file, one image address per line, `#` for a comment. The universal bridge: a
Nextcloud public share, an S3 bucket listing, a Google Drive single-file link, or anything else
this list doesn't name directly all become usable this way, at the cost of maintaining a text
file yourself.

## Mastodon / Pixelfed

A public account's own RSS or Atom feed (`https://instance/@user.rss`,
`https://instance/users/user.atom`). The licence is whatever the account holder posted under —
their own terms, or their instance's. Feeds are capped at what the platform serves (20 items on
Mastodon, 10 on Pixelfed) with no further paging.

## Nextcloud

A public share link to a folder of images. The licence is whatever the person who shared it
intends; a public share carries no licence metadata of its own.

## Custom address

Anything not named above. The app works out on its own whether it's a single image, a JSON
document, an RSS/Atom feed, or a plain list of addresses, and fetches accordingly. No assumption
is made about licence; that is between you and wherever you pointed it.

## Not offered, and why

**Google Photos.** Reaching a private album needs an OAuth client secret. An open-source app
cannot hold one safely — anyone can read it out of the published source, and Google's own
developer terms exist to prevent exactly that. Export the album to a folder and use "your own
server" or "a list file" instead.

**Unsplash.** Its [API guidelines](https://help.unsplash.com/en/articles/2511245-unsplash-api-guidelines)
prohibit wallpaper applications outright: *"you cannot replicate the core user experience of
Unsplash (unofficial clients, wallpaper applications, etc.)"*

## If a key is ever accepted for a source

None of the sources above needs one. If a future version adds one that does, the key is written to
`filesDir/background/credentials.json` — a location the backup feature's export never touches —
and never to the settings document itself, which is what a shared theme file and a shared theme
link both carry in full. A key inside the settings document would leave the device the first time
anyone shared their theme; this is why that never happens.
