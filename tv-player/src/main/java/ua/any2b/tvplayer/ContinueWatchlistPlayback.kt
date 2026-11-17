package ua.any2b.tvplayer

import ua.any2b.core.tmdb.TmdbEpisode
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.tvplayer.data.TmdbPlaybackSelection
import ua.any2b.tvplayer.data.TmdbWatchHistoryStore
import ua.any2b.tvplayer.data.TmdbWatchlistStore
import java.time.LocalDate

/** Resolves the saved playback source and episode before handing navigation to the host. */
internal class ContinueWatchlistPlayback(
    private val history: TmdbWatchHistoryStore,
    private val watchlist: TmdbWatchlistStore,
    private val openDetails: (TmdbTitle, TmdbPlaybackSelection?, Int?) -> Unit,
) {
    fun start(title: TmdbTitle, episode: TmdbEpisode?) {
        val progress = history.episodeProgress(title.type, title.id)
        val requestedSeason = episode?.seasonNumber ?: title.matchedSeasonNumber
            ?: progress?.seasonNumber
        val inferred = history.inferredPlaybackSelection(title.type, title.id)
        val selection = if (requestedSeason != null) {
            history.playbackSelection(title.type, title.id, requestedSeason) ?: inferred?.takeIf {
                it.source == TmdbPlaybackSelection.Source.TORRENT ||
                    (it.tmdbSeasonNumber ?: it.anitubeSeasonNumber) == requestedSeason
            }
        } else history.playbackSelection(title.type, title.id) ?: inferred
        val season = requestedSeason ?: selection?.tmdbSeasonNumber ?: selection?.anitubeSeasonNumber
        val requestedEpisode = episode?.episodeNumber ?: progress?.let {
            if (it.progress >= 0.95f) it.episodeNumber + 1 else it.episodeNumber
        } ?: if (title.type == TmdbMediaType.TV && selection != null) 1 else null
        val cachedEpisode = if (episode == null && season != null && requestedEpisode != null) {
            watchlist.cachedEpisodes(title.id, season)?.firstOrNull { candidate ->
                candidate.episodeNumber >= requestedEpisode && candidate.airDate?.let {
                    runCatching { LocalDate.parse(it) }.getOrNull()
                }?.let { !it.isAfter(LocalDate.now()) } == true
            }
        } else null
        val episodeNumber = episode?.episodeNumber ?: cachedEpisode?.episodeNumber ?: requestedEpisode
        openDetails(title.copy(
            matchedSeasonNumber = season,
            matchedAnimeSeasonNumber = selection?.anitubeSeasonNumber
                ?: title.matchedAnimeSeasonNumber,
        ), selection, episodeNumber)
    }
}
