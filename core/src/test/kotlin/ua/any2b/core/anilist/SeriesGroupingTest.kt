package ua.any2b.core.anilist

import kotlin.test.Test
import kotlin.test.assertEquals

class SeriesGroupingTest {
    private fun card(id: Int, name: String, year: Int, status: String = "FINISHED",
        format: String = "TV") = AnimeCard(id, Title(name, name, null), null, null,
        12, format, status, year, null)

    @Test fun finishedSeasonsBecomeOneTitleOpeningTheFirstSeason() {
        val grouped = groupNumberedTvSeasons(listOf(
            card(3, "The Apothecary Diaries Season 3", 2026),
            card(2, "The Apothecary Diaries Season 2", 2025),
            card(1, "The Apothecary Diaries", 2023),
        ))
        assertEquals(1, grouped.size)
        assertEquals(1, grouped.single().id)
        assertEquals("The Apothecary Diaries", grouped.single().title.display)
    }

    @Test fun ongoingSeasonIsDefaultAndExtrasStaySeparate() {
        val grouped = groupNumberedTvSeasons(listOf(
            card(1, "The Apothecary Diaries", 2023),
            card(2, "The Apothecary Diaries Season 2", 2025),
            card(3, "The Apothecary Diaries Season 3", 2026, "RELEASING"),
            card(4, "The Apothecary Diaries OVA", 2025, format = "OVA"),
        ))
        assertEquals(listOf(3, 4), grouped.map { it.id })
        assertEquals("The Apothecary Diaries", grouped.first().title.display)
    }

    @Test fun suffixesAreRemovedOnlyForTvSeasons() {
        val grouped = groupNumberedTvSeasons(listOf(
            card(1, "Монолог травниці (1 сезон)", 2023),
            card(2, "Монолог травниці (2 сезон)", 2025),
            card(3, "Монолог травниці (2 сезон)", 2025, format = "MOVIE"),
        ))
        assertEquals(listOf(1, 3), grouped.map { it.id })
        assertEquals("Монолог травниці", grouped.first().title.display)
        assertEquals("Монолог травниці (2 сезон)", grouped.last().title.display)
    }
}
