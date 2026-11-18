package ua.any2b.tvplayer

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.domain.playback.PlaybackSource
import ua.any2b.domain.playback.StreamFailure
import ua.any2b.domain.playback.anitubeResumePositionMs
import ua.any2b.domain.playback.canAdvanceToNextEpisode
import ua.any2b.domain.playback.classifyHttpPlaybackStatus
import ua.any2b.domain.playback.nextEpisodeNumber
import ua.any2b.domain.playback.torrentResumePositionMs

class PlayerPoliciesTest {
    @Test
    fun nextEpisodeRequiresTvValidPositionAndAvailableSuccessor() {
        assertTrue(canAdvance(source = PlayerActivity.SOURCE_ANITUBE_TMDB))
        assertFalse(canAdvance(source = PlayerActivity.SOURCE_ANITUBE_TMDB,
            nextEpisodeAvailable = false))
        assertFalse(canAdvance(source = PlayerActivity.SOURCE_ANITUBE_TMDB, season = 0))
        assertFalse(canAdvance(source = PlayerActivity.SOURCE_ANITUBE_TMDB, episode = 0))
        assertFalse(canAdvance(source = PlayerActivity.SOURCE_ANITUBE_TMDB, mediaType = "movie"))
        assertFalse(canAdvance(source = "external"))
    }

    @Test
    fun torrentNextEpisodeRequiresTopicButAnitubeDoesNot() {
        assertFalse(canAdvance(source = PlayerActivity.SOURCE_TOLOKA))
        assertTrue(canAdvance(source = PlayerActivity.SOURCE_TOLOKA, topicId = "topic-42"))
        assertTrue(canAdvance(source = PlayerActivity.SOURCE_ANITUBE_TMDB))
    }

    @Test
    fun nextEpisodeNumberIsAbsentWhenTransitionIsNotAllowed() {
        assertEquals(8, nextEpisodeNumber(7, canAdvance = true))
        assertNull(nextEpisodeNumber(7, canAdvance = false))
        assertNull(nextEpisodeNumber(0, canAdvance = true))
        assertNull(nextEpisodeNumber(-1, canAdvance = true))
    }

    @Test
    fun torrentResumePositionIsClampedInsideDuration() {
        assertEquals(4_500L, torrentResumePositionMs(4_500L, 10_000L))
        assertEquals(9_999L, torrentResumePositionMs(20_000L, 10_000L))
        assertNull(torrentResumePositionMs(0L, 10_000L))
        assertNull(torrentResumePositionMs(1L, 0L))
    }

    @Test
    fun anitubeResumeUsesOnlyMeaningfulProgressAndKnownDuration() {
        assertEquals(2_500L, anitubeResumePositionMs(0.25f, 10_000L))
        assertNull(anitubeResumePositionMs(0f, 10_000L))
        assertNull(anitubeResumePositionMs(0.99f, 10_000L))
        assertNull(anitubeResumePositionMs(0.5f, 0L))
    }

    @Test
    fun playbackErrorCodesAreGroupedForRecoveryAndUserMessages() {
        assertEquals(StreamFailure.TRANSIENT, classifyPlaybackErrorCode(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT))
        assertEquals(StreamFailure.MISSING, classifyPlaybackErrorCode(
            PlaybackException.ERROR_CODE_IO_READ_POSITION_OUT_OF_RANGE))
        assertEquals(StreamFailure.PARSING, classifyPlaybackErrorCode(
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED))
        assertEquals(StreamFailure.DECODER, classifyPlaybackErrorCode(
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED))
        assertEquals(StreamFailure.OTHER, classifyPlaybackErrorCode(Int.MIN_VALUE))
    }

    @Test
    fun httpPlaybackFailuresDistinguishCredentialsMissingFilesAndRetryableResponses() {
        assertEquals(StreamFailure.AUTH, classifyHttpPlaybackStatus(403))
        assertEquals(StreamFailure.MISSING, classifyHttpPlaybackStatus(416))
        assertEquals(StreamFailure.TRANSIENT, classifyHttpPlaybackStatus(503))
        assertEquals(StreamFailure.TRANSIENT, classifyHttpPlaybackStatus(429))
        assertEquals(StreamFailure.OTHER, classifyHttpPlaybackStatus(400))
    }

    private fun canAdvance(
        source: String,
        mediaType: String = TmdbMediaType.TV.path,
        season: Int = 1,
        episode: Int = 7,
        nextEpisodeAvailable: Boolean = true,
        topicId: String? = null,
    ) = canAdvanceToNextEpisode(
        isTv = mediaType == TmdbMediaType.TV.path,
        seasonNumber = season,
        episodeNumber = episode,
        source = when (source) {
            PlayerActivity.SOURCE_TOLOKA -> PlaybackSource.TORRENT
            PlayerActivity.SOURCE_ANITUBE_TMDB -> PlaybackSource.ANITUBE
            else -> null
        },
        nextEpisodeAvailable = nextEpisodeAvailable,
        tolokaTopicId = topicId,
    )
}
