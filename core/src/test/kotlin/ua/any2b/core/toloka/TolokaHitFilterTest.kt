package ua.any2b.core.toloka

import kotlin.test.Test
import kotlin.test.assertEquals
import ua.any2b.core.anilist.AnimeDetails
import ua.any2b.core.anilist.Title

class TolokaHitFilterTest {
    private val details = AnimeDetails(
        id = 1, title = Title("Example Show 2nd Season", "Example Show Season 2", null),
        cover = null, banner = null, description = null, score = null,
        episodes = 12, durationMinutes = 24, format = "TV", status = "FINISHED",
        seasonYear = 2024, genres = emptyList(), studios = emptyList(),
    )

    @Test
    fun keepsMultiplePlausibleReleasesAndRejectsWrongYearSeasonOrTitle() {
        val hits = listOf(
            hit("1", "Example Show Season 2 [2024] 1080p"),
            hit("2", "Example Show 2nd Season 2024 Ukrainian dub"),
            hit("3", "Example Show Season 3 [2024]"),
            hit("4", "Example Show Season 2 [2021]"),
            hit("5", "Different Show Season 2 [2024]"),
        )
        assertEquals(listOf("1", "2"), TolokaHitFilter.filter(details, null, hits).map { it.id })
    }

    @Test
    fun matchesUkrainianReleaseTitleWhenPresent() {
        val hits = listOf(hit("1", "Прикладне шоу 2 сезон 2024 WEB-DL"))
        assertEquals(listOf("1"), TolokaHitFilter.filter(details, "Прикладне шоу 2 сезон", hits).map { it.id })
    }

    private fun hit(id: String, title: String) = TolokaHit(id, title, "https://toloka.to/t$id", "1 GB", null, null)
}
