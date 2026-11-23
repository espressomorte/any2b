package ua.any2b.core.toloka

import kotlin.test.*
import ua.any2b.core.tmdb.TmdbSeason

class TolokaEpisodeAvailabilityTest {
    private val seasons = listOf(TmdbSeason(1, "S1", 2026, 2), TmdbSeason(2, "S2", 2027, 2))
    @Test fun onlyExistingFilesAreAvailableAndRefreshAddsNewEpisode() {
        val one = listOf(TorrServerFile(1, "Show/S01E01.mkv", 1))
        assertEquals(setOf(1), TolokaEpisodeAvailability.index(one, seasons, 1)[1])
        val two = one + TorrServerFile(2, "Show/S01E02.mkv", 1)
        assertEquals(setOf(1, 2), TolokaEpisodeAvailability.index(two, seasons, 1)[1])
    }
    @Test fun unqualifiedFilesStayInSelectedReleaseSeason() {
        val index = TolokaEpisodeAvailability.index(listOf(TorrServerFile(1, "Show/01.mkv", 1)), seasons, 1)
        assertEquals(setOf(1), index[1])
        assertEquals(emptySet(), index[2])
    }
    @Test fun knownEmptySeasonIsNotUnknownAvailability() {
        assertEquals(emptySet(), TolokaEpisodeAvailability.index(emptyList(), seasons, 1)[1])
    }
    @Test fun explicitJapaneseAudioIsNotPromotedByUkrainianSubtitles() {
        val files = listOf(TorrServerFile(1, "Show.S01E01.1080p.Ukr.mkv", 1),
            TorrServerFile(2, "Show.S01E02.1080p.Jap.Sub.Ukr.mkv", 1))
        val translated = TolokaEpisodeAvailability.ukrainianFiles(files, true)
        assertEquals(listOf(1), translated.map { it.id })
        assertEquals(setOf(1), TolokaEpisodeAvailability.index(translated, seasons, 1)[1])
    }

}
