package ua.any2b.core.mal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MalClientTest {
    @Test
    fun rankingParsesScorePosterAndPaging() {
        val page = MalClient.parseRanking("""{
            "data": [{"node": {
                "id": 52991, "title": "Sousou no Frieren", "mean": 9.24,
                "media_type": "tv", "start_date": "2023-09-29",
                "main_picture": {"medium": "medium.jpg", "large": "large.jpg"},
                "alternative_titles": {"en": "Frieren: Beyond Journey's End"}
            }}],
            "paging": {"next": "https://api.myanimelist.net/v2/anime/ranking?offset=25"}
        }""")

        assertEquals(52991, page.items.single().id)
        assertEquals(9.24, page.items.single().score)
        assertEquals(2023, page.items.single().year)
        assertEquals("large.jpg", page.items.single().posterUrl)
        assertTrue(page.hasMore)
        assertFalse(MalClient.parseRanking("""{"data":[],"paging":{}}""").hasMore)
    }
}
