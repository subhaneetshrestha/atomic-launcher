# Wallpaper sources: a picker, and pointing at your own collection

> Written 2026-09-14, after five research passes and a live re-verification of every endpoint below.
> This replaces §1 of [the research queue](2026-09-13-research-queue.md). It is a design note, not a
> summary: where research and live verification disagreed, the live check wins and the disagreement is
> named so nobody re-litigates it.

The ask was: replace "paste a URL" with a picker of named sources, and let a user point at their own
collection without OAuth. The answer is smaller than it looks, and one part of it is a bug fix.

**Three conclusions up front.**

1. **Do not replace paste-a-URL. Demote it to one entry named "Custom address".** It is already built,
   it is the escape hatch for every source not implemented, and it *is* the self-hosting story — an
   nginx directory, a `list.txt`, a Mastodon feed and a Nextcloud public share are all just addresses
   that the existing detector already reads correctly. Half the picker is help text over machinery
   that shipped in 1.0.
2. **atomic is shipping a defect today.** `CollectionParser.walk` tests `UrlRules.looksLikeImage`
   *before* it looks at the key, and that branch is key-agnostic. Wallhaven's `thumbs.{small,large,original}`
   are `.jpg` URLs, so they are collected. Simulated against a live 24-result search response, a
   Wallhaven rotation imports 96 URLs of which 72 are thumbnails — **three of every four wallpapers the
   rotation picks are 300-pixel thumbs**. `CollectionParserTest` has a `thumbs.small` entry in its
   fixture and only asserts `contains` on the two full-size paths, so nothing pins it. Fix this before
   adding a single source.
3. **Without OAuth, "my own collection" means one of two things** and it is worth saying plainly in
   the UI: *an address I control* (my server, my list file, my public share) or *a username whose
   uploads are public* (Wallhaven public collections, Commons uploads, a Mastodon account). Anything
   private-by-default — Google Photos, a Flickr album, a Wallhaven private collection, a Commons
   watchlist — is either impossible or costs a stored credential. The impossible ones get one honest
   line in the picker, not a half-working scraper.

---

## 1. Every source considered, ranked

Ranked by what a 434 KB GMS-free launcher should actually ship first. "Verified" means someone made
the request and got image bytes back on 2026-09-13. Anything whose live check failed is marked, however
good its documentation.

| # | Source | Auth | Image licence | Terms permit this | Own collection | Verdict |
|---|---|---|---|---|---|---|
| 1 | **Custom address** (list file, feed, JSON, single image) | none | whatever the user points at | n/a | yes — it *is* the user's address | **SHIP** (already shipped) |
| 2 | **Wallhaven search / toplist / random** | none | none granted; "all images remain property of their original owners" | conditional — wallpaper display is the site's stated purpose; only "don't mass-download" binds | no (global corpus) | **SHIP** (already shipped; needs the thumbnail fix) |
| 3 | **Wallhaven public collection** (`/user/<name>/favorites/<id>`) | none | same as above | conditional | **yes, keyless** — the headline finding | **SHIP** |
| 4 | **Your own web server** — nginx `autoindex_format json`, Caddy `file_server browse` | none | the user's own files | n/a | yes, completely | **SHIP** (nginx works unchanged; Caddy needs one `Accept` header) |
| 5 | **A list file** on any static host (incl. `raw.githubusercontent.com`, a Nextcloud public share) | none | the user's own list | n/a | yes | **SHIP** (works unchanged) |
| 6 | **Mastodon / Pixelfed account feed** | username | the user's own posts | yes | yes — their own account, any instance | **SHIP** (works unchanged) |
| 7 | **Nextcloud public share** (per-file public DAV URL + a `list.txt`) | none | the user's own files | yes | yes, with one manual step | **SHIP** (works unchanged) |
| 8 | **Wikimedia Commons** — CC0 featured pool, any category, a user's own uploads | none | mixed; CC0 subset filterable, `AttributionRequired` is a field | yes | yes — uploads by username, any curated category or gallery page | **SHIP** (needs a field selector; see §5) |
| 9 | **NASA Image and Video Library** | none | public domain, with partner exceptions | yes | no | **SHIP** — dark sky imagery is the only content here that is *good* behind white text |
| 10 | **Openverse** | none | CC/PD only, filterable to `cc0,pdm` | yes, plus one "made using Openverse, not endorsed" line | weakly (`unstable__collection=creator`, needs exact creator+source) | POSSIBLE — second wave |
| 11 | **Art Institute of Chicago** | none | CC0 where `is_public_domain` | yes | no (saved query only) | POSSIBLE — IIIF exact sizing; needs an `AIC-User-Agent` header or Cloudflare 403s |
| 12 | **Rijksmuseum** | none | PDM per object (not CC0 — checked 6 objects, all PDM) | yes | partial — `memberOfSetId` accepts a pasted set id | POSSIBLE — 4 chained requests per image; dark tonal paintings are good behind text |
| 13 | **Cleveland Museum of Art** | none | CC0 | yes | no | POSSIBLE — one request gives width/height/filesize; `images.web` longest side measured 287–1263px, so a width floor is mandatory |
| 14 | **Immich** (album shared link) | none (`?key=` query param) | the user's own photos | yes | **yes, one click in an app they already use** | POSSIBLE — needs a URL template; https with a real cert is a hard prerequisite |
| 15 | **PhotoPrism** | none on a public instance (`previewToken` from `/api/v1/config`) | the user's own photos | yes | yes | POSSIBLE — same shape as Immich |
| 16 | **Wallhaven with a pasted API key** | user key | as above | conditional; the FAQ recommends exactly this pattern over passwords | yes — the only route to *private* collections | POSSIBLE — **not verified**, no key available. Deferred for the credential-storage problem in §5 |
| 17 | **Metropolitan Museum of Art** | none | CC0 where `isPublicDomain` | yes | no | POSSIBLE but **do not ship now** — the v1 search endpoint retires **1 October 2026**, and v1.1 silently ignores `isPublicDomain` |
| 18 | **NASA APOD** | user key (`DEMO_KEY` unusable) | **mixed — 50% third-party copyrighted** (10 of 20 sampled) | conditional | no | POSSIBLE — needs `copyright == null AND media_type == "image" AND year >= ~2005`; #9 gets the same aesthetic keyless |
| 19 | **Piwigo** | none for public albums | the user's own gallery | yes | yes | **UNVERIFIED** — the blocking defect is confirmed (serves JSON as `text/plain`), but no live instance with a public album was found, so the image path is untested |
| 20 | **Flickr public feeds** (photostream / favourites) | NSID | mixed; ARR by default | conditional — the ToS never mentions wallpaper; the caching clause is an interpretation | photostream yes, albums no | REJECT for v1 — 1024px hard ceiling (`_h/_k/_o` all return 410 Gone), 20-item cap, 75×75 thumbnails polluting every item, and a mandatory "not endorsed or certified by SmugMug, Inc." notice |
| 21 | **Flickr REST API** | user key | mixed | conditional | albums, yes | REJECT — **unverified** (no key); same ToS cost, highest onboarding friction of anything here |
| 22 | **Pexels** | user key | Pexels License | conditional — the carve-out plausibly covers a launcher | yes, `/v1/collections` returns the key owner's | REJECT for v1 — the scheduled-rotation branch requires the credit **worked into the display**, i.e. on the home screen. That is the one thing this product cannot do |
| 23 | **Pixabay** | user key | Pixabay License | conditional | **no** — no user/collection/favourites parameter exists | REJECT — 1280px ceiling without per-account approval, fails the stated goal entirely, and no image was obtainable keylessly to check |
| 24 | **Smithsonian Open Access** | user key, mandatory | CC0 where `usage.access == "CC0"` | yes | no | REJECT — **live check failed**: keyless is 403 `API_KEY_MISSING`, and `DEMO_KEY` returned 429 with `retry-after: 21259` on the *first* request. 5.2M CC0 images dominated by herbarium sheets |
| 25 | **Wikimedia owner-only OAuth token** | pasted bearer | n/a | yes | unlocks the watchlist | REJECT — **unverified**, and the docs now say a bearer token alone does not lift the rate band without a cookie jar. More code than the anonymous path, for nothing measurable |
| 26 | **Openverse registered app** | OAuth2 client_credentials | n/a | n/a | no | REJECT — confidential client; and anonymous is really 20/min + 200/day, so there is nothing to want |
| 27 | **Unsplash** | key + approval | Unsplash License | **no — prohibited verbatim** | n/a | REJECT — *"You cannot replicate the core user experience of Unsplash (unofficial clients, wallpaper applications, etc.)"* Also mandates hotlinking, which a wallpaper setter cannot do. Reject pasted `unsplash.com` URLs too |
| 28 | **`source.unsplash.com`** | none | n/a | n/a | n/a | REJECT — **dead**. HTTP 503, a 567-byte Heroku error page. DNS still resolves, so it fails as a successful fetch of a non-image |
| 29 | **Google Photos** | OAuth confidential | the user's own photos | n/a | **structurally impossible** | REJECT — the read scopes were removed 2025-04-01; what survives is app-created content and a session picker. One line in the UI, no scraper |
| 30 | **Apache `mod_autoindex`** | none | the user's own files | yes | would be yes | REJECT — HTML only, opens with a DOCTYPE the parser refuses, and emits unclosed tags that would throw. This will be the most common user disappointment; the answer is "write a `wallpapers.txt`" |
| 31 | **S3 / R2 / MinIO bucket listing** | none | the user's own objects | yes | would be yes | REJECT — parses cleanly, yields zero. Fails honestly, which is the best kind of unusable |
| 32 | **Nextcloud WebDAV with an app password** | credentials | the user's own files | yes | yes | REJECT — needs PROPFIND (the fetcher is GET-only) and credentials in the authority (`UrlRules` bans them, correctly) |
| 33 | **GitHub contents API** | none | the user's own repo | yes | yes | REJECT as a *source* — five decoy fields per file, 80% junk, and three of the five download successfully as non-images. Use `raw.githubusercontent.com` + a `list.txt` (row 5) |
| 34 | **Lychee** | credentials | the user's own gallery | unclear | unclear | **UNTESTED** — no live instance found. The "POST-only API" claim is unverified and should not be quoted as fact |
| 35 | **A device folder** (Storage Access Framework) | none | the user's own files | n/a | **yes, more literally than anything above** | Out of scope for the collection engine, but it is the honest best answer to the actual need — see §3 |

