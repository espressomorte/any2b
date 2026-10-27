package ua.any2b.core.matcher

import ua.any2b.core.anilist.AnimeDetails
import ua.any2b.core.anilist.Title
import ua.any2b.core.anitube.AnitubeSearchResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TitleMatcherTest {
    @Test
    fun yearDurationAndEpisodeCountChooseTheMatchingRelease() {
        val details = details(year = 2024, episodes = 12, duration = 24)
        val correct = hit(1, "Пригоди", 2024, 4, 12, 24)
        val wrong = hit(2, "Пригоди", 2021, 1, 1, 110)

        assertEquals(correct, TitleMatcher.confidentBest(TitleMatcher.match(details, listOf(wrong, correct))))
        assertNull(TitleMatcher.confidentBest(TitleMatcher.match(details, listOf(wrong))))
    }

    @Test
    fun metadataCanRankUkrainianTitlesWithoutTextSimilarity() {
        val details = details(year = 2024, episodes = 12, duration = 24)
        val correct = hit(1, "Українська назва", 2024, 4, 12, 24)
        val wrong = hit(2, "Інша українська назва", 2021, 1, 1, 110)

        assertEquals(correct, TitleMatcher.confidentBest(TitleMatcher.match(details, listOf(wrong, correct))))
    }

    @Test
    fun closeCandidatesStayAmbiguous() {
        val details = details(year = 2024, episodes = 12, duration = 24)
        val first = hit(1, "Пригоди", 2024, 4, 12, 24)
        val second = hit(2, "Пригоди", 2024, 3, 12, 24)

        assertNull(TitleMatcher.confidentBest(TitleMatcher.match(details, listOf(first, second))))
    }

    @Test
    fun totalEpisodeCountBreaksAnOtherwiseCloseTie() {
        val details = details(year = 2024, episodes = 12, duration = 24)
        val correct = hit(1, "Пригоди", 2024, 4, 12, 24)
        val other = hit(2, "Пригоди", 2024, 4, 24, 24)

        assertEquals(correct, TitleMatcher.confidentBest(TitleMatcher.match(details, listOf(other, correct))))
    }

    @Test
    fun nextAiringEpisodeBoundsAiredEpisodesWhenTotalIsUnknown() {
        val details = details(year = 2024, episodes = null, duration = 24, nextEpisode = 5)
        val plausible = hit(1, "Пригоди", 2024, 4, null, 24)
        val impossible = hit(2, "Пригоди", 2024, 5, null, 24)

        assertEquals(plausible, TitleMatcher.confidentBest(TitleMatcher.match(details, listOf(impossible, plausible))))
        assertNull(TitleMatcher.confidentBest(TitleMatcher.match(details, listOf(impossible))))
    }

    @Test
    fun synonymCanProvideTheTitleMatch() {
        val details = details(year = null, episodes = null, duration = null)
            .copy(synonyms = listOf("Alternate Name"))
        val release = hit(1, "Alternate Name", null, null, null, null)

        assertEquals(release, TitleMatcher.confidentBest(TitleMatcher.match(details, listOf(release))))
    }

    private fun details(year: Int?, episodes: Int?, duration: Int?, nextEpisode: Int? = null) =
        AnimeDetails(
            id = 1,
            title = Title("Пригоди", null, null),
            cover = null,
            banner = null,
            description = null,
            score = null,
            episodes = episodes,
            durationMinutes = duration,
            format = "TV",
            status = null,
            seasonYear = year,
            genres = emptyList(),
            studios = emptyList(),
            nextEpisode = nextEpisode,
        )

    private fun hit(id: Int, title: String, year: Int?, aired: Int?, total: Int?, minutes: Int?) =
        AnitubeSearchResult(
            newsId = id,
            title = title,
            pageUrl = "https://anitube.in.ua/$id-sample.html",
            year = year,
            airedEpisodes = aired,
            totalEpisodes = total,
            durationMinutes = minutes,
        )
}
