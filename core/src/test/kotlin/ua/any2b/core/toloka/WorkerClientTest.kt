package ua.any2b.core.toloka

import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkerClientTest {
    private val base = "https://worker.example.test"

    @Test
    fun searchEncodesQueryAndReadsOptionalFieldsWithoutSelecting() = runBlocking {
        val query = "Кіберпанк: Місто & рік"
        var requestedQuery = ""
        var bearer = ""
        val client = client { request ->
            requestedQuery = request.url.queryParameter("q").orEmpty()
            bearer = request.header("Authorization").orEmpty()
            fixture(request, 200, """{
              "query":"$query","count":2,"parsedRows":2,"cached":true,"extra":42,
              "results":[
                {"id":"668277","title":"Повний сезон","pageUrl":"https://toloka.to/t668277","sizeText":"6.54 GB","seeders":0,"leechers":null,"extra":"ok"},
                {"id":"668278","title":"Інший сезон","pageUrl":"https://toloka.to/t668278","sizeText":"3 GB","seeders":null,"leechers":2}
              ]
            }""")
        }
        val result = client.search(base, "sample-user-key", query)
        assertEquals(query, requestedQuery)
        assertEquals("Bearer sample-user-key", bearer)
        assertEquals(2, result.results.size)
        assertEquals(0, result.results[0].seeders)
        assertNull(result.results[0].leechers)
        assertNull(result.results[1].seeders)
        assertNull(result.sessionReused)
    }

    @Test
    fun emptySearchIsAValidResult() = runBlocking {
        val result = client { request -> fixture(request, 200, """{"query":"x","results":[]}""") }
            .search(base, "key", "x")
        assertTrue(result.results.isEmpty())
    }

    @Test
    fun unauthorizedAndRateLimitAreDistinctAndCooldownBlocksNextCall() = runBlocking {
        assertFailsWith<WorkerFailure.Unauthorized> {
            client { request -> fixture(request, 401, "{}") }.status(base, "bad")
        }
        var calls = 0
        val limited = client { request ->
            calls++
            fixture(request, 429, """{"retryAfterSeconds":30}""")
        }
        val failure = assertFailsWith<WorkerFailure.RateLimited> { limited.search(base, "key", "x") }
        assertEquals(30, failure.waitSeconds)
        assertFailsWith<WorkerFailure.RateLimited> { limited.status(base, "key") }
        assertEquals(1, calls)
    }

    @Test
    fun timeoutHasSpecificError() = runBlocking {
        val http = OkHttpClient.Builder().addInterceptor { throw SocketTimeoutException("simulated") }.build()
        assertFailsWith<WorkerFailure.Timeout> { WorkerClient(http).status(base, "key") }
        Unit
    }

    @Test
    fun retryAfterHeaderOverridesBodyDelay() = runBlocking {
        val worker = client { request ->
            fixture(request, 429, """{"retryAfterSeconds":60}""")
                .newBuilder().header("Retry-After", "3").build()
        }
        assertEquals(3, assertFailsWith<WorkerFailure.RateLimited> {
            worker.status(base, "key")
        }.waitSeconds)
    }

    @Test
    fun torrentRejectsHtmlAndJsonEvenWithSuccessStatus() = runBlocking {
        for (type in listOf("text/html", "application/json")) {
            val worker = client { request -> fixture(request, 200, "<html>not a torrent</html>", type) }
            assertFailsWith<WorkerFailure.InvalidResponse> { worker.torrent(base, "key", "668277") }
        }
    }

    @Test
    fun expiredTorrentLinkRequiresNewSearchWithoutFallback() = runBlocking {
        val worker = client { request -> fixture(request, 404,
            """{"error":"run_search_first_or_link_expired"}""") }
        assertFailsWith<WorkerFailure.LinkExpired> { worker.torrent(base, "key", "668277") }
        Unit
    }

    @Test
    fun binaryTorrentIsReturnedUnchanged() = runBlocking {
        val bytes = "d4:infod4:name4:teste".toByteArray()
        val worker = client { request ->
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .header("Content-Type", "application/x-bittorrent")
                .body(bytes.toResponseBody()).build()
        }
        assertTrue(bytes.contentEquals(worker.torrent(base, "key", "668277")))
    }

    @Test
    fun torrentDownloadHandlesUnauthorizedAndRetryAfter() = runBlocking {
        assertFailsWith<WorkerFailure.Unauthorized> {
            client { request -> fixture(request, 401, "{}") }.torrent(base, "bad", "668277")
        }
        val limited = client { request -> fixture(request, 429, "{}")
            .newBuilder().header("Retry-After", "12").build() }
        assertEquals(12, assertFailsWith<WorkerFailure.RateLimited> {
            limited.torrent(base, "key", "668277")
        }.waitSeconds)
    }

    private fun client(respond: (okhttp3.Request) -> Response): WorkerClient = WorkerClient(
        OkHttpClient.Builder().addInterceptor { chain -> respond(chain.request()) }.build(),
    )

    private fun fixture(request: okhttp3.Request, code: Int, body: String, type: String = "application/json") =
        Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
            .header("Content-Type", type).body(body.toResponseBody()).build()
}