**Marked as failed or unverified:** rows 16, 19, 21, 23, 24, 25, 28, 34. Rows 24 and 28 failed live. Rows
16, 21, 23, 25 could not be exercised because they require a credential nobody had. Rows 19 and 34 had
no reachable instance. Full list with reasons in §7.

---

## 2. The shortlist, in detail

Seven picker entries. Three of them are a label and a URL template; three are help text over the engine
that already ships; one needs a field selector.

### 2.1 Custom address — *nothing changes*

The row that exists today, renamed. `askForCollection` (`SettingsActivity.kt:989`) verbatim, including
the host-disclosure dialog at `:1007`. This is where a user pastes a Piwigo URL, an Immich share, a
Flickr feed or anything else this document rejected — the engine does not need to know the name of a
thing to fetch it.

### 2.2 Wallhaven — a search

**Request.** `https://wallhaven.cc/api/v1/search?purity=100&categories=100&ratios=9x16,10x16,9x18&atleast=1080x1920&sorting=random`

Verified: `meta.total` 2,774, `last_page` 116, every hit a true phone ratio. That is a large enough pool
that a rotation never repeats and small enough to be coherent. `categories=100` is general-only — no
faces behind app names — and is the right default for a text launcher.

Two parameter traps, both verified: `atleast=` is a minimum **width and height independent of
orientation**, so `atleast=1080x1920` alone returns 3456×2304 landscape; it is useless without `ratios=`.
And `ratios=portrait` is the broad bucket (67,080 hits) which includes 1000×1414 — letterboxed on a
phone. Use `9x16,10x16,9x18`.

**Image path.** `data[].path`. Never reconstruct from `data[].id` — the extension follows `file_type`
and `.png` originals keep `.png`.

**Rate limit.** 45/min, one counter shared across every `/api/v1` endpoint, fixed one-minute window.
`MIN_SPACING_MS = 1_400` is ≈43/min — right at the edge, and only safe because the app makes about two
requests per rotation. The CDN hosts (`w.` and `th.`) send no rate-limit headers at all and are not
rationed; see the spacing bug in §5.

**Pagination.** `CARRIED` drops `page` and nothing paginates, so one index is the first 24 results.
With `sorting=random` that is not a defect: the 6-hour `INDEX_TTL_MS` refresh *is* the pagination — 24
fresh random images every six hours. Leave it.

**What the user types.** Nothing. It is the default entry.

### 2.3 Wallhaven — someone's public collection

**This is the whole "point at my own collection" feature, and it is keyless.** `WallhavenTest:44`
currently asserts `apiUrlFor("https://wallhaven.cc/user/someone/favorites")` is null. That assertion is
turning away the feature.

**Request.** The web URL `https://wallhaven.cc/user/<NAME>/favorites/<ID>` maps one-to-one onto
`https://wallhaven.cc/api/v1/collections/<NAME>/<ID>?purity=100` — verified against a real account.

