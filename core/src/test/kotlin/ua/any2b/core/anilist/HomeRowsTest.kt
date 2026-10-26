package ua.any2b.core.anilist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeRowsTest {
    @Test
    fun historyIsFirstFavoritesLastAndEmptyRowsAreHidden() {
        val catalog = listOf(
            HomeRow("empty", "Empty", emptyList()),
            HomeRow("trending", "Trending", listOf(card(1))),
        )

        assertEquals(
            listOf("history", "trending", "favorites"),
            rowsWithLocal(catalog, listOf(card(2)), listOf(card(3))).map { it.id },
        )
        assertEquals(listOf("trending"), rowsWithLocal(catalog, emptyList(), emptyList()).map { it.id })
    }

    @Test
    fun fullListAppearsOnlyWhenThereIsAFourteenthTitle() {
        val thirteen = HomeRow("history", "History", (1..13).map(::card))
        val fourteen = HomeRow("history", "History", (1..14).map(::card))

        assertFalse(thirteen.hasFullList)
        assertEquals(13, thirteen.previewItems.size)
        assertTrue(fourteen.hasFullList)
        assertEquals((1..13).toList(), fourteen.previewItems.map { it.id })
        assertEquals(14, HOME_FETCH_SIZE)
    }

    private fun card(id: Int) = AnimeCard(
        id = id,
        title = Title("Title $id", null, null),
        cover = null,
        score = null,
        episodes = null,
        format = null,
        status = null,
        seasonYear = null,
        nextEpisode = null,
    )
}
