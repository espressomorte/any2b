package ua.any2b.tvplayer

import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.tvplayer.data.TmdbPlaybackSelection

/** Source-specific continuation data, ready for the Android host to turn into navigation extras. */
internal sealed interface NextEpisodeSource {
    data class Torrent(val topicId: String) : NextEpisodeSource
    data class AniTube(
        val animeSeason: Int,
        val releaseId: Int?,
        val pageUrl: String?,
        val voiceIndex: Int,
        val voiceName: String?,
    ) : NextEpisodeSource
}

internal data class NextEpisodePlaybackRequest(
    val tmdbId: Int,
    val season: Int,
    val episode: Int,
    val source: TmdbPlaybackSelection.Source,
    val sourceData: NextEpisodeSource,
)

internal fun nextEpisodePlaybackRequest(
    tmdbId: Int,
    mediaType: TmdbMediaType,
    season: Int?,
    currentEpisode: Int?,
    source: TmdbPlaybackSelection.Source?,
    nextEpisodeAvailable: Boolean,
    torrentTopicId: String?,
    currentAnimeSeason: Int?,
    savedAnimeSeason: Int?,
    releaseId: Int?,
    pageUrl: String?,
    voiceIndex: Int,
    voiceName: String?,
): NextEpisodePlaybackRequest? {
    if (tmdbId <= 0 || mediaType != TmdbMediaType.TV || season == null || season <= 0 ||
        currentEpisode == null || currentEpisode <= 0 || !nextEpisodeAvailable) return null
    val next = currentEpisode + 1
    val sourceData = when (source) {
        TmdbPlaybackSelection.Source.TORRENT -> torrentTopicId?.takeIf(String::isNotBlank)
            ?.let(NextEpisodeSource::Torrent) ?: return null
        TmdbPlaybackSelection.Source.ANITUBE -> NextEpisodeSource.AniTube(
            currentAnimeSeason?.takeIf { it > 0 } ?: savedAnimeSeason?.takeIf { it > 0 } ?: season,
            releaseId?.takeIf { it > 0 }, pageUrl, voiceIndex, voiceName)
        null -> return null
    }
    return NextEpisodePlaybackRequest(tmdbId, season, next, source, sourceData)
}
