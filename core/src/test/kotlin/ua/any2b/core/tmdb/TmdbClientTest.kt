package ua.any2b.core.tmdb

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TmdbClientTest {
    @Test
    fun multiSearchEncodesQueryAndKeepsMoviesAndTvInServerOrder() = runBlocking {
        var request: Request? = null
        val languages = mutableListOf<String>()
        val client = client { incoming ->
            request = incoming
            languages += incoming.url.queryParameter("language").orEmpty()
            fixture(incoming, 200, """{"results":[
                {"id":1,"media_type":"tv","name":"Атака титанів: Школа","first_air_date":"2015-10-04"},
                {"id":2,"media_type":"person","name":"Актор"},
                {"id":1429,"media_type":"tv","name":"Атака титанів","original_name":"進撃の巨人",
                 "first_air_date":"2013-04-07","poster_path":"/poster.jpg","vote_average":8.69},
                {"id":11,"media_type":"movie","title":"Фільм","release_date":"2020-01-01"}
            ],"extra":true}""")
        }
        val results = client.search("Attack on Titan & year", "sample-token")
        assertEquals("Attack on Titan & year", request?.url?.queryParameter("query"))
        assertEquals("uk-UA", request?.url?.queryParameter("language"))
        assertEquals(listOf("en-US", "uk-UA"), languages)
        assertEquals("Bearer sample-token", request?.header("Authorization"))
        assertEquals(listOf(1, 1429, 11), results.map { it.id })
        assertEquals("Атака титанів", results[1].title)
        assertEquals(2013, results[1].year)
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", results[1].posterUrl)
        assertEquals(TmdbMediaType.MOVIE, results[2].type)
    }

    @Test
    fun namesPrioritizeQueryLanguageAndPutOtherOriginalLanguageLast() {
        val airplane = TmdbTitle(813, TmdbMediaType.MOVIE, "Airplane!", "Airplane!",
            1980, null, null, null, ukrainianTitle = "Аероплан",
            englishTitle = "Airplane!", originalLanguage = "en")
        assertEquals(listOf("Airplane!", "Аероплан"), airplane.searchNames("Airplane"))

        val asterix = TmdbTitle(122781, TmdbMediaType.TV,
            "Asterix & Obelix: The Big Fight", "Astérix & Obélix : Le Combat des chefs",
            2025, null, null, null,
            ukrainianTitle = "Астерікс і Обелікс: Поєдинок вождів",
            englishTitle = "Asterix & Obelix: The Big Fight", originalLanguage = "fr")
        assertEquals(listOf("Asterix & Obelix: The Big Fight",
            "Астерікс і Обелікс: Поєдинок вождів", "Astérix & Obélix : Le Combat des chefs"),
            asterix.searchNames("Asterix"))
        assertEquals("Astérix & Obélix : Le Combat des chefs", asterix.searchNames("Astérix").first())
    }

    @Test
    fun detailsUseCorrectEndpointAndReadLocalizedFields() = runBlocking {
        val paths = mutableListOf<String>()
        val client = client { request ->
            paths += request.url.encodedPath
            fixture(request, 200, if (request.url.encodedPath.endsWith("/tv/1429")) {
                """{"id":1429,"name":"Атака титанів","original_name":"進撃の巨人",
                    "first_air_date":"2013-04-07","overview":"Український опис",
                    "genres":[{"name":"Драма"}],"number_of_seasons":4,"number_of_episodes":87,
                    "episode_run_time":[24]}"""
            } else {
                """{"id":157336,"title":"Інтерстеллар","release_date":"2014-11-04",
                    "runtime":169,"genres":[{"name":"Пригоди"}],
                    "belongs_to_collection":{"id":123,"name":"Інтерстеллар | Колекція"}}"""
            })
        }
        val series = client.details(TmdbMediaType.TV, 1429, "token")
        val movie = client.details(TmdbMediaType.MOVIE, 157336, "token")
        assertEquals(listOf("/3/tv/1429", "/3/movie/157336"), paths)
        assertEquals("Український опис", series.title.overview)
        assertEquals(4, series.seasons)
        assertEquals(87, series.episodes)
        assertEquals(24, series.runtimeMinutes)
        assertEquals(169, movie.runtimeMinutes)
        assertEquals("Інтерстеллар | Колекція", movie.collectionName)
        assertEquals(null, series.collectionName)
        assertTrue(movie.seasons == null)
    }

    @Test
    fun missingOrRejectedTokenHasClearError() = runBlocking {
        val client = client { request -> fixture(request, 401, "{}") }
        assertFailsWith<TmdbFailure.MissingToken> { client.search("Anime", "") }
        assertFailsWith<TmdbFailure.Unauthorized> { client.search("Anime", "bad") }
        Unit
    }

    private fun client(reply: (Request) -> Response): TmdbClient = TmdbClient(
        OkHttpClient.Builder().addInterceptor { chain -> reply(chain.request()) }.build(),
        "https://tmdb.test/3/".toHttpUrl(),
    )

    private fun fixture(request: Request, status: Int, body: String): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(status)
        .message("fixture").body(body.toResponseBody()).build()
}
