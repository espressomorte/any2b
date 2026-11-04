package ua.any2b.tvplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ua.any2b.core.anilist.AnimeDetails
import ua.any2b.core.anilist.Title
import ua.any2b.core.tmdb.TmdbDetails
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle

class DetailsUiModelTest {
    @Test fun animeMapperLeavesTmdbOnlyFieldsAbsent() {
        val ui = AnimeDetails(
            id = 31,
            title = Title(romaji = "Cowboy Bebop", english = null, native = "カウボーイビバップ"),
            cover = "poster", banner = null, description = "Story", score = 85,
            episodes = 26, durationMinutes = 24, format = "TV", status = "FINISHED",
            seasonYear = 1998, genres = listOf("Sci-Fi"), studios = emptyList(),
        ).toUi()

        assertEquals(DetailsSource.ANILIST, ui.source)
        assertEquals("Cowboy Bebop", ui.title)
        assertEquals(8.5, ui.rating!!, 0.0)
        assertNull(ui.countries)
        assertNull(ui.cast)
        assertNull(ui.directors)
        assertNull(ui.seasons)
        assertNull(ui.studios)
    }

    @Test fun tmdbMapperLeavesAnimeOnlyFieldsAbsentAndMapsAvailableCredits() {
        val details = TmdbDetails(
            title = TmdbTitle(
                id = 44, type = TmdbMediaType.TV, title = "Series", originalTitle = "Original",
                year = 2020, posterUrl = "poster", overview = null, rating = 7.2,
                ukrainianTitle = "Серіал", originCountries = setOf("JP"),
            ),
            genres = listOf("Drama"), status = null, runtimeMinutes = 23,
            seasons = 2, episodes = 24, collectionName = null,
            directors = listOf("Director"), cast = listOf("Actor"), countryCodes = listOf("JP"),
        ).toUi()

        assertEquals(DetailsSource.TMDB, details.source)
        assertEquals("Серіал", details.title)
        assertEquals(listOf("JP"), details.countries)
        assertEquals(listOf("Actor"), details.cast)
        assertEquals(listOf("Director"), details.directors)
        assertNull(details.studios)
        assertNull(details.anilistDetails)
    }
}
