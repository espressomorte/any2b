package ua.any2b.tvplayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.tvplayer.data.TmdbPlaybackSelection

class NextEpisodePlaybackTest {
    @Test
    fun resolvesTorrentContinuationOnlyWhenTopicAndEpisodeExist() {
        val request = nextEpisodePlaybackRequest(12, TmdbMediaType.TV, 2, 7,
            TmdbPlaybackSelection.Source.TORRENT, true, "topic-1", null, null,
            null, null, 0, null)
        assertEquals(NextEpisodePlaybackRequest(12, 2, 8,
            TmdbPlaybackSelection.Source.TORRENT, NextEpisodeSource.Torrent("topic-1")), request)
        assertNull(nextEpisodePlaybackRequest(12, TmdbMediaType.TV, 2, 7,
            TmdbPlaybackSelection.Source.TORRENT, true, null, null, null,
            null, null, 0, null))
    }

    @Test
    fun resolvesAniTubeSeasonAndProviderContextWithoutIntent() {
        assertEquals(NextEpisodeSource.AniTube(5, 99, "https://example.test/99", 2, "Далі Буде"),
            nextEpisodePlaybackRequest(12, TmdbMediaType.TV, 2, 7,
                TmdbPlaybackSelection.Source.ANITUBE, true, null, 5, 4,
                99, "https://example.test/99", 2, "Далі Буде")?.sourceData)
    }

    @Test
    fun rejectsUnavailableNextEpisodeAndMovies() {
        assertNull(nextEpisodePlaybackRequest(12, TmdbMediaType.TV, 2, 7,
            TmdbPlaybackSelection.Source.ANITUBE, false, null, 5, null,
            null, null, 0, null))
        assertNull(nextEpisodePlaybackRequest(12, TmdbMediaType.MOVIE, null, null,
            TmdbPlaybackSelection.Source.ANITUBE, true, null, null, null,
            null, null, 0, null))
    }
}
