package ua.any2b.core.tmdb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import ua.any2b.core.anilist.AnimeDetails
import ua.any2b.core.anilist.Title

class AniListRomajiSelectorTest {
    private val tmdb = TmdbDetails(
        title = TmdbTitle(220542, TmdbMediaType.TV, "Монолог травниці",
            "薬屋のひとりごと", 2023, null, null, 8.7, genreIds = setOf(16)),
        genres = emptyList(), status = null, runtimeMinutes = 23,
        seasons = 1, episodes = 24, collectionName = null,
    )

    @Test
    fun choosesMainSeriesEvenWhenMiniAnimeIsFirst() {
        val mini = anime(170508, "Maomao no Hitorigoto", "猫猫のひとりごと", 2)
        val main = anime(161645, "Kusuriya no Hitorigoto", "薬屋のひとりごと", 24)
        assertEquals("Kusuriya no Hitorigoto",
            AniListRomajiSelector.select(tmdb, listOf(mini, main)))
    }

    @Test
    fun neverUsesSpinOffRomajiWhenMainSeriesIsMissing() {
        val mini = anime(170508, "Maomao no Hitorigoto", "猫猫のひとりごと", 2)
        assertNull(AniListRomajiSelector.select(tmdb, listOf(mini)))
    }

    private fun anime(id: Int, romaji: String, native: String, duration: Int) =
        AnimeDetails(id, Title(romaji, null, native), null, null, null, null,
            24, duration, "TV", "FINISHED", 2023, emptyList(), emptyList())
}
