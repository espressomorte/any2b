package ua.any2b.domain.playback

/** Source-specific continuation data, ready for the host to turn into navigation extras. */
sealed interface NextEpisodeSource {
    data class Torrent(val topicId: String) : NextEpisodeSource
    data class AniTube(
        val animeSeason: Int,
        val releaseId: Int?,
        val pageUrl: String?,
        val voiceIndex: Int,
        val voiceName: String?,
    ) : NextEpisodeSource
}

enum class PlaybackSource { TORRENT, ANITUBE }

data class NextEpisodePlaybackRequest(
    val tmdbId: Int,
    val season: Int,
    val episode: Int,
    val source: PlaybackSource,
    val sourceData: NextEpisodeSource,
)

fun nextEpisodePlaybackRequest(
    tmdbId: Int,
    isTv: Boolean,
    season: Int?,
    currentEpisode: Int?,
    source: PlaybackSource?,
    nextEpisodeAvailable: Boolean,
    torrentTopicId: String?,
    currentAnimeSeason: Int?,
    savedAnimeSeason: Int?,
    releaseId: Int?,
    pageUrl: String?,
    voiceIndex: Int,
    voiceName: String?,
): NextEpisodePlaybackRequest? {
    if (tmdbId <= 0 || !isTv || season == null || season <= 0 ||
        currentEpisode == null || currentEpisode <= 0 || !nextEpisodeAvailable) return null
    val sourceData = when (source) {
        PlaybackSource.TORRENT -> torrentTopicId?.takeIf(String::isNotBlank)
            ?.let(NextEpisodeSource::Torrent) ?: return null
        PlaybackSource.ANITUBE -> NextEpisodeSource.AniTube(
            currentAnimeSeason?.takeIf { it > 0 } ?: savedAnimeSeason?.takeIf { it > 0 } ?: season,
            releaseId?.takeIf { it > 0 }, pageUrl, voiceIndex, voiceName)
        null -> return null
    }
    return NextEpisodePlaybackRequest(tmdbId, season, currentEpisode + 1, source, sourceData)
}