**Do it as a URL rewrite, not a username field.** `/api/v1/collections/<NAME>` returns a *list* of
collections, not images, so a username-only field would need a configure-time network call and a
list-and-choose screen. The rewrite needs six lines in `Wallhaven.apiUrlFor` and one test, no new UI, and
goes through the paste path that already exists. The picker entry's job is to say *where to find that
URL* ("open your favourites on wallhaven.cc and copy the address").

**Image path.** `data[].path` — identical object shape to search.

**`purity=100` must be forced here too, and it is not optional.** On a collection the server does *not*
apply the guest purity default: `purity=111` keyless raised one collection's total from 142 to 158, and
another from 196 to 298 — a 52% widening, returning sketchy content. Without forcing, a collection URL
is a hole straight through the safe-content guarantee.

**Filters are silently ignored on collections.** `ratios=`, `atleast=`, `resolutions=` and `sorting=`
all change nothing — verified, total unchanged at 142 across every combination. Only `purity=` and
`page=` have any effect. So portrait selection on a collection must be client-side, and atomic cannot do
it today: the extension walk throws away `dimension_x`/`dimension_y`. Measured on a collection literally
named "Mobile Walls": 70 of 158 items are true phone ratio, ratios run 0.45 to 1.82, 23 files exceed
5 MB and the first item returned is 6192×8256 at 14.5 MB. **Accept this for v1** — it is the user's own
curated set, and a wrong-shaped wallpaper from your own favourites is a very different failure from a
wrong-shaped wallpaper from a stranger. Note it in the picker's help text.

**Two failure shapes, distinguish them in the UI.** Unknown username → HTTP 404 `{"error":"Nothing here"}`.
A real user with nothing public → HTTP 200 `{"data":[]}`. The second is the common case: 4 of 7 real
usernames probed returned empty.

**What the user types.** The address of their favourites page.

### 2.4 Your own web server

**nginx.** Two directives:

```nginx
location /wallpapers/ { autoindex on; autoindex_format json; }
```

The listing is exactly `[{"name":"a.jpg","type":"file","mtime":"…","size":1234}]` and **it is the only
source tested where the existing parser produces a perfectly clean list with zero false positives**:
`"file"`/`"directory"` have no extension, `type` is not in `ADDRESS_KEYS`, `size` is a number.

One catch. The pasted URL must end in a slash or relative names resolve one level too high, and this does
**not** self-correct: with `alias` — the form every wallpapers-directory guide shows — `GET /wallpapers`
returns 404 with no `Location` at all, and even the `root` form's 301 pointed at the listen port over
plain http, which `UrlRules` rejects. **The picker appends the slash itself.**

**Caddy.** `file_server browse` returns HTML unless the request carries `Accept: application/json`.
`fetchIndex` sends no `Accept` at all today. The literal string `Accept: application/json,*/*;q=0.8` was
tested and works; nginx, GitHub and Mastodon all ignore it.

**What the user types.** The directory address, trailing slash added for them.

### 2.5 A list file

`https://example.com/wallpapers.txt`, one address per line, `#` comments. Works unchanged. It is the
universal bridge: Nextcloud public shares, S3 objects, Google Drive single-file links and every source
this document rejected all become usable through it, at the cost of the user maintaining a text file —
a five-minute cost for a twenty-image set.

Caps to surface on import: `UrlRules.MAX_ENTRIES = 500`, 2048 chars per URL, `MAX_INDEX_BYTES = 1 MiB`.
All three truncate silently today; the import screen should say so.

### 2.6 Mastodon / Pixelfed

**Request.** `https://<instance>/@<user>.rss` or `https://<instance>/users/<user>.atom`.
**Image path.** `//item/media:content/@url` — matched by the existing `EnclosureHandler` on local name
`content` with `type=image/*`. Works unchanged, on any instance including one the user hosts.

Honest caveat, and the research overstated this: Mastodon serves the `original/` derivative, but
"original" means *what the user posted*. A sampled nixCraft image measured 1001×553 — a landscape
screenshot, under a phone's width. Pixelfed's measured 792×1350. Both are fine sources; neither is
wallpaper-grade by default, and text-heavy screenshots are common on both platforms, which is the worst
possible background for a text launcher. Feeds are also capped at 20 items (Mastodon) / 10 (Pixelfed)
with no paging.

**What the user types.** Their feed address.

### 2.7 Wikimedia Commons

The best-behaved third-party source tested: keyless, no registration, no wallpaper prohibition, a
server-side scaled thumbnail so you never pull a 40 MB original, and it hands over every attribution
field including an explicit `AttributionRequired` boolean.

**Request, the shippable default — a CC0 pool with no attribution obligation at all:**

```
https://commons.wikimedia.org/w/api.php?action=query&format=json&formatversion=2
  &generator=search
  &gsrsearch=incategory:"Featured pictures on Wikimedia Commons" incategory:"CC-Zero" filemime:image/jpeg
  &gsrnamespace=6&gsrlimit=50&gsrsort=random
  &prop=imageinfo&iiprop=url|size|extmetadata&iiurlwidth=1440
  &iiextmetadatafilter=Artist|LicenseShortName|LicenseUrl|AttributionRequired
```

Verified end to end: two keyless calls, one header, 755,635 bytes, a real 1920×2512 portrait, `LicenseShortName`
`CC0`, `AttributionRequired` `false`. `gsrsort=random` also fixes the alphabetical-sortkey problem that
otherwise returns a run of files beginning with punctuation.

**Request, own uploads:** `&generator=allimages&gaisort=timestamp&gaiuser=<NAME>&gaidir=descending&gailimit=50`
with the same `prop=imageinfo&iiurlwidth=1440`. **Use `generator=allimages`, not `list=allimages`** —
`aiprop` has no thumbnail option, so the list form returns only originals (7500×7094 straight off
`upload.wikimedia.org`). Same single request, right answer.

**Image path.** `query.pages[].imageinfo[0].thumburl`. **Not** `.url` (the untouched original),
**not** `.descriptionurl` (a wiki HTML page whose path ends in `.jpg`).

**Two things that are not true and would otherwise get baked in.** `thumbwidth`/`thumbheight` echo what
you *asked for*, not what was served — at `iiurlwidth=99999` the API reported 99999×66666 while serving
3840px. And `thumburl` is not always a thumbnail: when the original is narrower than the requested width
MediaWiki does not upscale and `thumburl` comes back byte-identical to `url`. **Take the size guard from
`width`/`height`, which are the original's and are correct.** The served width is the `/NNNpx-` segment;
Wikimedia snaps to buckets (ask 1080 → get 1280, ask 1440 → get 1920, ask 99999 → get 3840, the cap).

