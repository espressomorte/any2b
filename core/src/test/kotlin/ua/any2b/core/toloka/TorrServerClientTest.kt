package ua.any2b.core.toloka

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TorrServerClientTest {
    private val hash = "4256e89216a2a9fc296ff191307206bc1790eb01"
    private val status = """{"hash":"$hash","file_stats":[
        {"id":7,"path":"Show/S02E02.mkv","length":2000000000},
        {"id":2,"path":"Show/sample.txt","length":100},
        {"id":4,"path":"Show/S02E01.mp4","length":1500000000},
        {"id":9,"path":"Show/subtitles.srt","length":1000}
    ]}"""

    @Test
    fun uploadUsesMultipartBinaryAndPreservesRealFileIds() = runBlocking {
        var seenRequest: Request? = null
        val client = client { request ->
            seenRequest = request
            fixture(request, 200, status)
        }
        val torrent = client.upload("http://192.168.1.10:8090", "d4:infoe".toByteArray())

        assertEquals("/torrent/upload", seenRequest?.url?.encodedPath)
        assertTrue(seenRequest?.body?.contentType().toString().startsWith("multipart/form-data"))
        val sentBody = Buffer().also { seenRequest!!.body!!.writeTo(it) }.readUtf8()
        assertTrue(sentBody.contains("d4:infoe"))
        assertFalse(seenRequest!!.headers.names().contains("Authorization"))
        assertEquals(listOf(4, 7), torrent.videoFiles.map { it.id })
        assertEquals("Show/S02E01.mp4", torrent.videoFiles.first().path)
        assertEquals("http://192.168.1.10:8090/stream/S02E02.mkv?link=$hash&index=7&play",
            client.playbackUrl("http://192.168.1.10:8090", torrent, torrent.videoFiles.last()))
    }

    @Test
    fun acceptsSingleElementArrayResponseFromMatrixVersions() = runBlocking {
        val torrent = client { request -> fixture(request, 200, "[$status]") }
            .upload("http://192.168.1.10:8090", "d4:infoe".toByteArray())
        assertEquals(4, torrent.videoFiles.first().id)
    }

    @Test
    fun missingFileStatsKeepsHashAndMetadataPollNeverUploadsAgain() = runBlocking {
        val requests = mutableListOf<String>()
        val client = client { request ->
            requests += request.url.encodedPath
            fixture(request, 200, if (request.url.encodedPath == "/torrent/upload") {
                """{"hash":"$hash"}"""
            } else status)
        }
        val uploaded = client.upload("http://192.168.1.10:8090", "d4:infoe".toByteArray())
        assertEquals(hash, uploaded.hash)
        assertTrue(uploaded.files.isEmpty())
        val polled = client.get("http://192.168.1.10:8090", uploaded.hash)
        assertEquals(listOf("/torrent/upload", "/torrents"), requests)
        assertEquals(listOf(4, 7), polled.videoFiles.map { it.id })
    }

    @Test
    fun urlEncodesFileNameAndUsesActualMatrixFileId() {
        val file = TorrServerFile(12, "Season/Серія 1.mkv", 100)
        val torrent = TorrServerTorrent(hash, listOf(file))
        val url = TorrServerClient().playbackUrl("http://192.168.1.10:8090", torrent, file)
        assertEquals("http://192.168.1.10:8090/stream/%D0%A1%D0%B5%D1%80%D1%96%D1%8F%201.mkv?link=$hash&index=12&play", url)
    }

    @Test
    fun rejectsZeroFileIdInsteadOfInventingPlaybackIndex() = runBlocking {
        val invalid = """{"hash":"$hash","file_stats":[{"id":0,"path":"film.mkv","length":100}]}"""
        assertFailsWith<TorrServerFailure.InvalidResponse> {
            client { request -> fixture(request, 200, invalid) }
                .upload("http://192.168.1.10:8090", "d4:infoe".toByteArray())
        }
        Unit
    }

    @Test
    fun optionalBasicAuthUsesTorrServerCredentialsOnly() = runBlocking {
        var authorization: String? = null
        val client = client { request ->
            authorization = request.header("Authorization")
            fixture(request, 200, status)
        }
        client.upload("http://192.168.1.10:8090", "d4:infoe".toByteArray(),
            TorrServerBasicAuth("local-user", "local-password"))
        assertEquals("Basic bG9jYWwtdXNlcjpsb2NhbC1wYXNzd29yZA==", authorization)
    }

    @Test
    fun rejectsResponseWithoutHashAndDoesNotInventFileIndex() = runBlocking {
        val client = client { request -> fixture(request, 200, """{"file_stats":[{"path":"film.mp4"}]}""") }
        assertFailsWith<TorrServerFailure.InvalidResponse> {
            client.upload("http://192.168.1.10:8090", "d4:infoe".toByteArray())
        }
        Unit
    }

    private fun client(respond: (Request) -> Response) = TorrServerClient(
        OkHttpClient.Builder().addInterceptor { chain -> respond(chain.request()) }.build(),
    )

    private fun fixture(request: Request, code: Int, body: String) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
        .body(body.toResponseBody()).build()
}
