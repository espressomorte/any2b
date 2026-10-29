package ua.any2b.core.tmdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ua.any2b.core.anilist.AnimeDetails
import ua.any2b.core.anilist.Title

class AniListTmdbMatcherTest {
    private val anime = AnimeDetails(
        id = 1, title = Title("Shingeki no Kyojin", "Attack on Titan", "進撃の巨人"),
        cover = null, banner = null, description = null, score = null,
        episodes = null, durationMinutes = null, format = "TV", status = null,
        seasonYear = 2013, genres = emptyList(), studios = emptyList(),
    )

    private fun title(id: Int, type: TmdbMediaType = TmdbMediaType.TV,
        year: Int = 2013, genres: Set<Int> = setOf(16), language: String = "ja") =
        TmdbTitle(id, type, "Attack on Titan", "Shingeki no Kyojin", year,
            null, null, null, ukrainianTitle = "Атака титанів",
            englishTitle = "Attack on Titan", originalLanguage = language,
            genreIds = genres, originCountries = setOf("JP"))

    @Test
    fun typeYearAndAnimationExcludeWrongResultsRegardlessOfServerOrder() {
        val wrongType = title(1, TmdbMediaType.MOVIE)
        val wrongYear = title(2, year = 2021)
        val noAnimation = title(3, genres = setOf(18))
        val correct = title(4)
        val matches = AniListTmdbMatcher.rank(anime,
            listOf(wrongType, wrongYear, noAnimation, correct))
        assertEquals(listOf(4), matches.map { it.title.id })
        assertEquals(correct, AniListTmdbMatcher.clearSuggestion(matches))
    }

    @Test
    fun closeResultsRequireExplicitChoiceAndCountryLanguageAreOnlyBonuses() {
        val first = title(1)
        val second = title(2, language = "en").copy(originCountries = emptySet())
        val ranked = AniListTmdbMatcher.rank(anime, listOf(second, first))
        assertEquals(listOf(1, 2), ranked.map { it.title.id })
        assertNull(AniListTmdbMatcher.clearSuggestion(ranked))
        assertTrue(AniListTmdbMatcher.rank(anime, listOf(second)).isNotEmpty())
    }

    @Test
    fun laterSeasonYearCanMatchSeriesWhoseFirstYearIsOlder() {
        val secondSeason = anime.copy(
            title = Title("Cyberpunk: Edgerunners 2", "Cyberpunk: Edgerunners 2", null),
            seasonYear = 2026,
        )
        val series = TmdbTitle(105248, TmdbMediaType.TV,
            "Cyberpunk: Edgerunners", "Cyberpunk: Edgerunners", 2022,
            null, null, null, ukrainianTitle = "Cyberpunk: Edgerunners",
            englishTitle = "Cyberpunk: Edgerunners", originalLanguage = "ja",
            genreIds = setOf(16), originCountries = setOf("JP"))
        assertTrue(AniListTmdbMatcher.rank(secondSeason, listOf(series)).isEmpty())
        assertTrue(TmdbSeasonMatcher.shouldInspect(secondSeason, series))
        val matchedSeason = TmdbSeasonMatcher.enrich(secondSeason, series,
            listOf(TmdbSeason(1, "Сезон 1", 2022),
                TmdbSeason(2, "Cyberpunk: Edgerunners 2", 2026)))
        assertEquals(2026, matchedSeason.matchedSeasonYear)
        assertEquals("Cyberpunk: Edgerunners 2", matchedSeason.matchedSeasonTitle)
        assertEquals(matchedSeason, AniListTmdbMatcher.rank(secondSeason,
            listOf(matchedSeason)).single().title)
    }
}
