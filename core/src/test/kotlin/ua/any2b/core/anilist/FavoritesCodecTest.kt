package ua.any2b.core.anilist

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FavoritesCodecTest {
    @Test
    fun savedFavoritesKeepTheirOrderAndCardDetails() {
        val first = card(7, "First", score = 92)
        val second = card(3, "Second", score = null)

        val restored = FavoritesCodec.decode(FavoritesCodec.encode(listOf(first, second)))

        assertEquals(listOf(7, 3), restored.map { it.id })
        assertEquals(first, restored[0])
        assertEquals(second, restored[1])
        assertNull(restored[1].score)
    }

    @Test
    fun oldOrDamagedSavedDataDoesNotCrashFavorites() {
        assertTrue(FavoritesCodec.decode("broken json").isEmpty())
        assertTrue(FavoritesCodec.decode(null).isEmpty())
        assertEquals(
            listOf(4),
            FavoritesCodec.decode("""[{"id":4,"romaji":"Legacy"}]""").map { it.id },
        )
    }

    private fun card(id: Int, name: String, score: Int?) = AnimeCard(
        id = id,
        title = Title(name, null, null),
        cover = "https://example.com/$id.jpg",
        score = score,
        episodes = 12,
        format = "TV",
        status = "FINISHED",
        seasonYear = 2025,
        nextEpisode = null,
    )
}
