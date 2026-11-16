package ua.any2b.tvplayer

import androidx.media3.common.PlaybackException

internal enum class StreamFailure { TRANSIENT, AUTH, MISSING, PARSING, SUBTITLE, DECODER, OTHER }

internal fun canAdvanceToNextEpisode(
    mediaType: String?,
    seasonNumber: Int,
    episodeNumber: Int,
    source: String?,
    nextEpisodeAvailable: Boolean,
    tolokaTopicId: String?,
): Boolean = mediaType == ua.any2b.core.tmdb.TmdbMediaType.TV.path &&
    seasonNumber > 0 && episodeNumber > 0 && nextEpisodeAvailable && when (source) {
        PlayerActivity.SOURCE_TOLOKA -> !tolokaTopicId.isNullOrBlank()
        PlayerActivity.SOURCE_ANITUBE_TMDB -> true
        else -> false
    }

internal fun nextEpisodeNumber(currentEpisode: Int, canAdvance: Boolean): Int? =
    currentEpisode.takeIf { canAdvance && it > 0 }?.plus(1)

internal fun torrentResumePositionMs(savedPositionMs: Long, durationMs: Long): Long? =
    if (savedPositionMs > 0L && durationMs > 0L)
        savedPositionMs.coerceAtMost(durationMs - 1L).coerceAtLeast(0L)
    else null

internal fun anitubeResumePositionMs(progress: Float, durationMs: Long): Long? =
    if (durationMs > 0L && progress in 0.01f..0.98f)
        (durationMs * progress).toLong().coerceIn(0L, durationMs - 1L)
    else null

internal fun classifyPlaybackErrorCode(errorCode: Int): StreamFailure = when (errorCode) {
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> StreamFailure.TRANSIENT
    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
    PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE -> StreamFailure.MISSING
    PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
    PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> StreamFailure.PARSING
    PlaybackException.ERROR_CODE_DECODING_FAILED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
    PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> StreamFailure.DECODER
    else -> StreamFailure.OTHER
}

internal fun classifyHttpPlaybackStatus(statusCode: Int): StreamFailure = when (statusCode) {
    401, 403 -> StreamFailure.AUTH
    404, 410, 416 -> StreamFailure.MISSING
    in 500..599, 408, 429 -> StreamFailure.TRANSIENT
    else -> StreamFailure.OTHER
}
