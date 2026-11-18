package ua.any2b.tvplayer

import androidx.media3.common.PlaybackException
import ua.any2b.domain.playback.StreamFailure

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
