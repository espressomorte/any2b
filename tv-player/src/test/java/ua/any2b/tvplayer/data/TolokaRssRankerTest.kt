package ua.any2b.tvplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle

class TolokaRssRankerTest {
    private val now = 1_000_000_000L
    private val hour = 3_600_000L

    private fun title(id: Int, rating: Double?, type: TmdbMediaType = TmdbMediaType.TV) =
        TmdbTitle(id, type, "Title $id", null, null, null, null, rating)

    @Test fun freshnessDecaysAndUnratedTitleGetsNeutralRating() {
        val highRated = title(1, 8.8)
        val lowRated = title(2, 4.6)
        val unrated = title(3, null)
        val releases = listOf(highRated to now - 72 * hour,
            lowRated to now, unrated to now - 48 * hour)

        assertEquals(listOf(2, 1, 3), TolokaRssRanker.rank(releases, emptySet(), now)
            .map { it.id })
        assertEquals(listOf(1, 3, 2), TolokaRssRanker.rank(releases, emptySet(),
            now + 48 * hour).map { it.id })
    }

    @Test fun latestReleaseWinsForDuplicateAndTrendingAddsFourPoints() {
        val repeated = title(1, 6.0)
        val competitor = title(2, 8.0)
        val releases = listOf(repeated to now - 80 * hour,
            repeated to now - 8 * hour, competitor to now - 40 * hour)

        assertEquals(listOf(1, 2), TolokaRssRanker.rank(releases, emptySet(), now)
            .map { it.id })
        assertEquals(2, TolokaRssRanker.rank(releases, setOf(1), now).size)
        assertEquals(listOf(2, 1), TolokaRssRanker.rank(releases, setOf(2), now)
            .map { it.id })
    }
}
