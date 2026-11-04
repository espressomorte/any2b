package ua.any2b.core.anilist

import org.junit.Assert.assertEquals
import org.junit.Test
import ua.any2b.core.tmdb.TmdbSeason

class NumberedSeasonsTest {
    @Test fun onlyNumberedTvSequelsBecomePlayableSeasons() {
        fun card(id: Int, name: String, format: String, year: Int) = AnimeCard(
            id, Title(name, name, null), null, null, 12, format, null, year, null)
        val base = AnimeDetails(
            id = 1, title = Title("Example", "Example", null), cover = null, banner = null,
            description = null, score = null, episodes = 12, durationMinutes = 24,
            format = "TV", status = null, seasonYear = 2020,
            genres = emptyList(), studios = emptyList(),
            seasonRelations = listOf(
                AnimeRelation("SEQUEL", card(2, "Example Season 2", "TV", 2022)),
                AnimeRelation("SEQUEL", card(3, "Example OVA", "OVA", 2021)),
                AnimeRelation("SIDE_STORY", card(4, "Example Season 3", "TV", 2024)),
                AnimeRelation("SEQUEL", card(5, "Different Story", "TV", 2024)),
            ),
        )
        assertEquals(listOf(NumberedSeason(1, 12, 1), NumberedSeason(2, 10, 2),
            NumberedSeason(3, 8, null)), base.numberedSeasons(listOf(
            TmdbSeason(0, "Specials", 2020, 4),
            TmdbSeason(1, "Season 1", 2020, 12),
            TmdbSeason(2, "Season 2", 2022, 10),
            TmdbSeason(3, "Season 3", 2024, 8),
        )))
    }
}
