package ua.any2b.core.toloka

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import ua.any2b.core.tmdb.TmdbDetails
import ua.any2b.core.tmdb.TmdbMediaType
import ua.any2b.core.tmdb.TmdbTitle

class TolokaTmdbRankerTest {
    private val life = details("Життя за рік", "Life in a Year", 2020)

    @Test
    fun ranksDualTitleAndYearAboveUncertainResults() {
        val ranked = TolokaTmdbRanker.rank(life, listOf(
            hit("pdf", "Життя за рік [pdf]"),
            hit("wrong-year", "Життя за рік / Life in a Year (2022) WEB-DL"),
            hit("only-uk", "Життя за рік (2020) 1080p"),
            hit("both", "Життя за рік / Life in a Year (2020) WEB-DL"),
        ))
        assertEquals(listOf("both"), ranked.visible.map { it.id })
        assertEquals(setOf("wrong-year", "only-uk"), ranked.weak.map { it.id }.toSet())
    }

    @Test
    fun excludesNonVideoMediaEvenWhenNameMatches() {
        val names = listOf("Life in a Year audiobook m4b", "Life in a Year soundtrack FLAC",
            "Life in a Year [epub]", "Life in a Year книга", "Life in a Year.mp3")
        names.forEach { assertFalse(TolokaTmdbRanker.isVideoCandidate(hit("x", it)), it) }
    }

    @Test
    fun doesNotMatchWordInsideAnotherWord() {
        val ikiru = details("Жити", "Ikiru", 1952)
        val ranked = TolokaTmdbRanker.rank(ikiru,
            listOf(hit("unrelated", "Як вижити в раю (2022) WEB-DL")))
        assertEquals(emptyList(), ranked.visible)
        assertEquals(listOf("unrelated"), ranked.weak.map { it.id })
    }

    @Test
    fun movieCollectionAndRemakeAreUncertain() {
        val matrix = details("Матриця", "The Matrix", 1999)
        val ranked = TolokaTmdbRanker.rank(matrix, listOf(
            hit("original", "Матриця / The Matrix (1999) BDRip"),
            hit("remake", "Матриця: Воскресіння / The Matrix Resurrections (2021) BDRip"),
            hit("collection", "Матриця / The Matrix: Trilogy (1999-2003) BDRip"),
        ))
        assertEquals(listOf("original"), ranked.visible.map { it.id })
        assertEquals(setOf("remake", "collection"), ranked.weak.map { it.id }.toSet())
    }

    private fun details(uk: String, en: String, year: Int) = TmdbDetails(
        title = TmdbTitle(1, TmdbMediaType.MOVIE, uk, en, year, null, null, null,
            ukrainianTitle = uk, englishTitle = en, originalLanguage = "en"),
        genres = emptyList(), status = null, runtimeMinutes = null,
        seasons = null, episodes = null, collectionName = null,
    )

    private fun hit(id: String, title: String) = TolokaHit(
        id, title, "https://toloka.to/t$id", "2 GB", null, null)
}
