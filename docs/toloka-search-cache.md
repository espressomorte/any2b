# Toloka search cache

Opening a title uses a persistent cache of **raw search results**, then applies
current matching, learned aliases and rejection rules. Cached rankings are not
reused. Cached hits can be displayed after an app restart without logging into
Toloka or downloading tracker HTML again.

Defaults in `TolokaSearchDiskCache`:

- Maximum result-file payload budget: **32 MiB**.
- TTL: **10 days from download**, including valid empty search responses.
- At 90% of the budget, evict oldest downloads until usage is at most 75%
  (24 MiB). Reading does not update age or postpone expiration.
- At most 2048 files, trimmed to 1536 when that limit is exceeded.
- At most 512 KiB per entry; oversized responses are returned but not persisted.
- Expired files are removed on initialization, reads and writes. Broken files are
  treated as misses. Failed writes do not turn successful network searches into errors.

Location: app `cacheDir/toloka_search_v1`. Android may evict this reconstructible
cache under storage pressure. Writes use a temporary file followed by rename;
cache hits do not wait for ongoing network refreshes. Entries are scoped by
account and a normalized query (case and repeated whitespace), using hashed
filenames. Passwords, login cookies and personal torrent download URLs are never
serialized. Public hit metadata includes title, topic URL, size, peer counts and author.

A separate bounded query-hint store (256 entries, 10 days, typically under 150 KiB)
remembers the automatic query and optional AniList romaji, keyed by account,
TMDB identity, season and query inputs. A hint is usable only when its raw result
still exists. This avoids repeating AniList resolution before showing existing
Toloka results. Normal app settings storage holds these small hints; the 32 MiB
budget applies to result files.

Explicit manual search/retry bypasses both persistent and memory caches and
replaces the search snapshot. Expired torrent download links also force a real
search: a public cached result cannot restore a personal download URL.

Watchlist ongoing-topic checks bypass the 10-day cache and share the existing
5-minute memory cache, so episode updates are not hidden for 10 days and repeated
home refreshes are throttled. A fresh network answer also updates the disk
snapshot. The short-lived memory result cache is capped at 128 queries.

Validation covers restart persistence, account isolation, normalization, exact TTL
expiration despite repeated reads, oldest-first eviction and headroom, empty
results, malformed entries, oversized entries, and explicit memory-cache bypass.

```sh
./gradlew :core:test :tv-player:testDebugUnitTest :tv-player:assembleDebug
```
