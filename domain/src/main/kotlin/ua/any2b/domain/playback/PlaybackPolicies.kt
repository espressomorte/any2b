package ua.any2b.domain.playback

import ua.any2b.domain.playback.toloka.TolokaFileDecision
import ua.any2b.domain.playback.toloka.TolokaWatchlistPlayback

enum class StreamFailure { TRANSIENT, AUTH, MISSING, PARSING, SUBTITLE, DECODER, OTHER }

fun canAdvanceToNextEpisode(
    isTv: Boolean,
    seasonNumber: Int,
    episodeNumber: Int,
    source: PlaybackSource?,
    nextEpisodeAvailable: Boolean,
    tolokaTopicId: String?,
): Boolean = isTv && seasonNumber > 0 && episodeNumber > 0 && nextEpisodeAvailable && when (source) {
    PlaybackSource.TORRENT -> !tolokaTopicId.isNullOrBlank()
    PlaybackSource.ANITUBE -> true
    null -> false
}

fun nextEpisodeNumber(currentEpisode: Int, canAdvance: Boolean): Int? =
    currentEpisode.takeIf { canAdvance && it > 0 }?.plus(1)

fun torrentResumePositionMs(savedPositionMs: Long, durationMs: Long): Long? =
    if (savedPositionMs > 0L && durationMs > 0L)
        savedPositionMs.coerceAtMost(durationMs - 1L).coerceAtLeast(0L)
    else null

fun anitubeResumePositionMs(progress: Float, durationMs: Long): Long? =
    if (durationMs > 0L && progress in 0.01f..0.98f)
        (durationMs * progress).toLong().coerceIn(0L, durationMs - 1L)
    else null

fun classifyHttpPlaybackStatus(statusCode: Int): StreamFailure = when (statusCode) {
    401, 403 -> StreamFailure.AUTH
    404, 410, 416 -> StreamFailure.MISSING
    in 500..599, 408, 429 -> StreamFailure.TRANSIENT
    else -> StreamFailure.OTHER
}

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

/** A host-facing effect; the host performs Android work for the selected source. */
sealed interface PlaybackHostEffect {
    data class PrepareSource(val request: PlaybackPreparationRequest) : PlaybackHostEffect
    data class LaunchPlayback(val request: PlaybackRequest) : PlaybackHostEffect
    data class ShowError(val message: String) : PlaybackHostEffect
    data class RetrySource(val source: PlaybackSource) : PlaybackHostEffect
    data object Finish : PlaybackHostEffect
}

data class PlaybackTransitionResult(
    val accepted: Boolean,
    val effects: List<PlaybackHostEffect> = emptyList(),
)

/** Source preparation target selected by domain policy, before a playable URL exists. */
sealed interface PlaybackPreparationRequest {
    data object AniTubeEpisode : PlaybackPreparationRequest
    data class TolokaFile(val fileId: Int) : PlaybackPreparationRequest
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

fun decideAutoPlaybackEffect(
    request: AutoPlaybackRequest,
    detailsReady: Boolean,
    torrentFilesReady: Boolean,
    savedFileAvailable: Boolean,
    episodeFileId: Int?,
    alreadyStarted: Boolean,
    failed: Boolean,
): PlaybackHostEffect? = when (val decision = decideAutoPlayback(request, detailsReady,
    torrentFilesReady, savedFileAvailable, episodeFileId, alreadyStarted, failed)) {
    AutoPlaybackDecision.None, AutoPlaybackDecision.Wait -> null
    AutoPlaybackDecision.StartAniTube -> PlaybackHostEffect.PrepareSource(
        PlaybackPreparationRequest.AniTubeEpisode)
    is AutoPlaybackDecision.StartTorrent -> PlaybackHostEffect.PrepareSource(
        PlaybackPreparationRequest.TolokaFile(decision.fileId))
    AutoPlaybackDecision.MissingTorrentFile -> PlaybackHostEffect.ShowError(
        "У вибраному torrent не знайдено потрібний відеофайл")
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
