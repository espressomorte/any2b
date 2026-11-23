# Watchlist: episode availability and completed seasons

The watchlist row retains the full TMDB season episode list, including announced
and missing episodes. Torrent cards are playable only when the saved tracker topic
has confirmed Ukrainian audio and the episode maps to an actual video file.
Known-empty and unknown playlist indexes both disable playback; neither falls back
to TMDB as proof of file availability. Explicit per-file non-Ukrainian audio labels
are excluded even when the release as a whole advertises Ukrainian audio.
Language evidence comes from release/file metadata; subtitle language alone is not
proof of audio language.

The saved topic is refreshed from Toloka when the watchlist reloads. Its current
.torrent is uploaded to TorrServer to obtain the current file list, including
updates with a new infohash. Source identity remains the tracker topic, not a
previous file ID or a different search result. Successful refresh replaces the
playlist index, including an explicitly empty season. If refresh fails, the last
known index remains cached. Unknown audio evidence fails closed.

`TolokaEpisodeAvailability` shares file indexing between Details and watchlist.
Unqualified numbered files remain scoped to the saved source season; named season
folders, SxxExx names and verified absolute episode mapping retain their explicit
scope. A release header's advertised episode range does not create available files.

Unavailable cards are dimmed, labelled «Недоступно», and ignore playback clicks.
The continue action selects a playable card and rechecks the saved playlist before
navigation. During torrent preparation, a saved E1 file ID cannot override a request
for E2. Movies retain the normal continue action.

Season removal requires all three facts:

1. TMDB indicates a finished season: the full expected episode list has valid past
   or current air dates and the final episode has type `finale`, the series status
   is Ended/Canceled, or a later season has an actually aired cached episode.
2. All TMDB episodes exist in the selected translated playlist.
3. Every episode has an individually recorded watched marker (at least 98% progress
   or the playback-completed event). Visiting the finale never implies that earlier
   episodes were watched.

The episode type is read from TMDB's season details response and retained in the
local episode cache. TMDB season/episode metadata is refreshed with the watchlist,
so its cache cannot permanently freeze an ongoing season's first episode list.
An unknown date or absent completion signal prevents automatic removal.

Completion records a season marker and removes the watchlist entry representing
that season, preserving history and source caches. Other started seasons are not
removed from storage. Completed seasons are excluded from season choices after
refresh. Existing history only stores a playback cursor, so earlier episodes are
not retroactively marked watched without evidence.

Every card displays E1/E2/etc. `WatchlistPolicy.episodeSubtitle` suppresses generic
names such as Episode 2, Серія 2, Епізод №2 and Друга серія, while preserving proper
names such as Stray Dog Strut.

Validation:

```sh
./gradlew :domain:test :core:test :tv-player:testDebugUnitTest :tv-player:assembleDebug
```

Tests cover E1 present/E2 missing and subsequent arrival, known-empty vs unknown
availability, actual files before their advertised air date, all three completion
conditions, gaps in watched episodes, saved-file mismatches, generic titles,
season-scoped files, per-file audio labels and TMDB finale metadata. Device playback
and live ongoing-topic updates still require verification on the target TV.
