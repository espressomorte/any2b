package ua.any2b.core.fanart

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import ua.any2b.core.tmdb.TmdbMediaType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FanartClientTest {
    @Test
    fun keyCheckUsesPersonalHeaderAndKnownMovie() = runBlocking {
        var request: Request? = null
        val probe = client { incoming ->
            request = incoming
            fixture(incoming, 200, "{}")
        }
        assertEquals(200, probe.checkKey("fixture-key"))
        assertEquals("/v3.2/movies/550", request?.url?.encodedPath)
        assertEquals("fixture-key", request?.header("client-key"))
        assertNull(request?.url?.queryParameter("client_key"))
    }

    @Test
    fun keyCheckReportsUnauthorizedWithoutEchoingKey() = runBlocking {
        assertEquals(401, client { fixture(it, 401, "{}") }.checkKey("fixture-key"))
    }

    @Test
    fun movieUsesTmdbIdAndSelectsTextlessPosterAndEnglishLogo() = runBlocking {
        var request: Request? = null
        val movieClient = client { incoming ->
            request = incoming
            fixture(incoming, 200, """{
                "movieposter":[
                    {"url":"https://assets.fanart.tv/fanart/poster-en.jpg","lang":"en","likes":"99"},
                    {"url":"https://assets.fanart.tv/fanart/poster-blank.jpg","lang":"00","likes":"5"},
                    {"url":"https://assets.fanart.tv/fanart/poster-blank-2.jpg","lang":null,"likes":"12"}],
                "hdmovielogo":[
                    {"url":"https://assets.fanart.tv/fanart/logo-uk.png","lang":"uk","likes":"99"},
                    {"url":"https://assets.fanart.tv/fanart/logo-en.png","lang":"en","likes":"2"}]
            }""")
        }
        val artwork = movieClient.artwork(TmdbMediaType.MOVIE, 550, "fixture-personal-key")
        assertEquals("/v3.2/movies/550", request?.url?.encodedPath)
        assertNull(request?.url?.queryParameter("client_key"))
        assertEquals("fixture-personal-key", request?.header("client-key"))
        assertEquals("https://assets.fanart.tv/fanart/poster-blank-2.jpg", artwork?.posterUrl)
        assertEquals("https://assets.fanart.tv/fanart/logo-en.png", artwork?.logoUrl)
    }

    @Test
    fun tvExplicitlyUsesTmdbSourceAndFallsBackWithoutBothImages() = runBlocking {
        var request: Request? = null
        val tvClient = client { incoming ->
            request = incoming
            fixture(incoming, 200, """{"tvposter":[
                {"url":"https://assets.fanart.tv/fanart/poster.jpg","lang":"0"}],
                "clearlogo":[{"url":"https://assets.fanart.tv/fanart/logo.png","lang":"en"}]}""")
        }
        assertEquals("https://assets.fanart.tv/fanart/poster.jpg",
            tvClient.artwork(TmdbMediaType.TV, 1399, "key")?.posterUrl)
        assertEquals("/v3.2/tv/1399", request?.url?.encodedPath)
        assertEquals("tmdb", request?.url?.queryParameter("source"))
        val missingLogo = client { fixture(it, 200,
            """{"tvposter":[{"url":"https://assets.fanart.tv/fanart/poster.jpg","lang":"00"}]}""") }
        val inspection = missingLogo.inspect(TmdbMediaType.TV, 1399, "key")
        assertNull(inspection.artwork)
        assertEquals(true, inspection.entryFound)
        assertEquals(true, inspection.textlessPosterFound)
        assertEquals(false, inspection.englishLogoFound)
    }

    @Test
    fun englishPosterIsUsedWhenTextlessPosterIsMissing() = runBlocking {
        val lookup = client { incoming -> fixture(incoming, 200, """{
            "movieposter":[{"url":"https://assets.fanart.tv/fanart/english.jpg","lang":"en"}],
            "hdmovielogo":[{"url":"https://assets.fanart.tv/fanart/english.png","lang":"en"}]
        }""") }.inspect(TmdbMediaType.MOVIE, 550, "key")
        assertEquals("https://assets.fanart.tv/fanart/english.jpg", lookup.artwork?.posterUrl)
        assertEquals(false, lookup.textlessPosterFound)
    }

    @Test
    fun missingEntryIsNormalFallback() = runBlocking {
        val result = client { fixture(it, 404, "{}") }.inspect(TmdbMediaType.MOVIE, 550, "key")
        assertNull(result.artwork)
        assertEquals(false, result.entryFound)
    }

    private fun client(reply: (Request) -> Response): FanartClient = FanartClient(
        OkHttpClient.Builder().addInterceptor { chain -> reply(chain.request()) }.build(),
        "https://webservice.fanart.tv/v3.2/".toHttpUrl(),
    )

    private fun fixture(request: Request, status: Int, body: String): Response = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(status)
        .message("fixture").body(body.toResponseBody()).build()
}
