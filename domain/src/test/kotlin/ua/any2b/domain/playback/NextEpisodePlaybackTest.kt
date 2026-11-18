package ua.any2b.domain.playback

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NextEpisodePlaybackTest {
    @Test
    fun resolvesTorrentContinuationOnlyWhenTopicAndEpisodeExist() {
        val request = nextEpisodePlaybackRequest(12, true, 2, 7,
            PlaybackSource.TORRENT, true, "topic-1", null, null,
            null, null, 0, null)
        assertEquals(NextEpisodePlaybackRequest(12, 2, 8,
            PlaybackSource.TORRENT, NextEpisodeSource.Torrent("topic-1")), request)
        assertNull(nextEpisodePlaybackRequest(12, true, 2, 7,
            PlaybackSource.TORRENT, true, null, null, null,
            null, null, 0, null))
    }

    @Test
    fun resolvesAniTubeSeasonAndProviderContext() {
        assertEquals(NextEpisodeSource.AniTube(5, 99, "https://example.test/99", 2, "Далі Буде"),
            nextEpisodePlaybackRequest(12, true, 2, 7,
                PlaybackSource.ANITUBE, true, null, 5, 4,
                99, "https://example.test/99", 2, "Далі Буде")?.sourceData)
    }

    @Test
    fun rejectsUnavailableNextEpisodeAndMovies() {
        assertNull(nextEpisodePlaybackRequest(12, true, 2, 7,
            PlaybackSource.ANITUBE, false, null, 5, null,
            null, null, 0, null))
        assertNull(nextEpisodePlaybackRequest(12, false, null, null,
            PlaybackSource.ANITUBE, true, null, null, null,
            null, null, 0, null))
    }
}
