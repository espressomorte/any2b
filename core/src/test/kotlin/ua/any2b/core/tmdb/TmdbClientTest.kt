package ua.any2b.core.tmdb

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TmdbClientTest {
    @Test
    fun multiSearchEncodesQueryAndKeepsMoviesAndTvInServerOrder() = runBlocking {
        var request: Request? = null
        val languages = CopyOnWriteArrayList<String>()
        val client = client { incoming ->
            request = incoming
            languages += incoming.url.queryParameter("language").orEmpty()
            fixture(incoming, 200, """{"results":[
                {"id":1,"media_type":"tv","name":"Атака титанів: Школа","first_air_date":"2015-10-04"},
                {"id":2,"media_type":"person","name":"Актор"},
                {"id":1429,"media_type":"tv","name":"Атака титанів","original_name":"進撃の巨人",
                 "first_air_date":"2013-04-07","poster_path":"/poster.jpg","vote_average":8.69,
                 "genre_ids":[16,10759],"origin_country":["JP"],"original_language":"ja"},
                {"id":11,"media_type":"movie","title":"Фільм","release_date":"2020-01-01"}
            ],"extra":true}""")
        }
        val results = client.search("Attack on Titan & year", "sample-token")
        assertEquals("Attack on Titan & year", request?.url?.queryParameter("query"))
        assertEquals(setOf("en-US", "uk-UA"), languages.toSet())
        assertEquals("Bearer sample-token", request?.header("Authorization"))
        assertEquals(listOf(1, 1429, 11), results.map { it.id })
        assertEquals("Атака титанів", results[1].title)
        assertEquals(2013, results[1].year)
        assertEquals(setOf(16, 10759), results[1].genreIds)
        assertEquals(setOf("JP"), results[1].originCountries)
        assertEquals("https://image.tmdb.org/t/p/w500/poster.jpg", results[1].posterUrl)
        assertEquals(TmdbMediaType.MOVIE, results[2].type)
    }

    @Test
    fun workerProxyUsesWorkerBearerKeyAndRestrictedPath() = runBlocking {
        var path: String? = null
        var bearer: String? = null
        val worker = TmdbClient(
            OkHttpClient.Builder().addInterceptor { chain ->
                path = chain.request().url.encodedPath
                bearer = chain.request().header("Authorization")
                fixture(chain.request(), 200, """{"results":[]}""")
            }.build(),
            "https://worker.test/tmdb/3/".toHttpUrl(),
        )
        worker.search("Anime", "worker-key")
        assertEquals("/tmdb/3/search/multi", path)
        assertEquals("Bearer worker-key", bearer)
    }

    @Test
    fun popularAndRelatedListsUseTypedEndpointsAndIgnoreAdultItems() = runBlocking {
        val paths = CopyOnWriteArrayList<String>()
        val client = TmdbClient(
            OkHttpClient.Builder().addInterceptor { chain ->
                val request = chain.request()
                paths += request.url.encodedPath
                fixture(request, 200, if (request.url.encodedPath.contains("/tv/")) {
                    """{"results":[
                      {"id":10,"name":"Серіал","first_air_date":"2026-01-01"},
                      {"id":11,"name":"18+","adult":true}]}"""
                } else {
                    """{"results":[
                      {"id":20,"title":"Фільм","release_date":"2025-02-01"},
                      {"id":21,"title":"18+","adult":true}]}"""
                })
            }.build(),
            "https://worker.test/tmdb/3/".toHttpUrl(),
        )
        assertEquals(listOf(10), client.popular(TmdbMediaType.TV, "key").map { it.id })
        assertEquals(listOf(20), client.popular(TmdbMediaType.MOVIE, "key").map { it.id })
        assertEquals(listOf(10), client.related(TmdbMediaType.TV, 42, "key").map { it.id })
        assertEquals(listOf("/tmdb/3/tv/popular", "/tmdb/3/movie/popular",
            "/tmdb/3/tv/42/recommendations"), paths)
    }

    @Test
    fun listPagesUseServerSideSortAndPreservePagination() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = client { request ->
            requests += request
            fixture(request, 200, """{"page":2,"total_pages":5,"results":[
                {"id":25,"title":"Фільм","release_date":"2024-01-01"}]}""")
        }
        val rated = client.listPage(TmdbMediaType.MOVIE, "key", TmdbListSort.RATING, 2)
        val yearly = client.listPage(TmdbMediaType.TV, "key", TmdbListSort.YEAR, 2)
        assertEquals(listOf(25), rated.titles.map { it.id })
        assertTrue(rated.hasMore)
        assertEquals(2, rated.page)
        assertEquals("/3/discover/movie", requests[0].url.encodedPath)
        assertEquals("vote_average.desc", requests[0].url.queryParameter("sort_by"))
        assertEquals("100", requests[0].url.queryParameter("vote_count.gte"))
        assertEquals("2", requests[0].url.queryParameter("page"))
        assertEquals("/3/discover/tv", requests[1].url.encodedPath)
        assertEquals("first_air_date.desc", requests[1].url.queryParameter("sort_by"))
        assertTrue(yearly.hasMore)
    }

    @Test
    fun animationListsUseDiscoverWithGenre16ForBothMediaTypes() = runBlocking {
        val requests = mutableListOf<Request>()
        val client = client { request ->
            requests += request
            fixture(request, 200, if (request.url.encodedPath.endsWith("/tv"))
                """{"page":1,"total_pages":2,"results":[
                    {"id":10,"name":"Анімація","genre_ids":[16],"first_air_date":"2025-01-01"},
                    {"id":11,"name":"Ігровий серіал","genre_ids":[18],"first_air_date":"2025-01-01"}]}"""
                else """{"page":1,"total_pages":2,"results":[
                    {"id":20,"title":"Анімація","genre_ids":[16],"release_date":"2025-01-01"},
                    {"id":21,"title":"Ігровий фільм","genre_ids":[18],"release_date":"2025-01-01"}]}""")
        }
        assertEquals(listOf(10), client.popular(TmdbMediaType.TV, "key", genreId = 16).map { it.id })
        assertEquals(listOf(20), client.popular(TmdbMediaType.MOVIE, "key", genreId = 16).map { it.id })
        assertEquals(listOf("/3/discover/tv", "/3/discover/movie"),
            requests.map { it.url.encodedPath })
        assertTrue(requests.all { it.url.queryParameter("with_genres") == "16" &&
            it.url.queryParameter("sort_by") == "popularity.desc" })
        assertEquals("60", requests[1].url.queryParameter("with_runtime.gte"))
    }

    @Test
    fun trailerSearchUsesOriginalTitleAndYear() {
        val title = TmdbTitle(1, TmdbMediaType.MOVIE, "Українська назва",
            "English Name", 2025, null, null, null)
        val details = TmdbDetails(title, emptyList(), null, null, null, null, null)
        assertTrue(details.trailerSearchUrl().contains("English+Name+2025+trailer"))
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
                    "origin_country":["JP","US","JP"],
                    "alternative_titles":{"results":[{"title":"Синонім серіалу"}]},
                    "genres":[{"name":"Драма"}],"number_of_seasons":4,"number_of_episodes":87,
                    "episode_run_time":[24],"seasons":[
                      {"season_number":1,"name":"Сезон 1","air_date":"2013-04-07"},
                      {"season_number":2,"name":"Сезон 2","air_date":"2017-04-01"}]}"""
            } else {
                """{"id":157336,"title":"Інтерстеллар","release_date":"2014-11-04",
                    "runtime":169,"genres":[{"name":"Пригоди"}],
                    "production_countries":[{"iso_3166_1":"US"},{"iso_3166_1":"GB"},
                        {"iso_3166_1":"US"}],
                    "alternative_titles":{"titles":[{"title":"Інша назва"},
                        {"title":"Інша назва"},{"title":"Друга назва"}]},
                    "belongs_to_collection":{"id":123,"name":"Інтерстеллар | Колекція"},
                    "credits":{"crew":[{"job":"Producer","name":"Продюсер"},
                        {"job":"Director","name":"Крістофер Нолан"},
                        {"job":"Director","name":"Крістофер Нолан"}],
                        "cast":[{"name":"Меттью Макконегі"},{"name":"Енн Гетевей"},
                            {"name":"Меттью Макконегі"},{"name":null}]}}"""
            })
        }
        val series = client.details(TmdbMediaType.TV, 1429, "token")
        val movie = client.details(TmdbMediaType.MOVIE, 157336, "token")
        assertEquals(listOf("/3/tv/1429", "/3/movie/157336"), paths)
        assertEquals("Український опис", series.title.overview)
        assertEquals(4, series.seasons)
        assertEquals(87, series.episodes)
        assertEquals(24, series.runtimeMinutes)
        assertEquals(listOf(TmdbSeason(1, "Сезон 1", 2013),
            TmdbSeason(2, "Сезон 2", 2017)), series.seasonEntries)
        assertEquals(169, movie.runtimeMinutes)
        assertEquals("Інтерстеллар | Колекція", movie.collectionName)
        assertEquals(listOf("Крістофер Нолан"), movie.directors)
        assertEquals(listOf("Меттью Макконегі", "Енн Гетевей"), movie.cast)
        assertEquals(listOf("US", "GB"), movie.countryCodes)
        assertEquals(listOf("JP", "US"), series.countryCodes)
        assertEquals(listOf("Синонім серіалу"), series.alternativeTitles)
        assertEquals(listOf("Інша назва", "Друга назва"), movie.alternativeTitles)
        assertTrue(series.directors.isEmpty())
        assertTrue(series.cast.isEmpty())
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
