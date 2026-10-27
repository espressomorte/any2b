package ua.any2b.core.anilist

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrailerTest {
    @Test
    fun detailsRequestReadsAniListTrailer() = runBlocking {
        val response = """{"data":{"Media":{
          "id":1,"title":{"romaji":"Kimi no Na wa.","english":"Your Name","native":null},
          "coverImage":null,"bannerImage":null,"description":null,"averageScore":null,
          "episodes":1,"duration":106,"format":"MOVIE","status":"FINISHED",
          "seasonYear":2016,"synonyms":["Kimi no Na wa"],"nextAiringEpisode":{"episode":2},
          "genres":[],"studios":{"nodes":[]},
          "relations":{"edges":[]},"recommendations":{"pageInfo":{"hasNextPage":false},"nodes":[]},
          "trailer":{"id":"xU47nhruN-Q","site":"youtube"}
        }}}"""
        val http = OkHttpClient.Builder().addInterceptor { chain ->
            val payload = Buffer().also { chain.request().body?.writeTo(it) }.readUtf8()
            assertTrue(payload.contains("trailer { id site }"))
            assertTrue(payload.contains("synonyms"))
            assertTrue(payload.contains("nextAiringEpisode { episode }"))
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(response.toResponseBody()).build()
        }.build()

        val details = AniListClient(http).animeDetails(1)

        assertEquals("https://www.youtube.com/watch?v=xU47nhruN-Q", details.trailer?.videoUrl())
        assertEquals(listOf("Kimi no Na wa"), details.synonyms)
        assertEquals(2, details.nextEpisode)
    }

    @Test
    fun missingOrUnsupportedTrailerFallsBackToEncodedSearch() {
        val details = animeDetails(trailer = MediaTrailer("bad/id", "youtube"))

        assertNull(details.trailer?.videoUrl())
        assertEquals(
            "https://www.youtube.com/results?search_query=Your+Name+2016+trailer",
            details.trailerSearchUrl(),
        )
        assertNull(MediaTrailer("video-id", "unknown").videoUrl())
        assertEquals(
            "https://www.dailymotion.com/video/x123abc",
            MediaTrailer("x123abc", "dailymotion").videoUrl(),
        )
    }

    private fun animeDetails(trailer: MediaTrailer?) = AnimeDetails(
        id = 1,
        title = Title("Kimi no Na wa.", "Your Name", null),
        cover = null,
        banner = null,
        description = null,
        score = null,
        episodes = 1,
        durationMinutes = 106,
        format = "MOVIE",
        status = "FINISHED",
        seasonYear = 2016,
        genres = emptyList(),
        studios = emptyList(),
        trailer = trailer,
    )
}