**Rate limit.** 2026 gateway limits, `HTTP 429` + `Retry-After`, applying across the Action API: 10/min
unidentified, **200/min with a descriptive User-Agent**. But it is *not observable* — 25 back-to-back
requests returned 25×200 and Wikimedia sends no rate-limit headers of any kind, so honour `Retry-After`
reactively. There is no 429 handling anywhere in the codebase today (`grep 429` across all `.kt` returns
nothing). At one index per six hours and one image per rotation, even 10/min is two orders of magnitude
of headroom.

**User-Agent.** Generic agents are *not* blocked today — `curl`'s default and `Dalvik/2.1.0` both returned
200 — but an **empty** UA is a hard 403 whose body is plain text, not JSON, so any parser assuming a JSON
error throws on it. Set the descriptive UA anyway: it is the documented line between the 10/min and
200/min bands, and `ImageFetcher` already takes `userAgent` as a constructor arg, so it costs a config
value. Add the repository URL as the contact information the policy asks for.

**Self-hosted MediaWiki is free.** The identical query shape returned 200 and real thumbs from three
unrelated non-WMF wikis. It is the Commons entry with the host swapped, so build it as a base-URL field
in the same entry, not a second one. Accept a full `api.php` URL rather than guessing the path. One hazard:
for non-raster files MediaWiki returns a generic **file-type icon** as `thumburl` (a `.woff2` yielded a
40px `fileicon.png`), so filter on mime and a width floor, never on `thumburl` being present.

**Attribution.** `extmetadata.Artist` arrives as raw HTML (`<bdi><a href="…"><span title="Spanish photographer">Diego Delso</span></a></bdi>`)
and needs a tag stripper — a few lines, not a library. The CC0 default entry sidesteps it entirely; the
category and own-uploads entries do not. Display it as sourced ("Commons says: CC BY-SA 4.0"), because
the Foundation explicitly disclaims warranty on licence correctness.

**What the user types.** Nothing (CC0 pool), a username (own uploads), or a category / page title.

### 2.8 NASA Image and Video Library

The only genuinely zero-friction source of everything examined: no key, no registration, no rate limit
observed, no UA requirement, and — the thing that actually matters here — **dark, low-contrast imagery
that tolerates overlaid text without heavy dimming**. Every museum source is bright gallery photography
on a neutral field, which is the worst possible case for app names drawn on top. This is the one entry
that helps legibility rather than fighting it.

**Request.** `https://images-api.nasa.gov/search?q=nebula&media_type=image&page_size=30`
**Image URL.** Built from the id, not read from a field: `https://images-assets.nasa.gov/image/{nasa_id}/{nasa_id}~orig.jpg`
— verified present for all 30 items sampled, so no second request is needed. (`collection.items[].links[]`
also carries `~orig`, but the template is simpler than selecting the right link.)

**Caveats.** No dimensions or file sizes in the search response — they exist at
`https://images-api.nasa.gov/metadata/{nasa_id}` (`File:ImageWidth`, `Composite:ImageSize`) at two extra
hops, or skip it and cap the download. Mostly landscape and square, so a phone crops — but a dark
starfield crops gracefully in a way a portrait painting does not. A minority of items originate with
partner institutions; `rights` and `secondary_creator` carry the credit. `q=nebula|galaxy|deep field`
targets the good stuff; mission photography (rockets, ISS) is brighter and busier.

**What the user types.** Nothing, or a search word.

### 2.9 How the existing spacing satisfies all of this

`BackgroundEngine.run()` fetches **one image per run**, the index is cached for `INDEX_TTL_MS` (6h) with
ETag/Last-Modified revalidation, and the default interval is 360 minutes on unmetered only. A rotation
costs at most two requests. Against Wallhaven's 45/min, Commons' 200/min, Openverse's 20/min + 200/day and
NASA's nothing, every limit here is two to three orders of magnitude away. **The rate limits are not the
risk. The dead endpoint is** (§6).

---

## 3. What "your own collection" honestly looks like

| Source | What it is | What it costs | The honest limit |
|---|---|---|---|
| Your own server / list file | a directory or a text file you control | two nginx directives, or one text file | none — this is the real answer |
| Nextcloud public share | a shared folder + a `list.txt` inside it | one manual list | **no keyless GET lists a folder.** Every public route is an HTML SPA, a POST, or needs PROPFIND. `GET` on the public DAV folder returns 200 with a 114-byte "This is the WebDAV interface" stub, which atomic parses as an empty list with no warning |
| Mastodon / Pixelfed | your own account's media | nothing | 20/10 items, no paging, and what you post is not what you would choose as a wallpaper |
| Wallhaven public collection | your favourites, if you mark them public | nothing | no portrait/resolution filter server-side, purity must be forced client-side, and the owner can make it private at any moment, turning a working source into a 404 |
| Wallhaven private collection | your favourites, private | a pasted API key | the key is a bearer credential for the whole account read surface — private collections *and* `/settings`, which exposes purity settings, blacklists and resolutions. Regenerable by the user at any time, so it starts 401ing with no warning. **And it unlocks NSFW**, so `purity=100` must stay force-written on every request or a pasted key silently widens the home screen |
| Commons | your own uploads, by username; any category or gallery page you curate | nothing | it is *uploads*, not favourites. A real watchlist needs a token, and the token now needs a cookie jar to be worth anything |
| Immich / PhotoPrism | your own library, one click in an app you already run | a ~20-line URL template each | https with a real certificate is a hard prerequisite — `UrlRules` refuses plain http and the fetcher will not follow a redirect off https. Most self-hosted installs are http on a LAN or behind a self-signed cert, and those users will think atomic is broken. Say it in the help text |
| Openverse | `unstable__collection=creator` | nothing | requires **both** the exact creator string and the exact source, cannot be combined with `q`, and carries an `unstable__` prefix the project reserves the right to break |
| Flickr | your own photostream, keyless by NSID | nothing | 1024px hard ceiling, no album feed exists, and the feed rejects a username — it needs the NSID, and resolving username→NSID needs an API key |
| Pexels | `/v1/collections` returns the key owner's own | a pasted key + a home-screen credit line | the credit line is the blocker, not the key |
| Google Photos | — | — | **impossible.** Read scopes removed 2025-04-01; what remains is app-created content and a session picker that needs a human to hand-pick items every time |

**The pattern.** Keyless "my collection" works exactly where the platform already publishes the thing at
a stable public address. Everywhere else it costs a stored credential, and a stored credential in this
app costs the whole of §5.2.

