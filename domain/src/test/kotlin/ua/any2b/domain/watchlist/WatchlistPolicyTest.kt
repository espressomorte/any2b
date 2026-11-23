package ua.any2b.domain.watchlist

import kotlin.test.*

class WatchlistPolicyTest {
    @Test fun tmdbAnnouncementNeverGrantsTorrentAvailability() {
        assertTrue(WatchlistPolicy.available(1, true, true, setOf(1)))
        assertFalse(WatchlistPolicy.available(2, true, true, setOf(1)))
        assertFalse(WatchlistPolicy.available(2, true, true, emptySet()))
        assertFalse(WatchlistPolicy.available(2, true, true, null))
        assertTrue(WatchlistPolicy.available(2, true, true, setOf(1, 2)))
    }
    @Test fun actualTranslatedFileCanBeAvailableBeforeTmdbAirDate() {
        assertTrue(WatchlistPolicy.available(1, false, true, setOf(1)))
        assertFalse(WatchlistPolicy.available(1, false, false, null))
    }
    @Test fun allThreeCompletionConditionsAreRequired() {
        val all = (1..12).toSet()
        assertFalse(WatchlistPolicy.seasonComplete(true, all, (1..11).toSet(), (1..11).toSet()))
        assertFalse(WatchlistPolicy.seasonComplete(true, all, all, (1..11).toSet()))
        assertFalse(WatchlistPolicy.seasonComplete(false, all, all, all))
        assertFalse(WatchlistPolicy.seasonComplete(true, all, null, all))
        assertFalse(WatchlistPolicy.seasonComplete(true, emptySet(), all, all))
        assertTrue(WatchlistPolicy.seasonComplete(true, all, all, all))
    }
    @Test fun skippingDirectlyToFinaleDoesNotCompleteSeason() {
        assertFalse(WatchlistPolicy.seasonComplete(true, (1..12).toSet(), (1..12).toSet(), setOf(12)))
        assertFalse(WatchlistPolicy.seasonComplete(true, (1..12).toSet(), (1..12).toSet(), (2..12).toSet()))
    }
    @Test fun savedFileForE1CannotLaunchWhenRequestedE2IsMissingOrDifferent() {
        assertNull(WatchlistPolicy.savedFileForEpisode(1, 2, null))
        assertNull(WatchlistPolicy.savedFileForEpisode(1, 2, 2))
        assertEquals(2, WatchlistPolicy.savedFileForEpisode(2, 2, 2))
        assertEquals(1, WatchlistPolicy.savedFileForEpisode(1, 0, null))
    }
    @Test fun genericNamesDoNotRepeatTheNumber() {
        listOf("", "  ", "Episode 2", "EPISODE #2", "E2", "2", "Серія 2", "Епізод №2", "Друга серія", "дванадцята серія", "Серія друга").forEach {
            assertNull(WatchlistPolicy.episodeSubtitle(it), it)
        }
        assertEquals("Stray Dog Strut", WatchlistPolicy.episodeSubtitle(" Stray Dog Strut "))
        assertEquals("The Second Coming", WatchlistPolicy.episodeSubtitle("The Second Coming"))
    }
}
