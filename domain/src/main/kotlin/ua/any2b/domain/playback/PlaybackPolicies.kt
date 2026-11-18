package ua.any2b.domain.playback

import ua.any2b.domain.playback.toloka.TolokaFileDecision
import ua.any2b.domain.playback.toloka.TolokaWatchlistPlayback

data class AutoPlaybackRequest(
    val source: PlaybackSource?,
    val episodeNumber: Int,
    val savedFileId: Int? = null,
)

sealed interface AutoPlaybackDecision {
    data object None : AutoPlaybackDecision
    data object Wait : AutoPlaybackDecision
    data object StartAniTube : AutoPlaybackDecision
    data object MissingTorrentFile : AutoPlaybackDecision
    data class StartTorrent(val fileId: Int) : AutoPlaybackDecision
}

fun decideAutoPlayback(
    request: AutoPlaybackRequest,
    detailsReady: Boolean,
    torrentFilesReady: Boolean,
    savedFileAvailable: Boolean,
    episodeFileId: Int?,
    alreadyStarted: Boolean,
    failed: Boolean,
): AutoPlaybackDecision {
    if (alreadyStarted || failed || request.source == null ||
        (request.episodeNumber <= 0 && request.savedFileId == null)) return AutoPlaybackDecision.None
    if (!detailsReady) return AutoPlaybackDecision.Wait
    return when (request.source) {
        PlaybackSource.TORRENT -> when (val decision = TolokaWatchlistPlayback().resolveFile(
            torrentFilesReady, request.savedFileId, savedFileAvailable, episodeFileId)) {
            TolokaFileDecision.Wait -> AutoPlaybackDecision.Wait
            TolokaFileDecision.Missing -> AutoPlaybackDecision.MissingTorrentFile
            is TolokaFileDecision.Start -> AutoPlaybackDecision.StartTorrent(decision.fileId)
        }
        PlaybackSource.ANITUBE -> AutoPlaybackDecision.StartAniTube
    }
}

fun watchlistSeason(
    explicitSeason: Int?,
    matchedSeason: Int?,
    progressSeason: Int?,
): Int? = explicitSeason ?: matchedSeason ?: progressSeason

fun watchlistEpisode(
    explicitEpisode: Int?,
    progressEpisode: Int?,
    progressFraction: Float?,
    isTv: Boolean,
    hasSavedSelection: Boolean,
): Int? = explicitEpisode ?: progressEpisode?.let { episode ->
    if ((progressFraction ?: 0f) >= 0.95f) episode + 1 else episode
} ?: if (isTv && hasSavedSelection) 1 else null