**And the answer that beats all of them**, which was not in the brief and should be said anyway: a device
folder via `ACTION_OPEN_DOCUMENT_TREE`. No secret, no OAuth, no certificate, no rate limit, no network
policy — and it works on a metered connection and in aeroplane mode, which nothing above does. Paired with
Syncthing or the Nextcloud client it *is* the user's own photo collection. It needs no manifest permission,
so it does not touch the `QUERY_ALL_PACKAGES` build gate. It is a parallel path rather than a `SourceKind`,
because everything in `core:collections` assumes an https URL — but it belongs at the top of the picker
whenever someone builds it.

---

## 4. The design

### 4.1 The picker screen

No new UI machinery. The source picker is the same `Row(…, checked =, singleChoice = true)` pattern the
background *mode* picker already uses eleven lines above the collection rows (`SettingsActivity.kt:862`),
driven by the existing `choose()` helper (`:489`).

```
Background
  Mode            ( solid · gradient · collection )

Collection
  Source          Wallhaven — random portrait          ▸
  Address         wallhaven.cc                          ▸     ← only when the source asks for one
  Change every    6 hours
  On wi-fi only   ●
  Shuffle         ●
  Change now
  About this image                                      ▸
```

Four rules:

1. **Every source is listed, including the unconfigured ones**, greyed with a "needs an address" /
   "needs a username" affordance rather than hidden. Hiding them makes the picker look empty and makes
   the feature undiscoverable; this is Wallora's `isConfigured = false` pattern and it is the right one.
2. **One live subtitle per source**, showing what it is currently set to — `Wallhaven · random · portrait`,
   `Commons · your uploads · Diliff`. This is Muzei's `getDescription()` and it is the difference between
   a picker and a list of nouns.
3. **The schedule and network-policy rows stay on this screen**, directly under the picker. Muzei puts
   "auto advance only on Wi-Fi" in the same sheet as the source list for the same reason: choosing a
   source and deciding what it may spend are one decision.
4. **The host-disclosure dialog (`SettingsActivity.kt:1007`) stays on every path**, including the named
   sources. It is what keeps `docs/privacy.md:13` true, and a named source is not more trustworthy than a
   typed one — it is just pre-typed.

**Rejected sources get one line each, not silence.** A short "Not available" group at the bottom: *Google
Photos — needs a client secret an open-source app cannot hold; export to a folder instead.* *Unsplash —
its API terms prohibit wallpaper applications.* This is cheaper than answering the same issue four times a
year, and it is the truth.

### 4.2 How a source asks for its input

Three shapes, and only three:

- **Nothing.** Wallhaven search, Commons CC0, NASA. The row is absent.
- **An address.** Wallhaven collection, own server, list file, feed, Nextcloud, custom. Reuse
  `askForCollection` (`:989`) verbatim — validation, host disclosure, everything. The source's help text
  says where to find the address and, for nginx, the picker appends the trailing slash.
- **A username.** Commons own uploads. Reuse `askText` (`:476`). A username is not a secret — it is part of
  a public address and it ends up inside `url` anyway, so **it needs no new field and no new storage**.

**A key, if one is ever accepted**, is a fourth shape and it is deliberately not in v1: a masked field, the
issuing URL as tappable help text next to it, one validating request on save (so a typo surfaces now rather
than as a silent stall in six hours), never logged, and copy that says in as many words: *this key stays on
this phone. It is not in your backup and it is not in a shared theme.* That copy has to be true, which is
§5.2. WallFlow's version of this dialog — an unmasked single-line field, no link, no validation — is the
thing to improve on rather than copy.

### 4.3 Where attribution goes

**Not on the home screen. Ever.** The home screen is a list of app names over a dimmed photograph; a grey
credit string burned into that is hostile to the product *and* worse attribution than the alternative,
because it is unselectable, unfollowable, and sized to be ignored.

That is defensible, and it is worth being able to state why in three sentences:

1. **It is not legally required in the first place.** Every CC attribution obligation is conditioned on the
   verb *Share* — CC BY 4.0 §3(a)(1) opens "**If You Share** the Licensed Material". §1 defines Share as
   providing material *to the public*. atomic writes the image to `filesDir/background/current.img`
   (app-private) and draws it in its own `View`; `grep -rn WallpaperManager app core` returns **no hits**,
   so the bitmap never reaches the system wallpaper, the lockscreen, another app or a recents thumbnail.
   Each device fetches from the origin; the project distributes a pointer, not a picture. CC's own FAQ:
   *"If you are using the material personally but are not making it or any adaptations of it available to
   others, you do not have to attribute the licensor."*
2. **Where it *is* required, a one-gesture linked credit satisfies it.** CC BY §3(a)(2) says the conditions
   may be satisfied "in any reasonable manner based on the medium… it may be reasonable to satisfy the
   conditions by providing a URI or hyperlink to a resource that includes the required information", and
   CC's recommended-practices wiki names our exact medium: *"For media such as offline materials, video,
   audio, and images, consider publishing a web page with attribution information."*
