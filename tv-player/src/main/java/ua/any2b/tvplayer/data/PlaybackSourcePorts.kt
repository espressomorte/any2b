package ua.any2b.tvplayer.data

import ua.any2b.core.tmdb.TmdbTitle
import ua.any2b.domain.playback.toloka.TolokaEpisodeContext
import ua.any2b.domain.playback.toloka.TolokaWatchlistPlayback

/** Provider-owned operations exposed to the playback host without leaking into its coordinator. */
internal interface AniTubePlaybackPort {
    fun onWatchlistState(request: AniTubeWatchlistRequest, state: AnitubeState): AniTubeWatchlistAction
    fun playbackSeasons(
        title: TmdbTitle,
        requestedTmdbSeason: Int,
        requestedAnimeSeason: Int?,
        releaseId: Int?,
        found: AnitubeState.Found?,
    ): AniTubePlaybackSeasons
}

internal interface TolokaPlaybackPort {
    fun episodeContext(
        fileSeason: Int?,
        fileEpisodeStart: Int?,
        fileEpisodeEnd: Int?,
        folderSeason: Int?,
        requestedSeason: Int,
        requestedEpisode: Int?,
        matchedSeason: Int?,
        isTv: Boolean,
        mappedEpisodeForSeason: (Int) -> Int?,
    ): TolokaEpisodeContext

    fun classifyServerError(error: Throwable): String
}

internal class PlaybackSourcePorts(
    val aniTube: AniTubePlaybackPort = AniTubeWatchlistPlayback(),
    val toloka: TolokaPlaybackPort = TolokaPlaybackAdapter(),
)

private class TolokaPlaybackAdapter : TolokaPlaybackPort {
    private val policy = TolokaWatchlistPlayback()

    override fun episodeContext(
        fileSeason: Int?,
        fileEpisodeStart: Int?,
        fileEpisodeEnd: Int?,
        folderSeason: Int?,
        requestedSeason: Int,
        requestedEpisode: Int?,
        matchedSeason: Int?,
        isTv: Boolean,
        mappedEpisodeForSeason: (Int) -> Int?,
    ): TolokaEpisodeContext = policy.episodeContext(fileSeason, fileEpisodeStart,
        fileEpisodeEnd, folderSeason, requestedSeason, requestedEpisode, matchedSeason,
        isTv, mappedEpisodeForSeason)

    override fun classifyServerError(error: Throwable): String = policy.classifyServerError(error)
}