3. **ShareAlike cannot bite.** The scrim is `canvas.drawColor(...)` drawn *after* the bitmap
   (`BackgroundView.kt:134`); the stored file is byte-identical to what the server sent. Even if it were
   baked in, a uniform black overlay is not an adaptation ("manifests sufficient new creativity"), and
   scaling to fit is expressly covered by §2(a)(4) ("simply making modifications authorized by this Section
   2(a)(4) never produces Adapted Material"). And BY-SA §3(b) is conditioned on sharing anyway. The
   launcher's Apache-2.0 licence is untouched.

So, four places, in order of who they serve:

1. **A credit sheet, one gesture from the home screen.** Long-press the wallpaper → title, author,
   licence and source, with the author and licence as tappable links and an "Open the original page"
   action hitting `descriptionurl` / `foreign_landing_url` / `data[].url`. This is the piece that makes the
   whole scheme defensible, at a cost of zero home-screen pixels.
2. **Settings → Background → "About this image"**, plus the last `HISTORY_SIZE` (20) credits, which is
   state the rotation already keeps. Same data, discoverable without knowing the gesture.
3. **`docs/SOURCES.md` in the repository**, linked from the picker. One section per source: who runs it,
   what licence the content carries, what the terms require, what atomic sends. This is literally CC's
   "publish a web page" recommendation, it costs nothing against the 2.5 MiB budget, and it is the only
   honest place to write the Wallhaven sentence below.
4. **Any future share action — mandatory, not optional.** That is the one path where §3(a) genuinely
   fires, and the credit goes in the share text. Which is also the reason not to build "share this
   wallpaper with the scrim baked in": that *would* be an adaptation, shared publicly, and ShareAlike
   would finally have something to attach to.

**Two things CC names as pitfalls, which rules out the two shortcuts a minimalist app reaches for first:**
attribution in alt text or metadata only (*"many users are likely not aware of, and will never see,
attribution information included in metadata"*), and crediting the site instead of the author (*"crediting
the site where you have found the material cannot substitute crediting the author"*). So "Images from
Wikimedia Commons" in a settings screen is explicitly not enough.

**And the Wallhaven sentence, which has to be said out loud.** Wallhaven grants no licence to anyone. Its
footer reads *"All images remain property of their original owners"*, its ToS (last updated 2016) grants a
licence to Wallhaven and not to downstream users, and the content is user-submitted with no rights
assertion anywhere. atomic therefore **cannot generate a correct credit line for a Wallhaven image**,
because the data does not exist. The credit sheet must say so — not "Wallhaven, CC BY" but *"licence
unknown; the original rights holder is not identified"* with a link to `data[].url`. Rank it
PERMITTED-CONDITIONAL, not permitted-by-licence, and write that in `SOURCES.md`.

---

## 5. What changes in this repository

### 5.1 The settings schema: one nullable field

```kotlin
data class CollectionConfig(
    val url: String = "",
    val source: String? = null,   // new: which named source produced url; null means "pasted"
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    val unmeteredOnly: Boolean = true,
    val shuffle: Boolean = true,
)
```

**`url` keeps meaning exactly what it means today — the resolved, fetchable https address.** `source` is a
label plus the recipe that produced it, never a replacement. That single rule is what makes this
compatible in both directions:

- *1.0 file → 1.1 app.* `ThemeJson` sets `ignoreUnknownKeys = true` and every field has a default, so a
  1.0 document decodes to `source = null`, which means "a pasted URL" — literally 1.0 behaviour. No
  migration.
- *1.1 file → 1.0 app.* A 1.0 build ignores `source` and reads `url`, which still works. This is **only**
  true because `url` stays resolved. Store `source="wallhaven"` + `query="forest"` with a blank `url` and a
  1.0 build sees `isConfigured == false` and silently stops fetching, as does `BackgroundScheduler` and the
  "Change now" row.

Two traps:

- **Do not bump `SettingsCodec.SCHEMA`.** For settings a newer schema loads best-effort with a warning; for
  *themes* it is a flat refusal (`DecodeResult.Unsupported`). A bump means every 1.1 theme refuses to import
  into 1.0, and an additive nullable field needs none.
- **Nullable, not empty-string.** `ThemeJson` has `encodeDefaults = true` but `explicitNulls = false`, so
  `source: String? = null` is omitted from output and leaves all four `docs/themes/*.atomictheme` files
  byte-identical and the golden-file test (`SettingsCodecTest:186`) passing untouched. `source: String = ""`
  would be written and break all four.

`Sanitizer.collection()` (`Sanitizer.kt:339`) nulls a `source` this build does not know, **keeps the `url`**,
and warns. That degrades an imported theme to exactly 1.0 behaviour instead of silently going dark — which
is also what makes a source *removable without a migration* when one dies (§6).

### 5.2 The backup problem: a key must not go in the settings document

`CollectionConfig` sits inside `Theme`, not merely inside `Settings`, so it leaves the device by four
routes:

| Route | Code | Who receives it |
|---|---|---|
| Backup export | `SettingsActivity.kt:1603` | whatever the user exported to |
| **Theme share as file** | `ThemeFiles.share()` (`share/ThemeFiles.kt:33`) | any app in the share sheet |
| **Theme share as link** | `ThemeLink.write()` — the whole theme deflated into `atomic://theme?d=…`, explicitly designed to be pasted into a chat | a group chat |
| Android Auto Backup | `allowBackup="true"` with `<full-backup-content />` and **zero exclusions** (`res/xml/backup_rules.xml`) | the user's cloud account |

**A key in `CollectionConfig` is a key in a group chat the first time anyone shares their theme.** That
rules out the obvious design, and it is the reason row 16 of the ranking is deferred rather than shipped.

If a key is ever accepted:

- It goes in `filesDir/background/credentials.json`, written through `ImageStore`, which already does
  atomic JSON reads and writes in that directory. The export path takes `Settings` and nothing else, so it
  can never see it — **no change to the export code at all**, which is the entire point.
- Add `<exclude domain="file" path="background/credentials.json"/>` to **both** `res/xml/backup_rules.xml`
  and `res/xml/data_extraction_rules.xml`. Both currently carry zero exclusions; this would be the first.
- Prefer the header form where the API offers one (Wallhaven accepts `X-API-Key:`), because a key in a URL
  leaks into redirect `Referer`, into the server's own access log, and into any diagnostic a user pastes
  into a bug report. Note `UrlRules.problemWith` already refuses credentials in the authority — "just put
  it in the URL" is blocked on purpose and un-blocking it would be a regression.
- **It changes the privacy page.** `docs/privacy.md:19` currently says *"Wallhaven collections use its
  keyless public API, so no account and no token."* Any key-bearing source makes that sentence false and
  needs a row in the table plus a line on the settings screen.

A username needs none of this. It is public, it lives inside `url`, and the host-disclosure dialog already
tells the user what will be sent.

### 5.3 The parser: fix the bug, then add one field

**Fix first.** `CollectionParser.walk` tests `looksLikeImage` before the key check, and that branch ignores
the key entirely, so a Wallhaven search imports `data[].path` (24 URLs) *and* `thumbs.{small,large,original}`
(72 URLs). `CollectionParserTest:42` has a `thumbs.small` in its fixture and asserts only `contains`, so it
passes while documenting the bug. Change that test to assert the **exact list** and fix the parser. On a
single `/w/<id>` page the same walk yields 8 URLs of which the **first four are the uploader's avatar at
200, 128, 32 and 20 pixels**.

For Wallhaven alone the fix is one line (drop `th.wallhaven.cc`), and if Wallhaven is the only API source
that ships, stop there. But the same walk is why Commons yields six URLs per file (including
`descriptionurl`, an HTML page whose path ends `.jpg`), why the GitHub contents API yields five decoys per
file at 80% junk, and why PhotoPrism yields *zero* correct URLs from five photos. The general fix is small:

**One optional pair of fields on a source entry.**

```kotlin
data class Source(
    val id: String,
    val label: String,
    val ask: Ask,                    // None | Username | Address
    val request: String,             // URL template; "{1}" is what the user typed
    val item: String = "",           // JSON path to the repeated element; "" = walk as today
    val image: String = "",          // template over the item: "{thumburl}", "{path}", or a full URL
    val licence: String,
)
```

`item` is a dotted path with `*` for "every element" (`query.pages.*.imageinfo.*`, `data.*`,
`collection.items.*`). `image` is a template where `{a.b}` substitutes a field of the item. That covers
every shortlisted source and the deferred ones too:

| Source | `item` | `image` |
|---|---|---|
| Wallhaven | `data.*` | `{path}` |
| Commons | `query.pages.*.imageinfo.*` | `{thumburl}` |
| NASA | `collection.items.*` | `https://images-assets.nasa.gov/image/{data.0.nasa_id}/{data.0.nasa_id}~orig.jpg` |
| Immich (later) | `assets.*` | `{origin}/api/assets/{id}/original?key={key}` |
| PhotoPrism (later) | `.*` | `{origin}/api/v1/t/{Hash}/{token}/fit_2560` |

About 40 lines in `CollectionParser`, one new branch in `json()`, and the walk stays exactly as it is when
`item` is empty — which is every custom address, every list file and every feed. The same path mechanism
reads the credit fields, so §4.3 costs no extra machinery.

**Sequence it lazily.** Ship the picker with zero parser change (Wallhaven + the custom-address entries)
plus the one-line thumbnail fix; add `item`/`image` when Commons and NASA land. Do not add it speculatively.

`ParsedCollection`, `IndexCache` and `BackgroundState` all carry `List<String>` today, so a credit needs
them to carry a record — `Image(url, author?, authorUrl?, pageUrl?, licence?, title?)`, every field nullable.
`ImageStore`'s `Json { ignoreUnknownKeys = true }` tolerates that migration in both directions, and credit
strings are per-image state in `state.json`, not APK bytes, so the 2.5 MiB budget is unaffected except by
the sheet's layout.

### 5.4 Does Wallhaven's special case generalise?

**Halfway, and the half that generalises is not the interesting half.**

`Wallhaven.apiUrlFor` is a pure `String -> String?` with exactly one call site (`BackgroundEngine.kt:130`),
plus `ImageFetcher.spaceOutWallhaven` with one more. Ninety-five lines, two seams. It bundles two jobs:
(a) page URL → API URL, which every source shares; and (b) forcing `purity=100` and refusing any parameter
that could reshape the request — a content-safety policy written in Wallhaven's own parameter names, which
generalises to nothing and must be re-authored per source.

So: **do not build a registry for one implementation.** The source list is *data* — `(id, label, ask,
request, item, image, licence)` — in `core:collections`, which is pure Kotlin/JVM and must never import
`android.*`, so it stays unit-testable beside `WallhavenTest`. When source #2 lands, `apiUrlFor` becomes one
branch of `Sources.requestUrlFor(source, url)` and `BackgroundEngine.kt:130` keeps its exact shape. That
refactor is mechanical and cheap *because* it was left alone now.

### 5.5 The fetcher, four small things — three of which are already bugs

1. **Per-host spacing.** `spaceOutWallhaven` is a single `lastWallhavenRequest` field keyed to one hardcoded
   host, so any second API source silently gets no spacing at all. Make it `Map<host, Long>` with a
   per-source value.
2. **Match the API host exactly.** `UrlRules.hostOf(url)?.endsWith(Wallhaven.HOST)` also matches
   `w.wallhaven.cc` and `th.wallhaven.cc`, so **image downloads are being slept 1.4 s for a budget that never
   applied to them** — the CDN sends no rate-limit headers at all. Use `host == HOST`.
3. **`Accept: application/json,*/*;q=0.8` on `fetchIndex`.** One line, unlocks Caddy's browse JSON, ignored
   by nginx, GitHub, Mastodon and Wallhaven.
4. **Sniff `{`/`[` before believing a `text/plain` content-type.** `SourceDetector.byContentType` trusts the
   server's claim first, so Piwigo — which serves JSON as `text/plain` — is parsed as a one-URL-per-line list
   and yields nothing. Three lines, and it fixes every hand-rolled API that mislabels its JSON, not just
   Piwigo.

And one addition: **honour `Retry-After` on 429**. There is no 429 handling anywhere today. Wikimedia's
documented obligation is explicit — *"respect the Retry-After header provided with a 429 Too Many Requests
status code"*, and *"if no such header is present, clients should wait at least five seconds"*. Nothing
observed a 429 in testing, which is exactly why it will arrive unannounced.

Two things that are already right and should not be touched: `PERMANENT_CODES` correctly excludes 502 (see
§6), and `ImageFetcher` already handles 304, which matters because — contrary to the first research pass —
the Wallhaven CDN **does** send `ETag` and `Last-Modified`, so conditional revalidation is available.

### 5.6 The User-Agent

`ImageFetcher.kt:25` already takes `userAgent` and sets it at `:171`, and the current value
(`atomic-launcher/io.github.subhaneetshrestha.atomic (Android <release>)`) is already a named client rather
than `Dalvik/…`. Add the repository URL, which is the "contact information" Wikimedia's policy asks for and
the documented line between the 10/min and 200/min bands. One config value, no code.

---

## 6. What will rot

**Budget roughly one breakage per source per year, and design so a dead source is not an outage.** Wall You
shipped 15 sources and its commit log shows a source-fixing commit roughly every two months: Wikipedia's
missing User-Agent, Unsplash's bot detection, Pixabay's captcha, Reddit's removal of anonymous `.json`,
Lemmy NPEs. Muzei's flagship example source died outright when 500px shut down its API. WallFlow has been
silent for two years with 33 open issues. **This is the real cost of the feature, and it is recurring.**

What that means concretely here: `Sanitizer` nulls an unknown `source` and keeps the `url`, so removing a
source in a future release degrades every affected install to a plain pasted address rather than a blank
screen. Never make a source's removal require a migration.

**The specific things that will break, in rough order of likelihood:**

- **Wallhaven 502s, not 429s.** A parallel burst produced 25 of 60 requests as HTTP 502, and intermittent
  502s hit serialised single requests at ~40% at times — the `/terms` page and a `/user/<name>/favorites`
  page 502'd three times running, recovering with no pattern. **502 is the failure a rotating launcher will
  actually meet.** atomic already handles it correctly (502 is not in `PERMANENT_CODES`, so it retries next
  rotation). The documented 429 could not be reproduced in 110 requests across three minutes.
- **A silently dead Wallhaven source.** An unrecognised `sorting=` value returns **HTTP 200 with an empty
  `data[]` and a non-zero `meta.total`** — an infinitely-paginated source that yields nothing, with no error.
  atomic carries `sorting` verbatim from the pasted URL. Also: `categories=` (empty) and `purity=2` return
  HTTP 500 (atomic escapes this only because `carriedParams` drops empty values), and `categories=999` is
  silently ignored and defaults to all categories, pulling the people category back in.
- **A regenerated Wallhaven key**, if keys are ever accepted, starts returning 401 with no action by the
  user. That needs a clear "your key no longer works" state, not a silent stall behind a doubling backoff.
- **Wikimedia's 2026 gateway limits are invisible.** No rate-limit headers exist, so atomic cannot budget
  proactively; it can only react to a 429 it does not currently handle. `api.wikimedia.org` endpoints are
  being retired through June 2027 — build only on `commons.wikimedia.org/w/api.php`. (The first research
  pass said that host was already dead; it is alive and serving. The conclusion stands for the real reason:
  it exposes three fixed renditions and no licence metadata at all.)
- **The Met's v1 search endpoint retires 1 October 2026** — two weeks after this note. v1.1 silently ignores
  `isPublicDomain`. Do not ship it.
- **Openverse's collection parameters carry an `unstable__` prefix** the project reserves the right to
  change, and its own thumbnail endpoint currently returns HTTP 424 for every image tried, because its
  upstream Photon renderer is 400ing. Treat that endpoint as nonexistent.
- **Terms change under you.** Pexels revised its wallpaper-app article as recently as 26 August 2026 and has
  revised it before. Reddit removed anonymous `.json` access outright. The stock platforms are the category
  most likely to ban this use next, because a wallpaper app competes with their own — Pexels ships one, and
  the article that carves out an exception for launchers ends by advertising it.
- **The one policy this app must not drift on:** Wallhaven's *"we ask that you don't"* run mass-download
  scripts. One index page and one image per rotation is squarely inside that. Pre-caching a 694-item
  collection is not, and neither is a thumbnail grid that pulls 24 thumbs per page view. If a picker ever
  shows previews, they cost ~50 KB each and ~1.2 MB per page — budget it deliberately.
- **Legibility rot, which is the quiet one.** Museum and stock sources are bright, detail-dense photography
  shot on neutral grounds — the worst possible background for app names. NASA and Dutch Golden Age painting
  are the only naturally dark sets examined. `categories=100` (no people) is the right Wallhaven default for
  the same reason. If the picker ever grows toward "more sources", legibility, not licensing, is what will
  make it feel worse.

---

## 7. What is still unknown

Everything below was researched but **not confirmed against a live response**. None of it should be quoted
as fact, and none of it should be built on without someone running it first.

- **Wallhaven with an API key.** No account, no key. Every claim is documentation-only: the `X-API-Key`
  header, private collections, `/settings`, and "searches will be performed with that user's browsing
  settings and default filters" (which is why `purity=100` must still be sent explicitly). Two keyless
  failure shapes *were* confirmed and are counter-intuitive: `/api/v1/settings` with no key is 401, but
  `/api/v1/collections` with no key is **404, not 401** — do not treat a 404 there as "bad key".
- **Wallhaven's 429 and `Retry-After`.** 110 requests in ~3 minutes including a 60-way parallel burst
  produced zero 429s. The limit is documented and the headers (`X-Ratelimit-Limit: 45`, `-Remaining`) are
  real and shared across endpoints, but the throttled response itself was never observed.
- **The NSFW-id-returns-401 behaviour** on `/api/v1/w/<id>` is documented but was not observed (no NSFW id
  to test with).
- **Piwigo's image path.** The blocking defect is confirmed live (`text/plain` JSON), but `piwigo.org`
  exposes no public categories, `piwigo.org/demo` is 404, and five other candidate hosts were dead. The
  `element_url` / `derivatives.*.url` shape and the warning that derivatives inflate a 20-photo album into
  100+ entries are both untested.
- **Lychee.** No live instance found. The "POST-only API" claim is unverified and, given that PhotoPrism
  — written off in the same breath — turned out to be fully keyless and working, it should not be treated
  as settled.
- **Smithsonian's search API.** Keyless is 403 by design; `DEMO_KEY` returned 429 on the first request with
  `retry-after: 21259` (~6 hours), because `api.data.gov`'s DEMO_KEY quota is **10/hour shared per IP across
  every api.data.gov service** — a NASA APOD call on the same IP consumed it. Any carrier-NAT'd phone is
  permanently 429ed. The response shape was never verified. The *image* host (`ids.si.edu`) is genuinely
  keyless and was confirmed, with one correction: `/full/max/` returns HTTP 400, so always request an
  explicit width.
- **Pixabay's 1280px ceiling.** No image was obtainable keylessly — the API refuses without a key and the
  CDN URL tried returned 403 — so the resolution claim rests on documentation alone.
- **Flickr's `url_o` / `url_k` shapes** and the assertion that the REST JSON "would work unchanged" through
  the extension walk. Untested without a key. Separately, the claimed "mp4 declared as `type="image/jpeg"`"
  bug **could not be reproduced** in any feed fetched; it may be true for accounts that post video, but do
  not design around it.
- **The Wikimedia owner-only bearer token.** No Meta-Wiki account, so no token. Documentation now says
  elevated limits in older owner-only tokens "are ignored by the new rate limiting infrastructure" and that
  such clients "must implement support for returning cookies to the server" — so it is more code than the
  anonymous path, for a limit nothing could make bite in 25 rapid requests.
- **Immich and PhotoPrism on a real self-hosted install.** Both were verified against public demos.
  Untested: whether a typical install's certificate satisfies `UrlRules` (most are http on a LAN or
  self-signed, and both are refused), and whether `?apiKey=` works on endpoints where it is accepted by the
  auth service but is *not* a documented parameter.
- **Cloudflare's behaviour from a real device.** The Art Institute's IIIF host 403s every request without
  an `AIC-User-Agent` header (the fix is that header, **not** `Referer` — 8/8 deterministic 403s with
  `Referer` set). `wallhaven.cc` 403s WebFetch entirely but serves a `Dalvik/Android 14` UA fine. Cloudflare
  can also fingerprint TLS, in which case no header helps. Both need testing from the actual app before any
  claim about them is relied on.

---

## What to actually build first

Three picker entries and a bug fix, in this order:

1. **Fix the thumbnail import** and pin it with a test that asserts the exact URL list.
2. **Fix the two fetcher bugs next to it** — per-host spacing, and exact host matching so image downloads
   stop sleeping for a budget that never applied.
3. **`CollectionConfig.source: String? = null`**, `Sanitizer` degrading an unknown source to a plain address.
4. **The picker screen** — Custom address, Wallhaven search, Wallhaven collection, plus help-text entries for
   own server / list file / Mastodon / Nextcloud. Six lines in `Wallhaven.apiUrlFor` to accept
   `/user/<name>/favorites/<id>`, forcing `purity=100`. Zero parser changes.
5. **The credit sheet**, even though nothing shipped in step 4 legally requires one — because the record has
   to exist before Commons can use it, and because the Wallhaven sentence ("licence unknown") is worth saying.

Then, and only then, `item`/`image` in the parser, and Commons and NASA behind it.

Skipped deliberately: a registry for one source, per-source Kotlin adapters, a licence-compatibility engine,
key storage, and every stock platform. Add key storage when a source that needs one earns its place; add the
registry at source #2, when `BackgroundEngine.kt:130` will not change shape to get it.
